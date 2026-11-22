package com.example.my

import com.example.my.data.AiEndpoint
import com.example.my.data.AiFormat
import com.example.my.data.WebHit
import com.example.my.data.search.EvidenceAdjudicator
import com.example.my.data.search.CleanStats
import com.example.my.data.search.FusionProfile
import com.example.my.data.search.SeaDeck
import com.example.my.data.search.NumberGuard
import com.example.my.data.search.SeaFusion
import com.example.my.data.search.SearchCleaner
import com.example.my.data.search.SourceTier
import com.example.my.data.search.Sensitivity
import com.example.my.data.search.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SEA-AI 融合算法 v3 的数值与规则验证（纯 JVM，不联网、不依赖 Android）。
 *
 * 覆盖的是"上线后最容易出错、也最难靠肉眼发现"的四类问题：
 *  1. 清洗：广告/标题党/垃圾站/不相关结果必须被剔除，而不是靠模型"自己别信"；
 *  2. 假共识：同文转载不能把"1 个来源"算成"3 个来源"；
 *  3. 裁决：数字打架必须判成分歧，不能折中；
 *  4. 守门：模型编出来的价格/温度必须被抓到，合法数字不能被误伤。
 */
class SeaFusionTest {

    private fun hit(title: String, snippet: String, site: String, url: String = "https://$site/x") =
        WebHit(title = title, snippet = snippet, url = url, site = site)

    // ------------------------------------------------------------ 清洗

    @Test
    fun `清洗后没有证据也保留原始网页作为参考来源`() {
        val hits = listOf(
            hit("随手记：旅行到底该怎么安排", "聊聊我自己的旅行节奏", "example.com")
        )
        val deck = SeaDeck(
            query = "旅行该怎么安排",
            evidence = emptyList(),
            claims = emptyList(),
            profile = FusionProfile.of(0.3, "端侧模型"),
            stats = CleanStats(rawCount = 1),
            hits = hits
        )
        assertEquals(1, SeaFusion.sources(deck).size)
        assertEquals("随手记：旅行到底该怎么安排", SeaFusion.sources(deck).first().title)
    }

    @Test
    fun `广告标题党和垃圾站必须被硬过滤`() {
        val hits = listOf(
            hit("大理机票特价 广告", "限时抢购，推广位", "mp.weixin.qq.com"),
            hit("震惊！大理机票只要99元", "不看后悔，内部渠道", "sohu.com"),
            hit("大理机票价格查询文档", "免费下载", "book118.com"),
            hit("大理机场航班时刻表", "大理机场今日进出港航班与机票价格，官方发布", "dl.gov.cn")
        )
        val r = SearchCleaner.clean("大理机票价格", hits)
        assertEquals(1, r.evidence.size)
        assertTrue(r.evidence[0].site.endsWith("gov.cn"))
        assertTrue(r.stats.dropped.containsKey("广告推广"))
        assertTrue(r.stats.dropped.containsKey("标题党"))
        assertTrue(r.stats.dropped.containsKey("垃圾站"))
    }

    @Test
    fun `与问题无关的结果不应进入证据集`() {
        val hits = listOf(
            hit("iPhone 17 深度评测", "性能、续航、拍照全面测试", "zealer.com"),
            hit("大理天气预报", "大理今天多云，18到25度，明天有阵雨", "weather.com.cn")
        )
        val r = SearchCleaner.clean("大理天气", hits)
        assertEquals(1, r.evidence.size)
        assertTrue(r.stats.dropped.containsKey("低相关"))
    }

    @Test
    fun `来源分层按域名判定`() {
        assertEquals(1, SourceTier.L1.level)
        assertEquals(2, SourceTier.L2.level)
        val r = SearchCleaner.clean(
            "丽江古城门票",
            listOf(
                hit("丽江古城维护费说明", "丽江古城维护费50元，由古城区政府统一收取，凭票据进入景区。", "ljgc.gov.cn"),
                hit("丽江古城门票攻略", "丽江古城维护费50元，晚上七点后进入免收，适合夜游。", "mafengwo.cn"),
                hit("丽江古城怎么玩", "丽江古城维护费50元，学生和老人有优惠，附办理流程。", "zhihu.com")
            )
        )
        val tiers = r.evidence.map { it.site to it.tier.level }.toMap()
        assertEquals(3, r.evidence.size)
        assertEquals(1, tiers["ljgc.gov.cn"])
        assertEquals(2, tiers["mafengwo.cn"])
        assertEquals(3, tiers["zhihu.com"])
    }

