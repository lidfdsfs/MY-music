package com.example.my.data

import android.content.Context
import com.example.my.data.ai.AgentRuntime
import com.example.my.data.ai.AgentSafety
import com.example.my.data.ai.CapabilityRegistry
import com.example.my.data.ai.CapNetwork
import com.example.my.data.ai.ChatMode
import com.example.my.data.ai.LocalAiPolicy
import com.example.my.data.ai.LocalAiPrefs
import com.example.my.data.ai.LocalLlm
import com.example.my.data.ai.LocalLlmState
import com.example.my.data.search.EvidenceLookupTool
import com.example.my.data.search.OpenSourceSearch
import com.example.my.data.search.SeaFusion
import com.example.my.data.ticket.TicketDates
import com.example.my.data.ticket.TicketIntent
import com.example.my.state.AppState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 一轮对话（true = 用户） */
data class ChatTurn(val fromUser: Boolean, val text: String)

/**
 * 一轮工具调用的综合产物。
 * @param contextBlock 注入最终回答 system 的真实数据上下文；不需要工具时为 null
 * @param failures 失败/被拦截的工具说明（非空时 UI 要轻提示，且模型不得假装用了实时数据）
 */
data class ToolOutcome(
    val contextBlock: String?,
    val failures: List<String> = emptyList(),
    /**
     * 本轮票务查询的**可直接展示的原文**（车次/航班清单）。
     * 模型可能只回「正在查询」而不列结果，UI 用它做确定性兜底。
     */
    val ticketBlock: String? = null,
    /** 本轮 lookup_evidence 是否真正取到了证据（用于决定是否还要注入全量事实卡） */
    val evidenceHit: Boolean = false
) {
    val hasFailure: Boolean get() = failures.isNotEmpty()
}

/**
 * 用户这句话要对 App 做什么（语义兜底用，route 与对话页的意图分支名一一对应）。
 *
 * 关键词匹配只能覆盖「常见说法」，用户换个表达（「写进行程里面」「帮我记到计划里」）
 * 就会掉进纯聊天，模型只能嘴上说已执行。这张枚举是让模型直接判语义的最后一道网。
 */
enum class UserAction(val route: String) {
    /** 把对话里的行程/计划保存进 App 的行程列表（不管用户怎么说） */
    IMPORT_PLAN("import_plan"),

    /** 把某个地点记成足迹 / 打卡 */
    ADD_FOOTPRINT("footprint"),

    /** 把内容写成一篇攻略 */
    CREATE_GUIDE("guide"),

    /** 其他 App 操作：切设置、开页面、查天气、删行程、退出等 */
    APP_COMMAND("command"),

    /** 只是聊天、提问、要建议，不需要动 App 数据 */
    CHAT("chat")
}

/** 一次请求的 token 消耗；estimated = 服务商没返回 usage，由本地按字符估算 */
data class UsageDelta(val prompt: Long, val completion: Long, val estimated: Boolean)

/** AI 生成的单日行程 */
data class AiTripDay(
    val day: Int,
    val title: String,
    val items: List<String>,
    /** 当天最值得站的一处：位置＋最佳时段＋一句为什么；模型没给时为空 */
    val bestSpot: String = ""
)

/**
 * 全程最值得站的一处。
 *
 * 用户最想要的是「到底该站在哪儿、什么时候站」——所以单独立一个结构，
 * 而不是混在 summary 的一句话里：place 要具体到点位，moment 给时段/光线，
 * reason 说清为什么这里最好（视野、人少、当地人生活场景都算理由）。
 */
data class AiTripBestSpot(
    val place: String = "",
    val moment: String = "",
    val reason: String = ""
) {
    val isBlank: Boolean get() = place.isBlank() && moment.isBlank() && reason.isBlank()
}

/**
 * AI 对单条足迹 / 单篇攻略的「一键分析」结果。
 *
 * 足迹只用 headline + highlights 做事实总结，score/cautions/tips 不展示；
 * 攻略仍用「评分 + 亮点 + 注意 + 建议 + 标签」帮助判断参考价值。
 */
data class AiInsight(
    /** 一句话点评（带观点，不是复述原文） */
    val headline: String,
    /** 0-100 推荐指数 */
    val score: Int,
    val highlights: List<String>,
    val cautions: List<String>,
    val tips: List<String>,
    val tags: List<String>
) {
    /** 评分档位文案 */
    val verdict: String
        get() = when {
            score >= 80 -> "值得参考"
            score >= 60 -> "还不错"
            score >= 40 -> "一般"
            else -> "慎重参考"
        }
}

/**
 * AI 一键整理的足迹草稿。
 *
 * 只包含用户确认前可以先回填到表单的三个字段：
 * 标题、备注、主题标签。地点与日期仍以用户选的为准，不让模型改坐标。
 */
data class FootprintDraft(
    val title: String = "",
    val note: String = "",
    val tags: List<String> = emptyList()
)

/** AI 生成的完整行程草案（保存前可预览/调整） */
data class AiTripBlueprint(
    val title: String,
    val destination: String,
    val summary: String,
    val days: List<AiTripDay>,
    val packing: List<String>,
    val tips: List<String>,
    /** 出行建议：结合日期/天气/高峰期的综合建议 */
    val advice: List<String> = emptyList(),
    /** 出发日期 yyyy-MM-dd；对话导入时由模型从上下文推断，可能为空 */
    val startDate: String = "",
    /** 全程最值得站的一处（位置＋时段＋理由）；旧数据/模型没给时为 null */
    val bestSpot: AiTripBestSpot? = null
)

/** 图片生成结果：成功时给 url，失败时给可直接展示的原因 */
data class ImageGenOutcome(
    val url: String? = null,
    val error: String = ""
)

/** 视频生成结果：成功时给 url，失败时给可直接展示的原因 */
data class VideoGenOutcome(
    val url: String? = null,
    val error: String = ""
)

/**
 * AI 能力层，支持三种协议族：
 *  - OPENAI：/chat/completions（OpenAI、智谱、DeepSeek、Kimi、通义、Ollama…）
 *  - ANTHROPIC：/messages
 *  - GEMINI：/models/{model}:streamGenerateContent
 *
 * 所有请求都走用户当前选中的 [AiEndpoint]，Key 只在本地保存、只发往用户填写的
 * baseUrl，不经手任何中转。用法统计通过 onUsage 回调上抛，由调用方累加。
 */
object AiApi {

    val systemPrompt = """
        你是「MY足迹」App 里的 AI 助手，可以直接帮用户操作 App。
        5. 你可以引导用户使用这些能力：「记一笔足迹」「帮我做攻略」「打开足迹地图/回忆/我的/天气」「切换天气城市到 XX」「新建行程计划」「找大理的攻略」「打开某篇攻略」「点赞/收藏某攻略」「开启/关闭悬浮球、语音输入、消息通知」「切换中英文」「打开资讯/股市行情/音乐/课程表/创意工坊/票务专区」「切深色模式或浅色模式」「关掉触感反馈」「打开设置里的某个开关」「退出登录」「退出应用」；
        6. 用户告诉你重要的个人偏好（常住城市、出行方式、饮食忌口、预算、兴趣风格等）时，请在回复末尾另起一行，用「[记忆] 内容」格式简短记下一条（例如「[记忆] 用户常住重庆，偏好自由行」），方便以后沿用；每条 [记忆] 不超过 30 字。只记长期有效的稳定信息，不要记一次性的临时打算。
        7. 每次回答末尾给出「下一步」：优先用第 11 条的 <next>（可点击、能直接执行动作），确实不需要动作时才退回一行「[追问] 问题1 | 问题2 | 问题3」。要求具体到本轮的城市、景点或主题，彼此不重复，每条不超过 15 字；不要写「还需要什么帮助」这类空话。[记忆]、[追问]、<next>、<plan> 都是给 App 的控制行，会自动隐藏，不要写进正文里。
        8. 重要：你只能生成文字内容，不能直接写入或修改 App 的数据。当用户要求「导入计划/行程」「保存为行程」时，App 会自动从对话中提取行程草稿并请用户确认，你只需简短回应（如「好的，正在为你提取行程草稿…」），严禁声称「已成功导入/已保存到行程」。同样，创建行程、发布攻略等写操作都要等用户在确认卡上点击确认，确认前不要当作已完成。
        9. 【意图判断与动作协议】每条回复的最前面必须先输出一行意图标记，App 会自动隐藏这一行、不展示给用户：
           <intent>{"category":"类别","action":"动作","kind":"指令类型","target":"参数","value":"参数","label":"一句中文说明","confidence":0.9}</intent>
           标签名必须用英文 <intent> / <next> / <plan>，不要翻译成 <意图> / <下一步> / <计划>，也不要加任何额外文字。
           category 只能是以下六种之一，按这句话的真实语义判断，不要靠关键词或固定句式：
           - query 查询：问信息、查天气/票价/统计/攻略、问建议、要规划方案。绝大多数直接回答即可（action=none）；要打开天气页则用 action=command、kind=weather_query。
           - open 打开：要打开某个页面或 Tab、打开/搜索某篇攻略、新建计划、点赞收藏。
           - setting 设置：要改开关、切换语言、切换当前使用的模型、退出登录、退出应用。
           - publish 发布：要把内容写成攻略或游记发布，或生成图片、视频。
           - record 记录：要记一条足迹/打卡，或把刚才聊的行程存进行程列表。
           - chat 日常对话：闲聊、追问、翻译润色，以及不需要 App 动数据的普通问答。此时 action 必须是 none。
           action 取值：none | command | record_footprint | import_plan | publish_guide | draw | video
           - action=none：只回答、不操作 App（query 与 chat 通常用这个）
           - action=command：必须同时给出 kind，kind 只能是 weather_query / weather_city / setting / language / app_language / theme / haptic / settings_page / open / create_plan / open_guide / search_guide / like_guide / fav_guide / delete_trip / edit_footprint / set_chat_mode / set_local_model / exit_app / logout / none
             · kind=open：target 取 settings / weather / notifications / messages / guide_create / my_plans / year_report / map / memory / profile / discover / ticket_zone / music / course / news / stock / trade / creative / travelers / web_search / ai / upload / offline / trips
             · kind=setting：target 取 defaultPublic / notifEnabled / shareEnabled / ai_ball / voice_input / voice_tts（语音播报，朗读 AI 回复），value 取 true 或 false
             · kind=language：target 取 en 或 zh；kind=set_chat_mode：target 取 local（本地模型）/ api（云端模型），旧值 hybrid 等同 api
             · kind=settings_page：target 取 main / account / data / ai / ai_routing / general / about / trash / backup / language / theme / haptic / background / widget / profile / log / privacy / security / local_model / voice_library / api_model —— 用于「打开设置里的XX」
             · kind=theme：target 取 system（跟随系统）/ light（浅色）/ dark（深色）；kind=haptic：target 取 follow（跟随系统）/ on（总是开启）/ off（关闭）
             · kind=app_language：target 取 zh 或 en —— 改的是 App 界面语言（和 AI 回复语言一起变），不是翻译这句话
             · kind=weather_query / weather_city / set_local_model / delete_trip / edit_footprint / open_guide / search_guide / like_guide / fav_guide：target 填中文名称或关键词
           - action=record_footprint：可带 city 与 name（中文地名）；action=import_plan 与 publish_guide：无参数
           - action=draw 或 video：带 prompt（用用户的描述）
           label 用一句简短中文说明将要执行什么，用于向用户确认；confidence 是 0 到 1 的把握程度。可选字段：refs（这句话引用了上文/现场的哪个对象，如「刚才那篇攻略」）、followup（true 表示本轮是接着上一轮说的、省略了主语）。
           只输出一行 JSON，禁止 markdown 代码块。判断要结合上下文与常识：「把这个记一下」是 record 还是 chat 取决于上文在聊什么；「算了别弄了」是 chat；「我去过哪些城市」是 query 而不是 record。
           重要：写操作（发布、记录、设置、打开）都要等用户在确认卡上点击后才算执行，正文里简短回应即可，严禁声称「已保存/已导入/已发布」。
        10. 【现场与指代消解】system 末尾会附上「用户当前现场」：在哪一页、正在看哪篇攻略/哪个行程、天气城市、数据概况，以及「最近执行的 App 操作（真实回执）」。理解时要真正用上它：
           - 「这个」「刚才那篇」「把它…」「这边」「从这里出发」按现场对象补全；现场没有对应对象才反问，绝不凭空猜一个相似名字。
           - 「算了」「取消」「不用了」「别弄了」是放弃上一轮未确认的动作，当作 chat 简短回应即可，不要继续生成计划、也不要重复追问。
           - 「继续」「接着做」「下一步」是接着上一轮的计划或回执往下做，不要重新问一遍已经说过的信息。
           - 「再查一个」「也来一份」「换成重庆呢」沿用上一轮的城市/主题，只换动作或对象。
           - 最近操作回执是真实结果：它说失败就不要说已成功；它说成功就别再重复执行一遍；用户问「刚才那步做了吗」必须以上面为准。
           - 用户省略主语、或用「它/这个」时，在 refs 里写明引用的是什么，参数留空让 App 按现场补，不要硬编一个名字。
        11. 【主动延伸与多步任务】回答完不要停在「还有什么可以帮您」，要往前多走一步：
           - 每次回复输出一行 <next>[{"label":"≤12字","cap":"能力id或留空","args":{},"prompt":"点击后发送的话"}]</next>，最多 4 条。cap 留空 = 一条追问，点击直接发送 prompt；cap 填能力清单里的只读/写入 id（如 query_weather_city、open_guide、toggle_fav、query_footprints）= 一条能直接执行的动作，App 会走确认卡。至少给 1 条真正能推进当前话题的动作，不要全是空泛提问。
           - 只有当一句话里明确要连做两件以上 App 操作时（例如「查一下大理天气，再找找相关攻略，顺手排进行程」），才额外输出一行 <plan>{"goal":"一句话","steps":[{"label":"…","cap":"能力id","args":{}},…]}</plan>。步骤按依赖顺序排、最多 5 步、只填能力清单里存在且非破坏性的 id。App 会把整张计划卡给用户确认后按序执行，并把每步真实结果回灌给你。单步请求不要输出 <plan>。
           - 能力清单里没有的操作，明确说「这个我暂时做不到，可以手动去 XX」，不要承诺、不要假装已经执行；App 会把它记成待补的能力缺口。
    """.trimIndent()

