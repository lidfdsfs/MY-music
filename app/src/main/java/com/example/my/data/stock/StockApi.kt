package com.example.my.data.stock

import com.example.my.data.WebPrivacy
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

class StockException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 行情所属市场 */
enum class StockMarket { A_SHARE, HK, US, FUND }

/** 一条指数 / ETF 报价 */
data class MarketQuote(
    val code: String,
    val name: String,
    val price: Double,
    val changePct: Double,
    val market: StockMarket
)

/** 热门股（东方财富涨幅榜一条） */
data class HotStock(
    val code: String,
    val name: String,
    val price: Double,
    val changePct: Double,
    val turnover: Double // 成交额，单位：元
)

/** 一次拉取的完整快照 */
data class MarketSnapshot(
    val aShare: List<MarketQuote>,
    val hk: List<MarketQuote>,
    val us: List<MarketQuote>,
    val funds: List<MarketQuote>,
    val hot: List<HotStock>,
    val updatedAt: String,
    val fromCache: Boolean = false
)

/** 五档里的一档 */
data class OrderLevel(val price: Double, val volume: Long)

/** 分时上的一个点（车次时间 + 价格 + 均价） */
data class MinutePoint(val label: String, val price: Double, val avg: Double)

/** 日 K 一根 */
data class Candle(
    val date: String,
    val open: Double,
    val close: Double,
    val high: Double,
    val low: Double,
    val volume: Double
)

/** 一只票 / 指数的详情：报价 + 五档 + 分时 + K 线 + 估值与换手 */
data class StockDetail(
    val code: String,
    val name: String,
    val market: StockMarket,
    val price: Double,
    val prevClose: Double,
    val changePct: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val volumeHand: Long,
    val turnoverYuan: Double,
    val bid: List<OrderLevel>,
    val ask: List<OrderLevel>,
    val minute: List<MinutePoint>,
    val candles: List<Candle>,
    val updatedAt: String,
    /** 换手率 %（ETF / 指数可能没有） */
    val turnoverRate: Double = 0.0,
    /** 市盈率 TTM */
    val pe: Double = 0.0,
    /** 市净率 */
    val pb: Double = 0.0,
    /** 流通市值（亿元） */
    val floatCapYi: Double = 0.0,
    /** 总市值（亿元） */
    val totalCapYi: Double = 0.0,
    /** 量比 */
    val volumeRatio: Double = 0.0,
    /** 振幅 % */
    val amplitude: Double = 0.0,
    val limitUp: Double = 0.0,
    val limitDown: Double = 0.0,
    /** 均价 */
    val avgPrice: Double = 0.0
)

/** 搜索命中的一个标的 */
enum class StockKind { STOCK, FUND, INDEX, OTHER }

/** 板块行情（东方财富行业/概念板块） */
data class BoardRow(
    val code: String,
    val name: String,
    val price: Double,
    val changePct: Double,
    val turnover: Double
)

/** 一条公告 */
data class Announcement(
    val title: String,
    val date: String,
    val url: String
)

data class StockHit(
    /** 带交易所前缀的代码，如 sh600519，可直接进详情 */
    val code: String,
    val name: String,
    val kind: StockKind,
    val market: StockMarket
)

/**
 * 免费行情数据源（不依赖任何 API Key）：
 *
 * - 指数 / ETF：腾讯财经 `qt.gtimg.cn` 批量报价，一次请求拿全部代码，GBK 编码；
 * - 热门股：东方财富 `push2.eastmoney.com` 公开涨幅榜（A 股全市场，按涨跌幅降序）。
 *
 * 两个接口都是公开网页在用的免费接口，只取行情数字，不做任何交易动作。
 * 行情可能是延迟数据，仅作参考。所有失败都收敛成 StockException，
 * 上层据此显示「行情暂不可用」而不是崩掉。
 */
object StockApi {

    const val A_INDEX_CODES = "sh000001,sz399001,sz399006,sh000300,sh000688,sh000905"
    const val HK_INDEX_CODES = "hkHSI,hkHSTECH,hkHSCEI"
    const val US_INDEX_CODES = "usDJI,usIXIC,usINX"

    private val fundCodes = listOf(
        "sh510300", "sh510500", "sh510050", "sz159915", "sh588000",
        "sh518880", "sz159920", "sh513100", "sh513500", "sh512880"
    )

