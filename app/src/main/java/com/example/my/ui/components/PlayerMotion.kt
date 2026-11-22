package com.example.my.ui.components

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.my.ui.theme.Ink
import kotlin.math.abs
import kotlin.math.sin

private const val TAU = 6.2831853f

/**
 * 配乐条上的流光 / 流动粒子特效。
 *
 * 这里刻意**不画任何进度线和把手**：以前底部那条「细线 + 圆点」被用户点名不要了。
 * 播放进度只靠底色柔光暗示，拖动落点用一团柔光表达，视觉层只负责「炫」。
 */
enum class PlayerMotion(val id: String, val zh: String, val en: String) {
    AURORA("aurora", "极光", "Aurora"),
    COMET("comet", "彗星", "Comet"),
    STARDUST("stardust", "星尘", "Stardust"),
    WAVE("wave", "声波", "Wave"),
    NEON("neon", "霓虹", "Neon"),
    PRISM("prism", "棱镜", "Prism");

    companion object {
        fun byId(id: String?): PlayerMotion = entries.firstOrNull { it.id == id } ?: AURORA
    }
}

/** 特效选择和配色共用同一个偏好文件，重启 App 后仍是上次选的那套 */
object PlayerMotionStore {
    private const val PREFS = "player_style"
    private const val KEY = "motion"

    private val state = mutableStateOf(PlayerMotion.AURORA)
    private var loaded = false

    val current: PlayerMotion get() = state.value

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val id = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        state.value = PlayerMotion.byId(id)
    }

    fun select(context: Context, motion: PlayerMotion) {
        state.value = motion
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, motion.id)
            .apply()
    }
}

/** 粒子只生成一次：每帧不分配对象，也不参与重组 */
private class Particle(
    val y0: Float,
    val speed: Float,
    val size: Float,
    val phase: Float
)

private fun particlesFor(motion: PlayerMotion): List<Particle> {
    val count = when (motion) {
        PlayerMotion.STARDUST, PlayerMotion.PRISM -> 48
        PlayerMotion.COMET -> 18
        else -> 26
    }
    val rnd = java.util.Random(motion.id.hashCode().toLong())
    return List(count) {
        Particle(
            y0 = rnd.nextFloat(),
            speed = 0.35f + rnd.nextFloat() * 1.15f,
            size = 0.7f + rnd.nextFloat() * 1.7f,
            phase = rnd.nextFloat()
        )
    }
}

/**
 * 特效层：盖在 [drawPlayerChrome] 之上、文字之下。
 *
 * 时间只在 Canvas 的 draw 阶段读取：state 变化只触发重绘，不触发重组 / 重新排版。
 * 暂停时不再订阅动画时钟，画一层定格的弱光即可，后台不会空转。
 */
@Composable
fun PlayerMotionLayer(
    style: PlayerStyle,
    motion: PlayerMotion,
    playing: Boolean,
    progress: Float,
    focus: Float?,
    modifier: Modifier = Modifier,
    particleScale: Float = 1f
) {
    // 只有真在播放才订阅动画时钟：暂停时给一个固定相位画定格画面。
    // 原来无条件 rememberInfiniteTransition，注释说的「暂停不再订阅」其实没做到，
    // 暂停/后台时这层仍在按帧空转。
    val phase = if (playing) {
        val transition = rememberInfiniteTransition(label = "player-motion")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "motion-phase"
        )
        p
    } else {
        0.35f
    }
    val particles = remember(motion) { particlesFor(motion) }
    val count = (particles.size * particleScale.coerceIn(0f, 1f)).toInt()

    Canvas(modifier) {
        if (playing) {
            drawPlayerMotion(style, motion, phase, progress, focus, particles, count, dim = false)
        } else {
            // 定格 + 降亮：暂停时看得见余辉，但动画时钟不再驱动这一层
            drawPlayerMotion(style, motion, 0.35f, progress, focus, particles, count, dim = true)
        }
    }
}

