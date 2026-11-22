package com.example.my.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.my.state.AppState
import com.example.my.state.ExtraScreen
import com.example.my.model.Guide
import com.example.my.model.Route
import com.example.my.model.ExposureContext
import com.example.my.model.Feedback
import com.example.my.model.FeedbackType
import kotlin.math.min
import com.example.my.data.CausalInference
import com.example.my.ui.components.AppTopBar
import com.example.my.ui.components.Avatar
import com.example.my.ui.components.aiModelLabel
import com.example.my.ui.components.EmptyState
import com.example.my.ui.components.InteractionHintBanner
import com.example.my.ui.components.PrimaryButton
import com.example.my.ui.components.ProvideShimmer
import com.example.my.ui.components.FilterChip
import com.example.my.ui.components.GradientCover
import com.example.my.ui.components.SkeletonShimmer
import com.example.my.ui.components.TagChip
import com.example.my.ui.components.WeatherIcon
import com.example.my.ui.components.rememberIsResumed
import com.example.my.ui.components.staggeredIn
import com.example.my.ui.interaction.AnimatedLabel
import com.example.my.ui.interaction.AnimatedNumber
import com.example.my.ui.interaction.BurstEffect
import com.example.my.ui.interaction.EdgeHintIndicator
import com.example.my.ui.interaction.StickyCategoryTabs
import com.example.my.ui.interaction.SunRefreshIndicator
import com.example.my.ui.interaction.WormPagerIndicator
import com.example.my.ui.interaction.pressBounceScale
import com.example.my.ui.interaction.rememberBurst
import com.example.my.ui.interaction.rememberEdgeDamping
import com.example.my.ui.interaction.rememberHapticFeedback
import com.example.my.ui.interaction.rememberTapRipple
import com.example.my.ui.interaction.tapRipple
import com.example.my.ui.theme.*
import com.example.my.util.SearchUtils
import com.example.my.data.search.SeaFusion
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.my.ui.i18n.tr
import com.example.my.ui.i18n.Lang
import com.example.my.data.AiApi
import com.example.my.data.AiFeature
import com.example.my.data.WebHit
import com.example.my.data.WebSearch
import com.example.my.ui.components.markdownToAnnotated
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.withTimeoutOrNull

/** 骨架屏占位数量：常量，避免每次重组新建 list */
private val SKELETON_TILES = listOf(0, 1, 2, 3, 4, 5)

/**
 * 首屏大标题「这次，想去哪儿？」是否已经打完。
 * 放在文件级而不是 remember：切 Tab 再回来不能每次重新打字，只有冷启动才演一遍。
 */
private var heroTypedOnce = false

private val themeOptions = listOf("全部", "城市漫游", "美食", "自然风光", "人文历史")
private val regionOptions = listOf("全部", "西南", "华东", "华北", "西北", "东北", "华南", "华中")
private val daysOptions = listOf("全部", "1天", "2天", "3天", "4天以上")

/** 游玩天数分桶：1天/2天/3天/4天以上 */
private fun matchDaysBucket(days: Int, bucket: String): Boolean = when (bucket) {
    "1天" -> days == 1
    "2天" -> days == 2
    "3天" -> days == 3
    "4天以上" -> days >= 4
    else -> true
}

