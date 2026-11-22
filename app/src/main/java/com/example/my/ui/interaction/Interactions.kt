package com.example.my.ui.interaction

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================
// 1. 按压回弹 —— 干脆的收缩：按下立即压到位，松手快速复位
//    工具风不要"弹一下"的玩具感，damping 0.85 / stiffness 900 只保留极轻的回弹余量
// ============================================================

@Composable
fun pressBounceScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.94f
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 900f),
        label = "pressBounce"
    )
    return scale
}

/**
 * 通用按压回弹点击：按下轻微缩小、松手弹簧复位，再触发点击。
 * indication 置空——回弹缩放本身就是按压反馈，避免再加一层系统水波纹盖住卡片底色。
 * 需要水波的场景仍用 [tapRipple]；本修饰符面向卡片 / 行 / 标签这类整块可点元素。
 */
fun Modifier.pressBounceClickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    pressedScale: Float = 0.96f
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressBounceScale(interaction, pressedScale)
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

// ============================================================
// 2. 水波按钮 —— 以点击点为原点扩散，随按钮外形裁切
// ============================================================

class TapRippleState internal constructor(private val scope: CoroutineScope) {
    internal var origin by mutableStateOf(Offset.Unspecified)
    internal val progress = Animatable(0f)

    fun trigger(position: Offset) {
        origin = position
        scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(430, easing = LinearOutSlowInEasing))
        }
    }
}

@Composable
fun rememberTapRipple(): TapRippleState {
    val scope = rememberCoroutineScope()
    return remember { TapRippleState(scope) }
}

/**
 * 放在 clip(shape) 之后，水波会自动被按钮外形裁切。
 *
 * click lambda 每次重组都是新对象，若直接作为 pointerInput 的 key，
 * 列表滚动时每个 item 的手势节点都会被重建。这里用 rememberUpdatedState
 * 持有回调，pointerInput 只依赖 [state]。
 */
fun Modifier.tapRipple(
    state: TapRippleState,
    color: Color = Color.White.copy(alpha = 0.34f),
    onClick: () -> Unit
): Modifier = composed {
    val currentOnClick = rememberUpdatedState(onClick)
    val currentColor = rememberUpdatedState(color)
    this
        .pointerInput(state) {
            detectTapGestures(
                onPress = { offset ->
                    state.trigger(offset)
                    tryAwaitRelease()
                },
                onTap = { currentOnClick.value() }
            )
        }
        .drawBehind {
            val p = state.progress.value
            if (state.origin != Offset.Unspecified && p > 0f && p < 1f) {
                val radius = hypot(size.width, size.height) * p
                drawCircle(
                    color = currentColor.value.copy(alpha = color.alpha * (1f - p)),
                    radius = radius,
                    center = state.origin
                )
            }
        }
}

/**
 * 仅把按压状态写回 [source]，不触发点击、不画水波；与 [tapRipple] 并行挂在同一控件上，
 * 供 collectIsPressedAsState 读取 pressed，驱动图标微缩、阴影收窄、提亮叠层等按压态动画。
 */
fun Modifier.pressSource(source: MutableInteractionSource): Modifier = composed {
    pointerInput(source) {
        detectTapGestures(
            onPress = { offset ->
                val press = PressInteraction.Press(offset)
                source.tryEmit(press)
                tryAwaitRelease()
                source.tryEmit(PressInteraction.Release(press))
            }
        )
    }
}

// ============================================================
// 触感反馈 —— 选中/确认/收藏等「状态型」交互的轻震（现代优秀 App 标配）
// 只在关键操作上用，避免高频无意义震动
// ============================================================

/** 取当前环境的触感反馈对象：val haptic = rememberHapticFeedback() */
@Composable
fun rememberHapticFeedback(): HapticFeedback = LocalHapticFeedback.current

/** 包装一个回调：先轻震一次再执行。用于确认型交互：onClick = tickHaptic { ... } */
@Composable
fun tickHaptic(block: () -> Unit): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        block()
    }
}

// ============================================================
// 3. 点击爆散 —— 仅用于点赞/收藏等关键操作
// ============================================================

class BurstState internal constructor(private val scope: CoroutineScope) {
    internal val progress = Animatable(0f)

    fun fire() {
        scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(560, easing = LinearOutSlowInEasing))
        }
    }
}

@Composable
fun rememberBurst(): BurstState {
    val scope = rememberCoroutineScope()
    return remember { BurstState(scope) }
}

