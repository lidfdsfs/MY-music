package com.example.my.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.my.ui.theme.AppPalette
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.GlassType
import com.example.my.ui.theme.Ink
import kotlin.math.sin

/**
 * 苹果「透明液态玻璃」的 Compose 复刻。
 *
 * 对齐 React 液态玻璃库（react-liquid-glass-svg / @tomagranate/liquid-glass /
 * liquid-glass-component）的光学模型，逐项映射到 Android：
 *
 * | React / SVG 实现                              | Compose 实现                                    |
 * |----------------------------------------------|-------------------------------------------------|
 * | `feTurbulence` 生成有机噪声                   | [glassNoise]：seed 驱动的确定性正弦扰动         |
 * | `feDisplacementMap` 按位移图折射背景          | [glassDisplacementRim]：圆角矩形 SDF 边缘法线带 |
 * | 三通道不同 scale → chromatic aberration      | [glassChromaticAberration]：冷/暖双色偏移描边   |
 * | `backdrop-filter: blur()` 磨砂背景            | [LiquidGlassSurface] 的 backdrop 层 `.blur()`   |
 * | `tintColor` 玻璃底色                          | [glassFill]：自适应亮度 + 高不透明填充          |
 * | `glassBorder` inset 高光 + 对角镜面           | [glassSpecular] + [glassInnerEdge]              |
 * | center neutralization zone（中心清澈）        | [glassClearCenter]                              |
 * | `shadow` 外阴影                               | `Modifier.shadow()`                             |
 *
 * 与旧「半透明卡片」最大的区别：**填充不透明度有下限**（[GlassOptics.fillAlpha]）。
 * 旧的 0x59FFFFFF（35%）卡片会把底下的网页文字、课表网格一起透上来，造成文字重叠；
 * 这里中心区域始终 ≥ 0.86，背景只以「模糊后的色块」形式参与，文字对比度恒定达标。
 * 玻璃的"透"来自折射环、色散和高光，而不是把内容直接放进来。
 */
// ------------------------------------------------------------------ 能力与开关

/**
 * 液态玻璃的运行条件与用户开关。
 *
 * 对应 `@callstack/liquid-glass` 的两个概念：
 * - [isSupported] ≈ `isLiquidGlassSupported`：设备有没有能力画真实玻璃。
 *   iOS 26 的液态玻璃是系统级材质（实时模糊 + 边缘折射），Android 的等价能力是
 *   API 31(S) 起的 `RenderEffect` 模糊；API 33(T) 起还能跑 AGSL `RuntimeShader` 做折射。
 *   **不支持时绝不能直接画光学层** —— 玻璃层本身几乎无填充，底下没有可采样的背景，
 *   呈现出来就是「完全透明」，文字与图标会失去承载面。
 * - [enabled]：用户在设置页里的开关。默认开（保持既有观感），可单独关闭省电。
 *
 * 任何玻璃调用点都必须先问 [active]，不要在业务代码里自己写 `SDK_INT >= 31`。
 */
object LiquidGlass {
    /** 设备能力：有 RenderEffect 模糊才有「玻璃」可言 */
    val isSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** 能否跑真正的折射（AGSL RuntimeShader）；低于此版本只有模糊 + 高光近似 */
    val supportsRefraction: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** 设置页开关。snapshot state：切换后所有玻璃表面立刻重组 */
    var enabled: Boolean by mutableStateOf(true)

    /** 实际是否绘制玻璃 = 设备能力 && 用户开关 */
    val active: Boolean get() = enabled && isSupported
}

/**
 * 降级底色：不支持玻璃或用户关闭时使用的半透明背景。
 *
 * 取值刻意保留 [GlassOptics.fillAlpha] 的量级 —— 换掉玻璃只是换掉「光学效果」，
 * 不能把文字的可读性一起换掉。旧机型上这一层必须足够实，否则就是用户说的
 * 「旧手机上看会完全透明」。
 */
@Composable
fun glassFallbackColor(onDark: Boolean? = null, alpha: Float = 0.90f): Color {
    val dark = onDark ?: isGlassDark()
    return glassFill(dark).copy(alpha = alpha.coerceIn(0.55f, 1f))
}

