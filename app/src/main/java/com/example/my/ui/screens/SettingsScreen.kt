package com.example.my.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.appwidget.AppWidgetManager
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.Build
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.my.R
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PermIdentity
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.FootprintWidgetProvider
import com.example.my.MusicWidgetProvider
import com.example.my.widget.CaptureWidgetProvider
import com.example.my.widget.CityMapWidgetProvider
import com.example.my.widget.CourseWidgetProvider
import com.example.my.widget.DepartureWidgetProvider
import com.example.my.widget.GuideWidgetProvider
import com.example.my.widget.MonthlyWidgetProvider
import com.example.my.widget.QuoteWidgetProvider
import com.example.my.widget.TodayWidgetProvider
import com.example.my.widget.WeatherWidgetProvider
import com.example.my.widget.WidgetKit
import com.example.my.state.AppState
import com.example.my.data.CrashLog
import com.example.my.data.ServerSearchPrefs
import com.example.my.data.crypto.SuperEncryption
import com.example.my.data.ai.LocalAiPolicy
import com.example.my.data.ai.LocalAiPrefs
import com.example.my.data.music.MusicExternal
import com.example.my.data.music.MusicPlayer
import com.example.my.data.music.MusicPrefs
import com.example.my.data.music.LyricsRepository
import com.example.my.data.music.SongStoryCache
import com.example.my.data.music.WebLyricsTranslate
import com.example.my.data.music.WebMusicCache
import com.example.my.util.DataBackup
import com.example.my.util.LocalBackup
import com.example.my.ui.components.AppTopBar
import com.example.my.ui.components.FeatureItem
import com.example.my.ui.components.FeatureSearchPill
import com.example.my.ui.components.appFeatureItems
import com.example.my.ui.components.BackActionRow
import com.example.my.ui.components.LiquidGlass
import com.example.my.ui.components.Avatar
import com.example.my.ui.components.PrimaryButton
import com.example.my.ui.interaction.ElasticSwitch
import androidx.compose.foundation.text.selection.SelectionContainer
import com.example.my.ui.i18n.Lang
import com.example.my.ui.i18n.tr
import com.example.my.ui.interaction.Haptics
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.AppBackground
import com.example.my.ui.theme.BorderLight
import com.example.my.ui.theme.Coral
import com.example.my.ui.theme.Func
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.Spacing
import com.example.my.ui.theme.Terracotta
import com.example.my.ui.theme.TextMuted
import com.example.my.ui.theme.TextPrimary
import com.example.my.ui.theme.TextSecondary
import com.example.my.ui.theme.WarmBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 设置页的层级：主列表只放大类，点进去才显示该类下的具体功能（微信/QQ 的做法） */
private enum class SettingsPage(val zh: String, val en: String) {
    MAIN("设置", "Settings"),
    ACCOUNT("账号与安全", "Account & Security"),
    DATA("数据与备份", "Data & Backup"),
    AI("AI 与语音", "AI & Voice"),
    AI_ROUTING("AI 模型分配", "AI model routing"),
    GENERAL("通用", "General"),
    ABOUT("关于", "About");

    val title get() = tr(zh, en)
}

