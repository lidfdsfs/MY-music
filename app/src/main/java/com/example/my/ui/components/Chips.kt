package com.example.my.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.ui.interaction.pressBounceClickable
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Ink
import com.example.my.ui.theme.Terracotta

/** 圆角小标签（tag）。
 *  [liquid] 打开后走液态玻璃底纹（折射环 + 色散 + 镜面高光），用于封面图等复杂背景上。
 *
 *  文字色默认不写死：没显式传 [textColor] 时按底色明暗从 [Ink] 取不透明色，
 *  避免旧默认值「25% 白底 + 纯白字」在浅色卡片上完全看不见。
 */
@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Bg.SurfaceVariant,
    backgroundBrush: Brush? = null,
    textColor: Color = Color.Unspecified,
    borderColor: Color = Color.Transparent,
    radius: Dp = 8.dp,
    fontSize: Int = 10,
    liquid: Boolean = false,
    onDark: Boolean? = null
) {
    val bgModifier = when {
        liquid -> Modifier.liquidGlassBackground(radius, LiquidGlassStyles.Pill, onDark)
        backgroundBrush != null -> Modifier.background(backgroundBrush)
        else -> Modifier.background(background)
    }
    val resolvedTextColor = when {
        textColor != Color.Unspecified -> textColor
        liquid -> if (onDark ?: isGlassDark()) Ink.OnCover else Ink.OnGlass
        else -> Ink.OnGlass
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .then(bgModifier)
            .border(width = 1.dp, color = if (liquid) Color.Transparent else borderColor, shape = RoundedCornerShape(radius))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = resolvedTextColor,
            fontSize = fontSize.sp,
            lineHeight = (fontSize + 4).sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 可选中筛选 chip */
@Composable
fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp? = null,
    fontSize: Int = 13
) {
    val bg by animateColorAsState(
        targetValue = if (selected) Terracotta else com.example.my.ui.theme.WarmSurfaceVariant,
        animationSpec = tween(180),
        label = "filterChipBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) Ink.OnAccent else Ink.OnGlassSub,
        animationSpec = tween(180),
        label = "filterChipFg"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .then(if (height != null) Modifier.height(height) else Modifier)
            .background(bg)
            .pressBounceClickable(onClick = onClick, pressedScale = 0.94f)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = fontSize.sp,
            lineHeight = (fontSize + 4).sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 区块标题（左标题 + 右侧动作）。英文标题更长，按内容占宽 + 单行省略，不挤掉动作。 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f, fill = false),
            color = Ink.OnGlass,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            letterSpacing = (-0.2).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        action?.invoke()
    }
}
