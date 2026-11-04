package com.pulse.tts.playback

import android.content.Context
import android.media.MediaPlayer
import com.pulse.tts.data.SettingsStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 试听播放器的不可变快照，Compose 直接 collect。 */
data class PlaybackState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val text: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val error: String? = null,
) {
    val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    val isCompleted: Boolean
        get() = isActive && durationMs > 0L && !isPlaying && positionMs >= durationMs - 250L
}

/**
 * 单实例试听播放器：负责落盘、MediaPlayer 生命周期、进度上报与倍速。
 * 只做播放，不认识 UI；所有状态通过 [state] 暴露。
 */
class PlaybackController(
    context: Context,
    private val settings: SettingsStore,
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var file: File? = null
    private var ticker: Job? = null
    private var loadJob: Job? = null
    private var sleepJob: Job? = null

    private val resumeEnabled: Boolean
        get() = settings.appSettings.value.resume

    init {
        // 播放中修改「睡眠定时」需要立刻重设计时器，否则只有下一次播放才生效。
        scope.launch {
            settings.appSettings
                .map { it.sleepMinutes }
                .distinctUntilChanged()
                .collect { minutes -> if (_state.value.isActive) setSleepTimer(minutes) }
        }
    }

    fun play(bytes: ByteArray, title: String, text: String, speed: Float = 1f) {
        loadJob?.cancel()
        sleepJob?.cancel()
        releasePlayer(deleteFile = true)
        _state.value = PlaybackState(
            isActive = true,
            isPlaying = false,
            title = title.trim().ifBlank { "未命名文本" },
            text = text,
            speed = speed,
        )
        loadJob = scope.launch {
            val target = withContext(Dispatchers.IO) {
                val target = if (resumeEnabled) persistentFile() else cacheFile()
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
                target
            }
            if (!isActive) {
                target.delete()
                return@launch
            }
            file = target
            runCatching { createPlayer(target) }.onFailure { error ->
                _state.update { it.copy(isPlaying = false, error = error.message ?: "播放失败") }
                releasePlayer(deleteFile = true)
            }
        }
        val sleepMinutes = settings.appSettings.value.sleepMinutes
        if (sleepMinutes > 0) setSleepTimer(sleepMinutes)
    }

    fun toggle() {
        val p = player ?: return
        val current = _state.value
        if (current.isPlaying) {
            pause()
            return
        }
        val restart = current.durationMs > 0L && current.positionMs >= current.durationMs - 250L
        if (restart) runCatching { p.seekTo(0) }
        runCatching {
            p.start()
            applySpeed(p, current.speed)
        }.onFailure { error ->
            _state.update { it.copy(error = error.message ?: "无法继续播放") }
            return
        }
        _state.update {
            it.copy(
                isPlaying = true,
                positionMs = if (restart) 0L else it.positionMs,
                error = null,
            )
        }
        startTicker()
    }

    fun pause() {
        val p = player ?: return
        runCatching { p.pause() }
        _state.update { it.copy(isPlaying = false) }
        stopTicker()
        saveSnapshot()
    }

    fun seekTo(positionMs: Long) {
        val p = player ?: return
        val duration = _state.value.durationMs
        val target = positionMs.coerceIn(0L, if (duration > 0) duration else positionMs)
        runCatching { p.seekTo(target.toInt()) }
        _state.update { it.copy(positionMs = target) }
    }

    fun setSpeed(speed: Float) {
        val safe = speed.coerceIn(0.5f, 2f)
        _state.update { it.copy(speed = safe) }
        player?.let { applySpeed(it, safe) }
    }

    fun stop() {
        loadJob?.cancel()
        sleepJob?.cancel()
        sleepJob = null
        saveSnapshot()
        releasePlayer(deleteFile = !resumeEnabled)
        _state.value = PlaybackState()
    }

    fun release() {
        sleepJob?.cancel()
        scope.launch { loadJob?.cancelAndJoin() }
        stop()
    }

    /** 应用退到后台：关闭后台播放则暂停，开启则只保存进度以便进程被杀后恢复。 */
    fun onAppBackground(backgroundPlay: Boolean) {
        if (backgroundPlay) saveSnapshot() else pause()
    }

    /** 睡眠定时：到点后暂停并保存进度，不销毁音频文件。 */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes <= 0) return
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            pause()
        }
    }

    /** 恢复上次暂停/退出的进度；没有快照或文件陈旧时清理。 */
    fun restoreIfNeeded() {
        val snapshot = settings.readPlaybackSnapshot()
        val target = persistentFile()
        if (!resumeEnabled || snapshot == null || !target.exists()) {
            settings.clearPlaybackSnapshot()
            target.delete()
            return
        }
        file = target
        _state.value = PlaybackState(
            isActive = true,
            isPlaying = false,
            title = snapshot.title,
            text = snapshot.text,
            positionMs = snapshot.positionMs,
            durationMs = snapshot.durationMs,
            speed = snapshot.speed,
        )
        runCatching {
            createPlayer(target, autoPlay = false, startPositionMs = snapshot.positionMs)
        }.onFailure {
            settings.clearPlaybackSnapshot()
            target.delete()
            file = null
            _state.value = PlaybackState()
        }
    }

    private fun createPlayer(
        target: File,
        autoPlay: Boolean = true,
        startPositionMs: Long = 0L,
    ) {
        releasePlayer(deleteFile = false)
        val p = MediaPlayer()
        player = p
        p.setDataSource(target.absolutePath)
        p.setOnPreparedListener { prepared ->
            val duration = prepared.duration.coerceAtLeast(0).toLong()
            val start = startPositionMs.coerceIn(0L, if (duration > 0) duration else startPositionMs)
            if (start > 0L) runCatching { prepared.seekTo(start.toInt()) }
            _state.update {
                it.copy(
                    durationMs = duration,
                    positionMs = start,
                    isPlaying = autoPlay,
                    error = null,
                )
            }
            if (autoPlay) {
                runCatching {
                    prepared.start()
                    applySpeed(prepared, _state.value.speed)
                }.onFailure { error ->
                    _state.update { it.copy(isPlaying = false, error = error.message ?: "无法开始播放") }
                }
                startTicker()
            }
        }
        p.setOnCompletionListener {
            _state.update { state ->
                state.copy(isPlaying = false, positionMs = state.durationMs)
            }
            stopTicker()
            saveSnapshot()
        }
        p.setOnErrorListener { _, what, extra ->
            _state.update { it.copy(isPlaying = false, error = "播放失败（$what/$extra）") }
            stopTicker()
            true
        }
        p.prepareAsync()
    }

    private fun saveSnapshot() {
        if (!resumeEnabled) {
            settings.clearPlaybackSnapshot()
            return
        }
        val current = _state.value
        if (!current.isActive || current.title.isBlank()) return
        val target = file ?: return
        if (!target.exists()) return
        settings.savePlaybackSnapshot(
            PlaybackSnapshot(
                title = current.title,
                text = current.text,
                positionMs = current.positionMs.coerceAtLeast(0L),
                durationMs = current.durationMs,
                speed = current.speed,
            ),
        )
    }

    private fun persistentFile(): File =
        File(File(appContext.filesDir, "playback"), "last.mp3")

    private fun cacheFile(): File =
        File(appContext.cacheDir, "tts_play_${System.currentTimeMillis()}.mp3")

    private fun applySpeed(p: MediaPlayer, speed: Float) {
        if (p.isPlaying.not() && _state.value.isPlaying.not()) return
        runCatching {
            val params = p.playbackParams
            params.speed = speed.coerceIn(0.5f, 2f)
            params.pitch = 1f
            p.playbackParams = params
        }
    }

    private fun startTicker() {
        stopTicker()
        ticker = scope.launch {
            while (isActive) {
                val p = player ?: break
                val position = runCatching { p.currentPosition.toLong() }.getOrDefault(0L)
                val duration = runCatching { p.duration.toLong() }.getOrDefault(0L)
                _state.update { it.copy(positionMs = position, durationMs = duration.coerceAtLeast(it.durationMs)) }
                delay(200)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun releasePlayer(deleteFile: Boolean) {
        stopTicker()
        player?.let { p ->
            runCatching { if (p.isPlaying) p.stop() }
            runCatching { p.reset() }
            runCatching { p.release() }
        }
        player = null
        if (deleteFile) {
            file?.delete()
            file = null
        }
    }
}
