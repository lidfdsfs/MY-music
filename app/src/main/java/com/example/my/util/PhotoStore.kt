package com.example.my.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 足迹照片的本地存储。
 *
 * 为什么复制进私有目录而不是直接存 content:// Uri：
 * PhotoPicker 返回的 Uri 只在本次会话内被授权，来源应用卸载、云盘文件下线后就会失效；
 * 而导出长图时需要同步解码多张照片，本地文件才有确定性。
 * 作为代价会占用内部存储，因此统一把最长边压到 1280px，再用无损 WebP（Android 10 以下回退 PNG）
 * 写入；不做 JPEG 二次有损压缩，画质不随保存次数继续下降。
 */
object PhotoStore {

    private const val MAX_DIM = 1280
    private const val LOSSLESS_QUALITY = 100
    /** 列表缩略图的解码尺寸，避免整页 OOM */
    const val THUMB_DIM = 320
    /** 详情页封面尺寸，兼顾清晰度和内存 */
    const val DETAIL_DIM = 1080

    private val losslessFormat: Bitmap.CompressFormat
        get() = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS
        else Bitmap.CompressFormat.PNG

    private val losslessExt: String
        get() = if (Build.VERSION.SDK_INT >= 30) "webp" else "png"

    private fun losslessName(fileName: String): String {
        val base = fileName.substringBeforeLast('.', fileName)
        return "$base.$losslessExt"
    }

    private fun writeLossless(bitmap: Bitmap, target: File): Boolean =
        FileOutputStream(target).use {
            bitmap.compress(losslessFormat, LOSSLESS_QUALITY, it)
        }

    private fun root(context: Context): File = com.example.my.data.UserScope.dir(context, "footprints")

    fun dirOf(context: Context, footprintId: Int): File = File(root(context), footprintId.toString())

    /**
     * 把选中的照片复制进私有目录，返回成功写入的绝对路径列表。
     * 单张失败不影响其余照片，也不影响足迹本身保存。
     */
    suspend fun copyPhotos(context: Context, footprintId: Int, uris: List<Uri>): List<String> =
        withContext(Dispatchers.IO) {
            val dir = dirOf(context, footprintId)
            if (!dir.exists() && !dir.mkdirs()) return@withContext emptyList()
            val saved = ArrayList<String>(uris.size)
            uris.forEachIndexed { i, uri ->
                val dst = File(dir, losslessName("$i"))
                val bmp = decodeUri(context, uri, MAX_DIM) ?: return@forEachIndexed
                runCatching {
                    if (writeLossless(bmp, dst)) saved.add(dst.absolutePath)
                }
                bmp.recycle()
            }
            saved
        }

    fun deleteDir(context: Context, footprintId: Int) {
        runCatching { dirOf(context, footprintId).deleteRecursively() }
    }

    /**
     * 把视频 / 实况片段 / 配乐搬进足迹目录（不重新编码，纯文件搬移）。
     * [prefix] 用于区分用途（media / bgm），扩展名沿用源文件。
     */
    fun copyMedia(context: Context, footprintId: Int, src: File, prefix: String): String? = runCatching {
        if (!src.exists() || src.length() <= 0L) return@runCatching null
        val dir = dirOf(context, footprintId)
        if (!dir.exists() && !dir.mkdirs()) return@runCatching null
        val ext = src.extension.ifBlank { "mp4" }
        val dst = File(dir, "${prefix}_${System.currentTimeMillis()}.$ext")
        src.copyTo(dst, overwrite = true)
        dst.absolutePath
    }.getOrNull()

    /**
     * 单独复制一张照片并指定文件名。
     *
     * 路线足迹要区分起点 / 途中标记 / 终点，不能像 [copyPhotos] 那样按序号 0.jpg、1.jpg
     * 堆在一起——中途标记可能失败或用户跳过，序号一错位，起点就变成路边的树了。
     */
    suspend fun copyOne(context: Context, footprintId: Int, uri: Uri, fileName: String): String? =
        withContext(Dispatchers.IO) {
            val dir = dirOf(context, footprintId)
            if (!dir.exists() && !dir.mkdirs()) return@withContext null
            val dst = File(dir, losslessName(fileName))
            val bmp = decodeUri(context, uri, MAX_DIM) ?: return@withContext null
            val ok = runCatching {
                writeLossless(bmp, dst)
            }.getOrDefault(false)
            bmp.recycle()
            if (ok) dst.absolutePath else null
        }

