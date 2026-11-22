package com.example.my.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 卡片轻阴影。
 *
 * 成熟原生应用用中性黑低透明投影分层，不再用粉橙彩色投影；双层叠加合并为单层，
 * 浮起感更克制，换浅色/深色/自定义背景都不发彩色光晕。
 */
private val ShadowSpot = Color.Black.copy(alpha = 0.10f)
private val ShadowAmbient = Color.Black.copy(alpha = 0.06f)

fun Modifier.cardShadow(shape: Shape = RoundedCornerShape(16.dp)): Modifier = this.shadow(
    elevation = 3.dp,
    shape = shape,
    spotColor = ShadowSpot,
    ambientColor = ShadowAmbient
)

/** 大卡片/轮播中等阴影 */
fun Modifier.cardShadowMedium(): Modifier = this.shadow(
    elevation = 2.dp,
    shape = RoundedCornerShape(20.dp),
    spotColor = ShadowSpot,
    ambientColor = ShadowAmbient
)
