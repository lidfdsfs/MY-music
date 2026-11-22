package com.example.my.data

import android.util.Base64
import com.example.my.data.ai.SearchGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

/**
 * 一条 Bing 网页结果。url 保留原始地址，供 UI 直接打开来源页。
 *
 * [tier] / [score] 由数据清洗管线填写（见 data/search）：tier 1=官方一手 … 4=低质，
 * 0 表示"未经清洗"（旧调用路径或清洗后未保留的条目）。UI 只在 >0 时展示层级徽标。
 */
data class WebHit(
    val title: String,
    val snippet: String,
    val url: String,
    val site: String,
    val tier: Int = 0,
    val score: Double = 0.0,
    /** 搜索引擎返回的原始位次（1 起，0 = 答案卡/未知）。排名越靠前，关联度基线越高 */
    val rank: Int = 0,
    /** 相对本轮问题的关联度 0.0~1.0，由 [WebSearch.relevanceOf] 计算，UI 会显示 */
    val relevance: Double = 0.0,
    /** 搜索引擎返回的发布时间原文（新闻/公告类结果才有），用于提示模型判断时效 */
    val publishedAt: String = ""
)

/**
 * 全网检索唯一真源。
 *
 * AI 对话的「先搜再答」和发现页的「总结搜索答案」都走这里，好处有三：
 * 1. 结果带 URL，UI 能展示可点的引用来源，而不是只给模型塞一段纯文本；
 * 2. 同一关键词 10 分钟内复用内存缓存 —— ReAct 工具链和自动检索可能前后各要一次，
 *    不会真的联网两遍；
 * 3. 搜索词统一在这里做隐私清洗，精确经纬度绝不外发。
 *
 * 主源是 Bing 国内站的 RSS 输出（&format=rss）：体积只有 HTML 结果页的十分之一，
 * 结构与查询词严格对应；被限流时退回解析 HTML 结果页（cn.bing.com 大陆可直连）。
 */
object WebSearch {

    private const val MAX_HITS = 10
    private const val MAX_SNIPPET = 220
    private const val CANDIDATE_POOL = 40
    private const val CACHE_TTL_MS = 10 * 60 * 1000L
    private const val CACHE_LIMIT = 40

    /** 检索 / 抓取档位参数，由 [SearchMode] 映射；快速求快，高效求全。 */
    data class SearchProfile(
        val limit: Int,
        val maxBodies: Int,
        val bodyTimeoutMs: Long,
        val searchTimeoutMs: Long,
        val angles: Int,
        val maxEvidence: Int,
        val maxClaims: Int,
        /** 注入给模型的网页材料上限（字符）：快速精简、均衡适中、高效更全 */
        val contextChars: Int
    ) {
        /**
         * 轻量档搜不到/只回到 1~2 条时，允许升一级补搜，避免「简单问题快速答」
         * 变成「简单问题快速答不上来」。调用方只升级一次，不会无限加码。
         */
        fun escalate(): SearchProfile = when {
            limit <= 3 -> Companion.fast()
            limit <= 5 -> Companion.balanced()
            else -> Companion.deep()
        }

        companion object {
            fun balanced() = SearchProfile(10, 4, 4_500L, 9_000L, 2, 8, 6, 1500)
            /** 简单问题：只抓 1 条正文、1 个补充角度，2 秒级返回，别让寒暄等太久 */
            fun light() = SearchProfile(3, 1, 2_000L, 3_000L, 1, 3, 3, 600)
            fun fast() = SearchProfile(5, 1, 2_500L, 4_000L, 1, 4, 4, 800)
            /**
             * 快速档默认省流量；但人物/公司职务变动、新闻、价格这类会变的实时事实，
             * 只抓 1 条正文、800 字上下文很容易把「刚发生的事」漏掉。
             * [freshFast] 只在这些问题上放宽到够核实的最小型：仍是快速档，不拖成深搜。
             */
            fun freshFast() = SearchProfile(8, 3, 4_500L, 9_000L, 2, 8, 6, 1600)
            fun deep() = SearchProfile(14, 6, 8_000L, 14_000L, 3, 10, 8, 2400)
            /**
             * 行程规划专用：不是「多抓几篇」而是「多源覆盖」。
             * 官方文旅/OTA/知乎/小红书/路线/交通同时搜，候选池放大到 30 条，
             * 抓 8 篇正文后用清洗器压成事实卡，避免把几十页原文硬塞给模型。
             */
            fun travelDeep() = SearchProfile(30, 8, 6_500L, 22_000L, 7, 14, 12, 4_000)
            fun forMode(mode: SearchMode, freshNews: Boolean = false): SearchProfile = when (mode) {
                SearchMode.OFF -> balanced()
                SearchMode.FAST -> if (freshNews) freshFast() else fast()
                SearchMode.BALANCED -> balanced()
                SearchMode.DEEP -> deep()
            }

            /**
             * 按「问题复杂度」选档，而不是所有问题都套用户档位：
             * - 轻量：简单寒暄/翻译/短句，1 条正文快速答；
             * - 标准：普通事实，按时效性和用户档位取快档/均衡档；
             * - 深入：攻略、对比、职务变动、核实类，多角度多正文。
             * 用户的 FAST 档仍是上限，不会因为问题复杂就偷偷跑成深度档。
             */
            fun forDecision(
                mode: SearchMode,
                depth: SearchGate.Depth,
                freshNews: Boolean = false
            ): SearchProfile = when (depth) {
                SearchGate.Depth.LIGHT -> light()
                SearchGate.Depth.STANDARD -> when {
                    freshNews -> freshFast()
                    mode == SearchMode.FAST -> fast()
                    else -> balanced()
                }
                SearchGate.Depth.DEEP -> when (mode) {
                    SearchMode.FAST -> freshFast()
                    SearchMode.OFF -> light()
                    else -> deep()
                }
            }
        }
    }
    private const val UA =
        "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private data class Cached(val at: Long, val hits: List<WebHit>)

    private val cache = ConcurrentHashMap<String, Cached>()

    /**
     * 检索。失败返回空列表（调用方据此明确提示"没查到"，而不是让模型凭记忆编）。
     * 全部在 IO 线程，超时 9 秒，不阻塞 UI。
     */
    suspend fun search(
        rawQuery: String,
        limit: Int = MAX_HITS,
        profile: SearchProfile = SearchProfile.balanced(),
        /** 检索路由/模型给出的补充检索词（最多 2 条），必须过同主题校验后才使用 */
        extraQueries: List<String> = emptyList(),
        /**
         * 行程规划这类需要「多源覆盖」的场景设为 true：原话第一层成功后仍继续跑
         * 官方文旅/OTA/知乎/小红书/路线/交通等角度；普通问答保持 false，省时省请求。
         */
        expandAlways: Boolean = false
    ): List<WebHit> =
        withContext(Dispatchers.IO) {
            // 过渡功能：开启「服务器网页索引」时先取服务端清洗后的结果；
            // 服务端不可用或返回空，自动回落到下面的原本地抓取/清洗链路，原逻辑一行不改。
            if (ServerSearchPrefs.isEnabled()) {
                val remote = runCatching {
                    ServerSearch.searchRemote(rawQuery, limit, profile)
                }.getOrDefault(emptyList())
                if (remote.isNotEmpty()) return@withContext remote
            }
            val queries = planQueries(rawQuery, extraQueries)
            val primary = queries.firstOrNull() ?: return@withContext emptyList()
            val key = cacheKey(queries.joinToString(" | ")) + "#${profile.limit}/${profile.angles}/$expandAlways"
            cache[key]?.let {
                if (System.currentTimeMillis() - it.at < CACHE_TTL_MS) {
                    // 缓存只能复用「命中」，关联度必须按本轮问句重算：
                    // 否则同一个检索词被不同问题复用时，会挂着上一位用户的主题标签
                    return@withContext diversify(
                        keepOnTopic(it.hits.map { h -> h.copy(relevance = 0.0) }, rawQuery), limit
                    )
                }
            }
            // 第一层：用户原话直接搜，同时取 RSS 与 HTML 答案卡。
            // 这一步不掺任何改写词，先看原句本身能不能给出足够相关的内容。
            val rawBatches = coroutineScope {
                listOf(
                    async { runCatching { sourcesFor(primary) }.getOrDefault(emptyList()) },
                    // 主问句额外取一次 HTML：答案卡/新闻卡只存在于 HTML 结果页，
                    // RSS 即使满 10 条也不含这些一手摘要
                    async { runCatching { bingHtml(primary) }.getOrDefault(emptyList()) }
                )
            }.awaitAll()
            val rawPool = mergeAndRank(rawBatches.flatten(), rawQuery).take(CANDIDATE_POOL)
            // 主题闸门：按「原始排名 + 核心实体 + 意图」算关联度，不达标的一律挡在
            // 上下文与来源列表之外。地名只是弱加分，不能单独放行（问「重庆交通大学
            // 校长」却挂 8 篇「重庆旅游攻略」就是旧版这么翻车的）。
            var hits = keepOnTopic(rawPool, rawQuery)

            // 第二层：原话搜失败、或只搜到不够相关的内容时才启动。
            // 职务变动类长句会被 Bing 按「黄」单字拆成百科/姓氏页，这一步用
            // 「黄仁勋 英伟达 CEO 最新消息」等拆词角度兜底；原话能搜到就不额外发请求。
            if (claimQuery(rawQuery) != null ||
                expandAlways || !hasImportantContent(hits, rawQuery)
            ) {
                // 职务变动类断言必须多给一个「英文名 + 公司英文名」和官方域名角度：
                // 中文自媒体会把「辞职」标题刷满，但官方/一手页面通常只有英文。
                // 第一层已经拿到公司官网时，官方站内角度就不再重复打；
                // 只补「最新消息、拆词、英文名」三条，第一层的命中仍然参与最终合并。
                val claim = claimQuery(rawQuery)
                val officialDomain = claim?.let { officialDomainFor(it) }
                val firstLayerHasOfficial = officialDomain != null &&
                    hits.any { isOfficialHit(it, officialDomain) }
                val effectiveAngles = when {
                    claim == null -> profile.angles
                    firstLayerHasOfficial -> 3
                    else -> maxOf(profile.angles, 4)
                }
                val angles = queries.drop(1).take(maxOf(effectiveAngles, 1))
                if (angles.isNotEmpty()) {
                    val fallbackBatches = coroutineScope {
                        angles.map { q ->
                            async { runCatching { sourcesFor(q) }.getOrDefault(emptyList()) }
                        }
                    }.awaitAll()
                    hits = keepOnTopic(
                        mergeAndRank(rawPool + fallbackBatches.flatten(), rawQuery)
                            .take(CANDIDATE_POOL),
                        rawQuery
                    )
                }
            }

            // 两层都没搜到时，再退回最干净的主体词/第一个补充角度补搜一轮
            // （原句常带「最新/多少钱/预约」等限定词，长尾小站反而搜不到）
            if (hits.isEmpty()) {
                // 优先用改写后的垂直角度词再搜一轮，别用带「规划」的原句主体
                // 退回去，否则又会把整页政务规划文件捞回来。
                val fallback = queries.getOrNull(1) ?: subject(rawQuery)
                if (fallback.isNotBlank() && fallback != primary) {
                    val extra = coroutineScope {
                        listOf(
                            async { runCatching { sourcesFor(fallback) }.getOrDefault(emptyList()) },
                            async { runCatching { sourcesFor("$fallback 最新") }.getOrDefault(emptyList()) }
                        ).awaitAll()
                    }.flatten()
                    hits = keepOnTopic(mergeAndRank(extra, rawQuery).take(CANDIDATE_POOL), rawQuery)
                }
                // 两条路都只捞到跨领域页：宁可如实告诉用户「这次没查到相关网页」，
                // 也绝不把刚过滤掉的无关页原样塞回「参考来源」——这是旧版最致命的放水。
            }
            val ranked = hits.map { it.copy(relevance = relevanceOf(it, rawQuery)) }
                .sortedByDescending { it.relevance }
            if (ranked.isNotEmpty()) {
                if (cache.size >= CACHE_LIMIT) {
                    cache.entries.minByOrNull { it.value.at }?.key?.let { cache.remove(it) }
                }
                cache[key] = Cached(System.currentTimeMillis(), ranked)
            }
            diversify(ranked, limit)
        }

