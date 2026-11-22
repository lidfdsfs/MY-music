package com.example.my.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.my.data.microfeed.MicroFeedApi
import com.example.my.data.music.BgmPlayback
import com.example.my.model.Footprint
import com.example.my.model.MicroPost
import com.example.my.state.AppState
import com.example.my.ui.components.BlurInAsyncImage
import com.example.my.ui.components.MemoryMediaBlock
import com.example.my.ui.components.VideoBlurBackdrop
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.deband
import com.example.my.ui.theme.ImageSampling
import com.example.my.ui.theme.Ink
import com.example.my.ui.theme.blackScrim
import com.example.my.ui.theme.Layer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * 微足迹：抖音式全屏公开足迹流。
 *
 * 内容真源是 [AppState.microPosts]，与个人 Footprint 的 Room 数据完全隔离。
 * 视频页复用统一播放真源 MemoryMediaBlock（静音、自动播、点画面暂停）；
 * 图片页支持左右翻图与双击点赞。所有互动只改本地公开池快照。
 */
@Composable
fun MicroFeedScreen(state: AppState) {
    val posts = state.visibleMicroPosts()
    val pagerState = rememberPagerState(pageCount = { posts.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()
    var showPublish by remember { mutableStateOf(false) }
    var pendingId by remember { mutableStateOf<String?>(null) }
    var expandedIds by remember { mutableStateOf(setOf<String>()) }

    // 「我的」页入口：跳到「我的」分段并拉起发布面板一次
    LaunchedEffect(state.microFeedPublishTick) {
        if (state.microFeedPublishTick > 0) {
            state.microFeedTab = 3
            state.microFeedTagFilter = null
            showPublish = true
        }
    }
    // 切分段 / 话题变化：先向独立服务要这一屏的内容（拿不到就回落到本地池）
    LaunchedEffect(state.microFeedTab, state.microFeedQuery, state.microFeedTagFilter) {
        state.refreshMicroFeedRemote()
        if (posts.isNotEmpty()) pagerState.scrollToPage(0)
    }
    // 快滑到底前补一页，抖音式无限流
    LaunchedEffect(pagerState.settledPage, posts.size) {
        if (posts.size > 0 && pagerState.settledPage >= posts.size - 2) state.loadMoreMicroRemote()
    }
    // 停在某条即记一次浏览（服务端按用户去重，重复调用无副作用）
    LaunchedEffect(pagerState.settledPage, posts.size) {
        posts.getOrNull(pagerState.settledPage)?.let { state.markMicroViewed(it.id) }
    }
    LaunchedEffect(pendingId, posts) {
        val id = pendingId ?: return@LaunchedEffect
        val index = posts.indexOfFirst { it.id == id }
        if (index >= 0) {
            pagerState.scrollToPage(index)
            pendingId = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (posts.isEmpty()) {
            MicroFeedEmpty(state = state, tab = state.microFeedTab)
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                val post = posts.getOrNull(page) ?: return@VerticalPager
                MicroPostPage(
                    state = state,
                    post = post,
                    active = pagerState.settledPage == page,
                    expanded = post.id in expandedIds,
                    onToggleExpand = {
                        expandedIds = if (post.id in expandedIds) expandedIds - post.id else expandedIds + post.id
                    }
                )
            }
        }

        MicroFeedTopBar(state = state)

        if (state.microFeedSearchOpen) {
            MicroSearchOverlay(
                state = state,
                // 在线时只搜远端当前池，离线才搜本地示例池；
                // 否则在线点开本地示例会切到推荐流却定位不到。
                allPosts = if (state.microFeedOnline) state.remoteMicroFeed() else state.microPosts,
                onDismiss = { state.closeMicroSearch() },
                onPick = { id ->
                    state.closeMicroSearch()
                    state.microFeedTab = 1
                    state.microFeedTagFilter = null
                    // 远端帖可能不在当前页里：先聚焦（会拉单条并插到列表头），再让 pager 去定位
                    state.focusMicroPost(id)
                    pendingId = id
                }
            )
        }

        if (showPublish) {
            MicroPublishSheet(state = state, onDismiss = { showPublish = false })
        }

        state.microFeedCommentTarget?.let { id ->
            state.microPostById(id)?.let { post ->
                MicroCommentsSheet(
                    post = post,
                    state = state,
                    autoFocusInput = state.microCommentFocus,
                    onDismiss = { state.closeMicroComments() }
                )
            }
        }
        state.microFeedShareTarget?.let { id ->
            state.microPostById(id)?.let { post ->
                MicroShareSheet(post = post, state = state, onDismiss = { state.closeMicroShare() })
            }
        }
        state.microFeedAuthorTarget?.let { authorId ->
            MicroAuthorSheet(
                state = state,
                authorId = authorId,
                onDismiss = { state.closeMicroAuthor() },
                onPickPost = { id ->
                    state.closeMicroAuthor()
                    state.microFeedTab = 1
                    state.microFeedTagFilter = null
                    state.focusMicroPost(id)
                    pendingId = id
                }
            )
        }
    }
}

/** 顶部：关注 / 推荐 / 同城 / 我的 + 服务状态 + 搜索 */
@Composable
private fun BoxScope.MicroFeedTopBar(state: AppState) {
    val tabs = listOf(tr("关注", "Following"), tr("推荐", "For you"), tr("同城", "Nearby"), tr("我的", "Mine"))
    var showServer by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .statusBarsPadding()
            .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            tabs.forEachIndexed { index, label ->
                val selected = state.microFeedTab == index
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = label,
                        color = if (selected) Ink.OnCover else Ink.OnCoverSub,
                        fontSize = if (selected) 17.sp else 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.clickable {
                            state.microFeedTab = index
                            state.microFeedTagFilter = null
                            state.microFeedQuery = ""
                        }
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier
                            .size(width = 20.dp, height = 2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (selected) Brand.Terracotta else Color.Transparent)
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        // 服务状态：绿点=已连上微足迹服务；灰点=本地模式。点开可改服务地址
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x55000000))
                .clickable { showServer = true }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (state.microFeedOnline) Color(0xFF4ADE80) else Ink.OnCoverSub)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (state.microFeedOnline) tr("社区", "Live") else tr("本地", "Local"),
                    color = Ink.OnCover, fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.Outlined.Search,
            tr("搜索", "Search"),
            tint = Ink.OnCover,
            modifier = Modifier
                .size(24.dp)
                .clickable { state.openMicroSearch() }
        )
    }

    if (showServer) {
        MicroServerSheet(state = state, onDismiss = { showServer = false })
    }
}

