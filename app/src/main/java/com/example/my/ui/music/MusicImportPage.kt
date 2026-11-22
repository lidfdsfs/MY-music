package com.example.my.ui.music

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import coil.compose.AsyncImage
import com.example.my.data.music.ImportPipeline
import com.example.my.data.music.LocalSong
import com.example.my.data.music.MusicCollections
import com.example.my.data.music.MusicPlayer
import com.example.my.data.music.NeteaseImporter
import com.example.my.data.music.QQFetch
import com.example.my.data.music.QQMusic
import com.example.my.data.WebSafety
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.AppPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val Bg get() = AppPalette.value.background
private val Card get() = AppPalette.value.surface
private val White get() = AppPalette.value.textPrimary
private val Muted get() = AppPalette.value.textMuted
private val Chevron get() = AppPalette.value.textSecondary
private val Accent get() = AppPalette.value.terracotta
private val Side = 20.dp

/** 二维码登录用的桌面 UA（与 QQ 登录页一致，避免被引导到 App 唤起） */
private const val QR_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/122.0.0.0 Safari/537.36"

/**
 * 「导入我的音乐」统一页：网易云二维码登录一键导入 + QQ 音乐登录导入 + 已导入集合管理。
 *
 * 导入只写歌名占位（pendingSearch），音源在用户点击播放时再获取，避免一次性打满流量。
 */
