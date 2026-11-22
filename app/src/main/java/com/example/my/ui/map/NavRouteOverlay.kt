package com.example.my.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 导航风格的路线覆盖物。
 *
 * 高德 / Google 导航里那条线之所以"像条路"，靠的是四层叠加，而单个 Polyline 做不到：
 *
 *  1. **外描边（casing）** —— 比主线更宽的白色圆头描边。没有它，线压在深色卫星影像或
 *     密集路网上就是糊的；有了它，任何底图上都能抠出一条清晰的路。
 *  2. **主色实线** —— 圆角圆头，收尾不会是方角。
 *  3. **方向箭头** —— 沿线每 N dp 一枚白色小箭头，指出行进方向。这是"导航感"最关键的
 *     一笔：光有线只能看出"去过哪些点"，加了箭头才看得出"按什么顺序走的"。
 *  4. **起终点节点** —— 起点空心环、终点实心点（当前位置）。
 *
 * 为什么不用 osmdroid 的 Polyline：它只能画一条轮廓线，箭头需要在屏幕坐标系里沿线取点、
 * 按线段角度旋转，必须在 draw 里自己算。顺带还解决了 Polyline 的两个问题 ——
 * 虚线在低缩放级别会碎成点，以及长线在屏幕外坐标极大时被硬件加速丢弃。
 *
 * 所有尺寸都以 dp 声明，绘制时乘 density，保证不同屏幕密度下粗细一致。
 */
