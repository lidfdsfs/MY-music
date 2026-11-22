package com.example.my

import com.example.my.data.music.SongNameParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongNameParserTest {

    @Test
    fun repairs_compoundArtistBeforeSongTitle() {
        // 截图里的典型反数据：大字是「G.E.M. 邓紫棋」，小字是歌名
        val fixed = SongNameParser.repair("G.E.M. 邓紫棋", "多远都要在一起")
        assertEquals("多远都要在一起", fixed?.first)
        assertEquals("G.E.M. 邓紫棋", fixed?.second)

        val fixed2 = SongNameParser.repair("周杰伦 Jay", "晴天")
        assertEquals("晴天", fixed2?.first)
        assertEquals("周杰伦 Jay", fixed2?.second)
    }

    @Test
    fun keepsAlreadyCorrectOrder() {
        assertNull(SongNameParser.repair("多远都要在一起", "G.E.M. 邓紫棋"))
        assertNull(SongNameParser.repair("晴天", "周杰伦"))
    }

    @Test
    fun keepsEnglishTitleWithChineseArtist() {
        // 英文歌名不能被中文译名歌手误判为“歌手在前”
        assertNull(SongNameParser.repair("Love Story", "泰勒·斯威夫特"))
    }
}
