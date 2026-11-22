package com.example.my.data

import android.content.Context
import org.osmdroid.util.GeoPoint

/**
 * 全国省级行政区边界数据（GCJ-02）。
 *
 * 数据来源：高德开放平台「行政区划」接口 `v3/config/district?extensions=all`，
 * 由 `tools/gen_province_boundaries.py` 抓取 → Douglas-Peucker 抽稀 → 落到
 * `assets/province_boundaries.txt`（约 389KB / 34 省 / 2402 环 / 23543 点）。
 *
 * 为什么不用本地 `china_paths.txt`：
 * 那份是 @svg-maps/china 的 SVG 路径空间（0–774 / 0–569），与 App 里
 * `ChinaMapData` 使用的经纬度线性映射（73.5–135.1 / 18.2–53.6）**不是同一套坐标系**，
 * 实测回归残差 3~5 度，直接叠到高德底图上会明显错位。
 * 高德返回的是 GCJ-02，与 App 使用的高德瓦片底图完全同一坐标系，零偏移。
 *
 * 行政表述：包含台湾省（710000）与海南省的南海诸岛（270 环），符合规范。
 *
 * 每行格式：`adcode|name|rings`
 *   rings  — 环之间 `|` 分隔，环内点 `;` 分隔，点为 `lon,lat`
 *   环内点为闭合环（首尾不重复）
 */
object ProvinceBoundaryData {

    /** 单个省级行政区的边界 */
    class Province(
        val adcode: String,
        val name: String,
        /** 全部边界环；[0] 为主轮廓（点数最多），其余为海岛等附属环 */
        val rings: List<List<GeoPoint>>
    ) {
        /** 环数量最多的那条主轮廓 */
        val mainRing: List<GeoPoint> get() = rings.firstOrNull() ?: emptyList()

        /**
         * 外接矩形 [minLon, maxLon, minLat, maxLat]。
         * 加载时算一次：否则每次判定点落在哪个省都要把该省上千个点重扫一遍。
         */
        val bbox: DoubleArray = computeBbox(rings)
    }

    private fun computeBbox(rings: List<List<GeoPoint>>): DoubleArray {
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        for (ring in rings) {
            for (g in ring) {
                if (g.latitude < minLat) minLat = g.latitude
                if (g.latitude > maxLat) maxLat = g.latitude
                if (g.longitude < minLon) minLon = g.longitude
                if (g.longitude > maxLon) maxLon = g.longitude
            }
        }
        return doubleArrayOf(minLon, maxLon, minLat, maxLat)
    }

    @Volatile
    private var loaded = false

    /**
     * adcode → 省。
     * 整体替换（而不是原地 put）是为了让主线程读到的永远是"加载完成"的那份快照，
     * 不用加锁：加载发生在后台线程，赋值是一次引用切换。
     */
    @Volatile
    private var provinces: Map<String, Province> = emptyMap()

    /**
     * 加载边界数据（进程内仅一次）。
     * 资产缺失时降级为空表，地图不崩。
     *
     * 解析 23543 个点要一两百毫秒，所以调用方在后台线程跑；@Synchronized 保证
     * 卡片版和全屏版两个 MapView 同时触发加载时不会重复解析。
     */
    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        try {
            val map = LinkedHashMap<String, Province>()
            context.assets.open("province_boundaries.txt").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val t = line.trim()
                    if (t.isEmpty()) continue
                    val first = t.indexOf('|')
                    if (first <= 0) continue
                    val second = t.indexOf('|', first + 1)
                    if (second <= first) continue
                    val adcode = t.substring(0, first)
                    val name = t.substring(first + 1, second)
                    val rings = parseRings(t.substring(second + 1))
                    if (rings.isNotEmpty()) {
                        // 主轮廓（点数最多的环）排到最前，绘制时先画大环再画小岛
                        map[adcode] = Province(adcode, name, rings.sortedByDescending { it.size })
                    }
                }
            }
            provinces = map
        } catch (e: Exception) {
            // 资产缺失 / 解析失败：地图照常显示，只是没有省域高亮
        }
        loaded = true
    }

    private fun parseRings(blob: String): List<List<GeoPoint>> {
        val out = ArrayList<List<GeoPoint>>()
        for (raw in blob.split('|')) {
            if (raw.isEmpty()) continue
            val pts = ArrayList<GeoPoint>(raw.length / 12 + 8)
            for (p in raw.split(';')) {
                if (p.isEmpty()) continue
                val comma = p.indexOf(',')
                if (comma <= 0) continue
                val lon = p.substring(0, comma).toDoubleOrNull() ?: continue
                val lat = p.substring(comma + 1).toDoubleOrNull() ?: continue
                pts.add(GeoPoint(lat, lon))
            }
            if (pts.size >= 3) out.add(pts)
        }
        return out
    }

    /** 全部省（已加载时） */
    fun all(): List<Province> = provinces.values.toList()

    fun byAdcode(adcode: String): Province? = provinces[adcode]

    /**
     * 判断一个经纬度落在哪个省。
     *
     * 用射线法（ray casting）做点在多边形内判定：对每个环做一次，
     * 主轮廓命中即认为在该省；主轮廓没命中再试附属环（海岛）。
     *
     * 性能：全国 34 省共 23543 点，逐点判定在移动端偏慢。
     * 这里先按外接矩形粗筛（绝大多数点一步就被排除），只有 bbox 相交的省
     * 才做真正的射线法，实测单次判定 < 1ms。
     *
     * @param lon GCJ-02 经度
     * @param lat GCJ-02 纬度
     * @return 命中的省；不在任何省内则返回 null
     */
    fun provinceAt(lon: Double, lat: Double): Province? {
        if (provinces.isEmpty()) return null
        for (p in provinces.values) {
            if (bboxHit(p, lon, lat) && pointInProvince(p, lon, lat)) return p
        }
        return null
    }

    /** 外接矩形粗筛 */
    private fun bboxHit(p: Province, lon: Double, lat: Double): Boolean {
        val b = p.bbox
        return lon >= b[0] && lon <= b[1] && lat >= b[2] && lat <= b[3]
    }

    /** 射线法：任一环命中即算在该省内 */
    private fun pointInProvince(p: Province, lon: Double, lat: Double): Boolean {
        for (ring in p.rings) {
            if (ring.size < 3) continue
            var inside = false
            var j = ring.size - 1
            for (i in ring.indices) {
                val yi = ring[i].latitude
                val yj = ring[j].latitude
                if ((yi > lat) != (yj > lat)) {
                    val xi = ring[i].longitude
                    val xj = ring[j].longitude
                    // 与 lat 水平线的交点 x 是否在查询点右侧
                    val xCross = xi + (lat - yi) / (yj - yi) * (xj - xi)
                    if (lon < xCross) inside = !inside
                }
                j = i
            }
            if (inside) return true
        }
        return false
    }

    /**
     * 一批经纬度各自所属的省（去重，保持查询顺序）。
     * 用于「走过哪些省」→ 高亮这些省的省域轮廓。
     */
    fun provincesOf(points: List<Pair<Double, Double>>): List<Province> {
        val out = LinkedHashMap<String, Province>()
        for ((lon, lat) in points) {
            val p = provinceAt(lon, lat) ?: continue
            out[p.adcode] = p
        }
        return out.values.toList()
    }
}
