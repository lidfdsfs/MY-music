package com.example.my.data

import org.json.JSONArray
import org.json.JSONObject

/** 用户可接入的 API 协议族 */
enum class AiFormat(val label: String, val desc: String) {
    OPENAI("OpenAI 兼容", "OpenAI / 智谱 / DeepSeek / 月之暗面 / 通义 / Ollama 等"),
    ANTHROPIC("Anthropic", "Claude 官方 /v1/messages"),
    GEMINI("Google Gemini", "generativelanguage 官方接口");

    companion object {
        fun of(name: String?): AiFormat = entries.firstOrNull { it.name == name } ?: OPENAI
    }
}

/** 云端模型的用途：对话 / 图片生成 / 视频生成，三类各自独立选择和调用 */
enum class AiModelKind(val zh: String, val en: String) {
    CHAT("对话", "Chat"),
    IMAGE("图片生成", "Image"),
    VIDEO("视频生成", "Video");

    companion object {
        fun of(name: String?): AiModelKind =
            entries.firstOrNull { it.name == name } ?: CHAT
    }
}

/**
 * 一个端点能做的事情，可多选：同一个文本模型可能同时支持看图，
 * 保存模型时把这些能力勾上，分配页只显示真正能做这件事的模型。
 */
enum class AiCapability(val zh: String, val en: String, val kind: AiModelKind) {
    TEXT("文本对话", "Text chat", AiModelKind.CHAT),
    VISION("图像识别", "Vision", AiModelKind.CHAT),
    IMAGE_GEN("生成图片", "Image generation", AiModelKind.IMAGE),
    VIDEO_GEN("生成视频", "Video generation", AiModelKind.VIDEO);

    companion object {
        fun of(name: String?): AiCapability? = entries.firstOrNull { it.name == name }
    }
}

/**
 * 一个可用的模型端点。用户可以保存多条（工作用一个、备用一个），随时切换。
 * baseUrl 只写到版本号（如 https://api.deepseek.com/v1），具体路径由格式推导，
 * 这样同一份配置换协议时不用改地址。
 */
data class AiEndpoint(
    val id: Long,
    val name: String,
    val format: AiFormat,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    /** 视觉模型；留空则复用 model（部分服务商同一个模型就能看图） */
    val visionModel: String = "",
    /** 图片生成模型；留空则复用 model。智谱等平台聊天与绘图模型名不同时填这里 */
    val imageModel: String = "",
    /** 视频生成模型；留空则复用 model */
    val videoModel: String = "",
    /** 内置端点：不可删除，作为开箱可用的兜底 */
    val builtin: Boolean = false,
    /** 对话模型上下文窗口（tokens）。由模型自身能力决定，用户不可修改 */
    val contextTokens: Int = 200_000,
    /** 对话模型单次最大输出（tokens），同样由模型能力决定 */
    val maxOutputTokens: Int = 4096,
    /** 视觉模型上下文窗口（tokens） */
    val visionContextTokens: Int = 8_000,
    /** 视觉模型单次最大输出（tokens）；glm-4v-flash 上限为 1024 */
    val visionMaxOutputTokens: Int = 1024,
    /** 端点用途：对话 / 图片 / 视频（兼容旧数据；实际能力看 capabilities） */
    val kind: AiModelKind = AiModelKind.CHAT,
    /** 图片生成接口路径；留空用默认 /images/generations */
    val mediaPath: String = "",
    /** 视频生成接口路径；留空用默认 /videos */
    val videoPath: String = "",
    /** 这个端点支持的能力，可多选；为空时按 kind 兼容旧数据 */
    val capabilities: Set<AiCapability> = emptySet()
) {
    val vision: String get() = visionModel.ifBlank { model }
    val image: String get() {
        if (imageModel.isNotBlank()) return imageModel
        val m = model.trim()
        // 智谱同一把 Key：聊天常用 glm-4.x-flash，绘图是 cogview-3-flash。
        // 老配置没有单独图片模型名时自动切换，避免把对话模型发到 /images/generations。
        val zhipuTextModel = baseUrl.contains("bigmodel.cn", ignoreCase = true) &&
            (m.startsWith("glm-4", ignoreCase = true) || m.startsWith("glm-3", ignoreCase = true))
        return if (zhipuTextModel) "cogview-3-flash" else m
    }
    val video: String get() = videoModel.ifBlank { model }
    val ready: Boolean get() = model.isNotBlank() && (apiKey.isNotBlank() || builtin)
    val base: String get() = baseUrl.trim().trimEnd('/')

    /**
     * 拼接生成接口地址。支持：
     * - baseUrl 只写到 /v4，自定义路径留空，默认加 /images/generations；
     * - baseUrl 已经写到完整 /images/generations，不再重复拼接；
     * - 自定义路径直接填完整 URL。
     */
    fun endpointUrl(path: String, defaultPath: String): String {
        val custom = path.trim()
        if (custom.startsWith("http://") || custom.startsWith("https://")) return custom
        val def = defaultPath.trim().let { if (it.startsWith("/")) it else "/$it" }
        val p = (custom.ifBlank { def }).let { if (it.startsWith("/")) it else "/$it" }
        return if (base.endsWith(p)) base else base + p
    }

    /**
     * 实际能力：新数据直接读 capabilities；旧数据按 kind 推导，
     * 这样升级后原来保存的对话/图片/视频模型不会突然失效。
     */
    val effectiveCapabilities: Set<AiCapability>
        get() = when {
            capabilities.isNotEmpty() -> capabilities
            kind == AiModelKind.IMAGE -> setOf(AiCapability.IMAGE_GEN)
            kind == AiModelKind.VIDEO -> setOf(AiCapability.VIDEO_GEN)
            // 旧版对话端点没有能力字段，之前看图对话会把图片直接发给 model，
            // 这里保留「文本 + 图像识别」以免升级后旧配置突然不能看图；
            // 新保存的模型会写入用户实际勾选的能力。
            else -> setOf(AiCapability.TEXT, AiCapability.VISION)
        }

    fun supports(capability: AiCapability): Boolean =
        effectiveCapabilities.contains(capability)

    /** 主分类：能力里只要有文本/看图就算对话模型，其次图片、视频 */
    val primaryKind: AiModelKind
        get() = when {
            supports(AiCapability.TEXT) || supports(AiCapability.VISION) -> AiModelKind.CHAT
            supports(AiCapability.IMAGE_GEN) -> AiModelKind.IMAGE
            supports(AiCapability.VIDEO_GEN) -> AiModelKind.VIDEO
            else -> kind
        }

    /** 视觉调用：换模型，同时套用视觉模型自己的上下文与输出上限 */
    val visionEndpoint: AiEndpoint
        get() = copy(model = vision, contextTokens = visionContextTokens, maxOutputTokens = visionMaxOutputTokens)
}

