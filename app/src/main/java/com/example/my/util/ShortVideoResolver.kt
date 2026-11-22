package com.example.my.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import com.example.my.data.CloudConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.regex.Pattern

/**
 * 短视频链接解析。
 *
 * 解析主体走服务端 [CloudConfig.VIDEO_RESOLVER_URL]：服务端抓取抖音分享页/短链，
 * 返回清洗后的视频直链、封面、标题、作者、时长、图集、实况、配乐。
 * 端侧只负责把返回的媒体下载到缓存目录，再交给上传页填入。
 *
 * 如果服务端没有识别出内容（例如用户贴的是普通 MP4 直链），保留直链兜底下载；
 * 不再由端侧抓 HTML 猜 playAddr，避免平台改版后长期失效。
 */
object ShortVideoResolver {

    private const val TAG = "ShortVideoResolver"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0 Mobile Safari/537.36"
    private const val REFERER = "https://www.douyin.com/"

    /**
     * @param video 解析出的视频；图集/图文可能为空
     * @param cover 封面图，优先用服务端给的图，下载失败再抽视频首帧
     * @param images 图集或实况静图，可直接填入照片区
     * @param liveVideos 实况的动态片段，按普通视频追加
     * @param audio 配乐音频，可直接填入 BGM
     */
    data class Resolved(
        val video: File? = null,
        val cover: File? = null,
        val images: List<File> = emptyList(),
        val liveVideos: List<File> = emptyList(),
        val audio: File? = null,
        val audioTitle: String = "",
        val audioArtist: String = "",
        val title: String = "",
        val desc: String = "",
        val author: String = "",
        val durationMs: Long = 0L,
        /** 作品发布时间（毫秒），由抖音作品 ID 解出 */
        val createdAtMillis: Long? = null,
        /** 文案里带 📍 的定位，如「天津·解放北路、台儿庄路」 */
        val location: String = "",
        /** 从定位里拆出的城市，如「天津」 */
        val locationCity: String = "",
        val sourceUrl: String = "",
        val kind: String = "video"
    ) {
        val mediaCount: Int get() = (if (video != null) 1 else 0) + images.size + liveVideos.size
        val hasContent: Boolean get() = mediaCount > 0 || audio != null
    }

    suspend fun resolve(context: Context, raw: String): Result<Resolved> = runCatching {
        val url = extractUrl(raw)
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "链接必须以 http:// 或 https:// 开头"
        }
        val outDir = File(context.cacheDir, "link_video").apply { mkdirs() }
        val platform = detectPlatform(url)

        // 快手 / 小红书没有稳定的公开解析接口，直接在端侧读分享页：
        // 标题、正文、发布时间、POI 与图集/视频都从页面元数据和内嵌 JSON 里取。
        // 抖音仍走服务端，避免把已经稳定的链路改坏。
        var pageError: Throwable? = null
        if (platform == Platform.KUAISHOU || platform == Platform.XHS) {
            val page = runCatching { resolveByPage(platform, url, raw, outDir) }
            if (page.isSuccess) {
                val resolved = page.getOrThrow()
                if (resolved.hasContent) return@runCatching resolved
                pageError = IllegalStateException("分享页没有解析出可下载的媒体")
            } else {
                pageError = page.exceptionOrNull()
                Log.w(TAG, "page resolve failed: ${pageError?.message}", pageError)
            }
        }

        // 短链先跟随 302 拿到作品长链；服务端只认 iesdouyin 的分享地址
        var effectiveUrl = url
        var serverText = raw
        if (url.contains("v.douyin.com", true)) {
            runCatching { followShortLink(url) }.getOrNull()
                ?.takeIf { it.contains("iesdouyin.com", true) }
                ?.let {
                    effectiveUrl = it
                    // 服务端只认干净的作品地址，带 share_sign 等参数会解析失败
                    serverText = it.substringBefore('?')
                }
        }

