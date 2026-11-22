package com.example.my.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.my.model.Comment
import com.example.my.model.CommentReply
import com.example.my.model.CostItem
import com.example.my.model.DayPlan
import com.example.my.model.Footprint
import com.example.my.model.Guide
import com.example.my.model.Route
import com.example.my.model.RouteMark
import com.example.my.model.RoutePoint
import com.example.my.model.Spot
import org.json.JSONArray
import org.json.JSONObject

/** 攻略离线缓存行（整篇 Guide 序列化为 json 存一行） */
@Entity(tableName = "guide_cache")
data class GuideCache(
    @PrimaryKey val id: Int,
    val json: String,
    val cachedAt: Long
)

/** 某篇攻略的评论列表离线缓存行 */
@Entity(tableName = "comment_cache")
data class CommentCache(
    @PrimaryKey val guideId: Int,
    val json: String,
    val cachedAt: Long
)

/** 用户足迹本地权威存储行（整条 Footprint 序列化为 json 存一行） */
@Entity(tableName = "footprint_cache")
data class FootprintCache(
    @PrimaryKey val id: Int,
    val json: String,
    val cachedAt: Long
)

/** 用户行程本地权威存储行（整条 Route 序列化为 json 存一行） */
@Entity(tableName = "route_cache")
data class RouteCache(
    @PrimaryKey val id: Int,
    val json: String,
    val cachedAt: Long
)

@Dao
interface CacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGuides(items: List<GuideCache>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComments(item: CommentCache)

    @Query("DELETE FROM guide_cache")
    suspend fun clearGuides()

    @Query("DELETE FROM comment_cache")
    suspend fun clearComments()

    @Query("SELECT * FROM guide_cache")
    suspend fun getAllGuides(): List<GuideCache>

    @Query("SELECT * FROM comment_cache WHERE guideId = :guideId")
    suspend fun getComments(guideId: Int): CommentCache?

    // ---------- 足迹 / 路线：本地权威存储 ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFootprints(items: List<FootprintCache>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutes(items: List<RouteCache>)

    @Query("DELETE FROM footprint_cache")
    suspend fun clearFootprints()

    @Query("DELETE FROM route_cache")
    suspend fun clearRoutes()

    @Query("SELECT * FROM footprint_cache")
    suspend fun getAllFootprints(): List<FootprintCache>

    @Query("SELECT * FROM route_cache")
    suspend fun getAllRoutes(): List<RouteCache>
}

@Database(
    entities = [GuideCache::class, CommentCache::class, FootprintCache::class, RouteCache::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cacheDao(): CacheDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "my_trace.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}

// ---------- 模型 ↔ JSON（离线缓存与待传队列共用） ----------

internal fun spotToJson(s: Spot): JSONObject = JSONObject().apply {
    put("name", s.name)
    put("note", s.note)
    put("cost", s.cost)
    put("tips", s.tips ?: "")
    put("special", s.special)
    put("specialReason", s.specialReason ?: "")
    put("seed", s.seed)
}

internal fun spotFromJson(o: JSONObject): Spot = Spot(
    name = o.optString("name"),
    note = o.optString("note"),
    cost = o.optString("cost"),
    tips = o.optString("tips").ifBlank { null },
    special = o.optBoolean("special"),
    specialReason = o.optString("specialReason").ifBlank { null },
    seed = o.optInt("seed")
)

internal fun dayPlansToJson(plans: List<DayPlan>): JSONArray = JSONArray().apply {
    plans.forEach { d ->
        put(JSONObject().apply {
            put("day", d.day)
            put("title", d.title)
            put("special", d.special)
            put("specialReason", d.specialReason ?: "")
            put("spots", JSONArray().apply { d.spots.forEach { put(spotToJson(it)) } })
        })
    }
}

internal fun dayPlansFromJson(arr: JSONArray?): List<DayPlan> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).map { i ->
        val d = arr.getJSONObject(i)
        val spotsArr = d.optJSONArray("spots")
        DayPlan(
            day = d.optInt("day", i + 1),
            title = d.optString("title"),
            special = d.optBoolean("special"),
            specialReason = d.optString("specialReason").ifBlank { null },
            spots = if (spotsArr == null) emptyList()
            else (0 until spotsArr.length()).map { j -> spotFromJson(spotsArr.getJSONObject(j)) }
        )
    }
}

internal fun strListToJson(list: List<String>): JSONArray =
    JSONArray().apply { list.forEach { put(it) } }

