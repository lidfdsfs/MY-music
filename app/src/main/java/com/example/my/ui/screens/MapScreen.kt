package com.example.my.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.ViewTreeObserver
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.my.data.ChinaMapData
import com.example.my.data.map.OfflineTiles
import com.example.my.data.map.TencentTiles
import com.example.my.data.Locator
import com.example.my.data.PoiResult
import com.example.my.data.AmapApi
import com.example.my.data.CityHit
import com.example.my.data.GeoFix
import com.example.my.data.ProvinceBoundaryData
import com.example.my.data.WeatherApi
import com.example.my.model.Footprint
import com.example.my.model.Route
import com.example.my.state.AppState
import com.example.my.R
import com.example.my.ui.components.AppTopBar
import com.example.my.ui.components.PrimaryButton
import com.example.my.ui.interaction.AnimatedNumber
import com.example.my.ui.map.CityLabelOverlay
import com.example.my.ui.map.MapLabel
import com.example.my.ui.map.NavRouteOverlay
import com.example.my.ui.map.ProvinceHighlightOverlay
import com.example.my.ui.map.StarMarkerDrawable
import com.example.my.ui.theme.*
import com.example.my.util.GeoPoint as AlgoGeoPoint
import com.example.my.util.clusterSummary
import com.example.my.util.cleanNote
import com.example.my.util.douglasPeucker
import com.example.my.util.singleLinkGroups
import com.example.my.util.haversineKm
import com.example.my.util.kdeWeights
import com.example.my.util.tspOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.infowindow.InfoWindow
import com.example.my.ui.i18n.Lang
import com.example.my.ui.i18n.tr

private val LINE_COLOR = 0xFF2A9D8F.toInt()

/** 实时轨迹的颜色：和顺序连线的青色区分开，用品牌陶土色，一眼看出"这条是刚走的" */
private val LIVE_LINE_COLOR = 0xFFE76F51.toInt()

/**
 * 分色路线配色。取自参考图：绿 / 紫 / 蓝 / 黄 四条高饱和路线，
 * 压在卫星影像和蓝色省域高亮上都能一眼分清。
 */
private val ROUTE_PALETTE = intArrayOf(
    0xFF4CE85C.toInt(), // 绿
    0xFFCC4BE8.toInt(), // 紫
    0xFF3B7BF0.toInt(), // 蓝
    0xFFFFD21F.toInt(), // 黄
    0xFF35D6E0.toInt(), // 青
    0xFFFF8A3D.toInt()  // 橙
)

/** 不属于任何分色路线的城市名，用米白色（参考图里没连线的地名就是这个调子） */
private val LABEL_NEUTRAL = 0xFFFFF0B3.toInt()

/**
 * 足迹之间的连线不再用高饱和粗线：向白色混一半、再降一点透明度，
 * 既保留每条路线的色相，又不会压过地图底图和点位。
 */
private fun softLineColor(color: Int, towardWhite: Float = 0.58f, alpha: Int = 190): Int {
    val r = android.graphics.Color.red(color)
    val g = android.graphics.Color.green(color)
    val b = android.graphics.Color.blue(color)
    fun mix(c: Int): Int = (c + (255 - c) * towardWhite).toInt().coerceIn(0, 255)
    return android.graphics.Color.argb(alpha, mix(r), mix(g), mix(b))
}

/**
 * 一条分色路线：这条线经过的点 + 线的颜色 + 路线名。
 *
 * @param footprintIds 这条线覆盖的足迹 id，用来给城市名标签上同色
 */
private data class RouteSegment(
    val points: List<GeoPoint>,
    val color: Int,
    val title: String,
    val footprintIds: Set<Int>
)

/** 没有足迹时的默认视角：中国中心。不设的话 osmdroid 默认停在 (0,0)，也就是大西洋正中 */
private val CHINA_CENTER = GeoPoint(35.0, 105.0)
private const val DEFAULT_ZOOM = 4.0
private const val ZOOM_POI = 16.0
private const val ZOOM_ME = 14.0
private const val ZOOM_SINGLE = 13.0

/** 开始自动记录时把镜头拉到最后一个轨迹点的层级：15 级约能看到几百米范围，适合「我走到哪了」 */
private const val ZOOM_TRACK = 15.0

/** 腾讯地图瓦片源：矢量底图 / 卫星影像（免 Key，GCJ-02，与高德检索坐标同系） */
private class TencentTileSource(private val style: String = "vector") : OnlineTileSourceBase(
    "腾讯地图-$style", TencentTiles.MIN_ZOOM, TencentTiles.MAX_ZOOM, 256, "", arrayOf(""), "© 腾讯地图"
) {
    override fun getTileURLString(pMapTileIndex: Long): String = tencentTileUrl(style, pMapTileIndex)
}

/**
 * 同一套瓦片、同一个缓存键名，只把 [TileSourcePolicy] 放开到允许批量下载。
 *
 * [org.osmdroid.tileprovider.cachemanager.CacheManager] 动手前会问 policy 是否接受批量抓取，
 * 默认策略不一定放行；离线包下载必须显式放开，否则会直接抛 TileSourcePolicyException。
 * 名字必须与 [TencentTileSource] 一字不差，否则落到另一个缓存键，下完地图也读不出来。
 */
private class TencentBulkTileSource(private val style: String = "vector") : OnlineTileSourceBase(
    "腾讯地图-$style", TencentTiles.MIN_ZOOM, TencentTiles.MAX_ZOOM, 256, "", arrayOf(""), "© 腾讯地图",
    TileSourcePolicy(2, 0)
) {
    override fun getTileURLString(pMapTileIndex: Long): String = tencentTileUrl(style, pMapTileIndex)
}

private fun tencentTileUrl(style: String, pMapTileIndex: Long): String {
    val z = MapTileIndex.getZoom(pMapTileIndex)
    val x = MapTileIndex.getX(pMapTileIndex)
    val y = MapTileIndex.getY(pMapTileIndex)
    return TencentTiles.url(style, z, x, y)
}

/** 深色底图的底色：比纯黑亮一档，瓦片没加载完也能看出是「地图」而不是黑屏 */
private const val DARK_MAP_BG = 0xFF14181F.toInt()

/** 浅色底图的底色：瓦片缺失/加载中时铺在 MapView 底下，避免透出页面背景 */
private const val LIGHT_MAP_BG = 0xFFF6F3EF.toInt()
/**
 * 夜间底图滤镜：把高德的浅色路网「反相 → 压到暗端 → 去饱和 → 冷偏色」。
 *
 * 为什么不直接盖一层半透明黑：
 *  - 纯蒙版只是把整张图均匀压暗，浅色底图和白色道路会一起变灰，对比度反而更差；
 *  - alpha 一高就糊成一块，用户看到的就是「一片黑」（严重时连路网都没了）。
 *
 * 反相则把「浅色底 / 白色路」的亮度关系翻过来，路网变成暗色线条；
 * 再乘 0.62 加 16 把整体压进暗端（底色约 #14181F），
 * 最后去饱和 + 偏蓝，让高德的米黄底和彩色高速不至于变成负片色。
 */
