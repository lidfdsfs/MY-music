package com.example.my.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.OfflineManager
import com.example.my.state.AppState
import com.example.my.ui.components.AppTopBar
import com.example.my.ui.components.BackActionRow
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.MainGradient
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.my.ui.i18n.tr

/**
 * 离线旅行设置：
 * 把攻略 / 足迹 / 行程 / 出行计划打包进本地缓存，展示地图瓦片占用，
 * 并提供缓存清理。断网时这些内容仍可阅读，联网后足迹会自动补传云端。
 */
@Composable
fun OfflineScreen(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tileBytes by remember { mutableStateOf(OfflineManager.tileCacheBytes(context)) }
    var lastUpdated by remember { mutableStateOf(OfflineManager.lastUpdated(context)) }
    var online by remember { mutableStateOf(OfflineManager.isOnline(context)) }
    var busy by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Bg.Background)) {
        AppTopBar(title = tr("离线旅行"))
        BackActionRow(onBack = { state.closeScreen() })

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // 网络状态
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Bg.Surface)
                    .cardShadow(RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (online) Color(0xFF2E9E6B).copy(alpha = 0.12f)
                            else Brand.Terracotta.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (online) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
                        null,
                        tint = if (online) Color(0xFF2E9E6B) else Brand.Terracotta,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (online) tr("当前网络正常") else tr("当前处于离线状态"),
                        color = Text.Primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (online) tr("可以随时更新离线内容")
                        else tr("本地内容仍可查看，联网后再更新离线包"),
                        color = Text.Muted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 离线内容
            Text(tr("离线内容"), color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Bg.Surface)
                    .cardShadow(RoundedCornerShape(22.dp))
            ) {
                OfflineRow(tr("攻略"), tr("${state.guides.size} 篇", "${state.guides.size} guides"))
                OfflineDivider()
                OfflineRow(tr("本地足迹"), tr("${state.footprints.size} 条", "${state.footprints.size} footprints"))
                OfflineDivider()
                OfflineRow(tr("旅行回忆"), tr("${state.routes.size} 段", "${state.routes.size} journeys"))
                OfflineDivider()
                OfflineRow(tr("出行计划"), tr("${state.tripPlans.size} 个", "${state.tripPlans.size} plans"))
                OfflineDivider()
                OfflineRow(tr("地图瓦片缓存"), OfflineManager.formatBytes(tileBytes))
            }

            Spacer(Modifier.height(10.dp))
            Text(
                if (lastUpdated > 0L)
                    tr("上次更新：${SimpleDateFormat(tr("M月d日 HH:mm", "MMM d, HH:mm"), Locale.getDefault())
                        .format(Date(lastUpdated))}",
                        "Updated ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(lastUpdated))}")
                else tr("还没有更新过离线内容"),
                color = Text.Muted,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(16.dp))

            // 更新离线内容
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50.dp))
                    .background(Brush.linearGradient(MainGradient))
                    .clickable(enabled = !busy) {
                        busy = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                OfflineManager.updatePack(
                                    context = context,
                                    guides = state.guides,
                                    footprints = state.footprints,
                                    routes = state.routes,
                                    plans = state.tripPlans
                                )
                            }
                            lastUpdated = OfflineManager.lastUpdated(context)
                            tileBytes = OfflineManager.tileCacheBytes(context)
                            online = OfflineManager.isOnline(context)
                            busy = false
                            state.toast(tr("离线内容已更新，断网也能查看"))
                        }
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Download, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    if (busy) tr("正在保存…") else tr("更新离线内容"),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(10.dp))

            // 清除地图缓存
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50.dp))
                    .background(Bg.Surface)
                    .clickable {
                        scope.launch {
                            withContext(Dispatchers.IO) { OfflineManager.clearMapTiles(context) }
                            tileBytes = OfflineManager.tileCacheBytes(context)
                            state.toast(tr("地图缓存已清除"))
                        }
                    }
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.DeleteOutline, null, tint = Text.Secondary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("清除地图缓存"), color = Text.Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(22.dp))

            // 说明
            Text(tr("旅行时会发生什么"), color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OfflineTip(tr("足迹 / 行程 / 攻略都保存在手机本地，飞行模式下也能查看"))
            OfflineTip(tr("地图浏览过的区域会自动缓存成瓦片，离线时仍可拖动查看"))
            OfflineTip(tr("离线记录的足迹一落地就存在本机，联网后自动补全城市与天气"))
            OfflineTip(tr("换手机前建议先「更新离线内容」，避免临时没网看不到资料"))

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Bg.SurfaceVariant)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Map, null, tint = Text.Muted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    tr("离线地图是浏览自动缓存，不提供整块区域批量下载；出发前多拖动查看目的地周边即可缓存更多瓦片。"),
                    color = Text.Muted,
                    fontSize = 11.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun OfflineRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Text.Primary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Text.Secondary, fontSize = 12.sp)
    }
}

@Composable
private fun OfflineDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(Bg.Outline)
    )
}

@Composable
private fun OfflineTip(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            null,
            tint = Brand.Terracotta.copy(alpha = 0.75f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = Text.Secondary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}
