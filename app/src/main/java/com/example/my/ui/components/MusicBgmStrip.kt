package com.example.my.ui.components

import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.music.BgmPlayback
import com.example.my.data.music.LocalSong
import com.example.my.data.music.LrcLine
import com.example.my.data.music.LyricsRepository
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Ink
import com.example.my.ui.theme.TypeScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 足迹配乐播放器：整条就是播放进度，没有独立的播放/暂停按钮。
 *
 * · 点一下整条 = 暂停 / 继续；播完再点从当前位置续播；
 * · **左右滑动 = 跳到歌曲对应时间**（拖动时显示目标时间与一团柔光，松手 seekTo）；
 * · 长按 = 挑配色与流光特效，全局生效；
 * · 没有进度条与把手，流光 / 粒子层负责表现播放状态；
 * · 上面一行歌名，小字是此刻唱到的歌词（没有歌词就退回歌手 / 状态提示）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicBgmStrip(
    path: String,
    title: String,
    artist: String?,
    modifier: Modifier = Modifier,
    /** 有视频的足迹先不抢跑，等视频播完再让配乐接上 */
    autoplay: Boolean = true
) {
    val context = LocalContext.current
    val resumed = rememberIsResumed()

    // 配色是全局的：进页面读一次上次的选择，选完立刻写回，所有足迹的播放器一起变
    LaunchedEffect(Unit) {
        PlayerStyleStore.init(context)
        PlayerMotionStore.init(context)
    }
    val style = PlayerStyleStore.current
    val motion = PlayerMotionStore.current
    var pickerOpen by remember(path) { mutableStateOf(false) }

    // 单音源：所有配乐界面共用 BgmPlayback 里那一个 MediaPlayer，这里只是它的视图。
    // 以前每个界面各自 new 一个播放器，同一首歌会叠好几层声音。
    val owner = remember { BgmPlayback.newOwnerId("strip") }
    val playing = BgmPlayback.isPlaying
    val positionMs = BgmPlayback.positionMs
    val durationMs = BgmPlayback.durationMs
    var lyricLines by remember(path, title, artist) { mutableStateOf<List<LrcLine>>(emptyList()) }

    // 拖动 seek：null 表示当前没在拖，显示真实播放进度
    var scrubFraction by remember(path) { mutableStateOf<Float?>(null) }
    var wasPlayingBeforeScrub by remember(path) { mutableStateOf(false) }
    var wasCompletedBeforeScrub by remember(path) { mutableStateOf(false) }
    var lastDragEndMs by remember(path) { mutableLongStateOf(0L) }

    DisposableEffect(owner) {
        onDispose { BgmPlayback.detach(owner) }
    }

    LaunchedEffect(path, autoplay) {
        // 同一 path 只登记持有者：绝不重建播放器，也不会把刚按下的暂停改回播放
        BgmPlayback.attach(owner, path, looping = false, autoplay = autoplay)
    }

    // 前后台只暂停/恢复那一个实例，不销毁重建
    LaunchedEffect(resumed) {
        if (resumed) BgmPlayback.resumeFromBackground()
        else BgmPlayback.pauseForBackground()
    }

    // 歌词只为「已经知道时长」的曲子查：本地 .lrc / 之前缓存过的直接拿到，
    // 都没有才联网（失败就保持歌手名，不编造歌词）
    LaunchedEffect(path, title, artist, durationMs > 0) {
        if (durationMs <= 0L || title.isBlank()) return@LaunchedEffect
        lyricLines = withContext(Dispatchers.IO) {
            LyricsRepository.init(context)
            val song = LocalSong(
                id = 0L,
                title = title,
                artist = artist.orEmpty(),
                album = "",
                durationMs = durationMs,
                uri = Uri.fromFile(File(path)),
                path = path
            )
            val result = runCatching { LyricsRepository.fetch(song) }.getOrNull()
            result?.lines?.takeIf { it.isNotEmpty() }
                ?: result?.plain?.let { LyricsRepository.estimateLines(it, durationMs) }
                ?: emptyList()
        }
    }

    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    // 拖动时进度直接跟手，不做补间；平时做一点平滑，避免每 200ms 跳一格
    val animatedFill by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 260, easing = LinearEasing),
        label = "bgmFill"
    )
    val shownFraction = scrubFraction ?: progress
    val fill = if (scrubFraction != null) scrubFraction!! else animatedFill
    val percent = (shownFraction * 100).roundToInt()
    val targetMs = (shownFraction * durationMs).toLong().coerceIn(0L, durationMs)

    val currentLine = lyricLines.lastOrNull { it.timeMs <= positionMs }?.text?.takeIf { it.isNotBlank() }
    val subtitle = when {
        // 拖动中把「跳到几分几秒」直接写出来，松手前就能确认落点
        scrubFraction != null -> tr("跳到 ", "Seek ") +
            formatClock(targetMs) + " / " + formatClock(durationMs)
        // 没有单独的播放/暂停按钮，暂停状态只能靠这行字说清楚
        !playing -> tr("已暂停 · 点一下继续", "Paused · tap to resume")
        else -> currentLine
            ?: artist?.takeIf { it.isNotBlank() }
            ?: tr("正在播放", "Now playing")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {
                    // 拖完抬手这一下不算点按，别把刚跳好的位置又暂停掉
                    if (SystemClock.elapsedRealtime() - lastDragEndMs < 250L) return@combinedClickable
                    if (BgmPlayback.path != path) {
                        // 另一个界面换了歌：先把自己这首绑回去，下一次点按才是暂停
                        BgmPlayback.attach(owner, path, looping = false, autoplay = true)
                    } else {
                        BgmPlayback.toggle()
                    }
                },
                onLongClick = {
                    // 正在拖动时不弹样式面板，避免慢速滑动被判成长按
                    if (scrubFraction == null && SystemClock.elapsedRealtime() - lastDragEndMs >= 250L) {
                        pickerOpen = true
                    }
                }
            )
            .pointerInput(path, durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (durationMs <= 0L || size.width <= 0) return@detectHorizontalDragGestures
                        wasPlayingBeforeScrub = playing
                        wasCompletedBeforeScrub = !playing && positionMs >= durationMs - 200L
                        scrubFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        val f = scrubFraction
                        if (f != null && durationMs > 0L) {
                            val seekToMs = (f * durationMs).roundToInt().coerceIn(0, durationMs.toInt())
                            BgmPlayback.seekTo(seekToMs)
                            // 原本在播、或者播完了往回拖：松手就从这个位置继续出声
                            if (wasPlayingBeforeScrub || wasCompletedBeforeScrub) {
                                BgmPlayback.ensurePlaying()
                            }
                        }
                        scrubFraction = null
                        lastDragEndMs = SystemClock.elapsedRealtime()
                    },
                    onDragCancel = {
                        scrubFraction = null
                        lastDragEndMs = SystemClock.elapsedRealtime()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (durationMs > 0L && size.width > 0) {
                            scrubFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawPlayerChrome(style, fill)
        }
        // 流光 / 粒子盖在底图之上、文字之下；拖动时把落点交给一团柔光，不画线和圆点
        PlayerMotionLayer(
            style = style,
            motion = motion,
            playing = playing,
            progress = fill,
            focus = scrubFraction,
            modifier = Modifier.fillMaxSize()
        )

        Row(
            Modifier.fillMaxSize().padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title.ifBlank { tr("配乐", "Soundtrack") },
                    color = Ink.OnCover,
                    fontSize = TypeScale.subtitle,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = Ink.OnCoverSub,
                    fontSize = TypeScale.tiny,
                    letterSpacing = 0.6.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$percent",
                    color = Ink.OnCover,
                    fontSize = TypeScale.hero,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    "%",
                    color = Ink.OnCoverSub,
                    fontSize = TypeScale.body,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 1.dp, bottom = 6.dp)
                )
            }
            Spacer(Modifier.width(2.dp))
        }
    }

    if (pickerOpen) {
        PlayerStylePickerSheet(
            percent = percent,
            title = title.ifBlank { tr("配乐", "Soundtrack") },
            subtitle = subtitle,
            current = style,
            onPick = { picked ->
                PlayerStyleStore.select(context, picked)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
            currentMotion = motion,
            onPickMotion = { picked -> PlayerMotionStore.select(context, picked) }
        )
    }
}

/** 播放时间：1:05 / 3:48 */
private fun formatClock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return String.format(Locale.US, "%d:%02d", total / 60L, total % 60L)
}
