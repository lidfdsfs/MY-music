package com.example.my.data.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max

/**
 * 歌词卡 / 歌词海报的离屏绘制。
 *
 * 不依赖 Compose 截图，直接画到 Bitmap：尺寸固定，分享和保存都不受当前屏幕密度影响。
 * 两个产物视觉不同：歌词卡是浅色卡片，歌词海报是深色封面大图。
 */
object LyricShareArt {

    fun card(
        title: String,
        artist: String,
        lines: List<String>,
        focus: Int,
        cover: Bitmap?
    ): Bitmap {
        val w = 1080
        val h = 1440
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                Color.rgb(253, 246, 238), Color.rgb(238, 217, 196),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(RectF(70f, 70f, 1010f, 1370f), 42f, 42f, card)

        val label = textPaint(30f, Color.rgb(158, 122, 96), Typeface.NORMAL, Paint.Align.LEFT)
        canvas.drawText("MY足迹 · 歌词卡", 120f, 170f, label)

        if (cover != null) {
            drawCover(canvas, cover, RectF(120f, 230f, 300f, 410f), 24f)
        } else {
            val ph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(242, 226, 210) }
            canvas.drawRoundRect(RectF(120f, 230f, 300f, 410f), 24f, 24f, ph)
            canvas.drawText("MY", 168f, 338f, textPaint(42f, Color.rgb(176, 133, 100), Typeface.BOLD))
        }

