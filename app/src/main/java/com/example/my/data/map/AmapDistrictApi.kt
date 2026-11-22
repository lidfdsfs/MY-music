package com.example.my.data.map

import com.example.my.data.AmapApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.BoundingBox
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** 一个行政区：省 / 市 / 县区都能用同一套结构 */
data class AdminArea(
    val adcode: String,
    val name: String,
    val level: String,
    /** 所属市（直辖市的「城区/郊县」也照此填，方便显示「重庆 · 渝北区」） */
    val city: String,
    val lat: Double,
    val lon: Double
)

/**
 * 高德行政区划查询（`v3/config/district`）。
 *
 * 用途只有一个：把「按县下载离线地图」需要的两样东西问出来——
 * 1. 某省下面到底有哪些县 / 区（adcode + 中心点）；
 * 2. 某个县的边界（`extensions=all` 才返回 polyline），换算成瓦片下载用的矩形。
 *
 * 边界坐标是高德的 GCJ-02，和底图瓦片同一套坐标系，不需要再做偏移纠正。
 */
object AmapDistrictApi {

    private const val BASE = "https://restapi.amap.com/v3/config/district"

    /** 最近一次失败原因（中文），给界面在「列表空」时说明到底是谁的问题 */
    @Volatile
    var lastError: String? = null
        private set

    private val key: String
        get() = AmapApi.userKey?.trim()?.takeIf { it.isNotBlank() } ?: AmapApi.KEY

    /** 全国省级列表（一次请求拿完，界面按需展开） */
    suspend fun provinces(): List<AdminArea> = withContext(Dispatchers.IO) {
        val json = call(
            "keywords=${URLEncoder.encode("中国", "UTF-8")}&subdistrict=1&extensions=base"
        )
        val root = json.optJSONArray("districts")?.optJSONObject(0) ?: return@withContext emptyList()
        val children = root.optJSONArray("districts") ?: return@withContext emptyList()
        (0 until children.length()).mapNotNull { i ->
            children.optJSONObject(i)?.let { area(it, city = "") }
        }
    }

    /**
     * 某个省下的全部县 / 区。
     * `subdistrict=2` 会一次返回「省 → 市 → 区县」两层，直辖市则是「省 → 城区/郊县 → 区县」。
     */
    suspend fun counties(province: AdminArea): List<AdminArea> = withContext(Dispatchers.IO) {
        val json = call("keywords=${province.adcode}&subdistrict=2&extensions=base")
        val root = json.optJSONArray("districts")?.optJSONObject(0) ?: return@withContext emptyList()
        val out = mutableListOf<AdminArea>()
        collectCounties(root.optJSONArray("districts"), province.city, out)
        out.distinctBy { it.adcode }
    }

    private fun collectCounties(children: JSONArray?, parentCity: String, out: MutableList<AdminArea>) {
        if (children == null) return
        for (i in 0 until children.length()) {
            val node = children.optJSONObject(i) ?: continue
            val level = node.optString("level")
            val name = node.optString("name")
            val cityOfNode = if (level == "city") name else parentCity
            if (level == "district") {
                area(node, city = parentCity)?.let { out.add(it) }
            }
            // 市下面还有区县；「省直辖县」这类则是 district 直接挂在省下
            collectCounties(node.optJSONArray("districts"), cityOfNode, out)
        }
    }

    /**
     * 县的边界矩形（经纬度，GCJ-02）。
     * 边界缺失时退回「中心点 ± 一小块」——宁可框大一点多下几张瓦片，也不要下载成空。
     */
    suspend fun bounds(area: AdminArea): BoundingBox = withContext(Dispatchers.IO) {
        val json = runCatching {
            call("keywords=${area.adcode}&subdistrict=0&extensions=all")
        }.getOrNull()
        val node = json?.optJSONArray("districts")?.optJSONObject(0)
        val polyline = node?.optString("polyline").orEmpty()

        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var count = 0
        if (polyline.isNotBlank()) {
            polyline.split('|', ';').forEach { piece ->
                val p = piece.trim()
                if (p.isEmpty()) return@forEach
                val parts = p.split(',')
                if (parts.size < 2) return@forEach
                val lon = parts[0].toDoubleOrNull() ?: return@forEach
                val lat = parts[1].toDoubleOrNull() ?: return@forEach
                if (lon < minLon) minLon = lon
                if (lon > maxLon) maxLon = lon
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
                count++
            }
        }
        if (count < 3) {
            val dLat = 0.13
            val dLon = 0.16
            return@withContext BoundingBox(
                area.lat + dLat, area.lon + dLon, area.lat - dLat, area.lon - dLon
            )
        }
        // 边界外留一点余量，避免最外一圈的街道被切掉
        val padLat = 0.02
        val padLon = 0.02
        BoundingBox(maxLat + padLat, maxLon + padLon, minLat - padLat, minLon - padLon)
    }

    private fun area(node: JSONObject, city: String): AdminArea? {
        val adcode = node.optString("adcode").trim()
        val name = node.optString("name").trim()
        if (adcode.isEmpty() || name.isEmpty()) return null
        val center = node.optString("center").orEmpty().split(',')
        val lon = center.getOrNull(0)?.toDoubleOrNull() ?: 0.0
        val lat = center.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        return AdminArea(
            adcode = adcode,
            name = name,
            level = node.optString("level"),
            city = city,
            lat = lat,
            lon = lon
        )
    }

    private fun call(params: String): JSONObject {
        val url = "$BASE?$params&key=${URLEncoder.encode(key, "UTF-8")}&output=json"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) {
                lastError = "行政区划服务返回 HTTP ${conn.responseCode}"
                throw IOException("HTTP ${conn.responseCode}")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            if (json.optString("status") != "1") {
                val info = json.optString("info")
                lastError = when {
                    info.contains("INVALID_USER_KEY") -> "地图 Key 无效，离线地图无法下载"
                    info.contains("USERKEY_PLAT_NOMATCH") -> "地图 Key 平台不匹配（需「Web服务」类型）"
                    info.contains("OVER_LIMIT") -> "地图 Key 今日调用量已超限"
                    info.isBlank() -> "行政区划服务无响应"
                    else -> "行政区划服务返回：$info"
                }
                throw IOException(lastError)
            }
            lastError = null
            return json
        } finally {
            conn.disconnect()
        }
    }
}
