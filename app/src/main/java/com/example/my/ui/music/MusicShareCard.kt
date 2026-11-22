package com.example.my.ui.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import com.example.my.data.music.LocalSong
import com.example.my.data.music.MusicExternal

/**
 * 歌曲分享卡片：1080×1440 竖版图。
 * 背景取封面主色做纵向渐变，封面圆角大方块居中，歌名/歌手排版，底部 MY足迹 水印。
 * 纯 android.graphics 离屏绘制，不依赖 Compose，可在 Dispatchers.Default 直接跑。
 */
fun renderSongCard(context: Context, song: LocalSong): Bitmap {
    val w = 1080
    val h = 1440
    val cover = runCatching { MusicExternal.loadCover(context, song) }.getOrNull()
    val accent = cover?.let { dominantColor(it) } ?: 0xFF159A94.toInt()

    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)

    // 背景：主色 → 压暗的近黑，纵向渐变
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            lighten(accent, 0.10f),
            darken(accent, 0.82f),
            Shader.TileMode.CLAMP
        )
    }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

    // 顶部小字：MY足迹 · 正在听
    val tagPaint = textPaint(38f, Color.argb(190, 255, 255, 255), fakeBold = true)
    c.drawText("MY足迹 · 正在听", 72f, 128f, tagPaint)

    // 封面：720px 圆角方块，居中
    val coverSize = 720f
    val coverLeft = (w - coverSize) / 2f
    val coverTop = 220f
    if (cover != null) {
        val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(
            coverLeft, coverTop, coverLeft + coverSize, coverTop + coverSize,
            44f, 44f, coverPaint
        )
    } else {
        // 没封面：同色系深一点的圆角块，不空着
        val ph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darken(accent, 0.55f) }
        c.drawRoundRect(
            coverLeft, coverTop, coverLeft + coverSize, coverTop + coverSize,
            44f, 44f, ph
        )
        val phText = textPaint(120f, Color.argb(90, 255, 255, 255), fakeBold = true)
        c.drawText("♪", w / 2f, coverTop + coverSize / 2f + 40f, phText)
    }

    // 主色小横条，压在封面和标题之间
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lighten(accent, 0.35f) }
    c.drawRoundRect(72f, 1010f, 232f, 1022f, 6f, 6f, barPaint)

    // 歌名（单行超长省略）与歌手
    val titlePaint = textPaint(84f, Color.WHITE, fakeBold = true)
    c.drawText(ellipsize(song.title, titlePaint, w - 144f), 72f, 1150f, titlePaint)
    val artistPaint = textPaint(50f, Color.argb(205, 255, 255, 255))
    val artistText = song.artist.ifBlank { "未知歌手" }
    c.drawText(ellipsize(artistText, artistPaint, w - 144f), 72f, 1240f, artistPaint)

    // 底部水印
    val footPaint = textPaint(36f, Color.argb(140, 255, 255, 255))
    val foot = "来自 MY足迹 音乐"
    val footW = footPaint.measureText(foot)
    c.drawText(foot, (w - footW) / 2f, h - 84f, footPaint)
    return out
}

private fun textPaint(sizePx: Float, color: Int, fakeBold: Boolean = false) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = sizePx
        typeface = if (fakeBold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
    }

/** 单行文本超长时末尾补省略号 */
private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var t = text
    while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
    return "$t…"
}

/** 缩到 1×1 取主色；太小的图直接返回像素 */
private fun dominantColor(src: Bitmap): Int = runCatching {
    val px = Bitmap.createScaledBitmap(src, 1, 1, true).getPixel(0, 0)
    Color.rgb(
        (Color.red(px) * 1.05f).toInt().coerceIn(0, 255),
        (Color.green(px) * 1.05f).toInt().coerceIn(0, 255),
        (Color.blue(px) * 1.05f).toInt().coerceIn(0, 255)
    )
}.getOrDefault(0xFF159A94.toInt())

private fun lighten(color: Int, amount: Float): Int = Color.rgb(
    (Color.red(color) + (255 - Color.red(color)) * amount).toInt(),
    (Color.green(color) + (255 - Color.green(color)) * amount).toInt(),
    (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt()
)

private fun darken(color: Int, amount: Float): Int = Color.rgb(
    (Color.red(color) * (1f - amount)).toInt(),
    (Color.green(color) * (1f - amount)).toInt(),
    (Color.blue(color) * (1f - amount)).toInt()
)
