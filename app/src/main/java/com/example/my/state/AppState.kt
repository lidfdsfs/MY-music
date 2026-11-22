package com.example.my.state

import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.my.data.AmapApi
import com.example.my.data.AiTripBlueprint
import com.example.my.data.AuthResult
import com.example.my.data.BackendApi
import com.example.my.data.Captcha
import com.example.my.data.ChinaMapData
import com.example.my.data.CloudConfig
import com.example.my.data.CommandIntent
import com.example.my.data.DeletionStatus
import com.example.my.data.FootprintBlueprint
import com.example.my.data.FollowState
import com.example.my.data.GuideBlueprint
import com.example.my.data.Locator
import com.example.my.data.MockData
import com.example.my.data.MicroFeedSeed
import com.example.my.data.MicroFeedStore
import com.example.my.data.microfeed.MicroFeedApi
import com.example.my.data.WakeWord
import com.example.my.data.Persistence
import com.example.my.data.PersistedData
import com.example.my.data.SavedAccount
import com.example.my.data.TrackPoint
import com.example.my.data.TrackForegroundService
import com.example.my.data.TrackRecorder
import com.example.my.data.TrackService
import com.example.my.data.TrackWriter
import com.example.my.data.UserScope
import com.example.my.data.VerifyLink
import com.example.my.data.WeatherApi
import com.example.my.data.WeatherSnapshot
import com.example.my.data.WebSafety
import com.example.my.data.ai.AiCapabilities
import com.example.my.data.ai.GateOnly
import com.example.my.data.ai.LocalLlm
import com.example.my.data.local.OfflineCache
import com.example.my.data.local.PendingUploadWorker
import com.example.my.data.local.PlanReminderWorker
import com.example.my.data.local.UploadQueue
import com.example.my.data.local.guideToJson
import com.example.my.data.local.routeToJson
import com.example.my.data.sync.CloudRestore
import com.example.my.data.sync.CloudSync
import com.example.my.model.AppNotification
import com.example.my.model.Comment
import com.example.my.model.CommentReply
import com.example.my.model.CostItem
import com.example.my.model.DayPlan
import com.example.my.model.Footprint
import com.example.my.model.Guide
import com.example.my.model.Medal
import com.example.my.model.MicroAuthor
import com.example.my.model.MicroComment
import com.example.my.model.MicroPost
import com.example.my.model.Route
import com.example.my.model.RouteMark
import com.example.my.model.RoutePoint
import com.example.my.model.PendingRouteMark
import com.example.my.model.Spot
import com.example.my.model.TripPlan
import com.example.my.model.UserData
import com.example.my.model.UserSettings
import com.example.my.ui.i18n.Lang
import com.example.my.ui.i18n.tr
import com.example.my.ui.interaction.Haptics
import com.example.my.util.LocalBackup
import com.example.my.util.PhotoStore
import com.example.my.util.SplashGate
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 底部导航 Tab */
enum class AppTab(val label: String) {
    DISCOVER("发现"),
    MAP("微足迹"),
    TRIPS("行程"),
    PROFILE("我的")
}

/** 登录门禁状态 */
enum class AuthState { CHECKING, LOGGED_OUT, LOGGED_IN, GUEST }

/** 认证类操作结果（ok=false 时 message 为可直接展示的错误文案） */
data class AuthOpResult(
    val ok: Boolean,
    val message: String? = null,
    /** 服务端要求先过图形验证码（登录连续失败 / 注册必填） */
    val needCaptcha: Boolean = false
)

/**
 * 全局应用状态（对应网页端 App.jsx 的集中状态管理）。
 * 业务数据内存态 + 用户行为通过 DataStore 持久化（重启不丢：昵称/签名、新增足迹、点赞收藏、公开状态、设置）。
 */
