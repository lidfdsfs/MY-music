package com.example.my.ui.trade

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.news.NewsApi
import com.example.my.data.news.NewsBrief
import com.example.my.data.stock.MarketQuote
import com.example.my.data.stock.MarketSnapshot
import com.example.my.data.stock.StockApi
import com.example.my.data.trade.QuoteLite
import com.example.my.data.trade.TradeSide
import com.example.my.data.trade.TradeStore
import com.example.my.state.AppState
import com.example.my.ui.components.rememberIsResumed
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Layer
import com.example.my.ui.theme.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 交易模块的五个主页面 */
enum class TradeTab(val zh: String, val en: String) {
    HOME("资产", "Assets"),
    MARKET("行情", "Markets"),
    ORDER("交易", "Trade"),
    POSITIONS("持仓", "Positions"),
    ME("我的", "Me")
}

/**
 * 「我的交易」整套页面。
 *
 * 一个入口、五个主页面 + 一个全屏看盘页：
 * 资产 / 行情自选 / 交易委托 / 持仓 / 我的；点任意标的进详情（分时、K 线、盘口、指标、公告）。
 *
 * 行情用 [StockApi] 的公开接口，60 秒刷新一次；账户与撮合在 [TradeStore] 本地完成。
 * 页面里「模拟盘」标注始终可见——这里不做真实委托。
 */
@Composable
fun TradeScreen(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resumed = rememberIsResumed()

    LaunchedEffect(Unit) { TradeStore.init(context) }

    var tab by remember { mutableStateOf(TradeTab.HOME) }
    var detailCode by remember { mutableStateOf<String?>(null) }
    var orderTarget by remember { mutableStateOf<QuoteLite?>(null) }
    var orderSide by remember { mutableStateOf(TradeSide.BUY) }

    var snapshot by remember { mutableStateOf<MarketSnapshot?>(null) }
    var heldQuotes by remember { mutableStateOf<List<MarketQuote>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var brief by remember { mutableStateOf<NewsBrief?>(null) }

    suspend fun refresh(showSpinner: Boolean) {
        if (showSpinner) loading = true
        // 自选 + 持仓的代码单独批量报价：它们不在指数 / ETF 快照里
        val needCodes = (TradeStore.watchlist.map { it.code } +
            TradeStore.positions.map { it.code }).distinct()
        runCatching {
            withContext(Dispatchers.IO) {
                val snap = StockApi.snapshot()
                val extra = if (needCodes.isEmpty()) emptyList() else StockApi.quotes(needCodes)
                Triple(snap, extra, runCatching { NewsApi.brief() }.getOrNull())
            }
        }.onSuccess { (snap, extra, b) ->
            snapshot = snap
            heldQuotes = extra
            if (b != null) brief = b
            error = null
        }.onFailure {
            error = it.message ?: tr("行情加载失败", "Quote load failed")
        }
        loading = false
    }

    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        refresh(showSpinner = snapshot == null)
        while (true) {
            delay(60_000)
            if (snapshot != null || !loading) refresh(showSpinner = false)
        }
    }

    // 指数 + ETF + 涨幅榜 + 自选持仓 → 一张按带前缀代码索引的报价表
    val quotes: Map<String, QuoteLite> = remember(snapshot, heldQuotes) {
        val map = LinkedHashMap<String, QuoteLite>()
        fun put(q: MarketQuote) {
            map[q.code] = q.toLite()
        }
        snapshot?.let { snap ->
            (snap.aShare + snap.hk + snap.us + snap.funds).forEach { put(it) }
            snap.hot.forEach { h ->
                val code = StockApi.tencentCode(h.code)
                map[code] = QuoteLite(
                    code = code,
                    name = h.name,
                    price = h.price,
                    prevClose = if (h.changePct != -100.0) h.price / (1 + h.changePct / 100.0) else h.price,
                    changePct = h.changePct,
                    turnover = h.turnover
                )
            }
        }
        heldQuotes.forEach { put(it) }
        map
    }

    // 每次拿到新行情就推进一次挂单：限价单到价即成交，不靠用户手动点刷新
    LaunchedEffect(quotes) {
        if (quotes.isEmpty()) return@LaunchedEffect
        val filled = runCatching { withContext(Dispatchers.Default) { TradeStore.settlePending(quotes) } }
            .getOrDefault(0)
        if (filled > 0) state.toast(tr("有 $filled 笔挂单已成交", "$filled pending order(s) filled"))
        // 到期定投也在这时跑：与挂单共用一次行情，不额外发请求
        val invested = runCatching { withContext(Dispatchers.Default) { TradeStore.runDuePlans(quotes) } }
            .getOrDefault(0)
        if (invested > 0) state.toast(tr("有 $invested 笔定投已按市价买入", "$invested auto-invest plan(s) executed"))
    }

    fun openTrade(code: String, side: TradeSide, known: QuoteLite?) {
        detailCode = null
        orderSide = side
        if (code.isBlank()) {
            // 快捷入口直接进委托页挑标的，不要弹「拿不到报价」
            orderTarget = null
            tab = TradeTab.ORDER
            return
        }
        if (known != null) {
            orderTarget = known
            tab = TradeTab.ORDER
        } else {
            // 从搜索里选中的标的没有现价，单独补一次报价再进委托页
            scope.launch {
                val q = withContext(Dispatchers.IO) {
                    StockApi.quotes(listOf(code)).firstOrNull()
                }
                if (q == null) {
                    state.toast(tr("拿不到该标的最新价，稍后再试", "No quote for this symbol yet"))
                } else {
                    orderTarget = q.toLite()
                    tab = TradeTab.ORDER
                }
            }
        }
    }

    // 返回逻辑：
    // 1) 看盘页开着 → 返回只关看盘页，回到之前的 tab；
    // 2) 其余任何 tab → 返回关闭整个「我的交易」，回到入口来源页。
    //    （侧滑跟手返回由外层 SwipeBackPage 负责，系统返回键在这里兜底）
    BackHandler(enabled = detailCode != null) { detailCode = null }
    BackHandler(enabled = detailCode == null) { state.closeScreen() }

    Box(Modifier.fillMaxSize().background(Bg.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    TradeTab.HOME -> TradeHomePage(
                        quotes = quotes,
                        snapshot = snapshot,
                        brief = brief,
                        loading = loading,
                        onOpenDetail = { detailCode = it },
                        onTrade = { code, side, known -> openTrade(code, side, known) },
                        onTab = { tab = it }
                    )
                    TradeTab.MARKET -> TradeMarketPage(
                        quotes = quotes,
                        snapshot = snapshot,
                        loading = loading,
                        error = error,
                        onRefresh = {
                            scope.launch {
                                withContext(Dispatchers.IO) { runCatching { StockApi.snapshot() } }
                            }
                        },
                        onOpenDetail = { detailCode = it },
                        onTrade = { code, side, known -> openTrade(code, side, known) }
                    )
                    TradeTab.ORDER -> TradeOrderPage(
                        target = orderTarget,
                        side = orderSide,
                        quotes = quotes,
                        onSideChange = { orderSide = it },
                        onTarget = { orderTarget = it },
                        onOpenDetail = { detailCode = it },
                        onPickByCode = { code, name -> openTrade(code, orderSide, null) },
                        onToast = { state.toast(it) }
                    )
                    TradeTab.POSITIONS -> TradePositionsPage(
                        quotes = quotes,
                        onOpenDetail = { detailCode = it },
                        onTrade = { code, side -> openTrade(code, side, quotes[code]) }
                    )
                    TradeTab.ME -> TradeMePage(state = state, quotes = quotes)
                }
            }
            TradeBottomBar(current = tab, onSelect = { tab = it })
        }

        // 看盘页盖在最上层：返回落在原来的 tab，不把用户扔回首页。
        // 覆盖层用不透明底：自定义背景开启时底图与下面的 tab 内容都不能透上来
        detailCode?.let { code ->
            Box(Modifier.fillMaxSize().background(Layer.Opaque)) {
                TradeDetailPage(
                    code = code,
                    quotes = quotes,
                    onBack = { detailCode = null },
                    onTrade = { side, quote -> openTrade(quote.code, side, quote) },
                    onToast = { state.toast(it) }
                )
            }
        }
    }
}

