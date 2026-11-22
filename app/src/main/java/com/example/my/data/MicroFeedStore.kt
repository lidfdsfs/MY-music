package com.example.my.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.example.my.model.MicroAuthor
import com.example.my.model.MicroComment
import com.example.my.model.MicroPost

/**
 * 微足迹的本地公开池与互动状态。
 *
 * 与 Footprint 的 Room 权威存储严格分开：这里只存「发布出去的快照」和「我点过的赞/收藏」，
 * 用独立 SP 而不是塞进 app_settings，方便用户清理公开内容时不影响私密足迹。
 * 示例帖的点赞基数在 MicroFeedSeed 里，本地只记增量位，避免越点越膨胀。
 */
object MicroFeedStore {
    private const val PREFS = "micro_feed_local"
    private const val KEY = "snapshot_v1"

    data class Snapshot(
        val liked: Set<String> = emptySet(),
        val favored: Set<String> = emptySet(),
        val followed: Set<Int> = emptySet(),
        val shared: Set<String> = emptySet(),
        val hidden: Set<String> = emptySet(),
        val comments: Map<String, List<MicroComment>> = emptyMap(),
        /** 本地发布 id → 远端服务帖子 id：重启后仍能撤回社区上的那条 */
        val remoteIds: Map<String, String> = emptyMap(),
        val published: List<MicroPost> = emptyList()
    )

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): Snapshot {
        val raw = context.prefs().getString(KEY, null) ?: return Snapshot()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return Snapshot()
        return Snapshot(
            liked = root.optArray("liked").toStringSet(),
            favored = root.optArray("favored").toStringSet(),
            followed = root.optArray("followed").toIntSet(),
            shared = root.optArray("shared").toStringSet(),
            hidden = root.optArray("hidden").toStringSet(),
            comments = root.optJSONObject("comments")?.let { obj ->
                obj.keys().asSequence().associate { id ->
                    id to obj.optArray(id).toComments()
                }
            }.orEmpty(),
            remoteIds = root.optJSONObject("remoteIds")?.let { obj ->
                obj.keys().asSequence().mapNotNull { key ->
                    obj.optString(key).takeIf { it.isNotBlank() }?.let { key to it }
                }.toMap()
            }.orEmpty(),
            published = root.optArray("published").toPosts()
        )
    }

    fun save(context: Context, s: Snapshot) {
        val root = JSONObject().apply {
            put("liked", JSONArray(s.liked))
            put("favored", JSONArray(s.favored))
            put("followed", JSONArray(s.followed))
            put("shared", JSONArray(s.shared))
            put("hidden", JSONArray(s.hidden))
            put("comments", JSONObject().apply {
                s.comments.forEach { (id, list) -> put(id, JSONArray(list.map { it.toJson() })) }
            })
            put("remoteIds", JSONObject().apply {
                s.remoteIds.forEach { (localId, remoteId) -> put(localId, remoteId) }
            })
            put("published", JSONArray(s.published.map { it.toJson() }))
        }
        context.prefs().edit().putString(KEY, root.toString()).apply()
    }

    private fun JSONObject.optArray(name: String): JSONArray = optJSONArray(name) ?: JSONArray()

    private fun JSONArray.toStringSet(): Set<String> =
        (0 until length()).mapNotNull { runCatching { getString(it) }.getOrNull() }.toSet()

    private fun JSONArray.toIntSet(): Set<Int> =
        (0 until length()).mapNotNull { runCatching { getInt(it) }.getOrNull() }.toSet()

    private fun JSONArray.toStrings(): List<String> =
        (0 until length()).mapNotNull { runCatching { getString(it) }.getOrNull() }

    private fun JSONArray.toComments(): List<MicroComment> =
        (0 until length()).mapNotNull { i ->
            runCatching {
                val o = getJSONObject(i)
                MicroComment(
                    id = o.optString("id"),
                    authorName = o.optString("authorName"),
                    text = o.optString("text"),
                    time = o.optString("time"),
                    authorId = o.optInt("authorId"),
                    replyTo = o.optString("replyTo").takeIf { it.isNotBlank() },
                    likes = o.optInt("likes")
                )
            }.getOrNull()
        }

    private fun JSONArray.toPosts(): List<MicroPost> =
        (0 until length()).mapNotNull { i ->
            runCatching {
                val o = getJSONObject(i)
                val a = o.optJSONObject("author") ?: JSONObject()
                MicroPost(
                    id = o.optString("id"),
                    author = MicroAuthor(
                        id = a.optInt("id"),
                        name = a.optString("name"),
                        avatarText = a.optString("avatarText"),
                        avatarPath = a.optString("avatarPath").takeIf { it.isNotBlank() },
                        bio = a.optString("bio"),
                        followers = a.optInt("followers"),
                        verified = a.optBoolean("verified")
                    ),
                    place = o.optString("place"),
                    city = o.optString("city"),
                    caption = o.optString("caption"),
                    tags = o.optArray("tags").toStrings(),
                    imagePaths = o.optArray("imagePaths").toStrings(),
                    videoPath = o.optString("videoPath").takeIf { it.isNotBlank() },
                    bgmTitle = o.optString("bgmTitle").takeIf { it.isNotBlank() },
                    bgmPath = o.optString("bgmPath").takeIf { it.isNotBlank() },
                    bgmArtist = o.optString("bgmArtist").takeIf { it.isNotBlank() },
                    baseLikes = o.optInt("baseLikes"),
                    baseComments = o.optInt("baseComments"),
                    baseShares = o.optInt("baseShares"),
                    favorites = o.optInt("favorites"),
                    isMine = o.optBoolean("isMine", true),
                    footprintId = o.optInt("footprintId", 0).takeIf { it > 0 },
                    publishedAt = o.optLong("publishedAt")
                )
            }.getOrNull()
        }

    private fun MicroComment.toJson() = JSONObject().apply {
        put("id", id)
        put("authorName", authorName)
        put("text", text)
        put("time", time)
        put("authorId", authorId)
        put("replyTo", replyTo ?: "")
        put("likes", likes)
    }

    private fun MicroPost.toJson() = JSONObject().apply {
        put("id", id)
        put("author", JSONObject().apply {
            put("id", author.id)
            put("name", author.name)
            put("avatarText", author.avatarText)
            put("avatarPath", author.avatarPath ?: "")
            put("bio", author.bio)
            put("followers", author.followers)
            put("verified", author.verified)
        })
        put("place", place)
        put("city", city)
        put("caption", caption)
        put("tags", JSONArray(tags))
        put("imagePaths", JSONArray(imagePaths))
        put("videoPath", videoPath ?: "")
        put("bgmTitle", bgmTitle ?: "")
        put("bgmPath", bgmPath ?: "")
        put("bgmArtist", bgmArtist ?: "")
        put("baseLikes", baseLikes)
        put("baseComments", baseComments)
        put("baseShares", baseShares)
        put("favorites", favorites)
        put("isMine", isMine)
        put("footprintId", footprintId ?: 0)
        put("publishedAt", publishedAt)
    }
}
