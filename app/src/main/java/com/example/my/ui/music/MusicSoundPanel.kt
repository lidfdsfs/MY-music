package com.example.my.ui.music

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Article
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
import androidx.compose.material.icons.outlined.QueueMusic
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.my.data.music.CoverRepository
import com.example.my.data.music.EqPreset
import com.example.my.data.music.SoundFxCatalog
import com.example.my.data.music.SoundFxPrefs
import com.example.my.data.music.SoundFxSpec
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
import com.example.my.data.music.WebLyricsTranslate
import com.example.my.data.music.MusicNotification
import com.example.my.data.music.LrcLine
import com.example.my.data.music.HanziScript
import com.example.my.data.music.MusicPlayer
import com.example.my.data.music.QQFailReason
import com.example.my.data.music.QQFetch
import com.example.my.data.music.QQMusic
import com.example.my.data.music.SongNameParser
import com.example.my.data.music.SongAudio
import com.example.my.data.music.SongFetch
import com.example.my.data.music.SongFetchReason
import com.example.my.data.music.SongMeta
import com.example.my.data.music.SongResult
import com.example.my.data.music.SongSearch
import com.example.my.data.music.WebMusicCache
import com.example.my.state.AppState
import com.example.my.state.ExtraScreen
import com.example.my.ui.i18n.tr
import com.example.my.ui.icons.Lucide
import com.example.my.ui.theme.AppPalette
import com.example.my.util.ImageExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val SoundPanelBg = Color(0xFF0E1311)
private val SoundPanelCard = Color(0xFF1B2320)
private val SoundPanelCardHi = Color(0xFF27312C)
private val SoundPanelGreen = Color(0xFF2AF56B)
private val SoundPanelGold = Color(0xFFF5C451)
private val SoundPanelInk = Color(0xFFF4F7F5)
private val SoundPanelMuted = Color(0xFF98A29C)
private val SoundPanelLine = Color(0xFF2B3530)
private val SoundPanelBottom = Color(0xFF171D1A)

private enum class SoundGlyph {
    SMART, DTS, VINYL, STUDIO, VOICE, AI, SPEAKER, GALAXY, WIDE, VR, HIFI, HALL, CLEAR
}

/** 页面上的一个音效条目：名字/说明/使用量都从 DSP 参数对象上取，避免文案和参数两处维护 */
private data class SoundEffect(val spec: SoundFxSpec) {
    val id: String get() = spec.id
    val name: String get() = tr(spec.zh, spec.en)
    val usage: String get() = tr(spec.usageZh, spec.usageEn)
    val detail: String get() = tr(spec.detailZh, spec.detailEn)
    val glyph: SoundGlyph get() = glyphFor(spec.id)
}

private fun glyphFor(id: String): SoundGlyph = when (id) {
    "surround" -> SoundGlyph.DTS
    "hall" -> SoundGlyph.HALL
    "vintage" -> SoundGlyph.VINYL
    "vocal" -> SoundGlyph.VOICE
    "bass" -> SoundGlyph.SPEAKER
    "rock" -> SoundGlyph.AI
    "jazz" -> SoundGlyph.STUDIO
    "clear" -> SoundGlyph.CLEAR
    "night" -> SoundGlyph.HIFI
    "wide" -> SoundGlyph.WIDE
    "cinema" -> SoundGlyph.VR
    "rain" -> SoundGlyph.GALAXY
    "edm" -> SoundGlyph.AI
    "outdoor" -> SoundGlyph.SPEAKER
    "soft" -> SoundGlyph.CLEAR
    "live" -> SoundGlyph.HALL
    else -> SoundGlyph.SMART
}

private val SmartSoundEffect = SoundEffect(SoundFxCatalog.smart)

private val SoundAllEffects =
    (listOf(SoundFxCatalog.smart) + SoundFxCatalog.all).map { SoundEffect(it) }

private val SoundOfficialEffects =
    SoundAllEffects.filter { it.spec.group == 0 && it.id != SoundFxCatalog.DefaultId }

private val SoundCreatorEffects = SoundAllEffects.filter { it.spec.group == 1 }

