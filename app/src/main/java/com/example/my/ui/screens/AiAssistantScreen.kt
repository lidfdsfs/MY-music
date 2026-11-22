package com.example.my.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.my.data.AiApi
import com.example.my.data.AiChat
import com.example.my.data.AiChatMessage
import com.example.my.data.AiFeature
import com.example.my.data.AiIntent
import com.example.my.data.AiIntentMemory
import com.example.my.data.AiMemory
import com.example.my.data.AiPlan
import com.example.my.data.AiPlanStep
import com.example.my.data.NextStep
import com.example.my.data.IntentCategory
import com.example.my.data.IntentDecision
import com.example.my.data.MemItem
import com.example.my.data.AiPresets
import com.example.my.data.AiTripBlueprint
import com.example.my.data.ChatTurn
import com.example.my.data.CommandIntent
import com.example.my.data.FootprintBlueprint
import com.example.my.data.GuideBlueprint
import com.example.my.data.Persistence
import com.example.my.data.WebHit
import com.example.my.data.WebSearch
import com.example.my.data.WebSourcePicker
import com.example.my.data.WebSearchPrefs
import com.example.my.data.SearchMode
import com.example.my.data.search.EvidenceAdjudicator
import com.example.my.data.search.EvidenceIndex
import com.example.my.data.search.EvidenceStore
import com.example.my.data.search.SeaDeck
import com.example.my.data.search.SeaFusion
import com.example.my.data.search.SearchCleaner
import com.example.my.data.search.Verdict
import com.example.my.data.ai.AgentRuntime
import com.example.my.data.ai.SearchGate
import com.example.my.data.ai.AiResolution
import com.example.my.data.ai.AppContextSnapshot
import com.example.my.data.ai.CapConfirm
import com.example.my.data.ai.CapabilityRegistry
import com.example.my.data.ai.ChatMode
import com.example.my.data.ai.LocalCommandRules
import com.example.my.data.ai.LocalLlm
import com.example.my.data.ai.LocalLlmState
import com.example.my.data.ai.VoiceIo
import com.example.my.state.AppState
import com.example.my.state.ExtraScreen
import com.example.my.ui.components.AppTopBar
import com.example.my.ui.components.BackActionRow
import com.example.my.ui.components.markdownToAnnotated
import com.example.my.ui.components.rememberUrlBitmap
import com.example.my.ui.icons.Lucide
import com.example.my.ui.interaction.AiMascot
import com.example.my.ui.interaction.rememberHapticFeedback
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Coral
import com.example.my.ui.theme.Func
import com.example.my.ui.theme.MainGradient
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.example.my.ui.i18n.tr

private const val GREETING = "你好，我是 MY足迹 AI 助手\n什么问题都可以问我——科技、学习、生活、写作都行；也能帮你做旅行攻略、生成行程表、发图片写文案，或用指令控制 App。"

/** 回答底部「参考来源」折叠列表默认只摆这么多条，其余点「展开全部」再看 */
private const val DEFAULT_SOURCE_VISIBLE = 5

/** 待用户确认后执行的 AI 动作 */
private sealed class AiPendingAction
private data class PendingGuide(val bp: GuideBlueprint) : AiPendingAction()
private data class PendingFootprint(val bp: FootprintBlueprint) : AiPendingAction()
private data class PendingTrip(val bp: AiTripBlueprint) : AiPendingAction()
private data class PendingCommand(val intent: CommandIntent) : AiPendingAction()
/** 「下一步」里直接点了一个能力：仍然走同一张确认卡，不绕过安全分级 */
private data class PendingCapability(val label: String, val call: AgentRuntime.Call) : AiPendingAction()

/** 这条待确认动作是否属于高风险（由能力表的 risk 推导，不在 UI 里另写一份名单） */
private fun AiPendingAction.isDangerous(): Boolean = when (this) {
    is PendingCommand ->
        CapabilityRegistry.find(AgentRuntime.callFromCommand(intent).id)?.confirm == CapConfirm.DOUBLE

    is PendingCapability ->
        CapabilityRegistry.find(call.id)?.confirm == CapConfirm.DOUBLE

    else -> false
}

private data class AiMsg(
    val fromUser: Boolean,
    val text: String,
    val guideId: Int? = null,
    /** 用户发送的图片：本地文件路径，用于在气泡里回显（随对话一起持久化） */
    val imagePath: String? = null,
    /** AI 生成的图片：远程 URL，仅本次会话可见 */
    val imageUrl: String? = null,
    /** AI 生成的视频：远程 URL，点开用系统播放器打开 */
    val videoUrl: String? = null,
    /** 深度思考过程（GLM reasoning_content），默认折叠显示 */
    val thinking: String? = null,
    /** 这一轮回答引用过的 Bing 网页来源，气泡下方可展开、可点开原网页 */
    val sources: List<WebHit> = emptyList(),
    /** 正在进行的处理阶段：网页搜索中 / 获取相关知识 / 思考中 / 答案整合中；落定后为 null */
    val phase: String? = null,
    /** 本轮联网检索耗时（毫秒）；仅运行时展示，不落盘 */
    val searchMs: Long? = null,
    /** 本轮模型作答耗时（毫秒）；仅运行时展示，不落盘 */
    val thinkMs: Long? = null
)

/** 来源层级徽标：让用户一眼看出这条是官方一手、主流媒体还是社区帖子 */
private fun tierBadge(tier: Int): String = when (tier) {
    1 -> "L1"
    2 -> "L2"
    3 -> "L3"
    4 -> "L4"
    else -> ""
}

/** 毫秒耗时格式：不足 1 秒显示毫秒，否则保留一位小数秒 */
private fun formatDuration(ms: Long): String =
    if (ms < 1_000) "${ms}ms" else String.format("%.1fs", ms / 1000.0)

