package com.example.my.ui.screens

import androidx.activity.compose.BackHandler
import com.example.my.data.music.MusicPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.ChinaMapData
import com.example.my.state.AppState
import com.example.my.ui.components.AppTopBar
import com.example.my.ui.components.BackActionRow
import com.example.my.ui.interaction.AnimatedNumber
import com.example.my.ui.interaction.GrowingBarChart
import com.example.my.ui.interaction.rememberHapticFeedback
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.Type
import com.example.my.ui.theme.cardShadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.example.my.util.GeoPoint as AlgoGeoPoint
import com.example.my.util.PosterRenderer
import com.example.my.util.YearReportData
import com.example.my.util.clusterSummary
import com.example.my.util.haversineKm
import com.example.my.util.tspOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import com.example.my.ui.i18n.tr

/**
 * 年度足迹报告：把散落的足迹与路线汇总成一份「旅行年报」。
 * 全部离线计算：里程（TSP 顺路 + Haversine）、城市数、月度分布、片区聚类（DBSCAN）、旅行人格。
 */
/**
 * 年报的全部展示数据。页面与导出的长图共用这一份计算结果，
 * 保证「所见即所得」——不会出现页面上是一个数、导出图里是另一个数的情况。
 */
fun computeYearReport(state: AppState, year: Int): YearReportData {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val fps = state.footprints.filter { yearOf(it.date, currentYear) == year }
    val routes = state.routes.filter { it.year == year }
    // 里程：坐标转经纬度后用 TSP 顺路排序，再累加球面距离
    val totalKm = if (fps.size < 2) 0 else runCatching {
        val pts = fps.distinctBy { "${it.x},${it.y}" }.map { fp ->
            val (lon, lat) = ChinaMapData.mapToLonLat(fp.x, fp.y)
            AlgoGeoPoint(lat, lon)
        }
        val ordered = tspOrder(pts)
        ordered.zipWithNext().sumOf { (a, b) -> haversineKm(a, b) }.toInt()
    }.getOrDefault(0)
    val cities = (fps.map { it.city } + routes.flatMap { it.cities })
        .filter { it.isNotBlank() }.distinct()
    val tripDays = routes.sumOf { it.days }.let { if (it > 0) it else fps.size }
    val months = IntArray(12).also { arr ->
        fps.forEach { fp -> monthOf(fp.date)?.let { m -> arr[m - 1]++ } }
    }
    val areas = runCatching {
        clusterSummary(fps.map { fp ->
            val (lon, lat) = ChinaMapData.mapToLonLat(fp.x, fp.y)
            AlgoGeoPoint(lat, lon, fp.city)
        })
    }.getOrDefault(emptyList())
    val highlights = run {
        val fpNames = fps.filter { it.special }.map { it.name }
        val routeNames = routes.filter { it.special }.map { it.specialName ?: it.title }
        (fpNames + routeNames).distinct().take(5)
    }
    val persona = when {
        cities.size >= 8 -> tr("城市漫游者")
        totalKm >= 5000 -> tr("长途旅行家")
        highlights.size >= 2 -> tr("高光收藏家")
        tripDays >= 30 -> tr("慢旅行家")
        else -> tr("轻装出发者")
    }
    return YearReportData(
        year = year,
        totalKm = totalKm,
        cityCount = cities.size,
        footprintCount = fps.size,
        tripDays = tripDays,
        highlightCount = highlights.size,
        persona = persona,
        months = months,
        areas = areas,
        highlights = highlights,
        cities = cities
    )
}

