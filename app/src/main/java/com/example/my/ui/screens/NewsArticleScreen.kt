package com.example.my.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.AiApi
import com.example.my.data.AiFeature
import com.example.my.data.WebSafety
import com.example.my.data.ai.LocalLlm
import com.example.my.data.news.NewsArticle
import com.example.my.data.news.NewsArticleCache
import com.example.my.data.news.NewsArticleImageCache
import com.example.my.data.news.NewsArticleReader
import com.example.my.data.news.NewsBlockKind
import com.example.my.state.AppState
import com.example.my.ui.components.LiquidGlassStyles
import com.example.my.ui.components.liquidGlassBackground
import com.example.my.ui.components.aiModelLabel
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Ink
import com.example.my.ui.theme.Layer
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.TypeScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 阅读页品牌红：随深浅 / 自定义背景自动反转，不再写死一个在深色下看不清的暗红。 */
private val ReaderRed get() = Ink.ReaderAccent

/**
 * 本地资讯阅读页：正文和图片由 [NewsArticleReader] 抓成结构化块，用 Compose 重新排版；
 * 顶部保留原文入口，抓取失败时才明确退回浏览器，不再默认打开 WebView。
 */
@Composable
fun NewsArticleScreen(
    state: AppState,
    url: String,
    titleHint: String,
    onBack: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cacheKey = remember(url, titleHint) {
        if (url.isBlank()) "q:${titleHint.trim().take(80)}"
        else "url:${WebSafety.sanitize(url).orEmpty()}"
    }
    var article by remember(cacheKey) { mutableStateOf<NewsArticle?>(null) }
    var loading by remember(cacheKey) { mutableStateOf(true) }
    var error by remember(cacheKey) { mutableStateOf<String?>(null) }
    var reload by remember(cacheKey) { mutableStateOf(0) }
    var summary by remember(cacheKey) { mutableStateOf("") }
    var summaryLoading by remember(cacheKey) { mutableStateOf(false) }
    var summaryError by remember(cacheKey) { mutableStateOf<String?>(null) }
    var showModelPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = true) { onBack() }

    LaunchedEffect(cacheKey, reload) {
        loading = true
        error = null
        if (article == null) {
            article = withContext(Dispatchers.IO) { NewsArticleCache.load(ctx, cacheKey) }
        }
        val fresh = withContext(Dispatchers.IO) {
            val force = reload > 0
            if (url.isBlank()) {
                NewsArticleReader.readByQuery(ctx, titleHint, titleHint, force = force)
            } else {
                NewsArticleReader.read(ctx, url, titleHint, force = force)
            }
        } ?: if (url.isNotBlank() && titleHint.isNotBlank()) {
            // 原站拒绝抓取/正文结构异常时，再用标题检索兜底，保证点进来仍有内容可读
            withContext(Dispatchers.IO) {
                NewsArticleReader.readByQuery(ctx, titleHint, titleHint, force = false)
            }
        } else null
        if (fresh != null) {
            article = fresh
        } else if (article == null) {
            error = tr(
                "正文抓取失败，可能是原网页限制了访问，可重试或打开原文",
                "Could not fetch the article. The site may block access; retry or open the original."
            )
        }
        loading = false
    }

    fun runSummary() {
        val current = article ?: return
        if (summaryLoading) return
        val fullText = buildString {
            current.lead.takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
            current.blocks.forEach { b ->
                when (b.kind) {
                    NewsBlockKind.PARAGRAPH, NewsBlockKind.HEADING,
                    NewsBlockKind.QUOTE, NewsBlockKind.BULLET -> {
                        if (b.text.isNotBlank()) append(b.text).append('\n')
                    }
                    NewsBlockKind.IMAGE -> Unit
                }
            }
        }.trim()
        if (fullText.length < 30) {
            summaryError = tr("正文太少，暂时无法总结", "Not enough text to summarize")
            return
        }
        val feature = AiFeature.DISCOVER
        scope.launch {
            summaryLoading = true
            summaryError = null
            val system = tr(
                "你是新闻编辑。请只根据用户给出的文章全文做中文总结，不猜测、不补充文中没有的事实。先写 2-4 句摘要，再列 3-5 条要点；总字数控制在 220 字以内。直接输出正文，不要 JSON、不要 Markdown 标题。",
                "You are a news editor. Summarize the supplied article in Chinese only. Write 2-4 sentences, then 3-5 bullet points, under 220 Chinese characters. Do not add facts that are not in the article. Output plain text, no JSON or Markdown headings."
            )
            val user = "标题：${current.title}\n\n正文：\n${fullText.take(18_000)}"
            val local = if (state.aiFeatureUsesLocal(feature)) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val raw = LocalLlm.chatOnce(
                            ctx,
                            "$system\n\n$user",
                            state.aiFeatureLocalModelId(feature)
                        ).orEmpty()
                        raw.split('\u0001').let { if (it.size >= 3) it[2] else raw }
                    }.getOrNull()
                }
            } else null
            val answer = local?.takeIf { it.isNotBlank() }
                ?: runCatching {
                    withContext(Dispatchers.IO) {
                        AiApi.requestOnce(state.aiFeatureEndpoint(feature), system, user)
                    }
                }.getOrNull()
            summary = answer?.trim().orEmpty()
            if (summary.isBlank()) {
                summaryError = tr("AI 没有返回内容，请重试", "The AI returned nothing. Try again.")
            }
            summaryLoading = false
        }
    }

    // 文章页是叠在资讯首页 / 详情列表之上的覆盖层：必须用实心底。
    // 旧写法用 Bg.Background，自定义背景态下它是 Transparent，
    // 底层历史时间线的年份、标题、引用块会整排透上来和正文叠字。
    Box(Modifier.fillMaxSize().background(Layer.Opaque)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ReaderTopBar(
                title = article?.title ?: titleHint.ifBlank { tr("资讯阅读", "News reader") },
                loading = loading,
                hasArticle = article != null,
                canOpenOriginal = article?.url?.isNotBlank() == true || url.isNotBlank(),
                onBack = onBack,
                onRefresh = { reload++ },
                onOpenOriginal = {
                    val target = article?.url?.takeIf { it.isNotBlank() } ?: url
                    if (target.isNotBlank()) state.openUrl(target)
                }
            )
            when {
                article == null && loading -> LoadingReader()
                article == null && error != null -> ErrorReader(
                    message = error.orEmpty(),
                    onRetry = { reload++ },
                    onOpenOriginal = {
                        val target = url.ifBlank { article?.url.orEmpty() }
                        if (target.isNotBlank()) state.openUrl(target)
                    }
                )
                article != null -> ReaderBody(
                    article = article!!,
                    summary = summary,
                    summaryLoading = summaryLoading,
                    summaryError = summaryError,
                    modelLabel = aiModelLabel(state, AiFeature.DISCOVER),
                    onSummarize = ::runSummary,
                    onModelLongPress = { showModelPicker = true },
                    onOpenOriginal = {
                        val target = article?.url?.takeIf { it.isNotBlank() } ?: url
                        if (target.isNotBlank()) state.openUrl(target)
                    }
                )
            }
        }
        if (showModelPicker) {
            AiModelQuickSwitchDialog(
                feature = AiFeature.DISCOVER,
                state = state,
                onDismiss = { showModelPicker = false }
            )
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    loading: Boolean,
    hasArticle: Boolean,
    canOpenOriginal: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenOriginal: () -> Unit
) {
    Row(
        // 顶栏用近实心的液态玻璃底：网页里的文字不再透过标题栏与标题叠在一起
        Modifier.fillMaxWidth()
            .liquidGlassBackground(0.dp, LiquidGlassStyles.Bar)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReaderIconButton(Icons.AutoMirrored.Outlined.ArrowBack, tr("返回", "Back"), onBack)
        Text(
            title,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            color = Text.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(16.dp), color = ReaderRed, strokeWidth = 2.dp
            )
            Spacer(Modifier.width(6.dp))
        }
        if (hasArticle) {
            ReaderIconButton(Icons.Outlined.Refresh, tr("刷新", "Refresh"), onRefresh)
        }
        if (canOpenOriginal) {
            ReaderIconButton(Icons.AutoMirrored.Outlined.OpenInNew, tr("打开原文", "Open original"), onOpenOriginal)
        }
    }
}