private val NIGHT_FILTER: ColorFilter by lazy {
    val m = ColorMatrix()
    // 1) 反相：白底→深色，深色文字→亮字
    m.set(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    // 2) 压缩到暗端：out = 0.62 * c + 16
    m.postConcat(
        ColorMatrix(
            floatArrayOf(
                0.62f, 0f, 0f, 0f, 16f,
                0f, 0.62f, 0f, 0f, 16f,
                0f, 0f, 0.62f, 0f, 16f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    // 3) 去饱和：负片味的粉紫高速、荧光绿地收敛成灰蓝
    val desat = ColorMatrix()
    desat.setSaturation(0.55f)
    m.postConcat(desat)
    // 4) 冷偏色：蓝多一点、红少一点，贴近常见的夜间地图
    m.postConcat(
        ColorMatrix(
            floatArrayOf(
                0.96f, 0f, 0f, 0f, 2f,
                0f, 1.00f, 0f, 0f, 4f,
                0f, 0f, 1.14f, 0f, 6f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    ColorMatrixColorFilter(m)
}

/**
 * 底图外观：卫星/标准=原色，深色=夜间滤镜 + 深色底。
 *
 * 滤镜挂在 [org.osmdroid.views.overlay.TilesOverlay] 上，不动瓦片数据，
 * 已缓存的瓦片也能立刻变色，切图层不产生任何新的网络请求。
 */
private fun applyTileAppearance(v: MapView, dark: Boolean) {
    val tiles = runCatching { v.overlayManager?.tilesOverlay }.getOrNull()
    tiles?.let { t ->
        t.setColorFilter(if (dark) NIGHT_FILTER else null)
        // 瓦片缺失时 osmdroid 默认画白底灰线，在夜间模式下会闪一片白
        if (dark) {
            t.setLoadingBackgroundColor(DARK_MAP_BG)
            t.setLoadingLineColor(0xFF232A36.toInt())
        } else {
            t.setLoadingBackgroundColor(android.graphics.Color.WHITE)
            t.setLoadingLineColor(android.graphics.Color.LTGRAY)
        }
    }
    // 浅色底图也要给不透明底色：缺瓦片时若留透明，页面背景（含自定义背景图）
    // 会从地图中间透出来
    v.setBackgroundColor(if (dark) DARK_MAP_BG else LIGHT_MAP_BG)
    v.invalidate()
}

/**
 * 地图遥控器：把 osmdroid 的 MapView 句柄暴露给 Compose 侧的按钮（放大/缩小/回到足迹/定位）。
 * `ready` 是 Compose 状态，MapView 创建完成后置 true，用来驱动覆盖物的首次刷新。
 */
private class MapHandle {
    var view: MapView? = null
        private set
    var ready by mutableStateOf(false)
        private set

    // 上一次销毁时的视角。卡片地图在 LazyColumn 里会被回收重建，进/出全屏也会重建 MapView，
    // 不记住的话每次重建都回到默认的中国全图，用户刚拖到的位置就白找了。
    var savedCenter: GeoPoint? = null
        private set
    var savedZoom: Double? = null
        private set

    // 最多保留一个「等布局」的镜头动作：新的直接顶掉旧的，否则连点两次会叠加两段动画
    private var pendingListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var pendingView: MapView? = null

    /**
     * 用户手指拖动地图时回调。
     * 记录中镜头会自动跟着当前位置走，用户想自己看看周围时不能一直被拽回去。
     */
    var onUserPan: (() -> Unit)? = null

    fun attach(v: MapView) {
        view = v
        ready = true
    }

    fun detach() {
        cancelPending()
        // 先读视角再放掉引用；此时 MapView 还没 onDetach，内部滚动状态仍然可读
        view?.let { v ->
            runCatching {
                val c = v.mapCenter
                val z = v.zoomLevelDouble
                savedCenter = GeoPoint(c.latitude, c.longitude)
                savedZoom = z
            }
        }
        view = null
        ready = false
    }

    fun zoomIn() = view?.controller?.zoomIn()
    fun zoomOut() = view?.controller?.zoomOut()

    /**
     * 把另一个 MapView 当前的视角抄过来。
     * 卡片地图和全屏地图是两个独立实例，不抄的话点「全屏」会从卡片看到的位置
     * 直接跳回中国全图，看起来像丢了自己的位置。
     */
    fun adoptViewFrom(other: MapHandle) {
        val v = other.view
        if (v != null) {
            runCatching {
                val c = v.mapCenter
                savedCenter = GeoPoint(c.latitude, c.longitude)
                savedZoom = v.zoomLevelDouble
            }
        } else {
            savedCenter = other.savedCenter
            savedZoom = other.savedZoom
        }
    }

    fun moveTo(point: GeoPoint, zoom: Double? = null) {
        val v = view ?: return
        schedule(v) {
            if (zoom != null) it.controller.setZoom(zoom)
            it.controller.animateTo(point)
        }
    }

    /**
     * 搜索排序用的参考点。
     * 只在视野足够近（zoom ≥ 8）时才认地图中心：停在中国全图时中心落在青海，
     * 拿它当「当前位置」排序，搜「人民公园」会给出青海的结果，比不排序还糟。
     */
    fun sortAnchor(): GeoPoint? {
        val v = view
        if (v != null) {
            val z = runCatching { v.zoomLevelDouble }.getOrDefault(0.0)
            if (z >= 8.0) {
                return runCatching { GeoPoint(v.mapCenter.latitude, v.mapCenter.longitude) }.getOrNull()
            }
        }
        val z = savedZoom ?: return null
        return if (z >= 8.0) savedCenter else null
    }

    /**
     * 直接落镜头（不做动画）。
     * 必须在 View 完成布局之后调用：MapView 刚创建时是 0x0 视口，
     * 此时 setCenter 会把目标点当成「左上角」，整张图会偏出去几百公里。
     */
    fun snapTo(point: GeoPoint, zoom: Double) {
        val v = view ?: return
        schedule(v) {
            it.controller.setZoom(zoom)
            it.controller.setCenter(point)
            it.invalidate()
        }
    }

    /** 缩放到能装下全部足迹的范围；单个点直接给固定层级，否则会一路顶到最大缩放 */
    fun fit(points: List<GeoPoint>) {
        val v = view ?: return
        // 多条足迹坐标完全相同时去重后只剩一个点，不去重会得到零宽高 bbox 顶到最大缩放 19
        val pts = points.distinctBy { it.latitude to it.longitude }
        if (pts.isEmpty()) return
        schedule(v) {
            if (pts.size == 1) {
                it.controller.setZoom(ZOOM_SINGLE)
                it.controller.animateTo(pts.first())
            } else {
                it.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts), false, 80)
            }
        }
    }

    /**
     * 等 View 真正完成布局再动镜头。
     * 之前 zoomToBoundingBox 是紧跟 MapView 创建就调的，那时 width/height 还是 0，
     * 算出来的层级是错的，所以视口永远卡在默认那一格。
     */
    private fun schedule(v: MapView, action: (MapView) -> Unit) {
        cancelPending()
        if (v.width > 0 && v.height > 0) {
            action(v)
            return
        }
        val observer = v.viewTreeObserver
        if (!observer.isAlive) return
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (v.width <= 0 || v.height <= 0) return
                pendingListener = null
                pendingView = null
                removePending(v, this)
                action(v)
            }
        }
        pendingListener = listener
        pendingView = v
        observer.addOnGlobalLayoutListener(listener)
    }

    /**
     * 移除还没触发的 listener。View 从未布局就被移走（全屏秒开秒关）时，
     * 不主动摘掉它就会一直挂住并强引用 MapView；View detach 后 observer 也不再存活，
     * 这时候 remove 会抛 IllegalStateException，所以必须先判断 isAlive。
     */
    fun cancelPending() {
        val v = pendingView
        val l = pendingListener
        pendingView = null
        pendingListener = null
        if (v != null && l != null) removePending(v, l)
    }

    private fun removePending(v: MapView, l: ViewTreeObserver.OnGlobalLayoutListener) {
        // attached 前后 viewTreeObserver 可能是两个对象，这里必须重新取一次
        val observer = v.viewTreeObserver
        if (observer.isAlive) runCatching { observer.removeOnGlobalLayoutListener(l) }
    }
}

/** 一次聚焦请求。token 保证「同一个点再点一次」也能重新触发 */
private data class FocusRequest(val token: Long, val point: GeoPoint, val zoom: Double?)

/** 地图要渲染的全部内容，卡片版和全屏版共用同一份 */
private data class MapScene(
    val footprints: List<Footprint>,
    /** TSP 顺路连线，后台线程算好后按足迹列表缓存，这里只消费不重算 */
    val track: List<GeoPoint>,
    val heatOn: Boolean,
    val layer: String,
    val myLocation: GeoPoint?,
    /** 当前位置的精度半径（米）；>0 时画出精度圈，避免给人「精确到点」的假象 */
    val myAccuracyM: Float = 0f,
    val poi: PoiResult?,
    val focus: FocusRequest?,
    /** 自动记录中的实时轨迹（GCJ-02），画成实线 */
    val liveTrack: List<GeoPoint> = emptyList(),
    /** 要高亮的省（走过的省），按高德 adcode */
    val provinceCodes: Set<String> = emptySet(),
    /** 分色路线：每条行程/片区一条线，各有各的颜色 */
    val segments: List<RouteSegment> = emptyList(),
    /** 城市名标签，颜色跟着所属路线走 */
    val labels: List<MapLabel> = emptyList(),
    /** 走过的省是否高亮 */
    val provinceOn: Boolean = true,
    /** 是否用分色路线（false = 旧的单条青色顺路连线） */
    val coloredRoutes: Boolean = true,
    /** 是否显示足迹之间的连线。关闭后地图只留点位，线由用户按需打开 */
    val routeOn: Boolean = true,
    /** 是否显示城市名 */
    val cityLabels: Boolean = true
)

// 坐标转换统一收在 data/Locator 与 data/TrackRecorder 里：
// Locator.singleFix / observe 交出来的 GeoFix 已经是 GCJ-02，页面直接用，不要再转一次。

/**
 * 以 [center] 为圆心、[radiusM] 米为半径的正多边形顶点。
 * 用等距圆柱近似取经度方向缩放，精度圈这种「几米到几百米」的尺度足够。
 */
private fun accuracyCirclePoints(
    center: GeoPoint,
    radiusM: Double,
    segments: Int = 48
): List<GeoPoint> {
    val latDelta = radiusM / 111_320.0
    val lonScale = cos(Math.toRadians(center.latitude)).coerceAtLeast(0.01)
    val lonDelta = radiusM / (111_320.0 * lonScale)
    return (0 until segments).map { i ->
        val a = 2 * PI * i / segments
        GeoPoint(center.latitude + latDelta * sin(a), center.longitude + lonDelta * cos(a))
    }
}

private class MapIcons(
    val orange: Drawable,
    val gold: Drawable,
    val teal: Drawable,
    val purple: Drawable,
    /** 特别地点用的金星：Canvas 现画，任意屏幕密度下都锐利 */
    val star: Drawable,
    /** 当前位置蓝点：实心圆 + 白圈，和足迹图标区分开 */
    val myDot: Drawable
)

/**
 * 足迹页：顶部「地图足迹 / 旅行回忆」分段，把原来的回忆 Tab 收进同一页。
 * 地图模式沿用原地图内容，回忆模式直接复用 MemoryScreen（不再自带标题栏）。
 */
@Composable
fun MapScreen(state: AppState) {
    if (state.footprintMode == 1) {
        Column(modifier = Modifier.fillMaxSize().background(Bg.Background)) {
            AppTopBar(
                title = tr("足迹"),
                subtitle = tr("${state.footprints.size} 条回忆 · ${state.routes.size} 段旅程", "${state.footprints.size} memories · ${state.routes.size} journeys"),
                rightContent = { DiscoverTopBarActions(state) }
            )
            FootprintModeTabs(mode = 1, onSelect = { state.footprintMode = it })
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                MemoryScreen(state, embedded = true)
            }
        }
    } else {
        MapContent(state)
    }
}

/** 足迹页内的「地图足迹 / 旅行回忆」分段控件 */
@Composable
private fun FootprintModeTabs(mode: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(Bg.Surface)
            .padding(4.dp)
    ) {
        listOf("地图足迹", "旅行回忆").forEachIndexed { index, label ->
            val selected = mode == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50.dp))
                    .then(
                        if (selected) Modifier.background(
                            brush = Brush.linearGradient(MainGradient),
                            shape = RoundedCornerShape(50.dp)
                        ) else Modifier
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tr(label),
                    color = if (selected) Color.White else Text.Secondary,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/** 地图足迹模式（原「足迹地图」页内容） */
@Composable
private fun MapContent(state: AppState) {
    val context = LocalContext.current

    var selectedId by remember { mutableStateOf<Int?>(null) }
    var fullscreen by remember { mutableStateOf(false) }

    // 地图相关：热力 / 图层 / 我的位置 / 待聚焦的点
    var heatOn by remember { mutableStateOf(false) }
    var layer by remember { mutableStateOf("vector") }
    var myLoc by remember { mutableStateOf<GeoPoint?>(null) }
    var focus by remember { mutableStateOf<FocusRequest?>(null) }
    var focusToken by remember { mutableStateOf(0L) }
    var mapFailed by remember { mutableStateOf(false) }

    // 参考图风格的开关：省域高亮 / 分色路线 / 足迹连线 / 城市名标签
    var provinceOn by remember { mutableStateOf(true) }
    var coloredOn by remember { mutableStateOf(true) }
    var routeOn by remember { mutableStateOf(true) }
    var labelOn by remember { mutableStateOf(true) }
    var stylePanelOpen by remember { mutableStateOf(false) }
    /** 离线地图管理页是否打开（盖在地图之上的整页） */
    var offlineOpen by remember { mutableStateOf(false) }

    // 「我的」页点「离线地图包」时由 AppState 发一个请求过来，直接把管理面板打开，
    // 而不是把用户丢到别的页面上
    LaunchedEffect(state.mapPacksOpenTick) {
        if (state.mapPacksOpenTick > 0) offlineOpen = true
    }

    // 省域高亮开启时自动切深色底图，让浅蓝多边形跳出来；关闭时还原用户之前选的底图
    var preHighlightLayer by remember { mutableStateOf("vector") }
    LaunchedEffect(provinceOn) {
        if (provinceOn) {
            if (layer != "dark") {
                preHighlightLayer = layer
                layer = "dark"
            }
        } else {
            if (layer == "dark") layer = preHighlightLayer
        }
    }

    // 卡片版和全屏版各持有一个 MapView 句柄，同一时刻只有一个实例存活
    val cardHandle = remember { MapHandle() }
    val fsHandle = remember { MapHandle() }

    val mapIcons = remember(context) {
        val dm = context.resources.displayMetrics
        MapIcons(
            orange = ContextCompat.getDrawable(context, R.drawable.marker_orange)!!,
            gold = ContextCompat.getDrawable(context, R.drawable.marker_gold)!!,
            teal = ContextCompat.getDrawable(context, R.drawable.marker_teal)!!,
            purple = ContextCompat.getDrawable(context, R.drawable.marker_purple)!!,
            star = StarMarkerDrawable(sizePx = (16f * dm.density).toInt()),
            myDot = ContextCompat.getDrawable(context, R.drawable.ic_my_location_dot)!!
        )
    }

    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    // ---------- 地点 / POI 搜索 ----------
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<PoiResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var selectedPoi by remember { mutableStateOf<PoiResult?>(null) }
    var currentLocation by remember { mutableStateOf<CityHit?>(null) }

    fun requestFocus(point: GeoPoint, zoom: Double) {
        focusToken += 1
        focus = FocusRequest(focusToken, point, zoom)
    }

    // 获取当前位置用于搜索排序：只读缓存，别为了排序在后台一直搜星
    LaunchedEffect(Unit) {
        currentLocation = runCatching {
            WeatherApi.locateCity(context, allowLive = false)
        }.getOrNull()
    }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            hits = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(450)
        // 排序参考：先看用户当前盯着哪一块地图，再退回「我所在的位置」。
        // 原来只用启动时读到的一次位置缓存——人在成都、缓存还是北京，
        // 搜出来的结果就全在北京，看起来像「定位错了」。
        val anchor = (if (fullscreen) fsHandle else cardHandle).sortAnchor()
        hits = runCatching {
            AmapApi.searchPlaces(
                keyword = q,
                currentLat = anchor?.latitude ?: currentLocation?.lat,
                currentLon = anchor?.longitude ?: currentLocation?.lon,
                count = 20
            )
        }
            .onFailure { state.toast(tr("地点搜索失败，请稍后重试")) }
            .getOrDefault(emptyList())
        searchError = AmapApi.lastErrorHint
        searching = false
    }

    fun focusPoi(p: PoiResult) {
        selectedPoi = p
        // 搜索结果是「看地点」，直接进全屏，卡片那点高度不够用
        fullscreen = true
        requestFocus(GeoPoint(p.lat, p.lon), ZOOM_POI)
        query = ""
        hits = emptyList()
        keyboard?.hide()
    }

    /** 点卡片进全屏：先把卡片当前视角抄给全屏地图，避免跳回中国全图 */
    fun openFullscreen() {
        fsHandle.adoptViewFrom(cardHandle)
        fullscreen = true
    }

    /** 退出全屏：把全屏里拖到的位置抄回卡片，否则卡片会跳回进全屏之前的位置 */
    fun closeFullscreen() {
        cardHandle.adoptViewFrom(fsHandle)
        fullscreen = false
    }

    // ---------- 定位 ----------
    var locating by remember { mutableStateOf(false) }
    /** 当前定位的精度半径（米），和 myLoc 一起更新，用来画精度圈 */
    var myAcc by remember { mutableStateOf<Float?>(null) }
    /** 镜头是否跟着当前位置走；用户手动拖地图就自动关掉 */
    var followMe by remember { mutableStateOf(false) }

    // 卡片地图和全屏地图都要能感知「用户自己拖了」
    cardHandle.onUserPan = { followMe = false }
    fsHandle.onUserPan = { followMe = false }

    fun locateFailText(reason: String?): String = when (reason) {
        Locator.REASON_PERMISSION -> tr("未授予定位权限，无法定位")
        Locator.REASON_SERVICE_OFF -> tr("系统定位已关闭，请在设置中打开「位置信息」")
        else -> tr("定位超时，请到空旷处或靠近窗口再试")
    }

    /**
     * 一次精确定位：缓存优先 → 不够新就主动搜（最多 15 秒）。
     * 定位结果已经是 GCJ-02，可以直接落到底图上，不再需要二次转换。
     */
    fun locateMe() {
        val reason = Locator.problem(context)
        if (reason != null) {
            state.toast(locateFailText(reason))
            return
        }
        if (locating) return
        locating = true
        // 搜星最多要十几秒，先给个反馈，否则用户会以为按钮没反应而反复点
        state.toast(tr("正在获取位置…"))
        scope.launch {
            val fix = runCatching { Locator.singleFix(context, 15_000L) }.getOrNull()
            locating = false
            if (fix == null) {
                state.toast(locateFailText(Locator.problem(context) ?: Locator.REASON_TIMEOUT))
                return@launch
            }
            val pt = GeoPoint(fix.lat, fix.lon)
            myLoc = pt
            myAcc = fix.accuracyM
            fullscreen = true
            requestFocus(pt, ZOOM_ME)
            followMe = true
            state.toast(
                tr(
                    "已定位 · 精度 ±${fix.accuracyM.roundToInt()} 米",
                    "Located · ±${fix.accuracyM.roundToInt()} m"
                )
            )
        }
    }

    // 精确位置与大致位置一起要：只给 COARSE 的话只能拿到基站级定位，误差几百米起
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        when {
            fine -> locateMe()
            coarse -> {
                state.toast(tr("只给了大致位置权限，定位精度会差到几百米"))
                locateMe()
            }
            else -> state.toast(tr("未授予定位权限，无法定位"))
        }
    }

    // 从相册导入照片 → 读 EXIF GPS 自动生成足迹
    val photoImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val n = state.importPhotosAsFootprints(uris)
        state.toast(
            if (n > 0) tr("已从 $n 张照片生成足迹", "Created $n footprints from photos")
            else tr("所选照片没有 GPS 位置信息", "No GPS location in selected photos")
        )
    }

    fun requestLocate() {
        when {
            Locator.hasFine(context) -> locateMe()
            Locator.hasAny(context) -> {
                state.toast(tr("只给了大致位置权限，定位精度会差到几百米"))
                locateMe()
            }
            else -> locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // 定位之后持续更新蓝点（4 秒一次），走出几百米不会还停在原地。
    // 离开页面必须停，否则定位器一直开着耗电。
    DisposableEffect(followMe) {
        if (!followMe) return@DisposableEffect onDispose {}
        val stop = Locator.observe(context, intervalMs = 4_000L, minMeters = 3f) { fix ->
            myLoc = GeoPoint(fix.lat, fix.lon)
            myAcc = fix.accuracyM
        }
        onDispose { stop() }
    }

    // 记录中不再需要手动点定位：采到的点本身就是实时位置
    LaunchedEffect(state.trackPoints.size, state.trackingOn) {
        if (!state.trackingOn) return@LaunchedEffect
        state.trackPoints.lastOrNull()?.let { p ->
            myLoc = GeoPoint(p.lat, p.lon)
            myAcc = p.accuracy.takeIf { it > 0f } ?: myAcc
        }
    }

    val visible = state.footprints
    val selected = state.footprints.firstOrNull { it.id == selectedId }
    val specialCount = state.footprints.count { it.special }
    // 统计栏口径：只认真实足迹点（云端演示统计不参与，避免「0 足迹却有统计」的矛盾）
    val visitedCities = visible.map { it.city }.distinct().size
    val totalFootprints = visible.size
    // TSP 顺路 + 总里程：O(n²) 算法，挪到后台线程算，结果按足迹列表缓存。
    // 之前 totalKm 在组合期间同步算、rebuildOverlays 在主线程再算一遍，
    // 足迹一多每次切热力/进全屏都要全量重算，直接 ANR。
    var totalKm by remember { mutableStateOf(0) }
    var trackPoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    LaunchedEffect(state.footprints) {
        val fps = state.footprints
        val computed = withContext(Dispatchers.Default) {
            val pts = fps.map { it.toGeoPoint() }
                .distinctBy { it.latitude to it.longitude }
                .map { AlgoGeoPoint(it.latitude, it.longitude) }
            val ordered = runCatching { tspOrder(pts) }.getOrElse { pts }
            val km = if (ordered.size < 2) 0
            else runCatching {
                ordered.zipWithNext().sumOf { (a, b) -> haversineKm(a, b) }.toInt()
            }.getOrDefault(0)
            // 点数较多时做 Douglas-Peucker 抽稀，去掉近似共线的冗余拐点。
            // 足迹是全国尺度（城市间距几百公里），固定 0.5km 的阈值几乎删不掉任何点，白算一遍；
            // 改成按点集外接矩形对角线动态取阈值（对角的 2%，最少 0.5km）。
            val simplified = if (ordered.size > 20) {
                val latMin = ordered.minOf { it.lat }
                val latMax = ordered.maxOf { it.lat }
                val lonMin = ordered.minOf { it.lon }
                val lonMax = ordered.maxOf { it.lon }
                val diagonalKm = haversineKm(
                    AlgoGeoPoint(latMin, lonMin),
                    AlgoGeoPoint(latMax, lonMax)
                )
                val toleranceKm = maxOf(0.5, diagonalKm * 0.02)
                runCatching { douglasPeucker(ordered, toleranceKm) }.getOrElse { ordered }
            } else ordered
            Pair(simplified.map { GeoPoint(it.lat, it.lon) }, km)
        }
        trackPoints = computed.first
        totalKm = computed.second
    }

    // DBSCAN 片区聚类：在后台线程计算，结果显示在统计栏下方
    var clusterText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.footprints) {
        val fps = state.footprints
        if (fps.isEmpty()) { clusterText = null; return@LaunchedEffect }
        clusterText = withContext(Dispatchers.Default) {
            runCatching {
                val pts = fps.map { fp ->
                    val g = fp.toGeoPoint()
                    AlgoGeoPoint(g.latitude, g.longitude, fp.city)
                }
                val summary = clusterSummary(pts)
                if (summary.isEmpty()) {
                    tr("足迹遍布各地，还没有形成明显片区", "Footprints are scattered — no clear clusters yet")
                } else {
                    tr("🗺 已识别 ${summary.size} 个旅行片区：", "🗺 ${summary.size} travel clusters:") +
                        summary.joinToString(" · ") {
                            tr("${it.first}片区×${it.second}", "${it.first} ×${it.second}")
                        }
                }
            }.getOrNull()
        }
    }

    // ---------- 参考图风格的三件套 ----------
    // 分色路线 / 走过的省 / 城市名标签。三样都依赖足迹坐标，
    // 而且里面有 TSP 和省界射线法判定，必须放后台线程。
    var segments by remember { mutableStateOf<List<RouteSegment>>(emptyList()) }
    var provinceCodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var labels by remember { mutableStateOf<List<MapLabel>>(emptyList()) }

    LaunchedEffect(state.footprints, state.routes) {
        val fps = state.footprints
        val rts = state.routes
        val computed = withContext(Dispatchers.Default) {
            runCatching {
                val segs = buildRouteSegments(fps, rts)

                // 走过的省：省界是 GCJ-02，和足迹坐标同一坐标系，直接判定即可
                ProvinceBoundaryData.load(context)
                val codes = ProvinceBoundaryData.provincesOf(
                    fps.map { fp ->
                        val g = fp.toGeoPoint()
                        g.longitude to g.latitude
                    }
                ).map { it.adcode }.toSet()

                // 城市名标签：颜色跟着所属路线走（参考图里绿线配绿字）
                val colorOf = HashMap<Int, Int>()
                segs.forEach { s -> s.footprintIds.forEach { id -> colorOf[id] = s.color } }
                val lbls = fps.distinctBy { it.city }.map { fp ->
                    MapLabel(fp.toGeoPoint(), tr(fp.city), colorOf[fp.id] ?: LABEL_NEUTRAL)
                }

                Triple(segs, codes, lbls)
            }.getOrElse { Triple(emptyList(), emptySet(), emptyList()) }
        }
        segments = computed.first
        provinceCodes = computed.second
        labels = computed.third
    }

    val scene = MapScene(
        footprints = visible,
        track = trackPoints,
        heatOn = heatOn,
        layer = layer,
        myLocation = myLoc,
        myAccuracyM = myAcc ?: 0f,
        poi = selectedPoi,
        focus = focus,
        liveTrack = state.trackPoints.map { GeoPoint(it.lat, it.lon) },
        provinceCodes = provinceCodes,
        segments = segments,
        labels = labels,
        provinceOn = provinceOn,
        coloredRoutes = coloredOn,
        routeOn = routeOn,
        cityLabels = labelOn
    )

    // 开始记录时自动进入跟随状态：否则用户点完「开启」地图还停在原来的视角，
    // 看不到自己走到哪。手动拖一下地图就会退出跟随。
    LaunchedEffect(state.trackingOn) {
        if (state.trackingOn) followMe = true
    }

    // 记录中镜头跟着当前位置走（5 秒一个点，走起来就是实时的）。
    // 用户手动拖过地图就不再抢镜头，否则想看旁边一眼就被拽回去。
    LaunchedEffect(state.trackingOn, state.trackPoints.size, followMe, fullscreen) {
        if (!state.trackingOn || !followMe) return@LaunchedEffect
        val last = state.trackPoints.lastOrNull() ?: return@LaunchedEffect
        // 同一时刻只有一个 MapView 存活：全屏时卡片地图已被移除
        val target = if (fullscreen) fsHandle else cardHandle
        target.moveTo(GeoPoint(last.lat, last.lon), ZOOM_TRACK)
    }

    Box(modifier = Modifier.fillMaxSize().background(Bg.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 固定标题栏（不随滚动）：状态栏 insets 动态适配 + 纯文字标题 + 发丝分割线
            AppTopBar(
                title = tr("足迹"),
                subtitle = if (Lang.isEn) {
                    "${state.footprints.map { it.city }.distinct().size} cities · ${state.footprints.size} footprints"
                } else {
                    "${state.footprints.map { it.city }.distinct().size} 城 · ${state.footprints.size} 条足迹"
                },
                rightContent = { DiscoverTopBarActions(state) }
            )
            // 地图 / 回忆分段：回忆已并入足迹页
            FootprintModeTabs(mode = 0, onSelect = { state.footprintMode = it })
            TrackPanel(state)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 0.dp, bottom = 96.dp)
            ) {
            // 顶部：标签 + 图标（交互按钮在内容区，不随标题栏）
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 标签胶囊
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Bg.SurfaceVariant)
                            .padding(4.dp)
                    ) {
                        // 项目里没有「关注」数据源，之前那个 Tab 点了没任何反应，比没有更糟，去掉
                        TabPill(text = tr("我的足迹"), selected = true, onClick = {})
                    }
                    Spacer(Modifier.weight(1f))
                    // 「收藏」原来只弹一句 toast，没有任何数据源，点了等于没点，去掉
                    Spacer(Modifier.width(10.dp))
                    RoundIconBtn(Icons.Outlined.AddPhotoAlternate, tr("导入照片")) { photoImportLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    Spacer(Modifier.width(10.dp))
                    RoundIconBtn(Icons.Outlined.MyLocation, tr("定位")) { requestLocate() }
                }
            }

            // 地点 / POI 搜索
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50.dp))
                            .background(Bg.Surface)
                            .cardShadow(RoundedCornerShape(50.dp))
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Search, tr("搜索地点"), tint = Text.Muted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(tr("搜索地点 / 景区 / 美食"), color = Text.Muted, fontSize = 13.sp)
                            },
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
                        } else if (query.isNotEmpty()) {
                            Icon(
                                Icons.Outlined.Close,
                                tr("清空"),
                                tint = Text.Muted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable {
                                        query = ""
                                        hits = emptyList()
                                    }
                            )
                        }
                    }

                    // 搜索结果
                    if (query.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Bg.Surface)
                                .cardShadow(RoundedCornerShape(20.dp))
                        ) {
                            if (hits.isEmpty() && !searching) {
                                val err = searchError
                                Text(
                                    if (err != null) err else "没有找到「${query.trim()}」，换个关键词试试",
                                    color = if (err != null) Brand.Terracotta else Text.Muted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
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
                            hits.take(8).forEachIndexed { index, p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { focusPoi(p) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.Place, contentDescription = null, tint = Text.Secondary, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                p.name,
                                                color = Text.Primary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (p.distance > 0) {
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    when {
                                                        p.distance >= 1000 -> "%.1fkm".format(p.distance / 1000)
                                                        else -> "%.0fm".format(p.distance)
                                                    },
                                                    color = Brand.Terracotta,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        if (p.address.isNotBlank()) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                p.address,
                                                color = Text.Muted,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                if (index < hits.take(8).lastIndex) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 38.dp, end = 14.dp)
                                            .height(1.dp)
                                            .background(Bg.Outline)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 地图预览卡片：整块点击进全屏，本身不接管手势，页面滚动不受影响
            if (!fullscreen) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(220.dp)
                            .clip(MaterialTheme.shapes.large)
                            // 瓦片还没加载出来时露出的是地图卡底，不能是透明，
                            // 否则自定义背景图会从地图中间穿出来
                            .background(Layer.Opaque)
                            .cardShadowMedium()
                    ) {
                        if (mapFailed) {
                            MapFailedPlaceholder(
                                onRetry = { mapFailed = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            FootprintMap(
                                scene = scene,
                                interactive = false,
                                handle = cardHandle,
                                icons = mapIcons,
                                onMarkerClick = { selectedId = it.id },
                                onFocusConsumed = { focus = null },
                                onFailure = { mapFailed = true },
                                modifier = Modifier.fillMaxSize()
                            )
                            // 透明点击层：点任意位置进全屏（放在地图之上，顺带把拖动留给页面滚动）
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { openFullscreen() }
                            )
                            // 腾讯地图版权署名
                            MapAttribution()
                            // 右上「全屏」入口
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(Bg.Surface.copy(alpha = 0.92f))
                                    .clickable { openFullscreen() }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Fullscreen,
                                    tr("全屏"),
                                    tint = Brand.Terracotta,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    tr("全屏"),
                                    color = Brand.Terracotta,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 统计栏：0 足迹时四个 0 排一行纯属噪音，直接不显示
            if (visible.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(Bg.Surface)
                            .cardShadow()
                            .padding(vertical = 18.dp)
                    ) {
                        StatItem(visitedCities, tr("走过城市"), modifier = Modifier.weight(1f))
                        StatItem(totalFootprints, tr("足迹数"), modifier = Modifier.weight(1f))
                        StatItem(totalKm, tr("总里程(km)"), modifier = Modifier.weight(1f))
                        StatItem(specialCount, tr("特别地点"), modifier = Modifier.weight(1f))
                    }
                }
            }

            // 片区聚类摘要（DBSCAN）
            clusterText?.takeIf { visible.isNotEmpty() }?.let { text ->
                item {
                    Text(
                        text = text,
                        color = Text.Secondary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(Bg.Surface)
                            .cardShadow()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            // 列表标题（0 条时不显示「我的足迹 · 0 处」这种自我重复）
            if (visible.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("我的足迹 · ${state.footprints.size} 处", "My footprints · ${state.footprints.size} places"),
                            color = Text.Primary,
                            style = Type.sectionTitle
                        )
                    }
                }
            }

            // 足迹列表
            if (visible.isEmpty()) {
                // 0 足迹是「首次打开」的默认状态，不能只给一句灰字：
                // 直接给出下一步（记录 / 定位），否则用户不知道这块地图能干什么
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(Bg.Surface)
                            .cardShadow()
                            .padding(horizontal = 20.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = tr("还没有足迹", "No footprints yet"),
                            color = Text.Primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = tr(
                                "记录第一段旅程，地图上就会亮起属于你的第一个点",
                                "Record your first trip and your first point lights up on the map"
                            ),
                            color = Text.Muted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(
                            text = tr("记录第一段旅程", "Record a trip"),
                            onClick = { state.openUpload() }
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tr("或者先定位看看我在哪", "Or locate yourself first"),
                            color = Brand.Terracotta,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .clickable { requestLocate() }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                }
            } else {
                items(visible, key = { it.id }) { fp ->
                    FootprintItem(fp, onClick = { selectedId = fp.id })
                }
            }
        }            // ← LazyColumn
        }            // ← Column 结束。全屏地图必须留在这之后（作 Box 的兄弟），
                     //   塞进 Column 里会被挤成「页头还在、地图只剩一小块」的样子

        // ---------- 全屏地图 ----------
        // 失败时也要待在全屏里：否则 BackHandler 和「收起」都没了，用户会被困住，
        // 系统返回键直接穿透到外层页面
        if (fullscreen) {
            BackHandler { closeFullscreen() }
            // 必须用实心底：自定义背景开启时 Bg.Background 是透明的，
            // 全屏地图这一层会透出下面的足迹页（卡片、统计栏、标签叠在一起），
            // 看起来就是「两个页面重叠」。
            Box(modifier = Modifier.fillMaxSize().background(Layer.Opaque)) {
                if (mapFailed) {
                    MapFailedPlaceholder(
                        onRetry = { mapFailed = false },
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        RoundIconBtn(Icons.Outlined.Close, tr("收起")) { closeFullscreen() }
                    }
                } else {
                    FootprintMap(
                        scene = scene,
                        interactive = true,
                        handle = fsHandle,
                        icons = mapIcons,
                        onMarkerClick = { selectedId = it.id },
                        onFocusConsumed = { focus = null },
                        onFailure = { mapFailed = true },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 样式面板打开时，地图上的触摸先被这层透明区域吃掉（点面板外面关闭）。
                    // 必须放在工具栏之前：放在后面会把工具栏的按钮也一起盖掉。
                    if (stylePanelOpen) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { stylePanelOpen = false }
                        )
                    }

                    // 顶部：收起 / 热力图 / 地图样式
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RoundIconBtn(Icons.Outlined.Close, tr("收起")) { closeFullscreen() }
                        Spacer(Modifier.weight(1f))
                        RoundIconBtn(Icons.Outlined.CloudDownload, tr("离线地图")) { offlineOpen = true }
                        Spacer(Modifier.width(8.dp))
                        RoundIconBtn(
                            Icons.Outlined.Map,
                            tr("地图样式"),
                            active = provinceOn && coloredOn && routeOn && labelOn
                        ) { stylePanelOpen = !stylePanelOpen }
                    }

                    // 地图样式面板：参考图风格的四件套
                    if (stylePanelOpen) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 58.dp, end = 12.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Bg.Surface)
                                .cardShadowMedium()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // 底图三选：标准 / 卫星 / 深色（省域高亮时自动锁深色）
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tr("底图"),
                                    color = Text.Primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(78.dp)
                                )
                                Spacer(Modifier.weight(1f))
                                listOf(
                                    "vector" to tr("标准"),
                                    "satellite" to tr("卫星"),
                                    "dark" to tr("深色")
                                ).forEach { (k, label) ->
                                    val active = layer == k
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 4.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(if (active) Brand.Terracotta else Bg.SurfaceVariant)
                                            .clickable { layer = k }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            label,
                                            color = if (active) Color.White else Text.Secondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            StyleToggleRow(tr("省域高亮"), provinceOn) { provinceOn = !provinceOn }
                            StyleToggleRow(tr("分色路线"), coloredOn) { coloredOn = !coloredOn }
                            StyleToggleRow(tr("足迹连线", "Footprint links"), routeOn) { routeOn = !routeOn }
                            StyleToggleRow(tr("城市名"), labelOn) { labelOn = !labelOn }
                        }
                    }

                    // 右下：缩放 / 回到足迹 / 定位
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RoundIconBtn(Icons.Outlined.Add, tr("放大")) { fsHandle.zoomIn() }
                        RoundIconBtn(Icons.Outlined.Remove, tr("缩小")) { fsHandle.zoomOut() }
                        // 只在记录中且已有轨迹（至少两个点才连得成线）时出现
                        if (state.trackingOn && scene.liveTrack.size > 1) {
                            RoundIconBtn(Icons.Outlined.Route, tr("跟随轨迹")) {
                                fsHandle.fit(scene.liveTrack)
                            }
                        }
                        RoundIconBtn(Icons.Outlined.FullscreenExit, tr("回到足迹范围")) {
                            if (visible.isEmpty()) {
                                state.toast(tr("还没有足迹，点定位看看自己在哪"))
                            } else {
                                fsHandle.fit(visible.map { it.toGeoPoint() })
                            }
                        }
                        RoundIconBtn(Icons.Outlined.MyLocation, tr("定位")) { requestLocate() }
                    }

                    MapAttribution()

                    // 0 足迹进全屏时地图上只有底图，没有任何落脚点：
                    // 给一张居中的引导卡，否则就是一屏看不出所以然的地形。
                    // "我知道了"只是本次进入收起，下次再进全屏空态还会出现。
                    var dismissEmptyHint by remember { mutableStateOf(false) }
                    if (visible.isEmpty() && !dismissEmptyHint) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 40.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Bg.Surface.copy(alpha = 0.96f))
                                .cardShadowMedium()
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = tr("还没有足迹", "No footprints yet"),
                                color = Text.Primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = tr(
                                    "拖动地图看看，或用右下角定位；记录一次旅程后这里就会亮起你的足迹",
                                    "Drag the map or tap locate; record a trip and your footprints show up here"
                                ),
                                color = Text.Muted,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton(
                                text = tr("记录第一段旅程", "Record a trip"),
                                onClick = {
                                    fullscreen = false
                                    state.openUpload()
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = tr("我知道了", "Got it"),
                                color = Text.Muted,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .clickable { dismissEmptyHint = true }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // 记录中的导航信息条：全屏时 TrackPanel 被盖在下面，看不到走了多远。
                    // 抬到 64dp 是为了避开左下角的比例尺（34dp）和「© 腾讯地图」署名。
                    if (state.trackPoints.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, bottom = 64.dp)
                                .clip(RoundedCornerShape(50.dp))
                                .background(Bg.Surface.copy(alpha = 0.94f))
                                .cardShadow(RoundedCornerShape(50.dp))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (state.trackingOn) Color(LIVE_LINE_COLOR) else Text.Muted)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (state.trackingOn) {
                                    tr("记录中 · %.2f km · %d 点", "Recording · %.2f km · %d pts").format(
                                        state.trackDistanceKm(),
                                        state.trackPoints.size
                                    )
                                } else {
                                    tr("已暂停 · %d 点待处理", "Paused · %d pts pending").format(state.trackPoints.size)
                                },
                                color = Text.Primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // 选中的搜索地点
                    selectedPoi?.let { p ->
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 88.dp, start = 16.dp, end = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Bg.Surface.copy(alpha = 0.94f))
                                .cardShadow(RoundedCornerShape(16.dp))
                                .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Place, contentDescription = null, tint = Text.Secondary, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    p.name,
                                    color = Text.Primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (p.address.isNotBlank()) {
                                    Text(
                                        p.address,
                                        color = Text.Muted,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Outlined.Close,
                                tr("关闭"),
                                tint = Text.Muted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { selectedPoi = null }
                            )
                        }
                    }

                    // 离线地图管理：盖在地图之上，自带返回处理（BackHandler 在页面内部）
                    // 这里用允许批量下载的瓦片源，缓存键名与底图完全一致，下完能直接命中。
                    if (offlineOpen) {
                        OfflineMapScreen(
                            onBack = { offlineOpen = false },
                            tileSource = { style -> TencentBulkTileSource(style) }
                        )
                    }
                }
            }
    }

        // 足迹详情弹窗
        if (selected != null) {
            FootprintDetailSheet(fp = selected, onClose = { selectedId = null }, onViewRoute = {
                val rid = selected.routeId
                when {
                    // 独立路线：跳路线详情
                    rid != null -> {
                        state.openRoute(rid)
                        selectedId = null
                    }
                    // 自带轨迹点（自动记录的那一段）：关掉面板，地图上已经画了这条线
                    selected.route.isNotEmpty() -> {
                        selectedId = null
                    }
                    // 既没关联路线也没有内联轨迹：不该出现这个按钮，兜底提示
                    else -> state.toast(tr("这条足迹没有轨迹可查看"))
                }
            })
        }
    }
}

