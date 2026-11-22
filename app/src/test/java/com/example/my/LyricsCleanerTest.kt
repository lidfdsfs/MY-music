package com.example.my

import com.example.my.data.music.LyricsCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 网页歌词清洗的离线单测：喂一页仿真 HTML，只看洗出来的正文对不对 */
class LyricsCleanerTest {

    private val page = """
        <html><head><title>晴天 - 周杰伦 歌词</title><style>.x{color:red}</style></head>
        <body>
        <div class="nav">首页 搜索 登录 注册 下载</div>
        <p>作词：周杰伦</p><p>作曲：周杰伦</p>
        <div id="lyrics">
        故事的小黄花<br>
        从出生那年就飘着<br>
        童年的荡秋千<br>
        随记忆一直晃到现在<br>
        吹着前奏望着天空<br>
        我想起花瓣试着掉落
        </div>
        <div class="reco">相关推荐 稻香 七里香 收藏 评论 上一首 下一首</div>
        <div class="footer">返回顶部 | Copyright © 2024 | 沪ICP备12345678号 | 扫码关注公众号</div>
        </body></html>
    """.trimIndent()

    @Test
    fun keepsOnlyLyricsAndDropsSiteChrome() {
        val out = LyricsCleaner.extract(page, "晴天", "周杰伦")
        assertNotNull("应该能洗出歌词", out)
        val lines = out!!.lines()
        assertEquals(6, lines.size)
        assertTrue(lines.first() == "故事的小黄花")
        assertTrue(lines.last() == "我想起花瓣试着掉落")
        assertTrue("导航/推荐/制作信息不该混进歌词：$out", out.contains("下载").not())
        assertTrue("同一句歌词的占位字不该保留：$out", out.contains("作词").not())
        assertTrue("页脚/备案/公众号不该混进歌词：$out", out.contains("返回顶部").not())
        assertTrue("备案号不该混进歌词：$out", out.contains("ICP").not())
    }

    @Test
    fun rejectsFooterChromeLines() {
        listOf(
            "返回顶部",
            "Copyright © 2024 lyrics.net.cn",
            "沪ICP备12345678号",
            "扫码关注公众号",
            "上一篇 晴天",
            "Powered by Discuz"
        ).forEach { line ->
            assertTrue("页脚行应被判为非歌词：$line", LyricsCleaner.isLyricLine(line).not())
        }
    }

    @Test
    fun rejectsPageOfAnotherSong() {
        // 页面里没有「稻香」，说明抓到了别的歌的页面，宁可返回空也不能端错词
        assertNull(LyricsCleaner.extract(page, "稻香", "周杰伦"))
    }

    @Test
    fun decodesHtmlEntities() {
        val html = """
            <title>小情歌 歌词</title>
            <div>这是一首简单的小情歌<br>
            唱着人们心肠的曲折<br>
            我想我很快乐 &amp; 当有你的温热<br>
            脚边的空气转了<br>
            这是一首简单的小情歌<br>
            唱着我们心头的白鸽</div>
        """.trimIndent()
        val out = LyricsCleaner.extract(html, "小情歌", "")
        assertNotNull(out)
        assertTrue("&amp; 应还原成 &，实际=$out", out!!.contains("快乐 & 当有你的温热"))
    }

    @Test
    fun needsAtLeastSixLines() {
        val html = "<title>短歌 歌词</title>短歌<br>只有两句<br>什么都不够"
        assertNull(LyricsCleaner.extract(html, "短歌", ""))

        val plain = (1..6).joinToString("\n") { "第${it}句在这里等你" }
        assertNotNull(LyricsCleaner.pick("短歌\n$plain", "短歌"))
        assertNull(LyricsCleaner.pick("短歌\n第1句\n第2句", "短歌"))
    }

    @Test
    fun rejectsProseAndCredits() {
        assertTrue(LyricsCleaner.isLyricLine("我想起花瓣试着掉落"))
        assertTrue(LyricsCleaner.isLyricLine("作曲：周杰伦").not())
        assertTrue(LyricsCleaner.isLyricLine("这首歌讲述了一个关于青春与回忆的漫长故事，听完很有感触。").not())
    }

    /** 真站页面回归：拿一份真实的「晴天」歌词页 HTML，验证洗出来的是干净正文 */
    @Test
    fun realPageSampleExtractsCleanLyrics() {
        val html = javaClass.classLoader!!
            .getResourceAsStream("lyrics_sample.html")!!
            .use { it.readBytes().toString(Charsets.UTF_8) }
        val out = LyricsCleaner.extract(html, "晴天", "周杰伦")
        assertNotNull("真实页面应能洗出歌词", out)
        val lines = out!!.lines()
        assertTrue("歌词行数明显偏少：${lines.size}", lines.size >= 40)
        assertEquals("故事的小黄花", lines.first())
        assertTrue("末行应是歌词而非站点地址：${lines.last()}", lines.last().contains("拜"))
        assertTrue(lines.any { it.contains("从前从前有个人爱你很久") })
        assertTrue("标题/歌手不该当歌词：$lines", lines.none { it == "晴天" || it == "周杰伦" })
        assertTrue("站点噪声不该出现：$out", lines.none { it.contains("查看") || it.contains("首页") || it.contains("版权") })
    }
}
