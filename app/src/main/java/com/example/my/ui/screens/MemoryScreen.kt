package com.example.my.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.example.my.data.BackendApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.model.Footprint
import com.example.my.model.Route
import com.example.my.state.AppState
import com.example.my.ui.components.AppTopBar
import com.example.my.ui.components.BlurInAsyncImage
import com.example.my.ui.components.InteractionHintBanner
import com.example.my.ui.components.PrimaryButton
import com.example.my.ui.components.GradientCover
import com.example.my.ui.components.VideoPosterCover
import com.example.my.ui.components.staggeredIn
import com.example.my.ui.interaction.AnimatedNumber
import com.example.my.ui.interaction.EdgeHintIndicator
import com.example.my.ui.interaction.rememberEdgeDamping
import com.example.my.ui.interaction.scrollReveal
import com.example.my.ui.interaction.slideOutCancelClickable
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow
import com.example.my.ui.components.rememberIsResumed
import com.example.my.util.FootprintDate
import com.example.my.util.ImageExporter
import com.example.my.util.PhotoStore
import com.example.my.util.PosterRenderer
import com.example.my.util.cleanNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.abs
import com.example.my.ui.i18n.Lang
import com.example.my.ui.i18n.tr

/** 一个「年-月」分组下的足迹 */
private data class MonthGroup(
    val year: Int,
    val month: Int,
    val items: List<Footprint>
) {
    val key: String get() = "$year-$month"
    val photoCount: Int get() = items.sumOf { it.photoPaths.size }
}

/** 日期文本优先（用户改过日期就按改后的算），旧数据才回退到创建时间戳 */
private fun yearMonthOf(fp: Footprint): Pair<Int, Int> =
    FootprintDate.yearMonth(fp.date, fp.dateMillis)
        ?: (Calendar.getInstance().get(Calendar.YEAR) to 1)

/** 组内排序用的毫秒：同样文本优先，避免「改晚了日期还排在最上面」 */
private fun sortMillisOf(fp: Footprint): Long =
    FootprintDate.millis(fp.date) ?: fp.dateMillis ?: 0L

private fun yearOfDate(date: String): Int =
    date.take(4).toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)

private fun monthOfDate(date: String): Int? =
    Regex("""\d{4}[-年.](\d{1,2})""").find(date)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..12 }

/** 回忆页：统计 + 年份筛选 + 年→月折叠时间轴（足迹）/ 网格（路线）+ 批量删除与导出。
 *  embedded = true 时并入「足迹」页，不再自带标题栏。 */
