package com.example.my.ui.trade

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.stock.MarketQuote
import com.example.my.data.trade.QuoteLite
import com.example.my.data.trade.TradeAsset
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Allocation
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Func
import com.example.my.ui.theme.Layer
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.Type
import com.example.my.ui.theme.TypeScale
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 涨红跌绿（A 股口径），深色底上也不至于糊 */
val TradeUp = Color(0xFFD93B33)
val TradeDown = Color(0xFF15935F)

/** 行情 → 撮合用的精简报价 */
fun MarketQuote.toLite(): QuoteLite = QuoteLite(
    code = code,
    name = name,
    price = price,
    prevClose = if (prevCloseEstimate() > 0) prevCloseEstimate() else price,
    changePct = changePct,
    asset = TradeAsset.ofCode(code)
)

/**
 * 腾讯快照只给了现价与涨跌幅，昨收据现价反推：
 * prev = price / (1 + pct/100)。比直接用现价当昨收更接近真实，当日盈亏才不会是 0。
 */
private fun MarketQuote.prevCloseEstimate(): Double {
    if (price <= 0.0) return 0.0
    val ratio = changePct / 100.0
    if (ratio <= -0.98) return price
    return price / (1.0 + ratio)
}

fun priceText(v: Double): String =
    if (v <= 0.0) "—" else String.format(Locale.US, "%.2f", v)

fun signedPrice(v: Double): String =
    (if (v > 0) "+" else if (v < 0) "-" else "") + String.format(Locale.US, "%.2f", abs(v))

fun pctText(v: Double): String =
    (if (v > 0) "+" else if (v < 0) "-" else "") + String.format(Locale.US, "%.2f%%", abs(v))

/** 金额：一万以下两位小数，十万以上用「万」，再大用「亿」 */
fun moneyText(v: Double): String = when {
    abs(v) >= 1e8 -> String.format(Locale.US, "%.2f 亿", v / 1e8)
    abs(v) >= 1e4 -> String.format(Locale.US, "%.2f 万", v / 1e4)
    else -> String.format(Locale.US, "%.2f", v)
}

fun volumeText(v: Double): String = when {
    v >= 1e8 -> String.format(Locale.US, "%.2f 亿", v / 1e8)
    v >= 1e4 -> String.format(Locale.US, "%.1f 万", v / 1e4)
    else -> v.roundToInt().toString()
}

/** 统一卡片：标题行 + 内容 */
@Composable
fun TradeCard(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            // 交易页是数字密集页：卡片一律不透明底，自定义背景的照片不能透上来叠字
            .background(Layer.Opaque)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Text.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, color = Text.Muted, fontSize = 10.sp)
                }
            }
            trailing?.invoke()
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

/** 小胶囊选项 */
@Composable
fun TradeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Brand.Terracotta.copy(alpha = 0.16f) else Bg.SurfaceVariant.copy(alpha = 1f))
            .border(
                1.dp,
                if (selected) Brand.Terracotta else Color.Transparent,
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (selected) Brand.Terracotta else Text.Secondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 空状态：说清楚为什么空、下一步去哪 */
@Composable
fun TradeEmpty(text: String, hint: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = Text.Secondary, fontSize = 13.sp)
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(hint, color = Text.Muted, fontSize = 11.sp)
        }
    }
}

/** 涨跌配色 */
fun toneOf(v: Double): Color = if (v >= 0) TradeUp else TradeDown