// ------------------------------------------------------------------ 参数

/**
 * 一组液态玻璃光学参数。命名与 React 库 props 保持一致，方便对照调参。
 *
 * @param blurRadius          backdrop 磨砂半径（对应 `backdropBlur`）
 * @param displacementScale   折射强度 0..1（对应 `feDisplacementMap` 的 `scale`）
 * @param turbulenceSeed      噪声种子（对应 `turbulenceSeed`）
 * @param turbulenceFrequency 噪声频率（对应 `turbulenceBaseFrequency`）
 * @param chroma              色散强度 0..1（对应 `chroma` / `aberration`）
 * @param depth               折射环宽度（对应 `depth` / `rim`）
 * @param rimLight            边缘内高光强度（对应 `rimLight`）
 * @param specular            对角镜面反光强度（对应 `specular`）
 * @param fillAlpha           **可读性下限**：中心填充不透明度，绝不能低到透出文字
 * @param shadowElevation     外阴影高度
 */
data class GlassOptics(
    val blurRadius: Dp = 18.dp,
    val displacementScale: Float = 0.85f,
    val turbulenceSeed: Int = 7,
    val turbulenceFrequency: Float = 0.008f,
    val chroma: Float = 0.55f,
    val depth: Dp = 14.dp,
    val rimLight: Float = 0.9f,
    val specular: Float = 0.85f,
    val fillAlpha: Float = 0.88f,
    val shadowElevation: Dp = 8.dp
)

/** 四套预设：卡片 / 胶囊 / 底部弹层 / 顶栏，全部保证文字可读。 */
object LiquidGlassStyles {
    /** 悬浮卡片：攻略详情作者卡、课表今日概览 */
    val Card = GlassOptics(
        blurRadius = 18.dp, displacementScale = 0.85f, turbulenceSeed = 7,
        chroma = 0.55f, depth = 14.dp, rimLight = 0.9f, specular = 0.85f,
        fillAlpha = 0.90f, shadowElevation = 8.dp
    )

    /** 小胶囊：标签、输入框、星期/节次选择 */
    val Pill = GlassOptics(
        blurRadius = 10.dp, displacementScale = 0.55f, turbulenceSeed = 3,
        chroma = 0.32f, depth = 8.dp, rimLight = 0.75f, specular = 0.65f,
        fillAlpha = 0.88f, shadowElevation = 0.dp
    )

    /** 底部弹层：添加课程表单。fillAlpha 最高，底层网格绝不能穿透 */
    val Sheet = GlassOptics(
        blurRadius = 26.dp, displacementScale = 1f, turbulenceSeed = 11,
        chroma = 0.62f, depth = 20.dp, rimLight = 1f, specular = 1f,
        fillAlpha = 0.95f, shadowElevation = 18.dp
    )

    /** 顶栏 / 底栏：几乎实心，只留一道玻璃高光 */
    val Bar = GlassOptics(
        blurRadius = 16.dp, displacementScale = 0.45f, turbulenceSeed = 5,
        chroma = 0.25f, depth = 8.dp, rimLight = 0.6f, specular = 0.5f,
        fillAlpha = 0.97f, shadowElevation = 0.dp
    )
}

/** 玻璃底色：亮文字 → 深色玻璃；暗文字 → 暖白玻璃。随 [AppPalette] 重组。 */
@Composable
fun isGlassDark(): Boolean = AppPalette.value.textPrimary.luminance() > 0.55f

private fun glassFill(dark: Boolean): Color =
    if (dark) Color(0xFF211D1A) else Color(0xFFFDFBF9)

/** 输入框 / 胶囊的实心容器色：替换掉 `Bg.SurfaceVariant.copy(alpha = 0.4f)` 这类二次削弱写法。 */
@Composable
fun glassFieldContainer(focused: Boolean = false): Color = if (isGlassDark()) {
    if (focused) Color(0xF538322D) else Color(0xED302A26)
} else {
    if (focused) Color(0xFFFFFFFF) else Color(0xF7FFFFFF)
}

