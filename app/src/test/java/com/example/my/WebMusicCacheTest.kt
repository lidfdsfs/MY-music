package com.example.my

import com.example.my.data.music.WebMusicCache
import org.junit.Assert.assertEquals
import org.junit.Test

/** 网页歌曲缓存：歌名/歌手还原 */
class WebMusicCacheTest {

    @Test
    fun titleFromPageWins() {
        val meta = WebMusicCache.resolveMeta("花海 - 周杰伦 - 歌曲海", "track_1024.mp3")
        assertEquals("花海", meta.title)
        assertEquals("周杰伦", meta.artist)
    }

    @Test
    fun stripsSiteTail() {
        val meta = WebMusicCache.resolveMeta("晴天 - 周杰伦 - 歌曲海 GeQuHai.com", "晴天.mp3")
        assertEquals("晴天", meta.title)
        assertEquals("周杰伦", meta.artist)
    }

    @Test
    fun fallbackToDashName() {
        val meta = WebMusicCache.resolveMeta(null, "花海-周杰伦.mp3")
        assertEquals("花海", meta.title)
        assertEquals("周杰伦", meta.artist)
    }

    @Test
    fun plainNameKeepsTitleOnly() {
        val meta = WebMusicCache.resolveMeta(null, "晴天.mp3")
        assertEquals("晴天", meta.title)
        assertEquals("", meta.artist)
    }
}
