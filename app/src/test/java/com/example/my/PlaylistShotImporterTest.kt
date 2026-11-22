package com.example.my

import com.example.my.data.music.PlaylistShotImporter
import com.example.my.data.music.PlaylistShotImporter.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistShotImporterTest {

    @Test
    fun parses_stacked_title_and_artist_rows() {
        val lines = listOf(
            Line("我的收藏", 20, 0, 180, 24),
            Line("1. 天龙八部之宿敌", 20, 40, 520, 64),
            Line("许嵩", 20, 66, 90, 88),
            Line("2. 素颜", 20, 120, 300, 144),
            Line("许嵩 何曼婷", 20, 146, 200, 168)
        )
        val items = PlaylistShotImporter.parseLines(lines)
        assertEquals(2, items.size)
        assertEquals("天龙八部之宿敌", items[0].title)
        assertEquals("许嵩", items[0].artist)
        assertEquals("素颜", items[1].title)
        assertEquals("许嵩 何曼婷", items[1].artist)
    }

    @Test
    fun parses_same_row_title_and_artist() {
        val lines = listOf(
            Line("天龙八部之宿敌", 20, 40, 420, 64),
            Line("许嵩", 760, 44, 830, 62),
            Line("03:24", 1080, 44, 1140, 62)
        )
        val items = PlaylistShotImporter.parseLines(lines)
        assertEquals(1, items.size)
        assertEquals("天龙八部之宿敌", items[0].title)
        assertEquals("许嵩", items[0].artist)
    }

    @Test
    fun splits_inline_title_and_artist() {
        val items = PlaylistShotImporter.parseLines(
            listOf(Line("天龙八部之宿敌 - 许嵩", 20, 20, 500, 44))
        )
        assertEquals(1, items.size)
        assertEquals("天龙八部之宿敌", items[0].title)
        assertEquals("许嵩", items[0].artist)
    }

    @Test
    fun drops_headers_durations_and_duplicates() {
        val lines = listOf(
            Line("播放全部", 20, 0, 160, 24),
            Line("天龙八部之宿敌", 20, 40, 420, 64),
            Line("许嵩", 760, 44, 830, 62),
            Line("03:24", 1080, 44, 1140, 62),
            Line("天龙八部之宿敌", 20, 100, 420, 124),
            Line("许嵩", 760, 104, 830, 122)
        )
        val items = PlaylistShotImporter.parseLines(lines)
        assertEquals(1, items.size)
        assertTrue(items.none { it.title == "播放全部" })
    }

    @Test
    fun detectsArtistBeforeSongTitleByShape() {
        assertTrue(
            PlaylistShotImporter.looksSwappedByShape(
                PlaylistShotImporter.Item("G.E.M. 邓紫棋", "多远都要在一起")
            )
        )
        assertTrue(
            !PlaylistShotImporter.looksSwappedByShape(
                PlaylistShotImporter.Item("多远都要在一起", "G.E.M. 邓紫棋")
            )
        )
        // 英文歌名 + 中文译名歌手不能被误换
        assertTrue(
            !PlaylistShotImporter.looksSwappedByShape(
                PlaylistShotImporter.Item("Love Story", "泰勒·斯威夫特")
            )
        )
    }

    @Test
    fun parsesLocalAiSwapReplyOnly() {
        val reply = """
            1|SWAP
            2|OK
            3|swap
            4|交换
            5|不确定
            6|OK 因为...
        """.trimIndent()
        val swapped = PlaylistShotImporter.parseSwapReply(reply)
        assertEquals(setOf(1, 3, 4), swapped)
        assertEquals(emptySet<Int>(), PlaylistShotImporter.parseSwapReply("没有可解析的结果"))
    }
}
