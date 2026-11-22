package com.example.my.data.search

import com.example.my.data.WebHit

/**
 * 来源层级（清洗管线第三层）。
 *
 * 权重不是"可信度打分"，而是"这条证据能不能单独支撑一条事实"：
 * L1 可以单独成结论，L2 需要两条独立来源，L3/L4 只能作为补充或分歧的一方，
 * L5 直接剔除、不参与任何计数（否则低质页面会被当成"多源一致"）。
 */
enum class SourceTier(val level: Int, val weight: Double, val label: String) {
    L1(1, 1.0, "官方/一手"),
    L2(2, 0.8, "主流/头部"),
    L3(3, 0.5, "垂直/社区"),
    L4(4, 0.2, "低质/营销"),
    L5(5, 0.0, "可疑剔除");

    companion object {
        fun ofLevel(level: Int): SourceTier = entries.firstOrNull { it.level == level } ?: L3
    }
}

/**
 * 一条清洗后的证据。
 *
 * 和原始 [WebHit] 的区别：带层级、相关性、时效、最终分数，以及"是否为转载镜像"。
 * 镜像（mirrorOf > 0）仍然展示给用户、也可以被引用，但**不计入多源一致性** ——
 * 这是算法 v2 里"假共识"问题的直接修法：十家转载同一篇通稿，不等于十个独立来源。
 */
data class Evidence(
    val id: Int,
    val title: String,
    val snippet: String,
    val url: String,
    val site: String,
    val tier: SourceTier,
    /** 与子问题的相关性，0..1 */
    val relevance: Double,
    /** 时效性，0..1（按问题时效敏感度衰减） */
    val freshness: Double,
    /** 最终证据分 S，0..1 */
    val score: Double,
    /** 文内日期（原文写法，空串表示没找到） */
    val published: String = "",
    /** >0 表示与该 id 的证据同文转载/同事件复述 */
    val mirrorOf: Int = 0,
    /** 抓到的正文摘录 */
    val body: String = ""
) {
    /** 能否参与共识计数 */
    val independent: Boolean get() = mirrorOf == 0

    /** 句级事实抽取的语料：标题 + 摘要 + 正文 */
    val text: String
        get() = listOf(title, snippet, body).filter { it.isNotBlank() }.joinToString("。")

    fun toHit(): WebHit = WebHit(
        title = title,
        snippet = snippet,
        url = url,
        site = site,
        tier = tier.level,
        score = score
    )
}

/** 时效敏感度决定新鲜度衰减的半衰期：天气预报半天就旧了，门票价格能撑一周 */
enum class Sensitivity(val halfLifeDays: Double) {
    REALTIME(0.5),
    DAILY(1.0),
    WEEKLY(7.0),
    MONTHLY(30.0),
    STABLE(180.0)
}

/** 一条被裁决过的事实（原子声明） */
enum class Verdict {
    /** 高置信：可以直接陈述 */
    CONFIRMED,

    /** 中置信：必须加"多数来源显示"这类限定词 */
    QUALIFIED,

    /** 分歧：必须并列呈现，不能折中成平均值 */
    DISPUTED,

    /** 证据不足：只能说"网上暂未查到"，禁止用模型记忆补全 */
    INSUFFICIENT
}

/**
 * 一条原子事实及其裁决结果。
 *
 * [confidence] 完全由证据侧算出（证据分 + 多源一致性 + 覆盖度），模型自评只在强模型
 * 场景下占很小权重；[modelSelf] 保留是为了可解释，不是因为模型说"我很确定"就提高可信度。
 */
data class Claim(
    val id: Int,
    val topic: String,
    val statement: String,
    val numbers: List<String>,
    val evidenceIds: List<Int>,
    val support: Int,
    val contra: Int,
    val consistency: Double,
    val coverage: Double,
    val evidenceScore: Double,
    val modelSelf: Double,
    val confidence: Double,
    val verdict: Verdict,
    /** 分歧时另一方的话术（与 [statement] 相对） */
    val counterStatement: String = ""
)

/**
 * 模型能力档位与权重配置（算法 v2 里 β=min(μ*k,0.5) 的具体落地）。
 *
 * v3 的改动：模型权重只作用于"措辞与自评"，不再作用于"事实成立与否"。
 * 弱模型 β=0 —— 它写得多自信都不影响结论，事实由证据决定。
 */
data class FusionProfile(
    /** 模型能力系数 μ，0..1 */
    val mu: Double,
    /** 证据分权重 */
    val alpha: Double,
    /** 模型自评权重 */
    val beta: Double,
    /** 多源一致性权重 */
    val gamma: Double,
    /** 子问题覆盖度权重 */
    val delta: Double,
    /** 展示用：模型档位名 */
    val name: String,
    /** 强模型才允许"模型常识"作为补充（且必须标注未核实） */
    val allowModelFact: Boolean
) {
    val label: String
        get() = when {
            mu >= 0.8 -> "强模型"
            mu >= 0.5 -> "中等模型"
            else -> "小模型"
        }

    companion object {
        /**
         * 三档权重：强 / 中 / 小。每档权重之和为 1，便于把置信度直接当概率读。
         * 小模型：证据 0.52 + 一致性 0.28 + 覆盖 0.20，模型自评 0 ——
         * 这就是"小模型不负责知道，只负责说清楚"的量化表达。
         */
        fun of(mu: Double, name: String): FusionProfile {
            val m = mu.coerceIn(0.0, 1.0)
            return when {
                m >= 0.8 -> FusionProfile(
                    mu = m, alpha = 0.35, beta = 0.15, gamma = 0.28, delta = 0.22,
                    name = name, allowModelFact = true
                )
                m >= 0.5 -> FusionProfile(
                    mu = m, alpha = 0.45, beta = 0.10, gamma = 0.28, delta = 0.17,
                    name = name, allowModelFact = false
                )
                else -> FusionProfile(
                    mu = m, alpha = 0.52, beta = 0.0, gamma = 0.28, delta = 0.20,
                    name = name, allowModelFact = false
                )
            }
        }
    }
}

/** 一次清洗的统计：用于界面解释"为什么只留了这几条"，也用于后续调参 */
data class CleanStats(
    val rawCount: Int = 0,
    val keptCount: Int = 0,
    val mergedMirrors: Int = 0,
    val dropped: Map<String, Int> = emptyMap(),
    val halfLifeDays: Double = 30.0
) {
    fun summary(): String = buildString {
        append("原始 ${rawCount} 条 → 保留 ${keptCount} 条")
        if (mergedMirrors > 0) append("，合并转载 ${mergedMirrors} 条")
        if (dropped.isNotEmpty()) append("，剔除 " + dropped.entries.joinToString("、") { "${it.key}${it.value}" })
    }
}

/** 一轮「先搜再答」的完整产物：证据集 + 事实裁决 + 模型档位 */
data class SeaDeck(
    val query: String,
    val evidence: List<Evidence>,
    val claims: List<Claim>,
    val profile: FusionProfile,
    val stats: CleanStats,
    val topics: List<String> = emptyList(),
    /** 本轮真实搜到的原始命中：即使清洗后没留下证据，也作为可点参考来源展示 */
    val hits: List<WebHit> = emptyList()
) {
    val isEmpty: Boolean get() = evidence.isEmpty()

    /** 给模型的"事实卡"文本 */
    val block: String get() = DeckRenderer.render(this)
}
