package com.example.my.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.my.data.AmapApi
import com.example.my.data.AiApi
import com.example.my.data.AiFeature
import com.example.my.data.ChinaMapData
import com.example.my.data.CityCoords
import com.example.my.data.TrackRecorder
import com.example.my.model.Footprint
import com.example.my.state.AppState
import com.example.my.state.AppTab
import com.example.my.ui.components.AppTopBar
import com.example.my.ui.components.BackActionRow
import com.example.my.ui.components.BgmPickerSheet
import com.example.my.ui.components.BackExitButton
import com.example.my.ui.components.MAX_PUBLISH_PHOTOS
import com.example.my.ui.components.MemoryMediaBlock
import com.example.my.ui.components.MusicBgmStrip
import com.example.my.ui.components.PhotoGridEditor
import com.example.my.ui.components.PhotoSourceSheet
import com.example.my.ui.components.PhotoViewerDialog
import com.example.my.ui.components.SmoothProgressBar
import com.example.my.ui.components.VideoPosterCover
import com.example.my.ui.components.aiModelLabel
import com.example.my.ui.theme.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.my.data.music.BgmPick
import com.example.my.ui.interaction.MorphSaveButton
import com.example.my.ui.interaction.MorphSavePhase
import com.example.my.util.GeoPoint
import com.example.my.util.PhotoStore
import com.example.my.util.ShortVideoResolver
import com.example.my.util.CompactResult
import com.example.my.util.MediaCompactor
import com.example.my.util.douyin.DouyinPrep
import com.example.my.util.livephoto.LivePhoto
import com.example.my.util.livephoto.MotionVideo
import com.example.my.util.cleanNote
import com.example.my.util.haversineKm
import com.example.my.util.localizeLabels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.my.ui.i18n.tr

private val companionOptions = listOf("独自", "情侣", "朋友", "亲子", "家人", "跟团")
private val cityChips = listOf("大理", "成都", "重庆", "三亚", "拉萨", "京都", "新疆", "厦门")
private val themeTagOptions = listOf("休闲游", "自驾", "徒步", "亲子", "人文", "深度", "摄影", "美食", "海边", "古镇")
private val weatherOptions = listOf("晴" to "晴", "多云" to "多云", "雨" to "雨", "雪" to "雪", "雾" to "雾", "大风" to "大风")

