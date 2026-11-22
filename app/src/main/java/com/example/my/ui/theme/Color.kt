package com.example.my.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * MY足迹 语义色板（支持浅色 / 深色夜间模式）
 *
 * 设计原则（冷峻工具风）：
 *  - 中性冷灰主导：近白冷灰 / 近黑石墨，表面平整、发丝级分隔线，不用暖米色
 *  - 单一信号色：冰蓝仅用于 CTA / 选中态 / 关键数据，不做大面积铺色
 *  - 信息层级靠字号、字重与等宽数字，不靠渐变、光晕、彩色堆叠
 *  - 所有语义 token 通过 [AppPalette] 动态解析，主题按系统深浅色切换，读取会自动重组
 */
data class Palette(
    val terracotta: Color,
    val terracottaDark: Color,
    val terracottaLight: Color,
    val teal: Color,
    val gold: Color,
    val splashDeep: Color,
    val splashPurple: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceDim: Color,
    val surfaceGlass: Color,
    val outline: Color,
    val outlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textInverse: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    val scrim: Color,
    val shadow: Color,
    val officialGold: Color
)

/** 浅色（QQ 音乐式浅灰底 + 纯白卡面） */
val LightPalette = Palette(
    terracotta = Color(0xFF31C27C),
    terracottaDark = Color(0xFF23A566),
    terracottaLight = Color(0xFF74DDA9),
    teal = Color(0xFF2BA6A0),
    gold = Color(0xFFF0A93B),
    splashDeep = Color(0xFF3D2C4E),
    splashPurple = Color(0xFF6C4F8A),
    background = Color(0xFFF5F6F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F2F4),
    surfaceDim = Color(0xFFE8EBEE),
    surfaceGlass = Color(0xF2FFFFFF),
    outline = Color(0xFFEDEFF2),
    outlineStrong = Color(0xFFDDE2E7),
    textPrimary = Color(0xFF16181C),
    textSecondary = Color(0xFF5F6670),
    textMuted = Color(0xFF9AA1AA),
    textInverse = Color(0xFFFFFFFF),
    success = Color(0xFF1EA363),
    danger = Color(0xFFE5544B),
    warning = Color(0xFFF0A93B),
    scrim = Color(0x99000000),
    shadow = Color(0x14000000),
    officialGold = Color(0xFFB07A18)
)

/**
 * 深色（QQ 音乐夜间：深灰底 + 略亮卡面）。
 * 层级靠明度与圆角卡片，绿色只做强调。
 */
val DarkPalette = Palette(
    terracotta = Color(0xFF3DDC97),
    terracottaDark = Color(0xFF2FBE80),
    terracottaLight = Color(0xFF7FF0BE),
    teal = Color(0xFF37C0CC),
    gold = Color(0xFFF0C060),
    splashDeep = Color(0xFF3D2C4E),
    splashPurple = Color(0xFF6C4F8A),
    background = Color(0xFF101214),
    surface = Color(0xFF191C20),
    surfaceVariant = Color(0xFF22262B),
    surfaceDim = Color(0xFF2B3036),
    surfaceGlass = Color(0xE6191C20),
    outline = Color(0xFF2A2F35),
    outlineStrong = Color(0xFF3A4149),
    textPrimary = Color(0xFFF2F4F6),
    textSecondary = Color(0xFFB0B7C0),
    textMuted = Color(0xFF7D858F),
    textInverse = Color(0xFFFFFFFF),
    success = Color(0xFF3DDC97),
    danger = Color(0xFFFF6B5E),
    warning = Color(0xFFF0C060),
    scrim = Color(0xB3000000),
    shadow = Color(0x33000000),
    officialGold = Color(0xFFF0C060)
)

/** 当前生效调色板：由主题按系统深浅色写入，读取会自动触发重组 */
val AppPalette = mutableStateOf(LightPalette)

object Brand {
    /** 品牌主色：QQ 音乐绿（全 App 唯一强调色） */
    val Terracotta get() = AppPalette.value.terracotta
    /** 品牌主色深（按压态/渐变端） */
    val TerracottaDark get() = AppPalette.value.terracottaDark
    /** 品牌主色浅（高亮端） */
    val TerracottaLight get() = AppPalette.value.terracottaLight
    /** 辅助绿 */
    val Teal get() = AppPalette.value.teal
    /** 点缀金（勋章/特别地点） */
    val Gold get() = AppPalette.value.gold
    /** 启动页深紫 */
    val SplashDeep get() = AppPalette.value.splashDeep
    /** 启动页亮紫 */
    val SplashPurple get() = AppPalette.value.splashPurple
}

