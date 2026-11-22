package com.example.my.data.news

import android.content.Context
import android.graphics.BitmapFactory
import com.example.my.data.WebIncognito
import com.example.my.data.WebSafety
import com.example.my.data.WebSearch
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.security.MessageDigest

private typealias WebSearchHit = com.example.my.data.WebHit

/** 文章块类型：标题/正文/图片/引用/条目，用于在本地按原顺序重新排版。 */
enum class NewsBlockKind { HEADING, PARAGRAPH, IMAGE, QUOTE, BULLET }

data class NewsArticleBlock(
    val kind: NewsBlockKind,
    val text: String = "",
    val imageUrl: String = "",
    val caption: String = ""
)

data class NewsArticle(
    val title: String,
    val url: String,
    val site: String,
    val author: String = "",
    val publishedAt: String = "",
    val lead: String = "",
    val coverImage: String = "",
    val blocks: List<NewsArticleBlock> = emptyList(),
    val fetchedAt: Long = System.currentTimeMillis(),
    /** 原网页抓取受限时，用检索摘要兜底排版，界面上会明确标注 */
    val fallback: Boolean = false
)

/**
 * 资讯文章正文抓取：只取一篇文章，保留段落、小标题、引用、列表和图片的原始顺序，
 * 输出可本地渲染的结构，不再把用户丢进 WebView。
 *
 * 失败顺序：原网页正文 → 用标题检索最匹配的网页再抓 → 检索摘要兜底。
 * 结果和图片都会落本地缓存，断网/原站限流时仍能读已看过的内容。
 */
object NewsArticleReader {

    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36"

    private const val MAX_HTML = 900_000
    private const val MAX_BLOCKS = 300

    suspend fun read(
        context: Context,
        url: String,
        titleHint: String = "",
        force: Boolean = false
    ): NewsArticle? = withContext(Dispatchers.IO) {
        val safe = WebSafety.sanitize(url) ?: return@withContext null
        val key = "url:$safe"
        if (!force) NewsArticleCache.load(context, key)?.let { return@withContext it }

        val resp = fetch(safe, 18_000) ?: return@withContext NewsArticleCache.load(context, key)
        val html = decode(resp.first, resp.second)
        val article = runCatching { parse(html, safe, titleHint) }.getOrNull()
            ?.takeIf { it.blocks.isNotEmpty() || it.lead.isNotBlank() }
        if (article != null) NewsArticleCache.save(context, key, article)
        article ?: NewsArticleCache.load(context, key)
    }

    /** 只有标题时：先搜出最像正文的网页，再按同一套本地排版抓取。 */
    suspend fun readByQuery(
        context: Context,
        query: String,
        titleHint: String = "",
        force: Boolean = false
    ): NewsArticle? = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext null
        val key = "q:${q.take(80)}"
        if (!force) NewsArticleCache.load(context, key)?.let { return@withContext it }

