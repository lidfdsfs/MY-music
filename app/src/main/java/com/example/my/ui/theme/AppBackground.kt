package com.example.my.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.my.ui.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** 背景填充方式：纯色/渐变，或用户相册里的图片。 */
enum class BgFill { COLOR, IMAGE }

/** 调色模式：单色、双色渐变、三色渐变。 */
enum class BgColorMode { SINGLE, DUAL, TRI }

/**
 * 背景的**样式**（色彩怎么铺开），与 [BgColorMode] 正交：
 * [BgColorMode] 决定用几种颜色，这里决定这些颜色以什么形态呈现。
 *
 * 对标 QQ 音乐 / Apple Music 的几种底纹语言：
 *  - [SOLID]   纯色，最克制，信息密度高的页面好用
 *  - [LINEAR]  线性渐变，传统的上下（左右）过渡
 *  - [MESH]    网格渐变（mesh gradient）：多个径向光斑交叠、缓慢漂移，
 *              这是 QQ 音乐播放页与首页 banner 的主视觉，也是「不像模板」的关键
 *  - [AURORA]  极光：斜向多段渐变 + 一道柔光带，比 mesh 更安静、更有方向感
 */
enum class BgStyle(val zh: String, val en: String) {
    SOLID("纯色", "Solid"),
    LINEAR("渐变", "Gradient"),
    MESH("网格光晕", "Mesh glow"),
    AURORA("极光", "Aurora")
}

/**
 * 图片背景的排版方式。
 *
 * [BLUR_BACKDROP] 是 QQ 音乐播放页那套「模糊铺底 + 清晰主体」的做法：
 * 同一张图先重度模糊铺满、再压一层暗色，最后把清晰图摆在中间，
 * 既有氛围又不牺牲主体细节，也不会让整页被一张照片的花纹搅乱。
 */
enum class BgLayout(val zh: String, val en: String) {
    FILL("铺满裁剪", "Fill"),
    BLUR_BACKDROP("模糊铺底", "Blurred backdrop"),
    CENTER("完整居中", "Fit center")
}

/**
 * 取色同步：让界面主色跟随当前背景（图）而不是写死。
 *
 * - [OFF]    用「高亮色」里手选的颜色
 * - [ACCENT] 只同步高亮色（按钮 / 选中态 / 进度条），背景色仍是自己的
 * - [FULL]   高亮色 + 背景色阶都取自图，等于整套配色跟着图走
 *
 * 只在图片模式下有意义；纯色/渐变模式下 [ACCENT]/[FULL] 都退化为 [OFF]。
 */
enum class ColorSync(val zh: String, val en: String) {
    OFF("手动", "Manual"),
    ACCENT("跟随高亮", "Accent"),
    FULL("整套跟随", "Full")
}

/**
 * 玻璃强度：控制所有卡片 / 浮层的底色不透明度。
 *
 * 下限被 [surfaceForCustomText] 钉死在 0.86 —— 再低就会透出底层文字，
 * 这是本项目反复踩过的坑（见 Color.kt 里 Layer/Ink 的注释），所以这里三档
 * 全部落在 0.86~1.0 的安全区间里，只是观感上「更透」或「更实」。
 */
enum class GlassLevel(val zh: String, val en: String, val alpha: Float) {
    CLEAR("通透", "Clear", 0.86f),
    STANDARD("标准", "Standard", 0.92f),
    FROST("磨砂", "Frosted", 0.97f)
}

/**
 * 多张背景图之间的切换动画。
 *
 * 只用 Compose 自带的 ContentTransform 实现，不额外引依赖：
 * 淡入淡出最稳；滑动方向感强；推近/拉远带呼吸感；展开像擦除。
 * [SMART] 不是一个具体效果，而是「每次随机挑一个」。
 */
enum class BgTransition(val zh: String, val en: String) {
    SMART("智能轮播", "Smart mix"),
    FADE("淡入淡出", "Fade"),
    SLIDE("横向滑动", "Slide"),
    SLIDE_UP("纵向滑动", "Slide up"),
    ZOOM("推近", "Zoom in"),
    ZOOM_OUT("拉远", "Zoom out"),
    WIPE("展开擦除", "Wipe");

    /** 智能轮播只是开关，不参与随机池 */
    val isSmart: Boolean get() = this == SMART

    companion object {
        /** 智能轮播的可选效果（不含 SMART 自己） */
        val effects: List<BgTransition> = entries.filterNot { it.isSmart }

        private var lastSmartPick: BgTransition? = null

        /** 随机挑一个，且不跟上一次重复——连着两次一样会显得像没换效果 */
        fun smartNext(): BgTransition {
            val pool = effects.filter { it != lastSmartPick }.ifEmpty { effects }
            val pick = pool.random()
            lastSmartPick = pick
            return pick
        }
    }
}

/** 背景图最多支持几张：再多解码内存与切换节奏都不划算 */
const val MAX_BACKGROUND_IMAGES = 20

/**
 * 全局文字字体档位。
 *
 * 全部用 Android 自带系统字体族，不打包字体文件：一方面安装包不会变大，
 * 另一方面中文回退由系统的 Noto Sans CJK 兜着，不会出现缺字方框。
 * 某一档在个别定制 ROM 上不存在时，[Typeface.create] 会静默返回默认字体，
 * 表现是「选了但看起来没变」，不会崩。
 *
 * 注意：[name] 会被写进 SharedPreferences（[KEY_FONT]），**已有档位的枚举名不能改**，
 * 否则老用户存的值解析不出来，会被 getOrDefault 打回 SYSTEM。
 */
