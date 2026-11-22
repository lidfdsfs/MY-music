package com.example.my.ui.interaction

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * 触感反馈（轻震）统一入口。
 *
 * 行为约定：**默认跟随系统**。手机开了静音 / 关掉系统「触感反馈」时，
 * App 内所有轻震都静默 —— 之前在静音模式下点按钮仍然震，用户会觉得"关不掉"。
 *
 * 三个档位：
 * - [FOLLOW] 跟随系统（默认）：系统允许才震；
 * - [ON]     总是开启：只要求设备有振动马达；
 * - [OFF]    关闭：App 内不再有任何触感反馈。
 *
 * 注意：行程提醒的**通知震动**不归这里管（那是通知渠道属性），
 * 见 [com.example.my.data.local.PlanReminderWorker]，那边同样按系统静音判定。
 */
object Haptics {

    const val FOLLOW = "follow"
    const val ON = "on"
    const val OFF = "off"

    /** 用户选择，持久化在 AppState（KEY_HAPTIC_MODE） */
    var mode by mutableStateOf(FOLLOW)
        private set

    fun apply(value: String?) {
        mode = when (value) {
            ON -> ON
            OFF -> OFF
            else -> FOLLOW
        }
    }

    /** 系统当前是否允许震动：静音模式、系统触感反馈开关、系统振动开关任一关闭即为否 */
    fun systemAllows(context: Context): Boolean {
        if (!hasVibrator(context)) return false

        // 1) 铃声模式为「静音」时整机不震（振动模式仍允许）
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am != null && am.ringerMode == AudioManager.RINGER_MODE_SILENT) return false

        // 2) 系统的「触感反馈 / 触摸时振动」总开关
        val hapticEnabled = runCatching {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1
            )
        }.getOrDefault(1)
        if (hapticEnabled == 0) return false

        // 3) 旧版本系统里独立的「振动」开关（部分 ROM 仍有，读不到就当开启）
        val vibrateOn = runCatching {
            @Suppress("DEPRECATION")
            Settings.System.getInt(context.contentResolver, Settings.System.VIBRATE_ON, 1)
        }.getOrDefault(1)
        if (vibrateOn == 0) return false

        return true
    }

    /** 业务侧统一问这一句：现在该不该震 */
    fun allowed(context: Context): Boolean = when (mode) {
        OFF -> false
        ON -> hasVibrator(context)
        else -> systemAllows(context)
    }

    fun hasVibrator(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator?.hasVibrator() == true
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            v?.hasVibrator() == true
        }
    }.getOrDefault(false)
}

/**
 * 给 Compose 的 [HapticFeedback] 套一层系统判定。
 *
 * 在 `setContent` 根部用 `CompositionLocalProvider(LocalHapticFeedback provides ...)` 注入，
 * 全 App 的 `performHapticFeedback()`（包括 Material 组件内部的按下反馈）都会走这里，
 * 不需要在每个调用点各写一次判断。
 */
class SystemAwareHapticFeedback(
    private val base: HapticFeedback,
    private val context: Context
) : HapticFeedback {

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (!Haptics.allowed(context)) return
        base.performHapticFeedback(hapticFeedbackType)
    }
}
