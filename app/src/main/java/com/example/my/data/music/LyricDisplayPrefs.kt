package com.example.my.data.music

import android.content.Context

/** 歌词页的显示偏好：字号、时间偏移、助唱标记、状态栏/桌面歌词、简繁。 */
object LyricDisplayPrefs {
    private const val P = "lyric_display"
    private const val KEY_FONT = "font_scale"
    private const val KEY_GUIDE = "guide"
    private const val KEY_OFFSET = "offset_ms"
    private const val KEY_STATUS = "status_bar"
    private const val KEY_DESKTOP = "desktop"
    private const val KEY_TRADITIONAL = "traditional"

    private fun sp(context: Context) =
        context.getSharedPreferences(P, Context.MODE_PRIVATE)

    fun fontScale(context: Context): Float =
        sp(context).getFloat(KEY_FONT, 1f).coerceIn(0.8f, 1.6f)

    fun setFontScale(context: Context, value: Float) =
        sp(context).edit().putFloat(KEY_FONT, value.coerceIn(0.8f, 1.6f)).apply()

    fun guide(context: Context): Boolean = sp(context).getBoolean(KEY_GUIDE, false)

    fun setGuide(context: Context, value: Boolean) =
        sp(context).edit().putBoolean(KEY_GUIDE, value).apply()

    fun offsetMs(context: Context): Long =
        sp(context).getLong(KEY_OFFSET, 0L).coerceIn(-800L, 800L)

    fun setOffsetMs(context: Context, value: Long) =
        sp(context).edit().putLong(KEY_OFFSET, value.coerceIn(-800L, 800L)).apply()

    fun statusBar(context: Context): Boolean = sp(context).getBoolean(KEY_STATUS, false)

    fun setStatusBar(context: Context, value: Boolean) =
        sp(context).edit().putBoolean(KEY_STATUS, value).apply()

    fun desktop(context: Context): Boolean = sp(context).getBoolean(KEY_DESKTOP, false)

    fun setDesktop(context: Context, value: Boolean) =
        sp(context).edit().putBoolean(KEY_DESKTOP, value).apply()

    fun traditional(context: Context): Boolean =
        sp(context).getBoolean(KEY_TRADITIONAL, false)

    fun setTraditional(context: Context, value: Boolean) =
        sp(context).edit().putBoolean(KEY_TRADITIONAL, value).apply()

    private const val KEY_TWO_LINE = "two_line"

    /** 双行歌词模式：只显示当前句 + 下一句，不整页滚动（QQ 音乐默认样式） */
    fun twoLine(context: Context): Boolean = sp(context).getBoolean(KEY_TWO_LINE, false)

    fun setTwoLine(context: Context, value: Boolean) =
        sp(context).edit().putBoolean(KEY_TWO_LINE, value).apply()
}
