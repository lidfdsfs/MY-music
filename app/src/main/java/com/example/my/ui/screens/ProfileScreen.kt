package com.example.my.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.model.Medal
import com.example.my.data.BackendApi
import com.example.my.data.FollowUser
import com.example.my.state.AppState
import com.example.my.state.AppTab
import com.example.my.state.ExtraScreen
import com.example.my.ui.components.AppTopBar
import com.example.my.ui.components.EmptyState
import com.example.my.ui.components.ErrorState
import com.example.my.ui.components.GradientCover
import com.example.my.ui.components.LoadingState as LoadingStateView
import com.example.my.ui.components.staggeredIn
import com.example.my.ui.interaction.AnimatedNumber
import com.example.my.ui.interaction.EdgeHintIndicator
import com.example.my.ui.interaction.FlipCard
import com.example.my.ui.interaction.pressBounceScale
import com.example.my.ui.interaction.rememberEdgeDamping
import com.example.my.ui.interaction.slideOutCancelClickable
import com.example.my.ui.theme.*
import com.example.my.ui.i18n.tr
import com.example.my.ui.i18n.Lang
import kotlinx.coroutines.launch

/** 「我的」页背景方案（用户可在主页入口自由切换，持久化） */
private data class ProfileBg(val name: String, val c1: Color, val c2: Color)

private val profileBgs = listOf(
    ProfileBg(tr("暖阳橙"), Color(0xFFE07A5F), Color(0xFFF2CC8F)),
    ProfileBg(tr("薄荷青"), Color(0xFF1F8A7A), Color(0xFF74CEB2)),
    ProfileBg(tr("雾霭紫"), Color(0xFF6B5FC9), Color(0xFFAFA6E8)),
    ProfileBg(tr("海盐蓝"), Color(0xFF2F6FA8), Color(0xFF8FC1E8)),
    ProfileBg(tr("落日粉"), Color(0xFFD85C7E), Color(0xFFF3B0BF)),
    ProfileBg(tr("鎏金暖"), Color(0xFFC98A1F), Color(0xFFF0CE8E))
)

