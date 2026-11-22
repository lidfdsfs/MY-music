package com.example.my.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import java.util.concurrent.atomic.AtomicReference

/**
 * 一次定位结果。
 *
 * 原始值是系统给的 WGS-84；[lat] / [lon] 已经转成 GCJ-02，
 * 可以直接拿去和高德瓦片、AmapApi 的检索结果对齐（这是本项目所有坐标的统一口径）。
 */
data class GeoFix(
    val wgsLat: Double,
    val wgsLon: Double,
    /** 水平精度半径（米）。系统没给精度时为 999，调用方据此判断可信度 */
    val accuracyM: Float,
    val time: Long,
    val provider: String,
    /** 瞬时速度（米/秒）。系统没给速度时是 0，驾驶页据此显示真实车速，绝不自己编一个数 */
    val speedMps: Float = 0f,
    /** 行进方向（度，正北 0）。0 表示未知，导航锥就不画朝向 */
    val bearingDeg: Float = 0f
) {
    private val gcj: Pair<Double, Double> by lazy {
        TrackRecorder.wgs84ToGcj02(wgsLat, wgsLon)
    }
    val lat: Double get() = gcj.first
    val lon: Double get() = gcj.second

    /** 是否是「足够可信」的定位：≤35 米才算室外可用精度 */
    val reliable: Boolean get() = accuracyM <= 35f

    companion object {
        const val UNKNOWN_ACCURACY = 999f
    }
}

/**
 * 统一的定位入口。
 *
 * 旧实现只读 `getLastKnownLocation`，有两个硬伤：
 *  1. 缓存点可能是几小时前、甚至几百公里外（关机前的位置、另一个城市）；
 *  2. 各 provider 之间只按「时间最新」挑，会挑到 GPS 几分钟前的点和网络定位刚给的
 *     一千米精度点里的后者，位置反而更差。
 *
 * 这里按「精度为主、新鲜度为辅」打分，缓存不够用时主动开一次实时定位，
 * 拿到目标精度立即返回，拿不到就等到超时再把当前最好的交出去。
 */
object Locator {

    /** 定位失败原因：没有定位权限 */
    const val REASON_PERMISSION = "permission"

    /** 定位失败原因：系统定位开关（GPS / 位置信息）关掉了 */
    const val REASON_SERVICE_OFF = "service_off"

    /** 定位失败原因：等不到有效定位（室内 / 隧道 / 冷启动搜星慢） */
    const val REASON_TIMEOUT = "timeout"

    /** 缓存点默认最多认 90 秒，超过就当没有 */
    private const val FRESH_MS = 90_000L

    /** 达到这个精度就不再等，直接返回 */
    private const val GOOD_ACCURACY_M = 25f

    /** 主动定位的默认超时 */
    private const val DEFAULT_TIMEOUT_MS = 15_000L

    /** provider 优先级：同样精度下 GPS 更可信（网络定位的坐标经常被基站网格量化） */
    private fun providerRank(provider: String?): Int = when (provider) {
        LocationManager.GPS_PROVIDER -> 0
        LocationManager.NETWORK_PROVIDER -> 1
        LocationManager.PASSIVE_PROVIDER -> 2
        else -> 3
    }

    fun hasFine(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    fun hasAny(context: Context): Boolean = hasFine(context) || ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun manager(context: Context): LocationManager? =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private fun enabled(lm: LocationManager, provider: String): Boolean =
        runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)