class AppState(private val context: Context) {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, t ->
            // 后台恢复/同步抛出的异常不能把整个 App 带走：落崩溃日志，界面继续保持可进入状态
            runCatching { com.example.my.data.CrashLog.record(context, t) }
            // 初始化失败时不要让启动页永远转圈：退回登录页，用户仍可手动点“离线使用”
            if (authState == AuthState.CHECKING) {
                restoreProgress = null
                authState = AuthState.LOGGED_OUT
            }
        }
    )

    // ---------- UI 状态 ----------
    // 只有本进程第一次进入界面才显示启动页；Activity 重建、通知/小组件热启动都为 false
    var showSplash by mutableStateOf(SplashGate.consumeLaunch())

    var activeTab by mutableStateOf(AppTab.DISCOVER)
    /** 足迹页内的分段：0 = 地图足迹，1 = 旅行回忆（回忆已并入足迹页） */
    var footprintMode by mutableIntStateOf(0)
    /** AI 创作专区当前分段：0 = 图片生成，1 = 视频生成 */
    var creativeMode by mutableIntStateOf(0)

    var currentGuideId by mutableStateOf<Int?>(null)

    /** 最后查看的攻略 ID（持久化，发现页快捷入口） */
    var lastViewedGuideId by mutableStateOf<Int?>(null)
        private set
    var currentRouteId by mutableStateOf<Int?>(null)
    var showUpload by mutableStateOf(false)

    /** 桌面小组件「拍照即定位」：打开发布页后自动唤起一次相机 */
    var autoOpenCameraOnUpload by mutableStateOf(false)

    /**
     * 从「AI 旅行助手」进入新建足迹页时置 true：发布页会显示 AI 一键生成引导，
     * 并在标题栏标明这是 AI 生成流程。进入后由发布页消费一次，避免下次普通新建还挂着。
     */
    var aiFootprintAssist by mutableStateOf(false)
        private set

    /**
     * 发布页里的「地图选点」全屏页。
     *
     * 放在 AppState 而不是上传页内部 `remember`：系统返回键由 MainActivity 统一分层关闭，
     * 必须能看见这一层的开合，否则返回键会越过选点页直接把整个发布页关掉。
     */
    var mapPickOpen by mutableStateOf(false)
        private set

    /** 打开地图选点页（发布页内点击「地图选点」） */
    fun openMapPick() {
        mapPickOpen = true
    }

    /** 关闭地图选点页，退回发布页 */
    fun closeMapPick() {
        mapPickOpen = false
    }

    /** 发布页是否有未保存的内容（照片/标题/备注/地点），决定关闭时要不要二次确认 */
    var uploadDirty by mutableStateOf(false)
        private set

    /** 外部（系统返回键）请求关闭发布页，但内容没保存 —— 由发布页弹确认框 */
    var uploadExitRequested by mutableStateOf(false)
        private set

    /** 导航到哪个页面（设置页/通知页等全屏页） */
    var screen by mutableStateOf(ExtraScreen.NONE)

    /** 从 AI 旅行助手进入创作专区时记住来源，关专区后回到助手，不直接掉回首页 */
    private var screenBeforeCreative by mutableStateOf(ExtraScreen.NONE)

    /** 网页搜索关键词（WebView 页面用） */
    var webSearchQuery by mutableStateOf("")
        private set

    /** 非空时 WebView 直接打开这个原网页，而不是再拼一次 Bing 搜索地址（引用来源用） */
    var webDirectUrl by mutableStateOf("")
        private set

    /** 打开网页页之前待的页面：关网页时原路退回（音乐页搜歌 → 关网页回音乐页，而不是掉回主界面） */
    private var screenBeforeWeb by mutableStateOf(ExtraScreen.NONE)

    private fun enterWebScreen() {
        if (screen != ExtraScreen.WEB_SEARCH) screenBeforeWeb = screen
        screen = ExtraScreen.WEB_SEARCH
    }

    /** 关闭网页页并退回原页面 */
    fun closeWebSearch() {
        val back = screenBeforeWeb
        screenBeforeWeb = ExtraScreen.NONE
        screen = back
    }

    fun openWebSearch(q: String) {
        webSearchQuery = q
        webDirectUrl = ""
        enterWebScreen()
    }

    /** 在 App 内打开某个具体网页（AI 答案下方的引用来源）；不安全地址直接拒绝，绝不进 WebView */
    fun openUrl(url: String) {
        val safe = WebSafety.sanitize(url) ?: return
        webDirectUrl = safe
        webSearchQuery = hostOf(safe)
        enterWebScreen()
    }

    /**
     * 歌曲搜索：歌名按 UTF-8 逐字节转 %XX 拼进搜索地址，交给内置浏览器打开。
     * 例：红尘客栈 → https://www.gequhai.com/search?s=%E7%BA%A2%E5%B0%98%E5%AE%A2%E6%A0%88
     * 没用 Uri.encode 是为了保证每个字节都显式编码（空格、英文也一样变 %20 这种形式）。
     */
    fun openSongSearch(name: String) {
        val song = name.trim()
        if (song.isEmpty()) return
        webDirectUrl = SONG_SEARCH_BASE + pctEncodeUtf8(song)
        webSearchQuery = song
        enterWebScreen()
    }

    private fun pctEncodeUtf8(text: String): String = buildString {
        text.toByteArray(Charsets.UTF_8).forEach { b ->
            val v = b.toInt() and 0xFF
            append('%')
            append(HEX_DIGITS[v shr 4])
            append(HEX_DIGITS[v and 0x0F])
        }
    }

    private fun hostOf(url: String): String = runCatching {
        java.net.URL(url).host.orEmpty().removePrefix("www.").ifBlank { "网页来源" }
    }.getOrDefault("网页来源")

    // ---------- AI 就地入口 ----------
    /** 就地卡片是否展开（悬浮球点击时先不跳全屏，就在当前页面问） */
    var aiInlineOpen by mutableStateOf(false)
    /** 唤起入口时所在的现场描述，例如「攻略详情·大理三日慢游」 */
    var aiEntryHint by mutableStateOf<String?>(null)
    /** 从就地入口带进助手页、需要自动发出的问题 */
    var aiPrefill by mutableStateOf<String?>(null)

    fun openAiInline() {
        aiInlineOpen = true
        aiEntryHint = com.example.my.data.ai.AppContextSnapshot.sceneLabel(this)
    }

    fun closeAiInline() {
        aiInlineOpen = false
    }

    /** 就地提问：带着问题进完整助手，页面起来后自动发送 */
    fun askAi(question: String) {
        val q = question.trim()
        if (q.isBlank()) return
        aiPrefill = q
        aiInlineOpen = false
        // 记录现场：无论是悬浮球唤起还是页面内嵌卡片点 chip，都带上"你正在看哪篇攻略/哪个页面"，
        // AI 回复时知道上下文，也能正确理解"这个/刚才那篇"这类指代
        aiEntryHint = com.example.my.data.ai.AppContextSnapshot.sceneLabel(this)
        openScreen(ExtraScreen.AI_ASSISTANT)
    }

    /** 直接打开完整助手（就地卡片里的「打开完整助手」） */
    fun openAiFull() {
        aiInlineOpen = false
        openScreen(ExtraScreen.AI_ASSISTANT)
    }

    /** 打开设置并直接定位到「AI 与语音 / 模型」分类；-1 表示不指定，回主列表 */
    var settingsInitialPage by mutableStateOf(-1)
        private set

    /** 从 AI 助手等入口跳转设置里的模型相关页面 */
    fun openAiSettings() {
        settingsInitialPage = 1 // 对应 SettingsPage.AI 的索引
        openScreen(ExtraScreen.SETTINGS)
    }

    /** 设置页读取后清除一次性定位标记 */
    fun consumeSettingsInitialPage(): Int {
        val p = settingsInitialPage
        settingsInitialPage = -1
        return p
    }

    /**
     * 设置页一次性定位目标（能力表 / AI 助手用）。
     *
     * 取值：main/account/data/ai/ai_routing/general/about（二级页），
     * 或 trash/backup/language/theme/haptic/background/widget/profile/log/privacy/security/local_model/voice_library/api_model（直接打开对应弹层）。
     * 与 [settingsInitialPage] 并存：老入口继续用整数索引，新入口用字符串更直观。
     */
    var settingsInitialTarget by mutableStateOf<String?>(null)
        private set

    fun openSettings(target: String? = null) {
        settingsInitialTarget = target
        openScreen(ExtraScreen.SETTINGS)
    }

    fun consumeSettingsInitialTarget(): String? {
        val t = settingsInitialTarget
        settingsInitialTarget = null
        return t
    }

    /** 新建出行计划弹窗 */
    var showPlanSheet by mutableStateOf(false)

    private val prefs by lazy { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }

    /** 出行计划（本地持久化；创建时按出发日期安排系统提醒） */
    var tripPlans by mutableStateOf(emptyList<TripPlan>())
        private set

    /** 课表（本地持久化） */
    var courses by mutableStateOf(emptyList<com.example.my.model.Course>())
    /** 课表作息：一天几节、每节多长、开学周等，课表页与编辑面板都读它 */
    var courseTable by mutableStateOf(com.example.my.model.CourseTableSettings())
        private set

    /** 行程排序方式：false = 按出发时间，true = 自定义拖拽排序（重启保留） */
    var planCustomOrder by mutableStateOf(prefs.getBoolean(KEY_PLAN_CUSTOM_ORDER, false))
        private set

    /** 常用同行人（新建/编辑行程时可直接勾选） */
    var commonCompanions by mutableStateOf(emptyList<String>())
        private set

    /** 用户保留的清单模板（删除行程时选择保留，「分类|物品」编码） */
    var packingTemplate by mutableStateOf(emptyList<String>())
        private set

    // 全局 Toast
    var toastText by mutableStateOf<String?>(null)
        private set
    var toastSeq by mutableStateOf(0)
        private set

    // ---------- AI 悬浮球（默认关闭，设置页开关 / 长按关闭，持久化重启不丢） ----------
    var aiBallEnabled by mutableStateOf(prefs.getBoolean(KEY_AI_BALL, false))
        private set

    /** 开启/关闭 AI 悬浮球并持久化 */
    fun updateAiBallEnabled(on: Boolean) {
        aiBallEnabled = on
        prefs.edit().putBoolean(KEY_AI_BALL, on).apply()
    }

    // ---------- 主题模式（跟随系统 / 浅色 / 深色，持久化，即时换肤） ----------
    var themeMode by mutableStateOf(prefs.getInt(KEY_THEME_MODE, 0))
        private set

    init {
        // AppState 创建时把保存的主题模式同步到全局，根主题据此换肤
        com.example.my.ui.theme.themeMode = themeMode
    }

    /** 0=跟随系统，1=浅色，2=深色 */
    fun updateThemeMode(mode: Int) {
        themeMode = mode
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
        com.example.my.ui.theme.themeMode = mode
    }

    // ---------- AI 模型能力（视觉 / 语音 / 本地大模型，全部可在设置里单独开关） ----------
    var aiCapabilities by mutableStateOf(AiCapabilities.load(prefs))
        private set

    /** 修改任意一项 AI 能力并持久化，同时触发界面重组 */
    fun updateAiCapabilities(transform: (AiCapabilities) -> AiCapabilities) {
        val next = transform(aiCapabilities)
        if (next == aiCapabilities) return
        aiCapabilities = next
        AiCapabilities.save(prefs, next)
    }

    // ---------- 语音唤醒（开关与唤醒词都落本机，改完立即生效） ----------
    var wakeWordEnabled by mutableStateOf(WakeWord.enabled(context))
        private set
    var wakeWordPhrase by mutableStateOf(WakeWord.phrase(context))
        private set

    fun enableWakeWord(on: Boolean) {
        wakeWordEnabled = on
        WakeWord.setEnabled(context, on)
    }

    fun updateWakePhrase(text: String) {
        val next = text.trim().ifBlank { WakeWord.DEFAULT_PHRASE }
        if (next == wakeWordPhrase) return
        wakeWordPhrase = next
        WakeWord.setPhrase(context, next)
    }

    // ---------- API 模型端点管理（全部由用户自行接入，不再内置云端端点） ----------
    private val KEY_ENDPOINTS = "ai_endpoints_list"
    private val KEY_CURRENT_ENDPOINT = "ai_current_endpoint_id"
    private val KEY_CURRENT_IMAGE_ENDPOINT = "ai_current_image_endpoint_id"
    private val KEY_CURRENT_VIDEO_ENDPOINT = "ai_current_video_endpoint_id"
    private val KEY_OCR_ENDPOINT = "ai_ocr_endpoint"

    /** 用户保存的云端对话端点列表（OpenAI / Anthropic / Gemini 兼容） */
    var aiEndpoints by mutableStateOf(
        com.example.my.data.AiPresets.decode(prefs.getString(KEY_ENDPOINTS, null))
    )
        private set

    /** 独立的 OCR 文字识别端点，与对话模型分开配置；未接入时回退端侧识别 */
    var ocrEndpoint by mutableStateOf(
        com.example.my.data.AiPresets.OcrEndpoint.decode(prefs.getString(KEY_OCR_ENDPOINT, null))
    )
        private set

    /** 是否已接入至少一个可用云端端点 */
    val hasCloudEndpoint: Boolean
        get() = com.example.my.data.AiPresets.hasCloudEndpoint(aiEndpoints)

    /** 保存 OCR 端点配置（只存本机） */
    fun saveOcrEndpoint(baseUrl: String, apiKey: String, model: String) {
        ocrEndpoint = com.example.my.data.AiPresets.OcrEndpoint(
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            model = model.trim()
        )
        prefs.edit().putString(KEY_OCR_ENDPOINT, ocrEndpoint.encode()).apply()
        scheduleAiConfigUpload()
    }

    /** 清除已接入的 OCR 端点 */
    fun clearOcrEndpoint() {
        ocrEndpoint = com.example.my.data.AiPresets.OcrEndpoint()
        prefs.edit().remove(KEY_OCR_ENDPOINT).apply()
        scheduleAiConfigUpload()
    }

    /**
     * OCR 实际使用的端点：手动给「图片文字识别」指定了云端模型时优先用它，
     * 否则继续用设置里独立配置的 OCR 端点；独立端点也没配时，跟随全局图像识别模型。
     */
    fun effectiveOcrEndpoint(): com.example.my.data.AiPresets.OcrEndpoint {
        val route = aiFeatureRoute(com.example.my.data.AiFeature.OCR)
        val manual = route.startsWith(com.example.my.data.AiFeature.ROUTE_PREFIX_API)
        if (manual) {
            val ep = aiFeatureEndpoint(com.example.my.data.AiFeature.OCR)
            if (ep.ready && ep.supports(com.example.my.data.AiCapability.VISION)) {
                return com.example.my.data.AiPresets.OcrEndpoint(
                    baseUrl = ep.baseUrl,
                    apiKey = ep.apiKey,
                    model = ep.vision
                )
            }
        }
        if (ocrEndpoint.ready) return ocrEndpoint
        val ep = aiFeatureEndpoint(com.example.my.data.AiFeature.OCR)
        return if (ep.ready && ep.supports(com.example.my.data.AiCapability.VISION)) {
            com.example.my.data.AiPresets.OcrEndpoint(
                baseUrl = ep.baseUrl,
                apiKey = ep.apiKey,
                model = ep.vision
            )
        } else ocrEndpoint
    }

    /**
     * 兼容旧版本写入的端点 ID 类型。
     *
     * 历史版本把端点 ID 存成 Int，后来改成 Long 后，老用户升级时
     * SharedPreferences.getLong() 会对 Integer 抛 ClassCastException，导致启动即闪退。
     * 这里按 Number 读取，无论旧值是 Int 还是 Long 都不会崩。
     */
    private fun readEndpointId(key: String): Long =
        (prefs.all[key] as? Number)?.toLong() ?: 0L

    /** 当前选中的端点 ID；未接入任何端点时为 0 */
    var currentEndpointId by mutableStateOf(readEndpointId(KEY_CURRENT_ENDPOINT))
        private set

    /** 图片生成当前选中的端点 ID；与对话模型分开记忆 */
    var currentImageEndpointId by mutableStateOf(readEndpointId(KEY_CURRENT_IMAGE_ENDPOINT))
        private set

    /** 视频生成当前选中的端点 ID；与对话模型分开记忆 */
    var currentVideoEndpointId by mutableStateOf(readEndpointId(KEY_CURRENT_VIDEO_ENDPOINT))
        private set

    val chatEndpoints: List<com.example.my.data.AiEndpoint>
        get() = aiEndpoints.filter { it.supports(com.example.my.data.AiCapability.TEXT) }

    val visionEndpoints: List<com.example.my.data.AiEndpoint>
        get() = aiEndpoints.filter { it.supports(com.example.my.data.AiCapability.VISION) }

    val imageEndpoints: List<com.example.my.data.AiEndpoint>
        get() = aiEndpoints.filter { it.supports(com.example.my.data.AiCapability.IMAGE_GEN) }

    val videoEndpoints: List<com.example.my.data.AiEndpoint>
        get() = aiEndpoints.filter { it.supports(com.example.my.data.AiCapability.VIDEO_GEN) }

    /**
     * 对话模型的唯一真源：1 = 对话框里选中的 API 端点，2 = 对话框里选中的本地模型，
     * 3 = 自动路由（简单问题走本地、复杂问题走云端）。
     *
     * 用户在对话框底部选哪个模型，就用哪个模型；选「自动」时由路由层按问题分流。
     * 老版本保存的 0 = 自动路由在读取时迁移为 1，避免「明明选了模型却被自动切走」。
     */
    private val _runMode = mutableStateOf(
        prefs.getInt("ai_run_mode", 0).let { if (it == 0) 1 else it }
    )
    var runMode: Int
        get() = if (_runMode.value == 0) 1 else _runMode.value
        set(v) {
            _runMode.value = when (v) {
                2 -> 2
                3 -> 3
                else -> 1
            }
            prefs.edit().putInt("ai_run_mode", _runMode.value).apply()
            scheduleAiConfigUpload()
        }

    /** 当前实际生效的模型名（对话框底部那一行显示的就是它） */
    val activeModelName: String
        get() = when (runMode) {
            2 -> com.example.my.data.ai.LocalLlm.activeSpec(context).display
            3 -> "智能分配"
            else -> if (aiFeatureUsesLocal(com.example.my.data.AiFeature.ASSISTANT)) {
                com.example.my.data.ai.LocalLlm.activeSpec(context).display
            } else {
                val ep = aiFeatureEndpoint(com.example.my.data.AiFeature.ASSISTANT)
                if (ep.ready) ep.name else (if (Lang.isEn) "No cloud model" else "未接入云端模型")
            }
        }

    /** 能力网关的分级参数：选中本地模型时按 LOCAL（拦联网能力），其余按 API */
    val effectiveChatMode: com.example.my.data.ai.ChatMode
        get() = if (runMode == 2 ||
            (runMode != 3 && aiFeatureUsesLocal(com.example.my.data.AiFeature.ASSISTANT))
        ) com.example.my.data.ai.ChatMode.LOCAL
        else com.example.my.data.ai.ChatMode.API

    /** AI 长期记忆开关：关闭后不注入记忆、不再自动记录偏好 */
    private val _aiMemoryEnabled = mutableStateOf(prefs.getBoolean("ai_memory_enabled", true))
    var aiMemoryEnabled: Boolean
        get() = _aiMemoryEnabled.value
        set(v) {
            _aiMemoryEnabled.value = v
            prefs.edit().putBoolean("ai_memory_enabled", v).apply()
            scheduleAiConfigUpload()
        }

    /** 当前生效的对话端点；未接入任何端点时返回一个空占位，调用方据此提示用户去配置 */
    val currentEndpoint: com.example.my.data.AiEndpoint
        get() = chatEndpoints.firstOrNull { it.id == currentEndpointId }
            ?: chatEndpoints.firstOrNull()
            ?: com.example.my.data.AiPresets.builtin

    /** 当前生效的图片生成端点；没有接入时返回 null，由界面引导去模型管理 */
    val currentImageEndpoint: com.example.my.data.AiEndpoint?
        get() = imageEndpoints.firstOrNull { it.id == currentImageEndpointId }
            ?: imageEndpoints.firstOrNull { it.ready }
            ?: imageEndpoints.firstOrNull()

    /** 当前生效的视频生成端点；没有接入时返回 null，由界面引导去模型管理 */
    val currentVideoEndpoint: com.example.my.data.AiEndpoint?
        get() = videoEndpoints.firstOrNull { it.id == currentVideoEndpointId }
            ?: videoEndpoints.firstOrNull { it.ready }
            ?: videoEndpoints.firstOrNull()

    // ---------- AI 模型分配（按功能指定云端端点 / 本地模型，或全局智能分配） ----------
    private val KEY_AI_ROUTING_SMART = "ai_routing_smart"
    private val KEY_AI_ROUTING_LOCAL = "ai_routing_local"
    private val KEY_AI_ROUTE_PREFIX = "ai_route_"

    /** 全局智能分配：按任务复杂度自动挑模型；开启后本页的单功能指定暂时不生效 */
    var aiRoutingSmart by mutableStateOf(prefs.getBoolean(KEY_AI_ROUTING_SMART, false))
        private set

    /** 智能分配是否允许本机模型参与：开启后轻任务优先走端侧，失败自动回退云端 */
    var aiRoutingLocal by mutableStateOf(prefs.getBoolean(KEY_AI_ROUTING_LOCAL, true))
        private set

    /** key = AiFeature.key，value = "global" 或 "ep:<id>" */
    var aiRouteMap by mutableStateOf(loadAiRouteMap())
        private set

    private fun loadAiRouteMap(): Map<String, String> {
        val defaults = com.example.my.data.AiFeature.values()
            .associate { it.key to it.defaultRoute }
        val saved = defaults.mapValues { (key, def) ->
            prefs.getString(KEY_AI_ROUTE_PREFIX + key, def) ?: def
        }.toMutableMap()
        // 兼容旧的自定义键，忽略已下线的功能
        prefs.all.forEach { (k, v) ->
            if (k.startsWith(KEY_AI_ROUTE_PREFIX) && v is String) {
                val feature = com.example.my.data.AiFeature.fromKey(k.removePrefix(KEY_AI_ROUTE_PREFIX))
                if (feature != null) saved[feature.key] = v
            }
        }
        return saved
    }

    fun setAiRoutingSmartEnabled(enabled: Boolean) {
        aiRoutingSmart = enabled
        prefs.edit().putBoolean(KEY_AI_ROUTING_SMART, enabled).apply()
        scheduleAiConfigUpload()
    }

    fun setAiRoutingLocalEnabled(enabled: Boolean) {
        aiRoutingLocal = enabled
        prefs.edit().putBoolean(KEY_AI_ROUTING_LOCAL, enabled).apply()
        scheduleAiConfigUpload()
    }

    /** 这个功能可以选择的云端端点：按功能需要的能力过滤，媒体功能只列图片/视频模型 */
    fun aiFeatureCandidates(feature: com.example.my.data.AiFeature): List<com.example.my.data.AiEndpoint> =
        when (feature.capability) {
            com.example.my.data.AiCapability.IMAGE_GEN -> imageEndpoints
            com.example.my.data.AiCapability.VIDEO_GEN -> videoEndpoints
            com.example.my.data.AiCapability.VISION -> visionEndpoints
            com.example.my.data.AiCapability.TEXT -> chatEndpoints
        }

    fun setAiFeatureRoute(feature: com.example.my.data.AiFeature, route: String) {
        val candidates = aiFeatureCandidates(feature)
        val safe = when {
            route == com.example.my.data.AiFeature.ROUTE_GLOBAL -> route
            route == com.example.my.data.AiFeature.ROUTE_LOCAL && feature.localCapable -> route
            route.startsWith(com.example.my.data.AiFeature.ROUTE_LOCAL_PREFIX) && feature.localCapable -> {
                val modelId = com.example.my.data.AiFeature.localModelId(route)
                if (modelId != null &&
                    com.example.my.data.ai.LocalLlm.modelDownloaded(context, modelId)
                ) route else com.example.my.data.AiFeature.ROUTE_LOCAL
            }
            route.startsWith(com.example.my.data.AiFeature.ROUTE_PREFIX_API) -> {
                val id = route.removePrefix(com.example.my.data.AiFeature.ROUTE_PREFIX_API).toLongOrNull()
                if (id != null && candidates.any { it.id == id }) route else com.example.my.data.AiFeature.ROUTE_GLOBAL
            }
            else -> com.example.my.data.AiFeature.ROUTE_GLOBAL
        }
        aiRouteMap = aiRouteMap + (feature.key to safe)
        prefs.edit().putString(KEY_AI_ROUTE_PREFIX + feature.key, safe).apply()
        scheduleAiConfigUpload()
    }

    /** 把所有功能恢复为「跟随全局模型」，不改变智能分配开关 */
    fun resetAiFeatureRoutes() {
        aiRouteMap = com.example.my.data.AiFeature.values().associate { it.key to it.defaultRoute }
        prefs.edit().apply {
            com.example.my.data.AiFeature.values().forEach { feature ->
                putString(KEY_AI_ROUTE_PREFIX + feature.key, feature.defaultRoute)
            }
        }.apply()
        scheduleAiConfigUpload()
    }

    /** 当前全局主力模型（对话底部选择的端点） */
    private val builtinEndpoint: com.example.my.data.AiEndpoint
        get() = chatEndpoints.firstOrNull() ?: com.example.my.data.AiPresets.builtin

    /** 该功能当前保存的分配值：global / local / local:<modelId> / ep:<id> */
    fun aiFeatureRoute(feature: com.example.my.data.AiFeature): String =
        aiRouteMap[feature.key] ?: feature.defaultRoute

    /**
     * 这个功能当前是否该交给本机模型。
     *
     * 只有声明了 localCapable 的短任务才参与本机路由；本地模型未就绪、用户关掉
     * 「允许本机参与」或手动指定了云端端点时都返回 false。智能分配下重任务始终走云端，
     * 轻任务优先本机；手动模式下只有显式选择「本机模型」才走本地。
     */
    fun aiFeatureUsesLocal(feature: com.example.my.data.AiFeature): Boolean {
        if (!feature.localCapable || !aiRoutingLocal) return false
        if (localLlmState != com.example.my.data.ai.LocalLlmState.READY) return false
        if (aiRoutingSmart) return !feature.heavy
        return when (val route = aiFeatureRoute(feature)) {
            com.example.my.data.AiFeature.ROUTE_LOCAL -> true
            else -> {
                val modelId = com.example.my.data.AiFeature.localModelId(route)
                modelId != null &&
                    com.example.my.data.ai.LocalLlm.modelDownloaded(context, modelId)
            }
        }
    }

    /**
     * 该功能选中的具体本地模型 ID；没选本机、或本机不可用时返回 null。
     * 旧值 local（只写了「本机」）沿用当前启用的模型。
     */
    fun aiFeatureLocalModelId(feature: com.example.my.data.AiFeature): String? {
        if (!aiFeatureUsesLocal(feature)) return null
        val modelId = com.example.my.data.AiFeature.localModelId(aiFeatureRoute(feature))
        return modelId ?: com.example.my.data.ai.LocalLlm.activeSpec(context).id
    }

    /**
     * 某个功能实际使用的云端端点。
     *
     * - 走本机模型时，这里返回第一个云端端点作为端侧失败后的回退（未接入则为空占位）；
     * - 图片 / 视频功能只用对应能力的模型，默认跟随「当前图片 / 视频模型」；
     * - 智能分配：重任务跟当前主力模型，轻任务同样跟随全局，不再回退内置；
     * - 手动分配：指定了有效端点就用它，否则跟随全局。
     */
    fun aiFeatureEndpoint(feature: com.example.my.data.AiFeature): com.example.my.data.AiEndpoint {
        if (aiFeatureUsesLocal(feature)) return builtinEndpoint
        val route = aiRouteMap[feature.key] ?: feature.defaultRoute
        val id = route.removePrefix(com.example.my.data.AiFeature.ROUTE_PREFIX_API).toLongOrNull()
        val candidates = aiFeatureCandidates(feature)

        // 图片 / 视频是独立生成通道，不能被对话模型或智能分配覆盖
        when (feature.capability) {
            com.example.my.data.AiCapability.IMAGE_GEN -> {
                if (route.startsWith(com.example.my.data.AiFeature.ROUTE_PREFIX_API) && id != null) {
                    candidates.firstOrNull { it.id == id }?.let { return it }
                }
                return currentImageEndpoint ?: com.example.my.data.AiPresets.builtin
            }
            com.example.my.data.AiCapability.VIDEO_GEN -> {
                if (route.startsWith(com.example.my.data.AiFeature.ROUTE_PREFIX_API) && id != null) {
                    candidates.firstOrNull { it.id == id }?.let { return it }
                }
                return currentVideoEndpoint ?: com.example.my.data.AiPresets.builtin
            }
            com.example.my.data.AiCapability.VISION -> {
                if (route.startsWith(com.example.my.data.AiFeature.ROUTE_PREFIX_API) && id != null) {
                    candidates.firstOrNull { it.id == id }?.let { return it }
                }
                return candidates.firstOrNull { it.id == currentEndpointId }
                    ?: candidates.firstOrNull { it.ready }
                    ?: candidates.firstOrNull()
                    ?: builtinEndpoint
            }
            com.example.my.data.AiCapability.TEXT -> Unit
        }

        if (aiRoutingSmart) {
            return if (feature.heavy) currentEndpoint else builtinEndpoint
        }
        if (route == com.example.my.data.AiFeature.ROUTE_GLOBAL ||
            route == com.example.my.data.AiFeature.ROUTE_LOCAL ||
            route.startsWith(com.example.my.data.AiFeature.ROUTE_LOCAL_PREFIX)
        ) return currentEndpoint
        return chatEndpoints.firstOrNull { it.id == id } ?: currentEndpoint
    }

    /** 该功能当前的分配方式：local / smart / global / manual */
    fun aiFeatureRouteMode(feature: com.example.my.data.AiFeature): String {
        val route = aiRouteMap[feature.key] ?: feature.defaultRoute
        // 图片 / 视频没有「智能轻重」的概念，只看是否手动指定
        if (feature.capability != com.example.my.data.AiCapability.TEXT) {
            val id = route.removePrefix(com.example.my.data.AiFeature.ROUTE_PREFIX_API).toLongOrNull()
            return if (route.startsWith(com.example.my.data.AiFeature.ROUTE_PREFIX_API) &&
                id != null &&
                aiFeatureCandidates(feature).any { it.id == id }
            ) "manual" else "global"
        }
        if (aiRoutingSmart) {
            return if (aiFeatureUsesLocal(feature)) "local" else "smart"
        }
        return when {
            route == com.example.my.data.AiFeature.ROUTE_GLOBAL -> "global"
            // 已经选了本机、但本机未就绪或全局不允许时，明确标成待回退，
            // 不能让界面显示「本机」而实际请求走云端。
            route == com.example.my.data.AiFeature.ROUTE_LOCAL ||
                route.startsWith(com.example.my.data.AiFeature.ROUTE_LOCAL_PREFIX) ->
                if (aiFeatureUsesLocal(feature)) "local" else "local_fallback"
            else -> "manual"
        }
    }


    /** 添加自定义端点；按它具备的能力自动选中对应类型的第一条 */
    fun addAiEndpoint(ep: com.example.my.data.AiEndpoint) {
        val nextId = (aiEndpoints.maxOfOrNull { it.id } ?: 1L) + 1
        val newEp = ep.copy(id = nextId, builtin = false)
        aiEndpoints = aiEndpoints + newEp
        if (newEp.supports(com.example.my.data.AiCapability.TEXT) &&
            (chatEndpoints.size == 1 || currentEndpointId !in chatEndpoints.map { it.id })
        ) {
            currentEndpointId = newEp.id
            prefs.edit().putLong(KEY_CURRENT_ENDPOINT, newEp.id).apply()
        }
        if (newEp.supports(com.example.my.data.AiCapability.IMAGE_GEN) &&
            (imageEndpoints.size == 1 || currentImageEndpointId !in imageEndpoints.map { it.id })
        ) {
            currentImageEndpointId = newEp.id
            prefs.edit().putLong(KEY_CURRENT_IMAGE_ENDPOINT, newEp.id).apply()
        }
        if (newEp.supports(com.example.my.data.AiCapability.VIDEO_GEN) &&
            (videoEndpoints.size == 1 || currentVideoEndpointId !in videoEndpoints.map { it.id })
        ) {
            currentVideoEndpointId = newEp.id
            prefs.edit().putLong(KEY_CURRENT_VIDEO_ENDPOINT, newEp.id).apply()
        }
        saveEndpoints()
    }

    /** 修改已保存端点；用于模型管理里的编辑，不影响其它类型的当前选择 */
    fun updateAiEndpoint(ep: com.example.my.data.AiEndpoint) {
        if (aiEndpoints.none { it.id == ep.id }) return
        aiEndpoints = aiEndpoints.map { if (it.id == ep.id) ep.copy(builtin = false) else it }
        if (currentEndpointId !in chatEndpoints.map { it.id }) {
            currentEndpointId = chatEndpoints.firstOrNull()?.id ?: 0L
            prefs.edit().putLong(KEY_CURRENT_ENDPOINT, currentEndpointId).apply()
        }
        if (currentImageEndpointId !in imageEndpoints.map { it.id }) {
            currentImageEndpointId = imageEndpoints.firstOrNull()?.id ?: 0L
            prefs.edit().putLong(KEY_CURRENT_IMAGE_ENDPOINT, currentImageEndpointId).apply()
        }
        if (currentVideoEndpointId !in videoEndpoints.map { it.id }) {
            currentVideoEndpointId = videoEndpoints.firstOrNull()?.id ?: 0L
            prefs.edit().putLong(KEY_CURRENT_VIDEO_ENDPOINT, currentVideoEndpointId).apply()
        }
        saveEndpoints()
    }

    /** 删除自定义端点；删除当前选中项后回退到同类型列表里剩余的第一个 */
    fun deleteAiEndpoint(id: Long) {
        val removed = aiEndpoints.firstOrNull { it.id == id }
        aiEndpoints = aiEndpoints.filter { it.id != id }
        if (removed?.supports(com.example.my.data.AiCapability.TEXT) == true &&
            currentEndpointId == id
        ) {
            currentEndpointId = chatEndpoints.firstOrNull()?.id ?: 0L
            prefs.edit().putLong(KEY_CURRENT_ENDPOINT, currentEndpointId).apply()
        }
        if (removed?.supports(com.example.my.data.AiCapability.IMAGE_GEN) == true &&
            currentImageEndpointId == id
        ) {
            currentImageEndpointId = imageEndpoints.firstOrNull()?.id ?: 0L
            prefs.edit().putLong(KEY_CURRENT_IMAGE_ENDPOINT, currentImageEndpointId).apply()
        }
        if (removed?.supports(com.example.my.data.AiCapability.VIDEO_GEN) == true &&
            currentVideoEndpointId == id
        ) {
            currentVideoEndpointId = videoEndpoints.firstOrNull()?.id ?: 0L
            prefs.edit().putLong(KEY_CURRENT_VIDEO_ENDPOINT, currentVideoEndpointId).apply()
        }
        // 原来指向这个端点的单功能分配，回落为跟随全局
        val stale = "ep:$id"
        if (aiRouteMap.values.any { it == stale }) {
            val fallback = com.example.my.data.AiFeature.ROUTE_GLOBAL
            aiRouteMap = aiRouteMap.mapValues { if (it.value == stale) fallback else it.value }
            prefs.edit().apply {
                aiRouteMap.forEach { (key, value) -> putString(KEY_AI_ROUTE_PREFIX + key, value) }
            }.apply()
        }
        saveEndpoints()
    }

    /** 切换当前对话端点 */
    fun selectAiEndpoint(id: Long) {
        if (chatEndpoints.none { it.id == id }) return
        currentEndpointId = id
        prefs.edit().putLong(KEY_CURRENT_ENDPOINT, id).apply()
        scheduleAiConfigUpload()
        // 对话框底部是「总开关」：在这里换模型，顺手解除 AI 助手的单独指定，
        // 否则用户以为换了模型，对话却还被钉在旧端点上。
        val feature = com.example.my.data.AiFeature.ASSISTANT
        if (aiRouteMap[feature.key] != feature.defaultRoute) {
            aiRouteMap = aiRouteMap + (feature.key to feature.defaultRoute)
            prefs.edit().putString(KEY_AI_ROUTE_PREFIX + feature.key, feature.defaultRoute).apply()
        }
    }

    /** 切换当前图片生成端点 */
    fun selectImageEndpoint(id: Long) {
        if (imageEndpoints.none { it.id == id }) return
        currentImageEndpointId = id
        prefs.edit().putLong(KEY_CURRENT_IMAGE_ENDPOINT, id).apply()
        scheduleAiConfigUpload()
    }

    /** 切换当前视频生成端点 */
    fun selectVideoEndpoint(id: Long) {
        if (videoEndpoints.none { it.id == id }) return
        currentVideoEndpointId = id
        prefs.edit().putLong(KEY_CURRENT_VIDEO_ENDPOINT, id).apply()
        scheduleAiConfigUpload()
    }

    /** 保存自定义端点（简化版：只要名称、URL、key） */
    fun saveCustomEndpoint(name: String, url: String, key: String) {
        val ep = currentEndpoint
        val newEp = com.example.my.data.AiEndpoint(
            id = if (ep.builtin) (aiEndpoints.maxOfOrNull { it.id } ?: 1L) + 1L else ep.id,
            name = name.ifBlank { "自定义 API" },
            format = com.example.my.data.AiFormat.OPENAI,
            baseUrl = url,
            apiKey = key,
            model = "",  // 留空，AiApi 自动用默认模型名
            builtin = false
        )
        aiEndpoints = if (ep.builtin) aiEndpoints + newEp
        else aiEndpoints.map { if (it.id == ep.id) newEp else it }
        currentEndpointId = newEp.id
        prefs.edit().putLong(KEY_CURRENT_ENDPOINT, newEp.id).apply()
        saveEndpoints()
    }

    private fun saveEndpoints() {
        val custom = aiEndpoints.filter { !it.builtin }
        prefs.edit().putString(KEY_ENDPOINTS, com.example.my.data.AiPresets.encode(custom)).apply()
        scheduleAiConfigUpload()
    }

    // ---------- AI 端点云端保管（推理仍在手机直连） ----------

    private var aiCloudSyncJob: Job? = null

    /**
     * 把当前 AI 配置整包加密传到云端：端点 URL / Key / 模型名 + 当前选择 + OCR + 功能分配。
     * 服务器只保管，不代理请求；换机或重装后登录时由 [applyCloudAiConfig] 拉回。
     */
    fun aiConfigPayload(): JSONObject {
        val endpoints = JSONArray()
        aiEndpoints.filter { !it.builtin }.forEach { ep ->
            val caps = JSONArray()
            ep.effectiveCapabilities.forEach { caps.put(it.name) }
            endpoints.put(
                JSONObject()
                    .put("client_id", ep.id.toString())
                    .put("name", ep.name)
                    .put("format", ep.format.name)
                    .put("base_url", ep.baseUrl)
                    .put("api_key", ep.apiKey)
                    .put("model", ep.model)
                    .put("vision_model", ep.visionModel)
                    .put("image_model", ep.imageModel)
                    .put("video_model", ep.videoModel)
                    .put("capabilities", caps)
                    .put("context_tokens", ep.contextTokens)
                    .put("max_output_tokens", ep.maxOutputTokens)
                    .put("vision_context_tokens", ep.visionContextTokens)
                    .put("vision_max_output_tokens", ep.visionMaxOutputTokens)
                    .put("media_path", ep.mediaPath)
                    .put("video_path", ep.videoPath)
            )
        }
        val routes = JSONObject()
        aiRouteMap.forEach { (key, route) -> routes.put(key, route) }
        val state = JSONObject()
            .put("current_endpoint_id", currentEndpointId)
            .put("current_image_endpoint_id", currentImageEndpointId)
            .put("current_video_endpoint_id", currentVideoEndpointId)
            .put("run_mode", runMode)
            .put("memory_enabled", aiMemoryEnabled)
            .put("routing_smart", aiRoutingSmart)
            .put("routing_local", aiRoutingLocal)
            .put("ocr", JSONObject().apply {
                put("base_url", ocrEndpoint.baseUrl)
                put("api_key", ocrEndpoint.apiKey)
                put("model", ocrEndpoint.model)
            })
            .put("routes", routes)
        return JSONObject()
            .put("endpoints", endpoints)
            .put("state", state)
            .put("updated_at", System.currentTimeMillis())
    }

    /** 配置变更后合并成一次上传，避免连续操作打爆接口 */
    fun scheduleAiConfigUpload(delayMs: Long = 1_500L) {
        if (BackendApi.token == null || !CloudConfig.isEnabled(context)) return
        aiCloudSyncJob?.cancel()
        aiCloudSyncJob = scope.launch {
            delay(delayMs)
            runCatching { BackendApi.uploadAiConfig(aiConfigPayload()) }
        }
    }

    /**
     * 把云端配置合并回本机：同 id 端点以云端为准，云端没有的本地端点保留。
     * 没有 Key 的条目保持原样，等用户补填；不会因为恢复而清空本机配置。
     */
    fun applyCloudAiConfig(cloud: JSONObject?) {
        if (cloud == null) return
        val remote = cloud.optJSONArray("endpoints") ?: JSONArray()
        if (remote.length() > 0) {
            val merged = aiEndpoints.associateBy { it.id }.toMutableMap()
            for (i in 0 until remote.length()) {
                val o = remote.optJSONObject(i) ?: continue
                val id = o.optString("client_id").toLongOrNull() ?: continue
                val capsArr = o.optJSONArray("capabilities")
                val caps = buildSet {
                    if (capsArr != null) {
                        for (ci in 0 until capsArr.length()) {
                            com.example.my.data.AiCapability.of(capsArr.optString(ci))?.let { add(it) }
                        }
                    }
                }
                val kind = when {
                    com.example.my.data.AiCapability.IMAGE_GEN in caps -> com.example.my.data.AiModelKind.IMAGE
                    com.example.my.data.AiCapability.VIDEO_GEN in caps -> com.example.my.data.AiModelKind.VIDEO
                    else -> com.example.my.data.AiModelKind.CHAT
                }
                merged[id] = com.example.my.data.AiEndpoint(
                    id = id,
                    name = o.optString("name", "自定义 API"),
                    format = com.example.my.data.AiFormat.of(o.optString("format")),
                    baseUrl = o.optString("base_url", ""),
                    apiKey = o.optString("api_key", ""),
                    model = o.optString("model", ""),
                    visionModel = o.optString("vision_model", ""),
                    imageModel = o.optString("image_model", ""),
                    videoModel = o.optString("video_model", ""),
                    builtin = false,
                    kind = kind,
                    mediaPath = o.optString("media_path", ""),
                    videoPath = o.optString("video_path", ""),
                    capabilities = caps,
                    contextTokens = o.optInt("context_tokens", 200_000),
                    maxOutputTokens = o.optInt("max_output_tokens", 4096),
                    visionContextTokens = o.optInt("vision_context_tokens", 8_000),
                    visionMaxOutputTokens = o.optInt("vision_max_output_tokens", 1_024)
                )
            }
            aiEndpoints = merged.values.sortedBy { it.id }
            prefs.edit().putString(
                KEY_ENDPOINTS,
                com.example.my.data.AiPresets.encode(aiEndpoints.filter { !it.builtin })
            ).apply()
        }

        val state = cloud.optJSONObject("state") ?: return
        state.optLong("current_endpoint_id", currentEndpointId).takeIf { id ->
            chatEndpoints.any { it.id == id }
        }?.let { currentEndpointId = it }
        state.optLong("current_image_endpoint_id", currentImageEndpointId).takeIf { id ->
            imageEndpoints.any { it.id == id }
        }?.let { currentImageEndpointId = it }
        state.optLong("current_video_endpoint_id", currentVideoEndpointId).takeIf { id ->
            videoEndpoints.any { it.id == id }
        }?.let { currentVideoEndpointId = it }
        state.optInt("run_mode", runMode).takeIf { it in 1..3 }?.let { runMode = it }
        if (state.has("memory_enabled")) aiMemoryEnabled = state.optBoolean("memory_enabled", true)
        if (state.has("routing_smart")) {
            aiRoutingSmart = state.optBoolean("routing_smart", false)
            prefs.edit().putBoolean(KEY_AI_ROUTING_SMART, aiRoutingSmart).apply()
        }
        if (state.has("routing_local")) {
            aiRoutingLocal = state.optBoolean("routing_local", true)
            prefs.edit().putBoolean(KEY_AI_ROUTING_LOCAL, aiRoutingLocal).apply()
        }
        state.optJSONObject("ocr")?.let { ocr ->
            val baseUrl = ocr.optString("base_url", "")
            val apiKey = ocr.optString("api_key", "")
            val model = ocr.optString("model", "")
            if (baseUrl.isNotBlank() || apiKey.isNotBlank() || model.isNotBlank()) {
                ocrEndpoint = com.example.my.data.AiPresets.OcrEndpoint(baseUrl, apiKey, model)
                prefs.edit().putString(KEY_OCR_ENDPOINT, ocrEndpoint.encode()).apply()
            }
        }
        state.optJSONObject("routes")?.let { obj ->
            val allowed = com.example.my.data.AiFeature.values().associateBy { it.key }
            obj.keys().forEach { key ->
                val feature = allowed[key] ?: return@forEach
                val route = obj.optString(key)
                val safe = when {
                    route == com.example.my.data.AiFeature.ROUTE_GLOBAL -> route
                    route == com.example.my.data.AiFeature.ROUTE_LOCAL && feature.localCapable -> route
                    route.startsWith(com.example.my.data.AiFeature.ROUTE_LOCAL_PREFIX) &&
                        feature.localCapable -> route
                    route.startsWith(com.example.my.data.AiFeature.ROUTE_PREFIX_API) -> route
                    else -> com.example.my.data.AiFeature.ROUTE_GLOBAL
                }
                aiRouteMap = aiRouteMap + (feature.key to safe)
                prefs.edit().putString(KEY_AI_ROUTE_PREFIX + feature.key, safe).apply()
            }
        }
        prefs.edit().apply {
            putLong(KEY_CURRENT_ENDPOINT, currentEndpointId)
            putLong(KEY_CURRENT_IMAGE_ENDPOINT, currentImageEndpointId)
            putLong(KEY_CURRENT_VIDEO_ENDPOINT, currentVideoEndpointId)
        }.apply()
    }

    /** 本地大模型链路状态：引擎未内置 / 模型未导入 / 已就绪 */
    var localLlmState by mutableStateOf(LocalLlm.state(context))
        private set

    /** 重新探测引擎与模型（设置页打开、导入/删除模型后调用） */
    fun refreshLocalLlmState() {
        localLlmState = LocalLlm.state(context)
    }

    /** 从系统目录选择器导入已解压的模型文件夹（下载通道之外的手工通道） */
    suspend fun importLocalModel(treeUri: Uri, onProgress: (Int) -> Unit = {}): Boolean {
        val ok = runCatching { LocalLlm.importFromTree(context, treeUri, onProgress) }.getOrDefault(false)
        refreshLocalLlmState()
        return ok
    }

    /** 开始下载目录里的某个模型（应用级协程，离开设置页也不断线；完成自动启用） */
    fun startModelDownload(spec: com.example.my.data.ai.LocalModelSpec) {
        LocalLlm.startDownload(context, spec)
    }

    /** 取消进行中的下载 */
    fun cancelModelDownload(id: String) {
        LocalLlm.cancelDownload(id)
    }

    /** 切换当前使用的本地模型 */
    fun setActiveModel(id: String) {
        LocalLlm.setActive(context, id)
        refreshLocalLlmState()
    }

    /** 删除某个已下载的模型 */
    fun deleteLocalModel(id: String) {
        LocalLlm.deleteModel(context, id)
        // 原来指定这个本地模型的功能，改回「跟随当前本机模型」；
        // 一个都不剩时回落到全局云端，避免界面一直显示一个不存在的模型。
        val stale = com.example.my.data.AiFeature.localRoute(id)
        if (aiRouteMap.values.any { it == stale }) {
            val fallback = if (LocalLlm.downloadedSpecs(context).isNotEmpty())
                com.example.my.data.AiFeature.ROUTE_LOCAL
            else com.example.my.data.AiFeature.ROUTE_GLOBAL
            aiRouteMap = aiRouteMap.mapValues { (_, route) ->
                if (route == stale) fallback else route
            }
            prefs.edit().apply {
                aiRouteMap.forEach { (key, route) ->
                    putString(KEY_AI_ROUTE_PREFIX + key, route)
                }
            }.apply()
        }
        refreshLocalLlmState()
    }

    // ---------- 「我的」页背景（预设方案，用户可自定义，持久化重启不丢） ----------
    var profileBgIndex by mutableStateOf(prefs.getInt(KEY_PROFILE_BG, 0))
        private set

    /** 切换「我的」页背景主题并持久化 */
    fun updateProfileBgIndex(index: Int) {
        profileBgIndex = index
        prefs.edit().putInt(KEY_PROFILE_BG, index).apply()
    }

    // ---------- 业务数据 ----------
    var guides by mutableStateOf(MockData.guides)
    // 足迹/行程：本地 SQLite 为权威数据源（启动时从 my_trace.db 加载，初始为空不注入演示数据）
    var routes by mutableStateOf(emptyList<Route>())
    var footprints by mutableStateOf(emptyList<Footprint>())
    /**
     * 本机足迹的内存同步态（与 footprints 同源，均来自/落盘 SQLite）。
     * 保留用于兼容历史调用点；权威持久化已迁移到 OfflineCache（SQLite）。
     */
    var userFootprints by mutableStateOf(emptyList<Footprint>())
        private set

    /** 回收站：软删除的足迹（保留照片），3 天内可在设置里恢复，超时自动清除 */
    var trashedFootprints by mutableStateOf(emptyList<Persistence.TrashedEntry>())
        private set

    var user by mutableStateOf(MockData.user)

    // ---------- 微足迹（公开池，与个人 Footprint 严格分离） ----------
    /** 当前 feed 内容；示例池 + 本人发布，互动状态由本地快照叠加 */
    var microPosts by mutableStateOf(emptyList<MicroPost>())
        private set
    /** 0=关注 1=推荐 2=同城 3=我的 */
    var microFeedTab by mutableIntStateOf(1)
    var microFeedQuery by mutableStateOf("")
    var microFeedTagFilter by mutableStateOf<String?>(null)
    var microFeedSearchOpen by mutableStateOf(false)
    /** 弹层目标：null 表示未打开 */
    var microFeedCommentTarget by mutableStateOf<String?>(null)
    var microFeedShareTarget by mutableStateOf<String?>(null)
    var microFeedAuthorTarget by mutableStateOf<Int?>(null)
    /** 从「我的」页点发布时自增一次，feed 页组合后消费 */
    var microFeedPublishTick by mutableIntStateOf(0)
        private set

    private var microSnapshot by mutableStateOf(MicroFeedStore.Snapshot())

    // ---------- 微足迹远端（独立服务 microfeed-backend） ----------
    /** 服务是否可用：可用时四个分段用服务端内容，不可用自动回落到本地示例池 */
    var microFeedOnline by mutableStateOf(false)
        private set
    var microFeedLoading by mutableStateOf(false)
        private set
    /** 服务端返回的当前分段内容（按固定顺序：关注/推荐/同城） */
    private var remoteMicroPosts by mutableStateOf(emptyList<MicroPost>())
    private var remoteCursor: Int? = null
    private var remoteTabKey: String = ""
    /** 远端帖 id 集合：互动时据此判断要不要同步到服务端 */
    private val remotePostIds = mutableSetOf<String>()
    /** 本地发布 id → 服务端 id，撤回时用来删掉远端那条 */
    private val publishedRemoteIds = mutableMapOf<String, String>()
    private var profilePushed = false
    private val viewedRemote = mutableSetOf<String>()

    /** 评论面板是否要自动聚焦输入框：点底部评论框=true（抖音同款），点右侧评论数=false */
    var microCommentFocus by mutableStateOf(false)
        private set

    fun openMicroPublish() { microFeedPublishTick++ }
    fun openMicroSearch() { microFeedSearchOpen = true }
    fun closeMicroSearch() { microFeedSearchOpen = false; microFeedQuery = "" }
    fun openMicroComments(id: String, focusInput: Boolean = false) {
        microFeedCommentTarget = id
        microCommentFocus = focusInput
    }
    fun closeMicroComments() {
        microFeedCommentTarget = null
        microCommentFocus = false
    }
    fun openMicroShare(id: String) { microFeedShareTarget = id }
    fun closeMicroShare() { microFeedShareTarget = null }
    fun openMicroAuthor(id: Int) { microFeedAuthorTarget = id }
    fun closeMicroAuthor() { microFeedAuthorTarget = null }

    /** 点话题标签：切回推荐流并只显示该话题 */
    fun filterMicroTag(tag: String) {
        microFeedTagFilter = tag
        microFeedTab = 1
    }

    private fun persistMicro() {
        scope.launch(Dispatchers.IO) {
            runCatching { MicroFeedStore.save(context, microSnapshot) }
        }
    }

    /** 每次互动都从原始池重建，避免重复叠加导致点赞数越点越大 */
    private fun refreshMicroFeed() {
        microPosts = (microSnapshot.published + MicroFeedSeed.posts).map { p ->
            val localComments = microSnapshot.comments[p.id]
            p.copy(
                liked = p.id in microSnapshot.liked,
                favored = p.id in microSnapshot.favored,
                followed = p.author.id in microSnapshot.followed,
                comments = if (localComments == null) {
                    p.comments
                } else {
                    (p.comments + localComments).distinctBy { it.id }
                },
                baseShares = p.baseShares + if (p.id in microSnapshot.shared) 1 else 0
            )
        }
    }

    /**
     * 远端帖子的本地互动叠层。
     *
     * 服务端返回的 liked/favored 已经是最新个人态，拉取时先“吸收”进 snapshot；
     * 之后用户再点赞/收藏/评论，都只改 snapshot，再由这里统一叠加到 UI，
     * 避免远端列表原地不动、只有本地示例池会变化的割裂。
     */
    private fun applyMicroLocalState(p: MicroPost): MicroPost {
        val localComments = microSnapshot.comments[p.id].orEmpty()
        val mergedComments = if (localComments.isEmpty()) {
            p.comments
        } else {
            (p.comments + localComments).distinctBy { it.id }
        }
        return p.copy(
            liked = p.id in microSnapshot.liked,
            favored = p.id in microSnapshot.favored,
            followed = p.author.id in microSnapshot.followed,
            comments = mergedComments,
            baseShares = p.baseShares + if (p.id in microSnapshot.shared) 1 else 0
        )
    }

    /** 把服务端初始个人态并入本地快照，防止“服务端已点赞、本地显示未点赞” */
    private fun absorbRemoteState(posts: List<MicroPost>) {
        if (posts.isEmpty()) return
        microSnapshot = microSnapshot.copy(
            liked = microSnapshot.liked + posts.filter { it.liked }.map { it.id },
            favored = microSnapshot.favored + posts.filter { it.favored }.map { it.id },
            followed = microSnapshot.followed + posts.filter { it.followed }.map { it.author.id }
        )
    }

    // ---------- 远端（独立服务 microfeed-backend） ----------

    private fun tabSlug(tab: Int) = when (tab) {
        0 -> "following"
        2 -> "nearby"
        3 -> "mine"
        else -> "recommend"
    }

    /** 拉当前分段的远端内容；force=true 忽略缓存强制重拉 */
    fun refreshMicroFeedRemote(force: Boolean = false) {
        if (microFeedTab == 3) {
            // 「我的」是本地库视图，不依赖社区服务；顶栏也回到本地状态
            microFeedOnline = false
            refreshMyMicroFromDb()
            return
        }
        if (!MicroFeedApi.ready()) {
            microFeedOnline = false
            return
        }
        val key = "${microFeedTab}|${microFeedTagFilter}|${if (microFeedTab == 2) currentMicroCity() else ""}|${microFeedQuery}"
        if (!force && microFeedOnline && key == remoteTabKey && remoteMicroPosts.isNotEmpty()) return
        val tab = microFeedTab
        microFeedLoading = true
        scope.launch {
            val page = withContext(Dispatchers.IO) {
                MicroFeedApi.feed(
                    context,
                    tab = tabSlug(tab),
                    city = if (tab == 2) currentMicroCity() else null,
                    tag = microFeedTagFilter,
                    q = microFeedQuery.takeIf { it.isNotBlank() },
                    limit = 8
                )
            }
            microFeedLoading = false
            if (page == null) {
                // 服务没起/没网/未登录：保持本地池可用，不弹错
                microFeedOnline = false
                return@launch
            }
            microFeedOnline = true
            remoteTabKey = key
            absorbRemoteState(page.items)
            remoteMicroPosts = page.items
            remoteCursor = page.nextCursor
            remotePostIds.clear()
            remotePostIds += page.items.map { it.id }
            pushMicroProfileOnce()
        }
    }

    /** 滑到接近底部时补一页 */
    fun loadMoreMicroRemote() {
        val cursor = remoteCursor ?: return
        if (microFeedLoading || !microFeedOnline || microFeedTab == 3) return
        val tab = microFeedTab
        microFeedLoading = true
        scope.launch {
            val page = withContext(Dispatchers.IO) {
                MicroFeedApi.feed(
                    context,
                    tab = tabSlug(tab),
                    city = if (tab == 2) currentMicroCity() else null,
                    tag = microFeedTagFilter,
                    q = microFeedQuery.takeIf { it.isNotBlank() },
                    cursor = cursor,
                    limit = 8
                )
            }
            microFeedLoading = false
            if (page == null) {
                microFeedOnline = false
                return@launch
            }
            // 去重：分页边界可能因为新内容插入而重复
            val known = remoteMicroPosts.mapTo(HashSet()) { it.id }
            absorbRemoteState(page.items)
            remoteMicroPosts = remoteMicroPosts + page.items.filterNot { it.id in known }
            remotePostIds += page.items.map { it.id }
            remoteCursor = page.nextCursor
        }
    }

    /** 主后端是昵称/头像的权威来源：第一次连上微足迹服务时把展示资料推过去 */
    private fun pushMicroProfileOnce() {
        if (profilePushed) return
        profilePushed = true
        scope.launch(Dispatchers.IO) {
            MicroFeedApi.updateMe(context, user.nickname, user.avatarUrl, user.signature)
        }
    }

    private fun isRemotePost(id: String) = id in remotePostIds

    /** 搜索命中远端帖子时，把它插到列表最前面并跳过去 */
    fun focusMicroPost(id: String) {
        scope.launch {
            val post = remoteMicroPosts.firstOrNull { it.id == id }
                ?: withContext(Dispatchers.IO) { MicroFeedApi.post(context, id) }
            if (post != null) {
                absorbRemoteState(listOf(post))
                remoteMicroPosts = listOf(post) + remoteMicroPosts.filterNot { it.id == post.id }
                remotePostIds += post.id
                microFeedOnline = true
            }
        }
    }

    fun remoteMicroFeed(): List<MicroPost> = remoteMicroPosts.map(::applyMicroLocalState)

    /** 弹层/作者页按 id 取当前这条：本地池、远端当前页、「我的」真实足迹都要能查到 */
    fun microPostById(id: String): MicroPost? =
        (microPosts + remoteMicroFeed() + myFootprintPosts()).firstOrNull { it.id == id }

    /** 作者页用：作者可能是本地示例/远端用户/本人，三个池子一起查 */
    fun microPostsForAuthor(authorId: Int): List<MicroPost> =
        (microPosts + remoteMicroFeed() + myFootprintPosts())
            .filter { it.author.id == authorId }
            .distinctBy { it.id }

    fun isMicroFollowing(authorId: Int): Boolean = authorId in microSnapshot.followed

    /** 页面停留即记一次浏览（同一帖只报一次，服务端也会去重） */
    fun markMicroViewed(id: String) {
        if (!isRemotePost(id) || !viewedRemote.add(id)) return
        scope.launch(Dispatchers.IO) { MicroFeedApi.view(context, id) }
    }

    /**
     * 打开评论区时按需拉一次远端评论。
     * 本地新评论先上屏，服务端历史评论回来后再合并，避免两套评论各看各的。
     */
    fun loadMicroComments(id: String) {
        if (!isRemotePost(id)) return
        scope.launch {
            val fetched = withContext(Dispatchers.IO) { MicroFeedApi.comments(context, id) }
            if (fetched == null) return@launch
            val local = microSnapshot.comments[id].orEmpty()
            val merged = (fetched + local).distinctBy { it.id }
            microSnapshot = microSnapshot.copy(
                comments = microSnapshot.comments + (id to merged)
            )
            remoteMicroPosts = remoteMicroPosts.map { p ->
                if (p.id == id) p.copy(commentsLoaded = true) else p
            }
            refreshMicroFeed(); persistMicro()
        }
    }

    fun toggleMicroLike(id: String) {
        microSnapshot = microSnapshot.copy(
            liked = if (id in microSnapshot.liked) microSnapshot.liked - id else microSnapshot.liked + id
        )
        refreshMicroFeed(); persistMicro()
        // 远端帖再同步一次；失败只影响服务端计数，本地已经立刻反馈过
        if (isRemotePost(id)) scope.launch(Dispatchers.IO) { MicroFeedApi.like(context, id) }
    }

    fun toggleMicroFav(id: String) {
        val on = id !in microSnapshot.favored
        microSnapshot = microSnapshot.copy(
            favored = if (on) microSnapshot.favored + id else microSnapshot.favored - id
        )
        refreshMicroFeed(); persistMicro()
        if (isRemotePost(id)) scope.launch(Dispatchers.IO) { MicroFeedApi.favorite(context, id) }
        toast(if (on) tr("已收藏", "Saved") else tr("已取消收藏", "Removed"))
    }

    /** 离线也能关注：关注集合只作用于本地公开池，不冒充云端关注 */
    fun toggleMicroFollow(authorId: Int) {
        val on = authorId !in microSnapshot.followed
        microSnapshot = microSnapshot.copy(
            followed = if (on) microSnapshot.followed + authorId else microSnapshot.followed - authorId
        )
        refreshMicroFeed(); persistMicro()
        if (microFeedOnline && authorId != 0) {
            scope.launch(Dispatchers.IO) { MicroFeedApi.follow(context, authorId) }
        }
        toast(if (on) tr("已关注", "Following") else tr("已取消关注", "Unfollowed"))
    }

    /** 不感兴趣：只在本地公开池隐藏，不影响作者与其他内容 */
    fun hideMicroPost(id: String) {
        microSnapshot = microSnapshot.copy(hidden = microSnapshot.hidden + id)
        refreshMicroFeed(); persistMicro()
        if (isRemotePost(id)) {
            // 远端也记一笔，下次推荐流直接由服务端剔除
            remoteMicroPosts = remoteMicroPosts.filterNot { it.id == id }
            scope.launch(Dispatchers.IO) { MicroFeedApi.hide(context, id) }
        }
        toast(tr("已减少此类推荐", "We'll show fewer posts like this"))
    }

    fun markMicroShared(id: String) {
        if (id in microSnapshot.shared) return
        microSnapshot = microSnapshot.copy(shared = microSnapshot.shared + id)
        refreshMicroFeed(); persistMicro()
        if (isRemotePost(id)) scope.launch(Dispatchers.IO) { MicroFeedApi.share(context, id) }
    }

    fun addMicroComment(id: String, text: String, replyTo: String? = null) {
        val body = text.trim().take(300)
        if (body.isBlank()) return
        val c = MicroComment(
            id = "c_${System.nanoTime()}",
            authorName = user.nickname,
            text = body,
            time = tr("刚刚", "just now"),
            authorId = 0,
            replyTo = replyTo
        )
        microSnapshot = microSnapshot.copy(
            comments = microSnapshot.comments + (id to (microSnapshot.comments[id].orEmpty() + c))
        )
        refreshMicroFeed(); persistMicro()
        // 远端帖：本地先上屏，服务端成功后把临时 id 换成真 id（失败就保留本地这条）
        if (isRemotePost(id)) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    MicroFeedApi.addComment(context, id, body, replyTo)
                }
                if (saved != null) {
                    val current = microSnapshot.comments[id].orEmpty()
                    microSnapshot = microSnapshot.copy(
                        comments = microSnapshot.comments + (id to current.map { if (it.id == c.id) saved else it })
                    )
                    refreshMicroFeed(); persistMicro()
                }
            }
        }
    }

    /** 把一条私人足迹发布到公开池；媒体路径只是复制引用，不搬文件、不改 Footprint */
    fun publishFootprintToFeed(
        footprint: Footprint,
        caption: String,
        tags: List<String>,
        place: String = "",
        city: String = ""
    ): String {
        val images = (footprint.photoPaths + listOfNotNull(
            footprint.startPhotoPath,
            footprint.endPhotoPath
        ) + footprint.midPhotoPaths)
            .filter { it.isNotBlank() }.distinct()
        val post = MicroPost(
            id = "mine_${footprint.id}_${System.currentTimeMillis()}",
            author = MicroAuthor(
                id = 0,
                name = user.nickname,
                avatarText = user.nickname.take(1),
                avatarPath = user.avatarUrl,
                bio = user.signature,
                followers = user.followers
            ),
            place = place.ifBlank { footprint.name.ifBlank { footprint.city } },
            city = city.ifBlank { footprint.city },
            caption = caption.ifBlank { footprint.note },
            tags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(6),
            imagePaths = images,
            videoPath = footprint.allVideoPaths.firstOrNull(),
            bgmTitle = footprint.bgmTitle,
            bgmPath = footprint.bgmPath,
            bgmArtist = footprint.bgmArtist,
            isMine = true,
            footprintId = footprint.id,
            publishedAt = System.currentTimeMillis()
        )
        microSnapshot = microSnapshot.copy(published = listOf(post) + microSnapshot.published)
        refreshMicroFeed(); persistMicro()
        toast(tr("已发布到微足迹", "Published to Micro Footprints"))

        // 同步到独立服务：媒体先上传、再提交帖子。任何一步失败都只影响「别人能不能看到」，
        // 本地这条已经落盘，自己的「我的」页照常可见。
        if (MicroFeedApi.ready()) {
            scope.launch {
                val videoExt = setOf("mp4", "mov", "webm")
                val imageFiles = images.map(::File).filter { it.isFile && it.extension.lowercase() !in videoExt }
                val videoFile = footprint.allVideoPaths.firstOrNull()?.let(::File)?.takeIf { it.isFile }
                val bgmFile = footprint.bgmPath?.let(::File)?.takeIf { it.isFile }
                val saved = withContext(Dispatchers.IO) {
                    MicroFeedApi.publish(
                        context = context,
                        caption = post.caption,
                        place = post.place,
                        city = post.city,
                        tags = post.tags,
                        imageFiles = imageFiles,
                        videoFile = videoFile,
                        bgmFile = bgmFile,
                        bgmTitle = post.bgmTitle,
                        bgmArtist = post.bgmArtist,
                        lat = footprint.lat,
                        lon = footprint.lon
                    )
                }
                if (saved != null) {
                    publishedRemoteIds[post.id] = saved.id
                    microSnapshot = microSnapshot.copy(
                        remoteIds = microSnapshot.remoteIds + (post.id to saved.id)
                    )
                    absorbRemoteState(listOf(saved))
                    remotePostIds += saved.id
                    remoteMicroPosts = listOf(saved) + remoteMicroPosts.filterNot { it.id == saved.id }
                    microFeedOnline = true
                    persistMicro()
                    toast(tr("已同步到微足迹社区", "Synced to the community feed"))
                }
            }
        }
        return post.id
    }

    /** 撤回本人发布；示例帖永远撤不掉 */
    fun unpublishMicroPost(id: String) {
        val localId = microSnapshot.remoteIds.entries.firstOrNull { it.value == id }?.key
        val effectiveId = localId ?: id
        val remoteId = publishedRemoteIds.remove(effectiveId)
            ?: microSnapshot.remoteIds[effectiveId]
            ?: id.takeIf { isRemotePost(it) }

        microSnapshot = microSnapshot.copy(
            published = microSnapshot.published.filterNot { it.id == effectiveId },
            remoteIds = microSnapshot.remoteIds - effectiveId,
            liked = microSnapshot.liked - effectiveId - id,
            favored = microSnapshot.favored - effectiveId - id,
            shared = microSnapshot.shared - effectiveId - id,
            comments = microSnapshot.comments - effectiveId - id
        )
        remoteMicroPosts = remoteMicroPosts.filterNot { it.id == id || it.id == remoteId }
        remotePostIds -= id
        remoteId?.let { remotePostIds -= it }
        refreshMicroFeed(); persistMicro()
        if (remoteId != null) {
            scope.launch(Dispatchers.IO) { MicroFeedApi.deletePost(context, remoteId) }
        }
        toast(tr("已从微足迹撤回", "Removed from Micro Footprints"))
    }

    /**
     * 「我的」Tab 直接展示本机真实足迹（Room 的 [footprints]），不再要求先手动发布。
     * 若这条足迹曾发布到公开池，用发布时写的自定义文案/话题覆盖，媒体仍以足迹现状为准，
     * 这样「改了照片/文案」不会在 feed 里留下一份过期快照。
     */
    private fun myFootprintPosts(): List<MicroPost> {
        val me = MicroAuthor(
            id = 0,
            name = user.nickname,
            avatarText = user.nickname.take(1),
            avatarPath = user.avatarUrl,
            bio = user.signature,
            followers = user.followers
        )
        return footprints.asReversed().map { fp ->
            val published = microSnapshot.published.firstOrNull { it.footprintId == fp.id }
            val images = (fp.photoPaths + listOfNotNull(
                fp.startPhotoPath,
                fp.endPhotoPath
            ) + fp.midPhotoPaths)
                .filter { it.isNotBlank() }.distinct()
            val id = published?.id ?: "mine_fp_${fp.id}"
            MicroPost(
                id = id,
                author = me,
                place = fp.name.ifBlank { fp.city },
                city = fp.city,
                caption = published?.caption?.takeIf { it.isNotBlank() } ?: fp.note,
                tags = published?.tags?.takeIf { it.isNotEmpty() }
                    ?: listOfNotNull(fp.city.takeIf { it.isNotBlank() }, fp.name.takeIf { it.isNotBlank() }).distinct(),
                imagePaths = images,
                videoPath = fp.allVideoPaths.firstOrNull(),
                bgmTitle = fp.bgmTitle,
                bgmPath = fp.bgmPath,
                bgmArtist = fp.bgmArtist,
                liked = id in microSnapshot.liked,
                favored = id in microSnapshot.favored,
                comments = microSnapshot.comments[id].orEmpty(),
                isMine = true,
                footprintId = fp.id,
                publishedAt = published?.publishedAt ?: (fp.dateMillis ?: 0L)
            )
        }
    }

    /**
     * 进入「我的微足迹」时调用：**重新读一次和「我的足迹」共用的本地库**。
     *
     * 「我的微足迹」不是第二套数据，只是同一批足迹的抖音式展示：权威数据在
     * OfflineCache（my_trace.db）里，云端回灌/后台同步也都写这一份。所以进分段时
     * 重新 load 一次即可看到刚恢复的云端足迹，再顺手请求一次后台同步，把
     * 「云端有、本地还没落库」的纪录补齐。不复制媒体、不新建存储。
     */
    fun refreshMyMicroFromDb() {
        scope.launch(Dispatchers.IO) {
            val db = runCatching { OfflineCache.loadFootprints(context) }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) {
                footprints = db
                userFootprints = db
            }
        }
        // 未开启云端 / 未登录时 requestSync 内部直接返回，是安全空操作
        CloudSync.requestSync(context, 0L)
    }

    /** 当前 tab / 搜索 / 话题共同决定的 feed 内容 */
    fun visibleMicroPosts(): List<MicroPost> {
        // 远端可用时，「关注 / 推荐 / 同城」以服务端为准（tab、搜索、话题、分页都由服务端做，
        // 本地不再二次过滤，避免两边口径不一致）；服务不可用才回落到本地示例池。
        // 「我的」永远读本地库（与「我的足迹」同一份数据），不走远端。
        if (microFeedTab != 3 && microFeedOnline) {
            return remoteMicroPosts
                .filterNot { it.id in microSnapshot.hidden }
                .map(::applyMicroLocalState)
        }
        var list = microPosts.filterNot { it.id in microSnapshot.hidden }
        when (microFeedTab) {
            0 -> list = list.filter { it.isMine || it.author.id in microSnapshot.followed }
            2 -> {
                val city = currentMicroCity()
                list = list.filter { it.city == city || it.place.contains(city) }
            }
            3 -> list = myFootprintPosts()
        }
        microFeedTagFilter?.let { tag ->
            list = list.filter { tag in it.tags || it.caption.contains(tag) || it.place.contains(tag) }
        }
        val q = microFeedQuery.trim()
        if (q.isNotBlank()) {
            list = list.filter {
                it.caption.contains(q, true) || it.place.contains(q, true) ||
                    it.author.name.contains(q, true) || it.tags.any { t -> t.contains(q, true) }
            }
        }
        return list
    }

    /** 同城口径：取最近一条足迹的城市；没有足迹时给一个明确的默认城市 */
    fun currentMicroCity(): String =
        footprints.asReversed().firstOrNull { it.city.isNotBlank() }?.city ?: "重庆"

    // ---------- 设置与通知 ----------
    var settings by mutableStateOf(MockData.defaultSettings)
    var notifications by mutableStateOf(MockData.notifications)

    /** 站内私信未读数：消息中心只统计私信 */
    var dmUnread by mutableStateOf(0)
        private set

    fun updateDmUnread(count: Int) {
        dmUnread = count.coerceAtLeast(0)
    }

    /** AI 语言偏好（zh / en）。界面翻译尚未全量上线，仅作为 AI 回复语言与指令识别的轻量标记 */
    var aiLanguage by mutableStateOf("zh")

    /**
     * 界面语言（zh / en）。真正的值以 [Lang] 里的 Compose 状态为准，
     * 这里持有同一份并负责持久化 —— 切换后整个界面重组，无需重启 Activity。
     *
     * 首次启动且用户没手动选过时，跟随系统语言：系统是英文就默认英文，
     * 中文（或其它语言）默认中文。
     */
    var language by mutableStateOf(
        prefs.getString(KEY_LANGUAGE, null)
            ?: if (java.util.Locale.getDefault().language == "en") "en" else "zh"
    )
        private set

    fun updateLanguage(code: String) {
        Lang.apply(code)
        language = Lang.code
        prefs.edit().putString(KEY_LANGUAGE, Lang.code).apply()
        // 界面语言与 AI 回复语言保持一致，否则会出现「界面英文、AI 回中文」
        aiLanguage = Lang.code
    }

    /**
     * 新建足迹的拍照入口：false = 旧版来源面板（相册 / 系统相机），true = 抖音式取景页。
     * 用户点「切换新版 / 切回旧版」后长期保留，避免每次新建足迹都要重选。
     */
    var uploadStudioCapture by mutableStateOf(prefs.getBoolean(KEY_UPLOAD_STUDIO_CAPTURE, false))
        private set

    fun updateUploadStudioCapture(enabled: Boolean) {
        uploadStudioCapture = enabled
        prefs.edit().putBoolean(KEY_UPLOAD_STUDIO_CAPTURE, enabled).apply()
    }

    // ---------- 触感反馈（默认跟随系统：静音 / 系统关闭触感时不震） ----------
    var hapticMode by mutableStateOf(prefs.getString(KEY_HAPTIC_MODE, null) ?: Haptics.FOLLOW)
        private set

    fun updateHapticMode(mode: String) {
        hapticMode = when (mode) {
            Haptics.ON -> Haptics.ON
            Haptics.OFF -> Haptics.OFF
            else -> Haptics.FOLLOW
        }
        Haptics.apply(hapticMode)
        prefs.edit().putString(KEY_HAPTIC_MODE, hapticMode).apply()
    }

    /** 用户自填的高德 Web 服务 Key；为空表示使用内置 Key（启动时从持久化恢复并注入 AmapApi） */
    var amapKey by mutableStateOf("")
        private set


    // ---------- 天气（发现页顶部 + 天气页共用，实时同步） ----------
    var weatherCity by mutableStateOf("重庆")
        private set
    var weatherNow by mutableStateOf<WeatherSnapshot?>(null)
        private set
    var weatherLoading by mutableStateOf(false)
        private set
    var weatherError by mutableStateOf<String?>(null)
        private set

    private var weatherLat = 29.5630
    private var weatherLon = 106.5516
    private var weatherJob: Job? = null
    private var weatherFetchedAt = 0L

    val weatherLatitude: Double get() = weatherLat
    val weatherLongitude: Double get() = weatherLon

    /** 评论自增主键（新增评论/回复用） */
    private var commentSeq = 1_000_000

    val unreadCount: Int
        get() = notifications.count { !it.read } + dmUnread

    // ---------- 持久化状态（启动时合并） ----------
    // 只读暴露给发现页做个性化排序，写入仍只能在 AppState 内部
    var likedIds: Set<Int> = emptySet()
        private set
    var favoredIds: Set<Int> = emptySet()
        private set
    private var routePublic: Map<Int, Boolean> = emptyMap()

    init {
        // 云端开关先落内存：下面的 bootstrapAuth / 网络出口都依赖它，晚一步就会读到默认 false
        CloudConfig.init(context)
        // 微足迹独立服务的地址（用户可在 App 内覆盖，存自己的 SP）
        MicroFeedApi.init(context)
        // 私信到达事件：后台服务已经在通知栏提醒过，这里只负责更新 App 内红点
        scope.launch {
            com.example.my.data.messaging.DmEvents.events.collect { e ->
                val inOpenChat = com.example.my.data.messaging.AppForegroundState.isForeground &&
                    e.conversationId == com.example.my.data.messaging.AppForegroundState.openConversationId
                if (!inOpenChat) {
                    dmUnread = maxOf(dmUnread + 1, e.totalUnread)
                }
            }
        }
        CloudSync.onResult = { r ->
            cloudSyncing = false
            cloudLastSyncAt = r.at
            cloudSyncError = if (r.ok) null else r.message
            // 后台同步可能刚回拉了新足迹，落一次媒体并刷新内存态，页面不必等重启
            scope.launch { repairLocalMediaAndReload() }
        }
        // 冷启动先把持久化的语言同步给 Lang：首帧就是正确语言，不会先闪一下中文再切换
        Lang.apply(language)
        aiLanguage = Lang.code
        // 触感反馈档位同样要在首屏生效
        Haptics.apply(hapticMode)
        // 加载最后查看的攻略 ID
        lastViewedGuideId = prefs.getInt(KEY_LAST_GUIDE, -1).takeIf { it > 0 }
        scope.launch {
            val d: PersistedData = Persistence.loadAll(context)
            // 冷启动即注入用户自填的 Key，避免本次会话前几次搜索仍走失效的内置 Key
            amapKey = d.amapKey
            AmapApi.userKey = d.amapKey.trim().takeIf { it.isNotBlank() }
            settings = UserSettings(
                nickname = d.nickname ?: settings.nickname,
                signature = d.signature ?: settings.signature,
                defaultPublic = d.defaultPublic ?: settings.defaultPublic,
                notifEnabled = d.notifEnabled ?: settings.notifEnabled,
                shareEnabled = d.shareEnabled ?: settings.shareEnabled,
                liquidGlass = d.liquidGlass ?: settings.liquidGlass
            )
            // 冷启动即把玻璃开关注入渲染层：首帧就是用户上次选择的观感，不会先亮一下玻璃再关掉
            com.example.my.ui.components.LiquidGlass.enabled = settings.liquidGlass
            likedIds = d.likedIds
            favoredIds = d.favoredIds
            routePublic = d.routePublic

            guides = guides.map { g ->
                g.copy(
                    liked = g.id in likedIds,
                    favored = g.id in favoredIds,
                    commentList = g.commentList.mapIndexed { i, c -> c.normalized(g.id, i) }
                )
            }
            // 本地 SQLite 优先：足迹/行程启动即从 my_trace.db 读取（离线数据不丢、不注入演示数据）
            val dbRoutes = OfflineCache.loadRoutes(context)
            if (dbRoutes != null) {
                routes = dbRoutes
            }
            routes = routes.map { r -> routePublic[r.id]?.let { r.copy(isPublic = it) } ?: r }
            val dbFootprints = OfflineCache.loadFootprints(context)
            footprints = when {
                // 1) 已有 SQLite 数据：本地权威
                dbFootprints != null -> dbFootprints
                // 2) 老版本 DataStore 迁移：一次性迁入 SQLite（兼容升级用户不丢数据）
                d.extraFootprints.isNotEmpty() -> {
                    OfflineCache.saveFootprints(context, d.extraFootprints)
                    d.extraFootprints
                }
                // 3) 全新安装/无本地数据：空列表（演示数据不再冒充用户真实足迹）
                else -> emptyList()
            }
            userFootprints = footprints
            // 微足迹公开池独立于 Footprint Room，启动即从自己的 SP 读回
            microSnapshot = MicroFeedStore.load(context)
            publishedRemoteIds.clear()
            publishedRemoteIds.putAll(microSnapshot.remoteIds)
            refreshMicroFeed()
            // 冷启动即修复历史数据里的云端媒体地址（/uploads/... 或旧局域网地址），
            // 不用等登录/同步；已下载过的文件会直接跳过。
            scope.launch { repairLocalMediaAndReload() }
            recomputeMedals()
            tripPlans = Persistence.loadTripPlans(context).let { list ->
                autoArchiveIfNeeded(list)
            }
            courses = Persistence.loadCourses(context)
            courseTable = Persistence.loadCourseTable(context)
            commonCompanions = Persistence.loadCommonCompanions(context)
            packingTemplate = Persistence.loadPackingTemplate(context)
            user = user.copy(
                nickname = settings.nickname,
                signature = settings.signature,
                avatarText = settings.nickname.take(1),
                // 纯本地模式：头像就是本机 files 里的一个绝对路径
                avatarUrl = d.avatarPath?.takeIf { java.io.File(it).exists() },
                footprintCount = footprints.size,
                citiesCount = footprints.map { it.city }.distinct().size,
                daysCount = footprints
                    .map { it.dateMillis?.div(86_400_000L)?.toString() ?: it.date }
                    .distinct().count()
            )
            refreshWeather(relocate = true)
            bootstrapAuth(d)
        }
    }

    // ---------- 认证门禁 ----------

    /** CHECKING：启动校验 token 中；LOGGED_OUT：登录页；LOGGED_IN：在线；GUEST：离线模式（可正常使用本地功能） */
    var authState by mutableStateOf(AuthState.CHECKING)
        private set

    /** 离线模式：服务器不可达或用户主动选择离线；本地足迹/地图/拍摄全部照常，网络功能禁用 */
    var offlineMode by mutableStateOf(false)
        private set

    /** 离线时仍保留的登录账号（有 token 但服务器不可达）；仅用于展示与恢复连接 */
    private var offlineAccount: String? = null

    /** 最近一次在线登录的用户 id：离线时沿用同一个本机目录 */
    private var lastKnownUid: Int = 0

    /** 当前正在查看的他人主页 id（0 = 未打开） */
    var viewedUserId by mutableIntStateOf(0)
        private set

    /** 我关注的用户 id 集合：用于攻略作者卡片等位置的按钮初始态 */
    var followedUserIds by mutableStateOf<Set<Int>>(emptySet())
        private set

    /** 当前登录账号（用户名，仅内存保存，密码绝不落地） */
    private var currentAccount: String? = null

    /** 当前账号标识（用户名），只读给 UI 展示 */
    val accountId: String get() = currentAccount.orEmpty()

    /** 本机登录过的账号，最新在前；切换账号时直接点选，无需重输账号 */
    var savedAccounts by mutableStateOf(emptyList<SavedAccount>())
        private set

    /** 把本机缓存灌回内存；登录页、离线模式、云端失败降级共用 */
    private suspend fun loadLocalSnapshot() {
        val cached = OfflineCache.loadGuides(context)
        guides = cached?.map { g ->
            g.copy(liked = g.id in likedIds, favored = g.id in favoredIds)
        } ?: MockData.guides
        routes = OfflineCache.loadRoutes(context) ?: MockData.routes
        OfflineCache.loadFootprints(context)?.let { footprints = it }
        Persistence.loadTripPlans(context).let { if (it.isNotEmpty()) tripPlans = it }
    }

    private suspend fun bootstrapAuth(d: PersistedData) {
        savedAccounts = d.savedAccounts
        lastKnownUid = d.lastUserId
        if (lastKnownUid > 0) UserScope.setUid(lastKnownUid)
        // token 里没有账号名，用最近一次登录的账号补上上下文
        currentAccount = d.savedAccounts.firstOrNull()?.account
        // 云端能力默认打开；离线模式只在本次会话临时关闭，联网后自动恢复
        if (!CloudConfig.ENABLED) CloudConfig.setEnabled(context, true)
        cloudSyncEnabled = true
        // 恢复 token 期间先不要挂 401 回调，避免网络失败被误判成“登录过期”而清 token
        BackendApi.onUnauthorized = null
        val saved = d.backendToken
        if (!saved.isNullOrBlank()) {
            // 先探活：只有服务器活着才做 token 校验，避免后端没启动时把用户踢回登录页
            if (!BackendApi.pingServer()) {
                enterOffline(keepAccount = true)
                return
            }
            val restored = runCatching { BackendApi.restoreToken(saved) }.getOrDefault(false)
            if (restored) {
                BackendApi.onUnauthorized = { scope.launch { logout("登录已过期，请重新登录") } }
                offlineMode = false
                authState = AuthState.LOGGED_IN
                cloudLinked = true
                com.example.my.data.messaging.DmMessageService.start(context)
                BackendApi.remoteUserId?.let {
                    lastKnownUid = it
                    runCatching { Persistence.saveLastUserId(context, it) }
                }
                Persistence.saveOfflineMode(context, false)
                restoreFromCloud()
                syncCloudData()
                // 冷启动恢复登录态后补传一次：离线期间的改动这次一起上去
                CloudSync.requestSync(context, 0L)
                return
            }
            // 服务器可达但 token 失效：这才是真正需要重新登录的情况
            BackendApi.clearToken()
            Persistence.saveToken(context, "")
        } else {
            BackendApi.onUnauthorized = { scope.launch { logout("登录已过期，请重新登录") } }
        }

        loadLocalSnapshot()
        user = MockData.user
        val account = d.savedAccounts.firstOrNull()
        if (account != null) {
            user = user.copy(
                nickname = account.nickname.ifBlank { account.account },
                avatarText = account.nickname.take(1).ifBlank { account.account.take(1) }
            )
        }
        // 用户上次主动选了离线使用：本次也直接进入，不再卡登录页
        if (d.offlineMode) enterOffline(keepAccount = d.savedAccounts.isNotEmpty())
        else authState = AuthState.LOGGED_OUT
    }

    /** 进入离线模式：本地数据照常，所有服务器出口关闭；有 token 时后台定时重连 */
    fun goOffline() {
        scope.launch {
            BackendApi.onUnauthorized = null
            enterOffline(keepAccount = BackendApi.token != null || savedAccounts.isNotEmpty())
        }
    }

    private suspend fun enterOffline(keepAccount: Boolean) {
        offlineMode = true
        cloudLinked = false
        cloudSyncEnabled = false
        CloudConfig.setEnabled(context, false)
        BackendApi.clearToken()
        if (lastKnownUid > 0) UserScope.setUid(lastKnownUid)
        offlineAccount = if (keepAccount) currentAccount ?: savedAccounts.firstOrNull()?.account else null
        Persistence.saveOfflineMode(context, true)
        loadLocalSnapshot()
        user = MockData.user.let { base ->
            val name = offlineAccount
            if (name.isNullOrBlank()) base
            else {
                val nickname = savedAccounts.firstOrNull { it.account == name }?.nickname
                    ?.ifBlank { name } ?: name
                base.copy(nickname = nickname, avatarText = nickname.take(1))
            }
        }
        authState = AuthState.GUEST
        startOfflineReconnect()
        toast("离线模式：足迹、地图、拍摄照常使用；登录后可同步到云端")
    }

    /** 离线时每 20 秒探活一次；服务器回来且有 token 就自动恢复登录并补传 */
    private var reconnectJob: Job? = null
    private fun startOfflineReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            while (offlineMode) {
                delay(20_000L)
                if (!offlineMode) break
                if (!BackendApi.pingServer(1800)) continue
                val saved = Persistence.loadAll(context).backendToken
                if (saved.isNullOrBlank()) {
                    // 没有 token 的游客离线：只提示，等用户在“我的”里登录
                    toast("服务器已连接，可在「我的」登录账号并同步本机数据")
                    break
                }
                if (tryReconnect(saved)) break
            }
        }
    }

    /** 服务器恢复后恢复 token、切回在线并真正跑一次全量同步 */
    private suspend fun tryReconnect(saved: String): Boolean {
        val ok = runCatching { BackendApi.restoreToken(saved) }.getOrDefault(false)
        if (!ok) {
            // 探活成功后校验仍失败，可能只是请求瞬间抖动；再探一次，只有服务器确实活着才判定账号失效
            if (!BackendApi.pingServer(1800)) return false
            BackendApi.clearToken()
            Persistence.saveToken(context, "")
            logout("登录已过期，请重新登录")
            return true
        }
        offlineMode = false
        cloudLinked = true
        cloudSyncEnabled = true
        CloudConfig.setEnabled(context, true)
        Persistence.saveOfflineMode(context, false)
        authState = AuthState.LOGGED_IN
        BackendApi.onUnauthorized = { scope.launch { logout("登录已过期，请重新登录") } }
        com.example.my.data.messaging.DmMessageService.start(context)
        BackendApi.remoteUserId?.let {
            lastKnownUid = it
            runCatching { Persistence.saveLastUserId(context, it) }
        }
        toast("服务器已连接：正在恢复并上传本机数据")
        restoreFromCloud()
        syncCloudData()
        CloudSync.syncNow(context)
        return true
    }

    /** 「我的」页离线状态条上的重试按钮 */
    fun retryCloudConnection() {
        scope.launch {
            if (!offlineMode) return@launch
            if (!BackendApi.pingServer()) {
                toast("仍然连不上服务器，请确认后端已启动且在同一局域网")
                return@launch
            }
            val saved = Persistence.loadAll(context).backendToken
            if (saved.isNullOrBlank()) {
                openLoginFromOffline()
            } else {
                tryReconnect(saved)
            }
        }
    }

    /** 离线/游客状态下回到登录页；本机数据保留，登录成功后统一上传 */
    fun openLoginFromOffline() {
        offlineMode = false
        CloudConfig.setEnabled(context, true)
        scope.launch { Persistence.saveOfflineMode(context, false) }
        authState = AuthState.LOGGED_OUT
    }

    /** 登录 / 冷启动恢复时的云端全量回灌；同一账号只完整恢复一次 */
    private suspend fun restoreFromCloud() {
        val uid = BackendApi.remoteUserId ?: 0
        // 进入账号作用域：旧版单账号目录首次升级时搬进 users/<uid>/，之后各账号互不可见
        if (uid > 0) com.example.my.data.UserScope.activate(context, uid)
        // 每次都跑一次 restore：已有条目直接跳过，云端缺失/上次只恢复了一部分的条目在这里补齐。
        // 以前“恢复过一次就直接 return”，一旦上次媒体或足迹没恢复完整，后面永远不会再补。
        restoreProgress = "正在从云端恢复数据…"
        val result = runCatching {
            com.example.my.data.sync.CloudRestore.restore(context,
                onProgress = { text ->
                    // 回调在 IO 线程，切回主线程更新 Compose 状态
                    scope.launch { restoreProgress = text }
                },
                onMediaDone = {
                    // 照片/视频后台下载完成：重新读 Room 并刷新页面，不需要用户手动操作
                    scope.launch {
                        OfflineCache.loadFootprints(context)?.let {
                            footprints = it
                            userFootprints = it
                        }
                        OfflineCache.loadRoutes(context)?.let { routes = it }
                        restoreProgress = null
                    }
                }
            )
        }.onFailure {
            runCatching {
                java.io.File(context.filesDir, "restore_debug.log")
                    .appendText("${System.currentTimeMillis()} AppState restore failed: ${it.stackTraceToString()}\n")
            }
            android.util.Log.e("AppState", "CloudRestore.restore failed", it)
        }.getOrNull()
        android.util.Log.i("AppState", "CloudRestore result=$result")
        restoreProgress = null
        if (result != null) {
            applyCloudAiConfig(result.aiConfig)
            // 云端没有 AI 配置时，把本机这份补传上去，保证「本地与云端都有」
            scheduleAiConfigUpload(0L)
            // 回灌后刷新内存态：足迹/行程/计划立即出现在界面，不必等下次冷启动
            OfflineCache.loadFootprints(context)?.let {
                footprints = it
                userFootprints = it
            }
            OfflineCache.loadRoutes(context)?.let { routes = it }
            Persistence.loadTripPlans(context).let { if (it.isNotEmpty()) tripPlans = it }
            bumpLocalSequences()
        }
        // 新恢复的条目里可能还有下载失败的 URL，再兜一次；成功则改写成本地路径。
        // 即使后台媒体任务刚启动也要跑：它只处理新条目，而历史条目可能早就停在云端 URL 上，
        // 两边都走 downloadToFootprint 的同一个目标文件锁，不会重复下载或写坏半截文件。
        repairLocalMediaAndReload()
    }

    /**
     * 把本地足迹里的云端媒体地址下载到本机并回写。
     * 返回本次成功落地的文件数；没有任何远程媒体时几乎无开销。
     */
    private suspend fun repairLocalMediaAndReload(): Int {
        val repaired = runCatching {
            com.example.my.data.sync.CloudRestore.repairLocalMedia(context) { text ->
                scope.launch { restoreProgress = text }
            }
        }.getOrDefault(0)
        if (repaired > 0) {
            OfflineCache.loadFootprints(context)?.let {
                footprints = it
                userFootprints = it
            }
        }
        restoreProgress = null
        return repaired
    }

    /** 登录页/状态栏展示的恢复进度；null 表示不在恢复 */
    var restoreProgress by mutableStateOf<String?>(null)
        private set

    /**
     * 本地自增 id 跨越重启后要对齐：
     * guideSeq/fpSeq/commentSeq 每次冷启动都从初值开始，而 Room 里可能已经有同 id 的数据，
     * 不对齐就会重复 key（LazyColumn 直接抛 "Key was already used"）或互相覆盖。
     */
    private fun bumpLocalSequences() {
        guides.maxOfOrNull { it.id }?.let { if (it >= guideSeq) guideSeq = it + 1 }
        footprints.maxOfOrNull { it.id }?.let { if (it >= fpSeq) fpSeq = it + 1 }
        val maxComment = guides.flatMap { g ->
            g.commentList.flatMap { c -> listOf(c.id) + c.replies.map { r -> r.id } }
        }.maxOrNull() ?: 0
        if (maxComment >= commentSeq) commentSeq = maxComment + 1
    }

    /** 用户名登录：账号 + 固定盐预哈希密码，服务端直接校验。 */
    fun login(
        username: String, password: String,
        captchaId: String? = null, captchaText: String? = null,
        onResult: (AuthOpResult) -> Unit
    ) {
        scope.launch {
            val hash = BackendApi.hashPassword(password)
            when (val r = BackendApi.login(username, hash, captchaId, captchaText)) {
                is AuthResult.Success -> {
                    currentAccount = r.user.username.ifBlank { username.trim() }
                    onLoggedIn()
                    onResult(AuthOpResult(true))
                }
                is AuthResult.Failure -> onResult(AuthOpResult(false, r.message, r.needCaptcha))
            }
        }
    }

    /** 注册：用户名 + 图形验证码 + 密码；需先在浏览器打开注册验证地址。 */
    fun register(
        username: String, password: String, nickname: String,
        captchaId: String, captchaText: String,
        onResult: (AuthOpResult) -> Unit
    ) {
        scope.launch {
            when (val r = BackendApi.register(
                username, BackendApi.hashPassword(password), nickname, captchaId, captchaText
            )) {
                is AuthResult.Success -> {
                    currentAccount = r.user.username.ifBlank { username.trim() }
                    onLoggedIn()
                    onResult(AuthOpResult(true))
                }
                is AuthResult.Failure -> onResult(AuthOpResult(false, r.message, r.needCaptcha))
            }
        }
    }

    /** 申请注册验证地址：地址里带一次性验证码。 */
    fun requestVerifyLink(
        username: String, captchaId: String, captchaText: String,
        onResult: (VerifyLink?, String?) -> Unit
    ) {
        scope.launch {
            val (link, msg) = BackendApi.requestVerifyLink(username, captchaId, captchaText)
            onResult(link, msg)
        }
    }

    // ---------- 图形验证码 ----------

    /** 拉取一张图形验证码；回调 null 表示服务端不可达 */
    fun fetchCaptcha(onResult: (Captcha?) -> Unit) {
        scope.launch { onResult(BackendApi.fetchCaptcha()) }
    }

    // ---------- 找回密码 ----------

    /** 账号体系不再使用任何邮件验证码；忘记密码由管理员在后台重置。 */
    fun passwordResetHint(): String = "请联系管理员重置密码"

    // ---------- 注销账号 ----------

    fun fetchDeletionStatus(onResult: (DeletionStatus?, String?) -> Unit) {
        scope.launch {
            val (status, msg) = BackendApi.fetchDeletionStatus()
            onResult(status, msg)
        }
    }

    fun requestAccountDeletion(password: String, onResult: (DeletionStatus?, String?) -> Unit) {
        scope.launch {
            val (status, msg) = BackendApi.requestAccountDeletion(
                BackendApi.hashPassword(password)
            )
            onResult(status, msg)
        }
    }

    fun cancelAccountDeletion(onResult: (Boolean, String?) -> Unit) {
        scope.launch {
            val (ok, msg) = BackendApi.cancelAccountDeletion()
            onResult(ok, msg)
        }
    }

    // ---------- 切换账号 ----------

    /** 退出当前账号回到登录页，历史账号保留，可直接点选另一个账号登录 */
    fun switchAccount() {
        logout("已退出当前账号，请选择其他账号登录")
    }

    /** 从本机历史账号里移除某个账号 */
    fun removeSavedAccount(account: String) {
        savedAccounts = savedAccounts.filter { it.account != account }
        scope.launch { Persistence.removeAccount(context, account) }
    }

    private fun onLoggedIn() {
        authState = AuthState.LOGGED_IN
        offlineMode = false
        cloudLinked = true
        cloudSyncEnabled = true
        CloudConfig.setEnabled(context, true)
        com.example.my.data.messaging.DmMessageService.start(context)
        BackendApi.remoteUserId?.let {
            lastKnownUid = it
            scope.launch { Persistence.saveLastUserId(context, it); Persistence.saveOfflineMode(context, false) }
        }
        BackendApi.token?.let { t -> scope.launch { Persistence.saveToken(context, t) } }
        scope.launch {
            // 1) 先全量回灌云端数据（新设备/重装后，足迹等在这里回到本机）
            restoreFromCloud()
            // 2) 再拉个人资料 / 攻略 / 关注关系
            syncCloudData()
            // 3) 最后把本机（含刚恢复的数据）补传一次
            if (CloudConfig.ENABLED) CloudSync.syncNow(context)
            // 4) 增量回拉可能带回 /uploads/... 的新条目，再落一次媒体到本机
            if (!com.example.my.data.sync.CloudRestore.isMediaRestoring()) {
                repairLocalMediaAndReload()
            }
            currentAccount?.let { e ->
                val account = SavedAccount(e, user.nickname)
                Persistence.saveAccount(context, account)
                savedAccounts = listOf(account) + savedAccounts.filter { it.account != e }.take(4)
            }
        }
    }

    /** 登出：清 token、保留本地足迹/行程（跟设备走）、回到 GUEST 而非 LOGGED_OUT
     *  这样用户换账号/退出后自己的个人记录不会被清空。
     *  云端关联的 guides、likedIds、favoredIds 会重置；本地 routes/footprints/tripPlans 保留。 */
    fun logout(message: String? = null) {
        // 先通知服务端作废当前 token（异步，失败不阻塞本地退出）；随后清本地登录态
        scope.launch { runCatching { BackendApi.logoutRemote() } }
        BackendApi.clearToken()
        com.example.my.data.messaging.DmMessageService.stop(context)
        offlineMode = false
        offlineAccount = null
        reconnectJob?.cancel()
        reconnectJob = null
        CloudConfig.setEnabled(context, true)
        cloudSyncEnabled = true
        scope.launch {
            Persistence.saveToken(context, "")
            Persistence.saveOfflineMode(context, false)
        }
        cloudLinked = false
        // 云端攻略清掉，回退到 MockData（本地 Room 缓存的攻略仍在）
        guides = MockData.guides
        user = MockData.user
        // 本地足迹/行程保留：跟设备走，不跟账号绑定
        // routes 是个人旅行路线，也保留
        likedIds = emptySet()
        favoredIds = emptySet()
        followedUserIds = emptySet()
        currentGuideId = null
        currentRouteId = null
        showUpload = false
        showPlanSheet = false
        showAiTripSheet = false
        tripDetailId = null
        screen = ExtraScreen.NONE
        activeTab = AppTab.DISCOVER
        // 必须登录才能进入：退出后直接回登录页，不再提供游客模式
        authState = AuthState.LOGGED_OUT
        message?.let { toast(it) }
    }

    /** 游客/离线入口：不登录也能进主界面，只使用本机数据 */
    fun goGuest() {
        goOffline()
    }

    // ---------- 后端云端同步（失败静默降级为本地数据） ----------

    /** 是否已连接云端 */
    var cloudLinked by mutableStateOf(false)
        private set

    // ---------- 云端同步开关（设置页「数据与备份」里的开关） ----------

    /** 开关状态：true = 连接服务器并自动上传；false = 隔离服务器，只存本机 */
    var cloudSyncEnabled by mutableStateOf(CloudConfig.ENABLED)
        private set

    /** 正在上传（设置页显示"同步中"） */
    var cloudSyncing by mutableStateOf(false)
        private set

    /** 最近一次同步完成时间（0 = 还没同步过） */
    var cloudLastSyncAt by mutableStateOf(CloudSync.lastSyncAt(context))
        private set

    /** 最近一次同步失败原因（成功后清空） */
    var cloudSyncError by mutableStateOf(CloudSync.lastError(context))
        private set

    /**
     * 切换云端同步开关。
     * 打开：恢复已保存的 token；没有 token 就停在登录页。登录成功后自动开始上传。
     * 关闭：清 token、断开云端、回到本机模式 —— 之后所有网络出口短路，一个请求都不发。
     */
    fun setCloudSync(enabled: Boolean) {
        if (enabled == cloudSyncEnabled) return
        // 数据必须同时保存在本机与云端，云端同步不再允许关闭
        if (!enabled) {
            cloudSyncEnabled = true
            toast("云端备份为账号必选：数据同时保存在本机和云端")
            return
        }
        CloudConfig.setEnabled(context, enabled)
        BackendApi.onUnauthorized = { scope.launch { logout("登录已过期，请重新登录") } }
        scope.launch {
            val saved = Persistence.loadAll(context).backendToken
            val restored = !saved.isNullOrBlank() &&
                runCatching { BackendApi.restoreToken(saved) }.getOrDefault(false)
            if (restored) {
                authState = AuthState.LOGGED_IN
                cloudLinked = true
                com.example.my.data.messaging.DmMessageService.start(context)
                syncCloudData()
                syncCloudNow()
                toast("已开启云端同步，正在上传本机数据")
            } else {
                authState = AuthState.LOGGED_OUT
                toast("请先登录账号；登录成功后会自动上传本机数据")
            }
        }
    }

    /** 立即同步一次（设置页按钮 / 开关打开后首次上传） */
    fun syncCloudNow() {
        if (!CloudConfig.ENABLED) {
            toast("云端同步未开启")
            return
        }
        if (BackendApi.token == null) {
            toast("请先登录账号")
            return
        }
        cloudSyncing = true
        scope.launch {
            val r = CloudSync.syncNow(context)
            val repaired = repairLocalMediaAndReload()
            cloudSyncing = false
            cloudLastSyncAt = r.at
            cloudSyncError = if (r.ok) null else r.message
            toast(
                if (r.ok) "同步完成：${r.footprints} 条足迹 · ${r.routes} 条行程 · 新传媒体 ${r.media} 个" +
                    if (repaired > 0) " · 本地落地 $repaired 个" else ""
                else "同步失败：${r.message ?: "请检查网络与登录状态"}"
            )
        }
    }

    /** 数据有改动时合并触发一次后台上传（未开启/未登录时是空操作） */
    private fun scheduleCloudSync() {
        CloudSync.requestSync(context)
    }

    private suspend fun syncCloudData() {
        // 用户资料：以云端为准
        BackendApi.fetchMe()?.let { ru ->
            user = user.copy(
                nickname = ru.nickname,
                signature = ru.signature,
                avatarText = ru.nickname.take(1),
                avatarUrl = ru.avatar,
                citiesCount = ru.citiesCount,
                footprintCount = ru.footprintCount,
                daysCount = ru.daysCount,
                followers = ru.followers,
                following = ru.following,
                likesReceived = ru.likesReceived,
                joinedAt = ru.createdAt
            )
        }
        // 我关注的人：用于攻略作者卡片的「已关注」初始态和关注页
        BackendApi.fetchFollowing()?.let { list ->
            followedUserIds = list.filter { it.following }.map { it.peer.id }.toSet()
        }
        // 私信未读：登录/同步时顺带刷新顶部红点
        BackendApi.fetchConversations()?.let { list ->
            updateDmUnread(list.sumOf { it.unread })
        }
        // 攻略列表：云端有数据则替换本地 mock，同时写入 Room 离线缓存
        val remoteGuides = BackendApi.fetchGuides()?.takeIf { it.isNotEmpty() }
        if (remoteGuides != null) {
            guides = remoteGuides.map { g ->
                g.copy(liked = g.id in likedIds, favored = g.id in favoredIds)
            }
            OfflineCache.saveGuides(context, guides)
        } else {
            // 云端不可达/无数据：离线缓存兜底
            OfflineCache.loadGuides(context)?.let { cached ->
                guides = cached.map { g ->
                    g.copy(liked = g.id in likedIds, favored = g.id in favoredIds)
                }
            }
        }
        // 我的行程：云端同步合并——云端同 id 覆盖，本地独有（离线新建未上传）保留，结果写入 SQLite。
        // 过滤服务端演示行程：只保留「本地已有 id」或「与本地足迹城市+日期匹配」的行程
        // （App 内发布的行程 cities/date 与对应足迹一致；演示行程如三亚无本地足迹对应，直接剔除）
        BackendApi.fetchMyRoutes()?.let { remote ->
            val remoteMapped = remote.map { r -> routePublic[r.id]?.let { r.copy(isPublic = it) } ?: r }
            val localRouteIds = routes.map { it.id }.toSet()
            val fpCityDate = footprints.map { it.city to it.date }.toSet()
            val meaningful = remoteMapped.filter { r ->
                r.id in localRouteIds ||
                    (r.cities.firstOrNull()?.let { c -> (c to r.date) in fpCityDate } ?: false)
            }
            val remoteIds = meaningful.map { it.id }.toSet()
            val localOnly = routes.filter { it.id !in remoteIds }
            routes = meaningful + localOnly
            OfflineCache.saveRoutes(context, routes)
        }
        // 足迹无独立云端表：本地 SQLite 即权威（登录态不清空用户本机数据，也不注入云端演示数据）
        footprints = userFootprints
        recomputeMedals()
    }

    /** 当前用户发布的攻略（本地模式按本地作者 id 过滤；云端模式按服务端 id 过滤） */
    val myGuides: List<Guide>
        get() {
            if (!CloudConfig.ENABLED) {
                return guides.filter { it.authorId == CloudConfig.LOCAL_AUTHOR_ID }
            }
            val uid = BackendApi.remoteUserId ?: return emptyList()
            return guides.filter { it.authorId == uid }
        }

    /**
     * 按真实数据重算成就勋章，替代 mock 里写死的解锁状态。
     * 数据源：本地足迹（城市/条目/特别标记/月份覆盖），云端演示统计不参与。
     * 新注册账号所有勋章进度为 0。
     */
    private fun recomputeMedals() {
        val months = footprints.mapNotNull { monthOf(it.date) }.toSet()
        val specialCount = footprints.count { it.special }
        val fpCities = footprints.map { it.city }.distinct().count()
        val tpl = MockData.user.medals
        fun progress(name: String, current: Int): Medal {
            val t = tpl.first { it.name == name }
            return t.copy(unlocked = current >= t.target, current = current.coerceIn(0, t.target))
        }
        user = user.copy(
            medals = listOf(
                progress("初心者", footprints.size),
                progress("小有所成", footprints.size),
                progress("旅行达人", footprints.size),
                progress("城市收集者", fpCities),
                progress("城市漫游家", fpCities),
                // 暂无国家维度数据源，保持 0 进度
                progress("环球行者", 0),
                progress("星光收藏家", specialCount),
                progress("四季行者", seasonsOf(months)),
                progress("月月不停歇", months.size)
            )
        )
    }

    /**
     * "2023年8月" / "2023-08" / "2023-08-12" → 8。
     * 统一用一套正则：足迹日期原本有两种格式混用，各处再各写一套解析迟早会漏。
     */
    private fun monthOf(date: String): Int? =
        Regex("""\d{4}[-年.](\d{1,2})""").find(date)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..12 }

    /** 覆盖的季节总数（春夏秋冬） */
    private fun seasonsOf(months: Set<Int>): Int = listOf(
        listOf(3, 4, 5), listOf(6, 7, 8), listOf(9, 10, 11), listOf(12, 1, 2)
    ).count { season -> season.any { it in months } }

    /** 更新头像：纯本地模式存到本机 files/avatar/；云端模式才上传 */
    fun uploadAvatar(uri: Uri) {
        scope.launch {
            if (!CloudConfig.ENABLED) {
                val path = saveLocalAvatar(uri)
                if (path != null) {
                    user = user.copy(avatarUrl = path)
                    Persistence.saveAvatarPath(context, path)
                    toast("头像已更新")
                } else {
                    toast("头像保存失败，请换一张图片试试")
                }
                return@launch
            }
            val url = BackendApi.uploadAvatar(context, uri)
            if (url != null) {
                user = user.copy(avatarUrl = url)
                toast("头像已更新")
            } else {
                toast(if (cloudLinked) "头像上传失败，请重试" else "未连接服务器，无法上传")
            }
        }
    }

    /**
     * 本地头像落盘：解码 → 压到 720px → JPEG 存 files/avatar/，返回绝对路径。
     * 旧头像文件顺手删掉，避免每次换头像都留一份垃圾在用户手机上。
     */
    private suspend fun saveLocalAvatar(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = PhotoStore.decodeUri(context, uri, 720) ?: return@runCatching null
            val dir = com.example.my.data.UserScope.dir(context, "avatar")
            val out = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bmp.recycle()
            // 删旧文件（当前 user.avatarUrl 若指向本机旧头像）
            user.avatarUrl?.let { old ->
                runCatching {
                    val f = File(old)
                    if (f.exists() && f.absolutePath != out.absolutePath && f.parentFile?.name == "avatar") f.delete()
                }
            }
            out.absolutePath
        }.getOrNull()
    }

    /** 用服务端评论列表整体替换某攻略的评论区（保留本地点赞状态不追踪，以服务端为准） */
    private fun replaceComments(guideId: Int, comments: List<Comment>) {
        // LazyColumn 拿评论 id 当 key，id 重复或为 0 会抛
        // "Key ... was already used"，整个攻略详情页直接闪退。
        // 服务端数据也过一遍唯一化，异常数据宁可有 id 偏移也不能崩。
        val seen = HashSet<Int>(comments.size)
        val safe = ArrayList<Comment>(comments.size)
        for ((i, c) in comments.withIndex()) {
            var cid = if (c.id != 0) c.id else -(i + 1)
            if (!seen.add(cid)) {
                cid = -(i + 1)
                seen.add(cid)
            }
            safe.add(if (cid == c.id) c else c.copy(id = cid))
        }
        // 从本地缓存恢复评论后，自增起点要越过已有的最大 id，
        // 否则重启后新评论又会拿到 1000000，和旧评论 id 撞车 → LazyColumn 重复 key 闪退
        commentSeq = maxOf(commentSeq, (safe.maxOfOrNull { it.id } ?: 0) + 1)
        guides = guides.map { g ->
            if (g.id == guideId) g.copy(commentList = safe) else g
        }
    }

    // ---------- 导航 ----------

    val currentGuide: Guide?
        get() = guides.firstOrNull { it.id == currentGuideId }

    val currentRoute: Route?
        get() = routes.firstOrNull { it.id == currentRouteId }

    fun toast(msg: String) {
        toastText = msg
        toastSeq++
    }

    fun clearToast() {
        toastText = null
    }

    fun openGuide(id: Int) {
        currentGuideId = id
        lastViewedGuideId = id
        prefs.edit().putInt(KEY_LAST_GUIDE, id).apply()
        // 纯本地模式：评论也是本机权威，从 Room 取回（含自己发过的，重启不丢）
        if (!CloudConfig.ENABLED) {
            scope.launch {
                OfflineCache.loadComments(context, id)?.let { saved ->
                    replaceComments(id, saved)
                }
            }
            return
        }
        // 云端评论拉取（覆盖本地 mock 评论），成功后写入离线缓存
        if (cloudLinked) scope.launch {
            BackendApi.fetchComments(id)?.let {
                replaceComments(id, it)
                OfflineCache.saveComments(context, id, it)
            }
        }
    }

    fun closeGuide() {
        currentGuideId = null
    }

    fun openRoute(id: Int) {
        currentRouteId = id
    }

    fun closeRoute() {
        currentRouteId = null
    }

    fun openUpload(aiAssist: Boolean = false) {
        uploadDirty = false
        uploadExitRequested = false
        mapPickOpen = false
        aiFootprintAssist = aiAssist
        showUpload = true
    }

    /** 发布页取用一次「从 AI 助手进来」的标记；取完即清，避免影响下一次普通新建 */
    fun consumeAiFootprintAssist(): Boolean {
        val assist = aiFootprintAssist
        aiFootprintAssist = false
        return assist
    }

    /**
     * 关闭发布页。
     *
     * 有内容时不能直接关：用户可能已经选了 9 张照片、写了几百字备注，
     * 一个误触返回就全没了。这里先记一笔「请求关闭」，由 UploadScreen 弹二次确认。
     * 保存成功后的关闭走 force = true，不再打扰。
     */
    fun closeUpload(force: Boolean = false) {
        if (!force && uploadDirty) {
            uploadExitRequested = true
            return
        }
        uploadDirty = false
        uploadExitRequested = false
        mapPickOpen = false
        aiFootprintAssist = false
        showUpload = false
    }

    /** 返回键 / 顶部取消：有内容先确认，没内容直接关 */
    fun requestCloseUpload() = closeUpload(force = false)

    /** 发布页自己判断「算不算有内容」，供关闭时决策 */
    fun markUploadDirty(dirty: Boolean) {
        uploadDirty = dirty
    }

    fun consumeUploadExitRequest() {
        uploadExitRequested = false
    }

    /**
     * 「离线地图包」请求计数：MapScreen 每收到一次自增就打开地图包管理面板。
     * 用计数而不是布尔，是因为同一个入口可能被连点，布尔会被「还没消费就置 false」吞掉。
     */
    var mapPacksOpenTick by mutableStateOf(0)
        private set

    fun requestMapPacks() {
        mapPacksOpenTick += 1
    }

    fun openScreen(s: ExtraScreen) {
        if (s == ExtraScreen.CREATIVE && screen != ExtraScreen.CREATIVE) {
            screenBeforeCreative = screen
        }
        screen = s
    }

    /** 点击头像 / 作者卡片进入他人主页；id<=0 是示例数据，不打开 */
    fun openUserProfile(userId: Int, nickname: String = "") {
        if (userId <= 0) {
            toast("这是示例内容，暂无主页")
            return
        }
        viewedUserId = userId
        screen = ExtraScreen.USER_PROFILE
    }

    fun closeUserProfile() {
        viewedUserId = 0
        if (screen == ExtraScreen.USER_PROFILE) screen = ExtraScreen.NONE
    }

    /** 关注 / 取关，同时更新全局关注集合与自己的关注数 */
    fun toggleFollow(userId: Int, onResult: ((FollowState?) -> Unit)? = null) {
        if (!CloudConfig.ENABLED || BackendApi.token == null) {
            toast("离线模式下无法关注，登录并连接服务器后再试")
            onResult?.invoke(null)
            return
        }
        scope.launch {
            val f = BackendApi.toggleFollow(userId)
            if (f != null) {
                followedUserIds = if (f.following) followedUserIds + userId else followedUserIds - userId
                val delta = if (f.following) 1 else -1
                user = user.copy(following = (user.following + delta).coerceAtLeast(0))
            }
            onResult?.invoke(f)
        }
    }

    /** 通知栏点进来要直达的会话；MessagesScreen 组合后消费 */
    var pendingDmPeerId by mutableStateOf<Int?>(null)
        private set

    fun requestOpenDmPeer(peerId: Int) {
        screen = ExtraScreen.MESSAGES
        pendingDmPeerId = peerId.takeIf { it > 0 }
    }

    fun consumePendingDmPeer(): Int? {
        val id = pendingDmPeerId
        pendingDmPeerId = null
        return id
    }

    fun closeScreen() {
        // 创作专区是从 AI 助手点进来的，返回时回助手；从发现页/我的进入则回首页
        if (screen == ExtraScreen.CREATIVE) {
            val back = screenBeforeCreative
            screenBeforeCreative = ExtraScreen.NONE
            if (back != ExtraScreen.NONE) {
                screen = back
                return
            }
        }
        // 行情页是从资讯页点进去的，返回时回资讯，而不是直接掉回发现页
        if (screen == ExtraScreen.STOCK) {
            screen = ExtraScreen.NEWS
            return
        }
        // 离开助手页就丢掉就地入口的一次性上下文，避免下轮 system 还挂着上次现场
        if (screen == ExtraScreen.AI_ASSISTANT) {
            aiEntryHint = null
            aiPrefill = null
        }
        screen = ExtraScreen.NONE
    }

    fun openPlanSheet() {
        showPlanSheet = true
    }

    fun closePlanSheet() {
        showPlanSheet = false
    }

    /** AI 生成行程弹窗 */
    var showAiTripSheet by mutableStateOf(false)

    fun openAiTripSheet() {
        showAiTripSheet = true
    }

    fun closeAiTripSheet() {
        showAiTripSheet = false
    }

    // ---------- 出行计划（持久化 + 出发提醒） ----------

    /** 切换排序方式并持久化（拖动后自动切到自定义排序） */
    fun updatePlanSortMode(custom: Boolean) {
        planCustomOrder = custom
        prefs.edit().putBoolean(KEY_PLAN_CUSTOM_ORDER, custom).apply()
    }

    /**
     * 保存拖拽后的顺序。
     * 不能只给当前可见列表编号：分类筛选后再拖拽，其他分类的行程会全部被挤到末尾。
     * 这里以完整列表的有效顺序为基准，只把被拖拽的项按新顺序回填，其余保持相对位置。
     */
    fun reorderTripPlans(orderedIds: List<Long>) {
        if (orderedIds.isEmpty()) return
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        fun startOf(p: TripPlan): Long =
            runCatching { fmt.parse(p.startDate)?.time }.getOrNull() ?: Long.MAX_VALUE

        val baseline = tripPlans.sortedWith(
            compareBy<TripPlan>(
                { if (it.manualOrder >= 0) it.manualOrder else Int.MAX_VALUE },
                { startOf(it) }
            )
        )
        val moved = orderedIds.mapNotNull { id -> baseline.firstOrNull { it.id == id } }
        if (moved.isEmpty()) return
        val movedIds = orderedIds.toSet()
        var cursor = 0
        val merged = baseline.map { p -> if (p.id in movedIds) moved[cursor++] else p }
        val indexById = merged.withIndex().associate { (i, p) -> p.id to i }
        tripPlans = tripPlans.map { p -> indexById[p.id]?.let { p.copy(manualOrder = it) } ?: p }
        scope.launch { Persistence.saveTripPlans(context, tripPlans) }
        scheduleCloudSync()
        updatePlanSortMode(true)
    }

    /**
     * 新建计划：内存 + DataStore 落盘，并对出发日期安排一次系统提醒
     * （默认出发前 1 天 09:00，可在行程详情里自定义；已过期/当天出发则不安排）。
     */
    fun addTripPlan(
        name: String,
        destination: String,
        startDate: String,
        days: Int,
        note: String,
        dailyPlan: List<String> = emptyList(),
        customPacking: List<String> = emptyList(),
        companions: List<String> = emptyList()
    ) {
        // 毫秒级时间戳在极短时间内可能撞号（双击/时钟回拨）；混一点 ns 让尾几位抖动
        val now = System.currentTimeMillis()
        val id = now * 1_000L + (System.nanoTime() % 1_000L).coerceAtLeast(0L)
        val plan = TripPlan(
            id = id,
            name = name.trim(),
            destination = destination.trim(),
            startDate = startDate,
            days = days,
            note = note.trim(),
            createdAt = now,
            dailyPlan = dailyPlan,
            // 新建行程直接带上用户保留的清单模板
            customPacking = (customPacking + packingTemplate).distinct(),
            companions = companions.distinct()
        )
        tripPlans = listOf(plan) + tripPlans
        scope.launch { Persistence.saveTripPlans(context, tripPlans) }
        scheduleCloudSync()
        schedulePlanReminder(plan)
    }

    /** 更新单个计划（内存 + 落盘），用于归档、清单勾选、每日安排等轻量修改 */
    fun updateTripPlan(id: Long, transform: (TripPlan) -> TripPlan) {
        tripPlans = tripPlans.map { if (it.id == id) transform(it) else it }
        scope.launch { Persistence.saveTripPlans(context, tripPlans) }
        scheduleCloudSync()
    }

    fun togglePlanArchived(id: Long) {
        var updated: TripPlan? = null
        tripPlans = tripPlans.map {
            if (it.id == id) it.copy(archived = !it.archived).also { p -> updated = p } else it
        }
        scope.launch { Persistence.saveTripPlans(context, tripPlans) }
        scheduleCloudSync()
        // 归档立刻取消提醒；恢复则按原设置重新排队
        updated?.let { schedulePlanReminder(it) }
    }

    /** 批量归档 / 批量恢复 */
    fun batchArchiveTripPlans(ids: Set<Long>, archived: Boolean) {
        if (ids.isEmpty()) return
        tripPlans = tripPlans.map { if (it.id in ids) it.copy(archived = archived) else it }
        scope.launch { Persistence.saveTripPlans(context, tripPlans) }
        scheduleCloudSync()
        tripPlans.filter { it.id in ids }.forEach { schedulePlanReminder(it) }
    }

    /** 批量删除；keepPackingTemplate = true 时把选中行程的自定义清单存为下次可用模板 */
    fun batchDeleteTripPlans(ids: Set<Long>, keepPackingTemplate: Boolean = false) {
        if (ids.isEmpty()) return
        if (keepPackingTemplate) {
            val merged = tripPlans.filter { it.id in ids }.flatMap { it.customPacking }.distinct()
            if (merged.isNotEmpty()) savePackingTemplate(merged)
        }
        val wm = WorkManager.getInstance(context.applicationContext)
        ids.forEach { wm.cancelUniqueWork("plan_reminder_$it") }
        tripPlans = tripPlans.filterNot { it.id in ids }
        if (tripDetailId in ids) tripDetailId = null
        scope.launch { Persistence.saveTripPlans(context, tripPlans) }
        CloudSync.markPlansDeleted(context, ids)
        scheduleCloudSync()
    }
    /**
     * 恢复一条被删除的行程（AI 网关「撤销」用）。
     * 保留原 id 与全部内容，内存和落盘一起还原，重启后仍然在；重复恢复直接忽略。
     */
    fun restoreTripPlan(plan: TripPlan) {
        if (tripPlans.any { it.id == plan.id }) return
        tripPlans = listOf(plan) + tripPlans
        scope.launch { Persistence.saveTripPlans(context, tripPlans) }
        scheduleCloudSync()
    }

    // ---------- 课表 CRUD ----------

    fun saveCourse(c: com.example.my.model.Course) {
        val exists = courses.any { it.id == c.id }
        courses = if (exists) courses.map { if (it.id == c.id) c else it } else courses + c
        persistCourses()
    }

    fun deleteCourse(id: Long) {
        courses = courses.filterNot { it.id == id }
        persistCourses()
    }

    /**
     * 图片导入课表：[replace] = 覆盖已有课表，否则按「星期+课程名+起始节+周次」去重追加。
     * 周次必须进 key：同名同节次的单周课和双周课是两门不同的课，不能互相吃掉。
     */
    fun importCourses(list: List<com.example.my.model.Course>, replace: Boolean) {
        val merged = if (replace) {
            list
        } else {
            (courses + list).distinctBy { c ->
                listOf(
                    c.dayOfWeek, c.name.replace(" ", ""), c.startPeriod,
                    c.weekMode, c.weekStart, c.weekEnd
                )
            }
        }
        courses = merged.sortedWith(
            compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.name })
        )
        persistCourses()
    }

    /** 落盘后刷新课表桌面卡片：先写完 DataStore 再发广播，避免卡片读到旧数据 */
    private fun persistCourses() {
        scope.launch {
            runCatching { Persistence.saveCourses(context, courses) }
            com.example.my.widget.WidgetKit.refresh(
                context, com.example.my.widget.CourseWidgetProvider::class.java
            )
        }
    }

    /** 修改课表作息（节数/时长/开学周…）：落盘并刷新桌面卡片 */
    fun updateCourseTable(s: com.example.my.model.CourseTableSettings) {
        courseTable = s
        scope.launch {
            runCatching { Persistence.saveCourseTable(context, s) }
            com.example.my.widget.WidgetKit.refresh(
                context, com.example.my.widget.CourseWidgetProvider::class.java
            )
        }
    }

    var tripDetailId by mutableStateOf<Long?>(null)
        private set

    fun openTripDetail(id: Long) {
        tripDetailId = id
    }

    fun closeTripDetail() {
        tripDetailId = null
    }

    /** 删除计划：落盘并取消对应提醒 */
    fun deleteTripPlan(id: Long, keepPackingTemplate: Boolean = false) {
        batchDeleteTripPlans(setOf(id), keepPackingTemplate)
    }

    // ---------- 同行人 / 清单模板 ----------

    fun addCommonCompanion(name: String) {
        val n = name.trim()
        if (n.isBlank() || n in commonCompanions) return
        commonCompanions = commonCompanions + n
        scope.launch { Persistence.saveCommonCompanions(context, commonCompanions) }
    }

    fun removeCommonCompanion(name: String) {
        commonCompanions = commonCompanions - name
        scope.launch { Persistence.saveCommonCompanions(context, commonCompanions) }
    }

    fun savePackingTemplate(items: List<String>) {
        packingTemplate = items.distinct()
        scope.launch { Persistence.savePackingTemplate(context, packingTemplate) }
    }

    /** 自定义提醒设置变更后重新调度（关闭时仅取消旧任务） */
    fun updatePlanReminder(
        id: Long,
        enabled: Boolean,
        offsetDays: Int,
        hour: Int,
        minute: Int,
        vibrate: Boolean,
        sound: Boolean
    ) {
        var updated: TripPlan? = null
        tripPlans = tripPlans.map { p ->
            if (p.id == id) p.copy(
                reminderEnabled = enabled,
                reminderOffsetDays = offsetDays.coerceIn(0, 30),
                reminderHour = hour.coerceIn(0, 23),
                reminderMinute = minute.coerceIn(0, 59),
                reminderVibrate = vibrate,
                reminderSound = sound
            ).also { updated = it } else p
        }
        scope.launch { Persistence.saveTripPlans(context, tripPlans) }
        updated?.let { schedulePlanReminder(it) }
    }

    /** 进入行程页时刷新自动归档（App 长时间停留跨天后也能及时更新） */
    fun checkPlanAutoArchive() {
        val updated = autoArchiveIfNeeded(tripPlans)
        if (updated != tripPlans) tripPlans = updated
    }

    /** 已结束超过 30 天的行程自动归档（用户关闭 autoArchive 的不处理） */
    private fun autoArchiveIfNeeded(list: List<TripPlan>): List<TripPlan> {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val updated = list.map { p ->
            val start = runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(p.startDate)?.time
            }.getOrNull() ?: return@map p
            val end = start + (p.days - 1).coerceAtLeast(0) * DAY_MS
            if (!p.archived && p.autoArchive && end + AUTO_ARCHIVE_DAYS * DAY_MS < today) {
                p.copy(archived = true)
            } else p
        }
        if (updated != list) scope.launch { Persistence.saveTripPlans(context, updated) }
        // 自动归档同样要停掉已排队的提醒
        updated.filter { p -> p.archived && list.any { old -> old.id == p.id && !old.archived } }
            .forEach { schedulePlanReminder(it) }
        return updated
    }

    /** 按计划的提醒设置安排一次性通知 */
    private fun schedulePlanReminder(plan: TripPlan) {
        val wm = WorkManager.getInstance(context.applicationContext)
        // 先取消旧任务：关闭提醒或改时间后旧任务不能再触发
        wm.cancelUniqueWork("plan_reminder_${plan.id}")
        // 已归档的行程不再提醒；恢复时会重新调用本方法重建任务
        if (!plan.reminderEnabled || plan.archived) return
        val dep = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(plan.startDate)?.time
        }.getOrNull() ?: return
        val cal = Calendar.getInstance().apply {
            timeInMillis = dep
            add(Calendar.DAY_OF_YEAR, -plan.reminderOffsetDays)
            set(Calendar.HOUR_OF_DAY, plan.reminderHour)
            set(Calendar.MINUTE, plan.reminderMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val target = cal.timeInMillis
        val now = System.currentTimeMillis()
        if (target <= now) return // 已过期或当天出发，不提醒
        PlanReminderWorker.ensureChannels(context)
        val timeText = String.format(Locale.CHINA, "%02d:%02d", plan.reminderHour, plan.reminderMinute)
        val request = OneTimeWorkRequestBuilder<PlanReminderWorker>()
            .setInitialDelay(target - now, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(PlanReminderWorker.KEY_PLAN_ID, plan.id)
                    .putString(PlanReminderWorker.KEY_NAME, plan.name)
                    .putString(PlanReminderWorker.KEY_DEST, plan.destination)
                    .putString(PlanReminderWorker.KEY_DATE, plan.startDate)
                    .putString(PlanReminderWorker.KEY_TIME, timeText)
                    .putInt(PlanReminderWorker.KEY_OFFSET_DAYS, plan.reminderOffsetDays)
                    .putBoolean(PlanReminderWorker.KEY_VIBRATE, plan.reminderVibrate)
                    .putBoolean(PlanReminderWorker.KEY_SOUND, plan.reminderSound)
                    .build()
            )
            .build()
        wm.enqueueUniqueWork("plan_reminder_${plan.id}", ExistingWorkPolicy.REPLACE, request)
    }

    // ---------- 攻略互动 ----------

    /**
     * 纯本地模式：攻略是本地权威数据，任何改动马上落 Room，重启不丢。
     * commentsOf 非空时顺带把这条攻略的评论单独存一份（详情页按 id 精确读取）。
     */
    private fun persistGuidesLocal(commentsOf: Int? = null) {
        if (CloudConfig.ENABLED) return
        val snapshot = guides
        scope.launch {
            OfflineCache.saveGuides(context, snapshot)
            if (commentsOf != null) {
                snapshot.firstOrNull { it.id == commentsOf }?.let { g ->
                    OfflineCache.saveComments(context, commentsOf, g.commentList)
                }
            }
        }
    }

    fun toggleGuideLike(id: Int) {
        val target = guides.firstOrNull { it.id == id } ?: return
        val nextLiked = !target.liked
        guides = guides.map { g ->
            if (g.id == id) g.copy(liked = nextLiked, likes = g.likes + if (nextLiked) 1 else -1) else g
        }
        likedIds = if (nextLiked) likedIds + id else likedIds - id
        scope.launch { Persistence.saveGuideState(context, likedIds, favoredIds) }
        // 云端同步点赞（失败不回滚，本地状态为准）
        if (cloudLinked) scope.launch { BackendApi.toggleGuideLike(id) }
    }

    fun toggleGuideFav(id: Int) {
        val target = guides.firstOrNull { it.id == id } ?: return
        val nextFavored = !target.favored
        guides = guides.map { g ->
            if (g.id == id) g.copy(favored = nextFavored, favs = g.favs + if (nextFavored) 1 else -1) else g
        }
        favoredIds = if (nextFavored) favoredIds + id else favoredIds - id
        scope.launch { Persistence.saveGuideState(context, likedIds, favoredIds) }
        if (cloudLinked) scope.launch { BackendApi.toggleGuideFav(id) }
    }

    // ---------- 评论 / 楼中楼回复 ----------

    fun addComment(guideId: Int, text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        val comment = Comment(
            user = settings.nickname,
            time = "刚刚",
            content = content,
            likes = 0,
            id = commentSeq++
        )
        guides = guides.map { g ->
            if (g.id == guideId) g.copy(commentList = listOf(comment) + g.commentList) else g
        }
        persistGuidesLocal(guideId)
        // 云端发表成功后以服务端评论列表为准刷新
        if (cloudLinked) scope.launch {
            if (BackendApi.postComment(guideId, content, null) != null) {
                BackendApi.fetchComments(guideId)?.let { replaceComments(guideId, it) }
            }
        }
    }

    fun addReply(guideId: Int, commentId: Int, text: String, replyTo: String?) {
        val content = text.trim()
        if (content.isEmpty()) return
        val reply = CommentReply(
            user = settings.nickname,
            time = "刚刚",
            content = content,
            likes = 0,
            replyTo = replyTo?.takeIf { it.isNotBlank() },
            id = commentSeq++
        )
        guides = guides.map { g ->
            if (g.id == guideId) {
                g.copy(commentList = g.commentList.map { c ->
                    if (c.id == commentId) c.copy(replies = c.replies + reply) else c
                })
            } else g
        }
        persistGuidesLocal(guideId)
        if (cloudLinked) scope.launch {
            if (BackendApi.postComment(guideId, content, commentId) != null) {
                BackendApi.fetchComments(guideId)?.let { replaceComments(guideId, it) }
            }
        }
    }

    /** 点赞评论；replyId 为 null 表示点赞主评论，否则点赞楼中楼回复。点了赞会顺带取消踩 */
    fun toggleCommentLike(guideId: Int, commentId: Int, replyId: Int? = null) {
        guides = guides.map { g ->
            if (g.id != guideId) g else g.copy(commentList = g.commentList.map { c ->
                if (c.id != commentId) c else {
                    if (replyId == null) {
                        val liked = !c.liked
                        c.copy(
                            liked = liked,
                            likes = (c.likes + if (liked) 1 else -1).coerceAtLeast(0),
                            disliked = if (liked) false else c.disliked,
                            dislikes = if (liked && c.disliked) (c.dislikes - 1).coerceAtLeast(0) else c.dislikes
                        )
                    } else {
                        c.copy(replies = c.replies.map { r ->
                            if (r.id != replyId) r else {
                                val liked = !r.liked
                                r.copy(
                                    liked = liked,
                                    likes = (r.likes + if (liked) 1 else -1).coerceAtLeast(0),
                                    disliked = if (liked) false else r.disliked,
                                    dislikes = if (liked && r.disliked) (r.dislikes - 1).coerceAtLeast(0) else r.dislikes
                                )
                            }
                        })
                    }
                }
            })
        }
        persistGuidesLocal(guideId)
    }

    /** 踩评论；与点赞互斥，点了踩会顺带取消赞 */
    fun toggleCommentDislike(guideId: Int, commentId: Int, replyId: Int? = null) {
        guides = guides.map { g ->
            if (g.id != guideId) g else g.copy(commentList = g.commentList.map { c ->
                if (c.id != commentId) c else {
                    if (replyId == null) {
                        val disliked = !c.disliked
                        c.copy(
                            disliked = disliked,
                            dislikes = (c.dislikes + if (disliked) 1 else -1).coerceAtLeast(0),
                            liked = if (disliked) false else c.liked,
                            likes = if (disliked && c.liked) (c.likes - 1).coerceAtLeast(0) else c.likes
                        )
                    } else {
                        c.copy(replies = c.replies.map { r ->
                            if (r.id != replyId) r else {
                                val disliked = !r.disliked
                                r.copy(
                                    disliked = disliked,
                                    dislikes = (r.dislikes + if (disliked) 1 else -1).coerceAtLeast(0),
                                    liked = if (disliked) false else r.liked,
                                    likes = if (disliked && r.liked) (r.likes - 1).coerceAtLeast(0) else r.likes
                                )
                            }
                        })
                    }
                }
            })
        }
        persistGuidesLocal(guideId)
    }

    /** 删除自己的评论（长按自身评论触发） */
    fun deleteComment(guideId: Int, commentId: Int) {
        guides = guides.map { g ->
            if (g.id == guideId) g.copy(commentList = g.commentList.filterNot { it.id == commentId }) else g
        }
        persistGuidesLocal(guideId)
    }

    /** 删除自己的回复（长按自身回复触发） */
    fun deleteReply(guideId: Int, commentId: Int, replyId: Int) {
        guides = guides.map { g ->
            if (g.id == guideId) {
                g.copy(commentList = g.commentList.map { c ->
                    if (c.id == commentId) c.copy(replies = c.replies.filterNot { it.id == replyId }) else c
                })
            } else g
        }
        persistGuidesLocal(guideId)
    }

    // ---------- 天气 ----------

    /** 切换天气城市（天气页与发现页顶部共用同一份状态） */
    fun setWeatherCity(name: String, lat: Double, lon: Double) {
        // 城市变了先清空旧城市数据，避免短暂显示上一座城市的温度
        if (name != weatherCity) weatherNow = null
        weatherCity = name
        weatherLat = lat
        weatherLon = lon
        refreshWeather(force = true)
    }

    /**
     * 刷新天气。
     * @param force 忽略 60s 节流（下拉刷新 / 手动切城市）
     * @param relocate 先用系统定位更新城市（已授权时）
     */
    fun refreshWeather(
        force: Boolean = false,
        relocate: Boolean = false,
        /** true = 主动搜一次实时定位（下拉刷新 / 刚授权），false = 只读缓存定位 */
        precise: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        if (!force && now - weatherFetchedAt < 60_000L) return
        if (weatherJob?.isActive == true) return

        weatherJob = scope.launch {
            weatherLoading = true
            if (relocate) {
                // 天气只按城市取值：用缓存定位就够，不必为它等十几秒搜星把整页卡在加载态
                runCatching { WeatherApi.locateCity(context, allowLive = precise) }.getOrNull()?.let { hit ->
                    weatherCity = hit.name.ifBlank { weatherCity }
                    weatherLat = hit.lat
                    weatherLon = hit.lon
                    if (weatherNow?.city != weatherCity) weatherNow = null
                }
            }
            runCatching { WeatherApi.fetch(weatherCity, weatherLat, weatherLon) }
                .onSuccess {
                    weatherNow = it
                    weatherError = null
                    // 同步写入小组件缓存并刷新天气卡，保证桌面组件和 App 数据一致
                    runCatching {
                        val p = context.getSharedPreferences("my_widget_prefs", android.content.Context.MODE_PRIVATE)
                        p.edit()
                            .putString("w_city", it.city)
                            .putInt("w_temp", it.temp)
                            .putString("w_desc", it.desc)
                            .putString("w_emoji", it.emoji)
                            .putLong("w_time", System.currentTimeMillis())
                            .apply()
                        com.example.my.widget.WidgetKit.refresh(context, com.example.my.widget.WeatherWidgetProvider::class.java)
                    }
                }
                .onFailure {
                    weatherError = it.message ?: "网络异常"
                }
            weatherLoading = false
            weatherFetchedAt = System.currentTimeMillis()
        }
    }

    // ---------- 路线 ----------

    fun toggleRoutePublic(id: Int) {
        routes = routes.map { r ->
            if (r.id == id) r.copy(isPublic = !r.isPublic) else r
        }
        val newState = routes.first { it.id == id }.isPublic
        routePublic = routePublic + (id to newState)
        scope.launch { Persistence.saveRoutePublic(context, routePublic) }
        scope.launch { OfflineCache.saveRoutes(context, routes) }
    }

    // ---------- 足迹 ----------

    /**
     * 新足迹 id：取当前最大 id + 1。
     * 原来用 footprints.size + 1 会撞号——删掉一条后 size 回退，新足迹复用已被删除的 id，
     * 批量删除时就会误删到别的记录。
     */
    fun nextFootprintId(): Int = (footprints.maxOfOrNull { it.id } ?: 0) + 1

    fun addFootprint(fp: Footprint) {
        footprints = footprints + fp
        userFootprints = userFootprints + fp
        // 城市数只在出现新城市时 +1：原来每条足迹都 +1，
        // 在同一城市连记几条就会把「去过 N 座城市」刷虚高
        val newCity = fp.city.isNotBlank() && footprints.none { it !== fp && it.city == fp.city }
        user = user.copy(
            footprintCount = user.footprintCount + 1,
            citiesCount = user.citiesCount + if (newCity) 1 else 0
        )
        recomputeMedals()
        scope.launch {
            OfflineCache.saveFootprints(context, footprints)
            com.example.my.FootprintWidgetProvider.refresh(context)
        }
        // 纯本地模式：顺手落一张"旅程"卡，回忆/年度报告/旅程列表才有内容
        if (!CloudConfig.ENABLED) {
            ensureLocalRouteFor(fp)
        } else if (cloudLinked) scope.launch {
            val year = Regex("\\d{4}").find(fp.date)?.value?.toIntOrNull()
                ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val route = Route(
                id = 0,
                title = fp.name,
                year = year,
                date = fp.date,
                days = 1,
                cities = listOf(fp.city),
                coverSeed = fp.seed,
                special = fp.special,
                specialReason = fp.specialReason,
                note = fp.note,
                tags = emptyList(),
                totalCost = "",
                isPublic = settings.defaultPublic,
                photoCount = 0,
                itinerary = listOf(DayPlan(1, fp.name, listOf(Spot(fp.name, fp.note, "", seed = fp.seed))))
            )
            val createdId = BackendApi.createRoute(route)
            if (createdId == null) {
                UploadQueue.enqueue(context, PendingUploadWorker.TYPE_ROUTE, routeToJson(route).toString())
                toast("已保存本地，联网后自动同步")
            } else {
                // 云端行程落回本地列表并写 SQLite：离线也能在「回忆卡堆」看到自己的行程
                val saved = route.copy(id = createdId)
                routes = (routes + saved).distinctBy { it.id }
                OfflineCache.saveRoutes(context, routes)
            }
        }
        // 云端同步：新增足迹（含照片/视频）后合并上传；开关关闭或未登录时是空操作
        scheduleCloudSync()
    }

    /**
     * 用户手动编辑一条足迹：标题、城市、日期、备注、特别标记与照片。
     * 只改这一条，不重算 id / 坐标 / 路线；照片文件在保存时才清理旧图，
     * 编辑过程中取消不会动原图。
     */
    fun updateFootprint(updated: Footprint) {
        val old = footprints.firstOrNull { it.id == updated.id } ?: return
        footprints = footprints.map { if (it.id == updated.id) updated else it }
        userFootprints = userFootprints.map { if (it.id == updated.id) updated else it }
        old.photoPaths.filterNot { it in updated.photoPaths }.forEach { path ->
            runCatching { File(path).delete() }
        }
        // 编辑里换了或移除了视频 / 配乐：旧文件不再被任何记录引用，更新成功后再删
        val oldVideos = (old.videoPaths.ifEmpty { listOfNotNull(old.videoPath) })
        oldVideos.filterNot { it in updated.videoPaths }.forEach { path ->
            runCatching { File(path).delete() }
        }
        old.videoPath?.let { path ->
            if (path != updated.videoPath && oldVideos.size <= 1) runCatching { File(path).delete() }
        }
        old.bgmPath?.let { path -> if (path != updated.bgmPath) runCatching { File(path).delete() } }
        user = user.copy(
            footprintCount = footprints.size,
            citiesCount = footprints.map { it.city }.filter { it.isNotBlank() }.distinct().size
        )
        recomputeMedals()
        scope.launch {
            OfflineCache.saveFootprints(context, footprints)
            com.example.my.FootprintWidgetProvider.refresh(context)
        }
        // 云端同步：编辑（换了标题/照片/视频等）后重新上传这一条
        scheduleCloudSync()
    }

    /**
     * 纯本地模式的"旅程卡"：一条足迹对应一张卡。
     * 同城 + 同日 + 同名视为同一次出行，直接复用已有的卡 ——
     * 否则用户在一个城市连传 5 条足迹，回忆页会堆出 5 张一模一样的卡。
     */
    private fun ensureLocalRouteFor(fp: Footprint) {
        val exists = routes.any { r ->
            (r.date == fp.date && r.title == fp.name && r.cities.firstOrNull() == fp.city) ||
                // 单日、零照片的卡就是「足迹镜像卡」：同标题同城市的镜像已经有了一张，
                // 就别再补第二张。否则同一次出行换个日期保存一次，回忆页就多一张没照片的重复卡
                (r.days == 1 && r.photoCount == 0 && r.title == fp.name &&
                    r.cities.firstOrNull() == fp.city)
        }
        if (exists) return
        val year = Regex("\\d{4}").find(fp.date)?.value?.toIntOrNull()
            ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val route = Route(
            id = (routes.maxOfOrNull { it.id } ?: 0) + 1,
            title = fp.name,
            year = year,
            date = fp.date,
            days = 1,
            cities = listOf(fp.city),
            coverSeed = fp.seed,
            special = fp.special,
            specialReason = fp.specialReason,
            note = fp.note,
            tags = emptyList(),
            totalCost = "",
            isPublic = settings.defaultPublic,
            photoCount = 0,
            itinerary = listOf(DayPlan(1, fp.name, listOf(Spot(fp.name, fp.note, "", seed = fp.seed))))
        )
        routes = routes + route
        scope.launch { OfflineCache.saveRoutes(context, routes) }
    }

    // ---------- 自动轨迹记录 ----------

    var trackingOn by mutableStateOf(false)
        private set

    /** 当前记录中的轨迹点（GCJ-02）；持久化在 TrackRecorder，进程被杀也不丢 */
    var trackPoints by mutableStateOf(TrackRecorder.load(context))
        private set

    /**
     * 上次记录是否被异常中断（进程被系统回收 / 崩溃）。
     * 此时点还在，但采点早已停止——必须让 UI 明确提示，不能假装还在记录。
     */
    var trackInterrupted by mutableStateOf(false)
        private set

    /** 最近一次定位的精度（米）、速度（km/h）、收到的时刻（elapsedRealtime），给记录面板显示 */
    var trackAccuracy by mutableStateOf<Float?>(null)
        private set
    var trackSpeedKmh by mutableStateOf(0.0)
        private set
    var trackLastFixElapsed by mutableStateOf(0L)
        private set

    /** 启动记录失败的原因（Locator.REASON_*），成功后清空 */
    var trackError by mutableStateOf<String?>(null)
        private set

    /**
     * 灰色地带的候选点：位移够不上「确定移动」但明显不是噪声。
     * 下一次上报仍在附近才认定为真的走了，单次 GPS 抖动进不来。
     */
    private var pendingTrackPoint: TrackPoint? = null

    /** 落盘节流：距上次落盘累计的点数与时间戳 */
    private var trackSinceSave = 0
    private var trackLastSaveAt = 0L
    private var trackSaveJob: Job? = null

    /**
     * 每次放弃/结束记录自增，用来作废还在路上的后台落盘任务，
     * 否则「清空」之后迟到的写入会把点又写回磁盘。
     */
    @Volatile
    private var trackEpoch = 0

    /** 落盘互斥：保证「清空」不会被在途的后台写入覆盖回来 */
    private val trackSaveLock = Any()

    /**
     * 放在轨迹字段之后：Kotlin 的属性初始化按声明顺序执行，这里能安全读到 trackPoints。
     *  - TrackService 还在跑 = 只是 Activity 重建（旋转），直接接管现有会话，
     *    由单例内部先 stop 旧 listener，不会出现新旧两个 listener 并存的泄漏；
     *  - 磁盘上记着「正在记录」但单例没跑 = 进程被杀，保留点但绝不自动恢复采点
     *    （后台无定位权限，自动恢复不可靠），只标记中断交给 UI 处理。
     */
    init {
        if (TrackService.isRunning) {
            // 服务可能在后台独立记账（进程被杀后系统把它重新拉起），
            // 先收编它手上的点（落盘 + 关闸），再从磁盘读回来接管，防止两个写入方打架
            TrackWriter.deactivate(context)
            trackPoints = TrackRecorder.load(context)
            trackingOn = TrackService.start(context) { appendTrackPoint(it) }
            // 服务还在跑说明只是 UI 重建：把常驻通知一起接上，不能让记录处在「无保护」状态
            if (trackingOn) TrackForegroundService.start(context, trackNotificationText())
        }
        if (!trackingOn) {
            trackInterrupted = TrackRecorder.isRecording(context) && trackPoints.isNotEmpty()
        }
    }

    /**
     * 开始记录轨迹：GPS + 网络定位，5 秒采一点，实时画在地图上。
     *
     * 失败不是笼统的「开不了」，[trackError] 会区分「没给权限」和「系统定位关着」，
     * 页面据此给出可执行的提示（去设置开哪个开关）。
     */
    fun startTracking(): Boolean {
        if (trackingOn) return true
        trackError = Locator.problem(context)
        if (trackError != null) return false
        // 防止服务侧的独立记账还在跑：先收编再自己注册监听
        TrackWriter.deactivate(context)
        if (!TrackService.start(context) { appendTrackPoint(it) }) {
            trackError = if (Locator.isLocationOn(context)) Locator.REASON_TIMEOUT else Locator.REASON_SERVICE_OFF
            return false
        }
        trackingOn = true
        trackInterrupted = false
        trackError = null
        pendingTrackPoint = null
        trackSinceSave = 0
        trackLastSaveAt = System.currentTimeMillis()
        trackLastFixElapsed = 0L
        // 清掉上一次会话残留的精度/速度，否则刚点「开启」就显示上一次的旧读数
        trackAccuracy = null
        trackSpeedKmh = 0.0
        // 「记录中」标记落盘：若之后进程被杀，重启时就能发现上次没正常结束
        TrackRecorder.setRecording(context, true)
        // 前台服务：锁屏 / 切后台时系统不会因为「后台无定位」把采点掐掉
        TrackForegroundService.start(context, trackNotificationText())
        return true
    }

    /** 暂停采点：点保留，落盘一次；会话标记置 false（正常收尾） */
    fun stopTracking() {
        TrackService.stop()
        TrackForegroundService.stop(context)
        trackingOn = false
        pendingTrackPoint = null
        trackSpeedKmh = 0.0
        trackAccuracy = null
        flushTrackSave(force = true)
        TrackRecorder.setRecording(context, false)
    }

    /** 放弃本次记录（不清已生成的足迹） */
    fun clearTrack() {
        stopTracking()
        trackPoints = emptyList()
        discardPersistedPoints()
    }

    /** 立即把当前轨迹点落盘（离开足迹页时调用，避免节流窗口内丢点） */
    fun flushTrack() {
        if (trackPoints.isEmpty()) return
        flushTrackSave(force = true)
    }

    fun trackDistanceKm(): Double = TrackRecorder.distanceKm(trackPoints)

    /**
     * 本次记录已持续多久（秒）。
     *
     * 起点取第一个轨迹点的系统时间；记录中按当前时间实时增长，结束后就定格在
     * 最后一个点的时间——这样「结束」之后回头看统计卡，时长不会继续往上跑。
     */
    fun trackDurationSeconds(): Long {
        val first = trackPoints.firstOrNull() ?: return 0L
        val end = if (trackingOn) System.currentTimeMillis() else trackPoints.last().time
        return ((end - first.time) / 1000L).coerceAtLeast(0L)
    }

    /**
     * 估算消耗（千卡）。
     *
     * 没有运动类型与体重输入时只能给粗估：按 60kg 体重、步行约 0.5 kcal/kg/km，
     * 取每公里 30 千卡。数值仅作参考，界面上也标了「估算」。
     */
    fun trackCalories(): Int = (trackDistanceKm() * 30.0).toInt()

    /**
     * LocationListener 回调（主线程）：去重 → 转换坐标 → 精度自适应过滤 → 追加 → 惰性落盘。
     *
     * 几个容易踩的点都在这里处理：
     *  - GPS 与网络定位同时开启，同一位置会来两条，先由 [TrackService.shouldDispatch] 按
     *    provider 优先级丢掉粗糙的网络点；
     *  - 系统有时会补发几十秒前的缓存点，直接收会把线画出一段「瞬移」，按 elapsedRealtime 挡掉；
     *  - 手机静止时 GPS 仍会持续抖动，按精度自适应的阈值判断「真动了没有」。
     */
    private fun appendTrackPoint(location: Location) {
        if (!TrackService.shouldDispatch(location)) return
        val lat = location.latitude
        val lon = location.longitude
        if (lat == 0.0 && lon == 0.0) return
        // 陈旧补发点：不是当前时刻的位置，收进来会让轨迹倒退
        val fixElapsed = location.elapsedRealtimeNanos
        if (fixElapsed > 0L) {
            val ageMs = (SystemClock.elapsedRealtimeNanos() - fixElapsed) / 1_000_000
            if (ageMs > 60_000) return
        }
        val time = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        val accuracy = if (location.hasAccuracy()) location.accuracy else 0f
        // 记录面板要实时反映「现在定位准不准、在不在动」
        trackAccuracy = accuracy.takeIf { it > 0f }
        if (location.hasSpeed()) trackSpeedKmh = (location.speed * 3.6).coerceIn(0.0, 300.0)
        trackLastFixElapsed = SystemClock.elapsedRealtime()

        val (gcjLat, gcjLon) = TrackRecorder.wgs84ToGcj02(lat, lon)
        val candidate = TrackPoint(gcjLat, gcjLon, time, accuracy)
        // 系统速度是判断「真的在动」最可靠的证据，交给 decide 一起看
        val speedMps = if (location.hasSpeed()) location.speed else null
        when (TrackRecorder.decide(trackPoints.lastOrNull(), candidate, pendingTrackPoint, speedMps)) {
            TrackRecorder.Decision.ACCEPT -> {
                pendingTrackPoint = null
                trackPoints = TrackRecorder.appendPoint(trackPoints, candidate)
                scheduleTrackSave()
            }
            // 灰色地带先挂起：下一次上报仍在附近才认定真的移动了
            TrackRecorder.Decision.CONFIRM -> pendingTrackPoint = candidate
            TrackRecorder.Decision.REJECT -> Unit
        }
    }

    /** 距上一次收到定位有多少秒；用于提示「GPS 信号弱」而不是假装还在记录 */
    fun trackIdleSeconds(): Long {
        val ref = trackLastFixElapsed
        if (ref <= 0L) return if (trackingOn) -1 else 0
        return ((SystemClock.elapsedRealtime() - ref) / 1000).coerceAtLeast(0)
    }

    /** 常驻通知文案：走了多远、多少个点 */
    private fun trackNotificationText(): String {
        val km = TrackRecorder.distanceKm(trackPoints)
        return if (Lang.isEn) {
            "Recording · %.2f km · %d pts".format(km, trackPoints.size)
        } else {
            "正在记录 · %.2f km · %d 个点".format(km, trackPoints.size)
        }
    }

    /** 节流：每累计 5 个点或距上次落盘超过 30 秒才写一次盘（全量 JSON 序列化很贵） */
    private fun scheduleTrackSave() {
        trackSinceSave++
        val now = System.currentTimeMillis()
        if (trackSinceSave < 5 && now - trackLastSaveAt < 30_000L) return
        flushTrackSave(force = false)
    }

    /** 后台落盘；force = true 表示不管节流窗口，立即写（暂停/结束/离开页面时用） */
    private fun flushTrackSave(force: Boolean) {
        if (!force && trackPoints.isEmpty()) return
        val snapshot = trackPoints
        trackSinceSave = 0
        trackLastSaveAt = System.currentTimeMillis()
        val epoch = trackEpoch
        // 落盘的同时刷新常驻通知，节奏一致，也不用为每个点单独 notify；
        // 服务没跑起来时不能 notify，否则会留下一条划不掉的常驻通知
        if (trackingOn && TrackForegroundService.isRunning()) {
            TrackForegroundService.update(context, trackNotificationText())
        }
        trackSaveJob?.cancel()
        trackSaveJob = scope.launch(Dispatchers.IO) {
            synchronized(trackSaveLock) {
                if (epoch == trackEpoch) TrackRecorder.save(context, snapshot)
            }
        }
    }

    /** 清除磁盘上的点；先作废在途写入，避免刚清空又被写回来 */
    private fun discardPersistedPoints() {
        trackSaveJob?.cancel()
        trackSinceSave = 0
        synchronized(trackSaveLock) {
            trackEpoch++
            TrackRecorder.clear(context)
        }
    }

    /** 结束行程：一次记录存成一条路线足迹（见 finishTrackAsRoute） */

    /**
     * 结束路线记录，整趟存成**一条**足迹。
     *
     * 和旧的「按 400 米抽成十几条足迹」不同：一次记录就是一趟行程。起点坐标即足迹坐标，
     * 完整轨迹存进 route，详情页能还原整条线、起点终点和途中标记。
     *
     * 照片异步复制（起点 00-start.jpg、途中标记 m1.jpg…、终点 99-end.jpg）：
     * 解码压缩一张 1280px 的照片要几十到几百毫秒，同步做会把「保存」按钮卡住。
     * 复制完成后回填路径，即便失败也只丢照片，足迹本身照常保存。
     */
    fun finishTrackAsRoute(
        title: String,
        startPhotoUri: android.net.Uri?,
        endPhotoUri: android.net.Uri?,
        marks: List<PendingRouteMark>
    ): Boolean {
        val points = trackPoints
        if (points.isEmpty()) {
            clearTrack()
            return false
        }
        val first = points.first()
        val last = points.last()
        val (x, y) = ChinaMapData.lonLatToMap(first.lon, first.lat)
        val id = nextFootprintId()
        val distanceKm = TrackRecorder.distanceKm(points)
        val durationSec = ((last.time - first.time) / 1000L).coerceAtLeast(0L)
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.CHINA)
        val startedAt = Date(first.time)
        val endedAt = Date(last.time)

        val fp = Footprint(
            id = id,
            // 城市占位，反查成功后异步回填（与旧的批量转足迹同一套流程）
            city = TRACK_PLACEHOLDER_CITY,
            name = title.trim().ifBlank { "自动记录 · ${timeFmt.format(startedAt)}" },
            note = buildString {
                append(timeFmt.format(startedAt)).append(" → ").append(timeFmt.format(endedAt))
                append(" · ").append(String.format(Locale.CHINA, "%.2f", distanceKm)).append(" km")
                append(" · 用时 ").append(formatTrackDuration(durationSec))
                if (marks.isNotEmpty()) append(" · 标记 ").append(marks.size).append(" 处")
            },
            date = dateFmt.format(startedAt),
            x = x,
            y = y,
            dateMillis = first.time,
            lat = first.lat,
            lon = first.lon,
            route = points.map { RoutePoint(it.lat, it.lon, it.time) },
            distanceKm = distanceKm,
            durationSec = durationSec
        )
        addFootprint(fp)
        stopTracking()
        trackPoints = emptyList()
        discardPersistedPoints()
        backfillTrackCities(listOf(id to first))

        scope.launch {
            val startPath = startPhotoUri?.let { PhotoStore.copyOne(context, id, it, "00-start.jpg") }
            val endPath = endPhotoUri?.let { PhotoStore.copyOne(context, id, it, "99-end.jpg") }
            val savedMarks = marks.mapIndexed { index, mark ->
                RouteMark(
                    lat = mark.lat,
                    lon = mark.lon,
                    note = mark.note,
                    photoPath = mark.photoUri?.let {
                        PhotoStore.copyOne(context, id, it, "m${index + 1}.jpg")
                    },
                    time = mark.time
                )
            }
            // 画廊顺序 = 起点 → 途中标记 → 终点，详情页第一张永远是起点
            val photos = listOfNotNull(startPath) +
                    savedMarks.mapNotNull { it.photoPath } +
                    listOfNotNull(endPath)

            fun Footprint.withRoute(): Footprint =
                if (this.id == id) copy(marks = savedMarks, photoPaths = photos) else this
            footprints = footprints.map { it.withRoute() }
            userFootprints = userFootprints.map { it.withRoute() }
            OfflineCache.saveFootprints(context, footprints)
        }
        return true
    }

    /** 秒 → 「1:30:05」/「30:05」，与轨迹面板的秒表格式保持一致 */
    private fun formatTrackDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.CHINA, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.CHINA, "%02d:%02d", m, s)
    }

    /** 异步补城市：失败（无 Key / 无网络 / 无结果）静默降级，保持占位文案 */
    private fun backfillTrackCities(created: List<Pair<Int, TrackPoint>>) {
        if (created.isEmpty()) return
        scope.launch {
            created.forEach { (id, p) ->
                val city = AmapApi.reverseGeocode(p.lat, p.lon) ?: return@forEach
                applyFootprintCity(id, city)
            }
        }
    }

    /**
     * 从相册选一批照片，读 EXIF GPS 自动生成足迹。
     * 每张有 GPS 的照片生成一条足迹，坐标 WGS-84 → GCJ-02，城市名异步反查。
     * 返回成功创建的足迹数（无 GPS 的照片跳过）。
     */
    fun importPhotosAsFootprints(uris: List<android.net.Uri>): Int {
        if (uris.isEmpty()) return 0
        val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
        val created = mutableListOf<Pair<Int, DoubleArray>>()
        var count = 0

        for (uri in uris) {
            val (wgsLat, wgsLon, shotAt) = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val exif = androidx.exifinterface.media.ExifInterface(input)
                    val latLong = FloatArray(2)
                    if (!exif.getLatLong(latLong)) return@use Triple(0.0, 0.0, 0L)
                    val raw = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME)
                    var t: Long = 0L
                    if (!raw.isNullOrBlank()) {
                        t = runCatching {
                            java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.CHINA)
                                .parse(raw)?.time ?: 0L
                        }.getOrDefault(0L)
                    }
                    Triple(latLong[0].toDouble(), latLong[1].toDouble(), t)
                } ?: continue
            }.getOrNull() ?: continue

            if (wgsLat == 0.0 && wgsLon == 0.0) continue
            val (gcjLat, gcjLon) = TrackRecorder.wgs84ToGcj02(wgsLat, wgsLon)
            val (x, y) = ChinaMapData.lonLatToMap(gcjLon, gcjLat)
            val date = if (shotAt > 0L) dateFmt.format(java.util.Date(shotAt))
            else dateFmt.format(java.util.Date())

            val fp = Footprint(
                id = nextFootprintId(),
                city = "定位中…",
                name = "照片足迹",
                note = "从相册导入 · ${uris.size} 张照片",
                date = date,
                x = x,
                y = y,
                dateMillis = shotAt.takeIf { it > 0L },
                lat = gcjLat,
                lon = gcjLon
            )
            addFootprint(fp)
            created += fp.id to doubleArrayOf(gcjLat, gcjLon)
            count++
        }

        // 异步反查城市名
        if (created.isNotEmpty()) {
            scope.launch {
                created.forEach { (id, coord) ->
                    val city = AmapApi.reverseGeocode(coord[0], coord[1]) ?: return@forEach
                    applyFootprintCity(id, city)
                }
            }
        }
        return count
    }

    /** 把反查到的城市写回指定足迹（只替换仍是占位的），并同步 SQLite 与城市计数 */
    private fun applyFootprintCity(id: Int, city: String) {
        if (city.isBlank()) return
        fun Footprint.withCity(): Footprint =
            if (this.id == id && this.city == TRACK_PLACEHOLDER_CITY) copy(city = city) else this
        val updated = footprints.map { it.withCity() }
        if (updated == footprints) return
        footprints = updated
        userFootprints = userFootprints.map { it.withCity() }
        user = user.copy(citiesCount = footprints.map { it.city }.distinct().count())
        scope.launch { OfflineCache.saveFootprints(context, footprints) }
    }

    // ---------- 足迹删除：软删除进回收站，3 天内可恢复 ----------

    /** 回收站保留时长：3 天 */
    private val trashRetentionMs get() = 3L * 24 * 60 * 60 * 1000

    /**
     * 软删除：把选中足迹从列表移到回收站，照片保留。
     * 所有页面（网格/时间轴/地图）立即不再显示；可在设置里 3 天内恢复。
     */
    fun deleteFootprints(ids: Set<Int>) {
        if (ids.isEmpty()) return
        val removed = footprints.filter { it.id in ids }
        if (removed.isEmpty()) {
            toast("没有可删除的足迹")
            return
        }
        val now = System.currentTimeMillis()
        val newEntries = removed.map { Persistence.TrashedEntry(it, now) }
        footprints = footprints.filter { it.id !in ids }
        userFootprints = userFootprints.filter { it.id !in ids }
        trashedFootprints = (trashedFootprints + newEntries).sortedByDescending { it.trashedAt }
        user = user.copy(footprintCount = (user.footprintCount - removed.size).coerceAtLeast(0))
        // 连体的「旅程镜像卡」一起清掉：
        // 每保存一条足迹都会 ensureLocalRouteFor 补一张同标题/日期的单日旅程卡，
        // 以前删足迹不碰 routes，卡就留在回忆页里——没照片、和已有卡重复，而且当时根本没法删。
        val removedKeys = removed.map { Triple(it.name, it.date, it.city) }.toSet()
        val stillBacked = footprints.map { Triple(it.name, it.date, it.city) }.toSet()
        val orphanMirrors = routes.filter { r ->
            r.days == 1 && r.itinerary.size <= 1 &&
                Triple(r.title, r.date, r.cities.firstOrNull().orEmpty()) in removedKeys &&
                Triple(r.title, r.date, r.cities.firstOrNull().orEmpty()) !in stillBacked
        }
        if (orphanMirrors.isNotEmpty()) {
            routes = routes.filterNot { r -> r.id in orphanMirrors.map { it.id }.toSet() }
            scope.launch { OfflineCache.saveRoutes(context, routes) }
        }
        recomputeMedals()
        scope.launch {
            OfflineCache.saveFootprints(context, footprints)
            Persistence.saveTrash(context, trashedFootprints)
        }
        // 云端同步：删除会进服务端删除队列，下次同步用 deleted_client_ids 告知
        CloudSync.markDeleted(context, ids)
        scheduleCloudSync()
        toast(trashDeletedToast(removed.size))
    }

    /**
     * 删除旅程卡。
     *
     * 回忆页网格里的「旅程」卡（尤其是本地自动生成的单日镜像卡）以前完全没有删除入口，
     * 足迹删掉了、卡还在，就变成一堆没照片的重复卡。这里单独给路线一条删除路径，
     * 只动 routes，照片仍归足迹所有，不会被删。
     */
    fun deleteRoutes(ids: Set<Int>) {
        if (ids.isEmpty()) return
        val targets = routes.filter { it.id in ids }
        if (targets.isEmpty()) return
        routes = routes.filterNot { it.id in ids }
        scope.launch { OfflineCache.saveRoutes(context, routes) }
        CloudSync.markRoutesDeleted(context, ids)
        scheduleCloudSync()
        toast("已删除 ${targets.size} 张旅程卡")
    }

    /** 从回收站恢复：移回足迹列表，照片还在 */
    fun restoreFromTrash(ids: Set<Int>) {
        if (ids.isEmpty()) return
        val restored = trashedFootprints.filter { it.fp.id in ids }.map { it.fp }
        if (restored.isEmpty()) return
        trashedFootprints = trashedFootprints.filterNot { it.fp.id in ids }
        footprints = (footprints + restored).sortedBy { it.id }
        userFootprints = (userFootprints + restored).sortedBy { it.id }
        user = user.copy(footprintCount = user.footprintCount + restored.size)
        recomputeMedals()
        scope.launch {
            OfflineCache.saveFootprints(context, footprints)
            Persistence.saveTrash(context, trashedFootprints)
        }
        // 从回收站恢复：重新上传这一条（之前删除时已进过服务端删除队列）
        scheduleCloudSync()
    }

    /** 彻底删除：从回收站移除并删除照片目录，不可恢复 */
    fun deleteForever(ids: Set<Int>) {
        if (ids.isEmpty()) return
        val gone = trashedFootprints.filter { it.fp.id in ids }
        if (gone.isEmpty()) return
        trashedFootprints = trashedFootprints.filterNot { it.fp.id in ids }
        gone.forEach { PhotoStore.deleteDir(context, it.fp.id) }
        scope.launch { Persistence.saveTrash(context, trashedFootprints) }
    }

    /** 清空回收站（彻底删除全部） */
    fun emptyTrash() {
        val gone = trashedFootprints
        if (gone.isEmpty()) return
        gone.forEach { PhotoStore.deleteDir(context, it.fp.id) }
        trashedFootprints = emptyList()
        scope.launch { Persistence.saveTrash(context, trashedFootprints) }
    }

    /** 启动时加载回收站并清掉超过 3 天的条目（连照片一起删） */
    private suspend fun loadAndPruneTrash() {
        val loaded = Persistence.loadTrash(context)
        val cutoff = System.currentTimeMillis() - trashRetentionMs
        val expired = loaded.filter { it.trashedAt < cutoff }
        val alive = loaded.filterNot { it.trashedAt < cutoff }
        expired.forEach { PhotoStore.deleteDir(context, it.fp.id) }
        trashedFootprints = alive.sortedByDescending { it.trashedAt }
        if (expired.isNotEmpty()) Persistence.saveTrash(context, alive)
    }

    private fun trashDeletedToast(n: Int): String =
        if (com.example.my.ui.i18n.Lang.isEn) "Moved to trash ($n). Restore within 3 days in Settings."
        else "已移入回收站（$n 条），3 天内可在设置里恢复"

    // —— 以下为兼容旧调用点保留：回收站取代了 5 秒撤销，状态恒为空 ——
    var pendingDeleted by mutableStateOf(emptyList<Footprint>())
        private set
    var pendingDeleteToken by mutableStateOf(0L)
        private set
    private var pendingDeletedLocal: List<Footprint> = emptyList()
    private var deleteJob: kotlinx.coroutines.Job? = null

    @Deprecated("回收站取代了即时撤销", ReplaceWith("restoreFromTrash / deleteForever"))
    fun undoDeleteFootprints() {}

    fun commitPendingDelete() {
        deleteJob?.cancel(); deleteJob = null
        pendingDeleted = emptyList(); pendingDeletedLocal = emptyList(); pendingDeleteToken = 0L
    }

    companion object {
        /** 轨迹转足迹时城市反查完成前的占位文案（反查失败也保持它） */
        const val TRACK_PLACEHOLDER_CITY = "自动记录"

        /** 曲海网页搜索前缀，歌名按 UTF-8 逐字节 %XX 接在后面 */
        private const val SONG_SEARCH_BASE = "https://www.gequhai.com/search?s="
        private const val HEX_DIGITS = "0123456789ABCDEF"

        /** AI 悬浮球开关的持久化 Key */
        private const val KEY_AI_BALL = "ai_ball_enabled"
        private const val KEY_PROFILE_BG = "profile_bg_index"
        private const val KEY_PLAN_CUSTOM_ORDER = "plan_custom_order"

        /** 主题模式：0=跟随系统，1=浅色，2=深色 */
        private const val KEY_THEME_MODE = "theme_mode"

        /** 界面语言（zh / en）。也供 MainActivity 在 setContent 之前读取，避免首帧闪中文 */
        const val KEY_LANGUAGE = "app_language"

        /** 触感反馈档位（follow / on / off），默认跟随系统 */
        private const val KEY_HAPTIC_MODE = "haptic_mode"

        /** 新建足迹是否使用抖音式新版拍摄页（默认关闭，保留旧版系统相机入口） */
        private const val KEY_UPLOAD_STUDIO_CAPTURE = "upload_studio_capture"
    private const val KEY_LAST_GUIDE = "last_viewed_guide_id"

        /** 行程结束后多少天自动归档 */
        const val AUTO_ARCHIVE_DAYS = 30
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }

    // ---------- AI 能力：发布 / 指令 / 足迹 ----------

    private var guideSeq = 10000
    private var fpSeq = 1000

    /** 把 AI 生成的攻略蓝图真正加入发现页（置顶），返回新 id */
    fun publishGuide(bp: GuideBlueprint): Int {
        val guide = buildGuide(bp, guideSeq++)
        guides = listOf(guide) + guides
        toast("攻略《${guide.title.take(12)}》已发布到发现页")
        // 纯本地模式：攻略直接写 Room，重启还在
        if (!CloudConfig.ENABLED) {
            persistGuidesLocal()
            return guide.id
        }
        // 云端发布：成功后用服务端 id 替换本地临时 id；失败进入待传队列
        if (cloudLinked) scope.launch {
            val remoteId = BackendApi.createGuide(guide)
            if (remoteId != null) {
                guides = guides.map { g -> if (g.id == guide.id) g.copy(id = remoteId) else g }
            } else {
                UploadQueue.enqueue(context, PendingUploadWorker.TYPE_GUIDE, guideToJson(guide).toString())
                toast("已保存本地，联网后自动同步")
            }
        }
        return guide.id
    }

    /** AI 蓝图 → Guide 数据模型 */
    private fun buildGuide(bp: GuideBlueprint, id: Int): Guide {
        val tips = mutableListOf<String>()
        bp.hotels.forEach { tips += "住宿 ${it.first}：${it.second}" }
        bp.transports.forEach { tips += "交通 ${it.first}：${it.second}" }
        bp.foods.forEach { tips += "餐饮 ${it.first}：${it.second}" }
        val costText = if (bp.costs.isNotEmpty()) {
            bp.costs.joinToString("；") { "${it.first} ${it.second}" }
        } else "按需支出"

        return Guide(
            id = id,
            title = bp.title.ifBlank { "我的旅行攻略" },
            city = bp.city.ifBlank { "未知城市" },
            days = bp.days.size.coerceAtLeast(1),
            type = bp.type.ifBlank { "城市漫游" },
            author = settings.nickname,
            authorIntro = "AI 助手帮你整理的旅行攻略",
            likes = 0,
            favs = 0,
            cost = costText,
            coverSeed = (Math.abs(bp.city.hashCode()) % 10),
            tags = bp.tags.ifEmpty { listOf(bp.type.ifBlank { "城市漫游" }) },
            intro = bp.intro.ifBlank { "由 MY足迹 AI 助手自动生成。" },
            costBreakdown = bp.costs.map { CostItem(it.first, it.second) },
            tips = if (tips.isEmpty()) listOf("点击查看完整行程安排") else tips,
            itinerary = bp.days.map { d ->
                DayPlan(
                    day = d.day,
                    title = d.title.ifBlank { "第 ${d.day} 天" },
                    spots = d.spots.map { Spot(name = it.name, note = it.note, cost = "") }
                )
            },
            commentList = emptyList(),
            coverResId = null,
            // 本地模式：自己的攻略用本地作者 id 标识（-99），演示数据是 -1，不能混
            authorId = if (CloudConfig.ENABLED) (BackendApi.remoteUserId ?: -1) else CloudConfig.LOCAL_AUTHOR_ID
        )
    }

    /**
     * 执行 AI 识别出的自然语言指令（页面导航 / 天气 / 设置 / 攻略操作 / 行程计划）。
     *
     * 仅允许能力网关（AgentRuntime / CapabilityRegistry）内部调用：所有 AI 动作必须先过
     * 网关的 check（参数校验、模式过滤、确认分级），再由这里落地，最后写回执。UI 层不要直接调用。
     */
    @GateOnly
    fun applyCommand(intent: CommandIntent) {
        when (intent.kind) {
            "weather_city" -> scope.launch {
                val hit = runCatching { AmapApi.searchCity(intent.target) }.getOrNull()?.firstOrNull()
                if (hit != null) {
                    setWeatherCity(hit.name, hit.lat, hit.lon)
                    toast("天气城市已切换到 ${hit.name}")
                } else {
                    toast("没找到城市：${intent.target}")
                }
            }
            "weather_query" -> scope.launch {
                val hit = runCatching { AmapApi.searchCity(intent.target) }.getOrNull()?.firstOrNull()
                if (hit != null) {
                    setWeatherCity(hit.name, hit.lat, hit.lon)
                    openScreen(ExtraScreen.WEATHER)
                } else {
                    toast("没找到城市：${intent.target}")
                }
            }
            "setting" -> {
                val on = intent.value != "false"
                when (intent.target) {
                    "defaultPublic" -> updateSettings(defaultPublic = on)
                    "notifEnabled" -> updateSettings(notifEnabled = on)
                    "shareEnabled" -> updateSettings(shareEnabled = on)
                    "ai_ball" -> updateAiBallEnabled(on)
                    "voice_input" -> updateAiCapabilities { it.copy(voiceInput = on) }
                    "voice_tts" -> updateAiCapabilities { it.copy(voiceTts = on) }
                    else -> toast(intent.label)
                }
                toast("已${if (on) "开启" else "关闭"}：${when(intent.target){
                    "defaultPublic"->"默认公开";"notifEnabled"->"消息通知";"shareEnabled"->"内容分享"
                    "ai_ball"->"AI悬浮球";"voice_input"->"语音输入";"voice_tts"->"语音播报";else->intent.target
                }}")
            }
            "logout" -> {
                logout("已退出登录")
            }
            "exit_app" -> {
                // 退出应用交由 UI 层（持有 Activity）处理，这里只做标记
                toast("正在退出应用…")
            }
            "delete_trip" -> {
                val key = intent.target.trim()
                val hit = tripPlans.firstOrNull {
                    it.name.contains(key, true) || it.destination.contains(key, true)
                }
                if (hit == null) toast("没找到与「$key」相关的行程")
                else { deleteTripPlan(hit.id); toast("已删除行程《${hit.name}》") }
            }
            "edit_footprint" -> {
                val key = intent.target.trim()
                val newNote = intent.value.trim()
                val hit = footprints.firstOrNull {
                    it.name.contains(key, true) || it.city.contains(key, true)
                }
                when {
                    hit == null -> toast("没找到与「$key」相关的足迹")
                    newNote.isBlank() -> toast("没有提供新的文案")
                    else -> {
                        footprints = footprints.map {
                            if (it.id == hit.id) it.copy(note = newNote) else it
                        }
                        userFootprints = userFootprints.map {
                            if (it.id == hit.id) it.copy(note = newNote) else it
                        }
                        toast("已更新「${hit.name}」的文案")
                    }
                }
            }
            "set_chat_mode" -> {
                // 不再有「混合 / 本地 / API 模式」：这条指令就是换对话框当前用的模型。
                // hybrid 是旧协议遗留的说法，等同选中当前云端端点。
                val useLocal = intent.target.equals("local", true)
                runMode = if (useLocal) 2 else 1
                localLlmState = com.example.my.data.ai.LocalLlm.state(context)
                toast(
                    if (useLocal) "已切换到本地模型：${com.example.my.data.ai.LocalLlm.activeSpec(context).display}"
                    else "已切换到云端模型：${currentEndpoint.name}"
                )
            }
            "set_local_model" -> {
                val key = intent.target.trim()
                val spec = LocalLlm.CATALOG.firstOrNull {
                    it.display.contains(key, true) || it.id.contains(key, true)
                }
                when {
                    spec == null -> toast("没找到模型：$key")
                    !LocalLlm.modelDownloaded(context, spec.id) ->
                        toast("「${spec.display}」还没下载，不能切换")
                    else -> {
                        LocalLlm.setActive(context, spec.id)
                        localLlmState = LocalLlm.state(context)
                        toast("已切换本地模型：${spec.display}")
                    }
                }
            }
            "language" -> {
                aiLanguage = if (intent.target == "en") "en" else "zh"
                toast(
                    if (aiLanguage == "en") "已识别：AI 将用英文回复（界面翻译后续上线）"
                    else "已切回中文（界面翻译后续上线）"
                )
            }
            "settings_page" -> openSettings(intent.target.trim().ifBlank { "main" })
            "theme" -> {
                val mode = when (intent.target.trim().lowercase()) {
                    "light" -> 1
                    "dark" -> 2
                    else -> 0
                }
                updateThemeMode(mode)
                toast(
                    when (mode) {
                        1 -> "已切到浅色模式"
                        2 -> "已切到深色模式"
                        else -> "外观已跟随系统"
                    }
                )
            }
            "haptic" -> {
                val mode = when (intent.target.trim().lowercase()) {
                    "on" -> Haptics.ON
                    "off" -> Haptics.OFF
                    else -> Haptics.FOLLOW
                }
                updateHapticMode(mode)
                toast(
                    when (mode) {
                        Haptics.ON -> "触感反馈已开启"
                        Haptics.OFF -> "触感反馈已关闭"
                        else -> "触感反馈已跟随系统"
                    }
                )
            }
            "app_language" -> {
                val code = if (intent.target.trim().equals("en", true)) "en" else "zh"
                updateLanguage(code)
                toast(if (code == "en") "Interface language: English" else "界面语言已切回中文")
            }
            "open" -> when (intent.target) {
                "settings" -> openSettings(null)
                "weather" -> openScreen(ExtraScreen.WEATHER)
                "notifications" -> openScreen(ExtraScreen.NOTIFICATIONS)
                "messages" -> openScreen(ExtraScreen.MESSAGES)
                "guide_create" -> openScreen(ExtraScreen.GUIDE_CREATE)
                "my_plans" -> openScreen(ExtraScreen.MY_PLANS)
                "year_report" -> openScreen(ExtraScreen.YEAR_REPORT)
                "ticket_zone" -> openScreen(ExtraScreen.TICKET_ZONE)
                "shopping" -> openScreen(ExtraScreen.SHOPPING)
                "privacy" -> openScreen(ExtraScreen.PRIVACY_VAULT)
                "news" -> openScreen(ExtraScreen.NEWS)
                "stock" -> openScreen(ExtraScreen.STOCK)
                "trade" -> openScreen(ExtraScreen.TRADE)
                "creative" -> openScreen(ExtraScreen.CREATIVE)
                "music" -> openScreen(ExtraScreen.MUSIC)
                "course" -> openScreen(ExtraScreen.COURSE)
                "travelers" -> openScreen(ExtraScreen.TRAVELER_RANK)
                "web_search" -> openScreen(ExtraScreen.WEB_SEARCH)
                "ai" -> openScreen(ExtraScreen.AI_ASSISTANT)
                "upload" -> { closeScreen(); openUpload() }
                "offline" -> { activeTab = AppTab.MAP; requestMapPacks(); closeScreen() }
                "trips" -> { activeTab = AppTab.TRIPS; closeScreen() }
                "map" -> { activeTab = AppTab.MAP; footprintMode = 0; closeScreen() }
                "memory" -> { activeTab = AppTab.MAP; footprintMode = 1; closeScreen() }
                "profile" -> { activeTab = AppTab.PROFILE; closeScreen() }
                "discover" -> { activeTab = AppTab.DISCOVER; closeScreen() }
                else -> toast(intent.label)
            }
            "create_plan" -> { closeScreen(); openPlanSheet() }
            "open_guide", "search_guide", "like_guide", "fav_guide" -> {
                val key = intent.target.trim()
                val matches = guides.filter {
                    it.title.contains(key, true) || it.city.contains(key, true) ||
                        it.tags.any { t -> t.contains(key, true) } || it.type.contains(key, true)
                }
                when {
                    matches.isEmpty() -> toast("没找到与「$key」相关的攻略")
                    intent.kind == "search_guide" -> {
                        toast("找到 ${matches.size} 篇相关攻略，已打开最匹配的一篇")
                        closeScreen(); openGuide(matches.first().id)
                    }
                    intent.kind == "open_guide" -> { closeScreen(); openGuide(matches.first().id) }
                    intent.kind == "like_guide" -> {
                        val g = matches.first()
                        // 真正 toggle：之前没点赞→点上，之前已点赞→取消（可逆，无需确认）
                        toggleGuideLike(g.id)
                        toast(
                            if (!g.liked) "已点赞《${g.title}》，再说一次可取消"
                            else "已取消点赞《${g.title}》"
                        )
                    }
                    else -> {
                        val g = matches.first()
                        toggleGuideFav(g.id)
                        toast(
                            if (!g.favored) "已收藏《${g.title}》，再说一次可取消"
                            else "已取消收藏《${g.title}》"
                        )
                    }
                }
            }
            else -> toast(intent.label)
        }
    }

    /**
     * AI 从对话中提取的行程草稿 → 写入行程列表 → 跳到行程页。
     *
     * 「导入我的计划」此前只是模型嘴上说说，没有任何代码真正写数据，
     * 行程页自然是空的；现在由这里完成真正的落库（addTripPlan 会持久化 + 排提醒）。
     */
    fun applyTripBlueprint(bp: AiTripBlueprint) {
        val today = java.time.LocalDate.now().toString()
        val name = bp.title.ifBlank { "${bp.destination}${bp.days.size}日行程" }
        addTripPlan(
            name = name,
            destination = bp.destination,
            startDate = bp.startDate.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) } ?: today,
            days = bp.days.size.coerceAtLeast(1),
            note = bp.summary,
            dailyPlan = bp.days.map { d -> "D${d.day}｜${d.title}：" + d.items.joinToString(" / ") },
            customPacking = bp.packing
        )
        toast("行程《$name》已加入行程列表")
        activeTab = AppTab.TRIPS
        closeScreen()
    }

    /** AI 提取的足迹草稿 → 补全坐标 → 加入足迹地图 */
    fun applyFootprintBlueprint(bp: FootprintBlueprint) {
        scope.launch {
            var lat = bp.lat
            var lon = bp.lon
            if ((lat == null || lon == null) && bp.city.isNotBlank()) {
                runCatching { AmapApi.searchCity(bp.city) }.getOrNull()
                    ?.firstOrNull()?.let { lat = it.lat; lon = it.lon }
            }
            val (x, y) = if (lat != null && lon != null) {
                ChinaMapData.lonLatToMap(lon, lat)
            } else {
                (ChinaMapData.MAP_MIN_X + ChinaMapData.MAP_MAX_X) / 2f to
                    (ChinaMapData.MAP_MIN_Y + ChinaMapData.MAP_MAX_Y) / 2f
            }
            val fp = Footprint(
                id = fpSeq++,
                city = bp.city.ifBlank { "未知" },
                name = bp.name.ifBlank { bp.city.ifBlank { "我的足迹" } },
                note = bp.note.ifBlank { "AI 帮你记录的足迹" },
                date = bp.date.ifBlank { "最近" },
                x = x,
                y = y,
                seed = (Math.abs(bp.city.hashCode()) % 10),
                lat = lat,
                lon = lon
            )
            addFootprint(fp)
        }
    }

    // ---------- 通知 ----------

    fun markNotificationRead(id: Int) {
        notifications = notifications.map { n ->
            if (n.id == id && !n.read) n.copy(read = true) else n
        }
    }

    fun markAllNotificationsRead() {
        notifications = notifications.map { n -> if (!n.read) n.copy(read = true) else n }
    }

    fun clearNotifications() {
        notifications = emptyList()
    }

    // ---------- 本地数据：自动快照 / 恢复 ----------

    /** 上一次快照的数据指纹，用来跳过"什么都没改"的重复写盘 */
    private var lastSnapshotSig: String? = null

    /** 最近一次本地快照时间戳（0 = 还没备份过），设置页展示用 */
    var lastBackupAt by mutableStateOf(0L)
        private set

    /** 最近几份本地快照（最新在前），设置页直接读 */
    fun localSnapshots(): List<LocalBackup.Snapshot> = LocalBackup.list(context)

    /** 快照占用空间（字节） */
    fun backupBytes(): Long = LocalBackup.bytesOf(context)

    /**
     * 退到后台时自动快照。数据没变化就跳过，避免每次切后台都写一遍磁盘。
     * 只备份用户自己产生的数据（足迹/行程/计划/自己发布的攻略）。
     */
    fun autoSnapshot() {
        if (CloudConfig.ENABLED) return
        val fp = footprints
        val rt = routes
        val plans = tripPlans
        val mine = guides.filter { it.authorId == CloudConfig.LOCAL_AUTHOR_ID }
        val sig = buildString {
            append(fp.size).append('/').append(rt.size).append('/').append(plans.size)
            append('/').append(mine.size)
            append('/').append(fp.sumOf { it.id.toLong() })
        }
        if (sig == lastSnapshotSig) return
        lastSnapshotSig = sig
        scope.launch(Dispatchers.IO) {
            LocalBackup.save(context, fp, rt, plans, mine)?.let { f ->
                lastBackupAt = f.lastModified()
            }
        }
    }

    /** 从某份快照恢复；返回是否成功。恢复后立刻落盘，不需要等下次后台 */
    fun restoreSnapshot(file: File): Boolean {
        val payload = LocalBackup.read(file) ?: return false
        // 去重是防闪退的硬要求：LazyColumn 用 id 当 key，重复立即抛
        footprints = payload.footprints.distinctBy { it.id }
        routes = payload.routes.distinctBy { it.id }
        tripPlans = payload.plans.distinctBy { it.id }
        if (payload.guides.isNotEmpty()) {
            // 只替换"自己发布的攻略"，演示数据保持不动
            val others = guides.filter { it.authorId != CloudConfig.LOCAL_AUTHOR_ID }
            guides = (payload.guides + others).distinctBy { it.id }
        }
        userFootprints = footprints
        recomputeMedals()
        bumpLocalSequences()
        scope.launch {
            runCatching {
                OfflineCache.saveFootprints(context, footprints)
                OfflineCache.saveRoutes(context, routes)
                OfflineCache.saveGuides(context, guides)
                Persistence.saveTripPlans(context, tripPlans)
            }
        }
        return true
    }

    /** 立刻生成一份快照（用户手动点"立即备份"） */
    fun snapshotNow() {
        lastSnapshotSig = null
        autoSnapshot()
    }

    // ---------- 设置 ----------

    fun updateSettings(nickname: String? = null, signature: String? = null, defaultPublic: Boolean? = null, notifEnabled: Boolean? = null, shareEnabled: Boolean? = null, liquidGlass: Boolean? = null) {
        settings = settings.copy(
            nickname = nickname ?: settings.nickname,
            signature = signature ?: settings.signature,
            defaultPublic = defaultPublic ?: settings.defaultPublic,
            notifEnabled = notifEnabled ?: settings.notifEnabled,
            shareEnabled = shareEnabled ?: settings.shareEnabled,
            liquidGlass = liquidGlass ?: settings.liquidGlass
        )
        // 玻璃是纯本机渲染偏好，立刻作用到渲染层；不参与云端同步
        if (liquidGlass != null) com.example.my.ui.components.LiquidGlass.enabled = liquidGlass
        user = user.copy(
            nickname = settings.nickname,
            signature = settings.signature,
            avatarText = settings.nickname.take(1)
        )
        scope.launch {
            Persistence.saveSettings(context, nickname, signature, defaultPublic, notifEnabled, shareEnabled, liquidGlass)
        }
        // 云端同步资料与偏好
        if (cloudLinked) scope.launch {
            BackendApi.updateMe(
                nickname = nickname,
                signature = signature,
                defaultPublic = defaultPublic,
                notifEnabled = notifEnabled,
                shareEnabled = shareEnabled
            )
        }
        // 设置也作为一份快照推到 /api/sync/settings（换机恢复用）
        scheduleCloudSync()
    }

    /**
     * 设置用户自填的高德 Key：立即注入 AmapApi 生效，并落盘（下次冷启动自动恢复）。
     * 注意：不能叫 setAmapKey —— amapKey 是 public var，Kotlin 已经生成了同签名的 JVM setter，会撞。
     */
    fun updateAmapKey(key: String) {
        amapKey = key
        AmapApi.userKey = key.trim().takeIf { it.isNotBlank() }
        scope.launch { Persistence.saveAmapKey(context, key.trim()) }
    }

    /** 把已公开的路线生成攻略（演示：跳转发现 Tab + toast） */
    fun publishRouteAsGuide(routeId: Int) {
        val r = routes.firstOrNull { it.id == routeId }
        if (r == null) return
        activeTab = AppTab.DISCOVER
        toast("《${r.title}》已生成公开攻略")
    }
}

/** 全屏页（设置/通知/攻略发布） */
enum class ExtraScreen {
    NONE,
    SETTINGS,
    NOTIFICATIONS,
    GUIDE_CREATE,
    MY_PLANS,
    WEATHER,
    MESSAGES,
    USER_PROFILE,
    AI_ASSISTANT,
    YEAR_REPORT,
    OFFLINE,
    MUSIC,
    TRAVELER_RANK,
    WEB_SEARCH,
    COURSE,
    TICKET_ZONE,
    SHOPPING,
    PRIVACY_VAULT,
    NEWS,
    STOCK,
    TRADE,
    CREATIVE,
    DRIVING,
    /** 原足迹页（地图足迹 / 旅行回忆）从「我的」进入后的全屏承载页 */
    FOOTPRINTS
}

/** 给 mock 评论/回复补上稳定 id（点赞、回复、删除都靠它定位） */
private fun Comment.normalized(guideId: Int, index: Int): Comment {
    val cid = if (id != 0) id else guideId * 1000 + index + 1
    return copy(
        id = cid,
        replies = replies.mapIndexed { j, r ->
            r.copy(id = if (r.id != 0) r.id else cid * 100 + j + 1)
        }
    )
}

