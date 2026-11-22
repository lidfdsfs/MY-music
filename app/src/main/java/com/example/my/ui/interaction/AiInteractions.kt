package com.example.my.ui.interaction

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.R
import com.example.my.ui.icons.Lucide
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.MainGradient
import com.example.my.ui.theme.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.example.my.ui.i18n.tr

// ============================================================
// AI 交互组件库（8 个）
// 1. TearOffPanel          撕线弹出面板：下拉沿虚线撕开露票码，松手回弹
// 2. MorphSaveButton       保存按钮变进度环：按钮 ↔ 圆环 ↔ 「打开」
// 3. RangeSlider2          双头范围滑块：填充伸缩 + 拖动浮出数值标签
// 4. WormPagerIndicator    轮播指示点：当前项拉长，随滑动连续过渡
// 5. PhotoDrawerSheet      照片抽屉面板：面板升起压照片，上拖照片收窄
// 6. TimeRangePicker       拖动选时间段：连选合并成条 + 两端把手微调
// 7. EdgeDockingBall       吸边悬浮球：吸附最近边缘 + 滚动半隐
// 8. WeekDateStrip         横向周日期条：选中背景滑动 + 整周翻页
// ============================================================

// ============================================================
// 1. 撕线弹出面板 —— 底部虚线撕口，下拉底边沿虚线展开露票码，松手回弹合上
//    reveal 高度由拖动实时驱动（*0.75 阻尼感），松手弹簧归零。
// ============================================================

@Composable
fun TearOffPanel(
    code: String,
    modifier: Modifier = Modifier,
    title: String = "足迹票根",
    subtitle: String = "拉住底边向下撕开，查看票码",
    notchColor: Color = Bg.Background
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxRevealPx = remember(density) { with(density) { 92.dp.toPx() } }
    val reveal = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    val revealDp = with(density) { reveal.value.toDp() }
    val frac = (reveal.value / maxRevealPx).coerceIn(0f, 1f)
    // 条码宽度伪随机但稳定：同一票码每次重组保持一致
    val bars = remember(code) {
        var seed = abs(code.hashCode())
        List(26) {
            seed = (seed * 1103515245 + 12345) and 0x7fffffff
            1 + seed % 3
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(26.dp),
                spotColor = Color.Black.copy(alpha = 0.08f),
                ambientColor = Color.Black.copy(alpha = 0.05f)
            )
            .background(Bg.Surface)
    ) {
        // 票面
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brand.Terracotta.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.ConfirmationNumber, contentDescription = null, tint = Brand.Terracotta, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = Text.Muted, fontSize = 12.sp)
            }
            Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brand.Gold.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(tr("凭证"), color = Brand.Terracotta, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
        }

        // 撕口区：虚线 + 两侧半圆缺口 + 底边抓手，整体响应下拉
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragging = true
                            scope.launch { reveal.stop() }
                        },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            scope.launch {
                                reveal.snapTo((reveal.value + dy * 0.75f).coerceIn(0f, maxRevealPx))
                            }
                        },
                        onDragEnd = {
                            dragging = false
                            scope.launch {
                                reveal.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 360f))
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch {
                                reveal.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 360f))
                            }
                        }
                    )
                }
        ) {
            Column {
                // 虚线撕口（随下拉微微绷紧变深）
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                ) {
                    val y = size.height / 2f
                    drawLine(
                        color = Bg.Outline.copy(alpha = 0.6f + 0.4f * frac),
                        start = Offset(24.dp.toPx(), y),
                        end = Offset(size.width - 24.dp.toPx(), y),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
                    )
                    // 两侧缺口：用容器背景色挖出半圆
                    drawCircle(notchColor, radius = 9.dp.toPx(), center = Offset(0f, y))
                    drawCircle(notchColor, radius = 9.dp.toPx(), center = Offset(size.width, y))
                }
                // 隐藏票码层：高度随下拉展开
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(revealDp)
                        .clip(RoundedCornerShape(0.dp)),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.graphicsLayer {
                            alpha = frac
                            translationY = (1f - frac) * -18.dp.toPx()
                        }
                    ) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = code,
                            color = Text.Primary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        // 装饰条码
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            bars.forEach { w ->
                                Box(
                                    modifier = Modifier
                                        .width(w.dp)
                                        .height(18.dp)
                                        .background(Text.Primary.copy(alpha = 0.75f))
                                )
                            }
                        }
                    }
                }
                // 底边抓手
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (dragging) Brand.Terracotta else Bg.Outline)
                    )
                }
            }
        }
    }
}