/** 设置页：大类列表 + 二级功能页 */
@Composable
fun SettingsScreen(state: AppState) {
    val context = LocalContext.current
    var nickname by rememberSaveable { mutableStateOf(state.settings.nickname) }
    var signature by rememberSaveable { mutableStateOf(state.settings.signature) }
    var defaultPublic by rememberSaveable { mutableStateOf(state.settings.defaultPublic) }
    // 服务器网页索引（过渡功能）：抓取/清洗放服务端，模型仍在本机；关闭即纯本地检索
    var serverWebIndex by remember { mutableStateOf(ServerSearchPrefs.isEnabled()) }
    // 当前所在的设置页（主列表 / 某个大类）
    // AI 助手或能力网关跳过来时带的一次性目标（theme / trash / local_model …），优先于旧整数入口
    val initialTarget = remember { state.consumeSettingsInitialTarget() }
    var page by rememberSaveable {
        mutableStateOf(
            when (initialTarget) {
                "account", "profile", "super_crypto" -> SettingsPage.ACCOUNT
                "data", "backup", "trash" -> SettingsPage.DATA
                "ai", "local_model", "voice_library", "api_model", "local_runtime" -> SettingsPage.AI
                "ai_routing" -> SettingsPage.AI_ROUTING
                "general", "theme", "haptic", "language", "background", "widget" -> SettingsPage.GENERAL
                "about", "log", "privacy", "security" -> SettingsPage.ABOUT
                else -> when (state.consumeSettingsInitialPage()) {
                    1 -> SettingsPage.AI
                    else -> SettingsPage.MAIN
                }
            }
        )
    }
    // 崩溃日志对话框
    var showLogDialog by remember { mutableStateOf(initialTarget == "log") }
    // 隐私 / 安全说明
    var showSecurityDialog by remember { mutableStateOf(initialTarget == "security") }
    // 超级加密通信：开关状态与安全码说明
    var superEncrypted by remember { mutableStateOf(SuperEncryption.isEnabled(context)) }
    var showSuperCryptoDialog by remember { mutableStateOf(initialTarget == "super_crypto") }
    var showPrivacyDialog by remember { mutableStateOf(initialTarget == "privacy") }
    // 语言选择
    var showLangDialog by remember { mutableStateOf(initialTarget == "language") }
    // 触感反馈档位
    var showHapticDialog by remember { mutableStateOf(initialTarget == "haptic") }
    // 主题模式（跟随系统 / 浅色 / 深色）
    var showThemeDialog by remember { mutableStateOf(initialTarget == "theme") }
    // 全 App 自定义背景（独立整页，避免和设置二级页的返回逻辑打架）
    var showBackgroundPage by remember { mutableStateOf(initialTarget == "background") }
    // 本地快照（备份 / 恢复）
    var showBackupDialog by remember { mutableStateOf(initialTarget == "backup") }
    // 回收站
    var showTrashDialog by remember { mutableStateOf(initialTarget == "trash") }
    // 本地大模型管理
    var showAiModelDialog by remember { mutableStateOf(initialTarget == "local_model") }
    // 本地 AI 运行内存 / 上下文
    var showLocalAiRuntimeDialog by remember { mutableStateOf(initialTarget == "local_runtime") }
    // 语音库（可下载的声音包）
    var showVoiceLibraryDialog by remember { mutableStateOf(initialTarget == "voice_library") }
    var showProfileDialog by remember { mutableStateOf(initialTarget == "profile") }
    var modelImportProgress by remember { mutableStateOf<Int?>(null) }
    // API 模型管理（自定义端点）
    var showApiEndpointDialog by remember { mutableStateOf(initialTarget == "api_model") }
    // 桌面小组件 / 音乐播放通知
    var showWidgetDialog by remember { mutableStateOf(initialTarget == "widget") }
    var musicNotifEnabled by remember { mutableStateOf(MusicPrefs.notificationsEnabled(context)) }
    val settingsScope = rememberCoroutineScope()

    // 模型导入：系统目录选择器（模型约 1GB，让用户自己找私有目录不现实）
    val importModelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        modelImportProgress = 0
        settingsScope.launch {
            val ok = state.importLocalModel(uri) { p -> modelImportProgress = p }
            modelImportProgress = null
            state.toast(
                if (ok) tr("模型导入完成，本地对话已可用", "Model imported — local chat is ready")
                else tr("导入失败：请确认文件夹里有 config.json 等模型文件", "Import failed — make sure the folder contains config.json")
            )
        }
    }

    LaunchedEffect(Unit) { state.refreshLocalLlmState() }

    /**
     * 设置页已经开着时，AI 助手又说「打开设置里的回收站」——
     * 这种情况不会再重进组合，光靠初始化参数会「点了没反应」，所以这里再监听一次。
     */
    LaunchedEffect(state.settingsInitialTarget) {
        val t = state.consumeSettingsInitialTarget() ?: return@LaunchedEffect
        when (t) {
            "account", "profile" -> { page = SettingsPage.ACCOUNT; if (t == "profile") showProfileDialog = true }
            "super_crypto" -> { page = SettingsPage.ACCOUNT; showSuperCryptoDialog = true }
            "data", "backup", "trash" -> {
                page = SettingsPage.DATA
                when (t) {
                    "backup" -> showBackupDialog = true
                    "trash" -> showTrashDialog = true
                }
            }
            "ai", "local_model", "voice_library", "api_model", "local_runtime" -> {
                page = SettingsPage.AI
                when (t) {
                    "local_model" -> showAiModelDialog = true
                    "voice_library" -> showVoiceLibraryDialog = true
                    "api_model" -> showApiEndpointDialog = true
                    "local_runtime" -> showLocalAiRuntimeDialog = true
                }
            }
            "ai_routing" -> page = SettingsPage.AI_ROUTING
            "general", "theme", "haptic", "language", "background", "widget" -> {
                page = SettingsPage.GENERAL
                when (t) {
                    "theme" -> showThemeDialog = true
                    "haptic" -> showHapticDialog = true
                    "language" -> showLangDialog = true
                    "background" -> showBackgroundPage = true
                    "widget" -> showWidgetDialog = true
                }
            }
            "about" -> page = SettingsPage.ABOUT
            "log" -> { page = SettingsPage.ABOUT; showLogDialog = true }
            "privacy" -> { page = SettingsPage.ABOUT; showPrivacyDialog = true }
            "security" -> { page = SettingsPage.ABOUT; showSecurityDialog = true }
            "main" -> page = SettingsPage.MAIN
        }
    }

    // 头像选择器：选图后上传云端
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { state.uploadAvatar(it) }
    }

    // 二级页按返回键先回到大类列表，再按一次才退出设置
    BackHandler(enabled = page != SettingsPage.MAIN && !showBackgroundPage) { page = SettingsPage.MAIN }

    /**
     * 设置页的功能索引：本页的大类 + 藏在这一页里的对话框（都能直接打开），
     * 再并上全 App 的入口（点了先退出设置再跳，返回时不会掉回设置里）。
     */
    val searchItems = remember(state) {
        val local = listOf(
            FeatureItem("set_account", "设置", "Settings", "账号与安全", "Account & Security",
                "头像 · 昵称 · 签名 · 隐私", "Avatar · bio · privacy", "资料 昵称 头像 密码 隐私 权限") {
                page = SettingsPage.ACCOUNT
            },
            FeatureItem("set_super_crypto", "设置", "Settings", "超级加密通信", "Super encrypted chat",
                "端到端加密私信 · 安全码核对", "E2EE direct messages · safety code",
                "加密 超级 私信 端到端 安全码 密钥") {
                page = SettingsPage.ACCOUNT
                showSuperCryptoDialog = true
            },
            FeatureItem("set_data", "设置", "Settings", "数据与备份", "Data & Backup",
                "快照 · 导出 · 回收站", "Snapshots · export · trash", "备份 恢复 导出 数据 快照") {
                page = SettingsPage.DATA
            },
            FeatureItem("set_trash", "设置", "Settings", "回收站", "Recently deleted",
                "3 天内删除的足迹", "Deleted in last 3 days", "删除 恢复 还原 误删") {
                showTrashDialog = true
            },
            FeatureItem("set_backup", "设置", "Settings", "本地快照与恢复", "Snapshots & restore",
                "备份文件导出 / 导入", "Export or import a backup", "备份 恢复 导入 导出 json") {
                showBackupDialog = true
            },
            FeatureItem("set_ai", "设置", "Settings", "AI 与语音", "AI & Voice",
                "对话模型 · 视觉 · 离线语音", "Models · vision · offline voice", "模型 语音 识别 对话") {
                page = SettingsPage.AI
            },
            FeatureItem("set_ai_routing", "设置", "Settings", "AI 模型分配", "AI model routing",
                "哪个任务用哪个模型", "Which model handles what", "模型 分配 路由 视觉 语音") {
                page = SettingsPage.AI_ROUTING
            },
            FeatureItem("set_local_model", "设置", "Settings", "本地大模型管理", "Local model manager",
                "导入 / 删除本地模型", "Import or delete local models", "本地模型 离线 导入 gguf") {
                showAiModelDialog = true
            },
            FeatureItem("set_local_runtime", "设置", "Settings", "本地 AI 运行内存", "Local AI runtime",
                "上下文与内存占用", "Context and memory", "内存 上下文 本地 性能") {
                showLocalAiRuntimeDialog = true
            },
            FeatureItem("set_voice_lib", "设置", "Settings", "语音库", "Voice library",
                "可下载的声音包", "Downloadable voices", "声音 音色 下载 tts") {
                showVoiceLibraryDialog = true
            },
            FeatureItem("set_api", "设置", "Settings", "API 模型", "API models",
                "自定义端点与密钥", "Custom endpoint and key", "接口 密钥 endpoint 云端") {
                showApiEndpointDialog = true
            },
            FeatureItem("set_general", "设置", "Settings", "通用", "General",
                "语言 · 触感 · 缓存清理", "Language · haptics · cache", "通用 基础 偏好") {
                page = SettingsPage.GENERAL
            },
            FeatureItem("set_language", "设置", "Settings", "语言", "Language",
                "简体中文 / English", "Chinese / English", "中英 语言 english 切换") {
                showLangDialog = true
            },
            FeatureItem("set_theme", "设置", "Settings", "深色模式与主题", "Theme",
                "跟随系统 / 浅色 / 深色", "System / light / dark", "暗黑 夜间 主题 外观") {
                showThemeDialog = true
            },
            FeatureItem("set_haptic", "设置", "Settings", "触感反馈", "Haptics",
                "点击震动的强弱", "Tap vibration strength", "震动 手感 触感") {
                showHapticDialog = true
            },
            FeatureItem("set_background", "设置", "Settings", "自定义背景", "Custom background",
                "给全 App 换一张底图", "A background image app-wide", "背景 壁纸 底图 图片") {
                showBackgroundPage = true
            },
            FeatureItem("set_widget", "设置", "Settings", "桌面小组件", "Home widgets",
                "足迹 · 天气 · 语录卡片", "Trace · weather · quote", "小组件 桌面 卡片 通知") {
                showWidgetDialog = true
            },
            FeatureItem("set_profile", "设置", "Settings", "个人资料", "Profile",
                "头像 · 昵称 · 签名", "Avatar · nickname · bio", "头像 昵称 签名 资料") {
                showProfileDialog = true
            },
            FeatureItem("set_about", "设置", "Settings", "关于", "About",
                "版本号与更新", "Version and updates", "版本 关于 更新") {
                page = SettingsPage.ABOUT
            },
            FeatureItem("set_log", "设置", "Settings", "崩溃日志", "Crash logs",
                "排查闪退用", "For debugging crashes", "日志 崩溃 闪退 报错") {
                showLogDialog = true
            },
            FeatureItem("set_privacy", "设置", "Settings", "用户隐私政策", "Privacy policy",
                "收集了什么、怎么用", "What is collected and why", "隐私 政策 权限 数据") {
                showPrivacyDialog = true
            },
            FeatureItem("set_security", "设置", "Settings", "安全中心", "Security",
                "本地存储与换机说明", "Local storage and migration", "安全 加密 备份 换机") {
                showSecurityDialog = true
            }
        )
        // 全 App 入口：点之前先退出设置，免得返回时又掉回设置页
        val global = appFeatureItems(state)
            .filter { it.key != "settings" && it.key != "switch_account" }
            .map { item -> item.copy(action = { state.closeScreen(); item.action() }) }
        local + global
    }

    if (showBackgroundPage) {
        CustomBackgroundScreen(
            state = state,
            onBack = { showBackgroundPage = false },
            onApplied = {
                showBackgroundPage = false
                state.toast(tr("自定义背景已应用，正在同步到云端", "Background applied, syncing to cloud"))
                // 背景图与外观参数立即上云：换机登录后自动还原
                state.syncCloudNow()
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(WarmBg)) {
        AppTopBar(title = page.title)
        BackActionRow(
            onBack = {
                if (page == SettingsPage.MAIN) state.closeScreen() else page = SettingsPage.MAIN
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md)
        ) {
            // ---------- 设置主列表：只放大类 ----------
            if (page == SettingsPage.MAIN) {
                item {
                    Column {
                        FeatureSearchPill(items = searchItems)
                        Spacer(Modifier.height(Spacing.md))
                    }
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(Bg.Surface)
                    ) {
                        SettingsNavRow(
                            icon = Icons.Outlined.AccountCircle,
                            title = tr("账号与安全", "Account & Security"),
                            subtitle = tr("头像 · 昵称 · 签名 · 隐私与权限", "Avatar · bio · privacy"),
                            onClick = { page = SettingsPage.ACCOUNT }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.Storage,
                            title = tr("数据与备份", "Data & Backup"),
                            subtitle = tr(
                                "${state.footprints.size} 足迹 · ${state.routes.size} 旅程 · 快照与导出",
                                "${state.footprints.size} fp · ${state.routes.size} trips"
                            ),
                            onClick = { page = SettingsPage.DATA }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.AutoAwesome,
                            title = tr("AI 与语音", "AI & Voice"),
                            subtitle = tr(
                                "对话模型 · 视觉识别 · 离线语音",
                                "Chat model · Vision · Offline voice"
                            ),
                            onClick = { page = SettingsPage.AI }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.Settings,
                            title = tr("通用", "General"),
                            subtitle = tr(
                                "语言 · 触感反馈 · 缓存清理",
                                "Language · Haptics · Cache"
                            ),
                            onClick = { page = SettingsPage.GENERAL }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.Info,
                            title = tr("关于", "About"),
                            subtitle = tr("版本 v1.0.0 · 崩溃日志"),
                            onClick = { page = SettingsPage.ABOUT }
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(Spacing.xxl))
                    Text(
                        text = tr(
                            "MY足迹 · 每一步足迹都是独家回忆",
                            "MY足迹 · Every footprint is a memory of its own"
                        ),
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // ---------- 账号与安全：资料与隐私 ----------
            if (page == SettingsPage.ACCOUNT) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(Bg.Surface)
                            .clickable { showProfileDialog = true }
                            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(
                            text = nickname.take(1),
                            size = 56.dp,
                            imageUrl = state.user.avatarUrl
                        )
                        Spacer(Modifier.width(Spacing.lg))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = nickname,
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = signature.ifBlank { tr("点击设置头像、昵称与个性签名") },
                                color = TextMuted,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(text = "›", color = TextMuted, fontSize = 18.sp)
                    }
                }
            }

            // ---------- 账号与安全：超级加密通信 ----------
            if (page == SettingsPage.ACCOUNT) {
                item {
                    Spacer(Modifier.height(Spacing.xxl))
                    Text(
                        text = tr("安全通信", "Secure messaging"),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(Bg.Surface)
                    ) {
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Lock,
                            title = tr("超级加密通信", "Super encrypted chat"),
                            subtitle = if (superEncrypted) {
                                tr(
                                    "已开启：新文字消息端到端加密，服务器只存密文；图片/视频暂不支持",
                                    "On: new text messages are E2EE; photos/videos are not supported yet"
                                )
                            } else {
                                tr(
                                    "开启后私信正文只有你和对方能解开；历史消息不回溯加密",
                                    "Only you and the recipient can read new messages; history stays unchanged"
                                )
                            },
                            checked = superEncrypted,
                            onChecked = { on ->
                                if (on) {
                                    if (!com.example.my.data.BackendApi.loggedIn) {
                                        state.toast(tr("请先登录再开启", "Please sign in first"))
                                    } else if (SuperEncryption.enable(context)) {
                                        superEncrypted = true
                                        showSuperCryptoDialog = true
                                        state.toast(tr("超级加密已开启", "Super encryption enabled"))
                                    } else {
                                        state.toast(tr("加密引擎自检失败，未开启", "Crypto self-test failed"))
                                    }
                                } else {
                                    SuperEncryption.disable(context)
                                    superEncrypted = false
                                    state.toast(tr("已关闭：历史密文仍保留，新消息恢复明文", "Off: history remains, new messages are plain"))
                                }
                            }
                        )
                        if (superEncrypted) {
                            SettingsRowDivider()
                            SettingsNavRow(
                                icon = Icons.Outlined.Security,
                                title = tr("安全码与设备密钥", "Safety code & device key"),
                                subtitle = tr(
                                    "与对方当面核对同一串安全码，可发现中间人",
                                    "Compare the same safety code in person to detect a MITM"
                                ),
                                onClick = { showSuperCryptoDialog = true }
                            )
                        }
                    }
                }
            }

            // ---------- 数据与备份 ----------
            if (page == SettingsPage.DATA) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(Bg.Surface)
                    ) {
                        // 云端同步总开关：开着才连服务器，关掉就是彻底的本机模式
                        SettingsSwitchRow(
                            icon = if (state.cloudSyncEnabled) Icons.Outlined.Backup
                            else Icons.Outlined.CloudOff,
                            title = tr("云端同步", "Cloud sync"),
                            subtitle = if (state.cloudSyncEnabled) {
                                when {
                                    state.cloudSyncing -> tr("正在上传本机数据…", "Uploading…")
                                    state.cloudSyncError != null -> tr("上次失败：${state.cloudSyncError}")
                                    state.cloudLastSyncAt > 0 ->
                                        tr("已连接服务器 · 最近同步 ${backupAgo(state.cloudLastSyncAt)}",
                                            "Connected · synced ${backupAgo(state.cloudLastSyncAt)}")
                                    else -> tr("已连接服务器，等待首次上传", "Connected, waiting for first upload")
                                }
                            } else {
                                tr("已关闭：足迹、照片只保存在本机，不联网上传",
                                    "Off: data stays on this device only")
                            },
                            checked = state.cloudSyncEnabled,
                            onChecked = { state.setCloudSync(it) }
                        )
                        if (state.cloudSyncEnabled) {
                            SettingsRowDivider()
                            SettingsNavRow(
                                icon = Icons.Outlined.SwapHoriz,
                                title = tr("立即同步", "Sync now"),
                                subtitle = when {
                                    state.cloudSyncing -> tr("同步中…", "Syncing…")
                                    state.cloudSyncError != null ->
                                        tr("失败：${state.cloudSyncError}")
                                    state.cloudLastSyncAt > 0 ->
                                        tr("最近：${backupAgo(state.cloudLastSyncAt)}")
                                    else -> tr("还没同步过")
                                },
                                onClick = { state.syncCloudNow() }
                            )
                        }
                        SettingsRowDivider()
                        SettingsTextRow(
                            icon = Icons.Outlined.Storage,
                            title = tr("数据概览", "Overview"),
                            value = tr("${state.footprints.size} 足迹 · ${state.routes.size} 旅程")
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.Restore,
                            title = tr("本地快照", "Local snapshots"),
                            subtitle = if (state.lastBackupAt > 0) tr("最近：${backupAgo(state.lastBackupAt)}") else tr("还没有备份"),
                            onClick = { showBackupDialog = true }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.Backup,
                            title = tr("导出备份文件", "Export backup"),
                            subtitle = tr("导出为 JSON，可发送到微信/网盘"),
                            onClick = {
                                val ok = DataBackup.exportAndShare(context, state.footprints, state.routes, state.tripPlans)
                                if (!ok) state.toast(tr("导出失败", "Export failed"))
                            }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.DeleteOutline,
                            title = tr("回收站", "Trash"),
                            subtitle = if (state.trashedFootprints.isEmpty()) tr("空")
                            else tr("${state.trashedFootprints.size} 条 · 3 天后自动清除", "${state.trashedFootprints.size} items · auto-purged after 3 days"),
                            onClick = { showTrashDialog = true }
                        )
                    }
                }
            }

            // ---------- 账号与安全：隐私与权限 ----------
            if (page == SettingsPage.ACCOUNT) {
                item {
                    Spacer(Modifier.height(Spacing.xxl))
                    Text(text = tr("隐私与权限", "Privacy & Permissions"), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(Spacing.sm))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(Bg.Surface)
                    ) {
                        SettingsNavRow(
                            icon = Icons.Outlined.Security,
                            title = tr("安全中心"),
                            subtitle = tr("数据存储位置与本机安全说明"),
                            onClick = { showSecurityDialog = true }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.PrivacyTip,
                            title = tr("用户隐私政策"),
                            subtitle = tr("我们如何收集、使用与保护你的数据"),
                            onClick = { showPrivacyDialog = true }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.PermIdentity,
                            title = tr("权限管理"),
                            subtitle = tr("相册、位置、通知等系统权限"),
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        )
                    }
                }
            }

            // ---------- 账号与安全：默认可见范围 ----------
            if (page == SettingsPage.ACCOUNT) {
                item {
                    Spacer(Modifier.height(Spacing.xxl))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(Bg.Surface)
                    ) {
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Lock,
                            title = tr("新足迹默认公开"),
                            subtitle = tr("开启后新上传的足迹默认对所有人可见"),
                            checked = defaultPublic,
                            onChecked = { defaultPublic = it; state.updateSettings(defaultPublic = it) }
                        )
                    }
                }
            }

            // ---------- AI 与语音 ----------
            if (page == SettingsPage.AI) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(Bg.Surface)
                    ) {
                        // 模型只在 AI 助手对话框底部选：选哪个模型就用哪个模型。
                        // 「混合 / 本地模型 / API 模型」这类运行模式选项已移除，这里只显示当前模型并给入口。
                        SettingsNavRow(
                            icon = Icons.Outlined.AutoAwesome,
                            title = tr("当前对话模型", "Chat model"),
                            subtitle = tr(
                                "当前：${state.activeModelName}，点击管理云端端点",
                                "Now: ${state.activeModelName}. Tap to manage endpoints"
                            ),
                            onClick = { showApiEndpointDialog = true }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.SwapHoriz,
                            title = tr("AI 模型分配", "AI model routing"),
                            subtitle = tr(
                                if (state.aiRoutingSmart) "全局智能分配已开启"
                                else "按功能单独指定，或开启全局智能分配",
                                if (state.aiRoutingSmart) "Smart routing is on"
                                else "Assign per feature, or turn on smart routing"
                            ),
                            onClick = { page = SettingsPage.AI_ROUTING }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.Memory,
                            title = tr("本地模型管理", "On-device models"),
                            subtitle = tr("已占用 ${com.example.my.data.ai.LocalLlm.totalSizeMb(context)} MB", "${com.example.my.data.ai.LocalLlm.totalSizeMb(context)} MB used"),
                            onClick = { showAiModelDialog = true }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.Tune,
                            title = tr("本地 AI 运行设置", "On-device AI runtime"),
                            subtitle = tr(
                                "内存 ${LocalAiPolicy.gbLabel(LocalAiPrefs.memoryBudgetMb(context))} · 上下文 ${
                                    if (LocalAiPrefs.contextMode(context) == LocalAiPolicy.ContextMode.AUTO)
                                        "自动（4K–32K）"
                                    else "手动 ${LocalAiPolicy.label(LocalAiPrefs.manualContextTokens(context))}"
                                }",
                                "Memory ${LocalAiPolicy.gbLabel(LocalAiPrefs.memoryBudgetMb(context))} · context ${
                                    if (LocalAiPrefs.contextMode(context) == LocalAiPolicy.ContextMode.AUTO)
                                        "Auto (4K–32K)"
                                    else "Manual ${LocalAiPolicy.label(LocalAiPrefs.manualContextTokens(context))}"
                                }"
                            ),
                            onClick = { showLocalAiRuntimeDialog = true }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.Storage,
                            title = tr("API 模型管理", "API models"),
                            subtitle = tr(
                                if (state.hasCloudEndpoint) "当前：${state.currentEndpoint.name}" else "未接入，请自行添加 API 端点",
                                if (state.hasCloudEndpoint) "Now: ${state.currentEndpoint.name}" else "Not connected — add your API endpoint"
                            ),
                            onClick = { showApiEndpointDialog = true }
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(Spacing.xxl))
                    Text(text = tr("检索与证据", "Retrieval & evidence"), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(Spacing.sm))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(Bg.Surface)
                    ) {
                        SettingsSwitchRow(
                            icon = Icons.Outlined.AutoAwesome,
                            title = tr("边思考边查", "Look up while thinking"),
                            subtitle = tr(
                                "支持 tools 的 OpenAI 兼容云端模型可调用 lookup_evidence 按需查临时证据库；会多 1~3 轮请求。其他模型自动走预检索兜底。",
                                "OpenAI-compatible cloud models with tool support can call lookup_evidence; adds 1–3 rounds. Other models fall back to pre-retrieval."
                            ),
                            checked = state.aiCapabilities.evidenceToolLoop,
                            onChecked = { on ->
                                state.updateAiCapabilities { it.copy(evidenceToolLoop = on) }
                            }
                        )
                        SettingsRowDivider()
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Storage,
                            title = tr("MCP 搜索", "MCP search"),
                            subtitle = tr(
                                "支持 tools 的 OpenAI 兼容云端模型可调用 search_open_source_projects，按关键词/语言检索 GitHub 免费开源项目，返回 License、Star 和仓库地址。无需 Key；GitHub 公开检索有频率限制。",
                                "OpenAI-compatible cloud models with tool support can call search_open_source_projects to find free GitHub projects with a License, Stars and repo URL. No key needed; GitHub public search is rate-limited."
                            ),
                            checked = state.aiCapabilities.mcpSearch,
                            onChecked = { on ->
                                state.updateAiCapabilities { it.copy(mcpSearch = on) }
                            }
                        )
                        SettingsRowDivider()
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Article,
                            title = tr("服务器网页索引", "Server web index"),
                            subtitle = tr(
                                "过渡功能：开启后网页抓取与清洗由服务器完成，模型仍在本机；服务器不可用时自动回退本地检索。",
                                "Beta: when on, fetching and cleaning run on the server; the model stays on-device. Falls back to local search if the server is unreachable."
                            ),
                            checked = serverWebIndex,
                            onChecked = { on ->
                                serverWebIndex = on
                                ServerSearchPrefs.setEnabled(context, on)
                            }
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(Spacing.xxl))
                    Text(text = tr("视觉识别", "Vision"), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(Spacing.sm))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(Bg.Surface)
                    ) {
                        SettingsSwitchRow(
                            icon = Icons.Outlined.AutoAwesome,
                            title = tr("图像智能标签", "Smart tags"),
                            subtitle = tr("识别照片场景与物体", "Auto-tag scenes"),
                            checked = state.aiCapabilities.visionLabels,
                            onChecked = { on -> state.updateAiCapabilities { it.copy(visionLabels = on) } }
                        )
                        SettingsRowDivider()
                        SettingsSwitchRow(
                            icon = Icons.Outlined.DocumentScanner,
                            title = tr("OCR 文字识别", "OCR"),
                            subtitle = tr("读取票根、菜单、路牌；模型在 API 模型管理中接入", "Read tickets, menus, signs; connect a model in API models"),
                            checked = state.aiCapabilities.visionOcr,
                            onChecked = { on -> state.updateAiCapabilities { it.copy(visionOcr = on) } }
                        )
                        SettingsRowDivider()
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Face,
                            title = tr("人脸检测", "Face detection"),
                            subtitle = tr("统计合影人数，不存储特征", "Count people; no data stored"),
                            checked = state.aiCapabilities.visionFace,
                            onChecked = { on -> state.updateAiCapabilities { it.copy(visionFace = on) } }
                        )
                        SettingsRowDivider()
                        SettingsSwitchRow(
                            icon = Icons.Outlined.QrCodeScanner,
                            title = tr("条码 / 二维码", "Barcode / QR"),
                            subtitle = tr("识别门票、商品码", "Read ticket codes"),
                            checked = state.aiCapabilities.visionBarcode,
                            onChecked = { on -> state.updateAiCapabilities { it.copy(visionBarcode = on) } }
                        )
                        SettingsRowDivider()
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Translate,
                            title = tr("图文翻译", "Translate"),
                            subtitle = tr("端侧翻译，约 30MB 模型", "On-device, ~30MB"),
                            checked = state.aiCapabilities.visionTranslate,
                            onChecked = { on -> state.updateAiCapabilities { it.copy(visionTranslate = on) } }
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(Spacing.xxl))
                    Text(text = tr("语音输入与播报", "Voice"), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(Spacing.sm))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(Bg.Surface)
                    ) {
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Mic,
                            title = tr("语音输入", "Voice input"),
                            subtitle = tr("AI 助手支持说话输入", "Speak to AI assistant"),
                            checked = state.aiCapabilities.voiceInput,
                            onChecked = { on -> state.updateAiCapabilities { it.copy(voiceInput = on) } }
                        )
                        SettingsRowDivider()
                        SettingsSwitchRow(
                            icon = Icons.Outlined.VolumeUp,
                            title = tr("语音播报", "Voice TTS"),
                            subtitle = tr("朗读 AI 回复", "Read replies aloud"),
                            checked = state.aiCapabilities.voiceTts,
                            onChecked = { on ->
                                state.updateAiCapabilities { it.copy(voiceTts = on) }
                                if (!on) com.example.my.data.ai.VoiceIo.stop()
                            }
                        )
                        SettingsRowDivider()
                        SettingsSwitchRow(
                            icon = Icons.Outlined.CloudOff,
                            title = tr("仅离线语音", "Offline only"),
                            subtitle = tr("声音不出手机", "Audio stays on device"),
                            checked = state.aiCapabilities.voiceOfflineOnly,
                            onChecked = { on -> state.updateAiCapabilities { it.copy(voiceOfflineOnly = on) } }
                        )
                        SettingsRowDivider()
                        // 语音唤醒：只在前台监听，退后台立刻停，避免常驻耗电
                        val wakeMicPerm = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) { granted ->
                            if (granted) state.enableWakeWord(true)
                            else state.toast(
                                tr("没有录音权限，语音唤醒无法开启", "Microphone permission is required")
                            )
                        }
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Mic,
                            title = tr("语音唤醒", "Wake word"),
                            subtitle = tr(
                                "说「${state.wakeWordPhrase}」直接打开 AI 助手 · 需先下载下方离线模型，仅前台监听",
                                "Say “${state.wakeWordPhrase}” to open AI · needs the offline model, foreground only"
                            ),
                            checked = state.wakeWordEnabled,
                            onChecked = { on ->
                                val granted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                when {
                                    !on -> state.enableWakeWord(false)
                                    // 没有离线模型就开不了：先提示去下面下载，开关保持关闭
                                    !com.example.my.data.VoskRecognizer(context).hasModel() ->
                                        state.toast(
                                            tr(
                                                "先下载下方「离线语音模型」，语音唤醒才能离线工作",
                                                "Download the offline model below first"
                                            )
                                        )
                                    !granted -> wakeMicPerm.launch(Manifest.permission.RECORD_AUDIO)
                                    else -> state.enableWakeWord(true)
                                }
                            }
                        )
                        SettingsRowDivider()
                        SettingsField(
                            label = tr("唤醒词", "Wake word"),
                            value = state.wakeWordPhrase,
                            onChange = { state.updateWakePhrase(it) },
                            hint = com.example.my.data.WakeWord.DEFAULT_PHRASE,
                            maxLines = 1
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.VolumeUp,
                            title = tr("语音库 / 声音包", "Voice library"),
                            subtitle = tr(
                                "下载、试听与切换音色 · 当前：${com.example.my.data.ai.VoiceLibrary.activePack(context).zh}",
                                "Download, preview & switch voices · Now: ${com.example.my.data.ai.VoiceLibrary.activePack(context).en}"
                            ),
                            onClick = { showVoiceLibraryDialog = true }
                        )
                        SettingsRowDivider()
                        // Vosk 离线语音模型下载
                        val vosk = remember { com.example.my.data.VoskRecognizer(context) }
                        var voskPct by remember { mutableStateOf(-1) }
                        var voskStatus by remember {
                            mutableStateOf(if (vosk.hasModel()) tr("已下载 · 离线识别可用", "Downloaded — ready")
                            else tr("未下载 · 约 42MB", "Not downloaded · ~42MB"))
                        }
                        SettingsNavRow(
                            icon = Icons.Outlined.Download,
                            title = tr("离线语音模型（Vosk）", "Offline speech model"),
                            subtitle = voskStatus,
                            onClick = {
                                if (vosk.hasModel()) {
                                    state.toast(tr("离线语音模型已就绪", "Offline model ready"))
                                } else if (voskPct >= 0) {
                                    // 正在下载：再点一下没有意义，只提示当前进度
                                    state.toast(tr("正在下载 ${voskPct}%，请稍等", "Downloading ${voskPct}%…"))
                                } else {
                                    voskPct = 0
                                    voskStatus = tr("准备下载…", "Preparing…")
                                    vosk.downloadModel(
                                        onProgress = { pct ->
                                            voskPct = pct
                                            voskStatus = tr("下载中 $pct%（约 42MB）", "Downloading $pct% (~42MB)")
                                        },
                                        onDone = { ok ->
                                            voskPct = -1
                                            voskStatus = if (ok) tr("已下载 · 离线识别可用", "Downloaded — ready")
                                            else tr(
                                                "下载失败：${vosk.lastError ?: "未知原因"} · 点击重试",
                                                "Failed: ${vosk.lastError ?: "unknown"} — tap to retry"
                                            )
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // ---------- AI 模型分配 ----------
            if (page == SettingsPage.AI_ROUTING) {
                item { AiModelAssignPanel(state) }
            }

            // ---------- 通用：外观与交互 ----------
            if (page == SettingsPage.GENERAL) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(Bg.Surface)
                    ) {
                        // 语言放在通用第一项：这是切换后要立刻验证的地方
                        SettingsNavRow(
                            icon = Icons.Outlined.Translate,
                            title = tr("语言 / Language"),
                            subtitle = Lang.displayName,
                            onClick = { showLangDialog = true }
                        )
                        SettingsRowDivider()
                        // 触感反馈：默认跟随系统，静音模式下不震
                        SettingsNavRow(
                            icon = Icons.Outlined.Vibration,
                            title = tr("触感反馈", "Haptics"),
                            subtitle = hapticLabel(state.hapticMode),
                            onClick = { showHapticDialog = true }
                        )
                        SettingsRowDivider()
                        // 深色模式：跟随系统 / 浅色 / 深色，即时换肤
                        SettingsNavRow(
                            icon = Icons.Outlined.DarkMode,
                            title = tr("深色模式", "Dark mode"),
                            subtitle = when (state.themeMode) {
                                1 -> tr("浅色", "Light")
                                2 -> tr("深色", "Dark")
                                else -> tr("跟随系统", "Follow system")
                            },
                            onClick = { showThemeDialog = true }
                        )
                        SettingsRowDivider()
                        // 全 App 自定义背景：相册图片 / 调色 / 预设色，含高亮色与文字色
                        SettingsNavRow(
                            icon = Icons.Outlined.PhotoCamera,
                            title = tr("自定义背景", "Custom background"),
                            subtitle = AppBackground.summary(),
                            onClick = { showBackgroundPage = true }
                        )
                        SettingsRowDivider()
                        // 液态玻璃：设备能力 + 用户开关。副标题直接把当前生效档位说清楚，
                        // 避免用户开关开了却看不出变化（低版本只有模糊/降级底色）
                        SettingsSwitchRow(
                            icon = Icons.Outlined.BlurOn,
                            title = tr("液态玻璃效果", "Liquid glass"),
                            subtitle = when {
                                !LiquidGlass.isSupported -> tr(
                                    "当前设备不支持（需 Android 12 / API 31+），已自动启用半透明底色降级",
                                    "Not supported here (needs Android 12 / API 31+) — translucent fallback is used instead"
                                )
                                !LiquidGlass.supportsRefraction -> tr(
                                    "已开启：实时模糊 + 边缘高光；折射玻璃需 Android 13 / API 33+",
                                    "On: live blur + rim highlight; true refraction needs Android 13 / API 33+"
                                )
                                else -> tr(
                                    "只用于导航栏、卡片等关键元素，不铺满全屏；较吃 GPU，可随时关闭",
                                    "Key elements only (nav bar, cards) — never full-screen. GPU-heavy, turn off anytime"
                                )
                            },
                            checked = state.settings.liquidGlass,
                            onChecked = { state.updateSettings(liquidGlass = it) }
                        )
                        SettingsRowDivider()
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Insights,
                            title = tr("AI 悬浮球"),
                            subtitle = tr("开启后页面右下角常驻 AI 助手入口；默认关闭，长按悬浮球也可关闭"),
                            checked = state.aiBallEnabled,
                            onChecked = { state.updateAiBallEnabled(it) }
                        )
                        SettingsRowDivider()
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Memory,
                            title = tr("AI 长期记忆"),
                            subtitle = tr("从对话中自动记住你的偏好、常住城市、忌口等；关闭后不再记录"),
                            checked = state.aiMemoryEnabled,
                            onChecked = { state.aiMemoryEnabled = it }
                        )
                        SettingsRowDivider()
                        // 系统通知中心显示播放状态与控制按钮
                        SettingsSwitchRow(
                            icon = Icons.Outlined.MusicNote,
                            title = tr("音乐播放通知", "Music playback notification"),
                            subtitle = tr("在通知中心显示歌曲、封面与播放控制", "Show song, cover art and controls in the notification shade"),
                            checked = musicNotifEnabled,
                            onChecked = {
                                musicNotifEnabled = it
                                MusicPrefs.setNotificationsEnabled(context, it)
                                if (it) MusicExternal.refresh(context)
                            }
                        )
                        SettingsRowDivider()
                        // 一键把小组件固定到系统桌面（Android 8+ 支持直接请求，低版本给引导）
                        SettingsNavRow(
                            icon = Icons.Outlined.Widgets,
                            title = tr("桌面小组件", "Home screen widgets"),
                            subtitle = tr("11 款卡片：足迹、天气、行程、攻略、音乐、课表等", "11 cards: footprints, weather, trips, guides, music, timetable"),
                            onClick = { showWidgetDialog = true }
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(Spacing.xxl))
                    Text(text = tr("存储与缓存", "Storage & Cache"), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(Spacing.sm))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(Bg.Surface)
                    ) {
                        SettingsNavRow(
                            icon = Icons.Outlined.CleaningServices,
                            title = tr("清理缓存"),
                            subtitle = tr("释放临时文件，已保存的足迹不受影响"),
                            onClick = {
                                context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                                state.toast(tr("缓存已清理", "Cache cleared"))
                            }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.MusicNote,
                            title = tr("清除歌词缓存", "Clear lyric cache"),
                            subtitle = tr(
                                "不设上限，已缓存 ${LyricsRepository.cacheSummary(context)}，点击清理",
                                "Unlimited cache · ${LyricsRepository.cacheSummary(context)}"
                            ),
                            onClick = {
                                LyricsRepository.clearCache(context)
                                state.toast(tr("歌词缓存已清理", "Lyric cache cleared"))
                            }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.Translate,
                            title = tr("清除译词缓存", "Clear translation cache"),
                            subtitle = tr(
                                "不设上限，已缓存 ${WebLyricsTranslate.cacheSummary(context)}，点击清理",
                                "Unlimited cache · ${WebLyricsTranslate.cacheSummary(context)}"
                            ),
                            onClick = {
                                WebLyricsTranslate.clearCache(context)
                                state.toast(tr("译词缓存已清理", "Translation cache cleared"))
                            }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.Article,
                            title = tr("清除歌曲故事缓存", "Clear song story cache"),
                            subtitle = tr(
                                "不设上限，已缓存 ${SongStoryCache.count(context)} 篇故事，点击清理",
                                "Unlimited cache · ${SongStoryCache.count(context)} stories"
                            ),
                            onClick = {
                                SongStoryCache.clear(context)
                                state.toast(tr("歌曲故事缓存已清理", "Song story cache cleared"))
                            }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.DeleteOutline,
                            title = tr("清除地图缓存"),
                            subtitle = tr("已缓存200MB以内，浏览过的区域无需重新加载"),
                            onClick = {
                                File(context.cacheDir, "osmdroid").deleteRecursively()
                                state.toast(tr("地图缓存已清除", "Map cache cleared"))
                            }
                        )
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.MusicNote,
                            title = tr("清除网页歌曲缓存"),
                            subtitle = tr(
                                "已缓存 ${WebMusicCache.summary(context)}，清除后需重新下载",
                                "Cached ${WebMusicCache.summary(context)}"
                            ),
                            onClick = {
                                val n = MusicPlayer.clearWebCache(context)
                                state.toast(
                                    if (n > 0) tr("已清除 $n 首网页歌曲", "Removed $n cached songs")
                                    else tr("没有可清除的网页歌曲", "No cached songs to clear")
                                )
                            }
                        )
                    }
                }
            }

            // ---------- 关于 ----------
            if (page == SettingsPage.ABOUT) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(Bg.Surface)
                    ) {
                        SettingsTextRow(icon = Icons.Outlined.Info, title = tr("版本"), value = "v1.0.0")
                        SettingsRowDivider()
                        SettingsNavRow(
                            icon = Icons.Outlined.Info,
                            title = tr("崩溃日志"),
                            subtitle = tr("查看 / 复制 / 清空本地崩溃日志"),
                            onClick = { showLogDialog = true }
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(Spacing.xxl))
                    Text(
                        text = tr(
                            "MY足迹 · 每一步足迹都是独家回忆",
                            "MY足迹 · Every footprint is a memory of its own"
                        ),
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }

    if (showLangDialog) {
        LanguageDialog(
            current = Lang.code,
            onSelect = {
                state.updateLanguage(it)
                // 小组件文案走运行期 Lang（不是 values-en），切语言后主动刷一次
                WidgetKit.refreshAll(context)
                showLangDialog = false
            },
            onDismiss = { showLangDialog = false }
        )
    }

    if (showWidgetDialog) {
        WidgetDialog(state = state, onDismiss = { showWidgetDialog = false })
    }

    if (showHapticDialog) {
        HapticDialog(
            current = state.hapticMode,
            onSelect = {
                state.updateHapticMode(it)
                showHapticDialog = false
            },
            onDismiss = { showHapticDialog = false }
        )
    }

    if (showThemeDialog) {
        ThemeDialog(
            current = state.themeMode,
            onSelect = {
                state.updateThemeMode(it)
                // 桌面卡片也跟随 App 主题：RemoteViews 不会收到 Compose 重组
                WidgetKit.refreshAll(context)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showBackupDialog) {
        BackupDialog(state = state, onDismiss = { showBackupDialog = false })
    }

    if (showTrashDialog) {
        TrashDialog(state = state, onDismiss = { showTrashDialog = false })
    }

    // 个人资料二级页
    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text(text = tr("个人资料", "Profile"), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.clickable { pickAvatar.launch("image/*") }) {
                            Avatar(
                                text = nickname.take(1),
                                size = 56.dp,
                                imageUrl = state.user.avatarUrl
                            )
                        }
                        Spacer(Modifier.width(Spacing.lg))
                        Column {
                            Text(text = nickname, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = tr("点头像可更换"), color = TextMuted, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(Spacing.lg))
                    SettingsField(tr("昵称", "Nickname"), nickname, { nickname = it }, hint = tr("2-12 个字符"))
                    Spacer(Modifier.height(Spacing.md))
                    SettingsField(
                        tr("个性签名", "Bio"),
                        signature,
                        { signature = it },
                        hint = tr("一句话介绍自己"),
                        maxLines = 2
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    PrimaryButton(
                        text = tr("保存资料", "Save"),
                        onClick = {
                            state.updateSettings(
                                nickname = nickname.trim().ifBlank { tr("旅人", "Traveler") },
                                signature = signature.trim()
                            )
                            state.toast(tr("资料已保存", "Saved"))
                            showProfileDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text(text = tr("关闭", "Close"))
                }
            }
        )
    }

    if (showAiModelDialog) {
        AiModelDialog(
            state = state,
            progress = modelImportProgress,
            onImport = { importModelLauncher.launch(null) },
            onDismiss = { showAiModelDialog = false }
        )
    }

    if (showLocalAiRuntimeDialog) {
        LocalAiRuntimeDialog(
            state = state,
            onDismiss = { showLocalAiRuntimeDialog = false }
        )
    }

    if (showVoiceLibraryDialog) {
        VoiceLibraryDialog(
            state = state,
            onDismiss = { showVoiceLibraryDialog = false }
        )
    }

    if (showApiEndpointDialog) {
        ApiEndpointDialog(
            state = state,
            onDismiss = { showApiEndpointDialog = false }
        )
    }

    if (showSuperCryptoDialog) {
        val enabledNow = SuperEncryption.isEnabled(context)
        AlertDialog(
            onDismissRequest = { showSuperCryptoDialog = false },
            title = { Text(tr("超级加密通信", "Super encrypted chat")) },
            text = {
                Column {
                    Text(
                        if (enabledNow) tr("已开启。新文字消息在发送前加密，服务器只保存不透明密文。", "On. New text is encrypted before sending; the server only stores opaque ciphertext.")
                        else tr("未开启。开启后会生成本机身份密钥，并与对方交换公钥。", "Off. Turning it on creates a local identity key and exchanges public keys with the peer.")
                    )
                    Spacer(Modifier.height(10.dp))
                    if (enabledNow) {
                        Text(
                            tr("我的安全码", "My safety code"),
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                        Text(
                            SuperEncryption.mySafetyCode(context),
                            color = Terracotta,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        tr(
                            "加密层：X25519 协商 + Ed25519 签名 + AES-256-GCM-SIV；每条消息独立临时密钥，私钥由系统 Keystore 包裹，服务端无法解密。换机后需重新握手。",
                            "Layers: X25519 + Ed25519 + AES-256-GCM-SIV; per-message ephemeral keys; private keys wrapped by Android Keystore. Re-handshake after switching phones."
                        ),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr(
                            "暂未启用：ML-KEM-768 后量子混合、Shamir 分片恢复、密封发件人。当前版本仅加密文字，图片/视频在超级加密模式下暂不可发。",
                            "Not enabled yet: ML-KEM-768 hybrid, Shamir recovery, sealed sender. Only text is encrypted; photos/videos are blocked in this mode."
                        ),
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!enabledNow && SuperEncryption.enable(context)) {
                        superEncrypted = true
                        state.toast(tr("超级加密已开启", "Super encryption enabled"))
                    }
                    showSuperCryptoDialog = false
                }) {
                    Text(if (enabledNow) tr("知道了", "Got it") else tr("开启", "Turn on"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSuperCryptoDialog = false }) {
                    Text(tr("关闭", "Close"))
                }
            }
        )
    }

    if (showSecurityDialog) {
        InfoDialog(
            title = tr("安全中心"),
            sections = listOf(
                tr("数据存储", "Data storage") to
                    tr(
                        "本版本为纯本地模式：足迹、照片、行程与攻略全部保存在这台手机上，不经过任何服务器。",
                        "This build is local-only: footprints, photos, journeys and guides stay on this phone and never touch a server."
                    ),
                tr("数据安全", "Data safety") to
                    tr(
                        "每次退到后台会自动生成一份本机快照（保留最近 5 份），误删可在「本地数据 - 本地快照」恢复。",
                        "A local snapshot is saved whenever you leave the app (last 5 kept). Restore it under On this device → Local snapshots."
                    ),
                tr("换机与备份", "Switching phones") to
                    tr(
                        "换手机前请用「导出备份文件」生成 JSON 发给自己，否则数据无法跨设备迁移。",
                        "Before switching phones, use Export backup to send yourself a JSON file — data does not sync across devices."
                    )
            ),
            onDismiss = { showSecurityDialog = false }
        )
    }

    if (showPrivacyDialog) {
        InfoDialog(
            title = tr("用户隐私政策", "Privacy Policy"),
            sections = listOf(
                tr("我们收集的信息", "What we collect") to
                    tr(
                        "你主动填写的昵称、个性签名、足迹地点与照片，以及展示地图所需的位置信息。",
                        "The nickname, bio, footprint locations and photos you provide, plus location data needed to render the map."
                    ),
                tr("如何使用", "How we use it") to
                    tr(
                        "仅用于生成足迹记录、行程规划与年度报告，不会出售或提供给无关第三方。",
                        "Only to build your footprint records, trip plans and yearly report. Never sold or shared with unrelated third parties."
                    ),
                tr("第三方服务", "Third-party services") to
                    tr(
                        "地图瓦片来自 OSMDroid 公开服务；AI 功能会把你的输入发往智谱开放平台处理，仅在生成结果时上传图片。",
                        "Map tiles come from public OSMDroid services. AI features send your input to Zhipu's open platform; images are uploaded only when generating results."
                    ),
                tr("你的权利", "Your rights") to
                    tr(
                        "可随时在「通用」中清理缓存、删除足迹，或在系统设置中撤回相册、位置、通知权限。",
                        "You can clear the cache or delete footprints under General at any time, and revoke photo, location or notification permissions in system settings."
                    )
            ),
            onDismiss = { showPrivacyDialog = false }
        )
    }

    if (showLogDialog) {
        CrashLogDialog(
            context = context,
            onDismiss = { showLogDialog = false }
        )
    }
}

/**
 * 语言选择：简体中文 / English。
 * 选中即生效 —— [com.example.my.ui.i18n.Lang] 是 Compose 状态，改完整个界面重组，
 * 不 recreate Activity，所以对话框背后的列表滚动位置、输入内容都不会丢。
 */
/** Android 8+ 可以直接请求把小组件固定到桌面；低版本只提示手动添加路径 */
private fun requestPinWidget(context: Context, provider: ComponentName): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val mgr = AppWidgetManager.getInstance(context)
    if (!mgr.isRequestPinAppWidgetSupported) return false
    mgr.requestPinAppWidget(provider, null, null)
    return true
}

/** 设置页「桌面小组件」里的一行：真实预览缩略图 + 中英成对文案 + 对应的 provider */
private data class WidgetEntry(
    val preview: Int,
    val zh: String,
    val en: String,
    val subZh: String,
    val subEn: String,
    val provider: Class<*>
)

/** 设置页「桌面小组件」：列出全部卡片，点一下就发起系统添加请求 */
@Composable
private fun WidgetDialog(state: AppState, onDismiss: () -> Unit) {
    val context = LocalContext.current

    // 「旅行语录」小组件的文字可以自己写，留空则用内置的每日一句
    var quoteEdit by remember { mutableStateOf(false) }
    var quoteDraft by remember { mutableStateOf("") }

    fun pin(provider: ComponentName, zh: String, en: String) {
        val ok = requestPinWidget(context, provider)
        state.toast(
            if (ok) tr("已请求添加「$zh」，请在桌面确认", "Requested \"$en\" — confirm on your home screen")
            else tr("请长按桌面空白处 → 小组件 → 找到 MY足迹", "Long-press the home screen → Widgets → MY Trace")
        )
        onDismiss()
    }

    val entries = listOf(
        WidgetEntry(
            R.drawable.widget_preview_today, "今日足迹", "Daily trace",
            "分段进度环 + 今日足迹与城市数", "Goal ring, footprints and cities today",
            TodayWidgetProvider::class.java
        ),
        WidgetEntry(
            R.drawable.widget_preview_weather, "天气与行程", "Weather and trips",
            "温度、天气、行程倒计时与近半年足迹", "Temperature, trip countdown and 6-month stats",
            WeatherWidgetProvider::class.java
        ),
        WidgetEntry(
            R.drawable.widget_preview_quote, "旅行语录", "Travel quote",
            "每天一句手写体旅行语录", "A new handwritten travel quote each day",
            QuoteWidgetProvider::class.java
        ),
        WidgetEntry(
            R.drawable.widget_preview_departure, "出发倒计时", "Departure countdown",
            "最近一趟行程还有几天出发", "Days until your next trip",
            DepartureWidgetProvider::class.java
        ),
        WidgetEntry(
            R.drawable.widget_preview_city_map, "点亮城市地图", "City map",
            "真实省界上标出走过的城市", "Footprint pins on a real province map",
            CityMapWidgetProvider::class.java
        ),
        WidgetEntry(
            R.drawable.widget_preview_monthly, "本月足迹", "Monthly bars",
            "近 6 个月足迹柱状图，本月高亮", "6-month bar chart with this month highlighted",
            MonthlyWidgetProvider::class.java
        ),
        WidgetEntry(
            R.drawable.widget_preview_guide, "推荐攻略", "Featured guide",
            "封面、标题与想去人数，点击直达", "Cover, title and popularity — tap to open",
            GuideWidgetProvider::class.java
        ),
        WidgetEntry(
            R.drawable.widget_preview_capture, "拍照即定位", "Snap to pin",
            "点击直接唤起相机记录足迹", "Tap to open the camera and pin a place",
            CaptureWidgetProvider::class.java
        ),
        WidgetEntry(
            R.drawable.widget_preview_music, "音乐播放控制", "Music player",
            "黑胶唱片、歌名与播放/暂停合一按钮", "Vinyl cover, title and one play/pause button",
            MusicWidgetProvider::class.java
        ),
        WidgetEntry(
            R.drawable.widget_preview_footprint, "足迹统计", "Footprint stats",
            "足迹总数、去过的城市与最近足迹", "Total footprints, cities and the latest one",
            FootprintWidgetProvider::class.java
        ),
        WidgetEntry(
            R.drawable.widget_preview_course, "今日课表", "Today's classes",
            "半透明卡片显示当前/下一节课，可按桌面尺寸自由调整", "Translucent card with current/up-next class, resizable",
            CourseWidgetProvider::class.java
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(tr("添加到桌面", "Add to home screen"), fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    tr("共 ${entries.size} 款，点一下发起系统添加请求", "${entries.size} widgets — tap one to request pinning"),
                    color = TextMuted, fontSize = 11.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("卡片显示空白 / 停在加载中？先在这里刷新一次", "Card blank or stuck loading? Refresh it here"),
                        color = TextMuted, fontSize = 11.sp
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            WidgetKit.refreshAll(context)
                            state.toast(tr("已重新刷新全部桌面卡片", "All widgets refreshed"))
                        }
                    ) {
                        Text(tr("立即刷新", "Refresh"), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    entries.forEach { e ->
                        WidgetOption(
                            preview = e.preview,
                            title = tr(e.zh, e.en),
                            subtitle = tr(e.subZh, e.subEn),
                            onClick = {
                                pin(ComponentName(context, e.provider), e.zh, e.en)
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        quoteDraft = WidgetKit.customQuote(context)
                        quoteEdit = true
                    }
                ) {
                    Text(
                        tr("✎ 自定义「旅行语录」的文字", "✎ Customize the quote text"),
                        color = Terracotta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    tr("若系统未弹出确认框，请长按桌面空白处，从「小组件」里手动添加。",
                        "If nothing pops up, long-press the home screen and add it from Widgets."),
                    color = TextMuted, fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("关闭", "Close"), color = TextSecondary, fontSize = 14.sp)
            }
        }
    )

    // 自定义「旅行语录」文字
    if (quoteEdit) {
        AlertDialog(
            onDismissRequest = { quoteEdit = false },
            title = { Text(tr("自定义语录", "Custom quote"), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        tr(
                            "这段文字会原样显示在「旅行语录」小组件上，换行也照排。留空保存则恢复内置的每日一句。",
                            "Shown as-is on the quote widget, line breaks kept. Leave empty to use the built-in daily quote."
                        ),
                        color = TextMuted, fontSize = 11.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = quoteDraft,
                        onValueChange = { if (it.length <= 60) quoteDraft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp),
                        placeholder = {
                            Text(
                                tr("例如：\n山高路远，\n看世界，也找自己",
                                    "e.g.\nFar roads ahead,\nsee the world, find yourself"),
                                fontSize = 13.sp, color = TextMuted
                            )
                        }
                    )
                    Text(
                        "${quoteDraft.length}/60",
                        color = TextMuted, fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    WidgetKit.setCustomQuote(context, quoteDraft)
                    WidgetKit.refreshAll(context)
                    quoteEdit = false
                    state.toast(
                        if (quoteDraft.isBlank()) tr("已恢复内置每日一句", "Back to the built-in daily quote")
                        else tr("已更新小组件语录", "Quote updated")
                    )
                }) {
                    Text(tr("保存", "Save"), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    quoteDraft = ""
                    WidgetKit.setCustomQuote(context, "")
                    WidgetKit.refreshAll(context)
                    quoteEdit = false
                    state.toast(tr("已恢复内置每日一句", "Back to the built-in daily quote"))
                }) {
                    Text(tr("恢复默认", "Reset"), color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun WidgetOption(
    preview: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Terracotta.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 与系统小组件选择器用的是同一张 previewImage，所见即所得
        Image(
            painter = painterResource(preview),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Terracotta.copy(alpha = 0.07f))
                .padding(2.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
        Text("+", color = Terracotta, fontSize = 20.sp)
    }
}

@Composable
private fun LanguageDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val options = listOf("zh" to "简体中文", "en" to "English")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = tr("选择界面语言", "Choose your language"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                options.forEach { (code, label) ->
                    val picked = code == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (picked) Terracotta.copy(alpha = 0.10f) else Color.Transparent)
                            .clickable { onSelect(code) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = if (picked) Terracotta else TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (picked) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Terracotta,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = tr("切换语言后立即生效，无需重启", "Takes effect immediately, no restart needed"),
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("取消"), color = TextSecondary, fontSize = 14.sp)
            }
        }
    )
}

/**
 * 触感反馈档位文案。默认「跟随系统」：静音或系统关闭触感时 App 内不震。
 */
private fun hapticLabel(mode: String): String = when (mode) {
    Haptics.ON -> tr("总是开启", "Always on")
    Haptics.OFF -> tr("关闭", "Off")
    else -> tr("跟随系统", "Follow system")
}

@Composable
private fun HapticDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        Haptics.FOLLOW to tr("跟随系统", "Follow system"),
        Haptics.ON to tr("总是开启", "Always on"),
        Haptics.OFF to tr("关闭", "Off")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = tr("触感反馈", "Haptics"), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { (code, label) ->
                    val picked = code == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (picked) Terracotta.copy(alpha = 0.10f) else Color.Transparent)
                            .clickable { onSelect(code) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = if (picked) Terracotta else TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (picked) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Terracotta,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = tr(
                        "跟随系统时，手机静音或关闭系统触感反馈后 App 内不再震动",
                        "Follows your phone: no haptics in silent mode or when system touch feedback is off"
                    ),
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("取消"), color = TextSecondary, fontSize = 14.sp)
            }
        }
    )
}

@Composable
private fun ThemeDialog(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        0 to tr("跟随系统", "Follow system"),
        1 to tr("浅色", "Light"),
        2 to tr("深色", "Dark")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = tr("深色模式", "Dark mode"), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { (code, label) ->
                    val picked = code == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (picked) Terracotta.copy(alpha = 0.10f) else Color.Transparent)
                            .clickable { onSelect(code) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = if (picked) Terracotta else TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (picked) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Terracotta,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("取消"), color = TextSecondary, fontSize = 14.sp)
            }
        }
    )
}

