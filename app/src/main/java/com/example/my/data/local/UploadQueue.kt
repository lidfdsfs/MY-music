package com.example.my.data.local

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.my.data.BackendApi
import com.example.my.data.Persistence
import com.example.my.util.SplashGate
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 待传队列 Worker：联网后把本地上传的足迹/攻略补传到云端。
 * inputData：type("route"/"guide") + payload（对应模型的 json 字符串）。
 * 失败重试至多 5 次（runAttemptCount 从 0 起），超过则丢弃，避免死循环。
 */
class PendingUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // 补传任务可能在后台单独拉起进程，之后用户再进 App 属于热启动
        SplashGate.onBackgroundUse()
        val type = inputData.getString(KEY_TYPE) ?: return Result.success()
        val payload = inputData.getString(KEY_PAYLOAD) ?: return Result.success()

        // 进程被系统重建后内存 token 丢失：先从 DataStore 恢复登录态
        if (BackendApi.token == null) {
            val saved = Persistence.loadAll(applicationContext).backendToken
            if (saved.isNullOrBlank()) return Result.success() // 未登录，任务无意义直接丢弃
            val ok = runCatching { BackendApi.restoreToken(saved) }.getOrDefault(false)
            if (!ok) return retryOrDrop()
        }

        val uploaded = runCatching {
            when (type) {
                TYPE_ROUTE -> BackendApi.createRoute(routeFromJson(JSONObject(payload))) != null
                TYPE_GUIDE -> BackendApi.createGuide(guideFromJson(JSONObject(payload))) != null
                else -> true // 未知类型不再重试
            }
        }.getOrDefault(false)

        return if (uploaded) Result.success() else retryOrDrop()
    }

    private fun retryOrDrop(): Result =
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()

    companion object {
        const val KEY_TYPE = "type"
        const val KEY_PAYLOAD = "payload"
        const val TYPE_ROUTE = "route"
        const val TYPE_GUIDE = "guide"
        private const val MAX_ATTEMPTS = 5
    }
}

/** 待传队列门面：网络可用时才调度，指数退避 30s 起 */
object UploadQueue {

    fun enqueue(context: Context, type: String, payload: String) {
        val request = OneTimeWorkRequestBuilder<PendingUploadWorker>()
            .setInputData(
                Data.Builder()
                    .putString(PendingUploadWorker.KEY_TYPE, type)
                    .putString(PendingUploadWorker.KEY_PAYLOAD, payload)
                    .build()
            )
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }
}
