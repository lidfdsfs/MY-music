package com.example.my.data.music

import android.content.Context
import com.example.my.data.WebIncognito
import com.example.my.data.WebSafety
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** 歌曲评论的三档排序：推荐 / 热度 / 时间（对应 NeteaseCloudMusicApi 的 sortType 1/2/3） */
enum class CommentSort(val labelZh: String, val labelEn: String, val sortType: Int) {
    RECOMMEND("推荐", "Recommended", 1),
    HOT("热度", "Hottest", 2),
    NEW("最新", "Newest", 3)
}

/** 楼中楼里的一条回复（网易云只给前几条预览，不展开无限层级） */
data class NeteaseReply(
    val nickname: String,
    val content: String
)

/** 一条歌曲评论 */
data class NeteaseComment(
    val id: Long,
    val nickname: String,
    val avatarUrl: String,
    val content: String,
    /** epoch 毫秒；0 表示接口没给 */
    val timeMs: Long,
    val likedCount: Int,
    val liked: Boolean,
    val location: String,
    val replies: List<NeteaseReply>,
    val replyCount: Int,
    val hot: Boolean
)

/** 一页评论 */
data class NeteaseCommentPage(
    val total: Int,
    val comments: List<NeteaseComment>,
    val hasMore: Boolean,
    val cursor: Long?,
    /** true = 走的是 NeteaseCloudMusicApi 的 /comment/new 或 /comment/hot */
    val viaApi: Boolean,
    /** 实际匹配到的网易云歌曲名，用于在标题区提示 */
    val matchedSong: String = ""
)

enum class CommentFailReason {
    NETWORK,
    NO_SONG,
    BAD_API,
    PARSE,
    EMPTY
}

sealed class CommentFetch {
    data class Ok(val page: NeteaseCommentPage) : CommentFetch()
    data class Fail(val reason: CommentFailReason, val detail: String? = null) : CommentFetch()
}

/**
 * 网易云歌曲评论数据源。
 *
 * 设计取舍：
 * 1. **优先 NeteaseCloudMusicApi**（/comment/new 支持 sortType=1/2/3 与分页；/comment/hot 取热门），
 *    基址由用户在设置里填写，必须是 HTTPS（App 全项目禁明文 HTTP）。
 * 2. 没有配置基址、或基址暂时不可用时，自动回落到网易云网页版公开评论接口
 *    `music.163.com/api/v1/resource/comments/R_SO_4_<id>`。这条链路只读、无需登 App 账号，
 *    能保证“不填服务器也能看到评论”，但按时间/推荐排序只能在本地对返回结果再排一次。
 * 3. 本地歌曲没有网易云 songId，因此先用歌名 + 歌手（必要时带时长）在网易云搜索里打分取 ID，
 *    结果按 `本地 id + 歌名 + 歌手` 缓存；同名二创/翻唱优先选标题与歌手的共同命中。
 *
 * 所有请求只读：不发评论、不点赞、不读取用户 Cookie，避免把账号态带进这个功能。
 */
object NeteaseComments {

    private const val DIRECT_HOST = "https://music.163.com"
    private const val SEARCH_API = "$DIRECT_HOST/api/search/get"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val CACHE_TTL_MS = 3 * 60 * 1000L
    private const val PAGE_CACHE_LIMIT = 48

    private data class CachedPage(val at: Long, val page: NeteaseCommentPage)
    private data class SongCandidate(
        val id: Long,
        val title: String,
        val artist: String,
        val durationMs: Long
    )

    private val idCache = ConcurrentHashMap<String, Long>()
    private val pageCache = ConcurrentHashMap<String, CachedPage>()

    /** 配置了 NeteaseCloudMusicApi 基址时走官方封装接口；否则走网页公开接口兜底 */
    fun apiBase(ctx: Context?): String? = NeteaseCommentPrefs.apiBase(ctx)

    fun clearCache() {
        idCache.clear()
        pageCache.clear()
        matchedNames.clear()
    }

