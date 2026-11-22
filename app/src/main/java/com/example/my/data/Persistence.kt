package com.example.my.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.my.model.Footprint
import com.example.my.model.RouteMark
import com.example.my.model.RoutePoint
import com.example.my.model.TripPlan
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地持久化层（Jetpack DataStore Preferences）。
 * 持久化内容：用户设置、点赞/收藏集合、路线公开状态、用户新增足迹。
 */
private val Context.dataStore by preferencesDataStore(name = "myzuji_prefs")

private object K {
    val NICKNAME = stringPreferencesKey("nickname")
    val SIGNATURE = stringPreferencesKey("signature")
    val DEFAULT_PUBLIC = booleanPreferencesKey("default_public")
    val NOTIF_ENABLED = booleanPreferencesKey("notif_enabled")
    val SHARE_ENABLED = booleanPreferencesKey("share_enabled")
    /** 液态玻璃效果开关（默认开）。只在关键元素上生效，见 LiquidGlass.kt */
    val LIQUID_GLASS = booleanPreferencesKey("liquid_glass")
    val LIKED = stringPreferencesKey("liked_guides")
    val FAVORED = stringPreferencesKey("favored_guides")
    val ROUTE_PUBLIC = stringPreferencesKey("route_public")
    val FOOTPRINTS = stringPreferencesKey("user_footprints")
    val AI_CHATS = stringPreferencesKey("ai_chats")
    val TOKEN = stringPreferencesKey("backend_token")
    val ACCOUNTS = stringPreferencesKey("saved_accounts")
    /** 用户主动选择离线使用：下次冷启动不再卡在登录页 */
    val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
    /** 最近一次登录成功后的云端用户 id：离线时继续用同一个本机目录，照片/隐私空间不串账号 */
    val LAST_UID = intPreferencesKey("last_uid")
    val AI_ENDPOINTS = stringPreferencesKey("ai_endpoints")
    val AI_SELECTED = longPreferencesKey("ai_endpoint_selected")
    val AI_USAGE = stringPreferencesKey("ai_usage")
    val AI_MEMORY = stringPreferencesKey("ai_memory")
    /** 结构化长期记忆（v2）：带分类/命中次数/时间，见 AiMemory.kt */
    val AI_MEMORY_V2 = stringPreferencesKey("ai_memory_v2")
    val AMAP_KEY = stringPreferencesKey("amap_key")
    val TRIP_PLANS = stringPreferencesKey("trip_plans")
    val COMMON_COMPANIONS = stringPreferencesKey("common_companions")
    val PACKING_TEMPLATE = stringPreferencesKey("packing_template")
    /** 本地头像文件绝对路径（纯本地模式：头像不再上传服务器） */
    val AVATAR_PATH = stringPreferencesKey("avatar_path")
    /** 回收站：软删除的足迹（含删除时间戳），3 天后自动清除 */
    val TRASH = stringPreferencesKey("trashed_footprints")
    val COURSES = stringPreferencesKey("my_courses")
    /** 课表作息设置：一天几节、每节多长、开学日期等 */
    val COURSE_TABLE = stringPreferencesKey("course_table_settings")
}

/** 本地记录的登录过的账号（仅账号+昵称，绝不保存密码），用于切换账号时快速选择 */
data class SavedAccount(val account: String, val nickname: String = "")

object Persistence {

