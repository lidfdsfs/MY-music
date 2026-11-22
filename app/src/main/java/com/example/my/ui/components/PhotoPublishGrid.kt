package com.example.my.ui.components

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Func
// 主题里的 Text 是颜色 token 对象，与 material3 的 Text 组件同名，起别名避免歧义
import com.example.my.ui.theme.Text as ThemeText
import com.example.my.util.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 系统多选面板一次最多 100 张；加入足迹后不设总数上限，可反复追加 */
const val MAX_PUBLISH_PHOTOS = 100

/**
 * 发布页的九宫格照片编辑区。
 *
 * 为什么不用「一行横滑 + 只显示前 3 张」：
 * 之前选了 4 张以后，第 4 张开始用户既看不到也删不掉，保存后才发现多带了图。
 * 这里按常见社交 App 的排布走：
 *  - 0 张：整块虚线「添加」区域；
 *  - 1 张：单张大图（16:9 偏方），下方接一个小加号；
 *  - 2 张：左右各半；
 *  - 3 张及以上：一行三列的正方格子，与相册九宫格一致。
 * 点击看大图，右上角 × 删除，第一张标「封面」。
 */
@Composable
fun PhotoGridEditor(
    photos: List<Uri>,
    maxCount: Int,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onPreview: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** 视频 / 实况：跟照片混在同一个九宫格里，不再单独挂一张大黑卡 */
    videoPath: String? = null,
    videoIsLive: Boolean = false,
    videoSeconds: Int = 0,
    onRemoveVideo: (() -> Unit)? = null,
    onPreviewVideo: (() -> Unit)? = null
) {
    val gap = 8.dp
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val unit = (maxWidth - gap * 2) / 3
        val hasVideo = videoPath != null
        if (photos.isEmpty() && !hasVideo) {
            EmptyAddZone(
                width = maxWidth,
                height = 124.dp,
                maxCount = maxCount,
                onClick = onAdd
            )
            return@BoxWithConstraints
        }

        // 视频算一格，和照片一起决定列数与格子尺寸
        val count = photos.size + if (hasVideo) 1 else 0
        val columns = when {
            count <= 1 -> 1
            count == 2 -> 2
            else -> 3
        }
        val cellWidth = when {
            count <= 1 -> maxWidth
            count == 2 -> (maxWidth - gap) / 2
            else -> unit
        }
        // 单格时给 3:2 的横图比例，避免一屏被一张照片占满
        val cellHeight = if (count == 1) cellWidth * 0.68f else cellWidth

        val cells = ArrayList<CellSpec>(count + 1)
        photos.forEachIndexed { i, uri ->
            cells.add(CellSpec.Photo(i, uri, cellWidth, cellHeight))
        }
        videoPath?.let { cells.add(CellSpec.Video(it, videoIsLive, videoSeconds, cellWidth, cellHeight)) }
        if (photos.size < maxCount) {
            cells.add(CellSpec.Add(unit, unit))
        }

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            cells.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { cell ->
                        when (cell) {
                            is CellSpec.Photo -> PhotoCell(
                                uri = cell.uri,
                                isCover = cell.index == 0,
                                width = cell.width,
                                height = cell.height,
                                onRemove = { onRemove(cell.index) },
                                onOpen = { onPreview(cell.index) }
                            )
                            is CellSpec.Video -> VideoCell(
                                path = cell.path,
                                isLive = cell.isLive,
                                seconds = cell.seconds,
                                width = cell.width,
                                height = cell.height,
                                onRemove = onRemoveVideo,
                                onOpen = onPreviewVideo
                            )
                            is CellSpec.Add -> AddCell(
                                width = cell.width,
                                height = cell.height,
                                onClick = onAdd
                            )
                        }
                    }
                    // 补齐空位，保证每格宽度一致（否则最后一行会被拉宽）
                    if (row.size < columns) {
                        repeat(columns - row.size) { Spacer(Modifier.width(unit)) }
                    }
                }
            }
        }
    }
}

private sealed interface CellSpec {
    data class Photo(val index: Int, val uri: Uri, val width: Dp, val height: Dp) : CellSpec
    data class Video(
        val path: String,
        val isLive: Boolean,
        val seconds: Int,
        val width: Dp,
        val height: Dp
    ) : CellSpec
    data class Add(val width: Dp, val height: Dp) : CellSpec
}

