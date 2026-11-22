package com.example.my

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.example.my.ui.screens.DashboardCtrl
import com.example.my.ui.screens.DashboardScreen
import com.example.my.ui.screens.DrivingScreen
import com.example.my.ui.screens.ProfileMenuCtrl
import com.example.my.ui.screens.ProfileMenuScreen
import kotlinx.coroutines.launch
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.PlayerSkinStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.my.state.AppState
import com.example.my.data.CrashLog
import com.example.my.data.WakeWordEngine
import com.example.my.data.music.CoverRepository
import com.example.my.data.music.MusicActions
import com.example.my.data.music.MusicPlayer
import com.example.my.data.music.SongStoryCache
import com.example.my.widget.WidgetKit
import com.example.my.state.AppTab
import com.example.my.state.AuthState
import com.example.my.state.ExtraScreen
import com.example.my.ui.components.ProvideStaggerGate
import com.example.my.ui.components.MyBottomBar
import com.example.my.ui.components.ToastHost
import com.example.my.ui.interaction.EdgeDockingBall
import com.example.my.ui.interaction.SwipeBackPage
import com.example.my.ui.interaction.SystemAwareHapticFeedback
import com.example.my.ui.interaction.rememberScrollActivity
import com.example.my.ui.i18n.tr
import com.example.my.ui.screens.AiInlineSheet
import com.example.my.ui.screens.DiscoverScreen
import com.example.my.ui.screens.GuideCreateScreen
import com.example.my.ui.screens.GuideDetailScreen
import com.example.my.ui.screens.LoginScreen
import com.example.my.ui.screens.MapScreen
import com.example.my.ui.screens.MemoryScreen
import com.example.my.ui.screens.MyPlansScreen
import com.example.my.ui.screens.NewsScreen
import com.example.my.ui.screens.StockScreen
import com.example.my.ui.trade.TradeScreen
import com.example.my.ui.screens.AiAssistantScreen
import com.example.my.ui.screens.AiCreationScreen
import com.example.my.ui.screens.AiTripSheet
import com.example.my.ui.screens.MessagesScreen
import com.example.my.ui.screens.MicroFeedScreen
import com.example.my.music.MusicAppRoot
import com.example.my.ui.music.MusicScreen
import com.example.my.ui.screens.CourseScreen
import com.example.my.ui.screens.NotificationsScreen
import com.example.my.ui.screens.OfflineScreen
import com.example.my.ui.screens.PlanDetailSheet
import com.example.my.ui.screens.PlanSheet
import com.example.my.ui.screens.ProfileScreen
import com.example.my.ui.screens.RouteMemorySheet
import com.example.my.ui.screens.SettingsScreen
import com.example.my.ui.screens.SplashScreen
import com.example.my.ui.screens.UploadScreen
import com.example.my.ui.screens.WeatherScreen
import com.example.my.ui.screens.TicketZoneScreen
import com.example.my.ui.screens.ShoppingScreen
import com.example.my.ui.screens.PrivacyVaultScreen
import com.example.my.ui.screens.TravelerRankScreen
import com.example.my.ui.screens.WebSearchScreen
import com.example.my.ui.screens.YearReportScreen
import com.example.my.ui.screens.UserProfileScreen
import com.example.my.ui.components.DmBannerHost
import com.example.my.ui.theme.AppBackground
import com.example.my.ui.theme.AppBackgroundLayer
import com.example.my.ui.theme.MY足迹Theme
import com.example.my.ui.theme.WarmBg