    suspend fun loadAll(context: Context): PersistedData {
        val p = context.dataStore.data.first()
        return runCatching {
            PersistedData(
                nickname = p[K.NICKNAME],
                signature = p[K.SIGNATURE],
                defaultPublic = p[K.DEFAULT_PUBLIC],
                notifEnabled = p[K.NOTIF_ENABLED],
                shareEnabled = p[K.SHARE_ENABLED],
                liquidGlass = p[K.LIQUID_GLASS],
                likedIds = p[K.LIKED]?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet(),
                favoredIds = p[K.FAVORED]?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet(),
                routePublic = parseRoutePublic(p[K.ROUTE_PUBLIC]),
                extraFootprints = parseFootprints(p[K.FOOTPRINTS]),
                backendToken = p[K.TOKEN],
                savedAccounts = parseAccounts(p[K.ACCOUNTS]),
                offlineMode = p[K.OFFLINE_MODE] ?: false,
                lastUserId = p[K.LAST_UID] ?: 0,
                aiEndpoints = AiPresets.decode(p[K.AI_ENDPOINTS]),
                aiEndpointId = p[K.AI_SELECTED] ?: 0L,
                aiUsage = AiUsage.decode(p[K.AI_USAGE]),
                amapKey = p[K.AMAP_KEY].orEmpty(),
                avatarPath = p[K.AVATAR_PATH]
            )
        }.getOrElse { PersistedData() }
    }

    /** 保存本地头像路径（空串表示清除头像） */
    suspend fun saveAvatarPath(context: Context, path: String) {
        context.dataStore.edit { p ->
            if (path.isBlank()) p.remove(K.AVATAR_PATH) else p[K.AVATAR_PATH] = path
        }
    }

    /** 保存用户自填的高德 Web 服务 Key（只存在本机 DataStore，不上传） */
    suspend fun saveAmapKey(context: Context, key: String) {
        context.dataStore.edit { p -> p[K.AMAP_KEY] = key }
    }


    /** 保存用户配置的模型端点（含 Key，只存在本机 DataStore，不上传） */
    suspend fun saveAiEndpoints(context: Context, list: List<AiEndpoint>, selectedId: Long) {
        context.dataStore.edit { p ->
            p[K.AI_ENDPOINTS] = AiPresets.encode(list)
            p[K.AI_SELECTED] = selectedId
        }
    }

    suspend fun saveAiUsage(context: Context, usage: AiUsage) {
        context.dataStore.edit { p -> p[K.AI_USAGE] = AiUsage.encode(usage) }
    }

    /** 保存/更新登录过的账号记录（同账号覆盖，最多保留 5 个，最新在前） */
    suspend fun saveAccount(context: Context, account: SavedAccount) {
        context.dataStore.edit { p ->
            val current = parseAccounts(p[K.ACCOUNTS]).filter { it.account != account.account }
            p[K.ACCOUNTS] = encodeAccounts(listOf(account) + current.take(4))
        }
    }

    /** 移除某个历史账号（切换/退出时用户主动删除） */
    suspend fun removeAccount(context: Context, identifier: String) {
        context.dataStore.edit { p ->
            p[K.ACCOUNTS] = encodeAccounts(parseAccounts(p[K.ACCOUNTS]).filter { it.account != identifier })
        }
    }

    suspend fun saveToken(context: Context, token: String) {
        context.dataStore.edit { p -> p[K.TOKEN] = token }
    }

    /** 记录离线模式开关；退出登录/重新连上服务器时清掉 */
    suspend fun saveOfflineMode(context: Context, offline: Boolean) {
        context.dataStore.edit { p -> p[K.OFFLINE_MODE] = offline }
    }

    suspend fun saveLastUserId(context: Context, uid: Int) {
        context.dataStore.edit { p -> p[K.LAST_UID] = uid.coerceAtLeast(0) }
    }

    suspend fun saveSettings(context: Context, nickname: String?, signature: String?, defaultPublic: Boolean?, notifEnabled: Boolean?, shareEnabled: Boolean?, liquidGlass: Boolean? = null) {
        context.dataStore.edit { p ->
            nickname?.let { p[K.NICKNAME] = it }
            signature?.let { p[K.SIGNATURE] = it }
            defaultPublic?.let { p[K.DEFAULT_PUBLIC] = it }
            notifEnabled?.let { p[K.NOTIF_ENABLED] = it }
            shareEnabled?.let { p[K.SHARE_ENABLED] = it }
            liquidGlass?.let { p[K.LIQUID_GLASS] = it }
        }
    }

    suspend fun saveGuideState(context: Context, likedIds: Set<Int>, favoredIds: Set<Int>) {
        context.dataStore.edit { p ->
            p[K.LIKED] = likedIds.joinToString(",")
            p[K.FAVORED] = favoredIds.joinToString(",")
        }
    }

