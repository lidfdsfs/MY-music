package com.example.my.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.news.HistoryDigest
import com.example.my.data.news.HistoryEvent
import com.example.my.data.news.HotBoard
import com.example.my.data.news.HotItem
import com.example.my.data.news.NewsApi
import com.example.my.data.news.NewsBrief
import com.example.my.data.stock.MarketQuote
import com.example.my.data.stock.StockApi
import com.example.my.state.AppState
import com.example.my.state.ExtraScreen
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.FixedInk
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 参考稿里的正红；只用于资讯页，不改变 App 其他页面的赤陶色。 */
private val NewsRed = Color(0xFFC2261B)
private val NewsArtInk = Color(0xFF2B2823)
private val NewsArtPaper = Color(0xFFF1EDE5)

private enum class NewsTab(val zh: String, val en: String) {
    BRIEF("今日早报", "Morning brief"),
    HOT("热榜", "Trending"),
    HISTORY("历史上的今天", "On this day")
}

private enum class SearchKind { BRIEF, HOT, HISTORY }

private data class SearchHit(
    val kind: SearchKind,
    val title: String,
    val meta: String,
    val url: String = ""
)

/** 本地阅读目标：有 URL 直接抓原文；没有 URL 时用标题检索后抓最匹配的一篇。 */
private data class NewsReaderTarget(val url: String, val title: String)

/**
 * 资讯页：编辑部头版 + 三条索引 + 速览条 + 热点雷达 + 今日主线 + 历史时间卡。
 *
 * 功能仍然绑定原有数据：
 * - 今日要闻 / 头条速览 / 今日主线 → 早报详情或原网页；
 * - 热点雷达 → 平台切换、换一批、热榜详情，点条目直接打开来源；
 * - 历史时间卡 → 历史详情，点卡片打开资料；
 * - 顶部搜索框在当前已加载的早报、热榜、历史里即时过滤，空结果可直接联网搜索。
 */
