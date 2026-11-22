@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.my.ui.screens

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowManager
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.my.data.vault.PrivacyVault
import com.example.my.data.vault.VaultItem
import com.example.my.data.vault.VaultKind
import com.example.my.state.AppState
import com.example.my.ui.components.AppTopBar
import com.example.my.ui.components.BackActionRow
import com.example.my.ui.components.CloseButton
import com.example.my.ui.components.PrimaryButton
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 隐私空间。
 *
 * 交互三段式：首次进入设密码 → 密码解锁 → 加密媒体库（批量导入/批量移出）。
 * 密码连续错 3 次会要求手机锁屏验证，验证不通过就销毁整个空间（用户明确要求的行为）。
 * 页面存活期间窗口挂 FLAG_SECURE，截屏/录屏是黑的；离开页面自动清掉解密缓存。
 */
@Composable
fun PrivacyVaultScreen(state: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = remember(ctx) { ctx.findVaultActivity() }

    // 防截屏：只在隐私空间页生效，离开立刻撤销
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            PrivacyVault.lock()
            // 清缓存是磁盘 IO，别压在离开动画的主线程上
            Thread { PrivacyVault.clearPreview(ctx) }.start()
        }
    }

    var configured by remember { mutableStateOf(PrivacyVault.isConfigured(ctx)) }
    var unlocked by remember { mutableStateOf(PrivacyVault.hasSession()) }
    var items by remember { mutableStateOf<List<VaultItem>>(PrivacyVault.items()) }
    var fails by remember { mutableStateOf(PrivacyVault.failureCount(ctx)) }

    // 首次设置密码
    var pwd by remember { mutableStateOf("") }
    var pwd2 by remember { mutableStateOf("") }
    // 解锁
    var unlockPwd by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf<String?>(null) }

    var busy by remember { mutableStateOf(false) }
    var busyText by remember { mutableStateOf("") }
    var showVerify by remember { mutableStateOf(false) }
    var noLockScreen by remember { mutableStateOf(false) }

    // 媒体库
    var selectMode by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var viewing by remember { mutableStateOf<VaultItem?>(null) }
    var confirmHardDelete by remember { mutableStateOf(false) }

    fun refresh() {
        items = PrivacyVault.items()
        selection = selection.filter { id -> items.any { it.id == id } }.toSet()
        if (items.isEmpty()) selectMode = false
    }

    // 销毁：验证失败走这里，用户在确认框里主动点也走这里
    val wipeAll: () -> Unit = {
        scope.launch {
            busy = true
            busyText = tr("正在销毁…", "Destroying…")
            PrivacyVault.wipe(ctx)
            configured = false
            unlocked = false
            items = emptyList()
            selection = emptySet()
            selectMode = false
            fails = 0
            showVerify = false
            unlockPwd = ""
            pwd = ""
            pwd2 = ""
            busy = false
            hint = null
            state.toast(tr("隐私空间已销毁", "Private space destroyed"))
        }
    }

    val keyguard = remember(ctx) { ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager }

    // 设备凭证（PIN / 图案 / 密码）验证结果
    val verifyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        showVerify = false
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                PrivacyVault.resetFails(ctx)
                fails = 0
                hint = tr(
                    "设备验证通过，请输入隐私空间密码",
                    "Device verified — enter your private space password"
                )
            }
        } else {
            wipeAll()
        }
    }

    fun startVerify() {
        val km = keyguard
        val secure = km?.isDeviceSecure == true
        noLockScreen = !secure
        if (!secure || km == null) {
            // 没有锁屏密码就没法验证，按规则只能销毁；给一个「去设置」的出口
            showVerify = true
            return
        }
        val intent = km.createConfirmDeviceCredentialIntent(
            tr("验证设备锁屏密码", "Verify your screen lock"),
            tr("密码连续错误 3 次，验证失败将销毁隐私空间全部内容", "3 failed attempts — failure wipes the private space")
        )
        if (intent == null) {
            showVerify = true
        } else {
            runCatching { verifyLauncher.launch(intent) }
                .onFailure { showVerify = true }
        }
    }

    // 上次退出前就已经错满 3 次：本次进入直接进入验证流程
    LaunchedEffect(Unit) {
        if (configured && !unlocked && PrivacyVault.failureCount(ctx) >= PrivacyVault.MAX_FAILS) {
            startVerify()
        }
    }

    // 批量添加（系统照片选择器：不申请相册读权限也能选图/视频）
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            state.toast(tr("原图已从手机相册移除", "Originals removed from gallery"))
        } else {
            state.toast(
                tr(
                    "已加密存入隐私空间；相册原图未删除，可手动删除",
                    "Saved encrypted — originals are still in your gallery"
                )
            )
        }
    }

    // 导入：先加密落盘，再请求删掉相册原件
    val importUris: (List<Uri>) -> Unit = { uris ->
        scope.launch {
            busy = true
            busyText = tr("正在加密导入…", "Encrypting…")
            val n = PrivacyVault.import(ctx, uris)
            refresh()
            busy = false
            state.toast(
                if (n > 0) tr("已加密存入 $n 个文件", "Encrypted $n file(s)")
                else tr("导入失败，文件没有读取到", "Import failed")
            )
            if (n <= 0) return@launch
            // 只处理相册（MediaStore）里的 URI；文件管理器选来的文档删不了，直接跳过
            val galleryUris = uris.filter { it.scheme == "content" && it.authority == MediaStore.AUTHORITY }
            if (galleryUris.isEmpty()) {
                state.toast(tr("已加密存入；请到相册手动删除原图", "Saved — delete the originals manually"))
                return@launch
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // 照片选择器给的是 picker URI，Android 13 起先换成 MediaStore 直连 URI 再发起删除
                val targets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    galleryUris.map { u -> runCatching { MediaStore.getMediaUri(ctx, u) }.getOrNull() ?: u }
                } else {
                    galleryUris
                }
                runCatching {
                    deleteLauncher.launch(
                        IntentSenderRequest.Builder(
                            MediaStore.createDeleteRequest(ctx.contentResolver, targets).intentSender
                        ).build()
                    )
                }.onFailure {
                    state.toast(tr("已存入；请到相册手动删除原图", "Saved — delete originals manually"))
                }
            } else {
                val failed = PrivacyVault.deleteOriginals(ctx, galleryUris)
                if (failed > 0) {
                    state.toast(tr("已存入；有 $failed 个原图删不掉，请手动删除", "Saved — $failed originals need manual removal"))
                } else {
                    state.toast(tr("原图已从手机相册移除", "Originals removed from gallery"))
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        // 不传上限：默认取系统照片选择器自身上限，传死数字在部分机型会直接抛 IllegalArgumentException
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> if (uris.isNotEmpty()) importUris(uris) }

    // 移出到相册（Android 9 及以下写公共目录要运行时权限）
    var pendingExport by remember { mutableStateOf(false) }
    val writePerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingExport = true
        else scope.launch { state.toast(tr("没有存储权限，移出失败", "Storage permission denied")) }
    }

    LaunchedEffect(pendingExport) {
        if (!pendingExport) return@LaunchedEffect
        pendingExport = false
        val ids = selection
        if (ids.isEmpty()) return@LaunchedEffect
        busy = true
        busyText = tr("正在移出到相册…", "Moving to gallery…")
        val n = PrivacyVault.exportToGallery(ctx, ids)
        refresh()
        selectMode = false
        selection = emptySet()
        busy = false
        state.toast(
            if (n > 0) tr("已移出 $n 个文件到相册", "Moved $n file(s) to gallery")
            else tr("移出失败，文件仍在隐私空间", "Move failed — files kept")
        )
    }

    val startExport: () -> Unit = {
        if (selection.isEmpty()) {
            scope.launch { state.toast(tr("先选中要移出的文件", "Select files first")) }
        } else if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            writePerm.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            pendingExport = true
        }
    }

    BackHandler {
        when {
            viewing != null -> viewing = null
            confirmHardDelete -> confirmHardDelete = false
            selectMode -> {
                selectMode = false
                selection = emptySet()
            }
            else -> state.closeScreen()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg.Background)
    ) {
        AppTopBar(
            title = tr("隐私空间", "Private space"),
            subtitle = if (!configured) tr("首次使用 · 设置密码", "Set a password")
            else if (!unlocked) tr("已加密 · 需要密码", "Encrypted · locked")
            else tr("${items.size} 个文件", "${items.size} files")
        )
        BackActionRow(onBack = { state.closeScreen() })

        when {
            !configured -> SetupPane(
                pwd = pwd,
                pwd2 = pwd2,
                onPwd = { pwd = it.take(32) },
                onPwd2 = { pwd2 = it.take(32) },
                busy = busy,
                hint = hint
            ) {
                when {
                    pwd.length < 6 -> hint = tr("密码至少 6 位", "Use at least 6 characters")
                    pwd != pwd2 -> hint = tr("两次输入的密码不一致", "Passwords do not match")
                    else -> scope.launch {
                        busy = true
                        val ok = PrivacyVault.setup(ctx, pwd)
                        busy = false
                        if (ok) {
                            configured = true
                            unlocked = true
                            pwd = ""
                            pwd2 = ""
                            hint = null
                            state.toast(tr("隐私空间已创建", "Private space created"))
                        } else {
                            hint = tr("创建失败，请重试", "Could not create — try again")
                        }
                    }
                }
            }

            !unlocked -> UnlockPane(
                pwd = unlockPwd,
                onPwd = { unlockPwd = it.take(32) },
                fails = fails,
                busy = busy,
                hint = hint
            ) {
                if (unlockPwd.isBlank()) return@UnlockPane
                scope.launch {
                    busy = true
                    val ok = PrivacyVault.unlock(ctx, unlockPwd)
                    busy = false
                    if (ok) {
                        unlocked = true
                        fails = 0
                        unlockPwd = ""
                        hint = null
                        refresh()
                    } else {
                        val f = PrivacyVault.failureCount(ctx)
                        fails = f
                        unlockPwd = ""
                        if (f >= PrivacyVault.MAX_FAILS) {
                            hint = null
                            startVerify()
                        } else {
                            hint = tr(
                                "密码错误，还可尝试 ${PrivacyVault.MAX_FAILS - f} 次",
                                "Wrong password — ${PrivacyVault.MAX_FAILS - f} attempt(s) left"
                            )
                        }
                    }
                }
            }

            else -> VaultPane(
                items = items,
                selectMode = selectMode,
                selection = selection,
                onToggleSelect = { id ->
                    selection = if (id in selection) selection - id else selection + id
                },
                onEnterSelect = { id ->
                    selectMode = true
                    selection = if (id == null) emptySet() else setOf(id)
                },
                onCancelSelect = {
                    selectMode = false
                    selection = emptySet()
                },
                onSelectAll = { selection = items.map { it.id }.toSet() },
                onOpen = { viewing = it },
                onAdd = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                onExport = startExport,
                onHardDelete = { confirmHardDelete = true },
                onLock = {
                    PrivacyVault.lock()
                    PrivacyVault.clearPreview(ctx)
                    unlocked = false
                    items = emptyList()
                    selection = emptySet()
                    selectMode = false
                    hint = null
                }
            )
        }
    }

    // 校验 / 销毁确认
    if (showVerify) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(Bg.Surface)
                    .padding(20.dp)
            ) {
                Text(
                    tr("密码连续错误 3 次", "3 wrong attempts"),
                    color = Text.Primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (noLockScreen || keyguard?.isDeviceSecure != true) {
                        tr(
                            "本机没有设置锁屏密码，无法验证身份。按规则，验证失败会永久销毁隐私空间里的全部文件（不可恢复）。",
                            "No screen lock is set, so identity cannot be verified. Under the rule, a failed verification permanently destroys everything in the private space."
                        )
                    } else {
                        tr(
                            "需要用手机锁屏密码验证身份。验证不通过或取消，都会永久销毁隐私空间里的全部文件（不可恢复）。",
                            "Verify with your screen lock. If verification fails or is cancelled, everything in the private space is permanently destroyed."
                        )
                    },
                    color = Text.Secondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(18.dp))
                if (noLockScreen || keyguard?.isDeviceSecure != true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextButton(
                            text = tr("去设置锁屏", "Set screen lock"),
                            modifier = Modifier.weight(1f)
                        ) {
                            showVerify = false
                            runCatching {
                                activity?.startActivity(
                                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                                )
                            }
                        }
                        PrimaryButton(
                            text = tr("销毁", "Destroy"),
                            modifier = Modifier.weight(1f),
                            onClick = { wipeAll() }
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextButton(
                            text = tr("销毁", "Destroy"),
                            modifier = Modifier.weight(1f)
                        ) { wipeAll() }
                        PrimaryButton(
                            text = tr("验证身份", "Verify"),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showVerify = false
                                startVerify()
                            }
                        )
                    }
                }
            }
        }
    }

    if (confirmHardDelete) {
        Dialog(onDismissRequest = { confirmHardDelete = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(Bg.Surface)
                    .padding(20.dp)
            ) {
                Text(
                    tr("彻底删除选中的 ${selection.size} 个文件？", "Delete ${selection.size} selected file(s)?"),
                    color = Text.Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("不会导出到相册，删除后无法恢复。", "They will not be exported to the gallery. This cannot be undone."),
                    color = Text.Secondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextButton(
                        text = tr("取消", "Cancel"),
                        modifier = Modifier.weight(1f)
                    ) { confirmHardDelete = false }
                    PrimaryButton(
                        text = tr("删除", "Delete"),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            confirmHardDelete = false
                            val ids = selection
                            scope.launch {
                                busy = true
                                busyText = tr("正在删除…", "Deleting…")
                                val n = PrivacyVault.deleteItems(ctx, ids)
                                refresh()
                                selectMode = false
                                selection = emptySet()
                                busy = false
                                state.toast(tr("已删除 $n 个文件", "Deleted $n file(s)"))
                            }
                        }
                    )
                }
            }
        }
    }

    viewing?.let { item ->
        ViewerDialog(ctx = ctx, item = item, onClose = { viewing = null })
    }

    if (busy) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(Radius.md))
                    .background(Bg.Surface)
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = Brand.Terracotta,
                    strokeWidth = 2.5.dp
                )
                Spacer(Modifier.height(10.dp))
                Text(busyText, color = Text.Secondary, fontSize = 13.sp)
            }
        }
    }
}

