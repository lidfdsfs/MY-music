package com.example.my.ui.music

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.my.data.WebIncognito
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.my.data.WebSafety
import com.example.my.data.music.ChartInfo
import com.example.my.data.music.ChartSong
import com.example.my.data.music.QQFailReason
import com.example.my.data.music.QQMusic
import com.example.my.ui.i18n.Lang
import com.example.my.ui.i18n.tr
import com.example.my.ui.components.ProvideShimmer
import com.example.my.ui.components.SkeletonSongRow
import com.example.my.ui.theme.AppPalette

private val Bg get() = AppPalette.value.background
private val Card get() = AppPalette.value.surface
private val White get() = AppPalette.value.textPrimary
private val Muted get() = AppPalette.value.textMuted
private val Chevron get() = AppPalette.value.textSecondary
private val Accent get() = AppPalette.value.terracotta
private val Side = 20.dp

/** 桌面 Chrome UA：手机 UA 会被 QQ 音乐引导到 App 拉起/手机登录授权，容易直接失败 */
private const val UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/122.0.0.0 Safari/537.36"

/** 失败原因 → 用户能看懂的提示，中英双语 */
internal fun qqFailText(r: QQFailReason): String = when (r) {
    QQFailReason.NETWORK -> tr("网络不通，请检查联网开关后重试", "No connection. Check your network and retry")
    QQFailReason.PARSE -> tr("歌单地址没认出来，试试直接粘贴歌单文本", "Could not read that playlist. Try pasting the list instead")
    QQFailReason.NEED_LOGIN -> tr("这个歌单需要登录 QQ 音乐后才能读取", "Sign in to QQ Music to read this playlist")
    QQFailReason.EMPTY -> tr("这个榜单暂时没有内容", "This chart is empty right now")
}

