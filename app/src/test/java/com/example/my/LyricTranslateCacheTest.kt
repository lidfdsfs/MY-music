package com.example.my.data.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LyricTranslateCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun translatedLineIsCachedAndCounted() {
        val root: File = tmp.newFolder("files")
        LyricTranslateCache.put(root, "原谅我这一生不羁放纵爱自由", "Forgive my unruly life")
        assertEquals("Forgive my unruly life", LyricTranslateCache.get(root, "原谅我这一生不羁放纵爱自由"))
        assertEquals(1, LyricTranslateCache.count(root))
    }

    @Test
    fun repeatedLineDoesNotCreateDuplicateFiles() {
        val root: File = tmp.newFolder("files")
        LyricTranslateCache.put(root, "海阔天空", "Boundless ocean and sky")
        LyricTranslateCache.put(root, "海阔天空", "Boundless ocean and sky")
        assertEquals(1, LyricTranslateCache.count(root))
    }

    @Test
    fun clearRemovesCacheAndMemory() {
        val root: File = tmp.newFolder("files")
        LyricTranslateCache.put(root, "仍然自由自我", "Still free and myself")
        assertEquals(1, LyricTranslateCache.count(root))
        LyricTranslateCache.clear(root)
        assertEquals(0, LyricTranslateCache.count(root))
        assertNull(LyricTranslateCache.get(root, "仍然自由自我"))
        assertTrue(LyricTranslateCache.summary(root).startsWith("0 句"))
    }

    @Test
    fun differentTargetLanguagesKeepSeparateTranslations() {
        val root: File = tmp.newFolder("files")
        val line = "海阔天空"
        LyricTranslateCache.put(root, line, "Boundless ocean and sky", "en")
        LyricTranslateCache.put(root, line, "Mer et ciel infinis", "fr")
        assertEquals("Boundless ocean and sky", LyricTranslateCache.get(root, line, "en"))
        assertEquals("Mer et ciel infinis", LyricTranslateCache.get(root, line, "fr"))
        assertNull(LyricTranslateCache.get(root, line, "ja"))
        assertEquals(2, LyricTranslateCache.count(root))
    }
}
