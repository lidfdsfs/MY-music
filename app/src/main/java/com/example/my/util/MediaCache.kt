package com.example.my.util

import android.content.Context
import android.util.Log
import com.example.my.data.BackendApi
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * 云端媒体的本机缓存。
 *
 * 服务端返回的是 `/uploads/...` 或 `http(s)://...`，换机后本地文件还没落盘时，
 * UI 直接拿 URL 调 `BitmapFactory` / `MediaMetadataRetriever` 会一片空白。
 * 这里把远程地址按 URL 哈希缓存到 `cacheDir/media_remote`，先到先下、后续直接读本地；
 * 与 CloudRestore 的 `restored_<sha>` 落盘互补，不依赖数据库是否已经改写成绝对路径。
 */
object MediaCache {

    private const val MAX_PARALLEL = 4
    private val gate = Semaphore(MAX_PARALLEL)
    private val downloading = ConcurrentHashMap<String, Object>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private const val FAILED_COOLDOWN_MS = 5 * 60 * 1000L

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isRemote(path: String?): Boolean =
        path != null && (path.startsWith("http://") || path.startsWith("https://") ||
            path.startsWith("/uploads/")) &&
            // 云端恢复偶尔把服务端的本机绝对路径拼成 https://host/data/user/...，
            // 这种地址永远 404；直接当成本地路径处理，避免主线程/封面反复等 12 秒。
            !path.contains("/data/user/")

    /** 本地路径原样返回；远程地址下载成功后返回缓存路径，失败返回 null */
    fun localPath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (!isRemote(path)) return path.takeIf { File(it).isFile }
        val ctx = appContext ?: return null
        val full = BackendApi.fullUrl(path) ?: path
        val dir = File(ctx.cacheDir, "media_remote").apply { mkdirs() }
        val ext = full.substringBefore('?').substringAfterLast('.', "bin")
            .lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "mov", "m4v", "3gp") }
            ?: "bin"
        val target = File(dir, "m_${Integer.toHexString(full.hashCode())}.$ext")
        if (target.isFile && target.length() > 0) return target.absolutePath
        failedAt[full]?.let { if (System.currentTimeMillis() - it < FAILED_COOLDOWN_MS) return null }

        val lock = downloading.computeIfAbsent(full) { Object() }
        synchronized(lock) {
            if (target.isFile && target.length() > 0) return target.absolutePath
            var acquired = false
            return try {
                gate.acquire()
                acquired = true
                val tmp = File(target.absolutePath + ".part")
                val conn = (URL(full).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "MY足迹/1.0")
                }
                try {
                    conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                } finally {
                    conn.disconnect()
                }
                if (tmp.length() > 0 && tmp.renameTo(target)) {
                    failedAt.remove(full)
                    target.absolutePath
                } else {
                    tmp.delete()
                    failedAt[full] = System.currentTimeMillis()
                    null
                }
            } catch (e: Exception) {
                Log.w("MediaCache", "download failed $full", e)
                failedAt[full] = System.currentTimeMillis()
                null
            } finally {
                if (acquired) gate.release()
                downloading.remove(full)
            }
        }
    }

    fun localFile(path: String?): File? = localPath(path)?.let(::File)?.takeIf { it.isFile }
}
