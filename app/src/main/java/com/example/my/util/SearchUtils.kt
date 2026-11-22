package com.example.my.util

import android.icu.text.Transliterator
import java.text.Normalizer
import java.util.Locale

/**
 * 轻量模糊搜索工具（零第三方依赖）。
 *
 * 能力：
 * 1. 原文包含匹配（大小写不敏感）
 * 2. 拼音全拼 / 首字母匹配：输入 "chongqing"、"cq" 可命中「重庆」
 *    —— 使用 Android ICU 内置 Han-Latin 转换器（API 24+），无需引入 TinyPinyin
 * 3. 多音字地名修正：ICU 对「重庆」输出 zhongqing，这里用地名字典补上 chongqing
 * 4. 编辑距离容错：输入 "chongqin"（少一个字母）也能命中
 * 5. 相关性打分：完全包含 > 前缀 > 拼音 > 容错，用于搜索结果排序
 */
object SearchUtils {

    private val transliterator: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Han-Latin; Latin-ASCII") }.getOrNull()
            ?: runCatching { Transliterator.getInstance("Han-Latin") }.getOrNull()
    }

    /** 常见多音字地名的正确读音（ICU 无法按语境判断） */
    private val placePinyin = mapOf(
        "重庆" to "chongqing", "长沙" to "changsha", "厦门" to "xiamen",
        "贵阳" to "guiyang", "六安" to "luan", "蚌埠" to "bengbu",
        "亳州" to "bozhou", "乐山" to "leshan", "番禺" to "panyu",
        "大栅栏" to "dashilan", "拉萨" to "lasa", "都江堰" to "dujiangyan",
        "犍为" to "qianwei", "阆中" to "langzhong", "婺源" to "wuyuan",
        "歙县" to "shexian", "西双版纳" to "xishuangbanna", "呼伦贝尔" to "hulunbeier",
        "台州" to "taizhou", "丽水" to "lishui"
    )

    private val pinyinCache = HashMap<String, String>()

    @Synchronized
    private fun pinyinOf(text: String): String {
        pinyinCache[text]?.let { return it }
        val t = transliterator
        val result = if (t == null) "" else runCatching {
            val raw = t.transliterate(text)
            // 去除声调（NFD 分解后丢弃组合字符）
            val nfd = Normalizer.normalize(raw, Normalizer.Form.NFD)
            buildString {
                for (c in nfd) {
                    if (c.isLetterOrDigit()) append(c.toString().lowercase(Locale.ROOT))
                }
            }
        }.getOrElse { "" }
        if (pinyinCache.size > 800) pinyinCache.clear()
        pinyinCache[text] = result
        return result
    }

    /** 文本的拼音候选（ICU 结果 + 多音字地名修正结果） */
    private fun pinyinVariants(text: String): List<String> {
        val base = pinyinOf(text)
        if (placePinyin.isEmpty() || text.length > 120) {
            return listOfNotNull(base.ifEmpty { null })
        }
        val extra = placePinyin.filterKeys { text.contains(it) }.values
        if (extra.isEmpty()) return listOfNotNull(base.ifEmpty { null })
        return (listOf(base) + extra).filter { it.isNotEmpty() }.distinct()
    }

    /** 首字母缩写：成都游记 → cdyj；重庆 → cq（走地名修正） */
    private fun initialsOf(text: String): String = buildString {
        text.forEach { ch ->
            if (ch.code > 127) {
                val p = pinyinOf(ch.toString())
                if (p.isNotEmpty()) append(p.first())
            } else if (ch.isLetterOrDigit()) {
                append(ch.toString().lowercase(Locale.ROOT))
            }
        }
        // 多音字地名首字母（ICU 给 zhongqing → zq，这里补上 cq）
        placePinyin.forEach { (name, py) ->
            if (text.contains(name)) append(py.first())
        }
    }

    /** 限制最大距离的编辑距离（超限提前返回 max+1） */
    private fun editDistance(a: String, b: String, max: Int): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > max) return max + 1
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }

    /** 拼音串滑窗容错（query 比目标短时） */
    private fun windowDistance(query: String, target: String, max: Int): Int {
        if (target.length < query.length) return editDistance(query, target, max)
        var best = Int.MAX_VALUE
        val win = query.length
        var i = 0
        while (i + win <= target.length) {
            best = minOf(best, editDistance(query, target.substring(i, i + win), max))
            if (best == 0) return 0
            i++
        }
        return best
    }

    /**
     * 相关性打分（越大越相关，0 表示不命中）。
     * text 可传入多个字段拼接后的搜索域。
     */
    fun score(query: String, text: String): Int {
        val q = query.trim()
        if (q.isEmpty()) return 1
        if (text.isBlank()) return 0
        val lower = text.lowercase(Locale.ROOT)
        val ql = q.lowercase(Locale.ROOT)

        // 1) 原文包含
        if (lower.contains(ql)) return if (lower.startsWith(ql)) 120 else 100

        val qVariants = pinyinVariants(q)
        val tVariants = pinyinVariants(text)
        if (qVariants.isNotEmpty() && tVariants.isNotEmpty()) {
            for (qv in qVariants) {
                for (tv in tVariants) {
                    if (qv.length >= 2 && tv.contains(qv)) return if (tv.startsWith(qv)) 90 else 80
                    if (qv.length in 2..6 && initialsOf(text).contains(qv)) return 70
                    if (qv.length >= 3) {
                        val maxDist = if (qv.length >= 6) 2 else 1
                        if (windowDistance(qv, tv, maxDist) <= maxDist) return 50
                    }
                }
            }
        }
        // 2) 原文容错（少数错字，如「大里」→「大理」）
        if (q.length in 2..14) {
            val maxDist = if (q.length >= 6) 2 else 1
            if (editDistance(ql, lower, maxDist) <= maxDist) return 40
        }
        return 0
    }

    /** 是否命中 */
    fun matches(query: String, text: String): Boolean = score(query, text) > 0
}