// ============================================================
// 2. 保存按钮变进度环 —— 点击收缩成圆显环形进度，完成展开回按钮，文字切为打开
//    宽度用 BoxWithConstraints 拿满宽后动画到 56dp 圆；内容 Crossfade 切换。
// ============================================================

enum class MorphSavePhase { Idle, Saving, Done }

@Composable
fun MorphSaveButton(
    phase: MorphSavePhase,
    onSave: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    idleText: String = "记录这段旅程",
    doneText: String = "打开足迹"
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fullWidth = maxWidth
        val w by animateDpAsState(
        targetValue = if (phase == MorphSavePhase.Saving) 56.dp else fullWidth,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 360f),
        label = "morphSaveWidth"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "saveButtonPressScale"
    )
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .width(w)
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Brush.linearGradient(MainGradient))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = phase != MorphSavePhase.Saving
            ) {
                if (phase == MorphSavePhase.Done) onOpen() else onSave()
            },
        contentAlignment = Alignment.Center
    ) {
            Crossfade(phase, label = "morphSaveContent") { p ->
                when (p) {
                    MorphSavePhase.Idle -> Text(
                        idleText,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    MorphSavePhase.Saving -> {
                        val angle by rememberInfiniteTransition(label = "saveRing").animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing)),
                            label = "saveRingAngle"
                        )
                        Canvas(Modifier.size(26.dp)) {
                            drawArc(
                                color = Color.White,
                                startAngle = angle,
                                sweepAngle = 250f,
                                useCenter = false,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    MorphSavePhase.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            doneText,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// 3. 双头范围滑块 —— 双把手间填充随拖动伸缩；拖动时把手上方浮出
//    实时数值标签，松手收回。位置内部归一化 0..1，对外暴露真实值。
// ============================================================

@Composable
fun RangeSlider2(
    low: Float,
    high: Float,
    onChange: (Float, Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    format: (Float) -> String = { it.roundToInt().toString() }
) {
    val span = valueRange.endInclusive - valueRange.start
    val lowP = ((low - valueRange.start) / span).coerceIn(0f, 1f)
    val highP = ((high - valueRange.start) / span).coerceIn(0f, 1f)
    var activeHandle by remember { mutableIntStateOf(-1) } // -1 无 / 0 低 / 1 高
    val currentOnChange = rememberUpdatedState(onChange)

    BoxWithConstraints(modifier.fillMaxWidth().height(64.dp)) {
        val density = LocalDensity.current
        val padPx = with(density) { 11.dp.toPx() }
        val trackW = constraints.maxWidth.toFloat() - padPx * 2

        fun toValue(p: Float) = valueRange.start + p * span

        // 数值气泡：拖动时从把手上方浮出
        listOf(0 to lowP, 1 to highP).forEach { (which, p) ->
            val shown = activeHandle == which
            val scale by animateFloatAsState(
                targetValue = if (shown) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
                label = "rangeBubbleScale$which"
            )
            if (scale > 0.01f) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (padPx + p * trackW - 24.dp.toPx()).roundToInt(),
                                0
                            )
                        }
                        .size(width = 48.dp, height = 26.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brand.Terracotta),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        format(toValue(p)),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        // 轨道
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 11.dp)
                .fillMaxWidth()
                .offset(y = 10.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Bg.Outline)
        )
        // 轨道顶边 y = 64/2 + 10 - 2 = 40dp，把手（22dp）顶边 = 40 + 2 - 11 = 31dp
        val trackTopPx = with(density) { 40.dp.toPx() }.roundToInt()
        val handleTopPx = with(density) { 31.dp.toPx() }.roundToInt()
        // 双把手间填充
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((padPx + lowP * trackW).roundToInt(), trackTopPx) }
                .width(with(density) { ((highP - lowP) * trackW).toDp() })
                .height(4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brand.Terracotta)
        )

        // 把手
        listOf(0 to lowP, 1 to highP).forEach { (which, p) ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            (padPx + p * trackW - 11.dp.toPx()).roundToInt(),
                            handleTopPx
                        )
                    }
                    .size(22.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        spotColor = Color.Black.copy(alpha = 0.1f),
                        ambientColor = Color.Black.copy(alpha = 0.06f)
                    )
                    .clip(CircleShape)
                    .background(Color.White)
                    .pointerInput(which, trackW) {
                        detectHorizontalDragGestures(
                            onDragStart = { activeHandle = which },
                            onDragEnd = { activeHandle = -1 },
                            onDragCancel = { activeHandle = -1 },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                if (trackW <= 0f) return@detectHorizontalDragGestures
                                val dp = dx / trackW
                                if (which == 0) {
                                    val nl = (lowP + dp).coerceIn(0f, highP - 0.02f)
                                    currentOnChange.value(toValue(nl), toValue(highP))
                                } else {
                                    val nh = (highP + dp).coerceIn(lowP + 0.02f, 1f)
                                    currentOnChange.value(toValue(lowP), toValue(nh))
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Brand.Terracotta)
                )
            }
        }
    }
}

