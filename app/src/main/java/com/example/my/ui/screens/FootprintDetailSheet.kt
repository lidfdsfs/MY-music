package com.example.my.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.my.model.Footprint
import com.example.my.data.AiApi
import com.example.my.data.AiFeature
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.my.ui.components.AiInsightCard
import com.example.my.ui.components.FootprintMediaPager
import com.example.my.ui.components.FootprintMediaViewer
import com.example.my.ui.components.BgmPickerSheet
import com.example.my.ui.components.MemoryMediaBlock
import com.example.my.ui.components.MusicBgmStrip
import com.example.my.ui.interaction.PhotoDrawerSheet
import com.example.my.ui.interaction.TearOffPanel
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Func
import com.example.my.ui.theme.Text
import com.example.my.util.FootprintDate
import com.example.my.util.MediaCompactor
import com.example.my.util.CompactResult
import com.example.my.util.PhotoStore
import com.example.my.util.cleanNote
import com.example.my.state.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.my.ui.i18n.tr

/**
 * 足迹详情：照片抽屉面板（照片铺满上半屏，面板升起压住照片下沿，
 * 上拖面板照片同步收窄），面板内含地点 / 日期 / 备注 / 特别标记与撕线票根。
 * 地图页与时间轴共用同一份实现，原来地图页里的私有版本可逐步替换。
 */