class MainActivity : ComponentActivity() {
    companion object {
        /** 通知里“打开消息中心”的兜底入口 */
        const val EXTRA_OPEN_MESSAGES = "open_messages"
        private const val REQ_NOTIFICATION = 8_901

        /** 通知 / 桌面小组件点击「打开播放器」时，由 AppRoot 注册的跳转回调 */
        @Volatile
        var openMusicHandler: (() -> Unit)? = null

        /** 私信通知点击后直达会话；由 AppRoot 注册 */
        @Volatile
        var openDmHandler: ((Int) -> Unit)? = null

        /** 只打开消息列表（汇总通知/无 peer 时） */
        @Volatile
        var openMessagesHandler: (() -> Unit)? = null

        /** 桌面小组件点击整张卡片时，由 AppRoot 注册的页面跳转回调 */
        @Volatile
        var widgetNavigationHandler: ((String, Int) -> Unit)? = null

        /**
         * 内置网页页里「系统返回键」的接管：WebView 有历史就先回上一页，返回 true 表示已消费。
         * 用回调而不是在页面里再挂一个 BackHandler——本类末尾注册的返回键优先级最高，
         * 页面内层的 BackHandler 永远抢不过它。
         */
        @Volatile
        var webBackHandler: (() -> Boolean)? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // release 包全局关闭 WebView 调试：不依赖某个页面是否打开过搜索页，
        // 也避免调试端口/JS 调试在正式包里被启用。debug 包保留给开发者。
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            WebView.setWebContentsDebuggingEnabled(false)
        }
        // 全 App 只允许竖屏：清单里已声明 portrait，这里再请求一次兜底——
        // 个别 ROM 会忽略清单，分屏/手势旋转后也可能被系统改回横屏
        runCatching { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        enableEdgeToEdge()
        // 崩溃日志：捕获未捕获异常写入本地文件，设置页可查看/复制
        CrashLog.install(applicationContext)
        // 自定义背景：必须在 setContent 前读一次，主题首次组合就要用上高亮/文字色
        AppBackground.load(applicationContext)
        // 上次选的播放器皮肤同样在首帧前读回来，否则进播放页会先闪一下默认皮肤
        PlayerSkinStore.init(applicationContext)
        // MY音乐只有音乐模块：启动直接进音乐，不组合地图 / 足迹 / 资讯等页面
        setContent { MusicAppRoot(onExit = { finish() }) }
        // Android 13+ 通知运行时权限；播放控制通知依赖它
        requestNotificationPermissionOnce()
    }

    override fun onStart() {
        super.onStart()
        com.example.my.data.messaging.AppForegroundState.isForeground = true
        // 回到前台时，如果仍停在某个聊天里，顺手清掉它的系统通知
        com.example.my.data.messaging.AppForegroundState.openConversationId?.let {
            com.example.my.data.messaging.DmNotificationHelper.cancelConversation(applicationContext, it)
        }
    }

    override fun onStop() {
        com.example.my.data.messaging.AppForegroundState.isForeground = false
        super.onStop()
    }

    private fun requestNotificationPermissionOnce() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val prefs = getSharedPreferences("dm_notify_permission", MODE_PRIVATE)
        if (prefs.getBoolean("asked", false)) return
        prefs.edit().putBoolean("asked", true).apply()
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQ_NOTIFICATION
        )
    }

    private fun handleDmIntent(intent: Intent?) {
        if (intent == null) return
        val peer = intent.getIntExtra(
            com.example.my.data.messaging.DmNotificationHelper.EXTRA_DM_PEER_ID,
            0
        )
        if (peer > 0) {
            intent.removeExtra(com.example.my.data.messaging.DmNotificationHelper.EXTRA_DM_PEER_ID)
            intent.removeExtra(EXTRA_OPEN_MESSAGES)
            val handler = openDmHandler
            if (handler != null) handler(peer)
            else com.example.my.data.messaging.DmLaunch.peerId = peer
            return
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_MESSAGES, false)) {
            intent.removeExtra(EXTRA_OPEN_MESSAGES)
            val handler = openMessagesHandler
            if (handler != null) handler()
            else com.example.my.data.messaging.DmLaunch.openMessages = true
        }
    }

    /** 每次回到前台都把桌面卡片重画一次，避免系统把小组件冻在「加载中」白卡 */
    override fun onResume() {
        super.onResume()
        runCatching { com.example.my.widget.WidgetKit.refreshAll(applicationContext) }
    }

    /**
     * 系统内存吃紧或 App 退到后台时，主动放掉封面位图和歌曲故事的内存副本。
     * 磁盘缓存/用户数据不动，回到前台按需重新加载，避免后台一直占着几十 MB。
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        runCatching { CoverRepository.trimMemory(level) }
        runCatching { SongStoryCache.trimMemory(level) }
    }

    /** 通知/小组件点击时 Activity 可能已在栈顶，走 onNewIntent 而不是 onCreate */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(MusicActions.EXTRA_OPEN_MUSIC, false)) {
            openMusicHandler?.invoke()
            intent.removeExtra(MusicActions.EXTRA_OPEN_MUSIC)
        }
        handleDmIntent(intent)
        val target = intent.getStringExtra(WidgetKit.EXTRA_TARGET)
        if (!target.isNullOrBlank()) {
            widgetNavigationHandler?.invoke(
                target, intent.getIntExtra(WidgetKit.EXTRA_GUIDE_ID, 0)
            )
            intent.removeExtra(WidgetKit.EXTRA_TARGET)
            intent.removeExtra(WidgetKit.EXTRA_GUIDE_ID)
        }
    }
}

