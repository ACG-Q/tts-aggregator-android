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
    private var playFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                ProvideAppContainer {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        AppNav(onPlay = ::play, onToast = ::toast)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        playFile?.delete()
        playFile = null
        super.onDestroy()
    }

    /** 首页"立即朗读"的试听：MP3 落盘（后台线程）后用 MediaPlayer 异步播放 */
    private fun play(bytes: ByteArray) {
        Thread {
            // 每次用独立文件，避免覆盖仍在播放的音频
            val file = File(cacheDir, "tts_play_${System.currentTimeMillis()}.mp3")
            runCatching { file.writeBytes(bytes) }
                .onSuccess { runOnUiThread { startPlayer(file) } }
                .onFailure { runOnUiThread { toast("播放失败: ${it.message}") } }
        }.start()
    }

    private fun startPlayer(file: File) {
        runCatching {
            player?.release()
            playFile?.delete()
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp -> mp.start() }
                setOnErrorListener { _, what, extra ->
                    toast("播放失败（$what/$extra）")
                    true
                }
                prepareAsync()
            }
            playFile = file
        }.onFailure { toast("播放失败: ${it.message}") }
    }

    private fun toast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
