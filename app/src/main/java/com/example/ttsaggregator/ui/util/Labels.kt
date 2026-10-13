package com.example.ttsaggregator.ui.util

/**
 * 多级选择标签映射：中文（当前语言）+ 留未来语言扩展。
 * 三级 fallback：当前语言 → 中文 → key 原文（Azure 原始 id 天然可读）。
 * Kotlin 侧结构与 demo.html 的 I18N 一一对应。
 */
object Labels {
    /** 语言（short_name 首段，如 zh-CN → zh；覆盖 Azure 全部 locale 前缀） */
    fun lang(code: String): String = LANG[code] ?: code

    /** 地区（short_name 第二段，如 zh-CN → CN） */
    fun region(code: String): String = REGION[code] ?: code

    fun gender(g: String): String = when (g) {
        "Female" -> "女声"
        "Male" -> "男声"
        else -> g
    }

    /** 风格（与 /api/v1/config 的 styles（83 项）一一对应；未收录的回落 key 原文） */
    fun style(s: String): String = STYLE[s] ?: s

    private val LANG = mapOf(
        "zh" to "中文", "en" to "英语", "ja" to "日语", "ko" to "韩语", "fr" to "法语",
        "de" to "德语", "es" to "西班牙语", "pt" to "葡萄牙语", "ru" to "俄语",
        "ar" to "阿拉伯语", "hi" to "印地语", "th" to "泰语", "vi" to "越南语",
        "it" to "意大利语", "nl" to "荷兰语", "pl" to "波兰语", "tr" to "土耳其语",
        "sv" to "瑞典语", "da" to "丹麦语", "nb" to "挪威语", "fi" to "芬兰语",
        "cs" to "捷克语", "sk" to "斯洛伐克语", "hu" to "匈牙利语", "ro" to "罗马尼亚语",
        "bg" to "保加利亚语", "el" to "希腊语", "he" to "希伯来语", "fa" to "波斯语",
        "ur" to "乌尔都语", "bn" to "孟加拉语", "ta" to "泰米尔语", "te" to "泰卢固语",
        "kn" to "卡纳达语", "ml" to "马拉雅拉姆语", "mr" to "马拉地语", "gu" to "古吉拉特语",
        "pa" to "旁遮普语", "ne" to "尼泊尔语", "si" to "僧伽罗语", "km" to "高棉语",
        "lo" to "老挝语", "my" to "缅甸语", "id" to "印尼语", "ms" to "马来语",
        "fil" to "菲律宾语", "sw" to "斯瓦希里语", "sq" to "阿尔巴尼亚语", "hr" to "克罗地亚语",
        "sr" to "塞尔维亚语", "sl" to "斯洛文尼亚语", "et" to "爱沙尼亚语", "lv" to "拉脱维亚语",
        "lt" to "立陶宛语", "uk" to "乌克兰语", "az" to "阿塞拜疆语", "ka" to "格鲁吉亚语",
        "hy" to "亚美尼亚语", "ca" to "加泰罗尼亚语", "eu" to "巴斯克语", "is" to "冰岛语",
        "af" to "南非荷兰语", "am" to "阿姆哈拉语", "be" to "白俄罗斯语", "bs" to "波斯尼亚语",
        "cy" to "威尔士语", "ga" to "爱尔兰语", "gl" to "加利西亚语", "jv" to "爪哇语",
        "mk" to "马其顿语", "mn" to "蒙古语", "ps" to "普什图语", "uz" to "乌兹别克语",
        "zu" to "祖鲁语", "ht" to "海地克里奥尔语", "rw" to "卢旺达语", "yo" to "约鲁巴语",
        "sn" to "绍纳语", "so" to "索马里语", "xh" to "科萨语", "ig" to "伊博语",
    )

