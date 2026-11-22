package com.example.my

import com.example.my.data.music.MusicAi
import com.example.my.data.music.SongResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicAiTest {

    @Test
    fun picksExactTitleAndArtist() {
        val hits = listOf(
            SongResult("1", "晴天", "周杰伦", "https://example.com/1"),
            SongResult("2", "晴天 (Live)", "其他歌手", "https://example.com/2")
        )
        val pick = MusicAi.pickMatch("晴天", "周杰伦", hits)
        assertEquals("1", pick?.id)
    }

    @Test
    fun rejectsHallucinatedTitle() {
        val hits = listOf(
            SongResult("1", "去有风的地方", "郁可唯", "https://example.com/1"),
            SongResult("2", "风吹一夏", "某歌手", "https://example.com/2")
        )
        assertNull(MusicAi.pickMatch("不存在的歌", "不存在的歌手", hits))
    }

    @Test
    fun toleratesTitleSuffixes() {
        val hits = listOf(
            SongResult("1", "多远都要在一起 (Live)", "G.E.M. 邓紫棋", "https://example.com/1")
        )
        val pick = MusicAi.pickMatch("多远都要在一起", "邓紫棋", hits)
        assertEquals("1", pick?.id)
    }
}
