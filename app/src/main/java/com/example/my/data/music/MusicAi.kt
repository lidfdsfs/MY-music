package com.example.my.data.music

import com.example.my.data.AiApi
import com.example.my.data.AiEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

/**
 * AI 找歌：模型只负责「理解描述 + 提议候选」，每条候选都必须再经 SongSearch
 * 真实检索核对；核对不到的候选会被丢弃，不把模型编造的歌名展示给用户。
 */
object MusicAi {

    data class Track(
        val title: String,
        val artist: String,
        val reason: String,
        val result: SongResult
    )

    data class Plan(
        val interpretation: String,
        val summary: String,
        val tracks: List<Track>,
        val fallback: Boolean = false
    )

    private data class Draft(val title: String, val artist: String, val reason: String)

    private val FENCE = Regex("""^```[a-zA-Z]*\s*|\s*```$""")

    suspend fun recommend(
        endpoint: AiEndpoint,
        request: String
    ): Plan = withContext(Dispatchers.IO) {
        val raw = AiApi.requestOnce(endpoint, SYSTEM, userPrompt(request))
        val parsed = parseDraft(raw)
        if (parsed == null) return@withContext directSearch(request, "AI 暂时不可用，先按曲库直接搜索")

        val tracks = ArrayList<Track>()
        // 只核对前 3 首：够用，也避免对免费曲库短时间打太多请求。
        for (draft in parsed.second.take(3)) {
            val hit = verify(draft) ?: continue
            tracks += Track(
                title = hit.title,
                artist = hit.artist,
                reason = draft.reason,
                result = hit
            )
        }
        if (tracks.isEmpty()) {
            return@withContext Plan(
                interpretation = parsed.first.ifBlank { request.take(40) },
                summary = "AI 提议的歌在曲库里没有核对到，换个说法或直接搜歌名试试",
                tracks = emptyList()
            )
        }
        Plan(
            interpretation = parsed.first.ifBlank { request.take(40) },
            summary = "已按「${parsed.first.ifBlank { request.take(24) }}」核对曲库，找到 ${tracks.size} 首",
            tracks = tracks
        )
    }

    /**
     * AI 情绪歌单：和 [recommend] 同一套「模型提议 + 曲库核对」链路，
     * 但候选规模更大（want 首）、逐条核对，核对过的直接按顺序组成歌单。
     */
    suspend fun playlist(
        endpoint: AiEndpoint,
        request: String,
        want: Int
    ): Plan = withContext(Dispatchers.IO) {
        val n = want.coerceIn(3, 10)
        val raw = AiApi.requestOnce(endpoint, SYSTEM, playlistPrompt(request, n))
        val parsed = parseDraft(raw)
        if (parsed == null) return@withContext directSearch(request, "AI 暂时不可用，先按曲库直接搜索")

        val tracks = ArrayList<Track>()
        for (draft in parsed.second.take(n)) {
            val hit = verify(draft) ?: continue
            tracks += Track(
                title = hit.title,
                artist = hit.artist,
                reason = draft.reason,
                result = hit
            )
        }
        if (tracks.isEmpty()) {
            return@withContext Plan(
                interpretation = parsed.first.ifBlank { request.take(40) },
                summary = "AI 提议的歌在曲库里没有核对到，换个说法试试",
                tracks = emptyList()
            )
        }
        Plan(
            interpretation = parsed.first.ifBlank { request.take(40) },
            summary = "已按「${parsed.first.ifBlank { request.take(24) }}」核对曲库，组成 ${tracks.size} 首的歌单",
            tracks = tracks
        )
    }

    /** 只保留能在真实曲库里匹配到标题的候选；纯函数，便于单测。 */
    fun pickMatch(title: String, artist: String, results: List<SongResult>): SongResult? {        if (title.isBlank() || results.isEmpty()) return null
        val wantTitle = norm(title)
        val wantArtist = norm(artist)
        var best: SongResult? = null
        var bestScore = 0
        for (item in results) {
            val itemTitle = norm(item.title)
            val itemArtist = norm(item.artist)
            val titleScore = when {
                itemTitle == wantTitle -> 5
                itemTitle.contains(wantTitle) || wantTitle.contains(itemTitle) -> 3
                else -> 0
            }
            if (titleScore == 0) continue
            val artistScore = when {
                wantArtist.isBlank() -> 0
                itemArtist == wantArtist -> 2
                itemArtist.contains(wantArtist) || wantArtist.contains(itemArtist) -> 1
                else -> 0
            }
            val score = titleScore + artistScore
            if (score > bestScore) {
                bestScore = score
                best = item
            }
        }
        return best
    }

    private suspend fun verify(draft: Draft): SongResult? {
        val query = listOf(draft.title, draft.artist).filter { it.isNotBlank() }.joinToString(" ")
        val hits = SongSearch.search(query, 5).ifEmpty {
            if (draft.artist.isBlank()) emptyList() else SongSearch.search(draft.title, 5)
        }
        return pickMatch(draft.title, draft.artist, hits)
    }

    private suspend fun directSearch(request: String, summary: String): Plan {
        val hits = SongSearch.search(request, 5)
        return Plan(
            interpretation = request.take(40),
            summary = if (hits.isEmpty()) "$summary；曲库没有匹配结果" else summary,
            tracks = hits.map {
                Track(
                    title = it.title,
                    artist = it.artist,
                    reason = "曲库直接匹配",
                    result = it
                )
            },
            fallback = true
        )
    }

    private fun parseDraft(raw: String?): Pair<String, List<Draft>>? {
        if (raw.isNullOrBlank()) return null
        val text = raw.replace(FENCE, "").trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull() ?: return null
        val interpretation = obj.optString("interpretation").trim().take(40)
        val arr = obj.optJSONArray("songs") ?: return null
        val drafts = ArrayList<Draft>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val title = item.optString("title").trim().take(60)
            if (title.isBlank()) continue
            drafts += Draft(
                title = title,
                artist = item.optString("artist").trim().take(40),
                reason = item.optString("reason").trim().take(30)
            )
        }
        return if (drafts.isEmpty()) null else interpretation to drafts
    }

    private fun norm(value: String): String = value
        .lowercase(Locale.US)
        .replace(Regex("""[^\u4e00-\u9fa5a-z0-9]"""), "")

    private fun userPrompt(request: String): String =
        "用户想听：${request.take(300)}\n请推荐 3 首真实存在的歌，优先华语歌，兼顾曲风/场景。"

    private fun playlistPrompt(request: String, want: Int): String =
        "用户想要一份歌单：${request.take(300)}\n" +
            "请推荐 $want 首真实存在的歌，风格统一、贴合场景，优先华语歌；" +
            "不要全部选同一位歌手。reason 用一句话说明为什么适合这个场景。"

    private val SYSTEM = """
你是音乐策展人。根据用户的场景、心情、风格或参考歌曲，推荐真实存在的歌。
硬性规则：
- 只能推荐你确定存在的歌曲，宁缺毋滥；不确定就不要写。
- 不要编造歌名、歌手，不要推荐用户没提的付费/独家内容。
- 每首给一句具体理由（场景、情绪、曲风），不超过 20 字。
- 只输出一行 JSON，不要 markdown、不要解释：
{"interpretation":"你对需求的理解","summary":"一句话说明","songs":[{"title":"歌名","artist":"歌手","reason":"推荐理由"}]}
- songs 数量 3 首；用户明确只要一首时给 1 首。
    """.trimIndent()
}