enum class AppFont(val zh: String, val en: String, private val typefaceName: String?) {
    SYSTEM("系统默认", "System", null),
    SERIF("衬线", "Serif", "serif"),
    MONO("等宽", "Mono", "monospace"),
    CURSIVE("手写", "Cursive", "cursive"),
    /** 窄体：横向更省空间，适合信息密集的列表与顶栏 */
    CONDENSED("窄体", "Condensed", "sans-serif-condensed"),
    /** 细体：大标题、封面文案的轻盈感 */
    LIGHT("细体", "Light", "sans-serif-light"),
    /** 随性：偏手账/涂鸦的活泼字形 */
    CASUAL("随性", "Casual", "casual"),
    /** 衬线等宽：衬线骨架 + 等宽网格，统计数字与代码感文案 */
    SERIF_MONO("衬线等宽", "Serif mono", "serif-monospace");

    /**
     * 这一档在**当前设备**上是否真实存在。
     *
     * 国产定制 ROM 经常把系统字体族换掉，`casual` / `serif-monospace` 这类冷门 family 可能根本没有。
     * `Typeface.create` 遇到不存在的 family 不会报错，而是静默返回默认字体 ——
     * 表现就是「选了但看起来没变」。这里提前探测一次，让界面能如实标注，而不是让用户以为功能坏了。
     */
    val isAvailable: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (typefaceName == null) {
            true
        } else {
            runCatching { Typeface.create(typefaceName, Typeface.NORMAL) != Typeface.DEFAULT }
                .getOrDefault(false)
        }
    }

    /**
     * 解析后的 FontFamily，首次访问时创建并缓存。
     * Typeface 构造不便宜，不能每次重组/每段文字都重建一遍。
     */
    val family: FontFamily by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (typefaceName == null) {
            FontFamily.SansSerif
        } else {
            runCatching { FontFamily(Typeface.create(typefaceName, Typeface.NORMAL)) }
                .getOrElse { FontFamily.SansSerif }
        }
    }
}

/** 自定义背景的完整配置；所有字段都可持久化。 */
data class AppBackgroundConfig(
    val enabled: Boolean = false,
    val fill: BgFill = BgFill.COLOR,
    val mode: BgColorMode = BgColorMode.SINGLE,
    /** 色彩呈现形态（纯色 / 渐变 / 网格光晕 / 极光） */
    val style: BgStyle = BgStyle.LINEAR,
    /** 图片排版方式（铺满 / 模糊铺底 + 清晰主体 / 完整居中） */
    val layout: BgLayout = BgLayout.FILL,
    /** 取色同步档位 */
    val colorSync: ColorSync = ColorSync.OFF,
    val colors: List<Color> = listOf(BgMint),
    /**
     * 背景图列表（1–[MAX_BACKGROUND_IMAGES] 张）。
     * 之前只存一张，字段名 imagePath；现在换成列表，旧数据在 [AppBackground.load] 里迁移。
     */
    val imagePaths: List<String> = emptyList(),
    /** 图片不透明度，1f = 完全不透明。 */
    val imageAlpha: Float = 0.45f,
    /** 图片模糊半径（dp）。 */
    val blur: Float = 0f,
    /** 自动切换间隔（秒）；0 = 不自动切换，只显示第一张 */
    val intervalSec: Int = 12,
    /** 切换动画 */
    val transition: BgTransition = BgTransition.FADE,
    val highlight: Color = DefaultHighlight,
    val textColor: Color = DefaultTextColor,
    val font: AppFont = AppFont.SYSTEM,
    /** 暗角强度 0–100：四角压暗，把视线收到中间（照片背景尤其需要） */
    val vignette: Float = 0f,
    /** 颗粒噪点 0–100：给纯色/渐变加一点胶片质感，避免大面积色块出现色带 */
    val grain: Float = 0f,
    /** 玻璃强度档位，影响所有卡片与浮层的底色不透明度 */
    val glass: GlassLevel = GlassLevel.STANDARD,
    /** 光晕 / 极光是否缓慢流动；关掉可省电，也更安静 */
    val animate: Boolean = true,
    /** 当前套用的主题预设 id（[ThemePresets]），用户改过任何一项后置空 */
    val sceneId: String? = null
) {
    /** 兼容旧调用方：主图就是列表里的第一张 */
    val imagePath: String? get() = imagePaths.firstOrNull()

    /** 当前模式真正参与绘制的颜色。 */
    val activeColors: List<Color>
        get() {
            val safe = colors.ifEmpty { listOf(BgMint) }
            return when (mode) {
                BgColorMode.SINGLE -> listOf(safe.first())
                BgColorMode.DUAL -> safe.take(2).let { if (it.size == 1) listOf(it[0], it[0]) else it }
                BgColorMode.TRI -> safe.take(3).let { if (it.size == 3) it else it + safe.last() }
            }
        }

    /**
     * 实际生效的配色：开了取色同步且有提取结果时用图里的颜色。
     *
     * 只在 [BgFill.IMAGE] 下接管；纯色/渐变模式本来就是用户自己挑的色，
     * 被图覆盖会显得「我选的色没生效」。
     */
    fun resolvedColors(palette: ExtractedPalette?, dark: Boolean): List<Color> {
        if (fill != BgFill.IMAGE || colorSync == ColorSync.OFF) return activeColors
        val source = palette?.takeIf { it.colors.isNotEmpty() }?.colors ?: return activeColors
        val tuned = paletteForMode(source, dark)
        return when (mode) {
            BgColorMode.SINGLE -> listOf(tuned.first())
            BgColorMode.DUAL -> tuned.take(2).let { if (it.size == 1) listOf(it[0], it[0]) else it }
            BgColorMode.TRI -> tuned.take(3).let { if (it.size == 3) it else it + tuned.last() }
        }
    }

    /** 实际生效的高亮色：取色同步开启时跟随图片主色 */
    fun resolvedHighlight(palette: ExtractedPalette?): Color {
        if (colorSync == ColorSync.OFF || palette == null) return highlight
        return palette.accent
    }

    companion object {
        val Default = AppBackgroundConfig()
    }
}

