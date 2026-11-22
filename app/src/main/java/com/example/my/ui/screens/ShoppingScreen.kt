package com.example.my.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.AiFeature
import com.example.my.data.AiPresets
import com.example.my.data.shopping.ShopOffer
import com.example.my.data.shopping.ShopPrice
import com.example.my.data.shopping.ShopResult
import com.example.my.data.shopping.ShopTrend
import com.example.my.data.shopping.ShopWatchStore
import com.example.my.data.shopping.TrendMode
import com.example.my.data.shopping.shopMoney
import com.example.my.state.AppState
import com.example.my.ui.components.BackActionRow
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow
import com.example.my.ui.icons.Lucide
import kotlinx.coroutines.launch

private val HOT_KEYWORDS = listOf(
    "iPhone 16", "华为 Mate 70", "小米 15", "AirPods Pro",
    "戴森吹风机", "飞天茅台", "RTX 4060", "空气炸锅",
    "Switch 2", "防晒霜"
)

/** 电商主色：参考图暖橙；价格与主 CTA 用偏红的橙 */
private val ShopOrange = Color(0xFFF26522)
private val ShopOrangeDeep = Color(0xFFD94E16)
private val ShopOrangeLight = Color(0xFFFFEDE2)
private val ShopRed = Color(0xFFE8362B)
private val ShopRedDark = Color(0xFFC9271F)

/** 本次最低价与上次记录最低价，用于比价对比提示 */
private data class PriceDelta(val current: Double, val previous: Double)

/** 分类宫格：标签 → 点击带入搜索的关键词 */
private val CATEGORIES = listOf(
    "女装" to "连衣裙",
    "数码" to "蓝牙耳机",
    "美妆" to "防晒霜",
    "家居" to "空气炸锅"
)

/** 占位图位渐变：按标题哈希取一组暖色，避免无商品图时一片空白 */
private val PLACEHOLDER_PAIRS = listOf(
    listOf(Color(0xFFFFE9DC), Color(0xFFFFD2BC)),
    listOf(Color(0xFFE9F1FF), Color(0xFFD5E5FF)),
    listOf(Color(0xFFEAF7EC), Color(0xFFD5EEE0)),
    listOf(Color(0xFFF4EDFF), Color(0xFFE7DAFF)),
    listOf(Color(0xFFFFF3E0), Color(0xFFFFE7BF)),
    listOf(Color(0xFFFDECEC), Color(0xFFFBD7D7))
)

private fun placeholderFor(title: String): List<Color> {
    val idx = (title.hashCode().rem(PLACEHOLDER_PAIRS.size) + PLACEHOLDER_PAIRS.size) % PLACEHOLDER_PAIRS.size
    return PLACEHOLDER_PAIRS[idx]
}

/**
 * 购物专区：橙色电商首页风格。
 *
 * 数据边界写在页面上：没有电商官方历史价接口，走势是 AI 结合公开搜索材料归纳的
 * 参考曲线；AI 不可用时改为展示真实抓到的各平台报价对比图，不凭空造历史价。
 * 报价条目没有商品图/销量字段，图位用渐变占位 + 首字，不编造销量与原价。
 * 横幅、分类、券条为首页装饰位；分类点击会带入对应关键词发起真实比价。
 */
