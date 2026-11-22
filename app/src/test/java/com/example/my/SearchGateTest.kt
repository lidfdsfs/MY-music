package com.example.my

import com.example.my.data.ai.SearchGate
import com.example.my.data.ai.SearchIntentScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 检索路由的确定性规则与 JSON 解析，不依赖网络和 Android Context。 */
class SearchGateTest {

    @Test
    fun webOffAlwaysSkips() {
        val d = SearchGate.rule("今天重庆天气怎么样", false)!!
        assertFalse(d.search)
        assertEquals(SearchGate.Source.WEB_OFF, d.source)
    }

    @Test
    fun `开关打开后所有非空问题都强制检索`() {
        listOf(
            "你好",
            "我的行程里有哪些城市",
            "帮我润色这段文案",
            "为什么天空是蓝色的",
            "查一下明天重庆到成都的高铁"
        ).forEach { text ->
            val d = SearchGate.force(text)
            assertTrue("应触发检索：$text", d.search)
            assertEquals(SearchGate.Source.RULE_FORCE, d.source)
        }
        assertTrue(SearchGate.force("查一下明天重庆到成都的高铁").preferInternal)
        assertFalse(SearchGate.force("   ").search)
    }

    @Test
    fun `简单问题走轻量检索复杂问题才深入`() {
        assertEquals(SearchGate.Depth.LIGHT, SearchGate.force("你好").depth)
        assertEquals(SearchGate.Depth.LIGHT, SearchGate.force("帮我翻译这段文案").depth)
        assertEquals(SearchGate.Depth.LIGHT, SearchGate.force("我的足迹").depth)

        assertEquals(SearchGate.Depth.STANDARD, SearchGate.force("今天重庆天气").depth)
        assertEquals(SearchGate.Depth.STANDARD, SearchGate.force("明天重庆到成都的高铁").depth)

        assertEquals(SearchGate.Depth.DEEP, SearchGate.force("我听说黄仁勋辞去了首席执行官").depth)
        assertEquals(SearchGate.Depth.DEEP, SearchGate.force("帮我写一篇重庆三日游攻略").depth)
        assertEquals(SearchGate.Depth.DEEP, SearchGate.force("这条新闻是真的吗").depth)
    }

    @Test
    fun greetingsAndThanksSkip() {
        assertFalse(SearchGate.rule("你好", true)!!.search)
        assertFalse(SearchGate.rule("谢谢你！", true)!!.search)
    }

    @Test
    fun greetingPrefixDoesNotSwallowRealQuery() {
        val d = SearchGate.rule("你好，帮我查一下明天重庆的天气", true)!!
        assertTrue(d.search)
        assertEquals(SearchGate.Source.RULE_FORCE, d.source)
    }

    @Test
    fun ownAppDataStaysLocal() {
        assertFalse(SearchGate.rule("我的行程里有哪些城市", true)!!.search)
        assertFalse(SearchGate.rule("帮我看看我的收藏", true)!!.search)
    }

    @Test
    fun timeSensitiveAndTravelQueriesForceSearch() {
        assertTrue(SearchGate.rule("今天重庆天气怎么样", true)!!.search)
        assertTrue(SearchGate.rule("大理有什么好玩的", true)!!.search)
        assertTrue(SearchGate.rule("帮我写一篇重庆三日游攻略", true)!!.search)
        assertTrue(SearchGate.rule("这句歌词的出处是哪", true)!!.search)
    }

    @Test
    fun pureWritingAndTranslationSkip() {
        assertFalse(SearchGate.rule("帮我润色这段文案", true)!!.search)
        assertFalse(SearchGate.rule("请翻译下面这段介绍", true)!!.search)
        assertFalse(SearchGate.rule("帮我写一段生日祝福", true)!!.search)
    }

    @Test
    fun `人物卸任断言命中必搜而不是交给模型猜`() {
        val d = SearchGate.rule("黄仁勋不在担任英伟达首席执行官", true)!!
        assertTrue(d.search)
        assertEquals(SearchGate.Source.RULE_FORCE, d.source)
    }