/** 预设色：浅薄荷、浅海盐、浅樱粉、暖桃渐变。 */
val BgMint = Color(0xFFEAF7F1)
val BgSky = Color(0xFFEAF4FA)
val BgBlush = Color(0xFFFBEFF3)
val BgPeach1 = Color(0xFFF8E7D8)
val BgPeach2 = Color(0xFFFBEFF3)
val DefaultHighlight = Color(0xFFE07A5F)
val DefaultTextColor = Color(0xFF1F2328)

/**
 * 自定义背景照片上的遮罩。
 *
 * 旧实现把照片按「透明度」画在一层白底上，等于给整页蒙了一层白纱：
 * 背景越透越白，白色文字反而被冲淡。现在改成照片画满、上面压一层遮罩，
 * 越透越接近遮罩色。遮罩跟随系统深浅：深色用黑灰，浅色用中性灰（不再是刺眼的纯白）。
 */
val BgScrim = Color(0xFF15161A)
val BgScrimLight = Color(0xFFE9E6E2)

fun customScrimColor(dark: Boolean): Color = if (dark) BgScrim else BgScrimLight

/**
 * 模糊滑条值 → 实际模糊半径（dp）。
 *
 * 旧实现 1:1 映射，值调到 2 就已经明显糊了；高密度屏上更是整页发虚。
 * 统一按 0.3 折算（滑条 0–50 → 0–15dp），低档位只做轻微柔化，拖到中段才是毛玻璃。
 */
fun bgBlurDp(value: Float): Dp = (value.coerceIn(0f, 50f) * 0.3f).dp

/**
 * 自定义背景生效时的文字色。
 *
 * 用户没动过默认值（深墨色）时跟随系统深浅：深色模式自动转浅色墨，
 * 否则深色玻璃卡 + 深色文字会直接看不见。
 */
fun customInk(cfg: AppBackgroundConfig, dark: Boolean): Color =
    if (cfg.textColor == DefaultTextColor && dark) Color(0xFFF3F1EE) else cfg.textColor

data class BgPreset(
    val zh: String,
    val en: String,
    val colors: List<Color>,
    val mode: BgColorMode
)

val BackgroundPresets: List<BgPreset> = listOf(
    BgPreset("薄荷", "Mint", listOf(BgMint), BgColorMode.SINGLE),
    BgPreset("海盐", "Sea salt", listOf(BgSky), BgColorMode.SINGLE),
    BgPreset("樱粉", "Blush", listOf(BgBlush), BgColorMode.SINGLE),
    BgPreset("暖桃", "Peach", listOf(BgPeach1, BgPeach2), BgColorMode.DUAL)
)

/** 主题色板里的“真实底色”，不受自定义背景的透明化影响。 */
val AppBaseBackground = mutableStateOf(LightPalette.background)

private const val PREFS = "app_background"
private const val KEY_ENABLED = "enabled"
private const val KEY_FILL = "fill"
private const val KEY_MODE = "mode"
private const val KEY_STYLE = "bg_style"
private const val KEY_LAYOUT = "bg_layout"
private const val KEY_COLOR_SYNC = "color_sync"
private const val KEY_COLORS = "colors"
private const val KEY_IMAGE = "image_path"
private const val KEY_IMAGES = "image_paths"
private const val KEY_INTERVAL = "image_interval"
private const val KEY_TRANSITION = "image_transition"
private const val KEY_ALPHA = "image_alpha"
private const val KEY_BLUR = "blur"
private const val KEY_HIGHLIGHT = "highlight"
private const val KEY_TEXT = "text_color"
private const val KEY_FONT = "font"
private const val KEY_VIGNETTE = "vignette"
private const val KEY_GRAIN = "grain"
private const val KEY_GLASS = "glass_level"
private const val KEY_ANIMATE = "bg_animate"
private const val KEY_SCENE = "scene_id"
private const val BG_DIR = "app_background"

/**
 * 全 App 自定义背景。
 *
 * 只在 [AppRoot] 的最底层画一张背景；各页面的 `Bg.Background` 在启用时被主题置为透明，
 * 于是列表、卡片、浮层仍然保持自己的表面，背景图从它们之间透出来。
 */
object AppBackground {
    var config by mutableStateOf(AppBackgroundConfig.Default)
        private set

    /**
     * 当前背景图的提取配色。
     *
     * 由 [AppBackgroundLayer] 在解码完图片后算好写入，**不持久化** ——
     * 它完全可由图片路径重建，存下来只会在换图 / 删图后留下过期色。
     * 主题层读它来决定「跟随取色」时的高亮色与背景色阶。
     */
    var coverPalette by mutableStateOf<ExtractedPalette?>(null)
        private set

    fun updateCoverPalette(palette: ExtractedPalette?) {
        if (coverPalette != palette) coverPalette = palette
    }

