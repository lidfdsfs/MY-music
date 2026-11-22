package com.example.my.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.example.my.data.BackendApi
import com.example.my.data.CloudConfig
import com.example.my.data.Persistence
import com.example.my.data.music.MusicPlayer
import com.example.my.data.local.AppDatabase
import com.example.my.data.local.OfflineCache
import com.example.my.data.local.footprintFromJson
import com.example.my.data.local.footprintToJson
import com.example.my.data.local.routeFromJson
import com.example.my.data.local.routeToJson
import com.example.my.data.vault.PrivacyVault
import com.example.my.model.Footprint
import com.example.my.model.Route
import com.example.my.model.TripPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 用户数据上传引擎：把本机权威数据（足迹 / 行程 / 设置 / 照片 / 视频）推到后端 /api/sync。
 *
 * 设计要点：
 * - 本地永远是权威。每次同步都把当前本地数据整批 upsert（updated_at = 当前时间），
 *   服务端按 (user_id, client_id) 幂等覆盖，因此不需要维护复杂的增量 diff。
 * - 先传媒体再传足迹：照片/视频先 POST /api/sync/media 换回 URL，再写进足迹 payload，
 *   这样服务端存的是一条「媒体已经是 URL」的完整足迹，而不是一堆本机绝对路径。
 * - 媒体上传结果按「本机文件路径 → 服务端 URL」缓存在 SharedPreferences，
 *   同一张照片只上传一次（服务端还会按 sha256 二次去重）。
 * - 删除用 deleted_client_ids 显式告知服务端（本地软删除进回收站，彻底删除时才入队）。
 * - 只在 [CloudConfig.ENABLED] = true 且已登录时工作；关闭开关 / 未登录时是彻底的空操作。
 */
object CloudSync {

    /** 一次同步的结果，设置页与日志都用它 */
    data class SyncResult(
        val ok: Boolean,
        val footprints: Int = 0,
        val routes: Int = 0,
        val media: Int = 0,
        val mediaBytes: Long = 0,
        val deletedFootprints: Int = 0,
        val created: Int = 0,
        val updated: Int = 0,
        val unchanged: Int = 0,
        val archiveOk: Boolean = false,
        val vaultFiles: Int = 0,
        val downloaded: Int = 0,
        val message: String? = null,
        val at: Long = System.currentTimeMillis()
    )

    private const val PREFS = "cloud_sync_state"
    private const val KEY_MEDIA_PREFIX = "media:"
    private const val KEY_LAST_SYNC = "last_sync_at"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_DELETED_FP = "deleted_footprints"
    private const val KEY_DELETED_RT = "deleted_routes"
    private const val KEY_WATERMARK_FP = "watermark_footprints"
    private const val KEY_WATERMARK_RT = "watermark_routes"
    private const val KEY_DELETED_PLANS = "deleted_plans"
    private const val KEY_PLANS_BASELINE = "plans_baseline_ids_v1"

    /** 单次请求最多 200 条（服务端上限 500，留余量） */
    private const val BATCH_LIMIT = 200

    /** 单条 payload 上限约 1MB，超过就先丢照片序列化，避免整批 413 */
    private const val ITEM_PAYLOAD_LIMIT = 900_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var scheduled: Job? = null

    /** 同步结束回调（AppState 用它刷新设置页状态；只在设置了才回调） */
    @Volatile
    var onResult: ((SyncResult) -> Unit)? = null

    val running: Boolean get() = mutex.isLocked