/** 发现页（首页）：搜索 + 城市筛选 + 轮播 Banner + 类型入口 + 双列瀑布流攻略 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(state: AppState, onOpenMenu: (() -> Unit)? = null) {
    var query by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf<String?>(null) }
    var region by remember { mutableStateOf<String?>(null) }
    var daysFilter by remember { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 发现页「AI 总结搜索答案」：结果按关键词缓存，网格 item 滚出视口再回来也不丢
    var answerQuery by remember { mutableStateOf("") }
    var answerUi by remember { mutableStateOf(WebAnswerUi()) }
    var showDiscoverModelPicker by remember { mutableStateOf(false) }

    /** 检索 → 清洗 → 事实裁决 → 模型按事实卡归纳；回答落定后再做一次数字/引用校验 */
    fun runSearchAnswer(q: String) {
        answerQuery = q
        answerUi = WebAnswerUi(phase = 1)
        scope.launch {
            val deck = withTimeoutOrNull(20_000L) {
                SeaFusion.buildWithPages(q, state.aiFeatureEndpoint(AiFeature.DISCOVER))
            }
            if (deck == null || deck.evidence.isEmpty()) {
                answerUi = WebAnswerUi(
                    phase = 4,
                    message = tr(
                        "没搜到可用的网页信息，可能是网络受限、关键词太生僻，或结果质量不够",
                        "No usable results found. Check your connection or try another keyword"
                    )
                )
                return@launch
            }
            val hits = SeaFusion.sources(deck)
            answerUi = WebAnswerUi(phase = 2, hits = hits)
            val prompt = if (Lang.isEn) WEB_ANSWER_PROMPT_EN else WEB_ANSWER_PROMPT_ZH
            val facts = SeaFusion.block(deck).orEmpty()
            val reply = withTimeoutOrNull(35_000L) {
                AiApi.requestOnce(state.aiFeatureEndpoint(AiFeature.DISCOVER), prompt, facts)
            }
            answerUi = if (reply.isNullOrBlank()) {
                WebAnswerUi(
                    phase = 4,
                    hits = hits,
                    message = tr(
                        "AI 这次没返回答案，可以直接看下面的网页结果",
                        "The model returned no answer. Open the web results below instead"
                    )
                )
            } else {
                val note = SeaFusion.auditAnswer(reply, deck).note()
                WebAnswerUi(
                    phase = 3,
                    hits = hits,
                    answer = reply.trim() + if (note.isBlank()) "" else "\n\n$note"
                )
            }
        }
    }

    // 首次进入请求定位权限（用于顶部实时天气）
    // 精确 + 大致一起要：只申请 COARSE 等于只能拿基站定位，城市会定到隔壁县
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        state.refreshWeather(force = true, relocate = granted, precise = granted)
    }

    LaunchedEffect(Unit) {
        // 定位权限不首屏硬弹：已授权才按定位刷新；未授权用默认城市兜底，
        // 用户下拉刷新（主动语境）时再请求权限
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) state.refreshWeather(relocate = true)
    }

    // 每 30 分钟自动同步一次天气（10 分钟过于频繁，降频省流量省电）。
    // 只在页面处于前台时循环：退到后台就取消等待，避免后台整点唤醒联网。
    val weatherResumed = rememberIsResumed()
    LaunchedEffect(weatherResumed) {
        if (!weatherResumed) return@LaunchedEffect
        while (true) {
            delay(30 * 60 * 1000L)
            state.refreshWeather()
        }
    }

    // 回到前台时刷新（内部有 60s 节流）
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.refreshWeather()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val banners = remember(state.guides) { state.guides.filter { it.featured } }
    // 城市热度榜：攻略数×1000 + 浏览量/100 + 点赞，取 Top10
    val cityHot = remember(state.guides) {
        state.guides.groupBy { it.city }
            .map { (city, list) ->
                val heat = list.size * 1000 + list.sumOf { it.viewedCount } / 100 + list.sumOf { it.likes }
                city to heat
            }
            .sortedByDescending { it.second }
            .take(10)
    }
    // 景点热度榜：遍历所有攻略行程，按景点名统计出现频次，取 Top10
    val spotHot = remember(state.guides) {
        val map = linkedMapOf<String, Pair<String, Int>>()
        state.guides.forEach { g ->
            g.itinerary.forEach { day ->
                day.spots.forEach { spot ->
                    val cur = map[spot.name]
                    map[spot.name] = if (cur == null) g.city to 1 else g.city to (cur.second + 1)
                }
            }
        }
        map.entries.map { Triple(it.key, it.value.first, it.value.second) }
            .sortedByDescending { it.third }
            .take(10)
    }
    // 个性化排序：点赞/收藏过的攻略的 tags+type 词频作为兴趣向量，
    // 主攻略卡列表按「命中兴趣标签数×2 + 同城加分」降序；零命中（新用户）保持原顺序
    val personalized = remember(state.likedIds, state.favoredIds, state.guides) {
        val engaged = state.guides.filter { it.id in state.likedIds || it.id in state.favoredIds }
        val raw = if (engaged.isEmpty()) {
            state.guides
        } else {
            val interest = engaged.flatMap { it.tags + it.type }.groupingBy { it }.eachCount()
            val engagedCities = engaged.mapTo(HashSet()) { it.city }
            val homeCity = state.weatherCity
            // sortedByDescending 为稳定排序，同分项保持原有先后顺序
            state.guides.sortedByDescending { g ->
                (g.tags + g.type).sumOf { interest[it] ?: 0 } * 2 +
                        if (g.city in engagedCities || g.city == homeCity) 1 else 0
            }
        }
        // 因果重排序：去除曝光偏差、流行度偏差，按真实兴趣排序
        val userActivity = min(state.footprints.size.toFloat() / 100f, 1f) // 最多100个足迹算活跃满分
        CausalInference.reRank(
            items = raw,
            getContext = { guide, pos ->
                ExposureContext(
                    position = pos,
                    popularity = min(0f, 1f), // 暂时用0，后续加viewCount字段
                    userActivity = userActivity,
                    itemCategory = "guide",
                    userTags = guide.tags
                )
            },
            getFeedback = { guide ->
                when {
                    state.likedIds.contains(guide.id) -> 0.7f // 点赞次之
                    else -> 0f
                }
            }
        )
    }
    // 搜索：原文包含 + 拼音全拼/首字母 + 编辑距离容错，命中项按相关性降序
    val filtered = remember(query, theme, region, daysFilter, personalized) {
        val q = query.trim()
        val t = theme
        val r = region
        val d = daysFilter
        val byTheme = personalized.filter {
            (t == null || it.theme == t) &&
                    (r == null || it.region == r) &&
                    (d == null || matchDaysBucket(it.days, d))
        }
        if (q.isEmpty()) {
            byTheme
        } else {
            byTheme
                .map { g ->
                    val haystack = buildString {
                        append(g.title).append(' ')
                        append(g.city).append(' ')
                        append(g.author).append(' ')
                        append(g.type).append(' ')
                        g.tags.forEach { append(it).append(' ') }
                        append(g.intro)
                    }
                    g to SearchUtils.score(q, haystack)
                }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .map { it.first }
        }
    }
    // 旅人排行榜：按途经城市数取 Top3（数据真实来自路线记录）
    val ranking = remember(state.routes) {
        state.routes.sortedByDescending { it.cities.size }.take(3)
    }
    // 上一次查看的攻略：发现页快捷入口
    val lastGuide = remember(state.lastViewedGuideId, state.guides) {
        state.lastViewedGuideId?.let { id -> state.guides.firstOrNull { it.id == id } }
    }

    // 首屏骨架（模拟网络加载）
    LaunchedEffect(Unit) {
        delay(650)
        loading = false
    }

    // 吸顶分类：网格滚动状态 + 分类行在网格中的下标（标题栏 + Hero + 上次攻略? + 搜索栏）
    val gridState = rememberLazyStaggeredGridState()
    val chipsIndex = 3 + (if (lastGuide != null) 1 else 0)
    val showStickyTabs by remember {
        derivedStateOf { !loading && gridState.firstVisibleItemIndex > chipsIndex }
    }
    val refreshState = rememberPullToRefreshState()
    val selectedThemeIndex = themeOptions.indexOf(theme).coerceAtLeast(0)

    // ⑦ 边界阻尼：瀑布流滑到头继续拉阻力增大 + 到头提示
    val gridEdge = rememberEdgeDamping(
        isAtTop = { gridState.firstVisibleItemIndex == 0 },
        isAtBottom = {
            gridState.layoutInfo.totalItemsCount > 0 &&
                gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ==
                gridState.layoutInfo.totalItemsCount - 1
        }
    )

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                val granted = ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    state.refreshWeather(force = true, relocate = true, precise = true)
                } else {
                    // 下拉刷新是主动语境：此时请求定位权限，授权回调中会按定位刷新
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
                delay(900)
                refreshing = false
                state.toast(tr("已是最新攻略"))
            }
        },
        state = refreshState,
        indicator = {
            // 下拉时太阳从地平线升起，松手回弹刷新
            SunRefreshIndicator(
                state = refreshState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
    Box(Modifier.fillMaxSize().nestedScroll(gridEdge.connection)) {
    // 骨架屏：所有 shimmer 块共用一个动画进度，避免 N 个无限动画同时跑
    ProvideShimmer {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp
        ) {
            if (loading) {
                // ---------- 骨架屏：尺寸与真实内容一一对应，加载完成不跳动 ----------
                // 纯文字标题栏骨架（居中标尺条）
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SkeletonShimmer(Modifier.size(width = 84.dp, height = 20.dp), RoundedCornerShape(10.dp))
                    }
                }
                item(span = StaggeredGridItemSpan.FullLine) {
                    Column(Modifier.staggeredIn(0)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SkeletonShimmer(Modifier.size(40.dp), CircleShape)
                            Spacer(Modifier.width(10.dp))
                            SkeletonShimmer(Modifier.size(width = 88.dp, height = 14.dp), RoundedCornerShape(10.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        SkeletonShimmer(Modifier.size(width = 214.dp, height = 44.dp), RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(16.dp))
                        SkeletonShimmer(Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(28.dp))
                    }
                }
                // 继续上次的旅程（真实卡片：12dp 外间距 + 80dp 卡片）
                item(span = StaggeredGridItemSpan.FullLine) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        SkeletonShimmer(Modifier.fillMaxWidth().height(80.dp), RoundedCornerShape(24.dp))
                    }
                }
                // 搜索栏
                item(span = StaggeredGridItemSpan.FullLine) {
                    SkeletonShimmer(
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(32.dp)
                    )
                }
                // 主题分类
                item(span = StaggeredGridItemSpan.FullLine) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(4) {
                            SkeletonShimmer(Modifier.size(width = 72.dp, height = 36.dp), RoundedCornerShape(50))
                        }
                    }
                }
                // 轮播 Banner（8dp 外间距 + 200dp 高度）
                item(span = StaggeredGridItemSpan.FullLine) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        SkeletonShimmer(Modifier.fillMaxWidth().height(200.dp), RoundedCornerShape(24.dp))
                    }
                }
                // 旅人排行榜（16dp 外间距 + 80dp 卡片）
                item(span = StaggeredGridItemSpan.FullLine) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        SkeletonShimmer(Modifier.fillMaxWidth().height(80.dp), RoundedCornerShape(24.dp))
                    }
                }
                // 推荐标题
                item(span = StaggeredGridItemSpan.FullLine) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        SkeletonShimmer(Modifier.size(width = 150.dp, height = 22.dp), RoundedCornerShape(10.dp))
                        Spacer(Modifier.height(8.dp))
                    }
                }
                // 双列卡片：与 GuideCard 封面 + 两行标题 + 数据行等高
                itemsIndexed(SKELETON_TILES) { index, _ ->
                    SkeletonShimmer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (index % 2 == 0) 244.dp else 204.dp),
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            } else {
                // 纯文字标题栏：标题居中，交互按钮都在下方内容区
                item(span = StaggeredGridItemSpan.FullLine) {
                    DiscoverTopBar(state, onOpenMenu)
                }
                // 新交互一次性引导（下拉刷新 / 边界阻尼回弹）
                item(span = StaggeredGridItemSpan.FullLine) {
                    InteractionHintBanner(
                        text = tr("试试下拉刷新攻略与天气，列表滑到底再拉会有回弹提示"),
                        prefsKey = "hint_discover_edge",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                // 顶部定位 + 大标题 + 出行计划输入框
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(Modifier.staggeredIn(0)) { HeroHeader(state = state) }
                }
                // 上一次查看的攻略
                lastGuide?.let { guide ->
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Box(Modifier.staggeredIn(1)) { LastGuideCard(guide = guide, onClick = { state.openGuide(guide.id) }) }
                    }
                }
                // 搜索栏
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(Modifier.staggeredIn(2)) {
                        SearchBar(
                            query = query,
                            onQueryChange = { query = it },
                            onAddClick = { state.openScreen(ExtraScreen.GUIDE_CREATE) }
                        )
                    }
                }
                // 筛选入口按钮（常驻一行，点击展开三行条件）
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(Modifier.staggeredIn(3)) {
                        val activeCount = listOfNotNull(theme, region, daysFilter).size
                        val activeSummary = if (activeCount == 0) tr("全部攻略")
                            else listOfNotNull(theme, region, daysFilter).joinToString(" · ")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Bg.Surface)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showFilters = !showFilters
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = tr("筛选"),
                                tint = if (activeCount > 0) Brand.Terracotta else Text.Muted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = tr("筛选"),
                                color = if (activeCount > 0) Brand.Terracotta else Text.Muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = activeSummary,
                                color = Text.Muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (showFilters) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                tint = Text.Muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                // 展开区：三行筛选条件（主题 / 地区 / 天数）
                item(span = StaggeredGridItemSpan.FullLine) {
                    AnimatedVisibility(
                        visible = showFilters,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                items(themeOptions, key = { it }) { t ->
                                    FilterChip(
                                        text = tr(t),
                                        selected = if (t == "全部") theme == null else theme == t,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            theme = if (t == "全部") null
                                            else if (theme == t) null else t
                                        },
                                        height = 36.dp,
                                        fontSize = 13
                                    )
                                }
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                items(regionOptions, key = { it }) { r ->
                                    FilterChip(
                                        text = tr(r),
                                        selected = if (r == "全部") region == null else region == r,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            region = if (r == "全部") null
                                            else if (region == r) null else r
                                        },
                                        height = 36.dp,
                                        fontSize = 13
                                    )
                                }
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                items(daysOptions, key = { it }) { d ->
                                    FilterChip(
                                        text = tr(d),
                                        selected = if (d == "全部") daysFilter == null else daysFilter == d,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            daysFilter = if (d == "全部") null
                                            else if (daysFilter == d) null else d
                                        },
                                        height = 36.dp,
                                        fontSize = 13
                                    )
                                }
                            }
                        }
                    }
                }
                // 轮播 Banner + 热度榜（搜索时隐藏）
                if (query.trim().isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(Modifier.staggeredIn(4)) {
                    BannerPager(
                        guides = banners,
                        onOpen = { state.openGuide(it.id) },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    }
                }
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(Modifier.staggeredIn(5).padding(horizontal = 16.dp)) {
                        HotRankCard(
                            title = tr("城市热度榜"),
                            entries = cityHot.mapIndexed { i, (city, heat) ->
                                RankEntry(rank = i + 1, name = city, sub = formatHeat(heat))
                            },
                            accent = Brand.Terracotta
                        )
                        Spacer(Modifier.height(10.dp))
                        HotRankCard(
                            title = tr("景点热度榜"),
                            entries = spotHot.mapIndexed { i, (name, city, count) ->
                                RankEntry(rank = i + 1, name = name, sub = "$city · $count 篇攻略收录")
                            },
                            accent = Brand.Gold
                        )
                    }
                }
                }
                // 为你推荐标题
                item(span = StaggeredGridItemSpan.FullLine) {
                    Row(
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).staggeredIn(6),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(18.dp)
                                .background(Brush.verticalGradient(MainGradient))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = tr("为你推荐 · "),
                            style = Type.sectionTitle,
                            color = TextMain
                        )
                        // ③ 文字局部过渡：切主题只动画变化的部分（当前主题名）
                        AnimatedLabel(
                            text = tr(theme ?: tr("全部")),
                            color = TextMain,
                            fontSize = 18,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(4.dp))
                        // 数字滚动更新
                        AnimatedNumber(
                            value = filtered.size,
                            color = TextMain,
                            fontSize = 18,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                // 网页搜索 + AI 总结答案：输入关键词后，在结果顶部直接给出一段可溯源的回答
                if (query.trim().isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        WebAnswerCard(
                            query = query.trim(),
                            ui = if (answerQuery == query.trim()) answerUi else WebAnswerUi(),
                            modelLabel = aiModelLabel(state, AiFeature.DISCOVER),
                            onModelLongPress = { showDiscoverModelPicker = true },
                            onStart = { runSearchAnswer(it) },
                            onReset = { answerQuery = ""; answerUi = WebAnswerUi() },
                            onOpenSource = { url -> state.openUrl(url) },
                            onOpenBing = { state.openWebSearch(query.trim()) }
                        )
                    }
                }
                // 双列瀑布流
                if (filtered.isEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        // 空态引导：给强 CTA，而不是只提示"没找到"
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.Search, null,
                                tint = TextSub,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(tr("没有找到相关攻略"), color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(tr("换个关键词，或直接看看全部攻略"), color = TextSub, fontSize = 13.sp)
                            Spacer(Modifier.height(18.dp))
                            PrimaryButton(
                                text = tr("查看全部攻略"),
                                modifier = Modifier.padding(horizontal = 32.dp),
                                onClick = {
                                    query = ""
                                    theme = null
                                    region = null
                                    daysFilter = null
                                }
                            )
                        }
                    }
                } else {
                    itemsIndexed(filtered, key = { _, g -> g.id }) { index, g ->
                        // 列表项不加入场动画：item 滚出视口后会被回收，
                        // 滚回来重播淡入会造成滚动掉帧与闪烁
                        GuideCard(g = g, tall = index % 2 == 0, onClick = { state.openGuide(g.id) })
                    }
                }
            }
        }
        }
        // ⑦ 到头提示：到底/到顶后短暂浮现自动淡出
        EdgeHintIndicator(
            gridEdge,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        )
        // 吸顶分类标签：分类行滚出视口后吸在顶部，指示条跟随选中，点击平滑回滚
        AnimatedVisibility(
            visible = showStickyTabs,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 吸顶条是盖在列表上的覆盖层：自定义背景下 Bg.Background 是透明的，
                    // copy(alpha=0.96f) 会变成一条 96% 黑，必须用不透明实心底
                    .background(Layer.Opaque)
                    .statusBarsPadding()
            ) {
                StickyCategoryTabs(
                    options = themeOptions,
                    selectedIndex = selectedThemeIndex,
                    onSelect = { i ->
                        theme = if (i == 0) null else themeOptions[i]
                        // 点标签平滑回滚到分类行
                        scope.launch { gridState.animateScrollToItem(chipsIndex) }
                    }
                )
            }
        }
    }
    }

    if (showDiscoverModelPicker) {
        AiModelQuickSwitchDialog(
            feature = AiFeature.DISCOVER,
            state = state,
            onDismiss = { showDiscoverModelPicker = false }
        )
    }
}

