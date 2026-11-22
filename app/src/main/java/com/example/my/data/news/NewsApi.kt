package com.example.my.data.news

import com.example.my.data.ticket.McpSse
import com.example.my.data.ticket.TicketSecurity
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * 资讯聚合：早报 / 热搜榜 / 历史上的今天。
 *
 * 数据来源都是公开接口，全部只读：
 * - 60 秒早报：先走用户给的 60sapi 域名，失败后回落到 60s 官方镜像与静态日更文件；
 * - 热榜：先尝试用户给的 dailyhotlist POST 聚合，失败后走 hot MCP（微博/知乎/抖音/B站/头条/百度/贴吧/掘金），
 *   最后追加 V2EX 热帖、GitHub 趋势与 Hacker News 三个免 Key 的公开 JSON 源；
 * - 历史今天：60s 的 today_in_history，失败时明确报错，不用假数据凑数。
 *
 * 每次请求都经过 [NewsHttp] 的固定主机、HTTPS、禁跳转、拒收 HTML 的收紧口径。
 */
object NewsApi {

    private const val DAILY_ALL = "https://api.dailyhotlist.cn/all"
    private const val SIXTY_PRIMARY = "https://api.60sapi.xyz/api/60s"
    private const val SIXTY_API = "https://60s-api.viki.moe/v2/60s"
    private const val SIXTY_FALLBACK = "https://60s.b23.run/v2/60s"
    private const val SIXTY_STATIC = "https://60s-static.viki.moe/60s/"
    private const val HISTORY_PRIMARY = "https://api.60sapi.xyz/api/today_in_history"
    private const val HISTORY_FALLBACK = "https://60s.b23.run/v2/today_in_history"
    private const val HOT_ENDPOINT = "https://mcp.pianam.cn/hot-mcp/mcp"
    private const val HOT_HOST = "mcp.pianam.cn"
    private const val V2EX_HOT = "https://www.v2ex.com/api/topics/hot.json"
    private const val GITHUB_SEARCH =
        "https://api.github.com/search/repositories?q=created:%3E"
    private const val HN_TOP =
        "https://hn.algolia.com/api/v1/search?tags=front_page&hitsPerPage=8"

    private val ALLOWED = setOf(
        "api.60sapi.xyz",
        "60s-api.viki.moe",
        "60s.b23.run",
        "60s-static.viki.moe",
        "api.dailyhotlist.cn",
        "www.v2ex.com",
        "api.github.com",
        "hn.algolia.com",
        HOT_HOST
    )

    private val BOARD_NAMES = linkedMapOf(
        "weibo" to "微博",
        "zhihu" to "知乎",
        "douyin" to "抖音",
        "bilibili" to "B站",
        "toutiao" to "头条",
        "baidu" to "百度",
        "tieba" to "贴吧",
        "juejin" to "掘金",
        "v2ex" to "V2EX",
        "github" to "GitHub 趋势",
        "hackernews" to "Hacker News"
    )
    private val BOARD_ORDER = BOARD_NAMES.keys.toList()

    private const val TTL_MS = 5 * 60 * 1000L

    @Volatile private var briefCache: Pair<Long, NewsBrief>? = null
    @Volatile private var historyCache: Pair<Long, HistoryDigest>? = null
    @Volatile private var hotCache: Pair<Long, List<HotBoard>>? = null

    fun brief(force: Boolean = false): NewsBrief {
        val cached = briefCache
        if (!force && cached != null && System.currentTimeMillis() - cached.first < TTL_MS) {
            return cached.second
        }
        val fresh = fetchBrief()
        briefCache = System.currentTimeMillis() to fresh
        return fresh
    }

    fun history(force: Boolean = false): HistoryDigest {
        val cached = historyCache
        if (!force && cached != null && System.currentTimeMillis() - cached.first < TTL_MS) {
            return cached.second
        }
        val fresh = fetchHistory()
        historyCache = System.currentTimeMillis() to fresh
        return fresh
    }

    fun hotAll(force: Boolean = false): List<HotBoard> {
        val cached = hotCache
        if (!force && cached != null && System.currentTimeMillis() - cached.first < TTL_MS) {
            return cached.second
        }
        val fresh = fetchHotAll()
        hotCache = System.currentTimeMillis() to fresh
        return fresh
    }

    // ---------- 早报 ----------

    private fun fetchBrief(): NewsBrief {
        val endpoints = listOf(
            SIXTY_PRIMARY to "60s API",
            SIXTY_API to "60s API",
            SIXTY_FALLBACK to "60s API 镜像"
        )
        var lastError: Throwable? = null
        for ((url, source) in endpoints) {
            try {
                return parseBrief(NewsHttp.getJson(url, ALLOWED), source)
            } catch (e: Exception) {
                lastError = e
            }
        }
        // 最后一条兜底：静态日更 JSON（没有 date 包装，结构最稳定）。
        try {
            val date = today()
            return parseBrief(
                NewsHttp.getJson("$SIXTY_STATIC$date.json", ALLOWED),
                "60s 静态日更"
            )
        } catch (e: Exception) {
            lastError = e
        }
        throw NewsException(
            (lastError as? NewsException)?.message ?: "早报暂时拉取失败，请稍后再试",
            lastError
        )
    }

