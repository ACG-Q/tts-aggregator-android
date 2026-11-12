package com.pulse.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.pulse.tts.ui.ProvideAppContainer
import com.pulse.tts.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 宿主 Activity：只负责 Compose 容器、全局播放器与 Toast。
 * 导航、业务状态全部在 Compose/ViewModel 层。
 */
class MainActivity : ComponentActivity() {

    private lateinit var playback: PlaybackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        val container = (application as App).container
        playback = PlaybackController(applicationContext, container.settings)
        playback.restoreIfNeeded()
        startLaunchProbeIfEnabled(container, isFreshLaunch = savedInstanceState == null)

        setContent {
            val appSettings by container.settings.appSettings.collectAsState()
            val darkTheme = appSettings.theme.isDark(isSystemInDarkTheme())
            AppTheme(darkTheme = darkTheme) {
                val view = LocalView.current
                val statusBarColor = MaterialTheme.colorScheme.background
                SideEffect {
                    // XML 主题固定为浅色状态栏，应用内切换深色时需要同步，否则顶部留一条白带。
                    val window = (view.context as Activity).window
                    window.statusBarColor = statusBarColor.toArgb()
                    WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = !darkTheme
                }
                ProvideAppContainer(appSettings) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        AppNav(player = playback, onToast = ::toast)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
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
            val ids = container.nodeRepository.nodes.value.map { it.id }
            if (ids.isEmpty()) return@launch
            runCatching { container.nodeRepository.probeAll(ids) }
                .onFailure { Log.w(TAG, "launch probe failed", it) }
        }
    }

    private companion object {
        const val TAG = "TTS-Agg"
    }
}
