package com.example.my.data.shopping

import com.example.my.data.AiApi
import com.example.my.data.AiEndpoint
import com.example.my.data.WebHit
import com.example.my.data.WebSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一条公开报价：来自开放数据或网页搜索，价格可能为空（页面里没写价） */
data class ShopOffer(
    val title: String,
    val site: String,
    val url: String,
    val snippet: String,
    val price: Double?,
    /** ISO 货币代码；网页报价默认人民币 */
    val currency: String = "CNY"
) {
    val priceText: String get() = price?.let { shopMoney(it, currency) }.orEmpty()
}

/** 走势图上的一个点：label 是时间/平台，value 是金额 */
data class ShopPoint(val label: String, val value: Double)

enum class TrendMode {
    /** Open Prices 开放数据集的真实成交价：带日期，可直接当历史走势 */
    OPEN_PRICES,

    /** AI 结合公开资料归纳的近 30 天参考走势（非官方历史价） */
    AI_TIMELINE,

    /** 未接入历史价数据源时，用各平台当前公开报价做的对比图（全是真实抓到的价格） */
    PLATFORM
}

data class ShopTrend(
    val mode: TrendMode,
    val points: List<ShopPoint>,
    val summary: String,
    val advice: String
)

data class ShopResult(
    val query: String,
    val product: String,
    val offers: List<ShopOffer>,
    val trend: ShopTrend?,
    val note: String
)

/** 金额格式化：按币种加符号，千元以上取整并加千分位，小额保留两位有效小数 */
fun shopMoney(value: Double, currency: String = "CNY"): String {
    val symbol = when (currency.uppercase(Locale.US)) {
        "CNY", "RMB" -> "¥"
        "EUR" -> "€"
        "USD" -> "$"
        "GBP" -> "£"
        "JPY" -> "¥"
        else -> currency.uppercase(Locale.US) + " "
    }
    val rounded = Math.round(value * 100) / 100.0
    return if (rounded >= 1000) {
        symbol + String.format(Locale.CHINA, "%,.0f", rounded)
    } else if (rounded == Math.floor(rounded)) {
        symbol + rounded.toInt()
    } else {
        symbol + String.format(Locale.CHINA, "%.2f", rounded).trimEnd('0').trimEnd('.')
    }
}

/**
 * 购物专区的数据层。
 *
 * 数据来源按优先级：
 * 1. **Open Prices**（免费开源、无需 Key）：有包装条码的食品/日用品能拿到带日期的真实
 *    成交价，直接画历史走势，不经过 AI；
 * 2. **Bing 公开网页搜索**：其余商品抓当前公开报价，价格从标题/摘要正则抽取，附原链接；
 * 3. AI 只在前两步只有当前价、没有历史价时，基于公开材料归纳「参考走势」，并强制标注。
 *
 * 结果会进本地缓存（内存 LRU + 单个紧凑 JSON，见 [ShopCache]），同一关键词 6 小时内
 * 不重复联网、不重复写盘。
 */
object ShopPrice {

    private const val AI_FENCE = "```"
    private const val OPEN_NOTE =
        "价格来自 Open Prices 开放数据集（Open Food Facts 社区众包的真实成交记录，ODbL 许可），点击可查看来源。"

