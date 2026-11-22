package com.example.my.data.map

import com.example.my.data.AmapApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/** 一个经纬度点（GCJ-02，与高德瓦片、[AmapApi] 检索结果同一口径）。 */
data class LatLon(val lat: Double, val lon: Double)

/**
 * 驾车路线的一段引导。
 *
 * [polyline] 是这一段自己的折线（高德按导航动作切好的），
 * 所以「当前该播哪一条」可以由「剩余距离落在哪一段」直接推出来，
 * 不需要再把整条路线重新按角度切分。
 *
 * [action] 是高德给的原始动作码（「左转 / 右转 / 直行 / 靠左 / 靠右 / 掉头…」），
 * 界面据此挑转向图形；[assistantAction] 是「随即」那句，用来做副标题。
 */
data class DriveRouteStep(
    val instruction: String,
    val road: String,
    val distanceM: Int,
    val polyline: List<LatLon>,
    val action: String = "",
    val assistantAction: String = ""
)

/**
 * 一条真实的驾车路线。
 *
 * 距离与时长都取高德返回的原值（米 / 秒），界面层只做显示格式化；
 * [points] 是把所有 step 的折线首尾拼接后的完整轨迹，地图画线只用它。
 *
 * [tollsYuan] / [lights] / [strategyLabel] 是高德在同一份响应里一起给的方案特征，
 * 路线预览页要拿它们和时长一起比较——这是高德「三条路线任选」的信息基础。
 */
data class DriveRoute(
    val points: List<LatLon>,
    val distanceM: Int,
    val durationS: Int,
    val steps: List<DriveRouteStep>,
    val tollsYuan: Int = 0,
    val lights: Int = 0,
    val strategyLabel: String = ""
) {
    /**
     * 从 [lat]/[lon] 出发，沿路线已经走了多远（米）。
     *
     * 用「最近折线点之前的所有段长」近似，配合 [GeoUtil.metersBetween] 计算。
     * 走偏到几公里外时最近点会跳，导航层另有偏航判定，这里只负责进度。
     */
    fun travelledMeters(lat: Double, lon: Double): Int {
        if (points.isEmpty()) return 0
        var bestIdx = 0
        var bestD = Double.MAX_VALUE
        points.forEachIndexed { i, p ->
            val d = GeoUtil.metersBetween(lat, lon, p.lat, p.lon)
            if (d < bestD) {
                bestD = d
                bestIdx = i
            }
        }
        var sum = 0.0
        for (i in 1..bestIdx) {
            sum += GeoUtil.metersBetween(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
        }
        return sum.toInt()
    }

    /** 到终点的直线距离（米），到达态与「距目的地」显示用 */
    fun straightToEnd(lat: Double, lon: Double): Int {
        val end = points.lastOrNull() ?: return 0
        return GeoUtil.metersBetween(lat, lon, end.lat, end.lon).toInt()
    }

    /**
     * 当前应该显示哪一段引导：剩余距离落在第几段。
     * 全部走完后返回最后一段，避免到终点附近引导卡突然空白。
     */
    fun stepAt(travelledM: Int): DriveRouteStep? {
        if (steps.isEmpty()) return null
        var acc = 0
        steps.forEach { s ->
            acc += s.distanceM
            if (travelledM < acc) return s
        }
        return steps.last()
    }

    /** 当前这一段引导结束时的累计里程（米），用来算「还有多少米该转弯」 */
    fun stepEndMeters(travelledM: Int): Int {
        if (steps.isEmpty()) return distanceM
        var acc = 0
        steps.forEach { s ->
            acc += s.distanceM
            if (travelledM < acc) return acc
        }
        return distanceM
    }

    /** 车位离路线最近点的距离（米），偏航判定用 */
    fun nearestMeters(lat: Double, lon: Double): Int {
        var best = Double.MAX_VALUE
        points.forEach { p ->
            val d = GeoUtil.metersBetween(lat, lon, p.lat, p.lon)
            if (d < best) best = d
        }
        return if (best == Double.MAX_VALUE) 0 else best.toInt()
    }

    /**
     * 按「已走米数」在真实折线上插值出一个点。
     *
     * 只给**模拟导航**用：用户显式打开「模拟导航」后，车标沿真实路线按速度推进，
     * 这时候没有真实 GPS 位移可以投影，必须能在折线上按里程取点。
     * 正常导航永远走 [travelledMeters]，不允许拿它冒充定位。
     */
    fun pointAt(travelledM: Int): LatLon? {
        if (points.isEmpty()) return null
        var acc = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val seg = GeoUtil.metersBetween(a.lat, a.lon, b.lat, b.lon)
            if (acc + seg >= travelledM) {
                val t = if (seg <= 0.0) 0.0 else ((travelledM - acc) / seg).coerceIn(0.0, 1.0)
                return LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
            }
            acc += seg
        }
        return points.last()
    }

    /** 路线是否经过高速：用来决定要不要弹出口预告，而不是靠剩余里程硬凑 */
    val hasHighway: Boolean
        get() = steps.any {
            it.road.contains("高速") || it.instruction.contains("高速") ||
                it.road.contains("Expwy", true)
        }
}

