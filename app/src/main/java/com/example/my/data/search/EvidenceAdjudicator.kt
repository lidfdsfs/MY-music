package com.example.my.data.search

import kotlin.math.abs

/**
 * 证据裁决（算法第 ③ 步的本地部分）。
 *
 * 这里是 v3 相对 v2 最重要的改动：**不让小模型先写答案、再抽声明**，而是先在本地
 * 把证据切成句级事实、聚成"同一个事实的多条独立佐证"、算出置信度，再让模型做
 * "把这些既定事实说清楚"的改写。原因很直接：1.5B/0.5B 的模型抽不准声明，
 * 让它自评置信更是无效信号；而"哪几句在说同一件事、几个独立站点在说、数字是否打架"
 * 是纯计算问题，本地做得又快又准。
 *
 * 置信度只由三样东西决定：证据分、多源一致性、子问题覆盖度。
 * 模型档位只决定这三个权重的配比（见 [FusionProfile]），弱模型的自评权重为 0。
 */
object EvidenceAdjudicator {

    data class Config(
        /** 句级相关性下限（低于它的句子不进事实池） */
        val minSentenceRelevance: Double = 0.22,
        /** 判定为"同一事实"的 token Jaccard 下限（低于它基本不是同一件事） */
        val clusterTokenJaccard: Double = 0.18,
        /** 较短一句被另一句覆盖率的下限：容忍"同义改写 + 多说了细节"的长短差。
         *  实测两条同一门票事实（"上调至120元"/"为100元，索道另计，学生半价"）
         *  共同词 7 个、占比 7/19≈0.37；阈值高于它就会漏并，把数字冲突拆成两条各自"可信"的结论。 */
        val clusterContainment: Double = 0.35,
        /** 同一簇内允许的最大句数 */
        val clusterSize: Int = 4,
        /** 高置信阈值 */
        val highConfidence: Double = 0.72,
        /** 中置信阈值（低于它进"证据不足"） */
        val midConfidence: Double = 0.55,
        /** 反对来源占比达到该值即判为分歧 */
        val disputeRatio: Double = 0.35,
        val maxClaims: Int = 7
    )

    private data class NumCue(val value: Double, val unit: String, val raw: String)

    private data class Fact(
        val text: String,
        val evidenceId: Int,
        val site: String,
        val evScore: Double,
        val tokens: Set<String>,
        val nums: List<NumCue>,
        val rel: Double,
        val polarity: Set<String>
    )

