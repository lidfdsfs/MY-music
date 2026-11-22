package com.example.my.data

import android.content.Context
import com.example.my.data.search.EvidenceAdjudicator
import com.example.my.data.search.EvidenceIndex
import com.example.my.data.search.EvidenceStore
import com.example.my.data.search.SeaFusion
import com.example.my.data.search.SearchCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 行程规划的「多源检索 + 压缩」入口。
 *
 * 和 AI 旅行助手单问题检索的区别：
 *  - 助手是回答一个问题，2~8 条来源就够；
 *  - 行程要排 5 天动线，必须同时知道官方门票/预约、OTA 开放时间、UGC 避坑、
 *    景点间车程和当地美食，单一主问句必然漏项。
 *
 * 这里做三件事：
 *  1. 用 [WebSearch.SearchProfile.travelDeep] 强制展开官方文旅/OTA/知乎/小红书/
 *     路线/交通等多角度，候选池放大到 30 条；
 *  2. 按域名收敛到最多 12 个来源，同站最多 3 条，避免一个营销站刷屏；
 *  3. 交给 [SeaFusion] 抓正文、清洗、按来源层级裁决，最后只把「事实卡 +
 *     来源摘要」这段压缩文本喂给行程模型，而不是几十篇网页原文。
 *
 * 重要的真实性约束：正文抓取失败或清洗后没有证据时，仍返回来源摘要，并明确
 * 告诉模型「数字只能来自这些摘要，没写的不要编」，不会用训练记忆补票价/营业时间。
 */
object TripResearch {

    /** 一次行程检索的最多来源数（进 UI 清单 + 模型上下文） */
    const val MAX_SOURCES = 12

    data class Result(
        /** 压缩后的证据材料；为空表示这轮一条相关网页都没搜到 */
        val material: String,
        /** 实际进入材料的来源，UI 只回显这些，避免把没用上的网页算成参考 */
        val sources: List<WebHit>,
        /** 本轮候选网页数，用于告诉用户检索规模 */
        val rawCount: Int = 0
    )

    private val PROFILE = WebSearch.SearchProfile.travelDeep()

    suspend fun research(
        destination: String,
        days: Int,
        budget: String,
        preference: String,
        ep: AiEndpoint? = null,
        isLocal: Boolean = false,
        localModel: String = "",
        /** 传入 App Context 时证据会落到 cacheDir 的临时 JSONL，再按槽位读回 */
        context: Context? = null
    ): Result = withContext(Dispatchers.IO) {
        val city = destination.trim()
        if (city.isBlank()) return@withContext Result("", emptyList())

        // 主问句保持简洁，多角度由 planQueries 负责；预算/偏好不塞进检索词，
        // 否则会过度收窄，把「当地真实景点」搜成「某预算方案」。
        val query = "$city ${days}日游 攻略 行程"
        val hits = runCatching {
            withTimeoutOrNull(PROFILE.searchTimeoutMs + 4_000L) {
                WebSearch.search(
                    rawQuery = query,
                    limit = PROFILE.limit,
                    profile = PROFILE,
                    // 行程必须多源覆盖：原话第一层成功也继续展开，不能只凭一条主问句
                    expandAlways = true
                )
            }
        }.getOrNull().orEmpty()

        if (hits.isEmpty()) return@withContext Result("", emptyList())

        // 同站最多 3 条，避免一个营销号/聚合站占满清单；官方、OTA、UGC 都能留下。
        val used = WebSourcePicker.refine(hits, max = MAX_SOURCES, perHost = 3)
        if (used.isEmpty()) return@withContext Result("", emptyList(), hits.size)

        // 抓正文 + 清洗 + 事实裁决。maxBodies=8 对应 12 个来源里挑最可信的 8 篇，
        // 由 SourceTier 优先官方/主流/OTA，UGC 只能作为补充证据。
        val deck = runCatching {
            SeaFusion.buildWithPages(
                query = query,
                ep = ep,
                isLocal = isLocal,
                localModel = localModel,
                maxBodies = PROFILE.maxBodies,
                config = SearchCleaner.Config(
                    minRelevance = 0.30,
                    maxPerSite = 3,
                    maxEvidence = PROFILE.maxEvidence
                ),
                adjudicatorConfig = EvidenceAdjudicator.Config(maxClaims = PROFILE.maxClaims),
                initialHits = used,
                profile = PROFILE
            )
        }.getOrNull()

        val display = SeaFusion.sources(deck).ifEmpty { used }.take(MAX_SOURCES)
        val compact = isLocal

        // 第一段：清洗后的事实卡 / 来源摘要写入临时证据库。
        // 同一轮用固定 key，第二段马上按槽位读回；超过 24 小时的旧文件会被自动清理。
        val storeKey = "$query|$days|$budget|$preference|${System.currentTimeMillis()}"
        val records = run {
            val fromDeck = EvidenceIndex.recordsFrom(deck, query)
            if (fromDeck.isNotEmpty()) fromDeck
            else EvidenceIndex.recordsFromHits(used, query)
        }
        val saved = if (context != null) {
            EvidenceStore.save(context, storeKey, records)
            EvidenceStore.load(context, storeKey).ifEmpty { records }
        } else records

        // 第二段：行程按「门票/开放/交通/路线/美食/住宿/避坑/天气」取证据，
        // 不再把整批网页或整块事实卡一次性塞给模型。
        val slots = EvidenceIndex.tripSlots(days, preference)
        val evidenceHits = EvidenceIndex.retrieve(saved, slots, perSlot = if (compact) 2 else 3)
        val material = buildString {
            appendLine("【临时证据库·两段式生成】")
            appendLine(
                "检索到 ${hits.size} 条候选网页，清洗后写入临时库 ${saved.size} 条，" +
                    "按 ${slots.size} 个需求槽位取用 ${evidenceHits.size} 条。"
            )
            appendLine("真实数据优先级：官方文旅/景区公告 > OTA（携程/去哪儿）> 主流媒体 > 知乎/小红书等 UGC。")
            appendLine()
            if (evidenceHits.isNotEmpty()) {
                append(EvidenceIndex.render(evidenceHits, saved.size, if (compact) 2_600 else 4_800))
            } else {
                append(SeaFusion.noEvidenceBlock(query, used.size, "临时证据库没有命中槽位"))
            }
        }.trim()

        Result(material, display, hits.size)
    }
}
