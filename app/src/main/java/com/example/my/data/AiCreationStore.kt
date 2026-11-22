package com.example.my.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.media.MediaScannerConnection
import android.provider.MediaStore
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** 一条 AI 创作记录：图片或视频，文件保存在 App 私有目录，不依赖临时远程 URL */
data class AiCreation(
    val id: String,
    val type: String,
    val prompt: String,
    val path: String,
    val createdAt: Long
) {
    val isVideo: Boolean get() = type == AiCreationStore.TYPE_VIDEO
}

/**
 * 图片/视频生成专区的作品仓库。
 *
 * - 生成结果立即下载到 filesDir/ai_creations，避免第三方图片/视频 URL 过期后作品变空白；
 * - 图片按原始字节落盘，不做二次 JPEG 压缩，画质不损失；
 * - 记录只增不删，用户可在专区里单条删除或一键清空。
 */
object AiCreationStore {

    const val TYPE_IMAGE = "image"
    const val TYPE_VIDEO = "video"

    private const val PREFS = "ai_creation_store"
    private const val KEY_ITEMS = "items_v1"

    private fun root(context: Context): File =
        com.example.my.data.UserScope.dir(context, "ai_creations")

    private fun dir(context: Context, type: String): File =
        File(root(context), if (type == TYPE_VIDEO) "videos" else "images").apply { mkdirs() }

    fun list(context: Context): List<AiCreation> {
        val raw = UserScope.prefs(context, PREFS)
            .getString(KEY_ITEMS, null)
            .orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val path = o.optString("path")
                    if (path.isBlank() || !File(path).exists()) continue
                    add(
                        AiCreation(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            type = o.optString("type", TYPE_IMAGE),
                            prompt = o.optString("prompt", ""),
                            path = path,
                            createdAt = o.optLong("createdAt", 0L)
                        )
                    )
                }
            }.sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, type: String, prompt: String, path: String): AiCreation {
        val item = AiCreation(
            id = UUID.randomUUID().toString(),
            type = type,
            prompt = prompt.trim(),
            path = path,
            createdAt = System.currentTimeMillis()
        )
        val items = list(context) + item
        save(context, items)
        return item
    }

    fun remove(context: Context, item: AiCreation) {
        runCatching { if (item.path.isNotBlank()) File(item.path).delete() }
        save(context, list(context).filterNot { it.id == item.id })
    }

    fun clear(context: Context) {
        list(context).forEach { runCatching { File(it.path).delete() } }
        runCatching { root(context).deleteRecursively() }
        UserScope.prefs(context, PREFS).edit()
            .remove(KEY_ITEMS).apply()
    }

    private fun save(context: Context, items: List<AiCreation>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("type", item.type)
                    .put("prompt", item.prompt)
                    .put("path", item.path)
                    .put("createdAt", item.createdAt)
            )
        }
        UserScope.prefs(context, PREFS).edit()
            .putString(KEY_ITEMS, arr.toString()).apply()
    }

    /**
     * 下载图片并原样落盘。支持普通 http(s) URL 和 data:image/base64。
     * 返回本地文件路径；失败返回 null。图片字节不做重新编码。
     */
    fun saveImage(context: Context, url: String): String? = runCatching {
        val bytes: ByteArray
        val extHint: String
        if (url.startsWith("data:image")) {
            val comma = url.indexOf(',')
            if (comma < 0) return null
            val meta = url.substring(5, comma)
            bytes = Base64.decode(url.substring(comma + 1), Base64.DEFAULT)
            extHint = when {
                meta.contains("png", true) -> "png"
                meta.contains("webp", true) -> "webp"
                else -> "jpg"
            }
        } else {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "image/*")
            }
            try {
                if (conn.responseCode !in 200..299) return null
                bytes = conn.inputStream.use { it.readBytes() }
                extHint = imageExt(conn.contentType, url, bytes)
            } finally {
                conn.disconnect()
            }
        }
        if (bytes.isEmpty()) return null
        val f = File(dir(context, TYPE_IMAGE), "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.${imageExt(null, url, bytes, extHint)}")
        FileOutputStream(f).use { it.write(bytes) }
        f.absolutePath
    }.getOrNull()

    /**
     * 下载视频到本地。视频通常较大，直接流式写文件，避免整段读进内存。
     * 返回本地文件路径；失败返回 null。
     */
    fun saveVideo(context: Context, url: String): String? = runCatching {
        if (url.startsWith("data:")) return null
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 180_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) return null
            val ext = videoExt(conn.contentType, url)
            val tmp = File(dir(context, TYPE_VIDEO), "vid_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$ext")
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output, bufferSize = 16 * 1024) }
            }
            if (tmp.length() <= 0L) {
                tmp.delete()
                return null
            }
            tmp.absolutePath
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** 复制原文件到系统相册。图片/视频都不重新编码，保持生成时画质。 */
    fun saveToGallery(context: Context, item: AiCreation): Boolean = runCatching {
        val file = File(item.path)
        if (!file.exists()) return false
        val mime = if (item.isVideo) videoMime(file.name) else imageMime(file.name)
        val name = "MY足迹_${System.currentTimeMillis()}.${file.extension.ifBlank { if (item.isVideo) "mp4" else "png" }}"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (item.isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val relative = if (item.isVideo) {
                "${Environment.DIRECTORY_MOVIES}/MY足迹"
            } else {
                "${Environment.DIRECTORY_PICTURES}/MY足迹"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: return false
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: error("openOutputStream null")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                false
            }
        } else {
            // Android 9 及以下：系统相册仍按公共 Pictures/Movies 目录收录，
            // 复制原文件后用 MediaScanner 通知相册，不做二次转码。
            @Suppress("DEPRECATION")
            val publicDir = if (item.isVideo) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            }
            val folder = File(publicDir, "MY足迹").apply { mkdirs() }
            val target = File(folder, name)
            file.inputStream().use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
            true
        }
    }.getOrDefault(false)

    fun shareUri(context: Context, item: AiCreation): Uri? = runCatching {
        val file = File(item.path)
        if (!file.exists()) return null
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }.getOrNull()

    fun mimeOf(item: AiCreation): String =
        if (item.isVideo) videoMime(item.path) else imageMime(item.path)

    private fun imageExt(contentType: String?, url: String, bytes: ByteArray, fallback: String = ""): String {
        val ct = contentType.orEmpty().lowercase()
        if (ct.contains("png")) return "png"
        if (ct.contains("webp")) return "webp"
        if (ct.contains("gif")) return "gif"
        if (ct.contains("jpeg") || ct.contains("jpg")) return "jpg"
        if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) return "png"
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "jpg"
        if (bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte()) return "webp"
        if (fallback.isNotBlank()) return fallback
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".png") -> "png"
            path.endsWith(".webp") -> "webp"
            path.endsWith(".gif") -> "gif"
            else -> "jpg"
        }
    }

    private fun videoExt(contentType: String?, url: String): String {
        val ct = contentType.orEmpty().lowercase()
        if (ct.contains("webm")) return "webm"
        if (ct.contains("quicktime")) return "mov"
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".webm") -> "webm"
            path.endsWith(".mov") -> "mov"
            else -> "mp4"
        }
    }

    private fun imageMime(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }

    private fun videoMime(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        else -> "video/mp4"
    }
}
