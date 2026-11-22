package com.example.my.data.music

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.my.data.AiApi
import com.example.my.data.AiEndpoint
import com.example.my.data.AiPresets
import com.example.my.data.WebHit
import com.example.my.data.WebIncognito
import com.example.my.data.WebSafety
import com.example.my.data.WebSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/** 一行带时间轴的歌词；翻译歌词会并进同一行的第二行文本 */
data class LrcLine(val timeMs: Long, val text: String)

/** 歌词来源：用于 UI 标注可信度（AI 结果可能不准） */
enum class LyricsSource { LOCAL_FILE, WEB, NETEASE, QQ, KUGOU, LRCLIB, QUHAI, AI }

/** 一次歌词查询的结果；lines 为空时 plain 是纯文本歌词
 *  [matchScore] 是该结果对应候选的匹配分，供四个在线源之间统一择优（不是先到先用）。 */
data class LyricsResult(
    val lines: List<LrcLine>,
    val plain: String?,
    val source: LyricsSource,
    val matchScore: Int = 0
) {
    val hasContent: Boolean get() = lines.isNotEmpty() || !plain.isNullOrBlank()
}

/**
 * 歌词获取。源顺序：
 *
 *   1) 本地 .lrc：手动导入 / 音频同目录同名 —— 离线优先、带时间轴、最准；
 *   2) 联网搜索（首选在线源）：Bing 检索「歌名 歌手 歌词」→ 抓前几个歌词页 →
 *      LyricsCleaner 洗成纯歌词（去导航/推荐位/制作信息，校验页面确实提到这首歌）。
 *      不绑任何音乐平台接口，平台改版也不影响；
 *   3) 网易云 / QQ 音乐 / LRCLIB / 酷狗：仅在网页拿不到可读歌词时兜底；
 *   6) AI：当前选中的 API 端点（不可用时退内置），允许吐 LRC，严禁编造。
 *
 * 在线源 2~5 **并发发起，全部返回后按候选匹配分择优**（不能按源顺序先到先用：
 * 网易云常有同名二创，QQ 才有正版）。每个源都做候选打分（歌手精确匹配、时长差、
 * 二创降权）与内容校验（剔除制作信息行、至少 2 行、非「纯音乐请欣赏」占位）。
 * 全部失败才记 miss 并给 UI 明确原因。
 */
object LyricsRepository {

    private const val NETEASE_SEARCH = "https://music.163.com/api/search/get"
    private const val NETEASE_LYRIC = "https://music.163.com/api/song/lyric"
    private const val QQ_SEARCH = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
    private const val QQ_LYRIC = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"
    private const val LRCLIB_GET = "https://lrclib.net/api/get"
    private const val LRCLIB_SEARCH = "https://lrclib.net/api/search"
    private const val KUGOU_SEARCH = "https://songsearch.kugou.com/song_search_v2"
    private const val KUGOU_CANDIDATES = "https://krcs.kugou.com/search"
    private const val KUGOU_DOWNLOAD = "https://lyrics.kugou.com/download"
    private const val MAX_LRC_BYTES = 512 * 1024
    private const val CACHE_DIR = "lyrics_cache"
    private const val CACHE_VERSION = 1

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Mobile Safari/537.36"

    private var appContext: Context? = null
    private val cache = ConcurrentHashMap<String, LyricsResult>()
    private val misses: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** 最近一次失败时尝试过的源，供 UI 提示「已搜索 4 个来源」 */
    @Volatile
    var lastAttempt: String = ""
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun key(song: LocalSong) =
        "${song.title.trim()}|${song.artist.trim()}".lowercase()

    /** 文件名用 String.hashCode（Java 规范保证跨设备一致），避免中文/特殊字符落盘问题 */
    private fun fileName(k: String): String = "${k.hashCode()}.lrc"

    private fun internalFile(song: LocalSong): File? = importedFile(song, appContext)

