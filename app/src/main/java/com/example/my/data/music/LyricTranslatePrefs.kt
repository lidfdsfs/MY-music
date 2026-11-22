package com.example.my.data.music

import android.content.Context

/**
 * 歌词翻译的持久化偏好。
 *
 * 翻译开关按“歌曲”记，而不是按页面记：用户给某首歌打开译词后，
 * 退出播放器、切歌、甚至杀进程再进来，同一首歌仍然自动显示译词。
 * 目标语言则全局记住，下一次打开继续用上次选的语言。
 */
object LyricTranslatePrefs {
    private const val P = "lyric_translate"
    private const val KEY_ON_SONGS = "on_songs"
    private const val KEY_TARGET_LANGUAGE = "target_language"

    private fun sp(context: Context) =
        context.getSharedPreferences(P, Context.MODE_PRIVATE)

    /** 歌曲稳定键：有真实 id 用 id；没有 id 时退回歌名 + 歌手。 */
    fun songKey(id: Long?, title: String, artist: String): String {
        if (id != null && id != Long.MIN_VALUE) return "id:$id"
        val t = title.trim()
        val a = artist.trim()
        if (t.isBlank() && a.isBlank()) return ""
        return "ta:$t|$a"
    }

    fun isTranslationOn(context: Context, songKey: String): Boolean {
        if (songKey.isBlank()) return false
        val saved = sp(context).getStringSet(KEY_ON_SONGS, emptySet()) ?: emptySet()
        return saved.contains(songKey)
    }

    fun setTranslationOn(context: Context, songKey: String, on: Boolean) {
        if (songKey.isBlank()) return
        val saved = HashSet(sp(context).getStringSet(KEY_ON_SONGS, emptySet()) ?: emptySet())
        if (on) saved.add(songKey) else saved.remove(songKey)
        sp(context).edit().putStringSet(KEY_ON_SONGS, saved).apply()
    }

    fun targetLanguage(context: Context): String =
        LyricTranslateLanguages.byCode(
            sp(context).getString(KEY_TARGET_LANGUAGE, LyricTranslateLanguages.ENGLISH)
        ).code

    fun setTargetLanguage(context: Context, code: String) {
        sp(context).edit()
            .putString(KEY_TARGET_LANGUAGE, LyricTranslateLanguages.byCode(code).code)
            .apply()
    }
}
