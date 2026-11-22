package com.example.my.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.my.state.AppState
import com.example.my.state.ExtraScreen
import com.example.my.ui.components.AppBanner
import com.example.my.ui.components.FeatureSearchPill
import com.example.my.ui.components.appFeatureItems
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Func
import com.example.my.ui.theme.Layer
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「我的」页功能抽屉的进度控制器。
 *
 * 0f = 收起；1f = 抽屉完全展开（主框架往左让出面板宽度）。
 * 拖拽阶段只改 float，松手交给弹簧——和负一屏同一套手感，但方向相反。
 */
@Stable
class ProfileMenuCtrl {
    private var dragging by mutableStateOf(false)
    private var raw by mutableFloatStateOf(0f)
    private var hidden by mutableStateOf(false)
    private val anim = Animatable(0f)

    val progress: Float get() = if (hidden) 0f else if (dragging) raw else anim.value
    val visible: Boolean get() = !hidden && (dragging || anim.value > 0.0005f)

    /**
     * 立刻收起，不播动画。
     *
     * 点菜单项要跳页面时只能走这条：收起动画跑在抽屉自己的 rememberCoroutineScope 里，
     * 动画一结束 visible 变 false，抽屉退出组合、scope 被取消，排在后面的 action() 就丢了，
     * 用户看到的就是「点了没反应」。
     */
    fun hideNow() {
        dragging = false
        hidden = true
    }

    fun onDrag(deltaX: Float, widthPx: Float) {
        if (widthPx <= 0f) return
        if (!dragging) {
            dragging = true
            raw = if (hidden) 0f else anim.value
            hidden = false
        }
        raw = (raw + deltaX / (widthPx * 0.85f)).coerceIn(0f, 1f)
    }

    suspend fun settle() {
        val from = progress
        val target = if (from >= 0.38f) 1f else 0f
        anim.snapTo(from)
        dragging = false
        anim.animateTo(target, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow))
    }

    suspend fun open() {
        if (hidden) {
            hidden = false
            anim.snapTo(0f)
        }
        val from = progress
        if (from >= 1f) {
            dragging = false
            return
        }
        anim.snapTo(from)
        dragging = false
        anim.animateTo(1f, spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow))
    }

    suspend fun close() {
        if (hidden) return
        val from = progress
        if (from <= 0.0005f) {
            dragging = false
            return
        }
        anim.snapTo(from)
        dragging = false
        anim.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
    }
}

private data class MenuRow(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit
)

