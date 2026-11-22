package com.example.my.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.my.ui.theme.Ink
import com.example.my.ui.theme.TypeScale
import kotlinx.coroutines.delay

/**
 * 足迹里所有视频 / 实况片段的**统一播放真源**：状态机 + 解码器都只在这里有一份实现。
 *
 * - IDLE：默认态。只显示封面 + 操作提示，一个 MediaPlayer 都不创建，也不抢音频焦点；
 * - PLAYING：从 0 播一遍，不循环，画面上不显示任何独立播放/暂停按钮；
 * - PAUSED：播放中点一下停在当前帧；
 * - FINISHED：播完停在末帧，提示「重新播放」，点一下用同一个解码器从头开始。
 *
 * 媒体流页、详情/编辑面板、上传页的全屏预览共用这套状态，避免某个入口
 * 又变回「进页面自动播」或者「播完无限循环」。
 *
 * 之前 [VideoPhase] 虽然是共享枚举，但 ClipPlayer / VideoHolder 把解码器和状态
 * 各抄了一份，两边很快出现分叉（重播没重置首帧、错误分支少清一个标记）。
 * 现在 [VideoStageState] 管状态、[VideoPlaybackController] 管解码器，
 * 两个页面只剩布局差异，行为不可能再各自漂移。
 */
internal enum class VideoPhase { IDLE, PLAYING, PAUSED, FINISHED }

/** 解码器回调；controller 只认「当前还活着的那个 player」，迟到回调一律丢弃 */
internal class VideoStageCallbacks(
    val onSize: (Int, Int) -> Unit,
    val onFirstFrame: () -> Unit,
    val onCompletion: () -> Unit,
    val onError: () -> Unit,
)

/**
 * 全项目唯一的视频解码器实现。
 *
 * · 「想不想播」是一个独立于 player 的意愿：点播放时 TextureView 还没创建也没关系，
 *   surface 到位后自动接上，不再靠 LaunchedEffect 碰运气读一个非 state 的 player；
 * · 每次 attach 发一个 token，旧 surface 迟到的 destroy 回调只释放自己那一代，
 *   不会误杀刚挂上的新实例（换片 / 旋转 / generation 重建时的竞态）；
 * · release 把四个回调全摘掉，避免回调打到已销毁的界面。
 */
internal class VideoPlaybackController {
    var player: MediaPlayer? = null
        private set

    private var token = 0L
    private var wantPlaying = false
    private var backgrounded = false

    /** 返回本代 surface 的 token；销毁时交给 [releaseToken] */
    fun attachSurface(
        texture: SurfaceTexture,
        path: String,
        mute: Boolean,
        cb: VideoStageCallbacks,
    ): Long {
        releasePlayer()
        val myToken = ++token
        val mp = MediaPlayer()
        // 先登记再 prepare/start：这两步有可能同步触发 error / info 回调，
        // 若此时字段还是 null，回调里的释放会漏掉这个已经损坏的实例。
        player = mp
        try {
            mp.setSurface(Surface(texture))
            mp.setDataSource(path)
            mp.isLooping = false
            if (mute) mp.setVolume(0f, 0f)
            mp.setOnVideoSizeChangedListener { _, w, h ->
                if (player === mp) cb.onSize(w, h)
            }
            mp.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && player === mp) {
                    cb.onFirstFrame()
                    true
                } else false
            }
            mp.setOnCompletionListener {
                if (player === mp) {
                    wantPlaying = false
                    cb.onCompletion()
                }
            }
            // 抖音式滑到即播，但绝不可以在主线程同步 prepare：损坏视频 / 云端慢流
            // 会让 NuPlayer 重试十几秒，输入事件全部排队，表现就是整页卡死。
            mp.setOnPreparedListener { prepared ->
                if (player === mp && wantPlaying && !backgrounded) {
                    runCatching { prepared.start() }
                }
            }
            mp.setOnErrorListener { _, _, _ ->
                if (player === mp) {
                    wantPlaying = false
                    cb.onError()
                    releasePlayer()
                }
                true
            }
            // prepareAsync 把解码准备放到 MediaPlayer 自己的线程；UI 线程只负责接回调。
            mp.prepareAsync()
            return myToken
        } catch (t: Throwable) {
            releasePlayer()
            cb.onError()
            return myToken
        }
    }

    /** 只有本代 surface 才有权释放；换片时旧 view 的 destroy 不会再误杀新实例 */
    fun releaseToken(surfaceToken: Long) {
        if (surfaceToken == token) releasePlayer()
    }

    fun release() {
        releasePlayer()
    }

    /** UI 只表达意愿，player 尚未创建也不丢状态 */
    fun setWantPlaying(value: Boolean) {
        wantPlaying = value
        val mp = player ?: return
        if (value) {
            if (!backgrounded && !mp.isPlaying) runCatching { mp.start() }
        } else if (mp.isPlaying) {
            runCatching { mp.pause() }
        }
    }

    /** 播完后的「重新播放」：seekTo(0) 复用同一个解码器，绝不新建实例 */
    fun replay(): Boolean {
        val mp = player ?: return false
        val ok = runCatching {
            mp.seekTo(0)
            if (!backgrounded) mp.start()
        }.isSuccess
        if (ok) wantPlaying = true
        return ok
    }

    /** 前后台只暂停 / 续播，绝不销毁重建 */
    fun onBackground() {
        backgrounded = true
        val mp = player ?: return
        if (mp.isPlaying) runCatching { mp.pause() }
    }

    fun onForeground() {
        backgrounded = false
        val mp = player ?: return
        if (wantPlaying && !mp.isPlaying) runCatching { mp.start() }
    }

    private fun releasePlayer() {
        val mp = player ?: return
        player = null
        token++ // 让旧 surface 迟到的 destroy 回调失效
        runCatching {
            mp.setOnCompletionListener(null)
            mp.setOnInfoListener(null)
            mp.setOnErrorListener(null)
            mp.setOnVideoSizeChangedListener(null)
            mp.release()
        }
    }
}

