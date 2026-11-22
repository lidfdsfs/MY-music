package com.example.my.data.ticket

import com.example.my.data.AiApi
import com.example.my.data.AiEndpoint
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 购票专区的 AI 层：只负责「理解一句话」和「在真实结果里做推荐」，
 * 不生成价格、余票或车次，也不替用户下单。
 */
object TicketAi {

    enum class Kind { TRAIN, FLIGHT }

    data class Query(
        val kind: Kind,
        val from: String,
        val to: String,
        val date: String,
        val budget: Int? = null,
        val timeHint: String = "",
        val raw: String = ""
    )

    data class Advice(
        val pick: String?,
        val title: String,
        val reason: String,
        val alternatives: List<String> = emptyList()
    )

    private val API_DATE = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val DAY_MS = 24L * 60L * 60L * 1000L
    private val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val DATE_PREFIX = Regex("""^(?:今天|明天|后天|大后天|今日|明日)\s*""")
    private val TIME_PREFIX = Regex("""^(?:凌晨|早上|上午|中午|下午|傍晚|晚上|夜间)\s*""")
    private val MONTH_DAY_PREFIX = Regex("""^\d{1,2}月\d{1,2}[日号]\s*""")
    private val ISO_PREFIX = Regex("""^\d{4}-\d{1,2}-\d{1,2}\s*""")
    private val AI_FENCE = Regex("""^```[a-zA-Z]*\s*|\s*```$""")

    /** 先用本地规则兜底，再让 AI 细化；AI 不可用时至少还能把常见说法用起来。 */
    suspend fun understand(
        endpoint: AiEndpoint,
        text: String,
        today: String
    ): Query? {
        val local = parseLocal(text, today)
        val raw = AiApi.requestOnce(endpoint, INTENT_SYSTEM, intentUser(text, today))
        val ai = parseIntentJson(raw, today)
        if (ai == null) return local
        return Query(
            kind = ai.kind ?: local?.kind ?: Kind.TRAIN,
            from = ai.from.ifBlank { local?.from.orEmpty() },
            to = ai.to.ifBlank { local?.to.orEmpty() },
            date = ai.date.ifBlank { local?.date ?: shift(today, 1) },
            budget = ai.budget ?: local?.budget,
            timeHint = ai.timeHint.ifBlank { local?.timeHint.orEmpty() },
            raw = text
        ).takeIf { it.from.isNotBlank() && it.to.isNotBlank() }
    }

    /** 纯本地解析，也是 JVM 单测入口。 */
    fun parseLocal(raw: String, today: String): Query? {
        val text = raw.trim()
        if (text.isBlank()) return null
        val cleaned = text
            .replace(Regex("""^(?:帮我|请|麻烦|我想|我要|想|要|查一下|查询|查|看看|看)+"""), "")
            .trim()
        val date = dateFromText(cleaned, today)
        val withoutDate = TIME_PREFIX.replace(
            ISO_PREFIX.replace(MONTH_DAY_PREFIX.replace(DATE_PREFIX.replace(cleaned, ""), ""), ""),
            ""
        ).trim()
        val route = ROUTE.find(withoutDate)
        val from = route?.groupValues?.getOrNull(1)?.let(::cleanPlace).orEmpty()
        val to = route?.groupValues?.getOrNull(2)?.let(::cleanPlace).orEmpty()
        if (from.isBlank() || to.isBlank() || from == to) return null
        val kind = when {
            Regex("""(飞机|航班|机票|飞往|飞)""").containsMatchIn(cleaned) -> Kind.FLIGHT
            else -> Kind.TRAIN
        }
        val budget = BUDGET.find(cleaned)?.groupValues
            ?.drop(1)?.firstOrNull { it.isNotBlank() }?.toIntOrNull()
            ?.takeIf { it in 50..100_000 }
        val timeHint = TIME_HINTS.firstOrNull { cleaned.contains(it) }.orEmpty()
        return Query(kind, from, to, date, budget, timeHint, raw)
    }

    /**
     * 把真实查询结果压缩成少量事实交给 AI；模型只排序和解释，不允许补充不存在的价格/余票。
     */
    suspend fun advise(
        endpoint: AiEndpoint,
        query: Query,
        trains: TrainQueryResult?,
        flights: FlightQueryResult?
    ): Advice? {
        val facts = buildFacts(trains, flights)
        if (facts.isBlank()) return null
        val raw = AiApi.requestOnce(
            endpoint,
            ADVICE_SYSTEM,
            "出行需求：${query.from}→${query.to} ${query.date}" +
                (query.budget?.let { "，预算 $it 元" } ?: "") +
                (query.timeHint.takeIf { it.isNotBlank() }?.let { "，偏好$it" } ?: "") +
                "\n候选：\n$facts"
        )
        return parseAdviceJson(raw) ?: ruleAdvice(trains, flights)
    }

