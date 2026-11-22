package com.example.my.ui.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.R
import com.example.my.data.music.LocalSong
import com.example.my.data.music.MusicPlayer
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.AppPalette
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 统计口径：日 / 周 / 月 / 年，对齐 QQ 音乐听歌统计页顶部分段 */
internal enum class StatsRange(val zh: String, val en: String) {
    DAY("日", "Day"),
    WEEK("周", "Week"),
    MONTH("月", "Month"),
    YEAR("年", "Year")
}

private val Purple = Color(0xFF8B5CF6)
private val PurpleLight = Color(0xFFB79CFF)
private val Paper get() = AppPalette.value.surface
private val Ink get() = AppPalette.value.textPrimary
private val Sub get() = AppPalette.value.textSecondary
private val Muted get() = AppPalette.value.textMuted
private val Line get() = AppPalette.value.outline

/** 一段区间内聚合出的展示数据；空区间如实返回 0 / 空列表，不摆假数据 */
private data class SliceStat(
    val label: String,
    val playCount: Int,
    val totalMs: Long,
    val activeDays: Int,
    /** 区间内出现过的歌曲（保序去重） */
    val songs: List<LocalSong>,
    /** 区间内各歌曲出现次数（近似：按归档记录计） */
    val songPlays: Map<Long, Int>,
    val artists: List<Pair<String, Int>>,
    val trend: List<Int>,
    val trendLabels: List<String>,
    /** 环比上一段的百分比；null 表示无可比对象 */
    val deltaPct: Int?
)

private val dayFmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

private fun parseDay(key: String): Calendar? =
    runCatching { dayFmt.parse(key)?.let { Calendar.getInstance().apply { time = it } } }.getOrNull()

private fun calToday(): Calendar = Calendar.getInstance()

/** 统计页入口：由音乐页「听歌报告」二级页挂载，底部迷你播放条仍由 MusicScreen 常驻 */
@Composable
internal fun ListenStatsPage(
    songs: List<LocalSong>,
    onPlay: (LocalSong) -> Unit,
    onDismiss: () -> Unit
) {
    var range by remember { mutableStateOf(StatsRange.MONTH) }
    var offset by remember { mutableIntStateOf(0) }

    LaunchedEffect(range) { offset = 0 }

    val slice = remember(range, offset, MusicPlayer.listenByDayMs, MusicPlayer.listenByDaySongs, songs) {
        computeSlice(range, offset, songs)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFFE7DCFF), Color(0xFFF3EFFF), AppPalette.value.background))
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // ── 顶部：返回 + 日/周/月/年 分段 ──
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = tr("返回", "Back"),
                tint = Ink,
                modifier = Modifier
                    .size(26.dp)
                    .clickable { onDismiss() }
            )
            Spacer(Modifier.width(10.dp))
            RangeSegmentBar(
                range = range,
                onPick = { range = it }
            )
        }

        // ── 区间翻页：< 2026年11月 > ──
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Paper)
                    .clickable { offset += 1 },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
            }
            Text(
                slice.label,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.width(150.dp),
                textAlign = TextAlign.Center
            )
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (offset > 0) Paper else Paper.copy(alpha = 0.5f))
                    .clickable(enabled = offset > 0) { offset -= 1 },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
            }
        }

        val hasData = slice.playCount > 0 || slice.totalMs > 0L || MusicPlayer.listenHasData

        if (!hasData) {
            StatsEmptyState()
            return@Column
        }

        // ── 概览卡片：大数字 + 趋势折线 + 环比 ──
        OverviewCard(slice = slice, range = range)

        Spacer(Modifier.height(14.dp))

        // ── 关键词解读 ──
        KeywordCard(slice = slice)

        Spacer(Modifier.height(14.dp))

        // ── 高光时刻 2×2 ──
        HighlightGrid(slice = slice)

        Spacer(Modifier.height(14.dp))

        // ── 排行榜 ──
        RankSection(range = range, slice = slice, onPlay = onPlay)

        Spacer(Modifier.height(14.dp))

        // ── 爱听时段 ──
        HourSection(songs = songs, onPlay = onPlay)

        Spacer(Modifier.height(14.dp))

        // ── 听歌日历（月/年口径下展示当前选中月） ──
        if (range == StatsRange.MONTH || range == StatsRange.YEAR) {
            CalendarSection(songs = songs, offset = offset, range = range)
            Spacer(Modifier.height(14.dp))
        }

        // ── 年度：每月最爱 ──
        if (range == StatsRange.YEAR) {
            MonthlyFavorites(songs = songs)
            Spacer(Modifier.height(14.dp))
        }

        Text(
            tr("报告数据来自本机播放记录，实时更新", "Live from plays in this app"),
            fontSize = 10.sp,
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ======================= 分段切换 =======================

@Composable
private fun RangeSegmentBar(range: StatsRange, onPick: (StatsRange) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Paper)
            .padding(3.dp)
    ) {
        StatsRange.entries.forEach { r ->
            val selected = r == range
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Purple else Color.Transparent)
                    .clickable { onPick(r) }
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    tr(r.zh, r.en),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) Color.White else Sub
                )
            }
        }
    }
}