    /**
     * @param evidence 已清洗的证据集（只取 independent=true 的参与共识）
     * @param profile  模型档位（决定权重配比，不决定事实）
     */
    fun adjudicate(
        query: String,
        evidence: List<Evidence>,
        profile: FusionProfile,
        config: Config = Config()
    ): List<Claim> {
        val usable = evidence.filter { it.independent }
        if (usable.isEmpty()) return emptyList()
        val topics = topicsOf(query)
        val qTokens = SearchCleaner.tokens(query)
        if (qTokens.isEmpty()) return emptyList()

        // ---------- 1. 句级事实池 ----------
        val facts = mutableListOf<Fact>()
        for (e in usable) {
            // 只从摘要/正文里抽事实，标题不进事实池：
            // "玉龙雪山门票价格调整"这种标题只是话题标签，没有数字也没有断言，
            // 混进来会把两条互相矛盾的正文并到同一簇、却因为标题无数字而漏判冲突。
            val sentences = splitSentences(
                listOf(e.snippet, e.body).filter { it.isNotBlank() }.joinToString("。")
            )
            for (s in sentences) {
                val t = s.trim()
                if (t.length < 8 || t.length > 180) continue
                val tokens = SearchCleaner.tokens(t)
                val overlap = if (qTokens.isEmpty()) 0.0
                else qTokens.count { tokens.contains(it) }.toDouble() / qTokens.size
                // 有数字的句子天然更"具体"，给一点加成；但不能替代主题相关性
                val numBonus = if (numsOf(t).isNotEmpty()) 0.10 else 0.0
                val rel = (overlap * 0.9 + numBonus).coerceIn(0.0, 1.0)
                if (rel < config.minSentenceRelevance) continue
                // 只保留"和问题主题沾边"的句子：整句没有任何查询词时，多半是页面导航/版权声明
                if (overlap == 0.0 && numsOf(t).isEmpty()) continue
                facts += Fact(
                    text = t,
                    evidenceId = e.id,
                    site = e.site,
                    evScore = e.score,
                    tokens = tokens,
                    nums = numsOf(t),
                    rel = rel,
                    polarity = polarityOf(t)
                )
            }
        }
        if (facts.isEmpty()) return emptyList()

        // ---------- 2. 聚成事实簇 ----------
        // 用"包含度"而不是纯 Jaccard：同一事实的不同写法常有长短差（一方多说了索道/学生价），
        // Jaccard 会被额外细节稀释，包含度看的是"较短那句有多少被对方覆盖"，更贴近人判断"说的是不是同一件事"。
        val clusters = mutableListOf<MutableList<Fact>>()
        for (f in facts.sortedByDescending { it.evScore * 0.6 + it.rel * 0.4 }) {
            val target = clusters.firstOrNull { cl ->
                if (cl.size >= config.clusterSize) return@firstOrNull false
                val ref = cl.first()
                val tj = SearchCleaner.jaccard(ref.tokens, f.tokens)
                if (tj < config.clusterTokenJaccard) return@firstOrNull false
                val small = minOf(ref.tokens.size, f.tokens.size)
                val inter = ref.tokens.count { f.tokens.contains(it) }
                // 至少 6 个共同词才谈得上"同一件事"，避免"大理古城门票"和"大理古城住宿"这种
                // 只共享地名/对象词的句子被并簇
                val contain = if (small >= 6 && inter >= 6) inter.toDouble() / small else 0.0
                sameNumberSignature(ref, f) || contain >= config.clusterContainment
            }
            if (target != null) target += f else clusters += mutableListOf(f)
        }

        // ---------- 3. 逐簇裁决 ----------
        val claims = clusters.mapNotNull { cl ->
            buildClaim(cl, topics, profile, config)
        }.sortedByDescending { it.confidence }
            .take(config.maxClaims)
            .mapIndexed { i, c -> c.copy(id = i + 1) }

        // 覆盖度按整份答案统一回填：一个问题常含多个子问题（"天气和机票"），
        // 只答了其中一半的结论不该算高置信
        val covered = topics.count { topic ->
            val tt = SearchCleaner.tokens(topic)
            val need = maxOf(1, tt.size / 2)
            claims.any { c -> tt.count { c.statement.contains(it) } >= need }
        }
        val deckCoverage = if (topics.isEmpty()) 1.0
        else (covered.toDouble() / topics.size).coerceIn(0.0, 1.0)
        return claims.map { c ->
            val cov = (c.coverage * 0.5 + deckCoverage * 0.5).coerceIn(0.0, 1.0)
            val conf = confidenceOf(c.evidenceScore, c.consistency, cov, c.modelSelf, profile)
            c.copy(coverage = cov, confidence = conf, verdict = verdictOf(c, conf, config))
        }
    }

    // ---------------------------------------------------------------- 单簇裁决

