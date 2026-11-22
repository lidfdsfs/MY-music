package com.example.my.data.music

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 第三方歌单导入的共享管线：网易云 / QQ 都走这里，保证「占位入库 + 收藏 + 同名集合」行为一致。
 *
 * 只存歌名/歌手占位记录（pendingSearch=true），**不在导入阶段联网下载**——音源等用户
 * 点击播放时由现有 resolveSong 按需获取（与截图导入同链路），避免一次性打满流量或下错歌。
 */
object ImportPipeline {

    data class Track(
        val title: String,
        val artist: String = "",
        val album: String = "",
        val durationMs: Long = 0L
    )

    data class Report(
        val imported: Int,
        val already: Int,
        val collectionName: String
    )

    /**
     * @param collectionId 稳定 id，如 "netease:123456"；重导同一歌单会覆盖集合而不是重复建
     * @param collectionName 展示名，如 "我喜欢的音乐"
     */
    suspend fun importTracks(
        context: Context,
        source: String,
        sourceLabel: String,
        collectionId: String,
        collectionName: String,
        tracks: List<Track>
    ): Report = withContext(Dispatchers.IO) {
        MusicPlayer.init(context)

        // 已在本机的键（媒体库 + 缓存 + 待获取占位）：重导不重复入库
        val existingKeys = (MusicPlayer.songs + MusicPlayer.webSongs)
            .map { MusicCollections.normalizeKey(it.title, it.artist) }
            .toHashSet()

        val clean = tracks
            .map { it.copy(title = it.title.trim(), artist = it.artist.trim()) }
            .filter { it.title.isNotBlank() }
        if (clean.isEmpty()) return@withContext Report(0, 0, collectionName)

        val base = System.currentTimeMillis() * 1000L
        val toAdd = ArrayList<LocalSong>()
        val keys = ArrayList<String>()
        var already = 0
        val seen = HashSet<String>()
        clean.forEachIndexed { index, t ->
            val key = MusicCollections.normalizeKey(t.title, t.artist)
            if (!seen.add(key)) return@forEachIndexed
            keys.add(key)
            if (key in existingKeys) {
                already++
            } else {
                toAdd.add(
                    LocalSong(
                        id = -(base + index + 1L),
                        title = t.title,
                        artist = t.artist,
                        album = t.album.ifBlank { sourceLabel },
                        durationMs = t.durationMs,
                        uri = Uri.EMPTY,
                        pendingSearch = true
                    )
                )
            }
        }

        if (toAdd.isNotEmpty()) {
            MusicPlayer.addPendingSongs(toAdd)
            withContext(Dispatchers.Main.immediate) {
                toAdd.forEach { MusicPlayer.markFavorite(it.id) }
            }
        }

        // 同名集合：即使全部已收藏，也保留/刷新集合，方便用户从导入页直接播放这一组
        MusicCollections.save(
            context,
            MusicCollections.SongCollection(
                id = collectionId,
                name = collectionName,
                source = source,
                sourceLabel = sourceLabel,
                songKeys = keys,
                createdAt = System.currentTimeMillis()
            )
        )

        Report(imported = toAdd.size, already = already, collectionName = collectionName)
    }
}
