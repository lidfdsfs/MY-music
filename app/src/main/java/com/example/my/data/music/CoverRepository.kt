package com.example.my.data.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import com.example.my.data.WebIncognito
import com.example.my.data.WebSafety
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * 封面仓库：内嵌封面 → 磁盘缓存 → QQ 音乐 albummid → 曲海 mp3_cover → Bing 图片。
 *
 * 设计约束：
 * - 所有网络地址过 [WebSafety]；只收 image 类型（或明确的图片扩展名），单张上限 8MB；
 * - 解码失败 / Content-Type 是网页 / 下载到一半失败，一律不落盘；
 * - key 用「歌名|歌手」，同一首歌在歌词页、黑胶页、详情页只查一次；
 * - 同一 key 的并发请求合并成一个 Deferred，避免三页同时打三次接口。
 */
object CoverRepository {

    private const val MAX_IMAGE_BYTES = 8L * 1024 * 1024

    /** MusicBrainz 要求可识别的 UA，并限制 1 次/秒；只做最后兜底 */
    private const val MB_UA = "MYMusic/1.0 ( mailto:noreply@example.com )"
    private val mbMutex = Mutex()
    private var mbLastAt = 0L
    private const val DISK_DIR = "covers"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memory = object : LruCache<String, Bitmap>(48 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val pending = ConcurrentHashMap<String, Deferred<Bitmap?>>()

    /**
     * 系统内存紧张或退到后台时释放封面位图缓存。
     * 只清内存，磁盘封面文件保留；再进页面会按需重新解码。
     */
    fun trimMemory(level: Int) {
        // 10 = ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW（该常量在新系统里已标记 deprecated）
        if (level >= 10) {
            memory.evictAll()
        }
    }

    fun cacheKey(title: String, artist: String, fallbackId: Long = 0L): String {
        val t = title.trim()
        val a = artist.trim()
        return if (t.isBlank() && a.isBlank()) "id:$fallbackId" else "$t|$a".lowercase()
    }

    /** 页面入口：有内嵌封面先用，没有再去联网找；任何一步失败都返回 null，不抛异常 */
    suspend fun load(context: Context, song: LocalSong): Bitmap? {
        val key = cacheKey(song.title, song.artist, song.id)
        memory.get(key)?.let { return it }
        pending[key]?.let { return it.await() }
        val job = scope.async { loadUncached(context.applicationContext, song, key) }
        pending[key] = job
        return try {
            job.await()
        } finally {
            pending.remove(key, job)
        }
    }

    /**
     * 同步只读缓存：给通知栏 / 桌面小组件使用。
     * 这两个入口在主线程刷新，不能在这里等网络；命中磁盘缓存就直接返回，
     * 没命中由调用方决定是否后台联网。
     */
    fun cached(context: Context, song: LocalSong): Bitmap? {
        val key = cacheKey(song.title, song.artist, song.id)
        memory.get(key)?.let { return it }
        val bmp = decodeFile(diskFile(context, key)) ?: return null
        memory.put(key, bmp)
        return bmp
    }

    /** 网页歌曲下载后调用；已有封面就不重复下载 */
    suspend fun saveRemote(context: Context, title: String, artist: String, url: String?) {        if (url.isNullOrBlank()) return
        val key = cacheKey(title, artist)
        if (memory.get(key) != null) return
        if (diskFile(context, key).isFile) return
        withContext(Dispatchers.IO) {
            val bmp = fetchImage(WebSafety.sanitize(url), "https://www.gequhai.com/") ?: return@withContext
            store(context, key, bmp)
        }
    }

    private suspend fun loadUncached(context: Context, song: LocalSong, key: String): Bitmap? {
        memory.get(key)?.let { return it }
        decodeFile(diskFile(context, key))?.let {
            memory.put(key, it)
            return it
        }
        embedded(context, song)?.let {
            store(context, key, it)
            return it
        }

        val (title, artist) = song.title.trim() to song.artist.trim()
        if (title.isBlank()) return null

        qqSearchCover(title, artist)?.let {
            store(context, key, it)
            return it
        }
        quhaiCover(title, artist)?.let {
            store(context, key, it)
            return it
        }
        bingCover(title, artist)?.let {
            store(context, key, it)
            return it
        }
        // 最后兜底：MusicBrainz 元数据 → Cover Art Archive 封面
        musicBrainzCover(title, artist)?.let {
            store(context, key, it)
            return it
        }
        return null
    }

    /** 有的 content:// 音频只能通过 fd 打开，所以必须带 Context */
    private fun embedded(context: Context, song: LocalSong): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if ("file".equals(song.uri.scheme, ignoreCase = true) && song.path.isNotBlank()) {
                retriever.setDataSource(song.path)
            } else {
                retriever.setDataSource(context, song.uri)
            }
            val bytes = retriever.embeddedPicture ?: return null
            decodeSquare(bytes)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun qqSearchCover(title: String, artist: String): Bitmap? {
        val q = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=8&w=" +
            URLEncoder.encode(q, "UTF-8") + "&format=json&cr=1&g_tk=5381&t=0" +
            "&inCharset=utf-8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
        val body = httpGet(url, "https://y.qq.com/") ?: return null
        val txt = body.trim().let {
            if (it.startsWith("callback(")) it.removePrefix("callback(").removeSuffix(")") else it
        }
        val list = runCatching {
            JSONObject(txt).optJSONObject("data")
                ?.optJSONObject("song")?.optJSONArray("list")
        }.getOrNull() ?: return null
        var bestScore = 0
        var bestMid = ""
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val name = o.optString("songname")
            val singer = (0 until (o.optJSONArray("singer")?.length() ?: 0))
                .mapNotNull { o.optJSONArray("singer")?.optJSONObject(it)?.optString("name") }
                .filter { it.isNotBlank() }.joinToString(" / ")
            val mid = o.optString("albummid").ifBlank {
                o.optJSONObject("album")?.optString("mid").orEmpty()
            }
            val score = CoverUrls.matchScore(title, artist, name, singer)
            if (score >= 40 && score > bestScore) {
                bestScore = score
                bestMid = mid
            }
        }
        val coverUrl = CoverUrls.qqCover(bestMid) ?: return null
        return fetchImage(coverUrl, "https://y.qq.com/")
    }

