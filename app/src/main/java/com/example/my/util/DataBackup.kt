package com.example.my.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.my.model.Footprint
import com.example.my.model.Route
import com.example.my.model.TripPlan
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据导出备份：把足迹与路线序列化为 JSON 文件，
 * 经系统分享面板发送到微信/文件管理/网盘等，防止误删换机丢数据。
 * 对应 GitHub 成熟 Android 工程（含 Google 官方示例）中"设置页数据管理"的标准能力。
 */
object DataBackup {

    /** 生成备份文件并拉起系统分享面板；返回是否成功拉起 */
    fun exportAndShare(
        context: Context,
        footprints: List<Footprint>,
        routes: List<Route>,
        plans: List<TripPlan> = emptyList()
    ): Boolean {
        return try {
            val json = buildBackupJson(footprints, routes, plans)
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            val dir = File(context.cacheDir, "backup").apply { mkdirs() }
            val file = File(dir, "MY足迹备份-$stamp.json")
            file.writeText(json.toString(2), Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, "MY足迹备份", uri)
            }
            context.startActivity(
                Intent.createChooser(intent, "导出 MY足迹 备份").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun buildBackupJson(
        footprints: List<Footprint>,
        routes: List<Route>,
        plans: List<TripPlan> = emptyList()
    ): JSONObject {
        val root = JSONObject()
        root.put("app", "MY足迹")
        root.put("schemaVersion", 1)
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        root.put("footprintCount", footprints.size)
        root.put("routeCount", routes.size)
        root.put("planCount", plans.size)

        val fpArr = JSONArray()
        footprints.forEach { fp ->
            fpArr.put(
                JSONObject().apply {
                    put("id", fp.id)
                    put("city", fp.city)
                    put("name", fp.name)
                    put("note", fp.note)
                    put("date", fp.date)
                    put("x", fp.x.toDouble())
                    put("y", fp.y.toDouble())
                    put("special", fp.special)
                    put("specialReason", fp.specialReason ?: "")
                    put("routeId", fp.routeId ?: -1)
                    put("dateMillis", fp.dateMillis ?: -1L)
                    if (fp.lat != null && fp.lon != null) {
                        put("lat", fp.lat)
                        put("lon", fp.lon)
                    }
                    put("photoPaths", JSONArray(fp.photoPaths))
                    // 视频 / 实况 / 配乐：备份里保留路径，恢复时若文件还在就能直接播
                    fp.videoPath?.let { put("videoPath", it) }
                    if (fp.videoPaths.isNotEmpty()) put("videoPaths", JSONArray(fp.videoPaths))
                    if (fp.videoIsLive) put("videoIsLive", true)
                    if (fp.videoBytes > 0L) put("videoBytes", fp.videoBytes)
                    fp.bgmPath?.let { put("bgmPath", it) }
                    fp.bgmTitle?.let { put("bgmTitle", it) }
                    fp.bgmArtist?.let { put("bgmArtist", it) }
                    fp.bgmSource?.let { put("bgmSource", it) }
                }
            )
        }
        root.put("footprints", fpArr)

        val rtArr = JSONArray()
        routes.forEach { r ->
            rtArr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("title", r.title)
                    put("year", r.year)
                    put("date", r.date)
                    put("days", r.days)
                    put("cities", JSONArray(r.cities))
                    put("coverSeed", r.coverSeed)
                    put("special", r.special)
                    put("specialName", r.specialName ?: "")
                    put("note", r.note)
                    put("tags", JSONArray(r.tags))
                    put("totalCost", r.totalCost)
                    put("isPublic", r.isPublic)
                    put("photoCount", r.photoCount)
                }
            )
        }
        root.put("routes", rtArr)

        // 出行计划（本地模式）：和本地快照用同一套 JSON 结构，两边可互相导入
        val planArr = JSONArray()
        plans.forEach { planArr.put(TripPlansCodec.encode(it)) }
        root.put("plans", planArr)
        return root
    }
}
