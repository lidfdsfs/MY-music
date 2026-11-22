package com.example.my.ui.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * 地图上的一个文字标签（城市名）。
 *
 * @param point 标签锚点（城市坐标）
 * @param text 标签文字
 * @param color 文字颜色。参考图里标签颜色跟着它所属的路线走 ——
 *   绿色的线配绿色的字，紫色的线配紫色的字，一眼就能对上是哪条线上的城市。
 */
data class MapLabel(
    val point: GeoPoint,
    val text: String,
    val color: Int
)

/**
 * 城市名标签覆盖物。
 *
 * 两个关键点：
 *
 *  1. **深色描边（halo）** —— 参考图里每个地名都带一圈深色描边。
 *     压在卫星影像、蓝色省域高亮或者密集路网上，纯色文字都会糊掉，
 *     先描粗边再填色是地图标注的标准做法。
 *  2. **碰撞剔除** —— 缩到全国尺度时十几个标签会叠成一团。
 *     这里按屏幕矩形做一次简单剔除：后画的标签如果和已画的矩形相交就跳过（先画的优先）。
 */
class CityLabelOverlay(
    private val labels: List<MapLabel>,
    /** 文字相对坐标点往上抬多少（dp）。要避开下方那个水滴图钉 */
    private val offsetDp: Float = 20f
) : Overlay() {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isFakeBoldText = true
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isFakeBoldText = true
    }

    private val scratch = Point()
    private val placed = ArrayList<Rect>()

    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        if (shadow || canvas == null || mapView == null) return
        if (labels.isEmpty()) return
        val w = mapView.width
        val h = mapView.height
        if (w <= 0 || h <= 0) return

        val density = mapView.resources.displayMetrics.density
        val textSize = 12.5f * density
        textPaint.textSize = textSize
        haloPaint.textSize = textSize
        haloPaint.strokeWidth = 3f * density

        val offset = offsetDp * density
        val pad = (2f * density).toInt()
        placed.clear()

        for (label in labels) {
            mapView.projection.toPixels(label.point, scratch)
            val x = scratch.x.toFloat()
            val y = scratch.y.toFloat()
            // 视口外直接跳过（留一点余量，滑入屏幕边缘时才不会突然闪现）
            if (x < -80 || x > w + 80 || y < -80 || y > h + 80) continue

            val baseY = y - offset
            val tw = textPaint.measureText(label.text)
            val fm = textPaint.fontMetrics
            val left = (x - tw / 2f).toInt() - pad
            val top = (baseY + fm.ascent).toInt() - pad
            val right = (x + tw / 2f).toInt() + pad
            val bottom = (baseY + fm.descent).toInt() + pad
            val rect = Rect(left, top, right, bottom)

            var overlap = false
            for (r in placed) {
                if (Rect.intersects(r, rect)) {
                    overlap = true
                    break
                }
            }
            if (overlap) continue
            placed.add(rect)

            // 先描边后填色：描边在下，填充压在上面，文字边缘才有"发光"的干净感
            haloPaint.color = HALO_COLOR
            canvas.drawText(label.text, x - tw / 2f, baseY, haloPaint)
            textPaint.color = label.color
            canvas.drawText(label.text, x - tw / 2f, baseY, textPaint)
        }
    }

    private companion object {
        /** 深色描边（近黑蓝）：任何底图上都能把字托出来 */
        const val HALO_COLOR = 0xE6121F33.toInt()
    }
}