/**
 * 顶栏/首屏的「黑白底」胶囊。
 *
 * 底用不透明的 [FixedInk.ChipSurface]，文字用 [FixedInk.OnChip]，
 * 字体显式指定系统无衬线 —— 这三样都不读「自定义背景」里的字体与文字色，
 * 所以压在照片背景上也始终看得清，不会被设置带跑。
 */
private fun Modifier.barChip(): Modifier = this
    .clip(RoundedCornerShape(50))
    .background(FixedInk.ChipSurface)

/** 顶栏胶囊内的固定字体：绕开自定义字体，保证字形稳定 */
private val ChipFont = FontFamily.SansSerif

/** 纯文字标题栏：标题「发现」居中，右侧音符+消息入口 */
@Composable
private fun DiscoverTopBar(state: AppState, onOpenMenu: (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth()) {
        // 标题由 AppTopBar 统一渲染成居中黑白底胶囊（全 App 同一套观感）
        AppTopBar(
            title = tr("发现", "Discover"),
            insets = false,
            rightContent = { DiscoverTopBarActions(state, chip = true) }
        )
        // 左侧：功能抽屉圆钮 + 资讯入口。标题仍保持居中，两个入口都做成小胶囊，
        // 避免和右侧音乐/消息挤在一起。
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onOpenMenu != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Bg.Surface)
                        .clickable { onOpenMenu() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = tr("负一屏", "Dashboard"),
                        tint = Text.Primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Row(
                modifier = Modifier
                    .barChip()
                    .clickable { state.openScreen(ExtraScreen.NEWS) }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Article,
                    contentDescription = tr("资讯", "News"),
                    tint = Brand.Terracotta,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = tr("资讯", "News"),
                    color = FixedInk.OnChip,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ChipFont
                )
            }
        }
    }
}

