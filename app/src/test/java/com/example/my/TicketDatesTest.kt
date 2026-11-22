package com.example.my

import com.example.my.data.ticket.TicketDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TicketDatesTest {

    @Test
    fun keepsIsoDate() {
        assertEquals("2026-11-12", TicketDates.resolve("2026-11-12"))
        assertEquals("2026-11-12", TicketDates.resolve(" 2026-11-12 "))
    }

    @Test
    fun resolvesChineseRelativeDays() {
        val today = TicketDates.today()
        assertEquals(today, TicketDates.resolve("今天"))
        assertEquals(plus(1), TicketDates.resolve("明天"))
        assertEquals(plus(2), TicketDates.resolve("后天"))
        assertEquals(plus(3), TicketDates.resolve("大后天"))
    }

    @Test
    fun resolvesMonthDayWithoutYear() {
        assertTrue(TicketDates.resolve("12月31日").matches(Regex("^\\d{4}-12-31$")))
    }

    @Test
    fun invalidDateFallsBackToTomorrow() {
        val fallback = plus(1)
        assertEquals(fallback, TicketDates.resolve("2026-13-40"))
        assertEquals(fallback, TicketDates.resolve("随便哪天"))
        assertEquals(fallback, TicketDates.resolve(null))
    }

    @Test
    fun outputIsAlwaysIso() {
        assertTrue(TicketDates.resolve("明天").matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")))
    }

    private fun plus(days: Int): String {
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
    }
}