    /** 手动导入 .lrc：复制到应用私有目录并立刻失效缓存，之后完全离线可用 */
    fun import(context: Context, song: LocalSong, uri: Uri): Boolean {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return false
            if (bytes.isEmpty() || bytes.size > MAX_LRC_BYTES) return false
            val dir = File(context.filesDir, "lyrics").apply { mkdirs() }
            File(dir, fileName(key(song))).writeBytes(bytes)
            invalidate(song)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 删除这首歌导入的歌词（连同缓存），恢复自动匹配 */
    fun removeImported(song: LocalSong, context: Context? = appContext) {
        runCatching { importedFile(song, context)?.takeIf { it.isFile }?.delete() }
        invalidate(song)
    }

    /** 传 context 是为了菜单在 init 之前也能正确判断，避免初始化顺序影响 UI */
    fun hasImported(song: LocalSong, context: Context? = appContext): Boolean =
        importedFile(song, context)?.isFile == true

    private fun importedFile(song: LocalSong, context: Context?): File? =
        context?.let { File(File(it.filesDir, "lyrics"), fileName(key(song))) }

    private fun invalidate(song: LocalSong) {
        val k = key(song)
        cache.remove(k)
        misses.remove(k)
        cacheFile(k)?.let { runCatching { if (it.isFile) it.delete() } }
    }

    /** 清掉整份缓存（换网络、换模型后重试用） */
    fun clearCache(context: Context? = appContext) {
        cache.clear()
        misses.clear()
        context?.let {
            runCatching { File(it.filesDir, CACHE_DIR).deleteRecursively() }
        }
    }

    // ---------- 歌词落盘缓存 ----------
    //
    // 只缓存已经拿到的有效歌词（AI 生成的不落盘，避免把可能不准的内容长期固化）。
    // 一首歌一个 JSON 小文件，按「歌名|歌手」哈希命名，文件内再校验 key，防哈希碰撞串歌。
    // 不设数量/时间上限，由用户在设置页手动清理。

    private fun cacheFile(k: String): File? {
        val ctx = appContext ?: return null
        return File(File(ctx.filesDir, CACHE_DIR), "${k.hashCode()}.json")
    }

    private fun loadCachedLyrics(k: String): LyricsResult? {
        val file = cacheFile(k) ?: return null
        if (!file.isFile) return null
        val raw = runCatching { file.readText() }.getOrNull() ?: return null
        val result = runCatching {
            val o = JSONObject(raw)
            if (o.optInt("v") != CACHE_VERSION || o.optString("key") != k) return null
            val source = runCatching {
                LyricsSource.valueOf(o.optString("source"))
            }.getOrDefault(LyricsSource.WEB)
            if (source == LyricsSource.AI) return null
            val arr = o.optJSONArray("lines") ?: JSONArray()
            val lines = buildList {
                for (i in 0 until arr.length()) {
                    val line = arr.optJSONObject(i) ?: continue
                    val text = line.optString("text")
                    if (text.isBlank()) continue
                    add(LrcLine(line.optLong("time"), text))
                }
            }
            val plain = o.optString("plain").takeIf { it.isNotBlank() }
            LyricsResult(
                lines = lines,
                plain = plain,
                source = source,
                matchScore = o.optInt("score")
            ).takeIf { it.hasContent }
        }.getOrNull() ?: return null
        runCatching { file.setLastModified(System.currentTimeMillis()) }
        return result
    }

    private fun saveCachedLyrics(k: String, result: LyricsResult) {
        if (result.source == LyricsSource.AI || !result.hasContent) return
        val file = cacheFile(k) ?: return
        runCatching {
            val dir = file.parentFile ?: return
            if (!dir.isDirectory && !dir.mkdirs()) return
            val o = JSONObject()
                .put("v", CACHE_VERSION)
                .put("key", k)
                .put("source", result.source.name)
                .put("score", result.matchScore)
                .put("plain", result.plain.orEmpty())
            val arr = JSONArray()
            result.lines.forEach { line ->
                arr.put(JSONObject().put("time", line.timeMs).put("text", line.text))
            }
            o.put("lines", arr)
            file.writeText(o.toString())
        }
    }

    /** 设置页展示：已缓存多少首、占多少空间 */
    fun cacheSummary(context: Context? = appContext): String {
        val dir = context?.let { File(it.filesDir, CACHE_DIR) } ?: return "0 首"
        val files = dir.listFiles { f -> f.isFile && f.extension.equals("json", true) } ?: return "0 首"
        val bytes = files.sumOf { it.length() }
        val size = when {
            bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
            bytes >= 1024L -> "${bytes / 1024L} KB"
            else -> "$bytes B"
        }
        return "${files.size} 首 · $size"
    }

    /**
     * @param force true 时忽略「上次没查到」的记忆，用于用户点重试
     * @param endpoint 当前选中的 API 端点；不传或不可用时退内置端点
     * @param query 手动指定的「歌名 to 歌手」，自动匹配不到时由用户输入纠正
     */
    suspend fun fetch(
        song: LocalSong,
        endpoint: AiEndpoint? = null,
        force: Boolean = false,
        query: Pair<String, String>? = null
    ): LyricsResult? = withContext(Dispatchers.IO) {
        val target = if (query != null) song.copy(title = query.first, artist = query.second) else song
        val k = key(target)
        if (force) invalidate(target) else cache[k]?.let { return@withContext it }
        if (misses.contains(k)) return@withContext null

        // 手动改了歌名就不必再按新名字去找本地文件（文件是按原始歌曲导入的）
        val local = if (query == null) valid(loadLocal(target)) else null
        if (local != null) {
            cache[k] = local
            lastAttempt = "本地 .lrc"
            return@withContext local
        }

        // 上次联网拿到的完整歌词已落盘：直接复用，不再打 6 个在线源。
        val disk = loadCachedLyrics(k)
        if (disk != null) {
            cache[k] = disk
            lastAttempt = "本地歌词缓存"
            return@withContext disk
        }

        val attempted = mutableListOf<String>()
        var found: LyricsResult? = null
        val hits = CopyOnWriteArrayList<SourceHit>()
        coroutineScope {
            val jobs = listOf(
                // 联网搜索排第一：不绑任何音乐平台接口，网页里洗出歌词直接给用户
                async { hits.add(SourceHit("联网搜索", 0, withTimeoutOrNull(14_000L) { safe { fetchWeb(target) } })) },
                async { hits.add(SourceHit("网易云", 1, withTimeoutOrNull(9_000L) { safe { fetchNetease(target) } })) },
                async { hits.add(SourceHit("QQ音乐", 2, withTimeoutOrNull(9_000L) { safe { fetchQq(target) } })) },
                async { hits.add(SourceHit("LRCLIB", 3, withTimeoutOrNull(9_000L) { safe { fetchLrclib(target) } })) },
                async { hits.add(SourceHit("酷狗", 4, withTimeoutOrNull(9_000L) { safe { fetchKugou(target) } })) },
                // 曲海详情页给的是带时间轴的整首 LRC，中文歌覆盖好、不依赖平台接口，
                // 作为「完整歌词」的补充源（只取词，不换音频直链，避开风控接口）
                async { hits.add(SourceHit("曲海", 5, withTimeoutOrNull(10_000L) { safe { fetchQuhai(target) } })) }
            )
            // 各源全部返回后统一择优，绝不「先到先用」。
            // 血泪教训一：网易云搜「红尘客栈 周杰伦」第一条是 48 秒同名二创，
            //   QQ 音乐才有正版；先返回就 break 会把错误歌词端上来。
            // 血泪教训二：联网洗词只有 5~6 行片段，曾被硬编码 130 分无条件置顶，
            //   把 QQ / 曲海的 44 行完整 LRC 挤掉——「青花瓷」截图就是这么只剩几句的。
            withTimeoutOrNull(16_000L) { jobs.awaitAll() }
            jobs.forEach { if (it.isActive) it.cancel() }
        }
        attempted.addAll(listOf("联网搜索", "网易云", "QQ音乐", "LRCLIB", "酷狗", "曲海"))
        val validHits = hits.toList().mapNotNull { h ->
            valid(h.result)?.let { h.copy(result = it) }
        }
        // 统一按「完整度 + 匹配度」择优：带时间轴的整首 LRC 每行权重 3，
        // 纯文本片段每行 1；平台匹配分叠加，低分同名歌（<15）直接出局。
        found = validHits.filter { h ->
            val r = h.result
            r != null && (r.source == LyricsSource.WEB || r.matchScore >= 15)
        }.maxWithOrNull(
            compareByDescending<SourceHit> { it.result?.let { r -> quality(r) } ?: 0 }
                .thenBy { it.rank }
        )?.result

        val result = found ?: run {
            attempted.add("AI")
            try {
                valid(fetchAi(target, endpoint?.takeIf { it.ready } ?: AiPresets.builtin))
            } catch (_: Exception) {
                null
            }
        }

        lastAttempt = attempted.joinToString(" / ")
        if (result != null) {
            cache[k] = result
            saveCachedLyrics(k, result)
        } else {
            misses.add(k)
        }
        result
    }

    /** 最近一次查过但没结果的来源列表（失败提示用） */
    fun attemptedSources(): String = lastAttempt

    // ---------- 1. 本地 .lrc ----------

    private fun loadLocal(song: LocalSong): LyricsResult? {
        val imported = internalFile(song)?.takeIf { it.isFile && it.length() <= MAX_LRC_BYTES }
        if (imported != null) return parseTextFile(imported)

        val path = song.path
        if (path.isBlank()) return null
        val audio = File(path)
        val dir = audio.parentFile ?: return null
        val base = audio.nameWithoutExtension
        val candidates = listOfNotNull(
            File(dir, "$base.lrc"),
            File(dir, "$base.LRC"),
            File(dir, "${song.title}.lrc"),
            File(dir, "${song.artist} - ${song.title}.lrc"),
            // 歌名识别前的原始文件名（1231红尘客栈.lrc 之类）
            song.rawTitle.takeIf { it.isNotBlank() }?.let { File(dir, "$it.lrc") }
        )
        val found = candidates.firstOrNull { it.isFile && it.length() <= MAX_LRC_BYTES }
            ?: return null
        return parseTextFile(found)
    }

    private fun parseTextFile(file: File): LyricsResult? {
        val text = runCatching { decodeText(file.readBytes()) }.getOrNull() ?: return null
        if (text.isBlank()) return null
        val lines = parseLrc(text)
        return LyricsResult(
            lines = lines,
            plain = if (lines.isEmpty()) cleanPlain(text).takeIf { it.isNotBlank() } else null,
            source = LyricsSource.LOCAL_FILE
        )
    }

    /** 中文 .lrc 常见 GBK，先用严格 UTF-8 解码，失败再退 GBK，避免乱码 */
    private fun decodeText(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val s = try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            String(bytes, Charset.forName("GBK"))
        }
        return s.trimStart('\uFEFF')
    }

