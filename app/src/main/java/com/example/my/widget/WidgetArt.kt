package com.example.my.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.content.ContextCompat
import com.example.my.R
import kotlin.math.max
import kotlin.math.min

/**
 * 小组件图形工厂。
 *
 * RemoteViews 只能摆系统 View，画不了圆环 / 柱状图 / 中国省界地图 / 黑胶唱片，
 * 所以这些一律在进程内用 Canvas 生成小尺寸 Bitmap，再 `setImageViewBitmap`。
 * 尺寸控制在 260px 以内（单张 < 250KB），避免 Binder 事务过大；文字仍用 TextView，
 * 这样中英双语与主题色都能在刷新时直接改，不用重新绘图。
 */
object WidgetArt {

    private const val LON_MIN = 97.0
    private const val LON_MAX = 126.0
    private const val LAT_MIN = 19.0
    private const val LAT_MAX = 44.0

    private fun bitmap(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    private fun aa(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    /** 系统 vector → 带色 Bitmap；RemoteViews 直接 setImageViewResource(vector) 在部分 ROM 会崩 */
    fun vector(context: Context, resId: Int, size: Int, tint: Int): Bitmap {
        val b = bitmap(size, size)
        val c = Canvas(b)
        val d = runCatching { ContextCompat.getDrawable(context, resId)?.mutate() }.getOrNull()
        if (d != null) {
            d.setTint(tint)
            d.setBounds(0, 0, size, size)
            d.draw(c)
        }
        return b
    }

    /** 今日足迹：灰底环 + 分段赤陶橙进度弧（中心文字由 TextView 叠上去） */
    fun ring(percent: Int, c: WidgetKit.C): Bitmap {
        val s = 220
        val b = bitmap(s, s)
        val cv = Canvas(b)
        val cx = s / 2f
        val cy = s / 2f
        val r = s * 0.39f
        val width = s * 0.085f

        val base = aa(c.track).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
        }
        cv.drawCircle(cx, cy, r, base)

        val p = percent.coerceIn(0, 100)
        if (p > 0) {
            val total = p * 3.6f
            // 三段 + 两个小缺口，还原设计稿里「橙-灰-橙」的分段圆环
            val gap = 7f
            val seg = (total - gap * 2).coerceAtLeast(1f) / 3f
            val accent = aa(c.accent).apply {
                style = Paint.Style.STROKE
                strokeWidth = width
                strokeCap = Paint.Cap.ROUND
            }
            var start = -90f
            repeat(3) {
                cv.drawArc(
                    RectF(cx - r, cy - r, cx + r, cy + r),
                    start, seg, false, accent
                )
                start += seg + gap
            }
        }
        return b
    }

    /** 本月足迹：虚线网格 + 6 根圆角柱，最后一根（本月）用品牌橙 */
    fun bars(values: List<Int>, c: WidgetKit.C): Bitmap {
        val w = 260
        val h = 150
        val b = bitmap(w, h)
        val cv = Canvas(b)

        val grid = aa(c.grid).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
            pathEffect = DashPathEffect(floatArrayOf(6f, 8f), 0f)
        }
        listOf(0.22f, 0.52f, 0.82f).forEach { f ->
            cv.drawLine(8f, h * f, w - 8f, h * f, grid)
        }

        val peak = max(values.maxOrNull() ?: 1, 1).toFloat()
        val count = values.size.coerceAtLeast(1)
        val slot = w.toFloat() / count
        val barW = slot * 0.52f
        val bottom = h - 16f
        val maxH = h - 40f
        values.forEachIndexed { i, v ->
            val bh = max(12f, (v / peak) * maxH)
            val left = i * slot + (slot - barW) / 2f
            val rect = RectF(left, bottom - bh, left + barW, bottom)
            val color = if (i == count - 1) c.accentSoft else c.track
            cv.drawRoundRect(rect, barW / 2f, barW / 2f, aa(color))
        }
        return b
    }

