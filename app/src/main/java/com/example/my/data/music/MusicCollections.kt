package com.example.my.data.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 第三方歌单导入后在本机的「同名集合」：只存「歌名|歌手」归一化键，不绑死本地 id。
 *
 * 为什么不用 songId：导入时写入的是 pendingSearch 占位记录（负数 id），用户点击播放后
 * 会被真正的缓存歌曲替换（id 会变）。用归一化键匹配，集合在「占位 → 下载完成」之间
 * 始终指向同一首歌，不会因为 id 变化而丢歌。
 *
 * 持久化在 SharedPreferences（JSON 数组），不上传、不进云同步；用户可在导入页删除。
 */
object MusicCollections {

    private const val PREFS = "music_collections"
    private const val KEY = "collections_v1"

    data class SongCollection(
        val id: String,
        val name: String,
        /** 来源平台：netease / qq */
        val source: String,
        val sourceLabel: String,
        /** 归一化后的「歌名\u0001歌手」键，顺序即歌单顺序 */
        val songKeys: List<String>,
        val createdAt: Long
    ) {
        val size: Int get() = songKeys.size
    }

    fun normalizeKey(title: String, artist: String): String =
        "${normalize(title)}\u0001${normalizeArtist(artist)}"

    private fun normalize(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun normalizeArtist(value: String): String {
        val v = normalize(value)
        return if (v in setOf("", "未知艺术家", "未知歌手", "<unknown>", "unknown")) "" else v
    }

    fun all(context: Context): List<SongCollection> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                decode(arr.optJSONObject(i))
            }.sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    private fun decode(o: JSONObject?): SongCollection? {
        if (o == null) return null
        val id = o.optString("id").trim()
        val name = o.optString("name").trim()
        if (id.isBlank() || name.isBlank()) return null
        val keysArr = o.optJSONArray("songKeys") ?: JSONArray()
        val keys = (0 until keysArr.length()).mapNotNull { keysArr.optString(it).trim().takeIf { it.isNotBlank() } }
        return SongCollection(
            id = id,
            name = name,
            source = o.optString("source", "unknown"),
            sourceLabel = o.optString("sourceLabel", name),
            songKeys = keys,
            createdAt = o.optLong("createdAt", 0L)
        )
    }

    /** 同名 + 同来源则覆盖（更新歌曲列表与时间），否则新增 */
    fun save(context: Context, collection: SongCollection) {
        val list = all(context).toMutableList()
        val idx = list.indexOfFirst { it.id == collection.id }
        if (idx >= 0) list[idx] = collection else list.add(0, collection)
        persist(context, list)
    }

    fun delete(context: Context, id: String) {
        persist(context, all(context).filterNot { it.id == id })
    }

    private fun persist(context: Context, list: List<SongCollection>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("source", c.source)
                    .put("sourceLabel", c.sourceLabel)
                    .put("createdAt", c.createdAt)
                    .put("songKeys", JSONArray().apply { c.songKeys.forEach { put(it) } })
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, arr.toString()).apply()
    }

    /**
     * 把集合键解析成本机可播放的 LocalSong（占位记录 + 已缓存歌曲 + 媒体库）。
     * 找不到音源的键会被跳过，顺序按集合原始顺序。
     */
    fun resolveSongs(collection: SongCollection): List<LocalSong> {
        val keySet = collection.songKeys.toSet()
        val all = (MusicPlayer.songs + MusicPlayer.webSongs).distinctBy { it.id }
        val byKey = LinkedHashMap<String, LocalSong>()
        for (s in all) {
            val k = normalizeKey(s.title, s.artist)
            if (k in keySet) byKey.putIfAbsent(k, s)
        }
        return collection.songKeys.mapNotNull { byKey[it] }
    }
}
