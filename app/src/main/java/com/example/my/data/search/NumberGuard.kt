package com.example.my.data.search

import kotlin.math.abs

/**
 * 数字守门员（生成后校验）。
 *
 * 弱模型最常见的幻觉不是"整段胡说"，而是**把记忆里的旧价格/旧温度混进一段结构正确的回答**。
 * 生成前的事实卡能压住大部分，但压不住全部，所以在回答落定后做一次机械校验：
 *  - 回答里每个"带单位的具体数字"（元/℃/%/公里/小时/评分/折…）都必须在证据里出现过；
 *  - 引用编号 [n] 必须在本次来源表范围内。
 *
 * 刻意只校验带单位的数字：Day 1、第 3 天、2 天 1 晚属于行程结构，不算事实断言，
 * 全查会把正常攻略误伤成"幻觉"。校验结果不偷偷改用户的回答，而是追加一条诚实的提示。
 */
object NumberGuard {

    data class Report(
        val unsupported: List<String> = emptyList(),
        val badCitations: List<Int> = emptyList(),
        val cited: Int = 0,
        val numeric: Int = 0
    ) {
        val ok: Boolean get() = unsupported.isEmpty() && badCitations.isEmpty()

        /** UI 用的一行提示；没问题时返回空串，不打扰用户 */
        fun note(): String {
            if (ok) return ""
            val parts = mutableListOf<String>()
            if (unsupported.isNotEmpty()) {
                parts += "这些数字本次来源里没有出现：" + unsupported.take(4).joinToString("、") +
                    "（可能是模型推断，请以来源为准）"
            }
            if (badCitations.isNotEmpty()) {
                parts += "引用编号 " + badCitations.take(3).joinToString("、") { "[$it]" } +
                    " 不在本次来源列表中"
            }
            return "（数据校验：" + parts.joinToString("；") + "）"
        }
    }

    /** 只校验"事实型数字"：价格、温度、比例、里程、时长、评分、折扣 */
    private val CHECK_UNITS = listOf(
        "元", "块", "￥", "¥", "美元", "℃", "度", "%", "折", "公里", "km", "米",
        "小时", "分钟", "分", "张", "间", "人", "岁", "次"
    )

    private val NUM_WITH_UNIT = Regex(
        "(\\d+(?:[.,]\\d+)?)\\s*(" + CHECK_UNITS.joinToString("|") + ")"
    )

    fun audit(answer: String, deck: SeaDeck): Report {
        if (answer.isBlank()) return Report()
        val allowedRaw = HashSet<String>()
        val allowedValues = mutableListOf<Pair<Double, String>>()
        for (e in deck.evidence) {
            collect(e.text, allowedRaw, allowedValues)
        }
        deck.claims.forEach { c ->
            collect(c.statement + " " + c.counterStatement, allowedRaw, allowedValues)
        }

        // 去掉引用标记后再抽数字，避免 [12] 被当成数值
        val body = answer.replace(Regex("\\[\\d+]"), " ")
        val bad = mutableListOf<String>()
        var total = 0
        NUM_WITH_UNIT.findAll(body).forEach { m ->
            val raw = m.groupValues[1].replace(",", "")
            val unit = m.groupValues[2]
            val v = raw.toDoubleOrNull() ?: return@forEach
            total++
            // 年份不算事实数字（"2026 元"这种才是价格）
            if (unit == "度" && v in 1900.0..2100.0) return@forEach
            val hit = allowedRaw.any { it.replace(",", "") == raw } ||
                allowedValues.any { (av, au) ->
                    au == unit && abs(av - v) <= maxOf(0.02, 0.02 * abs(v))
                }
            if (!hit) bad += m.value.trim()
        }

        val cites = Regex("\\[(\\d+)]").findAll(answer)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toList()
        val maxId = deck.evidence.size
        val badCites = cites.filter { it <= 0 || it > maxId }.distinct()
        return Report(
            unsupported = bad.distinct().take(6),
            badCitations = badCites,
            cited = cites.distinct().size,
            numeric = total
        )
    }

    private fun collect(text: String, raw: MutableSet<String>, values: MutableList<Pair<Double, String>>) {
        NUM_WITH_UNIT.findAll(text).forEach { m ->
            raw += m.groupValues[1]
            m.groupValues[1].replace(",", "").toDoubleOrNull()?.let { values += it to m.groupValues[2] }
        }
    }
}
