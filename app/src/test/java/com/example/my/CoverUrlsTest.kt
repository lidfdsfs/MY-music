package com.example.my

import com.example.my.data.music.CoverUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 封面地址与候选匹配的离线单测：不联网，只验纯函数别贴错封面 */
class CoverUrlsTest {

    @Test
    fun buildsQqCoverOnlyFromSafeAlbumMid() {
        assertEquals(
            "https://y.qq.com/music/photo_new/T002R500x500M0000025NhlN2yWrP4.jpg",
            CoverUrls.qqCover("0025NhlN2yWrP4")
        )
        assertNull(CoverUrls.qqCover(""))
        assertNull("带路径/查询词的输入不能拼进 CDN", CoverUrls.qqCover("../../etc/passwd"))
        assertNull(CoverUrls.qqCover("abc def"))
    }

    @Test
    fun titleAndArtistMustMatchBeforeUsingSearchCover() {
        assertTrue(CoverUrls.matchScore("千里之外", "周杰伦 / 费玉清", "千里之外", "周杰伦 费玉清") >= 100)
        assertTrue(
            "同名不同歌手要扣到阈值以下",
            CoverUrls.matchScore("千里之外", "周杰伦", "千里之外", "某翻唱歌手") < 40
        )
        assertTrue(
            "括号版本应归一化",
            CoverUrls.matchScore("晴天 (Live)", "周杰伦", "晴天", "周杰伦") >= 100
        )
    }

    @Test
    fun extractsBingOriginalImageUrlsAndDeduplicates() {
        val html = """
            <a m="{&quot;murl&quot;:&quot;https:\/\/y.qq.com\/cover\/a.jpg&quot;}"></a>
            <a m="{&quot;murl&quot;:&quot;https:\/\/y.qq.com\/cover\/a.jpg&quot;}"></a>
            <a m="{&quot;murl&quot;:&quot;https:\/\/img.example.com\/b.png&quot;}"></a>
            <a m="{&quot;turl&quot;:&quot;https:\/\/img.example.com\/thumb.jpg&quot;}"></a>
        """.trimIndent()
        val urls = CoverUrls.extractBingImageUrls(html)
        assertEquals(2, urls.size)
        assertTrue(urls.contains("https://y.qq.com/cover/a.jpg"))
        assertTrue(urls.contains("https://img.example.com/b.png"))
    }

    @Test
    fun searchUrlEncodesChineseWithoutRawSpaces() {
        val url = CoverUrls.bingSearchUrl("千里之外", "周杰伦")
        assertTrue(url.startsWith("https://www.bing.com/images/search?q="))
        assertTrue("中文必须被编码，不能留空格", !url.contains(" "))
        assertTrue(url.contains("%E5%8D%83%E9%87%8C%E4%B9%8B%E5%A4%96"))
    }
}