    /**
     * 搜索任意商品：开放数据与网页搜索并发，优先用真实历史价；都失败返回 null。
     */
    suspend fun search(
        query: String,
        endpoint: AiEndpoint,
        context: android.content.Context? = null
    ): ShopResult? = withContext(Dispatchers.IO) {
        val q = query.trim().take(60)
        if (q.isBlank()) return@withContext null
        val cacheKey = q.lowercase(Locale.ROOT)
        val cacheDir = context?.cacheDir
        ShopCache.get(cacheDir, cacheKey)?.let { return@withContext it }

        val results = coroutineScope {
            val open = async { runCatching { OpenPrices.lookup(q) }.getOrNull() }
            val web = async {
                listOf(
                    async { runCatching { WebSearch.search("$q 价格", limit = 8) }.getOrDefault(emptyList()) },
                    async { runCatching { WebSearch.search("$q 多少钱 报价", limit = 8) }.getOrDefault(emptyList()) }
                ).awaitAll().flatten()
            }
            open.await() to web.await()
        }
        val open = results.first
        val hits = results.second
            .distinctBy { it.url }
            .sortedWith(compareByDescending<WebHit> { it.relevance }.thenBy { it.rank })
            .take(16)

        // 有开放数据的真实历史价：优先展示，不再让 AI 估算
        if (open != null) {
            return@withContext ShopResult(
                query = q,
                product = open.product,
                offers = open.offers,
                trend = open.trend,
                note = OPEN_NOTE
            ).also { ShopCache.put(cacheDir, cacheKey, it) }
        }

        if (hits.isEmpty()) return@withContext null

        val offers = hits.mapNotNull { hit ->
            val text = "${hit.title} ${hit.snippet}"
            ShopOffer(
                title = hit.title.ifBlank { hit.site },
                site = hit.site.ifBlank { hostOf(hit.url) },
                url = hit.url,
                snippet = hit.snippet,
                price = extractPrice(text)
            )
        }
        val priced = offers.filter { it.price != null }.sortedBy { it.price }
        val unpriced = offers.filter { it.price == null }.take(5)
        val merged = priced + unpriced

        val ai = runCatching { aiTrend(endpoint, q, offers) }.getOrNull()
        val trend = (ai?.second ?: platformTrend(priced))?.let {
            it.copy(
                summary = it.summary.ifBlank { defaultSummary(priced) },
                advice = it.advice.ifBlank { ruleAdvice(priced) }
            )
        }

        ShopResult(
            query = q,
            product = ai?.first ?: q,
            offers = merged,
            trend = trend,
            note = "公开报价来自 Bing 网页搜索，价格与库存以商家页面为准；" +
                "食品/日用品可换成条码对应的商品名，能命中 Open Prices 真实成交价。"
        ).also { ShopCache.put(cacheDir, cacheKey, it) }
    }

    /** 从标题/摘要里抽一个最可信的价格（优先「¥」「到手价」「售价」这类明确写法） */
    fun extractPrice(text: String): Double? {
        if (text.isBlank()) return null
        var best: Pair<Double, Int>? = null
        for ((regex, weight) in PRICE_PATTERNS) {
            regex.findAll(text).forEach { m ->
                val raw = m.groupValues.getOrNull(1).orEmpty().ifBlank {
                    m.groupValues.getOrNull(2).orEmpty()
                }
                val value = raw.toDoubleOrNull() ?: return@forEach
                if (value < 0.5 || value > 5_000_000) return@forEach
                // 带「万」的价格单独换算，避免把 1.2万 当成 1.2 元
                val adjusted = if (m.value.contains("万")) value * 10_000 else value
                if (adjusted > 5_000_000) return@forEach
                val score = weight + when {
                    adjusted in 20.0..200_000.0 -> 1
                    else -> 0
                }
                val current = best
                if (current == null || score > current.second) best = adjusted to score
            }
        }
        return best?.first
    }

    private fun defaultSummary(priced: List<ShopOffer>): String {
        if (priced.isEmpty()) return "暂未从公开网页里抽到明确价格，可以点开下方来源页比价。"
        val values = priced.mapNotNull { it.price }
        val min = values.min()
        val max = values.max()
        val median = sortedMiddle(values)
        return "共 ${priced.size} 条带价结果，区间 ${shopMoney(min)} - ${shopMoney(max)}，" +
            "主流价位约 ${shopMoney(median)}。"
    }

    private fun ruleAdvice(priced: List<ShopOffer>): String {
        if (priced.size < 2) {
            return "报价样本较少，建议点开来源页确认规格、运费与售后后再下单。"
        }
        val values = priced.mapNotNull { it.price }
        val minOffer = priced.filter { it.price != null }.minByOrNull { it.price ?: Double.MAX_VALUE }
        val avg = values.average()
        val cheapest = minOffer?.price ?: avg
        return if (cheapest <= avg * 0.92) {
            "当前最低 ${shopMoney(cheapest)}（${minOffer?.site.orEmpty()}），比均价低 " +
                "${((avg - cheapest) / avg * 100).toInt()}%。下单前确认是否同规格、含运费与保修。"
        } else {
            "各平台价差不大（均价约 ${shopMoney(avg)}），建议按销量、售后和运费综合选择。"
        }
    }

    /** 没有 AI 或 AI 不可用：用真实抓到的平台报价画对比图 */
    private fun platformTrend(priced: List<ShopOffer>): ShopTrend? {
        val points = priced.mapNotNull { offer ->
            offer.price?.let { ShopPoint(offer.site.take(6), it) }
        }.distinctBy { it.label }.take(8)
        if (points.size < 2) return null
        return ShopTrend(
            mode = TrendMode.PLATFORM,
            points = points.sortedBy { it.value },
            summary = "",
            advice = ""
        )
    }

