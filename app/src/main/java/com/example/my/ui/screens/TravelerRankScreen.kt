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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.model.Route
import com.example.my.state.AppState
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow
import com.example.my.ui.i18n.tr

/** 旅行达人：多维度排行榜入口页 */
@Composable
fun TravelerRankScreen(state: AppState, onBack: () -> Unit) {
    // 计算各维度榜单
    val citiesRank = remember(state.routes) {
        state.routes.sortedByDescending { it.cities.size }.take(10)
    }
    val daysRank = remember(state.routes) {
        state.routes.sortedByDescending { it.days }.take(10)
    }
    val photosRank = remember(state.routes) {
        state.routes.sortedByDescending { it.photoCount }.take(10)
    }
    val specialRank = remember(state.footprints) {
        state.footprints.filter { it.special }.sortedByDescending { it.name }.take(10)
    }
    val totalCities = state.footprints.map { it.city }.distinct().size
    val totalDays = state.footprints
        .map { it.dateMillis?.div(86_400_000L)?.toString() ?: it.date }
        .distinct().size
    val totalPhotos = state.footprints.sumOf { it.photoPaths.size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg.Background)
            .statusBarsPadding()
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, tr("返回"), tint = Text.Primary)
            }
            Spacer(Modifier.weight(1f))
            Text(tr("旅行达人"), color = Text.Primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
        ) {
            // 个人总览
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brand.Terracotta.copy(alpha = 0.08f))
                        .padding(20.dp)
                ) {
                    Text(tr("我的旅行足迹"), color = Text.Primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("$totalCities", tr("座城市"), Modifier.weight(1f))
                        StatItem("$totalDays", tr("天旅程"), Modifier.weight(1f))
                        StatItem("$totalPhotos", tr("张照片"), Modifier.weight(1f))
                    }
                }
            }

            // 途经城市榜
            item {
                RankSection(
                    title = tr("途经城市榜"),
                    subtitle = tr("按路线经过城市数"),
                    entries = citiesRank.map { route ->
                        RankRow(
                            title = route.title,
                            value = "${route.cities.size} 城",
                            sub = route.cities.take(3).joinToString(" · "),
                            onClick = { state.openRoute(route.id) }
                        )
                    }
                )
            }

            // 旅行天数榜
            item {
                RankSection(
                    title = tr("旅行天数榜"),
                    subtitle = tr("按单次行程天数"),
                    entries = daysRank.map { route ->
                        RankRow(
                            title = route.title,
                            value = "${route.days} 天",
                            sub = route.year.toString(),
                            onClick = { state.openRoute(route.id) }
                        )
                    }
                )
            }

            // 照片数量榜
            item {
                RankSection(
                    title = tr("照片收藏榜"),
                    subtitle = tr("按路线照片数量"),
                    entries = photosRank.map { route ->
                        RankRow(
                            title = route.title,
                            value = "${route.photoCount} 张",
                            sub = route.cities.firstOrNull() ?: "",
                            onClick = { state.openRoute(route.id) }
                        )
                    }
                )
            }

            // 特别回忆榜
            item {
                RankSection(
                    title = tr("特别回忆榜"),
                    subtitle = tr("标记为特别的足迹"),
                    entries = specialRank.map { fp ->
                        RankRow(
                            title = fp.name,
                            value = fp.city,
                            sub = fp.date,
                            onClick = { state.openRoute(0) }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Brand.Terracotta, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Text.Muted, fontSize = 12.sp)
    }
}

private data class RankRow(
    val title: String,
    val value: String,
    val sub: String,
    val onClick: () -> Unit
)

@Composable
private fun RankSection(title: String, subtitle: String, entries: List<RankRow>) {
    if (entries.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(subtitle, color = Text.Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Bg.Surface)
                .cardShadow()
                .padding(vertical = 4.dp)
        ) {
            entries.forEachIndexed { i, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { row.onClick() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (i < 3) Brand.Gold else Text.Muted.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${i + 1}",
                            color = if (i < 3) Color.White else Text.Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            row.title,
                            color = Text.Primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(row.sub, color = Text.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(row.value, color = Brand.Terracotta, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
