package com.example.my.data.sync

import android.content.Context
import android.util.Log
import com.example.my.data.BackendApi
import com.example.my.data.CloudConfig
import com.example.my.data.Persistence
import com.example.my.data.local.OfflineCache
import com.example.my.data.local.commentFromJson
import com.example.my.data.local.footprintFromJson
import com.example.my.data.local.jsonStringList
import com.example.my.data.local.routeFromJson
import com.example.my.model.Footprint
import com.example.my.model.Route
import com.example.my.model.TripPlan
import com.example.my.util.PhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 登录后全量回灌：把云端保存的所有个人数据写回本机。
 *
 * 触发时机：登录成功（[com.example.my.state.AppState.onLoggedIn]）。
 * 规则：
 * 1. 云端补本地缺失的条目，本地已有同 id 的不覆盖（本地权威，避免把离线编辑冲掉）；
 * 2. 足迹里的照片/视频从 `/uploads/...` 拉回本机私有目录，路径改写成本地绝对路径，
 *    否则 UI 的图片加载器读不到；
 * 3. 计划、评论缓存、隐私空间密文、AI 端点配置一并恢复；
 * 4. 完成后写 `restored_user_id` 标记，同一个账号重复登录不重复下载。
 *
 * 只读云端、不删本地：恢复失败时本机数据保持原样。
 */
object CloudRestore {

    private val mediaScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var mediaJob: Job? = null

    fun isMediaRestoring(): Boolean = mediaJob?.isActive == true

    data class Result(
        val ok: Boolean,
        val footprints: Int = 0,
        val routes: Int = 0,
        val plans: Int = 0,
        val musicFavorites: Int = 0,
        val media: Int = 0,
        val vault: Int = 0,
        val aiConfig: JSONObject? = null,
        val message: String? = null
    )

    private const val PREFS = "cloud_sync_state"
    private const val KEY_RESTORED_USER = "restored_user_id"
    private const val KEY_LOCAL_OWNER = "local_owner_user_id"

