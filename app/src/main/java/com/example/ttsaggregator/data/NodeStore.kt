package com.example.ttsaggregator.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 节点存储：Android 系统自带 SQLite（二进制存储、增量写入、索引搜索）。
 *
 * 选择理由：
 * - 相比 SharedPrefs：无 1MB 事务上限，可承载十万级节点
 * - 相比 JSON 文件：单行 UPDATE/INSERT 不全量重写；支持索引 + LIKE 搜索 + LIMIT 分页
 * - 相比 Room：零依赖、零注解处理器（避免 KSP 构建复杂度回归）
 *
 * 表结构刻意只存"节点元数据"，音色列表全局缓存一份，避免体积爆炸。
 */
class NodeStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE nodes (
                id TEXT PRIMARY KEY NOT NULL,
                base_url TEXT NOT NULL UNIQUE,
                label TEXT NOT NULL DEFAULT '',
                keyless INTEGER NOT NULL DEFAULT 0,
                api_key TEXT,
                health INTEGER NOT NULL DEFAULT 2,
                last_checked INTEGER NOT NULL DEFAULT 0,
                default_voice TEXT NOT NULL DEFAULT '',
                voice_count INTEGER NOT NULL DEFAULT 0,
                fail_count INTEGER NOT NULL DEFAULT 0,
                cooldown_until INTEGER NOT NULL DEFAULT 0,
                disabled INTEGER NOT NULL DEFAULT 0,
                api_version INTEGER NOT NULL DEFAULT 2
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_nodes_health ON nodes(health)")
        db.execSQL("CREATE INDEX idx_nodes_checked ON nodes(last_checked)")
        db.execSQL("CREATE INDEX idx_nodes_label ON nodes(label)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 → v2：调度字段（失败计数 / 冷却 / 禁用）
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE nodes ADD COLUMN fail_count INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE nodes ADD COLUMN cooldown_until INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE nodes ADD COLUMN disabled INTEGER NOT NULL DEFAULT 0")
        }
        // v2 → v3：API 版本标签（0=V1 1=LEGACY 2=UNKNOWN）
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE nodes ADD COLUMN api_version INTEGER NOT NULL DEFAULT 2")
        }
    }

    // ---------- 行 <-> 模型 ----------

    private fun TtsNode.toValues(includeId: Boolean): ContentValues = ContentValues().apply {
        if (includeId) put("id", id)
        put("base_url", baseUrl)
        put("label", label)
        put("keyless", if (keyless) 1 else 0)
        put("api_key", apiKey) // null 会写入 NULL ✓
        put("health", health.ordinal)
        put("last_checked", lastChecked)
        put("default_voice", defaultVoice)
        put("voice_count", voiceCount)
        put("fail_count", failCount)
        put("cooldown_until", cooldownUntil)
        put("disabled", if (disabled) 1 else 0)
        put("api_version", apiVersion.ordinal)
    }

    private fun Cursor.toNode(): TtsNode = TtsNode(
        id = getString(getColumnIndexOrThrow("id")),
        baseUrl = getString(getColumnIndexOrThrow("base_url")),
        label = getString(getColumnIndexOrThrow("label")),
        keyless = getInt(getColumnIndexOrThrow("keyless")) != 0,
        apiKey = getStringOrNull("api_key"),
        health = TtsNode.Health.entries[getInt(getColumnIndexOrThrow("health"))],
        lastChecked = getLong(getColumnIndexOrThrow("last_checked")),
        defaultVoice = getString(getColumnIndexOrThrow("default_voice")),
        voiceCount = getInt(getColumnIndexOrThrow("voice_count")),
        failCount = getInt(getColumnIndexOrThrow("fail_count")),
        cooldownUntil = getLong(getColumnIndexOrThrow("cooldown_until")),
        disabled = getInt(getColumnIndexOrThrow("disabled")) != 0,
        apiVersion = ApiVersion.entries[
            getInt(getColumnIndexOrThrow("api_version")).coerceIn(0, ApiVersion.entries.size - 1)
        ],
    )

    private fun Cursor.getStringOrNull(col: String): String? {
        val i = getColumnIndexOrThrow(col)
        return if (isNull(i)) null else getString(i)
    }

    // ---------- 增删查（增量写入，不全量重写） ----------

    /** 按 baseUrl upsert：已存在则只 UPDATE 变更列（保留原 id），不存在则 INSERT */
    fun insertOrUpdate(node: TtsNode) {
        insertOrUpdateAll(listOf(node))
    }

    /** 批量 upsert（单事务，导入万级节点也是毫秒级） */
    fun insertOrUpdateAll(nodes: List<TtsNode>) {
        if (nodes.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            nodes.forEach { node ->
                val updated = db.update(
                    "nodes",
                    node.toValues(includeId = false),
                    "base_url = ?",
                    arrayOf(node.baseUrl),
                )
                if (updated == 0) {
                    db.insertWithOnConflict(
                        "nodes", null, node.toValues(includeId = true),
                        SQLiteDatabase.CONFLICT_IGNORE,
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 全量：健康度优先、最近测试在前（也是节点池的候选顺序） */
    fun getAll(): List<TtsNode> = readableDatabase
        .rawQuery("SELECT * FROM nodes ORDER BY health ASC, last_checked DESC", null)
        .use { c ->
            val list = ArrayList<TtsNode>(c.count)
            while (c.moveToNext()) list.add(c.toNode())
            list
        }

    fun count(): Int = readableDatabase
        .rawQuery("SELECT COUNT(*) FROM nodes", null)
        .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun getById(id: String): TtsNode? = readableDatabase
        .rawQuery("SELECT * FROM nodes WHERE id = ?", arrayOf(id))
        .use { if (it.moveToFirst()) it.toNode() else null }

    /** 搜索（URL/标签模糊匹配），带分页 */
    fun search(query: String, limit: Int, offset: Int): List<TtsNode> {
        val q = "%$query%"
        return readableDatabase.rawQuery(
            "SELECT * FROM nodes WHERE base_url LIKE ? OR label LIKE ? " +
                "ORDER BY health ASC, last_checked DESC LIMIT ? OFFSET ?",
            arrayOf(q, q, limit.toString(), offset.toString()),
        ).use { c ->
            val list = ArrayList<TtsNode>(c.count)
            while (c.moveToNext()) list.add(c.toNode())
            list
        }
    }

    fun searchCount(query: String): Int {
        val q = "%$query%"
        return readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM nodes WHERE base_url LIKE ? OR label LIKE ?",
            arrayOf(q, q),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun delete(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val placeholders = ids.joinToString(",") { "?" }
        writableDatabase.delete("nodes", "id IN ($placeholders)", ids.toTypedArray())
    }

    /** 批量启用/停用（单条 UPDATE） */
    fun setDisabled(ids: Collection<String>, disabled: Boolean) {
        if (ids.isEmpty()) return
        val placeholders = ids.joinToString(",") { "?" }
        val args = (listOf<Any>(if (disabled) 1 else 0) + ids).toTypedArray()
        writableDatabase.execSQL(
            "UPDATE nodes SET disabled = ? WHERE id IN ($placeholders)",
            args,
        )
    }

    fun clear() = writableDatabase.delete("nodes", null, null)

    companion object {
        private const val DB_NAME = "tts_nodes.db"
        private const val DB_VERSION = 3
    }
}
