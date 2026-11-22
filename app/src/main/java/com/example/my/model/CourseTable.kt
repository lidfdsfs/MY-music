package com.example.my.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 课表外观与作息设置：一天几节、每节多少分钟、课间多长、几点开始、每周上几天，
 * 以及午休/晚休的位置与时长、开学第一周周一（用来算「第 N 周」和每列日期）。
 *
 * 全部是可配置项，默认值按国内大学常见作息（8:20 第一节、45 分钟一节、5 分钟课间、
 * 上午 4 节后午休、下午 4 节后晚休、周一至周五）。
 */
data class CourseTableSettings(
    val periodsPerDay: Int = 11,
    val lessonMinutes: Int = 45,
    val breakMinutes: Int = 5,
    /** 第一节开始时间，从 0 点起算的分钟数 */
    val startMinutes: Int = 8 * 60 + 20,
    val daysPerWeek: Int = 5,
    /** 第几节之后放午休（0 表示没有午休） */
    val lunchAfter: Int = 4,
    val lunchMinutes: Int = 120,
    /** 第几节之后放晚休（0 表示没有晚休） */
    val eveningAfter: Int = 8,
    val eveningMinutes: Int = 30,
    /** 开学第一周的周一，格式 yyyy-MM-dd；空串表示「本周就是第 1 周」 */
    val semesterMonday: String = "",
    /** 学期总周数：周次切换与「第 N 周」快选的上界 */
    val totalWeeks: Int = 20,
    /**
     * 用户逐节自定义的上课时间（从 0 点起算的分钟数），按第 1、2、3…节顺序排列。
     * 为空或某一节缺值时，该节按 lessonMinutes / breakMinutes / startMinutes 自动推导。
     */
    val periodStartMinutes: List<Int> = emptyList(),
    /** 与 [periodStartMinutes] 一一对应的下课时间 */
    val periodEndMinutes: List<Int> = emptyList()
) {
    val safePeriods: Int get() = periodsPerDay.coerceIn(1, 16)
    val safeTotalWeeks: Int get() = totalWeeks.coerceIn(1, com.example.my.model.Course.MAX_WEEKS)
    val hasCustomPeriodTimes: Boolean get() = periodStartMinutes.isNotEmpty() && periodEndMinutes.isNotEmpty()

    /**
     * 实际可翻到的最大周次。学期日期填得比 totalWeeks 更早时，当前周会超出总周数，
     * 这时以当前周为准，保证「本周」永远可达，不会翻着翻着把自己这一周弄丢。
     */
    val weekCount: Int get() = maxOf(safeTotalWeeks, currentWeek().coerceAtMost(com.example.my.model.Course.MAX_WEEKS))

    /** 第 [period] 节（1 基）的 [开始, 结束] 分钟数；优先用用户逐节设置的时间 */
    fun slot(period: Int): Pair<Int, Int> {
        val n = period.coerceIn(1, safePeriods)
        val customStart = periodStartMinutes.getOrNull(n - 1)
        val customEnd = periodEndMinutes.getOrNull(n - 1)
        if (customStart != null && customEnd != null && customEnd > customStart) {
            return customStart.coerceIn(0, 24 * 60 - 1) to customEnd.coerceIn(1, 24 * 60)
        }
        return autoSlot(n)
    }

    /** 按「每节时长 + 课间 + 午休/晚休」自动推导的第 [period] 节时间，不含逐节自定义 */
    fun autoSlot(period: Int): Pair<Int, Int> {
        val n = period.coerceIn(1, safePeriods)
        var start = startMinutes
        for (i in 2..n) {
            start += lessonMinutes + breakMinutes
            if (i - 1 == lunchAfter) start += lunchMinutes
            if (i - 1 == eveningAfter) start += eveningMinutes
        }
        return start to (start + lessonMinutes)
    }

    /** 第 [period] 节（1 基）的 [开始, 结束] 分钟数 */
    fun startText(period: Int): String = hhmm(slot(period).first)
    fun endText(period: Int): String = hhmm(slot(period).second)

    fun startHourText(): String = String.format(Locale.CHINA, "%02d", (startMinutes / 60) % 24)
    fun startMinuteText(): String = String.format(Locale.CHINA, "%02d", startMinutes % 60)

    companion object {
        fun hhmm(min: Int): String =
            String.format(Locale.CHINA, "%02d:%02d", (min / 60) % 24, min % 60)

        /** 解析 "8:20" / "08:20" / "0820" 这几种写法，失败返回 null */
        fun parseTime(raw: String): Int? {
            val s = raw.trim().replace('：', ':')
            val parts = s.split(":").mapNotNull { it.trim().toIntOrNull() }
            return when {
                parts.size == 2 && parts[0] in 0..23 && parts[1] in 0..59 -> parts[0] * 60 + parts[1]
                s.length == 4 && s.all { it.isDigit() } -> {
                    val h = s.substring(0, 2).toIntOrNull()
                    val m = s.substring(2, 4).toIntOrNull()
                    if (h != null && m != null && h in 0..23 && m in 0..59) h * 60 + m else null
                }
                else -> null
            }
        }

        /** 本周一 0 点 */
        fun thisWeekMonday(): Calendar {
            val c = Calendar.getInstance()
            c.firstDayOfWeek = Calendar.MONDAY
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            val dow = c.get(Calendar.DAY_OF_WEEK)
            val delta = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
            c.add(Calendar.DAY_OF_MONTH, delta)
            return c
        }
    }

    /** 开学第一周周一；没设置时退回本周一（即本周算第 1 周） */
    fun semesterStart(): Calendar {
        if (semesterMonday.isBlank()) return thisWeekMonday()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val parsed = runCatching { fmt.parse(semesterMonday.trim()) }.getOrNull()
            ?: return thisWeekMonday()
        val c = Calendar.getInstance()
        c.time = parsed
        c.firstDayOfWeek = Calendar.MONDAY
        // 设置里允许填任意一天，统一对齐到那一周的周一
        val dow = c.get(Calendar.DAY_OF_WEEK)
        val delta = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
        c.add(Calendar.DAY_OF_MONTH, delta)
        return c
    }

    /** 本周是第几周（最小 1） */
    fun currentWeek(): Int {
        val start = semesterStart().timeInMillis
        val now = thisWeekMonday().timeInMillis
        val diff = (now - start) / (7L * 24 * 60 * 60 * 1000)
        return (diff + 1L).coerceAtLeast(1L).toInt()
    }

    /** 第 [week] 周的周一 */
    fun mondayOfWeek(week: Int): Calendar {
        val c = semesterStart()
        c.add(Calendar.DAY_OF_MONTH, 7 * (week - 1))
        return c
    }

    fun dateOf(week: Int, dayIndex1: Int): Calendar {
        val c = mondayOfWeek(week)
        c.add(Calendar.DAY_OF_MONTH, dayIndex1 - 1)
        return c
    }
}
