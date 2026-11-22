package com.example.my.ui.components

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.my.data.music.BgmPlayback
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Ink
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.TypeScale
import com.example.my.util.VideoMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/**
 * 足迹详情 / 编辑面板 / 上传预览里的「实况 / 视频 + 配乐」播放块。
 *
 * 和媒体流页共用 [VideoPhase] 这一套状态：默认只显示封面，不创建播放器；
 * 点一下从 0 播**一遍**（不循环），播完停在末帧并提示「重新播放」，
 * 再点一下用同一个解码器从头开始。前后台只暂停/续播，绝不销毁重建。
 *
 * 画面用 TextureView + MediaPlayer：SurfaceView 是独立图层，既不吃圆角裁剪，
 * 在可拖动面板里还会花屏。容器已经按视频真实宽高比排版，TextureView 直接铺满，
 * 不再额外叠一层 setTransform 缩放矩阵。
 */
@Composable
fun MemoryMediaBlock(
    videoPath: String?,
    videoIsLive: Boolean,
    bgmPath: String?,
    bgmTitle: String?,
    modifier: Modifier = Modifier,
    /** 详情页这类「整块区域」可以给更大的上限；编辑面板里用小一点的值 */
    maxHeight: androidx.compose.ui.unit.Dp = 400.dp,
    /** 视频是否静音；不传时按「有配音就静音」处理，全屏查看器会显式传 true */
    muteVideo: Boolean? = null,
    /** 全屏查看器打开即播；详情/编辑面板保持默认「点一下才播」 */
    autoPlay: Boolean = false,
    /**
     * 是否处于激活页（feed 当前页 / 详情页可见）。feed 滑走时传 false，
     * 主动释放解码器回到封面——否则 beyondViewportPageCount 预组合的相邻页会
     * 留下多个 MediaPlayer，既占内存又可能在后台继续占用音频焦点。
     * 不传时恒为 true，保持详情页/编辑面板原有行为。
     */
    active: Boolean = true
) {
    if (videoPath == null && bgmPath == null) return
    val resumed = rememberIsResumed()
    // 状态机与解码器都来自统一真源，这里只保留「视频文件是否还在」这一条本地状态
    val stage = rememberVideoStage(videoPath)
    var videoMissing by remember(videoPath) { mutableStateOf(false) }

    // 有配乐才让视频静音：没有配乐时原声要保住，否则「视频」就成了默片
    val hasBgm = bgmPath != null && File(bgmPath).exists()
    val muteNow = muteVideo ?: hasBgm

    // 绝不能在组合线程里读视频元数据：MediaMetadataRetriever 会触发 binder 调用，
    // 本地文件异常/云端地址超时时会把主线程卡到 ANR。先给 16:9，IO 线程探测完再换真实比例。
    val aspect by produceState(initialValue = 16f / 9f, videoPath) {
        if (videoPath == null) return@produceState
        value = withContext(Dispatchers.IO) { VideoMeta.aspect(videoPath) }
    }
    val bgmOwner = remember { BgmPlayback.newOwnerId("memory") }

    // 全屏打开即播：先登记播放意愿，surface 晚到时 attach 会自己接上，不会起第二路
    LaunchedEffect(autoPlay, videoPath) {
        if (autoPlay && videoPath != null && stage.phase == VideoPhase.IDLE) stage.onTap()
    }

    // feed 滑走：释放解码器回到待播封面；滑回来时上面的 autoPlay effect 会重新起播。
    // 必须与 autoPlay effect 分开，否则 active 翻转会把 phase 重置和起播挤在同一帧。
    LaunchedEffect(active) {
        if (!active) stage.onActiveLost()
    }

    // 前后台只暂停 / 续播，绝不销毁重建；不再依赖「碰巧已经建好的 player」
    LaunchedEffect(resumed) {
        if (resumed) stage.onForeground() else stage.onBackground()
    }

    // 个别机型不发 VIDEO_RENDERING_START，兜底撤封面（重播用更短的遮挡）
    LaunchedEffect(stage.phase, stage.generation) {
        if (stage.phase == VideoPhase.PLAYING && !stage.firstFrame) stage.fallbackFirstFrame()
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val ratio = (stage.realAspect ?: aspect).takeIf { it > 0.05f && it.isFinite() } ?: (16f / 9f)
        val areaHeight = minOf(maxHeight, if (ratio > 0f) maxWidth / ratio else maxHeight)
        val frameWidth = if (ratio > 0f) areaHeight * ratio else maxWidth

        Box(Modifier.fillMaxWidth().height(areaHeight)) {
            VideoBlurBackdrop(videoPath, Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(frameWidth)
                    .height(areaHeight)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(Color.Black)
            ) {
                if (videoPath != null && !videoMissing) {
                    // 默认待播：这一段完全不挂 AndroidView，也就没有 MediaPlayer
                    if (stage.phase != VideoPhase.IDLE) {
                        androidx.compose.runtime.key(stage.generation, videoPath) {
                            AndroidView(
                                factory = { ctx ->
                                    // 每一代视图自己保管 token，旧 view 的 destroy 不会误杀新实例
                                    var surfaceToken = 0L
                                    TextureView(ctx).apply {
                                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                                // 网络视频没有本地文件，MediaPlayer 自己会走 HTTP 拉流
                                                val remote = videoPath.startsWith("http://") || videoPath.startsWith("https://")
                                                if (!remote && !File(videoPath).exists()) {
                                                    videoMissing = true
                                                    return
                                                }
                                                surfaceToken = stage.attach(st, videoPath, muteNow)
                                            }
                                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                                stage.releaseToken(surfaceToken)
                                                return true
                                            }
                                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // 首帧真正画出来之前一直用封面盖住未初始化的纹理
                    if (!stage.firstFrame) VideoPosterCover(videoPath, Modifier.fillMaxSize(), fit = true)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { stage.onTap() }
                    )

                    when {
                        stage.failed -> VideoActionBadge(
                            Icons.Filled.Refresh,
                            tr("读不出来，点一下重试", "Tap to retry")
                        )
                        stage.phase == VideoPhase.IDLE -> VideoActionBadge(
                            Icons.Filled.PlayArrow,
                            tr("点击播放", "Tap to play")
                        )
                        stage.phase == VideoPhase.PAUSED -> VideoActionBadge(
                            Icons.Filled.PlayArrow,
                            tr("继续播放", "Resume")
                        )
                        stage.phase == VideoPhase.FINISHED -> VideoActionBadge(
                            Icons.Filled.Refresh,
                            tr("重新播放", "Replay")
                        )
                        // 播放中：不显示任何独立播放/暂停按钮
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (bgmPath != null && bgmTitle != null) {
                            Text("♪ $bgmTitle", color = Ink.OnCover, fontSize = TypeScale.body, maxLines = 2)
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            if (videoMissing) tr("这段视频文件已丢失", "This video file is gone")
                            else tr("配乐", "Soundtrack"),
                            color = Ink.OnCover,
                            fontSize = TypeScale.body
                        )
                    }
                }

                SurfaceLabel(
                    text = when {
                        videoPath != null && videoIsLive -> tr("实况", "Live")
                        videoPath != null -> tr("视频", "Video")
                        else -> tr("配乐", "Soundtrack")
                    }
                )
                if (hasBgm && bgmTitle != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(Radius.xs))
                            .background(Color(0xB3101318))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("♪ $bgmTitle", color = Ink.OnCover, fontSize = TypeScale.caption, maxLines = 1)
                    }
                }
            }
        }
    }

    // 配乐交给全局单音源（与配乐条、音乐页互斥），视频轨保持静音
    DisposableEffect(bgmPath, bgmOwner) {
        val path = bgmPath
        if (path != null && File(path).exists()) {
            // 有视频时配乐跟着视频走：默认待播不发声，点播放才起、播完即停；
            // looping 与配乐条一致，避免同屏两个 owner 把全局改成无限循环
            BgmPlayback.attach(bgmOwner, path, looping = false, autoplay = true)
        }
        onDispose { BgmPlayback.detach(bgmOwner) }
    }
    // 配乐只暂停/恢复全局那一个实例，前后台切换绝不新建播放器
    LaunchedEffect(resumed) {
        if (resumed) BgmPlayback.resumeFromBackground()
        else BgmPlayback.pauseForBackground()
    }
}

// 解码器实现已统一到 VideoPlayback.kt 的 VideoPlaybackController：
// 这里不再维护第二份 MediaPlayer 生命周期，避免两个页面的行为各自漂移。

/** 半透明小标签，压在视频左上角 */
@Composable
private fun BoxScope.SurfaceLabel(text: String) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(10.dp)
            .clip(RoundedCornerShape(Radius.xs))
            .background(Color(0xB3101318))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = Ink.OnCover, fontSize = TypeScale.caption, fontWeight = FontWeight.SemiBold)
    }
}