/**
 * 微足迹服务设置：显示当前地址与连通性，允许改成自己的部署地址。
 * 保存后立即重连一次，不用杀进程。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MicroServerSheet(state: AppState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(MicroFeedApi.baseUrl(context)) }
    var checking by remember { mutableStateOf(false) }
    var diag by remember { mutableStateOf<MicroFeedApi.Diagnosis?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Layer.Opaque,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Ink.OnGlassSub) }
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp)) {
            Text(tr("微足迹服务", "Micro Footprints server"), color = Ink.OnGlass, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                tr(
                    "公开流、互动、搜索与媒体都由独立的微足迹服务提供；连不上时自动回落到本机内容。",
                    "The community feed runs on a standalone service; if unreachable the app falls back to local content."
                ),
                color = Ink.OnGlassSub, fontSize = 11.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape)
                    .background(if (state.microFeedOnline) Color(0xFF4ADE80) else Ink.OnGlassSub))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.microFeedOnline) tr("已连接", "Connected") else tr("未连接（本地模式）", "Offline (local mode)"),
                    color = Ink.OnGlass, fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            MicroField(draft, { draft = it }, tr("服务地址，如 https://mf.example.com", "Server URL, e.g. https://mf.example.com"))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Brand.Terracotta)
                        .clickable {
                            MicroFeedApi.setBaseUrl(context, draft)
                            state.refreshMicroFeedRemote(force = true)
                            onDismiss()
                        }
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(tr("保存并重连", "Save & reconnect"), color = Ink.OnAccent, fontSize = 13.sp)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x22FFFFFF))
                        .clickable {
                            MicroFeedApi.setBaseUrl(context, null)
                            draft = MicroFeedApi.baseUrl(context)
                            state.refreshMicroFeedRemote(force = true)
                        }
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(tr("恢复默认", "Reset"), color = Ink.OnGlass, fontSize = 13.sp)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x22FFFFFF))
                        .clickable {
                            if (!checking) {
                                checking = true
                                diag = null
                                scope.launch {
                                    diag = MicroFeedApi.diagnose(context)
                                    checking = false
                                }
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(
                        if (checking) tr("检测中…", "Checking…") else tr("检测", "Test"),
                        color = Ink.OnGlass, fontSize = 13.sp
                    )
                }
            }
            // 体检结果：把「网络不通」和「验签不过」分开，二者界面上都显示「未连接」，但修法完全不同
            diag?.let { d ->
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x1AFFFFFF))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    DiagRow(
                        ok = d.reachable,
                        label = tr("网络与证书", "Network & TLS"),
                        value = if (d.reachable) tr("可达", "Reachable") else d.detail
                    )
                    DiagRow(
                        ok = d.healthOk,
                        label = tr("健康检查 /healthz", "Health /healthz"),
                        value = if (d.healthOk) "ok" else tr("异常（HTTP ${d.status}）", "Failed (HTTP ${d.status})")
                    )
                    DiagRow(
                        ok = d.authorized,
                        label = tr("登录态验签", "Token verification"),
                        value = when {
                            d.authorized -> tr("通过", "Passed")
                            d.detail == "未登录" -> tr("未登录（先登录主账号）", "Not signed in")
                            d.status == 401 -> tr("失败：两边 SECRET_KEY 不一致", "Failed: SECRET_KEY mismatch")
                            else -> d.detail
                        }
                    )
                    if (d.reachable && !d.authorized && d.status == 401) {
                        Text(
                            tr(
                                "把主后端的 SECRET_KEY 写成微足迹服务的 MF_SECRET_KEY，重启容器后即可。",
                                "Copy the main backend's SECRET_KEY into MF_SECRET_KEY, then restart the service."
                            ),
                            color = Ink.OnGlassMuted, fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                tr(
                    "提示：正式环境请用 HTTPS；局域网调试地址需在 network_security_config 中放行明文。",
                    "Use HTTPS in production; LAN HTTP addresses must be allowlisted in network_security_config."
                ),
                color = Ink.OnGlassMuted, fontSize = 11.sp
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** 体检结果的一行：左侧圆点表示通过与否，右侧是具体状态 */
@Composable
private fun DiagRow(ok: Boolean, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (ok) Color(0xFF4ADE80) else Color(0xFFF87171))
        )
        Spacer(Modifier.width(7.dp))
        Text(label, color = Ink.OnGlassSub, fontSize = 11.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = value,
            color = if (ok) Ink.OnGlass else Color(0xFFFCA5A5),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 单条内容：媒体 + 右侧互动栏 + 底部文案 */
@Composable
private fun MicroPostPage(
    state: AppState,
    post: MicroPost,
    active: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit
) {
    var heartBurst by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(if (heartBurst) 1.35f else 0.8f, tween(160), label = "heart")
    val scope = rememberCoroutineScope()
    /** 弹幕开关：把评论/话题横向滚动叠在画面上 */
    var danmakuOn by remember { mutableStateOf(false) }
    /** 沉浸全屏观看：隐藏所有文字与互动，只留画面 */
    var immersive by remember { mutableStateOf(false) }

    // ---------- 远端媒体：服务端存的是 /media/... 相对地址，用前补成完整 URL ----------
    val context = LocalContext.current
    fun isServerPath(p: String) = p.startsWith("/media/") || p.startsWith("http://") || p.startsWith("https://")
    // 只有服务端媒体路径才拼微足迹域名；/data/user/、/storage/ 是本机绝对路径，拼了必然 404
    fun fullUrl(p: String): String = when {
        p.startsWith("http://") || p.startsWith("https://") -> p
        p.startsWith("/media/") || p.startsWith("/uploads/") -> MicroFeedApi.absolute(context, p)
        else -> p
    }
    val imageUrls = remember(post.imagePaths) { post.imagePaths.map { fullUrl(it) } }
    val videoUrl = remember(post.videoPath) { post.videoPath?.let { fullUrl(it) } }

    // ---------- 配乐：走全局单音源 BgmPlayback，只 attach / detach ----------
    // 本地足迹的 bgmPath 是私有目录里的文件，直接播；社区帖的配乐在服务端，
    // 先下到 cacheDir 再交给播放器（BgmPlayback 只吃本地路径，这样也顺带做到了离线可听）。
    val remoteBgm = post.bgmPath?.takeIf { isServerPath(it) }?.let { fullUrl(it) }
    var cachedBgm by remember(post.id, post.bgmPath) { mutableStateOf<String?>(null) }
    LaunchedEffect(remoteBgm, active) {
        if (remoteBgm != null && active && cachedBgm == null) {
            cachedBgm = MicroFeedApi.cacheMedia(context, remoteBgm, "bgm")
        }
    }
    val bgmFile = remember(cachedBgm, post.bgmPath) {
        val candidate = cachedBgm ?: post.bgmPath?.takeIf { !isServerPath(it) }
        candidate?.takeIf { it.isNotBlank() && File(it).exists() }
    }
    val bgmOwner = remember(post.id) { BgmPlayback.newOwnerId("microfeed") }
    val bgmPlaying = bgmFile != null && BgmPlayback.isPlaying && BgmPlayback.path == bgmFile
    DisposableEffect(bgmOwner, bgmFile, active) {
        // 只在当前页起播；滑走就摘掉 owner（最后一个界面离开时才真正释放播放器）
        if (bgmFile != null && active) {
            BgmPlayback.attach(bgmOwner, bgmFile, looping = true, autoplay = true)
        } else {
            BgmPlayback.detach(bgmOwner)
        }
        onDispose { BgmPlayback.detach(bgmOwner) }
    }

    fun likeFromDoubleTap() {
        if (!post.liked) state.toggleMicroLike(post.id)
        heartBurst = true
        scope.launch { delay(650); heartBurst = false }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ---------- 媒体层 ----------
        // 同一条足迹既可能有照片也有视频。以前是 if/else 二选一，视频帖里的照片
        // 会被整段吞掉；现在两者都有时走混合轮播，左右滑动就把照片和视频都看全。
        val hasMixedMedia = videoUrl != null && imageUrls.isNotEmpty()
        if (hasMixedMedia) {
            MicroMixedMediaCarousel(
                imagePaths = imageUrls,
                videoPath = videoUrl!!,
                place = post.place,
                bgmTitle = post.bgmTitle,
                muteVideo = bgmFile != null,
                active = active,
                onDoubleTap = { likeFromDoubleTap() }
            )
        } else if (post.isVideo) {
            val path = videoUrl
            val playable = path != null && (path.startsWith("http") || File(path).exists())
            if (playable) {
                // 全屏模糊铺底：横屏视频上下会留黑边，用首帧铺满整个画面，避免大片纯黑显空
                VideoBlurBackdrop(path, Modifier.fillMaxSize())
                MemoryMediaBlock(
                    videoPath = path,
                    videoIsLive = false,
                    bgmPath = null,
                    bgmTitle = post.bgmTitle,
                    modifier = Modifier.fillMaxSize(),
                    maxHeight = 900.dp,
                    // 有配乐时静音视频避免双声；没配乐就让视频用自己的原声
                    muteVideo = bgmFile != null,
                    autoPlay = active,
                    active = active
                )
            } else {
                // 云端恢复中途：视频还是远程地址，还没落到本机；说清楚，别让用户以为文件丢了
                val remote = path?.startsWith("http") == true
                MicroMediaFallback(
                    post = post,
                    reason = if (remote) tr("视频正在从云端缓存到本机，稍后自动出现", "Caching this video from the cloud…")
                    else tr("视频文件已不在本机", "Video file is missing")
                )
            }
        } else if (imageUrls.isNotEmpty()) {
            MicroImageCarousel(
                paths = imageUrls,
                place = post.place,
                onDoubleTap = { likeFromDoubleTap() }
            )
        } else {
            // 本人足迹里有很多「只有轨迹/文案、没有照片视频」的记录，它们也是这个库里的真实一条：
            // 用一张文字卡承接，别显示成「没有可用媒体」的故障态
            if (post.isMine) MicroTextPost(post)
            else MicroMediaFallback(post = post, reason = tr("这条足迹没有可用媒体", "No media in this post"))
        }

        // 覆盖层渐变：保证白字在任何照片上都可读（图片本身就是内容底，不属于主题叠层）
        // 多色阶黑 scrim 替代单段透明渐变，消除 OLED 上的条带色带
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(blackScrim(0.53f).reversed()))
                .deband()
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(360.dp)
                .background(Brush.verticalGradient(blackScrim(0.80f)))
                .deband()
        )

        AnimatedVisibility(
            visible = heartBurst,
            enter = scaleIn(tween(120)) + fadeIn(tween(120)),
            exit = scaleOut(tween(180)) + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                Icons.Filled.Favorite,
                tr("点赞", "Like"),
                tint = Brand.Terracotta,
                modifier = Modifier.size(88.dp * heartScale)
            )
        }

        // ---------- 弹幕层 ----------
        if (danmakuOn && !immersive) {
            val dmLines = (post.comments.map { it.text } + post.tags.map { "#$it" })
                .filter { it.isNotBlank() }
                .ifEmpty { listOf(post.place, post.caption.take(18)) }
            DanmakuOverlay(dmLines, Modifier.align(Alignment.TopCenter))
        }

        // ---------- 右侧互动栏（抖音同款：贴右下、等距竖排） ----------
        if (!immersive) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 112.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.clickable { state.openMicroAuthor(post.author.id) },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    MicroAvatar(post, size = 46.dp, onClick = { state.openMicroAuthor(post.author.id) })
                    // 关注徽标压在头像下沿
                    Box(
                        modifier = Modifier
                            .size(19.dp)
                            .clip(CircleShape)
                            .background(if (post.followed) Brand.Terracotta else Color(0xFFFE2C55))
                            .clickable { state.toggleMicroFollow(post.author.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (post.followed) {
                            Icon(Icons.Filled.Check, null, tint = Ink.OnAccent, modifier = Modifier.size(11.dp))
                        } else {
                            Icon(Icons.Outlined.PersonAdd, null, tint = Ink.OnAccent, modifier = Modifier.size(13.dp))
                        }
                    }
                }
                RailAction(
                    icon = if (post.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    label = compactCount(post.likeCount),
                    tint = if (post.liked) Color(0xFFFE2C55) else Ink.OnCover,
                    onClick = { state.toggleMicroLike(post.id) }
                )
                RailAction(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    label = compactCount(post.commentCount),
                    onClick = { state.openMicroComments(post.id) }
                )
                RailAction(
                    icon = if (post.favored) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    label = compactCount(post.favorites + if (post.favored) 1 else 0),
                    tint = if (post.favored) Brand.Terracotta else Ink.OnCover,
                    onClick = { state.toggleMicroFav(post.id) }
                )
                RailAction(
                    icon = Icons.Outlined.IosShare,
                    label = compactCount(post.shareCount),
                    onClick = { state.openMicroShare(post.id) }
                )
                // 旋转唱片 + 拍同款：抖音同款结构（有本地配乐时点唱片即播放/暂停，没有则进配乐页）
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    MicroMusicDisc(
                        playing = bgmPlaying,
                        hasAudio = bgmFile != null,
                        onClick = {
                            if (bgmFile != null) BgmPlayback.toggle()
                            else state.openScreen(com.example.my.state.ExtraScreen.MUSIC)
                        }
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tr("拍同款", "Same shot"),
                        color = Ink.OnCover,
                        fontSize = 10.sp,
                        modifier = Modifier.clickable {
                            state.updateUploadStudioCapture(true)
                            state.autoOpenCameraOnUpload = true
                            state.openUpload()
                        }
                    )
                }
            }

            // ---------- 全屏观看（视频）：抖音同款居中胶囊 ----------
            if (post.isVideo) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 262.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x66000000))
                        .clickable { immersive = true }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Fullscreen, null, tint = Ink.OnCover, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(tr("全屏观看", "Fullscreen"), color = Ink.OnCover, fontSize = 12.sp)
                }
            }

            // ---------- 底部：弹/推荐 → 作者·日期 → 文案 → 话题 → 音乐 ----------
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 76.dp, bottom = 92.dp)
            ) {
                // 弹幕开关 / 点击推荐（抖音同款小胶囊，自然排在文案上方，不做绝对定位避免压字）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (danmakuOn) Color(0xCCFE2C55) else Color(0x66000000))
                            .clickable { danmakuOn = !danmakuOn }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tr("弹", "DM"),
                            color = Ink.OnCover, fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x66000000))
                            .clickable { state.openMicroShare(post.id) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ThumbUp, null, tint = Ink.OnCover, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("点击推荐", "Recommend"), color = Ink.OnCover, fontSize = 11.sp)
                        Spacer(Modifier.width(2.dp))
                        Text("›", color = Ink.OnCoverSub, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "@${post.author.name}",
                        color = Ink.OnCover,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { state.openMicroAuthor(post.author.id) }
                    )
                    if (post.publishedAt > 0L) {
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "· " + android.text.format.DateFormat.format("yyyy年MM月dd日", post.publishedAt),
                            color = Ink.OnCoverSub, fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x55FFFFFF))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (post.isVideo) tr("视频", "Video") else tr("图文", "Photos"),
                            color = Color(0xFF1B1714),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = post.caption,
                    color = Ink.OnCover,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onToggleExpand() }
                )
                if (post.caption.length > 34) {
                    Text(
                        text = if (expanded) tr("收起", "less") else tr("...展开", "...more"),
                        color = Ink.OnCoverSub,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onToggleExpand() }
                    )
                }
                if (post.tags.isNotEmpty()) {
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        post.tags.take(3).forEach { tag ->
                            Text(
                                text = "#${tag}",
                                color = Color(0xFFD8E8FF),
                                fontSize = 12.sp,
                                modifier = Modifier.clickable { state.filterMicroTag(tag) }
                            )
                        }
                    }
                }
                post.bgmTitle?.let { title ->
                    Spacer(Modifier.height(7.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x33000000))
                            .clickable(enabled = bgmFile != null) { BgmPlayback.toggle() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (bgmPlaying) Icons.Filled.GraphicEq else Icons.Outlined.MusicNote,
                            contentDescription = tr("配乐", "Music"),
                            tint = if (bgmPlaying) Color(0xFFFFD54F) else Ink.OnCoverSub,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = buildString {
                                append(title)
                                post.bgmArtist?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                                if (bgmFile == null) {
                                    // 远端配乐需要先缓存到本机，不要把「还在下载」误报成「无音频」
                                    if (remoteBgm != null) append(tr("（配乐加载中）", " (loading audio…)"))
                                    else append(tr("（无音频）", " (no audio)"))
                                } else if (!bgmPlaying) append(tr("（已暂停，点一下续播）", " (tap to play)"))
                            },
                            color = Ink.OnCoverSub,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .widthIn(max = 210.dp)
                                .basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }
                }
            }

            // ---------- 相关搜索 + 评论输入条（抖音底部同款） ----------
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                val related = post.tags.firstOrNull() ?: post.place
                if (related.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x40000000))
                            .clickable {
                                state.microFeedQuery = related
                                state.openMicroSearch()
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Search, null, tint = Ink.OnCoverSub, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            tr("相关搜索 · $related", "Related · $related"),
                            color = Ink.OnCoverSub, fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text("›", color = Ink.OnCoverSub, fontSize = 14.sp)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x33FFFFFF))
                        .clickable { state.openMicroComments(post.id, focusInput = true) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tr("爱评论的人，运气不会差", "Say something nice"),
                        color = Color(0xB3FFFFFF), fontSize = 13.sp,
                        modifier = Modifier.weight(1f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Outlined.EmojiEmotions, null, tint = Ink.OnCoverSub, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Outlined.AlternateEmail, null, tint = Ink.OnCoverSub, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Outlined.Image, null, tint = Ink.OnCoverSub, modifier = Modifier.size(19.dp))
                }
            }
        } else {
            // 沉浸全屏：只留一个退出按钮，点画面任意处也能退出
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        immersive = false
                    }
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x66000000))
                        .clickable { immersive = false }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.FullscreenExit, null, tint = Ink.OnCover, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr("退出全屏", "Exit"), color = Ink.OnCover, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * 图片 + 视频混合轮播。
 *
 * 抖音式内容里，一条足迹可以同时有照片和视频；这里把视频作为最后一页接在照片后面，
 * 既不丢照片，也不把视频缩成角落小窗。只有停留在视频页时才创建/保留解码器，
 * 滑到照片页立即释放，避免 feed 里多个 MediaPlayer 同时存在。
 */
