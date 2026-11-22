package com.example.my.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.hardware.SensorManager
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface

/**
 * 屏幕方向判定：必须读系统「自动旋转」开关，不能靠猜。
 *
 * 之前的写法在播放页直接要 SENSOR，等于绕过了用户的竖屏锁定——系统里明明关掉了自动旋转，
 * 手机一转页面还是横过来了，用户会认为「检查旋转的功能坏了」。这里的规则是：
 *
 * - 自动旋转开着 → [ActivityInfo.SCREEN_ORIENTATION_USER]（跟重力走，正反都能转）
 * - 自动旋转关着（竖屏锁定）→ 按用户锁的方向来：锁竖屏就 PORTRAIT，锁横屏就 LANDSCAPE
 *
 * 读不到系统设置（个别 ROM 拒绝）时才按「允许旋转」处理，宁可转得动，
 * 也不要让用户的手机永远转不过去。
 */
object ScreenRotation {

    /** 系统「自动旋转」是否打开 */
    fun isAutoRotateOn(ctx: Context): Boolean = runCatching {
        Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
    }.getOrElse {
        // 个别 ROM 拒绝读这一项时按「允许旋转」处理：不能让用户的手机永远转不过去
        true
    }

    /** 自动旋转关着时，用户锁的是不是横屏（USER_ROTATION 1=90°, 3=270°）。仅用于排查展示 */
    fun isLockedLandscape(ctx: Context): Boolean = runCatching {
        when (Settings.System.getInt(ctx.contentResolver, Settings.System.USER_ROTATION, Surface.ROTATION_0)) {
            Surface.ROTATION_90, Surface.ROTATION_270 -> true
            else -> false
        }
    }.getOrDefault(false)

    /**
     * 播放页该申请的屏幕方向。
     * 自动旋转开着 → USER：完全交给系统里的用户设置，跟重力走、正反都能转；
     * 自动旋转关着（竖屏锁定）→ 竖屏：用户说了别转，这里就不许自己转。
     */
    fun playbackOrientation(ctx: Context): Int =
        if (isAutoRotateOn(ctx)) {
            ActivityInfo.SCREEN_ORIENTATION_USER
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

    /** 把播放页的方向请求落到 Activity 上；没有 Activity（预览/测试环境）就静默跳过 */
    fun applyToActivity(ctx: Context) {
        findActivity(ctx)?.let { act ->
            val want = playbackOrientation(ctx)
            if (act.requestedOrientation != want) {
                runCatching { act.requestedOrientation = want }
            }
        }
    }

    /** 系统「自动旋转」那一项设置的 URI，用来挂 ContentObserver 实时跟手 */
    fun autoRotateUri(): android.net.Uri =
        Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION)

    /** 从任意 Context 找到 Activity：改屏幕方向只能走 Activity */
    fun findActivity(ctx: Context): Activity? {
        var c: Context = ctx
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    /** 一次拿全：给 UI 层判断「现在到底是不是横屏」，以 Activity 真实方向为准，不看缓存 */
    fun isLandscapeNow(ctx: Context): Boolean = runCatching {
        val cfg = ctx.resources.configuration
        cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }.getOrDefault(false)

    /** 一次拿全：设备当前物理朝向是不是竖着拿（0° 或 180° 都算竖，反握也算） */
    class PortraitWatch internal constructor(
        private val listener: OrientationEventListener,
        val available: Boolean
    ) {
        fun stop() {
            runCatching { listener.disable() }
        }
    }

    /**
     * 监听「设备真的被竖起来了」这件事。
     *
     * 手动退出横屏时只把 Activity 锁成竖屏是不够的：手机还横在手里，方向一交还重力就立刻
     * 弹回横屏，用户会以为点了没反应。所以这里用重力传感器等用户把手机竖回去再解锁。
     * 传感器不可用（模拟器 / 个别平板）时 available=false，调用方退化成延时解锁。
     */
    fun watchPhysicalPortrait(ctx: Context, onPortrait: () -> Unit): PortraitWatch {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val listener = object : OrientationEventListener(ctx, SensorManager.SENSOR_DELAY_NORMAL) {
            /** 连续两次落在竖握区间才认，避免抖动误判 */
            private var hits = 0

            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) {
                    hits = 0
                    return
                }
                val vertical = orientation <= 30 || orientation >= 330 || orientation in 150..210
                if (vertical) {
                    hits++
                    if (hits >= 2) {
                        runCatching { disable() }
                        onPortrait()
                    }
                } else {
                    hits = 0
                }
            }
        }
        val ok = sm != null && runCatching { listener.enable() }.isSuccess
        if (!ok) runCatching { listener.disable() }
        return PortraitWatch(listener, ok)
    }
}