// ============================ 首次设置 ============================

@Composable
private fun SetupPane(
    pwd: String,
    pwd2: String,
    onPwd: (String) -> Unit,
    onPwd2: (String) -> Unit,
    busy: Boolean,
    hint: String?,
    onConfirm: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Brand.Terracotta.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Lock, null, tint = Brand.Terracotta, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    tr("设置隐私空间密码", "Set a password"),
                    color = Text.Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    tr("照片视频加密后才能存进来", "Files are encrypted before storage"),
                    color = Text.Muted,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        VaultPasswordField(
            value = pwd,
            onValueChange = onPwd,
            hint = tr("设置密码（至少 6 位）", "Password (6+ characters)"),
            imeAction = ImeAction.Next
        )
        Spacer(Modifier.height(10.dp))
        VaultPasswordField(
            value = pwd2,
            onValueChange = onPwd2,
            hint = tr("再输一次确认", "Confirm password"),
            imeAction = ImeAction.Done,
            onDone = onConfirm
        )

        hint?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Brand.Terracotta, fontSize = 12.sp)
        }

        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = tr("创建隐私空间", "Create private space"),
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = onConfirm
        )

        Spacer(Modifier.height(18.dp))
        WarnCard(
            tr(
                "请务必记住密码：没有找回功能。连续输错 3 次会要求验证手机锁屏密码；验证不通过或取消，空间内全部文件会被永久销毁。",
                "Remember this password — there is no recovery. After 3 wrong attempts you must verify your screen lock; failing or cancelling that permanently destroys every file in the space."
            )
        )
    }
}