@Composable
fun YearReportScreen(state: AppState) {
    BackHandler { state.closeScreen() }

    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val years = remember(state.footprints, state.routes) {
        (state.routes.map { it.year } +
                state.footprints.map { yearOf(it.date, currentYear) } +
                currentYear)
            .distinct()
            .sortedDescending()
    }
    var year by remember(years) { mutableStateOf(years.firstOrNull() ?: currentYear) }

    val data = remember(state.footprints, state.routes, year) { computeYearReport(state, year) }
    var exporting by remember { mutableStateOf(false) }
    var poster by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    fun export() {
        if (exporting) return
        scope.launch {
            exporting = true
            val bmp = withContext(Dispatchers.Default) {
                runCatching { PosterRenderer.renderYearReport(data) }.getOrNull()
            }
            exporting = false
            if (bmp == null) state.toast(tr("长图生成失败，请重试"))
            else poster = bmp
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg.Background)
            .navigationBarsPadding()
    ) {
        // 标题栏只放标题文字；返回与导出按钮都在内容区
        AppTopBar(title = tr("年度足迹报告"))
        BackActionRow(
            onBack = { state.closeScreen() },
            actions = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = !exporting) { export() }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (exporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Brand.Terracotta
                        )
                    } else {
                        Icon(Icons.Outlined.IosShare, tr("导出长图"), tint = Brand.Terracotta, modifier = Modifier.size(21.dp))
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // 年份切换
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(years, key = { it }) { y ->
                        val selected = y == year
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) Brand.Terracotta else Bg.Surface)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    year = y
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                tr("${y} 年", "${y}"),
                                color = if (selected) Color.White else Text.Secondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 主视觉卡片
            item {
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Brand.TerracottaLight, Brand.Terracotta, Brand.TerracottaDark)
                            )
                        )
                ) {
                    // 顶部一道柔和高光，给主视觉一点「呼吸感」（纯色线性渐变，不画任何圆团）
                    AuroraOverlay(Modifier.fillMaxSize())
                    Column(Modifier.padding(20.dp)) {
                        Text(tr("$year 年，我一共走了", "In $year, I travelled"), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.Bottom) {
                            AnimatedNumber(
                                value = data.totalKm,
                                color = Color.White,
                                fontSize = 42,
                                suffix = " "
                            )
                            Text("km", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            HeroStat("${data.cityCount}", tr("座城市", "cities"))
                            HeroStat("${data.footprintCount}", tr("个足迹", "footprints"))
                            HeroStat("${data.tripDays}", tr("天在路上", "days away"))
                            HeroStat("${data.highlightCount}", tr("个高光", "highlights"))
                        }
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.18f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(tr("我的旅行人格：${data.persona}", "My travel persona: ${data.persona}"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // 月度分布
            item {
                Spacer(Modifier.height(20.dp))
                SectionCard(title = tr("足迹分布"), subtitle = tr("按月份统计今年记录的足迹")) {
                    // 数据随滚动生长：进屏才触发，柱子从零长高、柱顶数字同步滚动
                    GrowingBarChart(
                        values = data.months.toList(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("1月", "3月", "6月", "9月", "12月").forEach {
                            Text(tr(it), color = Text.Muted, fontSize = 10.sp)
                        }
                    }
                }
            }

            // 旅行片区（DBSCAN 聚类）
            if (data.areas.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    SectionCard(title = tr("常去的片区"), subtitle = tr("按地理密度聚类，看看今年的主场")) {
                        val maxArea = data.areas.maxOf { it.second }.coerceAtLeast(1)
                        data.areas.take(5).forEachIndexed { idx, (city, count) ->
                            // 进度条进屏后从 0 生长到真实占比，仅在展示层做动画，不改变 count/maxArea
                            val frac = remember { Animatable(0f) }
                            LaunchedEffect(Unit) {
                                delay(idx * 120L)
                                frac.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 300f))
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(city, color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Bg.SurfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((count.toFloat() / maxArea) * frac.value)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                Brush.horizontalGradient(listOf(Brand.Gold, Brand.Terracotta))
                                            )
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(tr("$count 次", "$count times"), color = Text.Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 高光时刻
            item {
                Spacer(Modifier.height(16.dp))
                SectionCard(
                    title = tr("高光时刻"),
                    subtitle = if (data.highlights.isEmpty()) "今年还没有点亮特别足迹" else tr("这些时刻值得被记住")
                ) {
                    if (data.highlights.isEmpty()) {
                        Text(
                            tr("在足迹里标记「特别」后，会出现在这里"),
                            color = Text.Muted, fontSize = 12.sp
                        )
                    } else {
                        data.highlights.forEachIndexed { i, name ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${i + 1}".padStart(2, '0'), color = Brand.Terracotta, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(10.dp))
                                Text(name, color = Text.Primary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(tr("特别"), color = Brand.Gold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // 城市清单
            item {
                Spacer(Modifier.height(16.dp))
                SectionCard(title = tr("去过的地方"), subtitle = tr("${data.cityCount} 座城市 / 地区", "${data.cityCount} cities / regions")) {
                    if (data.cities.isEmpty()) {
                        Text(tr("今年还没有足迹记录，去地图上点亮第一个吧"), color = Text.Muted, fontSize = 12.sp)
                    } else {
                        Text(
                            data.cities.joinToString(" · "),
                            color = Text.Secondary, fontSize = 13.sp, lineHeight = 21.sp
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(28.dp)) }

            // 听歌年报：播放器统计（全部时间），与足迹年报分开算、分开标
            item {
                Spacer(Modifier.height(6.dp))
                ListenReportCard()
            }

            item { Spacer(Modifier.height(28.dp)) }
        }
    }

        poster?.let { bmp ->
            ExportSheet(
                bitmap = bmp,
                title = tr("MY足迹年报$year", "MY足迹 · Year in Review $year"),
                onDismiss = { poster = null },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
    }
}

/**
 * 主视觉卡片顶部一道柔和高光：纯白从上往下渐隐，纯装饰，不画任何圆团/深色块。
 */
@Composable
private fun AuroraOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.22f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = size.height * 0.55f
            )
        )
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Bg.Surface)
            .cardShadow(RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Text(title, color = Text.Primary, style = Type.sectionTitle)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = Text.Muted, fontSize = 11.sp)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

/** 从 "2024-05-12" / "2024年5月" 中解析年份 */
private fun yearOf(date: String, fallback: Int): Int =
    date.take(4).toIntOrNull() ?: fallback

/**
 * 听歌年报卡片：数据来自 MusicPlayer 的收听统计（全部时间累计）。
 * 数据为空时如实说「还没数据」，不摆一个 0 的空架子。
 */
@Composable
private fun ListenReportCard() {
    val stats = MusicPlayer.listenReport()
    val hasData = MusicPlayer.listenHasData && (stats.totalMs > 0L || stats.totalPlays > 0)
    SectionCard(
        title = tr("听歌年报", "Listening year"),
        subtitle = tr("音乐页开播以来的累计数据 · 与足迹分开统计", "All-time stats from the player")
    ) {
        if (!hasData) {
            Text(
                tr("在音乐页听一会儿歌，这份报告就会长出来", "Play some music and this report grows"),
                color = Text.Muted, fontSize = 12.sp
            )
            return@SectionCard
        }
        // 大数字：累计收听时长
        val hours = stats.totalMs / 3_600_000L
        val minutes = (stats.totalMs % 3_600_000L) / 60_000L
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (hours > 0) tr("$hours 小时 $minutes 分钟", "$hours h $minutes min")
                else tr("$minutes 分钟", "$minutes min"),
                color = Brand.Terracotta,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(tr("累计收听", "Total listening"), color = Text.Muted, fontSize = 11.sp)
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HeroStat("${stats.totalPlays}", tr("次开播", "plays"))
            HeroStat("${stats.nightPlays}", tr("次深夜", "late-night"))
            HeroStat("${stats.streakDays}", tr("天连听", "day streak"))
            HeroStat("${stats.activeDays}", tr("天在听", "active days"))
        }
        // 歌手榜：按播放次数 Top5，进度条复用年报的横条语言
        if (stats.topArtists.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            val maxCount = stats.topArtists.maxOf { it.second }.coerceAtLeast(1)
            stats.topArtists.forEachIndexed { idx, (artist, count) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${idx + 1}".padStart(2, '0'),
                        color = Brand.Terracotta,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        artist,
                        color = Text.Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(0.8f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Bg.SurfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(count.toFloat() / maxCount)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(Brush.horizontalGradient(listOf(Brand.Gold, Brand.Terracotta)))
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(tr("$count 次", "$count"), color = Text.Muted, fontSize = 12.sp)
                }
            }
            // 一句话总结：数据说话，不编修辞
            Spacer(Modifier.height(10.dp))
            val top = stats.topArtists.first()
            Text(
                tr(
                    "陪你最多的是「${top.first}」，一共点播 ${top.second} 次" +
                        if (stats.nightPlays > 0) "，其中有 ${stats.nightPlays} 次在深夜。" else "。"
                    ,
                    "Your most-played artist is \"${top.first}\" with ${top.second} plays" +
                        if (stats.nightPlays > 0) ", ${stats.nightPlays} of them late at night." else "."
                ),
                color = Text.Secondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

/** 从 "2024-05-12" / "2024年5月" 中解析月份（1..12） */
private fun monthOf(date: String): Int? =
    Regex("""\d{4}[-年.](\d{1,2})""").find(date)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..12 }
