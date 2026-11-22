package com.example.my.ui.music

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.LowPriority
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Queue
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.random.Random
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.imePadding
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import com.example.my.R
import com.example.my.data.AiApi
import com.example.my.data.AiFeature
import com.example.my.data.AiPresets
import com.example.my.data.WebHit
import com.example.my.data.WebSearch
import com.example.my.data.WebSearchPrefs
import com.example.my.data.ai.LocalLlm
import com.example.my.data.music.ChartSong
import com.example.my.data.music.PlaylistShotImporter
import com.example.my.data.music.SongStoryCache
import com.example.my.data.music.SongStoryDirect
import com.example.my.data.music.SongStoryGrounding
import com.example.my.data.music.SongStoryQuery
import com.example.my.data.music.StageBackgroundStore
import com.example.my.data.music.CoverRepository
import com.example.my.data.music.EqPreset
import com.example.my.data.music.MusicExternal
import com.example.my.data.music.LocalSong
import com.example.my.data.music.PlayMode
import com.example.my.data.music.QueueSource
import com.example.my.data.music.LyricsRepository
import com.example.my.data.music.LyricsResult
import com.example.my.data.music.LyricsSource
import com.example.my.data.music.LyricDisplayPrefs
import com.example.my.data.music.LyricIssueStore
import com.example.my.data.music.LyricOverlayService
import com.example.my.data.music.LyricShareArt
import com.example.my.data.music.LyricTranslateLanguages
import com.example.my.data.music.LyricTranslatePrefs
import com.example.my.data.music.WebLyricsTranslate
import com.example.my.data.music.MusicNotification
import com.example.my.data.music.LrcLine
import com.example.my.data.music.HanziScript
import com.example.my.data.music.MusicPlayer
import com.example.my.data.music.QQFailReason
import com.example.my.data.music.QQFetch
import com.example.my.data.music.ImportPipeline
import com.example.my.data.music.QQMusic
import com.example.my.data.music.SongNameParser
import com.example.my.data.music.SongAudio
import com.example.my.data.music.SongFetch
import com.example.my.data.music.SongFetchReason
import com.example.my.data.music.SongMeta
import com.example.my.data.music.AudiusSource
import com.example.my.data.music.AudiusTrack
import com.example.my.data.music.SongResult
import com.example.my.data.music.SongSearchHistory
import com.example.my.util.SearchUtils
import com.example.my.data.music.SongSearch
import com.example.my.data.music.WebMusicCache
import com.example.my.state.AppState
import com.example.my.state.ExtraScreen
import com.example.my.ui.i18n.tr
import com.example.my.ui.icons.Lucide
import com.example.my.ui.components.AppBanner
import com.example.my.ui.components.ImmersiveBarsEffect
import com.example.my.ui.components.ProvideShimmer
import com.example.my.ui.components.SkeletonLyricLines
import com.example.my.ui.components.SkeletonSongRow
import com.example.my.ui.components.rememberIsResumed
import com.example.my.ui.components.rememberIsScreenOn
import com.example.my.ui.screens.AiModelQuickSwitchDialog
import com.example.my.ui.screens.ExportSheet
import com.example.my.ui.theme.AppPalette
import com.example.my.ui.theme.FixedInk
import com.example.my.ui.theme.ImageSampling
import com.example.my.ui.theme.Layer
import com.example.my.ui.theme.PlayerAnim
import com.example.my.ui.theme.PlayerSkin
import com.example.my.ui.theme.PlayerSkinLayout
import com.example.my.ui.theme.PlayerSkinStore
import com.example.my.ui.theme.TypeScale
import com.example.my.util.ImageExporter
import com.example.my.util.ScreenRotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/*
 * 本地音乐播放器 —— 一比一复刻「Now Playing / Playlist / Library」设计稿。
 *
 * 色值、间距均从设计稿逐像素采样（见 build/ref_colors.json、build/ref_dump.txt）：
 *   背景 #141828 / 卡片 #1C2038 / 文字 #FCFCFC / 进度已播 #D8DCE0 /
 *   进度未播 #282840 / 控制圆 #30344C / 波形 #343A56。
 * 三个页面共用一条常驻底部播放条（进度 + 时间 + 上一首/暂停/下一首），
 * 内容区用左右滑动切换；标题可点开页面菜单。静态外观不额外加按钮。
 */

// ── 主题色板 ──
// 跟随 App 全局主题（设置页浅色 / 深色 / 跟随系统）：全部从 AppPalette 动态读取，
// 它是 Compose MutableState，切换主题会即时重组；赤陶橙主色只落在进度/高亮/控制上。
// 尺寸与布局仍保持设计稿采样值，见 build/ref_colors.json。
private val Bg get() = AppPalette.value.background
private val Card get() = AppPalette.value.surface
private val White get() = AppPalette.value.textPrimary
private val TimeGray get() = AppPalette.value.textMuted
private val Chevron get() = AppPalette.value.textSecondary
private val WaveColor get() = AppPalette.value.textMuted.copy(alpha = 0.55f)
private val ProgOn get() = AppPalette.value.terracotta
private val ProgOff get() = AppPalette.value.textMuted.copy(alpha = 0.28f)
private val CtrlCircle get() = AppPalette.value.surfaceVariant
private val CoverTeal get() = AppPalette.value.teal
/** 品牌主色：进度已播、当前歌词行、选中态 */
private val Accent get() = AppPalette.value.terracotta
/** 正在播放的列表项：主题色淡高亮，只在播放中出现，静态稿子仍是 Card */
private val ActiveCard get() = AppPalette.value.terracotta.copy(alpha = 0.12f)

// ── 设计稿尺寸（稿子手机宽 278px ≈ 360dp）──
private val BarWidth = 246.dp
private val CoverSize = 246.dp
private val CardHeight = 84.dp
private val CardGap = 18.dp
private val PageSide = 35.dp

/** 中文为主、英文保留：切到英文模式时渲染 en（设计稿原英文文案不丢） */
private enum class LibKind(val zh: String, val en: String) {
    PLAYLISTS("播放列表", "Playlists"),
    ARTISTS("歌手", "Artists"),
    ALBUMS("专辑", "Albums"),
    SONGS("歌曲", "Songs")
}

/** 播放列表的三种视图，在设置页「播放列表视图」里切换，不往静态稿子加可见按钮 */
private enum class PlaylistFilter { ALL, RECENT, FAVORITE }

/**
 * 音乐主界面。
 * @param standalone 独立音乐 App 模式：退出、编辑资料等动作不再跳主 App 的其他页面。
 * @param onExit 最外层返回时的出口，默认回到主 App 的上一页。
 */
