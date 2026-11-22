package com.example.my.data.music

/**
 * 歌曲故事的「先问模型」阶段。
 *
 * 旧流程每次都先搜网页，常见老歌明明模型很熟也要等一轮网络。
 * 现在先让模型凭已有知识写；只有它明确表示没听过 / 不确定时，
 * 才交给网页检索补资料。提示词要求首行输出 KNOWN / UNKNOWN，
 * 让调用方可以稳定区分「知道」和「不知道」，而不是去猜正文语气。
 */
object SongStoryDirect {

    private val HEADING = Regex("""(?m)^\s*(?:标题|歌曲|正文)\s*[:：]\s*""")
    private val MARKDOWN = Regex("""\*\*|__|#{1,6}\s*""")
    private val SOURCE_LINE = Regex("""(?m)^\s*(?:来源|参考来源|参考资料)\s*[:：].*$""")
    private val UNKNOWN_HINTS = listOf(
        "不了解", "没听过", "没有听过", "不清楚这首歌", "无法确定",
        "无法确认", "不确定这首歌", "抱歉"
    )

    /**
     * 直接生成提示词。
     *
     * [lyricsSnippet] 只放少量歌词，帮模型确认是哪首歌；没有歌词时传 null，
     * 让它只按歌名 + 歌手判断，避免把同名歌曲认错。
     */
    fun buildPrompt(title: String, artist: String, lyricsSnippet: String?): String {
        val lyrics = lyricsSnippet
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(300)
            .orEmpty()
        val lyricsBlock = if (lyrics.isNotBlank()) {
            "\n可参考的歌词摘录：\n$lyrics\n"
        } else {
            ""
        }
        return """
            你是熟悉华语流行音乐的音乐编辑。请介绍《$title》（$artist）这首歌：
            歌词讲了什么故事、创作背景、想表达的主题，写成 3-5 句、120-220 字。
            $lyricsBlock
            硬性要求：
            1. 只写你有把握的内容。如果你没听过这首歌，或不确定这就是你熟悉的同一首歌
               （尤其是新歌、翻唱、网络歌曲、同名歌曲），第一行只输出 UNKNOWN，不要猜、不要编。
            2. 确定了解时，第一行只输出 KNOWN，第二行开始写正文。
            3. 正文不要写来源、链接、标题、列表或 Markdown。
            4. 不要编造具体年份、奖项、销量、榜单位次；记不清的细节直接略过。
        """.trimIndent()
    }

    /**
     * 解析模型直答。
     *
     * 返回 null 表示「模型不了解 / 输出不可用」，调用方应进入联网检索；
     * 返回正文表示模型认识这首歌，可直接展示，不需要联网。
     */
    fun parse(raw: String?): String? {
        val text = SongStoryGrounding.stripThinking(raw)
            .replace(MARKDOWN, "")
            .replace(HEADING, "")
            .trim()
        if (text.isBlank()) return null

        val firstLine = text.lineSequence().firstOrNull()?.trim().orEmpty()
        val upper = firstLine.uppercase()
        if (upper.startsWith("UNKNOWN") || upper == "未知") return null

        // 有些模型不严格照格式输出，会用自然语言说“抱歉，我不了解这首歌”。
        // 只在开头 60 字里判断，避免正文里正常出现“抱歉”被误判。
        val opening = text.take(60)
        if (UNKNOWN_HINTS.any { opening.contains(it) }) return null

        var story = text
        if (upper.startsWith("KNOWN")) {
            story = firstLine.removePrefix("KNOWN").removePrefix("Known").removePrefix("known")
                .trim() + "\n" + text.lineSequence().drop(1).joinToString("\n").trim()
        }
        story = story.replace(HEADING, "").replace(SOURCE_LINE, "").trim()
        if (story.length < 40 || story.length > 420) return null
        return story
    }
}
