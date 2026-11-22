package com.example.my

import com.example.my.data.music.LyricTranslate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** AI 译词协议的单测：只校验逐行对齐和 JSON 解析，不联网。 */
class LyricTranslateTest {

    @Test
    fun inputUsesJsonArray() {
        assertEquals("[\"第一行\",\"第二行\"]", LyricTranslate.input(listOf("第一行", "第二行")))
    }

    @Test
    fun parsesStrictLineCount() {
        val raw = "```json\n[\"Hello\",\"World\"]\n```"
        assertEquals(listOf("Hello", "World"), LyricTranslate.parse(raw, 2))
    }

    @Test
    fun rejectsWrongLineCount() {
        assertNull(LyricTranslate.parse("[\"Hello\"]", 2))
        assertNull(LyricTranslate.parse("[\"Hello\",\"World\",\"Extra\"]", 2))
    }

    @Test
    fun rejectsBlankOrNonStringItems() {
        assertNull(LyricTranslate.parse("[\"Hello\",\"  \"]", 2))
        assertNull(LyricTranslate.parse("[\"Hello\",1]", 2))
    }

    @Test
    fun rejectsNonJson() {
        assertNull(LyricTranslate.parse("Hello\nWorld", 2))
        assertNull(LyricTranslate.parse(null, 2))
    }
}
