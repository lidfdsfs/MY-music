package com.example.my.data.shopping

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 免费开源的比价数据源：Open Prices（Open Food Facts 社区项目）。
 *
 * - 接口地址：https://prices.openfoodfacts.org/api/v1/prices
 * - 数据许可：Open Database License（ODbL），无需 API Key
 * - 覆盖范围：有包装条码的食品 / 日用品；价格由用户上传小票或货架照片众包而来，
 *   带真实日期与门店，因此可以直接画出「真实成交价走势」，不是估算。
 *
 * 非食品类（手机、家电等）这个库里没有记录，调用方会退回网页公开报价。
 */
internal object OpenPrices {

    private const val UA =
        "MYFootprint/1.0 (Android; shopping price lookup; contact: local-app)"
    private const val OFF_SEARCH =
        "https://search.openfoodfacts.org/search?q="
    private const val PRICES_API = "https://prices.openfoodfacts.org/api/v1/prices"

    data class Snapshot(
        val product: String,
        val offers: List<ShopOffer>,
        val trend: ShopTrend
    )

    /** 查一个商品：先用 Open Food Facts 找到条码，再拉这个条码的真实成交价 */
    suspend fun lookup(query: String): Snapshot? = withContext(Dispatchers.IO) {
        val codes = searchCodes(query)
        if (codes.isEmpty()) return@withContext null

        val offers = mutableListOf<ShopOffer>()
        val dated = mutableListOf<Pair<String, Double>>()
        var productName = query

        for ((code, name) in codes.take(2)) {
            val items = pricesOf(code) ?: continue
            items.forEach { item ->
                val price = item.optDouble("price", 0.0)
                val date = item.optString("date").take(10)
                if (price <= 0.0 || date.isBlank()) return@forEach
                val currency = item.optString("currency").ifBlank { "EUR" }
                val shop = item.optJSONObject("location")
                    ?.optString("osm_name").orEmpty().ifBlank { "社区上传" }
                val title = item.optJSONObject("product")
                    ?.optString("product_name").orEmpty()
                    .ifBlank { name }.ifBlank { query }
                if (name.isNotBlank()) productName = name
                offers += ShopOffer(
                    title = title,
                    site = "Open Prices",
                    url = "https://prices.openfoodfacts.org/app/prices?product_code=$code",
                    snippet = "$date · $shop",
                    price = price,
                    currency = currency
                )
                dated += date to price
            }
        }
        if (offers.isEmpty()) return@withContext null

        // 同一天同一商品可能有多条记录：按日期取最后一条，避免锯齿
        val byDate = LinkedHashMap<String, Double>()
        dated.sortedBy { it.first }.forEach { (date, price) -> byDate[date] = price }
        if (byDate.size < 3) return@withContext null

        val points = byDate.entries.toList().takeLast(12).map { (date, price) ->
            ShopPoint(date.takeLast(5), price)
        }
        val currency = offers.firstOrNull()?.currency ?: "EUR"
        val values = points.map { it.value }
        val trend = ShopTrend(
            mode = TrendMode.OPEN_PRICES,
            points = points,
            summary = "Open Prices 收录的 ${byDate.size} 条真实成交记录，" +
                "区间 ${shopMoney(values.min(), currency)} - ${shopMoney(values.max(), currency)}。",
            advice = "这是社区上传的历史成交价，能反映真实波动；" +
                "不同门店/地区会有差异，购买前请以商家页面为准。"
        )
        Snapshot(productName, offers.distinctBy { it.url + it.price }, trend)
    }

    /** Open Food Facts 全文搜索（Search-a-licious）→ (条码, 品名) */
    private fun searchCodes(query: String): List<Pair<String, String>> {
        val url = "$OFF_SEARCH${enc(query)}&page_size=4&fields=code,product_name,brands"
        val json = getJson(url) ?: return emptyList()
        val products = json.optJSONArray("hits") ?: return emptyList()
        return (0 until products.length()).mapNotNull { i ->
            val p = products.optJSONObject(i) ?: return@mapNotNull null
            val code = p.optString("code").trim()
            if (code.isBlank()) return@mapNotNull null
            val brands = when (val raw = p.opt("brands")) {
                is org.json.JSONArray ->
                    (0 until raw.length()).joinToString(" ") { raw.optString(it) }
                else -> p.optString("brands")
            }
            val name = listOf(p.optString("product_name"), brands)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .trim()
            code to name
        }.distinctBy { it.first }
    }

    /** 某个条码的真实成交价记录（按日期倒序取最近 30 条） */
    private fun pricesOf(code: String): List<JSONObject>? {
        val url = "$PRICES_API?product_code=${enc(code)}&size=30&order_by=date"
        val json = getJson(url) ?: return null
        val items = json.optJSONArray("items") ?: return null
        return (0 until items.length()).mapNotNull { items.optJSONObject(it) }
    }

    private fun getJson(url: String): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4_000
                readTimeout = 7_000
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun enc(text: String): String = URLEncoder.encode(text, "UTF-8")
}
