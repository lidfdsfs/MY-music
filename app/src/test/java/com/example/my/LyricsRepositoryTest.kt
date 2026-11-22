package com.example.my

import com.example.my.data.music.LyricsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** LRC 解析的离线单测：一行多时间标签、offset 平移、元数据行、纯文本估时 */
class LyricsRepositoryTest {

    @Test
    fun parsesMultipleTimeTagsAndAppliesOffset() {
        val raw = """
            [ar:周杰伦]
            [ti:千里之外]
            [offset:+500]
            [00:12.34][01:02.34]我送你离开千里之外
            [00:20.00]你无声黑白
        """.trimIndent()
        val lines = LyricsRepository.parseLrc(raw)
        assertEquals(3, lines.size)
        assertEquals(12_840L, lines[0].timeMs)
        assertEquals("我送你离开千里之外", lines[0].text)
        assertEquals(20_500L, lines[1].timeMs)
        assertEquals("你无声黑白", lines[1].text)
        assertEquals(62_840L, lines[2].timeMs)
        assertEquals("我送你离开千里之外", lines[2].text)
    }

    @Test
    fun dropsMetadataAndKeepsChorusAnnotation() {
        val raw = """
            [00:01.00]嘿
            [00:02.00]
            [00:03.00][Chorus]
            [00:04.00]最后一句
        """.trimIndent()
        val lines = LyricsRepository.parseLrc(raw)
        assertEquals(3, lines.size)
        assertTrue(lines.any { it.text == "[Chorus]" })
        assertTrue("空行不能占一条时间轴", lines.none { it.text.isBlank() })
    }

    @Test
    fun estimatesMonotonicTimesInsideDuration() {
        val plain = (1..12).joinToString("\n") { "第${it}句歌词在这里" }
        val lines = LyricsRepository.estimateLines(plain, 240_000L)
        assertEquals(12, lines.size)
        assertEquals(lines.sortedBy { it.timeMs }, lines)
        assertTrue("第一句不能从 0 开始，要留前奏", lines.first().timeMs > 0)
        assertTrue("最后一句必须落在歌曲时长内", lines.last().timeMs < 240_000L)
        assertTrue("相邻行要递增", lines.zipWithNext().all { (a, b) -> b.timeMs > a.timeMs })
    }
}
