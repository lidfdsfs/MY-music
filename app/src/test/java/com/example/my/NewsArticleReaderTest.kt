package com.example.my

import com.example.my.data.news.NewsArticleReader
import com.example.my.data.news.NewsBlockKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 正文解析的纯规则测试：不联网，只验证“完整保留顺序、过滤噪音、图片转绝对地址”。 */
class NewsArticleReaderTest {

    private val html = """
        <!doctype html>
        <html><head>
          <meta charset="utf-8">
          <title>fallback title</title>
          <meta property="og:title" content="测试文章 - 示例站">
          <meta property="og:description" content="这是一段导语，用来验证摘要提取。">
          <meta property="og:site_name" content="示例站">
          <meta property="og:image" content="/cover.jpg">
          <meta property="article:published_time" content="2026-11-12T08:30:00+08:00">
        </head><body>
          <script>alert(1)</script>
          <article>
            <h1>测试文章</h1>
            <p>第一段正文内容，长度足够进入解析结果。</p>
            <img src="/logo.png" alt="站点 logo">
            <figure>
              <img src="/img/a.jpg" alt="瀑布">
              <figcaption>图片说明</figcaption>
            </figure>
            <blockquote>这是一段被引用的原话，应该单独成块。</blockquote>
            <ul><li>条目一</li><li>条目二</li></ul>
            <p>第二段正文，带一张内嵌图片<img src="/img/c.jpg" alt="内嵌图">继续写。</p>
            <img data-src="https://cdn.example.com/b.png" alt="尾图">
            <p>相关阅读：别把这条当成正文。</p>
          </article>
        </body></html>
    """.trimIndent()

    @Test
    fun `正文按原始顺序保留段落图片引用和条目`() {
        val article = NewsArticleReader.parse(html, "https://example.com/news/1")
        assertEquals("测试文章 - 示例站", article.title)
        assertEquals("示例站", article.site)
        assertEquals("2026-11-12T08:30:00+08:00", article.publishedAt)
        assertEquals("https://example.com/cover.jpg", article.coverImage)

        val kinds = article.blocks.map { it.kind }
        assertEquals(
            listOf(
                NewsBlockKind.HEADING,
                NewsBlockKind.PARAGRAPH,
                NewsBlockKind.IMAGE,
                NewsBlockKind.QUOTE,
                NewsBlockKind.BULLET,
                NewsBlockKind.BULLET,
                NewsBlockKind.PARAGRAPH,
                NewsBlockKind.IMAGE,
                NewsBlockKind.PARAGRAPH,
                NewsBlockKind.IMAGE
            ),
            kinds
        )

        val images = article.blocks.filter { it.kind == NewsBlockKind.IMAGE }.map { it.imageUrl }
        assertEquals(
            listOf(
                "https://example.com/img/a.jpg",
                "https://example.com/img/c.jpg",
                "https://cdn.example.com/b.png"
            ),
            images
        )
        assertTrue(article.blocks.first { it.kind == NewsBlockKind.IMAGE }.caption.contains("图片说明"))
        assertTrue(article.blocks.any { it.text == "第一段正文内容，长度足够进入解析结果。" })
        assertTrue(article.blocks.any { it.text.contains("第二段正文") })
        assertTrue(
            article.blocks.joinToString("\n") { "${it.kind}:${it.text}|${it.imageUrl}" },
            article.blocks.any { it.text.contains("继续写。") }
        )
    }

    @Test
    fun `脚本 logo 和短噪音不会进入正文`() {
        val article = NewsArticleReader.parse(html, "https://example.com/news/1")
        val text = article.blocks.joinToString("\n") { it.text }
        assertFalse(text.contains("alert(1)"))
        assertFalse(text.contains("logo"))
        assertFalse(text.contains("相关阅读"))
        assertFalse(article.blocks.any { it.imageUrl.contains("logo") })
    }

    @Test
    fun `没有正文时用描述兜底而不是返回空文章`() {
        val article = NewsArticleReader.parse(
            """<html><head><meta name="description" content="只有描述，没有正文段落。"></head><body></body></html>""",
            "https://example.com/empty"
        )
        assertEquals(1, article.blocks.size)
        assertEquals("只有描述，没有正文段落。", article.blocks.first().text)
    }
}