// ============================ 解锁 ============================

@Composable
private fun UnlockPane(
    pwd: String,
    onPwd: (String) -> Unit,
    fails: Int,
    busy: Boolean,
    hint: String?,
    onUnlock: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(Brand.Terracotta.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Lock, null, tint = Brand.Terracotta, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            tr("输入密码解锁", "Enter your password"),
            color = Text.Primary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            tr("内容已加密存储", "Content is encrypted"),
            color = Text.Muted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(22.dp))
        VaultPasswordField(
            value = pwd,
            onValueChange = onPwd,
            hint = tr("隐私空间密码", "Password"),
            imeAction = ImeAction.Done,
            onDone = onUnlock
        )

        if (hint != null) {
            Spacer(Modifier.height(10.dp))
            Text(hint, color = Brand.Terracotta, fontSize = 12.sp, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = tr("解锁", "Unlock"),
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = onUnlock
        )

        Spacer(Modifier.height(16.dp))
        Text(
            if (fails <= 0) {
                tr(
                    "连续输错 ${PrivacyVault.MAX_FAILS} 次需验证手机锁屏密码，验证失败会销毁全部内容。",
                    "${PrivacyVault.MAX_FAILS} wrong attempts require screen-lock verification; failure destroys everything."
                )
            } else {
                tr(
                    "已连续输错 $fails 次；满 ${PrivacyVault.MAX_FAILS} 次需验证手机锁屏密码，验证失败会销毁全部内容。",
                    "$fails wrong attempt(s). At ${PrivacyVault.MAX_FAILS} you must verify your screen lock; failure destroys everything."
                )
            },
            color = Text.Muted,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ============================ 媒体库 ============================

@Composable
private fun VaultPane(
    items: List<VaultItem>,
    selectMode: Boolean,
    selection: Set<String>,
    onToggleSelect: (String) -> Unit,
    onEnterSelect: (String?) -> Unit,
    onCancelSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onOpen: (VaultItem) -> Unit,
    onAdd: () -> Unit,
    onExport: () -> Unit,
    onHardDelete: () -> Unit,
    onLock: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionChip(
                icon = Icons.Outlined.PhotoLibrary,
                text = tr("添加", "Add"),
                onClick = onAdd
            )
            ActionChip(
                icon = if (selectMode) Icons.Outlined.RadioButtonUnchecked else Icons.Outlined.CheckCircle,
                text = if (selectMode) tr("取消选择", "Cancel") else tr("批量选择", "Select"),
                onClick = { if (selectMode) onCancelSelect() else onEnterSelect(null) }
            )
            Spacer(Modifier.weight(1f))
            ActionChip(
                icon = Icons.Outlined.Lock,
                text = tr("锁定", "Lock"),
                onClick = onLock
            )
        }

        if (items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        tr("还没有文件", "Empty"),
                        color = Text.Secondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr(
                            "点「添加」从相册选照片或视频，加密后原件会从相册移除",
                            "Tap Add to pick photos or videos — originals are removed from the gallery"
                        ),
                        color = Text.Muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    VaultCell(
                        item = item,
                        selected = item.id in selection,
                        selectMode = selectMode,
                        onClick = {
                            if (selectMode) onToggleSelect(item.id) else onOpen(item)
                        },
                        onLongClick = {
                            if (!selectMode) onEnterSelect(item.id) else onToggleSelect(item.id)
                        }
                    )
                }
            }
        }

        if (selectMode && items.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Bg.Surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextButton(text = tr("全选", "All"), modifier = Modifier.weight(1f)) { onSelectAll() }
                OutlinedTextButton(text = tr("删除", "Delete"), modifier = Modifier.weight(1f)) {
                    if (selection.isNotEmpty()) onHardDelete()
                }
                PrimaryButton(
                    text = tr("移出到相册 (${selection.size})", "Move (${selection.size})"),
                    modifier = Modifier.weight(1.6f),
                    enabled = selection.isNotEmpty(),
                    onClick = onExport
                )
            }
        }
    }
}

