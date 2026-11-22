package com.example.my.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 足迹日期的唯一解析口径。
 *
 * 用户可以在编辑页手改「日期」文本，所以**文本才是真相**，`dateMillis` 只是排序用的缓存。
 * 以前时间轴优先读 `dateMillis`，改完日期仍然留在创建时的年月，这里统一成：
 * 文本 → 毫秒 → 归属年月，三处（时间轴分组、按日期排序、编辑保存）都走这一份。
 */
object FootprintDate {

    private val patterns = listOf(
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm",
        "yyyy年M月d日",
        "yyyy年M月",
        "yyyy.MM.dd",
        "yyyy-MM",
        "yyyy/MM",
        "yyyy年"
    )

    /** 把 "2024-11-13" / "2024年11月13日" / "2024.11.13" 这类文本解析成当天 0 点毫秒 */
    fun millis(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        for (p in patterns) {
            val sdf = SimpleDateFormat(p, Locale.CHINA).apply { isLenient = false }
            val parsed = runCatching { sdf.parse(text) }.getOrNull() ?: continue
            // 统一归到当天 0 点，避免同一天不同时刻被算成两天
            return Calendar.getInstance().apply {
                time = parsed
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        // 兜底：只写了年月，日期按 1 号
        val ym = Regex("""(\d{4})\s*[-/.年.]\s*(\d{1,2})""").find(text)
        if (ym != null) {
            val year = ym.groupValues[1].toIntOrNull()
            val month = ym.groupValues[2].toIntOrNull()
            if (year != null && month != null && month in 1..12) {
                return Calendar.getInstance().apply {
                    clear()
                    set(year, month - 1, 1)
                }.timeInMillis
            }
        }
        return null
    }

    /** 归属年月：先按文本，文本解析不出来才回退到 dateMillis */
    fun yearMonth(date: String?, dateMillis: Long?): Pair<Int, Int>? {
        val fromText = millis(date)
        if (fromText != null) {
            val c = Calendar.getInstance().apply { timeInMillis = fromText }
            return c.get(Calendar.YEAR) to (c.get(Calendar.MONTH) + 1)
        }
        if (dateMillis != null && dateMillis > 0L) {
            val c = Calendar.getInstance().apply { timeInMillis = dateMillis }
            return c.get(Calendar.YEAR) to (c.get(Calendar.MONTH) + 1)
        }
        return null
    }
}
