package com.example.my.data.search

import com.example.my.data.WebHit
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.pow

/**
 * 搜索清洗管线（算法第 ① 步，独立模块）。
 *
 * 四层，全部在本地完成、不额外联网，单次耗时是毫秒级（候选一般 8~20 条）：
 *
 *  L0 归一化：URL 去追踪参数、统一 host，得到"同一页面"的唯一键；
 *  L1 硬过滤：广告/推广、内容农场、标题党、关键词堆砌、空摘要、重复 URL；
 *  L2 去重与转载聚类：SimHash(64bit, 3-gram) + 3-gram Jaccard 双判据，
 *     同文转载保留最权威的一条，其余标 mirrorOf，**不计入多源一致性**（防假共识）；
 *  L3 来源分层：域名 → L1..L5 权重；
 *  L4 相关性 + 时效 + 打分：S = 0.40·层级 + 0.30·相关 + 0.15·时效 + 0.15·多样性。
 *
 * 只做"取舍与排序"，不做事实判断 —— 事实判断在 [EvidenceAdjudicator]。
 * 这个类是纯 Kotlin（不依赖 Android），可以在 JVM 单测里直接跑。
 */
object SearchCleaner {

    data class Config(
        /** 低于这个相关性直接剔除（保底至少留 1 条最好的） */
        val minRelevance: Double = 0.30,
        /** 同一站点最多保留几条（单一搜索源下，防止一个站点垄断前几名） */
        val maxPerSite: Int = 2,
        /** SimHash 汉明距离阈值 */
        val dupHamming: Int = 3,
        /** 3-gram Jaccard 判重阈值 */
        val dupJaccard: Double = 0.72,
        /** 包含度阈值：长文被追加"责任编辑/来源/版权"等模板尾巴时，Jaccard 会被稀释，
         *  改用"短文有多少比例的片段被长文覆盖"来兜住这类转载 */
        val dupContainment: Double = 0.82,
        /** 不同域名的同题复述，视为转载的标题相似度阈值 */
        val mirrorTitleJaccard: Double = 0.60,
        /** 标题不同但正文高度重合，同样判为转载（同一篇通稿换了标题） */
        val mirrorTextJaccard: Double = 0.55,
        /** 最终进入上下文的证据条数上限 */
        val maxEvidence: Int = 10
    )

    data class Result(
        val evidence: List<Evidence>,
        val stats: CleanStats,
        val sensitivity: Sensitivity
    )

    /** 内容农场 / 文档搬运站 / 明显垃圾源：直接 L5 剔除，不进入任何计数 */
    private val BLACKLIST = setOf(
        "book118.com", "renrendoc.com", "doc88.com", "docin.com", "taodocs.com",
        "51wendang.com", "wendangku.net", "mbalib.com", "docin.cn", "360doc.com",
        "jb51.net", "chinaz.com", "oschina.net", "cnblogs.com", "csdn.net"
    )

    /** 广告 / 推广 / 软文标记：出现在标题或摘要里就剔除 */
    private val AD_MARKERS = listOf("广告", "推广", "赞助", "软文", "特约", "商务合作", "立即购买", "限时抢购")

    /** 标题党 / 情绪化标题：典型特征是空信息量 + 强诱导 */
    private val CLICKBAIT = listOf(
        "震惊", "惊呆", "不看后悔", "太可怕", "疯传", "速看", "揭秘", "万万没想到",
        "必看", "绝密", "内幕", "史上最", "惊了", "吓人", "别再", "99%的人"
    )

    /** 高时效意图：这类问题按小时/天衰减 */
    private val REALTIME_WORDS = listOf("实时", "现在", "刚刚", "客流", "排队", "封路", "限流", "延误")
    private val DAILY_WORDS = listOf("天气", "气温", "温度", "几度", "下雨", "带伞", "空气", "风力", "新闻", "公告")
    private val WEEKLY_WORDS = listOf("价格", "票价", "多少钱", "费用", "机票", "机票价格", "酒店价格", "汇率", "预算", "优惠")
    private val MONTHLY_WORDS = listOf("门票", "预约", "开放时间", "营业时间", "几点", "订票", "攻略", "交通", "路线")
    private val STABLE_WORDS = listOf("历史", "介绍", "由来", "文化", "简介", "百科")