    /** 解码本地照片文件；远程 URL 先落到 MediaCache 再解码（缩略图用小尺寸，详情页用大尺寸） */
    fun decodeFile(path: String, maxDim: Int): Bitmap? = runCatching {
        val local = MediaCache.localPath(path) ?: return@runCatching null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(local, opts)
        var sample = 1
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) sample *= 2
        BitmapFactory.decodeFile(local, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** 解码 content:// Uri 到最长边不超过 maxDim 的位图（含 EXIF 方向纠正） */
    fun decodeUri(context: Context, uri: Uri, maxDim: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 28) {
            // setTargetSize 必须按原始宽高等比缩放，否则任意比例的照片会被强制拉成正方形
            val target = targetSizeOf(context, uri, maxDim)
            val src = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            // ImageDecoder 已按 EXIF 自动摆正，这里不能再调 applyOrientation，否则会二次旋转
            android.graphics.ImageDecoder.decodeBitmap(src) { d, _, _ ->
                d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                target?.let { (w, h) -> d.setTargetSize(w, h) }
            }
        } else {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
                ?.let { applyOrientation(context, uri, it) }
        }
    } catch (e: Exception) {
        null
    }

    /** 按原始宽高等比计算目标尺寸（最长边 = maxDim）；取不到尺寸时返回 null */
    private fun targetSizeOf(context: Context, uri: Uri, maxDim: Int): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }
        val w = opts.outWidth
        val h = opts.outHeight
        if (w <= 0 || h <= 0) return null
        val scale = (maxDim.toFloat() / maxOf(w, h)).coerceAtMost(1f)
        return (w * scale).toInt().coerceAtLeast(1) to (h * scale).toInt().coerceAtLeast(1)
    }

    private fun applyOrientation(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        val degree = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: return bmp
        val angle = when (degree) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bmp
        }
        return runCatching {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(angle) }, true)
        }.getOrDefault(bmp)
    }
}

/** ML Kit 常见英文标签 → 中文对照（未收录的原样保留） */
private val LABEL_ZH = mapOf(
    "wall" to "墙", "plant" to "植物", "branch" to "树枝", "tree" to "树", "leaf" to "树叶",
    "sky" to "天空", "cloud" to "云", "nature" to "自然", "outdoor" to "户外", "landscape" to "风景",
    "mountain" to "山", "sea" to "海", "ocean" to "海洋", "beach" to "海滩", "water" to "水",
    "lake" to "湖", "river" to "河", "bridge" to "桥", "building" to "建筑", "architecture" to "建筑",
    "city" to "城市", "street" to "街道", "road" to "道路", "snow" to "雪", "sunset" to "日落",
    "sunrise" to "日出", "night" to "夜景", "flower" to "花", "grass" to "草", "stone" to "石",
    "rock" to "岩石", "food" to "美食", "dish" to "菜肴", "meal" to "餐食", "people" to "人物",
    "person" to "人物", "car" to "汽车", "vehicle" to "车辆", "boat" to "船", "bird" to "鸟",
    "dog" to "狗", "cat" to "猫", "animal" to "动物", "text" to "文字", "art" to "艺术",
    "pattern" to "图案", "texture" to "纹理", "material" to "材质", "wood" to "木", "glass" to "玻璃",
    "light" to "光影", "shadow" to "阴影", "garden" to "园林", "park" to "公园", "temple" to "寺庙",
    "tower" to "塔", "window" to "窗", "door" to "门", "fence" to "篱笆", "path" to "小路",
    "field" to "田野", "forest" to "森林", "desert" to "沙漠", "rain" to "雨", "fog" to "雾",
    "autumn" to "秋", "spring" to "春", "summer" to "夏", "winter" to "冬", "holiday" to "假日",
    "travel" to "旅行", "vacation" to "度假", "adventure" to "探险", "hiking" to "徒步"
)

/** 把 ML Kit 英文标签翻译为中文，未收录的原样返回 */
fun localizeLabels(raw: List<String>): List<String> =
    raw.map { LABEL_ZH[it.trim().lowercase()] ?: it.trim() }

/**
 * 清理自动打标签追加进备注的分隔符残留，并把旧版本写入的英文标签翻译成中文：
 * " · Wall · Branch · Plant" → "墙 · 树枝 · 植物"。
 * 普通句子（不含分隔符）原样返回；含分隔符的按段拆分，命中词表的段翻译，未命中的原样保留。
 */
fun cleanNote(note: String): String {
    val parts = note.split("·", "•", "・")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (parts.isEmpty()) return ""
    return parts.joinToString(" · ") { p -> LABEL_ZH[p.lowercase()] ?: p }
}
