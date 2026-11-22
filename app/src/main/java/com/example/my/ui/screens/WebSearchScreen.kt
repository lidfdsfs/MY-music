package com.example.my.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import com.example.my.MainActivity
import com.example.my.data.ProbedResource
import com.example.my.data.WebIncognito
import com.example.my.data.WebPrivacy
import com.example.my.data.WebResourceProbe
import com.example.my.data.WebSafety
import com.example.my.data.music.MusicPlayer
import com.example.my.data.music.WebMusicCache
import com.example.my.ui.components.AppBanner
import com.example.my.ui.components.LiquidGlassStyles
import com.example.my.ui.components.liquidGlassBackground
import com.example.my.ui.components.liquidGlassScrim
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Ink
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.TypeScale

/** WebView 明文流量被拒错误码（低版本 compileSdk 未暴露该常量，直接用数值） */
private const val ERR_CLEARTEXT = -14

/**
 * 内置网页浏览器：
 * - directUrl 为空 → 打开 cn.bing.com 的关键词结果页（发现页「查看完整网页结果」）；
 * - directUrl 非空 → 直接打开引用来源原网页（AI 答案下方的来源条目）。
 *
 * 安全隔离（链接可能是外部/AI 给的，不能当可信输入）：
 * - 只加载 http/https；javascript:/data:/file:/intent:/market: 等伪协议一律拦下并提示；
 * - 带 user:pass@ 的地址、punycode 同形字、控制字符、超长地址一律拦；
 * - 关掉文件/资源访问、弹窗、地理位置、第三方 Cookie、混合内容；
 * - 明文 HTTP 页面禁用脚本，防止中途被注入；
 * - SSL 证书错误直接取消，绝不「继续访问」；
 * - 任何下载都不落盘，安装包类下载明确拦掉。
 *
 * 资源探（本轮新增）：像桌面浏览器那样，把网页里出现的音频 / 歌词地址捞出来，
 * 汇总到右下角的「资源探」里，可以直接在 App 内流式播放（不下载到手机）。
 */
/** 网页页里的提示也统一走全 App 顶部黑胶囊；下载回调可能在后台线程，统一回主线程再写状态 */
private fun webBanner(message: String) {
    Handler(Looper.getMainLooper()).post { AppBanner.show(message) }
}

