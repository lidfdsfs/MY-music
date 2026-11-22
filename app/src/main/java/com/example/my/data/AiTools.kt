package com.example.my.data

import android.content.Context
import com.example.my.data.search.OpenSourceSearch
import com.example.my.data.search.SeaFusion
import com.example.my.data.ticket.TicketDates
import com.example.my.data.ticket.TicketIntent
import com.example.my.model.Footprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * AI 工具调用（Function Calling）。
 *
 * 两个工具：
 *  1. [WEB_SEARCH]  联网搜索——天气、门票票价、美食、攻略、实时消息
 *  2. [STATS]       读本地 Room 里的足迹，按年份/城市聚合
 *
 * 一轮对话的流程（见 AiApi.toolContext）：
 *   用户发消息 → 预检（带 tools 参数请求一次，模型决定要不要调工具）
 *   → 需要就本地执行工具拿到真实结果 → 结果作为上下文喂给模型 → 再流式输出最终回答。
 * 用户侧看不到中间过程，只会发现答案更准。
 *
 * 搜索源说明：DuckDuckGo 在中国大陆直连不可用（实测连接超时，不是慢，是根本连不上），
 * 而 App 的目标用户都在国内，因此把 **Bing 国内站** 作为主搜索源，DuckDuckGo 作为备用
 * （海外网络 / 开启代理时仍然生效）。这样"自动联网搜索"在国内才真的搜得到东西。
 */
object AiTools {

    const val WEB_SEARCH = "web_search"
    const val STATS = "get_footprint_stats"

    /** 天气单独成工具：网页摘要里只有「XX天气预报」这种标题，拿不到真实温度 */
    const val WEATHER = "get_weather"

    /** 地点实搜：高德 POI 给的是真实存在、能导航的地名，比摘要里的"十大必去"实在 */
    const val PLACES = "search_places"

    /** GitHub 免费开源项目检索：只在设置里打开「MCP 搜索」时导出给云端模型 */
    const val OPEN_SOURCE = "search_open_source_projects"

    /** 一次工具调用最多带几条搜索结果回模型（多了浪费 token，少了不够交叉验证） */
    private const val MAX_RESULTS = 6

    /** 单条摘要截断长度 */
    private const val MAX_SNIPPET = 180

    /** DuckDuckGo 连不上一次就不再试，避免每条消息都白等几秒 */
    @Volatile
    private var duckDown = false

    /** 一次工具调用：工具名 + 参数 */
    data class Call(val name: String, val args: JSONObject) {
        val key: String get() = "$name|$args"
    }

    /** 一次工具执行结果 */
    data class Result(val call: Call, val output: String)

    // ---------------------------------------------------------------- schema

