package com.example.my.data.messaging

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.my.data.BackendApi
import com.example.my.data.CloudConfig
import com.example.my.data.Persistence
import java.util.concurrent.TimeUnit

/**
 * 15 分钟兜底轮询。
 *
 * 前台服务是实时链路；但部分 ROM 会杀后台服务，Worker 至少保证不会“彻底静默”。
 * 它只查未读总数，不重复弹具体消息，避免和服务通知打架。
 */
class DmFallbackWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        CloudConfig.init(applicationContext)
        if (!CloudConfig.ENABLED) return Result.success()
        // 服务还活着就由它负责实时提醒，Worker 不重复弹
        if (DmRuntime.serviceRunning) return Result.success()

        if (!BackendApi.loggedIn) {
            val saved = runCatching {
                Persistence.loadAll(applicationContext).backendToken
            }.getOrNull()
            if (!saved.isNullOrBlank()) {
                runCatching { BackendApi.restoreToken(saved) }
            }
        }
        if (!BackendApi.loggedIn) return Result.success()

        val total = BackendApi.fetchUnreadTotal() ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getInt(KEY_LAST_UNREAD, 0)
        if (total > last && total > 0) {
            DmNotificationHelper.showUnreadSummary(applicationContext, total)
        }
        prefs.edit().putInt(KEY_LAST_UNREAD, total).apply()
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "dm_fallback_unread"
        private const val PREFS = "dm_fallback_worker"
        private const val KEY_LAST_UNREAD = "last_unread"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DmFallbackWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}
