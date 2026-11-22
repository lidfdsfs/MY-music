package com.example.my.data.news

import com.example.my.data.WebIncognito
import com.example.my.data.WebPrivacy
import com.example.my.data.ticket.TicketSecurity
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import javax.net.ssl.HttpsURLConnection

class NewsException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 资讯接口的传输层：和票务链路同一套收紧口径。
 *
 * - 只允许写死的 HTTPS 主机 + 443，禁跳转，不跟随 3xx 去第三方；
 * - 不发 Cookie / Referer / 设备标识，UA 用通用移动 Chrome；
 * - 带 DNT + Sec-GPC，不落缓存；
 * - 只收 JSON / 纯文本，HTML 错误页与脚本内容一律拒收；
 * - 响应体上限 512KB，超限断开。
 */
internal object NewsHttp {

    private const val MAX_BODY_BYTES = 512 * 1024

    fun getJson(url: String, allowedHosts: Set<String>): String =
        request("GET", url, allowedHosts, null)

    fun postJson(url: String, allowedHosts: Set<String>, body: String): String =
        request("POST", url, allowedHosts, body)

    private fun request(
        method: String,
        url: String,
        allowedHosts: Set<String>,
        body: String?
    ): String {
        if (!TicketSecurity.isAllowedHttps(url, allowedHosts)) {
            throw NewsException("资讯接口地址未通过安全校验")
        }
        val uri = runCatching { URI(url) }.getOrNull()
            ?: throw NewsException("资讯接口地址无效")
        var conn: HttpsURLConnection? = null
        try {
            conn = uri.toURL().openConnection() as? HttpsURLConnection
                ?: throw NewsException("资讯接口必须使用 HTTPS")
            conn.requestMethod = method
            conn.instanceFollowRedirects = false
            conn.doInput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 25_000
            WebIncognito.applyTo(conn)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Accept-Encoding", "identity")
            conn.setRequestProperty("User-Agent", WebPrivacy.STANDARD_UA)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                runCatching { conn.errorStream?.close() }
                throw NewsException(
                    when (code) {
                        429 -> "请求太频繁，请稍后再试"
                        403, 451, 503 -> "资讯服务暂时拒绝访问（$code）"
                        else -> "资讯服务暂时不可用（$code）"
                    }
                )
            }
            val type = conn.contentType.orEmpty().lowercase()
            if (!type.startsWith("application/json") && !type.startsWith("text/plain")) {
                throw NewsException("资讯服务返回了非数据内容，已拒绝解析")
            }
            val bytes = readAtMost(conn.inputStream)
                ?: throw NewsException("资讯内容过大，已拒绝解析")
            return String(bytes, Charsets.UTF_8)
        } catch (e: NewsException) {
            throw e
        } catch (e: Exception) {
            throw NewsException("网络连接失败，请检查网络后重试", e)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun readAtMost(stream: java.io.InputStream?): ByteArray? {
        if (stream == null) return ByteArray(0)
        stream.use {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var total = 0
            while (true) {
                val n = it.read(buf)
                if (n <= 0) break
                total += n
                if (total > MAX_BODY_BYTES) return null
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
