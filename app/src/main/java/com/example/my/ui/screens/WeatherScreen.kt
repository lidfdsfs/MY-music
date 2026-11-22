package com.example.my.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.my.data.CityHit
import com.example.my.data.DayForecast
import com.example.my.data.WeatherApi
import com.example.my.data.AmapApi
import com.example.my.state.AppState
import com.example.my.state.AppTab
import com.example.my.ui.components.AppTopBar
import com.example.my.ui.components.BackActionRow
import com.example.my.ui.components.WeatherIcon
import com.example.my.ui.components.staggeredIn
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.my.ui.i18n.tr

private data class CityPreset(val name: String, val lat: Double, val lon: Double)

private val cityPresets = listOf(
    CityPreset(tr("重庆"), 29.5630, 106.5516),
    CityPreset(tr("北京"), 39.9042, 116.4074),
    CityPreset(tr("上海"), 31.2304, 121.4737),
    CityPreset(tr("广州"), 23.1291, 113.2644),
    CityPreset(tr("深圳"), 22.5431, 114.0579),
    CityPreset(tr("成都"), 30.5728, 104.0668),
    CityPreset(tr("杭州"), 30.2741, 120.1551),
    CityPreset(tr("西安"), 34.3416, 108.9398),
    CityPreset(tr("三亚"), 18.2528, 109.5119),
    CityPreset(tr("昆明"), 25.0389, 102.7183),
    CityPreset(tr("大理"), 25.6065, 100.2676),
    CityPreset(tr("拉萨"), 29.6520, 91.1721),
    CityPreset(tr("青岛"), 36.0671, 120.3826),
    CityPreset(tr("厦门"), 24.4798, 118.0894),
    CityPreset(tr("哈尔滨"), 45.8038, 126.5350),
    CityPreset(tr("乌鲁木齐"), 43.8256, 87.6168)
)

/** 演示数据（网络异常时兜底，保证页面不空白） */
private fun demoSnapshot(city: String): com.example.my.data.WeatherSnapshot {
    val seed = (city.hashCode() % 6 + 6) % 6
    val conds = listOf(
        Triple("阴", "阴", 73), Triple("多云", "多云", 58), Triple("晴", "晴", 12),
        Triple("雨", "小雨", 86), Triple("雷", "雷阵雨", 90), Triple("雾", "雾", 64)
    )
    val c = conds[seed]
    val base = 23 + (city.hashCode() % 7 + 7) % 7 - 3
    val labels = listOf("今天", "明天", "周三", "周四", "周五", "周六", "周日")
    return com.example.my.data.WeatherSnapshot(
        city = city,
        temp = base,
        emoji = c.first,
        desc = c.second,
        wind = 8,
        humidity = c.third,
        updatedAt = "",
        days = labels.mapIndexed { i, label ->
            DayForecast(
                day = label,
                emoji = conds[(seed + i) % conds.size].first,
                desc = conds[(seed + i) % conds.size].second,
                percent = 40 + (i * 13) % 55,
                low = base - 5 + (i % 3),
                high = base + 1 + (i % 4)
            )
        }
    )
}

