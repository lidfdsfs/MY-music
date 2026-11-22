package com.example.my.data.ticket

/**
 * 票务问句识别：只做「是不是在查真实余票/比价」的轻量判断，并尽量抽出
 * 出发地、到达地和日期提示，供不支持 Function Calling 的模型走规则兜底。
 *
 * 这里刻意不追求覆盖所有中文表达：OpenAI 兼容端点由模型直接给
 * query_train_tickets / query_flights 的结构化参数；本类只负责两件事：
 * 1. 让检索路由知道「票务查询优先走 App 内置接口」；
 * 2. 在规则兜底时，能看懂「重庆到成都」「重庆飞上海」这类最常见说法。
 */
object TicketIntent {

    enum class Kind { TRAIN, FLIGHT }

    data class Query(
        val kind: Kind,
        val from: String,
        val to: String,
        val dateHint: String? = null,
        val budget: Int? = null,
        val timeHint: String = ""
    )

    private val BUDGET_REGEX = Regex("""(?:预算|最多|不超过|控制在)\s*(\d{2,6})""")
    private val TIME_HINTS = listOf("凌晨", "早上", "上午", "中午", "下午", "傍晚", "晚上", "夜间")

    private val TRAIN_WORDS = listOf(
        "火车票", "高铁票", "动车票", "火车", "高铁", "动车", "车次", "余票", "12306"
    )
    private val FLIGHT_WORDS = listOf("机票", "飞机票", "航班", "飞机")

    /** 有这些词才说明用户要「查结果」，而不是在聊票务规则或写文案。 */
    private val ASK_WORDS = listOf(
        "查", "查询", "搜", "余票", "有票", "票价", "价格", "多少钱",
        "几点", "时刻", "班次", "车次", "比价", "订票"
    )

    /** 这些是「规则/流程/攻略」类问题，交给网页搜索更合适。 */
    private val NON_QUERY_WORDS = listOf(
        "怎么", "如何", "攻略", "流程", "规则", "退票", "改签", "官网",
        "购买", "学生票", "儿童票", "发票", "报销", "行李"
    )

    private val ROUTE_MARKS = listOf("到", "去", "飞")

    private val ROUTE_REGEX = Regex(
        "([\\u4e00-\\u9fa5]{2,10})\\s*(?:到|去|飞往|飞|→|->)\\s*([\\u4e00-\\u9fa5]{2,10})"
    )

    /**
     * 只有目的地、没有出发地的说法：「明天我要去秀山」「去秀山的高铁」。
     * 此时出发地由调用方按用户常住城市/天气城市补，不要让句首的「我要」被当成城市。
     */
    private val DEST_ONLY_REGEX = Regex(
        "(?:飞往|去|到|飞|→|->)\\s*([\\u4e00-\\u9fa5]{2,10})"
    )

    private val DATE_REGEX = Regex(
        "(今天|今日|明天|明日|后天|大后天|\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}月\\d{1,2}日?)"
    )

    private val LEAD_WORDS = listOf(
        "帮我", "帮忙", "请问", "请", "麻烦", "查询", "查一下", "查下", "查",
        "搜索", "搜一下", "搜", "看看", "看一下", "看",
        "我想要", "我要", "我想", "想要", "要", "我",
        "从", "乘坐", "坐", "今天", "今日", "明天", "明日", "后天", "大后天",
        "下周", "本周", "周末", "早上", "上午", "中午", "下午", "晚上"
    )

    private val TAIL_WORDS = listOf(
        "的高铁", "高铁", "的动车", "动车", "的火车", "火车",
        "的机票", "机票", "飞机票", "的航班", "航班",
        "车次", "余票", "有票", "票价", "价格", "多少钱",
        "有哪些", "有什么", "多少", "几点", "时刻表", "时刻", "有车",
        "的票", "票", "机", "出发", "到达", "的"
    )

    // 只拦「疑问/代词」这类明显不是城市名的碎片；不要用「上」「日」「月」这种
    // 单字，否则上海、日照会被误判成时间残留。
    private val BAD_CITY_CHARS = "我你他她它哪什么几这那里吗呢"

    /** 是不是「查真实余票 / 比价」的问句；规则/流程/写作文案返回 false。 */
    fun looksLikeQuery(text: String): Boolean {
        val t = text.trim()
        if (t.length < 4) return false
        if (TRAIN_WORDS.none { t.contains(it) } && FLIGHT_WORDS.none { t.contains(it) }) return false
        if (NON_QUERY_WORDS.any { t.contains(it) }) return false
        return ASK_WORDS.any { t.contains(it) } || ROUTE_MARKS.any { t.contains(it) }
    }

    /**
     * 抽取出出发地/到达地；抽不出就返回 null，让调用方回退到原来的搜索链路。
     *
     * @param defaultFrom 用户没有明说出发地时的兜底城市（通常是常住城市/天气城市）。
     *   只接受「去秀山」这种只有目的地的说法；明确写了两座城市时永远以原话为准。
     */
    fun parse(text: String, defaultFrom: String = ""): Query? {
        if (!looksLikeQuery(text)) return null
        val normalized = text.trim()
            .replace("—>", "->")
            .replace("→", "到")
        val dateHint = DATE_REGEX.find(text)?.value
        val budget = BUDGET_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?.takeIf { it in 50..100_000 }
        val timeHint = TIME_HINTS.firstOrNull { text.contains(it) }.orEmpty()
        val kind = if (FLIGHT_WORDS.any { normalized.contains(it) }) Kind.FLIGHT else Kind.TRAIN

        val match = ROUTE_REGEX.find(normalized)
        if (match != null) {
            val from = cleanCity(match.groupValues[1]).orEmpty()
            val to = cleanCity(match.groupValues[2]).orEmpty()
            if (from.isNotBlank() && to.isNotBlank()) {
                return if (from == to) null else Query(kind, from, to, dateHint, budget, timeHint)
            }
        }

        // 「明天我要去秀山」这类只说了目的地的问句：句首的「我要」不是城市名，
        // 出发地按用户常住城市补；没有兜底城市就交给模型/用户补，不猜。
        val to = DEST_ONLY_REGEX.find(normalized)
            ?.groupValues?.getOrNull(1)
            ?.let(::cleanCity)
            .orEmpty()
        val from = cleanCity(defaultFrom).orEmpty()
        return if (to.isNotBlank() && from.isNotBlank() && to != from) {
            Query(kind, from, to, dateHint, budget, timeHint)
        } else null
    }

    private fun cleanCity(raw: String): String? {
        var value = raw.trim()
        var changed = true
        var guard = 0
        while (changed && guard++ < 6) {
            changed = false
            for (prefix in LEAD_WORDS) {
                if (value.length > prefix.length && value.startsWith(prefix)) {
                    value = value.removePrefix(prefix)
                    changed = true
                }
            }
            for (suffix in TAIL_WORDS) {
                if (value.length > suffix.length && value.endsWith(suffix)) {
                    value = value.removeSuffix(suffix)
                    changed = true
                }
            }
        }
        if (value.length !in 2..8) return null
        if (value.any { it in BAD_CITY_CHARS }) return null
        return value.takeIf { !it.contains("票") && !it.contains("车次") }
    }
}