    /** AI 结合公开材料归纳走势；返回「标准品名」与 trend */
    private suspend fun aiTrend(
        endpoint: AiEndpoint,
        query: String,
        offers: List<ShopOffer>
    ): Pair<String, ShopTrend>? {
        if (!endpoint.ready) return null
        val facts = offers.take(12).joinToString("\n") { offer ->
            "${offer.site}|${offer.title.take(60)}|" +
                (offer.price?.let { shopMoney(it, offer.currency) } ?: "无明确价格") + "|" +
                offer.snippet.replace('\n', ' ').take(90)
        }
        if (facts.isBlank()) return null

        val today = SimpleDateFormat("MM-dd", Locale.CHINA).format(Date())
        val raw = AiApi.requestOnce(
            endpoint,
            SYSTEM_PROMPT,
            "商品/关键词：$query\n今天：$today\n公开搜索结果：\n$facts"
        )
        val obj = jsonOf(raw) ?: return null
        val points = obj.optJSONArray("points")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val p = arr.optJSONObject(i) ?: return@mapNotNull null
                val label = p.optString("label").trim().take(10)
                val value = p.optDouble("value", 0.0)
                if (label.isBlank() || value <= 0.0) null else ShopPoint(label, value)
            }.distinctBy { it.label }.take(12)
        }.orEmpty()
        if (points.size < 4) return null

        // 防幻觉：走势点必须落在已抓到价格的合理区间里，明显离谱的整段丢弃
        val known = offers.mapNotNull { it.price }
        if (known.isNotEmpty()) {
            val lo = known.min() * 0.25
            val hi = known.max() * 4.0
            if (points.count { it.value in lo..hi } < 4) return null
        }

        val product = obj.optString("product").trim().take(40).ifBlank { query }
        val summary = obj.optString("summary").trim().take(120)
        val advice = obj.optString("advice").trim().take(120)
        return product to ShopTrend(
            mode = TrendMode.AI_TIMELINE,
            points = points,
            summary = summary,
            advice = advice
        )
    }

    private fun jsonOf(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        val text = raw.replace(AI_FENCE, "").trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }

    private fun hostOf(url: String): String = runCatching {
        java.net.URL(url).host.orEmpty().removePrefix("www.").ifBlank { "网页来源" }
    }.getOrDefault("网页来源")

    private fun sortedMiddle(values: List<Double>): Double =
        values.sorted().let { it[it.size / 2] }

    private val PRICE_PATTERNS = listOf(
        // ¥1299 / ￥1299：最明确的写法
        Regex("""[¥￥]\s*(\d{1,7}(?:\.\d{1,2})?)""") to 3,
        // 到手价 1299 元 / 券后 99 / 售价 1999 / 低至 199
        Regex(
            """(?:到手价|券后价|券后|售价|现价|价格|低至|仅需|起售价|参考价|报价|补贴后)\D{0,6}(\d{1,7}(?:\.\d{1,2})?)\s*(?:元|万)?"""
        ) to 3,
        // 1299 元 / 1.2 万
        Regex("""(\d{1,7}(?:\.\d{1,2})?)\s*(?:元|块)""") to 1
    )

    private val SYSTEM_PROMPT = """
你是购物比价分析师。只能依据用户给出的公开搜索结果，归纳该商品的「参考价格走势」，严禁编造不存在的型号、促销或历史价格。
要求：
1. 先判断这些结果是不是同一件商品；混杂配件/贴膜/壳等低价结果时，只按主机价格归纳。
2. points 给 6-10 个点，按时间从早到晚，label 用 MM-dd（最近 30 天内），value 是人民币整数元。
3. value 必须落在材料给出的价格区间附近；材料完全没有价格时 points 返回空数组。
4. summary 说清价格区间和主流价位（不超过 60 字）；advice 给出是否适合现在买的简短建议（不超过 40 字）。
只输出一行 JSON，不要 markdown、不要解释：
{"product":"标准品名","summary":"...","advice":"...","points":[{"label":"10-01","value":1999}]}
    """.trimIndent()
}

/**
 * 购物结果的本地缓存：内存 LRU + 单个紧凑 JSON 文件。
 *
 * 为什么不用 Room / SQLite：
 * - 这里的数据是「可丢弃的查询缓存」，规模很小（最多 24 条、几十 KB），
 *   SQLite 的页缓存、WAL、索引开销比数据本身还大；
 * - 读写都只在用户发起搜索时发生，一次查询只写一次整文件，不存在高频小写入；
 * - 文件放在 `cacheDir`（系统可随时清理，不占用户长期存储），
 *   只存抽取后的字段数组，不存原始 HTML / 摘要全文，单条记录压到 1-2 KB。
 *
 * 结果：冷启动最多读一次文件，之后全部命中内存；条目按 LRU 淘汰，6 小时过期。
 */
