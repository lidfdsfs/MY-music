package com.example.my.ui.screens

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.my.data.AmapApi
import com.example.my.data.GeoFix
import com.example.my.data.Locator
import com.example.my.data.PoiResult
import com.example.my.data.ai.VoiceIo
import com.example.my.data.map.AmapDrive
import com.example.my.data.map.AmapTraffic
import com.example.my.data.map.DrivePref
import com.example.my.data.map.DriveRecent
import com.example.my.data.map.DriveRoute
import com.example.my.data.map.GeoUtil
import com.example.my.data.map.LatLon
import com.example.my.data.music.EqPreset
import com.example.my.data.music.MusicPlayer
import com.example.my.state.AppState
import com.example.my.ui.components.DriveMapView
import com.example.my.ui.i18n.Lang
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.DrivingPalette
import com.example.my.ui.theme.Ink
import com.example.my.ui.theme.TypeScale
import com.example.my.util.ScreenRotation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 驾驶模式功能入口**唯一真源**。
 *
 * 这一版按高德的界面语言重排：巡航态只有「搜索 / 音乐 / 图层 / 路况 / 回正 / 更多 / 行程」，
 * 导航态换成「路况 / 图层 / 放大 / 缩小 / 正北 / 全览 / 语音 / 更多 / 退出」。
 * 两个状态各自一张槽位表，合起来 `distinct()` 必须等于枚举总数——
 * 任何功能被漏掉或重复放到两处，都会在启动时直接抛异常。
 */
enum class DriveEntry(val zh: String, val en: String) {
    SEARCH("搜索", "Search"),
    MUSIC("音乐", "Music"),
    LAYERS("图层", "Layers"),
    TRAFFIC("路况", "Traffic"),
    RECENTER("回正", "Recenter"),
    MORE("更多", "More"),
    VEHICLE("行程", "Trip"),
    ZOOM_IN("放大", "Zoom in"),
    ZOOM_OUT("缩小", "Zoom out"),
    NORTH("正北", "North up"),
    OVERVIEW("全览", "Overview"),
    VOICE("语音", "Voice"),
    EXIT("退出", "Exit"),
    SOUND("音效", "Equalizer")
}

/** 巡航态：搜索框 + 右侧四个圆钮 + 播放器。 */
private val DRIVE_CRUISE_SLOTS = listOf(
    DriveEntry.SEARCH, DriveEntry.MUSIC, DriveEntry.LAYERS,
    DriveEntry.TRAFFIC, DriveEntry.RECENTER, DriveEntry.MORE, DriveEntry.VEHICLE
)

/** 导航态：顶部三钮 + 右侧缩放 / 正北 + 左侧语音 + 底部全览 / 退出。 */
private val DRIVE_NAV_SLOTS = listOf(
    DriveEntry.TRAFFIC, DriveEntry.LAYERS, DriveEntry.MORE, DriveEntry.ZOOM_IN,
    DriveEntry.ZOOM_OUT, DriveEntry.NORTH, DriveEntry.OVERVIEW, DriveEntry.VOICE,
    DriveEntry.EXIT
)

/** 浮层里的子入口（音效预设只从「更多」进）。 */
private val DRIVE_EXTRA_SLOTS = listOf(DriveEntry.SOUND)

/** 全界面槽位表：每个功能恰好一个入口。 */
private val DRIVE_SLOTS = (
    DRIVE_CRUISE_SLOTS + DRIVE_NAV_SLOTS + DRIVE_EXTRA_SLOTS
    ).distinct().also {
        require(it.size == DriveEntry.entries.size) {
            "驾驶模式有功能没有入口：缺 " +
                DriveEntry.entries.filter { e -> e !in it }.joinToString()
        }
    }

/** 设计画布：1080 × 630，所有坐标按这一组常量归一化。 */
private const val DESIGN_W = 1080f
private const val DESIGN_H = 630f

/** 模拟导航的推进速度（米/秒）：60 km/h。只在用户显式打开「模拟导航」时使用。 */
private const val SIM_SPEED_MPS = 16.7f

/**
 * 驾驶模式：全屏沉浸车机页，按高德的操作链路重做。
 *
 * 巡航（只有地图 + 搜索）→ 搜索 → **路线预览**（三条方案任选 / 改偏好）→ 导航
 * （路口放大、高速出口两个子态）→ 到达。所有读数只来自定位与高德接口，
 * 没开始导航就不存在车速、剩余时间这些数字。
 */
