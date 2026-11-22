package com.example.my.data.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.my.MainActivity
import com.example.my.util.SplashGate

/**
 * 行程出发提醒 Worker。
 *
 * 触发时间由 AppState 按用户的「提前 N 天 + 时刻」设置计算，这里只负责：
 * 1) 按提醒方式挑选通知渠道（铃声+震动 / 仅铃声 / 仅震动 / 静默）；
 * 2) 弹出一条可点击回到 App 的通知。
 *
 * 为什么要按组合建多个渠道：Android 8+ 的铃声/震动由渠道属性决定，
 * 单渠道无法同时支持「只要震动」和「只要铃声」，通知级的 setSound 在 8+ 会被忽略。
 */
class PlanReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // 提醒任务可能在后台单独拉起进程，之后用户点通知进 App 属于热启动
        SplashGate.onBackgroundUse()
        val planId = inputData.getLong(KEY_PLAN_ID, -1L)
        val name = inputData.getString(KEY_NAME) ?: return Result.success()
        val destination = inputData.getString(KEY_DEST).orEmpty()
        val startDate = inputData.getString(KEY_DATE).orEmpty()
        val offsetDays = inputData.getInt(KEY_OFFSET_DAYS, 1)
        val vibrate = inputData.getBoolean(KEY_VIBRATE, true) && systemAllowsVibrate()
        val sound = inputData.getBoolean(KEY_SOUND, true)

        // 未授予通知权限则静默跳过（创建/编辑计划时会再次请求）
        if (Build.VERSION.SDK_INT >= 33 &&
            NotificationManagerCompat.from(applicationContext)
                .areNotificationsEnabled().not()
        ) {
            return Result.success()
        }

        ensureChannels(applicationContext)
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // requestCode 不能直接用 planId.toInt()：Long 高位会被截断，两个不同行程可能撞上同一个低 32 位
        // 这里把高 32 位做一次 hash 混进去，再跟低 32 位异或，冲突概率大幅降低
        val requestCode = (planId.toInt() xor (planId.ushr(32)).toInt()).let { if (it == 0) 1 else it }
        val pending = PendingIntent.getActivity(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val whenText = when (offsetDays) {
            0 -> "今天出发"
            1 -> "明天出发"
            else -> "$offsetDays 天后出发"
        }
        val title = "$whenText：$name"
        val text = buildString {
            append("目的地：").append(if (destination.isBlank()) "待定" else destination)
            if (startDate.isNotBlank()) append(" · 出发日期 $startDate")
            append("\n收拾好行李，准备出发！")
        }
        val notification = NotificationCompat.Builder(applicationContext, channelIdFor(vibrate, sound))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(
                if (sound || vibrate) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_LOW
            )
            .setContentIntent(pending)
            .apply {
                if (!sound && !vibrate) setSilent(true)
                if (vibrate) setVibrate(longArrayOf(0, 400, 200, 400))
            }
            .build()
        // 通知 id 同样避免碰撞：独立于 PendingIntent 的 requestCode，给它加一个高位 hash
        val notifyId = (planId.toInt() xor (planId.ushr(32)).toInt() xor 0xA5A5A5A5.toInt())
            .let { if (it == 0) 1 else it }
        runCatching { NotificationManagerCompat.from(applicationContext).notify(notifyId, notification) }
        return Result.success()
    }

    /**
     * 系统当前是否允许震动。
     *
     * 通知的震动在 Android 8+ 由渠道属性决定，系统静音时渠道仍会震，
     * 所以这里提前判定：静音 / 系统关闭触感时把 vibrate 降为 false，
     * 选到「仅铃声」或「静默」渠道，行为就跟系统一致了。
     */
    private fun systemAllowsVibrate(): Boolean {
        val ctx = applicationContext
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am != null && am.ringerMode == AudioManager.RINGER_MODE_SILENT) return false
        val haptic = runCatching {
            Settings.System.getInt(ctx.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1)
        }.getOrDefault(1)
        return haptic != 0
    }

    companion object {
        const val KEY_PLAN_ID = "plan_id"
        const val KEY_NAME = "plan_name"
        const val KEY_DEST = "plan_dest"
        const val KEY_DATE = "plan_date"
        const val KEY_TIME = "plan_time"
        const val KEY_OFFSET_DAYS = "plan_offset_days"
        const val KEY_VIBRATE = "plan_vibrate"
        const val KEY_SOUND = "plan_sound"

        const val CHANNEL_ID = "trip_reminder"
        private const val CHANNEL_SOUND = "trip_reminder_sound"
        private const val CHANNEL_VIBRATE = "trip_reminder_vibrate"
        private const val CHANNEL_SILENT = "trip_reminder_silent"

        private fun channelIdFor(vibrate: Boolean, sound: Boolean): String = when {
            sound && vibrate -> CHANNEL_ID
            sound -> CHANNEL_SOUND
            vibrate -> CHANNEL_VIBRATE
            else -> CHANNEL_SILENT
        }

        /** 创建四种提醒方式对应的渠道（重复创建无副作用） */
        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            fun create(id: String, name: String, vibrate: Boolean, sound: Boolean, importance: Int) {
                val channel = NotificationChannel(id, name, importance).apply {
                    description = "出行计划的出发提醒"
                    enableVibration(vibrate)
                    if (!sound) setSound(null, null)
                }
                nm.createNotificationChannel(channel)
            }
            create(CHANNEL_ID, "行程提醒（铃声+震动）", true, true, NotificationManager.IMPORTANCE_HIGH)
            create(CHANNEL_SOUND, "行程提醒（仅铃声）", false, true, NotificationManager.IMPORTANCE_HIGH)
            create(CHANNEL_VIBRATE, "行程提醒（仅震动）", true, false, NotificationManager.IMPORTANCE_HIGH)
            create(CHANNEL_SILENT, "行程提醒（静默）", false, false, NotificationManager.IMPORTANCE_LOW)
        }

        /** 兼容旧调用方 */
        fun ensureChannel(context: Context) = ensureChannels(context)
    }
}