    /** 不依赖模型时的确定性推荐：火车选有票且耗时短，飞机选直飞最低价。 */
    fun ruleAdvice(trains: TrainQueryResult?, flights: FlightQueryResult?): Advice? {
        val trainList = trains?.trains.orEmpty().filter { it.available }
        val flightList = flights?.offers.orEmpty()
        if (flightList.isNotEmpty()) {
            val best = flightList
                .filter { it.direct && it.price != null }
                .minWithOrNull(compareBy<FlightOffer> { it.price ?: Double.MAX_VALUE }.thenBy { it.departTime })
                ?: flightList.minWithOrNull(compareBy<FlightOffer> { it.price ?: Double.MAX_VALUE })
                ?: return null
            val alternatives = flightList
                .filter { it.flightNo != best.flightNo }
                .sortedBy { it.price ?: Double.MAX_VALUE }
                .take(2)
                .map { "${it.flightNo} ${it.departTime} ${it.priceText}" }
            return Advice(
                pick = best.flightNo,
                title = "推荐 ${best.flightNo} ${best.priceText}",
                reason = buildString {
                    append(best.departTime.ifBlank { "--:--" })
                    append(" → ")
                    append(best.arriveTime.ifBlank { "--:--" })
                    append(" · ")
                    append(best.durationText.ifBlank { "耗时未知" })
                    if (best.direct) append(" · 直飞") else append(" · ${best.stopsText}")
                    append(" · ${best.priceText}")
                },
                alternatives = alternatives
            )
        }
        if (trainList.isNotEmpty()) {
            val best = trainList.minWithOrNull(
                compareBy<TrainTicket> { durationMinutes(it.duration) }.thenBy { it.departTime }
            ) ?: return null
            val alternatives = trainList
                .filter { it.code != best.code }
                .sortedBy { it.departTime }
                .take(2)
                .map { "${it.code} ${it.departTime}→${it.arriveTime}" }
            return Advice(
                pick = best.code,
                title = "推荐 ${best.code}",
                reason = buildString {
                    append(best.departTime.ifBlank { "--:--" })
                    append(" → ")
                    append(best.arriveTime.ifBlank { "--:--" })
                    append(" · ")
                    append(best.duration.ifBlank { "历时未知" })
                    best.seatSummary().firstOrNull()?.let { append(" · $it") }
                },
                alternatives = alternatives
            )
        }
        return null
    }

    private fun buildFacts(trains: TrainQueryResult?, flights: FlightQueryResult?): String {
        val sb = StringBuilder()
        trains?.trains.orEmpty().take(8).forEach {
            sb.append("火车|").append(it.code).append('|')
                .append(it.fromStation).append('→').append(it.toStation).append('|')
                .append(it.departTime).append('-').append(it.arriveTime).append('|')
                .append(it.duration).append('|')
                .append(it.seatSummary().joinToString("/").ifBlank { "无票" })
                .append('\n')
        }
        flights?.offers.orEmpty().take(8).forEach {
            sb.append("飞机|").append(it.flightNo).append('|')
                .append(if (it.direct) "直飞" else it.stopsText).append('|')
                .append(it.departTime).append('-').append(it.arriveTime).append('|')
                .append(it.durationText).append('|')
                .append(it.priceText).append('|')
                .append(it.baggageText)
                .append('\n')
        }
        return sb.toString().trim()
    }

    private fun parseIntentJson(raw: String?, today: String): Query? {
        val obj = jsonOf(raw) ?: return null
        val from = obj.optString("from").trim().take(24)
        val to = obj.optString("to").trim().take(24)
        if (from.isBlank() || to.isBlank()) return null
        val date = obj.optString("date").trim().takeIf { ISO_DATE.matches(it) }
            ?: shift(today, 1)
        val kind = when (obj.optString("kind").lowercase(Locale.US)) {
            "flight", "plane", "air" -> Kind.FLIGHT
            "train", "rail" -> Kind.TRAIN
            else -> null
        }
        val budget = obj.opt("budget")?.let { value ->
            when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
        }?.takeIf { it in 50..100_000 }
        return Query(
            kind = kind ?: Kind.TRAIN,
            from = from,
            to = to,
            date = date,
            budget = budget,
            timeHint = obj.optString("time_hint").trim().take(8),
            raw = obj.optString("raw").trim().take(120)
        )
    }

