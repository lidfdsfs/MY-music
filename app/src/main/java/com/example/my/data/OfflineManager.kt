package com.example.my.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.my.data.local.OfflineCache
import com.example.my.model.Footprint
import com.example.my.model.Guide
import com.example.my.model.Route
import com.example.my.model.TripPlan
import java.io.File

/**
 * 离线旅行模式的数据门面：
 *  - 一键把攻略 / 足迹 / 行程 / 出行计划写入本地缓存（断网也能读）
 *  - 统计与清理 OSMDroid 地图瓦片缓存（浏览过的区域会自动缓存）
 *  - 网络状态判断
 */
object OfflineManager {

    private const val PREFS = "offline_pack"
    private const val KEY_LAST_UPDATED = "last_updated"

    data class PackStats(
        val guideCount: Int,
        val footprintCount: Int,
        val routeCount: Int,
        val planCount: Int,
        val lastUpdated: Long
    )

    /** 本地离线快照：写入后断网也能看到全部内容 */
    suspend fun updatePack(
        context: Context,
        guides: List<Guide>,
        footprints: List<Footprint>,
        routes: List<Route>,
        plans: List<TripPlan>
    ): PackStats {
        OfflineCache.saveGuides(context, guides)
        OfflineCache.saveFootprints(context, footprints)
        OfflineCache.saveRoutes(context, routes)
        Persistence.saveTripPlans(context, plans)
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_UPDATED, now).apply()
        return PackStats(guides.size, footprints.size, routes.size, plans.size, now)
    }

    fun lastUpdated(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UPDATED, 0L)

    /** OSMDroid 瓦片缓存占用（字节） */
    fun tileCacheBytes(context: Context): Long {
        val dir = File(context.cacheDir, "osmdroid")
        if (!dir.exists()) return 0L
        return runCatching {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }

    /** 清除地图瓦片缓存；下次浏览时自动重新缓存 */
    fun clearMapTiles(context: Context): Boolean {
        val dir = File(context.cacheDir, "osmdroid")
        return runCatching { !dir.exists() || dir.deleteRecursively() }.getOrDefault(false)
    }

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(java.util.Locale.CHINA, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024L -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}
