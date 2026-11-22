package com.example.my

import com.example.my.data.ProbedResource
import com.example.my.data.WebResourceProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 资源探的识别规则：只认音频/歌词，伪协议与伪装地址一律不认 */
class WebResourceProbeTest {

    @Test
    fun detectsAudioByExtension() {
        assertEquals(ProbedResource.Kind.AUDIO, WebResourceProbe.kindOf("https://music.example.com/a/%E8%8A%B1%E6%B5%B7.mp3"))
        assertEquals(ProbedResource.Kind.AUDIO, WebResourceProbe.kindOf("https://music.example.com/stream.M4A"))
        assertEquals(ProbedResource.Kind.AUDIO, WebResourceProbe.kindOf("https://music.example.com/get?file=x.flac&t=1"))
    }

    @Test
    fun detectsLyricsFile() {
        assertEquals(ProbedResource.Kind.LYRICS, WebResourceProbe.kindOf("https://music.example.com/%E8%8A%B1%E6%B5%B7.lrc"))
        assertEquals(ProbedResource.Kind.LYRICS, WebResourceProbe.kindOf("https://music.example.com/lrc?id=1", "text/lrc"))
    }

    @Test
    fun detectsByMimeType() {
        assertEquals(ProbedResource.Kind.AUDIO, WebResourceProbe.kindOf("https://music.example.com/download?id=9", "audio/mpeg"))
        assertEquals(
            ProbedResource.Kind.AUDIO,
            WebResourceProbe.kindOf("https://music.example.com/download?id=9", "audio/mpeg; charset=binary")
        )
    }

    @Test
    fun ignoresPagesAndUnsafeSchemes() {
        assertNull(WebResourceProbe.kindOf("https://cn.bing.com/search?q=x"))
        assertNull(WebResourceProbe.kindOf("javascript:alert(1)//a.mp3"))
        assertNull(WebResourceProbe.kindOf("data:audio/mpeg;base64,AAAA"))
        assertNull(WebResourceProbe.kindOf("https://mp3.example.com@evil.example.org/x.mp3"))
    }

    @Test
    fun decodesReadableName() {
        assertEquals(
            "花海-周杰伦.mp3",
            WebResourceProbe.nameOf("https://x.example.com/d/%E8%8A%B1%E6%B5%B7-%E5%91%A8%E6%9D%B0%E4%BC%A6.mp3?a=1")
        )
    }

    @Test
    fun parsesPageScanResult() {
        val raw = "\"[\\\"https://x.example.com/a.mp3\\\",\\\"https://x.example.com/b.lrc\\\"]\""
        val list = WebResourceProbe.parseJsResult(raw)
        assertEquals(2, list.size)
        assertEquals("https://x.example.com/a.mp3", list[0])
        assertEquals("https://x.example.com/b.lrc", list[1])
        assertTrue(WebResourceProbe.parseJsResult("\"[]\"").isEmpty())
        assertTrue(WebResourceProbe.parseJsResult(null).isEmpty())
    }

    @Test
    fun addsWithoutDuplicates() {
        val list = mutableListOf<ProbedResource>()
        assertTrue(WebResourceProbe.add(list, "https://x.example.com/a.mp3"))
        assertTrue(!WebResourceProbe.add(list, "https://x.example.com/a.mp3"))
        assertEquals(1, list.size)
    }
}
