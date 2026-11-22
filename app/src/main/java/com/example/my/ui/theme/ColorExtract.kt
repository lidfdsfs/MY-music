package com.example.my.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs

/**
 * 从一张照片里提取出来的配色。
 *
 * 参考 QQ 音乐 / Apple Music 的「封面取色」：整页的强调色、渐变、进度条都取自当前封面，
 * 于是换一张图，界面整体色相跟着走，比让用户手动点色板更像「一个作品」。
 *
 * @param colors   背景可用的色阶（3–5 个），已按「作为背景」的亮度范围归一
 * @param dominant 面积占比最高的色，用于渐变主段
 * @param accent   饱和度最高的色（提饱和后），用于高亮按钮 / 选中态 / 进度条
 */
data class ExtractedPalette(
    val colors: List<Color>,
    val dominant: Color,
    val accent: Color
) {
    companion object {
        /** 提取失败或还没算出来时的兜底：品牌赤陶 */
        val Fallback = ExtractedPalette(
            colors = listOf(BgBlush, BgPeach1, BgPeach2),
            dominant = BgBlush,
            accent = DefaultHighlight
        )
    }
}

/**
 * 轻量主色提取：降采样 + 色相分桶加权，不引 Palette 依赖。
 *
 * 为什么不用 androidx.palette：
 *  - 它只为「一个主色」设计，返回 6 个固定目标色（Vibrant/DarkMuted…），
 *    这些目标色的亮度是给「文字色"用的，直接拿来当背景会偏暗、偏灰；
 *  - 我们需要的是「一组能铺成渐变的色阶」，还要按用户系统的深/浅色决定提亮还是压暗。
 *  自己分桶算一次只要 64×64 个像素，比 Palette 的八叉树还快。
 *
 * 步骤：
 *  1. 缩到长边 [SAMPLE_SIDE]，丢掉细节只留色块
 *  2. 按色相每 15° 一桶（24 桶 + 1 个灰度桶），权重 = 1 + 饱和度×2 —— 彩色优先，
 *     否则大片灰底会把鲜艳的主体色压掉
 *  3. 取权重前 [maxColors] 桶，每桶算加权平均色
 *  4. 归一化亮度到 [MIN_BG_LUM, MAX_BG_LUM]，保证铺成背景后上方黑字白字都能读
 */
fun extractPalette(image: ImageBitmap?, maxColors: Int = 5): ExtractedPalette {
    if (image == null) return ExtractedPalette.Fallback
    val source = runCatching { image.asAndroidBitmap() }.getOrNull()
        ?: return ExtractedPalette.Fallback
    return runCatching { extractFromBitmap(source, maxColors) }.getOrDefault(ExtractedPalette.Fallback)
}

private const val SAMPLE_SIDE = 64
private const val HUE_BUCKETS = 24
private const val GRAY_BUCKET = HUE_BUCKETS
private const val MIN_BG_LUM = 0.66f
private const val MAX_BG_LUM = 0.90f

private class Bucket {
    var weight = 0f
    var r = 0f
    var g = 0f
    var b = 0f
    var satSum = 0f
    var satMax = 0f
    var n = 0
    fun add(c: Color, w: Float, s: Float) {
        weight += w
        r += c.red * w
        g += c.green * w
        b += c.blue * w
        satSum += s
        if (s > satMax) satMax = s
        n += 1
    }

    fun average(): Color? {
        if (weight <= 0f || n == 0) return null
        return Color(
            red = (r / weight).coerceIn(0f, 1f),
            green = (g / weight).coerceIn(0f, 1f),
            blue = (b / weight).coerceIn(0f, 1f)
        )
    }

    val avgSat: Float get() = if (n == 0) 0f else satSum / n
}

