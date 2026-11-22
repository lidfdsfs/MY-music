package com.example.my.ui.music

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Queue
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.example.my.ui.theme.Layer
import com.example.my.util.ImageExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal val MoreBg = Color(0xFFF1F3F2)
internal val MoreInk = Color(0xFF171A1C)
internal val MoreSub = Color(0xFF8C9295)
internal val MoreAccent = Color(0xFF20E36C)
internal val MoreDividerColor = Color(0xFFE1E4E2)

@Composable
internal fun SongMoreSheet(
    song: LocalSong?,
    art: ImageBitmap?,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onShareCard: () -> Unit,
    onDrivingMode: () -> Unit,
    onOpenSoundPanel: () -> Unit,
    onPlayerStyle: () -> Unit,
    onLandscape: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val ctx = LocalContext.current
    var sleepDialog by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = remember(audio) {
        audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
    }
    var volume by remember {
        mutableIntStateOf(audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0)
    }
    val scrimBlocker = remember { MutableInteractionSource() }
    val sheetBlocker = remember { MutableInteractionSource() }
    val favorite = song?.let { MusicPlayer.isFavorite(it.id) } ?: false
    val playCount = song?.let { MusicPlayer.playCounts[it.id] ?: 0 } ?: 0
    val speed = MusicPlayer.playbackSpeed
    val speedText = when {
        abs(speed - 1f) < 0.01f -> tr("正常", "Normal")
        else -> "${speed}x"
    }
    fun tip(message: String) = stageToast(ctx, message)

    BackHandler { onClose() }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .background(Layer.Scrim)
                .clickable(interactionSource = scrimBlocker, indication = null) { onClose() }
        )
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(MoreBg)
                .clickable(interactionSource = sheetBlocker, indication = null) {}
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
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StageAlbumArt(
                    art,
                    Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                    RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song?.title ?: tr("暂无歌曲", "No song"),
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MoreInk,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoreMiniBadge(
                            if (song?.pendingSearch == true) tr("待获取", "Pending")
                            else tr("本地", "Local")
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            song?.artist?.takeIf { it.isNotBlank() } ?: tr("未知歌手", "Unknown artist"),
                            fontSize = 11.sp, color = MoreSub,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MoreAccent)
                        .clickable { MusicPlayer.toggle() }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (MusicPlayer.isPlaying) tr("暂停", "Pause") else tr("播放", "Play"),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFF06130B)
                    )
                }
            }

            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    MoreQuickGrid(
                        onSleep = { sleepDialog = true },
                        onAdd = {
                            if (favorite) tip(tr("已在「我的收藏」中", "Already in favorites"))
                            else {
                                onToggleFavorite()
                                tip(tr("已加入「我的收藏」", "Added to favorites"))
                            }
                        },
                        onShare = {
                            onClose()
                            onShare()
                        },
                        onStyle = {
                            onClose()
                            onPlayerStyle()
                        },
                        onCast = { tip(tr("当前设备不支持投放", "Casting is unavailable on this device")) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    MoreRowItem(Icons.Outlined.Queue, tr("下一首播放", "Play next"),
                        hint = tr("当前歌播完后立刻播这首，不打乱列表", "Plays right after the current song"),
                        onClick = {
                            when {
                                song == null -> tip(tr("还没有选中歌曲", "No song selected"))
                                song.pendingSearch -> tip(tr("待获取的歌先拿到音源再插队", "Resolve the source first"))
                                else -> {
                                    MusicPlayer.playNext(song)
                                    tip(tr("已插队：「${song.title}」将在当前歌曲后播放", "Queued: ${song.title} plays next"))
                                    onClose()
                                }
                            }
                        })
                }
                item {
                    MoreRowItem(Icons.Outlined.Collections, tr("歌曲卡片", "Song card"),
                        hint = tr("封面 + 歌名 + 播放数据，可保存可分享", "Cover, title and play stats"),
                        onClick = {
                            onClose()
                            onShareCard()
                        })
                }
                item {
                    MoreRowItem(Icons.Outlined.DirectionsCar, tr("驾驶模式", "Driving mode"),
                        hint = tr("超大字极简播放页，一眼看清", "Big-type minimal player"),
                        onClick = {
                            onClose()
                            onDrivingMode()
                        })
                }
                item {
                    MoreRowItem(Icons.Outlined.Album, tr("专辑", "Album"),
                        song?.album?.takeIf { it.isNotBlank() } ?: tr("未知专辑", "Unknown album"),
                        onClick = { tip(song?.album?.takeIf { it.isNotBlank() } ?: tr("未知专辑", "Unknown album")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.Person, tr("歌手", "Artist"),
                        song?.artist?.takeIf { it.isNotBlank() } ?: tr("未知歌手", "Unknown artist"),
                        onClick = { tip(song?.artist?.takeIf { it.isNotBlank() } ?: tr("未知歌手", "Unknown artist")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.GraphicEq, tr("音质", "Audio quality"),
                        tr("标准", "Standard"), hint = tr("本地播放", "Local playback"), badge = true,
                        onClick = { tip(tr("当前为标准音质", "Standard quality")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.NotificationsActive, tr("设置铃声", "Set ringtone"),
                        hint = tr("跟随系统设置", "Use system settings"),
                        onClick = { tip(tr("请在系统设置中把本地歌曲设为铃声", "Set the song as ringtone in system settings")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.ShoppingCart, tr("单曲购买", "Buy this song"),
                        hint = tr("本地歌曲无需购买", "Local song, no purchase needed"),
                        onClick = { tip(tr("本地歌曲无需购买", "Local song, no purchase needed")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.ScreenRotation, tr("横屏模式", "Landscape"),
                        hint = tr("全屏横屏播放器", "Fullscreen landscape"),

                        onClick = { onClose(); onLandscape() })
                }
                item {
                    MoreRowItem(Icons.Outlined.Speed, tr("倍速", "Speed"), speedText,
                        onClick = { speedDialog = true })
                }
                item {
                    MoreRowItem(Icons.Outlined.Settings, tr("播放设置", "Playback settings"),
                        hint = stageEqLabel(),
                        onClick = { onClose(); onOpenSoundPanel() })
                }
                item {
                    MoreVolumeRow(
                        volume = volume,
                        maxVolume = maxVolume,
                        onVolume = { value ->
                            volume = value
                            audio?.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                        }
                    )
                }
                item {
                    MoreRowItem(Icons.Outlined.Whatshot, tr("加热歌曲", "Boost song"),
                        onClick = { tip(tr("加热功能即将开放", "Boost is coming soon")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.Mic, tr("伴唱模式", "Sing along"),
                        hint = tr("听伴奏", "Instrumental"),
                        onClick = { tip(tr("伴唱模式暂未支持", "Sing-along is not supported yet")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.Star, tr("赞赏", "Reward"),
                        onClick = { tip(tr("赞赏功能即将开放", "Reward is coming soon")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.WorkspacePremium, tr("会员礼品卡", "Gift card"),
                        hint = tr("送给 TA，共享好音乐", "Share music with friends"),
                        onClick = { tip(tr("当前版本没有会员体系", "No membership in this version")) })
                }
                item { MoreDivider() }
                item {
                    MoreRowItem(Icons.Outlined.AutoAwesome, tr("用AI唱", "Sing with AI"),
                        hint = tr("一键克隆你唱的版本", "Clone your voice"),
                        onClick = { tip(tr("AI 演唱功能即将开放", "AI singing is coming soon")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.Edit, tr("AI改编", "AI remix"),
                        onClick = { tip(tr("AI 改编功能即将开放", "AI remix is coming soon")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.Headphones, tr("AI伴听解读", "AI song story"),
                        onClick = { tip(tr("可在歌曲详情页查看歌曲故事", "Open the song story in Detail")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.Piano, tr("AI演奏家", "AI performer"),
                        hint = tr("试试变音听歌新玩法", "Try a new way to listen"),
                        onClick = { tip(tr("AI 演奏家即将开放", "AI performer is coming soon")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.Tune, tr("多音轨调节", "Multi-track"),
                        onClick = { onClose(); onOpenSoundPanel() })
                }
                item {
                    MoreRowItem(Icons.Outlined.PlayCircleOutline, tr("播放视频", "Play video"),
                        onClick = { tip(tr("当前歌曲没有关联视频", "No video for this song")) })
                }
                item { MoreDivider() }
                item {
                    MoreRowItem(Icons.Outlined.Spa, tr("音乐疗愈", "Music therapy"),
                        onClick = { tip(tr("音乐疗愈即将开放", "Music therapy is coming soon")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.Lyrics, tr("查看曲谱", "View score"),
                        onClick = { tip(tr("曲谱功能即将开放", "Score is coming soon")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.DirectionsCar, tr("驾驶模式", "Driving mode"),
                        onClick = { onClose(); onDrivingMode() })
                }
                item {
                    MoreRowItem(Lucide.History, tr("歌曲回忆", "Song memory"),
                        tr("累计播放 ${playCount} 次", "Played $playCount times"),
                        onClick = { tip(tr("累计播放 ${playCount} 次", "Played $playCount times")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.DirectionsRun, tr("运动模式", "Workout mode"),
                        onClick = { tip(tr("运动模式即将开放", "Workout mode is coming soon")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.SportsEsports, tr("游戏模式", "Game mode"),
                        hint = tr("大家都在玩跳一跳", "Play a quick game"),
                        onClick = { tip(tr("游戏模式即将开放", "Game mode is coming soon")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.Groups, tr("制作团队", "Production team"),
                        song?.artist?.takeIf { it.isNotBlank() } ?: tr("未知", "Unknown"),
                        onClick = { tip(song?.artist?.takeIf { it.isNotBlank() } ?: tr("未知", "Unknown")) })
                }
                item { MoreDivider() }
                item {
                    MoreRowItem(Icons.Outlined.Headphones, tr("一起听", "Listen together"),
                        onClick = { tip(tr("一起听功能即将开放", "Listen together is coming soon")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.Circle, tr("微信状态", "WeChat status"),
                        onClick = { tip(tr("微信状态同步即将开放", "WeChat status is coming soon")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.ChatBubbleOutline, tr("QQ状态", "QQ status"),
                        onClick = { tip(tr("QQ 状态同步即将开放", "QQ status is coming soon")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.Image, tr("歌词海报", "Lyric poster"),
                        onClick = { onClose(); onShare() })
                }
                item {
                    MoreRowItem(Icons.Outlined.MicNone, tr("唱这首歌", "Sing this song"),
                        onClick = { tip(tr("跟唱功能即将开放", "Sing-along recording is coming soon")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.DeleteOutline, tr("删除", "Delete"),
                        onClick = { deleteDialog = true })
                }
                item {
                    MoreRowItem(Icons.Outlined.ThumbDown, tr("不喜欢", "Not interested"),
                        onClick = { tip(tr("本地播放器暂不支持不喜欢反馈", "Feedback is not supported yet")) })
                }
                item {
                    MoreRowItem(Icons.Outlined.ReportProblem, tr("举报", "Report"),
                        onClick = { reportOpen = true })
                }
            }
        }
    }

    if (reportOpen) {
        LyricIssueSheet(
            mode = "report",
            song = listOf(song?.title, song?.artist)
                .filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
            onClose = { reportOpen = false }
        )
    }

    if (sleepDialog) {
        val minutes = listOf(0, 15, 30, 45, 60, 90)
        val left = MusicPlayer.sleepMinutesLeft()
        MusicOptionDialog(
            title = when {
                MusicPlayer.sleepAfterCurrent -> tr("睡眠定时（播完当前歌停）", "Sleep timer (stop after this song)")
                left > 0 -> tr("睡眠定时（还剩 $left 分钟）", "Sleep timer ($left min left)")
                else -> tr("睡眠定时", "Sleep timer")
            },
            options = listOf(
                tr("关闭", "Off"), tr("15 分钟", "15 min"), tr("30 分钟", "30 min"),
                tr("45 分钟", "45 min"), tr("60 分钟", "60 min"), tr("90 分钟", "90 min"),
                tr("播完当前歌再停", "After current song")
            ),
            selected = 0,
            onPick = { idx ->
                sleepDialog = false
                if (idx == minutes.size) {
                    MusicPlayer.setSleepAfterCurrent()
                    tip(tr("当前歌播完后自动停止", "Stops after this song"))
                } else {
                    MusicPlayer.setSleepTimer(minutes[idx])
                    tip(
                        if (minutes[idx] == 0) tr("睡眠定时已关闭", "Sleep timer off")
                        else tr("将在 ${minutes[idx]} 分钟后暂停播放", "Playback stops in ${minutes[idx]} min")
                    )
                }
            },
            onDismiss = { sleepDialog = false }
        )
    }

    if (speedDialog) {
        val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
        val labels = listOf("0.75x", tr("1.0x 正常", "1.0x Normal"), "1.25x", "1.5x", "2.0x")
        val selected = speeds.indexOfFirst { abs(it - MusicPlayer.playbackSpeed) < 0.01f }
            .coerceAtLeast(1)
        MusicOptionDialog(
            title = tr("播放倍速", "Playback speed"),
            options = labels,
            selected = selected,
            onPick = { idx ->
                MusicPlayer.setSpeed(speeds[idx])
                speedDialog = false
                tip(tr("倍速已切换：${labels[idx]}", "Speed: ${labels[idx]}"))
            },
            onDismiss = { speedDialog = false }
        )
    }

    if (deleteDialog && song != null) {
        MusicDialogShell(
            onDismiss = { deleteDialog = false },
            title = tr("删除这首歌？", "Delete this song?"),
            subtitle = tr(
                "导入或缓存歌曲会删除 App 内文件；系统媒体库歌曲只从本 App 隐藏，不会动手机里的原文件。",
                "Imported or cached songs are removed from the app. System songs are only hidden here; the original file is untouched."
            )
        ) {
            Spacer(Modifier.height(2.dp))
            MusicDialogActions(
                primaryLabel = tr("删除", "Delete"),
                onPrimary = {
                    val removed = MusicPlayer.deleteSong(ctx, song.id)
                    deleteDialog = false
                    onClose()
                    tip(if (removed) tr("已删除", "Deleted") else tr("删除失败", "Delete failed"))
                },
                onDismiss = { deleteDialog = false },
                primaryDanger = true
            )
        }
    }
}

@Composable
private fun MoreMiniBadge(label: String) {
    Box(
        Modifier.clip(RoundedCornerShape(4.dp)).background(MoreAccent)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            label, fontSize = 8.sp, fontWeight = FontWeight.Bold,
            color = Color(0xFF06130B), maxLines = 1
        )
    }
}

@Composable
private fun MoreQuickGrid(
    onSleep: () -> Unit,
    onAdd: () -> Unit,
    onShare: () -> Unit,
    onStyle: () -> Unit,
    onCast: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        MoreQuickAction(Icons.Outlined.Schedule, tr("定时关闭", "Sleep timer"), Modifier.weight(1f), onSleep)
        MoreQuickAction(Icons.Outlined.PlaylistAdd, tr("加到歌单", "Add to list"), Modifier.weight(1f), onAdd)
        MoreQuickAction(Icons.Outlined.Share, tr("分享", "Share"), Modifier.weight(1f), onShare)
        MoreQuickAction(Icons.Outlined.Style, tr("播放器样式", "Player style"), Modifier.weight(1f), onStyle)
        MoreQuickAction(Icons.Outlined.Cast, tr("设备投放", "Cast"), Modifier.weight(1f), onCast)
    }
}

@Composable
private fun MoreQuickAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MoreInk, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label, fontSize = 11.sp, color = MoreInk,
            maxLines = 1, softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun MoreRowItem(
    icon: ImageVector,
    title: String,
    value: String = "",
    hint: String = "",
    badge: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MoreInk, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Text(
            title, fontSize = 15.sp, color = MoreInk,
            fontWeight = FontWeight.Medium, maxLines = 1
        )
        if (value.isNotBlank()) {
            Text(
                "：$value", fontSize = 15.sp, color = MoreInk,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (badge) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.WorkspacePremium, null, tint = MoreAccent,
                modifier = Modifier.size(15.dp)
            )
        }
        if (hint.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                hint, fontSize = 11.sp, color = MoreSub,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(2.dp))
        Icon(
            Icons.Outlined.ChevronRight, null, tint = Color(0xFFC3C8C5),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun MoreVolumeRow(
    volume: Int,
    maxVolume: Int,
    onVolume: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.VolumeUp, null, tint = MoreInk, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Text(
            tr("调节音量", "Volume"), fontSize = 15.sp, color = MoreInk,
            fontWeight = FontWeight.Medium, maxLines = 1
        )
        Slider(
            value = volume.toFloat(),
            onValueChange = { onVolume(it.toInt().coerceIn(0, maxVolume)) },
            valueRange = 0f..maxVolume.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MoreInk,
                inactiveTrackColor = Color(0xFFD4D8D6)
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        )
        Text(
            "$volume", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MoreInk,
            modifier = Modifier.width(24.dp)
        )
    }
}

@Composable
private fun MoreDivider() {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .height(1.dp).background(MoreDividerColor)
    )
}