    suspend fun saveRoutePublic(context: Context, map: Map<Int, Boolean>) {
        context.dataStore.edit { p ->
            p[K.ROUTE_PUBLIC] = map.entries.joinToString(",") { (k, v) -> "$k:$v" }
        }
    }

    /** 追加一条本机新增的足迹 */
    suspend fun saveExtraFootprint(context: Context, fp: Footprint) {
        context.dataStore.edit { p ->
            val current = parseFootprints(p[K.FOOTPRINTS])
            val updated = current + fp
            p[K.FOOTPRINTS] = encodeFootprints(updated)
        }
    }

    /** 全量覆写本机足迹（批量删除后用它落盘） */
    suspend fun saveFootprints(context: Context, list: List<Footprint>) {
        context.dataStore.edit { p -> p[K.FOOTPRINTS] = encodeFootprints(list) }
    }

    /** 回收站条目：足迹本体 + 删除时间戳 */
    data class TrashedEntry(val fp: Footprint, val trashedAt: Long)

    /** 读取回收站（按删除时间倒序，新删的在前） */
    suspend fun loadTrash(context: Context): List<TrashedEntry> {
        val raw = context.dataStore.data.first()[K.TRASH] ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.optJSONObject(i) ?: return@runCatching null
                val fpObj = o.optJSONObject("fp") ?: return@runCatching null
                // 复用单条足迹的反序列化：包成数组再解析
                val fp = parseFootprints(JSONArray().put(fpObj).toString()).firstOrNull()
                    ?: return@runCatching null
                TrashedEntry(fp, o.optLong("trashedAt", 0L))
            }.getOrNull()
        }.sortedByDescending { it.trashedAt }
    }

    /** 全量覆写回收站 */
    suspend fun saveTrash(context: Context, list: List<TrashedEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            // encodeFootprints 返回 [{...}]，取第一个对象塞进回收站条目
            val fpJson = runCatching { JSONArray(encodeFootprints(listOf(e.fp))).optJSONObject(0) }.getOrNull()
                ?: return@forEach
            arr.put(
                JSONObject()
                    .put("fp", fpJson)
                    .put("trashedAt", e.trashedAt)
            )
        }
        context.dataStore.edit { p -> p[K.TRASH] = arr.toString() }
    }

    // ---------- 出行计划（DataStore JSON，重启不丢；提醒由 WorkManager 按 id 调度） ----------

    suspend fun loadTripPlans(context: Context): List<TripPlan> {
        val raw = context.dataStore.data.first()[K.TRIP_PLANS]
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        // 逐条容错：单条损坏只丢弃该条
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { tripPlanFromJson(it) }
        }
    }

    /** 单条出行计划反序列化；云端恢复与本地读取共用同一套字段容错 */
    fun tripPlanFromJson(o: JSONObject): TripPlan? {
        if (!o.has("id") || !o.has("name")) return null
        return runCatching {
            TripPlan(
                id = o.optLong("id"),
                name = o.optString("name", ""),
                destination = o.optString("destination", ""),
                startDate = o.optString("startDate", ""),
                days = o.optInt("days", 0),
                note = o.optString("note", ""),
                createdAt = o.optLong("createdAt", 0L),
                archived = o.optBoolean("archived", false),
                dailyPlan = o.optJSONArray("dailyPlan")?.let { a ->
                    (0 until a.length()).map { a.optString(it) }
                }.orEmpty(),
                packedItems = o.optJSONArray("packedItems")?.let { a ->
                    (0 until a.length()).map { a.optString(it) }
                }.orEmpty(),
                customPacking = o.optJSONArray("customPacking")?.let { a ->
                    (0 until a.length()).map { a.optString(it) }
                }.orEmpty(),
                autoArchive = o.optBoolean("autoArchive", true),
                manualOrder = o.optInt("manualOrder", -1),
                companions = o.optJSONArray("companions")?.let { a ->
                    (0 until a.length()).map { a.optString(it) }
                }.orEmpty(),
                reminderEnabled = o.optBoolean("reminderEnabled", true),
                reminderOffsetDays = o.optInt("reminderOffsetDays", 1),
                reminderHour = o.optInt("reminderHour", 9),
                reminderMinute = o.optInt("reminderMinute", 0),
                reminderVibrate = o.optBoolean("reminderVibrate", true),
                reminderSound = o.optBoolean("reminderSound", true)
            )
        }.getOrNull()
    }

    suspend fun saveTripPlans(context: Context, list: List<TripPlan>) {
        context.dataStore.edit { p ->
            val arr = JSONArray()
            list.forEach { t ->
                arr.put(
                    JSONObject()
                        .put("id", t.id)
                        .put("name", t.name)
                        .put("destination", t.destination)
                        .put("startDate", t.startDate)
                        .put("days", t.days)
                        .put("note", t.note)
                        .put("createdAt", t.createdAt)
                        .put("archived", t.archived)
                        .put("dailyPlan", JSONArray(t.dailyPlan))
                        .put("packedItems", JSONArray(t.packedItems))
                        .put("customPacking", JSONArray(t.customPacking))
                        .put("autoArchive", t.autoArchive)
                        .put("manualOrder", t.manualOrder)
                        .put("companions", JSONArray(t.companions))
                        .put("reminderEnabled", t.reminderEnabled)
                        .put("reminderOffsetDays", t.reminderOffsetDays)
                        .put("reminderHour", t.reminderHour)
                        .put("reminderMinute", t.reminderMinute)
                        .put("reminderVibrate", t.reminderVibrate)
                        .put("reminderSound", t.reminderSound)
                )
            }
            p[K.TRIP_PLANS] = arr.toString()
        }
    }

    // ---------- 常用同行人 / 清单模板（新建行程时复用） ----------

    suspend fun loadCommonCompanions(context: Context): List<String> {
        val raw = context.dataStore.data.first()[K.COMMON_COMPANIONS]
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
    }

    suspend fun saveCommonCompanions(context: Context, names: List<String>) {
        context.dataStore.edit { p -> p[K.COMMON_COMPANIONS] = JSONArray(names).toString() }
    }

    suspend fun loadPackingTemplate(context: Context): List<String> {
        val raw = context.dataStore.data.first()[K.PACKING_TEMPLATE]
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
    }

    suspend fun savePackingTemplate(context: Context, items: List<String>) {
        context.dataStore.edit { p -> p[K.PACKING_TEMPLATE] = JSONArray(items).toString() }
    }

    // ---------- AI 对话 ----------

    suspend fun loadAiChats(context: Context): List<AiChat> {
        val raw = context.dataStore.data.first()[K.AI_CHATS]
        return parseAiChats(raw)
    }

    suspend fun saveAiChats(context: Context, chats: List<AiChat>) {
        context.dataStore.edit { p -> p[K.AI_CHATS] = encodeAiChats(chats) }
    }

    // ---------- AI 长期记忆（跨会话累积的用户偏好事实） ----------

    suspend fun loadAiMemory(context: Context): List<String> {
        val raw = context.dataStore.data.first()[K.AI_MEMORY]
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        // 逐条容错：空字符串直接丢弃
        return (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
    }

    /**
     * 结构化记忆：优先读 v2，没有就把 v1 的纯文本迁移过来（老用户记忆不丢）。
     * 迁移之后不再写 v1，只保留 v2 一处权威来源。
     */
    suspend fun loadAiMemories(context: Context): List<com.example.my.data.MemItem> {
        val p = context.dataStore.data.first()
        val v2 = p[K.AI_MEMORY_V2]
        if (!v2.isNullOrBlank()) return com.example.my.data.AiMemory.fromJson(v2)
        val legacy = loadAiMemory(context)
        return if (legacy.isEmpty()) emptyList() else com.example.my.data.AiMemory.fromLegacy(legacy)
    }

    suspend fun saveAiMemories(context: Context, items: List<com.example.my.data.MemItem>) {
        context.dataStore.edit { p ->
            p[K.AI_MEMORY_V2] = com.example.my.data.AiMemory.toJson(items)
        }
    }

    // ---------- 序列化 ----------

    private fun parseFootprints(raw: String?): List<Footprint> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        // 逐条容错：单条记录损坏只丢弃该条，不影响其余记录
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.optJSONObject(i) ?: return@runCatching null
                // id 是唯一定位字段，缺失则无法定位，直接丢弃该条
                if (!o.has("id") || o.isNull("id")) return@runCatching null
                Footprint(
                    id = o.optInt("id"),
                    city = o.optString("city", ""),
                    name = o.optString("name", ""),
                    note = o.optString("note", ""),
                    date = o.optString("date", ""),
                    x = o.optDouble("x", 0.0).toFloat(),
                    y = o.optDouble("y", 0.0).toFloat(),
                    special = o.optBoolean("special", false),
                    specialReason = o.optString("specialReason").ifBlank { null },
                    seed = o.optInt("seed", 0),
                    routeId = null,
                    photoPaths = parsePaths(o.optJSONArray("photos")),
                    // 区分「字段缺失」与「值存在」：optLong 对非数字也返回默认值，直接用会静默变成 0
                    dateMillis = if (o.has("dateMillis") && !o.isNull("dateMillis")) o.optLong("dateMillis") else null,
                    // 只有经纬度都在且非 0 才算有效坐标；旧数据没有这两个字段，保持 null
                    lat = o.takeIf { it.has("lat") && !it.isNull("lat") }
                        ?.optDouble("lat", 0.0)?.takeIf { it != 0.0 },
                    lon = o.takeIf { it.has("lon") && !it.isNull("lon") }
                        ?.optDouble("lon", 0.0)?.takeIf { it != 0.0 },
                    route = parseRoute(o.optJSONArray("route")),
                    marks = parseMarks(o.optJSONArray("marks")),
                    distanceKm = o.optDouble("distanceKm", 0.0),
                    durationSec = o.optLong("durationSec", 0L),
                    videoPath = o.optString("videoPath").ifBlank { null },
                    videoPaths = parsePaths(o.optJSONArray("videoPaths")).ifEmpty {
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
                    midPhotoPaths = parsePaths(o.optJSONArray("midPhotos"))
                )
            }.getOrNull()
        }
    }

    private fun encodeFootprints(list: List<Footprint>): String {
        val arr = JSONArray()
        list.forEach { fp ->
            arr.put(
                JSONObject()
                    .put("id", fp.id)
                    .put("city", fp.city)
                    .put("name", fp.name)
                    .put("note", fp.note)
                    .put("date", fp.date)
                    .put("x", fp.x.toDouble())
                    .put("y", fp.y.toDouble())
                    .put("special", fp.special)
                    .put("specialReason", fp.specialReason ?: "")
                    .put("seed", fp.seed)
                    .put("photos", JSONArray().apply { fp.photoPaths.forEach { put(it) } })
                    // 与 parseFootprints 的读取对称：0 值不写入，避免写进去再读回被当成缺失
                    .apply { fp.dateMillis?.takeIf { it != 0L }?.let { put("dateMillis", it) } }
                    // 有精确经纬度就带上，旧版本读到多余字段会忽略，不影响兼容
                    .apply {
                        if (fp.lat != null && fp.lon != null) {
                            put("lat", fp.lat)
                            put("lon", fp.lon)
                        }
                    }
                    // 路线型足迹：轨迹点多，用 "lat,lon,time" 紧凑串，能用数组就不塞对象
                    .apply {
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
                    }
                    // 视频 / 实况 / 配乐：没有就一个字段都不写，旧版本读到多余字段也会忽略
                    .apply {
                        fp.videoPath?.let { put("videoPath", it) }
                        if (fp.videoPaths.isNotEmpty()) {
                            put("videoPaths", org.json.JSONArray().apply { fp.videoPaths.forEach { put(it) } })
                        }
                        if (fp.videoIsLive) put("videoIsLive", true)
                        if (fp.videoBytes > 0L) put("videoBytes", fp.videoBytes)
                        fp.bgmPath?.let { put("bgmPath", it) }
                        fp.bgmTitle?.let { put("bgmTitle", it) }
                        fp.bgmArtist?.let { put("bgmArtist", it) }
                        fp.bgmSource?.let { put("bgmSource", it) }
                        fp.startPhotoPath?.let { put("startPhoto", it) }
                        fp.endPhotoPath?.let { put("endPhoto", it) }
                        if (fp.midPhotoPaths.isNotEmpty()) {
                            put("midPhotos", JSONArray().apply { fp.midPhotoPaths.forEach { put(it) } })
                        }
                    }
            )
        }
        return arr.toString()
    }

    /** 路线点 "lat,lon,time" → RoutePoint；格式不对的点丢弃，而不是整条路线丢掉 */
    private fun parseRoute(arr: JSONArray?): List<RoutePoint> {
        arr ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val parts = arr.optString(i).split(',')
            if (parts.size < 2) return@mapNotNull null
            val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val lon = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            val t = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            RoutePoint(lat, lon, t)
        }
    }

    /** 途中标记点：字段缺失时给安全空值，不因为一个坏标记丢掉整条足迹 */
    private fun parseMarks(arr: JSONArray?): List<RouteMark> {
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

    private fun parsePaths(arr: org.json.JSONArray?): List<String> {
        arr ?: return emptyList()
        return runCatching {
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
        }.getOrDefault(emptyList())
    }

    private fun parseRoutePublic(raw: String?): Map<Int, Boolean> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            raw.split(",").mapNotNull { s ->
                val parts = s.split(":")
                if (parts.size == 2) parts[0].toIntOrNull()?.let { it to (parts[1] == "true") } else null
            }.toMap()
        }.getOrElse { emptyMap() }
    }

    private fun parseAccounts(raw: String?): List<SavedAccount> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val account = o.optString("account")
                if (account.isBlank()) null
                else SavedAccount(account, o.optString("nickname", ""))
            }
        }.getOrElse { emptyList() }
    }

    private fun encodeAccounts(list: List<SavedAccount>): String {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().put("account", a.account).put("nickname", a.nickname))
        }
        return arr.toString()
    }

    private fun parseAiChats(raw: String?): List<AiChat> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        // 逐条容错：单个会话损坏只丢弃该会话
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.optJSONObject(i) ?: return@runCatching null
                val msgs = o.optJSONArray("messages") ?: JSONArray()
                AiChat(
                    id = o.optLong("id"),
                    title = o.optString("title", "新对话"),
                    updatedAt = o.optLong("updatedAt", 0L),
                    // 逐条容错：单条消息损坏只丢弃该消息，不丢整个会话
                    messages = (0 until msgs.length()).mapNotNull { j ->
                        runCatching {
                            val m = msgs.optJSONObject(j) ?: return@runCatching null
                            AiChatMessage(
                                fromUser = m.optBoolean("fromUser", false),
                                text = m.optString("text", ""),
                                guideId = if (m.has("guideId")) m.optInt("guideId") else null,
                                imagePath = m.optString("imagePath").takeIf { it.isNotBlank() },
                                thinking = m.optString("thinking").takeIf { it.isNotBlank() },
                                 sources = parseWebHits(m.optJSONArray("sources")),
                                 videoUrl = m.optString("videoUrl").takeIf { it.isNotBlank() }
                            )
                        }.getOrNull()
                    }
                )
            }.getOrNull()
        }
    }

    /** 参考来源逐条容错解析：单条坏掉就跳过，不影响整段历史 */
    private fun parseWebHits(arr: JSONArray?): List<com.example.my.data.WebHit> {
        if (arr == null || arr.length() == 0) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.optJSONObject(i) ?: return@runCatching null
                val url = o.optString("url")
                if (url.isBlank()) return@runCatching null
                com.example.my.data.WebHit(
                    title = o.optString("title"),
                    snippet = o.optString("snippet"),
                    url = url,
                    site = o.optString("site"),
                    tier = o.optInt("tier", 0),
                    score = o.optDouble("score", 0.0)
                )
            }.getOrNull()
        }
    }

    private fun encodeAiChats(chats: List<AiChat>): String {        val arr = JSONArray()
        chats.forEach { c ->
            val msgs = JSONArray()
            c.messages.forEach { m ->
                msgs.put(
                    JSONObject()
                        .put("fromUser", m.fromUser)
                        .put("text", m.text)
                        .apply { m.guideId?.let { put("guideId", it) } }
                        .apply { m.imagePath?.let { put("imagePath", it) } }
                        .apply { m.videoUrl?.let { put("videoUrl", it) } }
                        .apply { m.thinking?.let { put("thinking", it) } }
                        .apply {
                            if (m.sources.isNotEmpty()) {
                                val arr = JSONArray()
                                m.sources.forEach { h ->
                                    arr.put(
                                        JSONObject()
                                            .put("title", h.title)
                                            .put("snippet", h.snippet)
                                            .put("url", h.url)
                                            .put("site", h.site)
                                            .put("tier", h.tier)
                                            .put("score", h.score)
                                    )
                                }
                                put("sources", arr)
                            }
                        }
                )
            }
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("title", c.title)
                    .put("updatedAt", c.updatedAt)
                    .put("messages", msgs)
            )
        }
        return arr.toString()
    }

    // ---------- 课表（DataStore JSON） ----------

    suspend fun loadCourses(context: Context): List<com.example.my.model.Course> {
        val raw = context.dataStore.data.first()[K.COURSES] ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.optJSONObject(i) ?: return@runCatching null
                com.example.my.model.Course(
                    id = o.optLong("id"),
                    name = o.optString("name", ""),
                    teacher = o.optString("teacher", ""),
                    location = o.optString("location", ""),
                    dayOfWeek = o.optInt("dayOfWeek", 1),
                    startPeriod = o.optInt("startPeriod", 1),
                    endPeriod = o.optInt("endPeriod", 1),
                    colorIndex = o.optInt("colorIndex", 0),
                    // 旧版本没有周次字段：默认「每周」，升级后原有课程照常显示
                    weekMode = o.optInt("weekMode", com.example.my.model.Course.WEEK_EVERY),
                    weekStart = o.optInt("weekStart", 1),
                    weekEnd = o.optInt("weekEnd", 20)
                )
            }.getOrNull()
        }
    }

    suspend fun saveCourses(context: Context, list: List<com.example.my.model.Course>) {
        context.dataStore.edit { p ->
            val arr = JSONArray()
            list.forEach { c ->
                arr.put(
                    JSONObject()
                        .put("id", c.id)
                        .put("name", c.name)
                        .put("teacher", c.teacher)
                        .put("location", c.location)
                        .put("dayOfWeek", c.dayOfWeek)
                        .put("startPeriod", c.startPeriod)
                        .put("endPeriod", c.endPeriod)
                        .put("colorIndex", c.colorIndex)
                        .put("weekMode", c.weekMode)
                        .put("weekStart", c.weekStart)
                        .put("weekEnd", c.weekEnd)
                )
            }
            p[K.COURSES] = arr.toString()
        }
    }

    // ---------- 课表作息设置 ----------

    suspend fun loadCourseTable(context: Context): com.example.my.model.CourseTableSettings {
        val raw = context.dataStore.data.first()[K.COURSE_TABLE] ?: return com.example.my.model.CourseTableSettings()
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return com.example.my.model.CourseTableSettings()
        val def = com.example.my.model.CourseTableSettings()
        return com.example.my.model.CourseTableSettings(
            periodsPerDay = o.optInt("periodsPerDay", def.periodsPerDay),
            lessonMinutes = o.optInt("lessonMinutes", def.lessonMinutes),
            breakMinutes = o.optInt("breakMinutes", def.breakMinutes),
            startMinutes = o.optInt("startMinutes", def.startMinutes),
            daysPerWeek = o.optInt("daysPerWeek", def.daysPerWeek),
            lunchAfter = o.optInt("lunchAfter", def.lunchAfter),
            lunchMinutes = o.optInt("lunchMinutes", def.lunchMinutes),
            eveningAfter = o.optInt("eveningAfter", def.eveningAfter),
            eveningMinutes = o.optInt("eveningMinutes", def.eveningMinutes),
            semesterMonday = o.optString("semesterMonday", def.semesterMonday),
            totalWeeks = o.optInt("totalWeeks", def.totalWeeks),
            periodStartMinutes = o.optJSONArray("periodStartMinutes")?.let { a ->
                buildList { for (i in 0 until a.length()) add(a.optInt(i)) }
            } ?: emptyList(),
            periodEndMinutes = o.optJSONArray("periodEndMinutes")?.let { a ->
                buildList { for (i in 0 until a.length()) add(a.optInt(i)) }
            } ?: emptyList()
        )
    }

    suspend fun saveCourseTable(context: Context, s: com.example.my.model.CourseTableSettings) {
        context.dataStore.edit { p ->
            val o = JSONObject()
                .put("periodsPerDay", s.periodsPerDay)
                .put("lessonMinutes", s.lessonMinutes)
                .put("breakMinutes", s.breakMinutes)
                .put("startMinutes", s.startMinutes)
                .put("daysPerWeek", s.daysPerWeek)
                .put("lunchAfter", s.lunchAfter)
                .put("lunchMinutes", s.lunchMinutes)
                .put("eveningAfter", s.eveningAfter)
                .put("eveningMinutes", s.eveningMinutes)
                .put("semesterMonday", s.semesterMonday)
                .put("totalWeeks", s.totalWeeks)
                .put("periodStartMinutes", JSONArray(s.periodStartMinutes))
                .put("periodEndMinutes", JSONArray(s.periodEndMinutes))
            p[K.COURSE_TABLE] = o.toString()
        }
    }
}