@Composable
fun BurstEffect(
    state: BurstState,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(Brand.Terracotta, Brand.Gold, Brand.TerracottaLight),
    particleCount: Int = 10,
    reach: Dp = 34.dp
) {
    Canvas(modifier) {
        val p = state.progress.value
        if (p <= 0f || p >= 1f) return@Canvas
        val c = center
        val maxReach = reach.toPx()
        // 冲击环
        drawCircle(
            color = colors.first().copy(alpha = 0.35f * (1f - p)),
            radius = maxReach * 0.35f + maxReach * 0.65f * p,
            center = c,
            style = Stroke(width = 1.5.dp.toPx())
        )
        // 四散粒子
        for (i in 0 until particleCount) {
            val angle = (i * 360f / particleCount) * PI.toFloat() / 180f + 0.4f
            val dist = maxReach * (0.18f + 0.82f * p)
            val pos = Offset(c.x + cos(angle) * dist, c.y + sin(angle) * dist)
            val radius = (2.6f - 1.6f * p).coerceAtLeast(0.7f).dp.toPx()
            drawCircle(
                color = colors[i % colors.size].copy(alpha = 1f - p),
                radius = radius,
                center = pos
            )
        }
    }
}

// ============================================================
// 4. 弹性开关 —— 按下拇指拉长，松手弹簧复位，模拟质量感
// ============================================================

@Composable
fun ElasticSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = Brand.Terracotta
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val position by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.40f, stiffness = 420f),
        label = "elasticSwitchPos"
    )
    val stretch by animateDpAsState(
        targetValue = if (pressed) 9.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 760f),
        label = "elasticSwitchStretch"
    )
    val track by animateColorAsState(
        targetValue = if (checked) activeColor else Bg.SurfaceDim,
        animationSpec = tween(180),
        label = "elasticSwitchTrack"
    )

    // 拉长部分只朝运动方向延伸，保证拇指不越出轨道
    val thumbWidth = 26.dp + stretch
    val travel = 20.dp - stretch * position

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(track)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled
            ) { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 3.dp)
                .offset(x = travel * position)
                .size(width = thumbWidth, height = 26.dp)
                .shadow(2.dp, CircleShape, spotColor = Color(0x33000000))
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

// ============================================================
// 5. 液态滑块 —— 轨道、旋钮、数值共用同一个动画源
// ============================================================

@Composable
fun LiquidSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    title: String = "",
    unit: String = "",
    activeColors: List<Color> = listOf(Brand.TerracottaLight, Brand.Terracotta)
) {
    val start = range.first
    val end = range.last
    val span = (end - start).coerceAtLeast(1)

    val animated = remember { Animatable(value.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(value.toFloat()) }
    var trackWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val knobPx = with(density) { 22.dp.toPx() }
    val scope = rememberCoroutineScope()

    // 非拖动状态下的值变化走弹簧动画（与旋钮、数值同步）
    LaunchedEffect(value, dragging) {
        if (!dragging) {
            animated.animateTo(
                targetValue = value.toFloat(),
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 480f)
            )
        }
    }

    val display = if (dragging) dragValue else animated.value
    val fraction = ((display - start) / span).coerceIn(0f, 1f)

    fun valueAt(x: Float): Float {
        if (trackWidth <= knobPx) return start.toFloat()
        val f = ((x - knobPx / 2f) / (trackWidth - knobPx)).coerceIn(0f, 1f)
        return start + f * span
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (title.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Text.Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${display.roundToInt()}$unit",
                    color = Brand.Terracotta,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(start, end, trackWidth) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        dragging = true
                        dragValue = valueAt(down.position.x)
                        onValueChange(dragValue.roundToInt().coerceIn(start, end))
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            dragValue = valueAt(change.position.x)
                            onValueChange(dragValue.roundToInt().coerceIn(start, end))
                            change.consume()
                        }
                        scope.launch {
                            animated.snapTo(dragValue.coerceIn(start.toFloat(), end.toFloat()))
                            dragging = false
                        }
                    }
                }
                .onSizeChanged { trackWidth = it.width }
        ) {
            // 轨道
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Bg.SurfaceVariant)
            )
            // 已选进度
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(activeColors))
            )
            // 旋钮
            val knobX = with(density) {
                ((trackWidth - knobPx) * fraction).roundToInt()
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(knobX, 0) }
                    .size(22.dp)
                    .shadow(3.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.12f))
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(activeColors))
                )
            }
        }
    }
}

// ============================================================
// 6. 成功勾选 —— 两笔逐段绘制，体现完成过程
// ============================================================

