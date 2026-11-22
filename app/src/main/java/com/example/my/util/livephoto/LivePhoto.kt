package com.example.my.util.livephoto

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.example.my.util.MediaRemux
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * 动态照片（Live Photo / Motion Photo）的识别与转换。
 *
 * identify(file) → 判定属于哪一家；convert(file, target) → 生成目标平台相册能认的文件。
 * 解析走「文件头 + XMP/EXIF 字段 + 尾部 ftyp 盒」，不猜文件名，也不从头写 TIFF 解析器：
 * 读用 androidx ExifInterface，写用 [ExifWriter]。
 */
object LivePhoto {

    /** XMP 声明长度可能被写坏；尾部视频不会超过这个量级，超了直接判无效 */
    private const val MAX_EMBEDDED_VIDEO_BYTES = 64L * 1024 * 1024

    enum class Format(val zh: String, val en: String) {
        STATIC("普通静态图片", "Static image"),
        IOS_LIVE_PHOTO("iOS 实况照片（HEIC/JPG + MOV）", "iOS Live Photo"),
        ANDROID_MOTION_PHOTO("安卓通用动态照片", "Android Motion Photo"),
        OPPO_MOTION_PHOTO("OPPO / realme 动态照片", "OPPO Motion Photo"),
        HUAWEI_MOTION_PHOTO("华为 / 荣耀动态照片", "Huawei Motion Photo"),
        VIVO_SPLIT("vivo 分离式（JPG + MP4）", "vivo split"),
    }

    enum class Target(val zh: String, val en: String) {
        IOS("iOS 实况照片", "iOS Live Photo"),
        ANDROID("安卓通用", "Android generic"),
        OPPO("OPPO / realme", "OPPO / realme"),
        HUAWEI("华为 / 荣耀", "Huawei / HONOR"),
        VIVO("vivo 分离式", "vivo split"),
    }

    class Identification(
        val format: Format,
        val stillFile: File?,
        val videoFile: File?,
        val embeddedVideoLen: Long = 0,
        val make: String = "",
        val userComment: String = "",
        val reasons: List<String> = emptyList(),
    )

    class ConvertResult(val files: List<File>, val note: String)

    private const val OPPO_FLAG = "oplus_8388608"

    // ---------------- 识别 ----------------

    fun identify(file: File): Identification {
        val kind = MovMeta.sniff(file)
        return when (kind) {
            "heic" -> {
                // HEIC 也可能是「单文件动态照片」：Pixel 的 Motion Photo、部分小米/三星机型
                // 会把视频直接拼在 HEIC 盒链后面。旧实现只找同名 MOV，于是这类素材一律被当成
                // 「没有内嵌视频」。这里先用盒链扫描确认有没有尾巴视频，再退回配对文件判断。
                val embedded = MotionVideo.locate(file)
                val mov = sibling(file, listOf("MOV", "mov", "MP4", "mp4"))
                when {
                    embedded != null -> {
                        val exif = readExif(file.readBytes())
                        val format = vendorFormat(exif.make, exif.userComment)
                        Identification(
                            format, file, null, embedded.length, exif.make, exif.userComment,
                            reasons = listOf("HEIC 尾部内嵌视频 ${embedded.length / 1024}KB，判定为单文件动态照片"),
                        )
                    }

                    mov != null -> Identification(
                        Format.IOS_LIVE_PHOTO, file, mov,
                        reasons = listOf("HEIC + 同名 MOV，判定为 iOS 实况照片配对"),
                    )

                    else -> Identification(
                        // HEIC 基本只由 Apple 设备产生。系统相册选择器一次只回一个文件，
                        // 配对 MOV 不可能躺在缓存目录里，所以这里绝不能报「普通静态图片」——
                        // 否则用户只会看到一句无从下手的「没有内嵌视频」。
                        // 报成 iOS 实况，上层才能给出「连同 MOV 一起导出」这条可执行指引。
                        Format.IOS_LIVE_PHOTO, file, null,
                        reasons = listOf("HEIC 既没有内嵌视频也没有配对 MOV：iOS 实况是 HEIC + MOV 两个文件"),
                    )
                }
            }

            "mov", "mp4" -> {
                val image = sibling(file, listOf("HEIC", "heic", "JPG", "jpg", "JPEG", "jpeg"))
                when {
                    image == null -> Identification(Format.STATIC, file, null, reasons = listOf("只有视频，没有图片"))
                    kind == "mov" -> Identification(
                        Format.IOS_LIVE_PHOTO, image, file,
                        reasons = listOf("MOV + 同名静图，判定为 iOS 实况照片配对"),
                    )
                    else -> Identification(
                        Format.VIVO_SPLIT, image, file,
                        reasons = listOf("MP4 + 同名静图，判定为 vivo 分离式"),
                    )
                }
            }

            "jpeg" -> identifyJpeg(file)
            else -> Identification(Format.STATIC, file, null, reasons = listOf("无法识别的文件头（$kind）"))
        }
    }