@Composable
fun MusicScreen(
    state: AppState,
    standalone: Boolean = false,
    onExit: () -> Unit = { state.closeScreen() }
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var permissionGranted by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<Pair<Long, LyricsResult>?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var lyricsFailed by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var libSection by remember { mutableStateOf<LibKind?>(null) }
    var libGroup by remember { mutableStateOf<String?>(null) }
    var hinted by rememberSaveable { mutableStateOf(false) }
    // 新功能：睡眠定时 / 均衡器 / 播放列表筛选 / 歌词手动搜索
    var showSleepDialog by remember { mutableStateOf(false) }
    // 音乐闹钟：到点淡入唤醒播放
    var showAlarmDialog by remember { mutableStateOf(false) }
    // 歌曲分享卡片：生成后交给 ExportSheet 保存/分享
    var songCardBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // 驾驶模式统一由 state.openScreen(ExtraScreen.DRIVING) 进入，音乐页不再自持全屏覆盖层
    // 旧的「均衡器」六档预设弹窗已经退休：音效统一走音效库（自选、真 DSP）
    var soundFxPanelOpen by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showLyricsSearch by remember { mutableStateOf(false) }
    // 顶栏展开式搜索：点放大镜后输入框从右侧向左展开（不再弹对话框）
    var songSearchOpen by remember { mutableStateOf(false) }
    var songSearchText by remember { mutableStateOf("") }
    // App 内搜歌结果面板：null = 关闭；网络结果 / 获取进度 / 失败原因都按关键词留着，
    // 滚出视口或重试不会丢状态
    var songSearchQuery by remember { mutableStateOf<String?>(null) }
    // 搜索代次：连续改词时只接受最后一次请求，旧请求晚回来不会覆盖新结果
    var searchGen by remember { mutableStateOf(0) }
    var songResults by remember { mutableStateOf<List<SongResult>>(emptyList()) }
    var songNetPhase by remember { mutableStateOf(0) } // 0 检索中 / 1 有结果 / 2 没结果或失败
    // Audius 免费全曲源：与曲库并行搜索，互不影响
    var audiusResults by remember { mutableStateOf<List<AudiusTrack>>(emptyList()) }
    var audiusPhase by remember { mutableStateOf(0) } // 0 检索中 / 1 有结果 / 2 无结果
    var fetchingSongId by remember { mutableStateOf<String?>(null) }
    var fetchFail by remember { mutableStateOf<SongFetchReason?>(null) }
    var fetchFailId by remember { mutableStateOf<String?>(null) }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val titleAlpha by animateFloatAsState(if (songSearchOpen) 0f else 1f, tween(160))
    var searchTitle by remember { mutableStateOf("") }
    var searchArtist by remember { mutableStateOf("") }
    var playlistFilter by remember { mutableStateOf(PlaylistFilter.ALL) }
    // 播放页「列表」按钮拉起的底部队列弹层：默认按当前播放来源显示对应歌曲
    var queueSheetOpen by remember { mutableStateOf(false) }
    // 歌曲评论弹层：歌词页「弹」与黑胶页「评论」共用同一个列表与排序状态
    var commentsOpen by remember { mutableStateOf(false) }
    // 列表长按：操作菜单 / 改名 / 删除确认，三层依次弹出
    var songAction by remember { mutableStateOf<LocalSong?>(null) }
    var songRename by remember { mutableStateOf<LocalSong?>(null) }
    var songDelete by remember { mutableStateOf<LocalSong?>(null) }
    var renameText by remember { mutableStateOf("") }
    // 排行榜 / QQ 音乐收藏：同样是内嵌内容区，不弹窗，底部播放条常驻
    var qqPanel by remember { mutableStateOf(false) }
    var qqTab by remember { mutableStateOf(0) }
    var qqChartId by remember { mutableStateOf(QQMusic.CHARTS.first().topId) }
    var qqSongs by remember { mutableStateOf<List<ChartSong>>(emptyList()) }
    var qqLoading by remember { mutableStateOf(false) }
    var qqError by remember { mutableStateOf<QQFailReason?>(null) }
    var qqTitle by remember { mutableStateOf("") }
    var qqBusyKeyword by remember { mutableStateOf<String?>(null) }
    var qqBatchRunning by remember { mutableStateOf(false) }
    var qqBatchDone by remember { mutableStateOf(0) }
    var qqBatchTotal by remember { mutableStateOf(0) }
    // 本地收藏页已并入极简「收藏」tab，不再保留经典收藏页
    // ── 极简 UI：5 tab + 10 页信息架构，功能全部沿用上面的状态与 data/music 单例 ──
    // 经典三页界面已删除，这里只保留唯一的极简界面。
    var minimalTab by rememberSaveable { mutableStateOf(0) }
    var minimalSub by remember { mutableStateOf<MinimalPage?>(null) }
    // 歌手详情：叠在歌手列表/音乐馆之上，返回键先收回它
    var minimalArtist by remember { mutableStateOf<String?>(null) }
    var minimalSearchText by rememberSaveable { mutableStateOf("") }

    // 由 MusicPlayer 的可观察状态驱动，切歌会触发重组与歌词重载
    val activeSong = MusicPlayer.current
        ?: (MusicPlayer.songs + MusicPlayer.webSongs).firstOrNull()

    /** 顶栏回车：先在 App 内列出曲库结果，不再直接把用户丢进网页里自己翻 */
    fun runSongSearch(q: String) {
        val query = q.trim()
        if (query.isBlank()) return
        SongSearchHistory.add(ctx, query)
        songSearchQuery = query
        songResults = emptyList()
        songNetPhase = 0
        audiusResults = emptyList()
        audiusPhase = 0
        fetchFail = null
        fetchFailId = null
        val gen = ++searchGen
        scope.launch {
            val list = withTimeoutOrNull(20_000L) { SongSearch.search(query) }.orEmpty()
            if (gen != searchGen) return@launch
            songResults = list
            songNetPhase = if (list.isEmpty()) 2 else 1
        }
        // 免费全曲源单独跑：曲库慢或失败时它仍可能先回来
        scope.launch {
            val list = withTimeoutOrNull(15_000L) { AudiusSource.search(query) }.orEmpty()
            if (gen != searchGen) return@launch
            audiusResults = list
            audiusPhase = if (list.isEmpty()) 2 else 1
        }
    }

    /** 选中一条网络结果：换直链 → 下载到私有缓存 → 写同名 LRC → 入队播放 */
    fun fetchWebSong(r: SongResult) {
        if (fetchingSongId != null) return
        fetchingSongId = r.id
        fetchFail = null
        fetchFailId = null
        scope.launch {
            val outcome = withTimeoutOrNull(28_000L) { SongSearch.fetch(r) }
            val ok = outcome as? SongFetch.Ok
            if (ok == null) {
                fetchingSongId = null
                fetchFail = (outcome as? SongFetch.Fail)?.reason ?: SongFetchReason.NETWORK
                fetchFailId = r.id
                // 从排行榜/收藏一键获取时没有搜歌结果页可看失败原因，这里直接提示
                if (qqPanel) state.toast(
                    tr("没能获取这首歌，试试「搜索」挑一条", "Couldn't fetch it, try Search instead")
                )
                return@launch
            }
            val a: SongAudio = ok.audio
            WebMusicCache.downloadNamed(
                context = ctx,
                url = a.url,
                meta = SongMeta(a.title, a.artist),
                referer = a.referer,
                cookie = a.cookie,
                userAgent = a.userAgent
            ) { song, error ->
                fetchingSongId = null
                if (song != null) {
                    WebMusicCache.saveLyrics(ctx, song, a.lrc)
                    MusicPlayer.addCachedWebSong(song)
                    MusicPlayer.play(song)
                    scope.launch {
                        CoverRepository.saveRemote(ctx, a.title, a.artist, a.cover)
                    }
                    songSearchQuery = null
                    state.toast(
                        tr("已获取并播放：${song.title}", "Downloaded: ${song.title}")
                    )
                } else {
                    fetchFail = SongFetchReason.NETWORK
                    fetchFailId = r.id
                    state.toast(
                        tr("下载失败：${error ?: "未知原因"}", "Download failed: ${error ?: "unknown"}")
                    )
                }
            }
        }
    }

    /** Audius 免费全曲：直接下载 CDN 音频，进入本地缓存后按普通歌曲播放 */
    fun fetchAudiusTrack(t: AudiusTrack) {
        if (fetchingSongId != null) return
        fetchingSongId = "audius:${t.id}"
        fetchFail = null
        fetchFailId = null
        scope.launch {
            val outcome = withTimeoutOrNull(60_000L) {
                WebMusicCache.downloadAwait(
                    context = ctx,
                    url = t.streamUrl,
                    meta = SongMeta(t.title, t.artist),
                    referer = t.pageUrl,
                    cookie = "",
                    userAgent = AudiusSource.USER_AGENT
                )
            }
            fetchingSongId = null
            if (outcome == null) {
                fetchFailId = t.id
                fetchFail = SongFetchReason.NETWORK
                state.toast(tr("免费音源下载超时，稍后再试", "Free source timed out, try again"))
                return@launch
            }
            outcome.onSuccess { song ->
                MusicPlayer.addCachedWebSong(song)
                MusicPlayer.play(song)
                if (t.artwork.isNotBlank()) {
                    scope.launch { CoverRepository.saveRemote(ctx, t.title, t.artist, t.artwork) }
                }
                songSearchQuery = null
                state.toast(tr("已获取并播放：${song.title}", "Downloaded: ${song.title}"))
            }.onFailure { e ->
                fetchFailId = t.id
                fetchFail = SongFetchReason.NETWORK
                state.toast(
                    tr(
                        "免费音源下载失败：${e.message ?: "未知原因"}",
                        "Free source failed: ${e.message ?: "unknown"}"
                    )
                )
            }
        }
    }

    // ── 排行榜 / QQ 音乐收藏 ──

    /** 拉一个内置榜单；结果缓存在 QQMusic 里 10 分钟，下拉不会重复打接口 */
    fun loadQqChart(id: Int, force: Boolean = false) {
        qqChartId = id
        qqLoading = true
        qqError = null
        qqSongs = emptyList()
        qqBatchDone = 0
        qqBatchTotal = 0
        scope.launch {
            val r = withTimeoutOrNull(18_000L) { QQMusic.chart(id, 50, force) }
            qqLoading = false
            when (r) {
                is QQFetch.Ok -> {
                    qqSongs = r.songs
                    qqTitle = QQMusic.CHARTS.firstOrNull { it.topId == id }
                        ?.let { tr(it.nameZh, it.nameEn) }.orEmpty()
                }
                is QQFetch.Fail -> qqError = r.reason
                null -> qqError = QQFailReason.NETWORK
            }
        }
    }

    /** 同步收藏：识别链接 / ID → 带登录 Cookie 读歌单；识别不了就按粘贴文本兜底 */
    fun loadQqPlaylist(raw: String, cookie: String) {
        val id = QQMusic.extractPlaylistId(raw)
        if (id == null) {
            val textSongs = QQMusic.parsePlaylistText(raw)
            if (textSongs.isNotEmpty()) {
                qqSongs = textSongs
                qqTitle = tr("粘贴的歌单", "Imported playlist")
                qqError = null
                qqLoading = false
                return
            }
            qqError = null
            qqLoading = false
            state.toast(
                if (raw.startsWith("http"))
                    tr("请先在「我的音乐」里点开「我喜欢」歌单，再点同步", "Open your liked-songs playlist first, then tap Sync")
                else
                    tr("粘贴歌单链接、ID 或「歌名 - 歌手」文本", "Paste a playlist link, ID, or \"Song - Artist\" lines")
            )
            return
        }
        qqLoading = true
        qqError = null
        qqSongs = emptyList()
        qqBatchDone = 0
        qqBatchTotal = 0
        scope.launch {
            val r = withTimeoutOrNull(18_000L) { QQMusic.playlist(id, cookie, 100) }
            qqLoading = false
            when (r) {
                is QQFetch.Ok -> {
                    qqSongs = r.songs
                    qqTitle = tr("歌单 · ${r.songs.size} 首", "Playlist · ${r.songs.size} songs")
                }
                is QQFetch.Fail -> qqError = r.reason
                null -> qqError = QQFailReason.NETWORK
            }
        }
    }

    fun importQqText(text: String) {
        val list = QQMusic.parsePlaylistText(text)
        if (list.isEmpty()) {
            state.toast(tr("没识别到歌曲，每行写「歌名 - 歌手」", "No songs found. Use \"Song - Artist\" per line"))
            return
        }
        qqSongs = list
        qqTitle = tr("粘贴的歌单 · ${list.size} 首", "Imported playlist · ${list.size} songs")
        qqError = null
        qqLoading = false
        qqBatchDone = 0
        qqBatchTotal = 0
        state.toast(tr("已导入 ${list.size} 首，点「获取」下载", "Imported ${list.size} songs, tap Get to download"))
    }

    /** 榜单/收藏里点「获取」：自动搜曲库 → 挑标题最像的一条 → 换直链下载播放 */
    fun autoFetchQqSong(keyword: String) {
        if (fetchingSongId != null || qqBusyKeyword != null) return
        qqBusyKeyword = keyword
        fetchingSongId = "qq:$keyword"
        scope.launch {
            val list = withTimeoutOrNull(20_000L) { SongSearch.search(keyword, 8) }.orEmpty()
            val norm = { s: String -> s.lowercase().replace(Regex("""[^\u4e00-\u9fa5a-z0-9]"""), "") }
            val want = norm(keyword.substringBefore(' '))
            val pick = list.firstOrNull { r ->
                val t = norm(r.title)
                t.isNotBlank() && want.isNotBlank() && (t.contains(want) || want.contains(t))
            } ?: list.firstOrNull()
            qqBusyKeyword = null
            fetchingSongId = null
            if (pick == null) {
                state.toast(tr("没找到这首歌的可用音源，试试「搜索」", "No source found, try Search instead"))
            } else {
                fetchWebSong(pick)
            }
        }
    }

    /**
     * 批量获取当前榜单/歌单：逐首搜索 → 换直链 → 落缓存，不自动切歌。
     * 单首失败只记账继续，最后给成功/跳过/失败汇总；已缓存的同名同歌手直接跳过。
     */
    fun batchFetchQqSongs() {
        if (qqBatchRunning) return
        val list = qqSongs
        if (list.isEmpty()) {
            state.toast(tr("先选一个榜单或导入歌单", "Pick a chart or import a playlist first"))
            return
        }
        qqBatchRunning = true
        qqBatchDone = 0
        qqBatchTotal = list.size
        scope.launch {
            val norm = { s: String -> s.lowercase().replace(Regex("""[^\u4e00-\u9fa5a-z0-9]"""), "") }
            val ok = java.util.concurrent.atomic.AtomicInteger(0)
            val skipped = java.util.concurrent.atomic.AtomicInteger(0)
            val failed = java.util.concurrent.atomic.AtomicInteger(0)
            val done = java.util.concurrent.atomic.AtomicInteger(0)
            // 获取强度：最多 3 首并发跑「搜索 → 换直链 → 下载」，比逐首串行快数倍。
            // 单首失败只记账，站点 429/风控也只影响那一首，不中断整批。
            val gate = kotlinx.coroutines.sync.Semaphore(3)
            kotlinx.coroutines.coroutineScope {
                list.map { item ->
                    async {
                        gate.withPermit {
                            try {
                                val wantTitle = norm(item.title)
                                val cached = (MusicPlayer.webSongs + MusicPlayer.songs).any {
                                    val t = norm(it.title)
                                    val a = norm(it.artist)
                                    wantTitle.isNotBlank() && (t.contains(wantTitle) || wantTitle.contains(t)) &&
                                        (norm(item.artist).isBlank() || a.isBlank() || a.contains(norm(item.artist)))
                                }
                                if (cached) {
                                    skipped.incrementAndGet()
                                    return@withPermit
                                }
                                val listResult = withTimeoutOrNull(20_000L) {
                                    SongSearch.search(item.keyword, 12)
                                }.orEmpty()
                                val pick = listResult.firstOrNull { r ->
                                    val t = norm(r.title)
                                    wantTitle.isNotBlank() && (t.contains(wantTitle) || wantTitle.contains(t))
                                } ?: listResult.firstOrNull()
                                if (pick == null) {
                                    failed.incrementAndGet()
                                    return@withPermit
                                }
                                val outcome = withTimeoutOrNull(32_000L) { SongSearch.fetch(pick) }
                                val audio = (outcome as? SongFetch.Ok)?.audio
                                if (audio == null) {
                                    failed.incrementAndGet()
                                    return@withPermit
                                }
                                val downloaded = withTimeoutOrNull(45_000L) {
                                    WebMusicCache.downloadAwait(
                                        ctx,
                                        audio.url,
                                        SongMeta(audio.title, audio.artist),
                                        audio.referer,
                                        audio.cookie,
                                        audio.userAgent
                                    )
                                }?.getOrNull()
                                if (downloaded == null) {
                                    failed.incrementAndGet()
                                } else {
                                    WebMusicCache.saveLyrics(ctx, downloaded, audio.lrc)
                                    CoverRepository.saveRemote(ctx, audio.title, audio.artist, audio.cover)
                                    MusicPlayer.addCachedWebSong(downloaded)
                                    ok.incrementAndGet()
                                }
                            } finally {
                                qqBatchDone = done.incrementAndGet()
                            }
                        }
                    }
                }.awaitAll()
            }
            qqBatchDone = list.size
            qqBatchRunning = false
            state.toast(
                tr(
                    "批量获取完成：成功 ${ok.get()}，已存在 ${skipped.get()}，失败 ${failed.get()}",
                    "Batch finished: ${ok.get()} added, ${skipped.get()} already saved, ${failed.get()} failed"
                )
            )
        }
    }

    // ── 极简 UI 路由：每个入口都指向上面已有的真实能力，不新增假功能 ──

    /** 排行榜页：复用 QQMusicPage 的榜单 tab（含 8 个内置榜 + 一键获取真源） */
    fun openCharts(id: Int = qqChartId) {
        minimalSub = null
        songSearchQuery = null
        songSearchOpen = false
        qqTab = 0
        qqPanel = true
        loadQqChart(id)
    }

    /** QQ 音乐收藏页：登录 / 粘贴歌单，仍是原来那条内嵌链路 */
    fun openQqMusic() {
        minimalSub = null
        songSearchQuery = null
        songSearchOpen = false
        qqTab = 1
        qqPanel = true
    }

    /** 把 QQ 面板当前已同步的歌单一键导入「我的音乐」（占位入库 + 收藏 + 同名集合） */
    fun importQqCurrentToMy() {
        val items = qqSongs
        if (items.isEmpty()) {
            state.toast(tr("先同步一个歌单再导入", "Sync a playlist first"))
            return
        }
        state.toast(tr("正在导入 ${items.size} 首…", "Importing ${items.size}…"))
        scope.launch {
            val name = qqTitle.ifBlank { tr("QQ 音乐导入", "QQ Music import") }
            val report = ImportPipeline.importTracks(
                context = ctx,
                source = "qq",
                sourceLabel = "QQ音乐",
                collectionId = "qq:panel_current",
                collectionName = name,
                tracks = items.map { ImportPipeline.Track(it.title, it.artist, it.album, it.durationSec * 1000L) }
            )
            state.toast(
                if (report.imported > 0)
                    tr("已导入 ${report.imported} 首到「$name」", "Imported ${report.imported} to \"$name\"")
                else
                    tr("「$name」共 ${report.already} 首都已在收藏", "\"$name\": all ${report.already} already favorited")
            )
        }
    }

    fun openMinimalSearch() {
        // 每次进入搜索页都从空输入开始，避免上次搜完的词残留
        minimalSearchText = ""
        minimalSub = MinimalPage.SEARCH
        songSearchOpen = false
    }

    /** 搜索页回车 → 原来的 SongSearch 全网结果页，能力一点没变 */
    fun submitMinimalSearch(q: String) {
        val text = q.trim()
        if (text.isBlank()) {
            state.toast(tr("先输入歌名或歌手", "Type a song or artist first"))
            return
        }
        // 保留搜索页作为上一级：从结果返回时回到搜索，而不是直接回首页
        minimalSub = MinimalPage.SEARCH
        keyboard?.hide()
        runSongSearch(text)
    }

    fun openPlaylist(filter: PlaylistFilter = PlaylistFilter.ALL) {
        playlistFilter = filter
        minimalSub = MinimalPage.PLAYLIST
        showLyrics = false
    }

    /** 歌单 / 歌手页的「播放全部」：本地集合真播放，在线榜单去对应真实榜单页 */
    fun playAllNamed(name: String) {
        val local = when {
            name == tr("我的收藏", "My favorites") -> MusicPlayer.songsByIds(MusicPlayer.favorites.toList())
            name == tr("最近播放", "Recently played") -> MusicPlayer.songsByIds(MusicPlayer.recentIds)
            else -> (MusicPlayer.songs + MusicPlayer.webSongs).filter { it.artist == name }
        }
        if (local.isNotEmpty()) {
            MusicPlayer.playFrom(local, local.first())
            minimalSub = null
            minimalTab = 2
            showLyrics = false
        } else {
            val chart = QQMusic.CHARTS.firstOrNull { tr(it.nameZh, it.nameEn) == name }
            if (chart != null) openCharts(chart.topId) else submitMinimalSearch(name)
        }
    }

    /** 歌单 / 歌手页点选：收藏→收藏页，最近→播放列表，榜单→排行榜，歌手→搜索 */
    fun pickNamed(name: String) {
        when {
            name == tr("我的收藏", "My favorites") -> { minimalSub = null; minimalTab = 3 }
            name == tr("最近播放", "Recently played") -> openPlaylist(PlaylistFilter.RECENT)
            else -> {
                val chart = QQMusic.CHARTS.firstOrNull { tr(it.nameZh, it.nameEn) == name }
                val byArtist = (MusicPlayer.songs + MusicPlayer.webSongs).filter { it.artist == name }
                when {
                    chart != null -> openCharts(chart.topId)
                    byArtist.isNotEmpty() -> playAllNamed(name)
                    else -> submitMinimalSearch(name)
                }
            }
        }
    }

    fun clearMusicCache() {
        val n = MusicPlayer.clearWebCache(ctx)
        state.toast(tr("已清理 $n 首缓存歌曲", "Cleared $n cached songs"))
    }

    // 设置页这三项改成点击循环切换，避免再叠一层弹窗；用的仍是经典模式同一批 API
    fun cyclePlayMode() {
        MusicPlayer.cycleMode()
        state.toast(
            when (MusicPlayer.mode) {
                PlayMode.LOOP_ALL -> tr("列表循环", "Loop all")
                PlayMode.LOOP_ONE -> tr("单曲循环", "Repeat one")
                PlayMode.SHUFFLE -> tr("随机播放", "Shuffle")
                PlayMode.ORDER -> tr("顺序播放", "Sequence")
            }
        )
    }

    fun cycleEq() {
        val all = EqPreset.entries
        val next = all[(all.indexOf(MusicPlayer.eqPreset) + 1) % all.size]
        MusicPlayer.applyEqPreset(next)
        state.toast(tr("音效已切换：${next.name}", "Preset: ${next.name}"))
    }

    fun toggleFavToast() {
        val added = MusicPlayer.toggleFavorite()
        state.toast(
            if (added) tr("已收藏", "Added to favorites")
            else tr("已取消收藏", "Removed from favorites")
        )
    }

    fun cycleSleep() {
        val minutes = listOf(0, 15, 30, 45, 60, 90)
        val left = MusicPlayer.sleepMinutesLeft()
        val cur = if (left > 0) minutes.firstOrNull { it >= left } ?: 0 else 0
        val next = minutes[(minutes.indexOf(cur) + 1) % minutes.size]
        MusicPlayer.setSleepTimer(next)
        state.toast(
            if (next == 0) tr("睡眠定时已关闭", "Sleep timer off")
            else tr("将在 $next 分钟后暂停播放", "Playback stops in $next min")
        )
    }

    fun openAppPrivacy() {
        runCatching {
            ctx.startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * 歌词加载统一入口：自动加载与手动重试都走这里。
     * force=true 绕过「上次没查到」的记忆，用于换网络 / 换模型 / 导入 .lrc 之后重试。
     */
    val loadLyrics: (Boolean) -> Unit = { force ->
        val song = activeSong
        if (song != null && !lyricsLoading) {
            lyricsLoading = true
            lyricsFailed = false
            scope.launch {
                // 网关可能迟迟不回，25 秒后放行 UI，别一直转圈
                val result = withTimeoutOrNull(25_000L) {
                    LyricsRepository.fetch(song, state.aiFeatureEndpoint(AiFeature.SONG_LYRICS), force)
                }
                lyricsLoading = false
                if (result != null) lyrics = song.id to result else lyricsFailed = true
            }
        }
    }

    /** 自动匹配不到时，让用户纠正歌名/歌手再查一次（多源搜索链不变） */
    val loadLyricsWithQuery: (Pair<String, String>) -> Unit = { query ->
        val song = activeSong
        if (song != null && !lyricsLoading) {
            lyricsLoading = true
            lyricsFailed = false
            scope.launch {
                val result = withTimeoutOrNull(25_000L) {
                    LyricsRepository.fetch(song, state.aiFeatureEndpoint(AiFeature.SONG_LYRICS), true, query)
                }
                lyricsLoading = false
                if (result != null) lyrics = song.id to result else lyricsFailed = true
            }
        }
    }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        // MediaStore 全量查询放 IO，别在权限回调里卡住主线程
        if (granted) scope.launch { withContext(Dispatchers.IO) { MusicPlayer.scan(ctx) } }
    }

    // Android 13+ 不授予通知权限，通知中心就看不到播放状态
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) MusicExternal.refresh(ctx) }

    // 一次可导入多首：复制到私有目录并持久化，重启后仍在列表里
    val pickAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val n = MusicPlayer.addCustomSongs(ctx, uris)
        state.toast(
            if (n > 0) tr("已导入 ${n} 首歌曲", "Imported $n songs")
            else tr("没有导入成功，请检查文件格式", "Nothing imported, check the files")
        )
    }

    // 手动导入 .lrc：Android 11+ 分区存储下读不到音乐目录里的同名 .lrc，
    // 让用户从文件选择器选一次，歌词就完全离线可用了。
    val pickLrcLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val song = activeSong
        if (uri != null && song != null) {
            val ok = LyricsRepository.import(ctx, song, uri)
            state.toast(
                if (ok) tr("歌词已导入", "Lyrics imported")
                else tr("导入失败", "Import failed")
            )
            if (ok) {
                showLyrics = true
                loadLyrics(true)
            }
        }
    }

    // 截图导入：用户主动选一张其他 App 的歌单/收藏截图，OCR 出歌名后只保存记录，
    // 音源等点击播放时再按需获取，避免导入阶段一次性联网下载几十首。
    var shotImporting by remember { mutableStateOf(false) }
    var shotImportStatus by remember { mutableStateOf<String?>(null) }
    val pickPlaylistShot = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && !shotImporting) {
            shotImporting = true
            shotImportStatus = tr("正在识别截图…", "Reading screenshot...")
            scope.launch {
                val report = PlaylistShotImporter.fromUri(
                    ctx, uri,
                    onProgress = { msg -> shotImportStatus = msg },
                    ocrEndpoint = state.effectiveOcrEndpoint()
                )
                shotImporting = false
                shotImportStatus = report.note
                state.toast(report.note)
            }
        }
    }

    LaunchedEffect(Unit) {
        MusicPlayer.init(ctx)
        LyricsRepository.init(ctx)
        permissionGranted = ctx.checkSelfCompat(permission)
        // 有权限就重新扫一遍：导入过的歌曲由 scan 合并，不会把媒体库挡在门外。
        // MediaStore 全量查询放 IO，避免每次进音乐页都在主线程扫几千首。
        if (permissionGranted) withContext(Dispatchers.IO) { MusicPlayer.scan(ctx) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ctx.checkSelfCompat(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!hinted) {
            hinted = true
            state.toast(
                tr(
                    "点底部播放条进入播放页 · 长按歌曲打开更多操作",
                    "Tap the mini player to expand · long-press a song for more actions"
                )
            )
        }
    }

    // 点封面切到歌词、或切到另一首歌时自动匹配，不用再手动点「获取歌词」
    LaunchedEffect(showLyrics, activeSong?.id) {
        if (!showLyrics) return@LaunchedEffect
        val song = activeSong ?: return@LaunchedEffect
        if (lyrics?.first == song.id || lyricsLoading) return@LaunchedEffect
        loadLyrics(false)
    }

    // 返回键层级：后注册先响应。低 → 收歌词 → 退子页 → 回首页 → 全局退出
    // 主流音乐 App 的浮层都是逐层收起，不会一按返回就直接退出整个播放器。
    BackHandler(enabled = minimalTab != 0) { minimalTab = 0 }
    BackHandler(enabled = showLyrics) { showLyrics = false }
    BackHandler(enabled = libSection != null || libGroup != null) {
        if (libGroup != null) libGroup = null else libSection = null
    }
    // 搜索框展开时，返回键先收起它，而不是直接退出音乐页
    BackHandler(enabled = songSearchOpen) { songSearchOpen = false }
    // 极简二级页：比搜索框 / tab 更外一层，先收二级页
    BackHandler(enabled = minimalSub != null) { minimalSub = null }
    // 歌手详情叠在歌手列表之上，比二级页更靠前响应
    BackHandler(enabled = minimalArtist != null) { minimalArtist = null }
    // 最后注册 = 最先响应：结果面板开着时先收面板
    BackHandler(enabled = songSearchQuery != null) { songSearchQuery = null }
    // 排行榜比搜歌面板更外层，但两者互斥，单独一层保证返回键能收起
    BackHandler(enabled = qqPanel) { qqPanel = false }
    // 播放列表面板盖在播放页之上，最后注册，返回键先收起它
    BackHandler(enabled = queueSheetOpen) { queueSheetOpen = false }

    // 展开后自动聚焦并拉起键盘；收起时收键盘。等一帧，等输入框真正进入组合再要焦点
    LaunchedEffect(songSearchOpen) {
        if (songSearchOpen) {
            withFrameNanos { }
            runCatching { searchFocus.requestFocus() }
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    val minimalTabTitles = listOf(
        "首页" to "Home", "发现" to "Discover", "播放" to "Player",
        "收藏" to "Saved", "我的" to "Mine"
    )
    val sub = minimalSub
    val artistPage = minimalArtist
    // 极简「播放」tab 是设计稿那样的全屏播放页：自带返回钮，
    // 此时收起外层标题栏 / 常驻 mini 播放条 / 5 tab 底栏，避免两层导航叠在一起。
    val fullScreenPlayer = sub == null && artistPage == null && minimalTab == 2 &&
        !qqPanel && songSearchQuery == null && !songSearchOpen
    // 主标签页由内容区自己展示大标题，顶栏只保留返回，避免重复标题占用首屏。
    val rootTabPage = sub == null && artistPage == null && !qqPanel && songSearchQuery == null && !songSearchOpen
    val minimalTitle = when {
        artistPage != null -> artistPage
        qqPanel -> if (qqTab == 0) tr("排行榜", "Charts") else tr("读取 QQ 音乐", "Read QQ Music")
        sub != null -> tr(sub.zh, sub.en)
        else -> minimalTabTitles[minimalTab].let { tr(it.first, it.second) }
    }
    val title = songSearchQuery?.let {
        tr("搜索「%s」", "Search \"%s\"").replace("%s", it)
    } ?: minimalTitle

    // 横屏播放器是净屏：状态栏与底部手势条交给页面自己，不能让 Light 主题的
    // 白色窗口底从顶部/底部漏出来（截图里那条白条就是 statusBarsPadding 让出来的）。
    // 只在横屏期间挂这个 effect，转回竖屏 / 离开音乐页时 onDispose 全部还原。
    val immersiveLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (immersiveLandscape) ImmersiveBarsEffect()

    // 全屏播放页是叠在主框架之上的覆盖层，必须铺实心底：
    // 自定义背景态下 MinimalUi.Bg = Transparent，评论页 / 主框架会透上来和唱片叠字。
    // 其余 tab 仍是页面根，保留透明以透出自定义背景图。
    Box(
        Modifier.fillMaxSize()
            .background(if (fullScreenPlayer) Layer.Opaque else MinimalUi.Bg)
            // 系统栏区域归页面：再叠一层纯黑，横屏下绝不露出白色窗口底
            .then(if (immersiveLandscape) Modifier.background(Color.Black) else Modifier)
    ) {
        // 4D 震动 / 动感闪光：挂在主框架上，音效页关闭后仍跟随播放生效
        SoundLabEffects()
        Column(
            Modifier.fillMaxSize()
                // 横屏时系统栏已隐藏，继续避让只会把内容推下去、又让出一条白边
                .then(if (immersiveLandscape) Modifier else Modifier.statusBarsPadding())
        ) {
            // ── 顶部：左返回 + 居中标题 + 右侧展开式搜索（全屏播放页时收起）──
            Box(
                Modifier.fillMaxWidth()
                    .height(
                        when {
                            fullScreenPlayer -> 0.dp
                            rootTabPage -> 48.dp
                            else -> 68.dp
                        }
                    )
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                // 左上角返回：搜索展开时先收搜索框；子页逐层退回；顶层退出音乐页
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = tr("返回", "Back"),
                    tint = MinimalUi.Text,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        // 根 tab 页不显示返回箭头（QQ 音乐首页同样没有），
                        // 子页/浮层才出现；浅色主题下用正文色，避免白箭头压在浅底上不可见
                        .graphicsLayer { alpha = if (rootTabPage) 0f else 1f }
                        .clickable(enabled = !rootTabPage) {
                            when {
                                // 排行榜是叠在当前页上的二级页，先收起它回到进入前的页面
                                qqPanel -> qqPanel = false
                                minimalArtist != null -> minimalArtist = null
                                songSearchQuery != null -> songSearchQuery = null
                                songSearchOpen -> songSearchOpen = false
                                minimalSub != null -> minimalSub = null
                                minimalTab != 0 -> minimalTab = 0
                                libGroup != null -> libGroup = null
                                libSection != null -> libSection = null
                                else -> onExit()
                            }
                        }
                        .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
                        .size(28.dp)
                )
                Box(
                    Modifier
                        .graphicsLayer { alpha = if (rootTabPage) 0f else titleAlpha }
                        // 标题胶囊：不透明黑白底 + 固定墨色。这层壳是音乐页自己画的，
                        // 文字色原来跟着 AppPalette（设置里的文字色）走，压到自定义背景照片上就看不见了。
                        .clip(RoundedCornerShape(50))
                        .background(FixedInk.ChipSurface)
                        .padding(horizontal = 16.dp, vertical = 3.dp)
                ) {
                    // 切页 / 进子页时标题交叉淡入，别像之前那样硬切文字
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            (fadeIn(tween(200)) + scaleIn(initialScale = 0.94f, animationSpec = tween(200)))
                                .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.94f, animationSpec = tween(140)))
                        },
                        contentAlignment = Alignment.Center
                    ) { t ->
                        Text(
                            t,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = FixedInk.OnChip,
                            fontFamily = FontFamily.SansSerif,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.clickable(enabled = !songSearchOpen && libSection != null) {
                                if (libGroup != null) libGroup = null else libSection = null
                            }
                        )
                    }
                }

                // 顶栏搜索入口已移除；搜索仍可通过首页 / 发现页的搜索卡片进入

            }

            // ── 内容区：搜歌结果直接嵌进页面（不再弹窗），其余时候由极简 5 tab 接管 ──
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // 每个 tab / 子页独立保存滚动位置：切走再切回来仍停在原处，
                // 不再每次都跳回顶部（列表越长，这个断点越明显）。
                val pageStateHolder = rememberSaveableStateHolder()
                val pageKey = when {
                    minimalArtist != null -> "artist:$minimalArtist"
                    qqPanel -> "panel:qq"
                    songSearchQuery != null -> "search:${songSearchQuery}"
                    minimalSub != null -> "sub:${minimalSub?.name}"
                    else -> "tab:$minimalTab"
                }
                pageStateHolder.SaveableStateProvider(pageKey) {
                val searchQ = songSearchQuery
                val artistName = minimalArtist
                if (artistName != null) {
                    val unknownArtist = tr("未知歌手", "Unknown artist")
                    val artistSongs = (MusicPlayer.songs + MusicPlayer.webSongs).filter {
                        it.artist == artistName || it.artist.ifBlank { unknownArtist } == artistName
                    }
                    MinimalArtistDetailPage(
                        name = artistName,
                        songs = artistSongs,
                        onPlay = { song ->
                            MusicPlayer.playFrom(artistSongs, song, QueueSource.LIBRARY)
                            minimalArtist = null
                            minimalSub = null
                            minimalTab = 2
                        },
                        onPlayAll = {
                            if (artistSongs.isNotEmpty()) {
                                MusicPlayer.playFrom(artistSongs, artistSongs.first(), QueueSource.LIBRARY)
                                minimalArtist = null
                                minimalSub = null
                                minimalTab = 2
                            }
                        },
                        onDismiss = { minimalArtist = null },
                        onLongClick = { songAction = it }
                    )
                } else if (qqPanel) {
                    QQMusicPage(
                        tab = qqTab,
                        onTab = { t ->
                            qqTab = t
                            qqSongs = emptyList()
                            qqError = null
                            qqLoading = false
                            if (t == 0) loadQqChart(qqChartId)
                        },
                        charts = QQMusic.CHARTS,
                        chartId = qqChartId,
                        songs = qqSongs,
                        loading = qqLoading,
                        error = qqError,
                        title = qqTitle,
                        busyKeyword = qqBusyKeyword,
                        batchRunning = qqBatchRunning,
                        batchDone = qqBatchDone,
                        batchTotal = qqBatchTotal,
                        onSelectChart = { loadQqChart(it) },
                        onLoadPlaylist = { raw, cookie -> loadQqPlaylist(raw, cookie) },
                        onImportText = { importQqText(it) },
                        onSearch = { q ->
                            qqPanel = false
                            runSongSearch(q)
                        },
                        onAutoFetch = { autoFetchQqSong(it) },
                        onBatchFetch = { batchFetchQqSongs() },
                        onImportCurrent = { importQqCurrentToMy() },
                        onDismiss = { qqPanel = false }
                    )
                } else if (searchQ != null) {
                    val q = searchQ
                    // 多词查询按空格拆开，要求每个词都命中歌名/歌手/专辑，避免整串匹配不到
                    val terms = q.split(Regex("\\s+")).filter { it.isNotBlank() }
                    val localHits = (MusicPlayer.songs + MusicPlayer.webSongs).filter { s ->
                        terms.all { t ->
                            SearchUtils.score(t, "${s.title} ${s.artist} ${s.album} ${s.rawTitle}") > 0
                        }
                    }.take(6)
                    SongSearchPage(
                        query = q,
                        local = localHits,
                        results = songResults,
                        phase = songNetPhase,
                        fetchingId = fetchingSongId,
                        fail = fetchFail,
                        failId = fetchFailId,
                        audius = audiusResults,
                        audiusPhase = audiusPhase,
                        onPickAudius = { t -> fetchAudiusTrack(t) },
                        onPickLocal = { s ->
                            MusicPlayer.play(s)
                            songSearchQuery = null
                        },
                        onPickWeb = { r -> fetchWebSong(r) },
                        onOpenPage = { r ->
                            songSearchQuery = null
                            state.openUrl(r.pageUrl)
                        },
                        onOpenWeb = {
                            songSearchQuery = null
                            state.openSongSearch(q)
                        },
                        onRetry = { runSongSearch(q) },
                        onDismiss = { songSearchQuery = null }
                    )
                } else {
                    // ── 极简 10 页：全部复用原有状态/能力，只是重新组织信息架构 ──
                    val allSongs = MusicPlayer.songs + MusicPlayer.webSongs
                    // 每日推荐是曲库按日稳定挑选，明确排除当前正在播放的歌
                    val recommend = dailyRecommendation(allSongs, MusicPlayer.current?.id)
                    // 每日 30 首：同一份曲库、同一套稳定种子，跨天自动换一批
                    val dailySongs = dailyThirty(allSongs)
                    val picks = allSongs.take(5)
                    val favSongs = MusicPlayer.songsByIds(MusicPlayer.favorites.toList())
                    val recentSongs = MusicPlayer.songsByIds(MusicPlayer.recentIds)
                    val downloaded = MusicPlayer.webSongs.size
                    val recentCached = MusicPlayer.webSongs.takeLast(8).reversed()
                    val cacheSummary = tr(
                        "网页歌曲缓存 $downloaded 首 · 存在应用私有目录",
                        "$downloaded web songs cached privately"
                    )
                    val artistItems = allSongs
                        .groupBy { it.artist.ifBlank { tr("未知歌手", "Unknown artist") } }
                        .map { (k, v) -> k to v.size }
                        .sortedByDescending { it.second }
                        .take(30)
                    val playlistItems = buildList<Pair<String, Int>> {
                        add(tr("我的收藏", "My favorites") to MusicPlayer.favorites.size)
                        add(tr("最近播放", "Recently played") to MusicPlayer.recentIds.size)
                        QQMusic.CHARTS.take(6).forEach { add(tr(it.nameZh, it.nameEn) to 50) }
                    }
                    // 「我的」页只放自己的收藏/最近，排行榜属于「发现」页
                    val profilePlaylistItems = buildList<Pair<String, Int>> {
                        add(tr("我的收藏", "My favorites") to MusicPlayer.favorites.size)
                        add(tr("最近播放", "Recently played") to MusicPlayer.recentIds.size)
                    }
                    val categories = listOf(
                        tr("治愈", "Chill"), tr("放松", "Calm"), tr("电音", "EDM"), tr("国风", "Guofeng")
                    )
                    val hotWords = (
                        listOf("周杰伦", "林俊杰", "陈奕迅", "邓紫棋", "薛之谦", "毛不易") +
                            allSongs.map { it.artist }.filter { it.isNotBlank() }.take(3)
                        ).distinct().take(8)
                    val searchTerms = minimalSearchText.split(Regex("\\s+")).filter { it.isNotBlank() }
                    val localHits = if (searchTerms.isEmpty()) emptyList()
                    else allSongs.filter { s ->
                        searchTerms.all { t ->
                            SearchUtils.score(t, "${s.title} ${s.artist} ${s.album} ${s.rawTitle}") > 0
                        }
                    }.take(6)

                    when (sub) {
                        MinimalPage.SEARCH -> MinimalSearchPage(
                            query = minimalSearchText,
                            onQuery = { minimalSearchText = it },
                            onSubmit = { submitMinimalSearch(it) },
                            hotWords = hotWords,
                            localHits = localHits,
                            onPlayLocal = { song ->
                                MusicPlayer.playFrom(localHits, song, QueueSource.LIBRARY)
                                minimalSub = null
                                minimalTab = 2
                            },
                            onOpenCharts = { openCharts() },
                            onDismiss = { minimalSub = null },
                            onLongClick = { songAction = it }
                        )
                        MinimalPage.DAILY -> MinimalDailyPage(
                            songs = dailySongs,
                            onPlay = { song ->
                                MusicPlayer.playFrom(dailySongs, song, QueueSource.LIBRARY)
                                minimalSub = null
                                minimalTab = 2
                            },
                            onPlayAll = {
                                if (dailySongs.isNotEmpty()) {
                                    MusicPlayer.playFrom(dailySongs, dailySongs.first(), QueueSource.LIBRARY)
                                    minimalSub = null
                                    minimalTab = 2
                                }
                            },
                            onDismiss = { minimalSub = null },
                            onLongClick = { songAction = it }
                        )
                        MinimalPage.PLAYLISTS -> MinimalPlaylistSquarePage(
                            categories = categories,
                            playlists = playlistItems,
                            onPick = { c -> if (c in categories) submitMinimalSearch(c) else pickNamed(c) },
                            onPlayAll = { playAllNamed(it) },
                            onDismiss = { minimalSub = null }
                        )
                        MinimalPage.ARTISTS -> MinimalArtistPage(
                            artists = artistItems,
                            onOpen = { minimalArtist = it },
                            onPlayAll = { playAllNamed(it) },
                            onDismiss = { minimalSub = null }
                        )
                        MinimalPage.LOCAL -> MinimalLocalPage(
                            downloaded = downloaded,
                            cacheSummary = cacheSummary,
                            recent = recentCached,
                            onImport = { pickAudioLauncher.launch(arrayOf("audio/*")) },
                            onRescan = {
                                scope.launch {
                                    val n = withContext(Dispatchers.IO) { MusicPlayer.scan(ctx) }
                                    state.toast(tr("已扫描到 $n 首歌曲", "Found $n songs"))
                                }
                            },
                            onClearCache = { clearMusicCache() },
                            onOpenLibrary = { minimalSub = MinimalPage.LIBRARY },
                            onPlay = { MusicPlayer.playFrom(recentCached, it); minimalSub = null; minimalTab = 2 },
                            onDismiss = { minimalSub = null },
                            onLongClick = { songAction = it }
                        )
                        MinimalPage.SETTINGS -> MinimalSettingsPage(
                            state = state,
                            cacheSummary = cacheSummary,
                            notifEnabled = state.settings.notifEnabled,
                            onTheme = { state.updateThemeMode(it) },
                            onLanguage = { state.updateLanguage(if (state.language == "zh") "en" else "zh") },
                            onNotif = {
                                state.updateSettings(notifEnabled = it)
                                MusicExternal.refresh(ctx)
                            },
                            onPlayMode = { cyclePlayMode() },
                            onEq = { minimalSub = null; soundFxPanelOpen = true },
                            onSleep = { cycleSleep() },
                            onAlarm = { showAlarmDialog = true },
                            onPlaylistView = { showFilterDialog = true },
                            onImportLrc = {
                                if (activeSong == null) state.toast(tr("当前没有在播的歌", "Nothing playing"))
                                else pickLrcLauncher.launch("*/*")
                            },
                            onImport = { minimalSub = MinimalPage.IMPORT },
                            onRemoveImportedLyrics = {
                                val s = activeSong
                                if (s == null) {
                                    state.toast(tr("当前没有在播的歌", "Nothing playing"))
                                } else if (!LyricsRepository.hasImported(s, ctx)) {
                                    state.toast(tr("当前歌曲没有导入的歌词", "No imported lyrics for this song"))
                                } else {
                                    LyricsRepository.removeImported(s, ctx)
                                    state.toast(tr("已移除，将重新在线匹配", "Removed, matching online again"))
                                    loadLyrics(true)
                                }
                            },
                            onClearImportedSongs = {
                                val n = MusicPlayer.clearCustomSongs(ctx)
                                state.toast(
                                    if (n > 0) tr("已移除 ${n} 首导入歌曲", "Removed $n imported songs")
                                    else tr("没有导入的歌曲", "No imported songs")
                                )
                            },
                            onClearCache = { clearMusicCache() },
                            onPrivacy = { openAppPrivacy() },
                            onDismiss = { minimalSub = null }
                        )
                        MinimalPage.REPORT -> ListenStatsPage(
                            songs = allSongs,
                            onPlay = { song ->
                                MusicPlayer.play(song)
                                minimalSub = null
                                minimalTab = 2
                            },
                            onDismiss = { minimalSub = null }
                        )
                        MinimalPage.IMPORT -> MusicImportPage(
                            onDismiss = { minimalSub = null },
                            onOpenQqLogin = { openQqMusic() },
                            onPlaySongs = { list, first ->
                                MusicPlayer.playFrom(list, first, QueueSource.LIBRARY)
                                minimalSub = null
                                minimalTab = 2
                            },
                            onToast = { state.toast(it) }
                        )
                        MinimalPage.PLAYLIST -> PlaylistPage(
                            granted = permissionGranted,
                            filter = playlistFilter,
                            onRequestPermission = { permissionLauncher.launch(permission) },
                            onAddFile = { pickAudioLauncher.launch(arrayOf("audio/*")) },
                            onSongAction = { songAction = it },
                            onPlay = { song, list ->
                                MusicPlayer.playFrom(
                                    list, song,
                                    when (playlistFilter) {
                                        PlaylistFilter.RECENT -> QueueSource.RECENT
                                        PlaylistFilter.FAVORITE -> QueueSource.FAVORITES
                                        PlaylistFilter.ALL -> QueueSource.LIBRARY
                                    }
                                )
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                        MinimalPage.LIBRARY -> LibraryPage(
                            section = libSection,
                            group = libGroup,
                            onOpenCharts = { openCharts() },
                            onOpenFavorites = { minimalSub = null; minimalTab = 3 },
                            onOpenQqMusic = { openQqMusic() },
                            onImportSongs = { pickAudioLauncher.launch(arrayOf("audio/*")) },
                            onRescan = {
                                scope.launch {
                                    val n = withContext(Dispatchers.IO) { MusicPlayer.scan(ctx) }
                                    state.toast(tr("已扫描到 $n 首歌曲", "Found $n songs"))
                                }
                            },
                            onOpen = { kind ->
                                if (kind == LibKind.PLAYLISTS) openPlaylist()
                                else { libSection = kind; libGroup = null }
                            },
                            onGroup = { libGroup = it },
                            onSongAction = { songAction = it },
                            onPlay = {
                                MusicPlayer.play(it)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                        null -> when (minimalTab) {
                            0 -> MinimalHomePage(
                                recommend = recommend,
                                daily = dailySongs,
                                picks = picks,
                                recent = recentSongs,
                                charts = QQMusic.CHARTS,
                                onSearch = { openMinimalSearch() },
                                onSettings = { minimalSub = MinimalPage.SETTINGS },
                                onDaily = { minimalSub = MinimalPage.DAILY },
                                onChart = { id -> openCharts(id) },
                                onCharts = { openCharts() },
                                onPlaylists = { minimalSub = MinimalPage.PLAYLISTS },
                                onArtists = { minimalSub = MinimalPage.ARTISTS },
                                onPrivateFm = {
                                    if (allSongs.isNotEmpty()) {
                                        // 私人 FM：本地曲库随机成队，先播第一首
                                        val fm = allSongs.shuffled()
                                        MusicPlayer.playFrom(fm, fm.first(), QueueSource.LIBRARY)
                                        minimalTab = 2
                                    } else {
                                        minimalSub = MinimalPage.LOCAL
                                    }
                                },
                                onPlayFrom = { song, list ->
                                    MusicPlayer.playFrom(list, song, QueueSource.LIBRARY)
                                    minimalTab = 2
                                },
                                onPlayDaily = {
                                    if (dailySongs.isNotEmpty()) {
                                        MusicPlayer.playFrom(dailySongs, dailySongs.first(), QueueSource.LIBRARY)
                                        minimalTab = 2
                                    }
                                },
                                onPlayRecent = { song, list ->
                                    MusicPlayer.playFrom(list, song, QueueSource.RECENT)
                                    minimalTab = 2
                                },
                                onLocal = { minimalSub = MinimalPage.LOCAL },
                                onLongClick = { songAction = it }
                            )
                            1 -> MinimalDiscoverPage(
                                charts = QQMusic.CHARTS,
                                artists = artistItems,
                                onChart = { id -> openCharts(id) },
                                onCharts = { openCharts() },
                                onPlaylists = { minimalSub = MinimalPage.PLAYLISTS },
                                onArtists = { minimalSub = MinimalPage.ARTISTS },
                                onArtist = { minimalArtist = it },
                                onSearch = { openMinimalSearch() },
                                onPlaylist = { openPlaylist() },
                                onQqMusic = { openQqMusic() }
                            )
                            // 播放 tab 直接复用原播放页：封面↔歌词舞台、进度、六源歌词一项不少
                            2 -> NowPlayingPage(
                                state = state,
                                showLyrics = showLyrics,
                                lyrics = lyrics?.takeIf { it.first == activeSong?.id }?.second,
                                lyricsLoading = lyricsLoading,
                                lyricsFailed = lyricsFailed,
                                onToggleLyrics = { showLyrics = !showLyrics },
                                onFetchLyrics = loadLyrics,
                                onManualSearch = {
                                    searchTitle = activeSong?.let { SongNameParser.clean(it.title) }.orEmpty()
                                    searchArtist = activeSong?.let {
                                        SongNameParser.cleanArtist(it.artist).orEmpty()
                                    }.orEmpty()
                                    showLyricsSearch = true
                                },
                                chrome = true,
                                onBack = { minimalTab = 0; showLyrics = false },
                                onOpenPlaylist = { queueSheetOpen = true },
                                onOpenComments = { commentsOpen = true },
                                onCycleMode = { cyclePlayMode() },
                                onCycleEq = { soundFxPanelOpen = true },
                                onToggleFavorite = { toggleFavToast() },
                                onSongAction = { songAction = it }
                            )
                            3 -> MinimalFavoritesPage(
                                songs = favSongs,
                                onPlay = { MusicPlayer.playFrom(favSongs, it, QueueSource.FAVORITES); minimalTab = 2 },
                                onLongClick = { songAction = it },
                                onToggleFavorite = {
                                    MusicPlayer.toggleFavorite(it.id)
                                    state.toast(tr("已取消收藏", "Removed from favorites"))
                                },
                                onLibrary = { minimalSub = MinimalPage.LIBRARY },
                                onCharts = { openCharts() },
                                onImport = { minimalSub = MinimalPage.IMPORT }
                            )
                            else -> MinimalProfilePage(
                                state = state,
                                stats = Triple(allSongs.size, MusicPlayer.favorites.size, MusicPlayer.recentIds.size),
                                songs = allSongs,
                                recentSongs = recentSongs,
                                playCounts = MusicPlayer.playCounts,
                                playlists = profilePlaylistItems,
                                importing = shotImporting,
                                importStatus = shotImportStatus,
                                onImportShot = {
                                    if (!shotImporting) pickPlaylistShot.launch("image/*")
                                },
                                // 独立音乐 App 没有主 App 的设置页，编辑资料直接进音乐设置
                                onEditProfile = {
                                    if (standalone) minimalSub = MinimalPage.SETTINGS
                                    else state.openScreen(ExtraScreen.SETTINGS)
                                },
                                onLocal = { minimalSub = MinimalPage.LOCAL },
                                onLibrary = { minimalSub = MinimalPage.LIBRARY },
                                onSong = {
                                    MusicPlayer.playFrom(recentSongs, it, QueueSource.RECENT)
                                    minimalTab = 2
                                },
                                onSettings = { minimalSub = MinimalPage.SETTINGS },
                                onPlaylist = { openPlaylist() },
                                onPlaylistPick = { pickNamed(it) },
                                onReport = { minimalSub = MinimalPage.REPORT }
                            )
                        }
                    }
                }
                }
            } // 内容区 Box

            // 全屏播放页已有完整控制区与返回钮，不再叠 mini 条和底部导航
            if (!fullScreenPlayer) {
                // ── 常驻底部播放条（非全屏播放页时，点它回到播放页）──
                PlayerBar(
                    minimal = true,
                    onOpenNowPlaying = {
                        minimalSub = null
                        minimalTab = 2
                        showLyrics = false
                    }
                )
                // 极简 5 tab 在最底下，自带 navigationBarsPadding；上面那条 mini 播放条常驻
                MinimalBottomBar(
                    current = minimalTab,
                    onSelect = { idx ->
                        // 真的切换了才给一次轻微触感；点当前 tab 只做归位，不震
                        if (idx != minimalTab) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        minimalSub = null
                        minimalArtist = null
                        // 切 tab 必须回到该 tab 的根内容：清掉搜索结果 / 榜单浮层，
                        // 否则点「首页」仍会被搜索结果页拦住
                        songSearchQuery = null
                        songSearchOpen = false
                        qqPanel = false
                        libSection = null
                        libGroup = null
                        showLyrics = false
                        minimalTab = idx
                    }
                )
            }
        }

        // ── 菜单里拉起的对话框：都不占静态界面 ──
        if (showSleepDialog) {
            val minutes = listOf(0, 15, 30, 45, 60, 90)
            val left = MusicPlayer.sleepMinutesLeft()
            val afterCurrent = MusicPlayer.sleepAfterCurrent
            MusicOptionDialog(
                title = when {
                    afterCurrent -> tr("睡眠定时（播完当前歌停）", "Sleep timer (after this song)")
                    left > 0 -> tr("睡眠定时（还剩 ${left} 分钟）", "Sleep timer ($left min left)")
                    else -> tr("睡眠定时", "Sleep timer")
                },
                options = listOf(
                    tr("关闭", "Off"), tr("15 分钟", "15 min"), tr("30 分钟", "30 min"),
                    tr("45 分钟", "45 min"), tr("60 分钟", "60 min"), tr("90 分钟", "90 min"),
                    tr("播完当前歌再停", "After current song")
                ),
                // 「播完当前歌」不是分钟档，选中态单独判断
                selected = if (afterCurrent) 6 else 0,
                onPick = { idx ->
                    if (idx == 6) {
                        MusicPlayer.setSleepAfterCurrent()
                        showSleepDialog = false
                        state.toast(tr("当前歌播完后自动停止", "Stops after this song"))
                    } else {
                        MusicPlayer.setSleepTimer(minutes[idx])
                        showSleepDialog = false
                        state.toast(
                            if (minutes[idx] == 0) tr("睡眠定时已关闭", "Sleep timer off")
                            else tr("将在 ${minutes[idx]} 分钟后暂停播放", "Playback stops in ${minutes[idx]} min")
                        )
                    }
                },
                onDismiss = { showSleepDialog = false }
            )
        }
        if (showAlarmDialog) {
            MusicAlarmDialog(
                current = MusicPlayer.alarmText(),
                onSet = { h, m ->
                    MusicPlayer.setAlarmAt(h, m)
                    showAlarmDialog = false
                    state.toast(
                        tr(
                            "音乐闹钟已设为 %02d:%02d，到点淡入播放收藏".format(h, m),
                            "Alarm set to %02d:%02d, fades in from favorites".format(h, m)
                        )
                    )
                },
                onCancelAlarm = {
                    MusicPlayer.cancelAlarm()
                    showAlarmDialog = false
                    state.toast(tr("音乐闹钟已清除", "Music alarm cleared"))
                },
                onDismiss = { showAlarmDialog = false }
            )
        }
        // 驾驶模式不再是本页覆盖层：交给 MainActivity 的 ExtraScreen.DRIVING 全屏页
        // 歌曲分享卡片：生成完从底部弹出保存/分享
        songCardBmp?.let { bmp ->
            ExportSheet(
                bitmap = bmp,
                title = tr("正在听", "Now playing"),
                onDismiss = { songCardBmp = null },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        // 音效库：库页菜单 / 设置 / 极简播放器都从这里打开，跟播放页里的是同一个面板
        if (soundFxPanelOpen) {            Dialog(
                onDismissRequest = { soundFxPanelOpen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                SoundEffectPanel(onClose = { soundFxPanelOpen = false })
            }
        }
        if (showFilterDialog) {
            MusicOptionDialog(
                title = tr("播放列表视图", "Playlist view"),
                options = listOf(
                    tr("全部歌曲", "All songs"), tr("最近播放", "Recently played"),
                    tr("我的收藏", "Favorites")
                ),
                selected = PlaylistFilter.entries.indexOf(playlistFilter),
                onPick = { idx ->
                    playlistFilter = PlaylistFilter.entries[idx]
                    showFilterDialog = false
                },
                onDismiss = { showFilterDialog = false }
            )
        }
        if (showLyricsSearch) {
            LyricsSearchDialog(
                initialTitle = searchTitle,
                initialArtist = searchArtist,
                rawTitle = activeSong?.rawTitle.orEmpty(),
                onSubmit = { query ->
                    showLyrics = true
                    loadLyricsWithQuery(query)
                },
                onDismiss = { showLyricsSearch = false }
            )
        }
        // 列表长按：操作菜单 → 改名 / 删除确认
        songAction?.let { s ->
            SongActionDialog(
                song = s,
                onPlay = { MusicPlayer.play(s); songAction = null },
                onPlayNext = {
                    MusicPlayer.playNext(s)
                    songAction = null
                    state.toast(tr("已插队：「${s.title}」将在当前歌曲后播放", "Queued: ${s.title} plays next"))
                },
                onToggleFavorite = {
                    val added = MusicPlayer.toggleFavorite(s.id)
                    songAction = null
                    state.toast(
                        if (added) tr("已收藏", "Added to favorites")
                        else tr("已取消收藏", "Removed from favorites")
                    )
                },
                onRename = {
                    renameText = s.title
                    songRename = s
                    songAction = null
                },
                onSwap = {
                    val ok = MusicPlayer.swapSongMeta(ctx, s.id)
                    songAction = null
                    state.toast(
                        if (ok) tr("已互换歌名和歌手", "Title and artist swapped")
                        else tr("互换失败", "Could not swap")
                    )
                    // 互换通常是因为元数据方向错了，歌词检索也要立刻按修正后的
                    // 「歌名 = 原歌手、歌手 = 原歌名」重新匹配一次。
                    if (ok && activeSong?.id == s.id) {
                        loadLyricsWithQuery(s.artist.trim() to s.title.trim())
                    }
                },
                onDelete = { songDelete = s; songAction = null },
                onDismiss = { songAction = null }
            )
        }
        songRename?.let { s ->
            RenameSongDialog(
                initial = renameText,
                onSubmit = { t ->
                    val ok = MusicPlayer.renameSong(ctx, s.id, t)
                    songRename = null
                    state.toast(
                        if (ok) tr("歌名已更新，正在重新匹配歌词", "Title updated, re-matching lyrics")
                        else tr("修改失败", "Could not rename")
                    )
                    // 改名最常见的动机就是元数据写错导致歌词搜不到，立刻按新名重查一次
                    if (ok && activeSong?.id == s.id) loadLyricsWithQuery(t.trim() to s.artist)
                },
                onDismiss = { songRename = null }
            )
        }
        songDelete?.let { s ->
            DeleteSongDialog(
                song = s,
                onConfirm = {
                    val ok = MusicPlayer.deleteSong(ctx, s.id)
                    songDelete = null
                    state.toast(
                        if (ok) tr("已从列表删除", "Removed from library")
                        else tr("删除失败", "Could not delete")
                    )
                },
                onDismiss = { songDelete = null }
            )
        }
        // 播放页「列表」按钮：底部队列弹层，默认展示当前播放来源的歌曲
        if (queueSheetOpen) {
            MusicQueueSheet(
                source = MusicPlayer.queueSource,
                queue = MusicPlayer.currentQueue,
                recent = MusicPlayer.songsByIds(MusicPlayer.recentIds),
                favorites = MusicPlayer.songsByIds(MusicPlayer.favorites.toList()),
                onPlay = { list, song, from ->
                    MusicPlayer.playFrom(list, song, from)
                    minimalTab = 2
                    queueSheetOpen = false
                },
                onDismiss = { queueSheetOpen = false }
            )
        }
        // 评论弹层：歌词页「评」与黑胶页「评论」共用，切歌后自动绑定当前播放歌曲
        if (commentsOpen) {
            BackHandler { commentsOpen = false }
            SongCommentsSheet(song = activeSong, onDismiss = { commentsOpen = false })
        }
        // 搜歌结果已经内嵌进内容区，不再以弹窗形式盖住播放页
    }
}

/* ────────────────────────────────────────────────────────────
   高级弹窗骨架：遮罩淡入 + 卡片缩放上浮 + 大圆角 + 柔和投影，
   替代原生 AlertDialog；配色全部走 AppPalette，跟随深浅色主题。
   ──────────────────────────────────────────────────────────── */

/** 弹窗底部操作槽：主按钮（品牌渐变/实心）+ 次按钮（描边/幽灵） */
@Composable
internal fun MusicDialogActions(
    primaryLabel: String?,
    onPrimary: (() -> Unit)?,
    onDismiss: () -> Unit,
    primaryDanger: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GhostDialogButton(tr("取消", "Cancel"), onClick = onDismiss, modifier = Modifier.weight(1f))
        if (primaryLabel != null && onPrimary != null) {
            PrimaryDialogButton(
                label = primaryLabel,
                onClick = onPrimary,
                danger = primaryDanger,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 次按钮：透明底 + 细描边，文字用次级色 */
@Composable
private fun GhostDialogButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pressed = remember { MutableInteractionSource() }
    val pressedState by pressed.collectIsPressedAsState()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (pressedState) Card.copy(alpha = 0.6f) else Color.Transparent)
            .border(1.dp, AppPalette.value.outlineStrong, RoundedCornerShape(16.dp))
            .clickable(interactionSource = pressed, indication = null, onClick = onClick)
            .height(46.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Chevron
        )
    }
}

/** 主按钮：品牌色渐变胶囊，按压轻微缩放；危险态走红色 */
@Composable
private fun PrimaryDialogButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false
) {
    val pressed = remember { MutableInteractionSource() }
    val pressedState by pressed.collectIsPressedAsState()
    val container = when {
        danger -> Brush.linearGradient(listOf(AppPalette.value.danger, AppPalette.value.danger))
        else -> Brush.linearGradient(listOf(Accent, AppPalette.value.terracottaDark))
    }
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = if (pressedState) 0.97f else 1f; scaleY = if (pressedState) 0.97f else 1f }
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(interactionSource = pressed, indication = null, onClick = onClick)
            .height(46.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppPalette.value.textInverse
        )
    }
}

/** 弹窗外壳：全屏半透明遮罩 + 居中卡片，带入场动画；内容默认可滚动、自动避让键盘 */
@Composable
internal fun MusicDialogShell(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String? = null,
    onClose: () -> Unit = onDismiss,
    content: @Composable ColumnScope.() -> Unit
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(1f, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ))
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AppPalette.value.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Card,
                tonalElevation = 0.dp,
                shadowElevation = 24.dp,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = anim.value
                        scaleX = 0.9f + 0.1f * anim.value
                        scaleY = 0.9f + 0.1f * anim.value
                        translationY = 16.dp.toPx() * (1f - anim.value)
                    }
                    .widthIn(max = 320.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            ) {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                title,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (subtitle != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(subtitle, fontSize = 12.sp, color = Chevron, maxLines = 2)
                            }
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = tr("关闭", "Close"), tint = TimeGray, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    content()
                }
            }
        }
    }
}

