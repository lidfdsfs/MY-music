package com.example.my.ui.map

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sin

/**
 * 省域高亮覆盖物：把「走过」的省填成半透明蓝，边界带辉光。
 *
 * 参考图那种效果需要三层叠加，单层描边做不到：
 *
 *  1. **填充** —— 半透明蓝，压在卫星影像上能看出走过哪些省，
 *     但必须留出底图纹理（alpha 不能太高，否则山脉全糊成一块）。
 *  2. **外辉光** —— 比主描边更宽、透明度更低的浅青，模拟霓虹外溢。
 *  3. **主描边** —— 窄而亮的实线，勾出省的轮廓。
 *
 * 边界数据是 GCJ-02（见 [com.example.my.data.ProvinceBoundaryData]），
 * 与高德瓦片底图同一坐标系，所以能严丝合缝地贴在底图上。
 *
 * ## 性能：绝对不能在 draw() 里重建 Path
 *
 * 之前的实现在每帧 draw 时调用 `projection.toPixels` 重新投影每个环的每个点。
 * 全国 34 省一共 2 万多个点，拖动地图时每帧（~16ms）都要跑一遍几千次投影 +
 * 几千次 Path.lineTo —— 主线程直接被打满，掉帧到个位数，久了就是 ANR，
 * 系统弹「应用无响应」然后杀进程（用户看到的就是闪退）。
 *
 * 现在的做法：
 *
 *  - Path 用 **归一化墨卡托坐标**（0..[UNIT]）只构建一次，之后永不重建；
 *  - 每帧只用 2 次 `toPixels` 校准出「每路径单位多少像素」，做成一个
 *    [Matrix] 交给 canvas —— 拖拽、缩放全都由 GPU 那一侧的矩阵搞定；
 *  - 每个环带一个包围盒，先做屏幕相交剔除，放大后只会真正画那 1~2 个省；
 *  - 绘制前 `clipRect` 到视口，屏幕外的部分不参与光栅化。
 *
 * 归一化坐标还有一个额外好处：Path 的顶点值永远在 0..1e6 之间。
 * 之前那种「直接投影到屏幕像素」的写法，在 z=15 放大时其他省的点会落到
 * ±1e8 像素，float 尾数只剩个位，Path 形状直接崩掉（边界扭曲/整条丢失）。
 */
