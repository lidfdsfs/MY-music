package com.example.my.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.AiEndpoint
import com.example.my.data.AiFeature
import com.example.my.data.AiPresets
import com.example.my.state.AppState
import com.example.my.ui.components.aiModelLabel
import com.example.my.ui.i18n.tr
import com.example.my.ui.interaction.ElasticSwitch
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.BorderLight
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.Spacing
import com.example.my.ui.theme.Terracotta
import com.example.my.ui.theme.TextMuted
import com.example.my.ui.theme.TextPrimary
import com.example.my.ui.theme.TextSecondary

/**
 * 设置 → AI 与语音 → AI 模型分配。
 *
 * 两种模式：
 * 1) 手动：每一个 AI 功能可以单独指定一个云端端点或本机模型；选「跟随全局」则用对话底部选中的模型。
 * 2) 全局智能分配：按任务复杂度自动分配——重任务跟主力模型，轻任务用内置极速模型；
 *    开启「允许本机参与」后，适合端侧的轻任务优先交给本机模型，失败再回退云端。
 *
 * 本机模型开放给短任务（一键分析、路线标题）和手动指定的行程规划；
 * 长上下文/联网归纳仍走云端；端侧引擎同一时间只加载一个模型，不按功能重复换载。
 */
@Composable
fun AiModelAssignPanel(state: AppState) {
    var editing by remember { mutableStateOf<AiFeature?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ---------- 全局智能分配 ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(Bg.Surface)
                .padding(Spacing.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(Terracotta.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = Terracotta,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tr("全局智能分配", "Smart routing"),
                        color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        tr(
                            "按任务轻重自动挑模型；开启后单功能指定暂停",
                            "Picks a model by task weight; manual per-feature choices pause"
                        ),
                        color = TextMuted, fontSize = 11.sp
                    )
                }
                ElasticSwitch(
                    checked = state.aiRoutingSmart,
                    onCheckedChange = { state.setAiRoutingSmartEnabled(it) }
                )
            }

            Spacer(Modifier.height(Spacing.md))
            Text(
                tr(
                    "重任务（对话 / 行程 / 发现总结 / 页内问答）→ 当前主力模型；轻任务（歌曲故事 / 歌词补齐 / 一键分析 / 路线标题）→ 内置极速模型。图片生成、视频生成、看图、OCR、足迹总结、车票建议按各自能力单独指定。开启下方本机参与后，能端侧处理的短任务优先走本机，失败自动回退云端。",
                    "Heavy tasks (chat, trips, discover, in-page Q&A) → main model; light tasks (song story, lyrics, insight, route titles) → built-in fast model. Image/video generation, vision, OCR, footprint summaries and ticket advice are assigned by capability. With on-device routing on, short eligible tasks try the local model first and fall back to cloud."
                ),
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )

            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tr("允许本机模型参与", "Let on-device models join"),
                        color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium
                    )
                    Text(
                        tr(
                            if (state.localLlmState == com.example.my.data.ai.LocalLlmState.READY)
                                "本机模型已就绪，短任务会先试端侧"
                            else
                                "本机模型未就绪，暂时全部走云端",
                            if (state.localLlmState == com.example.my.data.ai.LocalLlmState.READY)
                                "Local model ready; short tasks try it first"
                            else
                                "No local model ready; cloud only for now"
                        ),
                        color = TextMuted, fontSize = 10.sp
                    )
                }
                ElasticSwitch(
                    checked = state.aiRoutingLocal,
                    onCheckedChange = { state.setAiRoutingLocalEnabled(it) }
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))
        // ---------- 模型来源总览：本地模型 / API 模型 ----------
        val localReadyForSummary =
            state.localLlmState == com.example.my.data.ai.LocalLlmState.READY
        Text(
            tr(
                "模型来源：本地模型（${if (localReadyForSummary) "已就绪" else "未就绪"}）" +
                    " · API 模型（${state.aiEndpoints.size} 个端点：对话 ${state.chatEndpoints.size} / 图片 ${state.imageEndpoints.size} / 视频 ${state.videoEndpoints.size}）",
                "Sources: on-device (${if (localReadyForSummary) "ready" else "not ready"})" +
                    " · API (${state.aiEndpoints.size} endpoints: ${state.chatEndpoints.size} chat / ${state.imageEndpoints.size} image / ${state.videoEndpoints.size} video)"
            ),
            color = TextSecondary, fontSize = 11.sp
        )

        Spacer(Modifier.height(Spacing.md))
        Text(
            tr(
                if (state.aiRoutingSmart) "各功能实际使用" else "按功能分配",
                if (state.aiRoutingSmart) "Effective routing" else "Assign per feature"
            ),
            color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(Spacing.sm))

        // ---------- 功能列表 ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(Bg.Surface)
        ) {
            AiFeature.values().forEachIndexed { index, feature ->
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(horizontal = Spacing.lg)
                            .background(BorderLight)
                    )
                }
                val endpoint = state.aiFeatureEndpoint(feature)
                val mode = state.aiFeatureRouteMode(feature)
                AiFeatureRow(
                    feature = feature,
                    endpoint = endpoint,
                    mode = mode,
                    useLocal = state.aiFeatureUsesLocal(feature),
                    localModelId = state.aiFeatureLocalModelId(feature),
                    clickable = true,
                    onClick = {
                        // 智能分配开启时单功能行原本不可点，用户会以为「选不了模型」。
                        // 点任意一行就切到手动分配，再打开模型选择。
                        if (state.aiRoutingSmart &&
                            feature.capability == com.example.my.data.AiCapability.TEXT
                        ) {
                            state.setAiRoutingSmartEnabled(false)
                            state.toast(tr("已切换为手动分配，可为单个功能选择模型"))
                        }
                        editing = feature
                    }
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))
        // 本地模型说明 + 重置
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(Terracotta.copy(alpha = 0.06f))
                .padding(Spacing.lg)
        ) {
            Text(
                tr(
                    "本页可分配云端 API 端点，也可给短任务指定本机模型。图片生成、视频生成、看图问答、OCR、足迹总结、车票建议都可以单独指定；模型支持哪些能力，在保存模型时勾选。长上下文/联网总结仍走云端。",
                    "Assign cloud endpoints or an on-device model for short tasks. Image/video generation, vision, OCR, footprint summaries and ticket advice can be assigned separately; tick the capabilities when saving a model. Long-context/web tasks stay on cloud."
                ),
                color = TextSecondary, fontSize = 11.sp, lineHeight = 17.sp
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                tr("恢复默认分配", "Reset to defaults"),
                color = Terracotta, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { state.resetAiFeatureRoutes() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }

    editing?.let { feature ->
        AiModelPickDialog(
            feature = feature,
            state = state,
            onDismiss = { editing = null },
            onPick = { route ->
                state.setAiFeatureRoute(feature, route)
                editing = null
            }
        )
    }
}

@Composable
private fun AiFeatureRow(
    feature: AiFeature,
    endpoint: AiEndpoint,
    mode: String,
    useLocal: Boolean,
    localModelId: String?,
    clickable: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val shownModel = endpoint.modelFor(feature).ifBlank { "默认模型" }
    val localDisplay = remember(context, localModelId) {
        localModelId
            ?.let { id -> com.example.my.data.ai.LocalLlm.CATALOG.firstOrNull { it.id == id } }
            ?.display
            ?: com.example.my.data.ai.LocalLlm.activeSpec(context).display
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (clickable) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = Spacing.lg, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tr(feature.zh, feature.en),
                color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium
            )
            Text(
                tr(feature.descZh, feature.descEn),
                color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                tr(
                    when {
                        useLocal -> "使用：本机模型 · $localDisplay"
                        mode == "local_fallback" ->
                            "已选本机 · 当前不可用，实际走 ${endpoint.name} · $shownModel"
                        else -> "使用：${endpoint.name} · $shownModel"
                    },
                    when {
                        useLocal -> "Using: on-device · $localDisplay"
                        mode == "local_fallback" ->
                            "On-device selected · unavailable, falling back to ${endpoint.name} · $shownModel"
                        else -> "Using: ${endpoint.name} · $shownModel"
                    }
                ),
                color = Terracotta.copy(alpha = 0.82f), fontSize = 10.sp,
                maxLines = 2
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    when (mode) {
                        "smart" -> Terracotta.copy(alpha = 0.14f)
                        "local" -> Terracotta.copy(alpha = 0.16f)
                        "local_fallback" -> Terracotta.copy(alpha = 0.10f)
                        "manual" -> Terracotta.copy(alpha = 0.10f)
                        else -> Bg.SurfaceVariant
                    }
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                when (mode) {
                    "smart" -> tr("智能", "Smart")
                    "local" -> tr("本机", "Local")
                    "local_fallback" -> tr("本机待就绪", "Local pending")
                    "manual" -> tr("指定", "Fixed")
                    else -> tr("跟随", "Global")
                },
                color = if (mode == "global") TextSecondary else Terracotta,
                fontSize = 9.sp, fontWeight = FontWeight.Medium
            )
        }
        if (clickable) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun AiModelPickDialog(
    feature: AiFeature,
    state: AppState,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val currentRoute = state.aiRouteMap[feature.key] ?: feature.defaultRoute
    val effective = state.aiFeatureEndpoint(feature)
    val effectiveModel = effective.modelFor(feature).ifBlank { "默认模型" }
    val candidates = state.aiFeatureCandidates(feature)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                tr("为「${feature.zh}」分配模型", "Model for \"${feature.en}\""),
                color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                PickOptionRow(
                    title = tr("跟随全局 / 当前模型", "Follow global / current model"),
                    subtitle = tr(
                        "当前：${effective.name.ifBlank { "未接入" }} · $effectiveModel",
                        "Now: ${effective.name.ifBlank { "none" }} · $effectiveModel"
                    ),
                    selected = currentRoute == AiFeature.ROUTE_GLOBAL,
                    onClick = { onPick(AiFeature.ROUTE_GLOBAL) }
                )
                AiDialogDivider()
                // ---------- 本地模型 ----------
                Text(
                    tr("本地模型", "On-device model"),
                    color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                if (feature.localCapable) {
                    val localCtx = LocalContext.current
                    val downloaded = remember(localCtx, state.localLlmState) {
                        com.example.my.data.ai.LocalLlm.downloadedSpecs(localCtx)
                    }
                    val activeId = remember(localCtx, state.localLlmState) {
                        com.example.my.data.ai.LocalLlm.activeSpec(localCtx).id
                    }
                    if (downloaded.isEmpty()) {
                        PickOptionRow(
                            title = tr("本地模型", "On-device model"),
                            subtitle = tr(
                                "还没有下载本地模型，先到「AI 模型能力」里下载",
                                "No on-device model downloaded yet; download one in AI capabilities"
                            ),
                            selected = false,
                            enabled = false,
                            onClick = {}
                        )
                    } else {
                        val localReady =
                            state.localLlmState == com.example.my.data.ai.LocalLlmState.READY
                        downloaded.forEachIndexed { index, spec ->
                            if (index > 0) AiDialogDivider()
                            val route = AiFeature.localRoute(spec.id)
                            val selected = currentRoute == route ||
                                (currentRoute == AiFeature.ROUTE_LOCAL && spec.id == activeId)
                            PickOptionRow(
                                title = tr(
                                    "本地模型 · ${spec.display}" +
                                        if (spec.id == activeId) "（对话默认）" else "",
                                    "On-device · ${spec.display}" +
                                        if (spec.id == activeId) " (chat default)" else ""
                                ),
                                subtitle = tr(
                                    when {
                                        !state.aiRoutingLocal ->
                                            "已关闭「允许本机模型参与」，先在上方开关打开后才能选用本机"
                                        !localReady ->
                                            "本机引擎未加载，这个模型暂时跑不起来"
                                        else ->
                                            "${spec.sizeHint} · 端侧运行，失败自动回退 API 模型"
                                    },
                                    when {
                                        !state.aiRoutingLocal ->
                                            "On-device participation is off; enable it above first"
                                        !localReady ->
                                            "On-device engine is unavailable right now"
                                        else ->
                                            "${spec.sizeHint} · runs on-device, falls back to API on failure"
                                    }
                                ),
                                selected = selected,
                                enabled = state.aiRoutingLocal && localReady,
                                onClick = { onPick(route) }
                            )
                        }
                    }
                } else {
                    PickOptionRow(
                        title = tr("本地模型", "On-device model"),
                        subtitle = tr(
                            if (feature.capability == com.example.my.data.AiCapability.TEXT)
                                "该功能上下文较长，暂只支持 API 模型"
                            else
                                "图片识别与图片/视频生成只支持 API 模型",
                            if (feature.capability == com.example.my.data.AiCapability.TEXT)
                                "This feature needs a long context; API model only for now"
                            else
                                "Vision and image/video generation use API models only"
                        ),
                        selected = false,
                        enabled = false,
                        onClick = {}
                    )
                }
                AiDialogDivider()
                // ---------- API 模型 ----------
                Text(
                    tr("API 模型（云端端点）", "API models (cloud endpoints)"),
                    color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                if (candidates.isEmpty()) {
                    PickOptionRow(
                        title = tr(
                            "还没有具备「${feature.capability.zh}」能力的模型",
                            "No model with \"${feature.capability.en}\" yet"
                        ),
                        subtitle = tr(
                            "先到「设置 - API 模型管理」接入模型，并在保存时勾选这项能力",
                            "Add a model in Settings → API models and tick this capability when saving"
                        ),
                        selected = false,
                        enabled = false,
                        onClick = {}
                    )
                } else {
                    candidates.forEachIndexed { index, ep ->
                        if (index > 0) AiDialogDivider()
                        val route = AiFeature.ROUTE_PREFIX_API + ep.id
                        PickOptionRow(
                            title = ep.name,
                            subtitle = buildString {
                                append(ep.model.ifBlank { tr("默认模型", "default model") })
                                append(" · ")
                                append(
                                    ep.effectiveCapabilities.joinToString(" / ") { tr(it.zh, it.en) }
                                )
                                append(" · ")
                                append(if (ep.ready) tr("已接通", "Ready") else tr("待补全", "Incomplete"))
                            },
                            selected = currentRoute == route,
                            enabled = ep.ready,
                            onClick = { onPick(route) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("取消", "Cancel"), color = TextSecondary)
            }
        }
    )
}

/**
 * 任意「模型：xxx」小字长按后共用的快捷换模型弹窗。
 *
 * 与「AI 模型能力」页保持一致：手动指定会关闭智能分配，否则单功能选择会被智能路由覆盖。
 */
@Composable
fun AiModelQuickSwitchDialog(
    feature: AiFeature,
    state: AppState,
    onDismiss: () -> Unit
) {
    AiModelPickDialog(
        feature = feature,
        state = state,
        onDismiss = onDismiss,
        onPick = { route ->
            if (state.aiRoutingSmart) state.setAiRoutingSmartEnabled(false)
            state.setAiFeatureRoute(feature, route)
            onDismiss()
            state.toast(
                tr(
                    "已切换模型：${aiModelLabel(state, feature)}",
                    "Model switched: ${aiModelLabel(state, feature)}"
                )
            )
        }
    )
}

@Composable
private fun PickOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) TextPrimary else TextMuted,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                subtitle,
                color = TextMuted, fontSize = 10.sp,
                maxLines = 2
            )
        }
        when {
            !enabled -> Text(
                tr("不可用", "Unavailable"),
                color = TextMuted, fontSize = 10.sp
            )
            selected -> Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Terracotta,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AiDialogDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(BorderLight)
    )
}

/** 分配页显示“这个功能实际会用哪个模型”，图片/视频取各自独立模型名 */
private fun AiEndpoint.modelFor(feature: AiFeature): String = when (feature) {
    AiFeature.IMAGE_GEN -> image
    AiFeature.VIDEO_GEN -> video
    else -> model
}