    private fun buildClaim(
        cluster: MutableList<Fact>,
        topics: List<String>,
        profile: FusionProfile,
        config: Config
    ): Claim? {
        if (cluster.isEmpty()) return null
        val sites = cluster.map { it.site }.filter { it.isNotBlank() }.toSet()
        val support = sites.size.coerceAtLeast(1)

        // 数字冲突：同单位、差值超过 3% 视为互相矛盾（"100元" vs "120元"）
        var contraSites = 0
        val numericGroups = cluster.flatMap { f -> f.nums.map { it to f.site } }
        val byUnit = numericGroups.groupBy { it.first.unit }
        for ((unit, list) in byUnit) {
            if (unit.isBlank()) continue
            val values = list.map { it.first.value }
            if (values.size < 2) continue
            val max = values.maxOrNull() ?: 0.0
            val min = values.minOrNull() ?: 0.0
            if (max > 0 && (max - min) / max > 0.03) {
                // 与"多数值"不同的站点算一票反对；只数独立站点，避免同站多条重复计数
                val majority = values.sorted()[values.size / 2]
                val against = list.filter { abs(it.first.value - majority) / max > 0.03 }
                    .map { it.second }.toSet()
                contraSites = maxOf(contraSites, against.size)
            }
        }

        // 极性冲突：免费/收费、开放/暂停、涨价/降价、含/不含
        var polarityContra = 0
        val pairList = cluster.map { it.polarity }
        if (pairList.size >= 2 && pairList.any { it.contains("负") } && pairList.any { it.contains("正") }) {
            polarityContra = maxOf(1, contraSites)
        }
        val contra = maxOf(contraSites, polarityContra)

        // 一致性：多源支持度减去反对惩罚，再归一化到 0..1
        val supportScore = (support.toDouble() / 2.5).coerceAtMost(1.0)
        val penalty = (contra.toDouble() * 0.5).coerceAtMost(1.0)
        val consistency = (supportScore - penalty).coerceIn(-1.0, 1.0)
        val consistencyNorm = ((consistency + 1.0) / 2.0).coerceIn(0.0, 1.0)

        // 代表句：证据分最高、且信息量最大（含数字 / 长度适中）
        val rep = cluster.maxWithOrNull(
            compareBy<Fact> { it.evScore * 0.5 + it.rel * 0.5 + if (it.nums.isNotEmpty()) 0.15 else 0.0 }
        ) ?: return null
        val statement = tidy(rep.text)
        val counter = if (contra > 0) {
            cluster.filter { it.nums.isNotEmpty() && !sameNumbers(it.nums, rep.nums) }
                .maxByOrNull { it.evScore }?.let { tidy(it.text) }.orEmpty()
        } else ""

        val evidenceIds = cluster.map { it.evidenceId }.distinct().sorted()
        val avgScore = cluster.map { it.evScore }.average().coerceIn(0.0, 1.0)
        val topic = topics.firstOrNull { t ->
            val tt = SearchCleaner.tokens(t)
            tt.isNotEmpty() && tt.any { statement.contains(it) }
        } ?: topics.firstOrNull().orEmpty()

        val topicCovered = topics.count { t ->
            val tt = SearchCleaner.tokens(t)
            val need = maxOf(1, tt.size / 2)
            tt.count { statement.contains(it) } >= need
        }
        val coverage = if (topics.size <= 1) {
            if (topicCovered >= 1 || topics.isEmpty()) 0.85 else 0.6
        } else {
            (topicCovered.toDouble() / topics.size).coerceIn(0.25, 1.0)
        }

        // 结构化"自评代理"：生成前拿不到模型自评，用中性值 + 信息完整度微调；
        // 弱模型 beta=0，这个值对结论没有影响。
        val modelSelf = (0.55 + if (rep.nums.isNotEmpty()) 0.10 else 0.0 +
            if (support >= 2) 0.05 else 0.0).coerceIn(0.0, 1.0)

        val conf = confidenceOf(avgScore, consistencyNorm, coverage, modelSelf, profile)
        val probe = Claim(
            id = 0, topic = topic, statement = statement, numbers = rep.nums.map { it.raw },
            evidenceIds = evidenceIds, support = support, contra = contra,
            consistency = consistency, coverage = coverage, evidenceScore = avgScore,
            modelSelf = modelSelf, confidence = conf, verdict = Verdict.INSUFFICIENT,
            counterStatement = counter
        )
        return probe.copy(verdict = verdictOf(probe, conf, config))
    }

    private fun confidenceOf(
        evidenceScore: Double,
        consistencyNorm: Double,
        coverage: Double,
        modelSelf: Double,
        profile: FusionProfile
    ): Double = (profile.alpha * evidenceScore +
        profile.beta * modelSelf +
        profile.gamma * consistencyNorm +
        profile.delta * coverage).coerceIn(0.0, 1.0)

