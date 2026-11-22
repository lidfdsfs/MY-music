package com.example.my.ui.music

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.music.CommentFailReason
import com.example.my.data.music.CommentFetch
import com.example.my.data.music.CommentSort
import com.example.my.data.music.LocalSong
import com.example.my.data.music.NeteaseComment
import com.example.my.data.music.NeteaseCommentPrefs
import com.example.my.data.music.NeteaseComments
import com.example.my.ui.components.Avatar
import com.example.my.ui.components.ProvideShimmer
import com.example.my.ui.components.SkeletonCommentRow
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.AppPalette
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Layer
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 歌曲评论底部弹层。
 *
 * 视觉方向：暖米白底 + 赤陶橙作选中/强调色，不另起紫色体系；评论主体不用重卡片，
 * 用「头像 + 昵称 + 正文 + 楼中楼浅底」的轻量行式排版，长评论默认 5 行带「全文」，
 * 让一屏能扫到更多真实内容，同时保留呼吸感。
 */
@Composable
fun SongCommentsSheet(
    song: LocalSong?,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var sort by remember { mutableStateOf(CommentSort.RECOMMEND) }
    var comments by remember { mutableStateOf<List<NeteaseComment>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<CommentFailReason?>(null) }
    var errorDetail by remember { mutableStateOf<String?>(null) }
    var moreError by remember { mutableStateOf<String?>(null) }
    /** null = 还没拿到结果；true = 当前列表来自 NCMAPI；false = 来自公开接口兜底 */
    var viaApi by remember { mutableStateOf<Boolean?>(null) }
    var cursor by remember { mutableStateOf<Long?>(null) }
    var retryTick by remember { mutableIntStateOf(0) }
    var apiVersion by remember { mutableIntStateOf(0) }
    var apiDialog by remember { mutableStateOf(false) }
    val configuredBase = NeteaseCommentPrefs.apiBase(ctx)

    val accent = AppPalette.value.terracotta
    val ink = AppPalette.value.textPrimary
    val sub = AppPalette.value.textSecondary
    val muted = AppPalette.value.textMuted
    val surface = AppPalette.value.surface
    val outline = AppPalette.value.outline
    // 评论弹层盖在全屏播放页之上：底色必须实心。
    // 旧写法取 AppPalette.background，自定义背景态下是 Transparent，
    // 黑胶唱片 / 歌名 / 进度条会整个透上来和评论文字叠在一起。
    val sheetBg = Layer.Opaque

    fun resetList() {
        comments = emptyList()
        total = 0
        page = 0
        hasMore = false
        error = null
        errorDetail = null
        moreError = null
        viaApi = null
        cursor = null
    }

    fun loadMore() {
        val s = song ?: return
        if (loading || loadingMore || !hasMore) return
        loadingMore = true
        moreError = null
        scope.launch {
            val r = withTimeoutOrNull(18_000L) {
                NeteaseComments.load(
                    ctx, s, sort, page + 1, 20, cursor,
                    force = retryTick > 0
                )
            }
            loadingMore = false
            when (r) {
                is CommentFetch.Ok -> {
                    viaApi = r.page.viaApi
                    val incoming = r.page.comments
                    if (incoming.isNotEmpty()) {
                        val existing = comments.map { it.id }.toHashSet()
                        comments = comments + incoming.filterNot { it.id in existing }
                    }
                    total = maxOf(total, r.page.total)
                    page = page + 1
                    hasMore = r.page.hasMore
                    cursor = r.page.cursor ?: cursor
                }
                is CommentFetch.Fail -> {
                    // 分页失败只影响底部这一点，不把已经读出来的列表整体换成错误页
                    moreError = r.detail?.take(80) ?: tr("加载更多失败", "Could not load more")
                }
                null -> {
                    moreError = tr("请求超时", "Request timed out")
                }
            }
        }
    }

    LaunchedEffect(song?.id, song?.title, song?.artist, sort, retryTick, apiVersion) {
        resetList()
        val s = song ?: return@LaunchedEffect
        loading = true
        val r = withTimeoutOrNull(20_000L) {
            NeteaseComments.load(ctx, s, sort, 1, 20, null, force = retryTick > 0)
        }
        loading = false
        when (r) {
            is CommentFetch.Ok -> {
                viaApi = r.page.viaApi
                comments = r.page.comments
                total = r.page.total
                page = 1
                hasMore = r.page.hasMore
                cursor = r.page.cursor
            }
            is CommentFetch.Fail -> {
                error = r.reason
                errorDetail = r.detail
            }
            null -> {
                error = CommentFailReason.NETWORK
                errorDetail = "请求超时"
            }
        }
    }

    LaunchedEffect(song?.id, sort) {
        listState.scrollToItem(0)
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
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(sheetBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
        ) {
            // 拖动条
            Box(
                Modifier
                    .padding(top = 9.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(38.dp, 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(muted.copy(alpha = 0.45f))
            )

            // 标题区：歌名 + 总数 + 接口状态
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 6.dp, top = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("评论", "Comments"),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = ink
                        )
                        if (total > 0) {
                            Spacer(Modifier.width(7.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(accent.copy(alpha = 0.12f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    formatCommentCount(total),
                                    fontSize = 10.5.sp,
                                    color = accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (song == null) tr("还没有正在播放的歌曲", "No song is playing")
                        else "${song.title} · ${song.artist}",
                        fontSize = 11.5.sp,
                        color = sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    when (viaApi) {
                                        true -> accent
                                        false -> muted.copy(alpha = 0.55f)
                                        null -> if (configuredBase != null) accent else muted.copy(alpha = 0.55f)
                                    }
                                )
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when (viaApi) {
                                true -> tr("NeteaseCloudMusicApi", "NeteaseCloudMusicApi")
                                false -> tr("网易云公开接口兜底", "Netease public fallback")
                                null -> if (configuredBase != null) {
                                    tr("NeteaseCloudMusicApi · 待连接", "NeteaseCloudMusicApi · connecting")
                                } else {
                                    tr("网易云公开评论接口", "Netease public comments")
                                }
                            },
                            fontSize = 9.5.sp,
                            color = muted
                        )
                    }
                }
                IconButton(onClick = { apiDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = tr("评论接口设置", "Comment API settings"),
                        tint = muted,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = tr("关闭", "Close"),
                        tint = sub,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // 排序：推荐 / 热度 / 最新
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CommentSort.entries.forEach { entry ->
                    val selected = sort == entry
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) accent.copy(alpha = 0.13f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) accent.copy(alpha = 0.45f) else outline,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { if (!selected) sort = entry }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tr(entry.labelZh, entry.labelEn),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) accent else sub
                        )
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(outline.copy(alpha = 0.75f))
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    song == null -> CommentsEmptyState(
                        title = tr("还没有正在播放的歌曲", "Nothing is playing"),
                        body = tr("先去播放一首歌，再来看看大家怎么说。", "Play a song first.")
                    )
                    loading -> CommentsLoading(muted)
                    error != null -> CommentsErrorState(
                        reason = error!!,
                        detail = errorDetail,
                        retry = { retryTick++ },
                        muted = muted,
                        sub = sub,
                        accent = accent
                    )
                    comments.isEmpty() -> CommentsEmptyState(
                        title = tr("这首歌还没有评论", "No comments yet"),
                        body = tr(
                            "换个排序看看，或者过一会儿再来。",
                            "Try another sort, or come back later."
                        )
                    )
                    else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(comments, key = { it.id }) { c ->
                            CommentRow(c, accent, ink, sub, muted)
                        }
                        item {
                            CommentsFooter(
                                loadingMore = loadingMore,
                                hasMore = hasMore,
                                total = total,
                                loaded = comments.size,
                                moreError = moreError,
                                onLoadMore = { loadMore() },
                                muted = muted,
                                accent = accent,
                                outline = outline
                            )
                        }
                    }
                }
            }
        }
    }

    if (apiDialog) {
        CommentApiDialog(
            current = configuredBase,
            onDismiss = { apiDialog = false },
            onSaved = {
                apiDialog = false
                apiVersion++
            }
        )
    }
}

