package com.example.my.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.my.data.AiApi
import com.example.my.data.AiCreation
import com.example.my.data.AiCreationStore
import com.example.my.data.AiFeature
import com.example.my.state.AppState
import com.example.my.state.ExtraScreen
import com.example.my.ui.components.AppTopBar
import com.example.my.ui.components.BackActionRow
import com.example.my.ui.components.PrimaryButton
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.MainGradient
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class CreationMode(val zh: String, val en: String) {
    IMAGE("图片生成", "Image"),
    VIDEO("视频生成", "Video")
}

private val IMAGE_EXAMPLES = listOf(
    "一只橘猫坐在窗台上，午后阳光，写实摄影",
    "雨后的山间瀑布，雾气缭绕，电影感",
    "重庆夜景，霓虹灯与江面倒影，广角"
)

private val VIDEO_EXAMPLES = listOf(
    "海边日落，镜头缓慢推进，电影质感",
    "古镇清晨，炊烟与石板路，慢镜头",
    "城市夜景航拍，灯光流动，4 秒短片"
)

private val CREATION_TIME = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)

/**
 * AI 创作专区：把原来散落在对话里的图片/视频生成收拢为独立入口。
 *
 * 所有按钮都接真实动作：
 * - 生成：分别调用 IMAGE_GEN / VIDEO_GEN 当前分配的端点；
 * - 结果：下载到 App 私有目录，图片/视频原文件不二次压缩；
 * - 保存到相册：MediaStore 复制原文件；
 * - 历史：可单条删除、可一键清空，清空会同步删除本地文件。
 */