object Bg {
    /** 页面背景 */
    val Background get() = AppPalette.value.background
    /** 卡片表面 */
    val Surface get() = AppPalette.value.surface
    /** 次级容器（输入框底/选中底色/标签底） */
    val SurfaceVariant get() = AppPalette.value.surfaceVariant
    /** 更深的容器（下拉项 hover / 时间轴底） */
    val SurfaceDim get() = AppPalette.value.surfaceDim
    /** 浮层/毛玻璃底（半透明） */
    val SurfaceGlass get() = AppPalette.value.surfaceGlass
    /** 描边 */
    val Outline get() = AppPalette.value.outline
    /** 强描边（按压态） */
    val OutlineStrong get() = AppPalette.value.outlineStrong
}

/**
 * 覆盖层专用底色：**所有盖在另一层页面之上的层都必须用这里的实心底**。
 *
 * 根因：自定义背景开启时 [AppPalette.background] 会被设成 `Color.Transparent`，
 * 目的是让根容器的自定义背景图透出来。若全屏叠加页 / bottom sheet / dialog
 * 也拿它当底色，下面那一层页面的文字就会整个透上来、与浮层文字叠成一片：
 * 资讯文章页压在历史时间线上、评论弹层压在全屏播放页上，都是同一个错。
 *
 * 因此严格分两类，别再混用：
 *  - **页面根**：用 [Bg.Background]（允许透明，透出自定义背景图）
 *  - **覆盖层**：用 [Layer.Opaque]（取 surface 的 RGB，强制 alpha = 1f）
 *
 * 取 surface 而非写死白/黑，是为了让浅色、深色、自定义文字色三态都自动跟随，
 * 同时保证 `alpha = 1f`，绝不放行任何底层内容穿透。
 */
object Layer {
    /** 全屏叠加页 / bottom sheet / dialog 的实心底，恒不透明 */
    val Opaque get(): Color = AppPalette.value.surface.copy(alpha = 1f)

    /** 弹层后方的压暗遮罩：下限 0.72，底层文字不再与浮层抢注意力 */
    val Scrim get(): Color = AppPalette.value.scrim.copy(alpha = 0.72f)
}

object Text {
    val Primary get() = AppPalette.value.textPrimary
    val Secondary get() = AppPalette.value.textSecondary
    val Muted get() = AppPalette.value.textMuted
    val Inverse get() = AppPalette.value.textInverse
}

/**
 * 固定墨色：**不读「自定义背景」里的字体与文字色设置**，只按表面亮度反转。
 *
 * 用途限定在必须保证可读性的顶栏元素（资讯 / 发现 / 定位 / 天气 / 音乐 / 消息）。
 * 这些元素压在自定义背景照片上，一旦跟着用户的文字色与手写字体走，就会糊成一片；
 * 用不透明胶囊底 + 按底反色的墨色，才是能保证对比度的做法。
 */
object FixedInk {
    /** 不透明胶囊底：取 surface 的 RGB 强制 alpha=1，照片不会透上来 */
    val ChipSurface get(): Color = AppPalette.value.surface.copy(alpha = 1f)

    /** 压在 [ChipSurface] 上的文字色 */
    val OnChip get(): Color =
        if (AppPalette.value.surface.luminance() > 0.55f) Color(0xFF1B1714) else Color(0xFFF6F2ED)
}

/** 启动页强调色：品牌蓝的固定副本，不跟随「自定义背景·高亮色」 */
val SplashAccent: Color = Color(0xFF2F72E8)

/** 启动页固定墨色（启动页底色是写死的薄荷白渐变，不随主题与自定义背景变化） */
val SplashInk: Color = Color(0xFF1E2A38)
val SplashInkSub: Color = Color(0xFF5D6B7A)
val SplashInkFaint: Color = Color(0xFFA3B0BF)

object Func {
    val Success get() = AppPalette.value.success
    val Danger get() = AppPalette.value.danger
    val Warning get() = AppPalette.value.warning
    /** 遮罩：与 [Layer.Scrim] 同口径，压在另一层页面上的遮罩下限 0.72 */
    val Scrim get() = Layer.Scrim
    /** 阴影色 */
    val Shadow get() = AppPalette.value.shadow
}

/**
 * 资产结构分类色（交易页占比环 / 图例 / 中心标签共用）。
 *
 * 分类色不是涨跌色：涨红跌绿另见 TradeUi 的 TradeUp / TradeDown。
 * 现金蓝按表面亮度分浅深两套，深色主题下提亮，避免与暗底糊成一片。
 * 全部走 [AppPalette]，切主题自动重组。
 */