@Composable
private fun ReaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = Text.Secondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun LoadingReader() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = ReaderRed, strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            tr("正在抓取正文与图片…", "Fetching the article and images..."),
            color = Text.Secondary, fontSize = 13.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            tr("会自动保留段落和图片顺序，失败时再退回原网页", "Paragraph and image order is preserved; the original page is the fallback"),
            color = Ink.OnGlassMuted, fontSize = TypeScale.caption,
            lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ErrorReader(message: String, onRetry: () -> Unit, onOpenOriginal: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            tr("这篇暂时读不到", "This article could not be read"),
            color = Text.Primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(message, color = Text.Secondary, fontSize = 12.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReaderPill(tr("重新抓取", "Retry"), true, onRetry)
            ReaderPill(tr("打开原文", "Open original"), false, onOpenOriginal)
        }
    }
}

@Composable
private fun ReaderPill(text: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Radius.pill))
            .background(if (primary) ReaderRed else Bg.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text,
            color = if (primary) Ink.OnCover else Ink.OnGlassSub,
            fontSize = TypeScale.body, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReaderBody(
    article: NewsArticle,
    summary: String,
    summaryLoading: Boolean,
    summaryError: String?,
    modelLabel: String,
    onSummarize: () -> Unit,
    onModelLongPress: () -> Unit,
    onOpenOriginal: () -> Unit
) {
    val blockImageUrls = remember(article) {
        // 按「去参数后的地址」比对：同一张图常带 ?w=600 这类尺寸参数，
        // 精确比字符串会导致封面和正文首图重复出现两遍
        article.blocks.filter { it.kind == NewsBlockKind.IMAGE }
            .map { imageKey(it.imageUrl) }
            .toSet()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(
                    article.title.ifBlank { tr("资讯详情", "News detail") },
                    color = Text.Primary, fontSize = 22.sp, lineHeight = 31.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    listOf(
                        article.site,
                        article.author,
                        article.publishedAt.take(19)
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Text.Muted, fontSize = 11.sp
                )
            }
        }
        if (article.fallback) {
            item {
                Text(
                    tr(
                        "原网页抓取受限，以下是检索摘要；可点右上角打开原文核对。",
                        "The original page blocked fetching. These are search excerpts; open the original to verify."
                    ),
                    color = ReaderRed, fontSize = 11.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                )
            }
        }
        if (article.coverImage.isNotBlank() && imageKey(article.coverImage) !in blockImageUrls) {
            item {
                ArticleImage(
                    article.coverImage,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                )
            }
        }
        item {
            NewsSummaryCard(
                summary = summary,
                loading = summaryLoading,
                error = summaryError,
                modelLabel = modelLabel,
                lead = article.lead,
                onSummarize = onSummarize,
                onModelLongPress = onModelLongPress
            )
        }
        if (article.lead.isNotBlank() &&
            article.blocks.firstOrNull { it.kind == NewsBlockKind.PARAGRAPH }?.text != article.lead
        ) {
            item {
                Text(
                    article.lead,
                    color = Text.Secondary, fontSize = 14.sp, lineHeight = 24.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
        items(article.blocks) { block ->
            when (block.kind) {
                NewsBlockKind.HEADING -> Text(
                    block.text,
                    color = Text.Primary, fontSize = 18.sp, lineHeight = 27.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                )
                NewsBlockKind.QUOTE -> Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Box(
                        Modifier.width(3.dp).height(44.dp)
                            .clip(RoundedCornerShape(2.dp)).background(ReaderRed.copy(alpha = 0.55f))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        block.text, color = Text.Secondary, fontSize = 14.sp, lineHeight = 23.sp
                    )
                }
                NewsBlockKind.BULLET -> Text(
                    block.text,
                    color = Text.Secondary, fontSize = 14.sp, lineHeight = 24.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                )
                NewsBlockKind.IMAGE -> ArticleImage(
                    block.imageUrl,
                    caption = block.caption,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
                NewsBlockKind.PARAGRAPH -> Text(
                    block.text,
                    color = Ink.OnGlass, fontSize = TypeScale.title, lineHeight = 28.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp)
                )
            }
        }
        if (article.url.isNotBlank()) {
            item {
                Spacer(Modifier.height(10.dp))
                Text(
                    tr("查看原文", "View original") + "  ↗",
                    color = ReaderRed, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 18.dp).clickable(onClick = onOpenOriginal)
                )
            }
        }
    }
}

@Composable
private fun NewsSummaryCard(
    summary: String,
    loading: Boolean,
    error: String?,
    modelLabel: String,
    lead: String,
    onSummarize: () -> Unit,
    onModelLongPress: () -> Unit
) {
    Column(
        Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            // 旧值 ReaderRed.copy(alpha=0.06f) 几乎全透，正文文字会与 AI 摘要叠成一层
            .liquidGlassBackground(Radius.lg, LiquidGlassStyles.Card)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("AI 一键总结", "AI summary"),
                color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                tr("模型：$modelLabel", "Model: $modelLabel"),
                color = Ink.OnGlassMuted, fontSize = TypeScale.tiny, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { onModelLongPress() })
                    }
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    Modifier.size(16.dp), color = ReaderRed, strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(tr("正在读完整篇…", "Reading the full article..."), color = Text.Secondary, fontSize = 12.sp)
            }
            summary.isNotBlank() -> Text(
                summary, color = Ink.OnGlass, fontSize = TypeScale.bodyLarge, lineHeight = 23.sp
            )
            else -> Text(
                lead.ifBlank {
                    tr(
                        "让 AI 读完这篇，提炼成几句摘要和要点；只依据正文，不补写事实。",
                        "Let AI read the article and produce a short summary and key points, using only the text."
                    )
                },
                color = Ink.OnGlassSub, fontSize = TypeScale.body, lineHeight = 20.sp,
                maxLines = 5, overflow = TextOverflow.Ellipsis
            )
        }
        if (error != null && !loading) {
            Spacer(Modifier.height(6.dp))
            Text(error, color = Ink.Danger, fontSize = TypeScale.caption)
        }
        Spacer(Modifier.height(10.dp))
        ReaderPill(
            if (summary.isNotBlank()) tr("重新总结", "Summarize again") else tr("AI 总结这篇", "Summarize"),
            true,
            onSummarize
        )
    }
}

