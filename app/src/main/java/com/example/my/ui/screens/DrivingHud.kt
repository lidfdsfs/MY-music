package com.example.my.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.DrivingPalette
import com.example.my.ui.theme.Ink
import com.example.my.ui.theme.TypeScale

/* =====================================================================
 *  驾驶模式的状态机
 * ===================================================================== */

/**
 * 驾驶页七个界面态。
 *
 * 与手机高德的操作链路一一对应：巡航（打开即有）→ 搜索 → **路线预览**
 * （高德选完点后一定先给三条方案让人挑）→ 导航（路口 / 高速两个子态）→ 到达。
 *
 * 原来的六态少了 PREVIEW，导致「选中一个 POI 就直接开始导航」，
 * 用户既看不到备选路线，也改不了偏好——这是本轮推翻重做的核心补全。
 */
internal enum class NavPhase { CRUISE, SEARCH, PREVIEW, NAV, JUNCTION, HIGHWAY, ARRIVE }

/** 导航中的三种子态共用一个判定，工具条收纳等逻辑不必各自判断。 */
internal val NavPhase.isNavigating: Boolean
    get() = this == NavPhase.NAV || this == NavPhase.JUNCTION || this == NavPhase.HIGHWAY

/** 车道指引的一格：方向 + 是否目标车道（高亮蓝底） */
internal data class DriveLane(val dir: NavGlyph, val active: Boolean)

/** 导航 HUD 需要的最小图形集，全部用 Canvas 画，不依赖图标库版本。 */
internal enum class NavGlyph {
    TURN_RIGHT, TURN_LEFT, STRAIGHT, UTURN, KEEP_LEFT, KEEP_RIGHT, ROUNDABOUT, ARRIVE,
    ARROW_RIGHT, ARROW_LEFT, CAMERA, LIST, REFRESH, ALERT, COMPASS, CHECK, SEARCH, PIN, CAR,
    LAYER, TRAFFIC, VOICE, SOUND, MORE, CLOSE, PLUS, MINUS, VOLUME, ROUTE
}

/**
 * 用高德返回的 `action` 挑转向图形。
 *
 * 高德给的 action 是「左转 / 右转 / 左前方 / 右前方 / 靠左 / 靠右 / 进入环岛 /
 * 掉头 / 到达目的地」这样的整词，比从整句 instruction 里找关键字准得多，
 * 所以优先用它；instruction 只在 action 缺失时兜底。
 */
internal fun navGlyphFor(action: String, instruction: String = ""): NavGlyph {
    val a = action.ifBlank { instruction }
    return when {
        a.contains("掉头") || a.contains("u-turn", true) || a.contains("uturn", true) -> NavGlyph.UTURN
        a.contains("环岛") || a.contains("roundabout", true) -> NavGlyph.ROUNDABOUT
        a.contains("靠右") || a.contains("keep right", true) -> NavGlyph.KEEP_RIGHT
        a.contains("靠左") || a.contains("keep left", true) -> NavGlyph.KEEP_LEFT
        a.contains("右") || a.contains("right", true) -> NavGlyph.TURN_RIGHT
        a.contains("左") || a.contains("left", true) -> NavGlyph.TURN_LEFT
        a.contains("到达") || a.contains("arrive", true) -> NavGlyph.ARRIVE
        else -> NavGlyph.STRAIGHT
    }
}

/** 车道箭头：竖长条，直行 / 左转 / 右转 / 掉头。 */
private fun DrawScope.drawNavLane(g: NavGlyph, c: Color) {
    val w = size.width
    val h = size.height
    val sw = w * 0.13f
    val style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val p = Path()
    when (g) {
        NavGlyph.TURN_RIGHT, NavGlyph.KEEP_RIGHT -> {
            p.moveTo(w * 0.40f, h * 0.96f)
            p.lineTo(w * 0.40f, h * 0.44f)
            p.lineTo(w * 0.66f, h * 0.44f)
            p.moveTo(w * 0.54f, h * 0.24f)
            p.lineTo(w * 0.78f, h * 0.44f)
            p.lineTo(w * 0.54f, h * 0.64f)
        }
        NavGlyph.TURN_LEFT, NavGlyph.KEEP_LEFT -> {
            p.moveTo(w * 0.60f, h * 0.96f)
            p.lineTo(w * 0.60f, h * 0.44f)
            p.lineTo(w * 0.34f, h * 0.44f)
            p.moveTo(w * 0.46f, h * 0.24f)
            p.lineTo(w * 0.22f, h * 0.44f)
            p.lineTo(w * 0.46f, h * 0.64f)
        }
        NavGlyph.UTURN -> {
            p.moveTo(w * 0.72f, h * 0.96f)
            p.lineTo(w * 0.72f, h * 0.44f)
            p.cubicTo(w * 0.72f, h * 0.20f, w * 0.28f, h * 0.20f, w * 0.28f, h * 0.44f)
            p.lineTo(w * 0.28f, h * 0.66f)
            p.moveTo(w * 0.12f, h * 0.54f)
            p.lineTo(w * 0.28f, h * 0.74f)
            p.lineTo(w * 0.44f, h * 0.54f)
        }
        else -> {
            p.moveTo(w * 0.5f, h * 0.96f)
            p.lineTo(w * 0.5f, h * 0.28f)
            p.moveTo(w * 0.26f, h * 0.46f)
            p.lineTo(w * 0.5f, h * 0.18f)
            p.lineTo(w * 0.74f, h * 0.46f)
        }
    }
    drawPath(p, c, style = style)
}