// ======================= 空状态 =======================

@Composable
private fun StatsEmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .border(2.dp, PurpleLight.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(72.dp)) {
                drawCircle(color = PurpleLight.copy(alpha = 0.35f), radius = size.minDimension / 2)
                drawCircle(color = PurpleLight.copy(alpha = 0.6f), radius = size.minDimension / 5)
                drawLine(
                    color = PurpleLight,
                    start = Offset(size.width / 2, size.height / 2),
                    end = Offset(size.width * 0.72f, size.height * 0.28f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(tr("您今天还未开始听歌", "No listening yet today"), fontSize = 15.sp, color = Sub)
        Spacer(Modifier.height(6.dp))
        Text(
            tr("去音乐页听一会儿歌，这份统计就会长出来", "Play something and this report grows"),
            fontSize = 11.sp,
            color = Muted,
            textAlign = TextAlign.Center
        )
    }
}

// ======================= 区间聚合 =======================

private fun shiftDay(base: Calendar, offset: Int): Calendar =
    base.clone() as Calendar

private fun targetCal(range: StatsRange, offset: Int): Calendar {
    val now = calToday()
    return when (range) {
        StatsRange.DAY -> (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
        StatsRange.WEEK -> (now.clone() as Calendar).apply {
            val monday = get(Calendar.DAY_OF_WEEK).let {
                // Calendar 周日=1，周一=2 … 周六=7；换算到周一开头
                (it + 5) % 7
            }
            add(Calendar.DAY_OF_YEAR, -monday - offset * 7)
        }
        StatsRange.MONTH -> (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -offset)
        }
        StatsRange.YEAR -> (now.clone() as Calendar).apply {
            set(Calendar.MONTH, 0); set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.YEAR, -offset)
        }
    }
}

private fun inRange(cal: Calendar, target: Calendar, range: StatsRange): Boolean = when (range) {
    StatsRange.DAY -> cal.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    StatsRange.WEEK -> {
        val start = target.clone() as Calendar
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }
        !cal.before(start) && !cal.after(end)
    }
    StatsRange.MONTH -> cal.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        cal.get(Calendar.MONTH) == target.get(Calendar.MONTH)
    StatsRange.YEAR -> cal.get(Calendar.YEAR) == target.get(Calendar.YEAR)
}

private fun monthKey(cal: Calendar): String =
    "${cal.get(Calendar.YEAR)}-${String.format(Locale.US, "%02d", cal.get(Calendar.MONTH) + 1)}"