    // ---------- 2. 网易云 ----------

    private data class Candidate(
        val id: String, val title: String, val artist: String,
        val durationSec: Int, val score: Int
    )

    /** 一个在线源的结果；rank 是同分时的优先级（0 最高） */
    private data class SourceHit(val name: String, val rank: Int, val result: LyricsResult?)

    private fun fetchNetease(song: LocalSong): LyricsResult? {
        val (title, artist) = queryOf(song)
        val url = "$NETEASE_SEARCH?s=${enc("$title $artist")}&type=1&offset=0&limit=8"
        val body = httpGet(url, mapOf("Referer" to "https://music.163.com/")) ?: return null
        val arr = JSONObject(body).optJSONObject("result")?.optJSONArray("songs") ?: return null
        val durSec = (song.durationMs / 1000L).toInt()
        val cands = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optLong("id", 0L).takeIf { it > 0 } ?: return@mapNotNull null
            val name = o.optString("name", "")
            val singers = joinNames(o.optJSONArray("artists"), "name")
            Candidate(
                id.toString(), name, singers,
                (o.optLong("duration", 0L) / 1000L).toInt(),
                score(title, artist, name, singers, (o.optLong("duration", 0L) / 1000L).toInt(), durSec)
            )
        }.sortedByDescending { it.score }.take(3)