@Composable
internal fun SoundEffectPanel(
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    // 深色音效页：状态栏/导航栏图标临时切成白色，关闭后还原
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.let { c ->
                oldStatus?.let { c.isAppearanceLightStatusBars = it }
                oldNav?.let { c.isAppearanceLightNavigationBars = it }
            }
        }
    }
    SoundFxPrefs.init(ctx)
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var recentTab by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    // 声音实验室：定时关闭 / 耳鸣舒缓 / 煲机 三个真实功能
    var sleepOpen by remember { mutableStateOf(false) }
    var reliefOpen by remember { mutableStateOf(false) }
    var burnOpen by remember { mutableStateOf(false) }
    val toneEngine = remember { ToneEngine() }
    DisposableEffect(Unit) { onDispose { toneEngine.stop() } }
    // 选中项 / 总开关都以播放器状态为准：横屏、竖屏、音效页看到的是同一份
    val selectedId = MusicPlayer.soundFxId ?: SoundFxCatalog.DefaultId
    val enabled = MusicPlayer.soundFxOn
    val blocker = remember { MutableInteractionSource() }

    val selected = SoundAllEffects.firstOrNull { it.id == selectedId } ?: SmartSoundEffect
    fun tip(message: String) {
        // 提示统一走全 App 顶部黑胶囊，不再弹系统 Toast
        com.example.my.ui.components.AppBanner.show(message)
    }
    fun apply(effect: SoundEffect) {
        MusicPlayer.applySoundFx(effect.spec)
        SoundFxPrefs.pushRecent(ctx, effect.id)
        tip(tr("已使用音效：${effect.name}", "Sound effect: ${effect.name}"))
    }

    /** 歌手音效：按当前歌手做稳定匹配，同一歌手每次都落到同一套 */
    fun applyArtistEffect() {
        val artist = MusicPlayer.current?.artist?.trim().orEmpty()
        if (artist.isEmpty()) {
            tip(tr("先播放一首歌，再按歌手匹配音效", "Play a song first to match an effect"))
            return
        }
        val pool = SoundOfficialEffects.ifEmpty { SoundAllEffects }
        val pick = pool[Math.floorMod(artist.hashCode(), pool.size)]
        apply(pick)
    }

    /** 设备管理：直接跳到系统蓝牙设置，比在 App 里假装"已连接 2 台"有用 */
    fun openBluetoothSettings() {
        val ok = runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (!ok) {
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            tip(tr("已打开系统设置", "Opened system settings"))
        }
    }

    BackHandler {
        if (searchOpen) searchOpen = false else onClose()
    }

    Box(
        Modifier.fillMaxSize().background(SoundPanelBg)
            .clickable(interactionSource = blocker, indication = null) {}
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // 顶栏：返回 + 三个频道 + 搜索
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        null,
                        tint = SoundPanelInk,
                        modifier = Modifier.size(24.dp)
                    )
                }
                val tabs = listOf(
                    tr("银河音效", "Galaxy"),
                    tr("空间音效", "Spatial"),
                    tr("调音台", "Console")
                )
                tabs.forEachIndexed { index, label ->
                    Column(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable { tab = index }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            label,
                            fontSize = 16.sp,
                            fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Medium,
                            color = if (tab == index) SoundPanelGreen else SoundPanelMuted,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.width(if (tab == index) 24.dp else 0.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(SoundPanelGreen)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .clickable { searchOpen = !searchOpen },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Lucide.Search, null, tint = SoundPanelInk, modifier = Modifier.size(21.dp))
                }
            }

            if (searchOpen) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SoundPanelCard)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Lucide.Search, null,
                        tint = SoundPanelMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                tr("搜索音效、音质、歌手", "Search effects, quality, artists"),
                                fontSize = 12.sp, color = SoundPanelMuted
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp, color = SoundPanelInk),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Outlined.Close, null,
                            tint = SoundPanelMuted,
                            modifier = Modifier.size(18.dp).clickable { query = "" }
                        )
                    }
                }
            }

            when (tab) {
                0 -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (query.trim().isNotEmpty()) {
                            val results = SoundAllEffects.filter {
                                it.name.contains(query.trim(), true) ||
                                    it.usage.contains(query.trim(), true)
                            }
                            if (results.isEmpty()) {
                                item {
                                    Column(
                                        Modifier.fillMaxWidth().padding(top = 60.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Lucide.Search, null,
                                            tint = SoundPanelMuted,
                                            modifier = Modifier.size(34.dp)
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            tr("没找到相关音效", "No matching sound effects"),
                                            fontSize = 13.sp, color = SoundPanelMuted
                                        )
                                    }
                                }
                            } else {
                                results.forEach { effect ->
                                    item(key = "search_${effect.id}") {
                                        SoundSearchRow(
                                            effect = effect,
                                            selected = selectedId == effect.id,
                                            onUse = { apply(effect) }
                                        )
                                        Spacer(Modifier.height(8.dp))
                                    }
                                }
                            }
                        } else {
                            item(key = "member") {
                                SoundMemberBanner(
                                    count = SoundAllEffects.size,
                                    onClick = {
                                        scope.launch { listState.animateScrollToItem(4) }
                                    }
                                )
                            }
                            item(key = "hero") {
                                Spacer(Modifier.height(14.dp))
                                // 推荐卡是横向可滑的：左滑出下一张（跟参考图底部那一排小圆点对应）
                                val heroPages = remember(SoundFxPrefs.recents, SoundFxPrefs.favorites) {
                                    val recent = SoundFxPrefs.recents.mapNotNull { id ->
                                        SoundAllEffects.firstOrNull { it.id == id }
                                    }
                                    val fav = SoundAllEffects.filter {
                                        SoundFxPrefs.favorites.contains(it.id)
                                    }
                                    (listOf(SmartSoundEffect) + recent + fav + SoundAllEffects)
                                        .distinctBy { it.id }
                                        .take(10)
                                }
                                val pagerState = rememberPagerState(pageCount = { heroPages.size })
                                val recentName = heroPages.getOrNull(1)
                                HorizontalPager(
                                    state = pagerState,
                                    pageSpacing = 10.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) { page ->
                                    val effect = heroPages[page]
                                    SoundHeroCard(
                                        effect = effect,
                                        selected = selectedId == effect.id && enabled,
                                        subtitle = if (page == 0) {
                                            if (recentName != null) {
                                                tr(
                                                    "最近使用${recentName.name}",
                                                    "Recently used: ${recentName.name}"
                                                )
                                            } else {
                                                tr("最近使用智能音效", "Recently used: Smart sound")
                                            }
                                        } else {
                                            tr("常听推荐 · 第 ${page + 1} 张", "Recommended · ${page + 1}")
                                        },
                                        onUse = { apply(effect) }
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    repeat(heroPages.size) { index ->
                                        val active = pagerState.currentPage == index
                                        Box(
                                            Modifier.padding(horizontal = 2.dp)
                                                .width(if (active) 14.dp else 4.dp)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(
                                                    if (active) SoundPanelGreen
                                                    else SoundPanelMuted.copy(alpha = 0.35f)
                                                )
                                                .clickable {
                                                    scope.launch { pagerState.animateScrollToPage(index) }
                                                }
                                        )
                                    }
                                }
                            }
                            item(key = "quick") {
                                Spacer(Modifier.height(18.dp))
                                SoundQuickEntries(
                                    allCount = SoundAllEffects.size,
                                    artistName = MusicPlayer.current?.artist.orEmpty(),
                                    onAll = {
                                        scope.launch { listState.animateScrollToItem(4) }
                                    },
                                    onDevices = { openBluetoothSettings() },
                                    onTherapy = { sleepOpen = true },
                                    onArtist = { applyArtistEffect() }
                                )
                            }
                            item(key = "recent") {
                                Spacer(Modifier.height(22.dp))
                                // 「最近 / 收藏」都读真实记录：收藏在音效页里点一下心就行
                                val recentEffects = SoundFxPrefs.recents
                                    .mapNotNull { id -> SoundAllEffects.firstOrNull { it.id == id } }
                                val favoriteEffects = SoundAllEffects
                                    .filter { SoundFxPrefs.favorites.contains(it.id) }
                                SoundRecentHeader(
                                    count = recentEffects.size,
                                    favoriteCount = favoriteEffects.size,
                                    active = recentTab,
                                    onSelect = { recentTab = it },
                                    onMore = { tip(tr("音效记录已是最新", "Effect history is up to date")) }
                                )
                                Spacer(Modifier.height(10.dp))
                                if (recentTab == 0) {
                                    val list = if (recentEffects.isEmpty()) listOf(SmartSoundEffect)
                                    else recentEffects.take(3)
                                    list.forEach { effect ->
                                        SoundRecentCard(
                                            effect = effect,
                                            selected = selectedId == effect.id,
                                            favorite = SoundFxPrefs.favorites.contains(effect.id),
                                            onFavorite = { SoundFxPrefs.toggleFavorite(ctx, effect.id) },
                                            onUse = { apply(effect) }
                                        )
                                        Spacer(Modifier.height(8.dp))
                                    }
                                } else {
                                    if (favoriteEffects.isEmpty()) {
                                        SoundEmptyFavorites()
                                    } else {
                                        favoriteEffects.forEach { effect ->
                                            SoundRecentCard(
                                                effect = effect,
                                                selected = selectedId == effect.id,
                                                favorite = true,
                                                onFavorite = { SoundFxPrefs.toggleFavorite(ctx, effect.id) },
                                                onUse = { apply(effect) }
                                            )
                                            Spacer(Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                            item(key = "official_title") {
                                Spacer(Modifier.height(24.dp))
                                SoundSectionHeader(
                                    title = tr("官方音效", "Official effects"),
                                    count = SoundOfficialEffects.size.toString(),
                                    onMore = {
                                        scope.launch { listState.animateScrollToItem(5) }
                                    }
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                            item(key = "official_grid") {
                                SoundEffectGrid(
                                    effects = SoundOfficialEffects,
                                    selectedId = selectedId,
                                    favorites = SoundFxPrefs.favorites,
                                    onUse = { apply(it) },
                                    onFavorite = { id -> SoundFxPrefs.toggleFavorite(ctx, id) }
                                )
                            }
                            item(key = "creator_title") {
                                Spacer(Modifier.height(24.dp))
                                SoundSectionHeader(
                                    title = tr("达人音效", "Creator effects"),
                                    count = SoundCreatorEffects.size.toString(),
                                    onMore = {
                                        tab = 1
                                    }
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                            item(key = "creator_grid") {
                                SoundEffectGrid(
                                    effects = SoundCreatorEffects,
                                    selectedId = selectedId,
                                    favorites = SoundFxPrefs.favorites,
                                    onUse = { apply(it) },
                                    onFavorite = { id -> SoundFxPrefs.toggleFavorite(ctx, id) }
                                )
                            }
                            item(key = "lab_title") {
                                Spacer(Modifier.height(26.dp))
                                Text(
                                    tr("声音实验室", "Sound lab"),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SoundPanelInk
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    tr("黑科技赋能，让音乐更有趣", "Make music more fun with audio tech"),
                                    fontSize = 11.sp, color = SoundPanelMuted
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            item(key = "lab_tracks") {
                                SoundLabRow(
                                    icon = Icons.Outlined.LibraryMusic,
                                    title = tr("多音轨调节", "Multi-track tuning"),
                                    subtitle = tr("切到调音台，分段调人声与伴奏", "Open the console for band-level tuning"),
                                    onClick = {
                                        tab = 2
                                        tip(tr("已切到调音台：五段频段可单独调节", "Console opened: five bands are adjustable"))
                                    }
                                )
                                SoundLabRow(
                                    icon = Icons.Outlined.Waves,
                                    title = tr("耳鸣舒缓", "Tinnitus relief"),
                                    subtitle = tr("生成 200–1000Hz 舒缓音，最多 5 分钟", "200–1000Hz relief tone, up to 5 minutes"),
                                    onClick = { reliefOpen = true }
                                )
                                SoundLabRow(
                                    icon = Icons.Outlined.Headphones,
                                    title = tr("智能煲机", "Smart burn-in"),
                                    subtitle = tr("40Hz–16kHz 低音量扫频，默认 2 分钟", "Low-volume 40Hz–16kHz sweep, 2 minutes"),
                                    onClick = { burnOpen = true }
                                )
                                SoundLabRow(
                                    icon = Icons.Outlined.Whatshot,
                                    title = tr("4D震动", "4D vibration"),
                                    subtitle = tr("播放时跟随节奏轻震", "Soft haptics while playing"),
                                    switch = true,
                                    checked = SoundLab.vibration,
                                    onCheckedChange = { SoundLab.vibration = it },
                                    onClick = { SoundLab.vibration = !SoundLab.vibration }
                                )
                                SoundLabRow(
                                    icon = Icons.Outlined.Star,
                                    title = tr("动感闪光", "Dynamic flash"),
                                    subtitle = tr("播放时让闪光灯跟随节奏", "Flash pulses while playing"),
                                    switch = true,
                                    checked = SoundLab.flash,
                                    onCheckedChange = { SoundLab.flash = it },
                                    onClick = { SoundLab.flash = !SoundLab.flash }
                                )
                            }
                            item(key = "footer") {
                                Spacer(Modifier.height(26.dp))
                                Text(
                                    tr("更多音效持续更新中", "More sound effects are on the way"),
                                    fontSize = 10.sp,
                                    color = SoundPanelMuted.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }
                1 -> {
                    // 空间 / 场景类：都是带 Virtualizer 或混响的套装，不是摆设
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        item(key = "spatial_intro") {
                            Text(
                                tr("空间音效", "Spatial effects"),
                                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SoundPanelInk
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                tr(
                                    "宽场 / 混响 / 低频，戴耳机最明显",
                                    "Wide field, reverb, deep lows · best with headphones"
                                ),
                                fontSize = 11.sp, color = SoundPanelMuted
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        item(key = "spatial_grid") {
                            SoundEffectGrid(
                                effects = SoundCreatorEffects,
                                selectedId = selectedId,
                                favorites = SoundFxPrefs.favorites,
                                onUse = { apply(it) },
                                onFavorite = { id -> SoundFxPrefs.toggleFavorite(ctx, id) }
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                        item(key = "spatial_note") {
                            Text(
                                tr(
                                    "部分机型会关掉 Virtualizer，这类设备上空间音效只剩 EQ 与混响起作用。",
                                    "Some ROMs disable Virtualizer; only EQ and reverb apply there."
                                ),
                                fontSize = 10.sp, color = SoundPanelMuted.copy(alpha = 0.75f)
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
                else -> {
                    SoundConsole(
                        modifier = Modifier.weight(1f),
                        onApply = { bands, bass, spatial, reverb, loudness ->
                            MusicPlayer.applyCustomSoundFx(bands, bass, spatial, reverb, loudness)
                            tip(tr("已应用自定义调音", "Custom tuning applied"))
                        }
                    )
                }
            }

            // 底部吸底开关：关闭即回到标准原声
            Row(
                Modifier.fillMaxWidth()
                    .background(SoundPanelBottom)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(SoundPanelCardHi),
                    contentAlignment = Alignment.Center
                ) {
                    Text("A", fontSize = 22.sp, color = SoundPanelInk, fontWeight = FontWeight.Light)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (enabled) tr("开启音效：${selected.name}", "Sound on: ${selected.name}")
                        else tr("音效已关闭", "Sound off"),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SoundPanelInk,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        tr("实时作用于本机播放", "Applied to local playback in real time"),
                        fontSize = 10.sp, color = SoundPanelMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { on -> MusicPlayer.setSoundFxEnabled(on) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SoundPanelGreen,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.16f)
                    )
                )
            }
            if (sleepOpen) SoundSleepTimerDialog(onClose = { sleepOpen = false })
            if (reliefOpen) {
                SoundToneDialog(
                    mode = ToneMode.Relief,
                    engine = toneEngine,
                    onClose = { reliefOpen = false }
                )
            }
            if (burnOpen) {
                SoundToneDialog(
                    mode = ToneMode.BurnIn,
                    engine = toneEngine,
                    onClose = { burnOpen = false }
                )
            }
        }
    }
}

@Composable
private fun SoundMemberBanner(count: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SoundPanelCard)
            .border(1.dp, SoundPanelGreen.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.WorkspacePremium, null,
            tint = SoundPanelGreen,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            tr(
                "本机音效已全部解锁 · 共 $count 款",
                "All $count sound effects unlocked on this device"
            ),
            fontSize = 11.sp, color = SoundPanelInk, maxLines = 2,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(50.dp))
                .background(SoundPanelGreen)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                tr("查看全部", "View all"),
                fontSize = 10.sp, color = Color(0xFF082016), fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SoundHeroCard(
    effect: SoundEffect,
    selected: Boolean,
    subtitle: String,
    onUse: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SoundPanelCard)
            .padding(14.dp)
    ) {
        Text(
            tr("根据你的常听音效推荐", "Recommended from your listening"),
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SoundPanelInk
        )
        Spacer(Modifier.height(3.dp))
        Text(
            subtitle,
            fontSize = 11.sp, color = SoundPanelMuted, maxLines = 1
        )
        SoundGalaxyOrb()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(SoundPanelCardHi),
                contentAlignment = Alignment.Center
            ) {
                if (effect.id == SoundFxCatalog.DefaultId) {
                    Text("A", fontSize = 22.sp, color = SoundPanelInk, fontWeight = FontWeight.Light)
                } else {
                    SoundGlyphIcon(effect.glyph, 20)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        effect.name,
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SoundPanelInk,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(effect.usage, fontSize = 9.sp, color = SoundPanelMuted, maxLines = 1)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    tr("智能适配，动态调节", "Adaptive, dynamically tuned"),
                    fontSize = 10.sp, color = SoundPanelMuted, maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
            SoundUseButton(selected = selected, onClick = onUse, width = 76.dp)
        }
    }
}

@Composable
private fun SoundGalaxyOrb() {
    val transition = rememberInfiniteTransition(label = "soundOrb")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(2600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "orbPulse"
    )
    Canvas(Modifier.fillMaxWidth().height(208.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(size.width, size.height) * 0.42f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    SoundPanelGreen.copy(alpha = 0.30f),
                    SoundPanelGreen.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = radius * 1.45f
            ),
            radius = radius * 1.45f,
            center = Offset(cx, cy)
        )
        repeat(140) { index ->
            val degrees = (index * 137.508f + 17f) % 360f
            val angle = Math.toRadians(degrees.toDouble())
            val length = radius * (0.18f + ((index * 29) % 100) / 100f * 0.82f) * pulse
            val start = Offset(
                cx + (cos(angle) * radius * 0.08).toFloat(),
                cy + (sin(angle) * radius * 0.08).toFloat()
            )
            val end = Offset(
                cx + (cos(angle) * length).toFloat(),
                cy + (sin(angle) * length).toFloat()
            )
            drawLine(
                color = SoundPanelGreen.copy(alpha = 0.10f + (index % 5) * 0.035f),
                start = start,
                end = end,
                strokeWidth = 1f
            )
            if (index % 3 == 0) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.55f),
                    radius = 1.1f + (index % 4) * 0.55f,
                    center = end
                )
            }
        }
        repeat(3) { ring ->
            drawCircle(
                color = SoundPanelGreen.copy(alpha = 0.22f - ring * 0.05f),
                radius = radius * (0.34f + ring * 0.13f),
                center = Offset(cx, cy),
                style = Stroke(width = 1.2f)
            )
        }
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.95f), SoundPanelGreen.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(cx, cy),
                radius = radius * 0.26f
            ),
            radius = radius * 0.26f,
            center = Offset(cx, cy)
        )
    }
}

@Composable
private fun SoundQuickEntries(
    allCount: Int,
    artistName: String,
    onAll: () -> Unit,
    onDevices: () -> Unit,
    onTherapy: () -> Unit,
    onArtist: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SoundQuickEntry(
            Modifier.weight(1f),
            icon = Icons.Outlined.LibraryMusic,
            title = tr("全部音效", "All effects"),
            subtitle = tr("$allCount 款", "$allCount sounds"),
            onClick = onAll
        )
        SoundQuickEntry(
            Modifier.weight(1f),
            icon = Icons.Outlined.Headphones,
            title = tr("设备管理", "Devices"),
            subtitle = tr("蓝牙 / 音频", "Bluetooth / audio"),
            onClick = onDevices
        )
        SoundQuickEntry(
            Modifier.weight(1f),
            icon = Icons.Outlined.Waves,
            title = tr("音乐疗愈", "Music therapy"),
            subtitle = tr("定时关闭", "Sleep timer"),
            onClick = onTherapy
        )
        SoundQuickEntry(
            Modifier.weight(1f),
            icon = Icons.Outlined.Mic,
            title = tr("歌手音效", "Artist effects"),
            subtitle = artistName.ifBlank { tr("按歌手匹配", "Match artist") },
            onClick = onArtist
        )
    }
}

@Composable
private fun SoundQuickEntry(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(SoundPanelCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = SoundPanelGreen, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            color = SoundPanelInk, maxLines = 1
        )
        Spacer(Modifier.height(1.dp))
        Text(subtitle, fontSize = 9.sp, color = SoundPanelMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SoundRecentHeader(
    count: Int,
    favoriteCount: Int,
    active: Int,
    onSelect: (Int) -> Unit,
    onMore: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SoundRecentTab(
            label = tr("最近", "Recent"),
            count = count,
            active = active == 0,
            onClick = { onSelect(0) }
        )
        Spacer(Modifier.width(18.dp))
        SoundRecentTab(
            label = tr("收藏", "Favorites"),
            count = favoriteCount,
            active = active == 1,
            onClick = { onSelect(1) }
        )
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Outlined.ChevronRight, null,
            tint = SoundPanelMuted,
            modifier = Modifier.size(20.dp).clickable { onMore() }
        )
    }
}

@Composable
private fun SoundRecentTab(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }, horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label, fontSize = 17.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) SoundPanelInk else SoundPanelMuted
            )
            Spacer(Modifier.width(5.dp))
            Text(count.toString(), fontSize = 10.sp, color = SoundPanelMuted)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.width(if (active) 22.dp else 0.dp).height(2.dp)
                .clip(RoundedCornerShape(1.dp)).background(SoundPanelGreen)
        )
    }
}

@Composable
private fun SoundRecentCard(
    effect: SoundEffect,
    selected: Boolean,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onUse: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SoundPanelCard)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(58.dp).clip(CircleShape).background(SoundPanelCardHi),
            contentAlignment = Alignment.Center
        ) {
            Text("A", fontSize = 26.sp, color = SoundPanelInk, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                effect.name, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = SoundPanelInk, maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(effect.usage, fontSize = 10.sp, color = SoundPanelMuted, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(
                tr("智能适配，动态调节", "Adaptive, dynamically tuned"),
                fontSize = 10.sp, color = SoundPanelMuted, maxLines = 1
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SoundFavoriteButton(favorite = favorite, onClick = onFavorite)
            Spacer(Modifier.height(10.dp))
            SoundUseButton(selected = selected, onClick = onUse, width = 72.dp)
        }
    }
}

@Composable
private fun SoundEmptyFavorites() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.FavoriteBorder, null,
            tint = SoundPanelMuted,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr("还没有收藏的音效", "No saved effects yet"),
            fontSize = 12.sp, color = SoundPanelMuted
        )
    }
}

