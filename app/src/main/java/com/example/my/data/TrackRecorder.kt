package com.example.my.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动记录的一个轨迹点（已转换为 GCJ-02，与地图底图一致）。
 *
 * [accuracy] 是这个点的水平精度半径（米）。以前没存，导致「这个点到底可不可信」无从判断：
 * 抖动点和真位移点长得一模一样。现在按点保存，过滤、抽稀、画线宽都能用上。
 */
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val time: Long,
    val accuracy: Float = 0f
)

/**
 * 轨迹记录的数据与算法工具：
 *  - 点集本地持久化（SharedPreferences，进程被杀也不丢）
 *  - 采样与里程计算
 *  - WGS-84 → GCJ-02 转换
 */
object TrackRecorder {

    private const val PREFS = "track_recorder"
    private const val KEY_POINTS = "points"
    private const val KEY_RECORDING = "recording"

    private const val GCJ_A = 6378245.0
    private const val GCJ_EE = 0.00669342162296594323

    /**
     * 精度差于 35 米的点直接丢弃（室内 / 基站定位常见）。
     * 门槛从 60 收到 35 是实测结论：40 米精度的静止噪声一小时能凭空「走」出 1~3 公里，
     * 与其画一条假的轨迹，不如老实留一段空白（记录面板会显示「GPS 信号弱」）。
     */
    private const val MAX_ACCURACY_M = 35f
    /** 「确实动了」的最小位移下限：比这还近的一律当抖动 */
    private const val MIN_MOVE_M = 5.0
    /** 隐含速度上限：超过视为卫星瞬移异常点 */
    private const val MAX_SPEED_KMH = 200.0
    /** 一次记录最多保留的点数，超过做 2:1 抽稀 */
    private const val MAX_POINTS = 5000
    /** 抽稀时转向角大于该值的点视为形状特征点，优先保留 */
    private const val SHAPE_TURN_DEG = 20.0

    fun load(context: Context): List<TrackPoint> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_POINTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                TrackPoint(
                    lat = o.optDouble("lat"),
                    lon = o.optDouble("lon"),
                    time = o.optLong("time"),
                    accuracy = o.optDouble("acc", 0.0).toFloat()
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, points: List<TrackPoint>) {
        val arr = JSONArray()
        points.forEach { p ->
            arr.put(
                JSONObject()
                    .put("lat", p.lat)
                    .put("lon", p.lon)
                    .put("time", p.time)
                    .put("acc", p.accuracy.toDouble())
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_POINTS, arr.toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_POINTS).apply()
    }

    /**
     * 「正在记录」标记。
     * 内存里的 trackingOn 会随进程一起消失，点集却能从磁盘恢复，
     * 两者不一致时用户会误以为还在记录（实际早已停止采点），
     * 所以这个开关必须落盘：正常结束/放弃时写 false，异常中断就留在 true。
     */
    fun setRecording(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_RECORDING, on).apply()
    }

    /** true 表示上次会话没有正常结束（进程被杀 / 崩溃 / 被系统回收） */
    fun isRecording(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_RECORDING, false)

    /** 轨迹总里程（公里） */
    fun distanceKm(points: List<TrackPoint>): Double =
        points.zipWithNext().sumOf { (a, b) -> haversineKm(a.lat, a.lon, b.lat, b.lon) }

    /** 每个点到起点的累计里程（公里），与 points 等长；用于「已走 xx km」 */
    fun cumulativeKm(points: List<TrackPoint>): DoubleArray {
        val out = DoubleArray(points.size)
        for (i in 1 until points.size) {
            out[i] = out[i - 1] + haversineKm(
                points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon
            )
        }
        return out
    }

    /** 两个 GCJ-02 坐标之间的地面距离（米）。定位观察与轨迹过滤共用 */
    fun metersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        haversineKm(lat1, lon1, lat2, lon2) * 1000.0