@Composable
fun AnimatedCheck(
    visible: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    color: Color = Brand.Terracotta
) {
    val progress = remember { Animatable(0f) }
    val pop = remember { Animatable(0.7f) }

    LaunchedEffect(visible) {
        if (visible) {
            pop.snapTo(0.7f)
            progress.snapTo(0f)
            launch { pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 460f)) }
            progress.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        } else {
            progress.snapTo(0f)
        }
    }

    Canvas(modifier.size(size)) {
        val scale = pop.value
        drawCircle(color = color, radius = this.size.minDimension / 2f * scale)
        if (visible) {
            val w = this.size.width
            val h = this.size.height
            val a = Offset(w * 0.30f, h * 0.52f)
            val b = Offset(w * 0.45f, h * 0.67f)
            val c = Offset(w * 0.72f, h * 0.36f)
            val stroke = (w * 0.075f).coerceAtLeast(2f)
            val t = progress.value
            val ink = Color.White
            fun lerpOffset(from: Offset, to: Offset, k: Float) =
                Offset(from.x + (to.x - from.x) * k, from.y + (to.y - from.y) * k)

            if (t <= 0.45f) {
                drawLine(ink, a, lerpOffset(a, b, (t / 0.45f).coerceIn(0f, 1f)), stroke, StrokeCap.Round)
            } else {
                drawLine(ink, a, b, stroke, StrokeCap.Round)
                val k = ((t - 0.45f) / 0.55f).coerceIn(0f, 1f)
                if (k > 0f) drawLine(ink, b, lerpOffset(b, c, k), stroke, StrokeCap.Round)
            }
        }
    }
}

// ============================================================
// 7. 数字滚动 —— 长尾缓出，平稳停稳，不急停
// ============================================================

@Composable
fun AnimatedNumber(
    value: Int,
    modifier: Modifier = Modifier,
    color: Color = Text.Primary,
    fontSize: Int = 18,
    fontWeight: FontWeight = FontWeight.Bold,
    prefix: String = "",
    suffix: String = "",
    animationSpec: AnimationSpec<Int> = tween(
        durationMillis = 650,
        easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    )
) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = animationSpec,
        label = "animatedNumber"
    )
    Text(
        text = "$prefix$animated$suffix",
        modifier = modifier,
        color = color,
        fontSize = fontSize.sp,
        fontWeight = fontWeight,
        style = TextStyle(fontFeatureSettings = "tnum")
    )
}

// ============================================================
// 9. 卡片翻面 —— 背面在 90° 时才切换，全程不穿帮
// ============================================================

@Composable
fun FlipCard(
    flipped: Boolean,
    modifier: Modifier = Modifier,
    front: @Composable BoxScope.() -> Unit,
    back: @Composable BoxScope.() -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 420f),
        label = "flipCard"
    )
    val density = LocalDensity.current
    Box(
        modifier = modifier.graphicsLayer {
            rotationY = rotation
            cameraDistance = 16f * density.density
            transformOrigin = TransformOrigin.Center
        }
    ) {
        if (rotation <= 90f) {
            Box(Modifier.fillMaxSize(), content = front)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f },
                content = back
            )
        }
    }
}

// ============================================================
// 12. 汉堡转叉 —— 同一元件连贯形变
// ============================================================

@Composable
fun AnimatedMenuIcon(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Text.Primary
) {
    val p by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "menuMorph"
    )
    val interaction = remember { MutableInteractionSource() }
    val press = pressBounceScale(interaction, 0.9f)

    Canvas(
        modifier = modifier
            .size(40.dp)
            .scale(press)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        val w = size.width * 0.40f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val gap = size.height * 0.105f * (1f - p)
        val stroke = 2.dp.toPx()

        rotate(45f * p, pivot = Offset(cx, cy)) {
            drawLine(tint, Offset(cx - w / 2f, cy - gap), Offset(cx + w / 2f, cy - gap), stroke, StrokeCap.Round)
        }
        rotate(-45f * p, pivot = Offset(cx, cy)) {
            drawLine(tint, Offset(cx - w / 2f, cy + gap), Offset(cx + w / 2f, cy + gap), stroke, StrokeCap.Round)
        }
        val middleWidth = w * (1f - p)
        if (middleWidth > 0.5f) {
            drawLine(
                tint,
                Offset(cx - middleWidth / 2f, cy),
                Offset(cx + middleWidth / 2f, cy),
                stroke,
                StrokeCap.Round
            )
        }
    }
}