        val hits = runCatching {
            WebSearch.search(
                q.take(70),
                limit = 6,
                profile = WebSearch.SearchProfile.fast()
            )
        }.getOrDefault(emptyList())
        val hit = hits.firstOrNull { isArticleUrl(it.url) } ?: hits.firstOrNull()
        val article = hit?.let {
            read(context, it.url, titleHint.ifBlank { q }, force = force)
        }
        if (article != null) {
            NewsArticleCache.save(context, key, article)
            return@withContext article
        }
        val fallback = articleFromHits(q, hits)
        if (fallback != null) NewsArticleCache.save(context, key, fallback)
        fallback
    }

    private fun isArticleUrl(url: String): Boolean {
        val host = WebSafety.hostOf(url)
        if (host.isBlank()) return false
        val bad = listOf(
            "bing.", "google.", "baidu.com", "sogou.com", "so.com",
            "youtube.com", "bilibili.com/video", "douyin.com"
        )
        return bad.none { host.contains(it) }
    }

    private fun articleFromHits(query: String, hits: List<WebSearchHit>): NewsArticle? {
        if (hits.isEmpty()) return null
        val body = hits.take(6).flatMap { hit ->
            val text = hit.snippet.trim()
            if (text.length < 8) emptyList()
            else listOf(
                NewsArticleBlock(NewsBlockKind.HEADING, hit.title.ifBlank { hit.site }),
                NewsArticleBlock(NewsBlockKind.PARAGRAPH, text),
                NewsArticleBlock(
                    NewsBlockKind.BULLET,
                    "${hit.site.ifBlank { WebSafety.hostOf(hit.url) }}  ·  ${hit.url}"
                )
            )
        }
        if (body.isEmpty()) return null
        return NewsArticle(
            title = query.take(120),
            url = hits.firstOrNull()?.url.orEmpty(),
            site = hits.firstOrNull()?.site.orEmpty(),
            lead = "原网页抓取受限，以下为联网检索到的摘要，未做改写。",
            blocks = body,
            fallback = true
        )
    }

    internal fun parse(html: String, baseUrl: String, titleHint: String = ""): NewsArticle {
        val raw = html
        val metaTitle = meta(raw, "og:title", "twitter:title").ifBlank { titleTag(raw) }
        val title = cleanInline(metaTitle).ifBlank { titleHint }.ifBlank { WebSafety.hostOf(baseUrl) }
        val description = cleanInline(meta(raw, "og:description", "description", "twitter:description"))
        val cover = meta(raw, "og:image", "twitter:image")
        val published = cleanInline(
            meta(raw, "article:published_time", "og:published_time", "pubdate", "publishdate")
        )
        val author = cleanInline(meta(raw, "article:author", "author", "byline"))
        val site = cleanInline(meta(raw, "og:site_name")).ifBlank { WebSafety.hostOf(baseUrl) }

        var content = raw
        val articleMatch = Regex("(?is)<article\\b[^>]*>(.*?)</article>").findAll(content)
            .maxByOrNull { cleanInline(it.groupValues[1]).length }
        if (articleMatch != null && cleanInline(articleMatch.groupValues[1]).length > 80) {
            content = articleMatch.groupValues[1]
        }
        content = content.replace(
            Regex(
                "(?is)<script.*?</script>|<style.*?</style>|<noscript.*?</noscript>|" +
                    "<iframe.*?</iframe>|<svg.*?</svg>|<!--.*?-->"
            ),
            " "
        )

        val blocks = mutableListOf<NewsArticleBlock>()
        val seenText = mutableSetOf<String>()
        val seenImage = mutableSetOf<String>()

        fun addText(kind: NewsBlockKind, value: String, minLength: Int = -1) {
            val t = cleanInline(value)
            // 小标题和列表条目可能很短（“背景”“结论”“是”），正文块才要求足够的信息量
            val floor = if (minLength >= 0) minLength else {
                if (kind == NewsBlockKind.HEADING || kind == NewsBlockKind.BULLET) 2 else 6
            }
            if (t.length < floor) return
            if (isNoise(t)) return
            val key = t.replace(Regex("[\\s\\p{Punct}，。！？、；：“”‘’（）《》]"), "")
            if (key.length < 2 || !seenText.add(key)) return
            if (kind == NewsBlockKind.HEADING && sameTitle(t, title)) return
            blocks += NewsArticleBlock(kind, t)
        }

        fun addImage(tag: String, caption: String = "") {
            val href = imageHref(tag) ?: return
            val abs = absolutize(baseUrl, href) ?: return
            if (!usableImage(abs, tag)) return
            if (!seenImage.add(abs)) return
            blocks += NewsArticleBlock(
                NewsBlockKind.IMAGE,
                imageUrl = abs,
                caption = cleanInline(caption).take(160)
            )
        }

        fun addParagraphWithInlineImages(inner: String) {
            val imgRe = Regex("(?is)<img\\b[^>]*>")
            // 图片会把一句话切成前后两段；整段文字够多时，允许保留很短的后半句
            val totalText = cleanInline(inner.replace(imgRe, " "))
            val minPart = if (totalText.length >= 12) 2 else 6
            var cursor = 0
            imgRe.findAll(inner).forEach { m ->
                addText(NewsBlockKind.PARAGRAPH, inner.substring(cursor, m.range.first), minPart)
                addImage(m.value)
                cursor = m.range.last + 1
            }
            addText(NewsBlockKind.PARAGRAPH, inner.substring(cursor), minPart)
        }

        // 按文档顺序一次性扫描：正文块/图注块优先匹配，块之外的独立图片再按出现位置补齐。
        // 这样图片不会因为“先收集再拼接”跑到全文最前面。
        val tokenRe = Regex(
            "(?is)<(figure|p|h1|h2|h3|h4|blockquote|li)\\b[^>]*>(.*?)</\\1>|<img\\b[^>]*>"
        )
        tokenRe.findAll(content).forEach { m ->
            val tag = m.groupValues[1].lowercase()
            if (tag.isBlank()) {
                addImage(m.value, altOf(m.value))
                return@forEach
            }
            val inner = m.groupValues[2]
            when (tag) {
                "figure" -> {
                    val cap = Regex("(?is)<figcaption\\b[^>]*>(.*?)</figcaption>")
                        .find(inner)?.groupValues?.get(1).orEmpty()
                    val imgs = Regex("(?is)<img\\b[^>]*>").findAll(inner).toList()
                    imgs.forEach { addImage(it.value, cap.ifBlank { altOf(it.value) }) }
                    if (imgs.isEmpty()) addText(NewsBlockKind.PARAGRAPH, cap)
                }
                "h1", "h2", "h3", "h4" -> addText(NewsBlockKind.HEADING, inner)
                "blockquote" -> addText(NewsBlockKind.QUOTE, inner)
                "li" -> {
                    val plain = cleanInline(inner)
                    if (plain.isNotBlank()) addText(NewsBlockKind.BULLET, "· $plain")
                }
                else -> addParagraphWithInlineImages(inner)
            }
        }

        if (blocks.isEmpty() && description.isNotBlank()) {
            blocks += NewsArticleBlock(NewsBlockKind.PARAGRAPH, description.take(600))
        }
        val trimmed = blocks.take(MAX_BLOCKS)
        // 站点在正文尾部塞的「相关阅读 / 猜你喜欢 / 更多精彩」推荐区：
        // 命中标题就整段砍掉，里面的条目和缩略图不是这篇文章的内容
        val tailCut = trimmed.indexOfFirst { b ->
            b.text.length <= 24 &&
                (b.kind == NewsBlockKind.HEADING ||
                    b.kind == NewsBlockKind.BULLET ||
                    b.kind == NewsBlockKind.PARAGRAPH) &&
                TAIL_SECTION.containsMatchIn(b.text)
        }
        val body = if (tailCut >= 3) trimmed.take(tailCut) else trimmed
        val lead = description.ifBlank {
            body.firstOrNull { it.kind == NewsBlockKind.PARAGRAPH }?.text.orEmpty().take(160)
        }
        return NewsArticle(
            title = title,
            url = baseUrl,
            site = site,
            author = author,
            publishedAt = published,
            lead = lead,
            coverImage = absolutize(baseUrl, cover).orEmpty(),
            blocks = body
        )
    }

    private fun sameTitle(a: String, b: String): Boolean {
        val na = a.replace(Regex("[\\s\\p{Punct}，。！？、；：“”‘’（）《》]"), "")
        val nb = b.replace(Regex("[\\s\\p{Punct}，。！？、；：“”‘’（）《》]"), "")
        return na.length >= 4 && na == nb
    }

    private fun isNoise(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true

        // 1) 纯链接、纯联系方式、纯符号：正文里出现这些基本是推广位
        if (t.matches(Regex("(?i)^(https?://|www\\.)\\S+$"))) return true
        if (t.matches(Regex("^(投稿|商务|广告|合作|客服|联系)\\S{0,8}[：:].{0,40}$"))) return true
        if (t.count { it.isLetterOrDigit() } < 4) return true

        // 2) 站点模板与版权声明：这类常常是完整长句，不能只看长度
        if (BOILERPLATE.any { it.containsMatchIn(t) }) return true

        // 3) 广告标识：出现在行首才算（正文里讨论「广告投放」的句子不该被砍）
        if (t.length <= 40 && AD_MARKER.containsMatchIn(t)) return true

        // 4) 推荐位标签：只有「短标签且无句读」才算，真实句子（带。，！？）不受影响
        if (t.length <= 20 && !PUNCT.containsMatchIn(t) && SECTION_LABEL.any { t.contains(it) }) return true

        // 5) 社交引导 / 运营噪音：这类词基本不会出现在正文句子里
        if (t.length <= 60 && SHORT_JUNK.any { t.contains(it) }) return true

        // 6) 短促的行动号召句：点击查看、扫码领券、立即抢购……
        if (t.length <= 30 && CALL_TO_ACTION.containsMatchIn(t)) return true
        return false
    }

    private val PUNCT = Regex("[。，！？；：]")

    /** 站点模板 / 版权 / 投稿 / 引流：无论长短一律丢弃 */
    private val BOILERPLATE = listOf(
        Regex("版权(所有|声明)"),
        Regex("未经(授权|许可).{0,12}(转载|复制|使用)"),
        Regex("(转载请)?注明(来源|出处)"),
        Regex("免责声明"),
        Regex("责任编辑"),
        Regex("(扫码|扫描二维码).{0,12}(关注|下载|领取)"),
        Regex("关注.{0,8}(公众号|微信|微博)"),
        Regex("下载.{0,10}(客户端|APP|App)"),
        Regex("(商务|广告)合作"),
        Regex("(QQ群|微信群|微信号)[：:]"),
        Regex("(?i)copyright|all rights reserved")
    )

    /** 行首的广告标识 */
    private val AD_MARKER = Regex("^(广告|赞助|推广|推广内容|品牌推广|广告位|赞助内容|合作推广)")

    /** 推荐区标签：这些只可能是栏目名，不可能是正文句子 */
    private val SECTION_LABEL = listOf(
        "相关阅读", "相关推荐", "热门推荐", "延伸阅读", "推荐阅读", "相关新闻",
        "猜你喜欢", "往期回顾", "更多精彩", "精彩推荐", "热门文章", "相关专题", "相关内容"
    )

    /** 社交引导 / 运营噪音 */
    private val SHORT_JUNK = listOf(
        "扫码关注", "关注我们", "下载客户端", "打开APP", "打开 App",
        "分享到", "点赞", "转发", "评论区", "阅读全文", "点击查看",
        "上一篇", "下一篇", "返回首页", "网站地图", "登录后", "更多内容"
    )

    /** 短促的行动号召：只有整句都是诱导点击时才过滤 */
    private val CALL_TO_ACTION = Regex("^(点击|戳|猛戳|扫码|长按|立即|马上|快去|快来|抢)[^。！？\\n]{0,14}$")

    /** 正文尾部的推荐区标题：命中后整段内容砍掉 */
    private val TAIL_SECTION = Regex(
        "相关阅读|相关推荐|热门推荐|延伸阅读|推荐阅读|相关新闻|猜你喜欢|" +
            "往期回顾|更多精彩|精彩推荐|热门文章|相关内容|相关专题"
    )

    private fun meta(html: String, vararg keys: String): String {
        val wanted = keys.map { it.lowercase() }.toSet()
        Regex("(?is)<meta\\b[^>]*>").findAll(html).forEach { m ->
            val attrs = attrs(m.value)
            val name = (attrs["property"] ?: attrs["name"] ?: attrs["itemprop"])
                ?.lowercase().orEmpty()
            if (name in wanted) {
                val content = attrs["content"].orEmpty()
                if (content.isNotBlank()) return content
            }
        }
        return ""
    }

    private fun titleTag(html: String): String =
        Regex("(?is)<title\\b[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1).orEmpty()

    private fun attrs(tag: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))""")
            .findAll(tag).forEach { m ->
                val key = m.groupValues[1].lowercase()
                val value = m.groupValues[3].ifBlank { m.groupValues[4].ifBlank { m.groupValues[5] } }
                if (key.isNotBlank() && value.isNotBlank()) out[key] = decodeEntities(value)
            }
        return out
    }

    private fun imageHref(tag: String): String? {
        val a = attrs(tag)
        val candidates = listOf(
            "data-original", "data-actualsrc", "data-lazy-src", "data-src", "data-url", "src"
        ).mapNotNull { a[it]?.trim() }.filter { it.isNotBlank() }
        val srcset = a["srcset"].orEmpty()
        val fromSet = srcset.split(',')
            .mapNotNull { part -> part.trim().split(' ').firstOrNull()?.takeIf { it.startsWith("http") || it.startsWith("/") } }
            .lastOrNull()
        val picked = candidates.firstOrNull { !it.startsWith("data:") && !it.contains("blank.gif") }
            ?: fromSet
            ?: candidates.firstOrNull()
        return picked
    }

    private fun altOf(tag: String): String =
        attrs(tag)["alt"] ?: attrs(tag)["title"].orEmpty()

    private fun usableImage(url: String, tag: String): Boolean {
        val lower = url.lowercase()
        if (!lower.startsWith("http")) return false
        if (lower.endsWith(".svg") || lower.contains("data:image")) return false
        if (lower.contains("logo") || lower.contains("avatar") || lower.contains("qrcode") ||
            lower.contains("qr-code") || lower.contains("pixel") || lower.contains("spacer") ||
            lower.contains("blank.gif") || lower.contains("loading.gif") ||
            lower.contains("icon") || lower.contains("sprite")
        ) return false
        // 广告域名 / 广告位路径：与「advert」这类正文地名缩写区分开，只在带分隔符时命中
        if (AD_HOST.containsMatchIn(lower) || AD_PATH.containsMatchIn(lower)) return false
        val a = attrs(tag)
        val w = a["width"]?.filter { it.isDigit() }?.toIntOrNull()
        val h = a["height"]?.filter { it.isDigit() }?.toIntOrNull()
        if (w != null && h != null && w > 0 && h > 0) {
            if (w <= 3 || h <= 3) return false
            // 小角标 / 表情图标
            if (w <= 80 && h <= 80) return false
            // 宽扁横幅：新闻插图极少是这个比例，基本是广告条与分割线
            if (w >= 600 && h <= 60) return false
            if (w.toFloat() / h > 8f) return false
        }
        return true
    }

    /** 广告/统计服务的图片域名 */
    private val AD_HOST = Regex(
        "googlesyndication|doubleclick|adservice|alimama|tanx\\.com|pos\\.baidu|" +
            "cpro\\.|eclick|adnxs|adsystem|admaster|miaozhen|guanggao",
        RegexOption.IGNORE_CASE
    )

    /** 广告位路径：/ad/ /ads/ /banner/ /sponsor/ /promo/ 这类整段 */
    private val AD_PATH = Regex(
        "/(ad|ads|adx|adv|advert|adverts|banner|banners|sponsor|sponsors|promo|promos)[._/\\-]",
        RegexOption.IGNORE_CASE
    )

    private fun absolutize(base: String, raw: String): String? {
        val href = raw.trim().replace("&amp;", "&").replace("\\/", "/")
        if (href.isBlank() || href.startsWith("data:") || href.startsWith("javascript:")) return null
        val abs = runCatching {
            if (href.startsWith("//")) {
                URI(base).resolve("https:$href").toString()
            } else {
                URI(base).resolve(href).toString()
            }
        }.getOrNull() ?: return null
        return WebSafety.sanitize(abs)
    }

    private fun cleanInline(raw: String): String {
        var s = raw.replace(Regex("(?is)<br\\s*/?>"), "\n")
            .replace(Regex("(?is)</?(p|div|li|h\\d|section|article|blockquote)[^>]*>"), "\n")
            .replace(Regex("(?s)<[^>]+>"), " ")
        s = decodeEntities(s)
        s = s.replace(Regex("[\\t\\u00A0]+"), " ")
            .replace(Regex("[ ]{2,}"), " ")
            .replace(Regex("\\n[ ]*"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        return s
    }

    private fun decodeEntities(raw: String): String {
        var s = raw
        s = Regex("&#x([0-9a-fA-F]+);").replace(s) { m ->
            runCatching { String(Character.toChars(m.groupValues[1].toInt(16))) }.getOrDefault("")
        }
        s = Regex("&#([0-9]+);").replace(s) { m ->
            runCatching { String(Character.toChars(m.groupValues[1].toInt())) }.getOrDefault("")
        }
        val map = mapOf(
            "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"",
            "apos" to "'", "hellip" to "…", "mdash" to "—", "ndash" to "–",
            "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’",
            "middot" to "·", "times" to "×", "copy" to "©", "reg" to "®",
            "deg" to "°", "euro" to "€", "pound" to "£", "yen" to "¥",
            "bull" to "•", "laquo" to "«", "raquo" to "»", "emsp" to "　",
            "ensp" to " ", "thinsp" to " ", "zwsp" to ""
        )
        for ((k, v) in map) s = s.replace("&$k;", v)
        return s
    }

    private fun fetch(url: String, timeoutMs: Int): Pair<String?, ByteArray>? {
        listOf(MOBILE_UA, DESKTOP_UA).forEach { ua ->
            var conn: HttpURLConnection? = null
            try {
                val budget = (timeoutMs / 2).coerceAtLeast(6_000)
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = budget
                    readTimeout = budget
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", ua)
                    setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
                    setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                    setRequestProperty("Referer", "https://cn.bing.com/")
                    WebIncognito.applyTo(this)
                }
                if (conn.responseCode == 200) {
                    return conn.contentType to readAtMost(conn.inputStream, MAX_HTML)
                }
            } catch (_: Exception) {
                // 换下一个 UA
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    private fun readAtMost(input: java.io.InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        input.use { stream ->
            while (out.size() < max) {
                val n = stream.read(buf)
                if (n <= 0) break
                out.write(buf, 0, minOf(n, max - out.size()))
            }
        }
        return out.toByteArray()
    }

    private fun decode(contentType: String?, bytes: ByteArray): String {
        val header = contentType?.let {
            Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)
        }
        val head = String(bytes.copyOf(minOf(bytes.size, 2048)), Charsets.ISO_8859_1)
        val meta = Regex("charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE)
            .find(head)?.groupValues?.get(1)
        val name = (header ?: meta ?: "utf-8").lowercase()
        val charset = when (name) {
            "gb2312", "gbk", "gb18030" -> Charset.forName("GBK")
            else -> runCatching { Charset.forName(name) }.getOrDefault(Charsets.UTF_8)
        }
        return runCatching { String(bytes, charset) }.getOrDefault("")
    }
}

/** 文章 JSON 缓存：按 URL / 搜索词各存一份，未设总量上限，可由设置页统一清理。 */
object NewsArticleCache {
    private const val DIR = "news_articles"
    private val gson = Gson()

    fun load(context: Context, key: String): NewsArticle? = runCatching {
        val f = file(context, key)
        if (!f.exists() || f.length() <= 0) return null
        gson.fromJson(f.readText(Charsets.UTF_8), NewsArticle::class.java)
    }.getOrNull()

    fun save(context: Context, key: String, article: NewsArticle) = runCatching {
        val f = file(context, key)
        f.parentFile?.mkdirs()
        f.writeText(gson.toJson(article), Charsets.UTF_8)
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.cacheDir, DIR).deleteRecursively() }
    }

    private fun file(context: Context, key: String): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        return File(dir, sha256(key) + ".json")
    }
}

/** 文章图片落盘缓存：同一张图只下载一次；渲染时按屏宽降采样，避免大图撑爆内存。 */
object NewsArticleImageCache {
    private const val DIR = "news_images"
    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val MAX_BYTES = 15 * 1024 * 1024

    suspend fun local(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        val safe = WebSafety.sanitize(url) ?: return@withContext null
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val f = File(dir, sha256(safe))
        if (f.exists() && f.length() > 0) return@withContext f
        val bytes = fetch(safe) ?: return@withContext null
        runCatching {
            f.writeBytes(bytes)
            f
        }.getOrNull()
    }

    private fun fetch(url: String): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", MOBILE_UA)
                setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
                setRequestProperty("Referer", "https://cn.bing.com/")
                WebIncognito.applyTo(this)
            }
            if (conn.responseCode != 200) return null
            val len = conn.contentLengthLong
            if (len > MAX_BYTES) return null
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            conn.inputStream.use { stream ->
                while (out.size() < MAX_BYTES) {
                    val n = stream.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, minOf(n, MAX_BYTES - out.size()))
                }
            }
            val bytes = out.toByteArray()
            if (bytes.size < 32) return null
            // 有些站点会用 200 + HTML 错误页代替图片，必须真正解出宽高才落盘
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            bytes
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}

private fun sha256(raw: String): String {
    val d = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return d.joinToString("") { "%02x".format(it) }
}