    private fun parseAdviceJson(raw: String?): Advice? {
        val obj = jsonOf(raw) ?: return null
        val title = obj.optString("title").trim().take(60).ifBlank { "AI 推荐" }
        val reason = obj.optString("reason").trim().take(180)
        if (reason.isBlank()) return null
        val pick = obj.optString("pick").trim().take(16).ifBlank { null }
        val alternatives = obj.optJSONArray("alternatives")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).trim().take(60).ifBlank { null } }.take(3)
        }.orEmpty()
        return Advice(pick, title, reason, alternatives)
    }

    private fun jsonOf(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        val text = raw.replace(AI_FENCE, "").trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }

    private fun dateFromText(text: String, today: String): String {
        Regex("""\d{4}-\d{1,2}-\d{1,2}""").find(text)?.value?.let { raw ->
            val parts = raw.split('-')
            if (parts.size == 3) {
                return String.format(Locale.US, "%04d-%02d-%02d", parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            }
        }
        Regex("""(\d{1,2})月(\d{1,2})[日号]""").find(text)?.let { m ->
            val cal = Calendar.getInstance().apply {
                time = runCatching { API_DATE.parse(today) }.getOrNull() ?: Date()
                set(Calendar.MONTH, m.groupValues[1].toInt() - 1)
                set(Calendar.DAY_OF_MONTH, m.groupValues[2].toInt())
            }
            return API_DATE.format(cal.time)
        }
        return when {
            text.contains("大后天") -> shift(today, 3)
            text.contains("后天") -> shift(today, 2)
            text.contains("明天") || text.contains("明日") -> shift(today, 1)
            else -> shift(today, 1)
        }
    }

    private fun shift(iso: String, days: Int): String {
        val cal = Calendar.getInstance().apply {
            time = runCatching { API_DATE.parse(iso) }.getOrNull() ?: Date()
            add(Calendar.DAY_OF_YEAR, days)
        }
        return API_DATE.format(cal.time)
    }

    private fun cleanPlace(raw: String): String = raw
        .replace(Regex("""^(?:从|由|在|从|自)+"""), "")
        .replace(Regex("""(?:的)?(?:高铁|动车|火车|飞机|航班|机票|车票|票|出发|往返)$"""), "")
        .replace(Regex("""[\s，,。；;、]+"""), "")
        .trim()
        .take(24)

    private fun durationMinutes(text: String): Int {
        val h = Regex("""(\d+)\s*小时""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val m = Regex("""(\d+)\s*分""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    private val ROUTE = Regex(
        """([\u4e00-\u9fa5A-Za-z]{2,12}?)\s*(?:飞往|到|去|前往|至|飞)\s*([\u4e00-\u9fa5A-Za-z]{2,12})"""
    )
    private val BUDGET = Regex("""(?:预算|不超过|最多|控制在)\s*(\d{2,5})|(\d{2,5})\s*(?:元|块)""")
    private val TIME_HINTS = listOf("凌晨", "早上", "上午", "中午", "下午", "傍晚", "晚上", "夜间")

    private val INTENT_SYSTEM = """
你是购票需求解析器。把用户的一句话解析成 JSON，只输出一行 JSON，不要 markdown、不要解释。
字段：
- kind: "train" 或 "flight"
- from: 出发城市/车站
- to: 到达城市/车站
- date: YYYY-MM-DD（结合给出的今天日期换算「明天/后天/下周一」等）
- budget: 整数元，没有就 null
- time_hint: 例如 上午/下午/晚上，没有就空字符串
规则：用户说高铁/动车/火车/车次选 train；说飞机/航班/机票选 flight；没说就按 train。
不要编造用户没说的城市或日期；缺 from/to 就留空。
    """.trimIndent()

    private fun intentUser(text: String, today: String): String =
        "今天日期：$today\n用户原话：${text.take(300)}"

    private val ADVICE_SYSTEM = """
你是购票结果推荐器。只能依据用户给出的候选班次做推荐，严禁编造价格、余票、车次或时间。
优先选有余票的火车、直飞且总价低的航班；如果全部无票，必须在理由里写明「已无余票」。
选择 1 个最合适的，并给出 1-2 个备选；理由要具体（时间、耗时、价格、直飞/经停、余票）。
只输出一行 JSON：
{"pick":"车次或航班号","title":"短标题","reason":"不超过80字","alternatives":["车次/航班号 + 简短说明"]}
没有合适候选时 pick 返回空字符串。
    """.trimIndent()
}