/**
 * 顶栏右侧：旋转音符（音乐入口）+ 消息（带未读红点）。
 *
 * @param chip 发现页传 true：两个图标包进一块黑白底胶囊，和「资讯」同一套观感，
 *             并且图标颜色固定，不再跟随自定义背景的文字色。地图页仍用原样（false）。
 */
@Composable
fun DiscoverTopBarActions(state: AppState, chip: Boolean = false) {
    val tint = if (chip) FixedInk.OnChip else Text.Primary
    val noteAngle = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(com.example.my.data.music.MusicPlayer.isPlaying) {
        if (com.example.my.data.music.MusicPlayer.isPlaying) {
            noteAngle.animateTo(
                noteAngle.value + 360f,
                androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.tween(3200, easing = androidx.compose.animation.core.LinearEasing)
                )
            )
        }
    }
    Row(
        modifier = if (chip) Modifier.barChip().padding(horizontal = 9.dp, vertical = 5.dp) else Modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (chip) 12.dp else 14.dp)
    ) {
        Icon(
            Icons.Outlined.MusicNote,
            contentDescription = tr("音乐", "Music"),
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .rotate(noteAngle.value)
                .clickable { state.openScreen(ExtraScreen.MUSIC) }
        )
        Box {
            Icon(
                Icons.Outlined.Chat,
                contentDescription = "消息",
                tint = tint,
                modifier = Modifier
                    .size(22.dp)
                    .clickable { state.openScreen(ExtraScreen.MESSAGES) }
            )
            if (state.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 1.dp, end = 1.dp)
                        .size(8.dp)
                        .background(Color(0xFFE53935), shape = androidx.compose.foundation.shape.CircleShape)
                )
            }
        }
    }
}

