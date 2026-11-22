package com.example.my

import com.example.my.model.CourseTableSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 课表作息时间轴：节次时间推算是排在「按时间排好」的前提，单独锁住 */
class CourseTableTest {

    @Test
    fun defaultSlotTimesAreCorrect() {
        val t = CourseTableSettings()
        // 08:20 开始，45 分钟一节，5 分钟课间
        assertEquals(8 * 60 + 20 to 9 * 60 + 5, t.slot(1))
        // 第 4 节 11:35 结束，午休 120 分钟，第 5 节 13:40 开始、14:25 结束
        assertEquals(13 * 60 + 40 to 14 * 60 + 25, t.slot(5))
        assertEquals("08:20", t.startText(1))
        assertEquals("09:05", t.endText(1))
    }

    @Test
    fun slotRespectsCustomDurationAndNoLunch() {
        val t = CourseTableSettings(
            periodsPerDay = 8,
            lessonMinutes = 40,
            breakMinutes = 10,
            startMinutes = 9 * 60,
            lunchAfter = 0,
            eveningAfter = 0
        )
        assertEquals(9 * 60 to 9 * 60 + 40, t.slot(1))
        assertEquals(10 * 60 + 40 to 11 * 60 + 20, t.slot(3))
        assertEquals("11:20", t.endText(3))
    }

    @Test
    fun customPeriodTimesOverrideAutoSchedule() {
        val t = CourseTableSettings(
            periodsPerDay = 5,
            periodStartMinutes = listOf(8 * 60 + 20, 10 * 60, 14 * 60, 15 * 60 + 30, 19 * 60),
            periodEndMinutes = listOf(9 * 60 + 45, 11 * 60 + 30, 15 * 60 + 20, 16 * 60 + 50, 20 * 60 + 30)
        )
        // 用户逐节设置的时间优先于「45 分钟一节」的自动推导
        assertEquals(8 * 60 + 20 to 9 * 60 + 45, t.slot(1))
        assertEquals(10 * 60 to 11 * 60 + 30, t.slot(2))
        assertEquals(14 * 60 to 15 * 60 + 20, t.slot(3))
        assertEquals("19:00", t.startText(5))
        assertEquals("20:30", t.endText(5))
        assertEquals(true, t.hasCustomPeriodTimes)
    }

    @Test
    fun missingOrInvalidCustomTimeFallsBackToAutoSlot() {
        val auto = CourseTableSettings(periodsPerDay = 3)
        val t = CourseTableSettings(
            periodsPerDay = 3,
            periodStartMinutes = listOf(9 * 60, 11 * 60),
            periodEndMinutes = listOf(10 * 60, 10 * 60)
        )
        // 第 1 节有合法自定义时间
        assertEquals(9 * 60 to 10 * 60, t.slot(1))
        // 第 2 节下课早于上课，视为无效，退回自动推导
        assertEquals(auto.slot(2), t.slot(2))
        // 第 3 节没有自定义值，同样退回自动推导
        assertEquals(auto.slot(3), t.slot(3))
    }

    @Test
    fun parseTimeAcceptsCommonFormats() {
        assertEquals(8 * 60 + 20, CourseTableSettings.parseTime("08:20"))
        assertEquals(14 * 60, CourseTableSettings.parseTime("14：00"))
        assertEquals(9 * 60 + 30, CourseTableSettings.parseTime("0930"))
        assertNull(CourseTableSettings.parseTime("25:00"))
        assertNull(CourseTableSettings.parseTime("abc"))
    }

    @Test
    fun semesterWeekAndDatesFollowSettings() {
        val t = CourseTableSettings(semesterMonday = "2026-09-07")
        val w1 = t.mondayOfWeek(1)
        assertEquals(2026, w1.get(java.util.Calendar.YEAR))
        assertEquals(9, w1.get(java.util.Calendar.MONTH) + 1)
        assertEquals(7, w1.get(java.util.Calendar.DAY_OF_MONTH))
        // 第 10 周的周一是 11/9
        val w10 = t.dateOf(10, 1)
        assertEquals(11, w10.get(java.util.Calendar.MONTH) + 1)
        assertEquals(9, w10.get(java.util.Calendar.DAY_OF_MONTH))
    }
}