    @Test
    fun `正文只多了来源尾巴的转载仍判为重复`() {
        val a = "玉龙雪山门票价格100元，大索道120元"
        val b = "玉龙雪山门票价格100元，大索道120元（2026最新）"
        val c = "大理古城雨季注意事项与交通指南"
        assertTrue(SearchCleaner.nearDuplicate(a, b))
        assertFalse(SearchCleaner.nearDuplicate(a, c))
        // 长文加"责任编辑/来源"尾巴：Jaccard 被稀释，靠包含度兜住
        val long = "玉龙雪山景区门票100元，大索道120元，进山费100元，开放时间7点到18点，建议提前在官方小程序预约。" +
            "旺季排队时间较长，建议早上八点前到达游客中心换乘环保车，山上温度较低需要带外套。"
        assertTrue(SearchCleaner.nearDuplicate(long, "$long 责任编辑：张某 来源：云南旅游网"))
    }

    // ------------------------------------------------------------ 假共识

    @Test
    fun `同文转载不能把单一来源算成多源一致`() {
        val body = "玉龙雪山门票100元，大索道120元，进山费100元，建议提前一天在小程序预约。" +
            "景区开放时间7点到18点，旺季排队时间较长，建议早上八点前到达游客中心换乘环保车。"
        val hits = listOf(
            hit("玉龙雪山门票和索道价格一览", body, "ctrip.com"),
            hit("玉龙雪山门票价格与预约方式", "$body 本文为转载，仅供参考。", "sohu.com"),
            hit("玉龙雪山门票价格汇总", "$body 内容来自网络整理，版权归原作者。", "163.com")
        )
        val deck = SeaFusion.build("玉龙雪山门票价格", hits)
        val independent = deck.evidence.count { it.independent }
        assertTrue("独立来源被转载放大成了 $independent", independent <= 1)
        val claim = deck.claims.firstOrNull { it.statement.contains("100") }
        assertNotNull(claim)
        assertTrue("单一来源被当成多源一致：support=${claim!!.support}", claim.support <= 1)
    }

    // ------------------------------------------------------------ 裁决

    @Test
    fun `数字互相矛盾时必须判成分歧而不是折中`() {
        val hits = listOf(
            hit("玉龙雪山门票价格调整", "玉龙雪山景区门票价格上调至120元，即日起执行。", "people.com.cn"),
            hit("玉龙雪山门票多少钱", "玉龙雪山景区门票为100元，索道费用另计，学生半价。", "thepaper.cn")
        )
        val deck = SeaFusion.build("玉龙雪山门票价格", hits)
        val disputed = deck.claims.filter { it.verdict == Verdict.DISPUTED }
        assertTrue("冲突没有被识别成分歧：${deck.claims.map { it.verdict }}", disputed.isNotEmpty())
        // 分歧不允许被平均成 110 元
        assertFalse(deck.claims.any { it.statement.contains("110") })
        // 必须带出另一方的说法，供并列呈现
        assertTrue(disputed.any { it.counterStatement.isNotBlank() })
    }

    @Test
    fun `单一权威来源也应给出可用的高置信结论`() {
        val hits = listOf(
            hit(
                "玉龙雪山景区门票价格公示",
                "玉龙雪山景区门票100元，大索道120元，进山费100元，开放时间7:00到18:00。",
                "yn.gov.cn"
            )
        )
        val deck = SeaFusion.build("玉龙雪山门票价格", hits)
        val claim = deck.claims.firstOrNull { it.statement.contains("100") }
        assertNotNull(claim)
        assertTrue(claim!!.confidence > 0.5)
        assertTrue(claim.verdict != Verdict.INSUFFICIENT)
    }

    // ------------------------------------------------------------ 模型档位

    @Test
    fun `弱模型的事实权重全给证据而强模型才有自评权重`() {
        val builtin = AiEndpoint(
            id = 1, name = "智谱 GLM（内置）", format = AiFormat.OPENAI,
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            apiKey = "k", model = "glm-4.7-flash", builtin = true
        )
        val small = SeaFusion.profileFor(null, isLocal = true, localModel = "Qwen2.5-1.5B")
        assertEquals(0.0, small.beta, 1e-6)
        assertFalse(small.allowModelFact)
        assertTrue("端侧 1.5B 的 μ 不应过高：${small.mu}", small.mu < 0.45)

        val flash = SeaFusion.profileFor(builtin, isLocal = false)
        assertTrue(flash.beta > 0.0)
        assertTrue(flash.mu < 0.8)
        assertEquals(1.0, flash.alpha + flash.beta + flash.gamma + flash.delta, 1e-6)

        val strong = SeaFusion.profileFor(
            AiEndpoint(2, "强模型", AiFormat.OPENAI, "https://x/v1", "k", "o3-mini-high"),
            isLocal = false
        )
        assertTrue("推理型模型应判为强档：${strong.mu}", strong.mu >= 0.8)
        assertTrue(strong.allowModelFact)
    }