/** 个人中心页 */
@Composable
fun ProfileScreen(state: AppState, onOpenMenu: (() -> Unit)? = null) {
    val user = state.user
    val unlockedCount = remember(user) { user.medals.count { it.unlocked } }
    val footprints = state.footprints
    val myGuides = state.myGuides
    // 派生列表缓存：避免每次重组（如 toast、展开收起）都重新过滤全量数据
    val favorites = remember(state.guides) { state.guides.filter { it.favored } }
    val privateRoutes = remember(state.routes) { state.routes.filter { !it.isPublic } }
    // 特别足迹数（真实数据：足迹中带"特别"标记的条目）
    val specialCount = remember(footprints) {
        footprints.count { it.special }
    }

    // 内容型弹层：成就/攻略/收藏/私密足迹 用底部弹层展示；背景选择独立弹层
    var openSheet by remember { mutableStateOf<String?>(null) }
    var showBgPicker by remember { mutableStateOf(false) }
    // 粉丝 / 关注列表弹层（"粉丝" / "关注"）
    var followSheet by remember { mutableStateOf<String?>(null) }

    // 固定英雄区：不做滚动折叠（参考微信——顶部大留白 + 居中大头像，内容自然滚走）
    val listState = rememberLazyListState()

    // ⑦ 边界阻尼：列表滑到头继续拉阻力增大 + 到头提示
    val profileEdge = rememberEdgeDamping(
        isAtTop = { !listState.canScrollBackward },
        isAtBottom = { !listState.canScrollForward }
    )

    Box(modifier = Modifier.fillMaxSize().background(Bg.Background).nestedScroll(profileEdge.connection)) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 固定标题栏（不随滚动）：状态栏 insets 动态适配 + 纯文字「我的」+ 发丝分割线
        AppTopBar(
            title = tr("我的"),
            rightContent = {
                // 功能抽屉入口：右滑之外再给一个明确按钮，省得用户找不到
                if (onOpenMenu != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Bg.Surface)
                            .clickable { onOpenMenu() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Menu,
                            contentDescription = tr("全部功能", "All features"),
                            tint = Text.Primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
        // 英雄区：暖橙渐变 + 居中大头像 + 签名（随内容一起滚动，标题栏固定在上方）
        item {
            ProfileHero(state = state)
        }

        // 离线状态：明确告诉用户哪些功能暂停，并给一个重试入口
        if (state.offlineMode || state.authState == com.example.my.state.AuthState.GUEST) {
            item {
                OfflineModeCard(state)
            }
        }

        // 足迹统计
        // 统计四卡：口径与真实足迹一致（云端演示统计不泄漏进用户界面），全 0 时折叠
        val fpCities = footprints.map { it.city }.distinct().count()
        val fpDays = footprints
            .map { it.dateMillis?.div(86_400_000L)?.toString() ?: it.date }
            .distinct().count()
        if (fpCities > 0 || footprints.size > 0 || fpDays > 0 || specialCount > 0) {
            item {
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(Bg.Surface)
                        .cardShadow()
                        .padding(vertical = 20.dp)
                        .staggeredIn(2)
                ) {
                    ProfileStat(fpCities, tr("走过城市"), Modifier.weight(1f))
                    ProfileStat(footprints.size, tr("足迹数"), Modifier.weight(1f))
                    ProfileStat(fpDays, tr("旅行天数"), Modifier.weight(1f))
                    ProfileStat(specialCount, tr("特别足迹"), Modifier.weight(1f))
                }
            }
        }

        // 社交数据（无真实数据时折叠，避免死数据占屏）
        if (user.followers > 0 || user.following > 0 || user.likesReceived > 0) {
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(Bg.Surface)
                        .cardShadow()
                        .padding(vertical = 18.dp)
                        .staggeredIn(3),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SocialStat(user.followers, tr("粉丝"), Modifier.weight(1f)) { followSheet = "粉丝" }
                    ColumnDivider()
                    SocialStat(user.following, tr("关注"), Modifier.weight(1f)) { followSheet = "关注" }
                    ColumnDivider()
                    SocialStat(user.likesReceived, tr("获赞"), Modifier.weight(1f)) { state.toast(tr("获赞：${user.likesReceived}", "Likes: ${user.likesReceived}")) }
                }
            }
        }

        // 「我的」页功能收纳：足迹创作 / 工具服务 / 账号设置 三组，每组可点标题折叠。
        // 原足迹页不再占底部 Tab，改由这里进入；底部 Tab 换成微足迹公开流。
        item {
            Spacer(Modifier.height(18.dp))
            ProfileEntryList(
                header = tr("足迹与创作", "Footprints & creation"),
                entries = listOfNotNull(
                    ProfileEntry(tr("我的足迹"), "${footprints.size}", true) {
                        state.footprintMode = 0
                        state.openScreen(ExtraScreen.FOOTPRINTS)
                    },
                    ProfileEntry(
                        title = tr("我的微足迹"),
                        value = "${state.microPosts.count { it.isMine }}",
                        subtitle = tr("发布公开足迹，收获点赞与评论", "Publish publicly and get reactions")
                    ) {
                        state.activeTab = AppTab.MAP
                        state.openMicroPublish()
                    },
                    ProfileEntry(tr("我的成就"), "$unlockedCount/${user.medals.size}", true) { openSheet = "成就" },
                    ProfileEntry(tr("我的攻略"), "${myGuides.size}", true) { openSheet = "攻略" },
                    ProfileEntry(tr("我的收藏"), "${favorites.size}", true) { openSheet = "收藏" },
                    ProfileEntry(
                        title = tr("我的计划"),
                        subtitle = tr("查看已保存的行程表"),
                        onClick = { state.openScreen(ExtraScreen.MY_PLANS) }
                    ),
                    ProfileEntry(
                        title = tr("年度足迹报告"),
                        subtitle = tr("这一年的里程、城市与高光时刻"),
                        onClick = { state.openScreen(ExtraScreen.YEAR_REPORT) }
                    ),
                    ProfileEntry(tr("私密足迹"), tr("仅自己可见", "Only me"), true) { openSheet = "私密" }
                )
            )
            Spacer(Modifier.height(12.dp))
            ProfileEntryList(
                header = tr("工具与服务", "Tools & services"),
                entries = listOfNotNull(
                    ProfileEntry(
                        title = tr("离线设置"),
                        subtitle = tr("下载离线内容，旅行没网也能用"),
                        onClick = { state.openScreen(ExtraScreen.OFFLINE) }
                    ),
                    // 全局自定义背景开启后，旧的本地英雄区换色已无意义，入口收起；
                    // 背景统一到「设置 > 通用 > 自定义背景」里改。
                    if (!AppBackground.config.enabled) {
                        ProfileEntry(
                            tr("更换背景"),
                            profileBgs[state.profileBgIndex.coerceIn(0, profileBgs.lastIndex)].name,
                            false
                        ) { showBgPicker = true }
                    } else null,
                    ProfileEntry(
                        title = tr("通知中心"),
                        value = if (state.unreadCount > 0)
                            tr("${state.unreadCount}条未读", "${state.unreadCount} unread") else null,
                        subtitle = tr("足迹动态与系统通知"),
                        onClick = { state.openScreen(ExtraScreen.NOTIFICATIONS) }
                    ),
                    ProfileEntry(
                        title = tr("音乐"),
                        subtitle = tr("播放本机歌曲，旅途 BGM"),
                        onClick = { state.openScreen(ExtraScreen.MUSIC) }
                    ),
                    ProfileEntry(
                        title = tr("课表"),
                        subtitle = tr("本周课程一览，桌面小组件"),
                        onClick = { state.openScreen(ExtraScreen.COURSE) }
                    )
                )
            )
            Spacer(Modifier.height(12.dp))
            ProfileEntryList(
                header = tr("账号与设置", "Account & settings"),
                entries = listOf(
                    ProfileEntry(tr("设置"), tr("主题、语言、隐私与同步", "Theme, language, privacy & sync")) {
                        state.openScreen(ExtraScreen.SETTINGS)
                    }
                )
            )
        }

        // 账号出口：在线用户退出登录；离线游客回到登录页（本机数据保留）
        item {
            Spacer(Modifier.height(20.dp))
            val guest = state.offlineMode || state.authState == com.example.my.state.AuthState.GUEST
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .staggeredIn(10)
                    .cardShadow(RoundedCornerShape(50.dp))
                    .clip(RoundedCornerShape(50.dp))
                    .background(Bg.Surface)
                    .clickable {
                        if (guest) state.openLoginFromOffline()
                        else state.logout(tr("已退出登录"))
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (guest) tr("去登录 / 开启云同步", "Sign in") else tr("退出账号", "Sign out"),
                    color = if (guest) Brand.Terracotta else Func.Danger,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        item {
            Spacer(Modifier.height(22.dp))
            Text(
                text = tr("MY足迹 v1.0"),
                color = Text.Muted,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
    }

        // ⑦ 到头提示：到底/到顶后短暂浮现自动淡出
        EdgeHintIndicator(
            profileEdge,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        )

        // 弹层：内容型入口（成就/攻略/收藏/私密足迹）
        openSheet?.let { key ->
            when (key) {
                "成就" -> ProfileSheet(tr("我的成就（$unlockedCount/${user.medals.size}）", "Achievements ($unlockedCount/${user.medals.size})"), { openSheet = null }) {
                    if (user.medals.isEmpty()) {
                        EmptyHint(tr("完成旅程解锁徽章"))
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Bg.Surface)
                                .cardShadow(RoundedCornerShape(20.dp))
                        ) {
                            user.medals.forEachIndexed { index, medal ->
                                MedalRow(medal)
                                if (index < user.medals.size - 1) {
                                    Box(Modifier.fillMaxWidth().padding(start = 74.dp, end = 16.dp).height(1.dp).background(Bg.Outline))
                                }
                            }
                        }
                    }
                }
                "攻略" -> ProfileSheet(tr("我的攻略（${myGuides.size}）", "My guides (${myGuides.size})"), { openSheet = null }) {
                    if (myGuides.isEmpty()) {
                        EmptyHint(tr("还没有发布过攻略，试试 AI 帮你生成一份"))
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Bg.Surface)
                                .cardShadow(RoundedCornerShape(20.dp))
                        ) {
                            myGuides.forEachIndexed { index, guide ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { state.openGuide(guide.id) }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 74.dp, height = 54.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                    ) {
                                        GradientCover(
                                            guide.coverSeed,
                                            Modifier.fillMaxSize(),
                                            RoundedCornerShape(14.dp),
                                            coverResId = guide.coverResId
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            guide.title,
                                            color = Text.Primary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = TextStyle(lineBreak = LineBreak.Paragraph)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            tr("${guide.days}天 · ${formatCount(guide.viewedCount)}浏览 · ${guide.city}", "${guide.days} days · ${formatCount(guide.viewedCount)} views · ${guide.city}"),
                                            color = Text.Muted,
                                            fontSize = 11.sp,
                                            style = TextStyle(fontFeatureSettings = "tnum")
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        PillAction(tr("编辑"), Brand.Terracotta) { state.toast(tr("编辑攻略")) }
                                        PillAction(tr("删除"), Text.Muted) { state.toast(tr("已删除该攻略")) }
                                    }
                                }
                                if (index < myGuides.size - 1) {
                                    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).height(1.dp).background(Bg.Outline))
                                }
                            }
                        }
                    }
                }
                "收藏" -> ProfileSheet(tr("我的收藏（${favorites.size}）", "My favorites (${favorites.size})"), { openSheet = null }) {
                    if (favorites.isEmpty()) {
                        EmptyHint(tr("收藏喜欢的攻略后会出现在这里"))
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(favorites, key = { it.id }) { guide ->
                                Column(
                                    modifier = Modifier
                                        .width(150.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Bg.Surface)
                                        .cardShadow()
                                        .clickable { state.openGuide(guide.id) }
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().height(88.dp)) {
                                        GradientCover(
                                            guide.coverSeed,
                                            Modifier.fillMaxSize(),
                                            RoundedCornerShape(0.dp),
                                            coverResId = guide.coverResId
                                        )
                                    }
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            guide.title,
                                            color = Text.Primary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = TextStyle(lineBreak = LineBreak.Paragraph)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(tr("${guide.city} · ${guide.days}天", "${guide.city} · ${guide.days} days"), color = Text.Muted, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                "私密" -> ProfileSheet(tr("私密足迹"), { openSheet = null }) {
                    if (privateRoutes.isEmpty()) {
                        EmptyHint(
                            tr(
                                "没有私密路线；上传足迹时选择\"仅自己可见\"会出现在这里",
                                "No private routes yet — choose “Only me” when uploading"
                            )
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Bg.Surface)
                                .cardShadow(RoundedCornerShape(20.dp))
                        ) {
                            privateRoutes.forEachIndexed { index, route ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { state.openRoute(route.id) }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.Lock, null, tint = Text.Muted, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            route.title,
                                            color = Text.Primary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            (route.cities + route.date).joinToString(" · "),
                                            color = Text.Muted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    PillAction(tr("公开"), Brand.Terracotta) {
                                        state.toggleRoutePublic(route.id)
                                        state.toast(tr("《${route.title}》已公开", "${route.title} is now public"))
                                    }
                                }
                                if (index < privateRoutes.size - 1) {
                                    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).height(1.dp).background(Bg.Outline))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 弹层：粉丝 / 关注列表
        followSheet?.let { kind ->
            FollowListSheet(
                state = state,
                kind = kind,
                count = if (kind == "粉丝") user.followers else user.following,
                onDismiss = { followSheet = null }
            )
        }

        // 弹层：更换背景（预设方案，点击即用并持久化）
        if (showBgPicker) {
            ProfileSheet(tr("更换背景"), { showBgPicker = false }) {
                profileBgs.forEachIndexed { index, bg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                state.updateProfileBgIndex(index)
                                showBgPicker = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(bg.c1, bg.c2)))
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            bg.name,
                            color = Text.Primary,
                            fontSize = 15.sp,
                            fontWeight = if (index == state.profileBgIndex) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(Modifier.weight(1f))
                        if (index == state.profileBgIndex) {
                            Text(tr("使用中"), color = Brand.Terracotta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Outlined.CheckCircle, null, tint = Brand.Terracotta, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (index < profileBgs.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Bg.Outline))
                    }
                }
            }
        }

    }
}

@Composable
private fun OfflineModeCard(state: AppState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Bg.Surface)
            .cardShadow()
            .padding(16.dp)
            .staggeredIn(1)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0A33E))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                tr("离线模式", "Offline mode"),
                color = Text.Primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                tr("服务器未连接", "Server offline"),
                color = Text.Muted,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            tr(
                "足迹、地图、行程、拍摄和隐私空间照常可用；社交、云备份、在线 AI 暂停。重新连上后会补传本机数据。",
                "Local features work normally. Social, cloud backup and online AI are paused."
            ),
            color = Text.Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Brand.Terracotta)
                    .clickable { state.retryCloudConnection() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tr("重试连接", "Reconnect"),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                tr("登录账号 / 开启云同步", "Sign in / enable cloud sync"),
                color = Brand.Terracotta,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { state.openLoginFromOffline() }
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ProfileHero(state: AppState) {
    val user = state.user
    // 用户自定义背景：跟随全局持久化的方案索引
    val bg = profileBgs[state.profileBgIndex.coerceIn(0, profileBgs.lastIndex)]
    val custom = AppBackground.config.enabled
    val heroInk = if (custom) Text.Primary else Color.White
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (custom) {
                    // 全局自定义背景开启时，英雄区必须让出底色，不能再盖一层本地方案
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                } else {
                    Brush.verticalGradient(
                        listOf(
                            bg.c1.copy(alpha = 0.95f),
                            bg.c2.copy(alpha = 0.6f),
                            Bg.Background
                        )
                    )
                }
            )
    ) {
        // 居中：头像 + 昵称 + 签名（标题栏已固定在页面顶部，这里只留头像区）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Brand.TerracottaLight, Brand.Terracotta))),
                contentAlignment = Alignment.Center
            ) {
                val avatarBmp = user.avatarUrl?.let { com.example.my.ui.components.rememberUrlBitmap(it) }
                if (avatarBmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = avatarBmp,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(
                        user.avatarText,
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                user.nickname,
                color = heroInk,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    // 签名胶囊压在封面照片上：旧的非自定义分支只有 85% 白，
                    // 自定义分支的 SurfaceGlass 更低，照片细节会透进文字里。
                    .background(Layer.Opaque)
                    .border(
                        width = if (custom) 1.dp else 0.dp,
                        color = if (custom) Color.White.copy(alpha = 0.24f) else Color.Transparent,
                        shape = RoundedCornerShape(50.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    user.signature,
                    color = if (custom) Text.Primary else Text.Secondary,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                user.joinedAt?.let { tr("加入于 $it", "Joined $it") }
                    ?: tr("本地模式 · 数据只存这台手机", "Local mode · data stays on this phone"),
                color = heroInk.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ProfileSectionHeader(
    title: String,
    count: String? = null,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .background(Brush.verticalGradient(MainGradient))
        )
        Spacer(Modifier.width(8.dp))
        Text(title, color = Text.Primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(count, color = Text.Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        if (expanded != null) {
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) tr("收起") else tr("展开"),
                tint = Text.Muted,
                modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f)
            )
        }
    }
}

@Composable
private fun ProfileStat(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedNumber(value, color = Brand.Terracotta, fontSize = 21)
        Spacer(Modifier.height(5.dp))
        Text(label, color = Text.Secondary, fontSize = 12.sp)
    }
}

@Composable
private fun SocialStat(value: Int, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedNumber(value, color = Text.Primary, fontSize = 20)
        Spacer(Modifier.height(3.dp))
        Text(label, color = Text.Secondary, fontSize = 12.sp)
    }
}

