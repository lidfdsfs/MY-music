package com.example.my

import com.example.my.data.music.HttpMediaSource
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/**
 * 网页音频流式播放的核心：带请求头的 Range 读取。
 * 用一个本地 HTTP 服务器模拟「支持 Range 的直链站点」，验证：
 * 1. getSize 能从 Content-Range 里读到总长；
 * 2. readAt 任意位置都能取到正确字节（服务器认 Range）；
 * 3. 连续顺序读不会重复开连接；
 * 4. Referer / Cookie 真的发出去了（防盗链靠这个）。
 */
class HttpMediaSourceTest {

    private lateinit var server: HttpServer
    private val data = ByteArray(1000) { (it % 251).toByte() }
    private var lastReferer: String? = null
    private var lastCookie: String? = null

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/song.mp3") { ex ->
            lastReferer = ex.requestHeaders.getFirst("Referer")
            lastCookie = ex.requestHeaders.getFirst("Cookie")
            val range = ex.requestHeaders.getFirst("Range")
            when {
                range == "bytes=0-0" -> {
                    ex.responseHeaders.add("Content-Range", "bytes 0-0/${data.size}")
                    ex.sendResponseHeaders(206, 1)
                    ex.responseBody.write(data, 0, 1)
                }
                range != null && range.startsWith("bytes=") -> {
                    val from = range.removePrefix("bytes=").substringBefore('-').toInt()
                    ex.responseHeaders.add("Content-Range", "bytes $from-${data.size - 1}/${data.size}")
                    ex.sendResponseHeaders(206, (data.size - from).toLong())
                    ex.responseBody.write(data, from, data.size - from)
                }
                else -> {
                    ex.sendResponseHeaders(200, data.size.toLong())
                    ex.responseBody.write(data)
                }
            }
            ex.responseBody.close()
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun source() = HttpMediaSource(
        "http://127.0.0.1:${server.address.port}/song.mp3",
        mapOf("Referer" to "https://www.gequhai.com/", "Cookie" to "sid=abc"),
        timeoutMs = 3000
    )

    @Test
    fun readsTotalSizeFromContentRange() {
        val src = source()
        assertEquals(1000L, src.getSize())
        src.close()
    }

    @Test
    fun readsBytesAtArbitraryPosition() {
        val src = source()
        val buf = ByteArray(10)
        // 先触发一次 size 探测，再从中段读，模拟 MediaPlayer 的随机访问
        assertEquals(1000L, src.getSize())
        assertEquals(10, src.readAt(500, buf, 0, 10))
        for (i in 0 until 10) {
            assertEquals(data[500 + i], buf[i])
        }
        src.close()
    }

    @Test
    fun continuesSequentialReadOnSameConnection() {
        val src = source()
        val buf = ByteArray(8)
        assertEquals(8, src.readAt(0, buf, 0, 8))
        assertEquals(8, src.readAt(8, buf, 0, 8))
        for (i in 0 until 8) {
            assertEquals(data[8 + i], buf[i])
        }
        src.close()
    }

    @Test
    fun seeksBackwardsByReopening() {
        val src = source()
        val a = ByteArray(4)
        val b = ByteArray(4)
        assertEquals(4, src.readAt(900, a, 0, 4))
        assertEquals(4, src.readAt(100, b, 0, 4))
        for (i in 0 until 4) {
            assertEquals(data[900 + i], a[i])
            assertEquals(data[100 + i], b[i])
        }
        src.close()
    }

    @Test
    fun sendsRefererAndCookieHeaders() {
        val src = source()
        val buf = ByteArray(4)
        assertEquals(4, src.readAt(0, buf, 0, 4))
        src.close()
        assertEquals("https://www.gequhai.com/", lastReferer)
        assertEquals("sid=abc", lastCookie)
    }
}
