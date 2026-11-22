package com.example.my.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一条长期记忆。
 *
 * 旧实现只存 `List<String>`，没有分类、没有来源、没有命中次数，也没法淘汰——
 * 15 条塞满之后新偏好就再也记不进来，用户也没法看见 AI 到底记住了什么。
 * 这里补上结构，让记忆变成"可累积、可纠正、可管理"的东西。
 *
 * [kind] 只有三类，注入 system 时分组呈现：模型能分清"用户是谁"和"用户喜欢什么"，
 * 回答时才不会把「常住重庆」当成「想去重庆」。
 */
data class MemItem(
    val id: Long,
    val text: String,
    val kind: String,
    val ts: Long,
    /** 被重复提及的次数：越常提的越重要，淘汰时优先保留 */
    val hits: Int = 1
) {
    /**
     * 规范化文本：只留文字与数字，并剔掉「在/的/了/是/和/与」这类虚词。
     * 否则「常住重庆」和「常住在重庆」会因为中间多一个字而判定不成同一件事，
     * 同一句话被反复提及就会攒出好几条近似记忆，把上限挤满。
     */
    val norm: String get() = text.lowercase().filter { it.isLetterOrDigit() && it !in "在的了是和与" }
}

object AiMemory {

    const val KIND_PREF = "偏好"
    const val KIND_FACT = "事实"
    const val KIND_HABIT = "习惯"

    /** 上限：再多就是挤占上下文，反而让模型抓不住重点 */
    const val MAX = 40

    /** 单条记忆字数上限，太长会变成一整句话，失去"事实"的形态 */
    private const val MAX_LEN = 30

    private val KIND_ORDER = listOf(KIND_FACT, KIND_PREF, KIND_HABIT)

    // ------------------------------------------------------------------ 解析

