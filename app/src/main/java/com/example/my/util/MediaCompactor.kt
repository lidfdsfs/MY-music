package com.example.my.util

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.my.util.livephoto.LivePhoto
import com.example.my.util.livephoto.MotionVideo
import com.example.my.util.media.VideoCompressor
import java.io.File

/** 压缩结果：[file] 是最终落盘文件，[compressed] 表示真的重编码过（false 即原片直存） */
data class CompactResult(val file: File, val bytes: Long, val compressed: Boolean)

/**
 * 视频 / 实况的落地与体积优化。
 *
 * 原则：
 * 1. **不丢信息优先**：分辨率、帧率、时长全部保留，只把码率压到「肉眼难分辨」的目标值；
 *    源片码率本来就在目标附近时直接复制，一步都不重编码 —— 这一半才是真正的无损。
 * 2. **绝不阻断上传**：任何一步失败（机型不支持、色彩格式不匹配、超时）都回退成原片直存，
 *    宁可存大文件也不能存不进去。
 * 3. 音频不重编码：AAC 轨按字节原样搬进新容器，音频零损失。
 */
object MediaCompactor {

    /** 每像素每帧目标比特：0.07 bpp ≈ 1080p30 约 4.3Mbps，高保真档，压完通常还能小一半 */
    private const val BITS_PER_PIXEL = 0.07
    private const val MIN_BITRATE = 900_000
    private const val MAX_BITRATE = 12_000_000

    private data class VideoStat(
        val width: Int,
        val height: Int,
        val fps: Int,
        val bitrate: Int,
        val durationUs: Long
    )

