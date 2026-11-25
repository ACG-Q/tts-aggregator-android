package com.pulse.tts.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 朗读音频磁盘缓存。
 *
 * 命中时直接返回字节，不发请求、不写调用日志、不影响节点健康；
 * 只有成功拿到的非空音频才落盘。文件放在 cacheDir 下，系统可随时回收，
 * 语义上就是「可再生的缓存」，不影响用户数据。
 *
 * 淘汰策略见 [isAudioCacheExpired] / [selectAudioCacheEvictions]：
 * 默认 7 天（最后访问时间起算），目录上限 200 MB，超出按最久未使用删除。
 */
class AudioCache(context: Context, private val settings: SettingsStore) {

    private val dir = File(context.applicationContext.cacheDir, DIR_NAME)
    private val maintainLock = Mutex()
    private val maintained = AtomicBoolean(false)

    /** 当前缓存占用 */
    data class Stats(val count: Int, val bytes: Long)

    /** 读取缓存；未命中、过期或缓存关闭时返回 null */
    suspend fun get(text: String, prefs: SynthPrefs): ByteArray? = withContext(Dispatchers.IO) {
        val ttlDays = settings.appSettings.value.audioCacheDays
        if (ttlDays <= 0 || text.isBlank()) return@withContext null
        ensureMaintained()

        val file = File(dir, "${audioCacheKey(text, prefs)}.mp3")
        if (!file.isFile) return@withContext null

        val now = System.currentTimeMillis()
        if (isAudioCacheExpired(file.lastModified(), now, ttlDays)) {
            file.delete()
            return@withContext null
        }
        val bytes = runCatching { file.readBytes() }.getOrNull()
        if (bytes == null || bytes.isEmpty()) {
            file.delete()
            return@withContext null
        }
        // 滑动续期：常用音频不会因为写入时间久而失效
        file.setLastModified(now)
        Log.i(TAG, "hit ${bytes.size}B")
        bytes
    }

    /** 写入缓存；缓存关闭或内容为空时直接跳过 */
    suspend fun put(text: String, prefs: SynthPrefs, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val ttlDays = settings.appSettings.value.audioCacheDays
        if (ttlDays <= 0 || text.isBlank() || bytes.isEmpty()) return@withContext
        ensureMaintained()
        if (!dir.exists() && !dir.mkdirs()) return@withContext

        val target = File(dir, "${audioCacheKey(text, prefs)}.mp3")
        val temp = File(dir, "${target.name}.${System.nanoTime()}.tmp")
        val written = runCatching {
            temp.writeBytes(bytes)
            true
        }.getOrDefault(false)
        if (!written) {
            temp.delete()
            return@withContext
        }
        if (!temp.renameTo(target)) {
            runCatching { temp.copyTo(target, overwrite = true) }
            temp.delete()
        }
        enforceLimit()
    }

    /** 扫描一次目录；用于设置页展示占用 */
    suspend fun stats(): Stats = withContext(Dispatchers.IO) {
        val entries = scan()
        Stats(count = entries.size, bytes = entries.sumOf { it.sizeBytes })
    }

    /** 清空全部缓存，返回释放的字节数 */
    suspend fun clear(): Long = withContext(Dispatchers.IO) {
        val entries = scan()
        val freed = entries.sumOf { it.sizeBytes }
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        Log.i(TAG, "cleared $freed B")
        freed
    }

    private suspend fun ensureMaintained() {
        if (maintained.get()) return
        maintainLock.withLock {
            if (maintained.get()) return@withLock
            val ttlDays = settings.appSettings.value.audioCacheDays
            val now = System.currentTimeMillis()
            if (ttlDays > 0) scan().forEach { entry ->
                if (isAudioCacheExpired(entry.lastModified, now, ttlDays)) {
                    File(dir, entry.name).delete()
                }
            }
            enforceLimit()
            maintained.set(true)
        }
    }

    private fun enforceLimit() {
        selectAudioCacheEvictions(scan(), AUDIO_CACHE_LIMIT_BYTES).forEach { entry ->
            File(dir, entry.name).delete()
        }
    }

    private fun scan(): List<AudioCacheEntry> = dir.listFiles()
        ?.filter { it.isFile && it.name.endsWith(".mp3") }
        ?.map { AudioCacheEntry(it.name, it.length(), it.lastModified()) }
        ?: emptyList()

    private companion object {
        const val TAG = "TTS-Agg"
        const val DIR_NAME = "audio-cache"
    }
}