/** 桌面小组件的点击落点：全部收敛到 AppState，冷/热启动走同一个函数 */
private fun handleWidgetTarget(state: AppState, target: String, guideId: Int) {
    when (target) {
        WidgetKit.TARGET_MAP, WidgetKit.TARGET_STATS -> {
            state.activeTab = AppTab.MAP
            state.footprintMode = 0
            state.screen = ExtraScreen.NONE
            state.showUpload = false
        }
        WidgetKit.TARGET_WEATHER -> state.openScreen(ExtraScreen.WEATHER)
        WidgetKit.TARGET_PLANS -> state.openScreen(ExtraScreen.MY_PLANS)
        WidgetKit.TARGET_COURSE -> state.openScreen(ExtraScreen.COURSE)
        WidgetKit.TARGET_MUSIC -> state.openScreen(ExtraScreen.MUSIC)
        WidgetKit.TARGET_GUIDE -> {
            if (guideId > 0) state.openGuide(guideId)
            else {
                state.activeTab = AppTab.DISCOVER
                state.screen = ExtraScreen.NONE
            }
        }
        WidgetKit.TARGET_CAPTURE -> {
            state.screen = ExtraScreen.NONE
            state.showUpload = true
            state.autoOpenCameraOnUpload = true
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val state = remember { AppState(context) }
    // 退到后台时强制落盘轨迹点：记录中的点按5点/30秒节流写盘，ON_STOP 补一次保证不丢
    val trackLifecycle = LocalLifecycleOwner.current
    DisposableEffect(trackLifecycle, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                state.flushTrack()
                // 播放器退到后台时把“上次这首歌 + 播放位置”补记一次，下次打开接着显示
                MusicPlayer.savePlaybackState()
                // 纯本地模式：退到后台时落一份快照，误删/覆盖还能捞回来
                state.autoSnapshot()
                // 回到桌面时把足迹 / 计划 / 攻略的最新数据推给所有小组件
                WidgetKit.refreshAll(context.applicationContext)
            }
        }
        trackLifecycle.lifecycle.addObserver(observer)
        onDispose { trackLifecycle.lifecycle.removeObserver(observer) }
    }
    // 语音唤醒：只在 App 前台、开关打开、且没停在 AI 页时监听；退后台立即释放麦克风
    val wakeLifecycle = LocalLifecycleOwner.current
    val wakeEngine = remember {
        WakeWordEngine(context.applicationContext) { state.openScreen(ExtraScreen.AI_ASSISTANT) }
    }
    DisposableEffect(wakeLifecycle, state.wakeWordEnabled, state.screen) {
        val active = state.wakeWordEnabled && state.screen != ExtraScreen.AI_ASSISTANT
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (active) wakeEngine.start()
                Lifecycle.Event.ON_STOP -> wakeEngine.stop()
                else -> {}
            }
        }
        wakeLifecycle.lifecycle.addObserver(observer)
        if (active && wakeLifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            wakeEngine.start()
        }
        onDispose {
            wakeLifecycle.lifecycle.removeObserver(observer)
            wakeEngine.stop()
        }
    }
    // 改唤醒词不用重载模型，直接替换匹配用的词
    LaunchedEffect(state.wakeWordPhrase) { wakeEngine.setPhrase(state.wakeWordPhrase) }
    // 全局滚动探测：驱动吸边悬浮球滚动半隐（非消费型连接，挂在根容器）
    val scrollActivity = rememberScrollActivity()
    // AI助手悬浮球：默认不显示，由设置页开关控制（state.aiBallEnabled），长按也可关闭

        // 在线登录、离线模式都放行；只有 CHECKING 停在启动页，LOGGED_OUT 停在登录页
        val canEnterMain = state.authState == AuthState.LOGGED_IN || state.authState == AuthState.GUEST

        // ---------- 返回键第一层（故意最先注册 = 优先级最低）----------
        // Compose 的返回回调按注册顺序「后注册先响应」，所以这里排在页面内容之前，
        // 页面内部自己的返回（回忆页多选、足迹详情等）才有机会先消费掉。
        // 等到没人消费时执行这里的「上一页 / 再按一次退出」。
        var exitPressedAt by remember { mutableLongStateOf(0L) }
        val activity = remember(context) { context.findActivity() }

        // 通知栏 / 桌面小组件点击后直接展开播放器：冷启动读 onCreate 的 intent，
        // 已在前台时由 onNewIntent 触发 openMusicHandler，两条路径都覆盖。
        LaunchedEffect(activity) {
            val i = (activity as? MainActivity)?.intent
            if (i?.getBooleanExtra(MusicActions.EXTRA_OPEN_MUSIC, false) == true) {
                state.openScreen(ExtraScreen.MUSIC)
                i.removeExtra(MusicActions.EXTRA_OPEN_MUSIC)
            }
            val target = i?.getStringExtra(WidgetKit.EXTRA_TARGET)
            if (!target.isNullOrBlank() && i != null) {
                handleWidgetTarget(state, target, i.getIntExtra(WidgetKit.EXTRA_GUIDE_ID, 0))
                i.removeExtra(WidgetKit.EXTRA_TARGET)
                i.removeExtra(WidgetKit.EXTRA_GUIDE_ID)
            }
            // 通知冷启动落点：有具体会话优先直达，否则只打开消息列表
            val dmPeer = com.example.my.data.messaging.DmLaunch.take()
            if (dmPeer != null && dmPeer > 0) {
                state.requestOpenDmPeer(dmPeer)
            } else if (com.example.my.data.messaging.DmLaunch.takeOpenMessages()) {
                state.openScreen(ExtraScreen.MESSAGES)
            }
        }
        DisposableEffect(state) {
            MainActivity.openMusicHandler = { state.openScreen(ExtraScreen.MUSIC) }
            MainActivity.openDmHandler = { peer -> state.requestOpenDmPeer(peer) }
            MainActivity.openMessagesHandler = { state.openScreen(ExtraScreen.MESSAGES) }
            MainActivity.widgetNavigationHandler = { target, guideId ->
                handleWidgetTarget(state, target, guideId)
            }
            onDispose {
                MainActivity.openMusicHandler = null
                MainActivity.openDmHandler = null
                MainActivity.openMessagesHandler = null
                MainActivity.widgetNavigationHandler = null
            }
        }

        BackHandler(enabled = canEnterMain) {
            when {
                // 我的足迹页里：回忆段先退回地图段，这才算「上一页」
                state.screen == ExtraScreen.FOOTPRINTS && state.footprintMode != 0 -> state.footprintMode = 0
                // 已经在主页最外层：两次返回退出应用，第一次只提醒
                // （不做「其它 Tab 先跳回发现页」——底部导航已经能切，返回键突然换 Tab 更像 bug）
                else -> {
                    val now = System.currentTimeMillis()
                    if (now - exitPressedAt <= 2_000L) {
                        activity?.finish()
                    } else {
                        exitPressedAt = now
                        state.toast(tr("再按一次退出应用", "Press back again to exit"))
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (AppBackground.config.enabled) Color.Transparent else WarmBg)
                .nestedScroll(scrollActivity.connection)
        ) {
        // 自定义背景铺在最底层；各页面在启用时使用透明 Bg.Background，让背景透出来
        if (AppBackground.config.enabled) {
            AppBackgroundLayer()
        }
        // 仅 LOGGED_OUT 时全屏显示登录页，新用户直接 GUEST 就能看首页
        if (canEnterMain) {
        // 自定义背景让页面本身透明后，全屏叠层页会透出下面的主框架，实测会把资讯/音乐/首页混在一起。
        // 只要叠层页打开，就先把主框架收起，只留根背景 + 当前页。
        // 驾驶模式是独立车机页：不管有没有开自定义背景，主框架（含负一屏 / 侧屏
        // 与它们的边缘手势层）都彻底不组合，免得底栏残留在沉浸页下面抢触摸。
        val drivingMode = state.screen == ExtraScreen.DRIVING
        val mainCovered = drivingMode || (AppBackground.config.enabled && (
            state.screen != ExtraScreen.NONE ||
                state.currentGuide != null ||
                state.currentRoute != null ||
                // 新建足迹（底部上滑页）也算叠层：页面在自定义背景下是透明的，
                // 主框架不收起来，发现页的 hero / 搜索框 / 卡片会从下面透上来跟表单文字重合
                state.showUpload
            ))
        if (!mainCovered) {
        // ---------- 主框架：Tab 内容 + 底部导航 ----------
        // 负一屏（发现页向右滑出）：主框架整体往右让位并微缩，仪表盘从左侧压上来。
        // 进度只用一个 float 驱动，故重绘成本是 graphicsLayer 级别的，不重组整棵 Tab 树。
        val dashboard = remember { DashboardCtrl() }
        // 「我的」页的功能抽屉（右上角按钮 + 右边缘向左拉两种入口）
        val profileMenu = remember { ProfileMenuCtrl() }
        val shellScope = rememberCoroutineScope()
        // 手势层只有 26dp 宽，拿它自己的 size 当分母会变成一点就开满，必须用屏幕宽度
        val screenWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx()
        }
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val p = dashboard.progress
                val m = profileMenu.progress
                if (p > 0f || m > 0f) {
                    // 负一屏是四分之三侧边面板，主页面不需要大幅让位，只留一点视差
                    translationX = p * size.width * 0.05f - m * size.width * 0.18f
                    val s = 1f - p * 0.018f - m * 0.045f
                    scaleX = s
                    scaleY = s
                    alpha = 1f - m * 0.10f
                }
            }
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = state.activeTab,
                    transitionSpec = {
                        // 自定义背景让两个 Tab 的内容都透明，滑动重叠会看起来像页面混在一起；
                        // 这时直接切换，不再同时组合新旧页。
                        if (AppBackground.config.enabled) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                        // 方向感转场：按 Tab 顺序决定滑入方向（新旧页同时组合含地图 View，时长克制防掉帧）
                        val forward = targetState.ordinal > initialState.ordinal
                        val enter = if (forward) {
                            slideInHorizontally(tween(200)) { it / 4 } + fadeIn(tween(200))
                        } else {
                            slideInHorizontally(tween(200)) { -it / 4 } + fadeIn(tween(200))
                        }
                        val exit = if (forward) {
                            slideOutHorizontally(tween(180)) { -it / 4 } + fadeOut(tween(180))
                        } else {
                            slideOutHorizontally(tween(180)) { it / 4 } + fadeOut(tween(180))
                        }
                        enter togetherWith exit
                        }
                    },
                    label = "tabContent"
                ) { tab ->
                    // 入场动画门控：整页只播一次，避免滚动回收后重播闪烁
                    ProvideStaggerGate {
                        when (tab) {
                            AppTab.DISCOVER -> DiscoverScreen(
                                state = state,
                                // 发现页左上圆钮：直接推开负一屏（和左边缘向右拖同一个面板）
                                onOpenMenu = { shellScope.launch { dashboard.open() } }
                            )
                            AppTab.MAP -> MicroFeedScreen(state)
                            AppTab.TRIPS -> MyPlansScreen(state, embedded = true)
                            AppTab.PROFILE -> ProfileScreen(
                                state = state,
                                onOpenMenu = { shellScope.launch { profileMenu.open() } }
                            )
                        }
                    }
                }
            }
            MyBottomBar(state = state, onUpload = { state.openUpload() })
        }

        // ---------- 负一屏本体（画在主框架之上，跟着同一个 progress 一起进出） ----------
        if (dashboard.visible) {
            DashboardScreen(state = state, ctrl = dashboard)
        }
        if (profileMenu.visible) {
            ProfileMenuScreen(state = state, ctrl = profileMenu)
        }

        // 发现页左边缘向右拖：负一屏的唯一手势入口。
        // 只在「发现页 + 两个抽屉都收起」时挂这层手势，别的 Tab 和已展开状态都不抢触摸，
        // 也不会跟发现页里那些横向滑动的卡片抢事件。
        if (state.activeTab == AppTab.DISCOVER && !dashboard.visible && !profileMenu.visible) {
            val scope = rememberCoroutineScope()
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(26.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = { scope.launch { dashboard.settle() } },
                            onDragCancel = { scope.launch { dashboard.settle() } }
                        ) { change, dragAmount ->
                            change.consume()
                            // 分母用面板宽度（屏宽 76%），和面板上往回拖的灵敏度保持一致
                            dashboard.onDrag(dragAmount, screenWidthPx * 0.76f)
                        }
                    }
            )
            // 边缘暗示条：一条细竖条，告诉用户这里能往右拉（不参与点击）
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp)
                    .width(3.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Text.Muted.copy(alpha = 0.28f))
            )
        }

        // 「我的」页右边缘向左拖：功能抽屉。方向与负一屏相反，两条手势各自守着一边，不打架。
        if (state.activeTab == AppTab.PROFILE && !profileMenu.visible && !dashboard.visible) {
            val scope = rememberCoroutineScope()
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(26.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = { scope.launch { profileMenu.settle() } },
                            onDragCancel = { scope.launch { profileMenu.settle() } }
                        ) { change, dragAmount ->
                            change.consume()
                            // 手指往左（dragAmount < 0）→ 进度增长，抽屉从右侧推入；分母同面板宽度（84%）
                            profileMenu.onDrag(-dragAmount, screenWidthPx * 0.84f)
                        }
                    }
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
                    .width(3.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Text.Muted.copy(alpha = 0.28f))
            )
        }
        }
        }

        // ---------- 叠层页面 ----------
        // 攻略详情（从右侧滑入）
        AnimatedVisibility(
            visible = state.currentGuide != null,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.currentGuide != null) {
                SwipeBackPage(onDismiss = { state.closeGuide() }) {
                    GuideDetailScreen(state)
                }
            }
        }
        // 足迹详情（统一的照片抽屉样式，唯一入口见 RouteMemorySheet）
        AnimatedVisibility(
            visible = state.currentRoute != null,
            enter = if (AppBackground.config.enabled) EnterTransition.None else fadeIn(tween(160)),
            exit = if (AppBackground.config.enabled) ExitTransition.None else fadeOut(tween(150))
        ) {
            val route = state.currentRoute
            if (route != null) {
                RouteMemorySheet(state, route)
            }
        }
        // 上传足迹（从底部上滑）
        AnimatedVisibility(
            visible = state.showUpload,
            enter = slideInVertically(tween(300)) { it },
            exit = slideOutVertically(tween(200)) { it }
        ) {
            if (state.showUpload) {
                UploadScreen(state)
            }
        }

        // 原足迹页：从「我的 > 我的足迹」进入，保留完整地图 + 旅行回忆能力
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.FOOTPRINTS,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.FOOTPRINTS) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    MapScreen(state)
                }
            }
        }

        // 全屏页（设置/通知）
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.SETTINGS,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.SETTINGS) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    SettingsScreen(state)
                }
            }
        }
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.NOTIFICATIONS,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.NOTIFICATIONS) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    NotificationsScreen(state)
                }
            }
        }
        // 攻略发布页
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.GUIDE_CREATE,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.GUIDE_CREATE) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    GuideCreateScreen(state)
                }
            }
        }
        // 我的计划页
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.MY_PLANS,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.MY_PLANS) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    MyPlansScreen(state)
                }
            }
        }
        // 购票专区（火车票 / 机票）
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.TICKET_ZONE,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.TICKET_ZONE) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    TicketZoneScreen(state)
                }
            }
        }
        // 购物专区（全网比价 / 价格走势）
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.SHOPPING,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.SHOPPING) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    ShoppingScreen(state)
                }
            }
        }
        // 隐私空间（加密相册：图片/视频）
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.PRIVACY_VAULT,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.PRIVACY_VAULT) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    PrivacyVaultScreen(state)
                }
            }
        }
        // AI 创作专区（图片 / 视频生成）
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.CREATIVE,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.CREATIVE) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    AiCreationScreen(state)
                }
            }
        }
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.NEWS,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.NEWS) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    NewsScreen(state)
                }
            }
        }
        // 股市 / 行情页（从资讯页「今日行情」卡片进入）
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.STOCK,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.STOCK) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    StockScreen(state)
                }
            }
        }
        // 我的交易（模拟盘）：资产 / 行情 / 委托 / 持仓 / 我的
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.TRADE,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.TRADE) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    TradeScreen(state)
                }
            }
        }
        // 离线旅行页
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.OFFLINE,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.OFFLINE) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    OfflineScreen(state)
                }
            }
        }
        // 本地音乐播放器
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.MUSIC,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.MUSIC) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    MusicScreen(state)
                }
            }
        }
        // 驾驶模式：全屏沉浸车机页。不用 SwipeBackPage——横屏下左边缘侧滑
        // 会跟地图手势抢事件，退出统一走页面内的 ‹ 按钮与系统返回键。
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.DRIVING,
            enter = fadeIn(tween(180)),
            exit = ExitTransition.None
        ) {
            if (state.screen == ExtraScreen.DRIVING) {
                DrivingScreen(state = state)
            }
        }
        // 旅行达人排行榜
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.TRAVELER_RANK,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.TRAVELER_RANK) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    TravelerRankScreen(state = state, onBack = { state.closeScreen() })
                }
            }
        }
        // 课表
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.COURSE,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.COURSE) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    CourseScreen(state)
                }
            }
        }
        // 天气页
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.WEATHER,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.WEATHER) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    ProvideStaggerGate { WeatherScreen(state) }
                }
            }
        }
        // 消息页（私信为主）
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.MESSAGES,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.MESSAGES) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    MessagesScreen(state)
                }
            }
        }
        // 他人主页（攻略作者 / 评论头像 / 粉丝关注列表 / 搜索账号点入）
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.USER_PROFILE,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.USER_PROFILE) {
                SwipeBackPage(onDismiss = { state.closeUserProfile() }) {
                    UserProfileScreen(state)
                }
            }
        }
        // AI 助手页
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.AI_ASSISTANT,
            enter = slideInVertically(tween(280)) { it },
            exit = slideOutVertically(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.AI_ASSISTANT) {
                AiAssistantScreen(state)
            }
        }
        // 年度足迹报告
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.YEAR_REPORT,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.YEAR_REPORT) {
                SwipeBackPage(onDismiss = { state.closeScreen() }) {
                    YearReportScreen(state)
                }
            }
        }
        // 新建出行计划弹窗（底部上滑，与上传面板一致的 sheet 语义）
        AnimatedVisibility(
            visible = state.showPlanSheet,
            enter = slideInVertically(tween(300)) { it },
            exit = slideOutVertically(tween(200)) { it }
        ) {
            if (state.showPlanSheet) {
                PlanSheet(state, onDismiss = { state.closePlanSheet() })
            }
        }

        // 行程详情弹窗（每日安排 / 出行清单 / 归档）
        AnimatedVisibility(
            visible = state.tripDetailId != null,
            enter = slideInVertically(tween(300)) { it },
            exit = slideOutVertically(tween(200)) { it }
        ) {
            val plan = state.tripDetailId?.let { id -> state.tripPlans.firstOrNull { it.id == id } }
            if (plan != null) {
                PlanDetailSheet(state, plan, onDismiss = { state.closeTripDetail() })
            }
        }

        // AI 生成行程弹窗
        AnimatedVisibility(
            visible = state.showAiTripSheet,
            enter = slideInVertically(tween(300)) { it },
            exit = slideOutVertically(tween(200)) { it }
        ) {
            if (state.showAiTripSheet) {
                AiTripSheet(state, onDismiss = { state.closeAiTripSheet() })
            }
        }

        // 网页搜索必须画在弹窗之后：AI 行程结果里点「参考网页」时，
        // 网页页覆盖在弹窗上，关掉网页后仍能看到原行程结果。
        AnimatedVisibility(
            visible = state.screen == ExtraScreen.WEB_SEARCH,
            enter = if (AppBackground.config.enabled) EnterTransition.None else slideInHorizontally(tween(250)) { it },
            exit = if (AppBackground.config.enabled) ExitTransition.None else slideOutHorizontally(tween(200)) { it }
        ) {
            if (state.screen == ExtraScreen.WEB_SEARCH) {
                // 网页页关掉边缘侧滑退出：这里「退出」只留左上角 < 一个出口，
                // 侧滑和系统返回都当「浏览器后退」用，不然会误退掉整页结果
                SwipeBackPage(enabled = false, onDismiss = { state.closeWebSearch() }) {
                    WebSearchScreen(
                        query = state.webSearchQuery,
                        directUrl = state.webDirectUrl,
                        onBack = { state.closeWebSearch() }
                    )
                }
            }
        }

        // ---------- 吸边悬浮球：AI 助手入口，松手吸附最近边缘，滚动半隐，长按关闭 ----------
        if (state.aiBallEnabled) {
            EdgeDockingBall(
                scrolling = scrollActivity.scrolling,
                // 先在当前页面就地发问，不直接跳全屏；点卡片里的「打开完整助手」才进对话页
            onClick = { state.openAiInline() },
                onHide = {
                    state.updateAiBallEnabled(false)
                    state.toast("AI助手球已关闭，可在设置中重新开启")
                }
            )
        }
        // ---------- AI 就地入口卡片：任意页面唤起，自带该页的现场语义提示 ----------
        if (state.aiInlineOpen) {
            AiInlineSheet(state = state, onDismiss = { state.closeAiInline() })
        }
        } // end LOGGED_IN

        // ---------- 登录页：开启云端同步且未登录时全屏显示 ----------
        // 纯本地模式（开关关闭）不会进入 LOGGED_OUT，因此这段对本地模式没有影响。
        // LoginScreen 里仍保留「先逛逛」入口，点了就回到本机模式，不会卡死在登录页。
        if (state.authState == AuthState.LOGGED_OUT) {
            LoginScreen(state)
        }

        // ---------- 私信前台横幅：只在 App 可见时出现，后台走系统通知 ----------
        DmBannerHost(
            state = state,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // ---------- 全局提示条：全 App 统一在页面顶部（黑胶囊，展开/收起动画） ----------
        Box(
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ToastHost(state)
        }

        // ---------- 启动页 ----------
        AnimatedVisibility(
            visible = state.showSplash,
            exit = fadeOut(tween(400))
        ) {
            SplashScreen(onFinish = { state.showSplash = false })
        }

        // ---------- 返回键最上层（最后注册 = 优先级最高）：逐层关闭覆盖层 ----------
        BackHandler(
            enabled = state.aiInlineOpen ||
                    state.mapPickOpen ||
                    state.currentGuide != null ||
                    state.currentRoute != null ||
                    state.showUpload ||
                    state.showPlanSheet ||
                    state.showAiTripSheet ||
                    state.tripDetailId != null ||
                    (state.screen != ExtraScreen.NONE && state.screen != ExtraScreen.DRIVING)
        ) {
            when {
                // 就地卡片是最上层，返回键先收它
                state.aiInlineOpen -> state.closeAiInline()
                // 选点页是发布页里的一层，必须先关它，不能直接关整个发布页
                state.mapPickOpen -> state.closeMapPick()
                // 全屏叠层（AI 助手/设置/天气/通知等）盖在详情页之上，
                // 返回键必须先关它，否则会直接关掉下面的攻略/路线详情，
                // 用户从"就地问 AI"回来时就回不到刚才那篇攻略了
                // —— 但网页页例外：网页历史没见底之前，返回键只回上一页。
                // 左上角 < 始终是「退出回发现页」，这样两层语义不会打架。
                state.screen == ExtraScreen.WEB_SEARCH && MainActivity.webBackHandler?.invoke() == true -> Unit
                // 网页历史见底：退回打开网页前的页面（音乐页搜歌就回音乐页），而不是一律回主界面
                state.screen == ExtraScreen.WEB_SEARCH -> state.closeWebSearch()
                // 原足迹页的回忆段要先用一次返回退回地图段，不能直接关页
                state.screen == ExtraScreen.FOOTPRINTS && state.footprintMode != 0 -> state.footprintMode = 0
                state.screen != ExtraScreen.NONE -> state.closeScreen()
                state.currentGuide != null -> state.closeGuide()
                state.currentRoute != null -> state.closeRoute()
                state.showUpload -> state.requestCloseUpload()
                state.showAiTripSheet -> state.closeAiTripSheet()
                state.tripDetailId != null -> state.closeTripDetail()
                state.showPlanSheet -> state.closePlanSheet()
            }
        }
    }
}

/** 从 Compose 的 LocalContext 里取出宿主 Activity（局部可能还包着 ContextThemeWrapper） */
private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