internal fun strListFromJson(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
}

/**
 * 兼容两种线上历史格式：
 * 1. 正常数组：["a","b"]
 * 2. 旧版压缩成字符串：a b c（photos/cities/tags 都出现过）
 * 不能只按 JSONArray 读，否则换机恢复时照片会被读成空列表，页面就是空白。
 */
internal fun jsonStringList(value: Any?): List<String> {
    return when (value) {
        is JSONArray -> strListFromJson(value)
        is String -> {
            val raw = value.trim()
            if (raw.isEmpty() || raw == "[]") return emptyList()
            if (raw.startsWith("[") && raw.endsWith("]")) {
                runCatching { JSONArray(raw) }.getOrNull()?.let { return strListFromJson(it) }
            }
            raw.removeSurrounding("[", "]")
                .split(Regex("[,\\s]+"))
                .map { it.trim().trim('"') }
                .filter { it.isNotBlank() && it != "[]" }
        }
        else -> emptyList()
    }
}

internal fun strListOf(o: JSONObject, key: String): List<String> = jsonStringList(o.opt(key))

/** 路线点 "lat,lon,time"；坏点丢弃，不拖垮整条路线 */
internal fun parseFootprintRoute(arr: JSONArray?): List<RoutePoint> {
    arr ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val parts = arr.optString(i).split(',')
        val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
        val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
        RoutePoint(lat, lon, parts.getOrNull(2)?.toLongOrNull() ?: 0L)
    }
}

/** 途中标记点；一个坏标记不影响整条足迹 */
internal fun parseFootprintMarks(arr: JSONArray?): List<RouteMark> {
    arr ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        RouteMark(
            lat = o.optDouble("lat", 0.0),
            lon = o.optDouble("lon", 0.0),
            note = o.optString("note", ""),
            photoPath = o.optString("photo").takeIf { it.isNotBlank() },
            time = o.optLong("time", 0L)
        )
    }
}

internal fun guideToJson(g: Guide, coverName: String? = null): JSONObject = JSONObject().apply {
    put("id", g.id)
    put("title", g.title)
    put("city", g.city)
    put("days", g.days)
    put("type", g.type)
    put("author", g.author)
    put("authorIntro", g.authorIntro)
    put("likes", g.likes)
    put("favs", g.favs)
    put("cost", g.cost)
    put("coverSeed", g.coverSeed)
    put("tags", strListToJson(g.tags))
    put("intro", g.intro)
    put("costBreakdown", JSONArray().apply {
        g.costBreakdown.forEach { c ->
            put(JSONObject().put("label", c.label).put("amount", c.amount).put("total", c.total))
        }
    })
    put("tips", strListToJson(g.tips))
    put("itinerary", dayPlansToJson(g.itinerary))
    put("featured", g.featured)
    put("theme", g.theme)
    put("region", g.region)
    put("viewedCount", g.viewedCount)
    put("bannerTitle", g.bannerTitle)
    put("bannerSubtitle", g.bannerSubtitle)
    put("liked", g.liked)
    put("favored", g.favored)
    put("authorId", g.authorId)
    // 存资源名而不是 R.id：应用升级后 R.id 可能错位，资源名稳定
    put("coverName", coverName ?: "")
}

internal fun guideFromJson(o: JSONObject, coverId: Int? = null): Guide {
    val cbArr = o.optJSONArray("costBreakdown")
    return Guide(
        id = o.optInt("id"),
        title = o.optString("title"),
        city = o.optString("city"),
        days = o.optInt("days", 1),
        type = o.optString("type"),
        author = o.optString("author"),
        authorIntro = o.optString("authorIntro"),
        likes = o.optInt("likes"),
        favs = o.optInt("favs"),
        cost = o.optString("cost"),
        coverSeed = o.optInt("coverSeed"),
        tags = strListFromJson(o.optJSONArray("tags")),
        intro = o.optString("intro"),
        costBreakdown = if (cbArr == null) emptyList() else (0 until cbArr.length()).map { i ->
            val c = cbArr.getJSONObject(i)
            CostItem(c.optString("label"), c.optString("amount"), c.optBoolean("total"))
        },
        tips = strListFromJson(o.optJSONArray("tips")),
        itinerary = dayPlansFromJson(o.optJSONArray("itinerary")),
        commentList = emptyList(),
        featured = o.optBoolean("featured"),
        theme = o.optString("theme"),
        region = o.optString("region"),
        viewedCount = o.optInt("viewedCount"),
        bannerTitle = o.optString("bannerTitle"),
        bannerSubtitle = o.optString("bannerSubtitle"),
        liked = o.optBoolean("liked"),
        favored = o.optBoolean("favored"),
        coverResId = coverId,
        authorId = o.optInt("authorId", -1)
    )
}

