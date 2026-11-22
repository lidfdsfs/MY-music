package com.example.my.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.example.my.R

/** 皮肤分组：装扮中心顶部的筛选标签 */
enum class SkinGroup(val zh: String, val en: String) {
    CLASSIC("经典", "Classic"),
    LIGHT("浅色", "Light"),
    DARK("深色", "Dark"),
    COLORFUL("彩彩", "Colorful"),
    DYNAMIC("动态", "Dynamic")
}

/**
 * 动态皮肤的效果类型：动画层叠在静态底色上，只在「真在播放 + 页面在前台」时跑帧，
 * 暂停/退后台一帧都不请求，和唱片旋转同一套省电策略。
 *
 * - [BLOOM]    呼吸光晕：唱片后面几团光随节奏胀缩
 * - [FLOW]     流光：大团彩色光斑缓慢环绕漂移，底色像液体一样流动
 * - [STARDUST] 星尘：细小星点从下往上飘，明暗闪烁
 * - [RIPPLE]   声波涟漪：一圈圈波纹从画面中心荡开，像水面上扩散的声波
 * - [AURORA]   极光帷幕：几道横向光带缓慢起伏流动，像夜空里的极光
 * - [METEOR]   流星雨：细长光点沿对角线划过，尾巴拖出一道光
 */
enum class PlayerAnim(val zh: String, val en: String) {
    NONE("无", "None"),
    BLOOM("呼吸光晕", "Bloom"),
    FLOW("流光", "Flow"),
    STARDUST("星尘", "Stardust"),
    RIPPLE("声波涟漪", "Ripple"),
    AURORA("极光帷幕", "Aurora"),
    METEOR("流星雨", "Meteor")
}

/**
 * 播放页版式骨架：换皮肤不只换颜色，也能换布局。
 *
 * - [STAGE]  原版：大唱片居中，信息与控件从上往下排
 * - [HUD]    科幻终端：唱片落在刻度环里，四周是读数与分段进度
 * - [POSTER] 杂志海报：超大字重歌名压场，唱片缩成角落小圆
 * - [ORBIT]  环绕式：控件散在唱片四周，进度是一圈弧
 * - [DUO]    双栏玻璃：左封面卡、右信息列
 */
enum class PlayerSkinLayout { STAGE, HUD, POSTER, ORBIT, DUO }

/**
 * 播放器皮肤：一套完整配色，直接驱动黑胶页的背景、唱片、文字与强调色。
 *
 * 只描述颜色/底图，不描述布局 —— 换皮肤不会改任何一个控件的尺寸和交互，
 * 所以选中的皮肤是真的"换上"，不是贴一张预览图。
 *
 * [bgImage] 是整页底图（照片/插画类皮肤用），[glow] 让唱片外圈罩一层柔和光晕
 * （"幻彩"那一挂的皮肤靠它做出参考图里的光圈）。
 */