/** AI 旅行助手（发现页「这次，想去哪儿？」入口） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(state: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val listState = rememberLazyListState()

    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    // 联网检索档位：关闭 / 快速 / 均衡 / 高效，落 SP 与其它入口共用；派生是否允许联网
    var searchMode by remember { mutableStateOf(WebSearchPrefs.getMode(ctx)) }
    val webSearchEnabled = searchMode != SearchMode.OFF
    val messages = remember { mutableStateOf(listOf(AiMsg(false, GREETING))) }
    /** 流式正文与阶段条来自两个协程（打字机 / 生产流程），都跑在 IO 线程池上，
     *  list 的读改写必须串行，否则后写的一方会用旧快照覆盖掉对方的阶段或正文 */
    val msgLock = remember { Mutex() }
    /** 本次组合是否自己发起了正在跑的那轮生成：是则本地打字机循环负责渲染，镜像 Effect 不插手 */
    var ownsGen by remember { mutableStateOf(false) }

    var conversations by remember { mutableStateOf<List<AiChat>>(emptyList()) }
    var currentId by remember { mutableStateOf(System.currentTimeMillis()) }
    var restored by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }
    var showModelPicker by remember { mutableStateOf(false) }
    // AI 长期记忆：跨会话累积的用户画像（结构化：事实/偏好/习惯），每次对话注入 system
    var aiMemory by remember { mutableStateOf(emptyList<MemItem>()) }
    var showMemory by remember { mutableStateOf(false) }

    // 本轮模型自己判定的意图（六类之一）。展示给用户看，并允许纠正，纠正结果反哺给模型。
    var lastIntent by remember { mutableStateOf<IntentDecision?>(null) }
    var intentQuestion by remember { mutableStateOf("") }
    var showIntentPicker by remember { mutableStateOf(false) }

    // 模型端点：全部由用户自行接入，未配置云端端点时对话/绘图会提示去模型管理
    val endpoint = state.aiFeatureEndpoint(AiFeature.ASSISTANT)

    // 面板
    var showHistory by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    // 历史对话批量删除
    var batchMode by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(emptySet<Long>()) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    // 豆包式推荐追问（助手回复后展示的快捷问题）
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    // 「会延伸」：模型给出的下一步（可点击、可直接调度能力）与多步计划卡
    var nextSteps by remember { mutableStateOf(emptyList<NextStep>()) }
    var planProposal by remember { mutableStateOf<AiPlan?>(null) }
    var planRunning by remember { mutableStateOf(false) }
    // 破坏性操作的两步确认：false=第一下只亮警告，true=第二下才真执行
    var dangerArmed by remember { mutableStateOf(false) }

    // 图片 / 动作 / 语音
    var attachedUri by remember { mutableStateOf<Uri?>(null) }
    var attachedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var pendingAction by remember { mutableStateOf<AiPendingAction?>(null) }
    var recording by remember { mutableStateOf(false) }
    val recognizerState = remember { mutableStateOf<SpeechRecognizer?>(null) }
    // 系统识别服务（Google / 厂商语音服务）；国产 ROM 上经常压根没有，要有离线模型兜底
    val systemVoice = remember { SpeechRecognizer.isRecognitionAvailable(ctx) }
    // 拍照（TakePicture 走 FileProvider，manifest 已配置 cache-path images/）
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = camUri
        if (ok == true && uri != null) {
            attachedUri = uri
            attachedBitmap = decodeUriBitmap(ctx, uri)?.asImageBitmap()
        }
    }

    fun startVoskVoice() {
        recording = true
        VoiceIo.startVoskRecognition(
            ctx,
            onResult = { text ->
                input = (input + " " + text).trim()
                recording = false
            },
            onError = { msg ->
                recording = false
                state.toast(msg)
            }
        )
    }

    fun startVoice() {
        if (!state.aiCapabilities.voiceInput) {
            state.toast(tr("语音输入已在「设置 - AI 模型能力」中关闭"))
            return
        }
        val offline = state.aiCapabilities.voiceOfflineOnly
        // 勾了「仅离线」或这台机器没有系统识别服务：直接走自带的离线模型
        if (offline || !systemVoice) {
            if (VoiceIo.voskAvailable(ctx)) { startVoskVoice(); return }
            if (!systemVoice) {
                state.toast(
                    tr(
                        "这台手机没有系统语音识别，也没下载离线模型：去「设置 - 语音识别」下载即可",
                        "No system speech service. Download the offline model in Settings - Voice"
                    )
                )
                return
            }
        }
        val r = recognizerState.value
        if (r == null) { state.toast(tr("当前设备不支持语音识别")); return }
        val intent = VoiceIo.speechIntent(
            offlineOnly = offline,
            language = if (state.aiLanguage.startsWith("en")) "en-US" else "zh-CN"
        )
        recording = true
        r.startListening(intent)
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        attachedUri = uri
        attachedBitmap = uri?.let { decodeUriBitmap(ctx, it) }?.asImageBitmap()
    }
    val audioPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice() else state.toast(tr("未授权录音权限，无法语音输入"))
    }

    DisposableEffect(Unit) {
        val r = if (systemVoice) SpeechRecognizer.createSpeechRecognizer(ctx) else null
        r?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(bundle: Bundle?) {
                bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { input = (input + it).trim() }
                recording = false
            }

            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { input = it }
            }

            override fun onError(error: Int) {
                recording = false
                state.toast(VoiceIo.errorText(error, state.aiCapabilities.voiceOfflineOnly))
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizerState.value = r
        onDispose {
            r?.destroy()
            // 离开页面时立刻停止朗读，避免声音跟着用户跑去别的页面
            VoiceIo.stop()
        }
    }

    fun toggleVoice() {
        if (recording) {
            recognizerState.value?.stopListening()
            VoiceIo.stopVosk()
            recording = false
            return
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoice()
        } else {
            audioPerm.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** 删除本地图片文件；失败不影响主流程 */
    fun deleteImages(paths: List<String>) {
        paths.forEach { runCatching { File(it).delete() } }
    }

    fun persistChat() {
        val msgs = messages.value.filter { it.fromUser || it.text.isNotBlank() }
        if (msgs.none { it.fromUser }) {
            val existing = conversations.firstOrNull { it.id == currentId }
            if (existing != null) {
                // 会话被丢弃，它引用的图片文件也要一起删，否则存储只增不减
                deleteImages(
                    existing.messages.mapNotNull { it.imagePath } +
                            msgs.mapNotNull { it.imagePath }
                )
                conversations = conversations.filterNot { it.id == currentId }
                val snapshot = conversations
                // 用应用级 scope 落盘：用户中途退页面时本页面 rememberCoroutineScope 会被取消，
                // 但后台那轮生成的最终回复必须写进磁盘，回来才能看到。
                AiTaskCenter.scope.launch { Persistence.saveAiChats(ctx, snapshot) }
            }
            return
        }
        val firstUser = msgs.first { it.fromUser }
        val title = firstUser.text.replace('\n', ' ').take(20)
            .ifBlank { if (firstUser.imagePath != null) tr("图片对话") else tr("新对话") }
        val chat = AiChat(
            id = currentId,
            title = title,
            updatedAt = System.currentTimeMillis(),
            messages = msgs.map {
                AiChatMessage(it.fromUser, it.text, it.guideId, it.imagePath, it.thinking, it.sources, it.videoUrl)
            }
        )
        conversations = (conversations.filterNot { it.id == currentId } + chat)
            .sortedByDescending { it.updatedAt }
        val snapshot = conversations
        AiTaskCenter.scope.launch { Persistence.saveAiChats(ctx, snapshot) }
    }

    fun addAssistant(text: String) {
        messages.value = messages.value + AiMsg(false, text)
        persistChat()
    }

    // 启动：恢复上次对话（模型固定内置，无端点配置需要恢复）
    LaunchedEffect(Unit) {
        val saved = Persistence.loadAiChats(ctx)
        conversations = saved
        saved.maxByOrNull { it.updatedAt }?.let { last ->
            currentId = last.id
            messages.value = last.messages.map { AiMsg(it.fromUser, it.text, it.guideId, it.imagePath, thinking = it.thinking, sources = it.sources, videoUrl = it.videoUrl) }
                .ifEmpty { listOf(AiMsg(false, GREETING)) }
        }
        aiMemory = Persistence.loadAiMemories(ctx)
        // 恢复动作回执与能力缺口记录；同时把覆盖率审计打进 logcat，供后续补能力
        AgentRuntime.restore(ctx)
        android.util.Log.d("MY足迹/AI", CapabilityRegistry.audit())
        // 进入助手页就刷新一次天气，让用户问“现在天气/几点”时 AI 手里是新鲜实况
        state.refreshWeather()
        restored = true
    }

    // 新消息或打字机增量时跟随到底部（用户手动上滑时不打扰）
    // 长回答会超过一屏：只在「用户本来就贴着底部」时逐帧跟随最新冒出的文字，
    // 一旦用户上滑阅读就停住，滑回底部再恢复跟随。
    var autoFollow by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            val gap = if (last == null) 0
            else last.offset + last.size - (info.viewportEndOffset - info.afterContentPadding)
            listState.isScrollInProgress to gap
        }.collect { (scrolling, gap) ->
            if (scrolling) autoFollow = gap <= 40
            else if (gap <= 40) autoFollow = true
        }
    }

    LaunchedEffect(messages.value.size) {
        if (messages.value.isNotEmpty()) {
            // messages.size <= 1 时列表首项还有 WelcomeBlock，索引要整体 +1；
            // 其余情况下 items 与 messages 一一对应，lastIndex 就是最后一项，+1 会越界。
            val welcomeOffset = if (messages.value.size <= 1) 1 else 0
            val lastIndex = messages.value.lastIndex.coerceAtLeast(0) + welcomeOffset
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (autoFollow || lastVisible >= lastIndex - 1 || messages.value.size <= 1) {
                listState.scrollToItem(lastIndex)
                autoFollow = true
            }
        }
    }

    // 流式正文逐字增长：贴着底部就一路滚到这条回答的最底部
    LaunchedEffect(
        messages.value.lastOrNull()?.text?.length,
        messages.value.lastOrNull()?.phase,
        thinking
    ) {
        if (!thinking || !autoFollow) return@LaunchedEffect
        val welcomeOffset = if (messages.value.size <= 1) 1 else 0
        val lastIndex = messages.value.lastIndex.coerceAtLeast(0) + welcomeOffset
        if ((listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) < lastIndex) {
            listState.scrollToItem(lastIndex)
        }
        // scrollBy 会被列表边界钳制，给一个足够大的量等于「滚到末尾」
        listState.scrollBy(150_000f)
    }

    suspend fun applyLastText(text: String, thinking: String? = null, phase: String? = null) {
        msgLock.withLock {
            val list = messages.value
            if (list.isEmpty() || list.last().fromUser) return@withLock
            val last = list.last()
            messages.value = list.dropLast(1) + last.copy(
                // 流式过程中控制标记会一个字一个字打出来（[记 → [记忆] → [记忆] 重庆…），
                // 必须边写边剥，否则用户每轮都能看见这两行指令闪一下再消失
                text = trimControlTail(stripControlMarkers(text)),
                thinking = thinking ?: last.thinking,
                phase = phase ?: last.phase
            )
        }
    }

    /** 更新最后一条助手气泡的处理阶段：搜索/清洗/思考/整合各阶段实时可见 */
    suspend fun setPhase(phase: String?) {
        AiTaskCenter.setPhase(phase)
        msgLock.withLock {
            val list = messages.value
            if (list.isEmpty() || list.last().fromUser) return@withLock
            val last = list.last()
            if (last.phase != phase) messages.value = list.dropLast(1) + last.copy(phase = phase)
        }
    }

    // 中途退出去再回来：有回复正在后台生成时，把 AiTaskCenter 里累积的实时文本接到最后一条气泡上，
    // 并同步本地 thinking 状态；等它跑完（chatActive 变 false）再从磁盘回拉最终对话。
    LaunchedEffect(AiTaskCenter.chatActive, AiTaskCenter.chatText, AiTaskCenter.chatThinking, restored) {
        if (ownsGen || !restored) return@LaunchedEffect
        if (AiTaskCenter.chatActive) {
            thinking = true
            // 从磁盘恢复的 messages 末尾是用户消息（空助手占位在落盘时被过滤掉了），
            // 直接 applyLastText 会因为 last().fromUser=true 直接 return，导致后台流式内容贴不上来。
            // 这里先补一个空助手气泡，再把镜像文本灌进去。
            if (messages.value.lastOrNull()?.fromUser != false) {
                messages.value = messages.value + AiMsg(false, "", phase = AiTaskCenter.chatPhase)
            }
            applyLastText(
                AiTaskCenter.chatText,
                AiTaskCenter.chatThinking.ifBlank { null },
                AiTaskCenter.chatPhase
            )
        } else {
            thinking = false
            // 后台那轮已经结束：从磁盘拿回最终落库的对话（含 guideId、记忆/追问处理后的文本）
            val saved = Persistence.loadAiChats(ctx)
            conversations = saved
            val matched = saved.firstOrNull { it.id == currentId }
            if (matched != null) {
                messages.value = matched.messages.map { AiMsg(it.fromUser, it.text, it.guideId, it.imagePath, thinking = it.thinking, sources = it.sources, videoUrl = it.videoUrl) }
            } else if (AiTaskCenter.chatText.isNotBlank() || AiTaskCenter.chatThinking.isNotBlank()) {
                // 兜底：磁盘还没回写到当前会话（时序竞争），用镜像文本补一条助手消息，别让用户看到只有自己的话
                if (messages.value.lastOrNull()?.fromUser != false) {
                    messages.value = messages.value + AiMsg(false, "")
                }
                applyLastText(
                    AiTaskCenter.chatText,
                    AiTaskCenter.chatThinking.ifBlank { null }
                )
            }
        }
    }

    fun deleteMessage(index: Int) {
        val list = messages.value.toMutableList()
        if (index !in list.indices) return
        val removed = list.removeAt(index)
        removed.imagePath?.let { runCatching { File(it).delete() } }
        messages.value = if (list.isEmpty()) listOf(AiMsg(false, GREETING)) else list
        persistChat()
        state.toast(tr("已删除该消息"))
    }



    fun requestDelete(index: Int) {
        if (thinking && index == messages.value.lastIndex) {
            state.toast(tr("正在回复中，请稍后再删"))
            return
        }
        pendingDelete = index
    }

    fun newChat() {
        if (thinking) { state.toast(tr("请等当前回复完成后再新建对话")); return }
        persistChat()
        currentId = System.currentTimeMillis()
        messages.value = listOf(AiMsg(false, GREETING))
        input = ""
        showHistory = false
        pendingAction = null
    }

    fun openChat(chat: AiChat) {
        if (thinking) { state.toast(tr("请等当前回复完成后再切换对话")); return }
        currentId = chat.id
        messages.value = chat.messages.map { AiMsg(it.fromUser, it.text, it.guideId, it.imagePath, thinking = it.thinking, sources = it.sources, videoUrl = it.videoUrl) }
            .ifEmpty { listOf(AiMsg(false, GREETING)) }
        showHistory = false
        batchMode = false
        picked = emptySet()
        pendingAction = null
    }

    fun deleteChats(ids: Set<Long>) {
        if (ids.isEmpty()) return
        if (thinking && currentId in ids) { state.toast(tr("请等当前回复完成后再删除")); return }
        // 删会话必须连带删掉它引用的图片文件，否则 ai_images 目录只增不减
        deleteImages(
            conversations.filter { it.id in ids }
                .flatMap { it.messages }
                .mapNotNull { it.imagePath }
        )
        conversations = conversations.filterNot { it.id in ids }
        scope.launch { Persistence.saveAiChats(ctx, conversations) }
        if (currentId in ids) {
            currentId = System.currentTimeMillis()
            messages.value = listOf(AiMsg(false, GREETING))
        }
        picked = emptySet()
        state.toast(tr("已删除 ${ids.size} 个对话", "Deleted ${ids.size} chats"))
    }

    /**
     * 离线关键词兜底：import_plan > command > footprint > guide > chat。
     *
     * 【不是主路径】主路径是模型判定的 <intent> 意图协议（见 AiIntent / AiApi.detectIntent）。
     * 这里只在「主回复没吐标记、API 模型补判也失败」时才用，是彻底断网时的保险丝。
     * 换句话说，用户换个说法不再影响功能是否生效——那由模型判断，不靠关键词。
     */
    fun classify(text: String): String {
        // 动作词族 × 行程名词族：覆盖「导入/写入/写进/保存/加入/加到/记到/存到/放进…」各种说法，
        // 只认固定几句会让用户换个表达就掉进聊天分支，模型只能嘴上说「已导入」
        val saveVerbs = listOf(
            "导入", "写入", "写进", "写到", "存进", "存到", "保存", "加入", "加到", "添加",
            "记到", "记进", "记下", "记录", "放到", "放进", "归档", "录入", "收纳", "同步",
            "整理成", "转成", "转换成", "复制到"
        )
        // 只针对「行程/计划」类名词；攻略、足迹有各自分支，不能在这里抢
        val tripNouns = listOf("行程", "计划", "日程", "安排", "路线", "旅程")
        val imp = saveVerbs.any { text.contains(it) } && tripNouns.any { text.contains(it) }

        val cmd = text.contains("切换") || text.contains("改成") || text.contains("换成") || text.contains("设为")
                || text.contains("调成") || text.contains("设置成")
                || text.contains("打开") || text.contains("进入") || text.contains("跳到")
                || text.contains("新建计划") || text.contains("规划行程") || text.contains("行程计划")
                || text.contains("找攻略") || text.contains("搜攻略") || text.contains("帮我找") || text.contains("搜索")
                || text.contains("点赞") || text.contains("收藏")
                || text.contains("天气怎么样") || text.contains("天气如何") || text.contains("多少度") || text.contains("下不下雨")
                || text.contains("天气城市") || text.contains("改成英文") || text.contains("改中文")
                || text.contains("退出应用") || text.contains("退出登录") || text.contains("登出") || text.contains("注销")
                || text.contains("悬浮球") || text.contains("语音输入") || text.contains("消息通知")
                || text.contains("开启") || text.contains("关闭")
                || text.contains("删除行程") || text.contains("删掉行程") || text.contains("切换模式")
                || text.contains("切换模型") || text.contains("换个模型") || text.contains("修改文案")
                // 设置类高频说法：深色、触感、界面语言、设置里的某一项
                || text.contains("深色") || text.contains("浅色") || text.contains("夜间模式")
                || text.contains("主题") || text.contains("触感") || text.contains("震动")
                || text.contains("界面语言") || text.contains("设置里")
                // 页面直达：行情、资讯、课程表、离线地图、年度报告、站内消息
                || text.contains("行情") || text.contains("股市") || text.contains("资讯")
                || text.contains("课程表") || text.contains("离线地图") || text.contains("年度报告")
                || text.contains("站内消息")
        val fp = text.contains("记一笔") || text.contains("打卡") || text.contains("去过") || text.contains("添加足迹")
                || text.contains("添加地点") || text.contains("标记") || text.contains("足迹：") || text.contains("足迹:")
                || text.contains("踩点")
        val gd = text.contains("攻略") && (text.contains("生成") || text.contains("写") || text.contains("总结")
                || text.contains("整理") || text.contains("帮我做") || text.contains("发布") || text.contains("根据")
                || text.contains("规划") || text.contains("游记"))
        // 「我去过哪些城市 / 我有多少条足迹」是在**问统计**，不是要记一笔新足迹。
        // 不先拦下来会掉进 footprint 分支被当成"从这句话里提取一条足迹"，问句直接被改写。
        val askStats = (text.contains("去过") || text.contains("足迹") || text.contains("打卡"))
                && (text.contains("哪些") || text.contains("哪几") || text.contains("哪里") || text.contains("哪儿")
                || text.contains("多少") || text.contains("几条") || text.contains("几个") || text.contains("几次")
                || text.contains("统计") || text.contains("几次"))
        return when {
            imp -> "import_plan"
            cmd -> "command"
            askStats -> "chat"
            fp -> "footprint"
            gd -> "guide"
            text.length >= 120 -> "guide"
            else -> "chat"
        }
    }

    /**
     * 意图判断与「生成回复的模型」彻底解耦：主回复没自报 <intent> 时，一律用 API
     * 模型补判一次（当前选中的是本地模型也一样——API 模型同样可以用来判断），
     * 判完立刻按判断处理这条消息。只有 API 也连不上，才回落到离线关键词 classify。
     *
     * 只传「这一句话 + 现场标签 + 纠错示例」，不带完整聊天历史、画像或精确坐标。
     */
    suspend fun judgeByApi(text: String): IntentDecision? {
        val hint = listOfNotNull(
            AppContextSnapshot.sceneLabel(state).takeIf { it.isNotBlank() },
            AiIntentMemory.render(AiIntentMemory.load(ctx))
        ).joinToString("\n")
        return runCatching { AiApi.detectIntent(endpoint, text, hint) }.getOrNull()
    }

    /**
     * 离线兜底路由的执行器：把原来「发送消息时先分类再分支」的逻辑搬到流式结束之后，
     * 只在「主回复没吐标记、API 模型补判也失败」时才跑，主路径不多一次模型请求。
     */
    suspend fun runFallbackRoute(route: String, text: String) {
        when (route) {
            "import_plan" -> {
                val log = messages.value.dropLast(1)
                    .filter { it.text.isNotBlank() && it.text != GREETING }
                    .joinToString("\n") { (if (it.fromUser) "用户：" else "助手：") + it.text }
                val bp = runCatching {
                    AiApi.extractTripFromChat(
                        endpoint,
                        log.ifBlank { text },
                        java.time.LocalDate.now().toString()
                    )
                }.getOrNull()
                if (bp != null) pendingAction = PendingTrip(bp)
            }
            "footprint" -> {
                val bp = runCatching { AiApi.extractFootprint(endpoint, text) }.getOrNull()
                if (bp != null && (bp.name.isNotBlank() || bp.city.isNotBlank())) {
                    pendingAction = PendingFootprint(bp)
                }
            }
            "guide" -> {
                val bp = runCatching { AiApi.generateGuideFromLog(endpoint, text) }.getOrNull()
                if (bp != null) pendingAction = PendingGuide(bp)
            }
            "command" -> {
                // 模型没吐意图、API 也补判失败时，本地规则兜住「打开深色模式」这类高频设置指令，
                // 不让用户说完一句就石沉大海
                val intent = runCatching { AiApi.detectCommand(endpoint, text) }.getOrNull()
                    ?: LocalCommandRules.parse(text)
                if (intent != null && intent.kind != "none") pendingAction = PendingCommand(intent)
            }
        }
    }

    /** 兜底路由 → 六类意图，仅用于给气泡上的「模型判断」标签一个可读类别 */
    fun categoryForRoute(route: String): IntentCategory = when (route) {
        "command" -> IntentCategory.OPEN
        "footprint", "import_plan" -> IntentCategory.RECORD
        "guide" -> IntentCategory.PUBLISH
        else -> IntentCategory.CHAT
    }

    /** 清掉上一轮挂着的延伸按钮与计划卡，别让旧按钮粘在新回复底下 */
    fun clearNext() {
        suggestions = emptyList()
        nextSteps = emptyList()
        planProposal = null
    }

    /** 把一条「下一步」拼成网关能收的调用；模型只给动作没给对象时按现场补 */
    fun buildCall(step: NextStep): AgentRuntime.Call {
        val cap = CapabilityRegistry.find(step.capability)
            ?: return AgentRuntime.Call(step.capability)
        val args = AgentRuntime.coerceArgs(cap, step.args)
        val scene = AiResolution.facts(state, "${step.label} ${step.prompt}")
        cap.params.forEach { p ->
            val missing = !args.has(p.name) || args.opt(p.name)?.toString().isNullOrBlank()
            if (missing) {
                val fill = when (p.name) {
                    "keyword" -> scene.guideTitle.ifBlank { scene.focusCity }
                    "city" -> scene.focusCity.ifBlank { scene.weatherCity }
                    else -> ""
                }
                if (fill.isNotBlank()) args.put(p.name, fill)
            }
        }
        return AgentRuntime.Call(cap.id, args)
    }

    /**
     * 多步计划：用户确认整张卡后按序执行，每步都过网关并留真实回执。
     * 失败即停，剩余步骤重新挂成一张卡，用户说「继续」就能接着跑，不用重述需求。
     */
    fun runPlan(p: AiPlan) {
        if (planRunning || p.steps.isEmpty()) return
        planProposal = null
        clearNext()
        planRunning = true
        addAssistant(tr("我按这个顺序来做：", "I'll do these in order: ") + p.goal)
        scope.launch {
            val mode = state.effectiveChatMode
            val lines = mutableListOf<String>()
            var rest = emptyList<AiPlanStep>()
            for ((i, s) in p.steps.withIndex()) {
                val cap = CapabilityRegistry.find(s.capability)
                if (cap == null || cap.gated) {
                    AgentRuntime.noteGap(s.capability)
                    lines += "✗ ${s.label}：" + tr("这个能力还不可用", "capability unavailable")
                    rest = p.steps.drop(i)
                    break
                }
                val call = AgentRuntime.Call(cap.id, AgentRuntime.coerceArgs(cap, s.args))
                val d = AgentRuntime.execute(state, ctx, call, mode, confirmed = true)
                lines += if (d.ok) "✓ ${s.label}" else "✗ ${s.label}：${d.message}"
                if (!d.ok) {
                    rest = p.steps.drop(i + 1)
                    break
                }
            }
            addAssistant(lines.joinToString("\n"))
            if (rest.isNotEmpty()) planProposal = AiPlan(p.goal, rest)
            planRunning = false
            persistChat()
        }
    }

    /**
     * 执行模型自己判定的意图。
     *
     * 六类意图由模型在 <intent> 标记里给出（见 AiIntent），代码不再猜。
     * 打开/设置/发布/记录这类会动数据的操作统一进「待确认动作卡」，用户点了才算执行。
     */
    suspend fun executeIntent(rawDecision: IntentDecision, userText: String) {
        // 先把「这个 / 刚才那篇 / 切到那边」按现场补成真实对象，再往下走。
        // 模型判意图，App 补现场：省略句不该因为一个空 target 就听不懂。
        val scene = AiResolution.facts(state, userText)
        val decision = AiResolution.resolve(rawDecision, scene)
        val spec = AiResolution.specWithScene(userText, scene)
        // command：先过能力网关的参数校验 / 模式过滤，再进确认卡。
        // 模型给的参数一律不可信：非法枚举、超长文本、含控制标记、纯本地模式下的联网动作
        // 都在这里拦掉，并给出可解释的原因，而不是等用户点完确认才发现什么都没发生。
        decision.asCommand()?.let { cmd ->
            if (cmd.kind != "none") {
                val call = AgentRuntime.callFromCommand(cmd)
                val verdict = AgentRuntime.check(call, state.effectiveChatMode)
                when (verdict.status) {
                    AgentRuntime.Status.BAD_ARGS, AgentRuntime.Status.BLOCKED ->
                        addAssistant(verdict.message + "。请换一种说法或调整设置后我再试。")
                    // 可逆低风险操作（点赞/收藏这类 toggle）：check 已判 EXECUTED，
                    // 免确认直接执行，结果走 AppState 里的轻提示，不弹确认卡
                    AgentRuntime.Status.EXECUTED -> {
                        val d = AgentRuntime.execute(
                            state, ctx, call, state.effectiveChatMode, confirmed = true
                        )
                        if (!d.ok) addAssistant("这个操作没成功：${d.message}。请换种说法再试。")
                    }
                    else -> pendingAction = PendingCommand(cmd)
                }
            }
            return
        }
        val obj = runCatching { org.json.JSONObject(decision.raw) }.getOrNull() ?: org.json.JSONObject()
        when (decision.action) {
            "record_footprint" -> {
                val bp = runCatching { AiApi.extractFootprint(endpoint, spec) }.getOrNull()
                if (bp != null && (bp.name.isNotBlank() || bp.city.isNotBlank())) {
                    pendingAction = PendingFootprint(bp)
                } else {
                    addAssistant(
                        tr(
                            "没提取出完整的地点信息，还差城市或地点名。告诉我「在哪座城市、哪个地方」，我就能记下来。",
                            "I couldn't extract a place. Tell me the city and the spot name and I'll record it."
                        )
                    )
                }
            }
            "import_plan" -> {
                val log = messages.value.dropLast(1)
                    .filter { it.text.isNotBlank() && it.text != GREETING }
                    .joinToString("\n") { (if (it.fromUser) "用户：" else "助手：") + it.text }
                val bp = runCatching {
                    AiApi.extractTripFromChat(
                        endpoint,
                        (log.ifBlank { userText }) + "\n（现场信息：${scene.hint()}）",
                        java.time.LocalDate.now().toString()
                    )
                }.getOrNull()
                if (bp != null) pendingAction = PendingTrip(bp)
                else addAssistant(
                    tr(
                        "对话里还没有能成形的逐日行程。先说一句「帮我规划大理 3 日游」，再说「写进行程里」就行。",
                        "There's no day-by-day itinerary yet. Ask me to plan a trip first, then say 'save it to my trips'."
                    )
                )
            }
            "publish_guide" -> {
                val bp = runCatching { AiApi.generateGuideFromLog(endpoint, spec) }.getOrNull()
                if (bp != null) pendingAction = PendingGuide(bp)
                else addAssistant(
                    tr(
                        "素材还不够写成一篇攻略。再说说去了哪、印象最深的是什么，我就能起草。",
                        "Not enough material for a guide yet. Tell me where you went and what stood out."
                    )
                )
            }
            "draw" -> {
                val imgEp = state.aiFeatureEndpoint(AiFeature.IMAGE_GEN)
                if (!imgEp.supports(com.example.my.data.AiCapability.IMAGE_GEN)) {
                    messages.value = messages.value + AiMsg(false, tr("还没有接入图片生成模型，请先在「设置 - API 模型管理」里添加一个图片模型", "No image model connected — add one in Settings → API models"))
                    return
                }
                if (!imgEp.ready) {
                    messages.value = messages.value + AiMsg(false, tr("图片模型「${imgEp.name}」还没填完整，请到「设置 - API 模型管理」补全 Key 或模型名", "Image model \"${imgEp.name}\" is incomplete — fill in its key or model name in Settings → API models"))
                    return
                }
                val prompt = obj.optString("prompt").trim().ifBlank { userText }
                val outcome = AiApi.generateImageOutcome(imgEp, prompt)
                val url = outcome.url
                if (url != null) {
                    val local = withContext(Dispatchers.IO) { downloadAiImage(ctx, url) }
                    messages.value = messages.value + AiMsg(false, "画好了，长按可删除", imagePath = local ?: url)
                } else {
                    messages.value = messages.value + AiMsg(
                        false,
                        tr("图片生成失败：", "Image generation failed: ") + outcome.error
                    )
                }
            }
            "video" -> {
                val videoEp = state.aiFeatureEndpoint(AiFeature.VIDEO_GEN)
                if (!videoEp.supports(com.example.my.data.AiCapability.VIDEO_GEN)) {
                    messages.value = messages.value + AiMsg(false, tr("还没有接入视频生成模型，请先在「设置 - API 模型管理」里添加一个视频模型", "No video model connected — add one in Settings → API models"))
                    return
                }
                if (!videoEp.ready) {
                    messages.value = messages.value + AiMsg(false, tr("视频模型「${videoEp.name}」还没填完整，请到「设置 - API 模型管理」补全 Key 或模型名", "Video model \"${videoEp.name}\" is incomplete — fill in its key or model name in Settings → API models"))
                    return
                }
                val prompt = obj.optString("prompt").trim().ifBlank { userText }
                messages.value = messages.value + AiMsg(false, "", phase = tr("视频生成中", "Generating video"))
                val url = try {
                    AiApi.generateVideo(videoEp, prompt)
                } finally {
                    // 占位气泡一定收掉，避免生成失败后一直显示“视频生成中”
                    if (messages.value.lastOrNull()?.phase != null) {
                        messages.value = messages.value.dropLast(1)
                    }
                }
                if (url != null) {
                    messages.value = messages.value + AiMsg(
                        false,
                        tr("视频生成好了，点下面的卡片用系统播放器打开。", "Video is ready — tap the card to open it in your player."),
                        videoUrl = url
                    )
                } else {
                    messages.value = messages.value + AiMsg(false, tr("视频生成失败了，请检查视频模型、生成路径或账户额度", "Video generation failed — check the model, path or account quota"))
                }
            }
        }
    }

    /** 通用流式回复：history 最后一条为本次用户消息，交给对话模型流式生成（完整历史全量发送） */
    fun streamAssistant(history: List<ChatTurn>) {
        val firstPhase = if (webSearchEnabled) "网页搜索中" else "思考中"
        messages.value = messages.value + AiMsg(false, "", phase = firstPhase)
        AiTaskCenter.setPhase(firstPhase)
        thinking = true
        persistChat()
        // 协程跑在应用级 scope：用户中途退页面/切 Tab 不取消，HTTP 流继续；
        // 回来时由下面的 LaunchedEffect 把 AiTaskCenter 里累积的文本接到最后一条气泡上。
        AiTaskCenter.beginChat()
        ownsGen = true
        AiTaskCenter.scope.launch {
            val buffer = StringBuilder()
            val thinkingBuffer = StringBuilder()
            var shown = 0
            var thinkingShown = 0
            var finished = false
            var failure: String? = null
            // 本轮耗时统计：检索时长（联网阶段）与思考作答时长（模型生成阶段），仅本轮展示
            val roundStartAt = SystemClock.elapsedRealtime()
            var searchMs: Long? = null
            var thinkStartAt: Long = 0
            /** 本轮 Bing 检索到的来源，生成结束后挂到回答气泡上 */
            var roundSources: List<WebHit> = emptyList()
            /** 本轮的证据卡：回答落定后用它做数字/引用校验，不通过就追加一条诚实提示 */
            var roundDeck: SeaDeck? = null
            val producer = launch {
                try {
                    // ⓪ 本地控制指令优先：用户说「打开声音播报」这类话，是一个要在后端执行的工作任务，
                    // 不是闲聊。直接改 AppState（UI 靠 Compose 状态自动刷新，前端无感、不用跳转），
                    // 然后本地拼一句确认秒回，不调 LLM。
                    val userText = history.lastOrNull()?.text.orEmpty()
                    tryApplyLocalControl(userText, state)?.let { ack ->
                        buffer.append(ack)
                        AiTaskCenter.appendChatChunk(ack)
                        return@launch
                    }
                    // ① 实时上下文（当前时间/天气，每次对话都现取，保证“问几点/问天气”答对）；
                    // ② 用户画像（来自 App 真实资料，稳定可靠）；③ 跨会话累积的长期记忆；
                    // ④ 轻量 RAG：App 内真实攻略摘要。合并注入 system，让助手"认识"用户且答得有据可依。
                    val liveBlock = buildLiveContext(state)
                    val profileBlock = buildProfileBlock(state)
                    val memoryBlock = if (state.aiMemoryEnabled) AiMemory.render(aiMemory) else ""
                    // ⑤ 意图纠错记忆：用户改过的判断作为 few-shot 注入，让模型学这个人的说话习惯
                    val intentBlock = AiIntentMemory.render(AiIntentMemory.load(ctx))
                    // ④ 轻量 RAG：目的地感知。用户提到某地时，优先挑出相关攻略（标题+简介），
                    // 让助手"我想去大理"直接关联 App 内已有笔记并给建议，而不是泛泛罗列。
                    val guideBlock = buildGuideBlock(state, userText)
                    val tripBlock = buildTripBlock(state, userText)
                    // 路由：选本地模型就只跑端侧；选「智能分配」时按任务复杂度分流——
                    // 短、单步、能靠本机上下文回答的问题走本机小模型，其余交云端。
                    // 智能分配模式里本机模型没就绪、用户关掉本机参与、或端侧没答出来时，
                    // 都自动降级到云端，不抛错。
                    val routeLocal = when (state.runMode) {
                        2 -> true
                        3 -> state.aiRoutingLocal &&
                            state.localLlmState == LocalLlmState.READY &&
                            isSimpleLocalQuery(userText)
                        else -> state.aiFeatureUsesLocal(AiFeature.ASSISTANT)
                    }
                    // 对话级明确选了本机（runMode=2）或智能分配（runMode=3）时仍用当前启用模型；
                    // 手动分配里给「对话」指定了具体本地模型时，按那个模型推理。
                    val localModelId = if (state.runMode == 2 || state.runMode == 3) null
                    else state.aiFeatureLocalModelId(AiFeature.ASSISTANT)
                    // 能力网关跟着本轮实际路由走：走本机时按 LOCAL 拦联网能力，
                    // 不能因为 runMode=3 就误判成云端而把联网工具放进来。
                    val mode = if (routeLocal) ChatMode.LOCAL else ChatMode.API
                    // ⑥ 检索路由：联网总开关打开后，所有问题一律进入网页检索。
                    // 不再让规则/模型决定「这轮要不要搜」，避免寒暄、创作、本地数据
                    // 等场景被静默跳过；票务仍标记 preferInternal，内置接口优先，
                    // 但网页检索照常发生。
                    val searchDecision = if (webSearchEnabled) {
                        SearchGate.force(userText)
                    } else SearchGate.WEB_OFF
                    // 联网总开关就是唯一闸门：打开后不再让 searchDecision/needsWeb
                    // 二次判断把寒暄、创作、本地数据或票务问题悄悄跳过。
                    val allowWebThisTurn = webSearchEnabled
                    // 票务查询：网页搜索关掉，但允许 App 内置的票务接口联网取真实余票/报价。
                    // 联网总开关仍然是一票否决，关掉时两条链路都不发请求。
                    val allowNetworkThisTurn = webSearchEnabled
                    // ⑤ 工具调用（Function Calling）：先预检这条消息要不要联网搜索 / 查本地足迹，
                    // 需要就先执行工具拿到真实数据，再让模型带着这些数据生成回答。
                    // 纯本地模式禁止联网（那是用户的隐私取舍），只保留读本地的足迹统计。
                    val searchStartedAt = SystemClock.elapsedRealtime()
                    // 简单问题轻量搜、复杂问题才深搜：都由 SearchGate 的 depth 决定，
                    // 不再让所有问题共用同一个档位。
                    val freshNews = searchMode == SearchMode.FAST &&
                        WebSearch.isTimeSensitive(userText)
                    val profile = WebSearch.SearchProfile.forDecision(
                        searchMode,
                        searchDecision.depth,
                        freshNews
                    )
                    when {
                        !webSearchEnabled -> Unit
                        searchDecision.preferInternal -> setPhase("查询内置票务与网页")
                        allowWebThisTurn -> setPhase("网页搜索中 · ${searchDecision.depth.zh}")
                        else -> setPhase("无需联网，直接回答")
                    }
                    val wantEvidenceTool = state.aiCapabilities.evidenceToolLoop &&
                        !routeLocal &&
                        endpoint.format == com.example.my.data.AiFormat.OPENAI &&
                        (endpoint.apiKey.isNotBlank() || endpoint.builtin)
                    // 只有确实会检索、且云端工具循环可用时，才把第一段提前到工具循环之前。
                    // 开关关闭 / 本地模型 / 非 OpenAI 协议仍保持原来的「先工具、后检索」顺序。
                    val preSearch = if (
                        wantEvidenceTool && allowWebThisTurn && userText.isNotBlank()
                    ) {
                        buildSearchBlock(
                            state, ctx, userText,
                            isLocal = routeLocal,
                            localModel = runCatching { LocalLlm.activeSpec(ctx).display }.getOrDefault(""),
                            profile = profile,
                            extraQueries = searchDecision.queries
                        )
                    } else null
                    val evidenceKey = preSearch?.evidenceKey
                    val useEvidenceTool = wantEvidenceTool && evidenceKey != null
                    val wantMcpSearch = state.aiCapabilities.mcpSearch &&
                        !routeLocal &&
                        endpoint.format == com.example.my.data.AiFormat.OPENAI &&
                        (endpoint.apiKey.isNotBlank() || endpoint.builtin) &&
                        allowNetworkThisTurn &&
                        !searchDecision.preferInternal
                    if (useEvidenceTool) setPhase("按需查证据")
                    val toolOutcome = AiApi.toolContext(
                        ep = endpoint,
                        system = AiApi.systemPrompt,
                        history = history,
                        appContext = ctx,
                        footprints = state.footprints,
                        // 开关拨到「离线」就整条链路都不联网（工具 + 自动检索），
                        // 这是用户的隐私取舍，不能出现「开关关了但工具还在偷偷联网」。
                        // 开关开着时交给检索路由：判断要搜才放行网页搜索；票务类问题
                        // 只放行 App 内置票务接口，不把网页搜索工具交给模型。
                        // 开了「边思考边查」时本轮网页已由第一段查完，工具循环只读临时库，
                        // 不再让模型重复发一次网页搜索；第一段没留下证据时也一样，避免重复抓取。
                        allowWeb = if (preSearch != null) false else allowWebThisTurn,
                        allowNetwork = allowNetworkThisTurn,
                        preferInternal = searchDecision.preferInternal,
                        state = state,
                        chatMode = mode,
                        evidenceKey = evidenceKey,
                        allowEvidenceTool = useEvidenceTool,
                        allowMcpSearch = wantMcpSearch
                    )
                    val toolBlock = toolOutcome.contextBlock
                    // 失败不静默：有工具没查到/被拦截，轻提示用户，并明确告诉模型这些实时数据不可用，
                    // 不得假装已经拿到
                    val failureNote = if (toolOutcome.hasFailure) {
                        state.toast("部分实时查询失败，以下回答基于通用知识")
                        "【以下实时工具未成功，回答时不得假装已取得这些数据，请基于通用知识并如实说明】\n" +
                            toolOutcome.failures.joinToString("；")
                    } else null
                    // 未提前检索时，保持原来的「工具链没搜过再补一次精准检索」；
                    // 已提前检索的「边思考边查」路径不重复查。
                    val autoSearch = if (
                        preSearch == null && allowWebThisTurn &&
                        toolBlock?.contains("网页搜索结果") != true && userText.isNotBlank()
                    ) {
                        buildSearchBlock(
                            state, ctx, userText,
                            isLocal = routeLocal,
                            localModel = runCatching { LocalLlm.activeSpec(ctx).display }.getOrDefault(""),
                            profile = profile,
                            extraQueries = searchDecision.queries
                        )
                    } else null
                    val toolAlreadySearched = toolBlock?.contains("网页搜索结果") == true
                    roundDeck = preSearch?.deck ?: autoSearch?.deck ?: if (toolAlreadySearched && allowWebThisTurn) {
                        val hits = withTimeoutOrNull(profile.searchTimeoutMs) {
                            WebSearch.search(userText, profile.limit, profile, searchDecision.queries)
                        }.orEmpty()
                        if (hits.isEmpty()) null else SeaFusion.build(
                            query = userText,
                            hits = hits,
                            ep = endpoint,
                            isLocal = routeLocal,
                            localModel = runCatching { LocalLlm.activeSpec(ctx).display }.getOrDefault(""),
                            config = SearchCleaner.Config(maxEvidence = 6),
                            adjudicatorConfig = EvidenceAdjudicator.Config(maxClaims = 6)
                        )
                    } else null
                    roundSources = preSearch?.sources ?: autoSearch?.sources ?: SeaFusion.sources(roundDeck)
                    // 检索 + 清洗 + 证据裁决对用户就是两段：先「网页搜索中」，再「获取相关知识」。
                    // 只保底 260ms 让状态切换不闪跳，不再人为拖慢（以前每段强等 900ms，白等近 2 秒）。
                    if (allowWebThisTurn) {
                        val spent = SystemClock.elapsedRealtime() - searchStartedAt
                        searchMs = spent
                        if (spent < 260L) delay(260L - spent)
                        setPhase("获取相关知识")
                        delay(260L)
                    }
                    // 模型已经通过 lookup_evidence 真取到证据时，不再把第一段的整块事实卡
                    // 重复塞进上下文；没有取到/没启用工具时仍保留原两段式材料，避免答空。
                    val searchBlock = if (useEvidenceTool && toolOutcome.evidenceHit) {
                        listOfNotNull(
                            "【临时证据库已准备】本轮已完成检索与清洗；" +
                                "模型已通过 lookup_evidence 按需取用命中证据，未重复注入全量事实卡。",
                            preSearch?.extraBlock
                        ).joinToString("\n\n---\n\n")
                    } else {
                        preSearch?.block ?: autoSearch?.block
                    }
                    // 现场快照 + 能力清单 + 操作回执：让模型知道「在哪一页、能做什么、刚才做成了没」
                    val sceneBlock = AppContextSnapshot.render(state, ctx)
                    val capabilityBlock = CapabilityRegistry.describe()
                    val receiptBlock = AgentRuntime.renderReceipts()
                    val ctxAll = listOfNotNull(
                        liveBlock, profileBlock, sceneBlock, capabilityBlock,
                        memoryBlock, intentBlock, guideBlock, tripBlock,
                        toolBlock, failureNote, searchBlock, receiptBlock
                    ).joinToString("\n\n").ifBlank { null }
                    // 本地小模型没有 system 通道，把实时时间/天气作为上下文前缀拼进提问，
                    // 否则端侧模型同样不知道现在几点、当地几度。工具结果/失败说明/联网结果一样拼进去
                    // （端侧上下文小，网页材料截断到 1200 字，够它抓住要点又不至于爆上下文）。
                    val searchBlockLimit = preSearch?.contextChars?.takeIf { it > 0 }
                        ?: autoSearch?.contextChars?.takeIf { it > 0 }
                        ?: profile.contextChars
                    val localPrompt = listOfNotNull(
                        liveBlock, toolBlock, failureNote,
                        searchBlock?.let { if (it.length > searchBlockLimit) it.take(searchBlockLimit) else it }
                    ).joinToString("\n\n") + "\n\n用户现在说：$userText"
                    // 材料齐了，开始让模型推理作答
                    thinkStartAt = SystemClock.elapsedRealtime()
                    setPhase("思考中")
                    val ticketBlock = toolOutcome.ticketBlock
                    val localReply = if (routeLocal) {
                        LocalLlm.chatOnce(ctx, localPrompt, localModelId)
                    } else null
                    when {
                    localReply != null -> {
                        // 本地返回格式：[\u0001思考\u0001正文] / [\u0001思考] / [正文]
                        // 思考进「思考气泡」，正文才进用户看到的回答，
                        // 防止 Qwen3 这类思考型小模型把推理过程直接吐给用户
                        val segs = localReply.split("\u0001", limit = 3)
                        when {
                            segs.size >= 3 -> {
                                thinkingBuffer.append(segs[1].trim())
                                buffer.append(segs[2].trim())
                            }
                            segs.size == 2 -> {
                                // 只有思考、没有正文：小模型想太多撞了 token 上限
                                thinkingBuffer.append(segs[1].trim())
                            }
                            else -> buffer.append(segs[0].trim())
                        }
                        // 兜底：小模型只输出了思考、没组织出正文时，
                        // 优先用已经查到的真实车次/航班清单，别拿天气文案糊弄票务问题。
                        if (buffer.isBlank()) {
                            if (ticketBlock != null) {
                                buffer.append(ticketBlock)
                            } else {
                                val now = SimpleDateFormat("M月d日 EEEE HH:mm", Locale.CHINA)
                                    .format(Date(System.currentTimeMillis()))
                                val w = state.weatherNow
                                val auto = state.runMode == 3
                                val tail = if (auto)
                                    "（本机小模型没组织出回答，已用实时信息直接作答；更复杂的问题会自动交给云端。）"
                                else
                                    "（当前本地小模型只输出了思考、没组织出回答，这是我从本机实时信息里直接取给你的；想聊更复杂的可以切回云端模型。）"
                                buffer.append(
                                    if (w != null)
                                        "现在是 $now，${w.city} ${w.temp}℃ ${w.emoji}${w.desc}。$tail"
                                    else
                                        "现在是 $now。$tail"
                                )
                            }
                        }
                    }
                    state.runMode == 2 -> throw IllegalStateException(
                        when (LocalLlm.state(ctx)) {
                            LocalLlmState.ENGINE_MISSING -> tr(
                                "本地模型跑不起来：当前安装包未内置 MNN 推理引擎。点对话框底部换一个云端模型就能继续。",
                                "On-device engine missing. Pick a cloud model at the bottom of this chat."
                            )
                            else -> tr(
                                "「${LocalLlm.activeSpec(ctx).display}」还没就绪（模型未下载完整或加载失败）。点对话框底部换一个已下载的模型或云端模型；也可以到「设置 - 本地模型管理」下载完整。",
                                "${LocalLlm.activeSpec(ctx).display} is not ready. Pick another model at the bottom of this chat, or finish the download in Settings."
                            )
                        }
                    )
                        else -> {
                            // 未接入任何云端端点时不发空请求，直接引导用户去模型管理配置
                            if (!endpoint.ready) {
                                throw IllegalStateException(
                                    tr(
                                        "还未接入云端模型。请到「设置 - API 模型管理」添加自己的 API 端点（OpenAI 兼容地址、Key、模型名），或在对话框底部切换到已下载的本地模型。",
                                        "No cloud model connected. Add your API endpoint in Settings → API models, or switch to a downloaded on-device model."
                                    )
                                )
                            }
                            // 思考型模型（Qwen3 / DeepSeek-R1 等）会先输出 <think>...</think> 再答。
                            // 流式逐 chunk 到达，标签可能被切断，用剥离器边收边把思考挡在正文外：
                            // 正文实时进回答气泡，思考实时进「思考气泡」，绝不漏给用户。
                            val stripper = ThinkTagStripper(
                                onAnswer = { piece ->
                                    buffer.append(piece)
                                    // 镜像到应用级：退页面再回来时靠它续显（只镜像正文）
                                    AiTaskCenter.appendChatChunk(piece)
                                },
                                onThink = { piece -> thinkingBuffer.append(piece) }
                            )
                            AiApi.chatStream(endpoint, history, ctxAll, settingsContext = ctx).collect { chunk ->
                                // 旧协议：\u0001 开头表示进入思考段；新模型的 <think> 交给剥离器
                                if (chunk.startsWith("\u0001")) {
                                    thinkingBuffer.append(chunk.removePrefix("\u0001"))
                                } else {
                                    stripper.feed(chunk)
                                }
                            }
                            stripper.flush()
                        }
                    }
                    // 票务结果兜底：模型只回「这就为你查询」、或只列了其中几趟时，
                    // 把 App 真实查到的清单原样补在回答末尾，保证用户能看到有哪些票。
                    if (ticketBlock != null) {
                        val codes = Regex("""(?:[GDCZTKLSY]\d{1,4}|[A-Z0-9]{2}\d{3,4})""")
                            .findAll(ticketBlock).map { it.value }.distinct().toList()
                        if (codes.isNotEmpty() && codes.any { !buffer.contains(it) }) {
                            buffer.append("\n\n完整车次列表：\n").append(ticketBlock)
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    failure = e.message ?: tr("网络异常")
                } finally {
                    finished = true
                }
            }
            while (!finished || shown < buffer.length) {
                if (shown < buffer.length || thinkingShown < thinkingBuffer.length) {
                    // 30fps 足够打字感：原来 18ms/帧会让几千字的 Text 每帧重测布局
                    shown = minOf(buffer.length, shown + 4)
                    thinkingShown = thinkingBuffer.length
                    applyLastText(
                        buffer.substring(0, shown),
                        if (thinkingBuffer.isNotEmpty()) thinkingBuffer.toString() else null
                    )
                    delay(30)
                } else delay(60)
            }
            producer.join()
            // 正文写完，进入最后的校验与整合（数字/引用核对、控制标记剥离）
            setPhase("答案整合中")
            delay(800)
            if (buffer.isEmpty()) {
                applyLastText(
                    when {
                        // 本地模式下手里的失败原因就是结论（引擎/模型没就绪、加载失败），
                        // 直接原样给他，别套上「没能连上模型」这种误导性的网络话术
                        state.runMode == 2 && failure != null -> failure
                        failure?.contains("429") == true ->
                            // 限流：已经后台重试过 2 次，提示用户稍等，别误导去检查 Key
                            "模型这会儿限流了（HTTP 429），已自动重试仍未恢复。\n等 10 秒左右再发一次就好，免费模型高峰期较挤。"
                        else -> "抱歉，没能连上模型（${failure ?: "网络异常"}）。"
                    }
                )
            } else if (failure != null) {
                applyLastText(buffer.toString() + "\n\n（连接中断，可重新发送）")
            }
            val lastUser = history.lastOrNull()?.text.orEmpty()
            // 气泡里的文本已经在流式阶段剥过标记了，抽取记忆/追问必须用原始 buffer
            val rawReply = buffer.toString().ifBlank { messages.value.last().text }
            // 意图由模型在 <intent> 标记里自己判定（兼容旧 <action>），代码只负责解析与执行
            val intent = AiIntent.parse(rawReply)
            // [记忆] / [追问] / <intent> / <action> 都是给 App 的指令行，不该出现在气泡里
            val reply = stripControlMarkers(rawReply)
            intentQuestion = lastUser
            // 先别塞一个假的「日常对话」：主回复自报了才显示，没自报等 API 判完再显示
            lastIntent = intent
            // 语音播报：回复完整落定后朗读（失败/中断不读，避免念半句报错文案）
            if (state.aiCapabilities.voiceTts && failure == null && reply.isNotBlank()) {
                // 引擎缺失时不静默失败：以前没声音也一声不吭，用户只会觉得「语音坏了」
                if (!VoiceIo.speak(ctx, reply, state.aiLanguage)) {
                    state.toast(
                        tr(
                            "这台手机没有可用的语音引擎，朗读失败（可在系统设置里安装文字转语音）",
                            "No TTS engine available — install one in system settings"
                        )
                    )
                }
            }
            val matched = state.guides.firstOrNull {
                lastUser.contains(it.city) || reply.contains(it.city) || it.title.contains(lastUser, ignoreCase = true)
            }
            // 长期记忆双通道：模型打的 [记忆] 标记 + 本地规则（自定义网关和端侧小模型不会吐标记，
            // 只靠标记等于这类用户永远记不住东西）。合并时去重、累加命中次数、超限淘汰。
            val learned = if (state.aiMemoryEnabled) {
                AiMemory.fromReply(rawReply) + AiMemory.extractLocal(lastUser)
            } else emptyList()
            if (learned.isNotEmpty()) {
                val merged = AiMemory.merge(aiMemory, learned)
                if (merged != aiMemory) {
                    aiMemory = merged
                    scope.launch { Persistence.saveAiMemories(ctx, merged) }
                }
            }
            // 生成后校验：回答里的价格/温度/评分等"带单位的具体数字"必须能在本轮证据里找到，
            // 引用编号必须落在来源表范围内。不通过时不偷偷改文案，而是在气泡末尾追加一条诚实提示。
            // 联网开着、本轮也挂了参考来源，模型却说「网上暂未查到具体信息」：
            // 端侧 0.6B 模型最容易把 prompt 里的负面指令当模板复读。用网页摘要
            // 机械重列一份，保证回答与来源一致，用户不会看到自相矛盾的对话。
            val grounded = SeaFusion.ensureGrounded(reply, roundSources, lastUser)
            // 机械兜底本身就是网页摘录（年份/序号来自原文），不必再跑数字审计
            val guardNote = if (grounded == reply) SeaFusion.auditAnswer(reply, roundDeck).note() else ""
            val shownReply = grounded.ifBlank { "（没有收到内容，请再试一次）" } +
                if (guardNote.isBlank()) "" else "\n\n$guardNote"
            val finalThinking = thinkingBuffer.toString().trim().takeIf { it.isNotBlank() }
            // 思考时长：优先用「材料齐备 → 落定」的作答段；没记到起点时回退整轮耗时，保证总有值可显示
            val finalThinkMs = when {
                thinkStartAt > 0 -> SystemClock.elapsedRealtime() - thinkStartAt
                else -> SystemClock.elapsedRealtime() - roundStartAt
            }.coerceAtLeast(0)
            messages.value = messages.value.dropLast(1) +
                AiMsg(
                    false,
                    shownReply,
                    matched?.id,
                    thinking = finalThinking,
                    sources = roundSources,
                    searchMs = searchMs,
                    thinkMs = finalThinkMs
                )
            AiTaskCenter.setPhase(null)
            thinking = false
            // 意图判断与「生成回复的模型」解耦：主回复自报了 <intent> 就直接用；
            // 没自报（端侧小模型、自定义网关、模型没守协议）或这一轮回复生成失败，
            // 也一律用 API 模型补判一次，判完立刻按判断处理这条消息。
            // 绝不因为「所选模型没就绪」就把消息丢掉、把意图默认成日常对话。
            scope.launch {
                // 先看本地规则：短句 + 明确动词的设置/页面指令不需要再花一次模型请求去判
                val local = intent ?: LocalCommandRules.parse(lastUser)?.let {
                    IntentDecision(
                        category = IntentCategory.fromAction(it.kind),
                        action = "command",
                        target = it.target,
                        value = it.value,
                        kind = it.kind,
                        label = it.label,
                        confidence = 0.9
                    )
                }
                val decision = local ?: judgeByApi(lastUser)
                if (decision != null) {
                    lastIntent = decision
                    executeIntent(decision, lastUser)
                } else {
                    // 连 API 都连不上才用离线关键词：只给类别与旧分支，不发云端请求
                    val route = classify(lastUser)
                    lastIntent = IntentDecision(categoryForRoute(route), route)
                    if (route != "chat") runFallbackRoute(route, lastUser)
                }
                persistChat()
            }
            // 按现场补一条能真正推进话题的可执行动作，不依赖模型是否守 <next> 协议
            fun sceneStep(userText: String, replyText: String): NextStep {
                val scene = AiResolution.facts(state, "$userText\n$replyText")
                return when {
                    scene.guideTitle.isNotBlank() -> NextStep(
                        tr("收藏这篇攻略", "Save this guide"), "toggle_fav",
                        mapOf("keyword" to scene.guideTitle)
                    )
                    scene.focusCity.isNotBlank() -> NextStep(
                        tr("查${scene.focusCity}天气", "Weather in ${scene.focusCity}"),
                        "query_weather_city", mapOf("city" to scene.focusCity)
                    )
                    else -> NextStep(tr("看看我的足迹统计", "My footprint stats"), "query_footprints")
                }
            }
            // 延伸建议：优先模型给的 <next>（可点击、能直接调度能力），再退 [追问] 与本地规则；
            // 复合请求吐的 <plan> 挂成一张可确认的计划卡。回复失败时不基于报错文案生成追问，
            // 但照样按现场留一条能执行的动作，别让这一轮停在报错上。
            if (failure != null) {
                clearNext()
                nextSteps = listOf(sceneStep(lastUser, ""))
            } else {
                nextSteps = AiIntent.parseNext(rawReply).ifEmpty {
                    extractFollowUps(rawReply).ifEmpty { smartSuggestions(lastUser, reply) }
                        .map { NextStep(it, prompt = it) }
                }
                if (nextSteps.none { it.isAction }) {
                    nextSteps = nextSteps + sceneStep(lastUser, reply)
                }
                planProposal = AiIntent.parsePlan(rawReply)
            }
            persistChat()
            AiTaskCenter.finishChat(failure)
            ownsGen = false
        }
    }

    /** 普通对话：流式回复（完整历史全量发送，不限制上下文大小） */
    fun chatReply(text: String) {
        // messages 末尾就是刚追加的本次用户消息，先把它摘掉，再组装「本次输入之前」的历史。
        // 不能用 filter + dropLast(1)：上一条助手消息若是空文本（流式中途失败留下的空壳）
        // 会被 filter 去掉，dropLast(1) 就会误删上一轮的用户提问。
        val prior = messages.value.dropLast(1)
        val rawHistory = prior.mapNotNull { m ->
            when {
                m.text == GREETING -> null
                m.text.isNotBlank() -> ChatTurn(m.fromUser, m.text)
                // 纯图片消息（text 为空）或用户侧空消息：用占位描述保留
                m.fromUser -> ChatTurn(true, if (m.imagePath != null) tr("[图片]") else tr("（无文字内容）"))
                // 助手侧空文本（未完成的流）不进历史，避免污染上下文
                else -> null
            }
        } + ChatTurn(true, text)
        streamAssistant(rawHistory)
    }

    /**
     * 重新生成最后一条回复：丢掉它，用对应的用户提问重跑。
     * 以前回答不满意只能重新打字，等于把用户的话又抄一遍——这是最影响手感的一处。
     */
    fun regenerateLast() {
        if (thinking) { state.toast(tr("正在回复中，请稍候")); return }
        val list = messages.value
        val lastIdx = list.indexOfLast { !it.fromUser }
        if (lastIdx < 0) return
        val userIdx = (lastIdx - 1 downTo 0).firstOrNull { list[it].fromUser } ?: return
        val question = list[userIdx]
        if (question.imagePath != null) { state.toast(tr("图片消息暂不支持重新生成")); return }
        // 截断到提问那条为止：旧回复必须一并删掉，否则会留下两份答案
        messages.value = list.take(userIdx + 1)
        pendingDelete = null
        clearNext()
        chatReply(question.text)
    }

    /** 豆包式文本工具（翻译/润色/总结/续写）：把工具指令拼进用户消息，交给对话模型 */
    fun toolReply(tool: String, text: String) {
        // 输入框为空时，回退取最近一条用户消息作为待处理内容
        val fallback = messages.value.lastOrNull { it.fromUser && it.text.isNotBlank() }?.text
        val source = text.ifBlank { fallback.orEmpty() }
        if (source.isBlank()) {
            state.toast(tr("请先在输入框写下要处理的内容"))
            showTools = false
            return
        }
        val instruction = when (tool) {
            "翻译" -> tr("请把下面这段内容翻译成另一种语言（若原文是中文则译为英文，否则译为中文），只输出译文，不要多余解释：")
            "润色" -> tr("请润色下面这段文字，让表达更通顺、更有感染力，保持原意与语气，只输出润色结果：")
            "总结" -> tr("请用简洁的要点概括下面这段内容，突出核心信息，只输出总结：")
            "续写" -> tr("请顺着下面这段内容自然地续写下去，保持一致的风格与主题：")
            else -> tr("请处理下面这段内容：")
        }
        // 界面只显示原文，实际发送时带上工具指令
        messages.value = messages.value + AiMsg(true, source)
        input = ""
        showTools = false
        clearNext()
        val prior = messages.value.dropLast(1).mapNotNull { m ->
            when {
                m.text == GREETING -> null
                m.text.isNotBlank() -> ChatTurn(m.fromUser, m.text)
                else -> null
            }
        }
        streamAssistant(prior + ChatTurn(true, "$instruction\n$source"))
    }

    /** 拍照：写入 cache 目录 + FileProvider 拉起系统相机 */
    fun launchCamera() {
        val dir = File(ctx.cacheDir, "images").apply { mkdirs() }
        val f = File(dir, "cam_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        camUri = uri
        cameraLauncher.launch(uri)
    }

    /** 复制某条消息文本到剪贴板 */
    fun copyMessage(index: Int) {
        val msg = messages.value.getOrNull(index) ?: return
        // 助手消息可能还带着 <意图>/<下一步> 等控制块，复制给用户前先清掉
        val plain = if (msg.fromUser) msg.text else stripControlMarkers(msg.text)
        val text = plain.ifBlank { if (msg.imagePath != null) "图片消息" else "（无内容）" }
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText(tr("AI消息"), text))
        state.toast(tr("已复制"))
    }

    fun confirmAction() {
        // 高风险操作分两步：第一下只把「再次确认」亮起来，绝不碰数据。
        // 危险等级由能力表推导，UI 不自己维护一份名单。
        if (pendingAction?.isDangerous() == true && !dangerArmed) {
            dangerArmed = true
            return
        }
        val armed = dangerArmed
        dangerArmed = false
        when (val a = pendingAction) {
            is PendingGuide -> {
                val id = state.publishGuide(a.bp)
                pendingAction = null
                state.openGuide(id)
                state.closeScreen()
            }

            is PendingFootprint -> {
                state.applyFootprintBlueprint(a.bp)
                pendingAction = null
                state.toast(tr("足迹已添加到地图"))
            }

            is PendingTrip -> {
                state.applyTripBlueprint(a.bp)
                pendingAction = null
            }

            is PendingCommand -> {
                val cmd = a.intent
                pendingAction = null
                val call = AgentRuntime.callFromCommand(cmd)
                val mode = state.effectiveChatMode
                val verdict = AgentRuntime.check(call, mode, confirmed = true, doubleConfirmed = armed)
                when (verdict.status) {
                    AgentRuntime.Status.EXECUTED -> scope.launch {
                        // 统一网关执行并留下回执，下一轮对话会把结果回灌给模型
                        val d = AgentRuntime.execute(
                            state, ctx, call, mode, confirmed = true, doubleConfirmed = armed
                        )
                        if (!d.ok) state.toast(d.message)
                    }

                    // 能力表里没有登记的动作：不再绕过网关直接执行，
                    // 明确告知并记录能力缺口，避免留下一条不受分级/确认/回执约束的旁路。
                    AgentRuntime.Status.NOT_FOUND -> {
                        AgentRuntime.noteGap(call.id)
                        state.toast("这个操作暂不支持通过助手执行")
                    }
                    else -> state.toast(verdict.message)
                }
                // 退出应用：真正 finish 掉 Activity（applyCommand 里只做了提示）
                if (cmd.kind == "exit_app") {
                    (ctx as? android.app.Activity)?.finish()
                }
            }

            is PendingCapability -> {
                pendingAction = null
                val mode = state.effectiveChatMode
                scope.launch {
                    val d = AgentRuntime.execute(
                        state, ctx, a.call, mode, confirmed = true, doubleConfirmed = armed
                    )
                    addAssistant(
                        if (d.ok) tr("已完成：", "Done: ") + d.message.ifBlank { a.label }
                        else tr("没做成：", "Couldn't: ") + d.message
                    )
                    persistChat()
                }
            }

            null -> {}
        }
    }

    fun cancelAction() {
        pendingAction = null
        planProposal = null
        dangerArmed = false
        addAssistant(tr("好的，已取消该操作。"))
    }

    /** 发送：根据有无图片 / 意图分流 */
    fun send() {
        val text = input.trim()
        if (text.isEmpty() && attachedUri == null) return
        if (thinking || !restored) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        clearNext()
        showTools = false

        // 「算了 / 取消」和「继续 / 接着做」是现场就能正确理解的短句，
        // 不该再发给模型重新判一遍，更不该把上一轮没确认的动作继续执行。
        if (attachedUri == null) {
            if (AiIntent.isCancellation(text)) {
                val had = pendingAction != null || planProposal != null
                pendingAction = null
                clearNext()
                input = ""
                addAssistant(
                    if (had) tr("好，已取消，刚才那一步没有执行。", "OK, cancelled. Nothing was changed.")
                    else tr("好的，先不做。", "Sure, let's skip it.")
                )
                persistChat()
                return
            }
            val pending = planProposal
            if (AiIntent.isContinuation(text) && pending != null) {
                input = ""
                runPlan(pending)
                return
            }
        }

        val uri = attachedUri
        if (uri != null) {
            // 图片先落盘：这样气泡能立刻显示，且随对话持久化后重进仍在
            val bmp = decodeUriBitmap(ctx, uri)
            if (bmp == null) {
                state.toast(tr("图片读取失败，请换一张试试"))
                return
            }
            val path = saveAiImage(ctx, bmp)
            val bytes = compressJpeg(bmp)
            bmp.recycle()
            messages.value = messages.value + AiMsg(true, text, imagePath = path)
            input = ""
            attachedUri = null
            attachedBitmap = null
            pendingAction = null

            val visionEp = state.aiFeatureEndpoint(AiFeature.VISION)
            if (!visionEp.supports(com.example.my.data.AiCapability.VISION) || !visionEp.ready) {
                addAssistant(tr("还未接入支持看图的云端模型，请先在「模型管理」里添加端点，并在模型上勾选「图像识别」", "No vision model connected — add an endpoint and enable \"Vision\" on it in Model settings"))
                persistChat()
                return
            }
            if (bytes == null) {
                addAssistant(tr("图片压缩失败，请换一张试试。"))
                return
            }
            // 先插入助手占位气泡：流式文本写到这里，结束时只替换占位，保留用户这条消息
            messages.value = messages.value + AiMsg(false, "", phase = "思考中")
            thinking = true
            scope.launch {
                val buffer = StringBuilder()
                val thinkBuf = StringBuilder()
                var finished = false
                var failure: String? = null
                val prod = launch {
                    try {
                        AiApi.visionStream(
                            visionEp,
                            Base64.encodeToString(bytes, Base64.NO_WRAP),
                            "image/jpeg",
                            text,
                            onUsage = {}
                        ).collect { chunk ->
                            // 思考过程单独收起，正文照常进气泡
                            if (chunk.startsWith("\u0001")) thinkBuf.append(chunk.removePrefix("\u0001"))
                            else buffer.append(chunk)
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        failure = e.message ?: tr("网络异常")
                    } finally {
                        finished = true
                    }
                }
                var shown = 0
                while (!finished || shown < buffer.length) {
                    if (shown < buffer.length) {
                        shown = minOf(buffer.length, shown + 4)
                        applyLastText(buffer.substring(0, shown))
                        delay(30)
                    } else delay(60)
                }
                prod.join()
                val reply = buffer.toString()
                messages.value = messages.value.dropLast(1) + AiMsg(
                    false,
                    when {
                        reply.isNotBlank() -> stripControlMarkers(reply)
                        failure?.contains("429") == true ->
                            "模型这会儿限流了（HTTP 429），已自动重试仍未恢复。\n等 10 秒左右重新发送图片即可。"
                        else ->
                            "图片识别失败：${failure ?: "网络异常"}\n请稍后重试，或换一张更清晰的图片。"
                    },
                    thinking = thinkBuf.toString().trim().takeIf { it.isNotBlank() }
                )
                thinking = false
                // 发图后主动追问：这是哪里 / 写成攻略 / 记成足迹
                messages.value = messages.value + AiMsg(
                    false,
                    "这张照片你想怎么处理？我可以：①认出这是哪里并查攻略；②帮你写成一篇旅行攻略；③记成一条足迹到地图。点下面的快捷按钮即可。"
                )
                suggestions = listOf("这是哪里？帮我查一下", "把这里写成一篇攻略", "把这里记成一条足迹")
                persistChat()
            }
            return
        }

        messages.value = messages.value + AiMsg(true, text)
        input = ""
        pendingAction = null
        lastIntent = null
        clearNext()

        // 纯事实题（现在几点 / 当地几度）的答案由本地时钟与天气实况算，杜绝模型编数字。
        // 这是「答案计算」，不是意图路由；六类意图仍由模型在对话里判定。
        scope.launch {
            val fact = buildFactAnswer(state, text)
            if (fact != null) {
                intentQuestion = text
                lastIntent = IntentDecision(IntentCategory.QUERY, "none")
                addAssistant(fact)
                return@launch
            }

            // 文生图 / 文生视频走用户在「API 模型管理」里接入的媒体模型，
            // 保留快捷通道避免自定义网关不支持 <intent> 时这两个能力完全失效。
            val isVideo = text.contains("生成视频") || text.contains("做个视频") || text.contains("做一段视频")
            val isDraw = !isVideo && (text.contains("画") || text.contains("生成图片") || text.contains("生成一张")
                    || text.contains("来一张图") || text.contains("做一张图"))
            if (isDraw || isVideo) {
                thinking = true
                if (isVideo) {
                    val videoEp = state.aiFeatureEndpoint(AiFeature.VIDEO_GEN)
                    if (!videoEp.supports(com.example.my.data.AiCapability.VIDEO_GEN)) {
                        addAssistant(tr("还没有接入视频生成模型，请先在「设置 - API 模型管理」里添加一个视频模型", "No video model connected — add one in Settings → API models"))
                    } else if (!videoEp.ready) {
                        addAssistant(tr("视频模型「${videoEp.name}」还没填完整，请到「设置 - API 模型管理」补全 Key 或模型名", "Video model \"${videoEp.name}\" is incomplete — fill in its key or model name"))
                    } else {
                        messages.value = messages.value + AiMsg(false, "", phase = tr("视频生成中", "Generating video"))
                        val url = try {
                            AiApi.generateVideo(videoEp, text)
                        } finally {
                            if (messages.value.lastOrNull()?.phase != null) {
                                messages.value = messages.value.dropLast(1)
                            }
                        }
                        if (url != null) {
                            messages.value = messages.value + AiMsg(
                                false,
                                tr("视频生成好了，点下面的卡片用系统播放器打开。", "Video is ready — tap the card to open it in your player."),
                                videoUrl = url
                            )
                        } else {
                            addAssistant(tr("视频生成失败了，请检查视频模型、生成路径或账户额度", "Video generation failed — check the model, path or account quota"))
                        }
                    }
                } else {
                    val imgEp = state.aiFeatureEndpoint(AiFeature.IMAGE_GEN)
                    if (!imgEp.supports(com.example.my.data.AiCapability.IMAGE_GEN)) {
                        addAssistant(tr("还没有接入图片生成模型，请先在「设置 - API 模型管理」里添加一个图片模型", "No image model connected — add one in Settings → API models"))
                    } else if (!imgEp.ready) {
                        addAssistant(tr("图片模型「${imgEp.name}」还没填完整，请到「设置 - API 模型管理」补全 Key 或模型名", "Image model \"${imgEp.name}\" is incomplete — fill in its key or model name"))
                    } else {
                        val outcome = AiApi.generateImageOutcome(imgEp, text)
                        val url = outcome.url
                        if (url != null) {
                            // 立刻下载落盘：远程图片 URL 有时效，只存 URL 的话
                            // 重进 App / 过一会儿图就挂了（表现为气泡空白「图片被吞」）
                            val local = withContext(Dispatchers.IO) { downloadAiImage(ctx, url) }
                            if (local != null) {
                                messages.value = messages.value + AiMsg(false, "画好了，长按可删除", imagePath = local)
                            } else {
                                // 下载失败退回远程 URL（仅当次会话内可见）
                                messages.value = messages.value + AiMsg(false, "画好了，长按可删除", imageUrl = url)
                            }
                        } else {
                            addAssistant(tr("图片生成失败：", "Image generation failed: ") + outcome.error)
                        }
                    }
                }
                thinking = false
                persistChat()
                return@launch
            }

            // 六类意图（查询/打开/设置/发布/记录/日常对话）不再由发送前的关键词决定，
            // 而是由模型在流式回复里输出 <intent> 自行判定，streamAssistant 结束后执行。
            // 这样「换个说法」不会影响功能，也不会在发消息前多等一次模型请求。
            chatReply(text)
        }
    }

    /** 点「下一步」：纯追问直接发送；能力调用走统一网关与确认分级，按钮不绕过安全闸 */
    fun runNextStep(step: NextStep) {
        clearNext()
        if (!step.isAction) {
            input = step.prompt.ifBlank { step.label }
            send()
            return
        }
        val cap = CapabilityRegistry.find(step.capability)
        if (cap == null || cap.gated) {
            AgentRuntime.noteGap(step.capability)
            addAssistant(
                tr("这个操作我暂时还做不到，已经记成待补能力了。", "I can't do that yet; noted it as a gap.")
            )
            persistChat()
            return
        }
        val call = buildCall(step)
        val mode = state.effectiveChatMode
        val verdict = AgentRuntime.check(call, mode)
        when (verdict.status) {
            AgentRuntime.Status.NEEDS_CONFIRM -> pendingAction = PendingCapability(step.label, call)
            AgentRuntime.Status.EXECUTED -> scope.launch {
                val d = AgentRuntime.execute(state, ctx, call, mode, confirmed = true)
                addAssistant(
                    if (d.ok) tr("已完成：", "Done: ") + d.message.ifBlank { step.label }
                    else tr("没做成：", "Couldn't: ") + d.message
                )
                persistChat()
            }
            else -> {
                if (verdict.status == AgentRuntime.Status.NOT_FOUND) AgentRuntime.noteGap(step.capability)
                addAssistant(verdict.message)
                persistChat()
            }
        }
    }

    // 就地入口带进来的问题：页面就绪后自动发出，不用用户再点一次发送。
    // 放在 send() 定义之后，避免局部函数前向引用。
    LaunchedEffect(restored, state.aiPrefill) {
        val p = state.aiPrefill
        if (restored && !p.isNullOrBlank()) {
            state.aiPrefill = null
            input = p
            send()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // 标题栏：只放标题文字「AI 旅行助手」（返回/历史/新对话等按钮移入下方内容区）
        AppTopBar(title = tr("AI 旅行助手"), insets = false)
        BackActionRow(
            onBack = { state.closeScreen() },
            actions = {
                IconBtn(Lucide.Settings2, tr("模型设置", "Model settings")) { state.openAiSettings() }
                IconBtn(Lucide.Sparkles, tr("AI 记忆")) { showMemory = true }
                IconBtn(Lucide.History, tr("历史对话")) { showHistory = true }
                IconBtn(Lucide.MessageSquarePlus, tr("新对话")) { newChat() }
            }
        )

        // 对话区
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.value.size <= 1) {
                item { WelcomeBlock(state = state, onPick = { input = it }) }
            }
            items(messages.value.size) { i ->
                val msg = messages.value[i]
                if (msg.fromUser) {
                    UserBubble(
                        msg = msg,
                        onLongClick = { requestDelete(i) }
                    )
                } else {
                    AssistantBubble(
                        msg = msg,
                        guides = state.guides,
                        onLongClick = { requestDelete(i) },
                        onOpenGuide = { id -> state.closeScreen(); state.openGuide(id) },
                        onOpenSource = { url -> state.openUrl(url) },
                        onRegenerate = if (
                            i == messages.value.lastIndex &&
                            !thinking &&
                            msg.text.isNotBlank()
                        ) {
                            { regenerateLast() }
                        } else null
                    )
                }
            }
            // 模型自己判定的意图：看得见、能改，改完会反哺给模型
            lastIntent?.let { d ->
                item { IntentChip(decision = d, onChange = { showIntentPicker = true }) }
            }
        }

        // 处理阶段固定在输入框上方：长回答往下滚也不会把「搜索→获取→思考→整合」滚出屏幕
        val livePhase = if (thinking) {
            messages.value.lastOrNull()?.takeIf { !it.fromUser }?.phase
        } else null
        livePhase?.takeIf { it.isNotBlank() }?.let { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 42.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.3.dp,
                    color = Brand.Terracotta
                )
                Spacer(Modifier.width(6.dp))
                Text(p, fontSize = 11.5.sp, color = Text.Secondary)
            }
        }

        // 待确认动作卡（发布攻略 / 添加足迹 / 执行指令 / 高风险二次确认）
        // 换动作时把二次确认状态归零，避免上一张卡的「已亮起」串到下一张
        LaunchedEffect(pendingAction) { dangerArmed = false }
        pendingAction?.let { action ->
            ActionCard(
                action = action,
                onConfirm = { confirmAction() },
                onCancel = { cancelAction() },
                dangerArmed = dangerArmed,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // 已选图片预览
        attachedBitmap?.let { bmp ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Bg.Outline, RoundedCornerShape(16.dp))
                ) {
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(tr("图片会在对话里显示，可加一句话说明"), color = Text.Muted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Bg.SurfaceVariant)
                        .clickable { attachedUri = null; attachedBitmap = null },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Lucide.Close, tr("取消选择"), tint = Text.Secondary, modifier = Modifier.size(14.dp))
                }
            }
        }

        // 从就地入口进来时标一下现场来源：模型和用户都清楚「这个/这篇」指的是什么
        state.aiEntryHint?.let { from ->
            if (!thinking) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tr("就地入口 · ", "From: ") + from,
                        color = Text.Secondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 「下一步」chips：模型给的延伸建议，既能直接发问，也能直接调度能力
        val chips = nextSteps.ifEmpty { suggestions.map { NextStep(it, prompt = it) } }
        if (chips.isNotEmpty() && !thinking && messages.value.lastOrNull()?.fromUser == false) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chips, key = { it.label + it.capability }) { s ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Bg.Surface)
                            .cardShadow(RoundedCornerShape(50))
                            .clickable { runNextStep(s) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            if (s.isAction) "${s.label} ›" else s.label,
                            color = Brand.Terracotta,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 复合请求的多步计划卡：整张确认后按序执行，失败即停、剩余步骤可以说「继续」
        planProposal?.let { p ->
            if (!thinking && !planRunning) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Bg.Surface)
                        .cardShadow(RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        tr("AI 想按这个顺序做", "AI suggests these steps"),
                        color = Text.Secondary, fontSize = 11.sp
                    )
                    Text(
                        p.goal, color = Text.Primary, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    p.steps.forEachIndexed { i, s ->
                        Text("${i + 1}. ${s.label}", color = Text.Primary, fontSize = 12.sp)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Brand.Terracotta)
                                .clickable { runPlan(p) }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text(
                                tr("按计划执行", "Run plan"),
                                color = Color.White, fontSize = 12.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { planProposal = null }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                tr("先不用", "Dismiss"),
                                color = Text.Secondary, fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // 图片 / 视频生成入口：固定在输入框上方，新对话和长对话里都能直接用
        AssistantCreationBar(
            onImage = {
                state.creativeMode = 0
                state.openScreen(ExtraScreen.CREATIVE)
            },
            onVideo = {
                state.creativeMode = 1
                state.openScreen(ExtraScreen.CREATIVE)
            }
        )

        Composer(
            input = input,
            onInputChange = { input = it },
            thinking = thinking,
            recording = recording,
            endpointName = state.activeModelName,
            onEndpointClick = { showModelPicker = true },
            onVoice = { toggleVoice() },
            onOpenTools = { if (!thinking) showTools = true },
            onSend = { send() },
            searchMode = searchMode,
            onCycleMode = {
                val next = SearchMode.values().let { it[(searchMode.ordinal + 1) % it.size] }
                searchMode = next
                WebSearchPrefs.setMode(ctx, next)
            }
        )
    }

    // 长按消息操作（豆包式：复制 / 删除）
    pendingDelete?.let { index ->
        ModalBottomSheet(
            onDismissRequest = { pendingDelete = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Bg.Surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .navigationBarsPadding()
            ) {
                val target = messages.value.getOrNull(index)
                if (target?.text?.isNotBlank() == true) {
                    MessageMenuRow(icon = Lucide.Copy, text = tr("复制消息"), danger = false) {
                        copyMessage(index); pendingDelete = null
                    }
                }
                // 最后一条回复才给「重新生成」：中间那条重跑会把后面整段对话丢掉
                if (index == messages.value.lastIndex && target?.fromUser == false) {
                    MessageMenuRow(icon = Lucide.Zap, text = tr("重新生成"), danger = false) {
                        regenerateLast()
                    }
                }
                MessageMenuRow(icon = Lucide.Trash, text = tr("删除消息"), danger = true) {
                    deleteMessage(index); pendingDelete = null
                }
            }
        }
    }

    // 模型选择弹窗：选哪个模型就用哪个模型，没有「自动 / 混合 / 模式」这一层
    if (showModelPicker) {
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text(tr("选择模型", "Choose model"), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        tr(
                            "当前：${state.activeModelName}。选哪个就用哪个，切换后立刻生效。",
                            "Now: ${state.activeModelName}. Pick one and it takes effect immediately."
                        ),
                        fontSize = 12.sp, color = Text.Muted
                    )
                    Spacer(Modifier.height(12.dp))

                    // ── 智能分配（按任务复杂度选本机 / 云端）──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                state.runMode = 3
                                showModelPicker = false
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            (if (state.runMode == 3) "✓  " else "    ") + tr("智能分配", "Smart routing"),
                            fontWeight = if (state.runMode == 3) FontWeight.Bold else FontWeight.Normal,
                            color = if (state.runMode == 3) Brand.Terracotta else Text.Primary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(tr("按复杂度选本机/云端", "Local vs cloud by complexity"), fontSize = 10.sp, color = Text.Muted)
                    }

                    // ── API 云端模型 ──
                    Spacer(Modifier.height(6.dp))
                    Text(tr("API 模型", "API models"), fontSize = 11.sp, color = Text.Muted, modifier = Modifier.padding(start = 4.dp))
                    Spacer(Modifier.height(4.dp))
                    if (state.chatEndpoints.isEmpty()) {
                        Text(
                            tr("尚未接入云端模型，请到「设置 → API 模型管理」添加自己的端点", "No cloud model connected — add your endpoint in Settings → API models"),
                            fontSize = 12.sp, color = Text.Muted, lineHeight = 17.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                    }
                    state.chatEndpoints.forEach { ep ->
                        val selected = state.runMode == 1 &&
                            ep.id == state.aiFeatureEndpoint(AiFeature.ASSISTANT).id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    state.selectAiEndpoint(ep.id)
                                    state.runMode = 1
                                    showModelPicker = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                (if (selected) "✓  " else "    ") + ep.name,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) Brand.Terracotta else Text.Primary,
                                modifier = Modifier.weight(1f)
                            )
                            if (!ep.builtin) Text(tr("已接入", "Connected"), fontSize = 10.sp, color = Text.Muted)
                        }
                    }

                    // ── 本地部署模型 ──
                    Spacer(Modifier.height(6.dp))
                    Text(tr("本地模型", "On-device models"), fontSize = 11.sp, color = Text.Muted, modifier = Modifier.padding(start = 4.dp))
                    Spacer(Modifier.height(4.dp))
                    val localReady = state.localLlmState == LocalLlmState.READY
                    val downloadedModels = remember(ctx) { LocalLlm.downloadedSpecs(ctx) }
                    if (downloadedModels.isEmpty()) {
                        Text(
                            tr("尚未下载本地模型，前往「设置 → AI 模型能力」下载", "No on-device model downloaded"),
                            fontSize = 11.sp, color = Text.Muted, modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    } else {
                        val activeLocal = LocalLlm.activeSpec(ctx)
                        downloadedModels.forEach { spec ->
                            val selected = state.runMode == 2 && activeLocal.id == spec.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        LocalLlm.setActive(ctx, spec.id)
                                        state.runMode = 2
                                        showModelPicker = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    (if (selected) "✓  " else "    ") + spec.display,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Brand.Terracotta else Text.Primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(spec.sizeHint, fontSize = 10.sp, color = Text.Muted)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr("添加或编辑 API 端点请前往「设置 → AI 模型能力」", "Manage endpoints in Settings"),
                        fontSize = 11.sp, color = Text.Muted
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelPicker = false }) { Text(tr("关闭", "Close")) }
            }
        )
    }

    // 意图纠错：用户把「模型判断」改成正确的类别后，这条说法会被记下来作为 few-shot，
    // 下次同类表达由模型自己按这个人的习惯判断，而不是回头改关键词表。
    if (showIntentPicker) {
        AlertDialog(
            onDismissRequest = { showIntentPicker = false },
            containerColor = Bg.Surface,
            title = {
                Text(
                    tr("这句话应该算哪一类？", "Which intent is this?"),
                    fontWeight = FontWeight.Bold, color = Text.Primary
                )
            },
            text = {
                Column {
                    Text(
                        tr(
                            "选错不要紧：我会记住你的说法，下次按你的习惯判断。",
                            "Your correction is remembered and used as an example next time."
                        ),
                        color = Text.Muted, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    IntentCategory.entries.forEach { cat ->
                        TextButton(
                            onClick = {
                                AiIntentMemory.add(ctx, intentQuestion, cat)
                                lastIntent = lastIntent?.copy(category = cat)
                                showIntentPicker = false
                                state.toast(tr("已记住，下次我会按这个理解", "Got it — I'll judge it this way next time"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = tr(cat.zh, cat.en),
                                color = if (cat == lastIntent?.category) Brand.Terracotta else Text.Primary,
                                fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    TextButton(onClick = {
                        AiIntentMemory.clear(ctx)
                        showIntentPicker = false
                        state.toast(tr("已清空意图学习记录", "Intent learning cleared"))
                    }) {
                        Text(tr("清空学习记录", "Clear learned intents"), color = Text.Muted, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showIntentPicker = false }) {
                    Text(tr("取消", "Cancel"), color = Text.Muted, fontSize = 13.sp)
                }
            }
        )
    }

    // 豆包式「+」功能面板
    if (showTools) {
        ModalBottomSheet(
            onDismissRequest = { showTools = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Bg.Surface
        ) {
            ToolsSheet(
                onCamera = { showTools = false; launchCamera() },
                onGallery = { showTools = false; imagePicker.launch("image/*") },
                onVoice = { showTools = false; toggleVoice() },
                onTool = { tool, text -> toolReply(tool, text) },
                onNewChat = { showTools = false; newChat() },
                onFootprint = {
                    showTools = false
                    // 真正的「AI 生成足迹」在新建页里完成：照片、地点、日期都用现有表单，
                    // 助手只负责把用户带过去，并让页面进入 AI 引导模式。
                    state.closeScreen()
                    state.openUpload(aiAssist = true)
                },
                onInput = { input }
            )
        }
    }

    // AI 长期记忆：可查看、可删除、可补录
    if (showMemory) {
        ModalBottomSheet(
            onDismissRequest = { showMemory = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Bg.Surface
        ) {
            MemorySheet(
                items = aiMemory,
                onDelete = { id ->
                    val next = AiMemory.remove(aiMemory, id)
                    aiMemory = next
                    scope.launch { Persistence.saveAiMemories(ctx, next) }
                },
                onAdd = { t, k ->
                    val next = AiMemory.add(aiMemory, t, k)
                    aiMemory = next
                    scope.launch { Persistence.saveAiMemories(ctx, next) }
                    state.toast(tr("已记住"))
                }
            )
        }
    }

    // 批量删除确认
    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            containerColor = Bg.Surface,
            title = { Text(tr("删除 ${picked.size} 个对话？", "Delete ${picked.size} chats?"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text(tr("删除后无法恢复，当前正在查看的对话也会被清空。"), color = Text.Secondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    deleteChats(picked)
                    confirmBatchDelete = false
                    batchMode = false
                }) { Text(tr("删除"), color = Func.Danger, fontSize = 14.sp) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchDelete = false }) { Text(tr("取消"), color = Text.Secondary, fontSize = 14.sp) }
            }
        )
    }

    // 历史对话（支持批量删除）
    if (showHistory) {
        ModalBottomSheet(
            onDismissRequest = { showHistory = false; batchMode = false; picked = emptySet() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Bg.Surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                    .navigationBarsPadding()
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("历史对话"), color = Text.Primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (conversations.isNotEmpty()) {
                        Text(
                            if (batchMode) tr("完成") else tr("批量管理"),
                            color = Brand.Terracotta,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable {
                                    batchMode = !batchMode
                                    if (!batchMode) picked = emptySet()
                                }
                                .padding(6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (batchMode) "已选 ${picked.size} / ${conversations.size}" else "${conversations.size} 个对话",
                    color = Text.Muted, fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))

                if (conversations.isEmpty()) {
                    Text(
                        tr("还没有历史对话，去问点什么吧"),
                        color = Text.Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 28.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(conversations, key = { it.id }) { chat ->
                            ChatHistoryRow(
                                chat = chat,
                                active = chat.id == currentId,
                                batchMode = batchMode,
                                picked = chat.id in picked,
                                onOpen = { openChat(chat) },
                                onTogglePick = {
                                    picked = if (chat.id in picked) picked - chat.id else picked + chat.id
                                },
                                onDelete = { deleteChats(setOf(chat.id)) }
                            )
                        }
                    }
                }

                if (batchMode && conversations.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val allPicked = picked.size == conversations.size
                        SheetAction(
                            text = if (allPicked) tr("取消全选") else tr("全选"),
                            primary = false,
                            modifier = Modifier.weight(1f)
                        ) {
                            picked = if (allPicked) emptySet() else conversations.map { it.id }.toSet()
                        }
                        SheetAction(
                            text = if (picked.isEmpty()) "删除选中" else "删除 ${picked.size} 项",
                            primary = picked.isNotEmpty(),
                            enabled = picked.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { confirmBatchDelete = true }
                    }
                }
            }
        }
    }

}

// ---------- 对话气泡 ----------

@Composable
private fun UserBubble(msg: AiMsg, onLongClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                .background(Brush.linearGradient(listOf(Brand.TerracottaLight, Brand.Terracotta)))
                .combinedClickable(onClick = {}, onLongClick = onLongClick)
                .padding(6.dp)
        ) {
            msg.imagePath?.let { path ->
                LocalImage(path, Modifier.widthIn(max = 240.dp))
                if (msg.text.isNotBlank()) Spacer(Modifier.height(6.dp))
            }
            if (msg.text.isNotBlank()) {
                Text(
                    msg.text,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AssistantBubble(
    msg: AiMsg,
    guides: List<com.example.my.model.Guide>,
    onLongClick: () -> Unit,
    onOpenGuide: (Int) -> Unit,
    onOpenSource: (String) -> Unit,
    onRegenerate: (() -> Unit)? = null
) {
    // 豆包式：左侧助手头像 + 浅色气泡，气泡右上角小圆角贴头像一侧
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        AssistantAvatar(size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f, fill = false)) {
            // 深度思考过程：默认折叠，点击展开
            msg.thinking?.takeIf { it.isNotBlank() }?.let { think ->
                var expanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Bg.Surface)
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (expanded) "▼ ${tr("思考过程", "Thinking")}"
                        else "▸ ${tr("思考过程", "Thinking")}",
                        fontSize = 11.sp, color = Text.Muted
                    )
                }
                if (expanded) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = think,
                        fontSize = 11.sp,
                        color = Text.Muted.copy(alpha = 0.8f),
                        lineHeight = 16.sp,
                        modifier = Modifier.widthIn(max = 300.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Bg.Surface)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                } else {
                    Spacer(Modifier.height(4.dp))
                }
            }
            val rendered = remember(msg.text, msg.phase) {
                markdownToAnnotated(
                    if (msg.text.isBlank()) {
                        if (msg.phase != null) tr("正在处理…", "Working…") else tr("正在思考…", "Thinking…")
                    } else msg.text
                )
            }
            Text(
                text = rendered,
                color = if (msg.text.isBlank()) Text.Muted else Text.Primary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                    .background(Bg.SurfaceVariant)
                    .combinedClickable(onClick = {}, onLongClick = onLongClick)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
            msg.imageUrl?.let { url ->
                Spacer(Modifier.height(6.dp))
                val bmp = rememberUrlBitmap(url)
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = tr("AI 生成图片"),
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Bg.Outline, RoundedCornerShape(16.dp))
                    )
                }
            }
            msg.videoUrl?.let { url ->
                Spacer(Modifier.height(8.dp))
                val context = LocalContext.current
                Row(
                    Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brand.Terracotta.copy(alpha = 0.10f))
                        .border(1.dp, Brand.Terracotta.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                        .clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.PlayCircle,
                        null,
                        tint = Brand.Terracotta,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr("播放生成的视频", "Play generated video"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Brand.Terracotta
                        )
                        Text(
                            tr("用系统播放器打开", "Opens in your player"),
                            fontSize = 10.sp,
                            color = Text.Muted,
                            maxLines = 1
                        )
                    }
                }
            }
            msg.guideId?.let { id ->
                Spacer(Modifier.height(6.dp))
                val guide = guides.firstOrNull { it.id == id }
                if (guide != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brand.Terracotta.copy(alpha = 0.10f))
                            .clickable { onOpenGuide(id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Lucide.Zap, null, tint = Brand.Terracotta, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            tr("打开攻略：${guide.title}", "Open guide: ${guide.title}"),
                            color = Brand.Terracotta,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            // 本轮耗时：检索时长（联网阶段）+ 思考作答时长；仅落定后展示，运行中不显示
            if (msg.phase == null && (msg.thinkMs != null || msg.searchMs != null)) {
                val timeParts = buildList {
                    msg.searchMs?.let { add(tr("检索 ${formatDuration(it)}", "Search ${formatDuration(it)}")) }
                    add(tr("思考 ${formatDuration(msg.thinkMs!!)}", "Think ${formatDuration(msg.thinkMs!!)}"))
                }
                Text(
                    timeParts.joinToString(" · "),
                    fontSize = 11.sp,
                    color = Text.Muted,
                    modifier = Modifier.padding(start = 14.dp, top = 4.dp)
                )
            }
            // 「AI 先搜了再答」的证据：默认折叠，点开按需查看，不让长列表把回答正文淹掉
            if (msg.sources.isNotEmpty()) {
                var sourcesOpen by remember { mutableStateOf(false) }
                var showAll by remember { mutableStateOf(false) }
                val visible = if (showAll) msg.sources.size else minOf(DEFAULT_SOURCE_VISIBLE, msg.sources.size)

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brand.Terracotta.copy(alpha = 0.10f))
                        .clickable { sourcesOpen = !sourcesOpen }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Lucide.Languages, contentDescription = null, tint = Brand.Terracotta, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(
                            tr(
                                "参考来源 · ${msg.sources.size} 篇网页",
                                "Sources · ${msg.sources.size} pages"
                            ),
                            color = Brand.Terracotta,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!sourcesOpen) {
                            Text(
                                tr("点击展开查看", "Tap to expand"),
                                color = Text.Muted,
                                fontSize = 9.5.sp,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (sourcesOpen) "▴" else "▾", color = Brand.Terracotta, fontSize = 11.sp)
                }
                if (sourcesOpen) {
                    Spacer(Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Bg.Surface)
                            .padding(vertical = 4.dp)
                    ) {
                        msg.sources.take(visible).forEachIndexed { i, h ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = h.url.startsWith("http")) { onOpenSource(h.url) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("[${i + 1}]", color = Brand.Terracotta, fontSize = 10.sp)
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        h.title.ifBlank { h.url },
                                        color = Text.Primary,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (h.site.isNotBlank() || h.tier > 0 || h.relevance > 0.0) {
                                        // 关联度来自「网页搜索排名 + 核心实体 + 意图」，
                                        // 让用户一眼看出哪些来源真的在讲他问的事
                                        val badges = buildList {
                                            if (h.tier in 1..4) add(tierBadge(h.tier))
                                            if (h.relevance > 0.0) {
                                                add("${(h.relevance * 100).toInt()}% ${tr("相关", "match")}")
                                            }
                                            if (h.rank > 0) {
                                                // 网页自带排名带来的相关强度，单独标出来：
                                                // 位次越靠前强度越高，但它只是关联度的一部分
                                                val pos = "${tr("第", "#")}${h.rank}${tr("位", "")}".trim()
                                                add(
                                                    "$pos · ${tr("排名强度", "rank weight")} " +
                                                        "+${(WebSearch.rankStrength(h.rank) * 100).toInt()}%"
                                                )
                                            }
                                            if (h.site.isNotBlank()) add(h.site)
                                        }
                                        Text(
                                            badges.joinToString(" · "),
                                            color = Text.Muted,
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Text("›", color = Text.Muted, fontSize = 14.sp)
                            }
                        }
                        if (msg.sources.size > DEFAULT_SOURCE_VISIBLE) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                                    .height(1.dp)
                                    .background(Text.Muted.copy(alpha = 0.15f))
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showAll = !showAll }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    if (showAll) {
                                        tr("收起，只看前 $DEFAULT_SOURCE_VISIBLE 篇", "Collapse")
                                    } else {
                                        tr("展开全部 ${msg.sources.size} 篇", "Show all ${msg.sources.size}")
                                    },
                                    color = Brand.Terracotta,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                        Text(
                            tr("点条目在浏览器打开原文", "Tap a row to open the page"),
                            color = Text.Muted,
                            fontSize = 9.5.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            onRegenerate?.let { again ->
                Spacer(Modifier.height(7.dp))
                Text(
                    tr("重新回答", "Regenerate"),
                    color = Brand.Terracotta,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = again)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** 助手头像：纸房子毛绒吉祥物（自带浮动待机动效） */
@Composable
private fun AssistantAvatar(size: Dp, modifier: Modifier = Modifier) {
    AiMascot(size = size, modifier = modifier)
}

/** 空对话时的欢迎区（随列表滚动消失，不占常驻空间） */
@Composable
private fun WelcomeBlock(state: AppState, onPick: (String) -> Unit) {
    // 首屏就用用户自己的数据说话：有足迹时提他已经走过的城市，比一句固定的"想去哪儿"更像认识他
    val cities = state.footprints.map { it.city }.filter { it.isNotBlank() }
    val fpCount = state.footprints.size
    val cityCount = cities.distinct().size
    val recentCity = cities.lastOrNull()
    val picks = remember(fpCount, cityCount, recentCity) {
        if (recentCity != null) {
            listOf(
                "$recentCity 还有什么小众玩法",
                "帮我规划 $recentCity 两日慢旅行",
                "我今年去过哪些城市？"
            )
        } else {
            listOf(
                "帮我规划大理 3 日慢旅行",
                "记一笔：昨天在洱海骑行",
                "把这段经历整理成一篇攻略"
            )
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistantAvatar(size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    tr("AI 旅行助手"),
                    color = Text.Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    tr("你的纸房子旅行搭子"),
                    color = Text.Muted,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            buildAnnotatedString {
                append(tr("这次，"))
                withStyle(SpanStyle(color = Coral)) { append(tr("想去哪儿？")) }
            },
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Text.Primary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (fpCount > 0) {
                tr(
                    "你已经留下 $fpCount 条足迹，走过 $cityCount 个城市\n长按消息可删除",
                    "You've logged $fpCount footprints across $cityCount cities\nLong-press a message to delete it"
                )
            } else {
                tr(
                    "对话 · 发图写文案 · 一句话记足迹 · 描述日志生成攻略\n长按消息可删除",
                    "Chat · caption photos · log a footprint in one line · turn notes into a guide\nLong-press a message to delete it"
                )
            },
            color = Text.Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(16.dp))
        picks.forEach { s ->
            Box(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Bg.Surface)
                    .cardShadow(RoundedCornerShape(50))
                    .clickable { onPick(s) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(s, color = Text.Secondary, fontSize = 13.sp)
            }
        }
    }
}

// ---------- 输入区 ----------

/** AI 旅行助手内的图片/视频专用入口，始终贴在输入框上方 */
@Composable
private fun AssistantCreationBar(onImage: () -> Unit, onVideo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(50))
            .background(Bg.Surface)
            .border(1.dp, Bg.Outline, RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistantCreationTab(
            icon = Lucide.ImagePlus,
            label = tr("图片", "Image"),
            onClick = onImage,
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier
                .width(1.dp)
                .height(18.dp)
                .background(Bg.Outline)
        )
        AssistantCreationTab(
            icon = Icons.Outlined.PlayCircle,
            label = tr("视频", "Video"),
            onClick = onVideo,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AssistantCreationTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Brand.Terracotta,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = Text.Primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun Composer(
    input: String,
    onInputChange: (String) -> Unit,
    thinking: Boolean,
    recording: Boolean,
    endpointName: String,
    onEndpointClick: () -> Unit,
    onVoice: () -> Unit,
    onOpenTools: () -> Unit,
    onSend: () -> Unit,
    searchMode: SearchMode,
    onCycleMode: () -> Unit
) {
    // 截图样式：大圆角白色卡片，上方输入区，底部一行功能按钮
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Bg.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // 输入区（上方大区域）
        BasicTextField(
            value = input,
            onValueChange = onInputChange,
            maxLines = 4,
            minLines = 1,
            textStyle = TextStyle(color = Text.Primary, fontSize = 15.sp, lineHeight = 22.sp),
            cursorBrush = SolidColor(Brand.Terracotta),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (input.isEmpty()) {
                        Text(
                            tr("问我问题，或发张图让我写文案…", "Ask me anything…"),
                            color = Text.Muted, fontSize = 14.sp
                        )
                    }
                    inner()
                }
            }
        )

        Spacer(Modifier.height(8.dp))

        // 底部功能行：+ | 端点名称 | 麦克风 | 发送
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // + 按钮
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Bg.SurfaceVariant)
                    .clickable { onOpenTools() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Lucide.Plus, tr("功能"), tint = Text.Primary, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.width(8.dp))

            // 端点名称胶囊按钮（点击切换/配置 API）
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Bg.SurfaceVariant)
                    .clickable { onEndpointClick() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    endpointName,
                    color = Text.Primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            // 联网检索档位：关闭 / 快速 / 均衡 / 高效，点击循环切换
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(if (searchMode != SearchMode.OFF) Brand.Terracotta.copy(alpha = 0.12f) else Bg.SurfaceVariant)
                    .clickable { onCycleMode() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (searchMode != SearchMode.OFF) Brand.Terracotta else Text.Muted.copy(alpha = 0.5f))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    tr(searchMode.zh, searchMode.en),
                    fontSize = 12.sp,
                    fontWeight = if (searchMode != SearchMode.OFF) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (searchMode != SearchMode.OFF) Brand.Terracotta else Text.Muted
                )
            }

            Spacer(Modifier.weight(1f))

            // 麦克风常驻：没有系统识别服务也没下离线模型时，点它会给明确提示，
            // 而不是把入口直接藏掉让人以为「这 App 没有语音」
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (recording) Coral else Color.Transparent)
                    .clickable { onVoice() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Lucide.Mic, tr("语音"),
                    tint = if (recording) Color.White else Text.Muted,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(6.dp))

            // 发送按钮（暖橙渐变，融合主题）
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (thinking) SolidColor(Coral.copy(alpha = 0.45f))
                        else Brush.linearGradient(MainGradient)
                    )
                    .clickable(enabled = !thinking, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(Lucide.ArrowUp, tr("发送"), tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    bg: Color,
    onClick: () -> Unit,
    active: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun IconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = Text.Primary, modifier = Modifier.size(20.dp))
    }
}

// ---------- 长按消息操作菜单 ----------

/** 消息操作菜单的一行（豆包式底部菜单） */
@Composable
private fun MessageMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    danger: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (danger) Func.Danger else Text.Primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = if (danger) Func.Danger else Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------- AI 长期记忆管理 ----------

/**
 * 让用户看见并纠正 AI 对自己的认知。
 * 之前的长期记忆是"只写不读"的黑盒：用户既不知道 AI 记住了什么，也没法删掉一条记错的事实，
 * 而错误记忆会一直污染之后所有对话。这个面板就是那扇门。
 */
@Composable
private fun MemorySheet(
    items: List<MemItem>,
    onDelete: (Long) -> Unit,
    onAdd: (String, String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(AiMemory.KIND_PREF) }
    val kinds = listOf(AiMemory.KIND_PREF, AiMemory.KIND_FACT, AiMemory.KIND_HABIT)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 22.dp)
            .navigationBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("AI 记住的事"), color = Text.Primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${items.size}/${AiMemory.MAX}", color = Text.Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            tr("这些会随每次对话一起交给 AI，让它越来越了解你。记错了就删掉，也可以自己补一条。"),
            color = Text.Secondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(14.dp))

        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(tr("还没有记住任何事"), color = Text.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(tr("说一句「我常住重庆」「我不吃辣」，它就会记住"), color = Text.Muted, fontSize = 11.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Bg.SurfaceVariant, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tr(m.kind),
                            color = Brand.Terracotta,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .background(Brand.Terracotta.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            m.text,
                            color = Text.Primary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Lucide.Trash,
                            contentDescription = tr("删除"),
                            tint = Text.Muted,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onDelete(m.id) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            kinds.forEach { k ->
                val on = kind == k
                Text(
                    tr(k),
                    color = if (on) Brand.Terracotta else Text.Secondary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) Brand.Terracotta.copy(alpha = 0.14f) else Bg.SurfaceVariant)
                        .clickable { kind = k }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(30) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(tr("比如：不吃香菜"), color = Text.Muted, fontSize = 12.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(color = Text.Primary, fontSize = 13.sp),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                tr("添加"),
                color = if (text.isBlank()) Text.Muted else Brand.Terracotta,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = text.isNotBlank()) {
                        onAdd(text.trim(), kind)
                        text = ""
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

// ---------- 豆包式「+」功能面板 ----------

@Composable
private fun ToolsSheet(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onVoice: () -> Unit,
    onTool: (String, String) -> Unit,
    onNewChat: () -> Unit,
    onFootprint: () -> Unit,
    onInput: () -> String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
            .navigationBarsPadding()
    ) {
        Text(tr("更多功能"), color = Text.Primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(tr("发图让 AI 写文案，或对文字做翻译、润色、总结、续写"), color = Text.Muted, fontSize = 11.sp)
        Spacer(Modifier.height(16.dp))

        // 图片/语音/会话
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolCell(Lucide.Camera, tr("拍照"), Modifier.weight(1f)) { onCamera() }
            ToolCell(Lucide.ImagePlus, tr("相册"), Modifier.weight(1f)) { onGallery() }
            ToolCell(Lucide.Mic, tr("语音输入"), Modifier.weight(1f)) { onVoice() }
            ToolCell(Lucide.MessageSquarePlus, tr("新对话"), Modifier.weight(1f)) { onNewChat() }
        }

        Spacer(Modifier.height(16.dp))
        Text(tr("旅行工具"), color = Text.Secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brand.Terracotta.copy(alpha = 0.09f))
                .border(1.dp, Brand.Terracotta.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                .clickable(onClick = onFootprint)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brand.Terracotta.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Lucide.Sparkles, null, tint = Brand.Terracotta, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tr("AI 帮写旅行备注", "AI travel note"),
                    color = Text.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tr("上传照片 + 地点，自动写这一段旅程的旅行备注", "Photos + place → a travel note for this trip"),
                    color = Text.Secondary, fontSize = 11.sp, lineHeight = 15.sp
                )
            }
            Text(tr("去写 ›", "Open ›"), color = Brand.Terracotta, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(16.dp))
        Text(tr("文字工具"), color = Text.Secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolCell(Lucide.Languages, tr("翻译"), Modifier.weight(1f)) { onTool(tr("翻译"), onInput()) }
            ToolCell(Lucide.Wand2, tr("润色"), Modifier.weight(1f)) { onTool(tr("润色"), onInput()) }
            ToolCell(Lucide.AlignLeft, tr("总结"), Modifier.weight(1f)) { onTool(tr("总结"), onInput()) }
            ToolCell(Lucide.PenLine, tr("续写"), Modifier.weight(1f)) { onTool(tr("续写"), onInput()) }
        }
    }
}

/** 功能面板里的一个入口（图标 + 名称）；weight 由调用方在 Row 作用域内传入 */
@Composable
private fun ToolCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Bg.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Brand.Terracotta, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = Text.Primary, fontSize = 12.sp)
    }
}

// ---------- 通用动作按钮 ----------

@Composable
private fun SheetAction(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    !enabled -> Bg.SurfaceVariant
                    primary -> Brand.Terracotta
                    else -> Bg.SurfaceVariant
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = when {
                !enabled -> Text.Muted
                primary -> Color.White
                else -> Text.Primary
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ChatHistoryRow(
    chat: AiChat,
    active: Boolean,
    batchMode: Boolean,
    picked: Boolean,
    onOpen: () -> Unit,
    onTogglePick: () -> Unit,
    onDelete: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                when {
                    picked -> Brand.Terracotta.copy(alpha = 0.14f)
                    active -> Brand.Terracotta.copy(alpha = 0.10f)
                    else -> Bg.SurfaceVariant
                }
            )
            .clickable { if (batchMode) onTogglePick() else onOpen() }
            .padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (batchMode) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (picked) Brand.Terracotta else Color.Transparent)
                    .border(1.5.dp, if (picked) Brand.Terracotta else Bg.OutlineStrong, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (picked) Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                chat.title, color = Text.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tr("${fmt.format(Date(chat.updatedAt))} · ${chat.messages.count { it.fromUser }} 条提问", "${fmt.format(Date(chat.updatedAt))} · ${chat.messages.count { it.fromUser }} questions"),
                color = Text.Muted, fontSize = 11.sp
            )
        }
        if (!batchMode) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) { Icon(Lucide.Trash, tr("删除对话"), tint = Text.Muted, modifier = Modifier.size(16.dp)) }
        }
    }
}