// ============================================================
// 4. 轮播指示点 —— 当前项拉长成短条（蠕虫）：滑动前半程向目标伸长，
//    后半程尾缘收缩，在相邻两点间连续过渡。
// ============================================================

@Composable
fun WormPagerIndicator(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.45f),
    dot: Dp = 6.dp,
    gap: Dp = 8.dp
) {
    val count = pagerState.pageCount
    if (count <= 1) return
    Canvas(modifier.width(dot * count + gap * (count - 1)).height(dot)) {
        val d = dot.toPx()
        val step = d + gap.toPx()
        val r = d / 2f
        val cy = size.height / 2f
        for (i in 0 until count) {
            drawCircle(inactiveColor, r, Offset(step * i + r, cy))
        }
        val vp = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
            .coerceIn(0f, (count - 1).toFloat())
        val from = floor(vp).toInt()
        val f = vp - from
        // 前半程右缘伸到下一颗，后半程左缘收过去
        val grow = min(f * 2f, 1f)
        val shrink = max((f - 0.5f) * 2f, 0f)
        val left = step * from + step * shrink
        val right = step * from + d + step * grow
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(left, cy - r),
            size = Size(right - left, d),
            cornerRadius = CornerRadius(r, r)
        )
    }
}

// ============================================================
// 5. 照片抽屉面板 —— 打开时照片铺满上半屏，面板从底部升起压住照片下沿；
//    上拖面板（或下拉照片）照片同步缩成窄条，松手吸附到最近档。
//    手势只放在抓手条与照片区，避开面板内容的纵向滚动。
// ============================================================