@Composable
fun ProfileMenuScreen(state: AppState, ctrl: ProfileMenuCtrl) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = ctrl.progress

    var moreOpen by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    // 注销账号：7 天冷静期内可撤销
    var deleteOpen by remember { mutableStateOf(false) }
    // 退出账号确认
    var signOutOpen by remember { mutableStateOf(false) }
    var deletePwd by remember { mutableStateOf("") }
    var deleteBusy by remember { mutableStateOf(false) }
    var deleteMsg by remember { mutableStateOf<String?>(null) }
    var deletion by remember { mutableStateOf<com.example.my.data.DeletionStatus?>(null) }
    // 「我的攻略」不再跳到发布页：先列出自己发过的攻略，点哪篇看哪篇，列表底部才有「写一篇」
    var guidesOpen by remember { mutableStateOf(false) }

    // 缓存占用：进抽屉时算一次，清完把 tick 自增再算一次
    var cacheTick by remember { mutableStateOf(0) }
    val cacheState = produceState(initialValue = 0L, cacheTick) {
        value = withContext(Dispatchers.IO) { cacheSize(ctx) }
    }
    val cacheBytes = cacheState.value

    // 离线地图包体积（和设置页同一份实体，不重复算）
    val offlineState = produceState(initialValue = 0L) {
        value = withContext(Dispatchers.IO) {
            runCatching { com.example.my.data.map.OfflinePackStore.totalBytes(ctx) }.getOrDefault(0L)
        }
    }
    val offlineBytes = offlineState.value

    // 跳页前同步收起抽屉（不播动画）：动画在本面板 scope 里，面板一退出组合就取消，
    // 排在动画后面的 action() 会被一起丢掉，用户看到的就是「点了没反应」
    val leave: (() -> Unit) -> Unit = { action ->
        ctrl.hideNow()
        action()
    }

    BackHandler { ctrl.hideNow() }

    val unread = state.notifications.count { !it.read }
    val fpCount = state.footprints.size
    val cities = state.footprints.map { it.city }.filter { it.isNotBlank() }.distinct().size

    Box(Modifier.fillMaxSize()) {
        // 主框架被压暗：抽屉是主体，底下只留个轮廓
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.26f * p))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { scope.launch { ctrl.close() } }
        )

        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.84f)
                .graphicsLayer {
                    translationX = (1f - p) * size.width
                    // 边角随进度收起：全开时是一块贴边面板，拉出来那一下才看得出是一张卡片
                    alpha = (p * 1.25f).coerceIn(0f, 1f)
                }
                .clip(
                    RoundedCornerShape(
                        // 弹簧会过冲一点点，1 - p 可能变负；负数圆角会直接抛 IllegalArgumentException
                        topStart = (26f * (1f - p).coerceIn(0f, 1f)).dp,
                        bottomStart = (26f * (1f - p).coerceIn(0f, 1f)).dp
                    )
                )
                .background(Layer.Opaque)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { scope.launch { ctrl.settle() } },
                        onDragCancel = { scope.launch { ctrl.settle() } }
                    ) { change, dragAmount ->
                        if (ctrl.progress <= 0.0005f) return@detectHorizontalDragGestures
                        change.consume()
                        ctrl.onDrag(dragAmount, size.width.toFloat())
                    }
                }
        ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(10.dp))

            // ---------- 顶部两个胶囊 ----------
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MenuPill(tr("设置", "Settings"), Icons.Outlined.Settings) {
                    leave { state.openScreen(ExtraScreen.SETTINGS) }
                }
                Spacer(Modifier.width(10.dp))
                MenuPill(tr("AI 助手", "Assistant"), Icons.Outlined.Star) {
                    leave { state.openAiFull() }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---------- 功能搜索（收纳成一颗胶囊，点开才展开） ----------
            val searchItems = remember(state) {
                appFeatureItems(state).filter { it.key != "switch_account" }
            }
            FeatureSearchPill(
                items = searchItems,
                onBeforeJump = { ctrl.hideNow() }
            )

            Spacer(Modifier.height(14.dp))

            // ---------- 账号 ----------
            // 放在最上面：退出/换号是高频操作，不能埋在“系统”卡最底部
            val accountName = state.accountId
            val displayName = state.user.nickname.ifBlank {
                accountName.ifBlank { tr("旅行者", "Traveler") }
            }
            MenuCard {
                MenuLine(
                    MenuRow(
                        displayName,
                        if (accountName.isNotBlank()) "@$accountName"
                        else tr("已登录", "Signed in"),
                        Icons.Outlined.Person,
                        Brand.Teal
                    ) {
                        leave { state.activeTab = com.example.my.state.AppTab.PROFILE }
                    }
                )
                MenuDivider()
                MenuLine(
                    MenuRow(
                        tr("切换账号", "Switch account"),
                        tr("回到登录页，选择另一个账号", "Back to sign-in, pick another"),
                        Icons.Outlined.Person,
                        Brand.Gold
                    ) { leave { state.switchAccount() } }
                )
                MenuDivider()
                MenuLine(
                    MenuRow(
                        tr("退出账号", "Sign out"),
                        tr("退出后回到登录页", "Back to sign-in"),
                        Icons.AutoMirrored.Outlined.Logout,
                        Func.Danger
                    ) { signOutOpen = true }
                )
            }

            // ---------- 驾驶模式（侧屏唯一直达入口，必须 leave{} 同步收起） ----------
            MenuCard {
                MenuLine(
                    MenuRow(
                        tr("驾驶模式", "Driving mode"),
                        tr("全屏车机 · 导航 / 音乐 / 语音", "Full-screen cockpit · nav / music / voice"),
                        Icons.Outlined.DirectionsCar,
                        Brand.Teal
                    ) { leave { state.openScreen(ExtraScreen.DRIVING) } }
                )
                MenuDivider()
                // 购物专区：紧挨驾驶模式，出行前后顺手比价
                MenuLine(
                    MenuRow(
                        tr("购物专区", "Shopping"),
                        tr("全网报价 · 价格走势", "Public prices · price trend"),
                        Icons.Outlined.ShoppingBag,
                        Brand.Gold
                    ) { leave { state.openScreen(ExtraScreen.SHOPPING) } }
                )
                // 隐私空间：紧挨购物专区下面，加密相册入口
                MenuDivider()
                MenuLine(
                    MenuRow(
                        tr("隐私空间", "Private space"),
                        tr("加密相册 · 密码保护", "Encrypted gallery · password"),
                        Icons.Outlined.Lock,
                        Brand.Terracotta
                    ) { leave { state.openScreen(ExtraScreen.PRIVACY_VAULT) } }
                )
            }

            // ---------- 我的 ----------
            MenuCard {
                MenuLine(
                    MenuRow(
                        tr("我的行程", "My trips"),
                        tr("${state.tripPlans.count { !it.archived }} 段", "${state.tripPlans.count { !it.archived }}"),
                        Icons.Outlined.CardTravel,
                        Brand.Terracotta
                    ) { leave { state.openScreen(ExtraScreen.MY_PLANS) } }
                )
                MenuDivider()
                MenuLine(
                    MenuRow(
                        tr("我的足迹", "My footprints"),
                        tr("$fpCount 条 · $cities 城", "$fpCount · $cities"),
                        Icons.Outlined.LocationCity,
                        Brand.Teal
                    ) {
                        leave {
                            state.activeTab = com.example.my.state.AppTab.MAP
                            state.footprintMode = 0
                        }
                    }
                )
                MenuDivider()
                MenuLine(
                    MenuRow(
                        tr("年度报告", "Year report"),
                        tr("今年走过 $cities 座城", "$cities cities this year"),
                        Icons.Outlined.EmojiEvents,
                        Brand.Gold
                    ) { leave { state.openScreen(ExtraScreen.YEAR_REPORT) } }
                )
            }

            // ---------- 我的内容 ----------
            MenuCard {
                MenuLine(
                    MenuRow(
                        tr("通知消息", "Notifications"),
                        if (unread > 0) tr("$unread 条未读", "$unread unread")
                        else tr("已读完", "All read"),
                        Icons.Outlined.Notifications,
                        Brand.Terracotta
                    ) { leave { state.openScreen(ExtraScreen.NOTIFICATIONS) } }
                )
                MenuDivider()
                MenuLine(
                    MenuRow(
                        tr("离线地图包", "Offline maps"),
                        tr("已缓存 ${sizeText(offlineBytes)}", "${sizeText(offlineBytes)} cached"),
                        Icons.Outlined.Map,
                        Brand.Teal
                    ) {
                        // 这一行报的是地图包体积，就应该打开地图包管理面板，
                        // 而不是「离线旅行」设置页
                        leave {
                            state.activeTab = com.example.my.state.AppTab.MAP
                            state.requestMapPacks()
                        }
                    }
                )
                MenuDivider()
                MenuLine(
                    MenuRow(
                        tr("我的攻略", "My guides"),
                        tr("${state.myGuides.size} 篇", "${state.myGuides.size}"),
                        Icons.AutoMirrored.Outlined.Assignment,
                        Brand.Gold
                    ) { guidesOpen = true }
                )
            }

            // ---------- 去哪儿逛 ----------
            MenuCard {
                MenuLine(
                    MenuRow(
                        tr("资讯", "News"),
                        tr("热点 · 行情", "Hot · market"),
                        Icons.AutoMirrored.Outlined.Assignment,
                        Brand.Terracotta
                    ) { leave { state.openScreen(ExtraScreen.NEWS) } }
                )
                MenuDivider()
                MenuLine(
                    MenuRow(
                        tr("股市行情", "Markets"),
                        tr("A股 · 港股 · 美股", "A / HK / US"),
                        Icons.Outlined.Public,
                        Brand.Teal
                    ) { leave { state.openScreen(ExtraScreen.STOCK) } }
                )
                MenuDivider()
                MenuLine(
                    MenuRow(
                        tr("我的交易", "My trading"),
                        tr("资产 · 委托 · 持仓", "Assets · orders · holdings"),
                        Icons.Outlined.AccountBalanceWallet,
                        Brand.Terracotta
                    ) { leave { state.openScreen(ExtraScreen.TRADE) } }
                )
                MenuDivider()
                MenuLine(
                    MenuRow(
                        tr("音乐", "Music"),
                        com.example.my.data.music.MusicPlayer.current?.title
                            ?: tr("还没在听", "Nothing playing"),
                        Icons.Outlined.PlayArrow,
                        Brand.Gold
                    ) { leave { state.openScreen(ExtraScreen.MUSIC) } }
                )
                MenuDivider()
                MenuLine(
                    MenuRow(
                        tr("课程表", "Timetable"),
                        tr("${state.courses.size} 节课", "${state.courses.size} lessons"),
                        Icons.Outlined.DateRange,
                        Brand.Terracotta
                    ) { leave { state.openScreen(ExtraScreen.COURSE) } }
                )
            }

            // ---------- 系统 ----------
            MenuCard {
                MenuLine(
                    MenuRow(
                        tr("清理缓存", "Clear cache"),
                        if (clearing) tr("正在清理…", "Clearing…")
                        else tr("占用 ${sizeText(cacheBytes)}", "${sizeText(cacheBytes)} used"),
                        Icons.Outlined.Delete,
                        Func.Danger
                    ) {
                        if (!clearing) {
                            clearing = true
                            scope.launch {
                                val freed = withContext(Dispatchers.IO) {
                                    val before = cacheSize(ctx)
                                    ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                                    before - cacheSize(ctx)
                                }
                                clearing = false
                                cacheTick += 1
                                AppBanner.show(
                                    if (freed > 0) {
                                        tr("已清理 ${sizeText(freed)}", "Freed ${sizeText(freed)}")
                                    } else {
                                        tr("已经很干净了", "Already clean")
                                    }
                                )
                            }
                        }
                    }
                )
                MenuDivider()
                MenuLine(
                    MenuRow(
                        tr("注销账号", "Delete account"),
                        deletion?.takeIf { it.pending }?.let {
                            tr("冷静期剩 ${it.daysLeft} 天，可撤销", "${it.daysLeft} days left")
                        } ?: tr("7 天冷静期 · 到期删除云端数据", "7-day grace period"),
                        Icons.Outlined.Delete,
                        Func.Danger
                    ) {
                        deleteOpen = true
                        deleteMsg = null
                        state.fetchDeletionStatus { s, m -> deletion = s; deleteMsg = m }
                    }
                )
                MenuDivider()
                MenuLine(
                    MenuRow(
                        tr("设置", "Settings"),
                        tr("外观 · 语言 · 隐私", "Theme · language · privacy"),
                        Icons.Outlined.Settings,
                        Brand.Gold
                    ) { leave { state.openScreen(ExtraScreen.SETTINGS) } }
                )
            }

            // ---------- 更多功能（展开出来的都是真入口） ----------
            Spacer(Modifier.height(2.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(Bg.SurfaceVariant.copy(alpha = 1f))
                    .clickable { moreOpen = !moreOpen }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (moreOpen) tr("收起", "Less") else tr("更多功能", "More"),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Text.Secondary
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    null,
                    tint = Text.Muted,
                    modifier = Modifier.size(16.dp)
                        .graphicsLayer { rotationZ = if (moreOpen) -90f else 90f }
                )
            }

            AnimatedVisibility(
                visible = moreOpen,
                enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(140))
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    MenuCard {
                        MenuLine(
                            MenuRow(
                                tr("常访问的旅行人", "Frequent travelers"),
                                tr("同行人 · 攻略作者", "Companions · authors"),
                                Icons.Outlined.Person,
                                Brand.Terracotta
                            ) { leave { state.openScreen(ExtraScreen.TRAVELER_RANK) } }
                        )
                        MenuDivider()
                        MenuLine(
                            MenuRow(
                                tr("创意工坊", "Creative"),
                                tr("图片 · 卡片", "Images · cards"),
                                Icons.Outlined.Create,
                                Brand.Teal
                            ) { leave { state.openScreen(ExtraScreen.CREATIVE) } }
                        )
                        MenuDivider()
                        MenuLine(
                            MenuRow(
                                tr("票务专区", "Tickets"),
                                tr("门票 · 机酒", "Tickets · flights"),
                                Icons.Outlined.Flight,
                                Brand.Gold
                            ) { leave { state.openScreen(ExtraScreen.TICKET_ZONE) } }
                        )
                        MenuDivider()
                        MenuLine(
                            MenuRow(
                                tr("网页搜索", "Web search"),
                                tr("查攻略原文", "Search sources"),
                                Icons.Outlined.Search,
                                Brand.Terracotta
                            ) { leave { state.openScreen(ExtraScreen.WEB_SEARCH) } }
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                tr("向右滑回收起", "Swipe right to close"),
                fontSize = 11.sp,
                color = Text.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.navigationBarsPadding().height(10.dp))
        }
        }

        // 我的攻略：抽屉开着时以浮层列出，点一篇直接进详情（详情是全 App 顶层的覆盖页，
        // 因此不管当前停在哪个 Tab 都能正常打开）
        if (guidesOpen) {
            Dialog(onDismissRequest = { guidesOpen = false }) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(Layer.Opaque)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("我的攻略（${state.myGuides.size}）", "My guides (${state.myGuides.size})"),
                            color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            tr("关闭", "Close"),
                            color = Text.Muted, fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { guidesOpen = false }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (state.myGuides.isEmpty()) {
                        Text(
                            tr("还没有发过攻略。写一篇，下次出发前自己也能翻。", "No guides yet — write one."),
                            color = Text.Muted, fontSize = 12.sp, lineHeight = 17.sp
                        )
                    } else {
                        Column(
                            Modifier
                                .heightIn(max = 380.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            state.myGuides.forEach { g ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(Radius.sm))
                                        .clickable {
                                            guidesOpen = false
                                            state.openGuide(g.id)
                                            scope.launch { ctrl.close() }
                                        }
                                        .padding(vertical = 9.dp)
                                ) {
                                    Text(
                                        g.title,
                                        color = Text.Primary, fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        tr(
                                            "${g.days}天 · ${g.city}",
                                            "${g.days} days · ${g.city}"
                                        ),
                                        color = Text.Muted, fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(Brand.Terracotta)
                            .clickable {
                                guidesOpen = false
                                leave { state.openScreen(ExtraScreen.GUIDE_CREATE) }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tr("写一篇新攻略", "Write a new guide"),
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (signOutOpen) {
            Dialog(onDismissRequest = { signOutOpen = false }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(Layer.Opaque)
                        .padding(18.dp)
                ) {
                    Text(
                        tr("退出账号", "Sign out"),
                        color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr(
                            "退出后会回到登录页。本机保存的账号仍保留，下次可直接点选；云端数据不会删除。",
                            "You will return to sign-in. Saved accounts stay on this device; cloud data is not deleted."
                        ),
                        color = Text.Muted, fontSize = 12.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(Bg.SurfaceVariant)
                                .clickable { signOutOpen = false }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(tr("取消", "Cancel"), color = Text.Secondary, fontSize = 13.sp)
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(Func.Danger)
                                .clickable {
                                    signOutOpen = false
                                    leave { state.logout(tr("已退出登录")) }
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                tr("退出账号", "Sign out"),
                                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        if (deleteOpen) {
            Dialog(onDismissRequest = { if (!deleteBusy) deleteOpen = false }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(Layer.Opaque)
                        .padding(18.dp)
                ) {
                    Text(
                        tr("注销账号", "Delete account"),
                        color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    val pending = deletion?.pending == true
                    if (pending) {
                        Text(
                            tr(
                                "已提交注销申请，${deletion?.scheduledAt ?: ""} 到期删除；冷静期内可撤销。",
                                "Deletion scheduled for ${deletion?.scheduledAt ?: ""}; you can cancel during the grace period."
                            ),
                            color = Text.Muted, fontSize = 12.sp, lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .background(Brand.Terracotta)
                                .clickable(enabled = !deleteBusy) {
                                    deleteBusy = true
                                    state.cancelAccountDeletion { ok, msg ->
                                        deleteBusy = false
                                        if (ok) {
                                            deletion = null
                                            deleteOpen = false
                                            state.toast(tr("已撤销注销申请"))
                                        } else {
                                            deleteMsg = msg
                                        }
                                    }
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(tr("撤销注销", "Cancel deletion"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text(
                            tr(
                                "注销后账号、云端足迹、照片、攻略与评论将全部删除且不可恢复。提交后 7 天内可撤销。",
                                "This permanently deletes your account and all cloud data. You can cancel within 7 days."
                            ),
                            color = Text.Muted, fontSize = 12.sp, lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        TextField(
                            value = deletePwd,
                            onValueChange = { deletePwd = it; deleteMsg = null },
                            placeholder = { Text(tr("当前密码"), color = Text.Muted, fontSize = 13.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Bg.SurfaceVariant,
                                unfocusedContainerColor = Bg.SurfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Brand.Terracotta
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Text.Primary, fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .background(if (deletePwd.isBlank() || deleteBusy) Brand.Terracotta.copy(alpha = 0.45f) else Brand.Terracotta)
                                .clickable(enabled = deletePwd.isNotBlank() && !deleteBusy) {
                                    deleteBusy = true
                                    state.requestAccountDeletion(deletePwd) { s, m ->
                                        deleteBusy = false
                                        if (s != null) {
                                            deletion = s
                                            deletePwd = ""
                                            state.toast(tr("注销申请已提交，7 天内可撤销"))
                                        } else {
                                            deleteMsg = m
                                        }
                                    }
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(tr("申请注销", "Request deletion"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    deleteMsg?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Color(0xFFD05353), fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        tr("取消", "Close"),
                        color = Text.Muted, fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable(enabled = !deleteBusy) { deleteOpen = false }
                    )
                }
            }
        }
    }
}

/** 顶部胶囊按钮：白底 + 图标 + 文字 */
@Composable
private fun MenuPill(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Bg.SurfaceVariant.copy(alpha = 1f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Text.Secondary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Text.Primary)
    }
}

/** 分组卡：白底 + 圆角 + 阴影 */
@Composable
private fun MenuCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(Radius.lg)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .cardShadow(shape)
            .clip(shape)
            .background(Layer.Opaque)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        content = content
    )
}

@Composable
private fun MenuLine(row: MenuRow) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xs))
            .clickable { row.onClick() }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(row.icon, null, tint = row.tint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            row.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Text.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            row.subtitle,
            fontSize = 11.sp,
            color = Text.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            null,
            tint = Text.Muted.copy(alpha = 0.7f),
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 31.dp)
            .height(0.6.dp)
            .background(Bg.Outline)
    )
}

private fun cacheSize(ctx: Context): Long {
    val root = ctx.cacheDir
    if (!root.exists()) return 0L
    var total = 0L
    val stack = ArrayDeque<File>()
    stack.addLast(root)
    while (stack.isNotEmpty()) {
        val dir = stack.removeLast()
        val children = dir.listFiles() ?: continue
        for (child in children) {
            if (child.isDirectory) stack.addLast(child) else total += child.length()
        }
    }
    return total
}

private fun sizeText(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.CHINA, "%.1f GB", bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> String.format(Locale.CHINA, "%.0f MB", bytes / 1024.0 / 1024)
    bytes >= 1024L -> String.format(Locale.CHINA, "%.0f KB", bytes / 1024.0)
    bytes > 0 -> "$bytes B"
    else -> "0 B"
}
