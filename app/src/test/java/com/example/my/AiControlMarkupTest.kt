package com.example.my

import com.example.my.ui.screens.stripControlMarkers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiControlMarkupTest {

    @Test
    fun stripsChineseIntentAndNextBlocks() {
        val raw = """
            <意图
            >{"category":"chat","action":"none","label":"介绍歌曲"}</意图>

            《天龙八部之宿敌》是许嵩创作的主题曲。

            <下一步>[{"label":"和半城烟沙对比","prompt":"对比一下"}]</下一步>
        """.trimIndent()

        val clean = stripControlMarkers(raw)

        assertFalse(clean.contains("<意图"))
        assertFalse(clean.contains("</意图>"))
        assertFalse(clean.contains("<下一步"))
        assertFalse(clean.contains("category"))
        assertTrue(clean.contains("《天龙八部之宿敌》"))
    }

    @Test
    fun stripsInternalAuditNote() {
        val raw = "正文\n\n（数据校验：引用编号 [1]、[2]、[3] 不在本次来源列表中）"
        val clean = stripControlMarkers(raw)
        assertFalse(clean.contains("数据校验"))
        assertFalse(clean.contains("引用编号"))
        assertEquals("正文", clean)
    }

    @Test
    fun leavesOrdinaryTextAlone() {
        val raw = "这首歌讲了江湖恩怨，<不是控制标签>。"
        assertEquals(raw, stripControlMarkers(raw))
    }
}
