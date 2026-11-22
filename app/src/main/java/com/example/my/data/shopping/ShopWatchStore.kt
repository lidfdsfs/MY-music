package com.example.my.data.shopping

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 比价收藏与历史最低价（纯本地、无后台轮询）。
 *
 * - 收藏关键词：用户把想长期关注的商品名存下来，下次进入购物页可一键重新比价；
 * - 历史最低价：每次比价成功后记录该词当前最低报价，下次再查时与上次对比，
 *   在页面上提示「较上次下降 / 上升」。
 *
 * 边界：这里不做后台定时抓取、不做降价推送——没有常驻轮询服务，提醒只在用户
 * 主动打开比价时计算，避免承诺无法兑现的实时通知。
 */
object ShopWatchStore {

    private const val PREF = "shop_watch_prefs"
    private const val KEY_FAV = "favorites"
    private const val KEY_PRICE = "last_lowest"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 已收藏的关键词（去重、保持插入顺序） */
    fun favorites(ctx: Context): List<String> {
        val raw = prefs(ctx).getString(KEY_FAV, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optString(it).trim().ifBlank { null } }.distinct()
        }.getOrDefault(emptyList())
    }

    fun isFavorite(ctx: Context, query: String): Boolean =
        favorites(ctx).any { it.equals(query.trim(), ignoreCase = true) }

    /** 切换收藏，返回切换后的状态（true = 已收藏） */
    fun toggleFavorite(ctx: Context, query: String): Boolean {
        val key = query.trim()
        if (key.isBlank()) return false
        val list = favorites(ctx).toMutableList()
        val existed = list.removeAll { it.equals(key, ignoreCase = true) }
        val nowFavorite = !existed
        if (nowFavorite) list.add(0, key)
        prefs(ctx).edit().putString(KEY_FAV, JSONArray(list).toString()).apply()
        return nowFavorite
    }

    /** 上次记录的最低报价；没有则返回 null */
    fun lastLowest(ctx: Context, query: String): Double? {
        val raw = prefs(ctx).getString(KEY_PRICE, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val v = o.optDouble(query.trim().lowercase(), Double.NaN)
            if (v.isNaN()) null else v
        }.getOrNull()
    }

    /**
     * 记录本次最低价。返回记录前的上次最低价（用于页面对比提示）；
     * 本次没有带价结果时不覆盖历史记录。
     */
    fun recordLowest(ctx: Context, query: String, price: Double?): Double? {
        val key = query.trim().lowercase()
        if (key.isBlank()) return null
        val prefs = prefs(ctx)
        val raw = prefs.getString(KEY_PRICE, null)
        val obj = runCatching { raw?.let { JSONObject(it) } }.getOrNull() ?: JSONObject()
        val previous = if (obj.has(key)) obj.optDouble(key, Double.NaN).let { if (it.isNaN()) null else it } else null
        if (price != null) {
            obj.put(key, price)
            prefs.edit().putString(KEY_PRICE, obj.toString()).apply()
        }
        return previous
    }
}
