package com.example.my.ui.trade

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.stock.StockApi
import com.example.my.data.stock.StockHit
import com.example.my.data.trade.OrderStatus
import com.example.my.data.trade.OrderType
import com.example.my.data.trade.QuoteLite
import com.example.my.data.trade.TradeAsset
import com.example.my.data.trade.TradeSide
import com.example.my.data.trade.TradeStore
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Func
import com.example.my.ui.theme.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class OrderPane(val zh: String, val en: String) {
    BUY("买入", "Buy"),
    SELL("卖出", "Sell"),
    CANCEL("撤单", "Cancel")
}

/**
 * 交易委托页：买入 / 卖出 / 撤单。
 *
 * 价格与数量都由用户填，限价单到价才成交；市价单按当前真实报价立即撮合。
 * 页面上把「可用资金 / 可卖数量」摆在输入框旁边，避免填完才被拒。
 */
@Composable
fun TradeOrderPage(
    target: QuoteLite?,
    side: TradeSide,
    quotes: Map<String, QuoteLite>,
    onSideChange: (TradeSide) -> Unit,
    onTarget: (QuoteLite) -> Unit,
    onOpenDetail: (String) -> Unit,
    onPickByCode: (String, String) -> Unit,
    onToast: (String) -> Unit
) {
    var pane by remember(side) {
        mutableStateOf(if (side == TradeSide.BUY) OrderPane.BUY else OrderPane.SELL)
    }
    var type by remember { mutableStateOf(OrderType.LIMIT) }
    var priceInput by remember(target?.code) {
        mutableStateOf(target?.let { if (it.price > 0) priceText(it.price) else "" } ?: "")
    }
    var qtyInput by remember(target?.code) { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<StockHit>>(emptyList()) }

    // 目标价格随行情更新：用户没改过价格就跟着最新价走，改过了就不动他填的数
    LaunchedEffect(target?.code) {
        val live = target?.code?.let { quotes[it] } ?: return@LaunchedEffect
        if (priceInput.isBlank() || priceInput == priceText(target.price)) {
            priceInput = priceText(live.price)
        }
        if (target.price <= 0 && live.price > 0) {
            onTarget(live)
        }
    }

    LaunchedEffect(search) {
        val q = search.trim()
        if (q.isEmpty()) {
            hits = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        hits = withContext(Dispatchers.IO) { runCatching { StockApi.search(q) }.getOrDefault(emptyList()) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp
        )
    ) {
        item {
            TradeTopBar(
                title = tr("交易委托", "Order ticket"),
                subtitle = tr("模拟撮合 · 不接券商", "Simulated matching")
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrderPane.entries.forEach { p ->
                    TradeChip(
                        label = tr(p.zh, p.en),
                        selected = pane == p,
                        modifier = Modifier.weight(1f)
                    ) {
                        pane = p
                        when (p) {
                            OrderPane.BUY -> onSideChange(TradeSide.BUY)
                            OrderPane.SELL -> onSideChange(TradeSide.SELL)
                            OrderPane.CANCEL -> Unit
                        }
                    }
                }
            }
        }
        item { TradeGap(12) }

        if (pane == OrderPane.CANCEL) {
            item {
                val pending = TradeStore.orders.filter { it.status == OrderStatus.PENDING }
                TradeCard(
                    title = tr("可撤委托", "Cancellable"),
                    subtitle = tr("挂单不冻结资金，成交那刻才校验", "No cash is frozen until a fill")
                ) {
                    if (pending.isEmpty()) {
                        TradeEmpty(tr("没有挂单中的委托", "No pending orders"))
                    }
                    pending.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${o.side.zh} ${o.name} ${o.qty}股",
                                    color = Text.Primary, fontSize = 12.5.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    tr(
                                        "限价 ${priceText(o.limitPrice)} · ${orderTime(o.createdAt)}",
                                        "Limit ${priceText(o.limitPrice)} · ${orderTime(o.createdAt)}"
                                    ),
                                    color = Text.Muted, fontSize = 10.sp
                                )
                            }
                            Text(
                                tr("撤单", "Cancel"),
                                color = Func.Danger,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Func.Danger.copy(alpha = 0.10f))
                                    .clickable {
                                        if (TradeStore.cancel(o.id)) onToast(tr("已撤单", "Order cancelled"))
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        TradeDivider()
                    }
                }
            }
            return@LazyColumn
        }

        if (target == null) {
            // 还没选标的：先给搜索 + 自选 / 涨幅榜快捷入口
            item {
                TradeInput(value = search, onValueChange = { search = it }, placeholder = tr("搜代码 / 名称", "Search code or name"))
            }
            item { TradeGap(10) }
            item {
                TradeCard(title = tr("选择标的", "Pick a symbol")) {
                    val candidates = if (search.trim().isNotEmpty()) {
                        hits.map { Triple(it.code, it.name, quotes[it.code]) }
                    } else {
                        TradeStore.watchlist.map { Triple(it.code, it.name, quotes[it.code]) }
                    }
                    if (candidates.isEmpty()) {
                        TradeEmpty(
                            if (search.isNotBlank()) tr("没有匹配结果", "No match")
                            else tr("自选是空的", "Watchlist empty"),
                            tr("先搜一只股票或 ETF", "Search for a stock or ETF")
                        )
                    }
                    candidates.forEach { (code, name, q) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPickByCode(code, name) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(name, color = Text.Primary, fontSize = 13.sp)
                                Text(codeDisplay(code), color = Text.Muted, fontSize = 10.sp)
                            }
                            Text(
                                if (q != null && q.price > 0) priceText(q.price) else tr("待报价", "No quote"),
                                color = if (q != null) toneOf(q.changePct) else Text.Muted,
                                fontSize = 12.sp
                            )
                        }
                        TradeDivider()
                    }
                }
            }
            return@LazyColumn
        }

        // 已选标的：报价头 + 下单表单
        val live = quotes[target.code] ?: target
        val asset = TradeAsset.ofCode(target.code)
        val isBuy = pane == OrderPane.BUY
        val maxQty = if (isBuy) {
            TradeStore.maxBuyQty(live.price.takeIf { it > 0 } ?: target.price, asset)
        } else {
            TradeStore.available(target.code)
        }

        item {
            TradeCard(
                title = target.name,
                subtitle = codeDisplay(target.code) + " · " + (if (asset == TradeAsset.FUND) tr("基金", "Fund") else tr("股票", "Stock")),
                trailing = {
                    Text(
                        tr("详情 ›", "Detail ›"),
                        color = Brand.Terracotta,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onOpenDetail(target.code) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        priceText(live.price),
                        color = toneOf(live.changePct),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(pctText(live.changePct), color = toneOf(live.changePct), fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        tr("实时价", "Live"),
                        color = Text.Muted,
                        fontSize = 10.sp
                    )
                }
            }
        }
        item { TradeGap() }
        item {
            TradeCard(title = tr("委托参数", "Order parameters")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrderType.entries.forEach { t ->
                        TradeChip(tr(t.zh, t.en), type == t) { type = t }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("委托价", "Price"), color = Text.Muted, fontSize = 10.sp)
                        Spacer(Modifier.height(4.dp))
                        if (type == OrderType.MARKET) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Bg.SurfaceVariant.copy(alpha = 1f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(tr("按市价成交", "At market"), color = Text.Secondary, fontSize = 13.sp)
                            }
                        } else {
                            TradeInput(
                                value = priceInput,
                                onValueChange = { priceInput = it },
                                placeholder = tr("价格", "Price")
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(tr("数量（股）", "Quantity"), color = Text.Muted, fontSize = 10.sp)
                        Spacer(Modifier.height(4.dp))
                        TradeInput(
                            value = qtyInput,
                            onValueChange = { qtyInput = it },
                            placeholder = if (asset == TradeAsset.STOCK) "100" else "1"
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isBuy) tr("可用资金 ${moneyText(TradeStore.cash)}", "Cash ${moneyText(TradeStore.cash)}")
                    else tr("可卖 ${TradeStore.available(target.code)} 股", "${TradeStore.available(target.code)} available"),
                    color = Text.Muted,
                    fontSize = 10.5.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        0.25 to tr("1/4", "1/4"),
                        0.5 to tr("1/2", "1/2"),
                        1.0 to tr("全仓", "Max")
                    ).forEach { (ratio, label) ->
                        TradeChip(label, false) {
                            var q = (maxQty * ratio).toInt()
                            if (asset == TradeAsset.STOCK) q = (q / 100) * 100
                            if (q <= 0 && maxQty > 0) q = if (asset == TradeAsset.STOCK) 100 else 1
                            qtyInput = q.toString()
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isBuy) TradeUp else TradeDown)
                        .clickable {
                            val price = if (type == OrderType.MARKET) live.price else priceInput.toDoubleOrNull() ?: 0.0
                            val qty = qtyInput.trim().toIntOrNull() ?: 0
                            val result = TradeStore.place(
                                side = if (isBuy) TradeSide.BUY else TradeSide.SELL,
                                type = type,
                                limitPrice = price,
                                qty = qty,
                                quote = live
                            )
                            onToast(result)
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tr(if (isBuy) "确认买入" else "确认卖出", if (isBuy) "Buy" else "Sell"),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    tr(
                        "佣金 0.025%（最低 5 元）；股票卖出另有 0.1% 印花税；基金按 0.01% 粗算。",
                        "Commission 0.025% (min ¥5); 0.1% stamp on stock sells; funds ~0.01%."
                    ),
                    color = Text.Muted,
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp
                )
            }
        }
        item { TradeGap() }
        item {
            // 同一标的的挂单，方便随时撤
            val mine = TradeStore.orders.filter {
                it.code == target.code && it.status == OrderStatus.PENDING
            }
            if (mine.isNotEmpty()) {
                TradeCard(title = tr("该标的挂单", "Pending for this symbol")) {
                    mine.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${o.side.zh} ${o.qty}股 @ ${priceText(o.limitPrice)}",
                                color = Text.Secondary, fontSize = 11.5.sp, modifier = Modifier.weight(1f)
                            )
                            Text(
                                tr("撤单", "Cancel"),
                                color = Func.Danger,
                                fontSize = 11.sp,
                                modifier = Modifier.clickable {
                                    if (TradeStore.cancel(o.id)) onToast(tr("已撤单", "Order cancelled"))
                                }
                            )
                        }
                    }
                }
            }
        }
        item { TradeGap() }
        item { SimulationNotice() }
    }
}

private fun orderTime(at: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(at))
