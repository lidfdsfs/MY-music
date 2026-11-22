package com.example.my.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.my.model.Footprint
import com.example.my.state.AppState
import com.example.my.ui.components.AppBanner
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.FixedInk
import com.example.my.ui.theme.Text
import com.example.my.util.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.min

/** 照片挂在哪个位置：起点 / 途中 / 终点 */
private sealed interface TripSlot {
    data object Start : TripSlot
    data object Mid : TripSlot
    data object End : TripSlot
}

/**
 * 「这一程」卡片：轨迹图 + 起点 / 途中 / 终点照片。
 *
 * 两件事都只在路线型足迹上出现（[Footprint.route] 非空），因为「起点…终点」这套说法
 * 只有记录了轨迹才成立；普通打卡足迹仍然只看照片轮播。
 *
 * 照片按位置分开存：起点、终点各一张，途中可以多张（含用户在路上打的标记自带的那张）。
 * 空位给一个虚线「＋」，点了就能补——不给空位的话这三个位置永远只有自动记录时的样子。
 */
@Composable
fun TripMemoryCard(
    fp: Footprint,
    state: AppState,
    modifier: Modifier = Modifier,
    onOpenMap: (() -> Unit)? = null
) {
    if (fp.route.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    /** 正在等照片的位置（相册/相机结果回来时按它归位） */
    var askSlot by remember { mutableStateOf<TripSlot?>(null) }
    var showSource by remember { mutableStateOf(false) }
    var viewerPath by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var showExistingPicker by remember { mutableStateOf(false) }

    suspend fun attach(slot: TripSlot, uri: Uri) {
        val fileName = when (slot) {
            TripSlot.Start -> "start.jpg"
            TripSlot.End -> "end.jpg"
            TripSlot.Mid -> "mid_${System.currentTimeMillis()}.jpg"
        }
        val path = withContext(Dispatchers.IO) {
            PhotoStore.copyOne(context, fp.id, uri, fileName)
        }
        if (path == null) {
            AppBanner.show(tr("这张照片读不出来，换一张试试", "Could not read this photo"))
            return
        }
        val updated = when (slot) {
            TripSlot.Start -> fp.copy(startPhotoPath = path)
            TripSlot.End -> fp.copy(endPhotoPath = path)
            TripSlot.Mid -> fp.copy(midPhotoPaths = fp.midPhotoPaths + path)
        }
        state.updateFootprint(updated)
        AppBanner.show(tr("已记到这一程的照片里", "Photo saved"))
    }

    val album = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val slot = askSlot
        askSlot = null
        if (uri != null && slot != null) scope.launch { attach(slot, uri) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val slot = askSlot
        val uri = cameraUri
        askSlot = null
        cameraUri = null
        if (ok && uri != null && slot != null) scope.launch { attach(slot, uri) }
    }

    fun pick(slot: TripSlot) {
        askSlot = slot
        showSource = true
    }

    fun removePhoto(path: String) {
        val updated = fp.copy(
            startPhotoPath = fp.startPhotoPath?.takeIf { it != path },
            endPhotoPath = fp.endPhotoPath?.takeIf { it != path },
            midPhotoPaths = fp.midPhotoPaths.filterNot { it == path },
            // 标记照片也是「途中照片」，移除只摘掉照片，标记点本身保留
            marks = fp.marks.map { if (it.photoPath == path) it.copy(photoPath = null) else it }
        )
        state.updateFootprint(updated)
        runCatching { File(path).delete() }
        AppBanner.show(tr("已移除这张照片", "Photo removed"))
    }

    val markPhotos = fp.marks.mapNotNull { it.photoPath }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Bg.SurfaceVariant)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("这一程", "This route"), color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (onOpenMap != null) {
                Text(
                    tr("在地图看 ›", "Open map ›"),
                    color = Brand.Terracotta,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onOpenMap)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        RouteTrackPreview(fp, Modifier.fillMaxWidth().height(118.dp))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(Brand.Terracotta, tr("起点", "Start"))
            Spacer(Modifier.width(10.dp))
            LegendDot(Color(0xFF2F6F4E), tr("终点", "End"))
            Spacer(Modifier.width(10.dp))
            if (fp.marks.isNotEmpty()) {
                LegendDot(Brand.Gold, tr("标记 ${fp.marks.size}", "${fp.marks.size} marks"))
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("路上照片", "Along the way"), color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                tr("点 ＋ 补一张，点照片可看大图/移除", "Tap + to add, tap a photo to view"),
                color = Text.Muted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TripPhotoTile(
                label = tr("起点", "Start"),
                path = fp.startPhotoPath,
                onPick = { pick(TripSlot.Start) },
                onView = { p -> viewerPath = p }
            )
            Spacer(Modifier.width(8.dp))
            markPhotos.forEachIndexed { index, path ->
                TripPhotoTile(
                    label = if (fp.marks.size > 1) tr("途中 ${index + 1}", "Stop ${index + 1}") else tr("途中", "Stop"),
                    path = path,
                    onPick = { pick(TripSlot.Mid) },
                    onView = { p -> viewerPath = p }
                )
                Spacer(Modifier.width(8.dp))
            }
            fp.midPhotoPaths.forEach { path ->
                TripPhotoTile(
                    label = tr("途中", "Stop"),
                    path = path,
                    onPick = { pick(TripSlot.Mid) },
                    onView = { p -> viewerPath = p }
                )
                Spacer(Modifier.width(8.dp))
            }
            // 途中永远留一个空位，方便继续加（标记带的不算，用户可以另加）
            if (markPhotos.isEmpty() && fp.midPhotoPaths.isEmpty()) {
                TripPhotoTile(
                    label = tr("途中", "Stop"),
                    path = null,
                    onPick = { pick(TripSlot.Mid) },
                    onView = {}
                )
                Spacer(Modifier.width(8.dp))
            }
            TripPhotoTile(
                label = tr("终点", "End"),
                path = fp.endPhotoPath,
                onPick = { pick(TripSlot.End) },
                onView = { p -> viewerPath = p }
            )
        }
    }

    if (showSource) {
        TripPhotoSourceSheet(
            hasExisting = fp.photoPaths.isNotEmpty(),
            onDismiss = { showSource = false },
            onAlbum = {
                showSource = false
                album.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onCamera = {
                showSource = false
                val dir = File(context.cacheDir, "camera").apply { runCatching { mkdirs() } }
                val file = File(dir, "trip_${System.currentTimeMillis()}.jpg")
                val uri = runCatching {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }.getOrNull()
                if (uri == null) {
                    AppBanner.show(tr("拍照失败，这台机器没有可用的相机", "Camera unavailable"))
                } else {
                    cameraUri = uri
                    camera.launch(uri)
                }
            },
            onExisting = if (fp.photoPaths.isNotEmpty()) {
                {
                    showSource = false
                    showExistingPicker = true
                }
            } else null
        )
    }

    // 从这条足迹已有的照片里挑一张：不用再翻一遍相册，也避免同一张图被复制成两份
    if (showExistingPicker) {
        ExistingPhotoPicker(
            photos = fp.photoPaths,
            onDismiss = { showExistingPicker = false },
            onPick = { existing ->
                showExistingPicker = false
                val slot = askSlot
                askSlot = null
                if (slot != null) {
                    val updated = when (slot) {
                        TripSlot.Start -> fp.copy(startPhotoPath = existing)
                        TripSlot.End -> fp.copy(endPhotoPath = existing)
                        TripSlot.Mid -> fp.copy(
                            midPhotoPaths = if (existing in fp.midPhotoPaths) fp.midPhotoPaths
                            else fp.midPhotoPaths + existing
                        )
                    }
                    state.updateFootprint(updated)
                    AppBanner.show(tr("已归位", "Saved"))
                }
            }
        )
    }

    viewerPath?.let { path ->
        TripPhotoViewer(
            path = path,
            onClose = { viewerPath = null },
            onRemove = {
                viewerPath = null
                removePhoto(path)
            }
        )
    }
}

/**
 * 轨迹图：把整条轨迹按真实比例（经度乘 cos(纬度)）等比缩放进一块画布。
 *
 * 不用静态地图图：那需要联网取图、要 Key、还会把离线状态暴露成一片灰。
 * 这里只画形状，起终点与标记点用三个色点区分，缩略图该有的信息都有。
 */
@Composable
private fun RouteTrackPreview(fp: Footprint, modifier: Modifier = Modifier) {
    val points = fp.route
    if (points.isEmpty()) return

    val meansLat = points.map { it.lat }.average()
    val lonScale = cos(Math.toRadians(meansLat)).coerceAtLeast(0.2)

    val xs = points.map { it.lon * lonScale }
    val ys = points.map { it.lat }
    val minX = xs.min()
    val maxX = xs.max()
    val minY = ys.min()
    val maxY = ys.max()
    val spanX = (maxX - minX).coerceAtLeast(1e-4)
    val spanY = (maxY - minY).coerceAtLeast(1e-4)
    val midX = (minX + maxX) / 2.0
    val midY = (minY + maxY) / 2.0

    val gridColor = Bg.Outline.copy(alpha = 0.45f)
    val routeColor = Brand.Terracotta
    val endColor = Color(0xFF2F6F4E)
    val markColor = Brand.Gold

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // 网格：给一块「地图感」的底，不至于是一张空白卡
        val rows = 3
        val cols = 5
        for (i in 1 until rows) {
            val y = h * i / rows
            drawLine(gridColor, Offset(0f, y), Offset(w, y), 1f)
        }
        for (i in 1 until cols) {
            val x = w * i / cols
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), 1f)
        }

        val pad = 18f
        val usableW = (w - pad * 2).coerceAtLeast(1f)
        val usableH = (h - pad * 2).coerceAtLeast(1f)
        val scale = min(usableW / spanX, usableH / spanY)
        val centerX = w / 2f
        val centerY = h / 2f

        fun px(lon: Double, lat: Double): Offset = Offset(
            x = (centerX + ((lon * lonScale) - midX) * scale).toFloat(),
            y = (centerY - (lat - midY) * scale).toFloat()
        )

        val coords = points.map { px(it.lon, it.lat) }
        if (coords.size > 1) {
            val path = Path().apply {
                moveTo(coords.first().x, coords.first().y)
                coords.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, routeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))
        }

        // 标记点先画，避免压住起终点
        fp.marks.forEach { mark ->
            drawCircle(markColor, radius = 2.6.dp.toPx(), center = px(mark.lon, mark.lat))
        }

        drawCircle(Color.White, radius = 5.2.dp.toPx(), center = coords.first())
        drawCircle(routeColor, radius = 5.2.dp.toPx(), center = coords.first(), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx()))
        drawCircle(endColor, radius = 4.6.dp.toPx(), center = coords.last())
        drawCircle(Color.White, radius = 1.8.dp.toPx(), center = coords.last())
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Text.Secondary, fontSize = 10.sp)
    }
}