@Composable
fun AiCreationScreen(state: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember {
        mutableStateOf(if (state.creativeMode == 1) CreationMode.VIDEO else CreationMode.IMAGE)
    }
    var prompt by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var latest by remember { mutableStateOf<AiCreation?>(null) }
    var creations by remember { mutableStateOf(AiCreationStore.list(ctx)) }
    var preview by remember { mutableStateOf<AiCreation?>(null) }
    var showClear by remember { mutableStateOf(false) }

    val feature = if (mode == CreationMode.VIDEO) AiFeature.VIDEO_GEN else AiFeature.IMAGE_GEN
    val endpoint = state.aiFeatureEndpoint(feature)
    val modelName = if (mode == CreationMode.VIDEO) endpoint.video else endpoint.image
    val configured = endpoint.ready &&
        endpoint.supports(if (mode == CreationMode.VIDEO) com.example.my.data.AiCapability.VIDEO_GEN else com.example.my.data.AiCapability.IMAGE_GEN)

    fun refreshHistory() {
        creations = AiCreationStore.list(ctx)
    }

    fun switchMode(next: CreationMode) {
        if (loading) return
        mode = next
        state.creativeMode = next.ordinal
        error = null
        latest = null
    }

    fun generate() {
        val text = prompt.trim()
        if (text.isBlank()) {
            error = tr("先写一句你想生成的画面", "Describe what you want first")
            return
        }
        if (!configured) {
            error = tr("「${mode.zh}」模型还没配置完整，请到设置里补全 Key、模型名和能力", "The ${mode.en} model is incomplete — fill in its key and model in Settings")
            return
        }
        if (loading) return
        loading = true
        error = null
        scope.launch {
            try {
                var saved: AiCreation? = null
                if (mode == CreationMode.IMAGE) {
                    val outcome = AiApi.generateImageOutcome(endpoint, text)
                    val url = outcome.url
                    if (url == null) {
                        error = tr("图片生成失败：", "Image generation failed: ") + outcome.error
                    } else {
                        val path = withContext(Dispatchers.IO) { AiCreationStore.saveImage(ctx, url) }
                        if (path == null) {
                            error = tr("图片已生成，但保存到本地失败，请重试", "The image was generated but could not be saved locally")
                        } else {
                            saved = withContext(Dispatchers.IO) {
                                AiCreationStore.add(ctx, AiCreationStore.TYPE_IMAGE, text, path)
                            }
                        }
                    }
                } else {
                    val outcome = AiApi.generateVideoOutcome(endpoint, text)
                    val url = outcome.url
                    if (url == null) {
                        error = tr("视频生成失败：", "Video generation failed: ") + outcome.error
                    } else {
                        val path = withContext(Dispatchers.IO) { AiCreationStore.saveVideo(ctx, url) }
                        if (path == null) {
                            error = tr("视频已生成，但下载到本地失败，请重试", "The video was generated but could not be downloaded")
                        } else {
                            saved = withContext(Dispatchers.IO) {
                                AiCreationStore.add(ctx, AiCreationStore.TYPE_VIDEO, text, path)
                            }
                        }
                    }
                }
                saved?.let {
                    latest = it
                    refreshHistory()
                }
            } catch (e: Exception) {
                error = tr("生成失败：", "Generation failed: ") + (e.message ?: "未知错误")
            } finally {
                loading = false
            }
        }
    }

    fun openCreation(item: AiCreation) {
        if (item.isVideo) {
            val file = File(item.path)
            if (!file.exists()) {
                state.toast(tr("视频文件已不存在", "The video file is missing"))
                return
            }
            val uri = runCatching {
                FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            }.getOrNull()
            if (uri == null) {
                state.toast(tr("视频打开失败", "Could not open the video"))
                return
            }
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, AiCreationStore.mimeOf(item))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { ctx.startActivity(intent) }
                .onFailure { state.toast(tr("没有可播放视频的应用", "No video player found")) }
        } else {
            preview = item
        }
    }

    fun saveToGallery(item: AiCreation) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            val activity = ctx as? Activity
            if (activity != null) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    4102
                )
                state.toast(tr("请允许存储权限，再点一次保存", "Allow storage permission, then tap save again"))
            } else {
                state.toast(tr("没有存储权限，无法保存到相册", "No storage permission — cannot save"))
            }
            return
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) { AiCreationStore.saveToGallery(ctx, item) }
            state.toast(
                if (ok) tr("已保存到相册", "Saved to gallery")
                else tr("保存失败，请检查存储权限或空间", "Save failed — check storage permission or space")
            )
        }
    }

    Column(Modifier.fillMaxSize().background(Bg.Background)) {
        AppTopBar(
            title = tr("AI 创作", "AI Studio"),
            subtitle = tr("图片 · 视频生成专区", "Image & video studio")
        )
        BackActionRow(onBack = { state.closeScreen() })

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StudioHero(mode = mode)
            }
            item {
                ModeTabs(mode = mode, enabled = !loading, onSelect = { switchMode(it) })
            }
            item {
                PromptPanel(
                    mode = mode,
                    prompt = prompt,
                    loading = loading,
                    examples = if (mode == CreationMode.IMAGE) IMAGE_EXAMPLES else VIDEO_EXAMPLES,
                    onPromptChange = { prompt = it; error = null },
                    onPickExample = { prompt = it; error = null },
                    onGenerate = { generate() },
                    onOpenSettings = { state.openScreen(ExtraScreen.SETTINGS) }
                )
            }
            item {
                ModelStrip(
                    mode = mode,
                    endpointName = endpoint.name,
                    modelName = modelName,
                    configured = configured,
                    onOpenSettings = { state.openScreen(ExtraScreen.SETTINGS) }
                )
            }
            error?.let { message ->
                item { ErrorCard(message) }
            }
            if (loading) {
                item { GeneratingCard(mode) }
            }
            latest?.let { item ->
                item {
                    LatestCreationCard(
                        item = item,
                        onOpen = { openCreation(item) },
                        onSave = { saveToGallery(item) },
                        onDelete = {
                            scope.launch {
                                withContext(Dispatchers.IO) { AiCreationStore.remove(ctx, item) }
                                if (latest?.id == item.id) latest = null
                                refreshHistory()
                                state.toast(tr("已删除这条创作", "Creation deleted"))
                            }
                        }
                    )
                }
            }
            item {
                HistoryHeader(
                    count = creations.size,
                    onClear = { showClear = true }
                )
            }
            if (creations.isEmpty()) {
                item { EmptyHistoryCard() }
            } else {
                items(creations, key = { it.id }) { item ->
                    CreationRow(
                        item = item,
                        onOpen = { openCreation(item) },
                        onDelete = {
                            scope.launch {
                                withContext(Dispatchers.IO) { AiCreationStore.remove(ctx, item) }
                                if (latest?.id == item.id) latest = null
                                refreshHistory()
                                state.toast(tr("已删除这条创作", "Creation deleted"))
                            }
                        }
                    )
                }
            }
        }
    }

    preview?.let { item ->
        ImagePreviewDialog(item = item, onDismiss = { preview = null })
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text(tr("清空全部创作？", "Clear all creations?")) },
            text = {
                Text(
                    tr(
                        "会删除本机保存的 ${creations.size} 条图片/视频，且无法恢复。",
                        "This deletes ${creations.size} local image/video files and cannot be undone."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClear = false
                    scope.launch {
                        withContext(Dispatchers.IO) { AiCreationStore.clear(ctx) }
                        latest = null
                        refreshHistory()
                        state.toast(tr("已清空全部创作", "All creations cleared"))
                    }
                }) {
                    Text(tr("清空", "Clear"), color = Brand.Terracotta)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) {
                    Text(tr("取消", "Cancel"), color = Text.Secondary)
                }
            }
        )
    }
}