    private val tencentHosts = setOf("qt.gtimg.cn")
    private val eastmoneyHosts = setOf("push2.eastmoney.com")
    private val chartHosts = setOf("web.ifzq.gtimg.cn")
    private val searchHosts = setOf("smartbox.gtimg.cn")
    private val boardHosts = setOf("push2.eastmoney.com")
    private val announceHosts = setOf("np-anotice-stock.eastmoney.com")

    private const val MAX_BODY = 256 * 1024
    private const val CACHE_MS = 20_000L

    private var cached: MarketSnapshot? = null
    private var cachedAt = 0L

    /** 首屏用的小快照：A 股三指数 + 恒生 + 纳指，一次请求就够 */
    fun headlineQuotes(): List<MarketQuote> {
        val codes = "sh000001,sz399001,sz399006,hkHSI,usIXIC"
        val parsed: Map<String, MarketQuote> = runCatching {
            parseTencent(fetchTencent(codes), codes.split(","))
        }.getOrElse { emptyMap() }
        return codes.split(",").mapNotNull { parsed[it.trim()] }
    }

    /**
     * 批量报价：给自选 / 持仓估值用，一次请求拿回全部代码。
     * 缺哪只就少哪只，不补假数据。
     */
    fun quotes(rawCodes: List<String>): List<MarketQuote> {
        val codes = rawCodes.map { tencentCode(it) }.filter { it.isNotBlank() }.distinct()
        if (codes.isEmpty()) return emptyList()
        val joined = codes.joinToString(",")
        val parsed = runCatching { parseTencent(fetchTencent(joined), codes) }.getOrElse { emptyMap() }
        return codes.mapNotNull { parsed[it] }
    }

    /** 分时线：给列表里的小迷你走势图用（同一次打开只取前几行，避免把接口打爆） */
    fun minute(rawCode: String): List<MinutePoint> =
        runCatching { fetchMinute(tencentCode(rawCode)) }.getOrElse { emptyList() }

    /** A 股涨幅榜（东方财富公开榜，25 条，带成交额） */
    fun aShareList(): List<HotStock> = runCatching { fetchEastmoneyHot() }.getOrElse { emptyList() }

    /** 行业板块榜：涨跌幅排序的行业/概念板块 */
    fun boards(): List<BoardRow> = runCatching { fetchBoards() }.getOrElse { emptyList() }

