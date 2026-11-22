package com.example.my.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.activity.compose.BackHandler
import com.example.my.data.music.MusicPlayer
import com.example.my.model.AppNotification
import com.example.my.model.Footprint
import com.example.my.model.NotifType
import com.example.my.model.TripPlan
import com.example.my.state.AppState
import com.example.my.data.news.HotBoard
import com.example.my.data.news.NewsApi
import com.example.my.data.stock.MarketQuote
import com.example.my.data.stock.StockApi
import com.example.my.state.ExtraScreen
import com.example.my.ui.components.AppBanner
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Func
import com.example.my.ui.theme.Layer
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.TypeScale
import com.example.my.ui.theme.cardShadow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 负一屏（发现页向右滑出）的进度控制器。
 *
 * 拖拽阶段用 [raw] 直接跟手（每帧只改一个 float，不启新协程），松手后再交给 [anim]
 * 用弹簧收拢——所以拖到一半松手是「回弹或落位」，不是「跳一下再动」。
 *
 * 0f = 收起（主框架原样），1f = 完全展开。
 */
@Stable
class DashboardCtrl {
    private var dragging by mutableStateOf(false)
    private var raw by mutableFloatStateOf(0f)
    private var hidden by mutableStateOf(false)
    private val anim = Animatable(0f)

    val progress: Float get() = if (hidden) 0f else if (dragging) raw else anim.value

    /** 只要还有一帧可见就为 true：MainActivity 用它决定要不要把仪表盘留在组合里 */
    val visible: Boolean get() = !hidden && (dragging || anim.value > 0.0005f)

    /**
     * 立刻收起，不播动画。
     *
     * 点组件要跳到别的页时只能走这条：收起动画跑在面板自己的 rememberCoroutineScope 里，
     * 动画一结束 visible 变 false，面板马上退出组合，那个 scope 随之被取消，
     * 排在 close() 后面的 action() 就永远执行不到——表现就是「点了没反应」。
     */
    fun hideNow() {
        dragging = false
        hidden = true
    }

    fun onDrag(deltaX: Float, widthPx: Float) {
        if (widthPx <= 0f) return
        if (!dragging) {
            dragging = true
            // 上次是「点组件跳页被立刻收起」的，从 0 重新拉开，不要从残留的 1f 跳变
            raw = if (hidden) 0f else anim.value
            hidden = false
        }
        raw = (raw + deltaX / (widthPx * 0.85f)).coerceIn(0f, 1f)
    }

    /** 松手：过半就落位展开，否则弹回收起 */
    suspend fun settle() {
        val from = progress
        val target = if (from >= 0.42f) 1f else 0f
        anim.snapTo(from)
        dragging = false
        anim.animateTo(target, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow))
    }

    /** 回收起态（点链接离开、按返回时用，不需要过半判断） */
    suspend fun close() {
        if (hidden) return
        val from = progress
        if (from <= 0.0005f) {
            dragging = false
            return
        }
        anim.snapTo(from)
        dragging = false
        anim.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
    }

    /** 点按钮直接推开（发现页左上那个圆钮走这条，手感与拖拽后半程一致） */
    suspend fun open() {
        if (hidden) {
            hidden = false
            anim.snapTo(0f)
        }
        val from = progress
        if (from >= 1f) {
            dragging = false
            return
        }
        anim.snapTo(from)
        dragging = false
        anim.animateTo(1f, spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow))
    }
}

/**
 * 可添加的组件。key 会写进 prefs，改名会清空用户的组件配置，所以别随手动。
 */
