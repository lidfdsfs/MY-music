package com.example.my.data.search

import com.example.my.data.AiEndpoint
import com.example.my.data.WebHit
import com.example.my.data.WebSourcePicker
import com.example.my.data.WebSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SEA-AI 融合算法 v3 的入口门面。
 *
 * 一次调用完成：检索结果 → 清洗（[SearchCleaner]）→ 事实裁决（[EvidenceAdjudicator]）
 *              → 事实卡（[DeckRenderer]）→（模型作答）→ 数字审计（[NumberGuard]）。
 *
 * 模型能力 μ 只影响三件事的配比，不影响事实本身：
 *   - 事实卡里"模型可以补充常识"是否开放；
 *   - 置信度中"证据 / 一致性 / 覆盖"与"模型自评"的权重；
 *   - 端侧小模型时把输出压成"复述既定事实"，不要求它判断。
 */
object SeaFusion {

    /** 纯计算：给定命中与正文，产出证据集 + 事实卡（不联网，可单测） */
    fun build(
        query: String,
        hits: List<WebHit>,
        bodies: Map<String, String> = emptyMap(),
        ep: AiEndpoint? = null,
        isLocal: Boolean = false,
        localModel: String = "",
        config: SearchCleaner.Config = SearchCleaner.Config(),
        // 对话里只要"够回答问题"的几条事实，条数越少模型越省 token
        adjudicatorConfig: EvidenceAdjudicator.Config = EvidenceAdjudicator.Config()
    ): SeaDeck {
        val profile = profileFor(ep, isLocal, localModel)
        // 事实卡只能从「与问题同主题」的网页里抽。SearchCleaner 自己的相关性
        // 是按词面算的，对中文问句容易放过「沾了同一个地名」的跨领域页；
        // 先用唯一真源的关联度闸一遍，编号仍与事实卡 [n] 严格一致。
        val scoped = WebSearch.relevantOnly(hits, query)
        val clean = SearchCleaner.clean(query, scoped, bodies, config)
        val claims = EvidenceAdjudicator.adjudicate(query, clean.evidence, profile, adjudicatorConfig)
        return SeaDeck(
            query = query,
            evidence = clean.evidence,
            claims = claims,
            profile = profile,
            stats = clean.stats,
            topics = EvidenceAdjudicator.topicsOf(query),
            hits = scoped
        )
    }

    /** 联网版：先搜，再抓前几条正文，然后清洗 + 裁决 */
    suspend fun buildWithPages(
        query: String,
        ep: AiEndpoint? = null,
        isLocal: Boolean = false,
        localModel: String = "",
        maxBodies: Int = 3,
        config: SearchCleaner.Config = SearchCleaner.Config(),
        adjudicatorConfig: EvidenceAdjudicator.Config = EvidenceAdjudicator.Config(),
        /** 调用方已经在链路开头用「用户原话」搜过一遍时直接复用，避免重复请求 */
        initialHits: List<WebHit>? = null,
        profile: WebSearch.SearchProfile = WebSearch.SearchProfile.balanced()
    ): SeaDeck? = withContext(Dispatchers.IO) {
        val hits = initialHits ?: runCatching {
            WebSearch.search(query, profile.limit, profile)
        }.getOrDefault(emptyList())
        if (hits.isEmpty()) return@withContext null
        val bodies = runCatching {
            WebSearch.fetchBodies(hits, maxBodies, profile.bodyTimeoutMs, candidateCap = profile.maxBodies + 2)
        }.getOrDefault(emptyList())
            .associate { (h, text) -> h.url to text }
        build(query, hits, bodies, ep, isLocal, localModel, config, adjudicatorConfig)
    }

    /**
     * 模型能力系数 μ。
     *
     * 端侧：按参数量换算（0.3B≈0.21，1.5B≈0.33，4B≈0.58），上限 0.6 ——
     * 手机上的 4B 再强也到不了"可以自己判断事实"的档位。
     * 云端：按型号关键词识别 flash/mini（0.55）、pro/max/70B+（0.9）、推理型（1.0），
     * 认不出来按 0.65（中等），宁可低估也不高估。
     */
    fun profileFor(ep: AiEndpoint?, isLocal: Boolean, localModel: String = ""): FusionProfile {
        if (isLocal) {
            val size = Regex("(\\d+(?:[.,]\\d+)?)\\s*B", RegexOption.IGNORE_CASE)
                .find(localModel)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
            val mu = if (size == null) 0.30 else (0.18 + 0.10 * size).coerceIn(0.12, 0.60)
            return FusionProfile.of(mu, localModel.ifBlank { "端侧模型" })
        }
        val name = ep?.model.orEmpty().ifBlank { ep?.name.orEmpty() }
        val lower = name.lowercase()
        val mu = when {
            Regex("(r1|o1|o3|o4|thinking|reasoner|reasoning)").containsMatchIn(lower) -> 1.0
            Regex("(4o|opus|sonnet|pro|max|plus|70b|72b|100b|235b|405b|671b)").containsMatchIn(lower) -> 0.90
            Regex("(flash|mini|lite|turbo|air|nano|haiku|1\\.5b|3b|8b)").containsMatchIn(lower) -> 0.55
            else -> 0.65
        }
        return FusionProfile.of(mu, name.ifBlank { "云端模型" })
    }

