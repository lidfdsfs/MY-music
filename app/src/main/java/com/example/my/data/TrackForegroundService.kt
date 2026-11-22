package com.example.my.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.SystemClock
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.my.MainActivity
import com.example.my.R
import com.example.my.util.SplashGate

/**
 * 轨迹记录的前台服务。
 *
 * 为什么必须有它：Android 10 起，App 不在前台时系统会限制后台定位，
 * 进程被回收后 `requestLocationUpdates` 直接停掉——「自动记录足迹」走到一半就断，
 * 用户第二天打开发现只记了前 200 米。
 *
 * 挂一个 location 类型的前台服务后，系统把 App 视为正在执行前台任务，
 * 锁屏 / 切到后台仍能持续收到 GPS 点；通知里同步显示已走了多远，用户随时能确认还在记录。
 *
 * 服务本身不持有 LocationManager：采点仍然是 [TrackService] 的职责，
 * 这里只负责「别让系统掐死进程」+ 展示状态。App 被划掉（force stop）时服务一起结束，
 * 下次启动由 TrackRecorder.isRecording 标记为「中断」交给 UI 处理。
 */
class TrackForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 系统按 START_STICKY 拉起服务时进程已经存活，不需要给用户补启动页
        SplashGate.onBackgroundUse()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: defaultText()
        // Android 12+ 前台服务启动窗口只有 5 秒，这里必须最先执行；失败就安静退出，不影响采点
        val ok = runCatching { startForeground(NOTIF_ID, buildNotification(this, text)) }.isSuccess
        if (!ok) {
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        // 进程被杀后系统按 START_STICKY 把服务拉起来：此时 AppState 已经不在了，
        // 由服务自己接着采点并直接落盘，用户回来时轨迹是连着的。
        if (!TrackService.isRunning && TrackRecorder.isRecording(this)) {
            TrackWriter.activate(this)
            val started = TrackService.start(this) { loc ->
                TrackWriter.append(this, loc) { msg -> update(this, msg) }
            }
            if (!started) TrackWriter.deactivate(this)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        // 只有「服务自己在记账」时才停采点；正常运行时采点归 AppState 管，不能在这里断掉
        if (TrackWriter.isActive) {
            TrackService.stop()
            TrackWriter.deactivate(this)
        }
        runCatching { stopForeground(true) }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "track_live"
        private const val NOTIF_ID = 1001
        private const val EXTRA_TEXT = "text"

        /**
         * 前台服务是否真的跑起来了。
         * 部分 ROM 会拒绝前台服务（后台启动限制 / 省电策略），
         * 这时再去 notify 会留下一条用户划不掉的常驻通知，必须先问这个。
         */
        @Volatile
        private var running = false

        fun isRunning(): Boolean = running

        /** 启动（幂等：重复调用只会刷新通知内容） */
        fun start(context: Context, text: String? = null) {
            val app = context.applicationContext
            val intent = Intent(app, TrackForegroundService::class.java)
                .putExtra(EXTRA_TEXT, text ?: defaultText())
            runCatching { ContextCompat.startForegroundService(app, intent) }
        }

        /** 只更新通知里的进度文字（采点时每累计几个点调一次，别每个点都刷） */
        fun update(context: Context, text: String) {
            val app = context.applicationContext
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            // 服务没在跑时 notify 一个不存在的前台通知没有意义，交给 start
            runCatching { nm.notify(NOTIF_ID, buildNotification(app, text)) }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            runCatching { app.stopService(Intent(app, TrackForegroundService::class.java)) }
            // 有些 ROM 上 startForeground 失败过，通知会以普通通知留在栏里，这里兜底清掉
            runCatching {
                (app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                    ?.cancel(NOTIF_ID)
            }
        }

        /** 通知栏文案。App 处于前台时用户正看着界面，这里只做「状态确认」用途 */
        fun defaultText(): String = "正在记录足迹…"

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "轨迹记录",
                NotificationManager.IMPORTANCE_LOW // 常驻但不出声，避免打断导航/音乐
            ).apply {
                description = "自动记录足迹时显示记录状态"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }

        private fun buildNotification(context: Context, text: String) =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_track)
                .setContentTitle("MY足迹")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()
    }
}

/**
 * 服务独立记账器。
 *
 * 只有一种场景会启用：**进程被杀、系统把前台服务重新拉起来**，此时 AppState 不存在，
 * 没人接收定位回调。这里用与 AppState 完全相同的过滤规则（TrackRecorder.decide）
 * 直接把点写进磁盘，用户回到 App 时轨迹是连续的。
 *
 * 和 AppState 的交接靠 [isActive] 开关保证互斥：
 * AppState 构造时先 [deactivate] 收编（落盘→关闸），再自己注册监听，
 * 因此不会出现两个写入方同时改同一个文件。
 */
object TrackWriter {

    @Volatile
    var isActive: Boolean = false
        private set

    private var points: MutableList<TrackPoint> = mutableListOf()
    private var pending: TrackPoint? = null
    private var lastSaveAt = 0L

    fun activate(context: Context) {
        if (isActive) return
        points = TrackRecorder.load(context).toMutableList()
        pending = null
        lastSaveAt = 0L
        isActive = true
    }

    /** 收编：把当前点落盘并关闸，之后由 AppState 接管 */
    fun deactivate(context: Context) {
        if (!isActive) return
        isActive = false
        runCatching { TrackRecorder.save(context, points) }
        points = mutableListOf()
        pending = null
    }

    /** 当前已记录的里程（公里），给通知文案用 */
    fun distanceKm(): Double = TrackRecorder.distanceKm(points)

    fun pointCount(): Int = points.size

    fun append(context: Context, location: Location, onProgress: (String) -> Unit) {
        if (!isActive) return
        if (!TrackService.shouldDispatch(location)) return
        val lat = location.latitude
        val lon = location.longitude
        if (lat == 0.0 && lon == 0.0) return
        val fixElapsed = location.elapsedRealtimeNanos
        if (fixElapsed > 0L &&
            (SystemClock.elapsedRealtimeNanos() - fixElapsed) / 1_000_000 > 60_000
        ) return

        val accuracy = if (location.hasAccuracy()) location.accuracy else 0f
        val time = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        val (gcjLat, gcjLon) = TrackRecorder.wgs84ToGcj02(lat, lon)
        val candidate = TrackPoint(gcjLat, gcjLon, time, accuracy)
        val speedMps = if (location.hasSpeed()) location.speed else null
        when (TrackRecorder.decide(points.lastOrNull(), candidate, pending, speedMps)) {
            TrackRecorder.Decision.ACCEPT -> {
                pending = null
                points = TrackRecorder.appendPoint(points, candidate).toMutableList()
            }
            TrackRecorder.Decision.CONFIRM -> pending = candidate
            TrackRecorder.Decision.REJECT -> Unit
        }

        val now = System.currentTimeMillis()
        // 15 秒落一次盘：进程被杀的瞬间最多丢十几秒的轨迹，比每个点都写省电得多
        if (now - lastSaveAt >= 15_000L) {
            lastSaveAt = now
            runCatching { TrackRecorder.save(context, points) }
            onProgress("正在记录 · %.2f km · %d 个点".format(distanceKm(), points.size))
        }
    }
}
