package com.example.my.ui.interaction

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.example.my.ui.i18n.tr

// ============================================================
// 七种更顺手的交互组件库
// 1. SwipeBackPage            跟手返回（①）
// 2. slideOutCancelClickable  按钮滑出取消（②）
// 3. AnimatedLabel            文字局部过渡（③）
// 4. EdgeDampingState + EdgeHintIndicator  边界阻尼与到头提示（⑦）
// （④方向锁定 / ⑤中途接管 / ⑥落点预览 在 MemoryCardStack 中升级实现）
// ============================================================

// ============================================================
// 1. 跟手返回 —— 边缘侧滑实时跟手：
//    - 按「拖动距离 + 松手速度」判断是否返回；
//    - 中途反向拖回阈值以下 → 松手回弹即撤回；
//    - 回弹动画进行中重新按住 → 直接接管接着拖（⑤ 动画中途接管）。
// ============================================================

@Composable
fun SwipeBackPage(
    enabled: Boolean = true,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val anim = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dismiss = rememberUpdatedState(onDismiss)
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val tx = if (dragging) dragOffset else anim.value
                translationX = tx
                alpha = 1f - (tx / size.width * 0.25f).coerceIn(0f, 0.25f)
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val edgePx = with(density) { 36.dp.toPx() }
                val maxPx = with(density) { 640.dp.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 只响应屏幕左侧边缘开始的手势
                    if (down.position.x > edgePx) return@awaitEachGesture
                    // ⑤ 动画中途接管：回弹动画还在跑时按住，从当前位移接着拖
                    val base = if (dragging) dragOffset else anim.value
                    var totalX = 0f
                    var totalY = 0f
                    var horizontal = false
                    var lastX = down.position.x
                    var lastT = down.uptimeMillis
                    var vel = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val d = change.positionChange()
                        if (!horizontal) {
                            totalX += d.x
                            totalY += d.y
                            if (abs(totalX) > viewConfiguration.touchSlop &&
                                abs(totalX) > abs(totalY) * 1.2f
                            ) {
                                horizontal = true
                                dragging = true
                                dragOffset =
                                    (base + change.position.x - down.position.x).coerceIn(0f, maxPx)
                                change.consume()
                            } else if (abs(totalY) > viewConfiguration.touchSlop) {
                                // 纵向手势：交给页面自身滚动，不参与返回
                                return@awaitEachGesture
                            }
                        } else {
                            change.consume()
                            dragOffset =
                                (base + change.position.x - down.position.x).coerceIn(0f, maxPx)
                            // 松手速度：最近一段位移 / 时间
                            val dt = (change.uptimeMillis - lastT).coerceAtLeast(1)
                            val inst = (change.position.x - lastX) / dt * 1000f
                            vel = vel * 0.6f + inst * 0.4f
                            lastX = change.position.x
                            lastT = change.uptimeMillis
                        }
                    }
                    if (horizontal) {
                        dragging = false
                        val offset = dragOffset
                        val width = size.width.toFloat()
                        // 判定：位移超 40% 屏幕宽；或位移超 18% 且松手速度足够
                        val goBack = offset > width * 0.40f ||
                                (offset > width * 0.18f && vel > 900f)
                        if (goBack) {
                            scope.launch {
                                anim.snapTo(offset)
                                anim.animateTo(width, tween(240, easing = FastOutSlowInEasing))
                                dismiss.value()
                            }
                        } else {
                            scope.launch {
                                anim.snapTo(offset)
                                anim.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                            }
                        }
                    }
                }
            }
    ) {
        content()
    }
}

// ============================================================
// 2. 按钮滑出取消 —— 按下后手指移出按钮范围，松手不触发点击；
//    移出瞬间按压视觉立即回弹，提示这次点击已取消。
//    与普通 clickable 的差异：按下后小幅移动（未超出 slop）仍算点击；
//    手指移出边界即取消；滑动超过 slop 视为滚动意图，不触发。
// ============================================================

