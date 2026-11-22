package com.example.my

import com.example.my.data.SearchMode
import com.example.my.data.WebHit
import com.example.my.data.WebSearch
import com.example.my.data.ai.SearchGate
import com.example.my.data.music.SongStoryQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 检索增强的纯规则验证（不联网）：URL 归一、跨站去重、排序权重、正文窗口、歌词检索词。
 * 这些函数直接决定候选池里留谁、谁排前面，是最容易在真实网络里悄悄退化的部分。
 */
class WebSearchTest {

    private fun hit(
        title: String,
        snippet: String = "",
        site: String = "example.com",
        url: String = "https://$site/a"
    ) = WebHit(title = title, snippet = snippet, url = url, site = site)

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.use {
            it.readBytes().toString(Charsets.UTF_8)
        }

    @Test
    fun `真实 Bing RSS 样本能解析出多条可用结果`() {
        val hits = WebSearch.parseBingRss(fixture("bing_rss_sample.xml"))
        assertTrue(hits.size >= 8)
        assertTrue(hits.all { it.title.isNotBlank() })
        assertTrue(hits.any { it.url.startsWith("http") })
        assertTrue(hits.count { it.site.isNotBlank() } >= 8)
    }

    @Test
    fun `真实 Bing HTML 样本能解析出结果卡`() {
        val hits = WebSearch.parseBingHtml(
            fixture("bing_html_sample.html"),
            "https://cn.bing.com/search?q=test"
        )
        assertTrue(hits.size >= 6)
        assertTrue(hits.all { it.title.isNotBlank() })
        assertTrue(hits.all { it.url.startsWith("http") })
    }

    @Test
    fun `交通类问题会生成怎么去角度`() {
        val queries = WebSearch.planQueries("重庆北站 怎么去 解放碑")
        assertTrue(queries.any { it.contains("怎么去") })
    }

    @Test
    fun `人物卸任长句第一层仍先搜原话并准备拆词兜底`() {
        // 第一层必须原话直搜；拆词只作为原话搜不到重要内容时的第二层。
        val queries = WebSearch.planQueries("黄仁勋不在担任英伟达首席执行官")
        assertEquals("黄仁勋不在担任英伟达首席执行官", queries.first())
        assertTrue(queries.contains("黄仁勋 英伟达 CEO 最新消息"))
    }

    @Test
    fun `我听说前缀会被剥掉只留人物与职务`() {
        assertEquals("黄仁勋 CEO", WebSearch.claimQuery("我听说黄仁勋辞去首席执行官"))
    }

    @Test
    fun `人物职务变动会补英文名与官方域名角度`() {
        val withCompany = WebSearch.planQueries("黄仁勋不在担任英伟达首席执行官")
        assertTrue(withCompany.any { it.contains("Jensen Huang") && it.contains("NVIDIA") })
        assertTrue(withCompany.any { it.startsWith("site:nvidia.com") })

        val personOnly = WebSearch.planQueries("我听说黄仁勋辞去首席执行官")
        assertTrue(personOnly.contains("黄仁勋 英伟达 CEO 最新消息"))
        assertTrue(personOnly.any { it.contains("Jensen Huang") && it.contains("NVIDIA") })
        assertTrue(personOnly.any { it.startsWith("site:nvidia.com") })
    }

    @Test
    fun `新鲜事实档会多抓正文并放大上下文`() {
        val fresh = WebSearch.SearchProfile.freshFast()
        assertTrue(fresh.maxBodies >= 3)
        assertTrue(fresh.contextChars >= 1600)
    }

    @Test
    fun `简单问题用轻量档复杂问题才用深档`() {
        val light = WebSearch.SearchProfile.forDecision(
            SearchMode.BALANCED, SearchGate.Depth.LIGHT
        )
        assertEquals(3, light.limit)
        assertEquals(1, light.maxBodies)

        val deep = WebSearch.SearchProfile.forDecision(
            SearchMode.BALANCED, SearchGate.Depth.DEEP
        )
        assertEquals(14, deep.limit)
        assertEquals(6, deep.maxBodies)

        // 用户选了快速档时，复杂问题也封顶在 freshFast，不偷偷跑成深搜
        val capped = WebSearch.SearchProfile.forDecision(
            SearchMode.FAST, SearchGate.Depth.DEEP
        )
        assertEquals(8, capped.limit)
        assertEquals(3, capped.maxBodies)

        // 轻量档只补一级：3 条候选 → 5 条候选，不直接跳到深搜
        val escalated = light.escalate()
        assertEquals(5, escalated.limit)
        assertEquals(1, escalated.maxBodies)
    }

    @Test
    fun `职务变动断言的上下文会要求区分官方与自媒体转述`() {
        val text = WebSearch.render(
            listOf(hit("英伟达 CEO 黄仁勋辞职", "多家媒体转述，未见官方公告。", "news.example.com")),
            "我听说黄仁勋辞去了首席执行官"
        )
        assertTrue(text.contains("说法是否准确"))
        assertTrue(text.contains("官方公告"))
    }

