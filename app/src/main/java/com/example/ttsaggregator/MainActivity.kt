package com.example.ttsaggregator

import android.app.Activity
import android.os.Bundle
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
import com.example.ttsaggregator.navigation.AppNav
import com.example.ttsaggregator.playback.PlaybackController
import com.example.ttsaggregator.ui.ProvideAppContainer
import com.example.ttsaggregator.ui.theme.AppTheme

/**
 * 宿主 Activity：只负责 Compose 容器、全局播放器与 Toast。
 * 导航、业务状态全部在 Compose/ViewModel 层。
 */
class MainActivity : ComponentActivity() {

    private lateinit var playback: PlaybackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as App).container
        playback = PlaybackController(applicationContext, container.settings)
        playback.restoreIfNeeded()

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
}