    /** L1：官方 / 一手 / 权威机构 / 同行评审 / 官方数据接口 */
    private val L1_HOSTS = listOf(
        "gov.cn", "edu.cn", "ac.cn", "org.cn",
        "weather.com.cn", "nmc.cn", "cma.cn", "12306.cn", "caac.gov.cn",
        "mct.gov.cn", "nhc.gov.cn", "stats.gov.cn", "pbc.gov.cn", "customs.gov.cn",
        "who.int", "un.org", "wmo.int", "icao.int", "iata.org",
        // 公司官网/一手公告：人物与公司职务变动必须优先核对这一类页面
        "nvidia.com", "tesla.com", "apple.com", "microsoft.com", "google.com",
        "amazon.com", "alibaba.com", "tencent.com", "baidu.com", "mi.com",
        "huawei.com", "byd.com", "catl.com", "bytedance.com", "jd.com",
        "meituan.com", "nio.com", "lixiang.com", "xpeng.com", "tsmc.com",
        "samsung.com", "sec.gov", "investor.gov"
    )

    /** L2：主流媒体 / 知名百科 / 行业头部 OTA */
    private val L2_HOSTS = listOf(
        "people.com.cn", "xinhuanet.com", "news.cn", "cctv.com", "chinadaily.com.cn",
        "thepaper.cn", "caixin.com", "yicai.com", "bjnews.com.cn", "36kr.com",
        "sina.com.cn", "163.com", "qq.com", "ifeng.com", "sohu.com", "guancha.cn",
        "baike.baidu.com", "wikipedia.org", "britannica.com",
        "ctrip.com", "qunar.com", "mafengwo.cn", "dianping.com", "meituan.com",
        "trip.com", "tuniu.com", "ly.com", "tripadvisor.com", "lonelyplanet.com",
        "amap.com", "baidu.com",
        // 国际通讯社/财经媒体：英文名查询的主要落点
        "reuters.com", "apnews.com", "bloomberg.com", "cnbc.com", "wsj.com",
        "ft.com", "bbc.com", "nytimes.com", "theverge.com", "techcrunch.com",
        "wired.com", "arstechnica.com", "businessinsider.com", "forbes.com",
        "scmp.com"
    )

    /** L3：垂直站 / 博客 / 社区（可参考，但单独一条不足以成结论） */
    private val L3_HOSTS = listOf(
        "zhihu.com", "xiaohongshu.com", "bilibili.com", "douban.com", "weibo.com",
        "douyin.com", "jianshu.com", "toutiao.com", "36dianping.com", "qyer.com",
        "mafengwo.com", "tieba.baidu.com", "zhidao.baidu.com", "wenku.baidu.com",
        "reddit.com", "quora.com", "medium.com", "youtube.com", "instagram.com"
    )

    /** 明显低质：UGC 问答 / 聚合抓取站，只能用来看"还有这种说法" */
    private val L4_HOSTS = listOf(
        "baijiahao.baidu.com", "hao123.com", "58.com", "ganji.com", "yiqi.com",
        "qq.com.cn", "so.com", "sm.cn", "uc.cn", "toutiao.com.cn"
    )

