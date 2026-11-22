package com.example.my.data.music

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.example.my.data.AiApi
import com.example.my.data.AiPresets
import com.example.my.data.ai.LocalLlm
import com.example.my.data.ai.LocalLlmState
import com.example.my.util.PhotoStore
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 其他音乐 App 的歌单/收藏截图 → 本机「我的收藏」。
 *
 * 链路：MLKit 中文 OCR（离线模型已打进 APK）→ 按文字框几何位置还原每一行
 * 「歌名 + 歌手」→ 本地小模型批量校对方向（歌名/歌手是否颠倒）→
 * 只把歌名/歌手保存成待获取记录，**不在这里联网搜源或下载**。
 *
 * 音源在用户点击播放时才由 [resolveSong] 按需搜索并下载，成功后替换占位记录。
 * 这样导入几十首不会一次性打满网络和存储，也避免导入时误下错歌。
 */
object PlaylistShotImporter {

    data class Item(val title: String, val artist: String = "")

    data class Report(
        val recognized: List<Item>,
        val imported: Int,
        val failed: Int,
        val note: String,
        /** 本地小模型判断并交换了多少组歌名/歌手 */
        val corrected: Int = 0
    )

    /** 自带矩形，避免几何解析依赖 android.graphics.Rect（方便 JVM 单测） */
    data class Line(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val centerY: Int get() = (top + bottom) / 2
        val height: Int get() = bottom - top
    }

    private val TIME_ONLY = Regex("""^[\[(]?(\d{1,2}:)?\d{1,2}:\d{2}[\])]?$""")
    private val NUMBER_ONLY = Regex("""^\d{1,4}(\.\d+)?(万|w|W)?$""")
    private val LEADING_INDEX = Regex("""^\s*(?:\d{1,3}|[一二三四五六七八九十]{1,3})\s*[.、:：)）]\s*""")
    private val TRAILING_BADGE = Regex(
        """\s*(?:VIP|Svip|SVIP|SQ|HQ|无损|高清|独家|付费|已下载|下载|MV|现场版)$""",
        RegexOption.IGNORE_CASE
    )
    private val SEPARATOR = Regex("""\s+(?:-|–|—|—|—|—|—|—|—|—|—|—|—|—|—|—|—|—|—|—|—|—|—|—|—|—|—|—|\||｜)\s+""")

    /** 本地小模型一次校对多少组歌名/歌手；太大容易让 4K 上下文读不全 */
    private const val AI_BATCH = 8
    private val SWAP_LINE = Regex(
        """^\s*(\d{1,3})\s*(?:[|,，:：]|\s)\s*(?:SWAP|交换)""",
        RegexOption.IGNORE_CASE
    )

    private val NOISE_EXACT = setOf(
        "播放全部", "全部播放", "收藏", "已收藏", "我的收藏", "我的歌单", "歌单", "最近播放",
        "推荐", "猜你喜欢", "创建歌单", "新建歌单", "歌曲", "歌手", "专辑", "时长",
        "更多", "分享", "排序", "下载", "已下载", "关注", "评论", "简介",
        "下一首", "上一首", "播放", "暂停", "无损", "SQ", "HQ", "VIP", "独家", "付费"
    )

    suspend fun fromUri(
        context: Context,
        uri: Uri,
        onProgress: (String) -> Unit = {},
        ocrEndpoint: AiPresets.OcrEndpoint = AiPresets.OcrEndpoint()
    ): Report = withContext(Dispatchers.IO) {
        val bmp = runCatching { PhotoStore.decodeUri(context, uri, 2000) }.getOrNull()
            ?: return@withContext Report(emptyList(), 0, 0, "图片读取失败，换一张截图试试")
        try {
            val lines = recognize(bmp, ocrEndpoint)
            if (lines.isEmpty()) {
                return@withContext Report(emptyList(), 0, 0, "没识别到文字，截图可能太糊或只截到了封面")
            }
            val items = parseLines(lines)
            if (items.isEmpty()) {
                return@withContext Report(emptyList(), 0, 0, "识别到文字，但没解析出歌名；换一张带歌名的列表截图")
            }
            val verified = verifyOrder(context, items, onProgress)
            saveItems(context, verified.items, verified.corrected, verified.aiUsed)
        } finally {
            runCatching { bmp.recycle() }
        }
    }