@Composable
fun ShoppingScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ShopResult?>(null) }
    // 收藏态刷新计数：点收藏后触发重组，重算心形填充
    var favTick by remember { mutableStateOf(0) }
    // 本次最低价 vs 上次记录最低价；无历史价时为 null
    var priceCompare by remember { mutableStateOf<PriceDelta?>(null) }
    val watches = remember(favTick) { ShopWatchStore.favorites(ctx.applicationContext) }

    fun runSearch(raw: String) {
        val q = raw.trim()
        if (q.isBlank()) {
            state.toast(tr("先输入想查的商品", "Type a product first"))
            return
        }
        keyboard?.hide()
        query = q
        loading = true
        error = null
        result = null
        priceCompare = null
        scope.launch {
            val endpoint = state.aiFeatureEndpoint(AiFeature.SHOPPING)
                .takeIf { it.ready } ?: AiPresets.builtin
            val r = runCatching { ShopPrice.search(q, endpoint, ctx.applicationContext) }.getOrNull()
            loading = false
            result = r
            if (r == null) {
                error = tr(
                    "没搜到相关报价，换个更具体的商品名试试（带上品牌或型号）",
                    "No prices found. Try a more specific product name."
                )
            } else {
                val lowest = r.offers.mapNotNull { it.price }.minOrNull()
                val previous = ShopWatchStore.recordLowest(ctx.applicationContext, q, lowest)
                priceCompare = if (lowest != null && previous != null) PriceDelta(lowest, previous) else null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg.Background)
    ) {
        BackActionRow(onBack = { state.closeScreen() })

        val favFavorite = remember(query, favTick) {
            query.isNotBlank() && ShopWatchStore.isFavorite(ctx.applicationContext, query)
        }
        SearchHeader(
            query = query,
            loading = loading,
            isFavorite = favFavorite,
            onQueryChange = { query = it },
            onSearch = { runSearch(query) },
            onToggleFavorite = {
                if (query.isBlank()) {
                    state.toast(tr("先输入想收藏的商品", "Type a product to save"))
                    return@SearchHeader
                }
                val now = ShopWatchStore.toggleFavorite(ctx.applicationContext, query)
                favTick++
                state.toast(if (now) tr("已加入收藏比价", "Saved to watchlist") else tr("已取消收藏", "Removed"))
            },
            onMessage = { state.toast(tr("消息为演示占位", "Messages are a placeholder")) }
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(2) }) { BannerCard() }
            item(span = { GridItemSpan(2) }) { CategoryGrid(onPick = { runSearch(it) }) }
            item(span = { GridItemSpan(2) }) { CouponStrip(onTap = { state.toast(tr("优惠券为演示占位", "Coupons are a placeholder")) }) }

            if (loading) {
                item(span = { GridItemSpan(2) }) { LoadingCard() }
            }

            error?.let { message ->
                item(span = { GridItemSpan(2) }) { ErrorCard(message) }
            }

            result?.let { r ->
                priceCompare?.let { delta ->
                    item(span = { GridItemSpan(2) }) { CompareBanner(delta) }
                }
                item(span = { GridItemSpan(2) }) { TrendCard(r) }
                item(span = { GridItemSpan(2) }) {
                    Text(
                        tr("各平台公开报价", "Public offers"),
                        color = Text.Primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (r.offers.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        EmptyCard(tr("这次只搜到页面，没有抽到明确价格", "No explicit price found"))
                    }
                } else {
                    items(r.offers, key = { it.url + it.title + it.price }) { offer ->
                        ShopProductCard(offer, state)
                    }
                }
                item(span = { GridItemSpan(2) }) { Disclaimer(r.note) }
            }

            if (result == null && !loading) {
                if (watches.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            tr("我的比价", "Watchlist"),
                            color = Text.Primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(watches, key = { it }) { word ->
                        PlaceholderProductCard(title = word, onClick = { runSearch(word) })
                    }
                }
                item(span = { GridItemSpan(2) }) {
                    Text(
                        tr("为你推荐", "Recommended"),
                        color = Text.Primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(HOT_KEYWORDS.take(4), key = { it }) { word ->
                    PlaceholderProductCard(title = word, onClick = { runSearch(word) })
                }
                item(span = { GridItemSpan(2) }) { HotKeywords(onPick = { runSearch(it) }) }
                item(span = { GridItemSpan(2) }) { IntroCard() }
                item(span = { GridItemSpan(2) }) {
                    Disclaimer(
                        tr(
                            "价格为公开网页检索结果，可能与实际售价存在差异；本页不提供下单与支付。",
                            "Prices come from public web results and may differ from the actual listing."
                        )
                    )
                }
            }
        }
    }
}

/** 顶部橙色头区：搜索胶囊 + 消息圆钮 */
@Composable
private fun SearchHeader(
    query: String,
    loading: Boolean,
    isFavorite: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMessage: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(ShopOrange, ShopOrangeLight))
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = Text.Muted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = { if (it.length <= 60) onQueryChange(it) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = Text.Primary, fontSize = 15.sp),
                cursorBrush = SolidColor(ShopOrange),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { inner ->
                    Box {
                        if (query.isBlank()) {
                            Text(
                                tr("搜索好物、品牌、优惠", "Search goods, brands, deals"),
                                color = Text.Muted,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(listOf(ShopOrange, ShopOrangeDeep)))
                    .clickable(enabled = !loading, onClick = onSearch)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (loading) tr("查询中", "Searching") else tr("搜索", "Search"),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(50))
                .background(if (isFavorite) Color.White else Color.White.copy(alpha = 0.25f))
                .clickable(onClick = onToggleFavorite),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Lucide.Heart,
                contentDescription = tr("收藏比价", "Watchlist"),
                tint = if (isFavorite) ShopOrangeDeep else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.25f))
                .clickable(onClick = onMessage),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Lucide.MessageSquare,
                contentDescription = tr("消息", "Messages"),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 今日爆款横幅：标题 + 双 CTA + 右侧占位商品 + 轮播指示点 */
@Composable
private fun BannerCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Brush.horizontalGradient(listOf(Color(0xFFFFF1E6), Color(0xFFFFE3D1))))
            .cardShadow()
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    tr("今日爆款", "Today's picks"),
                    color = Color(0xFF7A2E0C),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("限时直降", "Limited-time deals"),
                        color = Color(0xFF7A2E0C),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ShopOrange.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            tr("领券再买", "Coupon first"),
                            color = ShopOrangeDeep,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Brush.linearGradient(listOf(ShopOrange, ShopOrangeDeep)))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(tr("领券再买", "Coupon first"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, ShopOrange, RoundedCornerShape(50))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(tr("立即抢购", "Grab now"), color = ShopOrangeDeep, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            // 右侧占位商品组合：渐变圆角块模拟鞋 / 包 / 耳机
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.verticalGradient(PLACEHOLDER_PAIRS[1]))
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.verticalGradient(PLACEHOLDER_PAIRS[0]))
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.verticalGradient(PLACEHOLDER_PAIRS[3]))
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.Center) {
            repeat(2) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (i == 0) 16.dp else 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i == 0) ShopOrange else ShopOrange.copy(alpha = 0.3f))
                )
            }
        }
    }
}

