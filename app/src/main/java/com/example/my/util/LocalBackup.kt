package com.example.my.util

import android.content.Context
import com.example.my.data.local.footprintFromJson
import com.example.my.data.local.footprintToJson
import com.example.my.data.local.guideFromJson
import com.example.my.data.local.guideToJson
import com.example.my.data.local.routeFromJson
import com.example.my.data.local.routeToJson
import com.example.my.model.Footprint
import com.example.my.model.Guide
import com.example.my.model.Route
import com.example.my.model.TripPlan
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 纯本地模式的"后悔药"：把用户数据定时快照到 files/backups/，只保留最近几份。
 *
 * 为什么要有：砍掉云端后，数据只在这台手机上，误删/覆盖就真没了。
 * 自动快照在每次 App 退到后台时做一次（数据没变就跳过），设置页可查看并一键恢复。
 *
 * 只备份**用户自己产生的东西**（足迹 / 行程路线 / 出行计划 / 自己发布的攻略），
 * 演示数据不备份 —— 恢复了也只是把别人的样例又抄一遍。
 */
object LocalBackup {

    /** 最多保留几份快照，超出删最旧的 */
    private const val MAX_SNAPSHOTS = 5

    fun dirOf(context: Context): File = com.example.my.data.UserScope.dir(context, "backups")

    data class Snapshot(
        val file: File,
        val savedAt: Long,
        val footprintCount: Int,
        val routeCount: Int,
        val planCount: Int
    )

    /** 恢复出来的数据；任何一项解析失败都只会是空表，不会整体作废 */
    data class Payload(
        val footprints: List<Footprint>,
        val routes: List<Route>,
        val plans: List<TripPlan>,
        val guides: List<Guide>
    )

    /** 写一份快照，返回文件；失败返回 null（磁盘满/无权限时静默失败，不影响主流程） */
    fun save(
        context: Context,
        footprints: List<Footprint>,
        routes: List<Route>,
        plans: List<TripPlan>,
        myGuides: List<Guide>
    ): File? = runCatching {
        val root = JSONObject().apply {
            put("app", "MY足迹")
            put("kind", "local-snapshot")
            put("schemaVersion", 2)
            put("savedAt", System.currentTimeMillis())
            put("footprints", JSONArray().apply { footprints.forEach { put(footprintToJson(it)) } })
            put("routes", JSONArray().apply { routes.forEach { put(routeToJson(it)) } })
            put("plans", JSONArray().apply { plans.forEach { put(TripPlansCodec.encode(it)) } })
            put("guides", JSONArray().apply { myGuides.forEach { put(guideToJson(it)) } })
        }
        val dir = dirOf(context)
        // 文件名用可排序的时间戳，list() 直接倒序即可
        val file = File(dir, "snapshot-${System.currentTimeMillis()}.json")
        file.writeText(root.toString(), Charsets.UTF_8)
        prune(context, keep = MAX_SNAPSHOTS)
        file
    }.getOrNull()

    /** 最近快照，最新在前 */
    fun list(context: Context): List<Snapshot> {
        val files = runCatching {
            dirOf(context).listFiles { f -> f.isFile && f.name.endsWith(".json") }?.toList()
        }.getOrNull().orEmpty()
        return files.mapNotNull { f ->
            runCatching {
                val o = JSONObject(f.readText(Charsets.UTF_8))
                Snapshot(
                    file = f,
                    savedAt = o.optLong("savedAt", f.lastModified()),
                    footprintCount = o.optJSONArray("footprints")?.length() ?: 0,
                    routeCount = o.optJSONArray("routes")?.length() ?: 0,
                    planCount = o.optJSONArray("plans")?.length() ?: 0
                )
            }.getOrNull()
        }.sortedByDescending { it.savedAt }
    }

    /** 读一份快照；结构不认识返回 null */
    fun read(file: File): Payload? = runCatching {
        val o = JSONObject(file.readText(Charsets.UTF_8))
        if (o.optString("app") != "MY足迹") return null
        Payload(
            footprints = listOf(o.optJSONArray("footprints")).flatMap { arr -> arr.objects() }
                .mapNotNull { runCatching { footprintFromJson(it) }.getOrNull() },
            routes = listOf(o.optJSONArray("routes")).flatMap { arr -> arr.objects() }
                .mapNotNull { runCatching { routeFromJson(it) }.getOrNull() },
            plans = listOf(o.optJSONArray("plans")).flatMap { arr -> arr.objects() }
                .mapNotNull { runCatching { TripPlansCodec.decode(it) }.getOrNull() },
            guides = listOf(o.optJSONArray("guides")).flatMap { arr -> arr.objects() }
                .mapNotNull { runCatching { guideFromJson(it) }.getOrNull() }
        )
    }.getOrNull()

    /** 当前备份占用的空间，用于设置页展示 */
    fun bytesOf(context: Context): Long =
        runCatching { dirOf(context).listFiles()?.sumOf { it.length() } ?: 0L }.getOrDefault(0L)

    private fun prune(context: Context, keep: Int) {
        val files = dirOf(context).listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.name } ?: return
        files.drop(keep).forEach { runCatching { it.delete() } }
    }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }
}

/** TripPlan ↔ JSON（与 Persistence 内部格式保持一致，两边可互换） */
internal object TripPlansCodec {

    fun encode(t: TripPlan): JSONObject = JSONObject()
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

    fun decode(o: JSONObject): TripPlan? {
        if (!o.has("id") || !o.has("name")) return null
        fun strings(key: String) = o.optJSONArray(key)?.let { a ->
            (0 until a.length()).map { a.optString(it) }
        }.orEmpty()
        return TripPlan(
            id = o.optLong("id"),
            name = o.optString("name", ""),
            destination = o.optString("destination", ""),
            startDate = o.optString("startDate", ""),
            days = o.optInt("days", 0),
            note = o.optString("note", ""),
            createdAt = o.optLong("createdAt", 0L),
            archived = o.optBoolean("archived", false),
            dailyPlan = strings("dailyPlan"),
            packedItems = strings("packedItems"),
            customPacking = strings("customPacking"),
            autoArchive = o.optBoolean("autoArchive", true),
            manualOrder = o.optInt("manualOrder", -1),
            companions = strings("companions"),
            reminderEnabled = o.optBoolean("reminderEnabled", true),
            reminderOffsetDays = o.optInt("reminderOffsetDays", 1),
            reminderHour = o.optInt("reminderHour", 9),
            reminderMinute = o.optInt("reminderMinute", 0),
            reminderVibrate = o.optBoolean("reminderVibrate", true),
            reminderSound = o.optBoolean("reminderSound", true)
        )
    }
}