/** 顶部定位 + 大标题 + 出行计划输入框（参考图 Hero） */
@Composable
private fun HeroHeader(state: AppState) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        // 顶部栏：定位 + 天气 + 星星按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .barChip()
                    .clickable { state.openScreen(ExtraScreen.WEATHER) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Outlined.LocationOn,
                    null,
                    tint = FixedInk.OnChip,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = state.weatherCity,
                    color = FixedInk.OnChip,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ChipFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    null,
                    tint = FixedInk.OnChip,
                    modifier = Modifier.size(16.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .barChip()
                    .clickable { state.openScreen(ExtraScreen.WEATHER) }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                val now = state.weatherNow
                WeatherIcon(now?.emoji ?: "多云", Modifier.size(16.dp), FixedInk.OnChip)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = when {
                        now != null -> "${now.temp}° · ${now.desc}"
                        state.weatherLoading -> tr("-- · 获取中")
                        state.weatherError != null -> tr("-- · 点击重试")
                        else -> tr("-- · 待同步")
                    },
                    color = FixedInk.OnChip,
                    fontSize = 13.sp,
                    style = TextStyle(fontFeatureSettings = "tnum", fontFamily = ChipFont),
                    maxLines = 1,
                    modifier = Modifier.clickable {
                        if (now == null) state.refreshWeather(force = true)
                        else state.openScreen(ExtraScreen.WEATHER)
                    }
                )
                Spacer(Modifier.width(16.dp))
                // 主页右上角消息入口已按用户要求取消：天气行结束，不放置消息按钮
            }
        }
        Spacer(Modifier.height(16.dp))
        // 彩色标题（M3 Expressive）：逐个字打出来，像有人在屏幕上敲字
        val heroStart = tr("这次，")
        val heroAccent = tr("想去哪儿？")
        val heroLen = heroStart.length + heroAccent.length
        var typed by remember { mutableIntStateOf(if (heroTypedOnce) heroLen else 0) }
        // 点标题区重打一遍：冷启动自动演一遍，之后点哪里都能再看一次
        var replayToken by remember { mutableIntStateOf(0) }
        LaunchedEffect(heroStart, heroAccent, replayToken) {
            if (typed >= heroLen) return@LaunchedEffect
            while (typed < heroLen) {
                delay(110)
                typed += 1
            }
            heroTypedOnce = true
        }
        // 彩色渐变标题（M3 Expressive）：整句共用一条横向渐变，蓝 → 紫 → 玫红，
        // 逐字打出时颜色沿着句子流动，不再拆成「深色 + 单色强调」两段
        val heroBrush = Brush.linearGradient(
            listOf(Color(0xFF2E6BE6), Color(0xFF7B61FF), Color(0xFFE9518C))
        )
        Text(
            buildAnnotatedString {
                if (typed <= heroStart.length) {
                    append(heroStart.take(typed))
                } else {
                    append(heroStart)
                    append(heroAccent.take(typed - heroStart.length))
                }
            },
            style = Type.heroTitle.copy(brush = heroBrush),
            color = Color.Unspecified,
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                typed = 0
                replayToken++
            }
        )
        Spacer(Modifier.height(16.dp))
        // 出行计划输入框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(CardBg)
                .cardShadow(MaterialTheme.shapes.extraLarge)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tr("说说你的出行计划，生成行程表"),
                color = TextWeak,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            val arrowInteractionSource = remember { MutableInteractionSource() }
            val arrowRipple = rememberTapRipple()
            val arrowScale = pressBounceScale(arrowInteractionSource, 0.9f)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .scale(arrowScale)
                    .clip(CircleShape)
                    .background(Coral)
                    .tapRipple(arrowRipple) {
                        state.openScreen(ExtraScreen.AI_ASSISTANT)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ArrowForward, tr("去AI助手"), tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** 上一次查看的攻略（发现页快捷入口） */
@Composable
private fun LastGuideCard(guide: com.example.my.model.Guide, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val ripple = rememberTapRipple()
    val scale = pressBounceScale(interactionSource, 0.97f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .scale(scale)
            .clip(MaterialTheme.shapes.large)
            .background(CardBg)
            .cardShadow()
            .tapRipple(ripple, color = Coral.copy(alpha = 0.10f), onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(LightOrange),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = LightOrange, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tr("继续上次查看的攻略"),
                color = LightOrange,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = guide.title,
                color = TextMain,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = guide.city.ifBlank { tr("未分类") },
                color = TextSub,
                fontSize = 12.sp
            )
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = TextWeak, modifier = Modifier.size(20.dp))
    }
}

