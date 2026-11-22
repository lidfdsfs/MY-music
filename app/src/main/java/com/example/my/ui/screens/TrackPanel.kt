package com.example.my.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.AiApi
import com.example.my.data.AiFeature
import com.example.my.data.AiPresets
import com.example.my.data.Locator
import com.example.my.model.PendingRouteMark
import com.example.my.state.AppState
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.MainGradient
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import com.example.my.ui.i18n.tr
import com.example.my.ui.components.rememberIsResumed

/**
 * 足迹页顶部的自动轨迹记录条：
 * 开启后每 5 秒采一点（精度差时按精度放大位移门槛），实时画在地图上，
 * 结束行程可一键把轨迹点批量转成正式足迹。
 *
 * 记录期间挂一个 location 型前台服务，**锁屏 / 切后台 / 被系统回收后重启都会继续采点**；
 * 状态行会显示点数、里程、当前定位精度，掉信号时直接标「GPS 信号弱」。
 */
@Composable
fun TrackPanel(state: AppState) {
    val context = LocalContext.current
    /** 放弃本次记录的二次确认 */
    var confirmDiscard by remember { mutableStateOf(false) }

    fun beginTracking() {
        if (state.startTracking()) {
            state.toast(tr("已开始记录轨迹"))
            return
        }
        // 失败原因分开说：没权限要去授权，定位开关关着要去系统设置，笼统一句「失败」没法处理
        state.toast(
            when (state.trackError) {
                Locator.REASON_PERMISSION -> tr("未授予定位权限，无法记录轨迹")
                Locator.REASON_SERVICE_OFF -> tr("系统定位已关闭，请在设置中打开「位置信息」")
                else -> tr("请检查定位权限与 GPS 开关")
            }
        )
    }

    val granted = Locator.hasAny(context)

    // ---------- 路线足迹：起点图 / 途中标记 / 结束时的终点图与标题 ----------

    /** 起点照片（必填）：开始记录前先选，选完才真正开始采点 */
    var startPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val startPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            state.toast(tr("起点照片是路线记录的必填项，选一张才能开始"))
            return@rememberLauncherForActivityResult
        }
        startPhotoUri = uri
        beginTracking()
    }

    fun pickStartPhoto() {
        startPhotoLauncher.launch("image/*")
    }

    /** 途中标记（可选）：位置取当前最后一个轨迹点，可顺手带一张图 */
    var marks by remember { mutableStateOf(listOf<PendingRouteMark>()) }
    var markDialog by remember { mutableStateOf(false) }
    var markNote by remember { mutableStateOf("") }
    var markPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val markPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) markPhotoUri = uri
    }

    /** 结束面板（终点照片必填 + 标题必填，标题可以让 AI 想） */
    var finishDialog by remember { mutableStateOf(false) }
    var endPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var finishTitle by remember { mutableStateOf("") }
    var aiTitles by remember { mutableStateOf(emptyList<String>()) }
    var aiTitlesLoading by remember { mutableStateOf(false) }
    val endPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) endPhotoUri = uri
    }
    val scope = rememberCoroutineScope()

    // 精确 + 大致位置一起要：只有 COARSE 时轨迹会粗到几百米，自动记录就失去意义了
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val ok = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) pickStartPhoto() else state.toast(tr("未授予定位权限，无法记录轨迹"))
    }

    val km = state.trackDistanceKm()
    val hasPending = state.trackPoints.isNotEmpty() && !state.trackingOn

    // 记录中每秒刷新一次：时长要像秒表一样往上走，同时「多久没收到定位」也能立刻看出来。
    // 只有页面在前台才刷新；轨迹本身由 TrackForegroundService 继续记录，不受影响。
    var tick by remember { mutableStateOf(0L) }
    val panelResumed = rememberIsResumed()
    LaunchedEffect(state.trackingOn, panelResumed) {
        if (!state.trackingOn || !panelResumed) return@LaunchedEffect
        tick = System.currentTimeMillis()
        while (state.trackingOn && panelResumed) {
            delay(1_000)
            tick = System.currentTimeMillis()
        }
    }
    // tick 作为 key：每 1 秒强制重算一次「多久没定位」，否则状态行会停在旧值
    val idleSec = remember(tick) { state.trackIdleSeconds() }
    val durationSec = remember(tick) { state.trackDurationSeconds() }

    val pts = state.trackPoints.size
    val kmText = String.format(Locale.CHINA, "%.2f", km)
    val accValue = state.trackAccuracy?.takeIf { it > 0f }
    val accText = accValue?.let { "±${it.roundToInt()} m" }
    // 精度差于 35 米的点会被过滤掉，这时要告诉用户「不是没在记，是信号不行」
    val weakSignal = idleSec > 30L || (accValue != null && accValue > 35f)

    /**
     * 记录中的状态行：
     * 刚开启还没拿到第一帧 → 搜星；精度差 / 长时间没新点 → 信号弱；
     * 正常就给点数 / 里程 / 精度 / 速度，用户一眼能判断这条轨迹靠不靠谱。
     */
    val statusText: String = when {
        state.trackingOn -> {
            val base = tr("$pts 个点 · $kmText km", "$pts pts · $kmText km")
            when {
                accText == null -> tr("$base · 正在搜星…", "$base · searching…")
                weakSignal -> tr("$base · GPS 信号弱 $accText", "$base · weak GPS $accText")
                else -> {
                    val speed = state.trackSpeedKmh
                        .takeIf { it >= 1.0 }
                        ?.let { String.format(Locale.CHINA, "%.0f km/h", it) }
                    if (speed != null) "$base · $accText · $speed" else "$base · $accText"
                }
            }
        }
        // 进程被杀后重启：点还在但采点早已停止，必须说清楚，不能让人以为还在记录
        hasPending && state.trackInterrupted ->
            tr("已中断 · 还有 $pts 个点", "Interrupted · $pts pts left")
        hasPending -> tr("上次记录还有 $pts 个点未处理", "$pts unprocessed points from last recording")
        else -> tr("开启后自动打点 · 锁屏也会继续记录")
    }
    val statusColor = if (state.trackingOn && weakSignal) Brand.Terracotta else Text.Muted

    // 离开足迹页（切 Tab / 旋转）时补一次落盘，避免节流窗口内的点丢在内存里
    DisposableEffect(Unit) {
        onDispose { state.flushTrack() }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = Bg.Surface,
            title = {
                Text(
                    tr("放弃本次记录？"),
                    color = Text.Primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    tr("已采集的 ${state.trackPoints.size} 个轨迹点会被清除，无法恢复（已生成的足迹不受影响）。", "${state.trackPoints.size} recorded points will be cleared permanently (existing footprints are not affected)."),
                    color = Text.Muted,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    state.clearTrack()
                    state.toast(tr("已放弃本次记录"))
                }) {
                    Text(tr("放弃"), color = Brand.Terracotta, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(tr("取消"), color = Text.Muted, fontSize = 13.sp)
                }
            }
        )
    }

    // 途中标记：位置取当前最后一个轨迹点，备注与照片都可选
    if (markDialog) {
        AlertDialog(
            onDismissRequest = { markDialog = false },
            containerColor = Bg.Surface,
            title = {
                Text(
                    tr("标记当前位置"),
                    color = Text.Primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    Text(
                        tr("位置自动取当前定位，可写备注、加一张照片，都不是必填。"),
                        color = Text.Muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = markNote,
                        onValueChange = { markNote = it },
                        placeholder = { Text(tr("这个位置发生了什么"), color = Text.Muted, fontSize = 12.sp) },
                        textStyle = TextStyle(color = Text.Primary, fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (markPhotoUri != null) tr("已选照片，点这里可重选") else tr("＋ 添加照片（可选）"),
                        color = Brand.Terracotta,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Brand.Terracotta.copy(alpha = 0.10f))
                            .clickable { markPhotoLauncher.launch("image/*") }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val point = state.trackPoints.lastOrNull() ?: return@TextButton
                    marks = marks + PendingRouteMark(
                        lat = point.lat,
                        lon = point.lon,
                        note = markNote.trim().ifBlank { tr("标记点") },
                        photoUri = markPhotoUri,
                        time = point.time
                    )
                    markDialog = false
                    state.toast(tr("已标记第 ${marks.size} 处"))
                }) {
                    Text(tr("标记"), color = Brand.Terracotta, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { markDialog = false }) {
                    Text(tr("取消"), color = Text.Muted, fontSize = 13.sp)
                }
            }
        )
    }

    // 结束：时间自动匹配，终点照片与标题必填，标题可以让 AI 想
    if (finishDialog) {
        val startedAt = state.trackPoints.firstOrNull()?.time
        val endedAt = state.trackPoints.lastOrNull()?.time
        val dateText = startedAt?.let {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(it))
        }.orEmpty()
        val endTimeText = endedAt?.let {
            SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(it))
        }.orEmpty()

        AlertDialog(
            onDismissRequest = { finishDialog = false },
            containerColor = Bg.Surface,
            title = {
                Text(
                    tr("保存为一条足迹"),
                    color = Text.Primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    Text(
                        tr("$dateText 出发 · 结束 $endTimeText · $kmText km · 用时 ${formatTrackDuration(durationSec)}"),
                        color = Text.Muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))

                    // 终点照片（必填）
                    Text(
                        text = if (endPhotoUri != null) tr("已选终点照片，点这里可重选") else tr("＋ 选择终点照片（必填）"),
                        color = Brand.Terracotta,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Brand.Terracotta.copy(alpha = 0.10f))
                            .clickable { endPhotoLauncher.launch("image/*") }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = finishTitle,
                        onValueChange = { finishTitle = it },
                        placeholder = { Text(tr("给这趟路起个名字"), color = Text.Muted, fontSize = 12.sp) },
                        textStyle = TextStyle(color = Text.Primary, fontSize = 13.sp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = if (aiTitlesLoading) tr("AI 正在想标题…") else tr("✨ 问问 AI 帮我想标题"),
                        color = Brand.Terracotta,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .clickable(enabled = !aiTitlesLoading) {
                                aiTitlesLoading = true
                                scope.launch {
                                    val facts = buildString {
                                        append("出发时间：").append(dateText)
                                        append("；距离：").append(kmText).append(" 公里")
                                        append("；用时：").append(formatTrackDuration(durationSec))
                                        if (marks.isNotEmpty()) {
                                            append("；途中标记：").append(marks.map { it.note }.joinToString("、"))
                                        }
                                    }
                                    aiTitles = runCatching {
                                        AiApi.suggestRouteTitles(
                                            state.aiFeatureEndpoint(AiFeature.ROUTE_TITLE),
                                            facts,
                                            context,
                                            state.aiFeatureUsesLocal(AiFeature.ROUTE_TITLE),
                                            state.aiFeatureLocalModelId(AiFeature.ROUTE_TITLE)
                                        )
                                    }.getOrDefault(emptyList())
                                    aiTitlesLoading = false
                                    if (aiTitles.isEmpty()) {
                                        state.toast(tr("AI 暂时没想出来，先自己写一个试试"))
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                    aiTitles.forEach { title ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "· $title",
                            color = Text.Primary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { finishTitle = title }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (endPhotoUri == null) {
                        state.toast(tr("终点照片必填，选一张再保存"))
                        return@TextButton
                    }
                    if (finishTitle.isBlank()) {
                        state.toast(tr("给这趟路写个标题再保存"))
                        return@TextButton
                    }
                    val ok = state.finishTrackAsRoute(finishTitle, startPhotoUri, endPhotoUri, marks)
                    finishDialog = false
                    if (ok) {
                        state.toast(tr("已保存为足迹《${finishTitle.trim()}》"))
                        // 清掉本轮状态，下次记录从干净的起点图开始
                        marks = emptyList()
                        startPhotoUri = null
                        endPhotoUri = null
                        finishTitle = ""
                        aiTitles = emptyList()
                    } else {
                        state.toast(tr("没有采集到轨迹点，无法保存"))
                    }
                }) {
                    Text(tr("保存"), color = Brand.Terracotta, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { finishDialog = false }) {
                    Text(tr("继续记录"), color = Text.Muted, fontSize = 13.sp)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Bg.Surface)
            .cardShadow(RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.trackingOn) Brand.Terracotta.copy(alpha = 0.14f)
                        else Bg.SurfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.MyLocation,
                    null,
                    tint = if (state.trackingOn) Brand.Terracotta else Text.Muted,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        state.trackingOn -> tr("正在记录轨迹")
                        hasPending && state.trackInterrupted -> tr("上次记录已中断")
                        else -> tr("自动记录轨迹")
                    },
                    color = Text.Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                // 记录中状态行移到统计数字下面，这里只在空闲态显示
                if (!state.trackingOn) {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                        // 状态行信息量大（点数/里程/精度），英文更长，压成一行不准折行顶开卡片
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))

            if (state.trackingOn) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 途中标记：位置取当前最后一个轨迹点，可顺手带一张图
                    Text(
                        text = tr("标记"),
                        color = Brand.Terracotta,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Brand.Terracotta.copy(alpha = 0.10f))
                            .clickable {
                                if (state.trackPoints.isEmpty()) {
                                    state.toast(tr("正在搜星，等第一个轨迹点再标记"))
                                } else {
                                    markNote = ""
                                    markPhotoUri = null
                                    markDialog = true
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                    Text(
                        text = tr("结束"),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Brush.linearGradient(MainGradient))
                            .clickable {
                                // 不再直接落库：先让用户给终点照片、写标题
                                if (state.trackPoints.isEmpty()) {
                                    state.toast(tr("还没有采集到轨迹点"))
                                } else {
                                    endPhotoUri = null
                                    aiTitles = emptyList()
                                    finishDialog = true
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 误开记录 / 走错路重来时用：清空点集但不生成垃圾足迹
                    if (hasPending) {
                        // 导出 GPX：导入奥维/Strava/Garmin
                        Text(
                            text = tr("导出", "Export"),
                            color = Brand.Terracotta,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(Brand.Terracotta.copy(alpha = 0.10f))
                                .clickable {
                                    val ok = com.example.my.data.TrackRecorder.exportAndShare(
                                        context, state.trackPoints
                                    )
                                    state.toast(
                                        if (ok) tr("已导出 GPX，可分享到地图 App", "GPX exported")
                                        else tr("暂无轨迹点可导出", "No track points")
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = tr("放弃"),
                            color = Text.Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(Bg.SurfaceVariant)
                                .clickable { confirmDiscard = true }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                    Text(
                        text = if (state.trackPoints.isNotEmpty()) tr("继续", "Resume") else tr("开启"),
                        color = Brand.Terracotta,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Brand.Terracotta.copy(alpha = 0.10f))
                            .clickable {
                                // 先要起点照片，选完才真正开始采点（权限回调里也会走到这里）
                                if (granted) pickStartPhoto()
                                else permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // 记录中：运动数据面板（公里 / 时长 / 消耗），像运动 App 那样一眼能看清
        if (state.trackingOn) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TrackStat(tr("公里", "km"), kmText, Modifier.weight(1f))
                TrackStat(tr("时长", "time"), formatTrackDuration(durationSec), Modifier.weight(1f))
                TrackStat(tr("消耗(千卡)", "kcal"), "${state.trackCalories()}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                statusText,
                color = statusColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 运动数据面板里的一列：上方小字标签，下方大号数值 */
@Composable
private fun TrackStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Text.Muted, fontSize = 11.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = Text.Primary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

/** 秒数 → 秒表格式：不足 1 小时用 mm:ss，超过 1 小时用 h:mm:ss */
private fun formatTrackDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.CHINA, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.CHINA, "%02d:%02d", m, s)
}
