package com.example.my.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.ViewTreeObserver
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.my.data.AmapApi
import com.example.my.data.ChinaMapData
import com.example.my.data.CityCoords
import com.example.my.data.Locator
import com.example.my.data.PoiResult
import com.example.my.data.WeatherApi
import com.example.my.data.map.TencentTiles
import com.example.my.ui.components.AppTopBar
import com.example.my.ui.components.BackActionRow
import com.example.my.ui.components.BackExitButton
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Layer
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.example.my.ui.i18n.tr

/** 没有初始位置时的默认视角：中国中心 */
private val PICK_CENTER = OsmGeoPoint(35.0, 105.0)
private const val PICK_ZOOM_DEFAULT = 5.0
private const val PICK_ZOOM_POI = 16.0
private const val PICK_ZOOM_CITY = 11.5
private const val PICK_ZOOM_ME = 14.0

/** 中心针的尺寸：宽 26dp / 高 34dp，针尖落在地图中心 */
private const val PIN_W_DP = 26
private const val PIN_H_DP = 34

/**
 * 与 MapScreen 相同的矢量底图瓦片源（免 Key，腾讯地图瓦片）。
 * 单独保留一份：地图选点页不依赖足迹地图页的内部实现，避免改一处影响两处。
 */
private class PickMapTileSource : OnlineTileSourceBase(
    "腾讯地图-vector", TencentTiles.MIN_ZOOM, TencentTiles.MAX_ZOOM, 256, "", arrayOf(""), "© 腾讯地图"
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return TencentTiles.url("vector", z, x, y)
    }
}

/**
 * 地图选点页（全屏 Dialog）：中心固定选点，拖动/缩放地图后自动反查地名，
 * 支持关键词搜索（高德 POI）与「使用我的位置」，确认后回填经纬度 + 地点名 + 到访时间。
 *
 * 选点正确性依赖三件事，缺一件就会「选的位置不对」：
 *  1. 初始视角必须落在上次选的那一点上（地图测量完成后再 setCenter，否则 osmdroid 会把
 *     该点当成「视口左上角」，看起来整体偏出去大半屏）；
 *  2. 屏幕中心针的针尖必须**正好**指着 mapCenter（针画偏了，用户按看到的点，存的是另一个点）；
 *  3. 地点名必须和坐标对得上（拖完立刻点确定时，名字不能还是上一个位置的）。
 */