        for (c in cands) {
            val lyricJson = httpGet(
                "$NETEASE_LYRIC?id=${c.id}&lv=1&kv=1&tv=-1",
                mapOf("Referer" to "https://music.163.com/")
            ) ?: continue
            val root = runCatching { JSONObject(lyricJson) }.getOrNull() ?: continue
            val lrc = root.optJSONObject("lrc")?.optString("lyric").orEmpty()
            val trans = root.optJSONObject("tlyric")?.optString("lyric").orEmpty()
            val result = buildResult(lrc, trans, LyricsSource.NETEASE)
            if (result != null) return result.copy(matchScore = c.score)
        }
        return null
    }

    // ---------- 3. QQ 音乐 ----------

    private fun fetchQq(song: LocalSong): LyricsResult? {
        val (title, artist) = queryOf(song)
        val url = "$QQ_SEARCH?p=1&n=8&w=${enc("$title $artist")}&format=json"
        val raw = httpGet(url, mapOf("Referer" to "https://y.qq.com/")) ?: return null
        val txt = raw.trim().let {
            if (it.startsWith("callback(")) it.removePrefix("callback(").removeSuffix(")") else it
        }
        val arr = runCatching { JSONObject(txt) }.getOrNull()
            ?.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list") ?: return null
        val durSec = (song.durationMs / 1000L).toInt()
        val cands = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val mid = o.optString("songmid", "").ifBlank { o.optString("mid", "") }
            if (mid.isBlank()) return@mapNotNull null
            val name = o.optString("songname", "")
            val singers = joinNames(o.optJSONArray("singer"), "name")
            val sec = o.optInt("interval", 0)
            Candidate(mid, name, singers, sec, score(title, artist, name, singers, sec, durSec))
        }.sortedByDescending { it.score }.take(3)

        for (c in cands) {
            val lyricJson = httpGet(
                "$QQ_LYRIC?songmid=${c.id}&format=json&nobase64=1",
                mapOf("Referer" to "https://y.qq.com/")
            ) ?: continue
            val root = runCatching { JSONObject(lyricJson) }.getOrNull() ?: continue
            var lrc = root.optString("lyric", "")
            // 少数接口忽略 nobase64，仍返回 base64
            if (lrc.isNotBlank() && !lrc.trimStart().startsWith("[")) {
                lrc = decodeBase64(lrc)
            }
            val trans = root.optString("trans", "").let {
                if (it.isNotBlank() && !it.trimStart().startsWith("[")) decodeBase64(it) else it
            }
            val result = buildResult(lrc, trans, LyricsSource.QQ)
            if (result != null) return result.copy(matchScore = c.score)
        }
        return null
    }

    // ---------- 4. LRCLIB ----------

    private fun fetchLrclib(song: LocalSong): LyricsResult? {
        val (title, artist) = queryOf(song)
        // 故意不传 duration：本地文件常与数据库差 1~2 秒，带上就 404
        val getBody = httpGet("$LRCLIB_GET?track_name=${enc(title)}&artist_name=${enc(artist)}")
        if (getBody != null) {
            // /api/get 是按歌名+歌手精确查询，置信度比搜索结果高
            runCatching { parseLrclibJson(JSONObject(getBody)) }.getOrNull()
                ?.let { return it.copy(matchScore = 130) }
        }
        val searchUrl = "$LRCLIB_SEARCH?track_name=${enc(title)}&artist_name=${enc(artist)}"
        val body = httpGet(searchUrl) ?: httpGet("$LRCLIB_SEARCH?q=${enc(title)}") ?: return null
        return runCatching {
            val arr = JSONArray(body)
            val durSec = (song.durationMs / 1000L).toInt()
            val hits = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val r = parseLrclibJson(o) ?: return@mapNotNull null
                val name = o.optString("trackName", "")
                val singer = o.optString("artistName", "")
                val sec = o.optInt("duration", 0)
                r to score(title, artist, name, singer, sec, durSec)
            }
            hits.maxByOrNull { it.second }?.let { (r, s) -> r.copy(matchScore = s) }
        }.getOrNull()
    }

    private fun parseLrclibJson(o: JSONObject): LyricsResult? {
        val synced = o.optString("syncedLyrics", "")
        val plain = cleanPlain(o.optString("plainLyrics", ""))
        val lines = parseLrc(synced)
        if (lines.isEmpty() && plain.isBlank()) return null
        return LyricsResult(lines, plain.ifBlank { null }, LyricsSource.LRCLIB)
    }

    // ---------- 5. 酷狗 ----------

    private fun fetchKugou(song: LocalSong): LyricsResult? {
        val (title, artist) = queryOf(song)
        val body = httpGet("$KUGOU_SEARCH?keyword=${enc("$title $artist")}&page=1&pagesize=8")
            ?: return null
        val arr = JSONObject(body).optJSONObject("data")?.optJSONArray("lists") ?: return null
        val durSec = (song.durationMs / 1000L).toInt()
        val cands = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val hash = o.optString("FileHash", "")
            if (hash.isBlank()) return@mapNotNull null
            val name = o.optString("SongName", "")
            val singers = o.optString("SingerName", "")
            val sec = o.optInt("Duration", 0)
            Candidate(hash, name, singers, sec, score(title, artist, name, singers, sec, durSec))
        }.sortedByDescending { it.score }.take(2)

        for (c in cands) {
            val candJson = httpGet(
                "$KUGOU_CANDIDATES?ver=1&man=yes&client=mobi&keyword=${enc(c.title)}&hash=${c.id}" +
                    "&timelength=${c.durationSec}"
            ) ?: continue
            val list = JSONObject(candJson).optJSONArray("candidates") ?: continue
            val first = (0 until list.length()).mapNotNull { list.optJSONObject(it) }
                .firstOrNull { it.optString("id").isNotBlank() && it.optString("accesskey").isNotBlank() }
                ?: continue
            val down = httpGet(
                "$KUGOU_DOWNLOAD?ver=1&client=pc&id=${first.optString("id")}" +
                    "&accesskey=${first.optString("accesskey")}&fmt=lrc&charset=utf8"
            ) ?: continue
            val lrc = decodeBase64(JSONObject(down).optString("content", ""))
            val result = buildResult(lrc, "", LyricsSource.KUGOU)
            if (result != null) return result.copy(matchScore = c.score)
        }
        return null
    }

    // ---------- 6. AI 兜底 ----------

    private suspend fun fetchAi(song: LocalSong, ep: AiEndpoint): LyricsResult? {
        val (title, artist) = queryOf(song)
        val text = AiApi.searchLyrics(ep, title, artist) ?: return null
        val lines = parseLrc(text)
        return LyricsResult(
            lines = lines,
            plain = if (lines.isEmpty()) cleanPlain(text) else null,
            source = LyricsSource.AI
        )
    }

    // ---------- 通用：查询词、打分、校验、HTTP ----------

    private data class Query(val title: String, val artist: String)

    private fun queryOf(song: LocalSong): Query {
        val t = cleanTitle(song.title)
        val a = cleanArtist(song.artist).orEmpty()
        return Query(t, a)
    }

    /**
     * 统一的歌名识别入口：音轨号（含「1231红尘客栈」这种无分隔符）、版本后缀、
     * "歌手 - 歌名" 拆分都交给 SongNameParser；别 substringBeforeLast('.')，会砍掉 "Mr. Brightside"。
     */
    private fun cleanTitle(raw: String): String = SongNameParser.clean(raw)

    private fun cleanArtist(raw: String): String? {
        val s = raw.trim()
        if (s.isBlank() || s == "<unknown>" || s == "未知艺术家" || s == "未知" ||
            s == "本地文件" || s == "未知歌手"
        ) return null
        return s.replace(Regex("""^\d{1,3}[\s.\-_]+"""), "").trim()
    }

    /** 归一化：去括号版本信息、去常见修饰词、只留中英日文字与数字 */
    private fun norm(s: String): String = s.lowercase()
        .replace(Regex("""\([^)]*\)|\[[^]]*]|（[^）]*）|【[^】]*】"""), " ")
        .replace(
            Regex(
                """\b(feat\.?|ft\.?|live|remastered?|instrumental|karaoke|cover|version|remix|acoustic|demo|official|mv|hd|dj|ost|theme)\b""",
                RegexOption.IGNORE_CASE
            ), " "
        )
        .replace(Regex("""^\d{1,3}[\s.\-_]+"""), "")
        .replace(Regex("""[^a-z0-9\u4e00-\u9fff\u3040-\u30ff]"""), "")

    /** 二创 / 片段 / 伴奏关键词：同名但内容常常完全不是一首歌 */
    private val COVER_WORDS = Regex(
        """翻唱|原唱|伴奏|纯音乐|instrumental|karaoke|tribute|cover|特别版|片段|剪辑|串烧|改编|戏腔|网红|抖音""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 候选打分：标题精确 > 歌手精确 > 时长。
     * 实测教训：网易云搜「红尘客栈 周杰伦」第一条是「红尘客栈·特别版」/「周杰伦. 沈幼楚」，
     * 48 秒二创片段靠歌手串里蹭到「周杰伦」骗到高分；所以蹭名不给高分、
     * 时长差 > 45 秒直接淘汰，且四个源的结果在 fetch() 里按分数统一择优。
     */
    private fun score(
        wantTitle: String, wantArtist: String?,
        candTitle: String, candArtist: String?, candDurSec: Int, wantDurSec: Int
    ): Int {
        val t = norm(wantTitle)
        val c = norm(candTitle)
        val exactTitle = t.isNotBlank() && t == c
        var s = when {
            t.isBlank() || c.isBlank() -> 0
            exactTitle -> 100
            c.contains(t) -> 45
            t.contains(c) -> 35
            else -> (similarity(t, c) * 40).toInt()
        }
        val a = norm(wantArtist.orEmpty())
        val ca = norm(candArtist.orEmpty())
        if (a.isNotBlank()) {
            s += when {
                ca.isBlank() -> -15
                ca == a -> 90
                // 合唱 / 蹭名：歌手串含目标但还挂着别人（"周杰伦 沈幼楚"），不能算精确命中
                ca.contains(a) || a.contains(ca) -> 5
                else -> -140
            }
        }
        if (wantDurSec > 0 && candDurSec > 0) {
            val d = abs(wantDurSec - candDurSec)
            s += when {
                d <= 5 -> 30
                d <= 15 -> 15
                d <= 45 -> 0
                else -> -100
            }
        }
        // 标题不是精确同名 + 歌手串还多出人名的，基本是同名二创，再压一档
        if (!exactTitle && a.isNotBlank() && ca.length > a.length + 1) s -= 30
        if (COVER_WORDS.containsMatchIn(candTitle)) s -= 40
        return s
    }

    /** 归一化 Levenshtein 相似度 0~1 */
    private fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (abs(a.length - b.length) > 12) return 0f
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1) { i }
            for (j in 1..b.length) {
                cur[j] = minOf(
                    prev[j] + 1, cur[j - 1] + 1,
                    prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                )
            }
            prev = cur
        }
        return 1f - prev[b.length].toFloat() / maxOf(a.length, b.length)
    }

    private val PLACEHOLDER = Regex(
        """纯音乐[，,]?\s*请欣赏|此歌曲为没有填词|暂无歌词|no lyrics|instrumental only""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 网易云/QQ/酷狗常把制作信息写成带时间轴的「歌词行」（[00:00.00] 作词 : 沈幼楚）。
     * 这些行不是歌词，整列上移时会飘到顶部盖住歌名 —— 截图中重叠的就是它们。
     */
    private val CREDIT_LINE = Regex(
        """^(?:作词|作曲|编曲|词|曲|演唱|原唱|和声|和声编写|制作人|监制|出品|出品人|发行|混音|录音|母带|吉他|钢琴|鼓|贝斯|弦乐|统筹|策划|文案|版权|OP|SP|歌词|Lyric|Lyrics|Composer|Arranger|Producer|Vocal|Mixed\s*by|Mastered\s*by)\s*[:：]""",
        RegexOption.IGNORE_CASE
    )
    /** 「歌名 - 歌手 (别名)」这类 LRC 头行，例如「红尘客栈 - 周杰伦 (Jay Chou)」 */
    private val HEADER_LINE = Regex("""^[^\-]{2,40}\s+-\s+[^\-]{2,40}\s*(?:\([^)]*\)|（[^）]*）)\s*$""")
    /** 纯标点 / 省略号占位行 */
    private val NOISE_LINE = Regex("""^[\s·•\-—_.…。,:：;；/\\|]+$""")

    private fun isJunkLine(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        return CREDIT_LINE.containsMatchIn(t) || HEADER_LINE.containsMatchIn(t) ||
            NOISE_LINE.matches(t)
    }

    /** 纯文本歌词同样要剔除制作信息行 */
    fun cleanPlain(raw: String): String = raw.lines()
        .map { it.trim() }
        .filterNot { isJunkLine(it) }
        .joinToString("\n").trim()

    /** 内容校验：剔除制作信息后至少 2 行有效歌词，排除「纯音乐，请欣赏」这类占位 */
    private fun valid(r: LyricsResult?): LyricsResult? {
        if (r == null) return null
        val lines = r.lines.filterNot { isJunkLine(it.text) }
        val plain = r.plain?.let { cleanPlain(it) }?.takeIf { it.isNotBlank() }
        val cleaned = r.copy(lines = lines, plain = if (lines.isEmpty()) plain else null)
        if (!cleaned.hasContent) return null
        val lineCount = if (lines.isNotEmpty()) lines.size
        else plain?.lines()?.count { it.isNotBlank() } ?: 0
        if (lineCount < 2) return null
        val text = if (lines.isNotEmpty()) lines.joinToString(" ") { it.text }
        else plain.orEmpty()
        if (PLACEHOLDER.containsMatchIn(text)) return null
        return cleaned
    }

    /** 把 LRC 原文 + 翻译轨合并：同时间戳的译文并成该行第二行 */
    private fun buildResult(lrc: String, trans: String, source: LyricsSource): LyricsResult? {
        val lines = parseLrc(lrc)
        if (lines.isEmpty()) return null
        val transLines = if (trans.isBlank()) emptyList() else parseLrc(trans)
        val merged = if (transLines.isEmpty()) lines else {
            lines.map { l ->
                // 不同平台的翻译轨常差几十毫秒，按最近时间戳合并，不能要求完全相等
                val t = transLines.minByOrNull { kotlin.math.abs(it.timeMs - l.timeMs) }
                    ?.takeIf {
                        kotlin.math.abs(it.timeMs - l.timeMs) <= 500 &&
                            it.text.isNotBlank() && it.text != l.text
                    }
                if (t != null) l.copy(text = "${l.text}\n${t.text}") else l
            }
        }
        return LyricsResult(merged, null, source)
    }

    private fun joinNames(arr: JSONArray?, field: String): String {
        if (arr == null) return ""
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString(field) }
            .filter { it.isNotBlank() }.distinct().joinToString(" ")
    }

    private fun decodeBase64(s: String): String = try {
        String(Base64.decode(s.trim(), Base64.DEFAULT), Charsets.UTF_8).trimStart('\uFEFF')
    } catch (_: Exception) {
        ""
    }

    private inline fun <T> safe(block: () -> T): T? = try {
        block()
    } catch (_: Exception) {
        null
    }

    // ---------- 联网搜索（首选源） ----------

    /**
     * 择优质量分：带时间轴的整首 LRC 每行 3 分（能逐行同步、可点击跳转），
     * 纯文本每行 1 分；再叠加平台候选匹配分，避免低分同名歌靠行数多翻盘。
     * 联网洗词没有时间轴也不参与匹配分，只有确实比 LRC 更完整时才可能胜出。
     */
    private fun quality(r: LyricsResult): Int {
        val body = if (r.lines.isNotEmpty()) r.lines.size * 3 + 45
        else r.plain?.lines()?.count { it.isNotBlank() } ?: 0
        return body + if (r.source == LyricsSource.WEB) 0 else r.matchScore
    }

    /** 曲海整首 LRC：复用 SongSearch 的详情页解析，转成带时间轴的行 */
    private suspend fun fetchQuhai(song: LocalSong): LyricsResult? {
        val (title, artist) = queryOf(song)
        val raw = SongSearch.fetchLyricsOnly(title, artist) ?: return null
        val lines = parseLrc(raw)
        if (lines.size < 8) return null
        // 40 分：标题匹配但没做时长校验，低于平台精确命中，定位为补充源
        return LyricsResult(lines, null, LyricsSource.QUHAI, matchScore = 40)
    }

    /**
     * 用全网搜索找歌词：检索 → 抓前几个「像歌词页」的网页 → 洗成纯歌词。
     * 完全不依赖任何音乐平台接口，平台挂了 / 改版了也照常能出词。
     * 注意：这里洗出的是**没有时间轴的纯文本片段**，只作为兜底，
     * 不能再像以前那样无条件压过平台 / 曲海的完整 LRC。
     */
    private suspend fun fetchWeb(song: LocalSong): LyricsResult? {
        val q = queryOf(song)
        if (q.title.isBlank()) return null
        // 歌词页的标题写法很杂：歌名、歌手、歌词三件套齐全的命中率最高，
        // 再交给 WebSearch 做多角度扩展（它会给「完整版」「纯歌词」等变体）
        val keyword = listOf(q.title, q.artist).filter { it.isNotBlank() }.joinToString(" ") + " 歌词"
        val hits = withTimeoutOrNull(7_000L) { runCatching { WebSearch.search(keyword, 12) }.getOrNull() }
            .orEmpty()
            // 搜索结果里的地址同样是外部输入：伪协议 / 伪装域名一律不抓
            .filter { WebSafety.sanitize(it.url) != null }
        if (hits.isEmpty()) return null

        // 标题带「歌词」、域名带 geci/lrc/lyric 的优先；抓 6 页并发，谁先洗出完整歌词谁赢
        val ranked = hits.sortedByDescending { webRank(it, q.title, q.artist) }.take(6)
        val pages = coroutineScope {
            ranked.map { h ->
                async { withTimeoutOrNull(6_000L) { runCatching { fetchPage(h.url) }.getOrNull() } }
            }.awaitAll()
        }

        var best: String? = null
        var bestScore = 0
        pages.forEach { html ->
            if (html.isNullOrBlank()) return@forEach
            val lyrics = runCatching { LyricsCleaner.extract(html, q.title, q.artist) }.getOrNull()
                ?: return@forEach
            // 太短的多半是「歌词片段」或站点导航，不能当作整首。
            // 门槛从 6 行抬到 8 行：截图里那次就是 6 行片段冒充整首青花瓷。
            if (lyrics.length < 100) return@forEach
            val lineCount = lyrics.count { it == '\n' } + 1
            if (lineCount < 8) return@forEach
            // 同一首歌不同站点，行数多的一般更完整；再按歌名出现与否加分
            val score = lyrics.length + lineCount * 4 +
                if (lyrics.contains(q.title)) 20 else 0
            if (score > bestScore) {
                bestScore = score
                best = lyrics
            }
        }
        val plain = best ?: return null
        // 不再硬编码 130 分冒充「最可信」；质量分统一交给 fetch() 的 quality() 算，
        // 让带时间轴的完整 LRC 稳稳压过没有时间轴的片段。
        val lineCount = plain.lines().count { it.isNotBlank() }
        return LyricsResult(
            lines = emptyList(), plain = plain, source = LyricsSource.WEB,
            matchScore = minOf(90, lineCount * 2)
        )
    }

    private fun webRank(h: WebHit, title: String, artist: String): Int {
        val compactTitle = title.replace(" ", "")
        val pageTitle = h.title.replace(" ", "")
        var s = 0
        if (h.title.contains("歌词")) s += 40
        if (pageTitle.contains(compactTitle, ignoreCase = true)) s += 22
        if (artist.isNotBlank() && pageTitle.contains(artist.replace(" ", ""), ignoreCase = true)) s += 8
        if (h.url.contains("geci") || h.url.contains("lrc") || h.url.contains("lyric")) s += 30
        // 专门的歌词站比综合站更可能给整首，而不是一段试听词
        if (LYRIC_HOSTS.any { h.site.endsWith(it) }) s += 18
        if (h.snippet.contains(title)) s += 8
        // 「歌词大全 / 相关推荐 / 伴奏」这类聚合页经常只有片段或别的歌
        if (h.title.contains("歌词大全") || h.title.contains("相关推荐") || h.title.contains("伴奏")) s -= 12
        return s
    }

    private val LYRIC_HOSTS = listOf(
        "geci.net", "geciwang.com", "gequhai.com", "lyrics.net.cn",
        "lrc.la", "geci8.com", "cnlyric.com", "51lrc.com", "9ku.com"
    )

    /** 抓网页：限字节数、按响应头 / meta 判编码，中文歌词站不少还是 GBK */
    private fun fetchPage(urlStr: String): String? {
        val safeUrl = WebSafety.sanitize(urlStr) ?: return null
        val conn = try {
            URL(safeUrl).openConnection() as HttpURLConnection
        } catch (_: Exception) {
            return null
        }
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 7000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            WebIncognito.applyTo(conn)
            if (conn.responseCode != 200) null
            else {
                val bytes = readAtMost(conn.inputStream, 400_000)
                String(bytes, charsetOf(conn.contentType, bytes))
            }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun readAtMost(input: java.io.InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        input.use { stream ->
            while (out.size() < max) {
                val n = stream.read(buf)
                if (n <= 0) break
                out.write(buf, 0, minOf(n, max - out.size()))
            }
        }
        return out.toByteArray()
    }

    private fun charsetOf(contentType: String?, bytes: ByteArray): Charset {
        val header = contentType?.let {
            Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)
        }
        // 先用 latin1 只为了读 <meta charset>，不会因为解错编码抛异常
        val head = String(bytes.copyOf(minOf(bytes.size, 2048)), Charsets.ISO_8859_1)
        val meta = Regex("charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE)
            .find(head)?.groupValues?.get(1)
        val name = (header ?: meta ?: "utf-8").lowercase()
        val fixed = when (name) {
            "gb2312", "gbk", "gb18030" -> "GBK"
            else -> name
        }
        return runCatching { Charset.forName(fixed) }.getOrDefault(Charsets.UTF_8)
    }

    private fun httpGet(urlStr: String, headers: Map<String, String> = emptyMap()): String? {
        val conn = try {
            URL(urlStr).openConnection() as HttpURLConnection
        } catch (_: Exception) {
            return null
        }
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 7000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "*/*")
            WebIncognito.applyTo(conn)
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (conn.responseCode != 200) null
            else conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ---------- LRC 解析 ----------

    private val LRC_TIME_TAG = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val LRC_OFFSET_TAG = Regex("""^\[offset\s*:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)
    private val LRC_META_TAG = Regex(
        """\[(?:ar|ti|al|by|offset|re|ve|length|au)\s*:[^\]]*]""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 解析 .lrc：[mm:ss.xx] 文本，一行可能多个时间标签；无时间标签时返回空列表。
     * 同时支持 `[offset:+1200]`（整体平移，单位毫秒），避免平台歌词整体提前/延后。
     */
    fun parseLrc(raw: String): List<LrcLine> {
        if (raw.isBlank()) return emptyList()
        var offsetMs = 0L
        raw.lines().forEach { line ->
            LRC_OFFSET_TAG.find(line.trim())?.let {
                offsetMs = it.groupValues[1].toLongOrNull() ?: 0L
            }
        }
        val result = mutableListOf<LrcLine>()
        raw.lines().forEach { line ->
            val matches = LRC_TIME_TAG.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val text = LRC_META_TAG.replace(LRC_TIME_TAG.replace(line, ""), "").trim()
            if (text.isBlank() || isJunkLine(text)) return@forEach
            matches.forEach { m ->
                val min = m.groupValues[1].toIntOrNull() ?: 0
                val sec = m.groupValues[2].toIntOrNull() ?: 0
                val frac = m.groupValues[3].padEnd(3, '0').take(3).toIntOrNull() ?: 0
                val ms = (min * 60_000L + sec * 1000L + frac.toLong() + offsetMs)
                    .coerceAtLeast(0L)
                result.add(LrcLine(ms, text))
            }
        }
        return result
            .distinctBy { it.timeMs to it.text }
            .sortedBy { it.timeMs }
    }

    /**
     * 没有 LRC 时间轴时，按歌词字数和歌曲时长估算每一句的起始时间。
     * 只是“按比例对齐”，不声称是原版时间轴；用于保证纯文本歌词也能逐行跟唱，
     * 而不是每行等分、前面几句一直亮着。
     */
    fun estimateLines(plain: String, durationMs: Long): List<LrcLine> {
        val lines = plain.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !isJunkLine(it) }
        if (lines.isEmpty()) return emptyList()
        val total = durationMs.takeIf { it > 5_000L } ?: (lines.size * 2_600L)
        val intro = minOf(3_500L, total / 12)
        val outro = minOf(2_500L, total / 20)
        val span = (total - intro - outro).coerceAtLeast(lines.size * 900L)
        val weights = lines.map { line ->
            line.count { !it.isWhitespace() }.coerceAtLeast(2).toFloat()
        }
        val weightSum = weights.sum().coerceAtLeast(1f)
        var cursor = intro
        var lastAt = 0L
        return lines.mapIndexed { i, text ->
            val at = maxOf(cursor, lastAt + 1L)
                .coerceAtMost((total - 800L).coerceAtLeast(lastAt + 1L))
            lastAt = at
            val slot = if (i == lines.lastIndex) {
                (total - outro - at).coerceAtLeast(900L)
            } else {
                (span * (weights[i] / weightSum)).toLong().coerceAtLeast(900L)
            }
            cursor = at + slot
            LrcLine(at, text)
        }
    }
}