object AiPresets {

    /**
     * 云端模型端点不再内置：App 只保留端侧本地模型（需用户自行下载/导入），
     * 所有云端对话 / 视觉 / OCR 能力都要求用户在「模型管理」里自行接入 API 端点。
     * 保留 builtin 数据类仅作向后兼容占位，不再携带任何内置 Key 或可用地址。
     */
    val builtin = AiEndpoint(
        id = 1L,
        name = "未接入云端模型",
        format = AiFormat.OPENAI,
        baseUrl = "",
        apiKey = "",
        model = "",
        visionModel = "",
        builtin = false,
        contextTokens = 200_000,
        maxOutputTokens = 4096,
        visionContextTokens = 8_000,
        visionMaxOutputTokens = 1024
    )

    /** 当前是否已接入任意一个可用的云端文本端点 */
    fun hasCloudEndpoint(list: List<AiEndpoint>): Boolean =
        list.any { it.ready && !it.builtin && it.supports(AiCapability.TEXT) }

    fun encode(list: List<AiEndpoint>): String {
        val arr = JSONArray()
        list.forEach { e ->
            val caps = JSONArray()
            e.effectiveCapabilities.forEach { caps.put(it.name) }
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("name", e.name)
                    .put("format", e.format.name)
                    .put("baseUrl", e.baseUrl)
                    .put("apiKey", e.apiKey)
                    .put("model", e.model)
                    .put("visionModel", e.visionModel)
                    .put("imageModel", e.imageModel)
                    .put("videoModel", e.videoModel)
                    .put("builtin", e.builtin)
                    .put("kind", e.kind.name)
                    .put("mediaPath", e.mediaPath)
                    .put("videoPath", e.videoPath)
                    .put("capabilities", caps)
            )
        }
        return arr.toString()
    }

    /**
     * 解析用户保存的自定义端点。不再自动追加内置端点：
     * 没有保存任何端点时返回空列表，界面据此引导用户自行接入。
     */
    fun decode(raw: String?): List<AiEndpoint> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            // 旧数据里可能出现重复 id（例如历史导入/云端配置合并），
            // LazyColumn 以 ep_<id> 作 key 时会直接崩。这里在解码阶段修成唯一 id。
            val usedIds = mutableSetOf<Long>()
            var fallbackId = 1L
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val capsArr = o.optJSONArray("capabilities")
                val caps = buildSet {
                    if (capsArr != null) {
                        for (ci in 0 until capsArr.length()) {
                            AiCapability.of(capsArr.optString(ci))?.let { add(it) }
                        }
                    }
                }
                var id = o.optLong("id", 0L)
                if (id <= 0L || !usedIds.add(id)) {
                    while (usedIds.contains(fallbackId)) fallbackId++
                    id = fallbackId
                    usedIds.add(id)
                }
                AiEndpoint(
                    id = id,
                    name = o.optString("name", "自定义模型"),
                    format = AiFormat.of(o.optString("format")),
                    baseUrl = o.optString("baseUrl", ""),
                    apiKey = o.optString("apiKey", ""),
                    model = o.optString("model", ""),
                    visionModel = o.optString("visionModel", ""),
                    imageModel = o.optString("imageModel", ""),
                    videoModel = o.optString("videoModel", ""),
                    builtin = false,
                    kind = AiModelKind.of(o.optString("kind")),
                    mediaPath = o.optString("mediaPath", ""),
                    videoPath = o.optString("videoPath", ""),
                    capabilities = caps
                )
            }
        }.getOrDefault(emptyList())
    }

    // ---------- OCR（文字识别）独立端点：用户自行接入，与对话模型分开配置 ----------

    /** OCR 端点配置；baseUrl/apiKey/model 留空表示未接入，OCR 回退端侧识别（若可用） */
    data class OcrEndpoint(
        val baseUrl: String = "",
        val apiKey: String = "",
        val model: String = ""
    ) {
        val ready: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()
        val base: String get() = baseUrl.trim().trimEnd('/')

        fun encode(): String = JSONObject()
            .put("baseUrl", baseUrl)
            .put("apiKey", apiKey)
            .put("model", model)
            .toString()

        companion object {
            fun decode(raw: String?): OcrEndpoint {
                if (raw.isNullOrBlank()) return OcrEndpoint()
                return runCatching {
                    val o = JSONObject(raw)
                    OcrEndpoint(
                        baseUrl = o.optString("baseUrl", ""),
                        apiKey = o.optString("apiKey", ""),
                        model = o.optString("model", "")
                    )
                }.getOrDefault(OcrEndpoint())
            }
        }
    }
}

