package com.example.my.data.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.my.data.BackendApi
import com.example.my.data.CloudConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 设置项一改就尽快上云：登录到别的设备时能完整还原“我设置过什么功能”。
 *
 * 监听 app_settings / app_background，1.5 秒防抖后跑一次 [CloudSync.syncNow]。
 * 名称像 token / password / secret 的键不触发，避免把登录态或密钥卷进快照。
 */
object SettingsAutoSync {

    private const val DEBOUNCE_MS = 1_500L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    private var pending = false
    private val handler = Handler(Looper.getMainLooper())

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || isSensitive(key)) return@OnSharedPreferenceChangeListener
            schedule(app)
        }
        runCatching {
            app.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(listener)
            app.getSharedPreferences("app_background", Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(listener)
        }.onFailure { Log.w("SettingsAutoSync", "register failed", it) }
    }

    private fun schedule(app: Context) {
        if (pending) return
        pending = true
        handler.postDelayed({
            pending = false
            if (!CloudConfig.ENABLED || BackendApi.token == null) return@postDelayed
            scope.launch {
                runCatching { CloudSync.syncNow(app) }
                    .onFailure { Log.w("SettingsAutoSync", "sync settings failed", it) }
            }
        }, DEBOUNCE_MS)
    }

    private fun isSensitive(key: String): Boolean {
        val lower = key.lowercase()
        return lower.contains("token") || lower.contains("password") ||
            lower.contains("secret") || lower.contains("api_key")
    }
}
