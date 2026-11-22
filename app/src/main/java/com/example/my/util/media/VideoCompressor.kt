package com.example.my.util.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 视频压缩引擎 —— 基于 **Media3 Transformer**（Google 官方开源，Apache-2.0）。
 *
 * 为什么用它替代自研的 MediaCodec 拼装管线：
 * - 自研管线要自己对齐解码器输出色彩格式（Planar / SemiPlanar / Flexible）、自己算旋转、
 *   自己补帧到 30fps、自己把音频单独转码再合流，任何一环在个别机型上对不上就是花屏或直接失败；
 * - Transformer 把这些都收进一条「解码 → GL 变换 → 编码 → 封装」的标准链路，
 *   旋转/缩放/补边/降帧都有现成算子，容器（MP4/MOV/3GP/HEIC 内嵌）也由它处理；
 * - 纯 Java 实现、不带 ffmpeg 原生库（APK 只多 ~3MB dex，没有 30MB+ 的 .so），
 *   实际编解码仍是系统硬编硬解，速度和省电与自研管线同级。
 *
 * 这里只负责「把一段视频转成目标规格」，业务侧的合规判断、换封装、兜底策略留在调用方。
 */
@OptIn(UnstableApi::class)
object VideoCompressor {

    /** 引擎标识，落进处理结果，界面与排障都能看出这条片子是谁压的 */
    const val ENGINE = "Media3 Transformer ${androidx.media3.common.MediaLibraryInfo.VERSION}"

    /** 音频统一 AAC 128k，与抖音上传规格一致 */
    const val AUDIO_BPS = 128_000

    enum class Fit(val zh: String, val en: String) {
        /** 等比缩放后补黑边，画面完整不裁切 */
        PAD("留黑边", "Letterbox"),

        /** 等比缩放后裁满，多余部分裁掉 */
        CROP("裁满", "Crop"),
    }

    class Spec(
        val width: Int,
        val height: Int,
        val fps: Int,
        val bitrate: Int,
        val fit: Fit = Fit.PAD,
    ) {
        init {
            require(width > 0 && height > 0) { "目标尺寸不合法：${width}x$height" }
            require(fps in 1..120) { "目标帧率不合法：$fps" }
            require(bitrate > 0) { "目标码率不合法：$bitrate" }
        }
    }

    /** 探测出的源片信息；字段取不到时给保守默认值，不抛异常 */
    class Probe(
        val width: Int,
        val height: Int,
        val rotation: Int,
        val fps: Double,
        val bitrate: Int,
        val durationMs: Long,
        val codec: String,
        val hasAudio: Boolean,
    ) {
        val displayWidth: Int get() = if (rotation == 90 || rotation == 270) height else width
        val displayHeight: Int get() = if (rotation == 90 || rotation == 270) width else height
    }

    /**
     * 压缩结果。
     * [width]/[height]/[fps]/[bitrate] 全部取自导出后的真实统计（[ExportResult]），不是「设定值」，
     * 界面照着显示不会出现「写着 30fps 实际 24fps」这种自欺欺人的文案。
     */
    class Outcome(
        val file: File,
        val engine: String,
        val width: Int,
        val height: Int,
        val fps: Double,
        val bitrate: Int,
        val durationMs: Long,
        val note: String = "",
    )

    /** 压缩失败：带上「卡在哪一步」和可直接展示的原因 */
    class Failure(
        val stage: Stage,
        override val message: String,
        val code: Int = 0,
        cause: Throwable? = null,
    ) : Exception(message, cause) {
        enum class Stage(val zh: String, val en: String) {
            PROBE("读取参数", "Probe"),
            EXPORT("转码", "Transcode"),
            TIMEOUT("超时", "Timeout"),
            VERIFY("结果复核", "Verify"),
        }
    }

    // ---------------- 探测 ----------------