/** 视频 / 实况格子：用首帧封面当缩略图，和照片一样大小，一眼能看出是同一段旅程的素材 */
@Composable
private fun VideoCell(
    path: String,
    isLive: Boolean,
    seconds: Int,
    width: Dp,
    height: Dp,
    onRemove: (() -> Unit)?,
    onOpen: (() -> Unit)?
) {
    val poster = rememberVideoPosterPath(path)
    Box(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF14161A))
            .clickable(enabled = onOpen != null) { onOpen?.invoke() }
    ) {
        val posterPath = poster
        if (posterPath != null) {
            val bmp by produceState<ImageBitmap?>(initialValue = null, posterPath) {
                value = withContext(Dispatchers.IO) {
                    PhotoStore.decodeFile(posterPath, PhotoStore.THUMB_DIM)?.asImageBitmap()
                }
            }
            bmp?.let {
                Image(
                    bitmap = it,
                    contentDescription = tr("已加入的视频", "Added video"),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // 视频 / 实况标签：照片不标，视频必须标清楚
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.58f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                if (isLive) tr("实况", "Live") else tr("视频", "Video"),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        if (seconds > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.58f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    "0:${seconds.toString().padStart(2, '0')}",
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }

        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.48f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = tr("移除这段视频", "Remove video"),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun PhotoCell(
    uri: Uri,
    isCover: Boolean,
    width: Dp,
    height: Dp,
    onRemove: () -> Unit,
    onOpen: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 按下时轻微内缩：手指按到哪张照片一目了然，也顺手给了「可点」的暗示
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "photoCellPress")
    val thumb = rememberGridThumb(uri)

    Box(
        modifier = Modifier
            .size(width, height)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(Bg.SurfaceVariant)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onOpen
            )
    ) {
        val bmp = thumb
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = tr("已选照片", "Selected photo"),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // 解码中：给一个安静的底色，不要闪白
            Box(Modifier.fillMaxSize().background(Bg.SurfaceVariant))
        }

        if (isCover) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brand.Terracotta.copy(alpha = 0.92f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    tr("封面", "Cover"),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.48f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = tr("移除这张照片", "Remove photo"),
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun AddCell(width: Dp, height: Dp, onClick: () -> Unit) {
    val dashColor = Brand.Terracotta.copy(alpha = 0.42f)
    Box(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(16.dp))
            .background(Brand.Terracotta.copy(alpha = 0.04f))
            .drawBehind {
                drawRoundRect(
                    color = dashColor,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(7.dp.toPx(), 5.dp.toPx())
                        )
                    )
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = Brand.Terracotta,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                tr("添加", "Add"),
                color = Brand.Terracotta,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptyAddZone(width: Dp, height: Dp, maxCount: Int, onClick: () -> Unit) {
    val dashColor = Brand.Terracotta.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(18.dp))
            .background(Brand.Terracotta.copy(alpha = 0.04f))
            .drawBehind {
                drawRoundRect(
                    color = dashColor,
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(8.dp.toPx(), 6.dp.toPx())
                        )
                    )
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = Brand.Terracotta,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                tr("添加旅行照片", "Add travel photos"),
                color = Brand.Terracotta,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                tr("照片不设总数上限，可分批继续加；视频可一次多选，也能之后继续追加",
                    "Photos have no total cap; pick in batches. Videos can be multi-selected and appended any time"),
                color = ThemeText.Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun rememberGridThumb(uri: Uri): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            // 格子只有 1/3 屏宽，按 360px 解码足够清晰，9 张全在内存里也不到 5MB
            PhotoStore.decodeUri(context, uri, 360)?.asImageBitmap()
        }
    }.value
}

/**
 * 全屏看图：左右滑动切换、底部设为封面 / 删除。
 *
 * 发布前用「大图 + 明确的操作按钮」比在格子上堆图标更稳：格子只有几十 dp，
 * 手指容易点错，一旦误删就要重新选图。
 */
@Composable
fun PhotoViewerDialog(
    photos: List<Uri>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onDelete: (Int) -> Unit,
    onSetCover: (Int) -> Unit
) {
    if (photos.isEmpty()) return
    val countState = rememberUpdatedState(photos.size)
    val start = startIndex.coerceIn(0, photos.lastIndex)
    val pagerState = rememberPagerState(initialPage = start) { countState.value }
    val scope = rememberCoroutineScope()

    // 删掉当前这张后总数变少，索引可能越界，回退到新的最后一张
    LaunchedEffect(photos.size) {
        if (photos.isNotEmpty() && pagerState.currentPage > photos.lastIndex) {
            pagerState.scrollToPage(photos.lastIndex)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val uri = photos.getOrNull(page) ?: return@HorizontalPager
                ViewerImage(uri = uri, onTap = onDismiss)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIconButton(Icons.Filled.Close, tr("关闭", "Close"), onDismiss)
                Spacer(Modifier.weight(1f))
                Text(
                    "${pagerState.currentPage + 1} / ${photos.size}",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                // 与左侧关闭键等宽，保证计数真正居中
                Spacer(Modifier.size(40.dp))
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                val isCover = pagerState.currentPage == 0
                ActionPill(
                    icon = Icons.Filled.Star,
                    text = if (isCover) tr("当前封面", "Cover") else tr("设为封面", "Set cover"),
                    enabled = !isCover,
                    onClick = {
                        val page = pagerState.currentPage
                        onSetCover(page)
                        // 设为封面 = 把它挪到第一张，镜头跟着回到第一页，
                        // 用户能立刻看到「它就是封面」的结果
                        scope.launch { pagerState.scrollToPage(0) }
                    }
                )
                Spacer(Modifier.width(12.dp))
                ActionPill(
                    icon = Icons.Outlined.DeleteOutline,
                    text = tr("删除", "Delete"),
                    danger = true,
                    onClick = { onDelete(pagerState.currentPage) }
                )
            }
        }
    }
}

@Composable
private fun ViewerImage(uri: Uri, onTap: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            PhotoStore.decodeUri(context, uri, 1600)?.asImageBitmap()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = tr("照片大图", "Photo"),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.7f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 添加照片的来源选择：从相册选择 / 拍照。
 *
 * 直接用相册选择器也能用，但旅行途中「现场拍一张」是很高频的动作，
 * 少走一层系统相册能省下几秒 —— 这也是主流发布页的通行做法。
 */
@Composable
fun PhotoSourceSheet(
    onDismiss: () -> Unit,
    onAlbum: () -> Unit,
    onCamera: () -> Unit,
    /** 视频 / 实况也和照片走同一个入口，不再单独占一块 */
    onMedia: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Bg.Surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    // 面板自己吃掉点击，避免穿透到遮罩直接关闭
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {}
            ) {
                Text(
                    tr("添加照片", "Add photos"),
                    color = ThemeText.Primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                SourceRow(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = tr("从相册选择", "Choose from gallery"),
                    subtitle = tr("一次最多可选 $MAX_PUBLISH_PHOTOS 张",
                        "Up to $MAX_PUBLISH_PHOTOS at a time"),
                    onClick = onAlbum
                )
                SourceRow(
                    icon = Icons.Outlined.PhotoCamera,
                    title = tr("拍照", "Take a photo"),
                    subtitle = tr("用系统相机拍一张", "Shot with the system camera"),
                    onClick = onCamera
                )
                if (onMedia != null) {
                    SourceRow(
                        icon = Icons.Outlined.Movie,
                        title = tr("视频 / 实况", "Video / Live Photo"),
                        subtitle = tr("可一次多选 / 之后继续追加，自动处理成抖音规格",
                            "Multi-select or append anytime, normalized automatically"),
                        onClick = onMedia
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Bg.Outline)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tr("取消", "Cancel"), color = ThemeText.Secondary, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun SourceRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brand.Terracotta.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Brand.Terracotta, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ThemeText.Primary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                color = ThemeText.Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    text: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = when {
        !enabled -> Color.White.copy(alpha = 0.38f)
        danger -> Func.Danger
        else -> Color.White
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (enabled) 0.16f else 0.06f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
