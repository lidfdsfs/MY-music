package com.example.my.ui.music

/**
 * 现代内容优先音乐 UI：清晰标题、统一蓝色点缀、轻量容器与真实封面。
 *
 * 这一层只负责「信息架构与外观」：所有播放、搜歌、榜单、歌词、收藏等能力仍然
 * 由 MusicScreen 里原有的状态与 data/music 单例提供，新页面只是把它们重新组织成
 * 5 tab / 10 页，功能一个不删。颜色全部走 AppPalette getter，深浅主题自动跟随。
 */
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.R
import androidx.compose.ui.platform.LocalContext
import com.example.my.data.music.ChartInfo
import com.example.my.data.music.LocalSong
import com.example.my.data.music.MetaHit
import com.example.my.data.music.MetadataSearch
import com.example.my.data.music.MusicPlayer
import com.example.my.data.music.SongResult
import com.example.my.data.music.SongSearch
import com.example.my.data.music.SongSearchHistory
import com.example.my.util.SearchUtils
import com.example.my.state.AppState
import com.example.my.ui.i18n.tr
import com.example.my.ui.interaction.pressBounceClickable
import com.example.my.ui.theme.AppPalette
import com.example.my.ui.theme.CoverPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 极简稿色板：全部映射主题语义 token，深浅色与自定义背景自动跟随 */
internal object MinimalUi {
    /** 唯一强调色：QQ 音乐绿。跟随主题，不写死 */
    val Blue get() = AppPalette.value.terracotta
    val BlueSoft get() = Blue.copy(alpha = 0.13f)
    val Bg get() = AppPalette.value.background
    val Card get() = AppPalette.value.surface
    val SurfaceVariant get() = AppPalette.value.surfaceVariant
    val Text get() = AppPalette.value.textPrimary
    val Sub get() = AppPalette.value.textSecondary
    val Muted get() = AppPalette.value.textMuted
    val Line get() = AppPalette.value.outline
    val SurfaceDim get() = AppPalette.value.surfaceDim
    /** QQ 音乐式卡片不描边，靠圆角与留白分层 */
    val Hairline get() = Color.Transparent
}

/** 5 tab 之外的内嵌二级页（同样替换内容区，不弹窗） */
internal enum class MinimalPage(val zh: String, val en: String) {
    SEARCH("搜索", "Search"),
    DAILY("每日30首", "Daily 30"),
    PLAYLISTS("歌单广场", "Playlist Square"),
    ARTISTS("歌手", "Artists"),
    LOCAL("本地与下载", "Local & Downloads"),
    SETTINGS("设置", "Settings"),
    PLAYLIST("播放列表", "Playlist"),
    LIBRARY("音乐库", "Library"),
    REPORT("听歌报告", "Listening report"),
    IMPORT("导入我的音乐", "Import my music")
}

private val MPageSide = 20.dp
private val MCardShape = RoundedCornerShape(18.dp)

/** 内容容器：QQ 音乐式大圆角白卡，不描边，层级靠浅灰底与留白。 */
@Composable
private fun MCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier
        .fillMaxWidth()
        .clip(MCardShape)
        .background(MinimalUi.Card)
    Column(
        if (onClick != null) base.pressBounceClickable(onClick = { onClick() }, pressedScale = 0.99f) else base,
        content = content
    )
}

/** 分区标题：大号加粗，右侧「更多」轻量，不再画分隔线。 */
@Composable
private fun MSectionTitle(text: String, more: String? = null, onMore: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
            color = MinimalUi.Text
        )
        Spacer(Modifier.weight(1f))
        if (more != null) {
            Row(
                if (onMore != null) {
                    Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .clickable { onMore() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                } else {
                    Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(more, fontSize = 12.sp, color = MinimalUi.Muted)
                if (onMore != null) {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MinimalUi.Muted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/** 统一位图封面/插图：圆角裁切，默认裁满容器 */
@Composable
private fun MArtImage(res: Int, modifier: Modifier = Modifier, radius: Int = 12) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(radius.dp)),
        contentScale = ContentScale.Crop
    )
}

/** 优先展示歌曲真实封面，缺失时仅使用同品牌色块，不放音符或默认插图。 */
@Composable
private fun MCover(song: LocalSong, modifier: Modifier, radius: Int = 12) {
    val art = rememberAlbumArt(song)
    if (art == null) {
        MAbstractCover(modifier = modifier, seed = song.id.hashCode(), radius = radius)
    } else {
        // 封面到位的瞬间：700ms 从 14dp 模糊收清，同时淡入，避免硬切
        var entered by remember(art) { mutableStateOf(false) }
        LaunchedEffect(art) {
            withFrameNanos { }
            entered = true
        }
        val blurRadius by animateFloatAsState(
            targetValue = if (entered) 0f else 14f,
            animationSpec = tween(700, easing = FastOutSlowInEasing),
            label = "coverBlurIn"
        )
        val fade by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(360),
            label = "coverFadeIn"
        )
        Image(
            bitmap = art,
            contentDescription = null,
            modifier = modifier
                .clip(RoundedCornerShape(radius.dp))
                .blur(blurRadius.dp)
                .alpha(fade),
            contentScale = ContentScale.Crop
        )
    }
}

/** 分类歌单封面：治愈 / 放松 / 电音 / 国风 */
private fun categoryCover(index: Int): Int = when (index % 4) {
    0 -> R.drawable.cover_calm
    1 -> R.drawable.cover_relax
    2 -> R.drawable.cover_edm
    else -> R.drawable.cover_guofeng
}

/** 无封面占位：QQ 音乐式彩色渐变块，种子只决定取哪两组色。 */
@Composable
private fun MAbstractCover(modifier: Modifier = Modifier, seed: Int = 0, radius: Int = 12) {
    val shape = RoundedCornerShape(radius.dp)
    val c1 = CoverPalette[Math.floorMod(seed, CoverPalette.size)]
    val c2 = CoverPalette[Math.floorMod(seed + 3, CoverPalette.size)]
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(c1, c2)))
    )
}

/** 快捷入口：小圆角图标块 + 单行标签，不再用大色块撑面积。 */
@Composable
private fun MQuickEntry(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .pressBounceClickable(onClick = { onClick() }, pressedScale = 0.96f)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MinimalUi.BlueSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MinimalUi.Blue, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MinimalUi.Sub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 导航行使用统一的轻底图标、双行信息和细分隔，适配所有二级页面。 */
@Composable
private fun MNavRow(icon: ImageVector, label: String, sub: String = "", onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MinimalUi.BlueSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MinimalUi.Blue, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MinimalUi.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(sub, fontSize = 11.sp, color = MinimalUi.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Outlined.ChevronRight, null, tint = MinimalUi.Muted, modifier = Modifier.size(18.dp))
    }
}