@Composable
private fun ColumnDivider() {
    Box(Modifier.width(1.dp).height(26.dp).background(Bg.Outline))
}

/** 区块空态提示 */
@Composable
private fun EmptyHint(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.Eco, contentDescription = null, tint = Text.Muted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(text, color = Text.Muted, fontSize = 12.sp)
    }
}

/** 成就行：图标 + 名称 + 进度 + 描述 */
@Composable
private fun MedalRow(medal: Medal) {
    var flipped by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val press = pressBounceScale(interaction, 0.90f)
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        FlipCard(
            flipped = flipped,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(44.dp)
                .scale(press)
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null) { flipped = !flipped },
            front = {
                // 正面：勋章图标
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (medal.unlocked) Brand.Gold.copy(alpha = 0.18f) else Bg.SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        medal.icon,
                        null,
                        tint = if (medal.unlocked) Brand.Gold else Text.Muted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            back = {
                // 背面：解锁状态
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (medal.unlocked) Brand.Gold else Bg.SurfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (medal.unlocked) Icons.Outlined.EmojiEvents else Icons.Outlined.Lock,
                        null,
                        tint = if (medal.unlocked) Color.White else Text.Muted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(medal.name, color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${medal.current}/${medal.target}", color = Text.Muted, fontSize = 12.sp, style = TextStyle(fontFeatureSettings = "tnum"))
            }
            Spacer(Modifier.height(8.dp))
            val progress = if (medal.target > 0) (medal.current.toFloat() / medal.target).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Bg.SurfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.horizontalGradient(
                                if (medal.unlocked) listOf(Brand.Gold, Brand.TerracottaLight)
                                else listOf(Brand.TerracottaLight, Brand.Terracotta)
                            )
                        )
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(medal.desc, color = Text.Muted, fontSize = 12.sp)
        }
    }
}

