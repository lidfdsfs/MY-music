package com.example.my.data.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.my.MainActivity
import com.example.my.MusicWidgetProvider
import com.example.my.R
import com.example.my.ui.i18n.tr
import com.example.my.util.SplashGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 音乐相关的系统偏好（设置页开关读写这里） */
object MusicPrefs {
    private const val P = "music_prefs"
    private const val KEY_NOTIF = "show_notification"

    fun notificationsEnabled(context: Context): Boolean =
        context.getSharedPreferences(P, Context.MODE_PRIVATE).getBoolean(KEY_NOTIF, true)

    fun setNotificationsEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(P, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NOTIF, on).apply()
        if (on) MusicExternal.refresh(context) else MusicNotification.cancel(context)
    }
}

/** 通知 / 小组件 / 媒体会话共用的控制动作 */
object MusicActions {
    const val TOGGLE = "com.example.my.music.TOGGLE"
    const val PREV = "com.example.my.music.PREV"
    const val NEXT = "com.example.my.music.NEXT"
    const val STOP = "com.example.my.music.STOP"
    const val OPEN = "com.example.my.music.OPEN"
    /** 小组件与通知统一只用 TOGGLE；这两个是给外部快捷方式 / 旧版本兼容留的显式动作 */
    const val PLAY = "com.example.my.music.PLAY"
    const val PAUSE = "com.example.my.music.PAUSE"

    const val EXTRA_OPEN_MUSIC = "open_music"
}

/**
 * 通知栏 / 锁屏 / 桌面小组件上的播放控制。
 *
 * 用 BroadcastReceiver 而不是后台 Service：App 是纯本地播放器，退出页面即停；
 * 通知 action 与桌面小组件共用同一套 action，避免两套逻辑漂移。
 */
class MusicControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 通知控制按钮可能在后台单独拉起进程；之后进 App 属于热启动，不该再补启动页
        SplashGate.onBackgroundUse()
        // 通知按钮可能把已经退到后台/被系统回收的进程重新拉起来。
        // 以前这里 post 到主线程后立刻返回，系统会认为广播已处理完并可能马上杀进程，
        // 于是按钮“有时点了没反应”。用 goAsync 把这次广播的生命周期撑到实际操作结束。
        val pending = goAsync()
        try {
            // 从桌面小组件/通知冷启动：进程里还没有歌曲列表，先初始化并扫一次（有权限时）
            if (MusicPlayer.songs.isEmpty()) {
                MusicPlayer.init(context.applicationContext)
                val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    android.Manifest.permission.READ_MEDIA_AUDIO
                else android.Manifest.permission.READ_EXTERNAL_STORAGE
                if (context.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    MusicPlayer.scan(context)
                }
            }
            when (intent.action) {
                MusicActions.TOGGLE -> {
                    // 从桌面小组件冷启动点「播放」时进程里可能还没有当前歌曲
                    if (MusicPlayer.current == null) {
                        MusicPlayer.songs.firstOrNull()?.let { MusicPlayer.play(it) }
                    } else MusicPlayer.toggle()
                }
                MusicActions.PLAY -> {
                    if (MusicPlayer.current == null) {
                        MusicPlayer.songs.firstOrNull()?.let { MusicPlayer.play(it) }
                    } else if (!MusicPlayer.isPlaying) MusicPlayer.toggle()
                }
                MusicActions.PAUSE -> MusicPlayer.pause()
                MusicActions.PREV -> MusicPlayer.prev()
                MusicActions.NEXT -> MusicPlayer.next()
                MusicActions.STOP -> {
                    MusicPlayer.pause()
                    MusicNotification.cancel(context)
                }
                else -> return
            }
            // 当前就在主线程，refresh 会同步执行，状态在广播结束前一定已经写回通知
            MusicExternal.refresh(context)
        } finally {
            pending.finish()
        }
    }
}

