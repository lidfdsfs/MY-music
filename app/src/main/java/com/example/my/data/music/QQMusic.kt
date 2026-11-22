package com.example.my.data.music

import com.example.my.data.WebIncognito
import com.example.my.data.WebSafety
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** 内置榜单（QQ 音乐公开榜单接口，实测 8 个都可用，无需登录） */
data class ChartInfo(
    val topId: Int,
    val nameZh: String,
    val nameEn: String,
    val descZh: String,
    val descEn: String
)

/** 榜单 / 收藏歌单里的一首歌；[keyword] 直接喂给 SongSearch 去曲库找真源 */
data class ChartSong(
    val rank: Int,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSec: Int = 0,
    val mid: String = ""
) {
    val keyword: String
        get() = if (artist.isBlank()) title else "$title $artist"
}

enum class QQFailReason { NETWORK, PARSE, NEED_LOGIN, EMPTY }

sealed class QQFetch {
    data class Ok(val songs: List<ChartSong>, val name: String = "") : QQFetch()
    data class Fail(val reason: QQFailReason) : QQFetch()
}

/**
 * QQ 音乐榜单 / 歌单同步。
 *
 * 只用**公开只读**接口：
 *  - 榜单 `fcg_v8_toplist_cp.fcg?topid=…`：无需登录，返回 300 首歌名/歌手/时长；
 *  - 歌单 `fcg_v8_playlist_cp.fcg?id=…`：公开歌单匿名可读，私有「我喜欢」需要
 *    用户在内嵌浏览器里登录后带 Cookie 请求。
 *
 * 隐私边界：Cookie 由系统 WebView 管理，App 只在请求歌单时临时读取，
 * **不上传、不写入我们自己的持久化存储**；进程内留一份内存副本用于恢复
 * （网页搜索页离开时会清全局 Cookie），用户点「清除登录态」或进程结束即失效。
 * QQ 音乐没有给第三方 App 的官方收藏 API，「我喜欢」依赖网页版登录态，
 * 站点改版时可能失效，所以同时提供 `parsePlaylistText` 兜底——
 * 用户从 QQ 音乐复制歌单文本粘贴进来一样能批量入库。
 *
 * 所有地址过 [WebSafety]；结果缓存 10 分钟，避免反复下拉重复打接口。
 */
object QQMusic {

    const val LOGIN_URL = "https://y.qq.com/n/ryqq_v2/profile"
    private const val HOST = "https://c.y.qq.com/v8/fcg-bin/"
    private const val CACHE_TTL_MS = 10 * 60 * 1000L
    private const val CACHE_LIMIT = 12
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    val CHARTS = listOf(
        ChartInfo(26, "热歌榜", "Hot Songs", "最近最热的 300 首", "Top 300 hottest tracks"),
        ChartInfo(62, "飙升榜", "Rising Songs", "播放量涨得最快", "Fastest rising plays"),
        ChartInfo(4, "流行指数", "Popularity Index", "综合流行度排行", "Overall popularity"),
        ChartInfo(60, "抖音榜", "Douyin Chart", "短视频平台热歌", "Short-video hits"),
        ChartInfo(17, "内地榜", "Mainland Chart", "内地新歌排行", "Mainland China releases"),
        ChartInfo(3, "欧美榜", "Western Chart", "欧美热门单曲", "Western hits"),
        ChartInfo(16, "韩国榜", "K-Pop Chart", "韩国流行排行", "K-pop chart"),
        ChartInfo(5, "港台榜", "HK & Taiwan Chart", "港台地区排行", "HK & Taiwan chart")
    )

    private val cache = ConcurrentHashMap<String, Pair<Long, List<ChartSong>>>()

    /**
     * 进程内的登录态副本。网页搜索页离开时会清掉全局 WebView Cookie，
     * 这份副本让音乐页能把登录态恢复回去，用户不必反复扫码。
     * 不落盘、不上传，点「清除登录态」或杀进程即消失。
     */
    @Volatile
    var sessionCookie: String = ""
        private set

    fun rememberCookie(cookie: String) {
        sessionCookie = cookie.trim().take(4096)
    }