@Composable
private fun TripPhotoTile(
    label: String,
    path: String?,
    onPick: () -> Unit,
    onView: (String) -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(shape)
            .then(
                if (path == null) {
                    Modifier
                        .background(Bg.Surface)
                        // 虚线不太好画，用一圈很浅的描边代替：空位要能一眼看出「这里可以补一张」
                        .border(1.dp, Bg.Outline, shape)
                        .clickable(onClick = onPick)
                } else {
                    Modifier.clickable { onView(path) }
                }
            )
    ) {
        if (path == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = tr("添加照片", "Add photo"),
                    tint = Text.Muted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.height(3.dp))
                Text("$label", color = Text.Muted, fontSize = 10.sp)
            }
        } else {
            TripThumb(path, Modifier.fillMaxSize())
        }
        // 位置标签压在左上角：起点/途中/终点一眼分得清
        Box(
            modifier = Modifier
                .padding(5.dp)
                .clip(RoundedCornerShape(50))
                .background(if (path == null) Color.Transparent else FixedInk.ChipSurface)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                label,
                color = if (path == null) Color.Transparent else Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TripPhotoSourceSheet(
    hasExisting: Boolean,
    onDismiss: () -> Unit,
    onAlbum: () -> Unit,
    onCamera: () -> Unit,
    onExisting: (() -> Unit)?
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
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
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {}
            ) {
                Text(
                    tr("添加照片", "Add photo"),
                    color = Text.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tr("选好之后这张就归到对应的位置，不再进轮播", "It is pinned to that spot, not the carousel"),
                    color = Text.Muted,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(10.dp))
                PhotoSourceRow(Icons.Outlined.PhotoLibrary, tr("从相册选择", "Choose from album"), onAlbum)
                PhotoSourceRow(Icons.Outlined.PhotoCamera, tr("现场拍一张", "Take a photo"), onCamera)
                if (onExisting != null) {
                    PhotoSourceRow(
                        Icons.Outlined.Image,
                        tr("用这条足迹里已有的照片", "Use an existing photo"),
                        onExisting
                    )
                }
                if (!hasExisting) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tr("这条足迹还没有别的照片可以复用", "No other photos in this footprint"),
                        color = Text.Muted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoSourceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Text.Secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Text.Primary, fontSize = 13.sp)
    }
}

