package com.example.my.ui.trade

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.stock.Announcement
import com.example.my.data.stock.Candle
import com.example.my.data.stock.StockApi
import com.example.my.data.stock.StockDetail
import com.example.my.data.trade.QuoteLite
import com.example.my.data.trade.TradeSide
import com.example.my.data.trade.TradeAsset
import com.example.my.data.WebHit
import com.example.my.data.WebSearch
import com.example.my.ui.i18n.tr
import com.example.my.ui.screens.StockDetailScreen
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

private enum class TechPeriod(val key: String, val zh: String, val en: String) {
    DAY("day", "日K", "Daily"),
    WEEK("week", "周K", "Weekly"),
    MONTH("month", "月K", "Monthly")
}

/**
 * 交易看盘页：复用行情详情（分时 / 日周月 K / 五档盘口），
 * 再补上交易需要的东西——MA / MACD 指标、公告、相关资讯、基金信息，底部买入卖出。
 */
@Composable
fun TradeDetailPage(
    code: String,
    quotes: Map<String, QuoteLite>,
    onBack: () -> Unit,
    onTrade: (TradeSide, QuoteLite) -> Unit,
    onToast: (String) -> Unit
) {
    StockDetailScreen(
        code = code,
        onBack = onBack,
        bottomBar = { detail ->
            val lite = QuoteLite(
                code = detail.code,
                name = detail.name,
                price = detail.price,
                prevClose = detail.prevClose,
                changePct = detail.changePct,
                asset = TradeAsset.ofCode(detail.code)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Bg.Surface.copy(alpha = 1f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    TradeSide.BUY to TradeUp,
                    TradeSide.SELL to TradeDown
                ).forEach { (side, tone) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(tone)
                            .clickable { onTrade(side, lite) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tr(side.zh, side.en),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        extraSection = { detail ->
            Column {
                TradeGap()
                TechIndicatorCard(detail)
                TradeGap()
                if (TradeAsset.ofCode(detail.code) == TradeAsset.FUND) {
                    FundInfoCard(detail)
                    TradeGap()
                }
                AnnouncementCard(detail, onToast)
                TradeGap()
                NewsCard(detail, onToast)
            }
        }
    )
}

/** MA5/10/20 + MACD(12,26,9)：自己按收盘价算，不用接口给的现成值 */
@Composable
private fun TechIndicatorCard(detail: StockDetail) {
    var period by remember(detail.code) { mutableStateOf(TechPeriod.DAY) }
    var candles by remember(detail.code) { mutableStateOf(detail.candles) }
    var loading by remember(detail.code) { mutableStateOf(false) }

    LaunchedEffect(detail.code, period) {
        candles = if (period == TechPeriod.DAY) {
            detail.candles
        } else {
            loading = true
            val list = withContext(Dispatchers.IO) { StockApi.candles(detail.code, period.key) }
            loading = false
            list
        }
    }

    TradeCard(
        title = tr("技术指标", "Indicators"),
        subtitle = if (loading) tr("正在加载…", "Loading…")
        else tr("MA5/10/20 + MACD(12,26,9) · 按收盘价计算", "MA5/10/20 + MACD from closes")
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TechPeriod.entries.forEach { p ->
                TradeChip(tr(p.zh, p.en), period == p) { period = p }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (candles.isEmpty()) {
            TradeEmpty(tr("暂无 K 线数据", "No candles"))
        } else {
            PriceMaChart(candles, Modifier.fillMaxWidth().height(180.dp))
            Spacer(Modifier.height(6.dp))
            Row {
                LegendDot(tr("MA5", "MA5"), Color(0xFFE9C46A))
                Spacer(Modifier.width(10.dp))
                LegendDot(tr("MA10", "MA10"), Color(0xFF6C8EBF))
                Spacer(Modifier.width(10.dp))
                LegendDot(tr("MA20", "MA20"), Color(0xFF9B5DE5))
            }
            Spacer(Modifier.height(10.dp))
            MacdChart(candles, Modifier.fillMaxWidth().height(84.dp))
            Spacer(Modifier.height(4.dp))
            Row {
                LegendDot(tr("DIF", "DIF"), Color(0xFFE07A5F))
                Spacer(Modifier.width(10.dp))
                LegendDot(tr("DEA", "DEA"), Color(0xFF2A9D8F))
                Spacer(Modifier.width(10.dp))
                LegendDot(tr("柱=差值", "Histogram"), Color(0xFF8FA3B8))
                Spacer(Modifier.weight(1f))
                val last = macdSeries(candles).lastOrNull()
                if (last != null) {
                    Text(
                        "DIF ${String.format(Locale.US, "%.2f", last.first)} · DEA ${String.format(Locale.US, "%.2f", last.second)}",
                        color = Text.Muted,
                        fontSize = 9.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(10.dp).height(2.dp).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Text.Muted, fontSize = 9.sp)
    }
}

/** K 线 + 三条均线 */
@Composable
private fun PriceMaChart(candles: List<Candle>, modifier: Modifier) {
    Canvas(modifier) {
        val n = candles.size
        if (n == 0) return@Canvas
        val hi = candles.maxOf { it.high }
        val lo = candles.minOf { it.low }
        val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
        fun y(v: Double) = (size.height - ((v - lo) / span * size.height)).toFloat()
        val cw = size.width / n
        val bodyW = maxOf(cw * 0.55f, 1.4f)
        candles.forEachIndexed { i, c ->
            val color = if (c.close >= c.open) TradeUp else TradeDown
            val cx = cw * (i + 0.5f)
            drawLine(color, Offset(cx, y(c.high)), Offset(cx, y(c.low)), 1f)
            val top = y(maxOf(c.open, c.close))
            val bottom = y(minOf(c.open, c.close))
            drawRect(color, Offset(cx - bodyW / 2, top), Size(bodyW, maxOf(bottom - top, 1.4f)))
        }
        // 均线：不足周期的点不画假线
        listOf(5 to Color(0xFFE9C46A), 10 to Color(0xFF6C8EBF), 20 to Color(0xFF9B5DE5))
            .forEach { (period, color) ->
                if (n < period) return@forEach
                val path = Path()
                for (i in period - 1 until n) {
                    val avg = (i - period + 1..i).sumOf { candles[it].close } / period
                    val x = cw * (i + 0.5f)
                    val yy = y(avg)
                    if (i == period - 1) path.moveTo(x, yy) else path.lineTo(x, yy)
                }
                drawPath(path, color, style = Stroke(width = 1.2f))
            }
    }
}

/** MACD：DIF/DEA 两条线 + 差值柱 */
@Composable
private fun MacdChart(candles: List<Candle>, modifier: Modifier) {
    val series = remember(candles) { macdSeries(candles) }
    Canvas(modifier) {
        if (series.isEmpty()) return@Canvas
        val all = series.flatMap { listOf(it.first, it.second, it.third) }
        val maxAbs = maxOf(all.maxOf { abs(it) }, 0.01)
        val mid = size.height / 2f
        fun y(v: Double) = (mid - (v / maxAbs) * (size.height / 2f * 0.92)).toFloat()
        val n = series.size
        val cw = size.width / n
        drawLine(Color.Gray.copy(alpha = 0.25f), Offset(0f, mid), Offset(size.width, mid), 0.8f)
        series.forEachIndexed { i, (dif, dea, hist) ->
            val x = cw * (i + 0.5f)
            val hy = y(hist)
            drawRect(
                color = if (hist >= 0) TradeUp.copy(alpha = 0.55f) else TradeDown.copy(alpha = 0.55f),
                topLeft = Offset(x - cw * 0.28f, minOf(hy, mid)),
                size = Size(maxOf(cw * 0.56f, 1f), abs(hy - mid))
            )
        }
        fun line(values: List<Double>, color: Color) {
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = cw * (i + 0.5f)
                if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
            }
            drawPath(path, color, style = Stroke(width = 1.1f))
        }
        line(series.map { it.first }, Color(0xFFE07A5F))
        line(series.map { it.second }, Color(0xFF2A9D8F))
    }
}

/** 返回 (DIF, DEA, 柱)，标准 12/26/9 参数；前 33 根没有足够数据，跳过 */
private fun macdSeries(candles: List<Candle>): List<Triple<Double, Double, Double>> {
    if (candles.size < 35) return emptyList()
    val ema12 = ema(candles.map { it.close }, 12)
    val ema26 = ema(candles.map { it.close }, 26)
    val dif = candles.indices.map { ema12[it] - ema26[it] }
    val dea = ema(dif, 9)
    return (33 until candles.size).map { i ->
        Triple(dif[i], dea[i], dif[i] - dea[i])
    }
}

private fun ema(values: List<Double>, period: Int): List<Double> {
    if (values.isEmpty()) return emptyList()
    val k = 2.0 / (period + 1)
    val out = ArrayList<Double>(values.size)
    var prev = values.first()
    values.forEach { v ->
        prev = if (out.isEmpty()) v else v * k + prev * (1 - k)
        out += prev
    }
    return out
}

/** 公告：东方财富公开公告接口 */
@Composable
private fun AnnouncementCard(detail: StockDetail, onToast: (String) -> Unit) {
    var list by remember(detail.code) { mutableStateOf<List<Announcement>>(emptyList()) }
    var loading by remember(detail.code) { mutableStateOf(true) }
    var failed by remember(detail.code) { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(detail.code) {
        loading = true
        val items = withContext(Dispatchers.IO) {
            runCatching { StockApi.announcements(detail.code) }.getOrNull()
        }
        failed = items == null
        list = items.orEmpty()
        loading = false
    }

    TradeCard(
        title = tr("公告", "Announcements"),
        subtitle = tr("东方财富公告 · 点击看原文", "Eastmoney filings · tap to open")
    ) {
        when {
            loading -> TradeEmpty(tr("正在加载公告…", "Loading…"))
            failed -> TradeEmpty(tr("公告接口暂不可用", "Filing feed unavailable"))
            list.isEmpty() -> TradeEmpty(
                tr("这只标的近期没有公告", "No recent filings"),
                tr("指数、部分 ETF 没有公告", "Indices and some ETFs have none")
            )
            else -> list.forEachIndexed { i, a ->
                if (i > 0) TradeDivider()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.url)))
                            }.onFailure { onToast(tr("打不开公告链接", "Cannot open the filing")) }
                        }
                        .padding(vertical = 7.dp)
                ) {
                    Text(
                        a.title,
                        color = Text.Primary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(a.date, color = Text.Muted, fontSize = 9.5.sp)
                }
            }
        }
    }
}

/** 相关资讯：复用 App 内的全网检索，标题与摘要都来自结果页，不编新闻 */
@Composable
private fun NewsCard(detail: StockDetail, onToast: (String) -> Unit) {
    var hits by remember(detail.code) { mutableStateOf<List<WebHit>>(emptyList()) }
    var loading by remember(detail.code) { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(detail.code, detail.name) {
        loading = true
        val query = "${detail.name} ${detail.code} 最新消息"
        hits = withContext(Dispatchers.IO) {
            runCatching {
                WebSearch.search(query, limit = 5, profile = WebSearch.SearchProfile.light())
            }.getOrDefault(emptyList())
        }
        loading = false
    }

    TradeCard(
        title = tr("相关资讯", "Related news"),
        subtitle = tr("来自公开网页检索 · 点开看原文", "From public web search")
    ) {
        when {
            loading -> TradeEmpty(tr("正在检索…", "Searching…"))
            hits.isEmpty() -> TradeEmpty(
                tr("没搜到相关资讯", "No related news found"),
                tr("不拿旧闻或无关内容凑数", "Nothing padded in")
            )
            else -> hits.take(5).forEachIndexed { i, h ->
                if (i > 0) TradeDivider()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(h.url)))
                            }.onFailure { onToast(tr("打不开这条链接", "Cannot open the link")) }
                        }
                        .padding(vertical = 7.dp)
                ) {
                    Text(
                        h.title,
                        color = Text.Primary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (h.snippet.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            h.snippet,
                            color = Text.Muted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(h.site, color = Text.Muted, fontSize = 9.sp)
                }
            }
        }
    }
}

/** 基金：只有场内价格，没有基金公司持仓明细 —— 这里如实说明，不造持仓 */
@Composable
private fun FundInfoCard(detail: StockDetail) {
    TradeCard(
        title = tr("基金信息", "Fund info"),
        subtitle = tr("场内价格走势 · 单位净值需基金公司披露", "On-exchange price only")
    ) {
        InfoLineMini(tr("盘中均价", "Average"), priceText(detail.avgPrice))
        InfoLineMini(tr("昨收", "Prev close"), priceText(detail.prevClose))
        InfoLineMini(tr("振幅", "Amplitude"), if (detail.amplitude > 0) pctText(detail.amplitude) else "—")
        InfoLineMini(tr("成交额", "Turnover"), moneyText(detail.turnoverYuan))
        Spacer(Modifier.height(8.dp))
        Text(
            tr(
                "持仓明细来自基金公司定期披露，本 App 未接入该数据源，所以不在这里展示——" +
                    "宁可不显示，也不摆一份假的前十大持仓。",
                "Fund holdings come from the manager's periodic disclosures; this app has no such feed, " +
                    "so nothing is shown rather than a fabricated top-10 list."
            ),
            color = Text.Muted,
            fontSize = 10.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun InfoLineMini(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Text.Secondary, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = Text.Primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