    fun forgetCookie() {
        sessionCookie = ""
    }

    // ---------- 网络 ----------

    private fun get(url: String, referer: String, cookie: String = ""): String {
        val safe = WebSafety.sanitize(url) ?: throw IllegalArgumentException("unsafe url")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(safe).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Referer", referer)
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
                WebIncognito.applyTo(this)
                if (cookie.isNotBlank()) setRequestProperty("Cookie", cookie)
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn?.disconnect()
        }
    }

    private fun cached(key: String, force: Boolean): List<ChartSong>? {
        if (force) return null
        val (at, list) = cache[key] ?: return null
        return if (System.currentTimeMillis() - at < CACHE_TTL_MS) list else null
    }

    private fun store(key: String, songs: List<ChartSong>) {
        if (cache.size >= CACHE_LIMIT) {
            cache.entries.minByOrNull { it.value.first }?.key?.let { cache.remove(it) }
        }
        cache[key] = System.currentTimeMillis() to songs
    }

    private fun commonParams(extra: String): String =
        "format=json&inCharset=utf-8&outCharset=utf-8&platform=yqq&$extra"

    // ---------- 排行榜 ----------

    /** 拉一个榜单，默认缓存 10 分钟；[limit] 控制 UI 里展示多少首（接口本身给 300 首） */
    suspend fun chart(topId: Int, limit: Int = 50, force: Boolean = false): QQFetch =
        withContext(Dispatchers.IO) {
            val key = "chart:$topId"
            cached(key, force)?.let { return@withContext QQFetch.Ok(it.take(limit)) }
            val body = try {
                get(
                    HOST + "fcg_v8_toplist_cp.fcg?" + commonParams("topid=$topId"),
                    "https://y.qq.com/n/ryqq/toplist/$topId"
                )
            } catch (_: Exception) {
                return@withContext QQFetch.Fail(QQFailReason.NETWORK)
            }
            val songs = try {
                parseChart(body)
            } catch (_: Exception) {
                return@withContext QQFetch.Fail(QQFailReason.PARSE)
            }
            if (songs.isEmpty()) return@withContext QQFetch.Fail(QQFailReason.EMPTY)
            store(key, songs)
            QQFetch.Ok(songs.take(limit))
        }

    private fun parseChart(body: String): List<ChartSong> {
        val root = JSONObject(body)
        if (root.optInt("code", -1) != 0) return emptyList()
        val arr = root.optJSONArray("songlist") ?: return emptyList()
        val out = ArrayList<ChartSong>(arr.length())
        for (i in 0 until arr.length()) {
            val d = arr.optJSONObject(i)?.optJSONObject("data") ?: continue
            val title = d.optString("songname").trim()
            if (title.isBlank()) continue
            out.add(
                ChartSong(
                    rank = out.size + 1,
                    title = title,
                    artist = singerNames(d.optJSONArray("singer")),
                    album = d.optString("albumname").trim(),
                    durationSec = d.optInt("interval", 0),
                    mid = d.optString("songmid").trim()
                )
            )
        }
        return out
    }

    private fun singerNames(arr: JSONArray?): String {
        if (arr == null) return ""
        val names = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val n = arr.optJSONObject(i)?.optString("name").orEmpty().trim()
            if (n.isNotBlank()) names.add(n)
        }
        return names.distinct().joinToString(" / ").take(60)
    }

    // ---------- 歌单 / 收藏 ----------

    /**
     * 读一个歌单。公开歌单匿名即可；「我喜欢」这类私有歌单需要把内嵌浏览器里
     * 登录后的 Cookie 传进来（只用于这一次请求，用完即弃）。
     */
    suspend fun playlist(id: String, cookie: String = "", limit: Int = 100, force: Boolean = false): QQFetch =
        withContext(Dispatchers.IO) {
            val pid = id.trim()
            // QQ 的 dissid 可能是短数字（如「我喜欢」是 201），不要要求 4 位以上
            if (!Regex("""^\d{1,20}$""").matches(pid)) {
                return@withContext QQFetch.Fail(QQFailReason.PARSE)
            }
            val key = "playlist:$pid"
            cached(key, force)?.let { return@withContext QQFetch.Ok(it.take(limit)) }
            val body = try {
                get(
                    HOST + "fcg_v8_playlist_cp.fcg?" + commonParams("id=$pid&type=1&onlysong=0&new_format=1"),
                    "https://y.qq.com/n/ryqq/playlist/$pid",
                    cookie
                )
            } catch (_: Exception) {
                return@withContext QQFetch.Fail(QQFailReason.NETWORK)
            }
            val (songs, needLogin) = try {
                parsePlaylist(body)
            } catch (_: Exception) {
                return@withContext QQFetch.Fail(QQFailReason.PARSE)
            }
            when {
                songs.isNotEmpty() -> {
                    store(key, songs)
                    QQFetch.Ok(songs.take(limit))
                }
                needLogin -> QQFetch.Fail(QQFailReason.NEED_LOGIN)
                else -> QQFetch.Fail(QQFailReason.EMPTY)
            }
        }

    /** 返回 (歌曲列表, 是否因为没登录/无权限而拿不到) */
    private fun parsePlaylist(body: String): Pair<List<ChartSong>, Boolean> {
        val root = JSONObject(body)
        if (root.optInt("code", -1) != 0) return emptyList<ChartSong>() to true
        val cdlist = root.optJSONObject("data")?.optJSONArray("cdlist")
            ?: return emptyList<ChartSong>() to true
        val first = cdlist.optJSONObject(0) ?: return emptyList<ChartSong>() to false
        val arr = first.optJSONArray("songlist") ?: return emptyList<ChartSong>() to false
        val out = ArrayList<ChartSong>(arr.length())
        for (i in 0 until arr.length()) {
            val d = arr.optJSONObject(i) ?: continue
            val title = d.optString("songname").ifBlank { d.optString("title") }.trim()
            if (title.isBlank()) continue
            out.add(
                ChartSong(
                    rank = out.size + 1,
                    title = title,
                    artist = singerNames(d.optJSONArray("singer")),
                    album = d.optString("albumname").trim(),
                    durationSec = d.optInt("interval", 0),
                    mid = d.optString("songmid").trim()
                )
            )
        }
        return out to false
    }

    /** 从歌单链接 / 分享文本 / 纯数字里提取歌单 id */
    fun extractPlaylistId(raw: String): String? {
        val s = raw.trim()
        if (Regex("""^\d{4,20}$""").matches(s)) return s
        val patterns = listOf(
            Regex("""(?:playlist|songlist|taoge)[/#?]+(\d{4,20})""", RegexOption.IGNORE_CASE),
            Regex("""[?&](?:id|disstid|dirid)=(\d{4,20})""", RegexOption.IGNORE_CASE),
            Regex("""[?&]__=\w*?(\d{6,20})""")
        )
        for (p in patterns) p.find(s)?.groupValues?.get(1)?.let { return it }
        return null
    }

    /**
     * 兜底导入：用户从 QQ 音乐复制的歌单文本（每行「序号. 歌名 - 歌手」）。
     * 这里**固定按「歌名 - 歌手」**解析，不能走 SongNameParser 的双向判定——
     * 分享文本是平台导出的，方向是确定的。
     */
    fun parsePlaylistText(text: String): List<ChartSong> {
        val out = ArrayList<ChartSong>()
        val seen = HashSet<String>()
        val siteWords = listOf("QQ音乐", "qq音乐", "酷狗", "网易云", "分享", "歌单", "http", "www.")
        val separators = Regex("""\s+[-–—－]\s+|\s+-\s+|\s+–\s+|[-–—－]""")
        for (line in text.lines()) {
            var s = line.trim()
            if (s.isBlank() || siteWords.any { s.contains(it, ignoreCase = true) }) continue
            s = s.replace(Regex("""^\d{1,3}\s*[.、,，)）:：\-]\s*"""), "").trim()
            s = s.removePrefix("《").removeSuffix("》").trim()
            s = s.replace(Regex("""\s*[\(（\[【][^)）\]】]{0,20}[\)）\]】]\s*$"""), "").trim()
            if (s.length < 2 || s.length > 80) continue
            val parts = separators.split(s).map { it.trim() }.filter { it.isNotBlank() }
            val title: String
            val artist: String
            if (parts.size >= 2) {
                title = parts[0]
                artist = parts.drop(1).joinToString(" / ").take(50)
            } else {
                title = s
                artist = ""
            }
            if (title.isBlank() || title.length > 60) continue
            val cleanTitle = title.removePrefix("《").removeSuffix("》").trim()
            if (cleanTitle.isBlank()) continue
            val key = (cleanTitle + "|" + artist).lowercase()
            if (!seen.add(key)) continue
            out.add(ChartSong(rank = out.size + 1, title = cleanTitle, artist = artist))
            if (out.size >= 200) break
        }
        return out
    }

    // ---------- 登录后：列出「我的歌单」（含我喜欢，用于一键导入） ----------

    data class QqSonglist(val dissid: String, val name: String, val trackCount: Int)

    /** 网页端 Cookie 里的 uin 原始值（通常带 o 前缀，如 o1234567890），接口参数必须保留它 */
    private fun rawUinFromCookie(cookie: String): String? {
        val m = Regex("""(?:^|;\s*)uin=(o?[A-Za-z0-9]+)""", RegexOption.IGNORE_CASE).find(cookie)
            ?: return null
        return m.groupValues[1].trim().takeIf { it.isNotBlank() }
    }

    /** 对外：纯数字 QQ 号（去 o 前缀），只用于「是否已登录」判断 */
    fun uinFromCookie(cookie: String): String? {
        val digits = rawUinFromCookie(cookie)?.trimStart('o', 'O') ?: return null
        return digits.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
    }

    /**
     * 登录态下拉当前用户的歌单列表。QQ 没有给第三方的官方收藏 API，走网页端
     * `musicu.fcg` 的 GetMySongList；需要带登录 Cookie，未登录返回空。
     */
    suspend fun fetchMySonglists(cookie: String): List<QqSonglist> = withContext(Dispatchers.IO) {
        val raw = rawUinFromCookie(cookie) ?: return@withContext emptyList()
        // 接口要求 uin 是带 o 前缀的字符串；传纯数字会返回 code=500003
        val uin = if (raw.startsWith("o", ignoreCase = true)) raw else "o$raw"
        val body = """{"comm":{"ct":24,"cv":0,"uin":"$uin"},"getSongList":{"module":"music.mylist.GetMySongList","method":"GetMySongList","param":{"uin":"$uin","begin":0,"num":30}}}"""
        val encoded = try {
            java.net.URLEncoder.encode(body, "UTF-8")
        } catch (_: Exception) {
            return@withContext emptyList()
        }
        val text = try {
            get(
                "https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&data=$encoded",
                "https://y.qq.com/n/ryqq/profile",
                cookie
            )
        } catch (_: Exception) {
            return@withContext emptyList()
        }
        runCatching { parseSonglists(text) }.getOrDefault(emptyList())
    }

    private fun parseSonglists(text: String): List<QqSonglist> {
        val root = JSONObject(text)
        if (root.optInt("code", -1) != 0) return emptyList()
        val node = root.optJSONObject("getSongList") ?: return emptyList()
        if (node.optInt("code", -1) != 0) return emptyList()
        val data = node.optJSONObject("data") ?: return emptyList()
        val arr = data.optJSONArray("songlist") ?: data.optJSONArray("v_songlist")
            ?: data.optJSONArray("list") ?: return emptyList()
        val out = ArrayList<QqSonglist>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("dissid").ifBlank { o.optString("tid") }.trim()
            val name = o.optString("dissname").ifBlank { o.optString("name") }.trim()
            if (id.isBlank() || name.isBlank()) continue
            out.add(
                QqSonglist(
                    dissid = id,
                    name = name,
                    trackCount = o.optInt("songnum", o.optInt("song_num", 0))
                )
            )
        }
        return out
    }
}
