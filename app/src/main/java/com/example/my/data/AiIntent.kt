package com.example.my.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用户这一句话的六大意图类别。
 *
 * 以前「查询 / 打开 / 设置 / 发布 / 记录 / 日常对话」是靠对话页里一长串
 * `text.contains("打开")` 之类的关键词决定的，用户换个说法就掉进错误分支。
 * 现在这六类由模型在生成回复时自己判定（<intent> 协议），代码只负责解析、执行和兜底。
 */
enum class IntentCategory(val key: String, val zh: String, val en: String) {
    QUERY("query", "查询", "Query"),
    OPEN("open", "打开", "Open"),
    SETTING("setting", "设置", "Settings"),
    PUBLISH("publish", "发布", "Publish"),
    RECORD("record", "记录", "Record"),
    CHAT("chat", "日常对话", "Chat");

    companion object {
        fun of(key: String?): IntentCategory =
            entries.firstOrNull { it.key == key?.trim()?.lowercase() } ?: CHAT

        /** 模型只给了 action、忘了给 category 时，按动作语义补一个类别 */
        fun fromAction(action: String): IntentCategory = when (action) {
            "record_footprint", "import_plan", "edit_footprint" -> RECORD
            "publish_guide", "create_guide" -> PUBLISH
            "open_page", "open_guide", "search_guide", "create_plan" -> OPEN
            "setting", "set_chat_mode", "set_local_model", "language", "app_language",
            "theme", "haptic", "settings_page" -> SETTING
            "weather_query", "weather_city", "stats", "web_search" -> QUERY
            else -> CHAT
        }
    }
}

/**
 * 一次模型判定结果。
 *
 * `action=command` 时 [kind] 沿用 AppState.applyCommand 的指令枚举，直接转成
 * [CommandIntent] 走「待确认动作卡」；创作/记录类动作则由对话页做结构化抽取。
 */
data class IntentDecision(
    val category: IntentCategory,
    val action: String,
    val target: String = "",
    val value: String = "",
    val label: String = "",
    val kind: String = "none",
    val confidence: Double = 0.0,
    /** 这句话引用了上文/现场里的哪个对象，如「刚才那篇攻略」「这个城市」 */
    val refs: String = "",
    /** 本轮是接着上一轮说的（省略了主语或动作），解参数时要回看上文 */
    val followup: Boolean = false,
    /** 原始 JSON 串：画图/足迹这类动作还要从里面取 prompt、city、name */
    val raw: String = ""
) {
    /** 打开 / 设置 / 发布 / 记录 属于要动 App 数据的操作，统一弹确认卡 */
    val needsConfirm: Boolean
        get() = action != "none" && action != "draw" && action != "video" &&
            category in setOf(
                IntentCategory.OPEN, IntentCategory.SETTING,
                IntentCategory.PUBLISH, IntentCategory.RECORD
            )

    fun asCommand(): CommandIntent? = if (action == "command") {
        CommandIntent(
            kind = kind.ifBlank { "none" },
            target = target,
            value = value,
            label = label.ifBlank { "执行操作" }
        )
    } else null
}

/** 用户手动纠正过的意图，作为 few-shot 反哺给模型，让它越用越懂这个人 */
data class IntentOverride(val text: String, val category: String)

/**
 * 「会延伸」的最小单元：一条用户看得见、点得动的下一步。
 *
 * [capability] 为空 = 纯追问（点一下直接把 [prompt] 发出去）；
 * 非空 = 直接调度能力注册表里的一项能力，走统一网关，不再是只填进输入框的文字按钮。
 */
data class NextStep(
    val label: String,
    val capability: String = "",
    val args: Map<String, String> = emptyMap(),
    val prompt: String = ""
) {
    val isAction: Boolean get() = capability.isNotBlank()
}

/** 复合请求拆出来的有序步骤；用户确认整张卡后按序执行，失败即停 */
data class AiPlanStep(
    val label: String,
    val capability: String,
    val args: Map<String, String> = emptyMap()
)

