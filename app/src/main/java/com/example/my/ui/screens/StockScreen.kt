package com.example.my.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.stock.HotStock
import com.example.my.data.stock.MarketQuote
import com.example.my.data.stock.MarketSnapshot
import com.example.my.data.stock.StockApi
import com.example.my.data.stock.StockHit
import com.example.my.data.stock.StockKind
import com.example.my.data.stock.StockMarket
import com.example.my.state.AppState
import com.example.my.ui.components.rememberIsResumed
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.FixedInk
import com.example.my.ui.theme.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

/** 涨红跌绿（A 股习惯），两个色值在浅底和玻璃底上都够用 */
private val UpRed = Color(0xFFD93B33)
private val DownGreen = Color(0xFF15935F)

private enum class MarketTab(val zh: String, val en: String) {
    A("A股", "A-shares"),
    HK("港股", "HK"),
    US("美股", "US"),
    FUND("基金", "Funds")
}

/**
 * 股市页：A 股 / 港股 / 美股主要指数 + A 股涨幅榜 + 指数基金（ETF）行情。
 *
 * 数据来自公开免费接口（腾讯财经、东方财富），只读行情，可能延迟，仅供参照。
 * 从资讯页的「今日行情」卡片进入。
 */
@Composable
fun StockScreen(state: AppState) {
    var snapshot by remember { mutableStateOf<MarketSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(MarketTab.A) }
    var detailCode by remember { mutableStateOf<String?>(null) }
    /** 搜索：输入即查（350ms 防抖），结果直接进详情 */
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<StockHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load(showSpinner: Boolean) {
        if (showSpinner) loading = true
        runCatching { withContext(Dispatchers.IO) { StockApi.snapshot() } }
            .onSuccess {
                snapshot = it
                error = null
            }
            .onFailure { error = it.message ?: tr("行情加载失败", "Quote load failed") }
        loading = false
    }

    // 只有在页面可见（App 在前台）时才自动刷新：
    // 退到后台后 Composition 还在，继续每分钟拉三个网络请求纯属耗电耗流量。
    val resumed = rememberIsResumed()
    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        load(showSpinner = snapshot == null)
        while (true) {
            delay(60_000)
            load(showSpinner = false)
        }
    }

    // 搜索防抖：每敲一个字都发请求既慢又费流量，停手 350ms 再查
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            hits = emptyList()
            searchError = null
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(350)
        runCatching { withContext(Dispatchers.IO) { StockApi.search(q) } }
            .onSuccess {
                hits = it
                searchError = null
            }
            .onFailure { searchError = it.message ?: tr("搜索失败", "Search failed") }
        searching = false
    }

    // 点开某只票/某个指数 → 详情页（自带返回处理，这里先交出渲染权）
    val openDetail = detailCode
    if (openDetail != null) {
        StockDetailScreen(code = openDetail, onBack = { detailCode = null })
        return
    }

    BackHandler(enabled = true) { state.closeScreen() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg.Background)
            .statusBarsPadding()
    ) {
        StockHeader(
            updatedAt = snapshot?.updatedAt,
            onBack = { state.closeScreen() },
            onRefresh = { scope.launch { load(showSpinner = true) } }
        )
        MarketTabs(selected = tab, onSelect = { tab = it })
        StockSearchBar(
            query = query,
            onQuery = { query = it },
            searching = searching
        )

        // 有输入就切到搜索结果：代码、名称、拼音都能查，股票/ETF/指数混排
        if (query.trim().isNotEmpty()) {
            when {
                searching && hits.isEmpty() -> StockMessage(tr("正在搜索…", "Searching…"), true)
                searchError != null -> StockMessage(searchError ?: "", false)
                hits.isEmpty() -> StockMessage(
                    tr("没找到「${query.trim()}」，试试六位代码或公司简称", "Nothing found for “${query.trim()}”"),
                    false
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp)
                ) {
                    items(hits, key = { it.code }) { hit ->
                        SearchResultRow(hit, onPick = { detailCode = hit.code })
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
            return@Column
        }

        val snap = snapshot
        when {
            snap == null && loading -> StockMessage(tr("正在拉取行情…", "Loading quotes…"), true)
            snap == null && error != null -> StockMessage(error ?: "", false, onRetry = {
                scope.launch { load(showSpinner = true) }
            })
            snap == null -> StockMessage(tr("暂无行情数据", "No quote data"), false)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp)
            ) {
                val indexes = when (tab) {
                    MarketTab.A -> snap.aShare
                    MarketTab.HK -> snap.hk
                    MarketTab.US -> snap.us
                    MarketTab.FUND -> snap.funds
                }
                item {
                    SectionHead(
                        title = when (tab) {
                            MarketTab.A -> tr("A股指数", "A-share indices")
                            MarketTab.HK -> tr("港股指数", "HK indices")
                            MarketTab.US -> tr("美股指数", "US indices")
                            MarketTab.FUND -> tr("指数基金 / ETF", "Index funds & ETFs")
                        },
                        subtitle = when (tab) {
                            MarketTab.A, MarketTab.HK, MarketTab.US ->
                                tr("实时报价，延迟可能 ≥15 分钟", "Live-ish quotes, may lag 15min+")
                            MarketTab.FUND ->
                                tr("按涨幅排序 · 行情参考，非推荐", "Sorted by gain · reference only")
                        }
                    )
                }
                items(indexes, key = { it.code }) { quote ->
                    QuoteRow(quote, onPick = { detailCode = it })
                    Spacer(Modifier.height(8.dp))
                }
                if (tab == MarketTab.A && snap.hot.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(10.dp))
                        SectionHead(
                            title = tr("热门股票 · 涨幅榜", "Hot stocks · top gainers"),
                            subtitle = tr("A股全市场按涨跌幅排序", "Whole A-share market by gain")
                        )
                    }
                    items(snap.hot, key = { it.code }) { stock ->
                        HotStockRow(stock, onPick = { detailCode = it })
                        Spacer(Modifier.height(6.dp))
                    }
                }
                item {
                    Spacer(Modifier.height(14.dp))
                    Disclaimer()
                }
            }
        }
    }
}

