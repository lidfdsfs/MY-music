package com.example.my.data

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 城市命中（搜索结果 / 定位结果）。typeName 用于把行政区划本体排在同名 POI 之前 */
data class CityHit(
    val name: String,
    val region: String,
    val lat: Double,
    val lon: Double,
    val typeName: String = ""
)

/** 单日预报 */
data class DayForecast(
    val day: String,
    val emoji: String,
    val desc: String,
    val percent: Int,
    val low: Int,
    val high: Int
)

/** 实况 + 未来 7 天 */
data class WeatherSnapshot(
    val city: String,
    val temp: Int,
    val emoji: String,
    val desc: String,
    val wind: Int,
    val humidity: Int,
    val updatedAt: String,
    val days: List<DayForecast>
)

/**
 * 天气数据源：UAPI（https://uapis.cn，免费、免 Key）。
 * - 按城市名查询实时天气 + 7 天预报
 * - 城市搜索仍走高德 / Open-Meteo geocoding；定位走系统 Geocoder + 高德逆地理
 */
object WeatherApi {

    private const val GEO_URL = "https://geocoding-api.open-meteo.com/v1/search"
    private const val WEATHER_URL = "https://uapis.cn/api/v1/misc/weather"

    /** 按名称搜索城市 */
    suspend fun searchCity(query: String): List<CityHit> = withContext(Dispatchers.IO) {
        val url = "$GEO_URL?name=${URLEncoder.encode(query, "UTF-8")}&count=8&language=zh&format=json"
        val root = JSONObject(httpGet(url))
        val arr = root.optJSONArray("results") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").ifBlank { return@mapNotNull null }
            val region = listOf(o.optString("admin1"), o.optString("country"))
                .filter { it.isNotBlank() && it != name }
                .distinct()
                .joinToString(" · ")
            val lat = o.optDouble("latitude", Double.NaN)
            val lon = o.optDouble("longitude", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) null else CityHit(name, region, lat, lon)
        }
    }

    /** 拉取指定城市的实况与 7 天预报（UAPI，按城市名查询，忽略 lat/lon） */
    suspend fun fetch(city: String, lat: Double, lon: Double): WeatherSnapshot =
        withContext(Dispatchers.IO) {
            val url = "$WEATHER_URL?city=${URLEncoder.encode(city, "UTF-8")}&forecast=true&lang=zh"
            val root = JSONObject(httpGet(url))
            val weather = root.optString("weather").ifBlank { "未知" }
            val (emoji, desc) = describeText(weather)
            val windLevel = root.optString("wind_power").filter { it.isDigit() }.ifBlank { "0" }.toInt()

            val forecastArr = root.optJSONArray("forecast")
            val days = (0 until (forecastArr?.length() ?: 0)).map { i ->
                val o = forecastArr?.optJSONObject(i) ?: return@map DayForecast("", "多云", "", 0, 0, 0)
                val dayWeather = o.optString("weather_day").ifBlank { weather }
                val (e, d) = describeText(dayWeather)
                DayForecast(
                    day = dayLabelFromWeek(o.optString("date"), o.optString("week"), i),
                    emoji = e,
                    desc = d,
                    percent = o.optInt("pop", 0),
                    low = o.optDouble("temp_min", 0.0).toInt(),
                    high = o.optDouble("temp_max", 0.0).toInt()
                )
            }

            WeatherSnapshot(
                city = root.optString("city").ifBlank { city },
                temp = root.optDouble("temperature", 0.0).toInt(),
                emoji = emoji,
                desc = desc,
                wind = windLevel,
                humidity = root.optInt("humidity", 0),
                updatedAt = root.optString("report_time").ifBlank {
                    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date())
                },
                days = days
            )
        }

    /**
     * 定位当前城市。
     *
     * 走 [Locator.singleFix]：缓存点按精度打分选优，没有可用缓存就主动要一次实时定位，
     * 不再像旧实现那样直接采信「时间最新」的 getLastKnownLocation——
     * 那个点可能是几小时前在另一个城市留下的，位置和城市名都会错。
     *
     * 返回的 lat/lon 是 **GCJ-02**，可直接与高德地图 / 高德检索结果对齐。
     */
    suspend fun locateCity(
        context: Context,
        timeoutMs: Long = 8_000L,
        /** false = 只用缓存定位（天气这类只要城市级精度的场景，不值得为它等搜星） */
        allowLive: Boolean = true
    ): CityHit? =
        withContext(Dispatchers.IO) {
            val fix = if (allowLive) {
                Locator.singleFix(context, timeoutMs)
            } else {
                Locator.lastKnown(context, 30 * 60_000L)
            } ?: return@withContext null

            // Geocoder 用 WGS-84 入参；国产 ROM 多数没有 Google 服务会返回空
            val name = runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.CHINA)
                    .getFromLocation(fix.wgsLat, fix.wgsLon, 1)
                    ?.firstOrNull()
                    ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
            }.getOrNull()?.takeIf { it.isNotBlank() }

            // 高德逆地理兜底：比「当前位置」这种占位文案有用得多
            val city = name ?: AmapApi.reverseGeocode(fix.lat, fix.lon) ?: "当前位置"
            CityHit(city, "", fix.lat, fix.lon)
        }

    // ============ 内部工具 ============

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** 中文天气文本 → 图标判据（不再存 emoji，渲染层统一用矢量图标） */
    private fun describeText(weather: String): Pair<String, String> {
        val w = weather
        val glyph = when {
            w.contains("雷") -> "雷"
            w.contains("雪") -> "雪"
            w.contains("雨") && w.contains("小") -> "小雨"
            w.contains("雨") -> "雨"
            w.contains("雾") || w.contains("霾") -> "雾"
            w.contains("阴") -> "阴"
            w.contains("多云") || w.contains("间晴") -> "多云"
            w.contains("晴") -> "晴"
            else -> "多云"
        }
        return glyph to w
    }

    private fun dayLabelFromWeek(date: String, week: String, index: Int): String {
        if (index == 0) return "今天"
        if (index == 1) return "明天"
        if (week.isNotBlank()) return week
        if (date.isBlank()) return "第${index + 1}天"
        val cal = Calendar.getInstance()
        val parsed = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(date)
        }.getOrNull() ?: return date.takeLast(5)
        cal.time = parsed
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            else -> "周日"
        }
    }
}
