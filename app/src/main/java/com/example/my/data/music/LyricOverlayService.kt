package com.example.my.data.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.my.MainActivity
import com.example.my.R
import com.example.my.ui.i18n.tr
import com.example.my.util.SplashGate
import kotlin.math.abs

/**
 * 桌面歌词悬浮窗。
 *
 * 用前台服务持有 TYPE_APPLICATION_OVERLAY 窗口，按播放进度轮询当前歌词行；
 * 用户可在歌词页开关，退出 App 后仍保留，直到手动关闭或停止播放。
 */
class LyricOverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "lyric_overlay"
        private const val NOTIF_ID = 2101
        private const val ACTION_START = "com.example.my.lyric.overlay.START"
        private const val ACTION_STOP = "com.example.my.lyric.overlay.STOP"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_FALLBACK = "fallback"
        private const val EXTRA_TIMES = "times"
        private const val EXTRA_TEXTS = "texts"
        private const val EXTRA_OFFSET = "offset"

        fun start(
            context: Context,
            title: String,
            fallback: String,
            lines: List<LrcLine>,
            offsetMs: Long,
            traditional: Boolean
        ) {
            val intent = Intent(context, LyricOverlayService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_FALLBACK, fallback)
                putExtra(EXTRA_TIMES, lines.map { (it.timeMs + offsetMs).coerceAtLeast(0L) }.toLongArray())
                putExtra(EXTRA_TEXTS, ArrayList(lines.map {
                    if (traditional) HanziScript.toTraditional(it.text) else it.text
                }))
                putExtra(EXTRA_OFFSET, offsetMs)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, LyricOverlayService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }

    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private var lyricView: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var times: LongArray = LongArray(0)
    private var texts: List<String> = emptyList()
    private var fallback: String = ""
    private var title: String = ""
    private var foregroundStarted = false
    private val handler = Handler(Looper.getMainLooper())
    /** 上一次真正写进 TextView 的句子；没换句就跳过 setText，避免每 260ms 重绘。 */
    private var lastText: String? = null
    /** 息屏后悬浮歌词看不见，停止轮询；亮屏再补当前句并恢复。 */
    private var screenOn = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    handler.removeCallbacks(tick)
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    lastText = null
                    refreshLine()
                    handler.removeCallbacks(tick)
                    handler.post(tick)
                }
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            refreshLine()
            // 播放器的 positionMs 本来就是 500ms 更新一次，查得比它快没有意义；
            // 暂停时位置不再变化，进一步降到 1.5s，长时间挂桌面歌词也不空转。
            handler.postDelayed(this, if (MusicPlayer.isPlaying) 500L else 1_500L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 常驻服务被系统单独拉起时，进程已存活，不应再显示启动页
        SplashGate.onBackgroundUse()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        screenOn = (getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isInteractive ?: true
        runCatching {
            ContextCompat.registerReceiver(
                this,
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                },
                ContextCompat.RECEIVER_EXPORTED
            )
        }
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        fallback = intent?.getStringExtra(EXTRA_FALLBACK).orEmpty().ifBlank { title }
        times = intent?.getLongArrayExtra(EXTRA_TIMES) ?: LongArray(0)
        texts = intent?.getStringArrayListExtra(EXTRA_TEXTS).orEmpty()
        lastText = null
        ensureForeground()
        ensureOverlay()
        refreshLine()
        handler.removeCallbacks(tick)
        if (screenOn) handler.post(tick)
        return START_STICKY
    }

    private fun ensureForeground() {
        if (foregroundStarted) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification())
            return
        }
        val type = if (Build.VERSION.SDK_INT >= 34) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        foregroundStarted = true
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_music)
        .setContentTitle(title.ifBlank { tr("桌面歌词", "Floating lyrics") })
        .setContentText(tr("桌面歌词已开启", "Floating lyrics on"))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 2102,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MusicActions.EXTRA_OPEN_MUSIC, true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        NotificationChannel(
            CHANNEL_ID,
            tr("桌面歌词", "Floating lyrics"),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = tr("在其它应用上显示当前歌词", "Show current lyric over other apps")
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }.also { nm.createNotificationChannel(it) }
    }

    private fun ensureOverlay() {
        if (root != null) return
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.argb(218, 28, 24, 22))
                setStroke(dp(1), Color.argb(70, 255, 255, 255))
            }
        }

        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = resources.displayMetrics.widthPixels - dp(96)
            setShadowLayer(3f, 0f, 1f, Color.argb(180, 0, 0, 0))
            text = fallback.ifBlank { tr("桌面歌词", "Floating lyrics") }
        }
        container.addView(
            tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        lyricView = tv

        val close = TextView(this).apply {
            text = "×"
            setTextColor(Color.argb(210, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(6), 0)
            setOnClickListener { stopSelf() }
        }
        container.addView(close, LinearLayout.LayoutParams(dp(30), dp(36)))

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(120)
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) dragging = true
                    if (dragging) {
                        lp.x = startX + dx
                        lp.y = startY + dy
                        runCatching { wm.updateViewLayout(container, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging
                else -> false
            }
        }

        runCatching {
            wm.addView(container, lp)
            root = container
            params = lp
        }.onFailure {
            stopSelf()
        }
    }

    private fun refreshLine() {
        val tv = lyricView ?: return
        val next = if (texts.isEmpty() || times.isEmpty()) {
            fallback.ifBlank { title.ifBlank { tr("桌面歌词", "Floating lyrics") } }
        } else {
            val pos = MusicPlayer.positionMs
            val index = times.indexOfLast { it <= pos }.coerceAtLeast(0)
            texts.getOrElse(index) { fallback }
        }
        if (next == lastText) return
        lastText = next
        tv.text = next
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching {
            root?.let { wm.removeView(it) }
        }
        root = null
        lyricView = null
        params = null
        if (foregroundStarted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).toInt()
}
