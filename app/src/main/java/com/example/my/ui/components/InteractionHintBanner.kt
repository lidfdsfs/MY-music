package com.example.my.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.ui.interaction.pressBounceScale
import com.example.my.ui.interaction.rememberTapRipple
import com.example.my.ui.interaction.tapRipple
import com.example.my.ui.theme.Terracotta
import com.example.my.ui.theme.TerracottaLight
import com.example.my.ui.i18n.tr

/**
 * 新交互一次性引导条：首次进入页面时显示，点击「知道了」后关闭，
 * 通过 SharedPreferences 持久化，跨会话不再重复打扰。
 * 用于提示那些「不做就发现不了」的高级手势（卡堆滑动 / 边界阻尼等）。
 */
@Composable
fun InteractionHintBanner(
    text: String,
    prefsKey: String,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    var dismissed by rememberSaveable(prefsKey) {
        mutableStateOf(prefs.getBoolean(prefsKey, false))
    }
    if (dismissed) return

    val interaction = remember { MutableInteractionSource() }
    val ripple = rememberTapRipple()
    val press = pressBounceScale(interaction, 0.97f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(press)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(TerracottaLight, Terracotta)))
            .tapRipple(ripple, onClick = {
                prefs.edit().putBoolean(prefsKey, true).apply()
                dismissed = true
            })
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(tr("知道了"), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
    }
}