    private fun identifyJpeg(file: File): Identification {
        val data = file.readBytes()
        val (segs, _) = JpegIo.splitHeader(data)
        val infos = JpegIo.xmpPackets(segs).map { XmpMotion.parse(it) }
        val info = infos.firstOrNull { it.motion } ?: infos.firstOrNull()

        val exif = readExif(data)
        val make = exif.make
        val comment = exif.userComment

        // 声明长度只是线索，最终位置交给盒链扫描确认（声明缺失或写错时同样能定位到视频）
        val declared = info?.let { XmpMotion.videoLength(it) }
        val range = MotionVideo.locate(file, declared)
        val embedded = range?.length ?: 0L

        if ((info?.motion == true) || embedded > 0) {
            return Identification(
                vendorFormat(make, comment), file, null, embedded, make, comment,
                listOf(
                    "尾部内嵌视频 ${embedded / 1024}KB" +
                        if (declared != null && declared == embedded) "（按 XMP 声明长度定位）" else "（按盒链扫描定位）",
                    "EXIF Make=${make.ifBlank { "未知" }}",
                ),
            )
        }

        val mp4 = sibling(file, listOf("MP4", "mp4"))
        if (mp4 != null) {
            return Identification(
                Format.VIVO_SPLIT, file, mp4, 0, make, comment,
                listOf("图片里没有内嵌视频，但同目录有同名 MP4，判定为 vivo 分离式"),
            )
        }
        return Identification(Format.STATIC, file, null, 0, make, comment, listOf("普通静态 JPEG"))
    }

    /** 按厂商特征归到对应的动态照片形态；认不出就按安卓通用处理 */
    private fun vendorFormat(make: String, comment: String): Format = when {
        comment.contains(OPPO_FLAG, ignoreCase = true) -> Format.OPPO_MOTION_PHOTO
        make.contains("OPPO", true) || make.contains("realme", true) -> Format.OPPO_MOTION_PHOTO
        make.contains("HUAWEI", true) || make.contains("HONOR", true) -> Format.HUAWEI_MOTION_PHOTO
        make.contains("Apple", true) -> Format.IOS_LIVE_PHOTO
        else -> Format.ANDROID_MOTION_PHOTO
    }

    // ---------------- 转换 ----------------

    fun convert(file: File, target: Target, outDir: File): ConvertResult {
        val ident = identify(file)
        if (ident.format == Format.STATIC && ident.videoFile == null) {
            throw IllegalArgumentException("这是一张静态图片，没有可用的动态视频")
        }
        val stillBytes = ident.stillFile?.readBytes() ?: throw IllegalArgumentException("读不到静图")
        val videoBytes = readVideo(ident)
        return writeTarget(stillBytes, videoBytes, ident, target, outDir, file.nameWithoutExtension)
    }

    /** 从任意「一张图 + 一段视频」直接合成目标平台的动态照片 */
    fun convertPair(still: File, video: File, target: Target, outDir: File, stem: String = still.nameWithoutExtension): ConvertResult =
        writeTarget(still.readBytes(), video.readBytes(), null, target, outDir, stem)

