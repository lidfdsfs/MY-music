package com.example.my.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.my.data.AiApi
import com.example.my.data.AiFeature
import com.example.my.state.AppState
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.FixedInk
import com.example.my.ui.theme.blackScrim
import com.example.my.ui.theme.Ink
import com.example.my.util.media.CaptureEffects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 新版拍照入口：抖音式全屏取景器，所有工具均为真实可用功能。
 *
 * 只负责「拍 / 录 / 从相册加 + 拍摄期特效」这一件事，拍完的 Uri 交回 UploadScreen 走原有
 * EXIF 回填、视频规格化、保存链路。设置都是**拍摄期临时态**：翻转、闪光、倒计时、网格线、
 * 美颜、滤镜、水印只影响当前这一次拍摄，不写盘、不改 App 全局设置。
 *
 * 工具面板全部真实生效：
 *  - 设置：构图网格 / 分辨率 / 镜像 / 录制声音
 *  - 灵感跟拍 / 创作灵感：内置模板，一键回填文案与话题
 *  - 倒计时：3s / 10s / 关
 *  - 美颜：磨皮 / 提亮 / 红润，写入照片
 *  - 特效：8 套滤镜，写入照片
 *  - 提词器：取景框内滚动文字
 *  - 文字：文字水印写入照片
 *  - AI 创作：调用 AI 写文案回填
 *  - 开直播：走视频录制链路（本地录制，明确非推流）
 */