    @Test
    fun `行程规划会同时展开官方OTA与路线交通角度`() {
        val queries = WebSearch.planQueries("重庆 5日游 攻略 行程")
        assertTrue(queries.size >= 7)
        assertTrue(queries.any { it.contains("文旅局") && it.contains("官方") })
        assertTrue(queries.any { it.contains("site:ctrip.com") })
        assertTrue(queries.any { it.contains("交通") && it.contains("车程") })
        val profile = WebSearch.SearchProfile.travelDeep()
        assertEquals(30, profile.limit)
        assertEquals(8, profile.maxBodies)
        assertTrue(profile.angles >= 7)
    }

    @Test
    fun `原话直搜结果是黄姓百科时会判定为不重要`() {
        val noise = listOf(
            hit("黄（汉语文字）_百度百科", "黄，汉语常用字，读作huáng。", "baike.baidu.com"),
            hit("黄色演变史", "黄色是一种颜色。", "sohu.com")
        )
        assertFalse(WebSearch.hasImportantContent(noise, "黄仁勋不在担任英伟达首席执行官"))
    }

    @Test
    fun `原话直搜拿到辞任新闻时会判定为足够重要`() {
        val news = listOf(
            hit(
                "黄仁勋辞任NVIDIA CEO，33年皮衣刀客时代落幕",
                "英伟达宣布创始人兼CEO黄仁勋辞去首席执行官职务，即刻生效。",
                "ifanr.com"
            ),
            hit(
                "英伟达宣布CEO黄仁勋辞职",
                "黄仁勋辞去英伟达CEO、总裁和董事职务，CFO克雷斯接任临时CEO。",
                "news.qq.com"
            )
        )
        assertTrue(WebSearch.hasImportantContent(news, "黄仁勋不在担任英伟达首席执行官"))
    }

    @Test
    fun `普通问题仍然原句优先不被改写`() {
        assertEquals("重庆北站 怎么去 解放碑", WebSearch.planQueries("重庆北站 怎么去 解放碑").first())
    }

    @Test
    fun `快速档遇到时效事实会临时放宽抓取量`() {
        val normal = WebSearch.SearchProfile.forMode(com.example.my.data.SearchMode.FAST)
        val fresh = WebSearch.SearchProfile.forMode(com.example.my.data.SearchMode.FAST, freshNews = true)
        assertTrue(fresh.limit > normal.limit)
        assertTrue(fresh.maxBodies > normal.maxBodies)
        assertTrue(fresh.contextChars > normal.contextChars)
    }

    @Test
    fun `卸任新闻能通过关联度闸门而黄姓百科不能`() {
        val news = hit(
            "黄仁勋辞任NVIDIA CEO，33年皮衣刀客时代落幕",
            "英伟达宣布创始人兼CEO黄仁勋辞去首席执行官职务，即刻生效。",
            "ifanr.com"
        )
        val noise = hit("黄（汉语文字）_百度百科", "黄，汉语常用字，读作huáng。", "baike.baidu.com")
        val q = "黄仁勋不在担任英伟达首席执行官"
        assertTrue(WebSearch.relevanceOf(news, q) >= WebSearch.MIN_RELEVANCE)
        assertTrue(WebSearch.relevanceOf(noise, q) < WebSearch.MIN_RELEVANCE)
    }

    @Test
    fun `答案卡会被解析成可引用摘要`() {
        val html = """
            <li class="b_algo"><h2><strong>重庆天气预报</strong></h2><a href="https://tianqi.com/cq">详情</a>
            <p>重庆未来一周以阴天为主。</p></li>
            <li class="b_ans"><div class="b_ans"><div>重庆今天 18℃ 阴天，湿度 55%，东南风 2 级，空气质量良，出门建议带薄外套。</div></div></li>
        """.trimIndent()
        val hits = WebSearch.parseBingHtml(html, "https://cn.bing.com/search?q=重庆天气")
        assertTrue(hits.any { it.title == "Bing 答案摘要" })
        assertTrue(hits.first { it.title == "Bing 答案摘要" }.snippet.contains("18℃"))
    }

    @Test
    fun `交通线路页要压过百科词条`() {
        val route = hit("重庆北站到解放碑 公交地铁路线", "地铁1号线转2号线", "map.baidu.com")
        val baike = hit("重庆市 百度百科", "重庆，简称渝", "baike.baidu.com")
        assertTrue(WebSearch.rankScore(route, "重庆北站 怎么去 解放碑") > WebSearch.rankScore(baike, "重庆北站 怎么去 解放碑"))
    }

    @Test
    fun `URL 归一化去掉追踪参数与移动子域`() {
        val raw = "https://www.Ctrip.com/hotels/list?utm_source=x&city=1&from=app#top"
        assertEquals("https://ctrip.com/hotels/list?city=1", WebSearch.normalizeUrl(raw))
        assertEquals(
            "https://example.com/a",
            WebSearch.normalizeUrl("https://m.example.com/a/")
        )
    }

