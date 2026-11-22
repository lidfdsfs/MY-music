package com.example.my.data.music

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * 足迹配乐的**唯一音源**。
 *
 * 之前配乐条、MemoryMedia、FootprintMediaPager 各自新建一个播放器起一路，
 * 加上音乐页的 MusicPlayer，同一首歌能同时响好几层；而且某处把 `resumed`
 * 放进 `DisposableEffect` 的 key，前后台一切换就释放再新建一个自动播放的实例，
 * 表现为「点了暂停，又给我启动了一个新的这首歌的播放」。
 *
 * 这里把「足迹配乐」收成一个单例：
 * · 全进程只有一个 MediaPlayer 实例，多个界面只是它的视图，物理上不可能叠音；
 * · attach 对同一 path **绝不重建播放器**，也不碰用户刚按下的暂停状态；
 * · 起播时让音乐页 pause，音乐页 playInternal 起播时反向调 BgmPlayback.pause，
 *   两边互斥，任何时刻只有一路出声；
 * · 引用计数：最后一个界面离开才释放，前后台切换只暂停/恢复，不新建。
 */
object BgmPlayback {

    private class Owner(val path: String, val looping: Boolean)

    /** 当前绑定的配乐文件；null 表示没有任何界面在用它 */
    var path by mutableStateOf<String?>(null)
        private set

    /** 用户最后一次选择的播放状态（后台暂停期间仍为 true，用来决定回来要不要续播） */
    var isPlaying by mutableStateOf(false)
        private set

    var positionMs by mutableLongStateOf(0L)
        private set

    var durationMs by mutableLongStateOf(0L)
        private set

    private var player: MediaPlayer? = null
    private val owners = linkedMapOf<String, Owner>()
    private var looping = false
    private var bgPaused = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private val ownerSeq = AtomicLong(0L)

    init {
        // 音乐页的起播路径不止一条（点继续 / 切歌 / prepare 完成 / 音频焦点恢复），
        // 只在 playInternal 里让路会漏。只盯 isPlaying 这一个状态：任何路径变成
        // 「在播」，配乐立刻让路，保证任何时刻只有一路出声。
        scope.launch {
            snapshotFlow { MusicPlayer.isPlaying }
                .collect { playing -> if (playing) pause() }
        }
    }

    /** 每个 Composable 用独立的 owner id，避免两处同时展示时互相把对方 detach 掉 */
    fun newOwnerId(prefix: String): String = "$prefix:${ownerSeq.incrementAndGet()}"

    /**
     * 绑定一个界面到 [filePath]。
     * 同一首歌只更新循环设置，**不重建播放器、不改写播放状态**——这是杜绝
     * 「点暂停后又起一遍」的关键；换歌才释放旧的并新建。
     */
    fun attach(
        owner: String,
        filePath: String,
        looping: Boolean = false,
        autoplay: Boolean = true,
    ) {
        // 文件不存在时别留下「幽灵 owner」，否则 looping 会沿用上一首的设置
        if (!File(filePath).exists()) return
        owners[owner] = Owner(filePath, looping)

        val sameTrack = path == filePath && player != null
        // 只要有一个视图要求「播完停」，就不让别的视图把全局改成无限循环
        this.looping = owners.values.all { it.looping }
        if (sameTrack) {
            runCatching { player?.isLooping = this.looping }
            return
        }

        // 只有这次真的要出声才让音乐页让路；autoplay=false（视频待播）时
        // 配乐并不响，没必要把用户正在听的音乐掐掉。
        if (autoplay && MusicPlayer.isPlaying) MusicPlayer.pause()
        releasePlayer()
        path = filePath
        positionMs = 0L
        durationMs = 0L
        val mp = MediaPlayer()
        try {
            mp.setDataSource(filePath)
            mp.isLooping = this.looping
            mp.setOnCompletionListener {
                if (!this@BgmPlayback.looping) {
                    isPlaying = false
                    positionMs = durationMs
                }
            }
            // 配乐也走异步 prepare：DisposableEffect 在主线程执行，坏文件/慢存储
            // 同步 prepare 会直接把页面卡住，和视频 prepare 是同一类 ANR。
            mp.setOnPreparedListener { prepared ->
                if (player !== prepared) return@setOnPreparedListener
                durationMs = runCatching { prepared.duration.toLong() }.getOrDefault(0L).coerceAtLeast(0L)
                if (autoplay && !bgPaused) startInternal() else {
                    isPlaying = false
                    ensureTicker()
                }
            }
            player = mp
            mp.prepareAsync()
        } catch (t: Throwable) {
            runCatching { mp.release() }
            player = null
            path = null
            isPlaying = false
        }
    }