    /** 当前内存中的背景配置，供云端快照读取 */
    fun snapshot(): AppBackgroundConfig = config

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaults = AppBackgroundConfig.Default
        config = AppBackgroundConfig(
            enabled = p.getBoolean(KEY_ENABLED, false),
            fill = runCatching { BgFill.valueOf(p.getString(KEY_FILL, BgFill.COLOR.name)!!) }
                .getOrDefault(BgFill.COLOR),
            mode = runCatching { BgColorMode.valueOf(p.getString(KEY_MODE, BgColorMode.SINGLE.name)!!) }
                .getOrDefault(BgColorMode.SINGLE),
            style = runCatching { BgStyle.valueOf(p.getString(KEY_STYLE, BgStyle.LINEAR.name)!!) }
                .getOrDefault(BgStyle.LINEAR),
            layout = runCatching { BgLayout.valueOf(p.getString(KEY_LAYOUT, BgLayout.FILL.name)!!) }
                .getOrDefault(BgLayout.FILL),
            colorSync = runCatching {
                ColorSync.valueOf(p.getString(KEY_COLOR_SYNC, ColorSync.OFF.name)!!)
            }.getOrDefault(ColorSync.OFF),
            colors = decodeColors(p.getString(KEY_COLORS, null)) ?: defaults.colors,
            // 旧版本只存一张（KEY_IMAGE）；升级后读列表，读不到就用旧值兜一张
            imagePaths = (
                decodePaths(p.getString(KEY_IMAGES, null))
                    ?: listOfNotNull(p.getString(KEY_IMAGE, null))
                ).filter { it.isNotBlank() }.take(MAX_BACKGROUND_IMAGES),
            imageAlpha = p.getFloat(KEY_ALPHA, defaults.imageAlpha).coerceIn(0f, 1f),
            blur = p.getFloat(KEY_BLUR, defaults.blur).coerceIn(0f, 50f),
            intervalSec = p.getInt(KEY_INTERVAL, defaults.intervalSec).coerceIn(0, 300),
            transition = runCatching {
                BgTransition.valueOf(p.getString(KEY_TRANSITION, BgTransition.FADE.name)!!)
            }.getOrDefault(BgTransition.FADE),
            highlight = Color(p.getInt(KEY_HIGHLIGHT, DefaultHighlight.toArgb())),
            textColor = Color(p.getInt(KEY_TEXT, DefaultTextColor.toArgb())),
            font = runCatching { AppFont.valueOf(p.getString(KEY_FONT, AppFont.SYSTEM.name)!!) }
                .getOrDefault(AppFont.SYSTEM),
            vignette = p.getFloat(KEY_VIGNETTE, defaults.vignette).coerceIn(0f, 100f),
            grain = p.getFloat(KEY_GRAIN, defaults.grain).coerceIn(0f, 100f),
            glass = runCatching {
                GlassLevel.valueOf(p.getString(KEY_GLASS, GlassLevel.STANDARD.name)!!)
            }.getOrDefault(GlassLevel.STANDARD),
            animate = p.getBoolean(KEY_ANIMATE, true),
            sceneId = p.getString(KEY_SCENE, null)
        )
    }

    /** 应用一版草稿，并持久化。 */
    fun apply(context: Context, draft: AppBackgroundConfig) {
        val oldPaths = config.imagePaths
        val next = draft.copy(enabled = true)
        config = next
        // 配置换了，之前算出来的取色结果就不一定还属于当前这张图
        if (oldPaths != next.imagePaths) coverPalette = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putBoolean(KEY_ENABLED, true)
            putString(KEY_FILL, next.fill.name)
            putString(KEY_MODE, next.mode.name)
            putString(KEY_STYLE, next.style.name)
            putString(KEY_LAYOUT, next.layout.name)
            putString(KEY_COLOR_SYNC, next.colorSync.name)
            putString(KEY_COLORS, encodeColors(next.colors))
            putString(KEY_IMAGES, encodePaths(next.imagePaths))
            putString(KEY_IMAGE, next.imagePath)
            putFloat(KEY_ALPHA, next.imageAlpha)
            putFloat(KEY_BLUR, next.blur)
            putInt(KEY_INTERVAL, next.intervalSec)
            putString(KEY_TRANSITION, next.transition.name)
            putInt(KEY_HIGHLIGHT, next.highlight.toArgb())
            putInt(KEY_TEXT, next.textColor.toArgb())
            putString(KEY_FONT, next.font.name)
            putFloat(KEY_VIGNETTE, next.vignette)
            putFloat(KEY_GRAIN, next.grain)
            putString(KEY_GLASS, next.glass.name)
            putBoolean(KEY_ANIMATE, next.animate)
            putString(KEY_SCENE, next.sceneId)
        }.apply()
        // 换掉的图要清掉，否则私有目录会越攒越多
        oldPaths.filterNot { next.imagePaths.contains(it) }.forEach { deleteManagedFile(context, it) }
        if (oldPaths != next.imagePaths) {
            clearBackgroundBitmapCache()
            clearPaletteCache()
        }
    }

    /** 恢复默认暖米白；同时清掉之前复制到私有目录的图片。 */
    fun reset(context: Context) {
        val oldPaths = config.imagePaths
        config = AppBackgroundConfig.Default
        coverPalette = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putBoolean(KEY_ENABLED, false)
            putString(KEY_FILL, BgFill.COLOR.name)
            putString(KEY_MODE, BgColorMode.SINGLE.name)
            putString(KEY_STYLE, BgStyle.LINEAR.name)
            putString(KEY_LAYOUT, BgLayout.FILL.name)
            putString(KEY_COLOR_SYNC, ColorSync.OFF.name)
            putString(KEY_COLORS, encodeColors(AppBackgroundConfig.Default.colors))
            remove(KEY_IMAGE)
            remove(KEY_IMAGES)
            remove(KEY_INTERVAL)
            remove(KEY_TRANSITION)
            putFloat(KEY_ALPHA, AppBackgroundConfig.Default.imageAlpha)
            putFloat(KEY_BLUR, 0f)
            putInt(KEY_HIGHLIGHT, DefaultHighlight.toArgb())
            putInt(KEY_TEXT, DefaultTextColor.toArgb())
            putString(KEY_FONT, AppFont.SYSTEM.name)
            putFloat(KEY_VIGNETTE, 0f)
            putFloat(KEY_GRAIN, 0f)
            putString(KEY_GLASS, GlassLevel.STANDARD.name)
            putBoolean(KEY_ANIMATE, true)
            remove(KEY_SCENE)
        }.apply()
        oldPaths.forEach { deleteManagedFile(context, it) }
        clearBackgroundBitmapCache()
        clearPaletteCache()
    }

    /** 把相册图片复制到应用私有目录，避免来源 Uri 失效。 */
    fun importImage(context: Context, uri: Uri): String? {
        return runCatching {
            val dir = File(context.filesDir, BG_DIR).apply { mkdirs() }
            // 多选是一次循环导入，不能只用时间戳命名：同一毫秒内会互相覆盖
            val file = File(dir, "custom_bg_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}.img")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            file.absolutePath
        }.getOrNull()
    }

    /**
     * 放弃一组尚未应用的草稿图，避免取消/换图后留下孤儿文件。
     * 只有「当前正在用的图」才保留。
     */
    fun discardImages(context: Context, paths: List<String>) {
        val keep = config.imagePaths
        paths.filterNot { keep.contains(it) }.forEach { deleteManagedFile(context, it) }
    }

    fun summary(): String {
        if (!config.enabled) return tr("未开启 · 暖米白", "Off · Warm ivory")
        val sync = if (config.fill == BgFill.IMAGE && config.colorSync != ColorSync.OFF) {
            tr(" · 取色跟随", " · colors from photo")
        } else {
            ""
        }
        if (config.fill == BgFill.IMAGE) {
            val n = config.imagePaths.size
            val rotate = if (n >= 2 && config.intervalSec > 0) {
                tr(" · ${n} 张 · ${config.intervalSec}s 轮播", " · $n photos · every ${config.intervalSec}s")
            } else if (n >= 2) {
                tr(" · ${n} 张 · 不轮播", " · $n photos · static")
            } else {
                ""
            }
            val layout = when (config.layout) {
                BgLayout.FILL -> tr("铺满", "Fill")
                BgLayout.BLUR_BACKDROP -> tr("模糊铺底", "Blurred backdrop")
                BgLayout.CENTER -> tr("完整居中", "Fit center")
            }
            return tr(
                "图片 · $layout · 不透明度 ${(config.imageAlpha * 100).toInt()}%$rotate$sync",
                "Photo · $layout · ${(config.imageAlpha * 100).toInt()}% opacity$rotate$sync"
            )
        }
        val base = when (config.mode) {
            BgColorMode.SINGLE -> tr("单色", "Single color")
            BgColorMode.DUAL -> tr("双色渐变", "Two-color gradient")
            BgColorMode.TRI -> tr("三色渐变", "Three-color gradient")
        }
        return tr(
            "$base · ${config.style.zh}",
            "$base · ${config.style.en}"
        )
    }
}