/** 选择胶囊容器色。 */
@Composable
fun glassChipContainer(selected: Boolean = false): Color {
    if (selected) return Brand.Terracotta.copy(alpha = 0.20f)
    return if (isGlassDark()) Color(0xE62C2723) else Color(0xF2FFFFFF)
}

/** 玻璃上用的主文字色：不透明，杜绝自定义背景态下文字本身半透明造成的重叠。 */
@Composable
fun glassOnSurface(): Color = Ink.OnGlass

// ------------------------------------------------------------------ 组件

/**
 * 液态玻璃表面。
 *
 * @param cornerRadius 圆角半径（光学层与裁剪共用同一个值，边缘折射才对得上）
 * @param optics       光学预设
 * @param backdrop     可选的背景副本，会被强模糊后作为玻璃底纹 —— 对应 `backdrop-filter`
 *                     的采样对象。不传时依然有完整的折射/高光，只是没有背景色块。
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    optics: GlassOptics = LiquidGlassStyles.Card,
    contentAlignment: Alignment = Alignment.TopStart,
    onDark: Boolean? = null,
    backdrop: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = onDark ?: isGlassDark()
    val shape = RoundedCornerShape(cornerRadius)
    val shadow = if (optics.shadowElevation > 0.dp) {
        Modifier.shadow(
            elevation = optics.shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = Color(0x24000000),
            spotColor = Color(0x29000000)
        )
    } else Modifier

    // 降级分支：设备不支持，或用户在设置里关掉了玻璃。
    // 关键是不能只画光学层 —— 光学层的填充很薄，底下没有可采样的背景时就是一片透明，
    // 文字直接压在页面内容上。这里统一换成一层半透明底色，形状、阴影、排版保持不变。
    if (!LiquidGlass.active) {
        Box(
            modifier = modifier
                .then(shadow)
                .clip(shape)
                .background(glassFallbackColor(dark, optics.fillAlpha))
        ) {
            Box(contentAlignment = contentAlignment) {
                CompositionLocalProvider(
                    LocalTextStyle provides
                        if (dark) GlassType.body.copy(color = Ink.OnCover) else GlassType.body
                ) { content() }
            }
        }
        return
    }

    val blur = if (backdrop != null && optics.blurRadius > 0.dp &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        Modifier.blur(optics.blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    } else Modifier

    Box(
        modifier = modifier
            .then(shadow)
            .clip(shape)
    ) {
        // 1) backdrop：真实的"背后内容"，模糊后才允许参与玻璃（不是让它直接透上来）
        if (backdrop != null) {
            Box(Modifier.matchParentSize().then(blur)) { backdrop() }
        }
        // 2) tint + 折射 / 色散 / 镜面高光，全部画在内容之下
        Box(
            Modifier.matchParentSize().drawBehind {
                drawGlassOptics(cornerRadius.toPx(), optics, dark)
            }
        )
        // 3) 内容：文字始终压在≥0.86 不透明底上。
        //    故意不加 matchParentSize —— 这一层要作为父 Box 的尺寸来源，
        //    否则所有子层都是 matchParentSize 时容器测不出高度。
        //    同时注入玻璃默认排版：没有显式给 color 的 Text（输入框 label / placeholder /
        //    按钮文案）会自动拿到不透明、且随玻璃明暗反转的文字色，
        //    不会继承一个半透明值、也不会在深色玻璃上出现深字。
        Box(contentAlignment = contentAlignment) {
            CompositionLocalProvider(
                LocalTextStyle provides
                    if (dark) GlassType.body.copy(color = Ink.OnCover) else GlassType.body
            ) { content() }
        }
    }
}

/**
 * 只画玻璃底纹，不接管布局。用于替换现有 Box/TextField 的 `.background(...)`。
 * 必须用在新层级的**最底**，且调用点本身要是 @Composable（以保证主题切换重组）。
 */
@Composable
fun Modifier.liquidGlassBackground(
    cornerRadius: Dp = 16.dp,
    optics: GlassOptics = LiquidGlassStyles.Pill,
    onDark: Boolean? = null
): Modifier {
    val dark = onDark ?: isGlassDark()
    // 同一套能力判断：不支持 / 用户关闭 → 只画一层半透明圆角底，绝不画近乎空心的光学层
    if (!LiquidGlass.active) {
        val fallback = glassFallbackColor(dark, optics.fillAlpha)
        return this.drawBehind {
            val r = cornerRadius.toPx()
            drawRoundRect(color = fallback, cornerRadius = CornerRadius(r, r))
        }
    }
    return this.drawBehind { drawGlassOptics(cornerRadius.toPx(), optics, dark) }
}