/** 天气页（发现页左上角城市入口）——Open-Meteo 真实数据，失败时降级演示数据 */
@Composable
fun WeatherScreen(state: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var target by remember {
        mutableStateOf(CityPreset(state.weatherCity, state.weatherLatitude, state.weatherLongitude))
    }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<CityHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var currentLocation by remember { mutableStateOf<CityHit?>(null) }

    // 获取当前位置用于搜索排序：只读缓存（30 分钟内），不为一个排序去等搜星
    LaunchedEffect(Unit) {
        currentLocation = runCatching { WeatherApi.locateCity(ctx, allowLive = false) }.getOrNull()
    }

    // 天气数据与发现页顶部共用 AppState 里的一份状态，切换城市即时同步
    val snapshot = state.weatherNow
    val loading = state.weatherLoading
    val failed = state.weatherError != null && snapshot == null
    val demo = remember(failed, target.name) {
        if (failed) demoSnapshot(target.name) else null
    }
    val data = snapshot ?: demo

    LaunchedEffect(state.weatherCity, state.weatherLatitude, state.weatherLongitude) {
        target = CityPreset(state.weatherCity, state.weatherLatitude, state.weatherLongitude)
    }

    LaunchedEffect(Unit) { state.refreshWeather() }

    // 城市搜索（400ms 防抖）
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isBlank()) {
            hits = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(400)
        hits = runCatching {
            AmapApi.searchCity(
                keyword = q,
                currentLat = currentLocation?.lat,
                currentLon = currentLocation?.lon
            )
        }.getOrDefault(emptyList())
        // 结果为空时要能分清是「服务不可用」还是「真没这地方」，否则用户会一直以为是自己输错了
        searchError = AmapApi.lastErrorHint
        searching = false
    }

    fun applyCity(name: String, lat: Double, lon: Double) {
        target = CityPreset(name, lat, lon)
        query = ""
        hits = emptyList()
        state.setWeatherCity(name, lat, lon)
    }

    fun doLocate() {
        scope.launch {
            state.toast(tr("正在定位…"))
            val hit = WeatherApi.locateCity(ctx)
            if (hit == null) {
                state.toast(tr("定位失败，请手动选择城市"))
            } else {
                applyCity(hit.name, hit.lat, hit.lon)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // 精确 + 大致一起请求；只给大致时也能用，但要知道精度会差很多
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) doLocate() else state.toast(tr("未授予定位权限"))
    }

    val cities = remember(query) {
        if (query.isBlank()) cityPresets
        else cityPresets.filter { it.name.contains(query.trim()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg.Background)
    ) {
        // 标题栏只放标题文字；返回按钮在内容区
        AppTopBar(
            title = tr("天气"),
            subtitle = target.name
        )
        BackActionRow(onBack = { state.closeScreen() })

        // 就地 AI 入口：按当前天气现场给「适合出门吗 / 推荐去处」等问题
        AiEntryBar(
            state = state,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 当前天气大卡
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredIn(0)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFF09A78), Brand.Terracotta)))
                        .cardShadow(RoundedCornerShape(28.dp))
                        .padding(vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(data?.city ?: target.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (data == null) {
                        Box(modifier = Modifier.height(92.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WeatherIcon(data.emoji, Modifier.size(44.dp), Color.White)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${data.temp}°",
                                color = Color.White,
                                fontSize = 58.sp,
                                fontWeight = FontWeight.Light,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(tr(data.desc), color = Color.White, fontSize = 16.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            tr("风 ${data.wind}级 · 湿度 ${data.humidity}%", "Wind level ${data.wind} · Humidity ${data.humidity}%"),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                        if (data.updatedAt.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                tr("更新于 ${data.updatedAt}", "Updated ${data.updatedAt}"),
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }
                    }
                }
            }

            // 网络异常提示
            if (failed) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brand.Gold.copy(alpha = 0.14f))
                            .clickable { state.refreshWeather(force = true) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tr("网络异常，当前为演示数据 · 点击重试"),
                            color = Text.Secondary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (loading) {
                            CircularProgressIndicator(
                                color = Brand.Terracotta,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // 搜索
            item {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredIn(1)
                        .clip(RoundedCornerShape(50.dp))
                        .background(Bg.Surface)
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Search, null, tint = Text.Muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(tr("搜索城市 / 地点天气"), color = Text.Muted, fontSize = 14.sp) },
                        singleLine = true,
                        textStyle = TextStyle(color = Text.Primary, fontSize = 14.sp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Brand.Terracotta
                        )
                    )
                    if (searching) {
                        CircularProgressIndicator(
                            color = Brand.Terracotta,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 搜索结果
            if (query.isNotBlank()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Bg.Surface)
                            .cardShadow(RoundedCornerShape(20.dp))
                            .padding(vertical = 4.dp)
                    ) {
                        if (hits.isEmpty() && !searching) {
                            val err = searchError
                            Text(
                                if (err != null) err else tr("未找到「${query.trim()}」", "Nothing found for \"${query.trim()}\""),
                                color = if (err != null) Brand.Terracotta else Text.Muted,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                        if (hits.isNotEmpty() && searchError != null) {
                            Text(
                                tr("⚠️ 地图服务未生效，以下为备用数据源结果，可能不完整"),
                                color = Brand.Terracotta,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                        hits.forEach { hit ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { applyCity(hit.name, hit.lat, hit.lon) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.LocationOn,
                                    null,
                                    tint = Brand.Terracotta,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    hit.name,
                                    color = Text.Primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (hit.region.isNotBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        hit.region,
                                        color = Text.Muted,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // 定位我 / 地图选点
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.staggeredIn(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WeatherActionButton(
                        label = tr("定位我"),
                        icon = Icons.Filled.MyLocation,
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                ctx, Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                                ctx, Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) doLocate()
                            else permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    WeatherActionButton(
                        label = tr("地图选点"),
                        icon = Icons.Outlined.Map,
                        onClick = {
                            state.closeScreen()
                            state.activeTab = AppTab.MAP
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 城市快捷选择
            item {
                Spacer(Modifier.height(14.dp))
                Column(Modifier.staggeredIn(3)) {
                    cities.chunked(5).forEach { rowCities ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowCities.forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(
                                            if (c.name == target.name) Modifier
                                            else Modifier.cardShadow(RoundedCornerShape(50.dp))
                                        )
                                        .clip(RoundedCornerShape(50.dp))
                                        .background(if (c.name == target.name) Brand.Terracotta else Bg.Surface)
                                        .clickable { applyCity(c.name, c.lat, c.lon) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        c.name,
                                        color = if (c.name == target.name) Color.White else Text.Primary,
                                        fontSize = 13.sp,
                                        fontWeight = if (c.name == target.name) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }
                            }
                            repeat(5 - rowCities.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            // 未来 7 天
            item {
                Spacer(Modifier.height(6.dp))
                val days = data?.days.orEmpty()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredIn(4)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Bg.Surface)
                        .cardShadow(RoundedCornerShape(24.dp))
                        .padding(16.dp)
                ) {
                    Text(tr("未来 7 天"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (days.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(96.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Brand.Terracotta,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    days.forEach { d ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                tr(d.day),
                                color = Text.Secondary,
                                fontSize = 14.sp,
                                modifier = Modifier.width(44.dp)
                            )
                            WeatherIcon(d.emoji, Modifier.size(18.dp), Text.Secondary)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                tr(d.desc),
                                color = Text.Primary,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${d.percent}%",
                                color = Color(0xFF5FA394),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = Text.Secondary, fontSize = 13.sp)) { append("${d.low}°") }
                                    withStyle(SpanStyle(color = Brand.Terracotta, fontSize = 13.sp)) { append(" — ") }
                                    withStyle(SpanStyle(color = Text.Secondary, fontSize = 13.sp)) { append("${d.high}°") }
                                },
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun WeatherActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .cardShadow(RoundedCornerShape(50.dp))
            .clip(RoundedCornerShape(50.dp))
            .background(Bg.Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Brand.Terracotta, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Brand.Terracotta, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