    private fun readVideo(ident: Identification): ByteArray {
        ident.videoFile?.let { f ->
            require(f.length() in 1..MAX_EMBEDDED_VIDEO_BYTES) { "配对视频大小异常" }
            return f.readBytes()
        }
        val src = ident.stillFile ?: throw IllegalArgumentException("没有源文件")
        val range = MotionVideo.locate(src, ident.embeddedVideoLen.takeIf { it > 0 })
            ?: throw IllegalArgumentException("这个动态照片里找不到尾部视频")
        return readRange(src, range)
    }

    /** 读文件里的一段；[MotionVideo.Range] 指向的正是内嵌视频 */
    private fun readRange(file: File, range: MotionVideo.Range): ByteArray {
        // 先卡住长度再分配：声明长度异常时 ByteArray(toInt()) 会溢出或直接 OOM
        require(range.length in 1..MAX_EMBEDDED_VIDEO_BYTES) { "内嵌视频长度异常，文件可能已损坏" }
        return file.inputStream().use { input ->
            var skipped = 0L
            while (skipped < range.offset) {
                val n = input.skip(range.offset - skipped)
                if (n <= 0) break
                skipped += n
            }
            val out = ByteArray(range.length.toInt())
            var read = 0
            while (read < out.size) {
                val n = input.read(out, read, out.size - read)
                if (n <= 0) break
                read += n
            }
            // 读不满就别交一段尾部补 0 的坏片子出去
            require(read == out.size) { "内嵌视频不完整，文件可能已损坏" }
            out
        }
    }

    private fun writeTarget(
        stillBytes: ByteArray,
        videoBytes: ByteArray,
        ident: Identification?,
        target: Target,
        outDir: File,
        stem: String,
    ): ConvertResult {
        outDir.mkdirs()
        return when (target) {
            Target.IOS -> {
                val uuid = UUID.randomUUID().toString().uppercase()
                val jpg = buildIosStill(stillBytes, uuid, ident)
                val jpgFile = File(outDir, "$stem.JPG").apply { writeBytes(jpg) }

                val tmpMp4 = File(outDir, "$stem.mov_tmp.mp4").apply { writeBytes(videoBytes) }
                val movFile = File(outDir, "$stem.MOV")
                val ok = MediaRemux.remux(tmpMp4, movFile) &&
                    MovMeta.writeContentIdentifier(movFile, uuid)
                tmpMp4.delete()
                if (!ok) {
                    movFile.delete()
                    throw IllegalStateException("视频重封装失败，无法写入 Live Photo 标识")
                }
                ConvertResult(listOf(jpgFile, movFile), "HEIC 编码器不在 App 内，iOS 走 JPG+MOV 配对（iOS 相册同样识别）")
            }

            Target.VIVO -> {
                val jpg = File(outDir, "$stem.JPG").apply { writeBytes(cleanStill(stillBytes, null, null)) }
                val mp4 = File(outDir, "$stem.MP4").apply { writeBytes(videoBytes) }
                ConvertResult(listOf(jpg, mp4), "vivo 是分离式：同名 JPG + MP4")
            }

            Target.ANDROID, Target.OPPO, Target.HUAWEI -> {
                val comment = if (target == Target.OPPO) OPPO_FLAG else null
                val make = when (target) {
                    Target.OPPO -> "OPPO"
                    Target.HUAWEI -> "HUAWEI"
                    else -> null
                }
                val data = buildMotionJpeg(stillBytes, videoBytes, comment, make)
                val out = File(outDir, "$stem.JPG").apply { writeBytes(data) }
                val note = when (target) {
                    Target.OPPO -> "已写入 EXIF UserComment=oplus_8388608，APP1 在 APP0 之后"
                    Target.HUAWEI -> "通用 Motion Photo 结构 + Make=HUAWEI"
                    else -> "单文件 Motion Photo：XMP 双写 Camera/GCamera 字段"
                }
                ConvertResult(listOf(out), note)
            }
        }
    }