    @Test
    fun `三档权重配置之和必须为1`() {
        listOf(0.2, 0.6, 0.9).forEach { mu ->
            val p = FusionProfile.of(mu, "m")
            assertEquals(1.0, p.alpha + p.beta + p.gamma + p.delta, 1e-6)
        }
    }

    // ------------------------------------------------------------ 数字守门

    @Test
    fun `编造的价格会被守门员抓到而证据里的数字能通过`() {
        val hits = listOf(
            hit(
                "玉龙雪山门票价格公示",
                "玉龙雪山景区门票100元，大索道120元，开放时间7:00到18:00。",
                "yn.gov.cn"
            )
        )
        val deck = SeaFusion.build("玉龙雪山门票价格", hits)

        val honest = "玉龙雪山门票 100 元，大索道 120 元 [1]。"
        val ok = SeaFusion.auditAnswer(honest, deck)
        assertTrue("合法数字被误伤：${ok.unsupported}", ok.ok)
        assertEquals(1, ok.cited)

        val fabricated = "玉龙雪山门票 150 元，大索道 180 元 [1]。"
        val bad = SeaFusion.auditAnswer(fabricated, deck)
        assertFalse(bad.ok)
        assertTrue(bad.unsupported.any { it.contains("150") })
        assertTrue(bad.unsupported.any { it.contains("180") })
        assertTrue(bad.note().contains("校验"))
    }

    @Test
    fun `不存在的引用编号会被标记`() {
        val hits = listOf(
            hit("玉龙雪山门票价格公示", "玉龙雪山景区门票100元。", "yn.gov.cn")
        )
        val deck = SeaFusion.build("玉龙雪山门票价格", hits)
        val report = SeaFusion.auditAnswer("门票 100 元 [7]。", deck)
        assertTrue(report.badCitations.contains(7))
    }

    // ------------------------------------------------------------ 时效与事实卡

    @Test
    fun `旧日期在时效敏感问题上分数更低`() {
        val hits = listOf(
            hit("大理天气预报", "大理今天多云，18到25度 2026-11-08", "weather.com.cn"),
            hit("大理一周天气趋势与穿衣建议", "大理近期多云，18到25度，早晚偏凉 2025-08-01", "163.com")
        )
        val r = SearchCleaner.clean("大理天气", hits, sensitivity = Sensitivity.DAILY)
        val fresh = r.evidence.firstOrNull { it.published.contains("2026") }
        val old = r.evidence.firstOrNull { it.published.contains("2025") }
        assertNotNull(fresh)
        assertNotNull(old)
        assertTrue("旧结果没有被降权：${fresh!!.score} vs ${old!!.score}", fresh.score > old!!.score)
    }

    @Test
    fun `事实卡的引用编号与来源顺序一一对应`() {
        val hits = listOf(
            hit("玉龙雪山门票价格公示", "玉龙雪山景区门票100元，大索道120元。", "yn.gov.cn"),
            hit("玉龙雪山游玩攻略", "玉龙雪山建议早上出发，索道需排队，门票100元。", "mafengwo.cn")
        )
        val deck = SeaFusion.build("玉龙雪山门票价格", hits)
        val block = deck.block
        assertTrue(block.contains("引用表"))
        assertTrue(block.contains("[1]"))
        assertTrue(block.contains("写作规则"))
        assertTrue(block.contains("不得新增"))
        // 编号严格等于顺序
        deck.evidence.forEachIndexed { i, e ->
            assertTrue(block.contains("[${i + 1}] ${e.tier.name}"))
        }
    }

    @Test
    fun `证据不足时不得编造结论`() {
        val hits = listOf(
            hit("大理旅游注意事项", "大理海拔较高，注意防晒，雨季路滑。", "mafengwo.cn")
        )
        val deck = SeaFusion.build("大理玉龙雪山门票价格", hits)
        // 有一条相关度不足的证据时，要么没有结论，要么结论必须是低置信
        val high = deck.claims.filter {
            it.verdict == Verdict.CONFIRMED &&
                (it.statement.contains("元") || it.statement.contains("门票"))
        }
        assertTrue("证据不足却给出了高置信价格结论：${high.map { it.statement }}", high.isEmpty())
    }

    @Test
    fun `子问题拆解覆盖两个主题`() {
        val topics = EvidenceAdjudicator.topicsOf("大理天气和机票价格")
        assertEquals(2, topics.size)
        assertEquals("大理天气", topics[0])
        assertEquals("机票价格", topics[1])
    }

    @Test
    fun `没有任何结果时不产生事实卡`() {
        val deck = SeaFusion.build("大理天气", emptyList())
        assertTrue(deck.evidence.isEmpty())
        assertNull(SeaFusion.block(deck))
        assertTrue(SeaFusion.noEvidenceBlock("大理天气").contains("没有查到"))
        assertEquals(NumberGuard.Report(), SeaFusion.auditAnswer("随便说点什么", null))
    }
}
