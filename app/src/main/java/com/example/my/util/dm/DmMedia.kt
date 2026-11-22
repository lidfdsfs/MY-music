package com.example.my.util.dm

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.my.data.BackendApi
import com.example.my.util.MediaCompactor
import com.example.my.util.PhotoStore
import com.example.my.util.VideoPoster
import com.example.my.util.media.VideoCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * 私信图片 / 视频发送前的本地预处理与上传。
 *
 * 流程：相册 Uri → 本地压缩 / 抽封面 → 复用 /api/sync/media 上传换服务端 URL →
 * 组装成私信消息 extra 里的媒体元信息。服务端不存原始大文件，只存去重后的 URL。
 */
object DmMedia {

    /** 预处理后的本地媒体；[thumbFile] 仅视频有（首帧封面 jpg） */
    data class PreparedMedia(
        val type: String,
        val file: File,
        val mime: String,
        val bytes: Long,
        val width: Int?,
        val height: Int?,
        val duration: Long?,
        val thumbFile: File? = null
    )

    /** 图片：解码（含 EXIF 方向纠正）→ 长边 1280 → JPEG 75 写私有缓存 */
    suspend fun prepareImage(context: Context, uri: Uri): PreparedMedia? =
        withContext(Dispatchers.IO) {
            val bmp = runCatching { PhotoStore.decodeUri(context, uri, 1280) }.getOrNull()
                ?: return@withContext null
            val dir = File(context.cacheDir, "dm_media").apply { mkdirs() }
            val out = File(dir, "img_${System.currentTimeMillis()}.jpg")
            runCatching {
                FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 75, it) }
            }.getOrNull()
            if (!out.exists() || out.length() <= 0L) {
                out.delete()
                null
            } else {
                PreparedMedia(
                    type = "image",
                    file = out,
                    mime = "image/jpeg",
                    bytes = out.length(),
                    width = bmp.width,
                    height = bmp.height,
                    duration = null
                )
            }
        }

    /** 视频：落地并按现有管线压缩（失败回退原片），抽首帧封面，取时长 / 尺寸 */
    suspend fun prepareVideo(context: Context, uri: Uri): PreparedMedia? =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "dm_media").apply { mkdirs() }
            val res = MediaCompactor.importVideo(
                context, uri, dir, System.currentTimeMillis().toString()
            ) ?: return@withContext null
            val file = res.file
            val probe = runCatching { VideoCompressor.probe(file) }.getOrNull()
            val duration = MediaCompactor.durationSeconds(file).toLong()
            val thumbFile = runCatching { VideoPoster.ensure(context, file.absolutePath) }
                .getOrNull()?.let { File(it) }
            PreparedMedia(
                type = "video",
                file = file,
                mime = "video/mp4",
                bytes = res.bytes,
                width = probe?.displayWidth,
                height = probe?.displayHeight,
                duration = duration.takeIf { it > 0 },
                thumbFile = thumbFile
            )
        }

    /** 上传主文件（视频再单独上传封面），返回写进消息 extra 的媒体元信息；失败 null */
    suspend fun upload(prepared: PreparedMedia): JSONObject? = withContext(Dispatchers.IO) {
        val ref = BackendApi.uploadMedia(prepared.file, prepared.mime) ?: return@withContext null
        val extra = JSONObject().apply {
            put("url", ref.url)
            put("bytes", ref.bytes)
            put("mime", ref.mime)
            prepared.width?.let { put("width", it) }
            prepared.height?.let { put("height", it) }
            prepared.duration?.let { put("duration", it) }
        }
        if (prepared.type == "video") {
            prepared.thumbFile?.let { thumb ->
                runCatching { BackendApi.uploadMedia(thumb, "image/jpeg") }
                    .getOrNull()?.let { extra.put("thumb", it.url) }
            }
        }
        extra
    }
}
