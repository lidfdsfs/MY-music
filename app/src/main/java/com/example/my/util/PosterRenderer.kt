package com.example.my.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import com.example.my.model.Footprint
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Text
import kotlin.math.roundToInt

/** 年报长图的全部展示数据（与页面共用一份计算结果，保证导出与所见一致） */
data class YearReportData(
    val year: Int,
    val totalKm: Int,
    val cityCount: Int,
    val footprintCount: Int,
    val tripDays: Int,
    val highlightCount: Int,
    val persona: String,
    val months: IntArray,
    val areas: List<Pair<String, Int>>,
    val highlights: List<String>,
    val cities: List<String>
)

/**
 * 长图渲染器。
 *
 * 为什么不用 Compose 的 GraphicsLayer.drawToBitmap：
 * 它只能把**已经布局在屏幕上**的内容录制成位图，而年报长图远高于屏幕高度；
 * 官方给离屏渲染的方案是 VirtualDisplay + Presentation，成本高且受显示器尺寸限制。
 * 这里直接用 android.graphics.Canvas 绘制，同样零新增依赖，且高度不受屏幕约束。
 * 代价是不复用 Compose 的排版代码，所以尺寸/色值统一从主题 token 取，避免两套视觉漂移。
 */
object PosterRenderer {

    private const val W = 1080
    /** 1080 宽对应 360dp 的设计稿，即 1dp = 3px */
    private const val S = 3f
    private const val PAD = 20f * S
    private const val CARD_R = 20f * S
    /** 超过这个高度说明数据异常，直接截断，避免 Bitmap 过大 OOM */
    private const val MAX_H = 8000