    /**
     * 读取一页评论。
     *
     * @param page 从 1 开始
     * @param cursor sortType=3 且 page>1 时传上一页最后一条评论的 time
     */
    suspend fun load(
        ctx: Context?,
        song: LocalSong?,
        sort: CommentSort,
        page: Int = 1,
        pageSize: Int = 20,
        cursor: Long? = null,
        force: Boolean = false
    ): CommentFetch = withContext(Dispatchers.IO) {
        if (song == null) return@withContext CommentFetch.Fail(CommentFailReason.NO_SONG, "没有正在播放的歌曲")

        val safePage = page.coerceAtLeast(1)
        val key = cacheKey(song, sort, safePage, pageSize)
        if (!force) {
            pageCache[key]?.let { cached ->
                if (System.currentTimeMillis() - cached.at < CACHE_TTL_MS) {
                    return@withContext CommentFetch.Ok(cached.page)
                }
            }
        }

        val base = apiBase(ctx)
        val firstError = StringBuilder()

        if (base != null) {
            runCatching {
                fetchViaApi(base, song, sort, safePage, pageSize, cursor)
            }.onSuccess { result ->
                if (result is CommentFetch.Ok) {
                    putPage(key, result.page)
                    return@withContext result
                }
                firstError.append(((result as? CommentFetch.Fail)?.detail ?: "接口返回失败"))
            }.onFailure { e ->
                firstError.append(e.message ?: "接口请求失败")
            }
        }

        val direct = runCatching {
            fetchDirect(song, sort, safePage, pageSize)
        }.getOrElse { e ->
            CommentFetch.Fail(
                CommentFailReason.NETWORK,
                if (firstError.isNotEmpty()) "$firstError / ${e.message}" else (e.message ?: "网络异常")
            )
        }
        if (direct is CommentFetch.Ok) {
            putPage(key, direct.page)
            return@withContext direct
        }
        val detail = buildString {
            if (firstError.isNotEmpty()) append("NeteaseCloudMusicApi：").append(firstError)
            val directDetail = (direct as? CommentFetch.Fail)?.detail
            if (!directDetail.isNullOrBlank()) {
                if (isNotEmpty()) append("；")
                append("网易云官方接口：").append(directDetail)
            }
        }.takeIf { it.isNotBlank() }
        direct
    }

    /** 供 UI 打开前预估总评论数；失败返回 null，不阻塞评论列表 */
    suspend fun totalCount(ctx: Context?, song: LocalSong?): Int? = withContext(Dispatchers.IO) {
        if (song == null) return@withContext null
        when (val r = load(ctx, song, CommentSort.RECOMMEND, 1, 1)) {
            is CommentFetch.Ok -> r.page.total.takeIf { it > 0 }
            is CommentFetch.Fail -> null
        }
    }

    // ---------- NeteaseCloudMusicApi ----------

    private fun fetchViaApi(
        base: String,
        song: LocalSong,
        sort: CommentSort,
        page: Int,
        pageSize: Int,
        cursor: Long?
    ): CommentFetch {
        val songId = resolveSongId(base, song)
            ?: return CommentFetch.Fail(CommentFailReason.NO_SONG, "网易云没有找到「${song.title}」")
        val matched = matchedName(song, songId)

        val params = if (sort == CommentSort.HOT) {
            "type=0&id=$songId&limit=$pageSize&offset=${(page - 1) * pageSize}"
        } else {
            buildString {
                append("type=0&id=").append(songId)
                append("&sortType=").append(sort.sortType)
                append("&pageNo=").append(page)
                append("&pageSize=").append(pageSize)
                if (sort == CommentSort.NEW && page > 1 && cursor != null && cursor > 0) {
                    append("&cursor=").append(cursor)
                }
            }
        }
        val path = if (sort == CommentSort.HOT) "/comment/hot" else "/comment/new"
        val json = getJson("${base.trimEnd('/')}$path?$params")
        if (json == null) return CommentFetch.Fail(CommentFailReason.BAD_API, "接口没有返回内容")

        val code = json.optInt("code", 200)
        if (code != 200) {
            val message = json.optString("msg").ifBlank { json.optString("message") }
            return CommentFetch.Fail(
                CommentFailReason.BAD_API,
                "code=$code${if (message.isBlank()) "" else " $message"}"
            )
        }
        val parsed = parsePage(json, sort, viaApi = true, matched, includeHot = page == 1, pageSize = pageSize)
        return if (parsed.comments.isEmpty()) {
            // 接口 200 但没解析出评论：可能是这套 NCMAPI 的返回结构不兼容，继续走公开接口兜底
            CommentFetch.Fail(CommentFailReason.BAD_API, "接口返回 200，但没有可用评论")
        } else {
            CommentFetch.Ok(parsed)
        }
    }