/** 播放器状态变化的统一出口：一处调用，通知 / 媒体会话 / 小组件全部同步 */
object MusicExternal {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 通知 / 媒体会话 / 桌面小组件都属于系统 UI，只允许在主线程刷新。
     *
     * 尤其 MediaSessionCompat.setCallback 内部会 `new Handler()`（无参构造取当前线程的
     * Looper）：截图批量导入走 Dispatchers.IO → markFavorite → notifyChanged 时，后台线程
     * 没有 Looper，会直接抛「Can't create handler inside thread that has not called
     * Looper.prepare()」。这里在统一出口兜住：已在主线程就直接跑，否则 post 一次到主线程。
     * 顺带保证 session 单例只由主线程创建，消除原来的并发竞态。
     */
    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }

    fun notifyChanged(context: Context) {
        runOnMain { refresh(context) }
    }

    fun notifyStopped(context: Context) {
        runOnMain {
            MusicNotification.cancel(context)
            refreshSession(context)
            refreshWidgets(context)
        }
    }

    fun refresh(context: Context) {
        runOnMain {
            // 先同步建好媒体会话（主线程）；通知的 MediaStyle 要立刻拿到 sessionToken 才有进度条
            refreshSession(context)
            if (MusicPlayer.current == null) {
                MusicNotification.cancel(context)
            } else if (MusicPrefs.notificationsEnabled(context)) {
                MusicNotification.show(context)
            } else {
                MusicNotification.cancel(context)
            }
            refreshWidgets(context)
        }
    }

    private fun refreshWidgets(context: Context) {
        runCatching { MusicWidgetProvider.refresh(context) }
    }

    @Volatile
    private var session: MediaSessionCompat? = null

    /** 给 MusicNotification 的 MediaStyle 用，渲染进度条 */
    val sessionToken: MediaSessionCompat.Token? get() = session?.sessionToken

    private fun refreshSession(context: Context) {
        val s = ensureSession(context)
        val song = MusicPlayer.current
        val main = Handler(Looper.getMainLooper())
        fun applyState() {
            if (song == null) {
                s.setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE)
                        .setState(PlaybackStateCompat.STATE_NONE, 0L, 1f).build()
                )
                return
            }
            val md = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album.takeIf { it != "网页缓存" }.orEmpty())
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, MusicPlayer.durationMs)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, loadCover(context, song))
                .build()
            s.setMetadata(md)
            s.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO or
                            PlaybackStateCompat.ACTION_STOP
                    )
                    .setState(
                        if (MusicPlayer.isPlaying) PlaybackStateCompat.STATE_PLAYING
                        else PlaybackStateCompat.STATE_PAUSED,
                        MusicPlayer.positionMs,
                        if (MusicPlayer.isPlaying) 1f else 0f
                    ).build()
            )
        }
        // 已经在主线程就同步写，通知构建前 session 状态一定是最新的；
        // 后台线程才 post，避免锁屏/通知栏拿到上一首的旧状态。
        if (Looper.myLooper() == Looper.getMainLooper()) applyState()
        else main.post { applyState() }
    }

    private fun ensureSession(context: Context): MediaSessionCompat {
        session?.let { return it }
        val s = MediaSessionCompat(context.applicationContext, "MY足迹音乐").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                private fun onMain(block: () -> Unit) {
                    Handler(Looper.getMainLooper()).post {
                        block()
                        refresh(context.applicationContext)
                    }
                }

                override fun onPlay() {
                    onMain { if (!MusicPlayer.isPlaying) MusicPlayer.toggle() }
                }

                override fun onPause() {
                    onMain { MusicPlayer.pause() }
                }

                override fun onStop() {
                    onMain { MusicPlayer.pause() }
                }

                override fun onSkipToNext() {
                    onMain { MusicPlayer.next() }
                }

                override fun onSkipToPrevious() {
                    onMain { MusicPlayer.prev() }
                }

                override fun onSeekTo(pos: Long) {
                    onMain {
                        val dur = MusicPlayer.durationMs.takeIf { it > 0 } ?: return@onMain
                        MusicPlayer.seekTo(pos.toFloat() / dur)
                    }
                }

                override fun onFastForward() {
                    onMain { MusicPlayer.seekBy(10_000L) }
                }

                override fun onRewind() {
                    onMain { MusicPlayer.seekBy(-10_000L) }
                }
            }, Handler(Looper.getMainLooper()))
            isActive = true
        }
        session = s
        return s
    }

    /**
     * 内嵌专辑封面。一次 notifyChanged 会连刷通知 / 媒体会话 / 桌面小组件，
     * 以前同一首歌要全尺寸解码 3 次；现在按 song.id 缓存 + 预采样，只解一次。
     */
    private val coverCache = HashMap<Long, Bitmap?>()
    private const val COVER_CACHE_LIMIT = 8
    /** 已经发起过联网封面请求的歌曲，避免通知每次刷新都重复打接口 */
    private val coverAttempts = HashMap<Long, Long>()
    private const val COVER_RETRY_MS = 10L * 60L * 1000L
    private val coverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 同步拿封面：内嵌图 → MediaStore 专辑封面 → 封面仓库缓存。
     * 都没有时后台联网找一次，找到后会自动刷新通知和小组件；
     * 调用方不在主线程阻塞的情况下也能先拿到默认图占位。
     */
    fun loadCover(context: Context, song: LocalSong): Bitmap? {
        synchronized(coverCache) {
            coverCache[song.id]?.let { return it }
        }
        decodeCover(context, song)?.let {
            rememberCover(song.id, it)
            return it
        }
        CoverRepository.cached(context, song)?.let {
            rememberCover(song.id, it)
            return it
        }
        ensureCoverAsync(context, song)
        return null
    }

    /** 通知大图占位：真实封面还没下载好时不至于只剩一个小图标 */
    fun defaultCover(context: Context): Bitmap? {
        synchronized(coverCache) {
            coverCache[DEFAULT_COVER_ID]?.let { return it }
        }
        val bmp = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.music_cover_default)
                ?.let { Bitmap.createScaledBitmap(it, 256, 256, true) }
        }.getOrNull()
        if (bmp != null) rememberCover(DEFAULT_COVER_ID, bmp)
        return bmp
    }

    private fun rememberCover(id: Long, bitmap: Bitmap) {
        synchronized(coverCache) {
            if (coverCache.size >= COVER_CACHE_LIMIT) coverCache.clear()
            coverCache[id] = bitmap
        }
    }

    private fun ensureCoverAsync(context: Context, song: LocalSong) {
        val now = System.currentTimeMillis()
        synchronized(coverAttempts) {
            val last = coverAttempts[song.id] ?: 0L
            if (now - last < COVER_RETRY_MS) return
            coverAttempts[song.id] = now
        }
        coverScope.launch {
            val bmp = runCatching { CoverRepository.load(context.applicationContext, song) }.getOrNull()
                ?: return@launch
            rememberCover(song.id, bmp)
            // 封面是异步到的：重刷一次通知 / MediaSession / 桌面小组件
            runOnMain { refresh(context.applicationContext) }
        }
    }

    private fun decodeCover(context: Context, song: LocalSong): Bitmap? = try {
        val raw = try {
            val r = MediaMetadataRetriever()
            // 导入到 files/music/ 的音频是纯文件路径，file:// Uri 在部分 ROM 上取不到元数据
            if (song.path.isNotBlank()) r.setDataSource(song.path) else r.setDataSource(context, song.uri)
            val b = r.embeddedPicture
            r.release()
            b
        } catch (_: Exception) {
            null
        }
        if (raw != null) {
            // 先只读尺寸：3000×3000 的封面全尺寸解码会瞬时分配 ~36MB
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
            val decode = BitmapFactory.Options().apply {
                inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, 256)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(raw, 0, raw.size, decode)
                ?.let { Bitmap.createScaledBitmap(it, 256, 256, true) }
        } else {
            // 本地歌曲常见：音频本身没有内嵌图，但 MediaStore 里存了专辑封面
            mediaStoreCover(context, song)
        }
    } catch (_: Exception) {
        null
    }

    private fun mediaStoreCover(context: Context, song: LocalSong): Bitmap? {
        if (song.id <= 0 || song.uri.scheme != "content") return null
        return runCatching {
            val uri = song.albumArtUri()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, 256)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }?.let { Bitmap.createScaledBitmap(it, 256, 256, true) }
        }.getOrNull()
    }

    private const val DEFAULT_COVER_ID = Long.MIN_VALUE

    private fun sampleFor(w: Int, h: Int, target: Int): Int {
        if (w <= 0 || h <= 0) return 1
        var s = 1
        var width = w
        var height = h
        while (width / 2 >= target && height / 2 >= target) {
            width /= 2
            height /= 2
            s *= 2
        }
        return s
    }
}