    private fun Paint.textStyle(sp: Float, color: Int, bold: Boolean = false) {
        this.color = color
        textSize = sp * S
        isAntiAlias = true
        typeface = if (bold) Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD) else Typeface.DEFAULT
    }

    // ---------- 可空 Canvas：传 null 时只做测量，不真正绘制 ----------

    private fun Canvas?.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float, paint: Paint) {
        this ?: return
        drawRoundRect(RectF(x, y, x + w, y + h), r, r, paint)
    }

    /** 在 (x, baselineY) 处画一行文字 */
    private fun Canvas?.line(t: String, x: Float, baselineY: Float, paint: Paint) {
        this ?: return
        drawText(t, x, baselineY, paint)
    }

    /** 画一段自动换行的文字，返回占用高度 */
    private fun Canvas?.para(t: String, x: Float, y: Float, w: Float, paint: Paint, maxLines: Int = Int.MAX_VALUE): Int {
        val tp = TextPaint(paint)
        val layout = StaticLayout.Builder.obtain(t, 0, t.length, tp, w.roundToInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        if (this != null) {
            save()
            translate(x, y)
            layout.draw(this)
            restore()
        }
        return layout.height
    }

    /** 一张带白色卡片底的内容区块：返回区块总高度 */
    private fun Canvas?.card(
        y: Float,
        title: String,
        subtitle: String,
        bodyHeight: Float,
        body: (Canvas?, Float) -> Unit
    ): Float {
        val inner = 24f * S
        val titleH = 22f * S
        var h = inner + titleH + 10f * S + 16f * S + 14f * S + bodyHeight + inner
        if (this != null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            roundRect(PAD, y, W - PAD * 2, h, CARD_R, paint.apply { color = Bg.Surface.toArgb() })
            line(title, PAD + inner, y + inner + titleH, Paint().apply { textStyle(15f, Text.Primary.toArgb(), bold = true) })
            line(subtitle, PAD + inner, y + inner + titleH + 10f * S + 14f * S, Paint().apply { textStyle(11f, Text.Muted.toArgb()) })
        }
        body(this, y + inner + titleH + 10f * S + 16f * S + 14f * S)
        return h
    }

    // ---------- 年度报告 ----------

    fun renderYearReport(data: YearReportData): Bitmap {
        val height = layoutYearReport(null, data).coerceAtMost(MAX_H)
        val bmp = Bitmap.createBitmap(W, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Bg.Background.toArgb())
        layoutYearReport(canvas, data)
        return bmp
    }

    private fun layoutYearReport(c: Canvas?, d: YearReportData): Int {
        var y = PAD

        // —— 主视觉卡片 ——
        val heroH = 176f * S
        if (c != null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(
                PAD, y, W - PAD, y + heroH,
                Brand.TerracottaLight.toArgb(), Brand.TerracottaDark.toArgb(), Shader.TileMode.CLAMP
            )
            c.roundRect(PAD, y, W - PAD * 2, heroH, CARD_R, paint)
        }
        var hy = y + 26f * S
        c.line("${d.year} 年，我一共走了", PAD + 26f * S, hy + 13f * S,
            Paint().apply { textStyle(13f, android.graphics.Color.argb(217, 255, 255, 255)) })
        hy += 13f * S + 12f * S
        c.line("${d.totalKm}", PAD + 26f * S, hy + 42f * S,
            Paint().apply { textStyle(42f, android.graphics.Color.WHITE, bold = true) })
        val kmOffset = Paint().apply { textStyle(42f, android.graphics.Color.WHITE, bold = true) }
            .measureText("${d.totalKm}") + 6f * S
        c.line("km", PAD + 26f * S + kmOffset, hy + 42f * S,
            Paint().apply { textStyle(15f, android.graphics.Color.argb(230, 255, 255, 255)) })
        hy += 42f * S + 16f * S
        // 四项统计
        val stats = listOf(
            "${d.cityCount}" to "座城市",
            "${d.footprintCount}" to "个足迹",
            "${d.tripDays}" to "天在路上",
            "${d.highlightCount}" to "个高光"
        )
        val colW = (W - PAD * 2 - 52f * S) / 4f
        stats.forEachIndexed { i, (v, l) ->
            val x = PAD + 26f * S + colW * i
            c.line(v, x, hy + 20f * S, Paint().apply { textStyle(20f, android.graphics.Color.WHITE, bold = true) })
            c.line(l, x, hy + 20f * S + 14f * S + 8f * S,
                Paint().apply { textStyle(11f, android.graphics.Color.argb(199, 255, 255, 255)) })
        }
        hy += 20f * S + 14f * S + 8f * S + 18f * S
        val chipW = Paint().apply { textStyle(12f, android.graphics.Color.WHITE) }
            .measureText("我的旅行人格：${d.persona}") + 24f * S
        if (c != null) {
            c.roundRect(PAD + 26f * S, hy, chipW, 30f * S, 15f * S,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(46, 255, 255, 255) })
        }
        c.line("我的旅行人格：${d.persona}", PAD + 26f * S + 12f * S, hy + 21f * S,
            Paint().apply { textStyle(12f, android.graphics.Color.WHITE) })
        y += heroH + 20f * S

        // —— 月度分布 ——
        val chartH = 120f * S
        y += c.card(y, "足迹分布", "按月份统计今年记录的足迹", chartH + 10f * S + 16f * S) { cv, bodyY ->
            if (cv == null) return@card
            val max = d.months.max().coerceAtLeast(1)
            val barW = (W - PAD * 2 - 48f * S - 11 * 4f * S) / 12f
            d.months.forEachIndexed { i, count ->
                val frac = if (count == 0) 0.03f else count.toFloat() / max
                val h = (chartH * frac).coerceAtLeast(4f * S)
                val x = PAD + 24f * S + (barW + 4f * S) * i
                val top = bodyY + chartH - h
                cv.roundRect(x, top, barW, h, 4f * S, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (count == 0) Bg.SurfaceVariant.toArgb()
                    else Brand.Terracotta.toArgb()
                    alpha = (140 + 115 * frac).roundToInt().coerceIn(0, 255)
                })
            }
            val axisY = bodyY + chartH + 10f * S + 12f * S
            val labels = listOf("1月", "3月", "6月", "9月", "12月")
            val axisPaint = Paint().apply { textStyle(10f, Text.Muted.toArgb()) }
            val trackW = W - PAD * 2 - 48f * S
            val step = trackW / (labels.size - 1)
            labels.forEachIndexed { i, label ->
                val x = when (i) {
                    0 -> PAD + 24f * S
                    labels.lastIndex -> PAD + 24f * S + trackW - axisPaint.measureText(label)
                    else -> PAD + 24f * S + step * i - axisPaint.measureText(label) / 2f
                }
                cv.line(label, x, axisY, axisPaint)
            }
        } + 16f * S

        // —— 常去片区 ——
        if (d.areas.isNotEmpty()) {
            val rows = d.areas.take(5)
            val rowH = 26f * S
            y += c.card(y, "常去的片区", "按地理密度聚类，看看今年的主场", rows.size * rowH + (rows.size - 1) * 8f * S) { cv, bodyY ->
                if (cv == null) return@card
                val maxArea = rows.maxOf { it.second }.coerceAtLeast(1)
                val barX = PAD + 24f * S + 90f * S
                val barMaxW = W - PAD * 2 - 48f * S - 90f * S - 60f * S
                rows.forEachIndexed { i, (city, count) ->
                    val ry = bodyY + (rowH + 8f * S) * i
                    cv.line(city, PAD + 24f * S, ry + 16f * S,
                        Paint().apply { textStyle(13f, Text.Primary.toArgb()) })
                    cv.roundRect(barX, ry + 4f * S, barMaxW, 6f * S, 3f * S,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Bg.SurfaceVariant.toArgb() })
                    cv.roundRect(barX, ry + 4f * S, barMaxW * (count.toFloat() / maxArea), 6f * S, 3f * S,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Brand.Gold.toArgb() })
                    cv.line("$count 次", barX + barMaxW + 10f * S, ry + 16f * S,
                        Paint().apply { textStyle(12f, Text.Muted.toArgb()) })
                }
            } + 16f * S
        }

        // —— 高光时刻 ——
        run {
            val list = d.highlights.take(5)
            val rowH = 22f * S
            val rows = list.size.coerceAtLeast(1)
            y += c.card(
                y, "高光时刻",
                if (list.isEmpty()) "今年还没有点亮特别足迹" else "这些时刻值得被记住",
                rows * rowH + (rows - 1) * 10f * S
            ) { cv, bodyY ->
                if (cv == null) return@card
                if (list.isEmpty()) {
                    cv.line("在足迹里标记「特别」后，会出现在这里", PAD + 24f * S, bodyY + 14f * S,
                        Paint().apply { textStyle(12f, Text.Muted.toArgb()) })
                } else {
                    list.forEachIndexed { i, name ->
                        val ry = bodyY + (rowH + 10f * S) * i
                        cv.line("${i + 1}".padStart(2, '0'), PAD + 24f * S, ry + 16f * S,
                            Paint().apply { textStyle(12f, Brand.Terracotta.toArgb(), bold = true) })
                        cv.line(name, PAD + 24f * S + 34f * S, ry + 16f * S,
                            Paint().apply { textStyle(13f, Text.Primary.toArgb()) })
                        cv.line("特别", W - PAD - 24f * S - 26f * S, ry + 16f * S,
                            Paint().apply { textStyle(11f, Brand.Gold.toArgb()) })
                    }
                }
            } + 16f * S
        }

        // —— 去过的地方 ——
        run {
            val body = if (d.cities.isEmpty()) "今年还没有足迹记录，去地图上点亮第一个吧"
            else d.cities.joinToString(" · ")
            val paint = Paint().apply { textStyle(13f, Text.Secondary.toArgb()) }
            paint.textSize = 13f * S
            val textW = (W - PAD * 2 - 48f * S).roundToInt()
            val measurePaint = TextPaint(paint)
            val h = StaticLayout.Builder.obtain(body, 0, body.length, measurePaint, textW)
                .setIncludePad(false).build().height.toFloat()
            y += c.card(y, "去过的地方", "${d.cities.size} 座城市 / 地区", h) { cv, bodyY ->
                cv?.para(body, PAD + 24f * S, bodyY, textW.toFloat(), paint)
            } + 16f * S
        }

        // —— 水印 ——
        y += 8f * S
        val wm = "MY足迹 · ${d.year}"
        val wmPaint = Paint().apply { textStyle(12f, Text.Muted.toArgb()) }
        c.line(wm, (W - wmPaint.measureText(wm)) / 2f, y + 14f * S, wmPaint)
        y += 14f * S + 26f * S
        return y.roundToInt()
    }

    // ---------- 旅行纪念卡 ----------

    /** 选中的足迹生成一张竖版纪念卡（最多 9 条，每条最多 3 张照片） */
    fun renderMemoryCard(footprints: List<Footprint>): Bitmap {
        val height = layoutMemoryCard(null, footprints).coerceAtMost(MAX_H)
        val bmp = Bitmap.createBitmap(W, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Bg.Background.toArgb())
        layoutMemoryCard(canvas, footprints)
        return bmp
    }

    private fun layoutMemoryCard(c: Canvas?, fps: List<Footprint>): Int {
        var y = PAD
        val dates = fps.map { it.date }.filter { it.isNotBlank() }.sorted()
        val range = when {
            dates.isEmpty() -> ""
            dates.size == 1 || dates.first() == dates.last() -> dates.first()
            else -> "${dates.first()} → ${dates.last()}"
        }

        // 头部
        val headH = 96f * S
        if (c != null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(
                PAD, y, W - PAD, y + headH,
                Brand.TerracottaLight.toArgb(), Brand.TerracottaDark.toArgb(), Shader.TileMode.CLAMP
            )
            c.roundRect(PAD, y, W - PAD * 2, headH, CARD_R, paint)
        }
        c.line("旅行纪念卡", PAD + 26f * S, y + 36f * S,
            Paint().apply { textStyle(20f, android.graphics.Color.WHITE, bold = true) })
        c.line(if (range.isBlank()) "共 ${fps.size} 处足迹" else "$range · 共 ${fps.size} 处足迹",
            PAD + 26f * S, y + 36f * S + 26f * S,
            Paint().apply { textStyle(12f, android.graphics.Color.argb(217, 255, 255, 255)) })
        y += headH + 16f * S

        val inner = 24f * S
        fps.forEach { fp ->
            val photos = fp.photoPaths.take(3)
            val notePaint = Paint().apply { textStyle(13f, Text.Secondary.toArgb()) }
            val noteW = (W - PAD * 2 - inner * 2).roundToInt()
            val noteH = if (fp.note.isBlank()) 0f else {
                StaticLayout.Builder.obtain(fp.note, 0, fp.note.length, TextPaint(notePaint), noteW)
                    .setIncludePad(false).setMaxLines(3)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END).build().height.toFloat()
            }
            val thumbH = if (photos.isEmpty()) 0f else 108f * S
            var cardH = inner + 20f * S + 8f * S + 16f * S +
                (if (noteH > 0) noteH + 12f * S else 0f) +
                (if (thumbH > 0) thumbH + 12f * S else 0f) + inner
            c?.roundRect(PAD, y, W - PAD * 2, cardH, CARD_R,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Bg.Surface.toArgb() })

            var cy = y + inner
            val title = buildString {
                append(fp.name)
                if (fp.special) append("  · 特别")
            }
            c.line(title, PAD + inner, cy + 20f * S,
                Paint().apply { textStyle(16f, Text.Primary.toArgb(), bold = true) })
            cy += 20f * S + 8f * S
            c.line(listOf(fp.city, fp.date).filter { it.isNotBlank() }.joinToString(" · "),
                PAD + inner, cy + 14f * S, Paint().apply { textStyle(12f, Text.Muted.toArgb()) })
            cy += 16f * S
            if (noteH > 0) {
                c?.para(fp.note, PAD + inner, cy, noteW.toFloat(), notePaint, maxLines = 3)
                cy += noteH + 12f * S
            }
            if (photos.isNotEmpty()) {
                val gap = 8f * S
                val totalW = W - PAD * 2 - inner * 2
                val eachW = (totalW - gap * (photos.size - 1)) / photos.size
                photos.forEachIndexed { i, path ->
                    val bmp = PhotoStore.decodeFile(path, 400) ?: return@forEachIndexed
                    val x = PAD + inner + (eachW + gap) * i
                    val srcW = bmp.width.toFloat()
                    val srcH = bmp.height.toFloat()
                    val scale = maxOf(eachW / srcW, thumbH / srcH)
                    val dx = (eachW - srcW * scale) / 2f
                    val dy = (thumbH - srcH * scale) / 2f
                    if (c != null) {
                        c.save()
                        c.translate(x, cy)
                        c.clipRect(0f, 0f, eachW, thumbH)
                        c.drawBitmap(bmp, null,
                            RectF(dx, dy, dx + srcW * scale, dy + srcH * scale),
                            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                        c.restore()
                    }
                    bmp.recycle()
                }
                cy += thumbH + 12f * S
            }
            y += cardH + 12f * S
        }

        y += 4f * S
        val wm = "MY足迹 · 和我一起走过"
        val wmPaint = Paint().apply { textStyle(12f, Text.Muted.toArgb()) }
        c.line(wm, (W - wmPaint.measureText(wm)) / 2f, y + 14f * S, wmPaint)
        y += 14f * S + 26f * S
        return y.roundToInt()
    }
}