/** 足迹九宫格贴片 */
@Composable
private fun FootprintTile(seed: Int, name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        GradientCover(seed, Modifier.fillMaxSize(), RoundedCornerShape(16.dp))
        Icon(
            Icons.Outlined.DirectionsWalk,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
        )
        Text(
            name,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, end = 8.dp, bottom = 7.dp)
        )
    }
}

@Composable
private fun PillAction(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** 数字格式化：≥1万 显示为 x.x万，≥1亿 显示为 x.x亿（中文语境），千位直接显示完整数字 */
private fun formatCount(n: Int): String = when {
    n >= 100000000 -> tr("%.1f亿", "%.0fM").format(if (Lang.isEn) n / 1000000.0 else n / 100000000.0)
    n >= 10000 -> tr("%.1f万", "%.1fK").format(if (Lang.isEn) n / 1000.0 else n / 10000.0)
    else -> n.toString()
}

/** 图二式入口条目：左侧红色竖线 + 标题 + 右侧数值/说明 + 箭头 */
private data class ProfileEntry(
    val title: String,
    val value: String? = null,
    val chevron: Boolean = true,
    val subtitle: String? = null,
    val onClick: () -> Unit
)

/** 可折叠的分组入口列表（通栏卡片，标题可点收纳，行内左侧红色竖线） */
@Composable
private fun ProfileEntryList(header: String, entries: List<ProfileEntry>) {
    var expanded by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Bg.Surface)
            .cardShadow(RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .slideOutCancelClickable(onClick = { expanded = !expanded })
                .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                header,
                color = Text.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowRight,
                null,
                tint = Text.Muted,
                modifier = Modifier.size(18.dp)
            )
        }
        Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(Bg.Outline))
        if (!expanded) return@Column
        entries.forEachIndexed { index, entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .slideOutCancelClickable(onClick = entry.onClick)
                    .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brush.verticalGradient(MainGradient))
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.title,
                        color = Text.Primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (entry.subtitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            entry.subtitle,
                            color = Text.Muted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                if (entry.value != null) {
                    Text(
                        entry.value,
                        color = Text.Muted,
                        fontSize = 13.sp,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }
                if (entry.chevron) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.KeyboardArrowRight, null, tint = Text.Muted, modifier = Modifier.size(18.dp))
                }
            }
            if (index < entries.size - 1) {
                Box(Modifier.fillMaxWidth().padding(start = 31.dp).height(0.5.dp).background(Bg.Outline))
            }
        }
    }
}