    private fun verdictOf(c: Claim, conf: Double, config: Config): Verdict {
        // support 是簇内全部独立站点数，contra 是其中一个子集 —— 分母必须用 support，
        // 否则"2 个来源里 1 个说法相反"会被算成 1/3=33%，永远够不到 35% 的分歧线
        val total = maxOf(1, c.support)
        if (c.contra > 0 && c.contra.toDouble() / total >= config.disputeRatio) return Verdict.DISPUTED
        return when {
            conf >= config.highConfidence -> Verdict.CONFIRMED
            conf >= config.midConfidence -> Verdict.QUALIFIED
            else -> Verdict.INSUFFICIENT
        }
    }

    // ---------------------------------------------------------------- 文本与数值

    /** 子问题拆分："大理天气和机票" → ["大理天气","机票"] */
    fun topicsOf(query: String): List<String> {
        val cut = query.split(Regex("和|与|、|，|,|以及|还有|顺便|另外|＋|\\+|；|;"))
            .map { it.trim().trimEnd('？', '?', '。') }
            .filter { it.length >= 2 }
        return if (cut.size <= 1) listOf(query.trim()) else cut.take(3)
    }

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("[。！？；\\n\\r]+|\\s{3,}"))
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }

    /** 去掉开头常见的"来源：xx"“首页 > 攻略”这类页面噪声 */
    private fun tidy(s: String): String {
        var t = s.trim()
        t = t.replace(Regex("^(首页\\s*>\\s*[^ ]*\\s*)"), "")
            .replace(Regex("^(来源[:：][^ ]*\\s*)"), "")
            .replace(Regex("^\\[[^\\]]{1,10}]"), "")
            .trim()
        if (t.length > 120) {
            val cut = t.indexOf('，', 80)
            t = if (cut in 1..120) t.substring(0, cut) else t.substring(0, 120)
        }
        return t
    }

    private val NUM_UNIT = Regex(
        "(\\d+(?:[.,]\\d+)?)\\s*(元|块|￥|¥|美元|℃|度|%|折|公里|km|米|小时|分钟|天|晚|张|间|人|岁|分)?"
    )

    private fun numsOf(text: String): List<NumCue> {
        val out = mutableListOf<NumCue>()
        NUM_UNIT.findAll(text).forEach { m ->
            val raw = m.groupValues[1].replace(",", "")
            val v = raw.toDoubleOrNull() ?: return@forEach
            val unit = m.groupValues[2]
            out += NumCue(v, unit, m.value.trim())
        }
        return out.distinctBy { it.value to it.unit }.take(6)
    }

    private fun sameNumbers(a: Fact, b: Fact): Boolean = sameNumbers(a.nums, b.nums)

    private fun sameNumbers(a: List<NumCue>, b: List<NumCue>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val bv = b.map { it.value }
        return a.all { x -> bv.any { abs(it - x.value) <= 0.03 * maxOf(1.0, abs(x.value)) } }
    }

    private fun sameNumberSignature(a: Fact, b: Fact): Boolean {
        if (a.nums.isEmpty() || b.nums.isEmpty()) return false
        return a.nums.map { it.value }.sorted().take(3) == b.nums.map { it.value }.sorted().take(3)
    }

    /** 极性词：用于发现"同一个对象，一处说免费一处说收费"这类真冲突 */
    private fun polarityOf(text: String): Set<String> {
        val out = HashSet<String>()
        NEGATIVE_CUES.filter { text.contains(it) }.forEach { out += "负" }
        POSITIVE_CUES.filter { text.contains(it) }.forEach { out += "正" }
        return out
    }

    private val POSITIVE_CUES = listOf("免费", "正常开放", "开放", "含早餐", "可预约", "涨价", "上涨", "有票")
    private val NEGATIVE_CUES = listOf("收费", "暂停开放", "关闭", "维修", "不含早餐", "已约满", "降价", "下跌", "售罄", "禁止")
}
