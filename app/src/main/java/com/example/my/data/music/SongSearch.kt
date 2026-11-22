package com.example.my.data.music

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.my.data.WebIncognito
import com.example.my.data.WebPrivacy
import com.example.my.data.WebSafety
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** 曲库搜索里的一条结果（服务端渲染的搜索页，不依赖 JS） */
data class SongResult(
    val id: String,
    val title: String,
    val artist: String,
    val pageUrl: String,
    /** 曲库搜索页给出的专辑名；没有就是空串 */
    val album: String = "",
    /** mm:ss 形式的时长；没有就是空串 */
    val duration: String = ""
)

/** 已经换到真实音频直链、可以直接交给 [WebMusicCache] 下载的歌曲 */
data class SongAudio(
    val url: String,
    val title: String,
    val artist: String,
    val cover: String = "",
    /** 详情页自带的带时间轴 LRC；没有就空串 */
    val lrc: String = "",
    val pageUrl: String = "",
    val referer: String = "",
    val cookie: String = "",
    val userAgent: String = ""
)

/** 获取失败的原因：UI 分别给提示，并统一降级到网页手动获取 */
enum class SongFetchReason { NETWORK, PARSE, BLOCKED, RATE_LIMIT, NO_SOURCE, NOT_AUDIO }

sealed class SongFetch {
    data class Ok(val audio: SongAudio) : SongFetch()
    data class Fail(val reason: SongFetchReason) : SongFetch()
}

/**
 * 歌曲搜索 + 获取（曲库网页的服务端渲染结果，不需要 WebView）。
 *
 * 链路：搜索页 `/search?s=歌名` → 解析出「歌名 / 歌手 / 详情页 id」→ 详情页
 * `/play/id` 取 `play_id` 与 LRC → 站点自己的 `/api/music` 换真实音频直链 →
 * [WebMusicCache] 下载到私有目录后本地播放。
 *
 * 有两点必须守住：
 * 1. 站点接口要求**同一次会话的 Cookie**（先访问详情页再请求接口），所以每次搜索/获取
 *    都用独立的 [Session]，只保留浏览器本来就会发的 `name=value`，不伪造、不绕过人机验证；
 * 2. 所有地址都过 [WebSafety]，音频直链再用扩展名/Content-Type 判定一次；
 *    403 / 429 / 430 这类站点风控如实返回失败，由 UI 引导用户去网页手动完成，绝不自动重试绕过。
 *
 * 结果只缓存 10 分钟；直链会过期，一律不缓存。
 */
object SongSearch {

    private const val HOST = "https://www.gequhai.com"
    private const val SEARCH_PATH = "/search?s="
    private const val CACHE_TTL_MS = 30 * 60 * 1000L
    private const val CACHE_LIMIT = 64
    private val HEX = "0123456789ABCDEF"

    private val cache = ConcurrentHashMap<String, Pair<Long, List<SongResult>>>()

    /** 搜歌：返回按页面顺序排列的结果，失败/无结果返回空列表 */
    suspend fun search(rawQuery: String, limit: Int = 30): List<SongResult> =
        withContext(Dispatchers.IO) {
            val q = rawQuery.trim()
            if (q.isBlank()) return@withContext emptyList()
            cache[q]?.let { (at, list) ->
                if (System.currentTimeMillis() - at < CACHE_TTL_MS) return@withContext list.take(limit)
            }
            val list = runCatching {
                val session = Session()
                val url = HOST + SEARCH_PATH + pctEncode(q)
                parseSearch(request(session, url, referer = "$HOST/"), q)
            }.getOrDefault(emptyList())
            if (list.isNotEmpty()) {
                if (cache.size >= CACHE_LIMIT) {
                    cache.entries.minByOrNull { it.value.first }?.key?.let { cache.remove(it) }
                }
                cache[q] = System.currentTimeMillis() to list
            }
            list.take(limit)
        }