    /** 主入口：把原始搜索命中清洗成证据集 */
    fun clean(
        query: String,
        raw: List<WebHit>,
        bodies: Map<String, String> = emptyMap(),
        config: Config = Config(),
        sensitivity: Sensitivity = sensitivityOf(query)
    ): Result {
        val dropped = mutableMapOf<String, Int>()
        fun drop(reason: String, n: Int = 1) = dropped.merge(reason, n, Int::plus)

        val qTokens = tokens(query)
        val keyPhrase = query.replace(Regex("\\s+"), "").take(24)

        // ---------- L0 归一化 + L1 硬过滤 ----------
        val seenUrls = HashSet<String>()
        val candidates = mutableListOf<Evidence>()
        for (h in raw) {
            val title = h.title.trim()
            val snippet = h.snippet.trim()
            // 抓取的整页正文先在本地压缩：去导航/版权/推荐段落、近重复段，
            // 只保留与问题相关的高相关段，避免把整页 HTML 噪声塞进事实裁决与模型上下文
            val body = compressBody(qTokens, keyPhrase, bodies[h.url].orEmpty().trim())
            val site = hostOf(h.url).ifBlank { h.site.trim() }
            if (title.isBlank()) { drop("空标题"); continue }
            val normKey = urlKey(h.url).ifBlank { "$site|${title.take(30)}" }
            if (!seenUrls.add(normKey)) { drop("重复URL"); continue }
            if (isAd(title, snippet, h.url, site)) { drop("广告推广"); continue }
            if (isBlacklisted(site)) { drop("垃圾站"); continue }
            if (isClickbait(title)) { drop("标题党"); continue }
            if (isKeywordStuffed(title)) { drop("关键词堆砌"); continue }
            if (snippet.isBlank() && body.isBlank()) { drop("空摘要"); continue }

            val rel = relevance(query, qTokens, keyPhrase, title, snippet, body)
            if (rel < config.minRelevance) { drop("低相关"); continue }

            val tier = tierOf(site)
            if (tier == SourceTier.L5) { drop("可疑来源"); continue }
            val (published, daysAgo) = detectDate(title + " " + snippet + " " + body)
            val fresh = freshness(daysAgo, sensitivity)

            candidates += Evidence(
                id = 0,
                title = title,
                snippet = snippet,
                url = h.url,
                site = site,
                tier = tier,
                relevance = rel,
                freshness = fresh,
                score = 0.0,
                published = published,
                body = body
            )
        }

        // ---------- L2 去重 / 转载聚类 ----------
        val kept = mutableListOf<Evidence>()
        for (c in candidates.sortedByDescending { tierWeight(it.tier) }) {
            val dup = kept.firstOrNull { isDuplicate(it, c, config) }
            if (dup != null) {
                drop("内容重复")
                continue
            }
            kept += c
        }
        val mirrors = mutableListOf<Evidence>()
        for (c in kept) {
            // 已被保留的，再找"不同域名但同题复述"：标成转载镜像，保留展示，不计入一致性
            val same = kept.firstOrNull { it.site != c.site && it !== c && isMirror(it, c, config) }
            if (same != null) mirrors += c.copy(mirrorOf = 0) // 先占位，id 在排序后回填
        }

        // ---------- L3/L4 打分 + 站点配额 ----------
        var scored = kept.map { c ->
            c.copy(score = scoreOf(c, diversity = 1.0))
        }.sortedByDescending { it.score }

        val perSite = HashMap<String, Int>()
        scored = scored.map { c ->
            val used = perSite[c.site] ?: 0
            perSite[c.site] = used + 1
            if (used >= config.maxPerSite) c.copy(score = c.score * 0.65, relevance = c.relevance * 0.9)
            else c
        }.sortedWith(
            compareByDescending<Evidence> { siteQuotaOk(it, perSite, config) }
                .thenByDescending { it.score }
        )
        scored = scored.take(config.maxEvidence)

        // ---------- id 回填：id 必须与最终顺序一致，模型引用 [n] 才不会错位 ----------
        val idByKey = HashMap<String, Int>()
        val withIds = scored.mapIndexed { i, c ->
            idByKey[keyOf(c)] = i + 1
            c.copy(id = i + 1)
        }
        // 回填 mirrorOf：同题复述指向分数更高的那条
        val mirrorIds = HashMap<String, Int>()
        for (m in mirrors) {
            val same = withIds.firstOrNull {
                it.site != m.site && it.title != m.title && isMirror(it, m, config)
            }
            if (same != null) mirrorIds[keyOf(m)] = same.id
        }
        val finalList = withIds.map { c ->
            val mid = mirrorIds[keyOf(c)] ?: 0
            // 只有确实找到"另一条同题证据"才算转载；自己不能当自己的镜像
            if (mid != 0 && mid != c.id) c.copy(mirrorOf = mid) else c.copy(mirrorOf = 0)
        }

        val mirrorCount = finalList.count { it.mirrorOf > 0 }
        return Result(
            evidence = finalList,
            stats = CleanStats(
                rawCount = raw.size,
                keptCount = finalList.size,
                mergedMirrors = mirrorCount,
                dropped = dropped.toMap(),
                halfLifeDays = sensitivity.halfLifeDays
            ),
            sensitivity = sensitivity
        )
    }

    // ---------------------------------------------------------------- 正文压缩

    /** 抓取正文的本地压缩：去模板噪声 + 近重复段 + 只留与问题相关的段落。纯计算，不联网。 */
    internal fun compressBody(
        qTokens: Set<String>,
        keyPhrase: String,
        raw: String,
        maxChars: Int = BODY_MAX_CHARS
    ): String {
        if (raw.isBlank()) return ""
        val paragraphs = raw.split(Regex("\n+|\\s{3,}"))
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length >= BODY_MIN_PARA_CHARS && !isBoilerplate(it) }
        if (paragraphs.isEmpty()) return ""

