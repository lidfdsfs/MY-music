package com.example.my.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.my.model.TripPlan
import com.example.my.state.AppState
import com.example.my.state.ExtraScreen
import com.example.my.ui.components.BackActionRow
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Func
import com.example.my.ui.theme.MainGradient
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow
import com.example.my.util.ImageExporter
import com.example.my.util.PlanPoster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.example.my.ui.i18n.tr

private val planTabs = listOf("全部", "待出发", "进行中", "已完成", "归档")
private const val DAY_MS = 24L * 60 * 60 * 1000
private val OngoingGreen = Color(0xFF2E9E6B)

/** 计划的当前阶段（全部按日期实时计算，不再手动维护状态字段） */
private enum class PlanStage { UPCOMING, TODAY, ONGOING, DONE }

/**
 * 我的计划页。
 * 支持：列表/日历切换、按时间/自定义排序、长按拖拽排序、多选批量删除/归档/导出长图，
 * 以及进行中绿色呼吸点与已完成灰度遮罩的状态视觉。
 */
@Composable
fun MyPlansScreen(state: AppState, embedded: Boolean = false) {
    var tab by remember { mutableStateOf(0) }
    var calendarMode by remember { mutableStateOf(false) }
    var multiSelect by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var keepTemplate by remember { mutableStateOf(true) }
    var monthMillis by remember { mutableLongStateOf(monthStartOf(todayStartMillis())) }
    var selectedDay by remember { mutableStateOf<Long?>(null) }
    val today = remember { todayStartMillis() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 进入行程页刷新自动归档：跨天停留后也能及时把已结束计划移入归档
    LaunchedEffect(Unit) { state.checkPlanAutoArchive() }

    val activePlans = remember(state.tripPlans) { state.tripPlans.filterNot { it.archived } }

    // 选中项按 id 实时映射，避免批量操作后残留无效 id
    val selectedPlans = remember(state.tripPlans, selectedIds) {
        state.tripPlans.filter { it.id in selectedIds }
    }

    val filtered = remember(state.tripPlans, tab, today, state.planCustomOrder) {
        val base = state.tripPlans.filter { if (tab == 4) it.archived else !it.archived }
        val byStage = when (tab) {
            1 -> base.filter { stageOf(it, today) == PlanStage.UPCOMING }
            2 -> base.filter {
                stageOf(it, today).let { s -> s == PlanStage.TODAY || s == PlanStage.ONGOING }
            }
            3 -> base.filter { stageOf(it, today) == PlanStage.DONE }
            else -> base
        }
        when {
            // 归档分类永远按时间倒序，最新归档的在前
            tab == 4 -> byStage.sortedByDescending { startMillisOf(it) ?: Long.MIN_VALUE }
            // 自定义排序：拖过的按 manualOrder，没拖过的回落到出发日期
            state.planCustomOrder -> byStage.sortedWith(
                compareBy(
                    { plan -> if (plan.manualOrder >= 0) plan.manualOrder else Int.MAX_VALUE },
                    { plan -> startMillisOf(plan) ?: Long.MAX_VALUE }
                )
            )
            else -> byStage.sortedBy { startMillisOf(it) ?: Long.MAX_VALUE }
        }
    }

    // 下一段旅程：优先今天出发，其次最近一次待出发（已归档的不参与）
    val nextTrip = remember(activePlans, today) {
        val todayTrip = activePlans.firstOrNull { stageOf(it, today) == PlanStage.TODAY }
        todayTrip ?: activePlans
            .filter { stageOf(it, today) == PlanStage.UPCOMING }
            .minByOrNull { startMillisOf(it) ?: Long.MAX_VALUE }
    }

    val planById = remember(state.tripPlans) { state.tripPlans.associateBy { it.id } }
    val orderedIds = remember(filtered) { filtered.map { it.id }.toMutableStateList() }
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragMoved by remember { mutableStateOf(false) }

    val exportPlans: (List<TripPlan>) -> Unit = { targets ->
        if (targets.isNotEmpty()) {
            scope.launch {
                val bitmap = withContext(Dispatchers.Default) { PlanPoster.render(targets, today) }
                val name = "我的行程_${System.currentTimeMillis()}"
                val saved = withContext(Dispatchers.IO) { ImageExporter.saveToGallery(context, bitmap, name) }
                val uri = withContext(Dispatchers.IO) { ImageExporter.shareUri(context, bitmap, name) }
                if (uri != null) ImageExporter.share(context, uri)
                state.toast(if (saved) tr("长图已保存到相册，并打开分享") else tr("长图已生成，已打开分享"))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Bg.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ---------- 顶部标题栏（自绘，右侧放视图切换/多选） ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (multiSelect) {
                    Text(
                        tr("已选 ${selectedIds.size} 项", "${selectedIds.size} selected"),
                        color = Text.Primary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        tr("全选"),
                        color = Brand.Terracotta,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { selectedIds = orderedIds.toSet() }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    Text(
                        tr("反选"),
                        color = Brand.Terracotta,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { selectedIds = orderedIds.toSet() - selectedIds }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    Text(
                        tr("完成"),
                        color = Text.Secondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                multiSelect = false
                                selectedIds = emptySet()
                            }
                            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                    )
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            tr("行程"),
                            color = Text.Primary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (state.tripPlans.isEmpty()) tr("计划一次说走就走", "Plan a spontaneous trip")
                            else tr("${activePlans.size} 段旅程 · ${state.tripPlans.size} 个计划",
                "${activePlans.size} active · ${state.tripPlans.size} plans"),
                            color = Text.Secondary,
                            fontSize = 11.sp
                        )
                    }
                    HeaderPill(
                        icon = if (calendarMode) Icons.Outlined.ViewList else Icons.Outlined.CalendarMonth,
                        label = if (calendarMode) tr("列表") else tr("日历"),
                        onClick = {
                            calendarMode = !calendarMode
                            selectedDay = null
                        }
                    )
                    Spacer(Modifier.width(6.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Bg.Surface)
                            .cardShadow(RoundedCornerShape(50.dp))
                            .clickable {
                                calendarMode = false
                                multiSelect = true
                                selectedIds = emptySet()
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.CheckBox, null, tint = Text.Secondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("多选"), color = Text.Secondary, fontSize = 12.sp)
                    }
                }
            }
            if (!embedded) {
                BackActionRow(onBack = { state.closeScreen() })
            }

            // 购票专区：火车票余票 + 机票比价，独立叠层页面
            if (!multiSelect) {
                TicketZoneEntry(onClick = { state.openScreen(ExtraScreen.TICKET_ZONE) })
            }

            if (state.tripPlans.isEmpty()) {
                EmptyTrips(
                    onCreate = { state.openPlanSheet() },
                    onAiCreate = { state.openAiTripSheet() }
                )
            } else if (calendarMode) {
                // ---------- 日历视图 ----------
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Spacer(Modifier.height(8.dp))
                    PlanCalendar(
                        plans = activePlans,
                        monthMillis = monthMillis,
                        selectedDay = selectedDay,
                        today = today,
                        onMonthChange = { monthMillis = it },
                        onSelectDay = { selectedDay = it }
                    )
                    Spacer(Modifier.height(16.dp))
                    val day = selectedDay
                    if (day == null) {
                        Text(
                            tr("点击日期查看当天的全部行程"),
                            color = Text.Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        val dayPlans = activePlans.filter { coversDay(it, day) }
                            .sortedBy { startMillisOf(it) ?: Long.MAX_VALUE }
                        Text(
                            text = "${SimpleDateFormat(tr("M月d日", "MMM d"), Locale.getDefault()).format(Date(day))} · ${dayPlans.size} 个行程",
                            color = Text.Primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        if (dayPlans.isEmpty()) {
                            Text(tr("这天没有安排行程"), color = Text.Muted, fontSize = 12.sp)
                        } else {
                            dayPlans.forEach { plan ->
                                PlanCard(
                                    plan = plan,
                                    today = today,
                                    onOpen = { state.openTripDetail(plan.id) },
                                    onDelete = {
                                        state.deleteTripPlan(plan.id)
                                        state.toast(tr("计划已删除"))
                                    }
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }
            } else {
                // ---------- 列表视图 ----------
                // 排序方式切换
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tr("共 ${orderedIds.size} 个", "${orderedIds.size} total"),
                        color = Text.Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (!multiSelect) {
                        SortChip(tr("按时间"), !state.planCustomOrder) {
                            state.updatePlanSortMode(false)
                        }
                        Spacer(Modifier.width(6.dp))
                        SortChip(tr("自定义"), state.planCustomOrder) {
                            state.updatePlanSortMode(true)
                        }
                    } else {
                        Text(
                            tr("长按卡片可拖动排序"),
                            color = Text.Muted,
                            fontSize = 11.sp
                        )
                    }
                }

                // 分段筛选
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(Bg.Surface)
                        .cardShadow(RoundedCornerShape(50.dp))
                        .padding(4.dp)
                ) {
                    planTabs.forEachIndexed { index, label ->
                        val selected = tab == index
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
                                .clickable {
                                    tab = index
                                    selectedIds = emptySet()
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tr(label),
                                color = if (selected) Color.White else Text.Secondary,
                                fontSize = 13.sp,
                                // 五个等宽 tab，英文（Ongoing/Completed）比中文长，单行省略不换行
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        // 多选时给底部操作条留位置；Tab 内要避开右下角浮动新建按钮
                        bottom = when {
                            multiSelect -> 150.dp
                            embedded -> 96.dp
                            else -> 32.dp
                        }
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 首页给下一段旅程一个视觉焦点（多选时隐藏，避免误选）
                    if (tab == 0 && nextTrip != null && !multiSelect) {
                        item(key = "nextTrip") {
                            NextTripHero(nextTrip, today)
                        }
                    }
                    items(orderedIds, key = { it }) { id ->
                        val plan = planById[id]
                        if (plan != null) {
                            val dragModifier = if (!multiSelect) {
                                Modifier.pointerInput(plan.id, filtered) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingId = plan.id
                                            dragOffset = 0f
                                            dragMoved = false
                                        },
                                        onDragEnd = {
                                            if (dragMoved) state.reorderTripPlans(orderedIds.toList())
                                            draggingId = null
                                            dragOffset = 0f
                                            dragMoved = false
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragOffset = 0f
                                            dragMoved = false
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            if (draggingId == plan.id) {
                                                dragOffset += amount.y
                                                dragMoved = true
                                                val info = listState.layoutInfo
                                                val currentInfo = info.visibleItemsInfo
                                                    .firstOrNull { it.key == plan.id }
                                                if (currentInfo != null) {
                                                    val centerY = currentInfo.offset + currentInfo.size / 2f + dragOffset
                                                    val targetInfo = info.visibleItemsInfo.firstOrNull {
                                                        centerY >= it.offset && centerY <= it.offset + it.size
                                                    }
                                                    if (targetInfo != null) {
                                                        val targetId = targetInfo.key as? Long
                                                        if (targetId != null) {
                                                            val from = orderedIds.indexOf(plan.id)
                                                            val to = orderedIds.indexOf(targetId)
                                                            if (from >= 0 && to >= 0 && from != to) {
                                                                orderedIds.add(to, orderedIds.removeAt(from))
                                                                dragOffset -= (targetInfo.offset - currentInfo.offset).toFloat()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }.graphicsLayer {
                                    translationY = if (draggingId == plan.id) dragOffset else 0f
                                }.zIndex(if (draggingId == plan.id) 1f else 0f)
                            } else Modifier

                            PlanCard(
                                plan = plan,
                                today = today,
                                selected = if (multiSelect) id in selectedIds else null,
                                modifier = dragModifier,
                                onOpen = {
                                    if (multiSelect) {
                                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                                    } else {
                                        state.openTripDetail(plan.id)
                                    }
                                },
                                onDelete = {
                                    state.deleteTripPlan(plan.id)
                                    state.toast(tr("计划已删除"))
                                }
                            )
                        }
                    }
                    if (filtered.isEmpty()) {
                        item(key = "filterEmpty") {
                            Text(
                                text = if (tab == 4) tr("还没有归档的行程") else tr("这个分类下还没有计划"),
                                color = Text.Muted,
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth().padding(top = 56.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // ---------- 多选底部操作条 ----------
        if (multiSelect) {
            val allArchived = selectedPlans.isNotEmpty() && selectedPlans.all { it.archived }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Bg.Surface)
                    .cardShadow(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .then(if (embedded) Modifier else Modifier.navigationBarsPadding())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tr("已选 ${selectedIds.size} 项", "${selectedIds.size} selected"),
                        color = Text.Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        tr("全选"),
                        color = Brand.Terracotta,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { selectedIds = orderedIds.toSet() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        tr("反选"),
                        color = Brand.Terracotta,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { selectedIds = orderedIds.toSet() - selectedIds }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BatchAction(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.DeleteOutline,
                        label = tr("删除"),
                        enabled = selectedIds.isNotEmpty(),
                        tint = if (selectedIds.isEmpty()) Text.Muted else Func.Danger
                    ) { confirmDelete = true }
                    BatchAction(
                        modifier = Modifier.weight(1f),
                        icon = if (allArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                        label = if (allArchived) tr("恢复") else tr("归档"),
                        enabled = selectedIds.isNotEmpty(),
                        tint = if (selectedIds.isEmpty()) Text.Muted else Text.Secondary
                    ) {
                        state.batchArchiveTripPlans(selectedIds, !allArchived)
                        state.toast(if (allArchived) tr("已恢复 ${selectedIds.size} 个行程", "${selectedIds.size} trips restored")
            else tr("已归档 ${selectedIds.size} 个行程", "${selectedIds.size} trips archived"))
                        multiSelect = false
                        selectedIds = emptySet()
                    }
                    BatchAction(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.FileDownload,
                        label = tr("导出长图"),
                        enabled = selectedIds.isNotEmpty(),
                        tint = if (selectedIds.isEmpty()) Text.Muted else Brand.Terracotta
                    ) {
                        exportPlans(selectedPlans)
                        multiSelect = false
                        selectedIds = emptySet()
                    }
                }
            }
        }

        // 右下角浮动入口：AI 生成行程 + 新建计划（多选/日历模式下隐藏）
        if (!multiSelect && !calendarMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .then(if (embedded) Modifier else Modifier.navigationBarsPadding())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(Bg.Surface)
                        .cardShadow(RoundedCornerShape(50.dp))
                        .clickable { state.openAiTripSheet() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        null,
                        tint = Brand.Terracotta,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(tr("AI 生成"), color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(MainGradient))
                        .cardShadow(CircleShape)
                        .clickable { state.openPlanSheet() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, tr("新建计划"), tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    }

    // 批量删除确认
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("删除 ${selectedIds.size} 个行程？", "Delete ${selectedIds.size} trips?"), color = Text.Primary, fontSize = 16.sp) },
            text = {
                Column {
                    Text(tr("删除后不可恢复。"), color = Text.Secondary, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { keepTemplate = !keepTemplate },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = keepTemplate, onCheckedChange = { keepTemplate = it })
                        Text(tr("保留出行清单模板，下次新建行程自动带上"), color = Text.Secondary, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    state.batchDeleteTripPlans(selectedIds, keepTemplate)
                    state.toast(tr("已删除 ${selectedIds.size} 个行程", "Deleted ${selectedIds.size} trips"))
                    confirmDelete = false
                    multiSelect = false
                    selectedIds = emptySet()
                }) {
                    Text(tr("删除"), color = Func.Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(tr("取消"), color = Text.Secondary)
                }
            }
        )
    }
}

/** 排序方式小胶囊 */
@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) Brand.Terracotta.copy(alpha = 0.12f) else Bg.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color = if (selected) Brand.Terracotta else Text.Muted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** 顶栏胶囊按钮（图标 + 文案） */
@Composable
private fun HeaderPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(Bg.Surface)
            .cardShadow(RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = Text.Secondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Text.Secondary, fontSize = 12.sp)
    }
}

/** 多选底部操作按钮 */
@Composable
private fun BatchAction(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Bg.SurfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** 月历：有行程的日期标陶土色圆点，多个行程圆点放大 */
@Composable
private fun PlanCalendar(
    plans: List<TripPlan>,
    monthMillis: Long,
    selectedDay: Long?,
    today: Long,
    onMonthChange: (Long) -> Unit,
    onSelectDay: (Long?) -> Unit
) {
    val firstDay = remember(monthMillis) { monthStartOf(monthMillis) }
    val cal = remember(firstDay) {
        Calendar.getInstance().apply { timeInMillis = firstDay }
    }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // 周一为一周第一天：Calendar.SUNDAY(1) -> 6，MONDAY(2) -> 0
    val offset = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val monthTitle = SimpleDateFormat(tr("yyyy年M月", "MMMM yyyy"), Locale.getDefault()).format(Date(firstDay))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Bg.Surface)
            .cardShadow(RoundedCornerShape(24.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CalendarNav(Icons.Outlined.ChevronLeft, tr("上个月")) {
                onMonthChange(shiftMonth(firstDay, -1))
                onSelectDay(null)
            }
            Text(
                monthTitle,
                color = Text.Primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            CalendarNav(Icons.Outlined.ChevronRight, tr("下个月")) {
                onMonthChange(shiftMonth(firstDay, 1))
                onSelectDay(null)
            }
        }
        Spacer(Modifier.height(10.dp))
        // 星期表头
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { week ->
                Text(
                    week,
                    color = Text.Muted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // 6 行网格
        for (row in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNum = row * 7 + col - offset + 1
                    Box(
                        modifier = Modifier.weight(1f).height(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayNum in 1..daysInMonth) {
                            val dayStart = firstDay + (dayNum - 1) * DAY_MS
                            val count = plans.count { coversDay(it, dayStart) }
                            val selected = selectedDay == dayStart
                            val isToday = dayStart == today
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { onSelectDay(dayStart) }
                                    .padding(2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .then(
                                            when {
                                                selected -> Modifier.background(Brush.linearGradient(MainGradient))
                                                isToday -> Modifier.background(
                                                    Brand.Terracotta.copy(alpha = 0.12f)
                                                )
                                                else -> Modifier
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        dayNum.toString(),
                                        color = when {
                                            selected -> Color.White
                                            isToday -> Brand.Terracotta
                                            else -> Text.Secondary
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = if (selected || isToday) FontWeight.SemiBold
                                        else FontWeight.Normal
                                    )
                                }
                                if (count > 0) {
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(if (count > 1) 6.dp else 4.dp)
                                            .clip(CircleShape)
                                            .background(Brand.Terracotta)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarNav(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Bg.SurfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = Text.Secondary, modifier = Modifier.size(18.dp))
    }
}

/** 空态：柔和圆底图标 + 主行动按钮 */
@Composable
private fun EmptyTrips(onCreate: () -> Unit, onAiCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(Brand.Terracotta.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Brand.Terracotta.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Assignment,
                    null,
                    tint = Brand.Terracotta.copy(alpha = 0.65f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(tr("还没有出行计划"), color = Text.Primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(tr("把行程排好，出发那天就不会手忙脚乱"), color = Text.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(26.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(Brush.linearGradient(MainGradient))
                .clickable(onClick = onCreate)
                .padding(horizontal = 24.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Edit, null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(tr("新建第一个计划"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(Bg.Surface)
                .cardShadow(RoundedCornerShape(50.dp))
                .clickable(onClick = onAiCreate)
                .padding(horizontal = 20.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = Brand.Terracotta, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(tr("让 AI 帮我排行程"), color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** 下一段旅程：渐变卡片，倒计时 + 日期 + 天数 */
@Composable
private fun NextTripHero(plan: TripPlan, today: Long) {
    val start = startMillisOf(plan) ?: today
    val daysLeft = ((start - today + DAY_MS - 1) / DAY_MS).coerceAtLeast(0)
    val countdown = when {
        daysLeft == 0L -> tr("今天出发")
        daysLeft == 1L -> tr("明天出发")
        else -> tr("还有 $daysLeft 天出发", "In $daysLeft days")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(MainGradient))
            .cardShadow(RoundedCornerShape(26.dp))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 10.dp)
                .size(92.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 46.dp, bottom = 8.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
        )
        Column(modifier = Modifier.padding(20.dp)) {
            Text(tr("下一段旅程"), color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                plan.destination.ifBlank { plan.name },
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                dateRangeText(plan),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color.White.copy(alpha = 0.20f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(countdown, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(tr("· ${plan.days} 天", "· ${plan.days} days"), color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
            }
        }
    }
}

/**
 * 计划卡片。
 * selected != null 表示多选模式：右侧显示勾选框，点击走 onOpen（父级切换选中）。
 * 已完成行程叠加半透明灰度遮罩；进行中左上角有绿色呼吸点。
 */
@Composable
private fun PlanCard(
    plan: TripPlan,
    today: Long,
    selected: Boolean? = null,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val stage = stageOf(plan, today)
    val start = startMillisOf(plan)
    val month = start?.let { SimpleDateFormat(tr("M月", "MMM"), Locale.getDefault()).format(Date(it)) } ?: ""
    val day = start?.let { SimpleDateFormat("d", Locale.CHINA).format(Date(it)) } ?: "—"
    val ongoing = stage == PlanStage.TODAY || stage == PlanStage.ONGOING

    val (statusText, statusColor) = when (stage) {
        PlanStage.UPCOMING -> tr("待出发") to Brand.Terracotta
        PlanStage.TODAY -> tr("今天出发") to Color(0xFFE67E22)
        PlanStage.ONGOING -> tr("进行中") to OngoingGreen
        PlanStage.DONE -> tr("已完成") to Text.Muted
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .cardShadow(RoundedCornerShape(Radius.lg))
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (stage == PlanStage.DONE) 0.72f else 1f)
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 左侧日期徽标
            Column(
                modifier = Modifier
                    .width(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Bg.SurfaceVariant)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(month, color = Text.Muted, fontSize = 10.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    day,
                    color = if (stage == PlanStage.DONE) Text.Muted else Brand.Terracotta,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        plan.name,
                        color = Text.Primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(statusColor.copy(alpha = 0.10f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        null,
                        tint = Text.Muted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        plan.destination.ifBlank { tr("目的地待定", "Destination TBD") },
                        color = Text.Secondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        dateRangeText(plan),
                        color = Text.Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected != null) {
                        SelectionDot(selected)
                    } else {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .clickable(onClick = onDelete)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                tr("删除计划", "Delete plan"),
                                tint = Text.Muted.copy(alpha = 0.8f),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(tr("删除"), color = Text.Muted, fontSize = 11.sp)
                        }
                    }
                }
                if (plan.companions.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tr(
                                    "同行 ${plan.companions.size} 人 · ${plan.companions.joinToString("、")}",
                                    "With ${plan.companions.size} · ${plan.companions.joinToString(", ")}"
                                ),
                        color = Text.Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (plan.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = plan.note,
                        color = Text.Muted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 已完成：半透明灰度遮罩
        if (stage == PlanStage.DONE) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF8C8C8C).copy(alpha = 0.14f))
            )
        }

        // 进行中：左上角绿色呼吸点
        if (ongoing) {
            val transition = rememberInfiniteTransition(label = "ongoingDot")
            val dotAlpha by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotAlpha"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(OngoingGreen.copy(alpha = dotAlpha))
            )
        }
    }
}

@Composable
private fun SelectionDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(Brush.linearGradient(MainGradient))
                else Modifier.background(Bg.SurfaceVariant)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
        }
    }
}

// ---------- 日期工具 ----------

private fun todayStartMillis(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun startMillisOf(plan: TripPlan): Long? = runCatching {
    SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(plan.startDate)?.time
}.getOrNull()

private fun stageOf(plan: TripPlan, today: Long): PlanStage {
    val start = startMillisOf(plan) ?: return PlanStage.UPCOMING
    val end = start + (plan.days - 1).coerceAtLeast(0) * DAY_MS
    return when {
        start > today -> PlanStage.UPCOMING
        start == today -> PlanStage.TODAY
        today <= end -> PlanStage.ONGOING
        else -> PlanStage.DONE
    }
}

/** 行程区间是否覆盖某一天（日历圆点与当日列表都用它） */
private fun coversDay(plan: TripPlan, dayStart: Long): Boolean {
    val start = startMillisOf(plan) ?: return false
    val end = start + (plan.days - 1).coerceAtLeast(0) * DAY_MS
    return dayStart in start..end
}

private fun monthStartOf(millis: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun shiftMonth(millis: Long, delta: Int): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = millis
        add(Calendar.MONTH, delta)
    }
    return monthStartOf(cal.timeInMillis)
}

/** 10.12 - 10.19 · 8 天 */
private fun dateRangeText(plan: TripPlan): String {
    val start = startMillisOf(plan) ?: return tr("${plan.startDate} · ${plan.days} 天", "${plan.startDate} · ${plan.days} days")
    val end = start + (plan.days - 1).coerceAtLeast(0) * DAY_MS
    val fmt = SimpleDateFormat("M.d", Locale.CHINA)
    return tr(
            "${fmt.format(Date(start))} - ${fmt.format(Date(end))} · ${plan.days} 天",
            "${fmt.format(Date(start))} - ${fmt.format(Date(end))} · ${plan.days} days"
        )
}
