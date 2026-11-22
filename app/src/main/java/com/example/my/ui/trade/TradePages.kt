package com.example.my.ui.trade

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.my.data.news.NewsBrief
import com.example.my.data.stock.MarketQuote
import com.example.my.data.stock.MarketSnapshot
import com.example.my.data.trade.OrderStatus
import com.example.my.data.trade.QuoteLite
import com.example.my.data.trade.RiskLevel
import com.example.my.data.trade.TradeStore
import com.example.my.data.trade.TradeAsset
import com.example.my.data.trade.TradePosition
import com.example.my.data.trade.TradeSide
import com.example.my.state.AppState
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Func
import com.example.my.ui.theme.Layer
import com.example.my.ui.theme.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ============================================================================
// 一、资产概览
// ============================================================================

@Composable
fun TradeHomePage(
    quotes: Map<String, QuoteLite>,
    snapshot: MarketSnapshot?,
    brief: NewsBrief?,
    loading: Boolean,
    onOpenDetail: (String) -> Unit,
    onTrade: (String, TradeSide, QuoteLite?) -> Unit,
    onTab: (TradeTab) -> Unit
) {
    val total = TradeStore.totalAssets(quotes)
    val dayPnl = TradeStore.dayPnl(quotes)
    val returnPct = TradeStore.totalReturn(quotes)
    val floatPnl = TradeStore.floatPnl(quotes)
    var showTransfer by remember { mutableStateOf(false) }
    var showPlan by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp
        )
    ) {
        item {
            TradeTopBar(
                title = tr("我的交易", "My trading"),
                subtitle = tr("模拟盘 · 行情来自公开接口", "Paper account · public quotes")
            )
        }
        item {
            // 总资产 / 当日盈亏 / 总收益率
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Layer.Opaque)
                    .padding(16.dp)
            ) {
                Text(tr("总资产（元）", "Total assets (CNY)"), color = Text.Muted, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    moneyText(total),
                    color = Text.Primary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(tr("当日盈亏", "Today"), color = Text.Muted, fontSize = 10.sp)
                        Text(
                            signedPrice(dayPnl),
                            color = toneOf(dayPnl),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(tr("总收益率", "Total return"), color = Text.Muted, fontSize = 10.sp)
                        Text(
                            pctText(returnPct * 100),
                            color = toneOf(returnPct),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(tr("浮动盈亏", "Unrealized"), color = Text.Muted, fontSize = 10.sp)
                        Text(
                            signedPrice(floatPnl),
                            color = toneOf(floatPnl),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("可用资金 ${moneyText(TradeStore.cash)}", "Cash ${moneyText(TradeStore.cash)}"),
                    color = Text.Secondary,
                    fontSize = 11.sp
                )
            }
        }
        item { TradeGap() }
        item { SimulationNotice() }
        item { TradeGap() }

        // 持仓速览
        item {
            TradeCard(
                title = tr("我的持仓", "Holdings"),
                subtitle = tr("按市值排序，点一条看详情", "Sorted by value — tap for detail"),
                trailing = {
                    Text(
                        tr("全部 ›", "All ›"),
                        color = Brand.Terracotta,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onTab(TradeTab.POSITIONS) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            ) {
                if (TradeStore.positions.isEmpty()) {
                    TradeEmpty(
                        tr("还没有持仓", "No holdings yet"),
                        tr("从行情里选一只，点「买入」开始", "Pick a symbol in Markets and hit Buy")
                    )
                } else {
                    val sorted = TradeStore.positions.sortedByDescending { p ->
                        p.qty * (quotes[p.code]?.price ?: p.avgCost)
                    }.take(3)
                    sorted.forEachIndexed { i, p ->
                        if (i > 0) TradeDivider()
                        PositionRow(p, quotes[p.code], compact = true) { onOpenDetail(p.code) }
                    }
                }
            }
        }
        item { TradeGap() }

        // 今日涨跌榜
        item {
            TradeCard(
                title = tr("今日涨跌榜", "Today's movers"),
                subtitle = tr("东方财富 A 股涨幅榜 · 前 5", "Eastmoney A-share gainers · top 5")
            ) {
                val hot = snapshot?.hot?.take(5).orEmpty()
                if (hot.isEmpty()) {
                    TradeEmpty(if (loading) tr("正在拉取行情…", "Loading…") else tr("暂无榜单", "No data"))
                } else {
                    hot.forEachIndexed { i, h ->
                        if (i > 0) TradeDivider()
                        val code = com.example.my.data.stock.StockApi.tencentCode(h.code)
                        MarketRow(
                            code = code,
                            name = h.name,
                            price = h.price,
                            pct = h.changePct,
                            extra = tr("成交 ${moneyText(h.turnover)}", "Turnover ${moneyText(h.turnover)}"),
                            onClick = { onOpenDetail(code) }
                        )
                    }
                }
            }
        }
        item { TradeGap() }

        // 自选快捷
        item {
            TradeCard(
                title = tr("我的自选", "Watchlist"),
                subtitle = tr("点一下进详情，长按可移出自选", "Tap for detail; long-press to remove"),
                trailing = {
                    Text(
                        tr("去行情 ›", "Markets ›"),
                        color = Brand.Terracotta,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onTab(TradeTab.MARKET) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            ) {
                if (TradeStore.watchlist.isEmpty()) {
                    TradeEmpty(
                        tr("还没有自选", "No watchlist"),
                        tr("在行情页点星标加入", "Star a symbol in Markets")
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(TradeStore.watchlist, key = { it.code }) { w ->
                            val q = quotes[w.code]
                            Column(
                                Modifier
                                    .width(112.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Bg.SurfaceVariant.copy(alpha = 1f))
                                    .clickable { onOpenDetail(w.code) }
                                    .padding(10.dp)
                            ) {
                                Text(
                                    w.name,
                                    color = Text.Primary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    priceText(q?.price ?: 0.0),
                                    color = Text.Primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    q?.let { pctText(it.changePct) } ?: "—",
                                    color = toneOf(q?.changePct ?: 0.0),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        item { TradeGap() }

        // 基金推荐
        item {
            TradeCard(
                title = tr("基金 / ETF 行情", "Funds & ETFs"),
                subtitle = tr("场内基金按涨跌幅排序", "On-exchange funds by change")
            ) {
                val funds = snapshot?.funds?.take(3).orEmpty()
                if (funds.isEmpty()) {
                    TradeEmpty(if (loading) tr("正在拉取行情…", "Loading…") else tr("暂无数据", "No data"))
                } else {
                    funds.forEachIndexed { i, f ->
                        if (i > 0) TradeDivider()
                        MarketRow(
                            code = f.code,
                            name = f.name,
                            price = f.price,
                            pct = f.changePct,
                            extra = tr("ETF / 场内基金", "ETF / on-exchange"),
                            onClick = { onOpenDetail(f.code) }
                        )
                    }
                }
            }
        }
        item { TradeGap() }

        // 市场快讯
        item {
            TradeCard(
                title = tr("今日快讯", "Morning brief"),
                subtitle = brief?.let { tr("来源：${it.source} · ${it.date}", "Source: ${it.source} · ${it.date}") }
                    ?: tr("60 秒早报", "60s daily brief")
            ) {
                val lines = brief?.items?.take(4).orEmpty()
                if (lines.isEmpty()) {
                    TradeEmpty(tr("暂时没有快讯", "No brief yet"))
                } else {
                    lines.forEachIndexed { i, line ->
                        if (i > 0) Spacer(Modifier.height(6.dp))
                        Text(
                            "· ${line.trim()}",
                            color = Text.Secondary,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        item { TradeGap() }

        // 快捷入口
        item {
            TradeCard(title = tr("快捷入口", "Quick actions")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction(tr("买入", "Buy"), Color(0xFFD93B33), Modifier.weight(1f)) {
                        onTrade("", TradeSide.BUY, null)
                    }
                    QuickAction(tr("卖出", "Sell"), Color(0xFF15935F), Modifier.weight(1f)) {
                        onTrade("", TradeSide.SELL, null)
                    }
                    QuickAction(tr("定投", "Auto-invest"), Brand.Terracotta, Modifier.weight(1f)) {
                        showPlan = true
                    }
                    QuickAction(tr("条件单", "Limit"), Color(0xFF6C7A89), Modifier.weight(1f)) {
                        onTrade("", TradeSide.BUY, null)
                    }
                    QuickAction(tr("转账", "Transfer"), Color(0xFF2A9D8F), Modifier.weight(1f)) {
                        showTransfer = true
                    }
                }
            }
        }
    }

    if (showTransfer) {
        TransferDialog(onDismiss = { showTransfer = false })
    }
    if (showPlan) {
        InvestPlanDialog(onDismiss = { showPlan = false })
    }
}

@Composable
private fun QuickAction(label: String, tone: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tone.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = tone, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 持仓 / 行情通用一行：名称代码 + 现价涨跌 + 副信息 */
@Composable
fun MarketRow(
    code: String,
    name: String,
    price: Double,
    pct: Double,
    extra: String? = null,
    spark: List<Double> = emptyList(),
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = Text.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(codeDisplay(code), extra ?: "").filter { it.isNotBlank() }.joinToString(" · "),
                color = Text.Muted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (spark.size >= 2) {
            Sparkline(
                values = spark,
                baseline = price / (1.0 + pct / 100.0),
                modifier = Modifier.width(54.dp).height(22.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(priceText(price), color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(pctText(pct), color = toneOf(pct), fontSize = 11.sp)
        }
    }
}

/** 一条持仓行：数量 / 成本 / 现价 / 浮动盈亏 */
@Composable
fun PositionRow(
    p: TradePosition,
    quote: QuoteLite?,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val price = quote?.price?.takeIf { it > 0 } ?: p.avgCost
    val pnl = (price - p.avgCost) * p.qty
    val pnlPct = if (p.avgCost > 0) (price - p.avgCost) / p.avgCost * 100 else 0.0
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    p.name,
                    color = Text.Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (p.asset == TradeAsset.FUND) tr("基金", "Fund") else tr("股票", "Stock"),
                    color = Text.Muted,
                    fontSize = 10.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(priceText(price), color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${signedPrice(pnl)} · ${pctText(pnlPct)}",
                    color = toneOf(pnl),
                    fontSize = 10.5.sp
                )
            }
        }
        if (!compact) {
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    tr("持仓 ${p.qty} · 成本 ${priceText(p.avgCost)}", "${p.qty} @ ${priceText(p.avgCost)}"),
                    color = Text.Muted,
                    fontSize = 10.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    tr("市值 ${moneyText(price * p.qty)}", "Value ${moneyText(price * p.qty)}"),
                    color = Text.Secondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun TradeDivider() {
    Box(Modifier.fillMaxWidth().height(0.6.dp).background(Bg.Outline.copy(alpha = 0.4f)))
}

/** 00600 → sh600519 这类前缀提醒 */
fun codeDisplay(code: String): String = when {
    code.startsWith("sh") || code.startsWith("sz") -> code.uppercase(Locale.US)
    code.startsWith("hk") -> code.uppercase(Locale.US)
    code.startsWith("us") -> code.uppercase(Locale.US)
    else -> code
}

// ============================================================================
// 二、行情 / 自选
// ============================================================================

private enum class MarketTab2(val zh: String, val en: String) {
    WATCH("自选", "Watch"),
    A("A股", "A-shares"),
    FUND("基金", "Funds"),
    ETF("ETF", "ETF"),
    BOARD("板块", "Sectors")
}

private enum class SortMode(val zh: String, val en: String) {
    PCT_DESC("涨幅", "Gainers"),
    PCT_ASC("跌幅", "Losers"),
    TURNOVER("成交额", "Turnover")
}

@Composable
fun TradeMarketPage(
    quotes: Map<String, QuoteLite>,
    snapshot: MarketSnapshot?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onTrade: (String, TradeSide, QuoteLite?) -> Unit
) {
    var tab by remember { mutableStateOf(MarketTab2.A) }
    var sort by remember { mutableStateOf(SortMode.PCT_DESC) }
    var query by remember { mutableStateOf("") }
    var boards by remember { mutableStateOf<List<com.example.my.data.stock.BoardRow>>(emptyList()) }
    var hits by remember { mutableStateOf<List<com.example.my.data.stock.StockHit>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    // 迷你分时线：只给当前 tab 的前 6 只拉分时，多了接口会变慢也要被限流
    var sparks by remember { mutableStateOf<Map<String, List<Double>>>(emptyMap()) }
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(tab) {
        if (tab == MarketTab2.BOARD && boards.isEmpty()) {
            boards = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.my.data.stock.StockApi.boards()
            }
        }
    }
    // 搜索防抖：停手 350ms 再查
    androidx.compose.runtime.LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            hits = emptyList()
            searchError = null
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(350)
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.example.my.data.stock.StockApi.search(q) }
        }
        result.onSuccess { hits = it; searchError = null }
            .onFailure { hits = emptyList(); searchError = it.message ?: tr("搜索失败", "Search failed") }
    }

    // 迷你分时线：按当前 tab 取前 6 个代码，顺序拉，失败就留空
    androidx.compose.runtime.LaunchedEffect(tab, snapshot) {
        val codes: List<String> = when (tab) {
            MarketTab2.WATCH -> TradeStore.watchlist.map { it.code }
            MarketTab2.A -> snapshot?.hot.orEmpty()
                .map { com.example.my.data.stock.StockApi.tencentCode(it.code) }
            MarketTab2.FUND, MarketTab2.ETF -> snapshot?.funds.orEmpty().map { it.code }
            MarketTab2.BOARD -> emptyList()
        }.take(6)
        val missing = codes.filter { sparks[it].isNullOrEmpty() }
        if (missing.isEmpty()) return@LaunchedEffect
        val fetched = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            missing.mapNotNull { code ->
                val points = runCatching { com.example.my.data.stock.StockApi.minute(code) }
                    .getOrElse { emptyList() }
                val prices = points.map { it.price }
                if (prices.size >= 2) code to prices else null
            }
        }
        if (fetched.isNotEmpty()) sparks = sparks + fetched.toMap()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp
        )
    ) {
        item {
            TradeTopBar(
                title = tr("行情 / 自选", "Markets"),
                subtitle = if (loading) tr("正在刷新…", "Refreshing…")
                else error ?: tr("公开行情 · 60 秒自动刷新", "Public quotes · auto every 60s")
            )
        }
        item {
            // 搜索框：输入即查，结果直接进详情
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Bg.SurfaceVariant.copy(alpha = 1f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔍", fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            tr("搜代码 / 名称，如 600519、茅台", "Code or name, e.g. 600519"),
                            color = Text.Muted,
                            fontSize = 12.sp
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Text.Primary, fontSize = 13.sp),
                        cursorBrush = SolidColor(Brand.Terracotta),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    Text(
                        tr("清空", "Clear"),
                        color = Text.Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { query = "" }
                    )
                }
            }
        }
        item { TradeGap(10) }

        if (query.trim().isNotEmpty()) {
            item {
                TradeCard(title = tr("搜索结果", "Results"), subtitle = searchError ?: tr("点一条看详情或直接下单", "Tap for detail or trade")) {
                    if (hits.isEmpty()) {
                        TradeEmpty(tr("没有匹配的标的", "No match"))
                    } else {
                        hits.forEachIndexed { i, h ->
                            if (i > 0) TradeDivider()
                            val q = quotes[h.code]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenDetail(h.code) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(h.name, color = Text.Primary, fontSize = 13.sp)
                                    Text(
                                        codeDisplay(h.code),
                                        color = Text.Muted,
                                        fontSize = 10.sp
                                    )
                                }
                                TradeChip(tr("买入", "Buy"), false) { onTrade(h.code, TradeSide.BUY, q) }
                            }
                        }
                    }
                }
            }
            item { TradeGap() }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarketTab2.entries.forEach { t ->
                    TradeChip(tr(t.zh, t.en), tab == t) { tab = t }
                }
            }
        }
        item { TradeGap(10) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortMode.entries.forEach { s ->
                    TradeChip(tr(s.zh, s.en), sort == s) { sort = s }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    tr("刷新", "Refresh"),
                    color = Brand.Terracotta,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onRefresh)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
        item { TradeGap(8) }

        when (tab) {
            MarketTab2.WATCH -> item {
                TradeCard(title = tr("自选", "Watchlist"), subtitle = tr("长按移出", "Long-press to remove")) {
                    if (TradeStore.watchlist.isEmpty()) {
                        TradeEmpty(
                            tr("自选是空的", "Watchlist empty"),
                            tr("在上方搜索里加，或在 A 股列表里点星标", "Search above or star a row")
                        )
                    } else {
                        val rows = TradeStore.watchlist.mapNotNull { w ->
                            quotes[w.code]?.let { w to it }
                        }.let { list ->
                            when (sort) {
                                SortMode.PCT_DESC -> list.sortedByDescending { it.second.changePct }
                                SortMode.PCT_ASC -> list.sortedBy { it.second.changePct }
                                SortMode.TURNOVER -> list.sortedByDescending { it.second.turnover }
                            }
                        }
                        if (rows.isEmpty()) {
                            TradeEmpty(tr("自选行情还没回来", "Quotes not back yet"), tr("稍后点刷新", "Try Refresh"))
                        }
                        rows.forEachIndexed { i, (w, q) ->
                            if (i > 0) TradeDivider()
                            WatchRow(w.code, q, spark = sparks[w.code].orEmpty(), onClick = { onOpenDetail(w.code) })
                        }
                    }
                }
            }
            MarketTab2.A, MarketTab2.FUND, MarketTab2.ETF -> item {
                val source: List<Triple<String, String, QuoteLite>> = when (tab) {
                    MarketTab2.A -> snapshot?.hot.orEmpty().map {
                        Triple(com.example.my.data.stock.StockApi.tencentCode(it.code), it.name, quotes[com.example.my.data.stock.StockApi.tencentCode(it.code)] ?: QuoteLite(com.example.my.data.stock.StockApi.tencentCode(it.code), it.name, it.price, it.price / (1 + it.changePct / 100.0), it.changePct, it.turnover))
                    }
                    MarketTab2.FUND -> snapshot?.funds.orEmpty().map { Triple(it.code, it.name, quotes[it.code] ?: it.toLite()) }
                    else -> snapshot?.funds.orEmpty().map { Triple(it.code, it.name, quotes[it.code] ?: it.toLite()) }
                }
                val sorted = when (sort) {
                    SortMode.PCT_DESC -> source.sortedByDescending { it.third.changePct }
                    SortMode.PCT_ASC -> source.sortedBy { it.third.changePct }
                    SortMode.TURNOVER -> source.sortedByDescending { it.third.turnover }
                }
                TradeCard(
                    title = when (tab) {
                        MarketTab2.A -> tr("A 股涨幅榜", "A-share gainers")
                        MarketTab2.FUND -> tr("场外/场内基金", "Funds")
                        else -> tr("ETF 行情", "ETFs")
                    },
                    subtitle = if (loading) tr("正在拉取…", "Loading…") else tr("点一行看详情，点「买」直接下单", "Tap a row; Buy to trade")
                ) {
                    if (sorted.isEmpty()) {
                        TradeEmpty(
                            if (loading) tr("正在拉取行情…", "Loading…") else tr("暂无行情", "No quotes"),
                            tr("检查网络后点刷新", "Check network and refresh")
                        )
                    }
                    sorted.forEachIndexed { i, (code, name, q) ->
                        if (i > 0) TradeDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                MarketRow(
                                    code = code,
                                    name = name,
                                    price = q.price,
                                    pct = q.changePct,
                                    extra = tr("成交 ${moneyText(q.turnover)}", "Turnover ${moneyText(q.turnover)}"),
                                    spark = sparks[code].orEmpty(),
                                    onClick = { onOpenDetail(code) }
                                )
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Brand.Terracotta.copy(alpha = 0.12f))
                                    .clickable { onTrade(code, TradeSide.BUY, q) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(tr("买", "Buy"), color = Brand.Terracotta, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            MarketTab2.BOARD -> item {
                TradeCard(
                    title = tr("行业板块", "Sectors"),
                    subtitle = tr("东方财富行业板块 · 按涨跌幅", "Eastmoney sectors")
                ) {
                    if (boards.isEmpty()) {
                        TradeEmpty(tr("板块数据没回来", "No sector data"), tr("稍后重试", "Try again"))
                    } else {
                        val sorted = when (sort) {
                            SortMode.PCT_DESC -> boards.sortedByDescending { it.changePct }
                            SortMode.PCT_ASC -> boards.sortedBy { it.changePct }
                            SortMode.TURNOVER -> boards.sortedByDescending { it.turnover }
                        }
                        sorted.take(15).forEachIndexed { i, b ->
                            if (i > 0) TradeDivider()
                            MarketRow(
                                code = b.code,
                                name = b.name,
                                price = b.price,
                                pct = b.changePct,
                                extra = tr("成交 ${moneyText(b.turnover)}", "Turnover ${moneyText(b.turnover)}"),
                                // 板块代码不是可交易标的，点开也只有「暂无数据」，所以不跳详情
                                onClick = {}
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchRow(code: String, q: QuoteLite, spark: List<Double>, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .combinedClickableCompat(
                onClick = onClick,
                onLongClick = { TradeStore.toggleWatch(code, q.name) }
            )
            .padding(vertical = 8.dp)
    ) {
        MarketRow(
            code = code,
            name = q.name,
            price = q.price,
            pct = q.changePct,
            extra = null,
            spark = spark,
            onClick = onClick
        )
    }
}

// ============================================================================
// 三、持仓
// ============================================================================

private enum class PositionFilter(val zh: String, val en: String) {
    ALL("全部", "All"),
    STOCK("股票", "Stocks"),
    FUND("基金", "Funds")
}

private enum class PositionSort(val zh: String, val en: String) {
    PNL("按盈亏", "By P/L"),
    RATIO("按比例", "By %")
}

@Composable
fun TradePositionsPage(
    quotes: Map<String, QuoteLite>,
    onOpenDetail: (String) -> Unit,
    onTrade: (String, TradeSide) -> Unit
) {
    var filter by remember { mutableStateOf(PositionFilter.ALL) }
    var sort by remember { mutableStateOf(PositionSort.PNL) }

    val stockValue = TradeStore.positions.filter { it.asset == TradeAsset.STOCK }
        .sumOf { it.qty * (quotes[it.code]?.price ?: it.avgCost) }
    val fundValue = TradeStore.positions.filter { it.asset == TradeAsset.FUND }
        .sumOf { it.qty * (quotes[it.code]?.price ?: it.avgCost) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp
        )
    ) {
        item { TradeTopBar(title = tr("持仓", "Positions"), subtitle = tr("股票 / 基金 / 现金占比", "Stocks · funds · cash")) }
        item {
            TradeCard(title = tr("资产结构", "Allocation")) {
                AssetAllocation(stockValue, fundValue, TradeStore.cash)
            }
        }
        item { TradeGap() }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PositionFilter.entries.forEach { f ->
                    TradeChip(tr(f.zh, f.en), filter == f) { filter = f }
                }
                Spacer(Modifier.width(4.dp))
                PositionSort.entries.forEach { s ->
                    TradeChip(tr(s.zh, s.en), sort == s) { sort = s }
                }
            }
        }
        item { TradeGap(8) }
        item {
            val rows = TradeStore.positions
                .filter {
                    when (filter) {
                        PositionFilter.ALL -> true
                        PositionFilter.STOCK -> it.asset == TradeAsset.STOCK
                        PositionFilter.FUND -> it.asset == TradeAsset.FUND
                    }
                }
                .sortedWith(
                    when (sort) {
                        PositionSort.PNL -> compareByDescending { p ->
                            (quotes[p.code]?.price ?: p.avgCost) - p.avgCost
                        }
                        PositionSort.RATIO -> compareByDescending { p ->
                            val price = quotes[p.code]?.price ?: p.avgCost
                            if (p.avgCost > 0) (price - p.avgCost) / p.avgCost else 0.0
                        }
                    }
                )
            TradeCard(
                title = tr("持仓明细", "Holdings detail"),
                subtitle = tr("${rows.size} 只 · 点详情可继续买卖", "${rows.size} symbols")
            ) {
                if (rows.isEmpty()) {
                    TradeEmpty(tr("这个分类下没有持仓", "No holdings in this filter"))
                }
                rows.forEachIndexed { i, p ->
                    if (i > 0) TradeDivider()
                    Column {
                        PositionRow(p, quotes[p.code]) { onOpenDetail(p.code) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallTradeButton(tr("买入", "Buy"), TradeUp) { onTrade(p.code, TradeSide.BUY) }
                            SmallTradeButton(tr("卖出", "Sell"), TradeDown) { onTrade(p.code, TradeSide.SELL) }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
        item { TradeGap() }
        item { SimulationNotice() }
    }
}

@Composable
private fun SmallTradeButton(label: String, tone: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(tone.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(label, color = tone, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ============================================================================
// 四、我的
// ============================================================================

@Composable
fun TradeMePage(state: AppState, quotes: Map<String, QuoteLite>) {
    val context = LocalContext.current
    var sheet by remember { mutableStateOf<String?>(null) }
    var showRisk by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp
        )
    ) {
        item { TradeTopBar(title = tr("我的", "Me"), subtitle = tr("模拟账户", "Paper account")) }
        item {
            TradeCard(title = tr("账户信息", "Account")) {
                InfoLine(tr("可用资金", "Available"), moneyText(TradeStore.cash))
                InfoLine(tr("可取资金", "Withdrawable"), moneyText(TradeStore.cash))
                InfoLine(tr("总市值", "Market value"), moneyText(TradeStore.marketValue(quotes)))
                InfoLine(tr("总资产", "Total"), moneyText(TradeStore.totalAssets(quotes)))
                InfoLine(tr("已实现盈亏", "Realized P/L"), signedPrice(TradeStore.realizedPnl), toneOf(TradeStore.realizedPnl))
                InfoLine(
                    tr("风险等级", "Risk level"),
                    TradeStore.riskLevel?.let { tr(it.zh, it.en) } ?: tr("未测评", "Not assessed"),
                    Brand.Terracotta
                )
            }
        }
        item { TradeGap() }
        item {
            TradeCard(title = tr("记录与设置", "Records & settings")) {
                MenuLine(tr("成交记录", "Filled orders")) { sheet = "fills" }
                MenuLine(tr("委托记录", "All orders")) { sheet = "orders" }
                MenuLine(tr("资金流水", "Cash flow")) { sheet = "flows" }
                MenuLine(tr("定投计划", "Auto-invest plans")) { sheet = "plans" }
                MenuLine(tr("消息通知", "Notifications")) { state.openScreen(com.example.my.state.ExtraScreen.NOTIFICATIONS) }
                MenuLine(tr("设置", "Settings")) { state.openScreen(com.example.my.state.ExtraScreen.SETTINGS) }
                MenuLine(tr("风险测评", "Risk assessment")) { showRisk = true }
                MenuLine(tr("重置模拟账户", "Reset paper account"), danger = true) { showReset = true }
            }
        }
        item { TradeGap() }
        item { SimulationNotice() }
    }

    when (sheet) {
        "fills", "orders" -> OrderHistoryDialog(
            onlyFilled = sheet == "fills",
            onDismiss = { sheet = null },
            onCancel = { id ->
                if (TradeStore.cancel(id)) state.toast(tr("已撤单", "Cancelled"))
            }
        )
        "flows" -> FlowHistoryDialog(onDismiss = { sheet = null })
        "plans" -> PlanListDialog(onDismiss = { sheet = null })
    }
    if (showRisk) RiskDialog(onDismiss = { showRisk = false })
    if (showReset) {
        ConfirmDialog(
            title = tr("重置模拟账户？", "Reset paper account?"),
            message = tr(
                "会清空持仓、委托与流水，资金回到 10 万。行情与自选不受影响。",
                "Clears holdings, orders and cash flow; cash back to 100,000. Watchlist stays."
            ),
            confirm = tr("重置", "Reset"),
            onConfirm = {
                TradeStore.resetSimulation(context)
                showReset = false
                state.toast(tr("模拟账户已重置", "Paper account reset"))
            },
            onDismiss = { showReset = false }
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String, tone: Color = Text.Primary) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Text.Secondary, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = tone, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MenuLine(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (danger) Func.Danger else Text.Primary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text("›", color = Text.Muted, fontSize = 16.sp)
    }
}

// ============================================================================
// 弹层：转账 / 定投 / 记录 / 流水 / 风险测评 / 确认
// ============================================================================

@Composable
fun TradeSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Layer.Scrim).clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Layer.Opaque)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) {}
                    .padding(16.dp)
            ) {
                Box(
                    Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Bg.Outline)
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(12.dp))
                Text(title, color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}

@Composable
private fun TransferDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf("10000") }
    var deposit by remember { mutableStateOf(true) }
    TradeSheet(title = tr("银证转账（模拟）", "Cash transfer (simulated)"), onDismiss = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TradeChip(tr("转入", "Deposit"), deposit) { deposit = true }
            TradeChip(tr("转出", "Withdraw"), !deposit) { deposit = false }
        }
        Spacer(Modifier.height(10.dp))
        TradeInput(value = amount, onValueChange = { amount = it }, placeholder = tr("金额（元）", "Amount (CNY)"))
        Spacer(Modifier.height(6.dp))
        Text(
            tr("可用资金 ${moneyText(TradeStore.cash)}", "Cash ${moneyText(TradeStore.cash)}"),
            color = Text.Muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(12.dp))
        TradePrimaryButton(tr("确认${if (deposit) "转入" else "转出"}", if (deposit) "Deposit" else "Withdraw")) {
            val v = amount.toDoubleOrNull() ?: 0.0
            val real = TradeStore.transferCash(v, deposit)
            if (real == 0.0) {
                // 转账失败只可能是金额不合法或余额不足，直接留在弹层里说清楚
            } else {
                onDismiss()
            }
        }
        if (amount.toDoubleOrNull() == null) {
            Text(tr("请输入有效金额", "Enter a valid amount"), color = Func.Danger, fontSize = 10.sp)
        }
    }
}

@Composable
private fun InvestPlanDialog(onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("5000") }
    var days by remember { mutableStateOf("30") }
    TradeSheet(title = tr("定投计划（模拟）", "Auto-invest plan"), onDismiss = onDismiss) {
        Text(
            tr("到期后 App 打开时按市价买入一次，股票按整手取整。", "Buys at market when due while the app is open."),
            color = Text.Muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))
        TradeInput(value = code, onValueChange = { code = it }, placeholder = tr("代码，如 sh600519", "Code, e.g. sh600519"))
        Spacer(Modifier.height(8.dp))
        TradeInput(value = name, onValueChange = { name = it }, placeholder = tr("名称（可选）", "Name (optional)"))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                TradeInput(value = amount, onValueChange = { amount = it }, placeholder = tr("每期金额", "Amount"))
            }
            Box(Modifier.weight(1f)) {
                TradeInput(value = days, onValueChange = { days = it }, placeholder = tr("间隔天数", "Every days"))
            }
        }
        Spacer(Modifier.height(12.dp))
        TradePrimaryButton(tr("保存计划", "Save plan")) {
            val c = code.trim()
            val a = amount.toDoubleOrNull() ?: 0.0
            val d = days.toIntOrNull() ?: 0
            if (c.isBlank() || a <= 0 || d <= 0) {
                // 参数不全就不落库，避免生成一个永远跑不起来的计划
            } else {
                TradeStore.addPlan(c, name.ifBlank { c }, a, d)
                onDismiss()
            }
        }
    }
}

@Composable
private fun PlanListDialog(onDismiss: () -> Unit) {
    TradeSheet(title = tr("定投计划", "Auto-invest plans"), onDismiss = onDismiss) {
        if (TradeStore.plans.isEmpty()) {
            TradeEmpty(tr("还没有定投计划", "No plans yet"))
        }
        TradeStore.plans.forEach { p ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = Text.Primary, fontSize = 12.sp)
                    Text(
                        tr(
                            "每 ${p.everyDays} 天 · ${moneyText(p.amount)} · 下次到期后自动买",
                            "Every ${p.everyDays}d · ${moneyText(p.amount)}"
                        ),
                        color = Text.Muted, fontSize = 10.sp
                    )
                }
                Text(
                    tr("删除", "Remove"),
                    color = Func.Danger,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { TradeStore.removePlan(p.id) }
                )
            }
        }
    }
}

@Composable
private fun OrderHistoryDialog(
    onlyFilled: Boolean,
    onDismiss: () -> Unit,
    onCancel: (Long) -> Unit
) {
    val list = TradeStore.orders.filter { if (onlyFilled) it.filled else true }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }
    TradeSheet(
        title = if (onlyFilled) tr("成交记录", "Filled orders") else tr("委托记录", "All orders"),
        onDismiss = onDismiss
    ) {
        if (list.isEmpty()) {
            TradeEmpty(tr("还没有记录", "No records"))
        }
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(list, key = { it.id }) { o ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${o.side.zh} ${o.name} ${o.qty}股",
                            color = Text.Primary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            fmt.format(Date(o.createdAt)) +
                                (if (o.filled) " · 成交 ${priceText(o.fillPrice)}" else " · ${o.type.zh} ${priceText(o.limitPrice)}"),
                            color = Text.Muted,
                            fontSize = 10.sp
                        )
                    }
                    if (o.status == OrderStatus.PENDING) {
                        Text(
                            tr("撤单", "Cancel"),
                            color = Func.Danger,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Func.Danger.copy(alpha = 0.10f))
                                .clickable { onCancel(o.id) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    } else {
                        Text(
                            tr(o.status.zh, o.status.en),
                            color = when (o.status) {
                                OrderStatus.FILLED -> TradeUp
                                OrderStatus.PENDING -> Brand.Terracotta
                                else -> Text.Muted
                            },
                            fontSize = 10.5.sp
                        )
                    }
                }
                TradeDivider()
            }
        }
    }
}

@Composable
private fun FlowHistoryDialog(onDismiss: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }
    TradeSheet(title = tr("资金流水", "Cash flow"), onDismiss = onDismiss) {
        if (TradeStore.flows.isEmpty()) {
            TradeEmpty(tr("还没有流水", "No cash flow"))
        }
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(TradeStore.flows, key = { it.id }) { f ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.label, color = Text.Primary, fontSize = 12.sp)
                        Text(fmt.format(Date(f.at)), color = Text.Muted, fontSize = 10.sp)
                    }
                    Text(
                        signedPrice(f.amount),
                        color = toneOf(f.amount),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TradeDivider()
            }
        }
    }
}

/** 5 题风险测评：每题 1–4 分，5–10 保守 / 11–15 稳健 / 16–20 积极 */
@Composable
private fun RiskDialog(onDismiss: () -> Unit) {
    val questions = listOf(
        tr("你的投资经验？", "Your experience?") to listOf(
            tr("几乎为零", "None"), tr("偶尔买过", "A little"),
            tr("3–5 年", "3–5 years"), tr("5 年以上", "5+ years")
        ),
        tr("能承受的最大亏损？", "Max loss you accept?") to listOf(
            tr("5% 以内", "<5%"), tr("10% 左右", "~10%"),
            tr("20% 左右", "~20%"), tr("20% 以上", ">20%")
        ),
        tr("这笔钱更看重什么？", "Goal of this money?") to listOf(
            tr("保值", "Preserve"), tr("稳健增值", "Steady growth"),
            tr("较高收益", "Higher return"), tr("尽量高收益", "Max return")
        ),
        tr("投资资金占你闲钱的比例？", "Share of free cash?") to listOf(
            tr("一成以内", "<10%"), tr("三成", "~30%"),
            tr("一半", "~50%"), tr("大部分", "Most")
        ),
        tr("持仓遇到连续大跌，你会？", "In a sharp drop you...?") to listOf(
            tr("立刻卖出", "Sell all"), tr("先减仓", "Trim"),
            tr("继续持有", "Hold"), tr("加仓", "Buy more")
        )
    )
    var answers by remember { mutableStateOf(List(questions.size) { 0 }) }
    TradeSheet(title = tr("风险测评", "Risk assessment"), onDismiss = onDismiss) {
        Text(
            tr("题目只用于给出风险等级参考，不影响真实交易。", "Reference only; no effect on real trading."),
            color = Text.Muted, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            itemsIndexed(questions) { index, (title, options) ->
                Text(title, color = Text.Primary, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    options.forEachIndexed { i, opt ->
                        TradeChip(opt, answers[index] == i + 1) {
                            answers = answers.toMutableList().also { it[index] = i + 1 }
                        }
                    }
                }            }
        }
        Spacer(Modifier.height(12.dp))
        TradePrimaryButton(tr("提交", "Submit")) {
            if (answers.all { it > 0 }) {
                TradeStore.saveRisk(answers.sum())
                onDismiss()
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    TradeSheet(title = title, onDismiss = onDismiss) {
        Text(message, color = Text.Secondary, fontSize = 12.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                TradeGhostButton(tr("取消", "Cancel"), onDismiss)
            }
            Box(Modifier.weight(1f)) {
                TradePrimaryButton(confirm, onConfirm)
            }
        }
    }
}

// ============================================================================
// 小控件
// ============================================================================

@Composable
fun TradeInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Bg.SurfaceVariant.copy(alpha = 1f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = Text.Muted, fontSize = 12.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Text.Primary, fontSize = 13.sp),
            cursorBrush = SolidColor(Brand.Terracotta),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TradePrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brand.Terracotta)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TradeGhostButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Bg.SurfaceVariant.copy(alpha = 1f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Text.Secondary, fontSize = 13.sp)
    }
}

/** 长按：Compose 官方 combinedClickable 需要 opt-in，这里收一层避免每处都写注解 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Modifier.combinedClickableCompat(
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier = this.then(
    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
)

private fun absText(v: Double): String = if (abs(v) < 0.005) "0.00" else priceText(abs(v))