    /**
     * 新点该怎么处理。
     *
     * 旧的实现是「60 米精度 + 8 米/15 秒 + 120 km/h」三道一刀切，
     * 精度 30 米的点和精度 5 米的点用同一把尺子：要么把真位移当抖动（走两步不记），
     * 要么把抖动当真位移（原地站着里程表自己往上跑）。
     *
     * 现在分四层判断：
     *  1. 精度差于 35 米的点直接丢（室内 / 基站定位）；
     *  2. **系统给了速度且明确没在动（<0.4 m/s）**：位移只是噪声，要求超过 2.5 倍门槛
     *     并且连续两次一致才认 —— 这是最可靠的静止证据；
     *  3. 「动了」的门槛按这个点自己的精度走：有速度时 need = 精度 × 0.75（5~30 米），
     *     没有速度时放大到 ×3（15~150 米），位移 ≥ need 立即采纳，保证实时性；
     *  4. 门槛一半以上的灰色地带先挂起（[Decision.CONFIRM]），
     *     下一次上报**继续朝同一方向远离**才采纳 —— 单次抖动进不来，真走路也不会被吞掉。
     *
     * [pending] 是上一轮挂起的候选点，由调用方保管。
     */
    fun decide(
        prev: TrackPoint?,
        candidate: TrackPoint,
        pending: TrackPoint?,
        /** 系统给的瞬时速度（m/s），没有就传 null */
        speedMps: Float? = null
    ): Decision {
        if (candidate.accuracy > 0f && candidate.accuracy > MAX_ACCURACY_M) return Decision.REJECT
        val p = prev ?: return Decision.ACCEPT
        val dt = candidate.time - p.time
        // 时间差为 0 或负数：多 provider 同时上报同一条位置，或者系统时钟回拨
        if (dt <= 0L) return Decision.REJECT
        val distM = metersBetween(p.lat, p.lon, candidate.lat, candidate.lon)
        // 注意单位：distM 是米，要除以 1000 换成公里再除以小时数，否则任何移动都会超「200」被误杀
        if (dt >= 1_000L && distM / 1000.0 / (dt / 3_600_000.0) > MAX_SPEED_KMH) return Decision.REJECT

        // 精度越差、要求位移越大；两个点取较差的那个精度
        val accRef = maxOf(candidate.accuracy, p.accuracy).let { if (it <= 0f) 15f else it }
        /**
         * 「动了」的位移门槛。
         *
         * 有速度信息时按精度 × 0.75，保证步行 5 秒就能出一个点（实时）。
         * 没有速度信息时（网络定位 / 部分 ROM 不填 speed）要求 3 倍精度：
         * 这种情况下噪声没有任何旁证，放宽了整夜能凭空走掉几公里。
         */
        val need = if (speedMps == null) {
            (accRef * 3f).coerceIn(15f, 150f).toDouble()
        } else {
            (accRef * 0.75f).coerceIn(MIN_MOVE_M.toFloat(), 30f).toDouble()
        }

        // 上一次挂起的候选：超过 20 秒就不算数，否则一次很久以前的抖动会一直「授权」后续点
        val pend = pending?.takeIf { candidate.time - it.time <= 20_000L }
        val confirmed = pend != null &&
            metersBetween(pend.lat, pend.lon, candidate.lat, candidate.lon) <= need &&
            distM >= metersBetween(p.lat, p.lon, pend.lat, pend.lon)

        /**
         * 系统明确说「没在动」。
         * 这是最可靠的静止证据：站着时 GPS 会持续抖出十几米，光看坐标分不出真假，
         * 只要有速度计报 0，就要求**位移超过 2.5 倍门槛且两次连续一致**才认。
         */
        if (speedMps != null && speedMps < 0.4f) {
            if (distM < need * 2.5) return Decision.REJECT
            return if (confirmed) Decision.ACCEPT else Decision.CONFIRM
        }

        if (distM >= need) return Decision.ACCEPT
        if (distM < need * 0.5) return Decision.REJECT
        if (confirmed) return Decision.ACCEPT
        return Decision.CONFIRM
    }

    /** [decide] 的结论：采纳 / 挂起待确认 / 丢弃 */
    enum class Decision { ACCEPT, CONFIRM, REJECT }

    /** 追加一个点；超过 MAX_POINTS 时先抽稀，避免长时间记录无限增长 */
    fun appendPoint(points: List<TrackPoint>, p: TrackPoint): List<TrackPoint> {
        val next = points + p
        return if (next.size > MAX_POINTS) decimate(next) else next
    }

    /**
     * 2:1 抽稀：首尾必留，转向明显的点（形状特征）优先保留，
     * 其余隔一个留一个；极端情况（几乎全是转向点）退化为严格隔一留一。
     */
    private fun decimate(points: List<TrackPoint>): List<TrackPoint> {
        if (points.size <= MAX_POINTS) return points
        val out = ArrayList<TrackPoint>(points.size / 2 + 2)
        out.add(points.first())
        for (i in 1 until points.size - 1) {
            val keepShape = turnDegrees(points[i - 1], points[i], points[i + 1]) >= SHAPE_TURN_DEG
            if (keepShape || i % 2 == 0) out.add(points[i])
        }
        out.add(points.last())
        if (out.size <= MAX_POINTS) return out

        val thinned = ArrayList<TrackPoint>(points.size / 2 + 2)
        thinned.add(points.first())
        for (i in 1 until points.size - 1 step 2) thinned.add(points[i])
        thinned.add(points.last())
        return thinned
    }

