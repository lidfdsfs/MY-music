package com.example.my.data.music

/**
 * 足迹配乐（BGM）：本地缓存文件的绝对路径 + 展示信息。
 *
 * 之所以落到本地再引用：足迹要能离线回看，且 Tunetank 的预览直链会过期，
 * 只存 URL 的话几天后就变成一条放不出声的空记录。
 */
data class BgmPick(
    val path: String,
    val title: String,
    val artist: String? = null,
    val source: String = "Tunetank",
    val trackId: String? = null
)