    // ---------------- 具体封装 ----------------

    /** 安卓通用 / OPPO / 华为：JPEG + 尾部 MP4 + XMP */
    private fun buildMotionJpeg(
        stillBytes: ByteArray,
        videoBytes: ByteArray,
        userComment: String?,
        make: String?,
    ): ByteArray {
        var jpeg = cleanStill(stillBytes, make, userComment)
        // XMP 要写主图长度，而主图长度又包含 XMP 段自身：迭代到长度稳定
        var segment = ByteArray(0)
        repeat(4) {
            val primaryLen = jpeg.size + segment.size
            val packet = XmpMotion.build(primaryLen.toLong(), videoBytes.size.toLong())
            val newSegment = JpegIo.buildApp1(JpegIo.XMP_SIGNATURE + packet)
            if (newSegment.size == segment.size) {
                segment = newSegment
                return@repeat
            }
            segment = newSegment
        }
        return JpegIo.insertAfterApp0(jpeg, segment) + videoBytes
    }

    /** 去掉旧 XMP/exif，重建一份我们自己的 EXIF（顺带写厂商字段） */
    private fun cleanStill(stillBytes: ByteArray, make: String?, userComment: String?): ByteArray {
        val jpeg = toJpeg(stillBytes)
        val srcExif = readExif(jpeg)
        val stripped = JpegIo.strip(jpeg, xmp = true, exif = true)
        val tags = ExifWriter.Tags(
            make = make ?: srcExif.make.ifBlank { null },
            model = make ?: srcExif.model.ifBlank { null },
            userComment = userComment,
            dateTime = srcExif.dateTime,
            orientation = srcExif.orientation,
        )
        tags.pixelWidth = 0
        tags.pixelHeight = 0
        return JpegIo.insertAfterApp0(stripped, ExifWriter.buildApp1(tags))
    }

    private fun buildIosStill(stillBytes: ByteArray, uuid: String, ident: Identification?): ByteArray {
        val jpeg = toJpeg(stillBytes)
        val srcExif = readExif(jpeg)
        val stripped = JpegIo.strip(jpeg, xmp = true, exif = true)
        val tags = ExifWriter.Tags(
            make = "Apple",
            model = "iPhone",
            makerNote = ExifWriter.appleMakerNote(uuid),
            dateTime = srcExif.dateTime,
            orientation = srcExif.orientation,
        )
        return JpegIo.insertAfterApp0(stripped, ExifWriter.buildApp1(tags))
    }

    /** 非 JPEG 就用系统解码器转 JPEG（HEIC 在 API 28+ 才解得动） */
    private fun toJpeg(bytes: ByteArray): ByteArray {
        if (bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return bytes
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("这台手机解不开这张图（HEIC 需要 Android 9 以上）")
        val out = ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    // ---------------- EXIF 读 ----------------

    class ExifSnapshot(
        val make: String,
        val model: String,
        val userComment: String,
        val dateTime: String,
        val orientation: Int,
    )

    private fun readExif(bytes: ByteArray): ExifSnapshot = runCatching {
        val exif = ExifInterface(ByteArrayInputStream(bytes))
        ExifSnapshot(
            make = exif.getAttribute(ExifInterface.TAG_MAKE).orEmpty(),
            model = exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty(),
            userComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT).orEmpty(),
            dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME).orEmpty(),
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL),
        )
    }.getOrDefault(ExifSnapshot("", "", "", "", ExifInterface.ORIENTATION_NORMAL))

    private fun sibling(file: File, suffixes: List<String>): File? {
        val parent = file.parentFile ?: return null
        val stem = file.nameWithoutExtension
        return parent.listFiles()?.firstOrNull { candidate ->
            candidate.isFile && candidate != file && candidate.nameWithoutExtension == stem &&
                suffixes.any { candidate.extension.equals(it, ignoreCase = true) }
        }
    }
}
