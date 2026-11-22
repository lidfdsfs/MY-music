package com.example.my.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 「服务器网页索引」过渡功能的开关持久化。
 *
 * 设计约定（过渡阶段）：
 *  - 关闭（默认）：完全走原本地抓取/清洗链路，行为与旧版一致，本文件不参与运行；
 *  - 开启：检索先请求服务端索引（抓取 + 清洗在服务器完成），服务端不可用或返回空时，
 *    调用方自动回退到原本地链路。模型调用始终留在手机端，不经过这里。
 *
 * WebSearch 的检索入口拿不到 Context，因此开关在 Application 启动时由 [init] 载入内存，
 * 业务层用 [isEnabled] 读内存态；写入走 [setEnabled]，同步落 SP 与内存。
 */
object ServerSearchPrefs {
    private const val PREF = "server_search_prefs"
    private const val KEY_ENABLED = "server_web_index"

    @Volatile
    private var enabled: Boolean = false

    /** 进程启动时调用一次，把 SP 里的初始值载入内存。 */
    fun init(ctx: Context) {
        enabled = ctx.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun isEnabled(): Boolean = enabled

    fun setEnabled(ctx: Context, value: Boolean) {
        enabled = value
        ctx.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
    }
}

/**
 * 服务端网页索引客户端。
 *
 * 服务端职责：接收查询 → 在服务器侧抓取候选网页 → 解析/去噪/可信度分级 → 返回清洗后的结果。
 * 手机只做：发查询、解析响应、把结果映射成现有 [WebHit] 喂给同一套上下文/来源展示流程。
 *
 * 失败一律返回空列表（网络异常、非 2xx、解析失败、字段缺失），由调用方回退本地检索，
 * 不在此抛断主流程。
 *
 * 响应契约（服务端后续按此实现即可）：
 * ```
 * {
 *   "hits": [
 *     {
 *       "title": "页面标题",
 *       "snippet": "清洗后的摘要",
 *       "url": "https://...",
 *       "site": "站点名",
 *       "tier": 1,            // 0 未分级；1 官方一手 … 4 低质，可选
 *       "score": 0.82,        // 0~1，可选
 *       "rank": 1,            // 原始位次，可选
 *       "publishedAt": ""     // 发布时间原文，可选
 *     }
 *   ]
 * }
 * ```
 */
object ServerSearch {

    private const val ENDPOINT = "/api/search"
    private const val CONNECT_TIMEOUT_MS = 6_000
    private const val READ_TIMEOUT_MS = 6_000

    /**
     * 请求服务端索引。返回清洗后的命中列表；任何失败返回空列表。
     * @param limit 期望条数，透传给服务端做上限裁剪
     */
    suspend fun searchRemote(
        query: String,
        limit: Int,
        profile: WebSearch.SearchProfile
    ): List<WebHit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val body = JSONObject().apply {
                put("query", query)
                put("limit", limit)
                // 档位提示，供服务端决定抓取广度；本地档位名直接透传
                put("depth", depthTag(profile))
            }
            val conn = (URL(BackendApi.BASE_URL + ENDPOINT).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                BackendApi.token?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) return@withContext emptyList()
                parseHits(JSONObject(text))
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(emptyList())
    }

    /** 把本地档位参数映射成服务端可识别的广度标签。 */
    private fun depthTag(profile: WebSearch.SearchProfile): String = when {
        profile.maxEvidence >= 12 -> "deep"
        profile.maxBodies >= 4 -> "balanced"
        else -> "fast"
    }

    private fun parseHits(root: JSONObject): List<WebHit> {
        val arr: JSONArray = root.optJSONArray("hits") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("url").trim()
                val title = o.optString("title").trim()
                if (url.isBlank() || title.isBlank()) continue
                add(
                    WebHit(
                        title = title,
                        snippet = o.optString("snippet").trim(),
                        url = url,
                        site = o.optString("site").ifBlank { hostOf(url) },
                        tier = o.optInt("tier", 0),
                        score = o.optDouble("score", 0.0),
                        rank = o.optInt("rank", 0),
                        relevance = 0.0,
                        publishedAt = o.optString("publishedAt")
                    )
                )
            }
        }
    }

    /** 站点名兜底：从 URL 取主机名，去掉 www. 前缀。 */
    private fun hostOf(url: String): String = runCatching {
        URL(url).host.removePrefix("www.")
    }.getOrDefault("")
}
