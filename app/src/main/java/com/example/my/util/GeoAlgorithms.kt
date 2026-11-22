package com.example.my.util

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 通用地理坐标点（label 可传城市名等业务标签） */
data class GeoPoint(val lat: Double, val lon: Double, val label: String = "")

/** 球面距离（Haversine），单位 km */
fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val s = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(s.coerceIn(0.0, 1.0)))
}

/**
 * 标准 DBSCAN 密度聚类。
 * @param epsKm 邻域半径（km）
 * @param minPts 核心点最小邻居数（含自身）
 * @return 簇列表；噪声点被忽略
 */
fun dbscanGeo(points: List<GeoPoint>, epsKm: Double = 30.0, minPts: Int = 2): List<List<GeoPoint>> {
    val n = points.size
    if (n == 0) return emptyList()
    val visited = BooleanArray(n)
    val assigned = BooleanArray(n)
    val clusters = mutableListOf<List<GeoPoint>>()

    fun regionQuery(i: Int): List<Int> =
        (0 until n).filter { haversineKm(points[i], points[it]) <= epsKm }

    for (i in 0 until n) {
        if (visited[i]) continue
        visited[i] = true
        val neighbors = regionQuery(i).toMutableList()
        if (neighbors.size < minPts) continue // 噪声
        val cluster = mutableListOf<Int>()
        cluster.add(i)
        assigned[i] = true
        var k = 0
        while (k < neighbors.size) {
            val j = neighbors[k]
            if (!visited[j]) {
                visited[j] = true
                val nb2 = regionQuery(j)
                if (nb2.size >= minPts) {
                    nb2.forEach { if (!neighbors.contains(it)) neighbors.add(it) }
                }
            }
            if (!assigned[j]) {
                assigned[j] = true
                cluster.add(j)
            }
            k++
        }
        clusters.add(cluster.map { points[it] })
    }
    return clusters
}

/**
 * 聚类摘要：每个簇取簇内出现次数最多的 label（城市名）+ 点数，按点数降序。
 */
fun clusterSummary(points: List<GeoPoint>): List<Pair<String, Int>> =
    dbscanGeo(points).map { cluster ->
        val city = cluster
            .groupingBy { it.label.ifBlank { "未知" } }
            .eachCount()
            .maxByOrNull { it.value }?.key ?: "未知"
        city to cluster.size
    }.sortedByDescending { it.second }

/**
 * TSP 顺路排序（开放路径）：最近邻构造初始解 + 2-opt 迭代改进（最多 200 轮）。
 * 点数 < 3 时直接返回原列表。
 *
 * 起点会挨个试：最近邻对起点非常敏感，只从 0 号点出发经常陷进局部最优。
 * 实测大理/成都/重庆/西安这 4 个点，从 0 号点出发只能排出 1855km，
 * 而最优解是 1514km —— 差别就在"从大理出发"这一下。
 * 点数超过 [MULTI_START_MAX] 时只从 0 号点出发，避免多起点带来的 O(n³) 开销。
 */
fun tspOrder(points: List<GeoPoint>): List<GeoPoint> {
    val n = points.size
    if (n < 3) return points
    val dist = Array(n) { i -> DoubleArray(n) { j -> haversineKm(points[i], points[j]) } }

    var bestRoute = IntArray(n) { it }
    var bestLen = Double.MAX_VALUE
    val starts = if (n <= MULTI_START_MAX) (0 until n) else (0 until 1)
    for (start in starts) {
        val route = nearestNeighbor(dist, n, start)
        val len = twoOpt(route, dist, n)
        if (len < bestLen - 1e-9) {
            bestLen = len
            bestRoute = route
        }
    }
    return bestRoute.map { points[it] }
}

/** 最近邻初始解 */
private fun nearestNeighbor(dist: Array<DoubleArray>, n: Int, start: Int): IntArray {
    val route = IntArray(n)
    val used = BooleanArray(n)
    var count = 1
    route[0] = start
    used[start] = true
    while (count < n) {
        val last = route[count - 1]
        var best = -1
        var bestDist = Double.MAX_VALUE
        for (j in 0 until n) {
            if (!used[j] && dist[last][j] < bestDist) {
                bestDist = dist[last][j]
                best = j
            }
        }
        route[count++] = best
        used[best] = true
    }
    return route
}

/** 2-opt 改进（开放路径：不回到起点）。返回改进后的路径长度 */
private fun twoOpt(route: IntArray, dist: Array<DoubleArray>, n: Int): Double {
    var improved = true
    var rounds = 0
    while (improved && rounds < 200) {
        improved = false
        rounds++
        for (i in 0 until n - 1) {
            for (j in i + 1 until n) {
                val a = route[i]
                val b = route[i + 1]
                val c = route[j]
                val d = if (j + 1 < n) route[j + 1] else -1
                val oldLen = dist[a][b] + (if (d >= 0) dist[c][d] else 0.0)
                val newLen = dist[a][c] + (if (d >= 0) dist[b][d] else 0.0)
                if (newLen < oldLen - 1e-9) {
                    // 反转 route[i+1..j]
                    var lo = i + 1
                    var hi = j
                    while (lo < hi) {
                        val tmp = route[lo]
                        route[lo] = route[hi]
                        route[hi] = tmp
                        lo++
                        hi--
                    }
                    improved = true
                }
            }
        }
    }
    var len = 0.0
    for (i in 0 until n - 1) len += dist[route[i]][route[i + 1]]
    return len
}