private fun extractFromBitmap(source: Bitmap, maxColors: Int): ExtractedPalette {
    val longest = maxOf(source.width, source.height)
    if (longest <= 0) return ExtractedPalette.Fallback
    val scale = SAMPLE_SIDE.toFloat() / longest
    val w = (source.width * scale).toInt().coerceIn(1, SAMPLE_SIDE)
    val h = (source.height * scale).toInt().coerceIn(1, SAMPLE_SIDE)
    val small = Bitmap.createScaledBitmap(source, w, h, true)

    val buckets = Array(HUE_BUCKETS + 1) { Bucket() }
    var total = 0
    var colorful = 0
    val pixels = IntArray(w * h)
    small.getPixels(pixels, 0, w, 0, 0, w, h)
    if (small !== source) small.recycle()

    for (argb in pixels) {
        val a = (argb ushr 24) and 0xFF
        if (a < 160) continue
        val c = Color(argb)
        val lum = c.luminance()
        // 纯黑压边、纯白高光都不代表画面主色；留着只会让渐变两端发脏
        if (lum < 0.045f || lum > 0.965f) continue
        total += 1
        val s = saturationOf(c)
        val idx = if (s < 0.08f) GRAY_BUCKET else ((hueOf(c) / (360f / HUE_BUCKETS)).toInt()).coerceIn(0, HUE_BUCKETS - 1)
        if (s >= 0.08f) colorful += 1
        buckets[idx].add(c, 1f + s * 2f, s)
    }
    if (total == 0) return ExtractedPalette.Fallback

    // 画面本来就偏灰白（雪景、文档、纯色壁纸）时，别硬凑出彩色：
    // 灰度桶的权重已经足够高，排序后自然会排在前面。
    val ranked = buckets.filter { it.weight > 0f }.sortedByDescending { it.weight }
    if (ranked.isEmpty()) return ExtractedPalette.Fallback

    val picked = ranked.take(maxColors.coerceAtLeast(2)).mapNotNull { it.average() }
    if (picked.isEmpty()) return ExtractedPalette.Fallback

    // 归一化亮度：作为背景的色阶要落在浅色区间，否则浅色主题下一整页发暗
    val normalized = picked.map { normalizeForBackground(it) }
    val dominant = normalized.first()
    val accentSource = ranked.maxByOrNull { it.satMax * 0.7f + it.avgSat * 0.3f }
    val accent = accentSource?.average()?.let { boostAccent(it, colorful * 4 >= total) }
        ?: DefaultHighlight

    return ExtractedPalette(
        colors = ensureSpread(normalized),
        dominant = dominant,
        accent = accent
    )
}

/** 把任意色拉到「可以当页面背景」的亮度带；色相与饱和度保留。 */
private fun normalizeForBackground(c: Color): Color {
    val lum = c.luminance()
    if (lum in MIN_BG_LUM..MAX_BG_LUM) return c
    val target = lum.coerceIn(MIN_BG_LUM, MAX_BG_LUM)
    // 线性地朝目标亮度推：暗色提亮靠加白，亮色压暗靠乘系数，避免直接截断导致色相偏移
    return if (lum < target) lightenColor(c, ((target - lum) / (1f - lum).coerceAtLeast(0.12f)).coerceIn(0f, 0.72f))
    else darkenColor(c, (target / lum.coerceAtLeast(0.05f)).coerceIn(0.72f, 1f))
}

/**
 * 强调色：饱和度提上来、明度压到中段，才能在上面放白字。
 *
 * 明度必须用 HSV 的 value（即最大分量），**不能**用 luminance：
 * 饱和蓝的 luminance 只有 0.1 上下，拿它当 value 会得到一片近黑的深蓝。
 */
private fun boostAccent(c: Color, allowColor: Boolean): Color {
    val h = hueOf(c)
    val s = if (allowColor) maxOf(saturationOf(c), 0.58f) else maxOf(saturationOf(c), 0.32f)
    val v = maxOf(c.red, c.green, c.blue).coerceIn(0.52f, 0.80f)
    return Color.hsv(h, s.coerceIn(0f, 0.92f), v)
}

