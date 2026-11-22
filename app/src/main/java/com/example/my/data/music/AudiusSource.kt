package com.example.my.data.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Audius 免费音源。
 *
 * 官方公开 API，只需 app_name，不需要 Key / 登录：
 *  - 搜索：/v1/tracks/search
 *  - 全曲流：/v1/tracks/{id}/stream（302 到 CDN，可直接当音频下载）
 * 曲库以独立音乐、电音、欧美为主，华语主流很少；定位是「免费全曲兜底」。
 */
data class AudiusTrack(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val durationSec: Int,
    val artwork: String,
    val pageUrl: String,
    val streamUrl: String
) {
    val durationText: String
        get() = if (durationSec <= 0) "" else "%d:%02d".format(durationSec / 60, durationSec % 60)
}

object AudiusSource {
    private const val BASE = "https://api.audius.co/v1"
    private const val APP = "MYMusic"
    private const val CACHE_MS = 10 * 60 * 1000L

    /** 下载 CDN 时也带一个正常 UA，避免部分节点拒绝空 UA */
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Mobile Safari/537.36"

    private val cache = ConcurrentHashMap<String, Pair<Long, List<AudiusTrack>>>()

    suspend fun search(query: String, limit: Int = 12): List<AudiusTrack> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()
        val key = q.lowercase() + "|" + limit
        cache[key]?.let { (at, list) ->
            if (System.currentTimeMillis() - at < CACHE_MS) return@withContext list
        }
        val url = "$BASE/tracks/search?query=${enc(q)}&limit=$limit&app_name=$APP&sort_method=relevant"
        val body = runCatching { get(url) }.getOrNull() ?: return@withContext emptyList()
        val list = runCatching { parse(body) }.getOrDefault(emptyList())
        if (list.isNotEmpty()) cache[key] = System.currentTimeMillis() to list
        list
    }

    private fun parse(body: String): List<AudiusTrack> {
        val root = JSONObject(body)
        val data = root.opt("data")
        val arr: JSONArray = when (data) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray("tracks") ?: JSONArray()
            else -> JSONArray()
        }
        val out = mutableListOf<AudiusTrack>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            if (!o.optBoolean("is_streamable", true)) continue
            val title = o.optString("title")
            if (title.isBlank()) continue
            val user = o.optJSONObject("user")
            val artist = user?.optString("name").orEmpty()
                .ifBlank { user?.optString("handle").orEmpty() }
                .removePrefix("@")
            val art = o.optJSONObject("artwork")?.let { a ->
                listOf("480x480", "150x150", "1000x1000")
                    .firstNotNullOfOrNull { k -> a.optString(k).takeIf { it.isNotBlank() } }
            }.orEmpty()
            val sec = o.optInt("duration", 0)
                .takeIf { it > 0 }
                ?: o.optString("duration").toIntOrNull()
                ?: 0
            val permalink = o.optString("permalink").trim()
            out += AudiusTrack(
                id = id,
                title = title.take(80),
                artist = artist.take(40),
                genre = o.optString("genre").take(24),
                durationSec = sec,
                artwork = art,
                pageUrl = if (permalink.startsWith("http")) permalink else "https://audius.co$permalink",
                streamUrl = "$BASE/tracks/$id/stream?app_name=$APP"
            )
        }
        return out
    }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