@Composable
private fun MapAttribution() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        Text(
            text = tr("© 腾讯地图"),
            color = Text.Muted,
            fontSize = 10.sp,
            modifier = Modifier
                .padding(6.dp)
                .clip(RoundedCornerShape(8.dp))
                // 版权小字压在地图瓦片上：底必须实心，0.72 时底图线条会穿进文字。
                .background(Bg.Surface)
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

/**
 * 地图本体。卡片版 interactive=false（手势交给页面滚动），全屏版 true（可拖可缩放）。
 * 覆盖物、镜头移动、图层切换都在这里按 scene 变化重建。
 */
@Composable
private fun FootprintMap(
    scene: MapScene,
    interactive: Boolean,
    handle: MapHandle,
    icons: MapIcons,
    onMarkerClick: (Footprint) -> Unit,
    onFocusConsumed: () -> Unit,
    onFailure: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // 建图时是否带着上一次的视角。带的话就不要再自动缩放到足迹总范围，
    // 否则刚恢复的视角会立刻被「适配全部足迹」顶掉，等于没恢复。
    val resumedCamera = remember { handle.savedCenter != null && handle.savedZoom != null }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val v = handle.view ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> v.onResume()
                Lifecycle.Event.ON_PAUSE -> v.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            runCatching { createMapView(ctx, scene.layer, interactive, handle) }
                .onFailure { handle.detach(); onFailure() }
                .getOrElse { android.widget.FrameLayout(ctx) }
                .also { view ->
                    if (view is MapView) {
                        handle.attach(view)
                        // 初始镜头不能在这里直接 setCenter：此刻宽度还是 0，
                        // 会被当成「把这个点放到左上角」，整张图偏到几千公里外
                        // （用户看到的就是外蒙/西伯利亚一带的空白地形）。
                        handle.snapTo(
                            handle.savedCenter ?: CHINA_CENTER,
                            handle.savedZoom ?: DEFAULT_ZOOM
                        )
                    }
                }
        },
        update = { v ->
            if (v is MapView) v.setMultiTouchControls(interactive)
        },
        onRelease = { v ->
            // 先 detach 存视角（这一步要读 MapView 的滚动状态），再真正销毁
            handle.detach()
            if (v is MapView) v.onDetach()
        }
    )

    // 中途创建的 MapView 不会再收到 ON_RESUME，这里补一次
    LaunchedEffect(handle.ready) {
        val v = handle.view ?: return@LaunchedEffect
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) v.onResume()
    }

    // 覆盖物：足迹点 / 连线 / 热力 / 我的位置 / 搜索地点
    LaunchedEffect(
        handle.ready,
        scene.footprints,
        scene.track,
        scene.liveTrack,
        scene.heatOn,
        scene.myLocation,
        scene.myAccuracyM,
        scene.poi,
        scene.provinceCodes,
        scene.segments,
        scene.labels,
        scene.provinceOn,
        scene.coloredRoutes,
        scene.routeOn,
        scene.cityLabels,
        scene.layer
    ) {
        val v = handle.view ?: return@LaunchedEffect
        rebuildOverlays(v, scene, icons, onMarkerClick)
    }

    // 有足迹就自动缩放到能装下全部足迹的范围；没有足迹保持默认中国视角
    LaunchedEffect(handle.ready, scene.footprints) {
        if (handle.view == null) return@LaunchedEffect
        if (scene.footprints.isEmpty()) return@LaunchedEffect
        // 恢复出来的视角优先，别刚记住又顶回"全部足迹"
        if (resumedCamera) return@LaunchedEffect
        handle.fit(scene.footprints.map { it.toGeoPoint() })
    }

    // 聚焦请求（搜索选点 / 定位）
    LaunchedEffect(handle.ready, scene.focus?.token) {
        val f = scene.focus ?: return@LaunchedEffect
        val v = handle.view ?: return@LaunchedEffect
        handle.moveTo(f.point, f.zoom)
        onFocusConsumed()
    }

    // 图层切换：标准图 / 卫星影像 / 深色
    LaunchedEffect(handle.ready, scene.layer) {
        val v = handle.view ?: return@LaunchedEffect
        val dark = scene.layer == "dark"
        // 深色与标准共用同一份矢量瓦片，只有调色不同
        val style = if (dark) "vector" else scene.layer
        val current = runCatching { v.tileProvider.tileSource.name() }.getOrNull()
        if (current != "腾讯地图-$style") v.setTileSource(TencentTileSource(style))
        applyTileAppearance(v, dark)
    }
}

