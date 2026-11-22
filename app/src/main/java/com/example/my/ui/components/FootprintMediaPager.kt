package com.example.my.ui.components

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.my.data.music.BgmPlayback
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.ImageSamplingHigh
import com.example.my.ui.theme.Ink
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.TypeScale
import com.example.my.util.MediaCache
import com.example.my.util.PhotoStore
import com.example.my.util.VideoMeta
import com.example.my.util.VideoPoster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 足迹媒体流：照片、视频、实况片段全部排在同一条横向相册里。
 *
 * 之前视频是单独一个大黑块挂在标题下面，照片在顶上另走一条轮播——
 * 同一条足迹的两类素材被拆成两个位置，用户滑照片滑不到视频，也不明白那块黑的是什么。
 * 现在统一成一条流：
 * 1. 每一页右上角标「视频 / 实况」，照片不标；
 * 2. 视频页默认只显示封面、不创建播放器；点一下才从 0 播一遍，播完停在末帧，
 *    再点一下「重新播放」从头开始（同一个解码器，不另起一路）；
 * 3. 首帧真正画出来之前一直用 [VideoPoster] 的封面盖住纹理，不露黑屏或错帧。
 */
@Composable
fun FootprintMediaPager(
    photos: List<String>,
    videoPath: String?,
    videoIsLive: Boolean,
    bgmPath: String?,
    bgmTitle: String?,
    modifier: Modifier = Modifier,
    onTapPhoto: ((Int) -> Unit)? = null,
    /** 点视频页打开全屏查看器；不传则保持旧的「就地播放/暂停」 */
    onTapVideo: ((Int) -> Unit)? = null,
    onSettledPage: ((Int) -> Unit)? = null,
    videoPaths: List<String> = emptyList()
) {
    val hasBgm = bgmPath != null && File(bgmPath).exists()
    val allVideos = videoPaths.ifEmpty { listOfNotNull(videoPath) }
    // 云端 URL 先落到本机缓存再播放；解析在 IO 线程，避免首帧卡住主线程
    val resolvedVideos by produceState<List<String>>(initialValue = emptyList(), allVideos) {
        value = withContext(Dispatchers.IO) { allVideos.mapNotNull { MediaCache.localPath(it) } }
    }
    val existingVideos = resolvedVideos
    val hasVideo = existingVideos.isNotEmpty()

    val slides = remember(photos, existingVideos, videoIsLive) {
        buildList {
            photos.forEach { add(MediaSlide.Photo(it)) }
            existingVideos.forEach { add(MediaSlide.Clip(it, videoIsLive)) }
        }
    }

    if (slides.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Bg.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(tr("这张足迹还没有照片", "No photos yet"), color = com.example.my.ui.theme.Text.Muted, fontSize = 13.sp)
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val settledPage = pagerState.settledPage

    androidx.compose.runtime.LaunchedEffect(settledPage) {
        onSettledPage?.invoke(settledPage)
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (val slide = slides[page]) {
                is MediaSlide.Photo -> LocalFileImage(
                    path = slide.path,
                    maxDim = PhotoStore.DETAIL_DIM,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (onTapPhoto != null) Modifier.clickable { onTapPhoto(page) } else Modifier
                        )
                )

                is MediaSlide.Clip -> ClipStage(
                    path = slide.path,
                    active = settledPage == page,
                    // 有配音时视频轨静音，配音从头到尾不被打断；没有配音才用视频原声
                    mute = hasBgm,
                    bgmTitle = if (hasBgm) bgmTitle else null,
                    onOpen = if (onTapVideo != null) { { onTapVideo(page) } } else null
                )
            }
        }

        // 当前这一页是视频/实况时，右上角明说是什么
        (slides.getOrNull(settledPage) as? MediaSlide.Clip)?.let { clip ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(Color(0xB3101318))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (clip.live) tr("实况", "Live") else tr("视频", "Video"),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (slides.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                slides.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == settledPage) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == settledPage) Color.White else Color.White.copy(alpha = 0.45f)
                            )
                    )
                }
            }
        }
    }

    // 有配乐就走全局单音源：配音是这条足迹的主声音，视频画面只负责播画面（静音）。
    // 前后台只暂停/恢复，绝不释放重建；不再按视频播放态去 pause/ensurePlaying 配乐，
    // 否则「视频一播配乐就断」的割裂感很明显。
    val resumed = rememberIsResumed()
    val bgmOwner = remember { BgmPlayback.newOwnerId("pager") }
    DisposableEffect(bgmPath, bgmOwner) {
        val path = bgmPath
        if (path != null && File(path).exists()) {
            // 循环语义与配乐条一致：播完停在结尾，别让任一 owner 把全局改成无限循环
            BgmPlayback.attach(bgmOwner, path, looping = false, autoplay = true)
        }
        onDispose { BgmPlayback.detach(bgmOwner) }
    }
    LaunchedEffect(resumed) {
        if (resumed) BgmPlayback.resumeFromBackground()
        else BgmPlayback.pauseForBackground()
    }
}

private sealed interface MediaSlide {
    data class Photo(val path: String) : MediaSlide
    data class Clip(val path: String, val live: Boolean) : MediaSlide
}

/**
 * 视频 / 实况页：默认**只显示封面，一个解码器都不创建**。
 *
 * 用户点一下 → 从 0 播一遍（不循环）→ 播完停在末帧并显示「重新播放」→
 * 再点一下用同一个实例从头开始。滑走页面立刻释放并回到待播态，
 * 不会出现「进页面就自动播」「几路画面叠在一起」或者后台还在解码。
 */