/** 与轨迹模块同一套球面距离，避免两处口径不一致。 */
internal object GeoUtil {
    private const val R = 6_371_000.0

    fun metersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * R * Math.atan2(sqrt(a), sqrt(1 - a))
    }
}

/**
 * 路线偏好：直接映射高德 v3 driving 的 `strategy` 取值。
 *
 * 高德手机端「路线偏好」就是这四个（外加「智能推荐」），
 * 放在这里而不是界面里，是为了让「用户选了什么」与「请求发了什么」是同一个数。
 */
enum class DrivePref(val strategy: Int, val zh: String, val en: String) {
    SPEED(0, "速度优先", "Fastest"),
    NO_JAM(4, "躲避拥堵", "Avoid jams"),
    NO_TOLL(1, "避免收费", "Avoid tolls"),
    NO_HIGHWAY(6, "不走高速", "Avoid highway")
}

/**
 * 高德驾车路径规划（Web 服务 v3 driving）。
 *
 * 为什么不用第三方路径服务：底图、POI 检索已经全是高德，路线也统一走高德，
 * 三者坐标同为 GCJ-02，不需要在路线和底图之间再做一次纠偏。
 * 失败原因会写进 [lastError]，供驾驶页直接显示，而不是静默退回假路线。
 */
object AmapDrive {

    /** 最近一次失败原因（已转中文），成功时清空 */
    @Volatile
    var lastError: String? = null
        private set

    private val key: String
        get() = AmapApi.userKey?.trim()?.takeIf { it.isNotBlank() } ?: AmapApi.KEY

    /**
     * 只取最快的一条。给偏航重算、模拟导航这类「无脑要一条」的场景用。
     */
    suspend fun plan(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        strategy: Int = 0
    ): DriveRoute? = planAll(originLat, originLon, destLat, destLon, strategy).firstOrNull()

    /**
     * 取全部备选方案。
     *
     * 高德 v3 在 `route.paths` 里一次给最多三条方案（不同策略或不同路线），
     * 手机端的「3 条路线任选其一」就是这一份数据。只取第一条会让预览页
     * 永远只有一个选项，等于没有比较——所以这里把 paths 全解出来。
     */
    suspend fun planAll(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        strategy: Int = 0
    ): List<DriveRoute> = withContext(Dispatchers.IO) {
        lastError = null
        val origin = "${fmt(originLon)},${fmt(originLat)}"
        val dest = "${fmt(destLon)},${fmt(destLat)}"
        // 多策略（5）让高德自己按不同偏好给多套方案，避免三条路线只在几十米上有差别
        val url = "https://restapi.amap.com/v3/direction/driving" +
            "?origin=$origin&destination=$dest&extensions=all&strategy=$strategy" +
            "&key=${URLEncoder.encode(key, "UTF-8")}"
        try {
            val text = httpGet(url)
            val root = JSONObject(text)
            if (root.optString("status") != "1") {
                lastError = explain(root.optString("info"))
                return@withContext emptyList()
            }
            val paths = root.optJSONObject("route")?.optJSONArray("paths")
            if (paths == null || paths.length() == 0) {
                lastError = "没有规划出可行路线"
                return@withContext emptyList()
            }
            val out = mutableListOf<DriveRoute>()
            for (pi in 0 until paths.length()) {
                parsePath(paths.getJSONObject(pi))?.let { out += it }
            }
            if (out.isEmpty()) lastError = "路线数据不完整"
            out
        } catch (e: Exception) {
            lastError = "路线规划失败：${e.message ?: "网络异常"}"
            emptyList()
        }
    }

