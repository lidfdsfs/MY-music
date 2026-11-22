package com.example.my

import com.example.my.data.CourseImporter
import com.example.my.data.CourseImporter.Box
import com.example.my.data.CourseImporter.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表几何还原的离线单测：直接喂「文本框 + 坐标」，不依赖 OCR 模型和图片。
 */
class CourseImporterTest {

    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        Line(text, Box(left, top, right, bottom))

    private fun headers(y: Int = 100): List<Line> = listOf(
        "周一" to 150, "周二" to 280, "周三" to 410, "周四" to 540,
        "周五" to 670, "周六" to 800, "周日" to 930
    ).map { (t, cx) -> line(t, cx - 30, y, cx + 30, y + 30) }

    private fun periods(): List<Line> = listOf(220, 320, 420, 520, 620).mapIndexed { i, cy ->
        line("${i + 1}", 10, cy - 20, 40, cy + 20)
    }

    @Test
    fun readsHeadersPeriodsAndCellText() {
        val lines = headers() + periods() + listOf(
            line("高等数学", 250, 200, 330, 232),
            line("张老师", 250, 236, 320, 256),
            line("教1-201", 250, 258, 330, 278),
            line("大学英语", 490, 390, 590, 422),
            line("王老师", 490, 424, 560, 444),
            line("外语楼302", 490, 446, 590, 466)
        )

        val r = CourseImporter.parseLines(lines)
        assertTrue(
            "实际解析=${r.courses.joinToString { "${it.dayOfWeek}/${it.startPeriod}:${it.name}" }}",
            r.courses.size == 2
        )

        val math = r.courses.first { it.name == "高等数学" }
        assertEquals(2, math.dayOfWeek)
        assertEquals(1, math.startPeriod)
        assertEquals("张老师", math.teacher)
        assertTrue("地点应含 1-201，实际=${math.location}", math.location.contains("201"))

        val english = r.courses.first { it.name == "大学英语" }
        assertEquals(4, english.dayOfWeek)
        assertEquals(3, english.startPeriod)
        assertEquals("王老师", english.teacher)
        assertTrue("地点应含 外语楼302，实际=${english.location}", english.location.contains("外语楼"))
    }

    @Test
    fun mergesSameCourseInAdjacentPeriods() {
        val lines = headers() + periods() + listOf(
            line("数据结构", 250, 200, 330, 232),
            line("数据结构", 250, 300, 330, 332),
            line("李老师", 250, 336, 320, 356)
        )

        val r = CourseImporter.parseLines(lines)
        assertEquals(1, r.courses.size)
        val c = r.courses.first()
        assertEquals("数据结构", c.name)
        assertEquals(1, c.startPeriod)
        assertEquals(2, c.endPeriod)
    }

    @Test
    fun fallsBackWhenNoHeaderOrPeriodLabels() {
        val lines = listOf(
            line("数学分析", 120, 200, 200, 230),
            line("英语", 520, 200, 600, 230)
        )

        val r = CourseImporter.parseLines(lines)
        assertEquals(2, r.courses.size)
        assertTrue("缺少表头时应提示均分 7 列，实际=${r.note}", r.note.contains("均分"))
        assertEquals(1, r.courses.minByOrNull { it.dayOfWeek }?.dayOfWeek)
        assertEquals(7, r.courses.maxByOrNull { it.dayOfWeek }?.dayOfWeek)
        assertTrue(r.courses.all { it.startPeriod == 1 })
    }

    @Test
    fun ignoresTimetableNoise() {
        val lines = headers() + periods() + listOf(
            line("课程表", 400, 40, 520, 70),
            line("作息时间", 600, 40, 700, 70),
            line("高等数学", 250, 200, 330, 232)
        )

        val r = CourseImporter.parseLines(lines)
        assertEquals(1, r.courses.size)
        assertEquals("高等数学", r.courses.first().name)
    }

    @Test
    fun normalizesFullWidthDigitsAndChinesePeriods() {
        val lines = listOf(
            line("星期一", 120, 100, 180, 130),
            line("星期二", 250, 100, 310, 130),
            line("星期三", 380, 100, 440, 130),
            line("第１节", 10, 200, 60, 230),
            line("第二节", 10, 300, 60, 330),
            line("高等数学（1-16周）", 120, 200, 190, 230),
            line("张老师", 120, 232, 180, 252),
            line("教１－２０１", 120, 254, 200, 274),
            line("大学物理", 120, 350, 190, 380)
        )

        val r = CourseImporter.parseLines(lines)
        assertEquals("实际解析=${r.courses.joinToString { "${it.dayOfWeek}/${it.startPeriod}-${it.endPeriod}:${it.name}" }}",
            2, r.courses.size)

        val math = r.courses.first { it.name == "高等数学" }
        assertEquals(1, math.dayOfWeek)
        assertEquals(1, math.startPeriod)
        assertEquals("周次注记不应混进课名", "高等数学", math.name)
        assertTrue("全角地点应还原，实际=${math.location}", math.location.contains("201"))

        val physics = r.courses.first { it.name == "大学物理" }
        assertEquals(2, physics.startPeriod)
    }

    @Test
    fun mergesChineseNumberedPeriods() {
        val lines = headers() + listOf(
            line("第一节", 10, 200, 60, 230),
            line("第二节", 10, 300, 60, 330),
            line("数据结构", 250, 200, 330, 232),
            line("数据结构", 250, 300, 330, 332)
        )

        val r = CourseImporter.parseLines(lines)
        assertEquals(1, r.courses.size)
        assertEquals(1, r.courses.first().startPeriod)
        assertEquals(2, r.courses.first().endPeriod)
    }
}
