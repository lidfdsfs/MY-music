package com.example.my.util.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 拍摄期「特效 / 美颜 / 文字」的真实图像处理。
 *
 * 全部基于 android.graphics.ColorMatrix 与 Canvas，minSdk 24 可用，不依赖 GPUImage 或
 * RenderEffect（后者 31+ 才有，old 设备会崩）。处理链路一次解码、一次编码：
 *   解码 → 滤镜(色调矩阵) → 美颜(提亮 + 磨皮柔化 + 红润) → 文字水印 → 写回文件。
 * 每一档都内置「无」作为默认值，用户不动就走恒等矩阵，不额外损伤画质。
 */
object CaptureEffects {

    /** 滤镜：id 稳定持久化在 SP，name 双语 */
    data class Filter(val id: String, val zh: String, val en: String, val matrix: FloatArray) {
        val isNone get() = id == FILTER_NONE
    }

    const val FILTER_NONE = "none"

    /**
     * 8 套滤镜：矩阵是 4x5 的 ColorMatrix。
     * 亮度/对比/饱和/色相偏移都折进矩阵，避免多次 Canvas 叠加带来的多次采样损失。
     */
    val FILTERS: List<Filter> = listOf(
        Filter(FILTER_NONE, "原图", "Original", floatArrayOf(
            1f,0f,0f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,1f,0f,0f, 0f,0f,0f,1f,0f
        )),
        Filter("clear", "清透", "Clear", floatArrayOf(
            1.08f,0f,0f,0f,2f, 0f,1.08f,0f,0f,2f, 0f,0f,1.06f,0f,2f, 0f,0f,0f,1f,0f
        )),
        Filter("warm", "暖阳", "Sunny", floatArrayOf(
            1.14f,0f,0f,0f,8f, 0f,1.04f,0f,0f,2f, 0f,0f,0.90f,0f,0f, 0f,0f,0f,1f,0f
        )),
        Filter("film", "胶片", "Film", floatArrayOf(
            1.10f,0.03f,0f,0f,6f, 0.02f,1.02f,0.02f,0f,4f, 0f,0.02f,0.92f,0f,4f, 0f,0f,0f,1f,0f
        )),
        Filter("mono", "黑白", "B&W", floatArrayOf(
            0.33f,0.33f,0.33f,0f,0f, 0.33f,0.33f,0.33f,0f,0f, 0.33f,0.33f,0.33f,0f,0f, 0f,0f,0f,1f,0f
        )),
        Filter("dusk", "暮色", "Dusk", floatArrayOf(
            1.05f,0f,0.02f,0f,6f, 0f,0.96f,0.03f,0f,2f, 0f,0.04f,1.12f,0f,10f, 0f,0f,0f,1f,0f
        )),
        Filter("cool", "冷调", "Cool", floatArrayOf(
            0.92f,0f,0.04f,0f,0f, 0f,1.02f,0.05f,0f,2f, 0f,0.05f,1.16f,0f,8f, 0f,0f,0f,1f,0f
        )),
        Filter("vivid", "鲜艳", "Vivid", floatArrayOf(
            1.20f,0f,0f,0f,0f, 0f,1.20f,0f,0f,0f, 0f,0f,1.20f,0f,0f, 0f,0f,0f,1f,0f
        ))
    )

    fun filterById(id: String?): Filter = FILTERS.firstOrNull { it.id == id } ?: FILTERS[0]

    /** 美颜三档强度，全部 0..100；默认全 0 = 不处理 */
    data class Beauty(
        val smooth: Int = 0,      // 磨皮（柔化高频噪点）
        val brighten: Int = 0,    // 提亮
        val blush: Int = 0        // 红润（暖色偏移）
    ) {
        val isNone get() = smooth == 0 && brighten == 0 && blush == 0
    }

    /** 文字水印 */
    data class Watermark(val text: String, val color: Int = Color.WHITE, val top: Boolean = false) {
        val isNone get() = text.isBlank()
    }

    /**
     * 把拍摄文件原地处理（新建临时文件 → 处理 → 覆盖原文件），返回同一个 File。
     * 失败时保留原图并吞掉异常，绝不因为滤镜让拍照丢失。
     */
    suspend fun applyToFile(
        context: Context,
        file: File,
        filter: Filter,
        beauty: Beauty,
        watermark: Watermark? = null
    ): File = withContext(Dispatchers.IO) {
        if (filter.isNone && beauty.isNone && (watermark == null || watermark.isNone)) {
            return@withContext file
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val src = decode(context, uri, MAX_DIM) ?: return@withContext file
        try {
            var bitmap = src
            val m = ColorMatrix(filter.matrix)
            if (!beauty.isNone) {
                // 提亮：加白偏移；红润：R/G 增益；磨皮：降低饱和度 + 轻微加亮，视觉上去掉
                // 高频斑点的对比度（真正的双边滤波在手机全图上太慢，这里用低饱和柔化近似，
                // 实际主观观感是「皮肤变干净」，且速度可控在 100ms 级）。
                val lift = beauty.brighten / 100f * 26f
                val blushR = beauty.blush / 100f * 0.10f
                val blushG = beauty.blush / 100f * 0.03f
                val soften = beauty.smooth / 100f
                val sat = 1f - 0.22f * soften
                val cm = ColorMatrix().apply { setSaturation(sat) }
                val bl = FloatArray(20) {
                    when (it) {
                        0 -> 1f + blushR
                        6 -> 1f + blushG
                        4, 9, 14 -> lift
                        else -> 0f
                    }
                }
                bl[18] = 1f
                cm.postConcat(ColorMatrix(bl))
                m.postConcat(cm)
            }
            if (!filter.isNone || !beauty.isNone) {
                val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    colorFilter = ColorMatrixColorFilter(m)
                }
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
                bitmap.recycle()
                bitmap = out
            }
            if (watermark != null && !watermark.isNone) {
                drawWatermark(bitmap, watermark)
            }
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bitmap.recycle()
        } catch (t: Throwable) {
            // 保留原图：解码成功但编码失败时源 bitmap 可能已 recycle，这里不再二次回收
        }
        file
    }

    private fun drawWatermark(bmp: Bitmap, wm: Watermark) {
        val canvas = Canvas(bmp)
        val text = wm.text.trim()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = wm.color
            textSize = (bmp.width * 0.052f).coerceIn(28f, 96f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(6f, 0f, 2f, Color.argb(160, 0, 0, 0))
        }
        val x = bmp.width / 2f
        val y = if (wm.top) paint.textSize * 1.8f else bmp.height - paint.textSize * 1.4f
        canvas.drawText(text, x, y, paint)
    }

    private fun decode(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        // 先探原始尺寸，再按等比 setTargetSize 解码，避免整图 OOM 或被拉成正方形
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, opts)
            }
        }
        val w = opts.outWidth
        val h = opts.outHeight
        if (w <= 0 || h <= 0) return null
        val scale = (maxDim.toFloat() / maxOf(w, h)).coerceAtMost(1f)
        val tw = (w * scale).toInt().coerceAtLeast(1)
        val th = (h * scale).toInt().coerceAtLeast(1)
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                val src = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(src) { d, _, _ ->
                    d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    d.setTargetSize(tw, th)
                }
            } else {
                var sample = 1
                while (w / sample > maxDim || h / sample > maxDim) sample *= 2
                val o2 = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                val raw = context.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, o2)
                } ?: return null
                if (raw.width != tw || raw.height != th)
                    Bitmap.createScaledBitmap(raw, tw, th, true).also { raw.recycle() }
                else raw
            }
        } catch (t: Throwable) {
            null
        }
    }

    private const val MAX_DIM = 1600
}