/** AI 对话中的一条消息 */
data class AiChatMessage(
    val fromUser: Boolean,
    val text: String,
    val guideId: Int? = null,
    /** 用户发送的图片在私有目录的绝对路径（用于在对话气泡里回显） */
    val imagePath: String? = null,
    /** 深度思考过程，历史会话里也要能折叠查看 */
    val thinking: String? = null,
    /** 本轮引用的网页来源，历史会话里同样可展开 */
    val sources: List<com.example.my.data.WebHit> = emptyList(),
    /** AI 生成的视频地址；视频文件通常由服务商托管，仅保存可打开地址 */
    val videoUrl: String? = null
)

/** 一段完整对话（本地留存，可在历史里重新进入） */
data class AiChat(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    val messages: List<AiChatMessage>
)

data class PersistedData(
    /** 用户主动进入离线模式；有 token 但服务器不可达时也会置 true */
    val offlineMode: Boolean = false,
    /** 最近一次在线登录的云端用户 id；离线模式沿用它保持本机目录不串账号 */
    val lastUserId: Int = 0,
    val nickname: String? = null,
    val signature: String? = null,
    val defaultPublic: Boolean? = null,
    val notifEnabled: Boolean? = null,
    val shareEnabled: Boolean? = null,
    /** 液态玻璃效果开关；null 表示旧版本没存过，按默认开处理 */
    val liquidGlass: Boolean? = null,
    val likedIds: Set<Int> = emptySet(),
    val favoredIds: Set<Int> = emptySet(),
    val routePublic: Map<Int, Boolean> = emptyMap(),
    val extraFootprints: List<Footprint> = emptyList(),
    val backendToken: String? = null,
    val savedAccounts: List<SavedAccount> = emptyList(),
    val aiEndpoints: List<AiEndpoint> = emptyList(),
    val aiEndpointId: Long = 0L,
    val aiUsage: AiUsage = AiUsage(),
    /** 用户自填的高德 Web 服务 Key；为空表示使用内置 Key */
    val amapKey: String = "",
    /** 本地头像文件绝对路径；null 表示未设置（用昵称首字兜底） */
    val avatarPath: String? = null
)