/**
 * 渐变至少需要两段颜色；单色（或空）时复制一份兜底。
 * 否则 Compose 在创建 LinearGradientShader 时抛
 * "colors must have length of at least 2 if colorStops is omitted"。
 */
fun gradientColorList(colors: List<Color>): List<Color> {
    val base = colors.ifEmpty { listOf(BgMint) }
    return if (base.size >= 2) base else listOf(base.first(), base.first())
}

/** 单色背景也安全：渐变至少需要两段，单色时复制一份即可。 */
fun backgroundBrush(colors: List<Color>): Brush =
    Brush.verticalGradient(gradientColorList(colors))

/** 根据色相/饱和度生成当前模式的背景色；供设置页实时预览。 */
fun colorsForBackground(hue: Float, saturation: Float, mode: BgColorMode): List<Color> {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    return when (mode) {
        BgColorMode.SINGLE -> listOf(Color.hsv(h, s, 0.96f))
        BgColorMode.DUAL -> listOf(
            Color.hsv(h, s, 0.97f),
            Color.hsv((h + 42f) % 360f, (s * 0.86f).coerceIn(0f, 1f), 0.96f)
        )
        BgColorMode.TRI -> listOf(
            Color.hsv(h, s, 0.97f),
            Color.hsv((h + 40f) % 360f, (s * 0.88f).coerceIn(0f, 1f), 0.95f),
            Color.hsv((h + 82f) % 360f, (s * 0.78f).coerceIn(0f, 1f), 0.94f)
        )
    }
}

/** 取色相，供“调色”面板的滑条定位。 */
fun hueOf(color: Color): Float {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    if (delta <= 0.00001f) return 0f
    val h = when (max) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return (h + 360f) % 360f
}

/** 取饱和度，供“调色”面板的滑条定位。 */
fun saturationOf(color: Color): Float {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    return if (max <= 0.00001f) 0f else ((max - min) / max).coerceIn(0f, 1f)
}

/**
 * 自定义文字色时的表面色：液态玻璃底色。
 *
 * 对比度下限（勿调回 0x59 / 0x4D 那种 30% 左右的透明度）：
 * 旧值让底下的网页正文、课表网格、封面文字直接穿透卡片，与卡片内文字叠在一起。
 * 现在中心不透明度 ≥ 0.86（[GlassLevel.CLEAR] 就是下限档），背景只以「模糊色块」形式参与
 * （见 LiquidGlassSurface），玻璃质感由折射环 / 色散 / 镜面高光提供，而不是靠把内容放进来。
 *
 * [level] 是用户在「主题中心」选的玻璃强度，三档全部落在安全区间内。
 */
fun surfaceForCustomText(text: Color, level: GlassLevel = GlassLevel.STANDARD): Color {
    val rgb = if (text.luminance() > 0.55f) Color(0xFF1D1916) else Color(0xFFFFFFFF)
    return rgb.copy(alpha = level.alpha)
}

fun variantForCustomText(text: Color, level: GlassLevel = GlassLevel.STANDARD): Color {
    val rgb = if (text.luminance() > 0.55f) Color(0xFF26211D) else Color(0xFFFFFFFF)
    // 次级容器比主表面再透一档，但绝不掉到 0.74 以下：输入框底一旦太透，
    // 里面的占位文字会和背后的列表项叠在一起
    return rgb.copy(alpha = (level.alpha - 0.12f).coerceAtLeast(0.74f))
}

fun dimForCustomText(text: Color, level: GlassLevel = GlassLevel.STANDARD): Color {
    val rgb = if (text.luminance() > 0.55f) Color(0xFF2A2420) else Color(0xFFFFFFFF)
    return rgb.copy(alpha = (level.alpha - 0.06f).coerceAtLeast(0.78f))
}