    /** 天气卡下方 2×3 淡灰方格：近 6 个月足迹数 */
    fun tiles(labels: List<String>, values: List<Int>, c: WidgetKit.C): Bitmap {
        val w = 260
        val h = 216
        val b = bitmap(w, h)
        val cv = Canvas(b)
        val cols = 2
        val rows = 3
        val gap = 12f
        val tileW = (w - gap * (cols + 1)) / cols
        val tileH = (h - gap * (rows + 1)) / rows

        val tilePaint = aa(c.tile)
        val labelPaint = aa(c.muted).apply {
            textSize = 15f
            textAlign = Paint.Align.CENTER
            alpha = 150
        }
        val numPaint = aa(c.muted).apply {
            textSize = 20f
            textAlign = Paint.Align.CENTER
            alpha = 135
        }
        for (i in 0 until min(6, labels.size)) {
            val col = i % cols
            val row = i / cols
            val left = gap + col * (tileW + gap)
            val top = gap + row * (tileH + gap)
            cv.drawRoundRect(
                RectF(left, top, left + tileW, top + tileH),
                18f, 18f, tilePaint
            )
            val cx = left + tileW / 2f
            cv.drawText(labels[i], cx, top + tileH * 0.36f, labelPaint)
            cv.drawText(values.getOrElse(i) { 0 }.toString(), cx, top + tileH * 0.74f, numPaint)
        }
        return b
    }