/** 系统通知中心的播放状态通知 */
object MusicNotification {

    const val CHANNEL_ID = "music_playback"
    private const val NOTIF_ID = 2002

    /** 歌词页开启「状态栏歌词」后写入；null 表示恢复普通的歌手/专辑副标题。 */
    private var lyricLine: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun setLyricLine(context: Context, line: String?) {
        // 这是唯一直接写通知、没走 MusicExternal.notifyChanged 的入口，
        // 自己补上主线程护栏，别让后台线程碰 NotificationManager。
        val apply = {
            lyricLine = line?.trim()?.takeIf { it.isNotBlank() }
            show(context)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) apply()
        else mainHandler.post(apply)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        NotificationChannel(
            CHANNEL_ID,
            tr("音乐播放", "Music playback"),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = tr("显示当前歌曲与播放控制", "Show current song and playback controls")
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }.also { nm.createNotificationChannel(it) }
    }

    fun show(context: Context) {
        val song = MusicPlayer.current ?: return
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MusicActions.EXTRA_OPEN_MUSIC, true)
        }
        val contentPi = PendingIntent.getActivity(
            context, 30, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun actionPi(action: String, request: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, request,
                Intent(context, MusicControlReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val playing = MusicPlayer.isPlaying
        val pos = formatClock(MusicPlayer.positionMs)
        val dur = formatClock(MusicPlayer.durationMs)
        val modeText = when (MusicPlayer.mode) {
            PlayMode.LOOP_ALL -> tr("列表循环", "Loop all")
            PlayMode.LOOP_ONE -> tr("单曲循环", "Repeat one")
            PlayMode.SHUFFLE -> tr("随机播放", "Shuffle")
            PlayMode.ORDER -> tr("顺序播放", "Sequence")
        }

        val albumText = song.album.takeIf { it.isNotBlank() && it != "网页缓存" }
        val subtitle = listOf(song.artist, albumText)
            .filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
            .ifBlank { tr("本地音乐", "Local music") }

        // 用 MediaStyle 渲染成大卡片：左侧专辑封面 + 标题/歌手 + 进度条 + 大控制按钮，
        // 与系统锁屏/下拉通知中心统一，不再用 BigTextStyle 紧凑样式。
        val bodyText = lyricLine ?: subtitle
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(song.title)
            .setContentText(bodyText)
            .setSubText("MY足迹 · $modeText")
            .setLargeIcon(MusicExternal.loadCover(context, song) ?: MusicExternal.defaultCover(context))
            .setContentIntent(contentPi)
            .setDeleteIntent(actionPi(MusicActions.STOP, 34))
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_notif_prev, tr("上一首", "Previous"),
                actionPi(MusicActions.PREV, 31)
            )
            .addAction(
                if (playing) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
                if (playing) tr("暂停", "Pause") else tr("播放", "Play"),
                actionPi(MusicActions.TOGGLE, 32)
            )
            .addAction(
                R.drawable.ic_notif_next, tr("下一首", "Next"),
                actionPi(MusicActions.NEXT, 33)
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(MusicExternal.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build())
        }
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIF_ID) }
    }

    private fun formatClock(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }
}