private fun createMapView(ctx: Context, layer: String, interactive: Boolean, handle: MapHandle): MapView {
    // 瓦片缓存放私有 files 目录（不是 cacheDir）：系统空间紧张时会整目录清 cacheDir，
    // 用户按县下载的离线包不能跟着一起消失。仍由 osmdroid 自动管理大小与过期。
    OfflineTiles.prepare(ctx)
    return MapView(ctx).apply {
        // 深色只是套滤镜，底图瓦片与标准图共用同一份缓存
        setTileSource(TencentTileSource(if (layer == "dark") "vector" else layer))
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        setMultiTouchControls(interactive)
        // 手动拖动 = 用户想自己看看，停掉「跟随当前位置」，别把镜头抢回去。
        // 返回 false 让 osmdroid 继续处理手势，不能吞掉事件。
        setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_MOVE) handle.onUserPan?.invoke()
            false
        }
        minZoomLevel = 3.0
        maxZoomLevel = TencentTiles.MAX_ZOOM.toDouble()
        setTilesScaledToDpi(true)
        // 首帧就带上夜间调色和深色加载底，避免先闪一下白色再变暗
        applyTileAppearance(this, layer == "dark")
        // 默认给中国视角，避免 0 足迹时停在 (0,0)；
        // 重建时优先接上一次的视角（卡片滚出 LazyColumn 被回收、进出全屏都会重建）。
        // 真正的落镜头在 handle.snapTo()：这里 View 还没有尺寸，直接 setCenter 会算偏。
    }
}

