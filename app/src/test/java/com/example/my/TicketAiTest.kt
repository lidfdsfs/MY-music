package com.example.my

import com.example.my.data.ticket.FlightOffer
import com.example.my.data.ticket.TicketAi
import com.example.my.data.ticket.TrainQueryResult
import com.example.my.data.ticket.TrainSeat
import com.example.my.data.ticket.TrainTicket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TicketAiTest {

    @Test
    fun parsesTrainSentenceWithDateBudgetAndTime() {
        val query = TicketAi.parseLocal(
            "明天上午从重庆到成都，预算300，坐高铁",
            "2026-11-11"
        )
        assertNotNull(query)
        assertEquals(TicketAi.Kind.TRAIN, query!!.kind)
        assertEquals("重庆", query.from)
        assertEquals("成都", query.to)
        assertEquals("2026-11-12", query.date)
        assertEquals(300, query.budget)
        assertEquals("上午", query.timeHint)
    }

    @Test
    fun parsesFlightSentenceAndDayAfterTomorrow() {
        val query = TicketAi.parseLocal(
            "后天重庆飞北京 预算500",
            "2026-11-11"
        )
        assertNotNull(query)
        assertEquals(TicketAi.Kind.FLIGHT, query!!.kind)
        assertEquals("重庆", query.from)
        assertEquals("北京", query.to)
        assertEquals("2026-11-13", query.date)
        assertEquals(500, query.budget)
    }

    @Test
    fun parsesMonthDayDate() {
        val query = TicketAi.parseLocal("12月3日杭州到上海 高铁", "2026-11-11")
        assertEquals("2026-12-03", query!!.date)
        assertEquals("杭州", query.from)
        assertEquals("上海", query.to)
    }

    @Test
    fun ruleAdvicePrefersShortestAvailableTrain() {
        val trains = TrainQueryResult(
            date = "2026-11-12",
            from = "重庆",
            to = "成都",
            total = 2,
            note = "",
            trains = listOf(
                train("G8501", "07:00", "09:30", "2小时30分"),
                train("G8503", "08:00", "09:40", "1小时40分")
            )
        )
        val advice = TicketAi.ruleAdvice(trains, null)
        assertEquals("G8503", advice!!.pick)
        assertEquals(true, advice.reason.contains("1小时40分"))
    }

    @Test
    fun ruleAdvicePrefersCheapestDirectFlight() {
        val flights = com.example.my.data.ticket.FlightQueryResult(
            offers = listOf(
                flight("CA1001", 520.0, 0),
                flight("CA1002", 430.0, 0),
                flight("CA1003", 380.0, 1)
            ),
            note = ""
        )
        val advice = TicketAi.ruleAdvice(null, flights)
        assertEquals("CA1002", advice!!.pick)
        assertEquals(true, advice.reason.contains("直飞"))
    }

    private fun train(code: String, depart: String, arrive: String, duration: String) =
        TrainTicket(
            code = code,
            fromStation = "重庆北",
            toStation = "成都东",
            departTime = depart,
            arriveTime = arrive,
            duration = duration,
            seats = listOf(TrainSeat("二等座", "有"))
        )

    private fun flight(no: String, price: Double, stops: Int) = FlightOffer(
        airline = "国航",
        airlineCode = "CA",
        flightNo = no,
        fromCode = "CKG",
        toCode = "PEK",
        departTime = "08:00",
        arriveTime = "10:30",
        arriveDate = "",
        durationMinutes = 150,
        stops = stops,
        price = price,
        currency = "CNY",
        baggage = ""
    )
}
