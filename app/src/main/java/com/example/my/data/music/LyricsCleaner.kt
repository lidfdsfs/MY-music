package com.example.my.data.music

/**
 * 网页歌词清洗：把一整页 HTML 洗成「只有歌词正文」。
 *
 * 搜索引擎给出的歌词页从来不是干净的：上有导航、下载按钮、版权声明，下有「相关推荐」
 * 里别人歌的句子，正文还夹着作词/作曲/编曲一类制作信息。这里做三件事：
 *  1. 去标签、还原 <br> 换行、解码 HTML 实体；
 *  2. 按行分类，只保留「像歌词」的行（长度、中文占比、无站点噪声词）；
 *  3. 把连续成段的歌词块挑出来（至少 6 行），并校验页面确实提到了这首歌的歌名，
 *     避免把同名二创或别的歌端给用户。
 */
object LyricsCleaner {

    private const val MIN_LINES = 6
    private const val MAX_LINE_CHARS = 40

    private val SCRIPT = Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)
    private val STYLE = Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)
    private val NOSCRIPT = Regex("<noscript[\\s\\S]*?</noscript>", RegexOption.IGNORE_CASE)
    private val BREAK = Regex("<br\\s*/?\\s*>", RegexOption.IGNORE_CASE)
    private val BLOCK_END = Regex(
        "</?(?:p|div|li|tr|td|h[1-6]|section|article|span)\\s*[^>]*>", RegexOption.IGNORE_CASE
    )
    private val TAG = Regex("<[^>]+>")
    private val TITLE_TAG = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
    private val H1_TAG = Regex("<h[12][^>]*>([\\s\\S]*?)</h[12]>", RegexOption.IGNORE_CASE)
    private val ENTITY = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")

    /** 制作信息、站点功能、推荐位：这些行即使很短也不能进歌词 */
    private val META_LINE = Regex(
        "^(作词|作曲|编曲|词|曲|演唱|原唱|和声|和声编写|制作人|监制|出品人|出品|发行|混音|录音|母带|" +
            "吉他|贝斯|鼓|弦乐|钢琴|统筹|策划|文案|版权|OP|SP|Lyrics?|Composer|Arranger|Producer|" +
            "Vocal|Mixed\\s*by|Mastered\\s*by)\\s*[:：]",
        RegexOption.IGNORE_CASE
    )
    private val NOISE_WORDS = listOf(
        "歌词", "下载", "播放", "收藏", "分享", "评论", "登录", "注册", "首页", "上一首", "下一首",
        "相关推荐", "猜你喜欢", "热门", "查看更多", "版权", "免责", "联系我们", "关于我们",
        "版权所有", "http", "www.", ".com", ".cn", "广告", "举报", "扫码", "关注", "订阅",
        "歌单", "专辑介绍", "歌手：", "专辑：", "未经许可", "侵权", "转载",
        "歌词解读", "建议与反馈", "歌词收录", "ICP", "备案"
    )
    private val URL_LIKE = Regex("https?://|www\\.|@[A-Za-z0-9._-]+")
    /** 页脚/站点功能行：百度、歌词站、聚合站都用同一套模板，单独拦比堆关键词更稳 */
    private val FOOTER_LINE = Regex(
        "^(?:返回顶部|回到顶部|上一篇|下一篇|相关阅读|更多精彩|友情链接|网站地图|" +
            "免责声明|版权声明|特别声明|Copyright|©|Powered\\s*by|技术支持|" +
            "电话|地址\\s*[:：]|扫码|下载\\s*APP|打开\\s*APP|关注公众号|微信公众号|" +
            "ICP|备案号|沪ICP|京ICP|粤ICP)",
        RegexOption.IGNORE_CASE
    )
    private val JAIL_ONLY = Regex("^[\\s·•\\-—_.…。,:：;；/\\\\|*#=~`'\"()（）【】]+$")
    private val ENGLISH_LINE = Regex("^[A-Za-z0-9 ,.'’!?()\\-/:;]+$")

    /** 纯文本入口：给已经取下来的段落用（测试、或页面本身是 txt） */
    fun pick(raw: String, title: String): String? {
        val text = normalizeNewlines(raw)
        val squashed = squash(text)
        if (title.isNotBlank() && !squashed.contains(squash(title))) return null
        return pickBlock(text)
    }

    /** HTML 入口：洗标签 + 找歌词块 */
    fun extract(html: String, title: String, artist: String): String? {
        val text = htmlToText(html)
        // 只看 <title>/<h1> 和正文开头：整页随便搜歌名会被「相关推荐」里的歌名骗过去，
        // 之前就是这么把别人的歌词当成这首端出去的
        val headText = buildString {
            TITLE_TAG.find(html)?.let { append(it.groupValues[1]) }
            append(' ')
            H1_TAG.find(html)?.let { append(it.groupValues[1]) }
        }.takeIf { it.isNotBlank() }?.let { squash(htmlToText(it)) }
            ?: squash(text.lines().take(3).joinToString(" "))
        if (title.isNotBlank() && !headText.contains(squash(title))) return null
        // 歌手名用来二次确认；页面里没有就当是纯歌名页，放行
        if (artist.isNotBlank()) {
            val artistOk = squash(text).contains(squash(artist))
            if (!artistOk && title.length < 4) return null
        }
        val block = pickBlock(text) ?: return null
        // 歌名 / 歌手名常作为标题单独占一行，混在歌词最前面；它们不是歌词，去掉
        val t = squash(title)
        val a = squash(artist)
        val trimmed = block.lines().dropWhile { line ->
            val s = squash(line)
            s.isEmpty() || s == t || (a.isNotEmpty() && s == a)
        }
        if (trimmed.size < MIN_LINES) return null
        return trimmed.joinToString("\n")
    }

    // ---------- HTML → 文本 ----------

    fun htmlToText(html: String): String {
        var s = html
        s = SCRIPT.replace(s, "\n")
        s = STYLE.replace(s, "\n")
        s = NOSCRIPT.replace(s, "\n")
        s = BREAK.replace(s, "\n")
        s = BLOCK_END.replace(s, "\n")
        s = TAG.replace(s, "")
        s = decodeEntities(s)
        return normalizeNewlines(s)
    }

    private fun decodeEntities(s: String): String {
        val named = mapOf(
            "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
            "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’",
            "hellip" to "…", "middot" to "·", "mdash" to "—", "ndash" to "–", "times" to "×",
            "copy" to "©", "reg" to "®", "ensp" to " ", "emsp" to " ", "thinsp" to " "
        )
        return ENTITY.replace(s) { m ->
            val body = m.groupValues[1]
            if (body.startsWith("#")) {
                val code = if (body.startsWith("#x", true)) {
                    body.substring(2).toIntOrNull(16)
                } else {
                    body.substring(1).toIntOrNull()
                }
                code?.takeIf { it in 1..0x10FFFF }?.let { runCatching { String(Character.toChars(it)) }.getOrNull() }
                    ?: " "
            } else {
                named[body.lowercase()] ?: m.value
            }
        }
    }

    private fun normalizeNewlines(s: String): String = s
        .replace("\r\n", "\n").replace('\r', '\n').replace('\u00A0', ' ')
        .lines()
        .joinToString("\n") { it.replace(Regex("[\\t\\u000B\\f\\u2000-\\u200B]"), " ").trim() }

    private fun squash(s: String): String = s.replace(Regex("[\\s\\u00A0]+"), "").lowercase()

    // ---------- 找歌词块 ----------

    private fun pickBlock(text: String): String? {
        val lines = text.split('\n')
        var best: List<String>? = null
        var bestScore = 0

        var run = mutableListOf<String>()
        var blanks = 0

        fun close() {
            val body = trimBlanks(run)
            run = mutableListOf()
            blanks = 0
            if (body.size < MIN_LINES) return
            val distinct = body.distinct().size
            val avg = body.sumOf { it.length } / body.size
            val score = body.size * 2 + distinct + if (avg in 4..18) 8 else 0
            if (score > bestScore) {
                bestScore = score
                best = body
            }
        }

        lines.forEach { line ->
            if (line.isBlank()) {
                if (run.isNotEmpty()) blanks++
                if (blanks <= 2) run += ""
                return@forEach
            }
            if (isLyricLine(line)) {
                blanks = 0
                run += line
            } else {
                // 遇到导航空行之外的内容就截断：正文之外的站点元素不能糊进歌词
                if (run.isNotEmpty()) close()
            }
        }
        if (run.isNotEmpty()) close()

        val block = best ?: return null
        val deduped = block.filterNot { it.isBlank() }
            .fold(mutableListOf<String>()) { acc, s ->
                if (acc.lastOrNull() != s) acc += s
                acc
            }
        if (deduped.size < MIN_LINES) return null
        return deduped.joinToString("\n")
    }

    private fun trimBlanks(list: List<String>): List<String> {
        var start = 0
        var end = list.size
        while (start < end && list[start].isBlank()) start++
        while (end > start && list[end - 1].isBlank()) end--
        return list.subList(start, end)
    }

    fun isLyricLine(raw: String): Boolean {
        val t = raw.trim()
        if (t.length !in 2..MAX_LINE_CHARS) return false
        if (JAIL_ONLY.matches(t)) return false
        if (META_LINE.containsMatchIn(t)) return false
        if (FOOTER_LINE.containsMatchIn(t)) return false
        if (URL_LIKE.containsMatchIn(t)) return false
        val lower = t.lowercase()
        if (NOISE_WORDS.any { lower.contains(it.lowercase()) }) return false
        if (t.endsWith("：") || t.endsWith(":")) return false
        // 长篇散文（带句号、超过 20 字）不是歌词
        if (t.length > 20 && (t.contains("。") || t.contains("，"))) return false
        val chinese = t.count { it.code in 0x4E00..0x9FFF }
        if (chinese >= 2) return chinese.toFloat() / t.length >= 0.25f
        // 外语歌词：字母行，限制词数，避免把整段英文介绍吞进来
        if (ENGLISH_LINE.matches(t)) return t.split(' ').size <= 12
        return false
    }
}
