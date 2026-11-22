package com.example.my.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.state.AppState
import com.example.my.ui.components.DashedAddButton
import com.example.my.ui.interaction.LiquidSlider
import com.example.my.ui.interaction.TimeRangePicker
import com.example.my.ui.interaction.WeekDateStrip
import com.example.my.ui.interaction.pressBounceScale
import com.example.my.ui.interaction.rememberTapRipple
import com.example.my.ui.interaction.tapRipple
import com.example.my.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.my.ui.i18n.Lang
import com.example.my.ui.i18n.tr

private val planTimeSlots = listOf("清晨", "上午", "中午", "下午", "傍晚", "晚上")
private val planDateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

/** 时段集合 ↔ 连续区间（非连续的旧选择收敛为最小覆盖区间） */
private fun slotsToRange(set: Set<String>): IntRange? {
    val idx = set.mapNotNull { planTimeSlots.indexOf(it).takeIf { i -> i >= 0 } }
    if (idx.isEmpty()) return null
    return idx.min()..idx.max()
}

/** 新建出行计划底部弹窗 */
@Composable
fun PlanSheet(state: AppState, onDismiss: () -> Unit) {
    var planName by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var dayIds by remember { mutableStateOf(listOf(1, 2, 3)) }
    var slots by remember { mutableStateOf<Map<Int, Set<String>>>(emptyMap()) }
    var hotels by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var note by remember { mutableStateOf("") }
    var companions by remember { mutableStateOf(emptyList<String>()) }
    var companionInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    // 通知权限（Android 13+）：创建计划时顺带请求，保证出发提醒可达
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) state.toast(tr("未授予通知权限，出发提醒可能收不到"))
    }

    // 底部抽屉入场：上升到 92% 处停顿 120ms，再落位到 100%
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = 560
                0.92f at 280
                0.92f at 400
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Func.Scrim)
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = (1f - entrance.value) * size.height
                }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Bg.Surface)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // 顶部拖拽条 + 标题 + 关闭
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
                Text(tr("新建出行计划"), color = Text.Primary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Bg.SurfaceVariant)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, tr("关闭"), tint = Text.Secondary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(18.dp))

            // 计划名称
            PlanField(label = tr("计划名称"), required = true) {
                PlanInput(planName, tr("例如：川西环线7日游")) { planName = it }
            }
            Spacer(Modifier.height(14.dp))

            // 目的地
            PlanField(label = tr("目的地")) {
                PlanInput(destination, tr("成都、稻城、亚丁")) { destination = it }
            }
            Spacer(Modifier.height(14.dp))

            // 出发日期：横向周日期条，选中背景滑动、左右整周翻页、月份标题同步
            PlanField(label = tr("出发日期"), required = true) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Bg.SurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    WeekDateStrip(
                        selectedMillis = startDate.takeIf { it.isNotBlank() }
                            ?.let { runCatching { planDateFmt.parse(it)?.time }.getOrNull() },
                        onSelect = { millis -> startDate = planDateFmt.format(Date(millis)) }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            // 行程时间线
            Text(tr("行程时间线"), color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            LiquidSlider(
                value = dayIds.size,
                onValueChange = { n ->
                    dayIds = (1..n).toList()
                    slots = slots.filterKeys { it <= n }
                },
                range = 1..10,
                title = tr("行程天数"),
                unit = tr(" 天", " days"),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Spacer(Modifier.height(6.dp))
            dayIds.forEachIndexed { index, dayId ->
                PlanDayCard(
                    dayIndex = index + 1,
                    selectedSlots = slots[dayId] ?: emptySet(),
                    onSetSlots = { newSet -> slots = slots + (dayId to newSet) },
                    onDelete = {
                        if (dayIds.size > 1) {
                            dayIds = dayIds.filterNot { it == dayId }
                            slots = slots - dayId
                        } else {
                            state.toast(tr("至少保留一天行程"))
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
            }
            DashedAddButton(tr("+ 添加一天"), onClick = {
                dayIds = dayIds + ((dayIds.maxOrNull() ?: 0) + 1)
            })

            Spacer(Modifier.height(20.dp))

            // 住宿推荐
            Text(tr("住宿推荐"), color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            hotels.forEachIndexed { index, entry ->
                PlanInput(entry.first, tr("酒店/民宿名称")) { v ->
                    hotels = hotels.toMutableList().also { it[index] = v to entry.second }
                }
                Spacer(Modifier.height(8.dp))
                PlanInput(entry.second, tr("备注（位置/价格/亮点）")) { v ->
                    hotels = hotels.toMutableList().also { it[index] = entry.first to v }
                }
                Spacer(Modifier.height(10.dp))
            }
            DashedAddButton(tr("+ 添加一条"), onClick = { hotels = hotels + ("" to "") })

            Spacer(Modifier.height(20.dp))

            // 同行人：可手动输入，也可直接点常用联系人
            Text(tr("同行人"), color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (companions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    companions.forEach { name ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(Brand.Terracotta.copy(alpha = 0.10f))
                                .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, color = Brand.Terracotta, fontSize = 12.sp)
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Close,
                                tr("移除同行人", "Remove companion"),
                                tint = Brand.Terracotta.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable { companions = companions - name }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    PlanInput(companionInput, tr("输入同行人姓名")) { companionInput = it }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Bg.SurfaceVariant)
                        .clickable {
                            val name = companionInput.trim()
                            when {
                                name.isBlank() -> state.toast(tr("请输入姓名"))
                                name in companions -> state.toast(tr("已在同行人中"))
                                else -> {
                                    companions = companions + name
                                    companionInput = ""
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, tr("添加同行人"), tint = Brand.Terracotta, modifier = Modifier.size(20.dp))
                }
            }
            if (state.commonCompanions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(tr("常用联系人"), color = Text.Muted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.commonCompanions.forEach { name ->
                        val selected = name in companions
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(if (selected) Brand.Terracotta.copy(alpha = 0.12f) else Bg.SurfaceVariant)
                                .clickable {
                                    companions = if (selected) companions - name else companions + name
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selected) {
                                Icon(Icons.Filled.Check, null, tint = Brand.Terracotta, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                name,
                                color = if (selected) Brand.Terracotta else Text.Secondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // 备注
            Text(tr("备注"), color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Bg.SurfaceVariant)
            ) {
                TextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    placeholder = { Text(tr("想记住的事、要带的装备、住宿偏好 …"), color = Text.Muted, fontSize = 13.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Text.Primary, fontSize = 13.sp, lineHeight = 19.sp),
                    colors = planFieldColors()
                )
            }

            Spacer(Modifier.height(24.dp))

            // 创建按钮（按压回弹 + 水波）
            val createInteraction = remember { MutableInteractionSource() }
            val createRipple = rememberTapRipple()
            val createScale = pressBounceScale(createInteraction, 0.97f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(createScale)
                    .clip(RoundedCornerShape(50.dp))
                    .background(Brush.linearGradient(MainGradient))
                    .tapRipple(createRipple) {
                        when {
                            planName.isBlank() -> state.toast(tr("请填写计划名称"))
                            startDate.isBlank() -> state.toast(tr("请选择出发日期"))
                            else -> {
                                // 把每天的时段与住宿保存进详情页，避免用户填完后「每日安排」仍是空的
                                val dailyPlan = dayIds.mapIndexedNotNull { index, dayId ->
                                    val chosen = slots[dayId].orEmpty()
                                    if (chosen.isEmpty()) null
                                    else "D${index + 1}｜" + planTimeSlots.filter { it in chosen }.joinToString("、")
                                }
                                val hotelLines = hotels.mapNotNull { (name, desc) ->
                                    listOf(name.trim(), desc.trim())
                                        .filter { it.isNotEmpty() }
                                        .joinToString(" · ")
                                        .takeIf { it.isNotEmpty() }
                                }
                                val fullNote = buildString {
                                    if (note.isNotBlank()) append(note.trim())
                                    if (hotelLines.isNotEmpty()) {
                                        if (isNotEmpty()) append('\n')
                                        append(tr("住宿：", "Stay: ")).append(hotelLines.joinToString("；"))
                                    }
                                }
                                state.addTripPlan(
                                    planName, destination, startDate, dayIds.size, fullNote,
                                    dailyPlan = dailyPlan,
                                    companions = companions
                                )
                                // Android 13+ 请求通知权限（不阻塞创建，未授权只提示）
                                if (Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                    != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                state.toast(tr("计划已创建，出发前 1 天会提醒你"))
                                onDismiss()
                            }
                        }
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tr("创建计划"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PlanDayCard(
    dayIndex: Int,
    selectedSlots: Set<String>,
    onSetSlots: (Set<String>) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Bg.SurfaceVariant)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (Lang.isEn) "Day $dayIndex · Plan" else "Day $dayIndex · 行程",
                color = Text.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = tr("删除"),
                color = Brand.Terracotta,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onDelete)
            )
        }
        Spacer(Modifier.height(10.dp))
        // 拖动选时间段：按住连选合并成条，两端把手微调
        TimeRangePicker(
            slots = planTimeSlots,
            selected = slotsToRange(selectedSlots),
            onChange = { range ->
                onSetSlots(range?.let { planTimeSlots.slice(it).toSet() } ?: emptySet())
            }
        )
    }
}

@Composable
internal fun PlanField(label: String, required: Boolean = false, content: @Composable () -> Unit) {
    Column {
        Row {
            Text(label, color = Text.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (required) {
                Text(" *", color = Brand.Terracotta, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
internal fun PlanInput(value: String, placeholder: String, readOnly: Boolean = false, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Bg.SurfaceVariant)
            .padding(horizontal = 14.dp, vertical = 2.dp)
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            readOnly = readOnly,
            placeholder = { Text(placeholder, color = Text.Muted, fontSize = 14.sp) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Text.Primary, fontSize = 14.sp),
            colors = planFieldColors()
        )
    }
}

@Composable
internal fun planFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = Brand.Terracotta
)