@Composable
private fun ClipStage(
    path: String,
    active: Boolean,
    mute: Boolean,
    bgmTitle: String?,
    /** 传了就打开全屏查看器；不传则就地播放/暂停 */
    onOpen: (() -> Unit)? = null,
) {
    // 状态机与解码器都来自统一真源，这里只负责版式和点击层
    val stage = rememberVideoStage(path)
    val muteNow by rememberUpdatedState(mute)
    val openViewer by rememberUpdatedState(onOpen)

    // 同 MemoryMediaBlock：元数据探测必须在 IO 线程，组合阶段只给兜底比例，
    // 否则异常视频/慢速云端媒体会阻塞主线程直到系统判定 ANR。
    val aspect by produceState(initialValue = 16f / 9f, path) {
        value = withContext(Dispatchers.IO) { VideoMeta.aspect(path) }
    }

    // 滑走这一页：释放解码器、回到封面，声音和内存都不留在后台
    LaunchedEffect(active) {
        if (!active) stage.onActiveLost()
    }

    // 前后台只暂停 / 续播，绝不销毁重建；不再依赖「碰巧已经建好的 player」
    val resumed = rememberIsResumed()
    LaunchedEffect(resumed) {
        if (resumed) stage.onForeground() else stage.onBackground()
    }

    // 个别机型不发 VIDEO_RENDERING_START，兜底也把封面撤掉（重播用更短的遮挡）
    LaunchedEffect(stage.phase, stage.generation) {
        if (stage.phase == VideoPhase.PLAYING && !stage.firstFrame) stage.fallbackFirstFrame()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        VideoBlurBackdrop(path, Modifier.fillMaxSize())

        val ratio = (stage.realAspect ?: aspect).takeIf { it > 0.05f && it.isFinite() } ?: (16f / 9f)
        val frameWidth = (maxHeight * ratio).coerceAtMost(maxWidth)
        val frameHeight = if (ratio > 0f) frameWidth / ratio else maxHeight

        Box(
            modifier = Modifier
                .width(frameWidth)
                .height(frameHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
            // 播放器只在用户点过之后才挂：默认页连 MediaPlayer 都不存在
            if (stage.phase != VideoPhase.IDLE && active) {
                androidx.compose.runtime.key(stage.generation) {
                    AndroidView(
                        factory = { ctx ->
                            // 每一代视图自己保管 token，换片时旧 view 的 destroy 不会误杀新实例
                            var surfaceToken = 0L
                            TextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                        surfaceToken = stage.attach(st, path, muteNow)
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

            // 封面盖在纹理之上直到第一帧真正画出来：容器已经按真实比例排好，
            // 播放器直接铺满即可，不再叠加缩放矩阵（那正是画面被切成错位碎片的来源之一）
            if (!stage.firstFrame) VideoPosterCover(path, Modifier.fillMaxSize(), fit = true)

            // 整块可点：用一个不吃涟漪的透明层，横向滑动仍交给外层 Pager
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        val open = openViewer
                        if (open != null) open() else stage.onTap()
                    }
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

            if (bgmTitle != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
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

/** 视频封面放大 + 模糊当背景，避免竖屏视频周围出现整块纯黑 */
@Composable
internal fun VideoBlurBackdrop(path: String?, modifier: Modifier = Modifier) {
    val poster = rememberVideoPosterPath(path)
    Box(modifier.background(Color(0xFF0D0F12))) {
        if (poster != null) {
            LocalFileImage(
                poster,
                PhotoStore.THUMB_DIM,
                Modifier
                    .fillMaxSize()
                    .blur(24.dp),
                contentScale = ContentScale.Crop
            )
        }
        // 再压一层暗色，保证前景画面和角标小字都读得清
        Box(Modifier.fillMaxSize().background(Color(0x99000000)))
    }
}

/** 视频封面（首帧 jpg），抽不出帧时退回深色底 */
@Composable
fun VideoPosterCover(path: String?, modifier: Modifier = Modifier, fit: Boolean = false) {
    val poster = rememberVideoPosterPath(path)
    Box(modifier = modifier.background(Color(0xFF14161A))) {
        if (poster != null) {
            LocalFileImage(
                poster,
                PhotoStore.DETAIL_DIM,
                Modifier.fillMaxSize(),
                // 封面是视频首帧：必须完整显示，Crop 会把竖屏视频裁成只剩一张脸
                contentScale = if (fit) ContentScale.Fit else ContentScale.Crop
            )
        }
    }
}

@Composable
fun rememberVideoPosterPath(path: String?): String? {
    val context = LocalContext.current
    val poster by produceState<String?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            path?.takeIf { it.isNotBlank() }?.let { p ->
                MediaCache.localPath(p)?.let { VideoPoster.ensure(context, it) }
            }
        }
    }
    return poster
}

/** 本地图片文件 → 位图，按 maxDim 采样解码，解码在 IO 线程 */
@Composable
private fun LocalFileImage(
    path: String,
    maxDim: Int = PhotoStore.THUMB_DIM,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, maxDim) {
        value = withContext(Dispatchers.IO) {
            PhotoStore.decodeFile(path, maxDim)?.asImageBitmap()
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
            filterQuality = ImageSamplingHigh
        )
    } else {
        Box(modifier.background(Bg.SurfaceVariant))
    }
}

// 解码器实现已统一到 VideoPlayback.kt 的 VideoPlaybackController：
// 这里不再维护第二份 MediaPlayer 生命周期，避免两个页面的行为各自漂移。