/** 粉丝 / 关注列表底部弹层；后端未开放列表时明确提示，不伪造数据 */
@Composable
private fun FollowListSheet(
    state: AppState,
    kind: String,
    count: Int,
    onDismiss: () -> Unit
) {
    var users by remember { mutableStateOf<List<FollowUser>?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(kind) {
        loading = true
        val r = if (kind == "粉丝") BackendApi.fetchFollowers() else BackendApi.fetchFollowing()
        users = r
        loading = false
    }

    ProfileSheet(
        title = tr("$kind（$count）", "$kind ($count)"),
        onDismiss = onDismiss
    ) {
        when {
            loading -> LoadingStateView(text = "加载中…")
            users == null -> ErrorState(
                title = "列表暂不可用",
                message = "服务端暂未开放${kind}列表，稍后再来查看"
            )
            users!!.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Group,
                text = "暂无$kind",
                subtitle = if (kind == "粉丝") "还没有人关注你" else "还没有关注任何人"
            )
            else -> {
                val list = users!!
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Bg.Surface)
                        .cardShadow(RoundedCornerShape(20.dp))
                ) {
                    list.forEachIndexed { index, u ->
                        FollowUserRow(
                            state = state,
                            user = u,
                            onFollowChanged = { updated ->
                                users = users?.map { if (it.peer.id == updated.peer.id) updated else it }
                            },
                            onProfile = {
                                onDismiss()
                                state.openUserProfile(u.peer.id, u.peer.nickname)
                            },
                            onMessage = {
                                onDismiss()
                                state.requestOpenDmPeer(u.peer.id)
                            }
                        )
                        if (index < list.size - 1) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 66.dp, end = 16.dp)
                                    .height(1.dp)
                                    .background(Bg.Outline)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowUserRow(
    state: AppState,
    user: FollowUser,
    onFollowChanged: (FollowUser) -> Unit,
    onProfile: () -> Unit,
    onMessage: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Brand.TerracottaLight, Brand.Terracotta)))
                .clickable { onProfile() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                user.peer.nickname.take(1).ifBlank { "旅" },
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    user.peer.nickname,
                    color = Text.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (user.mutual) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "互关",
                        color = Brand.Terracotta,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Brand.Terracotta.copy(alpha = 0.12f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                user.peer.signature.ifBlank {
                    when {
                        user.mutual -> "互相关注"
                        user.following -> "已关注"
                        else -> "旅行者"
                    }
                },
                color = Text.Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(if (user.following) Bg.SurfaceVariant else Brand.Terracotta)
                .clickable(enabled = !user.following) {
                    scope.launch {
                        val f = BackendApi.toggleFollow(user.peer.id)
                        if (f == null) {
                            state.toast("操作失败，请检查网络")
                        } else {
                            onFollowChanged(
                                user.copy(
                                    following = f.following,
                                    followsMe = f.followsMe || user.followsMe,
                                    mutual = f.mutual || (f.following && user.followsMe)
                                )
                            )
                            state.toast(if (f.following) "已关注" else "已取消关注")
                        }
                    }
                }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (user.following) "已关注" else "关注",
                color = if (user.following) Text.Muted else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Bg.SurfaceVariant)
                .clickable { onMessage() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Send,
                contentDescription = tr("发消息", "Message"),
                tint = Text.Primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** 通用底部内容弹层 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Text(
            text = title,
            color = Text.Primary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            content = content
        )
        Spacer(Modifier.height(28.dp))
    }
}