/**
 * 排行榜 / QQ 音乐收藏，直接嵌进音乐页内容区（不弹窗），底部播放条始终常驻。
 *
 * 榜单用公开接口，无需登录；收藏走「内嵌浏览器登录 → 读出当前歌单 → 拉取歌曲」，
 * 同时提供粘贴歌单文本这条 100% 可用的兜底路径。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun QQMusicPage(
    tab: Int,
    onTab: (Int) -> Unit,
    charts: List<ChartInfo>,
    chartId: Int,
    songs: List<ChartSong>,
    loading: Boolean,
    error: QQFailReason?,
    title: String,
    busyKeyword: String?,
    batchRunning: Boolean,
    batchDone: Int,
    batchTotal: Int,
    onSelectChart: (Int) -> Unit,
    onLoadPlaylist: (String, String) -> Unit,
    onImportText: (String) -> Unit,
    onSearch: (String) -> Unit,
    onAutoFetch: (String) -> Unit,
    onBatchFetch: () -> Unit,
    onImportCurrent: () -> Unit,
    onDismiss: () -> Unit
) {
    var showLogin by remember { mutableStateOf(false) }
    var paste by remember { mutableStateOf("") }
    var pageUrl by remember { mutableStateOf("") }
    var loggedIn by remember { mutableStateOf(hasQqLogin(currentCookie())) }
    var webRef by remember { mutableStateOf<WebView?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current

    // 退出同步页时销毁 WebView：不留浏览历史/缓存；已登录则保留 Cookie 以便批量取歌
    DisposableEffect(Unit) {
        onDispose {
            val keepLogin = hasQqLogin(currentCookie())
            val wv = webRef
            webRef = null
            WebIncognito.releaseWebView(wv, clearCookies = !keepLogin)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = Side)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                tr("排行榜与收藏", "Charts & Favorites"),
                color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text(tr("关闭", "Close"), color = Accent, fontSize = 12.sp, maxLines = 1)
            }
        }

        // ── 两个 Tab ──
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Card)
                .padding(3.dp)
        ) {
            listOf(
                tr("排行榜", "Charts") to 0,
                tr("QQ 音乐收藏", "My QQ Music") to 1
            ).forEach { (label, idx) ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (tab == idx) Accent.copy(alpha = 0.18f) else Card)
                        .clickable { onTab(idx) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (tab == idx) Accent else Chevron,
                        fontSize = 13.sp,
                        fontWeight = if (tab == idx) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (tab == 0) {
            Spacer(Modifier.height(10.dp))
            // 榜单切换：横向可滚的一排小胶囊，8 个榜都能一键直达
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                charts.forEach { c ->
                    val on = c.topId == chartId
                    Text(
                        tr(c.nameZh, c.nameEn),
                        color = if (on) White else Chevron,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(end = 7.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) Accent else Card)
                            .clickable { onSelectChart(c.topId) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                charts.firstOrNull { it.topId == chartId }?.let { tr(it.descZh, it.descEn) }.orEmpty(),
                color = Muted, fontSize = 11.sp, maxLines = 1
            )
        } else {
            Spacer(Modifier.height(10.dp))
            Text(
                tr(
                    "登录 QQ 音乐 → 打开「我喜欢」歌单 → 点同步，就能把收藏列出来逐首获取。",
                    "Sign in to QQ Music, open your liked-songs playlist, then tap Sync."
                ),
                color = Chevron, fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (loggedIn) tr("已登录", "Signed in") else tr("未登录", "Not signed in"),
                    color = if (loggedIn) Accent else Muted,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (loggedIn) Accent.copy(alpha = 0.14f) else Card)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { showLogin = !showLogin }) {
                    Text(
                        if (showLogin) tr("收起登录页", "Hide sign-in") else tr("电脑版登录", "Desktop sign-in"),
                        color = Accent, fontSize = 12.sp, maxLines = 1
                    )
                }
                TextButton(onClick = {
                    val cookie = currentCookie()
                    if (hasQqLogin(cookie)) {
                        QQMusic.rememberCookie(cookie)
                        loggedIn = true
                    }
                    onLoadPlaylist(pageUrl, cookie)
                }) {
                    Text(tr("同步当前歌单", "Sync current playlist"), color = Accent, fontSize = 12.sp, maxLines = 1)
                }
            }
            if (showLogin) {
                Text(
                    tr(
                        "使用桌面 Chrome UA 打开 QQ 音乐网页，避免手机端唤起 App 导致登录请求失败。登录后打开「我喜欢」或任一歌单，再点同步。",
                        "Opened with desktop Chrome UA so QQ Music won't bounce to its mobile app. Sign in, open a playlist, then tap Sync."
                    ),
                    color = Muted, fontSize = 10.sp
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Card)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { c ->
                            WebView(c).apply {
                                webRef = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = UA
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                                settings.setSupportMultipleWindows(false)
                                // 与网页搜索共用同一套笼子：禁本地文件/内容访问、禁弹窗、
                                // 禁混合内容、禁定位；QQ 登录只允许 HTTPS 页面。
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.javaScriptCanOpenWindowsAutomatically = false
                                settings.setGeolocationEnabled(false)
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                settings.mediaPlaybackRequiresUserGesture = true
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    settings.safeBrowsingEnabled = true
                                }
                                val cm = CookieManager.getInstance()
                                cm.setAcceptCookie(true)
                                // QQ 登录跳转依赖第三方 Cookie，只在这个登录 WebView 内保留；
                                // 用户选择不保留登录态时，关闭页面会由 WebIncognito 清掉 Cookie。
                                cm.setAcceptThirdPartyCookies(this, true)
                                restoreQqCookies(cm)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest
                                    ): Boolean {
                                        val scheme = request.url.scheme?.lowercase()
                                        // file:// / content:// / intent:// / javascript: / http:// 一律拦下
                                        return scheme != "https"
                                    }

                                    override fun onReceivedSslError(
                                        view: WebView,
                                        handler: SslErrorHandler,
                                        error: SslError
                                    ) {
                                        // 证书不对直接断开，不给「继续访问」的入口
                                        handler.cancel()
                                    }

                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?
                                    ) {
                                        // 兜底：即使服务端 30x 跳到 http，也不让页面留下来
                                        if (!url.isNullOrBlank() && url != "about:blank" &&
                                            !url.startsWith("https://")
                                        ) {
                                            view?.stopLoading()
                                            view?.loadUrl("about:blank")
                                            return
                                        }
                                        super.onPageStarted(view, url, favicon)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        pageUrl = url.orEmpty()
                                        val cookie = currentCookie()
                                        if (hasQqLogin(cookie)) {
                                            QQMusic.rememberCookie(cookie)
                                            loggedIn = true
                                        }
                                    }

                                    override fun doUpdateVisitedHistory(
                                        view: WebView?, url: String?, isReload: Boolean
                                    ) {
                                        if (!url.isNullOrBlank()) pageUrl = url
                                    }
                                }
                                WebSafety.sanitize(QQMusic.LOGIN_URL)?.let { loadUrl(it) }
                            }
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("当前页面：", "Current page: ") +
                        (QQMusic.extractPlaylistId(pageUrl)?.let { tr("歌单 $it", "playlist $it") }
                            ?: tr("未识别到歌单", "no playlist detected")),
                    color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = {
                    QQMusic.forgetCookie()
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    loggedIn = false
                    showLogin = false
                }) {
                    Text(tr("清除登录态", "Sign out"), color = Chevron, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                tr("也可以直接粘贴 QQ 音乐分享的歌单文本（每行「歌名 - 歌手」）：", "Or paste a shared playlist (one \"Song - Artist\" per line):"),
                color = Chevron, fontSize = 11.sp
            )
            TextField(
                value = paste,
                onValueChange = { paste = it },
                minLines = 2,
                maxLines = 4,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = White),
                placeholder = { Text(tr("晴天 - 周杰伦\n稻香 - 周杰伦", "Song - Artist"), fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth()
            )
            Row {
                TextButton(onClick = {
                    if (paste.isNotBlank()) {
                        onImportText(paste)
                        keyboard?.hide()
                    }
                }) { Text(tr("导入文本", "Import text"), color = Accent, fontSize = 12.sp) }
                TextButton(onClick = {
                    if (paste.isNotBlank()) {
                        onLoadPlaylist(paste, currentCookie())
                        keyboard?.hide()
                    }
                }) { Text(tr("按链接/ID 读取", "Read by link / ID"), color = Accent, fontSize = 12.sp) }
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── 结果区 ──
        when {
            loading -> {
                Text(tr("正在读取…", "Loading…"), color = Chevron, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                ProvideShimmer {
                    Column {
                        repeat(3) { SkeletonSongRow() }
                    }
                }
            }
            error != null -> Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(qqFailText(error), color = Chevron, fontSize = 13.sp)
            }
            songs.isEmpty() -> Text(
                tr("选一个榜单，或粘贴歌单开始", "Pick a chart or paste a playlist to start"),
                color = Muted, fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 14.dp)
            )
            else -> {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title.ifBlank { tr("共 ${songs.size} 首", "${songs.size} songs") },
                        color = Chevron, fontSize = 11.sp, maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onBatchFetch,
                        enabled = !batchRunning && songs.isNotEmpty()
                    ) {
                        Text(
                            when {
                                batchRunning -> tr("批量获取 $batchDone/$batchTotal", "Getting $batchDone/$batchTotal")
                                batchTotal > 0 -> tr("再次批量获取", "Get all again")
                                else -> tr("全部获取", "Get all")
                            },
                            color = if (batchRunning) Muted else Accent,
                            fontSize = 11.sp, maxLines = 1
                        )
                    }
                    TextButton(onClick = onImportCurrent, enabled = songs.isNotEmpty() && !batchRunning) {
                        Text(
                            tr("导入到我的音乐", "Import to my music"),
                            color = if (songs.isEmpty() || batchRunning) Muted else Accent,
                            fontSize = 11.sp, maxLines = 1
                        )
                    }
                }
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(songs, key = { _, s -> "${s.rank}:${s.title}:${s.artist}" }) { _, s ->
                        ChartSongRow(
                            song = s,
                            busy = busyKeyword == s.keyword,
                            enabled = busyKeyword == null,
                            onSearch = { onSearch(s.keyword) },
                            onAutoFetch = { onAutoFetch(s.keyword) }
                        )
                    }
                }
            }
        }
    }
}

/** 读 WebView 的登录态；全局 Cookie 被别的页面清掉时回退到内存副本 */
private fun currentCookie(): String {
    val live = runCatching {
        val cm = CookieManager.getInstance()
        listOf("https://y.qq.com/", "https://.qq.com/", "https://c.y.qq.com/")
            .flatMap { cm.getCookie(it).orEmpty().split(";") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.substringBefore('=') }
            .joinToString("; ")
    }.getOrDefault("")
    return if (hasQqLogin(live)) live else QQMusic.sessionCookie.ifBlank { live }
}