private fun DrawScope.drawPlayerMotion(
    style: PlayerStyle,
    motion: PlayerMotion,
    t: Float,
    progress: Float,
    focus: Float?,
    particles: List<Particle>,
    count: Int,
    dim: Boolean
) {
    val w = size.width
    val h = size.height
    val glow = style.glow
    val a = if (dim) 0.42f else 1f

    // 通用扫光：六套特效都有一条缓慢横移的宽光带，作为「流光」底子
    val sweepX = -w * 0.35f + ((t * 1.15f) % 1f) * w * 1.7f
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                glow.copy(alpha = 0.10f * a),
                Ink.OnCover.copy(alpha = 0.055f * a),
                Color.Transparent
            ),
            startX = sweepX - w * 0.24f,
            endX = sweepX + w * 0.24f
        ),
        blendMode = BlendMode.Plus
    )

    // 已播区域的光稍亮，但绝不画成分界线，更不画圆点
    val p = progress.coerceIn(0f, 1f)
    if (p > 0.001f) {
        drawRect(
            brush = Brush.horizontalGradient(
                0f to glow.copy(alpha = 0.05f * a),
                (p * 0.8f) to glow.copy(alpha = 0.10f * a),
                1f to Color.Transparent,
                startX = 0f,
                endX = w
            ),
            blendMode = BlendMode.Plus
        )
    }

    when (motion) {
        PlayerMotion.AURORA -> {
            repeat(3) { k ->
                val cx = -w * 0.45f + ((t * 1.35f + k * 0.31f) % 1f) * w * 1.9f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            glow.copy(alpha = 0.17f * a),
                            Color.Transparent
                        ),
                        start = Offset(cx - w * 0.26f, 0f),
                        end = Offset(cx + w * 0.26f, h)
                    ),
                    blendMode = BlendMode.Plus
                )
            }
            val wave = Path()
            for (i in 0..28) {
                val x = w * i / 28f
                val y = h * 0.52f + sin(x / w * TAU * 1.4f + t * TAU * 2f) * h * 0.17f
                if (i == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
            }
            drawPath(
                wave,
                color = glow.copy(alpha = 0.16f * a),
                style = Stroke(width = h * 0.30f, cap = StrokeCap.Round),
                blendMode = BlendMode.Plus
            )
            drawPath(
                wave,
                color = Ink.OnCover.copy(alpha = 0.20f * a),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                blendMode = BlendMode.Plus
            )
            drawParticles(particles, count, t, glow, a, 1f)
        }

        PlayerMotion.COMET -> {
            repeat(3) { k ->
                val head = ((t * 1.15f + k * 0.36f) % 1f) * (w + 180f) - 90f
                val y = h * (0.26f + 0.24f * k)
                val tail = 110f + 40f * k
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, glow.copy(alpha = 0.55f * a)),
                        startX = head - tail,
                        endX = head
                    ),
                    start = Offset(head - tail, y),
                    end = Offset(head, y),
                    strokeWidth = (2.4f + k * 0.6f).dp.toPx(),
                    cap = StrokeCap.Round,
                    blendMode = BlendMode.Plus
                )
                val r = 16.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glow.copy(alpha = 0.9f * a),
                            Ink.OnCover.copy(alpha = 0.35f * a),
                            Color.Transparent
                        ),
                        center = Offset(head, y),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(head, y),
                    blendMode = BlendMode.Plus
                )
            }
            drawParticles(particles, count, t, glow, a, 1.4f)
        }

        PlayerMotion.STARDUST -> {
            val cx = -w * 0.3f + ((t * 0.8f) % 1f) * w * 1.6f
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        glow.copy(alpha = 0.13f * a),
                        Color.Transparent
                    ),
                    startX = cx - w * 0.3f,
                    endX = cx + w * 0.3f
                ),
                blendMode = BlendMode.Plus
            )
            drawParticles(particles, count, t, glow, a, 1f)
        }

        PlayerMotion.WAVE -> {
            repeat(3) { k ->
                val path = Path()
                for (i in 0..36) {
                    val x = w * i / 36f
                    val y = h * 0.5f +
                        sin(x / w * TAU * 2.2f - t * TAU * 2f + k * 0.9f) * h * (0.10f + 0.05f * k)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path,
                    color = glow.copy(alpha = (0.30f - 0.06f * k) * a),
                    style = Stroke(
                        width = (2.5f - 0.5f * k).dp.toPx(),
                        cap = StrokeCap.Round
                    ),
                    blendMode = BlendMode.Plus
                )
            }
            drawParticles(particles, count, t, glow, a, 0.8f)
        }

        PlayerMotion.NEON -> {
            val sx = ((t * 1.25f) % 1f) * (w * 1.3f) - w * 0.15f
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        glow.copy(alpha = 0.30f * a),
                        Ink.OnCover.copy(alpha = 0.12f * a),
                        glow.copy(alpha = 0.22f * a),
                        Color.Transparent
                    ),
                    startX = sx - w * 0.13f,
                    endX = sx + w * 0.13f
                ),
                blendMode = BlendMode.Plus
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(glow.copy(alpha = 0.13f * a), Color.Transparent),
                    startY = 0f,
                    endY = h * 0.5f
                ),
                size = Size(w, h * 0.5f),
                blendMode = BlendMode.Plus
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, glow.copy(alpha = 0.10f * a)),
                    startY = h * 0.5f,
                    endY = h
                ),
                topLeft = Offset(0f, h * 0.5f),
                size = Size(w, h * 0.5f),
                blendMode = BlendMode.Plus
            )
            drawParticles(particles, count, t, glow, a, 1.1f)
        }

        PlayerMotion.PRISM -> {
            repeat(9) { k ->
                val x = ((t * 0.85f + k / 9f) % 1f) * (w + h) - h
                drawLine(
                    color = glow.copy(alpha = 0.11f * a),
                    start = Offset(x, h),
                    end = Offset(x + h * 0.55f, 0f),
                    strokeWidth = 1.dp.toPx(),
                    blendMode = BlendMode.Plus
                )
            }
            drawParticles(particles, count, t, glow, a, 1.8f)
        }
    }

    // 拖动落点：一团柔光，不是圆点把手，也不是横线
    if (focus != null) {
        val fx = (w * focus).coerceIn(0f, w)
        val r = h * 1.15f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glow.copy(alpha = 0.52f),
                    glow.copy(alpha = 0.14f),
                    Color.Transparent
                ),
                center = Offset(fx, h * 0.55f),
                radius = r
            ),
            radius = r,
            center = Offset(fx, h * 0.55f),
            blendMode = BlendMode.Plus
        )
    }
}

/** 星尘：按种子速度横移 + 轻微上浮，生命周期两端淡入淡出 */
private fun DrawScope.drawParticles(
    particles: List<Particle>,
    count: Int,
    t: Float,
    glow: Color,
    a: Float,
    drift: Float
) {
    if (count <= 0 || particles.isEmpty()) return
    val w = size.width
    val h = size.height
    for (i in 0 until minOf(count, particles.size)) {
        val p = particles[i]
        val life = (t * p.speed * drift + p.phase) % 1f
        val x = life * w
        val y = (p.y0 * h + sin(life * TAU * 2f + p.phase * TAU) * h * 0.13f)
            .coerceIn(0f, h)
        val alpha = (1f - abs(life * 2f - 1f)) * 0.85f * a
        if (alpha <= 0.01f) continue
        drawCircle(
            color = glow.copy(alpha = alpha),
            radius = p.size.dp.toPx(),
            center = Offset(x, y),
            blendMode = BlendMode.Plus
        )
    }
}