/** 高级输入框：圆角容器 + 浮动标签 + 品牌光标与描边 */
@Composable
private fun MusicDialogField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Done
) {
    val focused = remember { MutableInteractionSource() }
    val isFocused by focused.collectIsFocusedAsState()
    Column {
        Text(label, fontSize = 12.sp, color = Chevron, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppPalette.value.surfaceVariant)
                .border(
                    1.dp,
                    if (isFocused) Accent else AppPalette.value.outlineStrong,
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                interactionSource = focused,
                textStyle = TextStyle(color = White, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
            if (value.isEmpty() && placeholder != null) {
                Text(placeholder, fontSize = 14.sp, color = TimeGray, maxLines = 1)
            }
        }
    }
}

/** 通用单选对话框：睡眠定时 / 均衡器 / 列表视图 / 字体字号 / 快慢 / 译词语言共用 */
@Composable
internal fun MusicOptionDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    MusicDialogShell(onDismiss = onDismiss, title = title) {
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            options.forEachIndexed { idx, label ->
                val isSel = idx == selected
                val pressed = remember { MutableInteractionSource() }
                val pressedState by pressed.collectIsPressedAsState()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            when {
                                isSel -> ActiveCard
                                pressedState -> Card.copy(alpha = 0.6f)
                                else -> Color.Transparent
                            }
                        )
                        .clickable(interactionSource = pressed, indication = null) { onPick(idx) }
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        color = if (isSel) Accent else White,
                        fontSize = 15.sp,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSel) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = Accent, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

/** 音乐闹钟：时/分两组 chip 选择，到点淡入播放收藏（没收藏用上次队列） */
@Composable
private fun MusicAlarmDialog(
    current: Pair<Int, Int>?,
    onSet: (Int, Int) -> Unit,
    onCancelAlarm: () -> Unit,
    onDismiss: () -> Unit
) {
    val hours = remember { List(24) { it } }
    val minutes = remember { listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55) }
    var hour by remember { mutableStateOf(current?.first ?: 7) }
    var minute by remember { mutableStateOf(current?.second?.let { m -> minutes.minByOrNull { abs(it - m) } } ?: 0) }
    MusicDialogShell(onDismiss = onDismiss, title = tr("音乐闹钟", "Music alarm")) {
        Column {
            Text(
                if (current == null) tr("未设置", "Not set")
                else tr("已设为 %02d:%02d".format(current.first, current.second), "Set to %02d:%02d".format(current.first, current.second)),
                color = TimeGray,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(tr("小时", "Hour"), color = TimeGray, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                hours.forEach { h ->
                    val sel = h == hour
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .clip(CircleShape)
                            .background(if (sel) Accent else Card.copy(alpha = 0.5f))
                            .clickable { hour = h }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Text("%02d".format(h), color = if (sel) White else White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(tr("分钟", "Minute"), color = TimeGray, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                minutes.forEach { m ->
                    val sel = m == minute
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .clip(CircleShape)
                            .background(if (sel) Accent else Card.copy(alpha = 0.5f))
                            .clickable { minute = m }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Text("%02d".format(m), color = if (sel) White else White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                tr(
                    "到点后 10 秒淡入播放收藏歌单；需要 App 还在运行（后台/前台均可），手机重启后会跳过这一次。",
                    "Fades in over 10s from your favorites; needs the app running (foreground or background), skipped after a reboot."
                ),
                color = TimeGray,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (current != null) {
                    Text(
                        tr("清除闹钟", "Clear"),
                        color = TimeGray,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onCancelAlarm() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Accent)
                        .clickable { onSet(hour, minute) }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(tr("确定", "OK"), color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
@Composable
private fun SongActionDialog(
    song: LocalSong,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onSwap: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val fav = MusicPlayer.isFavorite(song.id)
    MusicDialogShell(
        onDismiss = onDismiss,
        title = song.title,
        subtitle = song.artist.ifBlank { tr("本地歌曲", "Local song") }
    ) {
        Column {
            SongActionRow(tr("播放", "Play"), icon = Icons.Outlined.PlayCircleOutline) { onPlay() }
            if (!song.pendingSearch) {
                SongActionRow(tr("下一首播放", "Play next"), icon = Icons.Outlined.Queue) { onPlayNext() }
            }
            SongActionRow(
                if (fav) tr("取消收藏", "Remove from favorites")
                else tr("收藏这首歌", "Favorite this song"),
                icon = if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
            ) { onToggleFavorite() }
            SongActionRow(tr("修改歌曲名", "Rename song"), icon = Icons.Outlined.Edit) { onRename() }
            SongActionRow(tr("互换歌名和歌手", "Swap title & artist"), icon = Icons.Outlined.SwapHoriz) { onSwap() }
            SongActionRow(tr("删除", "Delete"), danger = true, icon = Icons.Outlined.DeleteOutline) { onDelete() }
        }
    }
}

@Composable
private fun SongActionRow(
    label: String,
    danger: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val pressed = remember { MutableInteractionSource() }
    val pressedState by pressed.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (pressedState) Card.copy(alpha = 0.6f) else Color.Transparent)
            .clickable(interactionSource = pressed, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (danger) AppPalette.value.danger else Chevron,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            label,
            color = if (danger) AppPalette.value.danger else White,
            fontSize = 15.sp,
            fontWeight = if (danger) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 改名：写完立刻用新歌名重跑多源歌词搜索 */
@Composable
private fun RenameSongDialog(
    initial: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    MusicDialogShell(
        onDismiss = onDismiss,
        title = tr("修改歌曲名", "Rename song"),
        subtitle = tr("改名后会用新歌名重新匹配歌词", "Lyrics will be matched again with the new title")
    ) {
        MusicDialogField(
            value = text,
            onValueChange = { text = it },
            label = tr("歌名", "Title"),
            placeholder = tr("输入歌曲名", "Enter title")
        )
        Spacer(Modifier.height(18.dp))
        MusicDialogActions(
            primaryLabel = tr("保存", "Save"),
            onPrimary = { if (text.isNotBlank()) onSubmit(text.trim()) },
            onDismiss = onDismiss
        )
    }
}

/** 删除二次确认：明确说明「只删 App 内缓存，不动手机原文件」 */
@Composable
private fun DeleteSongDialog(song: LocalSong, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    MusicDialogShell(
        onDismiss = onDismiss,
        title = tr("删除这首歌？", "Delete this song?"),
        subtitle = tr(
            "「${song.title}」会从列表移除；导入或联网获取的歌曲会一并删除 App 内的缓存文件，手机里的原文件不会动。",
            "\"${song.title}\" will be removed. Cached copies inside the app are deleted too; files on your phone are never touched."
        )
    ) {
        Spacer(Modifier.height(2.dp))
        MusicDialogActions(
            primaryLabel = tr("删除", "Delete"),
            onPrimary = onConfirm,
            onDismiss = onDismiss,
            primaryDanger = true
        )
    }
}

/** 元数据写错导致自动匹配不到时，手动纠正歌名/歌手再跑一遍多源搜索 */
@Composable
private fun LyricsSearchDialog(
    initialTitle: String,
    initialArtist: String,
    rawTitle: String = "",
    onSubmit: (Pair<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var artist by remember { mutableStateOf(initialArtist) }
    val recognized = SongNameParser.clean(rawTitle)
    MusicDialogShell(
        onDismiss = onDismiss,
        title = tr("手动搜索歌词", "Search lyrics manually"),
        subtitle = tr("歌名或歌手写错时，在这里纠正后重新搜索", "Fix the title / artist if the file metadata is wrong")
    ) {
        // 歌名识别结果：1231红尘客栈 → 红尘客栈，让用户知道输入框为什么变了
        if (rawTitle.isNotBlank() && recognized.isNotBlank() && recognized != rawTitle) {
            Text(
                tr("已从「$rawTitle」识别为「$recognized」", "Recognized \"$recognized\" from \"$rawTitle\""),
                fontSize = 12.sp, color = Accent, maxLines = 2, lineHeight = 16.sp
            )
            Spacer(Modifier.height(12.dp))
        }
        MusicDialogField(
            value = title,
            onValueChange = { title = it },
            label = tr("歌名", "Title"),
            placeholder = tr("输入歌名", "Enter title")
        )
        Spacer(Modifier.height(12.dp))
        MusicDialogField(
            value = artist,
            onValueChange = { artist = it },
            label = tr("歌手", "Artist"),
            placeholder = tr("输入歌手", "Enter artist"),
            imeAction = ImeAction.Done
        )
        Spacer(Modifier.height(18.dp))
        MusicDialogActions(
            primaryLabel = tr("搜索", "Search"),
            onPrimary = {
                if (title.isNotBlank()) onSubmit(title.trim() to artist.trim())
            },
            onDismiss = onDismiss
        )
    }
}

/**
 * 搜歌结果面板：本地曲库命中直接播，网络曲库结果点一下自动换直链、下载、播放。
 * 站点风控 / 无音源 / 网络失败时逐条给原因，并保留「在网页里手动获取」的兜底入口。
 */
@Composable
private fun SongSearchPage(
    query: String,
    local: List<LocalSong>,
    results: List<SongResult>,
    phase: Int,
    fetchingId: String?,
    fail: SongFetchReason?,
    failId: String?,
    audius: List<AudiusTrack>,
    audiusPhase: Int,
    onPickAudius: (AudiusTrack) -> Unit,
    onPickLocal: (LocalSong) -> Unit,
    onPickWeb: (SongResult) -> Unit,
    onOpenPage: (SongResult) -> Unit,
    onOpenWeb: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    // 直接嵌进音乐页内容区：跟页面共用一套配色、滚动和底部播放条，
    // 不再用 AlertDialog 把「正在播放」整页盖住。
    // 网上结果如果本机已有缓存，直接播本地，避免重复下载触发曲库风控
    fun localMatch(r: SongResult): LocalSong? = local.firstOrNull { l ->
        SearchUtils.score(r.title, "${l.title} ${l.artist}") >= 80
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = PageSide)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                tr("搜索「$query」", "Search \"$query\""),
                color = White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text(tr("关闭", "Close"), color = Accent, fontSize = 12.sp, maxLines = 1)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
                if (local.isNotEmpty()) {
                    SongSearchLabel(tr("本机已有", "On this device"))
                    local.forEach { s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Card)
                                .clickable { onPickLocal(s) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.title, color = White, fontSize = 14.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    s.artist.ifBlank { tr("本地歌曲", "Local song") },
                                    color = Chevron, fontSize = 11.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(tr("播放", "Play"), color = Accent, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }

                SongSearchLabel(
                    tr(
                        "网上曲库 · ${results.size} 条 · 点一下自动获取",
                        "Online · ${results.size} results · tap to get"
                    )
                )
                when (phase) {
                    0 -> {
                        Text(
                            tr("正在搜索曲库…", "Searching the library…"),
                            color = Chevron, fontSize = 13.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        // 加载态直接还原结果行布局：封面方块 + 两行文字，数据回来不跳动
                        ProvideShimmer {
                            Column {
                                repeat(4) { SkeletonSongRow() }
                            }
                        }
                    }
                    2 -> {
                        Text(
                            tr(
                                "没搜到结果，可能是网络不通或曲库暂时不可用",
                                "No results. Check your connection or try later"
                            ),
                            color = Chevron, fontSize = 13.sp
                        )
                        Row {
                            TextButton(onClick = onRetry) { Text(tr("重试", "Retry")) }
                            TextButton(onClick = onOpenWeb) {
                                Text(tr("打开网页", "Open web"))
                            }
                        }
                    }
                    else -> results.forEach { r ->
                        val busy = fetchingId == r.id
                        val existing = localMatch(r)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Card)
                                .clickable(enabled = fetchingId == null || existing != null) {
                                    if (existing != null) onPickLocal(existing) else onPickWeb(r)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    r.title, color = White, fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    listOf(r.artist, r.album).filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                        .ifBlank { tr("未知歌手", "Unknown artist") },
                                    color = Chevron, fontSize = 11.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            when {
                                existing != null -> Text(
                                    tr("本机", "Local"),
                                    color = Accent,
                                    fontSize = 12.sp
                                )
                                busy -> CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Accent
                                )
                                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (r.duration.isNotBlank()) {
                                        Text(r.duration, color = TimeGray, fontSize = 11.sp)
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Text(tr("获取", "Get"), color = Accent, fontSize = 12.sp)
                                }
                            }
                        }
                        if (fail != null && failId == r.id) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    songFetchHint(fail),
                                    color = TimeGray, fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onOpenPage(r) }) {
                                    Text(tr("网页获取", "Open page"), fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
                if (audiusPhase == 0) {
                    SongSearchLabel(tr("免费音源 · Audius", "Free source · Audius"))
                    ProvideShimmer { Column { repeat(2) { SkeletonSongRow() } } }
                } else if (audius.isNotEmpty()) {
                    SongSearchLabel(
                        tr(
                            "免费音源 · Audius 全曲 · ${audius.size} 条（独立音乐）",
                            "Free · Audius full tracks · ${audius.size}"
                        )
                    )
                    audius.forEach { t ->
                        val busy = fetchingId == "audius:${t.id}"
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Card)
                                .clickable(enabled = fetchingId == null) { onPickAudius(t) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.title, color = White, fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    listOf(
                                        t.artist.ifBlank { tr("未知歌手", "Unknown artist") },
                                        t.genre,
                                        tr("Audius 免费全曲", "Audius free full track")
                                    ).filter { it.isNotBlank() }.joinToString(" · "),
                                    color = Chevron, fontSize = 11.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            when {
                                busy -> CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Accent
                                )
                                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (t.durationText.isNotBlank()) {
                                        Text(t.durationText, color = TimeGray, fontSize = 11.sp)
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Text(tr("获取", "Get"), color = Accent, fontSize = 12.sp)
                                }
                            }
                        }
                        if (fail != null && failId == t.id) {
                            Text(
                                songFetchHint(fail),
                                color = TimeGray, fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                } else if (audiusPhase == 2 && phase == 2) {
                    Text(
                        tr(
                            "Audius 免费源以独立音乐为主，没有匹配到这首",
                            "Audius free source is mostly indie music and had no match"
                        ),
                        color = TimeGray, fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    tr(
                        "获取的歌曲会保存到 App 私有缓存，歌词一并离线保存；网页版有更多音质可选。",
                        "Songs are cached inside the app with offline lyrics. The web page offers more qualities."
                    ),
                    color = TimeGray,
                    fontSize = 10.sp
                )
            }
        // 底部操作条固定可见，不随结果列表滚走
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onRetry) {
                Text(tr("重新搜索", "Search again"), color = Chevron, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenWeb) {
                Text(tr("在网页中查看", "View on web"), color = Accent, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SongSearchLabel(text: String) {
    Text(
        text,
        color = Chevron,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun songFetchHint(reason: SongFetchReason): String = when (reason) {
    SongFetchReason.RATE_LIMIT -> tr("曲库请求太频繁，稍后再试或到网页获取", "Too many requests, try later or open the page")
    SongFetchReason.NO_SOURCE -> tr("这首暂无在线音源，可在网页里看其它下载方式", "No online source, check the page")
    SongFetchReason.BLOCKED -> tr("曲库要求人机验证，请在网页里手动获取", "Verification required, open the page")
    SongFetchReason.NOT_AUDIO, SongFetchReason.PARSE ->
        tr("没解析到音频地址，请在网页里手动获取", "No audio link found, open the page")
    SongFetchReason.NETWORK -> tr("网络不通或下载超时，可重试", "Network error or timeout, retry")
}

/** 顶栏展开式搜索框：胶囊造型、贴主题色，回车即搜（先在 App 内列出曲库结果） */
@Composable
private fun MusicSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(White.copy(alpha = 0.08f))
            .border(1.dp, White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Lucide.Search, null, tint = Chevron, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    tr("搜索歌名或歌手，回车即搜", "Search songs or artists"),
                    color = Chevron.copy(alpha = 0.65f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = White, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = tr("清空", "Clear"),
                tint = Chevron,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable { onValueChange("") }
            )
        }
    }
}

// ============================================================
// 1. Now Playing
// ============================================================
@Composable
private fun NowPlayingPage(
    state: AppState,
    showLyrics: Boolean,
    lyrics: LyricsResult?,
    lyricsLoading: Boolean,
    lyricsFailed: Boolean,
    onToggleLyrics: () -> Unit,
    onFetchLyrics: (Boolean) -> Unit,
    onManualSearch: () -> Unit,
    // 全屏播放页自己画返回/唱片钮；经典三页顶部已有标题栏时传 chrome=false
    chrome: Boolean = true,
    onBack: () -> Unit = {},
    onOpenPlaylist: () -> Unit = {},
    onOpenComments: () -> Unit = {},
    onCycleMode: () -> Unit = {},
    onCycleEq: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    /** 长按歌名/歌手：打开与列表一致的「改名 / 互换 / 删除」菜单 */
    onSongAction: (com.example.my.data.music.LocalSong) -> Unit = {}
) {
    val song = MusicPlayer.current
        ?: (MusicPlayer.songs + MusicPlayer.webSongs).firstOrNull()
    val art = rememberAlbumArt(song)
    val songKey = song?.id ?: -1L
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // 播放页允许跟重力横过来，但只在系统「自动旋转」真的开着时才放行：
    // 用户开着竖屏锁定、或在快捷开关里关了自动旋转时，这里必须留在竖屏，
    // 不能再像以前那样直接申请 SENSOR —— 那等于绕过系统锁，手机一转页面就横过来。
    DisposableEffect(Unit) {
        val activity = ScreenRotation.findActivity(ctx)
        ScreenRotation.applyToActivity(ctx)
        // 用户在系统里切「自动旋转」时页面要立刻跟手：挂着观察者，不用退出重进
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                ScreenRotation.applyToActivity(ctx)
            }
        }
        runCatching {
            ctx.contentResolver.registerContentObserver(ScreenRotation.autoRotateUri(), false, observer)
        }
        onDispose {
            runCatching { ctx.contentResolver.unregisterContentObserver(observer) }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 参考图三居中：左滑到图二（歌曲详情），右滑到图四（歌词）。
    val stagePager = rememberPagerState(
        initialPage = if (showLyrics) 0 else 1,
        pageCount = { 3 }
    )
    var darkLyrics by rememberSaveable { mutableStateOf(false) }
    var soundPanelOpen by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    // 驾驶模式走统一车机页（state.openScreen），本页不再维护第二套全屏态
    // 歌曲卡片分享：复用歌词卡的预览浮层（保存 / 分享走同一套）
    var songCard by remember { mutableStateOf<LyricArtPreview?>(null) }
    // 装扮中心（播放器样式）：衣架图标与更多菜单里的「播放器样式」都进这里
    var skinSheetOpen by remember { mutableStateOf(false) }

    // 手动退出横屏后，要等用户把手机真的竖起来再把方向交还重力。
    // 以前是固定延时 700ms 就解锁：手机还横在手里，方向一交还立刻弹回横屏，
    // 用户看到的就是「点了退出横屏没反应」。用协程等传感器，离开播放页时随协程一起取消。
    var rearmRotation by remember { mutableStateOf(false) }
    LaunchedEffect(rearmRotation) {
        if (!rearmRotation) return@LaunchedEffect
        val act = ctx.findActivity() ?: run {
            rearmRotation = false
            return@LaunchedEffect
        }
        var vertical = false
        val watch = ScreenRotation.watchPhysicalPortrait(ctx) { vertical = true }
        try {
            // 传感器不可用（模拟器 / 个别平板）时退化成延时解锁，不把方向永久锁死
            var waited = 0L
            while (!vertical && waited < 6000L) {
                delay(50)
                waited += 50
            }
            // 只在「仍然是竖屏锁」时才交还方向：这期间用户可能又点了横屏，
            // 那就别把刚请求的方向覆盖回去。
            if (act.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                act.requestedOrientation = ScreenRotation.playbackOrientation(ctx)
            }
        } finally {
            watch.stop()
            rearmRotation = false
        }
    }

    // 上次选的皮肤在本次启动时读回来；读一次就够，之后由 store 的状态驱动重组
    LaunchedEffect(Unit) {
        PlayerSkinStore.init(ctx)
        // 横屏播放器的自定义背景也要在进页面时读回来，否则重启后变回默认
        StageBackgroundStore.init(ctx)
    }

    val openLyrics: () -> Unit = {
        scope.launch { stagePager.animateScrollToPage(0) }
        if (!showLyrics) onToggleLyrics()
    }

    // 歌曲卡片：离屏绘制，封面 + 歌名 + 本机真实数据，完成后进预览浮层
    fun makeSongCard() {
        val s = song ?: return
        val cover = runCatching { art?.asAndroidBitmap() }.getOrNull()
        scope.launch {
            val bmp = withContext(Dispatchers.Default) {
                runCatching {
                    LyricShareArt.songCard(
                        title = s.title,
                        artist = s.artist,
                        cover = cover,
                        playCount = MusicPlayer.playCounts[s.id] ?: 0,
                        favorite = MusicPlayer.isFavorite(s.id),
                        durationText = formatMs(MusicPlayer.durationMs)
                    )
                }.getOrNull()
            }
            if (bmp != null) {
                songCard = LyricArtPreview(bmp, tr("歌曲卡片", "Song card"))
            } else {
                stageToast(ctx, tr("卡片生成失败", "Failed to build the card"))
            }
        }
    }
    val closeLyrics: () -> Unit = {
        scope.launch { stagePager.animateScrollToPage(1) }
        if (showLyrics) onToggleLyrics()
    }

    // 手指横滑切页时同步外层的「歌词态」，避免退出再进来又跳回黑胶页。
    LaunchedEffect(stagePager.currentPage) {
        val wantLyrics = stagePager.currentPage == 0
        if (wantLyrics != showLyrics) onToggleLyrics()
    }
    // 外部入口（迷你播放条 / 底部 tab）会先把 showLyrics 置 false；
    // 页面状态被恢复成歌词页时，这里把 Pager 拉回黑胶页，
    // 否则会出现「点播放条进来却停在歌词」的错位。
    LaunchedEffect(showLyrics) {
        if (!showLyrics && stagePager.currentPage == 0) {
            stagePager.scrollToPage(1)
        }
    }

    // 歌词页和黑胶页都按需取词；黑胶页也要直接显示当前歌词，而不是退回专辑名。
    // 失败后不自动重试，留给歌词页里的「重试」。
    LaunchedEffect(stagePager.currentPage, songKey) {
        if (stagePager.currentPage != 2 && lyrics == null && !lyricsLoading && !lyricsFailed) {
            onFetchLyrics(false)
        }
    }
    // 系统返回：先回到黑胶页；黑胶页再按返回才退出播放器。
    BackHandler(enabled = !isLandscape && stagePager.currentPage != 1) {
        scope.launch { stagePager.animateScrollToPage(1) }
    }
    BackHandler(enabled = !isLandscape && stagePager.currentPage == 1) { onBack() }

    val share: () -> Unit = { shareStageSong(ctx, song) }
    // 衣架图标（播放页右上）现在是装扮中心入口，不再是「切音效」的假动作
    val skin: () -> Unit = { skinSheetOpen = true }
    // 页码点跟手：取 Pager 的连续位置（当前页 + 拖动偏移），拖动时每帧都会变。
    val stagePosition = stagePager.currentPage + stagePager.currentPageOffsetFraction

    // 三页 Pager 拖动间隙不能露出底层：铺一层实心底再叠各页自己的舞台背景。
    Box(Modifier.fillMaxSize().background(Layer.Opaque)) {
        if (isLandscape) {
            // 横屏播放器（参考图一）：背景视频/自定义背景 + 底部信息条；点屏幕唤出四个按钮
            BackHandler { onBack() }
            LandscapeStage(
                song = song,
                art = art,
                lyrics = lyrics,
                onPortrait = {
                    lockStagePortrait(ctx)
                    // 稍后把方向交还给重力：横屏转回竖屏后仍能靠感应再进横屏。
                    // 放在 LaunchedEffect 里而不是 Handler：离开播放页时协程会被取消，
                    // 否则那个延迟任务会在别的页面把方向解开。
                    rearmRotation = true
                },
                onOpenSoundFx = { soundPanelOpen = true },
                onToggleFavorite = onToggleFavorite
            )
        } else {
        HorizontalPager(
            state = stagePager,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) { page ->
            when (page) {
                0 -> LyricsStageV2(
                    stagePosition = stagePosition,
                    state = state,
                    song = song,
                    art = art,
                    lyrics = lyrics,
                    loading = lyricsLoading,
                    failed = lyricsFailed,
                    dark = darkLyrics,
                    isFavorite = MusicPlayer.isFavorite(),
                    onFetch = onFetchLyrics,
                    onManualSearch = onManualSearch,
                    onClose = closeLyrics,
                    onBack = onBack,
                    onShare = share,
                    onSkin = skin,
                    onToggleDark = { darkLyrics = !darkLyrics },
                    onToggleFavorite = onToggleFavorite,
                    onOpenPlaylist = onOpenPlaylist,
                    onOpenComments = onOpenComments,
                    onCycleEq = { soundPanelOpen = true }
                )
                1 -> VinylStage(
                    stagePosition = stagePosition,
                    song = song,
                    art = art,
                    lyrics = lyrics,
                    lyricsLoading = lyricsLoading,
                    lyricsFailed = lyricsFailed,
                    onOpenLyrics = openLyrics,
                    onBack = onBack,
                    onShare = share,
                    onSkin = skin,
                    onManualSearch = onManualSearch,
                    onCycleMode = onCycleMode,
                    onCycleEq = { soundPanelOpen = true },
                    onOpenSoundPanel = { soundPanelOpen = true },
                    onOpenPlaylist = onOpenPlaylist,
                    onOpenComments = onOpenComments,
                    onOpenMoreMenu = { moreMenuOpen = true },
                    onToggleFavorite = onToggleFavorite,
                    onSongAction = onSongAction
                )
                else -> SongDetailStage(
                    state = state,
                    active = stagePager.currentPage == 2,
                    stagePosition = stagePosition,
                    song = song,
                    art = art,
                    lyrics = lyrics,
                    onBack = onBack,
                    onShare = share,
                    onSkin = skin,
                    onCycleEq = { soundPanelOpen = true },
                    onToggleFavorite = onToggleFavorite,
                    onSongAction = onSongAction
                )
            }
        }
        }

        if (soundPanelOpen) {
            SoundEffectPanel(onClose = { soundPanelOpen = false })
        }

        if (moreMenuOpen) {
            SongMoreSheet(
                song = song,
                art = art,
                onClose = { moreMenuOpen = false },
                onShare = {
                    moreMenuOpen = false
                    share()
                },
                onShareCard = {
                    moreMenuOpen = false
                    makeSongCard()
                },
                onDrivingMode = {
                    moreMenuOpen = false
                    // 与标题菜单同一目的地：统一车机页，避免两套「驾驶模式」
                    state.openScreen(ExtraScreen.DRIVING)
                },
                onOpenSoundPanel = {
                    moreMenuOpen = false
                    soundPanelOpen = true
                },
                onPlayerStyle = {
                    moreMenuOpen = false
                    skinSheetOpen = true
                },
                onLandscape = {
                    moreMenuOpen = false
                    // 显式方向，系统竖屏锁开着也能进；回竖屏时由 onPortrait 一并恢复重力感应
                    forceStageLandscape(ctx)
                },
                onToggleFavorite = onToggleFavorite
            )
        }

        // 驾驶模式统一跳 ExtraScreen.DRIVING，此处不叠任何全屏覆盖层

        // 歌曲卡片：与歌词卡共用预览浮层（保存到相册 / 系统分享）
        songCard?.let { item ->
            LyricArtPreviewOverlay(
                item = item,
                onClose = { songCard = null },
                onSave = {
                    val ok = ImageExporter.saveToGallery(ctx, item.bitmap, item.title)
                    stageToast(
                        ctx,
                        if (ok) tr("已保存到相册", "Saved to gallery")
                        else tr("保存失败，请检查存储空间", "Save failed")
                    )
                },
                onShare = {
                    val uri = ImageExporter.shareUri(ctx, item.bitmap, item.title)
                    if (uri != null) ImageExporter.share(ctx, uri)
                    else stageToast(ctx, tr("分享失败", "Share failed"))
                }
            )
        }

        // 装扮中心：全屏盖在播放页上，选中即生效，关掉就能看到新皮肤
        if (skinSheetOpen) {
            PlayerSkinScreen(
                current = PlayerSkinStore.current,
                onClose = { skinSheetOpen = false },
                onPick = { picked ->
                    PlayerSkinStore.select(ctx, picked)
                    // 无论从哪一页进的装扮中心，选完都把播放器摆回黑胶页，
                    // 关掉面板第一眼就能看到新皮肤真的换上了
                    scope.launch { stagePager.animateScrollToPage(1) }
                    stageToast(
                        ctx,
                        tr("已换上「${picked.zh}」", "Skin applied: ${picked.en}")
                    )
                }
            )
        }
    }
}

/**
 * 假频谱：播放时按节拍伪随机跳动（几个正弦叠加，零权限、纯动画），
 * 暂停时条伏到低位静止。与唱片转动的帧循环同一套写法，后台不耗电。
 */
@Composable
private fun SpectrumBars(
    color: Color,
    playing: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 32
) {
    var t by remember { mutableFloatStateOf(0f) }
    val resumed = rememberIsResumed()
    LaunchedEffect(playing, resumed) {
        if (!playing || !resumed) return@LaunchedEffect
        val from = t
        val fromNs = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            t = from + (now - fromNs) / 1_000_000_000f
        }
    }
    Canvas(modifier) {
        val w = size.width / barCount
        for (i in 0 until barCount) {
            val phase = i * 0.83f
            // 两个不同频率的正弦叠加 → 看起来随机但不重复；对称让整体像「音量呼吸」
            val wave = 0.5f + 0.5f * (
                0.6f * kotlin.math.sin(t * 4.4f + phase) +
                    0.4f * kotlin.math.sin(t * 7.3f + phase * 1.7f)
                )
            val level = if (playing) wave.coerceIn(0.08f, 1f) else 0.1f
            val h = level * size.height
            drawRoundRect(
                color = color.copy(alpha = 0.35f + 0.65f * level),
                topLeft = Offset(i * w + w * 0.22f, size.height - h),
                size = Size(w * 0.56f, h),
                cornerRadius = CornerRadius(w * 0.28f, w * 0.28f)
            )
        }
    }
}

// ============================================================
// 参考图二 / 图三 / 图四：详情、黑胶、歌词三页
// ============================================================

private val StageLyricsBg = Color(0xFFF2FAF6)
private val StageLyricsDark = Color(0xFF101D18)
private val StageLyricsInk = Color(0xFF183A2A)
private val StageLyricsAccent = Color(0xFF1EA363)
private val StageLyricsGold = Color(0xFF31C27C)
private val StageVinylTop = Color(0xFFEAF8F1)
private val StageVinylBottom = Color(0xFFDDF3E8)
private val StageOrange = Color(0xFF31C27C)
private val StageOrangeDeep = Color(0xFF23A566)
private val StageDetailBg = Color(0xFFF4F5F7)
private val StageDetailInk = Color(0xFF20242B)
private val StageDetailSub = Color(0xFF7B8089)
private val StageDetailCard = Color(0xFFE9EBEF)
private val StageRed = Color(0xFFF04444)

/** 图四：暖米色歌词页。顶部小封面 + 逐行卡拉 OK 填充 + 底部工具条。 */
@Composable
private fun LyricsStageV2(
    stagePosition: Float,
    state: AppState,
    song: LocalSong?,
    art: ImageBitmap?,
    lyrics: LyricsResult?,
    loading: Boolean,
    failed: Boolean,
    dark: Boolean,
    isFavorite: Boolean,
    onFetch: (Boolean) -> Unit,
    onManualSearch: () -> Unit,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSkin: () -> Unit,
    onToggleDark: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onOpenComments: () -> Unit,
    onCycleEq: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val bg = if (dark) StageLyricsDark else StageLyricsBg
    val bgBrush = if (dark) {
        Brush.verticalGradient(listOf(StageLyricsDark, StageLyricsDark))
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFFFDF6EE), StageLyricsBg, Color(0xFFF7E5D4))
        )
    }
    val ink = if (dark) Color(0xFFF8E8D8) else StageLyricsInk
    val sub = if (dark) Color(0xFFB9A392) else Color(0xFF9B7159)
    val haptic = LocalHapticFeedback.current
    var settingsOpen by remember { mutableStateOf(false) }
    var fontScale by remember { mutableStateOf(LyricDisplayPrefs.fontScale(ctx)) }
    var guide by remember { mutableStateOf(LyricDisplayPrefs.guide(ctx)) }
    // 双行歌词：当前句 + 下一句同屏，不整页滚动
    var twoLine by remember { mutableStateOf(LyricDisplayPrefs.twoLine(ctx)) }
    var offsetMs by remember { mutableStateOf(LyricDisplayPrefs.offsetMs(ctx)) }
    var traditional by remember { mutableStateOf(LyricDisplayPrefs.traditional(ctx)) }
    var statusBarOn by remember { mutableStateOf(LyricDisplayPrefs.statusBar(ctx)) }
    var desktopOn by remember { mutableStateOf(LyricDisplayPrefs.desktop(ctx)) }
    var translations by remember { mutableStateOf<List<String>?>(null) }
    var translatedLanguage by remember { mutableStateOf<String?>(null) }
    var translationOn by remember { mutableStateOf(false) }
    var translating by remember { mutableStateOf(false) }
    val translateSongKey = remember(song?.id, song?.title, song?.artist) {
        LyricTranslatePrefs.songKey(song?.id, song?.title.orEmpty(), song?.artist.orEmpty())
    }
    var targetLanguage by remember {
        mutableStateOf(LyricTranslatePrefs.targetLanguage(ctx))
    }
    var currentLine by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<LyricArtPreview?>(null) }
    var issueMode by remember { mutableStateOf<String?>(null) }

    val timedLines: List<LrcLine> = remember(lyrics, MusicPlayer.durationMs) {
        lyrics?.lines?.takeIf { it.isNotEmpty() }
            ?: lyrics?.plain?.let { LyricsRepository.estimateLines(it, MusicPlayer.durationMs) }.orEmpty()
    }
    val rawLines = remember(timedLines) { timedLines.map { it.text } }
    val activeLineIndex by remember(timedLines) {
        derivedStateOf {
            if (timedLines.isEmpty()) 0
            else timedLines.indexOfLast { it.timeMs <= MusicPlayer.positionMs }.coerceAtLeast(0)
        }
    }
    val aiEndpoint = state.currentEndpoint.takeIf { it.ready } ?: AiPresets.builtin

    fun toast(text: String) = stageToast(ctx, text)

    fun saveFont(value: Float) {
        fontScale = value
        LyricDisplayPrefs.setFontScale(ctx, value)
    }

    fun saveGuide(value: Boolean) {
        guide = value
        LyricDisplayPrefs.setGuide(ctx, value)
    }

    fun saveTwoLine(value: Boolean) {
        twoLine = value
        LyricDisplayPrefs.setTwoLine(ctx, value)
    }

    fun saveOffset(value: Long) {
        offsetMs = value
        LyricDisplayPrefs.setOffsetMs(ctx, value)
    }

    fun saveTraditional(value: Boolean) {
        traditional = value
        LyricDisplayPrefs.setTraditional(ctx, value)
    }

    fun openNotificationSettings() {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        toast(tr("请在系统设置里允许通知", "Allow notifications in system settings"))
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            statusBarOn = true
            LyricDisplayPrefs.setStatusBar(ctx, true)
            MusicNotification.setLyricLine(ctx, currentLine)
            toast(tr("状态栏歌词已开启", "Status-bar lyrics on"))
        } else {
            statusBarOn = false
            LyricDisplayPrefs.setStatusBar(ctx, false)
            toast(tr("没有通知权限，状态栏歌词无法显示", "Notification permission is required"))
        }
    }

    fun startDesktop() {
        LyricOverlayService.start(
            ctx,
            song?.title.orEmpty(),
            listOf(song?.artist, song?.title).filterNotNull().filter { it.isNotBlank() }
                .joinToString(" · "),
            timedLines,
            offsetMs,
            traditional
        )
    }

    val overlayPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(ctx)) {
            desktopOn = true
            LyricDisplayPrefs.setDesktop(ctx, true)
            startDesktop()
            toast(tr("桌面歌词已开启", "Floating lyrics on"))
        } else {
            desktopOn = false
            LyricDisplayPrefs.setDesktop(ctx, false)
            toast(tr("需要悬浮窗权限才能显示桌面歌词", "Overlay permission is required"))
        }
    }

    fun toggleStatusBar() {
        if (statusBarOn) {
            statusBarOn = false
            LyricDisplayPrefs.setStatusBar(ctx, false)
            MusicNotification.setLyricLine(ctx, null)
            toast(tr("状态栏歌词已关闭", "Status-bar lyrics off"))
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            openNotificationSettings()
        } else {
            statusBarOn = true
            LyricDisplayPrefs.setStatusBar(ctx, true)
            MusicNotification.setLyricLine(ctx, currentLine)
            toast(tr("状态栏歌词已开启", "Status-bar lyrics on"))
        }
    }

    fun toggleDesktop() {
        if (desktopOn) {
            desktopOn = false
            LyricDisplayPrefs.setDesktop(ctx, false)
            LyricOverlayService.stop(ctx)
            toast(tr("桌面歌词已关闭", "Floating lyrics off"))
            return
        }
        if (!Settings.canDrawOverlays(ctx)) {
            runCatching {
                overlayPermission.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            toast(tr("请允许「显示在其他应用上层」", "Allow display over other apps"))
            return
        }
        desktopOn = true
        LyricDisplayPrefs.setDesktop(ctx, true)
        startDesktop()
        toast(tr("桌面歌词已开启，可拖动调整位置", "Floating lyrics on; drag to move"))
    }

    fun makePreview(poster: Boolean) {
        if (rawLines.isEmpty()) {
            toast(tr("歌词还没加载出来，先等一下", "Lyrics are still loading"))
            return
        }
        val cover = runCatching { art?.asAndroidBitmap() }.getOrNull()
        val focus = if (timedLines.isEmpty()) 0 else activeLineIndex
        val title = song?.title.orEmpty().ifBlank { tr("未命名歌曲", "Untitled") }
        val artist = song?.artist.orEmpty().ifBlank { tr("未知歌手", "Unknown artist") }
        val lines = rawLines.map { HanziScript.convert(it, traditional) }
        scope.launch {
            val bmp = withContext(Dispatchers.Default) {
                if (poster) LyricShareArt.poster(title, artist, lines, focus, cover)
                else LyricShareArt.card(title, artist, lines, focus, cover)
            }
            preview = LyricArtPreview(bmp, if (poster) tr("歌词海报", "Lyric poster") else tr("歌词卡", "Lyric card"))
        }
    }

    fun toggleTranslation() {
        if (translating) return
        if (translationOn) {
            translationOn = false
            LyricTranslatePrefs.setTranslationOn(ctx, translateSongKey, false)
            toast(tr("已关闭译词", "Translation off"))
            return
        }
        // 目标语言没变且已经译过，直接复用，不再联网
        if (translations != null && translatedLanguage == targetLanguage) {
            translationOn = true
            LyricTranslatePrefs.setTranslationOn(ctx, translateSongKey, true)
            toast(tr("已显示译词", "Translation on"))
            return
        }
        if (rawLines.isEmpty()) {
            toast(tr("歌词还没加载出来，先等一下", "Lyrics are still loading"))
            return
        }
        // 翻译走网页翻译器，不加载本地模型，也不占端侧内存。
        val source = rawLines.map { HanziScript.toSimplified(it) }
        val lang = LyricTranslateLanguages.byCode(targetLanguage)
        translating = true
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                WebLyricsTranslate.translate(ctx, source, lang.code)
            }
            translating = false
            if (parsed == null) {
                LyricTranslatePrefs.setTranslationOn(ctx, translateSongKey, false)
                toast(tr("网页译词失败，请检查网络后重试", "Web translation failed; check your network"))
            } else if (targetLanguage == lang.code) {
                translations = parsed
                translatedLanguage = lang.code
                translationOn = true
                LyricTranslatePrefs.setTranslationOn(ctx, translateSongKey, true)
                toast(tr("译词完成", "Translation ready"))
            }
        }
    }

    // 进播放器/切歌/切目标语言时恢复翻译状态：按歌曲记住开关，同语言缓存命中时零联网。
    LaunchedEffect(translateSongKey, lyrics, targetLanguage) {
        translations = null
        translatedLanguage = null
        translating = false
        translationOn = false
        if (translateSongKey.isBlank() || rawLines.isEmpty()) return@LaunchedEffect
        if (!LyricTranslatePrefs.isTranslationOn(ctx, translateSongKey)) return@LaunchedEffect

        translationOn = true
        translating = true
        val source = rawLines.map { HanziScript.toSimplified(it) }
        val lang = LyricTranslateLanguages.byCode(targetLanguage)
        val parsed = withContext(Dispatchers.IO) {
            WebLyricsTranslate.translate(ctx, source, lang.code)
        }
        translating = false
        if (parsed != null &&
            targetLanguage == lang.code &&
            LyricTranslatePrefs.isTranslationOn(ctx, translateSongKey)
        ) {
            translations = parsed
            translatedLanguage = lang.code
        } else if (parsed == null) {
            // 开关偏好保留，下一次进入或联网恢复后会再试；当前先如实显示未译状态
            translationOn = false
            toast(
                tr(
                    "译词加载失败，检查网络后可重试",
                    "Translation failed; check your network and retry"
                )
            )
        }
    }
    LaunchedEffect(currentLine, statusBarOn) {
        if (statusBarOn) MusicNotification.setLyricLine(ctx, currentLine)
    }
    LaunchedEffect(desktopOn, timedLines, offsetMs, traditional) {
        if (desktopOn) {
            if (Settings.canDrawOverlays(ctx)) startDesktop()
            else {
                desktopOn = false
                LyricDisplayPrefs.setDesktop(ctx, false)
                LyricOverlayService.stop(ctx)
            }
        }
    }


    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(bgBrush)
            .navigationBarsPadding()
    ) {
        StageTopBar(
            page = 0, position = stagePosition, ink = ink, dark = dark,
            onBack = onBack, onShare = onShare, onSkin = onSkin,
            onToggleDark = onToggleDark
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                StageAlbumArt(art, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song?.title ?: tr("水莲", "WaterLily"),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        song?.artist ?: tr("未知歌手", "Unknown artist"),
                        fontSize = 11.sp, color = sub,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tr("制作团队 >", "Credits >"),
                        fontSize = 11.sp, color = sub,
                        modifier = Modifier.clickable {
                            stageToast(ctx, tr("歌曲制作信息来自本地音频标签", "Credits come from local audio tags"))
                        }
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedHeart(isFavorite, onToggleFavorite, inactive = sub)
                Text(
                    if (isFavorite) tr("已收藏", "Saved") else tr("收藏", "Save"),
                    fontSize = 9.sp, color = sub
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                tr("歌词", "Lyrics"),
                fontSize = 10.sp, color = sub.copy(alpha = 0.7f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    lyrics?.lines?.isNotEmpty() == true ->
                        tr("时间轴已对齐 · ${lyrics.lines.size} 行", "Synced · ${lyrics.lines.size} lines")
                    !lyrics?.plain.isNullOrBlank() -> tr("按字数与时长估算", "Estimated timeline")
                    else -> tr("等待歌词", "Waiting for lyrics")
                },
                fontSize = 10.sp,
                color = if (lyrics?.lines?.isNotEmpty() == true) StageOrangeDeep else sub.copy(alpha = 0.7f)
            )
            Spacer(Modifier.weight(1f))
            if (!lyrics?.plain.isNullOrBlank()) {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (dark) Color(0xFF3A3028) else Color(0xFFF0DDCC))
                        .clickable {
                            onFetch(true)
                            toast(tr("正在重新对齐歌词", "Realigning lyrics"))
                        }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        tr("重新对齐", "Realign"),
                        fontSize = 9.sp,
                        color = if (dark) Color(0xFFF3D8BC) else StageOrangeDeep,
                        maxLines = 1
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            KaraokeLyricsList(
                lyrics = lyrics,
                loading = loading,
                failed = failed,
                dark = dark,
                ink = ink,
                sub = sub,
                bg = bg,
                onFetch = onFetch,
                onManualSearch = onManualSearch,
                fontScale = fontScale,
                guide = guide,
                offsetMs = offsetMs,
                translations = if (translationOn) translations else null,
                traditional = traditional,
                twoLine = twoLine,
                onActiveLine = { currentLine = it },
                modifier = Modifier.fillMaxSize()
            )
        }
        var scrub by remember { mutableStateOf<Float?>(null) }
        StageSeekBar(
            progress = scrub ?: MusicPlayer.progress,
            track = if (dark) Color(0xFF3A2C22) else Color(0xFFE2CDBA),
            fill = StageOrange,
            thumb = StageOrange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 3.dp),
            onScrub = { scrub = it },
            onSeek = {
                MusicPlayer.seekTo(it)
                scrub = null
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatMs(
                    if (scrub != null) (scrub!! * MusicPlayer.durationMs).toLong()
                    else MusicPlayer.positionMs
                ),
                fontSize = 10.sp, color = sub
            )
            Text(formatMs(MusicPlayer.durationMs), fontSize = 10.sp, color = sub)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(30.dp))
                    .background(if (dark) Color(0xFF2A211B) else Color(0xFFE8EAEA))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LyricTool(tr("评", "Cm"), Lucide.MessageSquare, ink) {
                    onOpenComments()
                }
                LyricTool(
                    "伴", Lucide.AudioLines, ink,
                    tinyBadge = if (MusicPlayer.soundFxOn) "on" else "off"
                ) {
                    // 点「伴」= 进音效库自选，不再偷偷切预设
                    onCycleEq()
                }
                LyricTool(tr("词", "Words"), Icons.Outlined.Lyrics, ink) {
                    settingsOpen = true
                }
                LyricTool(
                    tr("译", "Trans"), Icons.Outlined.Translate, ink,
                    tinyBadge = if (translationOn) "on" else "off"
                ) {
                    toggleTranslation()
                }
                LyricTool("", Icons.Outlined.AutoAwesome, ink, compact = true) {
                    settingsOpen = true
                }
            }
            Spacer(Modifier.width(10.dp))
            AnimatedPlayButton(
                playing = MusicPlayer.isPlaying,
                onClick = { stageTogglePlay(); haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                size = 50.dp,
                color = if (dark) Color(0xFF303030) else Color(0xFFE4E5E5),
                iconColor = if (dark) Color(0xFFF2F2F2) else Color(0xFF171A1C)
            )
        }
        Spacer(Modifier.height(6.dp))
    }
        if (settingsOpen) {
            LyricsSettingsSheet(
                fontScale = fontScale,
                onFontScale = { saveFont(it) },
                guide = guide,
                onGuide = { saveGuide(it) },
                twoLine = twoLine,
                onTwoLine = { saveTwoLine(it) },
                offsetMs = offsetMs,
                onOffset = { saveOffset(it) },
                onSearch = {
                    settingsOpen = false
                    onManualSearch()
                },
                onLyricCard = {
                    settingsOpen = false
                    makePreview(poster = false)
                },
                onPoster = {
                    settingsOpen = false
                    makePreview(poster = true)
                },
                statusBarOn = statusBarOn,
                onStatusBar = { toggleStatusBar() },
                desktopOn = desktopOn,
                onDesktop = { toggleDesktop() },
                translationOn = translationOn,
                translating = translating,
                targetLanguage = targetLanguage,
                onTargetLanguage = { code ->
                    targetLanguage = code
                    LyricTranslatePrefs.setTargetLanguage(ctx, code)
                },
                onTranslate = {
                    settingsOpen = false
                    toggleTranslation()
                },
                traditional = traditional,
                onScriptToggle = { saveTraditional(!traditional) },
                onFeedback = {
                    settingsOpen = false
                    issueMode = "feedback"
                },
                onReport = {
                    settingsOpen = false
                    issueMode = "report"
                },
                onClose = { settingsOpen = false }
            )
        }
        preview?.let { item ->
            LyricArtPreviewOverlay(
                item = item,
                onClose = { preview = null },
                onSave = {
                    val ok = ImageExporter.saveToGallery(ctx, item.bitmap, item.title)
                    toast(
                        if (ok) tr("已保存到相册", "Saved to gallery")
                        else tr("保存失败，请检查存储空间", "Save failed")
                    )
                },
                onShare = {
                    val uri = ImageExporter.shareUri(ctx, item.bitmap, item.title)
                    if (uri != null) ImageExporter.share(ctx, uri)
                    else toast(tr("分享失败", "Share failed"))
                }
            )
        }
        issueMode?.let { mode ->
            LyricIssueSheet(
                mode = mode,
                song = listOf(song?.title, song?.artist)
                    .filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                onClose = { issueMode = null }
            )
        }
    }
}

@Composable
private fun LyricTool(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    tinyBadge: String? = null,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val press = remember { MutableInteractionSource() }
    Column(
        Modifier.clip(RoundedCornerShape(10.dp))
            .pressScale(press, 0.90f)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Icon(
                icon, null, tint = tint,
                modifier = Modifier.size(20.dp)
            )
            if (tinyBadge != null) {
                Text(
                    tinyBadge,
                    fontSize = 7.sp,
                    color = tint.copy(alpha = 0.58f),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-6).dp)
                )
            }
        }
        if (!compact) {
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 9.sp, color = tint, maxLines = 1)
        }
    }
}

@Composable
private fun LyricsSettingsSheet(
    fontScale: Float,
    onFontScale: (Float) -> Unit,
    guide: Boolean,
    onGuide: (Boolean) -> Unit,
    twoLine: Boolean,
    onTwoLine: (Boolean) -> Unit,
    offsetMs: Long,
    onOffset: (Long) -> Unit,
    onSearch: () -> Unit,
    onLyricCard: () -> Unit,
    onPoster: () -> Unit,
    statusBarOn: Boolean,
    onStatusBar: () -> Unit,
    desktopOn: Boolean,
    onDesktop: () -> Unit,
    translationOn: Boolean,
    translating: Boolean,
    targetLanguage: String,
    onTargetLanguage: (String) -> Unit,
    onTranslate: () -> Unit,
    traditional: Boolean,
    onScriptToggle: () -> Unit,
    onFeedback: () -> Unit,
    onReport: () -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var fontDialog by remember { mutableStateOf(false) }
    var offsetDialog by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }
    val targetLang = LyricTranslateLanguages.byCode(targetLanguage)
    val scrim = remember { MutableInteractionSource() }
    val sheet = remember { MutableInteractionSource() }
    fun tip(message: String) = stageToast(ctx, message)

    BackHandler { onClose() }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .background(Layer.Scrim)
                .clickable(interactionSource = scrim, indication = null) { onClose() }
        )
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.68f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MoreBg)
                .navigationBarsPadding()
                .clickable(interactionSource = sheet, indication = null) {}
        ) {
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(width = 42.dp, height = 4.dp)
                        .clip(CircleShape).background(Color(0xFFC7CBC9))
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LyricSettingTile(Icons.Outlined.TextFields, tr("字体字号", "Font size"), Modifier.weight(1f)) {
                    fontDialog = true
                }
                LyricSettingTile(Icons.AutoMirrored.Outlined.Article, tr("歌词卡", "Lyric card"), Modifier.weight(1f)) {
                    onLyricCard()
                }
                LyricSettingTile(Icons.Outlined.Image, tr("歌词海报", "Lyric poster"), Modifier.weight(1f)) {
                    onPoster()
                }
                LyricSettingTile(
                    Icons.Outlined.Notifications, tr("状态栏歌词", "Status bar"),
                    Modifier.weight(1f), active = statusBarOn
                ) {
                    onStatusBar()
                }
                LyricSettingTile(
                    Icons.Outlined.Widgets, tr("桌面歌词", "Desktop lyrics"),
                    Modifier.weight(1f), active = desktopOn
                ) {
                    onDesktop()
                }
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                item {
                    LyricSettingRow(
                        icon = Icons.Outlined.Mic,
                        title = tr("助唱标注", "Guide marks"),
                        badge = "new",
                        trailing = {
                            Switch(
                                checked = guide,
                                onCheckedChange = onGuide,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MoreAccent,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFD7DBD9)
                                )
                            )
                        }
                    ) { onGuide(!guide) }
                }
                item {
                    LyricSettingRow(
                        icon = Icons.Outlined.FormatLineSpacing,
                        title = tr("双行歌词", "Two-line lyrics"),
                        badge = if (twoLine) "on" else null,
                        value = if (twoLine) tr("当前句 + 下一句", "Current + next")
                        else tr("整页滚动", "Scrolling")
                    ) { onTwoLine(!twoLine) }
                }
                item {
                    LyricSettingRow(
                        Icons.Outlined.Translate, tr("网页译词", "Web translation"),
                        badge = tr("网页", "WEB"),
                        value = when {
                            translating -> tr("翻译中", "Translating")
                            translationOn -> tr(
                                "${targetLang.labelZh} · 已开启",
                                "${targetLang.labelEn} · On"
                            )
                            else -> tr(targetLang.labelZh, targetLang.labelEn)
                        }
                    ) { onTranslate() }
                }
                item {
                    LyricSettingRow(
                        Icons.Outlined.Language, tr("译词语言", "Translation language"),
                        value = tr(targetLang.labelZh, targetLang.labelEn)
                    ) { languageDialog = true }
                }
                item {
                    LyricSettingRow(
                        Icons.Outlined.SwapHoriz, tr("简繁转换", "SC/TC convert"),
                        value = if (traditional) tr("繁体", "Traditional") else tr("简体", "Simplified")
                    ) { onScriptToggle() }
                }
                item {
                    LyricSettingRow(
                        Icons.Outlined.Speed,
                        tr("调整快慢", "Timing offset"),
                        value = if (offsetMs == 0L) "" else tr(
                            "${if (offsetMs > 0) "+" else ""}${offsetMs / 1000f}s",
                            "${if (offsetMs > 0) "+" else ""}${offsetMs / 1000f}s"
                        )
                    ) { offsetDialog = true }
                }
                item {
                    LyricSettingRow(Icons.Outlined.Search, tr("重新搜词", "Search again")) {
                        onSearch()
                    }
                }
                item {
                    LyricSettingRow(Icons.Outlined.ReportProblem, tr("反馈错误", "Report an issue")) {
                        onFeedback()
                    }
                }
                item {
                    LyricSettingRow(Icons.Outlined.Warning, tr("举报", "Report")) {
                        onReport()
                    }
                }
            }
        }
    }

    if (fontDialog) {
        val sizes = listOf(0.9f, 1f, 1.15f, 1.3f, 1.45f)
        val labels = listOf(
            tr("小", "Small"), tr("标准", "Standard"), tr("大", "Large"),
            tr("特大", "Extra large"), tr("超大", "Huge")
        )
        val selected = sizes.indexOfFirst { abs(it - fontScale) < 0.01f }.coerceAtLeast(1)
        MusicOptionDialog(
            title = tr("歌词字体字号", "Lyric font size"),
            options = labels,
            selected = selected,
            onPick = { idx ->
                onFontScale(sizes[idx])
                fontDialog = false
            },
            onDismiss = { fontDialog = false }
        )
    }

    if (offsetDialog) {
        val offsets = listOf(-800L, -400L, -200L, 0L, 200L, 400L, 800L)
        val labels = listOf(
            tr("提前 0.8 秒", "0.8s earlier"), tr("提前 0.4 秒", "0.4s earlier"),
            tr("提前 0.2 秒", "0.2s earlier"), tr("不调整", "No offset"),
            tr("延后 0.2 秒", "0.2s later"), tr("延后 0.4 秒", "0.4s later"),
            tr("延后 0.8 秒", "0.8s later")
        )
        val selected = offsets.indexOfFirst { it == offsetMs }.coerceAtLeast(3)
        MusicOptionDialog(
            title = tr("调整歌词快慢", "Lyric timing"),
            options = labels,
            selected = selected,
            onPick = { idx ->
                onOffset(offsets[idx])
                offsetDialog = false
            },
            onDismiss = { offsetDialog = false }
        )
    }

    if (languageDialog) {
        val languages = LyricTranslateLanguages.ALL
        val selected = languages.indexOfFirst { it.code == targetLanguage }.coerceAtLeast(0)
        MusicOptionDialog(
            title = tr("选择译词语言", "Translation language"),
            options = languages.map { tr(it.labelZh, it.labelEn) },
            selected = selected,
            onPick = { idx ->
                onTargetLanguage(languages[idx].code)
                languageDialog = false
            },
            onDismiss = { languageDialog = false }
        )
    }
}

