package com.example.my.data

/** 单条景点 / 活动（AI 提取的轻量结构） */
data class SpotBlueprint(val name: String, val note: String)

/** 一天的行程（AI 提取） */
data class GuideDayBlueprint(
    val day: Int,
    val title: String,
    val spots: List<SpotBlueprint>
)

/**
 * AI 从"粗略旅游日志"里总结出的攻略蓝图。
 * 字段尽量贴近 Guide，方便一键映射成真正的 Guide。
 */
data class GuideBlueprint(
    val title: String,
    val city: String,
    val type: String,
    val tags: List<String>,
    val intro: String,
    val days: List<GuideDayBlueprint>,
    val hotels: List<Pair<String, String>>,
    val transports: List<Pair<String, String>>,
    val costs: List<Pair<String, String>>,
    val foods: List<Pair<String, String>>
)

/**
 * AI 从一句话里提取的足迹草稿。
 * lat/lon 为可选项；若缺失但城市已知，由 AppState 用高德补全坐标。
 * missing 列出仍需向用户追问的字段。
 */
data class FootprintBlueprint(
    val city: String,
    val name: String,
    val note: String,
    val date: String,
    val lat: Double?,
    val lon: Double?,
    val missing: List<String>
)

/**
 * AI 识别出的"设置类"指令意图。
 * kind: weather_city | setting | language | open | none
 */
data class CommandIntent(
    val kind: String,
    val target: String,
    val value: String,
    val label: String
)
