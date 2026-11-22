package com.example.my

import com.example.my.data.music.HanziScript
import org.junit.Assert.assertEquals
import org.junit.Test

/** 歌词简繁转换的离线单测：覆盖常用字和多义词。 */
class HanziScriptTest {

    @Test
    fun convertsCommonCharacters() {
        assertEquals("我愛你", HanziScript.toTraditional("我爱你"))
        assertEquals("我爱你", HanziScript.toSimplified("我愛你"))
        assertEquals("風吹過", HanziScript.toTraditional("风吹过"))
        assertEquals("风吹过", HanziScript.toSimplified("風吹過"))
    }

    @Test
    fun keepsAmbiguousPhrasesReadable() {
        assertEquals("後來", HanziScript.toTraditional("后来"))
        assertEquals("后来", HanziScript.toSimplified("後來"))
        assertEquals("這裡", HanziScript.toTraditional("这里"))
        assertEquals("这里", HanziScript.toSimplified("這裡"))
        assertEquals("頭髮", HanziScript.toTraditional("头发"))
        assertEquals("头发", HanziScript.toSimplified("頭髮"))
        assertEquals("見面", HanziScript.toTraditional("见面"))
        assertEquals("公里", HanziScript.toTraditional("公里"))
        assertEquals("皇后", HanziScript.toTraditional("皇后"))
    }

    @Test
    fun doesNotTouchOriginalWhenSimplified() {
        val text = "故事的小黄花 从出生那年就飘着"
        assertEquals(text, HanziScript.convert(text, traditional = false))
        assertEquals(text, HanziScript.toSimplified("故事的小黄花 从出生那年就飘着"))
    }

    @Test
    fun keepsLatinAndPunctuation() {
        assertEquals("MY足迹 2026!", HanziScript.toTraditional("MY足迹 2026!"))
    }
}
