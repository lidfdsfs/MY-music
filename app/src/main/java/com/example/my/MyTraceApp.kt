package com.example.my

import android.app.Application
import com.example.my.data.ServerSearchPrefs
import com.example.my.util.MediaCache
import com.example.my.util.SplashGate

/**
 * 进程入口。只做一件事：进程真正创建时打开启动页闸门。
 * 启动页是否显示由 SplashGate 判断，避免 Activity 重建 / 热启动重复显示。
 */
class MyTraceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 全局未捕获异常落盘（filesDir/crash.log），闪退后可在设置页查看/复制
        com.example.my.data.CrashLog.install(applicationContext)
        SplashGate.onProcessCreated()
        // 载入「服务器网页索引」开关到内存（WebSearch 检索入口拿不到 Context）
        ServerSearchPrefs.init(applicationContext)
        MediaCache.init(applicationContext)
        // 功能设置一改就防抖上云，换机登录可完整还原
        com.example.my.data.sync.SettingsAutoSync.start(applicationContext)
    }
}
