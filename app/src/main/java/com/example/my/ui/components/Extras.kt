package com.example.my.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.ui.interaction.pressBounceClickable
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.BorderLight
import com.example.my.ui.theme.FixedInk
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.Spacing
import com.example.my.ui.theme.TextPrimary
import com.example.my.ui.i18n.tr

/**
 * 纯文字标题栏（图二比例）：状态栏用系统 insets 动态适配机型（避免过大/过小），
 * 标题栏固定紧凑高度 48dp，标题居中 17sp 中粗，底部 0.5dp 发丝分割线。
 * 交互按钮一律不放标题栏；需要返回/操作时在内容区用 [BackActionRow]。
 *
 * 标题统一渲染成**居中的黑白底胶囊**（全 App 一致）：底用不透明的 [FixedInk.ChipSurface]，
 * 文字用 [FixedInk.OnChip]，字体固定系统无衬线。这样无论用户把「自定义背景」的字体
 * 换成手写体、还是把文字色调成浅色，标题压在照片背景上依然清楚。
 * 页面需要自绘标题时照旧传 `title = null`。
 *
 * @param insets 容器已自行处理状态栏 insets（如网格内部）时传 false，避免双重内边距。
 */
@Composable
fun AppTopBar(
    title: String? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    insets: Boolean = true,
    rightContent: @Composable (RowScope.() -> Unit) = {}
) {
    val base = Modifier
        .fillMaxWidth()
        .then(if (insets) Modifier.statusBarsPadding() else Modifier)
        .height(48.dp)
        .padding(horizontal = 10.dp)
    Box(modifier = modifier.then(base), contentAlignment = Alignment.Center) {
        if (title != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 230.dp)
                    .clip(RoundedCornerShape(50))
                    .background(FixedInk.ChipSurface)
                    .padding(
                        horizontal = 14.dp,
                        vertical = if (subtitle == null) 4.dp else 3.dp
                    )
            ) {
                Text(
                    text = title,
                    color = FixedInk.OnChip,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = FixedInk.OnChip.copy(alpha = 0.78f),
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = rightContent
        )
        // 微信式细分割线
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(BorderLight)
        )
    }
}

/**
 * 内容区操作行：返回 + 左侧操作 + 右侧操作。
 * 标题栏不放任何可交互按钮，统一由各页面在标题栏下方的内容区调用本组件。
 */
@Composable
fun BackActionRow(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (onBack != null) {
                CircleIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = tr("返回"),
                    onClick = onBack,
                    size = 38.dp,
                    background = Color.Transparent,
                    border = false
                )
            }
            leading()
        }
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = actions
        )
    }
}

/**
 * 「返回 / 退出」按钮：箭头 + 文字。
 *
 * 只放一个圆形箭头时，用户常常把它当成装饰（尤其是在满屏地图上），
 * 加上文字后「点这里可以退出」就没有歧义。发布页与地图选点页共用。
 */
@Composable
fun BackExitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .pressBounceClickable(onClick = onClick, pressedScale = 0.97f)
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = text,
            tint = TextPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 通用卡片容器：白底 + 细描边 + 大圆角（高级感：轻阴影） */
@Composable
fun MediaCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.lg),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val base = Modifier
        .clip(shape)
        .background(Bg.Surface)
        .border(1.dp, BorderLight, shape)
        .then(onClick?.let { Modifier.pressBounceClickable(onClick = it, pressedScale = 0.98f) } ?: Modifier)
    Box(modifier = modifier.then(base)) { content() }
}

/**
 * shimmer 进度共享源。
 *
 * 每个 SkeletonShimmer 各自 rememberInfiniteTransition 会让首屏同时跑
 * 8～10 个无限动画，与页面入场动画叠加时首屏容易掉帧。
 * 由 [ProvideShimmer] 在骨架屏外层提供一次，所有 shimmer 块共用同一个进度。
 */
val LocalShimmerProgress = compositionLocalOf<Float?> { null }

@Composable
fun ProvideShimmer(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX"
    )
    CompositionLocalProvider(LocalShimmerProgress provides x, content = content)
}

/** 骨架屏 shimmer 块 */
@Composable
fun SkeletonShimmer(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.sm)
) {
    // 父层提供共享进度时直接用，否则退化为自带一个（向后兼容）
    val shared = LocalShimmerProgress.current
    val own = if (shared == null) {
        val transition = rememberInfiniteTransition(label = "shimmer")
        transition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
            label = "shimmerX"
        ).value
    } else 0f
    val x = shared ?: own
    val brush = Brush.linearGradient(
        colors = listOf(Bg.SurfaceVariant, Bg.SurfaceDim, Bg.SurfaceVariant),
        start = Offset(x * 400f, 0f),
        end = Offset(x * 400f + 200f, 0f)
    )
    Box(modifier = modifier.clip(shape).background(brush))
}

/** 数字滚动动画文本 */
@Composable
fun CountUpText(
    value: Int,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    style: TextStyle = TextStyle.Default,
    color: Color = TextPrimary,
    fontWeight: FontWeight = FontWeight.Bold,
    fontSize: Int = 17
) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = tween(600),
        label = "countUp"
    )
    Text(
        text = "$prefix$animated$suffix",
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
        fontSize = fontSize.sp
    )
}