    fun parseBrief(raw: String, source: String = "60s API"): NewsBrief {
        val root = obj(raw)
        val data = root.obj("data") ?: root
        val date = data.text("date").ifBlank { today() }
        val array = data.array("news") ?: root.array("news")
        if (array == null) throw NewsException("早报数据格式异常")
        val items = array.mapNotNull { it.asStringOrNull() }
            .map { TicketSecurity.sanitize(it, 220) }
            .filter { it.isNotBlank() }
            .take(40)
        if (items.isEmpty()) throw NewsException("今天还没有早报内容")
        return NewsBrief(date = cleanDate(date), items = items, source = source)
    }

    // ---------- 历史今天 ----------

    private fun fetchHistory(): HistoryDigest {
        val endpoints = listOf(HISTORY_PRIMARY, HISTORY_FALLBACK)
        var lastError: Throwable? = null
        for (url in endpoints) {
            try {
                return parseHistory(NewsHttp.getJson(url, ALLOWED))
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw NewsException(
            (lastError as? NewsException)?.message ?: "历史上的今天暂时拉取失败",
            lastError
        )
    }

    fun parseHistory(raw: String): HistoryDigest {
        val root = obj(raw)
        val data = root.obj("data") ?: root
        val itemsArr = data.array("items") ?: root.array("items")
            ?: throw NewsException("历史数据格式异常")
        val items = itemsArr.mapNotNull { el ->
            val o = el.asJsonObjectOrNull() ?: return@mapNotNull null
            val title = TicketSecurity.sanitize(o.text("title"), 90)
            if (title.isBlank()) return@mapNotNull null
            HistoryEvent(
                year = TicketSecurity.sanitize(o.text("year"), 12),
                title = title,
                description = TicketSecurity.sanitize(o.text("description"), 220),
                link = safeUrl(o.text("link")),
                type = TicketSecurity.sanitize(o.text("event_type"), 18)
            )
        }.take(40)
        if (items.isEmpty()) throw NewsException("今天没有历史事件内容")
        return HistoryDigest(
            date = cleanDate(data.text("date")).ifBlank { "今天" },
            items = items
        )
    }

    // ---------- 热榜 ----------

    private fun fetchHotAll(): List<HotBoard> {
        // 三个免 Key 源先并发启动，再拉主榜单；任一慢源最多等 8 秒，不拖住整个热榜。
        val v2exFuture = CompletableFuture.supplyAsync { fetchV2exHot() }
        val githubFuture = CompletableFuture.supplyAsync { fetchGithubTrending() }
        val hackerNewsFuture = CompletableFuture.supplyAsync { fetchHackerNews() }

        val base = fetchBaseHot()
        // V2EX 在部分网络会握手超时，单独收紧到 4 秒；GitHub / HN 给 8 秒。
        val extras = listOf(
            v2exFuture to 4L,
            githubFuture to 8L,
            hackerNewsFuture to 8L
        ).mapNotNull { (future, seconds) ->
            runCatching { future.get(seconds, TimeUnit.SECONDS) }.getOrNull()
        }
        val merged = (base + extras)
            .distinctBy { it.id }
            .sortedBy {
                BOARD_ORDER.indexOf(it.id).let { index -> if (index < 0) 99 else index }
            }
        if (merged.isEmpty()) throw NewsException("热榜暂时拉取失败，请稍后再试")
        return merged
    }

    private fun fetchBaseHot(): List<HotBoard> {
        // 用户给的每日热榜聚合接口：优先尝试。域名失效/结构不兼容时静默回落到 MCP。
        runCatching {
            val raw = NewsHttp.postJson(DAILY_ALL, ALLOWED, "{}")
            parseDailyHot(raw)
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        runCatching {
            val obj = McpSse.callTool(
                HOT_ENDPOINT,
                setOf(HOT_HOST),
                "query_hot_trending",
                JsonObject().apply {
                    addProperty("platform", "all")
                }
            )
            parseHotAll(obj.toString())
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        // 最后兜底：60s 的五个平台热榜（和用户给的 /api/hot?type=... 同一数据源形态）。
        return fetchHotFromSixty()
    }

    private fun fetchV2exHot(): HotBoard? {
        val raw = NewsHttp.getJson(V2EX_HOT, ALLOWED)
        return parseV2exHot(raw)
    }

    /** V2EX 公开热帖：数组结构，title/url/replies 都是现成字段。 */
    fun parseV2exHot(raw: String): HotBoard {
        val root = runCatching { JsonParser.parseString(raw) }.getOrNull()
        val array = root?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw NewsException("V2EX 热榜数据格式异常")
        val items = array.mapIndexedNotNull { index, el ->
            val o = el.asJsonObjectOrNull() ?: return@mapIndexedNotNull null
            val title = TicketSecurity.sanitize(o.text("title"), 120)
            if (title.isBlank()) return@mapIndexedNotNull null
            HotItem(
                rank = index + 1,
                title = title,
                hot = o.long("replies")?.takeIf { it > 0 },
                url = safeUrl(o.text("url"))
            )
        }.take(10)
        if (items.isEmpty()) throw NewsException("V2EX 热榜暂无内容")
        return HotBoard(id = "v2ex", name = BOARD_NAMES["v2ex"] ?: "V2EX", items = items)
    }

    private fun fetchGithubTrending(): HotBoard? {
        val since = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(Date(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000))
        val url = "$GITHUB_SEARCH$since&sort=stars&order=desc&per_page=8"
        return parseGithubTrending(NewsHttp.getJson(url, ALLOWED))
    }

    /** GitHub 近 7 天新建仓库按星标排序；单次请求、无 Key、只读。 */
    fun parseGithubTrending(raw: String): HotBoard {
        val root = obj(raw)
        val array = root.array("items") ?: throw NewsException("GitHub 趋势数据格式异常")
        val items = array.mapIndexedNotNull { index, el ->
            val o = el.asJsonObjectOrNull() ?: return@mapIndexedNotNull null
            val title = TicketSecurity.sanitize(o.text("full_name"), 90)
            if (title.isBlank()) return@mapIndexedNotNull null
            HotItem(
                rank = index + 1,
                title = title,
                hot = o.long("stargazers_count")?.takeIf { it > 0 },
                url = safeUrl(o.text("html_url"))
            )
        }.take(8)
        if (items.isEmpty()) throw NewsException("GitHub 趋势暂无内容")
        return HotBoard(
            id = "github",
            name = BOARD_NAMES["github"] ?: "GitHub 趋势",
            items = items
        )
    }

    private fun fetchHackerNews(): HotBoard? {
        val raw = NewsHttp.getJson(HN_TOP, ALLOWED)
        return parseHackerNews(raw)
    }

    /** Hacker News 首页：Algolia 公共只读镜像，一次请求返回标题、分数与讨论链接。 */
    fun parseHackerNews(raw: String): HotBoard {
        val root = obj(raw)
        val array = root.array("hits") ?: throw NewsException("Hacker News 数据格式异常")
        val items = array.mapIndexedNotNull { index, el ->
            val o = el.asJsonObjectOrNull() ?: return@mapIndexedNotNull null
            val title = TicketSecurity.sanitize(o.text("title"), 140)
            if (title.isBlank()) return@mapIndexedNotNull null
            val objectId = o.text("objectID").filter { it.isDigit() }
            val discussion = objectId.takeIf { it.isNotBlank() }
                ?.let { "https://news.ycombinator.com/item?id=$it" }
                .orEmpty()
            HotItem(
                rank = index + 1,
                title = title,
                hot = o.long("points")?.takeIf { it > 0 },
                url = safeUrl(o.text("url")).ifBlank { discussion }
            )
        }.take(8)
        if (items.isEmpty()) throw NewsException("Hacker News 暂无内容")
        return HotBoard(
            id = "hackernews",
            name = BOARD_NAMES["hackernews"] ?: "Hacker News",
            items = items
        )
    }


    private fun fetchHotFromSixty(): List<HotBoard> {
        val ids = listOf("weibo", "zhihu", "douyin", "bilibili", "toutiao")
        return ids.mapNotNull { id ->
            listOf(
                "https://api.60sapi.xyz/api/hot?type=$id",
                "https://60s.b23.run/v2/$id"
            ).firstNotNullOfOrNull { url ->
                runCatching {
                    parseSixtyHot(NewsHttp.getJson(url, ALLOWED), id)
                }.getOrNull()
            }
        }
    }

    private fun parseSixtyHot(raw: String, id: String): HotBoard? {
        val root = obj(raw)
        val data = root.array("data") ?: return null
        val items = parseHotItems(data)
        if (items.isEmpty()) return null
        return HotBoard(id = id, name = BOARD_NAMES[id] ?: id, items = items)
    }

    /** 解析 hot MCP 的 all 结构：{platforms:{weibo:[...], zhihu:[...]}}。 */
    fun parseHotAll(raw: String): List<HotBoard> {
        val root = obj(raw)
        val platforms = root.obj("platforms") ?: root.obj("data")?.obj("platforms")
            ?: throw NewsException("热榜数据格式异常")
        val boards = platforms.entrySet().mapNotNull { (key, value) ->
            val arr = value as? JsonArray ?: return@mapNotNull null
            val items = parseHotItems(arr)
            if (items.isEmpty()) return@mapNotNull null
            HotBoard(
                id = key,
                name = BOARD_NAMES[key] ?: key,
                items = items,
                updatedAt = root.text("updated_at")
            )
        }
        // 热榜顺序按产品预设排，未识别的平台追加在后。
        return boards.sortedBy {
            BOARD_ORDER.indexOf(it.id).let { index -> if (index < 0) 99 else index }
        }
    }

    /** 解析 hot MCP 单平台结构：{platform, updated_at, items:[...]}。 */
    fun parseHotBoard(raw: String): HotBoard {
        val root = obj(raw)
        val id = root.text("platform").ifBlank { "weibo" }
        val items = parseHotItems(
            root.array("items") ?: throw NewsException("热榜数据格式异常")
        )
        if (items.isEmpty()) throw NewsException("这个榜单暂时没有内容")
        return HotBoard(
            id = id,
            name = BOARD_NAMES[id] ?: id,
            items = items,
            updatedAt = root.text("updated_at")
        )
    }

    /**
     * 用户给的 dailyhotlist 接口结构没有公开文档，这里按今日热榜类 API 的常见形态做
     * 宽松解析：data 可以是数组，也可以是 {平台名: 列表}。解析失败由调用方回落到 MCP。
     */
    fun parseDailyHot(raw: String): List<HotBoard> {
        val root = obj(raw)
        val data = root.get("data") ?: root
        val boards = mutableListOf<HotBoard>()
        if (data is JsonArray) {
            data.forEach { el ->
                val o = el.asJsonObjectOrNull() ?: return@forEach
                val arr = o.array("data") ?: o.array("list") ?: o.array("items")
                    ?: return@forEach
                val id = o.text("key").ifBlank {
                    o.text("slug").ifBlank { o.text("name") }
                }
                val name = o.text("name").ifBlank { BOARD_NAMES[id] ?: id }
                val items = parseHotItems(arr)
                if (items.isNotEmpty()) {
                    boards += HotBoard(id, name, items, o.text("update_time").ifBlank {
                        o.text("updated_at")
                    })
                }
            }
        } else if (data is JsonObject) {
            data.entrySet().forEach { (key, value) ->
                val arr = value as? JsonArray ?: return@forEach
                val items = parseHotItems(arr)
                if (items.isNotEmpty()) {
                    boards += HotBoard(key, BOARD_NAMES[key] ?: key, items)
                }
            }
        }
        return boards.sortedBy {
            BOARD_ORDER.indexOf(it.id).let { index -> if (index < 0) 99 else index }
        }
    }

    private fun parseHotItems(array: JsonArray): List<HotItem> {
        return array.mapNotNull { el ->
            val o = el.asJsonObjectOrNull() ?: return@mapNotNull null
            val title = TicketSecurity.sanitize(o.text("title"), 120)
            if (title.isBlank()) return@mapNotNull null
            val hot = o.long("hot") ?: o.long("hot_value") ?: o.long("heat")
                ?: o.long("hotValue") ?: o.long("num")
            HotItem(
                rank = o.int("rank") ?: o.int("index") ?: (array.indexOf(el) + 1),
                title = title,
                hot = hot?.takeIf { it > 0 },
                url = safeUrl(o.text("url").ifBlank {
                    o.text("link").ifBlank {
                        o.text("mobil_url").ifBlank { o.text("mobile_url") }
                    }
                })
            )
        }.take(50)
    }

    // ---------- 解析小工具 ----------

    private fun obj(raw: String): JsonObject =
        runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            ?: throw NewsException("资讯返回内容无法解析")

    private fun JsonElement?.asJsonObjectOrNull(): JsonObject? =
        this?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key).asJsonObjectOrNull()

    private fun JsonObject.array(key: String): JsonArray? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.text(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()

    private fun JsonObject.long(key: String): Long? = runCatching {
        get(key)?.takeIf { it.isJsonPrimitive }?.asLong
    }.getOrNull()

    private fun JsonObject.int(key: String): Int? = runCatching {
        get(key)?.takeIf { it.isJsonPrimitive }?.asInt
    }.getOrNull()

    private fun JsonElement.asStringOrNull(): String? =
        if (isJsonPrimitive) asString else null

    private fun safeUrl(raw: String): String {
        val cleaned = TicketSecurity.sanitize(raw, 2000).trim()
        // App 已关闭明文流量，只把 HTTPS 链接交给内置浏览器；http 链接回落到搜索。
        if (!cleaned.startsWith("https://", ignoreCase = true)) return ""
        return cleaned
    }

    private fun cleanDate(raw: String): String =
        TicketSecurity.sanitize(raw, 12).replace("/", "-")

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