    /** 三点转向角（度）；用等距圆柱近似，短距离下足够判断形状 */
    private fun turnDegrees(a: TrackPoint, b: TrackPoint, c: TrackPoint): Double {
        val k = cos(Math.toRadians(b.lat))
        val x1 = (b.lon - a.lon) * k
        val y1 = b.lat - a.lat
        val x2 = (c.lon - b.lon) * k
        val y2 = c.lat - b.lat
        val n1 = sqrt(x1 * x1 + y1 * y1)
        val n2 = sqrt(x2 * x2 + y2 * y2)
        if (n1 == 0.0 || n2 == 0.0) return 0.0
        val cosV = ((x1 * x2 + y1 * y2) / (n1 * n2)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosV))
    }

    /**
     * 抽样成足迹点：相邻采样间隔至少 400 米，最多 12 个，
     * 避免几百个 GPS 点把足迹地图铺满。
     */
    fun sampleForFootprints(points: List<TrackPoint>): List<TrackPoint> {
        if (points.isEmpty()) return emptyList()
        val out = mutableListOf(points.first())
        for (p in points.drop(1)) {
            val last = out.last()
            if (haversineKm(last.lat, last.lon, p.lat, p.lon) >= 0.4 && out.size < 12) {
                out.add(p)
            }
        }
        // 终点也留一个，让轨迹尾巴完整
        if (out.size < 12 && points.last() != out.last()) out.add(points.last())
        return out
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * Math.atan2(sqrt(a), sqrt(1 - a))
    }

    // ---------- WGS-84 → GCJ-02 ----------

    private fun outOfChina(lat: Double, lon: Double): Boolean =
        lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * Math.PI) + 320.0 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return ret
    }

    /** WGS-84 经纬度 → GCJ-02；境外不偏移 */
    fun wgs84ToGcj02(lat: Double, lon: Double): Pair<Double, Double> {
        if (outOfChina(lat, lon)) return lat to lon
        val dLat = transformLat(lon - 105.0, lat - 35.0)
        val dLon = transformLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * Math.PI
        var magic = sin(radLat)
        magic = 1 - GCJ_EE * magic * magic
        val sqrtMagic = sqrt(magic)
        val mgLat = lat + (dLat * 180.0) / ((GCJ_A * (1 - GCJ_EE)) / (magic * sqrtMagic) * Math.PI)
        val mgLon = lon + (dLon * 180.0) / (GCJ_A / sqrtMagic * cos(radLat) * Math.PI)
        return mgLat to mgLon
    }

    /**
     * GCJ-02 → WGS-84 近似反算（迭代法，精度 ~1 米，GPX 导出够用）。
     * 没有精确闭式解，用「正算偏移量」迭代逼近：每次把 GCJ 坐标当 WGS 再正算一次，
     * 差值趋近于 0 即收敛。
     */
    fun gcj02ToWgs84(lat: Double, lon: Double): Pair<Double, Double> {
        if (outOfChina(lat, lon)) return lat to lon
        var wgsLat = lat
        var wgsLon = lon
        repeat(5) {
            val (gLat, gLon) = wgs84ToGcj02(wgsLat, wgsLon)
            wgsLat -= (gLat - lat)
            wgsLon -= (gLon - lon)
        }
        return wgsLat to wgsLon
    }

    /**
     * 把轨迹点导出为标准 GPX 1.1 字符串。
     * 轨迹点内部存的是 GCJ-02，导出时逐点反算成 WGS-84（GPX 标准坐标），
     * 这样导入奥维互动地图 / Strava / Garmin 不会偏几百米。
     */
    fun exportGpx(points: List<TrackPoint>, name: String = "MY足迹轨迹"): String {
        if (points.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"MY足迹\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <metadata><name>").append(xmlEscape(name)).append("</name></metadata>\n")
        sb.append("  <trk>\n")
        sb.append("    <name>").append(xmlEscape(name)).append("</name>\n")
        sb.append("    <trkseg>\n")
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        for (p in points) {
            val (wLat, wLon) = gcj02ToWgs84(p.lat, p.lon)
            sb.append("      <trkpt lat=\"").append("%.7f".format(wLat))
              .append("\" lon=\"").append("%.7f".format(wLon)).append("\">\n")
            sb.append("        <time>").append(fmt.format(java.util.Date(p.time))).append("</time>\n")
            if (p.accuracy > 0f) {
                sb.append("        <hdop>").append("%.1f".format(p.accuracy / 5.0)).append("</hdop>\n")
            }
            sb.append("      </trkpt>\n")
        }
        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }

    private fun xmlEscape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;")

    /**
     * 把轨迹导出为 GPX 文件并拉起系统分享面板。
     * 可直接导入奥维互动地图 / Strava / Garmin Connect / 两步路等。
     */
    fun exportAndShare(context: Context, points: List<TrackPoint>): Boolean {
        if (points.isEmpty()) return false
        return try {
            val gpx = exportGpx(points)
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                .format(java.util.Date())
            val dir = java.io.File(context.cacheDir, "tracks").apply { mkdirs() }
            val file = java.io.File(dir, "MY足迹轨迹-$stamp.gpx")
            file.writeText(gpx, Charsets.UTF_8)

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newUri(context.contentResolver, "track", uri)
            }
            context.startActivity(
                android.content.Intent.createChooser(intent, "导出 GPX 轨迹")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

/**
 * 定位监听的进程级单例。
 *
 * AppState 由 `remember { AppState(context) }` 持有，旋转屏幕会重建出一个新实例；
 * 监听器如果挂在 AppState 的实例字段上，旧实例注册的 listener 永远不会被 remove，
 * 新旧两个 listener 会同时往同一份 SharedPreferences 写点。
 * 放在单例里保证全进程只有一个注册点：[start] 内部先 [stop] 掉旧监听。
 */
object TrackService {

    /** 采样间隔。旧值 30 秒对「实时轨迹」来说太稀，画出来是一段段直连的折线 */
    const val INTERVAL_MS = 5_000L

    /** 最小位移交给 TrackRecorder.decide 按精度判断，这里只挡住原地重复上报 */
    private const val MIN_DISTANCE_M = 3f

    /** 有这个时间内收到过 GPS 点，就忽略网络定位的粗点 */
    private const val GPS_TRUST_MS = 25_000L

    private var listener: LocationListener? = null
    private var manager: LocationManager? = null

    /** 最近一次收到 GPS 点的时间（elapsedRealtime），用于判断网络点要不要采信 */
    @Volatile
    private var lastGpsAt = 0L

    /** 最近一次收到任何点的时间（elapsedRealtime），UI 用它判断「是不是掉线了」 */
    @Volatile
    var lastFixElapsed = 0L
        private set

    /** 是否正在采点（进程内的真实状态，不受 AppState 重建影响） */
    val isRunning: Boolean get() = listener != null

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * 开始采点（GPS + 网络定位）。返回 false 表示没有定位权限或定位开关全关。
     * 内部先 stop()，因此重复调用不会出现两个 listener。
     */
    fun start(
        context: Context,
        intervalMs: Long = INTERVAL_MS,
        minDistance: Float = MIN_DISTANCE_M,
        onPoint: (Location) -> Unit
    ): Boolean {
        stop()
        val app = context.applicationContext
        if (!hasPermission(app)) return false
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        val fine = ContextCompat.checkSelfPermission(
            app, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { p ->
                if (p == LocationManager.GPS_PROVIDER && !fine) false
                else runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)
            }
        if (providers.isEmpty()) return false

        val l = LocationListener { location -> onPoint(location) }
        var registered = false
        providers.forEach { provider ->
            if (runCatching {
                    // 传 Looper：回调固定回主线程，AppState 里更新 Compose 状态才安全
                    lm.requestLocationUpdates(
                        provider, intervalMs, minDistance, l, android.os.Looper.getMainLooper()
                    )
                }.isSuccess
            ) {
                registered = true
            }
        }
        if (!registered) return false
        lastGpsAt = 0L
        lastFixElapsed = SystemClock.elapsedRealtime()
        listener = l
        manager = lm
        return true
    }

    fun stop() {
        val l = listener
        val lm = manager
        listener = null
        manager = null
        lastGpsAt = 0L
        if (l != null) runCatching { lm?.removeUpdates(l) }
    }

    /**
     * 这个点值不值得往上层送。
     *
     * GPS 与网络定位同时注册时，同一个位置会来两条（基站定位误差几百米），
     * 直接照单全收会让轨迹反复横跳、里程凭空翻倍。
     * 所以：有 GPS 点就只认 GPS；GPS 超过 25 秒没来（进隧道 / 室内）才退而用网络定位。
     */
    fun shouldDispatch(location: Location): Boolean {
        val now = SystemClock.elapsedRealtime()
        val isGps = location.provider == LocationManager.GPS_PROVIDER
        if (isGps) lastGpsAt = now
        // 注意顺序：被下面的优先级挡掉的网络点不能刷新「最近一次定位」，
        // 否则 GPS 明明掉线了，界面还显示刚刚才收到位置
        if (!isGps && now - lastGpsAt < GPS_TRUST_MS) return false
        lastFixElapsed = now
        return true
    }
}
