package com.example.my.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import com.example.my.ui.theme.CoverPalette
import com.example.my.ui.theme.ImageSampling
import com.example.my.ui.theme.Terracotta

/** 渐变封面（用 seed 从调色板取色，模拟照片封面）
 *  如果提供了 coverResId 则显示真实图片，否则显示渐变背景
 */
@Composable
fun GradientCover(
    seed: Int,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    coverResId: Int? = null,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (coverResId != null) {
        Image(
            painter = painterResource(id = coverResId),
            contentDescription = null,
            modifier = modifier.clip(shape),
            contentScale = contentScale
        )
    } else {
        val color = CoverPalette[Math.floorMod(seed, CoverPalette.size)]
        Box(
            modifier = modifier
                .clip(shape)
                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.78f))))
        )
    }
}

/** 头像（有网络图则显示图片，否则文字首字母圆形） */
@Composable
fun Avatar(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    background: Color = Terracotta,
    imageUrl: String? = null
) {
    val bmp = imageUrl?.let { rememberUrlBitmap(it) }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
            filterQuality = ImageSampling
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text.take(1),
                color = Color.White,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** 网络图片加载（Coil，默认内存 + 磁盘缓存；失败返回 null） */
@Composable
fun rememberUrlBitmap(url: String): ImageBitmap? {
    val context = LocalContext.current
    var bmp by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        val full = com.example.my.data.BackendApi.fullUrl(url) ?: return@LaunchedEffect
        bmp = runCatching {
            // 复用全局单例，避免每次重组都新建加载器导致缓存失效、图片反复解码
            Coil.imageLoader(context)
                .execute(ImageRequest.Builder(context).data(full).allowHardware(false).build())
                .drawable
                ?.toBitmap()
                ?.asImageBitmap()
        }.getOrNull()
    }
    return bmp
}

/** 数字徽标 */
@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Terracotta
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