fun Modifier.slideOutCancelClickable(
    onClick: () -> Unit,
    enabled: Boolean = true
): Modifier = composed {
    val currentOnClick = rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    val pressVisual: Modifier = this
        .graphicsLayer {
            val scale = if (pressed) 0.97f else 1f
            scaleX = scale
            scaleY = scale
        }
    pressVisual.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            pressed = true
            var inside = true
            var moved = false
            var totalDx = 0f
            var totalDy = 0f
            var up = down
            var released = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.pressed) {
                    up = change
                    val d = change.positionChange()
                    totalDx += d.x
                    totalDy += d.y
                    if (abs(totalDx) > viewConfiguration.touchSlop ||
                        abs(totalDy) > viewConfiguration.touchSlop
                    ) {
                        moved = true
                    }
                    // 手指移出按钮边界：立即取消按压反馈（视觉回弹）
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    if (up.position.x < 0f || up.position.x > w ||
                        up.position.y < 0f || up.position.y > h
                    ) {
                        inside = false
                        pressed = false
                    }
                } else {
                    up = change
                    released = true
                    break
                }
            }
            pressed = false
            if (released && inside && !moved) {
                // 移出边界后即使再移回也保持取消；只有全程在界内且未滑动才算点击
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                if (up.position.x in 0f..w && up.position.y in 0f..h) {
                    currentOnClick.value()
                }
            }
        }
    }
}

// ============================================================
// 3. 文字局部过渡 —— 切换筛选/数值时，只对变化的内容做位移动画；
//    未变化的内容保持原位不动，方便用户快速找到改动点。
// ============================================================

@Composable
fun AnimatedLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF3A3630),
    fontSize: Int = 18,
    fontWeight: FontWeight = FontWeight.Bold
) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            (slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(160)))
                .togetherWith(
                    slideOutVertically(tween(160)) { -it / 3 } + fadeOut(tween(120))
                )
        },
        label = "animatedLabel"
    ) { t ->
        Text(
            text = t,
            color = color,
            fontSize = fontSize.sp,
            fontWeight = fontWeight,
            textAlign = TextAlign.Start
        )
    }
}

// ============================================================
// 4. 边界阻尼 —— 列表滑到顶/底继续拉：越拉阻力越大（吃掉更大比例
//    的滑动量，内容位移更小）；松手由系统回弹；并给出「已经到头」提示。
//    Android 12+ 系统自带拉伸回弹，本组件在其上叠加阻力与提示。
// ============================================================

enum class EdgeHintKind { TOP, BOTTOM }

class EdgeDampingState internal constructor(
    private val scope: CoroutineScope,
    private val atTop: () -> Boolean,
    private val atBottom: () -> Boolean
) {
    var hint by mutableStateOf<EdgeHintKind?>(null)
        private set

    private var overPull = 0f

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            Offset.Zero

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (source != NestedScrollSource.Drag && source != NestedScrollSource.UserInput) {
                return Offset.Zero
            }
            val top = atTop()
            val bottom = atBottom()
            var consumedY = 0f
            if (available.y > 0f && top) {
                overPull += available.y
                if (overPull > 120f) hint = EdgeHintKind.TOP
                // 阻力随拉力增大：overPull 越大，吃掉比例越高
                consumedY = available.y * (overPull / 1200f).coerceIn(0.12f, 0.6f)
            } else if (available.y < 0f && bottom) {
                overPull += -available.y
                if (overPull > 120f) hint = EdgeHintKind.BOTTOM
                consumedY = available.y * (overPull / 1200f).coerceIn(0.12f, 0.6f)
            } else {
                overPull = 0f
            }
            return Offset(0f, consumedY)
        }

        override suspend fun onPreFling(available: Velocity): Velocity = Velocity.Zero

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (available.y < -500f && atBottom()) hint = EdgeHintKind.BOTTOM
            else if (available.y > 500f && atTop()) hint = EdgeHintKind.TOP
            return Velocity.Zero
        }
    }

    fun clearHint() {
        hint = null
    }
}

@Composable
fun rememberEdgeDamping(
    isAtTop: () -> Boolean,
    isAtBottom: () -> Boolean
): EdgeDampingState {
    val scope = rememberCoroutineScope()
    val topRef = rememberUpdatedState(isAtTop)
    val bottomRef = rememberUpdatedState(isAtBottom)
    return remember {
        EdgeDampingState(
            scope,
            { topRef.value() },
            { bottomRef.value() }
        )
    }
}

/** 到头提示：出现后短暂停留自动淡出 */
@Composable
fun EdgeHintIndicator(state: EdgeDampingState, modifier: Modifier = Modifier) {
    val hint = state.hint
    LaunchedEffect(hint) {
        if (hint != null) {
            delay(1100)
            state.clearHint()
        }
    }
    AnimatedVisibility(
        visible = hint != null,
        modifier = modifier,
        enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 2 },
        exit = fadeOut(tween(260))
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(Color(0xCC1F2328))
                .padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Text(
                text = when (hint) {
                    EdgeHintKind.TOP -> tr("已经到顶啦 ↑")
                    EdgeHintKind.BOTTOM -> tr("已经到底啦 ↓")
                    null -> ""
                },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