    /** 把高德的一条 path 解成 [DriveRoute]：折线、分步指令、方案特征一起拿。 */
    private fun parsePath(path: JSONObject): DriveRoute? {
        val all = mutableListOf<LatLon>()
        val steps = mutableListOf<DriveRouteStep>()
        val stepArr = path.optJSONArray("steps")
        if (stepArr != null) {
            for (i in 0 until stepArr.length()) {
                val s = stepArr.getJSONObject(i)
                val line = parsePolyline(s.optString("polyline"))
                if (line.isEmpty()) continue
                // 相邻段首尾点重合，拼接时去掉重复点，否则画线会在拐点处出现小毛刺
                if (all.isNotEmpty() && all.last() == line.first()) all.addAll(line.drop(1))
                else all.addAll(line)
                steps += DriveRouteStep(
                    instruction = s.optString("instruction").ifBlank { "继续前行" },
                    road = s.optString("road").orEmpty(),
                    distanceM = s.optString("distance").toIntOrNull() ?: 0,
                    polyline = line,
                    action = s.optString("action").orEmpty(),
                    assistantAction = parseAssistantAction(s)
                )
            }
        }
        if (all.size < 2) return null
        return DriveRoute(
            points = all,
            distanceM = path.optString("distance").toIntOrNull() ?: 0,
            durationS = path.optString("duration").toIntOrNull() ?: 0,
            steps = steps,
            tollsYuan = path.optString("tolls").toIntOrNull() ?: 0,
            lights = path.optString("traffic_lights").toIntOrNull() ?: 0,
            strategyLabel = path.optString("strategy").orEmpty()
        )
    }

    /**
     * 清洗「随即动作」：高德有时返回字符串、有时返回数组。
     * 数组被 optString 直接 toString 会变成 "[]" 或 "[xxx]"，这里统一取值并去掉空括号占位。
     */
    private fun parseAssistantAction(s: JSONObject): String {
        val arr: JSONArray? = s.optJSONArray("assistant_action")
        if (arr != null) {
            val items = (0 until arr.length()).map { arr.optString(it).orEmpty().trim() }
                .filter { it.isNotBlank() && it != "[]" }
            return items.joinToString("，")
        }
        return s.optString("assistant_action").orEmpty().trim()
            .removeSurrounding("[", "]").trim()
            .takeIf { it.isNotBlank() && it != "[]" } ?: ""
    }

    /** 高德 polyline："lon,lat;lon,lat;..." */
    private fun parsePolyline(raw: String): List<LatLon> {
        if (raw.isBlank()) return emptyList()
        return raw.split(';').mapNotNull { seg ->
            val parts = seg.split(',')
            if (parts.size < 2) return@mapNotNull null
            val lon = parts[0].trim().toDoubleOrNull() ?: return@mapNotNull null
            val lat = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            if (abs(lat) > 90 || abs(lon) > 180) null else LatLon(lat, lon)
        }
    }

    private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.6f", v)

    private fun explain(info: String): String = when {
        info.contains("INVALID_USER_KEY", true) -> "地图 Key 无效"
        info.contains("USERKEY_PLAT_NOMATCH", true) -> "地图 Key 平台不匹配，请填 Web 服务 Key"
        info.contains("DAILY_QUERY_OVER_LIMIT", true) -> "今日路线规划次数已用完"
        info.contains("OVER_QUERY_LIMIT", true) -> "请求太频繁，请稍后再试"
        info.isBlank() -> "路线规划失败"
        else -> info
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
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