    private suspend fun recognize(
        bitmap: Bitmap,
        endpoint: AiPresets.OcrEndpoint
    ): List<Line> {
        // 优先用用户在「模型管理 - OCR」接入的识别端点；未接入或识别失败再回退端侧 MLKit
        if (endpoint.ready) {
            remoteLines(bitmap, endpoint)?.let { if (it.isNotEmpty()) return it }
        }
        val client = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        val text = try {
            Tasks.await(client.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS)
        } finally {
            runCatching { client.close() }
        }
        return text.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            val box = l.boundingBox ?: return@mapNotNull null
            val t = l.text.replace('\u3000', ' ').trim()
            if (t.isBlank() || box.height() < 8) null
            else Line(t, box.left, box.top, box.right, box.bottom)
        }
    }

    /** 云端 OCR 只回传文本、不带坐标：按文本行构造等距行框，供后续行聚类解析 */
    private suspend fun remoteLines(bitmap: Bitmap, endpoint: AiPresets.OcrEndpoint): List<Line>? {
        val bytes = java.io.ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val prompt = "识别截图中的歌曲列表，逐行原样输出「歌名」和「歌手」，保持原有换行；" +
            "不要序号、不要时长、不要专辑徽标、不要解释或翻译。"
        val text = AiApi.ocrRemote(endpoint, base64, "image/jpeg", prompt, 2048) ?: return null
        val rowH = 28
        return text.split('\n').mapIndexedNotNull { index, raw ->
            val t = raw.replace('\u3000', ' ').trim()
            if (t.isBlank()) return@mapIndexedNotNull null
            val top = index * rowH
            Line(t, 0, top, 1000, top + rowH - 6)
        }.takeIf { it.isNotEmpty() }
    }

    /** 纯几何解析：输入 OCR 文本框，输出歌名/歌手。抽出来便于单测，不依赖图片 / MLKit */
    internal fun parseLines(rawLines: List<Line>): List<Item> {
        val all = rawLines.map { it.copy(text = normalize(it.text)) }
            .filter { it.text.isNotBlank() && !isNoise(it.text) }
            .sortedWith(compareBy({ it.top }, { it.left }))
        if (all.isEmpty()) return emptyList()

        // 同一行/同一张卡片里的文字框按纵向间距聚类：歌名和歌手常是上下两行，
        // 两首歌之间通常有明显的空隙。
        val rows = mutableListOf<MutableList<Line>>()
        for (line in all) {
            val last = rows.lastOrNull()
            if (last == null) {
                rows.add(mutableListOf(line))
                continue
            }
            val lastBottom = last.maxOf { it.bottom }
            val avgH = last.sumOf { it.height.coerceAtLeast(8) } / last.size
            if (line.top <= lastBottom + (avgH * 0.45f).toInt()) {
                last.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }

        val out = mutableListOf<Item>()
        val seen = mutableSetOf<String>()
        for (row in rows) {
            val lines = row.sortedBy { it.left }.filterNot { isNoise(it.text) }
            if (lines.isEmpty()) continue
            val item = itemOf(lines) ?: continue
            val key = normalize(item.title) + "|" + normalize(item.artist)
            if (key == "|" || !seen.add(key)) continue
            out.add(item)
            if (out.size >= 60) break
        }
        return out
    }

    private fun itemOf(lines: List<Line>): Item? {
        if (lines.isEmpty()) return null
        // 单行常见格式：「歌名 - 歌手」「歌名 | 歌手」
        if (lines.size == 1) {
            val text = cleanup(lines[0].text)
            if (text.isBlank()) return null
            val parts = SEPARATOR.split(text).filter { it.isNotBlank() }
            if (parts.size >= 2) {
                return Item(cleanup(parts[0]), cleanup(parts[1]))
            }
            return Item(text, "")
        }

        // 多行：最左且靠上的作为歌名，其余第一行非时间/非徽标的作为歌手
        val title = cleanup(lines.first().text)
        if (title.isBlank()) return null
        val artist = lines.drop(1)
            .map { cleanup(it.text) }
            .firstOrNull { candidate ->
                candidate.isNotBlank() &&
                    !TIME_ONLY.matches(candidate) &&
                    !NUMBER_ONLY.matches(candidate) &&
                    candidate != title
            }.orEmpty()
        return Item(title, artist)
    }

    private data class VerifyResult(
        val items: List<Item>,
        val corrected: Int,
        val aiUsed: Boolean
    )

    /**
     * 用本地小模型批量判断「歌名/歌手是否颠倒」。
     *
     * 不逐首调用：每首一次会让 30 首歌变成 30 次模型加载/生成。按 [AI_BATCH] 首
     * 一批，模型只输出「序号|OK」或「序号|SWAP」，解析失败就保持原样，不瞎改。
     */
    private suspend fun verifyOrder(
        context: Context,
        items: List<Item>,
        onProgress: (String) -> Unit
    ): VerifyResult {
        if (items.isEmpty()) return VerifyResult(items, 0, false)
        // 先按明显形状兜底：像「G.E.M. 邓紫棋 / 多远都要在一起」这种艺人名在上、
        // 歌名在下的组合，小模型即使不认识艺人也能先由规则换回来。
        val shapeSwapped = items.mapIndexedNotNull { index, item ->
            index.takeIf { looksSwappedByShape(item) }
        }.toSet()
        val downloaded = LocalLlm.CATALOG.filter { LocalLlm.modelDownloaded(context, it.id) }
        if (downloaded.isEmpty() || LocalLlm.state(context) != LocalLlmState.READY) {
            return VerifyResult(applySwaps(items, shapeSwapped), shapeSwapped.size, false)
        }
        // 优先用用户当前启用的模型；没有激活模型时再从已下载模型里选最小的，保证能跑起来。
        val model = LocalLlm.activeSpec(context)
            .takeIf { spec -> downloaded.any { it.id == spec.id } }
            ?: downloaded.minByOrNull { it.approxMb }
            ?: return VerifyResult(applySwaps(items, shapeSwapped), shapeSwapped.size, false)
        val pairs = items.mapIndexedNotNull { index, item ->
            item.takeIf { it.artist.isNotBlank() }?.let { index to it }
        }
        if (pairs.isEmpty()) return VerifyResult(applySwaps(items, shapeSwapped), shapeSwapped.size, true)

        val swapped = shapeSwapped.toMutableSet()
        var aiAnswered = false
        pairs.chunked(AI_BATCH).forEachIndexed { batchIndex, batch ->
            onProgress("本地 AI 正在校对歌名/歌手顺序 ${batchIndex + 1}/${(pairs.size + AI_BATCH - 1) / AI_BATCH}…")
            val prompt = buildString {
                appendLine("你是中文歌曲信息校对器。下面每行的格式是「序号|A|B」，A 和 B 一个是歌名、一个是歌手，OCR 可能把顺序弄反了。")
                appendLine("判断规则：")
                appendLine("1. 歌手名通常是艺人名/人名/乐队名，常见英文艺名、缩写、点号、& 符号，或者只有 2-5 个字。")
                appendLine("2. 歌名通常是完整短语、句子或情绪表达，往往更长，不会只是一个艺人名。")
                appendLine("3. 如果 A 更像歌手、B 更像歌名，就认为这一行颠倒了，输出「序号|SWAP」；否则输出「序号|OK」。")
                appendLine("4. 即使只有一行也要判断。不要因为 A 排在前面就默认它是歌名。")
                appendLine("示例：")
                appendLine("1|G.E.M. 邓紫棋|多远都要在一起 → 1|SWAP")
                appendLine("2|多远都要在一起|G.E.M. 邓紫棋 → 2|OK")
                appendLine("3|周杰伦|晴天 → 3|SWAP")
                appendLine("4|晴天|周杰伦 → 4|OK")
                appendLine("只输出每行的判断结果，格式严格为「序号|SWAP」或「序号|OK」，不要解释，不要 markdown。")
                batch.forEachIndexed { localIndex, pair ->
                    val item = pair.second
                    appendLine("${localIndex + 1}|${item.title.replace('|', '/')}|${item.artist.replace('|', '/')}")
                }
            }
            val raw = runCatching { LocalLlm.chatOnce(context, prompt, model.id) }.getOrNull()
                ?: return@forEachIndexed
            val answer = raw.split('\u0001').lastOrNull { it.isNotBlank() } ?: raw
            parseSwapReply(answer).forEach { localIndex ->
                if (localIndex in 1..batch.size) swapped.add(batch[localIndex - 1].first)
            }
            if (answer.isNotBlank()) aiAnswered = true
        }

        if (swapped.isEmpty()) return VerifyResult(items, 0, aiAnswered)
        return VerifyResult(applySwaps(items, swapped), swapped.size, aiAnswered)
    }

    private fun applySwaps(items: List<Item>, indexes: Set<Int>): List<Item> =
        items.mapIndexed { index, item ->
            if (index in indexes && item.artist.isNotBlank()) {
                item.copy(title = item.artist, artist = item.title)
            } else {
                item
            }
        }

    private val LATIN = Regex("""[A-Za-z]""")
    private val HAN = Regex("""[\u4e00-\u9fa5]""")
    private val HAN_PHRASE = Regex("""^[\u4e00-\u9fa5·]{4,}$""")

    /**
     * 明显「艺人名在上、歌名在下」的形状：
     * - A 同时含英文和中文，常见于「G.E.M. 邓紫棋」「周杰伦 Jay」「A-Lin 黄丽玲」这种艺名；
     * - B 是 4 字以上的纯中文短语，更像歌名。
     * 只做这一种低风险判断，其余交给本地模型，避免把英文歌名 + 中文译名歌手误换。
     */
    internal fun looksSwappedByShape(item: Item): Boolean {
        val title = item.title.trim()
        val artist = item.artist.trim()
        return title.length >= 3 &&
            artist.length >= 4 &&
            LATIN.containsMatchIn(title) &&
            HAN.containsMatchIn(title) &&
            HAN_PHRASE.matches(artist)
    }

    /** 解析本地模型输出；只认「数字 + SWAP」，其他内容一律忽略。 */
    internal fun parseSwapReply(reply: String): Set<Int> {
        val out = mutableSetOf<Int>()
        reply.lineSequence().forEach { line ->
            val m = SWAP_LINE.find(line.trim()) ?: return@forEach
            m.groupValues[1].toIntOrNull()?.let { out.add(it) }
        }
        return out
    }

    private suspend fun saveItems(
        context: Context,
        items: List<Item>,
        corrected: Int,
        aiUsed: Boolean
    ): Report {
        MusicPlayer.init(context)
        val now = System.currentTimeMillis()
        val songs = items.mapIndexed { index, item ->
            LocalSong(
                id = -((now * 1000L) + index + 1L),
                title = item.title,
                artist = item.artist,
                album = "截图导入",
                durationMs = 0L,
                uri = Uri.EMPTY,
                pendingSearch = true
            )
        }
        MusicPlayer.addPendingSongs(songs)
        withContext(Dispatchers.Main.immediate) {
            songs.forEach { MusicPlayer.markFavorite(it.id) }
        }
        val note = buildString {
            append("识别到 ${items.size} 首，已保存歌名/歌手")
            if (corrected > 0) {
                append(if (aiUsed) "，本地 AI 校正 $corrected 组颠倒" else "，已自动校正 $corrected 组颠倒")
            } else if (aiUsed) {
                append("，本地 AI 已检查顺序")
            } else {
                append("，本地 AI 未启用，未校对顺序")
            }
            append("；点击播放时再获取音源")
        }
        return Report(items, songs.size, 0, note, corrected)
    }

    /**
     * 点击播放时才调用：按歌名/歌手找音源并下载到缓存。
     * 导入阶段完全不联网，避免一次性打满网络、下错歌或占满存储。
     */
    suspend fun resolveSong(context: Context, title: String, artist: String): LocalSong? =
        withContext(Dispatchers.IO) {
            downloadOne(context, Item(title, artist))
        }

    private suspend fun downloadOne(context: Context, item: Item): LocalSong? {
        val title = item.title.trim()
        if (title.isBlank()) return null
        val queries = buildList {
            add(title)
            if (item.artist.isNotBlank()) add("$title ${item.artist}")
        }.distinct()
        val results = queries.firstNotNullOfOrNull { q ->
            SongSearch.search(q, 10).takeIf { it.isNotEmpty() }
        } ?: return null
        val pick = pickBest(title, item.artist, results) ?: return null
        return when (val fetched = SongSearch.fetch(pick)) {
            is SongFetch.Ok -> WebMusicCache.downloadAwait(
                context,
                fetched.audio.url,
                SongMeta(fetched.audio.title.ifBlank { title }, fetched.audio.artist),
                fetched.audio.referer,
                fetched.audio.cookie,
                fetched.audio.userAgent
            ).getOrNull()
            is SongFetch.Fail -> null
        }
    }

    private fun pickBest(title: String, artist: String, results: List<SongResult>): SongResult? {
        val nt = normalize(title)
        val na = normalize(artist)
        if (nt.isBlank()) return null
        val exact = results.filter { normalize(it.title) == nt }
        exact.firstOrNull { na.isNotBlank() && normalize(it.artist).contains(na) }?.let { return it }
        exact.firstOrNull()?.let { return it }
        val near = results.filter {
            val t = normalize(it.title)
            t.contains(nt) || nt.contains(t)
        }
        return near.firstOrNull { na.isNotBlank() && normalize(it.artist).contains(na) }
            ?: near.firstOrNull()
    }

    private fun isNoise(raw: String): Boolean {
        val t = raw.trim()
        if (t.isBlank()) return true
        if (TIME_ONLY.matches(t) || NUMBER_ONLY.matches(t)) return true
        if (NOISE_EXACT.any { t.equals(it, ignoreCase = true) }) return true
        if (t.startsWith("共") && t.endsWith("首")) return true
        if (t.startsWith("播放全部")) return true
        if (t.matches(Regex("""^[·•.\-—–_|/\s]+$"""))) return true
        if (t.startsWith("http://") || t.startsWith("https://")) return true
        return false
    }

    private fun normalize(raw: String): String = raw
        .replace('\u00A0', ' ')
        .replace('\u3000', ' ')
        .replace('０', '0').replace('１', '1').replace('２', '2')
        .replace('３', '3').replace('４', '4').replace('５', '5')
        .replace('６', '6').replace('７', '7').replace('８', '8').replace('９', '9')
        .replace('：', ':')
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun cleanup(raw: String): String = raw
        .replace(LEADING_INDEX, "")
        .replace(TRAILING_BADGE, "")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trim('·', '•', '-', '—', '–', '|', '｜', ',', '，')
        .trim()
}