/** 单个模型维度的累计用量 */
data class ModelUsage(
    val requests: Int = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    /** 其中有多少次是服务商没返回 usage、由本地估算的 */
    val estimated: Int = 0
) {
    val totalTokens: Long get() = promptTokens + completionTokens
}

/**
 * AI 用量统计。只统计本机发起的请求（不上传），
 * 服务商返回 usage 时用真实值，没返回时按字符数估算并单独计数，
 * 界面上会标注「含估算」，避免把估算值当成账单。
 */
data class AiUsage(
    val requests: Int = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val estimated: Int = 0,
    val since: Long = 0L,
    val byModel: Map<String, ModelUsage> = emptyMap()
) {
    val totalTokens: Long get() = promptTokens + completionTokens

    fun add(model: String, prompt: Long, completion: Long, isEstimate: Boolean): AiUsage {
        val prev = byModel[model] ?: ModelUsage()
        val next = prev.copy(
            requests = prev.requests + 1,
            promptTokens = prev.promptTokens + prompt,
            completionTokens = prev.completionTokens + completion,
            estimated = prev.estimated + if (isEstimate) 1 else 0
        )
        return copy(
            requests = requests + 1,
            promptTokens = promptTokens + prompt,
            completionTokens = completionTokens + completion,
            estimated = estimated + if (isEstimate) 1 else 0,
            since = if (since == 0L) System.currentTimeMillis() else since,
            byModel = byModel + (model to next)
        )
    }

    companion object {
        fun encode(u: AiUsage): String {
            val models = JSONObject()
            u.byModel.forEach { (k, v) ->
                models.put(
                    k,
                    JSONObject()
                        .put("requests", v.requests)
                        .put("prompt", v.promptTokens)
                        .put("completion", v.completionTokens)
                        .put("estimated", v.estimated)
                )
            }
            return JSONObject()
                .put("requests", u.requests)
                .put("prompt", u.promptTokens)
                .put("completion", u.completionTokens)
                .put("estimated", u.estimated)
                .put("since", u.since)
                .put("byModel", models)
                .toString()
        }

        fun decode(raw: String?): AiUsage {
            if (raw.isNullOrBlank()) return AiUsage()
            return runCatching {
                val o = JSONObject(raw)
                val models = o.optJSONObject("byModel")
                val map = mutableMapOf<String, ModelUsage>()
                models?.keys()?.forEach { k ->
                    val m = models.optJSONObject(k) ?: return@forEach
                    map[k] = ModelUsage(
                        requests = m.optInt("requests"),
                        promptTokens = m.optLong("prompt"),
                        completionTokens = m.optLong("completion"),
                        estimated = m.optInt("estimated")
                    )
                }
                AiUsage(
                    requests = o.optInt("requests"),
                    promptTokens = o.optLong("prompt"),
                    completionTokens = o.optLong("completion"),
                    estimated = o.optInt("estimated"),
                    since = o.optLong("since"),
                    byModel = map
                )
            }.getOrDefault(AiUsage())
        }
    }
}
