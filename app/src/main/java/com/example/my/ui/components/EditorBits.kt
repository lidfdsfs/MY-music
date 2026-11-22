package com.example.my.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.ui.interaction.pressBounceClickable
import com.example.my.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 圆角虚线描边 */
fun Modifier.dashedRoundBorder(color: Color, radius: Dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
        )
    )
}

/** 虚线描边的「+ 添加」按钮 */
@Composable
fun DashedAddButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .pressBounceClickable(onClick = onClick, pressedScale = 0.97f)
            .dashedRoundBorder(Brand.Terracotta.copy(alpha = 0.55f), 12.dp)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Brand.Terracotta,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 时段 / 选项胶囊：未选虚线描边，选中浅赤陶底 */
@Composable
fun DashedChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Brand.Terracotta.copy(alpha = 0.14f) else Color.Transparent)
            .pressBounceClickable(onClick = onClick, pressedScale = 0.94f)
            .then(
                if (selected) Modifier
                else Modifier.dashedRoundBorder(Brand.Terracotta.copy(alpha = 0.5f), 50.dp)
            )
            .padding(horizontal = 15.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Brand.Terracotta,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * 入场动画门控：记录本页已播放过的 index。
 *
 * LazyColumn 的 item 滚出视口后会被回收，滚回来会重新组合。
 * 没有门控时入场动画会重播，表现为"向上滚回去时页面闪一下"。
 * 门控由 [ProvideStaggerGate] 在页面顶层提供，整个页面共用一份。
 */
class StaggerGate internal constructor() {
    private val played = HashSet<Int>()

    /** 返回 true 表示此前已播放过，本次应直接落到终态 */
    fun alreadyPlayed(index: Int): Boolean = !played.add(index)
}

val LocalStaggerGate = compositionLocalOf<StaggerGate?> { null }

/** 页面根包裹一次，使该页所有 [staggeredIn] 只播放一次 */
@Composable
fun ProvideStaggerGate(content: @Composable () -> Unit) {
    val gate = remember { StaggerGate() }
    CompositionLocalProvider(LocalStaggerGate provides gate, content = content)
}

/**
 * 内容块依次错开淡入（alpha + 轻微上移），避免整页同时入场。
 *
 * 性能约定：
 * - 仅用于**页面级固定区块**（头部、搜索栏等数量确定的元素）。
 *   不要用在 LazyColumn / LazyRow 的 item 内——item 滚出视口后被回收，
 *   滚回来会重新组合并重播淡入，造成滚动掉帧与视觉闪烁。
 * - 动画结束后会**卸载 graphicsLayer**，不再占用渲染层；
 *   位移量在进入组合时一次性换算为 px，避免每帧重复 toPx()。
 * - 父层提供 [ProvideStaggerGate] 时，同一 index 只播一次。
 */
fun Modifier.staggeredIn(index: Int): Modifier = composed {
    val density = LocalDensity.current
    val gate = LocalStaggerGate.current
    // 入场位移（px）——只换算一次
    val startPx = remember(density) { with(density) { 10.dp.toPx() } }
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(startPx) }
    // 动画完成后置为 true，此后不再挂 graphicsLayer
    var settled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (gate != null && gate.alreadyPlayed(index)) {
            alpha.snapTo(1f)
            offsetY.snapTo(0f)
            settled = true
            return@LaunchedEffect
        }
        val delayMs = STAGGER_STEP_MS * index.coerceAtMost(STAGGER_MAX_INDEX)
        if (delayMs > 0) delay(delayMs)
        launch { alpha.animateTo(1f, tween(STAGGER_DURATION_MS, easing = FastOutSlowInEasing)) }
        launch {
            offsetY.animateTo(0f, tween(STAGGER_DURATION_MS, easing = FastOutSlowInEasing))
            settled = true
        }
    }

    if (settled) Modifier
    else graphicsLayer {
        this.alpha = alpha.value
        translationY = offsetY.value
    }
}

private const val STAGGER_STEP_MS = 28L
private const val STAGGER_MAX_INDEX = 8
private const val STAGGER_DURATION_MS = 230
