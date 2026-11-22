package com.example.my.data.map

import com.example.my.data.AmapApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 高德实时交通态势（Web 服务 v3 traffic/status/circle）。
 *
 * 用途只有一个：驾驶页「路况」按钮打开后，显示当前道路的真实拥堵状况。
 * 之前这一格画的是一条固定比例的「畅通/缓行/拥堵」色带，看起来像数据、其实是装饰，
 * 现在要么拿到真实状态，要么明确说没拿到。
 */
object AmapTraffic {

    private val key: String
        get() = AmapApi.userKey?.trim()?.takeIf { it.isNotBlank() } ?: AmapApi.KEY

    /**
     * 查当前点周边的路况，返回可直接显示的中文描述（畅通 / 缓行 / 拥堵 / 未知）。
     * 失败返回 null，调用方负责显示「暂无路况」。
     */
    suspend fun status(lat: Double, lon: Double, radiusM: Int = 1500): String? =
        withContext(Dispatchers.IO) {
            val url = "https://restapi.amap.com/v3/traffic/status/circle" +
                "?location=${fmt(lon)},${fmt(lat)}&radius=$radiusM" +
                "&extensions=base&key=${URLEncoder.encode(key, "UTF-8")}"
            try {
                val root = JSONObject(httpGet(url))
                if (root.optString("status") != "1") return@withContext null
                val eval = root.optJSONObject("trafficinfo")?.optJSONObject("evaluation")
                    ?: return@withContext null
                // description 是「畅通」「缓行」这类短描述，没有时退到 status 码
                val desc = eval.optString("description").trim()
                if (desc.isNotEmpty()) desc else when (eval.optString("status")) {
                    "1" -> "畅通"
                    "2" -> "缓行"
                    "3" -> "拥堵"
                    else -> "未知"
                }
            } catch (e: Exception) {
                null
            }
        }

    private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.6f", v)

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 6_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
