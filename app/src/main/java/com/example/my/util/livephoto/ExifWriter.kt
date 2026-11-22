package com.example.my.util.livephoto

import java.io.ByteArrayOutputStream

/**
 * 最小 EXIF 写入器。
 *
 * 原生侧没有「可写的 EXIF 库」（androidx ExifInterface 只能改有限标签，写不了 MakerNote/UserComment），
 * 所以这里现拼一个合法的 TIFF/EXIF：IFD0（Make/Model/Orientation/DateTime/Exif 指针）+
 * ExifIFD（UserComment / MakerNote / 像素尺寸 / 拍摄时间）。只写这几个标签，不解析外来数据。
 */
object ExifWriter {

    /** 想写进去的标签；null 表示不写 */
    class Tags(
        var make: String? = null,
        var model: String? = null,
        var userComment: String? = null,
        var makerNote: ByteArray? = null,
        var dateTime: String? = null,
        var pixelWidth: Int = 0,
        var pixelHeight: Int = 0,
        var orientation: Int = 1,
    )

    private const val TYPE_ASCII = 2
    private const val TYPE_SHORT = 3
    private const val TYPE_LONG = 4
    private const val TYPE_UNDEFINED = 7

    private const val TAG_MAKE = 0x010F
    private const val TAG_MODEL = 0x0110
    private const val TAG_ORIENTATION = 0x0112
    private const val TAG_DATETIME = 0x0132
    private const val TAG_EXIF_IFD = 0x8769

    private const val TAG_EXIF_DATETIME_ORIGINAL = 0x9003
    private const val TAG_MAKER_NOTE = 0x927C
    private const val TAG_USER_COMMENT = 0x9286
    private const val TAG_PIXEL_X = 0xA002
    private const val TAG_PIXEL_Y = 0xA003

    /** data 为 null 时把 inline 直接写进值字段 */
    private class Entry(val tag: Int, val type: Int, val count: Int, val data: ByteArray?, val inline: Int = 0)

    /** 返回完整的 APP1 段（含 "Exif\0\0"） */
    fun buildApp1(tags: Tags): ByteArray {
        val ifd0 = mutableListOf<Entry>()
        tags.make?.let { ifd0.add(Entry(TAG_MAKE, TYPE_ASCII, it.length + 1, ascii(it))) }
        tags.model?.let { ifd0.add(Entry(TAG_MODEL, TYPE_ASCII, it.length + 1, ascii(it))) }
        ifd0.add(Entry(TAG_ORIENTATION, TYPE_SHORT, 1, null, tags.orientation))
        tags.dateTime?.let { ifd0.add(Entry(TAG_DATETIME, TYPE_ASCII, it.length + 1, ascii(it))) }

        val exif = mutableListOf<Entry>()
        tags.dateTime?.let { exif.add(Entry(TAG_EXIF_DATETIME_ORIGINAL, TYPE_ASCII, it.length + 1, ascii(it))) }
        tags.userComment?.let {
            // UserComment 是 UNDEFINED，规范上前 8 字节是字符集；OPPO 只看正文里有没有 oplus 字样。
            val body = "ASCII\u0000\u0000\u0000".toByteArray(Charsets.US_ASCII) + ascii(it)
            exif.add(Entry(TAG_USER_COMMENT, TYPE_UNDEFINED, body.size, body))
        }
        tags.makerNote?.let { exif.add(Entry(TAG_MAKER_NOTE, TYPE_UNDEFINED, it.size, it)) }
        if (tags.pixelWidth > 0) exif.add(Entry(TAG_PIXEL_X, TYPE_LONG, 1, null, tags.pixelWidth))
        if (tags.pixelHeight > 0) exif.add(Entry(TAG_PIXEL_Y, TYPE_LONG, 1, null, tags.pixelHeight))
        exif.sortBy { it.tag }

        val ifd0BlockSize = 2 + (ifd0.size + 1) * 12 + 4
        val ifd0DataSize = extraDataSize(ifd0)
        val exifIfdOffset = 8 + ifd0BlockSize + ifd0DataSize
        val ifd0WithPointer = (ifd0 + Entry(TAG_EXIF_IFD, TYPE_LONG, 1, null, exifIfdOffset)).sortedBy { it.tag }

        val tiff = ByteArrayOutputStream()
        tiff.write('M'.code)
        tiff.write('M'.code)
        writeU16(tiff, 42)
        writeU32(tiff, 8)
        writeIfd(tiff, ifd0WithPointer, 8 + ifd0BlockSize)
        writeIfd(tiff, exif, exifIfdOffset + 2 + exif.size * 12 + 4)

        return JpegIo.buildApp1(JpegIo.EXIF_SIGNATURE + tiff.toByteArray())
    }

