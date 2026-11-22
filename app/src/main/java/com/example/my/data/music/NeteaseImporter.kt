package com.example.my.data.music

import android.content.Context
import com.example.my.data.WebIncognito
import com.example.my.data.WebSafety
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * 网易云音乐：二维码登录 → 拉取「我的歌单 / 我喜欢」→ 一键导入本机。
 *
 * 只用**只读 + 登录态**接口，全程直连 music.163.com（不依赖自建 NeteaseCloudMusicApi）：
 *  - 登录：`/api/login/qrcode/unikey` 取 key → `https://music.163.com/login?codekey=` 出二维码 →
 *    `/api/login/qrcode/client/login` 轮询（801 等扫 / 802 已扫 / 803 成功带 Cookie）；
 *  - 歌单：`/api/user/playlist?uid=` 列我的歌单，`/api/v6/playlist/detail?id=` 拿全量 trackIds；
 *  - 曲目：`/api/song/detail?ids=[批量]` 分批取歌名/歌手/专辑/时长。
 *
 * Cookie 只存本机 SharedPreferences（不上传），可一键退出清除。导入走 [ImportPipeline]，
 * 只写占位记录，音源播放时再取。
 */
object NeteaseImporter {

    private const val HOST = "https://music.163.com"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    private const val PREFS = "netease_session"
    private const val KEY_COOKIE = "cookie"
    private const val KEY_UID = "uid"

    /** 内存副本，避免每次读 SP；写 SP 时同步更新 */
    @Volatile private var memCookie: String? = null
    @Volatile private var memUid: Long = 0L

    data class QrKey(val key: String, val qrUrl: String)

    /** 801=等待扫码  802=已扫描待确认  803=登录成功(cookie 非空)  其余=过期/失败 */
    data class QrState(val code: Int, val message: String, val cookie: String?)

    data class NcPlaylist(
        val id: Long,
        val name: String,
        val trackCount: Int,
        val cover: String
    )

    data class NcTrack(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long
    )

    // ---------- 登录态 ----------

    fun isLoggedIn(context: Context): Boolean = cookie(context).isNotBlank()

