package com.example.my.data.map

import android.content.Context
import com.example.my.data.PoiResult

/**
 * 「最近去过」：驾驶页搜索框空态里那几条。
 *
 * 存的是用户自己搜过并真算过路的目的地，不是推荐位——高德首页的「最近搜索」
 * 也是这个语义。用独立的 SharedPreferences 而不是主 DataStore：
 * 这只是一份界面上要用的小缓存，丢了不影响任何核心数据，没必要进主链路。
 *
 * 编码用两个不可能出现在地名里的控制字符做分隔，避免地址里带逗号把记录切坏。
 */
object DriveRecent {

    private const val SP = "drive_recent"
    private const val KEY = "items"
    private const val MAX = 6
    private const val FIELD = '\u0001'
    private const val ROW = '\u0002'

    fun load(ctx: Context): List<PoiResult> {
        val raw = runCatching {
            ctx.getSharedPreferences(SP, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        }.getOrDefault("")
        if (raw.isBlank()) return emptyList()
        return raw.split(ROW).mapNotNull { row ->
            val parts = row.split(FIELD)
            if (parts.size < 4) return@mapNotNull null
            val lat = parts[2].toDoubleOrNull() ?: return@mapNotNull null
            val lon = parts[3].toDoubleOrNull() ?: return@mapNotNull null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            PoiResult(
                name = parts[0].ifBlank { "未命名地点" },
                address = parts[1],
                lat = lat,
                lon = lon
            )
        }
    }

    /** 记一条：同名（同坐标 50 米内）的旧记录先删掉，再插到最前面。 */
    fun push(ctx: Context, poi: PoiResult) {
        val kept = load(ctx).filterNot {
            it.name == poi.name &&
                kotlin.math.abs(it.lat - poi.lat) < 0.0005 &&
                kotlin.math.abs(it.lon - poi.lon) < 0.0005
        }
        val list = (listOf(poi) + kept).take(MAX)
        val raw = list.joinToString(ROW.toString()) { p ->
            listOf(p.name, p.address, p.lat.toString(), p.lon.toString()).joinToString(FIELD.toString())
        }
        runCatching {
            ctx.getSharedPreferences(SP, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
        }
    }
}