    @Test
    fun `同一文章的不同站点与追踪链接只留一条`() {
        val list = listOf(
            hit("重庆天气预报", "18℃ 阴", "a.com", "https://a.com/p?utm_source=x"),
            hit("重庆天气预报", "18℃ 阴", "a.com", "https://a.com/p"),
            hit("重庆天气预报", "18℃ 阴", "b.com", "https://b.com/p"),
            hit("重庆交通大学 2026 招生简章", "录取分数", "c.com", "https://c.com/z")
        )
        val merged = WebSearch.mergeAndRank(list, "重庆天气")
        assertEquals(2, merged.size)
    }

    @Test
    fun `标题命中比摘要命中排得更高`() {
        val titleHit = hit("重庆天气预报 未来一周", "今天多云", "weather.com.cn")
        val snippetHit = hit("重庆旅游攻略", "顺便提一下天气预报", "travel.com")
        assertTrue(WebSearch.rankScore(titleHit, "重庆天气") > WebSearch.rankScore(snippetHit, "重庆天气"))
    }

    @Test
    fun `权威站点优先于百科聚合页`() {
        val official = hit("重庆天气预报", "18℃", "weather.com.cn")
        val baike = hit("重庆天气 百度百科", "气候概况", "baike.baidu.com")
        assertTrue(WebSearch.rankScore(official, "重庆天气") > WebSearch.rankScore(baike, "重庆天气"))
    }

    @Test
    fun `正文抽取跳过导航取信息密度最高的一段`() {
        val html = """
            <html><body>
            <nav>首页 门票 酒店 机票 攻略 登录 注册 收藏 下载客户端 客服中心</nav>
            <header>某某旅游网 全国景点门票预订平台 欢迎您访问本站</header>
            <div>景区门票 80 元，开放时间 08:00-18:00，建议游玩 3 小时，地址：重庆市武隆区。</div>
            <div>预订须知：请提前一天预约，节假日价格可能上浮，具体以景区公告为准。</div>
            <footer>版权所有 免责声明 联系我们 备案号 渝ICP备00000000号</footer>
            </body></html>
        """.trimIndent()
        val text = WebSearch.extractMainText(html)
        assertTrue(text.contains("80"))
        assertTrue(text.contains("开放时间"))
        assertFalse(text.contains("备案号"))
        assertFalse(text.contains("下载客户端"))
    }

    @Test
    fun `标题近重复的跨站转载只留一条`() {
        val list = listOf(
            hit("重庆交通大学2026年招生简章", "招生计划", "a.com", "https://a.com/1"),
            hit("重庆交通大学2026年招生简章完整版", "招生计划", "b.com", "https://b.com/2"),
            hit("重庆交通大学2026年招生简章解读", "分数线", "c.com", "https://c.com/3")
        )
        val merged = WebSearch.mergeAndRank(list, "重庆交通大学 招生")
        assertTrue(merged.size <= 2)
    }

    @Test
    fun `同一站点最多占两条保证结果多样性`() {
        val same = (1..8).map { hit("聚合站结果$it", "", "same.com", "https://same.com/$it") }
        val others = (1..4).map { hit("其他站点$it", "", "site$it.com", "https://site$it.com/$it") }
        val out = WebSearch.diversify(same + others, 6)
        assertEquals(6, out.size)
        assertEquals(2, out.count { it.site == "same.com" })
    }

    @Test
    fun `主体抽取保留歌名与歌手两个词`() {
        val queries = WebSearch.planQueries("晴天 周杰伦 歌词")
        assertTrue(queries.any { it.contains("晴天 周杰伦") })
    }

    @Test
    fun `歌词检索会追加完整版角度`() {
        val queries = WebSearch.planQueries("晴天 周杰伦 歌词")
        assertTrue(queries.any { it.contains("完整版") })
        assertTrue(queries.size <= 4)
    }

    @Test
    fun `歌曲故事检索词带歌名歌手与创作背景`() {
        assertEquals("红尘客栈 周杰伦 创作背景", SongStoryQuery.build("红尘客栈", "周杰伦"))
        assertEquals("红尘客栈 创作背景", SongStoryQuery.build(" 红尘客栈 ", "未知歌手"))
        assertEquals("晴天 周杰伦 创作背景", SongStoryQuery.build(" 晴天  ", " 周杰伦 "))
    }

    @Test
    fun `歌曲故事检索词固定以创作背景收尾且不止两个词`() {
        val q = SongStoryQuery.build("红尘客栈", "周杰伦")
        assertTrue(q.contains("创作背景"))
        assertFalse(q.contains("歌词主题"))
        assertFalse(q.contains("专辑"))
        assertTrue(q.split(' ').size > 2)
        assertTrue(q.endsWith("创作背景"))
    }

    @Test
    fun `歌曲故事真实搜索结果不会被主题闸门全部过滤`() {
        val hits = WebSearch.parseBingRss(fixture("bing_rss_song_story.xml"))
        assertTrue(hits.size >= 8)
        val kept = WebSearch.keepOnTopic(hits, "红尘客栈 周杰伦")
        assertTrue(kept.isNotEmpty())
        assertTrue(kept.any { it.title.contains("红尘客栈") })
    }
}