/** 弹层遮罩：把底层压暗到不再与浮层文字抢注意力。 */
@Composable
fun LiquidGlassScrim(modifier: Modifier = Modifier, dim: Float = 0.56f) {
    Box(modifier.liquidGlassScrim(dim))
}

/** 遮罩的 Modifier 版，便于直接接 `.clickable { }` 关闭手势。 */
@Composable
fun Modifier.liquidGlassScrim(dim: Float = 0.56f): Modifier {
    val base = if (isGlassDark()) Color(0xFF000000) else Color(0xFF1C1512)
    return this.background(
        Brush.verticalGradient(
            0f to base.copy(alpha = dim * 0.82f),
            1f to base.copy(alpha = dim)
        )
    )
}

// ------------------------------------------------------------------ 光学绘制

/** 与 `feTurbulence` 等价的确定性扰动：同一 seed / 频率得到同一条纹，不产生随机数抖动。 */
private fun glassNoise(t: Float, seed: Int, frequency: Float): Float {
    val f = (frequency * 900f).coerceIn(0.6f, 14f)
    return (sin(t * f + seed * 0.73f) * 0.6f + sin(t * f * 2.3f + seed * 1.41f) * 0.4f)
        .coerceIn(-1f, 1f)
}

/** 绘制顺序严格对应 React 版的 filter 链：blur → tint → displacement → chroma → specular → border。 */
private fun DrawScope.drawGlassOptics(radiusPx: Float, optics: GlassOptics, dark: Boolean) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val r = radiusPx.coerceIn(0f, minOf(w, h) / 2f)
    val corner = CornerRadius(r, r)
    val depthPx = optics.depth.toPx().coerceAtMost(minOf(w, h) / 3f)
    val fill = glassFill(dark)

    // 1) tint：中心最实、边缘略透。下限由 fillAlpha 保证，任何主题下文字都不被底层干扰
    drawRoundRect(
        brush = Brush.radialGradient(
            0.00f to fill.copy(alpha = optics.fillAlpha),
            0.66f to fill.copy(alpha = optics.fillAlpha * 0.98f),
            1.00f to fill.copy(alpha = (optics.fillAlpha * 0.90f).coerceAtLeast(0.72f)),
            center = Offset(w / 2f, h / 2f),
            radius = maxOf(w, h) * 0.80f
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner
    )

    glassDisplacementRim(w, h, r, corner, depthPx, optics, dark)
    glassChromaticAberration(w, h, corner, optics)
    glassSpecular(w, h, corner, optics, dark)
    glassClearCenter(w, h, r, depthPx, dark)
    glassInnerEdge(w, h, corner, optics, dark)
}