/** 旅人排行榜（参考图板块）：按途经城市数取 Top3 真实路线 */
@Composable
private fun RankingSection(ranking: List<Route>, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val ripple = rememberTapRipple()
    val scale = pressBounceScale(interactionSource, 0.97f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .scale(scale)
            .clip(MaterialTheme.shapes.large)
            .background(CardBg)
            .cardShadow()
            .tapRipple(ripple, color = Coral.copy(alpha = 0.10f), onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(OfficialGold1.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = LightOrange, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tr("旅人排行榜"),
                color = TextMain,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = tr("看看旅行达人们都走过多少路"),
                color = TextSub,
                fontSize = 12.sp
            )
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = TextWeak, modifier = Modifier.size(20.dp))
    }
}

private data class RankEntry(val rank: Int, val name: String, val sub: String)

private fun formatHeat(heat: Int): String = when {
    heat >= 10000 -> "${heat / 10000}.${(heat % 10000) / 1000}万"
    heat >= 1000 -> "${heat / 1000}.${(heat % 1000) / 100}k"
    else -> "$heat"
}

/** 热度榜卡片：城市/景点通用，紧凑排名列表 */
@Composable
private fun HotRankCard(
    title: String,
    entries: List<RankEntry>,
    accent: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Bg.Surface)
            .cardShadow()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .background(Brush.verticalGradient(listOf(accent, Brand.TerracottaLight)))
            )
            Spacer(Modifier.width(8.dp))
            Text(title, color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        entries.take(5).forEach { e ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 排名数字
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (e.rank <= 3) accent else Bg.Background
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${e.rank}",
                        color = if (e.rank <= 3) Color.White else Text.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = e.name,
                    color = Text.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(e.sub, color = Text.Muted, fontSize = 11.sp)
            }
        }
    }
}

