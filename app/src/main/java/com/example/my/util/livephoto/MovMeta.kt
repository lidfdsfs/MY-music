package com.example.my.util.livephoto

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * 往 MOV/MP4 里写 `com.apple.quicktime.content.identifier`。
 *
 * iOS 把「实况照片」的两份文件按这个 UUID 配对：图片在 MakerNote 里写同一个 UUID，
 * 视频放在 moov/udta/meta（mdta 键表 + ilst）。
 *
 * 只在 moov 位于 mdat 之后（MediaMuxer / 相机 MOV 的常规布局）时改写：
 * 若 moov 在 mdat 之前（faststart），moov 变大后 mdat 样品偏移会整体位移，
 * 需要连带重算 stco —— 那种情况本方法返回 false，交给上层换条路径。
 */
object MovMeta {

    const val KEY = "com.apple.quicktime.content.identifier"

    data class Box(val type: String, val start: Int, val size: Int)

    fun boxes(data: ByteArray, from: Int = 0, to: Int = data.size): List<Box> {
        val list = mutableListOf<Box>()
        var off = from
        while (off + 8 <= to) {
            val size = JpegIo.u32(data, off).toInt()
            val type = String(data, off + 4, 4, Charsets.ISO_8859_1)
            if (size < 8 || off + size > to) break
            list.add(Box(type, off, size))
            off += size
        }
        return list
    }

    private fun payloadOf(data: ByteArray, box: Box): ByteArray =
        data.copyOfRange(box.start + 8, box.start + box.size)

    fun readIdentifier(data: ByteArray): String? {
        val moov = boxes(data).firstOrNull { it.type == "moov" } ?: return null
        val moovPayload = payloadOf(data, moov)
        val udtaPayload = boxes(moovPayload).firstOrNull { it.type == "udta" }?.let { payloadOf(moovPayload, it) }

        // meta 可能挂在 moov 下（QuickTime）也可能挂在 udta 下（iTunes 风格），两处都找
        val metaPayload = listOfNotNull(
            boxes(moovPayload).firstOrNull { it.type == "meta" }?.let { payloadOf(moovPayload, it) },
            udtaPayload?.let { ud -> boxes(ud).firstOrNull { it.type == "meta" }?.let { payloadOf(ud, it) } },
        ).firstOrNull() ?: return null

        // meta 是 full box，前 4 字节是 version/flags
        val metaChildren = boxes(metaPayload, 4)
        if (metaChildren.none { it.type == "keys" }) return null
        val ilst = metaChildren.firstOrNull { it.type == "ilst" } ?: return null
        val item = boxes(payloadOf(metaPayload, ilst)).firstOrNull() ?: return null
        val dataBox = boxes(payloadOf(payloadOf(metaPayload, ilst), item)).firstOrNull { it.type == "data" } ?: return null
        val bytes = payloadOf(payloadOf(payloadOf(metaPayload, ilst), item), dataBox)
        // data 盒前 8 字节是 type 指示与 locale
        if (bytes.size <= 8) return null
        return String(bytes, 8, bytes.size - 8, Charsets.UTF_8).trim('\u0000').ifBlank { null }
    }