        canvas.drawText(fit(title.ifBlank { "未命名歌曲" }, textPaint(48f, Color.rgb(52, 38, 30), Typeface.BOLD, Paint.Align.LEFT), 660f),
            330f, 292f, textPaint(48f, Color.rgb(52, 38, 30), Typeface.BOLD, Paint.Align.LEFT))
        canvas.drawText(fit(artist.ifBlank { "未知歌手" }, textPaint(30f, Color.rgb(150, 118, 96), Typeface.NORMAL, Paint.Align.LEFT), 660f),
            330f, 350f, textPaint(30f, Color.rgb(150, 118, 96), Typeface.NORMAL, Paint.Align.LEFT))

        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(238, 224, 209) }
        canvas.drawRect(120f, 470f, 960f, 472f, divider)

        val picked = pick(lines, focus, 5)
        val lyricPaint = textPaint(40f, Color.rgb(70, 50, 40), Typeface.NORMAL)
        var y = 570f
        picked.forEach { line ->
            val text = fit(line, lyricPaint, 800f)
            canvas.drawText(text, w / 2f, y, lyricPaint)
            y += 92f
        }

        val foot = textPaint(24f, Color.rgb(176, 142, 116), Typeface.NORMAL)
        canvas.drawText("把喜欢的歌词，留在自己的足迹里", w / 2f, 1290f, foot)
        return bmp
    }

    fun poster(
        title: String,
        artist: String,
        lines: List<String>,
        focus: Int,
        cover: Bitmap?
    ): Bitmap {
        val w = 1080
        val h = 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (cover != null) {
            drawCover(canvas, cover, RectF(0f, 0f, w.toFloat(), h.toFloat()), 0f)
        } else {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    Color.rgb(32, 28, 26), Color.rgb(92, 52, 38),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        }

        val veil = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(0x33000000, 0xCC000000.toInt(), 0xF2000000.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), veil)

        val label = textPaint(28f, Color.argb(210, 255, 238, 220), Typeface.NORMAL, Paint.Align.LEFT)
        canvas.drawText("MY足迹 · 歌词海报", 90f, 180f, label)

        val picked = pick(lines, focus, 4)
        val lyricPaint = textPaint(56f, Color.WHITE, Typeface.BOLD)
        var y = 760f
        picked.forEach { line ->
            canvas.drawText(fit(line, lyricPaint, 880f), w / 2f, y, lyricPaint)
            y += 112f
        }

        val titlePaint = textPaint(50f, Color.WHITE, Typeface.BOLD, Paint.Align.LEFT)
        canvas.drawText(fit(title.ifBlank { "未命名歌曲" }, titlePaint, 760f), 90f, 1510f, titlePaint)
        val artistPaint = textPaint(30f, Color.argb(200, 255, 240, 225), Typeface.NORMAL, Paint.Align.LEFT)
        canvas.drawText(fit(artist.ifBlank { "未知歌手" }, artistPaint, 760f), 90f, 1570f, artistPaint)

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 255, 255) }
        canvas.drawRect(90f, 1620f, 990f, 1622f, line)
        val foot = textPaint(24f, Color.argb(170, 255, 238, 220), Typeface.NORMAL, Paint.Align.LEFT)
        canvas.drawText("MY足迹 · 听见风景，也记住心情", 90f, 1690f, foot)
        return bmp
    }

    /**
     * 歌曲分享卡：封面全幅 + 歌名歌手大字 + 本地真实数据（播放次数 / 收藏 / 时长）。
     * 数据全部来自本机播放记录，不编造；没有播放记录就只显示时长。
     */
    fun songCard(
        title: String,
        artist: String,
        cover: Bitmap?,
        playCount: Int,
        favorite: Boolean,
        durationText: String
    ): Bitmap {
        val w = 1080
        val h = 1440
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (cover != null) {
            drawCover(canvas, cover, RectF(0f, 0f, w.toFloat(), h.toFloat()), 0f)
        } else {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    Color.rgb(28, 30, 34), Color.rgb(70, 62, 58),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        }
        val veil = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(0x59000000, 0xD9000000.toInt(), 0xF5000000.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), veil)

        val label = textPaint(28f, Color.argb(210, 255, 238, 220), Typeface.NORMAL, Paint.Align.LEFT)
        canvas.drawText("MY足迹 · 歌曲卡片", 90f, 180f, label)

        val titlePaint = textPaint(72f, Color.WHITE, Typeface.BOLD)
        canvas.drawText(fit(title.ifBlank { "未命名歌曲" }, titlePaint, 900f), w / 2f, 960f, titlePaint)
        val artistPaint = textPaint(34f, Color.argb(215, 255, 240, 225), Typeface.NORMAL)
        canvas.drawText(fit(artist.ifBlank { "未知歌手" }, artistPaint, 900f), w / 2f, 1035f, artistPaint)

        // 本地数据一行：真实播放次数 / 收藏状态 / 时长，没有就不显示对应段
        val stats = mutableListOf<String>()
        if (playCount > 0) stats += "播放 ${playCount} 次"
        if (favorite) stats += "已收藏"
        if (durationText.isNotBlank()) stats += durationText
        if (stats.isNotEmpty()) {
            val statPaint = textPaint(30f, Color.argb(200, 255, 238, 220), Typeface.NORMAL)
            canvas.drawText(stats.joinToString("  ·  "), w / 2f, 1120f, statPaint)
        }

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 255, 255) }
        canvas.drawRect(90f, 1240f, 990f, 1242f, line)
        val foot = textPaint(24f, Color.argb(170, 255, 238, 220), Typeface.NORMAL)
        canvas.drawText("MY足迹 · 本地音乐，循环自己的歌", w / 2f, 1320f, foot)
        return bmp
    }

    private fun textPaint(
        size: Float,
        color: Int,
        style: Int,
        align: Paint.Align = Paint.Align.CENTER
    ): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = align
            typeface = Typeface.create("serif", style)
        }

    private fun pick(lines: List<String>, focus: Int, count: Int): List<String> {
        val clean = lines.map { it.trim() }.filter { it.isNotBlank() }
        if (clean.isEmpty()) return List(count) { if (it == 0) "暂无歌词" else "" }
        val size = minOf(count, clean.size)
        val start = (focus - size / 2).coerceIn(0, (clean.size - size).coerceAtLeast(0))
        return clean.drop(start).take(size)
    }

    private fun fit(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private fun drawCover(canvas: Canvas, cover: Bitmap, dst: RectF, radius: Float) {
        val scale = max(dst.width() / cover.width, dst.height() / cover.height)
        val sw = (dst.width() / scale).toInt().coerceAtLeast(1)
        val sh = (dst.height() / scale).toInt().coerceAtLeast(1)
        val sx = ((cover.width - sw) / 2).coerceAtLeast(0)
        val sy = ((cover.height - sh) / 2).coerceAtLeast(0)
        val src = Rect(sx, sy, (sx + sw).coerceAtMost(cover.width), (sy + sh).coerceAtMost(cover.height))

        canvas.save()
        if (radius > 0f) {
            val path = Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) }
            canvas.clipPath(path)
        }
        canvas.drawBitmap(cover, src, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }
}