    private fun cacheKey(q: String): String =
        q.lowercase().replace(Regex("\\s+"), " ").trim()

    /**
     * 合并多批结果并重排：先按规范化 URL 去重，再合并「标题近重复」（同文不同站、
     * 只差「最新 / 2026 / 一周」这类后缀），最后按权重排序。
     * 以前用「标题前 12 字」硬去重，会把不同站点的不同文章误杀，也留不住更长尾的好结果。
     */
    internal fun mergeAndRank(raw: List<WebHit>, query: String): List<WebHit> {
        val seenUrl = HashMap<String, WebHit>()
        raw.filter { it.title.isNotBlank() }
            .forEach { h ->
                val k = normalizeUrl(h.url).ifBlank { titleKey(h.title) }
                val old = seenUrl[k]
                if (old == null) {
                    seenUrl[k] = h
                } else if (rankScore(h, query) >= rankScore(old, query)) {
                    // 同一网页从多个检索角度回来时，保留它最靠前的搜索引擎位次
                    seenUrl[k] = h.copy(rank = bestRank(h.rank, old.rank))
                }
            }
        val sorted = seenUrl.values.sortedByDescending { rankScore(it, query) }
        val kept = mutableListOf<WebHit>()
        sorted.forEach { h ->
            if (kept.none { nearDuplicate(it, h) }) kept += h
        }
        return kept
    }

    /** 标题近重复：完全同名，或前 10 个有效字符相同、多出来的只是「最新/完整版/2026」这类噪声后缀 */
    private fun nearDuplicate(a: WebHit, b: WebHit): Boolean {
        val ka = titleKey(a.title)
        val kb = titleKey(b.title)
        if (ka == kb) return true
        if (ka.length < 10 || kb.length < 10) return false
        if (!ka.take(10).equals(kb.take(10))) return false
        val longer = if (ka.length >= kb.length) ka else kb
        val shorter = if (ka.length >= kb.length) kb else ka
        val suffix = longer.removePrefix(shorter)
        if (suffix.length > 8) return false
        return Regex("^(最新|完整版|全文|全文阅读|官网|更新|修订版|版|20\\d{2}|\\d+)$")
            .matches(suffix)
    }

    /**
     * 站点多样性：同一站点最多占 2 条，避免前 10 条有 6 条来自同一个聚合站。
     * 名额没用满时再从溢出池按分数补齐，保证条数不缩水。
     */
    internal fun diversify(hits: List<WebHit>, limit: Int, maxPerSite: Int = 2): List<WebHit> {
        if (hits.size <= limit) return hits
        val out = mutableListOf<WebHit>()
        val overflow = mutableListOf<WebHit>()
        val perSite = HashMap<String, Int>()
        hits.forEach { h ->
            val site = h.site.ifBlank { h.url }
            val n = perSite[site] ?: 0
            if (n < maxPerSite && out.size < limit) {
                out += h
                perSite[site] = n + 1
            } else {
                overflow += h
            }
        }
        overflow.take(limit - out.size).forEach { out += it }
        return out
    }

    /** 规范化 URL：去掉 fragment、追踪参数，统一 www / m 子域，便于跨站去重 */
    internal fun normalizeUrl(raw: String): String {
        if (raw.isBlank()) return ""
        val noFrag = raw.substringBefore('#')
        return runCatching {
            val uri = URI(noFrag)
            val scheme = (uri.scheme ?: "https").lowercase()
            var host = (uri.host ?: return@runCatching noFrag.lowercase()).lowercase()
                .removePrefix("www.")
                .removePrefix("m.")
                .removePrefix("mobile.")
            if (host.isBlank()) return@runCatching noFrag.lowercase()
            val path = (uri.rawPath ?: "").trimEnd('/')
            val kept = parseQuery(uri.rawQuery)
            buildString {
                append(scheme).append("://").append(host).append(path)
                if (kept.isNotBlank()) append('?').append(kept)
            }
        }.getOrDefault(noFrag.lowercase())
    }

    private fun parseQuery(rawQuery: String?): String {
        if (rawQuery.isNullOrBlank()) return ""
        val drop = setOf(
            "from", "src", "ref", "source", "share", "spm", "fbclid", "gclid",
            "msclkid", "wfr", "_t", "timestamp", "utm_source", "utm_medium",
            "utm_campaign", "utm_term", "utm_content"
        )
        return rawQuery.split('&')
            .filter { it.isNotBlank() }
            .filter { p -> p.substringBefore('=').lowercase() !in drop }
            .filterNot { p -> p.substringBefore('=').lowercase().startsWith("utm_") }
            .sorted()
            .joinToString("&")
    }

    internal fun titleKey(title: String): String =
        title.lowercase().filter { it.isLetterOrDigit() }.take(40)

    private val AUTHORITY = mapOf(
        "gov.cn" to 6.0,
        "weather.com.cn" to 5.0,
        "cma.cn" to 4.5,
        "people.com.cn" to 4.0,
        "xinhuanet.com" to 4.0,
        "cctv.com" to 3.0,
        "ctrip.com" to 3.0,
        "qunar.com" to 2.8,
        "mafengwo.cn" to 2.5,
        "dianping.com" to 2.5,
        "wikipedia.org" to 2.0,
        "zhihu.com" to 2.6,
        "xiaohongshu.com" to 2.4,
        "douyin.com" to 2.0,
        "bilibili.com" to 1.8,
        "baike.baidu.com" to -3.0,
        "cn.bing.com" to 2.0
    )

    /** 关联度及格线：低于它的网页不进上下文，也不进「参考来源」列表 */
    internal const val MIN_RELEVANCE = 0.35

    /**
     * 网页自带排名 → 相关强度（连续衰减，不再用「1~3 名同分」的阶梯）。
     *
     * 搜索引擎已经按相关性排过序，位次本身携带信息：第 1 位强于第 2 位，
     * 第 2 位强于第 3 位……但强度必须「有限」：最高 0.34，单独低于
     * [MIN_RELEVANCE]，任何网页都不能只靠排名混进参考答案。
     * 0.34·e^(-0.11·(rank-1))：1位.34 / 2位.30 / 3位.27 / 5位.22 / 10位.13 / 20位.05
     */
    fun rankStrength(rank: Int): Double = if (rank <= 0) 0.14
    else (0.34 * kotlin.math.exp(-0.11 * (rank - 1))).coerceAtLeast(0.05)

    /** 合并同一 URL 的多批结果时，保留最靠前的那个位次 */
    private fun bestRank(a: Int, b: Int): Int = when {
        a <= 0 -> b
        b <= 0 -> a
        else -> minOf(a, b)
    }

