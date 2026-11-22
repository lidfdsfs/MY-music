package com.example.my.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.my.util.ScreenRotation

/**
 * 系统栏沉浸的「可见性生命周期」唯一真源：放进条件分支里，进入组合即隐藏
 * 状态栏 + 导航栏（含底部手势条），离开组合立刻全部还原。
 *
 * 只管系统栏，**不碰**屏幕方向请求和常亮：
 * · 横屏音乐页的方向由 ScreenRotation 那一整套链路（自动旋转观察者、
 *   退出回竖屏）负责，两边都抢方向请求会互相打架；
 * · 驾驶模式的强制横屏 / 常亮仍由 DrivingScreen 自己的 DrivingImmersiveEffect 负责。
 *
 * 关键：Light 主题的窗口底是白的。只 hide 系统栏却不压黑窗口底，
 * 顶部那一块会透出白色（就是截图里那条白条）；这里把窗口底、
 * 系统栏颜色与图标明暗一并接管，并在 onDispose 逐项还原。
 */
@Composable
fun ImmersiveBarsEffect() {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(context, view) {
        val window = runCatching { ScreenRotation.findActivity(context)?.window }.getOrNull()
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldLightStatus = controller?.isAppearanceLightStatusBars
        val oldLightNav = controller?.isAppearanceLightNavigationBars
        val oldBehavior = runCatching { controller?.systemBarsBehavior }.getOrNull()
        val oldBackground = window?.decorView?.background

        runCatching {
            // 先压黑再隐藏：隐藏那一帧不能闪出白色窗口底
            window?.setBackgroundDrawable(ColorDrawable(AndroidColor.BLACK))
            window?.statusBarColor = AndroidColor.TRANSPARENT
            window?.navigationBarColor = AndroidColor.TRANSPARENT
            // 上滑临时调出系统栏时图标也要是浅色，否则黑底上是一排深灰看不清
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            runCatching {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                oldLightStatus?.let { controller?.isAppearanceLightStatusBars = it }
                oldLightNav?.let { controller?.isAppearanceLightNavigationBars = it }
                oldBehavior?.let { controller?.systemBarsBehavior = it }
                window?.setBackgroundDrawable(oldBackground)
            }
        }
    }
}
