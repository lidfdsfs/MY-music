package com.example.my.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Brand
import com.example.my.util.media.CaptureEffects

/** 拍摄页工具面板共用的深色底部弹层外壳 */
@Composable
internal fun CaptureSheetShell(
    title: String,
    subtitle: String? = null,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x99000000))
                .clickable { onClose() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(Color(0xFF17161B))
                    .navigationBarsPadding()
                    .clickable { /* 吞掉点击，避免点到遮罩关闭 */ }
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // 顶部抓手
                Box(
                    Modifier
                        .width(38.dp).height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0x55FFFFFF))
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Filled.Close, null, tint = Color(0xAAFFFFFF),
                        modifier = Modifier.size(22.dp).clickable { onClose() }
                    )
                }
                if (subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, color = Color(0x99FFFFFF), fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                content()
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 面板内一行滑杆 */
@Composable
private fun CaptureSlider(
    label: String,
    value: Int,
    onValue: (Int) -> Unit,
    valueText: String = "$value"
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color(0xDDFFFFFF), fontSize = 13.sp, modifier = Modifier.width(52.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f).height(30.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Brand.Terracotta,
                inactiveTrackColor = Color(0x33FFFFFF)
            )
        )
        Text(valueText, color = Color(0xAAFFFFFF), fontSize = 12.sp,
            modifier = Modifier.width(42.dp), textAlign = TextAlign.End)
    }
}

/** 美颜面板：三档真实强度，写入照片 */
@Composable
internal fun BeautyPanel(
    beauty: CaptureEffects.Beauty,
    onChange: (CaptureEffects.Beauty) -> Unit,
    onClose: () -> Unit
) {
    CaptureSheetShell(tr("美颜", "Beauty"), tr("拍摄时生效，写入照片", "Applied to your photo"), onClose) {
        CaptureSlider(tr("磨皮", "Smooth"), beauty.smooth, { onChange(beauty.copy(smooth = it)) })
        CaptureSlider(tr("提亮", "Brighten"), beauty.brighten, { onChange(beauty.copy(brighten = it)) })
        CaptureSlider(tr("红润", "Blush"), beauty.blush, { onChange(beauty.copy(blush = it)) })
        Spacer(Modifier.height(10.dp))
        Text(
            tr("全部归零即关闭美颜", "Set all to zero to turn off beauty"),
            color = Color(0x77FFFFFF), fontSize = 11.sp,
            modifier = Modifier.clickable {
                onChange(CaptureEffects.Beauty())
            }
        )
    }
}