        // 1. 服务端解析（抖音分享文案/短链/图集/实况）
        val serverError: Throwable?
        val server = runCatching { resolveByServer(serverText, effectiveUrl, outDir) }
        if (server.isSuccess) {
            val resolved = server.getOrThrow()
            if (resolved.hasContent) return@runCatching resolved
            serverError = IllegalStateException("服务端没有解析出可下载的媒体")
        } else {
            serverError = server.exceptionOrNull()
            Log.w(TAG, "server resolve failed: ${serverError?.message}", serverError)
        }

        // 2. 直链兜底：用户直接贴 MP4/MOV/WEBM 时不依赖分享页
        val direct = runCatching { fetchVideo(url, outDir) }.getOrNull()
        if (direct != null && isPlayable(direct)) {
            return@runCatching Resolved(
                video = direct,
                cover = extractCoverSafe(direct),
                sourceUrl = url,
                kind = "video"
            )
        }

        throw IllegalStateException(
            pageError?.message?.takeIf { it.isNotBlank() }
                ?: serverError?.message?.takeIf { it.isNotBlank() }
                ?: "没有解析到可下载的视频或图片，请确认分享文案完整"
        )
    }

    /** 从分享文案里抠第一个 http(s) 链接；用户只贴纯链接时原样返回 */
    fun extractUrl(raw: String): String {
        val m = Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
            .matcher(raw.trim())
        return if (m.find()) m.group().trimEnd('.', ',', '，', '。') else raw.trim()
    }

    // ---------------- 快手 / 小红书分享页 ----------------

    private enum class Platform { DOUYIN, KUAISHOU, XHS, OTHER }

    private fun detectPlatform(url: String): Platform = when {
        url.contains("douyin.com", true) -> Platform.DOUYIN
        url.contains("kuaishou.com", true) -> Platform.KUAISHOU
        url.contains("xiaohongshu.com", true) || url.contains("xhslink.com", true) -> Platform.XHS
        else -> Platform.OTHER
    }

    /**
     * 快手 / 小红书没有统一的服务端解析接口，直接从分享页取：
     * - 先跟 302 到作品长链；
     * - 读 og:title / og:description / og:video / og:image；
     * - 再从 `__INITIAL_STATE__`（小红书）或 `__APOLLO_STATE__`（快手）里补标题、正文、发布时间、POI 和图集；
     * - 发布时间小红书用 24 位笔记 ID 前 8 位十六进制解，快手优先读页面 timestamp。
     */
    private fun resolveByPage(
        platform: Platform,
        rawUrl: String,
        raw: String,
        dir: File
    ): Resolved {
        val sourceUrl = followShortLink(rawUrl) ?: rawUrl
        val referer = when (platform) {
            Platform.KUAISHOU -> "https://www.kuaishou.com/"
            Platform.XHS -> "https://www.xiaohongshu.com/"
            else -> "https://www.douyin.com/"
        }
        val html = fetchText(sourceUrl, referer)
        if (html.length < 32) error("分享页内容为空，可能需要登录后才能查看")

        val pageTitle = metaContent(html, "og:title")
            ?: metaContent(html, "twitter:title")
            ?: htmlTitle(html)
            ?: ""
        val pageDesc = metaContent(html, "og:description")
            ?: metaContent(html, "description")
            ?: ""

        val embedded = embeddedJson(html, platform)
        val root = embedded?.let { text ->
            runCatching {
                JSONObject(
                    unescapeHtml(text)
                        .replace("\\/", "/")
                        .replace("\\u002F", "/")
                        .replace("undefined", "null")
                )
            }.getOrNull()
        }
        val strings = collectStrings(root)
        val pageTime = findTime(root)

        val title = cleanPlatformTitle(
            listOf(
                pageTitle,
                findNamed(root, setOf("title", "caption")).firstOrNull().orEmpty(),
                cleanTitle(raw)
            ).firstOrNull { it.isNotBlank() } ?: ""
        )
        val desc = listOf(
            pageDesc,
            findNamed(root, setOf("desc", "description", "content")).firstOrNull().orEmpty(),
            raw
        ).firstOrNull { it.isNotBlank() && !it.startsWith("http") } ?: ""
        val author = findNamed(root, setOf("nickname", "username", "authorName", "name"))
            .firstOrNull { it.length in 1..40 && !it.startsWith("http") }
            .orEmpty()
        val location = extractLocation(title, desc).ifBlank {
            findNamed(root, setOf("poiName", "poi", "poiInfo", "address", "location"))
                .firstOrNull { it.length in 2..80 && !it.startsWith("http") && !it.contains("IP属地") }
                .orEmpty()
        }

        val imageUrls = LinkedHashSet<String>()
        metaContent(html, "og:image")?.let { imageUrls += normalizeUrl(it) }
        metaContent(html, "og:image:url")?.let { imageUrls += normalizeUrl(it) }
        metaContent(html, "twitter:image")?.let { imageUrls += normalizeUrl(it) }
        strings.asSequence()
            .map(::normalizeUrl)
            .filter { looksLikeImage(it) || it.contains("sns-webpic", true) }
            .take(24)
            .forEach { imageUrls += it }

        val videoUrls = LinkedHashSet<String>()
        metaContent(html, "og:video")?.let { videoUrls += normalizeUrl(it) }
        metaContent(html, "og:video:url")?.let { videoUrls += normalizeUrl(it) }
        metaContent(html, "og:video:secure_url")?.let { videoUrls += normalizeUrl(it) }
        strings.asSequence()
            .map(::normalizeUrl)
            .filter { looksLikeVideo(it) || it.contains("sns-video", true) || it.contains("ks-video", true) }
            .take(12)
            .forEach { videoUrls += it }

        val audioUrls = strings.asSequence()
            .map(::normalizeUrl)
            .filter { looksLikeAudio(it) }
            .take(4)
            .toList()

        val images = mutableListOf<File>()
        for (u in imageUrls) {
            if (images.size >= 18) break
            if (looksLikeVideo(u)) continue
            runCatching { downloadImage(u, dir, "page_image", referer) }.getOrNull()?.let { images += it }
        }

        var video: File? = null
        for (u in videoUrls) {
            val file = runCatching { fetchVideo(u, dir, referer) }.getOrNull()
            if (file != null && isPlayable(file)) {
                video = file
                break
            }
        }

        val audio = audioUrls.firstOrNull()?.let { runCatching { downloadAudio(it, dir, referer) }.getOrNull() }
        val cover = images.firstOrNull() ?: video?.let { extractCoverSafe(it) }
        if (video == null && images.isEmpty() && audio == null) {
            error("没有解析到作品媒体；小红书/快手可能要求登录或作品已删除")
        }

        val noteTime = if (platform == Platform.XHS) xhsIdMillis(sourceUrl) else null
        val createdAt = if (platform == Platform.XHS) noteTime ?: pageTime else pageTime
        return Resolved(
            video = video,
            cover = cover,
            images = images,
            audio = audio,
            audioTitle = findNamed(root, setOf("musicTitle", "audioTitle")).firstOrNull().orEmpty(),
            audioArtist = findNamed(root, setOf("musicAuthor", "audioAuthor")).firstOrNull().orEmpty(),
            title = title,
            desc = desc,
            author = author,
            durationMs = findDuration(root),
            createdAtMillis = createdAt,
            location = location,
            locationCity = location.substringBefore('·').substringBefore('、').trim().take(12),
            sourceUrl = sourceUrl,
            kind = if (video != null) "video" else "image"
        )
    }

    private fun fetchText(url: String, referer: String): String {
        val conn = open(url, referer, readTimeoutMs = 60_000)
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("分享页返回 HTTP $code")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                while (sb.length < 4_000_000) {
                    val n = reader.read(buf)
                    if (n <= 0) break
                    sb.append(buf, 0, n)
                }
                return sb.toString()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun metaContent(html: String, key: String): String? {
        val k = Regex.escape(key)
        val first = Regex(
            "<meta[^>]+(?:property|name)\\s*=\\s*[\"']$k[\"'][^>]*content\\s*=\\s*[\"']([^\"']*)[\"']",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)
        if (!first.isNullOrBlank()) return unescapeHtml(first)
        val second = Regex(
            "<meta[^>]+content\\s*=\\s*[\"']([^\"']*)[\"'][^>]*(?:property|name)\\s*=\\s*[\"']$k[\"']",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)
        return second?.let { unescapeHtml(it) }
    }

    private fun htmlTitle(html: String): String? =
        Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.let { unescapeHtml(it).trim() }

    private fun embeddedJson(html: String, platform: Platform): String? {
        val markers = if (platform == Platform.XHS) {
            listOf("__INITIAL_STATE__", "window.__INITIAL_STATE__")
        } else {
            listOf("__APOLLO_STATE__", "window.__APOLLO_STATE__")
        }
        for (marker in markers) {
            val at = html.indexOf(marker)
            if (at < 0) continue
            val start = html.indexOfFirstInRange(at, marker.length, '{', '[') ?: continue
            return balancedJson(html, start)
        }
        return null
    }

    private fun String.indexOfFirstInRange(from: Int, extra: Int, vararg chars: Char): Int? {
        var i = (from + extra).coerceAtLeast(0)
        while (i < length) {
            if (this[i] in chars) return i
            i++
        }
        return null
    }

    /** 从 start 的 { 或 [ 开始，截到配对的右括号；跳过字符串里的括号 */
    private fun balancedJson(text: String, start: Int): String? {
        if (start !in text.indices) return null
        val open = text[start]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
                continue
            }
            when (c) {
                '"' -> inString = true
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun collectStrings(root: Any?): List<String> {
        val out = mutableListOf<String>()
        fun walk(node: Any?, depth: Int) {
            if (node == null || depth > 12 || out.size > 4000) return
            when (node) {
                is JSONObject -> {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        walk(node.opt(k), depth + 1)
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) walk(node.opt(i), depth + 1)
                }
                is String -> if (node.isNotBlank()) out += node
            }
        }
        walk(root, 0)
        return out
    }

    private fun findNamed(root: Any?, keys: Set<String>): List<String> {
        val wanted = keys.map { it.lowercase() }.toSet()
        val out = mutableListOf<String>()
        fun walk(node: Any?, depth: Int) {
            if (node == null || depth > 10 || out.size > 12) return
            when (node) {
                is JSONObject -> {
                    val it = node.keys()
                    while (it.hasNext()) {
                        val key = it.next()
                        val v = node.opt(key)
                        if (key.lowercase() in wanted) {
                            when (v) {
                                is String -> if (v.isNotBlank()) out += v
                                is Number -> out += v.toString()
                                is JSONObject -> {
                                    val inner = v.optString("name").ifBlank { v.optString("title") }
                                    if (inner.isNotBlank()) out += inner
                                }
                            }
                        }
                        walk(v, depth + 1)
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) walk(node.opt(i), depth + 1)
                }
            }
        }
        walk(root, 0)
        return out
    }

    private fun findTime(root: Any?): Long? {
        val keySet = setOf("time", "timestamp", "createtime", "uploadtime", "publishtime", "createdat")
        val values = mutableListOf<Long>()
        fun walk(node: Any?, depth: Int) {
            if (node == null || depth > 10 || values.size > 8) return
            when (node) {
                is JSONObject -> {
                    val it = node.keys()
                    while (it.hasNext()) {
                        val key = it.next()
                        val v = node.opt(key)
                        if (key.lowercase() in keySet) {
                            when (v) {
                                is Number -> values += v.toLong()
                                is String -> v.toLongOrNull()?.let { values += it }
                            }
                        }
                        walk(v, depth + 1)
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) walk(node.opt(i), depth + 1)
                }
            }
        }
        walk(root, 0)
        val now = System.currentTimeMillis()
        return values.mapNotNull { raw ->
            val ms = when {
                raw > 1_000_000_000_000L -> raw
                raw > 1_400_000_000L -> raw * 1000L
                else -> return@mapNotNull null
            }
            ms.takeIf { it in 1_400_000_000_000L..(now + 86_400_000L) }
        }.firstOrNull()
    }

    private fun findDuration(root: Any?): Long {
        val values = findNamed(root, setOf("duration", "durationms", "durationMillis"))
        return values.firstOrNull()?.toLongOrNull()?.let { raw ->
            if (raw > 10_000L) raw else raw * 1000L
        } ?: 0L
    }

    /** 小红书 24 位笔记 ID 的前 8 位是十六进制秒级时间戳 */
    private fun xhsIdMillis(url: String): Long? {
        val m = Regex("(?:explore|discovery/item|item)/([0-9a-fA-F]{24})").find(url) ?: return null
        val seconds = runCatching { m.groupValues[1].take(8).toLong(16) }.getOrNull() ?: return null
        val now = System.currentTimeMillis() / 1000
        if (seconds < 1_400_000_000L || seconds > now + 86_400L) return null
        return seconds * 1000L
    }

    private fun cleanPlatformTitle(raw: String): String {
        val cleaned = cleanTitle(raw)
            .replace(Regex("\\s*[-|｜]\\s*(小红书|快手)$"), "")
            .replace(Regex("^(小红书|快手)\\s*[-|｜:]\\s*"), "")
            .trim()
        return if (cleaned == "小红书" || cleaned == "快手") "" else cleaned
    }

    private fun normalizeUrl(raw: String): String =
        unescapeHtml(raw)
            .replace("\\/", "/")
            .replace("\\u002F", "/")
            .trim()

    private fun unescapeHtml(s: String): String {
        var out = s
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        Regex("&#(\\d+);").findAll(out).forEach { m ->
            val code = m.groupValues[1].toIntOrNull()
            if (code != null) out = out.replace(m.value, code.toChar().toString())
        }
        return out
    }

    private fun looksLikeImage(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
            path.endsWith(".webp") || path.endsWith(".gif") ||
            path.contains("sns-webpic") || path.contains("sns-img")
    }

    private fun looksLikeAudio(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".mp3") || path.endsWith(".m4a") || path.endsWith(".aac") ||
            path.endsWith(".wav") || path.endsWith(".ogg")
    }

    // ---------------- 服务端解析 ----------------

    private fun resolveByServer(raw: String, sourceUrl: String, dir: File): Resolved {
        val endpoint = CloudConfig.VIDEO_RESOLVER_URL +
            "?text=" + URLEncoder.encode(raw, "UTF-8")
        val conn = open(endpoint, REFERER, readTimeoutMs = 90_000)
        val body = try {
            val code = conn.responseCode
            if (code !in 200..299) {
                error("解析服务返回 HTTP $code")
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                while (sb.length < 4_000_000) {
                    val n = reader.read(buf)
                    if (n <= 0) break
                    sb.append(buf, 0, n)
                }
                sb.toString()
            }
        } finally {
            conn.disconnect()
        }

        val root = runCatching { JSONObject(body) }
            .getOrElse { error("解析服务返回的不是 JSON") }
        val msg = root.optString("msg").ifBlank { "解析失败" }
        if (root.optInt("code", 0) != 200) error(msg)
        val data = root.optJSONObject("data") ?: error(msg)

        val type = data.optString("type").ifBlank { "video" }
        val rawTitle = data.optString("title").ifBlank { data.optString("desc") }
        val desc = data.optString("desc")
        val author = data.optJSONObject("author")?.optString("name").orEmpty()
        val durationMs = data.optLong("duration", 0L)

        // 发布时间：抖音作品 ID 的高位是秒级时间戳；长链直接取，短链先跟随一次跳转
        val awemeId = findAwemeId(raw, sourceUrl)
        val createdAtMillis = awemeId?.let { idToMillis(it) }

        // 位置：文案里通常用 📍 标注，如「📍天津·解放北路、台儿庄路」
        val location = extractLocation(rawTitle, desc)
        val locationCity = location.substringBefore('·').substringBefore('、')
            .trim().take(12)

        val images = downloadMediaList(data.optJSONArray("images"), dir, "image")
        val live = parseLive(data.optJSONArray("live_photo"), dir)

        // 视频地址：主地址 + 备份地址，逐个试到能播放为止
        val videoUrls = LinkedHashSet<String>()
        data.optString("url").takeIf { it.startsWith("http") }?.let { videoUrls += it }
        collectHttpStrings(data.optJSONArray("video_backup"), videoUrls)

        var video: File? = null
        for (u in videoUrls) {
            val file = runCatching { fetchVideo(u, dir) }.getOrNull()
            if (file != null && isPlayable(file)) {
                video = file
                break
            }
        }

        // 图文/图集：主地址不是视频时，尝试把它当作图片下载
        val extraImages = mutableListOf<File>()
        if (video == null && images.isEmpty()) {
            val u = data.optString("url")
            if (u.startsWith("http")) {
                runCatching { downloadImage(u, dir, "image") }.getOrNull()?.let { extraImages += it }
            }
        }

        val allImages = (images + extraImages).distinctBy { it.absolutePath }
        if (video == null && allImages.isEmpty() && live.second.isEmpty()) {
            error(if (type == "image") "这条图文没有下载到图片" else "视频直链下载失败，可能已过期")
        }

        val cover = data.optString("cover").takeIf { it.startsWith("http") }
            ?.let { runCatching { downloadImage(it, dir, "cover") }.getOrNull() }
            ?: video?.let { extractCoverSafe(it) }

        val music = data.optJSONObject("music")
        val audio = music
            ?.optString("url").orEmpty()
            .takeIf { it.startsWith("http") }
            ?.let { runCatching { downloadAudio(it, dir) }.getOrNull() }

        return Resolved(
            video = video,
            cover = cover,
            images = allImages,
            liveVideos = live.second,
            audio = audio,
            audioTitle = music?.optString("title").orEmpty(),
            audioArtist = music?.optString("author").orEmpty(),
            title = cleanTitle(rawTitle),
            desc = desc,
            author = author,
            durationMs = durationMs,
            createdAtMillis = createdAtMillis,
            location = location,
            locationCity = locationCity,
            sourceUrl = sourceUrl,
            kind = type
        )
    }

    /** 实况条目兼容 JSONObject 和纯 URL 两种返回；返回 (静图, 动态片段) */
    private fun parseLive(array: JSONArray?, dir: File): Pair<List<File>, List<File>> {
        if (array == null || array.length() == 0) return emptyList<File>() to emptyList()
        val images = mutableListOf<File>()
        val videos = mutableListOf<File>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i)
            if (item != null) {
                val imageUrl = firstHttp(
                    item.optString("image"),
                    item.optString("image_url"),
                    item.optString("cover")
                )
                val videoUrl = firstHttp(
                    item.optString("video"),
                    item.optString("video_url"),
                    item.optString("url")
                )
                if (imageUrl != null) {
                    runCatching { downloadImage(imageUrl, dir, "live_img") }.getOrNull()?.let { images += it }
                }
                if (videoUrl != null) {
                    val f = runCatching { fetchVideo(videoUrl, dir) }.getOrNull()
                    if (f != null && isPlayable(f)) videos += f
                }
            } else {
                val u = array.optString(i)
                if (u.startsWith("http")) {
                    if (looksLikeVideo(u)) {
                        val f = runCatching { fetchVideo(u, dir) }.getOrNull()
                        if (f != null && isPlayable(f)) videos += f
                    } else {
                        runCatching { downloadImage(u, dir, "live_img") }.getOrNull()?.let { images += it }
                    }
                }
            }
        }
        return images to videos
    }

    private fun collectHttpStrings(array: JSONArray?, out: MutableSet<String>) {
        if (array == null) return
        for (i in 0 until array.length()) {
            array.optString(i).takeIf { it.startsWith("http") }?.let { out += it }
        }
    }

    private fun firstHttp(vararg values: String): String? =
        values.firstOrNull { it.startsWith("http") }

    private fun downloadMediaList(array: JSONArray?, dir: File, prefix: String): List<File> {
        if (array == null || array.length() == 0) return emptyList()
        val out = mutableListOf<File>()
        for (i in 0 until array.length()) {
            val u = array.optString(i)
            if (!u.startsWith("http")) continue
            runCatching { downloadImage(u, dir, prefix) }.getOrNull()?.let { out += it }
        }
        return out.distinctBy { it.absolutePath }
    }

    // ---------------- 作品时间与位置 ----------------

    /** 从长链/分享文案里取作品 ID；只有短链时跟随一次跳转拿最终地址 */
    private fun findAwemeId(raw: String, url: String): Long? {
        val pattern = Pattern.compile(
            "(?:share/(?:video|note)|/video/|/note/)(\\d{15,20})",
            Pattern.CASE_INSENSITIVE
        )
        fun fromText(text: String): Long? {
            val m = pattern.matcher(text)
            return if (m.find()) m.group(1)?.toLongOrNull() else null
        }
        fromText(raw)?.let { return it }
        fromText(url)?.let { return it }
        if (!url.contains("v.douyin.com", true)) return null
        val finalUrl = runCatching { followShortLink(url) }.getOrNull() ?: return null
        return fromText(finalUrl)
    }

    private fun followShortLink(url: String): String? {
        var current = url
        repeat(4) {
            val conn = URL(current).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("User-Agent", UA)
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: return null
                    current = URL(URL(current), location).toString()
                } else {
                    return current
                }
            } finally {
                conn.disconnect()
            }
        }
        return current
    }

    /** 抖音作品 ID 高 32 位是秒级时间戳；只接受 2016 年以后、不过期的值 */
    private fun idToMillis(id: Long): Long? {
        val seconds = id / 4_294_967_296L
        val now = System.currentTimeMillis() / 1000
        if (seconds < 1_400_000_000L || seconds > now + 86_400L) return null
        return seconds * 1000L
    }

    /** 取文案里的 📍 定位；没有定位标记时再看「地点/位置/地址：」 */
    private fun extractLocation(title: String, desc: String): String {
        val text = listOf(title, desc).filter { it.isNotBlank() }.joinToString("\n")
        val marker = "📍"
        val at = text.indexOf(marker)
        if (at >= 0) {
            val tail = text.substring(at + marker.length)
            val line = tail.lineSequence().firstOrNull().orEmpty()
            val cleaned = line
                .substringBefore('#')
                .trim(' ', '-', '—', ':', '：', '，', ',', '。', '.', '、')
            if (cleaned.isNotBlank()) return cleaned.take(120)
        }
        val m = Pattern.compile("(?:地点|位置|地址)\\s*[:：]\\s*([^\\n#]{2,80})")
            .matcher(text)
        if (m.find()) {
            return (m.group(1) ?: "").trim().take(120)
        }
        return ""
    }

    /** 标题清洗：去掉分享链接、抖音口令尾巴和多余符号，只留真正的内容标题 */
    fun cleanTitle(raw: String): String {
        // 标题通常在第一行；📍 之后是定位，不能混进标题
        var s = raw.lineSequence().firstOrNull { it.isNotBlank() } ?: raw
        val locAt = s.indexOf("📍")
        if (locAt >= 0) s = s.substring(0, locAt)
        s = s.replace(Regex("https?://\\S+"), " ")
        val stops = listOf(
            "复制此链接", "复制打开抖音", "打开抖音搜索", "复制打开",
            "复制本条信息", "复制后打开", "打开小红书", "打开快手",
            "直接观看视频", "看看这个", "##"
        )
        for (mark in stops) {
            val idx = s.indexOf(mark)
            if (idx >= 0) s = s.substring(0, idx)
        }
        s = s.replace(Regex("[#@]\\S*"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '—', ':', '：', '，', ',', '.', '…')
        // 标题取第一句，避免把整段图文说明塞进标题输入框
        val sentenceEnd = s.indexOfFirst { it == '。' || it == '！' || it == '？' || it == '!' || it == '?' || it == '\n' }
        if (sentenceEnd >= 0) s = s.substring(0, sentenceEnd).trim()
        return s.take(50).trim()
    }

    // ---------------- 下载 ----------------

    private fun fetchVideo(url: String, dir: File, referer: String = REFERER): File? {
        val conn = open(url, referer, readTimeoutMs = 90_000)
        try {
            val code = conn.responseCode
            if (code !in 200..299) return null
            val type = conn.contentType.orEmpty().lowercase()
            if (type.startsWith("text/") || type.contains("html") || type.contains("json")) {
                return null
            }
            val ext = when {
                type.contains("webm") -> "webm"
                type.contains("quicktime") || url.contains(".mov", true) -> "mov"
                else -> "mp4"
            }
            val out = File(dir, "link_${UUID.randomUUID().toString().take(8)}.$ext")
            conn.inputStream.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output, 256 * 1024) }
            }
            if (out.length() < 1024) {
                out.delete()
                return null
            }
            return out
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadImage(url: String, dir: File, prefix: String, referer: String = REFERER): File? {
        val conn = open(url, referer, readTimeoutMs = 45_000)
        try {
            val code = conn.responseCode
            if (code !in 200..299) return null
            val type = conn.contentType.orEmpty().lowercase()
            if (type.startsWith("text/") || type.contains("html") || type.contains("json")) {
                return null
            }
            val ext = when {
                type.contains("png") -> "png"
                type.contains("webp") -> "webp"
                type.contains("gif") -> "gif"
                url.contains(".png", true) -> "png"
                url.contains(".webp", true) -> "webp"
                else -> "jpg"
            }
            val out = File(dir, "${prefix}_${UUID.randomUUID().toString().take(8)}.$ext")
            conn.inputStream.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output, 128 * 1024) }
            }
            if (out.length() < 256) {
                out.delete()
                return null
            }
            return out
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadAudio(url: String, dir: File, referer: String = REFERER): File? {
        val conn = open(url, referer, readTimeoutMs = 60_000)
        try {
            val code = conn.responseCode
            if (code !in 200..299) return null
            val type = conn.contentType.orEmpty().lowercase()
            if (type.startsWith("text/") || type.contains("html") || type.contains("json")) {
                return null
            }
            val ext = when {
                type.contains("wav") -> "wav"
                type.contains("m4a") || type.contains("mp4") -> "m4a"
                type.contains("ogg") -> "ogg"
                else -> "mp3"
            }
            val out = File(dir, "audio_${UUID.randomUUID().toString().take(8)}.$ext")
            conn.inputStream.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output, 128 * 1024) }
            }
            if (out.length() < 512) {
                out.delete()
                return null
            }
            return out
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, referer: String, readTimeoutMs: Int = 25_000): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = readTimeoutMs
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "*/*")
        conn.setRequestProperty("Referer", referer)
        return conn
    }

    private fun looksLikeVideo(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".webm") ||
            path.endsWith(".m4v")
    }

    private fun isPlayable(file: File): Boolean = runCatching {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            duration > 0 && w > 0
        } finally {
            mmr.release()
        }
    }.getOrElse {
        Log.w(TAG, "not playable: ${file.absolutePath}", it)
        false
    }

    /** 抽第一帧当封面；返回本地 jpg，失败返回 null */
    private fun extractCoverSafe(video: File): File? = runCatching {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(video.absolutePath)
            val frame: Bitmap = mmr.getFrameAtTime(
                1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return@runCatching null
            val out = File(video.parentFile ?: return@runCatching null,
                "cover_${UUID.randomUUID().toString().take(8)}.jpg")
            FileOutputStream(out).use { frame.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            frame.recycle()
            out
        } finally {
            mmr.release()
        }
    }.getOrNull()
}
