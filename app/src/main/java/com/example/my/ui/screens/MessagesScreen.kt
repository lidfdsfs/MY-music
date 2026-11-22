package com.example.my.ui.screens

import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.BackendApi
import com.example.my.data.CloudConfig
import com.example.my.data.DmConversation
import com.example.my.data.DmMessage
import com.example.my.data.FollowEvent
import com.example.my.data.UserLookup
import com.example.my.data.crypto.SuperEncryption
import com.example.my.data.messaging.AppForegroundState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.my.data.messaging.DmNotificationHelper
import com.example.my.data.messaging.DmRuntime
import com.example.my.state.AppState
import com.example.my.state.ExtraScreen
import com.example.my.util.dm.DmMedia
import com.example.my.ui.components.AppTopBar
import com.example.my.ui.components.BackActionRow
import com.example.my.ui.components.EmptyState
import com.example.my.ui.components.ErrorState
import com.example.my.ui.components.LoadingState as LoadingStateView
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Func
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 消息中心：站内私信、关注通知。
 *
 * 数据全部走后端 /api/messages 接口；按账号找人、关注通知、互关/单条限额
 * 需要后端新增字段，未启用时界面会明确提示，不会假装成功。
 */
@Composable
fun MessagesScreen(state: AppState) {
    val context = LocalContext.current
    var openChat by remember { mutableStateOf<DmConversation?>(null) }

    // 点通知进来：等 AppRoot 把 pending peer 放好，再打开对应会话
    LaunchedEffect(state.pendingDmPeerId) {
        val peer = state.consumePendingDmPeer() ?: return@LaunchedEffect
        BackendApi.openConversation(peer)?.let {
            if (it.unread > 0) {
                state.updateDmUnread(state.dmUnread - it.unread)
                DmRuntime.decrementUnread(it.unread)
            }
            openChat = it
        }
    }

    openChat?.let { conv ->
        ChatDetail(
            state = state,
            initial = conv,
            onBack = { openChat = null },
            onChanged = { openChat = it }
        )
        return
    }

    var tab by remember { mutableStateOf(0) }
    var conversations by remember { mutableStateOf<List<DmConversation>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var lookup by remember { mutableStateOf<UserLookup?>(null) }
    var lookupBusy by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf<List<FollowEvent>>(emptyList()) }
    var eventError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refreshConversations() {
        if (!state.cloudSyncEnabled || !BackendApi.loggedIn) return
        scope.launch {
            loading = true
            val r = BackendApi.fetchConversations()
            loading = false
            if (r == null) {
                loadError = "会话加载失败，请检查网络或登录状态"
            } else {
                conversations = r
                state.updateDmUnread(r.sumOf { it.unread })
                loadError = null
            }
        }
    }

    fun doLookup() {
        val account = query.trim()
        if (account.length < 3) {
            state.toast("请输入至少 3 位账号")
            return
        }
        lookupBusy = true
        scope.launch {
            val r = BackendApi.lookupUserByAccount(account)
            lookupBusy = false
            lookup = r
            if (r == null) {
                state.toast("没找到该账号，请确认账号拼写")
            }
        }
    }

    LaunchedEffect(Unit) { refreshConversations() }
    LaunchedEffect(tab) {
        if (tab == 1 && state.cloudSyncEnabled && BackendApi.loggedIn) {
            val r = BackendApi.fetchFollowEvents()
            events = r.orEmpty()
            eventError = if (r == null) "服务端未返回关注通知；补齐 /users/notifications 后可用" else null
            if (r != null) BackendApi.markFollowEventsRead()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg.Background)
    ) {
        AppTopBar(title = tr("消息"))
        BackActionRow(onBack = { state.closeScreen() })

        if (!DmNotificationHelper.areMessageNotificationsEnabled(context)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Bg.SurfaceVariant)
                    .clickable { DmNotificationHelper.openNotificationSettings(context) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "系统通知已关闭，点此开启；否则退到后台收不到私信提醒",
                    color = Func.Danger,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "去开启",
                    color = Brand.Terracotta,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 顶部搜索：按账号找人
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = Text.Primary, fontSize = 14.sp),
                cursorBrush = SolidColor(Brand.Terracotta),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doLookup() }),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.xl))
                            .background(Bg.Surface)
                            .border(1.dp, Bg.Outline.copy(alpha = 0.25f), RoundedCornerShape(Radius.xl))
                            .cardShadow(RoundedCornerShape(Radius.xl))
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = Text.Muted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text("搜索账号", color = Text.Muted, fontSize = 13.sp)
                                }
                                inner()
                            }
                        }
                    }
                }
            )
            Spacer(Modifier.width(10.dp))
            SmallAction(
                text = if (lookupBusy) "查找中…" else "查找",
                enabled = !lookupBusy,
                onClick = { doLookup() }
            )
        }

        // 找人结果：搜索栏下方紧凑用户行
        lookup?.let { l ->
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Bg.Surface)
                    .cardShadow(RoundedCornerShape(18.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(
                    l.user.nickname,
                    size = 40,
                    modifier = Modifier.clickable {
                        state.openUserProfile(l.user.id, l.user.nickname)
                    }
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        l.user.nickname,
                        color = Text.Primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "@" + l.user.username + " · " + when {
                            l.mutual -> "互相关注"
                            l.following -> "已关注，等待对方回关"
                            else -> "已通过账号找到该用户"
                        },
                        color = Text.Muted,
                        fontSize = 11.sp
                    )
                }
                SmallAction("发消息") {
                    scope.launch {
                        val c = BackendApi.openConversation(l.user.id)
                        if (c != null) openChat = c else state.toast("无法打开会话，请稍后重试")
                    }
                }
                Spacer(Modifier.width(6.dp))
                SmallAction(
                    text = if (l.following) "已关注" else "关注",
                    enabled = !l.following
                ) {
                    scope.launch {
                        val f = BackendApi.toggleFollow(l.user.id)
                        if (f == null) {
                            state.toast("关注失败，请检查网络")
                        } else {
                            lookup = l.copy(
                                following = f.following,
                                followsMe = f.followsMe || l.followsMe,
                                mutual = f.mutual || (f.following && l.followsMe)
                            )
                            state.toast(if (f.following) "已关注，对方会收到关注通知" else "已取消关注")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 私信 / 互动切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(Bg.Surface)
                .cardShadow(RoundedCornerShape(50.dp))
                .padding(4.dp)
        ) {
            listOf("私信", "互动通知").forEachIndexed { index, label ->
                val selected = tab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50.dp))
                        .background(if (selected) Brand.Terracotta else Color.Transparent)
                        .clickable { tab = index }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (selected) Color.White else Text.Secondary,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (!state.cloudSyncEnabled || !BackendApi.loggedIn) {
                item {
                    Text(
                        "当前未登录云端。请先在设置中开启云端同步并登录，私信才会走服务器。",
                        color = Text.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Bg.SurfaceVariant)
                            .padding(12.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (tab == 0) {
                if (loading) {
                    item { LoadingStateView(text = "正在加载会话…") }
                }
                loadError?.let { err ->
                    item {
                        ErrorState(
                            title = "会话加载失败",
                            message = err,
                            actionLabel = "重试",
                            onRetry = { refreshConversations() }
                        )
                    }
                }
                if (conversations.isEmpty() && !loading && loadError == null) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Notifications,
                            text = "暂无会话",
                            subtitle = "在上方搜索账号找到对方，点「发消息」开始聊天"
                        )
                    }
                }
                items(conversations, key = { it.id }) { c ->
                    ConversationRow(c) {
                        if (c.unread > 0) {
                            state.updateDmUnread(state.dmUnread - c.unread)
                            DmRuntime.decrementUnread(c.unread)
                        }
                        openChat = c
                    }
                    Spacer(Modifier.height(10.dp))
                }
            } else {
                if (eventError != null) {
                    item {
                        ErrorState(title = "通知暂不可用", message = eventError)
                    }
                }
                if (events.isEmpty() && eventError == null) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Notifications,
                            text = "暂无关注通知",
                            subtitle = "对方关注你后会出现在这里，并可直接回关"
                        )
                    }
                }
                items(events, key = { it.id }) { e ->
                    FollowEventRow(
                        event = e,
                        onProfile = { state.openUserProfile(e.actor.id, e.actor.nickname) },
                        onFollowBack = {
                            scope.launch {
                                val f = BackendApi.toggleFollow(e.actor.id)
                                if (f == null) {
                                    state.toast("回关失败，请检查网络")
                                } else {
                                    events = events.map {
                                        if (it.id == e.id) it.copy(iFollowActor = f.following) else it
                                    }
                                    state.toast(if (f.mutual) "已回关，可以畅聊了" else "已关注对方")
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(c: DmConversation, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .cardShadow(RoundedCornerShape(Radius.lg))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(c.peer.nickname, size = 48)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.peer.nickname,
                    color = Text.Primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                if (c.relationshipKnown) {
                    Text(
                        if (c.mutual) "互关" else "未互关",
                        color = if (c.mutual) Brand.Terracotta else Text.Muted,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(if (c.mutual) Brand.Terracotta.copy(alpha = 0.12f) else Bg.SurfaceVariant)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    SuperEncryption.isEncryptedBody(c.lastPreview) ->
                        SuperEncryption.cachedPreview(context, c.id)
                            ?: tr("新消息", "New message")
                    c.lastPreview.isBlank() -> "还没有消息，打个招呼吧"
                    else -> c.lastPreview
                },
                color = Text.Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(shortTime(c.lastMessageAt), color = Text.Muted, fontSize = 11.sp)
            AnimatedVisibility(
                visible = c.unread > 0,
                enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.6f),
                exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.6f)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .height(18.dp)
                            .widthIn(min = 18.dp)
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(Func.Danger)
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (c.unread > 99) "99+" else "${c.unread}",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowEventRow(event: FollowEvent, onProfile: () -> Unit, onFollowBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .cardShadow(RoundedCornerShape(Radius.lg))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            event.actor.nickname,
            size = 44,
            modifier = Modifier.clickable { onProfile() }
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${event.actor.nickname} 关注了你",
                color = Text.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                if (event.iFollowActor) "已关注对方" else "回关后即可无限畅聊",
                color = Text.Muted,
                fontSize = 11.sp
            )
        }
        SmallAction(
            text = if (event.iFollowActor) "已关注" else "回关",
            enabled = !event.iFollowActor,
            onClick = onFollowBack
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatDetail(
    state: AppState,
    initial: DmConversation,
    onBack: () -> Unit,
    onChanged: (DmConversation) -> Unit
) {
    var conversation by remember(initial.id) { mutableStateOf(initial) }
    var messages by remember(initial.id) { mutableStateOf<List<DmMessage>>(emptyList()) }
    var loading by remember(initial.id) { mutableStateOf(true) }
    var draft by remember(initial.id) { mutableStateOf("") }
    var sending by remember(initial.id) { mutableStateOf(false) }
    var replyTo by remember(initial.id) { mutableStateOf<DmMessage?>(null) }
    var actionMsg by remember { mutableStateOf<DmMessage?>(null) }
    var mediaBusy by remember(initial.id) { mutableStateOf(false) }
    var showAttach by remember(initial.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val e2eeEnabled = SuperEncryption.isEnabled(context)
    var peerKeyKnown by remember(initial.id) {
        mutableStateOf(SuperEncryption.peerKeyKnown(context, initial.peer.id))
    }
    // 本地已删除 / 已撤回的消息 id：轮询回拉时据此过滤，避免被服务端旧记录覆盖复活
    val deletedIds = remember(initial.id) { mutableStateListOf<Int>() }
    val recalledIds = remember(initial.id) { mutableStateListOf<Int>() }

    fun applyLocalEdits(list: List<DmMessage>): List<DmMessage> =
        list.filterNot { deletedIds.contains(it.id) }
            .map { if (recalledIds.contains(it.id)) it.copy(recalled = true) else it }

    /** 解密并过滤掉纯握手消息；握手只用于换公钥，不占用聊天记录。 */
    fun decode(list: List<DmMessage>): List<DmMessage> {
        val decorated = SuperEncryption.decorate(context, initial.id, initial.peer.id, list)
        peerKeyKnown = SuperEncryption.peerKeyKnown(context, initial.peer.id)
        return decorated.filterNot { it.e2eeHandshake }
    }

    /** 对方公钥还没拿到时，自动发一条只含公钥的握手消息；对方打开聊天也会自动回握手。 */
    fun maybeHandshake() {
        if (!e2eeEnabled || !BackendApi.loggedIn) return
        if (SuperEncryption.peerKeyKnown(context, initial.peer.id)) return
        if (!conversation.canSend && conversation.relationshipKnown) return
        if (SuperEncryption.handshakeSentRecently(context, initial.peer.id)) return
        val hs = SuperEncryption.handshakeEnvelope(context) ?: return
        scope.launch {
            val r = BackendApi.sendDmMessage(conversation.id, SuperEncryption.LOCK_BODY, e2eeEnvelope = hs)
            if (r.ok) {
                SuperEncryption.markHandshakeSent(context, initial.peer.id)
            }
        }
    }

    DisposableEffect(initial.id) {
        AppForegroundState.openConversationId = initial.id
        DmNotificationHelper.cancelConversation(context, initial.id)
        onDispose {
            if (AppForegroundState.openConversationId == initial.id) {
                AppForegroundState.openConversationId = null
            }
        }
    }

    LaunchedEffect(initial.id) {
        val t = BackendApi.fetchThread(initial.id)
        loading = false
        if (t != null) {
            SuperEncryption.learnFromMessages(context, initial.peer.id, t.messages)
            messages = decode(applyLocalEdits(t.messages))
            t.conversation?.let {
                conversation = it
                onChanged(it)
            }
            BackendApi.markConversationRead(initial.id, t.messages.maxOfOrNull { it.seq } ?: 0)
            DmNotificationHelper.cancelConversation(context, initial.id)
            maybeHandshake()
        } else {
            state.toast("消息加载失败，请检查网络")
        }
    }

    // 轻量轮询补齐对方新消息；离开聊天页后协程自动取消。
    LaunchedEffect(initial.id) {
        while (true) {
            delay(4_000)
            val t = BackendApi.fetchThread(initial.id, 40) ?: continue
            SuperEncryption.learnFromMessages(context, initial.peer.id, t.messages)
            val fresh = decode(applyLocalEdits(t.messages))
            // 保留本地尚未拿到回执的待发送气泡，避免轮询回拉把它清掉
            val pendingLocal = messages.filter { it.pending }
            val merged = if (pendingLocal.isEmpty()) fresh else fresh + pendingLocal
            if (merged.size != messages.size ||
                merged.lastOrNull()?.id != messages.lastOrNull()?.id
            ) {
                messages = merged
            }
            t.conversation?.let {
                if (it != conversation) {
                    conversation = it
                    onChanged(it)
                }
            }
        }
    }

    val canSend = if (conversation.relationshipKnown) conversation.canSend else true

    fun sendMedia(uri: Uri, kind: String) {
        if (!canSend || mediaBusy) return
        if (e2eeEnabled) {
            state.toast(tr("超级加密模式下暂不支持图片/视频，请先关闭再发送", "Photos/videos are not supported in super encrypted mode"))
            return
        }
        mediaBusy = true
        scope.launch {
            val prepared = if (kind == "image") DmMedia.prepareImage(context, uri)
            else DmMedia.prepareVideo(context, uri)
            val extra = prepared?.let { DmMedia.upload(it) }
            if (prepared == null || extra == null) {
                mediaBusy = false
                state.toast(tr("媒体处理失败，请重试", "Failed to prepare media"))
                return@launch
            }
            val ref = replyTo
            val tempId = -(System.currentTimeMillis().toInt())
            val pendingMsg = DmMessage(
                id = tempId,
                seq = 0,
                body = "",
                mine = true,
                recalled = false,
                createdAt = nowIso(),
                replyToId = ref?.id,
                replyToBody = ref?.replyToBody,
                replyToMine = ref?.replyToMine ?: false,
                type = kind,
                mediaUrl = extra.optString("url").ifBlank { null },
                thumbUrl = extra.optString("thumb").ifBlank { null },
                width = extra.optInt("width").takeIf { it != 0 },
                height = extra.optInt("height").takeIf { it != 0 },
                duration = extra.optLong("duration").takeIf { it != 0L },
                mediaBytes = extra.optLong("bytes").takeIf { it != 0L },
                mediaMime = extra.optString("mime").ifBlank { null },
                pending = true
            )
            messages = messages + pendingMsg
            val r = BackendApi.sendDmMessage(
                conversation.id, "", replyToId = ref?.id, type = kind, media = extra
            )
            mediaBusy = false
            if (!r.ok) {
                messages = messages.filterNot { it.id == tempId }
                state.toast(r.error ?: tr("发送失败", "Send failed"))
            } else {
                messages = messages.filterNot { it.id == tempId }
                r.message?.let { messages = messages + it }
                r.conversation?.let {
                    conversation = it
                    onChanged(it)
                }
                replyTo = null
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { sendMedia(it, "image") }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { sendMedia(it, "video") }
    }

    val securityLine = remember(e2eeEnabled, peerKeyKnown, conversation.peer.id) {
        if (e2eeEnabled) SuperEncryption.chatStatus(context, conversation.peer.id) else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg.Background)
            .statusBarsPadding()
    ) {
        ChatTopBar(
            conversation = conversation,
            securityLine = securityLine,
            onBack = onBack,
            onOpenProfile = { state.openUserProfile(conversation.peer.id, conversation.peer.nickname) },
            onMore = { state.toast(tr("更多为演示占位", "More options placeholder")) }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (loading) {
                item { LoadingStateView(text = "正在加载消息…") }
            }
            if (!loading && messages.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Notifications,
                        text = "还没有消息",
                        subtitle = "发第一条打个招呼吧"
                    )
                }
            }
            itemsIndexed(messages) { index, m ->
                val showTime = index == 0 || messages[index - 1].createdAt != m.createdAt
                MessageBubbleRow(
                    message = m,
                    peerName = conversation.peer.nickname,
                    showTime = showTime,
                    onLongClick = { actionMsg = m }
                )
            }
        }

        // 关系提示条
        RelationshipHint(
            conversation = conversation,
            onFollow = {
                scope.launch {
                    val f = BackendApi.toggleFollow(conversation.peer.id)
                    if (f == null) {
                        state.toast("关注失败，请检查网络")
                    } else {
                        val mutualNow = f.mutual || (f.following && conversation.peerFollowsMe)
                        conversation = conversation.copy(
                            iFollowPeer = f.following,
                            peerFollowsMe = f.followsMe || conversation.peerFollowsMe,
                            mutual = mutualNow,
                            canSend = if (mutualNow) true else conversation.canSend
                        )
                        onChanged(conversation)
                        state.toast(if (mutualNow) "已互关，可以畅聊了" else "已关注，等待对方回关")
                    }
                }
            }
        )

        // 回复引用条
        replyTo?.let { ref ->
            ReplyQuoteBar(
                ref = ref,
                peerName = conversation.peer.nickname,
                onCancel = { replyTo = null }
            )
        }

        ChatInputBar(
            draft = draft,
            canSend = canSend,
            sending = sending,
            onDraftChange = { draft = it },
            onSend = {
                val text = draft.trim()
                if (text.isEmpty()) return@ChatInputBar
                // 超级加密：先加密再发；拿不到对方公钥时绝不退化成明文，只发一次握手
                val envelope = if (e2eeEnabled) {
                    SuperEncryption.encrypt(context, conversation.id, conversation.peer.id, text)
                } else null
                if (e2eeEnabled && envelope == null) {
                    sending = false
                    draft = text
                    maybeHandshake()
                    state.toast(
                        tr(
                            "还没拿到对方公钥，已发送加密握手；等对方上线后重发",
                            "Peer key missing. A handshake was sent; retry after they come online"
                        )
                    )
                    return@ChatInputBar
                }
                draft = ""
                sending = true
                val ref = replyTo
                val tempId = -(System.currentTimeMillis().toInt())
                val pendingMsg = DmMessage(
                    id = tempId,
                    seq = 0,
                    body = text,
                    mine = true,
                    recalled = false,
                    createdAt = nowIso(),
                    replyToId = ref?.id,
                    replyToBody = ref?.replyToBody,
                    replyToMine = ref?.replyToMine ?: false,
                    pending = true
                )
                messages = messages + pendingMsg
                scope.launch {
                    val r = BackendApi.sendDmMessage(
                        conversation.id,
                        if (envelope != null) SuperEncryption.LOCK_BODY else text,
                        replyToId = ref?.id,
                        e2eeEnvelope = envelope
                    )
                    sending = false
                    if (!r.ok) {
                        messages = messages.filterNot { it.id == tempId }
                        draft = text
                        state.toast(r.error ?: "发送失败")
                    } else {
                        messages = messages.filterNot { it.id == tempId }
                        r.message?.let {
                            if (envelope != null && it.id > 0) {
                                SuperEncryption.saveSentPlain(context, conversation.id, it.id, text)
                            }
                            messages = messages + it.copy(
                                displayBody = if (envelope != null) text else it.displayBody
                            )
                        }
                        peerKeyKnown = SuperEncryption.peerKeyKnown(context, conversation.peer.id)
                        r.conversation?.let {
                            conversation = it
                            onChanged(it)
                        }
                        replyTo = null
                    }
                }
            },
            onVoice = { state.toast(tr("语音消息暂未开放", "Voice messages unavailable")) },
            onEmoji = { state.toast(tr("表情面板为演示占位", "Emoji panel placeholder")) },
            onMore = { showAttach = true }
        )
    }

    if (showAttach) {
        ModalBottomSheet(
            onDismissRequest = { showAttach = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                AttachEntry(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = tr("发送图片", "Send photo"),
                    subtitle = tr("先压缩再发送，节省流量", "Compressed before sending")
                ) {
                    showAttach = false
                    imagePicker.launch("image/*")
                }
                AttachEntry(
                    icon = Icons.Outlined.Videocam,
                    title = tr("发送视频", "Send video"),
                    subtitle = tr("自动压缩并抽取封面", "Compressed with a poster frame")
                ) {
                    showAttach = false
                    videoPicker.launch("video/*")
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    // 长按消息操作菜单（回复 / 复制 / 撤回 / 删除）
    actionMsg?.let { m ->
        MessageActionSheet(
            message = m,
            onDismiss = { actionMsg = null },
            onReply = {
                replyTo = m
                actionMsg = null
            },
            onCopy = {
                val shown = m.displayBody ?: m.body
                if (shown.isNotBlank()) clipboard.setText(AnnotatedString(shown))
                actionMsg = null
                state.toast(tr("已复制", "Copied"))
            },
            onRecall = {
                actionMsg = null
                recalledIds.add(m.id)
                scope.launch {
                    val ok = BackendApi.recallMessage(m.id)
                    messages = messages.map { if (it.id == m.id) it.copy(recalled = true) else it }
                    if (!ok) state.toast(tr("撤回仅在本机生效，服务端暂未开放撤回", "Recall is local only"))
                }
            },
            onDelete = {
                actionMsg = null
                deletedIds.add(m.id)
                scope.launch {
                    BackendApi.deleteMessage(conversation.id, m.id)
                    messages = messages.filterNot { it.id == m.id }
                }
            }
        )
    }
}

/** 聊天顶部栏：返回 + 头像 + 昵称/关系 + 更多 */
@Composable
private fun ChatTopBar(
    conversation: DmConversation,
    securityLine: String?,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg.Surface)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIcon(
            icon = Icons.Outlined.KeyboardArrowLeft,
            desc = tr("返回"),
            onClick = onBack
        )
        Spacer(Modifier.width(4.dp))
        Avatar(
            conversation.peer.nickname,
            size = 34,
            modifier = Modifier.clickable { onOpenProfile() }
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f).clickable { onOpenProfile() }
        ) {
            Text(
                conversation.peer.nickname,
                color = Text.Primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                when {
                    !conversation.relationshipKnown -> tr("关系状态待同步", "Relationship pending")
                    conversation.mutual -> tr("互相关注 · 可无限畅聊", "Mutual · unlimited chat")
                    else -> tr("未互关 · 互关后可聊", "Mutual follow required")
                },
                color = Text.Muted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!securityLine.isNullOrBlank()) {
                Text(
                    securityLine,
                    color = Brand.Terracotta,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        RoundIcon(
            icon = Icons.Outlined.MoreHoriz,
            desc = tr("更多"),
            onClick = onMore
        )
    }
}

/** 关系提示条：额度/互关状态 + 关注按钮 */
@Composable
private fun RelationshipHint(conversation: DmConversation, onFollow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg.SurfaceVariant)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        val hint = when {
            !conversation.relationshipKnown ->
                tr("关系状态待服务端同步；当前先按可发送处理", "Relationship syncing; send allowed for now")
            conversation.mutual ->
                tr("你们已互相关注，可以无限畅聊", "You follow each other, unlimited chat")
            conversation.remainingMessages <= 0 ->
                tr("未互关，当前不能聊天；关注并等对方回关后即可对话", "Mutual follow required to chat")
            else ->
                tr("未互关，当前不能聊天；互关后会自动解锁", "Mutual follow required to chat")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                hint,
                color = Text.Secondary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            if (conversation.relationshipKnown && !conversation.mutual) {
                if (!conversation.iFollowPeer) {
                    SmallAction("关注 TA", onClick = onFollow)
                } else {
                    Text(tr("已关注，等待回关", "Followed, waiting"), color = Text.Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

/** 单条消息气泡（含时间分隔、引用回复、左右头像） */
@Composable
private fun MessageBubbleRow(
    message: DmMessage,
    peerName: String,
    showTime: Boolean,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "bubblePress"
    )
    val enterAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enterAnim.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
    }
    val shownBody = message.displayBody ?: message.body
    // 深色下表面与背景对比偏小，对方气泡换更亮的次级容器、描边提亮一档，避免糊成一片
    val dark = Bg.Surface.luminance() <= 0.55f
    val otherBg = if (dark) Bg.SurfaceVariant else Bg.Surface
    val outlineColor = if (dark) Bg.OutlineStrong else Bg.Outline

    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enterAnim.value * if (message.pending) 0.62f else 1f
                translationY = (1f - enterAnim.value) * 12.dp.toPx()
            }
    ) {
        if (showTime) {
            Text(
                shortTime(message.createdAt),
                color = Text.Muted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            if (!message.mine) {
                Avatar(peerName, size = 38)
                Spacer(Modifier.width(8.dp))
            }
            if (message.recalled) {
                Text(
                    tr("消息已撤回", "Message recalled"),
                    color = Text.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            } else {
                val bubbleShape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (message.mine) 18.dp else 4.dp,
                    bottomEnd = if (message.mine) 4.dp else 18.dp
                )
                val isMedia = message.type == "image" || message.type == "video"
                Box(
                    modifier = Modifier
                        .widthIn(max = 276.dp)
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                        .clip(bubbleShape)
                        .background(
                            if (message.mine)
                                Brush.linearGradient(listOf(Brand.Terracotta, Brand.TerracottaDark))
                            else Brush.linearGradient(listOf(otherBg, otherBg))
                        )
                        .then(
                            if (!message.mine)
                                Modifier.border(
                                    1.dp,
                                    outlineColor.copy(alpha = if (dark) 0.55f else 0.25f),
                                    bubbleShape
                                )
                            else Modifier
                        )
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {},
                            onLongClick = onLongClick
                        )
                        .then(if (isMedia) Modifier else Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                ) {
                    Column {
                        if (!message.replyToBody.isNullOrBlank()) {
                            QuoteStrip(message, peerName)
                        }
                        if (isMedia) {
                            MediaBubble(message)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    shownBody,
                                    color = if (message.mine) Color.White else Text.Primary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (message.pending) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (message.mine) Color.White.copy(alpha = 0.7f) else Text.Muted
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (message.mine) {
                Spacer(Modifier.width(8.dp))
                Avatar("我", size = 38)
            }
        }
    }
}

/** 图片 / 视频消息气泡：封面或图片 + 视频播放角标与时长 */
@Composable
private fun MediaBubble(message: DmMessage) {
    val url = dmMediaUrl(message.thumbUrl ?: message.mediaUrl)
    val ratio = if (message.width != null && message.height != null &&
        message.width > 0 && message.height > 0
    ) {
        (message.width.toFloat() / message.height.toFloat()).coerceIn(0.55f, 1.8f)
    } else 0.78f
    Box(
        modifier = Modifier
            .widthIn(max = 220.dp)
            .aspectRatio(ratio)
            .background(Bg.SurfaceVariant)
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    if (message.type == "video") Icons.Outlined.Videocam else Icons.Outlined.PhotoLibrary,
                    null,
                    tint = Text.Muted,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        if (message.type == "video") {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            message.duration?.let { dur ->
                Text(
                    formatDuration(dur),
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        if (message.pending) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Text(tr("发送中…", "Sending…"), color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

/** 消息媒体相对路径补全：联网模式下 /uploads 路径拼服务端域名 */
private fun dmMediaUrl(path: String?): String? {
    val p = path?.takeIf { it.isNotBlank() } ?: return null
    return when {
        p.startsWith("http") || p.startsWith("file://") -> p
        p.startsWith("/") -> if (CloudConfig.ENABLED) BackendApi.BASE_URL + p else p
        else -> BackendApi.fullUrl(p)
    }
}

private fun formatDuration(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return if (m > 0) "%d:%02d".format(m, s) else "0:%02d".format(s)
}

/** 气泡内的被回复引用条 */
@Composable
private fun QuoteStrip(message: DmMessage, peerName: String) {
    Row(
        modifier = Modifier
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(
                if (message.mine) Color.White.copy(alpha = 0.15f) else Bg.SurfaceVariant
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (message.mine) Color.White.copy(alpha = 0.6f)
                    else Brand.Terracotta.copy(alpha = 0.5f)
                )
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                if (message.replyToMine) tr("我", "Me") else peerName,
                color = if (message.mine) Color.White.copy(alpha = 0.8f) else Text.Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                message.displayReplyToBody ?: message.replyToBody ?: "",
                color = if (message.mine) Color.White.copy(alpha = 0.85f) else Text.Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 输入框上方的回复引用条（可取消） */
@Composable
private fun ReplyQuoteBar(ref: DmMessage, peerName: String, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg.SurfaceVariant)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (ref.mine) tr("回复我", "Replying to me") else tr("回复 $peerName", "Replying to $peerName"),
                color = Brand.Terracotta,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                ref.displayBody ?: ref.body,
                color = Text.Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Outlined.Close,
            contentDescription = tr("取消回复", "Cancel reply"),
            tint = Text.Muted,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onCancel)
                .padding(5.dp)
        )
    }
}

/** 底部输入栏：语音 / 输入框 / 表情 / 更多或发送 */
@Composable
private fun ChatInputBar(
    draft: String,
    canSend: Boolean,
    sending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onEmoji: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg.Surface)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIcon(
            icon = Icons.Outlined.Mic,
            desc = tr("语音", "Voice"),
            onClick = onVoice
        )
        Spacer(Modifier.width(8.dp))
        if (canSend) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = Text.Primary, fontSize = 14.sp),
                cursorBrush = SolidColor(Brand.Terracotta),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(Bg.SurfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (draft.isEmpty()) {
                            Text(tr("说点什么…", "Say something…"), color = Text.Muted, fontSize = 14.sp)
                        }
                        inner()
                    }
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(Bg.SurfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(tr("互相关注后才能继续对话", "Follow each other to continue"), color = Text.Muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        RoundIcon(
            icon = Icons.Outlined.SentimentSatisfiedAlt,
            desc = tr("表情", "Emoji"),
            onClick = onEmoji
        )
        Spacer(Modifier.width(8.dp))
        if (draft.isNotBlank() && canSend && !sending) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Brand.Terracotta, Brand.TerracottaDark)))
                    .clickable(onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Send,
                    contentDescription = tr("发送"),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            RoundIcon(
                icon = Icons.Outlined.Add,
                desc = tr("更多"),
                onClick = onMore
            )
        }
    }
}

/** 长按消息弹出的操作面板：回复 / 复制 / 撤回 / 删除 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionSheet(
    message: DmMessage,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onRecall: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Bg.Surface
    ) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (message.mine) tr("我", "Me") else tr("对方", "The other party"),
                        color = Text.Muted,
                        fontSize = 11.sp
                    )
                    Text(
                        message.displayBody ?: message.body,
                        color = Text.Primary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Bg.Outline.copy(alpha = 0.15f))
            )
            ActionItem(Icons.Outlined.Reply, tr("回复", "Reply"), onClick = onReply)
            if (message.displayBody?.isNotBlank() == true || message.body.isNotBlank()) {
                ActionItem(Icons.Outlined.ContentCopy, tr("复制", "Copy"), onClick = onCopy)
            }
            if (message.mine && !message.recalled) {
                ActionItem(Icons.Outlined.Undo, tr("撤回", "Recall"), onClick = onRecall)
            }
            ActionItem(Icons.Outlined.Delete, tr("删除", "Delete"), danger = true, onClick = onDelete)
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (danger) Func.Danger.copy(alpha = 0.10f) else Bg.SurfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (danger) Func.Danger else Text.Primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            color = if (danger) Func.Danger else Text.Primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AttachEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Bg.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Brand.Terracotta.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Brand.Terracotta, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Text.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RoundIcon(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    tint: Color = Text.Primary
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Bg.SurfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = desc,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun Avatar(name: String, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Brand.TerracottaLight, Brand.Terracotta)))
            .border(1.dp, Bg.Outline.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.take(1).ifBlank { "旅" },
            color = Color.White,
            fontSize = (size / 2.6f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SmallAction(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (enabled) Brand.Terracotta else Bg.SurfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) Color.White else Text.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun shortTime(iso: String): String {
    if (iso.isBlank()) return ""
    // 后端返回 2026-11-17T13:21:06；消息列表只展示日期/时间尾部
    val t = iso.replace("T", " ")
    return when {
        t.length >= 16 -> t.substring(5, 16)
        t.length >= 10 -> t.substring(5, 10)
        else -> t
    }
}

/** 本地待发送消息使用的本地时间戳，格式与后端 created_at 对齐 */
private fun nowIso(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