    /** 交给模型的事实卡；没有可用证据时返回 null，调用方改用 [noEvidenceBlock] */
    fun block(deck: SeaDeck?): String? =
        deck?.takeIf { it.evidence.isNotEmpty() }?.block

    /**
     * 检索失败/清洗后无可用证据时的上下文。
     *
     * 关键分寸：只有「一条网页都没搜到」（rawCount == 0）才允许模型说没查到。
     * 已经拿到网页、只是清洗没抽出结构化事实时，必须让模型照着网页摘要回答，
     * 否则端侧 0.6B 小模型会把「如实说明没查到」这句负面指令当成回答模板复读，
     * 出现「回答：网上暂未查到具体信息」+「参考来源：8 篇网页」的自相矛盾。
     */
    fun noEvidenceBlock(query: String, rawCount: Int = 0, reason: String = ""): String = buildString {
        if (rawCount > 0) {
            appendLine("【本轮已联网检索到 $rawCount 条网页，但未抽出结构化事实卡】")
            if (reason.isNotBlank()) appendLine("原因：$reason")
            appendLine("回答要求：必须直接依据上下文里的网页标题与摘要作答，并标注来源编号 [n]；")
            appendLine("严禁说「网上暂未查到」「没有可信的网络数据」——网页就在上面，已经查到了。")
            append("网页里没写的具体数字不要凭训练记忆补，可给方向性建议但标明不是实时信息。")
        } else {
            appendLine("【联网检索未得到可用证据】")
            appendLine("这一轮一条网页都没搜到（可能网络受限或关键词过于生僻）。")
            if (reason.isNotBlank()) appendLine("原因：$reason")
            appendLine("回答要求：必须如实说明「这次没有查到可靠的网络信息」，")
            appendLine("涉及价格、天气、门票、营业时间等会变化的信息时不得凭训练记忆给具体数字，")
            append("只能给方向性建议，并说明不是实时信息、建议以官方渠道为准。")
        }
    }.trim()

    /** 回传给 UI 的来源列表：顺序与事实卡引用编号严格一致（[1] 就是列表第 1 条） */
    fun sources(deck: SeaDeck?): List<WebHit> {
        if (deck == null) return emptyList()
        // 有证据时严格按事实卡编号；没有证据（闲聊/观点类问题清洗后全被滤掉）时
        // 也不能把原始命中一股脑挂成「参考来源」——必须再过一次主题关联度，
        // 只留与本轮问题真正相关的网页，并按关联度排序。
        val evidence = deck.evidence.map { it.toHit() }
        return if (evidence.isNotEmpty()) evidence
        else WebSourcePicker.refine(WebSearch.relevantOnly(deck.hits, deck.query).take(6))
            // 关联度闸门把最后一条也滤掉时，至少保留一条通过安全校验的原始网页：
            // 回答可以没有事实卡，但不能出现「明明搜到了网页却一条来源都不挂」。
            .ifEmpty { WebSourcePicker.refine(deck.hits).take(1) }
    }

    /**
     * 回答落定后的「联网一致性」兜底：本轮明明挂了参考来源，模型却说「没查到」，
     * 就用网页标题 + 摘要机械重列一份，避免用户看到自相矛盾的对话。
     * 本地小模型（Qwen3-0.6B 等）最容易踩这个坑，云端模型一般不会走到这里。
     */
    fun ensureGrounded(answer: String, hits: List<WebHit>, query: String): String {
        if (hits.isEmpty() || answer.isBlank()) return answer
        val denies = listOf(
            "网上暂未查到", "暂未查到", "没查到可靠", "没有可信的网络数据",
            "未查到具体信息", "没有查到相关", "无法获取相关", "暂无相关网络", "未检索到"
        )
        if (!denies.any { answer.contains(it, ignoreCase = true) }) return answer
        return buildString {
            appendLine("已联网检索到 ${hits.size} 条与「$query」相关的网页，以下是网页里确实出现的信息：")
            hits.take(4).forEachIndexed { i, h ->
                append("[${i + 1}] ${h.title}")
                if (h.site.isNotBlank()) append("（${h.site}）")
                appendLine()
                if (h.snippet.isNotBlank()) appendLine("    ${h.snippet.take(120)}")
            }
            appendLine()
            append("上面这些就是本次查到的资料，点下方「参考来源」可打开原文查看；")
            append("资料里没写的具体数字我不臆测。")
        }.trim()
    }

    /** 回答落定后的机械校验；没有问题返回的 note 为空串 */
    fun auditAnswer(answer: String, deck: SeaDeck?): NumberGuard.Report =
        if (deck == null) NumberGuard.Report() else NumberGuard.audit(answer, deck)
}
