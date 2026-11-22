package com.example.my.data.ai

import android.content.SharedPreferences

/**
 * 能力网关内部分级用的枚举，**不再是给用户选的运行模式**。
 *
 * 旧版的「混合 / 本地模型 / API 模型」三选一已移除：对话框底部选哪个模型就用哪个模型，
 * 由 AppState.runMode 唯一决定。这里仅保留 LOCAL / API 两档语义，供 AgentRuntime
 * 判断「是否允许联网能力」，HYBRID 只为兼容旧 prefs 与旧协议参数而保留。
 */
enum class ChatMode(val title: String, val desc: String) {
    HYBRID("混合", "旧版遗留值，等同 API"),
    LOCAL("本地模型", "当前选用本地模型：不联网的能力拦截"),
    API("API 模型", "当前选用云端模型");

    companion object {
        fun of(name: String?): ChatMode = entries.firstOrNull { it.name == name } ?: HYBRID
    }
}

/**
 * 「AI 模型能力」开关集合。
 *
 * 视觉、语音能力全部跑在本机：视觉能力来自已打包进 APK 的 ML Kit 模型，
 * 语音输入来自系统 SpeechRecognizer，语音播报来自系统 TTS。
 * 只有本地大模型需要额外下载模型包，未就绪时由调用方自动退回云端。
 * evidenceToolLoop / mcpSearch 是云端工具循环开关：前者按需查临时证据库，
 * 后者按需查 GitHub 免费开源项目。
 *
 * 默认值遵循「开箱即用但不过度打扰」：视觉识别默认开，翻译与播报默认关
 * （翻译模型首次使用要下载，播报会出声，交给用户自己决定）。
 */
data class AiCapabilities(
    /** 旧版遗留字段：已无 UI 入口，实际路由见 AppState.runMode / effectiveChatMode */
    val chatMode: ChatMode = ChatMode.HYBRID,
    /** 图像智能标签 */
    val visionLabels: Boolean = true,
    /** OCR 中英文文字识别（菜单、票根、路牌） */
    val visionOcr: Boolean = true,
    /** 人脸检测（合影人数 / 人像提示） */
    val visionFace: Boolean = true,
    /** 条码 / 二维码识别 */
    val visionBarcode: Boolean = true,
    /** 端侧图文翻译（中↔英） */
    val visionTranslate: Boolean = false,
    /** 语音输入 */
    val voiceInput: Boolean = true,
    /** 语音播报（朗读 AI 回复） */
    val voiceTts: Boolean = false,
    /** 语音识别只走离线引擎（不把声音发到云端） */
    val voiceOfflineOnly: Boolean = false,
    /**
     * 「边思考边查」：云端 OpenAI 兼容模型可以在 ReAct 循环里调用 lookup_evidence，
     * 按需查询本轮临时证据库。默认关，开启后会多 1~3 轮请求；本地模型自动走预检索兜底。
     */
    val evidenceToolLoop: Boolean = false,
    /**
     * 「MCP 搜索」：开启后云端模型可调用 search_open_source_projects，
     * 按关键词/语言检索 GitHub 上带明确 License 的免费开源项目。
     */
    val mcpSearch: Boolean = false
) {
    /** 是否开启了任意一项视觉能力 */
    val anyVision: Boolean
        get() = visionLabels || visionOcr || visionFace || visionBarcode || visionTranslate

    companion object {
        private const val KEY_LOCAL_CHAT = "ai_cap_local_chat"
        private const val KEY_CHAT_MODE = "ai_cap_chat_mode"
        private const val KEY_VISION_LABELS = "ai_cap_vision_labels"
        private const val KEY_VISION_OCR = "ai_cap_vision_ocr"
        private const val KEY_VISION_FACE = "ai_cap_vision_face"
        private const val KEY_VISION_BARCODE = "ai_cap_vision_barcode"
        private const val KEY_VISION_TRANSLATE = "ai_cap_vision_translate"
        private const val KEY_VOICE_INPUT = "ai_cap_voice_input"
        private const val KEY_VOICE_TTS = "ai_cap_voice_tts"
        private const val KEY_VOICE_OFFLINE = "ai_cap_voice_offline"
        private const val KEY_EVIDENCE_TOOL_LOOP = "ai_cap_evidence_tool_loop"
        private const val KEY_MCP_SEARCH = "ai_cap_mcp_search"

        fun load(prefs: SharedPreferences): AiCapabilities {
            val d = AiCapabilities()
            return AiCapabilities(
                chatMode = ChatMode.of(
                    prefs.getString(
                        KEY_CHAT_MODE,
                        // 老版本只有「本地优先」布尔开关：打开过就迁到混合，没打开过也迁到混合
                        // （混合在本地未就绪时等价于纯云端，等于没有行为变化）
                        if (prefs.getBoolean(KEY_LOCAL_CHAT, false)) ChatMode.HYBRID.name else d.chatMode.name
                    )
                ),
                visionLabels = prefs.getBoolean(KEY_VISION_LABELS, d.visionLabels),
                visionOcr = prefs.getBoolean(KEY_VISION_OCR, d.visionOcr),
                visionFace = prefs.getBoolean(KEY_VISION_FACE, d.visionFace),
                visionBarcode = prefs.getBoolean(KEY_VISION_BARCODE, d.visionBarcode),
                visionTranslate = prefs.getBoolean(KEY_VISION_TRANSLATE, d.visionTranslate),
                voiceInput = prefs.getBoolean(KEY_VOICE_INPUT, d.voiceInput),
                voiceTts = prefs.getBoolean(KEY_VOICE_TTS, d.voiceTts),
                voiceOfflineOnly = prefs.getBoolean(KEY_VOICE_OFFLINE, d.voiceOfflineOnly),
                evidenceToolLoop = prefs.getBoolean(KEY_EVIDENCE_TOOL_LOOP, d.evidenceToolLoop),
                mcpSearch = prefs.getBoolean(KEY_MCP_SEARCH, d.mcpSearch)
            )
        }

        fun save(prefs: SharedPreferences, c: AiCapabilities) {
            prefs.edit()
                .putString(KEY_CHAT_MODE, c.chatMode.name)
                .putBoolean(KEY_VISION_LABELS, c.visionLabels)
                .putBoolean(KEY_VISION_OCR, c.visionOcr)
                .putBoolean(KEY_VISION_FACE, c.visionFace)
                .putBoolean(KEY_VISION_BARCODE, c.visionBarcode)
                .putBoolean(KEY_VISION_TRANSLATE, c.visionTranslate)
                .putBoolean(KEY_VOICE_INPUT, c.voiceInput)
                .putBoolean(KEY_VOICE_TTS, c.voiceTts)
                .putBoolean(KEY_VOICE_OFFLINE, c.voiceOfflineOnly)
                .putBoolean(KEY_EVIDENCE_TOOL_LOOP, c.evidenceToolLoop)
                .putBoolean(KEY_MCP_SEARCH, c.mcpSearch)
                .apply()
        }
    }
}

/** 本地大模型链路状态（设置页与对话页共用同一套文案） */
enum class LocalLlmState {
    /** 原生库没加载起来：设备 ABI 不在 arm64-v8a / armeabi-v7a 之内 */
    ENGINE_MISSING,

    /** 引擎在，但 Qwen2.5-1.5B 模型包还没导入 */
    ENGINE_READY_MODEL_MISSING,

    /** 引擎与模型都齐了，可以本地推理 */
    READY;

    val title: String
        get() = when (this) {
            ENGINE_MISSING -> "引擎未加载"
            ENGINE_READY_MODEL_MISSING -> "模型未导入"
            READY -> "已就绪"
        }
}
