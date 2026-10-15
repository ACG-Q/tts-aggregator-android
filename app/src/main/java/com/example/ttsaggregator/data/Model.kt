package com.example.ttsaggregator.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** 部署的 API 世代（探测时识别，展示为节点标签） */
enum class ApiVersion {
    // 新版：有 /api/v1 路由（wanjq 等）
    V1,
    // 老版：只有 /tts、/voices 兼容路由（66421ee 等）
    LEGACY,
    // 探测未确定
    UNKNOWN,
}

/** TTS 节点（聚合的核心实体） */
@Serializable
data class TtsNode(
    @SerialName("id") val id: String = UUID.randomUUID().toString(),
    @SerialName("baseUrl") val baseUrl: String,
    @SerialName("label") val label: String = "",
    @SerialName("keyless") val keyless: Boolean = false,
    @SerialName("apiKey") val apiKey: String? = null,
    @SerialName("health") val health: Health = Health.UNKNOWN,
    @SerialName("lastChecked") val lastChecked: Long = 0,
    @SerialName("defaultVoice") val defaultVoice: String = "",
    @SerialName("voiceCount") val voiceCount: Int = 0,
    @SerialName("failCount") val failCount: Int = 0,          // 连续合成失败次数
    @SerialName("cooldownUntil") val cooldownUntil: Long = 0,  // 冷却截止时间戳（0=无）
    @SerialName("disabled") val disabled: Boolean = false,     // 用户手动禁用（不参与自动调度）
    @SerialName("apiVersion") val apiVersion: ApiVersion = ApiVersion.UNKNOWN,
) {
    enum class Health { OK, FAIL, UNKNOWN }

    val ttsEndpoint: String get() = "$baseUrl/tts"
    val v1TtsEndpoint: String get() = "$baseUrl/api/v1/tts"
    val voicesEndpoint: String get() = "$baseUrl/api/v1/voices"

    fun isCoolingDown(now: Long = System.currentTimeMillis()): Boolean = cooldownUntil > now
}

/**
 * Azure 中文音色英文人名 → 中文人名。
 * 覆盖经典音色与新一代 Dragon HD 系列（它们 local_name 为空、display_name 为纯英文，
 * 如 "Xiaoxiao Dragon HD Flash Latest" → "晓晓 Dragon HD Flash Latest"）。
 */
private val VOICE_NAME_ZH = mapOf(
    "Xiaoxiao" to "晓晓", "Xiaochen" to "晓辰", "Xiaoyi" to "晓伊",
    "Xiaorui" to "晓睿", "Xiaozhou" to "晓舟", "Xiaomo" to "晓墨",
    "Xiaohan" to "晓涵", "Xiaorou" to "晓柔", "Xiaobei" to "晓北",
    "Xiaoni" to "晓妮", "Xiaozhen" to "晓臻", "Xiaoxuan" to "晓萱",
    "Xiaomeng" to "晓梦", "XiaobeiNeural" to "晓北", "Xiaojiao" to "晓娇",
    "Yunxi" to "云希", "Yunyang" to "云扬", "Yunxia" to "云夏",
    "Yunfeng" to "云枫", "Yunze" to "云泽", "Yunjian" to "云健",
    "Yunxiao" to "云骁", "Yunfan" to "云帆", "Yunyi" to "云逸",
    "Yunhao" to "云皓", "Yunjin" to "云瑾", "Yunrui" to "云睿",
    "HsiaoChen" to "晓臻", "HsiaoYu" to "晓雅", "HsiaoTing" to "晓婷",
    "YunJhe" to "云哲", "YunJhih" to "云智", "YunZe" to "云泽",
    "HiuMaan" to "晓曼", "HiuGaai" to "晓佳", "HiuGei" to "晓儿",
    "HiuSin" to "晓芯", "HiuChing" to "晓晴", "WanLung" to "云龙",
    "WanYiu" to "云尧", "HoYan" to "可欣",
)