    @Test
    fun `纯个人辞职表达不因职务词被误判成外部事实`() {
        // 「我想辞职」没有职务名，不应该被当成公司高管变动去搜索
        val d = SearchGate.rule("我想辞职了，有点累", true)!!
        assertFalse(d.search)
        assertEquals(SearchGate.Source.RULE_SKIP, d.source)
    }

    @Test
    fun ticketQueriesPreferInternalCapability() {
        val d = SearchGate.rule("查一下明天重庆到成都的高铁", true)!!
        assertFalse(d.search)
        assertTrue(d.preferInternal)
        assertEquals(SearchGate.Source.RULE_INTERNAL, d.source)

        val flight = SearchGate.rule("明天重庆飞上海机票多少钱", true)!!
        assertFalse(flight.search)
        assertTrue(flight.preferInternal)
    }

    @Test
    fun ticketRulesAndHowToStillUseWebSearch() {
        assertTrue(SearchGate.rule("火车票退票规则是什么", true)!!.search)
        assertTrue(SearchGate.rule("机票怎么买最便宜", true)!!.search)
    }

    @Test
    fun obviousReasoningIsDecidedByLightScorer() {
        val d = SearchGate.rule("为什么天空是蓝色的", true)!!
        assertFalse(d.search)
        assertEquals(SearchGate.Source.RULE_SCORER, d.source)
        assertEquals(
            SearchIntentScorer.Intent.REASONING,
            SearchIntentScorer.score("为什么天空是蓝色的")!!.intent
        )
        assertFalse(SearchGate.rule("解释一下什么是光合作用", true)!!.search)
    }

    @Test
    fun vagueQuestionIsLeftToModel() {
        assertNull(SearchGate.rule("帮我介绍一下重庆", true))
        assertNull(SearchIntentScorer.score("介绍一下重庆"))
    }

    @Test
    fun lightScorerClassifiesRetrievalCreativeAndTransform() {
        val retrieval = SearchIntentScorer.score("重庆有哪些必去景点")!!
        assertTrue(retrieval.search)
        assertEquals(SearchIntentScorer.Intent.RETRIEVAL, retrieval.intent)

        val creative = SearchIntentScorer.score("帮我写一首关于重庆的诗")!!
        assertFalse(creative.search)
        assertEquals(SearchIntentScorer.Intent.CREATIVE, creative.intent)

        val transform = SearchIntentScorer.score("翻译这段文字")!!
        assertFalse(transform.search)
        assertEquals(SearchIntentScorer.Intent.TRANSFORM, transform.intent)
    }

    @Test
    fun lowConfidenceModelFalseFallsBackToSearch() {
        val d = SearchGate.parse(
            """{"search":false,"confidence":0.50,"reason":"有点拿不准"}"""
        )!!
        assertTrue(d.search)
        assertEquals(SearchGate.Source.FALLBACK, d.source)
    }

    @Test
    fun confidentModelFalseStaysFalse() {
        val d = SearchGate.parse(
            """{"search":false,"confidence":0.90,"reason":"纯常识"}"""
        )!!
        assertFalse(d.search)
        assertEquals(SearchGate.Source.MODEL, d.source)
    }

    @Test
    fun parsesPlainJson() {
        val d = SearchGate.parse("""{"search":true,"reason":"需要实时天气","queries":["重庆天气"]}""")!!
        assertTrue(d.search)
        assertEquals(SearchGate.Source.MODEL, d.source)
        assertEquals(listOf("重庆天气"), d.queries)
    }

    @Test
    fun parsesFencedJsonAndFalseDecision() {
        val d = SearchGate.parse(
            """
            ```json
            {"search": false, "reason": "纯常识解释", "queries": []}
            ```
            """.trimIndent()
        )!!
        assertFalse(d.search)
        assertEquals("纯常识解释", d.reason)
    }

    @Test
    fun rejectsInvalidModelOutput() {
        assertNull(SearchGate.parse("我觉得需要搜索"))
        assertNull(SearchGate.parse("""{"reason":"missing search"}"""))
        assertNull(SearchGate.parse(null))
    }

    @Test
    fun limitsQueriesToThree() {
        val d = SearchGate.parse(
            """{"search":true,"queries":["a","b","c","d"]}"""
        )!!
        assertEquals(3, d.queries.size)
    }
}