    fun writeContentIdentifier(file: File, identifier: String): Boolean = runCatching {
        val data = file.readBytes()
        val top = boxes(data)
        val moov = top.firstOrNull { it.type == "moov" } ?: return false
        val mdat = top.firstOrNull { it.type == "mdat" }
        if (mdat != null && moov.start < mdat.start) return false // faststart：改写会破坏样品偏移

        val moovPayload = data.copyOfRange(moov.start + 8, moov.start + moov.size)
        val children = boxes(moovPayload)
        val udta = children.firstOrNull { it.type == "udta" }
        val filteredUdta = udta?.let { stripMetaWithKey(moovPayload, it, identifier) }

        val metaBox = buildMeta(identifier)
        val newPayload: ByteArray = if (udta == null) {
            moovPayload + box("udta", metaBox)
        } else {
            val udtaPayload = filteredUdta ?: moovPayload.copyOfRange(udta.start + 8, udta.start + udta.size)
            moovPayload.copyOfRange(0, udta.start) +
                box("udta", udtaPayload + metaBox) +
                moovPayload.copyOfRange(udta.start + udta.size, moovPayload.size)
        }
        val newMoov = box("moov", newPayload)

        // ftyp 主品牌改成 qt：iOS 对这个更宽容（大小不变，直接原位替换 4 字节）
        val patched = data.copyOf()
        val ftyp = top.firstOrNull { it.type == "ftyp" }
        if (ftyp != null && ftyp.size >= 12) {
            "qt  ".toByteArray(Charsets.ISO_8859_1).copyInto(patched, ftyp.start + 8)
        }

        val out = ByteArrayOutputStream()
        out.write(patched, 0, moov.start)
        out.write(newMoov)
        out.write(patched, moov.start + moov.size, patched.size - (moov.start + moov.size))
        file.writeBytes(out.toByteArray())

        // 读回校验：确保键名真的落进了文件
        val written = file.readBytes()
        JpegIo.lastIndexOf(written, KEY.toByteArray(Charsets.US_ASCII)) >= 0 &&
            JpegIo.lastIndexOf(written, identifier.toByteArray(Charsets.US_ASCII)) >= 0
    }.getOrDefault(false)

    /** 去掉已有的、带我们这份键的 meta 盒，避免重复键让播放器读错 */
    private fun stripMetaWithKey(moovPayload: ByteArray, udta: Box, identifier: String): ByteArray? {
        val udtaPayload = moovPayload.copyOfRange(udta.start + 8, udta.start + udta.size)
        val meta = boxes(udtaPayload).firstOrNull { it.type == "meta" } ?: return udtaPayload
        val metaText = String(meta.let { udtaPayload.copyOfRange(it.start, it.start + it.size) }, Charsets.ISO_8859_1)
        if (!metaText.contains(KEY)) return udtaPayload
        return udtaPayload.copyOfRange(0, meta.start) + udtaPayload.copyOfRange(meta.start + meta.size, udtaPayload.size)
    }

    private fun buildMeta(identifier: String): ByteArray {
        val hdlr = box(
            "hdlr",
            byteArrayOf(0, 0, 0, 0) +
                byteArrayOf(0, 0, 0, 0) +
                "mdta".toByteArray(Charsets.ISO_8859_1) +
                ByteArray(12)
        )
        val keyPayload = "mdta".toByteArray(Charsets.ISO_8859_1) + KEY.toByteArray(Charsets.US_ASCII)
        val keys = box(
            "keys",
            byteArrayOf(0, 0, 0, 0) +
                byteArrayOf(0, 0, 0, 1) +
                byteArrayOf(0, 0, 0, (keyPayload.size + 4).toByte()) +
                keyPayload
        )
        // ilst 的条目类型是「键的 1-based 序号」，这里就是 1
        val dataBox = box(
            "data",
            byteArrayOf(0, 0, 0, 1) +                 // type = 1（UTF-8）
                byteArrayOf(0, 0, 0, 0) +             // locale
                identifier.toByteArray(Charsets.US_ASCII)
        )
        val item = box(String(byteArrayOf(0, 0, 0, 1), Charsets.ISO_8859_1), dataBox)
        val ilst = box("ilst", item)
        return box("meta", byteArrayOf(0, 0, 0, 0) + hdlr + keys + ilst)
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArray(8 + payload.size)
        val size = out.size
        out[0] = (size ushr 24).toByte(); out[1] = (size ushr 16).toByte()
        out[2] = (size ushr 8).toByte(); out[3] = size.toByte()
        type.toByteArray(Charsets.ISO_8859_1).copyInto(out, 4, 0, 4)
        payload.copyInto(out, 8)
        return out
    }

    /** 读文件头 12 字节判断类型 */
    fun sniff(file: File): String {
        RandomAccessFile(file, "r").use { raf ->
            val head = ByteArray(minOf(raf.length(), 32L).toInt())
            raf.readFully(head)
            return when {
                head.size >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> "jpeg"
                head.size >= 12 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> {
                    val brand = String(head, 8, 4, Charsets.ISO_8859_1)
                    when (brand) {
                        "heic", "heix", "hevc", "hevx", "mif1", "msf1" -> "heic"
                        "qt  " -> "mov"
                        else -> "mp4"
                    }
                }
                else -> "unknown"
            }
        }
    }
}