    /**
     * 中文问句里的「核心实体」：真正要查的那个机构 / 人 / 职务，而不是地名。
     *
     * 「重庆交通大学目前校长是谁」→ [重庆交通大学, 校长]。
     * 这一步是「参考网页与问题无关」的关键防线：地名「重庆」谁都沾得上，
     * 但旅游攻略里绝不会出现「交通大学 / 校长」。
     */
    internal fun coreEntities(raw: String): List<String> {
        val q = keywordize(raw)
        val out = LinkedHashSet<String>()
        Regex("[\\u4e00-\\u9fa5]{2,10}(?:大学|学院|学校|中学|小学|医院|公司|集团|景区|机场|车站|博物馆|公园|酒店|研究院)")
            .findAll(q).forEach { out += it.value }
        listOf(
            "校长", "院长", "书记", "院士", "主任", "负责人", "创始人", "董事长",
            "总经理", "市长", "局长", "馆长", "校长是谁", "现任领导"
        ).filter { q.contains(it) }.forEach { out += it }
        Regex("[A-Za-z][A-Za-z0-9 .'-]{2,20}").findAll(q)
            .map { it.value.trim() }.filter { it.length >= 3 }.forEach { out += it }
        return out.filter { it.length >= 2 }.distinct().take(8)
    }

    /**
     * 疑问 / 虚词 / 客套话：从**任意**问句里抠「真正要查的内容」时先剥掉。
     * 旧的精准判定只认机构/职务专名，遇到「红烧肉怎么做」「高血压能吃鸡蛋吗」
     * 这类普通问题就整页放行；这里让所有问题都有可比对的主题词。
     */
    private val QUESTION_NOISE = listOf(
        "请问", "帮我", "帮忙", "麻烦", "我想知道", "我想", "想问", "告诉我", "查一下", "搜一下",
        "是谁", "多少钱", "多少", "什么时候", "怎么样", "怎样", "怎么", "为何", "为什么",
        "哪些", "哪个", "什么", "是否", "有没有", "能不能", "可不可以", "如何", "哪里", "哪儿",
        "关于", "以及", "还有", "目前", "现在", "今年", "最近", "今天", "明天", "一下",
        "一些", "一个", "谢谢", "最新", "相关", "情况", "资料", "信息", "问题",
        "方法", "办法", "原因", "区别", "作用", "介绍", "含义", "意思", "推荐", "建议",
        // 只留不会切碎专名的虚词：「对」会毁掉「相对论」、「去」会毁掉「列表去重」，
        // 「是 / 在 / 有 / 和」等单字一律不进表，宁可留着也别把内容词切坏
        "的", "了", "吗", "呢", "吧", "啊", "呀", "做"
    )

    /** 纯闲聊：没有任何内容词可比，才允许只按排名给展示关联度（不误伤发现页） */
    private val CHAT_NOISE = listOf(
        "随便看看", "你好", "在吗", "嗨", "哈哈", "聊聊", "随便", "看看", "没事", "好的", "谢谢啊"
    )

    /** 去掉疑问/虚词后的紧凑内容串，供主题词与精准检索词共用 */
    private fun contentTerms(raw: String): List<String> {
        var q = keywordize(raw)
        CHAT_NOISE.forEach { q = q.replace(it, " ") }
        QUESTION_NOISE.forEach { q = q.replace(it, " ") }
        return q.replace(Regex("[^\\u4e00-\\u9fa5A-Za-z0-9]+"), " ")
            .split(' ').map { it.trim() }.filter { it.length >= 2 }
    }

    /**
     * 任意问句的通用主题词：强实体（机构/职务/英文专名）优先，其余用去疑问词后的
     * 内容片段补齐。刻意不再补「词干」——多一个词就会稀释命中覆盖率，
     * 让本来精准的网页被误判成「只沾到一点边」。
     */
    internal fun topicTerms(raw: String): List<String> {
        // 职务变动断言用拆好的「人名 公司 职务」做主题词；否则整句 8 字截断
        // 会把「不在担任」当成主词，真正讲「辞任」的新闻反而匹配不上。
        val effective = claimQuery(raw) ?: raw
        val out = LinkedHashSet<String>()
        coreEntities(keywordize(effective)).forEach { out += it }
        contentTerms(effective).forEach { seg -> out += if (seg.length > 8) seg.take(8) else seg }
        return out.filter { it.length >= 2 }.distinct().take(10)
    }

    /**
     * 中文没有分词：用「相邻字组合」认定是同一个东西，但只沾一个字不算命中。
     * 短词（3~4 字）必须几乎全命中，否则「红烧」会被当成「红烧肉」；
     * 长词允许自然改写（「高血压能吃鸡蛋」→「高血压病人可以吃鸡蛋吗」），
     * 但仍要求至少 3 个组合命中且覆盖 45% 以上。
     */
    private fun termHit(text: String, term: String): Boolean {
        if (text.contains(term, ignoreCase = true)) return true
        if (term.length < 3) return false
        val grams = term.windowed(2).distinct()
        val hit = grams.count { text.contains(it, ignoreCase = true) }
        val ratio = hit.toDouble() / grams.size
        return if (term.length <= 4) ratio >= 0.99 else hit >= 3 && ratio >= 0.45
    }

    /**
     * 精准检索词：原句之外的第二条检索角度，也是「过滤空了」时的兜底。
     * 实测 Bing 搜「Python 列表去重的方法」只给官网首页，搜「Python 列表去重」
     * 才返回真正讲去重的页面；中文原句仍是最稳的主词，这条只做补充。
     */
    fun preciseQuery(raw: String): String {
        val parts = contentTerms(raw)
        if (parts.isEmpty()) return keywordize(raw).take(24)
        // 中文片段之间不加空格：实测 Bing 对「高血压 鸡蛋」这类带空格的中文
        // 会拆成单字给百科；而「Python 列表去重」这种英文 + 中文保留空格反而更准
        return buildString {
            parts.forEachIndexed { i, p ->
                if (i > 0) {
                    val prev = parts[i - 1].lastOrNull()
                    val cur = p.firstOrNull()
                    val bothCjk = prev != null && cur != null &&
                        prev in '\u4e00'..'\u9fa5' && cur in '\u4e00'..'\u9fa5'
                    if (!bothCjk) append(' ')
                }
                append(p)
            }
        }.take(24)
    }

    /** 时效标记：只用于提示模型优先采信网页，不再是「要不要联网」的门槛 */
    fun isTimeSensitive(text: String): Boolean = WEB_HINTS.any { text.contains(it) }

    private fun contentChars(s: String): Set<Char> =
        keywordize(s).filter { it in '\u4e00'..'\u9fa5' || it.isLetterOrDigit() }.toSet()

    /**
     * 模型给出的检索词是否还在讲用户问的这件事。
     * 共享内容字覆盖率 ≥ 0.5 就算合理改写（「规划大理3日旅行」→「大理3日游攻略 行程」），
     * 只有完全串台（「重庆交通大学校长」→「重庆旅游攻略 行程」）才需要校正。
     */
    fun queryOnTopic(query: String, userText: String): Boolean {
        val a = contentChars(query)
        val b = contentChars(userText)
        if (b.isEmpty()) return true
        return b.count { it in a }.toDouble() / b.size >= 0.5
    }

    /** 问题所属意图锚点里，扣掉地名后剩下的「类别词」 */
    private fun intentAnchors(raw: String): List<String> {
        val city = CityCoords.detectIn(raw).orEmpty()
        return topicAnchors(raw).filter { it != city && it.length >= 2 }
    }

    /**
     * 关联度 = 网页自带排名的连续强度 + 主题词命中 + 意图命中 + 地名弱加分。
     *
     * 「所有用户问题都精准搜索」的落点：
     *  - 有机构/职务等中文专名 → 用强实体严格判定（一个没命中 -0.40）；
     *  - 普通问句（红烧肉怎么做 / Python 列表去重 / 高血压能吃鸡蛋吗）→ 用
     *    [topicTerms] 抠出的通用主题词做同样精准的判定，不再整页放行；
     *  - 只有纯闲聊连主题词都抠不出时，才不比对内容、只按排名给展示关联度。
     * 通用词还要求覆盖率：只沾到一个泛词（如官网首页只含「Python」）也算不精准。
     */
    internal fun relevanceOf(h: WebHit, query: String): Double {
        val title = h.title
        val text = "$title ${h.snippet} ${h.site}"
        // 职务变动断言按拆好的实体词判断关联，不能用被截断的整句主题词
        val effective = claimQuery(query) ?: query
        val allCore = coreEntities(effective)
        // 英文专名（Python / iPhone）单独不足以当「强实体」，否则官网首页会全放行
        val strong = allCore.filter { w -> w.any { it in '\u4e00'..'\u9fa5' } }
        val generic = topicTerms(effective)
        val strict = strong.isNotEmpty()
        val core = if (strict) strong else generic
        val intents = intentAnchors(effective)
        val city = CityCoords.detectIn(effective).orEmpty()
        if (generic.isEmpty() && intents.isEmpty() && city.isBlank()) {
            return (rankStrength(h.rank) + 0.25).coerceIn(0.0, 1.0)
        }
        var v = rankStrength(h.rank)
        var zeroHit = false
        if (core.isNotEmpty()) {
            val inTitle = core.count { w ->
                if (strict) title.contains(w, ignoreCase = true) else termHit(title, w)
            }
            val inAny = core.count { w ->
                if (strict) text.contains(w, ignoreCase = true) else termHit(text, w)
            }
            v += if (strict) minOf(0.42, inTitle * 0.22 + inAny * 0.10)
            else minOf(0.34, inTitle * 0.18 + inAny * 0.08)
            when {
                inAny == 0 -> {
                    v -= if (strict) 0.40 else 0.22
                    zeroHit = true
                }
                // 通用词只命中一个泛词：仍不算精准（专治「Python 官网首页」这类结果）
                !strict && inAny.toDouble() / core.size < 0.6 -> v -= 0.28
            }
        }
        val intentHits = intents.count { text.contains(it, ignoreCase = true) }
        v += minOf(0.24, intentHits * 0.08)
        // 旅行/攻略类问法常把主题词带偏，命中多个意图词时补回一部分零命中惩罚，
        // 避免把真正有用的攻略页误杀
        if (zeroHit && !strict && intentHits >= 2) v += 0.11
        if (city.isNotBlank() && text.contains(city)) v += 0.06
        if (title.contains("百度百科") || title.contains("人民政府")) v -= 0.08
        return v.coerceIn(0.0, 1.0)
    }

