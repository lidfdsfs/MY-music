package com.example.my

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.my.data.music.MusicActions
import com.example.my.data.music.MusicControlReceiver
import com.example.my.data.music.MusicExternal
import com.example.my.data.music.MusicPlayer
import com.example.my.util.SplashGate
import com.example.my.widget.WidgetArt
import com.example.my.widget.WidgetKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 音乐小组件：2×2 方卡（黑胶唱片 + 歌名 + 上一首 / 播放暂停 / 下一首）。
 *
 * 播放和暂停合成一枚按钮：状态变化时用 [WidgetArt.playPause] 生成 9 帧形变位图，
 * 通过 `partiallyUpdateAppWidget` 只刷这一枚按钮，做出「按下 → 收拢 → 展开 → 回弹」
 * 的过渡。RemoteViews 不支持属性动画，逐帧推送是系统小组件里最稳的做法。
 *
 * 与 [com.example.my.widget.BaseWidget] 不同，这里不读 Room —— 播放状态是进程内内存态，
 * 刷新极其频繁，只复用公共层的主题 / 双语 / 卡片背景 / 绘图工具。
 */
class MusicWidgetProvider : AppWidgetProvider() {

    /**
     * 播放状态变化时 [MusicExternal.refresh] 会发自定义 [WidgetKit.ACTION_REFRESH]。
     * AppWidgetProvider 默认只认系统那几个 action，旧版本漏了这里，
     * 导致歌一直在播、桌面小组件却永远停在「未在播放」。
     */
    override fun onReceive(context: Context, intent: Intent) {
        // 系统小组件更新可能在后台单独拉起进程；App 自己发 ACTION_REFRESH 不算
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE ||
            intent.action == AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED
        ) {
            SplashGate.onBackgroundUse()
        }
        if (intent.action == WidgetKit.ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                ?.takeIf { it.isNotEmpty() }
                ?: mgr.getAppWidgetIds(ComponentName(context, MusicWidgetProvider::class.java))
            onUpdate(context, mgr, ids)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val playing = MusicPlayer.isPlaying
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val had = p.contains(KEY_PLAYING)
        val prev = p.getBoolean(KEY_PLAYING, playing)
        p.edit().putBoolean(KEY_PLAYING, playing).apply()

        if (had && prev != playing) {
            // 先刷成「点击前的图标 + 最新歌名」，再由动画逐帧过渡到新状态
            ids.forEach { update(context, mgr, it, prev) }
            animate(context, mgr, ids, playing)
        } else {
            ids.forEach { update(context, mgr, it, playing) }
        }
    }

    private fun update(
        context: Context, mgr: AppWidgetManager, widgetId: Int,
        iconPlaying: Boolean = MusicPlayer.isPlaying
    ) {
        val v = RemoteViews(context.packageName, R.layout.widget_music_v2)
        val c = WidgetKit.colors(context)
        WidgetKit.decorate(context, v)

        val song = MusicPlayer.current
        v.setTextViewText(
            R.id.widget_music_title,
            song?.title ?: WidgetKit.t(context, "未在播放", "Not playing")
        )
        v.setTextViewText(
            R.id.widget_music_artist,
            song?.artist?.takeIf { it.isNotBlank() }?.let { "— $it" }
                ?: WidgetKit.t(context, "点击打开", "Tap to open")
        )
        v.setTextColor(R.id.widget_music_title, c.text)
        v.setTextColor(R.id.widget_music_artist, c.sub)

        val cover = song?.let { runCatching { MusicExternal.loadCover(context, it) }.getOrNull() }
        v.setImageViewBitmap(R.id.widget_music_disc, WidgetArt.vinyl(context, cover, c))

        icon(v, R.id.widget_music_prev, context, R.drawable.ic_notif_prev, c.accent)
        icon(v, R.id.widget_music_next, context, R.drawable.ic_notif_next, c.accent)
        v.setImageViewBitmap(
            R.id.widget_music_playpause,
            WidgetArt.playPause(iconPlaying, 1f, c)
        )

        v.setOnClickPendingIntent(
            R.id.widget_root,
            WidgetKit.openPending(context, WidgetKit.TARGET_MUSIC, 150)
        )
        control(context, v, R.id.widget_music_prev, MusicActions.PREV, 151)
        control(context, v, R.id.widget_music_playpause, MusicActions.TOGGLE, 152)
        control(context, v, R.id.widget_music_next, MusicActions.NEXT, 154)

        mgr.updateAppWidget(widgetId, v)
    }

    /** 只刷中间按钮的 9 帧形变，避免整卡刷新带来的闪烁 */
    private fun animate(context: Context, mgr: AppWidgetManager, ids: IntArray, playing: Boolean) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                val c = WidgetKit.colors(context)
                for (i in 0..8) {
                    val frame = WidgetArt.playPause(playing, i / 8f, c)
                    val partial = RemoteViews(context.packageName, R.layout.widget_music_v2).apply {
                        setImageViewBitmap(R.id.widget_music_playpause, frame)
                    }
                    runCatching { mgr.partiallyUpdateAppWidget(ids, partial) }
                        .onFailure {
                            // 少数 ROM 不支持局部刷新：退回整卡刷新，至少状态是对的
                            ids.forEach { id -> runCatching { update(context, mgr, id, playing) } }
                            return@launch
                        }
                    delay(26)
                }
                ids.forEach { runCatching { update(context, mgr, it, playing) } }
            } finally {
                pending.finish()
            }
        }
    }

    private fun icon(v: RemoteViews, id: Int, context: Context, res: Int, tint: Int) {
        v.setImageViewBitmap(id, WidgetArt.vector(context, res, 64, tint))
    }

    private fun control(context: Context, v: RemoteViews, id: Int, action: String, req: Int) {
        v.setOnClickPendingIntent(
            id,
            PendingIntent.getBroadcast(
                context, req,
                Intent(context, MusicControlReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    companion object {
        private const val PREFS = "music_widget_state"
        private const val KEY_PLAYING = "playing"

        fun refresh(context: Context) {
            WidgetKit.refresh(context, MusicWidgetProvider::class.java)
        }

        /** 切语言 / 切主题后全部小组件一起刷（RemoteViews 不走 values-en） */
        fun refreshAll(context: Context) {
            WidgetKit.refreshAll(context)
        }
    }
}