/** 待确认动作卡 */
@Composable
private fun ActionCard(
    action: AiPendingAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    dangerArmed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val danger = action.isDangerous()
    val (title, desc, confirmText) = when (action) {
        is PendingGuide -> Triple(
            "确认发布攻略？",
            "《${(action.bp.title.ifBlank { "我的旅行攻略" })}》· ${action.bp.city.ifBlank { "未知城市" }} · ${action.bp.days.size} 天",
            "确认发布"
        )

        is PendingFootprint -> Triple(
            "确认添加足迹？",
            "城市：${action.bp.city.ifBlank { "?" }} · 地点：${action.bp.name.ifBlank { "?" }}${if (action.bp.missing.isNotEmpty()) " · 还缺：${action.bp.missing.joinToString()}" else ""}",
            "确认添加"
        )

        is PendingTrip -> Triple(
            "确认导入行程？",
            "《${action.bp.title.ifBlank { "${action.bp.destination}行程" }}》· ${action.bp.destination.ifBlank { "?" }} · ${action.bp.days.size} 天" +
                    (if (action.bp.startDate.isNotBlank()) " · ${action.bp.startDate} 出发" else ""),
            "确认导入"
        )

        is PendingCommand -> Triple("确认执行指令？", action.intent.label, "确认执行")

        is PendingCapability -> Triple(
            tr("确认执行这一步？", "Run this step?"),
            action.label,
            tr("确认执行", "Run")
        )
    }
    // 高风险动作：标题换成警告色，描述明确说明影响与两步流程，按钮分两段
    val shownTitle = if (danger) tr("高风险操作", "High-risk action") else title
    val shownDesc = if (!danger) desc else "$desc\n" +
        if (dangerArmed)
            tr("第 2 步：再点一次下方按钮才会真正执行。", "Step 2: tap the button below again to actually run it.")
        else
            tr(
                "第 1 步：这一步会删除或退出，需要两步确认；删除后可在对话里说「撤销」。",
                "Step 1: this deletes data or exits. Two confirmations are required; deletions can be undone in chat."
            )
    val shownConfirm = when {
        !danger -> confirmText
        dangerArmed -> tr("再次确认，执行", "Confirm and run")
        else -> tr("我了解风险，继续", "I understand, continue")
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Bg.Surface)
            .cardShadow()
            .padding(14.dp)
    ) {
        Text(
            shownTitle,
            color = if (danger) Color(0xFFD9483B) else Text.Primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            shownDesc,
            color = Text.Secondary,
            fontSize = 13.sp,
            maxLines = if (danger) 5 else 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(Bg.SurfaceVariant)
                    .clickable { onCancel() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text(tr("取消"), color = Text.Secondary, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (danger && dangerArmed) SolidColor(Color(0xFFD9483B))
                        else Brush.linearGradient(MainGradient)
                    )
                    .clickable { onConfirm() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text(shownConfirm, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

// ---------- 本地图片 ----------

/** 从私有目录读取对话里回显的图片 */
@Composable
private fun LocalImage(path: String, modifier: Modifier = Modifier) {
    val bmp = produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, o)
                var sample = 1
                while (o.outWidth / sample > 720 || o.outHeight / sample > 720) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()?.asImageBitmap()
        }
    }.value
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = tr("发送的图片"),
            contentScale = ContentScale.Crop,
            modifier = modifier
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
        )
    }
}

