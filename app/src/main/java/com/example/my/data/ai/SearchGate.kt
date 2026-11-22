package com.example.my.data.ai

import android.content.Context
import com.example.my.data.AiApi
import com.example.my.data.AiEndpoint
import com.example.my.data.ticket.TicketIntent
import com.google.gson.JsonParser
import java.util.Locale

/**
 * 检索路由（SearchGate）：在「联网搜索」总开关打开的前提下，再判断这一轮
 * 到底要不要真的发网络请求。
 *
 * 三层判定，便宜的先跑：
 *  第一层 确定性规则：关键词/时间词/实体类型硬过滤，命中即裁决；
 *  第二层 轻量意图分类：词面触发短语 + 置信度，分数明确才裁决；
 *  第三层 大模型决策：前两层拿不准时，只做一次极短的 JSON 路由，
 *          并读取模型自评置信度；低置信度的「不搜」会保守升级为搜索。
 *
 * 模型超时、报错、输出不合法时一律保守地选择「搜索」，宁可多搜一次，
 * 也不能把需要实时信息的问题降级成凭记忆作答。
 *
 * 这一层只决定「搜不搜」，不改变原来的搜索实现、结果清洗、来源展示和
 * 手动搜索入口；调用方拿到 [Decision.search] 后仍走原有的 WebSearch /
 * AiTools 流程。
 */
object SearchGate {

    enum class Source { RULE_FORCE, RULE_SKIP, RULE_INTERNAL, RULE_SCORER, MODEL, FALLBACK, WEB_OFF }

    /**
     * 检索深度：开关打开后仍然「所有问题都搜」，但简单问题只做轻量检索，
     * 避免一句「你好」也去抓 6 篇正文、跑 4 个角度。
     */
    enum class Depth(val zh: String, val en: String) {
        LIGHT("轻量", "Light"),
        STANDARD("标准", "Standard"),
        DEEP("深入", "Deep")
    }

    data class Decision(
        val search: Boolean,
        val reason: String,
        val source: Source,
        val queries: List<String> = emptyList(),
        /** 票务类问题：不走网页搜索，优先调用 App 内置的票务接口。 */
        val preferInternal: Boolean = false,
        /** 规则/打分/模型给出的置信度；规则强制/跳过默认为 1.0。 */
        val confidence: Double = 1.0,
        /** 本轮检索深度；由问题复杂度决定，供调用方选择 SearchProfile。 */
        val depth: Depth = Depth.STANDARD
    )

    val WEB_OFF = Decision(false, "联网总开关已关闭", Source.WEB_OFF)

    /**
     * 产品当前口径：联网总开关一旦打开，**每一条非空消息都进入网页检索**，
     * 不再区分寒暄、创作、App 内数据或票务。票务仍然标记 preferInternal，
     * 让内置票务接口优先，但网页检索也会同时发生，保证模型拿到外部信息。
     */
    fun force(text: String): Decision {
        val t = text.trim()
        if (t.isEmpty()) return Decision(false, "空消息不检索", Source.RULE_SKIP)
        val depth = depthOf(t)
        return Decision(
            search = true,
            reason = when (depth) {
                Depth.LIGHT -> "简单问题：轻量联网快速回答"
                Depth.STANDARD -> "常规问题：标准联网检索"
                Depth.DEEP -> "复杂问题：深入联网核实"
            },
            source = Source.RULE_FORCE,
            preferInternal = TicketIntent.looksLikeQuery(t),
            depth = depth
        )
    }

    /**
     * 问题复杂度判断：只决定检索深度，不决定「搜不搜」。
     * 目标是让「你好」「翻译这段」这类简单问题 1 条正文就够，
     * 而攻略、行程、职务变动、对比核实类问题才多角度抓取。
     */
    internal fun depthOf(text: String): Depth {
        val t = normalized(text)
        if (t.isEmpty()) return Depth.LIGHT
        if (LIGHT_WORDS.any { t == it || t.startsWith(it) }) return Depth.LIGHT
        if (DEEP_WORDS.any { t.contains(it) }) return Depth.DEEP
        if (t.length >= 30) return Depth.DEEP
        if (FRESH_SIMPLE_WORDS.any { t.contains(it) }) return Depth.STANDARD
        if (TicketIntent.looksLikeQuery(t)) return Depth.STANDARD
        if (PURE_TEXT_PREFIXES.any { t.startsWith(it) }) return Depth.LIGHT
        if (t.length <= 8 && !t.contains('?') && !t.contains('？')) return Depth.LIGHT
        return Depth.STANDARD
    }

    private val LIGHT_WORDS = listOf(
        "你好", "您好", "嗨", "哈喽", "hello", "hi", "在吗", "谢谢", "多谢",
        "早上好", "中午好", "下午好", "晚上好", "晚安", "再见", "拜拜"
    )

