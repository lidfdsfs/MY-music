package com.example.my.ui.interaction

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ============================================================
// 滚动交互组件库
// 1. OverscrollZoomState      视差头图：下拉放大、松手回弹
// 2. StickyCategoryTabs       吸顶分类标签：指示条跟随、点击平滑回滚
// 3. Modifier.scrollReveal    滚动渐显：上移淡入 + 图片模糊变清晰，错开 80ms
// 4. SunRefreshIndicator      下拉刷新：太阳从地平线升起
// 5. GrowingBarChart          数据生长：进屏柱子从零长高、数字同步滚动
// ============================================================

// ============================================================
// 1. 下拉放大（松手回弹）—— 配合视差头图使用
//    onPostScroll 里捕获顶部继续下拉的剩余增量转为放大；
//    回滚时先在 onPreScroll 吃掉放大，再让列表滚动，避免跳动。
// ============================================================

class OverscrollZoomState internal constructor(private val scope: CoroutineScope) {
    var zoom by mutableFloatStateOf(1f)
        private set

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // 列表想往上滚（回顶部方向）且处于放大态：先缩小，把位移吃掉
            if (available.y < 0f && zoom > 1f) {
                val before = zoom
                zoom = (zoom + available.y / 320f).coerceAtLeast(1f)
                val used = (before - zoom) * 320f
                return Offset(0f, used)
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            // 已到顶还继续下拉：剩余增量放大头图
            if (available.y > 0f) {
                zoom = (zoom + available.y / 320f).coerceAtMost(1.45f)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            release()
            return Velocity.Zero
        }
    }

    /** 松手回弹：弹簧回到 1 倍 */
    suspend fun release() {
        val start = zoom
        if (start <= 1f) return
        androidx.compose.animation.core.animate(
            initialValue = start,
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f)
        ) { v, _ -> zoom = v }
    }

    fun releaseAsync() {
        scope.launch { release() }
    }
}

/** [isScrollInProgress] 传列表的 isScrollInProgress，松手即回弹 */
@Composable
fun rememberOverscrollZoom(isScrollInProgress: Boolean): OverscrollZoomState {
    val scope = rememberCoroutineScope()
    val state = remember { OverscrollZoomState(scope) }
    LaunchedEffect(isScrollInProgress) {
        if (!isScrollInProgress) state.release()
    }
    return state
}

// ============================================================
// 2. 吸顶分类标签 —— 选中项指示条生长跟随，点击后 LazyRow 平滑回滚居中
// ============================================================

@Composable
fun StickyCategoryTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Brand.Terracotta
) {
    val rowState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LazyRow(
        state = rowState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(options, key = { _, it -> it }) { index, label ->
            val selected = index == selectedIndex
            val color by animateColorAsState(
                targetValue = if (selected) Text.Primary else Text.Muted,
                animationSpec = tween(180),
                label = "tabColor$index"
            )
            val indicatorWidth by animateDpAsState(
                targetValue = if (selected) 20.dp else 0.dp,
                animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
                label = "tabIndicator$index"
            )
            Column(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onSelect(index)
                        // 点标签平滑回滚：把点击的标签滚动到可视区
                        scope.launch { rowState.animateScrollToItem(index) }
                    }
                    .padding(horizontal = 2.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    color = color,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .height(3.dp)
                        .width(indicatorWidth)
                        .clip(RoundedCornerShape(6.dp))
                        .background(activeColor)
                )
            }
        }
    }
}

// ============================================================
// 3. 滚动渐显 —— 进屏才播：上移淡入，Android 12+ 附加模糊变清晰
//    LazyColumn 会预组合视口外 item，所以用 onGloballyPositioned
//    判断真实可见性，只播一次。
// ============================================================

fun Modifier.scrollReveal(staggerMillis: Int = 0): Modifier = composed {
    val density = LocalDensity.current
    val startPx = remember(density) { with(density) { 16.dp.toPx() } }
    var visible by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(startPx) }
    val blurRadius = remember { Animatable(8f) }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        if (staggerMillis > 0) delay(staggerMillis.toLong())
        launch { alpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        launch { offsetY.animateTo(0f, tween(320, easing = FastOutSlowInEasing)) }
        if (Build.VERSION.SDK_INT >= 31) {
            launch { blurRadius.animateTo(0f, tween(420, easing = FastOutSlowInEasing)) }
        }
        settled = true
    }

    this
        .onGloballyPositioned { coords ->
            if (visible) return@onGloballyPositioned
            val rootH = coords.findRootCoordinates().size.height
            val b = coords.boundsInRoot()
            if (b.bottom > 0f && b.top < rootH) visible = true
        }
        .then(
            if (settled) Modifier
            else Modifier.graphicsLayer {
                this.alpha = alpha.value
                translationY = offsetY.value
            }
        )
        .then(
            if (!settled && Build.VERSION.SDK_INT >= 31) Modifier.blur(blurRadius.value.dp)
            else Modifier
        )
}

