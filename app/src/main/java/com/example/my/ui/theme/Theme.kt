package com.example.my.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 主题模式：0=跟随系统，1=浅色，2=深色。设置页修改会写回这里，整 App 即时换肤。 */
var themeMode by mutableStateOf(0)

private val Color_TerracottaContainer = Color(0xFFFBE9E2)
private val Color_TealSoft = Color(0xFFDCF0EC)
private val Color_GoldSoft = Color(0xFFFBF0D3)
private val Color_ContainerLowSoft = Color(0xFFF7F2EE)

private val Color_TerracottaContainerDark = Color(0xFF4A2E27)
private val Color_TealSoftDark = Color(0xFF24403B)
private val Color_GoldSoftDark = Color(0xFF4A3F22)
private val Color_ContainerLowSoftDark = Color(0xFF2A2622)

private val LightColorScheme = lightColorScheme(
    primary = Brand.Terracotta,
    onPrimary = Text.Inverse,
    primaryContainer = Color_TerracottaContainer,
    onPrimaryContainer = Brand.TerracottaDark,
    secondary = Brand.Teal,
    onSecondary = Text.Inverse,
    secondaryContainer = Color_TealSoft,
    onSecondaryContainer = Brand.Teal,
    tertiary = Brand.Gold,
    onTertiary = Text.Primary,
    tertiaryContainer = Color_GoldSoft,
    onTertiaryContainer = Brand.TerracottaDark,
    background = Bg.Background,
    onBackground = Text.Primary,
    surface = Bg.Surface,
    onSurface = Text.Primary,
    surfaceVariant = Bg.SurfaceVariant,
    onSurfaceVariant = Text.Secondary,
    surfaceContainerLow = Color_ContainerLowSoft,
    surfaceContainer = Bg.SurfaceVariant,
    surfaceContainerHigh = Bg.SurfaceDim,
    outline = Bg.Outline,
    outlineVariant = Bg.OutlineStrong,
    error = Func.Danger,
    onError = Text.Inverse,
    scrim = Func.Scrim,
    surfaceTint = Brand.Terracotta.copy(alpha = 0.12f)
)

/** 深色（夜间模式）Material 方案：直接用 DarkPalette 静态字段，避免顶部初始化误取浅色 */
private val DarkColorScheme = darkColorScheme(
    primary = DarkPalette.terracotta,
    onPrimary = DarkPalette.textInverse,
    primaryContainer = Color_TerracottaContainerDark,
    onPrimaryContainer = DarkPalette.terracottaLight,
    secondary = DarkPalette.teal,
    onSecondary = DarkPalette.textInverse,
    secondaryContainer = Color_TealSoftDark,
    onSecondaryContainer = DarkPalette.teal,
    tertiary = DarkPalette.gold,
    onTertiary = DarkPalette.textPrimary,
    tertiaryContainer = Color_GoldSoftDark,
    onTertiaryContainer = DarkPalette.terracottaLight,
    background = DarkPalette.background,
    onBackground = DarkPalette.textPrimary,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.textPrimary,
    surfaceVariant = DarkPalette.surfaceVariant,
    onSurfaceVariant = DarkPalette.textSecondary,
    surfaceContainerLow = Color_ContainerLowSoftDark,
    surfaceContainer = DarkPalette.surfaceVariant,
    surfaceContainerHigh = DarkPalette.surfaceDim,
    outline = DarkPalette.outline,
    outlineVariant = DarkPalette.outlineStrong,
    error = DarkPalette.danger,
    onError = DarkPalette.textInverse,
    scrim = DarkPalette.scrim,
    surfaceTint = DarkPalette.terracotta.copy(alpha = 0.12f)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl)
)

/** 品牌主题（沉浸式 edge-to-edge，适配系统栏，随系统深浅色切换） */
@Composable
fun MY足迹Theme(content: @Composable () -> Unit) {
    val sysDark = isSystemInDarkTheme()
    // 0=跟随系统，1=强制浅色，2=强制深色
    val dark = when (themeMode) { 1 -> false; 2 -> true; else -> sysDark }
    val base = if (dark) DarkPalette else LightPalette
    val custom = AppBackground.config
    // 自定义背景开启时：页面底色透明，让根容器的背景图透出来；
    // 高亮色 / 文字色同步换掉语义 token，卡片表面按文字深浅自动反转。
    // 「取色跟随」开着时高亮色取自背景图主色，用户手选的色不生效（设置页有对应说明）。
    val accent = custom.resolvedHighlight(AppBackground.coverPalette)
    val onAccent = if (accent.luminance() > 0.62f) Color(0xFF111111) else Color(0xFFFFFFFF)
    // 文字色跟随系统深浅：用户没改过默认深墨色时，深色模式自动换成浅色墨；
    // 卡片玻璃底色再按这档墨色的明度反推，保证「深字配浅卡、浅字配深卡」。
    val ink = customInk(custom, dark)
    val glassLevel = custom.glass
    val effective = if (!custom.enabled) base else base.copy(
        terracotta = accent,
        terracottaDark = darkenColor(accent),
        terracottaLight = lightenColor(accent),
        background = Color.Transparent,
        surface = surfaceForCustomText(ink, glassLevel),
        surfaceVariant = variantForCustomText(ink, glassLevel),
        surfaceDim = dimForCustomText(ink, glassLevel),
        surfaceGlass = glassForCustomText(ink, glassLevel),
        outline = outlineForCustomText(ink),
        outlineStrong = strongOutlineForCustomText(ink),
        textPrimary = ink,
        // 自定义背景态下文字本身也不能过透：半透明卡片 + 半透明文字会让底层内容透上来叠字。
        // 层级交给字号 / 字重 / 色相（见 TypeScale / GlassType / Ink），文字 alpha 一律 >= 0.82。
        textSecondary = ink.copy(alpha = 0.94f),
        textMuted = ink.copy(alpha = 0.84f),
        textInverse = onAccent
    )
    // 写入当前调色板：语义 token（Brand/Bg/Text/Func 等）读取会自动重组
    AppPalette.value = effective
    AppBaseBackground.value = base.background
    val view = LocalView.current
    if (!view.isInEditMode) {
        // 自定义背景时按文字色判断系统栏图标：深色文字配浅底，用深色图标；
        // 白色文字通常配深底或照片，用浅色图标。
        val useDarkSystemIcons = if (custom.enabled) ink.luminance() < 0.55f else !dark
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = useDarkSystemIcons
                isAppearanceLightNavigationBars = useDarkSystemIcons
            }
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }
    val baseScheme = if (dark) DarkColorScheme else LightColorScheme
    val scheme = if (!custom.enabled) baseScheme else baseScheme.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.20f),
        onPrimaryContainer = ink,
        background = Color.Transparent,
        onBackground = ink,
        surface = effective.surface,
        onSurface = ink,
        surfaceVariant = effective.surfaceVariant,
        // M3 原生槽位也要跟着反转：WebView 页混用了 colorScheme.onErrorContainer /
        // onSurfaceVariant，若不覆盖，自定义背景态下会残留浅色主题的静态暗字。
        onSurfaceVariant = ink.copy(alpha = 0.94f),
        errorContainer = effective.surfaceVariant,
        onErrorContainer = ink,
        surfaceContainerLow = effective.surfaceVariant,
        surfaceContainer = effective.surfaceVariant,
        surfaceContainerHigh = effective.surfaceDim,
        outline = effective.outline,
        outlineVariant = effective.outlineStrong
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography.withFontFamily(
            fontFamilyFor(if (custom.enabled) custom.font else AppFont.SYSTEM)
        ),
        shapes = AppShapes,
        content = content
    )
}