    private val REGION = mapOf(
        "CN" to "中国 · 简体", "TW" to "中国台湾 · 繁体", "HK" to "中国香港 · 繁体",
        "MO" to "中国澳门 · 繁体", "SG" to "新加坡", "MY" to "马来西亚",
        "US" to "美国", "GB" to "英国", "AU" to "澳大利亚", "CA" to "加拿大",
        "IE" to "爱尔兰", "NZ" to "新西兰", "IN" to "印度", "PK" to "巴基斯坦",
        "BD" to "孟加拉国", "LK" to "斯里兰卡", "NP" to "尼泊尔", "PH" to "菲律宾",
        "ID" to "印度尼西亚", "VN" to "越南", "TH" to "泰国", "JP" to "日本",
        "KR" to "韩国", "FR" to "法国", "DE" to "德国", "AT" to "奥地利",
        "CH" to "瑞士", "BE" to "比利时", "NL" to "荷兰", "ES" to "西班牙",
        "MX" to "墨西哥", "AR" to "阿根廷", "CO" to "哥伦比亚", "CL" to "智利",
        "BR" to "巴西", "PT" to "葡萄牙", "IT" to "意大利", "SE" to "瑞典",
        "NO" to "挪威", "DK" to "丹麦", "FI" to "芬兰", "PL" to "波兰",
        "CZ" to "捷克", "SK" to "斯洛伐克", "HU" to "匈牙利", "RO" to "罗马尼亚",
        "BG" to "保加利亚", "GR" to "希腊", "TR" to "土耳其", "RU" to "俄罗斯",
        "UA" to "乌克兰", "IL" to "以色列", "SA" to "沙特阿拉伯", "AE" to "阿联酋",
        "EG" to "埃及", "ZA" to "南非", "NG" to "尼日利亚", "KE" to "肯尼亚",
        "QA" to "卡塔尔", "KW" to "科威特", "OM" to "阿曼", "BH" to "巴林",
        "JO" to "约旦", "LB" to "黎巴嫩", "IQ" to "伊拉克", "MA" to "摩洛哥",
        "DZ" to "阿尔及利亚", "TN" to "突尼斯", "ET" to "埃塞俄比亚", "TZ" to "坦桑尼亚",
        "UG" to "乌干达", "GH" to "加纳", "IS" to "冰岛", "HR" to "克罗地亚",
        "RS" to "塞尔维亚", "SI" to "斯洛文尼亚", "EE" to "爱沙尼亚", "LT" to "立陶宛",
        "LV" to "拉脱维亚", "AZ" to "阿塞拜疆", "GE" to "格鲁吉亚", "AM" to "亚美尼亚",
        "BY" to "白俄罗斯", "KZ" to "哈萨克斯坦", "UZ" to "乌兹别克斯坦", "AF" to "阿富汗",
        "MM" to "缅甸", "KH" to "柬埔寨", "LA" to "老挝", "MN" to "蒙古",
        "TL" to "东帝汶", "FJ" to "斐济",
    )

    private val STYLE = mapOf(
        "whispering" to "耳语", "encouraging" to "鼓励", "saddisappointed" to "悲伤失望",
        "customer-service" to "客服", "assassin" to "冷酷", "chat-casual" to "随意聊天",
        "sad" to "悲伤", "angry" to "生气", "regretful" to "懊悔",
        "shouting" to "呼喊", "surprised" to "惊讶", "serious" to "严肃",
        "assistant" to "助手", "shy" to "害羞", "chat" to "闲聊",
        "debating" to "辩论", "sorry" to "抱歉", "sentimental" to "多愁善感",
        "gentle" to "温柔", "cute" to "可爱", "newscast" to "新闻播报",
        "friendlycheerful" to "友好愉快", "prince" to "王子", "game-narrator" to "游戏解说",
        "disgusted" to "厌恶", "caringempathy" to "关怀共情", "voice-assistant" to "语音助手",
        "lonely" to "孤独", "narration-relaxed" to "轻松旁白", "story" to "故事",
        "calm" to "平静", "depressed" to "低沉", "sports-commentary" to "体育解说",
        "fearful" to "恐惧", "happy" to "快乐", "jealous" to "嫉妒",
        "newscast-formal" to "正式新闻", "affectionate" to "深情", "envious" to "羡慕",
        "cheerful" to "愉快", "confused" to "困惑", "excited" to "兴奋",
        "tired" to "疲惫", "geomancer" to "风水师", "documentary-narration" to "纪录片旁白",
        "nervous" to "紧张", "newscast-casual" to "轻松新闻", "curious" to "好奇",
        "comforting" to "安慰", "cavalier" to "豪迈", "lyrical" to "抒情",
        "sports-commentary-excited" to "激动解说", "embarrassed" to "尴尬", "joyful" to "喜悦",
        "softvoice" to "轻声细语", "customerservice" to "客服", "funny" to "搞笑",
        "disappointed" to "失望", "captain" to "船长", "livecommercial" to "直播带货",
        "hopeful" to "希望", "terrified" to "惊恐", "narration-professional" to "专业旁白",
        "conversation" to "对话", "adventurous" to "冒险", "nostalgic" to "怀旧",
        "strict" to "严厉", "poetry-reading" to "诗歌朗诵", "empathetic" to "共情",
        "friendly" to "友好", "unfriendly" to "冷漠", "anxious" to "焦虑",
        "live-commercial" to "直播带货", "complaining" to "抱怨", "advertisement-upbeat" to "活力广告",
        "news" to "新闻", "determined" to "坚定", "reflective" to "沉思",
        "guilty" to "内疚", "poet" to "诗人", "story-telling" to "讲故事",
        "disgruntled" to "不满", "relieved" to "如释重负",
        // 部分部署用驼峰写法，一并覆盖
        "narrationRelaxed" to "轻松旁白", "narrationProfessional" to "专业旁白",
        "poetryReading" to "诗歌朗诵", "storyTelling" to "讲故事",
        "sadDisappointed" to "悲伤失望", "newscastFormal" to "正式新闻",
        "newscastCasual" to "轻松新闻", "sportsCommentary" to "体育解说",
        "documentaryNarration" to "纪录片旁白", "customerService" to "客服",
        "default" to "默认",
    )
}
