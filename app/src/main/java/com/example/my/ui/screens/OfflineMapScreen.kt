package com.example.my.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.map.AdminArea
import com.example.my.data.map.AmapDistrictApi
import com.example.my.data.map.OfflinePack
import com.example.my.data.map.OfflinePackStore
import com.example.my.data.map.OfflineTiles
import com.example.my.ui.components.AppBanner
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.FixedInk
import com.example.my.ui.theme.Layer
import com.example.my.ui.theme.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.BoundingBox
import java.util.Locale

/**
 * 离线地图下载：以「县 / 区」为最小单位，可以单下某一个县，也可以一键把整省还没下的县全部下下来。
 *
 * 下载写进 osmdroid 平时使用的那套瓦片缓存（每张瓦片永不过期），所以下完就真能断网看；
 * 删除时按同一个矩形范围逐张删，只影响这一个县。
 */
@Composable
fun OfflineMapScreen(onBack: () -> Unit, tileSource: (String) -> ITileSource) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val vectorSource = remember { tileSource("vector") }
    val satelliteSource = remember { tileSource("satellite") }

    var layer by remember { mutableStateOf("vector") }
    var precise by remember { mutableStateOf(false) }
    val source = if (layer == "satellite") satelliteSource else vectorSource
    val minZoom = OfflineTiles.MIN_ZOOM
    val maxZoom = if (precise) OfflineTiles.MAX_ZOOM_DETAIL else OfflineTiles.MAX_ZOOM_STANDARD

    var packs by remember { mutableStateOf(OfflinePackStore.all(context)) }
    var provinces by remember { mutableStateOf<List<AdminArea>>(emptyList()) }
    var loadingProvinces by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var expanded by remember { mutableStateOf<String?>(null) }
    var countiesMap by remember { mutableStateOf<Map<String, List<AdminArea>>>(emptyMap()) }
    var loadingCounties by remember { mutableStateOf<String?>(null) }

    var busy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("") }
    var ratio by remember { mutableStateOf(0f) }
    var progressText by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        val list = keepCancellation { AmapDistrictApi.provinces() }.orEmpty()
        provinces = list
        loadError = if (list.isEmpty()) {
            AmapDistrictApi.lastError ?: tr("省级列表拉取失败", "Failed to load provinces")
        } else null
        loadingProvinces = false
    }

    fun refreshPacks() {
        packs = OfflinePackStore.all(context)
    }

    fun startBatch(province: AdminArea, areas: List<AdminArea>) {
        if (busy) return
        val todo = areas.filter { a -> packs.none { it.adcode == a.adcode } }
        if (todo.isEmpty()) {
            AppBanner.show(tr("这个省的县都已经下载好了", "All counties in this province are downloaded"))
            return
        }
        busy = true
        job = scope.launch {
            var ok = 0
            var failed = 0
            try {
                todo.forEachIndexed { index, area ->
                    busyLabel = tr(
                        "${province.name} · ${area.name}（${index + 1}/${todo.size}）",
                        "${area.name} (${index + 1}/${todo.size})"
                    )
                    ratio = 0f
                    progressText = tr("正在取边界…", "Resolving boundary…")
                    val bb = keepCancellation { AmapDistrictApi.bounds(area) }
                    if (bb == null) {
                        failed++
                        return@forEachIndexed
                    }
                    val totalTiles = OfflineTiles.estimate(bb, minZoom, maxZoom)
                    progressText = "0 / $totalTiles"
                    val result = keepCancellation {
                        OfflineTiles.download(context, source, bb, minZoom, maxZoom) { done, all ->
                            ratio = if (all > 0) (done.toFloat() / all).coerceIn(0f, 1f) else 0f
                            progressText = "$done / $all"
                        }
                    }
                    if (result == null || result.tiles == 0) {
                        failed++
                        return@forEachIndexed
                    }
                    OfflinePackStore.save(
                        context,
                        OfflinePack(
                            adcode = area.adcode,
                            name = area.name,
                            province = province.name,
                            city = area.city,
                            minLat = bb.latSouth,
                            minLon = bb.lonWest,
                            maxLat = bb.latNorth,
                            maxLon = bb.lonEast,
                            minZoom = minZoom,
                            maxZoom = maxZoom,
                            tiles = result.tiles,
                            bytes = result.bytes,
                            savedAt = System.currentTimeMillis(),
                            layer = layer
                        )
                    )
                    ok++
                    refreshPacks()
                }
                AppBanner.show(
                    if (failed > 0) {
                        tr("下载完成 $ok 个县，$failed 个失败（可能是网络或地图 Key 配额）",
                            "Downloaded $ok counties, $failed failed")
                    } else {
                        tr("已离线保存 $ok 个县", "Saved $ok counties offline")
                    }
                )
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                // 取消后必须让协程真的结束，同时把已经下完的县保留下来
                AppBanner.show(tr("已取消下载，已下好的县保留", "Canceled; downloaded counties are kept"))
                throw cancel
            } finally {
                busy = false
                job = null
                refreshPacks()
            }
        }
    }

    fun removePack(pack: OfflinePack) {
        if (busy) return
        scope.launch {
            busy = true
            busyLabel = tr("正在移除 ${pack.name}", "Removing ${pack.name}")
            ratio = 0f
            progressText = ""
            keepCancellation {
                withContext(Dispatchers.IO) {
                    OfflineTiles.delete(
                        context,
                        tileSource(pack.layer),
                        BoundingBox(pack.maxLat, pack.maxLon, pack.minLat, pack.minLon),
                        pack.minZoom,
                        pack.maxZoom
                    )
                }
            }
            OfflinePackStore.remove(context, pack.adcode)
            busy = false
            refreshPacks()
            AppBanner.show(tr("已移除 ${pack.name} 的离线地图", "Removed offline map for ${pack.name}"))
        }
    }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 盖在地图之上的页面必须用不透明底，否则自定义背景图会透上来
            .background(Layer.Opaque)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 8.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = tr("返回地图", "Back to map"),
                    tint = Text.Primary,
                    modifier = Modifier.size(21.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    tr("离线地图", "Offline map"),
                    color = Text.Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    tr("按县下载 · 下完断网也能看底图", "Download by county · works offline"),
                    color = Text.Muted,
                    fontSize = 10.sp
                )
            }
            Text(
                text = if (packs.isEmpty()) tr("未下载", "None")
                else tr("${packs.size} 个县 · ${sizeText(packs.sumOf { it.bytes })}",
                    "${packs.size} · ${sizeText(packs.sumOf { it.bytes })}"),
                color = Text.Secondary,
                fontSize = 11.sp
            )
        }

        // 底图 / 精度：两排胶囊，和页面其他地方同一套视觉
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("vector" to tr("标准底图", "Standard"), "satellite" to tr("卫星底图", "Satellite"))
                .forEach { (key, label) ->
                    PillChip(label, key == layer, Modifier.weight(1f)) {
                        if (!busy) layer = key
                    }
                }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PillChip(tr("标准 6-12 级", "Standard 6-12"), !precise, Modifier.weight(1f)) {
                if (!busy) precise = false
            }
            PillChip(tr("精细 6-14 级", "Detailed 6-14"), precise, Modifier.weight(1f)) {
                if (!busy) precise = true
            }
        }

        if (busy) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Bg.SurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ratio > 0f) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = Brand.Terracotta
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        busyLabel,
                        color = Text.Primary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        tr("取消", "Cancel"),
                        color = Brand.Terracotta,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { job?.cancel() }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Bg.Outline)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(if (ratio <= 0f) 0.001f else ratio.coerceAtMost(1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Brand.Terracotta)
                    )
                }
                if (progressText.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("$progressText ${tr("张瓦片", "tiles")}", color = Text.Muted, fontSize = 10.sp)
                }
            }
        }

        when {
            loadingProvinces -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Brand.Terracotta)
            }

            provinces.isEmpty() -> Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Text(loadError ?: tr("暂无省级数据", "No province data"), color = Text.Secondary, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("离线地图要靠高德行政区划接口取县界，Key 失效或超额时会拉不到列表",
                        "Province list comes from the Amap district API; a bad key blocks it"),
                    color = Text.Muted,
                    fontSize = 11.sp
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp)
            ) {
                items(provinces, key = { it.adcode }) { province ->
                    val areaPacks = packs.filter { it.province == province.name }
                    val isOpen = expanded == province.adcode
                    ProvinceRow(
                        province = province,
                        downloaded = areaPacks.size,
                        expanded = isOpen,
                        onClick = {
                            if (isOpen) {
                                expanded = null
                            } else {
                                expanded = province.adcode
                                if (countiesMap[province.adcode] == null && loadingCounties == null) {
                                    loadingCounties = province.adcode
                                    scope.launch {
                                        val list = keepCancellation { AmapDistrictApi.counties(province) }.orEmpty()
                                        countiesMap = countiesMap + (province.adcode to list)
                                        loadingCounties = null
                                    }
                                }
                            }
                        }
                    )
                    if (isOpen) {
                        val counties = countiesMap[province.adcode]
                        when {
                            loadingCounties == province.adcode -> Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 6.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = Brand.Terracotta
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(tr("正在拉取县列表…", "Loading counties…"), color = Text.Muted, fontSize = 11.sp)
                            }

                            counties == null || counties.isEmpty() -> Text(
                                tr("这个省没有取到县列表", "No counties returned"),
                                color = Text.Muted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 10.dp)
                            )

                            else -> {
                                val pending = counties.filter { c -> packs.none { it.adcode == c.adcode } }
                                val estimate = pending.sumOf { roughBytes(it, minZoom, maxZoom) }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, top = 4.dp, bottom = 6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Brand.Terracotta)
                                        .clickable(enabled = !busy) { startBatch(province, counties) }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (pending.isEmpty()) tr("本省已全部下载", "All downloaded")
                                        else tr(
                                            "下载本省剩余 ${pending.size} 个县 · 预计 ${sizeText(estimate)}",
                                            "Download remaining ${pending.size} · ~${sizeText(estimate)}"
                                        ),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                counties.forEach { county ->
                                    val pack = packs.firstOrNull { it.adcode == county.adcode }
                                    CountyRow(
                                        county = county,
                                        pack = pack,
                                        busy = busy,
                                        onDownload = { startBatch(province, listOf(county)) },
                                        onRemove = pack?.let { p -> { removePack(p) } }
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, Bg.Outline, RoundedCornerShape(50))
                            .clickable(enabled = !busy) {
                                scope.launch {
                                    busy = true
                                    busyLabel = tr("正在清空瓦片缓存", "Clearing tile cache")
                                    withContext(Dispatchers.IO) {
                                        OfflineTiles.purgeAll()
                                    }
                                    OfflinePackStore.clear(context)
                                    busy = false
                                    refreshPacks()
                                    AppBanner.show(tr("瓦片缓存已清空", "Tile cache cleared"))
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tr("清空全部瓦片缓存", "Clear all tile cache"), color = Text.Secondary, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr(
                            "当前瓦片缓存 ${sizeText(OfflineTiles.cacheDbSize())}。清空会把平时浏览地图留下的缓存一起删掉，已下载的县也会一起失效。",
                            "Tile cache now ${sizeText(OfflineTiles.cacheDbSize())}. Clearing removes normal browsing cache and every offline pack."
                        ),
                        color = Text.Muted,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PillChip(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) FixedInk.ChipSurface else Bg.Surface)
            .border(1.dp, if (active) FixedInk.ChipSurface else Bg.Outline, RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) FixedInk.OnChip else Text.Secondary,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProvinceRow(province: AdminArea, downloaded: Int, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Bg.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            province.name,
            color = Text.Primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (downloaded > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Bg.SurfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(tr("已存 $downloaded 县", "$downloaded saved"), color = Text.Secondary, fontSize = 10.sp)
            }
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = Text.Muted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun CountyRow(
    county: AdminArea,
    pack: OfflinePack?,
    busy: Boolean,
    onDownload: () -> Unit,
    onRemove: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Bg.SurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                county.name,
                color = Text.Primary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = when {
                pack != null -> tr(
                    "${county.city.ifBlank { county.name }} · ${pack.sizeText} · ${pack.zoomText} 级",
                    "${pack.sizeText} · z${pack.zoomText}"
                )
                else -> county.city.ifBlank { tr("可下载", "Downloadable") }
            }
            Text(sub, color = Text.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (pack != null) {
            Text(
                tr("移除", "Remove"),
                color = Text.Secondary,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = !busy) { onRemove?.invoke() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        } else {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Brand.Terracotta)
                    .clickable(enabled = !busy, onClick = onDownload)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(tr("下载", "Download"), color = Color.White, fontSize = 11.sp)
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/** 一个县的粗略体积：用中心点 ±0.2° 的方框估，只用于「预计大小」，真实占用以下载结果为准 */
private fun roughBytes(area: AdminArea, minZoom: Int, maxZoom: Int): Long {
    val bb = BoundingBox(area.lat + 0.2, area.lon + 0.25, area.lat - 0.2, area.lon - 0.25)
    return OfflineTiles.estimateBytes(OfflineTiles.estimate(bb, minZoom, maxZoom))
}

/**
 * 把「网络 / 解析失败」变成 null 继续跑，但**取消必须继续往上抛**。
 *
 * 用 runCatching 直接包协程调用会连 CancellationException 一起吞掉：用户点了「取消」，
 * 批量下载却还在一个县一个县往下走，是最难查的那种「按钮像坏了一样」。
 */
private suspend fun <T> keepCancellation(block: suspend () -> T): T? = try {
    block()
} catch (cancel: kotlinx.coroutines.CancellationException) {
    throw cancel
} catch (t: Throwable) {
    null
}

private fun sizeText(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
