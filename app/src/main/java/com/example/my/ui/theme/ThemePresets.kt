package com.example.my.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.my.ui.i18n.tr

/**
 * 一套「成套主题」：背景 + 高亮 + 文字 + 玻璃 + 质感，一次点完。
 *
 * 参考 QQ 音乐 / Apple Music 的主题中心：用户不该自己一项一项配
 * （高亮色、文字色、玻璃强度、暗角…… 十几项组合出来的多半是难看的），
 * 而是**挑一套已经调好的**，细节想改再单独动。
 *
 * 每套都遵守项目铁律：文字色与背景亮度成对出现（深底配浅字、浅底配深字），
 * 玻璃强度全部落在 0.86~0.97 安全区间，所以任何一套切过去之后
 * [surfaceForCustomText] 都能推出可读的卡片底。
 */
data class ThemePreset(
    /** 持久化用的稳定 id，**不能改名**：老用户存的值要靠它还原 */
    val id: String,
    val zh: String,
    val en: String,
    val taglineZh: String,
    val taglineEn: String,
    /** 这套是深色系还是浅色系：只用于主题卡片的预览配色，不影响系统深浅 */
    val onDark: Boolean,
    val colors: List<Color>,
    val mode: BgColorMode,
    val style: BgStyle,
    val highlight: Color,
    val textColor: Color,
    val glass: GlassLevel,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val font: AppFont = AppFont.SYSTEM
) {
    val name: String get() = tr(zh, en)
    val tagline: String get() = tr(taglineZh, taglineEn)

    /** 卡片上的迷你背景：与真实背景同一套配色，只是简化成线性渐变 */
    val previewBrush: Brush
        get() = Brush.linearGradient(
            colors = colors.let { if (it.size >= 2) it else listOf(it.first(), it.first()) },
            start = Offset(0f, 0f),
            end = Offset(1000f, 1400f)
        )

    /** 把这套主题写成一份配置草稿（保留调用方原有的图片与轮播设置之外的字段） */
    fun draftFrom(base: AppBackgroundConfig): AppBackgroundConfig = base.copy(
        fill = BgFill.COLOR,
        mode = mode,
        style = style,
        colors = colors,
        // 预设本身就是成套配色，再让它跟着图取色会互相打架
        colorSync = ColorSync.OFF,
        highlight = highlight,
        textColor = textColor,
        glass = glass,
        vignette = vignette,
        grain = grain,
        font = font,
        sceneId = id
    )
}

/**
 * 主题预设清单。
 *
 * 前六套偏「氛围」（深色系为主，接近 QQ 音乐播放页的观感），
 * 后三套偏「安静」（浅色系与低饱和，适合白天、读书、看资讯）。
 * 顺序有意义：第一屏要能同时看到一深一浅，用户才知道可以选。
 */