    /**
     * 从助手回复里抓 `[记忆] …`。模型可以写成 `[记忆][偏好] 常住重庆`，
     * 不带标签时按偏好处理（这是最常见的一类）。
     */
    fun fromReply(reply: String, now: Long = System.currentTimeMillis()): List<MemItem> {
        if (reply.isBlank()) return emptyList()
        return reply.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("[记忆]") }
            .mapNotNull { line ->
                val body = line.removePrefix("[记忆]").trim()
                if (body.isBlank()) return@mapNotNull null
                val kind = when {
                    body.startsWith("[事实]") -> KIND_FACT
                    body.startsWith("[习惯]") -> KIND_HABIT
                    body.startsWith("[偏好]") -> KIND_PREF
                    else -> KIND_PREF
                }
                val text = body.replace(Regex("^\\[(偏好|事实|习惯)]"), "").trim().take(MAX_LEN)
                if (text.isBlank()) null
                else MemItem(now * 1000 + (body.hashCode() and 0xFFF).toLong(), text, kind, now)
            }
            .toList()
    }

    /**
     * 本地规则抽取：即使用户用的模型不吐 `[记忆]` 标记（自定义网关、端侧小模型都不会），
     * 也能从用户自己的话里认出长期有效的事实。
     *
     * 只抽"陈述句里的长期信息"。问句一律不抽——「我住在这里吗」不是一句自我介绍。
     */
    fun extractLocal(userText: String, now: Long = System.currentTimeMillis()): List<MemItem> {
        val raw = userText.replace("\n", "。").trim()
        if (raw.length < 4 || isQuestion(raw)) return emptyList()
        // 疑问/假设语气即便不带问号也不是事实陈述
        if (listOf("如果", "要是", "假如", "是不是", "有没有", "会不会").any { raw.contains(it) }) {
            return emptyList()
        }

        val hitsText = mutableListOf<Pair<String, String>>() // (文本, 类型)
        fun grab(re: Regex, kind: String, render: (String) -> String) {
            re.find(raw)?.groupValues?.getOrNull(1)?.let { v ->
                val clean = polish(v)
                if (clean.length in 2..MAX_LEN) hitsText += render(clean) to kind
            }
        }

        // 常住地 / 老家：最稳定的一条用户画像。
        // 注意这里不能写裸的「在」——「我在大理玩了三天」会被抽成「常在大理玩」，
        // 把一次旅行记成常住地，是最难被用户发现的那种错。
        val place = Regex("(?:我|本人)(?:现在)?(?:常住在|常住|住在|家在|定居在|老家在)([^，。！？、,.!?\\s]{2,12})")
            .find(raw)?.groupValues?.getOrNull(1)?.let { polish(it) }
        if (place != null && place.length in 2..12) hitsText += "常住$place" to KIND_FACT

        grab(Regex("(?:我|本人)(?:比较|很|最|特别|超)?(?:喜欢|偏爱|爱去|爱看|钟情于)([^，。！？、,.!?\\s]{2,14})"), KIND_PREF) { "喜欢$it" }
        grab(Regex("(?:我|本人)(?:一般|通常|习惯|每次|总是)([^，。！？、,.!?\\s]{2,16})"), KIND_HABIT) { it }
        grab(Regex("(?:我|本人)(?:不吃|不能吃|忌口|吃不惯)([^，。！？、,.!?\\s]{1,10})"), KIND_PREF) { "不吃$it" }
        grab(Regex("(?:我|本人)(?:的)?预算(?:大概|大约|差不多|在)?([0-9一二三四五六七八九十百千万]{1,8}\\s*(?:元|块|万|千)?)"), KIND_FACT) { "预算$it" }
        grab(Regex("(?:我|我们)(?:一般|通常|经常)?(?:和|跟)(朋友|家人|对象|爱人|孩子|同事|父母|同学)(?:一起)?(?:去|出去)?(?:旅行|旅游|玩)"), KIND_HABIT) { "常和${it}一起出行" }

        val stamp = now * 1000
        // 「我通常和朋友一起去旅行」会被两条规则同时命中：「通常…」抽成「和朋友一起去旅行」、
        // 「和X…旅行」抽成「常和朋友一起出行」。语义重复，只留表达更完整的那条。
        val companion = hitsText.any { it.first.startsWith("常和") }
        return hitsText
            .filterNot { companion && !it.first.startsWith("常和") && it.first.contains("旅行") }
            .distinctBy { it.first.filter { c -> c.isLetterOrDigit() } }
            .mapIndexed { i, (text, kind) -> MemItem(stamp + i, text.take(MAX_LEN), kind, now) }
    }

    /** 去掉尾部语气词/助词，避免存成「重庆的」「自驾游了」这种半截话 */
    private fun polish(raw: String): String =
        raw.trim().trimEnd('的', '了', '吧', '啊', '呢', '哦', '呀').trim()

    private fun isQuestion(t: String): Boolean {
        val s = t.trim()
        return s.endsWith("吗") || s.endsWith("?") || s.endsWith("？") ||
            s.endsWith("呢") || s.contains("什么") || s.contains("怎么") || s.contains("哪些")
    }

    // ------------------------------------------------------------------ 合并

    /** 判断两条记忆是否指向同一件事：完全相等，或短的那条被长的那条完整包含 */
    private fun same(a: MemItem, b: MemItem): Boolean {
        val x = a.norm
        val y = b.norm
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val short = if (x.length <= y.length) x else y
        val long = if (x.length <= y.length) y else x
        return short.length >= 4 && long.contains(short) && short.length * 10 >= long.length * 6
    }

    /**
     * 合并新旧记忆：重复的累加命中次数并保留信息更全的那条，超上限按「命中次数 → 新鲜度」淘汰。
     * 用命中次数而不是纯时间排序，是为了让"我常住重庆"这种反复出现的事实不被新的一次性信息挤掉。
     */
    fun merge(old: List<MemItem>, new: List<MemItem>): List<MemItem> {
        val out = old.toMutableList()
        for (n in new) {
            if (n.text.isBlank()) continue
            val idx = out.indexOfFirst { same(it, n) }
            if (idx >= 0) {
                val cur = out[idx]
                val longer = n.text.length > cur.text.length
                out[idx] = cur.copy(
                    text = if (longer) n.text else cur.text,
                    kind = if (longer) n.kind else cur.kind,
                    hits = cur.hits + 1,
                    ts = maxOf(cur.ts, n.ts)
                )
            } else {
                out += n
            }
        }
        return out.sortedWith(
            compareByDescending<MemItem> { it.hits }.thenByDescending { it.ts }
        ).take(MAX)
    }

    /** 手动添加一条（用户在记忆管理里自己填的）：走一遍去重，避免重复 */
    fun add(items: List<MemItem>, text: String, kind: String = KIND_PREF): List<MemItem> {
        val t = text.trim().take(MAX_LEN)
        if (t.isBlank()) return items
        val now = System.currentTimeMillis()
        return merge(items, listOf(MemItem(now * 1000, t, kind, now)))
    }

    fun remove(items: List<MemItem>, id: Long): List<MemItem> = items.filterNot { it.id == id }

    // ------------------------------------------------------------------ 渲染

    /** 注入 system 的记忆块：分组 + 明确的"这是长期记忆、自然沿用、别复述" */
    fun render(items: List<MemItem>): String {
        if (items.isEmpty()) return ""
        val groups = items.groupBy { it.kind }
        return buildString {
            appendLine("【我记住的关于你的事】（跨会话长期记忆）")
            appendLine("用法：回答时自然沿用这些信息（比如知道用户常住城市就不必再问），不要复述这份清单，也不要向用户提起「记忆」这个词。")
            KIND_ORDER.forEach { k ->
                val list = groups[k].orEmpty()
                if (list.isNotEmpty()) {
                    appendLine("$k：")
                    list.forEach { appendLine("- ${it.text}") }
                }
            }
            groups.filterKeys { it !in KIND_ORDER }.forEach { (k, v) ->
                appendLine("$k：")
                v.forEach { appendLine("- ${it.text}") }
            }
        }.trim()
    }

    // ------------------------------------------------------------------ 存储

    fun toJson(items: List<MemItem>): String = JSONArray().apply {
        items.forEach { m ->
            put(
                JSONObject().apply {
                    put("id", m.id)
                    put("text", m.text)
                    put("kind", m.kind)
                    put("ts", m.ts)
                    put("hits", m.hits)
                }
            )
        }
    }.toString()

    fun fromJson(raw: String?): List<MemItem> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val text = o.optString("text").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MemItem(
                id = o.optLong("id", i.toLong()),
                text = text,
                kind = o.optString("kind").ifBlank { KIND_PREF },
                ts = o.optLong("ts", 0L),
                hits = o.optInt("hits", 1).coerceAtLeast(1)
            )
        }
    }

    /** 兼容旧数据：v1 存的是一串纯文本，全部按"偏好"迁移，不丢用户已有的记忆 */
    fun fromLegacy(list: List<String>): List<MemItem> =
        list.filter { it.isNotBlank() }
            .mapIndexed { i, s -> MemItem(-(i + 1).toLong(), s.take(MAX_LEN), KIND_PREF, 0L) }
}
