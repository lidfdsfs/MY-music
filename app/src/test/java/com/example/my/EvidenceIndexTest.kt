package com.example.my

import com.example.my.data.search.EvidenceIndex
import com.example.my.data.search.EvidenceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceIndexTest {

    private fun record(
        id: String,
        category: String,
        fact: String,
        tier: Int = 2,
        status: String = "高置信",
        sources: List<Int> = listOf(1)
    ) = EvidenceRecord(
        id = id,
        category = category,
        fact = fact,
        status = status,
        title = fact.take(12),
        site = "example.com",
        tier = tier,
        sourceIds = sources
    )

    @Test
    fun `lookup_evidence 按类别和关键词只取命中证据`() {
        val records = listOf(
            record("1", "门票与预约", "洪安古镇门票 80 元，学生半价。"),
            record("2", "交通与车程", "从秀山站到洪安古镇车程约 40 分钟。"),
            record("3", "门票与预约", "边城茶峒古镇免费开放。")
        )
        val hits = EvidenceIndex.lookup(records, "门票与预约", "洪安古镇 门票")
        assertEquals(1, hits.size)
        assertTrue(hits.first().record.fact.contains("80 元"))
    }

    @Test
    fun `lookup_evidence 的筛选也能覆盖全部类别`() {
        val records = listOf(
            record("1", "门票与预约", "洪安古镇门票 80 元。"),
            record("2", "美食", "洪安古镇的米豆腐值得吃。"),
            record("3", "交通与车程", "秀山站到洪安古镇车程 40 分钟。")
        )
        val hits = EvidenceIndex.lookup(records, "全部证据", "洪安古镇")
        assertEquals(3, hits.size)
    }

    @Test
    fun `门票槽位只取门票证据不把车程混进来`() {
        val records = listOf(
            record("1", "门票与预约", "洪安古镇门票 80 元，学生半价。"),
            record("2", "交通与车程", "从秀山站到洪安古镇车程约 40 分钟。"),
            record("3", "开放时间", "景区 08:30-17:30 开放，16:30 停止入场。")
        )
        val slots = EvidenceIndex.tripSlots(5, "自然风光")
            .filter { it.label == "门票与预约" }
        val hits = EvidenceIndex.retrieve(records, slots, perSlot = 2)
        assertEquals(1, hits.size)
        assertTrue(hits.first().record.fact.contains("80 元"))
    }

    @Test
    fun `渲染会标出来源和缺失槽位`() {
        val records = listOf(
            record("1", "门票与预约", "洪安古镇门票 80 元，学生半价。", sources = listOf(2, 4))
        )
        val hits = EvidenceIndex.retrieve(
            records,
            EvidenceIndex.tripSlots(5, "自然风光").filter { it.label == "门票与预约" }
        )
        val text = EvidenceIndex.render(hits, records.size, 2_000)
        assertTrue(text.contains("临时证据库"))
        assertTrue(text.contains("[2,4]"))
        assertTrue(text.contains("开放时间"))
        assertTrue(text.contains("不要编具体数字"))
    }

    @Test
    fun `偏好槽位能把自然风光证据单独取出来`() {
        val records = listOf(
            record("1", "景点与路线", "洪安古镇沿江步道适合看山看水。"),
            record("2", "美食", "当地米豆腐和绿豆粉值得吃。")
        )
        val slots = EvidenceIndex.tripSlots(5, "自然风光")
            .filter { it.label == "偏好重点" }
        val hits = EvidenceIndex.retrieve(records, slots, perSlot = 1)
        assertEquals(1, hits.size)
        assertTrue(hits.first().record.fact.contains("看山看水"))
    }

    @Test
    fun `无清洗证据时网页摘要也能进临时库`() {
        val hit = com.example.my.data.WebHit(
            title = "秀山旅游攻略",
            snippet = "洪安古镇和边城茶峒可以安排一天。",
            url = "https://example.com/a",
            site = "example.com",
            tier = 2,
            score = 0.8
        )
        val records = EvidenceIndex.recordsFromHits(listOf(hit), "秀山 5日游")
        assertEquals(1, records.size)
        assertTrue(records.first().fact.contains("洪安古镇"))
    }
}
