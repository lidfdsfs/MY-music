package com.example.my.data.music

/**
 * 歌曲故事专用检索词。
 *
 * 用户明确要求检索词为「歌名 + 歌手 + 创作背景」：歌名和歌手负责锁定对象，
 * 「创作背景」把结果从歌词页/播放页拉向百科、访谈和专辑介绍。
 * 歌手未知时只拼「歌名 + 创作背景」；所有空白折叠成单个空格。
 */
object SongStoryQuery {

    const val BACKGROUND = "创作背景"

    private val UNKNOWN_ARTISTS = setOf(
        "未知歌手", "未知艺人", "unknown artist", "unknown singer", "未知"
    )

    fun build(title: String, artist: String?): String {
        val t = title.trim().replace(Regex("\\s+"), " ")
        val a = artist?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        return if (a.isBlank() || a.lowercase() in UNKNOWN_ARTISTS) {
            "$t $BACKGROUND"
        } else {
            "$t $a $BACKGROUND"
        }
    }
}