// ---------- 图片处理 ----------

private fun decodeUriBitmap(ctx: Context, uri: Uri): Bitmap? = runCatching {
    val input = ctx.contentResolver.openInputStream(uri) ?: return null
    val bmp = BitmapFactory.decodeStream(input) ?: return null
    input.close()
    val max = 1024
    val longest = maxOf(bmp.width, bmp.height)
    if (longest > max) {
        val s = max.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
        bmp.recycle()
        scaled
    } else bmp
}.getOrNull()

/** 把图片存进私有目录，供对话气泡回显与历史消息持久化 */
private fun saveAiImage(ctx: Context, bmp: Bitmap): String? = runCatching {
    val dir = File(ctx.filesDir, "ai_images").apply { mkdirs() }
    // 用 UUID 保证唯一：只用时间戳时，同一毫秒内连发两张图会撞名，
    // 删掉其中一条消息会让另一条消息的图片变空白
    val f = File(dir, "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    f.absolutePath
}.getOrNull()

/** 下载 AI 生成的图片到本地私有目录（远程 URL 有时效，必须落盘才能持久展示） */
private fun downloadAiImage(ctx: Context, url: String): String? = runCatching {
    val bmp = if (url.startsWith("data:image")) {
        val comma = url.indexOf(',')
        if (comma < 0) return null
        val bytes = Base64.decode(url.substring(comma + 1), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } else {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        val decoded = conn.inputStream.use { BitmapFactory.decodeStream(it) }
        conn.disconnect()
        decoded
    }
    if (bmp == null) return null
    val path = saveAiImage(ctx, bmp)
    bmp.recycle()
    path
}.getOrNull()

/** 压缩为 JPEG 字节，便于发给视觉模型 */
private fun compressJpeg(bmp: Bitmap): ByteArray? = runCatching {
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
    out.toByteArray()
}.getOrNull()

/** 从 App 真实资料构建用户画像块（稳定可靠、跨会话恒定的"记忆"），无资料时返回 null */
private fun buildProfileBlock(state: AppState): String? {    val nickname = state.settings.nickname.takeIf { it.isNotBlank() }
    val signature = state.settings.signature.takeIf { it.isNotBlank() }
    val homeCity = state.weatherCity.takeIf { it.isNotBlank() }
    val cityCount = state.footprints.map { it.city }.filter { it.isNotBlank() }.distinct().size
    if (nickname == null && signature == null && homeCity == null && cityCount == 0) return null
    return buildString {
        append(tr("关于用户（来自 App 真实资料，请据此自然地称呼与推荐）："))
        nickname?.let { append("\n- 昵称：$it") }
        homeCity?.let { append("\n- 常驻城市（天气城市）：$it") }
        if (cityCount > 0) append("\n- 已记录足迹 $cityCount 个城市")
        signature?.let { append("\n- 个人签名：$it") }
    }
}

/** 构建实时上下文块：当前日期时间 + 设备天气实况。
 *  这是让 AI “知道现在几点、今天几号、当地天气”的关键——不注入它就只能凭训练数据瞎编。 */
/**
 * 流式思考标签剥离器。
 *
 * 思考型模型（Qwen3、DeepSeek-R1 等）习惯先吐一段 <think>...</think> 推理，再给正式回答。
 * 这段推理不该展示给用户。SSE 是逐 chunk 到达的，标签可能被切到两个 chunk 之间
 * （例如上一个 chunk 末尾是 "<thin"，下一个 chunk 才是 "k>..."），所以不能只看单个 chunk。
 *
 * 用法：每个 chunk 调 [feed]，确定是正文的部分立即经 onAnswer 回调（保留流式打字效果），
 * 确定是思考的部分经 onThink 回调；拿不准的尾巴暂存，下一个 chunk 来了再判定。
 * 流结束后调 [flush] 收尾。
 */
private class ThinkTagStripper(
    private val onAnswer: (String) -> Unit,
    private val onThink: (String) -> Unit
) {
    private var inThink = false
    /** 可能是标签开头/结尾的尾巴，延迟判定，避免把 "<thin" 这种半截当正文吐出去 */
    private val tail = StringBuilder()

    fun feed(raw: String) {
        var s = tail.toString() + raw
        tail.setLength(0)
        while (s.isNotEmpty()) {
            if (inThink) {
                val close = s.indexOf("</think>", ignoreCase = true)
                if (close < 0) {
                    // 这一段全是思考；末尾 9 个字符留着，可能是被切断的 </think>
                    val spill = maxOf(0, s.length - 9)
                    onThink(s.substring(0, spill))
                    tail.append(s.substring(spill))
                    s = ""
                } else {
                    onThink(s.substring(0, close))
                    inThink = false
                    s = s.substring(close + "</think>".length)
                }
            } else {
                val open = s.indexOf("<think>", ignoreCase = true)
                if (open < 0) {
                    // 这一段全是正文；末尾 7 个字符留着，可能是被切断的 <think>
                    val spill = maxOf(0, s.length - 7)
                    onAnswer(s.substring(0, spill))
                    tail.append(s.substring(spill))
                    s = ""
                } else {
                    onAnswer(s.substring(0, open))
                    inThink = true
                    s = s.substring(open + "<think>".length)
                }
            }
        }
    }

    fun flush() {
        if (tail.isEmpty()) return
        if (inThink) onThink(tail.toString()) else onAnswer(tail.toString())
        tail.setLength(0)
    }
}

/**
 * 本地控制指令识别（Agent 的"后端工具"）。
 *
 * 用户对 AI 说「打开声音播报」「关掉悬浮球」「切深色模式」这类话时，这不是闲聊，
 * 而是一个要在 App 后端执行的工作任务。命中后直接改 AppState——
 * UI 靠 Compose 状态自动刷新，前端无感、不用跳设置页——并返回一句本地确认文案；
 * 未命中返回 null，继续走正常 LLM 流程。
 *
 * 这是第一类"真干活"的工具：以后要加新能力（清缓存、导出数据、设提醒…），
 * 在这里加分支即可，模型负责对话，规则负责高确定性的开关动作。
 */
private fun tryApplyLocalControl(raw: String, state: AppState): String? {
    val t = raw.trim()
    // 太长的不是指令（避免把一段话里的"打开"误判）
    if (t.length > 40) return null
    val turningOn = listOf("打开", "开启", "开一下", "启动", "帮我开", "请开", "切到开", "调到开", "把…打开")
        .any { t.contains(it) }
    val turningOff = listOf("关闭", "关掉", "关一下", "停止", "别开", "取消", "不要", "切到关", "调到关", "关上", "把…关掉")
        .any { t.contains(it) }
    if (!turningOn && !turningOff) return null
    val on = turningOn

    // ① 声音播报（朗读 AI 回复）
    val voiceTtsTarget = t.contains("声音播报") || t.contains("语音播报") ||
        t.contains("朗读") || t.contains("语音朗读") || t.contains("TTS", ignoreCase = true)
    if (voiceTtsTarget) {
        state.updateAiCapabilities { it.copy(voiceTts = on) }
        return if (on)
            "好的，已为你打开声音播报\n" +
                "以后我每次回复完都会自动念给你听；想关随时跟我说「关掉声音播报」。"
        else
            "好的，已为你关闭声音播报\n" +
                "以后回复只显示文字、不再朗读；想再开跟我说「打开声音播报」。"
    }

    // ② AI 悬浮球
    val ballTarget = t.contains("悬浮球") || t.contains("AI球") || t.contains("AI 球")
    if (ballTarget) {
        state.updateAiBallEnabled(on)
        return if (on)
            "好的，已为你打开 AI 悬浮球\n" +
                "屏幕上会出现一个小按钮，点它就能就地问我问题。想关跟我说「关掉悬浮球」。"
        else
            "好的，已为你关闭 AI 悬浮球\n" +
                "想再开跟我说「打开悬浮球」，或到设置里手动打开。"
    }

    // ③ 主题模式
    if (t.contains("深色") || t.contains("暗色") || t.contains("夜间") || t.contains("暗黑")) {
        state.updateThemeMode(2)
        return "好的，已切换到深色模式 想切回来跟我说「浅色模式」。"
    }
    if (t.contains("浅色") || t.contains("明亮") || t.contains("白天")) {
        state.updateThemeMode(1)
        return "好的，已切换到浅色模式 想切回来跟我说「深色模式」。"
    }
    if (t.contains("跟随系统") || t.contains("自动模式")) {
        state.updateThemeMode(0)
        return "好的，已切回跟随系统 主题会跟着系统设置自动变化。"
    }

    return null
}

/**
 * 智能分配的本地判定：按任务复杂度挑「本机能答好」的问题。
 *
 * 第一优先是明确的本机上下文问题：时间/日期/星期、天气、查本机已存的行程和足迹。
 * 第二优先是短、单步、没有强推理或联网要求的日常问句——这类问题端侧模型足够，
 * 也更省流量。攻略规划、写文案、翻译、总结、复杂推理、联网搜索继续交云端，
 * 避免小模型硬撑导致答非所问。
 */
private fun isSimpleLocalQuery(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty()) return false
    // 太短的寒暄（"你好""在吗"）也让云端接，小模型容易答得冷场
    if (t.length <= 2) return false

    val localKeys = listOf(
        // 时间 / 日期 / 星期
        "几点", "现在时间", "今天几号", "什么日期", "今天日期", "星期", "周几", "礼拜",
        // 天气
        "天气", "气温", "几度", "下雨", "温度", "预报", "穿什么",
        // 本机数据
        "我存了哪些", "我去过", "我的足迹", "我的行程", "我计划", "我存的", "我记录的",
        "我去过哪", "我走过", "我的路线"
    )
    // 明确需要联网/长推理的，即使沾关键词也不走本地
    val cloudOnly = listOf(
        "攻略", "推荐", "怎么玩", "路线规划", "行程规划", "写文案", "翻译", "润色",
        "总结", "续写", "搜索", "查一下", "最近", "门票", "多少钱", "美食", "酒店",
        "分析", "规划", "代码", "为什么", "怎么实现", "对比", "比较", "帮我写", "生成"
    )
    if (cloudOnly.any { t.contains(it) }) return false
    if (localKeys.any { t.contains(it) }) return true

    // 复杂度兜底：短句、单句、没有多步要求的日常问答交给本机。
    // 超过 18 字或出现多个句子，默认信息量/推理量已经超出小模型舒适区，走云端。
    val sentenceBreaks = t.count { it == '。' || it == '！' || it == '？' || it == '?' || it == '!' }
    return t.length <= 18 && sentenceBreaks <= 1
}

private fun buildLiveContext(state: AppState): String {
    val now = System.currentTimeMillis()
    val dateStr = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA).format(Date(now))
    val timeStr = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(now))
    val sb = StringBuilder(
        "当前时间：$dateStr $timeStr（设备本地时间；用户问“现在几点/今天几号/周几/星期几”一律以此为准，不要自己推算或猜测）。"
    )
    val w = state.weatherNow
    if (w != null) {
        sb.append("\n当前天气（${w.city}）：${w.temp}℃ ${w.emoji}${w.desc}，湿度${w.humidity}%，风力${w.wind}级")
        w.days.firstOrNull()?.let { today -> sb.append("；今日 ${today.low}~${today.high}℃ ${today.desc}") }
        if (w.updatedAt.isNotBlank()) sb.append("（更新于 ${w.updatedAt}）")
        sb.append("。用户问当地天气直接用这条真实数据作答，不要编造气温或天气现象。")
    } else if (state.weatherCity.isNotBlank()) {
        sb.append(
            "\n天气城市为 ${state.weatherCity}，但实况暂未取到（可能刚切换城市或网络较弱）；被问天气时如实说明“暂未获取到”，并可提示用户打开天气页下拉刷新，不要凭空报气温。"
        )
    }
    return sb.toString()
}

