package com.example.my.data.microfeed

import android.content.Context
import com.example.my.data.BackendApi
import com.example.my.data.CloudConfig
import com.example.my.model.MicroAuthor
import com.example.my.model.MicroComment
import com.example.my.model.MicroPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 微足迹独立服务（microfeed-backend）的客户端。
 *
 * 与原功能后端是两个服务：地址、数据、媒体都分开，只有登录 token 复用——
 * 服务端用同一个 SECRET_KEY 验签，所以这里直接把 [BackendApi.token] 带过去即可。
 *
 * 约定与 BackendApi 一致：失败返回 null / false，调用方静默回落到本地公开池，
 * 保证「服务器挂了也不影响刷已缓存的足迹」。
 */
object MicroFeedApi {

    private const val PREFS = "micro_feed_remote"
    private const val KEY_URL = "base_url"

    @Volatile
    private var overrideUrl: String? = null

    @Volatile
    var lastError: String? = null
        private set

    /** 最近一次请求拿到的 HTTP 状态码；0 表示连响应都没拿到（网络/证书/TLS 层就失败了） */
    @Volatile
    var lastStatus: Int = 0
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_URL, null)?.trim()?.trimEnd('/')
        // 迁移：早期默认值（局域网明文）被当成「用户自定义」存过盘。
        // 新默认已换成公网 HTTPS，这里把它当作「没自定义」清掉，否则老装机永远连局域网。
        if (saved != null && saved.equals(CloudConfig.MICROFEED_LEGACY_URL, ignoreCase = true)) {
            prefs.edit().remove(KEY_URL).apply()
            overrideUrl = null
        } else {
            overrideUrl = saved?.takeIf { it.isNotBlank() }
        }
    }

    fun baseUrl(context: Context): String {
        val saved = overrideUrl ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URL, null)
        return (saved ?: CloudConfig.MICROFEED_URL).trim().trimEnd('/')
    }

    /** 用户自定义服务地址（留空表示回到编译期默认值） */
    fun setBaseUrl(context: Context, url: String?) {
        overrideUrl = url?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .apply {
                if (overrideUrl == null) remove(KEY_URL) else putString(KEY_URL, overrideUrl)
            }
            .apply()
    }

    /** 能发请求的前提：云端开关打开且已登录（微足迹服务靠主后端 token 鉴权） */
    fun ready(): Boolean = CloudConfig.ENABLED && BackendApi.token != null

    // ---------------- 基础请求 ----------------

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun open(
        context: Context,
        method: String,
        path: String,
        contentType: String? = null,
        auth: Boolean = true,
        readTimeoutMs: Int = 12_000
    ): HttpURLConnection {
        val conn = (URL(baseUrl(context) + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = readTimeoutMs
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "identity")
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            if (auth) BackendApi.token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        return conn
    }

    private fun readResponse(conn: HttpURLConnection, expected: IntRange = 200..299): String? {
        return try {
            val code = conn.responseCode
            lastStatus = code
            val stream = if (code in expected) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in expected) {
                lastError = if (code == 401) "HTTP 401（服务端验签失败）" else "HTTP $code"
                null
            } else text
        } catch (t: Throwable) {
            lastStatus = 0
            lastError = t.message ?: t.javaClass.simpleName
            null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 连通性体检结果。
     *
     * 为什么需要它：网络不通、证书不被信任、以及「JWT 验签失败（两边 SECRET_KEY 不一致）」
     * 在界面上表现完全一样，都是「未连接」；但处理方式完全不同 —— 前者查网络，
     * 后者要把主后端的密钥同步到微足迹服务。分开报出来，才不用猜。
     */
    data class Diagnosis(
        val url: String,
        val reachable: Boolean,
        val healthOk: Boolean,
        val authorized: Boolean,
        val status: Int,
        val detail: String
    )

    /** 一键体检：先探 /healthz（不鉴权），再带当前 token 探 /v1/me */
    suspend fun diagnose(context: Context): Diagnosis = withContext(Dispatchers.IO) {
        val url = baseUrl(context)
        val health = runCatching {
            val conn = (URL("$url/healthz").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            runCatching { conn.errorStream?.close() }
            runCatching { if (code in 200..299) conn.inputStream.close() }
            conn.disconnect()
            code
        }
        if (health.isFailure) {
            return@withContext Diagnosis(
                url, false, false, false, 0,
                health.exceptionOrNull()?.message?.take(80) ?: "网络不可达"
            )
        }
        val healthCode = health.getOrDefault(0)
        if (BackendApi.token == null) {
            return@withContext Diagnosis(url, true, healthCode in 200..299, false, healthCode, "未登录")
        }
        val code = runCatching {
            val conn = open(context, "GET", "/v1/me", auth = true, readTimeoutMs = 8_000)
            val c = conn.responseCode
            runCatching { conn.errorStream?.close() }
            runCatching { if (c in 200..299) conn.inputStream.close() }
            conn.disconnect()
            c
        }.getOrDefault(0)
        Diagnosis(
            url = url,
            reachable = true,
            healthOk = healthCode in 200..299,
            authorized = code in 200..299,
            status = code,
            detail = when {
                code in 200..299 -> "ok"
                code == 401 -> "unauthorized"
                code == 0 -> "timeout"
                else -> "http $code"
            }
        )
    }

    private fun request(
        context: Context,
        method: String,
        path: String,
        body: JSONObject? = null
    ): JSONObject? {
        if (!ready()) return null
        return runCatching {
            val conn = open(context, method, path, if (body != null) "application/json; charset=utf-8" else null)
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            readResponse(conn)?.let { JSONObject(it) }
        }.getOrNull()
    }

    // ---------------- 资料 ----------------

    fun me(context: Context): JSONObject? = request(context, "GET", "/v1/me")

    fun updateMe(context: Context, nickname: String, avatarUrl: String?, bio: String): Boolean {
        val body = JSONObject()
            .put("nickname", nickname)
            .put("bio", bio)
            .put("avatarUrl", avatarUrl ?: "")
        return request(context, "PUT", "/v1/me", body) != null
    }

    fun stats(context: Context): JSONObject? = request(context, "GET", "/v1/me/stats")

    // ---------------- 信息流 ----------------

    data class FeedPage(val items: List<MicroPost>, val nextCursor: Int?, val hasMore: Boolean)

    fun feed(
        context: Context,
        tab: String,
        city: String? = null,
        tag: String? = null,
        q: String? = null,
        cursor: Int = 0,
        limit: Int = 8
    ): FeedPage? {
        if (!ready()) return null
        val sb = StringBuilder("/v1/feed?tab=").append(encode(tab))
            .append("&limit=").append(limit)
            .append("&cursor=").append(cursor)
        if (!city.isNullOrBlank()) sb.append("&city=").append(encode(city))
        if (!tag.isNullOrBlank()) sb.append("&tag=").append(encode(tag))
        if (!q.isNullOrBlank()) sb.append("&q=").append(encode(q))
        val root = request(context, "GET", sb.toString()) ?: return null
        val arr = root.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i ->
            runCatching { parsePost(arr.getJSONObject(i)) }.getOrNull()
        }
        val next = if (root.isNull("nextCursor")) null else root.optInt("nextCursor", -1).takeIf { it >= 0 }
        return FeedPage(items, next, root.optBoolean("hasMore", false))
    }

    fun post(context: Context, id: String): MicroPost? =
        request(context, "GET", "/v1/posts/${encode(id)}")?.let { runCatching { parsePost(it) }.getOrNull() }

    // ---------------- 发布 / 撤回 ----------------

    /** 单张/单个媒体上传：返回服务端相对地址（如 /media/1001/202611/xxx.jpg） */
    fun uploadMedia(context: Context, file: File): String? {
        if (!ready() || !file.isFile) return null
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            else -> "image/jpeg"
        }
        return runCatching {
            val conn = open(
                context, "POST", "/v1/media",
                contentType = mime,
                readTimeoutMs = 180_000
            )
            conn.setFixedLengthStreamingMode(file.length())
            conn.doOutput = true
            file.inputStream().use { input -> conn.outputStream.use { output -> input.copyTo(output, 256 * 1024) } }
            readResponse(conn, 200..201)?.let { JSONObject(it).optString("url").takeIf { u -> u.isNotBlank() } }
        }.getOrNull()
    }

    /**
     * 发布：先把媒体的本地文件上传成服务端 URL，再一次性提交帖子。
     * 任何一步失败都返回 null，由 AppState 保留本地公开发布（离线也能看到自己的内容）。
     */
    fun publish(
        context: Context,
        caption: String,
        place: String,
        city: String,
        tags: List<String>,
        imageFiles: List<File>,
        videoFile: File?,
        bgmFile: File?,
        bgmTitle: String?,
        bgmArtist: String?,
        lat: Double?,
        lon: Double?
    ): MicroPost? {
        if (!ready()) return null
        val media = JSONArray()
        var mediaFailed = false
        imageFiles.forEach { f ->
            val url = uploadMedia(context, f)
            if (url == null) mediaFailed = true
            else media.put(JSONObject().put("type", "image").put("url", url))
        }
        videoFile?.let { f ->
            val url = uploadMedia(context, f)
            if (url == null) mediaFailed = true
            else media.put(JSONObject().put("type", "video").put("url", url))
        }
        // 任何一张图/一段视频没传上去，就不要发一条“看起来少了几张”的残缺帖；
        // 本地发布仍保留，服务端这次失败由调用方静默处理。
        if (mediaFailed) return null
        // 配乐是可选媒体：传失败只丢配乐，不影响照片/视频主体
        val bgmUrl = bgmFile?.let { uploadMedia(context, it) }

        val body = JSONObject()
            .put("caption", caption)
            .put("place", place)
            .put("city", city)
            .put("tags", JSONArray(tags))
            .put("media", media)
            .put("bgmTitle", bgmTitle ?: "")
            .put("bgmArtist", bgmArtist ?: "")
            .put("bgmUrl", bgmUrl ?: "")
        if (lat != null) body.put("lat", lat)
        if (lon != null) body.put("lon", lon)
        return request(context, "POST", "/v1/posts", body)?.let { runCatching { parsePost(it) }.getOrNull() }
    }

    fun deletePost(context: Context, id: String): Boolean =
        request(context, "DELETE", "/v1/posts/${encode(id)}") != null

    // ---------------- 互动 ----------------

    data class ToggleResult(val active: Boolean, val count: Int)

    fun like(context: Context, id: String): ToggleResult? =
        request(context, "POST", "/v1/posts/${encode(id)}/like")?.let {
            ToggleResult(it.optBoolean("liked"), it.optInt("likes"))
        }

    fun favorite(context: Context, id: String): ToggleResult? =
        request(context, "POST", "/v1/posts/${encode(id)}/favorite")?.let {
            ToggleResult(it.optBoolean("favored"), it.optInt("favorites"))
        }

    fun share(context: Context, id: String): Boolean =
        request(context, "POST", "/v1/posts/${encode(id)}/share") != null

    fun hide(context: Context, id: String): Boolean =
        request(context, "POST", "/v1/posts/${encode(id)}/hide") != null

    fun view(context: Context, id: String) {
        request(context, "POST", "/v1/posts/${encode(id)}/view")
    }

    // ---------------- 评论 ----------------

    fun comments(context: Context, postId: String, limit: Int = 50): List<MicroComment>? {
        if (!ready()) return null
        val root = request(context, "GET", "/v1/posts/${encode(postId)}/comments?limit=$limit") ?: return null
        val arr = root.optJSONArray("items") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                MicroComment(
                    id = o.optString("id"),
                    authorName = o.optString("authorName"),
                    text = o.optString("text"),
                    time = o.optString("time"),
                    authorId = o.optInt("authorId"),
                    replyTo = o.optString("replyTo").takeIf { it.isNotBlank() },
                    likes = o.optInt("likes")
                )
            }.getOrNull()
        }
    }

    fun addComment(context: Context, postId: String, text: String, replyTo: String? = null): MicroComment? {
        val body = JSONObject().put("text", text).put("replyTo", replyTo ?: "")
        val root = request(context, "POST", "/v1/posts/${encode(postId)}/comments", body) ?: return null
        val o = root.optJSONObject("comment") ?: return null
        return MicroComment(
            id = o.optString("id"),
            authorName = o.optString("authorName"),
            text = o.optString("text"),
            time = o.optString("time"),
            authorId = o.optInt("authorId"),
            replyTo = o.optString("replyTo").takeIf { it.isNotBlank() },
            likes = o.optInt("likes")
        )
    }

    fun deleteComment(context: Context, commentId: String): Boolean =
        request(context, "DELETE", "/v1/comments/${encode(commentId)}") != null

    fun likeComment(context: Context, commentId: String): ToggleResult? =
        request(context, "POST", "/v1/comments/${encode(commentId)}/like")?.let {
            ToggleResult(it.optBoolean("liked"), it.optInt("likes"))
        }

    // ---------------- 关注 / 作者 ----------------

    fun follow(context: Context, uid: Int): ToggleResult? =
        request(context, "POST", "/v1/users/$uid/follow")?.let {
            ToggleResult(it.optBoolean("followingHim"), it.optInt("followers"))
        }

    fun user(context: Context, uid: Int): JSONObject? = request(context, "GET", "/v1/users/$uid")

    fun userPosts(context: Context, uid: Int, cursor: Int = 0, limit: Int = 12): FeedPage? {
        if (!ready()) return null
        val root = request(context, "GET", "/v1/users/$uid/posts?cursor=$cursor&limit=$limit") ?: return null
        val arr = root.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i ->
            runCatching { parsePost(arr.getJSONObject(i)) }.getOrNull()
        }
        val next = if (root.isNull("nextCursor")) null else root.optInt("nextCursor", -1).takeIf { it >= 0 }
        return FeedPage(items, next, root.optBoolean("hasMore", false))
    }

    // ---------------- 搜索 / 话题 ----------------

    data class SearchResult(val posts: List<MicroPost>, val tags: List<String>, val users: List<String>)

    fun search(context: Context, q: String): SearchResult? {
        if (!ready() || q.isBlank()) return null
        val root = request(context, "GET", "/v1/search?q=${encode(q)}") ?: return null
        val posts = root.optJSONArray("posts")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> runCatching { parsePost(arr.getJSONObject(i)) }.getOrNull() }
        }.orEmpty()
        val tags = root.optJSONArray("tags")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.optString("tag") }
        }.orEmpty()
        val users = root.optJSONArray("users")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.optString("name") }
        }.orEmpty()
        return SearchResult(posts, tags, users)
    }

    fun trendingTags(context: Context): List<String> {
        val root = request(context, "GET", "/v1/tags/trending") ?: return emptyList()
        val arr = root.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.optString("tag") }
    }

    // ---------------- JSON → 模型 ----------------

    fun parsePost(o: JSONObject): MicroPost {
        val authorObj = o.optJSONObject("author") ?: JSONObject()
        val name = authorObj.optString("name").ifBlank { "足迹用户" }
        // org.json 的 optString 对 JSON null 会返回字符串 "null"。服务端口径是 null，
        // 若直接把 "null" 交给路径判断，会被当成真实文件/远程地址，导致：
        // 1) 图片帖误走视频分支，显示「视频文件已不在本机」；
        // 2) bgmPath="null" 被当成不存在的本地文件，配乐永远不播放。
        fun nullableString(key: String): String? =
            o.optString(key).trim().takeIf {
                it.isNotBlank() && !it.equals("null", ignoreCase = true) && !it.equals("undefined", ignoreCase = true)
            }
        val liked = o.optBoolean("liked")
        val favored = o.optBoolean("favored")
        // 服务端的 baseLikes / favorites 都是「含我」的总数；App 的 likeCount 会在本地再 +1，
        // 这里先减掉自己那一票，避免显示值比服务端多 1。
        val baseLikes = (o.optInt("baseLikes") - if (liked) 1 else 0).coerceAtLeast(0)
        val favorites = (o.optInt("favorites") - if (favored) 1 else 0).coerceAtLeast(0)
        return MicroPost(
            id = o.optString("id"),
            author = MicroAuthor(
                id = authorObj.optInt("id"),
                name = name,
                avatarText = authorObj.optString("avatarText").ifBlank { name.take(1) },
                avatarPath = authorObj.optString("avatarPath").trim().takeIf {
                    it.isNotBlank() && !it.equals("null", ignoreCase = true)
                },
                bio = authorObj.optString("bio"),
                followers = authorObj.optInt("followers"),
                verified = authorObj.optBoolean("verified")
            ),
            place = o.optString("place"),
            city = o.optString("city"),
            caption = o.optString("caption"),
            tags = o.optJSONArray("tags")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> runCatching { arr.getString(i) }.getOrNull() }
            }.orEmpty(),
            imagePaths = o.optJSONArray("imagePaths")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { arr.getString(i) }.getOrNull()?.trim()?.takeIf {
                        it.isNotBlank() && !it.equals("null", ignoreCase = true)
                    }
                }
            }.orEmpty(),
            videoPath = nullableString("videoPath"),
            bgmTitle = nullableString("bgmTitle"),
            bgmPath = nullableString("bgmPath"),
            bgmArtist = nullableString("bgmArtist"),
            baseLikes = baseLikes,
            baseComments = o.optInt("baseComments"),
            baseShares = o.optInt("baseShares"),
            favorites = favorites,
            liked = liked,
            favored = favored,
            followed = o.optBoolean("followed"),
            isMine = o.optBoolean("isMine"),
            publishedAt = o.optLong("publishedAt")
        )
    }

    /** 把服务端相对地址（/media/...）补成可直接给 Coil / MediaPlayer 的完整 URL */
    fun absolute(context: Context, pathOrUrl: String): String {
        if (pathOrUrl.isBlank()) return pathOrUrl
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) return pathOrUrl
        return baseUrl(context) + pathOrUrl
    }

    /** 把远端媒体下载到 cacheDir，供 BgmPlayback 这种只吃本地路径的播放器使用 */
    suspend fun cacheMedia(context: Context, pathOrUrl: String, nameHint: String = "bgm"): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = absolute(context, pathOrUrl)
                val ext = url.substringBefore('?').substringAfterLast('.', "bin").take(5)
                val target = File(context.cacheDir, "microfeed/$nameHint-${url.hashCode()}.$ext")
                if (target.isFile && target.length() > 0) return@runCatching target.absolutePath
                target.parentFile?.mkdirs()
                val part = File(target.path + ".part")
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 60_000
                    setRequestProperty("Accept", "*/*")
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    runCatching { conn.errorStream?.close() }
                    conn.disconnect()
                    return@runCatching null
                }
                conn.inputStream.use { input ->
                    part.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
                }
                conn.disconnect()
                if (part.length() > 0) {
                    if (target.exists()) target.delete()
                    if (part.renameTo(target)) target.absolutePath else part.absolutePath
                } else {
                    part.delete()
                    null
                }
            }.getOrNull()
        }
}
