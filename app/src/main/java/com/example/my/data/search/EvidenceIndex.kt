package com.example.my.data.search

import com.example.my.data.WebHit

/**
 * 一条可检索的临时证据。
 *
 * 它不是网页原文，而是清洗/裁决后的原子事实或来源摘要。写入临时 JSONL 后，
 * 行程/问答链路按需求槽位取用，模型不再一次性吞下整批网页。
 */
data class EvidenceRecord(
    val id: String,
    /** 门票/交通/景点/美食等一级分类，用于槽位检索 */
    val category: String,
    /** 可以直接给模型的事实句或压缩摘要 */
    val fact: String,
    /** 高置信/中置信/分歧/证据不足/来源摘要 */
    val status: String,
    val title: String = "",
    val url: String = "",
    val site: String = "",
    /** 1=官方/一手，2=主流/OTA，3=社区，4=低质 */
    val tier: Int = 3,
    val published: String = "",
    /** 引用的来源编号，和 SeaFusion.sources 的顺序一致 */
    val sourceIds: List<Int> = emptyList(),
    /** 分歧时另一方的话术 */
    val counter: String = "",
    val query: String = ""
)

/**
 * 证据分类 + 槽位检索。纯 Kotlin，不依赖 Android，单测可直接跑。
 *
 * 关键设计：不是「按关键词全库搜索」，而是行程先列出它需要的槽位，
 * 每个槽位最多取几条最可信的证据。这样 5 天行程不会因为「重庆」一个词
 * 把 30 条网页全捞回来，也不会漏掉「车程/预约」这种单看主问句搜不到的信息。
 */
object EvidenceIndex {

    enum class Category(val label: String, val cues: List<String>) {
        TICKET(
            "门票与预约",
            listOf(
                "门票", "票价", "价格", "费用", "多少钱", "免费", "优惠", "半价",
                "预约", "预定", "购票", "实名", "限流"
            )
        ),
        OPEN(
            "开放时间",
            listOf("开放", "营业", "闭馆", "闭园", "入场", "停止入", "几点", "周一", "节假日")
        ),
        TRANSPORT(
            "交通与车程",
            listOf(
                "交通", "高铁", "火车", "机场", "车站", "车程", "公里", "自驾",
                "公交", "地铁", "打车", "包车", "班车", "步行", "分钟"
            )
        ),
        ROUTE(
            "景点与路线",
            listOf(
                "景点", "景区", "古镇", "路线", "动线", "游玩", "观景", "打卡",
                "必去", "推荐", "一日游", "两日游", "顺序", "入口", "索道"
            )
        ),
        FOOD(
            "美食",
            listOf("美食", "小吃", "餐厅", "火锅", "烧烤", "菜", "早餐", "夜市", "好吃")
        ),
        STAY(
            "住宿",
            listOf("住宿", "酒店", "民宿", "客栈", "房间", "住哪", "订房")
        ),
        AVOID(
            "避坑与排队",
            listOf("避坑", "注意", "排队", "人多", "拥挤", "建议", "不要", "谨慎", "踩雷", "坑")
        ),
        WEATHER(
            "天气与时机",
            listOf("天气", "气温", "下雨", "降雨", "晴天", "温度", "季节", "雨季", "淡季", "旺季")
        ),
        OTHER("其他背景", emptyList())
    }

    data class Slot(val label: String, val category: Category, val cues: List<String>)

    data class Hit(val slot: String, val record: EvidenceRecord)

    private val DATE_KEYS = listOf("2026", "2025", "2024", "2023", "2022", "2021", "2020")

