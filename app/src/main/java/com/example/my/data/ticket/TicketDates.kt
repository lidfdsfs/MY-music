package com.example.my.data.ticket

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 票务日期归一化：模型/用户可能给「今天/明天/后天」，也可能给 ISO 日期。
 * 上游两个 MCP 都只认 yyyy-MM-dd，这里统一收口，避免每个能力各写一套。
 */
object TicketDates {

    private val ISO = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val MONTH_DAY = Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日?")

    fun today(): String = format(Date())

    fun tomorrow(): String = plusDays(1)

    /** 相对天数：0=今天、1=明天、2=后天；负数表示前天 */
    fun plusDays(days: Int): String {
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }
        return format(c.time)
    }

    /**
     * 把模型/用户给的日期参数收成 yyyy-MM-dd。
     * 认不出就按「明天」兜底：12306 查询本身默认查明天，这和用户在对话里说
     * 「查一下火车票」时的预期一致，也比直接报参数错误更有用。
     */
    fun resolve(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (ISO.matches(text) && isValid(text)) return text
        // 「11月12日」这类没有年份的说法：按今年算；如果已经过去，按明年算。
        // 票务查询只会查未来，用户不会在 11 月说「1 月 2 日」指今年。
        monthDay(text)?.let { return it }
        val relative = when {
            text.contains("大后天") -> 3
            text.contains("后天") -> 2
            text.contains("明天") || text.contains("明日") -> 1
            text.contains("今天") || text.contains("今日") -> 0
            else -> 1
        }
        return plusDays(relative)
    }

    private fun monthDay(text: String): String? {
        val match = MONTH_DAY.find(text) ?: return null
        val month = match.groupValues[1].toIntOrNull() ?: return null
        val day = match.groupValues[2].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        val now = Calendar.getInstance()
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val candidate = Calendar.getInstance().apply {
            set(Calendar.YEAR, now.get(Calendar.YEAR))
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Calendar 对 2 月 30 日这类非法日期会滚动到 3 月，必须核对回读值。
        if (candidate.get(Calendar.MONTH) != month - 1 ||
            candidate.get(Calendar.DAY_OF_MONTH) != day) return null
        if (candidate.before(today)) candidate.add(Calendar.YEAR, 1)
        return format(candidate.time)
    }

    private fun isValid(text: String): Boolean = runCatching {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        sdf.parse(text) != null
    }.getOrDefault(false)

    private fun format(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
}