@Composable
fun NewsScreen(state: AppState) {
    var detail by remember { mutableStateOf<NewsTab?>(null) }
    var reader by remember { mutableStateOf<NewsReaderTarget?>(null) }
    var query by remember { mutableStateOf("") }
    var brief by remember { mutableStateOf<NewsBrief?>(null) }
    var history by remember { mutableStateOf<HistoryDigest?>(null) }
    var hotBoards by remember { mutableStateOf<List<HotBoard>>(emptyList()) }
    var selectedHot by remember { mutableStateOf("weibo") }

    var briefLoading by remember { mutableStateOf(true) }
    var historyLoading by remember { mutableStateOf(true) }
    var hotLoading by remember { mutableStateOf(true) }
    var briefError by remember { mutableStateOf<String?>(null) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var hotError by remember { mutableStateOf<String?>(null) }
    var lastUpdated by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    fun markUpdated() {
        lastUpdated = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date())
    }

    suspend fun loadBrief(force: Boolean) {
        briefLoading = true
        briefError = null
        runCatching { withContext(Dispatchers.IO) { NewsApi.brief(force) } }
            .onSuccess {
                brief = it
                markUpdated()
            }
            .onFailure { briefError = it.message ?: "早报加载失败" }
        briefLoading = false
    }

    suspend fun loadHistory(force: Boolean) {
        historyLoading = true
        historyError = null
        runCatching { withContext(Dispatchers.IO) { NewsApi.history(force) } }
            .onSuccess {
                history = it
                markUpdated()
            }
            .onFailure { historyError = it.message ?: "历史今天加载失败" }
        historyLoading = false
    }

    suspend fun loadHot(force: Boolean) {
        hotLoading = true
        hotError = null
        runCatching { withContext(Dispatchers.IO) { NewsApi.hotAll(force) } }
            .onSuccess {
                hotBoards = it
                if (it.isNotEmpty() && it.none { board -> board.id == selectedHot }) {
                    selectedHot = it.first().id
                }
                markUpdated()
            }
            .onFailure { hotError = it.message ?: "热榜加载失败" }
        hotLoading = false
    }

    fun refreshAll() {
        scope.launch { loadBrief(force = true) }
        scope.launch { loadHistory(force = true) }
        scope.launch { loadHot(force = true) }
    }

    LaunchedEffect(Unit) {
        launch { loadBrief(force = false) }
        launch { loadHistory(force = false) }
        launch { loadHot(force = false) }
    }

    val activeBoard = hotBoards.firstOrNull { it.id == selectedHot } ?: hotBoards.firstOrNull()
    val activeBoardId = activeBoard?.id ?: selectedHot
    val tickerItems = remember(hotBoards) {
        hotBoards.flatMap { board -> board.items.take(2) }.take(12)
    }
    val hits = remember(query, brief, hotBoards, history) {
        buildSearchHits(query, brief, hotBoards, history)
    }

    fun openArticle(url: String, title: String) {
        reader = NewsReaderTarget(url.trim(), title.trim())
    }

    fun openHit(hit: SearchHit) {
        openArticle(hit.url, hit.title)
    }

    // 返回层级：文章页 → 资讯详情 → 资讯首页 → 发现页。
    // 不能再落到主页面的「双击退出」，否则资讯页永远退不出去。
    BackHandler(enabled = true) {
        when {
            reader != null -> reader = null
            detail != null -> detail = null
            else -> state.closeScreen()
        }
    }

    val currentDetail = detail
    val currentReader = reader
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg.Background)
                .statusBarsPadding()
        ) {
        if (currentDetail == null) {
            NewsHeader(
                query = query,
                onQueryChange = { query = it },
                onBack = { state.closeScreen() },
                onNotifications = { state.openScreen(ExtraScreen.NOTIFICATIONS) },
                onRefresh = { refreshAll() }
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 30.dp)
            ) {
                if (query.isNotBlank()) {
                    item {
                        SearchResults(
                            query = query,
                            hits = hits,
                            onOpen = ::openHit,
                            onWebSearch = { state.openWebSearch(it) }
                        )
                    }
                    item { SourceNote() }
                } else {
                    item {
                        DailyMasthead(
                            brief = brief,
                            loading = briefLoading,
                            error = briefError,
                            lastUpdated = lastUpdated,
                            onOpenBrief = { detail = NewsTab.BRIEF },
                            onRetry = { scope.launch { loadBrief(force = true) } }
                        )
                    }
                    item {
                        NewsIndex(
                            brief = brief,
                            boards = hotBoards,
                            history = history,
                            onBrief = { detail = NewsTab.BRIEF },
                            onHot = { detail = NewsTab.HOT },
                            onHistory = { detail = NewsTab.HISTORY }
                        )
                    }
                    item {
                        MarketStrip(onOpen = { state.openScreen(ExtraScreen.STOCK) })
                    }
                    if (tickerItems.isNotEmpty()) {
                        item {
                            HeadlineTicker(
                                items = tickerItems,
                                onOpen = { item ->
                                    openArticle(item.url, item.title)
                                }
                            )
                        }
                    }
                    item {
                        HotPreview(
                            board = activeBoard,
                            boards = hotBoards,
                            selectedHot = activeBoardId,
                            loading = hotLoading,
                            error = hotError,
                            onSelectHot = { selectedHot = it },
                            onNextBoard = {
                                if (hotBoards.isNotEmpty()) {
                                    val current = hotBoards.indexOfFirst { it.id == activeBoardId }
                                    val next = if (current >= 0) current + 1 else 1
                                    selectedHot = hotBoards[next.mod(hotBoards.size)].id
                                }
                            },
                            onOpenAll = { detail = NewsTab.HOT },
                            onRetry = { scope.launch { loadHot(force = true) } },
                            onOpenItem = { item ->
                                openArticle(item.url, item.title)
                            }
                        )
                    }
                    item {
                        WorldCard(
                            brief = brief,
                            boards = hotBoards,
                            history = history,
                            onOpen = { detail = NewsTab.BRIEF }
                        )
                    }
                    item {
                        HistoryPreview(
                            digest = history,
                            loading = historyLoading,
                            error = historyError,
                            onOpenAll = { detail = NewsTab.HISTORY },
                            onRetry = { scope.launch { loadHistory(force = true) } },
                            onOpenEvent = { event ->
                                openArticle(event.link, "${event.year} ${event.title}")
                            }
                        )
                    }
                    item { SourceNote() }
                }
            }
        } else {
            NewsDetail(
                tab = currentDetail,
                brief = brief,
                history = history,
                boards = hotBoards,
                selectedHot = selectedHot,
                briefLoading = briefLoading,
                historyLoading = historyLoading,
                hotLoading = hotLoading,
                briefError = briefError,
                historyError = historyError,
                hotError = hotError,
                onBack = { detail = null },
                onRefresh = { refreshAll() },
                onSelectHot = { selectedHot = it },
                onOpenBrief = { openArticle("", it) },
                onOpenHot = { item ->
                    openArticle(item.url, item.title)
                },
                onOpenHistory = { event ->
                    openArticle(event.link, "${event.year} ${event.title}")
                },
                onRetryBrief = { scope.launch { loadBrief(force = true) } },
                onRetryHistory = { scope.launch { loadHistory(force = true) } },
                onRetryHot = { scope.launch { loadHot(force = true) } }
            )
        }
    }

        if (currentReader != null) {
            NewsArticleScreen(
                state = state,
                url = currentReader.url,
                titleHint = currentReader.title,
                onBack = { reader = null }
            )
        }
    }
}

// ---------- 顶部 ----------

@Composable
private fun NewsHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onNotifications: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(start = 8.dp, end = 10.dp),
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
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = tr("返回发现页", "Back to discover"),
                tint = Text.Primary,
                modifier = Modifier.size(21.dp)
            )
        }
        // 资讯标题：不透明胶囊 + 固定墨色 + 固定无衬线字体。
        // 这一页是自绘顶栏（没走 AppTopBar），所以胶囊要单独补一次；
        // 否则「自定义背景」一开，标题会跟着设置里的文字色走，压在照片上就糊了。
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(FixedInk.ChipSurface)
                .padding(horizontal = 13.dp, vertical = 2.dp)
        ) {
            Text(
                text = tr("资讯", "News"),
                color = FixedInk.OnChip,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .background(Bg.Surface)
                .border(1.dp, Bg.Outline, RoundedCornerShape(Radius.pill))
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = Text.Muted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Text.Primary,
                    fontSize = 13.sp
                ),
                cursorBrush = SolidColor(NewsRed),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (query.isBlank()) {
                            Text(
                                text = tr("搜索热点、话题、事件", "Search topics and events"),
                                color = Text.Muted,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                }
            )
            if (query.isNotBlank()) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = tr("清空搜索", "Clear search"),
                    tint = Text.Muted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onQueryChange("") }
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onRefresh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = tr("刷新资讯", "Refresh news"),
                tint = Text.Primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onNotifications),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = tr("消息通知", "Notifications"),
                tint = Text.Primary,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
