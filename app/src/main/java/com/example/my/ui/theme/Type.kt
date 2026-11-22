package com.example.my.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字号体系（QQ 音乐式层级）：
 *  - 标题加粗、字号偏大，页面第一眼有大标题
 *  - display/title 系列：24/22/20
 *  - body 系列：正文 14/13（行高 1.5）
 *  - label 系列：辅助 12/11/10
 */
val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    displayMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp, letterSpacing = (-0.2).sp),
    headlineLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp),
    titleLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp)
)

/**
 * 语义排版层（对标 M3 Expressive：用加重字重/加大字号引导注意力，建立清晰层级）。
 * 把高频文案模式统一成可复用样式，一处改、全局生效。
 */
object Type {
    /** 用户选择的全局字体，读取 Compose 状态，切换后所有引用会自动重组 */
    fun font() = fontFamilyFor(
        if (AppBackground.config.enabled) AppBackground.config.font else AppFont.SYSTEM
    )

    /** 首页大标题（Hero，带强调色片段） */
    val heroTitle get() = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp, letterSpacing = (-0.4).sp, fontFamily = font())
    /** 区块标题（QQ 音乐式大标题） */
    val sectionTitle get() = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp, fontFamily = font())
    /** 卡片标题 */
    val cardTitle get() = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp, fontFamily = font())
    /** 正文 */
    val body get() = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp, fontFamily = font())
    /** 说明/次要信息 */
    val caption get() = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp, fontFamily = font())
    /** 统计数字（等宽数字只保证不抖动，字体仍用系统常规字重） */
    val statNumber get() = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp, fontFeatureSettings = "tnum", fontFamily = font())
}

/**
 * 数字样式：QQ 音乐不使用等宽字体，这里统一回落到系统字体，
 * 只保留 tnum 保证位数变化时不左右跳动。命名保留以避免大范围改动。
 */
object Digits {
    /** 系统常规字体（数字仍启用 tnum） */
    val mono: FontFamily = FontFamily.Default

    /** 大号读数（统计总量 / 播放页时长） */
    val reading get() = TextStyle(
        fontSize = TypeScale.page, fontWeight = FontWeight.Bold, lineHeight = 28.sp,
        fontFeatureSettings = "tnum", fontFamily = mono
    )

    /** 中号读数（卡片角标 / 榜单序号） */
    val stat get() = TextStyle(
        fontSize = TypeScale.stat, fontWeight = FontWeight.Bold, lineHeight = 26.sp,
        fontFeatureSettings = "tnum", fontFamily = mono
    )

    /** 小号读数（时长 03:42 / 进度 / 日期） */
    val time get() = TextStyle(
        fontSize = TypeScale.caption, fontWeight = FontWeight.Normal, lineHeight = 14.sp,
        fontFeatureSettings = "tnum", fontFamily = mono
    )

    /** 辅助读数标签 */
    val label get() = TextStyle(
        fontSize = TypeScale.label, fontWeight = FontWeight.Medium, lineHeight = 16.sp,
        letterSpacing = 0.5.sp, fontFeatureSettings = "tnum", fontFamily = mono
    )
}

/**
 * 语义字号刻度（唯一真源）。
 *
 * 界面只允许取这里的档位，杜绝 `15.5.sp` / `9.5.sp` 这类随手字号造成的层级漂移；
 * 中英文共用同一刻度，英文变长靠 `maxLines` + `TextOverflow.Ellipsis` 兜底，**不靠缩字号**。
 */
object TypeScale {
    val micro = 9.sp          // 角标 / 周次徽标
    val tiny = 10.sp          // 表格密集信息
    val caption = 11.sp       // 说明文字
    val label = 12.sp         // 标签 / 按钮
    val body = 13.sp          // 正文
    val bodyLarge = 14.sp     // 强调正文
    val subtitle = 15.sp      // 卡片副标题
    val title = 16.sp         // 卡片标题
    val section = 17.sp       // 区块标题
    val headline = 18.sp      // 页面小标题
    val coverTitle = 20.sp    // 封面主标题
    val stat = 20.sp          // 统计大数字
    val page = 22.sp          // 页面大标题
    val display = 24.sp       // 展示标题
    val hero = 30.sp          // 首页 Hero
    // 车机导航专用：1080 逻辑画布上，引导距离与速度表需要压过所有卡片层级。
    // 只加这两个语义档，不允许页面再写 34.sp / 52.sp 这类随手值。
    val navMid = 26.sp        // 剩余时间 / 距离、高速出口号
    val navBig = 40.sp        // 大引导距离、圆速度表
}

/**
 * 浮层 / 液态玻璃专用排版：带完整行高、字距、全局字体族与**不透明**文字色。
 *
 * `LiquidGlassSurface` 会把 [GlassType.body] 注入 `LocalTextStyle`，
 * 于是玻璃内没有显式给 `color` 的 Text（输入框 label、placeholder、按钮文案等）
 * 自动获得达标对比度，而不是继承一个半透明或反色失败的值。
 */
object GlassType {
    /** 玻璃内默认正文 */
    val body get() = Type.body.copy(color = Ink.OnGlass)
    /** 玻璃内卡片标题 */
    val title get() = Type.cardTitle.copy(color = Ink.OnGlass)
    /** 玻璃内区块标题 */
    val section get() = Type.sectionTitle.copy(color = Ink.OnGlass)
    /** 玻璃内次要说明 */
    val caption get() = Type.caption.copy(color = Ink.OnGlassSub)
    /** 玻璃内最弱一档，仍保持 0.84 alpha 下限 */
    val muted get() = Type.caption.copy(color = Ink.OnGlassMuted)
    /** 玻璃内标签 / 按钮 */
    val label get() = AppTypography.labelLarge.copy(fontFamily = Type.font(), color = Ink.OnGlass)
    /** 玻璃内小胶囊 */
    val chip get() = AppTypography.labelMedium.copy(
        fontFamily = Type.font(), fontSize = TypeScale.caption, color = Ink.OnGlass
    )
    /** 封面主标题（白字 + 紧凑字距 + 明确行高） */
    val coverTitle get() = Type.sectionTitle.copy(
        fontSize = TypeScale.coverTitle,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
        color = Ink.OnCover
    )
}

/** 把自定义字体套到 Material Typography 的全部层级。 */
fun Typography.withFontFamily(family: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family)
)
