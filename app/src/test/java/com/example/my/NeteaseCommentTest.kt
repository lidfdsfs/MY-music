package com.example.my

import com.example.my.data.music.CommentSort
import com.example.my.data.music.normalizeApiBase
import com.example.my.ui.screens.formatCommentCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeteaseCommentTest {

    @Test
    fun `sort types follow NeteaseCloudMusicApi contract`() {
        assertEquals(1, CommentSort.RECOMMEND.sortType)
        assertEquals(2, CommentSort.HOT.sortType)
        assertEquals(3, CommentSort.NEW.sortType)
    }

    @Test
    fun `api base accepts https without credentials`() {
        assertEquals(
            "https://ncm.example.com",
            normalizeApiBase(" https://ncm.example.com/ ")
        )
        assertEquals(
            "https://ncm.example.com/api",
            normalizeApiBase("https://ncm.example.com/api")
        )
    }

    @Test
    fun `api base rejects cleartext and unsafe forms`() {
        assertNull(normalizeApiBase("http://localhost:3000"))
        assertNull(normalizeApiBase("https://user:pass@ncm.example.com"))
        assertNull(normalizeApiBase("https://ncm.example.com?x=1"))
        assertNull(normalizeApiBase("https://ncm.example.com#frag"))
        assertNull(normalizeApiBase(" "))
    }

    @Test
    fun `comment count is compacted without a fake zero`() {
        assertEquals("", formatCommentCount(0))
        assertEquals("9999", formatCommentCount(9_999))
        assertEquals("1万", formatCommentCount(10_000))
        assertEquals("1.2万", formatCommentCount(12_345))
        assertEquals("1.1亿", formatCommentCount(110_000_000))
    }
}
