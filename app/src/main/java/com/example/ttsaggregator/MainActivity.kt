package com.example.ttsaggregator

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