/** 至少三个色，且彼此有差异；太少时用邻近色相补一段，渐变才不会有硬边。 */
private fun ensureSpread(colors: List<Color>): List<Color> {
    if (colors.size >= 3) return colors
    val base = colors.first()
    val h = hueOf(base)
    val s = saturationOf(base).coerceAtMost(0.42f)
    val extra = when (colors.size) {
        1 -> listOf(
            Color.hsv((h + 34f) % 360f, (s * 0.92f).coerceIn(0f, 1f), 0.94f),
            Color.hsv((h + 300f) % 360f, (s * 0.80f).coerceIn(0f, 1f), 0.96f)
        )
        else -> listOf(Color.hsv((h + 28f) % 360f, (s * 0.9f).coerceIn(0f, 1f), 0.95f))
    }
    return colors + extra
}

/**
 * 一组色阶里挑一个「适合当高亮」的：饱和度最高的那个，再按需要提亮。
 * 用在「跟随背景取色」的高亮色推导，以及主题预设的自动配色。
 */
fun accentOf(colors: List<Color>): Color {
    val source = colors.maxByOrNull { saturationOf(it) } ?: DefaultHighlight
    return boostAccent(source, true)
}

/** 按当前深浅模式把「背景色阶」翻转：深色模式要压到近黑，浅色模式提到近白。 */
fun paletteForMode(colors: List<Color>, dark: Boolean): List<Color> {
    if (colors.isEmpty()) return listOf(BgMint)
    return if (dark) colors.map { it.darkBlend(0.24f) } else colors.map { it.lightBlend(0.86f) }
}

/** 朝黑混合，[amount] 越大越黑（0 = 原色，1 = 纯黑） */
fun Color.darkBlend(amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(red * (1f - a), green * (1f - a), blue * (1f - a), alpha)
}

/** 朝白混合，[amount] 越大越白 */
fun Color.lightBlend(amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (1f - red) * a,
        green = green + (1f - green) * a,
        blue = blue + (1f - blue) * a,
        alpha = alpha
    )
}

// ------------------------------------------------------------------ 缓存

/**
 * 取色结果缓存。
 *
 * 一次提取要读 4096 个像素、算 4096 次 luminance，虽然不慢，但重组时反复算毫无意义；
 * 轮播换回上一张时也应该立刻拿到同一套颜色，否则整页色相会闪。
 * 只留 6 张，一张 ExtractedPalette 只有几十字节。
 */
private const val PALETTE_CACHE_MAX = 6
private val paletteCache = LinkedHashMap<String, ExtractedPalette>(8, 0.75f, true)

fun cachedPalette(path: String): ExtractedPalette? = synchronized(paletteCache) { paletteCache[path] }

fun putCachedPalette(path: String, palette: ExtractedPalette) {
    synchronized(paletteCache) {
        paletteCache[path] = palette
        while (paletteCache.size > PALETTE_CACHE_MAX) {
            val eldest = paletteCache.keys.firstOrNull() ?: break
            paletteCache.remove(eldest)
        }
    }
}

fun clearPaletteCache() {
    synchronized(paletteCache) { paletteCache.clear() }
}

/** 颜色 → "#RRGGBB"，给主题预设的持久化用；省得存一堆 int */
fun colorToHex(c: Color): String = "%06X".format(c.toArgb() and 0xFFFFFF)

/** 十六进制色；解析失败回退 [fallback] */
fun hexToColor(hex: String?, fallback: Color = DefaultHighlight): Color {
    if (hex.isNullOrBlank()) return fallback
    return runCatching {
        val clean = hex.removePrefix("#")
        val v = clean.toLong(16)
        Color((0xFF000000L or (v and 0xFFFFFFL)).toInt())
    }.getOrDefault(fallback)
}

/** 两个色是否"看起来一样"：用于判断用户有没有手动改过高亮色 */
fun similarColor(a: Color, b: Color, tolerance: Float = 0.06f): Boolean =
    abs(a.red - b.red) < tolerance && abs(a.green - b.green) < tolerance && abs(a.blue - b.blue) < tolerance
