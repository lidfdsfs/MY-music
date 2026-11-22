package com.example.my.util.douyin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.example.my.util.MediaRemux
import com.example.my.util.media.VideoCompressor
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale

/**
 * 抖音上传前预处理。
 *
 * 规格：H.264 / MP4 / 1080×1920 / 30fps / 视频码率 ≤ 8Mbps / AAC 128kbps。
 *
 * 策略：
 * 1. 源已合规 → 只换封装，视频流一个字节都不重编码；
 * 2. 需要重编码 → 交给 Media3 Transformer（Google 官方开源，Apache-2.0）：
 *    解码 → 缩放/补边/旋转/降帧 → 硬件 H.264 + AAC 128k 一次出片，
 *    码率请求写成编码器参数（不支持的机型由它自动降级到可用档位）；
 *    机型上起不来时才回退到内置 MediaCodec 管线；
 * 3. 成片按「(文件字节 - 音频估算) * 8 / 时长」复核实际码率，超 8M 自动降档重编一次，
 *    仍超则报错而不是交付一个超标文件。
 */
object DouyinPrep {

    private const val TAG = "DouyinPrep"
    const val ENGINE_REMUX = "仅换封装"
    const val ENGINE_PLATFORM = "内置 MediaCodec"

    const val TARGET_W = 1080
    const val TARGET_H = 1920
    const val TARGET_FPS = 30
    const val MAX_BPS = 8_000_000
    const val SAFE_BPS = 7_600_000
    const val AUDIO_BPS = 128_000

    enum class Fit(val zh: String, val en: String) {
        PAD("留黑边", "Letterbox"),
        CROP("裁满", "Crop");

        fun toCompressor(): VideoCompressor.Fit =
            if (this == CROP) VideoCompressor.Fit.CROP else VideoCompressor.Fit.PAD
    }

    enum class Mode(val zh: String, val en: String) {
        REMUX("换封装（未重编码）", "Remuxed, no re-encode"),
        ENCODE("重新编码（硬编 H.264 + AAC）", "Re-encoded (HW H.264 + AAC)"),
    }

    class Info(
        val width: Int,
        val height: Int,
        val rotation: Int,
        val fps: Double,
        val bitrate: Int,
        val codec: String,
        val durationUs: Long,
        val hasAudio: Boolean,
        val audioMime: String,
        val audioBitrate: Int,
        val audioChannels: Int,
        val audioSampleRate: Int,
    ) {
        val displayWidth: Int get() = if (rotation == 90 || rotation == 270) height else width
        val displayHeight: Int get() = if (rotation == 90 || rotation == 270) width else height
    }

    class Result(
        val file: File,
        val mode: Mode,
        val info: Info,
        val videoBitrate: Int,
        val notes: List<String>,
        /** 谁压的：Media3 Transformer / 内置 MediaCodec / 仅换封装 */
        val engine: String = ENGINE_REMUX,
        /** 成片真实帧率（取导出统计，不是设定值） */
        val fps: Double = 0.0,
        /** 导出统计里的真实视频码率；仅换封装 / 内置管线没有这项统计时为 null */
        val realBitrate: Int? = null,
    )

    private class TrackPick(val videoIndex: Int, val videoFormat: MediaFormat?, val audioIndex: Int, val audioFormat: MediaFormat?)

    private class EncodeOutcome(
        val mode: Mode,
        val engine: String,
        val notes: List<String>,
        /** Media3 导出的真实统计；内置回退管线拿不到，交给上层用整片估算 */
        val realFps: Double? = null,
        val realBitrate: Int? = null,
    )

