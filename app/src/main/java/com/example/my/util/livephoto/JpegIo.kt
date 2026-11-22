package com.example.my.util.livephoto

import java.io.ByteArrayOutputStream

/**
 * JPEG 段级工具：读 APP 段、插/替 APP1（EXIF / XMP）、切出尾部视频。
 *
 * 只做「段的重排」，不解析 MCU 熵编码；EXIF 的读用 androidx ExifInterface，
 * 写则用 [ExifWriter] 现拼（原生侧没有可写的 EXIF 库）。
 */
object JpegIo {

    const val MARKER_SOI = 0xD8
    const val MARKER_SOS = 0xDA
    const val MARKER_EOI = 0xD9
    val XMP_SIGNATURE = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
    val EXIF_SIGNATURE = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)

    /** 一个头部段：payload 是去掉标记与长度后的内容，raw 是原样字节 */
    class Seg(val name: String, val payload: ByteArray, val raw: ByteArray, val start: Int)

    fun isJpeg(data: ByteArray): Boolean = data.size > 3 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte()

    /** 把 JPEG 头部切段；返回 (段列表, 图像数据起点) */
    fun splitHeader(data: ByteArray): Pair<MutableList<Seg>, Int> {
        require(isJpeg(data)) { "不是 JPEG（缺 SOI）" }
        val segs = mutableListOf<Seg>()
        var i = 2
        while (i + 2 <= data.size) {
            if (data[i] != 0xFF.toByte()) break
            val marker = data[i + 1].toInt() and 0xFF
            if (marker == MARKER_SOS || marker == MARKER_EOI || marker == 0x00) break
            // 独立的短标记（RST0-7、TEM）没有长度字段
            if (marker in 0xD0..0xD7 || marker == 0x01) {
                segs.add(Seg("0xFF%02X".format(marker), ByteArray(0), data.copyOfRange(i, i + 2), i))
                i += 2
                continue
            }
            if (i + 4 > data.size) break
            val len = u16(data, i + 2)
            if (len < 2) break
            val end = i + 2 + len
            if (end > data.size) break
            val name = when (marker) {
                in 0xE0..0xEF -> "APP${marker - 0xE0}"
                0xDB -> "DQT"
                0xC4 -> "DHT"
                0xDD -> "DRI"
                else -> "0xFF%02X".format(marker)
            }
            segs.add(Seg(name, data.copyOfRange(i + 4, end), data.copyOfRange(i, end), i))
            i = end
        }
        return segs to i
    }

    fun xmpPackets(segs: List<Seg>): List<ByteArray> = segs.filter { it.name == "APP1" }
        .mapNotNull { seg -> if (startsWith(seg.payload, XMP_SIGNATURE)) seg.payload.copyOfRange(XMP_SIGNATURE.size, seg.payload.size) else null }

    fun exifPayload(segs: List<Seg>): ByteArray? {
        val seg = segs.firstOrNull { it.name == "APP1" && startsWith(it.payload, EXIF_SIGNATURE) } ?: return null
        return seg.payload.copyOfRange(EXIF_SIGNATURE.size, seg.payload.size)
    }

    fun buildApp1(payload: ByteArray): ByteArray {
        require(payload.size + 2 <= 0xFFFF) { "APP1 段超过 64KB" }
        val out = ByteArray(4 + payload.size)
        out[0] = 0xFF.toByte(); out[1] = 0xE1.toByte()
        putU16(out, 2, payload.size + 2)
        System.arraycopy(payload, 0, out, 4, payload.size)
        return out
    }

    /**
     * 把 APP1 段插到 APP0（以及紧随其后的 EXIF APP1）之后。
     *
     * 插入点 = 2(SOI) + 2(APP0 标记) + APP0 长度字段值。
     * 长度字段本身就包含那 2 字节，写成 2 + 长度值就会落进 APP0 段内部，被 JFIF 段吞掉。
     */
    fun insertAfterApp0(data: ByteArray, segment: ByteArray): ByteArray {
        val (segs, tail) = splitHeader(data)
        val head = ByteArrayOutputStream()
        head.write(data, 0, 2)
        var inserted = false
        for (seg in segs) {
            if (!inserted) {
                val isApp0 = seg.name == "APP0"
                val isExif = seg.name == "APP1" && startsWith(seg.payload, EXIF_SIGNATURE)
                if (isApp0 || isExif) {
                    head.write(seg.raw, 0, seg.raw.size)
                    continue
                }
                head.write(segment, 0, segment.size)
                inserted = true
            }
            head.write(seg.raw, 0, seg.raw.size)
        }
        if (!inserted) head.write(segment, 0, segment.size)
        head.write(data, tail, data.size - tail)
        return head.toByteArray()
    }

    fun strip(data: ByteArray, xmp: Boolean = true, exif: Boolean = false): ByteArray {
        val (segs, tail) = splitHeader(data)
        val head = ByteArrayOutputStream()
        head.write(data, 0, 2)
        for (seg in segs) {
            val isXmp = seg.name == "APP1" && startsWith(seg.payload, XMP_SIGNATURE)
            val isExif = seg.name == "APP1" && startsWith(seg.payload, EXIF_SIGNATURE)
            if (xmp && isXmp) continue
            if (exif && isExif) continue
            head.write(seg.raw, 0, seg.raw.size)
        }
        head.write(data, tail, data.size - tail)
        return head.toByteArray()
    }

    /**
     * 把「静图 + 尾部 MP4」切成两段。
     * [declaredLen] 来自 XMP（MotionPhoto 项长度 / MicroVideoOffset），XMP 缺失时就找尾部最后一个
     * ftyp 盒并用盒长度校验。
     */
    fun splitVideo(jpeg: ByteArray, declaredLen: Long? = null): Pair<ByteArray, ByteArray> {
        if (declaredLen != null && declaredLen > 0 && declaredLen < jpeg.size) {
            val start = (jpeg.size - declaredLen).toInt()
            val still = jpeg.copyOfRange(0, start)
            val video = jpeg.copyOfRange(start, jpeg.size)
            if (looksLikeMp4(video) && still.endsWithEoi()) return still to video
        }
        var idx = lastIndexOf(jpeg, "ftyp".toByteArray(Charsets.US_ASCII))
        while (idx != -1) {
            val start = idx - 4
            if (start > 2) {
                val size = ByteArray(4).also { System.arraycopy(jpeg, start, it, 0, 4) }
                // 盒大小是 32 位大端；更大的（>4GB）用 1 + 64 位大小，这里只处理常规情况
                val boxSize = u32(size, 0)
                if (boxSize >= 8 && start.toLong() + boxSize <= jpeg.size.toLong()) {
                    val still = jpeg.copyOfRange(0, start)
                    if (still.endsWithEoi()) {
                        return still to jpeg.copyOfRange(start, jpeg.size)
                    }
                }
            }
            idx = lastIndexOf(jpeg, "ftyp".toByteArray(Charsets.US_ASCII), idx)
        }
        return jpeg to ByteArray(0)
    }

    fun looksLikeMp4(data: ByteArray): Boolean =
        data.size > 12 && String(data, 4, 4, Charsets.US_ASCII) == "ftyp"

    fun lastIndexOf(data: ByteArray, needle: ByteArray, endExclusive: Int = data.size): Int {
        var i = minOf(endExclusive, data.size) - needle.size
        while (i >= 0) {
            var match = true
            for (j in needle.indices) if (data[i + j] != needle[j]) { match = false; break }
            if (match) return i
            i--
        }
        return -1
    }

    private fun ByteArray.endsWithEoi(): Boolean =
        size >= 2 && this[size - 2] == 0xFF.toByte() && this[size - 1] == MARKER_EOI.toByte()

    private fun startsWith(data: ByteArray, prefix: ByteArray): Boolean {
        if (data.size < prefix.size) return false
        for (i in prefix.indices) if (data[i] != prefix[i]) return false
        return true
    }

    fun u16(data: ByteArray, off: Int): Int = ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)

    fun u32(data: ByteArray, off: Int): Long =
        ((data[off].toLong() and 0xFF) shl 24) or ((data[off + 1].toLong() and 0xFF) shl 16) or
            ((data[off + 2].toLong() and 0xFF) shl 8) or (data[off + 3].toLong() and 0xFF)

    fun putU16(dst: ByteArray, off: Int, value: Int) {
        dst[off] = (value shr 8).toByte(); dst[off + 1] = value.toByte()
    }
}
