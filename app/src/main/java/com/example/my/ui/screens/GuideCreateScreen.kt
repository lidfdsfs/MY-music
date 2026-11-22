package com.example.my.ui.screens

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.airbnb.lottie.compose.*
import com.example.my.state.AppState
import com.example.my.ui.components.AppTopBar
import com.example.my.ui.components.BackActionRow
import com.example.my.ui.components.DashedAddButton
import com.example.my.ui.interaction.AnimatedCheck
import com.example.my.ui.interaction.RangeSlider2
import com.example.my.ui.interaction.TimeRangePicker
import com.example.my.ui.interaction.pressBounceScale
import com.example.my.ui.interaction.rememberTapRipple
import com.example.my.ui.interaction.tapRipple
import com.example.my.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.example.my.ui.i18n.Lang
import com.example.my.ui.i18n.tr

private data class CoverOption(val icon: ImageVector, val bg: Color)

private val coverOptions = listOf(
    CoverOption(Icons.Outlined.Waves, Color(0xFFEE9B7C)),
    CoverOption(Icons.Outlined.Landscape, Color(0xFF5FA394)),
    CoverOption(Icons.Outlined.Umbrella, Color(0xFFF0C088)),
    CoverOption(Icons.Outlined.Favorite, Color(0xFFEBA7B4)),
    CoverOption(Icons.Outlined.Place, Color(0xFF9CB0D8)),
    CoverOption(Icons.Outlined.Star, Color(0xFF7FB0D0)),
    CoverOption(Icons.Outlined.Restaurant, Color(0xFFF0906B)),
    CoverOption(Icons.Outlined.Store, Color(0xFFAAB2C4)),
    CoverOption(Icons.Outlined.Home, Color(0xFFBBA23F)),
    CoverOption(Icons.Outlined.DirectionsWalk, Color(0xFF9CC083)),
    CoverOption(Icons.Outlined.WbSunny, Color(0xFFD3AE8C)),
    CoverOption(Icons.Outlined.AcUnit, Color(0xFF9CC2D8))
)

private val tagOptions = listOf("城市漫游", "美食", "自然风光", "人文历史", "亲子", "徒步", "自驾", "深度")
private val dayQuickOptions = listOf(3, 4, 5, 7)
private val timeSlots = listOf("清晨", "上午", "中午", "下午", "傍晚", "晚上")
private val coverIconTint = Color(0xFF4A3B31)

/** 时段集合 ↔ 连续区间（非连续的旧选择收敛为最小覆盖区间） */
private fun slotsToRange(set: Set<String>): IntRange? {
    val idx = set.mapNotNull { timeSlots.indexOf(it).takeIf { i -> i >= 0 } }
    if (idx.isEmpty()) return null
    return idx.min()..idx.max()
}