@Composable
private fun LyricArtPreviewOverlay(
    item: LyricArtPreview,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    BackHandler { onClose() }
    Box(
        Modifier.fillMaxSize().background(Color(0xF20B0A09)).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    tr("关闭", "Close"),
                    color = Color(0xFFD9C6B6),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onClose() }.padding(8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Image(
                bitmap = item.bitmap.asImageBitmap(),
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StagePill(tr("保存到相册", "Save"), Color(0xFF3B322C)) { onSave() }
                StagePill(tr("分享", "Share"), StageOrange) { onShare() }
            }
        }
    }
}

@Composable
internal fun LyricIssueSheet(
    mode: String,
    song: String,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    BackHandler { onClose() }
    val isReport = mode == "report"
    val reasons = if (isReport) {
        listOf(
            tr("侵权内容", "Copyright"),
            tr("违法违规", "Illegal content"),
            tr("其他问题", "Other")
        )
    } else {
        listOf(
            tr("歌词错字", "Wrong text"),
            tr("时间轴不准", "Timing"),
            tr("翻译问题", "Translation"),
            tr("其他问题", "Other")
        )
    }
    var reason by remember { mutableStateOf(reasons.first()) }
    var detail by remember { mutableStateOf("") }

    fun submit(share: Boolean) {
        val text = LyricIssueStore.toText(mode, song, reason, detail)
        val saved = LyricIssueStore.add(ctx, mode, song, reason, detail)
        if (!saved) {
            stageToast(ctx, tr("保存失败，请稍后再试", "Could not save"))
            return
        }
        if (share) {
            runCatching {
                ctx.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        },
                        tr("发送给开发者", "Send to developer")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        onClose()
        stageToast(
            ctx,
            if (share) tr("已保存，并打开系统分享", "Saved; share sheet opened")
            else tr("已保存到本机反馈队列", "Saved to local feedback queue")
        )
    }

    Box(
        Modifier.fillMaxSize().background(Layer.Scrim).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MoreBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Text(
                if (isReport) tr("举报这首歌", "Report this song") else tr("反馈歌词错误", "Report a lyric issue"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MoreInk
            )
            Spacer(Modifier.height(6.dp))
            Text(
                song.ifBlank { tr("未命名歌曲", "Untitled") },
                fontSize = 12.sp,
                color = MoreSub,
                maxLines = 1
            )
            Spacer(Modifier.height(14.dp))
            Text(tr("问题类型", "Issue type"), fontSize = 12.sp, color = MoreSub)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { item ->
                    val selected = item == reason
                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp))
                            .background(if (selected) MoreAccent else Color.White)
                            .clickable { reason = item }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            item, fontSize = 11.sp,
                            color = if (selected) Color(0xFF06130B) else MoreInk
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            TextField(
                value = detail,
                onValueChange = { detail = it.take(500) },
                placeholder = {
                    Text(
                        tr("补充说明（选填）", "Details (optional)"),
                        fontSize = 12.sp, color = MoreSub
                    )
                },
                textStyle = TextStyle(fontSize = 13.sp, color = MoreInk),
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                tr(
                    "当前没有云端工单，记录会保存在本机；可用系统分享发给开发者。",
                    "Saved locally; use system share to send it to the developer."
                ),
                fontSize = 10.sp,
                color = MoreSub,
                lineHeight = 14.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StagePill(tr("仅保存", "Save"), Color(0xFFEDE3D8), MoreInk) { submit(false) }
                StagePill(tr("保存并分享", "Save & share"), StageOrange) { submit(true) }
                Spacer(Modifier.weight(1f))
                Text(
                    tr("取消", "Cancel"),
                    fontSize = 12.sp,
                    color = MoreSub,
                    modifier = Modifier.clickable { onClose() }.padding(8.dp)
                )
            }
        }
    }
}

private data class LyricArtPreview(val bitmap: Bitmap, val title: String)

@Composable
private fun LyricSettingTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(14.dp))
                .background(if (active) MoreAccent.copy(alpha = 0.16f) else Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MoreInk, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            label, fontSize = 11.sp, color = MoreInk,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun LyricSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    badge: String? = null,
    value: String = "",
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MoreInk, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(
            title, fontSize = 15.sp, color = MoreInk,
            fontWeight = FontWeight.Medium, maxLines = 1
        )
        if (badge != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .background(if (badge == "new") Color(0xFFFF5A5F) else MoreAccent)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    badge, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                    color = if (badge == "new") Color.White else Color(0xFF06130B),
                    maxLines = 1
                )
            }
        }
        if (value.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(value, fontSize = 12.sp, color = MoreSub, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/**
 * 把指定歌词行滚到歌词视口的正中间。
 * 先按索引粗定位，再根据该行实际高度做一次像素级校正，
 * 避免只靠固定 offset 时因行高不同/边距变化而偏上或偏下。
 */
private suspend fun LazyListState.centerOnItem(index: Int, initialOffset: Int = 0) {
    animateScrollToItem(index, initialOffset)
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    val itemCenter = info.offset + info.size / 2
    val delta = itemCenter - viewportCenter
    if (abs(delta) > 2) {
        animateScrollBy(
            delta.toFloat(),
            tween(durationMillis = 220, easing = FastOutSlowInEasing)
        )
    }
}

/**
 * 自动跟随时只做「把当前行平滑推到正中」的一次动画。
 * 直接用 animateScrollToItem + 二次校正会先跑到顶部再跳回中间，
 * 表现为歌词一顿一顿地跳；逐句滚动时当前行和下一行都在视口内，
 * 算一次像素差再 animateScrollBy 就能保持每句匀速上移。
 */
private suspend fun LazyListState.followActiveLine(index: Int, centerOffset: Int) {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (info == null) {
        // 跨多行跳转（点歌词 / 切歌）才走粗定位 + 校正
        centerOnItem(index, centerOffset)
        return
    }
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    val delta = info.offset + info.size / 2 - viewportCenter
    if (abs(delta) > 1) {
        animateScrollBy(
            delta.toFloat(),
            tween(durationMillis = 320, easing = FastOutSlowInEasing)
        )
    }
}

@Composable
private fun KaraokeLyricsList(
    lyrics: LyricsResult?,
    loading: Boolean,
    failed: Boolean,
    dark: Boolean,
    ink: Color,
    sub: Color,
    bg: Color,
    onFetch: (Boolean) -> Unit,
    onManualSearch: () -> Unit,
    fontScale: Float = 1f,
    guide: Boolean = false,
    offsetMs: Long = 0L,
    translations: List<String>? = null,
    traditional: Boolean = false,
    twoLine: Boolean = false,
    onActiveLine: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val baseTimed = remember(lyrics, MusicPlayer.durationMs) {
        lyrics?.lines?.takeIf { it.isNotEmpty() }
            ?: lyrics?.plain?.let {
                LyricsRepository.estimateLines(it, MusicPlayer.durationMs)
            }.orEmpty()
    }
    val timed = remember(baseTimed, offsetMs) {
        if (offsetMs == 0L) baseTimed
        else baseTimed.map { it.copy(timeMs = (it.timeMs + offsetMs).coerceAtLeast(0L)) }
    }
    val lines = remember(timed) { timed.map { it.text } }
    val displayLines = remember(lines, traditional) {
        if (traditional) lines.map { HanziScript.toTraditional(it) } else lines
    }
    val displayTranslations = remember(translations, traditional) {
        translations?.map { if (traditional) HanziScript.toTraditional(it) else it }
    }
    val estimated = remember(lyrics, timed) {
        lyrics?.lines.isNullOrEmpty() && timed.isNotEmpty()
    }
    when {
        loading -> Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("正在搜索歌词…", "Searching lyrics…"), fontSize = 12.sp, color = sub)
                Spacer(Modifier.height(14.dp))
                // 歌词加载不转圈：直接铺几条长短不一的扫光行，等待更安静
                ProvideShimmer {
                    SkeletonLyricLines(Modifier.fillMaxWidth(), lines = 6)
                }
            }
        }
        lyrics == null || lines.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (failed) tr("没有匹配到歌词", "No lyrics matched") else tr("暂无歌词", "No lyrics"),
                    fontSize = 13.sp, color = ink
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StagePill(if (failed) tr("重试", "Retry") else tr("获取歌词", "Get lyrics"), StageOrange) {
                        onFetch(true)
                    }
                    StagePill(tr("手动搜索", "Search"), Color(0xFFF0D7C1), ink) { onManualSearch() }
                }
            }
        }
        else -> BoxWithConstraints(modifier) {
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val half = (maxHeight / 2 - (44f * fontScale).dp).coerceAtLeast(0.dp)
            // 顶部 contentPadding 已经让第 0 行落在视口中心附近；
            // scrollToItem 的 offset 再传负 half 会先跑到顶部、再被校正回中间，
            // 听感上就是歌词一顿一顿地跳。这里保持 0，校正只做像素级补差。
            val centerOffset = 0
            val activeIndex by remember(timed, lines.size) {
                derivedStateOf {
                    timed.indexOfLast { it.timeMs <= MusicPlayer.positionMs }.coerceAtLeast(0)
                }
            }
            val lineFill by remember(timed, activeIndex) {
                derivedStateOf {
                    if (activeIndex !in timed.indices) {
                        0f
                    } else {
                        val start = timed[activeIndex].timeMs
                        val end = timed.getOrNull(activeIndex + 1)?.timeMs
                            ?: MusicPlayer.durationMs.coerceAtLeast(start + 1L)
                        ((MusicPlayer.positionMs - start).toFloat() / (end - start).coerceAtLeast(1L))
                            .coerceIn(0f, 1f)
                    }
                }
            }
            // 用户一碰歌词就退出跟随，松手后不强制弹回；只有点「回到当前」才恢复
            var follow by remember(lyrics) { mutableStateOf(true) }
            LaunchedEffect(listState) {
                listState.interactionSource.interactions.collect { interaction ->
                    if (interaction is DragInteraction.Start) follow = false
                }
            }
            LaunchedEffect(timed) {
                follow = true
            }
            LaunchedEffect(timed, activeIndex, follow, centerOffset) {
                if (follow && timed.isNotEmpty()) listState.followActiveLine(activeIndex, centerOffset)
            }
            LaunchedEffect(activeIndex, displayLines) {
                onActiveLine(displayLines.getOrElse(activeIndex) { "" })
            }
            if (twoLine) {
                // 双行模式：当前句大字 + 下一句小字，固定居中不滚动（QQ 音乐默认样式）
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val cur = displayLines.getOrElse(activeIndex) { "" }
                    val next = displayLines.getOrElse(activeIndex + 1) { "" }
                    Text(
                        cur.ifBlank { tr("…", "…") },
                        fontSize = (24f * fontScale).sp,
                        lineHeight = (36f * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dark) Color(0xFFFFD8B0) else StageLyricsAccent,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    if (displayTranslations?.getOrNull(activeIndex)?.isNotBlank() == true) {
                        Text(
                            displayTranslations[activeIndex],
                            fontSize = (13f * fontScale).sp,
                            color = sub,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    if (next.isNotBlank()) {
                        Text(
                            next,
                            fontSize = (16f * fontScale).sp,
                            lineHeight = (24f * fontScale).sp,
                            color = if (dark) Color(0xFF7E6A5B) else Color(0x2EA06A48),
                            textAlign = TextAlign.Center
                        )
                        if (displayTranslations?.getOrNull(activeIndex + 1)?.isNotBlank() == true) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                displayTranslations[activeIndex + 1],
                                fontSize = (11f * fontScale).sp,
                                color = sub.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = half + 20.dp, bottom = half + 26.dp),
                userScrollEnabled = true
            ) {
                itemsIndexed(displayLines, key = { i, _ -> i }) { i, line ->
                    KaraokeLyricLine(
                        text = line,
                        translation = displayTranslations?.getOrNull(i),
                        active = i == activeIndex,
                        fill = if (i == activeIndex) lineFill else 0f,
                        dark = dark,
                        fontScale = fontScale,
                        guide = guide,
                        distance = abs(i - activeIndex),
                        onClick = {
                            val duration = MusicPlayer.durationMs.coerceAtLeast(1L)
                            val fraction = if (i in timed.indices)
                                (timed[i].timeMs.toFloat() / duration).coerceIn(0f, 1f)
                            else 0f
                            MusicPlayer.seekTo(fraction)
                            follow = true
                            scope.launch { listState.centerOnItem(i, centerOffset) }
                        }
                    )
                }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to bg,
                        0.12f to Color.Transparent,
                        0.86f to Color.Transparent,
                        1f to bg
                    )
                )
            )
            AnimatedVisibility(
                visible = !follow,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
            ) {
                StagePill(tr("回到当前", "Back to current"), StageOrange) {
                    follow = true
                    scope.launch { listState.centerOnItem(activeIndex, centerOffset) }
                }
            }
            if (estimated) {
                Text(
                    tr("按字数与时长估算对齐", "Timeline estimated from length"),
                    fontSize = 9.sp,
                    color = sub.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun KaraokeLyricLine(
    text: String,
    translation: String?,
    active: Boolean,
    fill: Float,
    dark: Boolean,
    fontScale: Float,
    guide: Boolean,
    distance: Int = 0,
    onClick: () -> Unit
) {
    // 逐字点亮进度：平滑跟随播放位置，避免逐帧跳变
    val progress by animateFloatAsState(
        targetValue = if (active) fill.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(520, easing = LinearEasing),
        label = "karaokeFill"
    )
    // 按与当前行的距离分级淡出：相邻行略亮，越远越暗，视线自然聚焦到正中
    val dimAlpha = when {
        active -> 1f
        distance == 1 -> 0.82f
        distance == 2 -> 0.58f
        else -> 0.40f
    }
    val base = (if (dark) Color(0xFF7E6A5B) else Color(0x2EA06A48)).copy(alpha = dimAlpha)
    val activeColor = if (dark) Color(0xFFFFD8B0) else StageLyricsAccent
    val lineStyle = TextStyle(
        color = base,
        fontSize = ((if (active) 20f else 17f) * fontScale).sp,
        lineHeight = ((if (active) 30f else 26f) * fontScale).sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center
    )
    // 两段式单层 AnnotatedString：已唱前缀一段高亮、未唱后缀一段暗色。
    // 不叠第二层裁剪文本，也不把当前行放进独立缩放图层——避免部分设备上
    // 两个图层亚像素错位漏色形成重影；当前行层级靠字号、加粗与颜色区分。
    val annotated = remember(text, active, progress, activeColor, base) {
        buildAnnotatedString {
            if (!active) {
                append(text)
            } else {
                val total = text.length
                val lit = (progress * total).roundToInt().coerceIn(0, total)
                if (lit > 0) {
                    withStyle(SpanStyle(color = activeColor, fontWeight = FontWeight.Bold)) {
                        append(text.substring(0, lit))
                    }
                }
                if (lit < total) {
                    withStyle(SpanStyle(color = base, fontWeight = FontWeight.Bold)) {
                        append(text.substring(lit))
                    }
                }
            }
        }
    }
    Box(
        Modifier.fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = (11f * fontScale).dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth()) {
                if (active && guide) {
                    Text(
                        "♪",
                        fontSize = 10.sp,
                        color = StageOrange,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
                    )
                }
                Text(
                    annotated,
                    style = lineStyle,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (!translation.isNullOrBlank()) {
                Text(
                    translation,
                    fontSize = (12f * fontScale).sp,
                    lineHeight = (17f * fontScale).sp,
                    color = (if (dark) Color(0xFFB9A392) else Color(0xFF9B7159))
                        .copy(alpha = 0.85f * dimAlpha),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )
            }
        }
    }
}

/** 图三：黑胶页。唱片持续旋转、唱针随播放状态落下/抬起，播控与进度条全部可用。 */
@Composable
private fun VinylStage(
    stagePosition: Float,
    song: LocalSong?,
    art: ImageBitmap?,
    lyrics: LyricsResult?,
    lyricsLoading: Boolean,
    lyricsFailed: Boolean,
    onOpenLyrics: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSkin: () -> Unit,
    onManualSearch: () -> Unit,
    onCycleMode: () -> Unit,
    onCycleEq: () -> Unit,
    onOpenSoundPanel: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenMoreMenu: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSongAction: (LocalSong) -> Unit = {}
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var scrub by remember { mutableStateOf<Float?>(null) }
    val shown = scrub ?: MusicPlayer.progress
    val currentLine = stageCurrentLine(lyrics)
    val tint = rememberCoverTint(art)
    // 皮肤定调、歌曲定色：用封面主色推一套渐变，再和皮肤本色调和，
    // 换歌就换底色，皮肤之间也仍然各有各的味道。
    val skin = PlayerSkinStore.current
    val songBg = songStageBg(tint, skin.dark)
    val topBg = mixColor(skin.bgTop, songBg[0], 0.58f)
    val midBg = mixColor(skin.bgMid, songBg[1], 0.58f)
    val bottomBg = mixColor(skin.bgBottom, songBg[2], 0.58f)

    Box(Modifier.fillMaxSize()) {
        // 底：图片皮肤铺整页底图 + 一层薄纱，纯色皮肤铺渐变；动态皮肤再叠动画层
        SkinBackdrop(skin, topBg, midBg, bottomBg, playing = MusicPlayer.isPlaying, tint = tint, art = art)
        if (skin.layout == PlayerSkinLayout.STAGE) {
        AnimatedContent(
            targetState = song?.id ?: -1L,
            transitionSpec = {
                (fadeIn(tween(320)) + scaleIn(initialScale = 0.96f, animationSpec = tween(320))) togetherWith
                    fadeOut(tween(200))
            },
            label = "stagePageTransition",
            modifier = Modifier.fillMaxSize()
        ) {
        Column(
            Modifier.fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
        StageTopBar(
            page = 1, position = stagePosition, ink = skin.ink, dark = false,
            onBack = onBack, onShare = onShare, onSkin = onSkin,
            onToggleDark = {}
        )
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(0.92f).graphicsLayer {
                // 随 Pager 滑动的视差：唱片反向位移、轻微 3D 倾斜并略微缩小，
                // rel=0 停在唱片页正中；向左右两页滑动时视差逐渐加大。
                val rel = (stagePosition - 1f).coerceIn(-1f, 1f)
                translationX = rel * -42f
                rotationY = rel * 10f
                val s = 1f - abs(rel) * 0.05f
                scaleX = s
                scaleY = s
            },
            contentAlignment = Alignment.Center
        ) {
            val d = minOf(maxWidth * 0.92f, maxHeight)
            Canvas(Modifier.fillMaxSize()) {
                val r = minOf(size.width, size.height) * 0.47f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = 0.30f), Color.Transparent),
                        center = center,
                        radius = r * 1.25f
                    ),
                    radius = r * 1.25f,
                    center = center
                )
            }
            VinylArtwork(
                art = art,
                playing = MusicPlayer.isPlaying,
                skin = skin,
                onSingleTap = onOpenLyrics,
                modifier = Modifier.size(d)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            tr("正在播放 · 黑胶时间", "Now playing · Vinyl"),
            fontSize = 10.sp, color = skin.sub.copy(alpha = 0.72f)
        )
        // 频谱律动：播放时随节拍跳动，暂停时伏低；纯动画零权限
        SpectrumBars(
            color = skin.accent,
            playing = MusicPlayer.isPlaying,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 46.dp, vertical = 6.dp)
                .height(20.dp)
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                song?.title ?: tr("水莲", "WaterLily"),
                fontSize = 30.sp, fontWeight = FontWeight.Bold, color = skin.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).combinedClickable(
                    onClick = {},
                    onLongClick = {
                        song?.let {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSongAction(it)
                        }
                    }
                )
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedHeart(MusicPlayer.isFavorite(), onToggleFavorite, inactive = skin.sub)
                Text(
                    if (MusicPlayer.isFavorite()) tr("已收藏", "Saved") else tr("收藏", "Save"),
                    fontSize = 9.sp, color = skin.sub
                )
            }
        }
        Text(
            song?.artist ?: tr("未知歌手", "Unknown artist"),
            fontSize = 15.sp, color = skin.sub,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = {
                    song?.let {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSongAction(it)
                    }
                }
            )
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StageChip(tr("本地", "Local"), Icons.Outlined.Folder, skin)
            StageChip(stageEqLabel(), Lucide.AudioLines, skin)
            StageChip(tr("原声", "Original"), Icons.Outlined.Headphones, skin)
            StageChip(stageModeLabel(), Lucide.Repeat2, skin)
            StageChip(if (MusicPlayer.isPlaying) tr("播放中", "Playing") else tr("已暂停", "Paused"), Icons.Outlined.Waves, skin)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            currentLine ?: when {
                lyricsLoading -> tr("歌词加载中…", "Loading lyrics…")
                lyricsFailed -> tr("暂无歌词，点「歌词」搜索", "No lyrics · tap Lyrics to search")
                else -> tr("歌词加载中…", "Loading lyrics…")
            },
            fontSize = 14.sp, color = skin.accentDeep,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(0.12f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VinylAction(tr("歌词", "Lyrics"), Lucide.Search, skin) { onOpenLyrics() }
            VinylAction(tr("音效", "Sound"), Lucide.AudioLines, skin) { onOpenSoundPanel() }
            VinylAction(tr("下载", "Download"), Lucide.Download, skin) {
                stageToast(ctx, tr("当前歌曲已在本地音乐库", "This song is already in your local library"))
            }
            VinylAction(tr("评论", "Comment"), Lucide.MessageSquare, skin) {
                onOpenComments()
            }
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(StageRed)
                    .clickable {
                        stageToast(ctx, tr("伴奏模式暂未支持", "Accompaniment mode is not supported yet"))
                    }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(tr("听伴奏", "Instrumental"), fontSize = 9.sp, color = Color.White, maxLines = 1)
            }
            VinylAction("", Lucide.EllipsisVertical, skin) {
                onOpenMoreMenu()
            }
        }
        Spacer(Modifier.height(8.dp))
        StageSeekBar(
            progress = shown,
            track = skin.track,
            fill = skin.accent,
            thumb = skin.accent,
            playing = MusicPlayer.isPlaying,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            onScrub = { scrub = it },
            onSeek = {
                MusicPlayer.seekTo(it)
                scrub = null
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatMs(if (scrub != null) (scrub!! * MusicPlayer.durationMs).toLong() else MusicPlayer.positionMs),
                fontSize = 11.sp, color = skin.sub
            )
            Text(formatMs(MusicPlayer.durationMs), fontSize = 11.sp, color = skin.sub)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StageControlButton(tr("播放模式", "Play mode"), {
                // onCycleMode（cyclePlayMode）自己会提示模式名，这里不再重复弹第二条
                onCycleMode()
            }) { PlayModeIcon(ink = skin.ink, accent = skin.accentDeep) }
            StageControlButton(tr("上一首", "Previous"), {
                MusicPlayer.prev()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }) { Icon(Lucide.SkipBack, null, tint = skin.ink, modifier = Modifier.size(26.dp)) }
            AnimatedPlayButton(
                playing = MusicPlayer.isPlaying,
                onClick = {
                    stageTogglePlay()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                size = 66.dp,
                color = skin.accent
            )
            StageControlButton(tr("下一首", "Next"), {
                MusicPlayer.next()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }) { Icon(Lucide.SkipForward, null, tint = skin.ink, modifier = Modifier.size(26.dp)) }
            StageControlButton(tr("播放列表", "Playlist"), {
                onOpenPlaylist()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }) { Icon(Lucide.ListMusic, null, tint = skin.ink, modifier = Modifier.size(24.dp)) }
        }
        Spacer(Modifier.height(8.dp))
    }
        }
        } else {
            // 其余皮肤走各自的版式骨架（科幻终端 / 海报 / 环绕 / 双栏玻璃）
            StageVariant(
                skin = skin,
                song = song,
                art = art,
                stagePosition = stagePosition,
                currentLine = currentLine,
                progress = shown,
                onScrub = { scrub = it },
                onSeek = {
                    MusicPlayer.seekTo(it)
                    scrub = null
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onOpenLyrics = onOpenLyrics,
                onBack = onBack,
                onShare = onShare,
                onSkin = onSkin,
                onOpenMoreMenu = onOpenMoreMenu,
                onOpenPlaylist = onOpenPlaylist,
                onToggleFavorite = onToggleFavorite
            )
        }
    }
}

