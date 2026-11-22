package com.example.my.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream

/**
 * 视频 / 实况片段的封面帧。
 *
 * 足迹卡片和详情页都要在「视频还没有播」的时候有东西可显示：之前只存了 mp4，
 * 列表里就只能退化成一块纯色渐变，用户根本认不出是哪段片子。
 * 这里抽第一帧存成 jpg 缓存（filesDir/posters），按「路径哈希 + 文件大小」做 key，
 * 同一个视频只抽一次；文件被换掉（大小变了）缓存自然失效，不会显示旧封面。
 *
 * 注意：抽帧是 IO，必须放到 IO 线程调用，不要在组合里直接调。
 */
object VideoPoster {

    /** 封面最长边，够卡片和详情页放大用，又不至于把内存撑爆 */
    private const val MAX_DIM = 720
    private const val QUALITY = 88

    private fun cacheDir(context: Context): File =
        File(context.filesDir, "posters").apply { if (!exists()) mkdirs() }

    private fun cacheFile(context: Context, videoPath: String): File {
        val src = File(videoPath)
        val key = "%08x_%d".format(videoPath.hashCode(), src.length())
        return File(cacheDir(context), "$key.jpg")
    }

    /** 返回封面 jpg 的绝对路径；抽不出来（编码不支持 / 文件损坏）返回 null */
    fun ensure(context: Context, videoPath: String): String? = runCatching {
        val src = File(videoPath)
        if (!src.exists() || src.length() <= 0L) return null
        val dst = cacheFile(context, videoPath)
        if (dst.exists() && dst.length() > 0L) return dst.absolutePath

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoPath)
            // 0.12s 而不是 0 帧：不少视频第 0 帧是黑场，抽出来等于没封面
            val frame = retriever.getFrameAtTime(120_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0)
                ?: return null
            if (frame.width <= 0 || frame.height <= 0) {
                frame.recycle()
                return null
            }
            val scale = (MAX_DIM.toFloat() / maxOf(frame.width, frame.height)).coerceAtMost(1f)
            val out = if (scale >= 1f) frame else Bitmap.createScaledBitmap(
                frame,
                (frame.width * scale).toInt().coerceAtLeast(1),
                (frame.height * scale).toInt().coerceAtLeast(1),
                true
            )
            FileOutputStream(dst).use { out.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            if (out !== frame) out.recycle()
            frame.recycle()
            if (dst.exists() && dst.length() > 0L) dst.absolutePath else null
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()
}
