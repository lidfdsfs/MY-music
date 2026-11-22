package com.example.my.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.messaging.AppForegroundState
import com.example.my.data.messaging.DmEvents
import com.example.my.data.messaging.DmIncoming
import com.example.my.state.AppState
import com.example.my.state.AuthState
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow
import kotlinx.coroutines.delay

/**
 * App 在前台时的“微信式”顶部横幅。
 * 系统通知只在后台发；前台避免通知栏和页面同时响，改为这条可点击的轻提示。
 */
@Composable
fun DmBannerHost(state: AppState, modifier: Modifier = Modifier) {
    var banner by remember { mutableStateOf<DmIncoming?>(null) }

    LaunchedEffect(Unit) {
        DmEvents.events.collect { event ->
            if (state.authState != AuthState.LOGGED_IN) return@collect
            if (!AppForegroundState.isForeground) return@collect
            if (event.conversationId == AppForegroundState.openConversationId) return@collect
            banner = event
        }
    }
    LaunchedEffect(banner) {
        val current = banner ?: return@LaunchedEffect
        delay(4_500)
        if (banner?.conversationId == current.conversationId &&
            banner?.body == current.body
        ) {
            banner = null
        }
    }

    AnimatedVisibility(
        visible = banner != null && state.authState == AuthState.LOGGED_IN,
        modifier = modifier,
        enter = slideInVertically(tween(220)) { -it } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(180)) { -it } + fadeOut(tween(140))
    ) {
        val item = banner ?: return@AnimatedVisibility
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Bg.Surface)
                .cardShadow(RoundedCornerShape(18.dp))
                .clickable {
                    banner = null
                    state.requestOpenDmPeer(item.peerId)
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Brand.Terracotta),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.peerName.take(1).ifBlank { "旅" },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.peerName,
                    color = Text.Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    item.body,
                    color = Text.Secondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = tr("回复"),
                tint = Brand.Terracotta,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.Close,
                contentDescription = tr("关闭"),
                tint = Text.Muted,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { banner = null }
            )
        }
    }
}