@Composable
fun DouyinCaptureScreen(
    state: AppState,
    onClose: () -> Unit,
    onPhotos: (List<Uri>) -> Unit,
    onVideo: (Uri) -> Unit,
    onPickMusic: () -> Unit,
    onSwitchBack: () -> Unit,
    onCaptionGenerated: (caption: String, tags: List<String>) -> Unit = { _, _ -> },
    musicLabel: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }
    var audioGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> audioGranted = granted }
    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var torchOn by remember { mutableStateOf(false) }
    var countdownSec by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var gridOn by remember { mutableStateOf(false) }
    var mirrorOn by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(Quality.HD) }
    var recordAudio by remember { mutableStateOf(true) }
    var moreExpanded by remember { mutableStateOf(false) }
    var mode by remember { mutableIntStateOf(CAPTURE_PHOTO) }
    var captureBusy by remember { mutableStateOf(false) }
    var screenFlash by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    // 拍摄期特效状态
    var beauty by remember { mutableStateOf(CaptureEffects.Beauty()) }
    var filter by remember { mutableStateOf(CaptureEffects.filterById(CaptureEffects.FILTER_NONE)) }
    var watermark by remember { mutableStateOf(CaptureEffects.Watermark("")) }
    var teleprompterText by remember { mutableStateOf("") }
    var teleprompterOn by remember { mutableStateOf(false) }
    var aiBusy by remember { mutableStateOf(false) }

    // 当前打开的面板：null 表示没有
    var openPanel by remember { mutableStateOf<CapturePanel?>(null) }
    var guide by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var recorder by remember { mutableStateOf<Recorder?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var providerRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }

    // PreviewView 是 Android View，remember 住同一实例，重组时不会重建取景表面
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    LaunchedEffect(permissionGranted, lensFacing, quality) {
        if (!permissionGranted) return@LaunchedEffect
        val provider = withContext(Dispatchers.IO) {
            runCatching { ProcessCameraProvider.getInstance(context).get() }.getOrNull()
        }
        if (provider == null) {
            state.toast(tr("相机启动失败，请检查系统相机权限", "Camera failed to start"))
            return@LaunchedEffect
        }
        providerRef = provider
        val preview = Preview.Builder().build()
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(flashMode)
            .build()
        val videoRecorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(quality))
            .build()
        val vcap = VideoCapture.withOutput(videoRecorder)
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val bound = withContext(Dispatchers.Main) {
            runCatching {
                provider.unbindAll()
                val cam = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, vcap)
                preview.setSurfaceProvider(previewView.surfaceProvider)
                imageCapture = capture
                videoCapture = vcap
                recorder = videoRecorder
                camera = cam
                cam
            }.getOrNull()
        }
        if (bound == null) {
            // 部分设备没有前置摄像头：静默退回后置，不要丢一个黑屏给用户
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                lensFacing = CameraSelector.LENS_FACING_BACK
            } else {
                state.toast(tr("相机被其他应用占用", "Camera is in use by another app"))
            }
        }
    }

    // 镜像：仅前置摄像头生效，缩放取景表面 X 轴
    LaunchedEffect(mirrorOn, lensFacing) {
        previewView.scaleX = if (mirrorOn && lensFacing == CameraSelector.LENS_FACING_FRONT) -1f else 1f
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recording?.stop() }
            runCatching { providerRef?.unbindAll() }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris -> if (uris.isNotEmpty()) onPhotos(uris) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onVideo(uri) }

    fun cameraDir(): File = File(context.cacheDir, "camera").apply { mkdirs() }

    fun captureStill() {
        val capture = imageCapture
        if (capture == null || captureBusy) {
            if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
            else if (capture == null) state.toast(tr("相机还没准备好", "Camera is not ready"))
            return
        }
        captureBusy = true
        val file = File(cameraDir(), "studio_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    captureBusy = false
                    screenFlash = true
                    scope.launch {
                        delay(120)
                        screenFlash = false
                        // 拍摄期特效：滤镜 + 美颜 + 水印真实写入照片
                        withContext(Dispatchers.IO) {
                            runCatching {
                                CaptureEffects.applyToFile(context, file, filter, beauty, watermark)
                            }
                        }
                        val uri = runCatching {
                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        }.getOrNull()
                        if (uri != null) onPhotos(listOf(uri))
                        else state.toast(tr("照片已拍下，但无法加入足迹", "Shot taken but could not be added"))
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    captureBusy = false
                    runCatching { file.delete() }
                    state.toast(tr("拍照失败，请重试", "Capture failed, try again"))
                }
            })
    }

    fun toggleRecording() {
        val active = recording
        if (active != null) {
            runCatching { active.stop() }
            return
        }
        val capture = videoCapture
        if (capture == null) {
            state.toast(tr("相机还没准备好", "Camera is not ready"))
            return
        }
        val file = File(cameraDir(), "studio_${System.currentTimeMillis()}.mp4")
        val options = FileOutputOptions.Builder(file).build()
        recordingSeconds = 0
        val videoRecorder = recorder
        if (videoRecorder == null) {
            state.toast(tr("相机还没准备好", "Camera is not ready"))
            return
        }
        val useAudio = recordAudio && audioGranted
        val pending = videoRecorder.prepareRecording(context, options)
        recording = (if (useAudio) pending.withAudioEnabled() else pending).start(
            ContextCompat.getMainExecutor(context),
            androidx.core.util.Consumer<VideoRecordEvent> { event ->
                when (event) {
                    is VideoRecordEvent.Start -> Unit
                    is VideoRecordEvent.Status -> {
                        recordingSeconds = (event.recordingStats.recordedDurationNanos / 1_000_000_000L).toInt()
                    }
                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        recordingSeconds = 0
                        if (!event.hasError()) {
                            val uri = runCatching {
                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            }.getOrNull()
                            if (uri != null) onVideo(uri)
                        } else {
                            runCatching { file.delete() }
                            state.toast(tr("录像失败，请重试", "Recording failed, try again"))
                        }
                    }
                }
            }
        )
    }

    fun onShutter() {
        if (mode == CAPTURE_VIDEO) {
            if (recordAudio && !audioGranted) audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            toggleRecording()
            return
        }
        if (captureBusy || countdown > 0) return
        val delaySec = countdownSec
        if (delaySec > 0) {
            scope.launch {
                for (i in delaySec downTo 1) {
                    countdown = i
                    delay(1000)
                }
                countdown = 0
                captureStill()
            }
        } else {
            captureStill()
        }
    }

    fun toggleFlash() {
        val cam = camera
        if (cam == null || !cam.cameraInfo.hasFlashUnit()) {
            state.toast(tr("当前设备没有闪光灯", "No flash on this device"))
            return
        }
        val next = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        flashMode = next
        imageCapture?.flashMode = next
        if (next == ImageCapture.FLASH_MODE_ON) {
            torchOn = true
            runCatching { cam.cameraControl.enableTorch(true) }
        } else if (torchOn) {
            torchOn = false
            runCatching { cam.cameraControl.enableTorch(false) }
        }
    }

    /** AI 创作：调用 AI 写一段旅行文案 + 话题，回填到发布页 */
    fun runAiCreate() {
        if (aiBusy) return
        val ep = state.aiFeatureEndpoint(AiFeature.FOOTPRINT_SUMMARY)
        if (!ep.ready) {
            guide = Triple(
                tr("AI 创作需要云端模型", "AI creation needs a cloud model"),
                tr("到「设置 - API 模型管理」添加模型后即可用 AI 写文案。也可以先用「灵感跟拍」里的模板。",
                    "Add a model in Settings - API Models to let AI write captions. Or use the inspiration templates."),
                tr("知道了", "Got it")
            )
            return
        }
        scope.launch {
            aiBusy = true
            val prompt = tr(
                "写一段短视频/旅行足迹的文案，2-3句，生动有画面感，只输出文案正文，不要解释。",
                "Write a short, vivid 2-3 sentence caption for a travel footprint. Output only the caption."
            )
            val text = AiApi.requestOnce(ep, tr("你是旅行文案助手", "You are a travel caption writer"), prompt)
            aiBusy = false
            if (text.isNullOrBlank()) {
                state.toast(tr("AI 创作失败，请稍后重试", "AI creation failed, try again"))
            } else {
                onCaptionGenerated(text.trim().take(300), emptyList())
                state.toast(tr("AI 文案已填入发布页", "AI caption filled in"))
            }
        }
    }

    BackHandler { onClose() }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { offset ->
                            runCatching {
                                val point = previewView.meteringPointFactory
                                    .createPoint(offset.x, offset.y)
                                camera?.cameraControl?.startFocusAndMetering(
                                    FocusMeteringAction.Builder(point).build()
                                )
                            }
                        })
                    }
            )

            // 滤镜预览：用代表色半透明蒙层示意当前滤镜（成片才是真实矩阵处理）
            val filterTint = filterTintFor(filter)
            if (filterTint != null) {
                Box(Modifier.fillMaxSize().background(filterTint))
            }

            // 上下压暗渐变：白色控件压在任意取景画面上都要读得清
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Brush.verticalGradient(blackScrim(0.80f).reversed()))
                    .align(Alignment.TopCenter)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(Brush.verticalGradient(blackScrim(0.87f)))
                    .align(Alignment.BottomCenter)
            )

            // 网格线
            if (gridOn) {
                Column(modifier = Modifier.fillMaxSize()) {
                    repeat(2) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x55FFFFFF)))
                        Spacer(Modifier.weight(1f))
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x55FFFFFF)))
                    Spacer(Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxSize()) {
                    repeat(2) {
                        Box(Modifier.width(1.dp).fillMaxSize().background(Color(0x55FFFFFF)))
                        Spacer(Modifier.weight(1f))
                    }
                    Box(Modifier.width(1.dp).fillMaxSize().background(Color(0x55FFFFFF)))
                    Spacer(Modifier.weight(1f))
                }
            }

            // 提词器滚动叠加层
            if (teleprompterOn && teleprompterText.isNotBlank()) {
                Box(Modifier.fillMaxWidth().align(Alignment.Center)) {
                    TeleprompterOverlay(teleprompterText)
                }
            }

            // 顶栏：关闭 / 完成 / 音乐 / 翻转 / 闪光
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = tr("关闭", "Close"),
                    tint = Ink.OnCover,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onClose() }
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Filled.Check,
                    contentDescription = tr("完成", "Done"),
                    tint = Ink.OnCover,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onClose() }
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x66FFFFFF))
                            .clickable { onPickMusic() }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.MusicNote, null, tint = Ink.OnCover, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            musicLabel ?: tr("选择音乐", "Choose music"),
                            color = Ink.OnCover, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
                CaptureTopIcon(Icons.Filled.Refresh, tr("翻转", "Flip")) {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                        CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                }
                Spacer(Modifier.width(18.dp))
                CaptureTopIcon(
                    if (flashMode == ImageCapture.FLASH_MODE_OFF) Icons.Filled.FlashlightOff
                    else Icons.Filled.FlashlightOn,
                    tr("闪光", "Flash")
                ) { toggleFlash() }
            }

            // 右侧工具列：全部真实面板
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                CaptureTool(
                    Icons.Filled.Settings,
                    tr("设置", "Settings")
                ) { openPanel = CapturePanel.SETTINGS }
                CaptureTool(
                    Icons.Filled.AutoAwesome,
                    tr("灵感跟拍", "Inspiration")
                ) { openPanel = CapturePanel.INSPIRATION }
                CaptureTool(
                    Icons.Filled.Timer,
                    if (countdownSec == 0) tr("倒计时", "Timer") else "${countdownSec}s",
                    active = countdownSec > 0
                ) {
                    countdownSec = when (countdownSec) { 0 -> 3; 3 -> 10; else -> 0 }
                }
                CaptureTool(
                    Icons.Filled.FaceRetouchingNatural,
                    tr("美颜", "Beauty"),
                    active = !beauty.isNone
                ) { openPanel = CapturePanel.BEAUTY }
                CaptureTool(
                    Icons.Filled.TextFields,
                    tr("提词器", "Teleprompter"),
                    active = teleprompterOn
                ) { openPanel = CapturePanel.TELEPROMPTER }
                CaptureTool(
                    Icons.Filled.KeyboardArrowDown,
                    tr("更多", "More")
                ) { moreExpanded = !moreExpanded }
            }

            if (moreExpanded) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xE6000000))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    CaptureMoreItem(tr("特效滤镜", "Filters")) { moreExpanded = false; openPanel = CapturePanel.FILTER }
                    CaptureMoreItem(tr("文字水印", "Watermark")) { moreExpanded = false; openPanel = CapturePanel.WATERMARK }
                    CaptureMoreItem(tr("创作灵感", "Inspiration")) { moreExpanded = false; openPanel = CapturePanel.CREATIVE }
                    CaptureMoreItem(
                        if (gridOn) tr("关闭网格线", "Hide grid") else tr("打开网格线", "Show grid")
                    ) { gridOn = !gridOn }
                    CaptureMoreItem(tr("旧版拍照", "Old camera")) { onSwitchBack() }
                }
            }

            // 倒计时大字
            if (countdown > 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(countdown.toString(), color = Ink.OnCover, fontSize = 72.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 录像计时
            if (recording != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 64.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF453A)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "%02d:%02d".format(recordingSeconds / 60, recordingSeconds % 60),
                        color = Ink.OnCover, fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                }
            }

            // 权限被拒：只挡住取景，相册仍然可用
            if (!permissionGranted) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            tr("需要相机权限才能取景拍照", "Camera permission is required"),
                            color = Ink.OnCover, fontSize = 14.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            tr("点此授权", "Grant permission"),
                            color = Ink.OnCover, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Brand.Terracotta)
                                .clickable { permissionLauncher.launch(Manifest.permission.CAMERA) }
                                .padding(horizontal = 20.dp, vertical = 9.dp)
                        )
                    }
                }
            }

            // 底部：模式 / 快门 / 相册
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CaptureModeTab(tr("分段拍", "Segment"), mode == CAPTURE_SEGMENT) {
                        mode = CAPTURE_SEGMENT
                        guide = Triple(
                            tr("分段拍", "Segment shooting"),
                            tr("分段拍会逐张拍照并依次加入足迹，拍完点右上角完成即可。",
                                "Segment mode takes photos one by one and adds them in order. Tap Done when finished."),
                            tr("开始拍", "Start")
                        )
                    }
                    CaptureModeTab(tr("照片", "Photo"), mode == CAPTURE_PHOTO) { mode = CAPTURE_PHOTO }
                    CaptureModeTab(tr("视频", "Video"), mode == CAPTURE_VIDEO) {
                        mode = CAPTURE_VIDEO
                        if (recordAudio && !audioGranted) audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 34.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 特效：真实滤镜面板
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF7B5CFF), Color(0xFFFF6BA1))))
                                .border(
                                    if (!filter.isNone) 2.dp else 0.dp,
                                    if (!filter.isNone) Color.White else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Ink.OnCover, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (filter.isNone) tr("特效", "Effects") else filter.zh,
                            color = Ink.OnCoverSub, fontSize = 10.sp,
                            modifier = Modifier.clickable { openPanel = CapturePanel.FILTER }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(4.dp, Ink.OnCover, CircleShape)
                            .clickable { onShutter() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (recording != null) {
                            Icon(Icons.Filled.Stop, null, tint = Color(0xFFFF453A), modifier = Modifier.size(30.dp))
                        } else {
                            Box(
                                Modifier
                                    .size(if (mode == CAPTURE_VIDEO) 34.dp else 62.dp)
                                    .clip(if (mode == CAPTURE_VIDEO) RoundedCornerShape(8.dp) else CircleShape)
                                    .background(if (mode == CAPTURE_VIDEO) Color(0xFFFF453A) else Ink.OnCover)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xDDFFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.PhotoLibrary, null,
                                tint = FixedInk.OnChip, modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            tr("相册", "Album"), color = Ink.OnCoverSub, fontSize = 10.sp,
                            modifier = Modifier.clickable {
                                if (mode == CAPTURE_VIDEO) videoPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.VideoOnly
                                    )
                                ) else imagePicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CaptureBottomTab(tr("文字", "Text"), Icons.Filled.TextFields) { openPanel = CapturePanel.WATERMARK }
                    CaptureBottomTab(tr("AI 创作", "AI create"), Icons.Filled.AutoAwesome) { runAiCreate() }
                    Text(tr("相机", "Camera"), color = Ink.OnCover, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    CaptureBottomTab(tr("创作灵感", "Inspire"), Icons.Filled.AutoAwesome) { openPanel = CapturePanel.CREATIVE }
                    CaptureBottomTab(tr("开直播", "Live"), Icons.Filled.Videocam) {
                        guide = Triple(
                            tr("开直播", "Go live"),
                            tr("当前为本地录制模式：点「开始录制」后用视频记录这一段，录制完成会进入足迹发布流程（不含网络推流）。",
                                "Local recording mode: tap Start to record a video. The result enters the footprint publishing flow (no network streaming)."),
                            tr("开始录制", "Start recording")
                        )
                    }
                }
            }

            // 拍照瞬间白闪
            if (screenFlash) {
                Box(Modifier.fillMaxSize().background(Color(0xAAFFFFFF)))
            }
        }
    }

    // ---------- 功能面板 ----------
    when (openPanel) {
        CapturePanel.SETTINGS -> CaptureSettingsSheet(
            gridOn = gridOn, onGrid = { gridOn = it },
            mirrorOn = mirrorOn, onMirror = { mirrorOn = it },
            recordAudio = recordAudio, onAudio = { recordAudio = it; if (it && !audioGranted) audioLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            quality = quality, onQuality = { quality = it },
            onClose = { openPanel = null }
        )
        CapturePanel.BEAUTY -> BeautyPanel(beauty, { beauty = it }, { openPanel = null })
        CapturePanel.FILTER -> FilterPanel(filter.id, { filter = it }, { openPanel = null })
        CapturePanel.TELEPROMPTER -> TeleprompterPanel(
            teleprompterText, { teleprompterText = it },
            teleprompterOn, { teleprompterOn = it }, { openPanel = null }
        )
        CapturePanel.WATERMARK -> WatermarkPanel(watermark, { watermark = it }, { openPanel = null })
        CapturePanel.INSPIRATION -> InspirationPanel(tr("灵感跟拍", "Inspiration"), { insp ->
            onCaptionGenerated(insp.caption, insp.tags)
            openPanel = null
            state.toast(tr("文案与话题已填入发布页", "Caption and tags filled in"))
        }, { openPanel = null })
        CapturePanel.CREATIVE -> InspirationPanel(tr("创作灵感", "Creative inspiration"), { insp ->
            onCaptionGenerated(insp.caption, insp.tags)
            openPanel = null
            state.toast(tr("灵感已填入发布页", "Inspiration filled in"))
        }, { openPanel = null })
        CapturePanel.NONE, null -> Unit
    }

    guide?.let { (t, m, a) ->
        CaptureGuideSheet(t, m, a, {
            val wasLive = a == tr("开始录制", "Start recording")
            guide = null
            if (wasLive) {
                mode = CAPTURE_VIDEO
                if (recordAudio && !audioGranted) audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                state.toast(tr("已进入视频录制，点快门开始", "Video mode ready, tap shutter to record"))
            }
        }, { guide = null })
    }
}

