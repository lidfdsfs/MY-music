package com.example.my

import com.example.my.data.search.OpenSourceSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceSearchTest {

    private val json = """
        {
          "total_count": 5,
          "items": [
            {
              "full_name": "low/star",
              "html_url": "https://github.com/low/star",
              "description": "small tool",
              "stargazers_count": 3,
              "language": "Kotlin",
              "license": {"spdx_id": "MIT"},
              "archived": false,
              "disabled": false,
              "updated_at": "2026-10-01T00:00:00Z"
            },
            {
              "full_name": "top/repo",
              "html_url": "https://github.com/top/repo",
              "description": "a useful offline toolkit",
              "stargazers_count": 1200,
              "language": "Kotlin",
              "license": {"spdx_id": "Apache-2.0"},
              "archived": false,
              "disabled": false,
              "updated_at": "2026-11-01T00:00:00Z"
            },
            {
              "full_name": "no/license",
              "html_url": "https://github.com/no/license",
              "description": "no license",
              "stargazers_count": 9999,
              "language": "Kotlin",
              "license": null,
              "archived": false,
              "disabled": false
            },
            {
              "full_name": "archived/repo",
              "html_url": "https://github.com/archived/repo",
              "description": "archived",
              "stargazers_count": 500,
              "language": "Kotlin",
              "license": {"spdx_id": "MIT"},
              "archived": true,
              "disabled": false
            },
            {
              "full_name": "bad/url",
              "html_url": "https://evil.example.com/bad/url",
              "description": "not github",
              "stargazers_count": 700,
              "language": "Kotlin",
              "license": {"spdx_id": "MIT"},
              "archived": false,
              "disabled": false
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parse_filters_license_archived_and_non_github_urls() {
        val projects = OpenSourceSearch.parseItems(json, 10)
        assertEquals(2, projects.size)
        assertEquals("top/repo", projects[0].fullName)
        assertEquals("Apache-2.0", projects[0].license)
        assertEquals(1200, projects[0].stars)
        assertEquals("low/star", projects[1].fullName)
    }

    @Test
    fun parse_respects_limit() {
        assertEquals(1, OpenSourceSearch.parseItems(json, 1).size)
    }

    @Test
    fun render_contains_license_stars_and_repo_url() {
        val text = OpenSourceSearch.render("kotlin 工具", OpenSourceSearch.parseItems(json, 2))
        assertTrue(text.contains("top/repo"))
        assertTrue(text.contains("Apache-2.0"))
        assertTrue(text.contains("1200"))
        assertTrue(text.contains("https://github.com/top/repo"))
        assertTrue(text.contains("LICENSE"))
    }

    @Test
    fun render_empty_is_honest() {
        val text = OpenSourceSearch.render("不存在", emptyList())
        assertTrue(text.contains("没有找到"))
        assertFalse(text.contains("https://github.com/"))
    }

    @Test
    fun looks_like_query_only_for_open_source_intent() {
        assertTrue(OpenSourceSearch.looksLikeQuery("帮我找免费开源项目"))
        assertTrue(OpenSourceSearch.looksLikeQuery("GitHub 上有没有可复用的图片压缩库"))
        assertTrue(OpenSourceSearch.looksLikeQuery("MCP 搜索找免费项目"))
        assertFalse(OpenSourceSearch.looksLikeQuery("今天大理天气怎么样"))
        assertFalse(OpenSourceSearch.looksLikeQuery("你好"))
    }
}
