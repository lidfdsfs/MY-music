package com.example.my.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.model.CostItem
import com.example.my.model.DayPlan
import com.example.my.model.Guide
import com.example.my.model.Spot
import com.example.my.state.AppState
import com.example.my.ui.components.AiInsightCard
import com.example.my.ui.components.CircleIconButton
import com.example.my.ui.components.CloseButton
import com.example.my.ui.components.GradientCover
import com.example.my.ui.components.LiquidGlassStyles
import com.example.my.ui.components.LiquidGlassSurface
import com.example.my.ui.components.TagChip
import com.example.my.ui.i18n.Lang
import com.example.my.ui.i18n.tr
import com.example.my.ui.interaction.BurstEffect
import com.example.my.ui.interaction.pressBounceScale
import com.example.my.ui.interaction.rememberBurst
import com.example.my.ui.interaction.rememberOverscrollZoom
import com.example.my.ui.interaction.rememberTapRipple
import com.example.my.ui.interaction.tapRipple
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.GlassType
import com.example.my.ui.theme.Ink
import com.example.my.ui.theme.Layer
import com.example.my.ui.theme.MainGradient
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.TypeScale
import com.example.my.ui.theme.cardShadow
import com.example.my.ui.i18n.tr

/**
 * 攻略详情页（覆盖层）——封面 Hero + 胶囊分段 + 三段**互相独立**的内容区 + 底部操作栏。
 *
 * 三个 Tab 各自一个 LazyColumn：点「行程」只看行程、点「评论」只看评论，
 * 不再是把简介/行程/评论串在一条长列表里靠锚点滚动（那样评论被挤到最底部，
 * 而且内容不足时锚点滚不到目标位置）。
 */