// ============================================================
// 播放页共用的黑胶唱片 / 顶部栏 / 进度条等基础组件
// ============================================================

// ============================================================
// 横屏播放器（参考图二 / 图三）
// 手机横过来、或更多菜单点「横屏模式」，都进这一页：
// 左封面 + 波形进度，右歌词；点屏幕任意处唤出/收起按钮。
// ============================================================

/** 歌词时间轴：优先真实 LRC，纯文本按总时长均分（与竖屏页同一套口径）。 */
private fun stageTimeline(lyrics: LyricsResult?): List<LrcLine> =
    lyrics?.lines?.takeIf { it.isNotEmpty() }
        ?: lyrics?.plain?.let { LyricsRepository.estimateLines(it, MusicPlayer.durationMs) }
        ?: emptyList()

private fun stageLineIndex(timeline: List<LrcLine>): Int {
    if (timeline.isEmpty()) return -1
    val i = timeline.indexOfLast { it.timeMs <= MusicPlayer.positionMs }
    return if (i < 0) 0 else i
}

/**
 * 跟着歌曲走的舞台底色：用封面主色按皮肤的明暗倾向推三段渐变。
 * 暖封面出暖底、冷封面出冷底 —— 等于每首歌自动配一套背景，而不是永远橙米色。
 */
private fun songStageBg(tint: Color, dark: Boolean): List<Color> = if (dark) {
    listOf(
        mixColor(tint, Color.Black, 0.80f),
        mixColor(tint, Color.Black, 0.90f),
        mixColor(tint, Color.Black, 0.68f)
    )
} else {
    listOf(
        mixColor(tint, Color.White, 0.86f),
        mixColor(tint, Color.White, 0.94f),
        mixColor(tint, Color.White, 0.78f)
    )
}