@Composable
private fun MicroMixedMediaCarousel(
    imagePaths: List<String>,
    videoPath: String,
    place: String,
    bgmTitle: String?,
    muteVideo: Boolean,
    active: Boolean,
    onDoubleTap: () -> Unit
) {
    val videoPage = imagePaths.size
    val pager = rememberPagerState(pageCount = { imagePaths.size + 1 })
    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            if (page < videoPage) {
                BlurInAsyncImage(
                    model = imagePaths[page],
                    contentDescription = place,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { onDoubleTap() })
                        },
                    contentScale = ContentScale.Crop
                )
            } else {
                val videoActive = active && pager.currentPage == videoPage
                MemoryMediaBlock(
                    videoPath = videoPath,
                    videoIsLive = false,
                    bgmPath = null,
                    bgmTitle = bgmTitle,
                    modifier = Modifier.fillMaxSize(),
                    maxHeight = 900.dp,
                    muteVideo = muteVideo,
                    autoPlay = videoActive,
                    active = videoActive
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 62.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x55000000))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text("${pager.currentPage + 1}/${imagePaths.size + 1}", color = Ink.OnCover, fontSize = 10.sp)
            if (pager.currentPage == videoPage) {
                Text("  视频", color = Ink.OnCover, fontSize = 10.sp)
            }
        }
    }
}