@Composable
private fun CrashLogDialog(context: Context, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf(CrashLog.readLog(context)) }
    // 复制成功只切提示文案，绝不能覆盖 log —— 覆盖了用户就再也复制不到原文
    var copied by remember { mutableStateOf(false) }
    var copyFailed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("崩溃日志"), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // 文本可长按选中：剪贴板万一失灵，还能手动框选复制
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text = log, color = TextSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = when {
                        copyFailed -> tr("复制失败，请长按上方文字手动选中复制")
                        copied -> tr("已复制到剪贴板，可直接粘贴发送")
                        else -> tr("崩溃会记录在此。点「复制」或「分享」把日志发出来即可定位问题。")
                    },
                    color = if (copyFailed) Func.Danger else TextMuted,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val ok = runCatching {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(tr("崩溃日志"), log)))
                        }.isSuccess
                        copied = ok
                        copyFailed = !ok
                    }
                }
            ) { Text(if (copied) tr("已复制") else tr("复制")) }
        },
        dismissButton = {
            Row {
                // 分享是最稳的出口：剪贴板在部分机型/权限下会失效，走系统分享一定能发出去
                TextButton(
                    onClick = {
                        runCatching {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, tr("MY足迹 崩溃日志"))
                                putExtra(Intent.EXTRA_TEXT, log)
                            }
                            context.startActivity(
                                Intent.createChooser(send, tr("分享崩溃日志"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                ) { Text(tr("分享")) }
                TextButton(onClick = { CrashLog.clear(context); log = CrashLog.readLog(context) }) { Text(tr("清空")) }
                TextButton(onClick = onDismiss) { Text(tr("关闭")) }
            }
        }
    )
}

/** 备份时间的人话表达（1 分钟内不显示秒，避免无意义的跳动） */
private fun backupAgo(at: Long): String {
    val minutes = ((System.currentTimeMillis() - at) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> tr("刚刚", "just now")
        minutes < 60 -> tr("${minutes} 分钟前", "${minutes} min ago")
        minutes < 1440 -> tr("${minutes / 60} 小时前", "${minutes / 60} h ago")
        else -> tr("${minutes / 1440} 天前", "${minutes / 1440} d ago")
    }
}

private fun backupStamp(at: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(at))

/**
 * 本地快照列表：纯本地模式没有云端，误删只能靠这里捞回来。
 * 恢复是"覆盖式"的，所以在顶部写清楚了。
 */
@Composable
private fun BackupDialog(state: com.example.my.state.AppState, onDismiss: () -> Unit) {
    var snapshots by remember { mutableStateOf(emptyList<LocalBackup.Snapshot>()) }
    var loading by remember { mutableStateOf(true) }
    var restoring by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        snapshots = withContext(Dispatchers.IO) { state.localSnapshots() }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = tr("本地快照"), fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = tr(
                        "退到后台会自动备份一次，最多保留 5 份。恢复会覆盖当前的足迹、旅程与计划。",
                        "A snapshot is saved each time you leave the app, up to 5 kept. Restoring overwrites current footprints, journeys and plans."
                    ),
                    color = TextMuted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(Spacing.md))
                when {
                    loading -> Text(text = tr("读取中…", "Loading…"), color = TextMuted, fontSize = 13.sp)
                    snapshots.isEmpty() -> Text(
                        text = tr("还没有快照，点「立即备份」生成一份", "No snapshots yet — tap Back up now"),
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                    else -> snapshots.forEach { snap ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = backupStamp(snap.savedAt),
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = tr(
                                        "${snap.footprintCount} 足迹 · ${snap.routeCount} 旅程 · ${snap.planCount} 计划",
                                        "${snap.footprintCount} fp · ${snap.routeCount} trips · ${snap.planCount} plans"
                                    ),
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            TextButton(
                                enabled = !restoring,
                                onClick = {
                                    scope.launch {
                                        restoring = true
                                        val ok = withContext(Dispatchers.IO) { state.restoreSnapshot(snap.file) }
                                        restoring = false
                                        state.toast(
                                            if (ok) tr("已恢复到 ${backupStamp(snap.savedAt)}", "Restored")
                                            else tr("恢复失败，快照可能已损坏", "Restore failed — snapshot may be damaged")
                                        )
                                        onDismiss()
                                    }
                                }
                            ) {
                                Text(text = tr("恢复"), color = Terracotta, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                state.snapshotNow()
                state.toast(tr("已生成快照", "Snapshot created"))
                scope.launch {
                    kotlinx.coroutines.delay(350)
                    snapshots = withContext(Dispatchers.IO) { state.localSnapshots() }
                }
            }) {
                Text(text = tr("立即备份"), color = Terracotta)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = tr("关闭"), color = TextMuted)
            }
        }
    )
}