    private fun resolveSongId(base: String, song: LocalSong): Long? {
        val key = idKey(song)
        idCache[key]?.let { return it }
        val title = runCatching { SongNameParser.clean(song.title) }.getOrDefault(song.title).trim()
        val artist = runCatching { SongNameParser.cleanArtist(song.artist) }
            .getOrNull().orEmpty().trim().ifBlank { song.artist.trim() }
        if (title.isBlank()) return null

        val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val candidates = runCatching {
            val api = getJson("${base.trimEnd('/')}/search?keywords=$encoded&type=1&limit=8")
            parseSearchSongs(api, preferCloud = true)
        }.getOrNull().orEmpty().ifEmpty {
            runCatching {
                val direct = getJson("$SEARCH_API?s=$encoded&type=1&offset=0&limit=8")
                parseSearchSongs(direct, preferCloud = false)
            }.getOrNull().orEmpty()
        }
        if (candidates.isEmpty()) return null

        val picked = candidates.maxByOrNull { score(song, title, artist, it) } ?: return null
        val bestScore = score(song, title, artist, picked)
        // 标题完全对不上就不硬凑：宁可提示“没找到”，也不把另一首歌的评论挂上来
        if (bestScore < 55) return null
        idCache[key] = picked.id
        matchedNames[key] = picked.title
        return picked.id
    }

    private val matchedNames = ConcurrentHashMap<String, String>()

    private fun matchedName(song: LocalSong, id: Long): String =
        matchedNames[idKey(song)] ?: song.title

