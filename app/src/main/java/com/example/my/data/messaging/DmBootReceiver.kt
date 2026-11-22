package com.example.my.data.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.my.data.CloudConfig

/**
 * 开机后恢复私信服务。
 * 只负责拉起；token 与登录态由 Service 自己从本地恢复。
 * 用户主动退出登录后，Service 会 stopSelf，不会继续跑。
 */
class DmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        if (!CloudConfig.isEnabled(context)) return
        DmMessageService.start(context)
    }
}
