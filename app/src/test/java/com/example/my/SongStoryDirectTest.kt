package com.example.my

import com.example.my.data.music.SongStoryDirect
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class SongStoryDirectTest {

    private val story = "这首歌以雨夜为底色，写一个人在城市里反复回想旧日感情。" +
        "歌词用很克制的语气把想念藏在日常细节里，副歌才把情绪推开。" +
        "整体不是声嘶力竭的告别，而是接受之后仍然留着的牵挂。"

    @Test
    fun knownPrefixIsRemovedAndStoryReturned() {
        val raw = "KNOWN\n$story"
        assertEquals(story, SongStoryDirect.parse(raw))
    }

    @Test
    fun unknownPrefixSkipsWebFallbackDecision() {
        assertNull(SongStoryDirect.parse("UNKNOWN\n这是一首新歌，我不确定。"))
    }

    @Test
    fun naturalLanguageUncertaintyIsTreatedAsUnknown() {
        assertNull(SongStoryDirect.parse("抱歉，我不了解这首歌，无法确定它的创作背景。"))
    }

    @Test
    fun tooShortAnswerIsNotTrusted() {
        assertNull(SongStoryDirect.parse("KNOWN\n这是一首情歌。"))
    }

    @Test
    fun sourceLinesFromModelAreRemovedFromDirectStory() {
        val raw = "KNOWN\n$story\n来源：网络"
        assertEquals(story, SongStoryDirect.parse(raw))
    }

    @Test
    fun thinkingPrefixIsStrippedBeforeParsing() {
        val raw = "\u0001先想想是哪首歌\u0001KNOWN\n$story"
        assertEquals(story, SongStoryDirect.parse(raw))
    }

    @Test
    fun promptCarriesLyricsWhenAvailable() {
        val prompt = SongStoryDirect.buildPrompt("泪海", "许茹芸", "你怎么舍得让我的泪流向海")
        assertTrue(prompt.contains("泪海"))
        assertTrue(prompt.contains("许茹芸"))
        assertTrue(prompt.contains("你怎么舍得让我的泪流向海"))
        assertTrue(prompt.contains("UNKNOWN"))
    }
}
