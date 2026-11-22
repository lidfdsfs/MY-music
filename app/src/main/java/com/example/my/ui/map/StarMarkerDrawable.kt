package com.example.my.ui.map

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 金色五角星标记。
 *
 * 参考图里「特别地点」用的是一颗金星（昆明位置那颗）。这里用 Canvas 直接画，
 * 不用 PNG 资源：矢量绘制在任意屏幕密度下都锐利，而且颜色能随时换
 * （金星 / 银星 / 蓝星），不用再往 res 里塞图。
 *
 * 图标带一圈白色柔光：参考图里那颗星压在蓝色省域高亮上，纯金色会和底色糊在一起。
 */
class StarMarkerDrawable(
    /** 星体颜色 */
    private val starColor: Int = 0xFFFFD54A.toInt(),
    /** 描边颜色（深金） */
    private val edgeColor: Int = 0xFF7A4E00.toInt(),
    /** 图标边长（px）。调用方按 density 换算 */
    private val sizePx: Int = 48
) : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x66FFFFFF
    }
    private val path = Path()

    /** osmdroid 画 Marker 图标前会调 setAlpha，这里记下来在绘制时用 */
    private var alphaValue = 255

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val outer = min(b.width(), b.height()) * 0.38f
        val inner = outer * 0.44f

        // 十点星：外角 5 个（i 为偶数），内角 5 个（i 为奇数），起始角朝正上方
        path.reset()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val ang = -Math.PI / 2.0 + i * Math.PI / 5.0
            val x = cx + (r * cos(ang)).toFloat()
            val y = cy + (r * sin(ang)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        // 白色柔光：压在蓝色省域高亮上也看得清
        glowPaint.color = 0x66FFFFFF
        glowPaint.alpha = (alphaValue * 0.4f).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, outer * 1.25f, glowPaint)

        fillPaint.color = starColor
        fillPaint.alpha = alphaValue
        canvas.drawPath(path, fillPaint)

        edgePaint.color = edgeColor
        edgePaint.alpha = alphaValue
        edgePaint.strokeWidth = maxOf(1f, outer * 0.14f)
        canvas.drawPath(path, edgePaint)
    }

    override fun getIntrinsicWidth(): Int = sizePx

    override fun getIntrinsicHeight(): Int = sizePx

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