/** 发现页 AI 搜索答案的状态：0 待总结 / 1 检索中 / 2 归纳中 / 3 完成 / 4 失败 */
private data class WebAnswerUi(
    val phase: Int = 0,
    val answer: String = "",
    val hits: List<WebHit> = emptyList(),
    val message: String = ""
)

private val WEB_ANSWER_PROMPT_ZH = """
你是旅行搜索助手。系统会给你一张「证据事实卡」，请据此回答用户的搜索词。
每个关键事实后面标注事实卡引用表里的编号，例如 [2]，方便用户对照下方来源列表。
事实卡里的价格、时刻、评分等数据优先采用；卡里没有又确实需要具体数字时，如实说明是通用参考、可能变动，不要当成已核实的实时数据。
""".trimIndent()

private val WEB_ANSWER_PROMPT_EN = """
You are a travel search assistant. You are given an evidence fact sheet. Answer the user's query based on it.
Cite the index from the sheet's reference list, such as [2], after each key fact.
Prefer concrete figures from the sheet; if a specific number is needed but absent, state plainly that it is a general reference that may change rather than presenting it as verified real-time data.
""".trimIndent()

/**
 * 发现页搜索框的「AI 总结搜索答案」卡片。
 * 点一下：Bing 实时检索 → 结果作为上下文交给当前选中的模型 → 一段带来源编号的归纳答案。
 * 来源逐条可点（App 内 WebView 直接打开原网页），并保留「查看完整网页结果」入口。
 */