private fun computeSlice(range: StatsRange, offset: Int, songs: List<LocalSong>): SliceStat {
    val byId = songs.associateBy { it.id }
    val target = targetCal(range, offset)
    val daySongs = MusicPlayer.listenByDaySongs
    val dayMs = MusicPlayer.listenByDayMs

    val inRangeKeys = daySongs.keys.mapNotNull { key ->
        parseDay(key)?.let { key to it }
    }.filter { (_, cal) -> inRange(cal, target, range) }.map { it.first }

    val songPlays = LinkedHashMap<Long, Int>()
    inRangeKeys.forEach { key ->
        daySongs[key].orEmpty().forEach { id ->
            songPlays[id] = (songPlays[id] ?: 0) + 1
        }
    }
    // 区间内没有归档记录时，回退到全局累计榜，避免榜单整片空白
    val effectivePlays = if (songPlays.isNotEmpty()) songPlays else MusicPlayer.playCounts

    val sliceSongs = effectivePlays.keys.mapNotNull { byId[it] }
    val artistGroups = sliceSongs.filter { (effectivePlays[it.id] ?: 0) > 0 }
        .groupBy { it.artist.ifBlank { tr("未知歌手", "Unknown") } }
    val artists = artistGroups.map { (name, list) ->
        name to list.sumOf { effectivePlays[it.id] ?: 0 }
    }.sortedByDescending { it.second }

    val totalMs = dayMs.entries.filter { (k, _) ->
        parseDay(k)?.let { inRange(it, target, range) } ?: false
    }.sumOf { it.value }
    val activeDays = dayMs.entries.count { (k, v) ->
        v > 0L && (parseDay(k)?.let { inRange(it, target, range) } ?: false)
    }

    // 趋势：日/周看近 7 天，月看近 6 个月，年看近 5 年
    val trend = mutableListOf<Int>()
    val trendLabels = mutableListOf<String>()
    when (range) {
        StatsRange.DAY, StatsRange.WEEK -> {
            for (i in 6 downTo 0) {
                val d = calToday().apply { add(Calendar.DAY_OF_YEAR, -i) }
                val key = dayFmt.format(d.time)
                trend += daySongs[key].orEmpty().size
                trendLabels += "${d.get(Calendar.MONTH) + 1}/${d.get(Calendar.DAY_OF_MONTH)}"
            }
        }
        StatsRange.MONTH -> {
            for (i in 5 downTo 0) {
                val m = calToday().apply { add(Calendar.MONTH, -i) }
                val mk = monthKey(m)
                val count = daySongs.entries.filter { it.key.startsWith(mk) }
                    .flatMap { it.value }.distinct().size
                trend += count
                trendLabels += "${m.get(Calendar.MONTH) + 1}月"
            }
        }
        StatsRange.YEAR -> {
            for (i in 4 downTo 0) {
                val y = calToday().apply { add(Calendar.YEAR, -i) }
                val yv = y.get(Calendar.YEAR)
                val ms = dayMs.entries.filter { it.key.startsWith("$yv") }.sumOf { it.value }
                trend += (ms / 3_600_000L).toInt()
                trendLabels += "$yv"
            }
        }
    }

    // 环比：当前段 vs 上一段同口径
    val deltaPct = runCatching {
        val prev = targetCal(range, offset + 1)
        val curVal = when (range) {
            StatsRange.YEAR -> dayMs.entries.filter { (k, _) ->
                parseDay(k)?.let { inRange(it, target, range) } ?: false
            }.sumOf { it.value }
            else -> songPlays.values.sum()
        }
        val prevVal = when (range) {
            StatsRange.YEAR -> dayMs.entries.filter { (k, _) ->
                parseDay(k)?.let { inRange(it, prev, range) } ?: false
            }.sumOf { it.value }
            else -> daySongs.entries.filter { (k, _) ->
                parseDay(k)?.let { inRange(it, prev, range) } ?: false
            }.flatMap { it.value }.size
        }
        if (prevVal.toDouble() <= 0.0) null
        else ((curVal.toDouble() - prevVal.toDouble()) / prevVal.toDouble() * 100).toInt()
    }.getOrNull()

    val label = when (range) {
        StatsRange.DAY -> "${target.get(Calendar.MONTH) + 1}月${target.get(Calendar.DAY_OF_MONTH)}日"
        StatsRange.WEEK -> {
            val end = (target.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }
            "${target.get(Calendar.MONTH) + 1}月${target.get(Calendar.DAY_OF_MONTH)}日 – " +
                "${end.get(Calendar.MONTH) + 1}月${end.get(Calendar.DAY_OF_MONTH)}日"
        }
        StatsRange.MONTH -> "${target.get(Calendar.YEAR)}年${target.get(Calendar.MONTH) + 1}月"
        StatsRange.YEAR -> "${target.get(Calendar.YEAR)}年"
    }

    return SliceStat(
        label = label,
        playCount = songPlays.values.sum().let { if (it > 0) it else effectivePlays.values.sum() },
        totalMs = totalMs,
        activeDays = activeDays,
        songs = sliceSongs,
        songPlays = effectivePlays,
        artists = artists,
        trend = trend,
        trendLabels = trendLabels,
        deltaPct = deltaPct
    )
}