object Allocation {
    private val p get() = AppPalette.value

    val Stock get(): Color = p.terracotta
    val Fund get(): Color = p.teal
    val Cash get(): Color =
        if (p.surface.luminance() > 0.55f) Color(0xFF5C7FB8) else Color(0xFF8FA9D6)
    val Track get(): Color = p.outline
    val Inner get(): Color = p.surfaceVariant
}

/**
 * 驾驶模式车机色板：HMI 固定深色，**不跟随浅色主题**。
 *
 * 车机界面要的是「深色地图 + 高对比导航蓝」，浅色主题下地图会失去对比度、
 * 路线也糊成一片，所以这里独立成一套，全部收在一处供 DrivingScreen 取用；
 * 页面里不许再出现随手写的颜色常量。文字色统一取 [Ink]，不走这套图形色。
 */
object DrivingPalette {
    val Stage: Color = Color(0xFF1B2734)
    val StageTop: Color = Color(0xFF243240)
    val Map: Color = Color(0xFF101720)
    val Road: Color = Color(0xFF3C4A5A)
    val RoadMinor: Color = Color(0xFF29333F)
    val RouteCasing: Color = Color(0xFF0E3C93)
    val Route: Color = Color(0xFF2A7CF6)
    val RouteLight: Color = Color(0xFF6FB2FF)
    val Puck: Color = Color(0xFF1FD8C8)
    val Card: Color = Color(0xFF2C3946)
    val CardSoft: Color = Color(0xFF354250)
    val PanelTop: Color = Color(0xFF253545)
    val PanelBottom: Color = Color(0xFF16222E)
    val Tool: Color = Color(0xFF394656)
    val Bar: Color = Color(0xFF33404F)
    val BarEdge: Color = Color(0xFF55677C)
    val Accent: Color = Color(0xFF3AA9F5)
    val Teal: Color = Color(0xFF2AD6C2)
    val Junction: Color = Color(0xFF5BE07A)
    val CoverA: Color = Color(0xFF38E0E6)
    val CoverB: Color = Color(0xFF12B7D8)
    val CoverC: Color = Color(0xFF4BF0BB)
    val CoverD: Color = Color(0xFF1658D8)
    val CoverE: Color = Color(0xFF0E3FA8)
    val Dim: Color = Color(0xFF8B9BAD)

    // ---------- 导航 HUD（对照高德等主流导航补齐的界面语言）----------
    // 白卡在深色底图上做高对比信息层；引导卡仍走深色，保持车机夜间基调。
    val CardWhite: Color = Color(0xFFF7FAFD)
    val CardWhiteSub: Color = Color(0xFFEAF0F7)
    val OnCard: Color = Color(0xFF16202C)
    val OnCardSub: Color = Color(0xFF6B7B8C)
    val OnCardFaint: Color = Color(0xFF8B9AA9)
    /** 大引导卡深底：与 Stage 同色系但不透明，压住下面的路网 */
    val GuideDark: Color = Color(0xFF111C28)
    val GuideEdge: Color = Color(0xFF2E4A66)
    val GuideSub: Color = Color(0xFF93A9BD)
    val GuideUnit: Color = Color(0xFFCFE0F0)
    /** 高德蓝：主操作、剩余时间、车道高亮 */
    val GoBlue: Color = Color(0xFF1E7BF0)
    val GoBlueSoft: Color = Color(0xFFEAF2FE)
    val LaneOff: Color = Color(0xFF3A4756)
    val LaneOffInk: Color = Color(0xFF7C8FA3)
    val LaneOn: Color = Color(0xFF1E7BF0)
    /** 限速红 / 电子眼红 */
    val WarnRed: Color = Color(0xFFE14A3C)
    val WarnRedSoft: Color = Color(0xFFFDECEA)
    /** 路况三色：畅通 / 缓行 / 拥堵 */
    val TrafficFree: Color = Color(0xFF34B36A)
    val TrafficSlow: Color = Color(0xFFF2A93B)
    val TrafficJam: Color = Color(0xFFE8503A)
    /** 高速出口绿 */
    val HighwayGreen: Color = Color(0xFF2F9E60)
    /** 路口放大窗的浅色纸底 */
    val JunctionPaper: Color = Color(0xFFF2F0EA)
    val JunctionCase: Color = Color(0xFFE2DDD3)
}