@Composable
fun PhotoDrawerSheet(
    modifier: Modifier = Modifier,
    minPhotoFraction: Float = 0.15f,
    maxPhotoFraction: Float = 0.42f,
    photo: @Composable BoxScope.() -> Unit,
    onPhotoPullDown: (() -> Unit)? = null,
    panel: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val totalH = constraints.maxHeight.toFloat()
        val scope = rememberCoroutineScope()
        val progress = remember { Animatable(0f) } // 0=照片铺满 1=收窄
        val entrance = remember { Animatable(0f) }
        val dragRange = totalH * (maxPhotoFraction - minPhotoFraction)

        LaunchedEffect(Unit) {
            // 苹果风格抽屉入场：平滑上升+轻微回弹，更自然
            entrance.animateTo(
                1f,
                spring(dampingRatio = 0.75f, stiffness = 320f)
            )
        }

        /** 拖动联动：上拖进度 +，下拉进度 -，松手吸附档位 */
        fun Modifier.drawerDrag(onPullDown: (() -> Unit)? = null): Modifier = this.pointerInput(dragRange) {
            var pulledDown = 0f
            detectVerticalDragGestures(
                onDragStart = {
                    pulledDown = 0f
                    scope.launch { progress.stop() }
                },
                onVerticalDrag = { change, dy ->
                    change.consume()
                    if (onPullDown != null && progress.value <= 0.01f && dy > 0f) {
                        pulledDown += dy
                    }
                    if (dragRange > 0f) {
                        scope.launch {
                            progress.snapTo((progress.value - dy / dragRange).coerceIn(0f, 1f))
                        }
                    }
                },
                onDragEnd = {
                    if (onPullDown != null && pulledDown > 90.dp.toPx()) {
                        onPullDown()
                        scope.launch { progress.snapTo(0f) }
                    } else {
                        scope.launch {
                            progress.animateTo(
                                if (progress.value < 0.5f) 0f else 1f,
                                spring(dampingRatio = 0.72f, stiffness = 380f)
                            )
                        }
                    }
                },
                onDragCancel = {
                    pulledDown = 0f
                    scope.launch {
                        progress.animateTo(
                            if (progress.value < 0.5f) 0f else 1f,
                            spring(dampingRatio = 0.62f, stiffness = 420f)
                        )
                    }
                }
            )
        }

        val photoHPx = totalH * (maxPhotoFraction + (minPhotoFraction - maxPhotoFraction) * progress.value)
        val overlapPx = with(density) { 24.dp.toPx() }
        val panelTopPx = (photoHPx - overlapPx).coerceAtLeast(0f)

        // 照片区（顶部，下拉可还原展开）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { photoHPx.toDp() })
                .graphicsLayer {
                    alpha = entrance.value
                    val s = 1.08f - 0.08f * entrance.value
                    scaleX = s
                    scaleY = s
                }
                .drawerDrag(onPullDown = onPhotoPullDown)
        ) {
            photo()
        }

        // 抽屉面板：顶部圆角压住照片下沿
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .offset {
                    IntOffset(
                        0,
                        (panelTopPx + (1f - entrance.value) * totalH * 0.55f).roundToInt()
                    )
                }
                .height(with(density) { (totalH - panelTopPx).coerceAtLeast(0f).toDp() })
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Bg.Surface)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {}
        ) {
            // 抓手条：唯一驱动面板拖动的区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .drawerDrag(onPullDown = onPhotoPullDown),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Bg.Outline)
                )
            }
            panel()
        }
    }
}

// ============================================================
// 6. 拖动选时间段 —— 按住拖动连选多个时段合并成条，两端把手可单独调整，
//    起止标签实时更新。选择对外暴露为连续下标区间。
// ============================================================

@Composable
fun TimeRangePicker(
    slots: List<String>,
    selected: IntRange?,
    onChange: (IntRange?) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragRange by remember { mutableStateOf<IntRange?>(null) }
    val shown = dragRange ?: selected
    val currentSelected = rememberUpdatedState(selected)
    val currentOnChange = rememberUpdatedState(onChange)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val segW = widthPx / slots.size

        fun indexAt(x: Float) = (x / segW).toInt().coerceIn(0, slots.lastIndex)

        Column {
            // 起止实时标签
            Text(
                text = if (shown == null) tr("按住拖动连选时段，两端把手可微调")
                else tr("起 ${slots[shown.first]} · 止 ${slots[shown.last]}",
                    "${slots[shown.first]} – ${slots[shown.last]}"),
                color = if (shown == null) Text.Muted else Brand.Terracotta,
                fontSize = 12.sp,
                fontWeight = if (shown == null) FontWeight.Normal else FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                // 合并选区条
                if (shown != null) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((shown.first * segW).roundToInt(), 0) }
                            .width(with(density) { ((shown.last - shown.first + 1) * segW).toDp() })
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brand.Terracotta.copy(alpha = 0.16f))
                    )
                }
                // 时段文字层
                Row(Modifier.fillMaxSize()) {
                    slots.forEachIndexed { i, s ->
                        val inRange = shown != null && i in shown
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                s,
                                color = if (inRange) Brand.Terracotta else Text.Muted,
                                fontSize = 12.sp,
                                fontWeight = if (inRange) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
                // 两端把手
                if (shown != null) {
                    listOf(shown.first * segW, (shown.last + 1) * segW).forEach { hx ->
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (hx - 2.5.dp.toPx()).roundToInt(),
                                        ((44.dp - 26.dp).toPx() / 2f).roundToInt()
                                    )
                                }
                                .width(5.dp)
                                .height(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brand.Terracotta)
                        )
                    }
                }
                // 手势层：按下判定「新选 / 调左把手 / 调右把手」，拖动实时更新，松手提交
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(widthPx, slots.size) {
                            val handleSlop = 28.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val cur = currentSelected.value
                                var mode = 0
                                var anchor = indexAt(down.position.x)
                                if (cur != null && abs(down.position.x - cur.first * segW) < handleSlop) {
                                    mode = 1
                                } else if (cur != null && abs(down.position.x - (cur.last + 1) * segW) < handleSlop) {
                                    mode = 2
                                } else {
                                    dragRange = anchor..anchor
                                }
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    val idx = indexAt(change.position.x.coerceIn(0f, widthPx - 1f))
                                    dragRange = when (mode) {
                                        0 -> min(anchor, idx)..max(anchor, idx)
                                        1 -> min(idx, cur!!.last)..cur!!.last
                                        else -> cur!!.first..max(idx, cur!!.first)
                                    }
                                    change.consume()
                                }
                                currentOnChange.value(dragRange ?: currentSelected.value)
                                dragRange = null
                            }
                        }
                )
            }
        }
    }
}

