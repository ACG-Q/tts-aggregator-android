package com.pulse.tts

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.pulse.tts.navigation.AppNav
import com.pulse.tts.playback.PlaybackController
import com.pulse.tts.tts.SynthesisNotification
import com.pulse.tts.tts.SystemReadingEntry
import com.pulse.tts.ui.ProvideAppContainer
import com.pulse.tts.ui.components.ProvideAppHaptics
import com.pulse.tts.ui.theme.AppTheme
import com.pulse.tts.ui.theme.resolveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 宿主 Activity：只负责 Compose 容器、全局播放器与 Toast。
 * 导航、业务状态全部在 Compose/ViewModel 层。
 */
class MainActivity : ComponentActivity() {

    private lateinit var playback: PlaybackController

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        handleReaderIntent(intent)
        val container = (application as App).container
        requestNotificationPermissionIfNeeded()
        playback = PlaybackController(applicationContext, container.settings)
        playback.restoreIfNeeded()
        startLaunchProbeIfEnabled(container, isFreshLaunch = savedInstanceState == null)
        startUpdateCheckIfNeeded(container, isFreshLaunch = savedInstanceState == null)

        setContent {
            val appSettings by container.settings.appSettings.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val resolved = resolveTheme(appSettings.themeStyle, appSettings.themeSkin, systemDark)

            AppTheme(
                style = appSettings.themeStyle,
                skin = appSettings.themeSkin,
                systemDark = systemDark,
                reduceMotion = appSettings.reduceMotion,
            ) {
                val view = LocalView.current
                val barColor = MaterialTheme.colorScheme.background
                SideEffect {
                    // XML 主题固定为浅色状态栏，应用内切换深色时需要同步，否则顶部留一条白带。
                    val window = (view.context as Activity).window
                    window.statusBarColor = barColor.toArgb()
                    window.navigationBarColor = barColor.toArgb()
                    WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars =
                        !resolved.dark
                }
                ProvideAppHaptics(appSettings.haptics) {
                    ProvideAppContainer(appSettings) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            AppNav(
                                player = playback,
                                onToast = ::toast,
                                onExitApp = { finishAffinity() },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
        handleReaderIntent(intent)
    }

    override fun onStop() {
        val backgroundPlay = (application as App).container.settings.appSettings.value.backgroundPlay
        playback.onAppBackground(backgroundPlay)
        super.onStop()
    }

    override fun onDestroy() {
        playback.release()
        super.onDestroy()
    }

    private fun toast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        if (data.startsWith("pulsetts://sync", ignoreCase = true)) {
            com.pulse.tts.data.lansync.LanSyncEntry.offer(data)
        }
    }

    private fun handleReaderIntent(intent: Intent?) {
        if (intent == null) return
        if (!intent.getBooleanExtra(SynthesisNotification.EXTRA_OPEN_SYSTEM_READER, false)) return
        SystemReadingEntry.requestOpen()
        intent.removeExtra(SynthesisNotification.EXTRA_OPEN_SYSTEM_READER)
    }

    /**
     * 「启动时自动测试」开关：冷启动后在后台刷新一次节点健康。
     *
     * 只在 [isFreshLaunch] 为 true（非旋转/重建）时执行，避免每次重建都打一遍网络。
     */
    private fun startLaunchProbeIfEnabled(container: AppContainer, isFreshLaunch: Boolean) {
        if (!isFreshLaunch) return
        if (!container.settings.appSettings.value.autoTestOnLaunch) return
        lifecycleScope.launch(Dispatchers.IO) {
            container.nodeRepository.awaitReady()
            if (!container.networkMonitor.isOnline()) return@launch
            val ids = container.nodeRepository.nodes.value.map { it.id }
            if (ids.isEmpty()) return@launch
            runCatching { container.nodeRepository.probeAll(ids) }
                .onFailure { Log.w(TAG, "launch probe failed", it) }
        }
    }

    /** Android 13+ 通知权限：只主动申请一次，拒绝后不反复弹窗。 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val prefs = getSharedPreferences(NOTIFICATION_PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)) return
        prefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * 冷启动静默检查更新：带 6 小时节流，仅强制更新会弹窗。
     * 任何网络异常都在仓库层忽略，这里只兜底记录。
     */
    private fun startUpdateCheckIfNeeded(container: AppContainer, isFreshLaunch: Boolean) {
        if (!isFreshLaunch) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                container.updateRepository.checkOnLaunch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "launch update check failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "TTS-Agg"
        const val NOTIFICATION_PREFS = "permissions"
        const val KEY_NOTIFICATION_ASKED = "notification_asked"
    }
}