/** 带开关的列表行：设置页里成对的开关项共用 */
@Composable
private fun MSwitchRow(
    icon: ImageVector,
    label: String,
    sub: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MinimalUi.Sub, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = MinimalUi.Text)
            if (sub.isNotBlank()) {
                Text(sub, fontSize = 11.sp, color = MinimalUi.Muted, lineHeight = 15.sp)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * 歌曲专用列表行：长按拉起「下一首播放」等操作菜单。
 * 与 MNavRow 分开，避免给通用导航行插入参数后影响十几处位置参数调用。
 */
@Composable
private fun MSongRow(
    icon: ImageVector,
    label: String,
    sub: String,
    song: LocalSong? = null,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (song != null) {
            MCover(song, Modifier.size(48.dp), 12)
        } else {
            MAbstractCover(Modifier.size(48.dp), seed = label.hashCode(), radius = 12)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MinimalUi.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = MinimalUi.Muted, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        sub,
                        fontSize = 11.sp,
                        color = MinimalUi.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(30.dp).clip(CircleShape).clickable { onLongClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.MoreVert, null, tint = MinimalUi.Muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MThinDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(MinimalUi.Line.copy(alpha = 0.55f))
    )
}

// ============================================================
// 底部 5 tab：首页 / 发现 / 播放 / 收藏 / 我的
// 选中态用 2px 指示线表达，不再用胶囊色块 — 工具风的"定位"而不是"按钮"
// ============================================================

@Composable
internal fun MinimalBottomBar(current: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Icons.Outlined.Home to tr("首页", "Home"),
        Icons.Outlined.Explore to tr("发现", "Discover"),
        Icons.Outlined.MusicNote to tr("播放", "Player"),
        Icons.Outlined.FavoriteBorder to tr("收藏", "Saved"),
        Icons.Outlined.PersonOutline to tr("我的", "Mine")
    )
    Column(Modifier.fillMaxWidth().background(MinimalUi.Card).navigationBarsPadding().imePadding()) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(MinimalUi.Hairline))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { idx, (icon, label) ->
                val on = idx == current
                Column(
                    Modifier
                        .weight(1f)
                        .pressBounceClickable(onClick = { onSelect(idx) }, pressedScale = 0.95f)
                        .padding(top = 4.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .width(52.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (on) MinimalUi.BlueSoft else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            null,
                            tint = if (on) MinimalUi.Blue else MinimalUi.Muted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        label,
                        fontSize = 10.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) MinimalUi.Blue else MinimalUi.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ============================================================
// 1. 首页（推荐页）
// ============================================================

/**
 * 每日推荐只从真实曲库里挑：排除正在播放的歌和尚未下载音源的待获取记录。
 * 用日期做稳定种子，同一天反复进页面不会乱跳；跨天自动换歌。
 */
internal fun dailyRecommendation(songs: List<LocalSong>, excludeId: Long?): LocalSong? {
    val pool = songs.filter { !it.pendingSearch }
    if (pool.isEmpty()) return null
    val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val start = Math.floorMod((day + "|" + pool.size).hashCode(), pool.size)
    // 以整库固定起点，遇到正在播放的歌就顺延下一首；切歌不会改变今天的整体推荐顺序。
    for (offset in pool.indices) {
        val song = pool[(start + offset) % pool.size]
        if (song.id != excludeId) return song
    }
    return null
}

/**
 * 每日 30 首：以「日期 + 曲库规模」为稳定种子，从真实曲库轮转取 30 首。
 * 同一天反复进入顺序不变，跨天自动换一批；曲库不足 30 首时全部给出。
 */
internal fun dailyThirty(songs: List<LocalSong>): List<LocalSong> {
    val pool = songs.filter { !it.pendingSearch }
    if (pool.isEmpty()) return emptyList()
    val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val start = Math.floorMod((day + "|daily30|" + pool.size).hashCode(), pool.size)
    val rotated = pool.drop(start) + pool.take(start)
    return rotated.take(30)
}

@Composable
internal fun MinimalHomePage(
    recommend: LocalSong?,
    daily: List<LocalSong>,
    picks: List<LocalSong>,
    recent: List<LocalSong>,
    charts: List<ChartInfo>,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onDaily: () -> Unit,
    onChart: (Int) -> Unit,
    onCharts: () -> Unit,
    onPlaylists: () -> Unit,
    onArtists: () -> Unit,
    onPrivateFm: () -> Unit,
    onPlayFrom: (LocalSong, List<LocalSong>) -> Unit,
    onPlayDaily: () -> Unit,
    onPlayRecent: (LocalSong, List<LocalSong>) -> Unit,
    onLocal: () -> Unit,
    onLongClick: (LocalSong) -> Unit = {}
) {
    val homeScroll = rememberScrollState()
    val homeScope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxSize()
            .background(MinimalUi.Bg)
            .verticalScroll(homeScroll)
            .padding(horizontal = MPageSide)
    ) {
        Spacer(Modifier.height(12.dp))
        // 页头：小号等宽标签 + 标题 + 日期，信息分层靠字距与字号，不靠加粗放大
        val today = remember { SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date()) }
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "MY MUSIC",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    color = MinimalUi.Blue
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    tr("今天想听什么？", "What shall we play?"),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    color = MinimalUi.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(today, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Default, color = MinimalUi.Muted)
                    Text("  ·  ", fontSize = 10.sp, color = MinimalUi.Muted)
                    Text(tr("让好音乐陪伴每一段足迹", "A soundtrack for every journey"), fontSize = 12.sp, color = MinimalUi.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MinimalUi.SurfaceVariant)
                    .clickable { onSettings() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = tr("设置", "Settings"),
                    tint = MinimalUi.Sub,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        // QQ 音乐式频道胶囊：推荐 / 每日30 / 排行榜 / 歌单 / 歌手
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val channels = listOf(
                tr("推荐", "For you"),
                tr("每日30", "Daily 30"),
                tr("排行榜", "Charts"),
                tr("歌单", "Playlists"),
                tr("歌手", "Artists")
            )
            channels.forEachIndexed { idx, label ->
                val active = idx == 0
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) Color.White else MinimalUi.Sub,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(if (active) MinimalUi.Blue else MinimalUi.SurfaceVariant)
                        .pressBounceClickable(
                            onClick = {
                                when (idx) {
                                    0 -> homeScope.launch { homeScroll.animateScrollTo(0) }
                                    1 -> if (daily.isNotEmpty()) onDaily() else onLocal()
                                    2 -> onCharts()
                                    3 -> onPlaylists()
                                    4 -> onArtists()
                                }
                            },
                            pressedScale = 0.95f
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        // 搜索入口：QQ 音乐式胶囊搜索框
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50.dp))
                .background(MinimalUi.SurfaceVariant)
                .pressBounceClickable(onClick = onSearch, pressedScale = 0.99f)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Search, null, tint = MinimalUi.Blue, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text(tr("搜索歌曲、歌手、歌单", "Search songs, artists, playlists"), fontSize = 13.sp, color = MinimalUi.Muted)
        }
        Spacer(Modifier.height(18.dp))
        // 每日 30 首：QQ 音乐式绿色渐变主推卡，整块可点
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(AppPalette.value.terracotta, AppPalette.value.terracottaDark)
                    )
                )
                .pressBounceClickable(
                    onClick = { if (daily.isNotEmpty()) onDaily() else onLocal() },
                    pressedScale = 0.99f
                )
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(108.dp)) {
                    val cover = daily.firstOrNull() ?: recommend
                    if (cover != null) {
                        MCover(cover, Modifier.fillMaxSize(), 16)
                    } else {
                        MAbstractCover(Modifier.fillMaxSize(), radius = 16)
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            daily.size.toString().padStart(2, '0'),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppPalette.value.terracotta
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).heightIn(min = 108.dp)) {
                    Text(
                        "DAILY 30",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.8.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr("每日 30 首", "Daily 30"),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        when {
                            daily.isEmpty() -> tr("导入歌曲后，每天为你挑 30 首", "Add songs to get 30 picks a day")
                            daily.size >= 30 -> tr("按今天的曲库口味挑好 30 首", "30 picks from your library today")
                            else -> tr("曲库里的 ${daily.size} 首都挑好了", "All ${daily.size} songs in your library")
                        },
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.88f),
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${daily.size.toString().padStart(2, '0')} TRACKS",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (daily.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.35f))
                                .clickable(enabled = daily.isNotEmpty()) { onPlayDaily() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = tr("播放每日 30 首", "Play daily 30"),
                                tint = AppPalette.value.terracottaDark,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // 五个 QQ 音乐式快捷入口，私人 FM 走本地随机队列
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MQuickEntry(
                Icons.Outlined.Radio,
                tr("私人FM", "Private FM"),
                onPrivateFm,
                Modifier.weight(1f)
            )
            MQuickEntry(
                Icons.Outlined.Album,
                tr("每日30", "Daily 30"),
                { if (daily.isNotEmpty()) onDaily() else onLocal() },
                Modifier.weight(1f)
            )
            MQuickEntry(Icons.Outlined.Insights, tr("排行榜", "Charts"), onCharts, Modifier.weight(1f))
            MQuickEntry(Icons.Outlined.MusicNote, tr("歌单", "Playlists"), onPlaylists, Modifier.weight(1f))
            MQuickEntry(Icons.Outlined.PersonOutline, tr("歌手", "Artists"), onArtists, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        // 猜你喜欢：横向歌曲卡，一屏露出下一张，滑动比纵向列表更接近 QQ 音乐
        val forYou = if (picks.isNotEmpty()) picks else daily
        MSectionTitle(tr("猜你喜欢", "For you"))
        if (forYou.isEmpty()) {
            MCard {
                Text(
                    tr("导入或获取几首歌，这里就会出现推荐", "Add songs to see recommendations here"),
                    fontSize = 12.sp, color = MinimalUi.Muted,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                forYou.take(10).forEach { song ->
                    MSongTile(
                        song = song,
                        onClick = { onPlayFrom(song, forYou.take(10)) },
                        onLongClick = { onLongClick(song) }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // 排行榜：横向卡直接进榜，不再先绕一层导航行
        MSectionTitle(tr("排行榜", "Charts"), tr("全部", "All"), onCharts)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            charts.take(6).forEachIndexed { idx, chart ->
                MChartRailCard(index = idx, chart = chart, onClick = { onChart(chart.topId) })
            }
        }
        Spacer(Modifier.height(16.dp))
        // 心情歌单：四张真实封面，点击进入歌单广场
        val moodNames = listOf(
            tr("治愈", "Healing"), tr("放松", "Calm"),
            tr("电音", "EDM"), tr("国风", "Guofeng")
        )
        MSectionTitle(tr("心情歌单", "By mood"), tr("全部", "All"), onPlaylists)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            moodNames.forEachIndexed { idx, name ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onPlaylists() }) {
                    MArtImage(categoryCover(idx), Modifier.fillMaxWidth().aspectRatio(1f), 12)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinimalUi.Sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // 最近在听：真实播放记录，点歌即播；去重后首页不再重复堆「排行榜 / 本地 / 播放列表」入口
        if (recent.isNotEmpty()) {
            MSectionTitle(tr("最近在听", "Recently played"))
            MCard(modifier = Modifier.padding(bottom = 18.dp)) {
                recent.take(3).forEachIndexed { idx, song ->
                    if (idx > 0) MThinDivider()
                    MSongRow(
                        Icons.Outlined.Schedule, song.title, song.artist,
                        song = song,
                        onLongClick = { onLongClick(song) }
                    ) {
                        onPlayRecent(song, recent)
                    }
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 横向歌曲卡：封面 + 单行歌名/歌手，长按仍走原有操作菜单。 */
@Composable
private fun MSongTile(song: LocalSong, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .width(124.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        MCover(song, Modifier.fillMaxWidth().aspectRatio(1f), 12)
        Spacer(Modifier.height(8.dp))
        Text(
            song.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MinimalUi.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            song.artist.ifBlank { tr("未知歌手", "Unknown artist") },
            fontSize = 11.sp,
            color = MinimalUi.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 榜单横卡：序号用等宽数字，标题/说明保持两行，卡片等宽等高于横向轨道。 */
@Composable
private fun MChartRailCard(index: Int, chart: ChartInfo, onClick: () -> Unit) {
    Column(
        Modifier
            .width(170.dp)
            .height(112.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MinimalUi.Card)
            .border(1.dp, MinimalUi.Hairline, RoundedCornerShape(16.dp))
            .pressBounceClickable(onClick = onClick, pressedScale = 0.98f)
            .padding(14.dp)
    ) {
        Text(
            (index + 1).toString().padStart(2, '0'),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
            color = MinimalUi.Blue
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr(chart.nameZh, chart.nameEn),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MinimalUi.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            tr(chart.descZh, chart.descEn),
            fontSize = 10.sp,
            color = MinimalUi.Muted,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        Text(
            "TOP 300",
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
            color = MinimalUi.Muted
        )
    }
}

// ============================================================
// 2/3/4/5. 发现页：排行榜 / 歌单广场 / 搜索 / 歌手 的聚合入口
// ============================================================

@Composable
internal fun MinimalDiscoverPage(
    charts: List<ChartInfo>,
    artists: List<Pair<String, Int>>,
    onChart: (Int) -> Unit,
    onCharts: () -> Unit,
    onPlaylists: () -> Unit,
    onArtists: () -> Unit,
    onArtist: (String) -> Unit,
    onSearch: () -> Unit,
    onPlaylist: () -> Unit,
    onQqMusic: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MinimalUi.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MPageSide)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            "MUSIC HALL",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
            color = MinimalUi.Blue
        )
        Spacer(Modifier.height(7.dp))
        Text(
            tr("音乐馆", "Music hall"),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            color = MinimalUi.Text
        )
        Spacer(Modifier.height(18.dp))
        // 全局搜索仍进入原有搜索页。
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50.dp))
                .background(MinimalUi.SurfaceVariant)
                .pressBounceClickable(onClick = onSearch, pressedScale = 0.99f)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Search, null, tint = MinimalUi.Blue, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text(tr("搜索歌曲、歌手、歌单", "Search songs, artists, playlists"), fontSize = 13.sp, color = MinimalUi.Muted)
        }
        Spacer(Modifier.height(18.dp))
        MSectionTitle(tr("排行榜", "Charts"), tr("全部", "All"), onCharts)
        if (charts.isEmpty()) {
            MCard {
                Text(
                    tr("暂无可用榜单", "No charts available"),
                    fontSize = 13.sp,
                    color = MinimalUi.Muted,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            // 全部榜单横向铺开，一屏能横向扫到多个榜，点哪张进哪张
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                charts.forEachIndexed { idx, chart ->
                    MChartRailCard(index = idx, chart = chart, onClick = { onChart(chart.topId) })
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        // 分类歌单：2×2 大图，先看封面再决定进哪个歌单
        MSectionTitle(tr("分类歌单", "Playlists"), tr("全部", "All"), onPlaylists)
        val moodNames = listOf(
            tr("治愈", "Healing"), tr("放松", "Calm"),
            tr("电音", "EDM"), tr("国风", "Guofeng")
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) { col ->
                        val idx = row * 2 + col
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPlaylists() }
                        ) {
                            MArtImage(
                                categoryCover(idx),
                                Modifier.fillMaxWidth().aspectRatio(1.7f), 6
                            )
                            Spacer(Modifier.height(7.dp))
                            Text(
                                moodNames[idx],
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MinimalUi.Sub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        // 热门歌手：头像墙式横滑，点一下直接看这位歌手的歌
        MSectionTitle(tr("热门歌手", "Artists"), tr("全部", "All"), onArtists)
        if (artists.isEmpty()) {
            MCard {
                Text(
                    tr("还没有歌手信息，先扫描或导入歌曲", "No artists yet, scan or import songs"),
                    fontSize = 12.sp, color = MinimalUi.Muted,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                artists.take(12).forEach { (name, count) ->
                    Column(
                        Modifier
                            .width(76.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .pressBounceClickable(onClick = { onArtist(name) }, pressedScale = 0.96f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(MinimalUi.BlueSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name.take(1), fontSize = 24.sp, color = MinimalUi.Blue)
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(
                            name,
                            fontSize = 12.sp,
                            color = MinimalUi.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            tr("$count 首", "$count songs"),
                            fontSize = 10.sp,
                            color = MinimalUi.Muted,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        MSectionTitle(tr("更多音乐服务", "More music"))
        MCard(modifier = Modifier.padding(bottom = 18.dp)) {
            MNavRow(Icons.Outlined.FavoriteBorder, tr("我的歌单", "My playlists"), tr("收藏 / 最近播放 / 本地下载", "Favorites / recent / downloads"), onPlaylist)
            MThinDivider()
            MNavRow(Icons.Outlined.MusicNote, tr("读取 QQ 音乐", "Read QQ Music"), tr("登录同步收藏 / 粘贴歌单", "Login sync / paste playlist"), onQqMusic)
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ============================================================
// 4. 搜索页：本地实时匹配 + 热搜词，回车交给原有 SongSearch 全网链路
// ============================================================

@Composable
internal fun MinimalSearchPage(
    query: String,
    onQuery: (String) -> Unit,
    onSubmit: (String) -> Unit,
    hotWords: List<String>,
    localHits: List<LocalSong>,
    onPlayLocal: (LocalSong) -> Unit,
    onOpenCharts: () -> Unit,
    onDismiss: () -> Unit,
    onLongClick: (LocalSong) -> Unit = {}
) {
    // 进搜索页自动聚焦并拉起键盘，和主流音乐 App 一致；离开页面由系统收键盘
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { SongSearchHistory.init(ctx) }
    // 所有提交入口（回车 / 联想 / 热词 / 历史）统一记一条历史
    val submitWithHistory: (String) -> Unit = { text ->
        SongSearchHistory.add(ctx, text)
        onSubmit(text)
    }

    // 输满 2 个字就做全网联想：等 350ms 防抖，避免每个按键都打一次曲库。
    var onlineHits by remember { mutableStateOf<List<SongResult>>(emptyList()) }
    var metaHits by remember { mutableStateOf<List<MetaHit>>(emptyList()) }
    var suggesting by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            onlineHits = emptyList()
            suggesting = false
            return@LaunchedEffect
        }
        suggesting = true
        delay(350)
        onlineHits = SongSearch.search(q, 8)
        suggesting = false
    }
    // QQ 音乐 + 网易云只读联想：补足曲库搜不到的歌名/歌手/专辑
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            metaHits = emptyList()
            return@LaunchedEffect
        }
        delay(420)
        metaHits = withTimeoutOrNull(9_000L) { MetadataSearch.suggestions(q, 6) }.orEmpty()    }
    MSubPageScaffold {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50.dp))
                .background(MinimalUi.SurfaceVariant)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                Icon(Icons.Outlined.Search, null, tint = MinimalUi.Blue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = MinimalUi.Text),
                    cursorBrush = SolidColor(MinimalUi.Blue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitWithHistory(query) }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                tr("输入歌名或歌手，回车搜索全网曲库", "Song or artist, press Enter to search"),
                                fontSize = 13.sp, color = MinimalUi.Muted, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                )
                if (query.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Outlined.Close, null, tint = MinimalUi.Muted,
                        modifier = Modifier.size(18.dp).clickable { onQuery("") }
                    )
                }
            }
        if (query.isBlank() && SongSearchHistory.items.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            MSectionTitle(tr("搜索历史", "Recent searches"), tr("清空", "Clear")) {
                SongSearchHistory.clear(ctx)
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SongSearchHistory.items.forEach { word ->
                    Text(
                        word,
                        fontSize = 12.sp,
                        color = MinimalUi.Sub,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(MinimalUi.SurfaceVariant)
                            .clickable { submitWithHistory(word) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (query.trim().length >= 2) {
            Spacer(Modifier.height(16.dp))
            MSectionTitle(
                tr("猜你要找", "Suggestions"),
                tr("输入两三个字就自动联想", "Suggestions update as you type")
            )
            MCard {
                val norm = { s: String -> s.lowercase().replace(Regex("[^\\u4e00-\\u9fa5a-z0-9]"), "") }
                // 曲库已经给出的结果不再重复；元数据源补充歌名/歌手/专辑
                val extras = metaHits
                    .filter { m -> onlineHits.none { norm(it.title) == norm(m.title) } }
                    .take(3)
                when {
                    suggesting && onlineHits.isEmpty() && extras.isEmpty() -> Text(
                        tr("正在联想相关歌名…", "Finding related songs…"),
                        fontSize = 12.sp,
                        color = MinimalUi.Muted,
                        modifier = Modifier.padding(18.dp)
                    )
                    onlineHits.isEmpty() && extras.isEmpty() -> Text(
                        tr("没有联想结果，按回车继续搜全网", "No suggestions, press Enter to search"),
                        fontSize = 12.sp,
                        color = MinimalUi.Muted,
                        modifier = Modifier.padding(18.dp)
                    )
                    else -> {
                        onlineHits.forEachIndexed { idx, song ->
                            if (idx > 0) MThinDivider()
                            MNavRow(
                                Icons.Outlined.MusicNote,
                                song.title,
                                listOf(song.artist, song.album).filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                    .ifBlank { tr("未知歌手", "Unknown artist") }
                            ) {
                                submitWithHistory(
                                    listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" ")
                                )
                            }
                        }
                        extras.forEachIndexed { idx, hit ->
                            if (onlineHits.isNotEmpty() || idx > 0) MThinDivider()
                            MNavRow(
                                Icons.Outlined.MusicNote,
                                hit.title,
                                listOf(hit.artist, hit.album.ifBlank { hit.source })
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                    .ifBlank { hit.source }
                            ) {
                                submitWithHistory(
                                    listOf(hit.title, hit.artist).filter { it.isNotBlank() }.joinToString(" ")
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        MSectionTitle(tr("大家都在搜", "Trending"))
        MCard {
            hotWords.forEachIndexed { idx, word ->
                if (idx > 0) MThinDivider()
                MNavRow(Icons.Outlined.Insights, word, "") { submitWithHistory(word) }
            }
            MThinDivider()
            MNavRow(Icons.Outlined.Insights, tr("去排行榜看看", "Browse charts"), tr("热度 / 飙升 / 地区榜", "Hot / rising / regional"), onOpenCharts)
        }
        if (query.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            MSectionTitle(tr("本机匹配", "On device"), tr("${localHits.size} 首", "${localHits.size} songs"))
            MCard(modifier = Modifier.padding(bottom = 18.dp)) {
                if (localHits.isEmpty()) {
                    Text(
                        tr("本机没有匹配结果，回车可在全网曲库里搜", "No local match, press Enter to search online"),
                        fontSize = 12.sp, color = MinimalUi.Muted, modifier = Modifier.padding(18.dp)
                    )
                } else {
                    localHits.forEachIndexed { idx, song ->
                        if (idx > 0) MThinDivider()
                        MSongRow(
                            Icons.Outlined.MusicNote, song.title, song.artist,
                            song = song,
                            onLongClick = { onLongClick(song) }
                        ) { onPlayLocal(song) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ============================================================
// 6. 收藏页：长按沿用原有的「收藏 / 改名 / 互换 / 删除」菜单
// ============================================================

@Composable
internal fun MinimalFavoritesPage(
    songs: List<LocalSong>,
    onPlay: (LocalSong) -> Unit,
    onLongClick: (LocalSong) -> Unit,
    onToggleFavorite: (LocalSong) -> Unit,
    onLibrary: () -> Unit,
    onCharts: () -> Unit,
    onImport: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MinimalUi.Bg)
            .padding(horizontal = MPageSide)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                tr("我的收藏", "My favorites"),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MinimalUi.Text,
                modifier = Modifier.weight(1f)
            )
            Text(tr("${songs.size} 首", "${songs.size} songs"), fontSize = 12.sp, color = MinimalUi.Muted)
        }
        MCard {
            MNavRow(
                Icons.Outlined.CloudSync,
                tr("导入我的音乐", "Import my music"),
                tr("从网易云 / QQ 音乐一键导入收藏歌单", "Import favorites from Netease / QQ Music"),
                onImport
            )
        }
        Spacer(Modifier.height(12.dp))
        // 收藏汇总头使用真实歌曲封面；播放全部仍交给原有整列表播放逻辑。
        MCard {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(MinimalUi.BlueSoft)) {
                    repeat(2) { row ->
                        Row(Modifier.weight(1f)) {
                            repeat(2) { column ->
                                val coverSong = songs.getOrNull(row * 2 + column)
                                Box(Modifier.weight(1f).fillMaxHeight().padding(1.dp)) {
                                    if (coverSong != null) {
                                        MCover(coverSong, Modifier.fillMaxSize(), 12)
                                    } else {
                                        MAbstractCover(
                                            Modifier.fillMaxSize(),
                                            seed = row * 2 + column,
                                            radius = 12
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(17.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("我喜欢的音乐", "Songs I love"),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinimalUi.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        tr("共 ${songs.size} 首歌曲", "${songs.size} saved songs"),
                        fontSize = 12.sp,
                        color = MinimalUi.Muted
                    )
                    if (songs.isNotEmpty()) {
                        Spacer(Modifier.height(13.dp))
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(MinimalUi.Blue)
                                .clickable { songs.firstOrNull()?.let(onPlay) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                tr("播放全部", "Play all"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // 歌单内搜索：按歌名 / 歌手过滤，几百首收藏不用翻
        var favQuery by remember { mutableStateOf("") }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(MinimalUi.SurfaceVariant)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                Icon(Icons.Outlined.Search, null, tint = MinimalUi.Blue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = favQuery,
                    onValueChange = { favQuery = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp, color = MinimalUi.Text
                    ),
                    decorationBox = { inner ->
                        Box {
                            if (favQuery.isEmpty()) {
                                Text(
                                    tr("在收藏里搜歌名 / 歌手", "Search favorites by title or artist"),
                                    fontSize = 14.sp, color = MinimalUi.Muted
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                if (favQuery.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Close, tr("清空搜索", "Clear search"),
                        tint = MinimalUi.Muted,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { favQuery = "" }
                    )
                }
            }
        val shownSongs = if (favQuery.isBlank()) songs else songs.filter {
            SearchUtils.score(favQuery, "${it.title} ${it.artist} ${it.album}") > 0
        }
        if (shownSongs.isEmpty() && favQuery.isNotBlank()) {
            MCard {
                Text(
                    tr("没找到匹配的歌", "No matching songs"),
                    fontSize = 13.sp, color = MinimalUi.Muted,
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else if (shownSongs.isEmpty()) {
            MCard {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MArtImage(
                        R.drawable.empty_favorites,
                        Modifier.fillMaxWidth().height(150.dp), 16
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(tr("还没有收藏", "No favorites yet"), fontSize = 15.sp, color = MinimalUi.Text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr("在歌曲列表长按，选「收藏」即可加入", "Long-press a song and choose Favorite"),
                        fontSize = 12.sp, color = MinimalUi.Muted, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            tr("打开音乐库", "Open library"), fontSize = 12.sp, color = MinimalUi.Blue,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MinimalUi.BlueSoft)
                                .clickable { onLibrary() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                        Text(
                            tr("去排行榜", "Browse charts"), fontSize = 12.sp, color = MinimalUi.Blue,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MinimalUi.BlueSoft)
                                .clickable { onCharts() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(shownSongs.size) { idx ->
                    val song = shownSongs[idx]
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = { onPlay(song) },
                                    onLongClick = { onLongClick(song) }
                                )
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                (idx + 1).toString().padStart(2, '0'),
                                fontSize = 11.sp,
                                color = MinimalUi.Muted,
                                modifier = Modifier.width(26.dp)
                            )
                            MCover(song, Modifier.size(54.dp), 14)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MinimalUi.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    song.artist.ifBlank { tr("未知歌手", "Unknown artist") },
                                    fontSize = 11.sp,
                                    color = MinimalUi.Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Icon(
                                Icons.Filled.Favorite,
                                tr("取消收藏", "Remove from favorites"),
                                tint = MinimalUi.Blue,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable { onToggleFavorite(song) }
                            )
                        }
                        if (idx < shownSongs.lastIndex) {
                            Box(
                                Modifier
                                    .padding(start = 92.dp)
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(MinimalUi.Line.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// 3. 歌单广场（官方 / 热门 / 分类）
// ============================================================

@Composable
internal fun MinimalPlaylistSquarePage(
    categories: List<String>,
    playlists: List<Pair<String, Int>>,
    onPick: (String) -> Unit,
    onPlayAll: (String) -> Unit,
    onDismiss: () -> Unit
) {
    MSubPageScaffold {
        MSectionTitle(tr("分类歌单", "By mood"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.take(4).forEachIndexed { idx, c ->
                Column(
                    Modifier.weight(1f).clickable { onPick(c) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MArtImage(
                        categoryCover(idx),
                        Modifier.fillMaxWidth().aspectRatio(1f), 14
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        c, fontSize = 12.sp, color = MinimalUi.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        MSectionTitle(tr("官方精选 / 热门歌单", "Featured & popular"))
        MCard {
            if (playlists.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MArtImage(R.drawable.empty_playlist, Modifier.fillMaxWidth().height(130.dp), 16)
                    Spacer(Modifier.height(10.dp))
                    Text(tr("还没有歌单", "No playlists yet"), fontSize = 12.sp, color = MinimalUi.Muted)
                }
            }
            playlists.forEachIndexed { idx, (name, count) ->
                if (idx > 0) MThinDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(name) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MArtImage(categoryCover(idx), Modifier.size(52.dp), 14)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 15.sp, color = MinimalUi.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            tr("$count 首", "$count songs"), fontSize = 11.sp, color = MinimalUi.Muted
                        )
                    }
                    Text(
                        tr("播放", "Play"), fontSize = 12.sp, color = MinimalUi.Blue,
                        modifier = Modifier.clickable { onPlayAll(name) }.padding(6.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

// ============================================================
// 4.5 每日 30 首
// ============================================================

@Composable
internal fun MinimalDailyPage(
    songs: List<LocalSong>,
    onPlay: (LocalSong) -> Unit,
    onPlayAll: () -> Unit,
    onDismiss: () -> Unit,
    onLongClick: (LocalSong) -> Unit = {}
) {
    val today = remember { SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date()) }
    MSubPageScaffold {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(AppPalette.value.terracotta, AppPalette.value.terracottaDark)
                    )
                )
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "DAILY 30",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.8.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        today,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr("按曲库口味挑选，0 点更新", "Picked from your library, refreshed at midnight"),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(12.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(if (songs.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.35f))
                        .clickable(enabled = songs.isNotEmpty()) { onPlayAll() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = tr("播放全部", "Play all"),
                        tint = AppPalette.value.terracottaDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        tr("播放全部", "Play all"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppPalette.value.terracottaDark
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (songs.isEmpty()) {
            MCard {
                Text(
                    tr("曲库为空，先导入或扫描歌曲", "Library is empty. Import or scan songs first."),
                    fontSize = 12.sp, color = MinimalUi.Muted,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            MSectionTitle(
                tr("今日曲目", "Today's tracks"),
                tr("${songs.size} 首", "${songs.size} tracks")
            )
            MCard {
                songs.forEachIndexed { idx, song ->
                    if (idx > 0) MThinDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onPlay(song) },
                                onLongClick = { onLongClick(song) }
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            (idx + 1).toString().padStart(2, '0'),
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            color = when (idx) {
                                0 -> MinimalUi.Blue
                                1 -> AppPalette.value.gold
                                2 -> AppPalette.value.danger
                                else -> MinimalUi.Muted
                            },
                            modifier = Modifier.width(26.dp)
                        )
                        MCover(song, Modifier.size(44.dp), 12)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MinimalUi.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist.ifBlank { tr("未知歌手", "Unknown artist") },
                                fontSize = 11.sp,
                                color = MinimalUi.Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            if (MusicPlayer.current?.id == song.id) Icons.Outlined.Equalizer else Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            tint = if (MusicPlayer.current?.id == song.id) MinimalUi.Blue else MinimalUi.Muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

// ============================================================
// 5. 歌手页
// ============================================================
@Composable
internal fun MinimalArtistPage(
    artists: List<Pair<String, Int>>,
    onOpen: (String) -> Unit,
    onPlayAll: (String) -> Unit,
    onDismiss: () -> Unit
) {
    MSubPageScaffold {
        MSectionTitle(tr("全部歌手", "All artists"), tr("${artists.size} 位", "${artists.size} artists"))
        MCard {
            if (artists.isEmpty()) {
                Text(
                    tr("还没有歌手信息，先扫描或导入歌曲", "No artists yet, scan or import songs"),
                    fontSize = 12.sp, color = MinimalUi.Muted, modifier = Modifier.padding(18.dp)
                )
            } else {
                artists.forEachIndexed { idx, (name, count) ->
                    if (idx > 0) MThinDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(name) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MinimalUi.BlueSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name.take(1), fontSize = 18.sp, color = MinimalUi.Blue)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, fontSize = 15.sp, color = MinimalUi.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                tr("$count 首", "$count songs"), fontSize = 11.sp, color = MinimalUi.Muted,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                        Text(
                            tr("播放全部", "Play all"), fontSize = 12.sp, color = MinimalUi.Blue,
                            modifier = Modifier.clickable { onPlayAll(name) }.padding(6.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

/**
 * 歌手详情：点头像/名字进入，先看全部本地单曲，再决定「播放全部」或点单曲。
 * 之前歌手行点击直接开播，和右侧「播放全部」重复；现在点击是浏览，播放才是播放。
 */
@Composable
internal fun MinimalArtistDetailPage(
    name: String,
    songs: List<LocalSong>,
    onPlay: (LocalSong) -> Unit,
    onPlayAll: () -> Unit,
    onDismiss: () -> Unit,
    onLongClick: (LocalSong) -> Unit = {}
) {
    MSubPageScaffold {
        MCard {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(MinimalUi.BlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name.take(1).ifBlank { "M" },
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinimalUi.Blue
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp,
                        color = MinimalUi.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        tr("${songs.size} 首单曲", "${songs.size} tracks"),
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                        color = MinimalUi.Muted
                    )
                }
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(if (songs.isNotEmpty()) MinimalUi.Blue else MinimalUi.SurfaceDim)
                        .clickable(enabled = songs.isNotEmpty()) { onPlayAll() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = tr("播放全部", "Play all"),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        tr("播放全部", "Play all"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (songs.isEmpty()) {
            MCard {
                Text(
                    tr("这位歌手还没有本地歌曲", "No local songs for this artist"),
                    fontSize = 12.sp, color = MinimalUi.Muted,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            MSectionTitle(tr("全部歌曲", "All songs"), tr("${songs.size} 首", "${songs.size} tracks"))
            MCard {
                songs.forEachIndexed { idx, song ->
                    if (idx > 0) MThinDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onPlay(song) },
                                onLongClick = { onLongClick(song) }
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            (idx + 1).toString().padStart(2, '0'),
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            color = MinimalUi.Muted,
                            modifier = Modifier.width(26.dp)
                        )
                        MCover(song, Modifier.size(44.dp), 12)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MinimalUi.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.album.ifBlank { tr("未知专辑", "Unknown album") },
                                fontSize = 11.sp,
                                color = MinimalUi.Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (song.durationMs > 0) {
                            Text(
                                mDuration(song.durationMs),
                                fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                                color = MinimalUi.Muted
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

private fun mDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}

// ============================================================
// 7. 本地 / 下载页
// ============================================================

@Composable
internal fun MinimalLocalPage(
    downloaded: Int,
    cacheSummary: String,
    recent: List<LocalSong>,
    onImport: () -> Unit,
    onRescan: () -> Unit,
    onClearCache: () -> Unit,
    onOpenLibrary: () -> Unit,
    onPlay: (LocalSong) -> Unit,
    onDismiss: () -> Unit,
    onLongClick: (LocalSong) -> Unit = {}
) {
    MSubPageScaffold {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MQuickEntry(Icons.Outlined.Download, tr("导入歌曲", "Import"), onImport, Modifier.weight(1f))
            MQuickEntry(Icons.Outlined.Restore, tr("扫描本地", "Scan"), onRescan, Modifier.weight(1f))
            MQuickEntry(Icons.Outlined.CleaningServices, tr("清理缓存", "Clear cache"), onClearCache, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        MSectionTitle(tr("下载中 / 已下载", "Downloading / Downloaded"))
        MCard {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Download, null, tint = MinimalUi.Blue, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("已下载 $downloaded 首", "$downloaded songs downloaded"),
                        fontSize = 15.sp, color = MinimalUi.Text
                    )
                    Text(cacheSummary, fontSize = 11.sp, color = MinimalUi.Muted)
                }
            }
            MThinDivider()
            MNavRow(Icons.Outlined.MusicNote, tr("打开完整音乐库", "Open full library"), tr("长按可改名 / 删除 / 收藏", "Long-press to manage"), onOpenLibrary)
        }
        Spacer(Modifier.height(16.dp))
        MSectionTitle(tr("最近下载的歌曲", "Recently downloaded"))
        MCard {
            if (recent.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MArtImage(
                        R.drawable.empty_library,
                        Modifier.fillMaxWidth().height(130.dp), 16
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        tr("联网获取的歌曲会离线保存在这里", "Songs fetched online are saved offline here"),
                        fontSize = 12.sp, color = MinimalUi.Muted, textAlign = TextAlign.Center
                    )
                }
            } else {
                recent.take(8).forEachIndexed { idx, s ->
                    if (idx > 0) MThinDivider()
                    MSongRow(
                        Icons.Outlined.MusicNote, s.title, s.artist,
                        song = s,
                        onLongClick = { onLongClick(s) }
                    ) { onPlay(s) }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

// ============================================================
// 8. 个人中心
// ============================================================

@Composable
internal fun MinimalProfilePage(
    state: AppState,
    stats: Triple<Int, Int, Int>,
    songs: List<LocalSong>,
    recentSongs: List<LocalSong>,
    playCounts: Map<Long, Int>,
    playlists: List<Pair<String, Int>>,
    importing: Boolean,
    importStatus: String?,
    onImportShot: () -> Unit,
    onEditProfile: () -> Unit,
    onLocal: () -> Unit,
    onLibrary: () -> Unit,
    onSong: (LocalSong) -> Unit,
    onSettings: () -> Unit,
    onPlaylist: () -> Unit,
    onPlaylistPick: (String) -> Unit,
    onReport: () -> Unit
) {
    // 「最近听过」默认是堆叠卡片，点「全部」在页内展开成完整列表
    var recentExpanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MinimalUi.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MPageSide)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            "PROFILE",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
            color = MinimalUi.Blue
        )
        Spacer(Modifier.height(7.dp))
        Text(
            tr("我的", "Profile"),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            color = MinimalUi.Text
        )
        Spacer(Modifier.height(18.dp))
        // 真实用户资料与曲库统计集中展示，编辑入口仍跳原有资料设置。
        MCard {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(62.dp).clip(RoundedCornerShape(12.dp)).background(MinimalUi.BlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        state.user.nickname.take(1).ifBlank { "M" },
                        fontSize = 25.sp,
                        color = MinimalUi.Blue,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.user.nickname,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MinimalUi.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(tr("简单听音乐", "Just listen"), fontSize = 11.sp, color = MinimalUi.Muted)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(MinimalUi.SurfaceVariant)
                        .clickable { onEditProfile() }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(tr("编辑", "Edit"), fontSize = 12.sp, color = MinimalUi.Sub)
                }
            }
            MThinDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                MStat(tr("全部歌曲", "Songs"), stats.first)
                MStat(tr("收藏", "Saved"), stats.second)
                MStat(tr("最近听过", "Recent"), stats.third)
            }
        }
        Spacer(Modifier.height(16.dp))
        // 我的歌单：收藏 / 最近 / 本地三块入口，像 QQ 音乐那样先看歌单再听
        MSectionTitle(tr("我的歌单", "My playlists"), tr("管理", "Manage"), onPlaylist)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            playlists.take(2).forEachIndexed { idx, (name, count) ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .pressBounceClickable(onClick = { onPlaylistPick(name) }, pressedScale = 0.98f)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MinimalUi.BlueSoft)
                            .border(1.dp, MinimalUi.Hairline, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            if (idx == 0) Icons.Outlined.FavoriteBorder else Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MinimalUi.Blue,
                            modifier = Modifier.align(Alignment.TopStart).padding(10.dp).size(18.dp)
                        )
                        Text(
                            count.toString().padStart(2, '0'),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            color = MinimalUi.Blue,
                            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        name, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = MinimalUi.Text, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .pressBounceClickable(onClick = onLocal, pressedScale = 0.98f)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MinimalUi.SurfaceDim)
                        .border(1.dp, MinimalUi.Hairline, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = null,
                        tint = MinimalUi.Sub,
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp).size(18.dp)
                    )
                    Text(
                        tr("本地", "Local"),
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = MinimalUi.Sub,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                    )
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    tr("下载与本地", "Downloads"),
                    fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = MinimalUi.Text, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        // 最近听过：真实播放记录，不放排行榜（排行榜在「发现」页）
        MSectionTitle(
            tr("最近听过", "Recent plays"),
            if (recentExpanded) tr("收起", "Collapse") else tr("全部", "All"),
            { recentExpanded = !recentExpanded }
        )
        if (recentSongs.isEmpty()) {
            MCard {
                Text(
                    tr("还没有播放记录，去首页挑一首开始听吧", "No plays yet. Pick a song to start."),
                    fontSize = 12.sp, color = MinimalUi.Muted, modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            MRecentSongStack(
                songs = recentSongs,
                playCounts = playCounts,
                onSong = onSong,
                expanded = recentExpanded
            )
        }
        Spacer(Modifier.height(18.dp))
        // 听歌报告：只统计本机播放记录，不混入排行榜数据
        val report = remember(songs, playCounts, recentSongs) {
            buildMListenReport(songs, recentSongs, playCounts, stats.second)
        }
        MSectionTitle(
            tr("听歌报告", "Listening report"),
            tr("查看", "Open"),
            onReport
        )
        MListenReportCard(report = report, onOpen = onReport)
        Spacer(Modifier.height(18.dp))
        // 常用入口：本地下载 / 音乐库 / 设置
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MQuickEntry(Icons.Outlined.Download, tr("本地下载", "Downloads"), onLocal, Modifier.weight(1f))
            MQuickEntry(Icons.Outlined.MusicNote, tr("音乐库", "Library"), onLibrary, Modifier.weight(1f))
            MQuickEntry(Icons.Outlined.Settings, tr("设置", "Settings"), onSettings, Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        // 截图导入：识别歌名/歌手并保存记录，音源等点击播放时再获取
        MSectionTitle(tr("导入", "Import"))
        MCard {
            MNavRow(
                Icons.Outlined.AddPhotoAlternate,
                tr("截图导入其他 App 歌单", "Import a playlist screenshot"),
                if (importing) tr("正在本地 OCR 识别，并用本地 AI 校对歌名/歌手…", "Local OCR and on-device AI check...")
                else tr("只记录歌名/歌手，不下载；播放时再获取音源", "Save names only; fetch audio when you play"),
                onImportShot
            )
            if (!importStatus.isNullOrBlank()) {
                MThinDivider()
                Text(
                    importStatus,
                    fontSize = 11.sp,
                    color = if (importing) MinimalUi.Blue else MinimalUi.Muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        MSectionTitle(tr("我的歌单", "My playlists"), tr("全部", "All"), onPlaylist)
        MCard(modifier = Modifier.padding(bottom = 18.dp)) {
            if (playlists.isEmpty()) {
                Text(
                    tr("收藏歌曲或从 QQ 音乐导入歌单后会出现在这里", "Import or favorite songs to create playlists"),
                    fontSize = 12.sp, color = MinimalUi.Muted, modifier = Modifier.padding(18.dp)
                )
            } else {
                playlists.forEachIndexed { idx, (name, count) ->
                    if (idx > 0) MThinDivider()
                    MNavRow(Icons.Outlined.MusicNote, name, tr("$count 首", "$count songs")) { onPlaylistPick(name) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private data class MListenReport(
    val totalPlays: Int,
    val heardCount: Int,
    val favoriteCount: Int,
    val topSong: LocalSong?,
    val topSongPlays: Int,
    val topArtist: String,
    val topArtistPlays: Int,
    val topArtistSong: LocalSong?,
    val recent: List<LocalSong>,
    val wave: List<Int>
)

private fun buildMListenReport(
    songs: List<LocalSong>,
    recentSongs: List<LocalSong>,
    playCounts: Map<Long, Int>,
    favoriteCount: Int
): MListenReport {
    val byId = songs.associateBy { it.id }
    val topEntry = playCounts.maxByOrNull { it.value }
    val topSong = topEntry?.key?.let { byId[it] }
        ?: recentSongs.firstOrNull { playCounts[it.id] != null }
        ?: recentSongs.firstOrNull()
    val topPlays = topEntry?.value ?: topSong?.let { playCounts[it.id] } ?: 0

    // 按歌手汇总真实播放次数，找最常听的歌手；封面优先用该歌手播放最多的一首。
    val artistGroups = songs
        .filter { (playCounts[it.id] ?: 0) > 0 && it.artist.isNotBlank() }
        .groupBy { it.artist }
    val topArtistEntry = artistGroups.maxByOrNull { (_, list) ->
        list.sumOf { playCounts[it.id] ?: 0 }
    }
    val topArtist = topArtistEntry?.key
        ?: topSong?.artist?.takeIf { it.isNotBlank() }
        ?: tr("未知歌手", "Unknown artist")
    val topArtistSong = topArtistEntry?.value?.maxByOrNull { playCounts[it.id] ?: 0 }
        ?: topSong
    val topArtistPlays = topArtistEntry?.value?.sumOf { playCounts[it.id] ?: 0 } ?: topPlays

    return MListenReport(
        totalPlays = playCounts.values.sum(),
        heardCount = playCounts.keys.count { byId.containsKey(it) } + recentSongs.count {
            it.id !in playCounts && it.id !in byId
        },
        favoriteCount = favoriteCount,
        topSong = topSong,
        topSongPlays = topPlays,
        topArtist = topArtist,
        topArtistPlays = topArtistPlays,
        topArtistSong = topArtistSong,
        recent = recentSongs,
        wave = recentSongs.take(14).reversed().map { playCounts[it.id] ?: 0 }
    )
}

@Composable
private fun MReportHashtag(text: String, color: Color = MinimalUi.Text) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** 「Music Report」波浪线：用最近播放次数画一条渐变曲线，不依赖任何图片素材 */
@Composable
private fun MReportWave(report: MListenReport, modifier: Modifier = Modifier) {
    val values = remember(report.wave) { report.wave.ifEmpty { listOf(0, 0) } }
    Canvas(modifier) {
        val pad = 14.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - 10.dp.toPx()
        val maxV = (values.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val points = values.mapIndexed { i, v ->
            val x = pad + w * (i.toFloat() / (values.size - 1).coerceAtLeast(1))
            val y = size.height - pad - h * (v.toFloat() / maxV) * 0.75f
            Offset(x, y)
        }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        if (points.size > 1) {
            val fill = Path().apply {
                moveTo(points.first().x, size.height - pad)
                lineTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, size.height - pad)
                close()
            }
            drawPath(fill, color = MinimalUi.Blue.copy(alpha = 0.10f))
        }
        drawPath(
            line,
            color = MinimalUi.Blue,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawCircle(
            color = MinimalUi.Blue,
            radius = 3.dp.toPx(),
            center = points.last()
        )
        drawCircle(
            color = MinimalUi.Card,
            radius = 1.5.dp.toPx(),
            center = points.last()
        )
    }
}

/** 个人中心听歌报告预览：沿用真实统计，以统一蓝色呈现。 */
@Composable
private fun MListenReportCard(report: MListenReport, onOpen: () -> Unit) {
    val purple = MinimalUi.Blue
    val day = remember { SimpleDateFormat("M.d", Locale.getDefault()).format(Date()) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MCardShape)
            .background(MinimalUi.Card)
            .border(1.dp, MinimalUi.Hairline, MCardShape)
            .clickable { onOpen() }
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "MUSIC REPORT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    color = purple
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("听歌报告", "Listening report"),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MinimalUi.Text
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tr("本机真实播放记录", "Real on-device plays"),
                    fontSize = 11.sp, color = MinimalUi.Muted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    tr("累计已听", "Total plays"),
                    fontSize = 11.sp, color = MinimalUi.Muted
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        report.totalPlays.toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                        color = MinimalUi.Text
                    )
                    Text(tr(" 首", " songs"), fontSize = 11.sp, color = MinimalUi.Sub)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    tr("最常听 ${report.topArtistPlays} 次", "Top artist · ${report.topArtistPlays}"),
                    fontSize = 10.sp, color = MinimalUi.Muted
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        MReportWave(report, Modifier.fillMaxWidth().height(88.dp))
        Spacer(Modifier.height(10.dp))
        MReportHashtag(tr("#${day}循环${report.topSongPlays}次", "#$day · ${report.topSongPlays} plays"))
        MReportHashtag(tr("#${report.topArtist}的歌你最爱听", "#Most played: ${report.topArtist}"), purple)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(MinimalUi.Blue),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    report.topSong?.title ?: tr("还没有播放记录", "No plays yet"),
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinimalUi.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    report.topSong?.artist ?: tr("去首页挑一首开始听", "Pick a song to start"),
                    fontSize = 12.sp, color = MinimalUi.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            val coverSong = report.topSong
            if (coverSong != null) {
                MCover(coverSong, Modifier.size(56.dp), 12)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            tr("点开查看完整报告", "Tap for the full report"),
            fontSize = 10.sp, color = MinimalUi.Muted
        )
    }
}

@Composable
private fun MReportDotLine(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        var x = 0f
        while (x < size.width) {
            drawCircle(color = color, radius = 1.dp.toPx(), center = Offset(x, size.height / 2))
            x += 7.dp.toPx()
        }
    }
}

/** 完整的听歌报告页：只用真实播放次数、最近播放和真实封面，不编造好友/城市数据 */
@Composable
internal fun MinimalListenReportPage(
    songs: List<LocalSong>,
    recentSongs: List<LocalSong>,
    playCounts: Map<Long, Int>,
    favoriteCount: Int,
    onPlay: (LocalSong) -> Unit
) {
    val report = remember(songs, recentSongs, playCounts, favoriteCount) {
        buildMListenReport(songs, recentSongs, playCounts, favoriteCount)
    }
    val purple = MinimalUi.Blue
    val paper = MinimalUi.Card
    val ink = MinimalUi.Text
    val sub = MinimalUi.Sub

    Column(
        Modifier
            .fillMaxSize()
            .background(MinimalUi.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(paper)
                .border(1.dp, MinimalUi.Hairline, RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    "LISTENING REPORT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    color = purple
                )
                Spacer(Modifier.height(7.dp))
                Text(tr("听歌报告", "Listening report"), fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = ink)
            }
            MReportDotLine(Modifier.padding(vertical = 12.dp), sub.copy(alpha = 0.5f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(tr("已听 ", "Heard "), fontSize = 15.sp, color = sub)
                Text(
                    report.totalPlays.toString(),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                    color = ink
                )
                Text(tr(" 首", " songs"), fontSize = 13.sp, color = sub)
            }
            Text(
                tr(
                    "来自本机 ${report.heardCount} 首歌的播放记录",
                    "From ${report.heardCount} songs played in this app"
                ),
                fontSize = 11.sp, color = sub
            )
            Spacer(Modifier.height(8.dp))
            MReportWave(report, Modifier.fillMaxWidth().height(110.dp))
            Spacer(Modifier.height(12.dp))
            MReportHashtag(
                tr("#${report.topArtist}的歌你最爱听", "#Most played: ${report.topArtist}"),
                purple
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(MinimalUi.Blue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        report.topSong?.title ?: tr("还没有播放记录", "No plays yet"),
                        fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        tr(
                            "${report.topSong?.artist ?: "—"} · 播放 ${report.topSongPlays} 次",
                            "${report.topSong?.artist ?: "—"} · ${report.topSongPlays} plays"
                        ),
                        fontSize = 12.sp, color = sub,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (report.topSong != null) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MinimalUi.Hairline, RoundedCornerShape(12.dp))
                            .clickable { report.topSong?.let(onPlay) }
                    ) {
                        MCover(report.topSong, Modifier.fillMaxSize(), 12)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MReportStat(tr("累计播放", "Plays"), report.totalPlays, tr("次", "plays"), Modifier.weight(1f))
            MReportStat(tr("听过歌曲", "Heard"), report.heardCount, tr("首", "songs"), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MReportStat(tr("收藏", "Saved"), report.favoriteCount, tr("首", "songs"), Modifier.weight(1f))
            MReportStat(tr("最爱循环", "Top loops"), report.topSongPlays, tr("次", "plays"), Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        MCard {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("最常听的歌手", "Most played artist"), fontSize = 13.sp, color = MinimalUi.Muted)
                    Spacer(Modifier.height(4.dp))
                    Text(report.topArtist, fontSize = 26.sp, fontWeight = FontWeight.Black, color = MinimalUi.Text)
                    Text(
                        tr("本机播放 ${report.topArtistPlays} 次", "${report.topArtistPlays} plays in this app"),
                        fontSize = 11.sp, color = MinimalUi.Muted
                    )
                }
                if (report.topArtistSong != null) {
                    Box(Modifier.size(82.dp).clip(RoundedCornerShape(16.dp))) {
                        MCover(report.topArtistSong, Modifier.fillMaxSize(), 20)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        MCard {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (report.topSong != null) {
                    Box(Modifier.size(86.dp).clip(RoundedCornerShape(16.dp))) {
                        MCover(report.topSong, Modifier.fillMaxSize(), 18)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("#播放最多", "#Most played"), fontSize = 12.sp, color = purple)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        report.topSong?.title ?: tr("还没有播放记录", "No plays yet"),
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MinimalUi.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        report.topSong?.artist ?: "—",
                        fontSize = 12.sp, color = MinimalUi.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(tr("播放 ${report.topSongPlays} 次", "${report.topSongPlays} plays"), fontSize = 11.sp, color = MinimalUi.Muted)
                }
                if (report.topSong != null) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(purple)
                            .clickable { report.topSong?.let(onPlay) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // 年度盘点入口：右侧「年度听歌报告」封面由生图补齐，文字用 Compose 叠在图上
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(paper)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("重温", "Replay"), fontSize = 28.sp, fontWeight = FontWeight.Black, color = ink)
                    Text(tr("年度听歌盘点", "Year in listening"), fontSize = 12.sp, color = sub)
                }
                Box(
                    Modifier.size(92.dp).clip(RoundedCornerShape(16.dp))
                ) {
                    Image(
                        painter = painterResource(R.drawable.report_year_cover),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Column(
                        Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("2026", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            tr("年度听歌\n报告", "Music\nReport"),
                            fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White,
                            textAlign = TextAlign.Center, lineHeight = 12.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            tr("报告数据来自本机播放记录，实时更新", "Live from plays in this app"),
            fontSize = 10.sp, color = MinimalUi.Muted,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MReportStat(label: String, value: Int, unit: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MinimalUi.Card)
            .padding(16.dp)
    ) {
        Text(label, fontSize = 11.sp, color = MinimalUi.Muted)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value.toString(), fontSize = 26.sp, fontWeight = FontWeight.Black, color = MinimalUi.Text)
            Text(unit, fontSize = 10.sp, color = MinimalUi.Muted, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

/**
 * 最近听过：默认堆叠成「一叠纸」，点「全部」在本页原地展开成完整列表。
 *
 * 折叠态只把最新一首完整展示，下面最多两张各露出 9dp 的边（并逐层向内收），
 * 一眼就能看出后面还有内容；展开态改成普通卡片竖排，显示全部播放记录，
 * 再点「收起」回到堆叠。
 */
@Composable
private fun MRecentSongStack(
    songs: List<LocalSong>,
    playCounts: Map<Long, Int>,
    onSong: (LocalSong) -> Unit,
    expanded: Boolean
) {
    val shape = RoundedCornerShape(16.dp)
    val peep = 9.dp
    val layers = minOf((songs.size - 1).coerceAtLeast(0), 2)

    /**
     * 折叠 ↔ 展开的统一进度：0 = 一叠纸，1 = 全部摊平。
     *
     * 之前直接用 animateContentSize（默认 spring），高度是「弹」出来的，
     * 垫层则是瞬间出现/消失，看着像闪一下。这里改成显式的慢速 tween：
     * 展开时垫层先收回、列表再一条条摊开；收起时列表先叠回去，垫层晚 180ms 才补上，
     * 两个方向都不打架。
     */
    val morph by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = 460,
            delayMillis = if (expanded) 0 else 180,
            easing = FastOutSlowInEasing
        ),
        label = "recent-stack-morph"
    )
    val collapse = 1f - morph

    Column(Modifier.fillMaxWidth()) {
        // 高度跟着进度走：垫层收回的同时容器的多出来的那截也一起收掉，不会留一道空缝
        Box(
            Modifier
                .fillMaxWidth()
                .height(88.dp + peep * layers * collapse)
        ) {
            // 背后两层：只露一条边，随进度归位并淡出。走 graphicsLayer，逐帧不触发重组
            for (i in layers downTo 1) {
                if (collapse > 0.01f) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .zIndex((layers - i).toFloat())
                            .graphicsLayer {
                                translationY = peep.toPx() * i * collapse
                                alpha = collapse
                            }
                            .padding(horizontal = 10.dp * i * collapse)
                            .clip(shape)
                            .background(MinimalUi.SurfaceVariant)
                            .border(1.dp, MinimalUi.Hairline, shape)
                    )
                }
            }
            val front = songs.first()
            MRecentSongCard(
                song = front,
                plays = playCounts[front.id] ?: 0,
                onSong = onSong,
                modifier = Modifier.zIndex(10f)
            )
        }

        // 其余条目：逐条缓慢展开，收起时倒序叠回去
        songs.drop(1).forEachIndexed { idx, song ->
            val enterDelay = 90 + idx * 45
            val exitDelay = ((songs.size - 2 - idx).coerceAtLeast(0)) * 35
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(420, delayMillis = enterDelay, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top
                ) + fadeIn(tween(300, delayMillis = enterDelay)) + slideInVertically(
                    animationSpec = tween(420, delayMillis = enterDelay, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 4 }
                ),
                exit = shrinkVertically(
                    animationSpec = tween(300, delayMillis = exitDelay, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(tween(200, delayMillis = exitDelay))
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    MRecentSongCard(
                        song = song,
                        plays = playCounts[song.id] ?: 0,
                        onSong = onSong
                    )
                }
            }
        }
    }
}

/** 单条最近播放：封面 + 歌名/歌手 + 播放次数 + 播放键 */
@Composable
private fun MRecentSongCard(
    song: LocalSong,
    plays: Int,
    onSong: (LocalSong) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(shape)
            .background(MinimalUi.Card)
            .border(1.dp, MinimalUi.Hairline, shape)
            .clickable { onSong(song) }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MCover(song, Modifier.size(52.dp), 12)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinimalUi.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                song.artist.ifBlank { tr("未知歌手", "Unknown artist") },
                fontSize = 11.sp, color = MinimalUi.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (song.pendingSearch) {
                Text(
                    if (MusicPlayer.resolvingSongId == song.id) {
                        tr("正在获取音源…", "Fetching audio...")
                    } else {
                        tr("待获取音源 · 点击播放时获取", "Audio not fetched yet · tap to play")
                    },
                    fontSize = 9.sp, color = MinimalUi.Blue,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (plays > 0) {
            Text(
                tr("$plays 次", "$plays plays"),
                fontSize = 10.sp, color = MinimalUi.Muted
            )
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            Icons.Outlined.PlayArrow,
            contentDescription = tr("播放", "Play"),
            tint = MinimalUi.Blue,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun RowScope.MStat(label: String, value: Int) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
            color = MinimalUi.Text
        )
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 10.sp, color = MinimalUi.Muted, letterSpacing = 0.5.sp)
    }
}

// ============================================================
// 10. 设置页（全部接真实 API，不新增假开关）
// ============================================================

@Composable
internal fun MinimalSettingsPage(
    state: AppState,
    cacheSummary: String,
    notifEnabled: Boolean,
    onTheme: (Int) -> Unit,
    onLanguage: () -> Unit,
    onNotif: (Boolean) -> Unit,
    onPlayMode: () -> Unit,
    onEq: () -> Unit,
    onSleep: () -> Unit,
    onAlarm: () -> Unit,
    onPlaylistView: () -> Unit,
    onImportLrc: () -> Unit,
    onImport: () -> Unit,
    onRemoveImportedLyrics: () -> Unit,
    onClearImportedSongs: () -> Unit,
    onClearCache: () -> Unit,
    onPrivacy: () -> Unit,
    onDismiss: () -> Unit
) {
    // 主题选项直接在本页展开，不用弹窗（与其余 9 页一样都是内嵌内容区）
    var themeExpanded by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var showCrashLog by remember { mutableStateOf(false) }
    var crashText by remember { mutableStateOf("") }
    MSubPageScaffold {
        MSectionTitle(tr("外观", "Appearance"))
        MCard {
            MNavRow(
                Icons.Outlined.DarkMode, tr("深色 / 浅色模式", "Light / Dark mode"),
                when (state.themeMode) {
                    1 -> tr("浅色", "Light"); 2 -> tr("深色", "Dark"); else -> tr("跟随系统", "Follow system")
                }
            ) { themeExpanded = !themeExpanded }
            if (themeExpanded) {
                listOf(
                    0 to tr("跟随系统", "Follow system"),
                    1 to tr("浅色", "Light"),
                    2 to tr("深色", "Dark")
                ).forEach { (mode, label) ->
                    MThinDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onTheme(mode); themeExpanded = false }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label, fontSize = 14.sp,
                            color = if (state.themeMode == mode) MinimalUi.Blue else MinimalUi.Text,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.themeMode == mode) {
                            Icon(Icons.Outlined.Check, null, tint = MinimalUi.Blue, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            MThinDivider()
            MNavRow(Icons.Outlined.Translate, tr("语言", "Language"), tr("简体中文 / English", "Chinese / English"), onLanguage)
        }
        Spacer(Modifier.height(16.dp))
        MSectionTitle(tr("播放", "Playback"))
        MCard {
            MNavRow(Icons.Outlined.MusicNote, tr("播放模式", "Play mode"), tr("点击切换：列表循环 / 单曲循环 / 随机 / 顺序", "Tap to cycle: Loop all / Repeat one / Shuffle / Sequence"), onPlayMode)
            MThinDivider()
            MNavRow(Icons.Outlined.GraphicEq, tr("音效库", "Sound effects"), tr("银河音效 / 空间音效 / 调音台，自选即生效", "Galaxy / Spatial / Console · pick your own"), onEq)
            MThinDivider()
            MNavRow(Icons.Outlined.Schedule, tr("睡眠定时", "Sleep timer"), tr("点击切换：关闭 / 15 / 30 / 45 / 60 / 90 分钟", "Tap to cycle sleep timer"), onSleep)
            MThinDivider()
            MNavRow(
                Icons.Outlined.NotificationsActive, tr("音乐闹钟", "Music alarm"),
                MusicPlayer.alarmText()?.let { "%02d:%02d".format(it.first, it.second) }
                    ?: tr("未设置，到点淡入播放收藏", "Off · fades in favorites at the set time"),
                onAlarm
            )
            MThinDivider()
            MSwitchRow(
                Icons.Outlined.Waves, tr("摇一摇切歌", "Shake to skip"),
                tr("播放中摇动手机切到下一首", "Shake the phone to play the next song"),
                MusicPlayer.shakeNextOn
            ) { on ->
                MusicPlayer.setShakeNext(on)
                state.toast(
                    if (on) tr("摇一摇切歌已开启，播放中生效", "Shake to skip on, works while playing")
                    else tr("摇一摇切歌已关闭", "Shake to skip off")
                )
            }
            MThinDivider()
            MSwitchRow(
                Icons.Outlined.ScreenRotation, tr("翻腕静音", "Flip to pause"),
                tr("屏幕朝下放平 1.5 秒自动暂停", "Face-down for 1.5s pauses playback"),
                MusicPlayer.flipPauseOn
            ) { on ->
                MusicPlayer.setFlipPause(on)
                state.toast(
                    if (on) tr("屏幕朝下放平 1.5 秒自动暂停", "Face-down for 1.5s pauses playback")
                    else tr("翻腕静音已关闭", "Flip to pause off")
                )
            }
            MThinDivider()
            MNavRow(Icons.Outlined.QueueMusic, tr("播放列表视图", "Playlist view"), tr("全部歌曲 / 最近播放 / 我的收藏", "All / Recent / Favorites"), onPlaylistView)
            MThinDivider()
            MNavRow(Icons.Outlined.GraphicEq, tr("音质", "Audio quality"), tr("标准 · 由音源文件决定", "Standard · depends on source")) {
                state.toast(tr("音质由歌曲文件决定，网页获取时以音源最高可用码率为准", "Quality depends on the source file"))
            }
            MThinDivider()
            // 音频焦点策略：允许时来电/导航/别的播放器会让本机音乐暂停，
            // 不允许时本 App 收到焦点丢失也不暂停（系统仍可能混音）。
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, tint = MinimalUi.Sub, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("允许其他声音打断", "Allow interruptions"), fontSize = 15.sp, color = MinimalUi.Text)
                    Text(
                        if (MusicPlayer.interruptible)
                            tr("来电、导航、其他音乐播放时自动暂停，结束后继续", "Pause for calls, navigation and other music, then resume")
                        else
                            tr("不会被其他声音暂停；可能和系统其他音频同时播放", "Never paused by other audio; may play alongside it"),
                        fontSize = 11.sp, color = MinimalUi.Muted, lineHeight = 15.sp
                    )
                }
                Switch(
                    checked = MusicPlayer.interruptible,
                    onCheckedChange = { allow ->
                        MusicPlayer.setInterruptionsAllowed(allow)
                        state.toast(
                            if (allow)
                                tr("已允许其他声音打断音乐", "Interruptions allowed")
                            else
                                tr("已关闭打断：其他声音不会暂停当前音乐", "Interruptions disabled")
                        )
                    }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        MSectionTitle(tr("导入与歌词", "Import & lyrics"))
        MCard {
            MNavRow(Icons.Outlined.CloudSync, tr("导入我的音乐", "Import my music"), tr("从网易云 / QQ 音乐一键导入收藏歌单", "Import favorites from Netease / QQ Music"), onImport)
            MThinDivider()
            MNavRow(Icons.Outlined.Lyrics, tr("导入 .lrc 歌词", "Import .lrc lyrics"), tr("为当前歌曲手动指定歌词文件", "Attach a lyric file to the current song"), onImportLrc)
            MThinDivider()
            MNavRow(Icons.Outlined.DeleteOutline, tr("移除已导入的歌词", "Remove imported lyrics"), tr("恢复在线自动匹配", "Restore online matching"), onRemoveImportedLyrics)
            MThinDivider()
            MNavRow(Icons.Outlined.Folder, tr("清空导入的歌曲", "Clear imported songs"), tr("只删除 App 内导入的音频文件", "Removes only songs imported into the app"), onClearImportedSongs)
        }
        Spacer(Modifier.height(16.dp))
        MSectionTitle(tr("诊断", "Diagnostics"))
        MCard {
            MNavRow(
                Icons.Outlined.BugReport, tr("崩溃日志", "Crash log"),
                tr("查看最近闪退记录，可复制反馈", "View recent crashes, copy to share")
            ) {
                crashText = com.example.my.data.CrashLog.readLog(ctx)
                showCrashLog = true
            }
        }
        Spacer(Modifier.height(16.dp))
        MSectionTitle(tr("通知与存储", "Notifications & storage"))
        MCard {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Notifications, null, tint = MinimalUi.Sub, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("音乐播放通知", "Playback notification"), fontSize = 15.sp, color = MinimalUi.Text)
                    Text(tr("通知栏显示歌曲与播放控制", "Show song and controls"), fontSize = 11.sp, color = MinimalUi.Muted)
                }
                Switch(checked = notifEnabled, onCheckedChange = onNotif)
            }
            MThinDivider()
            MNavRow(Icons.Outlined.CleaningServices, tr("清理缓存", "Clear cache"), cacheSummary, onClearCache)
        }
        Spacer(Modifier.height(16.dp))
        MSectionTitle(tr("隐私与关于", "Privacy & about"))
        MCard(modifier = Modifier.padding(bottom = 18.dp)) {
            MNavRow(Icons.Outlined.PrivacyTip, tr("权限与隐私", "Permissions & privacy"), tr("在系统设置中管理", "Manage in system settings"), onPrivacy)
            MThinDivider()
            MNavRow(Icons.Outlined.Info, tr("关于 MY足迹音乐", "About MY Trace Music"), tr("v1.0.0 · 简单听音乐", "v1.0.0 · Just listen")) {
                state.toast(tr("MY足迹 · 每一步足迹都是独家回忆", "MY Trace · every step is a memory"))
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showCrashLog) {
        AlertDialog(
            onDismissRequest = { showCrashLog = false },
            title = { Text(tr("崩溃日志", "Crash log"), fontSize = 16.sp) },
            text = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(crashText, fontSize = 10.sp, color = MinimalUi.Muted)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", crashText))
                    }
                    state.toast(tr("已复制崩溃日志", "Crash log copied"))
                    showCrashLog = false
                }) { Text(tr("复制", "Copy")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    com.example.my.data.CrashLog.clear(ctx)
                    crashText = com.example.my.data.CrashLog.readLog(ctx)
                }) { Text(tr("清空", "Clear")) }
            }
        )
    }
}

// ============================================================
// 二级页通用骨架：标题 + 关闭，内容纵向可滚
// ============================================================

@Composable
private fun MSubPageScaffold(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MinimalUi.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MPageSide)
    ) {
        // 标题与返回统一由外层顶栏承担，二级页不再画第二遍标题，避免重复表头
        Spacer(Modifier.height(8.dp))
        content()
    }
}
