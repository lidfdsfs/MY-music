package com.example.my

import com.example.my.data.WebHit
import com.example.my.data.WebSafety
import com.example.my.data.WebSourcePicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 参考来源收敛的纯 JVM 单测：判重、站点配额、安全过滤、顺序与上限。
 */
class WebSourcePickerTest {

    private fun hit(title: String, url: String, site: String = "") =
        WebHit(title = title, snippet = "", url = url, site = site)

    @Test
    fun `同一篇文章的跟踪参数变体只保留一条`() {
        val raw = listOf(
            hit("攻略 A", "https://example.com/a?utm_source=wechat&utm_medium=share"),
            hit("攻略 A", "https://example.com/a?spm=123&from=timeline"),
            hit("攻略 B", "https://example.com/b")
        )
        val out = WebSourcePicker.refine(raw)
        assertEquals(2, out.size)
        assertEquals("https://example.com/a?utm_source=wechat&utm_medium=share", out.first().url)
    }

    @Test
    fun `同一站点最多保留两条`() {
        val raw = listOf(
            hit("知乎 1", "https://zhuanlan.zhihu.com/p/1"),
            hit("知乎 2", "https://zhuanlan.zhihu.com/p/2"),
            hit("知乎 3", "https://zhuanlan.zhihu.com/p/3"),
            hit("知乎 4", "https://zhuanlan.zhihu.com/p/4"),
            hit("官网", "https://example.com/x")
        )
        val out = WebSourcePicker.refine(raw, max = 10, perHost = 2)
        assertEquals(3, out.size)
        assertTrue(out.count { WebSafety.hostOf(it.url).contains("zhihu.com") } == 2)
        assertEquals("官网", out.last().title)
    }

    @Test
    fun `伪协议与非法地址不会进入来源列表`() {
        val raw = listOf(
            hit("木马", "javascript:alert(1)"),
            hit("安装包", "https://example.com/a.apk"),
            hit("正常", "https://example.com/page")
        )
        val out = WebSourcePicker.refine(raw)
        assertEquals(1, out.size)
        assertEquals("正常", out.first().title)
    }

    @Test
    fun `顺序保持不变且遵守条数上限`() {
        val raw = (1..12).map { hit("第 $it 条", "https://site$it.example.com/p") }
        val out = WebSourcePicker.refine(raw, max = 8)
        assertEquals(8, out.size)
        assertEquals("第 1 条", out.first().title)
        assertEquals("第 8 条", out.last().title)
    }

    private fun WebSourcePickerHost(url: String): String =
        com.example.my.data.WebSafety.hostOf(url)
}
