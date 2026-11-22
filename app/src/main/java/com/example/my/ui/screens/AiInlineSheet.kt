package com.example.my.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.AiApi
import com.example.my.data.AiFeature
import com.example.my.data.ChatTurn
import com.example.my.data.ai.AppContextSnapshot
import com.example.my.state.AppState
import com.example.my.ui.components.aiModelLabel
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Layer
import com.example.my.ui.theme.Text as ThemeText
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * AI 就地入口。
 *
 * 「接入每一个地方」不等于每个页面塞一个聊天框：这里只在悬浮球点击后
 * 于**当前页面**浮起一张小卡，问题列表由 [AppContextSnapshot.hintsFor] 按现场动态生成，
 * 点任意一条都把问题带进同一套 AI 助手（[AppState.askAi]），不另起会话、不另接模型。
 */
@Composable
fun AiInlineSheet(
    state: AppState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hints = AppContextSnapshot.hintsFor(state)
    val label = state.aiEntryHint ?: AppContextSnapshot.sceneLabel(state)
    var draft by remember { mutableStateOf("") }
    var showAssistantModelPicker by remember { mutableStateOf(false) }

    fun submit(text: String) {
        val q = text.trim()
        if (q.isNotEmpty()) state.askAi(q)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Layer.Scrim)
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Bg.Surface)
                // 吃掉卡片内的点击，别让触摸穿透到外层遮罩把卡片关掉
                .clickable { }
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("就地问 AI", "Ask AI here"),
                    color = ThemeText.Primary, fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    color = ThemeText.Muted, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = tr(
                        "模型：${aiModelLabel(state, AiFeature.ASSISTANT)}",
                        "Model: ${aiModelLabel(state, AiFeature.ASSISTANT)}"
                    ),
                    color = ThemeText.Muted, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 118.dp)
                        .clip(RoundedCornerShape(50))
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { showAssistantModelPicker = true })
                        }
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", color = ThemeText.Secondary, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            // 页面语义提示：换一页就是另一组问题，不用每页各写一套
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(hints, key = { it }) { hint ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Brand.Terracotta.copy(alpha = 0.10f))
                            .clickable { submit(hint) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            hint, color = Brand.Terracotta, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Bg.SurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = ThemeText.Primary, fontSize = 14.sp),
                    cursorBrush = SolidColor(Brand.Terracotta),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit(draft) }),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(
                                tr("直接问点什么…", "Ask anything…"),
                                color = ThemeText.Muted, fontSize = 14.sp
                            )
                        }
                        inner()
                    }
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Brand.Terracotta)
                        .clickable { submit(draft) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(tr("发送", "Send"), color = Color.White, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tr("打开完整助手 ›", "Open full assistant ›"),
                color = ThemeText.Secondary, fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable { state.openAiFull() }
                    .padding(4.dp)
            )
        }
    }

    if (showAssistantModelPicker) {
        AiModelQuickSwitchDialog(
            feature = AiFeature.ASSISTANT,
            state = state,
            onDismiss = { showAssistantModelPicker = false }
        )
    }
}

/**
 * 页面内嵌版就地入口：放在内容流里的一行卡片，不与顶栏/悬浮球抢位置。
 * 点推荐追问后不跳全屏 AI 页，直接在下方流式展开回答；
 * 需要完整对话时再点「打开完整助手」。
 */
