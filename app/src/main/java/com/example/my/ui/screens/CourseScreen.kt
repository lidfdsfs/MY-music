package com.example.my.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.DialogProperties
import com.example.my.data.CourseImporter
import com.example.my.model.Course
import com.example.my.model.CourseTableSettings
import com.example.my.state.AppState
import com.example.my.ui.i18n.tr
import com.example.my.ui.components.LiquidGlassScrim
import com.example.my.ui.components.LiquidGlassStyles
import com.example.my.ui.components.LiquidGlassSurface
import com.example.my.ui.components.glassChipContainer
import com.example.my.ui.components.glassFieldContainer
import com.example.my.ui.components.liquidGlassBackground
import com.example.my.ui.components.rememberIsResumed
import com.example.my.ui.theme.AppPalette
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Ink
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.TypeScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 课表页。
 *
 * 布局要点：表头与网格共用一条横向滚动位置（表头用负 offset 跟随），窄屏选 7 天时
 * 每列仍有 50dp 可读宽度；节次栏固定在左侧不参与横滚。午休 / 晚休是真实插入的独立行，
 * 所以课程块的起止 y 由累积行高算出，而不是简单的「节次 × 行高」。
 *
 * 数据要点：课程支持每周 / 单周 / 双周 / 指定周范围，[Course.isInWeek] 是唯一判定入口，
 * App 内网格、今日概览和桌面小组件都走它，保证三处显示一致。
 */
@Composable
fun CourseScreen(state: AppState) {
    val table = state.courseTable
    val weekCount = table.weekCount
    var week by remember { mutableIntStateOf(table.currentWeek().coerceIn(1, weekCount)) }
    // 改了作息 / 开学日期 / 总周数后，周次重新对齐
    LaunchedEffect(table) { week = table.currentWeek().coerceIn(1, table.weekCount) }
    // 总周数调小后，别停留在「第 25 / 20 周」这种越界状态
    LaunchedEffect(weekCount) { if (week > weekCount) week = weekCount }

    var editing by remember { mutableStateOf<Course?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showTableSettings by remember { mutableStateOf(false) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Course?>(null) }
    var seedDay by remember { mutableIntStateOf(1) }
    var seedPeriod by remember { mutableIntStateOf(1) }

    // 每分钟更新一次「现在」，用于时间线和「正在上 / 下一节」判断。
    // 页面退到后台后不再空转，回到前台先补一次当前时间。
    var nowMin by remember { mutableIntStateOf(nowMinutes()) }
    val courseResumed = rememberIsResumed()
    LaunchedEffect(courseResumed) {
        if (!courseResumed) return@LaunchedEffect
        nowMin = nowMinutes()
        while (isActive) {
            delay(60_000L)
            nowMin = nowMinutes()
        }
    }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var importReport by remember { mutableStateOf<CourseImporter.Report?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val r = runCatching { CourseImporter.fromUri(ctx, uri, state.effectiveOcrEndpoint()) }.getOrElse {
                CourseImporter.Report(
                    emptyList(), 0,
                    tr("识别失败", "Recognition failed") + "：${it.message ?: tr("未知错误", "unknown error")}"
                )
            }
            importing = false
            importReport = r
        }
    }

    val days = table.daysPerWeek.coerceIn(5, 7)
    val periods = table.safePeriods
    val todayIdx = remember { todayDow() }
    val thisWeek = table.currentWeek()
    val isCurrentWeek = week == thisWeek

    val visibleCourses = state.courses.filter { it.dayOfWeek in 1..days && it.isInWeek(week) }
    val todayCourses = visibleCourses.filter { it.dayOfWeek == todayIdx }.sortedBy { it.startPeriod }
    val running = if (isCurrentWeek) todayCourses.firstOrNull {
        nowMin in table.slot(it.startPeriod).first..table.slot(it.endPeriod).second
    } else null
    val nextCourse = if (isCurrentWeek) todayCourses.firstOrNull {
        table.slot(it.startPeriod).first > nowMin
    } else null
    val weekCourseCount = visibleCourses.size

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 顶栏：标题 + 周次概览 + 导入 / 作息设置 / 加课
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("课表", "Timetable"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Text.Primary)
                Text(
                    tr("第 $week / $weekCount 周 · 本周 $weekCourseCount 门课", "Week $week of $weekCount · $weekCourseCount courses"),
                    fontSize = 11.sp,
                    color = Text.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (importing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp), color = Brand.Terracotta, strokeWidth = 2.dp
                )
                Spacer(Modifier.width(4.dp))
            }
            TextButton(onClick = { pickImage.launch("image/*") }, enabled = !importing) {
                Text(tr("导入", "Import"), color = Brand.Terracotta, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = { showTableSettings = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Settings, contentDescription = tr("课表设置", "Timetable settings"), tint = Text.Secondary)
            }
            IconButton(
                onClick = { editing = null; seedDay = todayIdx; seedPeriod = 1; showEditor = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = tr("添加课程", "Add course"), tint = Brand.Terracotta)
            }
        }

        // 今日概览：正在上 / 下一节 / 今天没课；不在本周时变成「回到本周」入口
        TodayCard(
            week = week,
            thisWeek = thisWeek,
            isCurrentWeek = isCurrentWeek,
            todayIdx = todayIdx,
            table = table,
            todayCount = todayCourses.size,
            running = running,
            next = nextCourse,
            onBackToThisWeek = { week = thisWeek }
        )

        // 周次导航：上一周 / 点周次快选 / 下一周 + 日期范围 + 本周标记
        WeekBar(
            week = week,
            weekCount = weekCount,
            isCurrentWeek = isCurrentWeek,
            table = table,
            days = days,
            onWeekChange = { week = it },
            onPick = { showWeekPicker = true },
            onBackToThisWeek = { week = thisWeek }
        )

        TimetableArea(
            modifier = Modifier.weight(1f),
            table = table,
            week = week,
            isCurrentWeek = isCurrentWeek,
            todayIdx = todayIdx,
            days = days,
            periods = periods,
            courses = visibleCourses,
            nowMin = nowMin,
            onCourseClick = { detail = it },
            onEmptyCellClick = { d, p ->
                editing = null; seedDay = d; seedPeriod = p; showEditor = true
            }
        )
    }

    if (showEditor) {
        CourseEditorSheet(
            initial = editing,
            table = table,
            week = week,
            allCourses = state.courses,
            seedDay = seedDay,
            seedPeriod = seedPeriod,
            onDismiss = { showEditor = false },
            onSave = { c -> state.saveCourse(c); showEditor = false },
            onDelete = editing?.let { { state.deleteCourse(it.id); showEditor = false } }
        )
    }

    if (showTableSettings) {
        CourseTableSettingsSheet(
            current = table,
            onDismiss = { showTableSettings = false },
            onSave = { state.updateCourseTable(it); showTableSettings = false }
        )
    }

    if (showWeekPicker) {
        WeekPickerDialog(
            week = week,
            weekCount = weekCount,
            currentWeek = thisWeek,
            onPick = { week = it; showWeekPicker = false },
            onDismiss = { showWeekPicker = false }
        )
    }

    detail?.let { c ->
        CourseDetailDialog(
            course = c,
            table = table,
            week = week,
            onDismiss = { detail = null },
            onEdit = { detail = null; editing = c; showEditor = true },
            onDelete = {
                state.deleteCourse(c.id)
                detail = null
                state.toast(tr("已删除课程", "Course deleted"))
            }
        )
    }

    importReport?.let { r ->
        CourseImportDialog(
            report = r,
            onDismiss = { importReport = null },
            onConfirm = { replace ->
                state.importCourses(r.courses, replace)
                importReport = null
                state.toast(
                    if (replace) tr("已覆盖导入 ${r.courses.size} 门课", "Replaced with ${r.courses.size} courses")
                    else tr("已追加导入 ${r.courses.size} 门课", "Added ${r.courses.size} courses")
                )
            }
        )
    }
}

