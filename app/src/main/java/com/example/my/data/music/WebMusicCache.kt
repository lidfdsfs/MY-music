package com.example.my.data.music

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.example.my.data.WebSafety
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** 一首网页歌曲的歌名与歌手 */
data class SongMeta(val title: String, val artist: String)

/**
 * 网页歌曲缓存：资源探里点「下载并播放」后，音频落到 App 私有目录 `files/web_music/`，
 * 用清洗过的「歌名 - 歌手」命名，之后再从本地文件播放（拖动、锁屏、通知都能用）。
 *
 * 只接受 http/https（过 [WebSafety] 闸门），只收音频（Content-Type 或扩展名判断），
 * 单文件上限 120MB，超限或返回网页就放弃，绝不把执行类文件落盘。
 */
object WebMusicCache {

    const val DIR_NAME = "web_music"
    private const val MAX_FILE_BYTES = 120L * 1024 * 1024
    private val ILLEGAL = Regex("""[\\/:*?"<>|\r\n\t]""")
    private val AUDIO_EXT = listOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "amr", "ape")
    private val FILE_EXT = Regex(
        """\.(mp3|m4a|aac|flac|wav|ogg|oga|opus|wma|amr|ape|mp4)$""",
        RegexOption.IGNORE_CASE
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main: Handler by lazy { Handler(Looper.getMainLooper()) }

    /** 站点后缀 / 尾巴，别混进歌名 */
    private val SITE_WORDS = listOf(
        "歌曲海", "gequhai", "gequhai.com", "歌曲海 - gequhai.com",
        "免费下载", "在线试听", "mp3下载"
    )

    fun dir(context: Context): File = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    fun sizeBytes(context: Context): Long =
        dir(context).listFiles()?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXT }
            ?.sumOf { it.length() } ?: 0L

    fun count(context: Context): Int =
        dir(context).listFiles()?.count { it.isFile && it.extension.lowercase() in AUDIO_EXT } ?: 0

    /** 设置页展示用：`3 首 · 42.1 MB` */
    fun summary(context: Context): String {
        val n = count(context)
        val mb = sizeBytes(context) / 1024.0 / 1024.0
        return if (n <= 0) "0 首" else String.format(Locale.US, "%d 首 · %.1f MB", n, mb)
    }

    /** 已缓存的歌曲（按缓存时间从早到晚，和播放队列顺序一致） */
    fun list(context: Context): List<LocalSong> = try {
        dir(context).listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXT }
            ?.sortedBy { it.lastModified() }
            ?.map { toSong(it) }
            .orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    /** 清空缓存目录，返回删除的歌曲数 */
    fun clear(context: Context): Int {
        val files = dir(context).listFiles() ?: return 0
        var n = 0
        files.forEach { f ->
            if (f.extension.lowercase() in AUDIO_EXT) n++
            runCatching { f.deleteRecursively() }
        }
        return n
    }

    fun toSong(file: File): LocalSong {
        val stem = file.nameWithoutExtension
        val meta = resolveMeta(null, stem)
        // 负 id，和系统媒体库的正 id 不会撞
        val id = -(file.absolutePath.hashCode().toLong() and 0x7FFFFFFFL) - 1L
        return LocalSong(
            id = id,
            title = meta.title.ifBlank { stem },
            artist = meta.artist,
            album = "网页缓存",
            durationMs = 0L,
            uri = Uri.fromFile(file),
            path = file.absolutePath,
            rawTitle = stem
        )
    }

    /**
     * 从页面标题 / 探到的文件名里还原「歌名 + 歌手」。
     * 页面标题优先（曲海页标题通常是「歌名 - 歌手 - 站点」），文件名兜底。
     */
    fun resolveMeta(pageTitle: String?, rawName: String): SongMeta {
        val fromTitle = parseLabel(pageTitle)
        if (fromTitle != null) return fromTitle
        val parsed = SongNameParser.parse(rawName)
        if (!parsed.artist.isNullOrBlank()) {
            return SongMeta(parsed.title.ifBlank { rawName }, parsed.artist.orEmpty())
        }
        // 没有权威歌手时，曲库下载的文件名默认是「歌名 - 歌手」；
        // 这里不能再走会默认「歌手 - 歌名」的解析结果，否则歌名和歌手会整体颠倒。
        val split = splitByDash(rawName.trim().replace(FILE_EXT, "").trim())
        if (split != null) return split
        return SongMeta(parsed.title.ifBlank { SongNameParser.clean(rawName) }, "")
    }

    private fun parseLabel(pageTitle: String?): SongMeta? {
        if (pageTitle.isNullOrBlank()) return null
        val cleaned = pageTitle.replace(Regex("""[\r\n\t]+"""), " ").trim()
        // 按 - / | / _ 切段，去掉站点名与下载字样，剩下的前两段当「歌名、歌手」
        val segments = cleaned.split(" - ", " | ", "｜", "_")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { seg -> SITE_WORDS.none { w -> seg.contains(w, ignoreCase = true) } }
        if (segments.isEmpty()) return null
        val title = segments.first().trim()
        val artist = segments.getOrNull(1)?.trim().orEmpty()
        if (title.isBlank() || title.length > 60) return null
        return SongMeta(title, artist)
    }

    private fun splitByDash(raw: String): SongMeta? {
        val cleaned = raw.trim()
        if (cleaned.isBlank()) return null
        for (sep in listOf(" - ", "-", "–", "—", "－")) {
            if (!cleaned.contains(sep)) continue
            val parts = cleaned.split(sep).map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size == 2 && parts[0].length in 1..30 && parts[1].length in 1..24) {
                return SongMeta(parts[0], parts[1])
            }
        }
        return null
    }

    /** 音频直链常在第三方 CDN 上，只有和来源页同域才允许携带凭据 */
    private fun sameHost(a: String, b: String): Boolean = runCatching {
        val ha = URL(a).host?.lowercase()
        val hb = URL(b).host?.lowercase()
        !ha.isNullOrBlank() && ha == hb
    }.getOrDefault(false)

    private fun safeName(s: String): String {
        val cleaned = s.replace(ILLEGAL, " ").replace(Regex("""\s+"""), " ").trim().trim('.')
        return cleaned.take(60).ifBlank { "网页音频" }
    }

    private fun extensionOf(url: String, mime: String?): String {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        val ext = path.substringAfterLast('.', "")
        if (ext in AUDIO_EXT) return ext
        return when (mime?.substringBefore(';')?.trim()?.lowercase()) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/mp4", "audio/x-m4a", "audio/m4a" -> "m4a"
            "audio/aac", "audio/x-aac" -> "aac"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/ogg", "audio/opus" -> "ogg"
            "audio/wav", "audio/x-wav" -> "wav"
            else -> "mp3"
        }
    }

    /**
     * 给缓存下来的音频写一份同名 .lrc。LyricsRepository 会优先读音频同目录的同名
     * 歌词文件，于是「搜到歌」的同时歌词也离线可用，不用再走四源联网匹配。
     */
    fun saveLyrics(context: Context, song: LocalSong, lrc: String) {
        if (lrc.isBlank()) return
        runCatching {
            val base = File(song.path).nameWithoutExtension
            File(dir(context), "$base.lrc").writeText(lrc, Charsets.UTF_8)
        }
    }

    /**
     * 下载一条网页音频到缓存文件夹，成功后回主线程回调歌曲；失败回错误文案。
     * 回调在主线程，UI 可直接用。
     */
    fun download(
        context: Context,
        url: String,
        pageTitle: String?,
        probeName: String,
        referer: String,
        cookie: String,
        userAgent: String,
        onDone: (LocalSong?, String?) -> Unit
    ) = downloadNamed(
        context, url, resolveMeta(pageTitle, probeName),
        referer, cookie, userAgent, onDone
    )

    /** 歌名/歌手已经确定时直接指定，避免标题里自带的「 - 」被二次拆错 */
    fun downloadNamed(
        context: Context,
        url: String,
        meta: SongMeta,
        referer: String,
        cookie: String,
        userAgent: String,
        onDone: (LocalSong?, String?) -> Unit
    ) {
        val app = context.applicationContext
        scope.launch {
            var song: LocalSong? = null
            var error: String? = null
            var part: File? = null
            try {
                val safe = WebSafety.sanitize(url) ?: throw IOException("不安全的链接")
                // 同源才带 Referer / Cookie：实测酷我 car-er 节点只要看到第三方 Referer
                // 就直接 403，去掉后 200；跨域也不该把来源页和站点 Cookie 送给 CDN。
                val sameHost = sameHost(safe, referer)
                val conn = (URL(safe).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000
                    readTimeout = 20_000
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "audio/*,*/*;q=0.8")
                    if (sameHost && referer.isNotBlank()) setRequestProperty("Referer", referer)
                    if (sameHost && cookie.isNotBlank()) setRequestProperty("Cookie", cookie)
                    if (userAgent.isNotBlank()) setRequestProperty("User-Agent", userAgent)
                    connect()
                }
                try {
                    if (conn.responseCode !in 200..299) throw IOException("服务器返回 ${conn.responseCode}")
                    val mime = conn.contentType?.substringBefore(';')?.trim()
                    if (mime?.startsWith("text/") == true) throw IOException("这个链接是网页，不是音频")
                    val total = conn.contentLengthLong
                    if (total > MAX_FILE_BYTES) throw IOException("文件太大，已跳过")
                    val ext = extensionOf(safe, mime)
                    val fileName = safeName(buildString {
                        append(meta.title)
                        if (meta.artist.isNotBlank()) append(" - ").append(meta.artist)
                    }) + "." + ext
                    val dest = File(dir(app), fileName)
                    part = File(dir(app), "$fileName.part")
                    conn.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var written = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                written += n
                                if (written > MAX_FILE_BYTES) throw IOException("文件太大，已跳过")
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    if (dest.exists()) dest.delete()
                    if (!part.renameTo(dest)) {
                        part.copyTo(dest, overwrite = true)
                        part.delete()
                    }
                    part = null
                    song = toSong(dest)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                error = e.message ?: "下载失败"
            } finally {
                runCatching { part?.delete() }
            }
            val resultSong = song
            val resultError = error
            main.post { onDone(resultSong, resultError) }
        }
    }

    /**
     * 批量导入用的挂起版本：把回调包装成 Result，单首失败只返回 failure，不取消整批。
     */
    suspend fun downloadAwait(
        context: Context,
        url: String,
        meta: SongMeta,
        referer: String,
        cookie: String,
        userAgent: String
    ): Result<LocalSong> = suspendCancellableCoroutine { cont ->
        downloadNamed(context, url, meta, referer, cookie, userAgent) { song, error ->
            if (!cont.isActive) return@downloadNamed
            if (song != null) cont.resume(Result.success(song))
            else cont.resume(Result.failure(IOException(error ?: "下载失败")))
        }
    }
}