@Composable
private fun StockHeader(updatedAt: String?, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = tr("返回资讯", "Back to news"),
                tint = Text.Primary,
                modifier = Modifier.size(21.dp)
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(FixedInk.ChipSurface)
                .padding(horizontal = 13.dp, vertical = 2.dp)
        ) {
            Text(
                text = tr("行情", "Markets"),
                color = FixedInk.OnChip,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (updatedAt != null) {
                tr("更新 $updatedAt", "Updated $updatedAt")
            } else {
                tr("A股 · 港股 · 美股 · 基金", "A / HK / US / Funds")
            },
            color = Text.Muted,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onRefresh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = tr("刷新行情", "Refresh quotes"),
                tint = Text.Primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MarketTabs(selected: MarketTab, onSelect: (MarketTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MarketTab.entries.forEach { item ->
            val active = item == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) FixedInk.ChipSurface else Bg.Surface)
                    .border(1.dp, if (active) FixedInk.ChipSurface else Bg.Outline, RoundedCornerShape(50))
                    .clickable { onSelect(item) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tr(item.zh, item.en),
                    color = if (active) FixedInk.OnChip else Text.Secondary,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SectionHead(title: String, subtitle: String) {
    Column(Modifier.padding(top = 6.dp, bottom = 10.dp)) {
        Text(
            text = title,
            color = Text.Primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = Text.Muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuoteRow(quote: MarketQuote, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Bg.Surface)
            .border(1.dp, Bg.Outline, RoundedCornerShape(16.dp))
            .clickable { onPick(quote.code) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = quote.name,
                color = Text.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = codeLabel(quote),
                color = Text.Muted,
                fontSize = 10.5.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            ChangeBar(quote.changePct)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = priceText(quote.price),
                color = if (quote.changePct >= 0) UpRed else DownGreen,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1
            )
            Spacer(Modifier.height(3.dp))
            PctPill(quote.changePct)
        }
    }
}

@Composable
private fun HotStockRow(stock: HotStock, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Bg.Surface)
            .clickable { onPick(stock.code) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stock.name,
                color = Text.Primary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = codeOf(stock.code, stock.name),
                color = Text.Muted,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = priceText(stock.price),
                color = if (stock.changePct >= 0) UpRed else DownGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = turnoverText(stock.turnover),
                color = Text.Muted,
                fontSize = 9.5.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(50))
                .background(if (stock.changePct >= 0) UpRed.copy(alpha = 0.10f) else DownGreen.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = pctText(stock.changePct),
                color = if (stock.changePct >= 0) UpRed else DownGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/** 中间为 0 轴的双向幅度条：涨往右、跌往左，幅度按 5% 归一 */
@Composable
private fun ChangeBar(pct: Double) {
    val frac = (abs(pct) / 5.0).coerceIn(0.0, 1.0).toFloat()
    Row(Modifier.fillMaxWidth().height(4.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight().background(Bg.SurfaceVariant)) {
            if (pct < 0) {
                Box(
                    Modifier
                        .fillMaxWidth(frac)
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd)
                        .background(DownGreen)
                )
            }
        }
        Spacer(Modifier.width(2.dp))
        Box(Modifier.weight(1f).fillMaxHeight().background(Bg.SurfaceVariant)) {
            if (pct >= 0) {
                Box(
                    Modifier
                        .fillMaxWidth(frac)
                        .fillMaxHeight()
                        .align(Alignment.CenterStart)
                        .background(UpRed)
                )
            }
        }
    }
}

@Composable
private fun PctPill(pct: Double) {
    val color = if (pct >= 0) UpRed else DownGreen
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.11f))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text = pctText(pct),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun Disclaimer() {
    Text(
        text = tr(
            "数据来源：腾讯财经、东方财富公开行情接口，仅含指数与个股报价，可能延迟，仅供参考，不构成投资建议。",
            "Sources: Tencent Finance and Eastmoney public quote feeds. Delayed data for reference only, not investment advice."
        ),
        color = Text.Muted,
        fontSize = 10.5.sp,
        lineHeight = 16.sp
    )
}

@Composable
private fun StockMessage(text: String, loading: Boolean, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (loading) tr("正在拉取行情…", "Loading quotes…") else text,
            color = Text.Secondary,
            fontSize = 13.sp
        )
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(FixedInk.ChipSurface)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = tr("重试", "Retry"),
                    color = FixedInk.OnChip,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun priceText(value: Double): String =
    String.format(Locale.CHINA, "%,.2f", value)

private fun pctText(pct: Double): String {
    val sign = if (pct > 0) "+" else if (pct < 0) "-" else ""
    return sign + String.format(Locale.CHINA, "%.2f%%", abs(pct))
}

private fun turnoverText(yuan: Double): String = when {
    yuan >= 1e8 -> String.format(Locale.CHINA, "%.1f亿", yuan / 1e8)
    yuan >= 1e4 -> String.format(Locale.CHINA, "%.0f万", yuan / 1e4)
    yuan > 0.0 -> String.format(Locale.CHINA, "%.0f", yuan)
    else -> "—"
}

private fun codeLabel(quote: MarketQuote): String {
    val market = when (quote.market) {
        StockMarket.A_SHARE -> tr("A股", "A")
        StockMarket.HK -> tr("港股", "HK")
        StockMarket.US -> tr("美股", "US")
        StockMarket.FUND -> tr("基金", "Fund")
    }
    return "$market · ${quote.code.uppercase(Locale.US)}"
}

/** 东财 f12 只给 6 位代码，市场位靠 f13 推不出来，这里按代码首位补个前缀 */
private fun codeOf(code: String, name: String): String = when {
    code.startsWith("6") -> "SH$code"
    code.startsWith("0") || code.startsWith("3") -> "SZ$code"
    code.startsWith("8") || code.startsWith("4") -> "BJ$code"
    else -> code
}


/**
 * 顶部搜索框：股票 / 基金 / 指数通用。
 * 用 BasicTextField 而不是 Material 输入框，是为了贴合页面已有的胶囊搜索样式。
 */
@Composable
private fun StockSearchBar(query: String, onQuery: (String) -> Unit, searching: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(Bg.Surface)
            .border(1.dp, Bg.Outline, RoundedCornerShape(50))
            .padding(horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = Text.Muted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = Text.Primary, fontSize = 13.sp),
                cursorBrush = SolidColor(UpRed),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            if (searching) tr("正在搜索…", "Searching…")
                            else tr("搜索股票 / 基金 / 指数（代码、名称、拼音）",
                                "Search stocks / funds / indices (code, name, pinyin)"),
                            color = Text.Muted, fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Box { inner() }
                    }
                }
            )
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = tr("清空", "Clear"),
                    tint = Text.Muted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onQuery("") }
                )
            }
        }
    }
}

/** 搜索结果一行：名称 + 代码，右侧标出是股票还是基金/指数 */
@Composable
private fun SearchResultRow(hit: StockHit, onPick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Bg.Surface)
            .clickable(onClick = onPick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                hit.name,
                color = Text.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(codeLabel(hit.code), color = Text.Muted, fontSize = 11.sp)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Bg.SurfaceVariant)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(kindLabel(hit.kind), color = Text.Secondary, fontSize = 10.sp)
        }
    }
}

private fun kindLabel(kind: StockKind): String = when (kind) {
    StockKind.STOCK -> tr("股票", "Stock")
    StockKind.FUND -> tr("基金", "Fund")
    StockKind.INDEX -> tr("指数", "Index")
    StockKind.OTHER -> tr("其他", "Other")
}

/** 美股代码本身就要大写，直接大写整串会变成 USAAPL，这里分开显示 */
private fun codeLabel(code: String): String =
    if (code.startsWith("us")) "US · " + code.removePrefix("us") else code.uppercase(Locale.US)