    private fun parseSearchSongs(o: JSONObject?, preferCloud: Boolean): List<SongCandidate> {
        if (o == null) return emptyList()
        val root = o.optJSONObject("result") ?: o
        val arr = root.optJSONArray("songs")
            ?: root.optJSONObject("result")?.optJSONArray("songs")
            ?: JSONArray()
        val out = ArrayList<SongCandidate>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val id = s.optLong("id", 0L)
            if (id <= 0L) continue
            val name = s.optString("name").ifBlank { s.optString("title") }
            if (name.isBlank()) continue
            val artists = s.optJSONArray("artists") ?: s.optJSONArray("ar")
            val artistNames = buildList {
                if (artists != null) {
                    for (j in 0 until artists.length()) {
                        val a = artists.optJSONObject(j) ?: continue
                        a.optString("name").takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
                if (isEmpty()) {
                    s.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() }?.let { add(it) }
                    s.optJSONArray("ar")?.let { ar ->
                        for (j in 0 until ar.length()) {
                            ar.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                }
            }.joinToString("/")
            val duration = s.optLong("duration", s.optLong("dt", 0L))
            out += SongCandidate(id, name, artistNames, duration)
        }
        // preferCloud 只是语义标记，两个接口字段都已兼容；保留参数方便以后区分
        if (preferCloud) Unit
        return out
    }

    private fun score(song: LocalSong, title: String, artist: String, c: SongCandidate): Int {
        val wantTitle = norm(title)
        val gotTitle = norm(c.title)
        if (wantTitle.isBlank() || gotTitle.isBlank()) return 0
        var score = when {
            wantTitle == gotTitle -> 100
            gotTitle.contains(wantTitle) || wantTitle.contains(gotTitle) -> 62
            else -> 0
        }
        if (score == 0) return 0

        val wantArtist = norm(artist)
        if (wantArtist.isNotBlank()) {
            val gotArtist = norm(c.artist)
            when {
                gotArtist == wantArtist -> score += 34
                gotArtist.contains(wantArtist) || wantArtist.contains(gotArtist) -> score += 26
                else -> {
                    val tokens = wantArtist.split('/', '&', ',', '、', ' ')
                        .map { it.trim() }.filter { it.length >= 2 }
                    val hits = tokens.count { gotArtist.contains(it) }
                    if (hits > 0) score += (hits * 10).coerceAtMost(24)
                }
            }
        }
        if (song.durationMs > 0 && c.durationMs > 0) {
            val diff = kotlin.math.abs(song.durationMs - c.durationMs)
            score += when {
                diff <= 2_000 -> 30
                diff <= 8_000 -> 18
                diff <= 20_000 -> 8
                diff > 60_000 -> -18
                else -> 0
            }
        }
        return score
    }

    private fun norm(s: String): String =
        s.lowercase().replace(Regex("[\\s\\p{Punct}·•—–_]+"), "")

    // ---------- 网易云网页公开接口（兜底） ----------

    private fun fetchDirect(song: LocalSong, sort: CommentSort, page: Int, pageSize: Int): CommentFetch {
        val songId = directResolveSongId(song)
            ?: return CommentFetch.Fail(CommentFailReason.NO_SONG, "网易云没有找到「${song.title}」")
        val offset = (page - 1) * pageSize
        val json = getJson(
            "$DIRECT_HOST/api/v1/resource/comments/R_SO_4_$songId?limit=$pageSize&offset=$offset"
        )
            ?: return CommentFetch.Fail(CommentFailReason.NETWORK, "评论接口无响应")
        val parsed = parsePage(json, sort, viaApi = false, matchedName(song, songId), includeHot = page == 1, pageSize = pageSize)
        return CommentFetch.Ok(parsed)
    }

    private fun directResolveSongId(song: LocalSong): Long? {
        val key = idKey(song)
        idCache[key]?.let { return it }
        val title = runCatching { SongNameParser.clean(song.title) }.getOrDefault(song.title).trim()
        val artist = runCatching { SongNameParser.cleanArtist(song.artist) }
            .getOrNull().orEmpty().trim().ifBlank { song.artist.trim() }
        if (title.isBlank()) return null
        val q = URLEncoder.encode(listOf(title, artist).filter { it.isNotBlank() }.joinToString(" "), "UTF-8")
        val json = getJson("$SEARCH_API?s=$q&type=1&offset=0&limit=8") ?: return null
        val list = parseSearchSongs(json, preferCloud = false)
        val picked = list.maxByOrNull { score(song, title, artist, it) } ?: return null
        if (score(song, title, artist, picked) < 55) return null
        idCache[key] = picked.id
        matchedNames[key] = picked.title
        return picked.id
    }

    // ---------- 解析 ----------

    private fun parsePage(
        root: JSONObject,
        sort: CommentSort,
        viaApi: Boolean,
        matchedSong: String,
        includeHot: Boolean = true,
        pageSize: Int = 20
    ): NeteaseCommentPage {
        val data = root.optJSONObject("data")
        val commentsArr = data?.optJSONArray("comments") ?: root.optJSONArray("comments")
        val hotArr = data?.optJSONArray("hotComments") ?: root.optJSONArray("hotComments")
        val total = when {
            data?.has("totalCount") == true -> data.optInt("totalCount")
            root.has("total") -> root.optInt("total")
            else -> 0
        }
        val sourceLen = if (sort == CommentSort.HOT && hotArr != null) {
            hotArr.length()
        } else {
            commentsArr?.length() ?: 0
        }
        val hasMore = when {
            data?.has("hasMore") == true -> data.optBoolean("hasMore")
            root.has("more") -> root.optBoolean("more")
            else -> sourceLen >= pageSize
        }
        val cursor = data?.optLong("cursor", 0L)?.takeIf { it > 0 }
            ?: commentsArr?.let { arr ->
                if (arr.length() == 0) null else arr.optJSONObject(arr.length() - 1)?.optLong("time", 0L)
                    ?.takeIf { it > 0 }
            }

        val main = ArrayList<NeteaseComment>()
        val sourceArr = if (sort == CommentSort.HOT && hotArr != null) hotArr else commentsArr
        if (sourceArr != null) {
            for (i in 0 until sourceArr.length()) {
                parseComment(sourceArr.optJSONObject(i), hot = sort == CommentSort.HOT)?.let { main += it }
            }
        }
        if (sort == CommentSort.NEW) {
            main.sortByDescending { it.timeMs }
        }
        val deduped = main.distinctBy { it.id }.let { if (it.isEmpty()) main else it }
        // 只有「推荐」把热评单独置顶；「最新」保持严格时间序，热门页本身已是热评
        val hot = if (sort == CommentSort.RECOMMEND && includeHot && hotArr != null) {
            buildList {
                for (i in 0 until hotArr.length()) {
                    parseComment(hotArr.optJSONObject(i), hot = true)?.let { add(it) }
                }
            }
        } else emptyList()
        val merged = if (hot.isEmpty()) deduped else (hot + deduped).distinctBy { it.id }
        val pageTotal = total.coerceAtLeast(merged.size)
        return NeteaseCommentPage(
            total = pageTotal,
            comments = merged,
            hasMore = hasMore,
            cursor = cursor,
            viaApi = viaApi,
            matchedSong = matchedSong
        )
    }

    private fun parseComment(o: JSONObject?, hot: Boolean): NeteaseComment? {
        if (o == null) return null
        val id = o.optLong("commentId", o.optLong("id", 0L))
        val content = o.optString("content").trim()
        if (content.isBlank()) return null
        val user = o.optJSONObject("user")
        val nickname = user?.optString("nickname")?.ifBlank { "网易云用户" } ?: "网易云用户"
        val avatar = user?.optString("avatarUrl").orEmpty().let {
            if (it.startsWith("http://")) "https://" + it.removePrefix("http://") else it
        }
        val repliesArr = o.optJSONArray("beReplied")
        val replies = buildList {
            if (repliesArr != null) {
                for (i in 0 until repliesArr.length()) {
                    val r = repliesArr.optJSONObject(i) ?: continue
                    val rContent = r.optString("content").trim()
                    if (rContent.isBlank()) continue
                    val rName = r.optJSONObject("user")?.optString("nickname").orEmpty()
                        .ifBlank { "网易云用户" }
                    add(NeteaseReply(rName, rContent))
                }
            }
        }.take(3)
        val location = when (val loc = o.opt("ipLocation")) {
            is JSONObject -> loc.optString("location")
            is String -> loc
            else -> ""
        }.trim()
        val replyCount = o.optInt("replyCount", replies.size)
        return NeteaseComment(
            id = if (id > 0L) id else (System.currentTimeMillis() + content.hashCode()).toLong(),
            nickname = nickname,
            avatarUrl = avatar,
            content = content,
            timeMs = o.optLong("time", 0L),
            likedCount = o.optInt("likedCount", 0).coerceAtLeast(0),
            liked = o.optBoolean("liked", false),
            location = location,
            replies = replies,
            replyCount = replyCount.coerceAtLeast(replies.size),
            hot = hot
        )
    }

    // ---------- 网络 ----------

    private fun getJson(url: String): JSONObject? {
        var conn: HttpURLConnection? = null
        try {
            val safe = WebSafety.sanitize(url) ?: return null
            conn = (URL(safe).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Referer", "https://music.163.com/")
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
                WebIncognito.applyTo(this)
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (text.isBlank()) return null
            return JSONObject(text)
        } finally {
            conn?.disconnect()
        }
    }

    private fun idKey(song: LocalSong): String =
        "${song.id}|${song.title.trim().lowercase()}|${song.artist.trim().lowercase()}"

    private fun cacheKey(song: LocalSong, sort: CommentSort, page: Int, pageSize: Int): String =
        "${idKey(song)}|${sort.name}|$page|$pageSize"

    private fun putPage(key: String, page: NeteaseCommentPage) {
        if (pageCache.size >= PAGE_CACHE_LIMIT) {
            pageCache.entries.minByOrNull { it.value.at }?.key?.let { pageCache.remove(it) }
        }
        pageCache[key] = CachedPage(System.currentTimeMillis(), page)
    }
}

/**
 * NeteaseCloudMusicApi 基址保存。
 * App 全局禁止明文 HTTP，所以这里只接受 HTTPS；地址里不允许账号密码、查询串或锚点。
 */
object NeteaseCommentPrefs {

    private const val PREFS = "netease_comment_prefs"
    private const val KEY_BASE = "api_base"

    fun apiBase(ctx: Context?): String? {
        if (ctx == null) return null
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BASE, null)
        return normalizeApiBase(raw)
    }

    /** 返回是否保存成功；传空串/空白 = 清除配置，回落到官方公开接口 */
    fun setApiBase(ctx: Context, raw: String?): Boolean {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_BASE).apply()
            NeteaseComments.clearCache()
            return true
        }
        val safe = normalizeApiBase(trimmed) ?: return false
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_BASE, safe).apply()
        NeteaseComments.clearCache()
        return true
    }
}

/** 纯函数，方便单测：只允许 HTTPS、主机名合法、无账号信息/查询串/锚点 */
internal fun normalizeApiBase(raw: String?): String? {
    val value = raw?.trim().orEmpty().trimEnd('/')
    if (value.isEmpty()) return null
    if (!value.startsWith("https://", ignoreCase = true)) return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    if (uri.rawUserInfo != null) return null
    if (uri.host.isNullOrBlank()) return null
    if (uri.query != null || uri.fragment != null) return null
    if (value.length > WebSafety.MAX_URL_LENGTH) return null
    return WebSafety.sanitize(value)?.trimEnd('/')
}