/** 特效滤镜面板：横向滚动，选中即时高亮 */
@Composable
internal fun FilterPanel(
    current: String,
    onSelect: (CaptureEffects.Filter) -> Unit,
    onClose: () -> Unit
) {
    CaptureSheetShell(tr("特效滤镜", "Filters"), tr("拍下的照片会应用所选滤镜", "Selected filter is applied to the photo"), onClose) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CaptureEffects.FILTERS.forEach { f ->
                val selected = f.id == current
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onSelect(f) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(filterPreviewColor(f))
                            .then(
                                if (selected) Modifier.border(3.dp, Brand.Terracotta, RoundedCornerShape(12.dp))
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(f.zh, color = if (selected) Color.White else Color(0xAAFFFFFF),
                        fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

/** 用滤镜矩阵的关键偏移生成一个代表色，作为滤镜预览块（无需真实图片） */
private fun filterPreviewColor(f: CaptureEffects.Filter): Color {
    if (f.id == CaptureEffects.FILTER_NONE) return Color(0xFF6B6B73)
    val m = f.matrix
    val r = (128 + m[4]).toInt().coerceIn(0, 255)
    val g = (128 + m[9]).toInt().coerceIn(0, 255)
    val b = (128 + m[14]).toInt().coerceIn(0, 255)
    return when (f.id) {
        "mono" -> Color(0xFF9A9A9A)
        "warm", "film" -> Color(0xFFC98B5E)
        "cool", "dusk" -> Color(0xFF5E86C9)
        "vivid" -> Color(0xFFC0569E)
        "clear" -> Color(0xFF7FB8C0)
        else -> Color(r, g, b)
    }
}

/** 提词器面板：编辑要滚动提示的文字 */
@Composable
internal fun TeleprompterPanel(
    text: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    CaptureSheetShell(tr("提词器", "Teleprompter"), tr("文字会在取景框内滚动显示", "Text scrolls over the preview"), onClose) {
        val fieldColors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF242329),
            unfocusedContainerColor = Color(0xFF242329),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Brand.Terracotta,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
        TextField(
            value = text,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().height(110.dp),
            placeholder = { Text(tr("输入要提示的台词…", "Enter your script…"), color = Color(0x77FFFFFF)) },
            colors = fieldColors,
            maxLines = 5
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onEnabled(!enabled) }) {
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .background(if (enabled) Brand.Terracotta else Color(0x44FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                if (enabled) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(tr("显示提词器", "Show teleprompter"), color = Color.White, fontSize = 14.sp)
        }
    }
}

/** 滚动提词器叠加层：在取景画面上左右循环滚动 */
@Composable
internal fun TeleprompterOverlay(text: String) {
    if (text.isBlank()) return
    val transition = rememberInfiniteTransition(label = "teleprompter")
    val widthPx = 1000f
    val offset by transition.animateFloat(
        initialValue = widthPx,
        targetValue = -widthPx,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scroll"
    )
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .graphicsLayer { translationX = offset }
                .background(Color(0x88000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** 文字水印面板：输入文字并叠加到照片 */
@Composable
internal fun WatermarkPanel(
    wm: CaptureEffects.Watermark,
    onChange: (CaptureEffects.Watermark) -> Unit,
    onClose: () -> Unit
) {
    CaptureSheetShell(tr("文字水印", "Text watermark"), tr("文字会写进照片", "Text is drawn onto the photo"), onClose) {
        val fieldColors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF242329),
            unfocusedContainerColor = Color(0xFF242329),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Brand.Terracotta,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
        TextField(
            value = wm.text,
            onValueChange = { onChange(wm.copy(text = it)) },
            modifier = Modifier.fillMaxWidth().height(72.dp),
            placeholder = { Text(tr("输入要写在照片上的文字…", "Text to draw on the photo…"), color = Color(0x77FFFFFF)) },
            colors = fieldColors,
            maxLines = 3
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("颜色", "Color"), color = Color(0xDDFFFFFF), fontSize = 13.sp)
            Spacer(Modifier.width(12.dp))
            listOf(0xFFFFFFFF.toInt(), 0xFFFFD54F.toInt(), 0xFFFF6E6E.toInt(), 0xFF64D2FF.toInt()).forEach { c ->
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(Color(c))
                        .then(if (wm.color == c) Modifier.border(2.dp, Brand.Terracotta, CircleShape) else Modifier)
                        .clickable { onChange(wm.copy(color = c)) }
                )
                Spacer(Modifier.width(10.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChange(wm.copy(top = !wm.top)) }) {
            Text(tr("位置", "Position"), color = Color(0xDDFFFFFF), fontSize = 13.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                if (wm.top) tr("顶部", "Top") else tr("底部", "Bottom"),
                color = Brand.Terracotta, fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

/** 灵感 / 创作灵感 模板：点击后把文案与话题回填到发布页 */
data class CaptureInspiration(val zh: String, val en: String, val caption: String, val tags: List<String>)

internal val CAPTURE_INSPIRATIONS = listOf(
    CaptureInspiration("天空之镜", "Sky mirror", "天空之镜，把整片云海装进镜头里。", listOf("天空之镜", "治愈系风景", "旅行摄影")),
    CaptureInspiration("城市漫步", "City walk", "用脚步丈量这座城，转角都是风景。", listOf("citywalk", "城市漫游", "周末去哪儿")),
    CaptureInspiration("美食探店", "Foodie", "一口入魂，这家店的味道值得专门跑一趟。", listOf("美食探店", "宝藏小店", "舌尖上的旅行")),
    CaptureInspiration("山野徒步", "Hiking", "走进山野，把烦恼留在海拔之上。", listOf("徒步", "山野", "户外")),
    CaptureInspiration("日落黄昏", "Sunset", "追一场日落，是旅行最浪漫的仪式感。", listOf("日落", "黄昏", "浪漫旅行")),
    CaptureInspiration("海边假期", "Seaside", "面朝大海，时间在这里慢了下来。", listOf("海边", "度假", "慢生活"))
)

@Composable
internal fun InspirationPanel(
    title: String,
    onPick: (CaptureInspiration) -> Unit,
    onClose: () -> Unit
) {
    CaptureSheetShell(title, tr("选一个灵感，自动填入文案和话题", "Pick one to fill caption and tags"), onClose) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            CAPTURE_INSPIRATIONS.forEach { insp ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF242329))
                        .clickable { onPick(insp) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(insp.zh, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(3.dp))
                        Text(insp.caption, color = Color(0x99FFFFFF), fontSize = 11.sp, maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(tr("使用", "Use"), color = Brand.Terracotta, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 开直播 / 分段拍 等「走视频链路但未完整实现」的引导，明确告知并给出真实动作 */
@Composable
internal fun CaptureGuideSheet(
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit,
    onClose: () -> Unit
) {
    CaptureSheetShell(title, null, onClose) {
        Text(message, color = Color(0xCCFFFFFF), fontSize = 14.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Brand.Terracotta)
                .clickable { onAction() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(actionText, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