    private suspend fun quhaiCover(title: String, artist: String): Bitmap? {
        val raw = SongSearch.fetchCoverUrl(title, artist) ?: return null
        val safe = WebSafety.sanitize(raw) ?: return null
        return fetchImage(safe, "https://www.gequhai.com/")
    }

    private fun bingCover(title: String, artist: String): Bitmap? {
        val html = httpGet(CoverUrls.bingSearchUrl(title, artist), "https://www.bing.com/")
            ?: return null
        val candidates = CoverUrls.extractBingImageUrls(html)
            .filter { CoverUrls.isLikelyImage(it) }
            .take(6)
        for (url in candidates) {
            val safe = WebSafety.sanitize(url) ?: continue
            fetchImage(safe, "https://www.bing.com/")?.let { return it }
        }
        return null
    }

    /**
     * MusicBrainz + Cover Art Archive 兜底：按「歌名 + 歌手」查 recording，
     * 取第一个 release 的 CAA 封面。接口限速 1 次/秒，因此加了互斥与最小间隔。
     */
    private suspend fun musicBrainzCover(title: String, artist: String): Bitmap? = mbMutex.withLock {
        val wait = 1_100L - (System.currentTimeMillis() - mbLastAt)
        if (wait > 0) delay(wait)
        mbLastAt = System.currentTimeMillis()
        val safeTitle = title.replace("\"", "").trim()
        val safeArtist = artist.replace("\"", "").trim()
        if (safeTitle.isBlank()) return@withLock null
        val query = buildString {
            append("recording:\"").append(safeTitle).append("\"")
            if (safeArtist.isNotBlank()) append(" AND artist:\"").append(safeArtist).append("\"")
        }
        val body = httpGetMb(
            "https://musicbrainz.org/ws/2/recording?query=${URLEncoder.encode(query, "UTF-8")}&fmt=json&limit=1"
        ) ?: return@withLock null
        val releaseId = runCatching {
            JSONObject(body).optJSONArray("recordings")
                ?.optJSONObject(0)?.optJSONArray("releases")
                ?.optJSONObject(0)?.optString("id").orEmpty()
        }.getOrNull().orEmpty()
        if (releaseId.isBlank()) return@withLock null
        fetchImage("https://coverartarchive.org/release/$releaseId/front-500", "https://musicbrainz.org/")
    }

    private fun httpGetMb(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", MB_UA)
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun fetchImage(rawUrl: String?, referer: String): Bitmap? {
        val safe = WebSafety.sanitize(rawUrl) ?: return null
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(safe).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6_000
                readTimeout = 9_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Referer", referer)
                setRequestProperty("Accept", "image/avif,image/webp,image/jpeg,image/png")
                WebIncognito.applyTo(this)
            }
            if (conn.responseCode !in 200..299) return null
            val mime = conn.contentType?.substringBefore(';')?.trim()?.lowercase()
            if (mime != null && mime.startsWith("text/")) return null
            if (mime != null && !mime.startsWith("image/") &&
                !CoverUrls.isLikelyImage(safe)
            ) return null
            val total = conn.contentLengthLong
            if (total > MAX_IMAGE_BYTES) return null
            val bytes = readAtMost(conn.inputStream, MAX_IMAGE_BYTES.toInt())
            if (bytes.size < 512) return null
            decodeSquare(bytes)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun httpGet(url: String, referer: String): String? {
        val safe = WebSafety.sanitize(url) ?: return null
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(safe).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6_000
                readTimeout = 9_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Referer", referer)
                setRequestProperty("Accept", "application/json,text/html")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
                WebIncognito.applyTo(this)
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun readAtMost(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        input.use { stream ->
            while (out.size() < max) {
                val n = stream.read(buf)
                if (n <= 0) break
                out.write(buf, 0, minOf(n, max - out.size()))
            }
        }
        return out.toByteArray()
    }

    /** 先读尺寸再按 2 的幂采样，避免 8000×8000 图直接 OOM */
    private fun decodeSquare(bytes: ByteArray): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var sample = 1
        while (opts.outWidth / sample > 1200 || opts.outHeight / sample > 1200) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        val side = minOf(decoded.width, decoded.height).coerceAtLeast(1)
        val square = if (decoded.width == decoded.height) decoded else
            Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(square, 512, 512, true)
        if (out !== square) square.recycle()
        if (out !== decoded) decoded.recycle()
        return out
    }

    private fun decodeFile(file: File): Bitmap? {
        if (!file.isFile || file.length() > MAX_IMAGE_BYTES) return null
        return decodeSquare(file.readBytes())
    }

    private fun store(context: Context, key: String, bitmap: Bitmap) {
        memory.put(key, bitmap)
        runCatching {
            val dir = File(context.filesDir, DISK_DIR).apply { mkdirs() }
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            File(dir, "${key.hashCode()}.jpg").writeBytes(out.toByteArray())
        }
    }

    private fun diskFile(context: Context, key: String): File =
        File(File(context.filesDir, DISK_DIR), "${key.hashCode()}.jpg")
}