/** 四分类宫格：圆角渐变底 + 标签，点击带入关键词搜索 */
@Composable
private fun CategoryGrid(onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .cardShadow()
            .padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CATEGORIES.forEach { (label, keyword) ->
                val palette = placeholderFor(label)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.md))
                        .clickable { onPick(keyword) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.verticalGradient(palette)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label.firstOrNull()?.toString() ?: "·",
                            color = palette[1].copy(alpha = 0.95f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(label, color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** 比价对比条：本次最低价 vs 上次记录，提示下降 / 上升 / 持平（仅本地历史，非实时推送） */
@Composable
private fun CompareBanner(delta: PriceDelta) {
    val diff = delta.current - delta.previous
    val down = diff < -0.005
    val up = diff > 0.005
    val tone = when {
        down -> Brand.Teal
        up -> ShopRed
        else -> Text.Muted
    }
    val arrow = when { down -> "↓"; up -> "↑"; else -> "→" }
    val label = when {
        down -> tr("较上次下降", "Lower than last time")
        up -> tr("较上次上升", "Higher than last time")
        else -> tr("与上次持平", "Same as last time")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(ShopOrangeLight)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(arrow, color = tone, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = tone,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${shopMoney(delta.previous)} → ${shopMoney(delta.current)}",
                color = Text.Secondary,
                fontSize = 11.sp
            )
        }
    }
}

/** 优惠券横条：两张演示券卡 */
@Composable
private fun CouponStrip(onTap: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CouponCard(tr("满200减30", "¥30 off ¥200"), onTap, Modifier.weight(1f))
        CouponCard(tr("新人专享券", "New user coupon"), onTap, Modifier.weight(1f))
    }
}

@Composable
private fun CouponCard(text: String, onTap: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(ShopOrangeLight)
            .clickable(onClick = onTap)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(50))
                .background(ShopOrange),
            contentAlignment = Alignment.Center
        ) {
            Icon(Lucide.Zap, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = ShopOrangeDeep,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HotKeywords(onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .cardShadow()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.TrendingDown,
                contentDescription = null,
                tint = ShopRed,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                tr("大家都在搜", "Popular searches"),
                color = Text.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HOT_KEYWORDS.forEach { word ->
                Text(
                    text = word,
                    color = Text.Secondary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Bg.SurfaceVariant)
                        .clickable { onPick(word) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun IntroCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .padding(16.dp)
    ) {
        Text(
            tr("怎么用", "How it works"),
            color = Text.Primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr(
                "输入任意商品名（手机、家电、数码、酒水都行）。有包装条码的食品/日用品" +
                    "优先用 Open Prices 开源数据集的真实成交价画历史走势；" +
                    "其余商品汇总全网公开报价，再给出参考走势。",
                "Search any product. Packaged goods use real recorded prices from Open Prices; " +
                    "other products use public offers and a reference trend."
            ),
            color = Text.Secondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = Text.Muted,
                modifier = Modifier.size(14.dp).padding(top = 2.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                tr(
                    "没有接入电商官方历史价接口，走势由公开资料归纳，仅供比价参考。",
                    "No official price-history API is connected; the trend is an estimate for reference."
                ),
                color = Text.Muted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun LoadingCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = ShopOrange
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                tr("正在全网找报价…", "Searching public prices…"),
                color = Text.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tr("首次查询需要几秒钟", "The first search may take a few seconds"),
                color = Text.Muted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .border(1.dp, ShopRed.copy(alpha = 0.25f), RoundedCornerShape(Radius.lg))
            .padding(16.dp)
    ) {
        Text(tr("这次没查到", "Nothing found"), color = ShopRed, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = Text.Secondary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun EmptyCard(text: String) {
    Text(
        text = text,
        color = Text.Muted,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Bg.Surface)
            .padding(16.dp)
    )
}

private fun trendBadge(mode: TrendMode): String = when (mode) {
    TrendMode.OPEN_PRICES -> tr("开源真实成交价", "Open data")
    TrendMode.AI_TIMELINE -> tr("AI 参考走势", "AI estimate")
    TrendMode.PLATFORM -> tr("实时报价对比", "Live offers")
}

@Composable
private fun TrendCard(result: ShopResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .cardShadow()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    result.product,
                    color = Text.Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                val theme = result.trend
                Text(
                    if (theme?.mode == TrendMode.AI_TIMELINE) {
                        tr("近 30 天参考走势", "30-day reference trend")
                    } else if (theme?.mode == TrendMode.OPEN_PRICES) {
                        tr("真实成交价走势", "Recorded price history")
                    } else {
                        tr("各平台当前报价", "Current platform prices")
                    },
                    color = Text.Muted,
                    fontSize = 11.sp
                )
            }
            result.trend?.let { trend ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        trendBadge(trend.mode),
                        color = ShopRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ShopRed.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    if (trend.points.isNotEmpty()) {
                        Text(
                            tr("${trend.points.size} 条样本", "${trend.points.size} samples"),
                            color = Text.Muted,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Bg.SurfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        val trend = result.trend
        if (trend == null) {
            Spacer(Modifier.height(14.dp))
            Text(
                tr("暂未凑够可绘制的价格样本，先看下方报价列表。", "Not enough samples for a chart."),
                color = Text.Secondary,
                fontSize = 12.sp
            )
        } else {
            Spacer(Modifier.height(14.dp))
            TrendChart(trend)
            Spacer(Modifier.height(8.dp))
            TrendLabels(trend)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(Bg.SurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column {
                    val values = trend.points.map { it.value }
                    val cur = result.offers.firstOrNull { it.price != null }?.currency ?: "CNY"
                    Text(
                        tr("最低 ${shopMoney(values.min(), cur)}", "Low ${shopMoney(values.min(), cur)}"),
                        color = Brand.Teal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tr("最高 ${shopMoney(values.max(), cur)}", "High ${shopMoney(values.max(), cur)}"),
                        color = Text.Secondary,
                        fontSize = 11.sp
                    )
                }
            }
            if (trend.summary.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(trend.summary, color = Text.Secondary, fontSize = 12.sp, lineHeight = 18.sp)
            }
            if (trend.advice.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    trend.advice,
                    color = Text.Primary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** 纯 Canvas 折线图：渐变填充 + 最低点高亮，不引入图表库 */
@Composable
private fun TrendChart(trend: ShopTrend) {
    val accent = ShopRed
    val grid = Bg.Outline
    val points = trend.points
    val values = points.map { it.value.toFloat() }
    if (values.isEmpty()) return
    val minV = values.min()
    val maxV = values.max()
    val lowestIndex = values.indexOf(minV)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
    ) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 24.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val rawSpan = maxV - minV
        val span = if (rawSpan < 0.01f) (maxV * 0.1f).coerceAtLeast(1f) else rawSpan
        val pad = span * 0.18f
        val lo = minV - pad
        val hi = maxV + pad

        fun xOf(index: Int): Float =
            if (values.size == 1) (left + right) / 2f
            else left + (right - left) * index / (values.size - 1)

        fun yOf(value: Float): Float =
            bottom - (value - lo) / (hi - lo) * (bottom - top)

        repeat(3) { row ->
            val y = top + (bottom - top) * row / 2f
            drawLine(color = grid, start = Offset(left, y), end = Offset(right, y), strokeWidth = 1.dp.toPx())
        }

        val line = Path()
        values.forEachIndexed { index, value ->
            val x = xOf(index)
            val y = yOf(value)
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }

        val fill = Path().apply {
            addPath(line)
            lineTo(xOf(values.size - 1), bottom)
            lineTo(xOf(0), bottom)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                colors = listOf(accent.copy(alpha = 0.26f), Color.Transparent),
                startY = top,
                endY = bottom
            )
        )
        drawPath(
            path = line,
            color = accent,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        values.forEachIndexed { index, value ->
            val center = Offset(xOf(index), yOf(value))
            if (index == lowestIndex) {
                drawCircle(accent.copy(alpha = 0.18f), radius = 8.dp.toPx(), center = center)
                drawCircle(Color.White, radius = 4.5.dp.toPx(), center = center)
                drawCircle(accent, radius = 3.dp.toPx(), center = center)
            } else {
                drawCircle(Color.White, radius = 3.5.dp.toPx(), center = center)
                drawCircle(accent, radius = 2.dp.toPx(), center = center)
            }
        }
    }
}

/** 横轴标签：点少时铺满，点多时只显示首/中/尾，避免挤成一团 */
@Composable
private fun TrendLabels(trend: ShopTrend) {
    val points = trend.points
    if (points.isEmpty()) return
    if (points.size <= 5) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEach { point ->
                Text(
                    point.label,
                    color = Text.Muted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(points.first().label, color = Text.Muted, fontSize = 9.sp)
            Text(points[points.size / 2].label, color = Text.Muted, fontSize = 9.sp)
            Text(points.last().label, color = Text.Muted, fontSize = 9.sp)
        }
    }
}

/** 双列商品卡：渐变占位图位 + 标题 + 平台 + 红色价格 + 加入购物车入口，点按打开来源 */
@Composable
private fun ShopProductCard(offer: ShopOffer, state: AppState) {
    val palette = placeholderFor(offer.title)
    val initial = offer.title.trim().firstOrNull()?.toString() ?: "商"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Bg.Surface)
            .cardShadow()
            .clickable { state.openUrl(offer.url) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .background(Brush.verticalGradient(palette)),
            contentAlignment = Alignment.Center
        ) {
            Text(initial, color = palette[1].copy(alpha = 0.95f), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                offer.title,
                color = Text.Primary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                offer.site,
                color = Text.Muted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Bg.SurfaceVariant)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (offer.price != null) {
                    Text(
                        offer.priceText,
                        color = ShopRed,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        tr("看报价", "View"),
                        color = ShopRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ShopOrangeLight)
                        .clickable { state.openUrl(offer.url) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        tr("加入购物车", "Cart"),
                        color = ShopOrangeDeep,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/** 未搜索时的占位推荐卡：渐变图位 + 关键词，点击发起比价 */
@Composable
private fun PlaceholderProductCard(title: String, onClick: () -> Unit) {
    val palette = placeholderFor(title)
    val initial = title.trim().firstOrNull()?.toString() ?: "荐"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Bg.Surface)
            .cardShadow()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .background(Brush.verticalGradient(palette)),
            contentAlignment = Alignment.Center
        ) {
            Text(initial, color = palette[1].copy(alpha = 0.95f), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                title,
                color = Text.Primary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("比价看看", "Compare"),
                    color = ShopRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Outlined.OpenInNew,
                    contentDescription = tr("打开来源", "Open source"),
                    tint = Text.Muted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun Disclaimer(text: String) {
    Text(
        text = text,
        color = Text.Muted,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}