/** 图片轮播：左右翻图 + 双击点赞（与视频「点画面暂停」语义不冲突） */
@Composable
private fun MicroImageCarousel(paths: List<String>, place: String, onDoubleTap: () -> Unit) {
    val pager = rememberPagerState(pageCount = { paths.size })
    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            BlurInAsyncImage(
                model = paths[page],
                contentDescription = place,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { onDoubleTap() })
                    },
                contentScale = ContentScale.Crop
            )
        }
        if (paths.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 62.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x55000000))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text("${pager.currentPage + 1}/${paths.size}", color = Ink.OnCover, fontSize = 10.sp)
            }
        }
    }
}

/**
 * 无媒体的本人足迹（纯轨迹/纯文案）用的文字卡。
 * 底色沿用主题暖棕，不与照片争艳；只在大标题上放地点，避免和底部文案重复。
 */
@Composable
private fun MicroTextPost(post: MicroPost) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3A2A22), Color(0xFF14100E))))
            .deband(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 36.dp)
        ) {
            Icon(Icons.Outlined.LocationOn, null, tint = Brand.Terracotta, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = post.place.ifBlank { tr("我的足迹", "My footprint") },
                color = Ink.OnCover,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (post.city.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(post.city, color = Ink.OnCoverSub, fontSize = 13.sp)
            }
            if (post.publishedAt > 0L) {
                Spacer(Modifier.height(6.dp))
                Text(
                    android.text.format.DateFormat.format("yyyy年MM月dd日", post.publishedAt).toString(),
                    color = Ink.OnCoverSub, fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun MicroMediaFallback(post: MicroPost, reason: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF24212A), Color(0xFF0F0E11))))
            .deband(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.PlayCircleOutline, null, tint = Ink.OnCoverSub, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(10.dp))
            Text(reason, color = Ink.OnCoverSub, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("@${post.author.name} · ${post.place}", color = Ink.OnCoverSub, fontSize = 11.sp)
        }
    }
}