/** 系列/产品词 → 中文（display_name 中人名之后的部分逐词翻译） */
private val VOICE_SERIES_ZH = mapOf(
    "Dragon" to "龙", "HD" to "高清", "Flash" to "闪速", "Latest" to "最新版",
    "Multilingual" to "多语种", "Instant" to "即时", "Turbo" to "加速版",
    "Lite" to "轻量版", "Pro" to "专业版", "Nano" to "微型版",
    "Standard" to "标准版", "Enhanced" to "增强版", "Natural" to "自然",
    "Fast" to "快速", "New" to "新版", "HighQuality" to "高品质",
    "Narrator" to "旁白", "Reader" to "朗读", "Mini" to "迷你版",
)

/** /api/v1/voices 单条（只取需要的字段，ignoreUnknownKeys 容错） */
@Serializable
data class Voice(
    @SerialName("short_name") val id: String,
    @SerialName("display_name") val name: String = "",
    /** 本地化名称（经典音色返回"晓晓"等，显示优先用它） */
    @SerialName("local_name") val localName: String = "",
    @SerialName("gender") val gender: String = "",
    @SerialName("locale") val locale: String = "",
    @SerialName("style_list") val styles: List<String>? = null, // 该声音支持的风格子集（可为空=不支持）
) {
    /**
     * 显示名三级映射（全中文）：
     * 1. local_name（服务端本地化名，如"晓晓"）
     * 2. 无 local_name 时（Dragon 新系列）：英文人名 → 中文人名 + 系列词逐词中文化
     *    "Xiaoxiao Dragon HD Flash Latest" → "晓晓 龙 高清 闪速 最新版"
     * 3. 非中文音色保留原文（Jenny / Nanami 等）
     */
    val displayName: String get() {
        val raw = localName.ifBlank { name }
        if (raw.isBlank()) return name
        // 人名前缀替换
        var base = raw
        VOICE_NAME_ZH.entries.firstOrNull { base.startsWith(it.key) }?.let { m ->
            base = m.value + base.substring(m.key.length)
        }
        // 系列词逐词翻译（命中的换中文，未命中的保留）
        return base.split(' ')
            .map { word -> VOICE_SERIES_ZH[word] ?: word }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }
}

/** 合成偏好（作用于单次合成请求） */
data class SynthPrefs(
    val voice: String = "",
    val rate: Int = 0,   // -100..100
    val pitch: Int = 0,  // -100..100
    val style: String = "",
)

/** 节点调用逻辑 */
enum class CallMode {
    /** 自动：按健康度依次尝试，失败自动切换下一个 */
    AUTO,
    /** 组：组内节点轮询，每次成功后换下一个（避免单节点被封） */
    GROUP,
    /** 手选：只用选中的节点，连续失败 3 次后停止 */
    MANUAL,
}

/** 节点池配置（持久化） */
@Serializable
data class PoolConfig(
    val mode: CallMode = CallMode.AUTO,
    val groupIds: List<String> = emptyList(),
    val manualId: String? = null,
    val groupCursor: Int = 0,        // 组轮询游标（成功后前进）
    val manualFailCount: Int = 0,    // 手选连续失败计数
)

/** 语音参数（持久化，作用于朗读与系统 TTS） */
@Serializable
data class SynthSettings(
    val rate: Int = 0,      // -100..100
    val pitch: Int = 0,     // -100..100
    val voice: String = "", // 系统 TTS 音色（空 = 节点默认）
)

/** 本机 HTTP 服务配置（持久化；仅监听 127.0.0.1，端口由 UI 校验 1024..65535） */
@Serializable
data class LocalServerConfig(
    val enabled: Boolean = false,
    val port: Int = 8787,
)

/** 一条调用日志 */
data class CallLog(
    val id: Long = 0,
    val time: Long = System.currentTimeMillis(),
    val nodeUrl: String,
    val mode: CallMode,
    val textLen: Int,
    val attempt: Int,        // 本次调用中的第几次节点尝试
    val success: Boolean,
    val latencyMs: Long,
    val fromNode: String? = null,   // 从哪个节点切换而来
    val error: String? = null,
    val triggeredCooldown: Boolean = false,
    val textPreview: String = "",   // 文本内容前缀（详情页展示）
    val voice: String = "",         // 本次使用的音色
    val rate: Int = 0,
    val pitch: Int = 0,
)

/** 日志过滤维度（UI 与存储共用，独立于存储实现） */
enum class LogFilter { ALL, OK, FAIL, SWITCH }