/** 多起点 TSP 的点数上限。超过这个数量只从 0 号点出发（多起点是 O(n³)） */
private const val MULTI_START_MAX = 32

/**
 * 单链聚类（single-linkage）：任意两点距离在 epsKm 以内就归为同一簇，
 * 簇之间靠"链"连通 —— A-B 近、B-C 近，即使 A-C 远，A 和 C 也会落进同一簇。
 *
 * 为什么不用 DBSCAN：DBSCAN 是标准密度聚类，半径小（默认 30km），
 * 城市级数据里 12 个点会全部变成噪声，一个簇都聚不出来。
 * 这里要的是"哪几个城市算同一趟行程"，量级是几百公里。
 *
 * 为什么不用贪心簇心（按输入顺序找最近簇）：结果依赖输入顺序。
 * 同样的城市集合换个顺序就会被拆成不同的簇，地图上线的颜色会跟着数据加载顺序变。
 * 单链只看两点距离，与顺序无关，结果稳定可复现。
 *
 * 链式合并在这里是优点不是缺点：一趟行程本来就是串起来的，
 * 大理→成都→西安 这种"每段都不远、整体很长"的路线正好应该归一簇。
 *
 * 复杂度 O(n²)，在后台线程跑。
 *
 * @param epsKm 判定同簇的最大距离（km）。700km 左右能覆盖"跨省的一趟长途"，
 *   又不会把上海和广州并到一起。
 */
fun singleLinkGroups(points: List<GeoPoint>, epsKm: Double = 700.0): List<List<GeoPoint>> {
    val n = points.size
    if (n == 0) return emptyList()
    val parent = IntArray(n) { it }

    fun find(x: Int): Int {
        var root = x
        while (parent[root] != root) root = parent[root]
        // 路径压缩：find 走过的节点直接挂到根上
        var cur = x
        while (parent[cur] != cur) {
            val next = parent[cur]
            parent[cur] = root
            cur = next
        }
        return root
    }

    fun union(a: Int, b: Int) {
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) parent[ra] = rb
    }

    for (i in 0 until n) {
        for (j in i + 1 until n) {
            if (haversineKm(points[i], points[j]) <= epsKm) union(i, j)
        }
    }

    val out = LinkedHashMap<Int, MutableList<GeoPoint>>()
    for (i in 0 until n) {
        out.getOrPut(find(i)) { ArrayList() }.add(points[i])
    }
    return out.values.toList()
}

/**
 * Douglas-Peucker 轨迹抽稀：保留形状关键拐点，丢弃近似共线的冗余点。
 * @param toleranceKm 距离阈值（km），越大保留点越少；0.5km 适合城市级轨迹
 */
fun douglasPeucker(points: List<GeoPoint>, toleranceKm: Double = 0.5): List<GeoPoint> {
    if (points.size < 3) return points
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true
    val stack = ArrayList<Pair<Int, Int>>()
    stack.add(0 to points.lastIndex)
    while (stack.isNotEmpty()) {
        val (start, end) = stack.removeAt(stack.lastIndex)
        if (end <= start + 1) continue
        var maxDist = 0.0
        var idx = -1
        for (i in start + 1 until end) {
            val d = pointToSegmentKm(points[i], points[start], points[end])
            if (d > maxDist) {
                maxDist = d
                idx = i
            }
        }
        if (maxDist > toleranceKm && idx >= 0) {
            keep[idx] = true
            stack.add(start to idx)
            stack.add(idx to end)
        }
    }
    return points.filterIndexed { i, _ -> keep[i] }
}

/** 点到线段的近似距离（等距圆柱投影，误差在公里级抽稀场景下可忽略） */
private fun pointToSegmentKm(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
    val latRef = Math.toRadians((a.lat + b.lat) / 2.0)
    val kx = 111.32 * cos(latRef)
    val ky = 110.57
    val x0 = a.lon * kx; val y0 = a.lat * ky
    val x1 = b.lon * kx; val y1 = b.lat * ky
    val xp = p.lon * kx; val yp = p.lat * ky
    val dx = x1 - x0
    val dy = y1 - y0
    val lenSq = dx * dx + dy * dy
    val t = if (lenSq <= 1e-12) 0.0 else (((xp - x0) * dx + (yp - y0) * dy) / lenSq).coerceIn(0.0, 1.0)
    val cx = x0 + t * dx
    val cy = y0 + t * dy
    return sqrt((xp - cx).pow(2) + (yp - cy).pow(2))
}

/**
 * KDE 高斯核密度权重：每个点与所有点（含自身）的核值求和后，min-max 归一化到 0..1。
 */
fun kdeWeights(points: List<GeoPoint>, bandwidthKm: Double = 50.0): List<Double> {
    if (points.isEmpty()) return emptyList()
    val h = if (bandwidthKm > 0) bandwidthKm else 50.0
    val raw = points.map { p ->
        points.sumOf { q ->
            val d = haversineKm(p, q)
            exp(-(d * d) / (2 * h * h))
        }
    }
    val min = raw.min()
    val max = raw.max()
    val range = max - min
    return if (range <= 1e-12) List(points.size) { 1.0 }
    else raw.map { (it - min) / range }
}