/** 从这条足迹已有的照片里挑一张：省一次相册翻找，也不会把同一张图复制成两份占空间 */
@Composable
private fun ExistingPhotoPicker(
    photos: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
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
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {}
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("选一张已有的照片", "Pick an existing photo"),
                        color = Text.Primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        tr("关闭", "Close"),
                        color = Text.Secondary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    photos.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { path ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onPick(path) }
                                ) {
                                    TripThumb(path, Modifier.fillMaxSize())
                                }
                            }
                            repeat(3 - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripThumb(path: String, modifier: Modifier = Modifier) {
    val bmp by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { PhotoStore.decodeFile(path, 260)?.asImageBitmap() }
    }
    Box(modifier.background(Bg.SurfaceVariant)) {
        bmp?.let {
            Image(
                bitmap = it,
                contentDescription = tr("照片", "Photo"),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun TripPhotoViewer(path: String, onClose: () -> Unit, onRemove: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { PhotoStore.decodeFile(path, 1600)?.asImageBitmap() }
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xF2000000)), contentAlignment = Alignment.Center) {
            bmp?.let {
                Image(
                    bitmap = it,
                    contentDescription = tr("照片", "Photo"),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(12.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF23262B))
                        .clickable(onClick = onRemove)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(tr("移除", "Remove"), color = Color.White, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF23262B))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, tr("关闭", "Close"), tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