private fun NewsDetailBar(tab: NewsTab, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(horizontal = 10.dp),
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
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = tr("返回资讯", "Back to news"),
                tint = Text.Primary,
                modifier = Modifier.size(21.dp)
            )
        }
        Text(
            text = tr(tab.zh, tab.en),
            color = Text.Primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onRefresh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = tr("刷新", "Refresh"),
                tint = Text.Secondary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

// ---------- 首页卡片 ----------

@Composable
private fun DailyMasthead(
    brief: NewsBrief?,
    loading: Boolean,
    error: String?,
    lastUpdated: String,
    onOpenBrief: () -> Unit,
    onRetry: () -> Unit
) {
    val headlines = brief?.items.orEmpty()
    val lead = headlines.firstOrNull()
    val date = friendlyDate(brief?.date ?: "")
    val stamp = listOfNotNull(
        date.takeIf { it.isNotBlank() },
        lastUpdated.takeIf { it.isNotBlank() }?.let { "$it 更新" }
    ).joinToString(" · ")

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(226.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .border(1.dp, Bg.Outline, RoundedCornerShape(Radius.lg))
            .clickable(onClick = onOpenBrief)
    ) {
        WorldNetworkArt(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(132.dp),
            accent = NewsRed,
            ink = NewsArtInk,
            paper = NewsArtPaper,
            withNewspaper = true
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.74f)
                .padding(start = 17.dp, top = 15.dp, bottom = 13.dp, end = 2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "DAILY BRIEF",
                    color = NewsRed,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .width(18.dp)
                        .height(1.dp)
                        .background(NewsRed.copy(alpha = 0.55f))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stamp.ifBlank { tr("今日 · 实时", "Today · live") },
                    color = Text.Muted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                text = lead ?: if (loading) tr("正在更新今日要闻", "Updating today's brief")
                else tr("今日要闻", "Today's brief"),
                color = Text.Primary,
                fontSize = if (lead == null) 25.sp else 23.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp,
                lineHeight = if (lead == null) 30.sp else 29.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(7.dp))
            if (headlines.size > 1) {
                headlines.drop(1).take(2).forEachIndexed { index, line ->
                    Row(
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "0${index + 2}",
                            color = NewsRed,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(19.dp)
                        )
                        Text(
                            text = line,
                            color = Text.Secondary,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else if (error != null && brief == null) {
                Text(
                    text = tr("$error · 点此重试", "$error · tap to retry"),
                    color = NewsRed,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    modifier = Modifier.clickable(onClick = onRetry)
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(NewsRed)
                        .clickable(onClick = onOpenBrief)
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (headlines.isEmpty()) tr("查看早报", "Read brief")
                        else tr("读全部 ${headlines.size} 条", "All ${headlines.size} briefs"),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (loading) {
                    Spacer(Modifier.width(10.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        color = NewsRed,
                        strokeWidth = 1.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun NewsIndex(
    brief: NewsBrief?,
    boards: List<HotBoard>,
    history: HistoryDigest?,
    onBrief: () -> Unit,
    onHot: () -> Unit,
    onHistory: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NewsIndexCard(
            number = "01",
            title = tr("今日早报", "Morning brief"),
            meta = brief?.items?.size?.let { tr("$it 条", "$it items") }
                ?: tr("每日更新", "Daily"),
            onClick = onBrief,
            modifier = Modifier.weight(1f)
        )
        NewsIndexCard(
            number = "02",
            title = tr("实时热榜", "Trending"),
            meta = boards.size.takeIf { it > 0 }?.let { tr("$it 个平台", "$it boards") }
                ?: tr("多平台", "Multi-source"),
            onClick = onHot,
            modifier = Modifier.weight(1f)
        )
        NewsIndexCard(
            number = "03",
            title = tr("历史今天", "On this day"),
            meta = history?.items?.size?.let { tr("$it 条", "$it events") }
                ?: tr("同日不同年", "Same day"),
            onClick = onHistory,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NewsIndexCard(
    number: String,
    title: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(86.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(Bg.Surface)
            .border(1.dp, Bg.Outline, RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = number,
                color = NewsRed,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .width(10.dp)
                    .height(1.dp)
                    .background(NewsRed.copy(alpha = 0.5f))
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            color = Text.Primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = (-0.3).sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = meta,
            color = Text.Muted,
            fontSize = 9.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HeadlineTicker(items: List<HotItem>, onOpen: (HotItem) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(NewsArtPaper.copy(alpha = 0.55f))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tr("速览", "WIRE"),
            color = NewsRed,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.width(34.dp)
        )
        LazyRow(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items) { item ->
                Row(
                    modifier = Modifier.clickable { onOpen(item) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(3.dp)
                            .background(NewsRed, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = item.title,
                        color = Text.Secondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HotPreview(
    board: HotBoard?,
    boards: List<HotBoard>,
    selectedHot: String,
    loading: Boolean,
    error: String?,
    onSelectHot: (String) -> Unit,
    onNextBoard: () -> Unit,
    onOpenAll: () -> Unit,
    onRetry: () -> Unit,
    onOpenItem: (HotItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            // 区块标题同样脱钩设置：黑白底胶囊 + 固定墨色。
            // 这一段是浮在自定义背景照片上的，跟着设置里的文字色走会直接淹掉。
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(FixedInk.ChipSurface)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Column {
                    Text(
                        text = tr("热点雷达", "Trending radar"),
                        color = FixedInk.OnChip,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tr(
                            "${boards.size} 个平台 · 5 分钟缓存",
                            "${boards.size} boards · 5-min cache"
                        ),
                        color = FixedInk.OnChip.copy(alpha = 0.62f),
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (boards.isNotEmpty()) {
            Text(
                text = tr("换一批", "Next"),
                color = NewsRed,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .clickable(onClick = onNextBoard)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenAll),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = tr("查看完整榜单", "Full board"),
                tint = Text.Muted,
                modifier = Modifier.size(17.dp)
            )
        }
    }
    if (boards.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(boards, key = { it.id }) { item ->
                HotPlatformChip(
                    text = item.name,
                    selected = item.id == selectedHot,
                    onClick = { onSelectHot(item.id) }
                )
            }
        }
    }
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .border(1.dp, Bg.Outline, RoundedCornerShape(Radius.lg))
            .padding(vertical = 2.dp)
    ) {
        val items = board?.items?.take(6).orEmpty()
        if (items.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 13.dp, end = 13.dp, top = 9.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = board?.name ?: "",
                    color = Text.Primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = board?.updatedAt?.let { friendlyClock(it) }?.takeIf { it.isNotBlank() }
                        ?.let { tr("$it 更新", "updated $it") }
                        ?: tr("实时", "Live"),
                    color = Text.Muted,
                    fontSize = 9.sp
                )
            }
        }
        if (loading && items.isEmpty()) {
            HotPlaceholder()
        } else if (error != null && items.isEmpty()) {
            Column(Modifier.padding(12.dp)) {
                Text(error, color = Text.Muted, fontSize = 12.sp)
                TextButton(onClick = onRetry) {
                    Text(tr("重试", "Retry"), color = NewsRed, fontSize = 12.sp)
                }
            }
        } else if (items.isEmpty()) {
            Text(
                text = tr("暂时没有热榜内容", "No trending items"),
                color = Text.Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            val maxHot = items.maxOfOrNull { it.hot ?: 0L } ?: 0L
            items.chunked(2).forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { item ->
                        HotRadarCell(
                            item = item,
                            maxHot = maxHot,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenItem(item) }
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                if (rowIndex != items.chunked(2).lastIndex) {
                    Box(
                        Modifier
                            .padding(horizontal = 12.dp)
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Bg.Outline)
                    )
                }
            }
        }
        if (items.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAll)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("查看完整榜单", "Full board"),
                    color = Text.Muted,
                    fontSize = 10.5.sp
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = Text.Muted,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun HotRadarCell(
    item: HotItem,
    maxHot: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.rank.toString().padStart(2, '0'),
                color = NewsRed,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(20.dp)
            )
            Text(
                text = item.title,
                color = Text.Primary,
                fontSize = 11.8.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Bg.Outline)
            ) {
                val fraction = if (maxHot > 0L && item.hot != null) {
                    (item.hot.toFloat() / maxHot.toFloat()).coerceIn(0.08f, 1f)
                } else {
                    0f
                }
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(2.dp)
                        .background(NewsRed.copy(alpha = 0.62f))
                )
            }
            if (item.hot != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = formatHotShort(item.hot),
                    color = Text.Muted,
                    fontSize = 8.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HotPlaceholder() {
    Column(Modifier.padding(vertical = 2.dp)) {
        repeat(3) {
            Row(Modifier.fillMaxWidth()) {
                repeat(2) {
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Bg.SurfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun WorldCard(
    brief: NewsBrief?,
    boards: List<HotBoard>,
    history: HistoryDigest?,
    onOpen: () -> Unit
) {
    val hotTopics = boards.firstOrNull()?.items?.take(3)?.map { it.title }.orEmpty()
    val briefTopics = brief?.items?.drop(1)?.take(3).orEmpty()
    val topics = hotTopics.ifEmpty { briefTopics }

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 13.dp)
            .fillMaxWidth()
            .height(196.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .border(1.dp, Bg.Outline, RoundedCornerShape(Radius.lg))
            .clickable(onClick = onOpen)
    ) {
        WorldNetworkArt(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(150.dp),
            accent = NewsRed,
            ink = NewsArtInk,
            paper = NewsArtPaper,
            withNewspaper = false
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.70f)
                .padding(start = 16.dp, top = 14.dp, bottom = 13.dp, end = 2.dp)
        ) {
            Text(
                text = "CONTEXT / ${tr("看懂世界", "WORLD")}",
                color = NewsRed,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = tr("今天正在发生的 3 条主线", "Three threads shaping today"),
                color = Text.Primary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 24.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(7.dp))
            if (topics.isEmpty()) {
                Text(
                    text = tr("热榜更新后显示今日主线", "Threads appear when trends load"),
                    color = Text.Muted,
                    fontSize = 10.5.sp
                )
            } else {
                topics.forEachIndexed { index, line ->
                    Row(
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "0${index + 1}",
                            color = NewsRed,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(18.dp)
                        )
                        Text(
                            text = line,
                            color = Text.Secondary,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tr(
                        "${brief?.items?.size ?: "--"} 要闻 · ${boards.size} 平台 · ${history?.items?.size ?: "--"} 历史",
                        "${brief?.items?.size ?: "--"} briefs · ${boards.size} boards · ${history?.items?.size ?: "--"} events"
                    ),
                    color = Text.Muted,
                    fontSize = 9.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = tr("读早报", "Brief"),
                    color = NewsRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = NewsRed,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun HistoryPreview(
    digest: HistoryDigest?,
    loading: Boolean,
    error: String?,
    onOpenAll: () -> Unit,
    onRetry: () -> Unit,
    onOpenEvent: (HistoryEvent) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Text(
                text = tr("历史上的今天", "On this day"),
                color = Text.Primary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = listOfNotNull(
                    digest?.date?.let { friendlyDate(it) },
                    digest?.items?.size?.let { tr("$it 条", "$it events") }
                ).joinToString(" · ").ifBlank { tr("同日不同年", "Same day, other years") },
                color = Text.Muted,
                fontSize = 9.5.sp
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .clickable(onClick = onOpenAll)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tr("全部", "All"),
                color = NewsRed,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = NewsRed,
                modifier = Modifier.size(12.dp)
            )
        }
    }
    when {
        loading && digest == null -> HistorySkeletonRow()
        error != null && digest == null -> ErrorCard(error, onRetry = onRetry)
        digest == null || digest.items.isEmpty() -> {
            EmptyCard(tr("今天没有历史事件内容", "No history items today"))
        }
        else -> LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(digest.items.take(8), key = { "${it.year}-${it.title}" }) { event ->
                HistoryTimelineCard(
                    event = event,
                    index = digest.items.indexOf(event),
                    onClick = { onOpenEvent(event) }
                )
            }
        }
    }
}

@Composable
private fun HistoryTimelineCard(event: HistoryEvent, index: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(154.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(Bg.SurfaceVariant)
        ) {
            HistoryArt(modifier = Modifier.fillMaxSize(), index = index)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xFF171512).copy(alpha = 0.62f))
                        )
                    )
            )
            Text(
                text = event.year.ifBlank { "—" },
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(9.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(Color(0xFF171512).copy(alpha = 0.46f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            )
            if (event.type.isNotBlank()) {
                Text(
                    text = event.type,
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 8.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 9.dp, vertical = 8.dp),
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = event.title,
            color = Text.Primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (event.description.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = event.description,
                color = Text.Muted,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HistorySkeletonRow() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(3) { index ->
            Column(Modifier.width(154.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(Bg.SurfaceVariant)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Bg.SurfaceVariant)
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.72f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Bg.SurfaceVariant)
                )
            }
        }
    }
}

// ---------- 搜索 ----------

private fun buildSearchHits(
    query: String,
    brief: NewsBrief?,
    boards: List<HotBoard>,
    history: HistoryDigest?
): List<SearchHit> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val hits = mutableListOf<SearchHit>()
    brief?.items?.forEach { line ->
        if (line.contains(q, ignoreCase = true)) {
            hits += SearchHit(SearchKind.BRIEF, line, tr("早报", "Brief"))
        }
    }
    boards.forEach { board ->
        board.items.forEach { item ->
            if (item.title.contains(q, ignoreCase = true)) {
                hits += SearchHit(
                    kind = SearchKind.HOT,
                    title = item.title,
                    meta = "${board.name} · ${item.rank}",
                    url = item.url
                )
            }
        }
    }
    history?.items?.forEach { event ->
        if (event.title.contains(q, ignoreCase = true) ||
            event.description.contains(q, ignoreCase = true)
        ) {
            hits += SearchHit(
                kind = SearchKind.HISTORY,
                title = event.title,
                meta = "${tr("历史", "History")} · ${event.year}",
                url = event.link
            )
        }
    }
    return hits.take(12)
}

@Composable
private fun SearchResults(
    query: String,
    hits: List<SearchHit>,
    onOpen: (SearchHit) -> Unit,
    onWebSearch: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .border(1.dp, Bg.Outline, RoundedCornerShape(Radius.lg))
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tr("搜索", "Search"),
                color = NewsRed,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "“$query”",
                color = Text.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = tr("${hits.size} 条", "${hits.size} results"),
                color = Text.Muted,
                fontSize = 10.5.sp
            )
        }
        if (hits.isEmpty()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
                Text(
                    text = tr("本地缓存里没有匹配，可直接联网搜索", "No local match. Search the web instead."),
                    color = Text.Muted,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(NewsRed)
                        .clickable { onWebSearch(query) }
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = tr("网页搜索“$query”", "Search the web for \"$query\""),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            hits.forEachIndexed { index, hit ->
                if (index > 0) {
                    Box(
                        Modifier
                            .padding(horizontal = 14.dp)
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Bg.Outline)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(hit) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = hit.meta,
                        color = NewsRed,
                        fontSize = 10.sp,
                        modifier = Modifier.width(58.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = hit.title,
                        color = Text.Primary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Box(
                Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Bg.Outline)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onWebSearch(query) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("没有想找的？联网搜索", "Not here? Search the web"),
                    color = Text.Muted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = tr("去搜索", "Search"),
                    color = NewsRed,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = NewsRed,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

// ---------- 详情页 ----------

@Composable
private fun NewsDetail(
    tab: NewsTab,
    brief: NewsBrief?,
    history: HistoryDigest?,
    boards: List<HotBoard>,
    selectedHot: String,
    briefLoading: Boolean,
    historyLoading: Boolean,
    hotLoading: Boolean,
    briefError: String?,
    historyError: String?,
    hotError: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectHot: (String) -> Unit,
    onOpenBrief: (String) -> Unit,
    onOpenHot: (HotItem) -> Unit,
    onOpenHistory: (HistoryEvent) -> Unit,
    onRetryBrief: () -> Unit,
    onRetryHistory: () -> Unit,
    onRetryHot: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        NewsDetailBar(tab = tab, onBack = onBack, onRefresh = onRefresh)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            val activeBoard = boards.firstOrNull { it.id == selectedHot } ?: boards.firstOrNull()
            when (tab) {
                NewsTab.BRIEF -> briefSection(
                    brief = brief,
                    loading = briefLoading,
                    error = briefError,
                    onRetry = onRetryBrief,
                    onOpen = onOpenBrief
                )
                NewsTab.HOT -> hotSection(
                    boards = boards,
                    active = activeBoard,
                    selected = selectedHot,
                    loading = hotLoading,
                    error = hotError,
                    onSelect = onSelectHot,
                    onRetry = onRetryHot,
                    onOpen = onOpenHot
                )
                NewsTab.HISTORY -> historySection(
                    digest = history,
                    loading = historyLoading,
                    error = historyError,
                    onRetry = onRetryHistory,
                    onOpen = onOpenHistory
                )
            }
            item { SourceNote() }
        }
    }
}

private fun LazyListScope.briefSection(
    brief: NewsBrief?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit
) {
    item {
        SectionTitle(
            title = tr("60 秒读懂世界", "60-second world"),
            subtitle = brief?.date?.let { friendlyDate(it) } ?: tr("每天一更", "Daily")
        )
    }
    when {
        loading && brief == null -> item { LoadingCard(tr("正在获取早报…", "Loading briefs…")) }
        error != null && brief == null -> item { ErrorCard(error, onRetry = onRetry) }
        brief == null || brief.items.isEmpty() -> item {
            EmptyCard(tr("今天还没有早报内容", "No briefs yet today"))
        }
        else -> {
            item {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(Bg.Surface)
                ) {
                    brief.items.forEachIndexed { index, line ->
                        BriefRow(index = index, text = line, onClick = { onOpen(line) })
                        if (index != brief.items.lastIndex) {
                            Box(
                                Modifier
                                    .padding(start = 58.dp, end = 16.dp)
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(Bg.Outline)
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = tr(
                        "点击任意一条，在 App 内阅读正文与图片",
                        "Tap a line to read the full article and images in the app"
                    ),
                    color = Text.Muted,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun BriefRow(index: Int, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(NewsRed.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                color = NewsRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            color = Text.Primary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun LazyListScope.hotSection(
    boards: List<HotBoard>,
    active: HotBoard?,
    selected: String,
    loading: Boolean,
    error: String?,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    onOpen: (HotItem) -> Unit
) {
    item {
        SectionTitle(
            title = tr("实时热榜", "Trending now"),
            subtitle = active?.updatedAt?.let { friendlyClock(it) }?.takeIf { it.isNotBlank() }
                ?.let { tr("$it 更新", "updated $it") }
                ?: tr("5 分钟缓存", "5-min cache")
        )
    }
    if (loading && boards.isEmpty()) {
        item { LoadingCard(tr("正在获取热榜…", "Loading trends…")) }
        return
    }
    if (error != null && boards.isEmpty()) {
        item { ErrorCard(error, onRetry = onRetry) }
        return
    }
    if (boards.isEmpty()) {
        item { EmptyCard(tr("暂时没有热榜数据", "No trending data")) }
        return
    }
    item {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(boards, key = { it.id }) { board ->
                HotPlatformChip(
                    text = board.name,
                    selected = board.id == selected,
                    onClick = { onSelect(board.id) }
                )
            }
        }
    }
    item {
        ContainerCard {
            if (active == null || active.items.isEmpty()) {
                EmptyCard(tr("这个榜单暂时没有内容", "This board is empty"), inCard = true)
            } else {
                active.items.forEachIndexed { index, item ->
                    HotRow(item = item, onClick = { onOpen(item) })
                    if (index != active.items.lastIndex) {
                        Box(
                            Modifier
                                .padding(start = 52.dp, end = 16.dp)
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(Bg.Outline)
                        )
                    }
                }
            }
        }
    }
    item {
        Text(
            text = tr(
                "榜单来自公开接口，热度值仅代表平台口径",
                "Boards come from public APIs; heat values follow each platform's own metric"
            ),
            color = Text.Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun HotPlatformChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (selected) NewsRed else Bg.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Text.Secondary,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun HotRow(item: HotItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(
                    if (item.rank <= 3) NewsRed.copy(alpha = 0.12f)
                    else Bg.SurfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.rank.toString(),
                color = if (item.rank <= 3) NewsRed else Text.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = Text.Primary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.hot != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = formatHot(item.hot),
                    color = Text.Muted,
                    fontSize = 10.5.sp
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = Text.Muted,
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun LazyListScope.historySection(
    digest: HistoryDigest?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpen: (HistoryEvent) -> Unit
) {
    item {
        SectionTitle(
            title = tr("历史上的今天", "On this day"),
            subtitle = digest?.date?.let { friendlyDate(it) } ?: tr("同日不同年", "Same day, other years")
        )
    }
    when {
        loading && digest == null -> item { LoadingCard(tr("正在翻阅历史…", "Loading history…")) }
        error != null && digest == null -> item { ErrorCard(error, onRetry = onRetry) }
        digest == null || digest.items.isEmpty() -> item {
            EmptyCard(tr("今天没有历史事件内容", "No history items today"))
        }
        else -> {
            item {
                ContainerCard {
                    digest.items.forEachIndexed { index, event ->
                        HistoryRow(
                            event = event,
                            isLast = index == digest.items.lastIndex,
                            onClick = { onOpen(event) }
                        )
                    }
                }
            }
            item {
                Text(
                    text = tr(
                        "历史资料来自公开百科数据，仅供了解",
                        "History data comes from public encyclopedia sources"
                    ),
                    color = Text.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(event: HistoryEvent, isLast: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.width(46.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = event.year.ifBlank { "—" },
                color = NewsRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (!isLast) {
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .width(1.dp)
                        .height(38.dp)
                        .background(Bg.Outline)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = event.title,
                color = Text.Primary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp
            )
            if (event.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = event.description,
                    color = Text.Secondary,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (event.link.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tr("查看资料", "Read more"),
                        color = NewsRed,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = NewsRed,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

// ---------- 插画 ----------

@Composable
private fun WorldNetworkArt(
    modifier: Modifier,
    accent: Color,
    ink: Color,
    paper: Color,
    withNewspaper: Boolean
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (withNewspaper) {
            drawRect(
                color = paper,
                topLeft = Offset(w * 0.16f, -h * 0.18f),
                size = Size(w * 0.95f, h * 1.35f)
            )
            repeat(5) { index ->
                val x = w * (0.25f + index * 0.14f)
                drawLine(
                    color = ink.copy(alpha = 0.08f),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1.dp.toPx()
                )
            }
            repeat(7) { index ->
                val y = h * (0.16f + index * 0.11f)
                drawLine(
                    color = ink.copy(alpha = 0.07f),
                    start = Offset(w * 0.18f, y),
                    end = Offset(w, y),
                    strokeWidth = 0.8.dp.toPx()
                )
            }
        }

        // 简化的点阵世界地图：北美、南美、欧洲、非洲、亚洲、澳洲六个簇。
        val clusters = listOf(
            floatArrayOf(0.03f, 0.20f, 0.30f, 0.54f),
            floatArrayOf(0.22f, 0.56f, 0.37f, 0.88f),
            floatArrayOf(0.43f, 0.19f, 0.57f, 0.42f),
            floatArrayOf(0.48f, 0.43f, 0.64f, 0.78f),
            floatArrayOf(0.60f, 0.18f, 0.96f, 0.55f),
            floatArrayOf(0.80f, 0.72f, 0.95f, 0.88f)
        )
        clusters.forEach { c ->
            var y = c[1]
            var row = 0
            while (y < c[3]) {
                var x = c[0] + (row % 2) * 0.014f
                var col = 0
                while (x < c[2]) {
                    val skip = ((row * 13 + col * 7 + (x * 100).toInt()) % 9) == 0
                    if (!skip) {
                        drawCircle(
                            color = ink.copy(alpha = 0.20f),
                            radius = 1.15.dp.toPx(),
                            center = Offset(x * w, y * h)
                        )
                    }
                    x += 0.028f
                    col++
                }
                y += 0.050f
                row++
            }
        }

        val nodes = listOf(
            Offset(w * 0.22f, h * 0.35f),
            Offset(w * 0.40f, h * 0.28f),
            Offset(w * 0.52f, h * 0.50f),
            Offset(w * 0.67f, h * 0.32f),
            Offset(w * 0.82f, h * 0.55f),
            Offset(w * 0.70f, h * 0.78f),
            Offset(w * 0.86f, h * 0.24f)
        )
        nodes.forEachIndexed { index, point ->
            if (index < nodes.lastIndex) {
                drawLine(
                    color = accent.copy(alpha = 0.50f),
                    start = point,
                    end = nodes[index + 1],
                    strokeWidth = 1.1.dp.toPx()
                )
            }
        }
        nodes.forEach { point ->
            drawCircle(
                color = accent.copy(alpha = 0.12f),
                radius = 6.dp.toPx(),
                center = point
            )
            drawCircle(
                color = accent,
                radius = 2.dp.toPx(),
                center = point
            )
        }
    }
}

@Composable
private fun HistoryArt(modifier: Modifier, index: Int) {
    Canvas(modifier = modifier) {
        val palettes = listOf(
            listOf(Color(0xFF6E6356), Color(0xFF26231F)),
            listOf(Color(0xFF817567), Color(0xFF302A24)),
            listOf(Color(0xFF5F5B53), Color(0xFF1F1D1A))
        )
        val palette = palettes[index % palettes.size]
        drawRect(
            brush = Brush.verticalGradient(palette)
        )
        drawCircle(
            color = Color(0xFFE5DCCB).copy(alpha = 0.32f),
            radius = size.minDimension * 0.18f,
            center = Offset(size.width * 0.72f, size.height * 0.26f)
        )
        // 低对比度的建筑/人群剪影，作为历史资料卡片的占位画面。
        drawRect(
            color = Color(0xFF171512).copy(alpha = 0.58f),
            topLeft = Offset(0f, size.height * 0.55f),
            size = Size(size.width, size.height * 0.45f)
        )
        repeat(5) { i ->
            val x = size.width * (0.08f + i * 0.20f)
            drawRect(
                color = Color(0xFFE8DED0).copy(alpha = 0.24f),
                topLeft = Offset(x, size.height * (0.34f + (i % 3) * 0.06f)),
                size = Size(size.width * 0.10f, size.height * 0.30f)
            )
        }
        repeat(9) { i ->
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(0f, size.height * (0.12f + i * 0.09f)),
                end = Offset(size.width, size.height * (0.08f + i * 0.09f)),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

// ---------- 通用小件 ----------

@Composable
private fun MarketStrip(onOpen: () -> Unit) {
    var quotes by remember { mutableStateOf<List<MarketQuote>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { StockApi.headlineQuotes() } }
            .onSuccess {
                quotes = it
                failed = it.isEmpty()
            }
            .onFailure { failed = true }
    }
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .border(1.dp, Bg.Outline, RoundedCornerShape(Radius.lg))
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(3.dp, 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NewsRed)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = tr("今日行情", "Markets today"),
                color = Text.Primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = tr("全部行情", "All markets") + " ›",
                color = Text.Muted,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        if (quotes.isEmpty()) {
            Text(
                text = if (failed) {
                    tr("行情暂时拉不到，点这里打开完整股市页", "Quotes unavailable — tap to open the markets page")
                } else {
                    tr("正在拉取行情…", "Loading quotes…")
                },
                color = Text.Muted,
                fontSize = 11.5.sp
            )
        } else {
            Row(Modifier.fillMaxWidth()) {
                quotes.take(5).forEach { quote ->
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = quote.name,
                            color = Text.Muted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stockPriceText(quote.price),
                            color = if (quote.changePct >= 0) StockUp else StockDown,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            text = stockPctText(quote.changePct),
                            color = if (quote.changePct >= 0) StockUp else StockDown,
                            fontSize = 10.5.sp,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = tr(
                    "A股 / 港股 / 美股指数 · 数据可能延迟，仅供参考",
                    "A / HK / US indices · may be delayed"
                ),
                color = Text.Muted,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val StockUp = Color(0xFFD93B33)
private val StockDown = Color(0xFF15935F)

private fun stockPriceText(value: Double): String =
    String.format(Locale.CHINA, "%,.2f", value)

private fun stockPctText(pct: Double): String {
    val sign = if (pct > 0) "+" else if (pct < 0) "-" else ""
    return sign + String.format(Locale.CHINA, "%.2f%%", kotlin.math.abs(pct))
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(3.dp, 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NewsRed)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = Text.Primary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        Text(text = subtitle, color = Text.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun ContainerCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
    ) {
        content()
    }
}

@Composable
private fun LoadingCard(text: String) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = NewsRed,
            strokeWidth = 2.dp
        )
        Spacer(Modifier.width(10.dp))
        Text(text = text, color = Text.Muted, fontSize = 12.5.sp)
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Bg.Surface)
            .padding(16.dp)
    ) {
        Text(text = message, color = Text.Secondary, fontSize = 12.5.sp, lineHeight = 19.sp)
        TextButton(onClick = onRetry) {
            Text(tr("重试", "Retry"), color = NewsRed, fontSize = 12.5.sp)
        }
    }
}

@Composable
private fun EmptyCard(text: String, inCard: Boolean = false) {
    Box(
        modifier = if (inCard) Modifier.fillMaxWidth().padding(vertical = 24.dp)
        else Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Text.Muted, fontSize = 12.5.sp)
    }
}

@Composable
private fun SourceNote() {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .fillMaxWidth()
            .navigationBarsPadding(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Article,
            contentDescription = null,
            tint = Text.Muted,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = tr(
                "数据源：60s 早报 · 每日热榜 · V2EX · GitHub 趋势 · Hacker News · 60s 历史。全部 HTTPS 只读，不含账号信息；点击条目会在 App 内置网页中打开。",
                "Sources: 60s brief, daily hot list, V2EX, GitHub trends, Hacker News and 60s history. Read-only HTTPS, no account data; items open in the in-app browser."
            ),
            color = Text.Muted,
            fontSize = 11.sp,
            lineHeight = 17.sp
        )
    }
}

private fun friendlyClock(raw: String): String {
    val match = Regex("""\d{1,2}:\d{2}""").find(raw.trim()) ?: return ""
    val value = match.value
    return if (value.length == 4) "0$value" else value
}

private fun friendlyDate(raw: String): String {
    val parts = raw.trim().split("-")
    if (parts.size >= 3) {
        val month = parts[1].toIntOrNull()
        val day = parts[2].toIntOrNull()
        if (month != null && day != null) return "${month}月${day}日"
    }
    if (parts.size == 2) {
        val month = parts[0].toIntOrNull()
        val day = parts[1].toIntOrNull()
        if (month != null && day != null) return "${month}月${day}日"
    }
    if (raw.isBlank()) {
        return SimpleDateFormat("M月d日", Locale.CHINA).format(Date())
    }
    return raw
}

private fun formatHot(n: Long): String = when {
    n >= 100_000_000 -> String.format(Locale.CHINA, "%.1f亿热度", n / 100_000_000.0)
    n >= 10_000 -> String.format(Locale.CHINA, "%.1f万热度", n / 10_000.0)
    else -> "$n 热度"
}

private fun formatHotShort(n: Long): String = when {
    n >= 100_000_000 -> String.format(Locale.CHINA, "%.1f亿", n / 100_000_000.0)
    n >= 10_000 -> String.format(Locale.CHINA, "%.1f万", n / 10_000.0)
    else -> n.toString()
}
