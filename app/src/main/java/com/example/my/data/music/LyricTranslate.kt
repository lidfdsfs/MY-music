package com.example.my.data.music

import com.google.gson.JsonParser

/**
 * AI 歌词翻译的输入/输出协议。
 *
 * 模型只负责翻译，不负责对齐：输入多少行就要求输出多少行，解析时严格校验行数，
 * 少一行或多一行都判失败，避免把整首歌词错位贴到时间轴上。
 */
object LyricTranslate {

    val SYSTEM_PROMPT = """
        你是歌词翻译。把用户给出的歌词逐行翻译成自然、口语化的英文。
        要求：
        1. 严格保留行数与顺序，一行原文对应一行译文，不合并、不拆分、不跳过空行；
        2. 只输出 JSON 字符串数组，不要输出编号、解释、Markdown 代码块或多余文字；
        3. 译文简洁，适合作为歌词字幕显示，不要写成长段解释。
    """.trimIndent()

    /** 把歌词行编码成模型输入；JSON 数组能明确行边界。 */
    fun input(lines: List<String>): String {
        val arr = com.google.gson.JsonArray()
        lines.forEach { arr.add(it) }
        return arr.toString()
    }

    /**
     * 解析模型返回。允许模型误加 ```json 围栏，但拒绝行数不一致、空行和纯占位结果。
     */
    fun parse(raw: String?, expected: Int): List<String>? {
        if (raw.isNullOrBlank() || expected <= 0) return null
        val text = raw.trim()
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        val json = text.substring(start, end + 1)
        return runCatching {
            val arr = JsonParser.parseString(json).asJsonArray
            if (arr.size() != expected) return@runCatching null
            val out = ArrayList<String>(expected)
            for (i in 0 until expected) {
                val item = arr.get(i)
                if (!item.isJsonPrimitive || !item.asJsonPrimitive.isString) return@runCatching null
                val line = item.asString.trim()
                if (line.isBlank()) return@runCatching null
                out.add(line)
            }
            out
        }.getOrNull()
    }
}