@Composable
private fun ArticleImage(url: String, caption: String = "", modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val density = LocalDensity.current
    var failed by remember(url) { mutableStateOf(false) }
    val bmp by produceState<ImageBitmap?>(initialValue = null, url) {
        val file = NewsArticleImageCache.local(ctx, url)
        val decoded = withContext(Dispatchers.IO) { decode(file, 1400) }
        failed = decoded == null
        value = decoded?.asImageBitmap()
    }
    Column(modifier.fillMaxWidth()) {
        val image = bmp
        if (image != null) {
            val rawRatio = image.width.toFloat() / image.height.toFloat().coerceAtLeast(1f)
            // 竖长图与超宽图都夹进可读比例：长图不再一屏塞不下一张，宽条也不再被压成一条线
            val shownRatio = rawRatio.coerceIn(0.72f, 2.6f)
            val clamped = shownRatio != rawRatio
            // 小图不放大：原图本来就比屏幕窄时按原尺寸居中，避免糊成大色块
            val intrinsicWidth = with(density) { image.width.toDp() }
            val narrow = intrinsicWidth < 260.dp
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = image,
                    contentDescription = caption.ifBlank { null },
                    modifier = Modifier
                        .then(if (narrow) Modifier.width(intrinsicWidth) else Modifier.fillMaxWidth())
                        .clip(RoundedCornerShape(Radius.md))
                        .aspectRatio(shownRatio)
                        // 被夹住比例时用底色补边，看着像装了个画框，而不是裁掉内容
                        .then(if (clamped) Modifier.background(Bg.Surface) else Modifier),
                    contentScale = if (clamped) ContentScale.Fit else ContentScale.FillWidth
                )
            }
        } else {
            Box(
                Modifier.fillMaxWidth().height(if (failed) 64.dp else 170.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(Bg.Surface),
                contentAlignment = Alignment.Center
            ) {
                if (failed) {
                    Text(
                        tr("图片未能加载", "Image unavailable"),
                        color = Text.Muted, fontSize = 11.sp
                    )
                } else {
                    CircularProgressIndicator(
                        Modifier.size(18.dp), color = ReaderRed.copy(alpha = 0.6f), strokeWidth = 2.dp
                    )
                }
            }
        }
        if (caption.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                caption, color = Ink.OnGlassMuted, fontSize = TypeScale.caption, lineHeight = 16.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

/** 图片去重用的比对键：去掉查询参数与协议差异，只留「站内路径」 */
private fun imageKey(url: String): String =
    url.trim().substringBefore('?').substringBefore('#').trimEnd('/').lowercase()

private fun decode(file: File?, maxWidth: Int): android.graphics.Bitmap? {    if (file == null || !file.exists() || file.length() <= 0) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { BitmapFactory.decodeFile(file.absolutePath, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxWidth) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
    }
    return runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull()
}