@Composable
fun AiEntryBar(state: AppState, modifier: Modifier = Modifier) {
    val hints = AppContextSnapshot.hintsFor(state)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // 就地回答状态：点 chip 后在本卡片内流式展开，不跳页
    var inlineQ by remember { mutableStateOf<String?>(null) }
    var inlineAnswer by remember { mutableStateOf("") }
    var inlineThinking by remember { mutableStateOf("") }
    var inlineBusy by remember { mutableStateOf(false) }
    var showInlineModelPicker by remember { mutableStateOf(false) }
    var showThinking by remember { mutableStateOf(false) }

    fun askInline(q: String) {
        val question = q.trim()
        if (question.isEmpty()) return
        inlineQ = question
        inlineAnswer = ""
        inlineThinking = ""
        showThinking = false
        inlineBusy = true
        scope.launch {
            try {
                val sceneCtx = AppContextSnapshot.render(state, ctx)
                AiApi.chatStream(
                    ep = state.aiFeatureEndpoint(AiFeature.INLINE),
                    history = listOf(ChatTurn(fromUser = true, text = question)),
                    context = sceneCtx,
                    settingsContext = ctx
                ).collect { chunk ->
                    // \u0001 前缀 = 模型思考过程，单独累积并折叠，不当正文显示
                    if (chunk.startsWith("\u0001")) {
                        inlineThinking += chunk.removePrefix("\u0001")
                    } else {
                        inlineAnswer += chunk
                    }
                }
            } catch (e: Exception) {
                inlineAnswer = "回答失败：${e.message ?: "网络异常"}"
            } finally {
                inlineBusy = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Bg.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("就地问 AI", "Ask AI here"),
                color = Brand.Terracotta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                AppContextSnapshot.sceneLabel(state),
                color = ThemeText.Muted, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = tr(
                    "模型：${aiModelLabel(state, AiFeature.INLINE)}",
                    "Model: ${aiModelLabel(state, AiFeature.INLINE)}"
                ),
                color = ThemeText.Muted, fontSize = 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 118.dp)
                    .clip(RoundedCornerShape(50))
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { showInlineModelPicker = true })
                    }
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            )
            if (inlineQ != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "×",
                    color = ThemeText.Muted, fontSize = 16.sp,
                    modifier = Modifier.clickable {
                        inlineQ = null
                        inlineAnswer = ""
                        inlineBusy = false
                    }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hints.take(3), key = { it }) { hint ->
                val active = inlineQ == hint
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (active) Brand.Terracotta.copy(alpha = 0.2f)
                            else Bg.SurfaceVariant
                        )
                        .clickable { askInline(hint) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        hint,
                        color = if (active) Brand.Terracotta else ThemeText.Primary,
                        fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 就地回答区：点了 chip 后在下方展开，不跳全屏
        if (inlineQ != null) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Bg.SurfaceVariant)
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        inlineQ!!,
                        color = ThemeText.Muted, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))

                    // 思考过程：折叠显示，默认收起，点「查看思考」展开
                    if (inlineThinking.isNotBlank()) {
                        Row(
                            modifier = Modifier.clickable { showThinking = !showThinking },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (showThinking) "收起思考 ▲" else "查看思考过程 ▼",
                                color = ThemeText.Muted, fontSize = 11.sp
                            )
                        }
                        if (showThinking) {
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 80.dp)
                                    .verticalScroll(rememberScrollState())
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.05f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    inlineThinking,
                                    color = ThemeText.Muted, fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    // 正式回复：限制最大高度，内部可滚动，不把页面顶太长
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            if (inlineAnswer.isNotBlank()) stripControlMarkers(inlineAnswer)
                            else if (inlineBusy) "思考中…"
                            else "",
                            color = ThemeText.Primary, fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!inlineBusy && inlineAnswer.isNotBlank()) {
                            Text(
                                tr("重新回答", "Regenerate"),
                                color = Brand.Terracotta, fontSize = 11.sp,
                                modifier = Modifier.clickable { inlineQ?.let { askInline(it) } }
                            )
                            Spacer(Modifier.width(14.dp))
                        }
                        Text(
                            tr("打开完整助手 ›", "Open full assistant ›"),
                            color = Brand.Terracotta, fontSize = 11.sp,
                            modifier = Modifier.clickable { state.openAiFull() }
                        )
                    }
                }
            }
        }
    }

    if (showInlineModelPicker) {
        AiModelQuickSwitchDialog(
            feature = AiFeature.INLINE,
            state = state,
            onDismiss = { showInlineModelPicker = false }
        )
    }
}