private suspend fun rebuildOverlays(
    v: MapView,
    scene: MapScene,
    icons: MapIcons,
    onMarkerClick: (Footprint) -> Unit
) {
    // KDE 权重是 O(n²)，后台算，别堵主线程
    val heat = if (scene.heatOn && scene.footprints.isNotEmpty()) {
        withContext(Dispatchers.Default) {
            runCatching {
                val dedup = scene.footprints.map { it.toGeoPoint() }
                    .distinctBy { "${it.latitude},${it.longitude}" }
                val weights = kdeWeights(dedup.map { AlgoGeoPoint(it.latitude, it.longitude) })
                Pair(dedup, weights)
            }.getOrNull()
        }
    } else null

    // 先关掉信息窗再 clear：残留的窗口会一直持有旧 MapView 引用
    InfoWindow.closeAllInfoWindowsOn(v)
    v.overlays.clear()

    // 0) 深色底图的调色由 TilesOverlay 的 ColorFilter 完成（applyTileAppearance），
    //    这里不再叠半透明蒙版：蒙版会把浅色底和白色路一起压灰，alpha 一高就整屏发黑。

    // 1) 省域高亮垫在最底层。参考图里走过的省是一整块半透明青蓝，边界带辉光。
    //    放在最下面是当"地貌晕染"用：路线、标记、标签全压在它上面。
    if (scene.provinceOn && scene.provinceCodes.isNotEmpty()) {
        v.overlays.add(
            ProvinceHighlightOverlay(scene.provinceCodes) { code ->
                ProvinceBoundaryData.byAdcode(code)?.rings
            }
        )
    }

    // 2) 热力垫在省域高亮之上。它是背景氛围，之前加在最后会被画在足迹点和路线之上，
    //    半透明红圆把整张图糊掉一层，标记也看不清。
    heat?.let { (hPoints, hWeights) ->
        v.overlays.add(HeatOverlay(hPoints, hWeights))
    }

    // 3) 足迹之间的连线。
    //    用户要求：不要粗黑实线，改成浅色虚线；并给一个独立开关按需显示。
    //    实时记录轨迹仍走下面的实线，方便一眼分清“走过的”和“正在走的”。
    if (scene.routeOn) {
        if (scene.coloredRoutes && scene.segments.isNotEmpty()) {
            for (seg in scene.segments) {
                if (seg.points.size < 2) continue
                v.overlays.add(
                    NavRouteOverlay(
                        points = seg.points,
                        color = softLineColor(seg.color),
                        coreWidthDp = 2.2f,
                        casingPadDp = 0f,
                        arrowSpacingDp = 96f,
                        drawArrows = false,
                        drawCasing = false,
                        dashed = true
                    )
                )
            }
        } else if (scene.track.size > 1) {
            v.overlays.add(
                NavRouteOverlay(
                    points = scene.track,
                    color = softLineColor(LINE_COLOR),
                    coreWidthDp = 2.2f,
                    casingPadDp = 0f,
                    arrowSpacingDp = 88f,
                    drawArrows = false,
                    drawCasing = false,
                    dashed = true
                )
            )
        }
    }

    // 4) 自动记录中的实时轨迹：压在顺序连线之上，更粗、带起终点
    // （起点空心环 = 从哪出发，终点实心点 = 现在在哪）
    if (scene.liveTrack.size > 1) {
        v.overlays.add(
            NavRouteOverlay(
                points = scene.liveTrack,
                color = LIVE_LINE_COLOR,
                coreWidthDp = 7.5f,
                casingPadDp = 4f,
                arrowSpacingDp = 56f,
                showEndpoints = true
            )
        )
    }

    // 5) 足迹点。特别地点换成 Canvas 画的金星（参考图里那颗星）
    scene.footprints.forEach { fp ->
        val marker = Marker(v).apply {
            position = fp.toGeoPoint()
            if (fp.special) {
                icon = icons.star
                setAnchor(0.5f, 0.5f) // 星标以城市为中心
            } else {
                icon = icons.orange
                setAnchor(0.5f, 1.0f) // PNG图标底部对准坐标点
            }
            title = fp.name
            setOnMarkerClickListener { _, _ ->
                onMarkerClick(fp)
                true
            }
        }
        v.overlays.add(marker)
    }

    // 5) 当前位置：先精度圈，再蓝点。
    //    只画一个点会让人误以为「精确到米」，实际室内可能差几十米；圈能把可信范围说清楚。
    scene.myLocation?.let { pt ->
        val acc = scene.myAccuracyM
        if (acc > 4f && acc < 3_000f) {
            runCatching {
                v.overlays.add(Polygon(v).apply {
                    points = accuracyCirclePoints(pt, acc.toDouble())
                    outlinePaint.color = 0x552A7CF6
                    outlinePaint.strokeWidth = 1.5f * v.resources.displayMetrics.density
                    fillPaint.color = 0x222A7CF6
                })
            }
        }
        v.overlays.add(Marker(v).apply {
            position = pt
            icon = icons.myDot
            title = tr("我的位置")
            setAnchor(0.5f, 0.5f)
            // 蓝点不弹信息窗：点它只是想看位置，弹窗反而挡住地图
            setOnMarkerClickListener { _, _ -> true }
        })
    }

    scene.poi?.let { p ->
        v.overlays.add(Marker(v).apply {
            position = GeoPoint(p.lat, p.lon)
            icon = icons.purple
            title = p.name
            snippet = p.address
            setAnchor(0.5f, 1.0f)
            setOnMarkerClickListener { m, _ ->
                m.showInfoWindow()
                true
            }
        })
    }

    // 6) 城市名标签压在最上层。参考图里每个地名都带描边，且颜色跟着所属路线走
    if (scene.cityLabels && scene.labels.isNotEmpty()) {
        v.overlays.add(CityLabelOverlay(scene.labels))
    }

    // 比例尺：放左下角，并整体上抬到「© 腾讯地图」署名之上。
    // 右下角在全屏模式下被 5 个圆按钮占着，左下角除署名的 26dp 外整块是空的。
    // osmdroid 的 alignBottom 语义是「锚在底部」，此时 y 偏移是自底向上的距离。
    val dm = v.resources.displayMetrics
    v.overlays.add(ScaleBarOverlay(v).apply {
        setAlignBottom(true)
        setScaleBarOffset((10 * dm.density).toInt(), (34 * dm.density).toInt())
        // 默认无底色，黑色文字压在卫星影像上看不清，给一层半透明白
        setBackgroundPaint(Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            alpha = 150
        })
    })

    v.invalidate()
}