// ---------------------------------------------------------------- 顶部卡片

@Composable
private fun TodayCard(
    week: Int,
    thisWeek: Int,
    isCurrentWeek: Boolean,
    todayIdx: Int,
    table: CourseTableSettings,
    todayCount: Int,
    running: Course?,
    next: Course?,
    onBackToThisWeek: () -> Unit
) {
    val accent = Brand.Terracotta
    val date = table.dateOf(week, 1)
    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clickable(enabled = !isCurrentWeek, onClick = onBackToThisWeek),
        cornerRadius = 18.dp,
        optics = LiquidGlassStyles.Card,
        contentAlignment = Alignment.Center
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (isCurrentWeek) tr("今天", "TODAY") else tr("查看周", "VIEWING"),
                    fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    color = if (isCurrentWeek) accent else Text.Muted
                )
                Text(
                    shortDate(if (isCurrentWeek) Calendar.getInstance() else table.dateOf(week, todayIdx)),
                    fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Text.Primary, maxLines = 1
                )
                Text(weekDayLabel(todayIdx), fontSize = 10.sp, color = Text.Muted)
            }
            Box(Modifier.width(1.dp).height(42.dp).background(Bg.Outline))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                when {
                    !isCurrentWeek -> {
                        Text(
                            tr("正在查看第 $week 周", "Viewing week $week"),
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Text.Primary
                        )
                        Text(
                            tr("点这里回到第 $thisWeek 周", "Tap to return to week $thisWeek"),
                            fontSize = 11.sp, color = Text.Muted
                        )
                    }
                    running != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Brand.Teal))
                            Spacer(Modifier.width(5.dp))
                            Text(tr("正在上", "In class"), fontSize = 10.sp, color = Brand.Teal, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            running.name, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = Text.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${table.startText(running.startPeriod)}–${table.endText(running.endPeriod)}" +
                                if (running.location.isBlank()) "" else " · ${running.location}",
                            fontSize = 11.sp, color = Text.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    next != null -> {
                        Text(tr("下一节", "Next class"), fontSize = 10.sp, color = accent, fontWeight = FontWeight.Bold)
                        Text(
                            next.name, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = Text.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${table.startText(next.startPeriod)}–${table.endText(next.endPeriod)}" +
                                if (next.location.isBlank()) "" else " · ${next.location}",
                            fontSize = 11.sp, color = Text.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    todayCount == 0 -> {
                        Text(
                            tr("今天没有课", "No classes today"),
                            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Text.Primary
                        )
                        Text(tr("好好休息，或去添加一门课", "Enjoy the day, or add a class"), fontSize = 11.sp, color = Text.Muted)
                    }
                    else -> {
                        Text(
                            tr("今天的课上完了", "All done for today"),
                            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Text.Primary
                        )
                        Text(
                            tr("今日共 $todayCount 节课", "$todayCount classes today"),
                            fontSize = 11.sp, color = Text.Muted
                        )
                    }
                }
            }
            if (!isCurrentWeek) {
                Text(tr("回本周", "This week"), fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WeekBar(
    week: Int,
    weekCount: Int,
    isCurrentWeek: Boolean,
    table: CourseTableSettings,
    days: Int,
    onWeekChange: (Int) -> Unit,
    onPick: () -> Unit,
    onBackToThisWeek: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Bg.SurfaceVariant)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = tr("上一周", "Previous week"),
                tint = if (week > 1) Text.Secondary else Text.Muted,
                modifier = Modifier.size(22.dp).clickable(enabled = week > 1) { onWeekChange(week - 1) }
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onPick)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tr("第 $week 周", "Week $week"),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Text.Primary
                )
                Text(" ▾", fontSize = 11.sp, color = Text.Muted)
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = tr("下一周", "Next week"),
                tint = if (week < weekCount) Text.Secondary else Text.Muted,
                modifier = Modifier.size(22.dp).clickable(enabled = week < weekCount) { onWeekChange(week + 1) }
            )
        }
        Spacer(Modifier.width(8.dp))
        val monday = table.dateOf(week, 1)
        val lastDay = table.dateOf(week, days)
        Text(
            "${shortDate(monday)} – ${shortDate(lastDay)}",
            fontSize = 10.sp, color = Text.Muted, maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        if (isCurrentWeek) {
            Text("● " + tr("本周", "This week"), fontSize = 10.sp, color = Brand.Teal, maxLines = 1)
        } else {
            TextButton(onClick = onBackToThisWeek, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(tr("回到本周", "Current week"), fontSize = 11.sp, color = Brand.Terracotta)
            }
        }
    }
}

// ---------------------------------------------------------------- 网格