/**
 * 行程与足迹摘要注入：用户问"我存了哪些行程/我去过哪些地方"时，
 * 直接基于真实数据回答，而不是让模型瞎编。
 */
private fun buildTripBlock(state: AppState, userText: String): String? {
    // token 口径：不问行程/足迹就别把计划清单塞进 prompt
    val asksTrip = TRIP_HINTS.any { userText.contains(it) }
    if (!asksTrip) return null
    val parts = mutableListOf<String>()
    val activeTrips = state.tripPlans.filter { !it.archived }
    if (activeTrips.isNotEmpty()) {
        val lines = activeTrips.take(8).joinToString("\n") {
            "- 《${it.name}》→ ${it.destination}（${it.startDate}，${it.days}天）"
        }
        parts.add("用户已存的行程计划：\n$lines")
    }
    if (state.footprints.isNotEmpty()) {
        val cities = state.footprints.map { it.city }.distinct().take(12)
        parts.add("用户已打卡足迹共 ${state.footprints.size} 条，去过的城市：${cities.joinToString("、")}")
    }
    return if (parts.isEmpty()) null else parts.joinToString("\n\n")
}

private val TRIP_HINTS = listOf(
    "行程", "计划", "安排", "路线", "几天", "日游", "出发", "预算", "住宿", "酒店",
    "机票", "高铁", "火车", "好玩", "攻略", "旅行", "玩", "足迹", "去过", "打卡", "记录"
)