    /** 构造 chat/completions URL：用户填完整 URL 就直接用，否则拼接 */
    fun chatUrl(ep: AiEndpoint): String {
        val base = ep.base
        return if (base.endsWith("/chat/completions") || base.endsWith("/messages")) base
        else "${base}/chat/completions"
    }

    /**
     * 兜底解压：部分自建/校内中转网关（如 chat.cqjtu.edu.cn）会把响应体 gzip 压缩，
     * 却漏发 Content-Encoding: gzip 头。标准 HTTP 客户端只在看到该头时才解压，
     * 于是拿到的是二进制，按 SSE 文本逐行解析必然失败，表现就是「每次都网络异常」。
     * 这里按 gzip 魔数（0x1f 0x8b）自行判断；服务端守规矩时行为与原先完全一致。
     */
    private fun maybeGunzip(raw: java.io.InputStream?): java.io.InputStream? {
        if (raw == null) return null
        val buf = java.io.BufferedInputStream(raw)
        buf.mark(2)
        val b0 = buf.read()
        val b1 = buf.read()
        buf.reset()
        return if (b0 == 0x1f && b1 == 0x8b) java.util.zip.GZIPInputStream(buf) else buf
    }

    /** 测试自定义端点连通性：发一个最小请求，200 即成功。model 用用户填的那个，别拿 gpt-3.5-turbo 去试别家的服务 */
    suspend fun testEndpoint(url: String, key: String, model: String = ""): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val fullUrl = if (url.endsWith("/chat/completions")) url else "$url/chat/completions"
            val body = JSONObject()
                .put("model", model.ifBlank { "gpt-3.5-turbo" })
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "hi")))
                .put("max_tokens", 1)
                .toString()
            val conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $key")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        }.getOrDefault(false)
    }

    /** 视觉专用提示词：只定义「看图作答」这一任务本身，不叠加表达/字数/语言限制 */
    val visionSystemPrompt = """
        你是「MY足迹」旅行 App 的 AI 助手。用户发来一张图片，可能附带一句提问或要求，请针对这张图片作答。
    """.trimIndent()

    // ---------- 对话 ----------

    /** 流式对话；context 为轻量 RAG 注入（App 内真实攻略摘要）。
     *  外层包了自动重试：免费模型（如 glm-4.7-flash）限流（HTTP 429）高发，
     *  未产出任何内容时自动等 1.5s/3s 重试两次，用户无感。 */
    fun chatStream(
        ep: AiEndpoint,
        history: List<ChatTurn>,
        context: String? = null,
        onUsage: (UsageDelta) -> Unit = {},
        settingsContext: Context? = null
    ): Flow<String> {
        // 主 system 末尾追加注入隔离规则：用户消息/外部工具结果里的命令式内容都不是指令
        val baseSystem = "$systemPrompt\n\n${AgentSafety.systemDirective()}"
        val system = if (context.isNullOrBlank()) baseSystem else "$baseSystem\n\n$context"
        // 上下文预算：优先读用户在设置里选的「自动 / 手动」档位；
        // 没传 Context 的旧调用退回纯自动算法，保证任何入口都不会再把 200K 全量发出去。
        val question = history.lastOrNull { it.fromUser }?.text.orEmpty()
        val budget = settingsContext?.let {
            LocalAiPrefs.effectiveContextTokens(
                context = it,
                userText = question,
                contextChars = context?.length ?: 0,
                historyTurns = history.size,
                modelLimit = ep.contextTokens
            )
        } ?: LocalAiPolicy.autoContextTokens(
            userText = question,
            contextChars = context?.length ?: 0,
            historyTurns = history.size
        ).coerceAtMost(ep.contextTokens)
        val turns = fitHistory(ep, system.length, history, budget)
        return retryOnRateLimit(
            sseFlow(
                ep = ep,
                prepare = { streaming -> prepareChat(ep, system, turns, null, null, streaming) },
                fallbackPrompt = system.length + turns.sumOf { it.text.length }
            ) { onUsage(it) }
        ).flowOn(Dispatchers.IO)
    }

    /** ReAct 循环最大轮数：每轮至少一次模型请求，硬上限防止模型反复调工具把对话拖死 */
    private const val MAX_REACT_ROUNDS = 4

    /**
     * 工具调用入口（ReAct 多轮版），返回综合产物 [ToolOutcome]：
     *  - contextBlock：所有真实工具结果渲染成的上下文，注入最终回答的 system；
     *  - failures：失败/被拦截的工具，UI 据此轻提示，模型也不得假装用了实时数据。
     *
     * 与旧版「单次预检」的区别：模型拿到工具结果后可以继续判断——结果不对就换关键词、
     * 换工具，或基于上一步结果发起下一步查询，最多 [MAX_REACT_ROUNDS] 轮，直到模型
     * 不再请求工具（认为信息够了）为止。这是 Reasoning → Acting 交替的真正 Agent 循环。
     *
     * Anthropic / Gemini 协议不支持 OpenAI tools，整条 ReAct 走不通时，退回
     * [AiTools.ruleRoute] 关键词单轮兜底，保证能力在任何模型上都还在。
     */
    suspend fun toolContext(
        ep: AiEndpoint,
        system: String,
        history: List<ChatTurn>,
        appContext: android.content.Context,
        /** 内存里的足迹快照，作为 Room 读不到时的兜底（首次安装、刚清库） */
        footprints: List<com.example.my.model.Footprint> = emptyList(),
        /** 纯本地模式要尊重用户的隐私取舍，不联网搜索 */
        allowWeb: Boolean = true,
        /**
         * 是否允许「非网页搜索」的网络能力（App 内置票务接口等）。
         * 和 [allowWeb] 分开：票务查询优先内部接口时，可以禁网页搜索但保留内部票务。
         * 默认跟随 [allowWeb]，不改变旧调用方的行为。
         */
        allowNetwork: Boolean = allowWeb,
        /** 票务类问题：不把网页搜索工具交给模型，只让模型走 App 内置票务能力。 */
        preferInternal: Boolean = false,
        /** 传入 AppState 才启用「App 内只读能力」的 Function Calling；null 时行为与旧版一致 */
        state: AppState? = null,
        chatMode: ChatMode? = null,
        /**
         * 本轮临时证据库的 key。非空且 [allowEvidenceTool] 为 true 时，
         * 把 `lookup_evidence` 交给云端模型，让它在 ReAct 循环里按需回查。
         */
        evidenceKey: String? = null,
        /** 设置里的「边思考边查」开关；本地模型/非 OpenAI 协议不会走到这里 */
        allowEvidenceTool: Boolean = false,
        /**
         * 设置里的「MCP 搜索」开关。只有开启时才把 search_open_source_projects
         * 交给云端模型；工具只查 GitHub 公开的免费开源项目，不查旅游/票务。
         */
        allowMcpSearch: Boolean = false
    ): ToolOutcome = withContext(Dispatchers.IO) {
        runCatching {
            val userText = history.lastOrNull()?.text.orEmpty()
            if (userText.isBlank()) return@runCatching ToolOutcome(null)
            // 预检门槛：寒暄、写文案不值得为它跑工具；问「我的行程/收藏/通知」也要能触发。
            val needsTool = AiTools.maybeNeedsTool(userText) ||
                (state != null && AiTools.maybeNeedsAppData(userText)) ||
                // 本轮已经检索并落盘证据时，只要开关打开就允许模型先查一次临时库；
                // 它查不查由模型决定，不再额外加一次关键词门槛。
                (allowEvidenceTool && !evidenceKey.isNullOrBlank()) ||
                (allowMcpSearch && OpenSourceSearch.looksLikeQuery(userText))
            if (!needsTool) return@runCatching ToolOutcome(null)

            // 合并工具表：外部信息工具 + App 内只读能力。
            // ReAct 是「自动执行、用户看不到确认卡」的路径，写/破坏性能力绝不在这里导出。
            // 票务查询优先内部接口时，把 web_search/weather/places 从工具表里拿掉：
            // 模型没有外部搜索工具，就不会先搜一堆携程/去哪儿入口再回答。
            val externalInfoTools = setOf(AiTools.WEB_SEARCH, AiTools.WEATHER, AiTools.PLACES)
            val combined = JSONArray()
            val builtin = AiTools.toolsJson(includeOpenSource = allowMcpSearch)
            for (i in 0 until builtin.length()) {
                val fn = builtin.optJSONObject(i)?.optJSONObject("function") ?: continue
                val name = fn.optString("name")
                if ((!allowWeb || preferInternal) && name in externalInfoTools) continue
                // 开源项目工具虽然也是联网工具，但不属于「网页搜索」；只有明确关网或
                // 票务优先时才拿掉，避免「边思考边查」把 allowWeb=false 时误伤它。
                if (name == AiTools.OPEN_SOURCE && (preferInternal || !allowNetwork)) continue
                combined.put(builtin.opt(i))
            }
            val appTools = if (state != null)
                CapabilityRegistry.toolsJson(allowNetwork = allowNetwork, readOnlyOnly = true)
            else JSONArray()
            for (i in 0 until appTools.length()) {
                val fn = appTools.optJSONObject(i)?.optJSONObject("function") ?: continue
                val name = fn.optString("name")
                // 票务查询只保留票务能力，避免模型顺手去搜歌/查天气；
                // 本地只读能力（行程、足迹、收藏等）仍保留，必要时可给票务补充上下文。
                val cap = CapabilityRegistry.find(name)
                if (preferInternal && cap?.network == CapNetwork.REQUIRES_NETWORK &&
                    cap.group != "票务") continue
                combined.put(appTools.opt(i))
            }

            // 设置开启「边思考边查」时，把临时证据库查询作为只读工具交给云端模型。
            // 本地模型 / Anthropic / Gemini 不会走到 canPreflight 的 ReAct 分支，
            // 仍走下面的预检索 + 证据库一次性注入兜底。
            if (allowEvidenceTool && !evidenceKey.isNullOrBlank()) {
                combined.put(EvidenceLookupTool.json())
            }

            // 只有 OpenAI 兼容协议认 tools 参数；没配 Key 的自定义端点也别去撞墙
            val canPreflight = ep.format == AiFormat.OPENAI && (ep.apiKey.isNotBlank() || ep.builtin)

            val failures = mutableListOf<String>()
            val infoResults = mutableListOf<AiTools.Result>()
            val capLines = mutableListOf<String>()
            val evidenceLines = mutableListOf<String>()
            var evidenceHit = false
            val mode = chatMode ?: ChatMode.HYBRID

            // 票务是「确定性数据」而不是「模型灵感」：只要规则能抽出出发地/到达地，
            // 就先由 App 直接查一次真实余票/报价，再交给模型组织语言。
            // 这样即使端侧小模型不会 Function Calling、或只回一句「这就为你查询」，
            // 也一定能拿到车次/航班清单，不会出现截图里那种只有承诺没有结果的情况。
            val autoTicket = if (preferInternal && allowNetwork) {
                TicketIntent.parse(userText, state?.weatherCity.orEmpty())
            } else null
            var autoTicketLine: String? = null
            if (autoTicket != null) {
                val tool = if (autoTicket.kind == TicketIntent.Kind.FLIGHT) {
                    "query_flights"
                } else {
                    "query_train_tickets"
                }
                val args = JSONObject()
                    .put("from_city", autoTicket.from)
                    .put("to_city", autoTicket.to)
                autoTicket.dateHint?.let { args.put("date", TicketDates.resolve(it)) }
                autoTicket.budget?.let { args.put("budget", it) }
                autoTicket.timeHint.takeIf { it.isNotBlank() }?.let { args.put("time_hint", it) }
                val before = capLines.size
                val beforeFailures = failures.size
                runOneCall(
                    AiTools.Call(tool, args), appContext, footprints, state, mode,
                    allowWeb, infoResults, capLines, failures
                )
                if (capLines.size > before && failures.size == beforeFailures) {
                    autoTicketLine = capLines.last()
                }
            }

            if (!canPreflight) {
                // 协议不支持 tools：关键词单轮兜底（含本地隐私过滤）
                val calls = AiTools.ruleRoute(
                    userText, allowWeb, allowNetwork, state?.weatherCity.orEmpty()
                ).filter {
                    allowWeb || it.name == AiTools.STATS ||
                        (allowNetwork && (it.name == "query_train_tickets" || it.name == "query_flights"))
                }.filterNot {
                    // 上面已经直接查过票务，不再重复请求同一个接口
                    autoTicketLine != null &&
                        (it.name == "query_train_tickets" || it.name == "query_flights")
                }
                calls.forEach { c ->
                    runOneCall(AiTools.guardSearchQuery(c, userText), appContext, footprints,
                        state, mode, allowNetwork, infoResults, capLines, failures)
                }
            } else if (autoTicketLine == null) {
                // ---- ReAct 多轮循环 ----
                val working = JSONArray()
                working.put(JSONObject().put("role", "system").put("content", reactRouterSystem()))
                // 只带最近 4 轮：工具路由要判断的是「这句话要不要查、接着上一步查什么」，
                // 全量历史白烧 token 又拖慢每一轮。用户消息做注入隔离包裹。
                history.takeLast(4).forEach { t ->
                    val content = if (t.fromUser) AgentSafety.wrapUserInput(t.text) else t.text
                    working.put(JSONObject()
                        .put("role", if (t.fromUser) "user" else "assistant")
                        .put("content", content))
                }
                val executedKeys = mutableSetOf<String>()
                for (round in 1..MAX_REACT_ROUNDS) {
                    val message = postToolRound(ep, working, combined)
                    if (message == null) {
                        failures.add("第 $round 轮工具路由请求失败（网络或服务异常）")
                        break
                    }
                    val toolCalls = message.optJSONArray("tool_calls")
                    // 模型不再请求工具 → 信息够了，结束循环
                    if (toolCalls == null || toolCalls.length() == 0) break

                    // 回传 assistant 消息（原样保留 tool_calls 结构，OpenAI 要求带 id）
                    working.put(JSONObject()
                        .put("role", "assistant")
                        .put("content", message.optString("content").ifBlank { null })
                        .put("tool_calls", toolCalls))

                    var anyNew = false
                    for (i in 0 until toolCalls.length()) {
                        val tc = toolCalls.optJSONObject(i) ?: continue
                        val fn = tc.optJSONObject("function") ?: continue
                        val name = fn.optString("name").ifBlank { continue }
                        val callId = tc.optString("id").ifBlank { "call_${round}_$i" }
                        val args = runCatching { JSONObject(fn.optString("arguments")) }
                            .getOrNull() ?: JSONObject()

                        // 临时证据库查询：只读本地缓存，不联网、不再跑网页检索。
                        // 结果直接回填到 working，让模型基于命中证据继续决定下一步。
                        if (name == EvidenceLookupTool.NAME) {
                            if (!allowEvidenceTool || evidenceKey.isNullOrBlank()) {
                                working.put(toolMessage(callId, "本轮未开启边思考边查，不能查询临时证据库"))
                                continue
                            }
                            val callKey = name + "|" + args.optString("category") + "|" + args.optString("keyword")
                            if (!executedKeys.add(callKey)) {
                                working.put(toolMessage(callId, "该证据查询已执行过，请换类别/关键词或直接回答"))
                                continue
                            }
                            val output = EvidenceLookupTool.run(
                                appContext,
                                evidenceKey,
                                args.optString("category"),
                                args.optString("keyword")
                            )
                            evidenceLines += output
                            if (output.startsWith("【lookup_evidence 回填】")) evidenceHit = true
                            working.put(toolMessage(callId, AgentSafety.sanitizeControl(output)))
                            anyNew = true
                            continue
                        }

                        // 模型给的检索词可能跑题（旅行助手语境下尤其容易把「XX大学校长」
                        // 写成「XX旅游攻略」），先按用户原句的核心实体校正再执行
                        val call = AiTools.guardSearchQuery(AiTools.Call(name, args), userText)

                        // 票务优先/关网/未开 MCP 搜索时，即使模型误编出这个工具名也不执行
                        if (name == AiTools.OPEN_SOURCE &&
                            (!allowMcpSearch || !allowNetwork || preferInternal)) {
                            working.put(toolMessage(callId, "本轮未开启 MCP 搜索或不允许联网，不能搜索开源项目"))
                            failures.add("「${AiTools.OPEN_SOURCE}」被设置拦截")
                            continue
                        }

                        // 纯本地隐私模式：联网工具不执行，回一条拦截说明并记录。
                        // 注意 allowWeb 与 allowNetwork 是两件事：票务查询可以只关网页搜索、
                        // 保留 App 内置票务接口；总开关关闭时 allowNetwork=false，两者都拦。
                        val isExternalInfo = name in externalInfoTools
                        if ((isExternalInfo && !allowWeb) || (!allowNetwork && name != AiTools.STATS &&
                                (!CapabilityRegistry.isAutoTool(name) ||
                                    CapabilityRegistry.find(name)?.network == CapNetwork.REQUIRES_NETWORK))) {
                            working.put(toolMessage(callId, "已按隐私设置拦截：当前不允许该工具联网"))
                            failures.add("「$name」被本地隐私模式拦截")
                            continue
                        }
                        // 防重复死循环：同一调用已执行过就不再跑，提示模型换思路或直接回答
                        if (!executedKeys.add(call.key)) {
                            working.put(toolMessage(callId, "该调用已执行过，请勿重复；信息不足请换方式或直接回答"))
                            continue
                        }
                        val beforeInfo = infoResults.size
                        val beforeCap = capLines.size
                        val beforeFailures = failures.size
                        runOneCall(call, appContext, footprints, state, mode, allowWeb,
                            infoResults, capLines, failures)
                        if (name == "query_train_tickets" || name == "query_flights") {
                            if (capLines.size > beforeCap && failures.size == beforeFailures) {
                                autoTicketLine = capLines.last()
                            }
                        }
                        // 取回这一步的输出文本回灌给模型
                        val output = when {
                            infoResults.size > beforeInfo ->
                                infoResults.last().output
                            capLines.size > beforeCap -> capLines.last()
                            else -> "该调用未返回有效结果"
                        }
                        // 外部结果在回灌前已做控制标记消毒（见 runOneCall）
                        working.put(toolMessage(callId, AgentSafety.sanitizeControl(output)))
                        anyNew = true
                    }
                    if (!anyNew) break
                }
            }

            // 渲染最终上下文块
            val blocks = mutableListOf<String>()
            AiTools.renderBlock(infoResults).takeIf { it.isNotBlank() }?.let { blocks += it }
            if (capLines.isNotEmpty()) {
                blocks += "【App 内真实数据（模型直接调用只读能力取得，必须据此回答，不要编造）】\n" +
                    capLines.joinToString("\n")
            }
            if (evidenceLines.isNotEmpty()) {
                blocks += "【临时证据库（模型按需查询取得，必须据此回答；没查到的不要编）】\n" +
                    evidenceLines.takeLast(3).joinToString("\n\n").take(3_000)
            }
            val block = blocks.joinToString("\n\n").ifBlank { null }
            ToolOutcome(block, failures.distinct(), autoTicketLine, evidenceHit)
        }.getOrElse { e ->
            // 取消（用户切页面/新建对话）要真正中断，不能被当成工具失败吞掉
            if (e is CancellationException) throw e
            ToolOutcome(null, listOf("工具流程异常：${e.message?.take(60) ?: "未知错误"}"))
        }
    }

    /** 执行一次工具调用，把结果分流收集到 infoResults / capLines / failures */
    private suspend fun runOneCall(
        call: AiTools.Call,
        appContext: android.content.Context,
        footprints: List<com.example.my.model.Footprint>,
        state: AppState?,
        mode: ChatMode,
        allowWeb: Boolean,
        infoResults: MutableList<AiTools.Result>,
        capLines: MutableList<String>,
        failures: MutableList<String>
    ) {
        val name = call.name
        try {
            if (AiTools.isInfoTool(name)) {
                val raw = AiTools.execute(name, call.args, appContext, footprints)
                // 外部/接口文本先消毒协议标记，再回灌、渲染
                val clean = AgentSafety.sanitizeControl(raw)
                if (clean.isBlank()) {
                    failures.add("「$name」没有返回有效结果")
                } else {
                    // 工具自己会在查不到时返回"请如实告诉用户没查到"的提示，按失败口径记录
                    if (clean.contains("没查到") || clean.contains("没有搜到") || clean.contains("没返回数据"))
                        failures.add("「$name」未查到有效数据")
                    infoResults.add(AiTools.Result(call, clean))
                }
            } else if (state != null && CapabilityRegistry.isAutoTool(name)) {
                val cap = CapabilityRegistry.find(name)
                val d = AgentRuntime.execute(
                    state, appContext, AgentRuntime.Call(name, call.args), mode
                )
                val text = if (d.ok) d.result?.data?.takeIf { it.isNotBlank() } ?: d.message
                else d.message
                if (!d.ok) failures.add("「${cap?.title ?: name}」执行失败：${d.message.take(40)}")
                capLines.add("【${cap?.title ?: name}】$text")
            } else {
                failures.add("「$name」不是可自动执行的只读能力")
            }
        } catch (e: Exception) {
            failures.add("「$name」执行异常：${e.message?.take(40) ?: "未知错误"}")
        }
    }

    private fun toolMessage(callId: String, content: String): JSONObject =
        JSONObject().put("role", "tool").put("tool_call_id", callId).put("content", content)

    /** 工具路由器的 system：角色约束 + 当前日期 + 注入隔离规则 */
    private fun reactRouterSystem(): String {
        val now = java.util.Calendar.getInstance()
        val year = now.get(java.util.Calendar.YEAR)
        val dateLine = "当前日期：" +
            java.text.SimpleDateFormat("yyyy年M月d日 EEEE", java.util.Locale.CHINA).format(now.time) +
            "（今年是 $year 年，「去年」= ${year - 1} 年，「前年」= ${year - 2} 年；传 year 参数按此换算）。"
        return "你是工具路由器。判断用户这句话是否需要调用给定工具，只通过 tool_calls 回答，不要输出正文。\n" +
            "工具分三类：web_search/get_footprint_stats/get_weather/search_places 查外部信息；" +
            "query_* 开头的工具读取本 App 本地数据或实时票务（query_train_tickets/query_flights），" +
            "search_music/play_music/control_music/find_music_by_description/make_mood_playlist 操作本 App 的真实音乐播放器。\n" +
            "用户问自己的记录、列表、数量或当前设置时必须调对应 query_* 工具，不要凭记忆编造；" +
            "票务查询必须优先调用 query_train_tickets / query_flights，不要先用 web_search 搜携程、去哪儿等网页入口；" +
            "只有内置票务接口返回失败，或用户问购票流程、退改签规则、官网入口时，才用 web_search。" +
            "用户问火车票/高铁/动车余票时调 query_train_tickets，问机票/航班比价时调 query_flights；" +
            "日期参数必须换算成 yyyy-MM-dd，用户说今天/明天/后天时按上面当前日期计算。" +
            "票务工具只返回真实余票/报价，禁止把携程、去哪儿等网页入口当成查询结果。" +
            "用户明确说播放/放一下/听某首歌时调 play_music；问暂停、继续、下一首、上一首时调 control_music。" +
            "用户用场景、心情或「像某首歌」描述想听什么时调 find_music_by_description，" +
            "它会提议候选并逐条在真实曲库核对，只返回核对到的歌；" +
            "用户说「做个/来个/生成一份歌单」并给了场景或心情时调 make_mood_playlist，" +
            "它会核对后组成队列直接播放；" +
            "用户只是给明确歌名问有没有这首歌，调 search_music，不要直接播放。" +
            "除以上可逆的播放控制外，其他需要改动数据的操作不要在这里调用，留给对话链路做确认。\n" +
            "调用 web_search 时，query 必须原样保留用户问题里的核心实体（机构名、人名、职务、歌名等），" +
            "禁止替换成泛化的「某地旅游攻略/行程」；允许只加限定词（最新、价格、开放时间），不许换主题。\n" +
            "拿到工具结果后，若信息不足以回答或参数有误，可以再次调用工具（换关键词/换工具），" +
            "确认信息足够后再停止调用。\n$dateLine\n\n${AgentSafety.systemDirective()}"
    }

    /** 一轮 ReAct 的非流式请求：返回 choices[0].message（可能含 tool_calls 或 content） */
    private suspend fun postToolRound(
        ep: AiEndpoint,
        messages: JSONArray,
        tools: JSONArray
    ): JSONObject? = runCatching {
        val body = JSONObject().apply {
            put("model", ep.model.ifBlank { "gpt-3.5-turbo" })
            put("messages", messages)
            put("tools", tools)
            put("tool_choice", "auto")
            put("stream", false)
            put("temperature", 0.1)
            // ReAct 一轮可能带多个工具调用，参数 JSON 也占 token，比旧预检的 256 放宽
            put("max_tokens", 800)
            // thinking 是智谱 GLM 专有参数：别的 OpenAI 兼容网关收到会 400，预检关掉省时间
            if (ep.builtin) put("thinking", JSONObject().put("type", "disabled"))
        }.toString()
        val p = Prepared(
            url = chatUrl(ep),
            headers = mapOf(
                "Content-Type" to "application/json; charset=utf-8",
                "Accept" to "application/json",
                "Accept-Encoding" to "identity",
                "Authorization" to "Bearer ${ep.apiKey.trim()}"
            ),
            body = body
        )
        // 单轮卡住会拖慢整个循环，超时比正式请求短
        val json = postJson(p, connectMs = 8_000, readMs = 25_000) ?: return@runCatching null
        json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
    }.getOrElse { e -> if (e is CancellationException) throw e else null }


    /**
     * 独立 OCR 端点识别：把图片以 base64 内联发给用户自填的视觉/识别模型，
     * 只回传图中文字。[endpoint] 未就绪时返回 null，由调用方决定是否回退端侧识别。
     */
    suspend fun ocrRemote(
        endpoint: com.example.my.data.AiPresets.OcrEndpoint,
        base64: String,
        mime: String,
        prompt: String = "请只识别并原样输出图片中的全部文字，不要解释、不要翻译、不要补充。",
        maxTokens: Int = 1024
    ): String? = withContext(Dispatchers.IO) {
        if (!endpoint.ready) return@withContext null
        runCatching {
            val url = if (endpoint.base.endsWith("/chat/completions")) endpoint.base
            else "${endpoint.base}/chat/completions"
            val payload = JSONObject().apply {
                put("model", endpoint.model)
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", prompt)
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:${mime.ifBlank { "image/jpeg" }};base64,$base64")
                            })
                        })
                    })
                }))
                put("max_tokens", maxTokens)
            }.toString()
            postJsonTo(url, payload, endpoint.apiKey)
                ?.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?.optString("content")?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /** 看图写文案：图片以 base64 内联发送，流式返回文案 */
    fun visionStream(
        ep: AiEndpoint,
        base64: String,
        mime: String,
        instruction: String,
        onUsage: (UsageDelta) -> Unit = {}
    ): Flow<String> {
        val ask = instruction.ifBlank {
            "请为这张旅行照片写一段适合发朋友圈 / 小红书的文案，轻松有画面感，可加少量 emoji，不超过 120 字。"
        }
        return retryOnRateLimit(
            sseFlow(
                ep = ep,
                prepare = { streaming ->
                    prepareChat(ep.visionEndpoint, visionSystemPrompt, listOf(ChatTurn(true, ask)), base64, mime, streaming)
                },
                fallbackPrompt = ask.length + 1200 // 图片计费与尺寸相关，这里给一个保守估算
            ) { onUsage(it) }
        ).flowOn(Dispatchers.IO)
    }

    /** AI 联网搜歌词：根据歌名+歌手返回完整歌词文本（失败返回 null） */
    suspend fun searchLyrics(ep: AiEndpoint, title: String, artist: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val prompt = """
你是歌词检索助手。请根据歌名和歌手，返回这首歌的完整歌词。
要求：
- 只输出歌词本身，不要解释、不要前言后语、不要 markdown 代码块
- 若能确定每句起始时间，用 LRC 格式逐行输出，例如 [00:12.34]歌词；不确定时间就输出纯歌词文本
- 无法确定或记不清歌词时，只回复四个字：未找到歌词。严禁编造或凭印象拼凑歌词
- 保留原歌词的换行结构，主歌副歌之间空行
                """.trimIndent()
                val artistLabel = artist.ifBlank { "未知" }
                val user = "歌名：$title\n歌手：$artistLabel"
                val content = requestOnce(ep, prompt, user) ?: return@runCatching null
                val cleaned = content
                    .replace(Regex("""^```[a-zA-Z]*\s*|\s*```$"""), "")
                    .trim()
                if (cleaned.contains("未找到") || cleaned.length < 10) null else cleaned
            }.getOrNull()
        }

    // ---------- 结构化（非流式） ----------

    suspend fun requestOnce(ep: AiEndpoint, system: String, user: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                retryRequest {
                    // 完整输入全量发送，不限制上下文大小（仅在极端逼近模型窗口时兜底）
                    val input = clampForPrompt(ep, system.length, user)
                    val p = prepareChat(ep, system, listOf(ChatTurn(true, input)), null, null, streaming = false)
                    val json = postJson(p) ?: return@retryRequest null
                    when (ep.format) {
                        AiFormat.OPENAI -> json.optJSONArray("choices")
                            ?.optJSONObject(0)?.optJSONObject("message")?.optString("content")

                        AiFormat.ANTHROPIC -> json.optJSONArray("content")
                            ?.let { arr -> (0 until arr.length()).joinToString("") { arr.optJSONObject(it)?.optString("text").orEmpty() } }

                        AiFormat.GEMINI -> geminiText(json)
                    }
                }
            }.getOrNull()
        }

    // ---------- 文生图 / 文生视频 ----------
    // 不再内置任意服务商接口：两项能力都要求用户在「模型管理」里接入对应类型的端点；
    // 对话模型、图片模型、视频模型互不混用。

    suspend fun generateImage(ep: AiEndpoint?, prompt: String): String? =
        generateImageOutcome(ep, prompt).url

    /**
     * 文生图。兼容 OpenAI Images 与智谱 CogView：
     * POST {base}/images/generations，body 为 model / prompt / size，
     * 返回 data[0].url；也兼容 b64_json 和常见 url 字段。
     *
     * 关键点：端点如果同时接了对话模型，「图片生成模型名」要单独填（如
     * 智谱聊天用 glm-4.7-flash、绘图用 cogview-3-flash），不能把对话模型名
     * 发到绘图接口。
     */
    suspend fun generateImageOutcome(ep: AiEndpoint?, prompt: String): ImageGenOutcome =
        withContext(Dispatchers.IO) {
            if (ep == null || !ep.ready || !ep.supports(AiCapability.IMAGE_GEN)) {
                return@withContext ImageGenOutcome(error = "图片模型未配置完整")
            }
            // 智谱的图片接口与 OpenAI Images 请求结构一致；其它协议不硬套。
            if (ep.format != AiFormat.OPENAI) {
                return@withContext ImageGenOutcome(error = "图片生成需要 OpenAI 兼容协议")
            }
            val modelName = ep.image.trim()
            if (modelName.isBlank()) {
                return@withContext ImageGenOutcome(error = "没有填写图片生成模型名")
            }
            try {
                val url = retryRequest {
                    val payload = JSONObject().apply {
                        put("model", modelName)
                        put("prompt", prompt.ifBlank { "一张有质感的旅行风景插画" })
                        put("size", "1024x1024")
                    }.toString()
                    val path = ep.endpointUrl(ep.mediaPath, "/images/generations")
                    val json = postJsonTo(path, payload, ep.apiKey)
                        ?: error("图片接口没有返回内容")
                    imageUrlFrom(json)
                        ?: error("图片接口没有返回图片地址（data[0].url 为空）")
                }
                ImageGenOutcome(url = url)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                ImageGenOutcome(error = (e.message ?: "未知错误").take(180))
            }
        }

    /**
     * 文生视频。
     *
     * 兼容常见的 OpenAI 风格异步视频接口：POST {base}/videos 创建任务，
     * 返回 url 时直接用；返回 id/task_id 时轮询 GET {视频任务地址}/{id}，
     * 直到拿到视频地址或明确失败。自定义服务商可在端点里改生成路径。
     * 默认最多等 3 分钟，避免网络慢时无限占着界面。
     */
    suspend fun generateVideo(ep: AiEndpoint?, prompt: String): String? =
        generateVideoOutcome(ep, prompt).url

    /**
     * 与 generateVideo 相同，但把失败原因带回界面。
     * 视频接口各家错误格式差异大，这里只保留一句人能看懂的原因，不暴露整段 JSON。
     */
    suspend fun generateVideoOutcome(ep: AiEndpoint?, prompt: String): VideoGenOutcome =
        withContext(Dispatchers.IO) {
            if (ep == null || !ep.ready) {
                return@withContext VideoGenOutcome(null, "视频模型未配置完整")
            }
            if (!ep.supports(AiCapability.VIDEO_GEN)) {
                return@withContext VideoGenOutcome(null, "当前模型没有勾选“视频生成”能力")
            }
            if (ep.format != AiFormat.OPENAI) {
                return@withContext VideoGenOutcome(null, "当前协议不是 OpenAI 兼容视频接口")
            }
            runCatching {
                val path = ep.endpointUrl(
                    ep.videoPath.ifBlank { ep.mediaPath },
                    "/videos"
                )
                val payload = JSONObject().apply {
                    put("model", ep.video.trim())
                    put("prompt", prompt.ifBlank { "一段有电影感的旅行风景视频" })
                }.toString()
                val created = postJsonTo(path, payload, ep.apiKey)
                    ?: return@runCatching VideoGenOutcome(null, "视频接口没有返回任务信息")
                mediaUrl(created)?.let { return@runCatching VideoGenOutcome(it, "") }
                val taskId = created.optString("id").ifBlank {
                    created.optString("task_id").ifBlank {
                        created.optString("request_id")
                    }
                }
                if (taskId.isBlank()) {
                    return@runCatching VideoGenOutcome(null, "视频接口没有返回视频地址或任务 id")
                }

                var lastStatus = ""
                repeat(60) {
                    delay(3_000L)
                    val state = getJsonTo(path.trimEnd('/') + "/" + taskId, ep.apiKey)
                    if (state == null) {
                        // 连续连不上就别白白让用户等满 3 分钟
                        if (it >= 8) {
                            return@runCatching VideoGenOutcome(null, "视频任务查询失败，请检查网络或接口地址")
                        }
                        return@repeat
                    }
                    mediaUrl(state)?.let { return@runCatching VideoGenOutcome(it, "") }
                    val status = state.optString("status")
                        .ifBlank { state.optString("state") }
                        .lowercase()
                    if (status.isNotBlank()) lastStatus = status
                    if (status.contains("fail") || status.contains("error") || status.contains("cancel")) {
                        return@runCatching VideoGenOutcome(null, "视频生成失败${if (lastStatus.isBlank()) "" else "（$lastStatus）"}")
                    }
                }
                VideoGenOutcome(null, "视频生成超时（约 3 分钟），请稍后在历史里查看或重试")
            }.getOrElse { e ->
                VideoGenOutcome(null, (e.message ?: "未知错误").take(180))
            }
        }

    /**
     * 解析图片生成响应。智谱 CogView 返回 data[0].url；
     * OpenAI 可能返回 b64_json。这里再兼容几种常见的 url 字段与顶层返回，
     * 避免接口成功却因为字段名不同被当成失败。
     */
    private fun imageUrlFrom(o: JSONObject): String? {
        fun httpUrl(v: String?): String? =
            v?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

        fun fromObject(item: JSONObject?): String? {
            if (item == null) return null
            listOf("url", "image_url", "file_url", "download_url", "output_url").forEach { key ->
                httpUrl(item.optString(key))?.let { return it }
                val nested = item.optJSONObject(key)
                httpUrl(nested?.optString("url"))?.let { return it }
            }
            return null
        }

        val dataArr = o.optJSONArray("data")
        fromObject(dataArr?.optJSONObject(0))?.let { return it }
        fromObject(o.optJSONObject("data"))?.let { return it }
        fromObject(o)?.let { return it }

        // 少数网关把 data 直接写成字符串数组
        httpUrl(dataArr?.optString(0))?.let { return it }

        val first = dataArr?.optJSONObject(0)
        val b64 = first?.optString("b64_json").orEmpty()
            .ifBlank { first?.optString("base64").orEmpty() }
            .ifBlank { o.optString("b64_json").orEmpty() }
        if (b64.isNotBlank()) return "data:image/png;base64,$b64"
        return null
    }

    /** 从各家视频/图片响应里提取可访问的媒体地址；只认真实 URL，不猜。 */
    private fun mediaUrl(o: JSONObject): String? {
        val directKeys = listOf("url", "video_url", "output_url", "download_url", "file_url")
        directKeys.forEach { k ->
            val v = o.optString(k)
            if (v.startsWith("http://") || v.startsWith("https://")) return v
        }
        val output = o.opt("output")
        if (output is String && (output.startsWith("http://") || output.startsWith("https://"))) return output
        if (output is JSONObject) output.optString("url").takeIf { it.startsWith("http") }?.let { return it }
        val data = o.optJSONArray("data")
        if (data != null) {
            val first = data.optJSONObject(0)
            val v = first?.optString("url") ?: first?.optString("video_url")
            if (!v.isNullOrBlank() && v.startsWith("http")) return v
        }
        val result = o.optJSONObject("result")
        val rv = result?.optString("url") ?: result?.optString("video_url")
        if (!rv.isNullOrBlank() && rv.startsWith("http")) return rv
        return null
    }

    // ---------- 结构化能力（沿用原 Prompt） ----------

    suspend fun generateGuideFromLog(ep: AiEndpoint, log: String): GuideBlueprint? {
        val prompt = """
你是一名资深旅行攻略编辑。根据用户描述的旅行日志，整理成一份结构化攻略。
要求：
- 标题简洁有吸引力（如：大理5日慢生活｜洱海边的风花雪月）
- 城市用中文
- 类型从以下选一：城市漫游 / 美食 / 自然风光 / 人文历史 / 亲子 / 徒步 / 自驾 / 深度
- 标签 3-5 个
- 简介 2-3 句话
- 行程按天规划，每天 2-4 个景点或活动（每个给 name 与一句话 note）
- 住宿、交通、花费、美食各给几条（每条是 [名称, 备注]）
只输出一行纯 JSON，禁止使用 markdown 代码块，不要任何解释：
{
  "title": "...",
  "city": "...",
  "type": "...",
  "tags": ["...","..."],
  "intro": "...",
  "days": [{"day":1,"title":"...","spots":[{"name":"...","note":"..."}]}],
  "hotels": [["名称","备注"]],
  "transports": [["方式","备注"]],
  "costs": [["项目","金额"]],
  "foods": [["餐厅/美食","备注"]]
}
        """.trimIndent()
        val content = requestOnce(ep, prompt, log) ?: return null
        return parseGuideBlueprint(content)
    }

    suspend fun extractFootprint(ep: AiEndpoint, spec: String): FootprintBlueprint? {
        val prompt = """
你帮助用户把一句话变成一条旅行足迹记录。尽量提取：城市、地点名称、备注、日期，以及该地点的近似经纬度（若你知道）。
如果某些字段确实无法从文本推断，就把它们放进 missing 数组（可取：city / name / note / date）。
只输出如下 JSON，不要任何解释或代码块：
{
  "city": "...",
  "name": "...",
  "note": "...",
  "date": "...",
  "lat": 数字或 null,
  "lon": 数字或 null,
  "missing": ["city"]
}
只输出一行纯 JSON，禁止使用 markdown 代码块，不要任何解释。
        """.trimIndent()
        val content = requestOnce(ep, prompt, spec) ?: return null
        return parseFootprintBlueprint(content)
    }

    /**
     * 一键写旅行备注：根据地点、日期、照片数量和已有备注，只写 note。
     * 标题与标签由用户自己填，模型不覆盖，避免不同页面显示不一致。
     *
     * 只允许使用调用方给的事实，避免模型凭「大理」两个字编出洱海日落、
     * 编出同行人、天气和美食；照片本身不做云端视觉，端侧已识别的标签会
     * 跟在 facts 里一起送进来。
     */
    suspend fun composeFootprint(ep: AiEndpoint, facts: String): FootprintDraft? {
        val content = requestOnce(ep, footprintComposePrompt, facts) ?: return null
        return parseFootprintDraft(content)
    }

    /**
     * 一键整理足迹的端侧版本：智能分配选了「本机」时真的走本机模型，
     * 不再出现「界面写端侧、实际发云端」的假标注；本机不可用返回 null 由调用方回退云端。
     */
    suspend fun composeFootprintLocal(
        context: Context,
        facts: String,
        localModelId: String? = null
    ): FootprintDraft? = withContext(Dispatchers.IO) {
        if (LocalLlm.state(context) != LocalLlmState.READY) return@withContext null
        val raw = LocalLlm.chatOnce(context, "$footprintComposePrompt\n\n$facts", localModelId)
            ?: return@withContext null
        val answer = raw.split("\u0001", limit = 3).let { segs ->
            if (segs.size >= 3) segs[2] else raw
        }
        parseFootprintDraft(answer)
    }

    private val footprintComposePrompt = """
你是「MY足迹」的旅行记录编辑。请根据用户给出的地点、日期、照片数量与已有备注，只写这一段旅程的「旅行备注」。
只输出一行纯 JSON，禁止使用 markdown 代码块，不要任何解释：
{"note":"..."}
硬性要求：
- note 80-160 个字，第一人称、自然、有画面感；只写给定事实，可以写「照片记录了…」，但不得虚构照片里没给的内容
- 不要另写标题、不要输出标签、不要打分、不要 emoji、不要标题党、不要营销腔
- 信息很少时宁可用更短、更朴素的表达，也绝对不能编造
    """.trimIndent()

    /** 解析一键生成的足迹草稿；模型没给出可用标题或备注时返回 null */
    fun parseFootprintDraft(content: String): FootprintDraft? {
        val root = extractJson(content) ?: return null
        fun strList(name: String): List<String> {
            val arr = root.optJSONArray(name) ?: return emptyList()
            return (0 until arr.length())
                .mapNotNull { arr.optString(it).trim().takeIf { s -> s.isNotBlank() } }
        }
        val title = root.optString("title").trim()
        val note = root.optString("note").trim()
        if (title.isBlank() && note.isBlank()) return null
        return FootprintDraft(
            title = title.take(40),
            note = note.take(500),
            tags = strList("tags").take(4)
        )
    }

    /** AI 生成逐日行程草案：目的地 + 天数 + 预算 + 偏好 → 结构化行程 */
    suspend fun generateTripPlan(
        ep: AiEndpoint,
        destination: String,
        days: Int,
        budget: String,
        preference: String
    ): AiTripBlueprint? {
        val prompt = """
你是一名资深旅行规划师，也是一名会讲故事的当地向导。根据用户给的目的地、天数、预算与偏好，输出一份「有概括、有画面、知道该站哪儿」的逐日行程。
写作要求：
- title：给整趟旅行一个概括性标题：地点＋玩法/气质，8-16 字，例如「大理三日：洱海风里走村串巷」。不要用「XX3日游」这种流水账标题。
- summary：2-3 句。第一句概括骨架与气质（什么时间、以什么方式、串哪几片区域）；第二句点出全程最值得站的位置和理由；最后一句说这趟适合谁。
- bestSpot：全程最值得站的一处。place 具体到点位（如「海舌公园北端栈道」），moment 给最佳时段/光线（如「日落前 40 分钟」），reason 一句话说明为什么最好（视野、人少、光线、当地人的生活场景）。
- days：每天 3-4 条 items，按时间顺序（如「09:00 …」），使用当地真实名称，避免空泛。
  每条都要带一个「人」或「环境」的锚点，不能只报地名：
  · 人：在那里生活/做事的人（渔民、菜市场摊主、手艺人、开店老人），写他们此刻在做什么；
  · 环境：风、光线、水位、潮汐、雾、气味、声音、街巷尺度、季节植被。
  例：「17:40 才村码头，渔船回港，船老大把网摊在石阶上晾，风里有柴油和鱼腥味」。
- 每天再给 bestSpot：当天最值得站的位置＋时段＋一句为什么（如「双廊玉几岛东侧礁石，19:10 逆光拍剪影」）。
- 禁止「感受当地文化」「打卡」「绝美」「网红」这类空话；没有资料支撑的门票、营业时间、车次不要写。
- packing 给 5-8 件建议携带物品。
- tips 给 3-5 条当地实用提示（门票、交通、避坑）。
只输出一行纯 JSON，禁止使用 markdown 代码块，不要任何解释：
{
  "title": "...",
  "destination": "...",
  "summary": "...",
  "bestSpot": {"place": "...", "moment": "...", "reason": "..."},
  "days": [{"day":1,"title":"概括当天主题的一句话","bestSpot":"位置＋时段＋为什么","items":["09:00 ...","12:00 ...","18:00 ..."]}],
  "packing": ["..."],
  "tips": ["..."]
}
        """.trimIndent()
        val input = "目的地：$destination\n天数：$days 天\n预算：$budget\n偏好：$preference"
        val content = requestOnce(ep, prompt, input) ?: return null
        return parseTripBlueprint(content)
    }

    /** 流式版行程规划：边生成边吐文本，UI 实时显示；收完后再 parseTripBlueprint。 */
    fun generateTripPlanStream(
        ep: AiEndpoint,
        destination: String,
        days: Int,
        budget: String,
        preference: String,
        extraContext: String = ""
    ): Flow<String> {
        val prompt = """
你是一名资深旅行规划师，也是一名会讲故事的当地向导。根据用户给的目的地、天数、预算与偏好，输出一份「有概括、有画面、知道该站哪儿」的逐日行程。
写作要求：
- title：给整趟旅行一个概括性标题：地点＋玩法/气质，8-16 字，例如「大理三日：洱海风里走村串巷」。不要用「XX3日游」这种流水账标题。
- summary：3-4 句。第一句概括骨架与气质（什么时间、以什么方式、串哪几片区域）；第二句点出全程最值得站的位置和理由；再说明本次出行的时机建议（是否推荐这个时间去、人流如何、注意什么）；最后一句说这趟适合谁。
- bestSpot：全程最值得站的一处。place 具体到点位（如「海舌公园北端栈道」），moment 给最佳时段/光线（如「日落前 40 分钟」），reason 一句话说明为什么最好（视野、人少、光线、当地人的生活场景）。
- days：每天 3-4 条 items，按时间顺序（如「09:00 …」），使用当地真实名称，避免空泛。
  每条都要带一个「人」或「环境」的锚点，不能只报地名：
  · 人：在那里生活/做事的人（渔民、菜市场摊主、手艺人、开店老人），写他们此刻在做什么；
  · 环境：风、光线、水位、潮汐、雾、气味、声音、街巷尺度、季节植被。
  例：「17:40 才村码头，渔船回港，船老大把网摊在石阶上晾，风里有柴油和鱼腥味」。
- 每天再给 bestSpot：当天最值得站的位置＋时段＋一句为什么（如「双廊玉几岛东侧礁石，19:10 逆光拍剪影」）。
- 禁止「感受当地文化」「打卡」「绝美」「网红」这类空话。
- 必须结合下面提供的【出行参考】：天气、节假日、旅游旺季/淡季、人流拥挤度，据此调整安排
  （雨天安排室内、旺季错峰出行、淡季推荐小众景点、节假日提醒预约/拥堵）
- 【出行参考】里若有「多源检索压缩证据 / 联网攻略资料」，那是刚检索官方文旅、OTA、
  知乎/小红书/马蜂窝等网页后清洗压缩出来的证据，不是模型记忆。
  必须优先采用其中真实出现的景点名、开放/预约信息、交通车程、避坑与花费提示，并在 tips 里体现。
- 数据真实性优先级：官方文旅/景区公告 > OTA（携程/去哪儿）> 主流媒体 > 知乎/小红书等 UGC。
  门票、开放时间、预约政策、车票等硬数字，只能采用官方/OTA/事实卡里的；UGC 只用于路线、体验、避坑。
  两家来源数字冲突时，采用层级更高、发布时间更新的那一条；无法确认就写「以景区当日公告为准」，
  资料里没写的具体价格、营业时间、车次一律不要凭训练记忆编造。
- packing 给 5-8 件建议携带物品（根据天气增减衣物/雨具）
- tips 给 3-5 条当地实用提示（门票、交通、避坑、预约建议）
- advice 给 3-5 条出行建议：综合天气、节假日/旺季/周末人流、错峰方案、穿搭建议、雨天备选
只输出一行纯 JSON，禁止使用 markdown 代码块，不要任何解释：
{
  "title": "...",
  "destination": "...",
  "summary": "概括整趟骨架与气质＋最值得站的一处＋时机建议",
  "bestSpot": {"place": "...", "moment": "...", "reason": "..."},
  "days": [{"day":1,"title":"概括当天主题的一句话","bestSpot":"位置＋时段＋为什么","items":["09:00 ...","12:00 ...","18:00 ..."]}],
  "packing": ["..."],
  "tips": ["..."],
  "advice": ["..."]
}
        """.trimIndent()
        val input = buildString {
            append("目的地：$destination\n天数：$days 天\n预算：$budget\n偏好：$preference")
            if (extraContext.isNotBlank()) {
                append("\n\n【出行参考】\n$extraContext")
            }
        }
        return sseFlow(
            ep = ep,
            prepare = { streaming -> prepareChat(ep, prompt, listOf(ChatTurn(true, input)), null, null, streaming) },
            fallbackPrompt = prompt.length + input.length
        ) {}.flowOn(Dispatchers.IO)
    }

    /**
     * 从对话记录里提取用户想要导入的行程草稿。
     *
     * 这是「导入我的计划」的真正执行者：对话模型只能生成文本，App 里没有任何
     * 代码会因为它说「已导入」就写数据——必须由这一步把对话中的逐日行程提取成
     * 结构化 JSON，再经用户确认写入行程列表。
     */
    suspend fun extractTripFromChat(ep: AiEndpoint, chatLog: String, today: String): AiTripBlueprint? {
        val prompt = """
你帮助用户把对话中出现过的旅行行程导入到 App 的行程列表。从下面的对话记录中，找出最近一份完整的逐日行程（通常是助手按 Day 1 / Day 2… 分段写出的规划），提取成结构化 JSON。
要求：
- title：给整趟旅行一个概括性标题（地点＋玩法/气质，8-16 字）；对话里已有标题就沿用，没有就按内容概括，不要用「XX3日游」
- destination：目的地城市（中文）
- startDate：出发日期，格式 yyyy-MM-dd；对话中明确说了出发/返程日期就用它，否则填 "$today"
- days：逐日安排，每天 title 一句话概括，items 是当天的具体安排（保留时间段前缀如「上午：」）；对话里若给过当天的最佳位置，填进该天的 bestSpot 字符串
- packing：对话中有打包/携带建议就提取，没有就给空数组
- summary：概括骨架与气质，对话里若提到最值得站的位置/时段也一并写进来
- bestSpot：对话里明确提到「最值得去/最佳位置/最佳时间/看日落最好的地方」时，填 {"place":"点位","moment":"时段","reason":"为什么"}；没提到就省略该字段
- 对话里没有可提取的完整行程时，输出 {"days":[]}（days 为空数组即可）
只输出一行纯 JSON，禁止使用 markdown 代码块，不要任何解释：
{"title":"...","destination":"...","summary":"...","bestSpot":{"place":"...","moment":"...","reason":"..."},"startDate":"yyyy-MM-dd","days":[{"day":1,"title":"...","bestSpot":"...","items":["..."]}],"packing":["..."],"tips":["..."]}
        """.trimIndent()
        val content = requestOnce(ep, prompt, "今天的日期：$today\n\n对话记录：\n$chatLog") ?: return null
        return parseTripBlueprint(content)?.takeIf { it.days.isNotEmpty() }
    }

    suspend fun detectCommand(ep: AiEndpoint, text: String): CommandIntent? {
        val prompt = """
判断用户的指令类型。只输出一行纯 JSON，禁止使用 markdown 代码块，不要任何解释。JSON 结构：
{"kind":"weather_city"|"weather_query"|"setting"|"language"|"app_language"|"theme"|"haptic"|"settings_page"|"open"|"create_plan"|"open_guide"|"search_guide"|"like_guide"|"fav_guide"|"delete_trip"|"edit_footprint"|"set_chat_mode"|"set_local_model"|"exit_app"|"logout"|"none","target":"...","value":"...","label":"一句中文，说明将要执行的操作，用于请用户确认"}
硬性要求：
- kind 必须且只能是上述值之一，不得写多个或用竖线
- target 一律用中文：城市名写中文（如 北京），不得写拼音
- 切换天气城市（只切换不打开页面）：kind=weather_city，target=中文城市名
- 查询某地天气并打开天气页：kind=weather_query，target=中文城市名（"北京天气怎么样"也归此类）
- 修改设置：kind=setting，target 取 defaultPublic(默认公开)/notifEnabled(消息通知)/shareEnabled(内容分享)/ai_ball(AI悬浮球)/voice_input(语音输入)/voice_tts(语音播报·朗读AI回复)，value 取 true 或 false（开/关）。用户用任何说法表达"打开/关闭朗读、让它自动念、别出声了"都归到这一类，不要靠关键词硬套。
- 切换语言：kind=language，target 取 en 或 zh
- 修改 App 界面语言（整个界面换成英文/中文）：kind=app_language，target 取 en 或 zh
- 切换外观：kind=theme，target 取 system(跟随系统)/light(浅色)/dark(深色)（"开深色模式""晚上护眼一点""换回浅色"）
- 触感反馈：kind=haptic，target 取 follow(跟随系统)/on(总是开启)/off(关闭)（"关掉震动""别震了"）
- 打开设置里的某一项：kind=settings_page，target 取 main/account/data/ai/ai_routing/general/about/trash(回收站)/backup(备份恢复)/language/theme/haptic/background(自定义背景)/widget(桌面小组件)/profile(个人资料)/log(崩溃日志)/privacy/security/local_model(本地大模型)/voice_library(语音库)/api_model(API 模型)
- 打开页面/Tab：kind=open，target 取 settings(设置)/weather(天气)/notifications(通知)/messages(站内消息)/guide_create(发布攻略)/my_plans(我的行程)/year_report(年度报告)/map(足迹地图)/memory(回忆)/profile(我的)/discover(发现)/ticket_zone(购票专区)/music(音乐)/course(课程表)/news(资讯)/stock(股市行情)/trade(我的交易：资产·委托·持仓)/creative(创意工坊)/travelers(常访问的旅行人)/web_search(网页搜索)/ai(AI助手)/upload(新建足迹)/offline(离线地图包)/trips(行程Tab)
- 新建行程计划：kind=create_plan（如"帮我规划行程""新建一个计划"）
- 打开某篇攻略：kind=open_guide，target=攻略名或城市名（如"打开大理那篇攻略"）
- 搜索攻略：kind=search_guide，target=关键词（如"找一下海边的攻略"）
- 点赞/收藏攻略：kind=like_guide 或 fav_guide，target=攻略名或城市名
- 退出应用：kind=exit_app（如"退出应用""关闭 App""退出"），target 留空
- 退出登录/注销账号：kind=logout（如"退出登录""注销""登出账号"），target 留空
- 删除某条已存行程：kind=delete_trip，target=行程名或目的地（破坏性操作，需确认）
- 修改某条足迹的文案：kind=edit_footprint，target=足迹/打卡点名称，value=新文案
- 切换当前对话模型：kind=set_chat_mode，target 取 local(本地模型)/api(云端API)；旧值 hybrid 等同 api
- 切换本地大模型：kind=set_local_model，target=模型名（如 Qwen3-0.6B、Pocket、Qwen2.5-0.5B）
- 其余或不确定：kind=none
        """.trimIndent()
        val content = requestOnce(ep, prompt, text) ?: return null
        return parseCommandIntent(content)
    }

    // ---------- 上下文：不限制大小 ----------
    // 需求变更：不再限制上下文大小。对话/攻略/足迹模型窗口足够大（GLM-4.7-Flash 200K），
    // 完整对话历史全量发给模型，不做固定预算裁剪；仅当单条输入极端长、逼近模型真实窗口时
    // 才做绝对兜底截断，避免被服务端直接拒绝。正常使用不受任何限制。

    /**
     * 在上下文预算内保留对话：
     * - 最近 4 轮原样保留；
     * - 更早的长回答压到 500 字；
     * - 仍然超预算时，从最老的轮次开始丢，只保留能装下的连续尾部。
     *
     * 这样「刚才说的第三点」这类近指代仍然有效，又不会把几千字历史每轮重发。
     */
    private fun fitHistory(
        ep: AiEndpoint,
        systemChars: Int,
        history: List<ChatTurn>,
        maxTokens: Int = ep.contextTokens
    ): List<ChatTurn> {
        if (history.isEmpty()) return history
        // 中文约 1 字 1 token、英文约 4 字符 1 token；按 1.8 字符/token 估算，
        // 给 system 留出空间后作为历史正文的字符预算。
        val maxChars = ((maxTokens.toFloat() * 1.8f) - systemChars)
            .toInt()
            .coerceAtLeast(RECENT_TURNS_FULL * 200)
        val kept = ArrayList<ChatTurn>(history.size)
        var used = 0
        var index = history.lastIndex
        while (index >= 0) {
            val turn = history[index]
            val recent = history.size - index <= RECENT_TURNS_FULL
            val body = when {
                // 最近几轮全文保留，保证当前话题的上下文连贯
                recent -> turn.text
                // 较早的消息：做关键句压缩（去寒暄/重复，留实体、数字、地点、承诺），
                // 不再整段硬截尾，避免早期的偏好与行程结论被从中间切断
                turn.text.length > OLD_TURN_MAX_CHARS -> compressOldTurn(turn.text)
                else -> turn.text
            }
            val room = maxChars - used
            if (room <= 0 && kept.isNotEmpty()) break
            val text = if (body.length > room && room > 0) {
                body.take(room) + "\n…（已按上下文预算裁剪）"
            } else {
                body
            }
            kept.add(0, turn.copy(text = text))
            used += text.length
            index--
        }
        return kept
    }

    /** 旧对话的本地压缩：分句 → 滤掉寒暄/客套/纯重复 → 按关键度选句，保留事实与实体。 */
    private fun compressOldTurn(raw: String): String {
        val sentences = raw.split(Regex("[。！？；\n\r]+|\\s{3,}"))
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
        if (sentences.isEmpty()) return raw.take(OLD_TURN_MAX_CHARS)

        val scored = sentences.map { s -> s to sentenceImportance(s) }
            .sortedByDescending { it.second }
        // 先按关键度取前 N 句，再按原文顺序拼回，避免把对话语序打乱
        val picked = scored.take(OLD_TURN_SENTENCES).map { it.first }.toMutableSet()
        val ordered = sentences.filter { it in picked }
        var out = ordered.joinToString("。")
        if (out.length > OLD_TURN_MAX_CHARS) out = out.take(OLD_TURN_MAX_CHARS)
        return if (out.isBlank()) raw.take(OLD_TURN_MAX_CHARS) else "$out\n…（较早对话已压缩）"
    }

    /** 句子关键度：含数字/地点/行程/承诺/偏好的得分高；寒暄客套、确认重复得分低。 */
    private fun sentenceImportance(s: String): Int {
        var score = 0
        if (Regex("\\d").containsMatchIn(s)) score += 3
        if (Regex(PLACE_CUES).containsMatchIn(s)) score += 3
        if (Regex(FACT_CUES).containsMatchIn(s)) score += 2
        if (Regex(PREFERENCE_CUES).containsMatchIn(s)) score += 2
        if (s.length in 8..120) score += 1
        // 寒暄 / 纯确认 / 语气兜底：没有信息增量，压缩时优先丢掉
        if (Regex(SMALLTALK_CUES).containsMatchIn(s)) score -= 3
        if (s.length < 8) score -= 2
        return score
    }

    private const val PLACE_CUES =
        "北京|上海|广州|深圳|重庆|成都|杭州|西安|大理|丽江|昆明|三亚|厦门|桂林|西藏|新疆|青海|云南|贵州|湖南|湖北|江苏|浙江|山东|河南|河北|四川|甘肃|香港|澳门|台湾|[一-龥]{2,}(?:市|省|区|县|古城|古镇|山|湖|海|江|寺|宫|街|路|站|机场|车站)"
    private const val FACT_CUES = "门票|价格|票价|费用|预算|住宿|酒店|航班|机票|高铁|火车|车程|公里|小时|分钟|开放|营业|预约|地址|电话|评分|推荐|攻略|行程|计划|路线|几天|天|晚|站"
    private const val PREFERENCE_CUES = "想去|喜欢|想吃|想玩|希望|打算|计划|安排|偏好|忌口|预算|不喜欢|过敏|时间|日期|出发|抵达"
    private const val SMALLTALK_CUES =
        "^(你好|您好|嗨|哈喽|谢谢|好的|收到|明白|了解|可以|行|嗯|哦|哈哈|辛苦了|没问题|稍后|在吗|打扰了)[，。！!？?\\s]*"

    private const val RECENT_TURNS_FULL = 4
    private const val OLD_TURN_MAX_CHARS = 500
    /** 旧消息压缩后最多保留的句子数，避免长对话把预算全摊在寒暄上 */
    private const val OLD_TURN_SENTENCES = 6

    /**
     * 结构化请求的单条输入同样不裁剪；仅在逼近模型真实窗口（200K token）时兜底。
     */
    private fun clampForPrompt(ep: AiEndpoint, systemChars: Int, text: String): String {
        val hardMaxChars = (ep.contextTokens * 1.8f).toInt().coerceAtLeast(300_000)
        return clampChars(text, hardMaxChars)
    }

    private fun clampChars(text: String, maxChars: Int): String {
        if (maxChars <= 0 || text.length <= maxChars) return text
        return text.take(maxChars) + "\n…（内容过长，已按模型上下文裁剪）"
    }

    // ---------- 请求组装 ----------

    private class Prepared(val url: String, val headers: Map<String, String>, val body: String)

    private fun prepareChat(
        ep: AiEndpoint,
        system: String,
        turns: List<ChatTurn>,
        imageBase64: String?,
        mime: String?,
        streaming: Boolean
    ): Prepared {
        val key = ep.apiKey.trim()
        return when (ep.format) {
            AiFormat.OPENAI -> {
                val messages = JSONArray()
                if (system.isNotBlank()) {
                    messages.put(JSONObject().put("role", "system").put("content", system))
                }
                turns.forEachIndexed { i, t ->
                    val role = if (t.fromUser) "user" else "assistant"
                    // 图片只挂在最后一条用户消息上
                    val attach = imageBase64 != null && t.fromUser && i == turns.lastIndex
                    if (attach) {
                        val parts = JSONArray()
                        parts.put(
                            JSONObject().put("type", "image_url").put(
                                "image_url",
                                JSONObject().put("url", "data:${mime ?: "image/jpeg"};base64,$imageBase64")
                            )
                        )
                        if (t.text.isNotBlank()) parts.put(JSONObject().put("type", "text").put("text", t.text))
                        messages.put(JSONObject().put("role", role).put("content", parts))
                    } else {
                        messages.put(JSONObject().put("role", role).put("content", t.text))
                    }
                }
                val body = JSONObject().apply {
                    put("model", ep.model.ifBlank { "gpt-3.5-turbo" })
                    put("stream", streaming)
                    put("temperature", 0.6)
                    put("max_tokens", ep.maxOutputTokens)
                    put("messages", messages)
                }.toString()
                Prepared(
                    url = chatUrl(ep),
                    headers = mapOf(
                        "Content-Type" to "application/json; charset=utf-8",
                        "Accept" to if (streaming) "text/event-stream" else "application/json",
                        // 明确要明文：部分网关压缩响应却漏发 Content-Encoding，会导致客户端无法解压
                        "Accept-Encoding" to "identity",
                        "Authorization" to "Bearer $key"
                    ),
                    body = body
                )
            }

            AiFormat.ANTHROPIC -> {
                val messages = JSONArray()
                turns.forEachIndexed { i, t ->
                    val attach = imageBase64 != null && t.fromUser && i == turns.lastIndex
                    val parts = JSONArray()
                    if (attach) {
                        parts.put(
                            JSONObject().put("type", "image").put(
                                "source",
                                JSONObject()
                                    .put("type", "base64")
                                    .put("media_type", mime ?: "image/jpeg")
                                    .put("data", imageBase64)
                            )
                        )
                    }
                    if (t.text.isNotBlank()) parts.put(JSONObject().put("type", "text").put("text", t.text))
                    messages.put(
                        JSONObject()
                            .put("role", if (t.fromUser) "user" else "assistant")
                            .put("content", parts)
                    )
                }
                val body = JSONObject().apply {
                    put("model", ep.model.ifBlank { "gpt-3.5-turbo" })
                    put("max_tokens", ep.maxOutputTokens)
                    put("stream", streaming)
                    if (system.isNotBlank()) put("system", system)
                    put("messages", messages)
                }.toString()
                Prepared(
                    url = "${ep.base}/messages",
                    headers = mapOf(
                        "Content-Type" to "application/json; charset=utf-8",
                        "Accept" to if (streaming) "text/event-stream" else "application/json",
                        // 明确要明文：部分网关压缩响应却漏发 Content-Encoding，会导致客户端无法解压
                        "Accept-Encoding" to "identity",
                        "x-api-key" to key,
                        "anthropic-version" to "2023-06-01"
                    ),
                    body = body
                )
            }

            AiFormat.GEMINI -> {
                val contents = JSONArray()
                turns.forEachIndexed { i, t ->
                    val attach = imageBase64 != null && t.fromUser && i == turns.lastIndex
                    val parts = JSONArray()
                    if (attach) {
                        parts.put(
                            JSONObject().put(
                                "inline_data",
                                JSONObject()
                                    .put("mime_type", mime ?: "image/jpeg")
                                    .put("data", imageBase64)
                            )
                        )
                    }
                    if (t.text.isNotBlank()) parts.put(JSONObject().put("text", t.text))
                    contents.put(
                        JSONObject()
                            .put("role", if (t.fromUser) "user" else "model")
                            .put("parts", parts)
                    )
                }
                val body = JSONObject().apply {
                    if (system.isNotBlank()) {
                        put(
                            "systemInstruction",
                            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
                        )
                    }
                    put("contents", contents)
                    put("generationConfig", JSONObject().put("temperature", 0.6).put("maxOutputTokens", ep.maxOutputTokens))
                }.toString()
                val verb = if (streaming) "streamGenerateContent?alt=sse&key=" else "generateContent?key="
                Prepared(
                    url = "${ep.base}/models/${ep.model}:$verb$key",
                    headers = mapOf(
                        "Content-Type" to "application/json; charset=utf-8",
                        "Accept" to if (streaming) "text/event-stream" else "application/json",
                        "Accept-Encoding" to "identity"
                    ),
                    body = body
                )
            }
        }
    }

    // ---------- SSE 流 ----------

    /**
     * 限流自动重试：429/5xx 且尚未输出任何内容时，等 1.5s → 3s 再试，最多重试 2 次。
     * 已经吐出部分文字再失败不重试（会导致回答重复），直接把错误抛给调用方。
     */
    private fun retryOnRateLimit(inner: Flow<String>): Flow<String> = flow {
        var attempt = 0
        while (true) {
            var emitted = false
            try {
                inner.collect { emitted = true; emit(it) }
                break
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                val msg = e.message ?: ""
                val canRetry = (msg.contains("HTTP 429") || msg.contains("HTTP 5")) && attempt < 2
                if (!emitted && canRetry) {
                    attempt++
                    kotlinx.coroutines.delay(1500L * attempt)
                } else {
                    throw e
                }
            }
        }
    }

    /**
     * 统一的流式读取。三种协议的 SSE 帧格式不同，解析差异收敛在 parseFrame 里。
     * 服务商返回 usage 就用真实值，不返回时按字符数估算（estimated = true）。
     */
    private fun sseFlow(
        ep: AiEndpoint,
        prepare: (streaming: Boolean) -> Prepared,
        fallbackPrompt: Int,
        onUsage: (UsageDelta) -> Unit
    ): Flow<String> = flow {
        // 注意：本函数体在 flowOn(Dispatchers.IO) 之后跑在 IO 线程。
        // 调用方 chatStream / visionStream 必须带 .flowOn(Dispatchers.IO)，
        // 否则整个 HTTP 连接会在主线程建立 → NetworkOnMainThreadException。
        val p = prepare(true)
        val conn = (URL(p.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 180000
            requestMethod = "POST"
            doOutput = true
            p.headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        var promptTokens = 0L
        var completionTokens = 0L
        var realUsage = false
        // 是否确实读到了有效响应（有输出内容或拿到了真实 usage）
        var received = false
        // 用户中途关闭页面 / 协程被取消
        var cancelled = false
        val out = StringBuilder()

        try {
            conn.outputStream.use { it.write(p.body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = maybeGunzip(conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("HTTP $code ${humanizeError(err, code)}")
            }
            val reader = maybeGunzip(conn.inputStream)!!.bufferedReader(Charsets.UTF_8)
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) {
                    // 有些网关（实测 chat.cqjtu.edu.cn）出错时仍返回 HTTP 200 + text/event-stream，
                    // body 却是一行裸的 {"error":{"message":"..."}}，没有 data: 前缀。
                    // 直接 continue 会把错误吞掉，用户只看到「没反应/空回复」，所以这里识别出来。
                    val t = line.trim()
                    if (t.startsWith("{") && t.contains("\"error\"")) {
                        val msg = runCatching {
                            JSONObject(t).optJSONObject("error")?.optString("message").orEmpty()
                        }.getOrNull().orEmpty()
                        error("模型服务报错：${msg.ifBlank { t.take(200) }}")
                    }
                    continue
                }
                val data = line.substring(5).trim()
                if (data.isEmpty()) continue
                if (data == "[DONE]") break
                val obj = runCatching { JSONObject(data) }.getOrNull() ?: continue

                when (ep.format) {
                    AiFormat.OPENAI -> {
                        val deltaObj = obj.optJSONArray("choices")?.optJSONObject(0)
                            ?.optJSONObject("delta")
                        // 思考内容（reasoning_content）：以 \u0001 前缀 emit，UI 层折叠
                        val reasoning = deltaObj?.optString("reasoning_content").orEmpty()
                        if (reasoning.isNotEmpty()) {
                            out.append(reasoning)
                            emit("\u0001$reasoning")
                        }
                        // 正式回复内容：部分模型（DeepSeek/豆包/Qwen 深度思考版）会在 content 里直接吐 <think>…</think>
                        // 这里统一提取思考部分，正文里过滤掉
                        val deltaRaw = deltaObj?.optString("content").orEmpty()
                        var delta = deltaRaw
                        var inlineThinking = ""
                        // 处理 <think> 标签：提取思考内容，正文去掉标签部分
                        if (deltaRaw.isNotEmpty()) {
                            var thinkStart = deltaRaw.indexOf("<think>")
                            var thinkEnd = deltaRaw.indexOf("</think>")
                            val sb = StringBuilder()
                            val thinkSb = StringBuilder()
                            var pos = 0
                            while (thinkStart >= 0 || thinkEnd >= 0) {
                                if (thinkStart >= 0 && (thinkEnd < 0 || thinkStart < thinkEnd)) {
                                    // <think> 开始，前面的是正文
                                    if (thinkStart > pos) sb.append(deltaRaw.substring(pos, thinkStart))
                                    pos = thinkStart + "<think>".length
                                    thinkStart = deltaRaw.indexOf("<think>", pos)
                                } else if (thinkEnd >= 0) {
                                    // </think> 结束，中间的是思考
                                    if (thinkEnd > pos) thinkSb.append(deltaRaw.substring(pos, thinkEnd))
                                    pos = thinkEnd + "</think>".length
                                    thinkEnd = deltaRaw.indexOf("</think>", pos)
                                } else break
                            }
                            if (pos < deltaRaw.length) sb.append(deltaRaw.substring(pos))
                            delta = sb.toString()
                            inlineThinking = thinkSb.toString()
                        }
                        // 内联思考单独 emit
                        if (inlineThinking.isNotEmpty()) {
                            emit("\u0001$inlineThinking")
                        }
                        if (delta.isNotEmpty()) {
                            out.append(delta)
                            emit(delta)
                        }
                        obj.optJSONObject("usage")?.let { u ->
                            promptTokens = u.optLong("prompt_tokens", 0L)
                            completionTokens = u.optLong("completion_tokens", 0L)
                            realUsage = promptTokens > 0 || completionTokens > 0
                        }
                    }

                    AiFormat.ANTHROPIC -> {
                        when (obj.optString("type")) {
                            "content_block_delta" -> {
                                val t = obj.optJSONObject("delta")?.optString("text").orEmpty()
                                if (t.isNotEmpty()) {
                                    out.append(t)
                                    emit(t)
                                }
                            }

                            "message_start" -> obj.optJSONObject("message")?.optJSONObject("usage")?.let { u ->
                                promptTokens = u.optLong("input_tokens", 0L)
                                realUsage = realUsage || promptTokens > 0
                            }

                            "message_delta" -> obj.optJSONObject("usage")?.let { u ->
                                completionTokens = u.optLong("output_tokens", 0L)
                                realUsage = realUsage || completionTokens > 0
                            }

                            "error" -> error(obj.optJSONObject("error")?.optString("message") ?: "模型返回错误")
                        }
                    }

                    AiFormat.GEMINI -> {
                        val parts = obj.optJSONArray("candidates")?.optJSONObject(0)
                            ?.optJSONObject("content")?.optJSONArray("parts")
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                val t = parts.optJSONObject(i)?.optString("text").orEmpty()
                                if (t.isNotEmpty()) {
                                    out.append(t)
                                    emit(t)
                                }
                            }
                        }
                        obj.optJSONObject("usageMetadata")?.let { u ->
                            promptTokens = u.optLong("promptTokenCount", 0L)
                            completionTokens = u.optLong("candidatesTokenCount", 0L)
                            realUsage = promptTokens > 0 || completionTokens > 0
                        }
                    }
                }
            }
            received = out.isNotEmpty() || realUsage
        } catch (ce: CancellationException) {
            cancelled = true
            throw ce
        } finally {
            conn.disconnect()
            // 只有真正收到有效响应才计入用量：
            // HTTP 4xx/5xx、连接中断、用户中途取消都不该被统计成一次成功请求。
            if (received && !cancelled) {
                onUsage(
                    if (realUsage) UsageDelta(promptTokens, completionTokens, false)
                    else UsageDelta(estimateTokens(fallbackPrompt), estimateTokens(out.length), true)
                )
            }
        }
    }

    // ---------- 非流式 POST ----------

    private fun postJson(p: Prepared, connectMs: Int = 15000, readMs: Int = 60000): JSONObject? {
        val conn = (URL(p.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectMs
            readTimeout = readMs
            requestMethod = "POST"
            doOutput = true
            p.headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { it.write(p.body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = maybeGunzip(conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("HTTP $code ${humanizeError(err, code)}")
            }
            return JSONObject(maybeGunzip(conn.inputStream)!!.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun postJsonTo(url: String, payload: String, key: String): JSONObject? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $key")
        }
        try {
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) {
                val code = conn.responseCode
                val err = maybeGunzip(conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                error("HTTP $code ${humanizeError(err, code)}")
            }
            return JSONObject(maybeGunzip(conn.inputStream)!!.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    /** 视频任务轮询用；请求结束立刻断开连接，不在后台留长连接 */
    private fun getJsonTo(url: String, key: String): JSONObject? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Connection", "close")
        }
        try {
            if (conn.responseCode !in 200..299) return null
            return JSONObject(maybeGunzip(conn.inputStream)!!.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun geminiText(json: JSONObject): String {
        val parts = json.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        return (0 until parts.length()).joinToString("") { parts.optJSONObject(it)?.optString("text").orEmpty() }
    }

    /** 把各家格式不一的错误体压成一句人话，避免界面上出现一大段 JSON */
    private fun humanizeError(raw: String, code: Int = 0): String {
        if (raw.isBlank() && code != 0) {
            return when (code) {
                400 -> "请求格式错误，请检查内容长度"
                401 -> "API Key 无效或已过期，请在设置里检查"
                402 -> "账号余额不足，请充值后再试"
                403 -> "无权限访问该模型"
                404 -> "模型不存在，请在设置里检查模型名称"
                413 -> "内容过长，请缩短输入"
                429 -> "您的账户已达到速率限制，请控制请求频率"
                in 500..599 -> "模型服务异常，请稍后再试"
                else -> "网络异常，请检查网络连接"
            }
        }
        val msg = runCatching {
            val o = JSONObject(raw)
            o.optJSONObject("error")?.optString("message")
                ?: o.optString("message")
                ?: o.optString("detail")
        }.getOrNull() ?: when (code) {
            401 -> "API Key 无效"
            404 -> "模型不存在"
            429 -> "请求频率过高"
            in 500..599 -> "服务异常"
            else -> ""
        }
        return (msg.takeIf { it.isNotBlank() } ?: raw).take(160)
    }

    /** 非流式请求自动重试：429/5xx 重试 2 次，间隔 1.5s/3s */
    private suspend fun <T> retryRequest(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                val msg = e.message ?: ""
                val canRetry = (msg.contains("HTTP 429") || msg.contains("HTTP 5")) && attempt < 2
                if (canRetry) {
                    attempt++
                    delay(1500L * attempt)
                } else {
                    throw e
                }
            }
        }
    }

    /** 粗略估算 token：中日韩字符约 1 字 1 token，其它字符约 4 字符 1 token */
    private fun estimateTokens(charCount: Int): Long {
        if (charCount <= 0) return 0
        return (charCount / 1.8).toLong().coerceAtLeast(1)
    }

    // ---------- 结构化解析（鲁棒：剥离代码块、截取首个 { 到末个 }） ----------

    private fun extractJson(content: String): JSONObject? {
        val c = content.trim()
        val start = c.indexOf('{')
        val end = c.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(c.substring(start, end + 1)) }.getOrNull()
    }

    fun parseTripBlueprint(content: String): AiTripBlueprint? {
        val root = extractJson(content) ?: return null
        fun strList(key: String): List<String> =
            root.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()

        val days = root.optJSONArray("days")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { d ->
                    // bestSpot 既可能是「位置 · 时段 · 为什么」的字符串，也可能是对象，
                    // 两种都要能读出来，缺了也不影响这一天的其它内容
                    val dayBest = runCatching {
                        d.optString("bestSpot").takeIf { it.isNotBlank() }?.let { return@runCatching it }
                        val o = d.optJSONObject("bestSpot") ?: return@runCatching ""
                        listOf(o.optString("place"), o.optString("moment"), o.optString("reason"))
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                    }.getOrDefault("")
                    AiTripDay(
                        day = d.optInt("day", i + 1),
                        title = d.optString("title"),
                        items = d.optJSONArray("items")?.let { s ->
                            (0 until s.length()).map { s.optString(it) }
                        }.orEmpty(),
                        bestSpot = dayBest
                    )
                }
            }
        }.orEmpty()
        if (days.isEmpty()) return null

        val dest = root.optString("destination")
        val bestSpot = runCatching {
            val o = root.optJSONObject("bestSpot")
            if (o != null) {
                AiTripBestSpot(
                    place = o.optString("place"),
                    moment = o.optString("moment"),
                    reason = o.optString("reason")
                )
            } else {
                root.optString("bestSpot").takeIf { it.isNotBlank() }?.let { AiTripBestSpot(place = it) }
            }
        }.getOrNull()?.takeIf { !it.isBlank }

        return AiTripBlueprint(
            title = root.optString("title").ifBlank { "$dest 行程" },
            destination = dest,
            summary = root.optString("summary"),
            days = days,
            packing = strList("packing"),
            tips = strList("tips"),
            advice = strList("advice"),
            startDate = root.optString("startDate"),
            bestSpot = bestSpot
        )
    }

    private fun parseGuideBlueprint(content: String): GuideBlueprint? {
        val root = extractJson(content) ?: return null
        val days = runCatching {
            root.optJSONArray("days")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val d = arr.optJSONObject(i) ?: return@mapNotNull null
                    GuideDayBlueprint(
                        day = d.optInt("day", i + 1),
                        title = d.optString("title"),
                        spots = d.optJSONArray("spots")?.let { s ->
                            (0 until s.length()).mapNotNull { j ->
                                val sp = s.optJSONObject(j) ?: return@mapNotNull null
                                SpotBlueprint(sp.optString("name"), sp.optString("note"))
                            }
                        }.orEmpty()
                    )
                }
            }.orEmpty()
        }.getOrDefault(emptyList())

        fun pairs(key: String): List<Pair<String, String>> =
            root.optJSONArray(key)?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val e = arr.optJSONArray(i) ?: return@mapNotNull null
                    (e.optString(0) to e.optString(1))
                }
            }.orEmpty()

        return GuideBlueprint(
            title = root.optString("title").ifBlank { "我的旅行攻略" },
            city = root.optString("city"),
            type = root.optString("type").ifBlank { "城市漫游" },
            tags = root.optJSONArray("tags")?.let { a ->
                (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
            }.orEmpty(),
            intro = root.optString("intro"),
            days = days,
            hotels = pairs("hotels"),
            transports = pairs("transports"),
            costs = pairs("costs"),
            foods = pairs("foods")
        )
    }

    private fun parseFootprintBlueprint(content: String): FootprintBlueprint? {
        val root = extractJson(content) ?: return null
        val lat = root.optDouble("lat").takeIf { !root.isNull("lat") && it != 0.0 }
        val lon = root.optDouble("lon").takeIf { !root.isNull("lon") && it != 0.0 }
        val missing = root.optJSONArray("missing")?.let { a ->
            (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
        }.orEmpty()
        return FootprintBlueprint(
            city = root.optString("city"),
            name = root.optString("name"),
            note = root.optString("note"),
            date = root.optString("date"),
            lat = lat,
            lon = lon,
            missing = missing
        )
    }

    /**
     * 一键分析：对单条足迹 / 单篇攻略做结构化内容。
     *
     * 足迹是用户自己的旅行记录，只做「总结」，不打分、不评价好坏、不给建议；
     * 攻略仍保留原来的参考价值分析。走内置云端端点——本地 1.5B 做这种
     * 结构化抽取不可靠，且分析可以随时重来，不像本地对话那样必须离线。
     */
    suspend fun analyzeContent(ep: AiEndpoint, kind: String, title: String, facts: String): AiInsight? {
        val prompt = insightPrompt(kind)
        val content = requestOnce(ep, prompt, "标题：$title\n\n内容：\n$facts") ?: return null
        return parseInsight(content)
    }

    private fun insightPrompt(kind: String, streaming: Boolean = false): String {
        if (kind == "足迹") {
            return """
你是旅行记录整理助手。请根据用户给出的足迹做一次「足迹总结」，只做忠实归纳，不打分、不评价、不给建议。
只输出一行纯 JSON，禁止使用 markdown 代码块，不要任何解释：
{"headline":"2-4句话的足迹总结","highlights":["记录要点1","记录要点2","记录要点3"],"cautions":[],"tips":[],"tags":["关键词1","关键词2"]}
硬性要求：
- 禁止输出 score、推荐指数、值不值得参考、评分档位
- headline 用 2-4 句话总结：去了哪里、什么时候、留下了哪些照片/路线/标记、备注里记录了什么、有什么特别意义；只写给定信息，不推测、不编造
- highlights 2-4 条，提炼这次足迹里的具体记录要点（地点、时间、路线、照片数量、特别标记等），只陈述事实，不评价好坏，不给出行建议
- cautions 与 tips 必须为空数组
- tags 2-4 个短标签
            """.trimIndent()
        }
        val think = if (streaming) "先在思考里快速判断这份内容的具体程度、可执行性、性价比，然后" else ""
        return """
你是资深旅行内容编辑。请对用户给出的${kind}做一次「一键分析」，帮用户判断这份内容值不值得参考、亮点在哪、有什么坑。
${think}只输出一行纯 JSON，禁止使用 markdown 代码块，不要任何解释：
{"headline":"一句话点评，不超过 30 字，要有观点","score":85,"highlights":["亮点1","亮点2"],"cautions":["需要注意的地方1"],"tips":["可以直接照做的建议1"],"tags":["关键词1","关键词2"]}
硬性要求：
- score 是 0-100 的整数推荐指数，按信息的具体程度、可执行性、性价比打分，不要一律给高分；信息太少就给低分
- headline 要有判断（比如「三天的节奏刚好，但交通安排偏紧」），不要只是复述标题
- highlights 2-4 条，说清好在哪里，必须结合给定内容，不要写空话
- cautions 1-3 条，讲避坑点、信息可能过时的部分；内容确实很完整时可以只给 1 条，数组不能为空
- tips 2-4 条，给用户能直接照着做的建议（时间、交通、预算、季节、装备）
- tags 2-4 个短标签
        """.trimIndent()
    }

    /**
     * 一键分析的端侧版本。给智能分配里「本机可跑」的短分析用：
     * 本机模型没有流式接口，一次性拿全文再解析；解析失败返回 null，调用方回退云端。
     */
    suspend fun analyzeLocal(
        context: Context,
        kind: String,
        title: String,
        facts: String,
        localModelId: String? = null
    ): AiInsight? =
        withContext(Dispatchers.IO) {
            if (LocalLlm.state(context) != LocalLlmState.READY) return@withContext null
            val prompt = insightPrompt(kind)
            val raw = LocalLlm.chatOnce(
                context,
                "$prompt\n\n标题：$title\n\n内容：\n$facts",
                localModelId
            ) ?: return@withContext null
            val answer = raw.split("\u0001", limit = 3).let { segs ->
                if (segs.size >= 3) segs[2] else raw
            }
            parseInsight(answer)
        }

    /** 从模型返回里抽出结构化分析；解析失败返回 null。流式与非流式共用 */
    fun parseInsight(content: String): AiInsight? {
        // 剥掉 ```json ... ``` 围栏和首尾多余文字，再从第一个 { 试到最后一个 }
        val cleaned = content
            .replace(Regex("```(?:json)?", RegexOption.IGNORE_CASE), "`")
            .trim()
        val root = extractJson(cleaned) ?: return null

        fun strList(name: String): List<String> {
            val arr = root.optJSONArray(name) ?: return emptyList()
            return (0 until arr.length())
                .mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        }

        val headline = root.optString("headline").trim()
            .ifBlank { root.optString("summary").trim() }
            .ifBlank { strList("highlights").firstOrNull().orEmpty() }
            .ifBlank { "AI 分析" }
        return AiInsight(
            headline = headline,
            score = root.optInt("score", 0).coerceIn(0, 100),
            highlights = strList("highlights"),
            cautions = strList("cautions"),
            tips = strList("tips"),
            tags = strList("tags")
        )
    }

    /**
     * 「一键分析」流式版：实时吐思考过程（以 \u0001 前缀 emit reasoning_content），
     * 思考结束后吐 JSON 正文。UI 边看模型想边等结果，不再干等一次性返回。
     */
    fun analyzeStream(
        ep: AiEndpoint,
        kind: String,
        title: String,
        facts: String,
        onUsage: (UsageDelta) -> Unit = {}
    ): Flow<String> {
        val prompt = insightPrompt(kind, streaming = true)
        val user = "标题：$title\n\n内容：\n$facts"
        return retryOnRateLimit(
            sseFlow(
                ep = ep,
                prepare = { streaming -> prepareChat(ep, prompt, listOf(ChatTurn(true, user)), null, null, streaming) },
                fallbackPrompt = prompt.length + user.length
            ) { onUsage(it) }
        ).flowOn(Dispatchers.IO)
    }

    /**
     * 给新记录完的路线起标题。
     *
     * 用户走完一段路往往不想自己憋标题，「AI 帮我想」给三个不同角度的候选，
     * 点一个就能用，也可以照着改。返回空列表表示没取到（调用方保留用户输入）。
     */
    suspend fun suggestRouteTitles(
        ep: AiEndpoint,
        facts: String,
        context: Context? = null,
        useLocal: Boolean = false,
        localModelId: String? = null
    ): List<String> {
        val prompt = """
为一段刚刚记录的行走路线起标题。只输出一行纯 JSON，禁止 markdown 代码块，不要任何解释：
{"titles":["标题1","标题2","标题3"]}
要求：
- 每个标题 8-16 字，口语自然，像朋友圈或旅行笔记的标题
- 三个角度要不同：一个写地点/风景，一个写感受/心情，一个写行动/过程
- 不要加引号、不要加 # 号、不要加 emoji
- 必须结合给定信息里的地点、时间、距离、时长与用户备注，不要泛泛而谈
        """.trimIndent()
        // 智能分配挑中本机时先试端侧；端侧没就绪或给不出可解析的结果，再回退云端。
        if (useLocal && context != null && LocalLlm.state(context) == LocalLlmState.READY) {
            val raw = LocalLlm.chatOnce(context, "$prompt\n\n$facts", localModelId)
            if (raw != null) {
                val parsed = parseRouteTitles(raw)
                if (parsed.isNotEmpty()) return parsed
            }
        }
        val content = requestOnce(ep, prompt, facts) ?: return emptyList()
        return parseRouteTitles(content)
    }

    private fun parseRouteTitles(content: String): List<String> {
        val root = extractJson(content) ?: return emptyList()
        val arr = root.optJSONArray("titles") ?: return emptyList()
        return (0 until arr.length())
            .mapNotNull { arr.optString(it).trim().takeIf { s -> s.isNotBlank() } }
            .take(3)
    }

    private fun parseCommandIntent(content: String): CommandIntent? {
        val root = extractJson(content) ?: return null
        return CommandIntent(
            kind = root.optString("kind").ifBlank { "none" },
            target = root.optString("target"),
            value = root.optString("value"),
            label = root.optString("label")
        )
    }

    /**
     * 独立的意图判定通道：这一轮回复由谁生成（本地小模型、自定义网关，甚至生成失败）
     * 都不影响判断，统一交给 API 模型判一次「这句话要 App 做什么」，判完由 App 执行。
     *
     * 与 [chatStream] 分开是为了两件事：① 端侧 1.5B 模型不会吐 <intent> 协议，
     * 把判断寄生在回复里等于本地模型永远判成「日常对话」；② 所选模型加载失败时，
     * 消息也不能就此丢掉。只发送「这一句话 + 现场标签」，不带完整历史/画像/坐标。
     */
    suspend fun detectIntent(ep: AiEndpoint, text: String, scene: String = ""): IntentDecision? {
        val prompt = """
你是「MY足迹」旅行 App 的意图分类器。只输出一行 <intent>{...}</intent>，禁止解释、禁止 markdown 代码块。
按用户这句话的真实语义判断，不要依赖固定关键词：
category 六选一：query(问信息、要规划方案) / open(打开页面或攻略、新建计划、点赞收藏) / setting(改开关、切语言、换模型、退出) / publish(写攻略游记、生成图片视频) / record(记足迹、把行程存进列表) / chat(闲聊、追问、翻译润色)。
action：none | command | record_footprint | import_plan | publish_guide | draw | video。
- 只要用文字回答就能满足的（问信息、要行程规划、闲聊）一律 action=none，category 用 query 或 chat。
- 要 App 动数据或打开页面才给具体 action；action=command 时必须同时给 kind。
- kind 只能是 weather_query / weather_city / setting / language / app_language / theme / haptic / settings_page / open / create_plan / open_guide / search_guide / like_guide / fav_guide / delete_trip / edit_footprint / set_chat_mode / set_local_model / exit_app / logout / none。
- kind=set_chat_mode 的 target 取 local(本地模型) 或 api(云端模型)，不要再输出 hybrid。
- kind=open 的 target 只能是 settings/weather/notifications/messages/guide_create/my_plans/year_report/map/memory/profile/discover/ticket_zone/music/course/news/stock/trade/creative/travelers/web_search/ai/upload/offline/trips 之一。
- kind=settings_page 的 target 只能是 main/account/data/ai/ai_routing/general/about/trash/backup/language/theme/haptic/background/widget/profile/log/privacy/security/local_model/voice_library/api_model 之一（「打开设置里的XX」用它）。
- kind=theme 的 target 取 system/light/dark；kind=haptic 取 follow/on/off；kind=app_language 取 zh/en（改界面语言，不是翻译这句话）。
- target/value 用中文名称；label 写一句简短中文说明；confidence 取 0~1。
- 结合现场理解「打开这个」「刚才那篇」，参数可留空让 App 按现场补；「算了」「取消」判 chat；「我去过哪些城市」判 query 不是 record。
- 写操作都要等用户确认，这里只给动作，不要声称已经执行。
示例：<intent>{"category":"query","action":"none","kind":"none","target":"","value":"","label":"回答大理两日行程规划","confidence":0.95}</intent>
        """.trimIndent()
        val input = buildString {
            if (scene.isNotBlank()) appendLine("【用户当前现场】\n$scene\n")
            append("【用户这句话】\n$text")
        }
        val content = requestOnce(ep, prompt, input) ?: return null
        // 模型偶尔只吐裸 JSON 或裹一层说明文字：统一包成 <intent> 交给协议解析器
        val wrapped = if (content.contains("<intent>")) content
        else "<intent>${(extractJson(content) ?: return null)}</intent>"
        return AiIntent.parse(wrapped)
    }

    /**
     * 语义意图判定：这句话到底是想让 App 执行动作，还是只是聊天。
     *
     * 关键词分类（对话页 classify）只在「用户用了常见说法」时命中；换个表达就会
     * 掉进 chat 分支，模型于是嘴上说「已导入」，实际什么都没写。这个函数用模型
     * 直接判语义，返回的 route 与对话页分支名对齐，作为关键词未命中时的兜底。
     */
    suspend fun detectAction(ep: AiEndpoint, text: String): UserAction? {
        val prompt = """
判断用户这句话要不要让旅行 App 执行动作。只输出一行纯 JSON，禁止 markdown 代码块，不要任何解释：
{"action":"import_plan"|"add_footprint"|"create_guide"|"app_command"|"chat"}
判定规则（按语义判断，不要依赖固定说法）：
- import_plan：用户要把刚才对话里出现的行程/计划保存进 App 的行程列表。任何说法都算，例如「导入我的计划」「写进行程里面」「存到我的计划里」「把这个行程记下来」「帮我加到行程」「就按这个安排好」「保存这份安排」。
- add_footprint：用户要把某个地点记成足迹/打卡记录。例如「把洱海记成足迹」「这里打个卡」「标记一下我去过大理」。
- create_guide：用户要把内容/经历整理成一篇攻略发布。例如「写成一篇攻略」「整理成游记发出来」「把这几天写成分享」。
- app_command：用户要操作 App：切设置/开关、换语言、切模型模式、打开页面、查某地天气、删行程、点赞收藏攻略、退出应用。
- chat：只是在问问题、要建议、闲聊、要行程规划本身（「帮我规划大理3日游」属于 chat，因为这会正常生成行程内容，不需要导入）。
特别注意：「帮我规划…」「推荐…」「有什么好吃」这种是 chat，不是 import_plan。
        """.trimIndent()
        val content = requestOnce(ep, prompt, text) ?: return null
        val root = extractJson(content) ?: return null
        return when (root.optString("action").lowercase()) {
            "import_plan" -> UserAction.IMPORT_PLAN
            "add_footprint", "footprint" -> UserAction.ADD_FOOTPRINT
            "create_guide", "guide" -> UserAction.CREATE_GUIDE
            "app_command", "command" -> UserAction.APP_COMMAND
            "chat" -> UserAction.CHAT
            else -> null
        }
    }

    /**
     * 联网搜索：委托给 [WebSearch]（Bing RSS 优先、HTML 兜底、带 URL 与来源站点）。
     * 旧实现直接按 `</li>` 切 b_algo，遇到嵌套列表会把结果截断，且没有链接，
     * 现在统一走唯一真源，AI 对话和发现页拿到的是同一批、可引用的结果。
     * 失败返回空字符串。不需要 API key。
     */
    suspend fun webSearch(query: String): String = withContext(Dispatchers.IO) {
        val hits = WebSearch.search(query)
        if (hits.isEmpty()) return@withContext ""
        // 事实卡抽不出来时也要把网页本身交给模型，不能返回空串让它凭记忆答
        SeaFusion.block(SeaFusion.build(query, hits))
            ?: WebSearch.render(WebSearch.relevantOnly(hits, query).take(6), query)
    }

    /** 结构化结果：给需要展示「引用来源」的界面用 */
    suspend fun webSearchHits(query: String): List<WebHit> = WebSearch.search(query)
}