/** `feDisplacementMap` 的视觉等价：越靠边法线位移越强，中心归零；噪声让环带有机的弯折。 */
private fun DrawScope.glassDisplacementRim(
    w: Float, h: Float, r: Float, corner: CornerRadius,
    depthPx: Float, optics: GlassOptics, dark: Boolean
) {
    if (optics.displacementScale <= 0f || depthPx <= 0f) return
    val bands = 5
    val highlight = if (dark) Color(0xFFF6EFE7) else Color.White
    val shade = if (dark) Color(0xFF000000) else Color(0xFF6B5546)
    for (i in 0 until bands) {
        val t = i / bands.toFloat()
        val wobble = 1f + 0.18f * glassNoise(t, optics.turbulenceSeed, optics.turbulenceFrequency)
        val inset = depthPx * (0.12f + t * 0.88f)
        val bandW = (depthPx / bands * wobble).coerceAtLeast(1f.dp.toPx())
        val strength = optics.displacementScale * (1f - t) * optics.rimLight
        val brush = Brush.linearGradient(
            0.00f to highlight.copy(alpha = (0.46f * strength).coerceIn(0f, 1f)),
            0.42f to highlight.copy(alpha = (0.08f * strength).coerceIn(0f, 1f)),
            0.72f to shade.copy(alpha = (0.12f * strength).coerceIn(0f, 1f)),
            1.00f to highlight.copy(alpha = (0.20f * strength).coerceIn(0f, 1f)),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
        drawRoundRect(
            brush = brush,
            topLeft = Offset(inset, inset),
            size = Size((w - 2f * inset).coerceAtLeast(0f), (h - 2f * inset).coerceAtLeast(0f)),
            cornerRadius = CornerRadius((r - inset).coerceAtLeast(0f)),
            style = Stroke(width = bandW)
        )
    }
}

/** 三通道不同位移强度造成的彩虹边：用冷/暖两道极细偏移描边近似。 */
private fun DrawScope.glassChromaticAberration(
    w: Float, h: Float, corner: CornerRadius, optics: GlassOptics
) {
    if (optics.chroma <= 0f) return
    val shift = (0.55f + 0.85f * optics.chroma).dp.toPx()
    val cool = Color(0xFF7FD8FF).copy(alpha = 0.20f * optics.chroma)
    val warm = Color(0xFFFFAE86).copy(alpha = 0.20f * optics.chroma)
    drawRoundRect(
        color = cool,
        topLeft = Offset(-shift, -shift * 0.4f),
        size = Size(w, h),
        cornerRadius = corner,
        style = Stroke(width = 1.1f.dp.toPx())
    )
    drawRoundRect(
        color = warm,
        topLeft = Offset(shift, shift * 0.4f),
        size = Size(w, h),
        cornerRadius = corner,
        style = Stroke(width = 1.1f.dp.toPx())
    )
}

/** `glassBorder` 的 diagonal specular sheen：左上入射的一道斜向镜面反光。 */
private fun DrawScope.glassSpecular(
    w: Float, h: Float, corner: CornerRadius, optics: GlassOptics, dark: Boolean
) {
    if (optics.specular <= 0f) return
    val peak = (if (dark) 0.24f else 0.30f) * optics.specular
    drawRoundRect(
        brush = Brush.linearGradient(
            0.00f to Color.White.copy(alpha = peak),
            0.28f to Color.White.copy(alpha = peak * 0.30f),
            0.58f to Color.White.copy(alpha = 0f),
            1.00f to Color.White.copy(alpha = 0f),
            start = Offset(0f, 0f),
            end = Offset(w * 0.82f, h)
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner
    )
}

/** React 版的 center neutralization zone：中心不做位移，保持清澈。 */
private fun DrawScope.glassClearCenter(
    w: Float, h: Float, r: Float, depthPx: Float, dark: Boolean
) {
    val inset = depthPx * 0.72f
    if (w - 2f * inset <= 0f || h - 2f * inset <= 0f) return
    drawRoundRect(
        color = Color.White.copy(alpha = if (dark) 0.018f else 0.055f),
        topLeft = Offset(inset, inset),
        size = Size(w - 2f * inset, h - 2f * inset),
        cornerRadius = CornerRadius((r - inset).coerceAtLeast(0f))
    )
}

/** inset 内高光（顶亮底暗）+ 外沿细描边，玻璃的"厚度"主要靠这一层。 */
private fun DrawScope.glassInnerEdge(
    w: Float, h: Float, corner: CornerRadius, optics: GlassOptics, dark: Boolean
) {
    val top = if (dark) 0.62f else 0.82f
    drawRoundRect(
        brush = Brush.verticalGradient(
            0.00f to Color.White.copy(alpha = top * optics.rimLight),
            0.16f to Color.White.copy(alpha = 0.10f * optics.rimLight),
            0.80f to Color.Transparent,
            1.00f to Color(0xFF31241C).copy(alpha = 0.20f * optics.rimLight),
            startY = 0f,
            endY = h
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner,
        style = Stroke(width = 1.25f.dp.toPx())
    )
    drawRoundRect(
        color = if (dark) Color.White.copy(alpha = 0.14f) else Color(0xFF7A6455).copy(alpha = 0.14f),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner,
        style = Stroke(width = 0.8f.dp.toPx())
    )
}