@Composable
private fun WebAnswerCard(
    query: String,
    ui: WebAnswerUi,
    modelLabel: String,
    onModelLongPress: () -> Unit,
    onStart: (String) -> Unit,
    onReset: () -> Unit,
    onOpenSource: (String) -> Unit,
    onOpenBing: () -> Unit
) {
    var showAllSources by remember(query) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, Brand.Terracotta.copy(alpha = 0.20f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brand.Terracotta.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Language, contentDescription = null, tint = Brand.Terracotta, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tr("AI 搜索答案", "AI search answer"),
                    color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("Bing 实时检索 + AI 归纳", "Live Bing results, summarized by AI"),
                        color = TextSub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        tr("模型：$modelLabel", "Model: $modelLabel"),
                        color = TextSub, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 118.dp)
                            .clip(RoundedCornerShape(50))
                            .pointerInput(modelLabel) {
                                detectTapGestures(onLongPress = { onModelLongPress() })
                            }
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            tr("「%s」", "\"%s\"").replace("%s", query),
            color = TextSub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))

        when (ui.phase) {
            // 待总结：一个主按钮 + 原有的完整网页结果入口
            0 -> Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(Brush.linearGradient(MainGradient))
                        .clickable { onStart(query) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tr("✨ 总结搜索答案", "Summarize web results"),
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    tr("查看网页结果 ›", "All web results ›"),
                    color = Brand.Terracotta, fontSize = 12.sp,
                    modifier = Modifier.clickable { onOpenBing() }.padding(4.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            // 检索 / 归纳
            1, 2 -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Brand.Terracotta
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (ui.phase == 1)
                        tr("正在检索 Bing 网页…", "Searching Bing…")
                    else
                        tr("找到 ${ui.hits.size} 条结果，AI 正在归纳答案…", "Found ${ui.hits.size} results, summarizing…"),
                    color = TextSub, fontSize = 12.sp
                )
            }

            // 答案 + 可点来源
            3 -> {
                Text(
                    text = markdownToAnnotated(ui.answer),
                    color = TextMain, fontSize = 13.sp, lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                if (ui.hits.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        tr("引用来源", "Sources"),
                        color = TextSub, fontSize = 11.sp, fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    val shown = if (showAllSources) ui.hits else ui.hits.take(3)
                    shown.forEachIndexed { i, h ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = h.url.startsWith("http")) { onOpenSource(h.url) }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("[${i + 1}]", color = Brand.Terracotta, fontSize = 10.sp)
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    h.title, color = TextMain, fontSize = 11.5.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                if (h.site.isNotBlank()) {
                                    Text(h.site, color = TextWeak, fontSize = 10.sp, maxLines = 1)
                                }
                            }
                            Text("›", color = TextSub, fontSize = 14.sp)
                        }
                    }
                    if (ui.hits.size > 3) {
                        Text(
                            if (showAllSources)
                                tr("收起来源 ▲", "Show less ▲")
                            else
                                tr("展开全部 ${ui.hits.size} 条来源 ▾", "Show all ${ui.hits.size} sources ▾"),
                            color = Brand.Terracotta, fontSize = 11.sp,
                            modifier = Modifier
                                .clickable { showAllSources = !showAllSources }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("重新总结", "Summarize again"),
                        color = Brand.Terracotta, fontSize = 12.sp,
                        modifier = Modifier.clickable { onReset(); onStart(query) }.padding(4.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        tr("查看完整网页结果 ›", "Open full Bing results ›"),
                        color = TextSub, fontSize = 12.sp,
                        modifier = Modifier.clickable { onOpenBing() }.padding(4.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 失败：不编造，明确给原因 + 重试
            else -> {
                Text(ui.message, color = TextSub, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("重试", "Retry"),
                        color = Brand.Terracotta, fontSize = 12.sp,
                        modifier = Modifier.clickable { onReset(); onStart(query) }.padding(4.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        tr("直接看网页结果 ›", "Open web results ›"),
                        color = TextSub, fontSize = 12.sp,
                        modifier = Modifier.clickable { onOpenBing() }.padding(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onAddClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(CardBg)
                .cardShadow(MaterialTheme.shapes.extraLarge)
                .padding(horizontal = 13.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, tr("搜索"), tint = TextSub, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(color = TextMain, fontSize = 13.sp),
                    cursorBrush = SolidColor(Coral),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    tr("搜索目的地、攻略、作者或拼音"),
                                    color = TextWeak,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                            inner()
                        }
                    }
                )
            }
        }
        val addInteractionSource = remember { MutableInteractionSource() }
        val addRipple = rememberTapRipple()
        val addScale = pressBounceScale(addInteractionSource, 0.9f)
        Box(
            modifier = Modifier
                .size(36.dp)
                .scale(addScale)
                .clip(CircleShape)
                .background(Brush.linearGradient(MainGradient))
                .tapRipple(addRipple, onClick = onAddClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, tr("新建攻略"), tint = Color.White, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun BannerPager(guides: List<Guide>, onOpen: (Guide) -> Unit, modifier: Modifier = Modifier) {
    if (guides.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { guides.size })
    // 轮播只在页面可见时跑：退到后台 / 锁屏后不再每 3.5s 唤醒 CPU 驱动滚动动画
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(pagerState, resumed) {
        if (!resumed) return@LaunchedEffect
        while (true) {
            delay(3500)
            if (pagerState.pageCount > 1 && !pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % pagerState.pageCount)
            }
        }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(MaterialTheme.shapes.large)
                .cardShadowMedium()
        ) { page ->
            val g = guides[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onOpen(g) }
            ) {
                GradientCover(
                    seed = g.coverSeed,
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.large,
                    coverResId = g.coverResId
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0x99000000))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row {
                        if (g.featured) {
                            TagChip(
                                text = tr("☆ 官方"),
                                background = Color.White.copy(alpha = 0.9f),
                                textColor = OfficialGold2,
                                fontSize = 12
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        TagChip(
                            text = tr("${g.days}天", "${g.days} days"),
                            background = Color.White.copy(alpha = 0.9f),
                            textColor = TextMain,
                            fontSize = 12
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = g.bannerTitle.ifEmpty { g.title },
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 28.sp
                    )
                    Text(
                        text = tr("${g.city} · ${formatCount(g.viewedCount)}人看过", "${g.city} · ${formatCount(g.viewedCount)} views"),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
        }
        // 轮播指示点：当前项拉长成短条，随滑动进度在相邻两点间连续过渡
        WormPagerIndicator(
            pagerState = pagerState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
        )
    }
}



@Composable
private fun GuideCard(g: Guide, tall: Boolean, onClick: () -> Unit) {
    var isFavorited by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val press = pressBounceScale(interactionSource, 0.97f)
    val ripple = rememberTapRipple()
    val starRipple = rememberTapRipple()
    val burst = rememberBurst()
    val haptic = rememberHapticFeedback()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(press)
            .clip(MaterialTheme.shapes.large)
            .background(CardBg)
            .cardShadow()
            .tapRipple(ripple, Coral.copy(alpha = 0.06f), onClick = onClick)
            .padding(0.dp)
    ) {
        // 封面
        Box(modifier = Modifier.fillMaxWidth()) {
            GradientCover(
                seed = g.coverSeed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (tall) 176.dp else 132.dp),
                shape = MaterialTheme.shapes.large,
                coverResId = g.coverResId
            )
            // 右上角收藏按钮
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                BurstEffect(
                    state = burst,
                    modifier = Modifier.fillMaxSize(),
                    colors = listOf(FavYellow, OfficialGold2, Coral),
                    reach = 26.dp
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f))
                        .tapRipple(starRipple, FavYellow.copy(alpha = 0.40f)) {
                            if (!isFavorited) burst.fire()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isFavorited = !isFavorited
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Star, null,
                        tint = if (isFavorited) FavYellow else TextSub,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (g.featured) {
                TagChip(
                    text = tr("☆ 官方"),
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    background = Color.White.copy(alpha = 0.9f),
                    textColor = OfficialGold2
                )
            }
            TagChip(
                text = tr("${g.days}天", "${g.days} days"),
                modifier = Modifier.align(Alignment.TopStart).padding(
                    top = 12.dp,
                    start = if (g.featured) 70.dp else 12.dp
                ),
                background = Color.White.copy(alpha = 0.9f),
                textColor = TextMain
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
            Text(
                text = g.title,
                color = TextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
                style = TextStyle(lineBreak = LineBreak.Paragraph)
            )
            Spacer(Modifier.height(4.dp))
            Text(text = tr("${g.city} · ${formatCount(g.viewedCount)}人看过", "${g.city} · ${formatCount(g.viewedCount)} views"), color = TextSub, fontSize = 12.sp)
        }
    }
}

private fun formatCount(n: Int): String = when {
    n >= 100000000 -> tr("%.1f亿", "%.0fM").format(if (Lang.isEn) n / 1000000.0 else n / 100000000.0)
    n >= 10000 -> tr("%.1f万", "%.1fK").format(if (Lang.isEn) n / 1000.0 else n / 10000.0)
    else -> n.toString()
}