/**
 * 视频画面的状态真源：点按序列、首帧封面、失败重试、滑页归零都只在这里。
 * 重播一律重新等首帧（旧实现漏了这一步，会把上一遍末帧/黑帧直接露出来）。
 */
@Stable
internal class VideoStageState(private val controller: VideoPlaybackController) {
    var phase by mutableStateOf(VideoPhase.IDLE)
        private set
    var firstFrame by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var generation by mutableIntStateOf(0)
        private set
    var realAspect by mutableStateOf<Float?>(null)
        private set

    /** 首播给足首帧渲染时间；重播解码器已热，短暂遮挡即可 */
    var firstFrameFallbackMs by mutableLongStateOf(700L)
        private set

    fun onTap() {
        when (phase) {
            VideoPhase.IDLE -> {
                val retryAfterFailure = failed
                firstFrame = false
                firstFrameFallbackMs = 700L
                failed = false
                // 上次是解码错误退出的，强制换一代视图，别拿坏掉的解码器反复试
                if (retryAfterFailure) generation += 1
                phase = VideoPhase.PLAYING
                controller.setWantPlaying(true)
            }
            VideoPhase.PLAYING -> {
                controller.setWantPlaying(false)
                phase = VideoPhase.PAUSED
            }
            VideoPhase.PAUSED -> {
                controller.setWantPlaying(true)
                phase = VideoPhase.PLAYING
            }
            VideoPhase.FINISHED -> {
                firstFrame = false
                firstFrameFallbackMs = 250L
                failed = false
                if (controller.replay()) {
                    phase = VideoPhase.PLAYING
                } else {
                    // 只有解码器确实不在了才允许重建一次
                    generation += 1
                    controller.setWantPlaying(true)
                    phase = VideoPhase.PLAYING
                }
            }
        }
    }

    /** 返回本代 surface 的 token，由创建它的 TextureView 自己保管，避免换片时互相误杀 */
    fun attach(texture: SurfaceTexture, path: String, mute: Boolean): Long =
        controller.attachSurface(
            texture, path, mute,
            VideoStageCallbacks(
                onSize = { w, h -> if (w > 0 && h > 0) realAspect = w.toFloat() / h },
                onFirstFrame = { firstFrame = true },
                onCompletion = { phase = VideoPhase.FINISHED },
                onError = {
                    failed = true
                    firstFrame = false
                    phase = VideoPhase.IDLE
                },
            )
        )

    fun releaseToken(surfaceToken: Long) {
        controller.releaseToken(surfaceToken)
    }

    fun release() {
        controller.release()
    }

    /** 滑走这一页：释放解码器并回到待播封面 */
    fun onActiveLost() {
        release()
        phase = VideoPhase.IDLE
        firstFrame = false
        failed = false
    }

    fun onBackground() = controller.onBackground()
    fun onForeground() = controller.onForeground()

    /** 个别机型不发 VIDEO_RENDERING_START，兜底也把封面撤掉 */
    suspend fun fallbackFirstFrame() {
        delay(firstFrameFallbackMs)
        if (phase == VideoPhase.PLAYING && !firstFrame) firstFrame = true
    }
}

@Composable
internal fun rememberVideoStage(key: String?): VideoStageState {
    val stage = remember(key) { VideoStageState(VideoPlaybackController()) }
    DisposableEffect(key) { onDispose { stage.release() } }
    return stage
}

/** 视频画面上的操作提示：是覆盖在画面上的一次性引导，不是常驻控制条；播放中整个收起 */
@Composable
internal fun BoxScope.VideoActionBadge(icon: ImageVector, label: String) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xB3101318))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = Ink.OnCover, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = Ink.OnCover, fontSize = TypeScale.caption, fontWeight = FontWeight.SemiBold)
    }
}