data class AiPlan(val goal: String, val steps: List<AiPlanStep>)


object AiIntent {

    private val INTENT_RE = Regex(
        "[<＜](?:intent|意图)[^>＞]*[>＞](.*?)[<＜]/(?:intent|意图)[>＞]",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val ACTION_RE = Regex(
        "[<＜](?:action|动作)[^>＞]*[>＞](.*?)[<＜]/(?:action|动作)[>＞]",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    /** 从一整段模型回复里找出意图标记；兼容旧版 <action> 协议和中英文标签 */
    fun parse(raw: String): IntentDecision? {
        if (raw.isBlank()) return null
        INTENT_RE.find(raw)?.groupValues?.getOrNull(1)?.let { json ->
            parseJson(json.trim())?.let { return it }
        }
        ACTION_RE.find(raw)?.groupValues?.getOrNull(1)?.let { json ->
            fromLegacyAction(json.trim())?.let { return it }
        }
        return null
    }

    private val NEXT_RE = Regex(
        "[<＜](?:next|下一步)[^>＞]*[>＞](.*?)[<＜]/(?:next|下一步)[>＞]",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val PLAN_RE = Regex(
        "[<＜](?:plan|计划)[^>＞]*[>＞](.*?)[<＜]/(?:plan|计划)[>＞]",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    /** 模型主动给出的「下一步」：既可以是追问，也可以是能直接执行的能力调用 */
    fun parseNext(raw: String): List<NextStep> {
        if (raw.isBlank()) return emptyList()
        val body = NEXT_RE.find(raw)?.groupValues?.getOrNull(1)?.trim() ?: return emptyList()
        return runCatching {
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val label = o.optString("label").trim().take(24)
                if (label.isBlank()) return@mapNotNull null
                NextStep(
                    label = label,
                    capability = o.optString("cap").trim(),
                    args = o.optJSONObject("args")?.toFlatMap() ?: emptyMap(),
                    prompt = o.optString("prompt").trim().ifBlank { label }
                )
            }.take(4)
        }.getOrDefault(emptyList())
    }

    /** 复合请求的多步计划；不合法/超长一律丢弃，避免模型自造一堆危险动作 */
    fun parsePlan(raw: String): AiPlan? {
        if (raw.isBlank()) return null
        val body = PLAN_RE.find(raw)?.groupValues?.getOrNull(1)?.trim() ?: return null
        return runCatching {
            val o = JSONObject(body)
            val arr = o.optJSONArray("steps") ?: return@runCatching null
            val steps = (0 until minOf(arr.length(), 5)).mapNotNull { i ->
                val s = arr.optJSONObject(i) ?: return@mapNotNull null
                val label = s.optString("label").trim().take(30)
                val cap = s.optString("cap").trim()
                if (label.isBlank() || cap.isBlank()) return@mapNotNull null
                AiPlanStep(label, cap, s.optJSONObject("args")?.toFlatMap() ?: emptyMap())
            }
            AiPlan(o.optString("goal").trim().take(60), steps).takeIf { it.steps.isNotEmpty() }
        }.getOrNull()
    }

    private fun JSONObject.toFlatMap(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        keys().forEach { k ->
            when (val v = opt(k)) {
                is String, is Number, is Boolean -> out[k] = v.toString()
            }
        }
        return out
    }

    /** 短句反悔：这类话不该再发给模型、更不该执行上一轮没确认的动作 */
    fun isCancellation(text: String): Boolean {
        val t = text.trim().trim('。', '！', '!', '~', '，', ',')
        return t in setOf(
            "算了", "取消", "不用了", "别弄了", "不弄了", "不要了", "撤回", "撤销上一步",
            "cancel", "算了别弄了", "先不用", "不用了谢谢"
        )
    }

    /** 继续类短句：把没做完的计划接着跑完，而不是重新问一遍 */
    fun isContinuation(text: String): Boolean {
        val t = text.trim().trim('。', '！', '!', '，', ',')
        return t in setOf("继续", "接着做", "继续吧", "下一步", "接着", "然后呢", "continue")
    }

    private fun parseJson(s: String): IntentDecision? = runCatching {
        val o = JSONObject(s)
        val action = o.optString("action").ifBlank { "none" }
        val category = if (o.has("category")) IntentCategory.of(o.optString("category"))
        else IntentCategory.fromAction(action)
        IntentDecision(
            category = category,
            action = action,
            target = o.optString("target"),
            value = o.optString("value"),
            label = o.optString("label"),
            kind = o.optString("kind").ifBlank { "none" },
            confidence = o.optDouble("confidence", 0.0),
            refs = o.optString("refs"),
            followup = o.optBoolean("followup", false),
            raw = s
        )
    }.getOrNull()

    /** 老协议 <action>{"type":"footprint","city":"重庆",...}</action> → 新意图结构 */
    private fun fromLegacyAction(s: String): IntentDecision? = runCatching {
        val o = JSONObject(s)
        val type = o.optString("type")
        if (type.isBlank()) return null
        val (action, kind, category) = when (type) {
            "weather" -> Triple("command", "weather_query", IntentCategory.QUERY)
            "open_guide" -> Triple("command", "open_guide", IntentCategory.OPEN)
            "footprint" -> Triple("record_footprint", "none", IntentCategory.RECORD)
            "trip_import" -> Triple("import_plan", "none", IntentCategory.RECORD)
            "guide_draft" -> Triple("publish_guide", "none", IntentCategory.PUBLISH)
            "draw" -> Triple("draw", "none", IntentCategory.CHAT)
            "video" -> Triple("video", "none", IntentCategory.CHAT)
            else -> Triple("none", "none", IntentCategory.CHAT)
        }
        IntentDecision(
            category = category,
            action = action,
            kind = kind,
            target = o.optString("city").ifBlank { o.optString("target") },
            value = o.optString("value"),
            label = o.optString("label"),
            raw = s
        )
    }.getOrNull()

    /** 把 [IntentDecision] 交给 App 时用的动作名；用于日志与 UI 展示 */
    fun describe(d: IntentDecision): String =
        "${d.category.zh} · ${d.label.ifBlank { d.action }}"
}

/**
 * 意图纠错记忆。
 *
 * 用户在气泡上把「设置」改成「查询」后，这条纠正会作为示例注入下一次对话的
 * system 上下文——这是「模型判断」相比关键词匹配真正能进步的地方：同一个人
 * 的说法偏好会被记住，而不是永远靠改代码加关键词。
 */
object AiIntentMemory {
    private const val PREF = "ai_intent_memory"
    private const val KEY = "overrides"
    private const val MAX = 20

    fun load(ctx: Context): List<IntentOverride> = runCatching {
        val arr = JSONArray(
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        )
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            IntentOverride(o.optString("text"), o.optString("category"))
                .takeIf { it.text.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    fun add(ctx: Context, text: String, category: IntentCategory) {
        val clean = text.trim().take(60)
        if (clean.isBlank()) return
        val kept = load(ctx).filterNot { it.text == clean } + IntentOverride(clean, category.key)
        val arr = JSONArray()
        kept.takeLast(MAX).forEach {
            arr.put(JSONObject().put("text", it.text).put("category", it.category))
        }
        runCatching {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply()
        }
    }

    fun clear(ctx: Context) {
        runCatching {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        }
    }

    /** 注入 system 的 few-shot 片段；无纠错记录时返回 null，不浪费 token */
    fun render(list: List<IntentOverride>): String? {
        if (list.isEmpty()) return null
        val lines = list.takeLast(8).joinToString("\n") {
            "「${it.text.replace("\n", " ")}」→ ${IntentCategory.of(it.category).key}"
        }
        return "【该用户纠正过的意图判断，优先按这些示例理解他的说法】\n$lines\n" +
            "注意：以上只是这个用户的表达习惯，仍要按当前这句话的真实语义判断，不要生搬硬套。"
    }
}