@Composable
fun DrivingScreen(state: AppState, modifier: Modifier = Modifier) {
    DrivingImmersiveEffect()

    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val scope = rememberCoroutineScope()

    // ================= 界面状态 =================
    var phase by remember { mutableStateOf(NavPhase.CRUISE) }
    val navigating = phase.isNavigating
    var layerIndex by remember { mutableIntStateOf(0) }
    var trafficOn by remember { mutableStateOf(false) }
    var northUp by remember { mutableStateOf(false) }
    var mediaOpen by remember { mutableStateOf(true) }
    var queueOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<DriveEntry?>(null) }
    var hint by remember { mutableStateOf("") }
    /** 语音播报开关：关掉就是真的不播，不是改个文案 */
    var voiceOn by remember { mutableStateOf(true) }
    /** 模拟导航：沿真实路线按 60 km/h 推进，只在用户显式打开时生效 */
    var simulating by remember { mutableStateOf(false) }
    var recentlySpoken by remember { mutableStateOf("") }
    var lastBackAt by remember { mutableLongStateOf(0L) }
    var recenterTick by remember { mutableIntStateOf(0) }
    var overviewToken by remember { mutableIntStateOf(0) }
    var zoomSeq by remember { mutableIntStateOf(0) }
    var zoomDir by remember { mutableIntStateOf(1) }

    // ================= 真实数据 =================
    var fix by remember { mutableStateOf<GeoFix?>(null) }
    var myAddr by remember { mutableStateOf("") }
    var addrTried by remember { mutableStateOf(false) }
    var dest by remember { mutableStateOf<PoiResult?>(null) }
    var routes by remember { mutableStateOf<List<DriveRoute>>(emptyList()) }
    var routeIndex by remember { mutableIntStateOf(0) }
    var pref by remember { mutableStateOf(DrivePref.SPEED) }
    var planning by remember { mutableStateOf(false) }
    var travelled by remember { mutableIntStateOf(0) }
    var follow by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<PoiResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchNote by remember { mutableStateOf("") }
    var lastRerouteAt by remember { mutableLongStateOf(0L) }
    var rerouting by remember { mutableStateOf(false) }
    var trafficText by remember { mutableStateOf("") }
    var tripMeters by remember { mutableIntStateOf(0) }
    var tripFrom by remember { mutableStateOf<GeoFix?>(null) }
    var fallbackSpeedKmh by remember { mutableFloatStateOf(0f) }
    var scaleMeters by remember { mutableIntStateOf(200) }
    /** 最近去过的目的地：用户自己算过路的，不是推荐位 */
    var recent by remember { mutableStateOf(DriveRecent.load(context)) }
    var volume by remember {
        mutableFloatStateOf(
            audio?.let {
                val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                it.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
            } ?: 0.72f
        )
    }

    val locProblem = remember { Locator.problem(context) }
    val clock by produceState(initialValue = "--:--") {
        while (true) {
            value = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
            delay(20_000L)
        }
    }

    LaunchedEffect(hint) {
        if (hint.isNotEmpty()) {
            delay(1_900L)
            hint = ""
        }
    }

    // 定位：驾驶页一直订阅，1 秒 / 2 米，比足迹记录更密（车速要跟得上）
    DisposableEffect(context) {
        val stop = Locator.observe(context, intervalMs = 1_000L, minMeters = 2f) { f -> fix = f }
        onDispose { stop() }
    }

    // 本次行程里程 + 车速兜底：必须同时过精度与噪声两道闸，否则定位漂移会自己涨里程
    LaunchedEffect(fix?.time) {
        val f = fix ?: return@LaunchedEffect
        val prev = tripFrom
        if (prev != null) {
            val d = GeoUtil.metersBetween(prev.wgsLat, prev.wgsLon, f.wgsLat, f.wgsLon)
            val dt = (f.time - prev.time) / 1000.0
            val acc = f.accuracyM.coerceAtLeast(0f)
            // 噪声下限：精度 50 米时，10 米的位移只是漂移，不能算行驶
            val noise = maxOf(5.0, acc * 0.75)
            val speed = if (dt > 0.2) d / dt * 3.6 else 0.0
            if (d >= noise && dt > 0.5 && acc <= 35f && speed <= 220.0) {
                tripMeters += d.roundToInt()
                fallbackSpeedKmh = speed.toFloat()
            } else if (dt > 4.0 || acc > 35f) {
                fallbackSpeedKmh = 0f
            }
        }
        tripFrom = f
    }

    // 路况：开关打开才查；按约 500 米网格变化重查，避免每秒打一次接口
    LaunchedEffect(trafficOn, (fix?.wgsLat ?: 0.0).times(200).toInt(), (fix?.wgsLon ?: 0.0).times(200).toInt()) {
        if (!trafficOn) {
            trafficText = ""
            return@LaunchedEffect
        }
        val f = fix ?: return@LaunchedEffect
        trafficText = AmapTraffic.status(f.wgsLat, f.wgsLon) ?: tr("暂无路况数据", "No traffic data")
    }

    // 逆地理只查一次：失败也置 tried，否则定位每秒更新=每秒打一次接口
    LaunchedEffect(fix?.wgsLat) {
        val f = fix ?: return@LaunchedEffect
        if (addrTried) return@LaunchedEffect
        addrTried = true
        val addr = AmapApi.reverseGeocode(f.wgsLat, f.wgsLon)
        if (!addr.isNullOrBlank()) myAddr = addr
    }

    /** 沿路线已走的米数 → 更新剩余与子态。导航推进的唯一入口。 */
    fun applyProgress(done: Int) {
        val r = routes.getOrNull(routeIndex) ?: return
        travelled = done
        val remain = (r.distanceM - done).coerceAtLeast(0)
        if (remain <= 25) {
            if (phase != NavPhase.ARRIVE) {
                phase = NavPhase.ARRIVE
                simulating = false
                hint = tr("已到达目的地附近", "Arrived at destination")
            }
            return
        }
        val nextDist = r.stepEndMeters(done) - done
        phase = when {
            nextDist <= 120 -> NavPhase.JUNCTION
            r.hasHighway && remain > 5_000 -> NavPhase.HIGHWAY
            else -> NavPhase.NAV
        }
    }

    /** 偏航 / 手动重算：以当前位置重新规划，落点仍是原目的地。 */
    fun reroute() {
        val d = dest ?: return
        val f = fix ?: return
        if (rerouting || planning) return
        rerouting = true
        scope.launch {
            val list = AmapDrive.planAll(f.wgsLat, f.wgsLon, d.lat, d.lon, pref.strategy)
            if (list.isNotEmpty()) {
                routes = list
                routeIndex = 0
                travelled = 0
                hint = tr("路线已重新规划", "Route recalculated")
            } else {
                hint = AmapDrive.lastError ?: tr("重新规划失败", "Reroute failed")
            }
            rerouting = false
        }
    }

    /** 真正的导航推进：把定位投影到路线上，车不动数字就不动。 */
    LaunchedEffect(phase.isNavigating, fix?.time, routes, routeIndex, simulating) {
        if (!phase.isNavigating || simulating) return@LaunchedEffect
        val r = routes.getOrNull(routeIndex) ?: return@LaunchedEffect
        val f = fix ?: return@LaunchedEffect
        applyProgress(r.travelledMeters(f.wgsLat, f.wgsLon))
        // 偏航：离路线 90 米以上且在动（步行速度以下不判，避免隧道里乱跳），25 秒冷却
        val now = System.currentTimeMillis()
        if (r.nearestMeters(f.wgsLat, f.wgsLon) > 90 && f.speedMps > 2.5f && now - lastRerouteAt > 25_000L) {
            lastRerouteAt = now
            reroute()
        }
    }

    /** 模拟导航：只沿真实折线推进，绝不冒充 GPS。 */
    LaunchedEffect(simulating, phase.isNavigating, routeIndex) {
        if (!simulating || !phase.isNavigating) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            val r = routes.getOrNull(routeIndex) ?: break
            if (travelled >= r.distanceM) break
            applyProgress((travelled + SIM_SPEED_MPS.toInt()).coerceAtMost(r.distanceM))
            if (phase == NavPhase.ARRIVE) break
        }
    }

    /** 进入路口态时播一次当前引导（语音关掉就真的不播）。 */
    LaunchedEffect(phase, routes, routeIndex, travelled, voiceOn) {
        if (!voiceOn || phase != NavPhase.JUNCTION) return@LaunchedEffect
        val text = routes.getOrNull(routeIndex)?.stepAt(travelled)?.instruction ?: return@LaunchedEffect
        if (text == recentlySpoken) return@LaunchedEffect
        recentlySpoken = text
        VoiceIo.speak(context, text, if (Lang.isEn) "en" else "zh")
    }

    /** 按当前目的地 + 偏好请求高德，拿回最多三条方案。 */
    fun planRoutes(target: PoiResult) {
        dest = target
        phase = NavPhase.PREVIEW
        val f = fix
        if (f == null) {
            hint = tr("正在定位，请稍候", "Locating, please wait")
            return
        }
        planning = true
        scope.launch {
            val list = AmapDrive.planAll(f.wgsLat, f.wgsLon, target.lat, target.lon, pref.strategy)
            planning = false
            routes = list
            routeIndex = 0
            if (list.isEmpty()) {
                hint = AmapDrive.lastError ?: tr("路线规划失败", "Route planning failed")
            } else {
                // 真算出路才记进「最近去过」，免得把搜不到的点也塞进列表
                DriveRecent.push(context, target)
                recent = DriveRecent.load(context)
            }
        }
    }

    /** 开始导航：只有拿到真实路线才进入导航态。 */
    fun startGuidance() {
        val r = routes.getOrNull(routeIndex)
        if (r == null) {
            hint = tr("还没有可用路线", "No route yet")
            return
        }
        phase = NavPhase.NAV
        travelled = 0
        follow = true
        northUp = false
        recentlySpoken = ""
        mediaOpen = false
        queueOpen = false
        hint = tr("已开始导航", "Navigation started")
    }

    /** 结束本次导航：目的地留着，回到巡航态。 */
    fun stopNavigation(msg: String) {
        phase = NavPhase.CRUISE
        simulating = false
        rerouting = false
        travelled = 0
        routeIndex = 0
        follow = true
        northUp = false
        mediaOpen = true
        hint = msg
    }

    fun setVolume(f: Float) {
        val v = f.coerceIn(0f, 1f)
        volume = v
        audio?.let {
            val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            runCatching { it.setStreamVolume(AudioManager.STREAM_MUSIC, (max * v).roundToInt(), 0) }
        }
    }

    fun playPause() {
        if (MusicPlayer.current == null) {
            // 优先播放收藏里的第一首，收藏为空再退回本地音乐列表
            val first = MusicPlayer.songsByIds(MusicPlayer.favorites.toList()).firstOrNull()
                ?: runCatching { MusicPlayer.songs.firstOrNull() }.getOrNull()
            if (first != null) MusicPlayer.play(first)
            else hint = tr("先在音乐页挑一首歌", "Pick a song in Music first")
        } else {
            MusicPlayer.toggle()
        }
    }

    /** 真实搜索：高德 POI 检索，结果带坐标。 */
    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) {
            searchNote = tr("输入地名、地址或 POI", "Type a place, address or POI")
            return
        }
        searching = true
        searchNote = ""
        scope.launch {
            val list = AmapApi.searchPlaces(q, fix?.wgsLat, fix?.wgsLon, 8)
            hits = list
            searching = false
            if (list.isEmpty()) {
                searchNote = AmapApi.lastErrorHint ?: tr("没搜到相关地点", "No matching place")
            }
        }
    }

    /** 所有按钮只走这一个分发口，杜绝「同一个功能两个地方各写一遍」。 */
    fun onAction(entry: DriveEntry) {
        when (entry) {
            DriveEntry.SEARCH -> phase = NavPhase.SEARCH
            DriveEntry.VOICE -> {
                if (navigating) {
                    voiceOn = !voiceOn
                    if (voiceOn) {
                        val text = routes.getOrNull(routeIndex)?.stepAt(travelled)?.instruction
                        if (!text.isNullOrBlank()) VoiceIo.speak(context, text, if (Lang.isEn) "en" else "zh")
                    }
                    hint = if (voiceOn) tr("语音播报已开启", "Voice guidance on")
                    else tr("语音播报已关闭", "Voice guidance off")
                } else {
                    // 没导航时这里就是「问候 / 查路况」的播报，念真实路况而不是编一句台词
                    val text = if (trafficOn && trafficText.isNotBlank()) {
                        trafficText
                    } else {
                        tr("还没开始导航", "Navigation not started")
                    }
                    val ok = VoiceIo.speak(context, text, if (Lang.isEn) "en" else "zh")
                    hint = if (ok) tr("正在播报", "Speaking") else tr("没有可用的语音引擎", "No TTS engine")
                }
            }
            DriveEntry.MUSIC -> {
                mediaOpen = !mediaOpen
                if (!mediaOpen) queueOpen = false
            }
            DriveEntry.LAYERS -> {
                layerIndex = (layerIndex + 1) % 3
                hint = when (layerIndex) {
                    1 -> tr("地图：夜色", "Map: night")
                    2 -> tr("地图：卫星", "Map: satellite")
                    else -> tr("地图：标准", "Map: standard")
                }
            }
            DriveEntry.TRAFFIC -> {
                if (fix == null) {
                    hint = tr("正在定位，暂时查不了路况", "Locating, traffic unavailable")
                } else {
                    trafficOn = !trafficOn
                    hint = if (trafficOn) tr("已叠加沿途路况", "Traffic layer on")
                    else tr("路况图层已关闭", "Traffic layer off")
                }
            }
            DriveEntry.RECENTER -> {
                follow = true
                recenterTick += 1
                hint = tr("已回到当前位置", "Back to current position")
            }
            DriveEntry.ZOOM_IN -> {
                zoomDir = 1
                zoomSeq += 1
                follow = true
            }
            DriveEntry.ZOOM_OUT -> {
                zoomDir = -1
                zoomSeq += 1
            }
            DriveEntry.NORTH -> {
                northUp = !northUp
                hint = if (northUp) tr("正北向上", "North up") else tr("车头向上", "Heading up")
            }
            DriveEntry.OVERVIEW -> {
                // 同一个键两用：正在全览（或用户拖开地图）时点它回正，否则缩到整条路线
                if (!follow) {
                    follow = true
                    recenterTick += 1
                    hint = tr("已回到当前位置", "Back to current position")
                } else {
                    overviewToken += 1
                    follow = false
                    hint = tr("路线全览", "Route overview")
                }
            }
            DriveEntry.MORE -> sheet = DriveEntry.MORE
            DriveEntry.VEHICLE -> sheet = DriveEntry.VEHICLE
            DriveEntry.SOUND -> sheet = DriveEntry.SOUND
            DriveEntry.EXIT -> state.closeScreen()
        }
    }

    BackHandler {
        when {
            sheet != null -> sheet = null
            queueOpen -> queueOpen = false
            phase == NavPhase.SEARCH || phase == NavPhase.PREVIEW -> phase = NavPhase.CRUISE
            navigating || phase == NavPhase.ARRIVE -> stopNavigation(tr("已退出导航", "Navigation ended"))
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackAt < 2_000L) {
                    state.closeScreen()
                } else {
                    lastBackAt = now
                    hint = tr("再按一次退出驾驶模式", "Press back again to exit driving mode")
                }
            }
        }
    }

    // ================= 派生数据 =================
    val p = DrivingPalette
    val route = routes.getOrNull(routeIndex)
    val remainM = route?.let { (it.distanceM - travelled).coerceAtLeast(0) }
    val remainMin = route?.let { r ->
        val rm = (r.distanceM - travelled).coerceAtLeast(0)
        if (r.distanceM <= 0) 0 else (r.durationS.toLong() * rm / r.distanceM / 60).toInt()
    }
    val speedKmh = if (simulating) {
        (SIM_SPEED_MPS * 3.6f).roundToInt()
    } else {
        ((fix?.speedMps ?: 0f) * 3.6f).roundToInt().let { gps ->
            if (gps > 0) gps else fallbackSpeedKmh.roundToInt()
        }
    }
    val progress = route?.let {
        if (it.distanceM <= 0) 0f else (travelled.toFloat() / it.distanceM).coerceIn(0f, 1f)
    } ?: 0f
    val step = route?.stepAt(travelled)
    val turnDist = route?.let { (it.stepEndMeters(travelled) - travelled).coerceAtLeast(0) } ?: 0
    val etaText = remainMin?.let {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(System.currentTimeMillis() + it * 60_000L))
    } ?: "--:--"

    fun distText(m: Int?): String = when {
        m == null -> ""
        m < 1000 -> tr("$m 米", "$m m")
        else -> String.format(java.util.Locale.US, "%.1f 公里", m / 1000f)
    }

    fun bearingDeg(a: LatLon, b: LatLon): Float {
        val dLon = Math.toRadians(b.lon - a.lon)
        val la1 = Math.toRadians(a.lat)
        val la2 = Math.toRadians(b.lat)
        val y = Math.sin(dLon) * Math.cos(la2)
        val x = Math.cos(la1) * Math.sin(la2) - Math.sin(la1) * Math.cos(la2) * Math.cos(dLon)
        return ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    // 音效预设：标签与真实 EqPreset 一一对应，点下去直接改均衡器
    val eqPresets = listOf(
        EqPreset.OFF to tr("原声", "Flat"),
        EqPreset.POP to tr("流行", "Pop"),
        EqPreset.ROCK to tr("摇滚", "Rock"),
        EqPreset.JAZZ to tr("爵士", "Jazz"),
        EqPreset.BASS to tr("低音增强", "Bass boost")
    )
    val eqIndex = eqPresets.indexOfFirst { it.first == MusicPlayer.eqPreset }.coerceAtLeast(0)

    // 模拟导航时给地图造一个「沿路线跑」的车位；真实导航永远用 fix
    val simFix = if (simulating && navigating) {
        route?.let { r ->
            val a = r.pointAt(travelled)
            if (a == null) null else {
                val b = r.pointAt(travelled + 15) ?: a
                GeoFix(a.lat, a.lon, 4f, System.currentTimeMillis(), "sim", SIM_SPEED_MPS, bearingDeg(a, b))
            }
        }
    } else null
    val mapFix = simFix ?: fix

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(p.StageTop, p.Stage)))
    ) {
        // 真全屏：不再按 1080×630 等比留黑边，横向铺满整块屏幕。
        // 左右位置按宽度缩放、上下位置按高度缩放，短屏也不会把页面缩成中间一条。
        val stageW = maxWidth.value
        val stageH = maxHeight.value
        fun nx(v: Number): Dp = (v.toFloat() / DESIGN_W * stageW).dp
        fun ny(v: Number): Dp = (v.toFloat() / DESIGN_H * stageH).dp

        Box(
            Modifier
                .align(Alignment.Center)
                .width(stageW.dp)
                .height(stageH.dp)
        ) {
            // ---------- 底图：真实高德瓦片，整屏铺满 ----------
            DriveMapView(
                modifier = Modifier.fillMaxSize(),
                layer = layerIndex,
                routePoints = route?.points ?: emptyList(),
                fix = mapFix,
                follow = follow,
                northUp = northUp,
                recenterToken = recenterTick,
                overviewToken = overviewToken,
                zoomTick = zoomSeq,
                zoomDir = zoomDir,
                onUserPan = { follow = false },
                onScaleMeters = { scaleMeters = it }
            )
            // 只在上下边缘加很轻的渐变，保证白卡有对比；
            // 中间完全透明——旧版那层全屏黑色半透明会把地图糊成一块。
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.26f),
                            0.22f to Color.Transparent,
                            0.78f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.30f)
                        )
                    )
            )

            // ================= 巡航态 =================
            if (phase == NavPhase.CRUISE) {
                if (dest == null) {
                    // 搜索框：高德首页那一颗，点了进搜索
                    Row(
                        Modifier
                            .offset(nx(16), ny(14))
                            .size(nx(640), ny(68))
                            .clip(RoundedCornerShape(nx(16)))
                            .background(p.CardWhite)
                            .clickable { onAction(DriveEntry.SEARCH) }
                            .padding(horizontal = nx(18)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.Canvas(Modifier.size(nx(20))) {
                            drawNavGlyph(NavGlyph.SEARCH, p.OnCardSub)
                        }
                        Spacer(Modifier.width(nx(12)))
                        Column {
                            Text(
                                tr("搜地点、查路线", "Search places, routes"),
                                fontSize = TypeScale.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = p.OnCard,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(ny(3)))
                            Text(
                                when {
                                    myAddr.isNotBlank() -> myAddr
                                    locProblem == Locator.REASON_PERMISSION -> tr("未授权定位", "No location permission")
                                    locProblem == Locator.REASON_SERVICE_OFF -> tr("系统定位已关闭", "Location is off")
                                    fix == null -> tr("正在定位…", "Locating…")
                                    else -> tr("当前位置已就绪", "Location ready")
                                },
                                fontSize = TypeScale.caption,
                                color = p.OnCardFaint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    // 已选目的地：卡片 + 开始导航（进预览页）
                    Row(
                        Modifier
                            .offset(nx(16), ny(14))
                            .size(nx(640), ny(72))
                            .clip(RoundedCornerShape(nx(16)))
                            .background(p.CardWhite)
                            .padding(start = nx(16), end = nx(12)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.Canvas(Modifier.size(nx(22))) {
                            drawNavGlyph(NavGlyph.PIN, p.GoBlue)
                        }
                        Spacer(Modifier.width(nx(12)))
                        val cur = dest
                        Column(Modifier.weight(1f)) {
                            Text(
                                cur?.name.orEmpty(),
                                fontSize = TypeScale.subtitle,
                                fontWeight = FontWeight.Bold,
                                color = p.OnCard,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(ny(3)))
                            Text(
                                listOfNotNull(
                                    cur?.address?.takeIf { it.isNotBlank() },
                                    if (fix != null && cur != null) {
                                        val m = GeoUtil.metersBetween(
                                            fix!!.wgsLat, fix!!.wgsLon, cur.lat, cur.lon
                                        ).toInt()
                                        tr("距你 " + distText(m), distText(m) + " away")
                                    } else null
                                ).joinToString(" · "),
                                fontSize = TypeScale.caption,
                                color = p.OnCardFaint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            Modifier
                                .size(nx(104), ny(40))
                                .clip(RoundedCornerShape(nx(10)))
                                .background(p.GoBlue)
                                .clickable { dest?.let { planRoutes(it) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                tr("路线", "Routes"),
                                fontSize = TypeScale.label,
                                fontWeight = FontWeight.Bold,
                                color = p.CardWhite,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.width(nx(8)))
                        Box(
                            Modifier
                                .size(nx(32), ny(32))
                                .clip(CircleShape)
                                .background(p.CardWhiteSub)
                                .clickable {
                                    dest = null
                                    routes = emptyList()
                                    phase = NavPhase.CRUISE
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Canvas(Modifier.size(nx(11))) {
                                drawNavGlyph(NavGlyph.CLOSE, p.OnCardSub)
                            }
                        }
                    }
                }

                // 右侧四个圆钮：图层 / 路况 / 回正 / 更多
                DriveRoundButton(nx = { nx(it) }, ny = { ny(it) }, 1018, 110, 46, NavGlyph.LAYER, onClick = { onAction(DriveEntry.LAYERS) })
                DriveRoundButton(nx = { nx(it) }, ny = { ny(it) }, 1018, 166, 46, NavGlyph.TRAFFIC, active = trafficOn, onClick = { onAction(DriveEntry.TRAFFIC) })
                DriveRoundButton(nx = { nx(it) }, ny = { ny(it) }, 1018, 222, 46, NavGlyph.CAR, onClick = { onAction(DriveEntry.RECENTER) })
                DriveRoundButton(nx = { nx(it) }, ny = { ny(it) }, 1018, 278, 46, NavGlyph.MORE, onClick = { onAction(DriveEntry.MORE) })

                // 本次行程：进入驾驶页后累计的真实位移
                Row(
                    Modifier
                        .offset(nx(16), ny(470))
                        .size(nx(320), maxOf(ny(58), 56.dp))
                        .clip(RoundedCornerShape(nx(14)))
                        .background(p.CardWhite.copy(alpha = 0.96f))
                        .clickable { onAction(DriveEntry.VEHICLE) }
                        .padding(horizontal = nx(16)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr("本次行程", "This trip"),
                            fontSize = TypeScale.micro,
                            color = p.OnCardSub,
                            maxLines = 1
                        )
                        Text(
                            distText(tripMeters),
                            fontSize = TypeScale.subtitle,
                            fontWeight = FontWeight.Bold,
                            color = p.OnCard,
                            maxLines = 1
                        )
                    }
                    Text(
                        when {
                            fix == null -> tr("定位中", "Locating")
                            else -> "±${fix!!.accuracyM.roundToInt()} m"
                        },
                        fontSize = TypeScale.caption,
                        color = p.OnCardSub,
                        maxLines = 1
                    )
                }

                // 播放器 + 队列
                if (mediaOpen) {
                    val hasTrack = MusicPlayer.current != null
                    // 驾驶页列表优先级：我的收藏 → 当前队列 → 本地音乐列表。
                    // 收藏是用户自己筛过的歌，开车时最需要「点开就能放」，不用再进音乐页。
                    val favorites = MusicPlayer.songsByIds(MusicPlayer.favorites.toList())
                    val playQueue = MusicPlayer.currentQueue
                    val library = MusicPlayer.songs
                    val listedSongs = favorites.ifEmpty {
                        if (playQueue.isNotEmpty()) playQueue else library
                    }
                    val listedTitle = when {
                        favorites.isNotEmpty() -> tr("我的收藏", "Favorites")
                        playQueue.isNotEmpty() -> tr("播放列表", "Queue")
                        else -> tr("音乐列表", "Music library")
                    }
                    DriveMiniPlayer(
                        nx = { nx(it) },
                        ny = { ny(it) },
                        title = MusicPlayer.current?.title?.takeIf { it.isNotBlank() }
                            ?: tr("未在播放", "Nothing playing"),
                        artist = MusicPlayer.current?.artist?.takeIf { it.isNotBlank() }
                            ?: if (listedSongs.isNotEmpty()) {
                                tr("点 ▶ 或打开收藏列表选歌", "Tap ▶ or pick from favorites")
                            } else {
                                tr("本地音乐还是空的", "Music library is empty")
                            },
                        progress = if (hasTrack) MusicPlayer.progress.coerceIn(0f, 1f) else 0f,
                        positionText = if (hasTrack) mmss(MusicPlayer.positionMs) else "00:00",
                        durationText = if (hasTrack && MusicPlayer.durationMs > 0L) mmss(MusicPlayer.durationMs) else "--:--",
                        isPlaying = MusicPlayer.isPlaying,
                        onPrev = {
                            if (MusicPlayer.current == null) playPause() else MusicPlayer.prev()
                        },
                        onPlayPause = { playPause() },
                        onNext = {
                            if (MusicPlayer.current == null) playPause() else MusicPlayer.next()
                        },
                        onQueue = { queueOpen = !queueOpen },
                        onCollapse = {
                            mediaOpen = false
                            queueOpen = false
                            hint = tr("已收起播放器，点「展开播放器」可恢复", "Player hidden · tap Show player to reopen")
                        },
                        onSeek = { f ->
                            if (hasTrack) MusicPlayer.seekTo(f)
                            else hint = tr("播放后可拖动进度", "Start playback to seek")
                        }
                    )
                    if (queueOpen) {
                        DriveQueueSheet(
                            nx = { nx(it) },
                            ny = { ny(it) },
                            title = listedTitle,
                            songs = listedSongs,
                            currentId = MusicPlayer.current?.id,
                            onPick = { song ->
                                // 用列表里的顺序播，上一首/下一首才连贯
                                MusicPlayer.playFrom(listedSongs, song)
                                queueOpen = false
                                hint = tr("正在播放 ${song.title}", "Playing ${song.title}")
                            },
                            onClose = { queueOpen = false }
                        )
                    }
                } else {
                    // 收起后的展开入口：占播放器原来的位置。
                    // 旧版收起后只留一句「点音乐可再打开」，但巡航态并没有音乐按钮，
                    // 用户找不到回来的路，所以这里补一个明确的「展开播放器」胶囊。
                    Row(
                        Modifier
                            .offset(nx(600), ny(470) + maxOf(ny(128), 104.dp) - maxOf(ny(60), 48.dp))
                            .size(nx(464), maxOf(ny(60), 48.dp))
                            .clip(RoundedCornerShape(nx(18)))
                            .background(Brush.verticalGradient(listOf(p.PanelTop, p.PanelBottom)))
                            .clickable {
                                mediaOpen = true
                                hint = tr("已展开播放器", "Player shown")
                            }
                            .padding(horizontal = nx(18)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.Canvas(Modifier.size(nx(20))) {
                            drawNavGlyph(NavGlyph.SOUND, Ink.OnCover)
                        }
                        Spacer(Modifier.width(nx(12)))
                        Column(Modifier.weight(1f)) {
                            Text(
                                tr("展开播放器", "Show player"),
                                fontSize = TypeScale.caption,
                                fontWeight = FontWeight.Bold,
                                color = Ink.OnCover,
                                maxLines = 1
                            )
                            Text(
                                MusicPlayer.current?.let { "${it.title} · ${it.artist}" }
                                    ?: tr("还没有正在播放的歌", "Nothing playing yet"),
                                fontSize = TypeScale.micro,
                                color = Ink.OnCoverSub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // 向上箭头：明确这是「展开」动作，而不是又一个菜单入口
                        androidx.compose.foundation.Canvas(Modifier.size(nx(14))) {
                            val u = size.minDimension
                            drawLine(
                                Ink.OnCover,
                                Offset(u * 0.15f, u * 0.62f),
                                Offset(u * 0.5f, u * 0.26f),
                                strokeWidth = u * 0.18f,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                Ink.OnCover,
                                Offset(u * 0.5f, u * 0.26f),
                                Offset(u * 0.85f, u * 0.62f),
                                strokeWidth = u * 0.18f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }

            // ================= 导航态 =================
            if (navigating) {
                val actionText = step?.action?.takeIf { it.isNotBlank() }
                    ?: when (navGlyphFor(step?.action.orEmpty(), step?.instruction.orEmpty())) {
                        NavGlyph.ARRIVE -> tr("到达", "Arrive")
                        else -> tr("继续直行", "Continue")
                    }
                // 清洗「随即动作」：去掉数组 toString 留下的空括号 []，避免副标题出现「然后 []」
                val assistant = step?.assistantAction.orEmpty().trim()
                    .removeSurrounding("[", "]").trim()
                    .takeIf { it.isNotBlank() && it != "[]" } ?: ""
                DriveGuideCard(
                    nx = { nx(it) },
                    ny = { ny(it) },
                    distance = if (turnDist >= 1000) {
                        String.format(java.util.Locale.US, "%.1f", turnDist / 1000f)
                    } else turnDist.toString(),
                    unit = if (turnDist >= 1000) tr("公里", "km") else tr("米", "m"),
                    action = actionText,
                    road = step?.road.orEmpty(),
                    next = when {
                        rerouting -> tr("偏航，正在重新规划…", "Off route, rerouting…")
                        planning -> tr("正在规划路线…", "Planning route…")
                        step == null -> tr("等待路线…", "Waiting for route…")
                        assistant.isNotBlank() -> tr("然后 " + assistant, "Then " + assistant)
                        else -> tr("沿当前道路继续行驶", "Continue on this road")
                    },
                    lanes = emptyList(),
                    turn = navGlyphFor(step?.action.orEmpty(), step?.instruction.orEmpty()),
                    hintPill = when {
                        rerouting || planning -> ""
                        assistant.isNotBlank() -> ""
                        else -> tr("沿当前道路继续行驶", "Continue on this road")
                    },
                    onTap = {
                        val text = step?.instruction
                        if (text.isNullOrBlank()) {
                            hint = tr("还没有可播报的引导", "No guidance yet")
                        } else {
                            hint = if (VoiceIo.speak(context, text, if (Lang.isEn) "en" else "zh")) {
                                tr("正在播报引导", "Reading guidance")
                            } else {
                                tr("这台设备没有可用的语音引擎", "No TTS engine available")
                            }
                        }
                    }
                )
                DriveTripCard(
                    nx = { nx(it) },
                    ny = { ny(it) },
                    minutes = (remainMin ?: 0).toString(),
                    km = String.format(java.util.Locale.US, "%.1f 公里", (remainM ?: 0) / 1000f),
                    eta = tr("$etaText 到达", "Arrive $etaText"),
                    progress = progress
                )

                // 顶部右侧三钮：路况 / 图层 / 更多
                DriveRoundButton(nx = { nx(it) }, ny = { ny(it) }, 906, 14, 46, NavGlyph.TRAFFIC, active = trafficOn, onClick = { onAction(DriveEntry.TRAFFIC) })
                DriveRoundButton(nx = { nx(it) }, ny = { ny(it) }, 962, 14, 46, NavGlyph.LAYER, onClick = { onAction(DriveEntry.LAYERS) })
                DriveRoundButton(nx = { nx(it) }, ny = { ny(it) }, 1018, 14, 46, NavGlyph.MORE, onClick = { onAction(DriveEntry.MORE) })

                // 右侧缩放 / 正北
                DriveRoundButton(nx = { nx(it) }, ny = { ny(it) }, 1018, 86, 46, NavGlyph.PLUS, onClick = { onAction(DriveEntry.ZOOM_IN) })
                DriveRoundButton(nx = { nx(it) }, ny = { ny(it) }, 1018, 142, 46, NavGlyph.MINUS, onClick = { onAction(DriveEntry.ZOOM_OUT) })
                DriveRoundButton(nx = { nx(it) }, ny = { ny(it) }, 1018, 198, 46, NavGlyph.COMPASS, active = northUp, onClick = { onAction(DriveEntry.NORTH) })

                // 左侧语音播报开关
                DriveRoundButton(
                    nx = { nx(it) }, ny = { ny(it) }, 20, 170, 46, NavGlyph.VOICE,
                    active = voiceOn, onClick = { onAction(DriveEntry.VOICE) }
                )

                // 车速 + 限速：有定位才有车速，限速牌没有真实数据源就不画
                if (fix != null || simulating) {
                    DriveSpeedGauge(nx = { nx(it) }, ny = { ny(it) }, speed = speedKmh.toString())
                }

                if (trafficOn && trafficText.isNotBlank()) {
                    Text(
                        trafficText,
                        Modifier
                            .offset(nx(20), ny(228))
                            .clip(RoundedCornerShape(nx(10)))
                            .background(p.GuideDark.copy(alpha = 0.86f))
                            .padding(horizontal = nx(12), vertical = ny(7)),
                        fontSize = TypeScale.caption,
                        color = Ink.OnCover,
                        maxLines = 1
                    )
                }

                if (phase == NavPhase.JUNCTION) {
                    DriveJunctionCard(
                        nx = { nx(it) }, ny = { ny(it) },
                        fromRoad = step?.road?.takeIf { it.isNotBlank() } ?: tr("当前道路", "Current road"),
                        toRoad = routes.getOrNull(routeIndex)?.let { r ->
                            val idx = r.steps.indexOfFirst { it === step }
                            r.steps.getOrNull(idx + 1)?.road?.takeIf { it.isNotBlank() }
                        } ?: tr("继续直行", "Continue straight"),
                        turn = navGlyphFor(step?.action.orEmpty(), step?.instruction.orEmpty()),
                        distance = distText(turnDist)
                    )
                }
                if (phase == NavPhase.HIGHWAY) {
                    DriveHighwayCard(
                        nx = { nx(it) }, ny = { ny(it) },
                        exitNo = step?.road?.filter { it.isDigit() }?.takeIf { it.isNotBlank() } ?: "--",
                        road = step?.instruction ?: tr("沿高速行驶", "Continue on expressway"),
                        detail = tr(
                            "剩余 " + distText(remainM) + " · " + (dest?.name.orEmpty()),
                            distText(remainM) + " left · " + dest?.name.orEmpty()
                        )
                    )
                }

                DriveBottomBar(
                    nx = { nx(it) }, ny = { ny(it) },
                    remainText = tr(
                        "剩余 ${remainMin ?: 0} 分钟 · " + distText(remainM),
                        "${remainMin ?: 0} min left · " + distText(remainM)
                    ),
                    subText = tr(
                        "$etaText 到达 · 全程 " + distText(route?.distanceM),
                        "Arrive $etaText · " + distText(route?.distanceM) + " total"
                    ),
                    progress = progress,
                    rerouting = rerouting,
                    overviewing = !follow,
                    onOverview = { onAction(DriveEntry.OVERVIEW) },
                    onExit = { stopNavigation(tr("已退出导航", "Navigation ended")) }
                )
            }

            // ================= 搜索 =================
            if (phase == NavPhase.SEARCH) {
                DriveSearchPanel(
                    nx = { nx(it) }, ny = { ny(it) },
                    query = query,
                    onQueryChange = { query = it },
                    searching = searching,
                    note = searchNote,
                    hits = hits.map { poi ->
                        DrivePlaceHit(
                            title = poi.name,
                            addr = poi.address.ifBlank { poi.typeName },
                            dist = if (fix == null) tr("距离未知", "unknown") else distText(
                                GeoUtil.metersBetween(fix!!.wgsLat, fix!!.wgsLon, poi.lat, poi.lon).toInt()
                            ),
                            point = LatLon(poi.lat, poi.lon)
                        )
                    },
                    recent = recent.map { poi ->
                        DrivePlaceHit(
                            title = poi.name,
                            addr = poi.address,
                            dist = if (fix == null) tr("距离未知", "unknown") else distText(
                                GeoUtil.metersBetween(fix!!.wgsLat, fix!!.wgsLon, poi.lat, poi.lon).toInt()
                            ),
                            point = LatLon(poi.lat, poi.lon)
                        )
                    },
                    onSearch = { doSearch() },
                    onPick = { hit ->
                        val pt = hit.point
                        if (pt == null) {
                            hint = tr("这条结果没有坐标，换一个试试", "No coordinates for this result")
                        } else {
                            // 选中即进路线预览，和高德一样：先挑方案再开导航
                            planRoutes(PoiResult(hit.title, hit.addr, pt.lat, pt.lon))
                            query = hit.title
                        }
                    },
                    onClose = { phase = NavPhase.CRUISE }
                )
            }

            // ================= 路线预览 =================
            if (phase == NavPhase.PREVIEW) {
                DriveRoutePanel(
                    nx = { nx(it) }, ny = { ny(it) },
                    targetName = dest?.name.orEmpty(),
                    routes = routes,
                    selected = routeIndex,
                    pref = pref,
                    planning = planning,
                    onSelect = { routeIndex = it },
                    onPref = { item ->
                        pref = item
                        dest?.let { planRoutes(it) }
                    },
                    onStart = { startGuidance() },
                    onBack = { phase = NavPhase.CRUISE }
                )
            }

            // ================= 到达 =================
            if (phase == NavPhase.ARRIVE) {
                DriveArriveCard(
                    nx = { nx(it) }, ny = { ny(it) },
                    name = dest?.name ?: tr("目的地", "Destination"),
                    addr = listOfNotNull(dest?.address).filter { it.isNotBlank() }
                        .joinToString(" · ").ifBlank { tr("暂无详细地址", "No address") },
                    km = String.format(java.util.Locale.US, "%.1f", (route?.distanceM ?: 0) / 1000f),
                    minutes = ((route?.durationS ?: 0) / 60).toString(),
                    toll = (route?.tollsYuan ?: 0).toString(),
                    onParking = {
                        query = tr("停车场", "parking")
                        phase = NavPhase.SEARCH
                        doSearch()
                    },
                    onFinish = {
                        stopNavigation(tr("本次导航已结束", "Navigation ended"))
                        dest = null
                        routes = emptyList()
                    }
                )
            }

            // 合规标注一直在图上（图源 / 审图号 / 比例尺），与界面态无关
            DriveComplianceStrip(
                nx = { nx(it) }, ny = { ny(it) },
                meters = scaleMeters
            )

            // ================= 浮层 =================
            val openSheet = sheet
            if (openSheet == DriveEntry.MORE) {
                DriveMorePanel(
                    nx = { nx(it) }, ny = { ny(it) },
                    voiceOn = voiceOn,
                    onVoice = { voiceOn = !voiceOn },
                    northUp = northUp,
                    onNorthUp = { northUp = !northUp },
                    trafficOn = trafficOn,
                    onTraffic = { onAction(DriveEntry.TRAFFIC) },
                    simulating = simulating,
                    onSimulate = {
                        if (!navigating) {
                            hint = tr("先开始导航再模拟", "Start navigation first")
                        } else {
                            simulating = !simulating
                            hint = if (simulating) tr("模拟导航已开启（沿真实路线推进）", "Simulating along the route")
                            else tr("模拟导航已关闭", "Simulation off")
                        }
                    },
                    accuracy = when {
                        locProblem == Locator.REASON_PERMISSION -> tr("未授权", "denied")
                        locProblem == Locator.REASON_SERVICE_OFF -> tr("定位关闭", "off")
                        fix == null -> tr("定位中", "locating")
                        else -> "±${fix!!.accuracyM.roundToInt()} m"
                    },
                    eqName = eqPresets[eqIndex].second,
                    onSound = { sheet = DriveEntry.SOUND },
                    onExit = {
                        sheet = null
                        onAction(DriveEntry.EXIT)
                    },
                    onClose = { sheet = null }
                )
            }
            if (openSheet == DriveEntry.VEHICLE) {
                DriveTripSheet(
                    nx = { nx(it) }, ny = { ny(it) },
                    clock = clock,
                    weather = state.weatherNow?.let { w ->
                        (w.temp?.let { "$it°C" } ?: "--°C") + " " + w.desc
                    } ?: tr("天气加载中", "Loading weather"),
                    speed = if (fix == null) "--" else "$speedKmh km/h",
                    remain = remainM?.let { distText(it) } ?: tr("未规划", "No route"),
                    trip = distText(tripMeters),
                    traffic = trafficText.takeIf { it.isNotBlank() } ?: tr("未查询", "Not checked"),
                    accuracy = if (fix == null) tr("定位中", "Locating") else "±${fix!!.accuracyM.roundToInt()} m",
                    onClose = { sheet = null }
                )
            }
            if (openSheet == DriveEntry.SOUND) {
                DriveSoundSheet(
                    nx = { nx(it) }, ny = { ny(it) },
                    presets = eqPresets.map { it.second },
                    selected = eqIndex,
                    current = eqPresets[eqIndex].second,
                    volume = volume,
                    onPreset = { idx -> MusicPlayer.applyEqPreset(eqPresets[idx].first) },
                    onVolume = { setVolume(it) },
                    onClose = { sheet = null }
                )
            }

            if (hint.isNotEmpty()) {
                Text(
                    hint,
                    Modifier
                        .offset(nx(392), ny(474))
                        .clip(RoundedCornerShape(nx(12)))
                        .background(p.GuideDark.copy(alpha = 0.94f))
                        .padding(horizontal = nx(16), vertical = ny(9)),
                    fontSize = TypeScale.body,
                    color = Ink.OnCover,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 全屏沉浸生命周期：进页面隐藏系统状态栏 / 导航栏、常亮、请求横屏；
 * 退出时**全部还原**（尤其是全局竖屏锁定，漏了会让整个 App 一直横着）。
 */
@Composable
private fun DrivingImmersiveEffect() {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(context, view) {
        val activity = ScreenRotation.findActivity(context)
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldLightStatus = controller?.isAppearanceLightStatusBars
        val oldLightNav = controller?.isAppearanceLightNavigationBars
        runCatching {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            runCatching {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                oldLightStatus?.let { controller?.isAppearanceLightStatusBars = it }
                oldLightNav?.let { controller?.isAppearanceLightNavigationBars = it }
            }
        }
    }
}

private fun mmss(ms: Long): String {
    val s = (ms / 1000L).coerceAtLeast(0L)
    return "%02d:%02d".format(s / 60, s % 60)
}