/** 滤镜预览色：用极低透明度叠一层代表色，给用户即时反馈（不替代成片的真实矩阵处理） */
private fun filterTintFor(filter: CaptureEffects.Filter): Color? = when (filter.id) {
    CaptureEffects.FILTER_NONE -> null
    "warm", "film" -> Color(0x18FF9A5A)
    "cool", "dusk" -> Color(0x185A9AFF)
    "mono" -> Color(0x22000000)
    "vivid" -> Color(0x14FF5ABE)
    "clear" -> Color(0x145AD2E0)
    else -> null
}

/** 拍摄设置面板：网格 / 镜像 / 录制声音 / 分辨率 */
@Composable
private fun CaptureSettingsSheet(
    gridOn: Boolean, onGrid: (Boolean) -> Unit,
    mirrorOn: Boolean, onMirror: (Boolean) -> Unit,
    recordAudio: Boolean, onAudio: (Boolean) -> Unit,
    quality: Quality, onQuality: (Quality) -> Unit,
    onClose: () -> Unit
) {
    CaptureSheetShell(tr("拍摄设置", "Capture settings"), tr("仅对本次拍摄生效", "Applies to this shoot only"), onClose) {
        CaptureSettingSwitch(tr("构图网格线", "Composition grid"), gridOn, onGrid)
        CaptureSettingSwitch(tr("前置镜像", "Front mirror"), mirrorOn, onMirror)
        CaptureSettingSwitch(tr("录制声音", "Record audio"), recordAudio, onAudio)
        Spacer(Modifier.height(10.dp))
        Text(tr("录像清晰度", "Video quality"), color = Color(0xDDFFFFFF), fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val options = listOf(
                Quality.SD to tr("标清", "SD"),
                Quality.HD to tr("高清", "HD"),
                Quality.FHD to tr("超清", "FHD")
            )
            options.forEach { (q, label) ->
                val selected = q == quality
                Text(
                    label,
                    color = if (selected) Color.White else Color(0xAAFFFFFF),
                    fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Brand.Terracotta else Color(0xFF242329))
                        .clickable { onQuality(q) }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun CaptureSettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xDDFFFFFF), fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .background(if (checked) Brand.Terracotta else Color(0x44FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

private enum class CapturePanel { NONE, SETTINGS, INSPIRATION, BEAUTY, FILTER, TELEPROMPTER, WATERMARK, CREATIVE }

private const val CAPTURE_PHOTO = 0
private const val CAPTURE_VIDEO = 1
private const val CAPTURE_SEGMENT = 2

/** 顶栏的白描图标按钮 */
@Composable
private fun CaptureTopIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(icon, null, tint = Ink.OnCover, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = Ink.OnCoverSub, fontSize = 10.sp)
    }
}

/** 右侧竖排工具 */
@Composable
private fun CaptureTool(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (active) Color(0x55FFFFFF) else Color(0x33000000)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Ink.OnCover, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (active) Ink.OnCover else Ink.OnCoverSub, fontSize = 10.sp)
    }
}

/** 更多里的一行 */
@Composable
private fun CaptureMoreItem(label: String, onClick: () -> Unit) {
    Text(
        label, color = Ink.OnCover, fontSize = 13.sp,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 7.dp)
    )
}

/** 分段拍 / 照片 / 视频 */
@Composable
private fun CaptureModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) FixedInk.OnChip else Ink.OnCoverSub,
        fontSize = if (selected) 15.sp else 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) FixedInk.ChipSurface else Color(0x33000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    )
}

/** 底部导航（相机之外的入口都是真实功能入口） */
@Composable
private fun CaptureBottomTab(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Icon(icon, null, tint = Ink.OnCoverSub, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = Ink.OnCoverSub, fontSize = 11.sp)
    }
}
