package com.example.my.data.music

import android.content.Context
import com.example.my.data.WebHit
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 歌曲故事缓存：同一首歌的「AI 生成 / AI 联网整理」结果只请求一次。
 *
 * 两层：
 *  - 内存 map：同一次浏览里退出详情页再回来，立刻命中；
 *  - SharedPreferences：杀进程/重启后仍然命中，避免每次进歌都重新搜网页、重新调模型。
 *
 * 只缓存成功生成的正文；联网失败/资料不足的本地兜底不写缓存，下次进入还能重试。
 * 不设数量/时间上限，由用户在设置页手动清理；歌名/歌手变了 key 会变，自动重新查。
 */
object SongStoryCache {

    data class Entry(
        val story: String,
        val sources: List<WebHit>,
        val at: Long,
        /** 生成这条故事时实际使用的模型端点，例如「智谱 GLM（内置） · glm-4.7-flash」 */
        val model: String = ""
    )

    // 旧版缓存不校验「正文是否真的引用了网页」，继续沿用会把虚假来源带进新版本；
    // 换 v2 让升级后第一首歌重新走一遍可核验的检索与生成。
    private const val PREF = "song_story_cache_v2"
    private const val KEY = "entries"

    private val mem = ConcurrentHashMap<String, Entry>()

    fun key(songId: Long?, title: String, artist: String): String =
        "${songId ?: 0}|$title|$artist"

    fun get(context: Context, key: String): Entry? {
        mem[key]?.let { return it }
        val all = loadAll(context)
        val hit = all.firstOrNull { it.first == key } ?: return null
        mem[key] = hit.second
        return hit.second
    }

    fun put(
        context: Context,
        key: String,
        story: String,
        sources: List<WebHit>,
        model: String = ""
    ) {
        if (story.isBlank()) return
        val entry = Entry(story.trim(), sources, System.currentTimeMillis(), model)
        mem[key] = entry
        val all = loadAll(context).filterNot { it.first == key } + (key to entry)
        saveAll(context, all)
    }

    /** 用户点「重新回答」时丢掉旧结果，下一次进入重新让模型生成，模型不认识才联网 */
    fun remove(context: Context, key: String) {
        mem.remove(key)
        saveAll(context, loadAll(context).filterNot { it.first == key })
    }

    /** 设置页：清空全部歌曲故事缓存 */
    fun clear(context: Context) {
        mem.clear()
        prefs(context).edit().remove(KEY).apply()
    }

    /**
     * 退到后台 / 内存紧张时只释放内存副本；磁盘缓存仍保留，
     * 用户设置里的「清除歌曲故事缓存」不受影响，故事也不会因此丢失。
     */
    fun trimMemory(level: Int) {
        // 与封面缓存一致：只在系统明确内存偏低或 UI 已隐藏时清，避免轻微回调就反复重读磁盘
        if (level >= 10) mem.clear()
    }

    /** 设置页展示：已缓存多少篇故事 */
    fun count(context: Context): Int {
        val raw = prefs(context).getString(KEY, "").orEmpty()
        if (raw.isBlank()) return 0
        return runCatching { JSONArray(raw).length() }.getOrDefault(0)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun loadAll(context: Context): MutableList<Pair<String, Entry>> {
        val raw = prefs(context).getString(KEY, "").orEmpty()
        if (raw.isBlank()) return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val k = o.optString("key")
                    val story = o.optString("story")
                    if (k.isBlank() || story.isBlank()) continue
                    val srcArr = o.optJSONArray("sources") ?: JSONArray()
                    val sources = buildList {
                        for (j in 0 until srcArr.length()) {
                            val s = srcArr.optJSONObject(j) ?: continue
                            add(
                                WebHit(
                                    title = s.optString("title"),
                                    snippet = s.optString("snippet"),
                                    url = s.optString("url"),
                                    site = s.optString("site"),
                                    tier = s.optInt("tier"),
                                    score = s.optDouble("score"),
                                    rank = s.optInt("rank"),
                                    relevance = s.optDouble("relevance")
                                )
                            )
                        }
                    }
                    add(
                        k to Entry(
                            story = story,
                            sources = sources,
                            at = o.optLong("at"),
                            model = o.optString("model")
                        )
                    )
                }
            }.toMutableList()
        }.getOrDefault(mutableListOf())
    }

    private fun saveAll(context: Context, entries: List<Pair<String, Entry>>) {
        runCatching {
            val arr = JSONArray()
            entries.forEach { (k, e) ->
                val o = JSONObject()
                o.put("key", k)
                o.put("story", e.story)
                o.put("at", e.at)
                o.put("model", e.model)
                val srcArr = JSONArray()
                e.sources.forEach { h ->
                    srcArr.put(
                        JSONObject().apply {
                            put("title", h.title)
                            put("snippet", h.snippet)
                            put("url", h.url)
                            put("site", h.site)
                            put("tier", h.tier)
                            put("score", h.score)
                            put("rank", h.rank)
                            put("relevance", h.relevance)
                        }
                    )
                }
                o.put("sources", srcArr)
                arr.put(o)
            }
            prefs(context).edit().putString(KEY, arr.toString()).apply()
        }
    }
}
