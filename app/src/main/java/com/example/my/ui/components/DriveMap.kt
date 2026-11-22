package com.example.my.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.my.data.GeoFix
import com.example.my.data.map.GeoUtil
import com.example.my.data.map.LatLon
import com.example.my.data.map.OfflineTiles
import com.example.my.data.map.TencentTiles
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 驾驶模式底图：**真实在线瓦片**（腾讯，免 Key，GCJ-02），不是画出来的示意图。
 *
 * 与足迹地图共用同一套瓦片缓存：tile source 的名字必须与 MapScreen 里的
 * `"腾讯地图-$style"` 一字不差，osmdroid 是按 source 名分目录存缓存的，
 * 名字一变就等于把用户下过的离线包全部丢掉、再走一遍流量。
 */
private class DriveTileSource(private val style: String) : OnlineTileSourceBase(
    "腾讯地图-$style", TencentTiles.MIN_ZOOM, TencentTiles.MAX_ZOOM, 256, "", arrayOf(""), "© 腾讯地图"
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return TencentTiles.url(style, z, x, y)
    }
}

/**
 * 夜间滤镜：高德没有深色矢量瓦片，用「反相 → 压暗 → 去饱和 → 偏蓝」
 * 把浅色路网翻成深色底，已缓存的瓦片也能立刻变色，不产生新流量。
 */
private val DRIVE_NIGHT_FILTER: ColorMatrixColorFilter by lazy {
    val m = ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
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
    m.postConcat(ColorMatrix().apply { setSaturation(0.55f) })
    ColorMatrixColorFilter(m)
}

private const val DARK_BG = 0xFF14181F.toInt()

/**
 * 当前位置图层：精度圈 + 蓝点 +（有 bearing 时）方向锥。
 * 直接画在 canvas 上，不用 Marker 图标，横屏大屏下也不会出现位图拉伸。
 */
private class PuckOverlay(private val fixProvider: () -> GeoFix?) : Overlay() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E7BF0.toInt() }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x332E8BFF }
    private val cone = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55339CFF }

    override fun draw(c: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val fix = fixProvider() ?: return
        val pt = mapView.projection.toPixels(GeoPoint(fix.lat, fix.lon), null)
        val accPx = runCatching {
            mapView.projection.metersToEquatorPixels(fix.accuracyM.coerceIn(5f, 200f))
        }.getOrDefault(0f)
        if (accPx > 8f) c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), accPx, halo)

        if (fix.bearingDeg != 0f) {
            val rad = Math.toRadians(fix.bearingDeg.toDouble())
            val len = 34f
            val wing = 13f
            val px = pt.x.toFloat()
            val py = pt.y.toFloat()
            val path = Path()
            val tipX = px + (Math.sin(rad) * len).toFloat()
            val tipY = py - (Math.cos(rad) * len).toFloat()
            val leftX = px + (Math.sin(rad - 0.42) * wing).toFloat()
            val leftY = py - (Math.cos(rad - 0.42) * wing).toFloat()
            val rightX = px + (Math.sin(rad + 0.42) * wing).toFloat()
            val rightY = py - (Math.cos(rad + 0.42) * wing).toFloat()
            path.moveTo(px, py)
            path.lineTo(tipX, tipY)
            path.lineTo(rightX + (tipX - px) * 0.35f, rightY + (tipY - py) * 0.35f)
            path.lineTo(px, py)
            path.lineTo(leftX + (tipX - px) * 0.35f, leftY + (tipY - py) * 0.35f)
            path.close()
            c.drawPath(path, cone)
        }

        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 9f, fill)
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 9f, ring)
    }
}

/**
 * 比例尺真值：把「HUD 上那条 54(nx) 长的白条」换算成它在地面上实际代表的米数。
 *
 * 以前这里写死 200 米，用户一缩放就不对了。做法是拿当前视野的南北跨距除以地图高度，
 * 得到米/像素，再乘白条的像素长度；最后归到 1/2/5×10ⁿ 这种习惯刻度上
 * （否则会显示「173 米」这种没法读的数）。
 */
private fun scaleMetersFor(v: MapView, barPx: Float): Int {
    val fallback = 200
    val h = v.height
    if (h <= 0 || barPx <= 0f) return fallback
    return runCatching {
        val bb: BoundingBox = v.boundingBox ?: return fallback
        val lon = v.mapCenter.longitude
        val spanM = GeoUtil.metersBetween(bb.latNorth, lon, bb.latSouth, lon).toDouble()
        if (spanM <= 0.0) return fallback
        val raw = spanM / h * barPx
        val exp = floor(log10(raw))
        val pow = 10.0.pow(exp)
        val n = raw / pow
        val nice = when {
            n < 1.5 -> 1.0
            n < 3.5 -> 2.0
            n < 7.5 -> 5.0
            else -> 10.0
        }
        (nice * pow).roundToInt().coerceAtLeast(1)
    }.getOrDefault(fallback)
}

