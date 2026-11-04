package com.pulse.tts

import android.app.Application
import android.content.Context
import com.pulse.tts.data.LogStore
import com.pulse.tts.data.NodePool
import com.pulse.tts.data.NodeRepository
import com.pulse.tts.data.NodeStore
import com.pulse.tts.data.SettingsStore
import com.pulse.tts.data.TtsApiClient
import com.pulse.tts.data.VoiceRepository

/**
 * 手写依赖容器（替代 Hilt）：
 * 本项目单例很少，一个 AppContainer 就够。
 *
 * 依赖方向（自下而上）：
 *   NodeStore / LogStore / SettingsStore   —— 存储层（SQLite / SharedPrefs）
 *   TtsApiClient                           —— 网络层（纯 HTTP，无状态存储）
 *   NodeRepository / VoiceRepository       —— 仓库层（存储 + 网络编排，暴露 StateFlow）
 *   NodePool                               —— 调度层（按配置路由合成请求）
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    // 存储层
    val settings = SettingsStore(appContext)
    val nodeStore = NodeStore(appContext)
    val logStore = LogStore(appContext)

    // 网络层
    val api = TtsApiClient()

    // 仓库层
    val nodeRepository = NodeRepository(appContext, nodeStore, api)
    val voiceRepository = VoiceRepository(appContext, settings, api) { nodeRepository.nodes.value }

    // 调度层
    val nodePool = NodePool(nodeRepository, settings, logStore, api)
}

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** 从任意 Context 取容器 */
val Context.appContainer: AppContainer
    get() = (applicationContext as App).container
