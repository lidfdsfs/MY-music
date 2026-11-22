package com.example.my.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.AiInsight
import com.example.my.ui.i18n.tr
import com.example.my.ui.screens.AiTaskCenter
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Text

/**
 * 「AI 一键分析」卡片：足迹详情与攻略详情共用。
 *
 * 状态全部由 [AiTaskCenter] 持有：点分析后协程跑在应用级 scope 上，
 * 用户中途退页面不会断流；思考过程实时流出（豆包/DeepSeek 式），
 * 回来重进同一份 key 直接续看。失败时展示真实原因，不再是「请检查网络」。
 */
@Composable
fun AiInsightCard(
    kind: String,
    title: String,
    facts: String,
    endpoint: com.example.my.data.AiEndpoint = com.example.my.data.AiPresets.builtin,
    context: Context? = null,
    useLocal: Boolean = false,
    localModelId: String? = null,
    modelLabel: String = aiModelLabel(endpoint, useLocal, localModelId),
    onModelLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val summaryMode = kind == "足迹"
    val key = "insight:$kind:$title:${facts.hashCode()}"
    val state = remember(key) { AiTaskCenter.insight(key) }
    var editing by remember(key) { mutableStateOf(false) }
    val status = state.status
    val reasoning = state.reasoning
    val raw = state.raw
    val insight = state.insight
    val error = state.error

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Bg.Surface)
            .border(1.dp, Brand.Terracotta.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Brand.Terracotta, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                tr(
                    if (summaryMode) "AI 足迹总结" else "AI 一键分析",
                    if (summaryMode) "AI footprint recap" else "AI insight"
                ),
                color = Text.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = tr("模型：$modelLabel", "Model: $modelLabel"),
                color = Text.Muted, fontSize = 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 118.dp)
                    .then(
                        if (onModelLongPress != null) {
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .pointerInput(Unit) {
                                    detectTapGestures(onLongPress = { onModelLongPress() })
                                }
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                        } else Modifier
                    )
            )
            Spacer(Modifier.weight(1f))
            if (status == AiTaskCenter.InsightStatus.DONE && !editing && !summaryMode) {
                Text(
                    text = tr("编辑"),
                    color = Text.Secondary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { editing = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            if (status == AiTaskCenter.InsightStatus.DONE || status == AiTaskCenter.InsightStatus.ERROR) {
                Text(
                    text = tr("重新分析"),
                    color = Brand.Terracotta,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            editing = false
                            AiTaskCenter.startInsight(key, kind, title, facts, endpoint, context, useLocal, localModelId)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(3.dp))

        when (status) {
            AiTaskCenter.InsightStatus.RUNNING -> {
                Spacer(Modifier.height(8.dp))
                // 实时思考面板
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Psychology, contentDescription = null, tint = Text.Muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (reasoning.isBlank()) tr("正在思考…") else tr("正在思考…"),
                        color = Text.Secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.weight(1f))
                    if (reasoning.isNotBlank()) {
                        Icon(
                            if (state.expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = Text.Muted,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { state.expanded = !state.expanded }
                                .padding(2.dp)
                        )
                    }
                }
                if (state.expanded && reasoning.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Bg.SurfaceVariant)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = reasoning,
                            color = Text.Secondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
                // 思考结束后正文开始冒（一段 JSON）：给个进度提示
                if (reasoning.isNotBlank() && raw.isBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(tr("思考完成，正在整理结论…"), color = Text.Muted, fontSize = 11.sp)
                }
            }

            AiTaskCenter.InsightStatus.ERROR -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error ?: tr("AI 分析失败，请稍后重试"),
                    color = Brand.Terracotta,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brand.Terracotta)
                        .clickable { AiTaskCenter.startInsight(key, kind, title, facts, endpoint, context, useLocal, localModelId) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr("重试"),
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AiTaskCenter.InsightStatus.DONE -> {
                val data: AiInsight = insight ?: return@Column
                if (editing) {
                    InsightEditor(
                        data = data,
                        onCancel = { editing = false },
                        onSave = {
                            state.editInsight(it)
                            editing = false
                        }
                    )
                } else {
                    Spacer(Modifier.height(10.dp))
                if (!summaryMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${data.score}", color = Brand.Terracotta, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(3.dp))
                        Text(tr("分"), color = Brand.Terracotta, fontSize = 11.sp)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Brand.Terracotta.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(tr(data.verdict), color = Brand.Terracotta, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    data.headline,
                    color = Text.Primary,
                    fontSize = if (summaryMode) 14.sp else 13.sp,
                    lineHeight = if (summaryMode) 21.sp else 19.sp,
                    fontWeight = FontWeight.Medium
                )

                if (summaryMode) {
                    InsightSection(tr("记录要点"), Icons.Outlined.CheckCircle, data.highlights)
                } else {
                    InsightSection(tr("亮点"), Icons.Outlined.CheckCircle, data.highlights)
                    InsightSection(tr("注意"), Icons.Outlined.WarningAmber, data.cautions)
                    InsightSection(tr("建议"), Icons.Outlined.Lightbulb, data.tips)
                }

                if (data.tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.tags.take(4).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Bg.SurfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("#$tag", color = Text.Secondary, fontSize = 10.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (summaryMode) tr(
                        "由 AI 根据这条足迹整理 · 只做回顾",
                        "Recap generated from this footprint · for review only"
                    ) else if (useLocal) tr(
                        "本机模型 · 失败自动回退云端，仅供出行前参考",
                        "On-device model · cloud fallback if needed · for reference only"
                    ) else tr(
                        "${endpoint.name} · ${endpoint.model.ifBlank { "默认模型" }}，仅供出行前参考",
                        "${endpoint.name} · ${endpoint.model.ifBlank { "default model" }} · for reference only"
                    ),
                    color = Text.Muted,
                    fontSize = 10.sp
                )
                }
            }

            AiTaskCenter.InsightStatus.IDLE -> {
                Spacer(Modifier.height(7.dp))
                Text(
                    text = if (summaryMode) tr(
                        "让 AI 读完这段足迹，把地点、时间、路线、照片和备注整理成一段总结，不打分、不挑错。",
                        "Let AI turn this footprint into a recap of places, dates, route, photos and notes — no scoring."
                    ) else tr("让 AI 看完这${kind}，给你打分、挑亮点、指出坑，再给几条能直接照做的建议。"),
                    color = Text.Secondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(11.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brand.Terracotta)
                        .clickable { AiTaskCenter.startInsight(key, kind, title, facts, endpoint, context, useLocal, localModelId) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (summaryMode) tr("总结这段足迹", "Recap this footprint") else tr("一键分析"),
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** 分析结果可手动修正：评分、点评、亮点、注意、建议、标签 */
@Composable
private fun InsightEditor(
    data: AiInsight,
    onCancel: () -> Unit,
    onSave: (AiInsight) -> Unit
) {
    var score by remember(data) { mutableStateOf(data.score.toString()) }
    var headline by remember(data) { mutableStateOf(data.headline) }
    var highlights by remember(data) { mutableStateOf(data.highlights.joinToString("\n")) }
    var cautions by remember(data) { mutableStateOf(data.cautions.joinToString("\n")) }
    var tips by remember(data) { mutableStateOf(data.tips.joinToString("\n")) }
    var tags by remember(data) { mutableStateOf(data.tags.joinToString("，")) }

    Spacer(Modifier.height(10.dp))
    Text(tr("修改分析结果"), color = Text.Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.Top) {
        OutlinedTextField(
            value = score,
            onValueChange = { score = it.filter(Char::isDigit).take(3) },
            label = { Text(tr("评分"), fontSize = 10.sp) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Text.Primary),
            modifier = Modifier.width(92.dp)
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = headline,
            onValueChange = { headline = it },
            label = { Text(tr("一句话点评"), fontSize = 10.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Text.Primary),
            minLines = 1,
            maxLines = 3,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = highlights,
        onValueChange = { highlights = it },
        label = { Text(tr("亮点（每行一条）"), fontSize = 10.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Text.Primary),
        minLines = 2,
        maxLines = 5,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = cautions,
        onValueChange = { cautions = it },
        label = { Text(tr("注意（每行一条）"), fontSize = 10.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Text.Primary),
        minLines = 2,
        maxLines = 5,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = tips,
        onValueChange = { tips = it },
        label = { Text(tr("建议（每行一条）"), fontSize = 10.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Text.Primary),
        minLines = 2,
        maxLines = 5,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = tags,
        onValueChange = { tags = it },
        label = { Text(tr("标签（用逗号分隔）"), fontSize = 10.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Text.Primary),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        Text(
            text = tr("取消"),
            color = Text.Secondary,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onCancel)
                .padding(horizontal = 12.dp, vertical = 7.dp)
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Brand.Terracotta)
                .clickable {
                    onSave(
                        AiInsight(
                            headline = headline.trim().ifBlank { data.headline },
                            score = score.toIntOrNull()?.coerceIn(0, 100) ?: data.score,
                            highlights = linesToItems(highlights),
                            cautions = linesToItems(cautions),
                            tips = linesToItems(tips),
                            tags = tags.split('，', ',', '\n').map { it.trim() }.filter { it.isNotBlank() }
                        )
                    )
                }
                .padding(horizontal = 16.dp, vertical = 7.dp)
        ) {
            Text(tr("保存"), color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun linesToItems(raw: String): List<String> =
    raw.lines().map { it.trim().removePrefix("·").trim() }.filter { it.isNotBlank() }

/** 分析结果里的一个分组（亮点 / 注意 / 建议） */
@Composable
private fun InsightSection(label: String, icon: ImageVector, items: List<String>) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Brand.Terracotta, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Text.Secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
    Spacer(Modifier.height(3.dp))
    items.forEach { line ->
        Row(modifier = Modifier.padding(bottom = 2.dp)) {
            Text("·", color = Text.Muted, fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Text(line, color = Text.Primary, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

