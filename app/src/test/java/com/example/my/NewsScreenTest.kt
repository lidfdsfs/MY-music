package com.example.my

import com.example.my.data.news.NewsApi
import com.example.my.data.news.NewsException
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 资讯解析的纯规则测试（不联网）。
 * 样本按真实接口结构手写：60s 早报、today_in_history、hot MCP、dailyhotlist 常见形态。
 */
class NewsScreenTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.use {
            it.readBytes().toString(Charsets.UTF_8)
        }

    @Test
    fun `60s 早报样本能解析日期与条目`() {
        val brief = NewsApi.parseBrief(fixture("news_brief_sample.json"), "测试源")
        assertEquals("2026-11-11", brief.date)
        assertEquals(3, brief.items.size)
        assertTrue(brief.items.first().contains("货物贸易"))
        assertEquals("测试源", brief.source)
    }

    @Test
    fun `早报条目会剥掉 HTML 与零宽字符`() {
        val raw = """
            {"data":{"date":"2026-11-11","news":[
              "正常标题<script>alert(1)</script>",
              "A\u202EB\u200B"
            ]}}
        """.trimIndent()
        val brief = NewsApi.parseBrief(raw)
        assertEquals(2, brief.items.size)
        assertFalse(brief.items[0].contains("<"))
        assertFalse(brief.items[0].contains("script"))
        assertEquals("AB", brief.items[1])
    }

    @Test
    fun `历史上的今天能解析年份标题描述与链接`() {
        val digest = NewsApi.parseHistory(fixture("news_history_sample.json"))
        assertEquals("11-11", digest.date)
        assertEquals(2, digest.items.size)
        assertEquals("1154", digest.items[0].year)
        assertTrue(digest.items[0].title.contains("桑乔一世"))
        assertTrue(digest.items[0].description.contains("葡萄牙"))
        assertTrue(digest.items[0].link.startsWith("https://baike.baidu.com/"))
        assertEquals("1918", digest.items[1].year)
    }

    @Test
    fun `热榜 all 结构按产品顺序排列并保留热度`() {
        val boards = NewsApi.parseHotAll(fixture("news_hot_all_sample.json"))
        assertEquals(3, boards.size)
        assertEquals(listOf("weibo", "zhihu", "unknown_board"), boards.map { it.id })
        assertEquals("微博", boards[0].name)
        assertEquals(2, boards[0].items.size)
        assertEquals(1611483L, boards[0].items.first().hot)
        assertEquals("2026-11-11 23:21", boards[0].updatedAt)
        assertEquals("一个未识别的平台", boards[2].items.first().title)
    }

    @Test
    fun `热榜单平台结构能解析`() {
        val raw = """
            {"platform":"douyin","updated_at":"2026-11-11 23:20","items":[
              {"rank":1,"title":"抖音热点一","hot":987654,"url":"https://www.douyin.com/hot/1"}
            ]}
        """.trimIndent()
        val board = NewsApi.parseHotBoard(raw)
        assertEquals("douyin", board.id)
        assertEquals("抖音", board.name)
        assertEquals(1, board.items.size)
        assertEquals(987654L, board.items.first().hot)
        assertTrue(board.items.first().url.startsWith("https://www.douyin.com/"))
    }

    @Test
    fun `dailyhotlist 常见的 data 数组结构能解析`() {
        val boards = NewsApi.parseDailyHot(fixture("news_daily_sample.json"))
        assertEquals(2, boards.size)
        assertEquals("weibo", boards[0].id)
        assertEquals(1000000L, boards[0].items.first().hot)
        assertEquals("zhihu", boards[1].id)
        assertEquals(200000L, boards[1].items.first().hot)
        assertTrue(boards[1].items.first().url.startsWith("https://www.zhihu.com/"))
    }

    @Test
    fun `非 http 链接不会进入可点击地址`() {
        val raw = """
            {"platform":"weibo","items":[
              {"rank":1,"title":"危险链接","hot":1,"url":"javascript:alert(1)"}
            ]}
        """.trimIndent()
        val board = NewsApi.parseHotBoard(raw)
        assertEquals("", board.items.first().url)
    }

    @Test
    fun `V2EX 热帖能解析排名回复数与安全链接`() {
        val raw = """
            [
              {"title":"有没有人也在用 Compose 做桌面端","url":"https://www.v2ex.com/t/1","replies":42},
              {"title":"<b>今天的网络</b>","url":"http://www.v2ex.com/t/2","replies":0}
            ]
        """.trimIndent()
        val board = NewsApi.parseV2exHot(raw)
        assertEquals("v2ex", board.id)
        assertEquals("V2EX", board.name)
        assertEquals(2, board.items.size)
        assertEquals(1, board.items[0].rank)
        assertEquals(42L, board.items[0].hot)
        assertTrue(board.items[0].url.startsWith("https://www.v2ex.com/"))
        assertFalse(board.items[1].title.contains("<b>"))
        assertNull(board.items[1].hot)
        assertEquals("", board.items[1].url)
    }

    @Test
    fun `GitHub 趋势能解析仓库名星标与 HTTPS 链接`() {
        val raw = """
            {"total_count":2,"items":[
              {"full_name":"owner/trending-repo","html_url":"https://github.com/owner/trending-repo","stargazers_count":1200},
              {"full_name":"another/repo","html_url":"http://github.com/another/repo","stargazers_count":0}
            ]}
        """.trimIndent()
        val board = NewsApi.parseGithubTrending(raw)
        assertEquals("github", board.id)
        assertEquals("GitHub 趋势", board.name)
        assertEquals(2, board.items.size)
        assertEquals("owner/trending-repo", board.items[0].title)
        assertEquals(1200L, board.items[0].hot)
        assertTrue(board.items[0].url.startsWith("https://github.com/"))
        assertNull(board.items[1].hot)
        assertEquals("", board.items[1].url)
    }

    @Test
    fun `Hacker News 能解析标题分数并回落到讨论页`() {
        val raw = """
            {"hits":[
              {"title":"Show HN: A small Compose app","url":"https://example.com/demo","points":321,"objectID":"9001"},
              {"title":"Ask HN: 如何做离线缓存","url":"http://example.com/http","points":0,"objectID":"9002"}
            ]}
        """.trimIndent()
        val board = NewsApi.parseHackerNews(raw)
        assertEquals("hackernews", board.id)
        assertEquals("Hacker News", board.name)
        assertEquals(2, board.items.size)
        assertEquals(321L, board.items[0].hot)
        assertEquals("https://example.com/demo", board.items[0].url)
        assertNull(board.items[1].hot)
        assertEquals("https://news.ycombinator.com/item?id=9002", board.items[1].url)
    }

    @Test
    fun `新热榜源的空结构与异常结构会明确报错`() {
        val v2exError = runCatching { NewsApi.parseV2exHot("""{"items":[]}""") }.exceptionOrNull()
        val githubError = runCatching { NewsApi.parseGithubTrending("""{"items":[]}""") }.exceptionOrNull()
        assertTrue(v2exError is NewsException)
        assertTrue(githubError is NewsException)
        assertTrue(runCatching { NewsApi.parseV2exHot("not-json") }.exceptionOrNull() is NewsException)
    }

    @Test
    fun `结构异常会抛出明确错误而不是返回空数据`() {
        val raw = """{"code":500,"message":"bad"}"""
        val error = runCatching { NewsApi.parseBrief(raw) }.exceptionOrNull()
        assertTrue(error is NewsException)
        assertEquals(null, runCatching { NewsApi.parseHistory(raw) }.getOrNull())
        assertNull(runCatching { NewsApi.parseHotAll(raw) }.getOrNull())
    }
}
