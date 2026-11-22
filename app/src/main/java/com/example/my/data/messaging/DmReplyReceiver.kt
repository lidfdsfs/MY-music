package com.example.my.data.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.my.data.BackendApi
import com.example.my.data.CloudConfig
import com.example.my.data.Persistence
import com.example.my.data.crypto.SuperEncryption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 通知栏「直接回复」的接收器。
 * 微信式体验的关键：用户在锁屏/下拉通知里输入文字后，不打开 App 也能把消息发出去。
 */
class DmReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DmNotificationHelper.ACTION_DM_REPLY) return
        val convId = intent.getIntExtra(DmNotificationHelper.EXTRA_DM_CONVERSATION_ID, 0)
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(DmNotificationHelper.KEY_DM_REPLY_TEXT)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (convId <= 0 || text.isBlank()) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                CloudConfig.init(context)
                if (!BackendApi.loggedIn) {
                    val saved = runCatching {
                        Persistence.loadAll(context.applicationContext).backendToken
                    }.getOrNull()
                    if (!saved.isNullOrBlank()) {
                        runCatching { BackendApi.restoreToken(saved) }
                    }
                }
                if (!BackendApi.loggedIn) return@launch
                // 通知栏直接回复也必须遵守超级加密：拿不到对方公钥就放弃发送，绝不降级成明文
                var envelope: String? = null
                if (SuperEncryption.isEnabled(context)) {
                    val conv = BackendApi.fetchConversations()?.firstOrNull { it.id == convId }
                    val peerId = conv?.peer?.id ?: 0
                    if (peerId > 0) {
                        envelope = SuperEncryption.encrypt(context, convId, peerId, text)
                    }
                    if (envelope == null) return@launch
                }
                val result = BackendApi.sendDmMessage(
                    convId,
                    if (envelope != null) SuperEncryption.LOCK_BODY else text,
                    e2eeEnvelope = envelope
                )
                if (result.ok) {
                    DmNotificationHelper.cancelConversation(context, convId)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