/**
 * 弹幕层：把评论/话题横向滚过画面上部。
 *
 * 每条独立相位、独立速度，视觉上不会整齐划一地"齐步走"；只在 draw 阶段读动画值，
 * 不触发重组。文字带半透明底，压在亮画面上也读得清。
 */
@Composable
private fun DanmakuOverlay(lines: List<String>, modifier: Modifier = Modifier) {
    if (lines.isEmpty()) return
    val transition = rememberInfiniteTransition(label = "danmaku")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 96.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        lines.take(5).forEachIndexed { index, text ->
            val travel = 2600 + index * 620
            val progress by transition.animateFloat(
                initialValue = 1.15f,
                targetValue = -1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = travel, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "dm$index"
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                val widthPx = constraints.maxWidth.toFloat()
                Text(
                    text = text,
                    color = Ink.OnCover,
                    fontSize = 13.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .graphicsLayer { translationX = progress * widthPx }
                        .background(Color(0x4D000000), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * 抖音式唱片：在播时匀速自转，暂停就停在当前角度（不弹回 0）。
 *
 * 用 Animatable 而不是 InfiniteTransition：暂停时要保留角度，无限动画没法中途停住。
 * 角度只在 graphicsLayer 的绘制 lambda 里读，不参与重组。
 */
@Composable
private fun MicroMusicDisc(playing: Boolean, hasAudio: Boolean, onClick: () -> Unit) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        while (true) {
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = tween(durationMillis = 9000, easing = LinearEasing)
            )
        }
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer { rotationZ = rotation.value }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Brand.Terracotta, Color(0xFF2B2B30))))
            .border(5.dp, Color(0x33FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (playing) Icons.Filled.GraphicEq else Icons.Outlined.MusicNote,
            contentDescription = if (hasAudio) tr("播放或暂停配乐", "Play or pause music") else tr("配乐", "Music"),
            tint = Ink.OnCover,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun RailAction(
    icon: ImageVector,
    label: String,
    tint: Color = Ink.OnCover,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            label,
            tint = tint,
            modifier = Modifier
                .size(31.dp)
                .clickable(onClick = onClick)
        )
        Spacer(Modifier.height(3.dp))
        Text(label, color = Ink.OnCover, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MicroAvatar(post: MicroPost, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val context = LocalContext.current
    val rawAvatar = post.author.avatarPath
    val avatar = rawAvatar?.let {
        if (it.startsWith("/media/") || it.startsWith("/uploads/")) MicroFeedApi.absolute(context, it) else it
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brand.Terracotta)
            .border(1.5.dp, Ink.OnCover, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (!avatar.isNullOrBlank()) {
            AsyncImage(
                model = avatar,
                contentDescription = post.author.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = ImageSampling
            )
        } else {
            Text(post.author.avatarText, color = Ink.OnAccent, fontSize = (size.value / 2.4f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun compactCount(n: Int): String = when {
    n >= 10_000 -> "${n / 10_000}.${(n % 10_000) / 1000}万"
    n >= 1000 -> "${n / 1000}.${(n % 1000) / 100}k"
    else -> n.toString()
}

@Composable
private fun MicroFeedEmpty(state: AppState, tab: Int) {
    val text = when (tab) {
        0 -> tr("关注的人还没有发布微足迹", "Nobody you follow has posted yet")
        2 -> tr("${state.currentMicroCity()} 暂时没有新足迹", "Nothing nearby yet")
        3 -> tr("还没有足迹记录", "No footprints yet")
        else -> tr("暂无微足迹", "No posts yet")
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Outlined.PlayCircleOutline, null, tint = Ink.OnCoverSub, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(14.dp))
            Text(text, color = Ink.OnCover, fontSize = 15.sp, textAlign = TextAlign.Center)
            if (tab == 3) {
                Spacer(Modifier.height(10.dp))
                Text(
                    tr(
                        "「我的微足迹」直接读你已有的足迹（含云端恢复的），只是换成竖屏排版",
                        "Mine mirrors your footprints (including cloud-restored ones) in a vertical feed"
                    ),
                    color = Ink.OnCoverSub, fontSize = 12.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Brand.Terracotta)
                        .clickable { state.openUpload() }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(tr("记录一段旅程", "Record a journey"), color = Ink.OnAccent, fontSize = 14.sp)
                }
            }
        }
    }
}

/* ---------------- 评论 / 分享 / 作者 / 搜索 / 发布 ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MicroCommentsSheet(
    post: MicroPost,
    state: AppState,
    autoFocusInput: Boolean,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(post.id) { state.loadMicroComments(post.id) }
    // 抖音：点底部“说点什么”直接进评论面板并弹键盘；点右侧评论数只看评论，不弹键盘。
    LaunchedEffect(autoFocusInput, post.id) {
        if (autoFocusInput) {
            delay(250)
            runCatching { inputFocus.requestFocus() }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Layer.Opaque,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Ink.OnGlassSub) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.76f)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Text(
                text = tr("${post.commentCount} 条评论", "${post.commentCount} comments"),
                color = Ink.OnGlass,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val hiddenComments = (post.baseComments - post.comments.size).coerceAtLeast(0)
                if (hiddenComments > 0) {
                    item {
                        Text(
                            text = tr("另有 $hiddenComments 条评论", "$hiddenComments more comments"),
                            color = Ink.OnGlassMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                items(post.comments, key = { it.id }) { c ->
                    Column {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Brand.Terracotta),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(c.authorName.take(1), color = Ink.OnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.authorName, color = Ink.OnGlassSub, fontSize = 12.sp)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = buildString {
                                        if (c.replyTo != null) append("回复 @").append(c.replyTo).append("：")
                                        append(c.text)
                                    },
                                    color = Ink.OnGlass,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text(c.time, color = Ink.OnGlassMuted, fontSize = 10.sp)
                                    Text(
                                        tr("回复", "Reply"),
                                        color = Ink.OnGlassMuted,
                                        fontSize = 10.sp,
                                        modifier = Modifier.clickable {
                                            replyTo = c.authorName
                                            runCatching { inputFocus.requestFocus() }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                if (post.comments.isEmpty()) {
                    item {
                        Text(
                            tr("来抢第一条沙发吧", "Be the first to comment"),
                            color = Ink.OnGlassMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(0.5.dp, Ink.OnGlassMuted.copy(alpha = 0.3f)), RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 300) draft = it },
                    modifier = Modifier.weight(1f).focusRequester(inputFocus),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Ink.OnGlass, fontSize = 14.sp),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(
                                replyTo?.let { tr("回复 $it", "Reply to $it") } ?: tr("说点什么...", "Say something..."),
                                color = Ink.OnGlassMuted,
                                fontSize = 14.sp
                            )
                        }
                        inner()
                    }
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.Send,
                    tr("发送", "Send"),
                    tint = if (draft.isBlank()) Ink.OnGlassMuted else Brand.Terracotta,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            if (draft.isNotBlank()) {
                                state.addMicroComment(post.id, draft, replyTo)
                                draft = ""; replyTo = null
                            }
                        }
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MicroShareSheet(post: MicroPost, state: AppState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val link = "mytrace://micro/${post.id}?author=${post.author.name}"
    val options: List<Pair<String, ImageVector>> = buildList {
        add(tr("复制链接", "Copy link") to Icons.Outlined.ContentCopy)
        add(tr("系统分享", "System share") to Icons.Outlined.IosShare)
        add((if (post.favored) tr("取消收藏", "Unsave") else tr("收藏", "Save")) to
            if (post.favored) Icons.Filled.Star else Icons.Outlined.StarBorder)
        add(tr("拍同款", "Same shot") to Icons.Outlined.PlayCircleOutline)
        add(tr("举报", "Report") to Icons.Outlined.ChatBubbleOutline)
        add(tr("不感兴趣", "Not interested") to Icons.Outlined.Close)
        if (post.isMine) add(tr("撤回发布", "Remove post") to Icons.Outlined.DeleteOutline)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Layer.Opaque,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Ink.OnGlassSub) }
    ) {
        Text(
            tr("分享这条足迹", "Share this footprint"),
            color = Ink.OnGlass,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            options.chunked(4).forEach { rowOptions ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    rowOptions.forEach { (label, icon) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(72.dp)
                        .clickable {
                            when (label) {
                                tr("复制链接", "Copy link") -> {
                                    clipboard.setText(AnnotatedString(link))
                                    state.markMicroShared(post.id)
                                    state.toast(tr("链接已复制", "Link copied"))
                                }
                                tr("系统分享", "System share") -> {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "${post.caption}\n$link")
                                    }
                                    ContextCompat.startActivity(
                                        context,
                                        Intent.createChooser(intent, tr("分享足迹", "Share footprint")),
                                        null
                                    )
                                    state.markMicroShared(post.id)
                                }
                                tr("收藏", "Save"), tr("取消收藏", "Unsave") -> state.toggleMicroFav(post.id)
                                tr("拍同款", "Same shot") -> {
                                    // 借用新建足迹页的抖音式取景器，并自动弹起相机
                                    state.updateUploadStudioCapture(true)
                                    state.autoOpenCameraOnUpload = true
                                    onDismiss()
                                    state.openUpload()
                                }
                                tr("不感兴趣", "Not interested") -> state.hideMicroPost(post.id)
                                tr("撤回发布", "Remove post") -> {
                                    state.unpublishMicroPost(post.id)
                                    onDismiss()
                                }
                                else -> state.toast(tr("已收到反馈，谢谢", "Thanks for the report"))
                            }
                            if (label != tr("撤回发布", "Remove post")) onDismiss()
                        }
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(FixedChip()),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, label, tint = Ink.OnGlass, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(label, color = Ink.OnGlassSub, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

/** 分享面板里的圆形底色：取不透明 surface，避免照片从下面透上来 */
@Composable
private fun FixedChip() = Layer.Opaque

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MicroAuthorSheet(
    state: AppState,
    authorId: Int,
    onDismiss: () -> Unit,
    onPickPost: (String) -> Unit
) {
    val all = state.microPostsForAuthor(authorId)
    val author = all.firstOrNull()?.author ?: return
    val followed = state.isMicroFollowing(authorId)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Layer.Opaque,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Ink.OnGlassSub) }
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Brand.Terracotta),
                    contentAlignment = Alignment.Center
                ) {
                    val authorAvatar = author.avatarPath?.let {
                        if (it.startsWith("/media/") || it.startsWith("/uploads/")) {
                            MicroFeedApi.absolute(LocalContext.current, it)
                        } else it
                    }
                    if (authorAvatar != null) {
                        AsyncImage(
                            model = authorAvatar, contentDescription = author.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(author.avatarText, color = Ink.OnAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(author.name, color = Ink.OnGlass, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        tr("${author.followers} 粉丝 · ${all.size} 条公开足迹", "${author.followers} followers · ${all.size} posts"),
                        color = Ink.OnGlassSub, fontSize = 11.sp
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (followed) FixedChip() else Brand.Terracotta)
                        .border(1.dp, Brand.Terracotta, RoundedCornerShape(50))
                        .clickable { state.toggleMicroFollow(authorId) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        if (followed) tr("已关注", "Following") else tr("+ 关注", "+ Follow"),
                        color = if (followed) Ink.OnGlass else Ink.OnAccent,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(author.bio, color = Ink.OnGlassSub, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Text(tr("公开足迹", "Public posts"), color = Ink.OnGlass, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(240.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(all, key = { it.id }) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(FixedChip())
                            .clickable { onPickPost(p.id) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF3A3438)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (p.isVideo) Icons.Outlined.PlayCircleOutline else Icons.Outlined.LocationOn,
                                null, tint = Ink.OnCover, modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.place, color = Ink.OnGlass, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(p.caption, color = Ink.OnGlassSub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MicroSearchOverlay(
    state: AppState,
    allPosts: List<MicroPost>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    var text by remember { mutableStateOf(state.microFeedQuery) }
    val hotTags = listOf("日照金山", "重庆", "冰岛", "独库公路", "京都", "徒步")
    val results = remember(text, allPosts) {
        val q = text.trim()
        if (q.isBlank()) allPosts.take(5) else allPosts.filter {
            it.caption.contains(q, true) || it.place.contains(q, true) ||
                it.author.name.contains(q, true) || it.tags.any { t -> t.contains(q, true) }
        }
    }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Layer.Opaque)
            .clickable(enabled = false, onClick = {})
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(FixedChip())
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Ink.OnGlass, fontSize = 14.sp),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (text.isEmpty()) Text(tr("搜足迹、地点、话题", "Search places and topics"), color = Ink.OnGlassMuted, fontSize = 14.sp)
                            inner()
                        }
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(tr("取消", "Cancel"), color = Ink.OnGlass, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onDismiss))
            }
            if (text.isBlank()) {
                Text(tr("热门搜索", "Trending"), color = Ink.OnGlass, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    hotTags.take(3).forEach { tag ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(FixedChip())
                                .clickable { text = tag }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("#$tag", color = Ink.OnGlassSub, fontSize = 12.sp) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(results, key = { it.id }) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(p.id) }
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF3A3438)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (p.isVideo) Icons.Outlined.PlayCircleOutline else Icons.Outlined.LocationOn,
                                null, tint = Ink.OnCover, modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.place, color = Ink.OnGlass, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text("@${p.author.name} · ${p.caption.take(30)}", color = Ink.OnGlassSub, fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Filled.PlayArrow, null, tint = Ink.OnGlassMuted, modifier = Modifier.size(18.dp))
                    }
                }
                if (results.isEmpty()) {
                    item {
                        Text(tr("没有找到相关足迹", "No matching footprints"), color = Ink.OnGlassSub, fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 30.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MicroPublishSheet(state: AppState, onDismiss: () -> Unit) {
    val candidates: List<Footprint> = remember(state.footprints) {
        state.footprints.filter { it.photoPaths.isNotEmpty() || it.allVideoPaths.isNotEmpty() }
    }
    var selected by remember { mutableStateOf<Footprint?>(null) }
    var caption by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Layer.Opaque,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Ink.OnGlassSub) }
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp)) {
            Text(tr("发布到微足迹", "Publish to Micro Footprints"), color = Ink.OnGlass,
                fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(tr("只公开选中的媒体副本，原足迹仍保存在本机", "Only a copy is published; the original stays on this device"),
                color = Ink.OnGlassSub, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))

            if (candidates.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("还没有带照片的足迹", "No footprints with photos yet"), color = Ink.OnGlassSub, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Brand.Terracotta)
                            .clickable { onDismiss(); state.openUpload() }
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) { Text(tr("去新建足迹", "Create a footprint"), color = Ink.OnAccent, fontSize = 13.sp) }
                }
            } else {
                Text(tr("选择足迹", "Select a footprint"), color = Ink.OnGlass, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(candidates, key = { it.id }) { fp ->
                        val isSelected = selected?.id == fp.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Brand.Terracotta.copy(alpha = 0.22f) else FixedChip())
                                .border(1.dp, if (isSelected) Brand.Terracotta else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable {
                                    selected = fp
                                    place = fp.name
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${fp.city} · ${fp.name}", color = Ink.OnGlass, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    tr("${fp.photoPaths.size} 张照片", "${fp.photoPaths.size} photos") +
                                        if (fp.allVideoPaths.isNotEmpty()) tr(" · 含视频", " · with video") else "",
                                    color = Ink.OnGlassSub, fontSize = 11.sp
                                )
                            }
                            if (isSelected) Icon(Icons.Filled.Star, null, tint = Brand.Terracotta, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                MicroField(caption, { caption = it }, tr("说说这次旅行...", "Say something about this trip..."))
                Spacer(Modifier.height(8.dp))
                MicroField(tags, { tags = it }, tr("话题用逗号分隔，如 雪山,徒步", "Topics separated by commas"))
                Spacer(Modifier.height(8.dp))
                MicroField(place, { place = it }, tr("地点标签（可留空）", "Place tag (optional)"))
                Spacer(Modifier.height(12.dp))
                Row {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(FixedChip())
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(tr("取消", "Cancel"), color = Ink.OnGlassSub, fontSize = 14.sp) }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .weight(1.4f)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected == null) FixedChip() else Brand.Terracotta)
                            .clickable {
                                val fp = selected ?: return@clickable
                                state.publishFootprintToFeed(
                                    footprint = fp,
                                    caption = caption,
                                    tags = tags.split(",", "，").map { it.trim() }.filter { it.isNotBlank() },
                                    place = place
                                )
                                onDismiss()
                                scope.launch { delay(120); state.microFeedTab = 3 }
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tr("发布", "Publish"), color = if (selected == null) Ink.OnGlassMuted else Ink.OnAccent,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MicroField(value: String, onValueChange: (String) -> Unit, hint: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(FixedChip())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Ink.OnGlass, fontSize = 13.sp),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(hint, color = Ink.OnGlassMuted, fontSize = 13.sp)
                inner()
            }
        )
    }
}
