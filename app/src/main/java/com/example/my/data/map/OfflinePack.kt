package com.example.my.data.map

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * 一块已下载的离线地图（最小单位是县 / 区，直辖市下辖区县同样按县处理）。
 *
 * 只存「这块区域的边界 + 下载了多少瓦片 + 占了多大」，瓦片本体仍在 osmdroid 的瓦片缓存库里，
 * 这样地图浏览时直接命中缓存，不需要再维护第二套读取路径。
 */
data class OfflinePack(
    val adcode: String,
    val name: String,
    val province: String,
    val city: String,
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val tiles: Int,
    val bytes: Long,
    val savedAt: Long,
    /** 下的是哪套底图：vector / satellite */
    val layer: String
) {
    val sizeText: String
        get() = when {
            bytes >= 1024L * 1024L * 1024L ->
                String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }

    /** 层级范围说明，如 6-13 级 */
    val zoomText: String get() = "$minZoom-$maxZoom"
}

/** 离线包的本地索引：filesDir/offline_map/index.json */
object OfflinePackStore {

    private const val DIR_NAME = "offline_map"
    private const val FILE_NAME = "index.json"

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { runCatching { mkdirs() } }

    private fun file(context: Context): File = File(dir(context), FILE_NAME)

    @Synchronized
    fun all(context: Context): List<OfflinePack> {
        val f = file(context)
        if (!f.isFile) return emptyList()
        return runCatching {
            val text = f.readText()
            if (text.isBlank()) return emptyList()
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                runCatching { read(o) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    fun find(context: Context, adcode: String): OfflinePack? =
        all(context).firstOrNull { it.adcode == adcode }

    @Synchronized
    fun save(context: Context, pack: OfflinePack) {
        val list = all(context).filterNot { it.adcode == pack.adcode } + pack
        write(context, list)
    }

    @Synchronized
    fun remove(context: Context, adcode: String) {
        write(context, all(context).filterNot { it.adcode == adcode })
    }

    @Synchronized
    fun clear(context: Context) {
        write(context, emptyList())
    }

    fun totalBytes(context: Context): Long = all(context).sumOf { it.bytes }

    private fun write(context: Context, list: List<OfflinePack>) {
        val arr = JSONArray()
        list.forEach { arr.put(write(it)) }
        runCatching {
            val tmp = File(dir(context), "$FILE_NAME.tmp")
            tmp.writeText(arr.toString())
            val target = file(context)
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }
    }

    private fun write(p: OfflinePack): JSONObject = JSONObject().apply {
        put("adcode", p.adcode)
        put("name", p.name)
        put("province", p.province)
        put("city", p.city)
        put("minLat", p.minLat)
        put("minLon", p.minLon)
        put("maxLat", p.maxLat)
        put("maxLon", p.maxLon)
        put("minZoom", p.minZoom)
        put("maxZoom", p.maxZoom)
        put("tiles", p.tiles)
        put("bytes", p.bytes)
        put("savedAt", p.savedAt)
        put("layer", p.layer)
    }

    private fun read(o: JSONObject): OfflinePack = OfflinePack(
        adcode = o.optString("adcode"),
        name = o.optString("name"),
        province = o.optString("province"),
        city = o.optString("city"),
        minLat = o.optDouble("minLat", 0.0),
        minLon = o.optDouble("minLon", 0.0),
        maxLat = o.optDouble("maxLat", 0.0),
        maxLon = o.optDouble("maxLon", 0.0),
        minZoom = o.optInt("minZoom", 6),
        maxZoom = o.optInt("maxZoom", 12),
        tiles = o.optInt("tiles", 0),
        bytes = o.optLong("bytes", 0L),
        savedAt = o.optLong("savedAt", 0L),
        layer = o.optString("layer", "vector")
    )
}