/**
 * 地图创建失败占位。必须可点重试：mapFailed 一旦置 true 就不复位的话，
 * 一次失败会导致整个会话地图永久不可用。
 */
@Composable
private fun MapFailedPlaceholder(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Bg.SurfaceVariant)
            .clickable(onClick = onRetry),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.MyLocation,
                null,
                tint = Brand.Terracotta,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(tr("地图加载失败，检查网络后重试"), color = Text.Secondary, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Brush.linearGradient(MainGradient))
                    .padding(horizontal = 18.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tr("重试"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) Brush.linearGradient(MainGradient) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Text.Secondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun RoundIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(42.dp)
            .scale(if (pressed.value) 0.9f else 1f)
            .clip(CircleShape)
            .background(if (active) Brand.Terracotta else Bg.Surface)
            .cardShadow(CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = if (active) Color.White else Text.Primary, modifier = Modifier.size(22.dp))
    }
}

/** 地图样式面板里的一行：名称 + 开关。整行可点，不用精准点小开关 */
@Composable
private fun StyleToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Text.Primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(78.dp)
        )
        Spacer(Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Brand.Terracotta,
                checkedBorderColor = Brand.Terracotta,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Bg.SurfaceVariant,
                uncheckedBorderColor = Bg.Outline
            )
        )
    }
}

