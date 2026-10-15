package com.example.ttsaggregator

import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ttsaggregator.localserver.LocalServerService
import com.example.ttsaggregator.navigation.AppNav
import com.example.ttsaggregator.ui.ProvideAppContainer
import com.example.ttsaggregator.ui.theme.AppTheme
import java.io.File

/**
 * 宿主 Activity：只负责 Compose 容器 + 首页试听播放器。
 * 导航、业务状态全部在 Compose/ViewModel 层，Activity 不持有任何业务逻辑。
 */
class MainActivity : ComponentActivity() {

    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 通知点击（EXTRA_OPEN_SETTINGS）直达设置页；旋转重建后不再强制跳转
        val openSettings = savedInstanceState == null &&
            intent.getBooleanExtra(LocalServerService.EXTRA_OPEN_SETTINGS, false)

        setContent {
            AppTheme {
                ProvideAppContainer {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        AppNav(onPlay = ::play, onToast = ::toast, openSettings = openSettings)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    /** 首页"立即朗读"的试听：MP3 落盘后用 MediaPlayer 播放 */
    private fun play(bytes: ByteArray) {
        runOnUiThread {
            runCatching {
                player?.release()
                val file = File(cacheDir, "tts_play.mp3")
                file.writeBytes(bytes)
                player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    start()
                }
            }.onFailure { toast("播放失败: ${it.message}") }
        }
    }

    private fun toast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
