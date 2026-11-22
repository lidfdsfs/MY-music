package com.example.my.data.messaging

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.my.data.BackendApi
import com.example.my.data.CloudConfig
import com.example.my.data.Persistence
import com.example.my.data.crypto.SuperEncryption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 私信实时接收服务。
 *
 * 为什么需要前台服务：Android 没有 iOS 那种系统级统一推送；要让 App 被杀到后台
 * 还能收到私信，只能自己维持一条 SSE 长连接。前台服务会有一条低优先级常驻通知，
 * 这是系统强制的，不是 App 多弹的广告。
 *
 * 连接策略：
 * - 启动/重启后用 since_id 续传，断线按 3s → 30s 退避重连
 * - 后端每 300 秒主动结束一次 SSE，客户端无感重连
 * - 收到消息先落 last_id，再发通知，避免重连重复提醒
 * - 收到 401 交给 AppState 的全局登出逻辑
 */
class DmMessageService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private var loop: Job? = null

    @Volatile
    private var streamConn: HttpURLConnection? = null

    override fun onCreate() {
        super.onCreate()
        DmRuntime.serviceRunning = true
        CloudConfig.init(applicationContext)
        DmNotificationHelper.ensureChannels(applicationContext)
        ensureForeground(DmRuntime.totalUnread)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground(DmRuntime.totalUnread)
        if (loop?.isActive != true) {
            loop = scope.launch { runLoop() }
        }
        return START_STICKY
    }

    private fun ensureForeground(unread: Int) {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(
                this,
                DmNotificationHelper.SERVICE_NOTIFICATION_ID,
                DmNotificationHelper.buildServiceNotification(this, unread),
                type
            )
        }
    }

    private suspend fun runLoop() {
        CloudConfig.init(applicationContext)
        if (!CloudConfig.ENABLED) {
            stopSelf()
            return
        }
        // 进程可能由 BOOT_COMPLETED / 通知回复单独拉起，token 还没进内存，先从本地恢复
        if (!BackendApi.loggedIn) {
            val saved = runCatching { Persistence.loadAll(applicationContext).backendToken }.getOrNull()
            if (!saved.isNullOrBlank()) {
                runCatching { BackendApi.restoreToken(saved) }
            }
        }
        if (!BackendApi.loggedIn) {
            stopSelf()
            return
        }

        DmRuntime.totalUnread = BackendApi.fetchUnreadTotal() ?: DmRuntime.totalUnread
        DmNotificationHelper.showService(this, DmRuntime.totalUnread)

        if (!ensureBaseline()) {
            // 第一次拿不到会话列表就先不连，避免 since_id=0 把历史消息全部弹一遍
            delay(5_000)
            if (!ensureBaseline()) {
                stopSelf()
                return
            }
        }

        var retry = 3_000L
        while (scope.isActive) {
            if (!CloudConfig.ENABLED || !BackendApi.loggedIn) {
                delay(5_000)
                continue
            }
            val since = prefs().getLong(KEY_LAST_ID, 0L)
            val conn = openStream(since)
            if (conn == null) {
                delay(retry)
                retry = (retry * 2).coerceAtMost(30_000L)
                continue
            }
            streamConn = conn
            try {
                val code = conn.responseCode
                if (code == 401) {
                    BackendApi.onUnauthorized?.invoke()
                    stopSelf()
                    return
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                retry = 3_000L

                val reader = conn.inputStream.bufferedReader(Charsets.UTF_8)
                var event = ""
                var data = ""
                while (scope.isActive) {
                    val line = reader.readLine() ?: break
                    when {
                        line.isEmpty() -> {
                            if (event == "message" && data.isNotEmpty()) {
                                handleSseData(data)
                            }
                            event = ""
                            data = ""
                        }
                        line.startsWith(":") -> Unit // 心跳注释
                        line.startsWith("event:") -> event = line.substringAfter("event:").trim()
                        line.startsWith("data:") -> data = line.substringAfter("data:").trim()
                    }
                }
            } catch (_: Throwable) {
                // 网络切换、服务端 300 秒主动断开、读超时都会走这里，统一退避重连
            } finally {
                if (streamConn === conn) streamConn = null
                runCatching { conn.disconnect() }
            }
            delay(retry)
            retry = (retry * 2).coerceAtMost(30_000L)
        }
    }

    private fun openStream(since: Long): HttpURLConnection? = runCatching {
        (URL(BackendApi.messageStreamUrl(since)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 45_000
            requestMethod = "GET"
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Cache-Control", "no-cache")
            BackendApi.token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
    }.getOrNull()

    private suspend fun ensureBaseline(): Boolean {
        val uid = BackendApi.remoteUserId ?: 0
        val savedUid = prefs().getInt(KEY_USER_ID, 0)
        if (savedUid != uid) {
            // 换账号后旧游标可能比新账号的消息 id 更大，必须清掉重新对齐
            prefs().edit().putInt(KEY_USER_ID, uid).remove(KEY_LAST_ID).apply()
        }
        if (prefs().getLong(KEY_LAST_ID, 0L) > 0L) return true
        val list = BackendApi.fetchConversations() ?: return false
        list.forEach { DmRuntime.putConversation(it) }
        val max = list.maxOfOrNull { it.lastMessageId } ?: 0
        prefs().edit().putLong(KEY_LAST_ID, max.toLong()).apply()
        return true
    }

    private suspend fun handleSseData(raw: String) {
        val event = BackendApi.parseDmStreamEvent(raw) ?: return
        // 先推进游标，再决定要不要提醒；即使这条是自己发的，也不能重连后重复拉取
        if (event.message.id > 0) {
            val old = prefs().getLong(KEY_LAST_ID, 0L)
            if (event.message.id.toLong() > old) {
                prefs().edit().putLong(KEY_LAST_ID, event.message.id.toLong()).apply()
            }
        }
        if (event.message.mine) return

        val conv = DmRuntime.conversation(event.conversationId)
            ?: BackendApi.fetchThread(event.conversationId, 1)?.conversation
        if (conv != null) DmRuntime.putConversation(conv)

        val peerId = conv?.peer?.id ?: 0
        val peerName = conv?.peer?.nickname ?: "新私信"
        // 本地通知也尽量显示解密后的原文；通知栏不是“密文展示区”，不能出现 🔒。
        val shown = if (peerId > 0 &&
            (event.message.e2eeEnvelope != null || event.message.body == SuperEncryption.LOCK_BODY)
        ) {
            SuperEncryption.decorateOne(this, event.conversationId, peerId, event.message)
        } else {
            event.message
        }
        val body = when {
            shown.recalled -> "对方撤回了一条消息"
            !shown.displayBody.isNullOrBlank() && shown.displayBody != SuperEncryption.DECRYPT_FAILED ->
                shown.displayBody!!
            shown.body.isBlank() || shown.body == SuperEncryption.LOCK_BODY -> "你收到一条新私信"
            else -> shown.body
        }

        // 正停在这个会话里且 App 可见：聊天页自己会轮询，不弹横幅/通知，也不再加未读
        val readingThisChat = AppForegroundState.isForeground &&
            event.conversationId == AppForegroundState.openConversationId
        if (readingThisChat) {
            DmNotificationHelper.showService(this, DmRuntime.totalUnread)
            return
        }

        DmRuntime.totalUnread = (DmRuntime.totalUnread + 1).coerceAtLeast(
            conv?.unread ?: 1
        )

        DmEvents.emit(
            DmIncoming(
                conversationId = event.conversationId,
                peerId = peerId,
                peerName = peerName,
                body = body,
                createdAt = event.message.createdAt,
                totalUnread = DmRuntime.totalUnread
            )
        )

        // 前台交给 App 内横幅，后台才走系统通知；正在聊天且 App 可见时 UI 自己会拉消息
        if (!AppForegroundState.isForeground) {
            DmNotificationHelper.showMessage(
                context = this,
                conversationId = event.conversationId,
                peerId = peerId,
                peerName = peerName,
                body = body,
                totalUnread = DmRuntime.totalUnread
            )
        }
        DmNotificationHelper.showService(this, DmRuntime.totalUnread)
    }

    private fun prefs() =
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun onDestroy() {
        DmRuntime.serviceRunning = false
        runCatching { streamConn?.disconnect() }
        loop?.cancel()
        job.cancel()
        runCatching { ServiceCompat.stopForeground(this, Service.STOP_FOREGROUND_REMOVE) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val PREFS = "dm_message_service"
        private const val KEY_LAST_ID = "last_message_id"
        private const val KEY_USER_ID = "last_user_id"

        fun start(context: Context) {
            CloudConfig.init(context)
            if (!CloudConfig.ENABLED) return
            runCatching { DmFallbackWorker.schedule(context) }
            val intent = Intent(context, DmMessageService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, DmMessageService::class.java)) }
            DmNotificationHelper.cancelAll(context)
            DmRuntime.totalUnread = 0
            DmRuntime.serviceRunning = false
            runCatching { DmFallbackWorker.cancel(context) }
        }

        fun resetCursor(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LAST_ID)
                .apply()
        }
    }
}