/** 攻略发布页 */
@Composable
fun GuideCreateScreen(state: AppState) {
    var title by remember { mutableStateOf("") }
    var selectedCover by remember { mutableStateOf(0) }
    var tags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dayIds by remember { mutableStateOf(listOf(1, 2, 3)) }
    var slots by remember { mutableStateOf<Map<Int, Set<String>>>(emptyMap()) }
    var hotels by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var transports by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var costs by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var foods by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    // 人均预算（双头范围滑块，单位：元）
    var budgetLow by remember { mutableStateOf(800f) }
    var budgetHigh by remember { mutableStateOf(6500f) }
    var showSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg.Background)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // 标题栏只放标题文字；取消按钮移入内容区
        AppTopBar(title = tr("发布攻略"))
        BackActionRow(
            leading = {
                androidx.compose.material3.TextButton(onClick = { state.closeScreen() }) {
                    Text(tr("取消"), color = Text.Secondary, fontSize = 15.sp)
                }
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 攻略标题
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(Bg.Surface)
                        .cardShadow()
                        .padding(16.dp)
                ) {
                    Text(tr("攻略标题"), color = Text.Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Bg.SurfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 2.dp)
                    ) {
                        TextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(tr("例如：大理7日慢生活 | 洱海边的风花雪月"), color = Text.Muted, fontSize = 14.sp) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Text.Primary, fontSize = 14.sp),
                            colors = fieldColors()
                        )
                    }
                }
            }

            // 封面选择
            item {
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(Bg.Surface)
                        .cardShadow()
                        .padding(16.dp)
                ) {
                    Text(tr("封面选择"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    coverOptions.chunked(4).forEach { rowCovers ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowCovers.forEach { option ->
                                CoverCell(
                                    option = option,
                                    selected = coverOptions.indexOf(option) == selectedCover,
                                    onClick = { selectedCover = coverOptions.indexOf(option) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            // 攻略标签
            item {
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(Bg.Surface)
                        .cardShadow()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("攻略标签"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Text(tr("（可多选）"), color = Text.Muted, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    tagOptions.chunked(4).forEach { rowTags ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowTags.forEach { t ->
                                TagCell(
                                    text = tr(t),
                                    selected = t in tags,
                                    onClick = { tags = if (t in tags) tags - t else tags + t },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            // 人均预算：双头范围滑块，拖动浮出实时数值
            item {
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(Bg.Surface)
                        .cardShadow()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("人均预算"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "¥${budgetLow.roundToInt()} – ¥${budgetHigh.roundToInt()}",
                            color = Brand.Terracotta,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    RangeSlider2(
                        low = budgetLow,
                        high = budgetHigh,
                        onChange = { l, h -> budgetLow = l; budgetHigh = h },
                        valueRange = 200f..20000f,
                        format = { "¥${it.roundToInt()}" }
                    )
                }
            }

            // 行程时间线
            item {
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(Bg.Surface)
                        .cardShadow()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("行程时间线"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        dayQuickOptions.forEach { d ->
                            DayQuickChip(
                                day = d,
                                selected = dayIds.size == d,
                                onClick = { dayIds = (1..d).toList() }
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    dayIds.forEachIndexed { index, dayId ->
                        DayEditorCard(
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
                }
            }

            // 住宿推荐 / 交通指南 / 花费参考 / 美食推荐
            item {
                Spacer(Modifier.height(16.dp))
                EntrySection(
                    title = tr("住宿推荐"),
                    mainHint = tr("酒店/民宿名称"),
                    noteHint = tr("备注（位置/价格/亮点）"),
                    entries = hotels,
                    onAdd = { hotels = hotels + ("" to "") },
                    onChange = { i, main, note ->
                        hotels = hotels.toMutableList().also { it[i] = main to note }
                    }
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                EntrySection(
                    title = tr("交通指南"),
                    mainHint = tr("交通方式（高铁 / 自驾 / 航班）"),
                    noteHint = tr("备注（时长/价格/贴士）"),
                    entries = transports,
                    onAdd = { transports = transports + ("" to "") },
                    onChange = { i, main, note ->
                        transports = transports.toMutableList().also { it[i] = main to note }
                    }
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                EntrySection(
                    title = tr("花费参考"),
                    mainHint = tr("项目（如：门票）"),
                    noteHint = tr("金额（如：¥120）"),
                    entries = costs,
                    onAdd = { costs = costs + ("" to "") },
                    onChange = { i, main, note ->
                        costs = costs.toMutableList().also { it[i] = main to note }
                    }
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                EntrySection(
                    title = tr("美食推荐"),
                    mainHint = tr("餐厅 / 美食名称"),
                    noteHint = tr("备注（位置/人均/推荐菜）"),
                    entries = foods,
                    onAdd = { foods = foods + ("" to "") },
                    onChange = { i, main, note ->
                        foods = foods.toMutableList().also { it[i] = main to note }
                    }
                )
            }

            // 实时预览
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(Bg.Surface)
                        .cardShadow()
                        .clickable { state.toast("预览：${title.ifBlank { "未填写标题" }}") }
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("实时预览 · ${title.length}字", "Live preview · ${title.length} chars"),
                        color = Text.Primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(tr("预览效果"), color = Brand.Terracotta, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        // 底部固定发布按钮（按压回弹 + 水波）
        Column(modifier = Modifier.fillMaxWidth().background(Bg.Surface)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Bg.Outline))
            val publishInteraction = remember { MutableInteractionSource() }
            val publishRipple = rememberTapRipple()
            val publishScale = pressBounceScale(publishInteraction, 0.97f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .scale(publishScale)
                    .clip(RoundedCornerShape(50.dp))
                    .background(Brush.linearGradient(MainGradient))
                    .tapRipple(publishRipple) {
                        if (title.isBlank()) {
                            state.toast(tr("请填写攻略标题"))
                        } else {
                            showSuccess = true
                            scope.launch {
                                delay(1100)
                                state.toast(tr("攻略《${title.take(12)}》已发布", "Guide ${title.take(12)} published"))
                                state.closeScreen()
                            }
                        }
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tr("发布攻略"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // 发布成功：逐笔绘制勾选，短暂停留后再回到列表
    if (showSuccess) {
        Dialog(onDismissRequest = {}) {
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Bg.Surface)
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val successComposition by rememberLottieComposition(
                    LottieCompositionSpec.Url("https://raw.githubusercontent.com/airbnb/lottie-android/master/sample/src/main/assets/Tests/CheckMark.json")
                )
                if (successComposition != null) {
                    LottieAnimation(
                        composition = successComposition,
                        iterations = 1,
                        modifier = Modifier.size(84.dp)
                    )
                } else {
                    AnimatedCheck(visible = true, size = 84.dp)
                }
                Spacer(Modifier.height(14.dp))
                Text(tr("发布成功"), color = Text.Primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(tr("攻略已同步到发现页"), color = Text.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CoverCell(
    option: CoverOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(option.bg)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) Brand.Terracotta else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(option.icon, null, tint = coverIconTint.copy(alpha = 0.78f), modifier = Modifier.size(34.dp))
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Brand.Terracotta),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun TagCell(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) Brand.Terracotta else Bg.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Text.Primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DayQuickChip(day: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) Brand.Terracotta.copy(alpha = 0.14f) else Bg.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tr("${day}天", "${day} days"),
            color = if (selected) Brand.Terracotta else Text.Secondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** 单天行程卡：标题 + 删除 + 时段胶囊 */
@Composable
private fun DayEditorCard(
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
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Bg.Surface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
        // 拖动选时间段：按住连选合并成条，两端把手微调，起止实时更新
        TimeRangePicker(
            slots = timeSlots,
            selected = slotsToRange(selectedSlots),
            onChange = { range ->
                onSetSlots(range?.let { timeSlots.slice(it).toSet() } ?: emptySet())
            },
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

/** 可增删的条目区（住宿/交通/花费/美食） */
@Composable
private fun EntrySection(
    title: String,
    mainHint: String,
    noteHint: String,
    entries: List<Pair<String, String>>,
    onAdd: () -> Unit,
    onChange: (Int, String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Bg.Surface)
            .cardShadow()
            .padding(16.dp)
    ) {
        Text(title, color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        entries.forEachIndexed { index, entry ->
            EntryInput(
                value = entry.first,
                placeholder = mainHint,
                onValueChange = { onChange(index, it, entry.second) }
            )
            Spacer(Modifier.height(8.dp))
            EntryInput(
                value = entry.second,
                placeholder = noteHint,
                onValueChange = { onChange(index, entry.first, it) }
            )
            Spacer(Modifier.height(12.dp))
        }
        DashedAddButton(tr("+ 添加一条"), onClick = onAdd)
    }
}

@Composable
private fun EntryInput(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Bg.SurfaceVariant)
            .padding(horizontal = 14.dp, vertical = 2.dp)
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = Text.Muted, fontSize = 13.sp) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Text.Primary, fontSize = 13.sp),
            colors = fieldColors()
        )
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = Brand.Terracotta
)