private object ShopCache {
    private const val FILE_NAME = "shop_price_cache.json"
    private const val MAX_ENTRIES = 24
    private const val TTL_MS = 6 * 60 * 60 * 1000L

    private var loaded = false
    private val entries = object : LinkedHashMap<String, Pair<Long, ShopResult>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Pair<Long, ShopResult>>
        ): Boolean = size > MAX_ENTRIES
    }

    @Synchronized
    fun get(dir: File?, key: String): ShopResult? {
        ensureLoaded(dir)
        val entry = entries[key] ?: return null
        if (System.currentTimeMillis() - entry.first > TTL_MS) {
            entries.remove(key)
            return null
        }
        return entry.second
    }

    @Synchronized
    fun put(dir: File?, key: String, value: ShopResult) {
        ensureLoaded(dir)
        entries[key] = System.currentTimeMillis() to value
        if (dir != null) save(dir)
    }

    private fun ensureLoaded(dir: File?) {
        if (loaded) return
        loaded = true
        if (dir == null) return
        val file = File(dir, FILE_NAME)
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("entries") ?: return@runCatching
            val now = System.currentTimeMillis()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val at = o.optLong("t")
                if (now - at > TTL_MS) continue
                val key = o.optString("k")
                val result = decodeResult(o.optJSONObject("r")) ?: continue
                if (key.isNotBlank()) entries[key] = at to result
            }
        }
    }

    private fun save(dir: File) {
        runCatching {
            val arr = JSONArray()
            entries.forEach { (key, value) ->
                arr.put(
                    JSONObject()
                        .put("k", key)
                        .put("t", value.first)
                        .put("r", encodeResult(value.second))
                )
            }
            val file = File(dir, FILE_NAME)
            val tmp = File(dir, "$FILE_NAME.tmp")
            tmp.writeText(JSONObject().put("v", 1).put("entries", arr).toString())
            // 先写临时文件再改名，避免写到一半被杀进程留下半截 JSON
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    private fun encodeResult(r: ShopResult): JSONObject = JSONObject().apply {
        put("q", r.query)
        put("p", r.product)
        put("n", r.note.take(160))
        put(
            "o",
            JSONArray().apply {
                r.offers.forEach { offer ->
                    put(
                        JSONArray().apply {
                            put(offer.title.take(90))
                            put(offer.site.take(24))
                            put(offer.url.take(300))
                            put(offer.price ?: JSONObject.NULL)
                            put(offer.currency)
                            put(offer.snippet.take(80))
                        }
                    )
                }
            }
        )
        val trend = r.trend
        put(
            "tr",
            if (trend == null) JSONObject.NULL else JSONObject().apply {
                put("m", trend.mode.name)
                put("s", trend.summary.take(140))
                put("a", trend.advice.take(140))
                put(
                    "pt",
                    JSONArray().apply {
                        trend.points.forEach { point ->
                            put(JSONArray().put(point.label.take(12)).put(point.value))
                        }
                    }
                )
            }
        )
    }

    private fun decodeResult(o: JSONObject?): ShopResult? {
        if (o == null) return null
        val offers = o.optJSONArray("o")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val a = arr.optJSONArray(i) ?: return@mapNotNull null
                ShopOffer(
                    title = a.optString(0),
                    site = a.optString(1),
                    url = a.optString(2),
                    snippet = a.optString(5),
                    price = if (a.isNull(3)) null else a.optDouble(3, 0.0),
                    currency = a.optString(4).ifBlank { "CNY" }
                )
            }
        }.orEmpty()
        val trend = o.optJSONObject("tr")?.let { t ->
            val mode = runCatching { TrendMode.valueOf(t.optString("m")) }.getOrNull()
                ?: return@let null
            val points = t.optJSONArray("pt")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val p = arr.optJSONArray(i) ?: return@mapNotNull null
                    ShopPoint(p.optString(0), p.optDouble(1, 0.0))
                }
            }.orEmpty()
            if (points.isEmpty()) null
            else ShopTrend(mode, points, t.optString("s"), t.optString("a"))
        }
        return ShopResult(
            query = o.optString("q"),
            product = o.optString("p"),
            offers = offers,
            trend = trend,
            note = o.optString("n")
        )
    }
}
