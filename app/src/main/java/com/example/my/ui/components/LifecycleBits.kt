package com.example.my.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * 页面是否处于 RESUMED。
 *
 * 常驻页面的秒表 / 定时刷新只能在前台跑：App 退到后台后，Composition 还在，
 * 但用户看不到页面，继续每秒重组只会无意义地唤醒 CPU。把循环的 key 接到这个值，
 * 后台自动取消，回到前台再恢复。
 */
@Composable
internal fun rememberIsResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> resumed = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}

/**
 * 屏幕是否亮着（[PowerManager.isInteractive]）。
 *
 * 熄屏时部分机型不会立刻回调 ON_PAUSE（音乐继续播、Activity 仍是 RESUMED），
 * 只看 [rememberIsResumed] 的话动画还在按帧跑，白白耗电。动画循环把亮屏判断也接上，
 * 熄屏即停帧，亮屏自动接着动。
 */
@Composable
internal fun rememberIsScreenOn(): Boolean {
    val context = LocalContext.current
    var on by remember(context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        mutableStateOf(pm?.isInteractive ?: true)
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> on = true
                    Intent.ACTION_SCREEN_OFF -> on = false
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return on
}
