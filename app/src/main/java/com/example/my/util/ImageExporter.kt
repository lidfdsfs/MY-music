package com.example.my.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * 长图落盘与分享。
 *
 * Android 10 起通过 MediaStore 写入 Pictures 目录是不需要任何运行时权限的，
 * 因此这里不申请 READ/WRITE_EXTERNAL_STORAGE（只在 manifest 里给 9- 保留一个
 * maxSdkVersion=28 的声明）。分享一律走 FileProvider 的 content:// ，
 * 否则 Android 7+ 会抛 FileUriExposedException。
 */
object ImageExporter {

    private const val ALBUM = "MY足迹"

    /** 行程名可能含 / \ : * ? " < > | 等非法字符，落盘/分享前统一清洗 */
    private fun safeFileName(title: String): String =
        title.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
            .trim()
            .take(60)
            .ifBlank { "my_footprint" }

    /** 写入相册。Android 10+ 走 MediaStore；9 及以下写应用私有 Pictures 目录并提示用户 */
    fun saveToGallery(context: Context, bitmap: Bitmap, title: String): Boolean {
        val name = safeFileName(title)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, bitmap, name)
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: return false
            val file = File(File(dir, ALBUM).apply { mkdirs() }, "$name.png")
            runCatching {
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                true
            }.getOrDefault(false)
        }
    }

    private fun saveViaMediaStore(context: Context, bitmap: Bitmap, title: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$title.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = runCatching {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return false
        return try {
            resolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            } ?: throw IllegalStateException("openOutputStream null")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }

    /** 生成第三方应用可读的 content:// Uri，用于调起系统分享面板 */
    fun shareUri(context: Context, bitmap: Bitmap, title: String): Uri? {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "${safeFileName(title)}.png")
        return runCatching {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享到").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