@Composable
fun MapPickScreen(
    initialMarker: Offset?,
    initialName: String,
    initialDateMillis: Long?,
    onConfirm: (lon: Double, lat: Double, name: String, dateMillis: Long?) -> Unit,
    onDismiss: () -> Unit,
    /** 精确起始坐标（GCJ-02）。优先于 initialMarker —— 后者是示意地图坐标，反算有误差 */
    initialLat: Double? = null,
    initialLon: Double? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    /** 逆地理出的行政区域（如「厦门市 · 思明区」），仅用于提示「选的是哪一片」 */
    var region by remember { mutableStateOf<String?>(null) }
    /** 地点名称：自动带出，用户可手动改；手动改过的值不被后续反查覆盖 */
    var placeName by remember { mutableStateOf(initialName) }
    /** 最近一次自动带出的地名，用来判断「当前名称是否仍是自动值」 */
    var autoName by remember { mutableStateOf(initialName) }
    var dateMillis by remember { mutableStateOf(initialDateMillis ?: todayStart()) }
    var query by remember { mutableStateOf("") }
    var poiResults by remember { mutableStateOf<List<PoiResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var showResults by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }

    // 起始坐标：优先精确经纬度；旧数据只有示意地图坐标，才反算
    val startPoint = remember {
        when {
            initialLat != null && initialLon != null -> OsmGeoPoint(initialLat, initialLon)
            initialMarker != null -> {
                val (lon, lat) = ChinaMapData.mapToLonLat(initialMarker.x, initialMarker.y)
                OsmGeoPoint(lat, lon)
            }
            else -> null
        }
    }
    /**
     * 起始视距：从「城市快捷选择」进来时名称就是城市名，此时直接怼到 16 级街景，
     * 用户会以为选错地方了——城市级别给 11.5 级（能看清城区轮廓），具体地点才给 16 级。
     */
    val startZoom = remember {
        if (initialName.isNotBlank() && CityCoords.lonLat(initialName) != null) PICK_ZOOM_CITY
        else PICK_ZOOM_POI
    }

    val handler = remember { Handler(Looper.getMainLooper()) }
    val latestView by rememberUpdatedState(mapView)
    val latestPlace by rememberUpdatedState(placeName)
    val latestAuto by rememberUpdatedState(autoName)

    // 去抖反查：地图滚动/缩放停止 350ms 后按屏幕中心取「地点名 + 行政区」
    val pickRunnable = remember {
        object : Runnable {
            override fun run() {
                scope.launch {
                    val v = latestView ?: return@launch
                    // 还没测量时 mapCenter 是默认值，查出来的地名会张冠李戴
                    if (v.width <= 0 || v.height <= 0) return@launch
                    val gp = v.mapCenter
                    val res = AmapApi.reversePick(gp.latitude, gp.longitude) ?: return@launch
                    region = res.region
                    val name = res.name ?: res.region ?: return@launch
                    // 只覆盖「空」或「仍是自动值」的名称，绝不覆盖用户手动输入
                    if (latestPlace.isBlank() || latestPlace == latestAuto) {
                        placeName = name
                        autoName = name
                    }
                }
            }
        }
    }
    val schedulePick = remember {
        { handler.postDelayed(pickRunnable, 350); Unit }
    }

    fun applyReverse(lat: Double, lon: Double) {
        scope.launch {
            val res = AmapApi.reversePick(lat, lon) ?: return@launch
            region = res.region
            val name = res.name ?: res.region ?: return@launch
            if (placeName.isBlank() || placeName == autoName) {
                placeName = name
                autoName = name
            }
        }
    }

    fun doLocate() {
        scope.launch {
            locating = true
            val hit = runCatching { WeatherApi.locateCity(context, timeoutMs = 12_000L) }.getOrNull()
            locating = false
            if (hit == null) {
                // 分清是没权限、定位开关关着，还是单纯没搜到信号
                region = when (Locator.problem(context)) {
                    Locator.REASON_PERMISSION -> tr("未授予定位权限")
                    Locator.REASON_SERVICE_OFF -> tr("系统定位已关闭")
                    else -> tr("定位超时，请到空旷处或靠近窗口再试")
                }
                return@launch
            }
            val v = mapView ?: return@launch
            v.controller.setZoom(PICK_ZOOM_ME)
            v.controller.animateTo(OsmGeoPoint(hit.lat, hit.lon))
            applyReverse(hit.lat, hit.lon)
        }
    }

    /** 定位权限请求；授予后定位到当前位置 */
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            doLocate()
        }
    }

    fun onLocate() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            doLocate()
        }
    }

    fun onPoiPick(r: PoiResult) {
        focusManager.clearFocus()
        placeName = r.name
        // 先断开「自动值」判定，避免下面 animateTo 引发的反查把刚选中的 POI 名冲掉；
        // 等镜头动画结束（1.2 秒）再恢复自动跟随，之后拖动地图照常刷新
        autoName = ""
        handler.postDelayed({ autoName = r.name }, 1200)
        region = r.address.takeIf { it.isNotBlank() }
        val v = mapView ?: return
        v.controller.setZoom(PICK_ZOOM_POI)
        v.controller.animateTo(OsmGeoPoint(r.lat, r.lon))
        showResults = false
        query = ""
    }

    /**
     * 确认选点。
     * 名称还是自动值时，按最终坐标再查一次——否则「拖完地图立刻点确定」会存下
     * 上一个位置的地名，用户看到的坐标和名字是两处地方。
     */
    fun confirm() {
        if (confirming) return
        val v = mapView ?: return
        if (v.width <= 0 || v.height <= 0) return
        val gp = v.mapCenter
        confirming = true
        scope.launch {
            var finalName = placeName.trim()
            if (finalName.isBlank() || finalName == autoName) {
                val res = runCatching { AmapApi.reversePick(gp.latitude, gp.longitude) }.getOrNull()
                val n = res?.name ?: res?.region
                if (!n.isNullOrBlank()) finalName = n
            }
            confirming = false
            onConfirm(gp.longitude, gp.latitude, finalName, dateMillis)
        }
    }

    // 搜索防抖：停顿 400ms 后才发起请求；query 变化会取消旧协程，避免过期结果顶掉新结果
    LaunchedEffect(query) {
        if (query.isBlank()) {
            poiResults = emptyList()
            showResults = false
            return@LaunchedEffect
        }
        delay(400)
        searching = true
        // 排序参考：视野够近才用地图中心。停在全国视野时中心在青海，
        // 拿它排序会让「人民公园」优先给出青海的结果
        val c = mapView?.let { v ->
            if (runCatching { v.zoomLevelDouble }.getOrDefault(0.0) >= 8.0) v.mapCenter else null
        }
        // 用当前视野的城市做排序倾向（不做硬限制）：搜「人民公园」先给本地的，
        // 但远景搜「鼓浪屿」时不会被卡在城市里一条都搜不到
        val cityHint = region?.substringBefore(" · ")?.trim()
            ?.takeIf { it.isNotBlank() && it != "当前位置" }
        val results = AmapApi.searchPoi(
            keyword = query,
            city = cityHint ?: "",
            currentLat = c?.latitude,
            currentLon = c?.longitude,
            radius = 50_000,
            count = 8,
            cityLimit = false
        )
        searching = false
        poiResults = results
        showResults = results.isNotEmpty()
    }

    // 中途创建的 MapView 不会再收到 ON_RESUME，这里补一次（与 MapScreen 一致）
    LaunchedEffect(mapReady) {
        val v = mapView ?: return@LaunchedEffect
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) v.onResume()
        if (startPoint != null) {
            v.whenMeasured {
                v.controller.setZoom(startZoom)
                v.controller.setCenter(startPoint)
            }
        } else {
            // 没有起点：能读到缓存位置就直接落过去，省得从中国全图开始找
            val fix = Locator.lastKnown(context)
            if (fix != null) {
                v.whenMeasured {
                    v.controller.setZoom(PICK_ZOOM_ME)
                    v.controller.setCenter(OsmGeoPoint(fix.lat, fix.lon))
                }
            }
        }
        // 等镜头落位（含可能的重新测量）再反查，否则查的是默认中心的地名
        handler.postDelayed(pickRunnable, 900)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Layer.Opaque)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            AppTopBar(title = tr("选择地点"))

            // 地图 + 中心选点针。顶部「返回/确定」操作行作为悬浮层盖在地图上并带实底，
            // 否则它紧挨着地图瓦片、像被地图盖住，既看不清也点不准。
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        createPickMap(ctx, schedulePick).also { m ->
                            mapView = m
                            mapReady = true
                        }
                    },
                    update = {},
                    onRelease = { v ->
                        (v as? MapView)?.onDetach()
                        mapView = null
                        mapReady = false
                    }
                )

                // 中心针：针尖正好落在地图中心（= 实际取到的坐标）。
                // 整体上移半个高度，让 Box 底边对齐中心线。
                Canvas(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(width = PIN_W_DP.dp, height = PIN_H_DP.dp)
                        .offset(y = (-(PIN_H_DP / 2f)).dp)
                ) {
                    val cx = size.width / 2f
                    val r = size.width * 0.34f
                    // 往下留出白边半径的余量，否则圆头会被 Canvas 顶边裁掉一圈
                    val headY = r + size.width * 0.12f
                    // 针身：圆头下方的三角，汇到底边中点（= 地图中心）
                    val body = Path().apply {
                        moveTo(cx - r * 0.72f, headY + r * 0.55f)
                        lineTo(cx, size.height)
                        lineTo(cx + r * 0.72f, headY + r * 0.55f)
                        close()
                    }
                    drawPath(body, Brand.Terracotta)
                    drawCircle(Color.White, radius = r + size.width * 0.09f, center = Offset(cx, headY))
                    drawCircle(Brand.Terracotta, radius = r, center = Offset(cx, headY))
                    drawCircle(Color.White, radius = r * 0.34f, center = Offset(cx, headY))
                }

                // 顶部悬浮操作行：实底背景，保证返回/确定始终压在地图之上、可读可点
                BackActionRow(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Layer.Opaque),
                    leading = {
                        // 退出按钮：箭头 + 文字，点了就放弃本次选点回到发布页（解析中不打断）
                        BackExitButton(
                            text = tr("返回", "Back"),
                            onClick = { if (!confirming) onDismiss() }
                        )
                    },
                    actions = {
                        TextButton(onClick = { confirm() }, enabled = mapReady && !confirming) {
                            Text(
                                tr("确定"),
                                color = if (mapReady) Brand.Terracotta else Text.Muted,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                )
            }

            // 底部信息卡
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Bg.Surface)
                    .cardShadow()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // 搜索
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Bg.SurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(tr("搜索地点，如：鼓浪屿、北京…"), color = Text.Muted, fontSize = 14.sp) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Filled.Search, null, tint = Text.Muted, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Brand.Terracotta
                                )
                            }
                        },
                        textStyle = TextStyle(color = Text.Primary, fontSize = 14.sp),
                        colors = pickFieldColors()
                    )
                }
                if (showResults && poiResults.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .padding(top = 6.dp)
                    ) {
                        items(poiResults, key = { it.name + it.lat + it.lon + it.address }) { r ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onPoiPick(r) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(r.name, color = Text.Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    if (r.address.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(r.address, color = Text.Muted, fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                                Icon(Icons.Filled.MyLocation, null, tint = Brand.Terracotta, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                // 已选择 + 使用我的位置
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("已选择"), color = Text.Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = region ?: tr("拖动地图，自动识别地名"),
                        color = if (region != null) Brand.Terracotta else Text.Muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onLocate() }) {
                        if (locating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Brand.Terracotta
                            )
                        } else {
                            Icon(Icons.Filled.MyLocation, null, tint = Brand.Terracotta, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(tr("使用我的位置"), color = Brand.Terracotta, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // 地点名称
                Text(tr("地点名称"), color = Text.Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Bg.SurfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                ) {
                    TextField(
                        value = placeName,
                        onValueChange = { placeName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(tr("自动识别或手动填写"), color = Text.Muted, fontSize = 14.sp) },
                        singleLine = true,
                        textStyle = TextStyle(color = Text.Primary, fontSize = 14.sp),
                        colors = pickFieldColors()
                    )
                }
                Spacer(Modifier.height(12.dp))

                // 到访时间
                Text(tr("到访时间"), color = Text.Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Bg.SurfaceVariant)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CalendarMonth, null, tint = Brand.Terracotta, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(text = formatPickDate(dateMillis), color = Text.Primary, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        if (showDatePicker) {
            val dateState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        dateMillis = dateState.selectedDateMillis ?: dateMillis
                        showDatePicker = false
                    }) { Text(tr("确定"), color = Brand.Terracotta) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text(tr("取消"), color = Text.Secondary) }
                }
            ) {
                DatePicker(state = dateState)
            }
        }
    }

    // 系统返回逐层退：解析最终地名时不打断 → 有搜索结果先收结果 → 否则退出选点
    BackHandler {
        when {
            confirming -> Unit
            showResults -> {
                showResults = false
                query = ""
                focusManager.clearFocus()
            }
            else -> onDismiss()
        }
    }
}

/**
 * 布局完成（拿到非零尺寸）后再执行。
 *
 * osmdroid 在 View 还是 0×0 时调用 setCenter，会把目标点当成「视口左上角」来摆放，
 * 等真正测量完，看到的中心已经偏出去半屏——这正是「打开选点页，位置就跑偏」的原因。
 */
private inline fun MapView.whenMeasured(crossinline action: () -> Unit) {
    if (width > 0 && height > 0) {
        action()
        return
    }
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            if (width > 0 && height > 0) {
                if (viewTreeObserver.isAlive) viewTreeObserver.removeOnGlobalLayoutListener(this)
                action()
            }
        }
    })
}

/** 创建可拖动缩放的选点地图；onMoved 在地图滚动/缩放时回调，由调用方做去抖反查 */
private fun createPickMap(ctx: Context, onMoved: () -> Unit): MapView {
    Configuration.getInstance().userAgentValue = "MYFootprint/1.0 (Android)"
    // 瓦片本地缓存放私有目录，无需存储权限
    val cacheRoot = File(ctx.cacheDir, "osmdroid").apply { mkdirs() }
    Configuration.getInstance().apply {
        osmdroidBasePath = cacheRoot
        osmdroidTileCache = File(cacheRoot, "tiles")
    }
    return MapView(ctx).apply {
        setTileSource(PickMapTileSource())
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        minZoomLevel = 3.0
        maxZoomLevel = TencentTiles.MAX_ZOOM.toDouble()
        controller.setZoom(PICK_ZOOM_DEFAULT)
        controller.setCenter(PICK_CENTER)
        addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent): Boolean {
                onMoved()
                return false
            }

            override fun onZoom(event: ZoomEvent): Boolean {
                onMoved()
                return false
            }
        })
    }
}

@Composable
private fun pickFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = Brand.Terracotta
)

/** 当天零点（到访时间默认值） */
private fun todayStart(): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun formatPickDate(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(millis))
