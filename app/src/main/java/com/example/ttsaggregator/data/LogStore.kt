package com.example.ttsaggregator.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 调用日志存储（SQLite，与节点库分离）。
 *
 * - 环形保留最近 [KEEP] 条（插入时按 id 裁剪，日志不会无限膨胀）
 * - 以 [StateFlow] 暴露当前过滤结果，写入/清空/改过滤后自动刷新，UI 直接 collect
 */
class LogStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    // ---------- 对外状态 ----------

    private val _filter = MutableStateFlow(LogFilter.ALL)
    val filter: StateFlow<LogFilter> = _filter

    private val _logs = MutableStateFlow<List<CallLog>>(emptyList())
    val logs: StateFlow<List<CallLog>> = _logs

    init {
        reload() // 冷启动加载最近日志，首页简版直接可用
    }

    /** 切换过滤维度并刷新列表 */
    fun setFilter(f: LogFilter) {
        _filter.value = f
        reload()
    }

    /** 从库重载当前过滤的列表 */
    fun reload() {
        _logs.value = queryBy(_filter.value)
    }

    // ---------- 建表 / 升级 ----------

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                time INTEGER NOT NULL,
                node_url TEXT NOT NULL,
                mode INTEGER NOT NULL,
                text_len INTEGER NOT NULL,
                attempt INTEGER NOT NULL,
                success INTEGER NOT NULL,
                latency_ms INTEGER NOT NULL,
                from_node TEXT,
                error TEXT,
                cooldown INTEGER NOT NULL DEFAULT 0,
                text_preview TEXT NOT NULL DEFAULT '',
                voice TEXT NOT NULL DEFAULT '',
                rate INTEGER NOT NULL DEFAULT 0,
                pitch INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_logs_time ON logs(time DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 → v2：日志记录更详细（文本/音色/参数）
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE logs ADD COLUMN text_preview TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE logs ADD COLUMN voice TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE logs ADD COLUMN rate INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE logs ADD COLUMN pitch INTEGER NOT NULL DEFAULT 0")
        }
    }

    // ---------- 读写 ----------

    fun insert(log: CallLog) {
        writableDatabase.insert("logs", null, ContentValues().apply {
            put("time", log.time)
            put("node_url", log.nodeUrl)
            put("mode", log.mode.ordinal)
            put("text_len", log.textLen)
            put("attempt", log.attempt)
            put("success", if (log.success) 1 else 0)
            put("latency_ms", log.latencyMs)
            put("from_node", log.fromNode)
            put("error", log.error)
            put("cooldown", if (log.triggeredCooldown) 1 else 0)
            put("text_preview", log.textPreview)
            put("voice", log.voice)
            put("rate", log.rate)
            put("pitch", log.pitch)
        })
        // 环形裁剪：只保留最近 KEEP 条
        writableDatabase.execSQL(
            "DELETE FROM logs WHERE id NOT IN (SELECT id FROM logs ORDER BY id DESC LIMIT $KEEP)",
        )
        reload()
    }

    /** 最近日志（倒序），首页简版用 */
    fun recent(limit: Int): List<CallLog> = query(
        "SELECT * FROM logs ORDER BY id DESC LIMIT ?", arrayOf(limit.toString()),
    )

    /** 按过滤维度查询详情页列表 */
    fun queryBy(filter: LogFilter, limit: Int = QUERY_LIMIT): List<CallLog> = when (filter) {
        LogFilter.ALL -> query("SELECT * FROM logs ORDER BY id DESC LIMIT ?", arrayOf(limit.toString()))
        LogFilter.OK -> query("SELECT * FROM logs WHERE success = 1 ORDER BY id DESC LIMIT ?", arrayOf(limit.toString()))
        LogFilter.FAIL -> query("SELECT * FROM logs WHERE success = 0 ORDER BY id DESC LIMIT ?", arrayOf(limit.toString()))
        LogFilter.SWITCH -> query(
            "SELECT * FROM logs WHERE from_node IS NOT NULL ORDER BY id DESC LIMIT ?",
            arrayOf(limit.toString()),
        )
    }

    fun clear() {
        writableDatabase.delete("logs", null, null)
        reload()
    }

    private fun query(sql: String, args: Array<String>): List<CallLog> =
        readableDatabase.rawQuery(sql, args).use { c ->
            val list = ArrayList<CallLog>(c.count)
            while (c.moveToNext()) list.add(c.toLog())
            list
        }

    private fun Cursor.toLog(): CallLog = CallLog(
        id = getLong(getColumnIndexOrThrow("id")),
        time = getLong(getColumnIndexOrThrow("time")),
        nodeUrl = getString(getColumnIndexOrThrow("node_url")),
        mode = CallMode.entries[getInt(getColumnIndexOrThrow("mode"))],
        textLen = getInt(getColumnIndexOrThrow("text_len")),
        attempt = getInt(getColumnIndexOrThrow("attempt")),
        success = getInt(getColumnIndexOrThrow("success")) != 0,
        latencyMs = getLong(getColumnIndexOrThrow("latency_ms")),
        fromNode = getStringOrNull("from_node"),
        error = getStringOrNull("error"),
        triggeredCooldown = getInt(getColumnIndexOrThrow("cooldown")) != 0,
        textPreview = getStringOrNull("text_preview") ?: "",
        voice = getStringOrNull("voice") ?: "",
        rate = getInt(getColumnIndexOrThrow("rate")),
        pitch = getInt(getColumnIndexOrThrow("pitch")),
    )

    private fun Cursor.getStringOrNull(col: String): String? {
        val i = getColumnIndexOrThrow(col)
        return if (isNull(i)) null else getString(i)
    }

    companion object {
        private const val DB_NAME = "tts_logs.db"
        private const val DB_VERSION = 2
        private const val KEEP = 500       // 环形保留上限
        private const val QUERY_LIMIT = 300 // 详情页单次加载上限
    }
}
