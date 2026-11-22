package com.example.my.data

import android.content.Context

/**
 * 「联网搜索」档位的唯一真源。
 *
 * 四档：
 *  - [OFF]      关闭：不发起任何联网网页检索（票务等内置接口同样受总闸拦截）
 *  - [FAST]     快速：默认追求抓取速度，少抓正文、短超时；遇到新闻/职务变动/价格等
 *                会变化的实时事实时，临时放宽到「够核实」的最小抓取量（仍是快速档）
 *  - [BALANCED] 均衡：速度与资料量兼顾（默认）
 *  - [DEEP]     高效：多角度检索、抓取更多网页正文、放宽超时，资料更全但更慢
 *
 * 档位落到 SP，助手页 / 生成行程 / 音乐等入口共用，重启后保留。旧的布尔开关
 * （enabled）仍被部分页面读取，这里同步维护：OFF 对应 false，其余对应 true。
 */
enum class SearchMode(val zh: String, val en: String) {
    OFF("关闭", "Off"),
    FAST("快速", "Fast"),
    BALANCED("均衡", "Balanced"),
    DEEP("高效", "Deep");

    val label: String get() = zh
}

object WebSearchPrefs {
    private const val PREF = "web_search_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MODE = "mode"

    /** 读取当前档位；旧布尔存档没有 mode 时，按 enabled 回退到 OFF / BALANCED。 */
    fun getMode(ctx: Context): SearchMode {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp.getString(KEY_MODE, null)?.let { raw ->
            runCatching { return SearchMode.valueOf(raw) }
        }
        return if (sp.getBoolean(KEY_ENABLED, true)) SearchMode.BALANCED else SearchMode.OFF
    }

    fun setMode(ctx: Context, mode: SearchMode) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .putBoolean(KEY_ENABLED, mode != SearchMode.OFF)
            .apply()
    }

    /** 兼容旧调用：是否开启联网（OFF 为关，其余为开）。 */
    fun isEnabled(ctx: Context): Boolean = getMode(ctx) != SearchMode.OFF

    /** 兼容旧调用：只写布尔时，开 → 均衡，关 → 关闭。 */
    fun setEnabled(ctx: Context, enabled: Boolean) {
        setMode(ctx, if (enabled) SearchMode.BALANCED else SearchMode.OFF)
    }
}