    /**
     * 只取整首 LRC：搜歌 → 标题最像的一条 → 详情页 #content-lrc2。
     * 不碰 /api/music 换直链（那个接口有风控），所以纯歌词场景可以安全复用；
     * 曲海详情页给的是带 [mm:ss.xx] 的完整歌词，正好补上「网页洗词只有片段」的短板。
     */
    suspend fun fetchLyricsOnly(title: String, artist: String): String? =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext null
            val q = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
            val results = search(title, 8).ifEmpty { search(q, 8) }
            if (results.isEmpty()) return@withContext null
            val norm = { s: String -> s.lowercase().replace(Regex("[^\\u4e00-\\u9fa5a-z0-9]"), "") }
            val want = norm(title)
            val pick = results.firstOrNull { r ->
                val t = norm(r.title)
                want.isNotBlank() && (t.contains(want) || want.contains(t))
            } ?: return@withContext null
            val page = WebSafety.sanitize(pick.pageUrl) ?: return@withContext null
            val html = try {
                request(Session(), page, referer = "$HOST/")
            } catch (_: Exception) {
                return@withContext null
            }
            parseLrc(html).takeIf { it.lines().size >= 8 }
        }

    /**
     * 只取封面地址：搜歌 → 标题最像的一条 → 详情页 `window.mp3_cover`。
     * 与 [fetchLyricsOnly] 一样不碰 /api/music，避免为了封面触发换直链的风控。
     */
    suspend fun fetchCoverUrl(title: String, artist: String): String? =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext null
            val results = search(title, 8).ifEmpty {
                search(listOf(title, artist).filter { it.isNotBlank() }.joinToString(" "), 8)
            }
            if (results.isEmpty()) return@withContext null
            val norm = { s: String -> s.lowercase().replace(Regex("[^\\u4e00-\\u9fa5a-z0-9]"), "") }
            val want = norm(title)
            val pick = results.firstOrNull { r ->
                val t = norm(r.title)
                want.isNotBlank() && (t.contains(want) || want.contains(t))
            } ?: return@withContext null
            val page = WebSafety.sanitize(pick.pageUrl) ?: return@withContext null
            val html = try {
                request(Session(), page, referer = "$HOST/")
            } catch (_: Exception) {
                return@withContext null
            }
            val raw = jsString(html, "mp3_cover")
            WebSafety.sanitize(raw)?.takeIf { it.length <= 2048 }
        }

    /** 把一条搜索结果换成可下载的音频直链 */
    suspend fun fetch(result: SongResult): SongFetch = withContext(Dispatchers.IO) {
        val page = WebSafety.sanitize(result.pageUrl) ?: return@withContext SongFetch.Fail(SongFetchReason.PARSE)
        val session = Session()
        val html = try {
            request(session, page, referer = "$HOST/")
        } catch (e: HttpStatus) {
            return@withContext SongFetch.Fail(reasonOf(e.code))
        } catch (_: Exception) {
            return@withContext SongFetch.Fail(SongFetchReason.NETWORK)
        }

        val playId = Regex("""window\.play_id\s*=\s*'([^']+)'""").find(html)?.groupValues?.get(1)
            ?: return@withContext SongFetch.Fail(SongFetchReason.PARSE)
        val type = Regex("""window\.mp3_type\s*=\s*(\d+)""").find(html)?.groupValues?.get(1) ?: "0"
        val title = jsString(html, "mp3_title").ifBlank { result.title }
        val artist = jsString(html, "mp3_author").ifBlank { result.artist }
        val cover = jsString(html, "mp3_cover").let { WebSafety.sanitize(it).orEmpty() }
        val lrc = parseLrc(html)

        val body = "id=" + pctEncode(playId) + "&type=" + pctEncode(type)
        val json = try {
            request(
                session, "$HOST/api/music", referer = page, post = body,
                extra = mapOf(
                    "X-Requested-With" to "http",
                    "X-Custom-Header" to "Key",
                    "Origin" to HOST,
                    "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                    "Accept" to "application/json, text/javascript, */*; q=0.01"
                )
            )
        } catch (e: HttpStatus) {
            return@withContext SongFetch.Fail(reasonOf(e.code))
        } catch (_: Exception) {
            return@withContext SongFetch.Fail(SongFetchReason.NETWORK)
        }

        val code = Regex(""""code"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull()
        when (code) {
            429 -> return@withContext SongFetch.Fail(SongFetchReason.RATE_LIMIT)
            430 -> return@withContext SongFetch.Fail(SongFetchReason.NO_SOURCE)
            403 -> return@withContext SongFetch.Fail(SongFetchReason.BLOCKED)
            null, 200 -> Unit
            else -> return@withContext SongFetch.Fail(SongFetchReason.BLOCKED)
        }

        val rawUrl = Regex(""""url"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(json)
            ?.groupValues?.get(1)?.let { unescapeJson(it) }.orEmpty()
        val url = WebSafety.sanitize(rawUrl)
            ?: return@withContext SongFetch.Fail(SongFetchReason.PARSE)
        // 站点接口明确返回的就是音频直链，但有些 CDN 节点不带标准扩展名
        // （酷我 /trackmedia/… 偶尔只有资源 id）；这类也放行，下载时再由
        // Content-Type 复检，绝不会把网页当音频落盘。
        val audioLike = com.example.my.data.WebResourceProbe.kindOf(url) ==
            com.example.my.data.ProbedResource.Kind.AUDIO ||
            listOf("trackmedia", "/resource/", "/media/", "/download/").any {
                url.substringBefore('?').lowercase().contains(it)
            }
        if (!audioLike) {
            return@withContext SongFetch.Fail(SongFetchReason.NOT_AUDIO)
        }

        SongFetch.Ok(
            SongAudio(
                url = url,
                title = title,
                artist = artist,
                cover = cover,
                lrc = lrc,
                pageUrl = page,
                referer = "$HOST/",
                cookie = session.header(),
                userAgent = WebPrivacy.STANDARD_UA
            )
        )
    }

    private fun reasonOf(code: Int): SongFetchReason = when (code) {
        429 -> SongFetchReason.RATE_LIMIT
        403, 401 -> SongFetchReason.BLOCKED
        430 -> SongFetchReason.NO_SOURCE
        else -> SongFetchReason.NETWORK
    }

    // ---------- HTML / JSON 解析（纯字符串，无第三方库，方便离线单测） ----------

    /** 搜索页每个 <tr> 是：序号 / 带 /play/id 的歌名 / 歌手 / 专辑 / 时长 */
    private fun parseSearch(html: String, query: String): List<SongResult> {
        val rows = Regex("""<tr[^>]*>(.*?)</tr>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val link = Regex("""href=["']/play/(\d+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val cells = Regex("""<td[^>]*>(.*?)</td>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val out = mutableListOf<SongResult>()
        rows.findAll(html).forEach { row ->
            val block = row.groupValues[1]
            val hit = link.find(block) ?: return@forEach
            val id = hit.groupValues[1]
            val title = clean(hit.groupValues[2])
            if (title.isBlank()) return@forEach
            val td = cells.findAll(block).map { clean(it.groupValues[1]) }.toList()
            val artist = td.getOrNull(2).orEmpty().ifBlank { td.lastOrNull().orEmpty() }
            // 时长列是最稳的识别点：mm:ss；其余第 3 列按专辑处理，避免把时长当专辑
            val duration = td.firstOrNull { Regex("""^\d{1,2}:\d{2}$""").matches(it) }.orEmpty()
            val album = td.getOrNull(3)
                ?.takeIf {
                    it.isNotBlank() && it != duration && it != artist &&
                        it.length <= 40 && !it.matches(Regex("""\d+"""))
                }
                .orEmpty()
            out += SongResult(
                id = id,
                title = title.take(80),
                artist = artist.take(40),
                pageUrl = "$HOST/play/$id",
                album = album,
                duration = duration
            )
        }
        // 同一首歌可能因不同专辑重复出现：先按 id 去重，再按「歌名+歌手」去重
        val norm = { s: String -> s.lowercase().replace(Regex("[^\\u4e00-\\u9fa5a-z0-9]"), "") }
        val want = norm(query)
        return out
            .distinctBy { it.id }
            .distinctBy { norm(it.title) to norm(it.artist) }
            .sortedBy { r ->
                val t = norm(r.title)
                val a = norm(r.artist)
                when {
                    t == want -> 0
                    t.startsWith(want) -> 1
                    t.contains(want) -> 2
                    a.contains(want) -> 3
                    else -> 4
                }
            }
            .take(50)
    }

    /** 详情页 #content-lrc2 是带 [mm:ss.xx] 的完整 LRC，<br /> 换行 */
    private fun parseLrc(html: String): String {
        val block = Regex(
            """id=["']content-lrc2["'][^>]*>(.*?)</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).find(html)?.groupValues?.get(1) ?: return ""
        return block
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]+>"""), "")
            .let { decodeEntities(it) }
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .take(20_000)
    }

    private val ENTITIES = listOf(
        "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
        "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&middot;" to "·"
    )

    private fun decodeEntities(s: String): String = ENTITIES.fold(s) { acc, (a, b) -> acc.replace(a, b) }

    private fun clean(s: String): String = decodeEntities(
        s.replace(Regex("""<[^>]+>"""), " ")
    ).replace(Regex("""\s+"""), " ").trim()

    private fun jsString(html: String, name: String): String =
        Regex("""window\.$name\s*=\s*'([^']*)'""").find(html)?.groupValues?.get(1).orEmpty().trim()

    private fun unescapeJson(s: String): String = s
        .replace("\\/", "/")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")

    /** 与浏览器地址栏一致：UTF-8 逐字节 %XX，不用 Uri.encode（它会保留部分字符） */
    private fun pctEncode(text: String): String = buildString {
        text.toByteArray(Charsets.UTF_8).forEach { b ->
            val v = b.toInt() and 0xFF
            append('%').append(HEX[v shr 4]).append(HEX[v and 0x0F])
        }
    }

    // ---------- 会话 / 请求 ----------

    private class HttpStatus(val code: Int) : Exception("HTTP $code")

    /** 一次「搜索 → 详情 → 接口」共用的浏览器会话 Cookie */
    private class Session {
        private val cookies = LinkedHashMap<String, String>()
        fun header(): String = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        fun absorb(conn: HttpURLConnection) {
            conn.headerFields.entries
                .firstOrNull { it.key.equals("Set-Cookie", ignoreCase = true) }
                ?.value.orEmpty()
                .forEach { line ->
                    val pair = line.substringBefore(';')
                    val k = pair.substringBefore('=').trim()
                    if (k.isNotBlank()) cookies[k] = pair.substringAfter('=', "").trim()
                }
        }
    }

    private fun request(
        session: Session,
        url: String,
        referer: String,
        post: String? = null,
        extra: Map<String, String> = emptyMap()
    ): String {
        val safe = WebSafety.sanitize(url) ?: throw IllegalArgumentException("unsafe url")
        val conn = (URL(safe).openConnection() as HttpURLConnection).apply {
            connectTimeout = 9_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", WebPrivacy.STANDARD_UA)
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            setRequestProperty("Referer", referer)
            WebIncognito.applyTo(this)
            if (session.header().isNotBlank()) setRequestProperty("Cookie", session.header())
            extra.forEach { (k, v) -> setRequestProperty(k, v) }
            if (post != null) {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }
        }
        try {
            if (post != null) conn.outputStream.use { it.write(post.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            session.absorb(conn)
            if (code !in 200..299) throw HttpStatus(code)
            return conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * 搜索历史：只存在本机，按最近使用排序，最多 12 条。
 *
 * 与 SoundFxPrefs 一样用 Compose 状态暴露，搜索页增删后立即重组；
 * 不记录搜索次数、不上传，清空按钮直接删掉整份记录。
 */
object SongSearchHistory {
    private const val PREFS = "song_search_history"
    private const val KEY = "items"
    private const val LIMIT = 12

    var items by mutableStateOf<List<String>>(emptyList())
        private set

    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val raw = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }.getOrNull()
        items = raw?.split('\n')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
    }

    fun add(context: Context, query: String) {
        val text = query.trim()
        if (text.isBlank()) return
        init(context)
        val next = (listOf(text) + items.filterNot { it.equals(text, ignoreCase = true) }).take(LIMIT)
        items = next
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, next.joinToString("\n")).apply()
        }
    }

    fun clear(context: Context) {
        items = emptyList()
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        }
    }
}