    /** 单条结果的综合分：标题权重远高于摘要，站点权威与新鲜度做修正 */
    internal fun rankScore(h: WebHit, query: String): Double {
        val title = h.title
        val snippet = h.snippet
        var s = 0.0
        INTENT_WORDS.forEach { w ->
            if (title.contains(w)) s += 4.0 else if (snippet.contains(w)) s += 1.5
        }
        // 中文问句没有空格，直接 split(' ') 会得到整句而一个词都匹配不上；
        // 改成通用主题词 + 意图锚点：所有问题都比对「这条网页是不是在讲这件事」
        (topicTerms(query) + intentAnchors(query)).distinct()
            .filter { it.length >= 2 }.take(8)
            .forEach { w ->
                if (title.contains(w, ignoreCase = true)) s += 2.5
                else if (snippet.contains(w, ignoreCase = true)) s += 1.0
            }
        // 关联度是硬性权重：与问题不同主题的页面，即便来自 gov.cn 也不能靠权威分挤进前排
        val rel = if (h.relevance > 0.0) h.relevance else relevanceOf(h, query)
        s += rel * 20.0
        if (rel < MIN_RELEVANCE) s -= 24.0
        else AUTHORITY.entries.firstOrNull { h.site.endsWith(it.key) }?.let { s += it.value }
        // Bing 直接给的答案摘要没有标题关键词，单独给一点权重，避免被普通结果挤下去
        if (h.title.startsWith("Bing 答案摘要")) s += 8.0
        if (snippet.contains("刚刚") || snippet.contains("分钟前") ||
            snippet.contains("小时前") || snippet.contains("今天")
        ) s += 3.0
        if (title.contains("百度百科")) s -= 4.0
        if (title.contains("人民政府") || title.contains("市政府")) s -= 2.0
        return s
    }

    /**
     * 抓前几条结果页的正文，让模型真正拿到价格 / 温度 / 评分这类数字，
     * 而不是只有一句搜索摘要。并发 + 整体 9 秒超时，失败就返回空串（不编造）。
     * 只抓公开网页，不带任何用户数据。
     */
    suspend fun fetchPages(
        hits: List<WebHit>,
        max: Int = 2,
        bodyTimeoutMs: Long = 4_500L
    ): String =
        withContext(Dispatchers.IO) {
            val pages = fetchBodies(hits, max, bodyTimeoutMs)
            if (pages.isEmpty()) return@withContext ""
            val lines = pages.map { (h, text) ->
                "[${hits.indexOf(h) + 1}] ${h.title}（${h.site}）\n    $text"
            }
            "【网页正文摘录】（从上面的结果页实时抓取，可能含价格/温度/评分等具体数字；" +
                "只引用这里确实出现过的数字，没出现就如实说未查到）\n" +
                lines.joinToString("\n\n")
        }

    /**
     * 抓正文并保留"命中 → 正文"的对应关系。
     *
     * [fetchPages] 只返回拼好的字符串，清洗管线需要结构化对应关系才能判断
     * "这条摘要是哪个来源的、正文里有没有可裁决的事实"，所以单独暴露这一版。
     */
    suspend fun fetchBodies(
        hits: List<WebHit>,
        max: Int = 4,
        bodyTimeoutMs: Long = 4_500L,
        candidateCap: Int = 5
    ): List<Pair<WebHit, String>> =
        withContext(Dispatchers.IO) {
            val picked = hits
                .filter { it.url.startsWith("http", ignoreCase = true) }
                .filterNot { it.site.contains("baike.baidu") }
                // 多取几条候选：携程/知乎这类站点常返回 403，跳过它们还有后面的页面能用
                .take(candidateCap.coerceAtLeast(max + 2))
            if (picked.isEmpty()) return@withContext emptyList()
            // 整体超时随档位：快速档短超时别让用户干等，高效档放宽以抓到更多正文
            val pages = withTimeoutOrNull(bodyTimeoutMs) {
                coroutineScope {
                    picked.map { h ->
                        async { h to runCatching { extractText(h.url) }.getOrDefault("") }
                    }.awaitAll()
                }
            }.orEmpty()
            // 不再"按搜索顺序取前 N 篇"：正文质量（数字/事实密度）高、且与问题
            // 关联度高的网页优先给模型，避免把无关页的正文当成本轮资料
            pages.filter { (_, text) -> text.length > 100 }
                .sortedByDescending { (h, text) -> bodyQuality(text) + (h.relevance * 40).toInt() }
                .take(max)
        }

    /** 正文里数字和事实词越多，越可能是真正的价格/天气/营业信息，而不是免责声明 */
    private fun bodyQuality(text: String): Int {
        val digits = text.count { it.isDigit() }
        val facts = Regex("℃|门票|价格|票价|评分|营业|开放|预约|地址|电话|公里|小时")
            .findAll(text).count()
        return digits + facts * 4 + text.length / 120
    }

    /** 把检索结果渲染成给模型的上下文，并写死「不许编造」的约束 */
    fun render(hits: List<WebHit>, query: String = ""): String {
        if (hits.isEmpty()) return ""
        return buildString {
            appendLine("【Bing 实时网页搜索结果】${if (query.isBlank()) "" else "查询词：$query"}｜共 ${hits.size} 条，抓取于刚刚")
            hits.forEachIndexed { i, h ->
                append("[${i + 1}] ${h.title}")
                if (h.site.isNotBlank()) append("（来源：${h.site}）")
                // 把「网页排名 / 关联度」显式交给模型：前排高关联度的网页应优先采信
                if (h.relevance > 0.0) {
                    append("〔关联度 ${(h.relevance * 100).toInt()}%")
                    if (h.rank > 0) {
                        append("，搜索第${h.rank}位·排名强度 ${(rankStrength(h.rank) * 100).toInt()}%")
                    }
                    append("〕")
                }
                appendLine()
                if (h.publishedAt.isNotBlank()) appendLine("    发布时间：${h.publishedAt}")
                if (h.snippet.isNotBlank()) appendLine("    ${h.snippet}")
                if (h.url.isNotBlank()) appendLine("    链接：${h.url}")
            }
            appendLine()
            if (isTimeSensitive(query)) {
                appendLine("本轮问题涉及会随时间变化的信息，以网页里的实时数据为准，不要用旧记忆里的数字覆盖。")
            }
            appendLine(
                "回答要求：本轮已联网查到以上 ${hits.size} 条真实网页，资料就在上面，必须据此作答。" +
                    "请优先引用关联度高、排名靠前的网页（排名强度已标注），在对应事实后标注来源编号 [n]；" +
                    "不要用训练记忆覆盖网页里的最新事实。只有网页里确实没写的数据才不要凭记忆补具体数字，" +
                    "可给方向性建议但要标明不是实时信息。"
            )
            if (claimQuery(query) != null) {
                appendLine(
                    "这是人物/公司职务变动类断言，先给出「说法是否准确」的结论，再解释依据：" +
                        "1) 优先采信公司官网、官方新闻室、SEC/交易所公告、通讯社等一手来源；" +
                        "2) 只有自媒体互相转载、没有官方公告时，明确说「目前只看到媒体转述，未见官方公告」，" +
                        "不要把它当成已被官方确认的事实；" +
                        "3) 官方页面仍显示原任职、且没有新公告时，直接说「该传闻未获官方证实」；" +
                        "4) 不要把「用户听说」复述成确定事实。"
                )
            } else {
                appendLine(
                    "如果用户这句话是在断言某个人/公司/机构「已卸任、已辞职、已换人」等事实，" +
                        "必须以网页（尤其发布时间最新的新闻）为准：网页与用户说法冲突时，直接指出「这条说法不准确」，" +
                        "并给出网页确认的最新职务状态和生效时间，不要再用训练记忆里的旧任职信息反驳。"
                )
            }
            appendLine(
                "与问题无关的条目直接忽略，不要为了凑引用而生硬罗列；如果现有网页都只是低质转述，" +
                    "要具体说明缺哪类一手来源（如官方公告），而不是笼统说「没有可信的网络数据」。直接给用户一段有用的结论。"
            )
        }.trim()
    }