@Composable
internal fun MusicImportPage(
    onDismiss: () -> Unit,
    onOpenQqLogin: () -> Unit,
    onPlaySongs: (List<LocalSong>, LocalSong) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var collections by remember { mutableStateOf<List<MusicCollections.SongCollection>>(emptyList()) }
    fun refreshCollections() { collections = MusicCollections.all(context) }

    // ── 网易云 ──
    var ncLoggedIn by remember { mutableStateOf(NeteaseImporter.isLoggedIn(context)) }
    var ncPlaylists by remember { mutableStateOf<List<NeteaseImporter.NcPlaylist>>(emptyList()) }
    var ncLoading by remember { mutableStateOf(false) }
    var ncError by remember { mutableStateOf<String?>(null) }
    var importingNc by remember { mutableStateOf<Long?>(null) }

    var showQr by remember { mutableStateOf(false) }
    var qrKey by remember { mutableStateOf<NeteaseImporter.QrKey?>(null) }
    var qrStatus by remember { mutableStateOf("") }
    var qrBusy by remember { mutableStateOf(false) }

    // ── QQ ──
    var qqLoggedIn by remember { mutableStateOf(QQMusic.sessionCookie.isNotBlank()) }
    var qqPlaylists by remember { mutableStateOf<List<QQMusic.QqSonglist>>(emptyList()) }
    var qqLoading by remember { mutableStateOf(false) }
    var importingQq by remember { mutableStateOf<String?>(null) }
    // QQ 接口不稳定时的兜底：粘贴歌单文本（每行「歌名 - 歌手」）直接导入
    var showPaste by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }

    fun loadNc() {
        ncLoading = true
        ncError = null
        scope.launch {
            val list = runCatching { NeteaseImporter.fetchUserPlaylists(context) }
                .getOrElse { ncError = it.message ?: tr("读取失败", "Load failed"); emptyList() }
            ncPlaylists = list
            ncLoading = false
        }
    }

    fun loadQq() {
        val cookie = QQMusic.sessionCookie
        qqLoggedIn = cookie.isNotBlank()
        if (cookie.isBlank()) { qqPlaylists = emptyList(); return }
        qqLoading = true
        scope.launch {
            qqPlaylists = runCatching { QQMusic.fetchMySonglists(cookie) }.getOrDefault(emptyList())
            qqLoading = false
        }
    }

    fun startQrLogin() {
        if (qrBusy) return
        qrBusy = true
        showQr = true
        qrStatus = tr("正在生成二维码…", "Preparing QR code…")
        scope.launch {
            val key = runCatching { NeteaseImporter.createQrKey() }.getOrNull()
            if (key == null) {
                qrStatus = tr("二维码获取失败，检查网络后重试", "Failed to get QR code, check network")
                qrBusy = false
                return@launch
            }
            qrKey = key
            qrStatus = tr("请用网易云 App 扫码登录", "Scan with Netease Cloud Music app")
            // 轮询：2.5s 一次，最多 150s
            val deadline = System.currentTimeMillis() + 150_000L
            while (isActive && System.currentTimeMillis() < deadline) {
                val st = runCatching { NeteaseImporter.pollQr(key.key) }.getOrNull()
                if (st == null) { delay(2500); continue }
                when (st.code) {
                    801 -> qrStatus = tr("等待扫码…", "Waiting for scan…")
                    802 -> qrStatus = tr("已扫描，请在手机上确认登录", "Scanned, confirm on your phone")
                    803 -> {
                        val cookie = st.cookie
                        if (cookie.isNullOrBlank()) {
                            qrStatus = tr("登录成功但未获取到凭证，重试", "Signed in but no credential, retry")
                        } else {
                            // 先落登录态；uid 取不到也不影响，刷新歌单时会再取一次
                            val uid = NeteaseImporter.fetchUid(cookie) ?: 0L
                            NeteaseImporter.saveSession(context, cookie, uid)
                            ncLoggedIn = true
                            showQr = false
                            qrKey = null
                            onToast(tr("网易云登录成功", "Netease signed in"))
                            loadNc()
                        }
                        qrBusy = false
                        return@launch
                    }
                    else -> {
                        qrStatus = tr("二维码已过期，点击重新生成", "QR expired, tap to regenerate")
                        qrBusy = false
                        return@launch
                    }
                }
                delay(2500)
            }
            qrStatus = tr("二维码已过期，点击重新生成", "QR expired, tap to regenerate")
            qrBusy = false
        }
    }

    fun importNc(pl: NeteaseImporter.NcPlaylist) {
        if (importingNc != null) return
        importingNc = pl.id
        scope.launch {
            val result = runCatching { NeteaseImporter.importPlaylist(context, pl) }
            importingNc = null
            result.fold(
                onSuccess = { report ->
                    refreshCollections()
                    onToast(
                        if (report.imported > 0)
                            tr("已导入 ${report.imported} 首到「${report.collectionName}」", "Imported ${report.imported} to \"${report.collectionName}\"")
                        else
                            tr("「${report.collectionName}」共 ${report.already} 首都已在收藏", "\"${report.collectionName}\": all ${report.already} already favorited")
                    )
                },
                onFailure = { e ->
                    com.example.my.data.CrashLog.record(context, e)
                    onToast(
                        tr(
                            "导入失败：${e.message ?: "网络异常"}",
                            "Import failed: ${e.message ?: "network error"}"
                        )
                    )
                }
            )
        }
    }

    fun importQq(sl: QQMusic.QqSonglist) {
        if (importingQq != null) return
        val cookie = QQMusic.sessionCookie
        if (cookie.isBlank()) { onToast(tr("请先登录 QQ 音乐", "Sign in to QQ Music first")); return }
        importingQq = sl.dissid
        scope.launch {
            val fetched = runCatching {
                when (val r = QQMusic.playlist(sl.dissid, cookie, 300)) {
                    is QQFetch.Ok -> r.songs
                    is QQFetch.Fail -> emptyList()
                }
            }.getOrDefault(emptyList())
            importingQq = null
            if (fetched.isEmpty()) {
                onToast(tr("读取歌单失败，试试在 QQ 音乐页打开后同步", "Failed to read playlist, try syncing from QQ page"))
                return@launch
            }
            val report = ImportPipeline.importTracks(
                context = context,
                source = "qq",
                sourceLabel = "QQ音乐",
                collectionId = "qq:${sl.dissid}",
                collectionName = sl.name,
                tracks = fetched.map { ImportPipeline.Track(it.title, it.artist, it.album, it.durationSec * 1000L) }
            )
            refreshCollections()
            onToast(
                if (report.imported > 0)
                    tr("已导入 ${report.imported} 首到「${report.collectionName}」", "Imported ${report.imported} to \"${report.collectionName}\"")
                else
                    tr("「${report.collectionName}」共 ${report.already} 首都已在收藏", "\"${report.collectionName}\": all ${report.already} already favorited")
            )
        }
    }

    /** 粘贴歌单文本兜底：不依赖任何登录态/接口，最稳的导入通道 */
    fun importQqText() {
        if (importingQq != null) return
        val songs = QQMusic.parsePlaylistText(pasteText)
        if (songs.isEmpty()) {
            onToast(tr("没识别到歌曲，每行写成「歌名 - 歌手」", "No songs recognized. Use one \"Title - Artist\" per line."))
            return
        }
        importingQq = "paste"
        scope.launch {
            val report = runCatching {
                ImportPipeline.importTracks(
                    context = context,
                    source = "qq",
                    sourceLabel = "QQ音乐",
                    collectionId = "qq:paste_${pasteText.hashCode()}",
                    collectionName = tr("QQ 粘贴歌单", "QQ pasted playlist"),
                    tracks = songs.map { ImportPipeline.Track(it.title, it.artist, it.album, it.durationSec * 1000L) }
                )
            }
            importingQq = null
            report.fold(
                onSuccess = { r ->
                    showPaste = false
                    pasteText = ""
                    refreshCollections()
                    onToast(tr("已导入 ${r.imported} 首", "Imported ${r.imported} songs"))
                },
                onFailure = { e ->
                    com.example.my.data.CrashLog.record(context, e)
                    onToast(tr("导入失败：${e.message ?: "未知错误"}", "Import failed: ${e.message ?: "unknown"}"))
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        refreshCollections()
        if (ncLoggedIn) loadNc()
        if (qqLoggedIn) loadQq()
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Side, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                tr("导入我的音乐", "Import my music"),
                color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Side, vertical = 4.dp)
        ) {
            // ── 已导入集合 ──
            item {
                SectionTitle(tr("已导入", "Imported"))
                if (collections.isEmpty()) {
                    Text(
                        tr("还没有导入任何歌单，登录下方平台一键导入", "No playlists imported yet. Sign in below to import."),
                        color = Muted, fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
            items(collections, key = { it.id }) { c ->
                val resolved = remember(c) { MusicCollections.resolveSongs(c) }
                CollectionRow(
                    name = c.name,
                    sourceLabel = c.sourceLabel,
                    count = c.size,
                    available = resolved.size,
                    onPlay = {
                        if (resolved.isEmpty()) onToast(tr("这些歌还没获取音源，点进收藏逐首播放", "No sources yet; play from favorites one by one"))
                        else { onPlaySongs(resolved, resolved.first()); onDismiss() }
                    },
                    onDelete = {
                        MusicCollections.delete(context, c.id)
                        refreshCollections()
                        onToast(tr("已移除集合", "Collection removed"))
                    }
                )
            }

            // ── 网易云 ──
            item {
                Spacer(Modifier.height(10.dp))
                SectionTitle(tr("网易云音乐", "Netease Cloud Music"))
            }
            item {
                PlatformCard(
                    accent = Color(0xFFD33A3A),
                    badge = tr("网易云", "Netease"),
                    loggedIn = ncLoggedIn,
                    statusText = if (ncLoggedIn) tr("已登录", "Signed in") else tr("未登录", "Not signed in"),
                    onPrimary = {
                        if (ncLoggedIn) { NeteaseImporter.logout(context); ncLoggedIn = false; ncPlaylists = emptyList(); onToast(tr("已退出登录", "Signed out")) }
                        else { if (!showQr) { qrKey = null; startQrLogin() } }
                    },
                    primaryLabel = if (ncLoggedIn) tr("退出登录", "Sign out") else tr("二维码登录", "Sign in with QR"),
                    onSecondary = { loadNc() },
                    secondaryLabel = tr("刷新歌单", "Refresh")
                )
            }
            if (showQr) {
                item {
                    QrCard(
                        qrKey = qrKey,
                        status = qrStatus,
                        busy = qrBusy,
                        onRegenerate = { qrKey = null; startQrLogin() },
                        onClose = { showQr = false; qrBusy = false; qrKey = null }
                    )
                }
            }
            if (ncLoggedIn) {
                when {
                    ncLoading -> item { LoadingRow() }
                    ncError != null -> item { Text(ncError ?: "", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)) }
                    ncPlaylists.isEmpty() -> item {
                        Text(tr("没有读取到歌单", "No playlists found"), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    else -> items(ncPlaylists, key = { it.id }) { pl ->
                        PlaylistRow(
                            name = pl.name,
                            count = pl.trackCount,
                            cover = pl.cover,
                            importing = importingNc == pl.id,
                            enabled = importingNc == null,
                            onImport = { importNc(pl) }
                        )
                    }
                }
            }

            // ── QQ 音乐 ──
            item {
                Spacer(Modifier.height(10.dp))
                SectionTitle(tr("QQ 音乐", "QQ Music"))
            }
            item {
                PlatformCard(
                    accent = Color(0xFF31C27C),
                    badge = tr("QQ音乐", "QQ Music"),
                    loggedIn = qqLoggedIn,
                    statusText = if (qqLoggedIn) tr("已登录", "Signed in") else tr("未登录", "Not signed in"),
                    onPrimary = {
                        if (qqLoggedIn) { QQMusic.forgetCookie(); qqLoggedIn = false; qqPlaylists = emptyList(); onToast(tr("已退出登录", "Signed out")) }
                        else onOpenQqLogin()
                    },
                    primaryLabel = if (qqLoggedIn) tr("退出登录", "Sign out") else tr("去登录", "Sign in"),
                    onSecondary = { loadQq() },
                    secondaryLabel = tr("刷新歌单", "Refresh"),
                    secondaryEnabled = qqLoggedIn
                )
            }
            if (qqLoggedIn) {
                if (qqLoading) item { LoadingRow() }
                else if (qqPlaylists.isEmpty()) item {
                    Text(
                        tr("接口未列出歌单（QQ 侧常变）。可在「排行榜与收藏」页打开「我喜欢」后点同步再导入，或直接用下面的「粘贴歌单导入」。",
                            "Could not list playlists. Sync from the Charts & Favorites page, or paste the playlist text below."),
                        color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else items(qqPlaylists, key = { it.dissid }) { sl ->
                    PlaylistRow(
                        name = sl.name,
                        count = sl.trackCount,
                        cover = "",
                        importing = importingQq == sl.dissid,
                        enabled = importingQq == null,
                        onImport = { importQq(sl) }
                    )
                }
            }

            // ── QQ 粘贴兜底（不需要登录）──
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showPaste = !showPaste }) {
                        Text(
                            if (showPaste) tr("收起粘贴框", "Hide paste box") else tr("粘贴歌单导入", "Import by pasting"),
                            color = Accent, fontSize = 12.sp
                        )
                    }
                }
            }
            if (showPaste) {
                item {
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Card)
                            .padding(12.dp)
                    ) {
                        Text(
                            tr("把 QQ / 网易云歌单文字粘进来，每行一首「歌名 - 歌手」，无需登录。",
                                "Paste playlist text, one \"Title - Artist\" per line. No sign-in needed."),
                            color = Muted, fontSize = 11.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pasteText,
                            onValueChange = { pasteText = it },
                            placeholder = { Text(tr("晴天 - 周杰伦", "Song - Artist"), fontSize = 12.sp, color = Chevron) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                            textStyle = TextStyle(fontSize = 13.sp, color = White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = Chevron,
                                cursorColor = Accent
                            )
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                onClick = { importQqText() },
                                enabled = importingQq == null && pasteText.isNotBlank()
                            ) {
                                if (importingQq == "paste") {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(tr("导入", "Import"), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    tr("导入只保存歌名与歌手，音源在你点击播放时按需获取，不会一次性批量下载。",
                        "Import saves only title/artist; audio is fetched on demand when you play."),
                    color = Chevron, fontSize = 11.sp
                )
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
    )
}

@Composable
private fun PlatformCard(
    accent: Color,
    badge: String,
    loggedIn: Boolean,
    statusText: String,
    onPrimary: () -> Unit,
    primaryLabel: String,
    onSecondary: () -> Unit,
    secondaryLabel: String,
    secondaryEnabled: Boolean = true
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.MusicNote, null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(badge, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    statusText,
                    color = if (loggedIn) accent else Muted,
                    fontSize = 11.sp
                )
            }
            TextButton(onClick = onPrimary) {
                Text(primaryLabel, color = accent, fontSize = 12.sp, maxLines = 1)
            }
        }
        if (loggedIn) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSecondary, enabled = secondaryEnabled) {
                    Text(secondaryLabel, color = Chevron, fontSize = 11.sp)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun QrCard(
    qrKey: NeteaseImporter.QrKey?,
    status: String,
    busy: Boolean,
    onRegenerate: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(14.dp)
    ) {
        // WebView 必须显式销毁，否则反复进出导入页会持续泄漏（严重时闪退）
        val webRef = remember { mutableStateOf<WebView?>(null) }
        DisposableEffect(Unit) {
            onDispose {
                webRef.value?.let { wv ->
                    runCatching {
                        wv.stopLoading()
                        wv.loadUrl("about:blank")
                        wv.removeAllViews()
                        wv.destroy()
                    }
                }
                webRef.value = null
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(White.copy(alpha = 0.06f))
        ) {
            if (qrKey != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        WebView(c).apply {
                            webRef.value = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = QR_UA
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.setGeolocationEnabled(false)
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.mediaPlaybackRequiresUserGesture = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView, request: WebResourceRequest
                                ): Boolean = request.url.scheme?.lowercase() != "https"
                            }
                            WebSafety.sanitize(qrKey.qrUrl)?.let { loadUrl(it) }
                        }
                    }
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Accent)
            Spacer(Modifier.width(if (busy) 6.dp else 0.dp))
            Text(status, color = Chevron, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2)
            TextButton(onClick = onRegenerate) { Text(tr("重新生成", "Regenerate"), color = Accent, fontSize = 11.sp) }
            TextButton(onClick = onClose) { Text(tr("取消", "Cancel"), color = Muted, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun CollectionRow(
    name: String,
    sourceLabel: String,
    count: Int,
    available: Int,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Card)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.QueueMusic, null, tint = Accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$sourceLabel · $count 首${if (available != count) " · 已取 $available" else ""}",
                color = Chevron, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onPlay) { Text(tr("播放", "Play"), color = Accent, fontSize = 12.sp) }
        TextButton(onClick = onDelete) { Text(tr("移除", "Remove"), color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun PlaylistRow(
    name: String,
    count: Int,
    cover: String,
    importing: Boolean,
    enabled: Boolean,
    onImport: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Card)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (cover.isNotBlank()) {
            AsyncImage(
                model = cover, contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.LibraryMusic, null, tint = Accent, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tr("$count 首", "$count songs"), color = Chevron, fontSize = 11.sp)
        }
        if (importing) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
            Spacer(Modifier.width(6.dp))
            Text(tr("导入中", "Importing"), color = Muted, fontSize = 11.sp)
        } else {
            TextButton(onClick = onImport, enabled = enabled) {
                Text(tr("一键导入", "Import"), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
        Spacer(Modifier.width(8.dp))
        Text(tr("读取中…", "Loading…"), color = Chevron, fontSize = 12.sp)
    }
}