    fun alreadyRestored(context: Context, userId: Int): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_RESTORED_USER, 0) == userId

    fun markRestored(context: Context, userId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_RESTORED_USER, userId)
            .putInt(KEY_LOCAL_OWNER, userId)
            .apply()
    }

    /**
     * 执行一次全量恢复。
     * [onProgress] 用于登录页展示「正在恢复第 n/m 张照片」，不阻塞主线程。
     */
    suspend fun restore(
        context: Context,
        onProgress: (String) -> Unit = {},
        onMediaDone: () -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        if (!CloudConfig.isEnabled(context)) return@withContext Result(false, message = "云端未启用")
        if (BackendApi.token == null) return@withContext Result(false, message = "未登录")

        val boot = BackendApi.fetchBootstrap() ?: return@withContext Result(false, message = "云端暂时不可达")
        val app = context.applicationContext
        val uid = BackendApi.remoteUserId ?: 0
        if (uid > 0) com.example.my.data.UserScope.activate(app, uid)

        // 换账号登录：本机数据属于上一个账号，先清掉再回灌本账号的云端数据，
        // 避免两个账号的足迹混在同一台设备上（上一账号的数据已在云端，不会丢）。
        if (uid > 0) switchAccountIfNeeded(app, uid)

        val settingsOk = applySettings(app, boot.optJSONObject("settings"))
        val aiConfig = boot.optJSONObject("ai")

        // ---------- 1. 足迹 / 行程：先把结构化数据落盘，界面立刻可见 ----------
        // 媒体（照片/视频）放在后台下载，不再让 20MB 的图片把「0 条足迹」卡住几分钟。
        val localFp = OfflineCache.loadFootprints(app) ?: emptyList()
        val localFpIds = localFp.map { it.id }.toSet()
        val remoteFpItems = boot.optJSONArray("footprints") ?: JSONArray()
        val pending = ArrayList<Pair<Int, JSONObject>>()
        for (i in 0 until remoteFpItems.length()) {
            val item = remoteFpItems.optJSONObject(i) ?: continue
            val cid = item.optInt("client_id")
            if (cid <= 0 || cid in localFpIds) continue
            item.optJSONObject("payload")?.let { pending.add(cid to it) }
        }
        val parsedRaw = ArrayList<Footprint>(pending.size)
        pending.forEach { (cid, payload) ->
            runCatching { footprintFromJson(payload).copy(id = cid) }
                .onFailure { debugLog(app, "footprint $cid parse failed: ${it.stackTraceToString()}") }
                .getOrNull()?.let { parsedRaw.add(it) }
        }
        val mergedRaw = localFp + parsedRaw
        if (parsedRaw.isNotEmpty()) {
            runCatching { OfflineCache.saveFootprints(app, mergedRaw) }
                .onFailure { debugLog(app, "saveFootprints raw failed size=${mergedRaw.size}: ${it.stackTraceToString()}") }
                .onSuccess { debugLog(app, "saveFootprints raw ok size=${mergedRaw.size}") }
        }
        Log.i("CloudRestore", "pending=${pending.size} rawParsed=${parsedRaw.size} local=${localFp.size}")

        val localRt = OfflineCache.loadRoutes(app) ?: emptyList()
        val localRtIds = localRt.map { it.id }.toSet()
        val remoteRtItems = boot.optJSONArray("routes") ?: JSONArray()
        val restoredRt = ArrayList<Route>()
        for (i in 0 until remoteRtItems.length()) {
            val item = remoteRtItems.optJSONObject(i) ?: continue
            val cid = item.optInt("client_id")
            if (cid <= 0 || cid in localRtIds) continue
            val payload = item.optJSONObject("payload") ?: continue
            runCatching { routeFromJson(payload, null) }.getOrNull()?.let { r ->
                restoredRt.add(r.copy(id = cid))
            }
        }
        if (restoredRt.isNotEmpty()) OfflineCache.saveRoutes(app, localRt + restoredRt)

        // 媒体后台下载：登录后立刻能看到文字/数量，照片和视频下载完再自动刷新一次。
        startMediaRestore(app, pending, onProgress, onMediaDone)

        // ---------- 3. 出行计划 ----------
        val localPlans = Persistence.loadTripPlans(app)
        val localPlanIds = localPlans.map { it.id }.toSet()
        val planItems = boot.optJSONObject("collections")?.optJSONArray("plans") ?: JSONArray()
        val restoredPlans = ArrayList<TripPlan>()
        for (i in 0 until planItems.length()) {
            val payload = planItems.optJSONObject(i)?.optJSONObject("payload") ?: continue
            val plan = Persistence.tripPlanFromJson(payload) ?: continue
            if (plan.id !in localPlanIds) restoredPlans.add(plan)
        }
        if (restoredPlans.isNotEmpty()) Persistence.saveTripPlans(app, localPlans + restoredPlans)
        CloudSync.seedPlanBaseline(app)

        // ---------- 3.5 音乐收藏（云端只存歌名+歌手） ----------
        val musicFavorites = runCatching { MusicFavoritesSync.restore(app, boot) }.getOrDefault(0)

        // ---------- 4. 评论缓存 ----------
        val commentItems = boot.optJSONObject("collections")?.optJSONArray("comments") ?: JSONArray()
        for (i in 0 until commentItems.length()) {
            val payload = commentItems.optJSONObject(i)?.optJSONObject("payload") ?: continue
            val guideId = payload.optInt("guide_id")
            val arr = payload.optJSONArray("comments") ?: continue
            if (guideId <= 0) continue
            if (OfflineCache.loadComments(app, guideId) != null) continue
            val comments = (0 until arr.length()).mapNotNull { idx ->
                runCatching { commentFromJson(arr.getJSONObject(idx)) }.getOrNull()
            }
            if (comments.isNotEmpty()) OfflineCache.saveComments(app, guideId, comments)
        }

        // ---------- 5. 隐私空间密文（只搬密文，服务端无密钥） ----------
        val vaultItems = boot.optJSONObject("vault")?.optJSONArray("items") ?: JSONArray()
        var vaultDone = 0
        val ordered = (0 until vaultItems.length())
            .mapNotNull { vaultItems.optJSONObject(it) }
            .sortedBy { item ->
                when (item.optString("kind")) {
                    "meta" -> 0
                    "index" -> 1
                    else -> 2
                }
            }
        ordered.forEach { item ->
            val url = item.optString("url").takeIf { it.isNotBlank() } ?: return@forEach
            val kind = item.optString("kind")
            val name = when (kind) {
                "meta" -> "vault.meta"
                "index" -> "index.enc"
                else -> item.optString("name").takeIf { it.endsWith(".bin") }
                    ?: "m_${item.optString("item_id")}.bin"
            }
            val target = File(com.example.my.data.UserScope.dir(app, "privacy_vault"), name)
            if (target.isFile && target.length() > 0) return@forEach
            if (download(url, target)) vaultDone++
        }

        // ---------- 6. 标记 ----------
        if (uid > 0) markRestored(app, uid)

        Result(
            ok = true,
            footprints = parsedRaw.size,
            routes = restoredRt.size,
            plans = restoredPlans.size,
            musicFavorites = musicFavorites,
            media = 0,
            vault = vaultDone,
            aiConfig = aiConfig,
            message = if (pending.isNotEmpty()) "照片/视频后台下载中" else null
        )
    }

    /**
     * 后台把足迹里的照片/视频下载到本机，完成后再落库并通知界面刷新。
     * 结构数据在调用它之前已经写进 Room，所以登录后不会因为 20MB 图片卡在「0 条足迹」。
     */
    private fun startMediaRestore(
        context: Context,
        pending: List<Pair<Int, JSONObject>>,
        onProgress: (String) -> Unit,
        onMediaDone: () -> Unit
    ) {
        if (pending.isEmpty()) {
            onMediaDone()
            return
        }
        if (mediaJob?.isActive == true) return
        val app = context.applicationContext
        val job = mediaScope.launch {
            val jobs = pending.flatMap { (cid, payload) ->
                payloadMediaPaths(payload).map { cid to it }
            }.distinctBy { it.first to it.second }
            if (jobs.isEmpty()) {
                onMediaDone()
                return@launch
            }
            debugLog(app, "media start jobs=${jobs.size}")
            val prefetched = prefetchMedia(app, jobs) { done, total ->
                onProgress("正在下载照片/视频 $done/$total")
            }
            val rewritten = ArrayList<Footprint>(pending.size)
            pending.forEach { (cid, payload) ->
                runCatching { footprintFromJson(rewriteMedia(app, cid, payload, prefetched)) }
                    .onFailure { debugLog(app, "rewrite footprint $cid failed: ${it.stackTraceToString()}") }
                    .getOrNull()?.let { rewritten.add(it.copy(id = cid)) }
            }
            if (rewritten.isNotEmpty()) {
                val current = OfflineCache.loadFootprints(app) ?: emptyList()
                val byId = current.associateBy { it.id }.toMutableMap()
                rewritten.forEach { byId[it.id] = it }
                OfflineCache.saveFootprints(app, byId.values.toList())
            }
            debugLog(app, "media done jobs=${jobs.size} rewritten=${rewritten.size} landed=${prefetched.size}")
            onProgress("照片/视频已保存到本机 ${prefetched.size} 个")
            onMediaDone()
        }
        mediaJob = job
        job.invokeOnCompletion { if (mediaJob === job) mediaJob = null }
    }

    // ---------- 账号隔离 ----------

    /**
     * 同设备换账号：清空与账号绑定的本机数据（足迹/行程/计划/回收站）。
     * 只清本机，云端那份属于上一个账号，不受影响。
     */
    private suspend fun switchAccountIfNeeded(app: Context, uid: Int) {
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val owner = p.getInt(KEY_LOCAL_OWNER, 0)
        if (owner == 0 || owner == uid) {
            if (owner == 0) p.edit().putInt(KEY_LOCAL_OWNER, uid).apply()
            return
        }
        OfflineCache.saveFootprints(app, emptyList())
        OfflineCache.saveRoutes(app, emptyList())
        Persistence.saveTripPlans(app, emptyList())
        Persistence.saveTrash(app, emptyList())
        // 听歌收藏/最近/统计属于个人数据，换账号必须一起清掉，否则会串到新账号
        runCatching { com.example.my.data.music.MusicPlayer.clearAccountMusicState(app) }
        MusicFavoritesSync.resetBaseline(app)
        CloudSync.resetPlanBaseline(app)
        p.edit()
            .putInt(KEY_LOCAL_OWNER, uid)
            .remove(KEY_RESTORED_USER)
            .remove("watermark_footprints")
            .remove("watermark_routes")
            .apply()
    }

    // ---------- 媒体 ----------

    private fun payloadMediaPaths(payload: JSONObject): List<String> {
        val out = ArrayList<String>()
        out += jsonStringList(payload.opt("photos"))
        out += jsonStringList(payload.opt("videoPaths"))
        listOf("videoPath", "startPhoto", "endPhoto").forEach { key ->
            payload.optString(key).takeIf { it.isNotBlank() }?.let { out += it }
        }
        out += jsonStringList(payload.opt("midPhotos"))
        return out.filter { isRemote(it) }
    }

    /** 6 路并发把媒体拉到本机；返回 key = "足迹id|远程地址" 的已落地文件 */
    private suspend fun prefetchMedia(
        context: Context,
        jobs: List<Pair<Int, String>>,
        onProgress: (Int, Int) -> Unit
    ): Map<String, File> = coroutineScope {
        val total = jobs.size
        var done = 0
        val results = java.util.concurrent.ConcurrentHashMap<String, File>()
        val gate = Semaphore(6)
        jobs.map { (footprintId, url) ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val file = runCatching { downloadToFootprint(context, footprintId, url) }.getOrNull()
                    if (file != null) results["$footprintId|$url"] = file
                    val n = synchronized(results) { done += 1; done }
                    onProgress(n, total)
                }
            }
        }.awaitAll()
        results.toMap()
    }

    private fun isRemote(path: String?): Boolean =
        path != null && (
            path.startsWith("http://") ||
                path.startsWith("https://") ||
                // 后端 /api/sync/media 返回的是相对地址 /uploads/...，
                // 老逻辑只认 http，导致换机恢复时照片/视频被原样留下，UI 读本地文件永远空白。
                path.startsWith("/uploads/")
            )

    /**
     * 把 payload 里所有 `/uploads/...` 媒体改写成本机路径；文件已由 [prefetchMedia] 并发下载。
     * 下载失败的保持 URL（至少不会丢数据），后续冷启动修复还会再试。
     */
    private fun rewriteMedia(
        context: Context,
        footprintId: Int,
        payload: JSONObject,
        cache: Map<String, File>
    ): JSONObject {
        fun localize(path: String?): String? {
            if (!isRemote(path)) return path
            val file = cache["$footprintId|$path"] ?: downloadToFootprint(context, footprintId, path!!)
            return file?.absolutePath ?: path
        }

        val out = JSONObject(payload.toString())
        // 兼容旧版字符串格式：photos 可能是 "a b c" 而不是数组
        val photos = jsonStringList(out.opt("photos"))
        if (photos.isNotEmpty()) {
            out.put("photos", JSONArray().apply { photos.forEach { put(localize(it)) } })
        }
        listOf("videoPath", "startPhoto", "endPhoto").forEach { key ->
            out.optString(key).takeIf { it.isNotBlank() }?.let { out.put(key, localize(it)) }
        }
        val videos = jsonStringList(out.opt("videoPaths"))
        if (videos.isNotEmpty()) {
            out.put("videoPaths", JSONArray().apply { videos.forEach { put(localize(it)) } })
        }
        val mids = jsonStringList(out.opt("midPhotos"))
        if (mids.isNotEmpty()) {
            out.put("midPhotos", JSONArray().apply { mids.forEach { put(localize(it)) } })
        }
        return out
    }

    private fun downloadToFootprint(context: Context, footprintId: Int, url: String): File? {
        val ext = extensionOf(url)
        val dir = PhotoStore.dirOf(context, footprintId).apply { if (!exists()) mkdirs() }
        val target = File(dir, "restored_" + sha256(url).take(16) + "." + ext)
        synchronized(target.absolutePath) {
            if (target.isFile && target.length() > 0) return target
            return if (download(url, target)) target else null
        }
    }

    /**
     * 修复「本地已有足迹，但 photoPaths/videoPath 仍是云端 URL」的历史数据。
     *
     * 线上媒体地址是 /uploads/...；旧版只把 http(s) 当远程，恢复时没有下载，
     * 于是 UI 的 PhotoStore.decodeFile 读不到文件，旅行回忆/足迹详情就是一片空白。
     * 这个函数不依赖 restored_user_id 标记，每次登录都可安全重跑；下载成功的路径改成本机绝对路径。
     */
    suspend fun repairLocalMedia(
        context: Context,
        onProgress: (String) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val local = OfflineCache.loadFootprints(app) ?: return@withContext 0

        fun mediaOf(fp: Footprint): List<String> {
            val out = ArrayList<String>()
            out += fp.photoPaths
            fp.videoPath?.let { out += it }
            out += fp.videoPaths
            fp.startPhotoPath?.let { out += it }
            fp.endPhotoPath?.let { out += it }
            out += fp.midPhotoPaths
            fp.marks.forEach { m -> m.photoPath?.let { out += it } }
            return out.filter { isRemote(it) }
        }

        // 并发预下载，避免大量照片/视频时一条一条串行等待
        val jobs = local.flatMap { fp -> mediaOf(fp).map { fp.id to it } }
            .distinctBy { it.first to it.second }
        val files = if (jobs.isNotEmpty()) {
            prefetchMedia(app, jobs) { done, total ->
                onProgress("正在把照片/视频保存到本机 $done/$total")
            }
        } else {
            emptyMap()
        }

        var done = 0
        var changed = false

        fun localize(footprintId: Int, path: String?): String? {
            if (path == null || !isRemote(path)) return path
            val file = files["$footprintId|$path"] ?: return path
            done++
            return file.absolutePath
        }

        val updated = local.map { fp ->
            var fpChanged = false
            val photos = fp.photoPaths.map { path ->
                val next = localize(fp.id, path) ?: path
                if (next != path) fpChanged = true
                next
            }
            val video = fp.videoPath?.let { path ->
                val next = localize(fp.id, path)
                if (next != path) fpChanged = true
                next
            }
            val videos = fp.videoPaths.map { path ->
                val next = localize(fp.id, path) ?: path
                if (next != path) fpChanged = true
                next
            }
            val start = fp.startPhotoPath?.let { path ->
                val next = localize(fp.id, path)
                if (next != path) fpChanged = true
                next
            }
            val end = fp.endPhotoPath?.let { path ->
                val next = localize(fp.id, path)
                if (next != path) fpChanged = true
                next
            }
            val mids = fp.midPhotoPaths.map { path ->
                val next = localize(fp.id, path) ?: path
                if (next != path) fpChanged = true
                next
            }
            val marks = fp.marks.map { mark ->
                val path = mark.photoPath ?: return@map mark
                val next = localize(fp.id, path)
                if (next != path) fpChanged = true
                mark.copy(photoPath = next)
            }

            if (!fpChanged) return@map fp
            changed = true
            fp.copy(
                photoPaths = photos,
                videoPath = video,
                videoPaths = videos,
                startPhotoPath = start,
                endPhotoPath = end,
                midPhotoPaths = mids,
                marks = marks
            )
        }

        if (done > 0) onProgress("照片/视频已保存到本机 $done 个")
        if (changed) OfflineCache.saveFootprints(app, updated)
        done
    }

    private fun extensionOf(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val ext = clean.substringAfterLast('.', "").lowercase()
        return if (ext in setOf("jpg", "jpeg", "png", "webp", "heic", "gif", "mp4", "mov", "3gp", "m4v")) ext else "jpg"
    }

    /** 下载到目标文件；先写 .part 再改名，避免半截文件被当成已下载 */
    private fun download(url: String, target: File): Boolean = runCatching {
        // fullUrl 会把 /uploads/... 补成当前域名，也会把旧的局域网/127.0.0.1 媒体地址归一到当前服务端
        val full = BackendApi.fullUrl(url) ?: return@runCatching false
        target.parentFile?.mkdirs()
        val tmp = File(target.absolutePath + ".part")
        val conn = (URL(full).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MYZuji/1.0")
        }
        try {
            if (conn.responseCode !in 200..299) return@runCatching false
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output, 64 * 1024) }
            }
            if (tmp.length() <= 0L) return@runCatching false
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** release 包 Log 会被裁掉，恢复过程额外写一份文件，方便现场排查 */
    private fun debugLog(context: Context, message: String) {
        runCatching {
            File(context.filesDir, "restore_debug.log")
                .appendText("${System.currentTimeMillis()} $message\n")
        }
    }

    // ---------- 设置 ----------

    /** 基础偏好回写；凭证类键一律跳过（与 CloudSync 的口径一致） */
    private suspend fun applySettings(context: Context, remote: JSONObject?): Boolean {
        if (remote == null) return false
        // 背景图与外观参数单独还原：先下载图片，再一次性 apply
        runCatching { BackgroundSync.restore(context, remote.optJSONObject("__background")) }
        val sp = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val ed = sp.edit()
        var applied = 0
        remote.keys().forEach { key ->
            if (key == "__background") return@forEach
            val lower = key.lowercase()
            if (lower.contains("token") || lower.contains("password") ||
                lower.contains("secret") || lower.contains("api_key")
            ) {
                return@forEach
            }
            when (val v = remote.opt(key)) {
                is Boolean -> { ed.putBoolean(key, v); applied++ }
                is Int -> { ed.putInt(key, v); applied++ }
                is Long -> { ed.putLong(key, v); applied++ }
                is Double -> { ed.putFloat(key, v.toFloat()); applied++ }
                is String -> if (v.length <= 512) { ed.putString(key, v); applied++ }
            }
        }
        ed.apply()
        return applied > 0 || remote.length() == 0
    }
}