/** 一行要么是节次，要么是插入的休息条 */
private data class GridRow(val period: Int?, val breakLabel: String?, val height: Dp)

private val BREAK_H = 22.dp

private fun buildRows(table: CourseTableSettings, periods: Int, rowH: Dp): List<GridRow> {
    val rows = mutableListOf<GridRow>()
    for (p in 1..periods) {
        rows += GridRow(p, null, rowH)
        if (p < periods && p == table.lunchAfter && table.lunchMinutes > 0) {
            rows += GridRow(null, tr("午休", "Lunch") + " · ${table.lunchMinutes}" + tr("分", "m"), BREAK_H)
        }
        if (p < periods && p == table.eveningAfter && table.eveningMinutes > 0) {
            rows += GridRow(null, tr("晚休", "Dinner") + " · ${table.eveningMinutes}" + tr("分", "m"), BREAK_H)
        }
    }
    return rows
}

private data class RowPositions(
    val startY: Map<Int, Float>,
    val endY: Map<Int, Float>,
    val breaks: List<Pair<Float, GridRow>>,
    val boundaries: List<Float>,
    val total: Float
)

private fun positions(rows: List<GridRow>): RowPositions {
    val starts = mutableMapOf<Int, Float>()
    val ends = mutableMapOf<Int, Float>()
    val breaks = mutableListOf<Pair<Float, GridRow>>()
    val bounds = mutableListOf(0f)
    var acc = 0f
    rows.forEach { r ->
        if (r.period != null) {
            starts[r.period] = acc
            ends[r.period] = acc + r.height.value
        } else {
            breaks += acc to r
        }
        acc += r.height.value
        bounds += acc
    }
    return RowPositions(starts, ends, breaks, bounds, acc)
}

/**
 * 同一节次有多门课（不同周次的课、手滑重复添加）时并排分栏，而不是互相盖住。
 * 只在「重叠连通块」内分配 lane，不相干的课仍占满整列。
 */
private fun placeCourses(list: List<Course>): Map<Long, Pair<Int, Int>> {
    val out = mutableMapOf<Long, Pair<Int, Int>>()
    list.groupBy { it.dayOfWeek }.values.forEach { dayList ->
        val sorted = dayList.sortedWith(compareBy({ it.startPeriod }, { it.endPeriod }, { it.id }))
        var cluster = mutableListOf<Course>()
        var clusterEnd = -1

        fun flush() {
            if (cluster.isEmpty()) return
            val laneEnds = mutableListOf<Int>()
            val laneOf = mutableMapOf<Long, Int>()
            cluster.forEach { c ->
                val i = laneEnds.indexOfFirst { it < c.startPeriod }
                if (i < 0) {
                    laneEnds.add(c.endPeriod)
                    laneOf[c.id] = laneEnds.lastIndex
                } else {
                    laneEnds[i] = c.endPeriod
                    laneOf[c.id] = i
                }
            }
            val n = laneEnds.size
            cluster.forEach { out[it.id] = (laneOf[it.id] ?: 0) to n }
        }

        sorted.forEach { c ->
            if (cluster.isNotEmpty() && c.startPeriod > clusterEnd) {
                flush()
                cluster = mutableListOf()
                clusterEnd = -1
            }
            cluster.add(c)
            clusterEnd = maxOf(clusterEnd, c.endPeriod)
        }
        flush()
    }
    return out
}