    /** 系统「位置信息」总开关。关着时任何 provider 都不会给点，必须单独提示 */
    fun isLocationOn(context: Context): Boolean {
        val lm = manager(context) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val on = runCatching { lm.isLocationEnabled }.getOrNull()
            if (on != null) return on
        }
        return enabled(lm, LocationManager.GPS_PROVIDER) ||
            enabled(lm, LocationManager.NETWORK_PROVIDER)
    }

    /**
     * 能不能定位。返回 null 表示可以，否则是 [REASON_PERMISSION] / [REASON_SERVICE_OFF]。
     * 调用方据此给出不同的提示，而不是一律「定位失败」。
     */
    fun problem(context: Context): String? = when {
        !hasAny(context) -> REASON_PERMISSION
        !isLocationOn(context) -> REASON_SERVICE_OFF
        else -> null
    }

    /** 当前可用的 provider，按优先级排序（没有 FINE 权限时 GPS 一律排除） */
    private fun activeProviders(context: Context, lm: LocationManager): List<String> {
        val fine = hasFine(context)
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { p ->
                if (p == LocationManager.GPS_PROVIDER && !fine) false else enabled(lm, p)
            }
    }

    /**
     * 缓存里最可信的位置。
     * 打分 = 精度（米）+ 每旧 10 秒加 1 米 + provider 优先级加成，
     * 也就是「宁可要 30 秒前 ±8 米的 GPS，也不要刚刚 ±800 米的基站定位」。
     */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context, maxAgeMs: Long = FRESH_MS): GeoFix? {
        if (!hasAny(context)) return null
        val lm = manager(context) ?: return null
        val now = System.currentTimeMillis()
        var best: GeoFix? = null
        var bestScore = Double.MAX_VALUE
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).forEach { provider ->
            val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: return@forEach
            val fix = loc.toFix() ?: return@forEach
            val age = (now - fix.time).coerceAtLeast(0L)
            // 未来时间戳（系统时钟被改过）或过期太久的点都不可信
            if (age > maxAgeMs) return@forEach
            val score = fix.accuracyM + age / 100.0 + providerRank(fix.provider) * 30.0
            if (score < bestScore) {
                bestScore = score
                best = fix
            }
        }
        return best
    }

    /**
     * 取一次实时定位（挂起函数）。
     *
     * 先用足够新鲜的缓存点，否则同时向 GPS 与网络定位要更新：
     * 拿到 ≤[goodAccuracyM] 的立刻返回，否则等到超时把精度最好的那个交出去。
     * 超时也拿不到时，最后再退一步用 5 分钟内的缓存点。
     */
    @SuppressLint("MissingPermission")
    suspend fun singleFix(
        context: Context,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        goodAccuracyM: Float = GOOD_ACCURACY_M
    ): GeoFix? {
        if (problem(context) != null) return null
        // 15 秒内的缓存直接可用，省掉一次搜星
        lastKnown(context, 15_000)?.let { if (it.accuracyM <= goodAccuracyM) return it }

        val app = context.applicationContext
        val lm = manager(app) ?: return lastKnown(context)
        val providers = activeProviders(app, lm)
        if (providers.isEmpty()) return lastKnown(context)

        val done = CompletableDeferred<GeoFix>()
        val best = AtomicReference<GeoFix?>(null)
        val listener = LocationListener { loc ->
            val fix = loc.toFix() ?: return@LocationListener
            val cur = best.get()
            if (cur == null || fix.accuracyM < cur.accuracyM) best.set(fix)
            if (fix.accuracyM <= goodAccuracyM && !done.isCompleted) done.complete(fix)
        }
        // 0 间隔取最快的第一帧：冷启动搜星第一帧往往只是「知道在哪」，精度靠后续收敛
        val registered = providers.count { p ->
            runCatching {
                lm.requestLocationUpdates(p, 600L, 0f, listener, Looper.getMainLooper())
            }.isSuccess
        }
        if (registered == 0) return lastKnown(context)

        val hit = withTimeoutOrNull(timeoutMs) { done.await() }
        runCatching { lm.removeUpdates(listener) }
        return hit ?: best.get() ?: lastKnown(context, 5 * 60_000)
    }

    /**
     * 持续观察位置（给「我的位置」蓝点用）。
     * 返回停止函数；调用方离开页面必须调用，否则定位器会一直开着耗电。
     *
     * [onFix] 在主线程回调，只给「有意义的更新」：
     *  - 有 GPS 信号时，粗糙的网络定位点直接丢弃（它会把蓝点甩到几条街外）；
     *  - 位移小于 [minMeters] 的重复上报不回调。
     */
    @SuppressLint("MissingPermission")
    fun observe(
        context: Context,
        intervalMs: Long = 4_000L,
        minMeters: Float = 3f,
        onFix: (GeoFix) -> Unit
    ): () -> Unit {
        val app = context.applicationContext
        if (problem(app) != null) return {}
        val lm = manager(app) ?: return {}
        val providers = activeProviders(app, lm)
        if (providers.isEmpty()) return {}

        // 先把还热乎的缓存点抛出去，UI 不用干等第一帧
        lastKnown(app, 30_000)?.let { onFix(it) }

        var lastGpsElapsed = 0L
        var last: GeoFix? = null
        val listener = LocationListener { loc ->
            val fix = loc.toFix() ?: return@LocationListener
            val nowElapsed = SystemClock.elapsedRealtime()
            if (fix.provider == LocationManager.GPS_PROVIDER) {
                lastGpsElapsed = nowElapsed
            } else if (nowElapsed - lastGpsElapsed < 20_000L && fix.accuracyM > 50f) {
                // 刚有 GPS 点，网络定位的粗点不要来捣乱
                return@LocationListener
            }
            val prev = last
            if (prev != null) {
                val moved = TrackRecorder.metersBetween(prev.wgsLat, prev.wgsLon, fix.wgsLat, fix.wgsLon)
                if (moved < minMeters && fix.accuracyM >= prev.accuracyM) return@LocationListener
            }
            last = fix
            onFix(fix)
        }
        val registered = providers.count { p ->
            runCatching {
                lm.requestLocationUpdates(p, intervalMs, minMeters, listener, Looper.getMainLooper())
            }.isSuccess
        }
        if (registered == 0) return {}
        return { runCatching { lm.removeUpdates(listener) } }
    }

    /** 系统 Location → GeoFix。坐标 (0,0) 与明显非法的值一律当无效点 */
    private fun Location.toFix(): GeoFix? {
        if (latitude.isNaN() || longitude.isNaN()) return null
        if (abs(latitude) < 0.0001 && abs(longitude) < 0.0001) return null
        if (latitude < -90.0 || latitude > 90.0) return null
        if (longitude < -180.0 || longitude > 180.0) return null
        val acc = if (hasAccuracy() && accuracy > 0f) accuracy else GeoFix.UNKNOWN_ACCURACY
        val t = if (time > 0L) time else System.currentTimeMillis()
        return GeoFix(
            latitude, longitude, acc, t, provider ?: "unknown",
            speedMps = if (hasSpeed()) speed.coerceAtLeast(0f) else 0f,
            bearingDeg = if (hasBearing()) bearing else 0f
        )
    }
}
