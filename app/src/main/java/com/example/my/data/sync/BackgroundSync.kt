package com.example.my.data.sync

import android.content.Context
import android.graphics.Color as AndroidColor
import android.util.Log
import com.example.my.data.BackendApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.my.ui.theme.AppBackground
import com.example.my.ui.theme.AppBackgroundConfig
import com.example.my.ui.theme.AppFont
import com.example.my.ui.theme.BgColorMode
import com.example.my.ui.theme.BgFill
import com.example.my.ui.theme.BgLayout
import com.example.my.ui.theme.BgStyle
import com.example.my.ui.theme.BgTransition
import com.example.my.ui.theme.ColorSync
import com.example.my.ui.theme.GlassLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 背景与外观设置的云端同步。
 *
 * 背景图先走 `/api/sync/media` 换成 URL，其余参数（填充方式、颜色、模糊、暗角、
 * 字体、玻璃强度、动画、场景预设…）序列化进 `/api/sync/settings` 的 `__background`。
 * 登录/换机恢复时把图片下载回本机私有目录，再调用 [AppBackground.apply] 一次性还原。
 */
object BackgroundSync {

    private const val PREFS = "bg_cloud_map"
    private const val KEY_URL_PREFIX = "url:"
    private const val KEY_MTIME_PREFIX = "mtime:"
    private const val BG_DIR = "app_background"

    /** 序列化当前背景配置；图片先上传，失败则保留本机路径（恢复时忽略） */
    suspend fun snapshot(context: Context): JSONObject = withContext(Dispatchers.IO) {
        val cfg = AppBackground.snapshot()
        val out = JSONObject()
        out.put("enabled", cfg.enabled)
        out.put("fill", cfg.fill.name)
        out.put("mode", cfg.mode.name)
        out.put("style", cfg.style.name)
        out.put("layout", cfg.layout.name)
        out.put("color_sync", cfg.colorSync.name)
        out.put("colors", JSONArray().apply { cfg.colors.forEach { put(it.toArgb()) } })
        out.put("image_alpha", cfg.imageAlpha.toDouble())
        out.put("blur", cfg.blur.toDouble())
        out.put("interval", cfg.intervalSec)
        out.put("transition", cfg.transition.name)
        out.put("highlight", cfg.highlight.toArgb())
        out.put("text_color", cfg.textColor.toArgb())
        out.put("font", cfg.font.name)
        out.put("vignette", cfg.vignette.toDouble())
        out.put("grain", cfg.grain.toDouble())
        out.put("glass", cfg.glass.name)
        out.put("animate", cfg.animate)
        cfg.sceneId?.let { out.put("scene_id", it) }

        val urls = JSONArray()
        cfg.imagePaths.forEach { path ->
            val file = File(path)
            if (!file.isFile) return@forEach
            val url = cachedUrl(context, file) ?: run {
                val mime = when (file.extension.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                }
                val ref = runCatching { BackendApi.uploadMedia(file, mime) }.getOrNull()
                ref?.url?.let { rememberUrl(context, file, it) }
            }
            if (url != null) urls.put(url)
        }
        out.put("images", urls)
        out
    }

    /** 从云端设置恢复背景；图片下载失败时保留本机已有图片，不把背景清空 */
    suspend fun restore(context: Context, json: JSONObject?) {
        if (json == null) return
        val cfg = runCatching { parse(json) }.getOrElse {
            Log.w("BackgroundSync", "parse background failed", it)
            return
        }
        val remoteImages = (0 until (json.optJSONArray("images")?.length() ?: 0))
            .mapNotNull { json.optJSONArray("images")?.optString(it) }
            // 服务端返回的是 /uploads/... 相对地址；只有补全域名后才能下载
            .filter { it.startsWith("http") || it.startsWith("/uploads/") }
        val local = remoteImages.mapNotNull { url -> download(context, url) }
        val finalCfg = cfg.copy(
            enabled = cfg.enabled,
            imagePaths = local.map { it.absolutePath }.ifEmpty { cfg.imagePaths.filter { File(it).isFile } }
        )
        withContext(Dispatchers.Main) {
            if (finalCfg.enabled) {
                AppBackground.apply(context, finalCfg)
            } else if (!AppBackground.snapshot().enabled) {
                // 云端明确关闭且本机本来也没开，才回默认；
                // 本机刚设了背景、云端还是旧快照时不要清掉，等自动同步把它推上去。
                AppBackground.reset(context)
            }
        }
    }

    private fun parse(o: JSONObject): AppBackgroundConfig {
        fun color(key: String, def: Int): Int = o.optInt(key, def)
        val colors = o.optJSONArray("colors")?.let { arr ->
            (0 until arr.length()).map { Color(arr.optInt(it)) }
        } ?: AppBackgroundConfig.Default.colors
        return AppBackgroundConfig(
            enabled = o.optBoolean("enabled", false),
            fill = enum(o.optString("fill"), BgFill.COLOR),
            mode = enum(o.optString("mode"), BgColorMode.SINGLE),
            style = enum(o.optString("style"), BgStyle.LINEAR),
            layout = enum(o.optString("layout"), BgLayout.FILL),
            colorSync = enum(o.optString("color_sync"), ColorSync.OFF),
            colors = colors,
            imagePaths = emptyList(),
            imageAlpha = o.optDouble("image_alpha", 0.45).toFloat().coerceIn(0f, 1f),
            blur = o.optDouble("blur", 0.0).toFloat().coerceIn(0f, 50f),
            intervalSec = o.optInt("interval", 12).coerceIn(0, 300),
            transition = enum(o.optString("transition"), BgTransition.FADE),
            highlight = Color(color("highlight", 0xFFE07A5F.toInt())),
            textColor = Color(color("text_color", 0xFF1F2328.toInt())),
            font = enum(o.optString("font"), AppFont.SYSTEM),
            vignette = o.optDouble("vignette", 0.0).toFloat().coerceIn(0f, 100f),
            grain = o.optDouble("grain", 0.0).toFloat().coerceIn(0f, 100f),
            glass = enum(o.optString("glass"), GlassLevel.STANDARD),
            animate = o.optBoolean("animate", true),
            sceneId = o.optString("scene_id").takeIf { it.isNotBlank() }
        )
    }

    private inline fun <reified T : Enum<T>> enum(name: String, def: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(def)

    private fun cachedUrl(context: Context, file: File): String? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stamp = sp.getLong(KEY_MTIME_PREFIX + file.absolutePath, -1L)
        if (stamp != file.lastModified()) return null
        return sp.getString(KEY_URL_PREFIX + file.absolutePath, null)
    }

    private fun rememberUrl(context: Context, file: File, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_MTIME_PREFIX + file.absolutePath, file.lastModified())
            .putString(KEY_URL_PREFIX + file.absolutePath, url)
            .apply()
    }

    private fun download(context: Context, remote: String): File? = runCatching {
        val full = BackendApi.fullUrl(remote) ?: remote
        val dir = File(context.filesDir, BG_DIR).apply { mkdirs() }
        val target = File(dir, "cloud_${Integer.toHexString(full.hashCode())}.jpg")
        if (target.isFile && target.length() > 0) return target
        val conn = (URL(full).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 25_000
            instanceFollowRedirects = true
        }
        try {
            conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        if (target.length() > 0) target else null
    }.getOrElse {
        Log.w("BackgroundSync", "download failed $remote", it)
        null
    }
}
