package com.example.my

import com.example.my.util.livephoto.ExifWriter
import com.example.my.util.livephoto.JpegIo
import com.example.my.util.livephoto.XmpMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 用真实素材（tools/media-prep/_test 里的 still.jpg 与 clip_motion.mp4）走一遍生产代码里的
 * 组装逻辑，产物落到 app/build/tmp/kotlin_artifacts，交给 Python 侧独立解析器复核。
 *
 * 这是「没有真机时最接近真机的验证」：段结构、EXIF 布局、XMP 长度一致性都在这里定型。
 */
class MotionPhotoArtifactTest {

    private val root = File(System.getProperty("user.dir").orEmpty()).let {
        if (File(it, "tools/media-prep/_test").exists()) it else it.parentFile
    }

    private fun outDir(): File = File(root, "app/build/tmp/kotlin_artifacts").apply { mkdirs() }

    private fun stillBytes(): ByteArray = File(root, "tools/media-prep/_test/still.jpg").readBytes()

    private fun videoBytes(): ByteArray = File(root, "tools/media-prep/_test/clip_motion.mp4").readBytes()

    /** 与 LivePhoto.buildMotionJpeg 同一套步骤 */
    @Test
    fun `组装安卓通用 Motion Photo`() {
        val still = stillBytes()
        val video = videoBytes()
        var jpeg = JpegIo.strip(still, xmp = true, exif = true)
        jpeg = JpegIo.insertAfterApp0(
            jpeg,
            ExifWriter.buildApp1(
                ExifWriter.Tags(make = "Xiaomi", model = "2211133C", dateTime = "2026:11:14 18:40:00", orientation = 1)
            )
        )
        var segment = ByteArray(0)
        repeat(4) {
            val primaryLen = jpeg.size + segment.size
            segment = JpegIo.buildApp1(
                JpegIo.XMP_SIGNATURE + XmpMotion.build(primaryLen.toLong(), video.size.toLong())
            )
        }
        val merged = JpegIo.insertAfterApp0(jpeg, segment)
        val file = File(outDir(), "android_motion.jpg")
        file.writeBytes(merged + video)

        // 结构自检：段顺序、XMP 里的主图长度必须等于合并后的真实字节数
        val (segs, _) = JpegIo.splitHeader(file.readBytes())
        assertEquals(listOf("APP0", "APP1", "APP1"), segs.take(3).map { it.name })
        val info = XmpMotion.parse(JpegIo.xmpPackets(segs).first())
        assertEquals(merged.size.toLong(), info.items.first().length)
        assertEquals(video.size.toLong(), XmpMotion.videoLength(info))

        // 尾部视频还能原样切回来
        val (still2, video2) = JpegIo.splitVideo(file.readBytes(), XmpMotion.videoLength(info))
        assertEquals(merged.size, still2.size)
        assertEquals(video.size, video2.size)
    }

    /** OPPO：多一条 UserComment 标记，其余同上 */
    @Test
    fun `组装 OPPO Motion Photo`() {
        val still = stillBytes()
        val video = videoBytes()
        var jpeg = JpegIo.strip(still, xmp = true, exif = true)
        jpeg = JpegIo.insertAfterApp0(
            jpeg,
            ExifWriter.buildApp1(
                ExifWriter.Tags(make = "OPPO", model = "PHK110", userComment = "oplus_8388608", orientation = 1)
            )
        )
        var segment = ByteArray(0)
        repeat(4) {
            segment = JpegIo.buildApp1(
                JpegIo.XMP_SIGNATURE + XmpMotion.build((jpeg.size + segment.size).toLong(), video.size.toLong())
            )
        }
        File(outDir(), "oppo_motion.jpg").writeBytes(JpegIo.insertAfterApp0(jpeg, segment) + video)
    }

    /** iOS 静图：Apple MakerNote 里带 UUID */
    @Test
    fun `组装 iOS 静图`() {
        val still = stillBytes()
        val uuid = "9F1D2C3B-4A5E-6789-ABCD-EF0123456789"
        var jpeg = JpegIo.strip(still, xmp = true, exif = true)
        jpeg = JpegIo.insertAfterApp0(
            jpeg,
            ExifWriter.buildApp1(
                ExifWriter.Tags(
                    make = "Apple", model = "iPhone",
                    makerNote = ExifWriter.appleMakerNote(uuid),
                    dateTime = "2026:11:14 18:40:00", orientation = 1
                )
            )
        )
        val file = File(outDir(), "ios.jpg")
        file.writeBytes(jpeg)
        val (segs, _) = JpegIo.splitHeader(file.readBytes())
        assertTrue(JpegIo.exifPayload(segs) != null)
    }
}
