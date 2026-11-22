package com.example.my.data.search

import com.example.my.data.WebPrivacy
import com.example.my.data.ticket.TicketSecurity
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * GitHub 免费开源项目检索。
 *
 * 只走 GitHub 公开 Search API，不需要用户填 Key，也不把查询发给任意第三方：
 * - 固定 HTTPS 主机 api.github.com、443 端口、禁跳转；
 * - 不带 Cookie / Referer / 设备标识；
 * - 只接受 application/json，响应硬上限 512KB；
 * - 只保留带明确开源许可证、未归档、链接仍在 github.com 的仓库。
 *
 * 未鉴权时 GitHub 对 Search 有频率限制（约 10 次/分钟），触发后如实提示，
 * 不在本地伪造结果，也不自动换 IP/换账号绕过。
 */
object OpenSourceSearch {

    private const val ENDPOINT = "https://api.github.com/search/repositories"
    private val HOSTS = setOf("api.github.com")
    private const val MAX_BODY_BYTES = 512 * 1024
    private const val MAX_QUERY_CHARS = 120
    private const val MAX_LIMIT = 10

    data class Project(
        val fullName: String,
        val url: String,
        val description: String,
        val stars: Int,
        val language: String,
        val license: String,
        val updatedAt: String
    )

    /** 用户这句话是不是在找开源项目；只有开关打开时才用它触发工具预检 */
    fun looksLikeQuery(text: String): Boolean {
        val t = text.lowercase()
        if (t.length < 4) return false
        return listOf(
            "开源", "open source", "opensource", "github", "代码库", "仓库",
            "license", "许可证", "免费项目", "免费开源", "star", "stars",
            "mcp server", "mcp 服务", "mcp项目", "mcp 项目", "mcp"
        ).any { t.contains(it) }
    }

