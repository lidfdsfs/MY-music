package com.example.my.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.model.Comment
import com.example.my.model.CommentReply
import com.example.my.model.Guide
import com.example.my.state.AppState
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Func
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow
import com.example.my.ui.i18n.Lang
import com.example.my.ui.i18n.tr

/** 回复目标：楼中楼回复时记录所属主评论与对方昵称 */
internal data class CommentTarget(val commentId: Int, val userName: String)

/**
 * 评论区（抖音式）：头像 + 昵称 + 时间 + 点赞、楼中楼回复、展开/收起、长按删除自己的评论。
 * 以 LazyListScope 扩展接入攻略详情的 LazyColumn，避免嵌套滚动。
 */
internal fun LazyListScope.commentItems(
    state: AppState,
    guide: Guide,
    expandedIds: Set<Int>,
    onToggleReplies: (Int) -> Unit,
    onReply: (CommentTarget) -> Unit
) {
    if (guide.commentList.isEmpty()) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tr("还没有评论，来抢沙发吧～"), color = Text.Muted, fontSize = 13.sp)
            }
        }
        return
    }

    // key 必须唯一：LazyColumn 遇到重复 key 会抛 IllegalArgumentException
    // "Key ... was already used" 直接崩掉整个详情页（点「评论」时才会走到这里）。
    // id 为 0 的用负索引兜底，保证不与真实 id 冲突。
    itemsIndexed(
        guide.commentList,
        key = { index, c -> if (c.id != 0) c.id else -(index + 1) }
    ) { _, comment ->
        CommentThreadCard(
            state = state,
            guideId = guide.id,
            comment = comment,
            expanded = comment.id in expandedIds,
            onToggleReplies = { onToggleReplies(comment.id) },
            onReply = { name -> onReply(CommentTarget(comment.id, name)) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentThreadCard(
    state: AppState,
    guideId: Int,
    comment: Comment,
    expanded: Boolean,
    onToggleReplies: () -> Unit,
    onReply: (String) -> Unit
) {
    var pendingDeleteComment by remember { mutableStateOf(false) }
    var pendingDeleteReplyId by remember { mutableStateOf<Int?>(null) }
    val isMine = comment.user == state.settings.nickname

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Bg.Surface)
            .cardShadow(RoundedCornerShape(22.dp))
            .padding(14.dp)
    ) {
        CommentAvatar(comment.user, 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.user,
                    color = Text.Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                Text(comment.time, color = Text.Muted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                LikePill(liked = comment.liked, likes = comment.likes) {
                    state.toggleCommentLike(guideId, comment.id)
                }
                Spacer(Modifier.width(10.dp))
                DislikePill(disliked = comment.disliked, dislikes = comment.dislikes) {
                    state.toggleCommentDislike(guideId, comment.id)
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = comment.content,
                color = Text.Primary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        // 点正文直接进入回复该评论
                        onClick = { onReply(comment.user) },
                        onLongClick = { if (isMine) pendingDeleteComment = true }
                    )
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("回复"),
                    color = Text.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .clickable { onReply(comment.user) }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                if (comment.replies.isNotEmpty()) {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = if (expanded) tr("收起回复") else tr(
                "查看 ${comment.replies.size} 条回复 ˅",
                "View ${comment.replies.size} replies ˅"
            ),
                        color = Brand.Terracotta,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .clickable(onClick = onToggleReplies)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (expanded && comment.replies.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    comment.replies.forEach { reply ->
                        ReplyRow(
                            state = state,
                            guideId = guideId,
                            commentId = comment.id,
                            reply = reply,
                            onReply = onReply,
                            onLongPress = {
                                if (reply.user == state.settings.nickname) pendingDeleteReplyId = reply.id
                            }
                        )
                    }
                }
            }
        }
    }

    if (pendingDeleteComment) {
        DeleteConfirmDialog(
            text = tr("删除后其他旅人将看不到这条评论。"),
            onDismiss = { pendingDeleteComment = false },
            onConfirm = {
                state.deleteComment(guideId, comment.id)
                pendingDeleteComment = false
                state.toast(tr("已删除该评论"))
            }
        )
    }
    pendingDeleteReplyId?.let { replyId ->
        DeleteConfirmDialog(
            text = tr("删除后其他旅人将看不到这条回复。"),
            onDismiss = { pendingDeleteReplyId = null },
            onConfirm = {
                state.deleteReply(guideId, comment.id, replyId)
                pendingDeleteReplyId = null
                state.toast(tr("已删除该回复"))
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReplyRow(
    state: AppState,
    guideId: Int,
    commentId: Int,
    reply: CommentReply,
    onReply: (String) -> Unit,
    onLongPress: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        CommentAvatar(reply.user, 24.dp)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reply.user,
                    color = Text.Primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                reply.replyTo?.let { to ->
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (Lang.isEn) "Reply to @$to" else "回复 @$to",
                        color = Text.Muted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(reply.time, color = Text.Muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = reply.content,
                color = Text.Primary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        // 点回复正文 = 回复该用户
                        onClick = { onReply(reply.user) },
                        onLongClick = onLongPress
                    )
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("回复"),
                    color = Text.Muted,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .clickable { onReply(reply.user) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(Modifier.weight(1f))
                LikePill(liked = reply.liked, likes = reply.likes) {
                    state.toggleCommentLike(guideId, commentId, reply.id)
                }
                Spacer(Modifier.width(8.dp))
                DislikePill(disliked = reply.disliked, dislikes = reply.dislikes) {
                    state.toggleCommentDislike(guideId, commentId, reply.id)
                }
            }
        }
    }
}

@Composable
private fun LikePill(liked: Boolean, likes: Int, onClick: () -> Unit) {
    val color by animateColorAsState(
        targetValue = if (liked) Brand.Terracotta else Text.Muted,
        label = "commentLikeColor"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (liked) tr("取消点赞") else tr("点赞"),
            tint = color,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = if (likes > 0) "$likes" else tr("赞"),
            color = color,
            fontSize = 11.sp,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

@Composable
private fun DislikePill(disliked: Boolean, dislikes: Int, onClick: () -> Unit) {
    val color by animateColorAsState(
        targetValue = if (disliked) Text.Secondary else Text.Muted,
        label = "commentDislikeColor"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
            contentDescription = if (disliked) tr("取消踩") else tr("踩"),
            tint = color,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = if (dislikes > 0) "$dislikes" else tr("踩"),
            color = color,
            fontSize = 11.sp,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

@Composable
private fun CommentAvatar(text: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Brand.TerracottaLight, Brand.Terracotta))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text.take(1),
            color = Color.White,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DeleteConfirmDialog(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg.Surface,
        title = { Text(tr("删除这条内容？"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = { Text(text, color = Text.Secondary, fontSize = 13.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(tr("删除"), color = Func.Danger, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("取消"), color = Text.Secondary, fontSize = 14.sp)
            }
        }
    )
}

/** 底部评论输入条（固定在页面底部，点击底部「说点什么…」会自动聚焦） */
@Composable
internal fun GuideCommentInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    replyTo: String?,
    focusSignal: Int,
    onSubmit: () -> Unit,
    onCancelReply: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(focusSignal) {
        if (focusSignal > 0) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg.Surface)
            // 不加 navigationBarsPadding 的话发送按钮会被系统导航条压住（三键导航机型点不到）；
            // 两者顺序不能反，先消费导航栏再消费 IME，避免底部多出一截空白
            .navigationBarsPadding()
            .imePadding()
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Bg.Outline))
        if (replyTo != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (Lang.isEn) "Replying to @$replyTo" else "回复 @$replyTo", color = Brand.Terracotta, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(
                    tr("取消"),
                    color = Text.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .clickable(onClick = onCancelReply)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = draft,
                // 200 字上限：再长会撑爆单行输入框，也没必要
                onValueChange = { if (it.length <= 200) onDraftChange(it) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(color = Text.Primary, fontSize = 14.sp),
                cursorBrush = SolidColor(Brand.Terracotta),
                decorationBox = { inner ->
                    // 自绘输入框：固定最小高度并垂直居中，避免长占位文字被裁切
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50.dp))
                            .background(Bg.SurfaceVariant)
                            .heightIn(min = 40.dp)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (draft.isEmpty()) {
                                Text(
                                    text = when {
                                        replyTo == null -> tr("说点什么…")
                                        Lang.isEn -> "Reply to @$replyTo: "
                                        else -> tr("回复 @$replyTo：", "Reply to @$replyTo: ")
                                    },
                                    color = Text.Muted,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                    }
                }
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (draft.isNotBlank()) Brand.Terracotta else Bg.SurfaceVariant)
                    .clickable(enabled = draft.isNotBlank(), onClick = onSubmit),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Send,
                    contentDescription = tr("发送评论"),
                    tint = if (draft.isNotBlank()) Color.White else Text.Muted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
