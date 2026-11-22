package com.example.my.data.messaging

import com.example.my.data.DmConversation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 一条刚到达的私信。service 只负责解析，UI 和 AppState 各自消费：
 * - 前台：顶部横幅
 * - 后台：系统通知
 * - 红点：AppState 累加未读
 */
data class DmIncoming(
    val conversationId: Int,
    val peerId: Int,
    val peerName: String,
    val body: String,
    val createdAt: String = "",
    val totalUnread: Int = 0
)

object DmEvents {
    private val _events = MutableSharedFlow<DmIncoming>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    fun emit(event: DmIncoming) {
        _events.tryEmit(event)
    }
}

/**
 * 全局前后台状态。Activity onStart/onStop 维护；聊天页维护当前打开的会话。
 * 前台时用横幅，后台时用系统通知，避免微信式“正在聊天还弹通知”。
 */
object AppForegroundState {
    @Volatile
    var isForeground: Boolean = false

    @Volatile
    var openConversationId: Int? = null
}

/**
 * 进程内私信缓存。service 与通知栏共用，避免每条消息都重新查会话资料。
 * 这里只放内存，不落盘；进程被杀后重新建立。
 */
object DmRuntime {
    @Volatile
    var totalUnread: Int = 0

    /** 前台服务是否活着；Worker 用它避免和服务重复提醒 */
    @Volatile
    var serviceRunning: Boolean = false

    private val conversations = mutableMapOf<Int, DmConversation>()
    private val activeNotifications = mutableSetOf<Int>()

    @Synchronized
    fun putConversation(conversation: DmConversation) {
        conversations[conversation.id] = conversation
    }

    @Synchronized
    fun conversation(id: Int): DmConversation? = conversations[id]

    @Synchronized
    fun addActiveNotification(id: Int) {
        activeNotifications += id
    }

    @Synchronized
    fun decrementUnread(count: Int) {
        totalUnread = (totalUnread - count.coerceAtLeast(0)).coerceAtLeast(0)
    }

    @Synchronized
    fun removeActiveNotification(id: Int): Int {
        activeNotifications -= id
        return activeNotifications.size
    }

    @Synchronized
    fun activeNotificationIds(): List<Int> = activeNotifications.toList()

    @Synchronized
    fun clearActiveNotifications() {
        activeNotifications.clear()
    }
}

/**
 * 冷启动时通知点击的落点。MainActivity 在 onCreate/onNewIntent 写入，
 * AppRoot 组合完成后消费；避免 AppState 还没创建就丢意图。
 */
object DmLaunch {
    @Volatile
    var peerId: Int? = null

    @Volatile
    var openMessages: Boolean = false

    fun take(): Int? {
        val v = peerId
        peerId = null
        return v
    }

    fun takeOpenMessages(): Boolean {
        val v = openMessages
        openMessages = false
        return v
    }
}