private val QQ_LOGIN_KEYS = Regex(
    """(^|;\s*)(uin|p_skey|skey|qm_keyst|psrf_qqaccess_token)=""",
    RegexOption.IGNORE_CASE
)

private fun hasQqLogin(cookie: String): Boolean = QQ_LOGIN_KEYS.containsMatchIn(cookie)

/** 网页搜索页清过全局 Cookie 后，把只存在内存里的登录态写回 WebView */
private fun restoreQqCookies(cm: CookieManager) {
    val saved = QQMusic.sessionCookie
    if (saved.isBlank() || hasQqLogin(currentCookie())) return
    saved.split(";").map { it.trim() }.filter { it.isNotBlank() }.forEach { kv ->
        listOf("https://y.qq.com/", "https://.qq.com/").forEach { domain ->
            runCatching { cm.setCookie(domain, kv) }
        }
    }
    cm.flush()
}

@Composable
private fun ChartSongRow(
    song: ChartSong,
    busy: Boolean,
    enabled: Boolean,
    onSearch: () -> Unit,
    onAutoFetch: () -> Unit
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
        Text(
            "%02d".format(song.rank),
            color = if (song.rank <= 3) Accent else Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(30.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                song.title, color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist.ifBlank { tr("未知歌手", "Unknown artist") },
                color = Chevron, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
        } else {
            TextButton(onClick = onSearch, enabled = enabled) {
                Text(tr("搜索", "Search"), color = Chevron, fontSize = 11.sp, maxLines = 1)
            }
            TextButton(onClick = onAutoFetch, enabled = enabled) {
                Text(tr("获取", "Get"), color = Accent, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