    /**
     * 个股 / 基金公告：东方财富公告接口，按代码查最近 10 条。
     * 返回的是原始公告标题与日期，详情链接指向东财公告页。
     */
    fun announcements(rawCode: String, limit: Int = 10): List<Announcement> {
        val code = tencentCode(rawCode).removePrefix("sh").removePrefix("sz").removePrefix("bj")
        if (code.length != 6 || !code.all { it.isDigit() }) return emptyList()
        val url = "https://np-anotice-stock.eastmoney.com/api/security/ann?" +
            "sr=-1&page_size=$limit&page_index=1&ann_type=A&client_source=web&stock_list=$code"
        val raw = fetchText(url, announceHosts, gbk = false)
        val root = JSONObject(raw)
        val list = root.optJSONObject("data")?.optJSONArray("list") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val row = list.optJSONObject(i) ?: return@mapNotNull null
            val title = row.optString("title").trim()
            val artCode = row.optString("art_code").trim()
            val date = row.optString("notice_date").orEmpty().take(10)
            if (title.isBlank() || artCode.isBlank()) return@mapNotNull null
            Announcement(
                title = title.replace("<em>", "").replace("</em>", ""),
                date = date,
                url = "https://data.eastmoney.com/notices/detail/$code/$artCode.html"
            )
        }
    }

    /** 完整快照：指数 + 热门股；20 秒内复用，避免进页面时重复打接口 */
    fun snapshot(): MarketSnapshot {
        val now = System.currentTimeMillis()
        cached?.let { if (now - cachedAt < CACHE_MS) return it.copy(fromCache = true) }

        val allCodes = listOf(
            A_INDEX_CODES, HK_INDEX_CODES, US_INDEX_CODES, fundCodes.joinToString(",")
        ).joinToString(",")
        val quotes = parseTencent(fetchTencent(allCodes), allCodes.split(","))

        val hot = runCatching { fetchEastmoneyHot() }.getOrElse { emptyList() }

        val snap = MarketSnapshot(
            aShare = pick(quotes, A_INDEX_CODES),
            hk = pick(quotes, HK_INDEX_CODES),
            us = pick(quotes, US_INDEX_CODES),
            funds = fundCodes.mapNotNull { quotes[it] }.sortedByDescending { it.changePct },
            hot = hot,
            updatedAt = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())
        )
        cached = snap
        cachedAt = now
        return snap
    }

    private fun pick(quotes: Map<String, MarketQuote>, codes: String): List<MarketQuote> =
        codes.split(",").mapNotNull { quotes[it.trim()] }

    // ---------------- 详情：报价 + 五档 + 分时 + 日 K ----------------

    /**
     * 拉一只个股 / 指数的详情。
     *
     * 报价走腾讯快照（自带五档买卖价量），分时走 `app/minute`，日 K 走 `fqkline`。
     * 分时和 K 线任何一路失败都不影响报价——宁可少张图，也不要整页报错。
     */
    fun detail(rawCode: String): StockDetail {
        val code = tencentCode(rawCode)
        val fields = quoteFields(code)
        val name = fields.getOrNull(1).orEmpty().ifBlank { code }
        val price = fields.getOrNull(3)?.toDoubleOrNull() ?: 0.0
        val prevClose = fields.getOrNull(4)?.toDoubleOrNull() ?: 0.0
        val pct = fields.getOrNull(32)?.toDoubleOrNull()
            ?: if (prevClose > 0) (price - prevClose) / prevClose * 100 else 0.0

        val bid = mutableListOf<OrderLevel>()
        val ask = mutableListOf<OrderLevel>()
        // 买一..买五：f9..f18；卖一..卖五：f19..f28，成对取价与量
        for (i in 0 until 5) {
            val bidPrice = fields.getOrNull(9 + i * 2)?.toDoubleOrNull() ?: 0.0
            val bidVol = fields.getOrNull(10 + i * 2)?.toLongOrNull() ?: 0L
            if (bidPrice > 0 && bidVol > 0) bid += OrderLevel(bidPrice, bidVol)
            val askPrice = fields.getOrNull(19 + i * 2)?.toDoubleOrNull() ?: 0.0
            val askVol = fields.getOrNull(20 + i * 2)?.toLongOrNull() ?: 0L
            if (askPrice > 0 && askVol > 0) ask += OrderLevel(askPrice, askVol)
        }

        val minute = runCatching { fetchMinute(code) }.getOrElse { emptyList() }
        val candles = runCatching { fetchCandles(code) }.getOrElse { emptyList() }

        return StockDetail(
            code = code,
            name = name,
            market = marketOf(code),
            price = price,
            prevClose = prevClose,
            changePct = pct,
            open = fields.getOrNull(5)?.toDoubleOrNull() ?: 0.0,
            high = fields.getOrNull(33)?.toDoubleOrNull() ?: 0.0,
            low = fields.getOrNull(34)?.toDoubleOrNull() ?: 0.0,
            volumeHand = fields.getOrNull(36)?.toLongOrNull() ?: 0L,
            turnoverYuan = (fields.getOrNull(37)?.toDoubleOrNull() ?: 0.0) * 1e4,
            bid = bid,
            ask = ask,
            minute = minute,
            candles = candles,
            updatedAt = fields.getOrNull(30)?.let { clockText(it) } ?: "",
            // 38 换手率 / 39 市盈率 / 43 振幅 / 44 流通市值(亿) / 45 总市值(亿)
            // 46 市净率 / 47 涨停 / 48 跌停 / 49 量比 / 51 均价：指数与部分 ETF 这些位是空串
            turnoverRate = fields.num(38),
            pe = fields.num(39),
            pb = fields.num(46),
            floatCapYi = fields.num(44),
            totalCapYi = fields.num(45),
            volumeRatio = fields.num(49),
            amplitude = fields.num(43),
            limitUp = fields.num(47),
            limitDown = fields.num(48),
            avgPrice = fields.num(51)
        )
    }

    /** 安全取字段：越界或空串都当 0，不让一个缺失字段把整页拖垮 */
    private fun List<String>.num(index: Int): Double =
        getOrNull(index)?.trim()?.toDoubleOrNull() ?: 0.0

    /**
     * 按代码 / 名称 / 拼音搜股票、ETF、指数。
     *
     * 腾讯 `smartbox` 是网页搜索框背后的联想接口，返回 `v_hint` 里用 `^` 分隔多条、
     * `~` 分隔字段（市场 ~ 代码 ~ 名称 ~ 拼音 ~ 类型），名称是 `\uXXXX` 转义，需要手工还原。
     */
    fun search(raw: String): List<StockHit> {
        val q = raw.trim()
        if (q.isEmpty()) return emptyList()
        val encoded = java.net.URLEncoder.encode(q, "UTF-8")
        val rawText = fetchText("https://smartbox.gtimg.cn/s3/?q=$encoded&t=all", searchHosts, gbk = true)
        val hint = Regex("""v_hint="([^"]*)"""").find(rawText)?.groupValues?.getOrNull(1)
            ?: return emptyList()
        if (hint.isBlank() || hint == "N") return emptyList()
        return hint.split("^").mapNotNull { item ->
            val parts = item.split("~")
            if (parts.size < 5) return@mapNotNull null
            val market = parts[0].trim().lowercase(Locale.US)
            val rawCode = parts[1].trim()
            val name = unescapeUnicode(parts[2].trim())
            if (rawCode.isBlank() || name.isBlank()) return@mapNotNull null
            // 美股联想结果带 `.oq` / `.ps` 后缀，腾讯报价只认纯代码且要大写
            val code = if (market == "us") {
                rawCode.substringBefore('.').uppercase(Locale.US)
            } else {
                rawCode
            }
            val full = market + code
            StockHit(
                code = full,
                name = name,
                kind = kindOf(parts[4]),
                market = marketOf(full)
            )
        }.take(30)
    }

    private fun unescapeUnicode(raw: String): String =
        Regex("""\\u([0-9a-fA-F]{4})""").replace(raw) { m ->
            runCatching { Integer.parseInt(m.groupValues[1], 16).toChar().toString() }.getOrDefault("")
        }

    private fun kindOf(type: String): StockKind = when (type.trim().uppercase(Locale.US)) {
        "ETF", "LOF", "FUND" -> StockKind.FUND
        "ZS" -> StockKind.INDEX
        else -> if (type.trim().uppercase(Locale.US).startsWith("GP")) StockKind.STOCK else StockKind.OTHER
    }

    /** 某个周期的 K 线：day / week / month，统一前复权 */
    fun candles(rawCode: String, period: String): List<Candle> =
        runCatching { fetchCandles(tencentCode(rawCode), period) }.getOrElse { emptyList() }

    /** 六位代码按首位补交易所前缀；已带前缀的原样返回 */
    fun tencentCode(raw: String): String {
        val c = raw.trim()
        val lower = c.lowercase(Locale.US)
        // 美股代码区分大小写（usAAPL 有效、usaapl 无效），且腾讯不收 `.oq` 这类后缀
        if (lower.startsWith("us")) {
            val ticker = c.substring(2).substringBefore('.').uppercase(Locale.US)
            return if (ticker.isBlank()) lower else "us$ticker"
        }
        if (lower.startsWith("sh") || lower.startsWith("sz") ||
            lower.startsWith("hk") || lower.startsWith("bj")
        ) return lower
        if (lower.length == 6 && lower.all { it.isDigit() }) {
            return when {
                lower.startsWith("6") -> "sh$lower"
                lower.startsWith("0") || lower.startsWith("3") -> "sz$lower"
                lower.startsWith("8") || lower.startsWith("4") -> "bj$lower"
                else -> "sh$lower"
            }
        }
        return lower
    }

    private fun quoteFields(code: String): List<String> {
        val raw = fetchText("https://qt.gtimg.cn/q=$code", tencentHosts, gbk = true)
        val body = Regex("""v_([A-Za-z0-9]+)="([^"]*)"""").find(raw)?.groupValues?.getOrNull(2)
            ?: throw StockException("行情数据为空")
        val fields = body.split("~")
        if (fields.size < 35) throw StockException("行情字段不完整")
        return fields
    }

    private fun fetchMinute(code: String): List<MinutePoint> {
        val raw = fetchText(
            "https://web.ifzq.gtimg.cn/appstock/app/minute/query?code=$code",
            chartHosts,
            gbk = false
        )
        val node = JSONObject(raw).optJSONObject("data")?.optJSONObject(code) ?: return emptyList()
        val rows = node.optJSONObject("data")?.optJSONArray("data") ?: return emptyList()
        val points = mutableListOf<MinutePoint>()
        var cumVol = 0.0
        var cumAmount = 0.0
        for (i in 0 until rows.length()) {
            val line = rows.optString(i).orEmpty().trim()
            val parts = line.split(" ")
            if (parts.size < 3) continue
            val label = clockText(parts[0])
            val price = parts[1].toDoubleOrNull() ?: continue
            val vol = parts[2].toDoubleOrNull() ?: 0.0
            val amount = parts.getOrNull(3)?.toDoubleOrNull() ?: 0.0
            cumVol += vol
            cumAmount += amount
            val avg = if (cumVol > 0 && cumAmount > 0 && cumVol * 100 > 0) {
                cumAmount / (cumVol * 100.0)
            } else {
                price
            }
            points += MinutePoint(label, price, avg)
        }
        // 美股指数这类只有一两个点的，画出来是一条贴边的直线，不如直接不画
        return if (points.size < 20) emptyList() else points
    }

    private fun fetchCandles(code: String, period: String = "day"): List<Candle> {
        // 周/月 K 的键名分别是 qfqweek / qfqmonth，日 K 仍是 qfqday
        val p = when (period) {
            "week", "month" -> period
            else -> "day"
        }
        val raw = fetchText(
            "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=$code,$p,,,60,qfq",
            chartHosts,
            gbk = false
        )
        val node = JSONObject(raw).optJSONObject("data")?.optJSONObject(code) ?: return emptyList()
        val rows = node.optJSONArray("qfq$p") ?: node.optJSONArray(p) ?: return emptyList()
        val out = mutableListOf<Candle>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONArray(i) ?: continue
            val date = row.optString(0).orEmpty()
            val open = row.optString(1).toDoubleOrNull() ?: continue
            val close = row.optString(2).toDoubleOrNull() ?: continue
            val high = row.optString(3).toDoubleOrNull() ?: close
            val low = row.optString(4).toDoubleOrNull() ?: close
            val vol = row.optString(5).toDoubleOrNull() ?: 0.0
            out += Candle(date, open, close, high, low, vol)
        }
        return out
    }

    /** 20261113161455 -> 16:14 */
    private fun clockText(raw: String): String {
        val t = raw.trim()
        return when {
            t.length >= 12 -> "${t.substring(8, 10)}:${t.substring(10, 12)}"
            t.length == 4 && t.all { it.isDigit() } -> "${t.substring(0, 2)}:${t.substring(2, 4)}"
            else -> t
        }
    }

    // ---------------- 腾讯：指数 / ETF 报价 ----------------

    private fun fetchTencent(codes: String): String =
        fetchText("https://qt.gtimg.cn/q=$codes", tencentHosts, gbk = true)

    /** 形如 v_sh000001="1~上证指数~000001~4020.12~3980.00~..." 的返回体，逐行拆字段 */
    private fun parseTencent(raw: String, expected: List<String>): Map<String, MarketQuote> {
        val out = LinkedHashMap<String, MarketQuote>()
        val pattern = Regex("""v_([A-Za-z0-9]+)="([^"]*)"""")
        for (m in pattern.findAll(raw)) {
            val code = m.groupValues[1]
            val fields = m.groupValues[2].split("~")
            if (fields.size < 5) continue
            val name = fields[1].trim().ifBlank { code }
            val price = fields[3].trim().toDoubleOrNull() ?: continue
            val prevClose = fields[4].trim().toDoubleOrNull() ?: 0.0
            val pct = if (prevClose > 0.0) {
                (price - prevClose) / prevClose * 100.0
            } else {
                fields.getOrNull(32)?.trim()?.toDoubleOrNull() ?: 0.0
            }
            out[code] = MarketQuote(code, name, price, pct, marketOf(code))
        }
        // 有些指数在某些时段会缺行，缺的就不显示，不瞎补 0
        return out.filterKeys { it in expected }.ifEmpty { out }
    }

    private fun marketOf(code: String): StockMarket = when {
        code.startsWith("hk") -> StockMarket.HK
        code.startsWith("us") -> StockMarket.US
        code.startsWith("sh5") || code.startsWith("sz15") -> StockMarket.FUND
        else -> StockMarket.A_SHARE
    }

    // ---------------- 东方财富：A 股涨幅榜 ----------------

    private fun fetchEastmoneyHot(): List<HotStock> {
        val url = "https://push2.eastmoney.com/api/qt/clist/get?" +
            "pn=1&pz=25&po=1&np=1&fltt=2&invt=2&fid=f3" +
            "&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23" +
            "&fields=f2,f3,f6,f12,f13,f14" +
            "&ut=bd1d9ddb04089700cf9c27f6f7426281"
        val raw = fetchText(url, eastmoneyHosts, gbk = false)
        val root = JSONObject(raw)
        val data = root.optJSONObject("data") ?: return emptyList()
        val diff = data.opt("diff") ?: return emptyList()
        val rows: List<JSONObject> = when (diff) {
            is JSONArray -> (0 until diff.length()).mapNotNull { diff.optJSONObject(it) }
            is JSONObject -> diff.keys().asSequence().mapNotNull { diff.optJSONObject(it) }.toList()
            else -> emptyList()
        }
        return rows.mapNotNull { row ->
            val name = row.optString("f14").trim()
            val code = row.optString("f12").trim()
            if (name.isBlank() || code.isBlank()) return@mapNotNull null
            val price = row.optDouble("f2", Double.NaN)
            val pct = row.optDouble("f3", Double.NaN)
            if (price.isNaN() || pct.isNaN()) return@mapNotNull null
            HotStock(code, name, price, pct, row.optDouble("f6", 0.0))
        }
    }

    private fun fetchBoards(): List<BoardRow> {
        val url = "https://push2.eastmoney.com/api/qt/clist/get?" +
            "pn=1&pz=25&po=1&np=1&fltt=2&invt=2&fid=f3" +
            "&fs=m:90+t:2" +
            "&fields=f2,f3,f6,f12,f14" +
            "&ut=bd1d9ddb04089700cf9c27f6f7426281"
        val raw = fetchText(url, boardHosts, gbk = false)
        val data = JSONObject(raw).optJSONObject("data") ?: return emptyList()
        val diff = data.opt("diff") ?: return emptyList()
        val rows: List<JSONObject> = when (diff) {
            is JSONArray -> (0 until diff.length()).mapNotNull { diff.optJSONObject(it) }
            is JSONObject -> diff.keys().asSequence().mapNotNull { diff.optJSONObject(it) }.toList()
            else -> emptyList()
        }
        return rows.mapNotNull { row ->
            val name = row.optString("f14").trim()
            val code = row.optString("f12").trim()
            if (name.isBlank() || code.isBlank()) return@mapNotNull null
            val price = row.optDouble("f2", Double.NaN)
            val pct = row.optDouble("f3", Double.NaN)
            if (price.isNaN() || pct.isNaN()) return@mapNotNull null
            BoardRow(code, name, price, pct, row.optDouble("f6", 0.0))
        }
    }

    // ---------------- 传输层 ----------------

    private fun fetchText(url: String, hosts: Set<String>, gbk: Boolean): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: throw StockException("行情地址无效")
        if (uri.scheme != "https" || uri.host !in hosts) throw StockException("行情地址未通过校验")
        var conn: HttpsURLConnection? = null
        try {
            conn = uri.toURL().openConnection() as? HttpsURLConnection
                ?: throw StockException("行情接口必须使用 HTTPS")
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8_000
            conn.readTimeout = 12_000
            conn.setRequestProperty("User-Agent", WebPrivacy.STANDARD_UA)
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("Accept-Encoding", "identity")
            val code = conn.responseCode
            if (code !in 200..299) throw StockException("行情服务暂时不可用（$code）")
            val bytes = readAtMost(conn.inputStream)
                ?: throw StockException("行情数据过大，已拒绝解析")
            return String(bytes, if (gbk) Charset.forName("GBK") else Charsets.UTF_8)
        } catch (e: StockException) {
            throw e
        } catch (e: Exception) {
            throw StockException("行情连接失败，请检查网络", e)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun readAtMost(stream: java.io.InputStream?): ByteArray? {
        if (stream == null) return ByteArray(0)
        stream.use {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var total = 0
            while (true) {
                val n = it.read(buf)
                if (n <= 0) break
                total += n
                if (total > MAX_BODY) return null
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
