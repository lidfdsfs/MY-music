package com.example.my.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.R
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.SplashAccent
import com.example.my.ui.theme.SplashInk
import com.example.my.ui.theme.SplashInkFaint
import com.example.my.ui.theme.SplashInkSub
import kotlinx.coroutines.delay

/**
 * 启动页：薄荷白 → 淡蓝渐变背景 + 白色圆角方块内嵌品牌 Logo（呼吸柔光）+
 * 品牌名与那句「心底藏着美好与信仰」；1.6 秒后自动进入，点击立即进入。
 */
@Composable
fun SplashScreen(onFinish: () -> Unit) {
    LaunchedEffect(Unit) {
        // 本地模式数据全在本机，没有网络要等：1.6 秒够看清品牌与两行文案
        delay(1600)
        onFinish()
    }
    val breathe = rememberInfiniteTransition(label = "logoBreathe")
    val glowScale by breathe.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1800), repeatMode = RepeatMode.Reverse),
        label = "glowScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF4FBF8), Color(0xFFEDF4FE), Color(0xFFF7FCFA))
                )
            )
            .clickable(onClick = onFinish),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Logo 区块
            Box(contentAlignment = Alignment.Center) {
                // 柔光光晕：绿到蓝，与 Logo 渐变同源
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(glowScale)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFF4FD48F).copy(alpha = 0.30f),
                                    Color(0xFF2E6BFF).copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                // 白色圆角方块（与桌面图标同一视觉）
                Box(
                    modifier = Modifier
                        .size(124.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(38.dp),
                            spotColor = Color(0xFF2E6BFF).copy(alpha = 0.20f),
                            ambientColor = Color(0xFF4FD48F).copy(alpha = 0.14f)
                        )
                        .clip(RoundedCornerShape(38.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.brand_logo),
                        contentDescription = tr("MY音乐 Logo", "MY Music logo"),
                        modifier = Modifier.size(78.dp)
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            // 启动页文案全部固定字体与固定色：不跟随设置里的字体/文字色，
            // 保证品牌名和那句蓝绿标语在任何自定义配置下都保持设计原样
            Text(
                text = tr("MY音乐", "MY Music"),
                color = SplashInk,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.SansSerif
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = tr(
                    "心底藏着美好与信仰，MY音乐，是属于你的一方净土。",
                    "Beauty and faith live in your heart — MY Music is your own quiet land."
                ),
                color = SplashInkSub,
                fontSize = 13.5.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = tr(
                    "在这里重拾宁静，让美好成为耳边日常。",
                    "Find your calm here, and let beauty play on repeat."
                ),
                color = SplashAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }

        Text(
            text = "MY MUSIC",
            color = SplashInkFaint,
            fontSize = 11.sp,
            letterSpacing = 4.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
        )
    }
}
