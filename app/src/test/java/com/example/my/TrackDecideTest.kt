package com.example.my

import com.example.my.data.TrackPoint
import com.example.my.data.TrackRecorder
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * 轨迹过滤的数值验证。
 *
 * 没有真机也没法跑模拟器，这一层用纯 JVM 模拟把「原地站着」和「正常步行」两种典型场景
 * 各跑一小时，看过滤后的里程是否接近真实值：
 *  - 站着时里程必须压得很低（否则一夜凭空多出几公里）；
 *  - 走路时里程必须接近理论值（否则实时轨迹会丢点、变形）。
 *
 * 用固定种子的随机数保证可重复。
 */
class TrackDecideTest {

    private val lat0 = 30.0
    private val lon0 = 120.0
    /** 1 米对应的纬度增量 */
    private val mPerDegLat = 111_320.0

    private class Result(val km: Double, val points: Int)

    /**
     * @param speedMps 每步系统上报的速度；null 表示设备没给速度
     * @param moveMps 真实移动速度（米/秒）
     * @param noiseM 单次定位噪声标准差（米）
     */
    private fun simulate(
        steps: Int = 720,          // 5 秒一次 × 720 = 1 小时
        speedMps: Float?,
        moveMps: Double,
        noiseM: Double,
        accuracy: Float = 8f,
        seed: Long = 42L
    ): Result {
        val rnd = Random(seed)
        var prev: TrackPoint? = null
        var pending: TrackPoint? = null
        var accepted = 0
        var dist = 0.0
        val intervalMs = 5_000L
        var t = 1_600_000_000_000L

        repeat(steps) { i ->
            t += intervalMs
            // 真实位置：沿正北匀速移动
            val trueM = moveMps * (i + 1) * 5.0
            val lat = lat0 + trueM / mPerDegLat
            val lon = lon0
            // 观测位置：真实位置 + 高斯噪声（纬向/经向各一份）
            val noiseLat = rnd.nextGaussian() * noiseM / mPerDegLat
            val noiseLon = rnd.nextGaussian() * noiseM / mPerDegLat
            val cand = TrackPoint(lat + noiseLat, lon + noiseLon, t, accuracy)

            when (TrackRecorder.decide(prev, cand, pending, speedMps)) {
                TrackRecorder.Decision.ACCEPT -> {
                    prev?.let { dist += TrackRecorder.metersBetween(it.lat, it.lon, cand.lat, cand.lon) }
                    prev = cand
                    pending = null
                    accepted++
                }
                TrackRecorder.Decision.CONFIRM -> pending = cand
                TrackRecorder.Decision.REJECT -> Unit
            }
        }
        return Result(dist / 1000.0, accepted)
    }

    @Test
    fun `静止一小时不应凭空产生里程`() {
        val r = simulate(speedMps = 0.2f, moveMps = 0.0, noiseM = 6.0)
        println("stationary(with speed): ${r.km} km / ${r.points} pts")
        // 精度 8 米、静止一小时：真实里程 0，过滤后允许的残余误差控制在 300 米以内
        assertTrue("静止一小时却记录了 ${r.km} km", r.km < 0.3)
    }

    @Test
    fun `没有速度信息时静止也不至于失控`() {
        val r = simulate(speedMps = null, moveMps = 0.0, noiseM = 6.0)
        println("stationary(no speed): ${r.km} km / ${r.points} pts")
        assertTrue("无速度信息时里程失控：${r.km} km", r.km < 2.0)
    }

    @Test
    fun `正常步行一小时里程应接近真实值`() {
        val r = simulate(speedMps = 1.4f, moveMps = 1.4, noiseM = 3.0)
        println("walking: ${r.km} km / ${r.points} pts")
        // 1.4 m/s × 3600 s ≈ 5.04 km
        assertTrue("步行里程偏差过大：${r.km} km", r.km > 4.2 && r.km < 5.6)
        // 5 秒一个点、一小时 720 个；允许少量被灰色地带吞掉，但必须保持「实时」
        assertTrue("采样点太少，实时性不足：${r.points}", r.points > 500)
    }

    @Test
    fun `精度差于35米的点应直接丢弃`() {
        val r = simulate(speedMps = 0.2f, moveMps = 0.0, noiseM = 12.0, accuracy = 40f)
        println("bad accuracy: ${r.km} km / ${r.points} pts")
        // 室内 / 基站定位的点不可信，宁可留空白也不画假轨迹（起点一个点除外）
        assertTrue("40 米精度的点被采纳了：${r.points}", r.points <= 1)
    }

    @Test
    fun `驾车速度不应被当成瞬移丢掉`() {
        val r = simulate(steps = 720, speedMps = 22.2f, moveMps = 22.2, noiseM = 4.0)
        println("driving: ${r.km} km / ${r.points} pts")
        // 80 km/h × 1 小时 ≈ 80 km
        assertTrue("驾车里程偏差过大：${r.km} km", r.km > 70.0 && r.km < 90.0)
    }
}