enum class DashWidget(val key: String, val zh: String, val en: String, val icon: ImageVector) {
    GUIDE("guide", "继续看的攻略", "Keep reading", Icons.AutoMirrored.Outlined.Assignment),
    FOOTPRINT("footprint", "最近一条足迹", "Latest footprint", Icons.Outlined.LocationCity),
    MUSIC("music", "正在听", "Now playing", Icons.Outlined.PlayArrow),
    PLAN("plan", "下一段行程", "Next trip", Icons.Outlined.CardTravel),
    NOTIF("notif", "未读消息", "Unread", Icons.Outlined.Star),
    STATS("stats", "今年走过", "This year", Icons.Outlined.EmojiEvents),
    NEWS("news", "今日热点", "Hot topics", Icons.AutoMirrored.Outlined.Assignment),
    STOCK("stock", "股市行情", "Markets", Icons.Outlined.Public);

    companion object {
        fun byKey(key: String): DashWidget? = entries.firstOrNull { it.key == key }
    }
}

private object DashPrefs {
    private const val NAME = "dashboard_v1"
    private const val KEY = "widgets"

    /** 首次进入的默认四件：看过什么、在听什么、下一步去哪、最近留下了什么 */
    private val defaults =
        listOf(DashWidget.GUIDE, DashWidget.MUSIC, DashWidget.PLAN, DashWidget.FOOTPRINT)

    fun load(ctx: Context): List<DashWidget> {
        val raw = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return defaults
        val parsed = raw.split(",").mapNotNull { DashWidget.byKey(it) }.distinct()
        return if (parsed.isEmpty()) defaults else parsed
    }

    fun save(ctx: Context, list: List<DashWidget>) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY, list.joinToString(",") { it.key })
            .apply()
    }
}

/** 行情卡里一行指数：名称 + 涨跌幅（涨红跌绿，和股市页一致） */
private data class WidgetRow(val name: String, val pct: String, val up: Boolean)

private val StockUp = Color(0xFFD93B33)
private val StockDown = Color(0xFF15935F)

/** 指数点位：3888.66 → 3,888.66 */
private fun quotePrice(v: Double): String = String.format(Locale.CHINA, "%,.2f", v)

/** 涨跌幅带符号；颜色由调用方按涨红跌绿决定 */
private fun changeText(q: MarketQuote): String =
    (if (q.changePct >= 0) "+" else "") + String.format(Locale.CHINA, "%.2f%%", q.changePct)

/** 组件卡要显示的四行信息 + 点击后的动作 */
private data class WidgetFace(
    val widget: DashWidget,
    val label: String,
    val value: String,
    val hint: String,
    /** 行情 / 热点这类需要多行明细的卡用 [rows]，不用时走 value + hint 两行 */
    val rows: List<WidgetRow> = emptyList(),
    /** value 的取色：行情卡按涨跌上红下绿 */
    val valueColor: Color? = null,
    // onClick 放最后：调用处都用尾随 lambda，参数顺序变了尾随 lambda 会绑到别的参数上
    val onClick: () -> Unit
)

