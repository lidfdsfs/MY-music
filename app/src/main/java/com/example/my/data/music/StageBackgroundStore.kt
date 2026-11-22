package com.example.my.data.music

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 横屏播放器的自定义背景（视频或图片）。
 *
 * 只存 content:// 的字符串，真正解码交给 UI 层；配合 OpenDocument 拿到的持久授权，
 * 重启 App、换歌都还在，跟 QQ 音乐那种「我的播放器背景」是一个意思。
 */
object StageBackgroundStore {
    private const val PREFS = "player_stage_bg"
    private const val KEY_URI = "bg_uri"

    var uri by mutableStateOf<String?>(null)
        private set

    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        uri = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URI, null)
    }

    fun set(context: Context, value: String?) {
        uri = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (value == null) remove(KEY_URI) else putString(KEY_URI, value)
        }.apply()
    }

    fun clear(context: Context) = set(context, null)
}