        val seenGram = HashSet<Set<String>>()
        val kept = mutableListOf<String>()
        var used = 0
        // 按段落相关度排序取高相关段；同主题近重复段只留第一条（保留更具体、带数字的）
        val ranked = paragraphs.map { p ->
            p to paragraphRelevance(p, qTokens, keyPhrase)
        }.sortedByDescending { it.second }
        for ((p, rel) in ranked) {
            if (rel < BODY_MIN_REL && kept.isNotEmpty()) continue
            val g = grams(p, 3)
            if (g.isNotEmpty() && seenGram.any { jaccard(g, it) >= BODY_DUP_JACCARD }) continue
            seenGram += g
            if (used + p.length > maxChars && kept.isNotEmpty()) break
            kept += p
            used += p.length
        }
        // 按阅读顺序拼回，避免相关度排序把网页语序打乱
        val ordered = paragraphs.filter { it in kept }
        return ordered.joinToString("。").take(maxChars)
    }

    /** 段落级相关度：词面命中 + 整句关键词命中 + 数字/事实词加成，与句子裁决口径一致 */
    private fun paragraphRelevance(p: String, qTokens: Set<String>, keyPhrase: String): Double {
        if (qTokens.isEmpty()) return 0.5
        val tTokens = tokens(p)
        val overlap = qTokens.count { tTokens.contains(it) }.toDouble() / qTokens.size
        val phraseHit = if (keyPhrase.length >= 4 &&
            p.replace(Regex("\\s+"), "").contains(keyPhrase, ignoreCase = true)
        ) 1.0 else 0.0
        val factBonus = if (Regex("\\d|元|℃|门票|价格|票价|评分|营业|开放|预约|地址|公里|小时").containsMatchIn(p)) 0.10 else 0.0
        return (0.6 * overlap + 0.25 * phraseHit + factBonus).coerceIn(0.0, 1.0)
    }

    /** 导航 / 版权 / 推荐阅读 / 登录引导等页面模板噪声段落 */
    private fun isBoilerplate(p: String): Boolean {
        if (p.length < BODY_MIN_PARA_CHARS) return true
        return BOILERPLATE_MARKERS.any { p.contains(it, ignoreCase = true) }
    }

    private val BOILERPLATE_MARKERS = listOf(
        "版权所有", "免责声明", "责任编辑", "转载请注明", "来源：", "来源:", "本文地址",
        "分享到", "微信扫一扫", "扫码关注", "点击下载", "APP下载", "手机端访问",
        "相关推荐", "猜你喜欢", "热门文章", "上一篇", "下一篇", "返回首页", "首页 >",
        "登录后", "注册账号", "广告合作", "商务合作", "Copyright", "All Rights Reserved",
        "本站仅提供", "仅供参考", "评论区", "发布于", "阅读全文"
    )

    private const val BODY_MAX_CHARS = 900
    private const val BODY_MIN_PARA_CHARS = 12
    private const val BODY_MIN_REL = 0.12
    private const val BODY_DUP_JACCARD = 0.70

    // ---------------------------------------------------------------- 判断

    private fun isBlacklisted(site: String): Boolean {
        val s = site.lowercase()
        return BLACKLIST.any { s == it || s.endsWith(".$it") }
    }

    private fun isAd(title: String, snippet: String, url: String, site: String): Boolean {
        val t = "$title $snippet"
        if (AD_MARKERS.any { t.contains(it, ignoreCase = true) }) return true
        val u = url.lowercase()
        if (u.contains("/ad/") || u.contains("/ads/") || u.contains("click.jsp") ||
            u.contains("promotion") || u.contains("?ad=") || u.contains("&ad=")
        ) return true
        // 购物链接不是不能看，但不能当"攻略证据"：交给 L4 降权即可，不在这里一刀切
        return false
    }

    private fun isClickbait(title: String): Boolean {
        if (CLICKBAIT.any { title.contains(it) }) return true
        if (title.count { it == '!' || it == '！' } >= 3) return true
        return false
    }

    /** 关键词堆砌：2-gram 在同一标题里重复 4 次以上（正常中文标题几乎不会出现） */
    private fun isKeywordStuffed(title: String): Boolean {
        val cn = title.filter { it in '\u4e00'..'\u9fa5' }
        if (cn.length < 8) return false
        val counts = HashMap<String, Int>()
        for (i in 0 until cn.length - 1) {
            val g = cn.substring(i, i + 2)
            counts[g] = (counts[g] ?: 0) + 1
        }
        return (counts.values.maxOrNull() ?: 0) >= 4 && title.length < 40
    }

    private fun tierOf(site: String): SourceTier {
        val s = site.lowercase().removePrefix("www.")
        if (s.isBlank()) return SourceTier.L4
        L1_HOSTS.firstOrNull { s == it || s.endsWith(".$it") }?.let { return SourceTier.L1 }
        L2_HOSTS.firstOrNull { s == it || s.endsWith(".$it") }?.let { return SourceTier.L2 }
        L3_HOSTS.firstOrNull { s == it || s.endsWith(".$it") }?.let { return SourceTier.L3 }
        L4_HOSTS.firstOrNull { s == it || s.endsWith(".$it") }?.let { return SourceTier.L4 }
        // 短链 / 追踪域一律不可信
        if (s.length <= 4 || s.endsWith(".top") || s.contains("link") || s.contains("click")) {
            return SourceTier.L5
        }
        return SourceTier.L4
    }

    private fun tierWeight(t: SourceTier): Double = t.weight

    // ---------------------------------------------------------------- 相关性

    fun relevance(
        query: String,
        qTokens: Set<String>,
        keyPhrase: String,
        title: String,
        snippet: String,
        body: String
    ): Double {
        if (qTokens.isEmpty()) return 0.5
        val text = "$title $snippet $body"
        val tTokens = tokens(text)
        val hit = qTokens.count { tTokens.contains(it) }
        val overlap = hit.toDouble() / qTokens.size
        // 完整关键词串出现（"大理机票价格"整体命中）比零散词命中更可信
        val phraseHit = if (keyPhrase.length >= 4 &&
            text.replace(Regex("\\s+"), "").contains(keyPhrase, ignoreCase = true)
        ) 1.0 else 0.0
        val intentWords = listOf(
            "价格", "票价", "门票", "费用", "元", "天气", "气温", "℃", "评分", "评价",
            "开放", "营业", "预约", "地址", "攻略", "推荐", "航班", "机票", "酒店"
        )
        val intentHit = intentWords.count { text.contains(it) }.coerceAtMost(3) / 3.0
        val numberBonus = if (Regex("\\d+").containsMatchIn(text) && qTokens.any {
                it in setOf("价格", "门票", "多少钱", "天气", "温度", "评分", "几点", "时间")
            }
        ) 0.08 else 0.0
        return (0.55 * overlap + 0.22 * phraseHit + 0.18 * intentHit + numberBonus)
            .coerceIn(0.0, 1.0)
    }

    // ---------------------------------------------------------------- 去重

    /** SimHash：把文本 3-gram 哈希后按位投票，64 位指纹 */
    fun simHash(text: String): Long {
        val grams = grams(text, 3)
        if (grams.isEmpty()) return 0L
        val bits = IntArray(64)
        for (g in grams) {
            val h = fnv1a64(g)
            for (b in 0 until 64) {
                if ((h ushr b) and 1L == 1L) bits[b]++ else bits[b]--
            }
        }
        var out = 0L
        for (b in 0 until 64) if (bits[b] > 0) out = out or (1L shl b)
        return out
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.count { b.contains(it) }
        return inter.toDouble() / (a.size + b.size - inter)
    }

    private fun isDuplicate(a: Evidence, b: Evidence, config: Config): Boolean =
        nearDuplicate(a.text, b.text, config)

    /**
     * 两段文本是否在讲"同一篇内容"。三个判据任一命中：
     *  - SimHash 汉明距离 ≤3（近全文级重复）；
     *  - 3-gram Jaccard ≥0.72（同文改写）；
     *  - 包含度 ≥0.82（正文被加了"责任编辑/来源/版权"尾巴，Jaccard 被稀释）。
     * 单看 Jaccard 会漏掉转载尾巴，单看 SimHash 对短摘要又不稳，所以三者取并集。
     */
    fun nearDuplicate(a: String, b: String, config: Config = Config()): Boolean {
        val ga = grams(a, 3)
        val gb = grams(b, 3)
        if (ga.isEmpty() || gb.isEmpty()) return false
        if (hamming(simHash(a), simHash(b)) <= config.dupHamming) return true
        if (jaccard(ga, gb) >= config.dupJaccard) return true
        val small = minOf(ga.size, gb.size)
        if (small < 14) return false
        val inter = ga.count { gb.contains(it) }
        return inter.toDouble() / small >= config.dupContainment
    }

    /** 不同域名、标题高度重合或正文高度重合，且关键数字一致 → 同一篇通稿/同一事件复述 */
    private fun isMirror(a: Evidence, b: Evidence, config: Config): Boolean {
        val na = numbersOf(a.text)
        val nb = numbersOf(b.text)
        val sharedNumber = na.intersect(nb).isNotEmpty() || (na.isEmpty() && nb.isEmpty())
        if (!sharedNumber) return false
        val ta = grams(a.title, 2)
        val tb = grams(b.title, 2)
        if (ta.isNotEmpty() && tb.isNotEmpty() &&
            jaccard(ta, tb) >= config.mirrorTitleJaccard
        ) return true
        // 标题被改写（"一览/汇总/与预约方式"）但正文仍是同一篇：按正文相似度认转载
        val ga = grams(a.text, 3)
        val gb = grams(b.text, 3)
        if (ga.isEmpty() || gb.isEmpty()) return false
        return jaccard(ga, gb) >= config.mirrorTextJaccard
    }

    // ---------------------------------------------------------------- 时效

    fun sensitivityOf(query: String): Sensitivity = when {
        REALTIME_WORDS.any { query.contains(it) } -> Sensitivity.REALTIME
        DAILY_WORDS.any { query.contains(it) } -> Sensitivity.DAILY
        WEEKLY_WORDS.any { query.contains(it) } -> Sensitivity.WEEKLY
        MONTHLY_WORDS.any { query.contains(it) } -> Sensitivity.MONTHLY
        STABLE_WORDS.any { query.contains(it) } -> Sensitivity.STABLE
        else -> Sensitivity.MONTHLY
    }

    /**
     * 新鲜度：有日期按半衰期指数衰减；没日期给 0.55 的中性值。
     * 刻意不给"无日期"满分 —— 大量 SEO 页面故意不写日期，给满分等于奖励它。
     */
    fun freshness(daysAgo: Int?, sensitivity: Sensitivity): Double {
        val d = daysAgo ?: return 0.55
        if (d <= 0) return 1.0
        val half = sensitivity.halfLifeDays
        return 0.5.pow(d / half).coerceIn(0.05, 1.0)
    }

    /** 从文本里抠日期，返回（原文, 距今天数）；"3天前/昨天/今天"也认 */
    fun detectDate(text: String): Pair<String, Int?> {
        Regex("(20\\d{2}|19\\d{2})[-/.年](\\d{1,2})[-/.月](\\d{1,2})").find(text)?.let { m ->
            val y = m.groupValues[1].toIntOrNull() ?: return "" to null
            val mo = m.groupValues[2].toIntOrNull() ?: return "" to null
            val d = m.groupValues[3].toIntOrNull() ?: return "" to null
            return m.value to daysSince(y, mo, d)
        }
        Regex("(?<!\\d)(\\d{1,2})[-/.月](\\d{1,2})日").find(text)?.let { m ->
            val mo = m.groupValues[1].toIntOrNull() ?: return "" to null
            val d = m.groupValues[2].toIntOrNull() ?: return "" to null
            if (mo !in 1..12 || d !in 1..31) return "" to null
            val now = Calendar.getInstance()
            val year = now.get(Calendar.YEAR)
            var days = daysSince(year, mo, d) ?: return "" to null
            // 跨年：12 月看到"1月5日"应是去年
            if (days < -30) days = daysSince(year - 1, mo, d) ?: days
            return m.value to days
        }
        val rel = Regex("(\\d+)\\s*(分钟|小时|天|周|个月|月|年)前").find(text)
        if (rel != null) {
            val n = rel.groupValues[1].toIntOrNull() ?: 0
            val days = when (rel.groupValues[2]) {
                "分钟", "小时" -> 0
                "天" -> n
                "周" -> n * 7
                "个月", "月" -> n * 30
                else -> n * 365
            }
            return rel.value to days
        }
        return when {
            text.contains("前天") -> "" to 2
            text.contains("昨天") -> "" to 1
            text.contains("今天") -> "" to 0
            else -> "" to null
        }
    }

    private fun daysSince(y: Int, m: Int, d: Int): Int? = runCatching {
        val then = Calendar.getInstance().apply {
            set(Calendar.YEAR, y); set(Calendar.MONTH, m - 1)
            set(Calendar.DAY_OF_MONTH, d); set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        ((today - then) / (24 * 60 * 60 * 1000L)).toInt()
    }.getOrNull()

    // ---------------------------------------------------------------- 分数

    fun scoreOf(e: Evidence, diversity: Double): Double =
        (0.40 * e.tier.weight + 0.30 * e.relevance + 0.15 * e.freshness + 0.15 * diversity)
            .coerceIn(0.0, 1.0)

    private fun siteQuotaOk(e: Evidence, perSite: Map<String, Int>, config: Config): Boolean =
        (perSite[e.site] ?: 0) <= config.maxPerSite

    // ---------------------------------------------------------------- 文本

    /** 中文按 2-gram 切、英文/数字按词切；停用词不进集合，避免"怎么/什么"制造假相关 */
    fun tokens(s: String): Set<String> {
        val out = HashSet<String>()
        val lower = s.lowercase()
        val cn = lower.filter { it in '\u4e00'..'\u9fa5' }
        for (i in 0 until cn.length - 1) {
            val g = cn.substring(i, i + 2)
            if (g !in STOP) out.add(g)
        }
        Regex("[a-z0-9]{2,}").findAll(lower).forEach { out.add(it.value) }
        return out
    }

    private val STOP = setOf(
        "的", "了", "吗", "呢", "吧", "怎么", "什么", "多少", "一下", "请问", "帮我",
        "推荐", "哪里", "可以", "如何", "最新", "现在", "今天", "明天", "我们", "你们",
        "他们", "这个", "那个", "这里", "那里", "以及", "还有", "是否", "能不能"
    )

    fun grams(text: String, n: Int): Set<String> {
        val t = text.lowercase().replace(Regex("[\\s\\p{Punct}，。！？；：、“”‘’（）《》【】…—]+"), "")
        if (t.length < n) return t.takeIf { it.isNotBlank() }?.let { setOf(it) } ?: emptySet()
        val out = HashSet<String>()
        for (i in 0..t.length - n) out.add(t.substring(i, i + n))
        return out
    }

    fun numbersOf(text: String): Set<String> =
        Regex("\\d+(?:[.,]\\d+)?").findAll(text)
            .map { it.value.replace(",", "") }
            .filter { it.length <= 12 }
            .toSet()

    private fun fnv1a64(s: String): Long {
        var h = -0x340d631b7bdddcdbL // FNV offset basis 0xcbf29ce484222325
        for (c in s) {
            h = h xor c.code.toLong()
            h *= 0x100000001b3L
        }
        return h
    }

    fun hostOf(url: String): String = runCatching {
        java.net.URI(url).host.orEmpty().lowercase().removePrefix("www.")
    }.getOrDefault("").ifBlank {
        Regex("https?://([^/]+)").find(url)?.groupValues?.getOrNull(1)
            ?.lowercase()?.removePrefix("www.").orEmpty()
    }

    /** URL 归一化：去 fragment、去追踪参数、去末尾斜杠，用于判"同一页面" */
    fun urlKey(url: String): String {
        if (url.isBlank()) return ""
        val noFrag = url.substringBefore('#')
        val base = noFrag.substringBefore('?')
        val query = noFrag.substringAfter('?', "")
        val kept = query.split('&').filter { p ->
            val k = p.substringBefore('=').lowercase()
            k.isNotBlank() && !(k.startsWith("utm_") || k in TRACKING_KEYS)
        }
        val schemeStripped = base.removePrefix("https://").removePrefix("http://")
        val host = schemeStripped.substringBefore('/').removePrefix("www.")
        val path = schemeStripped.substringAfter('/', "").trimEnd('/')
        return (host + "/" + path + if (kept.isEmpty()) "" else "?" + kept.joinToString("&")).lowercase()
    }

    private val TRACKING_KEYS = setOf(
        "spm", "from", "source", "ref", "referrer", "share", "share_source", "gclid",
        "fbclid", "msclkid", "yclid", "scene", "chksm", "sessionid", "traceid"
    )

    private fun keyOf(e: Evidence): String = "${e.url}|${e.title}"
}

/** 绝对值（保留给测试与后续分差比较） */
internal fun absDiff(a: Double, b: Double): Double = abs(a - b)