@Composable
private fun TradeBottomBar(current: TradeTab, onSelect: (TradeTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Bg.Surface.copy(alpha = 1f))
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icons = listOf(
            TradeTab.HOME to Icons.Outlined.Home,
            TradeTab.MARKET to Icons.Outlined.ShowChart,
            TradeTab.ORDER to Icons.Outlined.SwapHoriz,
            TradeTab.POSITIONS to Icons.Outlined.AccountBalanceWallet,
            TradeTab.ME to Icons.Outlined.Person
        )
        icons.forEach { (t, icon) ->
            val selected = current == t
            val tint = if (selected) Brand.Terracotta else Text.Muted
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(t) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = tr(t.zh, t.en), tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(3.dp))
                Text(
                    tr(t.zh, t.en),
                    color = tint,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}

/** 顶部标题栏：交易各页共用 */
@Composable
fun TradeTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = Text.Primary, fontSize = 22.sp)
            }
            Spacer(Modifier.width(2.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Text.Primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, color = Text.Muted, fontSize = 10.sp)
            }
        }
        trailing?.invoke()
    }
}

/** 页面通用横向内边距 */
val TradePagePadding = Modifier.padding(horizontal = 16.dp)

/** 小分隔条 */
@Composable
fun TradeGap(h: Int = 12) {
    Spacer(Modifier.height(h.dp))
}

/** 强调色的价格色块（详情顶部的涨跌背景） */
val TradeToneSoft: Color = Brand.Terracotta.copy(alpha = 0.10f)