internal fun routeToJson(r: Route, coverName: String? = null): JSONObject = JSONObject().apply {
    put("id", r.id)
    put("title", r.title)
    put("year", r.year)
    put("date", r.date)
    put("days", r.days)
    put("cities", strListToJson(r.cities))
    put("coverSeed", r.coverSeed)
    put("special", r.special)
    put("specialName", r.specialName ?: "")
    put("specialReason", r.specialReason ?: "")
    put("note", r.note)
    put("tags", strListToJson(r.tags))
    put("totalCost", r.totalCost)
    put("isPublic", r.isPublic)
    put("photoCount", r.photoCount)
    put("itinerary", dayPlansToJson(r.itinerary))
    put("coverName", coverName ?: "")
}

internal fun routeFromJson(o: JSONObject, coverId: Int? = null): Route = Route(
    id = o.optInt("id"),
    title = o.optString("title"),
    year = o.optInt("year"),
    date = o.optString("date"),
    days = o.optInt("days", 1),
    cities = strListOf(o, "cities"),
    coverSeed = o.optInt("coverSeed"),
    special = o.optBoolean("special"),
    specialName = o.optString("specialName").ifBlank { null },
    specialReason = o.optString("specialReason").ifBlank { null },
    note = o.optString("note"),
    tags = strListOf(o, "tags"),
    totalCost = o.optString("totalCost"),
    isPublic = o.optBoolean("isPublic", true),
    photoCount = o.optInt("photoCount"),
    itinerary = dayPlansFromJson(o.optJSONArray("itinerary")),
    coverResId = coverId
)

internal fun commentToJson(c: Comment): JSONObject = JSONObject().apply {
    put("user", c.user)
    put("time", c.time)
    put("content", c.content)
    put("likes", c.likes)
    put("liked", c.liked)
    put("id", c.id)
    put("replies", JSONArray().apply {
        c.replies.forEach { r ->
            put(JSONObject().apply {
                put("user", r.user)
                put("time", r.time)
                put("content", r.content)
                put("likes", r.likes)
                put("liked", r.liked)
                put("replyTo", r.replyTo ?: "")
                put("id", r.id)
            })
        }
    })
}

internal fun commentFromJson(o: JSONObject): Comment {
    val rArr = o.optJSONArray("replies")
    return Comment(
        user = o.optString("user"),
        time = o.optString("time"),
        content = o.optString("content"),
        likes = o.optInt("likes"),
        liked = o.optBoolean("liked"),
        id = o.optInt("id"),
        replies = if (rArr == null) emptyList() else (0 until rArr.length()).map { i ->
            val r = rArr.getJSONObject(i)
            CommentReply(
                user = r.optString("user"),
                time = r.optString("time"),
                content = r.optString("content"),
                likes = r.optInt("likes"),
                liked = r.optBoolean("liked"),
                replyTo = r.optString("replyTo").ifBlank { null },
                id = r.optInt("id")
            )
        }
    )
}

// ---------- 足迹序列化（本地 SQLite 权威存储） ----------