/**
 * 驾驶模式真实地图。
 *
 * @param layer 0 标准 / 1 夜色 / 2 卫星
 * @param routePoints 当前驾车路线的完整折线（GCJ-02），空表示还没算路
 * @param follow 是否让镜头跟着车走（用户拖动地图后由页面置 false）
 * @param northUp 车头向上还是正北向上
 * @param recenterToken 递增即触发一次「回到车位」
 * @param overviewToken 递增非 0 时把整条路线装进视野
 * @param zoomTick 每次点击「放大 / 缩小」递增，用来让 HUD 的缩放按钮真的改地图
 * @param zoomDir +1 放大 / -1 缩小，与 [zoomTick] 配对使用
 * @param onScaleMeters 缩放变化时回报「比例尺白条代表多少米」，给 HUD 的比例尺用
 */
@Composable
fun DriveMapView(
    modifier: Modifier,
    layer: Int,
    routePoints: List<LatLon>,
    fix: GeoFix?,
    follow: Boolean,
    northUp: Boolean,
    recenterToken: Int,
    overviewToken: Int,
    zoomTick: Int = 0,
    zoomDir: Int = 1,
    onUserPan: () -> Unit,
    onScaleMeters: (Int) -> Unit = {}
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var map by remember { mutableStateOf<MapView?>(null) }
    val fixRef = remember { arrayOfNulls<GeoFix>(1) }
    val panRef = remember { arrayOfNulls<() -> Unit>(1) }
    // 白条长度按 1080×630 基准换算成屏幕像素：nx(54) 在别的分辨率下不等于 54dp
    val density = LocalDensity.current.density
    val screenWDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val barPx = 54f * density * (if (screenWDp > 0f) screenWDp / 1080f else 1f)
    val scaleRef = remember { arrayOfNulls<(Int) -> Unit>(1) }
    fixRef[0] = fix
    panRef[0] = onUserPan
    scaleRef[0] = onScaleMeters

    AndroidView(
        modifier = modifier,
        factory = { c ->
            OfflineTiles.prepare(c)
            val v = MapView(c).apply {
                setTileSource(DriveTileSource("vector"))
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                setMultiTouchControls(true)
                minZoomLevel = 3.0
                maxZoomLevel = TencentTiles.MAX_ZOOM.toDouble()
                setTilesScaledToDpi(true)
                setBackgroundColor(DARK_BG)
                // 默认落在车头附近；没有定位时用一个全国视角，别停在几内亚湾
                controller.setZoom(15.0)
                controller.setCenter(GeoPoint(39.9087, 116.3975))
                setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_MOVE) panRef[0]?.invoke()
                    false
                }
                // 蓝点必须画在最上层，否则会被路线盖住
                overlays.add(PuckOverlay { fixRef[0] })
            }
            // 缩放 / 平移都要重算比例尺，否则用户捏合放大后 HUD 上的米数还是旧的
            v.addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent): Boolean {
                    scaleRef[0]?.invoke(scaleMetersFor(v, barPx))
                    return false
                }

                override fun onZoom(event: ZoomEvent): Boolean {
                    scaleRef[0]?.invoke(scaleMetersFor(v, barPx))
                    return false
                }
            })
            map = v
            v
        },
        update = { v ->
            map = v
            val dark = layer == 1
            val style = if (layer == 2) "satellite" else "vector"
            val current = runCatching { v.tileProvider.tileSource.name() }.getOrNull()
            if (current != "腾讯地图-$style") v.setTileSource(DriveTileSource(style))
            runCatching {
                v.overlayManager?.tilesOverlay?.let { t ->
                    t.setColorFilter(if (dark) DRIVE_NIGHT_FILTER else null)
                    t.setLoadingBackgroundColor(if (dark) DARK_BG else android.graphics.Color.WHITE)
                    t.setLoadingLineColor(if (dark) 0xFF232A36.toInt() else android.graphics.Color.LTGRAY)
                }
            }
            v.setBackgroundColor(if (dark) DARK_BG else android.graphics.Color.TRANSPARENT)
            v.invalidate()
        },
        onRelease = { v ->
            map = null
            runCatching { v.onDetach() }
        }
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val v = map ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> v.onResume()
                Lifecycle.Event.ON_PAUSE -> v.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 缩放按钮：HUD 上的 + / − 必须真的改地图，否则用户点了没反应只能捏合
    LaunchedEffect(map, zoomTick) {
        val v = map ?: return@LaunchedEffect
        if (zoomTick <= 0) return@LaunchedEffect
        v.controller.setZoom((v.zoomLevelDouble + 0.8 * zoomDir).coerceIn(3.0, TencentTiles.MAX_ZOOM.toDouble()))
        v.post { runCatching { scaleRef[0]?.invoke(scaleMetersFor(v, barPx)) } }
    }

    // 首帧先算一次比例尺：View 的高度要等测量结束才是真值，所以丢到 post 里做
    LaunchedEffect(map) {
        val v = map ?: return@LaunchedEffect
        v.post { runCatching { scaleRef[0]?.invoke(scaleMetersFor(v, barPx)) } }
    }

    // 路线：外描边 + 主色，拐点用真实折线，不再手画贝塞尔
    LaunchedEffect(map, routePoints) {
        val v = map ?: return@LaunchedEffect
        // 保留蓝点图层，其余覆盖物整体重建
        val puck = v.overlays.filterIsInstance<PuckOverlay>()
        v.overlays.clear()
        v.overlays.addAll(puck)
        if (routePoints.size >= 2) {
            val pts = routePoints.map { GeoPoint(it.lat, it.lon) }
            val casing = Polyline(v).apply {
                setPoints(pts)
                outlinePaint.color = 0xFF0E3C93.toInt()
                outlinePaint.strokeWidth = 16f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
            }
            val main = Polyline(v).apply {
                setPoints(pts)
                outlinePaint.color = 0xFF2A7CF6.toInt()
                outlinePaint.strokeWidth = 11f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
            }
            v.overlays.add(casing)
            v.overlays.add(main)
            // 起点终点
            v.overlays.add(EndpointOverlay(pts.first(), 0xFF2AD6C2.toInt(), false))
            v.overlays.add(EndpointOverlay(pts.last(), 0xFFE14A3C.toInt(), true))
        }
        // 蓝点最后加，保证压在路线之上，不会被自己的路线盖住
        v.overlays.addAll(puck)
        v.invalidate()
    }

    // 镜头：跟随 / 回正 / 全览
    LaunchedEffect(map, fix?.lat, fix?.lon, follow, northUp) {
        val v = map ?: return@LaunchedEffect
        val f = fix ?: return@LaunchedEffect
        if (!follow) return@LaunchedEffect
        v.controller.setCenter(GeoPoint(f.lat, f.lon))
        if (v.zoomLevelDouble < 16.0) v.controller.setZoom(17.0)
        // 正北向上 = 不旋转；车头向上 = 把地图反向转掉 bearing（真实旋转，不只是文案）
        v.mapOrientation = if (northUp) 0f else -f.bearingDeg
    }

    LaunchedEffect(recenterToken) {
        if (recenterToken == 0) return@LaunchedEffect
        val v = map ?: return@LaunchedEffect
        val f = fix ?: return@LaunchedEffect
        v.controller.animateTo(GeoPoint(f.lat, f.lon))
        v.controller.setZoom(17.0)
    }

    LaunchedEffect(overviewToken) {
        if (overviewToken == 0) return@LaunchedEffect
        val v = map ?: return@LaunchedEffect
        val pts = routePoints.map { GeoPoint(it.lat, it.lon) }
        if (pts.size >= 2) {
            runCatching {
                v.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts), true, 80)
            }
        }
    }
}

/** 起终点圆点：终点用旗标形状，一眼区分「我要去哪」和「我从哪来」。 */
private class EndpointOverlay(
    private val point: GeoPoint,
    private val color: Int,
    private val destination: Boolean
) : Overlay() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    override fun draw(c: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val pt = mapView.projection.toPixels(point, null)
        if (destination) {
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 10f, paint)
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 10f, ring)
        } else {
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 7f, paint)
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 7f, ring)
        }
    }
}

/** 米 → 屏幕像素的距离换算在部分层级的低纬度会偏，这里给比例尺留个公开口径。 */
fun metersPerPixel(zoom: Double, latitude: Double): Double {
    val equator = 156_543.03392 * cos(Math.toRadians(latitude)) / Math.pow(2.0, zoom)
    return equator / 256.0
}
