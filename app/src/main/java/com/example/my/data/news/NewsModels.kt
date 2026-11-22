package com.example.my.data.news

/** 一天一期的 60 秒早报。 */
data class NewsBrief(
    val date: String,
    val items: List<String>,
    val source: String
)

/** 一个热搜平台/频道。 */
data class HotBoard(
    val id: String,
    val name: String,
    val items: List<HotItem>,
    val updatedAt: String = ""
)

data class HotItem(
    val rank: Int,
    val title: String,
    val hot: Long? = null,
    val url: String = ""
)

/** 历史上的今天。 */
data class HistoryDigest(
    val date: String,
    val items: List<HistoryEvent>
)

data class HistoryEvent(
    val year: String,
    val title: String,
    val description: String,
    val link: String = "",
    val type: String = ""
)