@Composable
fun FootprintDetailSheet(fp: Footprint, onDismiss: () -> Unit, state: AppState) {
    BackHandler { onDismiss() }
    var editing by remember { mutableStateOf(false) }
    var showInsightModelPicker by remember { mutableStateOf(false) }
    var photoIndex by remember { mutableIntStateOf(0) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    /** 点视频卡片进全屏查看器；与照片下标互斥，避免同时弹两层 */
    var viewerVideoIndex by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Func.Scrim)
            .clickable(enabled = true, onClick = onDismiss)
    ) {
        PhotoDrawerSheet(
            modifier = Modifier
                .fillMaxSize()
                // 拦截内容区点击，避免穿透到遮罩误关闭
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {},
            onPhotoPullDown = if (fp.photoPaths.isNotEmpty()) {
                { viewerIndex = photoIndex.coerceIn(0, fp.photoPaths.lastIndex) }
            } else null,
            photo = {
                // 照片、视频、实况排成一整条相册：视频不再是标题下面那块孤立黑块
                FootprintMediaPager(
                    photos = fp.photoPaths,
                    videoPath = fp.videoPath,
                    videoIsLive = fp.videoIsLive,
                    bgmPath = fp.bgmPath,
                    bgmTitle = fp.bgmTitle,
                    modifier = Modifier.fillMaxSize(),
                    onTapPhoto = { index ->
                        viewerVideoIndex = null
                        viewerIndex = index
                    },
                    onTapVideo = { index ->
                        viewerIndex = null
                        viewerVideoIndex = index
                    },
                    onSettledPage = { page ->
                        // 只记照片页的下标，拉到视频页时大图查看器不该被带回越界下标
                        if (page in fp.photoPaths.indices) photoIndex = page
                    },
                    videoPaths = fp.videoPaths.ifEmpty { listOfNotNull(fp.videoPath) }
                )
            }
        ) {
            // 面板内容（可滚动，底部留导航栏间距）
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 标题必须吃满剩余宽度：之前写成 weight(1f, fill = false) + 再来一个 weight(1f) 的 Spacer，
                    // 空出来的半行没人认领，右侧「编辑 ×」就被顶到屏幕中间了
                    Text(
                        fp.name,
                        color = Text.Primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (fp.special) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Brand.Gold.copy(alpha = 0.22f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Star, contentDescription = null, tint = Brand.Gold, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(tr("特别"), color = Brand.Terracotta, fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        tr("编辑"),
                        color = Text.Secondary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { editing = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Close, tr("关闭"), tint = Text.Secondary, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    listOf(tr(fp.city), fp.date).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Text.Secondary, fontSize = 13.sp
                )
                // 配乐条对所有足迹生效：配音是主声音，视频只播画面（静音），
                // 所以这里始终自动起播，不再因为足迹里带视频就让配乐等在那里。
                if (fp.bgmPath != null && java.io.File(fp.bgmPath).exists()) {
                    Spacer(Modifier.height(14.dp))
                    MusicBgmStrip(
                        path = fp.bgmPath,
                        title = fp.bgmTitle.orEmpty(),
                        artist = fp.bgmArtist,
                        autoplay = true
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr(
                            "左右滑动跳到播放时间 · 点一下暂停/继续 · 长按换样式",
                            "Drag to seek · tap to pause/resume · hold to change style"
                        ),
                        color = Text.Muted, fontSize = 10.sp
                    )
                }
                val noteText = cleanNote(fp.note)
                if (noteText.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(noteText, color = Text.Primary, fontSize = 14.sp, lineHeight = 22.sp)
                }
                fp.specialReason?.let { reason ->
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brand.Gold.copy(alpha = 0.14f))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Star, contentDescription = null, tint = Brand.Gold, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(reason, color = Text.Secondary, fontSize = 12.sp)
                        }
                    }
                }
                // 路线型足迹：起点、终点与运动数据（自动记录结束保存的那一条）
                if (fp.route.isNotEmpty()) {
                    val timeFmt = SimpleDateFormat("HH:mm", Locale.CHINA)
                    val startPoint = fp.route.first()
                    val endPoint = fp.route.last()
                    Spacer(Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Bg.SurfaceVariant)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tr("起点"), color = Text.Muted, fontSize = 10.sp)
                                Text(
                                    timeFmt.format(Date(startPoint.time)),
                                    color = Text.Primary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "%.5f, %.5f".format(startPoint.lat, startPoint.lon),
                                    color = Text.Muted,
                                    fontSize = 9.sp
                                )
                            }
                            Text("→", color = Text.Muted, fontSize = 14.sp)
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(tr("终点"), color = Text.Muted, fontSize = 10.sp)
                                Text(
                                    timeFmt.format(Date(endPoint.time)),
                                    color = Text.Primary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "%.5f, %.5f".format(endPoint.lat, endPoint.lon),
                                    color = Text.Muted,
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row {
                            listOf(
                                tr("距离") to "%.2f km".format(fp.distanceKm),
                                tr("时长") to formatRouteDuration(fp.durationSec),
                                tr("标记") to "${fp.marks.size} 处"
                            ).forEach { (label, value) ->
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, color = Text.Muted, fontSize = 10.sp)
                                    Text(
                                        value,
                                        color = Text.Primary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                    // 这一程：轨迹缩略图 + 起点 / 途中 / 终点照片（补照片会影响这条记录的存储）
                    Spacer(Modifier.height(10.dp))
                    TripMemoryCard(fp = fp, state = state)
                    // 途中标记：备注 + 是否有照片（照片本身在图库里按顺序可翻）
                    if (fp.marks.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        fp.marks.forEachIndexed { index, mark ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Brand.Terracotta.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${index + 1}",
                                        color = Brand.Terracotta,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    mark.note,
                                    color = Text.Primary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (mark.photoPath != null) {
                                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = Text.Muted, modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                // 每条足迹都让 AI 根据已有记录做一次总结：不打分、不评价，只归纳事实
                AiInsightCard(
                    kind = "足迹",
                    title = listOf(fp.name, fp.city).filter { it.isNotBlank() }.joinToString(" · "),
                    facts = buildString {
                        append("城市：").append(fp.city).append("；")
                        append("地点：").append(fp.name).append("；")
                        append("日期：").append(fp.date).append("；")
                        if (noteText.isNotBlank()) append("备注：").append(noteText).append("；")
                        if (fp.special) append("用户把这里标为特别之地")
                        fp.specialReason?.let { append("，原因：").append(it) }
                        if (fp.distanceKm > 0) {
                            append("；路线距离：").append("%.2f 公里".format(fp.distanceKm))
                        }
                        if (fp.durationSec > 0) {
                            append("；路线时长：").append(formatRouteDuration(fp.durationSec))
                        }
                        if (fp.marks.isNotEmpty()) {
                            append("；沿途标记：")
                            append(fp.marks.joinToString("；") { it.note.ifBlank { "未命名标记" } })
                        }
                        if (fp.photoPaths.isNotEmpty()) {
                            append("；照片数量：").append(fp.photoPaths.size).append(" 张")
                        }
                    },
                    endpoint = state.aiFeatureEndpoint(com.example.my.data.AiFeature.INSIGHT),
                    context = LocalContext.current,
                    useLocal = state.aiFeatureUsesLocal(com.example.my.data.AiFeature.INSIGHT),
                    localModelId = state.aiFeatureLocalModelId(com.example.my.data.AiFeature.INSIGHT),
                    onModelLongPress = { showInsightModelPicker = true }
                )
                Spacer(Modifier.height(18.dp))
                // 撕线票根：下拉底边沿虚线撕开露票码，松手回弹合上
                TearOffPanel(
                    code = "NO.%06d".format(fp.id % 1000000),
                    notchColor = Bg.Surface
                )
                Spacer(Modifier.height(16.dp))
            }
        }
        if (editing) {
            FootprintEditorSheet(
                footprint = fp,
                state = state,
                onDismiss = { editing = false }
            )
        }
        if (viewerIndex != null || viewerVideoIndex != null) {
            FootprintMediaViewer(
                photos = fp.photoPaths,
                videoPaths = fp.videoPaths.ifEmpty { listOfNotNull(fp.videoPath) },
                bgmTitle = fp.bgmTitle,
                onDismiss = {
                    viewerIndex = null
                    viewerVideoIndex = null
                },
                startPhotoIndex = viewerIndex,
                startVideoIndex = viewerVideoIndex
            )
        }
        if (showInsightModelPicker) {
            AiModelQuickSwitchDialog(
                feature = com.example.my.data.AiFeature.INSIGHT,
                state = state,
                onDismiss = { showInsightModelPicker = false }
            )
        }
    }
}

@Composable
private fun FootprintEditorSheet(
    footprint: Footprint,
    state: AppState,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember(footprint) { mutableStateOf(footprint.name) }
    var city by remember(footprint) { mutableStateOf(footprint.city) }
    var date by remember(footprint) { mutableStateOf(footprint.date) }
    var note by remember(footprint) { mutableStateOf(footprint.note) }
    var special by remember(footprint) { mutableStateOf(footprint.special) }
    var specialReason by remember(footprint) { mutableStateOf(footprint.specialReason.orEmpty()) }
    var photos by remember(footprint) { mutableStateOf(footprint.photoPaths) }
    var replaceIndex by remember { mutableStateOf(-1) }
    var aiBusy by remember(footprint) { mutableStateOf(false) }

    // ---------- 已存在的足迹：编辑时补媒体 ----------
    // 视频 / 实况 / 配乐以前只能在「新建足迹」时选，存下来之后想补就只能重传一条。
    // 这里让编辑面板直接接收这三类素材：选完立刻搬进这条足迹自己的目录，
    // 保存时写回 footprint，和新建那条走的是同一套落盘方式。
    var videoPath by remember(footprint) { mutableStateOf(footprint.videoPath) }
    var videoPaths by remember(footprint) {
        mutableStateOf(footprint.videoPaths.ifEmpty { listOfNotNull(footprint.videoPath) })
    }
    var videoIsLive by remember(footprint) { mutableStateOf(footprint.videoIsLive) }
    var videoBytesSaved by remember(footprint) { mutableStateOf(footprint.videoBytes) }
    var bgmPath by remember(footprint) { mutableStateOf(footprint.bgmPath) }
    var bgmTitle by remember(footprint) { mutableStateOf(footprint.bgmTitle) }
    var bgmArtist by remember(footprint) { mutableStateOf(footprint.bgmArtist) }
    var bgmSource by remember(footprint) { mutableStateOf(footprint.bgmSource) }
    var mediaBusy by remember { mutableStateOf(false) }
    var showBgmSheet by remember { mutableStateOf(false) }

    /** 压缩 / 抠实况时的中间文件放缓存目录，成品再搬进足迹目录 */
    fun mediaScratch(): java.io.File =
        java.io.File(context.cacheDir, "media_in").apply { mkdirs() }

    fun attachVideo(uri: Uri, live: Boolean) {
        if (mediaBusy) return
        scope.launch {
            mediaBusy = true
            val saved = withContext(Dispatchers.IO) {
                val dir = mediaScratch()
                val clip: CompactResult? = if (live) {
                    // 实况：先把整张 JPEG 落下来，再从尾部抠出内嵌的那段视频
                    val jpeg = java.io.File(dir, "live_src_${System.nanoTime()}.jpg")
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            jpeg.outputStream().use { out -> input.copyTo(out) }
                        }
                    }
                    val extracted = MediaCompactor.extractMotionPhoto(jpeg, dir, "${System.nanoTime()}")
                    jpeg.delete()
                    extracted?.let { src ->
                        MediaCompactor.compact(context, src, dir, "live${System.nanoTime()}")
                            .also { src.delete() }
                    }
                } else {
                    MediaCompactor.importVideo(context, uri, dir, "editv${System.nanoTime()}")
                }
                clip?.let { result ->
                    val path = PhotoStore.copyMedia(context, footprint.id, result.file, "media")
                    result.file.delete()
                    path?.let { it to result.bytes }
                }
            }
            mediaBusy = false
            if (saved == null) {
                state.toast(
                    // iOS / vivo 的实况是「图 + 视频」两个文件，编辑面板只拿得到一个 Uri，
                    // 这时唯一可执行的指引是用「添加视频」直接选那段视频，别再让用户无从下手。
                    if (live) tr(
                        "这张照片里没有可用的内嵌视频；iOS / vivo 的实况是「图 + 视频」两个文件，请用「添加视频」直接选那段视频",
                        "No usable embedded video. iOS / vivo Live Photos are an image + a separate video — use Add video to pick that clip",
                    )
                    else tr("视频添加失败，请换一段再试", "Could not add this video, try another")
                )
                return@launch
            }
            videoPath = saved.first
            videoPaths = (videoPaths + saved.first).distinct()
            videoIsLive = live
            videoBytesSaved = videoBytesSaved + saved.second
            state.toast(if (live) tr("实况已加入", "Live photo added") else tr("视频已加入", "Video added"))
        }
    }

    val videoAddPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { attachVideo(it, live = false) }
    }
    val liveAddPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { attachVideo(it, live = true) }
    }

    // AI 一键整理实际走的模型：端侧本机模型优先，否则是当前分配到的云端端点
    val aiFeature = AiFeature.FOOTPRINT_SUMMARY
    val aiEndpoint = state.aiFeatureEndpoint(aiFeature)
    val aiUsesLocal = state.aiFeatureUsesLocal(aiFeature)
    val aiLocalId = state.aiFeatureLocalModelId(aiFeature)
    val aiModelReady = aiUsesLocal || aiEndpoint.model.isNotBlank()
    val aiModelLabel = when {
        aiUsesLocal -> tr("本机端侧 · ${aiLocalId ?: "已启用模型"}", "On-device · ${aiLocalId ?: "local"}")
        aiEndpoint.model.isNotBlank() -> {
            val provider = aiEndpoint.name.trim()
            if (provider.isBlank() || provider == aiEndpoint.model) aiEndpoint.model else "$provider · ${aiEndpoint.model}"
        }
        else -> tr("尚未接入模型", "no model configured")
    }

    /** 手动编辑太琐碎时，用 AI 只把「旅行备注」重写一遍，标题保持用户自己填的 */
    fun runAiOrganize() {
        if (aiBusy) return
        if (title.isBlank() && city.isBlank() && note.isBlank() && photos.isEmpty()) {
            state.toast(tr("先写一点内容或加照片，AI 才有东西可写"))
            return
        }
        val facts = buildString {
            appendLine("地点：${city.trim().ifBlank { "未提供" }}")
            appendLine("日期：${date.trim().ifBlank { "未提供" }}")
            appendLine("已有备注：${note.trim().ifBlank { "无" }}")
            appendLine("照片数量：${photos.size}")
            appendLine("特别回忆：${if (special) specialReason.trim().ifBlank { "是" } else "否"}")
        }
        scope.launch {
            aiBusy = true
            // 走本机就真的用本机，端侧不可用才回退云端——别让界面标着端侧、实际发云端
            val draft = if (aiUsesLocal) {
                AiApi.composeFootprintLocal(context, facts, aiLocalId)
                    ?: AiApi.composeFootprint(aiEndpoint, facts)
            } else {
                AiApi.composeFootprint(aiEndpoint, facts)
            }
            aiBusy = false
            if (draft == null || draft.note.isBlank()) {
                state.toast(tr("AI 写备注失败，请检查网络或模型设置后重试"))
                return@launch
            }
            val keptMeta = note.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("📍") || it.startsWith("作品发布：") || it.startsWith("识别文字：") }
                .toList()
            note = (keptMeta + draft.note).joinToString("\n").take(500)
            state.toast(tr("AI 已重写旅行备注，标题保持不变，确认后记得保存"))
        }
    }

    val replacePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val index = replaceIndex
        replaceIndex = -1
        if (uri == null || index < 0 || index >= photos.size) return@rememberLauncherForActivityResult
        scope.launch {
            val path = PhotoStore.copyOne(
                context,
                footprint.id,
                uri,
                "edit_${System.nanoTime()}_$index.jpg"
            )
            if (path == null) {
                state.toast(tr("照片替换失败，请重试"))
            } else {
                photos = photos.toMutableList().also { it[index] = path }
                state.toast(tr("已替换照片"))
            }
        }
    }

    val addPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var added = 0
            uris.forEachIndexed { index, uri ->
                val path = PhotoStore.copyOne(
                    context,
                    footprint.id,
                    uri,
                    "edit_${System.nanoTime()}_add_$index.jpg"
                )
                if (path != null) {
                    photos = photos + path
                    added++
                }
            }
            state.toast(if (added > 0) tr("已添加 $added 张照片") else tr("照片添加失败，请重试"))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Func.Scrim)
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Bg.Surface)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {}
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("编辑足迹"), color = Text.Primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDismiss)
                        .padding(6.dp)
                ) {
                    Icon(Icons.Outlined.Close, tr("关闭"), tint = Text.Secondary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brand.Terracotta.copy(alpha = 0.07f))
                    .border(1.dp, Brand.Terracotta.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Brand.Terracotta,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tr("AI 帮写旅行备注", "AI travel note"),
                        color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tr("按地点、日期、照片和现有备注，只重写旅行备注；标题保持你填的内容。",
                            "Rewrite the travel note only from place, date, photos and the current note; the title stays yours."),
                        color = Text.Secondary, fontSize = 11.sp, lineHeight = 15.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    // 标明这次整理实际用哪个模型：用户不该点完才发现没接入
                    Text(
                        text = tr("模型 · ", "Model · ") + aiModelLabel,
                        color = if (aiModelReady) Text.Muted else Func.Warning,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Brand.Terracotta)
                        .clickable(enabled = !aiBusy) { runAiOrganize() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (aiBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(tr("写备注", "Write"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(tr("标题"), fontSize = 11.sp) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Text.Primary),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text(tr("城市"), fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Text.Primary),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text(tr("日期"), fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Text.Primary),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(tr("备注"), fontSize = 11.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Text.Primary),
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (special) Brand.Gold.copy(alpha = 0.18f) else Bg.SurfaceVariant)
                        .clickable { special = !special }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = null,
                        tint = if (special) Brand.Gold else Text.Muted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        tr("特别回忆"),
                        color = if (special) Brand.Terracotta else Text.Secondary,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    tr("已选 ${photos.size} 张"),
                    color = Text.Muted,
                    fontSize = 11.sp
                )
            }
            if (special) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = specialReason,
                    onValueChange = { specialReason = it },
                    label = { Text(tr("特别原因"), fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Text.Primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // 实况 / 视频 / 配乐：编辑时就能补、能换、能移
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("视频 · 实况 · 音乐", "Video · Live · Music"),
                    color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                if (mediaBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Brand.Terracotta
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorChip(
                    label = if (videoPath != null && !videoIsLive) tr("换一段视频", "Replace video")
                    else tr("加入视频", "Add video"),
                    enabled = !mediaBusy
                ) {
                    videoAddPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                }
                EditorChip(
                    label = if (videoPath != null && videoIsLive) tr("换一张实况", "Replace live")
                    else tr("加入实况", "Add Live Photo"),
                    enabled = !mediaBusy
                ) {
                    liveAddPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                EditorChip(
                    label = if (bgmPath == null) tr("加入音乐", "Add music") else tr("换一首音乐", "Replace music"),
                    enabled = true
                ) { showBgmSheet = true }
            }
            if (videoPath != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (videoIsLive && videoPaths.size == 1) tr("已加入实况", "Live photo added")
                        else if (videoPaths.size > 1) tr("已加入视频（${videoPaths.size} 段）", "${videoPaths.size} videos added")
                        else tr("已加入视频", "Video added"),
                        color = Text.Secondary, fontSize = 12.sp, modifier = Modifier.weight(1f)
                    )
                    Text(
                        tr("移除", "Remove"),
                        color = Func.Danger, fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(enabled = !mediaBusy) {
                                // 只解引用、不删文件：这时还没保存，用户取消的话原视频得还在。
                                // 真正的旧文件清理由 updateFootprint 在保存后做
                                videoPaths = emptyList()
                                videoPath = null
                                videoIsLive = false
                                videoBytesSaved = 0L
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (bgmPath != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "♪ ${bgmTitle ?: tr("已选配乐", "Soundtrack")}",
                        color = Text.Secondary, fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Text(
                        tr("移除", "Remove"),
                        color = Func.Danger, fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                // 同上：先只解引用，保存时由 updateFootprint 清理旧文件
                                bgmPath = null
                                bgmTitle = null
                                bgmArtist = null
                                bgmSource = null
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (videoPath != null) {
                Spacer(Modifier.height(10.dp))
                // 补完之后当场能看/能听，不用先保存再点进详情验证
                MemoryMediaBlock(
                    videoPath = videoPath,
                    videoIsLive = videoIsLive,
                    bgmPath = bgmPath,
                    bgmTitle = bgmTitle
                )
            } else if (bgmPath != null) {
                // 只加了配乐：直接放播放器，整条可左右滑动跳到歌曲时间
                val trackPath = bgmPath
                if (trackPath != null) {
                    Spacer(Modifier.height(10.dp))
                    MusicBgmStrip(
                        path = trackPath,
                        title = bgmTitle.orEmpty(),
                        artist = bgmArtist
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr(
                            "左右滑动跳到播放时间 · 点一下暂停/继续 · 长按换样式",
                            "Drag to seek · tap to pause/resume · hold to change style"
                        ),
                        color = Text.Muted, fontSize = 10.sp
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("照片"), color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Brand.Terracotta.copy(alpha = 0.12f))
                        .clickable {
                            addPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(tr("添加照片"), color = Brand.Terracotta, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (photos.isEmpty()) {
                Text(tr("还没有照片，点「添加照片」补几张"), color = Text.Muted, fontSize = 12.sp)
            } else {
                photos.forEachIndexed { index, path ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    ) {
                        PhotoThumb(
                            path = path,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                tr("照片 ${index + 1}"),
                                color = Text.Secondary,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Row {
                                Text(
                                    tr("替换"),
                                    color = Brand.Terracotta,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable {
                                            replaceIndex = index
                                            replacePicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    tr("删除"),
                                    color = Text.Secondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable {
                                            photos = photos.filterIndexed { i, _ -> i != index }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text(
                    tr("取消"),
                    color = Text.Secondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Brand.Terracotta)
                        .clickable {
                            if (title.trim().isBlank()) {
                                state.toast(tr("标题不能为空"))
                            } else {
                                state.updateFootprint(
                                    footprint.copy(
                                        name = title.trim(),
                                        city = city.trim(),
                                        date = date.trim(),
                                        // 日期文本是真相：改完顺手把排序用的时间戳一起刷新，
                                        // 否则时间轴会继续按创建那年的年月归档
                                        dateMillis = FootprintDate.millis(date.trim()) ?: footprint.dateMillis,
                                        note = note.trim(),
                                        special = special,
                                        specialReason = specialReason.trim().ifBlank { null },
                                        photoPaths = photos.distinct(),
                                        // 编辑里补的媒体一起写回，和新建时的字段一一对应
                                        videoPath = videoPaths.firstOrNull(),
                                        videoPaths = videoPaths.distinct(),
                                        videoIsLive = videoPaths.size == 1 && videoIsLive,
                                        videoBytes = videoBytesSaved,
                                        bgmPath = bgmPath,
                                        bgmTitle = bgmTitle,
                                        bgmArtist = bgmArtist,
                                        bgmSource = bgmSource
                                    )
                                )
                                state.toast(tr("已保存修改"))
                                onDismiss()
                            }
                        }
                        .padding(horizontal = 22.dp, vertical = 9.dp)
                ) {
                    Text(
                        tr("保存"),
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    // 配乐选择器：曲源是 App 自己的音乐库（已下载的歌）+ 本地音频文件
    if (showBgmSheet) {
        BgmPickerSheet(
            onDismiss = { showBgmSheet = false },
            onPick = { pick ->
                showBgmSheet = false
                scope.launch {
                    val copied = withContext(Dispatchers.IO) {
                        runCatching {
                            PhotoStore.copyMedia(context, footprint.id, java.io.File(pick.path), "bgm")
                        }.getOrNull()
                    }
                    if (copied == null) {
                        state.toast(tr("这首音乐没能存进这条足迹，换一首试试", "Could not attach this track, try another"))
                    } else {
                        bgmPath = copied
                        bgmTitle = pick.title
                        bgmArtist = pick.artist
                        bgmSource = pick.source
                        state.toast(tr("音乐已加入：${pick.title}", "Music added: ${pick.title}"))
                    }
                }
            }
        )
    }
}

/** 编辑面板里的小胶囊按钮 */
@Composable
private fun EditorChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Brand.Terracotta.copy(alpha = if (enabled) 0.12f else 0.06f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (enabled) Brand.Terracotta else Text.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PhotoThumb(path: String, modifier: Modifier = Modifier) {
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            PhotoStore.decodeFile(path, 240)?.asImageBitmap()
        }
    }
    Box(modifier = modifier.background(Bg.SurfaceVariant)) {
        bmp?.let {
            Image(
                bitmap = it,
                contentDescription = tr("照片"),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/** 路线时长显示：不足 1 小时 mm:ss，超过 h:mm:ss */
private fun formatRouteDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

@Composable
private fun DetailPhoto(path: String, modifier: Modifier = Modifier) {
    val bmp = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            PhotoStore.decodeFile(path, 1080)?.asImageBitmap()
        }
    }.value
    Box(
        modifier = modifier.background(Bg.SurfaceVariant)
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = tr("足迹照片"),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
