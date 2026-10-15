package com.example.ttsaggregator.localserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.example.ttsaggregator.MainActivity
import com.example.ttsaggregator.R
import com.example.ttsaggregator.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 本机服务运行状态（进程级单例，设置页直接 collect） */
sealed interface ServerStatus {
    data object Stopped : ServerStatus
    data class Running(val port: Int) : ServerStatus
}

/**
 * 前台服务：持 [LocalTtsHttpServer] 生命周期。
 * 开：设置页开关 → startForegroundService；关：ACTION_STOP intent；
 * 杀：START_STICKY 重启后按持久化 enabled 决定拉起或退出。
 */
class LocalServerService : Service() {

    private var server: LocalTtsHttpServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            appContainer.settings.saveLocalServerConfig(
                appContainer.settings.localServerConfig.value.copy(enabled = false),
            )
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }

        val cfg = appContainer.settings.localServerConfig.value
        if (!cfg.enabled) {
            // 进程被杀重启后用户已关闭开关：直接退出，不再拉起
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }

        // 先满足前台时限，再绑端口（绑定失败也能安全退出）
        startAsForeground(cfg.port)
        return try {
            server = LocalTtsHttpServer(cfg.port, appContainer.localTtsHandler).also { it.start() }
            _status.value = ServerStatus.Running(cfg.port)
            START_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "本机服务启动失败: ${e.message}")
            Toast.makeText(this, "本机服务启动失败：${e.message}", Toast.LENGTH_LONG).show()
            appContainer.settings.saveLocalServerConfig(cfg.copy(enabled = false))
            shutdown()
            stopSelf()
            START_NOT_STICKY
        }
    }

    private fun startAsForeground(port: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "本机 TTS 服务", NotificationManager.IMPORTANCE_LOW),
        )
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN_SETTINGS, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("本机 TTS 服务运行中")
            .setContentText("http://127.0.0.1:$port/tts")
            .setContentIntent(tap)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun shutdown() {
        server?.stop()
        server = null
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        _status.value = ServerStatus.Stopped
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        _status.value = ServerStatus.Stopped
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LocalServerService"
        private const val CHANNEL_ID = "local_tts_server"
        private const val NOTIF_ID = 1

        const val ACTION_STOP = "com.example.ttsaggregator.localserver.STOP"
        const val EXTRA_OPEN_SETTINGS = "com.example.ttsaggregator.localserver.OPEN_SETTINGS"

        private val _status = MutableStateFlow<ServerStatus>(ServerStatus.Stopped)

        /** 进程内运行状态：设置页据此渲染开关与状态行 */
        val status: StateFlow<ServerStatus> = _status
    }
}
