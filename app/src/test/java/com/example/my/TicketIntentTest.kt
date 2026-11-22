package com.example.my

import com.example.my.data.ticket.TicketIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 票务问句识别：只测纯字符串规则，不联网、不依赖 Android。 */
class TicketIntentTest {

    @Test
    fun parsesTrainRouteAndDate() {
        val q = TicketIntent.parse("查一下明天重庆到成都的高铁")!!
        assertEquals(TicketIntent.Kind.TRAIN, q.kind)
        assertEquals("重庆", q.from)
        assertEquals("成都", q.to)
        assertEquals("明天", q.dateHint)
    }

    @Test
    fun parsesFlightRouteWithNoiseWords() {
        val q = TicketIntent.parse("帮我看看明天重庆飞上海机票多少钱")!!
        assertEquals(TicketIntent.Kind.FLIGHT, q.kind)
        assertEquals("重庆", q.from)
        assertEquals("上海", q.to)
    }

    @Test
    fun stripsSuffixQuestionsFromDestination() {
        val q = TicketIntent.parse("重庆到成都有哪些车次")!!
        assertEquals("重庆", q.from)
        assertEquals("成都", q.to)
    }

    @Test
    fun doesNotRejectCitiesStartingWithTimeLikeChars() {
        val q = TicketIntent.parse("查上海到北京的高铁")!!
        assertEquals("上海", q.from)
        assertEquals("北京", q.to)
    }

    @Test
    fun rulesAndWritingAreNotTicketQueries() {
        assertFalse(TicketIntent.looksLikeQuery("火车票退票规则是什么"))
        assertFalse(TicketIntent.looksLikeQuery("机票怎么买最便宜"))
        assertFalse(TicketIntent.looksLikeQuery("帮我写一段火车票文案"))
        assertNull(TicketIntent.parse("机票怎么买"))
    }

    @Test
    fun ticketWordWithoutQueryIntentFallsThrough() {
        assertFalse(TicketIntent.looksLikeQuery("火车票"))
        assertFalse(TicketIntent.looksLikeQuery("航班动态"))
    }

    @Test
    fun shortOrVagueQueriesAreRejected() {
        assertFalse(TicketIntent.looksLikeQuery("查票"))
        assertNull(TicketIntent.parse("查票"))
    }

    @Test
    fun destinationOnlyUsesDefaultOrigin() {
        val q = TicketIntent.parse("明天我要去秀山，帮我看看有哪些火车票", "重庆")!!
        assertEquals(TicketIntent.Kind.TRAIN, q.kind)
        assertEquals("重庆", q.from)
        assertEquals("秀山", q.to)
        assertEquals("明天", q.dateHint)
    }

    @Test
    fun destinationOnlyWithoutDefaultDoesNotInventOrigin() {
        assertNull(TicketIntent.parse("明天我要去秀山，帮我看看有哪些火车票"))
    }

    @Test
    fun stripsIWantPrefixFromExplicitRoute() {
        val q = TicketIntent.parse("我要从重庆去秀山的高铁")!!
        assertEquals("重庆", q.from)
        assertEquals("秀山", q.to)
    }

    @Test
    fun parsesBudgetAndTimeHint() {
        val q = TicketIntent.parse("明天上午重庆到成都，预算 300，坐高铁")!!
        assertEquals(TicketIntent.Kind.TRAIN, q.kind)
        assertEquals("重庆", q.from)
        assertEquals("成都", q.to)
        assertEquals("明天", q.dateHint)
        assertEquals(300, q.budget)
        assertEquals("上午", q.timeHint)
    }

    @Test
    fun sameOriginAndDestinationIsRejected() {
        assertNull(TicketIntent.parse("重庆到重庆的火车票", "重庆"))
    }
}
