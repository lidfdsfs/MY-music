package com.example.my.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.AiFeature
import com.example.my.model.Route
import com.example.my.state.AppState
import com.example.my.ui.components.AiInsightCard
import com.example.my.ui.components.GradientCover
import com.example.my.ui.i18n.tr
import com.example.my.ui.interaction.PhotoDrawerSheet
import com.example.my.ui.interaction.TearOffPanel
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Func
import com.example.my.ui.theme.Text
import com.example.my.util.PhotoStore
import com.example.my.util.cleanNote

/**
 * 足迹详情（旅行回忆/地图/个人页/旅行人榜 共用的唯一一版）。
 *
 * 原来那套「封面 + 四列统计卡 + 这段旅程卡 + 公开足迹卡 + 操作列表卡」的页面已经删掉了：
 * 白卡片一张叠一张、阴影互压，看起来糊成一块。这里改成和单个足迹完全一致的抽屉样式
 * （照片在上、内容从下往上托起），入口全部收口到这一个 Composable。
 */
@Composable
fun RouteMemorySheet(state: AppState, route: Route) {
    val photos = routeCoverPhotos(route, state.footprints)
    var showInsightModelPicker by remember { mutableStateOf(false) }

    BackHandler { state.closeRoute() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Func.Scrim)
            .clickable(enabled = true, onClick = { state.closeRoute() })
    ) {
        PhotoDrawerSheet(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {},
            onPhotoPullDown = null,
            photo = {
                if (photos.isNotEmpty()) {
                    AutoPhotoCarousel(
                        photos = photos,
                        modifier = Modifier.fillMaxSize(),
                        dim = PhotoStore.DETAIL_DIM,
                        showIndicator = true,
                        fallback = {
                            GradientCover(
                                route.coverSeed,
                                Modifier.fillMaxSize(),
                                RoundedCornerShape(0.dp),
                                coverResId = route.coverResId
                            )
                        }
                    )
                } else {
                    GradientCover(
                        route.coverSeed,
                        Modifier.fillMaxSize(),
                        RoundedCornerShape(0.dp),
                        coverResId = route.coverResId
                    )
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, bottom = 28.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = route.title,
                        color = Text.Primary,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { state.closeRoute() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            null,
                            tint = Text.Secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = (route.cities + route.date + tr("${route.days} 天", "${route.days} days"))
                        .joinToString(" · "),
                    color = Text.Secondary,
                    fontSize = 13.sp
                )

                if (route.note.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = route.note,
                        color = Text.Primary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }

                if (route.tags.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        route.tags.take(4).forEach { tag ->
                            Text(
                                text = "#$tag",
                                color = Brand.Terracotta,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(Bg.SurfaceVariant)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // 旧版「公开足迹开关 + 生成攻略 + 回到足迹地图」区块已删：
                // 公开/私密切换在「我的页 · 私密行程」里操作，地图入口底部导航直达，
                // 这里只留内容和 AI 总结，与单条足迹详情保持同一套样式。
                // 和单个足迹同一张 AI 总结卡：只归纳已有记录，不打分、不评价
                AiInsightCard(
                    kind = "足迹",
                    title = route.title,
                    facts = buildString {
                        append("城市：").append(route.cities.joinToString("、")).append("；")
                        append("日期：").append(route.date).append("；")
                        append("天数：").append(route.days).append(" 天；")
                        if (route.note.isNotBlank()) {
                            append("备注：").append(cleanNote(route.note)).append("；")
                        }
                        if (route.tags.isNotEmpty()) {
                            append("标签：").append(route.tags.joinToString("、")).append("；")
                        }
                        append("照片数量：").append(route.photoCount).append(" 张")
                    },
                    endpoint = state.aiFeatureEndpoint(AiFeature.INSIGHT),
                    context = LocalContext.current,
                    useLocal = state.aiFeatureUsesLocal(AiFeature.INSIGHT),
                    localModelId = state.aiFeatureLocalModelId(AiFeature.INSIGHT),
                    onModelLongPress = { showInsightModelPicker = true }
                )
                Spacer(Modifier.height(18.dp))
                // 撕线票根，和足迹详情保持一致
                TearOffPanel(code = "NO.%06d".format(route.id % 1000000), notchColor = Bg.Surface)
                Spacer(Modifier.height(16.dp))
            }
        }
        if (showInsightModelPicker) {
            AiModelQuickSwitchDialog(
                feature = AiFeature.INSIGHT,
                state = state,
                onDismiss = { showInsightModelPicker = false }
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Bg.Outline)
    )
}

@Composable
private fun SheetActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Text.Primary, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Text(text = label, color = Text.Primary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            null,
            tint = Text.Muted,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 赤陶样式开关 */
@Composable
private fun AppSwitch(checked: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .scale(if (pressed.value) 0.94f else 1f)
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(if (checked) Brand.Terracotta else Bg.SurfaceVariant)
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