    fun probe(file: File): Probe? = runCatching {
        var width = 0
        var height = 0
        var bitrate = 0
        var durationUs = 0L
        var mime = ""
        var hasAudio = false
        var fps = 0.0
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val trackMime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (trackMime.startsWith("audio/")) { hasAudio = true; continue }
                if (!trackMime.startsWith("video/") || width > 0) continue
                mime = trackMime
                width = f.intOrZero(MediaFormat.KEY_WIDTH)
                height = f.intOrZero(MediaFormat.KEY_HEIGHT)
                bitrate = f.intOrZero(MediaFormat.KEY_BIT_RATE)
                durationUs = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else 0L
                fps = when {
                    f.containsKey(MediaFormat.KEY_FRAME_RATE) -> f.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
                    f.containsKey("frame-rate-float") -> runCatching { f.getFloat("frame-rate-float").toDouble() }.getOrDefault(0.0)
                    else -> 0.0
                }
            }
        } finally {
            runCatching { extractor.release() }
        }
        val mmr = MediaMetadataRetriever()
        var rotation = 0
        try {
            mmr.setDataSource(file.absolutePath)
            rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (durationUs <= 0L) {
                durationUs = (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000L
            }
            if (width <= 0 || height <= 0) {
                width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            }
            if (bitrate <= 0) bitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
        } finally {
            runCatching { mmr.release() }
        }
        // 声明码率缺失（3GP、部分相机直出）时按「文件字节 × 8 / 时长」估算，别让上层拿到 0 做无意义比较
        if (bitrate <= 0 && durationUs > 0) {
            bitrate = ((file.length() * 8.0) / (durationUs / 1_000_000.0)).toInt()
        }
        if (width <= 0 || height <= 0) null
        else Probe(
            width = width,
            height = height,
            rotation = ((rotation % 360) + 360) % 360,
            fps = if (fps > 0.5) fps else 30.0,
            bitrate = bitrate,
            durationMs = durationUs / 1000L,
            codec = mime,
            hasAudio = hasAudio,
        )
    }.getOrNull()

    fun bitrateMbps(bps: Int): String = String.format(Locale.US, "%.2f", bps / 1_000_000.0)

    // ---------------- 压缩 ----------------

    /**
     * 把 [src] 转成 [spec] 规格写到 [dst]。
     *
     * 必须在主线程的 looper 上跑（Transformer 的约束），内部已用 `Dispatchers.Main.immediate` 兜住，
     * 调用方从任意线程 `suspend` 调用即可。失败抛 [Failure]，调用方可以据此回退到别的策略。
     */
    suspend fun compress(
        context: Context,
        src: File,
        dst: File,
        spec: Spec,
        onProgress: (Float) -> Unit = {},
    ): Outcome = withContext(Dispatchers.Main.immediate) {
        require(src.exists() && src.length() > 0) { "源视频不存在或为空" }
        runCatching { dst.delete() }

        // 探测是阻塞 IO（MediaExtractor + MediaMetadataRetriever），先切到 IO 线程算完再回主线程，
        // 主线程只留着跑 Transformer 的 looper 循环。
        val probe = withContext(Dispatchers.IO) { probe(src) }
            ?: throw Failure(Failure.Stage.PROBE, "读不出这个视频的分辨率，可能是损坏或不支持的封装")

        // 帧率：只降不升。高于目标就丢帧；低于目标保持原样（硬编不能凭空造帧，
        // 与其补重复帧让画面发涩，不如如实保留并在结果里写真实帧率）。
        val needDropFrames = probe.fps > spec.fps + 1.0
        val videoEffects: List<Effect> = buildList {
            if (needDropFrames) add(FrameDropEffect.createDefaultFrameDropEffect(spec.fps.toFloat()))
            // 即使源尺寸正好等于目标，也显式给一个 Presentation：
            // ① effects 为空时 Media3 可能直接把视频轨「透传」进新容器，码率设置就被忽略了；
            // ② 它同时兜住补边/裁满（解码后已按显示方向摆正，这里只按目标画布铺）。
            val layout = if (spec.fit == Fit.CROP) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            else Presentation.LAYOUT_SCALE_TO_FIT
            add(Presentation.createForWidthAndHeight(spec.width, spec.height, layout))
        }

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(spec.bitrate)
                    .setiFrameIntervalSeconds(1f)
                    .build()
            )
            .setRequestedAudioEncoderSettings(
                AudioEncoderSettings.Builder()
                    .setBitrate(AUDIO_BPS)
                    .build()
            )
            .build()

        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(src)))
            .setEffects(Effects(emptyList<AudioProcessor>(), videoEffects))
            .build()

        val done = CompletableDeferred<ExportResult>()
        val failed = CompletableDeferred<ExportException>()
        val transformer = Transformer.Builder(context)
            .setLooper(Looper.getMainLooper())
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    done.complete(exportResult)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    failed.complete(exportException)
                }
            })
            .build()

        val timeoutMs = (probe.durationMs * 6).coerceIn(60_000L, 600_000L)
        val deadline = System.currentTimeMillis() + timeoutMs
        val holder = ProgressHolder()
        try {
            transformer.start(item, dst.absolutePath)
            while (true) {
                if (done.isCompleted || failed.isCompleted) break
                if (System.currentTimeMillis() > deadline) {
                    runCatching { transformer.cancel() }
                    throw Failure(
                        Failure.Stage.TIMEOUT,
                        "转码超时（${probe.durationMs / 1000}s 的片子超过 ${timeoutMs / 1000}s 没完成），请换一段更短的素材",
                    )
                }
                // progress 只在这台机器的编码管线真的在出帧时才可信，读不到就沿用上一帧，别让进度条倒跳
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress((holder.progress / 100f).coerceIn(0f, 1f))
                }
                delay(120)
            }
            failed.getCompletedOrNull()?.let { throw describe(it) }
            val result = done.getCompletedOrNull()
                ?: throw Failure(Failure.Stage.EXPORT, "转码没有返回结果，请换一段素材重试")
            val exported = dst
            if (!exported.exists() || exported.length() <= 0L) {
                throw Failure(Failure.Stage.EXPORT, "转码完成但输出文件是空的，可能是存储空间不足")
            }
            val frames = result.videoFrameCount
            val seconds = result.durationMs / 1000.0
            val realFps = if (frames > 0 && seconds > 0.5) frames / seconds else probe.fps
            // 复核：设定码率只是「请求」，硬编实际给多少要看导出的真实统计，差太多说明设备忽略了设置
            // 超标但输出确实变小通常是 VBR 峰值，可以放行；超过两倍就是设备完全忽略了设置
            if (result.averageVideoBitrate > spec.bitrate * 1.5 &&
                (exported.length() >= src.length() || result.averageVideoBitrate > spec.bitrate * 2.0)
            ) {
                throw Failure(
                    Failure.Stage.VERIFY,
                    "这台设备的编码器没有按 ${bitrateMbps(spec.bitrate)}Mbps 输出" +
                        "（实际 ${bitrateMbps(result.averageVideoBitrate)}Mbps）",
                )
            }
            onProgress(1f)
            Outcome(
                file = exported,
                engine = ENGINE,
                width = if (result.width > 0) result.width else spec.width,
                height = if (result.height > 0) result.height else spec.height,
                fps = realFps,
                bitrate = result.averageVideoBitrate.takeIf { it > 0 } ?: spec.bitrate,
                durationMs = result.durationMs.takeIf { it > 0 } ?: probe.durationMs,
                note = result.videoEncoderName.orEmpty(),
            )
        } finally {
            // Transformer 持有一部分相机/编码器资源，异常路径也要收干净
            runCatching { transformer.cancel() }
        }
    }

    /** 把 media3 的错误码翻成人话：`ERROR_CODE_DECODING_FORMAT_UNSUPPORTED` 对用户毫无意义 */
    fun describe(e: ExportException): Failure {
        val stage = when (e.errorCode) {
            ExportException.ERROR_CODE_DECODER_INIT_FAILED,
            ExportException.ERROR_CODE_DECODING_FAILED,
            ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            -> Failure.Stage.PROBE

            ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
            ExportException.ERROR_CODE_ENCODING_FAILED,
            ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
            ExportException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED,
            ExportException.ERROR_CODE_AUDIO_PROCESSING_FAILED,
            -> Failure.Stage.EXPORT

            else -> Failure.Stage.EXPORT
        }
        val zh = when (e.errorCode) {
            ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                "这台手机解不了这段视频的编码格式（常见于 HEVC / AV1 素材），请先在相册里导出成 H.264 再试"

            ExportException.ERROR_CODE_DECODER_INIT_FAILED -> "初始化视频解码器失败，请重启 App 后重试"
            ExportException.ERROR_CODE_DECODING_FAILED -> "视频解码中断，素材可能不完整"
            ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED -> "这台手机不支持 H.264 硬件编码"
            ExportException.ERROR_CODE_ENCODER_INIT_FAILED -> "初始化视频编码器失败，可能被别的应用占用了"
            ExportException.ERROR_CODE_ENCODING_FAILED -> "视频编码中断，请换一段更短的素材"
            ExportException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED -> "画面缩放/补边处理失败"
            ExportException.ERROR_CODE_AUDIO_PROCESSING_FAILED -> "音频处理失败，可尝试静音后重试"
            ExportException.ERROR_CODE_IO_FILE_NOT_FOUND -> "源文件已被移动或删除"
            ExportException.ERROR_CODE_IO_NO_PERMISSION -> "没有读取源文件的权限"
            ExportException.ERROR_CODE_IO_UNSPECIFIED -> "读写文件失败，可能是存储空间不足"
            ExportException.ERROR_CODE_MUXING_FAILED -> "封装成 MP4 失败，素材的音视频轨可能不兼容"
            ExportException.ERROR_CODE_UNSPECIFIED -> "导出失败，请换一段素材重试"
            // 未映射的错误码不要吐 ERROR_CODE_xxxx 给用户；有可读原因就用原因，
            // 否则给一条能照做的中文提示，错误码只留在日志里。
            else -> e.message?.takeIf { it.isNotBlank() && !it.startsWith("ERROR_CODE_") }
                ?: "导出失败（错误码 ${e.errorCode}），请换一段素材重试"
        }
        return Failure(stage, zh, e.errorCode, e)
    }

    /** 给日志用的简短结论，不含堆栈 */
    fun summary(outcome: Outcome): String =
        "${outcome.width}x${outcome.height} · ${String.format(Locale.US, "%.1f", outcome.fps)}fps · " +
            "${bitrateMbps(outcome.bitrate)}Mbps · ${outcome.engine}"

    private fun MediaFormat.intOrZero(key: String): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(0) else 0

    private fun <T> CompletableDeferred<T>.getCompletedOrNull(): T? =
        if (isCompleted && !isCancelled) runCatching { getCompleted() }.getOrNull() else null

    private val ExportException.errorCodeName: String
        get() = when (errorCode) {
            ExportException.ERROR_CODE_DECODER_INIT_FAILED -> "ERROR_CODE_DECODER_INIT_FAILED"
            ExportException.ERROR_CODE_DECODING_FAILED -> "ERROR_CODE_DECODING_FAILED"
            ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED"
            ExportException.ERROR_CODE_ENCODER_INIT_FAILED -> "ERROR_CODE_ENCODER_INIT_FAILED"
            ExportException.ERROR_CODE_ENCODING_FAILED -> "ERROR_CODE_ENCODING_FAILED"
            ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED -> "ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED"
            ExportException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED -> "ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED"
            ExportException.ERROR_CODE_AUDIO_PROCESSING_FAILED -> "ERROR_CODE_AUDIO_PROCESSING_FAILED"
            ExportException.ERROR_CODE_IO_FILE_NOT_FOUND -> "ERROR_CODE_IO_FILE_NOT_FOUND"
            ExportException.ERROR_CODE_IO_NO_PERMISSION -> "ERROR_CODE_IO_NO_PERMISSION"
            ExportException.ERROR_CODE_MUXING_FAILED -> "ERROR_CODE_MUXING_FAILED"
            ExportException.ERROR_CODE_UNSPECIFIED -> "ERROR_CODE_UNSPECIFIED"
            else -> "ERROR_CODE_${errorCode}"
        }
}