class ProvinceHighlightOverlay(
    /** 要高亮的省（按 adcode） */
    private val adcodes: Set<String>,
    private val resolve: (String) -> List<List<GeoPoint>>?
) : Overlay() {

    /** 填充色（半透明蓝） */
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_FILL
    }

    /** 外辉光：宽、淡 */
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = COLOR_GLOW
    }

    /** 主描边：窄、亮 */
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = COLOR_STROKE
    }

    private class Shape(val path: Path, val bounds: RectF)

    /**
     * 全部环的路径 + 包围盒。惰性构建一次，之后整个覆盖物生命周期内复用。
     * 构建成本约 2 万次三角函数，只发生在第一次绘制（毫秒级）。
     */
    private val shapes: List<Shape> by lazy { buildShapes() }

    private val matrix = Matrix()
    private val screen = RectF()
    private val tmpRect = RectF()
    private val pa = android.graphics.Point()
    private val pb = android.graphics.Point()

    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        if (shadow || canvas == null || mapView == null) return
        if (adcodes.isEmpty()) return
        val w = mapView.width
        val h = mapView.height
        if (w <= 0 || h <= 0) return

        val all = shapes
        if (all.isEmpty()) return

        val center = mapView.mapCenter
        val projection = mapView.projection

        // 用经纬度方向（线性）校准缩放比：投影 1 度经度差得到多少屏幕像素。
        // 这样不依赖 256*2^zoom 这类常数，也不会因为 osmdroid 内部改动而错位。
        projection.toPixels(GeoPoint(center.latitude, center.longitude), pa)
        projection.toPixels(GeoPoint(center.latitude, center.longitude + 1.0), pb)
        val pxPerLonDeg = abs(pb.x - pa.x).toDouble()
        if (pxPerLonDeg < 1e-9) return
        // 1 度经度 = UNIT/360 个路径单位，换算成「每路径单位多少像素」
        val scale = pxPerLonDeg * 360.0 / UNIT
        val worldSize = scale * UNIT

        // 屏幕坐标 = (归一化坐标 - 中心归一化坐标) * worldSize + 屏幕中心
        val tx = w / 2.0 - mercX(center.longitude) * worldSize
        val ty = h / 2.0 - mercY(center.latitude) * worldSize

        matrix.setScale(scale.toFloat(), scale.toFloat())
        matrix.postTranslate(tx.toFloat(), ty.toFloat())

        screen.set(0f, 0f, w.toFloat(), h.toFloat())

        val density = mapView.resources.displayMetrics.density

        canvas.save()
        // clip 必须在 concat 之前：先限定在屏幕空间，再让矩阵把路径映射进来
        canvas.clipRect(screen)
        canvas.concat(matrix)

        fillPaint.alpha = FILL_ALPHA

        // 线宽是在「路径单位」空间里生效的，要除以 scale 才能得到屏幕上的 dp
        glowPaint.strokeWidth = (GLOW_WIDTH_DP * density / scale).toFloat()
        glowPaint.alpha = GLOW_ALPHA
        strokePaint.strokeWidth = (STROKE_WIDTH_DP * density / scale).toFloat()
        strokePaint.alpha = STROKE_ALPHA

        for (s in all) {
            // 屏幕相交剔除：放大到城市级时，全国 34 省里只有 1~2 个真的可见
            tmpRect.set(s.bounds)
            matrix.mapRect(tmpRect)
            if (!RectF.intersects(tmpRect, screen)) continue

            canvas.drawPath(s.path, fillPaint)
            canvas.drawPath(s.path, glowPaint)
            canvas.drawPath(s.path, strokePaint)
        }

        canvas.restore()
    }

    /** 把地理坐标转成归一化墨卡托路径。只在第一次绘制时跑一次 */
    private fun buildShapes(): List<Shape> {
        val out = ArrayList<Shape>()
        for (code in adcodes) {
            val rings = resolve(code) ?: continue
            for (ring in rings) {
                if (ring.size < 3) continue
                val path = Path()
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE
                for (i in ring.indices) {
                    val g = ring[i]
                    val x = (mercX(g.longitude) * UNIT).toFloat()
                    val y = (mercY(g.latitude) * UNIT).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                path.close()
                out.add(Shape(path, RectF(minX, minY, maxX, maxY)))
            }
        }
        return out
    }

    /** 覆盖物集合变化时重建路径（adcodes 变了就换个实例，一般用不到） */
    fun invalidateCache() {
        // shapes 是 lazy 的 val，这里无法重置；调用方在 adcodes 变化时
        // 直接重新 new 一个 ProvinceHighlightOverlay 即可（rebuildOverlays 就是这么做的）。
    }

    private companion object {
        /** 归一化墨卡托坐标的放大倍数。1e6 在 float 精度和数值范围之间最平衡 */
        const val UNIT = 1_000_000.0

        /**
         * 填充：参考图那种高饱和的青蓝。
         * alpha 不用太低 —— 低了会跟卫星影像的山脉纹理混在一起，看不出"这块走过"；
         * 也不能太高，太高就成了一块死蓝，地形全被盖住。
         */
        const val COLOR_FILL = 0xFF17A8E8.toInt()
        const val FILL_ALPHA = 100

        /** 外辉光：浅青，模拟霓虹外溢 */
        const val COLOR_GLOW = 0xFF8CEAFF.toInt()
        const val GLOW_ALPHA = 95
        const val GLOW_WIDTH_DP = 9f

        /** 主描边：亮青，勾出省的轮廓 */
        const val COLOR_STROKE = 0xFFB4F0FF.toInt()
        const val STROKE_ALPHA = 245
        const val STROKE_WIDTH_DP = 2.4f

        /** 经度 → 归一化墨卡托 x（0..1） */
        fun mercX(lon: Double): Double = (lon + 180.0) / 360.0

        /** 纬度 → 归一化墨卡托 y（0..1，北为 0）。与 osmdroid 的 Web Mercator 一致 */
        fun mercY(lat: Double): Double {
            val s = sin(lat * PI / 180.0).coerceIn(-0.9999, 0.9999)
            return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
        }
    }
}