    fun lastSyncAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SYNC, 0L)

    fun lastError(context: Context): String? =
        prefs(context).getString(KEY_LAST_ERROR, null)?.takeIf { it.isNotBlank() }

    /** 换机恢复：拉取服务端全量档案正文并解析为 JSONObject；失败返回 null */
    suspend fun fetchRemoteArchive(): JSONObject? = withContext(Dispatchers.IO) {
        runCatching { BackendApi.fetchArchiveContent()?.let { JSONObject(it) } }.getOrNull()
    }

    /** 读取服务端聚合快照（不含正文），供设置页展示云端版本信息 */
    suspend fun fetchRemoteSnapshot(): JSONObject? = withContext(Dispatchers.IO) {
        runCatching { BackendApi.fetchSnapshot() }.getOrNull()
    }

    /**
     * 有改动时调用：延迟 [delayMs] 合并成一次同步（避免连续编辑触发多次上传）。
     * 未开启开关或未登录时直接返回，不排队。
     */
    fun requestSync(context: Context, delayMs: Long = 2_000L) {
        val app = context.applicationContext
        if (!CloudConfig.isEnabled(app)) return
        if (BackendApi.token == null) return
        synchronized(this) {
            scheduled?.cancel()
            scheduled = scope.launch {
                delay(delayMs)
                syncNow(app)
            }
        }
    }

    /**
     * 立即同步一次（设置页「立即同步」按钮、登录成功后直接调用）。
     * 同一时刻只允许一个同步在跑，后到的会等前一个结束。
     */
    suspend fun syncNow(context: Context): SyncResult = mutex.withLock {
        val app = context.applicationContext
        val result = runCatching { doSync(app) }.getOrElse { e ->
            SyncResult(false, message = e.message ?: "同步失败")
        }
        val p = prefs(app)
        p.edit().apply {
            putLong(KEY_LAST_SYNC, result.at)
            if (result.ok) remove(KEY_LAST_ERROR) else putString(KEY_LAST_ERROR, result.message ?: "同步失败")
        }.apply()
        onResult?.invoke(result)
        result
    }

    /** 记录需要服务端删除的足迹 id（彻底删除时调用，同步成功后会从队列移除） */
    fun markDeleted(context: Context, ids: Set<Int>) {
        if (!CloudConfig.isEnabled(context)) return
        if (ids.isEmpty()) return
        val p = prefs(context)
        val cur = (p.getStringSet(KEY_DELETED_FP, emptySet()) ?: emptySet()).toMutableSet()
        ids.forEach { cur.add(it.toString()) }
        p.edit().putStringSet(KEY_DELETED_FP, cur).apply()
    }

    /** 记录需要服务端删除的行程 id（删除旅程卡时调用） */
    fun markRoutesDeleted(context: Context, ids: Set<Int>) {
        if (!CloudConfig.isEnabled(context)) return
        if (ids.isEmpty()) return
        val p = prefs(context)
        val cur = (p.getStringSet(KEY_DELETED_RT, emptySet()) ?: emptySet()).toMutableSet()
        ids.forEach { cur.add(it.toString()) }
        p.edit().putStringSet(KEY_DELETED_RT, cur).apply()
    }

    /** 记录需要服务端删除的出行计划 id；哪怕还没建立基线也能先记进墓碑队列 */
    fun markPlansDeleted(context: Context, ids: Set<Long>) {
        if (ids.isEmpty()) return
        val p = prefs(context)
        val cur = (p.getStringSet(KEY_DELETED_PLANS, emptySet()) ?: emptySet()).toMutableSet()
        ids.forEach { cur.add(it.toString()) }
        p.edit().putStringSet(KEY_DELETED_PLANS, cur).apply()
    }

    /** 登录回灌完成后，把当前计划 id 设为删除比较基线（不发起请求） */
    suspend fun seedPlanBaseline(context: Context) {
        val ids = runCatching { Persistence.loadTripPlans(context) }
            .getOrDefault(emptyList())
            .map { it.id.toString() }
            .toSet()
        prefs(context).edit().putStringSet(KEY_PLANS_BASELINE, ids).apply()
    }

    /** 换账号时清掉计划基线，防止把上一个账号的计划当成新账号的待删除项 */
    fun resetPlanBaseline(context: Context) {
        prefs(context).edit()
            .remove(KEY_PLANS_BASELINE)
            .remove(KEY_DELETED_PLANS)
            .apply()
    }

    // ---------- 内部实现 ----------

    private suspend fun doSync(app: Context): SyncResult {
        if (!CloudConfig.isEnabled(app)) return SyncResult(false, message = "云端同步未开启")
        if (BackendApi.token == null) return SyncResult(false, message = "未登录，无法同步")

        val p = prefs(app)
        val mediaCount = intArrayOf(0)
        val mediaBytes = longArrayOf(0)

        val footprints = OfflineCache.loadFootprints(app) ?: emptyList()
        val routes = OfflineCache.loadRoutes(app) ?: emptyList()

        // 1) 足迹：媒体先上传，再整批 upsert（删除清单只在第一批里带一次）
        val fpItems = ArrayList<JSONObject>(footprints.size)
        footprints.forEach { fp -> fpItems.add(buildFootprintItem(app, p, fp, mediaCount, mediaBytes)) }

        var deletedDone = 0
        var created = 0
        var updated = 0
        var unchanged = 0
        var fpOk = true
        val fpError = StringBuilder()
        val pendingDeletes = pendingDeletes(p)
        val batches = if (fpItems.isEmpty()) listOf(emptyList()) else fpItems.chunked(BATCH_LIMIT)
        batches.forEachIndexed { index, batch ->
            val deletes = if (index == 0) pendingDeletes else emptyList()
            val res = BackendApi.uploadFootprints(batch, deletes)
            if (res == null) {
                fpOk = false
                if (fpError.isEmpty()) fpError.append("足迹上传失败（服务端不可达或返回错误）")
            } else {
                val c = BackendApi.parseSyncCounts(res)
                deletedDone += c.deleted
                created += c.created
                updated += c.updated
                unchanged += c.unchanged
            }
        }
        if (fpOk && pendingDeletes.isNotEmpty()) {
            p.edit().remove(KEY_DELETED_FP).apply()
        }

        // 2) 行程：结构简单，整批 upsert（删除清单同样只在第一批带一次）
        val rtItems = routes.map { r ->
            JSONObject().apply {
                put("client_id", r.id)
                put("updated_at", System.currentTimeMillis())
                put("payload", routeToJson(r))
            }
        }
        var rtOk = true
        val pendingRouteDeletes = pendingRouteDeletes(p)
        val rtBatches = if (rtItems.isEmpty()) listOf(emptyList()) else rtItems.chunked(BATCH_LIMIT)
        rtBatches.forEachIndexed { index, batch ->
            val deletes = if (index == 0) pendingRouteDeletes else emptyList()
            val rtRes = BackendApi.uploadRoutes(batch, deletes)
            if (rtRes == null) {
                rtOk = false
            } else {
                val c = BackendApi.parseSyncCounts(rtRes)
                deletedDone += c.deleted
                created += c.created
                updated += c.updated
                unchanged += c.unchanged
            }
        }
        if (rtOk && pendingRouteDeletes.isNotEmpty()) {
            p.edit().remove(KEY_DELETED_RT).apply()
        }

        // 3) 设置快照：整机偏好（不含任何密钥/令牌），每次同步都推一份最新的
        val settingsOk = BackendApi.uploadSettings(settingsSnapshot(app), System.currentTimeMillis())

        // 3.1) 出行计划：逐条 upsert + 显式墓碑删除，不再依赖全量档案
        val plansOk = pushPlans(app, p)

        // 3.2) 音乐收藏：只上传歌名+歌手；删除走显式墓碑，避免删了又回来
        val musicOk = MusicFavoritesSync.pushNow(app)

        // 4) 全量档案：音乐收藏 / 评论 / 计划 / 隐私空间索引 / 设置各分区一次落库，
        //    服务端会同时写进用户文件夹的 data/*.json 与对应数据表。
        //    注意：新设备还没完成云端回灌前不上传档案，否则空的本地状态会覆盖云端档案。
        val restoredForUser = BackendApi.remoteUserId?.let { CloudRestore.alreadyRestored(app, it) } ?: false
        val archiveDoc = if (restoredForUser) runCatching { buildArchive(app, fpItems, rtItems) }.getOrNull() else null
        var archiveOk = !restoredForUser
        if (archiveDoc != null) {
            archiveOk = BackendApi.uploadArchive(archiveDoc) != null
        }

        // 5) 隐私空间密文块：端侧加密后的原始字节，服务端只存不解密
        val vaultFiles = runCatching { uploadVaultBlobs(app, p) }.getOrDefault(0)

        // 6) 下行回拉：只补本地缺失的条目（本地权威，已有同 id 不被云端覆盖），并推进水位
        var downloaded = 0
        val mergedFp = pullFootprints(app, p, footprints)
        if (mergedFp.second > 0) {
            OfflineCache.saveFootprints(app, mergedFp.first)
            downloaded += mergedFp.second
        }
        val mergedRt = pullRoutes(app, p, routes)
        if (mergedRt.second > 0) {
            OfflineCache.saveRoutes(app, mergedRt.first)
            downloaded += mergedRt.second
        }
        // 设置快照下行：换机恢复基础偏好，凭证键仍被过滤
        pullSettings(app)

        val ok = fpOk && rtOk && plansOk && musicOk
        val message = when {
            !ok && fpError.isNotEmpty() -> fpError.toString()
            !ok && !fpOk -> "足迹上传失败（服务端不可达或返回错误）"
            !ok && !rtOk -> "行程上传失败（服务端不可达或返回错误）"
            !ok && !plansOk -> "出行计划同步失败（服务端不可达或返回错误）"
            !ok && !musicOk -> "音乐收藏同步失败（服务端不可达或返回错误）"
            !settingsOk -> "足迹与行程已上传，设置未上传成功"
            restoredForUser && !archiveOk -> "足迹与行程已上传，全量档案未上传成功"
            else -> null
        }
        return SyncResult(
            ok = ok,
            footprints = if (fpOk) fpItems.size else 0,
            routes = if (rtOk) rtItems.size else 0,
            media = mediaCount[0],
            mediaBytes = mediaBytes[0],
            deletedFootprints = deletedDone,
            created = created,
            updated = updated,
            unchanged = unchanged,
            archiveOk = archiveOk,
            vaultFiles = vaultFiles,
            downloaded = downloaded,
            message = message
        )
    }

    private suspend fun pushPlans(app: Context, p: SharedPreferences): Boolean {
        val plans = runCatching { Persistence.loadTripPlans(app) }.getOrDefault(emptyList())
        val currentIds = plans.map { it.id.toString() }.toSet()
        val queued = (p.getStringSet(KEY_DELETED_PLANS, emptySet()) ?: emptySet()).toMutableSet()
        if (p.contains(KEY_PLANS_BASELINE)) {
            val previous = p.getStringSet(KEY_PLANS_BASELINE, emptySet()) ?: emptySet()
            queued.addAll(previous - currentIds)
        }
        val deleted = queued.toList()
        if (plans.isEmpty() && deleted.isEmpty()) {
            if (p.contains(KEY_PLANS_BASELINE)) p.edit().putStringSet(KEY_PLANS_BASELINE, currentIds).apply()
            return true
        }

        val now = System.currentTimeMillis()
        val items = JSONArray()
        plans.forEach { plan ->
            items.put(
                JSONObject()
                    .put("client_id", plan.id.toString())
                    .put("updated_at", now)
                    .put("payload", tripPlanToJson(plan).put("updated_at", now))
            )
        }
        val ok = BackendApi.putCollection("plans", items, deleted)
        if (ok) {
            p.edit()
                .putStringSet(KEY_PLANS_BASELINE, currentIds)
                .remove(KEY_DELETED_PLANS)
                .apply()
        }
        return ok
    }

    private suspend fun buildFootprintItem(
        app: Context,
        p: SharedPreferences,
        fp: Footprint,
        mediaCount: IntArray,
        mediaBytes: LongArray
    ): JSONObject {
        val payload = footprintToJson(fp)
        val photoUrls = JSONArray()
        fp.photoPaths.forEach { path ->
            val url = uploadMediaPath(p, path, mediaCount, mediaBytes)
            photoUrls.put(url ?: path)
        }
        payload.put("photos", photoUrls)
        fp.videoPath?.let { path ->
            uploadMediaPath(p, path, mediaCount, mediaBytes)?.let { payload.put("videoPath", it) }
        }
        if (fp.videoPaths.isNotEmpty()) {
            val videoUrls = JSONArray()
            fp.videoPaths.forEach { path ->
                videoUrls.put(uploadMediaPath(p, path, mediaCount, mediaBytes) ?: path)
            }
            payload.put("videoPaths", videoUrls)
        }
        val item = JSONObject().apply {
            put("client_id", fp.id)
            put("updated_at", System.currentTimeMillis())
            put("payload", payload)
        }
        // 极端情况（几十张原图）会超过单条 1MB：退化成不带照片的 payload，先保住文字与坐标
        if (item.toString().length > ITEM_PAYLOAD_LIMIT) {
            payload.put("photos", JSONArray())
            item.put("payload", payload)
        }
        return item
    }

    /** 上传单个媒体文件并返回服务端 URL；已上传过的直接读缓存 */
    private suspend fun uploadMediaPath(
        p: SharedPreferences,
        path: String,
        mediaCount: IntArray,
        mediaBytes: LongArray
    ): String? {
        if (path.isBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val cached = p.getString(KEY_MEDIA_PREFIX + path, null)
        if (!cached.isNullOrBlank()) return cached
        val file = File(path)
        if (!file.isFile || file.length() <= 0L) return null
        val mime = guessMime(file.extension) ?: return null
        val ref = BackendApi.uploadMedia(file, mime) ?: return null
        p.edit().putString(KEY_MEDIA_PREFIX + path, ref.url).apply()
        mediaCount[0] += 1
        mediaBytes[0] += if (ref.bytes > 0) ref.bytes else file.length()
        return ref.url
    }

    private fun pendingDeletes(p: SharedPreferences): List<Int> =
        (p.getStringSet(KEY_DELETED_FP, emptySet()) ?: emptySet())
            .mapNotNull { it.toIntOrNull() }
            .distinct()

    private fun pendingRouteDeletes(p: SharedPreferences): List<Int> =
        (p.getStringSet(KEY_DELETED_RT, emptySet()) ?: emptySet())
            .mapNotNull { it.toIntOrNull() }
            .distinct()

    /**
     * 下行合并足迹：按本地水位增量拉取，只补充本地缺失的 client_id。
     * 本地已有的条目不被云端覆盖（本地权威）；新增条目按 client_id 回填 id。
     * 返回合并后的全量列表与本次新增条数。
     */
    private suspend fun pullFootprints(
        app: Context,
        p: SharedPreferences,
        local: List<Footprint>
    ): Pair<List<Footprint>, Int> {
        val since = p.getLong(KEY_WATERMARK_FP, 0L)
        val arr = BackendApi.fetchSyncFootprints(since) ?: return local to 0
        val existing = local.associateBy { it.id }.toMutableMap()
        var maxAt = since
        var added = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val cid = o.optInt("client_id")
            maxAt = maxOf(maxAt, o.optLong("updated_at", 0L))
            if (cid == 0 || existing.containsKey(cid)) continue
            val payload = o.optJSONObject("payload") ?: continue
            runCatching { footprintFromJson(payload) }.getOrNull()?.let { fp ->
                existing[cid] = fp.copy(id = cid)
                added++
            }
        }
        p.edit().putLong(KEY_WATERMARK_FP, maxAt).apply()
        return existing.values.toList() to added
    }

    /** 下行合并行程，规则同 [pullFootprints]；封面资源名解析失败时回退渐变封面 */
    private suspend fun pullRoutes(
        app: Context,
        p: SharedPreferences,
        local: List<Route>
    ): Pair<List<Route>, Int> {
        val since = p.getLong(KEY_WATERMARK_RT, 0L)
        val arr = BackendApi.fetchSyncRoutes(since) ?: return local to 0
        val existing = local.associateBy { it.id }.toMutableMap()
        var maxAt = since
        var added = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val cid = o.optInt("client_id")
            maxAt = maxOf(maxAt, o.optLong("updated_at", 0L))
            if (cid == 0 || existing.containsKey(cid)) continue
            val payload = o.optJSONObject("payload") ?: continue
            runCatching { routeFromJson(payload, null) }.getOrNull()?.let { r ->
                existing[cid] = r.copy(id = cid)
                added++
            }
        }
        p.edit().putLong(KEY_WATERMARK_RT, maxAt).apply()
        return existing.values.toList() to added
    }

    /** 设置快照下行：基础类型偏好回写 app_settings，凭证键一律跳过 */
    private suspend fun pullSettings(app: Context) {
        val remote = runCatching { BackendApi.fetchSyncSettings() }.getOrNull() ?: return
        val sp = app.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val ed = sp.edit()
        remote.keys().forEach { key ->
            if (key == "__background") return@forEach
            val lower = key.lowercase()
            if (lower.contains("token") || lower.contains("password") ||
                lower.contains("secret") || lower.contains("api_key")
            ) {
                return@forEach
            }
            when (val v = remote.opt(key)) {
                is Boolean -> ed.putBoolean(key, v)
                is Int -> ed.putInt(key, v)
                is Long -> ed.putLong(key, v)
                is Double -> ed.putFloat(key, v.toFloat())
                is String -> ed.putString(key, v)
            }
        }
        ed.apply()
    }

    /**
     * 设置快照：把 app_settings 里的基础类型偏好整体带上（换机恢复用）。
     * 明确排除任何名字像密钥/密码/令牌的键，避免把隐私带出设备。
     * `__background` 是背景图与外观参数，图先上传成 URL，登录后由 BackgroundSync 还原。
     */
    private suspend fun settingsSnapshot(app: Context): JSONObject {
        val prefs = app.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val out = JSONObject()
        runCatching {
            prefs.all.forEach { (key, value) ->
                val lower = key.lowercase()
                if (lower.contains("token") || lower.contains("password") ||
                    lower.contains("secret") || lower.contains("api_key")
                ) {
                    return@forEach
                }
                when (value) {
                    is Boolean -> out.put(key, value)
                    is Int -> out.put(key, value)
                    is Long -> out.put(key, value)
                    is Float -> out.put(key, value.toDouble())
                    is String -> if (value.length <= 512) out.put(key, value)
                }
            }
        }
        runCatching {
            val bg = BackgroundSync.snapshot(app)
            // 本机开了背景但图片没传上去（例如云端恢复下载失败、路径为空）时不要覆盖云端，
            // 否则会把云端仅存的一张背景图洗成空数组，换机就再也恢复不出来。
            val broken = bg.optBoolean("enabled") &&
                (bg.optJSONArray("images")?.length() ?: 0) == 0
            if (!broken) out.put("__background", bg)
        }
        return out
    }

    // ---------- 全量档案 / 隐私空间 ----------

    /** 服务端单次档案上限 16MB，留出余量后超限就把照片列表瘦身 */
    private const val ARCHIVE_BYTES_LIMIT = 15_000_000

    private suspend fun buildArchive(
        app: Context,
        fpItems: List<JSONObject>,
        rtItems: List<JSONObject>
    ): JSONObject {
        val sections = JSONObject()
        sections.put("footprints", JSONArray().apply {
            fpItems.forEach { put(it.optJSONObject("payload") ?: JSONObject()) }
        })
        sections.put("routes", JSONArray().apply {
            rtItems.forEach { put(it.optJSONObject("payload") ?: JSONObject()) }
        })
        sections.put("plans", JSONArray().apply {
            runCatching { Persistence.loadTripPlans(app) }.getOrDefault(emptyList()).forEach {
                put(tripPlanToJson(it))
            }
        })
        sections.put("music", musicSection(app))
        sections.put("comments", commentSection(app))
        sections.put("vault", vaultSection(app))
        sections.put("settings", settingsSnapshot(app))

        val doc = JSONObject().apply {
            put("schema_version", 2)
            put("exported_at", System.currentTimeMillis())
            put("app", "MY足迹")
            put("sections", sections)
        }
        if (doc.toString().length > ARCHIVE_BYTES_LIMIT) {
            val fps = sections.optJSONArray("footprints")
            if (fps != null) {
                for (i in 0 until fps.length()) {
                    fps.optJSONObject(i)?.put("photos", JSONArray())
                }
            }
        }
        return doc
    }

    private fun tripPlanToJson(t: TripPlan): JSONObject = JSONObject()
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
        .put("companions", JSONArray(t.companions))
        .put("reminderEnabled", t.reminderEnabled)
        .put("reminderOffsetDays", t.reminderOffsetDays)
        .put("reminderHour", t.reminderHour)
        .put("reminderMinute", t.reminderMinute)

    /**
     * 音乐收藏快照：按产品口径只上传「歌名 + 歌手」。
     *
     * 不上传歌曲 id、文件路径、封面、歌词、评论或音频；最近播放与播放次数属于
     * 本机行为数据，只留本地，避免把设备文件结构带到云端。
     */
    private suspend fun musicSection(app: Context): JSONObject {
        val favorites = JSONArray()
        MusicPlayer.favoriteRecords(app).forEach { (title, artist) ->
            favorites.put(
                JSONObject()
                    .put("title", title)
                    .put("artist", artist)
            )
        }
        return JSONObject()
            .put("favorites", favorites)
            .put("updated_at", System.currentTimeMillis())
    }

    /** 评论缓存：每篇攻略缓存的评论列表（离线可见的那份） */
    private suspend fun commentSection(app: Context): JSONArray {
        val out = JSONArray()
        val dao = runCatching { AppDatabase.get(app).cacheDao() }.getOrNull() ?: return out
        val guides = runCatching { dao.getAllGuides() }.getOrNull() ?: return out
        guides.forEach { g ->
            val row = runCatching { dao.getComments(g.id) }.getOrNull() ?: return@forEach
            val arr = runCatching { JSONArray(row.json) }.getOrNull() ?: return@forEach
            out.put(
                JSONObject()
                    .put("guide_id", g.id)
                    .put("cached_at", row.cachedAt)
                    .put("comments", arr)
            )
        }
        return out
    }

    /** 隐私空间索引（仅元数据；解锁状态下才有名称/类型） */
    private fun vaultSection(app: Context): JSONObject {
        val items = JSONArray()
        PrivacyVault.items().forEach { item ->
            items.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("kind", item.kind.name.lowercase())
                    .put("size", item.size)
                    .put("added_at", item.addedAt)
            )
        }
        return JSONObject()
            .put("configured", PrivacyVault.isConfigured(app))
            .put("locked", !PrivacyVault.hasSession())
            .put("items", items)
    }

    /**
     * 上传隐私空间密文块：m_xxx.bin（每条内容）、index.enc（索引）、vault.meta（salt/校验）。
     * 都是端侧 AES-GCM 密文，服务端拿不到明文；同一次内容只上传一次。
     */
    private suspend fun uploadVaultBlobs(app: Context, p: SharedPreferences): Int {
        if (!PrivacyVault.isConfigured(app)) return 0
        val root = com.example.my.data.UserScope.dir(app, "privacy_vault")
        val files = ArrayList<File>()
        File(root, "m").listFiles()?.forEach { if (it.isFile && it.length() > 0) files.add(it) }
        File(root, "index.enc").takeIf { it.isFile }?.let { files.add(it) }
        File(root, "vault.meta").takeIf { it.isFile }?.let { files.add(it) }

        var uploaded = 0
        files.forEach { f ->
            val key = "vault:" + f.name + ":" + f.length() + ":" + f.lastModified()
            if (p.contains(key)) return@forEach
            val item = PrivacyVault.items().firstOrNull { "m_${it.id}.bin" == f.name }
            val itemId = item?.id ?: f.name.substringBeforeLast('.')
            val name = item?.name?.takeIf { it.isNotBlank() } ?: f.name
            val kind = when {
                f.name == "index.enc" -> "index"
                f.name == "vault.meta" -> "meta"
                item != null -> item.kind.name.lowercase()
                else -> "encrypted"
            }
            if (BackendApi.uploadVaultFile(f, itemId, name, kind) != null) {
                p.edit().putBoolean(key, true).apply()
                uploaded += 1
            }
        }
        return uploaded
    }

    private fun jsonOrText(raw: String): Any {
        if (raw.isBlank()) return ""
        return runCatching { JSONObject(raw) }.getOrElse { runCatching { JSONArray(raw) }.getOrElse { raw } }
    }

    private fun guessMime(ext: String): String? = when (ext.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic", "heif" -> "image/heic"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        else -> null
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
