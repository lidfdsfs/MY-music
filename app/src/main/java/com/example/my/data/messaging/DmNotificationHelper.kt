package com.example.my.data.messaging

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.my.MainActivity
import com.example.my.R

/**
 * 私信通知。目标是对齐微信的核心体验：
 * - 后台收到消息：高优先级横幅/锁屏通知，带声音震动
 * - 通知栏直接回复（RemoteInput），不用先打开 App
 * - 同一会话合并成一条，多个会话汇总成一组
 * - 前台不弹系统通知，改由 App 内顶部横幅提示
 */
object DmNotificationHelper {

    const val CHANNEL_MESSAGES = "dm_messages_v3"
    const val CHANNEL_SERVICE = "dm_service_v3"
    const val CHANNEL_FOLLOW = "dm_follow_v3"

    const val EXTRA_DM_PEER_ID = "dm_peer_id"
    const val EXTRA_DM_CONVERSATION_ID = "dm_conversation_id"
    const val ACTION_DM_REPLY = "com.example.my.action.DM_REPLY"
    const val KEY_DM_REPLY_TEXT = "dm_reply_text"

    const val SERVICE_NOTIFICATION_ID = 8_810_001
    private const val SUMMARY_NOTIFICATION_ID = 8_810_002
    private const val BASE_MESSAGE_ID = 8_820_000
    private const val GROUP_DM = "com.example.my.DM_GROUP"

    private fun manager(context: Context): NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    fun notificationId(conversationId: Int): Int = BASE_MESSAGE_ID + conversationId

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = manager(context) ?: return

        val message = NotificationChannel(
            CHANNEL_MESSAGES,
            "私信消息",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "用户之间发送的私信；支持通知栏直接回复"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 180, 120, 180)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            "私信接收服务",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "保持私信实时接收；常驻通知可收起，不影响收发"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        val follow = NotificationChannel(
            CHANNEL_FOLLOW,
            "关注与互动",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "谁关注了你、回关提醒"
            setShowBadge(true)
        }

        nm.createNotificationChannel(message)
        nm.createNotificationChannel(service)
        nm.createNotificationChannel(follow)
    }

    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** 系统总开关 + “私信消息”渠道都没关，才认为能真正提醒 */
    fun areMessageNotificationsEnabled(context: Context): Boolean {
        if (!areNotificationsEnabled(context)) return false
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = manager(context)?.getNotificationChannel(CHANNEL_MESSAGES)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }
        return true
    }

    /** 跳到系统通知设置，用户关了权限时给出唯一可行路径 */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun messagesIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_MESSAGES, true)
        }
        return PendingIntent.getActivity(
            context,
            8_830_001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun conversationIntent(context: Context, peerId: Int, convId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_DM_PEER_ID, peerId)
            putExtra(EXTRA_DM_CONVERSATION_ID, convId)
        }
        return PendingIntent.getActivity(
            context,
            8_830_000 + convId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun replyIntent(context: Context, convId: Int): PendingIntent {
        val intent = Intent(context, DmReplyReceiver::class.java).apply {
            action = ACTION_DM_REPLY
            putExtra(EXTRA_DM_CONVERSATION_ID, convId)
        }
        // RemoteInput 要由系统往里填文字，Android 12+ 必须是 MUTABLE；低版本没有这个 flag
        val replyFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(
            context,
            8_840_000 + convId,
            intent,
            replyFlags
        )
    }

    /**
     * 显示一条私信通知。前台且正开着同一会话时返回 false，由调用方决定是否走横幅。
     */
    fun showMessage(
        context: Context,
        conversationId: Int,
        peerId: Int,
        peerName: String,
        body: String,
        totalUnread: Int
    ): Boolean {
        if (!areMessageNotificationsEnabled(context)) return false
        ensureChannels(context)

        val safeName = peerName.ifBlank { "旅行者" }
        val safeBody = body.ifBlank { "你收到一条新私信" }
        val publicText = "你收到一条新私信"

        val reply = RemoteInput.Builder(KEY_DM_REPLY_TEXT)
            .setLabel("回复 $safeName")
            .build()
        val action = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "回复",
            replyIntent(context, conversationId)
        ).addRemoteInput(reply)
            .setAllowGeneratedReplies(true)
            .build()

        val contentIntent = if (peerId > 0) {
            conversationIntent(context, peerId, conversationId)
        } else {
            messagesIntent(context)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setLargeIcon(
                BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
            )
            .setContentTitle(safeName)
            .setContentText(safeBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(safeBody))
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setNumber(totalUnread.coerceAtLeast(1))
            .setGroup(GROUP_DM)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle("MY足迹")
                    .setContentText(publicText)
                    .setContentIntent(messagesIntent(context))
                    .setAutoCancel(true)
                    .build()
            )
            .addAction(action)

        if (Build.VERSION.SDK_INT < 26) {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(longArrayOf(0, 180, 120, 180))
        }

        val notification = builder.build()
        DmRuntime.addActiveNotification(conversationId)
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(notificationId(conversationId), notification)
            showSummary(context, totalUnread)
        }
        return true
    }

    /** 同一会话合并成一条；这里只更新汇总，避免每个会话都响一次 */
    private fun showSummary(context: Context, totalUnread: Int) {
        if (!areNotificationsEnabled(context)) return
        val summary = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("MY足迹")
            .setContentText("你有 ${totalUnread.coerceAtLeast(1)} 条未读私信")
            .setNumber(totalUnread.coerceAtLeast(1))
            .setGroup(GROUP_DM)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(messagesIntent(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, summary) }
    }

    fun cancelConversation(context: Context, conversationId: Int) {
        val nm = NotificationManagerCompat.from(context)
        runCatching { nm.cancel(notificationId(conversationId)) }
        val remaining = DmRuntime.removeActiveNotification(conversationId)
        if (remaining <= 0) {
            runCatching { nm.cancel(SUMMARY_NOTIFICATION_ID) }
        } else {
            showSummary(context, DmRuntime.totalUnread)
        }
    }

    fun cancelAll(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        for (id in DmRuntime.activeNotificationIds()) {
            runCatching { nm.cancel(notificationId(id)) }
        }
        DmRuntime.clearActiveNotifications()
        runCatching { nm.cancel(SUMMARY_NOTIFICATION_ID) }
    }

    /** 常驻服务通知；只用于满足 Android 前台服务要求，文案保持静态低调，不呈现“正在接收”的加载感 */
    fun buildServiceNotification(context: Context, unread: Int): Notification {
        ensureChannels(context)
        val text = if (unread > 0) "$unread 条未读私信" else "消息服务已在后台保持连接"
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("MY足迹 · 消息服务")
            .setContentText(text)
            .setContentIntent(messagesIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun showService(context: Context, unread: Int) {
        ensureChannels(context)
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(SERVICE_NOTIFICATION_ID, buildServiceNotification(context, unread))
        }
    }

    /** 兜底 Worker 用：没有拿到具体消息时，只提示“有几条未读” */
    fun showUnreadSummary(context: Context, totalUnread: Int) {
        if (!areMessageNotificationsEnabled(context) || totalUnread <= 0) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("MY足迹")
            .setContentText("你有 $totalUnread 条未读私信")
            .setNumber(totalUnread)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(messagesIntent(context))
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, notification)
        }
    }

    fun defaultSoundUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
}