    /** 从 content Uri 导入一段视频：先落到私有目录，再压缩 */
    suspend fun importVideo(context: Context, uri: Uri, destDir: File, prefix: String): CompactResult? {
        destDir.mkdirs()
        val raw = File(destDir, "raw_$prefix.mp4")
        val imported = runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取视频" }
                raw.outputStream().use { out -> input.copyTo(out) }
            }
        }
        if (imported.isFailure || !raw.exists() || raw.length() <= 0L) {
            raw.delete()
            return null
        }
        val result = runCatching { compact(context, raw, destDir, prefix) }.getOrNull()
        if (result != null && result.file.exists() && result.file.length() > 0L) {
            raw.delete()
            return result
        }
        // 压缩链路出任何意外都原片直存：旧写法整段 runCatching{}.getOrNull()
        // 会把 copyTo 的 IO 异常吞成 null，用户等于白选一次视频。
        val dst = File(destDir, "media_$prefix.mp4")
        val kept = runCatching { raw.copyTo(dst, overwrite = true) }.isSuccess &&
            dst.exists() && dst.length() > 0L
        raw.delete()
        return if (kept) CompactResult(dst, dst.length(), false) else null
    }

    /** 视频 / 实况的统一压缩入口 */
    suspend fun compact(context: Context, src: File, destDir: File, prefix: String): CompactResult? {
        if (!src.exists() || src.length() <= 0L) return null
        destDir.mkdirs()
        val dst = File(destDir, "media_$prefix.mp4")
        val stat = probe(src)
        if (stat == null || stat.width <= 0 || stat.height <= 0) {
            // 探不出参数就原样保存，别拿用户的片子试错
            src.copyTo(dst, overwrite = true)
            return CompactResult(dst, dst.length(), false)
        }
        val target = targetBitrate(stat.width, stat.height, stat.fps)
        // 源片已经够省：直接复制（无损那一半）
        if (stat.bitrate in 1..(target * 115 / 100)) {
            src.copyTo(dst, overwrite = true)
            return CompactResult(dst, dst.length(), false)
        }
        // 首选 Media3 Transformer（成熟开源链路，缩放/补边/旋转/降帧都由它处理）；
        // 输出没有真的变小就当作失败 —— 有的机型会「透传」视频轨，那种文件对用户毫无价值。
        val viaEngine = runCatching {
            VideoCompressor.compress(
                context = context,
                src = src,
                dst = dst,
                spec = VideoCompressor.Spec(
                    width = stat.width,
                    height = stat.height,
                    fps = stat.fps.coerceIn(1, 60),
                    bitrate = target,
                ),
            )
        }.getOrNull()?.takeIf { it.file.length() in 1 until src.length() }
        if (viaEngine != null) return CompactResult(viaEngine.file, viaEngine.file.length(), true)

        // 引擎没成功就原片直存。这里刻意不再接第二条自研 MediaCodec 转码兜底：
        // 那条老路把解码输出缓冲按紧凑 YUV 直接喂编码器，忽略行跨距与裁剪区，
        // 每行都会错开一点，成片正是「画面被切成几条错位碎片、中间夹黑块」。
        // 宁可不压，也绝不交付一段花屏视频。
        dst.delete()
        src.copyTo(dst, overwrite = true)
        return CompactResult(dst, dst.length(), false)
    }

    /**
     * 把相册选中的 Uri 复制进私有缓存。
     *
     * MediaCodec / MediaExtractor / ExifInterface 都只认本地路径，任何处理链之前先落地。
     */
    fun importToCache(context: Context, uri: Uri, isVideo: Boolean, dirName: String = "media_in"): File? = runCatching {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        val ext = when {
            mime.contains("mp4") -> "mp4"
            mime.contains("quicktime") -> "mov"
            mime.contains("heic") -> "heic"
            mime.contains("png") -> "png"
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            isVideo -> "mp4"
            else -> "jpg"
        }
        val dir = File(context.cacheDir, dirName).apply { mkdirs() }
        val file = File(dir, "src_${System.currentTimeMillis()}.$ext")
        resolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { out -> input.copyTo(out) }
        } ?: return null
        file
    }.getOrNull()

    /**
     * 从「动态照片」里抠出实况片段；普通照片返回 null。
     *
     * 定位交给 [MotionVideo]：先信 XMP 声明长度，再用盒链扫描兜底，
     * JPEG / HEIC 单文件动图、以及「图 + 同名视频」的分离式都能处理。
     */
    fun extractMotionPhoto(photo: File, destDir: File, prefix: String): File? = runCatching {
        if (!photo.exists() || photo.length() < 64 * 1024) return@runCatching null
        destDir.mkdirs()
        val ident = LivePhoto.identify(photo)
        ident.videoFile?.let { paired ->
            val out = File(destDir, "live_$prefix.mp4")
            paired.copyTo(out, overwrite = true)
            return@runCatching out
        }
        val out = File(destDir, "live_$prefix.mp4")
        MotionVideo.extract(photo, out, ident.embeddedVideoLen.takeIf { it > 0 })?.file ?: run {
            runCatching { out.delete() }
            null
        }
    }.getOrNull()

    /** 时长（秒），列表展示用；取不到返回 0 */
    fun durationSeconds(file: File): Int = runCatching {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            (ms / 1000L).toInt()
        } finally {
            runCatching { mmr.release() }
        }
    }.getOrDefault(0)

    private fun probe(file: File): VideoStat? = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/")) continue
                val w = f.getInteger(MediaFormat.KEY_WIDTH)
                val h = f.getInteger(MediaFormat.KEY_HEIGHT)
                val fps = if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    f.getInteger(MediaFormat.KEY_FRAME_RATE).coerceIn(1, 60)
                } else 30
                val bitrate = if (f.containsKey(MediaFormat.KEY_BIT_RATE)) f.getInteger(MediaFormat.KEY_BIT_RATE) else 0
                val duration = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else 0L
                return@runCatching VideoStat(w, h, fps, bitrate, duration)
            }
            null
        } finally {
            runCatching { extractor.release() }
        }
    }.getOrNull()

    /** 目标码率 = 宽×高×帧率×每像素比特，夹在合理区间，避免极端分辨率算飞 */
    private fun targetBitrate(width: Int, height: Int, fps: Int): Int {
        val raw = (width.toDouble() * height * fps * BITS_PER_PIXEL).toInt()
        return minOf(raw, MAX_BITRATE).coerceAtLeast(MIN_BITRATE)
    }


}