    /**
     * 联网总开关打开后，所有非空消息都值得检索一次。
     * 这里不再排除本地创作、改写或 App 内数据：产品要求「开关一开，所有问题都触发」，
     * 由上层决定如何处理检索结果，而不是在检索入口先把问题静默挡掉。
     */
    fun needsWeb(text: String): Boolean = text.trim().isNotEmpty()

    /**
     * 时效词表：现在只给 [isTimeSensitive] 用来提示模型「以网页实时数据为准」，
     * **不再决定要不要联网**——联网开关打开时所有用户问题都会检索。
     */
    private val WEB_HINTS = listOf(
        // 价格 / 票务
        "多少钱", "价格", "票价", "机票", "机票价格", "车票", "高铁票", "航班", "费用", "预算",
        "汇率", "涨价", "降价", "免费吗", "门票", "团购",
        // 实时状态
        "天气", "气温", "温度", "几度", "下雨", "带伞", "最新", "最近", "今年", "现在",
        "今日", "今天", "明天", "新闻", "公告", "限流", "客流", "人流", "实时",
        // 人物/公司职务变动：这类「某人已卸任」的断言必须按最新新闻核对
        "不再担任", "不在担任", "卸任", "辞任", "辞去", "辞职", "离职",
        "接任", "出任", "任命", "换帅", "罢免", "免职",
        // 商家 / 口碑
        "酒店", "民宿", "住宿", "评价", "口碑", "评分", "推荐", "值不值", "好吃", "餐厅",
        "开放时间", "营业时间", "几点开门", "几点关门", "预约", "订票", "开业", "关闭", "新开",
        // 通用检索意图
        "查一下", "搜一下", "网上", "攻略", "活动", "演出", "怎么去", "交通"
    )

    /** 用于给合并结果打分：标题/摘要里出现的意图词越多越靠前 */
    private val INTENT_WORDS = listOf(
        "机票", "航班", "特价", "价格", "票价", "多少钱", "费用", "元",
        "天气", "预报", "气温", "温度", "℃", "几度", "下雨",
        "门票", "预约", "开放时间", "营业时间", "订票",
        "酒店", "民宿", "住宿", "点评", "评价", "评分", "口碑",
        "攻略", "景点", "美食", "推荐", "最新", "更新",
        // 交通/路线类：搜「怎么去」时，公交地铁线路页必须压过百科词条
        "公交", "地铁", "线路", "路线", "驾车", "步行", "打车", "怎么去",
        "交通", "高铁", "车站", "机场",
        // 歌词/公告类
        "歌词", "官网", "公告", "通知"
    )

    /**
     * 旅行 / 行程规划类问句里的「规划」是政务高频词。实测 Bing 搜
     * 「规划大理3日旅行」前 8 条全是「国家发展规划法 / 发改委 / 规划局 /
     * 职业规划大赛」，和大理一点关系都没有。检索词必须改写成旅游攻略词，
     * 光靠排序是救不回来的。这里只放会触发「改写主词」的词，保持窄一点，
     * 免得把「大理到丽江路线」这类交通问句也改掉。
     */
    private val TRAVEL_PLAN_HINTS = listOf(
        "规划", "行程", "旅行", "旅游", "日游", "天游", "自由行", "几日", "游玩", "怎么玩"
    )

    /** 职务变动动词：只有和职务名同时出现时才按「事实断言」拆词检索。 */
    private val ROLE_CHANGE_VERBS = listOf(
        "不再担任", "不在担任", "不再出任", "卸任", "辞任", "辞去", "辞职", "离职",
        "接任", "出任", "任命", "换帅", "罢免", "免职", "暂代", "代理"
    )

    /** 职务名。中文长职务名在 Bing 里很容易被单字实体带偏，优先映射成英文缩写。 */
    private val ROLE_TITLES = listOf(
        "首席执行官", "首席财务官", "首席技术官", "首席运营官",
        "董事长", "总裁", "总经理", "创始人", "法定代表人",
        "CEO", "CFO", "CTO", "COO"
    )
    private val ROLE_TITLE_ABBR = mapOf(
        "首席执行官" to "CEO",
        "首席财务官" to "CFO",
        "首席技术官" to "CTO",
        "首席运营官" to "COO"
    )
    private val TRAVEL_ANCHORS = listOf(
        "旅游", "旅行", "攻略", "景点", "行程", "游玩", "日游", "游记", "打卡", "路线", "自由行", "景区"
    )
    private val LYRIC_ANCHORS = listOf("歌词", "LRC", "lrc")
    private val WEATHER_ANCHORS = listOf("天气", "预报", "气温", "温度", "℃")
    private val FLIGHT_ANCHORS = listOf("机票", "航班", "票价", "特价")
    private val HOTEL_ANCHORS = listOf("酒店", "民宿", "住宿", "点评", "评分")
    private val TICKET_ANCHORS = listOf("门票", "开放", "预约", "营业", "景区")
    private val TRANSIT_ANCHORS = listOf("公交", "地铁", "线路", "路线", "驾车", "交通", "高铁", "车站", "机场")

    /**
     * 「交通」这个意图词很容易误伤专名：「重庆交通大学校长」里的「交通大学」
     * 不是问路。先把这些专名扣掉再判断，否则整题会被当成路线查询，检索词
     * 直接拐去「重庆旅游攻略 行程」——截图里那 8 篇无关来源就是这么来的。
     */
    private val TRANSIT_NAME_NOISE = listOf(
        "交通大学", "交通学院", "交通局", "交通银行", "交通运输部", "交通广播"
    )

    private fun hasTransitIntent(raw: String): Boolean {
        val cleaned = TRANSIT_NAME_NOISE.fold(raw) { acc, n -> acc.replace(n, " ") }
        return listOf("怎么去", "交通", "地铁", "公交", "高铁", "路线", "乘车", "打车", "换乘")
            .any { cleaned.contains(it) }
    }

    /**
     * 意图类别锚点：给关联度补「攻略 / 天气 / 票价」这类类别词，也给检索词改写用。
     * 它不再承担「要不要过滤」的职责——所有有内容词的问题都由
     * [relevanceOf] + [topicTerms] 统一精准判定，拿不准类别 ≠ 放行无关网页。
     */
    internal fun topicAnchors(raw: String): List<String> {
        val has = { ws: List<String> -> ws.any { raw.contains(it) } }
        val kind: List<String> = when {
            has(listOf("歌词", "LRC")) -> LYRIC_ANCHORS
            has(TRAVEL_PLAN_HINTS) || has(listOf("景点", "好玩", "攻略", "美食", "小吃"))
                -> TRAVEL_ANCHORS
            has(listOf("天气", "气温", "温度", "几度", "下雨", "带伞")) -> WEATHER_ANCHORS
            has(listOf("机票", "航班", "飞机票")) -> FLIGHT_ANCHORS
            has(listOf("酒店", "民宿", "住宿", "点评", "评价", "口碑", "评分")) -> HOTEL_ANCHORS
            has(listOf("门票", "开放时间", "预约", "营业时间")) -> TICKET_ANCHORS
            hasTransitIntent(raw) -> TRANSIT_ANCHORS
            else -> emptyList()
        }
        val city = CityCoords.detectIn(raw)
        // 没识别出类别时返回空：只表示「没有额外类别词加分」，
        // 不代表放行无关网页，内容比对交给 topicTerms / relevanceOf
        if (kind.isEmpty() && city.isNullOrBlank()) return emptyList()
        val anchors = LinkedHashSet<String>()
        anchors += kind
        if (!city.isNullOrBlank()) anchors += city
        subject(raw).split(' ')
            .map { it.trim() }.filter { it.length >= 2 }.take(2)
            .forEach { anchors += it }
        return anchors.toList()
    }

    /** 标题 / 摘要 / 站点命中任一锚点即视为同一主题（保留给旧调用点，实际判定走关联度） */
    internal fun isOnTopic(h: WebHit, anchors: List<String>): Boolean {
        if (anchors.isEmpty()) return true
        val text = h.title + " " + h.snippet + " " + h.site
        return anchors.any { text.contains(it, ignoreCase = true) }
    }

    /**
     * 只保留与问题同主题的网页，并写回关联度、按关联度从高到低排好。
     * 「一条不剩」时返回空列表 —— 绝不能把刚过滤掉的跨领域页再塞回来源列表。
     */
    internal fun keepOnTopic(hits: List<WebHit>, raw: String): List<WebHit> {
        // 只有连通用主题词都抠不出的纯闲聊才不比对内容；
        // 任何真实问题（含普通问句）都必须过关联度闸门
        if (topicTerms(raw).isEmpty() && topicAnchors(raw).isEmpty()) return hits
        return hits.map { h -> h to relevanceOf(h, raw) }
            .filter { (h, r) ->
                r >= MIN_RELEVANCE || h.title.startsWith("Bing 答案摘要")
            }
            .sortedByDescending { (_, r) -> r }
            .map { (h, r) -> h.copy(relevance = r) }
    }

    /** 给外部（来源列表 / 行程资料）用的过滤：只留关联度达标的网页 */
    fun relevantOnly(hits: List<WebHit>, query: String): List<WebHit> =
        hits.map { h -> h.copy(relevance = relevanceOf(h, query)) }
            .filter { it.relevance >= MIN_RELEVANCE }
            .sortedByDescending { it.relevance }