@Composable
private fun StatItem(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedNumber(
            value = value,
            color = Brand.Terracotta,
            fontSize = 22,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(text = label, color = Text.Secondary, fontSize = 12.sp)
    }
}

@Composable
private fun FootprintItem(fp: Footprint, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .scale(if (pressed.value) 0.98f else 1f)
            .clip(MaterialTheme.shapes.large)
            .background(Bg.Surface)
            .cardShadow()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(LightOrange),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.DirectionsWalk, contentDescription = null, tint = Text.Primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fp.name,
                color = Text.Primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${tr(fp.city)} · ${fp.date}",
                color = Text.Secondary,
                fontSize = 13.sp
            )
            if (fp.note.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = fp.note,
                    color = Text.Muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (fp.special) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.Star, contentDescription = null, tint = Brand.Gold, modifier = Modifier.size(16.dp))
        }
    }
}


private fun Footprint.toGeoPoint(): GeoPoint {
    // 有精确经纬度就直接用；旧数据只有示意地图坐标，才反算（会有几十米误差）
    if (lat != null && lon != null) return GeoPoint(lat, lon)
    val (lon, lat) = ChinaMapData.mapToLonLat(x, y)
    return GeoPoint(lat, lon)
}

/**
 * 把足迹分组成若干条分色路线（参考图里那种多条彩色线）。
 *
 * 分组规则，先到先得：
 *  1. **有行程的按行程分** —— `Footprint.routeId` 指向一条 Route（一次旅行），
 *     同一条行程的点天然是一条线。
 *  2. **没行程的按地理聚类** —— 700km 半径内算同一片区（川滇线、长三角那种）。
 *  3. 只有 1 个点的组不画线（一个点连不成线），点本身由 Marker 呈现。
 *
 * 组内再用 TSP 排成顺路顺序，语义和原来那条青色连线保持一致。
 */