/**
 * 目的地感知的攻略 RAG：用户说"我想去大理/大理有什么好玩的"时，
 * 从 App 内真实攻略里挑出相关的（标题+一句话简介），让助手基于已有笔记给建议；
 * 没提到具体城市时，退化为列出全部攻略标题供推荐。
 */
private fun buildGuideBlock(state: AppState, userText: String): String? {
    if (state.guides.isEmpty()) return null
    // 已知城市集合（攻略城市 + 足迹城市 + 当前天气城市）
    val knownCities = (state.guides.map { it.city } + state.footprints.map { it.city } + state.weatherCity)
        .map { it.trim() }.filter { it.length >= 2 }.distinct()
    // 用户话里命中的城市（取最长命中，避免"大理白族"误匹配）
    val hitCity = knownCities.filter { userText.contains(it) }.maxByOrNull { it.length }
    return if (hitCity != null) {
        val matched = state.guides.filter { it.city.contains(hitCity, true) || it.title.contains(hitCity, true) }
        if (matched.isEmpty()) {
            "用户提到了「$hitCity」，但 App 内暂时没有相关攻略。可如实告诉用户，并提议帮他生成一篇或做行程规划。"
        } else {
            val lines = matched.joinToString("\n") { "- 《${it.title}》：${it.intro.take(60)}" }
            "App 内与「$hitCity」相关的真实攻略（可据此给建议，并告诉用户能帮他打开）：\n$lines"
        }
    } else {
        // token 口径：没问目的地/玩法，就别把全量攻略标题塞进 prompt
        if (GUIDE_HINTS.none { userText.contains(it) }) return null
        state.guides.take(15)
            .joinToString("\n") { "- 《${it.title}》(${it.city}·${it.type})" }
            .let { "当前 App 内已有攻略：\n$it\n推荐攻略时优先从上述列表选，并告诉用户可以帮你打开。" }
    }
}

