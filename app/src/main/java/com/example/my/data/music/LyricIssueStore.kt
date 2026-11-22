package com.example.my.data.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 歌词反馈/举报的本地队列。
 *
 * App 目前没有云端反馈接口，这里不做「假装已提交」：记录先落到本机，
 * 用户可以在反馈页复制内容后通过系统分享发送给开发者。
 */
object LyricIssueStore {
    private const val P = "lyric_issues"
    private const val KEY_LIST = "items"
    private const val MAX = 200

    fun add(
        context: Context,
        kind: String,
        song: String,
        reason: String,
        detail: String
    ): Boolean = runCatching {
        val prefs = context.getSharedPreferences(P, Context.MODE_PRIVATE)
        val old = runCatching { JSONArray(prefs.getString(KEY_LIST, "[]")) }
            .getOrDefault(JSONArray())
        val item = JSONObject()
            .put("kind", kind)
            .put("song", song)
            .put("reason", reason)
            .put("detail", detail)
            .put("time", System.currentTimeMillis())
        val next = JSONArray()
        next.put(item)
        for (i in 0 until old.length()) {
            if (next.length() >= MAX) break
            next.put(old.get(i))
        }
        prefs.edit().putString(KEY_LIST, next.toString()).apply()
        true
    }.getOrDefault(false)

    fun toText(
        kind: String,
        song: String,
        reason: String,
        detail: String
    ): String = buildString {
        appendLine("【MY足迹 · ${if (kind == "report") "举报" else "歌词反馈"}】")
        appendLine("歌曲：$song")
        appendLine("类型：$reason")
        if (detail.isNotBlank()) appendLine("说明：$detail")
        appendLine("时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date())}")
    }
}
