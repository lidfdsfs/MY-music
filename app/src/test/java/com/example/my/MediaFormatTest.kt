package com.example.my

import com.example.my.util.livephoto.ExifWriter
import com.example.my.util.livephoto.JpegIo
import com.example.my.util.livephoto.MovMeta
import com.example.my.util.livephoto.XmpMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 纯字节层的自测：这些逻辑不依赖 Android API，可以在 JVM 上真跑。
 * 覆盖：JPEG 段切分 / APP1 插入点 / EXIF 布局 / XMP 往返 / 尾部视频切分 / MOV 标识写入。
 */
class MediaFormatTest {

    private fun fakeJpeg(withExif: Boolean = true, withXmp: Boolean = false): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        // APP0 / JFIF(len=16)
        val app0 = ByteArray(16)
        app0[0] = 0xFF.toByte(); app0[1] = 0xE0.toByte()
        JpegIo.putU16(app0, 2, 14)
        "JFIF\u0000".toByteArray().copyInto(app0, 4)
        out.write(app0)
        if (withExif) {
            out.write(ExifWriter.buildApp1(ExifWriter.Tags(make = "OPPO", model = "PHK110", userComment = "oplus_8388608")))
        }
        if (withXmp) {
            out.write(JpegIo.buildApp1(JpegIo.XMP_SIGNATURE + XmpMotion.build(100, 4242)))
        }
        // SOS 段
        val sos = ByteArray(14)
        sos[0] = 0xFF.toByte(); sos[1] = 0xDA.toByte()
        JpegIo.putU16(sos, 2, 12)
        out.write(sos)
        out.write(ByteArray(64) { 0x11 })
        out.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        return out.toByteArray()
    }

    @Test
    fun `段切分遇到 SOS 停止`() {
        val data = fakeJpeg()
        val (segs, tail) = JpegIo.splitHeader(data)
        assertEquals(2, segs.size)
        assertEquals("APP0", segs[0].name)
        assertEquals("APP1", segs[1].name)
        assertTrue(data[tail] == 0xFF.toByte() && data[tail + 1] == 0xDA.toByte())
    }

    @Test
    fun `APP1 插在 APP0 与 EXIF 之后`() {
        val data = fakeJpeg(withExif = true)
        val xmp = JpegIo.buildApp1(JpegIo.XMP_SIGNATURE + XmpMotion.build(100, 4242))
        val merged = JpegIo.insertAfterApp0(data, xmp)
        val (segs, _) = JpegIo.splitHeader(merged)
        assertEquals(listOf("APP0", "APP1", "APP1"), segs.map { it.name })
        // 第一条 APP1 必须还是 EXIF（插段不能顶掉原来的 EXIF）
        assertTrue(JpegIo.exifPayload(listOf(segs[1])) != null)
        assertEquals(1, JpegIo.xmpPackets(segs).size)
        // APP1 的长度字段必须覆盖到段尾而不是吞进 JFIF
        assertEquals(xmp.size, segs[2].raw.size)
    }

    @Test
    fun `EXIF 布局可被重新解析`() {
        val tags = ExifWriter.Tags(
            make = "OPPO", model = "PHK110", userComment = "oplus_8388608",
            dateTime = "2026:11:14 12:00:00", orientation = 1,
        ).apply { pixelWidth = 1080; pixelHeight = 1920 }
        val app1 = ExifWriter.buildApp1(tags)
        val payload = app1.copyOfRange(4, app1.size)
        assertEquals(0xE1, app1[1].toInt() and 0xFF)
        assertEquals(app1.size - 2, JpegIo.u16(app1, 2))

        val tiff = payload.copyOfRange(6, payload.size)
        assertEquals('M'.code.toByte(), tiff[0])
        assertEquals('M'.code.toByte(), tiff[1])
        assertEquals(42, JpegIo.u16(tiff, 2))
        val ifd0 = JpegIo.u32(tiff, 4).toInt()
        val count = JpegIo.u16(tiff, ifd0)
        assertTrue("IFD0 条目应该 >= 4（Make/Model/Orientation/Exif 指针）", count >= 4)

        // 逐条遍历 IFD0，找 Exif 指针，再在 ExifIFD 里找 UserComment / MakerNote
        var exifOffset = -1
        for (i in 0 until count) {
            val entry = ifd0 + 2 + i * 12
            val tag = JpegIo.u16(tiff, entry)
            if (tag == 0x8769) exifOffset = JpegIo.u32(tiff, entry + 8).toInt()
            if (tag == 0x010F) {
                val off = JpegIo.u32(tiff, entry + 8).toInt()
                val make = String(tiff, off, JpegIo.u32(tiff, entry + 4).toInt() - 1, Charsets.US_ASCII)
                assertEquals("OPPO", make)
            }
        }
        assertTrue("必须有 Exif 子 IFD", exifOffset > 0)
        val exifCount = JpegIo.u16(tiff, exifOffset)
        var sawUserComment = false
        var sawPixelY = false
        for (i in 0 until exifCount) {
            val entry = exifOffset + 2 + i * 12
            when (JpegIo.u16(tiff, entry)) {
                0x9286 -> {
                    val off = JpegIo.u32(tiff, entry + 8).toInt()
                    val len = JpegIo.u32(tiff, entry + 4).toInt()
                    val text = String(tiff, off, len, Charsets.US_ASCII)
                    assertTrue("UserComment 里要有 OPPO 标记", text.contains("oplus_8388608"))
                    sawUserComment = true
                }
                0xA003 -> {
                    assertEquals(1920L, JpegIo.u32(tiff, entry + 8))
                    sawPixelY = true
                }
            }
        }
        assertTrue(sawUserComment)
        assertTrue(sawPixelY)
    }

    @Test
    fun `Apple MakerNote 含 ContentIdentifier`() {
        val uuid = "3F2504E0-4F89-11D3-9A0C-0305E82C3301"
        val note = ExifWriter.appleMakerNote(uuid)
        assertTrue(note.size > 20)
        assertEquals("Apple iOS", String(note, 0, 9, Charsets.US_ASCII))
        val text = String(note, Charsets.ISO_8859_1)
        assertTrue("MakerNote 内要有两处 UUID（ContentIdentifier 与 PhotoIdentifier）",
            text.split(uuid).size >= 3)
        // 条目数应等于写入的 7 个标签
        // 头部 14 字节（"Apple iOS" + 2 个 NUL + 0x01 + MM），随后是条目数
        assertEquals(7, JpegIo.u16(note, 14))
    }

    @Test
    fun `XMP 往返且视频长度一致`() {
        val packet = XmpMotion.build(1234, 987654)
        val info = XmpMotion.parse(packet)
        assertTrue(info.motion)
        assertEquals(2, info.items.size)
        assertEquals(1234L, info.items[0].length)
        assertEquals("MotionPhoto", info.items[1].semantic)
        assertEquals(987654L, XmpMotion.videoLength(info))
        assertEquals(987654L, info.microVideoOffset)
        assertTrue(String(packet).contains("Container:Directory"))
        assertTrue(String(packet).contains("GCamera:MicroVideoOffset=\"987654\""))
    }

    @Test
    fun `尾部视频能按 ftyp 切出来`() {
        val jpeg = fakeJpeg()
        val video = ByteArray(32).apply {
            // box: size=32, type=ftyp, 后面随便填
            this[3] = 32
            "ftyp".toByteArray(Charsets.US_ASCII).copyInto(this, 4)
        }
        val joined = jpeg + video
        val (still, cut) = JpegIo.splitVideo(joined)
        assertEquals(jpeg.size, still.size)
        assertEquals(video.size, cut.size)
        assertTrue(JpegIo.looksLikeMp4(cut))

        // XMP 里声明的长度优先
        val (still2, cut2) = JpegIo.splitVideo(joined, video.size.toLong())
        assertEquals(video.size, cut2.size)
        assertEquals(jpeg.size, still2.size)
    }

    @Test
    fun `MOV 里写入 content identifier`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "mov_test_${System.currentTimeMillis()}")
        dir.mkdirs()
        val file = File(dir, "test.mov")
        // ftyp(mp42) + mdat + moov(udta 里带一个空 udta 子盒)
        val ftyp = box("ftyp", "mp42".toByteArray() + byteArrayOf(0, 0, 0, 0) + "mp42isom".toByteArray())
        val mdat = box("mdat", ByteArray(16))
        val moov = box("moov", box("udta", ByteArray(0)))
        file.writeBytes(ftyp + mdat + moov)

        val uuid = "7C1B0F33-AAAA-4B33-9999-111122223333"
        assertTrue("写入应成功", MovMeta.writeContentIdentifier(file, uuid))
        val data = file.readBytes()
        assertEquals("qt  ", String(data, 8, 4, Charsets.ISO_8859_1))
        assertTrue(JpegIo.lastIndexOf(data, MovMeta.KEY.toByteArray()) >= 0)
        assertEquals(uuid, MovMeta.readIdentifier(data))
        assertTrue(MovMeta.sniff(file) == "mov")
        file.delete()
        dir.delete()
    }

    @Test
    fun `moov 在 mdat 之前拒绝改写`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "mov_test2_${System.currentTimeMillis()}")
        dir.mkdirs()
        val file = File(dir, "fast.mov")
        val ftyp = box("ftyp", "qt  ".toByteArray() + byteArrayOf(0, 0, 0, 0) + "qt  ".toByteArray())
        val moov = box("moov", box("udta", ByteArray(0)))
        val mdat = box("mdat", ByteArray(8))
        file.writeBytes(ftyp + moov + mdat)
        assertFalse(MovMeta.writeContentIdentifier(file, "ABCD-1234"))
        file.delete()
        dir.delete()
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArray(8 + payload.size)
        val size = out.size
        out[0] = (size ushr 24).toByte(); out[1] = (size ushr 16).toByte()
        out[2] = (size ushr 8).toByte(); out[3] = size.toByte()
        type.toByteArray(Charsets.ISO_8859_1).copyInto(out, 4)
        payload.copyInto(out, 8)
        return out
    }
}
