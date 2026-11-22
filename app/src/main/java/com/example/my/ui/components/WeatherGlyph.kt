package com.example.my.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 天气图标：把旧数据里的 emoji（☀️/🌧…）和中文描述统一映射成矢量图标。
 *
 * 这样接口、缓存、演示数据都可以继续存原来的字符串，
 * 只有渲染层换掉，不会影响已落盘的天气记录。
 */
object WeatherGlyph {

    fun iconOf(code: String?): ImageVector {
        val s = code.orEmpty()
        return when {
            s.contains("雷") || s.contains("⛈") -> Icons.Outlined.Thunderstorm
            s.contains("雪") || s.contains("❄") || s.contains("🌨") -> Icons.Outlined.AcUnit
            s.contains("雾") || s.contains("霾") || s.contains("🌫") -> Icons.Outlined.FilterDrama
            s.contains("风") || s.contains("💨") -> Icons.Outlined.Air
            s.contains("雨") || s.contains("🌧") || s.contains("🌦") || s.contains("☂") -> Icons.Outlined.WaterDrop
            s.contains("多云") || s.contains("间晴") || s.contains("⛅") || s.contains("🌤") -> Icons.Outlined.WbCloudy
            s.contains("阴") || s.contains("☁") -> Icons.Outlined.Cloud
            s.contains("晴") || s.contains("☀") -> Icons.Outlined.WbSunny
            else -> Icons.Outlined.WbCloudy
        }
    }
}

@Composable
fun WeatherIcon(code: String?, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    Icon(WeatherGlyph.iconOf(code), contentDescription = null, tint = tint, modifier = modifier)
}
