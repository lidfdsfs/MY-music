package com.example.my.data.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * 只读元数据联想：QQ 音乐 + 网易云公开搜索接口。
 *
 * 两个接口都不需要登录/Key，返回歌名、歌手、专辑，用来补全搜索框联想；
 * 不提供音频地址，点击后仍走现有曲库与 Audius 免费源。
 */
data class MetaHit(
    val title: String,
    val artist: String,
    val album: String,
    val source: String
)

object MetadataSearch {
    private const val QQ_SEARCH = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
    private const val NETEASE_SEARCH = "https://music.163.com/api/search/get/web"
    private const val CACHE_MS = 10 * 60 * 1000L
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Mobile Safari/537.36"

    private val cache = ConcurrentHashMap<String, Pair<Long, List<MetaHit>>>()

    suspend fun suggestions(query: String, limit: Int = 6): List<MetaHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()
        val key = q.lowercase() + "|" + limit
        cache[key]?.let { (at, list) ->
            if (System.currentTimeMillis() - at < CACHE_MS) return@withContext list
        }
        // 两个平台并行，谁先回来都先合并，单个失败不影响另一个
        val merged = coroutineScope {
            val qq = async { runCatching { searchQq(q) }.getOrDefault(emptyList()) }
            val ne = async { runCatching { searchNetease(q) }.getOrDefault(emptyList()) }
            qq.await() + ne.await()
        }
        val norm = { s: String -> s.lowercase().replace(Regex("[^\\u4e00-\\u9fa5a-z0-9]"), "") }
        val want = norm(q)
        val list = merged
            .filter { it.title.isNotBlank() }
            .distinctBy { norm(it.title) to norm(it.artist) }
            .sortedBy { hit ->
                val t = norm(hit.title)
                when {
                    t == want -> 0
                    t.startsWith(want) -> 1
                    t.contains(want) -> 2
                    else -> 3
                }
            }
            .take(limit)
        if (list.isNotEmpty()) cache[key] = System.currentTimeMillis() to list
        list
    }

    private fun searchQq(q: String): List<MetaHit> {
        val url = "$QQ_SEARCH?p=1&n=8&w=${enc(q)}&format=json&cr=1&t=0" +
            "&inCharset=utf-8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
        val body = get(url, "https://y.qq.com/") ?: return emptyList()
        val txt = body.trim().let {
            if (it.startsWith("callback(")) it.removePrefix("callback(").removeSuffix(")") else it
        }
        val arr = runCatching {
            JSONObject(txt).optJSONObject("data")
                ?.optJSONObject("song")?.optJSONArray("list")
        }.getOrNull() ?: return emptyList()
        return parseQq(arr)
    }

    private fun parseQq(arr: JSONArray): List<MetaHit> {
        val out = mutableListOf<MetaHit>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("songname").ifBlank { o.optString("title") }
            if (title.isBlank()) continue
            val singers = o.optJSONArray("singer") ?: o.optJSONArray("singer_list")
            val artist = (0 until (singers?.length() ?: 0))
                .mapNotNull { singers?.optJSONObject(it)?.optString("name") }
                .filter { it.isNotBlank() }
                .joinToString(" / ")
            val album = o.optString("albumname").ifBlank {
                o.optJSONObject("album")?.optString("name").orEmpty()
            }
            out += MetaHit(title.take(80), artist.take(40), album.take(40), "QQ音乐")
        }
        return out
    }

    private fun searchNetease(q: String): List<MetaHit> {
        val url = "$NETEASE_SEARCH?s=${enc(q)}&type=1&limit=8&offset=0"
        val body = get(url, "https://music.163.com/") ?: return emptyList()
        val arr = runCatching {
            JSONObject(body).optJSONObject("result")?.optJSONArray("songs")
        }.getOrNull() ?: return emptyList()
        val out = mutableListOf<MetaHit>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("name")
            if (title.isBlank()) continue
            val artists = o.optJSONArray("artists")
            val artist = (0 until (artists?.length() ?: 0))
                .mapNotNull { artists?.optJSONObject(it)?.optString("name") }
                .filter { it.isNotBlank() }
                .joinToString(" / ")
            val album = o.optJSONObject("album")?.optString("name").orEmpty()
            out += MetaHit(title.take(80), artist.take(40), album.take(40), "网易云")
        }
        return out
    }

    private fun get(url: String, referer: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 7_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Referer", referer)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