fun glassForCustomText(text: Color, level: GlassLevel = GlassLevel.STANDARD): Color {
    val rgb = if (text.luminance() > 0.55f) Color(0xFF1D1916) else Color(0xFFFFFFFF)
    return rgb.copy(alpha = (level.alpha + 0.04f).coerceAtMost(1f))
}

/** 当前字体选择对应的 Compose 字体族（带缓存，见 [AppFont.family]）。 */
fun fontFamilyFor(font: AppFont): FontFamily = font.family

fun outlineForCustomText(text: Color): Color = text.copy(alpha = 0.14f)

fun strongOutlineForCustomText(text: Color): Color = text.copy(alpha = 0.24f)

fun darkenColor(color: Color, factor: Float = 0.82f): Color = Color(
    red = (color.red * factor).coerceIn(0f, 1f),
    green = (color.green * factor).coerceIn(0f, 1f),
    blue = (color.blue * factor).coerceIn(0f, 1f),
    alpha = color.alpha
)

fun lightenColor(color: Color, factor: Float = 0.24f): Color = Color(
    red = color.red + (1f - color.red) * factor,
    green = color.green + (1f - color.green) * factor,
    blue = color.blue + (1f - color.blue) * factor,
    alpha = color.alpha
)

/** 网格 / 极光一个完整漂移周期的时长：够慢才像「呼吸」，用户不会注意到它在动 */
private const val BG_CYCLE_MS = 34_000

/**
 * 根容器最底层：先铺真实底色，再叠加图片或色彩背景，最后统一压质感层。
 *
 * 绘制分三段，顺序不能换：
 *  1. 不透明底色（[AppBaseBackground]）—— 保证任何情况下不会有透明区域露出系统壁纸
 *  2. 背景主体（图片 / 纯色 / 渐变 / 网格光晕 / 极光）
 *  3. 质感层（暗角 + 颗粒），对上面两种主体一视同仁
 *
 * 动效的相位用 [Animatable] 持有、**只在 draw 阶段读**：
 * 若在组合阶段读 `phase.value`，每帧都会重组整棵背景树；
 * 在 `drawBehind` 里读则只触发重绘（deferred read），这是全屏动画唯一可接受的做法。
 */
@Composable
fun AppBackgroundLayer() {
    val cfg = AppBackground.config
    if (!cfg.enabled) return
    val dark = AppBaseBackground.value.luminance() < 0.45f
    val paths = remember(cfg.imagePaths) { cfg.imagePaths.take(MAX_BACKGROUND_IMAGES) }
    val useImage = cfg.fill == BgFill.IMAGE && paths.isNotEmpty()
    var index by remember(paths) { mutableIntStateOf(0) }

    LaunchedEffect(paths, cfg.intervalSec, useImage) {
        if (!useImage || paths.size < 2 || cfg.intervalSec <= 0) {
            index = 0
            return@LaunchedEffect
        }
        while (true) {
            delay(cfg.intervalSec * 1000L)
            val next = (index + 1) % paths.size
            // 先把下一张解码好（命中缓存就是瞬间），再切过去，
            // 否则切换那一刻会先露出空底，看着像闪一下
            withContext(Dispatchers.IO) {
                runCatching { decodeBackgroundImageCached(paths[next]) }
            }
            index = next
        }
    }

    val currentPath = if (useImage) paths.getOrNull(index % paths.size.coerceAtLeast(1)) else null
    val bitmap = rememberBackgroundBitmap(currentPath)

    // 取色：图片解码完再算，结果推给全局，供主题层「跟随取色」使用。
    // 只在有图时算，且优先走缓存 —— 轮播换回上一张要立刻拿到同一套色，不能抖。
    LaunchedEffect(currentPath, bitmap) {
        if (currentPath == null || bitmap == null) {
            AppBackground.updateCoverPalette(null)
            return@LaunchedEffect
        }
        val cached = cachedPalette(currentPath)
        if (cached != null) {
            AppBackground.updateCoverPalette(cached)
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.Default) { extractPalette(bitmap) }
        putCachedPalette(currentPath, extracted)
        AppBackground.updateCoverPalette(extracted)
    }

    val palette = AppBackground.coverPalette
    val colors = cfg.resolvedColors(palette, dark)

    val phase = remember { Animatable(0f) }
    LaunchedEffect(cfg.animate, cfg.style, useImage) {
        val moving = cfg.animate && !useImage &&
            (cfg.style == BgStyle.MESH || cfg.style == BgStyle.AURORA)
        if (!moving) {
            phase.snapTo(0f)
            return@LaunchedEffect
        }
        phase.snapTo(0f)
        phase.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(BG_CYCLE_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    Box(Modifier.fillMaxSize().background(AppBaseBackground.value)) {
        if (useImage) {
            if (paths.size < 2 || cfg.intervalSec <= 0) {
                BackgroundPhoto(bitmap, cfg, colors)
            } else {
                AnimatedContent(
                    targetState = index,
                    transitionSpec = { backgroundTransition(cfg.transition) },
                    label = "bg-cycle",
                    modifier = Modifier.fillMaxSize()
                ) { i ->
                    BackgroundPhoto(rememberBackgroundBitmap(paths.getOrNull(i)), cfg, colors)
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        when (cfg.style) {
                            BgStyle.SOLID -> drawRect(colors.first())
                            BgStyle.LINEAR -> drawRect(Brush.verticalGradient(gradientColorList(colors)))
                            BgStyle.MESH -> drawMeshGlow(colors, phase.value)
                            BgStyle.AURORA -> drawAurora(colors, phase.value)
                        }
                    }
            )
        }
        BackgroundTexture(vignette = cfg.vignette, grain = cfg.grain)
    }
}

/**
 * 网格光晕：底部铺主色，上面叠 4 个缓慢游走的径向光斑。
 *
 * 这是 QQ 音乐播放页 / 首页 banner 的底纹语言——没有一条硬边，
 * 颜色之间柔和过渡，而且**一直在极慢地动**，静态截图看不出，用起来有「活」的感觉。
 * 光斑轨迹用不同频率的正余弦错开，避免四个球同步绕圈露出机械感。
 */
private fun DrawScope.drawMeshGlow(colors: List<Color>, phase: Float) {
    drawRect(colors.first())
    val twoPi = (PI * 2).toFloat()
    val t = phase * twoPi
    for (i in 0 until 4) {
        val seed = i * 1.73f
        val cx = size.width * (0.5f + 0.38f * cos(t * (0.80f + i * 0.07f) + seed))
        val cy = size.height * (0.5f + 0.32f * sin(t * (0.62f + i * 0.09f) + seed * 1.31f))
        val radius = size.minDimension * (0.66f + 0.13f * (i % 2))
        val tint = colors[(i + 1) % colors.size]
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(tint.copy(alpha = 0.94f), tint.copy(alpha = 0f)),
                center = Offset(cx, cy),
                radius = radius
            )
        )
    }
    // 顶部一道极淡白光：模拟环境光源，避免整页颜色"平"在一个亮度上
    drawRect(
        Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
            startY = 0f,
            endY = size.height * 0.42f
        )
    )
}