    /** 写 IFD 与它的变长数据区；[dataBase] 是数据区在 TIFF 内的绝对起点 */
    private fun writeIfd(out: ByteArrayOutputStream, entries: List<Entry>, dataBase: Int) {
        writeU16(out, entries.size)
        val data = ByteArrayOutputStream()
        for (e in entries) {
            writeU16(out, e.tag)
            writeU16(out, e.type)
            writeU32(out, e.count)
            val bytes = e.data
            when {
                bytes == null -> writeU32(out, e.inline)
                bytes.size <= 4 -> {
                    val pad = ByteArray(4)
                    System.arraycopy(bytes, 0, pad, 0, bytes.size)
                    out.write(pad)
                }
                else -> {
                    writeU32(out, dataBase + data.size())
                    data.write(bytes)
                    if (bytes.size % 2 == 1) data.write(0)
                }
            }
        }
        writeU32(out, 0)
        out.write(data.toByteArray())
    }

    private fun extraDataSize(entries: List<Entry>): Int = entries.sumOf { e ->
        val d = e.data
        if (d == null || d.size <= 4) 0 else d.size + (d.size % 2)
    }

    /** ASCII 类型必须以 NUL 结尾：count = 字符数 + 1，字节数必须与 count 一致，否则变长区偏移会整体错位 */
    private fun ascii(text: String): ByteArray = (text + "\u0000").toByteArray(Charsets.US_ASCII)

    private fun writeU16(out: ByteArrayOutputStream, v: Int) {
        out.write((v shr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    private fun writeU32(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 24) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    /**
     * Apple 实况照片的 MakerNote：iOS「照片」App 就在这里找 ContentIdentifier / PhotoIdentifier。
     * 结构照 pyheic-struct 的实现（大端 IFD + 变长数据区）。
     */
    fun appleMakerNote(contentId: String): ByteArray {
        val normalized = contentId.uppercase()
        class Item(val tag: Int, val type: Int, val count: Int, val value: Int, val data: ByteArray?)

        val items = listOf(
            Item(0x0001, 9, 1, 16, null),
            Item(0x0011, 2, normalized.length + 1, 0, (normalized + "\u0000").toByteArray(Charsets.US_ASCII)),
            Item(0x0014, 9, 1, 12, null),
            Item(0x0017, 16, 1, 0, ByteArray(8)),
            Item(0x001F, 9, 1, 0, null),
            Item(0x0020, 2, normalized.length + 1, 0, (normalized + "\u0000").toByteArray(Charsets.US_ASCII)),
            Item(0x002B, 2, normalized.length + 1, 0, (normalized + "\u0000").toByteArray(Charsets.US_ASCII)),
        )

        val out = ByteArrayOutputStream()
        out.write("Apple iOS".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0, 0, 1, 'M'.code.toByte(), 'M'.code.toByte()))
        writeU16(out, items.size)

        val dataOffsetBase = 14 + items.size * 12 + 4
        val variable = ByteArrayOutputStream()
        for (item in items) {
            writeU16(out, item.tag)
            writeU16(out, item.type)
            writeU32(out, item.count)
            if (item.data != null) {
                writeU32(out, dataOffsetBase + variable.size())
                variable.write(item.data)
                if (item.data.size % 2 == 1) variable.write(0)
            } else {
                writeU32(out, item.value)
            }
        }
        writeU32(out, 0)
        out.write(variable.toByteArray())
        return out.toByteArray()
    }
}
