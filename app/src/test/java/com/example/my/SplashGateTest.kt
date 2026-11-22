package com.example.my

import com.example.my.util.SplashGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SplashGateTest {
    @Before
    fun reset() {
        SplashGate.resetForTest()
    }

    @Test
    fun coldProcessShowsSplashOnce() {
        SplashGate.onProcessCreated()
        assertTrue("进程刚创建时第一次进入界面应显示启动页", SplashGate.consumeLaunch())
        assertFalse("同一进程的第二次界面启动不应再显示启动页", SplashGate.consumeLaunch())
    }

    @Test
    fun warmProcessNeverShowsSplash() {
        // 进程没有重新创建（例如 Activity 重建 / 通知回到已有进程）
        assertFalse(SplashGate.consumeLaunch())
    }

    @Test
    fun backgroundComponentWakesProcessWithoutSplash() {
        SplashGate.onProcessCreated()
        // 小组件刷新 / 常驻服务 / WorkManager 把进程拉起来，用户并没有进入界面
        SplashGate.onBackgroundUse()
        assertFalse("进程已被后台组件拉起，再进界面属于热启动", SplashGate.consumeLaunch())
    }

    @Test
    fun processDeathThenRelaunchShowsAgain() {
        SplashGate.onProcessCreated()
        assertTrue(SplashGate.consumeLaunch())
        assertFalse(SplashGate.consumeLaunch())
        // 进程被杀后 Application 再次 onCreate，等于新一轮冷启动
        SplashGate.onProcessCreated()
        assertTrue(SplashGate.consumeLaunch())
    }
}