    /** 需要最新数据、但答案很短的问题：标准深度，不降到轻量。 */
    private val FRESH_SIMPLE_WORDS = listOf(
        "天气", "气温", "温度", "几度", "下雨", "带伞", "台风",
        "多少钱", "价格", "票价", "汇率", "股价", "比分", "赛程",
        "几点", "开放时间", "营业时间", "航班", "高铁", "机票", "门票"
    )

    /** 需要多角度、多来源交叉核实的问题：深搜。 */
    private val DEEP_WORDS = listOf(
        "攻略", "行程", "规划", "路线", "几日游", "日游", "几天", "怎么安排",
        "怎么玩", "安排", "方案", "对比", "比较", "哪个好", "更值得", "推荐",
        "排行", "榜单", "预算", "签证", "政策", "规定", "最新", "新闻", "公告",
        "核实", "求证", "真假", "真的吗", "来源", "出处", "财报", "投资",
        "分析", "影响", "区别", "原因", "原理", "评价", "值不值",
        "辞职", "辞去", "辞任", "卸任", "接任", "任命", "换帅", "罢免", "免职", "离职"
    )

    /** 明确需要外部实时信息：命中即强制搜索，不再额外调模型。 */
    private val FORCE_WORDS = listOf(
        // 时效 / 实时
        "最新", "最近", "今天", "今日", "明天", "现在", "实时", "当前", "今年",
        "新闻", "公告", "通知", "政策", "规定", "新规", "发布", "上线",
        // 价格 / 票务 / 营业
        "价格", "多少钱", "票价", "门票", "机票", "车票", "高铁票", "航班",
        "开放时间", "营业时间", "几点开门", "几点关门", "预约", "订票",
        "汇率", "股价", "比分", "赛程",
        // 天气 / 出行
        "天气", "气温", "温度", "几度", "下雨", "带伞", "台风",
        "怎么去", "交通", "公交", "地铁", "路线", "限行",
        "酒店", "民宿", "住宿", "餐厅", "好吃", "景点", "攻略", "行程",
        "好玩", "值得去", "必去", "美食", "小吃", "特色",
        // 事实核验 / 外部信息
        "搜索", "搜一下", "查一下", "帮我查", "网上", "官网", "来源", "出处",
        "真的吗", "真假", "核实", "求证", "依据", "联网", "引用", "参考文献",
        // 人物/公司职务变动：用户把「某人已卸任/已换人」当陈述句发来时，
        // 模型很容易判成「不用搜」，但这恰恰是最需要拿最新新闻核对的时刻。
        "不再担任", "不在担任", "不再出任", "卸任", "辞任", "辞去", "辞职", "离职",
        "接任", "出任", "任命", "换帅", "罢免", "免职",
        // 高风险领域：医疗/法律/金融等，规则层直接强制检索，绕过自主判断
        "医疗", "诊断", "症状", "用药", "剂量", "药量",
        "法律", "法条", "律师", "起诉", "合同", "诉讼",
        "金融", "理财", "投资", "股票", "基金", "保险", "税务", "报税",
        // 内容型外部资料
        "歌词", "歌曲故事", "创作背景", "百科", "资料"
    )

    /** 明确不需要联网的场景：本地数据、纯文本加工、寒暄。 */
    private val LOCAL_DATA_WORDS = listOf(
        "我的足迹", "我的行程", "我的收藏", "我的攻略", "我的计划",
        "我的笔记", "我收藏", "我点赞", "我去过", "我的数据"
    )

    private val PURE_TEXT_PREFIXES = listOf(
        "请把下面这段", "请润色", "请翻译", "请顺着", "请改写", "请续写",
        "帮我润色", "帮我翻译", "帮我改写", "帮我续写",
        "帮我写一段", "帮我写一篇", "写一段", "写一篇", "写个文案",
        "生成一段文案", "生成一篇文案", "起个标题", "取个名字", "列个提纲"
    )

    private val GREETING_PREFIXES = listOf(
        "你好", "您好", "嗨", "哈喽", "hello", "hi", "在吗", "谢谢", "多谢",
        "早上好", "中午好", "下午好", "晚上好", "晚安", "再见", "拜拜"
    )

    private fun normalized(text: String): String = text.trim().lowercase(Locale.ROOT)

