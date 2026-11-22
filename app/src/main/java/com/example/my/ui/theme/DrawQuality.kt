package com.example.my.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 绘制质量统一真源 —— 专门解决「毛边」三类问题：
 *
 * 1) 位图缩放锯齿：Compose [androidx.compose.foundation.Image] 默认 [FilterQuality.Low]，
 *    大图缩到缩略图时用最近邻采样，边缘会出锯齿/毛边。全项目 Image 统一传这里的常量。
 * 2) 大面积深色渐变色带（banding）：8bit 渐变在 OLED / 低色深屏上会出现条带，
 *    用一张极淡的噪声贴图平铺打散，GPU 直接采样，几乎零成本。
 * 3) 渐变透明过渡的「黑边」：Color.Transparent 是 0x00000000，直接和实色插值
 *    会在中间段变暗发脏；用 [transparentScrim] 显式给同色 alpha 色阶。
 *
 * 这里只放纯函数 / 常量，不持有状态，任何页面都能直接用。
 */

/** 全局图片采样质量：feed 图、缩略图、头像等绝大多数场景。
 *  Medium（双线性）在清晰度与开销之间取平衡，替代默认的 Low 最近邻。 */
val ImageSampling: FilterQuality = FilterQuality.Medium

/** 高采样质量：全屏大图查看、自定义背景大图等静态放大场景。 */
val ImageSamplingHigh: FilterQuality = FilterQuality.High

/** 黑色 scrim 的多色阶渐变：从全透明到 [endAlpha]，避免单段透明渐变的色带与发灰。
 *  典型用法：Brush.verticalGradient(blackScrim(endAlpha)) */
fun blackScrim(endAlpha: Float): List<androidx.compose.ui.graphics.Color> = listOf(
    androidx.compose.ui.graphics.Color(0x00000000),
    androidx.compose.ui.graphics.Color(0x22000000),
    androidx.compose.ui.graphics.Color(0x55000000),
    androidx.compose.ui.graphics.Color((endAlpha * 255).toInt() shl 24)
)

/** 从下往上加深的黑色 scrim（底部文字压暗专用），多色阶抗色带。 */
fun blackScrimTopToBottom(endAlpha: Float): List<androidx.compose.ui.graphics.Color> = blackScrim(endAlpha)

/**
 * 抗色带噪声画笔：160px 噪声贴图平铺，alpha 极低（叠加后约 2%~4%），
 * 只用来打散渐变条带，肉眼看到的是「平滑」而不是颗粒。
 */
private var cachedNoise: ImageBitmap? = null

/** 噪声贴图（进程内缓存一次，全局复用）。 */
fun debandNoiseTile(): ImageBitmap = cachedNoise ?: run {
    val side = 160
    val rnd = java.util.Random(20261115L)
    val pixels = IntArray(side * side)
    for (i in pixels.indices) {
        val bright = rnd.nextInt(256) > 128
        val alpha = if (bright) rnd.nextInt(26) else rnd.nextInt(20)
        pixels[i] = (alpha shl 24) or (if (bright) 0xFFFFFF else 0x000000)
    }
    android.graphics.Bitmap.createBitmap(pixels, side, side, android.graphics.Bitmap.Config.ARGB_8888)
        .asImageBitmap()
        .also { cachedNoise = it }
}

/** 把噪声平铺成可绘制的 Brush，配合 [androidx.compose.ui.draw.drawBehind] 或 drawRect 使用。 */
fun debandBrush(): ShaderBrush = ShaderBrush(
    ImageShader(debandNoiseTile(), TileMode.Repeated, TileMode.Repeated)
)

/** 渐变 + 噪声的合成画笔：先铺渐变，再叠一层噪声打散色带。
 *  用于合成一张「抗色带」的底，例如全屏 scrim、深色背景。 */
fun antiBandingGradient(
    gradient: Brush,
    noiseAlpha: Float = 0.035f
): List<Pair<Brush, Float>> = listOf(gradient to 1f, debandBrush() to noiseAlpha.coerceIn(0f, 1f))

/** 给任意大面积渐变 Box 叠一层极淡噪声，打散色带。
 *  放在 background(...) 之后调用；噪声贴图 GPU 平铺采样，几乎零开销。 */
fun Modifier.deband(alpha: Float = 0.035f): Modifier = this.drawBehind {
    drawRect(brush = debandBrush(), alpha = alpha.coerceIn(0f, 1f))
}