/** 回收站：列出软删除的足迹，可单条恢复 / 彻底删除；3 天后自动清除 */
@Composable
private fun TrashDialog(state: com.example.my.state.AppState, onDismiss: () -> Unit) {
    val items = state.trashedFootprints
    val retentionMs = 3L * 24 * 60 * 60 * 1000
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = tr("回收站", "Trash"), fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = tr(
                        "删除的足迹会在这里保留 3 天，照片仍在，可随时恢复。",
                        "Deleted footprints are kept here for 3 days with photos intact."
                    ),
                    color = TextMuted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(Spacing.md))
                when {
                    items.isEmpty() -> Text(
                        text = tr("回收站是空的", "Trash is empty"),
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                    else -> items.forEach { entry ->
                        val remainMs = entry.trashedAt + retentionMs - System.currentTimeMillis()
                        val remainDays = (remainMs / (24 * 60 * 60 * 1000.0)).coerceAtLeast(0.0)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.fp.name.ifBlank { tr("未命名足迹") },
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Text(
                                    text = listOf(entry.fp.city, entry.fp.date)
                                        .filter { it.isNotBlank() }.joinToString(" · "),
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                                Text(
                                    text = tr("剩 %.1f 天自动清除", "Auto-purge in %.1f days").format(remainDays),
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                            TextButton(onClick = { state.restoreFromTrash(setOf(entry.fp.id)) }) {
                                Text(text = tr("恢复"), color = Terracotta, fontSize = 12.sp)
                            }
                            TextButton(onClick = { state.deleteForever(setOf(entry.fp.id)) }) {
                                Text(text = tr("彻底删除"), color = Color(0xFFE76F51), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (items.isNotEmpty()) {
                TextButton(onClick = { state.emptyTrash() }) {
                    Text(text = tr("清空回收站", "Empty trash"), color = Color(0xFFE76F51))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = tr("关闭"), color = TextMuted)
            }
        }
    )
}

/**
 * 本地大模型管理：模型商店式列表。
 *
 * 引擎随 APK 打包（MNN 3.6.2 官方预编译 .so），缺的只是模型文件。
 * 点「下载」直接从 ModelScope 拉到私有目录（应用级协程，离开本页也不断线），
 * 下完自动启用；已下载的模型可以切换「使用」或单个「删除」；
 * 底部保留「导入模型文件夹」手工通道（电脑上已下好的可以拷进手机直接导入）。
 */
/**
 * 本地 AI 运行设置：运行内存预算滑条 + 上下文自动/手动档位。
 *
 * 内存预算不是摆设：模型列表会按它过滤推荐；加载时也会用「权重 + KV 估算」
 * 做准入，超预算直接不加载，避免把手机拖进 OOM。
 */
@Composable
private fun LocalAiRuntimeDialog(
    state: AppState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val totalRamMb = remember(context) { LocalAiPrefs.totalRamMb(context) }
    val maxMb = remember(context) { LocalAiPrefs.maxMemoryMb(context) }
    val recommendMb = remember(context) { LocalAiPrefs.recommendedMemoryMb(context) }
    var memoryMb by remember { mutableStateOf(LocalAiPrefs.memoryBudgetMb(context)) }
    var mode by remember { mutableStateOf(LocalAiPrefs.contextMode(context)) }
    var manualTokens by remember { mutableStateOf(LocalAiPrefs.manualContextTokens(context)) }
    var customOpen by remember {
        mutableStateOf(LocalAiPrefs.manualContextTokens(context) !in LocalAiPolicy.MANUAL_CONTEXT_OPTIONS)
    }
    var customK by remember {
        mutableStateOf((LocalAiPrefs.manualContextTokens(context) / 1024).toString())
    }
    val applyManual: (Int) -> Unit = { tokens ->
        manualTokens = tokens
        mode = LocalAiPolicy.ContextMode.MANUAL
        LocalAiPrefs.setManualContextTokens(context, tokens)
        LocalAiPrefs.setContextMode(context, mode)
    }
    val active = remember(context) { com.example.my.data.ai.LocalLlm.activeSpec(context) }
    val representativeTokens = if (mode == LocalAiPolicy.ContextMode.AUTO) 8192 else manualTokens
    val activeNeedMb = LocalAiPolicy.estimateRuntimeMb(active.approxMb, representativeTokens)
    val fits = activeNeedMb <= memoryMb

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg.Surface,
        title = {
            Text(
                tr("本地 AI 运行设置", "On-device AI runtime"),
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    tr("运行内存分配", "Memory budget"),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        LocalAiPolicy.gbLabel(memoryMb),
                        color = Terracotta,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tr("建议 ${LocalAiPolicy.gbLabel(recommendMb)}", "Suggested ${LocalAiPolicy.gbLabel(recommendMb)}"),
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                if (maxMb > LocalAiPolicy.MIN_MEMORY_MB) {
                    Slider(
                        value = (memoryMb / 1024f).coerceIn(1f, maxMb / 1024f),
                        onValueChange = { v ->
                            val mb = LocalAiPolicy.roundToStep((v * 1024f).toLong())
                                .coerceIn(LocalAiPolicy.MIN_MEMORY_MB, maxMb)
                            if (mb != memoryMb) {
                                memoryMb = mb
                                LocalAiPrefs.setMemoryBudgetMb(context, mb)
                            }
                        },
                        valueRange = 1f..(maxMb / 1024f),
                        steps = (((maxMb - LocalAiPolicy.MIN_MEMORY_MB) / LocalAiPolicy.MEMORY_STEP_MB) - 1)
                            .coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = Terracotta,
                            activeTrackColor = Terracotta,
                            inactiveTrackColor = BorderLight
                        )
                    )
                } else {
                    Text(
                        tr("当前设备可分配空间有限，已固定为 1 GB。", "This device leaves only 1 GB for on-device AI."),
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                Text(
                    tr(
                        "设备总内存 ${LocalAiPolicy.gbLabel(totalRamMb.toInt())}，最多可拉到 ${LocalAiPolicy.gbLabel(maxMb)}。建议取出总内存的一半：给本地 AI 留够权重和 KV cache，另一半留给系统、当前 App 和其他后台。",
                        "Device RAM ${LocalAiPolicy.gbLabel(totalRamMb.toInt())}, up to ${LocalAiPolicy.gbLabel(maxMb)}. Half of RAM is the sweet spot: enough for weights and KV cache, while leaving room for Android."
                    ),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Terracotta.copy(alpha = 0.08f))
                        .padding(11.dp)
                ) {
                    Text(
                        tr(
                            "当前模型 ${active.display}：按 ${LocalAiPolicy.label(representativeTokens)} 上下文估算约 ${LocalAiPolicy.gbLabel(activeNeedMb)}",
                            "Current ${active.display}: about ${LocalAiPolicy.gbLabel(activeNeedMb)} at ${LocalAiPolicy.label(representativeTokens)} context"
                        ),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        tr(
                            "这个数值包含权重、KV cache 和运行时余量，用于加载前判断，不是操作系统级的硬上限。",
                            "This estimate covers weights, KV cache and overhead. It gates loading; it is not an OS-level hard cap."
                        ),
                        color = TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                    if (!fits) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr(
                                "当前分配低于该模型估算需求，本地对话可能无法加载；调高内存或换更小的模型。",
                                "The budget is below this model's estimate, so it may fail to load. Raise memory or pick a smaller model."
                            ),
                            color = Coral,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    tr("模型上下文", "Model context"),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tr(
                        "自动档按本轮问题的长度、是否带长材料、对话轮数选择 4K / 8K / 16K / 32K；手动档可选 32K / 64K / 84K / 128K，也能自定义 1K–512K。",
                        "Auto picks 4K / 8K / 16K / 32K from question length, long material and history. Manual offers 32K / 64K / 84K / 128K plus a custom 1K–512K value."
                    ),
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LocalAiChoiceChip(
                        label = tr("自动", "Auto"),
                        selected = mode == LocalAiPolicy.ContextMode.AUTO,
                        modifier = Modifier.weight(1f)
                    ) {
                        mode = LocalAiPolicy.ContextMode.AUTO
                        LocalAiPrefs.setContextMode(context, mode)
                    }
                    LocalAiChoiceChip(
                        label = "32K",
                        selected = mode == LocalAiPolicy.ContextMode.MANUAL && manualTokens == 32_768,
                        modifier = Modifier.weight(1f)
                    ) {
                        customOpen = false
                        applyManual(32_768)
                    }
                    LocalAiChoiceChip(
                        label = "64K",
                        selected = mode == LocalAiPolicy.ContextMode.MANUAL && manualTokens == 65_536,
                        modifier = Modifier.weight(1f)
                    ) {
                        customOpen = false
                        applyManual(65_536)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LocalAiChoiceChip(
                        label = "84K",
                        selected = mode == LocalAiPolicy.ContextMode.MANUAL && manualTokens == 86_016,
                        modifier = Modifier.weight(1f)
                    ) {
                        customOpen = false
                        applyManual(86_016)
                    }
                    LocalAiChoiceChip(
                        label = "128K",
                        selected = mode == LocalAiPolicy.ContextMode.MANUAL && manualTokens == 131_072,
                        modifier = Modifier.weight(1f)
                    ) {
                        customOpen = false
                        applyManual(131_072)
                    }
                    LocalAiChoiceChip(
                        label = tr("自定义", "Custom"),
                        selected = mode == LocalAiPolicy.ContextMode.MANUAL &&
                            (customOpen || manualTokens !in LocalAiPolicy.MANUAL_CONTEXT_OPTIONS),
                        modifier = Modifier.weight(1f)
                    ) {
                        customOpen = true
                        customK = (manualTokens / 1024).toString()
                    }
                }
                if (customOpen) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = customK,
                            onValueChange = { raw ->
                                if (raw.length <= 4 && raw.all { it.isDigit() }) customK = raw
                            },
                            label = { Text(tr("自定义上下文（K）", "Custom context (K)")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            val k = customK.toIntOrNull()
                            if (k != null) {
                                val tokens = k * 1024
                                if (tokens in LocalAiPolicy.MANUAL_CUSTOM_MIN_TOKENS..
                                    LocalAiPolicy.MANUAL_CUSTOM_MAX_TOKENS
                                ) {
                                    applyManual(tokens)
                                    customOpen = false
                                }
                            }
                        }) { Text(tr("应用", "Apply")) }
                    }
                    Text(
                        tr(
                            "可填 1–512，单位 K；实际运行值还会受模型自身窗口和内存预算限制。",
                            "Enter 1–512 in K; the runtime value is still capped by the model window and memory budget."
                        ),
                        color = TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    tr(
                        "提示：上下文越大，首轮预填充越慢、KV cache 越占内存；不确定就用自动。手动值受模型自身窗口上限约束，超出会自动夹到上限。",
                        "Larger context means slower prefill and more KV memory. Leave it on Auto if unsure; manual values are capped by the model window."
                    ),
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                LocalAiPrefs.setMemoryBudgetMb(context, memoryMb)
                LocalAiPrefs.setContextMode(context, mode)
                LocalAiPrefs.setManualContextTokens(context, manualTokens)
                state.toast(tr("运行设置已保存", "Runtime settings saved"))
                onDismiss()
            }) {
                Text(tr("保存", "Save"), color = Terracotta, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("取消", "Cancel"), color = TextSecondary)
            }
        }
    )
}

@Composable
private fun LocalAiChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Terracotta.copy(alpha = 0.14f) else Bg.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Terracotta else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun AiModelDialog(
    state: AppState,
    progress: Int?,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val llm = com.example.my.data.ai.LocalLlm
    val downloading = llm.downloadProgress
    val errors = llm.downloadError
    val activeId = llm.activeSpec(context).id
    // 下载进度变化（完成/取消/失败）后刷新链路状态
    LaunchedEffect(downloading, errors) { state.refreshLocalLlmState() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg.Surface,
        title = { Text(tr("本地大模型", "On-device models"), fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = tr(
                        "当前状态：${state.localLlmState.title} · 当前模型：${llm.activeSpec(context).display}",
                        "Status: ${state.localLlmState.title} · Active: ${llm.activeSpec(context).display}"
                    ),
                    color = if (state.localLlmState == com.example.my.data.ai.LocalLlmState.READY) Terracotta else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tr(
                        "下载完成后自动启用，可随用随删；引擎已随 APK 打包，模型不占安装包体积。",
                        "Downloads auto-activate once finished. The engine ships with the APK; weights don't."
                    ),
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(Spacing.md))
                // 按「设备内存 / 可用存储 + 当前分配」推荐一个具体本地模型，
                // 不把 8 个包一股脑推给用户，避免下完发现跑不动或不够用。
                val profile = remember(context, state.localLlmState) {
                    com.example.my.data.ai.LocalModelAdvisor.profile(context)
                }
                val memBudget = remember(context) {
                    com.example.my.data.ai.LocalAiPrefs.memoryBudgetMb(context)
                }
                val needQuality = remember(state.runMode, state.aiRouteMap) {
                    state.runMode == 2 || com.example.my.data.AiFeature.values().any { feature ->
                        (feature == com.example.my.data.AiFeature.ASSISTANT ||
                            feature == com.example.my.data.AiFeature.TRIP) &&
                            state.aiFeatureRoute(feature).let { route ->
                                route == com.example.my.data.AiFeature.ROUTE_LOCAL ||
                                    route.startsWith(com.example.my.data.AiFeature.ROUTE_LOCAL_PREFIX)
                            }
                    }
                }
                val recommendation = remember(profile, needQuality) {
                    com.example.my.data.ai.LocalModelAdvisor.recommend(
                        profile = profile,
                        needQuality = needQuality,
                        memBudgetMb = memBudget.toLong()
                    )
                }
                val recommendedDownloaded = llm.modelDownloaded(context, recommendation.spec.id)
                val recommendedPct = downloading[recommendation.spec.id]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .background(Terracotta.copy(alpha = 0.08f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("下载建议", "Download tip"),
                            color = Terracotta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                        when {
                            recommendedDownloaded ->
                                Text(tr("已下载", "Downloaded"), color = TextMuted, fontSize = 11.sp)
                            recommendedPct != null ->
                                Text("$recommendedPct%", color = Terracotta, fontSize = 11.sp)
                            else ->
                                TextButton(onClick = { state.startModelDownload(recommendation.spec) }) {
                                    Text(
                                        tr("下载推荐模型", "Download"),
                                        color = Terracotta, fontSize = 12.sp, fontWeight = FontWeight.Medium
                                    )
                                }
                        }
                    }
                    Text(
                        tr(
                            "为你推荐 ${recommendation.spec.display}",
                            "Recommended: ${recommendation.spec.display}"
                        ),
                        color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        recommendation.reasonZh,
                        color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                llm.CATALOG.forEachIndexed { index, spec ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .height(0.5.dp)
                                .background(BorderLight)
                        )
                    }
                    val downloaded = llm.modelDownloaded(context, spec.id)
                    val pct = downloading[spec.id]
                    val err = errors[spec.id]
                    val fitsBudget = llm.fitsMemoryBudget(context, spec.id, contextTokens = 8192)
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(spec.display, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    if (spec.id == recommendation.spec.id) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            Modifier
                                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                                                .background(Terracotta.copy(alpha = 0.14f))
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                tr("推荐", "For you"),
                                                color = Terracotta, fontSize = 9.sp, fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                                Text(spec.sizeHint, color = TextMuted, fontSize = 11.sp)
                                Text(spec.desc, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                                if (!fitsBudget) {
                                    Text(
                                        tr(
                                            "超出 ${LocalAiPolicy.gbLabel(memBudget)} 运行内存预算",
                                            "Exceeds the ${LocalAiPolicy.gbLabel(memBudget)} memory budget"
                                        ),
                                        color = Coral,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            when {
                                // 下载中：进度 + 可取消
                                pct != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("$pct%", color = Terracotta, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    TextButton(onClick = { state.cancelModelDownload(spec.id) }) {
                                        Text(tr("取消", "Cancel"), color = TextMuted, fontSize = 12.sp)
                                    }
                                }
                                // 已下载：使用中 / 使用 + 删除
                                downloaded -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (spec.id == activeId) {
                                        Text(tr("使用中", "Active"), color = Terracotta, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    } else {
                                        TextButton(onClick = { state.setActiveModel(spec.id) }) {
                                            Text(tr("使用", "Use"), color = Terracotta, fontSize = 12.sp)
                                        }
                                    }
                                    TextButton(onClick = {
                                        state.deleteLocalModel(spec.id)
                                        state.toast(tr("已删除 ${spec.display}", "Deleted ${spec.display}"))
                                    }) {
                                        Text(tr("删除", "Delete"), color = TextMuted, fontSize = 12.sp)
                                    }
                                }
                                // 未下载（或上次失败）：下载 / 重试
                                else -> TextButton(onClick = { state.startModelDownload(spec) }) {
                                    Text(
                                        tr(if (err != null) "重试" else "下载", if (err != null) "Retry" else "Download"),
                                        color = Terracotta,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        if (err != null && pct == null && !downloaded) {
                            Text(
                                text = tr("上次下载失败：$err", "Last download failed: $err"),
                                color = Coral,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                if (progress != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = tr("正在导入：$progress%", "Importing: $progress%"),
                        color = Terracotta,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onImport, enabled = progress == null) {
                Text(tr("导入模型文件夹", "Import folder"), color = Terracotta, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("关闭", "Close"), color = TextSecondary, fontSize = 13.sp)
            }
        }
    )
}

/** 说明型弹窗：安全中心 / 隐私政策共用，分段可滚动 */
@Composable
private fun InfoDialog(title: String, sections: List<Pair<String, String>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg.Surface,
        title = { Text(title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                sections.forEachIndexed { index, (head, body) ->
                    if (index > 0) Spacer(Modifier.height(Spacing.md))
                    Text(head, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(body, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(tr("我知道了"), color = Terracotta) }
        }
    )
}

/** 微信式行分割线：细发丝线，左侧与图标文字对齐内缩 */
@Composable
private fun SettingsRowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg)
            .height(0.5.dp)
            .background(BorderLight)
    )
}

@Composable
private fun SettingsField(label: String, value: String, onChange: (String) -> Unit, hint: String, maxLines: Int = 1) {
    Column {
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        TextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(hint, color = TextMuted, fontSize = 14.sp) },
            maxLines = maxLines,
            shape = RoundedCornerShape(Radius.sm),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Bg.SurfaceVariant,
                unfocusedContainerColor = Bg.SurfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Terracotta
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(Bg.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Terracotta, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            // 英文文案比中文长得多：标题给两行、副标题给两行并显式省略号，
            // 否则 maxLines=1 且没 overflow 时会被硬切掉半句话
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        ElasticSwitch(
            checked = checked,
            onCheckedChange = onChecked
        )
    }
}

/**
 * 分段选择行：一段标题说明 + 下方三等分的选项按钮。
 * 用于「对话文本模型」这种互斥、且需要一眼看清当前值的三选一场景。
 */
@Composable
private fun SettingsChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(Bg.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Terracotta, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            options.forEachIndexed { idx, label ->
                val active = idx == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(if (active) Terracotta.copy(alpha = 0.12f) else Bg.SurfaceVariant)
                        .border(
                            1.dp,
                            if (active) Terracotta else Color.Transparent,
                            RoundedCornerShape(Radius.sm)
                        )
                        .clickable { onSelect(idx) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (active) Terracotta else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(Bg.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Terracotta, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(text = "›", color = TextMuted, fontSize = 18.sp)
    }
}

@Composable
private fun SettingsTextRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(Bg.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Terracotta, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Spacing.md))
        Text(text = title, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(text = value, color = TextMuted, fontSize = 13.sp)
    }
}

/** API 模型端点管理：对话 / 图片 / 视频分开接入、分开展示，告别一串单选列表 */
@Composable
private fun ApiEndpointDialog(state: AppState, onDismiss: () -> Unit) {
    var editorOpen by remember { mutableStateOf(false) }
    var editorKind by remember { mutableStateOf(com.example.my.data.AiModelKind.CHAT) }
    var editing by remember { mutableStateOf<com.example.my.data.AiEndpoint?>(null) }
    var deleteTarget by remember { mutableStateOf<com.example.my.data.AiEndpoint?>(null) }
    var showOcrForm by remember { mutableStateOf(false) }

    fun openEditor(kind: com.example.my.data.AiModelKind, ep: com.example.my.data.AiEndpoint? = null) {
        editorKind = ep?.kind ?: kind
        editing = ep
        editorOpen = true
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(Radius.xl),
            color = Bg.Surface
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr("API 模型管理"),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tr(
                                "对话、图片、视频模型分开接入；Key 只保存在本机，不会上传。",
                                "Chat, image and video models are connected separately. Keys stay on this device."
                            ),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = TextMuted
                        )
                    }
                    Text(
                        tr("关闭"),
                        fontSize = 14.sp,
                        color = Terracotta,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(start = 12.dp, top = 4.dp, bottom = 8.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            ApiKindCount(
                                com.example.my.data.AiModelKind.CHAT,
                                state.chatEndpoints.size,
                                Modifier.weight(1f)
                            )
                            ApiKindCount(
                                com.example.my.data.AiModelKind.IMAGE,
                                state.imageEndpoints.size,
                                Modifier.weight(1f)
                            )
                            ApiKindCount(
                                com.example.my.data.AiModelKind.VIDEO,
                                state.videoEndpoints.size,
                                Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.md))
                                .background(Terracotta)
                                .clickable { openEditor(com.example.my.data.AiModelKind.CHAT) }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                tr("+ 接入模型"),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    apiSection(
                        kind = com.example.my.data.AiModelKind.CHAT,
                        title = tr("文本 / 图像识别模型", "Text & vision models"),
                        subtitle = tr("聊天、写作、总结；支持看图的模型可勾选「图像识别」", "Chat, writing and summaries; tick Vision for models that can read images"),
                        endpoints = state.chatEndpoints,
                        activeId = state.chatEndpoints.firstOrNull { it.id == state.currentEndpointId }?.id
                            ?: state.chatEndpoints.firstOrNull()?.id,
                        onAdd = { openEditor(com.example.my.data.AiModelKind.CHAT) },
                        onUse = { state.selectAiEndpoint(it.id) },
                        onEdit = { openEditor(it.kind, it) },
                        onDelete = { deleteTarget = it }
                    )
                    apiSection(
                        kind = com.example.my.data.AiModelKind.IMAGE,
                        title = tr("图片生成模型", "Image models"),
                        subtitle = tr("用于「画一张」「生成图片」等文生图请求", "For text-to-image requests"),
                        endpoints = state.imageEndpoints,
                        activeId = state.currentImageEndpoint?.id,
                        onAdd = { openEditor(com.example.my.data.AiModelKind.IMAGE) },
                        onUse = { state.selectImageEndpoint(it.id) },
                        onEdit = { openEditor(it.kind, it) },
                        onDelete = { deleteTarget = it }
                    )
                    apiSection(
                        kind = com.example.my.data.AiModelKind.VIDEO,
                        title = tr("视频生成模型", "Video models"),
                        subtitle = tr("用于「生成视频」「做一段视频」等文生视频请求", "For text-to-video requests"),
                        endpoints = state.videoEndpoints,
                        activeId = state.currentVideoEndpoint?.id,
                        onAdd = { openEditor(com.example.my.data.AiModelKind.VIDEO) },
                        onUse = { state.selectVideoEndpoint(it.id) },
                        onEdit = { openEditor(it.kind, it) },
                        onDelete = { deleteTarget = it }
                    )

                    item {
                        Spacer(Modifier.height(4.dp))
                        ApiOcrCard(
                            state = state,
                            onConnect = { showOcrForm = true }
                        )
                    }
                }
            }
        }
    }

    if (editorOpen) {
        ApiModelEditorDialog(
            state = state,
            existing = editing,
            initialKind = editorKind,
            onDismiss = { editorOpen = false }
        )
    }
    if (showOcrForm) {
        OcrEndpointDialog(state = state, onDismiss = { showOcrForm = false })
    }
    deleteTarget?.let { ep ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(tr("删除模型", "Delete model")) },
            text = {
                Text(
                    tr(
                        "确定删除「${ep.name}」？删除后不能恢复，但不会影响其它模型。",
                        "Delete \"${ep.name}\"? This cannot be undone and won't affect other models."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    state.deleteAiEndpoint(ep.id)
                    deleteTarget = null
                }) { Text(tr("删除"), color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(tr("取消")) }
            }
        )
    }
}

@Composable
private fun ApiKindCount(
    kind: com.example.my.data.AiModelKind,
    count: Int,
    modifier: Modifier = Modifier
) {
    val icon = when (kind) {
        com.example.my.data.AiModelKind.CHAT -> Icons.Outlined.ChatBubbleOutline
        com.example.my.data.AiModelKind.IMAGE -> Icons.Outlined.PhotoCamera
        com.example.my.data.AiModelKind.VIDEO -> Icons.Outlined.Movie
    }
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(Bg.SurfaceVariant)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Terracotta, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                tr(kind.zh, kind.en),
                fontSize = 11.sp,
                color = TextMuted,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "$count",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.apiSection(
    kind: com.example.my.data.AiModelKind,
    title: String,
    subtitle: String,
    endpoints: List<com.example.my.data.AiEndpoint>,
    activeId: Long?,
    onAdd: () -> Unit,
    onUse: (com.example.my.data.AiEndpoint) -> Unit,
    onEdit: (com.example.my.data.AiEndpoint) -> Unit,
    onDelete: (com.example.my.data.AiEndpoint) -> Unit
) {
    item(key = "head_${kind.name}") {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(subtitle, fontSize = 11.sp, color = TextMuted, lineHeight = 16.sp)
            }
            Text(
                tr("+ 接入"),
                fontSize = 12.sp,
                color = Terracotta,
                modifier = Modifier
                    .clickable(onClick = onAdd)
                    .padding(start = 10.dp, top = 6.dp, bottom = 6.dp)
            )
        }
    }
    if (endpoints.isEmpty()) {
        item(key = "empty_${kind.name}") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(Bg.SurfaceVariant)
                    .clickable(onClick = onAdd)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tr("还没有接入${title}，点这里添加", "No ${title.lowercase()} connected — tap to add"),
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.weight(1f)
                )
                Text(tr("接入"), fontSize = 12.sp, color = Terracotta)
            }
        }
    } else {
        endpoints.forEachIndexed { index, ep ->
            // 除了解码阶段修重复 id，这里再用 kind + 序号兜底，保证 LazyColumn key 唯一。
            item(key = "ep_${kind.name}_${ep.id}_$index") {
                ApiEndpointCard(
                    ep = ep,
                    active = ep.id == activeId,
                    onUse = { onUse(ep) },
                    onEdit = { onEdit(ep) },
                    onDelete = { onDelete(ep) }
                )
            }
        }
    }
}

@Composable
private fun ApiEndpointCard(
    ep: com.example.my.data.AiEndpoint,
    active: Boolean,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val icon = when (ep.primaryKind) {
        com.example.my.data.AiModelKind.CHAT -> Icons.Outlined.ChatBubbleOutline
        com.example.my.data.AiModelKind.IMAGE -> Icons.Outlined.PhotoCamera
        com.example.my.data.AiModelKind.VIDEO -> Icons.Outlined.Movie
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Bg.SurfaceVariant.copy(alpha = if (active) 0.9f else 0.55f))
            .border(
                1.dp,
                if (active) Terracotta.copy(alpha = 0.55f) else Bg.Outline.copy(alpha = 0.35f),
                RoundedCornerShape(Radius.md)
            )
            .padding(horizontal = Spacing.md, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) Terracotta.copy(alpha = 0.16f) else Bg.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Terracotta, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    ep.name.ifBlank { tr("未命名模型") },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    ep.model.ifBlank { tr("未填写模型名") },
                    fontSize = 11.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    ep.effectiveCapabilities.joinToString(" · ") { tr(it.zh, it.en) },
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (active) {
                Text(
                    tr("使用中"),
                    fontSize = 11.sp,
                    color = Terracotta,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Terracotta.copy(alpha = 0.12f))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                ep.format.label,
                fontSize = 10.sp,
                color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Bg.Surface)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (ep.ready) tr("已接通", "Ready") else tr("待补全", "Incomplete"),
                fontSize = 10.sp,
                color = if (ep.ready) Terracotta else TextMuted
            )
            Spacer(Modifier.weight(1f))
            if (!active) {
                Text(
                    tr("设为当前"),
                    fontSize = 11.sp,
                    color = Terracotta,
                    modifier = Modifier
                        .clickable(onClick = onUse)
                        .padding(horizontal = 6.dp, vertical = 5.dp)
                )
            }
            Text(
                tr("编辑"),
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            )
            Text(
                tr("删除"),
                fontSize = 11.sp,
                color = Coral,
                modifier = Modifier
                    .clickable(onClick = onDelete)
                    .padding(start = 6.dp, top = 5.dp, bottom = 5.dp)
            )
        }
    }
}

@Composable
private fun ApiOcrCard(state: AppState, onConnect: () -> Unit) {
    val ocr = state.ocrEndpoint
    val effective = state.effectiveOcrEndpoint()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Bg.SurfaceVariant)
            .padding(horizontal = Spacing.md, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Bg.Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.DocumentScanner, null, tint = Terracotta, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(tr("OCR 文字识别", "OCR"), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(
                when {
                    ocr.ready -> tr("已接入：${ocr.model}", "Connected: ${ocr.model}")
                    effective.ready -> tr("沿用「图像识别」模型：${effective.model}", "Uses the vision model: ${effective.model}")
                    else -> tr("未接入，可回退端侧识别", "Not connected; on-device fallback")
                },
                fontSize = 11.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            if (ocr.ready) tr("修改", "Edit") else tr("接入", "Connect"),
            fontSize = 12.sp,
            color = Terracotta,
            modifier = Modifier
                .clickable(onClick = onConnect)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        if (ocr.ready) {
            Text(
                tr("清除", "Clear"),
                fontSize = 12.sp,
                color = Coral,
                modifier = Modifier
                    .clickable { state.clearOcrEndpoint() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

/** 新增 / 编辑模型：类型、名称、地址、Key、模型名一次填完 */
@Composable
private fun ApiModelEditorDialog(
    state: AppState,
    existing: com.example.my.data.AiEndpoint?,
    initialKind: com.example.my.data.AiModelKind,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var caps by remember {
        mutableStateOf(
            existing?.effectiveCapabilities
                ?: when (initialKind) {
                    com.example.my.data.AiModelKind.IMAGE -> setOf(com.example.my.data.AiCapability.IMAGE_GEN)
                    com.example.my.data.AiModelKind.VIDEO -> setOf(com.example.my.data.AiCapability.VIDEO_GEN)
                    else -> setOf(com.example.my.data.AiCapability.TEXT)
                }
        )
    }
    var format by remember { mutableStateOf(existing?.format ?: com.example.my.data.AiFormat.OPENAI) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf(existing?.apiKey.orEmpty()) }
    var model by remember { mutableStateOf(existing?.model.orEmpty()) }
    var visionModel by remember { mutableStateOf(existing?.visionModel.orEmpty()) }
    var imageModel by remember { mutableStateOf(existing?.imageModel.orEmpty()) }
    var videoModel by remember { mutableStateOf(existing?.videoModel.orEmpty()) }
    var mediaPath by remember { mutableStateOf(existing?.mediaPath.orEmpty()) }
    var videoPath by remember { mutableStateOf(existing?.videoPath.orEmpty()) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    val canSave = caps.isNotEmpty() && name.isNotBlank() && baseUrl.isNotBlank() &&
        apiKey.isNotBlank() && model.isNotBlank()
    val hasText = caps.contains(com.example.my.data.AiCapability.TEXT)
    val hasVision = caps.contains(com.example.my.data.AiCapability.VISION)
    val hasImageGen = caps.contains(com.example.my.data.AiCapability.IMAGE_GEN)
    val hasVideoGen = caps.contains(com.example.my.data.AiCapability.VIDEO_GEN)
    val needsOpenAi = hasImageGen || hasVideoGen

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .imePadding(),
            shape = RoundedCornerShape(Radius.xl),
            color = Bg.Surface
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (existing == null) tr("接入模型", "Connect model") else tr("编辑模型", "Edit model"),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            tr("能力可多选；文本与图像识别共用主模型，图片/视频可单独指定模型", "Multi-select capabilities; text and vision share the main model, image/video can use separate models"),
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Text(
                        tr("关闭"),
                        fontSize = 14.sp,
                        color = Terracotta,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(8.dp)
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg)
                ) {
                    Text(tr("能力（可多选）", "Capabilities (multi-select)"), fontSize = 12.sp, color = TextMuted)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        ApiChoiceChip(
                            text = tr(com.example.my.data.AiCapability.TEXT.zh, com.example.my.data.AiCapability.TEXT.en),
                            selected = hasText,
                            modifier = Modifier.weight(1f)
                        ) {
                            // 图像识别依附于文本模型；取消文本时一并取消图像识别，
                            // 避免出现只能看图、却不能对话的端点。
                            caps = if (hasText) {
                                caps - com.example.my.data.AiCapability.TEXT - com.example.my.data.AiCapability.VISION
                            } else {
                                caps + com.example.my.data.AiCapability.TEXT
                            }
                            testResult = null
                        }
                        ApiChoiceChip(
                            text = tr(com.example.my.data.AiCapability.VISION.zh, com.example.my.data.AiCapability.VISION.en),
                            selected = hasVision,
                            modifier = Modifier.weight(1f)
                        ) {
                            caps = if (hasVision) {
                                caps - com.example.my.data.AiCapability.VISION
                            } else {
                                caps + com.example.my.data.AiCapability.VISION + com.example.my.data.AiCapability.TEXT
                            }
                            testResult = null
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        ApiChoiceChip(
                            text = tr(com.example.my.data.AiCapability.IMAGE_GEN.zh, com.example.my.data.AiCapability.IMAGE_GEN.en),
                            selected = hasImageGen,
                            modifier = Modifier.weight(1f)
                        ) {
                            caps = if (hasImageGen) caps - com.example.my.data.AiCapability.IMAGE_GEN
                            else caps + com.example.my.data.AiCapability.IMAGE_GEN
                            if (!hasImageGen) format = com.example.my.data.AiFormat.OPENAI
                            testResult = null
                        }
                        ApiChoiceChip(
                            text = tr(com.example.my.data.AiCapability.VIDEO_GEN.zh, com.example.my.data.AiCapability.VIDEO_GEN.en),
                            selected = hasVideoGen,
                            modifier = Modifier.weight(1f)
                        ) {
                            caps = if (hasVideoGen) caps - com.example.my.data.AiCapability.VIDEO_GEN
                            else caps + com.example.my.data.AiCapability.VIDEO_GEN
                            if (!hasVideoGen) format = com.example.my.data.AiFormat.OPENAI
                            testResult = null
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr(
                            "同一个模型可同时勾选「文本对话 + 图像识别」；生成图片/视频需要 OpenAI 兼容接口，可分别填生成路径。",
                            "A model can support text and vision together. Image/video generation uses an OpenAI-compatible endpoint with its own path."
                        ),
                        fontSize = 10.sp, color = TextMuted, lineHeight = 14.sp
                    )
                    Spacer(Modifier.height(Spacing.md))
                    if (hasText || hasVision) {
                        Text(tr("协议", "Protocol"), fontSize = 12.sp, color = TextMuted)
                        Spacer(Modifier.height(6.dp))
                        if (needsOpenAi) {
                            Text(
                                tr("已勾选生成图片/视频，统一使用 OpenAI 兼容协议", "Image/video generation requires an OpenAI-compatible protocol"),
                                fontSize = 11.sp, color = TextSecondary
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                com.example.my.data.AiFormat.entries.forEach { f ->
                                    ApiChoiceChip(
                                        text = f.label,
                                        selected = format == f,
                                        modifier = Modifier.weight(1f)
                                    ) { format = f }
                                }
                            }
                        }
                        Spacer(Modifier.height(Spacing.md))
                    }

                    androidx.compose.material3.OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(tr("名称（如：DeepSeek）")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text(tr("Base URL（如：https://api.deepseek.com/v1）")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = {
                            Text(
                                when {
                                    hasImageGen && !hasText && !hasVideoGen ->
                                        tr("图片模型名（如：gpt-image-1）", "Image model")
                                    hasVideoGen && !hasText && !hasImageGen ->
                                        tr("视频模型名（如：sora-2）", "Video model")
                                    hasImageGen || hasVideoGen ->
                                        tr("主模型名（对话/图像识别；生成模型可在下方单独填）", "Main model (chat/vision; generation model can be set below)")
                                    else -> tr("对话模型名（如：deepseek-chat）", "Chat model")
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (hasVision) {
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = visionModel,
                            onValueChange = { visionModel = it },
                            label = { Text(tr("视觉模型名（可选，留空复用模型名）", "Vision model (optional)")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (hasImageGen) {
                        if (hasText || hasVision || hasVideoGen) {
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.material3.OutlinedTextField(
                                value = imageModel,
                                onValueChange = { imageModel = it },
                                label = {
                                    Text(
                                        tr(
                                            "图片生成模型名（可选，如：cogview-3-flash；留空复用主模型）",
                                            "Image model (optional, e.g. cogview-3-flash)"
                                        )
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = mediaPath,
                            onValueChange = { mediaPath = it },
                            label = { Text(tr("图片生成路径（可选，默认 /images/generations）", "Image path (default /images/generations)")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (hasVideoGen) {
                        if (hasText || hasVision || hasImageGen) {
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.material3.OutlinedTextField(
                                value = videoModel,
                                onValueChange = { videoModel = it },
                                label = { Text(tr("视频生成模型名（可选，留空复用主模型）", "Video model (optional)")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = videoPath,
                            onValueChange = { videoPath = it },
                            label = { Text(tr("视频生成路径（可选，默认 /videos）", "Video path (default /videos)")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (hasImageGen || hasVideoGen) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tr(
                                "同一平台聊天与绘图/视频模型名不同时（如智谱 GLM + cogview-3-flash），请分别填写；Base URL 写到 /v4，无需重复写 /images/generations。",
                                "If chat and image/video models differ (e.g. Zhipu GLM + cogview-3-flash), fill them separately. Base URL ends at /v4; do not repeat /images/generations."
                            ),
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    testResult?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            fontSize = 12.sp,
                            color = if (it.startsWith("✓")) Terracotta else Coral
                        )
                    }
                    Spacer(Modifier.height(Spacing.lg))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasText || hasVision) {
                        Text(
                            if (testing) tr("测试中…", "Testing…") else tr("测试连接", "Test"),
                            fontSize = 13.sp,
                            color = if (testing) TextMuted else Terracotta,
                            modifier = Modifier
                                .clickable(enabled = !testing && canSave) {
                                    testing = true
                                    testResult = null
                                    scope.launch {
                                        val ok = com.example.my.data.AiApi.testEndpoint(
                                            baseUrl.trim(), apiKey.trim(), model.trim()
                                        )
                                        testing = false
                                        testResult = if (ok) "✓ " + tr("连接成功", "Connected")
                                        else "✗ " + tr("连接失败，请检查 URL、Key 和模型名", "Connection failed")
                                    }
                                }
                                .padding(vertical = 10.dp)
                        )
                    } else {
                        Text(
                            tr("图片 / 视频模型保存后在对话里实际生成验证", "Verify by generating in chat after saving"),
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(tr("取消")) }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Button(
                        enabled = canSave,
                        onClick = {
                            val primaryKind = when {
                                hasText || hasVision -> com.example.my.data.AiModelKind.CHAT
                                hasImageGen -> com.example.my.data.AiModelKind.IMAGE
                                else -> com.example.my.data.AiModelKind.VIDEO
                            }
                            val ep = com.example.my.data.AiEndpoint(
                                id = existing?.id ?: 0L,
                                name = name.trim(),
                                format = if (needsOpenAi) com.example.my.data.AiFormat.OPENAI else format,
                                baseUrl = baseUrl.trim(),
                                apiKey = apiKey.trim(),
                                model = model.trim(),
                                visionModel = if (hasVision) visionModel.trim() else "",
                                imageModel = if (hasImageGen) imageModel.trim() else "",
                                videoModel = if (hasVideoGen) videoModel.trim() else "",
                                kind = primaryKind,
                                mediaPath = if (hasImageGen) mediaPath.trim() else "",
                                videoPath = if (hasVideoGen) videoPath.trim() else "",
                                capabilities = caps
                            )
                            if (existing == null) state.addAiEndpoint(ep)
                            else state.updateAiEndpoint(ep)
                            onDismiss()
                        }
                    ) { Text(tr("保存")) }
                }
            }
        }
    }
}

@Composable
private fun ApiChoiceChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Terracotta.copy(alpha = 0.14f) else Bg.SurfaceVariant)
            .border(
                1.dp,
                if (selected) Terracotta else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (selected) Terracotta else TextSecondary,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** OCR 独立端点接入表单：地址 / Key / 模型名（OpenAI 兼容视觉识别接口） */
@Composable
private fun OcrEndpointDialog(state: AppState, onDismiss: () -> Unit) {
    val initial = state.ocrEndpoint
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("接入 OCR 模型", "Connect OCR model")) },
        text = {
            Column {
                Text(
                    tr("用于识别照片中的文字（票根、菜单、路牌），以及歌单截图、课表截图的提取。填写 OpenAI 兼容的视觉接口地址、Key 与模型名；留空保存则回退端侧识别。"),
                    fontSize = 12.sp, color = TextMuted
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(tr("Base URL（如：https://api.deepseek.com/v1）")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(tr("API Key")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(tr("视觉识别模型名（如：gpt-4o-mini）")) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                state.saveOcrEndpoint(baseUrl, apiKey, model)
                onDismiss()
            }) { Text(tr("保存")) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(tr("取消")) }
        }
    )
}
