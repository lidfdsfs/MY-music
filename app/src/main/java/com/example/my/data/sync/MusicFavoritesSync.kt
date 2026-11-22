package com.example.my.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.example.my.data.BackendApi
import com.example.my.data.CloudConfig
import com.example.my.data.music.MusicPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 音乐收藏云同步。
 *
 * 云端只保存「歌名 + 歌手」两列，不上传本机歌曲 id、文件路径、封面、歌词和音频。
 * 用歌名 + 歌手归一化后的哈希作为稳定 client_id，所以同一首歌在换机、换文件 id 后
 * 仍能识别为同一条收藏。
 *
 * 删除采用显式墓碑：本地记录「上次同步成功的键集合」，本次集合减去上次集合就是
 * 需要删除的云端记录；新增同理。上传成功后才推进基线，失败会保留差异下次重试。
 */
object MusicFavoritesSync {

    private const val PREFS = "cloud_sync_state"
    private const val KEY_BASELINE = "music_favorites_baseline_v1"
    private const val KIND = "music_favorites"
    private const val BATCH_LIMIT = 500

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var scheduled: Job? = null

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 收藏变更后合并触发一次上传；未登录 / 云端关闭时是空操作，本地已落盘。 */
    fun requestSync(context: Context, delayMs: Long = 1_500L) {
        val app = context.applicationContext
        if (!CloudConfig.isEnabled(app) || BackendApi.token == null) return
        synchronized(this) {
            scheduled?.cancel()
            scheduled = scope.launch {
                delay(delayMs)
                pushNow(app)
            }
        }
    }

    /**
     * 把本地收藏与云端对齐。返回 true 表示本次没有失败。
     * 首次运行只上传、不删除，避免把云端已有数据误删。
     */
    suspend fun pushNow(context: Context): Boolean = mutex.withLock {
        val app = context.applicationContext
        if (!CloudConfig.isEnabled(app) || BackendApi.token == null) return@withLock false

        val p = prefs(app)
        val current = MusicPlayer.favoriteRecords(app)
            .mapNotNull { (title, artist) ->
                val t = title.trim()
                if (t.isBlank()) null else FavoriteRecord(t, artist.trim(), keyOf(t, artist))
            }
            .distinctBy { it.key }
            .sortedBy { it.key }

        val currentKeys = current.map { it.key }.toSet()
        val hasBaseline = p.contains(KEY_BASELINE)
        val previous = p.getStringSet(KEY_BASELINE, emptySet()) ?: emptySet()
        // 有收藏解析不出歌名歌手时（无权限 / 文件已删），不能把“查不到”当成“已删除”
        val unresolved = runCatching { MusicPlayer.unresolvedFavoriteCount(app) }.getOrDefault(0)
        val added = current.filter { it.key !in previous }
        val deleted = if (hasBaseline && unresolved == 0) (previous - currentKeys).toList() else emptyList()

        if (hasBaseline && added.isEmpty() && deleted.isEmpty()) return@withLock true

        if (added.isEmpty() && deleted.isEmpty()) {
            if (unresolved == 0) p.edit().putStringSet(KEY_BASELINE, currentKeys).apply()
            return@withLock true
        }

        var ok = true
        val items = added.chunked(BATCH_LIMIT)
        val deletes = deleted.chunked(BATCH_LIMIT)
        val rounds = maxOf(items.size, deletes.size, 1)
        for (i in 0 until rounds) {
            val arr = JSONArray()
            items.getOrNull(i)?.forEach { record ->
                arr.put(
                    JSONObject()
                        .put("client_id", record.key)
                        .put("updated_at", System.currentTimeMillis())
                        // 只存歌手和歌名：没有 id、路径、封面、歌词或音频
                        .put("payload", JSONObject()
                            .put("title", record.title)
                            .put("artist", record.artist))
                )
            }
            val gone = deletes.getOrNull(i) ?: emptyList()
            if (arr.length() == 0 && gone.isEmpty()) continue
            if (!BackendApi.putCollection(KIND, arr, gone)) {
                ok = false
                break
            }
        }

        if (ok) {
            // 解析不全时只把本次成功新增的键并进基线，保留那些暂时读不到的老键，
            // 等权限/文件恢复后再决定是否真的删除。
            val baseline = if (unresolved == 0) currentKeys else previous + added.map { it.key }
            p.edit().putStringSet(KEY_BASELINE, baseline).apply()
        }
        ok
    }

    /**
     * 登录 / 重装后的收藏回灌。只读云端并合并进本地，不删本地已有收藏。
     * 兼容旧版全量档案里的 music.favorites（当时只有 id + 歌名歌手）。
     */
    suspend fun restore(context: Context, boot: JSONObject): Int {
        val app = context.applicationContext
        val records = ArrayList<Pair<String, String>>()
        val collection = boot.optJSONObject("collections")?.optJSONArray(KIND)
        if (collection != null) {
            for (i in 0 until collection.length()) {
                val payload = collection.optJSONObject(i)?.optJSONObject("payload") ?: continue
                val title = payload.optString("title").trim()
                val artist = payload.optString("artist").trim()
                if (title.isNotBlank()) records.add(title to artist)
            }
        }
        if (records.isEmpty()) {
            // 旧版档案：collections.music 里有一条 id=snapshot，payload 含 favorites
            val legacy = boot.optJSONObject("collections")?.optJSONArray("music")
            if (legacy != null) {
                for (i in 0 until legacy.length()) {
                    val payload = legacy.optJSONObject(i)?.optJSONObject("payload") ?: continue
                    val favs = payload.optJSONArray("favorites") ?: continue
                    for (j in 0 until favs.length()) {
                        val o = favs.optJSONObject(j) ?: continue
                        val title = o.optString("title").trim()
                        val artist = o.optString("artist").trim()
                        if (title.isNotBlank()) records.add(title to artist)
                    }
                }
            }
        }
        if (records.isEmpty()) return 0

        val merged = MusicPlayer.mergeCloudFavorites(app, records)
        // 回灌完成后把当前集合作为下次删除比较的基线
        seedBaseline(app)
        return merged
    }

    /** 把当前本地收藏记为「已同步」，不发起请求（回灌后 / 首次接管账号时用） */
    suspend fun seedBaseline(context: Context) {
        val app = context.applicationContext
        val keys = MusicPlayer.favoriteRecords(app)
            .mapNotNull { (title, artist) ->
                title.trim().takeIf { it.isNotBlank() }?.let { keyOf(it, artist) }
            }
            .toSet()
        prefs(app).edit().putStringSet(KEY_BASELINE, keys).apply()
    }

    /** 换账号时清掉上一个账号的同步基线，避免把 A 的收藏当成 B 的删除清单 */
    fun resetBaseline(context: Context) {
        prefs(context).edit().remove(KEY_BASELINE).apply()
    }

    data class FavoriteRecord(val title: String, val artist: String, val key: String)

    /** 歌名/歌手归一化后做 SHA-256，取前 32 位作为稳定 client_id */
    fun keyOf(title: String, artist: String): String {
        val t = normalize(title)
        val a = normalizeArtist(artist)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$t\u0001$a".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun normalize(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")

    /** 系统媒体库常见的「未知艺术家 / <unknown>」统一当成空歌手，避免换机后匹配不上 */
    private fun normalizeArtist(value: String): String {
        val v = normalize(value)
        return if (v in setOf("", "未知艺术家", "未知歌手", "<unknown>", "unknown")) "" else v
    }
}