class NavRouteOverlay(
    private val points: List<GeoPoint>,
    private val color: Int,
    private val coreWidthDp: Float = 6f,
    /** 白色描边在主线两侧各加宽多少 */
    private val casingPadDp: Float = 4f,
    private val arrowSpacingDp: Float = 72f,
    private val drawArrows: Boolean = true,
    /** 是否画起点环 / 终点位置点。足迹顺序连线用不到（端点本身有 Marker），实时轨迹需要 */
    private val showEndpoints: Boolean = false,
    private val casingColor: Int = Color.WHITE,
    /** 足迹之间的“去过哪里”连线用浅色虚线，实时轨迹仍用实线 */
    private val dashed: Boolean = false,
    private val dashOnDp: Float = 6f,
    private val dashOffDp: Float = 5f,
    /** 虚线不叠白色粗描边，避免又变成一条抢眼的粗线 */
    private val drawCasing: Boolean = true
) : Overlay() {

    private val casingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = casingColor
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = Color.WHITE
        alpha = 235
    }
    private val nodeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val nodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.color = Color.WHITE
    }

    private val linePath = Path()
    private val arrowPath = Path()
    private val reusablePoint = Point()

    // 屏幕坐标缓存：每帧重建，避免每次 draw 都分配一堆 Point 对象
    private var screenX = FloatArray(0)
    private var screenY = FloatArray(0)

    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        if (shadow || canvas == null || mapView == null) return
        if (points.size < 2) return
        val w = mapView.width
        val h = mapView.height
        if (w <= 0 || h <= 0) return

        val density = mapView.resources.displayMetrics.density
        val projection = mapView.projection

        val n = projectToScreen(projection, w, h)
        if (n < 2) return

        // 1) 拼路径
        linePath.reset()
        linePath.moveTo(screenX[0], screenY[0])
        for (i in 1 until n) linePath.lineTo(screenX[i], screenY[i])

        // 2) 先描边再主色，两次都画同一条路径
        //    足迹连线走虚线时不给白色粗描边，只保留一条轻线；实时轨迹仍是实线 + casing。
        val coreWidth = coreWidthDp * density
        val effect = if (dashed) {
            DashPathEffect(floatArrayOf(dashOnDp * density, dashOffDp * density), 0f)
        } else null
        if (drawCasing) {
            casingPaint.strokeWidth = coreWidth + casingPadDp * 2f * density
            casingPaint.pathEffect = effect
            canvas.drawPath(linePath, casingPaint)
        }
        corePaint.strokeWidth = coreWidth
        corePaint.pathEffect = effect
        canvas.drawPath(linePath, corePaint)

        // 3) 方向箭头
        if (drawArrows) drawDirectionArrows(canvas, n, density, w, h)

        // 4) 起终点
        if (showEndpoints) {
            val r = 7f * density
            drawStartNode(canvas, screenX[0], screenY[0], r, density)
            drawEndNode(canvas, screenX[n - 1], screenY[n - 1], r, density)
        }
    }

    /**
     * 把经纬度投影成屏幕像素，同时做两件必须做的事：
     *
     * - **夹制**：一条跨越大半个中国的轨迹，在放大到街道级时端点像素坐标能到几十万。
     *   Android 硬件加速的 Path 在坐标过大时会被整条丢弃（线忽然消失），所以把屏幕外的点
     *   夹到 ±16000px。这个距离远在视口之外，视觉上等价于"延伸到看不见的地方"。
     * - **抽点**：相邻两个屏幕点间距小于 1.5px 的直接跳过，5000 个轨迹点不至于变成
     *   5000 段路径。最后一个点强制保留，否则线头会停在离当前位置一小截的地方。
     *
     * @return 有效点数
     */
    private fun projectToScreen(
        projection: org.osmdroid.views.Projection,
        w: Int,
        h: Int
    ): Int {
        val size = points.size
        if (screenX.size < size) {
            screenX = FloatArray(size)
            screenY = FloatArray(size)
        }
        val margin = 16000f
        val minX = -margin
        val minY = -margin
        val maxX = w + margin
        val maxY = h + margin
        val minStepSq = 1.5f * 1.5f

        var n = 0
        var lastX = 0f
        var lastY = 0f
        for (i in 0 until size) {
            projection.toPixels(points[i], reusablePoint)
            val x = reusablePoint.x.toFloat().coerceIn(minX, maxX)
            val y = reusablePoint.y.toFloat().coerceIn(minY, maxY)
            if (n > 0 && i < size - 1) {
                val dx = x - lastX
                val dy = y - lastY
                if (dx * dx + dy * dy < minStepSq) continue
            }
            screenX[n] = x
            screenY[n] = y
            lastX = x
            lastY = y
            n++
        }
        return n
    }

    private fun drawDirectionArrows(canvas: Canvas, n: Int, density: Float, w: Int, h: Int) {
        val spacing = (arrowSpacingDp * density).coerceAtLeast(24f)
        // 箭头大小跟着线宽走：线粗了箭头太小会显得秃，太大又会溢出到白色 casing 上
        val corePx = coreWidthDp * density
        val headLen = corePx * 1.25f
        val headHalf = corePx * 0.55f
        // 视口外多留一点，箭头划过屏幕边缘时才不会突然断
        val pad = spacing
        val left = -pad
        val top = -pad
        val right = w + pad
        val bottom = h + pad

        var travelled = 0f
        var nextAt = spacing * 0.55f // 起点附近也放一枚，方向一眼就能看出来
        var drawn = 0

        for (i in 1 until n) {
            val x0 = screenX[i - 1]
            val y0 = screenY[i - 1]
            val x1 = screenX[i]
            val y1 = screenY[i]
            val segLen = hypot(x1 - x0, y1 - y0)
            if (segLen <= 0.01f) continue

            while (nextAt <= travelled + segLen) {
                if (drawn >= MAX_ARROWS) return
                val t = ((nextAt - travelled) / segLen).coerceIn(0f, 1f)
                val ax = x0 + (x1 - x0) * t
                val ay = y0 + (y1 - y0) * t
                if (ax >= left && ax <= right && ay >= top && ay <= bottom) {
                    drawHead(canvas, ax, ay, atan2(y1 - y0, x1 - x0), headLen, headHalf)
                    drawn++
                }
                nextAt += spacing
            }
            travelled += segLen
        }
    }

    private fun drawHead(canvas: Canvas, x: Float, y: Float, angle: Float, len: Float, half: Float) {
        val ca = cos(angle)
        val sa = sin(angle)
        // 尖端在前，底边在后，底边两点分别沿法线左右张开
        val baseX = x - ca * len * 0.55f
        val baseY = y - sa * len * 0.55f
        arrowPath.reset()
        arrowPath.moveTo(x + ca * len, y + sa * len)
        arrowPath.lineTo(baseX + sa * half, baseY - ca * half)
        arrowPath.lineTo(baseX - sa * half, baseY + ca * half)
        arrowPath.close()
        canvas.drawPath(arrowPath, arrowPaint)
    }

    /** 起点：白底 + 主色描边（空心环），和终点实心点一眼区分 */
    private fun drawStartNode(canvas: Canvas, x: Float, y: Float, r: Float, density: Float) {
        nodeFillPaint.color = Color.WHITE
        canvas.drawCircle(x, y, r, nodeFillPaint)
        nodeStrokePaint.color = color
        nodeStrokePaint.strokeWidth = 3f * density
        canvas.drawCircle(x, y, r, nodeStrokePaint)
    }

    /** 终点（实时轨迹即当前位置）：主色实心 + 白边 + 白点 */
    private fun drawEndNode(canvas: Canvas, x: Float, y: Float, r: Float, density: Float) {
        nodeFillPaint.color = color
        canvas.drawCircle(x, y, r, nodeFillPaint)
        nodeStrokePaint.color = Color.WHITE
        nodeStrokePaint.strokeWidth = 3f * density
        canvas.drawCircle(x, y, r, nodeStrokePaint)
        nodeFillPaint.color = Color.WHITE
        canvas.drawCircle(x, y, r * 0.34f, nodeFillPaint)
    }

    private companion object {
        /** 单帧箭头上限。极低缩放级别下一条线可能横跨几千个箭头间距，全画既慢又糊成一团 */
        const val MAX_ARROWS = 220
    }
}
