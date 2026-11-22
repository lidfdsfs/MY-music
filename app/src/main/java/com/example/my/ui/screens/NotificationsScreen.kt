package com.example.my.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.model.AppNotification
import com.example.my.model.NotifType
import com.example.my.state.AppState
import com.example.my.ui.components.AppTopBar
import com.example.my.ui.components.BackActionRow
import com.example.my.ui.components.EmptyState
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.BorderLight
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.Spacing
import com.example.my.ui.theme.Terracotta
import com.example.my.ui.theme.TextMuted
import com.example.my.ui.theme.TextPrimary
import com.example.my.ui.theme.TextSecondary
import com.example.my.ui.theme.WarmBg
import com.example.my.ui.i18n.tr

private fun notifIcon(type: NotifType): ImageVector = when (type) {
    NotifType.LIKE -> Icons.Outlined.FavoriteBorder
    NotifType.COMMENT -> Icons.Outlined.Forum
    NotifType.FOLLOW -> Icons.Outlined.PersonAdd
    NotifType.SYSTEM -> Icons.Outlined.Campaign
}

private fun notifColor(type: NotifType): Color = when (type) {
    NotifType.LIKE -> Color(0xFFE07A5F)
    NotifType.COMMENT -> Color(0xFF2A9D8F)
    NotifType.FOLLOW -> Color(0xFF457B9D)
    NotifType.SYSTEM -> Color(0xFFE9C46A)
}

/** 通知中心：互动与系统消息，未读高亮 + 标记已读 */
@Composable
fun NotificationsScreen(state: AppState) {
    Column(modifier = Modifier.fillMaxSize().background(WarmBg)) {
        AppTopBar(title = tr("消息通知"))
        BackActionRow(
            onBack = { state.closeScreen() },
            actions = {
                if (state.notifications.isNotEmpty()) {
                    Text(
                        text = tr("全部已读"),
                        color = Terracotta,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable {
                                state.markAllNotificationsRead()
                                state.toast(tr("已全部标记为已读"))
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        )

        if (state.notifications.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Campaign,
                text = tr("暂无消息"),
                modifier = Modifier.fillMaxSize()
            )
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = Spacing.sm)
        ) {
            items(state.notifications, key = { it.id }) { n ->
                NotificationRow(n, onClick = { state.markNotificationRead(n.id) })
            }
            item {
                Text(
                    text = tr("仅展示最近消息 · 清空按钮在设置中"),
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(notifColor(n.type).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(notifIcon(n.type), null, tint = notifColor(n.type), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = n.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (!n.read) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (!n.read) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Terracotta)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(text = n.body, color = TextSecondary, fontSize = 13.sp, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(text = n.time, color = TextMuted, fontSize = 11.sp)
        }
    }
}