private val GUIDE_HINTS = listOf(
    "攻略", "推荐", "去哪", "好玩", "好吃", "景点", "旅行", "行程", "规划", "路线",
    "打卡", "必去", "住宿", "酒店", "几天", "日游", "玩"
)

/**
 * 「先搜再答」：检索 → 清洗 → 事实裁决 → 事实卡，一起交给模型；
 * 同时把结构化来源回传给 UI，让回答气泡下方能展开可点的引用列表。
 *
 * 返回 Triple(注入 system 的文本, 本轮来源, 本轮证据卡)。证据卡在回答落定后
 * 还要用于数字/引用校验（[SeaFusion.auditAnswer]）。检索失败时文本里明确写"没查到"，
 * 不允许模型拿训练记忆里的旧价格/旧天气冒充实时信息。
 * 地点类问题再补一次高德 POI 实搜，和事实卡一起给模型。
 */
private data class SearchBlockResult(
    val block: String?,
    val sources: List<WebHit>,
    val deck: SeaDeck?,
    /** 本轮临时证据库的 key；非空时云端模型可调用 lookup_evidence 回查 */
    val evidenceKey: String? = null,
    /** 证据块之外的材料（高德地点等）；工具查证据后仍要保留这部分 */
    val extraBlock: String? = null,
    /** 本次实际使用的上下文预算（轻量档降级补搜后可能与入参不同） */
    val contextChars: Int = 0
)