@Composable
private fun StudioHero(mode: CreationMode) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Brush.linearGradient(MainGradient))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tr("一句话，生成你的画面", "One sentence, one visual"),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                tr(
                    if (mode == CreationMode.IMAGE) "写实、插画、旅行海报都可以" else "风景短片、氛围镜头都可以",
                    if (mode == CreationMode.IMAGE) "Realistic, illustrated or travel poster" else "Scenery, mood shots and short clips"
                ),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ModeTabs(mode: CreationMode, enabled: Boolean, onSelect: (CreationMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Bg.SurfaceVariant)
            .padding(4.dp)
    ) {
        CreationMode.entries.forEach { item ->
            val selected = item == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Brand.Terracotta else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(item) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tr(item.zh, item.en),
                    color = if (selected) Color.White else Text.Secondary,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun PromptPanel(
    mode: CreationMode,
    prompt: String,
    loading: Boolean,
    examples: List<String>,
    onPromptChange: (String) -> Unit,
    onPickExample: (String) -> Unit,
    onGenerate: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .border(1.dp, Bg.Outline, RoundedCornerShape(Radius.lg))
            .cardShadow(RoundedCornerShape(Radius.lg))
            .padding(14.dp)
    ) {
        Text(
            tr("描述画面", "Prompt"),
            color = Text.Primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = { if (it.length <= 500) onPromptChange(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    if (mode == CreationMode.IMAGE) {
                        tr("例如：一只橘猫坐在窗台上，午后阳光，写实摄影", "e.g. An orange cat on a windowsill, afternoon light, photo")
                    } else {
                        tr("例如：海边日落，镜头缓慢推进，电影质感", "e.g. Sunset by the sea, slow push-in, cinematic")
                    },
                    color = Text.Muted,
                    fontSize = 13.sp
                )
            },
            minLines = 3,
            maxLines = 5,
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(examples) { example ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Bg.SurfaceVariant)
                        .clickable { onPickExample(example) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        example,
                        color = Text.Secondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = if (loading) {
                tr("生成中…", "Generating…")
            } else {
                tr(if (mode == CreationMode.IMAGE) "生成图片" else "生成视频", if (mode == CreationMode.IMAGE) "Generate image" else "Generate video")
            },
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr(
                "生成结果会自动保存到本机「我的创作」，不会因远程链接过期而丢失。",
                "Results are saved locally under My creations so expiring links won't lose them."
            ),
            color = Text.Muted,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun ModelStrip(
    mode: CreationMode,
    endpointName: String,
    modelName: String,
    configured: Boolean,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(if (configured) Bg.Surface else Brand.Terracotta.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (configured) Bg.Outline else Brand.Terracotta.copy(alpha = 0.28f),
                RoundedCornerShape(Radius.md)
            )
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = if (configured) Brand.Teal else Brand.Terracotta,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tr(if (mode == CreationMode.IMAGE) "当前图片模型" else "当前视频模型", if (mode == CreationMode.IMAGE) "Image model" else "Video model"),
                color = Text.Muted,
                fontSize = 10.sp
            )
            Text(
                if (configured) {
                    "${endpointName.ifBlank { tr("未命名模型", "Unnamed") }} · ${modelName.ifBlank { tr("未填写模型名", "No model name") }}"
                } else {
                    tr("模型未配置完整", "Model not configured")
                },
                color = Text.Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            tr(if (configured) "去调整" else "去设置", if (configured) "Adjust" else "Set up"),
            color = Brand.Terracotta,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Brand.Terracotta.copy(alpha = 0.08f))
            .border(1.dp, Brand.Terracotta.copy(alpha = 0.26f), RoundedCornerShape(Radius.md))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Brand.Terracotta.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text("!", color = Brand.Terracotta, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(9.dp))
        Text(message, color = Brand.TerracottaDark, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun GeneratingCard(mode: CreationMode) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Bg.Surface)
            .border(1.dp, Bg.Outline, RoundedCornerShape(Radius.md))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = Brand.Terracotta,
            strokeWidth = 2.dp
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                tr(if (mode == CreationMode.IMAGE) "正在生成图片…" else "正在生成视频…", if (mode == CreationMode.IMAGE) "Generating image…" else "Generating video…"),
                color = Text.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                tr("长视频可能需要几分钟，请保持网络连接", "Long videos may take a few minutes — keep the network on"),
                color = Text.Muted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun LatestCreationCard(
    item: AiCreation,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .border(1.dp, Bg.Outline, RoundedCornerShape(Radius.lg))
            .cardShadow(RoundedCornerShape(Radius.lg))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("本次生成", "Latest"),
                color = Text.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                tr(if (item.isVideo) "视频" else "图片", if (item.isVideo) "Video" else "Image"),
                color = Brand.Terracotta,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Brand.Terracotta.copy(alpha = 0.10f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        CreationPreview(
            item = item,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(14.dp)),
            onClick = onOpen,
            thumbnail = false
        )
        Spacer(Modifier.height(9.dp))
        Text(
            item.prompt.ifBlank { tr("未填写提示词", "No prompt") },
            color = Text.Secondary,
            fontSize = 12.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniAction(
                icon = Icons.Outlined.FileDownload,
                label = tr("保存到相册", "Save to gallery"),
                onClick = onSave
            )
            MiniAction(
                icon = Icons.Outlined.DeleteOutline,
                label = tr("删除", "Delete"),
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun MiniAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Bg.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Text.Secondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Text.Secondary, fontSize = 11.sp)
    }
}

@Composable
private fun HistoryHeader(count: Int, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            tr("我的创作", "My creations"),
            color = Text.Primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(6.dp))
        Text("$count", color = Text.Muted, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        if (count > 0) {
            Text(
                tr("清空", "Clear"),
                color = Text.Muted,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .border(1.dp, Bg.Outline, RoundedCornerShape(Radius.lg))
            .padding(vertical = 26.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = Text.Muted,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(tr("还没有作品", "No creations yet"), color = Text.Primary, fontSize = 13.sp)
        Spacer(Modifier.height(3.dp))
        Text(
            tr("在上面写一句描述，生成第一张图片或第一段视频", "Write a prompt above to create your first image or video"),
            color = Text.Muted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun CreationRow(
    item: AiCreation,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Bg.Surface)
            .border(1.dp, Bg.Outline, RoundedCornerShape(Radius.md))
            .clickable(onClick = onOpen)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CreationPreview(
            item = item,
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(11.dp)),
            onClick = null,
            thumbnail = true
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.prompt.ifBlank { tr("未填写提示词", "No prompt") },
                color = Text.Primary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${tr(if (item.isVideo) "视频" else "图片", if (item.isVideo) "Video" else "Image")} · ${CREATION_TIME.format(Date(item.createdAt))}",
                color = Text.Muted,
                fontSize = 10.sp
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = tr("删除", "Delete"),
                tint = Text.Muted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CreationPreview(
    item: AiCreation,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    thumbnail: Boolean
) {
    val clickableModifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    if (item.isVideo) {
        Box(
            modifier = clickableModifier.background(Bg.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (thumbnail) 30.dp else 52.dp)
                    .clip(CircleShape)
                    .background(Brand.Terracotta),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.PlayCircle,
                    contentDescription = tr("播放视频", "Play video"),
                    tint = Color.White,
                    modifier = Modifier.size(if (thumbnail) 18.dp else 30.dp)
                )
            }
            if (!thumbnail) {
                Text(
                    tr("点击用系统播放器打开", "Tap to open in your player"),
                    color = Text.Secondary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                )
            }
        }
    } else {
        val bmp = rememberCreationBitmap(item.path, if (thumbnail) 360 else 1600)
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = tr("AI 生成图片", "AI image"),
                modifier = clickableModifier.background(Bg.SurfaceVariant),
                contentScale = if (thumbnail) ContentScale.Crop else ContentScale.Fit
            )
        } else {
            Box(
                modifier = clickableModifier.background(Bg.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Text.Muted,
                    modifier = Modifier.size(if (thumbnail) 20.dp else 30.dp)
                )
            }
        }
    }
}

@Composable
private fun ImagePreviewDialog(item: AiCreation, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val bmp = rememberCreationBitmap(item.path, 2200)
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = tr("AI 生成图片", "AI image"),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 36.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 16.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = tr("关闭", "Close"),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                item.prompt,
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(18.dp)
            )
        }
    }
}

/** 按目标尺寸采样解码，避免历史记录一多就把整张大图都读进内存 */
@Composable
private fun rememberCreationBitmap(path: String, maxSide: Int): ImageBitmap? =
    remember(path, maxSide) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) {
                sample *= 2
            }
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?.asImageBitmap()
        }.getOrNull()
    }
