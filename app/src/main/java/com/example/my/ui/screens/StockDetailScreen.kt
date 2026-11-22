package com.example.my.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.stock.Candle
import com.example.my.data.stock.MinutePoint
import com.example.my.data.stock.OrderLevel
import com.example.my.data.stock.StockApi
import com.example.my.data.stock.StockDetail
import com.example.my.data.stock.StockMarket
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.FixedInk
import com.example.my.ui.theme.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val DetailUp = Color(0xFFD93B33)
private val DetailDown = Color(0xFF15935F)
private val AvgLine = Color(0xFFE0952F)
private val MaLine = Color(0xFF3F7BD6)

/**
 * 个股 / 指数详情：报价 + 分时 + 日 K + 五档盘口。
 *
 * 从股市页点指数卡或热门股进入；五档只有 A 股个股才有，指数和 ETF 份数不生效就不显示。
 */
@Composable
fun StockDetailScreen(
    code: String,
    onBack: () -> Unit,
    /** 底部动作条（交易页放买入/卖出）；普通看盘传 null */
    bottomBar: (@Composable (StockDetail) -> Unit)? = null,
    /** 追加区块（交易页放指标 / 公告 / 资讯 / 基金信息） */
    extraSection: (@Composable (StockDetail) -> Unit)? = null
) {
    var detail by remember(code) { mutableStateOf<StockDetail?>(null) }
    var loading by remember(code) { mutableStateOf(true) }
    var error by remember(code) { mutableStateOf<String?>(null) }
    /** K 线周期：day / week / month；日 K 用详情里已经拉到的那份，周月 K 按需单独拉 */
    var period by remember(code) { mutableStateOf("day") }
    var periodCandles by remember(code) { mutableStateOf<List<Candle>>(emptyList()) }
    var periodLoading by remember(code) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        runCatching { withContext(Dispatchers.IO) { StockApi.detail(code) } }
            .onSuccess {
                detail = it
                error = null
            }
            .onFailure { error = it.message ?: tr("详情加载失败", "Failed to load") }
        loading = false
    }

    LaunchedEffect(code) { load() }

    // 切到周 / 月 K 才发请求；切回日 K 直接复用详情里的日 K，省一次网络
    LaunchedEffect(code, period) {
        if (period == "day") {
            periodLoading = false
            return@LaunchedEffect
        }
        periodLoading = true
        periodCandles = withContext(Dispatchers.IO) { StockApi.candles(code, period) }
        periodLoading = false
    }

    BackHandler(enabled = true) { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg.Background)
            .statusBarsPadding()
    ) {
        DetailHeader(
            detail = detail,
            loading = loading,
            onBack = onBack,
            onRefresh = { scope.launch { load() } }
        )
        val current = detail
        when {
            current == null && loading -> DetailMessage(tr("正在拉取详情…", "Loading…"))
            current == null -> DetailMessage(error ?: tr("暂无数据", "No data"), retry = {
                scope.launch { load() }
            })
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp)
            ) {
                item { PriceBlock(current) }
                item {
                    ChartCard(
                        title = tr("分时", "Intraday"),
                        subtitle = if (current.minute.isNotEmpty()) {
                            tr("今日走势 · 均价线为橙色", "Today · avg line in orange")
                        } else {
                            tr("该市场暂无分时数据", "No intraday data for this market")
                        }
                    ) {
                        if (current.minute.isEmpty()) {
                            ChartEmpty(tr("暂无分时数据", "No intraday data"))
                        } else {
                            MinuteChart(
                                points = current.minute,
                                prevClose = current.prevClose,
                                modifier = Modifier.fillMaxWidth().height(190.dp)
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = current.minute.first().label,
                                    color = Text.Muted,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = current.minute.last().label,
                                    color = Text.Muted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                item {
                    PeriodChips(period = period, onSelect = { period = it })
                }
                item {
                    val candles = if (period == "day") current.candles else periodCandles
                    ChartCard(
                        title = when (period) {
                            "week" -> tr("周 K · 近 60 周", "Weekly K · last 60 weeks")
                            "month" -> tr("月 K · 近 60 月", "Monthly K · last 60 months")
                            else -> tr("日 K · 近 60 日", "Daily K · last 60 days")
                        },
                        subtitle = if (periodLoading) {
                            tr("正在加载…", "Loading…")
                        } else {
                            tr("前复权 · 蓝色为 MA5", "Adjusted · blue = MA5")
                        }
                    ) {
                        if (candles.isEmpty()) {
                            ChartEmpty(
                                if (periodLoading) tr("正在加载…", "Loading…")
                                else when (period) {
                                    "week" -> tr("暂无周 K 数据", "No weekly candles")
                                    "month" -> tr("暂无月 K 数据", "No monthly candles")
                                    else -> tr("暂无日 K 数据", "No daily candles")
                                }
                            )
                        } else {
                            CandleChart(
                                candles = candles,
                                modifier = Modifier.fillMaxWidth().height(200.dp)
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = candles.first().date,
                                    color = Text.Muted,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = candles.last().date,
                                    color = Text.Muted,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = tr(
                                        "高 ${priceText(candles.maxOf { it.high })}",
                                        "High ${priceText(candles.maxOf { it.high })}"
                                    ),
                                    color = Text.Muted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                if (current.bid.isNotEmpty() || current.ask.isNotEmpty()) {
                    item {
                        ChartCard(
                            title = tr("五档盘口", "Order book"),
                            subtitle = tr("买一至买五 / 卖一至卖五", "5 bid / 5 ask levels")
                        ) {
                            OrderBook(current)
                        }
                    }
                }
                if (extraSection != null) {
                    item { extraSection.invoke(current) }
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = tr(
                            "数据来源：腾讯财经公开行情接口，可能延迟，仅供参考，不构成投资建议。",
                            "Source: Tencent Finance public feed. Delayed data, reference only, not investment advice."
                        ),
                        color = Text.Muted,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
        // 交易页的底部动作条：只在详情已加载出来时才能买卖，避免拿不到价格就下单
        val loadedDetail = detail
        if (loadedDetail != null && bottomBar != null) {
            bottomBar.invoke(loadedDetail)
        }
    }
}

@Composable
private fun DetailHeader(
    detail: StockDetail?,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
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
                contentDescription = tr("返回行情", "Back"),
                tint = Text.Primary,
                modifier = Modifier.size(21.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = detail?.name ?: tr("加载中…", "Loading…"),
                    color = Text.Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                if (detail != null) MarketBadge(detail.market)
            }
            Text(
                text = detail?.let { "${codeDisplay(it.code)} · ${it.updatedAt}" }.orEmpty(),
                color = Text.Muted,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(enabled = !loading, onClick = onRefresh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = tr("刷新详情", "Refresh"),
                tint = if (loading) Text.Muted else Text.Primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MarketBadge(market: StockMarket) {
    val label = when (market) {
        StockMarket.A_SHARE -> tr("A股", "A")
        StockMarket.HK -> tr("港股", "HK")
        StockMarket.US -> tr("美股", "US")
        StockMarket.FUND -> tr("基金", "ETF")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Bg.SurfaceVariant)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text = label, color = Text.Secondary, fontSize = 9.5.sp, maxLines = 1)
    }
}

@Composable
private fun PriceBlock(detail: StockDetail) {
    val up = detail.changePct >= 0
    val tone = if (up) DetailUp else DetailDown
    val delta = detail.price - detail.prevClose
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = priceText(detail.price),
                color = tone,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = signedText(delta),
                    color = tone,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = pctText(detail.changePct),
                    color = tone,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Bg.Surface)
                .border(1.dp, Bg.Outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            StatCell(tr("今开", "Open"), priceText(detail.open), Modifier.weight(1f))
            StatCell(tr("最高", "High"), priceText(detail.high), Modifier.weight(1f))
            StatCell(tr("最低", "Low"), priceText(detail.low), Modifier.weight(1f))
            StatCell(tr("昨收", "Prev"), priceText(detail.prevClose), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Bg.Surface)
                .border(1.dp, Bg.Outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            StatCell(tr("成交额", "Turnover"), moneyText(detail.turnoverYuan), Modifier.weight(1f))
            StatCell(tr("成交量", "Volume"), volumeText(detail.volumeHand), Modifier.weight(1f))
            StatCell(tr("振幅", "Range"), pctText(rangePct(detail)), Modifier.weight(1f))
        }
        // 交易软件里最常看的一组：换手、量比、估值、市值与涨跌停
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Bg.Surface)
                .border(1.dp, Bg.Outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            StatCell(tr("换手", "Turnover"), pctOrDash(detail.turnoverRate), Modifier.weight(1f))
            StatCell(tr("量比", "Vol ratio"), numOrDash(detail.volumeRatio), Modifier.weight(1f))
            StatCell(tr("市盈率", "P/E"), numOrDash(detail.pe), Modifier.weight(1f))
            StatCell(tr("市净率", "P/B"), numOrDash(detail.pb), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Bg.Surface)
                .border(1.dp, Bg.Outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            StatCell(tr("总市值", "Mkt cap"), capText(detail.totalCapYi), Modifier.weight(1f))
            StatCell(tr("流通市值", "Float cap"), capText(detail.floatCapYi), Modifier.weight(1f))
            StatCell(tr("涨停", "Limit up"), priceOrDash(detail.limitUp), Modifier.weight(1f))
            StatCell(tr("跌停", "Limit dn"), priceOrDash(detail.limitDown), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(text = label, color = Text.Muted, fontSize = 9.5.sp, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = Text.Primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Bg.Surface)
            .border(1.dp, Bg.Outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, color = Text.Primary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                text = subtitle,
                color = Text.Muted,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** 分时：昨收基准虚线 + 价格折线 + 均价线（橙色）+ 下方淡色面积 */
@Composable
private fun MinuteChart(points: List<MinutePoint>, prevClose: Double, modifier: Modifier) {
    val last = points.last().price
    val tone = if (last >= prevClose) DetailUp else DetailDown
    Canvas(modifier) {
        var lo = points.minOf { min(it.price, it.avg) }
        var hi = points.maxOf { max(it.price, it.avg) }
        if (prevClose > 0) {
            lo = min(lo, prevClose)
            hi = max(hi, prevClose)
        }
        val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
        fun y(value: Double): Float =
            (size.height - ((value - lo) / span * size.height)).toFloat()

        if (prevClose > 0) {
            drawLine(
                color = Text.Muted.copy(alpha = 0.45f),
                start = Offset(0f, y(prevClose)),
                end = Offset(size.width, y(prevClose)),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
        }

        val step = if (points.size > 1) size.width / (points.size - 1) else size.width
        val pricePath = Path()
        val areaPath = Path()
        points.forEachIndexed { i, p ->
            val x = step * i
            if (i == 0) {
                pricePath.moveTo(x, y(p.price))
                areaPath.moveTo(x, size.height)
                areaPath.lineTo(x, y(p.price))
            } else {
                pricePath.lineTo(x, y(p.price))
                areaPath.lineTo(x, y(p.price))
            }
        }
        areaPath.lineTo(size.width, size.height)
        areaPath.close()
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(tone.copy(alpha = 0.16f), Color.Transparent)
            )
        )
        drawPath(
            path = pricePath,
            color = tone,
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )

        val avgPath = Path()
        points.forEachIndexed { i, p ->
            val x = step * i
            if (i == 0) avgPath.moveTo(x, y(p.avg)) else avgPath.lineTo(x, y(p.avg))
        }
        drawPath(
            path = avgPath,
            color = AvgLine.copy(alpha = 0.85f),
            style = Stroke(width = 1.2f)
        )
    }
}

/** 日 K：红涨绿跌的实体 + 上下影线 + MA5 */
@Composable
private fun CandleChart(candles: List<Candle>, modifier: Modifier) {
    Canvas(modifier) {
        val n = candles.size
        if (n == 0) return@Canvas
        val hi = candles.maxOf { it.high }
        val lo = candles.minOf { it.low }
        val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
        fun y(value: Double): Float =
            (size.height - ((value - lo) / span * size.height)).toFloat()
        val cw = size.width / n
        val bodyW = max(cw * 0.56f, 1.6f)

        candles.forEachIndexed { i, c ->
            val color = if (c.close >= c.open) DetailUp else DetailDown
            val cx = cw * (i + 0.5f)
            drawLine(color, Offset(cx, y(c.high)), Offset(cx, y(c.low)), 1.2f)
            val top = y(max(c.open, c.close))
            val bottom = y(min(c.open, c.close))
            drawRect(
                color = color,
                topLeft = Offset(cx - bodyW / 2f, top),
                size = Size(bodyW, max(bottom - top, 1.6f))
            )
        }

        if (n >= 5) {
            val maPath = Path()
            for (i in 4 until n) {
                val ma = (i - 4..i).sumOf { candles[it].close } / 5.0
                val x = cw * (i + 0.5f)
                val yy = y(ma)
                if (i == 4) maPath.moveTo(x, yy) else maPath.lineTo(x, yy)
            }
            drawPath(maPath, MaLine.copy(alpha = 0.9f), style = Stroke(width = 1.4f))
        }
    }
}

@Composable
private fun OrderBook(detail: StockDetail) {
    val levels = detail.ask + detail.bid
    val maxVol = (levels.maxOfOrNull { it.volume } ?: 1L).coerceAtLeast(1L)
    Column {
        // 卖五 → 卖一（列表本身是卖一在前，倒着画才符合看盘习惯）
        detail.ask.reversed().forEachIndexed { index, level ->
            val label = tr("卖${detail.ask.size - index}", "Ask ${detail.ask.size - index}")
            OrderRow(label, level, detail.prevClose, maxVol, DetailDown)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tr("最新", "Last"),
                color = Text.Muted,
                fontSize = 10.5.sp,
                modifier = Modifier.width(44.dp)
            )
            Text(
                text = priceText(detail.price),
                color = if (detail.changePct >= 0) DetailUp else DetailDown,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = pctText(detail.changePct),
                color = if (detail.changePct >= 0) DetailUp else DetailDown,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        detail.bid.forEachIndexed { index, level ->
            val label = tr("买${index + 1}", "Bid ${index + 1}")
            OrderRow(label, level, detail.prevClose, maxVol, DetailUp)
        }
    }
}

@Composable
private fun OrderRow(label: String, level: OrderLevel, prevClose: Double, maxVol: Long, tone: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Text.Muted,
            fontSize = 10.5.sp,
            modifier = Modifier.width(44.dp)
        )
        Text(
            text = priceText(level.price),
            color = if (level.price >= prevClose) DetailUp else DetailDown,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(78.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Bg.SurfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth((level.volume.toFloat() / maxVol).coerceIn(0.02f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(tone.copy(alpha = 0.55f))
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${level.volume}",
            color = Text.Secondary,
            fontSize = 10.5.sp,
            modifier = Modifier.width(64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1
        )
    }
}

@Composable
private fun ChartEmpty(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Text.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun DetailMessage(text: String, retry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = text, color = Text.Secondary, fontSize = 13.sp)
        if (retry != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(FixedInk.ChipSurface)
                    .clickable(onClick = retry)
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
    if (value == 0.0) "—" else String.format(Locale.CHINA, "%,.2f", value)

private fun signedText(value: Double): String {
    val sign = if (value > 0) "+" else if (value < 0) "-" else ""
    return sign + String.format(Locale.CHINA, "%,.2f", abs(value))
}

private fun pctText(pct: Double): String {
    val sign = if (pct > 0) "+" else if (pct < 0) "-" else ""
    return sign + String.format(Locale.CHINA, "%.2f%%", abs(pct))
}

private fun moneyText(yuan: Double): String = when {
    yuan >= 1e8 -> String.format(Locale.CHINA, "%.2f亿", yuan / 1e8)
    yuan >= 1e4 -> String.format(Locale.CHINA, "%.0f万", yuan / 1e4)
    yuan > 0.0 -> String.format(Locale.CHINA, "%.0f", yuan)
    else -> "—"
}

private fun volumeText(hand: Long): String = when {
    hand >= 10_000L -> String.format(Locale.CHINA, "%.1f万手", hand / 10_000.0)
    hand > 0L -> "$hand 手"
    else -> "—"
}

private fun rangePct(detail: StockDetail): Double =
    if (detail.prevClose > 0 && detail.high > 0) {
        (detail.high - detail.low) / detail.prevClose * 100
    } else {
        0.0
    }

/** 日 / 周 / 月 K 切换：和行情页的分段 chips 同一套视觉 */
@Composable
private fun PeriodChips(period: String, onSelect: (String) -> Unit) {
    val options = listOf(
        "day" to tr("日 K", "Day"),
        "week" to tr("周 K", "Week"),
        "month" to tr("月 K", "Month")
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (key, label) ->
            val active = period == key
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) FixedInk.ChipSurface else Bg.Surface)
                    .border(1.dp, if (active) FixedInk.ChipSurface else Bg.Outline, RoundedCornerShape(50))
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (active) FixedInk.OnChip else Text.Secondary,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

private fun pctOrDash(v: Double): String =
    if (v > 0) String.format(Locale.US, "%.2f%%", v) else "—"

private fun numOrDash(v: Double): String =
    if (v > 0) String.format(Locale.US, "%.2f", v) else "—"

private fun priceOrDash(v: Double): String = if (v > 0) priceText(v) else "—"

private fun capText(yi: Double): String = when {
    yi <= 0 -> "—"
    yi >= 10000 -> String.format(Locale.US, "%.2f万亿", yi / 10000)
    else -> String.format(Locale.US, "%.0f亿", yi)
}
/** 美股代码本身要大写，整串大写会变成 USAAPL，这里把市场与代码分开显示 */
private fun codeDisplay(code: String): String =
    if (code.startsWith("us")) "US · " + code.removePrefix("us") else code.uppercase(Locale.US)