internal fun footprintToJson(fp: Footprint): JSONObject = JSONObject().apply {
    put("id", fp.id)
    put("city", fp.city)
    put("name", fp.name)
    put("note", fp.note)
    put("date", fp.date)
    put("x", fp.x.toDouble())
    put("y", fp.y.toDouble())
    put("special", fp.special)
    put("specialReason", fp.specialReason ?: "")
    put("seed", fp.seed)
    put("routeId", fp.routeId ?: JSONObject.NULL)
    put("photos", JSONArray().apply { fp.photoPaths.forEach { put(it) } })
    // 与读取对称：0 值不写入，避免写进去再读回被当成缺失
    fp.dateMillis?.takeIf { it != 0L }?.let { put("dateMillis", it) }
    // 精确经纬度：选点/EXIF 得到的原始坐标，缺了就退回 x/y 反算
    if (fp.lat != null && fp.lon != null) {
        put("lat", fp.lat)
        put("lon", fp.lon)
    }
    // 视频 / 实况 / 配乐
    fp.videoPath?.let { put("videoPath", it) }
    if (fp.videoPaths.isNotEmpty()) put("videoPaths", JSONArray().apply { fp.videoPaths.forEach { put(it) } })
    if (fp.videoIsLive) put("videoIsLive", true)
    if (fp.videoBytes > 0L) put("videoBytes", fp.videoBytes)
    fp.bgmPath?.let { put("bgmPath", it) }
    fp.bgmTitle?.let { put("bgmTitle", it) }
    fp.bgmArtist?.let { put("bgmArtist", it) }
    fp.bgmSource?.let { put("bgmSource", it) }
    // 轨迹/途中标记/起点终点照片：这些字段以前只存在文件备份里，
    // Room 缓存漏掉后重启就只剩一条普通足迹，必须完整落盘。
    if (fp.route.isNotEmpty()) {
        put("route", JSONArray().apply {
            fp.route.forEach { put("${it.lat},${it.lon},${it.time}") }
        })
    }
    if (fp.marks.isNotEmpty()) {
        put("marks", JSONArray().apply {
            fp.marks.forEach { m ->
                put(
                    JSONObject()
                        .put("lat", m.lat)
                        .put("lon", m.lon)
                        .put("note", m.note)
                        .put("time", m.time)
                        .apply { m.photoPath?.let { put("photo", it) } }
                )
            }
        })
    }
    if (fp.distanceKm > 0.0) put("distanceKm", fp.distanceKm)
    if (fp.durationSec > 0L) put("durationSec", fp.durationSec)
    fp.startPhotoPath?.let { put("startPhoto", it) }
    fp.endPhotoPath?.let { put("endPhoto", it) }
    if (fp.midPhotoPaths.isNotEmpty()) {
        put("midPhotos", JSONArray().apply { fp.midPhotoPaths.forEach { put(it) } })
    }
}

internal fun footprintFromJson(o: JSONObject): Footprint = Footprint(
    id = o.optInt("id"),
    city = o.optString("city").takeIf { it.isNotBlank() && it != "[]" }.orEmpty(),
    name = o.optString("name"),
    note = o.optString("note"),
    date = o.optString("date"),
    x = o.optDouble("x", 0.0).toFloat(),
    y = o.optDouble("y", 0.0).toFloat(),
    special = o.optBoolean("special"),
    specialReason = o.optString("specialReason").ifBlank { null },
    seed = o.optInt("seed"),
    routeId = if (o.has("routeId") && !o.isNull("routeId")) o.optInt("routeId") else null,
    photoPaths = strListOf(o, "photos"),
    dateMillis = if (o.has("dateMillis") && !o.isNull("dateMillis")) o.optLong("dateMillis") else null,
    lat = if (o.has("lat") && !o.isNull("lat")) o.optDouble("lat", 0.0).takeIf { it != 0.0 } else null,
    lon = if (o.has("lon") && !o.isNull("lon")) o.optDouble("lon", 0.0).takeIf { it != 0.0 } else null,
    route = parseFootprintRoute(o.optJSONArray("route")),
    marks = parseFootprintMarks(o.optJSONArray("marks")),
    distanceKm = o.optDouble("distanceKm", 0.0),
    durationSec = o.optLong("durationSec", 0L),
    videoPath = o.optString("videoPath").ifBlank { null },
    videoPaths = strListOf(o, "videoPaths").ifEmpty {
        listOfNotNull(o.optString("videoPath").ifBlank { null })
    },
    videoIsLive = o.optBoolean("videoIsLive", false),
    videoBytes = o.optLong("videoBytes", 0L),
    bgmPath = o.optString("bgmPath").ifBlank { null },
    bgmTitle = o.optString("bgmTitle").ifBlank { null },
    bgmArtist = o.optString("bgmArtist").ifBlank { null },
    bgmSource = o.optString("bgmSource").ifBlank { null },
    startPhotoPath = o.optString("startPhoto").ifBlank { null },
    endPhotoPath = o.optString("endPhoto").ifBlank { null },
    midPhotoPaths = strListOf(o, "midPhotos")
)

/** 资源 id → 资源名，用于持久化封面；取不到名字时返回 null（只画渐变） */
private fun coverNameOf(context: Context, id: Int?): String? =
    id?.let { runCatching { context.resources.getResourceEntryName(it) }.getOrNull() }

/** 资源名 → 资源 id；找不到（换主题/删资源/旧数据）返回 null，由调用方回退渐变 */
private fun coverIdOf(context: Context, name: String?): Int? =
    name?.takeIf { it.isNotBlank() }?.let {
        runCatching { context.resources.getIdentifier(it, "drawable", context.packageName) }
            .getOrNull()?.takeIf { id -> id != 0 }
    }