/**
 * 浮层文字色（液态玻璃 / 封面 / 强调色按钮 / 危险操作）。
 *
 * 铁律：文字层级靠**字号、字重、色相**建立，不靠降低文字 alpha。
 * 液态玻璃表面本身已是 0.86~0.97 不透明，若文字再降到 0.7 以下，
 * 自定义背景态下的有效对比度会跌破 3:1，重新出现「发灰、与底层糊成一片」。
 * 因此本对象所有 getter 的 alpha 下限为 0.84，主文字恒为 1.0。
 *
 * 全部读 [AppPalette]，浅色 / 深色 / 自定义背景三态切换会自动重组。
 */
object Ink {
    private val p get() = AppPalette.value

    /** 玻璃 / 卡片主文字：跟随主题与自定义文字色，强制不透明 */
    val OnGlass get(): Color = p.textPrimary.copy(alpha = 1f)

    /** 玻璃次级文字：保留语义色相，但 alpha 不低于 0.92 */
    val OnGlassSub get(): Color =
        p.textSecondary.copy(alpha = p.textSecondary.alpha.coerceAtLeast(0.92f))

    /** 玻璃辅助文字：alpha 不低于 0.84（旧写法 0.70 在半透明卡上几乎读不清） */
    val OnGlassMuted get(): Color =
        p.textMuted.copy(alpha = p.textMuted.alpha.coerceAtLeast(0.84f))

    /** 封面 / 深色照片上的主文字：纯白，不再用 0.8 / 0.85 这类发灰值 */
    val OnCover: Color = Color(0xFFFFFFFF)

    /** 封面次级文字：0.93 不透明，压在黑色渐变上仍有清晰边界 */
    val OnCoverSub: Color = Color(0xEDFFFFFF)

    /** 强调色（品牌赤陶 / 自定义高亮）上的文字：按底色亮度反转 */
    val OnAccent get(): Color =
        if (p.terracotta.luminance() > 0.62f) Color(0xFF171412) else Color(0xFFFFFFFF)

    /** 危险 / 删除 / 校验错误文字：随深浅主题反转，白底不用亮红 */
    val Danger get(): Color =
        if (p.textPrimary.luminance() > 0.55f) Color(0xFFFF9B82) else Color(0xFFB4372A)

    /** 阅读页品牌红：同样随主题反转，深色下不用暗红 */
    val ReaderAccent get(): Color =
        if (p.textPrimary.luminance() > 0.55f) Color(0xFFFF8A73) else Color(0xFFB23426)
}

// ============ 兼容旧引用（保持品牌名，指向语义 token，随深浅色切换） ============
val Terracotta get() = Brand.Terracotta
val TerracottaDark get() = Brand.TerracottaDark
val TerracottaLight get() = Brand.TerracottaLight
val Teal get() = Brand.Teal
val WarmYellow get() = Brand.Gold
val SplashDeep get() = Brand.SplashDeep
val SplashPurple get() = Brand.SplashPurple
val WarmBg get() = Bg.Background
val WarmSurface get() = Bg.Surface
val WarmSurfaceVariant get() = Bg.SurfaceVariant
val WarmSurfaceDim get() = Bg.SurfaceDim
val BorderLight get() = Bg.Outline
val BorderStrong get() = Bg.OutlineStrong
val TextPrimary get() = Text.Primary
val TextSecondary get() = Text.Secondary
val TextMuted get() = Text.Muted
val SuccessColor get() = Func.Success
val DangerColor get() = Func.Danger
val ScrimColor get() = Func.Scrim

// —— 兼容 token（映射回语义色板，随深浅色切换）——
val TextMain get() = Text.Primary
val TextSub get() = Text.Secondary
val TextWeak get() = Text.Muted
val Coral get() = Brand.Terracotta
val CardBg get() = Bg.Surface
val FavYellow get() = Brand.Gold
val LightOrange get() = Brand.TerracottaLight
val OfficialGold1 get() = Brand.Gold
val OfficialGold2 get() = AppPalette.value.officialGold
val MainGradient: List<Color> get() = listOf(Brand.Terracotta, Brand.TerracottaDark)

/** 封面渐变取色调色板：清新高饱和，接近 QQ 音乐的无图封面 */
val CoverPalette: List<Color> = listOf(
    Color(0xFF31C27C), Color(0xFF4FB3A7), Color(0xFF5C9BD6), Color(0xFF8E7CC3), Color(0xFFE0A458),
    Color(0xFFD98BA0), Color(0xFF46BFA0), Color(0xFF6A8FD8),
    Color(0xFF9BBF4B), Color(0xFFCB8A5E)
)