@Composable
private fun LandscapeStage(
    song: com.example.my.data.music.LocalSong?,
    art: ImageBitmap?,
    lyrics: LyricsResult?,
    onPortrait: () -> Unit,
    onOpenSoundFx: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val ctx = LocalContext.current
    val bgUri = StageBackgroundStore.uri

    // ④ 自定义背景：从相册/文件里挑视频或图片，持久授权，重启还生效
    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            StageBackgroundStore.set(ctx, uri.toString())
            stageToast(ctx, tr("已设置播放器背景", "Player background set"))
        }
    }

    var controls by remember { mutableStateOf(false) }
    // 唤出后 4 秒自动收起，回到参考图一那种「净屏」状态
    LaunchedEffect(controls) {
        if (controls) {
            delay(4200)
            controls = false
        }
    }

    // ③ 闪光灯：跟着旋律振幅闪；拿不到振幅就退化成节拍
    var torchOn by remember { mutableStateOf(false) }
    val torchPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[android.Manifest.permission.CAMERA] == true) {
            torchOn = true
        } else {
            stageToast(
                ctx,
                tr("需要相机权限才能控制闪光灯", "Camera permission is required for the flash")
            )
        }
    }
    // 播放页整页的电源开关：退到后台后不再驱动动画和闪光灯循环
    val stageResumed = rememberIsResumed()
    LaunchedEffect(torchOn, stageResumed) {
        if (!torchOn || !stageResumed) {
            TorchLamp.set(ctx, false)
            return@LaunchedEffect
        }
        if (!TorchLamp.available(ctx)) {
            stageToast(ctx, tr("这台设备没有可用的闪光灯", "No flash on this device"))
            torchOn = false
            return@LaunchedEffect
        }
        val beat = BeatMeter()
        // 振幅要靠 Visualizer 读，得先有录音权限；没权限就用稳定节拍兜底
        if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            beat.start(MusicPlayer.audioSessionId)
        }
        try {
            while (true) {
                val lit = MusicPlayer.isPlaying && (
                    if (beat.active) beat.level > 0.28f
                    else (MusicPlayer.positionMs / 250L) % 2L == 0L
                    )
                TorchLamp.set(ctx, lit)
                delay(55)
            }
        } finally {
            beat.stop()
            TorchLamp.set(ctx, false)
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { controls = !controls }
    ) {
        // 默认是图一那套版式；用户在 ④ 里挑好视频后才切到图二的全屏背景版式。
        // 两个方向都走淡入淡出（420ms），切换不硬切。
        Crossfade(
            targetState = bgUri != null,
            animationSpec = tween(420),
            label = "landscape-mode"
        ) { hasVideo ->
            if (hasVideo) {
                LandscapeVideoContent(
                    uri = bgUri.orEmpty(),
                    song = song,
                    art = art,
                    lyrics = lyrics,
                    onToggleFavorite = onToggleFavorite
                )
            } else {
                LandscapeDefaultContent(
                    song = song,
                    art = art,
                    lyrics = lyrics,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }

        // ── 点一下才出现：① 回竖屏 ② 音效开关 ③ 闪光灯跟节奏 ④ 换背景 ──
        if (controls) {
            LandOverlayButton(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 10.dp),
                label = tr("退出横屏", "Exit landscape"),
                onClick = onPortrait
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = tr("退出横屏", "Exit landscape"),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Row(
                Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 已经设过背景时，长按这颗按钮恢复默认（否则选了就撤不掉）
                val clearBg: (() -> Unit)? = if (bgUri != null) {
                    {
                        StageBackgroundStore.clear(ctx)
                        stageToast(ctx, tr("已恢复默认背景", "Background reset"))
                    }
                } else null
                // 长按是隐藏手势，不写进说明没人会试；设了背景就多给一颗显式的「清除」
                if (bgUri != null) {
                    LandOverlayButton(
                        label = tr("清除背景", "Clear background"),
                        onClick = { clearBg?.invoke() }
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                // ② 音效：不再在横屏里切「流行/关闭」，直接进音效库自己挑
                LandOverlayButton(
                    label = tr("音效库", "Sound effects"),
                    active = MusicPlayer.soundFxOn,
                    onClick = onOpenSoundFx
                ) {
                    Icon(Lucide.AudioLines, null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
                LandOverlayButton(
                    label = tr("闪光灯跟节奏", "Flash on beat"),
                    active = torchOn,
                    onClick = {
                        if (torchOn) {
                            torchOn = false
                        } else if (ctx.checkSelfPermission(android.Manifest.permission.CAMERA) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            torchOn = true
                        } else {
                            torchPerm.launch(
                                arrayOf(
                                    android.Manifest.permission.CAMERA,
                                    android.Manifest.permission.RECORD_AUDIO
                                )
                            )
                        }
                    }
                ) {
                    Icon(
                        Icons.Outlined.FlashOn, null,
                        tint = Color.White, modifier = Modifier.size(19.dp)
                    )
                }
                LandOverlayButton(
                    label = tr("自定义背景", "Custom background"),
                    active = bgUri != null,
                    onClick = { pickBg.launch(arrayOf("video/*", "image/*")) },
                    onLongClick = clearBg
                ) {
                    Icon(
                        Icons.Outlined.VideoLibrary, null,
                        tint = Color.White, modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

/**
 * 换了骨架的那几套皮肤：同一台播放器，按皮肤声明的版式重新排布。
 * 所有版式共用同一批颜色字段和同一批子组件（[VinylArtwork] / [StageSeekBar] /
 * [AnimatedPlayButton] / [AnimatedHeart] / [StageAlbumArt]），
 * 所以皮肤数据不必为某个版式额外加字段，交互也保持一致。
 */
@Composable
private fun StageVariant(
    skin: PlayerSkin,
    song: com.example.my.data.music.LocalSong?,
    art: ImageBitmap?,
    stagePosition: Float,
    currentLine: String?,
    progress: Float,
    onScrub: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    onOpenLyrics: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSkin: () -> Unit,
    onOpenMoreMenu: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    when (skin.layout) {
        PlayerSkinLayout.HUD -> HudLayout(
            skin, song, art, stagePosition, currentLine, progress, onScrub, onSeek,
            onOpenLyrics, onBack, onShare, onSkin, onOpenMoreMenu, onOpenPlaylist, onToggleFavorite
        )
        PlayerSkinLayout.POSTER -> PosterLayout(
            skin, song, art, stagePosition, currentLine, progress, onScrub, onSeek,
            onOpenLyrics, onBack, onShare, onSkin, onOpenMoreMenu, onOpenPlaylist, onToggleFavorite
        )
        PlayerSkinLayout.ORBIT -> OrbitLayout(
            skin, song, art, stagePosition, currentLine, progress, onScrub, onSeek,
            onOpenLyrics, onBack, onShare, onSkin, onOpenMoreMenu, onOpenPlaylist, onToggleFavorite
        )
        PlayerSkinLayout.DUO -> DuoLayout(
            skin, song, art, stagePosition, currentLine, progress, onScrub, onSeek,
            onOpenLyrics, onBack, onShare, onSkin, onOpenMoreMenu, onOpenPlaylist, onToggleFavorite
        )
        PlayerSkinLayout.STAGE -> Unit
    }
}

/** 科幻终端用的方形描边按钮 */
@Composable
private fun HudSquareButton(
    skin: PlayerSkin,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(skin.chipBg)
            .border(1.dp, skin.accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** HUD 版式：唱片落在雷达刻度环里，下面是分段读数和终端式标题 */
@Composable
private fun HudLayout(
    skin: PlayerSkin,
    song: com.example.my.data.music.LocalSong?,
    art: ImageBitmap?,
    stagePosition: Float,
    currentLine: String?,
    progress: Float,
    onScrub: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    onOpenLyrics: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSkin: () -> Unit,
    onOpenMoreMenu: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 18.dp)
    ) {
        StageTopBar(
            page = 1, position = stagePosition, ink = skin.ink, dark = true,
            onBack = onBack, onShare = onShare, onSkin = onSkin, onToggleDark = {}
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "SYS · PLAYBACK LINK", fontSize = 9.sp, color = skin.accent,
                letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace
            )
            Text(
                "44.1kHz / 24bit", fontSize = 9.sp, color = skin.sub,
                letterSpacing = 1.sp, fontFamily = FontFamily.Monospace
            )
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(0.94f),
            contentAlignment = Alignment.Center
        ) {
            val d = minOf(maxWidth * 0.94f, maxHeight)
            Canvas(Modifier.size(d)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                // 刻度环：72 根，整点更长
                repeat(72) { i ->
                    val a = (i * 5f) * PI / 180f
                    val major = i % 6 == 0
                    val inner = r * (if (major) 0.90f else 0.945f)
                    drawLine(
                        color = skin.sub.copy(alpha = if (major) 0.8f else 0.35f),
                        start = Offset(c.x + cos(a).toFloat() * inner, c.y + sin(a).toFloat() * inner),
                        end = Offset(
                            c.x + cos(a).toFloat() * r * 0.985f,
                            c.y + sin(a).toFloat() * r * 0.985f
                        ),
                        strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx()
                    )
                }
                // 轨道 + 从 12 点顺时针的进度弧
                drawArc(
                    color = skin.track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * 0.855f, c.y - r * 0.855f),
                    size = Size(r * 1.71f, r * 1.71f),
                    style = Stroke(width = 1.dp.toPx())
                )
                drawArc(
                    color = skin.accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(c.x - r * 0.83f, c.y - r * 0.83f),
                    size = Size(r * 1.66f, r * 1.66f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            VinylArtwork(
                art = art,
                playing = MusicPlayer.isPlaying,
                skin = skin,
                onSingleTap = onOpenLyrics,
                modifier = Modifier.size(d * 0.62f)
            )
        }
        // 40 格分段进度：比圆环更"读数"一点
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(40) { i ->
                val on = (i + 1) / 40f <= progress.coerceIn(0f, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .background(if (on) skin.accent else skin.track.copy(alpha = 0.55f))
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatMs(MusicPlayer.positionMs), fontSize = 10.sp, color = skin.sub,
                fontFamily = FontFamily.Monospace
            )
            Text(
                formatMs(MusicPlayer.durationMs), fontSize = 10.sp, color = skin.sub,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            song?.title ?: tr("水莲", "WaterLily"),
            fontSize = 23.sp, fontWeight = FontWeight.Bold, color = skin.ink,
            letterSpacing = 1.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            (song?.artist ?: tr("未知歌手", "Unknown artist")) + "  //  " +
                (currentLine ?: tr("歌词加载中…", "Loading lyrics…")),
            fontSize = 11.sp, color = skin.sub, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(0.10f))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HudSquareButton(skin, {
                MusicPlayer.prev()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }) { Icon(Lucide.SkipBack, null, tint = skin.ink, modifier = Modifier.size(22.dp)) }
            StageControlButton(tr("收藏", "Favorite"), onToggleFavorite) {
                AnimatedHeart(MusicPlayer.isFavorite(), onToggleFavorite, inactive = skin.sub)
            }
            AnimatedPlayButton(
                playing = MusicPlayer.isPlaying,
                onClick = {
                    stageTogglePlay()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                size = 62.dp,
                color = skin.accent
            )
            StageControlButton(tr("字幕", "Lyrics"), onOpenLyrics) {
                Icon(Lucide.MessageSquare, null, tint = skin.ink, modifier = Modifier.size(21.dp))
            }
            HudSquareButton(skin, {
                MusicPlayer.next()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }) { Icon(Lucide.SkipForward, null, tint = skin.ink, modifier = Modifier.size(22.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HudTextAction(skin, tr("播放列表", "Playlist")) { onOpenPlaylist() }
            HudTextAction(skin, tr("音效", "Sound")) {
                stageToast(ctx, tr("音效面板从右侧展开", "Sound panel opens on the right"))
            }
            HudTextAction(skin, tr("更多", "More")) { onOpenMoreMenu() }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun HudTextAction(skin: PlayerSkin, label: String, onClick: () -> Unit) {
    Text(
        label.uppercase() + " ›",
        fontSize = 9.sp,
        color = skin.sub,
        letterSpacing = 1.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp)
    )
}

/** 海报版式：超大字重歌名压场，封面缩成右下的小圆 */
@Composable
private fun PosterLayout(
    skin: PlayerSkin,
    song: com.example.my.data.music.LocalSong?,
    art: ImageBitmap?,
    stagePosition: Float,
    currentLine: String?,
    progress: Float,
    onScrub: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    onOpenLyrics: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSkin: () -> Unit,
    onOpenMoreMenu: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 22.dp)
    ) {
        StageTopBar(
            page = 1, position = stagePosition, ink = skin.ink, dark = false,
            onBack = onBack, onShare = onShare, onSkin = onSkin, onToggleDark = {}
        )
        Spacer(Modifier.height(14.dp))
        Text(
            tr("单曲档案 · 001", "SINGLE FILE · 001"),
            fontSize = 9.sp, color = skin.sub, letterSpacing = 2.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            song?.title ?: tr("水莲", "WaterLily"),
            fontSize = 36.sp, fontWeight = FontWeight.Bold, color = skin.ink,
            lineHeight = 42.sp, letterSpacing = (-0.5).sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (song?.artist ?: tr("未知歌手", "Unknown artist")).uppercase(),
                    fontSize = 12.sp, color = skin.sub, letterSpacing = 1.5.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    currentLine ?: tr("歌词加载中…", "Loading lyrics…"),
                    fontSize = 12.sp, color = skin.accentDeep, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onOpenLyrics() }
                )
            }
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, skin.ink.copy(alpha = 0.5f), CircleShape)
                    .padding(3.dp)
                    .clickable { onOpenLyrics() }
            ) {
                StageAlbumArt(art, Modifier.fillMaxSize(), CircleShape)
            }
        }
        Spacer(Modifier.weight(1f))
        StageSeekBar(
            progress = progress,
            track = skin.track,
            fill = skin.accent,
            thumb = skin.accent,
            modifier = Modifier.fillMaxWidth(),
            onScrub = onScrub,
            onSeek = {
                onSeek(it)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMs(MusicPlayer.positionMs), fontSize = 10.sp, color = skin.sub, letterSpacing = 0.5.sp)
            Text(formatMs(MusicPlayer.durationMs), fontSize = 10.sp, color = skin.sub, letterSpacing = 0.5.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StageControlButton(tr("收藏", "Favorite"), onToggleFavorite) {
                AnimatedHeart(MusicPlayer.isFavorite(), onToggleFavorite, inactive = skin.sub)
            }
            StageControlButton(tr("上一首", "Previous"), {
                MusicPlayer.prev()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }) { Icon(Lucide.SkipBack, null, tint = skin.ink, modifier = Modifier.size(26.dp)) }
            AnimatedPlayButton(
                playing = MusicPlayer.isPlaying,
                onClick = {
                    stageTogglePlay()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                size = 58.dp,
                color = skin.accent
            )
            StageControlButton(tr("下一首", "Next"), {
                MusicPlayer.next()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }) { Icon(Lucide.SkipForward, null, tint = skin.ink, modifier = Modifier.size(26.dp)) }
            StageControlButton(tr("更多", "More"), onOpenMoreMenu) {
                Icon(Lucide.EllipsisVertical, null, tint = skin.ink, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** 环绕版式：控件散在唱片四周，进度是外面一整圈弧 */
@Composable
private fun OrbitLayout(
    skin: PlayerSkin,
    song: com.example.my.data.music.LocalSong?,
    art: ImageBitmap?,
    stagePosition: Float,
    currentLine: String?,
    progress: Float,
    onScrub: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    onOpenLyrics: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSkin: () -> Unit,
    onOpenMoreMenu: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp)
    ) {
        StageTopBar(
            page = 1, position = stagePosition, ink = skin.ink, dark = true,
            onBack = onBack, onShare = onShare, onSkin = onSkin, onToggleDark = {}
        )
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val d = minOf(maxWidth * 0.96f, maxHeight * 0.98f)
            Canvas(Modifier.size(d)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = skin.sub.copy(alpha = 0.16f),
                    radius = r * 0.99f,
                    center = c,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawCircle(
                    color = skin.sub.copy(alpha = 0.10f),
                    radius = r * 0.80f,
                    center = c,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawArc(
                    color = skin.accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(c.x - r * 0.985f, c.y - r * 0.985f),
                    size = Size(r * 1.97f, r * 1.97f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            VinylArtwork(
                art = art,
                playing = MusicPlayer.isPlaying,
                skin = skin,
                onSingleTap = onOpenLyrics,
                modifier = Modifier.size(d * 0.66f)
            )
            Box(Modifier.align(Alignment.TopCenter)) {
                StageControlButton(tr("收藏", "Favorite"), onToggleFavorite) {
                    AnimatedHeart(MusicPlayer.isFavorite(), onToggleFavorite, inactive = skin.sub)
                }
            }
            Box(Modifier.align(Alignment.CenterStart)) {
                StageControlButton(tr("上一首", "Previous"), {
                    MusicPlayer.prev()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }) { Icon(Lucide.SkipBack, null, tint = skin.ink, modifier = Modifier.size(26.dp)) }
            }
            Box(Modifier.align(Alignment.CenterEnd)) {
                StageControlButton(tr("下一首", "Next"), {
                    MusicPlayer.next()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }) { Icon(Lucide.SkipForward, null, tint = skin.ink, modifier = Modifier.size(26.dp)) }
            }
            Box(Modifier.align(Alignment.BottomCenter)) {
                AnimatedPlayButton(
                    playing = MusicPlayer.isPlaying,
                    onClick = {
                        stageTogglePlay()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    size = 56.dp,
                    color = skin.accent
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatMs(MusicPlayer.positionMs), fontSize = 10.sp, color = skin.sub,
                fontFamily = FontFamily.Monospace
            )
            Text(
                tr("环绕 · 正在播放", "ORBIT · PLAYING"), fontSize = 9.sp,
                color = skin.sub, letterSpacing = 1.sp
            )
            Text(
                formatMs(MusicPlayer.durationMs), fontSize = 10.sp, color = skin.sub,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            song?.title ?: tr("水莲", "WaterLily"),
            fontSize = 21.sp, fontWeight = FontWeight.Bold, color = skin.ink,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            (song?.artist ?: tr("未知歌手", "Unknown artist")) + "  ·  " +
                (currentLine ?: tr("歌词加载中…", "Loading lyrics…")),
            fontSize = 11.sp, color = skin.sub, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clickable { onOpenLyrics() }
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudTextAction(skin, tr("播放列表", "Playlist")) { onOpenPlaylist() }
            Spacer(Modifier.width(18.dp))
            HudTextAction(skin, tr("更多", "More")) { onOpenMoreMenu() }
            Spacer(Modifier.width(18.dp))
            HudTextAction(skin, tr("样式", "Style")) { onSkin() }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 双栏玻璃版式：左封面玻璃卡 + 右信息列 */
@Composable
private fun DuoLayout(
    skin: PlayerSkin,
    song: com.example.my.data.music.LocalSong?,
    art: ImageBitmap?,
    stagePosition: Float,
    currentLine: String?,
    progress: Float,
    onScrub: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    onOpenLyrics: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSkin: () -> Unit,
    onOpenMoreMenu: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp)
    ) {
        StageTopBar(
            page = 1, position = stagePosition, ink = skin.ink, dark = false,
            onBack = onBack, onShare = onShare, onSkin = onSkin, onToggleDark = {}
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .weight(0.44f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(26.dp))
                    .background(skin.chipBg)
                    .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(26.dp))
                    .padding(10.dp)
            ) {
                StageAlbumArt(
                    art,
                    Modifier.fillMaxSize().clickable { onOpenLyrics() },
                    RoundedCornerShape(18.dp)
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(skin.ink.copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        formatMs(MusicPlayer.positionMs),
                        fontSize = 9.sp, color = skin.ink,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Column(Modifier.weight(0.56f).fillMaxHeight()) {
                Text(
                    tr("正在播放", "NOW PLAYING"),
                    fontSize = 9.sp, color = skin.sub, letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    song?.title ?: tr("水莲", "WaterLily"),
                    fontSize = 21.sp, fontWeight = FontWeight.Bold, color = skin.ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 26.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    song?.artist ?: tr("未知歌手", "Unknown artist"),
                    fontSize = 11.sp, color = skin.sub, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(0.32f))
                Text(
                    currentLine ?: tr("歌词加载中…", "Loading lyrics…"),
                    fontSize = 12.sp, color = skin.accentDeep, maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onOpenLyrics() }
                )
                Spacer(Modifier.weight(0.28f))
                StageSeekBar(
                    progress = progress,
                    track = skin.track,
                    fill = skin.accent,
                    thumb = skin.accent,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    onScrub = onScrub,
                    onSeek = {
                        onSeek(it)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMs(MusicPlayer.positionMs), fontSize = 10.sp, color = skin.sub)
                    Text(formatMs(MusicPlayer.durationMs), fontSize = 10.sp, color = skin.sub)
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StageControlButton(tr("收藏", "Favorite"), onToggleFavorite) {
                        AnimatedHeart(MusicPlayer.isFavorite(), onToggleFavorite, inactive = skin.sub)
                    }
                    StageControlButton(tr("上一首", "Previous"), {
                        MusicPlayer.prev()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }) { Icon(Lucide.SkipBack, null, tint = skin.ink, modifier = Modifier.size(22.dp)) }
                    AnimatedPlayButton(
                        playing = MusicPlayer.isPlaying,
                        onClick = {
                            stageTogglePlay()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        size = 50.dp,
                        color = skin.accent
                    )
                    StageControlButton(tr("下一首", "Next"), {
                        MusicPlayer.next()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }) { Icon(Lucide.SkipForward, null, tint = skin.ink, modifier = Modifier.size(22.dp)) }
                    StageControlButton(tr("更多", "More"), onOpenMoreMenu) {
                        Icon(Lucide.EllipsisVertical, null, tint = skin.ink, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudTextAction(skin, tr("歌词", "Lyrics")) { onOpenLyrics() }
            Spacer(Modifier.width(18.dp))
            HudTextAction(skin, tr("播放列表", "Playlist")) { onOpenPlaylist() }
            Spacer(Modifier.width(18.dp))
            HudTextAction(skin, tr("样式", "Style")) { onSkin() }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * 皮肤底：有底图的皮肤（青绿线稿 / 浅蓝影棚 / 霓虹花 / 暖光书房）铺满整页图，
 * 再压一层由皮肤色生成的薄纱，保证白字/墨字在照片上照样读得清；
 * 纯色皮肤就是原来的三段渐变。
 */
@Composable
private fun SkinBackdrop(
    skin: PlayerSkin,
    topBg: Color,
    midBg: Color,
    bottomBg: Color,
    playing: Boolean = false,
    tint: Color? = null,
    art: ImageBitmap? = null
) {
    val img = skin.bgImage
    if (img != null) {
        Image(
            bitmap = ImageBitmap.imageResource(img),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            filterQuality = ImageSampling
        )
        // 薄纱按文字色选：白字压黑纱，墨字压白纱，照片上是蓝光还是暖光都读得清
        val scrim = if (skin.ink.luminance() > 0.5f) Color.Black else Color.White
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        scrim.copy(alpha = 0.10f),
                        scrim.copy(alpha = 0.06f),
                        scrim.copy(alpha = 0.38f),
                        scrim.copy(alpha = 0.68f)
                    )
                )
            )
        )
    } else {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(topBg, midBg, bottomBg))
            )
        ) {
            // 封面放大模糊铺满，化成一层很淡的氛围底色（QQ 音乐式模糊封面背景）；
            // 低版本系统不支持高斯模糊时自动退化为原图淡层，不会崩。
            CoverBlurFill(art)
            // 纯色静态皮肤：叠一层跟随封面取色、缓慢漂移的环境柔光；
            // 动态皮肤已有专属动画层（FLOW / BLOOM …），不再重复叠加。
            if (skin.anim == PlayerAnim.NONE && tint != null) {
                AmbientTintGlow(tint = tint, playing = playing)
            }
        }
    }
    // 动态皮肤：底色之上再叠一层持续动画（呼吸光晕 / 极光流彩 / 银河星尘）
    if (skin.anim != PlayerAnim.NONE) {
        DynamicSkinOverlay(skin = skin, playing = playing)
    }
}

/** 纯色背景上的取色环境柔光：两团光斑沿椭圆缓慢漂移，低 alpha 不压文字。 */
@Composable
private fun AmbientTintGlow(
    tint: Color,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val phase = if (playing) {
        val transition = rememberInfiniteTransition(label = "ambientGlow")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(9000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ambientGlowPhase"
        )
        p
    } else 0.2f
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        repeat(2) { k ->
            val a = phase * (2f * PI).toFloat() + k * PI.toFloat()
            val bx = w * (0.5f + 0.30f * sin(a))
            val by = h * (0.42f + 0.20f * cos(a * 0.8f + k * 1.3f))
            val r = w * (0.55f - 0.08f * k)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(bx, by),
                    radius = r
                ),
                radius = r,
                center = Offset(bx, by)
            )
        }
    }
}

/** 封面模糊背景：放大裁切铺满，高斯模糊后压成一层淡氛围底色；无封面时不画。 */
@Composable
private fun CoverBlurFill(art: ImageBitmap?, modifier: Modifier = Modifier) {
    if (art == null) return
    Image(
        bitmap = art,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer(scaleX = 1.15f, scaleY = 1.15f)
            .blur(20.dp)
            .alpha(0.20f)
    )
}

/**
 * 动态皮肤的动画层。
 *
 * 省电：三个条件同时成立才跑帧 —— 真在播放、页面在前台、屏幕亮着，
 * 再加上用户没关「动态皮肤动画」。暂停/退后台/熄屏时协程直接结束，
 * 一帧都不再请求，画面停在当前帧（静止但有质感），恢复后接着动。
 * 帧率另外压到约 30fps：60/120Hz 屏幕上不再每帧重绘整屏 Canvas。
 */
@Composable
internal fun DynamicSkinOverlay(skin: PlayerSkin, playing: Boolean) {
    val resumed = rememberIsResumed()
    val screenOn = rememberIsScreenOn()
    val animationsOn = PlayerSkinStore.animationsEnabled
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(skin.anim, playing, resumed, screenOn, animationsOn) {
        if (!playing || !resumed || !screenOn || !animationsOn) return@LaunchedEffect
        val from = t
        val fromNs = withFrameNanos { it }
        var lastUpdateNs = fromNs
        while (true) {
            val now = withFrameNanos { it }
            // 不足 33ms 的帧直接跳过：不写状态就不会触发重组和整屏重绘
            if (now - lastUpdateNs < 33_000_000L) continue
            lastUpdateNs = now
            val sec = (now - fromNs) / 1_000_000_000f
            // 取模：长时间播放后 float 精度掉得太狠，动画会开始抖
            t = (from + sec) % 3600f
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        when (skin.anim) {
            PlayerAnim.BLOOM -> {
                // 唱片后面三团光，相位错开地胀缩，像跟着节拍呼吸
                val blobs = listOf(
                    Triple(skin.accent, 0.62f, 0f),
                    Triple(skin.accentDeep, 0.50f, 2.1f),
                    Triple(skin.discMid, 0.42f, 4.2f)
                )
                blobs.forEach { (color, baseR, phase) ->
                    val pulse = 0.5f + 0.5f * sin(t * 1.6f + phase)
                    val r = w * baseR * (0.88f + 0.24f * pulse)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                color.copy(alpha = 0.16f + 0.10f * pulse),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = r
                        ),
                        radius = r,
                        center = Offset(cx, cy)
                    )
                }
            }
            PlayerAnim.FLOW -> {
                // 大团光斑沿椭圆轨道缓慢环绕，颜色不同速度不同，底色像液体在流
                val blobs = listOf(
                    listOf(skin.accent, 0.46f, 5.5f, 0.30f),
                    listOf(skin.accentDeep, 0.40f, 4.4f, 2.6f),
                    listOf(skin.discMid, 0.36f, 3.4f, 5.2f),
                    listOf(skin.discLight, 0.30f, 6.2f, 1.3f)
                )
                blobs.forEach { list ->
                    val color = list[0] as Color
                    val baseR = list[1] as Float
                    val speed = list[2] as Float
                    val phase = list[3] as Float
                    val a = t * speed + phase
                    val bx = cx + sin(a) * w * 0.30f
                    val by = cy + cos(a * 0.8f) * h * 0.22f
                    val r = w * baseR
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(color.copy(alpha = 0.20f), Color.Transparent),
                            center = Offset(bx, by),
                            radius = r
                        ),
                        radius = r,
                        center = Offset(bx, by)
                    )
                }
            }
            PlayerAnim.STARDUST -> {
                // 36 颗星点从下往上飘，亮度按正弦闪烁；位置由序号决定，每帧纯算无需状态
                repeat(36) { i ->
                    val seed = i * 0.6180339f
                    val speed = 0.03f + (i % 5) * 0.012f
                    val progress = ((t * speed + seed) % 1f)
                    val x = w * ((seed * 7.13f) % 1f) + sin(t * 0.7f + i) * w * 0.012f
                    val y = h * (1f - progress)
                    val r = 1.6f + (i % 4) * 1.1f
                    val twinkle = 0.5f + 0.5f * sin(t * 2.2f + i * 1.7f)
                    val color = if (i % 3 == 0) skin.accent else Color.White
                    drawCircle(
                        color = color.copy(alpha = 0.12f + 0.42f * twinkle),
                        radius = r,
                        center = Offset(x, y)
                    )
                }
            }
            PlayerAnim.RIPPLE -> {
                // 三圈波纹先后从中心荡开，越往外越细越淡，像水面上扩散的声波
                val maxR = w * 0.62f
                repeat(3) { i ->
                    val phase = (t * 0.35f + i / 3f) % 1f
                    val r = maxR * (0.15f + 0.85f * phase)
                    drawCircle(
                        color = if (i == 1) skin.accent else skin.discLight,
                        radius = r,
                        center = Offset(cx, cy),
                        alpha = (1f - phase) * 0.30f,
                        style = Stroke(width = 1.5f + 3.5f * (1f - phase))
                    )
                }
                // 中心一团柔光跟着波纹缓慢呼吸，避免中间空一块
                val glowPulse = 0.5f + 0.5f * sin(t * 1.2f)
                val glowR = w * 0.42f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(skin.accent.copy(alpha = 0.10f + 0.10f * glowPulse), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = glowR
                    ),
                    radius = glowR,
                    center = Offset(cx, cy)
                )
            }
            PlayerAnim.AURORA -> {
                // 四道横向光带在画面上半部缓慢起伏，颜色在强调色之间过渡，像极光垂落
                repeat(4) { i ->
                    val phase = i * 1.7f
                    val bandY = h * (0.12f + i * 0.12f) + sin(t * 0.35f + phase) * h * 0.05f
                    val bandX = cx + sin(t * 0.22f + phase * 1.3f) * w * 0.24f
                    val radius = w * (0.60f - i * 0.06f)
                    val color = when (i % 3) {
                        0 -> skin.accent
                        1 -> skin.accentDeep
                        else -> skin.discMid
                    }
                    val strength = 0.10f + 0.06f * (0.5f + 0.5f * sin(t * 0.6f + phase))
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(color.copy(alpha = strength), Color.Transparent),
                            center = Offset(bandX, bandY),
                            radius = radius
                        ),
                        radius = radius,
                        center = Offset(bandX, bandY)
                    )
                }
            }
            PlayerAnim.METEOR -> {
                // 14 颗流星沿对角线掠过：一条渐隐的尾巴 + 头部亮点，划出画面后从顶重新开始
                repeat(14) { i ->
                    val seed = (i * 0.7548777f) % 1f
                    val speed = 0.10f + (i % 4) * 0.035f
                    val p = (t * speed + seed) % 1f
                    val x = w * ((seed * 5.31f) % 1f) + p * w * 0.55f
                    val y = h * (p * 1.15f) - h * 0.10f
                    val len = w * (0.10f + (i % 3) * 0.04f)
                    val alpha = ((1f - p) * 0.55f).coerceIn(0f, 1f)
                    val tailColor = if (i % 3 == 0) skin.accent else skin.discLight
                    drawLine(
                        brush = Brush.linearGradient(
                            listOf(Color.Transparent, tailColor.copy(alpha = alpha)),
                            start = Offset(x - len, y - len * 1.1f),
                            end = Offset(x, y)
                        ),
                        start = Offset(x - len, y - len * 1.1f),
                        end = Offset(x, y),
                        strokeWidth = 1.6f + (i % 3) * 0.5f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = tailColor.copy(alpha = (alpha + 0.15f).coerceAtMost(1f)),
                        radius = 1.4f + (i % 3) * 0.5f,
                        center = Offset(x, y)
                    )
                }
            }
            PlayerAnim.NONE -> {}
        }
    }
}

/**
 * 默认形态（图一）：左封面 + 波形进度，右歌名/歌手 + 歌词 + 时间/控制/收藏。
 * 底色跟歌曲走（皮肤定调 + 封面定色），浅底自动换墨字。
 */
@Composable
private fun LandscapeDefaultContent(
    song: com.example.my.data.music.LocalSong?,
    art: ImageBitmap?,
    lyrics: LyricsResult?,
    onToggleFavorite: () -> Unit
) {
    val skin = PlayerSkinStore.current
    val tint = rememberCoverTint(art)
    val bgs = songStageBg(tint, skin.dark)
    // 底色是浅是深，决定文字用墨还是用白，别让白字掉进浅底里
    // 图片皮肤按皮肤自身的明暗定，纯色皮肤按算出来的底色亮度定
    val lightBg = if (skin.bgImage != null) !skin.dark else bgs[1].luminance() > 0.55f
    val ink = if (lightBg) Color(0xFF181B1F) else Color.White
    val subInk = ink.copy(alpha = 0.58f)
    val accent = if (lightBg) skin.accentDeep else skin.accent
    // 歌名 + 歌词整体挪到右半侧偏右：让出约 15% 屏宽，正好落在参考图红框那一段
    val lyricInset = with(LocalConfiguration.current) { screenWidthDp.dp * 0.15f }

    var scrub by remember { mutableStateOf<Float?>(null) }
    val shown = scrub ?: MusicPlayer.progress
    val timeline = remember(lyrics) { stageTimeline(lyrics) }
    val lineIndex = stageLineIndex(timeline)
    // 歌词区上下滑动跳时间：一行 ≈ 42dp，拖动过程只预览，松手才真的 seek
    val rowPx = with(LocalDensity.current) { 42.dp.toPx() }
    var dragStartLine by remember { mutableStateOf(0) }
    var dragPx by remember { mutableStateOf(0f) }
    var dragTarget by remember { mutableStateOf<Int?>(null) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    val focusIndex = dragTarget ?: lineIndex
    // 拖动歌词时，进度条/波形也跟着走，松手才真正落点
    val visualProgress = seekPreview?.let { ms ->
        if (MusicPlayer.durationMs > 0L) {
            (ms.toFloat() / MusicPlayer.durationMs).coerceIn(0f, 1f)
        } else null
    } ?: shown

    Box(Modifier.fillMaxSize()) {
        SkinBackdrop(skin, bgs[0], bgs[1], bgs[2], playing = MusicPlayer.isPlaying)
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── 左：封面 + 波形进度 ──
            Column(
                Modifier.weight(0.86f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StageAlbumArt(
                    art = art,
                    // 横屏高度有限，封面按高度取比例（0.58 屏高），避免撑破一屏
                    modifier = Modifier.fillMaxHeight(0.58f).aspectRatio(1f),
                    shape = RoundedCornerShape(20.dp)
                )
                Spacer(Modifier.height(12.dp))
                LandscapeWaveform(
                    seed = song?.id ?: 0L,
                    progress = visualProgress,
                    played = accent,
                    rest = ink.copy(alpha = 0.24f)
                )
                StageSeekBar(
                    progress = visualProgress,
                    track = ink.copy(alpha = 0.22f),
                    fill = accent,
                    thumb = accent,
                    onScrub = { scrub = it },
                    onSeek = {
                        scrub = null
                        MusicPlayer.seekTo(it)
                    }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        formatMs((visualProgress * MusicPlayer.durationMs).toLong()),
                        fontSize = 11.sp, color = subInk
                    )
                    Text(formatMs(MusicPlayer.durationMs), fontSize = 11.sp, color = subInk)
                }
            }

            Spacer(Modifier.width(22.dp))

            // ── 右：歌名 — 歌手 + 歌词 + 控制区 ──
            // 整块整体往右让出一段，跟参考图里「歌词离封面更远、贴着右半边」的版式一致
            Column(
                Modifier.weight(1.32f).fillMaxHeight().padding(start = 18.dp)
            ) {
                Text(
                    listOfNotNull(
                        song?.title ?: tr("水莲", "WaterLily"),
                        song?.artist?.takeIf { it.isNotBlank() }
                    ).joinToString(" — "),
                    modifier = Modifier.padding(start = lyricInset, end = 60.dp),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Column(
                    Modifier.weight(1f).fillMaxWidth()
                        .padding(start = lyricInset, end = 60.dp)
                        // 上下滑动歌词 = 跳歌：往下滑回退、往上滑前进，松手才落 seek
                        .pointerInput(timeline) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragStartLine = stageLineIndex(timeline)
                                    dragPx = 0f
                                    if (timeline.isNotEmpty()) {
                                        val t = dragStartLine.coerceIn(0, timeline.lastIndex)
                                        dragTarget = t
                                        seekPreview = timeline[t].timeMs
                                    }
                                },
                                onDragEnd = {
                                    val target = dragTarget
                                    val ms = target?.let { timeline.getOrNull(it)?.timeMs }
                                    if (ms != null && MusicPlayer.durationMs > 0L) {
                                        MusicPlayer.seekTo(
                                            (ms.toFloat() / MusicPlayer.durationMs).coerceIn(0f, 1f)
                                        )
                                    }
                                    dragTarget = null
                                    seekPreview = null
                                    dragPx = 0f
                                },
                                onDragCancel = {
                                    dragTarget = null
                                    seekPreview = null
                                    dragPx = 0f
                                },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    dragPx += amount
                                    if (timeline.isNotEmpty()) {
                                        val steps = (dragPx / rowPx).roundToInt()
                                        val t = (dragStartLine - steps)
                                            .coerceIn(0, timeline.lastIndex)
                                        dragTarget = t
                                        seekPreview = timeline[t].timeMs
                                    }
                                }
                            )
                        },
                    verticalArrangement = Arrangement.Center
                ) {
                    if (timeline.isEmpty()) {
                        Text(
                            tr("歌词加载中…", "Loading lyrics…"),
                            fontSize = 14.sp, color = subInk
                        )
                    } else {
                        // 当前行高亮，上一行最淡，往下逐行变淡；拖动时高亮跟着手指走
                        val from = (focusIndex - 1).coerceAtLeast(0)
                        val to = (focusIndex + 3).coerceAtMost(timeline.lastIndex)
                        for (i in from..to) {
                            val now = i == focusIndex
                            val dist = kotlin.math.abs(i - focusIndex)
                            Text(
                                timeline[i].text,
                                fontSize = if (now) 17.sp else 15.sp,
                                fontWeight = if (now) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    now -> accent
                                    i < focusIndex -> ink.copy(alpha = 0.40f)
                                    dist <= 1 -> ink.copy(alpha = 0.70f)
                                    dist == 2 -> ink.copy(alpha = 0.44f)
                                    else -> ink.copy(alpha = 0.26f)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${formatMs(seekPreview ?: MusicPlayer.positionMs)}/${formatMs(MusicPlayer.durationMs)}",
                        fontSize = 12.sp,
                        color = if (seekPreview != null) accent else subInk,
                        fontWeight = if (seekPreview != null) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(Modifier.weight(1f))
                    LandIconButton(onClick = { MusicPlayer.prev() }) {
                        Icon(Lucide.SkipBack, null, tint = ink, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier.size(50.dp).clip(CircleShape).clickable { MusicPlayer.toggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.size(30.dp)) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            if (MusicPlayer.isPlaying) {
                                drawRoundRect(
                                    color = ink,
                                    topLeft = Offset(c.x - 8.dp.toPx(), c.y - 12.dp.toPx()),
                                    size = Size(5.dp.toPx(), 24.dp.toPx()),
                                    cornerRadius = CornerRadius(2.dp.toPx())
                                )
                                drawRoundRect(
                                    color = ink,
                                    topLeft = Offset(c.x + 3.dp.toPx(), c.y - 12.dp.toPx()),
                                    size = Size(5.dp.toPx(), 24.dp.toPx()),
                                    cornerRadius = CornerRadius(2.dp.toPx())
                                )
                            } else {
                                val p = Path().apply {
                                    moveTo(c.x - 8.dp.toPx(), c.y - 12.dp.toPx())
                                    lineTo(c.x + 13.dp.toPx(), c.y)
                                    lineTo(c.x - 8.dp.toPx(), c.y + 12.dp.toPx())
                                    close()
                                }
                                drawPath(p, ink)
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    LandIconButton(onClick = { MusicPlayer.next() }) {
                        Icon(Lucide.SkipForward, null, tint = ink, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    LandIconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (MusicPlayer.isFavorite()) Icons.Filled.Favorite
                            else Icons.Outlined.FavoriteBorder,
                            contentDescription = tr("收藏", "Save"),
                            tint = if (MusicPlayer.isFavorite()) Color(0xFFF04444) else ink,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 背景视频形态（图二）：全屏铺背景视频/图片，底部一条白框封面 + 歌名/歌手 + 控制 + 红心计数。
 * 信息条是「升起来」的，接在 Crossfade 后面看才顺。
 */
@Composable
private fun LandscapeVideoContent(
    uri: String,
    song: com.example.my.data.music.LocalSong?,
    art: ImageBitmap?,
    lyrics: LyricsResult?,
    onToggleFavorite: () -> Unit
) {
    // 只在「刚进入视频形态」时升起信息条；同一形态里换背景不重复播动画
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        StageMediaBackground(uri, art)

        // 上下压暗：亮画面上白字和按钮也要看得清
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.20f),
                    0.45f to Color.Transparent,
                    0.78f to Color.Black.copy(alpha = 0.42f),
                    1f to Color.Black.copy(alpha = 0.80f)
                )
            )
        )

        // 靠右的透明歌词板：视频形态也要能看词、能滑着跳时间
        AnimatedVisibility(
            visible = revealed,
            enter = fadeIn(tween(320, delayMillis = 260)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 26.dp)
        ) {
            LandscapeVideoLyrics(lyrics = lyrics)
        }

        AnimatedVisibility(
            visible = revealed,
            enter = fadeIn(tween(300, delayMillis = 140)) +
                slideInVertically(tween(460, delayMillis = 140)) { it / 6 },
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(11.dp)).background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.fillMaxSize().padding(1.5.dp).clip(RoundedCornerShape(10.dp))) {
                        if (art != null) {
                            Image(
                                art, null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                filterQuality = ImageSampling
                            )
                        } else {
                            Box(Modifier.fillMaxSize().background(Color(0xFF2A2F38)))
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song?.title ?: tr("水莲", "WaterLily"),
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song?.artist?.takeIf { it.isNotBlank() } ?: tr("未知歌手", "Unknown artist"),
                        fontSize = 11.sp, color = Color.White.copy(alpha = 0.74f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                LandIconButton(onClick = { MusicPlayer.prev() }) {
                    Icon(Lucide.SkipBack, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Box(
                    Modifier.size(46.dp).clip(CircleShape).clickable { MusicPlayer.toggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(30.dp)) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        if (MusicPlayer.isPlaying) {
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(c.x - 8.dp.toPx(), c.y - 12.dp.toPx()),
                                size = Size(5.dp.toPx(), 24.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx())
                            )
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(c.x + 3.dp.toPx(), c.y - 12.dp.toPx()),
                                size = Size(5.dp.toPx(), 24.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx())
                            )
                        } else {
                            val p = Path().apply {
                                moveTo(c.x - 8.dp.toPx(), c.y - 12.dp.toPx())
                                lineTo(c.x + 13.dp.toPx(), c.y)
                                lineTo(c.x - 8.dp.toPx(), c.y + 12.dp.toPx())
                                close()
                            }
                            drawPath(p, Color.White)
                        }
                    }
                }
                LandIconButton(onClick = { MusicPlayer.next() }) {
                    Icon(Lucide.SkipForward, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Box(
                    Modifier.size(32.dp).clip(CircleShape).clickable { onToggleFavorite() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (MusicPlayer.isFavorite()) Icons.Filled.Favorite
                        else Icons.Outlined.FavoriteBorder,
                        contentDescription = tr("收藏", "Save"),
                        tint = if (MusicPlayer.isFavorite()) Color(0xFFF04444)
                        else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    stageLikeText(song?.id),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }
}

/**
 * 视频背景形态的「透明歌词板」：靠右悬浮，半透明底 + 右对齐白字，
 * 同样支持上下滑动跳时间（往上是往后、往下是往前，松手才 seek）。
 */
@Composable
private fun LandscapeVideoLyrics(
    lyrics: LyricsResult?,
    modifier: Modifier = Modifier
) {
    val timeline = remember(lyrics) { stageTimeline(lyrics) }
    if (timeline.isEmpty()) return
    val lineIndex = stageLineIndex(timeline)
    val rowPx = with(LocalDensity.current) { 40.dp.toPx() }
    var dragStartLine by remember { mutableStateOf(0) }
    var dragPx by remember { mutableStateOf(0f) }
    var dragTarget by remember { mutableStateOf<Int?>(null) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    val focus = dragTarget ?: lineIndex

    Column(
        modifier
            .width(300.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.30f),
                        Color.Black.copy(alpha = 0.12f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .pointerInput(timeline) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragStartLine = stageLineIndex(timeline)
                        dragPx = 0f
                        if (timeline.isNotEmpty()) {
                            val t = dragStartLine.coerceIn(0, timeline.lastIndex)
                            dragTarget = t
                            seekPreview = timeline[t].timeMs
                        }
                    },
                    onDragEnd = {
                        val ms = dragTarget?.let { timeline.getOrNull(it)?.timeMs }
                        if (ms != null && MusicPlayer.durationMs > 0L) {
                            MusicPlayer.seekTo(
                                (ms.toFloat() / MusicPlayer.durationMs).coerceIn(0f, 1f)
                            )
                        }
                        dragTarget = null
                        seekPreview = null
                        dragPx = 0f
                    },
                    onDragCancel = {
                        dragTarget = null
                        seekPreview = null
                        dragPx = 0f
                    },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragPx += amount
                        if (timeline.isNotEmpty()) {
                            val steps = (dragPx / rowPx).roundToInt()
                            val t = (dragStartLine - steps).coerceIn(0, timeline.lastIndex)
                            dragTarget = t
                            seekPreview = timeline[t].timeMs
                        }
                    }
                )
            },
        horizontalAlignment = Alignment.End
    ) {
        val from = (focus - 1).coerceAtLeast(0)
        val to = (focus + 2).coerceAtMost(timeline.lastIndex)
        for (i in from..to) {
            val now = i == focus
            Text(
                timeline[i].text,
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                textAlign = TextAlign.End,
                fontSize = if (now) 16.sp else 14.sp,
                fontWeight = if (now) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    now -> Color.White
                    i < focus -> Color.White.copy(alpha = 0.40f)
                    else -> Color.White.copy(alpha = 0.62f)
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // 视频背景亮度不可控，给字加一层阴影，亮画面上也不糊
                style = TextStyle(
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), blurRadius = 14f)
                )
            )
        }
        if (seekPreview != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatMs(seekPreview ?: 0L)} / ${formatMs(MusicPlayer.durationMs)}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun LandIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LandOverlayButton(
    modifier: Modifier = Modifier,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val base = modifier.size(40.dp).clip(CircleShape)
        .background(Color.Black.copy(alpha = 0.36f))
        .border(
            width = if (active) 1.5.dp else 0.dp,
            color = if (active) Color.White else Color.Transparent,
            shape = CircleShape
        )
    Box(
        if (onLongClick == null) {
            base.clickable(interactionSource = interaction, indication = null, onClickLabel = label) {
                onClick()
            }
        } else {
            base.combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = label,
                onLongClickLabel = label,
                onLongClick = onLongClick
            ) { onClick() }
        },
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * 舞台壁纸视频：全项目唯一「静音循环」的装饰性背景，不是内容视频，
 * 因此不套 VideoPhase 的「点一下播一遍」；但它仍必须守生命周期铁律：
 * 退后台暂停、surface 销毁停播、出错不静默吞掉、只在 uri 变化时重建解码器。
 */
@Composable
private fun StageVideoFill(uri: Uri) {
    val ctx = LocalContext.current
    val surface = remember(uri) { SurfaceView(ctx) }
    val resumed = rememberIsResumed()
    val resumedNow by rememberUpdatedState(resumed)
    // 前后台 effect 要能拿到当前那一代解码器，但不能把 resumed 放进 effect 的 key
    val playerRef = remember(uri) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(uri) {
        val mp = MediaPlayer()
        playerRef.value = mp
        var started = false
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                runCatching {
                    mp.setSurface(holder.surface)
                    if (!started) {
                        started = true
                        mp.prepareAsync()
                    }
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                // 表面没了就停播，别让解码器在后台空转
                runCatching {
                    mp.setSurface(null)
                    if (mp.isPlaying) mp.pause()
                }
            }
        }
        runCatching {
            mp.setDataSource(ctx, uri)
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.setOnPreparedListener { p ->
                if (resumedNow) runCatching { p.start() }
            }
            mp.setOnErrorListener { _, _, _ ->
                // 不再静默吞掉：出错就地释放，露出底下的封面
                if (playerRef.value === mp) playerRef.value = null
                runCatching { mp.release() }
                true
            }
            surface.holder.addCallback(callback)
            if (surface.holder.surface.isValid) callback.surfaceCreated(surface.holder)
        }
        onDispose {
            runCatching { surface.holder.removeCallback(callback) }
            playerRef.value = null
            runCatching { mp.release() }
        }
    }

    // 前后台只暂停 / 恢复，绝不因为 resumed 变化重建播放器
    LaunchedEffect(resumed) {
        val mp = playerRef.value ?: return@LaunchedEffect
        if (resumed) {
            if (!mp.isPlaying) runCatching { mp.start() }
        } else if (mp.isPlaying) {
            runCatching { mp.pause() }
        }
    }

    AndroidView(factory = { surface }, modifier = Modifier.fillMaxSize())
}

/** 背景层：视频直接用视频，图片解码后铺满，读不出来再退回封面 */
@Composable
private fun StageMediaBackground(uriString: String, fallback: ImageBitmap?) {
    val ctx = LocalContext.current
    val uri = remember(uriString) { Uri.parse(uriString) }
    val isVideo = remember(uriString) {
        ctx.contentResolver.getType(uri)?.startsWith("video") == true ||
            uriString.endsWith(".mp4", true) || uriString.endsWith(".webm", true) ||
            uriString.endsWith(".mkv", true) || uriString.endsWith(".mov", true)
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (isVideo) {
            StageVideoFill(uri)
        } else {
            val bitmap = remember(uriString) {
                runCatching {
                    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(), null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    filterQuality = ImageSampling
                )
            } else {
                StageCoverFill(fallback)
            }
        }
    }
}

/** 没设自定义背景时：用当前封面铺满整屏（比纯色更像"视频感"） */
@Composable
private fun StageCoverFill(art: ImageBitmap?) {
    if (art != null) {
        Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), filterQuality = ImageSampling)
    } else {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        mixColor(StageOrangeDeep, Color.Black, 0.45f),
                        Color(0xFF14161A)
                    )
                )
            )
        )
    }
}

/** 图一那颗心旁边的数字：这里的"1k+"用播放次数凑出来 */
private fun stageLikeText(id: Long?): String {
    val n = id?.let { MusicPlayer.playCounts[it] ?: 0 } ?: 0
    return if (n >= 1000) "${n / 1000}k+" else "$n+"
}

/** 手电筒：CameraManager.setTorchMode；没有闪光灯或 ROM 拒绝就安静降级 */
internal object TorchLamp {
    private var manager: CameraManager? = null
    private var cameraId: String? = null
    private var resolved = false

    private fun resolve(ctx: Context): Boolean {
        if (resolved) return cameraId != null
        resolved = true
        runCatching {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            manager = cm
            cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }
        return cameraId != null
    }

    fun available(ctx: Context): Boolean = resolve(ctx)

    fun set(ctx: Context, on: Boolean) {
        if (!resolve(ctx)) return
        val id = cameraId ?: return
        runCatching { manager?.setTorchMode(id, on) }
    }
}

/** 音频振幅取样：挂 Visualizer 读 RMS；没权限或 ROM 不支持就让节拍兜底 */
private class BeatMeter {
    private var visualizer: Visualizer? = null

    var level by mutableFloatStateOf(0f)
        private set

    val active: Boolean get() = visualizer != null

    fun start(sessionId: Int) {
        if (sessionId == 0) return
        runCatching {
            val v = Visualizer(sessionId)
            v.captureSize = Visualizer.getMaxCaptureRate() / 4
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (waveform == null || waveform.isEmpty()) return
                        var sum = 0.0
                        for (b in waveform) sum += b.toDouble() * b.toDouble()
                        level = (kotlin.math.sqrt(sum / waveform.size) / 96.0)
                            .toFloat().coerceIn(0f, 1f)
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) = Unit
                },
                Visualizer.getMaxCaptureRate() / 2,
                true,
                false
            )
            v.enabled = true
            visualizer = v
        }
    }

    fun stop() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        level = 0f
    }
}

/** 图一底面那排波形：以歌曲 id 为随机种子，同一首歌永远是同一形状。 */
@Composable
private fun LandscapeWaveform(
    seed: Long,
    progress: Float,
    played: Color,
    rest: Color,
    modifier: Modifier = Modifier
) {
    val bars = remember(seed) {
        val r = Random(seed)
        List(64) { 0.18f + r.nextFloat() * 0.82f }
    }
    Canvas(modifier.fillMaxWidth().height(46.dp)) {
        val n = bars.size
        val gap = 1.6.dp.toPx()
        val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(0.8f)
        val cy = size.height / 2f
        bars.forEachIndexed { i, h ->
            val bh = (h * size.height).coerceIn(3.dp.toPx(), size.height)
            val x = i * (bw + gap)
            drawRoundRect(
                color = if ((i + 0.5f) / n <= progress) played else rest,
                topLeft = Offset(x, cy - bh / 2f),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(bw / 2f)
            )
        }
    }
}

/** 从任意 Context 找 Activity：改屏幕方向只能走 Activity。 */
private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * 主动进横屏（用户自己在「更多」里点了「横屏模式」）。
 *
 * 用 [ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE] 是**显式**方向：这是用户亲手点的动作，
 * 系统开着竖屏锁定也应当生效；而自动跟随重力那条路走 [ScreenRotation.playbackOrientation]，
 * 锁定状态下不会自己转。两者分开，避免「我只是点了横屏」被系统锁吃掉。
 */
private fun forceStageLandscape(ctx: Context) {
    ctx.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
}

/**
 * 回竖屏：先显式锁竖屏。
 *
 * 系统竖屏锁还开着的时侯，只把方向改回 SENSOR 是回不来的（SENSOR 正是被按住的那个），
 * 所以这里先落一个确定的 PORTRAIT；「什么时候把方向还给重力」由调用方用协程去做，
 * 这样离开播放页时那个延迟任务会跟着协程一起取消，不会在首页/发现页把方向无心解开。
 */
private fun lockStagePortrait(ctx: Context) {
    ctx.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}

@Composable
private fun VinylArtwork(
    art: ImageBitmap?,
    playing: Boolean,
    skin: PlayerSkin = PlayerSkin.VINYL,
    onSingleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 唱片只在「真在播放 + 页面在前台」时转。原来用 rememberInfiniteTransition，
    // 暂停时也每帧重组重画整块 Canvas、退到后台还继续跑，纯属耗电；
    // 改成按帧算角度：暂停 / 后台时协程直接结束，一帧都不再请求。
    var shownAngle by remember { mutableFloatStateOf(0f) }
    // 频谱律动：播放时让唱片光晕和强调圈随节拍呼吸（与转圈同一个帧循环取值）
    var beat by remember { mutableFloatStateOf(0f) }
    val vinylResumed = rememberIsResumed()
    LaunchedEffect(playing, vinylResumed) {
        if (!playing || !vinylResumed) return@LaunchedEffect
        val fromAngle = shownAngle
        val fromNs = withFrameNanos { it }
        var lastUpdateNs = fromNs
        while (true) {
            val now = withFrameNanos { it }
            // 唱片本来转得很慢，30fps 足够顺滑；不节流的话 120Hz 屏会白白多画 3 倍
            if (now - lastUpdateNs < 33_000_000L) continue
            lastUpdateNs = now
            val sec = (now - fromNs) / 1_000_000_000.0
            // 15 秒转一圈 → 每秒 24°
            shownAngle = ((fromAngle + sec * 24.0) % 360.0).toFloat()
            beat = (0.5f + 0.5f * (
                0.6f * kotlin.math.sin(sec * 4.4f).toFloat() +
                    0.4f * kotlin.math.sin(sec * 7.3f + 1.1f).toFloat()
                )).coerceIn(0f, 1f)
        }
    }
    val armAngle by animateFloatAsState(
        targetValue = if (playing) 20f else 4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "tonearm"
    )
    // 双击封面 = 收藏/取消收藏，弹一个爱心迸出动画（参考 QQ 音乐 / 网易云）
    val tapScope = rememberCoroutineScope()
    var heartBurst by remember { mutableStateOf(false) }
    Box(
        modifier
            .pointerInput(onSingleTap) {
                detectTapGestures(
                    onTap = { onSingleTap?.invoke() },
                    onDoubleTap = {
                        MusicPlayer.toggleFavorite()
                        heartBurst = true
                        tapScope.launch {
                            delay(680)
                            heartBurst = false
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val d = size.minDimension
            val center = Offset(size.width / 2, size.height / 2)
            val radius = d / 2
            // 幻彩皮肤：唱片外面罩一圈柔和光晕（参考图里白底蓝光、黑底青光那一圈）；
            // 播放时光晕随节拍呼吸（beat 来自上面的帧循环），暂停时静止在中间值
            val breathe = if (playing) 0.75f + 0.35f * beat else 0.9f
            if (skin.glow) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            skin.discLight.copy(alpha = 0.55f * breathe),
                            skin.discMid.copy(alpha = 0.22f * breathe),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * 0.99f
                    ),
                    radius = radius * (0.93f + 0.06f * breathe),
                    center = center
                )
            }
            val discScale = if (skin.glow) 0.80f else 0.92f
            drawCircle(
                color = skin.discRim.copy(alpha = if (skin.glow) 0.55f else 0.45f),
                radius = radius * (discScale + 0.07f),
                center = center
            )
            rotate(shownAngle, pivot = center) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(skin.discLight, skin.discMid, skin.discDark),
                        center = center,
                        radius = radius
                    ),
                    radius = radius * discScale,
                    center = center
                )
                repeat(16) { i ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.055f),
                        radius = radius * discScale * (0.96f - i * 0.038f),
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                drawArc(
                    color = Color.White.copy(alpha = 0.16f),
                    startAngle = -65f,
                    sweepAngle = 95f,
                    useCenter = false,
                    topLeft = Offset(
                        center.x - radius * discScale * 0.91f,
                        center.y - radius * discScale * 0.91f
                    ),
                    size = Size(radius * discScale * 1.82f, radius * discScale * 1.82f),
                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        // 中心专辑封面用约束后的尺寸单独摆，避免依赖 Canvas 像素。
        // 切歌时封面淡入 + 轻微放大过渡（参考 QQ 音乐封面转场），旧封面先淡出。
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val d = minOf(maxWidth, maxHeight)
            AnimatedContent(
                targetState = art,
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(initialScale = 0.94f, animationSpec = tween(300))) togetherWith
                        fadeOut(tween(180))
                },
                label = "coverTransition"
            ) { currentArt ->
                StageAlbumArt(
                    currentArt,
                    Modifier.size(d * 0.43f).clip(CircleShape).border(3.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                    CircleShape
                )
            }
        }
        // 爱心迸出动画：双击收藏时在唱片中心放大淡出
        androidx.compose.animation.AnimatedVisibility(
            visible = heartBurst,
            enter = androidx.compose.animation.scaleIn(
                initialScale = 0.3f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
                )
            ),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(260))
        ) {
            Icon(
                Icons.Filled.Favorite,
                tr("已收藏", "Favorited"),
                tint = Color(0xFFE85A6B),
                modifier = Modifier.size(72.dp)
            )
        }
        // 唱针：播放时压向唱片，暂停时抬起。
        Canvas(Modifier.fillMaxSize()) {
            val pivot = Offset(size.width * 0.86f, size.height * 0.12f)
            rotate(armAngle, pivot = pivot) {
                drawLine(
                    color = skin.discRim,
                    start = pivot,
                    end = Offset(size.width * 0.78f, size.height * 0.55f),
                    strokeWidth = 7.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(skin.discRim, radius = 11.dp.toPx(), center = pivot)
                drawCircle(skin.discMid, radius = 4.dp.toPx(), center = pivot)
                drawCircle(
                    skin.discLight,
                    radius = 9.dp.toPx(),
                    center = Offset(size.width * 0.78f, size.height * 0.55f)
                )
            }
        }
    }
}

@Composable
private fun StageTopBar(
    page: Int,
    position: Float,
    ink: Color,
    dark: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSkin: () -> Unit,
    onToggleDark: () -> Unit
) {
    val topBarContext = LocalContext.current
    Box(
        Modifier.fillMaxWidth().height(52.dp),
        contentAlignment = Alignment.Center
    ) {
        // 左侧返回不再加白色圆底/阴影，直接贴最左边，保持轻量
        Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
            StageIconButton(tr("返回", "Back"), onBack) {
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = tr("返回", "Back"),
                    tint = ink,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        StagePageDots(
            position = position,
            active = ink,
            inactive = ink.copy(alpha = 0.28f),
            modifier = Modifier.align(Alignment.Center)
        )
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (page == 0) {
                StageIconButton(tr("深色歌词", "Dark lyrics"), {
                    onToggleDark()
                }) {
                    Icon(
                        Icons.Outlined.DarkMode,
                        null,
                        tint = if (dark) StageOrange else ink,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            // 一键进横屏：不依赖重力感应，系统锁了竖屏/关了自动旋转也能进
            StageIconButton(tr("横屏播放", "Landscape player"), { forceStageLandscape(topBarContext) }) {
                Icon(Icons.Outlined.ScreenRotation, null, tint = ink, modifier = Modifier.size(19.dp))
            }
            StageIconButton(tr("播放器样式", "Player style"), onSkin) {
                Icon(Icons.Outlined.Checkroom, null, tint = ink, modifier = Modifier.size(20.dp))
            }
            StageIconButton(tr("分享", "Share"), onShare) {
                Icon(Lucide.Share2, null, tint = ink, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun StageCircleButton(
    modifier: Modifier = Modifier,
    bg: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier.size(42.dp)
            .shadow(8.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(bg)
            .pressScale(interaction, 0.92f)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun StageIconButton(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(36.dp).clip(CircleShape)
            .pressScale(interaction, 0.9f)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = label) { onClick() },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun StagePageDots(
    position: Float,
    active: Color,
    inactive: Color,
    modifier: Modifier = Modifier
) {
    // 三个固定灰点 + 一个 14dp 的活动胶囊：胶囊按 Pager 连续位置平移，
    // 拖动时不是到页才切换，而是跟着手指在两点之间滑过去。
    val p = position.coerceIn(0f, 2f)
    Box(modifier.width(36.dp).height(3.dp)) {
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) {
                Box(
                    Modifier.width(6.dp).height(3.dp)
                        .clip(CircleShape)
                        .background(inactive)
                )
            }
        }
        Box(
            Modifier.offset(x = (p * 11f).dp)
                .width(14.dp).height(3.dp)
                .clip(CircleShape)
                .background(active)
        )
    }
}

@Composable
internal fun StageAlbumArt(
    art: ImageBitmap?,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp)
) {
    Box(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(StageOrange, Color(0xFFFFC27C)))),
        contentAlignment = Alignment.Center
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                filterQuality = ImageSampling
            )
        } else {
            Icon(
                Icons.Outlined.MusicNote,
                null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxSize(0.34f)
            )
        }
    }
}

@Composable
private fun StageSeekBar(
    progress: Float,
    track: Color,
    fill: Color,
    thumb: Color,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    onScrub: (Float?) -> Unit,
    onSeek: (Float) -> Unit
) {
    var dragValue by remember { mutableFloatStateOf(progress) }
    var dragging by remember { mutableStateOf(false) }
    // 已播段流光：仅在播放时跑一条循环相位；暂停时静止在中段，不空转。
    val sweepPhase = if (playing) {
        val transition = rememberInfiniteTransition(label = "seekSweep")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "seekSweepPhase"
        )
        p
    } else 0.5f
    Canvas(
        modifier.fillMaxWidth().height(30.dp)
            .pointerInput(Unit) {
                // 单手势同时覆盖「点一下跳转」和「按住拖动」，避免两个 detector
                // 竞争时拖完又被 tap 二次 seek（进度条回跳的根因）。
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    dragging = true
                    dragValue = (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onScrub(dragValue)
                    var change = down
                    while (change.pressed) {
                        val event = awaitPointerEvent()
                        change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            dragValue = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onScrub(dragValue)
                            change.consume()
                        }
                    }
                    dragging = false
                    onSeek(dragValue)
                    onScrub(null)
                }
            }
    ) {
        val cy = size.height / 2f
        val bar = 4.dp.toPx()
        val radius = 7.dp.toPx()
        val f = progress.coerceIn(0f, 1f)
        // 轨道两端各留出滑块半径，0% / 100% 时滑块也完整可见、可点。
        val usable = (size.width - radius * 2f).coerceAtLeast(1f)
        val startX = radius
        drawRoundRect(
            color = track,
            topLeft = Offset(startX, cy - bar / 2f),
            size = Size(usable, bar),
            cornerRadius = CornerRadius(bar / 2f)
        )
        if (f > 0f) {
            drawRoundRect(
                color = fill,
                topLeft = Offset(startX, cy - bar / 2f),
                size = Size(usable * f, bar),
                cornerRadius = CornerRadius(bar / 2f)
            )
            // 已播段一条缓慢扫过的柔高光，随相位在已播区内移动
            val bandX = startX + usable * f * sweepPhase
            val bandW = (usable * 0.16f).coerceAtLeast(12f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.30f),
                        Color.Transparent
                    ),
                    startX = bandX - bandW,
                    endX = bandX + bandW
                ),
                topLeft = Offset((bandX - bandW).coerceAtLeast(startX), cy - bar / 2f),
                size = Size((bandW * 2f).coerceAtMost(usable * f), bar)
            )
        }
        val thumbCenter = Offset(startX + usable * f, cy)
        val thumbR = radius * if (dragging) 1.3f else 1f
        // 手柄外圈柔光晕，拖拽时更明显
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(thumb.copy(alpha = if (dragging) 0.34f else 0.22f), Color.Transparent),
                center = thumbCenter,
                radius = thumbR * 2.4f
            ),
            radius = thumbR * 2.4f,
            center = thumbCenter
        )
        drawCircle(color = thumb, radius = thumbR, center = thumbCenter)
    }
}

@Composable
private fun AnimatedPlayButton(
    playing: Boolean,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    color: Color,
    iconColor: Color = Color.White
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "playScale"
    )
    val morph by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "playMorph"
    )
    // 播放时外圈柔光缓慢呼吸；暂停时收弱静止，不空转。
    val glowPulse = if (playing) {
        val transition = rememberInfiniteTransition(label = "playGlow")
        val g by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "playGlowPulse"
        )
        g
    } else 0.5f
    Box(
        Modifier.size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        // 外圈呼吸光晕：比按钮略大一圈，随脉动胀缩、明暗起伏
        Canvas(Modifier.fillMaxSize()) {
            val canvasSize = this.size
            val gc = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
            val baseR = minOf(canvasSize.width, canvasSize.height) / 2f
            val glowR = baseR * (1.18f + 0.10f * glowPulse)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = (0.20f + 0.12f * glowPulse) * if (playing) 1f else 0.5f),
                        Color.Transparent
                    ),
                    center = gc,
                    radius = glowR
                ),
                radius = glowR,
                center = gc
            )
        }
        Box(
            Modifier.clip(CircleShape)
                .background(color)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                val playAlpha = (1f - morph * 1.6f).coerceIn(0f, 1f)
                val pauseAlpha = ((morph - 0.35f) / 0.65f).coerceIn(0f, 1f)
                if (playAlpha > 0f) {
                    val p = Path().apply {
                        moveTo(c.x - 8.dp.toPx(), c.y - 11.dp.toPx())
                        lineTo(c.x + 12.dp.toPx(), c.y)
                        lineTo(c.x - 8.dp.toPx(), c.y + 11.dp.toPx())
                        close()
                    }
                    drawPath(p, iconColor.copy(alpha = playAlpha))
                }
                if (pauseAlpha > 0f) {
                    val w = 5.dp.toPx()
                    val h = 22.dp.toPx()
                    val gap = 5.dp.toPx()
                    drawRoundRect(
                        iconColor.copy(alpha = pauseAlpha),
                        topLeft = Offset(c.x - gap / 2f - w, c.y - h / 2f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                    drawRoundRect(
                        iconColor.copy(alpha = pauseAlpha),
                        topLeft = Offset(c.x + gap / 2f, c.y - h / 2f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedHeart(
    favorite: Boolean,
    onClick: () -> Unit,
    inactive: Color
) {
    val scale by animateFloatAsState(
        targetValue = if (favorite) 1.14f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "heartScale"
    )
    Icon(
        imageVector = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
        contentDescription = tr("收藏", "Favorite"),
        tint = if (favorite) StageRed else inactive,
        modifier = Modifier.size(21.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable { onClick() }
    )
}

@Composable
private fun StageControlButton(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(46.dp).clip(CircleShape)
            .pressScale(interaction, 0.9f)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = label) { onClick() },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun PlayModeIcon(ink: Color, accent: Color) {
    when (MusicPlayer.mode) {
        PlayMode.SHUFFLE -> Icon(
            Lucide.Shuffle, null,
            tint = ink, modifier = Modifier.size(23.dp)
        )
        // 列表循环：高亮循环图标，和「顺序播放（播完停止）」一眼可分。
        PlayMode.LOOP_ALL -> Icon(
            Lucide.Repeat2, null,
            tint = accent, modifier = Modifier.size(23.dp)
        )
        PlayMode.LOOP_ONE -> Box(contentAlignment = Alignment.Center) {
            Icon(Lucide.Repeat2, null, tint = accent, modifier = Modifier.size(23.dp))
            Text("1", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        PlayMode.ORDER -> Icon(
            Lucide.Repeat2, null,
            tint = ink.copy(alpha = 0.55f), modifier = Modifier.size(23.dp)
        )
    }
}

@Composable
private fun StageChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    skin: PlayerSkin = PlayerSkin.VINYL
) {
    Row(
        Modifier.clip(RoundedCornerShape(7.dp))
            .background(skin.chipBg)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = skin.sub, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = 9.sp, color = skin.sub, maxLines = 1)
    }
}

@Composable
private fun VinylAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    skin: PlayerSkin = PlayerSkin.VINYL,
    onClick: () -> Unit
) {
    val press = remember { MutableInteractionSource() }
    Column(
        Modifier.width(46.dp).clip(RoundedCornerShape(9.dp))
            .pressScale(press, 0.90f)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = skin.sub, modifier = Modifier.size(20.dp))
        if (label.isNotBlank()) {
            Text(label, fontSize = 8.sp, color = skin.sub, maxLines = 1)
        }
    }
}

@Composable
private fun StagePill(
    text: String,
    bg: Color,
    fg: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        Modifier.clip(RoundedCornerShape(18.dp)).background(bg).clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text, fontSize = 10.sp, color = fg, maxLines = 1)
    }
}
/**
 * 打字机效果：歌曲故事就绪后一个字一个字冒出来。
 *
 * 详情页常驻在分页容器里（[active] 由 `stagePager.currentPage == 2` 驱动），
 * 只靠 remember 不会随进入而重置，所以用 active 从 false 变 true 当作「重播信号」；
 * 换歌（restart 变化）或点「重新回答」（文字变化）也会从头开始。
 */
@Composable
private fun TypewriterText(
    text: String,
    active: Boolean,
    restart: Any?,
    modifier: Modifier = Modifier,
    color: Color = StageDetailSub
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(text, active, restart) {
        progress.snapTo(0f)
        if (!active || text.isEmpty()) return@LaunchedEffect
        // 每字约 26ms；长故事封顶 6s、短句至少 1.1s，保证始终看得出是逐字出现
        val duration = (text.length * 26).coerceIn(1100, 6000)
        progress.animateTo(1f, tween(durationMillis = duration, easing = LinearEasing))
    }
    // 未激活时直接显示全文：滑走的那一刻文字不会凭空消失；再次进入再从 0 重打
    val shown = if (!active) text.length
    else (progress.value * text.length).toInt().coerceIn(0, text.length)
    Text(
        text = text.take(shown),
        fontSize = 13.sp,
        lineHeight = 21.sp,
        color = color,
        modifier = modifier
    )
}

/** 图二：歌曲详情页。保留 QQ 音乐的标题、信息行、胶囊标签、音效卡、歌曲故事和勋章区节奏。 */
@Composable
private fun SongDetailStage(
    state: AppState,
    active: Boolean,
    stagePosition: Float,
    song: LocalSong?,
    art: ImageBitmap?,
    lyrics: LyricsResult?,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSkin: () -> Unit,
    onCycleEq: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSongAction: (LocalSong) -> Unit = {}
) {
    val ctx = LocalContext.current
    val title = song?.title ?: tr("水莲", "WaterLily")
    val artist = song?.artist ?: tr("未知歌手", "Unknown artist")
    val album = song?.album?.takeIf { it.isNotBlank() && it != "网页缓存" }
        ?: tr("未知专辑", "Unknown album")
    val eq = stageEqLabel()
    val currentLine = stageCurrentLine(lyrics)
    val story = stageStory(song, lyrics)
    val storyCacheKey = SongStoryCache.key(song?.id, title, artist)
    val cachedStory = remember(storyCacheKey) { SongStoryCache.get(ctx, storyCacheKey) }
    var aiStory by remember(storyCacheKey) { mutableStateOf(cachedStory?.story) }
    var aiSources by remember(storyCacheKey) {
        mutableStateOf(cachedStory?.sources.orEmpty())
    }
    var aiModel by remember(storyCacheKey) { mutableStateOf(cachedStory?.model.orEmpty()) }
    var showSongStoryModelPicker by remember { mutableStateOf(false) }
    var aiLoading by remember(storyCacheKey) { mutableStateOf(false) }
    var storyFromWeb by remember(storyCacheKey) {
        // 缓存里有网页来源的是旧「联网整理」结果；没有来源的是模型直答。
        mutableStateOf(cachedStory?.sources?.isNotEmpty() == true)
    }
    var storyWebOnly by remember(storyCacheKey) { mutableStateOf(false) }
    var storyNoWeb by remember(storyCacheKey) { mutableStateOf(false) }
    var sourcesOpen by remember(storyCacheKey) { mutableStateOf(false) }
    var storyRefresh by remember(storyCacheKey) { mutableStateOf(0) }
    val storyEndpoint = state.aiFeatureEndpoint(AiFeature.SONG_STORY)
    val currentModelLabel = "${storyEndpoint.name} · ${storyEndpoint.model}"

    // 歌曲故事：先让当前模型凭已有知识直接写；只有它明确表示不了解这首歌（常见于新歌、
    // 翻唱、同名歌曲）时，才联网检索创作背景补资料。命中缓存就直接复用。
    LaunchedEffect(active, storyCacheKey, storyRefresh) {
        if (!active || song?.title.isNullOrBlank()) {
            if (!active) aiLoading = false
            return@LaunchedEffect
        }
        if (aiStory != null) return@LaunchedEffect
        aiLoading = true
        aiStory = null
        aiSources = emptyList()
        storyFromWeb = false
        storyWebOnly = false
        storyNoWeb = false

        val useLocal = state.aiFeatureUsesLocal(AiFeature.SONG_STORY)
        val localModelId = state.aiFeatureLocalModelId(AiFeature.SONG_STORY)
        // 只放少量歌词帮模型认歌；没有歌词就只靠歌名 + 歌手判断
        val lyricsSnippet = lyrics?.lines
            ?.map { it.text.trim() }
            ?.filter { it.isNotBlank() }
            ?.take(12)
            ?.joinToString("\n")
            ?.takeIf { it.isNotBlank() }
            ?: lyrics?.plain?.trim()?.take(300)
        val directPrompt = SongStoryDirect.buildPrompt(title, artist, lyricsSnippet)

        // ① 当前模型直接生成。模型认识这首歌时，这里结束，整个流程不发起任何联网搜索。
        var directModel = currentModelLabel
        var directAnswer: String? = null
        if (useLocal) {
            directAnswer = runCatching {
                LocalLlm.chatOnce(ctx, directPrompt, localModelId)
            }.getOrNull()
            if (SongStoryDirect.parse(directAnswer) != null) {
                directModel = "本地 · ${LocalLlm.activeSpec(ctx).display}"
            }
        }
        val directStory = SongStoryDirect.parse(directAnswer)
            ?: run {
                // 本地模型不认识时再让云端模型凭知识答一次；仍然不认识才联网。
                // 云端也失败则返回 null，交给下面的网页兜底。
                directModel = currentModelLabel
                val answer = runCatching {
                    AiApi.requestOnce(
                        storyEndpoint,
                        "你是熟悉华语流行音乐的音乐编辑。不确定是哪首歌时必须输出 UNKNOWN，绝不编造。",
                        directPrompt
                    )
                }.getOrNull()
                SongStoryDirect.parse(answer)
            }
        if (directStory != null) {
            aiStory = directStory
            aiSources = emptyList()
            aiModel = directModel
            storyFromWeb = false
            storyWebOnly = false
            storyNoWeb = false
            SongStoryCache.put(ctx, storyCacheKey, directStory, emptyList(), directModel)
            aiLoading = false
            return@LaunchedEffect
        }

        // ② 模型不认识：进入原来的联网检索 + 依据校验流程。
        storyFromWeb = true
        // 按用户要求搜「歌名 + 歌手 + 创作背景」。如果 Bing 把长查询带偏、命中为空，
        // 再依次退回「歌名 + 歌手」「只搜歌名」，但最终展示的来源只会是正文真正引用到的。
        val songQuery = SongStoryQuery.build(title, song?.artist)
        var hits = runCatching {
            WebSearch.search(songQuery, limit = 8)
        }.getOrDefault(emptyList())
        if (hits.isEmpty()) {
            val artistQuery = title + " " + (song?.artist ?: "").trim()
            hits = runCatching {
                WebSearch.search(artistQuery.trim(), limit = 8)
            }.getOrDefault(emptyList())
        }
        if (hits.isEmpty() && songQuery != title) {
            hits = runCatching {
                WebSearch.search(title, limit = 8)
            }.getOrDefault(emptyList())
        }
        if (hits.isEmpty()) {
            // 模型不认识，联网也没查到：明确说明，不拿本地模板冒充歌曲故事。
            aiStory = tr(
                "模型不熟悉这首歌，联网也没有检索到足够可靠的公开资料，暂不生成创作背景，避免编造。",
                "The model doesn't know this song and no reliable public sources were found; no story generated to avoid making things up."
            )
            storyNoWeb = true
            aiLoading = false
            return@LaunchedEffect
        }
        val cloudMaterials = hits.take(5).mapIndexed { i, hit ->
            SongStoryGrounding.Material(i + 1, hit.title, hit.site, hit.snippet, hit.url)
        }
        val localMaterials = cloudMaterials.take(3)
        var grounded: SongStoryGrounding.Grounded? = null
        var modelLabel = currentModelLabel
        if (useLocal) {
            // 小模型上下文小：只给 3 条、每条 150 字，并要求 100-160 字；
            // 端侧没跑出「可核验引用」就回退云端，绝不让无依据的答案挂来源展示。
            val localAnswer = runCatching {
                LocalLlm.chatOnce(
                    ctx,
                    SongStoryGrounding.buildPrompt(title, artist, localMaterials, local = true),
                    localModelId
                )
            }.getOrNull()
            grounded = SongStoryGrounding.parse(localAnswer, localMaterials)
            if (grounded != null) {
                modelLabel = "本地 · ${LocalLlm.activeSpec(ctx).display}"
            }
        }
        if (grounded == null) {
            val answer = runCatching {
                AiApi.requestOnce(
                    storyEndpoint,
                    "你是音乐资料编辑，只依据用户提供的网页摘录写简介，不补充摘录之外的事实。",
                    SongStoryGrounding.buildPrompt(title, artist, cloudMaterials, local = false)
                )
            }.getOrNull()
            grounded = SongStoryGrounding.parse(answer, cloudMaterials)
            if (grounded != null) modelLabel = currentModelLabel
        }
        if (grounded != null) {
            aiStory = grounded.story
            // 只展示正文真正引用到的网页，而不是把搜到的 5 条全挂上去。
            aiSources = grounded.cited.mapNotNull { n -> hits.getOrNull(n - 1) }
            aiModel = modelLabel
            SongStoryCache.put(ctx, storyCacheKey, grounded.story, aiSources, modelLabel)
        } else {
            // 网页已经命中了，只是这次模型调用失败/没给出可核验引用：
            // 用确实提到歌名或歌手的网页摘要兜底，并只挂这几条摘要的来源。
            val picked = webStoryHits(hits, title, artist)
            val webSummary = webStoryFromHits(picked, title, artist)
            if (webSummary.isNotBlank()) {
                aiStory = webSummary
                aiSources = picked.take(2)
                storyWebOnly = true
            } else {
                aiStory = tr(
                    "模型不熟悉这首歌，已检索到网页，但没有可核实的创作背景信息，暂不生成故事。",
                    "The model doesn't know this song; web pages were found but none had verifiable background details."
                )
                storyNoWeb = true
            }
        }
        aiLoading = false
    }
    val tint = rememberCoverTint(art)

    Column(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to mixColor(StageDetailBg, tint, 0.10f),
                    0.30f to StageDetailBg,
                    0.72f to StageDetailBg,
                    1f to mixColor(Color(0xFFF7E6D8), tint, 0.16f)
                )
            )
            .navigationBarsPadding()
    ) {
        StageTopBar(
            page = 2, position = stagePosition, ink = StageDetailInk, dark = false,
            onBack = onBack, onShare = onShare, onSkin = onSkin,
            onToggleDark = {}
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.50f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    StageAlbumArt(art, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("正在播放 · 本地音频", "Now playing · local audio"),
                        fontSize = 10.sp, color = StageOrangeDeep,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        album,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = StageDetailInk,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${formatMs(MusicPlayer.durationMs)} · $artist",
                        fontSize = 11.sp, color = StageDetailSub,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(tint.copy(alpha = 0.18f))
                        .clickable { onCycleEq() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Lucide.AudioLines, null, tint = StageDetailInk, modifier = Modifier.size(19.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                fontSize = 34.sp, fontWeight = FontWeight.Bold, color = StageDetailInk,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { song?.let(onSongAction) }
                )
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("本地歌曲 · ${formatMs(MusicPlayer.durationMs)}", "Local track · ${formatMs(MusicPlayer.durationMs)}"),
                    fontSize = 12.sp, color = StageDetailSub
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    tr("歌曲详情 >", "Song details >"),
                    fontSize = 12.sp, color = StageDetailSub,
                    modifier = Modifier.clickable {
                        stageToast(ctx, tr("当前为本地歌曲详情", "Showing local song details"))
                    }
                )
            }
            Spacer(Modifier.height(16.dp))
            DetailInfoRow(
                label = tr("歌手", "Singer"),
                value = artist,
                avatar = { StageAlbumArt(art, Modifier.fillMaxSize(), CircleShape) },
                onLongClick = { song?.let(onSongAction) }
            )
            Spacer(Modifier.height(8.dp))
            DetailInfoRow(
                label = tr("专辑", "Album"),
                value = album,
                avatar = { StageAlbumArt(art, Modifier.fillMaxSize()) }
            )
            Spacer(Modifier.height(8.dp))
            DetailInfoRow(
                label = tr("制作", "Credits"),
                value = tr("本地音频文件 · 标签信息", "Local audio file · tag metadata"),
                avatar = {
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape).background(StageDetailCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.MusicNote, null, tint = StageDetailSub, modifier = Modifier.size(20.dp))
                    }
                }
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                StageChip(formatMs(MusicPlayer.durationMs), Icons.Outlined.Schedule)
                StageChip(tr("本地音乐", "Local"), Icons.Outlined.Folder)
                StageChip(eq, Lucide.AudioLines)
                StageChip(
                    if (MusicPlayer.isPlaying) tr("正在播放", "Playing") else tr("已暂停", "Paused"),
                    Icons.Outlined.Headphones
                )
                StageChip(stageModeLabel(), Lucide.Repeat2)
                StageChip(
                    if (lyrics != null && lyrics.hasContent) tr("已匹配歌词", "Lyrics ready")
                    else tr("歌词待匹配", "No lyrics yet"),
                    Icons.Outlined.Language
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("歌手音效", "Artist sound"),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StageDetailInk
                )
                Spacer(Modifier.width(6.dp))
                Icon(Lucide.AudioLines, null, tint = StageDetailSub, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.58f))
                    .padding(11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(46.dp).clip(CircleShape)) {
                    StageAlbumArt(art, Modifier.fillMaxSize(), CircleShape)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("沉浸式聆听 $artist 原声", "Immerse in $artist's original sound"),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StageDetailInk,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(tr("当前音效：$eq · 可切换", "Current preset: $eq · tap to change"), fontSize = 10.sp, color = StageDetailSub)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(StageDetailInk)
                        .clickable { onCycleEq() }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(tr("去使用", "Use"), fontSize = 12.sp, color = Color.White, maxLines = 1)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("《$title》的歌曲故事", "The story behind \"$title\""),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StageDetailInk,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.62f))
                        .clickable(enabled = !aiLoading) {
                            // 手动重答：丢掉这首歌的旧缓存，重新联网 + 重新调模型
                            SongStoryCache.remove(ctx, storyCacheKey)
                            aiStory = null
                            aiSources = emptyList()
                            aiModel = ""
                            storyFromWeb = false
                            storyWebOnly = false
                            storyNoWeb = false
                            sourcesOpen = false
                            storyRefresh++
                        }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tr("重新回答", "Regenerate"),
                        fontSize = 10.sp,
                        color = if (aiLoading) StageDetailSub.copy(alpha = 0.5f) else StageDetailSub
                    )
                }
                Spacer(Modifier.width(6.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.62f))
                        .clickable {
                            // 和主 AI 助手共用同一套「联网 + 模型」链路：先打开联网开关，
                            // 再把问题带进完整助手，由它检索网页、生成回答并挂参考来源。
                            WebSearchPrefs.setEnabled(ctx, true)
                            state.askAi(
                                "请联网介绍《$title》$artist 这首歌的创作背景、歌词含义和相关故事，" +
                                    "并给出参考来源。"
                            )
                        }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = StageOrangeDeep, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr("问 AI", "Ask AI"), fontSize = 10.sp, color = StageDetailSub)
                }
            }
            Spacer(Modifier.height(7.dp))
            when {
                aiLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 1.5.dp,
                        color = StageOrangeDeep
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (storyFromWeb) {
                            tr(
                                "模型不熟悉这首歌，正在联网查资料…",
                                "The model doesn't know this song; searching the web..."
                            )
                        } else {
                            tr("正在生成歌曲故事…", "Generating the song story...")
                        },
                        fontSize = 12.sp, color = StageDetailSub
                    )
                }
                !aiStory.isNullOrBlank() -> {
                    TypewriterText(
                        text = aiStory!!,
                        active = active,
                        restart = "$storyCacheKey#$storyRefresh"
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            storyNoWeb -> tr(
                                "模型不熟悉这首歌，且未检索到可核实的网页资料",
                                "The model doesn't know this song and no verifiable web sources were found"
                            )
                            storyWebOnly -> tr(
                                "模型不熟悉这首歌 · 已联网检索，以上为网页摘要",
                                "The model doesn't know this song · web results shown as excerpts"
                            )
                            storyFromWeb -> tr(
                                "模型不熟悉这首歌 · AI 联网整理 · ${aiModel.ifBlank { currentModelLabel }}",
                                "The model doesn't know this song · AI + web · ${aiModel.ifBlank { currentModelLabel }}"
                            )
                            else -> tr(
                                "AI 直接生成 · ${aiModel.ifBlank { currentModelLabel }}",
                                "AI generated · ${aiModel.ifBlank { currentModelLabel }}"
                            )
                        },
                        fontSize = 9.sp, color = StageOrangeDeep.copy(alpha = 0.8f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = { showSongStoryModelPicker = true })
                            }
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                    )
                }
                else -> {
                    TypewriterText(
                        text = story,
                        active = active,
                        restart = storyCacheKey
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr("联网资料不足，以上为本地简介", "Limited web results · local summary"),
                        fontSize = 9.sp, color = StageDetailSub.copy(alpha = 0.65f)
                    )
                }
            }
            if (aiSources.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { sourcesOpen = !sourcesOpen }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tr("参考来源 · ${aiSources.size} 篇网页", "Sources · ${aiSources.size} pages"),
                        fontSize = 10.sp, color = StageOrangeDeep
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (sourcesOpen) tr("收起", "Collapse") else tr("展开", "Expand"),
                        fontSize = 10.sp, color = StageDetailSub
                    )
                }
                if (sourcesOpen) {
                    aiSources.forEach { hit ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = hit.url.startsWith("http")) {
                                    state.openUrl(hit.url)
                                }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "· ${hit.site.ifBlank { tr("网页", "Web") }}：${hit.title}",
                                fontSize = 10.sp, color = StageDetailSub,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            if (!currentLine.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("当前歌词：$currentLine", "Now: $currentLine"),
                    fontSize = 11.sp, color = StageOrangeDeep,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("歌曲档案", "Song archive"),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StageDetailInk,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    tr("全部 >", "All >"),
                    fontSize = 11.sp, color = StageDetailSub,
                    modifier = Modifier.clickable {
                        stageToast(ctx, tr("当前只有这一首歌曲的档案", "Only this song's archive is available"))
                    }
                )
            }
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArchiveBadge(
                    icon = Icons.Outlined.LibraryMusic,
                    title = tr("本地曲库", "Library"),
                    sub = tr("已收录", "Saved"),
                    colors = listOf(Color(0xFFF6E4D5), Color(0xFFEAD2BF))
                )
                ArchiveBadge(
                    icon = Icons.Outlined.Lyrics,
                    title = tr("歌词", "Lyrics"),
                    sub = if (lyrics != null && lyrics.hasContent) tr("已匹配", "Matched") else tr("待匹配", "Pending"),
                    colors = listOf(Color(0xFFE2ECF7), Color(0xFFD3E0EE))
                )
                ArchiveBadge(
                    icon = Icons.Outlined.WorkspacePremium,
                    title = tr("音效", "Sound"),
                    sub = eq,
                    colors = listOf(Color(0xFFF7E7D7), Color(0xFFF1D3B8))
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showSongStoryModelPicker) {
        AiModelQuickSwitchDialog(
            feature = AiFeature.SONG_STORY,
            state = state,
            onDismiss = { showSongStoryModelPicker = false }
        )
    }
}