    /**
     * 第一层「原话直搜」是否已经拿到足够有用的内容：
     * 至少 2 条过关联度闸门的结果，或 1 条关联度很高的答案卡。
     * 不达标才进入拆词/最新消息等后续层，避免原句能搜到时白跑额外请求。
     */
    internal fun hasImportantContent(hits: List<WebHit>, query: String): Boolean {
        if (hits.isEmpty()) return false
        val relevant = hits.map { it to relevanceOf(it, query) }
            .filter { (_, r) -> r >= MIN_RELEVANCE }
        if (relevant.size >= 2) return true
        return relevant.firstOrNull()?.second?.let { it >= 0.62 } == true
    }

    /**
     * 人物/公司职务变动类事实断言：把长中文句拆成「人名 + 公司 + 职务」的可检索关键词。
     *
     * 实测「黄仁勋不在担任英伟达首席执行官」整句直搜，Bing 会把「黄」当成主实体，
     * 返回「黄（汉语文字）_百度百科」「黄色演变史」这类完全无关结果；拆成
     * 「黄仁勋 英伟达 CEO」后才返回 11/12 的辞任新闻。这里只在同时出现
     * 职务变动动词 + 职务名时才改写，避免把「我想辞职」这类个人表达误伤。
     */
    internal fun claimQuery(raw: String): String? {
        val hasVerb = ROLE_CHANGE_VERBS.any { raw.contains(it) }
        val hasTitle = ROLE_TITLES.any { raw.contains(it) }
        if (!hasVerb || !hasTitle) return null
        var s = raw
        // 「我听说/据说/网传」只是消息来源前缀，留在检索词里会把 Bing 的主实体带偏
        s = s.replace(Regex("(我听说|听说|据说|听闻|网传|有人說|有人说|我看到|看到)"), " ")
        ROLE_CHANGE_VERBS.forEach { s = s.replace(it, " ") }
        ROLE_TITLE_ABBR.forEach { (cn, en) -> s = s.replace(cn, " $en ") }
        ROLE_TITLES.forEach { s = s.replace(it, " $it ") }
        s = s.replace(Regex("[\\s，。！？、,.!?：:；;]+"), " ").trim()
        val drop = setOf(
            "宣布", "公告", "消息", "传闻", "据悉", "表示", "认为", "已经", "正式",
            "立即", "即刻", "即日", "目前", "最新", "重磅", "突发"
        )
        val chunks = s.split(' ').map { it.trim() }
            .filter { it.isNotBlank() && it !in drop }
            .distinct()
        return chunks.take(6).joinToString(" ").take(60).takeIf { it.isNotBlank() }
    }

    /**
     * 常见科技/商业人物与公司的英文名。人物/公司职务变动只靠中文搜索时，
     * 很容易被自媒体标题和「黄（汉语文字）」这类单字实体带偏；补一条英文查询，
     * 能拿到公司官网、通讯社和英文履历页。只映射公开人物/公司，不扩展隐私查询。
     */
    private val ENTITY_EN = linkedMapOf(
        "黄仁勋" to "Jensen Huang", "英伟达" to "NVIDIA",
        "马斯克" to "Elon Musk", "特斯拉" to "Tesla",
        "库克" to "Tim Cook", "苹果" to "Apple",
        "纳德拉" to "Satya Nadella", "微软" to "Microsoft",
        "皮查伊" to "Sundar Pichai", "谷歌" to "Google",
        "扎克伯格" to "Mark Zuckerberg", "Meta" to "Meta",
        "贝索斯" to "Jeff Bezos", "亚马逊" to "Amazon",
        "马云" to "Jack Ma", "阿里巴巴" to "Alibaba",
        "马化腾" to "Pony Ma", "腾讯" to "Tencent",
        "李彦宏" to "Robin Li", "百度" to "Baidu",
        "雷军" to "Lei Jun", "小米" to "Xiaomi",
        "任正非" to "Ren Zhengfei", "华为" to "Huawei",
        "王传福" to "Wang Chuanfu", "比亚迪" to "BYD",
        "曾毓群" to "Robin Zeng", "宁德时代" to "CATL",
        "张一鸣" to "Zhang Yiming", "字节跳动" to "ByteDance",
        "梁汝波" to "Liang Rubo", "黄峥" to "Colin Huang", "拼多多" to "PDD",
        "刘强东" to "Richard Liu", "京东" to "JD.com",
        "王兴" to "Wang Xing", "美团" to "Meituan",
        "李斌" to "William Li", "蔚来" to "NIO",
        "李想" to "Li Xiang", "理想汽车" to "Li Auto",
        "何小鹏" to "He Xiaopeng", "小鹏汽车" to "XPeng",
        "魏哲家" to "C.C. Wei", "台积电" to "TSMC",
        "李在镕" to "Lee Jae-yong", "三星" to "Samsung"
    )

    /** 常见公司官网域名，用于职务变动时的站内检索（只做补充，不替代公开搜索） */
    private val COMPANY_DOMAINS = linkedMapOf(
        "英伟达" to "nvidia.com", "特斯拉" to "tesla.com",
        "苹果" to "apple.com", "微软" to "microsoft.com",
        "谷歌" to "google.com", "亚马逊" to "amazon.com",
        "阿里巴巴" to "alibaba.com", "腾讯" to "tencent.com",
        "百度" to "baidu.com", "小米" to "mi.com",
        "华为" to "huawei.com", "比亚迪" to "byd.com",
        "宁德时代" to "catl.com", "字节跳动" to "bytedance.com",
        "拼多多" to "pddholdings.com", "京东" to "jd.com",
        "美团" to "meituan.com", "蔚来" to "nio.com",
        "理想汽车" to "lixiang.com", "小鹏汽车" to "xpeng.com",
        "台积电" to "tsmc.com", "三星" to "samsung.com"
    )

    /** 用户只报人名、没报公司时，用公开的「人名→公司」关系补官方域名 */
    private val PERSON_COMPANY = linkedMapOf(
        "黄仁勋" to "英伟达", "马斯克" to "特斯拉", "库克" to "苹果",
        "纳德拉" to "微软", "皮查伊" to "谷歌", "扎克伯格" to "Meta",
        "贝索斯" to "亚马逊", "马云" to "阿里巴巴", "马化腾" to "腾讯",
        "李彦宏" to "百度", "雷军" to "小米", "任正非" to "华为",
        "王传福" to "比亚迪", "曾毓群" to "宁德时代", "张一鸣" to "字节跳动",
        "梁汝波" to "字节跳动", "黄峥" to "拼多多", "刘强东" to "京东",
        "王兴" to "美团", "李斌" to "蔚来", "李想" to "理想汽车",
        "何小鹏" to "小鹏汽车", "魏哲家" to "台积电", "李在镕" to "三星"
    )

    /**
     * 只报人名时自动补公司名。否则「黄仁勋 CEO」会被 Bing 拆成「黄」字百科，
     * 「Jensen Huang」也会撞上同名的工业洗衣品牌 JENSEN-GROUP。
     */
    private fun claimWithCompany(claim: String): String {
        if (COMPANY_DOMAINS.keys.any { claim.contains(it) }) return claim
        PERSON_COMPANY.entries.firstOrNull { claim.contains(it.key) }?.let { (person, company) ->
            return claim.replace(person, "$person $company")
        }
        return claim
    }

    private fun englishClaim(claim: String, withYear: Boolean): String? {        var s = claim
        var replaced = false
        ENTITY_EN.forEach { (cn, en) ->
            if (s.contains(cn)) {
                s = s.replace(cn, en)
                replaced = true
            }
        }
        if (!replaced) return null
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val q = s.replace(Regex("\\s+"), " ").trim()
        return (if (withYear) "$q $year" else q).take(90).takeIf { it.isNotBlank() }
    }

    private fun officialClaim(claim: String): String? {
        val domain = officialDomainFor(claim) ?: return null
        val english = englishClaim(claim, withYear = false) ?: claim
        return "site:$domain $english"
    }

    private fun officialDomainFor(claim: String): String? {
        val expanded = claimWithCompany(claim)
        return COMPANY_DOMAINS.entries.firstOrNull { expanded.contains(it.key) }?.value
    }

    private fun isOfficialHit(hit: WebHit, domain: String): Boolean {
        val host = hostOf(hit.url)
        return host == domain || host.endsWith(".$domain")
    }

    /**
     * 职务变动断言的补充检索角度。保持中文「最新消息」在前，英文/官方域名只做补充；
     * 不把「官方公告」「辟谣」这类词硬塞进中文查询，Bing 会把它们和「黄」单字实体
     * 拼成百科页，反而更差。
     */
    private fun claimQueries(claim: String): List<String> {
        val expanded = claimWithCompany(claim)
        val out = linkedSetOf("$expanded 最新消息", expanded)
        englishClaim(expanded, withYear = true)?.let { out += it }
        officialClaim(expanded)?.let { out += it }
        return out.toList()
    }