@Composable
private fun TimetableArea(
    modifier: Modifier,
    table: CourseTableSettings,
    week: Int,
    isCurrentWeek: Boolean,
    todayIdx: Int,
    days: Int,
    periods: Int,
    courses: List<Course>,
    nowMin: Int,
    onCourseClick: (Course) -> Unit,
    onEmptyCellClick: (Int, Int) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val gutterW = 46.dp
        // 最小列宽保证 7 天模式也能读；屏幕装不下时整块横向滚动，表头同步偏移
        val colW = maxOf(50.dp, (maxWidth - gutterW) / days)
        val contentW = colW * days
        val rowH = if (days >= 7) 68.dp else 74.dp
        val rows = buildRows(table, periods, rowH)
        val pos = positions(rows)
        val hState = rememberScrollState()
        val vState = rememberScrollState()
        // 表头不套横滚容器，用负 offset 跟随网格的滚动值，避免两个容器抢同一个 ScrollState
        val headerOffset = Dp(-hState.value / LocalDensity.current.density)
        val placed = placeCourses(courses)
        val gridColor = Bg.Outline.copy(alpha = 0.7f)
        val accent = Brand.Terracotta
        // 用 surface 而非 background 判深浅：自定义背景态 background = Transparent，
        // RGB 恒为 0 会误判成深色，导致课程块底色永远走 0.17f 分支。
        val surfaceIsDark = AppPalette.value.surface.let { it.red + it.green + it.blue < 1.2f }
        val nowPeriod = if (isCurrentWeek) (1..periods).firstOrNull {
            nowMin in table.slot(it).first..table.slot(it).second
        } else null

        Column(Modifier.fillMaxSize()) {
            // 星期 / 日期表头（与下方网格同一列宽基准）
            Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(gutterW), contentAlignment = Alignment.Center) {
                    Text(tr("节次", "No."), fontSize = 10.sp, color = Text.Muted)
                }
                Box(Modifier.weight(1f).clipToBounds()) {
                    Row(Modifier.offset(x = headerOffset).width(contentW).fillMaxHeight()) {
                        for (d in 1..days) {
                            val date = table.dateOf(week, d)
                            val isToday = isCurrentWeek && d == todayIdx
                            Column(
                                Modifier.width(colW).fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (isToday) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(accent)
                                            .padding(horizontal = 7.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(weekDayLabel(d), fontSize = 11.sp, color = Text.Inverse, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text(
                                        weekDayLabel(d), fontSize = 12.sp,
                                        color = Text.Secondary, fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(shortDate(date), fontSize = 10.sp, color = if (isToday) accent else Text.Muted)
                            }
                        }
                    }
                }
            }

            // 网格：整块纵向滚动，右侧列横向滚动，左侧节次栏固定
            Row(Modifier.fillMaxSize().verticalScroll(vState)) {
                Column(Modifier.width(gutterW).height(pos.total.dp)) {
                    rows.forEach { r ->
                        if (r.period != null) {
                            Column(
                                Modifier.fillMaxWidth().height(r.height),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("${r.period}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Text.Primary)
                                Text(table.startText(r.period), fontSize = 9.sp, color = Text.Muted)
                                Text(table.endText(r.period), fontSize = 9.sp, color = Text.Muted)
                            }
                        } else {
                            Box(Modifier.fillMaxWidth().height(r.height))
                        }
                    }
                }

                Box(Modifier.weight(1f).horizontalScroll(hState)) {
                    Box(Modifier.width(contentW).height(pos.total.dp)) {
                        // 今日整列淡色底
                        if (isCurrentWeek && todayIdx <= days) {
                            Box(
                                Modifier
                                    .offset(x = colW * (todayIdx - 1), y = 0.dp)
                                    .width(colW).height(pos.total.dp)
                                    .background(accent.copy(alpha = 0.07f))
                            )
                        }

                        // 网格线：横线按实际行边界（含休息条），竖线按列
                        Canvas(Modifier.matchParentSize()) {
                            val cw = colW.toPx()
                            pos.boundaries.forEach { y ->
                                val py = y.dp.toPx()
                                drawLine(gridColor, Offset(0f, py), Offset(size.width, py), 1f)
                            }
                            repeat(days + 1) { d ->
                                val px = cw * d
                                drawLine(gridColor, Offset(px, 0f), Offset(px, size.height), 1f)
                            }
                        }

                        // 午休 / 晚休：真实占位行，不再浮在课程上
                        pos.breaks.forEach { (y, r) ->
                            Box(
                                Modifier
                                    .offset(y = y.dp).width(contentW).height(r.height)
                                    .background(Bg.SurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.width(16.dp).height(1.dp).background(Bg.OutlineStrong))
                                    Spacer(Modifier.width(6.dp))
                                    Text(r.breakLabel.orEmpty(), fontSize = 10.sp, color = Text.Muted)
                                    Spacer(Modifier.width(6.dp))
                                    Box(Modifier.width(16.dp).height(1.dp).background(Bg.OutlineStrong))
                                }
                            }
                        }

                        // 空白格点击 → 在该格新增课程
                        for (d in 1..days) {
                            for (p in 1..periods) {
                                val y = pos.startY[p] ?: continue
                                Box(
                                    Modifier
                                        .offset(x = colW * (d - 1), y = y.dp)
                                        .width(colW).height(rowH)
                                        .clickable { onEmptyCellClick(d, p) }
                                )
                            }
                        }

                        // 现在时间线：只画在本周今天的那一列
                        if (nowPeriod != null && todayIdx <= days) {
                            val (s, e) = table.slot(nowPeriod)
                            val ratio = ((nowMin - s).toFloat() / (e - s).coerceAtLeast(1)).coerceIn(0f, 1f)
                            val lineY = Dp((pos.startY[nowPeriod] ?: 0f) + rowH.value * ratio)
                            // zIndex 抬到课程块之上，不然正在上课时红线会被课程块盖住看不见
                            Canvas(Modifier.matchParentSize().zIndex(1f)) {
                                val x0 = colW.toPx() * (todayIdx - 1)
                                val py = lineY.toPx()
                                drawLine(accent, Offset(x0, py), Offset(x0 + colW.toPx(), py), 2f)
                                drawCircle(accent, radius = 3.dp.toPx(), center = Offset(x0, py))
                            }
                        }

                        // 课程块
                        courses.forEach { c ->
                            val sp = c.startPeriod.coerceIn(1, periods)
                            val ep = c.endPeriod.coerceIn(sp, periods)
                            val y0 = pos.startY[sp] ?: return@forEach
                            val y1 = pos.endY[ep] ?: return@forEach
                            val (lane, lanes) = placed[c.id] ?: (0 to 1)
                            val laneW = (colW - 4.dp) / lanes
                            val blockH = Dp(y1 - y0) - 4.dp
                            val tint = courseTint(c.colorIndex)
                            val past = week < table.currentWeek() ||
                                (isCurrentWeek && c.dayOfWeek == todayIdx && table.slot(ep).second < nowMin)
                            Box(
                                Modifier
                                    .offset(
                                        x = colW * (c.dayOfWeek - 1) + 2.dp + laneW * lane,
                                        y = Dp(y0) + 2.dp
                                    )
                                    .width(laneW - if (lanes > 1) 2.dp else 0.dp)
                                    .height(blockH)
                                    .clip(RoundedCornerShape(12.dp))
                                    // 过去课程只淡化底色 / 色条，文字保持 >=0.84 不透明。
                                    // 旧的整体 .alpha(0.55f) 会把课名、地点、老师一起糊掉。
                                    .background(tint.copy(alpha = if (past) {
                                        if (surfaceIsDark) 0.10f else 0.15f
                                    } else if (surfaceIsDark) 0.17f else 0.28f))
                                    .border(1.dp, tint.copy(alpha = if (past) 0.32f else 0.55f), RoundedCornerShape(12.dp))
                                    .clickable { onCourseClick(c) }
                            ) {
                                Row(Modifier.fillMaxSize()) {
                                    Box(Modifier.width(3.dp).fillMaxHeight().background(tint))
                                    Column(Modifier.padding(horizontal = 5.dp, vertical = 4.dp)) {
                                        Text(
                                            c.name,
                                            fontSize = if (lanes > 1) 10.sp else 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (past) Ink.OnGlassSub else Ink.OnGlass,
                                            maxLines = if (blockH > 100.dp) 3 else 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (blockH >= 70.dp && c.location.isNotBlank()) {
                                            Text(
                                                c.location, fontSize = TypeScale.tiny, color = if (past) Ink.OnGlassMuted else Ink.OnGlassSub,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (blockH >= 96.dp && c.teacher.isNotBlank()) {
                                            Text(
                                                c.teacher, fontSize = TypeScale.tiny, color = Ink.OnGlassMuted,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (c.safeWeekMode != Course.WEEK_EVERY && blockH >= 56.dp) {
                                            Text(
                                                weekLabel(c), fontSize = TypeScale.micro,
                                                color = Ink.OnGlassMuted, fontWeight = FontWeight.Medium,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis
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
    }
}

// ---------------------------------------------------------------- 弹窗

@Composable
private fun WeekPickerDialog(
    week: Int,
    weekCount: Int,
    currentWeek: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = Bg.Surface, shadowElevation = 12.dp) {
            Column(Modifier.padding(18.dp).heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text(tr("选择周次", "Jump to week"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Text.Primary)
                Text(
                    tr("第 $currentWeek 周是本周", "Week $currentWeek is this week"),
                    fontSize = 11.sp, color = Text.Muted
                )
                Spacer(Modifier.height(14.dp))
                (1..weekCount).chunked(5).forEach { chunk ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chunk.forEach { w ->
                            SelectPill(
                                text = tr("第${w}周", "W$w"),
                                selected = w == week,
                                modifier = Modifier.weight(1f)
                            ) { onPick(w) }
                        }
                        repeat(5 - chunk.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun CourseDetailDialog(
    course: Course,
    table: CourseTableSettings,
    week: Int,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tint = courseTint(course.colorIndex)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = Bg.Surface, shadowElevation = 12.dp) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(4.dp).height(26.dp).clip(RoundedCornerShape(2.dp)).background(tint))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        course.name, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = Text.Primary, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = tr("关闭", "Close"), tint = Text.Muted)
                    }
                }
                Spacer(Modifier.height(12.dp))
                DetailLine(
                    tr("时间", "Time"),
                    "${weekDayLabel(course.dayOfWeek)} · " +
                        tr("第 ${course.startPeriod}–${course.endPeriod} 节", "Periods ${course.startPeriod}–${course.endPeriod}")
                )
                DetailLine(tr("作息", "Hours"), "${table.startText(course.startPeriod)}–${table.endText(course.endPeriod)}")
                DetailLine(tr("周次", "Weeks"), weekLabel(course))
                if (course.location.isNotBlank()) DetailLine(tr("地点", "Room"), course.location)
                if (course.teacher.isNotBlank()) DetailLine(tr("老师", "Teacher"), course.teacher)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (course.isInWeek(week))
                        tr("第 $week 周有这节课", "Scheduled in week $week")
                    else
                        tr("第 $week 周没有这节课", "Not scheduled in week $week"),
                    fontSize = 11.sp,
                    color = if (course.isInWeek(week)) Brand.Teal else Text.Muted
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDelete) {
                        Text(tr("删除", "Delete"), color = Ink.Danger, fontSize = 13.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onEdit,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Brand.Terracotta, contentColor = Text.Inverse
                        )
                    ) { Text(tr("编辑课程", "Edit"), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 12.sp, color = Text.Muted, modifier = Modifier.width(52.dp))
        Text(value, fontSize = 13.sp, color = Text.Primary)
    }
}

// ---------------------------------------------------------------- 设置 / 编辑 / 导入

/** 设置面板里逐节编辑用的时间文本，保存时才解析成分钟 */
private data class EditablePeriodTime(val start: String, val end: String)

@Composable
private fun CourseTableSettingsSheet(
    current: CourseTableSettings,
    onDismiss: () -> Unit,
    onSave: (CourseTableSettings) -> Unit
) {
    var periods by remember { mutableIntStateOf(current.periodsPerDay.coerceIn(1, 16)) }
    var lesson by remember { mutableIntStateOf(current.lessonMinutes) }
    var breakMin by remember { mutableIntStateOf(current.breakMinutes) }
    var days by remember { mutableIntStateOf(current.daysPerWeek) }
    var lunchAfter by remember { mutableIntStateOf(current.lunchAfter) }
    var lunchMin by remember { mutableIntStateOf(current.lunchMinutes) }
    var eveningAfter by remember { mutableIntStateOf(current.eveningAfter) }
    var eveningMin by remember { mutableIntStateOf(current.eveningMinutes) }
    var semester by remember { mutableStateOf(current.semesterMonday) }
    var totalWeeks by remember { mutableIntStateOf(current.totalWeeks) }
    var dateError by remember { mutableStateOf(false) }
    var slotError by remember { mutableStateOf(false) }
    // 每节时间用文本编辑，允许用户中途只输入「08:2」，保存时再校验
    var periodTimes by remember {
        mutableStateOf(
            (1..current.safePeriods).map { p ->
                val auto = current.autoSlot(p)
                EditablePeriodTime(
                    CourseTableSettings.hhmm(current.periodStartMinutes.getOrNull(p - 1) ?: auto.first),
                    CourseTableSettings.hhmm(current.periodEndMinutes.getOrNull(p - 1) ?: auto.second)
                )
            }
        )
    }

    // 用当前面板上的「每节时长 / 课间 / 第一节开始 / 午休 / 晚休」生成一份草稿，用于自动重排
    fun draftSettings(count: Int): CourseTableSettings {
        val start = periodTimes.firstOrNull()?.let { CourseTableSettings.parseTime(it.start) } ?: current.startMinutes
        return CourseTableSettings(
            periodsPerDay = count,
            lessonMinutes = lesson,
            breakMinutes = breakMin,
            startMinutes = start,
            daysPerWeek = days,
            lunchAfter = lunchAfter.coerceIn(0, count),
            lunchMinutes = lunchMin,
            eveningAfter = eveningAfter.coerceIn(0, count),
            eveningMinutes = eveningMin,
            semesterMonday = semester,
            totalWeeks = totalWeeks
        )
    }

    // 增减每天节数时，保留已编辑的节次；新增的节次按当前规则补上默认时间
    LaunchedEffect(periods) {
        if (periodTimes.size != periods) {
            val draft = draftSettings(periods)
            periodTimes = (1..periods).map { p ->
                periodTimes.getOrNull(p - 1)
                    ?: draft.autoSlot(p).let { EditablePeriodTime(CourseTableSettings.hhmm(it.first), CourseTableSettings.hhmm(it.second)) }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                color = Bg.Surface,
                shadowElevation = 24.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp)
                ) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally)
                            .width(38.dp).height(4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Bg.OutlineStrong.copy(alpha = 0.45f))
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("课表设置", "Timetable settings"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Text.Primary)
                            Text(tr("按你的学校作息排时间轴", "Match your school schedule"), fontSize = 11.sp, color = Text.Muted)
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = tr("关闭", "Close"), tint = Text.Muted)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        NumberStepper(tr("每天节数", "Periods / day"), "$periods " + tr("节", ""), { if (periods > 1) periods -= 1 }, { if (periods < 16) periods += 1 })
                        NumberStepper(tr("每节时长", "Lesson length"), "$lesson " + tr("分", "min"), { if (lesson > 20) lesson -= 5 }, { if (lesson < 180) lesson += 5 })
                        NumberStepper(tr("课间休息", "Break"), "$breakMin " + tr("分", "min"), { if (breakMin >= 5) breakMin -= 5 }, { if (breakMin < 40) breakMin += 5 })
                        Text(
                            tr("下面可直接改每一节；改完点「按上方规则重排」按节数和时长自动铺一遍", "Edit each period below, or auto-fill using the rules above"),
                            fontSize = 10.sp, color = Text.Muted,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("每节课时间", "Period times"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Text.Primary)
                                Text(
                                    tr("可直接改每一节的上课 / 下课时间", "Edit start / end for every period"),
                                    fontSize = 10.sp, color = Text.Muted
                                )
                            }
                            TextButton(onClick = {
                                val draft = draftSettings(periods)
                                periodTimes = (1..periods).map { p ->
                                    draft.autoSlot(p).let {
                                        EditablePeriodTime(CourseTableSettings.hhmm(it.first), CourseTableSettings.hhmm(it.second))
                                    }
                                }
                                slotError = false
                            }) {
                                Text(tr("按上方规则重排", "Auto-fill"), color = Brand.Terracotta, fontSize = 11.sp)
                            }
                        }
                        periodTimes.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    tr("第 ${index + 1} 节", "P${index + 1}"),
                                    fontSize = 12.sp,
                                    color = Text.Secondary,
                                    modifier = Modifier.weight(1f)
                                )
                                PeriodTimeField(item.start) { value ->
                                    periodTimes = periodTimes.toMutableList().also { it[index] = item.copy(start = value) }
                                    slotError = false
                                }
                                Text("–", fontSize = 12.sp, color = Text.Muted, modifier = Modifier.padding(horizontal = 6.dp))
                                PeriodTimeField(item.end) { value ->
                                    periodTimes = periodTimes.toMutableList().also { it[index] = item.copy(end = value) }
                                    slotError = false
                                }
                            }
                        }
                        if (slotError) Text(
                            tr("每一节的下课时间要晚于上课时间，格式如 08:20", "End time must be after start time, e.g. 08:20"),
                            fontSize = 10.sp, color = Ink.Danger
                        )

                        Text(tr("每周上课天数", "Days per week"), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Text.Secondary, modifier = Modifier.padding(top = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(5, 6, 7).forEach { d ->
                                SelectPill(tr("${d} 天", "$d days"), days == d, Modifier.weight(1f)) { days = d }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        NumberStepper(
                            tr("学期总周数", "Weeks in term"),
                            "$totalWeeks " + tr("周", "weeks"),
                            { if (totalWeeks > 1) totalWeeks -= 1 },
                            { if (totalWeeks < Course.MAX_WEEKS) totalWeeks += 1 }
                        )
                        NumberStepper(
                            tr("午休在第几节后", "Lunch after period"),
                            if (lunchAfter == 0) tr("不设午休", "None") else tr("第 $lunchAfter 节后", "After $lunchAfter"),
                            { if (lunchAfter > 0) lunchAfter -= 1 },
                            { if (lunchAfter < periods) lunchAfter += 1 }
                        )
                        NumberStepper(tr("午休时长", "Lunch length"), "$lunchMin " + tr("分", "min"), { if (lunchMin >= 10) lunchMin -= 10 }, { if (lunchMin < 240) lunchMin += 10 })
                        NumberStepper(
                            tr("晚休在第几节后", "Dinner after period"),
                            if (eveningAfter == 0) tr("不设晚休", "None") else tr("第 $eveningAfter 节后", "After $eveningAfter"),
                            { if (eveningAfter > 0) eveningAfter -= 1 },
                            { if (eveningAfter < periods) eveningAfter += 1 }
                        )
                        NumberStepper(tr("晚休时长", "Dinner length"), "$eveningMin " + tr("分", "min"), { if (eveningMin >= 10) eveningMin -= 10 }, { if (eveningMin < 180) eveningMin += 10 })

                        CourseField(
                            semester,
                            {
                                semester = it
                                dateError = it.isNotBlank() && !runCatching {
                                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).parse(it.trim())
                                }.isSuccess
                            },
                            tr("开学第一周周一（留空=本周）", "First Monday (blank = this week)"),
                            tr("例如 2026-09-07", "e.g. 2026-09-07")
                        )
                        if (dateError) Text(tr("日期格式应为 2026-09-07", "Use yyyy-MM-dd"), fontSize = 10.sp, color = Ink.Danger)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text(tr("取消", "Cancel"), color = Text.Secondary, fontSize = 14.sp) }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                                val sem = if (dateError) current.semesterMonday else semester.trim()
                                val parsedTimes = periodTimes.take(periods).map {
                                    CourseTableSettings.parseTime(it.start) to CourseTableSettings.parseTime(it.end)
                                }
                                val start = parsedTimes.firstOrNull()?.first ?: current.startMinutes
                                val slotsValid = parsedTimes.size == periods && parsedTimes.all { (s, e) ->
                                    s != null && e != null && e > s
                                }
                                if (!slotsValid) {
                                    slotError = true
                                } else {
                                    slotError = false
                                    onSave(
                                        CourseTableSettings(
                                            periodsPerDay = periods,
                                            lessonMinutes = lesson,
                                            breakMinutes = breakMin,
                                            startMinutes = start,
                                            daysPerWeek = days,
                                            lunchAfter = lunchAfter.coerceIn(0, periods),
                                            lunchMinutes = lunchMin,
                                            eveningAfter = eveningAfter.coerceIn(0, periods),
                                            eveningMinutes = eveningMin,
                                            semesterMonday = sem,
                                            totalWeeks = totalWeeks.coerceIn(1, Course.MAX_WEEKS),
                                            periodStartMinutes = parsedTimes.map { it.first!! },
                                            periodEndMinutes = parsedTimes.map { it.second!! }
                                        )
                                    )
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Brand.Terracotta, contentColor = Text.Inverse
                            )
                        ) { Text(tr("保存设置", "Save"), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = Text.Secondary, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(12.dp)).background(Bg.SurfaceVariant).clickable(onClick = onMinus),
                contentAlignment = Alignment.Center
            ) { Text("−", fontSize = 15.sp, color = Text.Primary) }
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Text.Primary)
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(12.dp)).background(Bg.SurfaceVariant).clickable(onClick = onPlus),
                contentAlignment = Alignment.Center
            ) { Text("+", fontSize = 15.sp, color = Text.Primary) }
        }
    }
}

@Composable
private fun CourseImportDialog(report: CourseImporter.Report, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (report.courses.isEmpty()) tr("没识别到课程", "No courses found")
                else tr("识别到 ${report.courses.size} 门课", "Found ${report.courses.size} courses"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(report.note, fontSize = 12.sp, color = Text.Muted)
                Spacer(Modifier.height(10.dp))
                if (report.courses.isEmpty()) {
                    Text(
                        tr("换一张更清晰、正对课表的截图再试，或者手动添加。", "Try a clearer, straight-on screenshot, or add courses manually."),
                        fontSize = 13.sp, color = Text.Muted
                    )
                } else {
                    Column(
                        Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        report.courses.forEach { c ->
                            val periods = if (c.startPeriod == c.endPeriod) {
                                tr("第${c.startPeriod}节", "P${c.startPeriod}")
                            } else {
                                tr("第${c.startPeriod}-${c.endPeriod}节", "P${c.startPeriod}-${c.endPeriod}")
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(weekDayLabel(c.dayOfWeek), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Brand.Terracotta)
                                    Spacer(Modifier.width(8.dp))
                                    Text(periods, fontSize = 12.sp, color = Text.Muted)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        c.name, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                        color = Text.Primary, maxLines = 2, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val extra = listOf(c.teacher, c.location).filter { it.isNotBlank() }
                                if (extra.isNotEmpty()) Text(extra.joinToString(" · "), fontSize = 11.sp, color = Text.Muted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (report.courses.isNotEmpty()) {
                TextButton(onClick = { onConfirm(true) }) {
                    Text(tr("覆盖导入", "Replace"), color = Brand.Terracotta, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            Row {
                if (report.courses.isNotEmpty()) {
                    TextButton(onClick = { onConfirm(false) }) {
                        Text(tr("追加导入", "Append"), color = Brand.Terracotta)
                    }
                }
                TextButton(onClick = onDismiss) { Text(tr("取消", "Cancel")) }
            }
        }
    )
}

@Composable
private fun CourseEditorSheet(
    initial: Course?,
    table: CourseTableSettings,
    week: Int,
    allCourses: List<Course>,
    seedDay: Int,
    seedPeriod: Int,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val maxPeriod = table.safePeriods
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var teacher by remember { mutableStateOf(initial?.teacher ?: "") }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    var dayOfWeek by remember { mutableIntStateOf(initial?.dayOfWeek ?: seedDay) }
    var startPeriod by remember { mutableIntStateOf((initial?.startPeriod ?: seedPeriod).coerceIn(1, maxPeriod)) }
    var endPeriod by remember { mutableIntStateOf((initial?.endPeriod ?: startPeriod).coerceIn(startPeriod, maxPeriod)) }
    var weekMode by remember { mutableIntStateOf(initial?.weekMode ?: Course.WEEK_EVERY) }
    var weekStart by remember { mutableIntStateOf(initial?.weekStart ?: 1) }
    var weekEnd by remember { mutableIntStateOf(initial?.weekEnd ?: table.safeTotalWeeks) }

    // 保存前先看有没有「同星期 + 节次重叠 + 周次可能撞上」的课，只提示不拦截
    // （同一节次的单周课和双周课是合法排法，不该被硬拦）
    val conflicts = run {
        val draft = Course(
            id = initial?.id ?: -1L, name = name, dayOfWeek = dayOfWeek,
            startPeriod = startPeriod, endPeriod = endPeriod,
            weekMode = weekMode, weekStart = weekStart, weekEnd = weekEnd
        )
        allCourses.filter {
            it.id != draft.id && draft.overlapsPeriod(it) && Course.weeksOverlap(draft, it)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.BottomCenter) {
            // 把课表网格压暗一层，玻璃弹层才有"浮起"的层次；底层文字不再与表单文字抢读
            LiquidGlassScrim(Modifier.matchParentSize(), dim = 0.42f)
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 26.dp,
                optics = LiquidGlassStyles.Sheet
            ) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp)
                ) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(4.dp)
                            .clip(RoundedCornerShape(6.dp)).background(Bg.OutlineStrong.copy(alpha = 0.45f))
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (initial == null) tr("添加课程", "Add course") else tr("编辑课程", "Edit course"),
                            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Text.Primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = tr("关闭", "Close"), tint = Text.Muted)
                        }
                    }
                    Text(
                        tr("保存后会自动同步到桌面课表小组件", "Saved courses sync to the home-screen widget"),
                        fontSize = 11.sp, color = Text.Muted
                    )
                    Spacer(Modifier.height(16.dp))

                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CourseField(name, { name = it }, tr("课程名", "Course name"), tr("例如：高等数学", "e.g. Linear Algebra"))
                        CourseField(teacher, { teacher = it }, tr("老师", "Teacher"), tr("可选", "Optional"))
                        CourseField(location, { location = it }, tr("地点", "Room"), tr("可选，例如：教学楼 A301", "e.g. Building A, Room 301"))

                        Spacer(Modifier.height(4.dp))
                        Text(tr("星期", "Day"), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Text.Secondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Course.weekDays.forEachIndexed { index, _ ->
                                SelectPill(
                                    text = weekDayLabel(index + 1),
                                    selected = dayOfWeek == index + 1,
                                    modifier = Modifier.weight(1f)
                                ) { dayOfWeek = index + 1 }
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr("节次（第 $startPeriod – $endPeriod 节）", "Periods ($startPeriod – $endPeriod)"),
                            fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Text.Secondary
                        )
                        Text(tr("开始节次", "Starts at"), fontSize = 10.sp, color = Text.Muted)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            (1..maxPeriod).forEach { p ->
                                SelectPill(tr("第${p}节", "P$p"), startPeriod == p, Modifier.width(58.dp)) {
                                    startPeriod = p
                                    if (endPeriod < p) endPeriod = p
                                }
                            }
                        }
                        Text(tr("结束节次", "Ends at"), fontSize = 10.sp, color = Text.Muted)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            (startPeriod..maxPeriod).forEach { p ->
                                SelectPill(tr("第${p}节", "P$p"), endPeriod == p, Modifier.width(58.dp)) { endPeriod = p }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(tr("上课周次", "Weeks"), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Text.Secondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf(
                                Course.WEEK_EVERY to tr("每周", "All"),
                                Course.WEEK_ODD to tr("单周", "Odd"),
                                Course.WEEK_EVEN to tr("双周", "Even"),
                                Course.WEEK_RANGE to tr("指定", "Range")
                            ).forEach { (mode, label) ->
                                SelectPill(label, weekMode == mode, Modifier.weight(1f)) { weekMode = mode }
                            }
                        }
                        if (weekMode == Course.WEEK_RANGE) {
                            NumberStepper(
                                tr("起始周", "From week"),
                                tr("第 $weekStart 周", "W$weekStart"),
                                { if (weekStart > 1) weekStart -= 1 },
                                { if (weekStart < Course.MAX_WEEKS) weekStart += 1 }
                            )
                            NumberStepper(
                                tr("结束周", "To week"),
                                tr("第 $weekEnd 周", "W$weekEnd"),
                                { if (weekEnd > 1) weekEnd -= 1 },
                                { if (weekEnd < Course.MAX_WEEKS) weekEnd += 1 }
                            )
                        }
                        Text(
                            tr("当前第 $week 周：", "Week $week: ") +
                                (if (Course(
                                        id = -1L, name = "", dayOfWeek = 1, startPeriod = 1,
                                        weekMode = weekMode, weekStart = weekStart, weekEnd = weekEnd
                                    ).isInWeek(week)
                                ) tr("这周有课", "class scheduled")
                                else tr("这周没课", "no class")),
                            fontSize = 10.sp, color = Text.Muted
                        )

                        if (conflicts.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.14f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚠", fontSize = TypeScale.body, color = Ink.Danger)
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        tr("与 ${conflicts.size} 门课时间重叠", "Overlaps ${conflicts.size} course(s)"),
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Text.Primary
                                    )
                                    Text(
                                        conflicts.joinToString("、") { it.name }.take(40),
                                        fontSize = 10.sp, color = Text.Secondary,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onDelete != null) {
                            TextButton(onClick = onDelete) {
                                Text(tr("删除课程", "Delete"), color = Ink.Danger, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text(tr("取消", "Cancel"), color = Text.Secondary, fontSize = 14.sp) }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    onSave(
                                        Course(
                                            id = initial?.id ?: System.currentTimeMillis(),
                                            name = name.trim(),
                                            teacher = teacher.trim(),
                                            location = location.trim(),
                                            dayOfWeek = dayOfWeek,
                                            startPeriod = startPeriod,
                                            endPeriod = endPeriod,
                                            colorIndex = initial?.colorIndex ?: (startPeriod - 1) % Course.barColors.size,
                                            weekMode = weekMode,
                                            weekStart = weekStart,
                                            weekEnd = weekEnd
                                        )
                                    )
                                }
                            },
                            enabled = name.isNotBlank(),
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Brand.Terracotta,
                                contentColor = Text.Inverse,
                                disabledContainerColor = Bg.SurfaceVariant,
                                disabledContentColor = Text.Muted
                            )
                        ) { Text(tr("保存", "Save"), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = Brand.Terracotta
    // 胶囊也走液态玻璃：选中态用赤陶实底，未选中用带折射环的玻璃底
    val bg = if (selected) {
        Modifier.background(glassChipContainer(true))
    } else {
        Modifier.liquidGlassBackground(16.dp, LiquidGlassStyles.Pill)
    }
    Box(
        modifier = modifier.height(38.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(bg)
            .border(1.dp, if (selected) accent.copy(alpha = 0.75f) else Bg.Outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontSize = 12.sp, maxLines = 1,
            color = if (selected) Text.Primary else Text.Secondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun PeriodTimeField(value: String, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .width(76.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .liquidGlassBackground(10.dp, LiquidGlassStyles.Pill)
            .border(1.dp, Bg.Outline, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Text.Primary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CourseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label, fontSize = TypeScale.body, color = Ink.OnGlassSub) },
        placeholder = { Text(placeholder, fontSize = TypeScale.body, color = Ink.OnGlassMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Ink.OnGlass,
            unfocusedTextColor = Ink.OnGlass,
            focusedContainerColor = glassFieldContainer(true),
            unfocusedContainerColor = glassFieldContainer(false),
            focusedBorderColor = Brand.Terracotta,
            unfocusedBorderColor = Bg.Outline,
            focusedLabelColor = Ink.OnGlassSub,
            unfocusedLabelColor = Ink.OnGlassSub,
            focusedPlaceholderColor = Ink.OnGlassMuted,
            unfocusedPlaceholderColor = Ink.OnGlassMuted,
            cursorColor = Brand.Terracotta
        ),
        modifier = modifier.fillMaxWidth()
    )
}

// ---------------------------------------------------------------- 小工具

/** 课程块配色：低饱和粉/橙/黄/绿/蓝/紫，深浅主题下都靠左侧色条区分 */
private val CourseTints = listOf(
    0xFFF2AEB8, 0xFFF7C99A, 0xFFF7E39B, 0xFFB7DFA8,
    0xFFA8D8E8, 0xFFC3BDEB, 0xFFE3B8DE, 0xFFF0C0A2
)

private fun courseTint(index: Int): Color {
    val n = CourseTints.size
    return Color(CourseTints[((index % n) + n) % n])
}

private fun todayDow(): Int {
    val d = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    return if (d == Calendar.SUNDAY) 7 else d - 1
}

private fun nowMinutes(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

private fun weekDayLabel(day: Int): String {
    val i = (day - 1).coerceIn(0, 6)
    return tr(Course.weekDays[i], Course.weekDaysEn[i])
}

private fun weekLabel(c: Course): String = when (c.safeWeekMode) {
    Course.WEEK_ODD -> tr("单周", "Odd weeks")
    Course.WEEK_EVEN -> tr("双周", "Even weeks")
    Course.WEEK_RANGE -> tr("第 ${c.weekFrom}–${c.weekTo} 周", "Weeks ${c.weekFrom}–${c.weekTo}")
    else -> tr("每周", "Every week")
}

private fun shortDate(c: Calendar): String =
    "${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.DAY_OF_MONTH)}"
