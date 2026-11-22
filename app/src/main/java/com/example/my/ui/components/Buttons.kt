package com.example.my.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.ui.interaction.BurstEffect
import com.example.my.ui.interaction.pressBounceScale
import com.example.my.ui.interaction.pressSource
import com.example.my.ui.interaction.rememberBurst
import com.example.my.ui.interaction.rememberTapRipple
import com.example.my.ui.interaction.tapRipple
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Ink
import com.example.my.ui.theme.Terracotta
import com.example.my.ui.theme.TerracottaDark
import com.example.my.ui.i18n.tr

/** 圆形图标按钮（白底 + 轻阴影 + 按压回弹 + 水波；按压时图标微缩、阴影收窄） */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    background: Color = Bg.Surface,
    tint: Color = Ink.OnGlass,
    border: Boolean = true,
    liquid: Boolean = false,
    onDark: Boolean? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val ripple = rememberTapRipple()
    val scale = pressBounceScale(interaction, 0.9f)
    val pressed by interaction.collectIsPressedAsState()
    val iconScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 560f),
        label = "circleIconScale"
    )
    val shadowElev by animateDpAsState(
        targetValue = when { !border -> 0.dp; pressed -> 2.dp; else -> 6.dp },
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 560f),
        label = "circleShadow"
    )
    val dark = onDark ?: isGlassDark()
    val bg = if (liquid) {
        Modifier.liquidGlassBackground(size / 2f, LiquidGlassStyles.Pill, dark)
    } else {
        Modifier.background(background)
    }
    // 深色玻璃上强制白字；其余情况用语义色，杜绝深色主题下「白底 + 亮字」看不见
    val resolvedTint = if (liquid && dark) Ink.OnCover else tint
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .shadow(shadowElev, CircleShape, spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(CircleShape)
            .then(bg)
            .pressSource(interaction)
            .tapRipple(ripple, color = resolvedTint.copy(alpha = 0.18f), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription,
            tint = resolvedTint,
            modifier = Modifier.size(size * 0.52f).scale(iconScale)
        )
    }
}

/** 主按钮（渐变背景 + 按压回弹 + 水波；按压时叠加提亮层、文字轻微下压） */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val ripple = rememberTapRipple()
    val scale = pressBounceScale(interaction, 0.96f)
    val pressed by interaction.collectIsPressedAsState()
    val gloss by animateFloatAsState(
        targetValue = if (pressed) 0.14f else 0f,
        animationSpec = tween(120),
        label = "primaryGloss"
    )
    val labelScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 560f),
        label = "primaryLabelScale"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(50.dp))
            .background(Brush.linearGradient(listOf(Terracotta, TerracottaDark)))
            .pressSource(interaction)
            .tapRipple(ripple, onClick = { if (enabled) onClick() })
            .height(48.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        // 按压提亮叠层：随手指按下浮出一层高光，松开淡出
        if (gloss > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color.White.copy(alpha = gloss))
            )
        }
        Text(
            text = text,
            color = Ink.OnAccent,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            // 英文文案比中文长（"Save profile" vs "保存资料"），按钮高度是固定的 48dp，
            // 不限制行数会折成两行被裁掉
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.scale(labelScale)
        )
    }
}

/**
 * 两态形变图标按钮：选中/未选中之间切换时，旧图标淡出缩小、新图标淡入放大并轻微旋转，
 * 配合按压回弹、轻震与可选爆散粒子。用于收藏、收藏、喜欢这类状态型交互。
 *
 * @param checked 当前是否选中
 * @param onIcon 选中态图标
 * @param offIcon 未选中态图标
 * @param activeTint 选中态图标色
 * @param showBurst 选中瞬间是否播放爆散粒子
 */
@Composable
fun MorphIconButton(
    checked: Boolean,
    onIcon: ImageVector,
    offIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = Ink.OnGlass,
    activeTint: Color = Brand.Terracotta,
    background: Color = Bg.Surface,
    showBurst: Boolean = false,
    contentDescription: String? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val ripple = rememberTapRipple()
    val press = pressBounceScale(interaction, 0.9f)
    val burst = rememberBurst()
    val haptic = LocalHapticFeedback.current
    var prev by remember { mutableStateOf(checked) }

    LaunchedEffect(checked) {
        if (checked != prev) {
            prev = checked
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (showBurst) burst.fire()
        }
    }

    val morph by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 480f),
        label = "morphIcon"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(press)
            .shadow(
                if (showBurst) 6.dp else 0.dp,
                CircleShape,
                spotColor = Color.Black.copy(alpha = 0.10f)
            )
            .clip(CircleShape)
            .background(background)
            .pressSource(interaction)
            .tapRipple(
                ripple,
                color = activeTint.copy(alpha = 0.16f),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (showBurst) {
            BurstEffect(
                burst,
                Modifier.size(size),
                colors = listOf(activeTint, Brand.Gold, Brand.TerracottaLight)
            )
        }
        Icon(
            offIcon,
            contentDescription,
            tint = tint,
            modifier = Modifier
                .size(size * 0.52f)
                .graphicsLayer {
                    alpha = 1f - morph
                    scaleX = 1f - 0.25f * morph
                    scaleY = 1f - 0.25f * morph
                    rotationZ = -14f * morph
                }
        )
        Icon(
            onIcon,
            contentDescription,
            tint = activeTint,
            modifier = Modifier
                .size(size * 0.52f)
                .graphicsLayer {
                    alpha = morph
                    scaleX = 0.8f + 0.2f * morph
                    scaleY = 0.8f + 0.2f * morph
                    rotationZ = 14f * morph
                }
        )
    }
}

/** 关闭按钮（圆形半透明；[liquid] 打开后是液态玻璃圆钮） */
@Composable
fun CloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    liquid: Boolean = false,
    onDark: Boolean? = null
) {
    val dark = onDark ?: isGlassDark()
    // 旧实现用 25% 黑底 + 纯白图标：浅色主题下糊成一片。
    // 改成与主题同向的近实心底 + 自适应图标色，文字/图标对比度恒定达标。
    val bg = if (liquid) {
        Modifier.liquidGlassBackground(size / 2f, LiquidGlassStyles.Pill, dark)
    } else {
        Modifier.background(if (dark) Color(0xE6201C19) else Color(0xF2FFFFFF))
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Close, tr("关闭"),
            tint = if (dark) Ink.OnCover else Ink.OnGlass,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}