    /**
     * 搜索并过滤。`query` 为空时返回空列表；任何网络/协议异常都抛 [OpenSourceException]，
     * 由工具层转成「没查到」的文本，不在这里编造仓库。
     */
    fun search(query: String, language: String?, minStars: Int, limit: Int): List<Project> {
        val q = query.trim().replace(Regex("\\s+"), " ").take(MAX_QUERY_CHARS)
        if (q.isBlank()) return emptyList()
        val perPage = limit.coerceIn(1, MAX_LIMIT)
        val stars = minStars.coerceIn(0, 500_000)
        val lang = language.orEmpty().trim()
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '+' || it == '#' }
            .take(24)
        val terms = buildString {
            append(q)
            append(" in:name,description,readme")
            append(" is:public archived:false")
            if (stars > 0) append(" stars:>=$stars")
            if (lang.isNotBlank()) append(" language:").append(lang)
        }
        val url = "$ENDPOINT?q=${URLEncoder.encode(terms, "UTF-8")}" +
            "&sort=stars&order=desc&per_page=$perPage"
        val body = getJson(url)
        return parseItems(body, perPage)
    }

    /** 纯解析 + 过滤，方便单测；不联网。 */
    fun parseItems(json: String, limit: Int = MAX_LIMIT): List<Project> {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
            ?: throw OpenSourceException("GitHub 返回格式异常")
        val items = root.getAsJsonArray("items") ?: return emptyList()
        val out = mutableListOf<Project>()
        for (el in items) {
            val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val fullName = obj.string("full_name")
            val htmlUrl = obj.string("html_url")
            val license = obj.obj("license")?.string("spdx_id").orEmpty()
            val stars = obj.int("stargazers_count")
            val archived = obj.bool("archived")
            val disabled = obj.bool("disabled")
            // 没有明确许可证、被归档/禁用的仓库不算「可用的免费开源项目」
            if (fullName.isBlank() || htmlUrl.isBlank()) continue
            if (license.isBlank() || license.equals("NOASSERTION", ignoreCase = true)) continue
            if (archived || disabled) continue
            if (!isGitHubRepoUrl(htmlUrl)) continue
            out += Project(
                fullName = fullName.take(120),
                url = htmlUrl,
                description = obj.string("description").take(220),
                stars = stars.coerceAtLeast(0),
                language = obj.string("language").ifBlank { "未标注" }.take(32),
                license = license.take(32),
                updatedAt = obj.string("updated_at").take(10)
            )
            if (out.size >= limit.coerceIn(1, MAX_LIMIT)) break
        }
        return out.sortedByDescending { it.stars }
    }

    /** 渲染成喂给模型的纯文本；链接只保留 GitHub 仓库页，不拼搜索页。 */
    fun render(query: String, projects: List<Project>): String {
        if (projects.isEmpty()) {
            return "【GitHub 免费开源项目】没有找到带明确开源许可证、未归档的匹配仓库。"
        }
        return buildString {
            appendLine("【GitHub 免费开源项目搜索结果】关键词：${query.take(80)}")
            projects.forEachIndexed { index, p ->
                appendLine("${index + 1}. ${p.fullName} · Star ${p.stars} · ${p.language} · ${p.license}")
                if (p.description.isNotBlank()) appendLine("   简介：${p.description}")
                appendLine("   仓库：${p.url}")
            }
            appendLine()
            appendLine("说明：以上仓库均带明确开源许可证、未归档；许可证只代表可免费获取和按条款使用，商用/分发前仍需读 LICENSE。")
            append("回答时只挑最贴合用户需求的 3-5 个，不要编造 Star、License 或仓库地址。")
        }
    }

    private fun getJson(url: String): String {
        if (!TicketSecurity.isAllowedHttps(url, HOSTS)) {
            throw OpenSourceException("开源项目检索地址未通过安全校验")
        }
        val uri = runCatching { URI(url) }.getOrNull()
            ?: throw OpenSourceException("开源项目检索地址无效")
        var conn: HttpsURLConnection? = null
        try {
            conn = (uri.toURL().openConnection() as? HttpsURLConnection)
                ?: throw OpenSourceException("开源项目检索必须使用 HTTPS")
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.doInput = true
            conn.connectTimeout = 12_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            conn.setRequestProperty("Accept-Encoding", "identity")
            conn.setRequestProperty("Cache-Control", "no-store, no-cache")
            conn.setRequestProperty("DNT", "1")
            // 不设置 Cookie 就是不带 Cookie；空值头会被部分 HTTP 栈拒绝。
            conn.setRequestProperty("User-Agent", WebPrivacy.STANDARD_UA)

            val code = conn.responseCode
            if (code == 403 || code == 429) {
                throw OpenSourceException("GitHub 公开检索频率受限，请稍后再试")
            }
            if (code !in 200..299) throw OpenSourceException("GitHub 检索暂时不可用（$code）")
            val contentType = conn.contentType.orEmpty().lowercase()
            if (!contentType.startsWith("application/json")) {
                throw OpenSourceException("GitHub 返回了非 JSON 内容，已拒绝解析")
            }
            val bytes = readAtMost(conn.inputStream, MAX_BODY_BYTES)
                ?: throw OpenSourceException("GitHub 返回内容过大，已拒绝解析")
            return String(bytes, Charsets.UTF_8)
        } catch (e: OpenSourceException) {
            throw e
        } catch (e: Exception) {
            throw OpenSourceException("网络连接失败，请检查网络后重试", e)
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

    private fun isGitHubRepoUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (!uri.userInfo.isNullOrBlank()) return false
        if (uri.port != -1 && uri.port != 443) return false
        if (!uri.host.equals("github.com", ignoreCase = true)) return false
        return uri.path.orEmpty().trim('/').split('/').size == 2
    }

    private fun JsonObject.string(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.int(key: String): Int =
        get(key)?.takeIf { it.isJsonPrimitive }?.asInt ?: 0

    private fun JsonObject.bool(key: String): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject
}

class OpenSourceException(message: String, cause: Throwable? = null) : Exception(message, cause)