@Composable
private fun SoundSectionHeader(title: String, count: String, onMore: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SoundPanelInk
        )
        Spacer(Modifier.width(7.dp))
        Text(count, fontSize = 10.sp, color = SoundPanelMuted)
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Outlined.ChevronRight, null,
            tint = SoundPanelMuted,
            modifier = Modifier.size(20.dp).clickable { onMore() }
        )
    }
}

@Composable
private fun SoundEffectGrid(
    effects: List<SoundEffect>,
    selectedId: String,
    favorites: Set<String>,
    onUse: (SoundEffect) -> Unit,
    onFavorite: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        effects.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { effect ->
                    SoundEffectCard(
                        effect = effect,
                        selected = selectedId == effect.id,
                        favorite = favorites.contains(effect.id),
                        onUse = { onUse(effect) },
                        onFavorite = { onFavorite(effect.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SoundEffectCard(
    effect: SoundEffect,
    selected: Boolean,
    favorite: Boolean,
    onUse: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.height(168.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SoundPanelCard)
            .padding(9.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(46.dp)) {
            Box(
                Modifier.size(42.dp).align(Alignment.Center)
                    .clip(CircleShape).background(SoundPanelCardHi),
                contentAlignment = Alignment.Center
            ) {
                SoundGlyphIcon(effect.glyph, 20)
            }
            Box(
                Modifier.size(28.dp).align(Alignment.TopEnd)
                    .clip(CircleShape).clickable { onFavorite() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    null,
                    tint = if (favorite) SoundPanelGold else SoundPanelMuted,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            effect.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = SoundPanelInk, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            effect.usage, fontSize = 8.sp, color = SoundPanelMuted,
            textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.weight(1f))
        SoundUseButton(selected = selected, onClick = onUse, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SoundUseButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp? = null
) {
    val base = if (width != null) modifier.width(width) else modifier
    Box(
        base.height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) SoundPanelGreen else Color.White.copy(alpha = 0.08f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (selected) tr("使用中", "In use") else tr("使用", "Use"),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Color(0xFF08120C) else SoundPanelGreen,
            maxLines = 1
        )
    }
}

@Composable
private fun SoundFavoriteButton(favorite: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            null,
            tint = if (favorite) SoundPanelGold else SoundPanelMuted,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun SoundLabRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    switch: Boolean = false,
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(SoundPanelCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = SoundPanelGreen, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = SoundPanelInk, maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle, fontSize = 10.sp, color = SoundPanelMuted,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (switch) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SoundPanelGreen,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.16f)
                )
            )
        } else {
            Icon(
                Icons.Outlined.ChevronRight, null,
                tint = SoundPanelMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SoundSearchRow(effect: SoundEffect, selected: Boolean, onUse: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(SoundPanelCard).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(SoundPanelCardHi),
            contentAlignment = Alignment.Center
        ) {
            SoundGlyphIcon(effect.glyph, 18)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(effect.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SoundPanelInk)
            Text(effect.usage, fontSize = 10.sp, color = SoundPanelMuted)
        }
        SoundUseButton(selected = selected, onClick = onUse, width = 64.dp)
    }
}

@Composable
private fun SoundGlyphIcon(glyph: SoundGlyph, size: Int) {
    val tint = SoundPanelGreen
    when (glyph) {
        SoundGlyph.SMART -> Text(
            "A", fontSize = size.sp, color = SoundPanelInk, fontWeight = FontWeight.Light
        )
        SoundGlyph.DTS -> Text(
            "dts", fontSize = (size * 0.5f).sp, color = SoundPanelInk, fontWeight = FontWeight.Bold
        )
        SoundGlyph.VINYL -> Icon(Icons.Outlined.Album, null, tint = tint, modifier = Modifier.size(size.dp))
        SoundGlyph.STUDIO -> Icon(Icons.Outlined.Waves, null, tint = tint, modifier = Modifier.size(size.dp))
        SoundGlyph.VOICE -> Icon(Icons.Outlined.Mic, null, tint = tint, modifier = Modifier.size(size.dp))
        SoundGlyph.AI -> Icon(Icons.Outlined.AutoAwesome, null, tint = tint, modifier = Modifier.size(size.dp))
        SoundGlyph.SPEAKER -> Icon(Icons.Outlined.SurroundSound, null, tint = tint, modifier = Modifier.size(size.dp))
        SoundGlyph.GALAXY -> Icon(Icons.Outlined.Star, null, tint = tint, modifier = Modifier.size(size.dp))
        SoundGlyph.WIDE -> Icon(Icons.Outlined.Waves, null, tint = tint, modifier = Modifier.size(size.dp))
        SoundGlyph.VR -> Icon(Icons.Outlined.Headphones, null, tint = tint, modifier = Modifier.size(size.dp))
        SoundGlyph.HIFI -> Text(
            "HiFi", fontSize = (size * 0.42f).sp, color = tint, fontWeight = FontWeight.Bold
        )
        SoundGlyph.HALL -> Icon(Icons.Outlined.LibraryMusic, null, tint = tint, modifier = Modifier.size(size.dp))
        SoundGlyph.CLEAR -> Icon(Icons.Outlined.Mic, null, tint = tint, modifier = Modifier.size(size.dp))
    }
}

/**
 * 调音台：五个频段 + 低频增强 + 空间环绕 + 响度 + 混响档位。
 * 拖完点「应用」才真正把这组参数交给播放器，避免拖动时每帧重建 AudioEffect。
 */
@Composable
private fun SoundConsole(
    modifier: Modifier = Modifier,
    onApply: (List<Float>, Int, Int, Int, Int) -> Unit
) {
    val seed = MusicPlayer.currentSoundFx()
    var bands by remember(seed?.id) {
        mutableStateOf((seed?.bands ?: List(5) { 0f }).toList())
    }
    var bass by remember(seed?.id) { mutableStateOf((seed?.bass ?: 0).toFloat()) }
    var spatial by remember(seed?.id) { mutableStateOf((seed?.spatial ?: 0).toFloat()) }
    var loudness by remember(seed?.id) { mutableStateOf((seed?.loudness ?: 0).toFloat()) }
    var reverb by remember(seed?.id) { mutableIntStateOf(seed?.reverb ?: 0) }

    Column(
        modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            tr("调音台", "Console"),
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SoundPanelInk
        )
        Spacer(Modifier.height(3.dp))
        Text(
            tr(
                "五段频段 + 低频 / 空间 / 响度 / 混响，拖完点应用即生效",
                "Five bands plus bass, spatial, loudness and reverb"
            ),
            fontSize = 11.sp, color = SoundPanelMuted
        )
        Spacer(Modifier.height(10.dp))

        val bandLabels = listOf(
            tr("低频 60Hz", "Low 60Hz"),
            tr("中低 250Hz", "Low-mid 250Hz"),
            tr("中频 1kHz", "Mid 1kHz"),
            tr("中高 4kHz", "High-mid 4kHz"),
            tr("高频 12kHz", "High 12kHz")
        )
        bandLabels.forEachIndexed { index, label ->
            SoundConsoleSlider(
                label = label,
                value = bands[index],
                valueText = "%+.1f dB".format(bands[index]),
                range = -6f..6f,
                onChange = { v ->
                    bands = bands.toMutableList().also { it[index] = v }
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        SoundConsoleSlider(
            label = tr("低频增强", "Bass boost"),
            value = bass,
            valueText = "${(bass / 10f).toInt()}%",
            range = 0f..1000f,
            onChange = { bass = it }
        )
        SoundConsoleSlider(
            label = tr("空间环绕", "Spatial"),
            value = spatial,
            valueText = "${(spatial / 10f).toInt()}%",
            range = 0f..1000f,
            onChange = { spatial = it }
        )
        SoundConsoleSlider(
            label = tr("响度提升", "Loudness"),
            value = loudness,
            valueText = "${(loudness / 10f).toInt()}%",
            range = 0f..800f,
            onChange = { loudness = it }
        )

        Spacer(Modifier.height(10.dp))
        Text(
            tr("混响", "Reverb"),
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SoundPanelInk
        )
        Spacer(Modifier.height(6.dp))
        val reverbOptions = listOf(
            0 to tr("关", "Off"),
            2 to tr("房间", "Room"),
            4 to tr("大厅", "Hall"),
            5 to tr("剧院", "Theatre"),
            6 to tr("录音室", "Studio")
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            reverbOptions.forEach { option ->
                val value = option.first
                val label = option.second
                val active = reverb == value
                Box(
                    Modifier.weight(1f).height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) SoundPanelGreen.copy(alpha = 0.22f) else SoundPanelCard)
                        .border(
                            1.dp,
                            if (active) SoundPanelGreen else Color.Transparent,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { reverb = value },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label, fontSize = 10.sp,
                        color = if (active) SoundPanelGreen else SoundPanelMuted, maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SoundPanelCard)
                    .clickable {
                        bands = List(5) { 0f }
                        bass = 0f
                        spatial = 0f
                        loudness = 0f
                        reverb = 0
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(tr("重置", "Reset"), fontSize = 12.sp, color = SoundPanelMuted)
            }
            Box(
                Modifier.weight(2f).height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SoundPanelGreen)
                    .clickable {
                        onApply(bands, bass.toInt(), spatial.toInt(), reverb, loudness.toInt())
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tr("应用这套设置", "Apply this"),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF08120C)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SoundConsoleSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, fontSize = 12.sp, color = SoundPanelInk,
            modifier = Modifier.width(96.dp), maxLines = 1
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = SoundPanelGreen,
                activeTrackColor = SoundPanelGreen,
                inactiveTrackColor = Color.White.copy(alpha = 0.14f)
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            valueText, fontSize = 10.sp, color = SoundPanelMuted,
            modifier = Modifier.width(58.dp), textAlign = TextAlign.End, maxLines = 1
        )
    }
}

// ───────────────────────── 声音实验室：真实生效的附加功能 ─────────────────────────

/** 4D 震动 / 动感闪光总开关：关掉音效页后依然生效，所以放在全局对象里 */
internal object SoundLab {
    var vibration by mutableStateOf(false)
    var flash by mutableStateOf(false)
}

internal enum class ToneMode { Relief, BurnIn }

/** 定时关闭：复用播放器已有的 sleep timer，不是只弹个提示 */
@Composable
private fun SoundSleepTimerDialog(onClose: () -> Unit) {
    val options = listOf(
        0 to tr("关闭定时", "Turn off"),
        15 to tr("15 分钟", "15 minutes"),
        30 to tr("30 分钟", "30 minutes"),
        60 to tr("60 分钟", "60 minutes"),
        90 to tr("90 分钟", "90 minutes")
    )
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = SoundPanelCard,
        titleContentColor = SoundPanelInk,
        textContentColor = SoundPanelMuted,
        title = {
            Text(tr("音乐疗愈 · 定时关闭", "Sleep timer"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                options.forEach { (minutes, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                MusicPlayer.setSleepTimer(minutes)
                                com.example.my.ui.components.AppBanner.show(
                                    if (minutes <= 0) tr("已关闭定时", "Sleep timer off")
                                    else tr("将在 $minutes 分钟后停止播放", "Playback stops in $minutes minutes")
                                )
                                onClose()
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontSize = 14.sp, color = SoundPanelInk)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text(tr("取消", "Cancel"), color = SoundPanelMuted)
            }
        }
    )
}

/**
 * 耳鸣舒缓 / 智能煲机：用 AudioTrack 合成正弦音，真实出声。
 * 舒缓为固定频率（可调），煲机为 40Hz–16kHz 低音量扫频，都有时长上限。
 */
@Composable
private fun SoundToneDialog(mode: ToneMode, engine: ToneEngine, onClose: () -> Unit) {
    val isRelief = mode == ToneMode.Relief
    val total = if (isRelief) 300 else 120
    var playing by remember { mutableStateOf(false) }
    var hz by remember { mutableStateOf(420f) }
    var left by remember { mutableStateOf(total) }

    fun stopAll() {
        playing = false
        engine.stop()
    }

    DisposableEffect(Unit) { onDispose { engine.stop() } }

    // 播放期间倒计时，到点自动停
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        left = total
        engine.start(sweep = !isRelief, hz = hz.toDouble())
        repeat(total) {
            delay(1000)
            left = (left - 1).coerceAtLeast(0)
        }
        engine.stop()
        playing = false
    }
    // 舒缓音调频率实时生效，不用重启整条音轨
    LaunchedEffect(hz) {
        if (playing && isRelief) engine.setFrequency(hz.toDouble())
    }

    val mm = left / 60
    val ss = (left % 60).toString().padStart(2, '0')
    AlertDialog(
        onDismissRequest = { stopAll(); onClose() },
        containerColor = SoundPanelCard,
        titleContentColor = SoundPanelInk,
        textContentColor = SoundPanelMuted,
        title = {
            Text(
                if (isRelief) tr("耳鸣舒缓", "Tinnitus relief") else tr("智能煲机", "Smart burn-in"),
                fontSize = 17.sp, fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    if (isRelief) {
                        tr(
                            "播放 200–1000Hz 的柔和正弦音，音量固定在安全范围；每次最多 5 分钟。",
                            "A soft 200–1000Hz sine tone at a safe level, up to 5 minutes."
                        )
                    } else {
                        tr(
                            "以低音量播放 40Hz–16kHz 扫频，建议音量保持 30%，每次 2 分钟。",
                            "Low-volume 40Hz–16kHz sweep, 2 minutes. Keep volume around 30%."
                        )
                    },
                    fontSize = 12.sp, lineHeight = 18.sp, color = SoundPanelMuted
                )
                if (isRelief) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("频率", "Frequency"), fontSize = 12.sp, color = SoundPanelInk)
                        Spacer(Modifier.width(10.dp))
                        Slider(
                            value = hz,
                            onValueChange = { hz = it },
                            valueRange = 200f..1000f,
                            steps = 15,
                            colors = SliderDefaults.colors(
                                thumbColor = SoundPanelGreen,
                                activeTrackColor = SoundPanelGreen,
                                inactiveTrackColor = Color.White.copy(alpha = 0.14f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text("${hz.toInt()}Hz", fontSize = 11.sp, color = SoundPanelMuted)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    if (playing) tr("剩余 %d:%s", "Remaining %d:%s").format(mm, ss)
                    else tr("未开始 · 约 %d 分钟", "Not started · %d minutes").format(total / 60),
                    fontSize = 12.sp, color = if (playing) SoundPanelGreen else SoundPanelMuted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (playing) {
                    stopAll()
                } else {
                    // 避免和音乐叠在一起；停掉音乐再出声
                    MusicPlayer.pause()
                    playing = true
                }
            }) {
                Text(if (playing) tr("停止", "Stop") else tr("开始", "Start"), color = SoundPanelGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = { stopAll(); onClose() }) {
                Text(tr("关闭", "Close"), color = SoundPanelMuted)
            }
        }
    )
}

/**
 * 正弦音 / 扫频发生器。
 *
 * 只做最简单的 PCM 流：单声道、44100Hz、16bit，振幅压在安全范围；
 * 线程退出时释放 AudioTrack，页面关闭立即 stop，不会留在后台偷偷响。
 */
private class ToneEngine {
    @Volatile private var running = false
    @Volatile private var sweep = false
    @Volatile private var frequency = 420.0
    private var track: AudioTrack? = null
    private var worker: Thread? = null

    fun setFrequency(hz: Double) {
        frequency = hz
    }

    fun start(sweep: Boolean, hz: Double) {
        stop()
        this.sweep = sweep
        frequency = hz
        running = true
        worker = Thread {
            val sampleRate = 44100
            val buffer = ShortArray(2048)
            val min = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
            val t = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(min * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull() ?: run {
                running = false
                return@Thread
            }
            track = t
            runCatching { t.play() }
            var phase = 0.0
            val startNs = System.nanoTime()
            while (running) {
                val hzNow = if (sweep) {
                    val sec = (System.nanoTime() - startNs) / 1_000_000_000.0
                    // 40Hz 起，按指数扫到 16kHz，120 秒一轮
                    40.0 * Math.pow(400.0, (sec % 120.0) / 120.0)
                } else {
                    frequency
                }
                val amp = if (sweep) 0.05 else 0.16
                for (i in buffer.indices) {
                    buffer[i] = (sin(phase) * amp * Short.MAX_VALUE).toInt().toShort()
                    phase += 2.0 * PI * hzNow / sampleRate
                    if (phase > 2.0 * PI) phase -= 2.0 * PI
                }
                val written = runCatching { t.write(buffer, 0, buffer.size) }.getOrDefault(-1)
                if (written < 0) break
            }
            runCatching { t.stop() }
            runCatching { t.release() }
            track = null
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        worker?.let { runCatching { it.join(400) } }
        worker = null
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.release() }
        }
        track = null
    }
}

/**
 * 全局挂载：4D 震动与动感闪光在音效页关闭后仍跟随播放生效。
 * 闪光灯用 CameraManager.setTorchMode（不需要 CAMERA 权限），失败就自动关闭开关。
 */
@Composable
internal fun SoundLabEffects() {
    val ctx = LocalContext.current

    val vibrator = remember(ctx) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }
    LaunchedEffect(SoundLab.vibration) {
        if (!SoundLab.vibration) {
            runCatching { vibrator?.cancel() }
            return@LaunchedEffect
        }
        while (true) {
            if (MusicPlayer.isPlaying) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(45L, 110))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(45L)
                    }
                }
            }
            delay(620)
        }
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { vibrator?.cancel() } }
    }

    val hasTorch = remember(ctx) { TorchLamp.available(ctx) }
    LaunchedEffect(SoundLab.flash) {
        if (!SoundLab.flash) {
            TorchLamp.set(ctx, false)
            return@LaunchedEffect
        }
        if (!hasTorch) {
            SoundLab.flash = false
            com.example.my.ui.components.AppBanner.show(
                tr("本机没有可用的闪光灯", "No flashlight available")
            )
            return@LaunchedEffect
        }
        try {
            while (true) {
                if (MusicPlayer.isPlaying) {
                    TorchLamp.set(ctx, true)
                    delay(110)
                    TorchLamp.set(ctx, false)
                    delay(560)
                } else {
                    delay(420)
                }
            }
        } finally {
            TorchLamp.set(ctx, false)
        }
    }
}