/** 列表里的迷你分时线：没有数据就画一条淡横线，不假装有走势 */
@Composable
fun Sparkline(
    values: List<Double>,
    baseline: Double,
    modifier: Modifier = Modifier
) {
    val tone = if (values.lastOrNull() ?: baseline >= baseline) TradeUp else TradeDown
    Canvas(modifier) {
        if (values.size < 2) {
            drawLine(
                color = Color.Gray.copy(alpha = 0.25f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.dp.toPx()
            )
            return@Canvas
        }
        val hi = max(values.maxOrNull() ?: baseline, baseline)
        val lo = minOf(values.minOrNull() ?: baseline, baseline)
        val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
        fun y(v: Double) = (size.height - ((v - lo) / span * size.height * 0.92 + size.height * 0.04)).toFloat()
        // 昨收基准虚线
        if (baseline > 0) {
            drawLine(
                color = Color.Gray.copy(alpha = 0.28f),
                start = Offset(0f, y(baseline)),
                end = Offset(size.width, y(baseline)),
                strokeWidth = 0.8.dp.toPx()
            )
        }
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1).toFloat()
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        drawPath(path, tone, style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** 渐变亮端：不透明提亮，避免半透明叠出脏色 */
private fun Color.lift(): Color = Color(
    red = red + (1f - red) * 0.22f,
    green = green + (1f - green) * 0.22f,
    blue = blue + (1f - blue) * 0.22f,
    alpha = 1f,
    colorSpace = colorSpace
)

/**
 * 资产结构：正圆分段环 + 可点选图例。
 *
 * 旧版把 Canvas 拉成「满宽 × 160dp」后，直接拿 width / height 当椭圆两轴画弧，
 * 于是圆被压成扁椭圆；这里固定正方形画布，宽高恒等，任何屏宽下都是正圆。
 * 点环上任意一段、或点图例行，中心就切到该类别的金额与占比，再点一次回到总资产。
 */
@Composable
fun AssetAllocation(
    stock: Double,
    fund: Double,
    cash: Double,
    modifier: Modifier = Modifier
) {
    val total = (stock + fund + cash).takeIf { it > 0.0 } ?: 0.0
    val values = listOf(stock, fund, cash)
    val colors = listOf(Allocation.Stock, Allocation.Fund, Allocation.Cash)
    val labels = listOf(tr("股票", "Stocks"), tr("基金", "Funds"), tr("现金", "Cash"))
    var selected by remember { mutableStateOf<Int?>(null) }

    // 分类被卖空后清掉选中，中心不会停在一个 0 值的幽灵分类上
    LaunchedEffect(stock, fund, cash) {
        val cur = selected
        if (cur != null && values[cur] <= 0.0) selected = null
    }

    // 首次进入从 0 展开；之后数值变化 / 切换选中都不重播动画
    var revealTarget by remember { mutableStateOf(0f) }
    val reveal by animateFloatAsState(
        targetValue = revealTarget,
        animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing),
        label = "asset-reveal"
    )
    val emphasis by animateFloatAsState(
        targetValue = if (selected != null) 1.16f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "asset-emphasis"
    )
    LaunchedEffect(Unit) { revealTarget = 1f }

    val strokeDp = 22.dp
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(184.dp)
                .pointerInput(values, total) {
                    detectTapGestures { offset ->
                        if (total <= 0.0) return@detectTapGestures
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val radius = hypot(
                            (offset.x - cx).toDouble(), (offset.y - cy).toDouble()
                        ).toFloat()
                        val outer = minOf(size.width, size.height) / 2f - 2.dp.toPx()
                        val innerEdge = outer - strokeDp.toPx() * 1.45f
                        if (radius < innerEdge || radius > outer) {
                            selected = null
                            return@detectTapGestures
                        }
                        // 12 点为 0°、顺时针递增，与 drawArc(-90f, ...) 同口径
                        val deg = ((Math.toDegrees(
                            atan2((offset.y - cy).toDouble(), (offset.x - cx).toDouble())
                        ).toFloat() + 90f) % 360f + 360f) % 360f
                        var acc = 0f
                        var hit: Int? = null
                        values.forEachIndexed { i, v ->
                            val sweep = (v / total * 360.0).toFloat()
                            if (deg >= acc && deg < acc + sweep) hit = i
                            acc += sweep
                        }
                        val h = hit
                        selected = if (h != null && values[h] > 0.0 && h != selected) h else null
                    }
                }
        ) {
            val side = min(size.width, size.height)
            val stroke = strokeDp.toPx()
            val inset = stroke / 2f + 3.dp.toPx()
            val arcSide = side - inset * 2f
            val arcTopLeft = Offset(
                (size.width - side) / 2f + inset,
                (size.height - side) / 2f + inset
            )
            // 宽高恒等：这才是正圆，任何容器比例都不会把它压扁
            val arcSize = Size(arcSide, arcSide)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = arcSide / 2f

            drawCircle(
                color = Allocation.Inner,
                radius = (radius - stroke / 2f - 6.dp.toPx()).coerceAtLeast(0f),
                center = center
            )
            drawArc(
                color = Allocation.Track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            if (total <= 0.0) return@Canvas

            val capDeg = Math.toDegrees(stroke / 2.0 / radius).toFloat()
            val visible = values.count { it > 0.0 }
            var start = -90f
            values.forEachIndexed { i, v ->
                val sweep = (v / total * 360.0).toFloat()
                if (v > 0.0) {
                    // 多段留缝；圆角端会自行外扩 capDeg，间隙按此反算才不会被吃掉
                    val gap = if (visible > 1) min(3.2f + capDeg * 2f, sweep * 0.3f) else 0f
                    val active = selected == i
                    val brush = Brush.linearGradient(
                        colors = listOf(colors[i].lift(), colors[i]),
                        start = arcTopLeft,
                        end = Offset(arcTopLeft.x + arcSide, arcTopLeft.y + arcSide)
                    )
                    drawArc(
                        brush = brush,
                        startAngle = start + gap / 2f,
                        sweepAngle = (sweep * reveal - gap).coerceAtLeast(0.6f),
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        alpha = if (selected != null && !active) 0.34f else 1f,
                        style = Stroke(
                            width = stroke * if (active) emphasis else 1f,
                            cap = StrokeCap.Round
                        )
                    )
                }
                start += sweep
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            val idx = selected
            when {
                total <= 0.0 -> {
                    Text(tr("暂无资产", "No assets"), color = Text.Secondary, fontSize = TypeScale.body)
                    Text(moneyText(0.0), color = Text.Primary, style = Type.statNumber, maxLines = 1)
                }
                idx == null -> {
                    Text(tr("总资产", "Total"), color = Text.Secondary, fontSize = TypeScale.caption)
                    Text(
                        moneyText(total), color = Text.Primary, style = Type.statNumber,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        tr("点环或明细看占比", "Tap a slice for details"),
                        color = Text.Muted, fontSize = TypeScale.tiny, maxLines = 1
                    )
                }
                else -> {
                    Text(
                        labels[idx], color = colors[idx], fontSize = TypeScale.caption,
                        fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                    Text(
                        moneyText(values[idx]), color = Text.Primary, style = Type.statNumber,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        tr("占比 ", "Share ") + String.format(Locale.US, "%.1f%%", values[idx] / total * 100),
                        color = Text.Muted, fontSize = TypeScale.tiny, maxLines = 1
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        labels.forEachIndexed { i, label ->
            val active = selected == i
            val fraction = if (total > 0.0) (values[i] / total).toFloat().coerceIn(0f, 1f) else 0f
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) Bg.SurfaceVariant.copy(alpha = 1f) else Layer.Opaque)
                    .clickable { selected = if (active || values[i] <= 0.0) null else i }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(colors[i]))
                Spacer(Modifier.width(9.dp))
                Text(
                    label, color = if (active) Text.Primary else Text.Secondary,
                    fontSize = TypeScale.body,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.width(42.dp), maxLines = 1
                )
                Text(
                    moneyText(values[i]), color = Text.Primary, fontSize = TypeScale.body,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    String.format(Locale.US, "%.1f%%", fraction * 100),
                    color = Text.Muted, fontSize = TypeScale.caption, maxLines = 1
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Bg.Outline)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors[i])
                )
            }
        }
    }
}

/** 模拟盘提示条：每个交易相关页面都放一条，避免误以为是真券商账户 */
@Composable
fun SimulationNotice() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Func.Warning.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            tr(
                "模拟盘 · 行情来自公开接口，撮合与资金只在本机，不接券商、不产生真实成交",
                "Paper trading · quotes are public data, matching and cash stay on device — no broker, no real fills"
            ),
            color = Text.Secondary,
            fontSize = 10.sp,
            lineHeight = 15.sp
        )
    }
}
