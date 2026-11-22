package com.example.my.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.R
import com.example.my.state.AppState
import com.example.my.state.AppTab
import com.example.my.ui.interaction.pressBounceScale
import com.example.my.ui.interaction.rememberHapticFeedback
import com.example.my.ui.i18n.tr
import com.example.my.ui.interaction.rememberTapRipple
import com.example.my.ui.interaction.tapRipple
import com.example.my.ui.theme.BorderLight
import com.example.my.ui.theme.Terracotta
import com.example.my.ui.theme.TextSecondary
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

private data class TabSpec(
    val tab: AppTab?,
    val label: String,
    val icon: ImageVector? = null,
    val iconRes: Int? = null
)

private val leftTabs = listOf(
    TabSpec(AppTab.DISCOVER, tr("发现"), Icons.Outlined.Explore),
    // 原「足迹」Tab 已升级为抖音式公开足迹流；个人地图/回忆收进「我的」页
    TabSpec(AppTab.MAP, tr("微足迹", "Micro"), Icons.Outlined.PlayCircle)
)
private val rightTabs = listOf(
    TabSpec(AppTab.TRIPS, tr("行程"), Icons.Outlined.CalendarMonth),
    TabSpec(AppTab.PROFILE, tr("我的"), Icons.Outlined.PersonOutline)
)

/** 底部 5 位导航：毛玻璃底 + 中间突出渐变 +号（不切换 Tab），选中项弹跳动画 */
@Composable
fun MyBottomBar(state: AppState, onUpload: () -> Unit) {
    // 选中/切换等状态型交互的轻震反馈
    val haptic = rememberHapticFeedback()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 液态玻璃顶栏：只在这一层画玻璃，内部图标/文字仍是实色，不做任何嵌套堆叠。
            // 设备不支持或用户关闭时，liquidGlassBackground 自动退成半透明底色。
            .liquidGlassBackground(0.dp, LiquidGlassStyles.Bar)
            .border(width = 1.dp, color = BorderLight)
            .navigationBarsPadding()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        leftTabs.forEach { TabItem(it, state.activeTab, onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            state.activeTab = it.tab!!
        }) }
        // 中间突出 +号（按压回弹 + 水波）
        val plusInteraction = remember { MutableInteractionSource() }
        val plusRipple = rememberTapRipple()
        val plusScale = pressBounceScale(plusInteraction, 0.9f)
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(plusScale)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Terracotta, com.example.my.ui.theme.TerracottaDark)))
                .tapRipple(plusRipple, onClick = onUpload),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, tr("上传足迹"), tint = Color.White, modifier = Modifier.size(30.dp))
        }
        rightTabs.forEach { TabItem(it, state.activeTab, onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            state.activeTab = it.tab!!
        }) }
    }
}

@Composable
private fun TabItem(spec: TabSpec, activeTab: AppTab, onClick: () -> Unit) {
    val selected = spec.tab == activeTab
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "tabScale"
    )
    val interaction = remember { MutableInteractionSource() }
    val ripple = rememberTapRipple()
    val press = pressBounceScale(interaction, 0.92f)
    Column(
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .tapRipple(ripple, color = Terracotta.copy(alpha = 0.16f), onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (selected) Terracotta.copy(alpha = 0.14f) else Color.Transparent)
                .scale(scale * press),
            contentAlignment = Alignment.Center
        ) {
            val iconTint = if (selected) Terracotta else TextSecondary
            val iconSize = if (selected) 28.dp else 25.dp
            if (spec.iconRes != null) {
                Icon(
                    painter = painterResource(spec.iconRes),
                    contentDescription = tr(spec.label),
                    tint = iconTint,
                    modifier = Modifier.size(iconSize)
                )
            } else {
                Icon(
                    imageVector = spec.icon!!,
                    contentDescription = tr(spec.label),
                    tint = iconTint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        // 微信式文字标签。英文比中文长，这里给足两行空间并显式省略，
        // 否则 "Footprints" 这类词会被 maxLines=1 直接硬切掉结尾
        Spacer(Modifier.height(1.dp))
        Text(
            text = tr(spec.label),
            color = if (selected) Terracotta else TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