/**
 * 攻略/评论/足迹/行程 的 Room 离线缓存门面。
 * 攻略/评论：网络拉取成功后写入，云端不可达时读出兜底（返回 null 表示无缓存）。
 * 足迹/行程：本地权威存储——每次变更全量覆写，启动时读出为内存初始值。
 */
object OfflineCache {

    suspend fun saveGuides(context: Context, guides: List<Guide>) {
        runCatching {
            val dao = AppDatabase.get(context).cacheDao()
            val now = System.currentTimeMillis()
            dao.clearGuides()
            dao.upsertGuides(guides.map {
                GuideCache(it.id, guideToJson(it, coverNameOf(context, it.coverResId)).toString(), now)
            })
        }
    }

    /** 空表/读失败返回 null，由调用方保留现有数据 */
    suspend fun loadGuides(context: Context): List<Guide>? {
        val rows = runCatching { AppDatabase.get(context).cacheDao().getAllGuides() }.getOrNull()
        if (rows.isNullOrEmpty()) return null
        return rows.mapNotNull { row ->
            runCatching {
                val o = JSONObject(row.json)
                guideFromJson(o, coverIdOf(context, o.optString("coverName")))
            }.getOrNull()
        }.takeIf { it.isNotEmpty() }
    }

    suspend fun saveComments(context: Context, guideId: Int, comments: List<Comment>) {
        runCatching {
            val arr = JSONArray().apply { comments.forEach { put(commentToJson(it)) } }
            AppDatabase.get(context).cacheDao().upsertComments(
                CommentCache(guideId, arr.toString(), System.currentTimeMillis())
            )
        }
    }

    suspend fun loadComments(context: Context, guideId: Int): List<Comment>? {
        val row = runCatching { AppDatabase.get(context).cacheDao().getComments(guideId) }.getOrNull()
            ?: return null
        return runCatching {
            val arr = JSONArray(row.json)
            (0 until arr.length()).map { commentFromJson(arr.getJSONObject(it)) }
        }.getOrNull()
    }

    // ---------- 足迹 / 行程：本地权威存储 ----------

    /** 全量覆写本地足迹（启动加载 / 增删改后落盘） */
    suspend fun saveFootprints(context: Context, list: List<Footprint>) {
        runCatching {
            val dao = AppDatabase.get(context).cacheDao()
            val now = System.currentTimeMillis()
            dao.clearFootprints()
            dao.upsertFootprints(list.map { FootprintCache(it.id, footprintToJson(it).toString(), now) })
        }.onFailure {
            runCatching {
                java.io.File(context.filesDir, "restore_debug.log")
                    .appendText("${System.currentTimeMillis()} OfflineCache.saveFootprints size=${list.size} failed: ${it.stackTraceToString()}\n")
            }
            android.util.Log.e("OfflineCache", "saveFootprints failed size=${list.size}", it)
        }
    }

    /** 空表/读失败返回 null，调用方决定回退（首次安装/无本地数据 → 空列表） */
    suspend fun loadFootprints(context: Context): List<Footprint>? {
        val rows = runCatching { AppDatabase.get(context).cacheDao().getAllFootprints() }.getOrNull()
        if (rows.isNullOrEmpty()) return null
        return rows.mapNotNull { runCatching { footprintFromJson(JSONObject(it.json)) }.getOrNull() }
            .takeIf { it.isNotEmpty() }
    }

    /** 全量覆写本地行程 */
    suspend fun saveRoutes(context: Context, list: List<Route>) {
        runCatching {
            val dao = AppDatabase.get(context).cacheDao()
            val now = System.currentTimeMillis()
            dao.clearRoutes()
            dao.upsertRoutes(list.map {
                RouteCache(it.id, routeToJson(it, coverNameOf(context, it.coverResId)).toString(), now)
            })
        }
    }

    suspend fun loadRoutes(context: Context): List<Route>? {
        val rows = runCatching { AppDatabase.get(context).cacheDao().getAllRoutes() }.getOrNull()
        if (rows.isNullOrEmpty()) return null
        return rows.mapNotNull { row ->
            runCatching {
                val o = JSONObject(row.json)
                routeFromJson(o, coverIdOf(context, o.optString("coverName")))
            }.getOrNull()
        }.takeIf { it.isNotEmpty() }
    }
}