    /**
     * 检索词计划：**普通问题的第一条是用户发送的原话**，其余才是垂直/改写/限定角度。
     *
     * 原句直搜是第一步，绝不在开头就用规则或模型猜出来的「更好的词」替换掉它——
     * 「重庆交通大学校长」被换成「重庆旅游攻略 行程」那次翻车就是这么来的。
     * 但单一原句确实会被 Bing 的实体识别带偏（地名先给百科、旅行规划先给法规文件），
     * 所以垂直词、UGC 站点限定仍作为补充角度并发进候选池，最终由关联度闸门裁决。
     *
     * 职务变动类事实断言（如「黄仁勋不在担任英伟达首席执行官」）整句直搜时，
     * Bing 会把「黄」按单字拆成百科/姓氏页；这类句子仍然**先搜原句**，只有原句
     * 没有足够相关内容时，[search] 才用 [claimQuery] 的「人名 公司 CEO 最新消息」兜底。
     */
    internal fun planQueries(
        raw: String,
        extraQueries: List<String> = emptyList()
    ): List<String> {
        val base = sanitize(keywordize(raw))
        if (base.isBlank()) return emptyList()
        val has = { words: List<String> -> words.any { raw.contains(it) } }
        val subj = subject(raw)
        val year = Regex("\\d{4}").find(raw)?.value
            ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()
        val city = CityCoords.detectIn(raw)
        val travel = !city.isNullOrBlank() && !has(listOf("歌词", "LRC")) &&
            has(TRAVEL_PLAN_HINTS)
        val days = Regex("(\\d+)\\s*[日天]").find(raw)?.groupValues?.get(1)
        // 第一层永远是用户原话：只做精确坐标隐私清洗 + 60 字上限，一个字都不改写。
        // 只有这层没搜到足够相关的内容时，才会在 [search] 里启动拆词/最新消息等后续层。
        val claim = claimQuery(raw)
        val primary = sanitize(raw).ifBlank { base }
        // 旅行规划词从「主词」降级为「补充角度」：原句仍会先搜一遍，
        // 改写词负责把真正有一手攻略的站点拉进候选池
        val travelRewrite = if (travel) {
            "$city${if (days != null) "${days}日游" else "旅游"}攻略 行程"
        } else ""
        val extras: List<String> = if (claim != null) {
            // 中文原句先跑；下面四条是补角度，不替换原句：
            // 1) 最新消息（中文新闻） 2) 拆好的「人名 公司 职务」
            // 3) 英文名 + 公司英文名（官方/通讯社多用英文） 4) 官方域名站内检索
            claimQueries(claim)
        } else when {
            has(listOf("歌词")) ->
                listOf("$base 完整版", "$subj 歌词")
            travel -> listOf(
                travelRewrite,
                // 官方文旅/景区公告是门票、开放时间、预约政策的一手来源
                "$city 文旅局 景区 官方 公告 门票 预约",
                // 携程/去哪儿补齐景点开放时间、票价与交通信息，再交给层级清洗裁决
                "site:ctrip.com $city 景点 门票 开放时间",
                // 知乎长文讲避坑、小红书讲打卡与实时排队、抖音讲路线与花费；
                // 混合角度一次把三类 UGC 拉进候选池
                "$city 旅游攻略 知乎 小红书 抖音 避坑",
                "site:zhihu.com $city 旅游攻略 必去景点",
                "site:xiaohongshu.com $city ${days ?: "3"}日游 攻略",
                // 路线、景点间车程和到达方式放在同一条：行程能不能落地主要看这段
                "$city 必去景点 路线 推荐 交通 车程"
            )
            has(listOf("机票", "航班", "飞机票", "机票价格")) ->
                listOf("特价机票查询 机票价格", "$subj 航班 时刻表")
            has(listOf("天气", "气温", "温度", "几度", "下雨", "带伞")) -> {
                val city = CityCoords.detectIn(raw).orEmpty()
                if (city.isBlank()) listOf("天气预报查询")
                else listOf("${city}天气预报", "site:weather.com.cn $city 天气")
            }
            has(listOf("门票", "票价", "开放时间", "预约", "营业时间")) ->
                listOf("$subj 门票预约 开放时间", "site:ctrip.com $subj 门票")
            // 带上地名那条容易被 Bing 的实体优先绑架成「XX 旅游攻略」，
            // 再补一条不带地名的纯意图词，把携程/点评类真实站点拉进候选
            has(listOf("酒店", "民宿", "住宿", "评价", "口碑", "评分")) ->
                listOf("$subj 酒店点评 评分", "site:ctrip.com $subj 酒店 点评")
            has(listOf("景点", "好玩", "攻略", "美食", "小吃")) ->
                listOf(
                    // 攻略类问题的真正干货在 UGC：知乎长文讲避坑、小红书讲打卡
                    // 与实时排队、抖音/马蜂窝讲路线与花费，单靠 Bing 整句只会返回百科
                    "$subj 攻略 知乎 小红书 抖音 避坑",
                    "site:zhihu.com $subj 攻略 避坑",
                    "site:xiaohongshu.com $subj 游玩 攻略"
                )
            hasTransitIntent(raw) ->
                listOf("$subj 怎么去 交通", "$subj 公交 地铁 路线")
            has(listOf("汇率", "换汇", "兑换", "外币")) ->
                listOf("$base 今天", "美元 人民币 汇率 最新")
            has(listOf("新闻", "公告", "通知")) ->
                listOf("$subj 最新消息", "$base 今天")
            // 任何普通问题都补一条「去掉疑问/虚词的精准检索词」：主句被 Bing
            // 带偏（如「Python 列表去重的方法」只回官网首页）时还有一条精准角度，
            // 同时它也是过滤空了之后的 fallback 词
            else -> listOf(preciseQuery(raw), "$base 最新", "$subj $year")
        }
        // 模型/检索路由给的补充词必须仍与用户原句同主题，不能让它把话题带跑
        val routed = extraQueries.map { sanitize(it) }
            .filter { it.isNotBlank() && queryOnTopic(it, raw) }
            .take(2)
        // 上限放宽到 8 条：行程规划要同时覆盖官方/OTA/知乎/小红书/路线交通；
        // 其余档位由 SearchProfile.angles 决定实际跑几条，不会因此变慢。
        return (listOf(primary) + extras + routed)
            .map { it.trim() }.distinct().filter { it.isNotBlank() }.take(8)
    }

    /** 从问句里抠出主体（地名/景区/酒店名），去掉意图词和客套话 */
    private fun subject(raw: String): String {
        var s = keywordize(raw)
        listOf(
            "多少钱", "价格", "票价", "门票", "开放时间", "营业时间", "预约", "评价",
            "口碑", "评分", "酒店", "民宿", "住宿", "景点", "攻略", "美食", "小吃",
            "天气", "气温", "最新", "现在", "今天", "明天", "怎么样", "如何",
            "值得去", "推荐", "查一下", "搜一下", "机票", "航班", "网上",
            "歌词", "完整版"
        ).forEach { s = s.replace(it, " ") }
        s = s.replace(Regex("\\s+"), " ").trim()
        // 保留前两个词："晴天 周杰伦" 不能只剩 "晴天"，"重庆 交通大学" 也不能只剩 "重庆"
        val parts = s.split(" ").filter { it.isNotBlank() }
        return parts.take(2).joinToString(" ").take(16).ifBlank { s.take(16) }
    }

    /** 正文页缓存：同一 URL 在 TTL 内重复取用，直接复用，不再发请求（并发安全） */
    private val bodyCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()

    /** 网页正文：先按编码解出 HTML，再抽「信息密度最高的一段」，而不是第一个数字窗口 */
    private fun extractText(url: String): String {
        val now = System.currentTimeMillis()
        bodyCache[url]?.let { (at, text) ->
            if (now - at < CACHE_TTL_MS && text.isNotEmpty()) return text
        }
        val resp = httpGetRaw(url, 4_000) ?: return ""
        val raw = runCatching { String(resp.second, charsetOf(resp.first, resp.second)) }
            .getOrDefault("")
        if (raw.isBlank()) return ""
        val text = extractMainText(raw)
        if (text.isNotBlank()) {
            if (bodyCache.size >= 64) bodyCache.clear()
            bodyCache[url] = now to text
        }
        return text
    }

    /**
     * 从整页 HTML 里挑正文：去掉导航/页脚/脚本后按块切成行，再聚成 ~900 字的窗口，
     * 按「数字 + 价格/天气/营业等事实词」密度选最优窗口。
     * 导航、评论区、免责声明因此不会挤掉真正的信息段。
     */
    internal fun extractMainText(html: String): String {
        val lines = htmlChunks(html)
        if (lines.isEmpty()) return ""
        val maxLen = 900
        if (lines.sumOf { it.length } <= maxLen) return lines.joinToString("\n")
        val windows = mutableListOf<String>()
        val sb = StringBuilder()
        lines.forEach { line ->
            if (sb.length + line.length > maxLen && sb.isNotEmpty()) {
                windows += sb.toString()
                sb.setLength(0)
            }
            sb.appendLine(line)
        }
        if (sb.isNotEmpty()) windows += sb.toString()
        return windows.maxByOrNull { bodyQuality(it) }?.trim().orEmpty()
    }

    private fun htmlChunks(html: String): List<String> {
        var s = html.replace(
            Regex("(?is)<script.*?</script>|<style.*?</style>|<noscript.*?</noscript>|<nav.*?</nav>|<header.*?</header>|<footer.*?</footer>|<form.*?</form>"),
            " "
        )
        s = s.replace(
            Regex("(?is)<br\\s*/?>|</?(p|div|li|h\\d|tr|td|section|article|blockquote)[^>]*>"),
            "\n"
        )
        s = s.replace(Regex("(?s)<[^>]+>"), " ")
        s = decodeEntities(s)
        return s.split('\n').asSequence()
            .map { it.replace(Regex("[\\t\\u00A0 ]+"), " ").trim() }
            .filter { it.length >= 12 && it.count { c -> c.isLetterOrDigit() } >= 6 }
            .toList()
    }