@Composable
private fun CommentRow(
    c: NeteaseComment,
    accent: Color,
    ink: Color,
    sub: Color,
    muted: Color
) {
    var expanded by remember(c.id) { mutableStateOf(false) }
    var overflow by remember(c.id) { mutableStateOf(false) }
    val likes = formatCommentCount(c.likedCount)

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 13.dp),
            verticalAlignment = Alignment.Top
        ) {
            Avatar(
                text = c.nickname,
                size = 36.dp,
                background = accent.copy(alpha = 0.85f),
                imageUrl = c.avatarUrl.takeIf { it.isNotBlank() }
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.nickname,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (c.hot) {
                        Spacer(Modifier.width(5.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accent.copy(alpha = 0.12f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                tr("热评", "Hot"),
                                fontSize = 8.5.sp,
                                color = accent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                // 正文：默认 5 行，超过才出现「全文」
                Spacer(Modifier.height(5.dp))
                Text(
                    c.content,
                    fontSize = 14.5.sp,
                    lineHeight = 23.sp,
                    color = ink,
                    maxLines = if (expanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { overflow = it.hasVisualOverflow }
                )
                if (overflow || expanded) {
                    Text(
                        if (expanded) tr("收起", "Less") else tr("全文", "More"),
                        fontSize = 11.sp,
                        color = accent,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .clickable { expanded = !expanded }
                    )
                }

                // 楼中楼只放前 3 条，显示「回复 @昵称：内容」，不做无限嵌套
                if (c.replies.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Bg.Surface)
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        c.replies.take(3).forEach { r ->
                            Text(
                                buildString {
                                    append("回复 @").append(r.nickname).append("：")
                                    append(r.content)
                                },
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = sub,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (c.replyCount > c.replies.size) {
                            Text(
                                tr("共 ${c.replyCount} 条回复", "${c.replyCount} replies"),
                                fontSize = 10.sp,
                                color = muted
                            )
                        }
                    }
                }

                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatCommentTime(c.timeMs),
                        fontSize = 10.sp,
                        color = muted
                    )
                    if (c.location.isNotBlank()) {
                        Text(" · ", fontSize = 10.sp, color = muted.copy(alpha = 0.7f))
                        Text(c.location, fontSize = 10.sp, color = muted)
                    }
                    Spacer(Modifier.weight(1f))
                    if (likes.isNotEmpty()) {
                        Icon(
                            Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = if (c.liked) accent else muted.copy(alpha = 0.8f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(likes, fontSize = 10.sp, color = if (c.liked) accent else muted)
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 64.dp)
                .height(0.8.dp)
                .background(AppPalette.value.outline.copy(alpha = 0.55f))
        )
    }
}

@Composable
private fun CommentsFooter(
    loadingMore: Boolean,
    hasMore: Boolean,
    total: Int,
    loaded: Int,
    moreError: String?,
    onLoadMore: () -> Unit,
    muted: Color,
    accent: Color,
    outline: Color
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            loadingMore -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = accent,
                    strokeWidth = 1.5.dp
                )
                Spacer(Modifier.width(7.dp))
                Text(tr("正在加载更多评论…", "Loading more…"), fontSize = 11.sp, color = muted)
            }
            moreError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    tr("加载更多失败，点此重试", "Could not load more. Tap to retry"),
                    fontSize = 11.sp,
                    color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onLoadMore() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(moreError, fontSize = 9.sp, color = muted, maxLines = 2, textAlign = TextAlign.Center)
            }
            hasMore -> Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, outline, RoundedCornerShape(18.dp))
                    .clickable { onLoadMore() }
                    .padding(horizontal = 22.dp, vertical = 8.dp)
            ) {
                Text(
                    tr("加载更多评论", "Load more comments"),
                    fontSize = 12.sp,
                    color = accent,
                    fontWeight = FontWeight.Medium
                )
            }
            else -> Text(
                tr("已经到底了 · 共 ${formatCommentCount(maxOf(total, loaded))} 条", "The end · ${formatCommentCount(maxOf(total, loaded))} total"),
                fontSize = 10.5.sp,
                color = muted,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(2.dp))
        if (loaded > 0) {
            Text(
                tr("已加载 $loaded 条", "$loaded loaded"),
                fontSize = 9.5.sp,
                color = muted.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun CommentsLoading(muted: Color) {
    Column(
        Modifier.fillMaxSize().padding(top = 54.dp, start = 18.dp, end = 18.dp)
    ) {
        Text(tr("正在读取网易云评论…", "Reading Netease comments…"), fontSize = 11.5.sp, color = muted)
        Spacer(Modifier.height(4.dp))
        Text(tr("首次打开需要先匹配这首歌的网易云 ID", "First open may need to match the song ID"), fontSize = 9.5.sp, color = muted.copy(alpha = 0.8f))
        Spacer(Modifier.height(12.dp))
        // 评论加载用骨架行还原「头像 + 三行文字」，不再整页转圈
        ProvideShimmer {
            Column {
                repeat(4) { SkeletonCommentRow() }
            }
        }
    }
}

@Composable
private fun CommentsEmptyState(title: String, body: String) {
    val ink = AppPalette.value.textPrimary
    val muted = AppPalette.value.textMuted
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(AppPalette.value.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = muted,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 11.5.sp, lineHeight = 18.sp, color = muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CommentsErrorState(
    reason: CommentFailReason,
    detail: String?,
    retry: () -> Unit,
    muted: Color,
    sub: Color,
    accent: Color
) {
    val title = when (reason) {
        CommentFailReason.NETWORK -> tr("网络连接失败", "Network unavailable")
        CommentFailReason.NO_SONG -> tr("没在网易云找到这首歌", "Song not found on Netease")
        CommentFailReason.BAD_API -> tr("评论服务暂不可用", "Comment service unavailable")
        CommentFailReason.PARSE -> tr("评论数据解析失败", "Could not read comments")
        CommentFailReason.EMPTY -> tr("暂时没有评论", "No comments")
    }
    val body = when (reason) {
        CommentFailReason.NETWORK -> tr(
            "检查网络后再试；如果用了自建评论接口，确认服务已启动且地址可访问。",
            "Check the network, or make sure your self-hosted API is running."
        )
        CommentFailReason.NO_SONG -> tr(
            "同名翻唱/现场版容易匹配错。可在播放页长按歌名改成更准确的「歌名」后重试。",
            "Covers can match wrong. Long-press the title to correct it and retry."
        )
        CommentFailReason.BAD_API -> tr(
            "接口返回异常。可在右上角设置里换用官方公开接口，或检查 NeteaseCloudMusicApi 地址。",
            "Bad API response. Switch to the public endpoint in settings."
        )
        else -> tr("稍后再试。", "Try again later.")
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(13.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppPalette.value.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 11.5.sp, lineHeight = 18.sp, color = sub, textAlign = TextAlign.Center)
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                detail.take(120),
                fontSize = 9.5.sp,
                lineHeight = 14.sp,
                color = muted,
                textAlign = TextAlign.Center,
                maxLines = 3
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                tr("重新加载", "Retry"),
                fontSize = 12.sp,
                color = accent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { retry() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun CommentApiDialog(
    current: String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(current.orEmpty()) }
    var hint by remember { mutableStateOf<String?>(null) }
    val accent = AppPalette.value.terracotta
    val ink = AppPalette.value.textPrimary
    val sub = AppPalette.value.textSecondary
    val muted = AppPalette.value.textMuted
    val outline = AppPalette.value.outline

    MusicDialogShell(
        onDismiss = onDismiss,
        title = tr("评论接口", "Comment API"),
        subtitle = tr("填 NeteaseCloudMusicApi 地址，留空改用网易云公开接口", "NeteaseCloudMusicApi base URL; leave empty for public fallback")
    ) {
        Text(
            tr("App 禁止明文 HTTP，地址必须是 https:// 开头。",
               "Only https:// is allowed by the app."),
            fontSize = 10.5.sp,
            lineHeight = 16.sp,
            color = muted
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AppPalette.value.surfaceVariant)
                .border(1.dp, outline, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
            if (text.isBlank()) {
                Text(
                    "https://your-ncm-api.example.com",
                    fontSize = 12.sp,
                    color = muted.copy(alpha = 0.7f)
                )
            }
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    hint = null
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp, color = ink),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            tr("将使用 /comment/new 与 /comment/hot；接口需允许只读访问。",
               "Uses /comment/new and /comment/hot; read-only access is enough."),
            fontSize = 9.5.sp,
            lineHeight = 14.sp,
            color = muted
        )
        hint?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 10.sp, color = AppPalette.value.danger)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (current != null) {
                TextButton(onClick = {
                    NeteaseCommentPrefs.setApiBase(ctx, "")
                    onSaved()
                }) {
                    Text(tr("改用公开接口", "Use public API"), fontSize = 12.sp, color = sub)
                }
            }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = {
                val ok = NeteaseCommentPrefs.setApiBase(ctx, text)
                if (ok) {
                    onSaved()
                } else {
                    hint = tr(
                        "地址无效：需要 https:// 开头、不含账号密码/问号/锚点。",
                        "Invalid URL: https:// with no userinfo, query or fragment."
                    )
                }
            }) {
                Text(tr("保存", "Save"), fontSize = 12.sp, color = accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------- 纯格式化函数：UI 与单测共用 ----------

/** 1.2万 / 12.3万 / 1.1亿；0 显示空串，避免评论区出现一排 0 */
internal fun formatCommentCount(n: Int): String {
    if (n <= 0) return ""
    if (n < 10_000) return n.toString()
    if (n < 100_000_000) {
        val v = n / 10_000.0
        val s = String.format(Locale.CHINA, "%.1f", v)
        return s.removeSuffix(".0") + "万"
    }
    val v = n / 100_000_000.0
    val s = String.format(Locale.CHINA, "%.1f", v)
    return s.removeSuffix(".0") + "亿"
}

internal fun formatCommentTime(ms: Long): String {
    if (ms <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - ms
    if (diff < 60_000L) return tr("刚刚", "Just now")
    if (diff < 3_600_000L) return tr("${diff / 60_000L} 分钟前", "${diff / 60_000L}m ago")
    if (diff < 86_400_000L) return tr("${diff / 3_600_000L} 小时前", "${diff / 3_600_000L}h ago")
    if (diff < 30L * 86_400_000L) return tr("${diff / 86_400_000L} 天前", "${diff / 86_400_000L}d ago")
    return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(ms))
}