/**
 * 极光：一条斜向的主渐变 + 一道来回扫的柔光带。
 *
 * 比 [drawMeshGlow] 安静、有方向感，适合放在信息密集的页面（资讯 / 课表）底下，
 * 不会像光斑那样抢视线。柔光带左右摆动的幅度刻意压在 22% 以内，
 * 摆太大就成了"进度条扫光"，完全不是这个气质。
 */
private fun DrawScope.drawAurora(colors: List<Color>, phase: Float) {
    val angle = (phase * 2 * PI).toFloat()
    val wide = colors.take(3).let { if (it.size >= 3) it else it + colors.last() }
    drawRect(
        Brush.linearGradient(
            colors = wide,
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        )
    )
    val shift = 0.5f + 0.22f * sin(angle)
    val band = colors.lastOrNull() ?: colors.first()
    drawRect(
        Brush.linearGradient(
            colors = listOf(Color.Transparent, band.copy(alpha = 0.60f), Color.Transparent),
            start = Offset(size.width * (shift - 0.45f), -size.height * 0.25f),
            end = Offset(size.width * (shift + 0.55f), size.height * 1.25f)
        )
    )
    drawRect(
        Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
            startY = 0f,
            endY = size.height * 0.36f
        )
    )
}

/**
 * 质感层：暗角 + 颗粒。
 *
 * 暗角把视线收到画面中部，照片背景没有它总像「贴纸」；
 * 颗粒则用来消除大面积渐变的色带（banding）—— 低价位屏幕上看纯渐变尤其明显。
 * 颗粒是一张 160px 的噪声贴图平铺，不是每帧画上千个圆点：
 * `ImageShader` + `TileMode.Repeated` 全交给 GPU，几乎零成本。
 */
@Composable
private fun BackgroundTexture(vignette: Float, grain: Float) {
    if (vignette <= 0f && grain <= 0f) return
    val noise = if (grain > 0f) remember { buildNoiseTile() } else null
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                if (vignette > 0f) {
                    val edge = (vignette / 100f).coerceIn(0f, 1f) * 0.74f
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = edge)),
                            center = center,
                            radius = size.maxDimension * 0.74f
                        )
                    )
                }
                if (noise != null && grain > 0f) {
                    drawRect(
                        brush = ShaderBrush(
                            ImageShader(noise, TileMode.Repeated, TileMode.Repeated)
                        ),
                        alpha = (grain / 100f).coerceIn(0f, 1f) * 0.62f
                    )
                }
            }
    )
}

/** 160×160 的随机噪声贴图（黑白各半、alpha 很低），用于平铺出胶片颗粒 */
private fun buildNoiseTile(side: Int = 160): ImageBitmap {
    val rnd = java.util.Random(20261115L)
    val pixels = IntArray(side * side)
    for (i in pixels.indices) {
        val bright = rnd.nextInt(256) > 128
        val alpha = if (bright) rnd.nextInt(30) else rnd.nextInt(24)
        pixels[i] = (alpha shl 24) or (if (bright) 0xFFFFFF else 0x000000)
    }
    return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * 一张背景图的实际绘制，按 [BgLayout] 分三种排版。
 *
 * 透明度一律由**遮罩**控制、照片本身恒定不透明：
 * 旧实现把照片按 alpha 画在白底上，等于给整页蒙一层白纱，越透越白，
 * 白字反而被冲淡。现在照片画满，上面压跟随主题深浅的遮罩，越透越接近遮罩色。
 */
@Composable
private fun BackgroundPhoto(bitmap: ImageBitmap?, cfg: AppBackgroundConfig, colors: List<Color>) {
    if (bitmap == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val scrim = customScrimColor(AppBaseBackground.value.luminance() < 0.45f).copy(alpha = customScrimAlpha(cfg))
    when (cfg.layout) {
        BgLayout.FILL -> Box(Modifier.fillMaxSize()) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 1f
                        scaleX = 1.08f
                        scaleY = 1.08f
                    }
                    .blur(bgBlurDp(cfg.blur), edgeTreatment = BlurredEdgeTreatment.Unbounded),
                contentScale = ContentScale.Crop,
                filterQuality = ImageSamplingHigh
            )
            Box(Modifier.fillMaxSize().background(scrim))
        }

        // QQ 音乐播放页那套：底下是一张重度模糊的同一张图，中间摆清晰主体。
        // 模糊层负责氛围，清晰层负责"可辨认"，两者叠在一起比单纯铺满更有层次。
        BgLayout.BLUR_BACKDROP -> Box(Modifier.fillMaxSize()) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.32f
                        scaleY = 1.32f
                    }
                    .blur(42.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                contentScale = ContentScale.Crop,
                filterQuality = ImageSamplingHigh
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(customScrimColor(true).copy(alpha = 0.42f + cfg.imageAlpha * 0.12f))
            )
            val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.86f)
                    .aspectRatio(ratio.coerceIn(0.55f, 2.2f))
                    .clip(RoundedCornerShape(26.dp))
                    .blur(bgBlurDp(cfg.blur) * 0.35f, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .graphicsLayer { alpha = 0.97f },
                contentScale = ContentScale.Crop
            )
        }

        // 完整居中：不裁切，四边用配色渐变补上，等于给照片留了一圈"画框"
        BgLayout.CENTER -> Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(gradientColorList(colors))))
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(bgBlurDp(cfg.blur), edgeTreatment = BlurredEdgeTreatment.Unbounded),
                contentScale = ContentScale.Fit,
                filterQuality = ImageSamplingHigh
            )
            Box(Modifier.fillMaxSize().background(scrim))
        }
    }
}

