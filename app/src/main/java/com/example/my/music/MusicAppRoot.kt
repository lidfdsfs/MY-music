package com.example.my.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.my.data.music.MusicPlayer
import com.example.my.state.AppState
import com.example.my.state.ExtraScreen
import com.example.my.ui.components.ToastHost
import com.example.my.ui.screens.DrivingScreen
import com.example.my.ui.screens.SplashScreen
import com.example.my.ui.interaction.SystemAwareHapticFeedback
import com.example.my.ui.music.MusicScreen
import com.example.my.ui.theme.AppBackground
import com.example.my.ui.theme.AppBackgroundLayer
import com.example.my.ui.theme.AppPalette
import com.example.my.ui.theme.MY足迹Theme

/**
 * 独立音乐 App 的根界面：只有一个音乐模块，不组合地图 / 足迹 / 资讯等其它页面。
 * 复用同一套 AppState、主题与播放器单例，功能与主 App 内的音乐完全一致。
 */
@Composable
fun MusicAppRoot(onExit: () -> Unit) {
    val context = LocalContext.current
    val state = remember { AppState(context) }
    // 触感反馈跟随系统开关，与主 App 行为一致
    val baseHaptic = LocalHapticFeedback.current
    val haptic = remember(baseHaptic, context) {
        SystemAwareHapticFeedback(baseHaptic, context.applicationContext)
    }
    // 退到后台时记住当前歌曲与播放进度，再次打开接着听
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) MusicPlayer.savePlaybackState()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    CompositionLocalProvider(LocalHapticFeedback provides haptic) {
        MY足迹Theme {
            // 音乐页内部的返回层级都没消费时，直接退出独立音乐 App
            BackHandler { onExit() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (AppBackground.config.enabled) Color.Transparent
                        else AppPalette.value.background
                    )
            ) {
                // 自定义背景在最底层，音乐页启用时以透明底透出背景图
                if (AppBackground.config.enabled) AppBackgroundLayer()
                MusicScreen(state = state, standalone = true, onExit = onExit)
                // 驾驶模式是音乐的一部分：独立版同样保留车机播放页
                if (state.screen == ExtraScreen.DRIVING) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(AppPalette.value.background)
                    ) {
                        DrivingScreen(state)
                    }
                }
                // 主 App 里提示条挂在根节点，独立音乐 App 也要自己挂一份
                ToastHost(state)

                // 启动页：本进程第一次进入界面时盖在最上层，1.6 秒后淡出
                AnimatedVisibility(
                    visible = state.showSplash,
                    exit = fadeOut(tween(400))
                ) {
                    SplashScreen(onFinish = { state.showSplash = false })
                }
            }
        }
    }
}
