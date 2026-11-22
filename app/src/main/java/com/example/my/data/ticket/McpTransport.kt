package com.example.my.data.ticket

import com.example.my.data.WebPrivacy
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/**
 * 票务 MCP 的传输层。
 *
 * 这里刻意不复用 App 里的通用 HttpURLConnection 封装，而是把约束收紧到最小：
 * - 只允许固定 HTTPS 主机、443 端口，禁跳转（防止被 302 到别的域）；
 * - 不读取/保存 Cookie，不带 Referer、不带设备标识，UA 用通用 Chrome；
 * - 只接受 application/json / text/event-stream，HTML 错误页直接拒收；
 * - 响应体硬上限 512KB，超限即断开；
 * - 解析只走 JSON，不执行任何返回的 HTML/脚本。
 *
 * 能做到的是「不主动交出身份与设备指纹、不让响应变成可执行内容」；
 * 出口 IP 属于网络层，App 本身无法隐藏，UI 里会如实写明。
 */
object TicketNetGuard {

    const val MAX_BODY_BYTES = 512 * 1024

    fun postJson(
        endpoint: String,
        allowedHosts: Set<String>,
        body: String,
        readTimeoutMs: Int = 35_000
    ): String {
        if (!TicketSecurity.isAllowedHttps(endpoint, allowedHosts)) {
            throw TicketException("票务接口地址未通过安全校验")
        }
        val uri = runCatching { URI(endpoint) }.getOrNull()
            ?: throw TicketException("票务接口地址无效")
        var conn: HttpsURLConnection? = null
        try {
            conn = (uri.toURL().openConnection() as? HttpsURLConnection)
                ?: throw TicketException("票务接口必须使用 HTTPS")
            conn.requestMethod = "POST"
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.doInput = true
            conn.doOutput = true
            conn.connectTimeout = 12_000
            conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("Accept", "application/json, text/event-stream")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept-Encoding", "identity")
            conn.setRequestProperty("Cache-Control", "no-store, no-cache")
            conn.setRequestProperty("Pragma", "no-cache")
            conn.setRequestProperty("DNT", "1")
            conn.setRequestProperty("Sec-GPC", "1")
            // 不设置 Cookie 请求头就是「不带 Cookie」；不要发空值头，
            // 部分 HTTP 栈会直接拒绝空 header value。
            conn.setRequestProperty("User-Agent", WebPrivacy.STANDARD_UA)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                runCatching { conn.errorStream?.close() }
                throw TicketException(
                    when (code) {
                        429 -> "查询太频繁，请稍后再试"
                        403, 503 -> "票务服务暂时拒绝访问，请稍后再试"
                        else -> "票务服务暂时不可用（$code）"
                    }
                )
            }
            val contentType = conn.contentType.orEmpty().lowercase()
            if (!contentType.startsWith("application/json") &&
                !contentType.startsWith("text/event-stream")
            ) {
                throw TicketException("票务服务返回了非数据内容，已拒绝解析")
            }
            val bytes = readAtMost(conn.inputStream, MAX_BODY_BYTES)
                ?: throw TicketException("票务服务返回内容过大，已拒绝解析")
            return String(bytes, Charsets.UTF_8)
        } catch (e: TicketException) {
            throw e
        } catch (e: Exception) {
            throw TicketException("网络连接失败，请检查网络后重试", e)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun readAtMost(stream: java.io.InputStream?, max: Int): ByteArray? {
        if (stream == null) return ByteArray(0)
        stream.use {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var total = 0
            while (true) {
                val n = it.read(buf)
                if (n <= 0) break
                total += n
                if (total > max) return null
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}

/** MCP Streamable HTTP 的 SSE/JSON-RPC 解析。 */
object McpSse {

    /** 取出所有 SSE data 事件；没有 SSE 时返回空列表，由调用方回退成纯 JSON。 */
    fun dataEvents(raw: String): List<String> {
        val out = mutableListOf<String>()
        val data = StringBuilder()
        var hasData = false

        fun flush() {
            if (hasData && data.isNotEmpty()) out.add(data.toString())
            data.setLength(0)
            hasData = false
        }

        raw.lineSequence().forEach { line ->
            when {
                line.startsWith("data:") -> {
                    if (hasData) data.append('\n')
                    data.append(line.substring(5).trimStart())
                    hasData = true
                }
                line.isBlank() -> flush()
                // event:/id:/retry: 只是 SSE 元数据，不需要进入 JSON
                else -> Unit
            }
        }
        flush()
        return out
    }

    private fun parseObject(text: String): JsonObject? =
        runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()

    fun parseResult(raw: String): JsonObject {
        val candidates = dataEvents(raw).ifEmpty { listOf(raw) }
        for (event in candidates) {
            val obj = parseObject(event) ?: continue
            obj.get("error")?.let { err ->
                if (!err.isJsonNull) throw TicketException(errorMessage(err))
            }
            obj.get("result")?.takeIf { !it.isJsonNull }?.let {
                return it.asJsonObject
            }
        }
        throw TicketException("票务服务返回格式异常")
    }

    /**
     * 调用一个 MCP 工具，并把 `result.content[0].text` 这个 JSON 字符串解析成对象。
     * 兼容部分服务返回 `structuredContent.result` 的情况。
     */
    fun callTool(
        endpoint: String,
        allowedHosts: Set<String>,
        tool: String,
        arguments: JsonObject
    ): JsonObject {
        val request = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", 1)
            addProperty("method", "tools/call")
            add("params", JsonObject().apply {
                addProperty("name", tool)
                add("arguments", arguments)
            })
        }
        val raw = TicketNetGuard.postJson(endpoint, allowedHosts, request.toString())
        val result = parseResult(raw)
        if (result.get("isError")?.let { !it.isJsonNull && it.asBoolean } == true) {
            throw TicketException(errorMessage(result.get("content")))
        }
        val text = contentText(result)
        if (text.isNotBlank()) {
            return parseObject(text) ?: throw TicketException("票务服务返回内容无法解析")
        }
        val structured = result.getAsJsonObject("structuredContent")
            ?.get("result")?.takeIf { it.isJsonPrimitive }?.asString
        if (!structured.isNullOrBlank()) {
            return parseObject(structured) ?: throw TicketException("票务服务返回内容无法解析")
        }
        throw TicketException("票务服务没有返回数据")
    }

    private fun contentText(result: JsonObject): String {
        val content = result.getAsJsonArray("content") ?: return ""
        val first = content.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return ""
        val text = first.get("text")?.takeIf { it.isJsonPrimitive }?.asString ?: return ""
        return text
    }

    private fun errorMessage(element: JsonElement?): String {
        if (element == null || element.isJsonNull) return "票务服务暂时不可用"
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            obj.get("message")?.takeIf { it.isJsonPrimitive }?.asString?.let {
                return TicketSecurity.sanitize(it, 120)
            }
            obj.get("error")?.takeIf { it.isJsonPrimitive }?.asString?.let {
                return TicketSecurity.sanitize(it, 120)
            }
        }
        if (element.isJsonArray) {
            val first = element.asJsonArray.firstOrNull()
            if (first != null && first.isJsonObject) {
                first.asJsonObject.get("text")?.takeIf { it.isJsonPrimitive }?.asString?.let {
                    val clean = TicketSecurity.sanitize(it, 120)
                    if (clean.isNotBlank()) return clean
                }
            }
        }
        return "票务服务暂时不可用"
    }
}
