package com.example.my.data.music

import java.net.URLEncoder

/**
 * 封面地址的纯函数部分：不碰 Android，方便 JVM 单测。
 * QQ 音乐公开封面 CDN 只认 albummid，不接受整段歌名/歌手。
 */
object CoverUrls {

    const val QQ_ALBUM_BASE = "https://y.qq.com/music/photo_new/T002R500x500M000"
    private val QQ_MID = Regex("""^[A-Za-z0-9]{5,40}$""")
    private val IMAGE_EXT = Regex("""\.(jpe?g|png|webp|gif|bmp)(?:$|[?#])""", RegexOption.IGNORE_CASE)

    /** albummid -> 500x500 封面；非法 mid 直接拒绝，避免把用户输入拼进 CDN 路径 */
    fun qqCover(albumMid: String?): String? {
        val mid = albumMid?.trim().orEmpty()
        if (mid.isEmpty() || !QQ_MID.matches(mid)) return null
        return "$QQ_ALBUM_BASE$mid.jpg"
    }

    fun bingSearchUrl(title: String, artist: String): String {
        val q = listOf(title, artist, "专辑封面").filter { it.isNotBlank() }.joinToString(" ")
        return "https://www.bing.com/images/search?q=" +
            URLEncoder.encode(q, "UTF-8") + "&first=1&count=20"
    }

    fun baiduImageSearchUrl(title: String, artist: String): String {
        val q = listOf(title, artist, "封面").filter { it.isNotBlank() }.joinToString(" ")
        return "https://image.baidu.com/search/index?tn=baiduimage&word=" +
            URLEncoder.encode(q, "UTF-8")
    }

    /** 归一化歌名/歌手：去掉括号版本、标点和大小写，只用于匹配，不用于落盘 */
    fun normalize(s: String): String = s.lowercase()
        .replace(Regex("""\([^)]*\)|\[[^]]*]|（[^）]*）|【[^】]*】"""), " ")
        .replace(Regex("""[^a-z0-9\u4e00-\u9fff\u3040-\u30ff]"""), "")

    /**
     * 候选封面匹配分：标题相等 > 包含；歌手相等再加分。
     * 低于 40 的候选不取，宁可没有封面也不贴错别人的专辑。
     */
    fun matchScore(title: String, artist: String, candTitle: String, candArtist: String): Int {
        val t = normalize(title)
        val c = normalize(candTitle)
        if (t.isBlank() || c.isBlank()) return 0
        var s = when {
            t == c -> 100
            c.contains(t) || t.contains(c) -> 55
            else -> 0
        }
        val a = normalize(artist)
        val ca = normalize(candArtist)
        if (a.isNotBlank() && ca.isNotBlank()) {
            s += when {
                a == ca -> 35
                ca.contains(a) || a.contains(ca) -> 15
                else -> -80
            }
        }
        return s
    }

    /** Bing 图片页里 murl 是原图，turl 只是缩略图；优先原图，再去重 */
    fun extractBingImageUrls(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val normalized = html.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("\\/", "/")
        val out = LinkedHashSet<String>()
        Regex(""""murl"\s*:\s*"([^"]+)"""").findAll(normalized).forEach { m ->
            val u = m.groupValues[1].trim()
            if (u.startsWith("http://") || u.startsWith("https://")) out.add(u)
        }
        if (out.isEmpty()) {
            Regex(""""turl"\s*:\s*"([^"]+)"""").findAll(normalized).forEach { m ->
                val u = m.groupValues[1].trim()
                if (u.startsWith("http://") || u.startsWith("https://")) out.add(u)
            }
        }
        return out.toList()
    }

    fun isLikelyImage(url: String): Boolean {
        if (IMAGE_EXT.containsMatchIn(url.substringBefore('?'))) return true
        val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
        return host.contains("y.qq.com") || host.contains("music.163.com") ||
            host.contains("doubanio") || host.contains("img") || host.contains("photo")
    }
}