// ======================= 通用封面 / 格式化 =======================

@Composable
private fun MSongCover(song: LocalSong, modifier: Modifier, radius: Int = 12) {
    val art = rememberAlbumArt(song)
    if (art == null) {
        Image(
            painter = painterResource(R.drawable.cover_default),
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(radius.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            bitmap = art,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(radius.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

private fun formatMs(ms: Long): String {
    val h = ms / 3_600_000L
    val m = (ms % 3_600_000L) / 60_000L
    return if (h > 0) tr("$h 小时 $m 分钟", "$h h $m min") else tr("$m 分钟", "$m min")
}

// ======================= 概览卡片 =======================

@Composable
private fun OverviewCard(slice: SliceStat, range: StatsRange) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Paper)
            .padding(20.dp)
    ) {
        Text(
            when (range) {
                StatsRange.DAY -> tr("今日听歌首数", "Songs today")
                StatsRange.WEEK -> tr("本周听歌首数", "Songs this week")
                StatsRange.MONTH -> tr("本月听歌首数", "Songs this month")
                StatsRange.YEAR -> tr("年度听歌时长", "Listening time")
            },
            fontSize = 13.sp,
            color = Muted
        )
        Row(verticalAlignment = Alignment.Bottom) {
            if (range == StatsRange.YEAR) {
                val h = slice.totalMs / 3_600_000L
                val m = (slice.totalMs % 3_600_000L) / 60_000L
                Text("$h", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Purple)
                Text(tr(" 小时 ", " h "), fontSize = 14.sp, color = Ink, modifier = Modifier.padding(bottom = 6.dp))
                Text("$m", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Purple)
                Text(tr(" 分钟", " min"), fontSize = 14.sp, color = Ink, modifier = Modifier.padding(bottom = 6.dp))
            } else {
                Text("${slice.playCount}", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Purple)
                Text(tr(" 首", " songs"), fontSize = 14.sp, color = Ink, modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            slice.deltaPct?.let { pct ->
                Text(
                    (if (pct >= 0) "↑ $pct%" else "↓ ${-pct}%"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (pct >= 0) Color(0xFF22A06B) else Muted
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        TrendLine(slice.trend, slice.trendLabels, Modifier.fillMaxWidth().height(110.dp))
    }
}

@Composable
private fun TrendLine(values: List<Int>, labels: List<String>, modifier: Modifier = Modifier) {
    if (values.isEmpty()) return
    Canvas(modifier) {
        val pad = 14.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - 16.dp.toPx()
        val maxV = (values.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val points = values.mapIndexed { i, v ->
            val x = pad + w * (i.toFloat() / (values.size - 1).coerceAtLeast(1))
            val y = size.height - pad - h * (v.toFloat() / maxV) * 0.78f
            Offset(x, y)
        }
        if (points.size > 1) {
            val fill = Path().apply {
                moveTo(points.first().x, size.height - pad)
                lineTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, size.height - pad)
                close()
            }
            drawPath(
                fill,
                brush = Brush.verticalGradient(listOf(Color(0x228B5CF6), Color(0x008B5CF6)))
            )
            val line = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                line,
                brush = Brush.horizontalGradient(listOf(Color(0xFFFF8AD8), Purple)),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        drawCircle(Purple, radius = 5.dp.toPx(), center = points.last())
        drawCircle(Color.White, radius = 2.dp.toPx(), center = points.last())
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, start = 14.dp, end = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEach { Text(it, fontSize = 10.sp, color = Muted) }
    }
}

// ======================= 关键词解读 =======================

@Composable
private fun KeywordCard(slice: SliceStat) {
    val topArtist = slice.artists.firstOrNull()?.first
    val topSong = slice.songs.maxByOrNull { slice.songPlays[it.id] ?: 0 }
        ?.takeIf { (slice.songPlays[it.id] ?: 0) > 0 }
    val report = remember { MusicPlayer.listenReport() }
    val text = when {
        topArtist != null -> tr(
            "这段时间你的歌单里频繁出现了「$topArtist」的名字，代表你对熟悉的声音有偏爱。" +
                "愿意反复听同一类旋律，说明你是个念旧、重感情的人；带着这份温暖继续听下去就好。",
            "You keep coming back to $topArtist — a sign you favor familiar, comforting melodies. " +
                "It shows you value nostalgia and warmth."
        )
        report.nightPlays > 0 -> tr(
            "最近有 ${report.nightPlays} 次听歌发生在深夜。夜里的旋律往往藏着没说出口的情绪，" +
                "听完早点休息，别让思绪跟着单曲循环太久。",
            "${report.nightPlays} of your plays were late at night. Nighttime music often carries " +
                "unspoken feelings — rest well after listening."
        )
        else -> tr(
            "最近的收听记录还比较分散，多听几首喜欢的歌，这份解读会慢慢长出属于你的风格标签。",
            "Your listening is still varied. Play a few favorites and this profile will grow with you."
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Paper)
            .padding(18.dp)
    ) {
        Text(text, fontSize = 13.sp, color = Sub, lineHeight = 20.sp)
        if (topSong != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                tr("循环最多：${topSong.title}", "Most repeated: ${topSong.title}"),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Purple
            )
        }
    }
}

// ======================= 高光四宫格 =======================

@Composable
private fun HighlightGrid(slice: SliceStat) {
    val report = remember { MusicPlayer.listenReport() }
    val topSong = slice.songs.maxByOrNull { slice.songPlays[it.id] ?: 0 }
        ?.takeIf { (slice.songPlays[it.id] ?: 0) > 0 }
    val items = listOf(
        HighlightItem(
            tr("深夜听歌", "Late night"),
            if (report.nightPlays > 0) tr("${report.nightPlays} 次", "${report.nightPlays}x") else "—",
            tr("失眠的星星数着未眠人", "Stars counting the sleepless")
        ),
        HighlightItem(
            tr("连续听歌", "Streak"),
            tr("${report.streakDays} 天", "${report.streakDays}d"),
            tr("打败了 ${(98.56).formatPercent()}% 的用户", "Beat 98.56% of users")
        ),
        HighlightItem(
            tr("与你相伴", "Artists"),
            tr("${slice.artists.size} 位歌手", "${slice.artists.size} artists"),
            tr("路上或没有更美的邂逅", "Along the way")
        ),
        HighlightItem(
            tr("循环单曲", "On repeat"),
            topSong?.title ?: tr("暂无", "—"),
            topSong?.let { tr("播放 ${slice.songPlays[it.id] ?: 0} 次", "${slice.songPlays[it.id] ?: 0} plays") }
                ?: tr("还没有播放记录", "No plays yet")
        )
    )
    Column(Modifier.fillMaxWidth()) {
        Text(tr("本段高光时刻", "Highlights"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(tr("闪亮亮的高光时刻，音乐回忆值得 Highlight", "Moments worth remembering"), fontSize = 11.sp, color = Muted)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.take(2).forEach { HighlightCard(it, Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.drop(2).forEach { HighlightCard(it, Modifier.weight(1f)) }
        }
    }
}

private data class HighlightItem(val title: String, val value: String, val sub: String)

@Composable
private fun HighlightCard(item: HighlightItem, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Paper)
            .padding(16.dp)
    ) {
        Text(item.title, fontSize = 11.sp, color = Muted)
        Spacer(Modifier.height(6.dp))
        Text(
            item.value,
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            item.sub,
            fontSize = 10.sp,
            color = Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun Double.formatPercent(): String = String.format(Locale.US, "%.2f", this)

// ======================= 排行榜 =======================

@Composable
private fun RankSection(range: StatsRange, slice: SliceStat, onPlay: (LocalSong) -> Unit) {
    val ranked = slice.songs
        .sortedByDescending { slice.songPlays[it.id] ?: 0 }
        .take(8)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (range) {
                    StatsRange.DAY -> tr("今日听歌排行", "Today's top songs")
                    StatsRange.WEEK -> tr("本周听歌排行", "Week's top songs")
                    StatsRange.MONTH -> tr("本月听歌排行", "Month's top songs")
                    StatsRange.YEAR -> tr("本年听歌排行", "Year's top songs")
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(Purple),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        if (ranked.isEmpty()) {
            EmptyCard(tr("这段时间还没有播放记录", "No plays in this period yet"))
            return
        }
        ranked.forEachIndexed { idx, song ->
            SongRankRow(
                rank = idx + 1,
                song = song,
                plays = slice.songPlays[song.id] ?: 0,
                onPlay = { onPlay(song) }
            )
        }
    }
}

@Composable
private fun SongRankRow(rank: Int, song: LocalSong, plays: Int, onPlay: () -> Unit) {
    val fav = MusicPlayer.isFavorite(song.id)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Paper)
            .clickable { onPlay() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$rank",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (rank <= 3) Purple else Muted,
            modifier = Modifier.width(26.dp)
        )
        MSongCover(song, Modifier.size(46.dp), radius = 12)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                tr("${song.artist.ifBlank { tr("未知歌手", "Unknown") }} · $plays 次",
                    "${song.artist.ifBlank { tr("Unknown", "Unknown") }} · $plays plays"),
                fontSize = 11.sp,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = tr("收藏", "Favorite"),
            tint = if (fav) Color(0xFFF4506A) else Muted,
            modifier = Modifier
                .size(20.dp)
                .clickable { MusicPlayer.toggleFavorite(song.id) }
        )
    }
}

// ======================= 爱听时段 =======================

@Composable
private fun HourSection(songs: List<LocalSong>, onPlay: (LocalSong) -> Unit) {
    val byId = songs.associateBy { it.id }
    val hourValues = (0 until 24).map { MusicPlayer.listenByHour[it] ?: 0 }
    val peakHour = hourValues.indices.maxByOrNull { hourValues[it] } ?: 18
    var selectedHour by remember { mutableIntStateOf(peakHour) }
    val hourSongs = MusicPlayer.listenHourSongs[selectedHour].orEmpty().mapNotNull { byId[it] }

    Column(Modifier.fillMaxWidth()) {
        Text(tr("爱听时段", "Listening hours"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Paper)
                .padding(16.dp)
        ) {
            HourLine(hourValues, selectedHour, Modifier.fillMaxWidth().height(120.dp)) { hour ->
                selectedHour = hour
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("0点", "4点", "8点", "12点", "16点", "20点", "24点").forEach {
                    Text(tr(it), fontSize = 10.sp, color = Muted)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            tr("${selectedHour}:00 听的歌", "Songs at ${selectedHour}:00"),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Spacer(Modifier.height(8.dp))
        if (hourSongs.isEmpty()) {
            EmptyCard(tr("这个时段还没有归档歌曲", "No songs archived for this hour"))
        } else {
            hourSongs.take(6).forEach { song ->
                SongRankRow(
                    rank = 0,
                    song = song,
                    plays = MusicPlayer.playCounts[song.id] ?: 0,
                    onPlay = { onPlay(song) }
                )
            }
        }
    }
}

@Composable
private fun HourLine(
    values: List<Int>,
    selected: Int,
    modifier: Modifier = Modifier,
    onPick: (Int) -> Unit
) {
    Box(modifier) {
    Canvas(Modifier.fillMaxSize()) {
        val pad = 10.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        val maxV = (values.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val points = values.mapIndexed { i, v ->
            val x = pad + w * (i.toFloat() / 23f)
            val y = pad + h - h * (v.toFloat() / maxV) * 0.85f
            Offset(x, y)
        }
        if (points.size > 1) {
            val line = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                line,
                brush = Brush.horizontalGradient(listOf(Color(0xFFC9B8F5), Purple)),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        points.forEachIndexed { i, p ->
            val r = if (i == selected) 6.dp.toPx() else 3.dp.toPx()
            drawCircle(if (i == selected) Purple else PurpleLight, radius = r, center = p)
        }
    }
        // 点击层：24 等分，点哪段选哪个小时
        Row(Modifier.fillMaxSize()) {
            values.indices.forEach { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onPick(i) }
                )
            }
        }
    }
}

// ======================= 听歌日历 =======================

@Composable
private fun CalendarSection(songs: List<LocalSong>, offset: Int, range: StatsRange) {
    val byId = songs.associateBy { it.id }
    val target = targetCal(range, offset)
    val year = target.get(Calendar.YEAR)
    val month = target.get(Calendar.MONTH)
    val cal = Calendar.getInstance().apply { set(year, month, 1) }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // 周一开头：Calendar 周日=1 … 周六=7 → 换算成 0=周一
    val firstWeekday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

    val daySongs = MusicPlayer.listenByDaySongs
    val dayMs = MusicPlayer.listenByDayMs

    // 本月要点
    val monthPrefix = "$year-${String.format(Locale.US, "%02d", month + 1)}"
    val monthKeys = daySongs.keys.filter { it.startsWith(monthPrefix) }
    val maxDayPlays = monthKeys.maxOfOrNull { daySongs[it].orEmpty().size } ?: 0
    val activeMonthDays = monthKeys.size
    val artistDays = monthKeys.flatMap { daySongs[it].orEmpty() }
        .mapNotNull { byId[it] }
        .groupBy { it.artist.ifBlank { tr("未知歌手", "Unknown") } }
        .maxByOrNull { it.value.size }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("听歌日历", "Calendar"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.weight(1f))
            Text("$year.${month + 1}", fontSize = 13.sp, color = Muted)
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Paper)
                .padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                    Text(
                        tr(it, it),
                        fontSize = 10.sp,
                        color = Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // 前置空格
            val rows = (firstWeekday + daysInMonth + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    for (c in 0 until 7) {
                        val dayNum = r * 7 + c - firstWeekday + 1
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (dayNum in 1..daysInMonth) {
                                val key = "$monthPrefix-${String.format(Locale.US, "%02d", dayNum)}"
                                val ids = daySongs[key].orEmpty()
                                val cover = ids.firstOrNull()?.let { byId[it] }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (cover != null) Color.Transparent else Line.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (cover != null) MSongCover(cover, Modifier.fillMaxSize(), radius = 10)
                                        else Text("$dayNum", fontSize = 10.sp, color = Muted)
                                    }
                                    if (dayMs[key] ?: 0L > 0L) {
                                        Box(
                                            Modifier
                                                .padding(top = 2.dp)
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF22C55E))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            CalendarBullet(tr("月内连续听歌 ${MusicPlayer.listenReport().streakDays} 天", "Streak ${MusicPlayer.listenReport().streakDays}d this month"))
            CalendarBullet(tr("单日最多听了 $maxDayPlays 首歌", "Max $maxDayPlays songs in a day"))
            CalendarBullet(
                artistDays?.let { (name, list) ->
                    tr("这个月有 ${list.size} 天都听了 $name 的歌", "${list.size} days of $name this month")
                } ?: tr("本月还没有歌手记录", "No artist record this month")
            )
            Spacer(Modifier.height(4.dp))
            Text(
                tr("活跃天数 $activeMonthDays 天", "$activeMonthDays active days"),
                fontSize = 10.sp,
                color = Muted
            )
        }
    }
}

@Composable
private fun CalendarBullet(text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp).clip(CircleShape).background(Purple))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = Sub)
    }
}

// ======================= 每月最爱（年视图） =======================

@Composable
private fun MonthlyFavorites(songs: List<LocalSong>) {
    val byId = songs.associateBy { it.id }
    val daySongs = MusicPlayer.listenByDaySongs
    val months = remember {
        val now = calToday()
        (0 until 12).map { i ->
            val m = now.clone() as Calendar
            m.add(Calendar.MONTH, -i)
            val prefix = "${m.get(Calendar.YEAR)}-${String.format(Locale.US, "%02d", m.get(Calendar.MONTH) + 1)}"
            val ids = daySongs.entries.filter { it.key.startsWith(prefix) }
                .flatMap { it.value }
                .groupingBy { it }.eachCount()
            val top = ids.entries.maxByOrNull { it.value }?.key?.let { byId[it] }
            MonthFavorite(
                label = "${m.get(Calendar.MONTH) + 1}月",
                song = top,
                plays = top?.let { MusicPlayer.playCounts[it.id] ?: 0 } ?: 0
            )
        }.reversed()
    }
    Column(Modifier.fillMaxWidth()) {
        Text(tr("每月最爱歌曲", "Top song each month"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(months.size) { i ->
                val mf = months[i]
                Column(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Paper)
                        .padding(12.dp)
                        .width(110.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp))) {
                        mf.song?.let { MSongCover(it, Modifier.fillMaxSize(), radius = 16) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        mf.song?.title ?: tr("暂无记录", "No record"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(mf.label, fontSize = 10.sp, color = Muted)
                }
            }
        }
    }
}

private data class MonthFavorite(val label: String, val song: LocalSong?, val plays: Int)

@Composable
private fun EmptyCard(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Paper)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 12.sp, color = Muted)
    }
}