    /** 天气卡右上角的太阳：黄色圆盘 + 12 道橙色射线 */
    fun sun(c: WidgetKit.C): Bitmap {
        val s = 140
        val b = bitmap(s, s)
        val cv = Canvas(b)
        val cx = s / 2f
        val ray = aa(0xFFFFB52E.toInt()).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until 12) {
            val a = Math.toRadians((i * 30).toDouble())
            val r1 = s * 0.33f
            val r2 = s * 0.44f
            cv.drawLine(
                cx + (r1 * Math.cos(a)).toFloat(), cx + (r1 * Math.sin(a)).toFloat(),
                cx + (r2 * Math.cos(a)).toFloat(), cx + (r2 * Math.sin(a)).toFloat(), ray
            )
        }
        cv.drawCircle(cx, cx, s * 0.27f, aa(0xFFFFC93C.toInt()))
        return b
    }

    /** 已点亮城市：真实省界 + 足迹坐标点位（裁剪到中东部，接近设计稿的局部地图） */
    fun map(context: Context, points: List<Pair<Double, Double>>, c: WidgetKit.C): Bitmap {
        val w = 260
        val h = 226
        val b = bitmap(w, h)
        val cv = Canvas(b)
        cv.drawColor(c.water)

        val fill = aa(c.land)
        val line = aa(c.landLine).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.1f
        }
        val road = aa(0x66FFFFFF).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }

        runCatching {
            context.assets.open("province_boundaries.txt").bufferedReader().useLines { lines ->
                for (raw in lines) {
                    val parts = raw.split('|')
                    if (parts.size < 3) continue
                    for (seg in parts.drop(2)) {
                        val path = Path()
                        var started = false
                        for (pair in seg.split(';')) {
                            val ll = pair.split(',')
                            if (ll.size < 2) continue
                            val lon = ll[0].toDoubleOrNull() ?: continue
                            val lat = ll[1].toDoubleOrNull() ?: continue
                            val x = ((lon - LON_MIN) / (LON_MAX - LON_MIN) * w).toFloat()
                            val y = ((LAT_MAX - lat) / (LAT_MAX - LAT_MIN) * h).toFloat()
                            if (!started) {
                                path.moveTo(x, y)
                                started = true
                            } else path.lineTo(x, y)
                        }
                        if (started) {
                            path.close()
                            cv.drawPath(path, fill)
                            cv.drawPath(path, line)
                            cv.drawPath(path, road)
                        }
                    }
                }
            }
        }

        // 没有足迹时用一组示意点，避免空地图显得像坏了
        val pts = points.ifEmpty {
            listOf(
                30.6 to 114.3, 29.6 to 106.5, 25.0 to 102.7,
                31.2 to 121.5, 23.1 to 113.3, 30.3 to 120.2,
                26.6 to 106.7, 34.3 to 108.9, 22.5 to 114.1
            )
        }
        val white = aa(0xFFFFFFFF.toInt())
        val dot = aa(c.accentSoft)
        pts.forEach { (lat, lon) ->
            val x = ((lon - LON_MIN) / (LON_MAX - LON_MIN) * w).toFloat()
            val y = ((LAT_MAX - lat) / (LAT_MAX - LAT_MIN) * h).toFloat()
            if (x < -10f || x > w + 10f || y < -10f || y > h + 10f) return@forEach
            cv.drawCircle(x, y, 6.5f, white)
            cv.drawCircle(x, y, 4.5f, dot)
        }
        return b
    }

    /** 音乐卡：黑胶唱片 + 中间圆形封面 */
    fun vinyl(context: Context, cover: Bitmap?, c: WidgetKit.C): Bitmap {
        val s = 220
        val b = bitmap(s, s)
        val cv = Canvas(b)
        val cx = s / 2f
        val r = s * 0.46f

        cv.drawCircle(cx, cx, r, aa(0xFF1A191C.toInt()))
        val groove = aa(0xFFFFFFFF.toInt()).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            alpha = 26
        }
        for (i in 1..7) {
            cv.drawCircle(cx, cx, r * (0.48f + i * 0.065f), groove)
        }

        val src = cover ?: runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.music_cover_default)
        }.getOrNull()
        if (src != null) {
            val inner = r * 0.56f
            val size = (inner * 2).toInt().coerceAtLeast(8)
            val scaled = Bitmap.createScaledBitmap(src, size, size, true)
            val m = Matrix().apply {
                setTranslate(cx - size / 2f, cx - size / 2f)
            }
            val shader = android.graphics.BitmapShader(
                scaled,
                android.graphics.Shader.TileMode.CLAMP,
                android.graphics.Shader.TileMode.CLAMP
            ).apply { setLocalMatrix(m) }
            val shaderPaint = aa(0xFFFFFFFF.toInt()).apply { this.shader = shader }
            cv.drawCircle(cx, cx, inner, shaderPaint)
        }
        cv.drawCircle(cx, cx, 4f, aa(0xFFFDFBF9.toInt()))
        return b
    }

    /**
     * 播放 / 暂停合一按钮的形变帧。
     *
     * `phase` 0→1 表示「点击前的状态」过渡到「点击后的状态」：旧图标横向收拢淡出、
     * 新图标横向展开淡入，同时整枚按钮先缩到 0.8 再弹性放大到 1.08 回弹到 1.0，
     * 形成主流播放器那种按下即形变的动效。RemoteViews 不支持属性动画，所以由
     * MusicWidgetProvider 用 partiallyUpdateAppWidget 逐帧推送这一组小位图。
     */
    fun playPause(playing: Boolean, phase: Float, c: WidgetKit.C): Bitmap {
        val s = 128
        val b = bitmap(s, s)
        val cv = Canvas(b)
        val cx = s / 2f
        val t = phase.coerceIn(0f, 1f)
        val ease = 1f - (1f - t) * (1f - t)
        var scale = 0.80f + 0.20f * ease
        if (t > 0.72f) {
            scale += 0.08f * kotlin.math.sin(((t - 0.72f) / 0.28f * Math.PI).toFloat())
        }

        cv.save()
        cv.scale(scale, scale, cx, cx)
        // 旧形：横向收拢 + 淡出
        cv.save()
        cv.scale(1f - t * 0.28f, 1f, cx, cx)
        shape(cv, playing, s, c.accent, (255 * (1f - t)).toInt().coerceIn(0, 255))
        cv.restore()
        // 新形：横向展开 + 淡入
        cv.save()
        cv.scale(0.72f + t * 0.28f, 1f, cx, cx)
        shape(cv, !playing, s, c.accent, (255 * t).toInt().coerceIn(0, 255))
        cv.restore()
        cv.restore()
        return b
    }

    /** 三角形（播放）或两根圆角竖条（暂停），用 FILL_AND_STROKE 得到圆润边角 */
    private fun shape(cv: Canvas, play: Boolean, s: Int, color: Int, alpha: Int) {
        if (alpha <= 0) return
        val p = aa(color).apply {
            this.alpha = alpha
            style = Paint.Style.FILL_AND_STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = s * 0.05f
        }
        if (play) {
            val path = Path().apply {
                moveTo(s * 0.36f, s * 0.25f)
                lineTo(s * 0.36f, s * 0.75f)
                lineTo(s * 0.78f, s * 0.50f)
                close()
            }
            cv.drawPath(path, p)
        } else {
            val w = s * 0.14f
            val top = s * 0.25f
            val bottom = s * 0.75f
            cv.drawRoundRect(
                RectF(s * 0.31f, top, s * 0.31f + w, bottom), w / 2, w / 2, p
            )
            cv.drawRoundRect(
                RectF(s * 0.69f - w, top, s * 0.69f, bottom), w / 2, w / 2, p
            )
        }
    }

    /** 拍照即定位：橙色发光圆 + 白色加号 */
    fun plus(c: WidgetKit.C): Bitmap {
        val s = 180
        val b = bitmap(s, s)
        val cv = Canvas(b)
        val cx = s / 2f
        val glow = aa(c.accentSoft).apply { alpha = 45 }
        cv.drawCircle(cx, cx, s * 0.46f, glow)
        cv.drawCircle(cx, cx, s * 0.36f, aa(c.accentSoft))
        val white = aa(c.onAccent).apply {
            strokeWidth = 9f
            strokeCap = Paint.Cap.ROUND
        }
        val arm = s * 0.15f
        cv.drawLine(cx - arm, cx, cx + arm, cx, white)
        cv.drawLine(cx, cx - arm, cx, cx + arm, white)
        return b
    }

    /** 语录卡右下角的橙色脚印 */
    fun foot(c: WidgetKit.C): Bitmap {
        val s = 96
        val b = bitmap(s, s)
        val cv = Canvas(b)
        val p = aa(c.accent)
        cv.save()
        cv.rotate(-18f, s / 2f, s / 2f)
        // 脚掌 + 脚跟
        cv.drawOval(RectF(s * 0.31f, s * 0.30f, s * 0.68f, s * 0.72f), p)
        cv.drawOval(RectF(s * 0.36f, s * 0.70f, s * 0.63f, s * 0.87f), p)
        // 四个脚趾，越外侧越小
        val toes = listOf(
            Triple(0.29f, 0.20f, 7.5f),
            Triple(0.42f, 0.15f, 6.5f),
            Triple(0.55f, 0.16f, 5.5f),
            Triple(0.67f, 0.21f, 4.5f)
        )
        toes.forEach { (fx, fy, r) ->
            cv.drawCircle(s * fx, s * fy, r, p)
        }
        cv.restore()
        return b
    }

    /** 攻略卡左侧圆角缩略图：优先真实封面，回退大理示例图 */
    fun roundedCover(context: Context, src: Bitmap?, resId: Int?): Bitmap {
        val s = 150
        val b = bitmap(s, s)
        val cv = Canvas(b)
        val bm = src ?: runCatching {
            if (resId != null && resId != 0) BitmapFactory.decodeResource(context.resources, resId)
            else BitmapFactory.decodeResource(context.resources, R.drawable.ic_guide_dali)
        }.getOrNull() ?: return b

        val radius = s * 0.20f
        val clip = Path().apply {
            addRoundRect(RectF(0f, 0f, s.toFloat(), s.toFloat()), radius, radius, Path.Direction.CW)
        }
        cv.clipPath(clip)
        val scale = max(s.toFloat() / bm.width, s.toFloat() / bm.height)
        val dw = bm.width * scale
        val dh = bm.height * scale
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate((s - dw) / 2f, (s - dh) / 2f)
        }
        cv.drawBitmap(
            bm, m,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        )
        return b
    }
}