private suspend fun buildSearchBlock(
    state: AppState,
    ctx: Context,
    userText: String,
    isLocal: Boolean,
    localModel: String,
    /** 检索 / 抓取档位：候选数、正文条数、超时、证据条数都随用户所选档位 */
    profile: WebSearch.SearchProfile,
    /** 检索路由模型给出的补充检索词；必须过同主题校验后才会并入查询计划 */
    extraQueries: List<String> = emptyList()
): SearchBlockResult {
    if (userText.isBlank()) return SearchBlockResult(null, emptyList(), null)
    val parts = mutableListOf<String>()
    var sources: List<WebHit> = emptyList()
    var deck: SeaDeck? = null
    var evidenceKey: String? = null

    // ① 第一步就把用户发送的内容原样送去搜索：不等模型判意图、不让规则改写主词。
    var activeProfile = profile
    var verbatimHits = withTimeoutOrNull(activeProfile.searchTimeoutMs) {
        WebSearch.search(userText, activeProfile.limit, activeProfile, extraQueries)
    }.orEmpty()
    // 轻量档只有 3 条候选、1 篇正文；如果几乎没搜到，别让「快速回答」变成「答不上来」。
    // 只补一级（轻量→快速），最多多打一次请求，不把简单问题拖成深搜。
    if (activeProfile.limit <= 3 && verbatimHits.size < 3 && userText.trim().length > 6) {
        activeProfile = activeProfile.escalate()
        verbatimHits = withTimeoutOrNull(activeProfile.searchTimeoutMs) {
            WebSearch.search(userText, activeProfile.limit, activeProfile, extraQueries)
        }.orEmpty()
    }
    // 2. 检索结果 → 四层清洗 → 句级事实裁决：模型拿到的是"已经分好置信度的事实卡"
    val cleanerCfg = SearchCleaner.Config(maxEvidence = activeProfile.maxEvidence)
    val adjCfg = EvidenceAdjudicator.Config(maxClaims = activeProfile.maxClaims)
    val built = withTimeoutOrNull(activeProfile.searchTimeoutMs) {
        SeaFusion.buildWithPages(
            query = userText,
            ep = state.aiFeatureEndpoint(AiFeature.ASSISTANT),
            isLocal = isLocal,
            localModel = localModel,
            maxBodies = activeProfile.maxBodies,
            config = cleanerCfg,
            adjudicatorConfig = adjCfg,
            initialHits = verbatimHits,
            profile = activeProfile
        )
    }
    // 只有清洗出证据、且裁决出至少一条"能说"的结论才注入事实卡；
    // 闲聊时搜到的泛泛网页不往 prompt 里塞，省 token也不干扰回答
    val usable = built != null && built.evidence.isNotEmpty() &&
        built.claims.any { it.verdict == Verdict.CONFIRMED || it.verdict == Verdict.QUALIFIED }
    if (usable) {
        deck = built
        sources = SeaFusion.sources(built)
        // 两段式：先把清洗后的事实卡写进临时证据库，再按本轮问题的意图槽位读回。
        // 单问题场景通常只有 1~2 个槽位，不会把整块事实卡原样塞给模型。
        val key = "assistant|$userText|${System.currentTimeMillis()}"
        evidenceKey = key
        val records = EvidenceStore.save(ctx, key, userText, built)
        val saved = if (records.isNotEmpty()) {
            EvidenceStore.load(ctx, key).ifEmpty { records }
        } else emptyList()
        val material = if (saved.isNotEmpty()) {
            val slots = EvidenceIndex.slotsForText(userText)
            val hits = EvidenceIndex.retrieve(saved, slots, perSlot = 3)
            EvidenceIndex.render(hits, saved.size, activeProfile.contextChars)
        } else ""
        parts.add(
            material.ifBlank {
                SeaFusion.block(built) ?: SeaFusion.noEvidenceBlock(userText, built.stats.rawCount)
            }
        )
    } else {
        // 联网开着就「全问题必搜」：闲聊、观点、写作类问题清洗后通常留不下"事实卡"，
        // 但网页结果本身就是本轮参考来源，不能因此丢掉——否则用户会觉得联网没生效。
        deck = built
        // 无事实卡时也要过一次关联度：宁可只挂 3 条真正相关的网页，
        // 也不能把原始命中里的跨领域页全列成「参考来源」
        // 清洗器超时/失败时 built 可能为 null，但第一段已经拿到的 verbatimHits 仍是
        // 真实检索结果；这里必须回退到它，不能把搜到的网页一起丢掉只剩「没查到」。
        val candidateHits = built?.hits?.takeIf { it.isNotEmpty() } ?: verbatimHits
        val rawHits = WebSourcePicker.refine(
            WebSearch.relevantOnly(candidateHits, userText).take(activeProfile.maxEvidence)
        )
        if (rawHits.isNotEmpty()) {
            sources = rawHits
            parts.add(
                WebSearch.render(rawHits, userText) +
                    "\n\n（以上是本轮全网检索到的参考资料，已按关联度排序：必须据此作答并标注 [n]，" +
                    "严禁说「网上暂未查到」；与问题无关的条目直接忽略。）"
            )
        } else {
            // 联网开着但过滤后一条相关网页都没有：如实说明，不能静默降级成凭记忆答。
            // 此时 roundSources 为空，与「没查到」的回答一致，不会出现自相矛盾。
            parts.add(SeaFusion.noEvidenceBlock(userText, 0))
        }
    }

    // 2. 高德地图搜地点（仅限问地点类问题）
    val extraStart = parts.size
    val searchKeywords = listOf(
        "有什么好玩的", "有什么好吃的", "景点", "哪里好玩", "哪里好吃",
        "推荐一下", "值得去", "必去", "打卡", "美食", "小吃",
        "怎么玩", "攻略", "路线", "交通", "怎么去"
    )
    val isLocationQuery = searchKeywords.any { userText.contains(it) }
    if (isLocationQuery) {
        val knownCities = (state.guides.map { it.city } + state.footprints.map { it.city } + state.weatherCity)
            .map { it.trim() }.filter { it.length >= 2 }.toSet()
        val hitCity = knownCities.filter { userText.contains(it) }.maxByOrNull { it.length }
        val query = hitCity ?: run {
            val m = Regex("([\\u4e00-\\u9fa5]{2,6})(?:有什么|景点|好玩|好吃|美食|攻略)").find(userText)
            m?.groupValues?.getOrNull(1)?.takeIf {
                it !in setOf("这里", "那里", "我们", "你们", "这个", "那个", "现在", "今天")
            }
        }
        if (query != null) {
            val hasLocalGuide = hitCity != null && state.guides.any { it.city.contains(hitCity, true) || it.title.contains(hitCity, true) }
            if (!hasLocalGuide) {
                val results = runCatching {
                    com.example.my.data.AmapApi.searchPlaces(query, count = 8)
                }.getOrDefault(emptyList())
                if (results.isNotEmpty()) {
                    val lines = results.take(6).joinToString("\n") { poi ->
                        "- ${poi.name}（${poi.address.take(30)}）"
                    }
                    parts.add("【高德地图地点搜索】「$query」真实地点：\n$lines")
                }
            }
        }
    }

    return SearchBlockResult(
        block = buildString {
            if (parts.isNotEmpty()) append(parts.joinToString("\n\n---\n\n"))
            // 轻量档的“快”不只是搜得少，也包括答得短：避免一句天气也写成研究报告。
            if (activeProfile.limit <= 3 && parts.isNotEmpty()) {
                append("\n\n（本轮是轻量检索：请用 2~4 句话直接回答；没查到就直说，不要展开成长篇。）")
            }
        }.ifBlank { null },
        sources = sources,
        deck = deck,
        evidenceKey = evidenceKey,
        extraBlock = if (parts.size > extraStart) {
            parts.drop(extraStart).joinToString("\n\n---\n\n")
        } else null,
        contextChars = activeProfile.contextChars
    )
}

/** 不是地名的候选词（正则从问句里抠出来的常见误抓） */
private val NON_CITY_WORDS = setOf(
    "今天", "现在", "这里", "那里", "我们", "你们", "这个", "那个",
    "明天", "最近", "未来", "这边", "那边", "当地", "明后天", "这几天"
)

/** 剥掉城市名后面粘着的修饰词："大理现在"→"大理"，"大理市的"→"大理" */
private fun stripCityNoise(raw: String): String {
    var s = raw.trim()
    val noise = listOf("现在", "今天", "今日", "明天", "后天", "最近", "的", "市", "县", "区")
    var changed = true
    while (changed && s.length > 2) {
        changed = false
        for (n in noise) {
            if (s.length > 2 && s.endsWith(n)) {
                s = s.removeSuffix(n)
                changed = true
            }
        }
    }
    return s.trim()
}

/**
 * 本地事实拦截器：命中"现在几点/今天几号/天气怎么样/冷不冷/要不要带伞"等纯事实题时，
 * 直接用系统时钟 + 天气实况模板作答，完全不调用大模型。数字由代码算，保证 100% 正确。
 * 命中返回答案字符串；不属于这类问题（或问的是别的城市）返回 null，走正常模型流程。
 */
private suspend fun buildFactAnswer(state: AppState, raw: String): String? {
    val t = raw.trim()
    if (t.isBlank()) return null
    // 明显是操作指令/创作类（规划行程、写攻略、打开页面）不拦截
    if (t.contains("帮我规划") || t.contains("做攻略") || t.contains("写一篇") || t.contains("生成")) return null

    val timeHit = t.contains("几点") || t.contains("现在时间") || t.contains("现在几点") ||
        t.contains("今天几号") || t.contains("今天日期") || t.contains("现在日期") ||
        t.contains("今天星期") || t.contains("星期几") || t.contains("周几") ||
        t.contains("今天什么日子") || t.contains("现在几号")
    val weatherHit = t.contains("天气") || t.contains("气温") || t.contains("几度") ||
        t.contains("温度") || t.contains("冷不冷") || t.contains("热不热") ||
        t.contains("带伞") || t.contains("下雨") || t.contains("穿什么") || t.contains("刮风") ||
        t.contains("降温") || t.contains("升温")
    if (!timeHit && !weatherHit) return null

    val knownCities = (state.guides.map { it.city } + state.footprints.map { it.city } + state.weatherCity)
        .map { it.trim() }.filter { it.length >= 2 && it.isNotBlank() }.toSet()

    // 尝试从用户文本里提取城市名：匹配"XX天气/XX气温/XX怎么样"模式
    val requestedCity = if (weatherHit) {
        // 已知城市里命中的
        knownCities.firstOrNull { c -> t.contains(c) && c != state.weatherCity }
            ?: run {
                // 未知城市：提取"天气"前面的中文地名（2-4个字）
                val raw = Regex("([\\u4e00-\\u9fa5]{2,5})(?:天气|气温|温度|怎么样|几度)").find(t)
                    ?.groupValues?.getOrNull(1)
                // 候选要先剥掉"现在/今天/的"这类修饰词——否则"大理现在天气怎么样"会得到
                // 候选"大理现在"，搜不到任何城市，最后退化成用当前城市的实况回答，
                // 等于把重庆的气温报成了大理的（用户看到的就是"AI 瞎编天气"）。
                raw?.let { stripCityNoise(it) }?.takeIf { it.length >= 2 && it !in NON_CITY_WORDS }
            }
    } else null

    // 手上这份实况是不是"用户问的那个城市"的？不是就现查一次。
    // 查不到宁可不答（退回模型 + 联网搜索工具），也不能拿别的城市的温度冒充。
    var w = state.weatherNow
    if (weatherHit) {
        val asked = requestedCity ?: state.weatherCity.takeIf { it.isNotBlank() }
        val current = w?.city?.takeIf { it.isNotBlank() }
        if (asked != null && asked != current) {
            w = runCatching { com.example.my.data.WeatherApi.searchCity(asked).firstOrNull() }
                .getOrNull()
                ?.let { hit ->
                    runCatching { com.example.my.data.WeatherApi.fetch(hit.name, hit.lat, hit.lon) }.getOrNull()
                }
            if (w == null) return null
        }
    }

    val sb = StringBuilder()
    if (timeHit) {
        val now = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA).format(Date(now))
        val timeStr = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(now))
        sb.append("现在是 $dateStr $timeStr（手机本地时间）。")
    }
    if (weatherHit) {
        if (w == null) {
            sb.append(if (timeHit) " " else "")
            sb.append("暂时还没拿到天气实况，下拉一下天气页刷新后再问我，我不瞎报气温")
        } else {
            val today = w.days.firstOrNull()
            if (timeHit) sb.append("\n")
            sb.append("${w.city}现在 ${w.temp}℃ ${w.emoji}${w.desc}，湿度${w.humidity}%，风力${w.wind}级")
            if (today != null) sb.append("，今天 ${today.low}~${today.high}℃")
            sb.append("。")
            when {
                t.contains("带伞") || t.contains("下雨") -> {
                    val rain = today != null && (today.desc.contains("雨") || today.percent >= 40)
                    sb.append(if (rain) "今天有降水，建议带把伞" else "今天基本不下雨，不用带伞")
                }
                t.contains("冷不冷") || t.contains("穿什么") -> {
                    sb.append(
                        when {
                            w.temp <= 8 -> "挺冷的，套件厚外套"
                            w.temp <= 18 -> "有点凉，穿件薄外套刚好"
                            w.temp >= 28 -> "挺热的，短袖就行"
                            else -> "气温舒服，单层长袖刚好。"
                        }
                    )
                }
            }
        }
    }
    return sb.toString().ifBlank { null }
}

/** 模型自己判定的意图标签：看得见，点一下能改成正确的六类之一 */
@Composable
private fun IntentChip(decision: IntentDecision, onChange: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Bg.Surface)
                .clickable(onClick = onChange)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Lucide.Sparkles,
                contentDescription = null,
                tint = Brand.Terracotta,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = tr("模型判断：", "Model intent: ") + tr(decision.category.zh, decision.category.en),
                color = Text.Secondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = tr("更改", "Change"),
                color = Brand.Terracotta,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** 剥掉给 App 用的控制标记行（[记忆] / [追问] / <intent> / <action> / <next> / <plan> / <意图> / <下一步>） */
internal fun stripControlMarkers(text: String): String {
    if (text.isBlank()) return text
    val kept = text.lineSequence()
        .filterNot { l ->
            val t = l.trim()
            t.startsWith("[记忆]") || t.startsWith("[追问]")
        }
        .joinToString("\n")
    return stripActionMarkup(kept)
        // 数据校验/引用核对是给 App 看的内部提示，不塞进用户正文
        .replace(Regex("[（(]数据校验[：:][\\s\\S]*?[）)]"), "")
        .replace(Regex("[（(]数据校验[：:][^\\n]*$"), "")
        .let { cleanupPlainText(it) }
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

/**
 * 不同模型吐格式的习惯差别很大：有的带 markdown、代码块、推理标签，有的裹一层 JSON。
 * 这里统一剥掉这些非正文标记，只留给用户可读的文字（保留段落、标点与 [1] 这类引用编号）。
 */
internal fun cleanupPlainText(text: String): String {
    if (text.isBlank()) return text
    var out = text
    // 代码块围栏 ```lang ... ```：去掉围栏，保留块内文字
    out = Regex("```[\\w]*\\n?([\\s\\S]*?)```", RegexOption.IGNORE_CASE).replace(out) { m ->
        m.groupValues[1]
    }
    out = out.replace(Regex("```"), "")
    // 推理 / 思考标签块（含半开残尾）：整块不进正文
    out = Regex(
        "[<＜](?:think|reasoning|thought|思考|推理)[\\s\\S]*?[>＞][\\s\\S]*?[<＜]/(?:think|reasoning|thought|思考|推理)[>＞]\\n?",
        RegexOption.IGNORE_CASE
    ).replace(out, "")
    out = Regex(
        "[<＜](?:think|reasoning|thought|思考|推理)[\\s\\S]*$",
        RegexOption.IGNORE_CASE
    ).replace(out, "")
    // markdown 链接 [文字](url) → 只留文字；裸 url 不在此处理（引用来源另走来源卡片）
    out = Regex("\\[([^\\]]*)\\]\\(https?://[^)]*\\)").replace(out, "$1")
    // 行内代码 `x` → x；加粗 **x** / __x__ → x
    out = Regex("`([^`]+)`").replace(out, "$1")
    out = Regex("\\*\\*([^*]+)\\*\\*").replace(out, "$1")
    out = Regex("__([^_]+)__").replace(out, "$1")
    // 标题前缀 #、## 等
    out = Regex("^#{1,6}\\s+", RegexOption.MULTILINE).replace(out, "")
    // 整段被 JSON 裹住时，尝试取出里面的 text / content / answer 字段
    out = unwrapJsonObject(out)
    return out
}

/** 回复被模型裹成 JSON 时，提取常见正文字段；提取失败原样返回。 */
private fun unwrapJsonObject(text: String): String {
    val t = text.trim()
    if (!t.startsWith("{") || !t.endsWith("}")) return t
    return runCatching {
        val o = org.json.JSONObject(t)
        val field = listOf("text", "content", "answer", "reply", "message")
            .firstNotNullOfOrNull { o.optString(it).takeIf { v -> v.isNotBlank() } }
        field ?: t
    }.getOrDefault(t)
}

/** 控制标签的中英文写法都要认：云端模型偶尔会把 <intent> 译成 <意图> 输出 */
private val CONTROL_BLOCK_RE = Regex(
    "[<＜](?:intent|action|next|plan|意图|下一步|计划)[\\s\\S]*?[>＞]" +
        "[\\s\\S]*?[<＜]/(?:intent|action|next|plan|意图|下一步|计划)[>＞]\\n?",
    RegexOption.IGNORE_CASE
)

private val CONTROL_OPEN_TAIL_RE = Regex(
    "[<＜](?:intent|action|next|plan|意图|下一步|计划)[\\s\\S]*$",
    RegexOption.IGNORE_CASE
)

/** 从 AI 回复里去掉 <intent>…</intent> / <意图>…</意图> 等控制块及其流式残尾 */
private fun stripActionMarkup(text: String): String {
    if (text.isBlank()) return text
    return text
        .replace(CONTROL_BLOCK_RE, "")
        // 打字机逐字显示时标记只吐了一半（"<intent{" / "<next>[{\"la" 还没闭合），
        // 从半截标签起整段藏住，不让 JSON 碎片闪现到气泡里
        .replace(CONTROL_OPEN_TAIL_RE, "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

/**
 * 流式渲染时，末尾那半截正在输入的标记（「[追」「<inte」「<意图」）还没形成完整行，
 * 用 startsWith 判断不出来，会短暂显示成乱符号。这里先把没闭合的行藏住。
 */
private fun trimControlTail(text: String): String {
    if (text.isBlank()) return text
    val lines = text.split("\n")
    val last = lines.lastOrNull() ?: return text
    val t = last.trimStart()
    if (t.startsWith("[") && !t.contains("]")) {
        return lines.dropLast(1).joinToString("\n").trimEnd()
    }
    val lt = t.lastIndexOf('<')
    if (lt >= 0) {
        val tail = t.substring(lt)
        val markers = listOf("<intent", "<action", "<next", "<plan", "<意图", "<下一步", "<计划")
        val halfTag = !tail.contains('>') &&
            markers.any { it.startsWith(tail) || tail.startsWith(it) }
        if (halfTag) {
            return (lines.dropLast(1) + t.substring(0, lt)).joinToString("\n").trimEnd()
        }
    }
    return text
}

/** 取模型给出的推荐追问：「[追问] A | B | C」。格式不对就返回空，交给本地规则兜底 */
private fun extractFollowUps(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val line = text.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("[追问]") }
        ?: return emptyList()
    return line.removePrefix("[追问]")
        .split('|', '｜')
        .map { it.trim().removePrefix("-").trim() }
        .filter { it.length in 2..26 }
        .distinct()
        .take(3)
}

/**
 * 本地兜底追问：按"这一轮到底聊了什么"生成。
 *
 * 旧实现每条回复后都推「帮我规划具体行程 / 整理成一篇攻略 / 记一笔足迹 / 打开足迹地图」，
 * 用户问天气也推、问门票也推，看起来像摆了一排无关按钮。这里改为先认出城市和话题，
 * 再给贴合上下文的问题；末尾那条"记成足迹"是 AI 与 App 真正连起来的入口。
 */
private fun smartSuggestions(userText: String, reply: String): List<String> {
    val both = "$userText\n$reply"
    val city = com.example.my.data.CityCoords.detectIn(userText)
        ?: com.example.my.data.CityCoords.detectIn(reply)
    val weather = listOf("天气", "气温", "温度", "几度", "下雨", "降雨").any { both.contains(it) }
    val food = listOf("美食", "小吃", "好吃", "餐厅", "吃什么", "馆子", "特色菜").any { both.contains(it) }
    val spot = listOf("景点", "好玩", "小众", "打卡", "值得去", "必去", "游玩").any { both.contains(it) }
    val plan = listOf("Day 1", "Day1", "第一天", "行程", "三天", "四天", "两日", "几日", "怎么安排")
        .any { both.contains(it) }

    val out = mutableListOf<String>()
    if (city != null) {
        when {
            weather -> {
                out += "${city}未来一周天气怎么样"
                out += "${city}这个季节适合穿什么"
            }

            food -> {
                out += "${city}有哪些本地人常去的小馆子"
                out += "${city}有什么必吃的特色菜"
            }

            spot -> {
                out += "${city}三天怎么安排比较顺"
                out += "${city}有哪些人少的小众去处"
            }

            plan -> {
                out += "${city}这个行程大概花多少钱"
                out += "${city}住哪个区域比较方便"
            }

            else -> {
                out += "${city}有哪些值得去的地方"
                out += "${city}有什么好吃的"
            }
        }
        out += "帮我把「$city」记成一条足迹"
    } else {
        when {
            plan -> {
                out += "把上面的安排排成一份行程计划"
                out += "这个行程大概要花多少钱"
            }

            weather -> out += "那未来三天呢"
            else -> {
                out += "说得再具体一点，给几个地名"
                out += "帮我按这个思路规划三天行程"
            }
        }
    }
    return out.distinct().take(3)
}