/** 遮罩不透明度：随「透明度」滑条走，最低保底 8% 压暗，保证浅色照片上的白字也有轮廓 */
fun customScrimAlpha(cfg: AppBackgroundConfig): Float =
    (1f - cfg.imageAlpha).coerceIn(0f, 1f) * 0.92f + 0.08f

/** 切换动画：所有效果都从 Compose 自带动画拼出来，不引额外依赖。 */
private fun backgroundTransition(t: BgTransition): ContentTransform {
    val dur = 900
    // 智能轮播：每一次切换现场抽一个，抽完不留重复
    if (t.isSmart) return backgroundTransition(BgTransition.smartNext())
    return when (t) {
        BgTransition.SMART ->
            fadeIn(tween(dur)) togetherWith fadeOut(tween(dur))
        BgTransition.FADE ->
            fadeIn(tween(dur)) togetherWith fadeOut(tween(dur))
        BgTransition.SLIDE ->
            (slideInHorizontally(tween(dur)) { it } + fadeIn(tween(dur))) togetherWith
                (slideOutHorizontally(tween(dur)) { -it } + fadeOut(tween(dur)))
        BgTransition.SLIDE_UP ->
            (slideInVertically(tween(dur)) { it } + fadeIn(tween(dur))) togetherWith
                (slideOutVertically(tween(dur)) { -it } + fadeOut(tween(dur)))
        BgTransition.ZOOM ->
            (scaleIn(tween(dur), initialScale = 1.16f) + fadeIn(tween(dur))) togetherWith
                (scaleOut(tween(dur), targetScale = 0.92f) + fadeOut(tween(dur)))
        BgTransition.ZOOM_OUT ->
            (scaleIn(tween(dur), initialScale = 0.82f) + fadeIn(tween(dur))) togetherWith
                (scaleOut(tween(dur), targetScale = 1.18f) + fadeOut(tween(dur)))
        BgTransition.WIPE ->
            (expandIn(tween(dur), expandFrom = Alignment.BottomEnd) + fadeIn(tween(dur))) togetherWith
                (shrinkOut(tween(dur), shrinkTowards = Alignment.TopStart) + fadeOut(tween(dur)))
    }
}

/** 异步解码本地背景图；路径不变时不会重复解码。 */
@Composable
fun rememberBackgroundBitmap(path: String?): ImageBitmap? {
    val context = LocalContext.current
    val state = produceState<ImageBitmap?>(initialValue = null, path, context) {
        value = if (path.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            decodeBackgroundImageCached(path)
        }
    }
    return state.value
}

/**
 * 已解码背景图的小缓存。
 *
 * 轮播时每张图会反复回来，每次都重新解码 1800px 的 JPEG 既慢又费电，
 * 并且「先解码再切」的预加载也需要这个缓存才能在切换瞬间命中。
 * 只留 3 张，按 1800px 采样算一张约 10MB 左右，再大就占内存了。
 */
private val backgroundBitmapCache = object : LruCache<String, ImageBitmap>(3) {}

fun decodeBackgroundImageCached(path: String): ImageBitmap? {
    backgroundBitmapCache.get(path)?.let { return it }
    val bitmap = decodeBackgroundImage(path) ?: return null
    backgroundBitmapCache.put(path, bitmap)
    return bitmap
}

/** 列表里的小缩略图：只解到 260px，20 张也不会把内存吃满 */
@Composable
fun rememberThumbBitmap(path: String?): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            decodeBackgroundImage(path, maxPx = 260)
        }
    }
    return state.value
}

/** 图片列表被清空或换了整套时，把旧缓存丢掉，避免内存里留着已删除文件的位图 */
fun clearBackgroundBitmapCache() {
    backgroundBitmapCache.evictAll()
}

fun decodeBackgroundImage(path: String, maxPx: Int = 1800): ImageBitmap? {
    val file = File(path)
    if (!file.isFile) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val longest = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / sample > maxPx) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = BitmapFactory.decodeFile(path, opts) ?: return null
        val orientation = runCatching { ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        ) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (rotated !== bmp) bmp.recycle()
            bmp = rotated
        }
        bmp.asImageBitmap()
    }.getOrNull()
}

private fun encodeColors(colors: List<Color>): String =
    colors.joinToString(";") { it.toArgb().toString() }

private fun encodePaths(paths: List<String>): String =
    paths.filter { it.isNotBlank() }.joinToString("\n")

private fun decodePaths(raw: String?): List<String>? {
    if (raw.isNullOrBlank()) return null
    val parsed = raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    return parsed.ifEmpty { null }
}

private fun decodeColors(raw: String?): List<Color>? {
    if (raw.isNullOrBlank()) return null
    val parsed = raw.split(";").mapNotNull { it.toLongOrNull()?.let { v -> Color(v.toInt()) } }
    return parsed.ifEmpty { null }
}

private fun deleteManagedFile(context: Context, path: String) {
    runCatching {
        val target = File(path).canonicalFile
        val root = File(context.filesDir, BG_DIR).canonicalFile
        if (target.path.startsWith(root.path + File.separator)) target.delete()
    }
}