// ============================================================
// 7. 吸边悬浮球 —— 拖动后松手吸附最近屏幕边缘；页面滚动时半隐藏，
//    停止滚动约 0.6s 后滑出。附全局滚动探测（非消费型，挂在根容器）。
// ============================================================

class ScrollActivityState internal constructor() {
    var scrolling by mutableStateOf(false)
        private set
    // 空闲时零开销：不再每 150ms 无限轮询，改成「最后一次滚动后一次性延时」。
    // 每次滚动只 cancel + 重启一个轻量协程，页面静止时没有任何协程在跑。
    private var scope: CoroutineScope? = null
    private var settleJob: Job? = null

    internal fun attach(s: CoroutineScope) {
        scope = s
    }

    internal fun detach() {
        settleJob?.cancel()
        settleJob = null
        scope = null
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            touch()
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            touch()
            return Offset.Zero
        }

        private fun touch() {
            scrolling = true
            settleJob?.cancel()
            settleJob = scope?.launch {
                delay(650)
                scrolling = false
            }
        }
    }
}

@Composable
fun rememberScrollActivity(): ScrollActivityState {
    val state = remember { ScrollActivityState() }
    val scope = rememberCoroutineScope()
    DisposableEffect(state) {
        state.attach(scope)
        onDispose { state.detach() }
    }
    return state
}

/**
 * AI 助手吉祥物：纸房子毛绒小人。
 * 自带上下浮动 + 左右轻摆的待机动效，像在打招呼。白色圆形底托保证在浅色/深色背景都看得清。
 */