@Composable
fun GuideDetailScreen(state: AppState) {
    val guide = state.currentGuide ?: return
    // 当前 Tab：0 简介 / 1 行程 / 2 评论。换一篇攻略回到「简介」
    var section by remember(guide.id) { mutableStateOf(0) }
    var commentDraft by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<CommentTarget?>(null) }
    var expandedComments by remember(guide.id) { mutableStateOf(setOf<Int>()) }
    // 每 +1 表示「请把焦点给输入框」，切换 Tab 本身不发信号（避免一进评论就弹键盘）
    var focusSignal by remember { mutableStateOf(0) }
    var showInsightModelPicker by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    // 键盘弹起时收起封面，否则 246dp 的 Hero 会把输入区之外的空间全吃掉
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // 三个列表各自记住滚动位置，来回切换不丢阅读进度
    val introListState = rememberLazyListState()
    val dayListState = rememberLazyListState()
    val commentListState = rememberLazyListState()

    // 封面 Hero 自身处理点击/下拉看图；内容区不额外触发全屏
    fun submitComment() {
        val text = commentDraft.trim()
        if (text.isEmpty()) return
        val target = replyTarget
        if (target == null) state.addComment(guide.id, text)
        else state.addReply(guide.id, target.commentId, text, target.userName)
        commentDraft = ""
        replyTarget = null
        keyboard?.hide()
        state.toast(if (target == null) tr("评论成功") else tr("回复成功"))
    }

    // 攻略详情是从右侧滑入的全屏叠层页，同样必须铺实心底：
    // Bg.Background 在自定义背景态是 Transparent，下方主页的卡片与照片会透上来。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Layer.Opaque)
    ) {
        // 封面 + 浮起的作者卡：固定在顶部，不随内容滚动
        if (!keyboardOpen) {
            GuideHero(
                state = state,
                guide = guide,
                onClose = { state.closeGuide() }
            )
        }

        // 胶囊分段：真正的页面切换，不再是锚点跳转
        GuideSegmentedTabs(
            selected = section,
            onSelect = {
                section = it
                // 复位焦点信号：从「评论」切走再切回来时不该自动弹键盘
                focusSignal = 0
            },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
        )

        // 就地 AI 入口：问题由现场语义生成（这篇攻略 / 这个城市），不另起聊天框
        AiEntryBar(
            state = state,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (section) {
                // —— 简介：概述 / 花费 / 避坑 / 数据 ——
                0 -> LazyColumn(
                    state = introListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item { GuideIntroCard(guide.intro) }
                    item { GuideCostCard(guide.costBreakdown) }
                    item { GuideTipsCard(guide.tips) }
                    item { GuideStatsCard(guide) }
                    item {
                        // 每篇攻略都能让 AI 现场点评：打分 + 亮点 + 避坑 + 建议
                        AiInsightCard(
                            kind = "攻略",
                            title = guide.title,
                            facts = buildString {
                                append("城市：").append(guide.city).append("；")
                                append("天数：").append(guide.days).append(" 天；")
                                append("类型：").append(guide.type).append("；")
                                append("简介：").append(guide.intro).append("；")
                                append("花费：")
                                    .append(guide.costBreakdown.joinToString("，") { "${it.label} ${it.amount}" })
                                    .append("；")
                                append("提示：").append(guide.tips.joinToString("；")).append("；")
                                append("行程：").append(
                                    guide.itinerary.joinToString("；") { day ->
                                        "第${day.day}天 ${day.title}：" +
                                                day.spots.joinToString("，") { spot -> spot.name }
                                    }
                                )
                            },
                            endpoint = state.aiFeatureEndpoint(com.example.my.data.AiFeature.INSIGHT),
                            context = LocalContext.current,
                            useLocal = state.aiFeatureUsesLocal(com.example.my.data.AiFeature.INSIGHT),
                            localModelId = state.aiFeatureLocalModelId(com.example.my.data.AiFeature.INSIGHT),
                            onModelLongPress = { showInsightModelPicker = true },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // —— 行程：逐日卡片（保留展开/收起）——
                1 -> LazyColumn(
                    state = dayListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
                ) {
                    if (guide.itinerary.isEmpty()) {
                        item { GuideEmptyHint(tr("暂无行程安排")) }
                    } else {
                        // key 用「天数+位置」：光用 day 的话，数据里出现两个「第 3 天」
                        // 就会撞 key 抛 "Key was already used"，整个详情页闪退
                        itemsIndexed(guide.itinerary, key = { i, day -> "${day.day}-$i" }) { _, day ->
                            GuideDayCard(
                                day = day,
                                initiallyExpanded = day.day == guide.itinerary.first().day
                            )
                        }
                    }
                }

                // —— 评论：楼中楼回复 / 点赞 / 长按删除 ——
                else -> LazyColumn(
                    state = commentListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        GuideCommentHeader(
                            count = guide.commentList.sumOf { it.replies.size + 1 }
                        )
                    }
                    commentItems(
                        state = state,
                        guide = guide,
                        expandedIds = expandedComments,
                        onToggleReplies = { id ->
                            expandedComments =
                                if (id in expandedComments) expandedComments - id
                                else expandedComments + id
                        },
                        onReply = { target ->
                            replyTarget = target
                            focusSignal++
                        }
                    )
                }
            }
        }

        // 评论 Tab：输入条常驻底部，随时能发（不再依赖「点过说点什么」才出现）
        if (section == 2) {
            GuideCommentInputBar(
                draft = commentDraft,
                onDraftChange = { commentDraft = it },
                replyTo = replyTarget?.userName,
                focusSignal = focusSignal,
                onSubmit = { submitComment() },
                onCancelReply = { replyTarget = null }
            )
        } else {
            GuideBottomBar(
                guide = guide,
                onLike = { state.toggleGuideLike(guide.id) },
                onFav = { state.toggleGuideFav(guide.id) },
                onCommentClick = {
                    section = 2
                    focusSignal++
                },
                onSaySomething = {
                    section = 2
                    focusSignal++
                },
                onPlanClick = { state.openPlanSheet() }
            )
        }
    }

    if (showInsightModelPicker) {
        AiModelQuickSwitchDialog(
            feature = com.example.my.data.AiFeature.INSIGHT,
            state = state,
            onDismiss = { showInsightModelPicker = false }
        )
    }
}

// ============ 1. 封面 Hero + 作者卡 ============

@Composable
private fun GuideHero(
    state: AppState,
    guide: Guide,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    // 图集：空则回退封面单图
    val images = guide.images.ifEmpty { listOfNotNull(guide.coverResId) }
    val pagerState = rememberPagerState(pageCount = { images.size })
    var showFullscreen by remember { mutableStateOf(false) }
    // 多张图自动轮播（3.5s 一张），单图不播；用户滑动或页面退到后台时暂停，
    // 避免动画冲突卡中间，也不在锁屏后继续每 3.5s 唤醒 CPU。
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(images.size, resumed) {
        if (images.size > 1 && resumed) {
            while (true) {
                kotlinx.coroutines.delay(3500)
                if (!pagerState.isScrollInProgress) {
                    val next = (pagerState.currentPage + 1) % images.size
                    pagerState.animateScrollToPage(next)
                }
            }
        }
    }
    // 下拉手势：在封面区域向下拉超过阈值即进入全屏看图
    var pullProgress by remember { mutableStateOf(0f) }
    // 276 = 封面 240 + 作者卡浮出的 36。
    // 封面不能再压矮：标题允许两行，加上标签行和城市行共约 98dp，
    // 240-52(底部留白) - 98 = 90dp 的起点刚好压在顶部按钮行(约 76dp)下面。
    Box(modifier = Modifier.fillMaxWidth().height(276.dp)) {
        // 封面与文字层（clip 保证放大时不溢出圆角）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .pointerInput(images.size) {
                    detectVerticalDragGestures(
                        onDragStart = { pullProgress = 0f },
                        onDragEnd = {
                            if (pullProgress > 180f) showFullscreen = true
                            pullProgress = 0f
                        },
                        onDragCancel = { pullProgress = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0) {
                                pullProgress += dragAmount
                                change.consume()
                            }
                        }
                    )
                }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showFullscreen = true }
            ) { page ->
                GradientCover(
                    seed = guide.coverSeed + page * 7,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                    coverResId = images.getOrNull(page) ?: guide.coverResId
                )
            }
            // 自上而下渐深的黑色遮罩，保证文字可读
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.20f),
                            0.45f to Color.Black.copy(alpha = 0.35f),
                            1f to Color.Black.copy(alpha = 0.85f)
                        )
                    )
            )
            // 多图轮播圆点指示器
            if (images.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(images.size) { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == pagerState.currentPage) 7.dp else 5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == pagerState.currentPage) Color.White
                                    else Color.White.copy(alpha = 0.4f)
                                )
                        )
                    }
                }
            }
            // 顶部按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CloseButton(onClick = onClose, liquid = true, onDark = true)
                Spacer(Modifier.weight(1f))
                CircleIconButton(
                    icon = Icons.Outlined.Share,
                    contentDescription = tr("分享"),
                    onClick = {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, guide.title)
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                if (Lang.isEn) "「${guide.title}」· ${tr(guide.city)}, ${guide.days}-day guide, ~${guide.cost} per person — from MY足迹"
                                else "「${guide.title}」· ${guide.city} ${guide.days}天旅行攻略，人均 ${guide.cost} —— 来自 MY足迹"
                            )
                        }
                        ctx.startActivity(android.content.Intent.createChooser(shareIntent, "分享攻略"))
                    },
                    tint = Ink.OnCover,
                    border = false,
                    liquid = true,
                    onDark = true
                )
            }
            // 底部文字块（底部留出空间给浮起作者卡）
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 16.dp, bottom = 52.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    if (guide.featured) {
                        TagChip(
                            text = tr("⭐ 官方攻略"),
                            backgroundBrush = Brush.linearGradient(
                                listOf(Brand.Gold, Brand.TerracottaLight)
                            ),
                            textColor = Ink.OnCover,
                            radius = 50.dp,
                            fontSize = 11
                        )
                    }
                    TagChip(
                        text = tr("${guide.days}天", "${guide.days} days"),
                        liquid = true,
                        onDark = true,
                        radius = 50.dp,
                        fontSize = 11
                    )
                    if (guide.theme.isNotBlank()) {
                        TagChip(
                            text = guide.theme,
                            background = Color.Black.copy(alpha = 0.35f),
                            liquid = true,
                            onDark = true,
                            radius = 50.dp,
                            fontSize = 11
                        )
                    }
                    guide.tags.firstOrNull()?.takeIf { it.isNotBlank() }?.let { tag ->
                        TagChip(
                            text = tag,
                            background = Color.Black.copy(alpha = 0.35f),
                            liquid = true,
                            onDark = true,
                            radius = 50.dp,
                            fontSize = 11
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = guide.title,
                    style = GlassType.coverTitle.copy(lineBreak = LineBreak.Paragraph),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = buildString {
                        append(tr(guide.city))
                        if (guide.viewedCount > 0) {
                            append(" · ${guideCount(guide.viewedCount)}人看过")
                        }
                    },
                    color = Ink.OnCoverSub,
                    fontSize = TypeScale.body,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 浮在 Hero 下沿的作者卡
        GuideAuthorCard(
            state = state,
            guide = guide,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp)
        )

        // 全屏看图
        if (showFullscreen) {
            FullscreenImageViewer(
                images = images,
                initialPage = pagerState.currentPage,
                coverSeed = guide.coverSeed,
                imageSources = guide.imageSources,
                onDismiss = {
                    showFullscreen = false
                }
            )
        }
    }
}