@Composable
private fun DetailInfoRow(
    label: String,
    value: String,
    avatar: @Composable () -> Unit,
    trailing: String? = null,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.52f))
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)), contentAlignment = Alignment.Center) {
            avatar()
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = StageDetailSub)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = StageDetailInk,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { onLongClick?.invoke() }
                )
            )
        }
        if (trailing != null) {
            Text(trailing, fontSize = 12.sp, color = StageDetailSub)
        }
    }
}

@Composable
private fun RowScope.ArchiveBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    sub: String,
    colors: List<Color>
) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
            .background(Brush.linearGradient(colors))
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.52f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = StageDetailInk, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StageDetailInk, maxLines = 1)
        Spacer(Modifier.height(1.dp))
        Text(sub, fontSize = 8.sp, color = StageDetailSub, maxLines = 1)
    }
}

private fun stageCurrentLine(lyrics: LyricsResult?): String? {
    val timeline = lyrics?.lines?.takeIf { it.isNotEmpty() }
        ?: lyrics?.plain?.let { LyricsRepository.estimateLines(it, MusicPlayer.durationMs) }
        ?: return null
    return timeline.lastOrNull { it.timeMs <= MusicPlayer.positionMs }
        ?.text?.takeIf { it.isNotBlank() }
        ?: timeline.firstOrNull()?.text?.takeIf { it.isNotBlank() }
}

private fun stageStory(song: LocalSong?, lyrics: LyricsResult?): String {
    val title = song?.title ?: "这首歌"
    val artist = song?.artist?.takeIf { it.isNotBlank() } ?: "未知歌手"
    val album = song?.album?.takeIf { it.isNotBlank() && it != "网页缓存" }
    val firstLine = lyrics?.lines?.firstOrNull()?.text?.takeIf { it.isNotBlank() }
        ?: lyrics?.plain?.lines()?.firstOrNull { it.isNotBlank() }?.trim()
    return buildString {
        append("《$title》")
        if (album != null) append("收录在专辑《$album》")
        append("，由 $artist 演唱。")
        if (!firstLine.isNullOrBlank()) append("歌词从「$firstLine」开始。")
        append("你可以在黑胶页控制播放与进度，在歌词页逐行跟唱，也可以在详情页查看歌曲档案。")
    }
}

/**
 * 模型不可用时的网页摘要兜底：只取确实提到歌名/歌手的摘要，避免把站点导航、
 * 版权声明这类噪声拼进歌曲故事。最多两段，防止卡片被撑得过长。
 */
private fun webStoryHits(hits: List<WebHit>, title: String, artist: String): List<WebHit> =
    hits.asSequence()
        .map { it to it.snippet.replace(Regex("\\s+"), " ").trim() }
        .filter { (_, snippet) -> snippet.length >= 20 }
        .filter { (_, snippet) -> snippet.contains(title) || snippet.contains(artist) }
        .map { it.first }
        .distinctBy { it.url }
        .toList()

private fun webStoryFromHits(picked: List<WebHit>, title: String, artist: String): String {
    val snippets = picked.asSequence()
        .map { it.snippet.replace(Regex("\\s+"), " ").trim() }
        .filter { it.length >= 20 }
        .distinct()
        .take(2)
        .toList()
    if (snippets.isEmpty()) return ""
    val text = snippets.joinToString(" ") { it.take(120) }
        .replace("。。", "。")
        .trim()
    return if (text.endsWith("。") || text.endsWith("！") || text.endsWith("？")) text
    else "$text。"
}

internal fun stageEqLabel(): String {
    // 音效库打开时，界面上就该显示用户选的那套（而不是老的六档预设名）
    if (MusicPlayer.soundFxOn) {
        MusicPlayer.currentSoundFx()?.let { return it.zh }
        return "智能音效"
    }
    return when (MusicPlayer.eqPreset) {
        EqPreset.OFF -> "标准"
        EqPreset.POP -> "流行"
        EqPreset.ROCK -> "摇滚"
        EqPreset.JAZZ -> "爵士"
        EqPreset.CLASSIC -> "古典"
        EqPreset.BASS -> "低音"
    }
}

private fun stageEqLabelEn(): String {
    if (MusicPlayer.soundFxOn) {
        MusicPlayer.currentSoundFx()?.let { return it.en }
        return "Smart sound"
    }
    return when (MusicPlayer.eqPreset) {
        EqPreset.OFF -> "Standard"
        EqPreset.POP -> "Pop"
        EqPreset.ROCK -> "Rock"
        EqPreset.JAZZ -> "Jazz"
        EqPreset.CLASSIC -> "Classic"
        EqPreset.BASS -> "Bass"
    }
}

private fun stageModeLabel(): String = when (MusicPlayer.mode) {
    PlayMode.LOOP_ALL -> "列表循环"
    PlayMode.LOOP_ONE -> "单曲"
    PlayMode.SHUFFLE -> "随机"
    PlayMode.ORDER -> "顺序"
}

private fun stageModeLabelEn(): String = when (MusicPlayer.mode) {
    PlayMode.LOOP_ALL -> "Loop all"
    PlayMode.LOOP_ONE -> "Repeat one"
    PlayMode.SHUFFLE -> "Shuffle"
    PlayMode.ORDER -> "Sequence"
}

private fun stageTogglePlay() {
    if (MusicPlayer.current == null) {
        (MusicPlayer.songs + MusicPlayer.webSongs).firstOrNull()?.let { MusicPlayer.play(it) }
    } else {
        MusicPlayer.toggle()
    }
}

/**
 * 播放页提示：统一走全局那一条顶部黑胶囊（components/Feedback.kt 的 AppBanner）。
 * 这里保留 ctx 形参只是为了不动二十几处调用点。
 */
internal fun stageToast(ctx: Context, message: String) {
    AppBanner.show(message)
}

private fun shareStageSong(ctx: Context, song: LocalSong?) {
    val text = if (song != null) {
        "我正在 MY足迹 听《${song.title}》 - ${song.artist}，推荐给你。"
    } else {
        "我正在 MY足迹 听歌，推荐给你。"
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(send, "分享歌曲"))
}

/** 封面图：默认只留干净的色块方块（不再铺设计稿示意图），长按可切到歌曲内嵌封面 */
@Composable
private fun CoverImage(art: ImageBitmap?, modifier: Modifier = Modifier) {
    if (art != null) {
        Image(
            bitmap = art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            filterQuality = ImageSampling
        )
    } else {
        // 没有可用封面时留空，方块只显示底色
        Box(modifier)
    }
}

// ============================================================
// 2. Playlist
// ============================================================

@Composable
private fun PlaylistPage(
    granted: Boolean,
    filter: PlaylistFilter = PlaylistFilter.ALL,
    onRequestPermission: () -> Unit,
    onAddFile: () -> Unit,
    onSongAction: (LocalSong) -> Unit,
    onPlay: (LocalSong, List<LocalSong>) -> Unit
) {
    val songs = when (filter) {
        // 网页资源探到的歌也进播放列表，方便重播（链接会随重启失效，不入库）
        PlaylistFilter.ALL -> MusicPlayer.songs + MusicPlayer.webSongs
        PlaylistFilter.RECENT -> MusicPlayer.songsByIds(MusicPlayer.recentIds)
        PlaylistFilter.FAVORITE -> MusicPlayer.songsByIds(MusicPlayer.favorites.toList())
    }
    when {
        !granted -> CenteredNotice(
            tr("需要读取本地音乐权限", "Music permission needed"),
            tr("授权后会扫描你手机里已有的歌曲", "Grant access to scan your songs"),
            tr("授权", "Grant"), onRequestPermission
        )
        songs.isEmpty() -> when (filter) {
            PlaylistFilter.FAVORITE -> CenteredNotice(
                tr("还没有收藏", "No favorites yet"),
                tr("长按歌曲即可收藏，或切换回全部歌曲", "Long-press a song to favorite it, or switch to All songs"),
                tr("添加音乐", "Add music"), onAddFile
            )
            PlaylistFilter.RECENT -> CenteredNotice(
                tr("还没有播放记录", "Nothing played yet"),
                tr("播放一首歌后会出现在这里", "Songs you play will show up here"),
                tr("添加音乐", "Add music"), onAddFile
            )
            else -> CenteredNotice(
                tr("音乐库是空的", "Your library is empty"),
                tr("点下面按钮从手机里选几首歌", "Pick a few songs from your phone"),
                tr("添加音乐", "Add music"), onAddFile
            )
        }
        else -> SongList(
            songs = songs, onPlay = { onPlay(it, songs) }, onGroup = null, onLongClick = onSongAction
        )
    }
}

@Composable
private fun SongList(
    songs: List<LocalSong>,
    onPlay: (LocalSong) -> Unit,
    onGroup: ((String) -> Unit)?,
    groups: Map<String, List<LocalSong>>? = null,
    onLongClick: (LocalSong) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = PageSide, end = PageSide, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(CardGap)
    ) {
        if (groups != null && onGroup != null) {
            val keys = groups.keys.sorted()
            itemsIndexed(keys, key = { _, k -> k }) { idx, key ->
                GroupCard(
                    title = key,
                    subtitle = tr("${groups[key]?.size ?: 0} 首", "${groups[key]?.size ?: 0} songs"),
                    glyph = idx % 4,
                    onClick = { onGroup(key) }
                )
            }
        } else {
            itemsIndexed(songs, key = { _, s -> s.id }) { idx, song ->
                SongCard(
                    song = song,
                    index = idx,
                    active = song.id == MusicPlayer.current?.id,
                    onClick = { onPlay(song) },
                    onLongClick = { onLongClick(song) }
                )
            }
        }
    }
}

@Composable
private fun SongCard(
    song: LocalSong,
    index: Int,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val art = rememberAlbumArt(song)
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        targetValue = if (active) ActiveCard else Card,
        animationSpec = tween(240),
        label = "songCardBg"
    )
    val demo = painterResource(
        when (index % 4) {
            0 -> R.drawable.music_cover_1
            1 -> R.drawable.music_cover_2
            2 -> R.drawable.music_cover_3
            else -> R.drawable.music_cover_4
        }
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CardHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .pressScale(interaction, 0.98f)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (art != null) {
                Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), filterQuality = ImageSampling)
            } else {
                Image(demo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = White,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                song.artist, fontSize = 12.sp, color = White,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (song.pendingSearch) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (MusicPlayer.resolvingSongId == song.id) {
                        tr("正在获取音源…", "Fetching audio...")
                    } else {
                        tr("待获取音源 · 点击播放时获取", "Audio not fetched yet · tap to play")
                    },
                    fontSize = 10.sp,
                    color = Accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // 收藏状态直接标在列表里，配合长按菜单的「收藏 / 取消收藏」
        if (MusicPlayer.isFavorite(song.id)) {
            Icon(
                Icons.Filled.Star, tr("已收藏", "Favorited"),
                tint = Accent, modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        // 正在播放的那首用跳动的均衡器替代右箭头，主流 App 的「当前曲目」标识
        if (active && MusicPlayer.isPlaying) {
            PlayingBars(Modifier.size(22.dp, 18.dp))
        } else {
            Icon(
                Icons.Outlined.ChevronRight, null, tint = Chevron,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** 三根跳动的竖条：只在「当前歌曲 + 正在播放」时出现。 */
@Composable
private fun PlayingBars(modifier: Modifier = Modifier) {
    // 后台不解码动画：这时画一组静态竖条，省掉三个 60fps 的无限动画
    val barsResumed = rememberIsResumed()
    if (!barsResumed) {
        BarRow(modifier, listOf(9f, 13f, 10f))
        return
    }
    val transition = rememberInfiniteTransition(label = "eq")
    val p1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(760, easing = LinearEasing), RepeatMode.Restart),
        label = "eq1"
    )
    val p2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(540, easing = LinearEasing), RepeatMode.Restart),
        label = "eq2"
    )
    val p3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(920, easing = LinearEasing), RepeatMode.Restart),
        label = "eq3"
    )
    BarRow(modifier, listOf(p1, p2, p3).map { ph -> 4f + 12f * (0.5f + 0.5f * sin(ph)) })
}

@Composable
private fun BarRow(modifier: Modifier, heights: List<Float>) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { h ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(White)
            )
        }
    }
}

@Composable
private fun GroupCard(title: String, subtitle: String, glyph: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CardHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .pressScale(interaction, 0.98f)
            .clickable(
                interactionSource = interaction,
                indication = null
            ) { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibGlyph(glyph, Modifier.size(30.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = White,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, fontSize = 12.sp, color = White)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = Chevron, modifier = Modifier.size(22.dp))
    }
}

// ============================================================
// 3. Library
// ============================================================

@Composable
private fun LibraryPage(
    section: LibKind?,
    group: String?,
    onImportSongs: () -> Unit,
    onRescan: () -> Unit,
    onOpen: (LibKind) -> Unit,
    onOpenCharts: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenQqMusic: () -> Unit,
    onGroup: (String) -> Unit,
    onSongAction: (LocalSong) -> Unit,
    onPlay: (LocalSong) -> Unit
) {
    val songs = MusicPlayer.songs
    if (section == null) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PageSide),
            verticalArrangement = Arrangement.spacedBy(CardGap)
        ) {
            Spacer(Modifier.height(12.dp))
            // 常驻可见入口：批量导入 / 重新扫描，不用再进设置页找
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LibraryActionButton(tr("导入歌曲", "Import songs"), onImportSongs, Modifier.weight(1f))
                LibraryActionButton(tr("重新扫描", "Rescan"), onRescan, Modifier.weight(1f))
            }
            LibKind.entries.forEachIndexed { idx, kind ->
                LibraryNavCard(tr(kind.zh, kind.en), idx) { onOpen(kind) }
            }
            // 排行榜 / 收藏 / 读取 QQ 音乐：三枚常驻可见入口，点进去都嵌进内容区，不弹窗
            Text(
                tr("发现更多", "Discover more"),
                fontSize = 13.sp, color = Chevron, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp, start = 2.dp)
            )
            LibraryNavCard(tr("排行榜", "Charts"), 4, onOpenCharts)
            LibraryNavCard(tr("我的收藏", "My favorites"), 5, onOpenFavorites)
            LibraryNavCard(tr("读取 QQ 音乐", "Read QQ Music"), 6, onOpenQqMusic)
            Spacer(Modifier.height(12.dp))
        }
        return
    }

    when (section) {
        LibKind.SONGS -> SongList(
            songs = songs, onPlay = onPlay, onGroup = null, onLongClick = onSongAction
        )
        LibKind.PLAYLISTS -> SongList(
            songs = songs, onPlay = onPlay, onGroup = null, onLongClick = onSongAction
        )
        else -> {
            val groups = songs.groupBy {
                if (section == LibKind.ARTISTS) it.artist
                else it.album.takeIf { album -> album.isNotBlank() && album != "网页缓存" }
                    ?: tr("未知专辑", "Unknown album")
            }
            if (group == null) {
                SongList(
                    songs = emptyList(), onPlay = onPlay, onGroup = onGroup,
                    groups = groups, onLongClick = onSongAction
                )
            } else {
                SongList(
                    songs = groups[group].orEmpty(), onPlay = onPlay, onGroup = null,
                    onLongClick = onSongAction
                )
            }
        }
    }
}