// ============================================================
// 4. 下拉刷新：太阳从地平线升起，刷新中光芒旋转
//    PullToRefreshState.distanceFraction：0 → 1 为下拉进度
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SunRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val fraction = state.distanceFraction
    // 仅在刷新时驱动旋转：空闲时动画循环不跑，避免常驻无限动画每帧拖帧/耗电
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            rotation.snapTo(rotation.value % 360f)
            while (true) {
                rotation.animateTo(rotation.value + 360f, tween(1400, easing = LinearEasing))
            }
        }
    }
    val rise = if (isRefreshing) 1f else fraction.coerceIn(0f, 1f)
    val alpha = if (isRefreshing) 1f else ((fraction - 0.12f) / 0.5f).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .graphicsLayer { this.alpha = alpha }
    ) {
        val w = size.width
        val h = size.height
        val horizonY = h * 0.94f
        val sunR = 12.dp.toPx()
        val cx = w / 2f
        // 太阳中心：地平线以下 → 升到地平线上方
        val cy = horizonY + sunR - rise * (horizonY - sunR * 1.7f)

        // 地平线
        drawLine(
            color = Color(0xFFD8CFC4),
            start = Offset(w * 0.30f, horizonY),
            end = Offset(w * 0.70f, horizonY),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        // 光芒（刷新中旋转，下拉中随进度展开 90°）
        val rayCount = 8
        rotate(if (isRefreshing) rotation.value else rise * 90f, pivot = Offset(cx, cy)) {
            for (i in 0 until rayCount) {
                val a = i * (2f * PI.toFloat() / rayCount)
                val inner = sunR + 3.dp.toPx()
                val outer = inner + 5.dp.toPx()
                drawLine(
                    color = Brand.Gold,
                    start = Offset(cx + cos(a) * inner, cy + sin(a) * inner),
                    end = Offset(cx + cos(a) * outer, cy + sin(a) * outer),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        // 日轮
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Brand.TerracottaLight, Brand.Terracotta),
                center = Offset(cx, cy),
                radius = sunR
            ),
            radius = sunR,
            center = Offset(cx, cy)
        )
    }
}

// ============================================================
// 5. 数据随滚动生长 —— 进屏才触发：柱子从零长到实际高度，
//    柱顶数字同步滚动；只播一次。
// ============================================================

@Composable
fun GrowingBarChart(
    values: List<Int>,
    modifier: Modifier = Modifier,
    activeColor: Color = Brand.Terracotta,
    emptyColor: Color = Bg.SurfaceVariant,
    showValue: Boolean = true
) {
    var started by remember { mutableStateOf(false) }
    val max = remember(values) { (values.maxOrNull() ?: 0).coerceAtLeast(1) }
    val peakIdx = remember(values) { values.indexOf(max) }
    Row(
        modifier = modifier.onGloballyPositioned { coords ->
            if (started) return@onGloballyPositioned
            val rootH = coords.findRootCoordinates().size.height
            val b = coords.boundsInRoot()
            if (b.bottom > 0f && b.top < rootH * 0.96f) started = true
        },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        values.forEachIndexed { i, v ->
            val isPeak = v > 0 && i == peakIdx
            val target = if (v == 0) 0.03f else v.toFloat() / max
            val frac by animateFloatAsState(
                targetValue = if (started) target else 0f,
                animationSpec = tween(620, delayMillis = i * 28, easing = FastOutSlowInEasing),
                label = "growBar$i"
            )
            val number by animateIntAsState(
                targetValue = if (started) v else 0,
                animationSpec = tween(620, delayMillis = i * 28),
                label = "growNum$i"
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showValue && v > 0) {
                    Text(
                        "$number",
                        color = if (isPeak) Brand.Gold else Text.Muted,
                        fontSize = 9.sp,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(frac)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
                        .background(
                            when {
                                v == 0 -> Brush.linearGradient(listOf(emptyColor, emptyColor))
                                isPeak -> Brush.verticalGradient(listOf(Brand.Gold, activeColor))
                                else -> Brush.verticalGradient(
                                    listOf(activeColor.copy(alpha = 0.72f), activeColor)
                                )
                            }
                        )
                )
            }
        }
    }
}
