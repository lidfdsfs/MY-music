package com.example.my.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.my.model.Packing
import com.example.my.model.TripPlan
import com.example.my.state.AppState
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Func
import com.example.my.ui.theme.MainGradient
import com.example.my.ui.theme.Text
import com.example.my.util.ImageExporter
import com.example.my.util.PlanPoster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.example.my.ui.i18n.Lang
import com.example.my.ui.i18n.tr

private const val PLAN_DETAIL_DAY_MS = 24L * 60 * 60 * 1000

/**
 * 行程详情：两个 Tab。
 * 「行程」= 每日安排 / 同行人 / 提醒设置 / 自动归档 / 归档删除；
 * 「出行清单」= 分类模板 + 自定义物品 + 勾选准备状态。
 */
@Composable
fun PlanDetailSheet(state: AppState, plan: TripPlan, onDismiss: () -> Unit) {
    var tab by remember(plan.id) { mutableIntStateOf(0) }
    var newItem by remember { mutableStateOf("") }
    var newItemCategory by remember { mutableStateOf(Packing.categories.first()) }
    var newCompanion by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var keepTemplate by remember { mutableStateOf(true) }
    var editing by remember(plan.id) { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val grouped = remember(plan.customPacking) { Packing.grouped(plan.customPacking) }
    val allItems = remember(grouped) { grouped.flatMap { it.second } }
    val packedCount = allItems.count { it in plan.packedItems }

    // 通讯录导入：选中联系人后读取显示名
    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            val name = runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
            if (name.isNullOrBlank()) {
                state.toast(tr("未能读取联系人姓名，请检查通讯录权限"))
            } else if (name in plan.companions) {
                state.toast(if (Lang.isEn) "“$name” is already in your companions" else "「$name」已在同行人列表中")
            } else {
                state.updateTripPlan(plan.id) { it.copy(companions = it.companions + name) }
                state.toast(if (Lang.isEn) "Added companion “$name”" else "已添加同行人「$name」")
            }
        }
    }
    val contactPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) contactPicker.launch(null)
        else state.toast(tr("未授予通讯录权限，无法导入联系人"))
    }

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

    fun saveReminder(
        enabled: Boolean = plan.reminderEnabled,
        offsetDays: Int = plan.reminderOffsetDays,
        hour: Int = plan.reminderHour,
        minute: Int = plan.reminderMinute,
        vibrate: Boolean = plan.reminderVibrate,
        sound: Boolean = plan.reminderSound
    ) {
        state.updatePlanReminder(plan.id, enabled, offsetDays, hour, minute, vibrate, sound)
        state.toast(
            if (enabled) "提醒已更新：${if (offsetDays == 0) "当天" else "提前 $offsetDays 天"} " +
                String.format(Locale.CHINA, "%02d:%02d", hour, minute)
            else tr("已关闭该行程的提醒")
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
                .fillMaxHeight(0.92f)
                .graphicsLayer { translationY = (1f - entrance.value) * size.height }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Bg.Surface)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Bg.Outline)
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(16.dp))

            // ---------- 标题 + 导出 / 关闭 ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        plan.name,
                        color = Text.Primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        plan.destination.ifBlank { tr("目的地待定", "Destination TBD") },
                        color = Text.Secondary,
                        fontSize = 12.sp
                    )
                }
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(Bg.SurfaceVariant)
                        .clickable { editing = true }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Edit, tr("编辑行程"), tint = Brand.Terracotta, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr("编辑"), color = Brand.Terracotta, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Bg.SurfaceVariant)
                        .clickable {
                            scope.launch {
                                val bitmap = withContext(Dispatchers.Default) {
                                    PlanPoster.render(listOf(plan), System.currentTimeMillis())
                                }
                                val name = "行程_${plan.name}_${System.currentTimeMillis()}"
                                val saved = withContext(Dispatchers.IO) {
                                    ImageExporter.saveToGallery(context, bitmap, name)
                                }
                                val uri = withContext(Dispatchers.IO) {
                                    ImageExporter.shareUri(context, bitmap, name)
                                }
                                if (uri != null) ImageExporter.share(context, uri)
                                state.toast(if (saved) tr("长图已保存到相册，并打开分享") else tr("长图已生成，已打开分享"))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.FileDownload, tr("导出长图"), tint = Brand.Terracotta, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
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
            Spacer(Modifier.height(12.dp))

            // ---------- 日期 / 天数 / 状态 ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Bg.SurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = Text.Muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    detailDateRange(plan),
                    color = Text.Secondary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(plan.days.toString(), color = Brand.Terracotta, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(tr(" 天"), color = Text.Muted, fontSize = 11.sp)
            }
            if (plan.archived) {
                Spacer(Modifier.height(8.dp))
                Text(tr("已归档 · 仅在「归档」分类中展示"), color = Text.Muted, fontSize = 11.sp)
            }

            Spacer(Modifier.height(16.dp))

            // ---------- Tab 切换 ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50.dp))
                    .background(Bg.SurfaceVariant)
                    .padding(3.dp)
            ) {
                listOf(tr("行程"),
                        tr("出行清单 $packedCount/${allItems.size}", "Checklist $packedCount/${allItems.size}")).forEachIndexed { index, label ->
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
                            .clickable { tab = index }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (selected) Color.White else Text.Secondary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            if (tab == 0) {
                // ==================== 行程 Tab ====================

                // 同行人
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("同行人"), color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (plan.companions.isEmpty()) tr("还没有同行人", "No companions yet")
                        else tr("${plan.companions.size} 人", "${plan.companions.size} people"),
                        color = Text.Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        tr("存为常用"),
                        color = Brand.Terracotta,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clickable {
                                if (plan.companions.isEmpty()) {
                                    state.toast(tr("先添加同行人再保存"))
                                } else {
                                    plan.companions.forEach { state.addCommonCompanion(it) }
                                    state.toast(tr("已保存为常用联系人"))
                                }
                            }
                            .padding(4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (plan.companions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        plan.companions.forEach { name ->
                            CompanionChip(name, onRemove = {
                                state.updateTripPlan(plan.id) { p ->
                                    p.copy(companions = p.companions - name)
                                }
                            })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        PlanInput(newCompanion, tr("输入同行人姓名")) { newCompanion = it }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(MainGradient))
                            .clickable {
                                val name = newCompanion.trim()
                                if (name.isBlank()) {
                                    state.toast(tr("请输入姓名"))
                                } else if (name in plan.companions) {
                                    state.toast(tr("已在同行人中"))
                                } else {
                                    state.updateTripPlan(plan.id) { it.copy(companions = it.companions + name) }
                                    newCompanion = ""
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Add, tr("添加同行人"), tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Bg.SurfaceVariant)
                            .clickable {
                                // 通讯录读取需要权限；已授权则直接打开联系人选择器
                                if (Build.VERSION.SDK_INT >= 23 &&
                                    ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.READ_CONTACTS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    contactPermLauncher.launch(Manifest.permission.READ_CONTACTS)
                                } else {
                                    contactPicker.launch(null)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.PersonAdd, tr("从通讯录导入"), tint = Text.Secondary, modifier = Modifier.size(20.dp))
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
                        state.commonCompanions.filterNot { it in plan.companions }.forEach { name ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(Bg.SurfaceVariant)
                                    .clickable {
                                        state.updateTripPlan(plan.id) { it.copy(companions = it.companions + name) }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(name, color = Text.Secondary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // 每日安排
                Spacer(Modifier.height(22.dp))
                Text(tr("每日安排"), color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                if (plan.dailyPlan.isEmpty()) {
                    Text(
                        tr("还没有每日安排。可以用「AI 生成行程」自动排好，或新建行程时手动填写。"),
                        color = Text.Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                } else {
                    plan.dailyPlan.forEach { line ->
                        // 解析：D1｜主题：09:00 活动 / 12:00 活动 / ...
                        val (dayTitle, body) = run {
                            val colonIdx = line.indexOf('：').let { if (it < 0) line.indexOf(':') else it }
                            if (colonIdx > 0) line.substring(0, colonIdx) to line.substring(colonIdx + 1)
                            else line to ""
                        }
                        val items = body.split(" / ", " /", "/").map { it.trim() }.filter { it.isNotBlank() }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Bg.SurfaceVariant)
                                .padding(14.dp)
                        ) {
                            // Day 标题行：D1 徽章 + 主题
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val dayLabel = dayTitle.substringBefore('｜').substringBefore('|').trim()
                                val theme = dayTitle.substringAfter('｜', dayTitle).substringAfter('|', dayTitle).trim()
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF2DD4BF).copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(dayLabel, color = Color(0xFF0D9488), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(8.dp))
                                if (theme.isNotBlank()) {
                                    Text(
                                        theme,
                                        color = Text.Primary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            if (items.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                items.forEach { item ->
                                    // 拆时间前缀
                                    val timeMatch = Regex("""^(\d{1,2}:\d{2})""").find(item.trim())
                                    val time = timeMatch?.value ?: ""
                                    val rest = if (timeMatch != null) item.substring(timeMatch.range.last + 1).trim() else item
                                    Row(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        // 时间轴竖线 + 圆点
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(Color(0xFF2DD4BF))
                                            )
                                            if (item != items.last()) {
                                                Spacer(Modifier.height(2.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .width(1.dp)
                                                        .height(22.dp)
                                                        .background(Color(0xFF2DD4BF).copy(alpha = 0.3f))
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            if (time.isNotBlank()) {
                                                Text(
                                                    time,
                                                    color = Color(0xFF0D9488),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Text(
                                                rest,
                                                color = Text.Secondary,
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(Modifier.height(6.dp))
                                Text(line, color = Text.Secondary, fontSize = 12.sp, lineHeight = 19.sp)
                            }
                        }
                    }
                }

                if (plan.note.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(tr("备注"), color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(plan.note, color = Text.Muted, fontSize = 12.sp, lineHeight = 18.sp)
                }

                // ---------- 提醒设置 ----------
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("出发提醒"), color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (plan.reminderEnabled) tr("自定义时间") else tr("已关闭"),
                        color = Text.Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = plan.reminderEnabled,
                        onCheckedChange = { saveReminder(enabled = it) }
                    )
                }
                if (plan.reminderEnabled) {
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Bg.SurfaceVariant)
                            .padding(12.dp)
                    ) {
                        StepperRow(
                            title = tr("提前天数"),
                            value = if (plan.reminderOffsetDays == 0) tr("当天") else tr("${plan.reminderOffsetDays} 天", "${plan.reminderOffsetDays} days"),
                            onMinus = { saveReminder(offsetDays = (plan.reminderOffsetDays - 1).coerceAtLeast(0)) },
                            onPlus = { saveReminder(offsetDays = (plan.reminderOffsetDays + 1).coerceAtMost(30)) }
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            tr("提醒时刻 ") + String.format(
                                Locale.CHINA, "%02d:%02d", plan.reminderHour, plan.reminderMinute
                            ),
                            color = Text.Secondary,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        StepperRow(
                            title = tr("小时"),
                            value = tr("%02d 时", "%02d h").format(plan.reminderHour),
                            onMinus = { saveReminder(hour = (plan.reminderHour + 23) % 24) },
                            onPlus = { saveReminder(hour = (plan.reminderHour + 1) % 24) }
                        )
                        Spacer(Modifier.height(8.dp))
                        StepperRow(
                            title = tr("分钟"),
                            value = tr("%02d 分", "%02d m").format(plan.reminderMinute),
                            onMinus = { saveReminder(minute = (plan.reminderMinute + 59) % 60) },
                            onPlus = { saveReminder(minute = (plan.reminderMinute + 1) % 60) }
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(tr("提醒方式"), color = Text.Secondary, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToggleChip(tr("震动"), plan.reminderVibrate) {
                                saveReminder(vibrate = !plan.reminderVibrate)
                            }
                            ToggleChip(tr("铃声"), plan.reminderSound) {
                                saveReminder(sound = !plan.reminderSound)
                            }
                            ToggleChip(tr("系统通知"), true, enabled = false) {}
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr("自动归档"), color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            tr("行程结束 ${AppState.AUTO_ARCHIVE_DAYS} 天后自动移入归档", "Auto-archived ${AppState.AUTO_ARCHIVE_DAYS} days after the trip ends"),
                            color = Text.Muted,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = plan.autoArchive,
                        onCheckedChange = { on ->
                            state.updateTripPlan(plan.id) { it.copy(autoArchive = on) }
                        }
                    )
                }
            } else {
                // ==================== 出行清单 Tab ====================

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("出行清单"), color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tr("已准备 $packedCount / ${allItems.size}", "$packedCount / ${allItems.size} packed"),
                        color = Text.Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        tr("全部勾选"),
                        color = Brand.Terracotta,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clickable {
                                state.updateTripPlan(plan.id) { p ->
                                    p.copy(packedItems = allItems)
                                }
                            }
                            .padding(4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Bg.Outline)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(
                                if (allItems.isEmpty()) 0f
                                else packedCount.toFloat() / allItems.size
                            )
                            .fillMaxHeight()
                            .background(Brush.linearGradient(MainGradient))
                    )
                }
                Spacer(Modifier.height(14.dp))

                grouped.forEach { (category, keys) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(category, color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${keys.count { it in plan.packedItems }}/${keys.size}",
                            color = Text.Muted,
                            fontSize = 10.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    keys.forEach { key ->
                        PackingRow(
                            label = Packing.label(key),
                            checked = key in plan.packedItems,
                            onToggle = {
                                state.updateTripPlan(plan.id) { p ->
                                    p.copy(
                                        packedItems = if (key in p.packedItems) p.packedItems - key
                                        else p.packedItems + key
                                    )
                                }
                            },
                            onRemove = if (key in plan.customPacking) {
                                {
                                    state.updateTripPlan(plan.id) { p ->
                                        p.copy(
                                            customPacking = p.customPacking - key,
                                            packedItems = p.packedItems - key
                                        )
                                    }
                                }
                            } else null
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // 添加自定义物品
                Text(tr("添加物品"), color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Packing.categories.forEach { category ->
                        ToggleChip(category, newItemCategory == category) { newItemCategory = category }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        PlanInput(newItem, tr("添加物品，如 登山杖")) { newItem = it }
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(MainGradient))
                            .clickable {
                                val name = newItem.trim()
                                val key = Packing.key(newItemCategory, name)
                                if (name.isBlank()) {
                                    state.toast(tr("请输入物品名称"))
                                } else if (key in allItems) {
                                    state.toast(tr("「$newItemCategory」里已有「$name」", "$name is already in $newItemCategory"))
                                } else {
                                    state.updateTripPlan(plan.id) { p ->
                                        p.copy(customPacking = p.customPacking + key)
                                    }
                                    newItem = ""
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Add, tr("添加物品"), tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---------- 归档 / 删除 ----------
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailAction(
                    modifier = Modifier.weight(1f),
                    icon = if (plan.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                    label = if (plan.archived) tr("恢复行程") else tr("归档行程"),
                    tint = Text.Secondary,
                    background = Bg.SurfaceVariant
                ) {
                    // plan 是本次组合的旧值，必须按操作前的状态判断提示，否则文案会相反
                    val wasArchived = plan.archived
                    state.togglePlanArchived(plan.id)
                    state.toast(if (wasArchived) tr("已恢复行程") else tr("已归档，可在「归档」中找回"))
                }
                DetailAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.DeleteOutline,
                    label = if (confirmDelete) tr("确认删除") else tr("删除计划"),
                    tint = if (confirmDelete) Func.Danger else Text.Muted,
                    background = if (confirmDelete) Func.Danger.copy(alpha = 0.10f) else Bg.SurfaceVariant
                ) {
                    if (confirmDelete) {
                        state.deleteTripPlan(plan.id, keepTemplate)
                        state.toast(if (keepTemplate) tr("计划已删除，清单模板已保留") else tr("计划已删除"))
                        onDismiss()
                    } else {
                        confirmDelete = true
                    }
                }
            }
            if (confirmDelete) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Bg.SurfaceVariant)
                        .clickable { keepTemplate = !keepTemplate }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = keepTemplate, onCheckedChange = { keepTemplate = it })
                    Text(
                        tr("保留出行清单模板，下次新建行程自动带上"),
                        color = Text.Secondary,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (editing) {
            PlanEditorSheet(
                state = state,
                plan = plan,
                onDismiss = { editing = false }
            )
        }
    }
}

/** 编辑已生成/已保存的行程：标题、目的地、日期、说明与每日安排都能改 */
@Composable
private fun PlanEditorSheet(
    state: AppState,
    plan: TripPlan,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    var name by remember(plan.id) { mutableStateOf(plan.name) }
    var destination by remember(plan.id) { mutableStateOf(plan.destination) }
    var startDate by remember(plan.id) { mutableStateOf(plan.startDate) }
    var note by remember(plan.id) { mutableStateOf(plan.note) }
    var dailyPlan by remember(plan.id) { mutableStateOf(plan.dailyPlan) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Func.Scrim)
            .clickable { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Bg.Surface)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("编辑行程"),
                    color = Text.Primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Bg.SurfaceVariant)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, tr("关闭"), tint = Text.Secondary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(tr("行程标题"), fontSize = 11.sp) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = Text.Primary),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text(tr("目的地"), fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = Text.Primary),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text(tr("出发日期"), fontSize = 11.sp) },
                    placeholder = { Text("2026-11-13", fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = Text.Primary),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                tr("日期格式：yyyy-MM-dd；共 ${if (dailyPlan.isEmpty()) plan.days else dailyPlan.size} 天"),
                color = Text.Muted,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(tr("行程说明"), fontSize = 11.sp) },
                textStyle = TextStyle(fontSize = 13.sp, color = Text.Primary),
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("每日安排"), color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(Brand.Terracotta.copy(alpha = 0.12f))
                        .clickable {
                            val next = dailyPlan.size + 1
                            dailyPlan = dailyPlan + "D$next｜新的行程：09:00 安排"
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(tr("添加一天"), color = Brand.Terracotta, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                tr("每行一天，格式：D1｜主题：09:00 活动 / 12:00 活动"),
                color = Text.Muted,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(8.dp))

            if (dailyPlan.isEmpty()) {
                Text(
                    tr("还没有每日安排，点「添加一天」开始写。"),
                    color = Text.Muted,
                    fontSize = 12.sp
                )
            } else {
                dailyPlan.forEachIndexed { index, line ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Bg.SurfaceVariant)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tr("第 ${index + 1} 天"),
                                color = Text.Secondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                tr("删除"),
                                color = Text.Muted,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .clickable {
                                        dailyPlan = dailyPlan.filterIndexed { i, _ -> i != index }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = line,
                            onValueChange = { value ->
                                dailyPlan = dailyPlan.toMutableList().also { it[index] = value }
                            },
                            textStyle = TextStyle(fontSize = 12.sp, color = Text.Primary),
                            minLines = 2,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text(
                    tr("取消"),
                    color = Text.Secondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(Brand.Terracotta)
                        .clickable {
                            val cleanName = name.trim()
                            val cleanDate = startDate.trim()
                            val dateValid = runCatching {
                                SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                                    .apply { isLenient = false }
                                    .parse(cleanDate) != null
                            }.getOrDefault(false)
                            val cleanPlan = dailyPlan.map { it.trim() }.filter { it.isNotBlank() }
                            when {
                                cleanName.isBlank() -> state.toast(tr("行程标题不能为空"))
                                !dateValid -> state.toast(tr("出发日期格式应为 yyyy-MM-dd"))
                                else -> {
                                    state.updateTripPlan(plan.id) {
                                        it.copy(
                                            name = cleanName,
                                            destination = destination.trim(),
                                            startDate = cleanDate,
                                            days = cleanPlan.size.coerceAtLeast(1),
                                            note = note.trim(),
                                            dailyPlan = cleanPlan
                                        )
                                    }
                                    // 改了出发日期时，提醒要按新日期重排
                                    state.updatePlanReminder(
                                        plan.id,
                                        plan.reminderEnabled,
                                        plan.reminderOffsetDays,
                                        plan.reminderHour,
                                        plan.reminderMinute,
                                        plan.reminderVibrate,
                                        plan.reminderSound
                                    )
                                    state.toast(tr("行程已更新"))
                                    onDismiss()
                                }
                            }
                        }
                        .padding(horizontal = 22.dp, vertical = 9.dp)
                ) {
                    Text(
                        tr("保存"),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** 同行人胶囊：姓名 + 移除 */
@Composable
private fun CompanionChip(name: String, onRemove: () -> Unit) {
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
            modifier = Modifier.size(13.dp).clickable(onClick = onRemove)
        )
    }
}

/** 开关型小胶囊 */
@Composable
private fun ToggleChip(label: String, checked: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(
                if (checked) Brand.Terracotta.copy(alpha = 0.12f) else Bg.Surface
            )
            .then(if (checked) Modifier else Modifier.border(1.dp, Bg.Outline, RoundedCornerShape(50.dp)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (checked) {
            Icon(Icons.Filled.Check, null, tint = Brand.Terracotta, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            label,
            color = if (checked) Brand.Terracotta else Text.Muted,
            fontSize = 12.sp
        )
    }
}

/** 「− 值 +」步进行 */
@Composable
private fun StepperRow(
    title: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Text.Secondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        StepButton("−", onMinus)
        Text(
            value,
            color = Text.Primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        StepButton("+", onPlus)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Bg.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Brand.Terracotta, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/** 清单单行：勾选圆点 + 文本 + 自定义项删除 */
@Composable
private fun PackingRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    onRemove: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .then(
                    if (checked) Modifier.background(Brush.linearGradient(MainGradient))
                    else Modifier.border(1.dp, Bg.Outline, CircleShape)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = if (checked) Text.Muted else Text.Secondary,
            fontSize = 13.sp,
            textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, tr("移除物品"), tint = Text.Muted, modifier = Modifier.size(13.dp))
            }
        }
    }
}

/** 底部胶囊操作按钮 */
@Composable
private fun DetailAction(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun detailTodayStart(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** 4.12 - 4.18 · 进行中 */
private fun detailDateRange(plan: TripPlan): String {
    val start = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(plan.startDate)?.time
    }.getOrNull() ?: return tr("${plan.startDate} · ${plan.days} 天", "${plan.startDate} · ${plan.days} days")
    val end = start + (plan.days - 1).coerceAtLeast(0) * PLAN_DETAIL_DAY_MS
    val fmt = SimpleDateFormat("M.d", Locale.CHINA)
    val today = detailTodayStart()
    val stage = when {
        start > today -> tr("待出发")
        today <= end -> tr("进行中")
        else -> tr("已结束")
    }
    return "${fmt.format(Date(start))} - ${fmt.format(Date(end))} · $stage"
}