    fun analyze(file: File): Info? = runCatching {
        val extractor = MediaExtractor().apply { setDataSource(file.absolutePath) }
        var vf: MediaFormat? = null
        var af: MediaFormat? = null
        try {
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && vf == null) vf = f
                if (mime.startsWith("audio/") && af == null) af = f
            }
        } finally {
            runCatching { extractor.release() }
        }
        val video = vf ?: return null
        val rotation = runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(file.absolutePath)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            } finally {
                runCatching { mmr.release() }
            }
        }.getOrDefault(video.getIntegerOrZero(MediaFormat.KEY_ROTATION))
        val fps = video.frameRate()
        val durationUs = if (video.containsKey(MediaFormat.KEY_DURATION)) video.getLong(MediaFormat.KEY_DURATION)
        else runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(file.absolutePath)
                (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000L
            } finally {
                runCatching { mmr.release() }
            }
        }.getOrDefault(0L)
        val declaredBitrate = if (video.containsKey(MediaFormat.KEY_BIT_RATE)) video.getInteger(MediaFormat.KEY_BIT_RATE) else 0
        val estimated = if (durationUs > 0) ((file.length() * 8.0) / (durationUs / 1_000_000.0)).toInt() else 0
        // 宽高不能直接 getInteger：3GP、部分相机直出、以及只给 MAX_WIDTH 的轨道都会抛异常，
        // 一抛整条 analyze 就变 null，最终只显示一句无从排查的「读不出这个视频的参数」。
        val (width, height) = videoWidthHeight(file, video)
        if (width <= 0 || height <= 0) return null
        Info(
            width = width,
            height = height,
            rotation = rotation,
            fps = fps,
            bitrate = declaredBitrate.takeIf { it > 0 } ?: estimated,
            codec = video.getString(MediaFormat.KEY_MIME).orEmpty(),
            durationUs = durationUs,
            hasAudio = af != null,
            audioMime = af?.getString(MediaFormat.KEY_MIME).orEmpty(),
            audioBitrate = af?.let { if (it.containsKey(MediaFormat.KEY_BIT_RATE)) it.getInteger(MediaFormat.KEY_BIT_RATE) else 0 } ?: 0,
            audioChannels = af?.let { if (it.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) it.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2 } ?: 0,
            audioSampleRate = af?.let { if (it.containsKey(MediaFormat.KEY_SAMPLE_RATE)) it.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 48_000 } ?: 0,
        )
    }.getOrNull()

    fun issues(info: Info): List<String> = buildList {
        if (info.codec != "video/avc") add("编码是 ${info.codec.removePrefix("video/")}，需要 H.264")
        if (info.displayWidth != TARGET_W || info.displayHeight != TARGET_H) {
            add("分辨率 ${info.displayWidth}×${info.displayHeight}，需要 1080×1920")
        }
        if (info.fps < 29.0 || info.fps > 31.0) add("帧率 ${"%.2f".format(info.fps)}，需要 30fps")
        if (info.bitrate > MAX_BPS) add("码率 ${"%.2f".format(info.bitrate / 1_000_000.0)}Mbps > 8Mbps")
    }

    /** 目标目录里生成成片；[onProgress] 0..1 */
    suspend fun prepare(
        context: Context,
        src: File,
        dst: File,
        fit: Fit = Fit.PAD,
        onProgress: (Float) -> Unit = {},
    ): Result {
        val info = analyze(src) ?: throw IllegalArgumentException("读不出这个视频的参数")
        val problems = issues(info)
        val audioNeedsWork = info.hasAudio &&
            (info.audioMime != "audio/mp4a-latm" || info.audioBitrate > 160_000)

        if (problems.isEmpty() && !audioNeedsWork) {
            onProgress(0.15f)
            if (!MediaRemux.remux(src, dst)) throw IllegalStateException("换封装失败")
            val out = analyze(dst) ?: throw IllegalStateException("换封装后的文件读不出来")
            onProgress(1f)
            return Result(dst, Mode.REMUX, out, out.bitrate, listOf("源已合规，仅换封装"), ENGINE_REMUX, out.fps)
        }

        var vBitrate = SAFE_BPS
        var encoded = encodeOnce(context, src, dst, info, fit, vBitrate, onProgress)
        var out = analyze(dst) ?: throw IllegalStateException("成片读不出来")
        var measured = measuredVideoBitrate(dst, out)
        if (measured > MAX_BPS) {
            // 复核超线：降一档再编一次，仍超就失败，不交付超标文件
            vBitrate = (SAFE_BPS * 0.8).toInt()
            onProgress(0.2f)
            encoded = encodeOnce(context, src, dst, info, fit, vBitrate, onProgress)
            out = analyze(dst) ?: throw IllegalStateException("成片读不出来")
            measured = measuredVideoBitrate(dst, out)
            if (measured > MAX_BPS) throw IllegalStateException("压到 8Mbps 以下失败，请先裁剪素材时长")
        }
        onProgress(1f)
        return Result(
            dst, encoded.mode, out, measured,
            encoded.notes + "复核 ${"%.2f".format(Locale.US, measured / 1_000_000.0)}Mbps",
            encoded.engine,
            // 真源是 ExportResult 的 frames/seconds；只有内置回退管线才退回容器声明值。
            // measured 仍用于 8Mbps 合规复核（整片字节估算更保守），两者用途不同。
            encoded.realFps?.takeIf { it > 0.5 } ?: out.fps,
            encoded.realBitrate?.takeIf { it > 0 },
        )
    }

    /**
     * 一次完整出片：先走 Media3 Transformer，失败才退回内置 MediaCodec。
     *
     * 退回不是「兜底交差」，而是因为硬编机型确实存在差异：个别设备的 GL 变换链路起不来
     * （老 GPU / 缺 GLES3），此时内置管线走 Surface → Canvas → 紧凑 I420 的重编更稳。
     * 解码输出缓冲带行跨距填充和 crop rect，绝不能原样喂编码器（那样会花屏），
     * 所以两条路都只产出紧凑帧，日志里能看出到底是哪条生效。
     */
    private suspend fun encodeOnce(
        context: Context,
        src: File,
        dst: File,
        info: Info,
        fit: Fit,
        bitrate: Int,
        onProgress: (Float) -> Unit,
    ): EncodeOutcome {
        runCatching { dst.delete() }
        val spec = VideoCompressor.Spec(TARGET_W, TARGET_H, TARGET_FPS, bitrate, fit.toCompressor())
        val attempt = runCatching { VideoCompressor.compress(context, src, dst, spec, onProgress) }
        attempt.getOrNull()?.let { outcome ->
            return EncodeOutcome(
                Mode.ENCODE, outcome.engine,
                listOf(
                    "${outcome.width}×${outcome.height} · " +
                        "${"%.1f".format(Locale.US, outcome.fps)}fps · " +
                        "${VideoCompressor.bitrateMbps(outcome.bitrate)}Mbps",
                ),
                realFps = outcome.fps,
                realBitrate = outcome.bitrate,
            )
        }
        Log.w(TAG, "Media3 Transformer 转码失败，回退内置 MediaCodec 管线", attempt.exceptionOrNull())
        runCatching { dst.delete() }
        legacyEncode(context, src, dst, info, fit, bitrate, onProgress)
        return EncodeOutcome(
            Mode.ENCODE, ENGINE_PLATFORM,
            listOf("目标 ${bitrate / 1000}kbps（内置管线）"),
        )
    }

    /** 内置 MediaCodec 管线：视频轨重编 + 音频轨单独转 AAC 128k + 合流（Transformer 不可用时使用） */
    private fun legacyEncode(
        context: Context,
        src: File,
        dst: File,
        info: Info,
        fit: Fit,
        bitrate: Int,
        onProgress: (Float) -> Unit,
    ) {
        val problems = issues(info)
        val workDir = File(context.cacheDir, "douyin_prep").apply { mkdirs() }
        val videoPart = File(workDir, "video_part.mp4")
        val audioPart: File? = if (info.hasAudio) {
            File(workDir, "audio_part.m4a").also { target ->
                val ok = AudioTranscode.toAac(src, target, AUDIO_BPS)
                if (!ok) target.delete()
            }
        } else null

        if (problems.isEmpty()) {
            onProgress(0.35f)
            val tmp = File(workDir, "video_copy.mp4")
            MediaRemux.copyVideo(src, tmp) || throw IllegalStateException("视频流拷贝失败")
            tmp.copyTo(videoPart, overwrite = true)
            tmp.delete()
        } else {
            transcodeVideo(src, videoPart, info, fit, onProgress, bitrateOverride = bitrate)
        }

        onProgress(0.9f)
        val combined = File(workDir, "combined.mp4")
        if (!MediaRemux.mux(videoPart, audioPart, combined)) throw IllegalStateException("合流失败")
        combined.copyTo(dst, overwrite = true)
        videoPart.delete(); audioPart?.delete(); combined.delete()
    }

    /**
     * 取轨道宽高，取不到时逐级降级。
     *
     * 不能直接 `getInteger(KEY_WIDTH)`：3GP、部分相机直出、以及只给 MAX_WIDTH 的轨道都会抛异常，
     * 一抛整条 [analyze] 就变 null，最终只显示一句无从排查的「读不出这个视频的参数」。
     */
    private fun videoWidthHeight(file: File, format: MediaFormat): Pair<Int, Int> {
        fun read(key: String): Int =
            if (format.containsKey(key)) runCatching { format.getInteger(key) }.getOrDefault(0) else 0

        var w = read(MediaFormat.KEY_WIDTH)
        var h = read(MediaFormat.KEY_HEIGHT)
        if (w <= 0) w = read("coded-width")
        if (h <= 0) h = read("coded-height")
        if (w <= 0 || h <= 0) {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(file.absolutePath)
                w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            } catch (_: Exception) {
                // 读不到就当 0 交给调用方判定，绝不把异常抛回 analyze
            } finally {
                runCatching { mmr.release() }
            }
        }
        return w to h
    }

    private fun measuredVideoBitrate(file: File, info: Info): Int {
        val audioBits = if (info.hasAudio) info.durationUs / 1_000_000.0 * (info.audioBitrate.takeIf { it > 0 } ?: AUDIO_BPS) else 0.0
        val total = file.length() * 8.0
        val video = (total - audioBits).coerceAtLeast(0.0)
        return if (info.durationUs > 0) (video / (info.durationUs / 1_000_000.0)).toInt() else info.bitrate
    }

    // ---------------- 视频转码 ----------------

    private fun transcodeVideo(
        src: File,
        dst: File,
        info: Info,
        fit: Fit,
        onProgress: (Float) -> Unit,
        bitrateOverride: Int? = null,
    ) {
        val extractor = MediaExtractor().apply { setDataSource(src.absolutePath) }
        var videoTrack = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") && videoTrack < 0) { videoTrack = i; format = f }
        }
        val vf = format ?: throw IllegalStateException("没有视频轨")
        extractor.selectTrack(videoTrack)

        // 统一走「解码到 Surface → Canvas 变换 → 自己按紧凑布局写 YUV」这条路。
        // 旧的同尺寸直通分支把解码输出缓冲原样喂编码器：解码缓冲带行跨距填充和裁剪区，
        // 编码器却按紧凑的宽高逐行解读，每行都错开一点，成片就是花屏碎片 + 黑块；
        // 更麻烦的是它通常不抛异常，catch 兜底根本不会触发。已知会坏，就不留捷径。
        val bitrate = bitrateOverride ?: SAFE_BPS
        runCatching { dst.delete() }
        encodeWithTransform(extractor, vf, info, dst, fit, bitrate, onProgress)
        runCatching { extractor.release() }
    }

    /** 需要缩放 / 补边 / 旋转 / 改帧率：解码到 Surface，Canvas 变换后自己转 I420 喂编码器 */
    private fun encodeWithTransform(
        extractor: MediaExtractor,
        vf: MediaFormat,
        info: Info,
        dst: File,
        fit: Fit,
        bitrate: Int,
        onProgress: (Float) -> Unit,
    ) {
        val srcW = vf.getInteger(MediaFormat.KEY_WIDTH)
        val srcH = vf.getInteger(MediaFormat.KEY_HEIGHT)
        val reader = ImageReader.newInstance(srcW, srcH, PixelFormat.RGBA_8888, 3)
        val srcMime = vf.getString(MediaFormat.KEY_MIME) ?: throw IllegalStateException("视频轨没有 MIME")

        val decoder = MediaCodec.createDecoderByType(srcMime)
        decoder.configure(vf, reader.surface, null, 0)
        decoder.start()

        val colorFormat = pickColorFormat()
        val encFormat = MediaFormat.createVideoFormat("video/avc", TARGET_W, TARGET_H).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, TARGET_FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            applyCq(this)
        }
        val encoder = MediaCodec.createEncoderByType("video/avc")
        encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(dst.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val sink = EncoderSink(muxer)

        val canvasBitmap = Bitmap.createBitmap(TARGET_W, TARGET_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val yuv = ByteArray(TARGET_W * TARGET_H * 3 / 2)
        val pixels = IntArray(TARGET_W * TARGET_H)
        val scratch = FrameScratch()

        val decInfo = MediaCodec.BufferInfo()
        val encInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var decoderInputDone = false
        var eosQueued = false
        var eosPtsUs = 0L
        var encoderDone = false
        var outIndex = 0
        var accumulator = 0.0
        val fps = if (info.fps > 1) info.fps else 30.0
        val total = info.durationUs.coerceAtLeast(1L)
        val deadline = System.currentTimeMillis() + maxOf(180_000L, total / 1000 * 4)

        fun emit(timestampUs: Long) {
            toYuv(canvasBitmap, yuv, pixels, colorFormat)
            var encIn = encoder.dequeueInputBuffer(10_000)
            var tries = 0
            while (encIn < 0 && tries < 4) {
                sink.drain(encoder, encInfo)
                encIn = encoder.dequeueInputBuffer(10_000)
                tries++
            }
            if (encIn >= 0) {
                encoder.getInputBuffer(encIn)?.apply {
                    clear()
                    put(yuv)
                    encoder.queueInputBuffer(encIn, 0, yuv.size, timestampUs, 0)
                }
            }
        }

        try {
            while (!encoderDone) {
                if (System.currentTimeMillis() > deadline) throw IllegalStateException("转码超时，请换一段更短的素材")
                if (!decoderInputDone) {
                    val inIdx = decoder.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)
                        val size = inBuf?.let { extractor.readSampleData(it, 0) } ?: -1
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            decoderInputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var decIdx = decoder.dequeueOutputBuffer(decInfo, 10_000)
                if (decIdx >= 0) {
                    val eos = decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(decIdx, decInfo.size > 0)
                    if (decInfo.size > 0) {
                        val image = acquire(reader)
                        if (image != null) {
                            drawToCanvas(canvas, image, srcW, srcH, info.rotation, fit, paint, scratch)
                            image.close()
                            accumulator += TARGET_FPS / fps
                            while (accumulator >= 1.0) {
                                emit(outIndex * 1_000_000L / TARGET_FPS)
                                outIndex++
                                accumulator -= 1.0
                            }
                        }
                    }
                    if (eos) {
                        // 把最后不足一帧的部分补齐，避免尾帧丢失
                        if (accumulator > 0.0) { emit(outIndex * 1_000_000L / TARGET_FPS); outIndex++; accumulator = 0.0 }
                        eosPtsUs = outIndex * 1_000_000L / TARGET_FPS
                        inputDone = true
                    }
                    onProgress(0.15f + 0.7f * (decInfo.presentationTimeUs.toFloat() / total).coerceIn(0f, 1f))
                }
                if (inputDone && !eosQueued) {
                    val encIn = encoder.dequeueInputBuffer(10_000)
                    if (encIn >= 0) {
                        encoder.queueInputBuffer(encIn, 0, 0, eosPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eosQueued = true
                    }
                }
                encoderDone = sink.drain(encoder, encInfo) || encoderDone
            }
        } finally {
            runCatching { decoder.stop(); decoder.release() }
            runCatching { encoder.stop(); encoder.release() }
            runCatching { sink.stop() }
            runCatching { muxer.release() }
            runCatching { reader.close() }
            runCatching { canvasBitmap.recycle() }
            runCatching { scratch.bitmap?.recycle() }
        }
    }

    private fun acquire(reader: ImageReader): Image? {
        repeat(50) {
            val image = reader.acquireNextImage()
            if (image != null) return image
            Thread.sleep(10)
        }
        return null
    }

    /** 每帧复用同一张位图，避免 1080p 下每帧新建 8MB 带来的 GC 抖动 */
    private class FrameScratch {
        var bitmap: Bitmap? = null
        var widthPixels = 0
        var heightPixels = 0
    }

    private fun drawToCanvas(
        canvas: Canvas,
        image: Image,
        srcW: Int,
        srcH: Int,
        rotation: Int,
        fit: Fit,
        paint: Paint,
        scratch: FrameScratch,
    ) {
        val plane = image.planes[0]
        val rowPixels = plane.rowStride / plane.pixelStride
        if (scratch.bitmap == null || scratch.widthPixels != rowPixels || scratch.heightPixels != image.height) {
            scratch.bitmap?.recycle()
            scratch.bitmap = Bitmap.createBitmap(rowPixels, image.height, Bitmap.Config.ARGB_8888).also { bmp ->
                scratch.widthPixels = rowPixels
                scratch.heightPixels = image.height
            }
        }
        val bmp = scratch.bitmap ?: return
        plane.buffer.rewind()
        bmp.copyPixelsFromBuffer(plane.buffer)

        canvas.drawColor(Color.BLACK)
        canvas.save()
        when (rotation) {
            90 -> canvas.rotate(90f, TARGET_W / 2f, TARGET_H / 2f)
            180 -> canvas.rotate(180f, TARGET_W / 2f, TARGET_H / 2f)
            270 -> canvas.rotate(270f, TARGET_W / 2f, TARGET_H / 2f)
        }
        // 旋转后画布坐标系里的目标框宽高互换
        val boxW = if (rotation == 90 || rotation == 270) TARGET_H else TARGET_W
        val boxH = if (rotation == 90 || rotation == 270) TARGET_W else TARGET_H

        val srcRect = Rect(0, 0, srcW, srcH)
        val scale = when (fit) {
            Fit.PAD -> minOf(boxW.toFloat() / srcW, boxH.toFloat() / srcH)
            Fit.CROP -> maxOf(boxW.toFloat() / srcW, boxH.toFloat() / srcH)
        }
        val dstW = (srcW * scale).toInt()
        val dstH = (srcH * scale).toInt()
        val left = (boxW - dstW) / 2
        val top = (boxH - dstH) / 2
        canvas.drawBitmap(bmp, srcRect, Rect(left, top, left + dstW, top + dstH), paint)
        canvas.restore()
    }

    /** Bitmap(ARGB) → I420，供 COLOR_FormatYUV420Flexible 编码器使用 */
    private fun toI420(bitmap: Bitmap, out: ByteArray, pixels: IntArray) {
        val w = bitmap.width
        val h = bitmap.height
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val ySize = w * h
        val uvSize = w * h / 4
        var yIndex = 0
        var uIndex = ySize
        var vIndex = ySize + uvSize
        var i = 0
        for (row in 0 until h) {
            for (col in 0 until w) {
                val c = pixels[i++]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                out[yIndex++] = y.coerceIn(16, 235).toByte()
                if (row % 2 == 0 && col % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    out[uIndex++] = u.coerceIn(16, 240).toByte()
                    out[vIndex++] = v.coerceIn(16, 240).toByte()
                }
            }
        }
    }

    /**
     * 选编码器的输入颜色格式。
     *
     * 喂进编码器的是我们自己生成的 YUV，所以格式必须和 [toYuv] 写出的布局严格一致：
     * - Planar（I420）是唯一「布局确定、可逐字节对齐」的格式，优先选；
     * - Flexible 在绝大多数设备上等价 I420，次选；
     * - 只有前两者都不支持时才退到 SemiPlanar，并让 [toYuv] 改写成 NV12。
     *
     * 旧实现把 Flexible 排在第一位、SemiPlanar 也可能是返回值，但 [toI420] 永远按 Planar 平铺，
     * 于是选中 SemiPlanar 的机型上 UV 会整体错位：轻则成片花屏，重则写入越界直接失败。
     */
    private fun pickColorFormat(): Int {
        val info = findEncoder() ?: return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        val caps = runCatching { info.getCapabilitiesForType("video/avc") }.getOrNull() ?: return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        val supported = caps.colorFormats
        return when {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar in supported ->
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible in supported ->
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar in supported ->
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            else -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        }
    }

    /** Bitmap(ARGB) → 目标颜色格式的 YUV，布局必须和 [pickColorFormat] 的选择对齐 */
    private fun toYuv(bitmap: Bitmap, out: ByteArray, pixels: IntArray, colorFormat: Int) {
        if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
            toNv12(bitmap, out, pixels)
        } else {
            toI420(bitmap, out, pixels)
        }
    }

    /** ARGB → NV12：Y 平面后接 UV 交错半平面，供 SemiPlanar 编码器使用 */
    private fun toNv12(bitmap: Bitmap, out: ByteArray, pixels: IntArray) {
        val w = bitmap.width
        val h = bitmap.height
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val ySize = w * h
        var yIndex = 0
        var uvIndex = ySize
        var i = 0
        for (row in 0 until h) {
            for (col in 0 until w) {
                val c = pixels[i++]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                out[yIndex++] = y.coerceIn(16, 235).toByte()
                if (row % 2 == 0 && col % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    out[uvIndex++] = u.coerceIn(16, 240).toByte()
                    out[uvIndex++] = v.coerceIn(16, 240).toByte()
                }
            }
        }
    }

    private fun findEncoder(): MediaCodecInfo? = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull {
            it.isEncoder && it.supportedTypes.any { t -> t.equals("video/avc", true) }
        }
    }.getOrNull()

    /** 硬编码器支持恒定质量模式时优先用 CQ：低动态不浪费码率，高动态顶上限 */
    private fun applyCq(format: MediaFormat) {
        val encoder = findEncoder() ?: return
        val caps = runCatching { encoder.getCapabilitiesForType("video/avc").encoderCapabilities }.getOrNull() ?: return
        if (caps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)) {
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)
        } else if (caps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)) {
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        }
    }

    private fun MediaFormat.getIntegerOrZero(key: String): Int =
        if (containsKey(key)) getInteger(key) else 0

    private fun MediaFormat.frameRate(): Double {
        if (containsKey(MediaFormat.KEY_FRAME_RATE)) return getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
        val float = if (containsKey("frame-rate-float")) runCatching { getFloat("frame-rate-float") }.getOrNull() else null
        return float?.toDouble() ?: 30.0
    }

    /** 编码器输出 → Muxer；第一帧输出格式变化时加轨并 start */
    private class EncoderSink(private val muxer: MediaMuxer) {
        private var started = false
        private var track = -1

        fun drain(encoder: MediaCodec, info: MediaCodec.BufferInfo): Boolean {
            while (true) {
                when (val idx = encoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> if (!started) {
                        track = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        started = true
                    }
                    else -> if (idx >= 0) {
                        val buf = encoder.getOutputBuffer(idx)
                        val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!config && info.size > 0 && started && buf != null) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        encoder.releaseOutputBuffer(idx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                    }
                }
            }
        }

        fun stop() {
            if (started) muxer.stop()
        }
    }
}