enum class PlayerSkin(
    val id: String,
    val zh: String,
    val en: String,
    val group: SkinGroup,
    val tagZh: String,
    val tagEn: String,
    val dark: Boolean,
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    val ink: Color,
    val sub: Color,
    val accent: Color,
    val accentDeep: Color,
    val discLight: Color,
    val discMid: Color,
    val discDark: Color,
    val discRim: Color,
    val track: Color,
    val chipBg: Color,
    val bgImage: Int? = null,
    val glow: Boolean = false,
    val layout: PlayerSkinLayout = PlayerSkinLayout.STAGE,
    /** 动态皮肤：叠加在底色上的动画效果层 */
    val anim: PlayerAnim = PlayerAnim.NONE
) {
    /** 黑胶时间（原版）：暖橙唱片 + 米白底 */
    VINYL(
        id = "vinyl", zh = "黑胶时间", en = "Vinyl Time",
        group = SkinGroup.CLASSIC, tagZh = "默认", tagEn = "Default",
        dark = false,
        bgTop = Color(0xFFE9F3FC), bgMid = Color(0xFFF7EEE6), bgBottom = Color(0xFFF9E3D4),
        ink = Color(0xFF20242B), sub = Color(0xFF7B8089),
        accent = Color(0xFFFF8A35), accentDeep = Color(0xFFF0641C),
        discLight = Color(0xFFFFC79B), discMid = Color(0xFFFF8A3D), discDark = Color(0xFFE85A19),
        discRim = Color(0xFFFFD9BD), track = Color(0xFFE0D5CD),
        chipBg = Color(0x8FFFFFFF)
    ),

    /** 简约羽白 · 幻彩：白底 + 一大圈淡蓝光晕，蓝胶 */
    MIST(
        id = "mist", zh = "简约羽白 · 幻彩", en = "Misty Glow",
        group = SkinGroup.LIGHT, tagZh = "幻彩", tagEn = "Glow",
        dark = false,
        bgTop = Color(0xFFFFFFFF), bgMid = Color(0xFFF8FBFF), bgBottom = Color(0xFFE8F2FE),
        ink = Color(0xFF14181D), sub = Color(0xFF6B7684),
        accent = Color(0xFF1E9BFF), accentDeep = Color(0xFF0B6FD1),
        discLight = Color(0xFFBFE3FF), discMid = Color(0xFF7CC4FB), discDark = Color(0xFF3E9BE8),
        discRim = Color(0xFFE8F6FF), track = Color(0xFFDDE9F5),
        chipBg = Color(0x8FFFFFFF),
        glow = true
    ),

    /** 简约雅黑 · 幻彩：黑底 + 青蓝光晕，墨蓝胶 */
    NOIR(
        id = "noir", zh = "简约雅黑 · 幻彩", en = "Noir Glow",
        group = SkinGroup.DARK, tagZh = "幻彩", tagEn = "Glow",
        dark = true,
        bgTop = Color(0xFF0B0E12), bgMid = Color(0xFF05070A), bgBottom = Color(0xFF101722),
        ink = Color(0xFFF4F8FF), sub = Color(0xFF9AA5B2),
        accent = Color(0xFF2E9BFF), accentDeep = Color(0xFF1B6FD6),
        discLight = Color(0xFF1E6FA8), discMid = Color(0xFF0E3B57), discDark = Color(0xFF06141F),
        discRim = Color(0xFF164B6E), track = Color(0xFF22303C),
        chipBg = Color(0x1FFFFFFF),
        glow = true
    ),

    /** 炫彩律动 · 万：深紫底 + 霓虹花底图，玫红胶 */
    NEON(
        id = "neon", zh = "炫彩律动 · 万", en = "Neon Bloom",
        group = SkinGroup.COLORFUL, tagZh = "霓虹", tagEn = "Neon",
        dark = true,
        bgTop = Color(0xFF1A0A28), bgMid = Color(0xFF0C0514), bgBottom = Color(0xFF240A33),
        ink = Color(0xFFFFFFFF), sub = Color(0xFFD9B5E8),
        accent = Color(0xFFFF4FD8), accentDeep = Color(0xFFC026A8),
        discLight = Color(0xFFFF9BE8), discMid = Color(0xFFE040C0), discDark = Color(0xFF5B1878),
        discRim = Color(0xFFFFB3F2), track = Color(0xFF3C1E4E),
        chipBg = Color(0x1FFFFFFF),
        bgImage = R.drawable.skin_neon_bloom,
        glow = true
    ),

    /** 森林绿：苔绿底 + 森系绿唱片 */
    FOREST(
        id = "forest", zh = "森林绿", en = "Forest",
        group = SkinGroup.LIGHT, tagZh = "自然", tagEn = "Nature",
        dark = false,
        bgTop = Color(0xFFEFF6EC), bgMid = Color(0xFFF6F9F2), bgBottom = Color(0xFFD9EAD8),
        ink = Color(0xFF1B2A1E), sub = Color(0xFF69796A),
        accent = Color(0xFF16A34A), accentDeep = Color(0xFF0E7A37),
        discLight = Color(0xFFD8ECD3), discMid = Color(0xFF9FD4A5), discDark = Color(0xFF4E9A60),
        discRim = Color(0xFFE8F5E3), track = Color(0xFFD3E3CF),
        chipBg = Color(0xA6FFFFFF)
    ),

    /** 落日橘粉：粉霞底 + 珊瑚唱片 */
    SUNSET(
        id = "sunset", zh = "落日橘粉", en = "Sunset Pink",
        group = SkinGroup.COLORFUL, tagZh = "暖调", tagEn = "Warm",
        dark = false,
        bgTop = Color(0xFFFFF3EC), bgMid = Color(0xFFFFE7DD), bgBottom = Color(0xFFFFD2C2),
        ink = Color(0xFF33201A), sub = Color(0xFF8A625A),
        accent = Color(0xFFF43F5E), accentDeep = Color(0xFFC81E42),
        discLight = Color(0xFFFFC2B8), discMid = Color(0xFFFF7E6B), discDark = Color(0xFFD63A3C),
        discRim = Color(0xFFFFD9CE), track = Color(0xFFEFCFC5),
        chipBg = Color(0x8FFFFFFF)
    ),

    /** 定制专属 AI 播放器：青绿渐变 + 白色线稿唱片机（自制底图） */
    AI_STUDIO(
        id = "ai_studio", zh = "定制专属 AI 播放器", en = "AI Studio",
        group = SkinGroup.COLORFUL, tagZh = "自制", tagEn = "Custom",
        dark = false,
        bgTop = Color(0xFF22D0B0), bgMid = Color(0xFF14BE95), bgBottom = Color(0xFF1ED6AC),
        ink = Color(0xFFFFFFFF), sub = Color(0xFFD7FBF1),
        accent = Color(0xFFFFFFFF), accentDeep = Color(0xFF0A6B57),
        discLight = Color(0xFFEFFFFB), discMid = Color(0xFFA9F4E4), discDark = Color(0xFF4FC9B2),
        discRim = Color(0xFFFFFFFF), track = Color(0xFF1A7F6B),
        chipBg = Color(0x2FFFFFFF),
        bgImage = R.drawable.skin_ai_teal
    ),

    /** 简约羽白 · 经典：浅蓝影棚底图（自制），淡蓝胶 */
    MIST_CLASSIC(
        id = "mist_classic", zh = "简约羽白 · 经典", en = "Misty Classic",
        group = SkinGroup.CLASSIC, tagZh = "影棚", tagEn = "Studio",
        dark = false,
        bgTop = Color(0xFFEAF6FF), bgMid = Color(0xFFFFFFFF), bgBottom = Color(0xFFD8EBFB),
        ink = Color(0xFF16202B), sub = Color(0xFF5E7186),
        accent = Color(0xFF2AA7FF), accentDeep = Color(0xFF0C79C7),
        discLight = Color(0xFFBFE6FF), discMid = Color(0xFF7FCBF9), discDark = Color(0xFF2E9FE0),
        discRim = Color(0xFFFFFFFF), track = Color(0xFFD9E9F6),
        chipBg = Color(0x8FFFFFFF),
        bgImage = R.drawable.skin_mist_studio
    ),

    /** 暖光午后 · 经典系列：暖橘书房底图（自制），琥珀胶 */
    WARM_STUDY(
        id = "warm_study", zh = "暖光午后", en = "Warm Study",
        group = SkinGroup.COLORFUL, tagZh = "暖光", tagEn = "Warm light",
        dark = true,
        bgTop = Color(0xFFFFE2BC), bgMid = Color(0xFFF6C892), bgBottom = Color(0xFFD99B63),
        ink = Color(0xFFFFFFFF), sub = Color(0xFFFFE3C4),
        accent = Color(0xFFFFC46B), accentDeep = Color(0xFFFF9A3D),
        discLight = Color(0xFFFFD9A0), discMid = Color(0xFFE8A85C), discDark = Color(0xFFA9642A),
        discRim = Color(0xFFFFE9C8), track = Color(0xFFC98F52),
        chipBg = Color(0x2BFFFFFF),
        bgImage = R.drawable.skin_warm_study
    ),

    /** 极夜星轨：深蓝夜空 + 星轨极光，青蓝胶 */
    MIDNIGHT(
        id = "midnight", zh = "极夜星轨", en = "Midnight Orbit",
        group = SkinGroup.DARK, tagZh = "星夜", tagEn = "Night",
        dark = true,
        bgTop = Color(0xFF0A1430), bgMid = Color(0xFF050A1A), bgBottom = Color(0xFF0B1B33),
        ink = Color(0xFFEAF2FF), sub = Color(0xFF93A6C4),
        accent = Color(0xFF6EE7F9), accentDeep = Color(0xFF22A7C4),
        discLight = Color(0xFF2A4B7A), discMid = Color(0xFF132A48), discDark = Color(0xFF060F20),
        discRim = Color(0xFF4E7BAF), track = Color(0xFF1D2C44),
        chipBg = Color(0x1FFFFFFF),
        bgImage = R.drawable.skin_midnight,
        glow = true
    ),

    /** 赛博黄昏：橙粉紫合成波日落 + 霓虹网格，熔金胶 */
    CYBER(
        id = "cyber", zh = "赛博黄昏", en = "Cyber Dusk",
        group = SkinGroup.COLORFUL, tagZh = "合成波", tagEn = "Synthwave",
        dark = true,
        bgTop = Color(0xFF2A0B3F), bgMid = Color(0xFF12051E), bgBottom = Color(0xFF3A0F2E),
        ink = Color(0xFFFFF2E6), sub = Color(0xFFF0A8C8),
        accent = Color(0xFFFF7A3D), accentDeep = Color(0xFFC43A8E),
        discLight = Color(0xFFFF9A5C), discMid = Color(0xFFC1338E), discDark = Color(0xFF4A0F52),
        discRim = Color(0xFFFFC79E), track = Color(0xFF3B1230),
        chipBg = Color(0x1FFFFFFF),
        bgImage = R.drawable.skin_cyber_dusk,
        glow = true
    ),

    /** 樱雾粉樱：浅粉樱花散景，玫瑰胶 */
    SAKURA(
        id = "sakura", zh = "樱雾粉樱", en = "Sakura Mist",
        group = SkinGroup.LIGHT, tagZh = "春日", tagEn = "Spring",
        dark = false,
        bgTop = Color(0xFFFFF3F6), bgMid = Color(0xFFFFFFFF), bgBottom = Color(0xFFF8DCE6),
        ink = Color(0xFF2A1620), sub = Color(0xFF8A6A78),
        accent = Color(0xFFFF6FA5), accentDeep = Color(0xFFD93F7E),
        discLight = Color(0xFFFFD6E4), discMid = Color(0xFFFFA7C4), discDark = Color(0xFFE4638F),
        discRim = Color(0xFFFFE8F0), track = Color(0xFFEFD6E0),
        chipBg = Color(0x8FFFFFFF),
        bgImage = R.drawable.skin_sakura
    ),

    /** 深海鲸落：水下光柱，青碧胶 */
    DEEP(
        id = "deep", zh = "深海鲸落", en = "Deep Dive",
        group = SkinGroup.DARK, tagZh = "静谧", tagEn = "Calm",
        dark = true,
        bgTop = Color(0xFF062A33), bgMid = Color(0xFF021A22), bgBottom = Color(0xFF03222C),
        ink = Color(0xFFE6FBFF), sub = Color(0xFF8FC7D2),
        accent = Color(0xFF35E0D0), accentDeep = Color(0xFF12A79B),
        discLight = Color(0xFF1F7F8C), discMid = Color(0xFF0D5566), discDark = Color(0xFF032A36),
        discRim = Color(0xFF3FBFB4), track = Color(0xFF0F3742),
        chipBg = Color(0x20FFFFFF),
        bgImage = R.drawable.skin_deep_sea,
        glow = true
    ),

    /** 水墨留白：宣纸墨迹 + 朱印，墨色胶 */
    INK(
        id = "ink", zh = "水墨留白", en = "Ink Wash",
        group = SkinGroup.CLASSIC, tagZh = "东方", tagEn = "Oriental",
        dark = false,
        bgTop = Color(0xFFFAF7F1), bgMid = Color(0xFFFFFFFF), bgBottom = Color(0xFFEFEAE1),
        ink = Color(0xFF14110D), sub = Color(0xFF6B6255),
        accent = Color(0xFFC0392B), accentDeep = Color(0xFF8E2418),
        discLight = Color(0xFFE8E3D9), discMid = Color(0xFFA79C8C), discDark = Color(0xFF4B443A),
        discRim = Color(0xFFD8D1C4), track = Color(0xFFDFD8CB),
        chipBg = Color(0x9FFFFFFF),
        bgImage = R.drawable.skin_ink_wash
    ),

    /** 薄荷玻璃：无底图的纯色玻璃感，薄荷胶 + 光晕 */
    MINT(
        id = "mint", zh = "薄荷玻璃", en = "Mint Glass",
        group = SkinGroup.LIGHT, tagZh = "通透", tagEn = "Fresh",
        dark = false,
        bgTop = Color(0xFFEAFBF4), bgMid = Color(0xFFF7FFFC), bgBottom = Color(0xFFD6F5EA),
        ink = Color(0xFF0E2A24), sub = Color(0xFF5A8177),
        accent = Color(0xFF12C2A0), accentDeep = Color(0xFF0A9377),
        discLight = Color(0xFFCFF7EA), discMid = Color(0xFF8FE6CE), discDark = Color(0xFF33B79C),
        discRim = Color(0xFFE4FBF4), track = Color(0xFFCDEDE3),
        chipBg = Color(0xA6FFFFFF),
        glow = true
    ),

    // ── 值几套换了骨架的：科幻终端 / 极光全息 / 白噪海报 / 双轨玻璃 / 深空引力 ──

    /** 星际终端：雷达刻度环里放唱片，分段进度 + 等宽读数 */
    STAR_TERMINAL(
        id = "star_terminal", zh = "星际终端", en = "Star Terminal",
        group = SkinGroup.DARK, tagZh = "HUD", tagEn = "Sci-fi",
        dark = true,
        bgTop = Color(0xFF0A1A20), bgMid = Color(0xFF05121A), bgBottom = Color(0xFF071A22),
        ink = Color(0xFFE8FBFF), sub = Color(0xFF7FB3C0),
        accent = Color(0xFF2EE6E0), accentDeep = Color(0xFF0FA9A6),
        discLight = Color(0xFFCFFCF8), discMid = Color(0xFF2FB8C4), discDark = Color(0xFF06202A),
        discRim = Color(0xFF7DFFFF), track = Color(0xFF123039),
        chipBg = Color(0x1A33FFF0),
        bgImage = R.drawable.skin_sci_hud,
        glow = true,
        layout = PlayerSkinLayout.HUD
    ),

    /** 极光全息：控件环绕唱片，进度是外圈弧 */
    AURORA_HOLO(
        id = "aurora_holo", zh = "极光全息", en = "Aurora Holo",
        group = SkinGroup.COLORFUL, tagZh = "全息", tagEn = "Hologram",
        dark = true,
        bgTop = Color(0xFF1B1140), bgMid = Color(0xFF0C0A24), bgBottom = Color(0xFF221A4A),
        ink = Color(0xFFF4ECFF), sub = Color(0xFFBCA9E0),
        accent = Color(0xFFA56CFF), accentDeep = Color(0xFF6E3FD6),
        discLight = Color(0xFFE8D6FF), discMid = Color(0xFFB58CFF), discDark = Color(0xFF4B2E86),
        discRim = Color(0xFFD9C2FF), track = Color(0xFF2E2352),
        chipBg = Color(0x1FA070FF),
        bgImage = R.drawable.skin_aurora,
        glow = true,
        layout = PlayerSkinLayout.ORBIT
    ),

    /** 白噪海报：超大字重 + 极细线，黑白单色 */
    MONO_POSTER(
        id = "mono_poster", zh = "白噪海报", en = "Mono Poster",
        group = SkinGroup.CLASSIC, tagZh = "黑白", tagEn = "Mono",
        dark = false,
        bgTop = Color(0xFFFFFFFF), bgMid = Color(0xFFF5F5F3), bgBottom = Color(0xFFE7E7E4),
        ink = Color(0xFF0B0B0C), sub = Color(0xFF6E6E70),
        accent = Color(0xFF0B0B0C), accentDeep = Color(0xFF000000),
        discLight = Color(0xFFEDEDEB), discMid = Color(0xFFB9B9B6), discDark = Color(0xFF4A4A48),
        discRim = Color(0xFFDCDCDA), track = Color(0xFFD6D6D3),
        chipBg = Color(0xA6FFFFFF),
        layout = PlayerSkinLayout.POSTER
    ),

    /** 双轨玻璃：左封面玻璃卡 + 右信息列 */
    GLASS_DUO(
        id = "glass_duo", zh = "双轨玻璃", en = "Glass Duo",
        group = SkinGroup.LIGHT, tagZh = "玻璃", tagEn = "Glass",
        dark = false,
        bgTop = Color(0xFFF2FBFF), bgMid = Color(0xFFFFFFFF), bgBottom = Color(0xFFDCEFF7),
        ink = Color(0xFF0C2430), sub = Color(0xFF5D8091),
        accent = Color(0xFF14B8C4), accentDeep = Color(0xFF0C8A95),
        discLight = Color(0xFFD9F6FC), discMid = Color(0xFF9BE0EC), discDark = Color(0xFF3FA9BA),
        discRim = Color(0xFFEAFDFF), track = Color(0xFFCFE7EE),
        chipBg = Color(0x8FFFFFFF),
        bgImage = R.drawable.skin_frost,
        glow = true,
        layout = PlayerSkinLayout.DUO
    ),

    /** 深空引力：星云底 + 超大歌名，唱片缩成右下一枚小圆 */
    DEEP_GRAVITY(
        id = "deep_gravity", zh = "深空引力", en = "Deep Gravity",
        group = SkinGroup.DARK, tagZh = "星云", tagEn = "Nebula",
        dark = true,
        bgTop = Color(0xFF2A1140), bgMid = Color(0xFF0B0A1E), bgBottom = Color(0xFF1A0F33),
        ink = Color(0xFFF6EEFF), sub = Color(0xFFC0AAD8),
        accent = Color(0xFFFF5FA2), accentDeep = Color(0xFFC22E72),
        discLight = Color(0xFFFFC7E0), discMid = Color(0xFFE86AB0), discDark = Color(0xFF5B1740),
        discRim = Color(0xFFFFD9EC), track = Color(0xFF33183C),
        chipBg = Color(0x1FFFB0D8),
        bgImage = R.drawable.skin_nebula,
        glow = true,
        layout = PlayerSkinLayout.POSTER
    ),

    // ── 动态系列：底色之上叠一层持续动画（只在播放时跑帧，暂停/退后台即停） ──

    /** 动态 · 心跳玫光：暗夜底，唱片后一团玫红光随播放胀缩，像跟着节拍呼吸 */
    PULSE_BLOOM(
        id = "pulse_bloom", zh = "动态 · 心跳玫光", en = "Pulse Bloom",
        group = SkinGroup.DYNAMIC, tagZh = "呼吸", tagEn = "Pulse",
        dark = true,
        bgTop = Color(0xFF170A1C), bgMid = Color(0xFF0A0510), bgBottom = Color(0xFF22101F),
        ink = Color(0xFFFFF0F6), sub = Color(0xFFD8A9C4),
        accent = Color(0xFFFF4F8E), accentDeep = Color(0xFFC22762),
        discLight = Color(0xFFFFB8D2), discMid = Color(0xFFE84C8B), discDark = Color(0xFF5C1236),
        discRim = Color(0xFFFFD2E4), track = Color(0xFF3A1B31),
        chipBg = Color(0x1FFF9CC4),
        glow = true,
        anim = PlayerAnim.BLOOM
    ),

    /** 动态 · 极光流彩：深靛底，几团青紫光斑绕着画面缓慢漂移，底色像液体在流 */
    LIQUID_FLOW(
        id = "liquid_flow", zh = "动态 · 极光流彩", en = "Liquid Flow",
        group = SkinGroup.DYNAMIC, tagZh = "流光", tagEn = "Flow",
        dark = true,
        bgTop = Color(0xFF07202E), bgMid = Color(0xFF03101C), bgBottom = Color(0xFF0C1E38),
        ink = Color(0xFFECFAFF), sub = Color(0xFF8FB8CE),
        accent = Color(0xFF35E0D0), accentDeep = Color(0xFF6E5BE8),
        discLight = Color(0xFFBFF6EF), discMid = Color(0xFF3FC9BD), discDark = Color(0xFF123B55),
        discRim = Color(0xFFC9F4EC), track = Color(0xFF14324A),
        chipBg = Color(0x1F63E8D8),
        glow = true,
        anim = PlayerAnim.FLOW
    ),

    /** 动态 · 银河星尘：近黑深空底，细小星点从下往上飘、明暗闪烁 */
    STARDUST_SKY(
        id = "stardust_sky", zh = "动态 · 银河星尘", en = "Stardust Sky",
        group = SkinGroup.DYNAMIC, tagZh = "星尘", tagEn = "Stardust",
        dark = true,
        bgTop = Color(0xFF050814), bgMid = Color(0xFF02040C), bgBottom = Color(0xFF0A1226),
        ink = Color(0xFFEAF2FF), sub = Color(0xFF93A6C4),
        accent = Color(0xFF9BB8FF), accentDeep = Color(0xFF5E7BE0),
        discLight = Color(0xFFD6E2FF), discMid = Color(0xFF7E97E8), discDark = Color(0xFF1C2A56),
        discRim = Color(0xFFDDE7FF), track = Color(0xFF1A2440),
        chipBg = Color(0x1FAFC8FF),
        glow = true,
        anim = PlayerAnim.STARDUST
    ),

    /** 动态 · 潮汐涟漪：深海青底，一圈圈声波从中心荡开 */
    RIPPLE_TIDE(
        id = "ripple_tide", zh = "动态 · 潮汐涟漪", en = "Ripple Tide",
        group = SkinGroup.DYNAMIC, tagZh = "涟漪", tagEn = "Ripple",
        dark = true,
        bgTop = Color(0xFF032A33), bgMid = Color(0xFF01161C), bgBottom = Color(0xFF062A36),
        ink = Color(0xFFE8FBFF), sub = Color(0xFF8FBFCC),
        accent = Color(0xFF3FE0D8), accentDeep = Color(0xFF1E8FA8),
        discLight = Color(0xFFC6F7F2), discMid = Color(0xFF3FC6C2), discDark = Color(0xFF0E3E4C),
        discRim = Color(0xFFD2FAF5), track = Color(0xFF123642),
        chipBg = Color(0x1F6FEFE4),
        glow = true,
        anim = PlayerAnim.RIPPLE
    ),

    /** 动态 · 极光帷幕：墨绿夜空，几道光带缓慢起伏 */
    AURORA_VEIL(
        id = "aurora_veil", zh = "动态 · 极光帷幕", en = "Aurora Veil",
        group = SkinGroup.DYNAMIC, tagZh = "极光", tagEn = "Aurora",
        dark = true,
        bgTop = Color(0xFF041A1A), bgMid = Color(0xFF020B12), bgBottom = Color(0xFF0A1A24),
        ink = Color(0xFFEDFFF6), sub = Color(0xFF9CC4B6),
        accent = Color(0xFF5BF0A8), accentDeep = Color(0xFF7A6BF0),
        discLight = Color(0xFFCCFFE4), discMid = Color(0xFF44D69A), discDark = Color(0xFF0D3B36),
        discRim = Color(0xFFD6FFEB), track = Color(0xFF123028),
        chipBg = Color(0x1F8CFFC8),
        glow = true,
        anim = PlayerAnim.AURORA
    ),

    /** 动态 · 流星夜航：靛蓝夜空，流星拖着尾巴斜着划过 */
    METEOR_NIGHT(
        id = "meteor_night", zh = "动态 · 流星夜航", en = "Meteor Night",
        group = SkinGroup.DYNAMIC, tagZh = "流星", tagEn = "Meteor",
        dark = true,
        bgTop = Color(0xFF0B1030), bgMid = Color(0xFF04060F), bgBottom = Color(0xFF12102E),
        ink = Color(0xFFF2F0FF), sub = Color(0xFFA9A6CF),
        accent = Color(0xFFFFC97A), accentDeep = Color(0xFF8F7BFF),
        discLight = Color(0xFFFFE8BE), discMid = Color(0xFFB99CFF), discDark = Color(0xFF241A52),
        discRim = Color(0xFFFFF0CF), track = Color(0xFF1B1A3C),
        chipBg = Color(0x1FFFD9A0),
        glow = true,
        anim = PlayerAnim.METEOR
    );

    companion object {
        fun byId(id: String?): PlayerSkin = entries.firstOrNull { it.id == id } ?: VINYL
    }
}

/**
 * 当前皮肤：只存 id 到 SharedPreferences，重启 App 仍然是上次选的那套。
 * state 是 Compose 状态，切换后播放页立刻重组，不用重建播放器。
 */
object PlayerSkinStore {
    private const val PREFS = "player_skin"
    private const val KEY_ID = "skin_id"
    private const val KEY_ANIM = "anim_enabled"

    private val state = mutableStateOf(PlayerSkin.VINYL)
    private val anim = mutableStateOf(true)
    private var loaded = false

    val current: PlayerSkin get() = state.value

    /** 动态皮肤动画总开关：关掉后动画层停在静止帧，一帧都不再请求 */
    val animationsEnabled: Boolean get() = anim.value

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        state.value = PlayerSkin.byId(prefs.getString(KEY_ID, null))
        anim.value = prefs.getBoolean(KEY_ANIM, true)
    }

    fun select(context: Context, skin: PlayerSkin) {
        state.value = skin
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ID, skin.id)
            .apply()
    }

    fun setAnimationsEnabled(context: Context, on: Boolean) {
        anim.value = on
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ANIM, on)
            .apply()
    }
}
