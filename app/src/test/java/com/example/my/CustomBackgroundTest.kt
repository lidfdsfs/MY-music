package com.example.my

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import com.example.my.ui.theme.AppBackgroundConfig
import com.example.my.ui.theme.AppFont
import com.example.my.ui.theme.AppTypography
import com.example.my.ui.theme.backgroundBrush
import com.example.my.ui.theme.BgColorMode
import com.example.my.ui.theme.BgFill
import com.example.my.ui.theme.BgMint
import com.example.my.ui.theme.colorsForBackground
import com.example.my.ui.theme.fontFamilyFor
import com.example.my.ui.theme.glassForCustomText
import com.example.my.ui.theme.hueOf
import com.example.my.ui.theme.saturationOf
import com.example.my.ui.theme.surfaceForCustomText
import com.example.my.ui.theme.withFontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomBackgroundTest {

    @Test
    fun `default config is warm mint and disabled`() {
        val config = AppBackgroundConfig.Default
        assertFalse(config.enabled)
        assertEquals(BgFill.COLOR, config.fill)
        assertEquals(BgColorMode.SINGLE, config.mode)
        assertEquals(listOf(BgMint), config.activeColors)
    }

    @Test
    fun `active colors follow the selected color mode`() {
        val config = AppBackgroundConfig.Default.copy(
            mode = BgColorMode.TRI,
            colors = listOf(Color.Red, Color.Green, Color.Blue)
        )
        assertEquals(3, config.activeColors.size)
        assertEquals(
            1,
            AppBackgroundConfig.Default.copy(mode = BgColorMode.SINGLE).activeColors.size
        )
    }

    @Test
    fun `color modes generate one two and three colors`() {
        assertEquals(1, colorsForBackground(120f, 0.2f, BgColorMode.SINGLE).size)
        assertEquals(2, colorsForBackground(120f, 0.2f, BgColorMode.DUAL).size)
        assertEquals(3, colorsForBackground(120f, 0.2f, BgColorMode.TRI).size)
    }

    @Test
    fun `hue and saturation round trip from generated color`() {
        val color = colorsForBackground(210f, 0.3f, BgColorMode.SINGLE).first()
        assertEquals(210f, hueOf(color), 1.5f)
        assertEquals(0.3f, saturationOf(color), 0.03f)
    }

    @Test
    fun `hue wraps negative and over 360`() {
        val a = colorsForBackground(-90f, 0.3f, BgColorMode.SINGLE).first()
        assertEquals(270f, hueOf(a), 1.5f)
        val b = colorsForBackground(450f, 0.3f, BgColorMode.SINGLE).first()
        assertEquals(90f, hueOf(b), 1.5f)
    }

    @Test
    fun `single color brush is safe`() {
        // 单色背景不能因为渐变至少两色而崩；内部会复制成两段
        backgroundBrush(listOf(BgMint))
        backgroundBrush(emptyList())
        backgroundBrush(listOf(Color.Red, Color.Blue))
    }

    @Test
    fun `white text gets dark surface and black text keeps light surface`() {
        assertTrue(surfaceForCustomText(Color.White).luminance() < 0.3f)
        assertTrue(surfaceForCustomText(Color(0xFF111111)).luminance() > 0.7f)
    }

    @Test
    fun `custom surfaces are translucent liquid glass`() {
        assertTrue(surfaceForCustomText(Color.White).alpha < 0.6f)
        assertTrue(surfaceForCustomText(Color(0xFF111111)).alpha < 0.6f)
        assertTrue(glassForCustomText(Color.White).alpha < 0.5f)
        assertTrue(glassForCustomText(Color(0xFF111111)).alpha < 0.5f)
    }

    @Test
    fun `font choices map to system families`() {
        assertEquals(FontFamily.SansSerif, fontFamilyFor(AppFont.SYSTEM))
        assertEquals(FontFamily.Serif, fontFamilyFor(AppFont.SERIF))
        assertEquals(FontFamily.Monospace, fontFamilyFor(AppFont.MONO))
        assertEquals(FontFamily.Cursive, fontFamilyFor(AppFont.CURSIVE))
        assertEquals(AppFont.SYSTEM, AppBackgroundConfig.Default.font)
    }

    @Test
    fun `font family applies to every material typography style`() {
        val typography = AppTypography.withFontFamily(FontFamily.Monospace)
        assertEquals(FontFamily.Monospace, typography.displayLarge.fontFamily)
        assertEquals(FontFamily.Monospace, typography.headlineLarge.fontFamily)
        assertEquals(FontFamily.Monospace, typography.bodyLarge.fontFamily)
        assertEquals(FontFamily.Monospace, typography.bodyMedium.fontFamily)
        assertEquals(FontFamily.Monospace, typography.labelSmall.fontFamily)
    }
}