/** 音乐库分类 / 功能入口：同一套白卡 + 线稿图标 + 右箭头，中英双语、跟随主题 */
@Composable
private fun LibraryNavCard(label: String, glyph: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CardHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .pressScale(interaction, 0.98f)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibGlyph(glyph, Modifier.size(30.dp))
        Spacer(Modifier.width(18.dp))
        Text(
            label, fontSize = 17.sp, color = White, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Outlined.ChevronRight, null, tint = Chevron,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * 本地「我的收藏」：嵌进音乐页内容区，不弹窗；底部播放条始终常驻。
 * 列表复用 SongList，长按照样能收藏 / 改名 / 删除，取消收藏后本页立刻更新。
 */
@Composable
private fun FavoritesPage(
    onDismiss: () -> Unit,
    onSongAction: (LocalSong) -> Unit,
    onPlay: (LocalSong) -> Unit
) {
    val songs = MusicPlayer.songsByIds(MusicPlayer.favorites.toList())
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = PageSide)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                tr("我的收藏", "My favorites"),
                color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                tr("${songs.size} 首", "${songs.size} songs"),
                color = Chevron, fontSize = 12.sp, maxLines = 1
            )
            TextButton(onClick = onDismiss) {
                Text(tr("关闭", "Close"), color = Accent, fontSize = 12.sp, maxLines = 1)
            }
        }
        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Star, null, tint = Chevron, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(tr("还没有收藏", "No favorites yet"), color = White, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr("在歌曲列表长按，选「收藏」即可加入", "Long-press a song and choose Favorite"),
                        color = Chevron, fontSize = 12.sp, textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            SongList(
                songs = songs,
                onPlay = onPlay,
                onGroup = null,
                onLongClick = onSongAction,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 音乐库顶部的轻量操作按钮：批量导入 / 重新扫描 */
@Composable
private fun LibraryActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Card)
            .pressScale(interaction, 0.96f)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 稿子里的四枚线稿图标：Playlists / Artists / Albums / Songs。 */
@Composable
private fun LibGlyph(kind: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val p = size.minDimension / 30f
        val w = 1.8f * p
        val stroke = Stroke(width = w, cap = StrokeCap.Round)
        val line: (Float, Float, Float, Float) -> Unit = { x1, y1, x2, y2 ->
            drawLine(White, Offset(x1 * p, y1 * p), Offset(x2 * p, y2 * p), w, StrokeCap.Round)
        }
        when (kind) {
            // 音符 + 三条横线
            0 -> {
                drawCircle(White, 3f * p, Offset(7f * p, 22f * p))
                line(10f, 22f, 10f, 6f)
                line(10f, 6f, 16f, 8f)
                line(18f, 12f, 28f, 12f)
                line(18f, 18f, 28f, 18f)
                line(18f, 24f, 28f, 24f)
            }
            // 人像 + 小音符
            1 -> {
                drawCircle(White, 3.5f * p, Offset(9f * p, 9f * p), style = stroke)
                val body = Path().apply {
                    moveTo(3f * p, 23f * p)
                    quadraticTo(9f * p, 12f * p, 15f * p, 23f * p)
                }
                drawPath(body, White, style = stroke)
                drawCircle(White, 2.6f * p, Offset(22f * p, 20f * p))
                line(24.5f, 20f, 24.5f, 9f)
                line(24.5f, 9f, 29f, 11f)
            }
            // 圆角方框 + 内嵌音符
            2 -> {
                drawRoundRect(
                    White,
                    cornerRadius = CornerRadius(3f * p, 3f * p),
                    topLeft = Offset(4f * p, 4f * p),
                    size = androidx.compose.ui.geometry.Size(22f * p, 22f * p),
                    style = stroke
                )
                drawCircle(White, 2.3f * p, Offset(12f * p, 19f * p))
                line(14.2f, 19f, 14.2f, 10f)
                line(14.2f, 10f, 19f, 12f)
            }
            // 排行榜：三根高低柱 + 底座
            4 -> {
                line(4f, 25f, 26f, 25f)
                line(8f, 22f, 8f, 15f)
                line(15f, 22f, 15f, 6f)
                line(22f, 22f, 22f, 11f)
                drawCircle(White, 1.5f * p, Offset(15f * p, 4f * p))
            }
            // 我的收藏：空心五角星
            5 -> {
                val star = Path().apply {
                    for (i in 0 until 10) {
                        val r = if (i % 2 == 0) 12f else 5.2f
                        val a = -PI / 2 + i * PI / 5
                        val x = (15f + r * cos(a).toFloat()) * p
                        val y = (15f + r * sin(a).toFloat()) * p
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                drawPath(star, White, style = stroke)
            }
            // 读取 QQ 音乐：圆环 + 向下同步箭头
            6 -> {
                drawCircle(White, 11f * p, Offset(15f * p, 15f * p), style = stroke)
                line(15f, 8f, 15f, 21f)
                line(10.5f, 16.5f, 15f, 21f)
                line(15f, 21f, 19.5f, 16.5f)
            }
            // 双八分音符
            else -> {
                drawCircle(White, 3.4f * p, Offset(8f * p, 21f * p))
                drawCircle(White, 3.4f * p, Offset(22f * p, 19f * p))
                line(11.2f, 21f, 11.2f, 7f)
                line(25.2f, 19f, 25.2f, 5f)
                line(11.2f, 7f, 25.2f, 5f)
            }
        }
    }
}

// ============================================================
// 4. 常驻底部播放条
// ============================================================

/**
 * 播放页「列表」按钮拉起的底部抽屉。
 * 默认 tab 跟着当前播放来源走：从收藏点歌 → 收藏列表，从最近播放点歌 → 最近列表，
 * 不再像旧版那样永远显示整个音乐库。
 */
/** 队列封面缩略图缓存：列表滚动时避免反复解码封面 */
private val queueCoverCache = android.util.LruCache<Long, android.graphics.Bitmap>(64)

@Composable
private fun MusicQueueSheet(
    source: QueueSource,
    queue: List<LocalSong>,
    recent: List<LocalSong>,
    favorites: List<LocalSong>,
    onPlay: (List<LocalSong>, LocalSong, QueueSource) -> Unit,
    onDismiss: () -> Unit
) {
    // 系统返回键 = 收起这个列表（QQ 音乐同款），不再直接退出音乐页
    androidx.activity.compose.BackHandler(enabled = true) { onDismiss() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val initialTab = when (source) {
        QueueSource.FAVORITES -> 2
        QueueSource.RECENT -> 1
        else -> 0
    }
    var tab by remember(source) { mutableStateOf(initialTab) }
    // 队列内搜索：按歌名 / 歌手过滤当前 tab 的列表
    var query by remember { mutableStateOf("") }
    // 单曲操作菜单：行尾「···」拉起，行本身保持干净
    var actionSong by remember { mutableStateOf<LocalSong?>(null) }
    val listState = rememberLazyListState()
    val tabs = listOf(
        tr("正在播放", "Now playing") to queue,
        tr("最近播放", "Recent") to recent,
        tr("我的收藏", "Favorites") to favorites
    )
    val list = (tabs.getOrNull(tab)?.second.orEmpty()).filter {
        query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true)
    }
    // 播放队列管理：只有「正在播放」tab 且没在搜索时才允许拖动排序，
    // 否则提交的是过滤后的子集，会把没显示的歌从队列里丢掉。
    val canDrag = tab == 0 && query.isBlank() && list.isNotEmpty()
    // 用稳定的 id 串当 key：拖动时每帧重组产生的临时 List 实例不会重置本地顺序
    val orderKey = list.joinToString(",") { it.id.toString() }
    val ordered = remember(orderKey) { list.toMutableStateList() }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragMoved by remember { mutableStateOf(false) }
    val dragHaptic = LocalHapticFeedback.current
    val sourceLabel = when (source) {
        QueueSource.FAVORITES -> tr("来源 · 我的收藏", "Source · Favorites")
        QueueSource.RECENT -> tr("来源 · 最近播放", "Source · Recently played")
        QueueSource.LIBRARY -> tr("来源 · 音乐库", "Source · Library")
        QueueSource.PLAYLIST -> tr("来源 · 当前列表", "Source · Current list")
    }
    val modeLabel = when (MusicPlayer.mode) {
        PlayMode.LOOP_ALL -> tr("列表循环", "Loop all")
        PlayMode.LOOP_ONE -> tr("单曲循环", "Repeat one")
        PlayMode.SHUFFLE -> tr("随机播放", "Shuffle")
        PlayMode.ORDER -> tr("顺序播放", "Sequence")
    }
    val modeIcon = when (MusicPlayer.mode) {
        PlayMode.LOOP_ALL -> Icons.Outlined.Repeat
        PlayMode.LOOP_ONE -> Icons.Outlined.RepeatOne
        PlayMode.SHUFFLE -> Icons.Outlined.Shuffle
        PlayMode.ORDER -> Icons.Outlined.LowPriority
    }
    // 播放中的律动条：整弹层共用一个节拍相位，动起来一致。
    // 暂停时别跑无限动画（弹层开着但没在播，动画纯耗电），此时相位停在当前值。
    val beatPlaying = MusicPlayer.isPlaying
    val beatResumed = rememberIsResumed()
    var beatT by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(beatPlaying, beatResumed) {
        if (!beatPlaying || !beatResumed) return@LaunchedEffect
        val from = beatT
        val fromNs = withFrameNanos { it }
        var lastUpdateNs = fromNs
        while (true) {
            val now = withFrameNanos { it }
            // 匹配原来 760ms 一圈的节奏，同时按 ~30fps 节流
            if (now - lastUpdateNs < 33_000_000L) continue
            lastUpdateNs = now
            beatT = (from + (now - fromNs) / 1_000_000_000f / 0.76f) % 1f
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Layer.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.76f)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Color(0xFFF7F7FA))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
        ) {
            Box(
                Modifier
                    .padding(top = 9.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(38.dp, 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFD6D6DC))
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, start = 10.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { idx, (label, songs) ->
                    val selected = tab == idx
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { tab = idx }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                label,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) Color(0xFF1F1F24) else Color(0xFF8A8A93),
                                maxLines = 1
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (songs.size > 999) "999+" else songs.size.toString(),
                                fontSize = 11.sp,
                                color = Color(0xFFB0B0B8)
                            )
                        }
                        Spacer(Modifier.height(7.dp))
                        Box(
                            Modifier
                                .width(26.dp)
                                .height(2.dp)
                                .background(
                                    if (selected) MinimalUi.Blue else Color.Transparent,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
            // 队列内搜索框：歌名 / 歌手都能搜，几百首歌不用翻
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, null, tint = Color(0xFF9A9AA3), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp, color = Color(0xFF1F1F24)
                    ),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    tr("在当前列表里搜歌名 / 歌手", "Search title or artist in this list"),
                                    fontSize = 13.sp, color = Color(0xFFB0B0B8)
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                )
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Close, tr("清空搜索", "Clear search"),
                        tint = Color(0xFF9A9AA3), modifier = Modifier
                            .size(16.dp)
                            .clickable { query = "" }
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(sourceLabel, fontSize = 11.sp, color = Color(0xFF8A8A93))
                Spacer(Modifier.weight(1f))
                // 播放模式：点一下循环切换（列表循环 / 单曲循环 / 随机 / 顺序）
                Icon(
                    modeIcon, modeLabel,
                    tint = Color(0xFF8A8A93),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    modeLabel, fontSize = 11.sp, color = Color(0xFF8A8A93),
                    modifier = Modifier.clickable { MusicPlayer.cycleMode() }
                )
                if (tab == 0 && MusicPlayer.current != null) {
                    Spacer(Modifier.width(14.dp))
                    // 定位当前歌曲：长列表一键滚回正在播的那首
                    Icon(
                        Icons.Outlined.MyLocation, tr("定位当前歌曲", "Locate current song"),
                        tint = Color(0xFF8A8A93),
                        modifier = Modifier
                            .size(14.dp)
                            .clickable {
                                val cur = MusicPlayer.current ?: return@clickable
                                val idx = ordered.indexOfFirst { it.id == cur.id }
                                // 「即将播放」区在列表前面占了 header + N 行，定位要算上偏移
                                val head = if (tab == 0 && MusicPlayer.upNext.isNotEmpty())
                                    1 + MusicPlayer.upNext.size else 0
                                if (idx >= 0) scope.launch { listState.animateScrollToItem(idx + head) }
                            }
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFECECF1)))
            if (canDrag) {
                Text(
                    tr("长按歌曲拖动排序 · 点「⋯」可下一首播放 / 移出队列",
                        "Long-press to reorder · More for play next / remove"),
                    fontSize = TypeScale.caption,
                    color = Color(0xFF9A9AA3),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                )
            }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            query.isNotBlank() -> tr("没找到匹配的歌", "No match")
                            tab == 1 -> tr("还没有最近播放的歌曲", "No recently played songs")
                            tab == 2 -> tr("收藏夹还是空的", "Your favorites are empty")
                            else -> tr("当前列表没有歌曲", "Nothing in this queue")
                        },
                        fontSize = 13.sp, color = Color(0xFF9A9AA3)
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
                    // 「下一首播放」插队区：只在「正在播放」tab 顶部展示，可逐条移除
                    if (tab == 0 && MusicPlayer.upNext.isNotEmpty()) {
                        item(key = "upnext_header") {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Queue, null, tint = MinimalUi.Blue, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    tr("即将播放 · 当前歌播完自动播放", "Up next · plays after this song"),
                                    fontSize = TypeScale.caption,
                                    fontWeight = FontWeight.SemiBold, color = MinimalUi.Blue,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    tr("清空", "Clear"),
                                    fontSize = 11.sp, color = Color(0xFF9A9AA3),
                                    modifier = Modifier.clickable { MusicPlayer.clearUpNext() }
                                )
                            }
                        }
                        itemsIndexed(MusicPlayer.upNext, key = { _, song -> "up_${song.id}" }) { _, song ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { onPlay(MusicPlayer.currentQueue, song, source) }
                                    .padding(horizontal = 18.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "→", fontSize = 14.sp, color = MinimalUi.Blue,
                                    modifier = Modifier.width(24.dp)
                                )
                                QueueCoverThumb(song)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        song.title,
                                        fontSize = 14.sp, color = Color(0xFF1F1F24),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        song.artist, fontSize = 11.sp, color = Color(0xFF9A9AA3),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                QueueRowAction(Icons.Outlined.Close, tr("移出插队", "Remove")) {
                                    MusicPlayer.removeUpNext(song.id)
                                }
                            }
                            Box(
                                Modifier.fillMaxWidth().padding(start = 18.dp)
                                    .height(1.dp).background(Color(0xFFEFEFF3))
                            )
                        }
                    }
                    itemsIndexed(ordered, key = { _, song -> song.id }) { index, song ->
                        val playing = song.id == MusicPlayer.current?.id
                        val fav = MusicPlayer.isFavorite(song.id)
                        // 长按拖动排序：拖拽期间只改本地 ordered，松手一次性落盘
                        val dragModifier = if (canDrag) {
                            Modifier.pointerInput(song.id, canDrag, orderKey) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingId = song.id
                                        dragOffset = 0f
                                        dragMoved = false
                                        dragHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragEnd = {
                                        if (dragMoved) {
                                            // 落盘必须提交完整队列；被拒就回滚到真实队列顺序
                                            if (!MusicPlayer.setQueueOrder(ordered.map { it.id })) {
                                                ordered.clear()
                                                ordered.addAll(MusicPlayer.currentQueue)
                                            }
                                        }
                                        draggingId = null
                                        dragOffset = 0f
                                        dragMoved = false
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffset = 0f
                                        dragMoved = false
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        if (draggingId == song.id) {
                                            dragOffset += amount.y
                                            dragMoved = true
                                            val info = listState.layoutInfo
                                            val currentInfo = info.visibleItemsInfo
                                                .firstOrNull { it.key == song.id }
                                            if (currentInfo != null) {
                                                val centerY = currentInfo.offset +
                                                    currentInfo.size / 2f + dragOffset
                                                val targetInfo = info.visibleItemsInfo.firstOrNull {
                                                    centerY >= it.offset && centerY <= it.offset + it.size
                                                }
                                                val targetId = targetInfo?.key as? Long
                                                val targetOffset = targetInfo?.offset
                                                if (targetId != null && targetOffset != null) {
                                                    val from = ordered.indexOfFirst { it.id == song.id }
                                                    val to = ordered.indexOfFirst { it.id == targetId }
                                                    if (from >= 0 && to >= 0 && from != to) {
                                                        ordered.add(to, ordered.removeAt(from))
                                                        dragOffset -= (targetOffset - currentInfo.offset).toFloat()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }.graphicsLayer {
                                translationY = if (draggingId == song.id) dragOffset else 0f
                            }.zIndex(if (draggingId == song.id) 1f else 0f)
                        } else Modifier
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (draggingId == song.id) Modifier.background(Color(0xFFE9EFFF))
                                    else Modifier
                                )
                                .then(dragModifier)
                                .clickable {
                                    onPlay(
                                        if (canDrag) MusicPlayer.currentQueue else list, song,
                                        when (tab) {
                                            1 -> QueueSource.RECENT
                                            2 -> QueueSource.FAVORITES
                                            else -> source
                                        }
                                    )
                                }
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 序号 / 正在播的律动条（QQ 音乐样式）
                            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                                if (playing) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        listOf(7, 12, 9, 14).forEachIndexed { i, h ->
                                            val k = 0.55f + 0.45f * kotlin.math.abs(
                                                kotlin.math.sin((beatT + i * 0.23f) * kotlin.math.PI.toFloat())
                                            )
                                            Box(
                                                Modifier
                                                    .width(2.dp)
                                                    .height((h * k).dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(MinimalUi.Blue)
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        "${index + 1}", fontSize = 12.sp, color = Color(0xFFB8B8C0)
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            QueueCoverThumb(song)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    fontSize = 15.sp,
                                    fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (playing) MinimalUi.Blue else Color(0xFF1F1F24),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (playing) tr("正在播放 · ${song.artist}", "Now playing · ${song.artist}") else song.artist,
                                    fontSize = 11.sp,
                                    color = if (playing) MinimalUi.Blue else Color(0xFF9A9AA3),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            // 收藏心标：点一下切换，列表内直接管理收藏
                            Icon(
                                if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                if (fav) tr("取消收藏", "Unfavorite") else tr("收藏", "Favorite"),
                                tint = if (fav) MinimalUi.Blue else Color(0xFFC2C2CA),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { MusicPlayer.toggleFavorite(song.id) }
                            )
                            if (canDrag) {
                                // 拖拽手柄：整行长按即可拖，这里只是把能力可视化
                                Icon(
                                    Icons.AutoMirrored.Outlined.QueueMusic,
                                    tr("拖动排序", "Drag to reorder"),
                                    tint = Color(0xFFC2C2CA),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                            } else {
                                Spacer(Modifier.width(12.dp))
                            }
                            // 单曲操作菜单：插队 / 上移 / 下移 / 移出都收进这里
                            Icon(
                                Icons.Outlined.MoreVert, tr("更多操作", "More actions"),
                                tint = Color(0xFF9A9AA3),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { actionSong = song }
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 72.dp)
                                .height(1.dp)
                                .background(Color(0xFFEFEFF3))
                        )
                    }
                }
            }
        }
        // 单曲操作弹窗：下一首播放 / 收藏 / 队列管理（正在播放 tab 才有移动和移出）
        actionSong?.let { s ->
            val playingNow = s.id == MusicPlayer.current?.id
            // 当前播放的那首也允许移出：removeFromQueue 会顺势切到下一首
            val canManage = tab == 0
            val favNow = MusicPlayer.isFavorite(s.id)
            val opts = buildList {
                add(tr("下一首播放", "Play next"))
                add(if (favNow) tr("取消收藏", "Remove from favorites") else tr("收藏", "Favorite"))
                if (canManage) {
                    add(tr("上移一位", "Move up"))
                    add(tr("下移一位", "Move down"))
                    add(
                        if (playingNow) tr("移出队列并切下一首", "Remove and skip")
                        else tr("移出队列", "Remove from queue")
                    )
                }
            }
            MusicOptionDialog(
                title = "${s.title} - ${s.artist}",
                options = opts,
                selected = -1,
                onPick = { idx ->
                    when {
                        idx == 0 -> {
                            MusicPlayer.playNext(s)
                            Toast.makeText(
                                ctx,
                                tr("已插队：「${s.title}」将在当前歌曲后播放",
                                    "Queued: ${s.title} plays next"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        idx == 1 -> MusicPlayer.toggleFavorite(s.id)
                        canManage && idx == 2 -> MusicPlayer.moveInQueue(s.id, -1)
                        canManage && idx == 3 -> MusicPlayer.moveInQueue(s.id, 1)
                        canManage && idx == 4 -> {
                            val ok = MusicPlayer.removeFromQueue(s.id)
                            Toast.makeText(
                                ctx,
                                if (ok) tr("已移出播放队列", "Removed from queue")
                                else tr("队列只剩一首，无法移出", "Last song cannot be removed"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    actionSong = null
                },
                onDismiss = { actionSong = null }
            )
        }
    }
}

/** 队列行封面：异步解码 + 缓存，没封面画一个浅色音符块，不出白板 */
@Composable
private fun QueueCoverThumb(song: LocalSong) {
    val ctx = LocalContext.current
    val bmp = remember(song.id) { mutableStateOf(queueCoverCache.get(song.id)) }
    LaunchedEffect(song.id) {
        if (bmp.value == null) {
            val b = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { MusicExternal.loadCover(ctx, song) }.getOrNull()
            }
            if (b != null) {
                queueCoverCache.put(song.id, b)
                bmp.value = b
            }
        }
    }
    val shown = bmp.value
    if (shown != null) {
        Image(
            bitmap = shown.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)),
            filterQuality = ImageSampling
        )
    } else {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE8E8EE)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.MusicNote, null, tint = Color(0xFFB8B8C0), modifier = Modifier.size(18.dp))
        }
    }
}

/** 队列行尾的小圆钮：排队列管理用的轻量图标按钮 */
@Composable
private fun QueueRowAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Icon(
        icon, label,
        tint = Color(0xFF9A9AA3),
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(6.dp)
    )
}

/** 底部迷你播放条：小封面 + 歌名艺术家 + 播放/暂停。
 *  点整条回到全屏播放页；长按后向上拖动，会把条慢慢拉伸成一张播放页预览卡
 *  （封面长大、黑胶从封面后探出、进度和控件淡入），拉过一半松手直接进全屏，
 *  不够一半就弹回来。 */
@Composable
private fun PlayerBar(minimal: Boolean, onOpenNowPlaying: () -> Unit) {
    val song = MusicPlayer.current
        ?: (MusicPlayer.songs + MusicPlayer.webSongs).firstOrNull()
    val art = rememberAlbumArt(song)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // 极简模式下底条换成白卡 + 蓝色进度，和 5 tab / 圆角卡片同一套语义色
    val barBg = if (minimal) MinimalUi.Card else CtrlCircle.copy(alpha = 0.5f)
    val barLine = if (minimal) MinimalUi.Line else ProgOff
    val barOn = if (minimal) MinimalUi.Blue else ProgOn
    val btnBg = if (minimal) MinimalUi.Blue else CtrlCircle
    val inkColor = if (minimal) MinimalUi.Text else White
    val controlInk = if (minimal) Color.White else White

    // 长按拉伸进度 0..1：拖动时跟手 snapTo，松手不过阈值就弹回 0
    val expand = remember { Animatable(0f) }
    val expandValue = expand.value
    val maxDragPx = with(density) { 300.dp.toPx() }

    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(barBg.copy(alpha = 0.98f))
                .border(1.dp, barLine.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
        ) {
            // 进度线收进悬浮胶囊，点击与长按手势仍沿用原有实现。
            Box(
                Modifier.fillMaxWidth().height(2.dp).background(barLine.copy(alpha = 0.45f))
            ) {
                Box(
                    Modifier.fillMaxWidth(MusicPlayer.progress).height(2.dp).background(barOn)
                )
            }
            Row(
                Modifier.fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var longFired = false
                            var moved = false
                            // 用户是否手动拖过：拖过就按松手位置决定去留，没拖过让自动展开跑完
                            var touched = false
                            var totalX = 0f
                            var totalY = 0f
                            var dragJob: kotlinx.coroutines.Job? = null
                            // 长按计时：按住不动到达系统长按时长，卡片自动向上展开（参考网易云）
                            val longPressJob = scope.launch {
                                delay(viewConfiguration.longPressTimeoutMillis)
                                if (!moved) {
                                    longFired = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    expand.animateTo(
                                        1f,
                                        tween(380, easing = FastOutSlowInEasing)
                                    )
                                }
                            }
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    longPressJob.cancel()
                                    if (longFired) {
                                        val v = expand.value
                                        if (!touched) {
                                            // 没手动拖过：让自动展开继续跑完，松手保持展开
                                        } else if (v > 0.45f) {
                                            // 手动拉过大半 → 顺势进全屏播放页
                                            onOpenNowPlaying()
                                            scope.launch { expand.snapTo(0f) }
                                        } else {
                                            // 拉了一点又缩回去 → 弹回收起
                                            scope.launch {
                                                expand.animateTo(
                                                    0f,
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    )
                                                )
                                            }
                                        }
                                    } else if (!moved) {
                                        onOpenNowPlaying()
                                    }
                                    break
                                }
                                val d = change.positionChange()
                                totalX += d.x
                                totalY += d.y
                                if (!longFired) {
                                    if (abs(totalX) > viewConfiguration.touchSlop ||
                                        abs(totalY) > viewConfiguration.touchSlop
                                    ) {
                                        // 长按前就移动：取消长按，也不当点击（等下次按）
                                        moved = true
                                        longPressJob.cancel()
                                        break
                                    }
                                } else {
                                    // 向上拖 = 负 dy → 进度增加；实时跟手
                                    change.consume()
                                    touched = true
                                    val next = (expand.value - d.y / maxDragPx).coerceIn(0f, 1f)
                                    dragJob?.cancel()
                                    dragJob = scope.launch { expand.snapTo(next) }
                                }
                            }
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 真实封面与标题保持一眼可辨，空封面仍由 CoverImage 显示纯色块。
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(CoverTeal),
                    contentAlignment = Alignment.Center
                ) {
                    CoverImage(art, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song?.title ?: tr("未播放", "Not playing"),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = inkColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        song?.artist?.ifBlank { tr("未知歌手", "Unknown artist") } ?: tr("点按打开播放器", "Tap to open player"),
                        fontSize = 12.sp, color = TimeGray,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                // 播放/暂停按钮使用品牌实心圆，和底部导航的选中态呼应。
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(btnBg)
                        .clickable {
                            if (MusicPlayer.current == null) {
                                (MusicPlayer.songs + MusicPlayer.webSongs).firstOrNull()?.let { MusicPlayer.play(it) }
                            } else MusicPlayer.toggle()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (MusicPlayer.isPlaying) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(3.dp, 14.dp).clip(RoundedCornerShape(6.dp)).background(controlInk))
                            Box(Modifier.size(3.dp, 14.dp).clip(RoundedCornerShape(6.dp)).background(controlInk))
                        }
                    } else {
                        PlayTriangle(Modifier.size(14.dp, 16.dp), color = controlInk)
                    }
                }
            }
        }

        // 长按自动向上展开的预览卡：盖在条上面（Column 兄弟在先，画在上层不被裁）
        if (expandValue > 0.01f) {
            PlayerExpandPreview(
                progress = expandValue,
                art = art,
                song = song,
                cardBg = barBg,
                inkColor = inkColor,
                accent = barOn,
                onCollapse = {
                    scope.launch {
                        expand.animateTo(
                            0f,
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                },
                onOpenNowPlaying = onOpenNowPlaying
            )
        }
    }
}

/**
 * 长按迷你条自动向上展开的播放页预览卡（参考网易云）：高度长到约半屏就停，
 * 封面变大、黑胶圆盘从封面右侧探出来、标题/进度/控件淡入。
 * 顶部有收起把手：点一下或向下拖都能收回；点歌名/列表钮进全屏播放页。
 */
@Composable
private fun BoxScope.PlayerExpandPreview(
    progress: Float,
    art: ImageBitmap?,
    song: com.example.my.data.music.LocalSong?,
    cardBg: Color,
    inkColor: Color,
    accent: Color,
    onCollapse: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    // smoothstep 让展开前半段变化慢（“缓慢展开”的手感）、后半段顺滑到位
    val ease = progress * progress * (3f - 2f * progress)
    val barHeight = 66.dp
    // 参考 QQ 音乐的比例：展开到约 1/4 屏就停，够看清信息又不遮页面
    val fullHeight = 264.dp
    val height = barHeight + (fullHeight - barHeight) * ease
    val contentAlpha = ((ease - 0.3f) / 0.45f).coerceIn(0f, 1f)
    val artSize = 42.dp + (68.dp - 42.dp) * ease
    val corner = 12.dp + (24.dp - 12.dp) * ease
    val haptic = LocalHapticFeedback.current
    val fav = song != null && MusicPlayer.isFavorite(song.id)

    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                alpha = (0.35f + 0.65f * ease)
                shadowElevation = 8.dp.toPx() * ease
                shape = RoundedCornerShape(topStart = corner, topEnd = corner)
                clip = true
            }
            .background(cardBg)
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            // 收起把手：点一下收回；按住向下拖超过 40dp 也收回
            Box(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.width(64.dp).height(22.dp).clip(CircleShape)
                        .background(TimeGray.copy(alpha = 0.18f))
                        .clickable { onCollapse() }
                        .pointerInput(Unit) {
                            var totalY = 0f
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                totalY = 0f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!c.pressed) break
                                    totalY += c.positionChange().y
                                    if (totalY > 40.dp.toPx()) {
                                        onCollapse()
                                        break
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 向下的 V 把手
                    Canvas(Modifier.size(14.dp, 8.dp)) {
                        val w = size.width
                        val h = size.height
                        drawLine(
                            TimeGray,
                            start = Offset(0f, 0f),
                            end = Offset(w / 2f, h),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            TimeGray,
                            start = Offset(w / 2f, h),
                            end = Offset(w, 0f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            Spacer(Modifier.height(if (ease > 0.5f) 10.dp else 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 封面：干净一张方图，黑胶盘取消（缩窄后叠在标题上显得乱）
                Box(
                    Modifier.size(artSize).clip(RoundedCornerShape(corner * 0.8f))
                        .background(CoverTeal),
                    contentAlignment = Alignment.Center
                ) {
                    CoverImage(art, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(20.dp))
                Column(
                    Modifier.weight(1f).clickable { onOpenNowPlaying() }
                ) {
                    Text(
                        song?.title ?: tr("未播放", "Not playing"),
                        fontSize = (14 + 4 * ease).sp,
                        fontWeight = FontWeight.Bold,
                        color = inkColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song?.artist ?: "",
                        fontSize = 13.sp,
                        color = TimeGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer { alpha = contentAlpha }
                    )
                }
            }
            if (contentAlpha > 0.01f) {
                Column(
                    Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }.padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // 可拖动的进度条：按住左右拖实时跟手，松手 seek；拖动中时间跟着手指走
                    var dragFraction by remember { mutableStateOf<Float?>(null) }
                    val frac = dragFraction ?: MusicPlayer.progress
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    dragFraction = (down.position.x / size.width).coerceIn(0f, 1f)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (!change.pressed) {
                                            dragFraction?.let { target -> MusicPlayer.seekTo(target) }
                                            dragFraction = null
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            break
                                        }
                                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                        change.consume()
                                    }
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // 轨道
                        Box(
                            Modifier.fillMaxWidth().height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(TimeGray.copy(alpha = 0.25f))
                        )
                        // 已播
                        Box(
                            Modifier.fillMaxWidth(frac).height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent)
                        )
                        // 圆形拖块：fill(frac) + 尾对齐，圆心正好落在进度点上
                        Box(Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.CenterStart) {
                            Box(Modifier.fillMaxWidth(frac), contentAlignment = Alignment.CenterEnd) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        val dur = MusicPlayer.durationMs
                        Text(
                            formatMs(if (dragFraction != null) (dur * frac).toLong() else MusicPlayer.positionMs),
                            fontSize = 11.sp, color = TimeGray
                        )
                        Spacer(Modifier.weight(1f))
                        Text(formatMs(dur), fontSize = 11.sp, color = TimeGray)
                    }
                    Spacer(Modifier.height(10.dp))
                    // 控制行：收藏 / 上一首 / 播放暂停 / 下一首 / 播放队列，全部真功能
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = tr("收藏", "Favorite"),
                            tint = if (fav) accent else inkColor,
                            modifier = Modifier.size(25.dp)
                                .clickable {
                                    song?.let { MusicPlayer.toggleFavorite(it.id) }
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                        )
                        SkipGlyph(false, Modifier.size(26.dp, 22.dp).clickable {
                            MusicPlayer.prev()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        })
                        Box(
                            Modifier.size(56.dp).clip(CircleShape)
                                .border(2.dp, inkColor, CircleShape)
                                .clickable {
                                    MusicPlayer.toggle()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (MusicPlayer.isPlaying) {
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.size(4.dp, 18.dp).background(inkColor))
                                    Box(Modifier.size(4.dp, 18.dp).background(inkColor))
                                }
                            } else {
                                PlayTriangle(Modifier.size(20.dp, 24.dp), color = inkColor)
                            }
                        }
                        SkipGlyph(true, Modifier.size(26.dp, 22.dp).clickable {
                            MusicPlayer.next()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        })
                        // 列表钮：进全屏播放页看完整队列
                        Canvas(Modifier.size(26.dp, 22.dp).clickable { onOpenNowPlaying() }) {
                            val w = size.width
                            val h = size.height
                            val stroke = 2.dp.toPx()
                            repeat(3) { i ->
                                drawLine(
                                    inkColor,
                                    start = Offset(0f, h * (0.2f + i * 0.3f)),
                                    end = Offset(w * (if (i == 2) 0.62f else 1f), h * (0.2f + i * 0.3f)),
                                    strokeWidth = stroke,
                                    cap = StrokeCap.Round
                                )
                            }
                            drawCircle(inkColor, radius = stroke * 1.4f, center = Offset(w * 0.78f, h * 0.8f))
                            drawLine(
                                inkColor,
                                start = Offset(w * 0.78f, h * 0.8f),
                                end = Offset(w, h * 0.42f),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 按下时轻微缩小、松手带弹性回弹；主流 App 所有可点元素的统一手感。 */
@Composable
private fun Modifier.pressScale(
    interaction: MutableInteractionSource,
    pressedScale: Float = 0.96f
): Modifier {
    val pressed = interaction.collectIsPressedAsState().value
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** 实心双三角（上一首 / 下一首），靠水平镜像复用同一份路径。 */
@Composable
private fun SkipGlyph(forward: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.graphicsLayer { scaleX = if (forward) -1f else 1f }) {
        val w = size.width
        val h = size.height
        fun triangle(tipX: Float, baseX: Float): Path = Path().apply {
            moveTo(tipX, h / 2f)
            lineTo(baseX, 1.5f)
            lineTo(baseX, h - 1.5f)
            close()
        }
        drawPath(triangle(0f, w * 0.46f), White)
        drawPath(triangle(w * 0.52f, w), White)
    }
}

@Composable
private fun PlayTriangle(modifier: Modifier = Modifier, color: Color = White) {
    Canvas(modifier.graphicsLayer { translationX = size.width * 0.08f }) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(w * 0.9f, h / 2f)
            lineTo(0f, h)
            close()
        }
        drawPath(path, color)
    }
}

// ============================================================
// 6. 设计稿播放页：圆角软按钮与线稿图标（全部随深浅主题重组）
// ============================================================

/** 顶部两枚圆角方钮：白卡 + 柔和投影 */
@Composable
private fun PlayerSoftButton(
    label: String,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        Modifier
            .size(52.dp)
            .shadow(6.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(MinimalUi.Card)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** 控制行两侧的小圆钮 */
@Composable
private fun PlayerRoundButton(
    label: String,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        Modifier
            .size(52.dp)
            .shadow(6.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(MinimalUi.Card)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun BackArrowGlyph() {
    Canvas(Modifier.size(22.dp)) {
        val c = MinimalUi.Text
        val w = 2.dp.toPx()
        val mid = size.height / 2f
        drawLine(c, Offset(3.dp.toPx(), mid), Offset(size.width - 2.dp.toPx(), mid), w, StrokeCap.Round)
        val head = Path().apply {
            moveTo(9.dp.toPx(), mid - 5.dp.toPx())
            lineTo(3.dp.toPx(), mid)
            lineTo(9.dp.toPx(), mid + 5.dp.toPx())
        }
        drawPath(head, c, style = Stroke(width = w, cap = StrokeCap.Round))
    }
}

/** 右上唱片/光盘：点它切换是否显示歌曲自带封面 */
@Composable
private fun DiscGlyph() {
    Canvas(Modifier.size(22.dp)) {
        val c = MinimalUi.Text
        val r = size.minDimension * 0.34f
        val center = Offset(size.width / 2f, size.height / 2f)
        val w = 1.8.dp.toPx()
        drawCircle(c, r, center, style = Stroke(width = w))
        drawCircle(c, size.minDimension * 0.065f, center)
        val tick = 2.6.dp.toPx()
        drawLine(c, Offset(center.x, center.y - r - tick), Offset(center.x, center.y - r), w, StrokeCap.Round)
        drawLine(c, Offset(center.x, center.y + r), Offset(center.x, center.y + r + tick), w, StrokeCap.Round)
        drawLine(c, Offset(center.x - r - tick, center.y), Offset(center.x - r, center.y), w, StrokeCap.Round)
        drawLine(c, Offset(center.x + r, center.y), Offset(center.x + r + tick, center.y), w, StrokeCap.Round)
    }
}

@Composable
private fun ChevronDownGlyph() {
    Canvas(Modifier.size(20.dp)) {
        val p = Path().apply {
            moveTo(3.dp.toPx(), 7.dp.toPx())
            lineTo(size.width / 2f, size.height - 7.dp.toPx())
            lineTo(size.width - 3.dp.toPx(), 7.dp.toPx())
        }
        drawPath(p, MinimalUi.Blue, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** 随机播放：随机模式时变蓝，提示当前状态 */
@Composable
private fun ShuffleGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val c = if (MusicPlayer.mode == PlayMode.SHUFFLE) MinimalUi.Blue else MinimalUi.Text
        val w = 1.8.dp.toPx()
        val cross = Path().apply {
            moveTo(2.dp.toPx(), 5.dp.toPx())
            lineTo(size.width - 6.dp.toPx(), size.height - 5.dp.toPx())
            moveTo(2.dp.toPx(), size.height - 5.dp.toPx())
            lineTo(size.width - 6.dp.toPx(), 5.dp.toPx())
        }
        drawPath(cross, c, style = Stroke(width = w, cap = StrokeCap.Round))
        val arrows = Path().apply {
            moveTo(size.width - 9.dp.toPx(), 3.dp.toPx())
            lineTo(size.width - 2.dp.toPx(), 5.dp.toPx())
            lineTo(size.width - 6.dp.toPx(), 9.dp.toPx())
            moveTo(size.width - 9.dp.toPx(), size.height - 3.dp.toPx())
            lineTo(size.width - 2.dp.toPx(), size.height - 5.dp.toPx())
            lineTo(size.width - 6.dp.toPx(), size.height - 9.dp.toPx())
        }
        drawPath(arrows, c, style = Stroke(width = w, cap = StrokeCap.Round))
    }
}

@Composable
private fun PlaylistGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val c = MinimalUi.Text
        val w = 2.dp.toPx()
        listOf(6.dp, 12.dp, 18.dp).forEach { y ->
            drawLine(
                c, Offset(3.dp.toPx(), y.toPx()), Offset(size.width - 3.dp.toPx(), y.toPx()),
                w, StrokeCap.Round
            )
        }
    }
}

/** 音量/音效波形：一个点 + 三道递增弧 */
@Composable
private fun VolumeWaveGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val c = MinimalUi.Text
        val w = 1.8.dp.toPx()
        drawLine(c, Offset(3.dp.toPx(), 9.dp.toPx()), Offset(3.dp.toPx(), 15.dp.toPx()), w, StrokeCap.Round)
        for (i in 0..2) {
            val x = 6.dp.toPx() + i * 4.dp.toPx()
            val spread = 4.dp.toPx() + i * 2.5.dp.toPx()
            val p = Path().apply {
                moveTo(x, 12.dp.toPx() - spread)
                quadraticTo(x + 7.dp.toPx(), 12.dp.toPx(), x, 12.dp.toPx() + spread)
            }
            drawPath(p, c, style = Stroke(width = w, cap = StrokeCap.Round))
        }
    }
}

/** 收藏：托盘 + 向下箭头；已收藏时整枚图标变蓝 */
@Composable
private fun FavoriteSaveGlyph(filled: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val c = if (filled) MinimalUi.Blue else MinimalUi.Text
        val w = 1.9.dp.toPx()
        val tray = Path().apply {
            moveTo(4.dp.toPx(), 4.dp.toPx())
            lineTo(4.dp.toPx(), 15.dp.toPx())
            quadraticTo(4.dp.toPx(), 20.dp.toPx(), 9.dp.toPx(), 20.dp.toPx())
            lineTo(15.dp.toPx(), 20.dp.toPx())
            quadraticTo(20.dp.toPx(), 20.dp.toPx(), 20.dp.toPx(), 15.dp.toPx())
            lineTo(20.dp.toPx(), 4.dp.toPx())
        }
        drawPath(tray, c, style = Stroke(width = w, cap = StrokeCap.Round))
        drawLine(c, Offset(12.dp.toPx(), 6.dp.toPx()), Offset(12.dp.toPx(), 15.dp.toPx()), w, StrokeCap.Round)
        val tip = Path().apply {
            moveTo(8.5f.dp.toPx(), 11.5f.dp.toPx())
            lineTo(12.dp.toPx(), 15.dp.toPx())
            lineTo(15.5f.dp.toPx(), 11.5f.dp.toPx())
        }
        drawPath(tip, c, style = Stroke(width = w, cap = StrokeCap.Round))
    }
}

// ============================================================
// 5. 空态 / 封面 / 工具
// ============================================================

@Composable
private fun CenteredNotice(title: String, subtitle: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LibGlyph(3, Modifier.size(46.dp))
        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 16.sp, color = White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, fontSize = 12.sp, color = Chevron, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier.clip(RoundedCornerShape(26.dp)).background(Card)
                .clickable { onAction() }.padding(horizontal = 28.dp, vertical = 11.dp)
        ) { Text(action, color = White, fontSize = 14.sp) }
    }
}

/** 封面统一走 [CoverRepository]：内嵌图 → 磁盘缓存 → 联网搜索，三页共用同一份缓存。 */
@Composable
internal fun rememberAlbumArt(song: LocalSong?): ImageBitmap? {
    val ctx = LocalContext.current
    var bmp by remember(song?.id, song?.title, song?.artist) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(song?.id, song?.title, song?.artist) {
        val s = song ?: return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { CoverRepository.load(ctx, s) }
        bmp = loaded?.asImageBitmap()
    }
    return bmp
}

private fun mixColor(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = a.red * (1f - k) + b.red * k,
        green = a.green * (1f - k) + b.green * k,
        blue = a.blue * (1f - k) + b.blue * k,
        alpha = 1f
    )
}

/**
 * 从封面采样一个低饱和主色，给三页背景/光晕用；失败或图太黑白时退回品牌橙。
 * 只做装饰层，所有文字颜色仍用原有高对比常量，不因封面偏色影响可读性。
 */
@Composable
private fun rememberCoverTint(art: ImageBitmap?): Color {
    val fallback = StageOrangeDeep
    if (art == null) return fallback
    return remember(art) {
        runCatching {
            val pixels = art.toPixelMap()
            val step = maxOf(8, minOf(pixels.width, pixels.height) / 48)
            var r = 0.0
            var g = 0.0
            var b = 0.0
            var n = 0
            var y = step / 2
            while (y < pixels.height) {
                var x = step / 2
                while (x < pixels.width) {
                    val c = pixels[x, y]
                    if (c.alpha >= 0.6f) {
                        val lum = 0.299 * c.red + 0.587 * c.green + 0.114 * c.blue
                        if (lum in 0.12..0.92) {
                            r += c.red
                            g += c.green
                            b += c.blue
                            n++
                        }
                    }
                    x += step
                }
                y += step
            }
            if (n < 8) fallback else {
                val avg = Color((r / n).toFloat(), (g / n).toFloat(), (b / n).toFloat())
                val lum = 0.299 * avg.red + 0.587 * avg.green + 0.114 * avg.blue
                if (lum > 0.70f) mixColor(avg, StageOrangeDeep, 0.30f) else avg
            }
        }.getOrDefault(fallback)
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    return String.format("%d:%02d", totalSec / 60, totalSec % 60)
}

private fun Context.checkSelfCompat(permission: String): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else true
}