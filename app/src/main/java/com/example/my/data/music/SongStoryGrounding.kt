package com.example.my.data.music

/**
 * 歌曲故事的「网页依据」校验。
 *
 * 只让模型写得好没用：如果它没真正读网页，界面却挂着 5 条来源，就是虚假引用。
 * 这里要求模型在正文里逐字引用一段资料原句，并在末尾列出实际用到的资料编号；
 * 只有「引文能在对应网页摘要里原样找到」的答案才算通过，来源列表也只显示这些编号。
 */
object SongStoryGrounding {

    data class Material(
        val index: Int,
        val title: String,
        val site: String,
        val snippet: String,
        val url: String = ""
    )

    data class Grounded(
        val story: String,
        val cited: List<Int>,
        val quotes: List<String>
    )

    private val SOURCE_LINE = Regex("""(?m)^\s*(?:来源|参考来源)\s*[:：].*$""")
    private val QUOTE = Regex("""[「“]([^」”]{6,60})[」”]""")

    /**
     * 给云端模型/本地小模型的提示词。
     * [local] 为 true 时压缩资料条数和字数，避免超出小模型上下文。
     */
    fun buildPrompt(
        title: String,
        artist: String,
        materials: List<Material>,
        local: Boolean
    ): String {
        val picked = materials.take(if (local) 3 else 5)
        val body = picked.joinToString("\n") { m ->
            val snippet = m.snippet.replace(Regex("\\s+"), " ").trim()
                .take(if (local) 150 else 260)
            "【${m.index}】${m.title}｜${m.site}\n$snippet"
        }
        val length = if (local) "3-4 句，100-160 字" else "3-5 句，120-200 字"
        return """
            你是音乐资料编辑。只允许使用下面编号的网页资料，介绍《$title》（$artist）的创作背景、歌词主题和专辑信息。
            硬性要求：
            1. 正文必须是 $length；
            2. 正文中至少有一处用「」逐字引用资料里的连续原句（6-30 字），引号内不得改写、拼接或概括；
            3. 最后单独一行写：来源：1,2（只写你实际用到的资料编号，不要把没用的资料全列上）；
            4. 资料没写的内容不要补，查不到就写“公开资料未提到”；
            5. 不要输出标题、列表、编号、链接或 Markdown。

            网页资料：
            $body
        """.trimIndent()
    }

    /** 本地模型的返回可能是「\u0001思考\u0001正文」；只取正文，避免把思考当故事。 */
    fun stripThinking(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return ""
        val segs = text.split("\u0001", limit = 3)
        return when {
            segs.size >= 3 -> segs[2].trim()
            segs.size == 2 -> "" // 只有思考，没有正文
            else -> text
        }
    }

    /**
     * 校验并抽出正文、实际引用来源和引用原句。
     * 返回 null 表示「没有可验证的网页依据」，调用方应改用网页摘要兜底，
     * 不能把这次生成当成「AI 联网整理」展示。
     */
    fun parse(raw: String?, materials: List<Material>): Grounded? {
        val text = stripThinking(raw)
            .replace(Regex("""\*\*|__|#{1,6}\s*"""), "")
            .trim()
        if (text.isBlank() || materials.isEmpty()) return null

        val quoteMatches = QUOTE.findAll(text).map { it.groupValues[1].trim() }.toList()
        if (quoteMatches.isEmpty()) return null

        // 归一化只去掉空白和常见标点，保留文字本身；引文必须能在资料原文里找到。
        fun norm(s: String) = s.replace(Regex("""[\s，。！？、；：,.!?;:「」“”"'（）()\-—…]"""), "")
        val materialNorms = materials.associate { it.index to norm(it.snippet) }
        val matched = quoteMatches.mapNotNull { q ->
            val nq = norm(q)
            if (nq.length < 6) null
            else materials.firstOrNull { materialNorms[it.index]?.contains(nq) == true }?.index
        }.distinct()
        if (matched.isEmpty()) return null

        val numbers = SOURCE_LINE.findAll(text)
            .flatMap { m -> Regex("[0-9]+").findAll(m.value).map { it.value }.asSequence() }
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { n -> materials.any { it.index == n } }
            .distinct()
            .toList()
        // 只展示「正文里确实有原句引用」的来源；模型顺手列上的其它编号不展示，
        // 避免搜了 5 条就挂 5 条、实际只用了一条的虚假来源。
        val cited = numbers.filter { it in matched }.ifEmpty { matched }

        val story = text.replace(SOURCE_LINE, "").trim()
        if (story.isBlank() || story.length > 420) return null
        return Grounded(story, cited, quoteMatches)
    }
}
