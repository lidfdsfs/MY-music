package com.example.my.util.livephoto

import java.io.File
import java.io.RandomAccessFile

/**
 * 在「一张图 + 尾巴上一段视频」的单文件里定位并切出视频。
 *
 * 各家的动态照片本质都是同一个套路：主图正常结束（JPEG 是 `FF D9`，HEIC 是 ISO-BMFF 的盒链），
 * 后面直接拼一段完整的 MP4/MOV。差别只在「长度写没写、写在哪」：
 * - Google / 三星 / 小米：XMP `GCamera:MicroVideoOffset`（尾部字节数）或新格式 `Item:Length`；
 * - OPPO / realme：EXIF UserComment 里的 `oplus_8388608` + XMP；
 * - 华为 / 荣耀：XMP 有 `MotionPhoto=1`，长度常靠 `Item:Length`；
 * - 还有一批机器（尤其 HEIC 动态照片）干脆不写长度，只把 MP4 拼在后面。
 *
 * 所以这里做双保险：先信声明的长度，再用 **盒链扫描** 兜底 ——
 * 从文件尾部往前找 `ftyp`，逐个验证「这个位置往后能不能走出合法的 MP4 盒链并遇到 moov」。
 * 只靠 XMP 的正则会在声明缺失或声明错误时直接失败，而这正是「没有内嵌视频」误报的主因。
 */
object MotionVideo {

    /** 尾部视频在主文件里的位置 */
    data class Range(val offset: Long, val length: Long) {
        val end: Long get() = offset + length
    }

    class ExtractResult(val file: File, val range: Range, val byDeclaredLength: Boolean)

    /** 只扫尾部这么多字节：3~5 秒的实况片段很少超过这个量级，避免把几十 MB 的原图读进内存 */
    private const val SCAN_TAIL_BYTES = 16L * 1024 * 1024

    /** 太小的文件不可能是动态照片（小于这段没有 3 秒视频的容身之处） */
    private const val MIN_VIDEO_BYTES = 16 * 1024

    private val FTYP = byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())

    // ---------------- 定位 ----------------

    /**
     * 定位尾部视频；找不到返回 null。
     * [declared] 为 XMP/EXIF 里声明的视频字节数（没有则传 null）。
     */
    fun locate(file: File, declared: Long? = null): Range? = runCatching {
        val len = file.length()
        if (len <= MIN_VIDEO_BYTES.toLong()) return@runCatching null

        // 1) 先信声明：位置对得上 ftyp、且盒链里真有 moov 才算数
        if (declared != null && declared in MIN_VIDEO_BYTES.toLong() until len) {
            val start = len - declared
            if (startsWithFtyp(file, start)) {
                val range = Range(start, declared)
                if (walkHasMoov(file, range)) return@runCatching range
            }
        }

        // 2) 扫描兜底：从尾部往前找 ftyp，验证盒链
        val tailLen = minOf(len, SCAN_TAIL_BYTES).toInt()
        val base = len - tailLen
        val tail = ByteArray(tailLen)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(base)
            raf.readFully(tail)
        }
        val precededByEoi = mutableListOf<Long>()
        val others = mutableListOf<Long>()
        var idx = lastIndexOf(tail, FTYP, tail.size)
        while (idx >= 4) {
            val boxStart = idx - 4
            val start = base + boxStart
            if (start > 0 && start < len) {
                // JPEG 主图必然以 FF D9 结束，紧跟着才是视频：这一条能排除绝大多数误命中
                val eoi = boxStart >= 2 &&
                    tail[boxStart - 2] == 0xFF.toByte() && tail[boxStart - 1] == 0xD9.toByte()
                (if (eoi) precededByEoi else others).add(start)
            }
            idx = lastIndexOf(tail, FTYP, idx)
        }
        // 先试「紧跟 EOI」的候选（JPEG 动态照片的确定形态），没有再试其它（HEIC 动图没有 EOI）
        for (start in (precededByEoi.sorted() + others.sorted())) {
            val range = Range(start, len - start)
            if (range.length >= MIN_VIDEO_BYTES && walkHasMoov(file, range)) return@runCatching range
        }
        null
    }.getOrNull()

    /** 切出视频写到 [out]；[declared] 同上 */
    fun extract(file: File, out: File, declared: Long? = null): ExtractResult? = runCatching {
        val range = locate(file, declared) ?: return@runCatching null
        out.parentFile?.mkdirs()
        RandomAccessFile(file, "r").use { src ->
            src.seek(range.offset)
            out.outputStream().use { dst ->
                val buf = ByteArray(256 * 1024)
                var remaining = range.length
                while (remaining > 0L) {
                    val want = minOf(remaining, buf.size.toLong()).toInt()
                    val n = src.read(buf, 0, want)
                    if (n <= 0) break
                    dst.write(buf, 0, n)
                    remaining -= n
                }
            }
        }
        val byDeclared = declared != null && declared > 0 && range.offset == file.length() - declared
        if (!out.exists() || out.length() < MIN_VIDEO_BYTES) {
            out.delete()
            null
        } else {
            ExtractResult(out, range, byDeclared)
        }
    }.getOrNull()

    /** 文件在 [offset] 处是否正好是一个 MP4 头（`....ftyp`） */
    private fun startsWithFtyp(file: File, offset: Long): Boolean = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            val head = ByteArray(8)
            if (raf.read(head, 0, 8) < 8) return@runCatching false
            head[4] == FTYP[0] && head[5] == FTYP[1] && head[6] == FTYP[2] && head[7] == FTYP[3]
        }
    }.getOrDefault(false)

    /**
     * 从 [range] 的起点逐盒往后走，遇到 `moov` 就认为这是一段完整视频。
     *
     * 只检查 ftyp 是不够的：JPEG 的 EXIF/XMP 里也可能出现 "ftyp" 字样（相机型号、软件名），
     * 能一路走出 ftyp → mdat → moov 这样的盒链才说明它真是 MP4。
     */
    private fun walkHasMoov(file: File, range: Range): Boolean = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            var off = range.offset
            val end = range.end
            var guard = 0
            while (off + 8 <= end && guard++ < 64) {
                raf.seek(off)
                val head = ByteArray(16)
                val read = raf.read(head, 0, 8)
                if (read < 8) return@runCatching false
                var size = u32(head, 0)
                val type = String(head, 4, 4, Charsets.ISO_8859_1)
                if (type == "moov") return@runCatching true
                if (size == 1L) {
                    // 64 位盒大小：紧跟在标准头后面
                    val big = ByteArray(8)
                    if (raf.read(big, 0, 8) < 8) return@runCatching false
                    size = ((u32(big, 0).toLong() shl 32) or u32(big, 4).toLong())
                } else if (size == 0L) {
                    size = end - off // 一直到文件末尾
                }
                if (size < 8L) return@runCatching false
                off += size
            }
            false
        }
    }.getOrDefault(false)

    private fun u32(data: ByteArray, at: Int): Long =
        ((data[at].toLong() and 0xFF) shl 24) or
            ((data[at + 1].toLong() and 0xFF) shl 16) or
            ((data[at + 2].toLong() and 0xFF) shl 8) or
            (data[at + 3].toLong() and 0xFF)

    /** 从 [endExclusive] 往前找第一次出现的位置，找不到返回 -1 */
    private fun lastIndexOf(data: ByteArray, needle: ByteArray, endExclusive: Int): Int {
        var i = minOf(endExclusive, data.size) - needle.size
        while (i >= 0) {
            var match = true
            for (j in needle.indices) {
                if (data[i + j] != needle[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
            i--
        }
        return -1
    }
}