    /** 问句 → 搜索引擎友好关键词，去掉客套话但保留"多少钱/最新/评价"这类限定词 */
    fun keywordize(text: String): String {
        var q = text.trim()
        listOf(
            "帮我", "帮忙", "请问", "麻烦你", "麻烦", "我想知道", "我想", "想问",
            "请", "告诉我", "一下", "呢", "吧", "吗", "？", "?", "，", ",", "。"
        ).forEach { q = q.replace(it, " ") }
        return q.replace(Regex("\\s+"), " ").trim().take(60)
    }

    /** 隐私清洗：精确坐标、门牌级数字串一律不外发云端搜索引擎 */
    private fun sanitize(q: String): String = q
        .replace(Regex("\\d{1,3}\\.\\d{4,}"), " ")
        .replace(Regex("[NSWE]\\s?\\d{1,3}\\.\\d{2,}[°]?"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(60)

    // ---------------------------------------------------------------- 搜索源

    /**
     * 单个检索角度的取数：RSS 正常（≥4 条）就直接用；只回几条通常意味着被限流/截断，
     * 这时再补 HTML 结果页（还带答案卡和新闻卡），避免一个角度只贡献 1~2 条。
     */
    private fun sourcesFor(query: String, first: Int = 0): List<WebHit> {
        val rss = bingRss(query, first)
        if (rss.size >= 4) return rss
        val html = bingHtml(query, first)
        return (rss + html).distinctBy { normalizeUrl(it.url).ifBlank { it.title } }
    }

    private fun bingRss(query: String, first: Int = 0): List<WebHit> = runCatching {
        val url = "https://cn.bing.com/search?q=" + URLEncoder.encode(query, "UTF-8") +
            "&format=rss&mkt=zh-CN" + if (first > 0) "&first=$first" else ""
        parseBingRss(httpGet(url, 9_000))
    }.getOrDefault(emptyList())

    internal fun parseBingRss(xml: String): List<WebHit> {
        if (xml.isBlank()) return emptyList()
        return Regex("<item[^>]*>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).withIndex().mapNotNull { (i, m) ->
            val block = m.groupValues[1]
            fun tag(name: String): String =
                Regex("<$name[^>]*>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL)
                    .find(block)?.groupValues?.getOrNull(1).orEmpty()
            val title = clean(tag("title"))
            if (title.isBlank()) return@mapNotNull null
            val link = realUrl(clean(tag("link")))
            WebHit(
                title = title.take(90),
                snippet = clean(tag("description")).take(MAX_SNIPPET),
                url = link,
                site = hostOf(link),
                // Bing RSS 的 item 顺序就是它给出的相关度排名
                rank = i + 1,
                publishedAt = clean(tag("pubDate")).take(40)
            )
        }.toList()
    }

    /**
     * HTML 结果页兜底：按结果卡起点切块（普通结果 b_algo、新闻卡 b_news），
     * 另外把 Bing 直接给出的答案卡（b_ans）也捞出来——天气、汇率、价格这类问题，
     * 答案卡本身就是最准的一手摘要，以前完全丢掉。
     */
    private fun bingHtml(query: String, first: Int = 0): List<WebHit> = runCatching {
        val searchUrl = "https://cn.bing.com/search?q=" + URLEncoder.encode(query, "UTF-8") +
            "&setlang=zh-CN&ensearch=0" + if (first > 0) "&first=$first" else ""
        parseBingHtml(httpGet(searchUrl, 9_000), searchUrl)
    }.getOrDefault(emptyList())

    internal fun parseBingHtml(html: String, searchUrl: String): List<WebHit> {
        if (html.isBlank()) return emptyList()
        val out = mutableListOf<WebHit>()
        val starts = Regex("<li class=\"(?:b_algo|b_news)[\"\\s]")
            .findAll(html).map { it.range.first }.toList()
        starts.forEachIndexed { i, start ->
            val end = if (i + 1 < starts.size) starts[i + 1] else minOf(html.length, start + 6_000)
            val block = html.substring(start, end)
            // Bing 的标题链接有时包在 <h2> 里，有时 <a> 在 <h2> 外面（<a class="tilk"><h2>…</h2></a>），
            // 所以标题和链接分开取，不能再要求 <h2><a> 这种嵌套结构
            val titleHtml = Regex("<h2[^>]*>(.*?)</h2>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.getOrNull(1) ?: return@forEachIndexed
            val href = Regex("<a[^>]+href=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.getOrNull(1).orEmpty()
            val snippetHtml = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.getOrNull(1).orEmpty()
            val title = clean(titleHtml)
            if (title.isBlank()) return@forEachIndexed
            val link = realUrl(unescape(href))
            if (link.isBlank()) return@forEachIndexed
            out += WebHit(
                title = title.take(90),
                snippet = clean(snippetHtml).take(MAX_SNIPPET),
                url = link,
                site = hostOf(link),
                // HTML 结果卡的位次同样是 Bing 的相关度排名
                rank = i + 1
            )
        }
        Regex("<li class=\"b_ans[\"\\s]").findAll(html).take(2).forEach { m ->
            val end = minOf(html.length, m.range.first + 4_000)
            val text = clean(html.substring(m.range.first, end))
            if (text.length < 30) return@forEach
            if (!Regex("℃|度|元|价格|评分|汇率|开放|预约|\\d{2,}").containsMatchIn(text)) return@forEach
            out += WebHit(
                title = "Bing 答案摘要",
                snippet = text.take(MAX_SNIPPET),
                url = searchUrl,
                site = "cn.bing.com"
            )
        }
        return out
    }

    /** Bing HTML 页的结果链接是 /ck/a 跳转，真实地址藏在 u=a1<base64url> 里 */
    private fun realUrl(href: String): String {
        if (!href.contains("bing.com/ck/a")) return href
        val encoded = Regex("[?&]u=a1([^&]+)").find(href)?.groupValues?.getOrNull(1)
            ?: return href
        return runCatching {
            val padded = encoded.replace('-', '+').replace('_', '/')
                .let { it + "=".repeat((4 - it.length % 4) % 4) }
            String(Base64.decode(padded, Base64.URL_SAFE), Charsets.UTF_8)
                .takeIf { it.startsWith("http") } ?: href
        }.getOrDefault(href)
    }

    private fun hostOf(href: String): String = runCatching {
        URL(href).host.orEmpty().removePrefix("www.")
    }.getOrDefault("")

    private fun httpGet(url: String, timeoutMs: Int): String {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                WebIncognito.applyTo(this)
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } catch (e: Exception) {
            ""
        } finally {
            conn?.disconnect()
        }
    }

    /** 正文抓取要自己判编码：中文站点大量仍是 GBK，统一按 UTF-8 读会整页乱码 */
    private fun httpGetRaw(url: String, timeoutMs: Int): Pair<String?, ByteArray>? {
        // 移动 UA 被 403 时，换桌面 UA 往往就能过——携程/知乎/部分地方站都按 UA 拦
        listOf(UA, DESKTOP_UA).forEach { ua ->
            var conn: HttpURLConnection? = null
            try {
                val budget = (timeoutMs / 2).coerceAtLeast(3_000)
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = budget
                    readTimeout = budget
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", ua)
                    setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
                    setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                    setRequestProperty("Referer", "https://cn.bing.com/")
                    WebIncognito.applyTo(this)
                }
                if (conn.responseCode == 200) {
                    val bytes = readAtMost(conn.inputStream, 400_000)
                    return conn.contentType to bytes
                }
            } catch (_: Exception) {
                // 换下一个 UA 再试
            } finally {
                conn?.disconnect()
            }
        }
        return null
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
        // 先用 latin1 只为读 <meta charset>，不会因为解错编码抛异常
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

    /** 去标签 / CDATA / HTML 实体，压成单行纯文本 */
    private fun clean(raw: String): String {
        var s = raw
            .replace(Regex("(?is)<!\\[CDATA\\[(.*?)\\]\\]>"), "$1")
            .replace(Regex("(?is)<script.*?</script>|<style.*?</style>"), " ")
            .replace(Regex("(?s)<[^>]+>"), " ")
        s = decodeEntities(s)
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private fun decodeEntities(raw: String): String {
        var s = raw
        repeat(2) {
            s = s.replace("&nbsp;", " ").replace("&#160;", " ")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&amp;", "&")
            s = Regex("&#(\\d+);").replace(s) { m ->
                m.groupValues[1].toIntOrNull()?.takeIf { it in 0..0xFFFF }
                    ?.let { Char(it).toString() } ?: m.value
            }
            s = Regex("&#x([0-9a-fA-F]+);").replace(s) { m ->
                m.groupValues[1].toIntOrNull(16)?.takeIf { it in 0..0xFFFF }
                    ?.let { Char(it).toString() } ?: m.value
            }
        }
        return s
    }

    /** href 里的 &amp; 之类要还原，不然跳转链接解析不出来 */
    private fun unescape(s: String): String = s
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
}

/**
 * 本轮检索的「证据暂存区」。
 *
 * 联网结果可能来自两条完全不同的路径：工具链里的 `AiTools.webSearch`（模型自己决定去搜）
 * 和助手页的自动检索。两条路径都要能把「这轮到底引用了哪些网页」交回给气泡底部展示，
 * 所以这里只做一件事：谁搜到结果就记一笔，助手页在回答落定时取走。
 */
object SearchEvidence {
    @Volatile
    private var hits: List<WebHit> = emptyList()

    fun remember(list: List<WebHit>) {
        if (list.isNotEmpty()) hits = list
    }

    fun current(): List<WebHit> = hits

    fun clear() {
        hits = emptyList()
    }
}