    fun cookie(context: Context): String {
        memCookie?.let { return it }
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_COOKIE, null)?.trim().orEmpty()
        memCookie = raw
        return raw
    }

    fun uid(context: Context): Long {
        if (memUid > 0L) return memUid
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_UID, 0L)
        memUid = v
        return v
    }

    fun saveSession(context: Context, cookie: String, uid: Long) {
        val c = cookie.trim()
        memCookie = c
        memUid = uid
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_COOKIE, c)
            .putLong(KEY_UID, uid)
            .apply()
    }

    fun logout(context: Context) {
        memCookie = null
        memUid = 0L
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // ---------- 二维码登录 ----------

    suspend fun createQrKey(): QrKey? = withContext(Dispatchers.IO) {
        val resp = http("$HOST/api/login/qrcode/unikey", method = "POST", body = "type=1")
        if (resp.code !in 200..299) return@withContext null
        val key = runCatching { JSONObject(resp.text).optString("unikey").trim() }.getOrDefault("")
        if (key.isBlank()) null else QrKey(key, "$HOST/login?codekey=$key")
    }

    suspend fun pollQr(key: String): QrState = withContext(Dispatchers.IO) {
        val url = "$HOST/api/login/qrcode/client/login?key=" +
            URLEncoder.encode(key, "UTF-8") + "&type=1"
        val resp = http(url, cookie = "")
        val root = runCatching { JSONObject(resp.text) }.getOrNull()
        val code = root?.optInt("code", -1) ?: -1
        val msg = root?.optString("message").orEmpty()
        // 关键：803 时凭证在 JSON body 的 cookie 字段里（MUSIC_U=…; __csrf=…），
        // 不在响应头。只读响应头会拿到空串，导致“登录成功却没凭证”。
        val cookie = if (code == 803) {
            root?.optString("cookie").orEmpty().trim()
                .ifBlank { resp.cookie }
                .takeIf { it.isNotBlank() }
        } else null
        QrState(code, msg, cookie)
    }

    /** 登录成功后取 uid；失败返回 null（调用方应提示重新扫码） */
    suspend fun fetchUid(cookie: String): Long? = withContext(Dispatchers.IO) {
        val resp = http("$HOST/api/nuser/account/get", cookie = cookie)
        if (resp.code !in 200..299) return@withContext null
        runCatching {
            val root = JSONObject(resp.text)
            root.optJSONObject("profile")?.optLong("userId", 0L)
                ?.takeIf { it > 0L }
                ?: root.optJSONObject("account")?.optLong("id", 0L)?.takeIf { it > 0L }
        }.getOrNull()
    }

    // ---------- 歌单 / 曲目 ----------

    private val playlistCache = ConcurrentHashMap<Long, Pair<Long, List<NcPlaylist>>>()

    suspend fun fetchUserPlaylists(context: Context): List<NcPlaylist> {
        val ck = cookie(context)
        if (ck.isBlank()) return emptyList()
        val uid = uid(context).takeIf { it > 0L } ?: fetchUid(ck) ?: return emptyList()
        if (uid(context) <= 0L) saveSession(context, ck, uid)
        return fetchUserPlaylists(uid, ck)
    }

    suspend fun fetchUserPlaylists(uid: Long, cookie: String): List<NcPlaylist> =
        withContext(Dispatchers.IO) {
            playlistCache[uid]?.takeIf { System.currentTimeMillis() - it.first < 3 * 60 * 1000L }
                ?.let { return@withContext it.second }
            val url = "$HOST/api/user/playlist?uid=$uid&limit=100&offset=0"
            val resp = http(url, cookie = cookie)
            if (resp.code !in 200..299) return@withContext emptyList()
            val list = runCatching { parsePlaylists(resp.text) }.getOrDefault(emptyList())
            playlistCache[uid] = System.currentTimeMillis() to list
            list
        }

    private fun parsePlaylists(text: String): List<NcPlaylist> {
        val root = JSONObject(text)
        if (root.optInt("code", -1) != 200) return emptyList()
        val arr = root.optJSONArray("playlist") ?: return emptyList()
        val out = ArrayList<NcPlaylist>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            val id = o.optLong("id", 0L)
            if (name.isBlank() || id <= 0L) continue
            out.add(
                NcPlaylist(
                    id = id,
                    name = name,
                    trackCount = o.optInt("trackCount", 0),
                    cover = o.optString("coverImgUrl").let { if (it.startsWith("http://")) "https://" + it.removePrefix("http://") else it }
                )
            )
        }
        return out
    }

    suspend fun fetchPlaylistTracks(context: Context, playlist: NcPlaylist): List<NcTrack> {
        val ck = cookie(context)
        if (ck.isBlank()) return emptyList()
        return fetchPlaylistTracks(playlist.id, ck)
    }

    suspend fun fetchPlaylistTracks(pid: Long, cookie: String): List<NcTrack> =
        withContext(Dispatchers.IO) {
            val detail = http("$HOST/api/v6/playlist/detail?id=$pid&n=1000", cookie = cookie)
            if (detail.code !in 200..299) return@withContext emptyList()
            val ids = runCatching {
                val root = JSONObject(detail.text)
                val p = root.optJSONObject("playlist") ?: return@withContext emptyList()
                val tids = p.optJSONArray("trackIds") ?: return@withContext emptyList()
                (0 until tids.length()).mapNotNull {
                    tids.optJSONObject(it)?.optLong("id", 0L)?.takeIf { it > 0L }
                }
            }.getOrDefault(emptyList())
            if (ids.isEmpty()) return@withContext emptyList()

            // song/detail 一次最多 ~1000 个，保守按 500 分批，避免 URL 过长被拒
            val out = ArrayList<NcTrack>(ids.size)
            val seen = HashSet<Long>()
            ids.chunked(500).forEach { chunk ->
                val joined = chunk.joinToString(",")
                val url = "$HOST/api/song/detail?ids=" +
                    URLEncoder.encode("[$joined]", "UTF-8")
                val resp = http(url, cookie = cookie)
                if (resp.code in 200..299) {
                    runCatching { parseSongs(resp.text) }.getOrDefault(emptyList())
                        .forEach { t -> if (seen.add(t.id)) out.add(t) }
                }
            }
            // 按歌单原序排
            val order = ids.withIndex().associate { it.value to it.index }
            out.sortedBy { order[it.id] ?: Int.MAX_VALUE }
        }

    private fun parseSongs(text: String): List<NcTrack> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("songs") ?: return emptyList()
        val out = ArrayList<NcTrack>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val id = s.optLong("id", 0L)
            val name = s.optString("name").ifBlank { s.optString("title") }.trim()
            if (id <= 0L || name.isBlank()) continue
            val artists = s.optJSONArray("ar") ?: s.optJSONArray("artists")
            val artist = artistNames(artists)
            val album = s.optJSONObject("al")?.optString("name").orEmpty().trim()
                .ifBlank { s.optJSONObject("album")?.optString("name").orEmpty().trim() }
            out.add(
                NcTrack(
                    id = id,
                    title = name,
                    artist = artist,
                    album = album,
                    durationMs = s.optLong("dt", s.optLong("duration", 0L))
                )
            )
        }
        return out
    }

    private fun artistNames(arr: JSONArray?): String {
        if (arr == null) return ""
        val names = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.optString("name")?.trim()?.takeIf { it.isNotBlank() }
                ?.let { names.add(it) }
        }
        return names.distinct().joinToString(" / ").take(60)
    }

    // ---------- 一键导入 ----------

    suspend fun importPlaylist(
        context: Context,
        playlist: NcPlaylist
    ): ImportPipeline.Report {
        val tracks = fetchPlaylistTracks(context, playlist)
        // 空曲目几乎都是登录态失效/接口拒绝，不能当成“全部已收藏”静默跳过
        if (tracks.isEmpty()) {
            throw IllegalStateException("未读取到该歌单曲目，可能登录已过期，请退出后重新扫码")
        }
        return ImportPipeline.importTracks(
            context = context,
            source = "netease",
            sourceLabel = "网易云音乐",
            collectionId = "netease:${playlist.id}",
            collectionName = playlist.name,
            tracks = tracks.map {
                ImportPipeline.Track(it.title, it.artist, it.album, it.durationMs)
            }
        )
    }

    // ---------- 网络 ----------

    private data class Resp(val code: Int, val text: String, val cookie: String)

    private fun http(
        urlStr: String,
        method: String = "GET",
        body: String = "",
        cookie: String = ""
    ): Resp {
        val safe = WebSafety.sanitize(urlStr) ?: throw IllegalArgumentException("unsafe url")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(safe).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                requestMethod = method
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Referer", "https://music.163.com/")
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                WebIncognito.applyTo(this)
                if (cookie.isNotBlank()) setRequestProperty("Cookie", cookie)
                instanceFollowRedirects = true
                if (method == "POST" && body.isNotBlank()) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val setCookie = extractCookie(conn)
            return Resp(code, text, setCookie)
        } finally {
            conn?.disconnect()
        }
    }

    /** 从响应头拼出 Cookie 串（name=value; ...），去重保留最后值。header 名大小写不敏感。 */
    private fun extractCookie(conn: HttpURLConnection): String {
        val map = LinkedHashMap<String, String>()
        conn.headerFields.forEach { (name, values) ->
            if (name == null || !name.equals("Set-Cookie", ignoreCase = true)) return@forEach
            values?.forEach { raw ->
                val pair = raw.substringBefore(';').trim()
                val idx = pair.indexOf('=')
                if (idx > 0) {
                    val key = pair.substring(0, idx).trim()
                    val value = pair.substring(idx + 1).trim()
                    if (key.isNotBlank()) map[key] = value
                }
            }
        }
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
}