    /** 工具 schema（OpenAI Function Calling 格式），注入 chat 请求的 tools 参数 */
    fun toolsJson(includeOpenSource: Boolean = false): JSONArray = JSONArray().apply {
        put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", WEB_SEARCH)
                put(
                    "description",
                    "联网搜索最新的外部信息。当用户询问你知识库里没有、或会随时间变化的信息时调用：" +
                        "目的地天气、景点门票价格、开放时间、美食餐厅推荐、交通方式、旅游攻略、" +
                        "近期活动和新闻、某地小众景点。不确定的事就去搜，不要凭记忆编造。"
                )
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("query", JSONObject().apply {
                            put("type", "string")
                            put(
                                "description",
                                "搜索关键词，用中文、像搜索引擎那样简短，如「大理 天气」「重庆 小众景点」「故宫 门票 价格」"
                            )
                        })
                    })
                    put("required", JSONArray().put("query"))
                })
            })
        })
        put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", STATS)
                put(
                    "description",
                    "查询用户在本 App 里记录的足迹（本地数据库真实数据）：去过哪些城市、总共多少条足迹、" +
                        "某一年去过哪、某个城市去过几次、最近去了哪里。" +
                        "凡是问「我去过哪些城市」「我去年去过哪」「有多少条足迹」「我去过几次大理」" +
                        "这类关于用户自己旅行记录的问题，必须调用本工具，不要瞎猜。"
                )
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("year", JSONObject().apply {
                            put("type", "integer")
                            put("description", "只统计某一年，如「去年」就传去年的年份（4 位数字）。不限年份就不要传这个字段。")
                        })
                        put("city", JSONObject().apply {
                            put("type", "string")
                            put("description", "只统计某个城市，如「大理」。不限城市就不要传这个字段。")
                        })
                    })
                })
            })
        })
        put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", WEATHER)
                put(
                    "description",
                    "查某个城市当前天气与未来几天预报，返回真实温度、天气现象、湿度、风速、降水概率。" +
                        "凡是问「某地天气怎么样」「要不要带伞」「冷不冷」「该穿什么」都要调用本工具，" +
                        "不要凭记忆说天气——天气每天都在变，记忆里的数字一定是错的。"
                )
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("city", JSONObject().apply {
                            put("type", "string")
                            put("description", "城市名，如「大理」「重庆」。上文已经明确提到城市时直接用它。")
                        })
                        put("days", JSONObject().apply {
                            put("type", "integer")
                            put("description", "要几天预报，1-7，默认 3。只问现在天气就传 1。")
                        })
                    })
                    put("required", JSONArray().put("city"))
                })
            })
        })
        put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", PLACES)
                put(
                    "description",
                    "在地图上搜索某城市的真实地点（景点、美食、餐厅、公园、博物馆、古镇、民宿等），" +
                        "返回地点名、类型、地址。用户问「XX有什么好玩的 / 好吃的 / 小众的地方」「附近有什么」时调用。" +
                        "拿到的是地图上真实存在的具体地名，优先用它们来推荐，不要用泛泛的分类词敷衍。"
                )
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("keyword", JSONObject().apply {
                            put("type", "string")
                            put("description", "要找什么，如「小众景点」「火锅」「博物馆」。不要带城市名。")
                        })
                        put("city", JSONObject().apply {
                            put("type", "string")
                            put("description", "限定城市，如「重庆」。用户问的就是某个城市时填上。")
                        })
                    })
                    put("required", JSONArray().put("keyword"))
                })
            })
        })
        if (includeOpenSource) {
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", OPEN_SOURCE)
                    put(
                        "description",
                        "在 GitHub 上搜索带明确开源许可证的免费开源项目。只在用户想找开源项目、" +
                            "免费项目、可复用的代码/技术方案时调用；返回仓库名、Star、语言、" +
                            "许可证（License）和仓库地址。不要把它用于旅游、天气、票务问题。"
                    )
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("query", JSONObject().apply {
                                put("type", "string")
                                put(
                                    "description",
                                    "搜索关键词，如「安卓 离线 语音识别」「kotlin 图片压缩」。" +
                                        "不要带「免费开源」这几个字，工具会自动过滤许可证。"
                                )
                            })
                            put("language", JSONObject().apply {
                                put("type", "string")
                                put("description", "可选，限定主要语言，如 Kotlin、Python、TypeScript。")
                            })
                            put("min_stars", JSONObject().apply {
                                put("type", "integer")
                                put(
                                    "description",
                                    "可选，最低 Star 数；用户没提就不要传，避免把可用的小项目过滤掉。"
                                )
                            })
                            put("limit", JSONObject().apply {
                                put("type", "integer")
                                put("description", "返回条数，1-10，默认 6。")
                            })
                        })
                        put("required", JSONArray().put("query"))
                    })
                })
            })
        }
    }

    // ---------------------------------------------------------------- 执行

    /** 依次执行工具调用；同一调用只跑一次，空结果的丢弃 */
    suspend fun run(calls: List<Call>, context: Context, footprints: List<Footprint>): List<Result> =
        calls.distinctBy { it.key }
            .map { Result(it, execute(it.name, it.args, context, footprints)) }
            .filter { it.output.isNotBlank() }

    /** 执行单个工具，返回喂给模型的纯文本结果 */
    suspend fun execute(
        name: String,
        args: JSONObject,
        context: Context,
        footprints: List<Footprint> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        when (name) {
            WEB_SEARCH -> webSearch(args.optString("query"), context)
            STATS -> footprintStats(footprints.ifEmpty { readFootprints(context) }, args)
            WEATHER -> weatherTool(args.optString("city"), args.optInt("days", 3))
            PLACES -> placeTool(args.optString("keyword"), args.optString("city"))
            OPEN_SOURCE -> openSourceTool(
                args.optString("query"),
                args.optString("language").ifBlank { null },
                args.optInt("min_stars", 0),
                args.optInt("limit", 6)
            )
            else -> "未知工具：$name"
        }
    }

    /** 把工具结果渲染成注入 system 的上下文块：写清"这是真实数据"和"查不到就说查不到" */
    fun renderBlock(results: List<Result>): String {
        if (results.isEmpty()) return ""
        val body = results.joinToString("\n\n") { r ->
            val label = when (r.call.name) {
                WEB_SEARCH -> "联网搜索「${r.call.args.optString("query")}」的结果"
                STATS -> "本地足迹数据库查询结果"
                WEATHER -> "「${r.call.args.optString("city")}」实时天气"
                PLACES -> "地图实搜「${r.call.args.optString("keyword")}」的结果"
                OPEN_SOURCE -> "GitHub 免费开源项目检索「${r.call.args.optString("query")}」的结果"
                else -> r.call.name
            }
            "【$label】\n${r.output}"
        }
        // 地点清单最容易变成"照抄地址"：实测把 10 条原样贴给模型，它会一条不落地全抄下来，
        // 连"保税体验景区"这种和用户诉求无关的也推荐。这里对地点结果单独给挑选规则。
        val hasPlaces = results.any { it.call.name == PLACES }
        return buildString {
            appendLine("下面是你在本轮回答前刚刚查到的真实数据（刚刚获取，不是记忆）：")
            appendLine(body)
            appendLine()
            appendLine("回答要求：")
            appendLine("1. 优先使用上面这些真实信息里的具体数字、地名、价格和天气，它们比你的训练数据新，冲突时以上面为准；")
            appendLine("2. 如果结果里确实没有用户问的那一项（或写着没查到），就如实说没查到，不要用旧知识补一个看似合理的答案；")
            if (hasPlaces) {
                appendLine(
                    "3. 上面是地点清单：只挑 3-5 个最贴合用户诉求的说，每个用一句话讲清「适合什么玩法 / 为什么值得去」；" +
                        "不要罗列全部，也不要把地址整段抄出来（用户点开就能导航），更不要推荐明显不符合诉求的地点；"
                )
            }
            append("${if (hasPlaces) "4" else "3"}. 不要向用户描述你「调用了搜索/查询工具」的过程，直接给结论。")
        }
    }

    // ---------------------------------------------------------------- 规则路由
    // 不走 function calling 的场景（Anthropic / Gemini 协议、预检失败、纯本地模式）用关键词
    // 兜底，保证这两个能力在任何模型上都还能用。

    /** 预检门槛：明显不像需要外部信息的消息（寒暄、写文案、翻译）就别白跑一次请求 */
    fun maybeNeedsTool(text: String): Boolean {
        val t = text.trim()
        if (t.length < 4) return false
        val hints = listOf(
            "天气", "气温", "温度", "几度", "下雨", "带伞", "冷不冷", "热不热",
            "门票", "票价", "价格", "多少钱", "开放时间", "预约",
            "美食", "小吃", "好吃", "餐厅", "吃什么",
            "景点", "好玩", "小众", "值得去", "必去", "打卡", "推荐",
            "攻略", "路线", "怎么去", "交通", "住宿", "酒店",
            "去过", "足迹", "统计", "去过哪", "去过几次",
            "附近", "周边", "哪家", "怎么安排", "值得", "古镇", "冷门",
            "火车票", "火车", "高铁", "动车", "车次", "余票", "12306", "购票",
            "机票", "航班", "飞机", "比价",
            "播放", "放一下", "听歌", "歌曲", "搜歌", "歌词", "暂停", "下一首", "上一首",
            "想听", "找歌", "歌单", "轻快", "通勤听", "背景音乐", "BGM"
        )
        return hints.any { t.contains(it) }
    }

    /** 这四个是「外部信息查询」工具；其余名字来自 App 能力注册表 */
    fun isInfoTool(name: String): Boolean =
        name == WEB_SEARCH || name == STATS || name == WEATHER || name == PLACES ||
            name == OPEN_SOURCE

    /**
     * App 内数据查询的预检门槛。
     *
     * 这是「要不要多跑一次预检请求」的性能门槛，不是意图判断：意图仍由模型定。
     * 只有接进 AppState 的对话链路才会启用，避免所有消息都白白多一次请求。
     */
    fun maybeNeedsAppData(text: String): Boolean {
        val t = text.trim()
        if (t.length < 4) return false
        return listOf(
            "我的行程", "我的攻略", "我的足迹", "我的计划", "收藏", "点赞",
            "通知", "提醒", "清单", "列表", "统计", "去过", "足迹", "设置了", "开关"
        ).any { t.contains(it) }
    }

    /** 关键词路由：返回应该执行的工具调用（无命中则空） */
    fun ruleRoute(
        userText: String,
        allowWeb: Boolean,
        allowNetwork: Boolean = allowWeb,
        defaultFromCity: String = ""
    ): List<Call> {
        val t = userText.trim()
        if (t.isBlank() || isPureTextTask(t)) return emptyList()
        val calls = mutableListOf<Call>()

        // —— 票务优先：能抽出「出发地/到达地」就直接走 App 内置接口，
        // 不把 12306 余票、航班比价交给网页搜索。只有目的地的问句（「我要去秀山」）
        // 用调用方给的常住城市补出发地；抽不出城市时继续走下面的
        // 原有搜索兜底，避免把「机票怎么买」这类规则问题也塞给票务工具。
        if (allowNetwork) {
            TicketIntent.parse(t, defaultFromCity)?.let { q ->
                val args = JSONObject().put("from_city", q.from).put("to_city", q.to)
                q.dateHint?.let { args.put("date", TicketDates.resolve(it)) }
                q.budget?.let { args.put("budget", it) }
                q.timeHint.takeIf { it.isNotBlank() }?.let { args.put("time_hint", it) }
                val tool = if (q.kind == TicketIntent.Kind.FLIGHT) "query_flights"
                else "query_train_tickets"
                return listOf(Call(tool, args))
            }
        }

        // —— 描述式找歌：说场景/心情/「像某首歌」时，交给 App 内的候选核对能力，
        // 不让它落到网页搜索里搜出一堆音源站。
        if (allowNetwork && calls.isEmpty()) {
            val musicHints = listOf(
                "想听", "听点", "找几首", "找歌", "推荐几首", "像《", "像\"",
                "歌单", "背景音乐", "BGM", "轻快", "通勤听"
            )
            // 「做个/来个/生成歌单」要的是一整份可播放队列，不是几条候选
            val moodPlaylist =
                (t.contains("歌单") && listOf("做个", "来个", "来一份", "生成", "安排", "组一", "建个", "给我一个", "整一个").any { t.contains(it) }) ||
                    t.contains("AI歌单") || t.contains("情绪歌单") || t.contains("智能歌单")
            if (moodPlaylist) {
                calls += Call("make_mood_playlist", JSONObject().put("description", t))
            } else if (musicHints.any { t.contains(it) }) {
                calls += Call("find_music_by_description", JSONObject().put("description", t))
            }
        }

        // —— 足迹统计：问自己旅行记录的一律先查本地
        val statNouns = t.contains("去过") || t.contains("足迹") || t.contains("打卡") ||
            t.contains("城市") || t.contains("统计")
        // 注意：这里只放"问数量/问范围"的词。「最近」不算——「重庆这个城市最近有什么活动」
        // 问的是活动不是统计，误判会把联网搜索挤掉。
        val statAsk = t.contains("哪些") || t.contains("哪几") || t.contains("哪里") || t.contains("哪儿") ||
            t.contains("多少") || t.contains("几条") || t.contains("几个") || t.contains("几次") ||
            t.contains("统计") || t.contains("去过哪") || t.contains("去年") || t.contains("今年")
        if (statNouns && statAsk) {
            val args = JSONObject()
            extractYearHint(t)?.let { args.put("year", it) }
            extractCityHint(t)?.let { args.put("city", it) }
            calls += Call(STATS, args)
        }

        // —— 天气：能认出城市就直接查实况，比搜网页准得多
        if (allowWeb && calls.isEmpty() && WEATHER_WORDS.any { t.contains(it) }) {
            val city = extractWeatherCity(t) ?: CityCoords.detectIn(t)
            if (city != null) {
                val days = if (t.contains("一周") || t.contains("未来几天") || t.contains("这几天")) 7 else 3
                calls += Call(WEATHER, JSONObject().put("city", city).put("days", days))
            }
        }

        // —— 地点实搜：问"有什么好玩/好吃/小众"时，高德能给出一串真实地名
        if (allowWeb && calls.isEmpty() && looksPlaceQuery(t)) {
            val kw = placeKeyword(t)
            if (kw.isNotBlank()) {
                val args = JSONObject().put("keyword", kw)
                CityCoords.detectIn(t)?.let { args.put("city", it) }
                calls += Call(PLACES, args)
            }
        }

        // —— 联网搜索：兜底的实时信息
        if (allowWeb && calls.isEmpty() && looksLikeWebQuery(t)) {
            calls += Call(WEB_SEARCH, JSONObject().put("query", buildSearchQuery(t)))
        }
        return calls
    }

    /** 从「重庆有什么小众景点」里抽出「小众景点」当检索词，去掉城市名和语气词 */
    private fun placeKeyword(t: String): String {
        val topics = listOf(
            "小众景点", "景点", "美食", "小吃", "餐厅", "火锅", "古镇",
            "公园", "博物馆", "夜景", "咖啡馆", "民宿"
        )
        topics.firstOrNull { t.contains(it) }?.let { return it }
        if (t.contains("吃") || t.contains("饭")) return "美食"
        return "景点"
    }

    /** 翻译/润色/总结/续写这类纯文本加工，不需要外部信息 */
    private fun isPureTextTask(t: String): Boolean =
        t.startsWith("请把下面这段") || t.startsWith("请润色") ||
            t.startsWith("请用简洁") || t.startsWith("请顺着") || t.startsWith("请处理下面")

    private fun looksLikeWebQuery(t: String): Boolean {
        if (t.contains("我去过") || t.contains("我的足迹")) return false
        // "把这里写成一篇攻略"是创作任务，要搜的应该由写作流程自己决定，
        // 拿整句去搜只会得到一堆无关结果
        if (t.contains("攻略") && listOf("写", "生成", "整理", "做一篇", "帮我做").any { t.contains(it) }) return false
        val webHints = listOf(
            "天气", "气温", "温度", "几度", "下雨", "带伞",
            "门票", "票价", "多少钱", "开放时间", "预约", "攻略",
            "美食", "小吃", "好吃", "餐厅", "景点", "小众", "好玩", "值得去", "必去",
            "怎么去", "交通", "住宿", "酒店", "推荐", "最近", "新闻", "现在",
            "火车票", "高铁", "动车", "车次", "余票", "机票", "飞机票", "航班", "比价"
        )
        return webHints.any { t.contains(it) }
    }

    /** 去掉客套话，压成搜索引擎友好的关键词 */
    private fun buildSearchQuery(t: String): String {
        var q = t
        listOf(
            "帮我", "帮忙", "请问", "请", "我想", "想", "麻烦", "你", "我",
            "一下", "推荐", "有什么", "怎么样", "如何", "吗", "呢", "吧", "？", "?", "，", ",", "。"
        ).forEach { q = q.replace(it, " ") }
        q = q.replace(Regex("\\s+"), " ").trim()
        return if (q.length < 2) t.take(30) else q.take(40)
    }

    /**
     * 工具路由器（ReAct）给的联网检索词必须和用户原句同主题。
     *
     * 实测翻车：用户问「重庆交通大学目前校长是谁」，模型因为 App 是旅行助手，
     * 把 query 写成了「重庆旅游攻略 行程」，结果气泡下面挂了 8 篇景点攻略，
     * 回答却说「网上暂未查到具体信息」。这里按「核心实体」硬校验：
     * 原句里有明确机构/职务，模型给的词一个都没带上，就强制用原句重搜。
     */
    fun guardSearchQuery(call: Call, userText: String): Call {
        if (call.name != WEB_SEARCH) return call
        val q = call.args.optString("query").trim()
        if (q.isBlank()) {
            return Call(call.name, JSONObject(call.args.toString()).put("query", userText.take(60)))
        }
        // 所有用户问题都要守住检索词：强实体、通用主题词、内容字覆盖率三层校验，
        // 只校正「完全串台」的词，正常改写（规划大理3日旅行 → 大理3日游攻略）照放。
        val terms = WebSearch.topicTerms(userText)
        if (terms.isEmpty()) return call
        if (terms.any { q.contains(it, ignoreCase = true) }) return call
        if (WebSearch.queryOnTopic(q, userText)) return call
        // 串台了就退回用户原话：第一步本来就该直接拿用户发送的内容去搜，
        // 而不是继续用模型或规则猜出来的词
        val fixed = userText.trim().take(60)
            .ifBlank { WebSearch.preciseQuery(userText) }
        return Call(
            call.name,
            JSONObject(call.args.toString()).put("query", fixed)
        )
    }

    private fun extractYearHint(t: String): Int? {
        val now = Calendar.getInstance().get(Calendar.YEAR)
        Regex("(20\\d{2})\\s*年").find(t)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        return when {
            t.contains("前年") -> now - 2
            t.contains("去年") -> now - 1
            t.contains("今年") || t.contains("本年") -> now
            else -> null
        }
    }

    /**
     * 从「大理去过几次」「我去过几次大理」这类句子里抠城市名。
     *
     * 这里不能图省事写成「动词前 2-5 个汉字」：「我去年去过哪些城市」会把「我去年」
     * 当成城市，接着按这个城市过滤本地数据 → 一条都不命中 → 回答"没有关于我去年的记录"。
     * 所以候选里只要含代词/时间字就丢掉，宁可当成"没指定城市"。
     */
    private fun extractCityHint(t: String): String? {
        val candidates = mutableListOf<String>()
        Regex("([\\u4e00-\\u9fa5]{2,4})(?:我)?(?:去过|去了|到过|玩过)")
            .findAll(t).forEach { candidates += it.groupValues[1] }
        Regex("(?:去过|去了|到过)(?:几次|一次|两次)?([\\u4e00-\\u9fa5]{2,4})")
            .findAll(t).forEach { candidates += it.groupValues[1] }
        val stopChars = "我你他她它都也就还刚曾前后上年月日次多少几哪什么这那里"
        return candidates.firstOrNull { c ->
            c.length in 2..4 && c.none { it in stopChars } && c != "城市" && c != "哪里"
        }
    }

    // ---------------------------------------------------------------- 工具一：联网搜索

    private suspend fun webSearch(query: String, context: Context): String {
        if (query.isBlank()) return "搜索关键词为空，未执行搜索"
        // 天气类问题先走天气接口：网页摘要里通常只有"XX天气预报"这种标题，没有真实温度
        weatherDigest(query, context)?.let { return it }

        val parts = mutableListOf<String>()
        // 问"去哪玩/吃什么"时，高德 POI 能给出一串真实存在、带地址的地点名，
        // 比搜索结果页里"十大必去景点"这类标题党摘要实在得多，所以优先附上
        if (looksPlaceQuery(query)) {
            amapPlaces(query)?.let { parts += it }
        }
        // 与「自动联网检索」共用 WebSearch：相同关键词命中 10 分钟内存缓存不重复联网；
        // WebSearch 无结果时再退回本文件里的 RSS / HTML / DDG 三源，保证能力不丢。
        // 拿到原始结果后统一进 v3 清洗 + 裁决管线（去重/转载聚类/来源分层/相关性/打分），
        // 模型拿到的是"事实卡"而不是一堆网页摘要 —— 弱模型照卡说话就不会编数字。
        val webHits: List<WebHit> = WebSearch.search(query).ifEmpty {
            val fallback: List<Hit> = searchBingRss(query)
                .ifEmpty { searchBing(query) }
                .ifEmpty { searchDuckDuckGo(query) }
            fallback.map { WebHit(it.title, it.snippet, "", it.site) }
        }
        if (webHits.isNotEmpty()) {
            val deck = SeaFusion.build(query, webHits)
            val block = SeaFusion.block(deck)
            // 清洗后没抽出结构化事实 ≠ 没查到：网页标题与摘要本身就是资料。
            // 以前这里直接塞 noEvidenceBlock，端侧小模型会照着复读
            // 「网上暂未查到具体信息」，可气泡下面又挂着一堆参考来源。
            parts += when {
                block != null -> block
                else -> {
                    val relevant = WebSearch.relevantOnly(webHits, query)
                    if (relevant.isNotEmpty()) WebSearch.render(relevant.take(6), query)
                    else SeaFusion.noEvidenceBlock(query, 0)
                }
            }
        }
        if (parts.isEmpty()) {
            return "没有搜到「$query」的结果（可能网络受限或关键词过于生僻）。请如实告诉用户这次没查到，" +
                "可以基于你的常识给方向性建议，但要说明不是实时信息。"
        }
        return parts.joinToString("\n\n")
    }

    /** 这句话是不是在问"去哪儿玩 / 吃什么"（这类问题高德 POI 比网页摘要靠谱） */
    private fun looksPlaceQuery(q: String): Boolean {
        val hints = listOf("景点", "好玩", "小众", "去哪", "玩什么", "打卡", "美食", "好吃", "小吃", "餐厅", "古镇", "公园", "博物馆")
        return hints.any { q.contains(it) }
    }

    /** 高德 POI 实搜：把"城市 + 关键词"拆开，城市做硬限定，关键词保留主题词 */
    private suspend fun amapPlaces(query: String): String? {
        val city = CityCoords.detectIn(query)
        val rest = if (city != null) query.replace(city, " ") else query
        val topics = listOf("小众", "景点", "美食", "小吃", "餐厅", "古镇", "公园", "博物馆", "夜景")
            .filter { rest.contains(it) }
        val keyword = topics.joinToString("").ifBlank { rest.trim() }
        if (keyword.isBlank()) return null
        val pois = runCatching {
            AmapApi.searchPoi(keyword, city = city.orEmpty(), count = 8, cityLimit = city != null)
        }.getOrDefault(emptyList())
        if (pois.isEmpty()) return null
        return buildString {
            appendLine("高德地图实搜地点（真实存在，可直接导航）：")
            pois.take(8).forEach { p ->
                append("- ${p.name}")
                if (p.address.isNotBlank()) append("（${p.address.take(40)}）")
                appendLine()
            }
        }.trim()
    }

    /** 旅游站点命中的内容对"去哪玩/吃什么"明显更有用，优先排；顺带按标题去重（百科常占好几个位） */
    private fun prioritize(hits: List<Hit>): List<Hit> {
        val travel = listOf(
            "zhihu.com", "mafengwo", "ctrip", "qyer", "xiaohongshu", "dianping",
            "trip.com", "tuniu", "ly.com", "tripadvisor", "sohu.com", "163.com"
        )
        val (good, rest) = hits.partition { h -> travel.any { h.site.contains(it, true) } }
        return (good + rest).distinctBy { it.title.take(12) }
    }

    /** 天气类提问 → 走 App 自己的天气接口拿真实数字（比搜索摘要可靠得多） */
    private suspend fun weatherDigest(query: String, context: Context): String? {
        val weatherWords = listOf("天气", "气温", "温度", "几度", "下雨", "带伞", "冷不冷", "热不热", "湿度")
        if (weatherWords.none { query.contains(it) }) return null
        val city = extractWeatherCity(query) ?: return null
        val hit = runCatching { WeatherApi.searchCity(city).firstOrNull() }.getOrNull() ?: return null
        val snap = runCatching { WeatherApi.fetch(hit.name, hit.lat, hit.lon) }.getOrNull() ?: return null
        return buildString {
            appendLine("实时天气（${snap.city}，数据来源 Open-Meteo，更新于 ${snap.updatedAt}）：")
            appendLine("- 现在 ${snap.temp}℃ ${snap.desc}，湿度 ${snap.humidity}%，风速 ${snap.wind}km/h")
            snap.days.take(3).forEach { d ->
                appendLine("- ${d.day}：${d.desc}，${d.low}~${d.high}℃，降水概率 ${d.percent}%")
            }
            append("以上为真实实况数据，回答气温/穿衣/带伞问题时直接用它，不要另编数字。")
        }
    }

    private fun extractWeatherCity(query: String): String? {
        val m = Regex("([\\u4e00-\\u9fa5]{2,5}?)(?:现在|今天|明天|后天|的)*(?:天气|气温|温度|几度|下雨)")
            .find(query)?.groupValues?.getOrNull(1)
        val bad = setOf("现在", "今天", "明天", "后天", "这里", "那里", "这个", "那个", "最近", "未来", "这边", "那边")
        return m?.trim()?.takeIf { it.length >= 2 && it !in bad }
    }

    // ---------------------------------------------------------------- 工具三：天气

    private val WEATHER_WORDS =
        listOf("天气", "气温", "温度", "几度", "下雨", "带伞", "冷不冷", "热不热", "湿度", "穿什么")

    /**
     * 天气工具。以前它藏在 web_search 里，模型必须先"决定去搜网页"才可能拿到温度，
     * 而且摘要里通常只有标题没有数字。独立成工具后，模型对"要不要带伞"这类问题的调用直觉准得多。
     */
    private suspend fun weatherTool(cityRaw: String, days: Int): String {
        val city = cityRaw.trim()
        if (city.isBlank()) return "没给城市名，无法查询天气。请先问用户是哪个城市。"
        val hit = runCatching { WeatherApi.searchCity(city).firstOrNull() }.getOrNull()
            ?: return "没有找到「$city」这个城市。请如实告诉用户没查到，不要用别的城市的天气充数。"
        val snap = runCatching { WeatherApi.fetch(hit.name, hit.lat, hit.lon) }.getOrNull()
            ?: return "「$city」的天气接口这次没返回数据。请告诉用户暂时查不到，不要编造温度。"
        val n = days.coerceIn(1, 7)
        return buildString {
            appendLine("【${snap.city} 天气】数据来源 Open-Meteo，${snap.updatedAt}")
            appendLine("实况：${snap.temp}℃ ${snap.desc}，湿度 ${snap.humidity}%，风速 ${snap.wind}km/h")
            snap.days.take(n).forEach { d ->
                appendLine("${d.day}：${d.desc}，${d.low}~${d.high}℃，降水概率 ${d.percent}%")
            }
            append("以上是接口返回的真实数据，回答气温/穿衣/带伞时直接引用，不要另编数字。")
        }
    }

    // ---------------------------------------------------------------- 工具四：地点实搜

    private suspend fun placeTool(keywordRaw: String, cityRaw: String): String {
        val kw = keywordRaw.trim().ifBlank { "景点" }
        val city = cityRaw.trim().ifBlank { CityCoords.detectIn(kw).orEmpty() }
        val pois = runCatching {
            AmapApi.searchPoi(kw, city = city, count = 8, cityLimit = city.isNotBlank())
        }.getOrDefault(emptyList())
        if (pois.isEmpty()) {
            return "在${if (city.isNotBlank()) "「$city」" else "地图上"}没有检索到「$kw」相关地点" +
                "（可能关键词太宽或网络受限）。如实告诉用户这次没查到，可以给方向性建议，但不要编造具体店名。"
        }
        return buildString {
            appendLine("高德地图实搜「$kw」${if (city.isNotBlank()) "（限定 $city）" else ""}：")
            pois.take(8).forEach { p ->
                append("- ${p.name}")
                if (p.typeName.isNotBlank()) append("｜${p.typeName}")
                if (p.address.isNotBlank()) append("（${p.address.take(30)}）")
                appendLine()
            }
            append("这些是地图上真实存在的地点，可直接推荐给用户并建议导航。")
        }
    }

    private suspend fun openSourceTool(
        queryRaw: String,
        language: String?,
        minStars: Int,
        limit: Int
    ): String {
        val query = queryRaw.trim()
        if (query.isBlank()) {
            return "搜索开源项目需要关键词；请让用户说清想找什么方向，例如「安卓 图片压缩」。"
        }
        return runCatching {
            val projects = OpenSourceSearch.search(query, language, minStars, limit)
            OpenSourceSearch.render(query, projects)
        }.getOrElse { e ->
            "GitHub 开源项目检索失败（${e.message?.take(60) ?: "网络异常"}）。" +
                "如实告诉用户这次没查到，不要编造仓库名、Star 或 License。"
        }
    }

    // ---- 搜索源 ----

    private data class Hit(val title: String, val snippet: String, val site: String)

    /**
     * Bing 的 RSS 输出（`&format=rss`）：结果与查询词严格对应，体积只有 HTML 页的十分之一，
     * 移动网络下明显更快，解析也不用对付嵌套标签，所以排在 HTML 抓取前面。
     */
    private fun searchBingRss(query: String): List<Hit> = runCatching {
        val url = "https://cn.bing.com/search?q=" + URLEncoder.encode(query, "UTF-8") + "&format=rss"
        val xml = httpGet(url, 9000) ?: return emptyList()
        Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { m ->
            val block = m.groupValues[1]
            fun tag(name: String): String =
                Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL)
                    .find(block)?.groupValues?.getOrNull(1).orEmpty()
            val title = cleanText(tag("title"))
            if (title.isBlank()) return@mapNotNull null
            Hit(title.take(90), cleanText(tag("description")).take(MAX_SNIPPET), hostOf(tag("link")))
        }.toList()
    }.getOrDefault(emptyList())

    /** Bing 国内站：大陆网络可直连，速度快、中文结果质量好，作为主搜索源 */
    private fun searchBing(query: String): List<Hit> = runCatching {
        val url = "https://cn.bing.com/search?q=" + URLEncoder.encode(query, "UTF-8") + "&setlang=zh-CN&ensearch=0"
        val html = httpGet(url, 9000) ?: return emptyList()
        // 按 b_algo 起始位置切块，避免 </li> 嵌套把结果截断；
        // class 后面可能跟别的类名（b_algo b_algoBorder），所以不要求紧跟引号
        val starts = Regex("<li class=\"b_algo[\"\\s]").findAll(html).map { it.range.first }.toList()
        if (starts.isEmpty()) return emptyList()
        starts.mapIndexedNotNull { i, start ->
            val end = if (i + 1 < starts.size) starts[i + 1] else minOf(html.length, start + 6000)
            val block = html.substring(start, end)
            val titleHtml = Regex("<h2>.*?<a[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.getOrNull(1) ?: return@mapIndexedNotNull null
            val snippetHtml = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.getOrNull(1).orEmpty()
            val href = Regex("<h2>.*?<a[^>]*href=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.getOrNull(1).orEmpty()
            val title = cleanText(titleHtml)
            if (title.isBlank()) return@mapIndexedNotNull null
            Hit(title.take(90), cleanText(snippetHtml).take(MAX_SNIPPET), hostOf(href))
        }
    }.getOrDefault(emptyList())

    /**
     * DuckDuckGo 备用源（html 版结果页）。
     * 大陆网络下连接会直接超时，所以只在 [duckDown] 为 false 时尝试一次；
     * 失败即置位，本进程内不再走这条路。
     */
    private fun searchDuckDuckGo(query: String): List<Hit> {
        if (duckDown) return emptyList()
        val hits = runCatching {
            val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")
            val html = httpGet(url, 6000) ?: return emptyList()
            val titles = Regex("<a[^>]+class=\"result__a[^\"]*\"[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
                .findAll(html).toList()
            if (titles.isEmpty()) return emptyList()
            val snippets = Regex("<a[^>]+class=\"result__snippet[^\"]*\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
                .findAll(html).map { cleanText(it.groupValues[1]) }.toList()
            titles.mapIndexedNotNull { i, m ->
                val title = cleanText(m.groupValues[2])
                if (title.isBlank()) return@mapIndexedNotNull null
                Hit(
                    title.take(90),
                    snippets.getOrNull(i).orEmpty().take(MAX_SNIPPET),
                    hostOf(ddgRealUrl(m.groupValues[1]))
                )
            }
        }.getOrDefault(emptyList())
        if (hits.isEmpty()) duckDown = true
        return hits
    }

    /** DuckDuckGo 的结果链接是跳转链接，真实地址在 uddg 参数里 */
    private fun ddgRealUrl(href: String): String = runCatching {
        val raw = if (href.startsWith("//")) "https:$href" else href
        Regex("[?&]uddg=([^&]+)").find(raw)?.groupValues?.getOrNull(1)
            ?.let { URLDecoder.decode(it, "UTF-8") } ?: raw
    }.getOrDefault(href)

    private fun hostOf(href: String): String = runCatching {
        val h = URL(href).host.orEmpty()
        h.removePrefix("www.")
    }.getOrDefault("")

    private fun httpGet(url: String, timeoutMs: Int): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            // 联网搜索兜底源也走无痕 + 每请求断开，不在后台留 keep-alive socket
            WebIncognito.applyTo(this)
        }
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** 去标签 + 解 HTML 实体 + 压空白 */
    private fun cleanText(html: String): String {
        var s = html.replace(Regex("<[^>]+>"), "")
        s = s.replace("&nbsp;", " ").replace("&ensp;", " ").replace("&emsp;", " ").replace("&middot;", "·")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
        s = Regex("&#(\\d+);").replace(s) { m ->
            m.groupValues[1].toIntOrNull()?.let { cp -> runCatching { String(Character.toChars(cp)) }.getOrDefault("") } ?: ""
        }
        s = Regex("&#x([0-9a-fA-F]+);").replace(s) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { cp -> runCatching { String(Character.toChars(cp)) }.getOrDefault("") } ?: ""
        }
        return s.replace(Regex("\\s+"), " ").trim()
    }

    // ---------------------------------------------------------------- 工具二：足迹统计

    /** 读本地 Room（App 里足迹的权威存储）；空表返回空列表 */
    private suspend fun readFootprints(context: Context): List<Footprint> =
        runCatching { com.example.my.data.local.OfflineCache.loadFootprints(context) }.getOrNull()
            ?: emptyList()

    private fun footprintStats(all: List<Footprint>, args: JSONObject): String {
        if (all.isEmpty()) {
            return "本地数据库里没有任何足迹记录。请如实告诉用户还没记录过足迹（不要说成「查不到」像是出错），" +
                "并可以顺手告诉他点「记一笔足迹」就能开始记录。"
        }
        val year = args.optInt("year", 0).takeIf { it in 1970..2100 }
        val city = args.optString("city").trim().takeIf { it.isNotBlank() }
        val allCities = all.mapNotNull { c -> c.city.trim().takeIf { it.isNotBlank() && !it.contains("定位中") } }
            .distinct()

        val scoped = when {
            city != null -> all.filter { it.city.contains(city, true) || it.name.contains(city, true) }
            year != null -> all.filter { yearOf(it) == year }
            else -> all
        }

        if (city != null && scoped.isEmpty()) {
            return "本地共有 ${all.size} 条足迹，覆盖 ${allCities.size} 个城市，但没有任何关于「$city」的记录。" +
                "去过的城市：${allCities.joinToString("、")}。请如实告诉用户没去过这里，不要编。"
        }

        return buildString {
            if (city != null) {
                appendLine("「$city」相关足迹 ${scoped.size} 条（本地足迹共 ${all.size} 条）")
                val years = scoped.mapNotNull { yearOf(it) }.groupingBy { it }.eachCount().toSortedMap()
                if (years.isNotEmpty()) {
                    appendLine("到访年份：" + years.entries.joinToString("、") { "${it.key} 年 ${it.value} 次" })
                }
                appendLine("明细：")
                scoped.sortedByDescending { dateKey(it) }.take(6).forEach { appendLine(describe(it)) }
            } else if (year != null) {
                appendLine("${year} 年足迹 ${scoped.size} 条，涉及 ${citiesOf(scoped).size} 个城市")
                if (scoped.isEmpty()) {
                    appendLine("（该年没有任何记录。本地总共有 ${all.size} 条足迹，年份分布见下）")
                } else {
                    appendLine("城市：${citiesOf(scoped).joinToString("、")}")
                    val months = scoped.mapNotNull { monthOf(it) }.groupingBy { it }.eachCount().toSortedMap()
                    if (months.isNotEmpty()) {
                        appendLine("月份分布：" + months.entries.joinToString("、") { "${it.key}月 ${it.value}条" })
                    }
                    appendLine("明细：")
                    scoped.sortedByDescending { dateKey(it) }.take(8).forEach { appendLine(describe(it)) }
                }
                appendLine("（全部年份：${yearOverview(all)}）")
            } else {
                appendLine("足迹总数：${all.size} 条；去过的城市：${allCities.size} 个")
                appendLine("按年份：${yearOverview(all)}")
                appendLine("最近 5 条：")
                all.sortedByDescending { dateKey(it) }.take(5).forEach { appendLine(describe(it)) }
                if (allCities.size <= 40) appendLine("全部城市（去重）：${allCities.joinToString("、")}")
            }
            append("以上是本地数据库的真实统计结果，直接据此回答，数字不要改动。")
        }.trim()
    }

    private fun citiesOf(list: List<Footprint>): List<String> =
        list.mapNotNull { it.city.trim().takeIf { c -> c.isNotBlank() && !c.contains("定位中") } }.distinct()

    /** 按年聚合，新→旧，最多列 8 年 */
    private fun yearOverview(all: List<Footprint>): String {
        val byYear = all.groupBy { yearOf(it) }
        if (byYear.keys.all { it == null }) return "日期未记录，无法按年统计（共 ${all.size} 条）"
        return byYear.entries
            .sortedByDescending { it.key ?: 0 }
            .take(8)
            .joinToString("；") { (y, list) ->
                if (y == null) "年份未知 ${list.size} 条"
                else "$y 年 ${list.size} 条（${citiesOf(list).take(6).joinToString("、")}）"
            }
    }

    private fun describe(f: Footprint): String {
        val name = f.name.trim().ifBlank { "未命名地点" }
        val cityPart = if (f.city.isBlank()) "" else "${f.city} · "
        return "  · ${dateText(f)} $cityPart$name"
    }

    // ---- 日期处理：dateMillis 优先，老数据回退解析 date 字符串（"2025年5月" / "2025-05-12" / "2025.5" 混用） ----

    private fun yearOf(f: Footprint): Int? =
        f.dateMillis?.let { ms -> Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR) }
            ?: Regex("(19|20)\\d{2}").find(f.date)?.value?.toIntOrNull()

    private fun monthOf(f: Footprint): Int? =
        f.dateMillis?.let { ms -> Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.MONTH) + 1 }
            ?: Regex("(?:19|20)\\d{2}\\s*[-年./]\\s*(\\d{1,2})").find(f.date)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 }

    private fun dateKey(f: Footprint): Long =
        f.dateMillis ?: (yearOf(f)?.toLong()?.times(10000) ?: 0L) + (monthOf(f)?.toLong()?.times(100) ?: 0L)

    /** 落给模型的日期文本：统一成 yyyy-MM-dd，模型不容易读错 */
    private fun dateText(f: Footprint): String {
        val ms = f.dateMillis
        if (ms != null && ms > 0) {
            return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(ms))
        }
        val y = yearOf(f) ?: return f.date.ifBlank { "日期未知" }
        val m = monthOf(f)
        return if (m != null) String.format(Locale.CHINA, "%04d-%02d", y, m) else "$y 年"
    }
}