    /** 界面离开：只有最后一个持有者走了才真正释放 */
    fun detach(owner: String) {
        owners.remove(owner)
        if (owners.isEmpty()) releasePlayer()
        else {
            looping = owners.values.all { it.looping }
            runCatching { player?.isLooping = looping }
        }
    }

    /** 点一下整条：暂停 / 继续；播完再点从头续播 */
    fun toggle() {
        // 与 MusicPlayer.toggle() 同口径：先判「在播」，再判底层实例，
        // 避免状态不一致时点一下毫无反应、甚至又起一路。
        if (isPlaying) {
            pause()
            return
        }
        val mp = player ?: return
        if (!looping && durationMs > 0L && positionMs >= durationMs - 200L) {
            runCatching { mp.seekTo(0) }
            positionMs = 0L
        }
        startInternal()
    }

    /** 松手跳到对应时间；只改这一个实例，不新建播放器 */
    fun seekTo(ms: Int) {
        val mp = player ?: return
        val target = ms.coerceIn(0, durationMs.toInt().coerceAtLeast(0))
        // seekTo 抛异常时别把 UI 进度写成一个播放器根本没去到的位置
        if (runCatching { mp.seekTo(target) }.isSuccess) positionMs = target.toLong()
    }

    /** 拖动前在播 / 播完往回拖：确保从这个位置继续出声 */
    fun ensurePlaying() {
        if (!isPlaying) startInternal()
    }

    /**
     * 音乐页起播时调用：配乐让路。
     * 只暂停、不释放，用户回到足迹页点一下即可接着听。
     */
    fun pause() {
        if (!isPlaying) return
        runCatching { player?.pause() }
        isPlaying = false
        stopTicker()
    }

    /** 退到后台：保留用户状态，只停声音；绝不销毁重建 */
    fun pauseForBackground() {
        bgPaused = true
        if (isPlaying) runCatching { player?.pause() }
        stopTicker()
    }

    /** 回到前台：仅当用户最后一次选择是「播」才恢复，且仍要走完整互斥 */
    fun resumeFromBackground() {
        bgPaused = false
        // 后台期间通知/小组件可能起播了音乐页；直接 start() 会绕过
        // startInternal() 里的 MusicPlayer.pause()，出现毫秒级双声窗口。
        if (isPlaying) startInternal()
    }

    private fun startInternal() {
        val mp = player ?: return
        val ok = runCatching { mp.start() }.isSuccess
        if (!ok) {
            isPlaying = false
            return
        }
        isPlaying = true
        bgPaused = false
        // 单音源：配乐一出声，音乐页必须停
        if (MusicPlayer.isPlaying) MusicPlayer.pause()
        ensureTicker()
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /** 唯一的进度轮询；多个界面同时展示时也只跑这一条 */
    private fun ensureTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                val mp = player
                if (mp != null && isPlaying && !bgPaused) {
                    runCatching {
                        positionMs = mp.currentPosition.toLong().coerceAtLeast(0L)
                        if (durationMs <= 0L) durationMs = mp.duration.toLong().coerceAtLeast(0L)
                        if (!mp.isPlaying && !looping && durationMs > 0L &&
                            positionMs >= durationMs - 200L
                        ) {
                            isPlaying = false
                            positionMs = durationMs
                        }
                    }
                }
                delay(200)
            }
        }
    }

    private fun releasePlayer() {
        runCatching {
            player?.setOnCompletionListener(null)
            player?.release()
        }
        player = null
        stopTicker()
        path = null
        isPlaying = false
        positionMs = 0L
        durationMs = 0L
        looping = false
    }
}