private fun buildRouteSegments(
    footprints: List<Footprint>,
    routes: List<Route>
): List<RouteSegment> {
    if (footprints.size < 2) return emptyList()

    val byRoute = LinkedHashMap<Int, MutableList<Footprint>>()
    val loose = ArrayList<Footprint>()
    for (fp in footprints) {
        val rid = fp.routeId
        if (rid == null) {
            loose.add(fp)
        } else {
            byRoute.getOrPut(rid) { ArrayList() }.add(fp)
        }
    }

    // 每组 = (成员足迹, 路线名)
    val groups = ArrayList<Pair<List<Footprint>, String>>()
    for ((rid, list) in byRoute) {
        if (list.size >= 2) {
            val title = routes.firstOrNull { it.id == rid }?.title ?: "${tr("行程", "Trip")} $rid"
            groups.add(list to title)
        } else {
            // 只有一个人走过的行程不构成一条线。这些点退回地理聚类，
            // 不然会被整个丢掉，在地图上变成一个孤零零的钉子。
            loose.addAll(list)
        }
    }
    if (loose.size >= 2) {
        // label 借用来携带足迹 id，聚类完能原样映射回去
        val pts = loose.map { fp ->
            val g = fp.toGeoPoint()
            AlgoGeoPoint(g.latitude, g.longitude, fp.id.toString())
        }
        for (cluster in singleLinkGroups(pts, epsKm = 700.0)) {
            if (cluster.size < 2) continue
            val members = cluster.mapNotNull { g ->
                g.label.toIntOrNull()?.let { id -> loose.firstOrNull { it.id == id } }
            }
            if (members.size < 2) continue
            groups.add(members to members.first().city)
        }
    }

    return groups.mapIndexedNotNull { index, (members, title) ->
        val pts = members.map { fp ->
            val g = fp.toGeoPoint()
            AlgoGeoPoint(g.latitude, g.longitude, fp.id.toString())
        }
        val ordered = runCatching { tspOrder(pts) }.getOrElse { pts }
        if (ordered.size < 2) return@mapIndexedNotNull null
        RouteSegment(
            points = ordered.map { GeoPoint(it.lat, it.lon) },
            color = ROUTE_PALETTE[index % ROUTE_PALETTE.size],
            title = title,
            footprintIds = ordered.mapNotNull { it.label.toIntOrNull() }.toSet()
        )
    }
}

/** 热力圆代表的地理半径基准（米）。半径按地理尺度算，缩放时才会自然变大变小 */
private const val HEAT_RADIUS_M = 2000.0

/**
 * KDE 热力 Overlay：每个足迹点画半透明圆，半径/透明度随权重增大。
 *
 * 半径不是固定屏幕像素，而是把「固定地理半径」按当前缩放换算成像素：
 * 之前固定像素时，缩到全国尺度几十个大圆糊成一团红，放大后圆又几乎看不见，
 * 完全失去「密度」语义。上下限保证极端缩放下不会变成看不见的点或糊满屏幕。
 */
private class HeatOverlay(
    private val points: List<GeoPoint>,
    private val weights: List<Double>
) : Overlay() {
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        if (shadow || canvas == null || mapView == null) return
        val width = mapView.width
        if (width <= 0 || points.isEmpty()) return
        // 1 像素 ≈ 多少米：用当前视口的经度跨度 / 屏幕宽度换算（经度 1° 约 111320m·cos(lat)）
        val box = mapView.boundingBox ?: return
        val lonSpanMeters = (box.lonEast - box.lonWest) * 111320.0 *
            cos(mapView.mapCenter.latitude / 180.0 * PI)
        if (lonSpanMeters <= 0) return
        val metersPerPx = lonSpanMeters / width

        val projection = mapView.projection
        val screenPt = android.graphics.Point()
        points.forEachIndexed { i, gp ->
            val w = weights.getOrElse(i) { 0.0 }.coerceIn(0.0, 1.0)
            projection.toPixels(gp, screenPt)
            paint.color = 0xFFE07A5F.toInt() // Brand.Terracotta
            paint.alpha = (30 + 90 * w).toInt().coerceIn(30, 120)
            val radiusPx = (HEAT_RADIUS_M * (0.5 + 0.5 * w) / metersPerPx).coerceIn(6.0, 120.0)
            canvas.drawCircle(
                screenPt.x.toFloat(),
                screenPt.y.toFloat(),
                radiusPx.toFloat(),
                paint
            )
        }
    }
}

/** 底图色调蒙版：整块视口压一层半透明色，只压暗/染色瓦片，不压省域高亮和标记。 */
@Composable
private fun FootprintDetailSheet(fp: Footprint, onClose: () -> Unit, onViewRoute: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Func.Scrim)
            .clickable { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Bg.Surface)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Bg.Outline)
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = fp.name,
                    color = Text.Primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (fp.special) {
                    Text(tr("⭐ 特别地点"), color = Brand.Gold, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("${tr(fp.city)} · ${fp.date}", color = Text.Secondary, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Text(cleanNote(fp.note), color = Text.Primary, fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(20.dp))
            // 只有真有路线可看才显示这个按钮：关联了独立路线，或自带轨迹点
            if (fp.routeId != null || fp.route.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50.dp))
                        .background(Brush.linearGradient(MainGradient))
                        .clickable { onViewRoute() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tr("查看路线 →"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