/** 上传足迹页：步骤条 + 照片 + 基本信息 + 备注 */
@Composable
fun UploadScreen(state: AppState) {
    val context = LocalContext.current
    remember { ChinaMapData.load(context) }

    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var marker by remember { mutableStateOf<Offset?>(null) }
    /**
     * 选中的精确坐标（GCJ-02，lon to lat）。
     *
     * marker 是「示意中国地图」上的坐标，只用于在小地图上画点；它由经纬度线性投影而来，
     * 再反算回经纬度会有误差。真正要存、要回填到选点页的，是这里的原始经纬度。
     * 两者必须同步维护：只改 marker 会让下次打开选点页偏几十米。
     */
    var coords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    /** 当前坐标是否来自照片 EXIF（用于界面提示，手动选城市后清除） */
    var geoFromPhoto by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    /** 地点（城市快捷选择 / 地图选点），与标题分离：选城市不再覆盖用户自己起的标题 */
    var city by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf<Long?>(null) }
    /** 当前日期是否来自照片 EXIF（用户手动改过就置 false，不再被后续照片覆盖） */
    var dateFromPhoto by remember { mutableStateOf(false) }
    var companion by remember { mutableStateOf("独自") }
    var note by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var rating by remember { mutableStateOf(0) }
    var weather by remember { mutableStateOf<String?>(null) }
    var special by remember { mutableStateOf(false) }
    var isPublic by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    /**
     * 选中的视频 / 实况片段：选片时就已经压好并落在缓存目录，保存时只做一次文件搬移，
     * 避免用户点了保存才等半分钟压缩。
     */
    var mediaFile by remember { mutableStateOf<CompactResult?>(null) }
    /** 除第一条外的视频/实况；不设总数上限，用户可一批批继续加 */
    var extraMediaFiles by remember { mutableStateOf<List<CompactResult>>(emptyList()) }
    var mediaIsLive by remember { mutableStateOf(false) }
    var mediaBusy by remember { mutableStateOf(false) }
    var mediaSeconds by remember { mutableIntStateOf(0) }
    /** 处理进度与阶段文案：拖视频进来会转码，必须让用户看见「在动」 */
    var mediaProgress by remember { mutableFloatStateOf(0f) }
    var mediaStatus by remember { mutableStateOf("") }
    /** 成片规格摘要（真实值 + 引擎），例如「1080×1920 · 29.8fps · 3.24Mbps · Transformer」 */
    var mediaSpec by remember { mutableStateOf("") }
    /** 短视频分享链接 / MP4 直链 */
    var videoLink by remember { mutableStateOf("") }
    var linkBusy by remember { mutableStateOf(false) }
    var linkStatus by remember { mutableStateOf("") }
    /** 已选配乐（Tunetank 或本地音频），保存时搬进足迹目录 */
    var bgm by remember { mutableStateOf<BgmPick?>(null) }
    /** 保存成功后暂存足迹：底部按钮从进度环展开为「打开足迹」 */
    var savedFp by remember { mutableStateOf<Footprint?>(null) }
    /** 已发布到微足迹的帖子 id（null 表示还没发布）；用于底部「发布/已发布」按钮 */
    var publishedPostId by remember { mutableStateOf<String?>(null) }
    // 重复足迹确认
    var showDuplicateConfirm by remember { mutableStateOf(false) }
    var pendingFootprint: Footprint? by remember { mutableStateOf(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCompanionMenu by remember { mutableStateOf(false) }
    /** 地图选点页（全屏 Dialog 覆盖）；开合状态放在 AppState，系统返回键才能逐层退出 */
    val showMapPicker = state.mapPickOpen

    val scope = rememberCoroutineScope()

    /** 是否从 AI 旅行助手进入；只消费一次，普通新建不会带 AI 文案 */
    val aiAssist = remember { state.consumeAiFootprintAssist() }
    var aiBusy by remember { mutableStateOf(false) }
    /** 长按「模型：xxx」时打开模型库，为足迹总结单独换模型 */
    var showAiModelPicker by remember { mutableStateOf(false) }

    /** 新版抖音式取景页：入口状态持久化，切回旧版后仍保留来源面板 */
    var useStudioCapture by remember { mutableStateOf(state.uploadStudioCapture) }
    var showStudioCapture by remember { mutableStateOf(false) }

    /** 正在全屏预览第几张（null 表示没开预览） */
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    // 视频格子的全屏预览：路径为空表示没开着
    var previewVideo by remember { mutableStateOf<String?>(null) }
    /** 刚删掉的那张（原位置 to Uri），用于 5 秒内撤销 */
    var undoRemoved by remember { mutableStateOf<Pair<Int, Uri>?>(null) }
    LaunchedEffect(undoRemoved) {
        if (undoRemoved != null) {
            delay(5_000)
            undoRemoved = null
        }
    }

    /** 有内容时退出要二次确认（系统返回键与「取消」共用这一个框） */
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val hasContent = photos.isNotEmpty() || extraMediaFiles.isNotEmpty() || name.isNotBlank() || city.isNotBlank() || note.isNotBlank() || coords != null || mediaFile != null
    LaunchedEffect(hasContent) { state.markUploadDirty(hasContent) }
    LaunchedEffect(state.uploadExitRequested, hasContent) {
        if (state.uploadExitRequested) {
            state.consumeUploadExitRequest()
            if (hasContent) showDiscardConfirm = true else state.closeUpload(force = true)
        }
    }

    /** 顶部返回箭头：有内容先确认，没内容直接关（与系统返回键同一套判断） */
    fun requestExit() {
        if (saving) {
            state.toast(tr("正在保存，请稍候", "Saving, please wait"))
            return
        }
        // 保存成功后按钮已变成「打开足迹」，此时返回就是单纯离开，不再问「放弃？」
        if (savedFp != null) {
            state.closeUpload(force = true)
            return
        }
        if (hasContent) showDiscardConfirm = true else state.closeUpload()
    }

    /**
     * 选完照片的统一处理：截断到剩余名额 → 去重 → 按 EXIF 回填地点与日期。
     *
     * picker 不会自动遵守剩余名额（多选的最大张数是构造时写死的 9），
     * 所以超出部分必须在这里裁掉，并且明确告诉用户被裁了，避免「选了 9 张只来 3 张」的困惑。
     */
    fun onPhotosPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        // 不设总数上限：同一批去重后全部加入，用户还可以一批批继续选。
        val fresh = uris.filterNot { it in photos }
        if (fresh.isEmpty()) return
        photos = photos + fresh
        // 选中照片后用端侧 AI 分析（标签 / OCR / 人脸 / 条码 / 翻译），结果追加到备注末尾（失败静默）
        fresh.firstOrNull()?.let { uri ->
            scope.launch {
                visionSuffix(context, uri, state.aiCapabilities, state.effectiveOcrEndpoint())?.let { suffix -> note += suffix }
            }
        }
        run {
            scope.launch {
                // GPS 与日期合并为一次扫描：每张照片只开一次流，且任一条件先满足就短路，
                // 避免重复 openInputStream（部分来源的 Uri 二次打开会失败）。
                val (gps, shotAt) = withContext(Dispatchers.IO) { scanExif(context, fresh) }
                if (gps != null && marker == null) {
                    val (wgsLat, wgsLon) = gps
                    // EXIF 写的是 WGS-84；底图、POI、天气全是 GCJ-02。
                    // 不转换的话打点会偏 300~600 米，反查地名也会查到隔壁街区
                    val (gcjLat, gcjLon) = TrackRecorder.wgs84ToGcj02(wgsLat, wgsLon)
                    val (x, y) = ChinaMapData.lonLatToMap(gcjLon, gcjLat)
                    marker = Offset(x, y)
                    coords = gcjLon to gcjLat
                    geoFromPhoto = true
                    // 取最近的 POI 名（如「鼓浪屿」），拿不到才退回「城市 · 区县」
                    val picked = AmapApi.reversePick(gcjLat, gcjLon)
                    val place = picked?.name ?: picked?.region
                    // 照片反查的行政区填入"地点"；标题仅在用户没起名字时才自动建议
                    if (city.isBlank() && !picked?.region.isNullOrBlank()) city = picked?.region.orEmpty()
                    if (name.isBlank() && !place.isNullOrBlank()) name = place
                    val filled = mutableListOf<String>()
                    if (!place.isNullOrBlank()) {
                        filled.add(place)
                    } else if (gps != null) {
                        filled.add(tr("拍摄位置", "location"))
                    }
                    if (shotAt != null && dateMillis == shotAt) {
                        filled.add(tr("拍摄时间", "time"))
                    }
                    if (filled.isNotEmpty()) {
                        state.toast(
                        tr("已按照片自动填充：${filled.joinToString("、")}",
                            "Filled from photo: ${filled.joinToString(", ")}")
                    )
                    }
                }
                // 日期回填：仅在用户还没手动选过日期时生效，与 GPS 的 marker == null 条件对齐
                if (shotAt != null && dateMillis == null) {
                    dateMillis = shotAt
                    dateFromPhoto = true
                }
            }
        }
    }

    /**
     * 系统相册选择器。
     *
     * 不再先要 READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE：
     * Photo Picker（API 30+ 走系统选择器，旧系统走 ACTION_OPEN_DOCUMENT）本来就只把用户
     * 亲手挑的那几张授权给 App，不需要读整个相册的权限。原来的实现还要等权限回调，
     * 但回调是空的 —— 用户第一次点「添加照片」永远没反应，这是最影响手感的一个坑。
     *
     * 拆成多选 / 单选两个 launcher：只剩 1 个名额时弹单选，
     * 否则用户能在多选面板里勾 9 张，回来只留下 1 张。
     */
    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PUBLISH_PHOTOS)
    ) { uris -> onPhotosPicked(uris) }

    val singlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { onPhotosPicked(listOf(it)) } }

    fun openPicker() {
        if (saving || savedFp != null) return
        multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // ---------- 视频 / 实况 ----------
    /** 体积文案：不足 1MB 给 KB，其余给 MB，避免「1234567 KB」这种没意义的数字 */
    fun sizeText(bytes: Long): String =
        if (bytes < 1024 * 1024) "${(bytes / 1024).coerceAtLeast(1)} KB"
        else String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)

    /** 压缩过程中的中间文件都放缓存目录，保存时才搬进足迹目录 */
    fun mediaDir(): java.io.File = java.io.File(context.cacheDir, "media_in").apply { mkdirs() }

    /**
     * 视频 / 实况处理结果：成片 + 识别出的来源格式，失败时带上「卡在哪一步」和「为什么」。
     *
     * 之前失败只返回 null，上层统一弹一句「这段视频读不出来」，
     * 换封装失败、解码器不支持 HEVC、超时、磁盘写不进去全都长一个样，用户和开发者都无从下手。
     */
    class PrepOutcome(
        val result: DouyinPrep.Result?,
        val format: LivePhoto.Format? = null,
        /** 失败阶段：导入 / 识别 / 处理（写日志用，不直接展示给用户） */
        val stage: String = "",
        /** 底层抛出的具体原因，可能为空 */
        val reason: String = "",
        /** 实况识别成功但没抠到视频时保留的静图，供「补选配对视频」使用；用完由调用方删除 */
        val still: java.io.File? = null,
    )

    /** 成片规格摘要，例如「1080×1920 · 30.0fps · 3.24Mbps · Transformer」 */
    fun specText(result: DouyinPrep.Result): String {
        val fps = if (result.fps > 0.5) result.fps else 30.0
        val engine = when {
            result.engine.contains("Transformer") -> " · Transformer"
            result.engine == DouyinPrep.ENGINE_PLATFORM -> " · MediaCodec"
            else -> ""
        }
        // 有导出统计就显示真实码率，别把整片字节估算当成编码器实际输出
        val bitrate = result.realBitrate?.takeIf { it > 0 } ?: result.videoBitrate
        return "${result.info.displayWidth}×${result.info.displayHeight} · " +
            String.format(Locale.US, "%.1f", fps) + "fps · " +
            String.format(Locale.US, "%.2f", bitrate / 1_000_000.0) + "Mbps" + engine
    }

    /** 处理完成后统一落地：第一条占用封面格，后续追加到列表，不再删掉上一段 */
    suspend fun finishMedia(prepared: DouyinPrep.Result, live: Boolean) {
        val item = CompactResult(prepared.file, prepared.file.length(), prepared.mode == DouyinPrep.Mode.ENCODE)
        if (mediaFile == null) {
            mediaFile = item
            mediaIsLive = live
            mediaSpec = specText(prepared)
            mediaSeconds = withContext(Dispatchers.IO) { MediaCompactor.durationSeconds(prepared.file) }
        } else {
            extraMediaFiles = extraMediaFiles + item
        }
    }

    /**
     * 待配对的静图。
     *
     * iOS / vivo 的实况是「图 + 视频」两个文件，系统相册选择器一次只回一个 Uri，
     * 缓存目录里永远不会有同名 MOV —— 旧实现到这一步只能报「没有内嵌视频」。
     * 这里把静图留着，用户再选一次配对视频就能直接出片。
     */
    var pairStill by remember { mutableStateOf<java.io.File?>(null) }

    /** 用用户补选的视频作为实况片段 */
    fun pickPair(still: java.io.File, videoUri: Uri) {
        if (mediaBusy) { runCatching { still.delete() }; return }
        scope.launch {
            mediaBusy = true
            mediaProgress = 0f
            mediaStatus = tr("正在处理配对的动态片段…", "Processing paired clip…")
            val prepared = withContext(Dispatchers.IO) {
                val clip = runCatching { MediaCompactor.importToCache(context, videoUri, true, "media_pair") }.getOrNull()
                if (clip == null) {
                    // 静图先留着：导入失败时还允许重新补选，不能把照片一起删掉
                    null
                } else {
                    val dst = java.io.File(mediaDir(), "upload_${System.currentTimeMillis()}.mp4")
                    val result = runCatching {
                        DouyinPrep.prepare(context, clip, dst, DouyinPrep.Fit.PAD) { p ->
                            mediaProgress = p
                            mediaStatus = tr("自动处理 ${(p * 100).toInt()}%", "Processing ${(p * 100).toInt()}%")
                        }
                    }
                    clip.delete()
                    val err = result.exceptionOrNull()
                    if (result.isFailure) {
                        dst.delete()
                        Log.w("UploadScreen", "配对片段处理失败 uri=$videoUri", err)
                    }
                    result.getOrNull()
                }
            }
            mediaBusy = false
            mediaStatus = ""
            if (prepared == null) {
                // 失败可重试：静图还在，用户换一段配对视频即可，不用重新选照片
                pairStill = still
                state.toast(tr("配对的视频处理失败，请换一段再试", "Paired video failed, try another"))
                return@launch
            }
            runCatching { still.delete() }
            finishMedia(prepared, live = true)
            state.toast(tr("实况已拼好（${sizeText(prepared.file.length())}）", "Live photo ready (${sizeText(prepared.file.length())})"))
        }
    }

    val pairPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val still = pairStill
        pairStill = null
        if (uri != null && still != null) pickPair(still, uri) else runCatching { still?.delete() }
    }

    /**
     * 选视频（支持一次多选 / 多次追加）：导入后**自动按抖音上传规格处理**——
     * H.264 / MP4 / 1080×1920 / 30fps / 视频 ≤8Mbps / 音频 AAC 128k。
     * 已合规只换封装（不重编码）；不合格才转码，且成片会复核码率，不交付超线文件。
     * 第一条占封面格，其余全部进入 [extraMediaFiles]，总数不设上限。
     */
    fun pickVideo(uris: List<Uri>) {
        if (uris.isEmpty() || mediaBusy || saving) return
        scope.launch {
            mediaBusy = true
            var added = 0
            var failed = 0
            for ((index, uri) in uris.withIndex()) {
                mediaProgress = 0f
                mediaStatus = tr("正在处理第 ${index + 1}/${uris.size} 段视频…",
                    "Processing video ${index + 1}/${uris.size}…")
                val outcome = withContext(Dispatchers.IO) {
                    val imported = runCatching { MediaCompactor.importToCache(context, uri, true) }
                    val raw = imported.getOrNull() ?: return@withContext PrepOutcome(
                        null,
                        stage = tr("导入", "Import"),
                        reason = imported.exceptionOrNull()?.message
                            ?: tr("无法读取所选文件（可能已被移动或没有访问权限）",
                                "Cannot open the picked file (moved or not accessible)"),
                    )
                    val dst = java.io.File(mediaDir(), "upload_${System.currentTimeMillis()}_$index.mp4")
                    val prepared = runCatching {
                        DouyinPrep.prepare(context, raw, dst, DouyinPrep.Fit.PAD) { p ->
                            mediaProgress = p
                            mediaStatus = tr("自动处理 ${(p * 100).toInt()}%", "Processing ${(p * 100).toInt()}%")
                        }
                    }
                    raw.delete()
                    val err = prepared.exceptionOrNull()
                    if (prepared.isFailure) {
                        dst.delete()
                        Log.w("UploadScreen", "视频处理失败 uri=$uri", err)
                    }
                    PrepOutcome(prepared.getOrNull(), stage = tr("处理", "Processing"), reason = err?.message.orEmpty())
                }
                val prepared = outcome.result
                if (prepared == null) {
                    failed++
                    val why = outcome.reason
                    if (why.isNotBlank()) Log.w("UploadScreen", "视频失败：$why")
                    continue
                }
                finishMedia(prepared, live = false)
                added++
            }
            mediaBusy = false
            mediaStatus = ""
            if (added > 0) {
                state.toast(
                    if (uris.size == 1) tr("视频已加入（${sizeText(mediaFile?.file?.length() ?: 0L)}）",
                        "Video added")
                    else tr("已加入 $added 段视频${if (failed > 0) "，$failed 段失败" else ""}",
                        "Added $added video(s)${if (failed > 0) ", $failed failed" else ""}")
                )
            } else {
                state.toast(tr("视频处理失败，请换一段再试", "Video processing failed, try another clip"))
            }
        }
    }

    /** 链接解析出的视频直接进入待发布列表；第一条占封面格，后续追加 */
    fun addVideoFile(file: java.io.File, live: Boolean = false, durationSec: Int = 0) {
        val item = CompactResult(file, file.length(), false)
        if (mediaFile == null) {
            mediaFile = item
            mediaIsLive = live
            mediaSeconds = durationSec
            mediaSpec = "链接解析"
        } else {
            extraMediaFiles = extraMediaFiles + item
        }
    }

    /** 解析短视频链接：服务端解析视频/图集/实况/配乐，能拿到的全部直接填入 */
    fun resolveVideoLink() {
        val input = videoLink.trim()
        if (input.isBlank() || linkBusy || mediaBusy) return
        scope.launch {
            linkBusy = true
            linkStatus = tr("正在解析链接…", "Resolving link…")
            val result = withContext(Dispatchers.IO) {
                runCatching { ShortVideoResolver.resolve(context, input).getOrThrow() }
            }
            linkBusy = false
            result.onSuccess { resolved ->
                val added = mutableListOf<String>()

                // 标题优先填入；用户已经手写过标题就不覆盖
                if (resolved.title.isNotBlank() && name.isBlank()) {
                    name = resolved.title
                    added += tr("标题", "title")
                }

                // 作品发布时间与定位：先于照片 EXIF 写入，避免被“未识别拍摄时间”覆盖
                val metaLines = mutableListOf<String>()
                resolved.createdAtMillis?.let { millis ->
                    if (dateMillis == null) {
                        dateMillis = millis
                        dateFromPhoto = false
                        added += tr("发布时间", "published time")
                    }
                    metaLines += tr(
                        "作品发布：" + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis)),
                        "Published: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))
                    )
                }
                if (resolved.location.isNotBlank()) {
                    if (city.isBlank() && resolved.locationCity.isNotBlank()) {
                        city = resolved.locationCity
                    }
                    metaLines.add(0, "📍 ${resolved.location}")
                    added += tr("地点", "location")
                }
                if (note.isBlank() && metaLines.isNotEmpty()) {
                    note = metaLines.joinToString("\n")
                }

                // 图集 / 实况静图直接进照片区
                if (resolved.images.isNotEmpty()) {
                    onPhotosPicked(resolved.images.map { Uri.fromFile(it) })
                    added += tr("${resolved.images.size} 张图片", "${resolved.images.size} image(s)")
                }

                // 视频、实况动态片段都进待发布视频列表
                val videos = buildList {
                    resolved.video?.let { add(it to resolved.durationMs) }
                    resolved.liveVideos.forEach { add(it to 0L) }
                }
                videos.forEachIndexed { index, pair ->
                    val file = pair.first
                    val durationMs = pair.second
                    addVideoFile(
                        file,
                        live = false,
                        durationSec = if (index == 0) (durationMs / 1000).toInt() else 0
                    )
                }
                if (videos.isNotEmpty()) {
                    added += tr("${videos.size} 段视频", "${videos.size} video(s)")
                }

                // 解析出的配乐直接设为 BGM；用户已手动选过则不覆盖
                resolved.audio?.let { audio ->
                    if (bgm == null) {
                        bgm = BgmPick(
                            path = audio.absolutePath,
                            title = resolved.audioTitle.ifBlank { resolved.title.ifBlank { audio.name } },
                            artist = resolved.audioArtist.ifBlank { resolved.author.ifBlank { null } },
                            source = when {
                                resolved.sourceUrl.contains("kuaishou", true) -> "快手"
                                resolved.sourceUrl.contains("xiaohongshu", true) ||
                                    resolved.sourceUrl.contains("xhslink", true) -> "小红书"
                                else -> "抖音"
                            }
                        )
                        added += tr("音频", "audio")
                    }
                }

                if (!resolved.hasContent) {
                    linkStatus = tr("解析失败：没有可加入的媒体", "Failed: no media")
                    state.toast(tr("链接里没有可加入的媒体", "No media in this link"))
                    return@onSuccess
                }

                val summary = added.joinToString("、")
                linkStatus = tr("已解析并加入：$summary", "Added: $summary")
                videoLink = ""
                state.toast(tr("链接解析完成，已直接填入", "Link resolved and filled in"))
            }.onFailure { err ->
                linkStatus = tr("解析失败：${err.message ?: "未知原因"}",
                    "Failed: ${err.message ?: "unknown"}")
                state.toast(tr("链接解析失败：${err.message ?: "请换 MP4 直链"}",
                    "Link failed: ${err.message ?: "try a direct MP4"}"))
            }
        }
    }

    /**
     * 选一张实况/动态照片：先用 [LivePhoto.identify] 判定属于哪一家（iOS 配对 / 安卓通用 /
     * OPPO / 华为·荣耀 / vivo 分离式、以及 HEIC 单文件动态照片），
     * 再用 [MotionVideo] 切出内嵌视频（声明长度 + 盒链扫描双保险），最后按抖音规格自动处理。
     * 照片本身照常进照片区；分离式素材缺视频时会引导补选配对文件。
     */
    fun pickLive(uri: Uri) {
        if (mediaBusy || saving) return
        scope.launch {
            mediaBusy = true
            mediaProgress = 0f
            mediaStatus = tr("正在识别动态照片…", "Detecting…")
            onPhotosPicked(listOf(uri))
            val outcome = withContext(Dispatchers.IO) {
                val imported = runCatching { MediaCompactor.importToCache(context, uri, false, "media_live") }
                val image = imported.getOrNull() ?: return@withContext PrepOutcome(
                    null,
                    stage = tr("识别", "Detect"),
                    reason = imported.exceptionOrNull()?.message
                        ?: tr("无法读取所选照片", "Cannot open the picked photo"),
                )
                val identRun = runCatching { LivePhoto.identify(image) }
                val ident = identRun.getOrNull()
                identRun.exceptionOrNull()?.let { Log.w("UploadScreen", "实况识别失败 uri=$uri", it) }

                // 抠内嵌视频：优先用同目录配对文件，其次按「声明长度 + 盒链扫描」切尾部。
                // 两步都失败时保留静图，交给上层引导用户补选配对视频。
                val clip = java.io.File(mediaDir(), "live_${System.currentTimeMillis()}.mp4")
                val extracted = when {
                    ident?.videoFile != null ->
                        runCatching { ident.videoFile.copyTo(clip, overwrite = true) }.isSuccess
                    else -> MotionVideo.extract(image, clip, ident?.embeddedVideoLen?.takeIf { it > 0 }) != null
                }
                if (!extracted || !clip.exists() || clip.length() <= 0L) {
                    runCatching { clip.delete() }
                    return@withContext PrepOutcome(
                        null, ident?.format,
                        stage = tr("识别", "Detect"),
                        reason = ident?.reasons?.firstOrNull().orEmpty(),
                        still = image,
                    )
                }
                val dst = java.io.File(mediaDir(), "upload_${System.currentTimeMillis()}.mp4")
                val prepared = runCatching {
                    DouyinPrep.prepare(context, clip, dst, DouyinPrep.Fit.PAD) { p ->
                        mediaProgress = p
                        mediaStatus = tr("自动处理 ${(p * 100).toInt()}%", "Processing ${(p * 100).toInt()}%")
                    }
                }
                clip.delete()
                image.delete()
                val err = prepared.exceptionOrNull()
                if (prepared.isFailure) {
                    dst.delete()
                    Log.w("UploadScreen", "实况片段处理失败 uri=$uri fmt=${ident?.format}", err)
                }
                PrepOutcome(prepared.getOrNull(), ident?.format, tr("处理", "Processing"), err?.message.orEmpty())
            }
            mediaBusy = false
            mediaStatus = ""
            val prepared = outcome.result
            if (prepared == null) {
                val format = outcome.format
                val still = outcome.still
                // 分离式实况（iOS 图 + MOV / vivo 图 + MP4）选择器只回一个 Uri，
                // 缓存目录里没有同名视频，identify 会把 JPG 静图判成 STATIC，
                // 旧闸门只认 IOS/VIVO 于是补选链根本不可达。
                // 现在只要「没抠到视频但静图还在」就给一次补选机会；照片本身已进照片区，
                // 不是实况的用户直接返回即可。
                if (still != null) {
                    pairStill = still
                    state.toast(
                        when (format) {
                            LivePhoto.Format.IOS_LIVE_PHOTO -> tr(
                                "这是 iOS 实况的静图，请接着选它配对的 MOV",
                                "iOS Live Photo still — pick its paired MOV next"
                            )
                            LivePhoto.Format.VIVO_SPLIT -> tr(
                                "这是分离式实况，请接着选它配对的 MP4",
                                "Split motion photo — pick its paired MP4 next"
                            )
                            else -> tr(
                                "这张照片里没抠到动态视频；如果它是分离式实况，请接着选配对的 MOV/MP4，不是的话直接返回即可",
                                "No embedded motion found; if this is a split Live Photo, pick its paired clip, or just go back"
                            )
                        }
                    )
                    pairPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    return@launch
                }
                state.toast(
                    when {
                        // 识别阶段连文件都没读到
                        format == null -> tr(
                            "识别不出这张照片的格式，请用手机原相机拍摄的实况照片再试",
                            "Cannot identify this photo; use a Live Photo from the stock camera"
                        )
                        else -> {
                            val why = outcome.reason
                            if (why.isNotBlank()) {
                                tr("实况处理失败：$why", "Live photo failed: $why")
                            } else {
                                tr("这张照片里没有内嵌视频（${format.zh}）", "No embedded video (${format.en})")
                            }
                        }
                    }
                )
                return@launch
            }
            finishMedia(prepared, live = true)
            state.toast(tr("实况已自动处理（${sizeText(prepared.file.length())}）", "Live photo processed (${sizeText(prepared.file.length())})"))
        }
    }

    // 添加照片/视频的来源面板：系统面板支持一次多选，图片回到照片区，视频按列表顺序处理
    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val images = ArrayList<Uri>()
            val videos = ArrayList<Uri>()
            uris.forEach { uri ->
                val type = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
                if (type.startsWith("video")) videos += uri else images += uri
            }
            if (images.isNotEmpty()) onPhotosPicked(images)
            if (videos.isNotEmpty()) pickVideo(videos)
        }
    }
    /** 相机写入的目标文件（回调里要用它做清理，必须留在 state 里） */
    var cameraFile by remember { mutableStateOf<java.io.File?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var showSourceSheet by remember { mutableStateOf(false) }
    var showBgmSheet by remember { mutableStateOf(false) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = cameraUri
        if (ok && uri != null) {
            onPhotosPicked(listOf(uri))
        } else {
            // 取消拍摄：删掉系统相机刚建的空文件，别让缓存越攒越大
            runCatching { cameraFile?.delete() }
        }
        cameraUri = null
        cameraFile = null
    }

    fun takePhoto() {
        if (saving || savedFp != null) return
        val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
        val file = java.io.File(dir, "shot_${System.currentTimeMillis()}.jpg")
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
        if (uri == null) {
            state.toast(tr("无法创建拍照文件", "Could not create a photo file"))
            return
        }
        cameraFile = file
        cameraUri = uri
        // 没有相机应用 / 被系统策略禁用时 launch 会抛异常，不能让它闪退
        runCatching { cameraLauncher.launch(uri) }.onFailure {
            cameraUri = null
            cameraFile = null
            runCatching { file.delete() }
            state.toast(tr("没有可用的相机应用", "No camera app available"))
        }
    }

    // 桌面小组件「拍照即定位」：进入发布页后直接唤起一次相机，标志位用完即清，
    // 避免返回后再次进入发布页又自动弹相机。
    LaunchedEffect(state.autoOpenCameraOnUpload) {
        if (state.autoOpenCameraOnUpload) {
            state.autoOpenCameraOnUpload = false
            if (useStudioCapture) showStudioCapture = true else takePhoto()
        }
    }

    /** 添加照片：新版直接进抖音式取景页，旧版弹来源面板（相册 / 系统相机） */
    fun addPhoto() {
        if (useStudioCapture) showStudioCapture = true else showSourceSheet = true
    }

    /**
     * 足迹落库后，按「公开足迹」开关决定是否同步发布到微足迹公开流。
     * 这样从拍摄到发布是一条完整的链：拍照 → 特效 → 保存足迹 → 公开即上微足迹。
     * 发布幂等：已经发布过就不再重复写快照（避免重复确认保存时叠加）。
     */
    fun publishIfPublic(fp: Footprint) {
        if (!isPublic || publishedPostId != null) return
        val caption = note.trim().ifBlank { fp.note }
        val place = city.trim().ifBlank { fp.city }
        publishedPostId = state.publishFootprintToFeed(
            footprint = fp,
            caption = caption,
            tags = tags.toList(),
            place = place,
            city = place
        )
    }

    fun save() {
        if (saving || savedFp != null) return
        // 标题与地点分离：标题可留空，留空时用所选地点名兜底，避免选了城市却卡在"请填写"
        val finalName = name.trim().ifBlank { city.trim() }
        if (finalName.isBlank()) {
            state.toast(tr("请填写标题或选择地点"))
            return
        }
        scope.launch {
            saving = true
            val id = state.nextFootprintId()
            // 先落盘照片再保存足迹：id 一旦确定就固定，照片目录与足迹一一对应
            val paths = PhotoStore.copyPhotos(context, id, photos)
            if (photos.isNotEmpty() && paths.size < photos.size) {
                state.toast(tr("部分照片保存失败，其余信息已保存"))
            }
            // 视频 / 实况 / 配乐：缓存目录里的成品直接搬进足迹目录，不重新编码
            // 支持多段：第一条进 videoPath 兼容旧逻辑，全部路径写进 videoPaths
            val allMedia = listOfNotNull(mediaFile) + extraMediaFiles
            val mediaPaths = allMedia.mapNotNull { item ->
                withContext(Dispatchers.IO) { PhotoStore.copyMedia(context, id, item.file, "media") }
            }
            val mediaPath = mediaPaths.firstOrNull()
            val totalVideoBytes = allMedia.sumOf { it.bytes }
            val bgmPath = bgm?.path?.let { src ->
                withContext(Dispatchers.IO) { PhotoStore.copyMedia(context, id, java.io.File(src), "bgm") }
            }
            delay(400)
            // 没有选点时落在示意图默认中心：此时不能顺手编一个经纬度，
            // 否则「未选位置」和「选了位置」在数据上就分不出来了
            val exact = coords ?: marker?.let { ChinaMapData.mapToLonLat(it.x, it.y) }
            val fp = Footprint(
                id = id,
                city = city.trim().ifBlank { finalName },
                name = finalName,
                note = note.trim().ifEmpty { tr("刚刚记录的一段旅程", "A journey just recorded") },
                // 统一成 yyyy-MM-dd：原来未选日期时写的是 "yyyy年M月"，两种格式混在
                // Footprint.date 里，导致各处要维护三套不同的解析正则
                date = dateMillis?.let { formatDate(it) } ?: formatDate(System.currentTimeMillis()),
                x = marker?.x ?: (ChinaMapData.MAP_MIN_X + ChinaMapData.MAP_MAX_X) / 2f,
                y = marker?.y ?: (ChinaMapData.MAP_MIN_Y + ChinaMapData.MAP_MAX_Y) / 2f,
                special = special,
                specialReason = if (special) tr("这段旅程值得被点亮") else null,
                seed = id % 10,
                photoPaths = paths,
                dateMillis = dateMillis,
                lat = exact?.second,
                lon = exact?.first,
                videoPath = mediaPath,
                videoPaths = mediaPaths,
                videoIsLive = mediaIsLive,
                videoBytes = totalVideoBytes,
                bgmPath = bgmPath,
                bgmTitle = if (bgmPath != null) bgm?.title else null,
                bgmArtist = if (bgmPath != null) bgm?.artist else null,
                bgmSource = if (bgmPath != null) bgm?.source else null
            )

            // 查重：检查最近90天内有没有1公里以内的足迹
            // 优先用精确经纬度；旧数据/无坐标时退回示意地图坐标反算
            val currentLonLat = ChinaMapData.mapToLonLat(fp.x, fp.y)
            val currentPoint = GeoPoint(currentLonLat.second, currentLonLat.first)
            val ninetyDaysAgo = System.currentTimeMillis() - 90 * 24 * 60 * 60 * 1000L
            val duplicate = state.footprints.any { existing ->
                // 只比较最近90天的，且不是默认位置的足迹
                if ((existing.dateMillis ?: 0L) < ninetyDaysAgo) return@any false
                if (existing.x == (ChinaMapData.MAP_MIN_X + ChinaMapData.MAP_MAX_X) / 2f
                    && existing.y == (ChinaMapData.MAP_MIN_Y + ChinaMapData.MAP_MAX_Y) / 2f
                ) return@any false
                val existingLonLat = ChinaMapData.mapToLonLat(existing.x, existing.y)
                val existingPoint = GeoPoint(existingLonLat.second, existingLonLat.first)
                haversineKm(currentPoint, existingPoint) < 1.0 // 1公里以内
            }

            if (duplicate) {
                // 有重复，弹出确认对话框
                pendingFootprint = fp
                showDuplicateConfirm = true
                saving = false
            } else {
                // 无重复，直接保存；不立即关页，让底部按钮完成「进度环 → 打开足迹」的变形
                state.addFootprint(fp)
                saving = false
                savedFp = fp
                // 公开足迹：保存即同步发布到微足迹，打通「拍摄→发布」完整链路
                publishIfPublic(fp)
                // 已经落库了，之后返回不该再问「放弃这次记录？」
                state.markUploadDirty(false)
                // 没在地图上选点时坐标会落到默认位置，明确告知用户，避免回头在地图上找不到
                state.toast(
                    if (marker == null) tr("已保存（未选位置，落在默认位置）")
                    else if (publishedPostId != null) tr("足迹已保存，并发布到微足迹")
                    else tr("足迹保存成功")
                )
            }
        }
    }

    /** 用户确认重复后直接保存 */
    fun confirmSaveDuplicate() {
        scope.launch {
            saving = true
            val fp = pendingFootprint ?: return@launch
            state.addFootprint(fp)
            saving = false
            savedFp = fp
            publishIfPublic(fp)
            state.markUploadDirty(false)
            state.toast(if (publishedPostId != null) tr("足迹已保存，并发布到微足迹") else tr("足迹保存成功"))
            showDuplicateConfirm = false
            pendingFootprint = null
        }
    }

    /**
     * AI 一键写备注：只把地点、日期、照片和已有文字交给模型，
     * 只回填「旅行备注」；标题和主题标签保持用户自己填的内容，避免不同步。
     */
    fun runAiFootprint() {
        if (aiBusy || saving) return
        if (photos.isEmpty()) {
            state.toast(tr("先添加至少一张旅行照片，AI 才有内容可写"))
            return
        }
        if (city.isBlank() && coords == null) {
            state.toast(tr("先选择地点（城市或地图选点），AI 才能写得具体"))
            return
        }
        val ep = state.aiFeatureEndpoint(AiFeature.FOOTPRINT_SUMMARY)
        if (!ep.ready) {
            state.toast(tr("AI 写备注需要先接入云端模型，请到「设置 - API 模型管理」添加", "Connect a cloud model first in Settings → API models"))
            return
        }
        val place = city.ifBlank {
            coords?.let { String.format(Locale.US, "%.4f, %.4f", it.second, it.first) }
                ?: tr("未提供")
        }
        val facts = buildString {
            appendLine("地点：$place")
            appendLine("日期：${dateMillis?.let { formatDate(it) } ?: "未提供"}")
            appendLine("照片数量：${photos.size}")
            appendLine("位置来源：${if (geoFromPhoto) "照片 EXIF" else "用户手动选择"}")
            appendLine("已有备注（可能含端侧照片识别标签，不要照抄标签）：${note.trim().ifBlank { "无" }}")
        }
        scope.launch {
            aiBusy = true
            val draft = AiApi.composeFootprint(ep, facts)
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
            state.toast(tr("AI 已写好旅行备注，标题和标签保持不变"))
        }
    }

    // 这是从底部上滑的全屏叠层页（MainActivity 的 AnimatedVisibility 分支），必须铺实心底：
    // 自定义背景态下 Bg.Background 是 Transparent，下层页面的照片会整片透上来，
    // 步骤条、照片区标题、"0/9"、说明文字全部和底图糊在一起。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Layer.Opaque)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // 标题栏只放标题文字；退出/保存移入内容区
        AppTopBar(title = if (aiAssist) tr("AI 写旅行备注", "AI travel note") else tr("新建足迹"))
        BackActionRow(
            leading = {
                // 退出按钮：箭头 + 文字。系统返回键与它走同一条判断（有内容先确认）
                BackExitButton(
                    text = if (savedFp != null) tr("完成", "Done") else tr("退出", "Exit"),
                    onClick = { requestExit() }
                )
            },
            actions = {
                TextButton(onClick = { save() }, enabled = !saving) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Brand.Terracotta)
                    } else {
                        Text(tr("保存"), color = Brand.Terracotta, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        )

        // 步骤指示
        StepIndicator(photos.isNotEmpty())

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // AI 一键生成：从 AI 助手进来时就是主入口，普通新建也能直接用
            item {
                val aiModelName = aiModelLabel(state, AiFeature.FOOTPRINT_SUMMARY)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brand.Terracotta.copy(alpha = 0.08f))
                        .border(1.dp, Brand.Terracotta.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = Brand.Terracotta,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                tr("AI 帮写旅行备注", "AI travel note"),
                                color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            // 小字标出这枚按钮实际会请求哪个模型；长按换模型，不必绕去设置页
                            Text(
                                tr("模型：$aiModelName", "Model: $aiModelName"),
                                color = Text.Muted, fontSize = 9.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .pointerInput(aiModelName) {
                                        detectTapGestures(onLongPress = { showAiModelPicker = true })
                                    }
                                    .padding(horizontal = 2.dp, vertical = 2.dp)
                            )
                        }
                        if (aiAssist) {
                            Text(
                                tr("来自 AI 旅行助手", "From AI assistant"),
                                color = Text.Muted, fontSize = 10.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr("先选照片和地点，AI 只帮你写旅行备注；标题和主题标签仍由你自己填。",
                            "Pick photos and a place first; AI writes the travel note only. Title and tags stay yours."),
                        color = Text.Secondary, fontSize = 12.sp, lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Brand.Terracotta)
                            .clickable(enabled = !aiBusy) { runAiFootprint() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (aiBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(7.dp))
                            } else {
                                Icon(
                                    Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(7.dp))
                            }
                            Text(
                                if (aiBusy) tr("AI 正在写备注…", "AI is drafting…") else tr("AI 帮我写备注", "Write note with AI"),
                                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // 旅行照片（视频 / 实况也作为同一格排进来）
            item {
                SectionTitle(
                    tr("旅行照片", "Travel photos"),
                    buildString {
                        append("${photos.size}/$MAX_PUBLISH_PHOTOS")
                        mediaFile?.let { append(" · ${if (mediaIsLive) tr("实况", "Live") else tr("视频", "Video")}") }
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (useStudioCapture) tr("新版取景已开启，点下方加号拍照", "New camera on — tap + to shoot")
                        else tr("想用抖音式取景拍照？", "Want the Douyin-style camera?"),
                        color = Text.Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            val next = !useStudioCapture
                            useStudioCapture = next
                            state.updateUploadStudioCapture(next)
                            if (next) {
                                showStudioCapture = true
                            } else {
                                state.toast(tr("已切回旧版拍照", "Back to the old camera"))
                            }
                        }
                    ) {
                        Text(
                            if (useStudioCapture) tr("切回旧版", "Old version") else tr("切换新版", "New version"),
                            color = Brand.Terracotta,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            item {
                PhotoGridEditor(
                    photos = photos,
                    maxCount = MAX_PUBLISH_PHOTOS,
                    onAdd = { addPhoto() },
                    onRemove = { index ->
                        // 连位置一起记住：删掉的是中间某张，撤销要还原到原来的顺序（封面顺序是用户排的）
                        val removed = photos.getOrNull(index)
                        if (removed != null) {
                            if (previewIndex == index) previewIndex = null
                            photos = photos.filterIndexed { i, _ -> i != index }
                            undoRemoved = index to removed
                        }
                    },
                    onPreview = { index -> previewIndex = index },
                    videoPath = mediaFile?.file?.absolutePath,
                    videoIsLive = mediaIsLive,
                    videoSeconds = mediaSeconds,
                    onRemoveVideo = {
                        runCatching { mediaFile?.file?.delete() }
                        mediaFile = null
                        mediaSeconds = 0
                        mediaSpec = ""
                        previewVideo = null
                    },
                    onPreviewVideo = { mediaFile?.let { previewVideo = it.file.absolutePath } }
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("点照片看大图 · 第一张作为封面 · 视频格子点开预览，右上角 × 移除",
                            "Tap a photo to preview · first one is the cover · tap the video tile to play, × removes it"),
                        color = Text.Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    // 误删是最容易手抖的操作，给 5 秒撤销，比弹二次确认更顺手
                    undoRemoved?.let { (pos, uri) ->
                        TextButton(
                            onClick = {
                                val list = photos.toMutableList()
                                val at = pos.coerceIn(0, list.size)
                                list.add(at, uri)
                                photos = list
                                undoRemoved = null
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(tr("撤销删除", "Undo"), color = Brand.Terracotta, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 追加视频：第一条在封面格，其余全部列在这里；不设总数上限
            if (extraMediaFiles.isNotEmpty()) {
                item {
                    Text(
                        tr("已添加视频（${extraMediaFiles.size} 段）", "Added videos (${extraMediaFiles.size})"),
                        color = Text.Secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(extraMediaFiles.size) { index ->
                            val item = extraMediaFiles[index]
                            Box(
                                modifier = Modifier
                                    .size(width = 92.dp, height = 120.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { previewVideo = item.file.absolutePath }
                            ) {
                                VideoPosterCover(item.file.absolutePath, Modifier.fillMaxSize())
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xB3101318))
                                        .clickable {
                                            runCatching { item.file.delete() }
                                            extraMediaFiles = extraMediaFiles.filterIndexed { i, _ -> i != index }
                                            if (previewVideo == item.file.absolutePath) previewVideo = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null,
                                        tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // 短视频链接：支持 MP4 直链和部分分享页，解析出来的视频直接进入待发布列表
            item {
                Column {
                    Text(
                        tr("短视频链接", "Short video link"),
                        color = Text.Secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = videoLink,
                            onValueChange = { videoLink = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(tr("粘贴分享链接或 MP4 直链", "Paste a share link or MP4 URL"), fontSize = 12.sp) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = CardBg,
                                unfocusedContainerColor = CardBg,
                                focusedIndicatorColor = Brand.Terracotta,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { resolveVideoLink() },
                            enabled = !linkBusy && !mediaBusy && videoLink.isNotBlank(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (linkBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Text(tr("解析", "Resolve"), fontSize = 12.sp)
                            }
                        }
                    }
                    if (linkStatus.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(linkStatus,
                            color = if (linkStatus.startsWith("解析失败") || linkStatus.startsWith("Failed")) Text.Muted else Brand.Terracotta,
                            fontSize = 11.sp)
                    }
                }
            }

            // 视频处理进度：只在转码/分析时挂一条细进度，不再单独占一张卡片
            item {
                if (mediaBusy) {
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            mediaStatus.ifBlank { tr("正在处理视频…", "Processing video…") },
                            color = Text.Secondary, fontSize = 11.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        // 真实进度：转码/上传回调给多少就画多少，动画只负责抹平跳变
                        SmoothProgressBar(
                            progress = mediaProgress,
                            height = 6.dp,
                            showPercent = true
                        )
                    }
                }
            }

            // 配音 · 背景音乐
            item {
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(Bg.Surface)
                        .cardShadow()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("配音 · 背景音乐"), color = Text.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            bgm?.let {
                                "${it.title}${it.artist?.let { a -> " · $a" } ?: ""}   ${it.source}"
                            } ?: tr("选一首配乐，播放这段回忆时一起放", "Add a track that plays with this memory"),
                            color = Text.Muted, fontSize = 11.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (bgm == null) tr("选择", "Pick") else tr("更换", "Change"),
                        color = Brand.Terracotta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(6.dp).clickable(enabled = !saving) { showBgmSheet = true }
                    )
                    if (bgm != null) {
                        Text(
                            tr("移除", "Remove"), color = Text.Secondary, fontSize = 12.sp,
                            modifier = Modifier.padding(6.dp).clickable(enabled = !saving) { bgm = null }
                        )
                    }
                }
            }

            // 解析出的短视频配乐直接进同一套流光播放器：先听，再决定保不保存
            bgm?.let { picked ->
                item {
                    Spacer(Modifier.height(8.dp))
                    MusicBgmStrip(
                        path = picked.path,
                        title = picked.title,
                        artist = picked.artist
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr(
                            "左右滑动跳到播放时间 · 点一下暂停/继续 · 长按换样式",
                            "Drag to seek · tap to pause/resume · hold to change style"
                        ),
                        color = Text.Muted, fontSize = 10.sp
                    )
                }
            }

            // 基本信息卡片
            item {
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(Bg.Surface)
                        .cardShadow()
                        .padding(16.dp)
                ) {
                    Text(tr("基本信息"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))

                    Text(tr("标题"), color = Text.Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Bg.SurfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 2.dp)
                    ) {
                        TextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(tr("例如：洱海骑行"), color = Text.Muted, fontSize = 14.sp) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Text.Primary, fontSize = 14.sp),
                            colors = fieldColors()
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    Text(tr("选择地点（城市快捷选择 或 地图选点）"), color = Text.Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(cityChips, key = { it }) { c ->
                            CityChip(tr(c), selected = city == c, onClick = {
                                city = c
                                geoFromPhoto = false
                                // 城市快捷选择必须同时给坐标，否则保存时会落到示意图正中心
                                val ll = CityCoords.lonLat(c)
                                if (ll != null) {
                                    val (x, y) = ChinaMapData.lonLatToMap(ll.first, ll.second)
                                    marker = Offset(x, y)
                                    coords = ll
                                } else {
                                    marker = null
                                    coords = null
                                }
                            })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.dp, Brand.Terracotta.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                            .background(Brand.Terracotta.copy(alpha = 0.04f))
                            .clickable { state.openMapPick() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.MyLocation, tr("地图选点"), tint = Brand.Terracotta, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (marker != null) tr("✓ 已选择地点（点击地图调整）") else tr("地图选点"),
                                color = if (marker != null) Brand.Terracotta else Text.Muted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (geoFromPhoto) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tr("📍 位置由照片 EXIF 自动读取，可点上方地图选点调整"),
                            color = Brand.Terracotta, fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("出行日期"), color = Text.Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Bg.SurfaceVariant)
                                    .clickable { showDatePicker = true }
                                    .padding(horizontal = 14.dp, vertical = 14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.CalendarMonth, null, tint = Brand.Terracotta, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = dateMillis?.let { formatDate(it) } ?: tr("选择日期"),
                                        color = if (dateMillis != null) Text.Primary else Text.Muted,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            if (dateFromPhoto) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    tr("🕒 日期来自照片拍摄时间，可点击修改"),
                                    color = Brand.Terracotta, fontSize = 12.sp
                                )
                            } else if (photos.isNotEmpty() && dateMillis == null) {
                                Spacer(Modifier.height(6.dp))
                                Text(tr("未识别到照片拍摄时间"), color = Text.Muted, fontSize = 12.sp)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("与谁同行"), color = Text.Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Bg.SurfaceVariant)
                                    .clickable { showCompanionMenu = true }
                                    .padding(horizontal = 14.dp, vertical = 14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = companion, color = Text.Primary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Icon(Icons.Outlined.KeyboardArrowDown, null, tint = Text.Muted)
                                }
                            }
                            DropdownMenu(expanded = showCompanionMenu, onDismissRequest = { showCompanionMenu = false }) {
                                companionOptions.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(tr(c), fontSize = 14.sp) },
                                        onClick = { companion = c; showCompanionMenu = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 旅行备注
            item {
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(Bg.Surface)
                        .cardShadow()
                        .padding(16.dp)
                ) {
                    Text(tr("旅行备注"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Bg.SurfaceVariant)
                    ) {
                        TextField(
                            value = note,
                            onValueChange = { if (it.length <= 500) note = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            placeholder = {
                                Text(tr("写下这段旅程的故事 …"), color = Text.Muted, fontSize = 14.sp)
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Text.Primary, fontSize = 14.sp, lineHeight = 20.sp),
                            colors = fieldColors()
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${note.length}/500", color = Text.Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.End))
                }
            }

            // 主题标签
            item {
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(Bg.Surface)
                        .cardShadow()
                        .padding(16.dp)
                ) {
                    Text(tr("主题标签"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        themeTagOptions.forEach { t ->
                            UploadTagChip(
                                text = tr(t),
                                selected = t in tags,
                                onClick = { tags = if (t in tags) tags - t else tags + t }
                            )
                        }
                    }
                }
            }

            // 推荐指数 + 当天天气
            item {
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(Bg.Surface)
                        .cardShadow()
                        .padding(16.dp)
                ) {
                    Text(tr("推荐指数"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        (1..5).forEach { i ->
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = tr("第 $i 颗星", "Star $i"),
                                tint = if (i <= rating) Brand.Gold else Bg.SurfaceDim,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable { rating = if (rating == i) 0 else i }
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(tr("点星评分（可选）"), color = Text.Muted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(tr("当天天气"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    weatherOptions.chunked(4).forEach { rowW ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowW.forEach { (emoji, label) ->
                                WeatherChip(
                                    emoji = emoji,
                                    label = tr(label),
                                    selected = weather == label,
                                    onClick = { weather = if (weather == label) null else label }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            // 特殊设置
            item {
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(Bg.Surface)
                        .cardShadow()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = Brand.Gold, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr("特别地点"), color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(tr("标记后在地图上金色高亮展示"), color = Text.Muted, fontSize = 12.sp)
                        }
                        UploadSwitch(checked = special, onToggle = { special = !special })
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Bg.Outline))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("公开足迹"), color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(tr("公开后其他旅友可在地图上看到"), color = Text.Muted, fontSize = 12.sp)
                        }
                        UploadSwitch(checked = isPublic, onToggle = { isPublic = !isPublic })
                    }
                }
            }

            // 提交按钮：点击收缩成圆显环形进度，完成展开回按钮，文字切为「打开足迹」
            item {
                Spacer(Modifier.height(20.dp))
                MorphSaveButton(
                    phase = when {
                        saving -> MorphSavePhase.Saving
                        savedFp != null -> MorphSavePhase.Done
                        else -> MorphSavePhase.Idle
                    },
                    onSave = { save() },
                    onOpen = {
                        savedFp?.let { fp ->
                            // 保存后手动补发到微足迹（保存时没开公开开关，或想再发一次）
                            if (publishedPostId == null) {
                                isPublic = true
                                publishedPostId = null
                                publishIfPublic(fp)
                            }
                        }
                        state.closeUpload()
                        state.activeTab = AppTab.MAP
                        state.microFeedTab = 3
                    }
                )
                // 保存成功后的发布状态提示：公开时给「去微足迹看」的明确出口
                if (savedFp != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(Bg.SurfaceVariant)
                            .clickable {
                                state.closeUpload()
                                state.activeTab = AppTab.MAP
                                state.microFeedTab = if (publishedPostId != null) 3 else 1
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (publishedPostId != null) Icons.Filled.Star else Icons.Filled.Public,
                            contentDescription = null, tint = Brand.Terracotta,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (publishedPostId != null) tr("已发布到微足迹，去看看")
                            else tr("发布到微足迹，让更多旅友看到"),
                            color = Text.Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    // 添加照片：先选来源（相册 / 拍照 / 视频·实况）
    if (showSourceSheet) {
        PhotoSourceSheet(
            onDismiss = { showSourceSheet = false },
            onAlbum = { showSourceSheet = false; openPicker() },
            onCamera = { showSourceSheet = false; takePhoto() },
            onMedia = {
                showSourceSheet = false
                mediaPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            }
        )
    }

    // 新版取景页：拍完的照片继续走原链路（EXIF 回填 / AI 备注 / 保存），
    // 视频交给 pickVideo 做抖音规格化；选音乐与「旧版拍照」都先关掉取景页再执行。
    if (showStudioCapture) {
        DouyinCaptureScreen(
            state = state,
            onClose = { showStudioCapture = false },
            onPhotos = { uris ->
                onPhotosPicked(uris)
                state.toast(tr("已加入 ${uris.size} 张照片", "Added ${uris.size} photo(s)"))
            },
            onVideo = { uri ->
                showStudioCapture = false
                pickVideo(listOf(uri))
            },
            onPickMusic = {
                showStudioCapture = false
                showBgmSheet = true
            },
            onSwitchBack = {
                useStudioCapture = false
                state.updateUploadStudioCapture(false)
                showStudioCapture = false
                takePhoto()
            },
            onCaptionGenerated = { caption, newTags ->
                // AI 创作 / 灵感跟拍 / 创作灵感：文案填进备注，话题并入标签
                if (caption.isNotBlank()) {
                    note = if (note.isBlank()) caption else "$note\n$caption"
                }
                if (newTags.isNotEmpty()) tags = tags + newTags
            },
            musicLabel = bgm?.title
        )
    }

    // 配乐选择器：曲源是 App 自己的音乐库（已下载的歌）+ 本地音频文件
    if (showBgmSheet) {
        BgmPickerSheet(
            onDismiss = { showBgmSheet = false },
            onPick = { showBgmSheet = false; bgm = it }
        )
    }

    // 全屏看图：滑动切换、设为封面、删除。放在主布局之后，保证盖在最上层
    previewIndex?.let { index ->
        PhotoViewerDialog(
            photos = photos,
            startIndex = index,
            onDismiss = { previewIndex = null },
            onDelete = { i ->
                val next = photos.filterIndexed { idx, _ -> idx != i }
                photos = next
                // 删空了就顺手关掉预览，否则会停在一个空白黑屏上
                if (next.isEmpty()) previewIndex = null
            },
            onSetCover = { i ->
                if (i in photos.indices && i != 0) {
                    val list = photos.toMutableList()
                    val cover = list.removeAt(i)
                    list.add(0, cover)
                    photos = list
                }
            }
        )
    }

    // 全屏看视频：点画面暂停/继续，右上角关闭
    previewVideo?.let { path ->
        Dialog(
            onDismissRequest = { previewVideo = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                MemoryMediaBlock(
                    videoPath = path,
                    videoIsLive = mediaIsLive,
                    bgmPath = null,
                    bgmTitle = null,
                    maxHeight = 620.dp
                )
                Text(
                    tr("关闭", "Close"),
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x66000000))
                        .clickable { previewVideo = null }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            containerColor = Bg.Surface,
            title = @Composable {
                Text(tr("放弃这次记录？", "Discard this entry?"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = @Composable {
                Text(
                    if (photos.isNotEmpty()) {
                        tr("已选的 ${photos.size} 张照片和填写的内容都不会保留。",
                            "${photos.size} selected photos and what you filled in will be lost.")
                    } else {
                        tr("填写的内容不会保留。", "What you filled in will be lost.")
                    },
                    color = Text.Secondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = @Composable {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    state.closeUpload(force = true)
                }) {
                    Text(tr("放弃", "Discard"), color = Func.Danger, fontSize = 14.sp)
                }
            },
            dismissButton = @Composable {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(tr("继续编辑", "Keep editing"), color = Brand.Terracotta, fontSize = 14.sp)
                }
            }
        )
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateMillis = dateState.selectedDateMillis
                    // 用户显式选过日期后，后续再加照片不再覆盖他的选择
                    dateFromPhoto = false
                    showDatePicker = false
                }) { Text(tr("确定"), color = Brand.Terracotta) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(tr("取消"), color = Text.Secondary) }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    // 重复足迹确认对话框
    if (showDuplicateConfirm) {
        AlertDialog(
            onDismissRequest = { showDuplicateConfirm = false; pendingFootprint = null },
            containerColor = Bg.Surface,
            title = @Composable { Text(tr("检测到重复地点"), color = Text.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = @Composable { Text(tr("你最近90天内已经在这个地点1公里范围内记录过足迹了，是否仍要保存？"), color = Text.Secondary, fontSize = 13.sp) },
            confirmButton = @Composable {
                TextButton(onClick = { confirmSaveDuplicate() }) {
                    Text(tr("仍要保存"), color = Brand.Terracotta, fontSize = 14.sp)
                }
            },
            dismissButton = @Composable {
                TextButton(onClick = { showDuplicateConfirm = false; pendingFootprint = null }) {
                    Text(tr("取消"), color = Text.Secondary, fontSize = 14.sp)
                }
            }
        )
    }

    // 长按模型小字：只为「足迹总结」换模型，不影响其他 AI 功能
    if (showAiModelPicker) {
        AiModelQuickSwitchDialog(
            feature = AiFeature.FOOTPRINT_SUMMARY,
            state = state,
            onDismiss = { showAiModelPicker = false }
        )
    }

    // 地图选点页：确认后回填坐标、地点名与到访时间
    if (showMapPicker) {
        MapPickScreen(
            initialMarker = marker,
            initialName = city,
            initialDateMillis = dateMillis,
            initialLat = coords?.second,
            initialLon = coords?.first,
            onConfirm = { lon, lat, pickedName, date ->
                val (x, y) = ChinaMapData.lonLatToMap(lon, lat)
                marker = Offset(x, y)
                coords = lon to lat
                geoFromPhoto = false
                // 地图选到的地点名作为"地点"，不覆盖用户自己起的标题
                if (pickedName.isNotBlank()) city = pickedName
                if (date != null) {
                    dateMillis = date
                    dateFromPhoto = false
                }
                state.closeMapPick()
                state.toast(
                            if (pickedName.isNotBlank())
                                tr("已选地点：$pickedName", "Location: $pickedName")
                            else tr("已选地点")
                        )
            },
            onDismiss = { state.closeMapPick() }
        )
    }
}

@Composable
private fun StepIndicator(hasPhotos: Boolean) {
    val steps = listOf(tr("照片", "Photos"), tr("信息", "Details"), tr("完成", "Done"))
    val currentStep = if (hasPhotos) 1 else 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { i, label ->
            val active = i <= currentStep
            val isCurrent = i == currentStep
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) Brush.linearGradient(MainGradient)
                        else Brush.horizontalGradient(listOf(Bg.SurfaceVariant, Bg.SurfaceVariant))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${i + 1}",
                    color = if (active) Color.White else Text.Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = if (active) Brand.Terracotta else Text.Muted,
                fontSize = 12.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
            )
            if (i < steps.size - 1) {
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(2.dp)
                        .background(if (i < currentStep) Brand.Terracotta else Bg.Outline)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, right: String?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, color = Text.Primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        right?.let { Text(text = it, color = Text.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = Brand.Terracotta
)

@Composable
private fun CityChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) Brand.Terracotta else Bg.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Color.White else Text.Primary, fontSize = 13.sp)
    }
}


/** 一张照片的 EXIF 摘要：GPS 经纬度 + 原始拍摄时间 */
private class ExifInfo(val gps: Pair<Double, Double>?, val dateMillis: Long?)

/**
 * 扫描一批照片的 EXIF，取第一张有 GPS 的、第一张有拍摄时间的。
 * 与原来的 firstNotNullOfOrNull 规则一致，但只遍历一次、每张照片只开一次流。
 */
@Suppress("DEPRECATION")
private fun scanExif(context: Context, uris: List<Uri>): Pair<Pair<Double, Double>?, Long?> {
    var gps: Pair<Double, Double>? = null
    var shotAt: Long? = null
    for (uri in uris) {
        val info = readExifInfo(context, uri) ?: continue
        if (gps == null) gps = info.gps
        if (shotAt == null) shotAt = info.dateMillis
        if (gps != null && shotAt != null) break
    }
    return gps to shotAt
}

@Suppress("DEPRECATION")
private fun readExifInfo(context: Context, uri: Uri): ExifInfo? = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val exif = ExifInterface(input)
        val latLong = FloatArray(2)
        val gps = if (exif.getLatLong(latLong) &&
            latLong[0].toDouble() != 0.0 && latLong[1].toDouble() != 0.0
        ) latLong[0].toDouble() to latLong[1].toDouble() else null
        ExifInfo(gps, readExifTime(exif))
    }
} catch (e: Exception) {
    null
}

/**
 * 读取 EXIF DateTimeOriginal（无则退回 DateTime / DateTimeDigitized）。
 * 兼容多种格式：冒号分隔、横杠分隔、带/不带时分秒；多 Locale 尝试解析。
 * 做两道过滤：解析失败返回 null；落在 1990-01-01 ~ 明天 之外的时间视为脏数据丢弃
 * （相机未设日期会写出 1970 年，部分工具会写出未来时间）。
 * EXIF 的时间字段不带时区，因此按原值取年月日，不做换算。
 */
private fun readExifTime(exif: ExifInterface): Long? {
    val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
        ?: return null

    // 尝试所有常见格式，优先 Locale.CHINA 适配国内相机
    val formats = listOf(
        "yyyy:MM:dd HH:mm:ss" to Locale.CHINA,
        "yyyy-MM-dd HH:mm:ss" to Locale.CHINA,
        "yyyy/MM/dd HH:mm:ss" to Locale.CHINA,
        "yyyy:MM:dd" to Locale.CHINA,
        "yyyy-MM-dd" to Locale.CHINA,
        "yyyy:MM:dd HH:mm:ss" to Locale.US,
        "yyyy-MM-dd HH:mm:ss" to Locale.US
    )

    var millis: Long? = null
    for ((pattern, locale) in formats) {
        millis = runCatching {
            val fmt = SimpleDateFormat(pattern, locale)
            fmt.isLenient = false
            fmt.parse(raw.trim())?.time
        }.getOrNull()
        if (millis != null) break
    }

    if (millis == null) return null
    val min = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse("1990-01-01")?.time
    }.getOrNull() ?: 0L
    val max = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
    return millis.takeIf { it in min..max }
}

/** 自动定位与逆地理编码已移入 MapPickScreen（地图选点页），本页不再使用 */

/**
 * 端侧视觉分析：按用户在「设置 - AI 模型能力」里开启的项逐项执行
 * （图像标签 / OCR / 人脸 / 条码 / 图文翻译），结果拼成紧凑后缀。
 * 解码与识别全部在 IO 线程，失败静默返回 null，不打断发布流程。
 */
private suspend fun visionSuffix(
    context: Context,
    uri: Uri,
    caps: com.example.my.data.ai.AiCapabilities,
    ocrEndpoint: com.example.my.data.AiPresets.OcrEndpoint
): String? {
    if (!caps.anyVision) return null
    // 640px 足够 OCR 与条码：再大只会拖慢识别，小图又读不清票根小字
    val bitmap = withContext(Dispatchers.IO) { PhotoStore.decodeUri(context, uri, 640) } ?: return null
    val result = com.example.my.data.ai.VisionAnalyzer.analyze(
        bitmap, caps, ocrEndpoint = ocrEndpoint,
        localizeLabels = { localizeLabels(it) }
    )
    return result.noteSuffix()
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(millis))

/** 主题标签胶囊 */
@Composable
private fun UploadTagChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) Brand.Terracotta else Bg.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Text.Secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 天气胶囊 */
@Composable
private fun WeatherChip(emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) Brand.Terracotta.copy(alpha = 0.12f) else Bg.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            com.example.my.ui.components.WeatherGlyph.iconOf(emoji),
            contentDescription = null,
            tint = if (selected) Brand.Terracotta else Text.Secondary,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = if (selected) Brand.Terracotta else Text.Secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 赤陶样式开关 */
@Composable
private fun UploadSwitch(checked: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(if (checked) Brand.Terracotta else Bg.SurfaceDim)
            .clickable(onClick = onToggle)
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