    /**
     * 纯规则路由。返回 null 表示「规则拿不准」，需要交给模型判断。
     * 规则必须保持确定性，方便单测和线上排查。
     */
    internal fun rule(text: String, allowWeb: Boolean): Decision? {
        if (!allowWeb) return WEB_OFF
        val t = normalized(text)
        if (t.length < 2) return Decision(false, "空消息或过短", Source.RULE_SKIP)
        // 只拦「纯寒暄」，不能因为句首有「你好」就把后面真正的查询一起跳过。
        if (GREETING_PREFIXES.any { t == it || (t.startsWith(it) && t.length <= it.length + 3) }) {
            return Decision(false, "寒暄不需要外部信息", Source.RULE_SKIP)
        }
        if (LOCAL_DATA_WORDS.any { t.contains(it) }) {
            return Decision(false, "查询 App 内自己的数据，走本地能力", Source.RULE_SKIP)
        }
        // 票务优先走 App 内置接口（12306 只读余票 / 免费航班比价）。
        // 只有「怎么买、退改签规则、官网入口」这类规则问题才继续走网页搜索。
        if (TicketIntent.looksLikeQuery(t)) {
            return Decision(
                false,
                "票务查询优先使用 App 内置票务接口",
                Source.RULE_INTERNAL,
                preferInternal = true
            )
        }
        // 先放行「攻略 / 行程 / 天气」等明确要外部资料的写作请求，再拦纯文本加工，
        // 否则「帮我写重庆攻略」会被误判成普通文案。
        // 「我想辞职/准备离职」是个人表达，不是需要核对的公司职务变动新闻。
        if (Regex("(我想|我打算|我要|我准备|准备|打算)(辞职|离职|换工作)").containsMatchIn(t)) {
            return Decision(false, "个人职业表达不需要外部事实检索", Source.RULE_SKIP)
        }
        if (FORCE_WORDS.any { t.contains(it) }) {
            return Decision(true, "命中必须联网的时效/事实词", Source.RULE_FORCE)
        }
        if (PURE_TEXT_PREFIXES.any { t.startsWith(it) }) {
            return Decision(false, "纯文本加工/创作，不需要外部信息", Source.RULE_SKIP)
        }
        // 第二层：轻量意图打分。分数明确就直接裁决，低置信度才升级给模型。
        SearchIntentScorer.score(t)?.let { s ->
            return Decision(
                s.search,
                s.reason,
                Source.RULE_SCORER,
                confidence = s.confidence
            )
        }
        return null
    }

    /**
     * 解析模型返回的极简 JSON。容忍 ```json 包裹、前后解释文字；
     * 只要 search 字段缺失或类型不对就返回 null，让调用方走保守兜底。
     */
    internal fun parse(raw: String?): Decision? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            val obj = JsonParser.parseString(cleaned.substring(start, end + 1)).asJsonObject
            val field = obj.get("search") ?: return null
            if (!field.isJsonPrimitive) return null
            val search = field.asBoolean
            val confidence = obj.get("confidence")
                ?.takeIf { it.isJsonPrimitive }
                ?.asDouble?.coerceIn(0.0, 1.0) ?: 1.0
            val reason = obj.get("reason")?.takeIf { it.isJsonPrimitive }
                ?.asString?.trim().orEmpty().take(120)
            val queries = obj.get("queries")?.takeIf { it.isJsonArray }
                ?.asJsonArray?.mapNotNull { q ->
                    q.takeIf { it.isJsonPrimitive }?.asString?.trim()
                        ?.takeIf { it.isNotBlank() }?.take(80)
                }?.take(3).orEmpty()
            // 第三层也做置信度兜底：模型说「不搜」，但自评置信度低于 0.60 时
            // 不要相信这个省成本的判断，保守升级为搜索。
            if (!search && confidence < 0.60) {
                return Decision(
                    search = true,
                    reason = "模型判断不需搜索但置信度仅 ${"%.2f".format(confidence)}，保守检索",
                    source = Source.FALLBACK,
                    queries = queries,
                    confidence = confidence
                )
            }
            Decision(
                search = search,
                reason = reason.ifBlank { if (search) "模型判断需要外部信息" else "模型判断可直接回答" },
                source = Source.MODEL,
                queries = queries,
                confidence = confidence
            )
        }.getOrNull()
    }

    /**
     * 真正入口：规则优先，模糊问题才调一次模型。
     *
     * @param recent 最近几轮对话（用于「那明天呢」这类省略主语的追问），可空
     * @param useLocal 是否走端侧小模型
     * @param localModelId 端侧模型 id，null 表示用当前激活模型
     */
    suspend fun decide(
        context: Context,
        text: String,
        recent: String,
        endpoint: AiEndpoint,
        useLocal: Boolean,
        localModelId: String?,
        allowWeb: Boolean
    ): Decision {
        // 产品口径：开关打开就全量检索，不再跑规则/模型路由。
        // rule/parse 仍保留给离线调试和历史测试，但运行时不参与决策。
        if (!allowWeb) return WEB_OFF
        return force(text)
    }
}