@Composable
fun MemoryScreen(state: AppState, embedded: Boolean = false) {
    var year by remember { mutableStateOf("全部") }
    var specialOnly by remember { mutableStateOf(false) }
    var timeline by remember { mutableStateOf(true) }

    // 多选态
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 导出
    var exporting by remember { mutableStateOf(false) }
    var shareBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detailId by remember { mutableStateOf<Int?>(null) }
    // 网格卡片长按删除：足迹卡与旅程卡分别走两条删除路径
    var cardDeleteRequest by remember { mutableStateOf<CardDeleteRequest?>(null) }

    val scope = rememberCoroutineScope()

    val yearOptions = remember(state.routes, state.footprints) {
        listOf("全部") +
            (state.routes.map { it.year.toString() } +
                state.footprints.map { yearMonthOf(it).first.toString() })
                .distinct().sortedDescending()
    }
    val filteredRoutes = remember(year, specialOnly, state.routes) {
        state.routes.filter {
            (year == "全部" || it.year.toString() == year) && (!specialOnly || it.special)
        }
    }
    val filteredFps = remember(year, specialOnly, state.footprints) {
        state.footprints.filter {
            (year == "全部" || yearMonthOf(it).first.toString() == year) && (!specialOnly || it.special)
        }
    }
    // 时间轴按年倒序、年内按月倒序
    val groups = remember(filteredFps) {
        filteredFps.groupBy { yearMonthOf(it) }
            .map { (ym, list) -> MonthGroup(ym.first, ym.second, list.sortedByDescending { sortMillisOf(it) }) }
            .sortedWith(compareByDescending<MonthGroup> { it.year }.thenByDescending { it.month })
    }
    // 默认展开最近 3 个月，其余折叠；筛选条件变化时重置
    val groupSig = groups.joinToString { it.key }
    var collapsed by remember(groupSig) {
        mutableStateOf(groups.drop(3).map { it.key }.toSet())
    }

    // 统计与空态同源：全部从用户真实足迹派生（杜绝"没有足迹却显示旅程/天数/特别回忆"的矛盾）
    val journeyCount = state.footprints.map { it.city }.distinct().count()
    val daysCount = state.footprints
        .map {
            val ms = sortMillisOf(it)
            if (ms > 0L) (ms / 86_400_000L).toString() else it.date
        }
        .distinct().count()
    val specialCount = state.footprints.count { it.special }

    // 旅人排行榜：按途经城市数取 Top5（真实路线数据）
    val topRoutes = remember(state.routes) {
        state.routes.sortedByDescending { it.cities.size }.take(5)
    }

    fun exitSelection() {
        selectionMode = false
        selected = emptySet()
    }

    // 多选态下返回键先退出多选，而不是直接离开页面
    BackHandler(enabled = selectionMode) { exitSelection() }
    BackHandler(enabled = detailId != null) { detailId = null }
    // 撤销窗口的唯一数据源是 AppState：切 Tab 离开页面不再强制结算，
    // AppState 里那个 5 秒的 deleteJob 到期才真正清理，切回来撤销条仍在、仍可撤销。
    // pendingDeleteToken 每批删除自增，用它做标识可避免"连续删两批但数量相同"时计时不重启。
    val undoBatch = state.pendingDeleteToken
    val undoCount = state.pendingDeleted.size

    // ⑦ 边界阻尼：滑到头继续拉阻力增大，松手回弹并提示已经到头
    val listState = rememberLazyListState()
    val listEdge = rememberEdgeDamping(
        isAtTop = { !listState.canScrollBackward },
        isAtBottom = { !listState.canScrollForward }
    )

    fun doExport() {
        val picked = state.footprints.filter { it.id in selected }.take(9)
        if (picked.isEmpty()) return
        scope.launch {
            exporting = true
            val bmp = withContext(Dispatchers.Default) {
                runCatching { PosterRenderer.renderMemoryCard(picked) }.getOrNull()
            }
            exporting = false
            if (bmp == null) state.toast(tr("纪念卡生成失败，请重试"))
            else shareBitmap = bmp
        }
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(listEdge.connection)) {
        Column(modifier = Modifier.fillMaxSize().background(Bg.Background)) {
            // 固定标题栏（不随滚动）：独立页时显示；并入足迹页时由足迹页头部承载
            if (!embedded) {
                AppTopBar(
                    title = tr("旅行回忆"),
                    subtitle = tr("${state.footprints.size} 条回忆 · ${state.routes.size} 段旅程", "${state.footprints.size} memories · ${state.routes.size} journeys")
                )
            }
            // 固定操作行：多选入口 + 时间轴/网格切换（固定在标题栏下方，不随滚动）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = if (embedded) 8.dp else 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                if (timeline && state.footprints.isNotEmpty()) {
                    Text(
                        if (selectionMode) tr("退出多选") else tr("多选"),
                        color = Brand.Terracotta,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .clickable {
                                if (selectionMode) exitSelection() else selectionMode = true
                            }
                    )
                }
                ViewToggle(timeline = timeline, onToggle = {
                    if (selectionMode) exitSelection()
                    timeline = !timeline
                })
            }
            // 新交互一次性引导（卡堆滑动 / 边界阻尼回弹）
            InteractionHintBanner(
                text = tr("左右滑动卡片快速浏览回忆，滑到底继续拉会有回弹"),
                prefsKey = "hint_memory_swipe",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState,
                contentPadding = PaddingValues(top = 0.dp, bottom = if (selectionMode) 140.dp else 96.dp)
            ) {

            // 统计三卡（全为 0 时折叠，与空态一致）
            if (journeyCount > 0 || daysCount > 0 || specialCount > 0) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .staggeredIn(1),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MemoryStat(journeyCount, tr("旅程"), Modifier.weight(1f))
                        MemoryStat(daysCount, tr("旅行天数"), Modifier.weight(1f))
                        MemoryStat(specialCount, tr("特别回忆"), Modifier.weight(1f))
                    }
                }
            }

            // 旅行达人入口：点击进入多维度排行榜页
            if (topRoutes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .staggeredIn(2)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Bg.Surface)
                            .cardShadow()
                            .clickable { state.openScreen(com.example.my.state.ExtraScreen.TRAVELER_RANK) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Brand.Gold.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = Brand.Gold, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("旅行达人"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text(tr("城市数 · 天数 · 照片 · 回忆排行"), color = Text.Muted, fontSize = 12.sp)
                        }
                        Text("›", color = Text.Muted, fontSize = 20.sp)
                    }
                }
            }

            // 回忆卡堆：抽走上层露出下层（有真实足迹时才展示行程卡，避免空数据页面出现演示行程）
            if (state.routes.isNotEmpty() && state.footprints.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .staggeredIn(2)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("回忆卡堆"), color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(tr("左右滑动抽走"), color = Text.Muted, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        MemoryCardStack(
                            routes = state.routes,
                            footprints = state.footprints,
                            onOpen = { state.openRoute(it) }
                        )
                    }
                }
            }

            // 筛选栏
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .staggeredIn(2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(yearOptions, key = { it }) { y ->
                            YearFilterChip(text = tr(y), selected = year == y, onClick = { year = y })
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .then(if (specialOnly) Modifier else Modifier.cardShadow(RoundedCornerShape(50.dp)))
                            .clip(RoundedCornerShape(50.dp))
                            .background(if (specialOnly) Brand.Gold else Bg.Surface)
                            .slideOutCancelClickable(onClick = { specialOnly = !specialOnly })
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr("⭐ 特别"),
                            color = if (specialOnly) Color.White else Text.Secondary,
                            fontSize = 12.sp,
                            fontWeight = if (specialOnly) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (timeline) {
                if (groups.isEmpty()) {
                    item { EmptyMemory(cloudOnly = state.footprints.isEmpty(), onRecord = { state.openUpload() }) }
                } else {
                    val yearGroups = groups.groupBy { it.year }.toSortedMap(compareByDescending { it })
                    yearGroups.forEach { (y, months) ->
                        item {
                            Spacer(Modifier.height(20.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.width(56.dp), contentAlignment = Alignment.Center) {
                                    YearPill("$y")
                                }
                            }
                        }
                        months.forEach { g ->
                            val isCollapsed = g.key in collapsed
                            // 月份头（点击折叠/展开）
                            item {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (isCollapsed) Bg.SurfaceVariant else Brand.Terracotta.copy(alpha = 0.10f))
                                        .clickable {
                                            collapsed = if (isCollapsed) collapsed - g.key else collapsed + g.key
                                        }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.KeyboardArrowDown,
                                        null,
                                        tint = if (isCollapsed) Text.Muted else Brand.Terracotta,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .rotate(if (isCollapsed) -90f else 0f)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        tr("${g.month}月", "Month ${g.month}"),
                                        color = if (isCollapsed) Text.Secondary else Brand.Terracotta,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        tr("${g.items.size} 处足迹 · ${g.photoCount} 张照片", "${g.items.size} places · ${g.photoCount} photos"),
                                        color = Text.Muted,
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.weight(1f))
                                    if (selectionMode) {
                                        Text(
                                            if (g.items.all { it.id in selected }) tr("取消全选") else tr("全选本月"),
                                            color = Brand.Terracotta,
                                            fontSize = 12.sp,
                                            modifier = Modifier.clickable {
                                                selected = if (g.items.all { it.id in selected })
                                                    selected - g.items.map { it.id }.toSet()
                                                else selected + g.items.map { it.id }.toSet()
                                            }
                                        )
                                    }
                                }
                            }
                            if (!isCollapsed) {
                                itemsIndexed(g.items, key = { _, it -> it.id }) { fpIndex, fp ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                            // 滚动渐显：进屏才播，每块错开 80ms
                                            .scrollReveal(staggerMillis = (fpIndex % 4) * 80)
                                            .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .width(56.dp)
                                                .fillMaxHeight(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 6.dp)
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(Brand.Terracotta.copy(alpha = 0.55f))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .weight(1f)
                                                    .background(Brand.Terracotta.copy(alpha = 0.35f))
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        FootprintTimelineCard(
                                            fp = fp,
                                            selectionMode = selectionMode,
                                            selected = fp.id in selected,
                                            onToggleSelect = {
                                                selected = if (fp.id in selected) selected - fp.id else selected + fp.id
                                            },
                                            onClick = {
                                                if (selectionMode) {
                                                    selected = if (fp.id in selected) selected - fp.id else selected + fp.id
                                                } else detailId = fp.id
                                            },
                                            onLongClick = { selectionMode = true; selected = selected + fp.id },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 网格视图：同时显示足迹和旅程，避免"时间轴说0条但网格有卡片"的数据不一致
                if (filteredRoutes.isEmpty() && filteredFps.isEmpty()) {
                    item { EmptyMemory(cloudOnly = state.footprints.isEmpty() && state.routes.isEmpty(), onRecord = { state.openUpload() }) }
                } else {
                    // 本地自动生成的"旅程镜像卡"（与某条足迹同标题/日期/城市，见 AppState.ensureLocalRouteFor）
                    // 在网格里和足迹重复：足迹卡已带真实照片，这里跳过镜像路线，避免一条记忆渲染成两张卡；
                    // 时间轴视图本来就只显示足迹、不显示路线，不受影响。真正多日创作的旅程仍会显示。
                    val mirrorKeys = filteredFps.map { Triple(it.name, it.date, it.city) }.toSet()
                    val journeyRoutes = filteredRoutes.filterNot { r ->
                        Triple(r.title, r.date, r.cities.firstOrNull().orEmpty()) in mirrorKeys
                    }
                    // 先显示足迹卡片（如果有），再显示旅程卡片
                    if (filteredFps.isNotEmpty()) {
                        items(filteredFps.chunked(2), key = { "fp-${it.first().id}" }) { rowFps ->
                            Row(
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowFps.forEach { fp ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        MemoryGridTile(
                                            route = Route(
                                                id = fp.id,
                                                title = fp.name,
                                                year = yearOfDate(fp.date),
                                                date = fp.date,
                                                days = 1,
                                                cities = listOf(fp.city),
                                                coverSeed = fp.seed,
                                                special = fp.special,
                                                specialReason = fp.specialReason,
                                                note = fp.note,
                                                tags = emptyList(),
                                                totalCost = "",
                                                isPublic = false,
                                                photoCount = 0,
                                                itinerary = emptyList()
                                            ),
                                            onClick = { detailId = fp.id },
                                            onLongClick = {
                                                cardDeleteRequest = CardDeleteRequest(
                                                    footprintId = fp.id,
                                                    routeId = null,
                                                    title = fp.name
                                                )
                                            },
                                            modifier = Modifier,
                                            coverPhotoPath = fp.photoPaths.firstOrNull()
                                        )
                                    }
                                }
                                repeat(2 - rowFps.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    // 再显示旅程卡片（已剔除与足迹重复的本地镜像卡）
                    if (journeyRoutes.isNotEmpty()) {
                        items(journeyRoutes.chunked(2), key = { "jr-${it.first().id}" }) { rowRoutes ->
                            Row(
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowRoutes.forEach { r ->
                                    MemoryGridTile(
                                        route = r,
                                        onClick = { state.openRoute(r.id) },
                                        onLongClick = {
                                            cardDeleteRequest = CardDeleteRequest(
                                                footprintId = null,
                                                routeId = r.id,
                                                title = r.title
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        coverPhotoPath = routeCoverPhoto(r, state.footprints)
                                    )
                                }
                                repeat(2 - rowRoutes.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }

        // ⑦ 到头提示：到底/到顶后短暂浮现自动淡出
        EdgeHintIndicator(
            listEdge,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        )

        // 多选底部操作条
        if (selectionMode) {
            SelectionBar(
                count = selected.size,
                exporting = exporting,
                onCancel = { exitSelection() },
                onDelete = { if (selected.isNotEmpty()) showDeleteConfirm = true },
                onExport = { doExport() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // 删除后可撤销提示条：数量与批次都来自 AppState，撤销条随撤销窗口自然消失
        if (undoBatch != 0L && undoCount > 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1F2328))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (Lang.isEn) "Deleted $undoCount footprints" else "已删除 $undoCount 条足迹", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    tr("撤销"),
                    color = Brand.TerracottaLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { state.undoDeleteFootprints() }
                )
            }
        }

        // 导出结果：保存 / 分享面板
        shareBitmap?.let { bmp ->
            ExportSheet(
                bitmap = bmp,
                title = tr("旅行纪念卡"),
                onDismiss = { shareBitmap = null },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // 足迹详情
        detailId?.let { id ->
            state.footprints.firstOrNull { it.id == id }?.let { fp ->
                FootprintDetailSheet(fp = fp, onDismiss = { detailId = null }, state = state)
            }
        }
    }

    // 网格卡片长按删除（足迹卡走回收站，旅程卡直接移除）
    cardDeleteRequest?.let { req ->
        AlertDialog(
            onDismissRequest = { cardDeleteRequest = null },
            title = { Text(tr("删除这张卡片"), fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (req.routeId != null) {
                        tr("只删掉这张旅程卡「${req.title}」，足迹和照片都保留。",
                            "Remove this journey card \"${req.title}\". Footprints and photos are kept.")
                    } else {
                        tr("把「${req.title}」移入回收站，照片保留，3 天内可在设置里恢复。",
                            "\"${req.title}\" moves to trash. Photos are kept — restore within 3 days in Settings.")
                    },
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (req.routeId != null) {
                        state.deleteRoutes(setOf(req.routeId))
                    } else if (req.footprintId != null) {
                        state.deleteFootprints(setOf(req.footprintId))
                    }
                    cardDeleteRequest = null
                }) { Text(tr("删除"), color = Brand.Terracotta, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { cardDeleteRequest = null }) { Text(tr("取消"), color = Text.Secondary) }
            }
        )
    }

    if (showDeleteConfirm) {        val specialInSelection = state.footprints.count { it.id in selected && it.special }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(tr("删除足迹"), fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        if (Lang.isEn) "This moves ${selected.size} footprints to trash. Photos are kept and you can restore within 3 days in Settings."
                        else tr("将把 ${selected.size} 条足迹移入回收站，照片保留，3 天内可在设置里恢复。",
                        "This moves ${selected.size} footprints to trash. Photos are kept — restore within 3 days in Settings."),
                        fontSize = 14.sp
                    )
                    if (specialInSelection > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (Lang.isEn) "This includes $specialInSelection special memories."
                            else tr("其中包含 $specialInSelection 条特别回忆。",
                    "$specialInSelection of them are special memories."),
                            color = Brand.Terracotta,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    state.deleteFootprints(selected)
                    showDeleteConfirm = false
                    exitSelection()
                }) { Text(tr("删除"), color = Brand.Terracotta, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(tr("取消"), color = Text.Secondary) }
            }
        )
    }
}

/** 网格卡片长按删除请求：二选一，足迹卡带 footprintId，旅程卡带 routeId */
private data class CardDeleteRequest(val footprintId: Int?, val routeId: Int?, val title: String)

/** 多选底部操作条 */
@Composable
private fun SelectionBar(
    count: Int,
    exporting: Boolean,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(50))
            .background(Bg.Surface)
            .cardShadow(RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (Lang.isEn) "$count selected" else "已选 $count 项",
            color = Text.Primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp)
        )
        Spacer(Modifier.weight(1f))
        if (exporting) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Brand.Terracotta)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            tr("导出纪念卡"),
            color = if (count > 0 && !exporting) Brand.Terracotta else Text.Muted,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(enabled = count > 0 && !exporting, onClick = onExport)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
        Text(
            tr("删除"),
            color = if (count > 0) Color(0xFFE76F51) else Text.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(enabled = count > 0, onClick = onDelete)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
        Text(
            tr("取消"),
            color = Text.Secondary,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onCancel)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

/** 卡片堆叠：上层可左右拖动抽走，下层依次露出
 *  升级（七种交互）：④ 方向锁定 —— 初期判定横向且明显占优才进入抽卡；
 *  ⑤ 中途接管 —— 回弹/飞出动画进行中可直接按住接着拖；
 *  ⑥ 落点预览 —— 拖动中实时显示「松手抽走/松手回位」，下层卡片浮起预览补位，落点与预览一致 */
@Composable
private fun MemoryCardStack(
    routes: List<Route>,
    footprints: List<Footprint>,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var stack by remember(routes.size, routes.firstOrNull()?.id) { mutableStateOf(routes.take(4)) }
    val scope = rememberCoroutineScope()
    val anim = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // ⑥ 落点预览状态：拖动实时判定，松手后按同一判定落位，保证预览与落点一致
    var willDismiss by remember { mutableStateOf(false) }
    val nextLift by animateFloatAsState(
        targetValue = if (willDismiss) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 460f),
        label = "nextLift"
    )
    val nextAlpha by animateFloatAsState(
        targetValue = if (willDismiss) 1f else 0.45f,
        animationSpec = tween(150),
        label = "nextAlpha"
    )

    if (stack.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(152.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Bg.Surface)
                .cardShadow(RoundedCornerShape(24.dp))
                .clickable { stack = routes.take(4) },
            contentAlignment = Alignment.Center
        ) {
            Text(tr("👣 都已整理完，点击重新堆放"), color = Text.Muted, fontSize = 13.sp)
        }
        return
    }

    Box(modifier = modifier.fillMaxWidth().height(178.dp)) {
        for (i in stack.take(3).indices.reversed()) {
            val route = stack[i]
            val isTop = i == 0
            val coverPhotoPaths = routeCoverPhotos(route, footprints)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(152.dp)
                    .graphicsLayer {
                        val tx = if (isTop) (if (dragging) dragOffset else anim.value) else 0f
                        translationX = tx
                        // ⑥ 落点预览：判定抽走后下层卡片浮起补位，与最终落点一致
                        val extraLift = if (!isTop && i == 1) nextLift * 6.dp.toPx() else 0f
                        val extraScale = if (!isTop && i == 1) nextLift * 0.05f else 0f
                        translationY = i * 12.dp.toPx() - extraLift
                        val s = 1f - i * 0.05f + extraScale
                        scaleX = s
                        scaleY = s
                        alpha = if (!isTop && i == 1) nextAlpha else 1f
                        rotationZ = if (isTop) (tx / 60f).coerceIn(-7f, 7f) else 0f
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Bg.Surface)
                    .cardShadow(RoundedCornerShape(24.dp))
                    .then(
                        if (!isTop) Modifier
                        else Modifier.pointerInput(route.id, stack.size) {
                            val threshold = 96.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // ⑤ 动画中途接管：以动画当前位移为拖拽基准，接着拖
                                val base = anim.value
                                var totalX = 0f
                                var totalY = 0f
                                var horizontal = false
                                var released = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        released = true
                                        break
                                    }
                                    val d = change.positionChange()
                                    if (!horizontal) {
                                        totalX += d.x
                                        totalY += d.y
                                        // ④ 方向锁定：初期判定横向且明显占优才锁定为抽卡
                                        if (abs(totalX) > viewConfiguration.touchSlop &&
                                            abs(totalX) > abs(totalY)
                                        ) {
                                            horizontal = true
                                            dragging = true
                                            dragOffset = base + (change.position.x - down.position.x)
                                            change.consume()
                                        } else if (abs(totalY) > viewConfiguration.touchSlop) {
                                            break
                                        }
                                    } else {
                                        change.consume()
                                        dragOffset = base + (change.position.x - down.position.x)
                                    }
                                    // ⑥ 落点预览随拖动实时更新
                                    if (horizontal) {
                                        willDismiss = abs(dragOffset) > threshold
                                    }
                                }
                                if (horizontal) {
                                    dragging = false
                                    val go = abs(dragOffset) > threshold
                                    willDismiss = false
                                    if (go) {
                                        val target =
                                            if (dragOffset > 0) size.width * 1.6f else -size.width * 1.6f
                                        scope.launch {
                                            anim.snapTo(dragOffset)
                                            anim.animateTo(target, tween(240))
                                            stack = stack.drop(1)
                                            anim.snapTo(0f)
                                            dragOffset = 0f
                                        }
                                    } else {
                                        scope.launch {
                                            anim.snapTo(dragOffset)
                                            anim.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 420f))
                                            dragOffset = 0f
                                        }
                                    }
                                } else if (released) {
                                    onOpen(route.id)
                                }
                            }
                        }
                    )
            ) {
                StackCardContent(route, coverPhotoPaths)
                // ⑥ 落点预览：拖动中的状态提示（与松手后的落点严格一致）
                if (isTop && dragging) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                            .clip(RoundedCornerShape(50.dp))
                            .background(if (willDismiss) Color(0xE6E76F51) else Color(0xB3FFFFFF))
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            if (willDismiss) tr("松手抽走") else tr("松手回位"),
                            color = if (willDismiss) Color.White else Text.Primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StackCardContent(route: Route, coverPhotoPaths: List<String>) {
    Box(Modifier.fillMaxSize()) {
        if (coverPhotoPaths.isNotEmpty()) {
            AutoPhotoCarousel(
                photos = coverPhotoPaths,
                modifier = Modifier.fillMaxSize(),
                dim = PhotoStore.THUMB_DIM,
                clipShape = RoundedCornerShape(24.dp),
                fallback = {
                    GradientCover(
                        route.coverSeed,
                        Modifier.fillMaxSize(),
                        RoundedCornerShape(24.dp),
                        coverResId = route.coverResId
                    )
                }
            )
        } else {
            GradientCover(
                route.coverSeed,
                Modifier.fillMaxSize(),
                RoundedCornerShape(24.dp),
                coverResId = route.coverResId
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))
                    )
                )
        )
        if (route.special) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Star,
                        contentDescription = null,
                        tint = Brand.Gold,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(tr("特别回忆", "Special memory"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = route.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = (route.cities + route.date).joinToString(" · "),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun MemoryStat(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Bg.Surface)
            .cardShadow(RoundedCornerShape(22.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedNumber(value, color = Brand.Terracotta, fontSize = 20)
        Spacer(Modifier.height(6.dp))
        Text(label, color = Text.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun YearFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .then(if (selected) Modifier else Modifier.cardShadow(RoundedCornerShape(50.dp)))
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) Brand.Terracotta else Bg.Surface)
            .slideOutCancelClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Text.Secondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ViewToggle(timeline: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .cardShadow(RoundedCornerShape(50.dp))
            .clip(RoundedCornerShape(50.dp))
            .background(Bg.Surface)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToggleSegment(tr("时间轴"), timeline, Icons.Outlined.ViewTimeline) { if (!timeline) onToggle() }
        ToggleSegment(tr("网格"), !timeline, Icons.Outlined.GridView) { if (timeline) onToggle() }
    }
}

@Composable
private fun ToggleSegment(text: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) Brand.Terracotta else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            tint = if (selected) Color.White else Text.Secondary,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            color = if (selected) Color.White else Text.Secondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun YearPill(year: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(Brand.Terracotta)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = year,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** 时间轴上的足迹卡片：照片缩略图 + 地点 + 日期 + 备注 */
@Composable
private fun FootprintTimelineCard(
    fp: Footprint,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(if (selected) Brand.Terracotta.copy(alpha = 0.07f) else Bg.Surface)
                .then(
                    if (selected) Modifier.border(1.5.dp, Brand.Terracotta, RoundedCornerShape(24.dp))
                    else Modifier
                )
                .tapAndLongPress(onClick = onClick, onLongClick = onLongClick)
                .padding(14.dp)
        ) {
            if (fp.photoPaths.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fp.photoPaths.take(3).forEachIndexed { i, path ->
                        Box(modifier = Modifier.size(56.dp)) {
                            ThumbPhoto(path, Modifier.fillMaxSize())
                            if (i == 2 && fp.photoPaths.size > 3) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "+${fp.photoPaths.size - 3}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            } else if (fp.allVideoPaths.isNotEmpty()) {
                // 只有视频/实况的足迹：拿首帧当封面。之前这里是一块纯色渐变，完全认不出是哪段片子
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    VideoPosterCover(fp.allVideoPaths.first(), Modifier.fillMaxSize())
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xB3101318))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            if (fp.videoIsLive) tr("实况", "Live") else tr("视频", "Video"),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            } else {
                // 既没照片也没视频（如示例数据）降级为品牌渐变封面，避免出现空白块
                GradientCover(fp.seed, Modifier.fillMaxWidth().height(84.dp), RoundedCornerShape(18.dp))
                Spacer(Modifier.height(10.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = fp.name,
                    color = Text.Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (fp.special) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Star, contentDescription = null, tint = Brand.Gold, modifier = Modifier.size(14.dp))
                }
                // 有实况/视频、有配乐的足迹在卡片上就能认出来，不用点进去才知道
                if (fp.allVideoPaths.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    MemoryMediaBadge(
                        if (fp.allVideoPaths.size > 1) tr("视频 ×${fp.allVideoPaths.size}",
                            "Videos ×${fp.allVideoPaths.size}")
                        else if (fp.videoIsLive) tr("实况", "Live") else tr("视频", "Video")
                    )
                }
                if (fp.bgmPath != null && java.io.File(fp.bgmPath).exists()) {
                    Spacer(Modifier.width(6.dp))
                    MemoryMediaBadge("♪")
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = listOf(fp.city, fp.date).filter { it.isNotBlank() }.joinToString(" · "),
                color = Text.Secondary,
                fontSize = 12.sp
            )
            val noteText = cleanNote(fp.note)
            if (noteText.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(
                    text = noteText,
                    color = Text.Primary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) Brand.Terracotta else Color.White)
                    .border(1.5.dp, if (selected) Brand.Terracotta else Bg.OutlineStrong, CircleShape)
                    .clickable(onClick = onToggleSelect),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** 点击 + 长按：进入多选既支持顶部入口，也支持长按卡片 */
private fun Modifier.tapAndLongPress(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        detectTapGestures(
            onTap = { onClick() },
            onLongPress = { onLongClick() }
        )
    }

/** 卡片上的「实况 / 视频 / 配乐」小徽标 */
@Composable
private fun MemoryMediaBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Brand.Terracotta.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = Brand.Terracotta, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ThumbPhoto(path: String, modifier: Modifier = Modifier) {
    val bmp = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            PhotoStore.decodeFile(path, PhotoStore.THUMB_DIM)?.asImageBitmap()
        }
    }.value
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Bg.SurfaceVariant)
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = tr("足迹照片"),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            remoteUrl(path)?.let { url ->
                BlurInAsyncImage(
                    model = url,
                    contentDescription = tr("足迹照片"),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

/** 云端 /uploads/... 或完整 http 地址；本地绝对路径返回 null，交给 BitmapFactory 读。 */
internal fun remoteUrl(path: String?): String? {
    val p = path?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return if (p.startsWith("http") || p.startsWith("/uploads/")) BackendApi.fullUrl(p) else null
}

@Composable
internal fun AutoPhotoCarousel(
    photos: List<String>,
    modifier: Modifier = Modifier,
    dim: Int = PhotoStore.THUMB_DIM,
    clipShape: Shape = RectangleShape,
    intervalMillis: Long = 3500L,
    showIndicator: Boolean = false,
    onClick: ((Int) -> Unit)? = null,
    onIndexChange: ((Int) -> Unit)? = null,
    fallback: @Composable () -> Unit
) {
    val urls = remember(photos) {
        photos.filter { it.isNotBlank() }.distinct().take(8)
    }
    val bitmaps by produceState<Map<String, ImageBitmap>>(emptyMap(), urls, dim) {
        value = withContext(Dispatchers.IO) {
            urls.mapNotNull { path ->
                PhotoStore.decodeFile(path, dim)?.asImageBitmap()?.let { path to it }
            }.toMap()
        }
    }
    var index by remember(urls) { mutableIntStateOf(0) }
    // 轮播只在页面可见时推进：退到后台后不再每 3.5s 唤醒一次去换图
    val carouselResumed = rememberIsResumed()
    LaunchedEffect(urls, carouselResumed) {
        index = 0
        if (urls.size <= 1 || !carouselResumed) return@LaunchedEffect
        while (true) {
            delay(intervalMillis)
            index = (index + 1) % urls.size
        }
    }
    LaunchedEffect(index) { onIndexChange?.invoke(index) }
    Box(
        modifier = if (onClick != null) {
            modifier.clickable { onClick(index.coerceIn(0, urls.lastIndex.coerceAtLeast(0))) }
        } else {
            modifier
        }
    ) {
        if (urls.isEmpty()) {
            fallback()
        } else {
            Crossfade(
                targetState = index.coerceAtMost(urls.lastIndex),
                animationSpec = tween(650),
                label = "routeCover"
            ) { current ->
                val path = urls.getOrNull(current % urls.size)
                val bitmap = path?.let { bitmaps[it] }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(clipShape),
                        contentScale = ContentScale.Crop
                    )
                } else if (path != null && remoteUrl(path) != null) {
                    // 本地文件还没下载完时先用云端图顶上，不让旅行回忆空着
                    BlurInAsyncImage(
                        model = remoteUrl(path),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(clipShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    fallback()
                }
            }
        }
        if (showIndicator && urls.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(urls.size) { dot ->
                    Box(
                        modifier = Modifier
                            .size(if (dot == index) 6.dp else 4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (dot == index) 0.95f else 0.45f))
                    )
                }
            }
        }
    }
}

internal fun routeCoverPhotos(route: Route, footprints: List<Footprint>): List<String> {
    val candidates = footprints.filter { it.photoPaths.isNotEmpty() }
    candidates.filter { it.routeId == route.id }
        .takeIf { it.isNotEmpty() }
        ?.let { linked -> return linked.flatMap { it.photoPaths }.distinct() }
    candidates.firstOrNull {
        it.name == route.title &&
            it.date == route.date
    }?.let { return it.photoPaths.distinct() }
    val sameDay = candidates.filter {
        // 云端旧数据里 route.cities 可能为空（"[]"）：只要日期相同就参与匹配，
        // 不能因为城市为空把「十一月海边的五张照片」这类无地点回忆的封面漏掉。
        it.date == route.date &&
            (route.cities.isEmpty() || route.cities.any { city -> city == it.city })
    }
    sameDay.firstOrNull {
        route.title.contains(it.name) || it.name.contains(route.title)
    }?.let { return it.photoPaths.distinct() }
    return sameDay.singleOrNull()?.photoPaths?.distinct().orEmpty()
}

internal fun routeCoverPhoto(route: Route, footprints: List<Footprint>): String? =
    routeCoverPhotos(route, footprints).firstOrNull()

@Composable
private fun MemoryGridTile(
    route: Route,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverPhotoPath: String? = null,
    onLongClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Bg.Surface)
            .cardShadow()
            .then(
                if (onLongClick != null) {
                    Modifier.tapAndLongPress(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.slideOutCancelClickable(onClick = onClick)
                }
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(108.dp)) {
            // 优先显示真实照片（与时间轴同源），没有照片才退回渐变封面
            if (coverPhotoPath != null) {
                val coverBmp = produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, coverPhotoPath) {
                    value = withContext(Dispatchers.IO) {
                        PhotoStore.decodeFile(coverPhotoPath, PhotoStore.THUMB_DIM)?.asImageBitmap()
                    }
                }.value
                if (coverBmp != null) {
                    Image(
                        bitmap = coverBmp,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (remoteUrl(coverPhotoPath) != null) {
                    AsyncImage(
                        model = remoteUrl(coverPhotoPath),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    GradientCover(route.coverSeed, Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
                }
            } else {
                GradientCover(
                    route.coverSeed,
                    Modifier.fillMaxSize(),
                    RoundedCornerShape(0.dp),
                    coverResId = route.coverResId
                )
            }
            if (route.special) {
                Icon(
                    Icons.Outlined.Star,
                    contentDescription = null,
                    tint = Brand.Gold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp)
                )
            }
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = route.title,
                color = Text.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(route.date, color = Text.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EmptyMemory(cloudOnly: Boolean, onRecord: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = Text.Muted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(tr("还没有这段回忆"), color = Text.Secondary, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            if (cloudOnly) tr("本机还没有足迹记录，去记下第一段旅程吧", "No footprints on this device yet — record your first trip") else tr("去记录一段新的旅程吧"),
            color = Text.Muted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = if (cloudOnly) tr("去发布足迹") else tr("记录第一条足迹"),
            modifier = Modifier.padding(horizontal = 40.dp),
            onClick = onRecord
        )
    }
}