/** 全屏看图：黑底，左右滑动，点击关闭，顶部页码，底部来源标注 */
@Composable
private fun FullscreenImageViewer(
    images: List<Int>,
    initialPage: Int,
    coverSeed: Int,
    imageSources: List<String> = emptyList(),
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        val state = rememberPagerState(initialPage = initialPage, pageCount = { images.size })
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(state = state, modifier = Modifier.fillMaxSize()) { page ->
                GradientCover(
                    seed = coverSeed + page * 7,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onDismiss),
                    shape = RoundedCornerShape(0.dp),
                    coverResId = images.getOrNull(page),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
            // 顶部页码
            if (images.size > 1) {
                Text(
                    text = "${state.currentPage + 1} / ${images.size}",
                    color = Ink.OnCover,
                    fontSize = TypeScale.body,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            // 关闭按钮
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = tr("关闭"),
                tint = Ink.OnCover,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(28.dp)
                    .clickable(onClick = onDismiss)
            )
            // 底部来源标注
            val src = imageSources.getOrNull(state.currentPage).orEmpty()
            if (src.isNotBlank()) {
                Text(
                    text = "图源：$src",
                    color = Ink.OnCoverSub,
                    fontSize = TypeScale.label,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun GuideAuthorCard(state: AppState, guide: Guide, modifier: Modifier = Modifier) {
    val followed = state.followedUserIds.contains(guide.authorId)
    var busy by remember(guide.authorId) { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val press = pressBounceScale(interaction, 0.92f)
    val ripple = rememberTapRipple()
    val openProfile = {
        if (guide.authorId > 0) state.openUserProfile(guide.authorId, guide.author)
        else state.toast(tr("这是示例攻略作者，暂无主页", "Demo author has no profile"))
    }
    // 液态玻璃：背后只放「封面氛围色」的模糊副本，绝不让封面文字直接穿透
    LiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        optics = LiquidGlassStyles.Card,
        contentAlignment = Alignment.Center,
        backdrop = {
            Box(
                Modifier.matchParentSize().background(
                    Brush.linearGradient(
                        listOf(
                            Brand.Terracotta.copy(alpha = 0.52f),
                            Brand.TerracottaDark.copy(alpha = 0.42f),
                            Brand.Gold.copy(alpha = 0.30f)
                        )
                    )
                )
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        GuideGradientAvatar(guide.author.take(1), 48.dp, onClick = openProfile)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f).clickable { openProfile() }
        ) {
            Text(
                text = guide.author,
                color = Ink.OnGlass,
                fontSize = TypeScale.subtitle,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = guide.authorIntro,
                color = Ink.OnGlassMuted,
                fontSize = TypeScale.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .scale(press)
                .clip(RoundedCornerShape(50.dp))
                .then(
                    if (followed) Modifier.background(Bg.SurfaceVariant)
                    else Modifier.background(Brush.linearGradient(listOf(Brand.Terracotta, Brand.TerracottaLight)))
                )
                .tapRipple(ripple, Color.White.copy(alpha = 0.30f)) {
                    if (busy) return@tapRipple
                    when {
                        guide.authorId <= 0 -> state.toast(
                            tr("这是示例攻略作者，暂不支持关注", "Demo author cannot be followed")
                        )
                        state.offlineMode -> state.toast(
                            tr("离线模式下无法关注，登录并连接服务器后再试", "Sign in to follow")
                        )
                        else -> {
                            busy = true
                            state.toggleFollow(guide.authorId) { f ->
                                busy = false
                                if (f == null) state.toast(tr("关注失败，请检查网络", "Follow failed"))
                                else state.toast(
                                    if (f.following) tr("已关注 ${guide.author}", "Following ${guide.author}")
                                    else tr("已取消关注 ${guide.author}", "Unfollowed ${guide.author}")
                                )
                            }
                        }
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (followed) tr("✓ 已关注") else tr("+ 关注"),
                color = if (followed) Ink.OnGlassSub else Ink.OnAccent,
                fontSize = TypeScale.label,
                fontWeight = FontWeight.SemiBold
            )
        }
        }
    }
}

/** 赤陶渐变的圆形头像 */
@Composable
private fun GuideGradientAvatar(
    text: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Brand.TerracottaLight, Brand.Terracotta)))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.take(1),
            color = Ink.OnAccent,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ============ 2. 胶囊分段控件 ============

@Composable
private fun GuideSegmentedTabs(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    // 英文 "Itinerary" 比「行程」长得多，三格等宽 13sp 仍然放得下，但保险起见限一行
    val labels = listOf(tr("简介"), tr("行程"), tr("评论"))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .cardShadow(RoundedCornerShape(50.dp))
            .clip(RoundedCornerShape(50.dp))
            .background(Bg.Surface)
            .padding(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = selected == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50.dp))
                    .then(
                        if (isSelected) {
                            Modifier.background(
                                brush = Brush.linearGradient(MainGradient),
                                shape = RoundedCornerShape(50.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Ink.OnAccent else Ink.OnGlassSub,
                    fontSize = TypeScale.body,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============ 3. 简介 Tab ============

@Composable
private fun GuideIntroCard(intro: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Bg.Surface)
            .cardShadow(RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Text(
            text = intro,
            color = Text.Primary,
            fontSize = 14.sp,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun GuideCostCard(items: List<CostItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Bg.Surface)
            .cardShadow(RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.ReceiptLong,
                contentDescription = null,
                tint = Text.Primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(tr("花费明细"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        items.forEachIndexed { index, item ->
            if (index > 0) GuideDashedDivider()
            GuideCostRow(item)
        }
    }
}

@Composable
private fun GuideCostRow(item: CostItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = item.label, color = Text.Secondary, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(
            text = item.amount,
            color = if (item.total) Brand.Terracotta else Text.Primary,
            fontSize = 14.sp,
            fontWeight = if (item.total) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

/** 虚线分隔线 */
@Composable
private fun GuideDashedDivider() {
    Canvas(modifier = Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = Bg.Outline,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )
    }
}

@Composable
private fun GuideTipsCard(tips: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Bg.Surface)
            .cardShadow(RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = Text.Primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(tr("避坑建议"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tips.forEach { tip -> GuideTipRow(tip) }
        }
    }
}

@Composable
private fun GuideTipRow(tip: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(Brand.Terracotta)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = tip,
            color = Text.Primary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 底部数据统计卡：4 等分 */
@Composable
private fun GuideStatsCard(guide: Guide) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Bg.Surface)
            .cardShadow()
            .padding(vertical = 16.dp)
    ) {
        GuideStatItem(
            icon = Icons.Outlined.FavoriteBorder,
            value = guideCount(guide.likes),
            label = tr("点赞"),
            modifier = Modifier.weight(1f)
        )
        GuideStatItem(
            icon = Icons.Outlined.ChatBubbleOutline,
            value = guideCount(guide.commentList.sumOf { it.replies.size + 1 }),
            label = tr("评论"),
            modifier = Modifier.weight(1f)
        )
        GuideStatItem(
            icon = Icons.Outlined.CalendarMonth,
            value = guideCount(guide.days),
            label = tr("行程天数"),
            modifier = Modifier.weight(1f)
        )
        GuideStatItem(
            icon = Icons.Outlined.RemoveRedEye,
            value = guideCount(guide.viewedCount),
            label = tr("浏览量"),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GuideStatItem(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Text.Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(6.dp))
        Text(text = value, color = Brand.Terracotta, fontSize = 18.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"))
        Spacer(Modifier.height(2.dp))
        Text(text = label, color = Text.Muted, fontSize = 12.sp)
    }
}

// ============ 4. 行程 Tab ============

@Composable
private fun GuideDayCard(day: DayPlan, initiallyExpanded: Boolean) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Bg.Surface)
            .cardShadow()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brand.Terracotta),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "D${day.day}",
                    color = Ink.OnAccent,
                    fontSize = TypeScale.body,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = day.title,
                    color = Text.Primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(text = tr("${day.spots.size} 个景点", "${day.spots.size} spots"), color = Text.Muted, fontSize = 11.sp)
            }
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) tr("收起") else tr("展开"),
                tint = Text.Muted,
                modifier = Modifier.size(22.dp).rotate(if (expanded) 180f else 0f)
            )
        }
        Column(modifier = Modifier.animateContentSize()) {
            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (day.special && day.specialReason != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brand.Gold.copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = Brand.Gold, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = day.specialReason,
                                    color = Brand.TerracottaDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    day.spots.forEach { GuideSpotRow(it) }
                }
            }
        }
    }
}

@Composable
private fun GuideSpotRow(spot: Spot) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            // 自定义背景下 Bg.Background 是透明的：卡片边界会整块消失
            .background(Layer.Opaque)
            .padding(10.dp)
    ) {
        GradientCover(
            spot.seed,
            Modifier.size(width = 64.dp, height = 64.dp),
            RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = spot.name,
                color = Text.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = spot.note,
                color = Text.Secondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = spot.cost,
                    color = Brand.TerracottaDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (spot.tips != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Tips: ${spot.tips}",
                        color = Text.Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideEmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Text.Muted, fontSize = 13.sp)
    }
}

// ============ 5. 评论 Tab ============
// 评论 UI 见 GuideComments.kt（抖音式卡片 + 楼中楼回复 + 点赞 + 长按删除）

/** 评论列表顶部的小标题：让用户一眼知道这是评论区，而不是「内容到头了」 */
@Composable
private fun GuideCommentHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tr("全部评论"),
            color = Text.Primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (count > 0) "$count" else "0",
            color = Text.Muted,
            fontSize = 12.sp
        )
    }
}