@Composable
fun AiMascot(
    size: Dp,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    // animate=false 时直接画静态图，不进入 rememberInfiniteTransition：
    // 无限动画不会因为 targetValue 变成 0 就停，照样每帧计算。
    if (!animate) {
        MascotBody(size, modifier)
        return
    }
    val transition = rememberInfiniteTransition(label = "mascot")
    val dy by transition.animateFloat(
        initialValue = 0f, targetValue = -4f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val tilt by transition.animateFloat(
        initialValue = -1.6f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "tilt"
    )
    MascotBody(
        size,
        modifier.graphicsLayer {
            translationY = dy.dp.toPx()
            rotationZ = tilt
        }
    )
}

@Composable
private fun MascotBody(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // 白色圆底托：把吉祥物的白身和背景分开
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White)
        )
        Image(
            painter = painterResource(R.drawable.ai_mascot),
            contentDescription = "AI 助手",
            modifier = Modifier.size(size * 0.92f),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun EdgeDockingBall(
    scrolling: Boolean,
    onClick: () -> Unit,
    onHide: () -> Unit = {},
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    edgePadding: Dp = 14.dp
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val ballPx = with(density) { size.toPx() }
        val padPx = with(density) { edgePadding.toPx() }
        val parentW = constraints.maxWidth.toFloat()
        val parentH = constraints.maxHeight.toFloat()
        val scope = rememberCoroutineScope()
        val x = remember { Animatable(0f) }
        val y = remember { Animatable(0f) }
        var ready by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        var pressed by remember { mutableStateOf(false) }
        val currentOnClick = rememberUpdatedState(onClick)
        val currentOnHide = rememberUpdatedState(onHide)

        // 首次拿到父尺寸后落到右边缘 42% 高度处
        LaunchedEffect(parentW, parentH) {
            if (!ready && parentW > 0f && parentH > 0f) {
                x.snapTo(parentW - padPx - ballPx)
                y.snapTo(parentH * 0.42f)
                ready = true
            }
        }

        val dockedLeft = x.value < (parentW - ballPx) / 2f
        // 滚动半隐：朝吸附边缘缩进 55%，透明度降低
        val hide by animateFloatAsState(
            targetValue = if (scrolling && !dragging && ready) 1f else 0f,
            animationSpec = tween(220),
            label = "edgeBallHide"
        )
        val hideOffset = (if (dockedLeft) -1f else 1f) * ballPx * 0.55f * hide

        if (ready) {
            val pressScale by animateFloatAsState(
                targetValue = if (pressed) 0.9f else 1f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
                label = "edgeBallPress"
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) }
                    .graphicsLayer {
                        translationX = hideOffset
                        alpha = 1f - 0.45f * hide
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .size(size)
                    .shadow(
                        elevation = 14.dp,
                        shape = CircleShape,
                        spotColor = Color.Black.copy(alpha = 0.16f),
                        ambientColor = Color.Black.copy(alpha = 0.10f)
                    )
                    .clip(CircleShape)
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(2.dp)
                        else Modifier
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onTap = { currentOnClick.value() },
                            onLongPress = { currentOnHide.value() }
                        )
                    }
                    .pointerInput(parentW, parentH) {
                        detectDragGestures(
                            onDragStart = {
                                pressed = true
                                dragging = true
                                scope.launch { x.stop(); y.stop() }
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                scope.launch {
                                    x.snapTo((x.value + drag.x).coerceIn(0f, parentW - ballPx))
                                    y.snapTo((y.value + drag.y).coerceIn(padPx, parentH - ballPx - padPx))
                                }
                            },
                            onDragEnd = {
                                pressed = false
                                dragging = false
                                // 吸附最近边缘
                                val target = if (x.value + ballPx / 2f < parentW / 2f) padPx
                                else parentW - padPx - ballPx
                                scope.launch {
                                    x.animateTo(target, spring(dampingRatio = 0.72f, stiffness = 340f))
                                }
                            },
                            onDragCancel = {
                                pressed = false
                                dragging = false
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // 吉祥物替换原来的渐变圆 + Sparkles
                AiMascot(size = size)
            }
        }
    }
}

// ============================================================
// 8. 横向周日期条 —— 选中背景滑动到点击日期；左右滑动整周翻页，
//    月份标题同步更新。日历计算用 Calendar（minSdk 24 无 java.time）。
// ============================================================