/**
 * HUD 通用图形。
 *
 * 全部按 `size` 归一化绘制，同一个函数既能画 17dp 的小图标，
 * 也能画 60dp 的转向块，不会「换个尺寸就变形」。
 */
internal fun DrawScope.drawNavGlyph(g: NavGlyph, c: Color) {
    val w = size.width
    val h = size.height
    val u = minOf(w, h)
    val sw = u * 0.12f
    val bold = Stroke(width = u * 0.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val p = Path()
    when (g) {
        // ---------- 大号转向（引导卡）：粗圆角折线，和高德的白块深箭头一致 ----------
        NavGlyph.TURN_RIGHT -> {
            p.moveTo(w * 0.20f, h * 0.90f)
            p.cubicTo(w * 0.20f, h * 0.36f, w * 0.34f, h * 0.22f, w * 0.70f, h * 0.22f)
            drawPath(p, c, style = bold)
            p.reset()
            p.moveTo(w * 0.52f, h * 0.04f)
            p.lineTo(w * 0.82f, h * 0.22f)
            p.lineTo(w * 0.52f, h * 0.40f)
            drawPath(p, c, style = bold)
        }
        NavGlyph.TURN_LEFT -> {
            p.moveTo(w * 0.80f, h * 0.90f)
            p.cubicTo(w * 0.80f, h * 0.36f, w * 0.66f, h * 0.22f, w * 0.30f, h * 0.22f)
            drawPath(p, c, style = bold)
            p.reset()
            p.moveTo(w * 0.48f, h * 0.04f)
            p.lineTo(w * 0.18f, h * 0.22f)
            p.lineTo(w * 0.48f, h * 0.40f)
            drawPath(p, c, style = bold)
        }
        NavGlyph.STRAIGHT -> {
            p.moveTo(w * 0.5f, h * 0.92f)
            p.lineTo(w * 0.5f, h * 0.14f)
            drawPath(p, c, style = bold)
            p.reset()
            p.moveTo(w * 0.25f, h * 0.36f)
            p.lineTo(w * 0.5f, h * 0.12f)
            p.lineTo(w * 0.75f, h * 0.36f)
            drawPath(p, c, style = bold)
        }
        NavGlyph.UTURN -> {
            // 掉头：一根上行后反折回下方的 U 形，箭头朝下
            p.moveTo(w * 0.72f, h * 0.10f)
            p.lineTo(w * 0.72f, h * 0.56f)
            p.cubicTo(w * 0.72f, h * 0.86f, w * 0.28f, h * 0.86f, w * 0.28f, h * 0.56f)
            p.lineTo(w * 0.28f, h * 0.36f)
            drawPath(p, c, style = bold)
            p.reset()
            p.moveTo(w * 0.10f, h * 0.50f)
            p.lineTo(w * 0.28f, h * 0.28f)
            p.lineTo(w * 0.46f, h * 0.50f)
            drawPath(p, c, style = bold)
        }
        NavGlyph.KEEP_LEFT, NavGlyph.KEEP_RIGHT -> {
            val dir = if (g == NavGlyph.KEEP_RIGHT) 1f else -1f
            p.moveTo(w * (0.5f - dir * 0.12f), h * 0.92f)
            p.lineTo(w * (0.5f - dir * 0.12f), h * 0.48f)
            p.lineTo(w * (0.5f + dir * 0.24f), h * 0.24f)
            drawPath(p, c, style = bold)
            p.reset()
            p.moveTo(w * (0.5f + dir * 0.06f), h * 0.14f)
            p.lineTo(w * (0.5f + dir * 0.40f), h * 0.20f)
            p.lineTo(w * (0.5f + dir * 0.30f), h * 0.46f)
            drawPath(p, c, style = bold)
        }
        NavGlyph.ROUNDABOUT -> {
            drawArc(
                color = c, startAngle = 140f, sweepAngle = 260f, useCenter = false,
                topLeft = Offset(w * 0.18f, h * 0.22f), size = Size(w * 0.62f, h * 0.62f),
                style = bold
            )
            p.moveTo(w * 0.78f, h * 0.20f)
            p.lineTo(w * 0.94f, h * 0.42f)
            p.lineTo(w * 0.68f, h * 0.48f)
            drawPath(p, c, style = bold)
        }
        NavGlyph.ARRIVE -> {
            drawCircle(c, radius = u * 0.18f, center = Offset(w * 0.5f, h * 0.36f), style = bold)
            p.moveTo(w * 0.5f, h * 0.56f)
            p.lineTo(w * 0.5f, h * 0.92f)
            drawPath(p, c, style = bold)
        }
        // ---------- 小号图标 ----------
        NavGlyph.ARROW_RIGHT -> {
            p.moveTo(w * 0.14f, h * 0.5f)
            p.lineTo(w * 0.80f, h * 0.5f)
            drawPath(p, c, style = style)
            p.reset()
            p.moveTo(w * 0.56f, h * 0.22f)
            p.lineTo(w * 0.86f, h * 0.5f)
            p.lineTo(w * 0.56f, h * 0.78f)
            drawPath(p, c, style = style)
        }
        NavGlyph.ARROW_LEFT -> {
            p.moveTo(w * 0.86f, h * 0.5f)
            p.lineTo(w * 0.20f, h * 0.5f)
            drawPath(p, c, style = style)
            p.reset()
            p.moveTo(w * 0.44f, h * 0.22f)
            p.lineTo(w * 0.14f, h * 0.5f)
            p.lineTo(w * 0.44f, h * 0.78f)
            drawPath(p, c, style = style)
        }
        NavGlyph.CAMERA -> {
            drawRoundRect(
                c, topLeft = Offset(w * 0.08f, h * 0.26f),
                size = Size(w * 0.54f, h * 0.48f), cornerRadius = CornerRadius(w * 0.12f)
            )
            drawCircle(c, radius = w * 0.10f, center = Offset(w * 0.35f, h * 0.5f))
            p.moveTo(w * 0.64f, h * 0.40f)
            p.lineTo(w * 0.92f, h * 0.26f)
            p.lineTo(w * 0.92f, h * 0.74f)
            p.lineTo(w * 0.64f, h * 0.60f)
            p.close()
            drawPath(p, c)
        }
        NavGlyph.LIST -> {
            listOf(0.28f, 0.52f, 0.76f).forEachIndexed { i, y ->
                p.moveTo(w * 0.18f, h * y)
                p.lineTo(w * if (i == 0) 0.86f else 0.70f, h * y)
            }
            drawPath(p, c, style = style)
            drawCircle(c, radius = u * 0.075f, center = Offset(w * 0.10f, h * 0.28f))
            drawCircle(c, radius = u * 0.075f, center = Offset(w * 0.10f, h * 0.52f))
            drawCircle(c, radius = u * 0.075f, center = Offset(w * 0.10f, h * 0.76f))
        }
        NavGlyph.REFRESH -> {
            drawArc(
                color = c, startAngle = -60f, sweepAngle = 280f, useCenter = false,
                topLeft = Offset(w * 0.14f, h * 0.14f), size = Size(w * 0.72f, h * 0.72f),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            p.moveTo(w * 0.62f, h * 0.06f)
            p.lineTo(w * 0.92f, h * 0.20f)
            p.lineTo(w * 0.72f, h * 0.42f)
            drawPath(p, c, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        NavGlyph.ALERT -> {
            p.moveTo(w * 0.5f, h * 0.12f)
            p.lineTo(w * 0.94f, h * 0.84f)
            p.lineTo(w * 0.06f, h * 0.84f)
            p.close()
            drawPath(p, c, style = style)
            p.reset()
            p.moveTo(w * 0.5f, h * 0.40f)
            p.lineTo(w * 0.5f, h * 0.60f)
            drawPath(p, c, style = Stroke(width = sw, cap = StrokeCap.Round))
            drawCircle(c, radius = sw * 0.55f, center = Offset(w * 0.5f, h * 0.71f))
        }
        NavGlyph.COMPASS -> {
            p.moveTo(w * 0.5f, h * 0.10f)
            p.lineTo(w * 0.66f, h * 0.52f)
            p.lineTo(w * 0.5f, h * 0.44f)
            p.lineTo(w * 0.34f, h * 0.52f)
            p.close()
            drawPath(p, c)
            p.reset()
            p.moveTo(w * 0.5f, h * 0.90f)
            p.lineTo(w * 0.34f, h * 0.48f)
            p.lineTo(w * 0.5f, h * 0.56f)
            p.lineTo(w * 0.66f, h * 0.48f)
            p.close()
            drawPath(p, c, style = strokeOf(sw * 0.8f))
        }
        NavGlyph.CHECK -> {
            p.moveTo(w * 0.16f, h * 0.54f)
            p.lineTo(w * 0.40f, h * 0.78f)
            p.lineTo(w * 0.86f, h * 0.22f)
            drawPath(p, c, style = Stroke(width = sw * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        NavGlyph.SEARCH -> {
            drawCircle(
                color = c, radius = w * 0.30f, center = Offset(w * 0.42f, h * 0.42f),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            p.moveTo(w * 0.64f, h * 0.64f)
            p.lineTo(w * 0.88f, h * 0.88f)
            drawPath(p, c, style = Stroke(width = sw, cap = StrokeCap.Round))
        }
        NavGlyph.PIN -> {
            p.moveTo(w * 0.5f, h * 0.94f)
            p.cubicTo(w * 0.14f, h * 0.56f, w * 0.18f, h * 0.12f, w * 0.5f, h * 0.12f)
            p.cubicTo(w * 0.82f, h * 0.12f, w * 0.86f, h * 0.56f, w * 0.5f, h * 0.94f)
            p.close()
            drawPath(p, c, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        NavGlyph.CAR -> {
            p.moveTo(w * 0.16f, h * 0.62f)
            p.lineTo(w * 0.26f, h * 0.36f)
            p.lineTo(w * 0.74f, h * 0.36f)
            p.lineTo(w * 0.84f, h * 0.62f)
            p.lineTo(w * 0.86f, h * 0.76f)
            p.lineTo(w * 0.14f, h * 0.76f)
            p.close()
            drawPath(p, c, style = style)
            drawCircle(c, radius = w * 0.06f, center = Offset(w * 0.30f, h * 0.80f))
            drawCircle(c, radius = w * 0.06f, center = Offset(w * 0.70f, h * 0.80f))
        }
        NavGlyph.LAYER -> {
            p.moveTo(w * 0.5f, h * 0.10f)
            p.lineTo(w * 0.92f, h * 0.36f)
            p.lineTo(w * 0.5f, h * 0.62f)
            p.lineTo(w * 0.08f, h * 0.36f)
            p.close()
            drawPath(p, c, style = style)
            p.reset()
            p.moveTo(w * 0.08f, h * 0.58f)
            p.lineTo(w * 0.5f, h * 0.84f)
            p.lineTo(w * 0.92f, h * 0.58f)
            drawPath(p, c, style = style)
        }
        NavGlyph.TRAFFIC -> {
            drawRoundRect(
                c, topLeft = Offset(w * 0.32f, h * 0.06f),
                size = Size(w * 0.36f, h * 0.88f), cornerRadius = CornerRadius(w * 0.16f),
                style = style
            )
            listOf(0.28f, 0.50f, 0.72f).forEach { y ->
                drawCircle(c, radius = w * 0.075f, center = Offset(w * 0.5f, h * y))
            }
        }
        NavGlyph.VOICE -> {
            drawRoundRect(
                c, topLeft = Offset(w * 0.36f, h * 0.08f),
                size = Size(w * 0.28f, h * 0.46f), cornerRadius = CornerRadius(w * 0.14f),
                style = style
            )
            p.moveTo(w * 0.20f, h * 0.48f)
            p.cubicTo(w * 0.20f, h * 0.84f, w * 0.80f, h * 0.84f, w * 0.80f, h * 0.48f)
            p.moveTo(w * 0.5f, h * 0.80f)
            p.lineTo(w * 0.5f, h * 0.94f)
            drawPath(p, c, style = style)
        }
        NavGlyph.SOUND -> {
            drawLine(c, Offset(w * 0.30f, h * 0.24f), Offset(w * 0.30f, h * 0.72f), strokeWidth = sw)
            drawLine(c, Offset(w * 0.68f, h * 0.18f), Offset(w * 0.68f, h * 0.66f), strokeWidth = sw)
            drawLine(c, Offset(w * 0.30f, h * 0.24f), Offset(w * 0.68f, h * 0.18f), strokeWidth = sw)
            drawCircle(c, radius = u * 0.11f, center = Offset(w * 0.21f, h * 0.76f))
            drawCircle(c, radius = u * 0.11f, center = Offset(w * 0.59f, h * 0.70f))
        }
        NavGlyph.MORE -> {
            listOf(0.20f, 0.50f, 0.80f).forEach { x ->
                drawCircle(c, radius = u * 0.065f, center = Offset(w * x, h * 0.5f))
            }
        }
        NavGlyph.CLOSE -> {
            drawLine(c, Offset(w * 0.2f, h * 0.2f), Offset(w * 0.8f, h * 0.8f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(w * 0.8f, h * 0.2f), Offset(w * 0.2f, h * 0.8f), strokeWidth = sw, cap = StrokeCap.Round)
        }
        NavGlyph.PLUS -> {
            drawLine(c, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.82f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
        }
        NavGlyph.MINUS -> {
            drawLine(c, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
        }
        NavGlyph.VOLUME -> {
            p.moveTo(w * 0.12f, h * 0.38f)
            p.lineTo(w * 0.30f, h * 0.38f)
            p.lineTo(w * 0.52f, h * 0.18f)
            p.lineTo(w * 0.52f, h * 0.82f)
            p.lineTo(w * 0.30f, h * 0.62f)
            p.lineTo(w * 0.12f, h * 0.62f)
            p.close()
            drawPath(p, c, style = style)
            drawArc(
                c, startAngle = -60f, sweepAngle = 120f, useCenter = false,
                topLeft = Offset(w * 0.46f, h * 0.26f), size = Size(w * 0.30f, h * 0.48f),
                style = style
            )
        }
        NavGlyph.ROUTE -> {
            drawCircle(c, radius = u * 0.10f, center = Offset(w * 0.22f, h * 0.78f), style = style)
            drawCircle(c, radius = u * 0.10f, center = Offset(w * 0.78f, h * 0.22f), style = style)
            p.moveTo(w * 0.22f, h * 0.66f)
            p.cubicTo(w * 0.22f, h * 0.34f, w * 0.66f, h * 0.66f, w * 0.72f, h * 0.34f)
            drawPath(p, c, style = style)
        }
    }
}

private fun strokeOf(w: Float) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

/* =====================================================================
 *  通用积木
 * ===================================================================== */

/**
 * 地图上的一枚悬浮圆钮。
 *
 * 高德右侧那一列（图层 / 路况 / 指南针 / 缩放）都是这个形态：
 * 半透明深底 + 白色图标 + 选中时蓝底。统一到一个组件，
 * 免得每处各写一遍尺寸与描边，最后高度差 1dp 排不齐。
 */
@Composable
internal fun DriveRoundButton(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    x: Number,
    y: Number,
    size: Number,
    glyph: NavGlyph,
    active: Boolean = false,
    tint: Color = Ink.OnCover,
    onClick: () -> Unit
) {
    val p = DrivingPalette
    Box(
        Modifier
            .offset(nx(x), ny(y))
            .size(nx(size))
            .clip(CircleShape)
            .background(if (active) p.GoBlue else p.Tool.copy(alpha = 0.94f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(nx(size.toFloat() * 0.46f))) {
            drawNavGlyph(glyph, if (active) p.CardWhite else tint)
        }
    }
}

/* =====================================================================
 *  导航态：左上引导卡
 * ===================================================================== */

/**
 * 大引导卡：高德导航页最显眼的那块。
 *
 * 左侧深色块放「还要多远 + 怎么转」，右侧白区放「转到哪条路 + 随后怎么走 + 车道」。
 * 这块卡片的每一个字符串都来自 [com.example.my.data.map.DriveRouteStep]，
 * 没有路线时调用方根本不会渲染它。
 */
@Composable
internal fun DriveGuideCard(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    distance: String,
    unit: String,
    action: String,
    road: String,
    next: String,
    lanes: List<DriveLane>,
    turn: NavGlyph,
    hintPill: String = "",
    onTap: () -> Unit
) {
    val p = DrivingPalette
    Box(
        Modifier
            .offset(nx(16), ny(14))
            .size(nx(568), ny(140))
            .clip(RoundedCornerShape(nx(18)))
            .background(p.CardWhite)
            .clickable { onTap() }
    ) {
        // 左侧深色转向块
        Box(
            Modifier
                .size(nx(232), ny(140))
                .clip(RoundedCornerShape(topStart = nx(18), bottomStart = nx(18)))
                .background(Brush.verticalGradient(listOf(p.RouteCasing, p.GuideDark)))
        ) {
            Canvas(Modifier.offset(nx(86), ny(14)).size(nx(60))) {
                drawNavGlyph(turn, p.CardWhite)
            }
            Box(
                Modifier.offset(nx(0), ny(74)).width(nx(232)).height(ny(54)),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        distance,
                        fontSize = TypeScale.navBig,
                        fontWeight = FontWeight.Bold,
                        color = p.CardWhite,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(nx(4)))
                    Text(
                        unit,
                        Modifier.padding(bottom = ny(6)),
                        fontSize = TypeScale.body,
                        color = p.GuideUnit,
                        maxLines = 1
                    )
                }
            }
        }
        // 右侧白区：动作 + 路名 + 下一步
        Text(
            if (road.isBlank()) action else "$action  $road",
            Modifier.offset(nx(250), ny(18)).width(nx(300)),
            fontSize = TypeScale.display,
            fontWeight = FontWeight.Bold,
            color = p.OnCard,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            next,
            Modifier.offset(nx(250), ny(56)).width(nx(300)),
            fontSize = TypeScale.body,
            color = p.OnCardSub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // 车道：目标车道蓝底，其余灰底，和路口实拍的车道分布对得上
        if (lanes.isNotEmpty()) {
            Row(
                Modifier.offset(nx(250), ny(94)),
                horizontalArrangement = Arrangement.spacedBy(nx(6))
            ) {
                lanes.forEach { lane ->
                    Box(
                        Modifier
                            .size(nx(34), ny(34))
                            .clip(RoundedCornerShape(nx(8)))
                            .background(if (lane.active) p.GoBlue else p.CardWhiteSub),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.size(nx(13), ny(22))) {
                            drawNavLane(lane.dir, if (lane.active) p.CardWhite else p.LaneOffInk)
                        }
                    }
                }
            }
        } else if (hintPill.isNotBlank()) {
            Box(
                Modifier
                    .offset(nx(250), ny(100))
                    .height(ny(24))
                    .clip(RoundedCornerShape(ny(12)))
                    .background(p.CardWhiteSub)
                    .padding(horizontal = nx(10)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    hintPill,
                    fontSize = TypeScale.caption,
                    color = p.OnCardSub,
                    maxLines = 1
                )
            }
        }
    }
}

/* =====================================================================
 *  导航态：剩余信息卡
 * ===================================================================== */

/** 剩余时间 / 剩余里程 / 到达时刻 + 一条按真实进度走的细条。 */
@Composable
internal fun DriveTripCard(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    minutes: String,
    km: String,
    eta: String,
    progress: Float
) {
    val p = DrivingPalette
    Box(
        Modifier
            .offset(nx(592), ny(14))
            .size(nx(200), ny(140))
            .clip(RoundedCornerShape(nx(18)))
            .background(p.CardWhite)
    ) {
        Row(
            Modifier.offset(nx(16), ny(16)),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                minutes,
                fontSize = TypeScale.navBig,
                fontWeight = FontWeight.Bold,
                color = p.GoBlue,
                maxLines = 1
            )
            Spacer(Modifier.width(nx(4)))
            Text(
                tr("分钟", "min"),
                Modifier.padding(bottom = ny(7)),
                fontSize = TypeScale.caption,
                color = p.OnCardSub,
                maxLines = 1
            )
        }
        Text(
            km,
            Modifier.offset(nx(16), ny(66)).width(nx(168)),
            fontSize = TypeScale.section,
            fontWeight = FontWeight.SemiBold,
            color = p.OnCard,
            maxLines = 1
        )
        Text(
            eta,
            Modifier.offset(nx(16), ny(94)).width(nx(168)),
            fontSize = TypeScale.caption,
            color = p.OnCardSub,
            maxLines = 1
        )
        // 进度：已走的部分用高德蓝，未走用浅灰
        Box(
            Modifier.offset(nx(16), ny(122)).size(nx(168), ny(5))
                .clip(RoundedCornerShape(ny(3)))
                .background(p.CardWhiteSub)
        ) {
            Box(
                Modifier
                    .width(nx(168f * progress.coerceIn(0f, 1f)))
                    .height(ny(5))
                    .background(p.GoBlue)
            )
        }
    }
}

/* =====================================================================
 *  导航态：车速 + 限速
 * ===================================================================== */

/**
 * 左下角车速表。
 *
 * 数字取自 GPS 瞬时速度（机型不给速度时用相邻定位差分兜底），
 * 没有定位时整个表盘不出现——高德在没定位时也是这样。
 */
@Composable
internal fun DriveSpeedGauge(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    speed: String
) {
    val p = DrivingPalette
    Box(
        Modifier
            .offset(nx(20), ny(424))
            .size(nx(92))
            .clip(CircleShape)
            .background(p.CardWhite.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                speed,
                fontSize = TypeScale.navMid,
                fontWeight = FontWeight.Bold,
                color = p.OnCard,
                maxLines = 1
            )
            Text(
                "km/h",
                fontSize = TypeScale.micro,
                color = p.OnCardSub,
                maxLines = 1
            )
        }
    }
}

/** 限速牌：红圈白底黑数字，形状与路牌一致，一眼能认。 */
@Composable
internal fun DriveLimitSign(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    limit: String
) {
    val p = DrivingPalette
    Box(
        Modifier
            .offset(nx(120), ny(440))
            .size(nx(60))
            .clip(CircleShape)
            .background(p.CardWhite)
    ) {
        Canvas(Modifier.size(nx(60))) {
            drawCircle(p.WarnRed, radius = size.minDimension * 0.46f, style = Stroke(width = size.minDimension * 0.10f))
        }
        Text(
            limit,
            Modifier.align(Alignment.Center),
            fontSize = TypeScale.title,
            fontWeight = FontWeight.Bold,
            color = p.OnCard,
            maxLines = 1
        )
    }
}

/* =====================================================================
 *  导航态：底部行程条
 * ===================================================================== */

/**
 * 底部白色行程条。
 *
 * 左边是剩余时间与里程，中间是这次行程的进度（已走 / 全程），
 * 右边是「全览」与「退出」——高德导航页的退出就在这条上，
 * 不用再去翻菜单，也避免驾驶中要做「点 ⋯ 再点退出」两步。
 */
@Composable
internal fun DriveBottomBar(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    remainText: String,
    subText: String,
    progress: Float,
    rerouting: Boolean,
    overviewing: Boolean,
    onOverview: () -> Unit,
    onExit: () -> Unit
) {
    val p = DrivingPalette
    Box(
        Modifier
            .offset(nx(16), ny(540))
            .size(nx(1048), ny(58))
            .clip(RoundedCornerShape(nx(16)))
            .background(p.CardWhite.copy(alpha = 0.97f))
    ) {
        Text(
            remainText,
            Modifier.offset(nx(18), ny(10)).width(nx(340)),
            fontSize = TypeScale.section,
            fontWeight = FontWeight.Bold,
            color = p.OnCard,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            subText,
            Modifier.offset(nx(18), ny(33)).width(nx(340)),
            fontSize = TypeScale.caption,
            color = p.OnCardSub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // 中段进度：一条 6dp 的细带，左蓝右浅灰
        Box(
            Modifier.offset(nx(376), ny(26)).size(nx(380), ny(6))
                .clip(RoundedCornerShape(ny(3)))
                .background(p.CardWhiteSub)
        ) {
            Box(
                Modifier
                    .width(nx(380f * progress.coerceIn(0f, 1f)))
                    .height(ny(6))
                    .background(p.GoBlue)
            )
        }
        if (rerouting) {
            Text(
                tr("重新规划中…", "Rerouting…"),
                Modifier.offset(nx(772), ny(20)).width(nx(90)),
                fontSize = TypeScale.caption,
                color = p.WarnRed,
                maxLines = 1
            )
        }
        Box(
            Modifier
                .offset(nx(864), ny(12))
                .size(nx(88), ny(34))
                .clip(RoundedCornerShape(nx(9)))
                .background(p.GoBlueSoft)
                .clickable { onOverview() },
            contentAlignment = Alignment.Center
        ) {
            // 全览之后这个键就地变成「回正」——否则缩到整条路线就回不到车位了
            Text(
                if (overviewing) tr("回正", "Center") else tr("全览", "Overview"),
                fontSize = TypeScale.label,
                fontWeight = FontWeight.SemiBold,
                color = p.GoBlue,
                maxLines = 1
            )
        }
        Box(
            Modifier
                .offset(nx(960), ny(12))
                .size(nx(74), ny(34))
                .clip(RoundedCornerShape(nx(9)))
                .background(p.WarnRedSoft)
                .clickable { onExit() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                tr("退出", "Exit"),
                fontSize = TypeScale.label,
                fontWeight = FontWeight.Bold,
                color = p.WarnRed,
                maxLines = 1
            )
        }
    }
}

/* =====================================================================
 *  合规条：图源 + 审图号 + 比例尺
 * ===================================================================== */

/**
 * 地图合规标注 + 比例尺。
 *
 * **必须常驻在图上**，与处于哪个界面态无关：底图是腾讯瓦片，
 * 就写腾讯地图的审图号；普通图与卫星图共用同一个图源服务号。
 * 米数由地图按当前缩放上报（[onScaleMeters]），不是写死的 200。
 */
@Composable
internal fun DriveComplianceStrip(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    meters: Int
) {
    val p = DrivingPalette
    Row(
        Modifier
            .offset(nx(18), ny(602))
            .height(ny(22))
            .clip(RoundedCornerShape(ny(11)))
            .background(p.GuideDark.copy(alpha = 0.78f))
            .padding(horizontal = nx(10)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 比例尺：一条 46 宽的横线，两端各一短竖
        Canvas(Modifier.size(nx(46), ny(10))) {
            val y = size.height * 0.5f
            drawLine(p.CardWhite, Offset(0f, y), Offset(size.width, y), strokeWidth = size.height * 0.28f)
            drawLine(p.CardWhite, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = size.height * 0.24f)
            drawLine(p.CardWhite, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = size.height * 0.24f)
        }
        Spacer(Modifier.width(nx(6)))
        Text(
            if (meters >= 1000) tr("${meters / 1000} 公里", "${meters / 1000} km")
            else tr("$meters 米", "$meters m"),
            fontSize = TypeScale.micro,
            color = p.CardWhite,
            maxLines = 1
        )
        Spacer(Modifier.width(nx(10)))
        Text(
            tr("腾讯地图 · 审图号 GS粤(2023)1171号 · GCJ-02", "Tencent · GS粤(2023)1171 · GCJ-02"),
            fontSize = TypeScale.micro,
            color = p.GuideSub,
            maxLines = 1
        )
    }
}

/* =====================================================================
 *  导航态：路口放大 / 高速出口
 * ===================================================================== */

/**
 * 路口放大窗。
 *
 * 只在「距当前引导点 120 米内」出现（由真实引导点算出来），
 * 用路名 + 示意路口告诉用户该从哪条道走，不是一直挂在屏幕上。
 */
@Composable
internal fun DriveJunctionCard(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    fromRoad: String,
    toRoad: String,
    turn: NavGlyph,
    distance: String
) {
    val p = DrivingPalette
    Box(
        Modifier
            .offset(nx(740), ny(352))
            .size(nx(308), ny(176))
            .clip(RoundedCornerShape(nx(16)))
            .background(p.CardWhite)
    ) {
        Text(
            tr("路口放大", "Junction"),
            Modifier.offset(nx(14), ny(10)).width(nx(160)),
            fontSize = TypeScale.caption,
            color = p.OnCardSub,
            maxLines = 1
        )
        Text(
            distance,
            Modifier.offset(nx(190), ny(6)).width(nx(104)),
            fontSize = TypeScale.subtitle,
            fontWeight = FontWeight.Bold,
            color = p.GoBlue,
            textAlign = TextAlign.End,
            maxLines = 1
        )
        // 路口示意：横向主路 + 竖向支路 + 一条真实的转向轨迹。
        // 只画一个悬空箭头看不出「从哪条道拐到哪条道」，所以按动作把轨迹画出来。
        Canvas(Modifier.offset(nx(14), ny(34)).size(nx(280), ny(94))) {
            val w = size.width
            val h = size.height
            drawRoundRect(
                p.JunctionCase,
                topLeft = Offset(0f, h * 0.58f),
                size = Size(w, h * 0.34f),
                cornerRadius = CornerRadius(h * 0.10f)
            )
            drawRoundRect(
                p.JunctionPaper,
                topLeft = Offset(w * 0.56f, 0f),
                size = Size(w * 0.26f, h * 0.58f),
                cornerRadius = CornerRadius(h * 0.08f)
            )
            // 主路中线
            drawLine(
                p.CardWhite, Offset(0f, h * 0.75f), Offset(w, h * 0.75f),
                strokeWidth = h * 0.035f, cap = StrokeCap.Round
            )
            val sw = h * 0.11f
            val turnRight = turn == NavGlyph.TURN_RIGHT || turn == NavGlyph.KEEP_RIGHT
            val turnLeft = turn == NavGlyph.TURN_LEFT || turn == NavGlyph.KEEP_LEFT
            val laneX = if (turnRight) w * 0.30f else if (turnLeft) w * 0.70f else w * 0.50f
            val exitX = if (turnRight) w * 0.69f else if (turnLeft) w * 0.31f else w * 0.50f
            val lane = Path().apply {
                moveTo(laneX, h * 1.0f)
                lineTo(laneX, h * 0.75f)
                lineTo(exitX, h * 0.75f)
                lineTo(exitX, h * 0.32f)
            }
            drawPath(lane, p.GoBlue, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // 箭头尖
            val head = Path().apply {
                moveTo(exitX, h * 0.10f)
                lineTo(exitX + sw * 1.45f, h * 0.30f)
                lineTo(exitX - sw * 1.45f, h * 0.30f)
                close()
            }
            drawPath(head, p.GoBlue)
        }
        Text(
            fromRoad,
            Modifier.offset(nx(14), ny(132)).width(nx(140)),
            fontSize = TypeScale.caption,
            color = p.OnCardSub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "→ $toRoad",
            Modifier.offset(nx(150), ny(132)).width(nx(146)),
            fontSize = TypeScale.caption,
            fontWeight = FontWeight.SemiBold,
            color = p.OnCard,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 高速出口预告：绿色出口牌 + 服务区 / 下一出口，只在确实在高速上时出现。 */
@Composable
internal fun DriveHighwayCard(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    exitNo: String,
    road: String,
    detail: String
) {
    val p = DrivingPalette
    Row(
        Modifier
            .offset(nx(740), ny(392))
            .size(nx(308), ny(84))
            .clip(RoundedCornerShape(nx(16)))
            .background(p.GuideDark.copy(alpha = 0.94f))
            .padding(horizontal = nx(14)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(nx(46), ny(46))
                .clip(RoundedCornerShape(nx(10)))
                .background(p.HighwayGreen),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    tr("出口", "EXIT"),
                    fontSize = TypeScale.micro,
                    color = p.CardWhite,
                    maxLines = 1
                )
                Text(
                    exitNo,
                    fontSize = TypeScale.subtitle,
                    fontWeight = FontWeight.Bold,
                    color = p.CardWhite,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(nx(12)))
        Column(Modifier.weight(1f)) {
            Text(
                road,
                fontSize = TypeScale.body,
                fontWeight = FontWeight.SemiBold,
                color = p.CardWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(ny(4)))
            Text(
                detail,
                fontSize = TypeScale.micro,
                color = p.GuideSub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/* =====================================================================
 *  媒体控件（播放器与队列共用）
 * ===================================================================== */

/** 细线 + 圆点把手：点按或横向拖动都能改比例，进度与音量共用一套。 */
@Composable
internal fun DriveTrack(
    fraction: Float,
    onSeek: (Float) -> Unit,
    active: Color,
    knob: Dp,
    modifier: Modifier = Modifier
) {
    val trackColor = DrivingPalette.Road
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    onSeek((p.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSeek((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
    ) {
        val y = size.height / 2f
        val f = fraction.coerceIn(0f, 1f)
        val x = size.width * f
        drawLine(trackColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        if (f > 0f) {
            drawLine(active, Offset(0f, y), Offset(x, y), strokeWidth = 2.5f.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(Ink.OnCover, radius = knob.toPx(), center = Offset(x, y))
        drawCircle(active, radius = knob.toPx() * 0.42f, center = Offset(x, y))
    }
}

/** 几何折纸专辑封面：纯 Canvas 复刻，不依赖网络图片。 */
@Composable
internal fun DriveAlbumArt(modifier: Modifier = Modifier) {
    val p = DrivingPalette
    Canvas(modifier.clip(RoundedCornerShape(12.dp))) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(p.CoverA, p.CoverB)))
        drawRect(brush = Brush.horizontalGradient(listOf(p.CoverA, p.CoverB, p.CoverA)), size = Size(w, h * 0.30f))
        drawRect(color = p.CoverB.copy(alpha = 0.55f), topLeft = Offset(0f, h * 0.27f), size = Size(w, h * 0.055f))
        val fold = Path().apply {
            moveTo(0f, h * 0.32f)
            lineTo(w, h * 0.32f)
            lineTo(w * 0.52f, h * 0.86f)
            lineTo(0f, h * 0.62f)
            close()
        }
        drawPath(fold, Brush.linearGradient(listOf(p.CoverC, p.CoverB)))
        val right = Path().apply {
            moveTo(w * 0.58f, h * 0.32f)
            lineTo(w, h * 0.32f)
            lineTo(w, h * 0.68f)
            close()
        }
        drawPath(right, p.CoverC)
        val bottom = Path().apply {
            moveTo(0f, h)
            lineTo(w, h)
            lineTo(w * 0.52f, h * 0.86f)
            close()
        }
        drawPath(bottom, Brush.linearGradient(listOf(p.CoverD, p.CoverE)))
        drawLine(
            p.CoverA.copy(alpha = 0.85f),
            Offset(0f, h * 0.32f), Offset(w * 0.52f, h * 0.86f),
            strokeWidth = 1.5f.dp.toPx()
        )
    }
}

/** 上一首 / 下一首：一个三角 + 一根竖线，和系统播放器的图形一致。 */
@Composable
internal fun DriveSkipButton(
    forward: Boolean,
    onClick: () -> Unit,
    glyph: Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier.size(glyph).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.minDimension
            val c = Ink.OnCover
            if (forward) {
                val tri = Path().apply {
                    moveTo(0.18f * u, 0.20f * u)
                    lineTo(0.72f * u, 0.50f * u)
                    lineTo(0.18f * u, 0.80f * u)
                    close()
                }
                drawPath(tri, c)
                drawLine(c, Offset(0.82f * u, 0.20f * u), Offset(0.82f * u, 0.80f * u), strokeWidth = u * 0.10f)
            } else {
                val tri = Path().apply {
                    moveTo(0.82f * u, 0.20f * u)
                    lineTo(0.28f * u, 0.50f * u)
                    lineTo(0.82f * u, 0.80f * u)
                    close()
                }
                drawPath(tri, c)
                drawLine(c, Offset(0.18f * u, 0.20f * u), Offset(0.18f * u, 0.80f * u), strokeWidth = u * 0.10f)
            }
        }
    }
}

internal fun DrawScope.drawDrivePause(c: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(c, topLeft = Offset(w * 0.26f, h * 0.18f), size = Size(w * 0.16f, h * 0.64f), cornerRadius = CornerRadius(w * 0.05f))
    drawRoundRect(c, topLeft = Offset(w * 0.58f, h * 0.18f), size = Size(w * 0.16f, h * 0.64f), cornerRadius = CornerRadius(w * 0.05f))
}

internal fun DrawScope.drawDrivePlay(c: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.28f, h * 0.14f)
        lineTo(w * 0.86f, h * 0.5f)
        lineTo(w * 0.28f, h * 0.86f)
        close()
    }
    drawPath(path, c)
}
