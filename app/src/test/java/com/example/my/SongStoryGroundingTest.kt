package com.example.my

import com.example.my.data.music.SongStoryGrounding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongStoryGroundingTest {

    private val materials = listOf(
        SongStoryGrounding.Material(
            1, "红尘客栈", "百度百科",
            "《红尘客栈》是周杰伦演唱的歌曲，由方文山作词，收录在2012年专辑《十二新作》中。"
        ),
        SongStoryGrounding.Material(
            2, "周杰伦谈红尘客栈", "网易云音乐",
            "周杰伦表示这首歌想表达江湖里的儿女情长，编曲用了中国风乐器。"
        )
    )

    @Test
    fun `正文引用了网页原句才通过，并只保留实际引用来源`() {
        val raw = """
            「《红尘客栈》是周杰伦演唱的歌曲」
            这首歌由方文山作词，收录在2012年专辑《十二新作》中，编曲用了中国风乐器。
            来源：1
        """.trimIndent()
        val g = SongStoryGrounding.parse(raw, materials)
        assertNotNull(g)
        assertEquals(listOf(1), g!!.cited)
        assertTrue(g.story.contains("十二新作"))
        assertTrue(g.story.contains("「") || g.story.contains("“"))
    }

    @Test
    fun `编造的引文无法在网页里找到时不通过`() {
        val raw = """
            这首歌据说是周杰伦在2003年写下的，记录了他在伦敦的旅行。
            「这首歌写于伦敦的雨天」
            来源：1
        """.trimIndent()
        assertNull(SongStoryGrounding.parse(raw, materials))
    }

    @Test
    fun `本地模型只有思考没有正文时不通过`() {
        assertNull(SongStoryGrounding.parse("\u0001先想想资料……", materials))
        assertEquals("", SongStoryGrounding.stripThinking("\u0001先想想资料……"))
    }

    @Test
    fun `引文来自第二条却把来源写成第一条时按原句出处纠正`() {
        val raw = """
            「周杰伦表示这首歌想表达江湖里的儿女情长」
            来源：1
        """.trimIndent()
        val g = SongStoryGrounding.parse(raw, materials)
        assertNotNull(g)
        assertEquals(listOf(2), g!!.cited)
    }
}