@Composable
fun DashboardScreen(state: AppState, ctrl: DashboardCtrl) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var widgets by remember { mutableStateOf(DashPrefs.load(ctx)) }
    var editing by remember { mutableStateOf(false) }
    var pickOpen by remember { mutableStateOf(false) }
    var planSheet by remember { mutableStateOf<TripPlan?>(null) }
    var footprintSheet by remember { mutableStateOf<Footprint?>(null) }

    val p = ctrl.progress

    // 打开别的页面之前先把负一屏收回去。
    // 直接同步收起（不播动画）：动画跑在本面板的 scope 里，面板一退出组合 scope 就被取消，
    // 排在动画后面的导航代码根本执行不到。
    val leave: (() -> Unit) -> Unit = { action ->
        ctrl.hideNow()
        action()
    }

    BackHandler { ctrl.hideNow() }

    // ---------- 数据 ----------
    val guide = state.lastViewedGuideId?.let { id -> state.guides.firstOrNull { it.id == id } }
        ?: state.guides.firstOrNull()
    val latest = state.footprints.maxByOrNull { it.dateMillis ?: 0L } ?: state.footprints.lastOrNull()
    val song = MusicPlayer.current
    val nextPlan = remember(state.tripPlans) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val live = state.tripPlans.filter { !it.archived }
        live.sortedBy { it.startDate }.firstOrNull { it.startDate >= today }
            ?: live.maxByOrNull { it.startDate }
    }
    val unread = state.notifications.count { !it.read }
    val cities = state.footprints.map { it.city }.filter { it.isNotBlank() }.distinct().size

    // 热点 / 行情只有用户真的把这两张卡加进负一屏时才联网：
    // 没人看的时候不该每次开面板都发一次请求
    val wantsNews = DashWidget.NEWS in widgets
    val wantsStock = DashWidget.STOCK in widgets
    val hotBoard by produceState<HotBoard?>(initialValue = null, wantsNews) {
        value = if (!wantsNews) null else withContext(Dispatchers.IO) {
            runCatching { NewsApi.hotAll().firstOrNull() }.getOrNull()
        }
    }
    val indexQuotes by produceState<List<MarketQuote>>(initialValue = emptyList(), wantsStock) {
        value = if (!wantsStock) emptyList() else withContext(Dispatchers.IO) {
            runCatching { StockApi.headlineQuotes() }.getOrDefault(emptyList())
        }
    }

    val faces = listOf(
        WidgetFace(
            DashWidget.GUIDE,
            tr("继续看的攻略", "Keep reading"),
            guide?.title ?: tr("还没看过攻略", "No guide yet"),
            guide?.let { "${it.city} · ${it.days} ${tr("天", "days")}" }
                ?: tr("去发现页挑一篇", "Pick one in Discover")
        ) {
            if (guide != null) leave { state.openGuide(guide.id) }
            else leave { state.activeTab = com.example.my.state.AppTab.DISCOVER }
        },
        WidgetFace(
            DashWidget.FOOTPRINT,
            tr("最近一条足迹", "Latest footprint"),
            latest?.name ?: tr("还没有足迹", "No footprint"),
            latest?.let { "${it.city} · ${it.date}" } ?: tr("点底部的 ＋ 记第一条", "Tap + to add one")
        ) { latest?.let { footprintSheet = it } },
        WidgetFace(
            DashWidget.MUSIC,
            tr("正在听", "Now playing"),
            song?.title ?: tr("还没听歌", "Nothing playing"),
            song?.artist ?: tr("去音乐页挑一首", "Open music")
        ) { leave { state.openScreen(ExtraScreen.MUSIC) } },
        WidgetFace(
            DashWidget.PLAN,
            tr("下一段行程", "Next trip"),
            nextPlan?.name ?: tr("还没有行程", "No trip yet"),
            nextPlan?.let { "${it.destination} · ${it.startDate}" }
                ?: tr("去行程页新建一段", "Create one")
        ) { nextPlan?.let { planSheet = it } },
        WidgetFace(
            DashWidget.NOTIF,
            tr("未读消息", "Unread"),
            tr("$unread 条未读", "$unread unread"),
            state.notifications.firstOrNull { !it.read }?.title
                ?: tr("没有新消息", "All caught up")
        ) { leave { state.openScreen(ExtraScreen.NOTIFICATIONS) } },
        WidgetFace(
            DashWidget.STATS,
            tr("今年走过", "This year"),
            tr("${state.footprints.size} 条足迹", "${state.footprints.size} footprints"),
            tr("$cities 座城市", "$cities cities")
        ) { leave { state.openScreen(ExtraScreen.YEAR_REPORT) } },
        WidgetFace(
            DashWidget.NEWS,
            tr("今日热点", "Hot topics"),
            hotBoard?.items?.firstOrNull()?.title ?: tr("热点加载中…", "Loading…"),
            hotBoard?.let { "${it.name} · ${it.items.size} ${tr("条", "items")}" }
                ?: tr("点开资讯看全部", "Open News")
        ) { leave { state.openScreen(ExtraScreen.NEWS) } },
        WidgetFace(
            DashWidget.STOCK,
            tr("股市行情", "Markets"),
            indexQuotes.firstOrNull()?.let { "${it.name} ${quotePrice(it.price)}" }
                ?: tr("行情加载中…", "Loading…"),
            indexQuotes.firstOrNull()?.let { changeText(it) }
                ?: tr("点开看 A股 / 港股 / 美股", "Open markets"),
            rows = indexQuotes.take(3).map {
                WidgetRow(it.name, changeText(it), it.changePct >= 0)
            },
            valueColor = indexQuotes.firstOrNull()?.let { if (it.changePct >= 0) StockUp else StockDown }
        ) { leave { state.openScreen(ExtraScreen.STOCK) } }
    )
    val shown = widgets.mapNotNull { key -> faces.firstOrNull { it.widget == key } }
    val candidates = DashWidget.entries.filter { it !in widgets }

    // ---------- 版式 ----------
    // 负一屏是「占屏四分之三的侧边面板」，不是整页：左边贴着屏幕，右边留一条看见主页面，
    // 这样收起来时是面板滑走，而不是换了一整屏。
    Box(Modifier.fillMaxSize()) {
        // 面板之外那条区域轻压暗，给一层层次感（不是全屏遮罩）；点它就收回负一屏
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.12f * p))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { scope.launch { ctrl.close() } }
        )

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.76f)
                .graphicsLayer {
                    // 面板整体从左侧滑入：收起时完全退到屏幕外
                    translationX = -(1f - p) * size.width
                }
                .clip(
                    RoundedCornerShape(
                        // 右侧圆角固定：它是贴着左边缘的一张卡，右边要一直是圆角
                        topEnd = 26.dp,
                        bottomEnd = 26.dp
                    )
                )
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Bg.Surface.copy(alpha = 1f),
                            Bg.SurfaceDim.copy(alpha = 1f)
                        )
                    )
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { scope.launch { ctrl.settle() } },
                        onDragCancel = { scope.launch { ctrl.settle() } }
                    ) { change, dragAmount ->
                        if (ctrl.progress <= 0.0005f) return@detectHorizontalDragGestures
                        change.consume()
                        ctrl.onDrag(dragAmount, size.width.toFloat())
                    }
                }
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(6.dp))

                // ---------- 顶栏：天气 / 编辑 / 设置 ----------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Layer.Opaque)
                            .clickable { leave { state.openScreen(ExtraScreen.WEATHER) } }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.WbSunny,
                            null,
                            tint = Brand.Gold,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        val temp = state.weatherNow?.temp
                        Text(
                            if (temp != null) "$temp°" else tr("天气", "Weather"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Text.Primary
                        )
                        val desc = state.weatherNow?.desc
                        if (desc != null) {
                            Spacer(Modifier.width(5.dp))
                            Text(desc, fontSize = 12.sp, color = Text.Secondary)
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(
                            state.weatherNow?.city ?: state.weatherCity,
                            fontSize = 12.sp,
                            color = Text.Secondary
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { editing = !editing },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = tr("编辑组件", "Edit widgets"),
                            tint = if (editing) Brand.Terracotta else Text.Secondary,
                            modifier = Modifier.size(20.dp)
                                .graphicsLayer { rotationZ = if (editing) 45f else 0f }
                        )
                    }
                    IconButton(
                        onClick = { leave { state.openScreen(ExtraScreen.SETTINGS) } },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = tr("设置", "Settings"),
                            tint = Text.Secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                // 驾驶模式直达：负一屏的唯一入口，整行大热区方便开车前单手点。
                // 必须走 leave{} 同步收起，否则本面板会盖在沉浸页之上。
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Layer.Opaque)
                        .clickable { leave { state.openScreen(ExtraScreen.DRIVING) } }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Brand.Teal.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.DirectionsCar, null,
                            tint = Brand.Teal, modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr("驾驶模式", "Driving mode"),
                            fontSize = TypeScale.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Text.Primary,
                            maxLines = 1
                        )
                        Text(
                            tr("全屏车机 · 导航 / 音乐 / 语音", "Full-screen cockpit · nav / music / voice"),
                            fontSize = TypeScale.caption,
                            color = Text.Secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                        tint = Text.Muted, modifier = Modifier.size(18.dp)
                    )
                }

                // ---------- 组件 ----------
                SectionHeader(
                    title = tr("我的组件", "My widgets"),
                    action = if (editing) tr("完成", "Done") else tr("编辑", "Edit"),
                    onAction = { editing = !editing }
                )

                shown.chunked(2).forEachIndexed { rowIndex, rowItems ->
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEachIndexed { i, face ->
                            val index = rowIndex * 2 + i
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                WidgetCard(
                                    face = face,
                                    editing = editing,
                                    progress = p,
                                    index = index,
                                    onRemove = {
                                        val next = widgets - face.widget
                                        if (next.isEmpty()) {
                                            AppBanner.show(
                                                tr("至少留一个组件", "Keep at least one widget")
                                            )
                                        } else {
                                            widgets = next
                                            DashPrefs.save(ctx, next)
                                        }
                                    }
                                )
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                if (editing) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(Brand.Terracotta.copy(alpha = 0.07f))
                            .clickable { pickOpen = true }
                            .padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Add,
                                null,
                                tint = Brand.Terracotta,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                tr("添加组件", "Add widget"),
                                fontSize = 13.sp,
                                color = Brand.Terracotta,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // ---------- 通知消息 ----------
                SectionHeader(
                    title = tr("通知消息", "Notifications"),
                    action = tr("全部", "All"),
                    onAction = { leave { state.openScreen(ExtraScreen.NOTIFICATIONS) } },
                    badge = if (unread > 0) unread else null
                )
                DashCard {
                    val preview = state.notifications.take(3)
                    if (preview.isEmpty()) {
                        Text(
                            tr("还没有消息", "No messages yet"),
                            fontSize = 13.sp,
                            color = Text.Muted
                        )
                    } else {
                        preview.forEachIndexed { i, n ->
                            if (i > 0) {
                                Spacer(Modifier.height(10.dp))
                            }
                            NotificationRow(n)
                        }
                    }
                }

                // ---------- 最近行程 ----------
                SectionHeader(
                    title = tr("历史行程", "Past trips"),
                    action = tr("全部", "All"),
                    onAction = { leave { state.openScreen(ExtraScreen.MY_PLANS) } }
                )
                val plans = remember(state.tripPlans) {
                    state.tripPlans.filter { !it.archived }
                        .sortedByDescending { it.startDate }
                        .take(8)
                }
                if (plans.isEmpty()) {
                    DashCard {
                        Text(
                            tr("还没有行程计划，去行程页新建一段", "No trips yet"),
                            fontSize = 13.sp,
                            color = Text.Muted
                        )
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(plans, key = { it.id }) { plan ->
                            PlanMiniCard(plan) { planSheet = plan }
                        }
                    }
                }

                // ---------- 最近足迹 ----------
                SectionHeader(
                    title = tr("最近足迹", "Recent footprints"),
                    action = tr("全部", "All"),
                    onAction = {
                        leave {
                            state.activeTab = com.example.my.state.AppTab.MAP
                            state.footprintMode = 1
                        }
                    }
                )
                val recent = remember(state.footprints) {
                    state.footprints.sortedByDescending { it.dateMillis ?: 0L }.take(8)
                }
                if (recent.isEmpty()) {
                    DashCard {
                        Text(
                            tr("还没有足迹，点底部 ＋ 记下第一条", "No footprints yet"),
                            fontSize = 13.sp,
                            color = Text.Muted
                        )
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recent, key = { it.id }) { fp ->
                            FootprintMiniCard(fp) { footprintSheet = fp }
                        }
                    }
                }

                // ---------- 常访问的旅行人 ----------
                val travelers = remember(state.commonCompanions, state.guides) {
                    state.commonCompanions.ifEmpty {
                        state.guides.map { it.author }.distinct()
                    }.take(8)
                }
                SectionHeader(
                    title = tr("常访问的旅行人", "Frequent travelers"),
                    action = tr("全部", "All"),
                    onAction = { leave { state.openScreen(ExtraScreen.TRAVELER_RANK) } }
                )
                if (travelers.isEmpty()) {
                    DashCard {
                        Text(
                            tr("在行程里添加同行人后会出现在这里", "Add companions to see them here"),
                            fontSize = 13.sp,
                            color = Text.Muted
                        )
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(travelers) { name ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(66.dp)
                                    .clickable {
                                        leave { state.openScreen(ExtraScreen.TRAVELER_RANK) }
                                    }
                            ) {
                                Box(
                                    Modifier.size(50.dp).clip(CircleShape)
                                        .background(Brand.Terracotta.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        name.take(1),
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Brand.Terracotta
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    name,
                                    fontSize = 12.sp,
                                    color = Text.Secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(26.dp))
                Text(
                    tr("向左滑回到发现页", "Swipe left to go back"),
                    fontSize = 11.sp,
                    color = Text.Muted,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.navigationBarsPadding().height(8.dp))
            }
        }

        // ---------- 添加组件：底部卡片，不走 Dialog（要压在自定义背景之上、还要跟着负一屏一起退场） ----------
        if (pickOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.34f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { pickOpen = false }
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .navigationBarsPadding()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(Layer.Opaque)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("添加组件", "Add widget"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Text.Primary
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { pickOpen = false }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Outlined.Close,
                            tr("关闭", "Close"),
                            tint = Text.Muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (candidates.isEmpty()) {
                    Text(
                        tr("所有组件都添加了", "All widgets are added"),
                        fontSize = 13.sp,
                        color = Text.Muted
                    )
                } else {
                    candidates.forEach { w ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sm))
                                .clickable {
                                    val next = widgets + w
                                    widgets = next
                                    DashPrefs.save(ctx, next)
                                    pickOpen = false
                                    AppBanner.show(tr("已添加「${w.zh}」", "Added ${w.en}"))
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                                    .background(Brand.Terracotta.copy(alpha = 0.13f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    w.icon,
                                    null,
                                    tint = Brand.Terracotta,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                tr(w.zh, w.en),
                                fontSize = 14.sp,
                                color = Text.Primary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                tr("添加", "Add"),
                                fontSize = 12.sp,
                                color = Brand.Terracotta
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    // ---------- 行程 / 足迹详情：负一屏里直接开，不跳回主框架 ----------
    val plan = planSheet
    if (plan != null) {
        PlanDetailSheet(state = state, plan = plan, onDismiss = { planSheet = null })
    }
    val fp = footprintSheet
    if (fp != null) {
        FootprintDetailSheet(fp = fp, onDismiss = { footprintSheet = null }, state = state)
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    badge: Int? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Text.Primary)
        if (badge != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Func.Danger)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text("$badge", fontSize = 10.sp, color = Color.White)
            }
        }
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onAction() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(action, fontSize = 12.sp, color = Text.Secondary)
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    null,
                    tint = Text.Muted,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun DashCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(Radius.lg)
    Column(
        modifier
            .fillMaxWidth()
            .cardShadow(shape)
            .clip(shape)
            .background(Layer.Opaque)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        content = content
    )
}

@Composable
private fun WidgetCard(
    face: WidgetFace,
    editing: Boolean,
    progress: Float,
    index: Int,
    onRemove: () -> Unit
) {
    val shape = RoundedCornerShape(Radius.lg)
    Box(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .graphicsLayer {
                // 每张卡比上一张晚一点点到：滑入时是一排卡片依次追上，不是一整块平移
                translationX = (1f - progress) * (14 + index * 8).dp.toPx()
                alpha = (progress * 1.2f).coerceIn(0f, 1f)
            }
            .cardShadow(shape)
            .clip(shape)
            .background(Layer.Opaque)
            .clickable { if (!editing) face.onClick() }
            .padding(13.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(9.dp))
                        .background(Brand.Terracotta.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        face.widget.icon,
                        null,
                        tint = Brand.Terracotta,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    face.label,
                    fontSize = 11.sp,
                    color = Text.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (editing) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(20.dp).clip(CircleShape)
                            .background(Func.Danger.copy(alpha = 0.12f))
                            .clickable { onRemove() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            tr("移除组件", "Remove widget"),
                            tint = Func.Danger,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                face.value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = face.valueColor ?: Text.Primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (face.rows.isNotEmpty()) {
                // 行情卡：一行一个指数，涨红跌绿
                Spacer(Modifier.height(4.dp))
                face.rows.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            r.name,
                            fontSize = 11.sp,
                            color = Text.Secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            r.pct,
                            fontSize = 11.sp,
                            color = if (r.up) StockUp else StockDown,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(3.dp))
                Text(
                    face.hint,
                    fontSize = 11.sp,
                    color = Text.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .background(Brand.Terracotta.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                when (n.type) {
                    NotifType.LIKE -> Icons.Outlined.Star
                    NotifType.COMMENT -> Icons.AutoMirrored.Outlined.Assignment
                    NotifType.FOLLOW -> Icons.Outlined.Public
                    NotifType.SYSTEM -> Icons.Outlined.Eco
                },
                null,
                tint = Brand.Terracotta,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    n.title,
                    fontSize = 13.sp,
                    color = Text.Primary,
                    fontWeight = if (n.read) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!n.read) {
                    Spacer(Modifier.width(5.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Func.Danger))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                n.body,
                fontSize = 11.sp,
                color = Text.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(n.time, fontSize = 11.sp, color = Text.Muted)
    }
}

@Composable
private fun PlanMiniCard(plan: TripPlan, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.md)
    Column(
        Modifier
            .width(170.dp)
            .cardShadow(shape)
            .clip(shape)
            .background(Layer.Opaque)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Text(
            plan.destination.ifBlank { tr("未定目的地", "Undecided") },
            fontSize = 11.sp,
            color = Brand.Terracotta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            plan.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Text.Primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.CalendarMonth,
                null,
                tint = Text.Muted,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "${plan.startDate} · ${plan.days}${tr("天", "d")}",
                fontSize = 11.sp,
                color = Text.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FootprintMiniCard(fp: Footprint, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.md)
    Column(
        Modifier
            .width(132.dp)
            .cardShadow(shape)
            .clip(shape)
            .background(Layer.Opaque)
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(14.dp))) {
            val path = fp.photoPaths.firstOrNull()
            if (path != null) {
                PhotoThumb(path, Modifier.fillMaxSize())
            } else {
                Box(
                    Modifier.fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Brand.Terracotta.copy(alpha = 0.20f),
                                    Brand.Gold.copy(alpha = 0.16f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.LocationCity,
                        null,
                        tint = Brand.Terracotta.copy(alpha = 0.55f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            fp.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Text.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "${fp.city} · ${fp.date}",
            fontSize = 10.sp,
            color = Text.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PhotoThumb(path: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { decodeThumb(path, 360) }
    }
    Box(modifier.background(Bg.SurfaceVariant.copy(alpha = 1f))) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** 只解出卡片要用的尺寸：附件照片动辄几千万像素，整张解进内存会在滑动时掉帧 */
private fun decodeThumb(path: String, target: Int): ImageBitmap? {
    val file = File(path)
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > target || bounds.outHeight / sample > target) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return runCatching {
        BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
    }.getOrNull()
}