@Composable
fun WebSearchScreen(query: String, onBack: () -> Unit, directUrl: String = "") {
    val rawTarget = if (directUrl.startsWith("http", ignoreCase = true)) {
        directUrl
    } else {
        "https://cn.bing.com/search?q=" + Uri.encode(query) + "&setlang=zh-CN"
    }
    // 进页面之前先过一遍闸门；不合法就只显示提示页，不创建 WebView
    val target = WebSafety.sanitize(rawTarget)
    var lastLoaded by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val ctx = LocalContext.current

    // 资源探的状态：捕获到的资源 + 面板开关
    val found = remember { mutableStateListOf<ProbedResource>() }
    var probeOpen by remember { mutableStateOf(false) }
    var pageUrl by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("") }
    // 页面是否还在：下载回主线程时可能用户早已手动退出，别再多退一次
    var screenAlive by remember { mutableStateOf(true) }
    // 网站风控/验证码：只提示用户手动验证，不做任何自动绕过
    var challengeHint by remember { mutableStateOf<String?>(null) }
    var lastHintAt by remember { mutableLongStateOf(0L) }
    // 主文档加载失败（网络中断 / 明文被拦 / 站点不可达）：本地兜底页接管，不再露系统错误页
    var loadError by remember { mutableStateOf<Int?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun showChallenge() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHintAt < 15_000L) return
        lastHintAt = now
        challengeHint = tr(
            "该网站要求人机验证。请在下方页面里手动完成（滑动 / 点选 / 输入验证码）；" +
                "页面空白或一直转圈时可稍后再试。",
            "This site asks for human verification. Please complete it manually in the page below."
        )
    }

    /** 捕获回调可能来自 WebView 的 IO 线程，统一切回主线程再进列表 */
    fun record(url: String?, mime: String? = null) {
        val u = url ?: return
        mainHandler.post { WebResourceProbe.add(found, u, mime) }
    }

    fun playResource(r: ProbedResource) {
        // 防盗链头：Referer 只给站点根，别把带搜索词的完整页面地址泄给第三方 CDN
        val parsed = Uri.parse(r.url)
        val pageOrigin = runCatching {
            val u = Uri.parse(pageUrl)
            "${u.scheme}://${u.host}/"
        }.getOrNull().orEmpty()
        val referer = pageOrigin.ifBlank { "${parsed.scheme}://${parsed.host}/" }
        val cookies = listOf(
            CookieManager.getInstance().getCookie(referer).orEmpty(),
            CookieManager.getInstance().getCookie(r.url).orEmpty()
        ).filter { it.isNotBlank() }.joinToString("; ")
        val ua = webView?.settings?.userAgentString.orEmpty()

        probeOpen = false
        webBanner(tr("开始下载音频…", "Downloading audio…"))
        // 下载到 files/web_music，成功后入队播放并关掉网页
        WebMusicCache.download(
            context = ctx,
            url = r.url,
            pageTitle = pageTitle.ifBlank { null },
            probeName = r.name,
            referer = referer,
            cookie = cookies,
            userAgent = ua
        ) { song, error ->
            if (song != null) {
                MusicPlayer.addCachedWebSong(song)
                MusicPlayer.play(song)
                webBanner(tr("已缓存并播放：${song.title}", "Cached and playing: ${song.title}"))
                if (screenAlive) onBack()
            } else {
                webBanner(tr("下载失败：${error ?: "未知原因"}", "Download failed: ${error ?: "unknown"}"))
            }
        }
    }

    fun copyResource(url: String) {
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("url", url))
        webBanner(tr("链接已复制", "Link copied"))
    }

    // 系统返回键：网页还有历史就先回上一页；历史见底才退出（MainActivity 里的返回键分支负责调用）
    DisposableEffect(Unit) {
        val handler: () -> Boolean = {
            val wv = webView
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
                true
            } else {
                false
            }
        }
        MainActivity.webBackHandler = handler
        onDispose {
            screenAlive = false
            if (MainActivity.webBackHandler === handler) MainActivity.webBackHandler = null
            // 关闭搜索页时必须真正释放网页进程：stopLoading + 清缓存 + terminate 渲染进程
            // + destroy，否则页面里的 JS 定时器 / 渲染线程会一直跑。
            val wv = webView
            webView = null
            WebIncognito.releaseWebView(wv, clearCookies = true)
        }
    }

    // 这个页面是盖在主界面之上的浮层（AnimatedVisibility 叠在发现页上），
    // 必须自己铺一层不透明底色，否则底下的「这次，想去哪儿？」会从状态栏和标题条透上来，
    // 和搜索关键词叠成一片（截图里那次就是）。
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Bg.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // WebView 顶栏原来完全透明，网页标题会与栏内标题叠字；改成近实心玻璃
                    .liquidGlassBackground(0.dp, LiquidGlassStyles.Bar)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr("返回", "Back"), tint = Text.Primary)
                }
                Icon(
                    if (directUrl.isNotBlank()) Icons.Outlined.Language else Icons.Outlined.Search,
                    contentDescription = null,
                    tint = Text.Primary,
                    modifier = Modifier.padding(start = 8.dp).size(17.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    query,
                    color = Text.Primary,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(color = Bg.Outline)
            // 检测到人机验证时，在页面上方给一条提示：只提醒，交由用户手动完成
            challengeHint?.let { hint ->
                Surface(
                    color = Bg.SurfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            hint,
                            modifier = Modifier.weight(1f),
                            fontSize = TypeScale.label,
                            lineHeight = 17.sp,
                            color = Ink.OnGlass,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = { challengeHint = null }) {
                            Text(tr("我知道了", "Got it"), fontSize = TypeScale.label, maxLines = 1)
                        }
                    }
                }
            }
            if (target == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        tr(
                            "这个链接不安全，已拦截。\n只允许打开正常的 http / https 网页。",
                            "This link was blocked for safety.\nOnly normal http / https pages are allowed."
                        ),
                        color = Text.Primary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { c ->
                            WebView(c).apply {
                                harden(this)
                                webViewClient = SafeWebClient(
                                    blocked = {
                                        webBanner(tr("已拦截不安全链接", "Unsafe link blocked"))
                                    },
                                    onResource = { record(it) },
                                    onPage = { pageUrl = it; loadError = null },
                                    onTitle = { pageTitle = it.orEmpty() },
                                    onChallenge = { showChallenge() },
                                    onPageError = { code, _ -> loadError = code }
                                )
                                webChromeClient = object : WebChromeClient() {
                                    // 不允许网页自己开新窗口（弹窗广告 / 钓鱼页的常见手法）
                                    override fun onCreateWindow(
                                        view: WebView,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: Message
                                    ): Boolean = false

                                    override fun onGeolocationPermissionsShowPrompt(
                                        origin: String?,
                                        callback: android.webkit.GeolocationPermissions.Callback
                                    ) {
                                        callback.invoke(origin, false, false)
                                    }
                                }
                                // 下载一律不落盘；音频/歌词先收进资源探，别的一概拒绝
                                setDownloadListener { url, _, _, mime, _ ->
                                    if (WebResourceProbe.kindOf(url, mime) != null) {
                                        record(url, mime)
                                        webBanner(tr("已加入「资源探」，可在下方直接播放", "Added to Probe — play it below"))
                                    } else if (WebSafety.isExecutableDownload(url, mime)) {
                                        webBanner(tr("已拦截可疑安装包下载", "Installer download blocked"))
                                    } else {
                                        webBanner(tr("内置浏览器不下载文件", "Downloads are disabled in this browser"))
                                    }
                                }
                                webView = this
                            }
                        },
                        update = { wv ->
                            if (lastLoaded != target) {
                                lastLoaded = target
                                // 每次换搜索目标先清一次痕迹，避免上一次搜索的 Cookie / 缓存串进来
                                WebIncognito.clearWebView(wv, clearCookies = true)
                                // 只有 HTTPS 才放行脚本；明文 HTTP 当静态页看，避免被中途注入
                                wv.settings.javaScriptEnabled = WebSafety.allowJavaScript(target)
                                // 带上 DNT，明确表示不希望被跨站跟踪
                                wv.loadUrl(target, mapOf("DNT" to "1"))
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 主文档打不开时本地兜底：不再露出系统错误页，给重试与关键词搜索两个出口
                    if (loadError != null) {
                        val isCleartext = loadError == ERR_CLEARTEXT
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Bg.Surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Language,
                                    contentDescription = null,
                                    tint = Brand.Terracotta,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    tr("页面暂时打不开", "Page unavailable"),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Text.Primary
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (isCleartext) tr(
                                        "该站点仍使用明文地址，已自动改走加密链接，仍未连通；可换关键词搜索。",
                                        "This site uses unencrypted traffic; the secure fallback still failed. Try searching instead."
                                    ) else tr(
                                        "可能是网络不佳、站点不可用或被风控拦截。可以重试，或用关键词在站内搜索。",
                                        "Network issues, the site is down, or it blocked the request. Retry or search by keyword."
                                    ),
                                    fontSize = TypeScale.label,
                                    lineHeight = 18.sp,
                                    textAlign = TextAlign.Center,
                                    color = Ink.OnGlassSub,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(18.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    TextButton(onClick = {
                                        loadError = null
                                        webView?.reload()
                                    }) {
                                        Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(tr("重试", "Retry"), fontSize = 13.sp)
                                    }
                                    TextButton(onClick = {
                                        loadError = null
                                        val bing = "https://cn.bing.com/search?q=" +
                                            Uri.encode(query) + "&setlang=zh-CN"
                                        webView?.loadUrl(bing, mapOf("DNT" to "1"))
                                    }) {
                                        Icon(Icons.Outlined.Search, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(tr("搜索关键词", "Search"), fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 右下角「资源探」入口：只有真捞到东西才出现，不打扰浏览
                    if (found.isNotEmpty() && !probeOpen) {
                        ProbeChip(
                            count = found.size,
                            onClick = { probeOpen = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(end = 16.dp, bottom = 16.dp)
                        )
                    }

                    if (probeOpen && found.isNotEmpty()) {
                        // 半透明遮罩，点空白关闭
                        Box(
                            Modifier
                                .fillMaxSize()
                                .liquidGlassScrim(0.52f)
                                .clickable { probeOpen = false }
                        )
                        ProbeSheet(
                            items = found.reversed(),
                            onPlay = { playResource(it) },
                            onCopy = { url -> copyResource(url) },
                            onClose = { probeOpen = false },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}

/** 右下角悬浮入口：Play 图标 + 「资源探 N」 */
@Composable
private fun ProbeChip(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Brand.Terracotta,
        shadowElevation = 8.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Ink.OnAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                tr("资源探 $count", "Probe $count"),
                color = Ink.OnAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** 资源探面板：底部弹出，列音频/歌词，点一下直接播 */
@Composable
private fun ProbeSheet(
    items: List<ProbedResource>,
    onPlay: (ProbedResource) -> Unit,
    onCopy: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = Bg.Surface,
        tonalElevation = 8.dp,
        shadowElevation = 16.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Bg.Outline.copy(alpha = 0.45f))
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("资源探", "Resource probe"),
                        fontSize = TypeScale.headline,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink.OnGlass,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        tr(
                            "网页里的音频与歌词，音频可下载后直接播放",
                            "Audio and lyrics found on this page"
                        ),
                        fontSize = TypeScale.caption,
                        lineHeight = 15.sp,
                        color = Ink.OnGlassSub,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, tr("关闭", "Close"), tint = Ink.OnGlassSub)
                }
            }
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 330.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                items.forEach { item ->
                    ProbeRow(item = item, onPlay = { onPlay(item) }, onCopy = { onCopy(item.url) })
                    HorizontalDivider(color = Bg.Outline.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tr(
                    "音频会下载到 App 私有目录（网页歌曲缓存），可在「设置 · 存储与缓存」里一键清除。",
                    "Audio is downloaded into the app's private web-song cache; clear it in Settings."
                ),
                fontSize = TypeScale.tiny,
                lineHeight = 14.sp,
                color = Ink.OnGlassSub,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProbeRow(item: ProbedResource, onPlay: () -> Unit, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brand.Terracotta.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (item.kind == ProbedResource.Kind.AUDIO) "MP3" else "LRC",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Brand.Terracotta
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Ink.OnGlass,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                WebSafety.hostOf(item.url),
                fontSize = 10.sp,
                color = Ink.OnGlassSub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.kind == ProbedResource.Kind.AUDIO) {
            TextButton(onClick = onPlay) { Text(tr("下载", "Get"), fontSize = 13.sp) }
        }
        TextButton(onClick = onCopy) { Text(tr("复制", "Copy"), fontSize = 13.sp) }
    }
}

/** 把 WebView 关进笼子：禁文件访问、禁弹窗、禁混合内容、禁第三方 Cookie、开安全浏览 */
@SuppressLint("SetJavaScriptEnabled")
private fun harden(wv: WebView) {
    val s = wv.settings
    s.javaScriptEnabled = true // 具体每个页面按 allowJavaScript 再定
    s.domStorageEnabled = true
    s.allowFileAccess = false
    s.allowContentAccess = false
    s.javaScriptCanOpenWindowsAutomatically = false
    s.setSupportMultipleWindows(false)
    s.setGeolocationEnabled(false)
    s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    s.mediaPlaybackRequiresUserGesture = true
    // 用通用 Chrome UA，不再给 App 打专属标记（自定义 UA 本身就是可追踪指纹）
    s.userAgentString = WebPrivacy.STANDARD_UA
    // 搜索页不落 HTTP 缓存，关闭后也不留页面快照
    s.cacheMode = WebSettings.LOAD_NO_CACHE
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        s.safeBrowsingEnabled = true
    }
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(wv, false)
    }
    WebView.setWebContentsDebuggingEnabled(false)
}

/** 导航闸门：伪协议/伪装地址拦在 WebView 之外，SSL 错误直接中断；顺带把音频链接喂给资源探 */
private class SafeWebClient(
    private val blocked: () -> Unit,
    private val onResource: (String?) -> Unit,
    private val onPage: (String) -> Unit,
    private val onTitle: (String?) -> Unit,
    private val onChallenge: () -> Unit,
    private val onPageError: (Int, String?) -> Unit
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val raw = request.url?.toString().orEmpty()
        val safe = WebSafety.sanitize(raw)
        if (safe == null) {
            blocked()
            return true
        }
        // 点到的如果是音频/歌词直链：不把二进制塞进网页，收进资源探
        if (WebResourceProbe.kindOf(safe) != null) {
            onResource(safe)
            return true
        }
        // 跳转后的页面也要重算脚本开关：https → 允许，http → 禁止
        view.settings.javaScriptEnabled = WebSafety.allowJavaScript(safe)
        if (safe != raw) {
            view.loadUrl(safe, mapOf("DNT" to "1"))
            return true
        }
        return false
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val u = request.url?.toString().orEmpty()
        // 第三方统计 / 广告 / 追踪域名直接掐掉，返回空响应
        if (WebPrivacy.isTracker(u)) {
            return WebResourceResponse(
                "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
            )
        }
        // 页面自己加载/播放音频时顺手记一笔；只是旁观，照常放行
        if (WebResourceProbe.kindOf(u) != null) onResource(u)
        return null
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (!url.isNullOrBlank()) onPage(url)
        view.settings.javaScriptEnabled = WebSafety.allowJavaScript(url)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        if (!url.isNullOrBlank()) onPage(url)
        onTitle(view.title)
        // 标题像人机验证页就直接提示用户手动处理（先判，不依赖脚本是否放行）
        if (WebPrivacy.looksLikeChallenge(url, view.title)) onChallenge()
        // 页面加载完后扫一遍链接，把 .mp3/.lrc 这类地址捞出来（只读脚本，不注入任何桥）
        if (!view.settings.javaScriptEnabled) return
        runCatching {
            view.evaluateJavascript(WebResourceProbe.scanScript()) { result ->
                WebResourceProbe.parseJsResult(result).forEach { onResource(it) }
            }
        }
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) onPageError(error.errorCode, error.description?.toString())
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        // 主文档 403 / 429 / 503 多半是风控拦了：提示用户手动验证，别反复重试
        if (request.isForMainFrame && WebPrivacy.isChallengeStatus(errorResponse.statusCode)) {
            onChallenge()
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        // 绝不弹「是否继续」：证书不对就直接断
        handler.cancel()
        blocked()
    }
}
