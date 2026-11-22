package com.example.my.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.BackendApi
import com.example.my.data.FollowState
import com.example.my.data.UserProfileInfo
import com.example.my.state.AppState
import com.example.my.ui.components.ErrorState
import com.example.my.ui.components.LoadingState as LoadingStateView
import com.example.my.ui.components.rememberUrlBitmap
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Func
import com.example.my.ui.theme.Text as TextColor
import com.example.my.ui.theme.TypeScale
import com.example.my.ui.theme.cardShadow

/**
 * 他人主页：从攻略作者、评论头像、搜索账号、粉丝/关注列表点进来。
 * 只读展示对方资料与关注关系；关注按钮走后端 /users/{id}/follow。
 */
@Composable
fun UserProfileScreen(state: AppState) {
    val userId = state.viewedUserId
    var info by remember(userId) { mutableStateOf<UserProfileInfo?>(null) }
    var loading by remember(userId) { mutableStateOf(true) }
    var followBusy by remember(userId) { mutableStateOf(false) }

    LaunchedEffect(userId) {
        loading = true
        info = runCatching { BackendApi.fetchUserProfile(userId) }.getOrNull()
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Bg.Surface)
                    .clickable { state.closeUserProfile() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = tr("返回", "Back"),
                    tint = TextColor.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                tr("主页", "Profile"),
                color = TextColor.Primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        when {
            loading -> LoadingStateView(text = tr("加载主页…", "Loading profile…"))
            info == null -> ErrorState(
                title = tr("主页加载失败", "Could not load profile"),
                message = if (state.offlineMode) {
                    tr("离线模式无法访问服务器，登录并连接后再查看", "Sign in to view profiles")
                } else {
                    tr("请检查网络或该账号是否已停用", "Check your connection and try again")
                }
            )
            else -> {
                val data = info!!
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(Bg.Surface)
                            .cardShadow(RoundedCornerShape(26.dp))
                            .padding(horizontal = 18.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Brand.TerracottaLight, Brand.Terracotta)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = data.user.avatar?.let { rememberUrlBitmap(it) }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    data.user.nickname.take(1).ifBlank { "旅" },
                                    color = Color.White,
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            data.user.nickname.ifBlank { tr("旅行者", "Traveler") },
                            color = TextColor.Primary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (data.user.username.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "@" + data.user.username,
                                color = TextColor.Muted,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            data.user.signature.ifBlank { tr("这个人很懒，还没有留下签名", "No bio yet") },
                            color = TextColor.Secondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )

                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ProfileMetric(data.user.followers, tr("粉丝", "Followers"))
                            ProfileMetric(data.user.following, tr("关注", "Following"))
                            ProfileMetric(data.user.likesReceived, tr("获赞", "Likes"))
                            ProfileMetric(data.guideCount, tr("攻略", "Guides"))
                        }

                        Spacer(Modifier.height(22.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val following = state.followedUserIds.contains(userId) || data.following
                            val mutual = data.mutual ||
                                (following && data.followsMe)
                            Box(
                                modifier = Modifier
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(
                                        if (following) Bg.SurfaceVariant
                                        else Brand.Terracotta
                                    )
                                    .clickable(enabled = !followBusy) {
                                        if (followBusy) return@clickable
                                        if (state.offlineMode) {
                                            state.toast(tr("离线模式无法关注", "Offline"))
                                            return@clickable
                                        }
                                        followBusy = true
                                        state.toggleFollow(userId) { f: FollowState? ->
                                            followBusy = false
                                            if (f == null) {
                                                state.toast(tr("关注失败，请检查网络", "Follow failed"))
                                            } else {
                                                info = info?.let { old ->
                                                    old.copy(
                                                        following = f.following,
                                                        followsMe = f.followsMe || old.followsMe,
                                                        mutual = f.mutual,
                                                        user = old.user.copy(
                                                            followers = (old.user.followers + if (f.following) 1 else -1).coerceAtLeast(0)
                                                        )
                                                    )
                                                }
                                                state.toast(
                                                    if (f.following) tr("已关注", "Following")
                                                    else tr("已取消关注", "Unfollowed")
                                                )
                                            }
                                        }
                                    }
                                    .padding(horizontal = 26.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    when {
                                        mutual -> tr("互相关注", "Mutual")
                                        following -> tr("已关注", "Following")
                                        else -> tr("+ 关注", "+ Follow")
                                    },
                                    color = if (following) TextColor.Muted else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(Bg.SurfaceVariant)
                                    .clickable {
                                        if (state.offlineMode) {
                                            state.toast(tr("离线模式无法发消息", "Offline"))
                                        } else if (!mutual) {
                                            state.toast(
                                                tr("互相关注后才能聊天，先关注并等待对方回关", "Mutual follow required")
                                            )
                                        } else {
                                            state.closeUserProfile()
                                            state.requestOpenDmPeer(userId)
                                        }
                                    }
                                    .padding(horizontal = 26.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (mutual) tr("发消息", "Message") else tr("互关后可聊", "Mutual to chat"),
                                    color = TextColor.Primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = tr(
                            "仅展示公开资料。对方的足迹、行程和隐私空间不会出现在这里。",
                            "Only public profile information is shown."
                        ),
                        color = TextColor.Muted,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileMetric(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            color = TextColor.Primary,
            fontSize = TypeScale.subtitle,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(label, color = TextColor.Muted, fontSize = 11.sp)
    }
}