// ============ 6. 底部固定操作栏 ============

@Composable
private fun GuideBottomBar(
    guide: Guide,
    onLike: () -> Unit,
    onFav: () -> Unit,
    onCommentClick: () -> Unit,
    onSaySomething: () -> Unit,
    onPlanClick: () -> Unit
) {
    val likeColor by animateColorAsState(
        targetValue = if (guide.liked) Brand.Terracotta else Text.Secondary,
        label = "guideLikeColor"
    )
    val inputRipple = rememberTapRipple()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg.Surface)
            .navigationBarsPadding()
    ) {
        // 顶部 1dp 描边
        Box(Modifier.fillMaxWidth().height(1.dp).background(Bg.Outline))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GuideBottomAction(
                icon = if (guide.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                tint = likeColor,
                label = tr("点赞"),
                onClick = onLike,
                burstColors = listOf(Brand.Terracotta, Brand.Gold, Brand.TerracottaLight),
                burstOnActivate = !guide.liked
            )
            Spacer(Modifier.width(10.dp))
            GuideBottomAction(
                icon = if (guide.favored) Icons.Filled.Star else Icons.Outlined.StarBorder,
                tint = if (guide.favored) Brand.Gold else Text.Secondary,
                label = if (guide.favored) tr("已收藏") else tr("收藏"),
                onClick = onFav,
                burstColors = listOf(Brand.Gold, Brand.TerracottaLight, Brand.Terracotta),
                burstOnActivate = !guide.favored
            )
            Spacer(Modifier.width(10.dp))
            GuideBottomAction(
                icon = Icons.Outlined.CalendarMonth,
                tint = Text.Secondary,
                label = tr("计划"),
                onClick = onPlanClick
            )
            Spacer(Modifier.width(10.dp))
            GuideBottomAction(
                icon = Icons.Outlined.ChatBubbleOutline,
                tint = Text.Secondary,
                label = tr("评论"),
                badge = "${guide.commentList.sumOf { it.replies.size + 1 }}",
                onClick = onCommentClick
            )
            Spacer(Modifier.width(12.dp))
            // 浅灰输入框占位
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(Bg.SurfaceVariant)
                    .tapRipple(inputRipple, Brand.Terracotta.copy(alpha = 0.08f), onClick = onSaySomething)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(text = tr("说点什么…"), color = Text.Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun GuideBottomAction(
    icon: ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit,
    badge: String? = null,
    burstColors: List<Color>? = null,
    burstOnActivate: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val press = pressBounceScale(interaction, 0.88f)
    val ripple = rememberTapRipple()
    val burst = rememberBurst()
    Box(contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .scale(press)
                .clip(RoundedCornerShape(16.dp))
                .tapRipple(ripple, tint.copy(alpha = 0.14f)) {
                    if (burstOnActivate) burst.fire()
                    onClick()
                }
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
                // 数量角标（如评论数）：图标右上角小圆点
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 7.dp, y = (-3).dp)
                            .clip(RoundedCornerShape(50.dp))
                            .background(tint)
                            .padding(horizontal = 4.dp)
                            .height(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(badge, color = Ink.OnAccent, fontSize = TypeScale.micro, style = TextStyle(fontFeatureSettings = "tnum"))
                    }
                }
            }
        }
        if (burstColors != null) {
            BurstEffect(
                state = burst,
                modifier = Modifier.matchParentSize(),
                colors = burstColors,
                particleCount = 8,
                reach = 22.dp
            )
        }
    }
}

/** 数字格式化：≥1万 显示为 x.x万，≥1亿 显示为 x.x亿，千位直接显示完整数字 */
private fun guideCount(n: Int): String = when {
    n >= 100000000 -> tr("%.1f亿", "%.0fM").format(if (Lang.isEn) n / 1000000.0 else n / 100000000.0)
    n >= 10000 -> tr("%.1f万", "%.1fK").format(if (Lang.isEn) n / 1000.0 else n / 10000.0)
    else -> n.toString()
}