    fun categoryOf(text: String): Category {
        val t = text.lowercase()
        // 先匹配强特征：门票/交通/天气这类，避免「景区门票」被判成泛景点
        Category.entries
            .filter { it != Category.OTHER }
            .map { it to it.cues.count { c -> t.contains(c.lowercase()) } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.let { return it.first }
        return Category.OTHER
    }

    /** 行程固定要覆盖的槽位；偏好词再追加一个重点槽位 */
    fun tripSlots(days: Int, preference: String = ""): List<Slot> {
        val base = listOf(
            Slot("门票与预约", Category.TICKET, Category.TICKET.cues),
            Slot("开放时间", Category.OPEN, Category.OPEN.cues),
            Slot("交通与车程", Category.TRANSPORT, Category.TRANSPORT.cues),
            Slot("景点与路线", Category.ROUTE, Category.ROUTE.cues),
            Slot("美食", Category.FOOD, Category.FOOD.cues),
            Slot("住宿", Category.STAY, Category.STAY.cues),
            Slot("避坑与排队", Category.AVOID, Category.AVOID.cues),
            Slot("天气与时机", Category.WEATHER, Category.WEATHER.cues)
        )
        val prefCues = when {
            preference.contains("自然") || preference.contains("风光") ->
                listOf("山", "湖", "峡谷", "森林", "瀑布", "草原", "溪", "湿地", "自然")
            preference.contains("人文") || preference.contains("历史") ->
                listOf("古镇", "博物馆", "古城", "遗址", "文化", "历史", "寺", "故居")
            preference.contains("美食") ->
                listOf("美食", "小吃", "餐厅", "火锅", "夜市")
            preference.contains("亲子") ->
                listOf("亲子", "儿童", "乐园", "动物园", "适合孩子")
            preference.contains("慢") ->
                listOf("慢", "休闲", "不赶", "轻松", "半日")
            else -> emptyList()
        }
        return if (prefCues.isEmpty()) base
        else base + Slot("偏好重点", Category.OTHER, prefCues)
    }

    /**
     * 按槽位取证据：每个槽位最多 [perSlot] 条，全局去重。
     *
     * 排序不是单纯关键词命中，而是「分类命中 > 槽位词命中 > 来源层级 > 有数字/日期」。
     * 官方/OTA 的事实优先，UGC 只作为路线/避坑的补充。
     */
    fun retrieve(
        records: List<EvidenceRecord>,
        slots: List<Slot>,
        perSlot: Int = 3
    ): List<Hit> {
        val out = mutableListOf<Hit>()
        val used = HashSet<String>()
        for (slot in slots) {
            val ranked = records.asSequence()
                .filter { it.id !in used }
                .map { r -> r to score(r, slot) }
                .filter { it.second > 0 }
                .sortedWith(
                    compareByDescending<Pair<EvidenceRecord, Int>> { it.second }
                        .thenBy { it.first.tier }
                )
                .take(perSlot)
                .toList()
            for ((record, _) in ranked) {
                if (used.add(record.id)) out.add(Hit(slot.label, record))
            }
        }
        return out
    }

    private fun score(r: EvidenceRecord, slot: Slot): Int {
        var s = 0
        if (slot.label == "全部证据") return 8 - r.tier
        val text = (r.fact + " " + r.title).lowercase()
        if (r.category == slot.category.label) s += 4
        val catCues = slot.category.cues.count { text.contains(it.lowercase()) }
        s += minOf(catCues, 3) * 2
        val slotCues = slot.cues.count { text.contains(it.lowercase()) }
        s += minOf(slotCues, 4)
        // 只靠来源层级不能把「车程」混进「门票」槽位：必须有分类或槽位词命中
        if (s == 0) return 0
        s += when (r.tier) {
            1 -> 3
            2 -> 2
            3 -> 1
            else -> 0
        }
        if (r.status == "高置信") s += 2
        if (DATE_KEYS.any { text.contains(it) }) s += 1
        return s
    }

    /** 把洗后的 SeaDeck 转成临时证据记录（事实卡优先，来源摘要兜底） */
    fun recordsFrom(deck: SeaDeck?, query: String): List<EvidenceRecord> {
        if (deck == null) return emptyList()
        val evidenceById = deck.evidence.associateBy { it.id }
        val claimRecords = deck.claims.map { c ->
            val first = c.evidenceIds.firstOrNull()?.let { evidenceById[it] }
            EvidenceRecord(
                id = "c${c.id}",
                category = categoryOf(c.statement).label,
                fact = c.statement,
                status = when (c.verdict) {
                    Verdict.CONFIRMED -> "高置信"
                    Verdict.QUALIFIED -> "中置信"
                    Verdict.DISPUTED -> "分歧"
                    Verdict.INSUFFICIENT -> "证据不足"
                },
                title = first?.title.orEmpty(),
                url = first?.url.orEmpty(),
                site = first?.site.orEmpty(),
                tier = first?.tier?.level ?: 3,
                published = first?.published.orEmpty(),
                sourceIds = c.evidenceIds,
                counter = c.counterStatement,
                query = query
            )
        }
        val usedSourceIds = claimRecords.flatMap { it.sourceIds }.toSet()
        val sourceRecords = deck.evidence
            .filter { it.id !in usedSourceIds }
            .map { e ->
                val text = (e.body.ifBlank { e.snippet })
                    .replace(Regex("\\s+"), " ")
                    .trim()
                EvidenceRecord(
                    id = "s${e.id}",
                    category = categoryOf(text).label,
                    fact = text.take(180),
                    status = "来源摘要",
                    title = e.title,
                    url = e.url,
                    site = e.site,
                    tier = e.tier.level,
                    published = e.published,
                    sourceIds = listOf(e.id),
                    query = query
                )
            }
        return (claimRecords + sourceRecords)
            .filter { it.fact.isNotBlank() }
            .distinctBy { it.fact }
    }

    /** 清洗管线没产出证据时，至少把收敛后的网页摘要存进临时库 */
    fun recordsFromHits(hits: List<WebHit>, query: String): List<EvidenceRecord> =
        hits.mapIndexed { i, h ->
            val text = listOf(h.title, h.snippet).filter { it.isNotBlank() }
                .joinToString("。").replace(Regex("\\s+"), " ").trim()
            EvidenceRecord(
                id = "h${i + 1}",
                category = categoryOf(text).label,
                fact = text.take(180),
                status = "来源摘要",
                title = h.title,
                url = h.url,
                site = h.site,
                tier = h.tier,
                published = h.publishedAt,
                sourceIds = listOf(i + 1),
                query = query
            )
        }.filter { it.fact.isNotBlank() }.distinctBy { it.fact }

    /**
     * 工具循环里的 `lookup_evidence(category, keyword)`：只从本轮临时库取证据，
     * 不重新联网。category 用九类标签，也接受“全部证据”。
     */
    fun lookup(
        records: List<EvidenceRecord>,
        categoryLabel: String,
        keyword: String,
        perSlot: Int = 5
    ): List<Hit> {
        val label = categoryLabel.trim().ifBlank { "全部证据" }
        val kw = keyword.trim()
        if (label == "全部证据") {
            val pool = if (kw.isBlank()) records else records.filter {
                (it.fact + " " + it.title).contains(kw, ignoreCase = true)
            }
            return retrieve(
                pool,
                listOf(Slot("全部证据", Category.OTHER, emptyList())),
                perSlot
            )
        }
        val category = Category.entries.firstOrNull { it.label == label } ?: Category.OTHER
        val tokens = kw.split(Regex("[\\s,，、/]+")).map { it.trim() }.filter { it.isNotBlank() }
        // 关键词是硬筛选：给了具体景点/关键词时，不把同一类别里别的景点也倒给模型；
        // 一个都没命中时返回空，让模型换关键词或直接回答，而不是误以为“查过了没有”。
        val pool = if (tokens.isEmpty()) records else records.filter { r ->
            val text = r.fact + " " + r.title
            tokens.any { text.contains(it, ignoreCase = true) }
        }
        // 具体类别不把关键词当加分项：否则“洪安古镇”会把交通证据也拉进门票槽。
        // 关键词只负责圈定范围，分类命中仍由 category / 分类词决定；
        // “其他背景”没有分类词时才用关键词兜底打分。
        val cues = if (category == Category.OTHER) (tokens + category.cues).distinct() else category.cues
        return retrieve(pool, listOf(Slot(label, category, cues)), perSlot)
    }

    /** 临时证据库 → 模型上下文；按槽位分组，明确哪些槽位没有证据 */
    fun render(hits: List<Hit>, totalRecords: Int, maxChars: Int): String {
        if (hits.isEmpty()) return ""
        return buildString {
            appendLine("【临时证据库·按需检索结果】")
            appendLine(
                "库内共 $totalRecords 条清洗证据，本次按 ${hits.map { it.slot }.distinct().size} 个需求槽位取用 ${hits.size} 条。"
            )
            appendLine("规则：官方/OTA > 主流媒体 > UGC；具体数字只能引用下面的事实句，没写的不要补。")
            hits.groupBy { it.slot }.forEach { (slot, list) ->
                appendLine()
                appendLine("■ $slot")
                list.forEach { h ->
                    val r = h.record
                    val cite = if (r.sourceIds.isEmpty()) "" else " [${r.sourceIds.joinToString(",")}]"
                    val meta = buildList {
                        if (r.site.isNotBlank()) add(r.site)
                        if (r.published.isNotBlank()) add(r.published)
                    }.joinToString("｜")
                    appendLine("- ${r.fact}$cite" + if (meta.isNotBlank()) "（$meta）" else "")
                    if (r.status == "分歧" && r.counter.isNotBlank()) {
                        appendLine("  另有来源称：${r.counter}")
                    }
                }
            }
            val covered = hits.map { it.slot }.toSet()
            val missing = listOf("门票与预约", "开放时间", "交通与车程", "景点与路线", "美食", "住宿", "避坑与排队")
                .filter { it !in covered }
            if (missing.isNotEmpty()) {
                appendLine()
                appendLine("未检索到证据的槽位：${missing.joinToString("、")}。这些槽位不要编具体数字，只能给方向性建议。")
            }
            appendLine()
            appendLine("写作要求：只使用上面出现的事实和数字；来源冲突时优先官方/OTA 与更新的日期；无法确认就写「以景区当日公告为准」。")
        }.take(maxChars).trim()
    }

    /** 给 AI 助手这类单问题场景用：按问题里的意图词生成槽位 */
    fun slotsForText(text: String): List<Slot> {
        val slots = mutableListOf<Slot>()
        Category.entries.filter { it != Category.OTHER }.forEach { cat ->
            if (cat.cues.any { text.contains(it) }) slots.add(Slot(cat.label, cat, cat.cues))
        }
        if (slots.isEmpty()) {
            slots.add(Slot("全部证据", Category.OTHER, emptyList()))
        }
        return slots
    }
}