private fun weekDaysOf(offsetWeeks: Int): List<Long> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    // 以周一为一周起点
    val shift = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
    cal.add(Calendar.DAY_OF_YEAR, -shift + offsetWeeks * 7)
    return (0 until 7).map {
        val t = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        t
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun WeekDateStrip(
    selectedMillis: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var weekOffset by remember { mutableIntStateOf(0) }
    val dragX = remember { Animatable(0f) }
    val currentOnSelect = rememberUpdatedState(onSelect)
    val today = remember { Calendar.getInstance().timeInMillis }
    val weekLabels = remember { listOf("一", "二", "三", "四", "五", "六", "日") }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val cellW = maxWidth / 7
        val cellWPx = widthPx / 7f

        // 月份标题取当前周周四（该周归属月），整周翻页时同步切换
        val curWeek = remember(weekOffset) { weekDaysOf(weekOffset) }
        val monthCal = Calendar.getInstance().apply { timeInMillis = curWeek[3] }
        val monthTitle = "${monthCal.get(Calendar.YEAR)}年${monthCal.get(Calendar.MONTH) + 1}月"

        // 选中背景：滑动到选中日期（选中项不在当前周时隐藏）
        val selectedIndex = selectedMillis?.let { sel -> curWeek.indexOfFirst { sameDay(it, sel) } } ?: -1
        val pillIndex by animateFloatAsState(
                targetValue = if (selectedIndex >= 0) selectedIndex.toFloat() else 0f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
                label = "weekPillIndex"
            )
        val pillAlpha by animateFloatAsState(
            targetValue = if (selectedIndex >= 0) 1f else 0f,
            animationSpec = tween(160),
            label = "weekPillAlpha"
        )

        Column {
            // 月份标题
            Crossfade(monthTitle, label = "weekMonth") { t ->
                Text(t, color = Text.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            // 星期表头
            Row(Modifier.fillMaxWidth()) {
                weekLabels.forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = Text.Muted,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // 日期行：三周并排，随手势平移
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .pointerInput(widthPx) {
                        detectHorizontalDragGestures(
                            onDragStart = { scope.launch { dragX.stop() } },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                scope.launch { dragX.snapTo(dragX.value + dx) }
                            },
                            onDragEnd = {
                                scope.launch {
                                    val th = widthPx * 0.22f
                                    when {
                                        dragX.value < -th -> {
                                            dragX.animateTo(-widthPx, tween(160))
                                            weekOffset++
                                            dragX.snapTo(widthPx)
                                            dragX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 520f))
                                        }

                                        dragX.value > th -> {
                                            dragX.animateTo(widthPx, tween(160))
                                            weekOffset--
                                            dragX.snapTo(-widthPx)
                                            dragX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 520f))
                                        }

                                        else -> dragX.animateTo(
                                            0f,
                                            spring(dampingRatio = 0.7f, stiffness = 520f)
                                        )
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { dragX.animateTo(0f, tween(160)) } }
                        )
                    }
            ) {
                // 选中背景（先绘制，垫在日期文字下层，随手势同步平移）
                if (pillAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (dragX.value + pillIndex * cellWPx + (cellWPx - 38.dp.toPx()) / 2f).roundToInt(),
                                    ((44.dp - 38.dp).toPx() / 2f).roundToInt()
                                )
                            }
                            .size(38.dp)
                            .graphicsLayer { alpha = pillAlpha * 0.9f }
                            .shadow(
                                elevation = 4.dp,
                                shape = RoundedCornerShape(16.dp),
                                spotColor = Color.Black.copy(alpha = 0.1f),
                                ambientColor = Color.Black.copy(alpha = 0.06f)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brand.Terracotta)
                    )
                }
                // 三周内容（上周 / 本周 / 下周）
                Row(
                    modifier = Modifier
                        .offset { IntOffset((dragX.value - widthPx).roundToInt(), 0) }
                ) {
                    listOf(weekOffset - 1, weekOffset, weekOffset + 1).forEach { off ->
                        val days = remember(off) { weekDaysOf(off) }
                        Row(modifier = Modifier.width(this@BoxWithConstraints.maxWidth)) {
                            days.forEach { dayMillis ->
                                val dayCal = Calendar.getInstance().apply { timeInMillis = dayMillis }
                                val isToday = sameDay(dayMillis, today)
                                val isSelected = selectedMillis != null && sameDay(dayMillis, selectedMillis)
                                val dayInteraction = remember { MutableInteractionSource() }
                                val dayPressed by dayInteraction.collectIsPressedAsState()
                                val dayScale by animateFloatAsState(
                                    targetValue = if (dayPressed) 0.9f else 1f,
                                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                                    label = "dayPressScale$dayMillis"
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .graphicsLayer {
                                            scaleX = dayScale
                                            scaleY = dayScale
                                        }
                                        .clickable(
                                            interactionSource = dayInteraction,
                                            indication = null
                                        ) { currentOnSelect.value(dayMillis) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "${dayCal.get(Calendar.DAY_OF_MONTH)}",
                                            color = when {
                                                isSelected -> Color.White
                                                isToday -> Brand.Terracotta
                                                else -> Text.Primary
                                            },
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isToday && !isSelected) {
                                            Spacer(Modifier.height(2.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(3.dp)
                                                    .clip(CircleShape)
                                                    .background(Brand.Terracotta)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
