package com.example.my.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Ink
import com.example.my.util.ImageExporter
import com.example.my.util.MediaCache
import com.example.my.util.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 足迹全屏媒体查看器：照片与视频排在同一条 Pager 里。
 *
 * 照片支持双指缩放、双击放大/还原、保存到相册；视频打开即播，但画面轨静音，
 * 声音继续由足迹自己的配音（全局单音源）承担，所以从卡片进全屏不会有声音断裂。
 */
@Composable
fun FootprintMediaViewer(
    photos: List<String>,
    videoPaths: List<String>,
    bgmTitle: String?,
    onDismiss: () -> Unit,
    startPhotoIndex: Int? = null,
    startVideoIndex: Int? = null
) {
    val context = LocalContext.current
    val allVideos = videoPaths.distinct().filter { it.isNotBlank() }
    val resolvedVideos by produceState<List<String>>(initialValue = emptyList(), allVideos) {
        value = withContext(Dispatchers.IO) { allVideos.mapNotNull { MediaCache.localPath(it) } }
    }

    val slides = remember(photos, resolvedVideos) {
        buildList {
            photos.forEach { add(ViewerSlide.Picture(it)) }
            resolvedVideos.forEach { add(ViewerSlide.Video(it)) }
        }
    }
    if (slides.isEmpty()) return

    val initialPage = remember(resolvedVideos, startPhotoIndex, startVideoIndex, photos.size) {
        when {
            startVideoIndex != null && resolvedVideos.isNotEmpty() ->
                photos.size + startVideoIndex.coerceIn(0, resolvedVideos.lastIndex)
            startPhotoIndex != null -> startPhotoIndex.coerceIn(0, photos.lastIndex)
            else -> 0
        }
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { slides.size }
    val current = slides[pagerState.currentPage]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        var dragY by remember { mutableStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { dragY = 0f },
                        onDragCancel = { dragY = 0f }
                    ) { _, delta ->
                        dragY += delta
                        if (dragY > 110.dp.toPx()) {
                            dragY = 0f
                            onDismiss()
                        }
                    }
                }
                .graphicsLayer { translationY = dragY }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (val slide = slides[page]) {
                    is ViewerSlide.Picture -> ZoomablePicture(path = slide.path)
                    is ViewerSlide.Video -> MemoryMediaBlock(
                        videoPath = slide.path,
                        videoIsLive = false,
                        bgmPath = null,
                        bgmTitle = bgmTitle,
                        modifier = Modifier.fillMaxSize(),
                        maxHeight = 900.dp,
                        // 配音由足迹的配乐条持续播放，视频只出画面
                        muteVideo = bgmTitle != null,
                        autoPlay = true
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, null, tint = Ink.OnCover, modifier = Modifier.size(20.dp))
                }

                Text(
                    "${pagerState.currentPage + 1}/${slides.size}",
                    color = Ink.OnCover, fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x99000000))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )

                if (current is ViewerSlide.Picture) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x99000000))
                            .clickable {
                                savePicture(context, current.path)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Download, null, tint = Ink.OnCover, modifier = Modifier.size(19.dp))
                    }
                } else {
                    Box(Modifier.size(36.dp))
                }
            }

            Text(
                if (current is ViewerSlide.Picture)
                    tr("双指缩放 · 双击放大 · 下滑关闭", "Pinch to zoom · double-tap to enlarge · swipe down to close")
                else
                    tr("点画面暂停/继续 · 下滑关闭", "Tap to pause/resume · swipe down to close"),
                color = Ink.OnCoverSub, fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x99000000))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}

private sealed interface ViewerSlide {
    data class Picture(val path: String) : ViewerSlide
    data class Video(val path: String) : ViewerSlide
}

/** 原图解码 + 缩放/平移；缩放为 1 时位置归零，避免图片被拖到屏幕外 */
@Composable
private fun ZoomablePicture(path: String) {
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offsetX by remember(path) { mutableFloatStateOf(0f) }
    var offsetY by remember(path) { mutableFloatStateOf(0f) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            PhotoStore.decodeFile(path, PhotoStore.DETAIL_DIM)?.asImageBitmap()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .pointerInput(path) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val next = (scale * zoom).coerceIn(1f, 5f)
                            scale = next
                            if (next <= 1f) {
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                val maxX = size.width * (next - 1) / 2f
                                val maxY = size.height * (next - 1) / 2f
                                offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                            }
                        }
                    }
                    .pointerInput(path) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        })
                    },
                contentScale = ContentScale.Fit
            )
        }
    }
}

private fun savePicture(context: Context, path: String) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        val ok = withContext(Dispatchers.IO) {
            val bitmap = PhotoStore.decodeFile(path, PhotoStore.DETAIL_DIM)
                ?: return@withContext false
            ImageExporter.saveToGallery(context, bitmap, "足迹_${System.currentTimeMillis().toString().takeLast(8)}")
        }
        Toast.makeText(
            context,
            if (ok) tr("已保存到相册", "Saved to gallery") else tr("保存失败", "Save failed"),
            Toast.LENGTH_SHORT
        ).show()
    }
}