@Composable
private fun VaultCell(
    item: VaultItem,
    selected: Boolean,
    selectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val ctx = LocalContext.current
    val thumb by produceState<File?>(null, item.id) {
        value = withContext(Dispatchers.IO) { PrivacyVault.thumbFile(ctx, item) }
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Radius.sm))
            .background(Bg.SurfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        when {
            item.kind == VaultKind.VIDEO -> Box(
                Modifier.fillMaxSize().background(Color(0xFF2A2521)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.PlayArrow, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(30.dp))
            }

            thumb != null -> AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PhotoLibrary, null, tint = Text.Muted, modifier = Modifier.size(24.dp))
            }
        }

        if (selectMode) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (selected) Brand.Terracotta else Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Icon(Icons.Outlined.CheckCircle, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(2.dp, Brand.Terracotta, RoundedCornerShape(Radius.sm))
                )
            }
        }
    }
}

// ============================ 查看 ============================

@Composable
private fun ViewerDialog(ctx: Context, item: VaultItem, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (item.kind == VaultKind.IMAGE) {
                val bmp by produceState<Bitmap?>(null, item.id) {
                    value = withContext(Dispatchers.IO) { PrivacyVault.fullImage(ctx, item) }
                }
                bmp?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                val file by produceState<File?>(null, item.id) {
                    value = withContext(Dispatchers.IO) { PrivacyVault.previewFile(ctx, item) }
                }
                file?.let { f ->
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { c ->
                            VideoView(c).apply {
                                setVideoPath(f.absolutePath)
                                setOnPreparedListener { it.start() }
                                start()
                            }
                        }
                    )
                }
            }

            CloseButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
                onDark = true
            )
            Text(
                text = item.name,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 26.dp)
            )
        }
    }
}

// ============================ 小组件 ============================

@Composable
private fun VaultPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    imeAction: ImeAction,
    onDone: () -> Unit = {}
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Text.Primary, fontSize = 15.sp),
        cursorBrush = SolidColor(Brand.Terracotta),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(Bg.SurfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 15.dp)
            ) {
                if (value.isEmpty()) {
                    Text(hint, color = Text.Muted, fontSize = 14.sp)
                }
                inner()
            }
        }
    )
}

@Composable
private fun OutlinedTextButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .border(1.dp, Bg.Outline, RoundedCornerShape(50.dp))
            .combinedClickable(onClick = onClick)
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Text.Primary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .cardShadow()
            .clip(RoundedCornerShape(50.dp))
            .background(Bg.Surface)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = Brand.Terracotta, modifier = Modifier.size(16.dp))
        Text(text, color = Text.Primary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WarnCard(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Brand.Terracotta.copy(alpha = 0.10f))
            .padding(14.dp)
    ) {
        Text(text, color = Text.Secondary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

private fun Context.findVaultActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