val ThemePresets: List<ThemePreset> = listOf(
    ThemePreset(
        id = "aurora_night",
        zh = "极光夜航", en = "Aurora night",
        taglineZh = "深蓝极光，安静地流动", taglineEn = "Deep blue aurora, drifting slowly",
        onDark = true,
        colors = listOf(Color(0xFF0E1830), Color(0xFF16305C), Color(0xFF0B2A46)),
        mode = BgColorMode.TRI,
        style = BgStyle.AURORA,
        highlight = Color(0xFF4EA8FF),
        textColor = Color(0xFFF2F6FF),
        glass = GlassLevel.STANDARD,
        vignette = 34f,
        grain = 10f
    ),
    ThemePreset(
        id = "midnight_violet",
        zh = "午夜紫霞", en = "Midnight violet",
        taglineZh = "紫调光晕，夜里最有质感", taglineEn = "Violet glow, best after dark",
        onDark = true,
        colors = listOf(Color(0xFF191024), Color(0xFF2C1740), Color(0xFF1A2246)),
        mode = BgColorMode.TRI,
        style = BgStyle.MESH,
        highlight = Color(0xFFB98CFF),
        textColor = Color(0xFFF7F1FF),
        glass = GlassLevel.CLEAR,
        vignette = 28f,
        grain = 12f
    ),
    ThemePreset(
        id = "ink_black",
        zh = "星野墨黑", en = "Ink black",
        taglineZh = "近黑底 + 一点暖颗粒", taglineEn = "Near black with warm grain",
        onDark = true,
        colors = listOf(Color(0xFF0D0D0F)),
        mode = BgColorMode.SINGLE,
        style = BgStyle.SOLID,
        highlight = Color(0xFF5B8CFF),
        textColor = Color(0xFFF2F2F4),
        glass = GlassLevel.FROST,
        vignette = 20f,
        grain = 16f
    ),
    ThemePreset(
        id = "forest_moss",
        zh = "森屿青苔", en = "Forest moss",
        taglineZh = "墨绿极光，像深夜的林子", taglineEn = "Dark green aurora",
        onDark = true,
        colors = listOf(Color(0xFF0F1F19), Color(0xFF17402F), Color(0xFF102A24)),
        mode = BgColorMode.TRI,
        style = BgStyle.AURORA,
        highlight = Color(0xFF34C98A),
        textColor = Color(0xFFEFFBF4),
        glass = GlassLevel.STANDARD,
        vignette = 30f,
        grain = 14f
    ),
    ThemePreset(
        id = "cyber_neon",
        zh = "赛博霓虹", en = "Cyber neon",
        taglineZh = "紫青撞色，冲突得很聪明", taglineEn = "Violet and cyan, on purpose",
        onDark = true,
        colors = listOf(Color(0xFF150A2C), Color(0xFF2A0F3E), Color(0xFF0B1F3A)),
        mode = BgColorMode.TRI,
        style = BgStyle.MESH,
        highlight = Color(0xFF00E0C0),
        textColor = Color(0xFFF0FBFF),
        glass = GlassLevel.CLEAR,
        vignette = 26f,
        grain = 18f
    ),
    ThemePreset(
        id = "deep_sea",
        zh = "深海静默", en = "Deep sea",
        taglineZh = "青蓝渐变，专注时的底色", taglineEn = "Teal gradient for focus",
        onDark = true,
        colors = listOf(Color(0xFF0B1B24), Color(0xFF12303C), Color(0xFF0A2430)),
        mode = BgColorMode.TRI,
        style = BgStyle.LINEAR,
        highlight = Color(0xFF3FD0D8),
        textColor = Color(0xFFEDF7FA),
        glass = GlassLevel.STANDARD,
        vignette = 24f,
        grain = 8f
    ),
    ThemePreset(
        id = "morning_mist",
        zh = "晨雾", en = "Morning mist",
        taglineZh = "冷白到淡蓝，清爽不刺眼", taglineEn = "Cool white to pale blue",
        onDark = false,
        colors = listOf(Color(0xFFEDF2F7), Color(0xFFDCE8F3), Color(0xFFF4F7FA)),
        mode = BgColorMode.TRI,
        style = BgStyle.LINEAR,
        highlight = Color(0xFF3F7FD6),
        textColor = Color(0xFF1F2328),
        glass = GlassLevel.STANDARD
    ),
    ThemePreset(
        id = "peach_cream",
        zh = "蜜桃奶霜", en = "Peach cream",
        taglineZh = "柔粉暖橙，最上镜的一套", taglineEn = "Soft pink and warm peach",
        onDark = false,
        colors = listOf(Color(0xFFFDEFE6), Color(0xFFFBE4EC), Color(0xFFFDF6EC)),
        mode = BgColorMode.TRI,
        style = BgStyle.MESH,
        highlight = Color(0xFFE8695A),
        textColor = Color(0xFF2A211D),
        glass = GlassLevel.CLEAR,
        vignette = 12f
    ),
    ThemePreset(
        id = "sea_salt",
        zh = "海盐气泡", en = "Sea salt",
        taglineZh = "青白通透，夏天用", taglineEn = "Airy aqua for summer",
        onDark = false,
        colors = listOf(Color(0xFFE7F5FB), Color(0xFFD8EFF7), Color(0xFFEFFAF6)),
        mode = BgColorMode.TRI,
        style = BgStyle.AURORA,
        highlight = Color(0xFF2AA9C4),
        textColor = Color(0xFF1C2A2E),
        glass = GlassLevel.CLEAR,
        vignette = 10f
    ),
    ThemePreset(
        id = "paper_ivory",
        zh = "纸感米白", en = "Paper ivory",
        taglineZh = "暖白 + 颗粒，像一本旧书", taglineEn = "Warm white with paper grain",
        onDark = false,
        colors = listOf(Color(0xFFF7F2E9)),
        mode = BgColorMode.SINGLE,
        style = BgStyle.SOLID,
        highlight = Color(0xFF8C6A4A),
        textColor = Color(0xFF2A2520),
        glass = GlassLevel.FROST,
        vignette = 10f,
        grain = 26f,
        font = AppFont.SERIF
    ),
    ThemePreset(
        id = "morandi",
        zh = "莫兰迪", en = "Morandi",
        taglineZh = "低饱和灰调，看久了不累", taglineEn = "Muted grey, easy on the eyes",
        onDark = false,
        colors = listOf(Color(0xFFEAE6E0), Color(0xFFDCD8D2), Color(0xFFF0ECE7)),
        mode = BgColorMode.TRI,
        style = BgStyle.MESH,
        highlight = Color(0xFF8A8078),
        textColor = Color(0xFF2C2926),
        glass = GlassLevel.STANDARD,
        vignette = 14f,
        grain = 10f
    )
)

/** 按 id 找预设；找不到返回 null（用户删过、或配置来自旧版本） */
fun themePresetById(id: String?): ThemePreset? =
    if (id.isNullOrBlank()) null else ThemePresets.firstOrNull { it.id == id }

/** 供主题中心做「当前是哪套」的判断：null 表示用户自己调的，不是任何预设 */
fun currentSceneId(): String? = themePresetById(AppBackground.config.sceneId)?.id

/** 预设卡片右上角的勾选态：配置里任意一项偏离预设就算「已修改」 */
fun presetMatches(preset: ThemePreset, cfg: AppBackgroundConfig): Boolean =
    cfg.sceneId == preset.id
