package com.example.my.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.ImageSampling
import com.example.my.ui.theme.Text as TextToken
import kotlin.math.roundToInt

/**
 * 等待体验三件套：扫光骨架 / 平滑进度 / 模糊入场。
 *
 * 设计原则：
 * - 骨架只还原「真实布局」，尺寸和最终内容对齐，数据回来时页面不跳；
 * - 进度只跟随真实数据，动画只负责把跳变的数字抹平，绝不做假匀速；
 * - 图片先模糊后清晰，比纯淡入更柔和，也顺便遮住解码瞬间的颗粒感。
 */

/** 品牌渐变（绿 → 蓝），与图标、启动页保持一致 */
private val BrandFlow = listOf(Color(0xFF41D38C), Color(0xFF2F8FE0), Color(0xFF2F72E8))

/**
 * 平滑进度条：传入真实进度（0..1），宽度用 520ms 缓动跟随，
 * 填充部分带一条循环扫光，让「正在下载 / 正在转码」也有呼吸感。
 */
@Composable
fun SmoothProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    colors: List<Color> = BrandFlow,
    showPercent: Boolean = false
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "smoothProgress"
    )
    val transition = rememberInfiniteTransition(label = "progressSweep")
    val sweep by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progressSweepX"
    )
    val radius = RoundedCornerShape(height / 2)
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(radius)
                .background(Bg.SurfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(radius)
                    .background(Brush.horizontalGradient(colors))
                    .drawBehind {
                        // 扫光只画在已填充区间内，宽度变化时自然贴合
                        val w = size.width
                        val band = w * 0.55f
                        val x = sweep * (w + band) - band
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.42f), Color.Transparent),
                                startX = x,
                                endX = x + band
                            )
                        )
                    }
            )
        }
        if (showPercent) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${(animated * 100).roundToInt()}%",
                color = TextToken.Secondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

/**
 * 模糊到清晰：图片解码完成的瞬间，用 700ms 把 14dp 模糊收回到 0，
 * 同时从透明淡入。加载期间铺一层同尺寸扫光骨架，避免出现空洞。
 */
@Composable
fun BlurInAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    filterQuality: FilterQuality = ImageSampling,
    placeholder: Boolean = true
) {
    var loaded by remember(model) { mutableStateOf(false) }
    val blurRadius by animateFloatAsState(
        targetValue = if (loaded) 0f else 14f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "blurIn"
    )
    val fade by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = tween(durationMillis = 360),
        label = "blurFade"
    )
    Box(modifier) {
        if (placeholder && !loaded) {
            SkeletonShimmer(Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
        }
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius.dp)
                .alpha(fade),
            contentScale = contentScale,
            alignment = alignment,
            filterQuality = filterQuality,
            onSuccess = { loaded = true },
            onError = { loaded = true }
        )
    }
}

/** 搜索结果骨架行：两行文字 + 右侧操作块，尺寸贴近真实结果行 */
@Composable
fun SkeletonSongRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonShimmer(Modifier.fillMaxWidth(0.55f).height(13.dp), RoundedCornerShape(6.dp))
            SkeletonShimmer(Modifier.fillMaxWidth(0.32f).height(10.dp), RoundedCornerShape(5.dp))
        }
        Spacer(Modifier.width(12.dp))
        SkeletonShimmer(Modifier.size(width = 44.dp, height = 13.dp), RoundedCornerShape(6.dp))
    }
}

/** 评论骨架行：头像圆 + 两行文字，替代评论区的整页转圈 */
@Composable
fun SkeletonCommentRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        SkeletonShimmer(Modifier.size(34.dp), RoundedCornerShape(50))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonShimmer(Modifier.fillMaxWidth(0.34f).height(11.dp), RoundedCornerShape(5.dp))
            SkeletonShimmer(Modifier.fillMaxWidth(0.92f).height(11.dp), RoundedCornerShape(5.dp))
            SkeletonShimmer(Modifier.fillMaxWidth(0.56f).height(11.dp), RoundedCornerShape(5.dp))
        }
    }
}

/** 歌词骨架：几条长短不一的扫光条，替代「正在搜索歌词…」转圈 */
@Composable
fun SkeletonLyricLines(
    modifier: Modifier = Modifier,
    lines: Int = 6
) {
    val widths = listOf(0.72f, 0.50f, 0.86f, 0.62f, 0.44f, 0.68f, 0.56f, 0.78f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(lines.coerceAtLeast(1)) { i ->
            SkeletonShimmer(
                Modifier.fillMaxWidth(widths[i % widths.size]).height(12.dp),
                RoundedCornerShape(6.dp)
            )
        }
    }
}
