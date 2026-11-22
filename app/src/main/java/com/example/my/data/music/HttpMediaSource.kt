package com.example.my.data.music

import android.media.MediaDataSource
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 带请求头的 HTTP 媒体源：让 MediaPlayer 能直接播网页直链，
 * 同时带上 Referer / Cookie / UA，绕过常见的防盗链。
 *
 * 用 Range 请求做随机读取，边播边取，不把整首歌下载到手机。
 * 服务器不认 Range 时退化为顺序读 + 手动跳过（仍可播，只是拖动会慢一点）。
 */
class HttpMediaSource(
    private val url: String,
    private val headers: Map<String, String>,
    private val timeoutMs: Int = 12_000
) : MediaDataSource() {

    private var conn: HttpURLConnection? = null
    private var stream: InputStream? = null

    /** 当前连接已经读到的绝对位置 */
    private var readPos: Long = 0L
    private var total: Long = -1L

    @Synchronized
    override fun getSize(): Long {
        if (total >= 0) return total
        total = probeSize()
        return total
    }

    /** 先用 Range: bytes=0-0 问一下总长度；问不到就 -1（未知，MediaPlayer 仍可播） */
    private fun probeSize(): Long = try {
        val c = open("bytes=0-0")
        val contentRange = c.getHeaderField("Content-Range")
        val rangeTotal = contentRange?.substringAfter('/', "-1")?.trim()?.toLongOrNull()
        val contentLength = c.getHeaderField("Content-Length")?.trim()?.toLongOrNull()
        val code = c.responseCode
        c.disconnect()
        when {
            rangeTotal != null && rangeTotal > 0 -> rangeTotal
            code == HttpURLConnection.HTTP_OK && contentLength != null && contentLength > 0 -> contentLength
            else -> -1L
        }
    } catch (_: Exception) {
        -1L
    }

    @Synchronized
    override fun readAt(pos: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size <= 0) return if (size == 0) 0 else -1
        return try {
            if (stream == null || pos < readPos) {
                reopen(pos)
            } else if (pos > readPos) {
                skipTo(pos)
            }
            val n = stream?.read(buffer, offset, size) ?: -1
            if (n > 0) readPos += n
            n
        } catch (_: Exception) {
            -1
        }
    }

    /** 从指定位置重新开一个 Range 连接 */
    private fun reopen(from: Long) {
        closeConnection()
        val c = open("bytes=$from-")
        conn = c
        val body = c.inputStream
        stream = body
        if (c.responseCode == HttpURLConnection.HTTP_PARTIAL) {
            readPos = from
        } else {
            // 服务器忽略 Range，返回整段：顺序读并丢掉前 from 字节
            readPos = 0L
            skipTo(from)
        }
    }

    /** 在同一连接里向前跳过若干字节（不认 Range 的兜底） */
    private fun skipTo(target: Long) {
        var remain = target - readPos
        val s = stream ?: return
        while (remain > 0) {
            val skipped = s.skip(remain)
            if (skipped > 0) {
                remain -= skipped
                readPos += skipped
            } else {
                // skip 无效时手动读掉
                if (s.read() < 0) return
                readPos += 1
                remain -= 1
            }
        }
    }

    private fun open(range: String?): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.instanceFollowRedirects = true
        c.setRequestProperty("Accept", "*/*")
        headers.forEach { (k, v) -> if (v.isNotBlank()) c.setRequestProperty(k, v) }
        if (range != null) c.setRequestProperty("Range", range)
        c.connect()
        return c
    }

    private fun closeConnection() {
        try { stream?.close() } catch (_: Exception) {}
        try { conn?.disconnect() } catch (_: Exception) {}
        stream = null
        conn = null
    }

    @Synchronized
    override fun close() {
        closeConnection()
        readPos = 0L
    }
}
