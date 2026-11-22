package com.example.my

import com.example.my.data.WebSafety
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSafetyTest {

    @Test
    fun allowsNormalHttpAndHttps() {
        assertNotNull(WebSafety.sanitize("https://www.gequhai.com/search?s=%E7%BA%A2%E5%B0%98%E5%AE%A2%E6%A0%88"))
        assertNotNull(WebSafety.sanitize("http://cn.bing.com/search?q=test"))
    }

    @Test
    fun blocksDangerousSchemes() {
        // intent:// 能拉起外部应用/安装器，file:///data 能读私有文件，都必须拦
        listOf(
            "intent://install#Intent;package=com.evil;end",
            "javascript:alert(1)",
            "file:///android_asset/evil.html",
            "content://com.android.providers.downloads.documents/1",
            "data:text/html;base64,PHNjcmlwdD4=",
            "market://details?id=com.evil"
        ).forEach { assertNull("应拦截：$it", WebSafety.sanitize(it)) }
    }

    @Test
    fun blocksUserInfoDisguise() {
        // 看起来像百度，实际会跳到 evil.com
        assertNull(WebSafety.sanitize("https://baidu.com@evil.com/malware"))
        assertNull(WebSafety.sanitize("https://user:pass@evil.com/x"))
    }

    @Test
    fun blocksPunycodeAndControlChars() {
        assertNull(WebSafety.sanitize("https://xn--80ak6aa92e.com/"))
        assertNull(WebSafety.sanitize("https://evil.com/a\r\nSet-Cookie: x=1"))
        assertNull(WebSafety.sanitize("https://evil.com/" + "a".repeat(3000)))
        assertNull(WebSafety.sanitize(""))
    }

    @Test
    fun javascriptOnlyOnHttps() {
        assertTrue(WebSafety.allowJavaScript("https://www.gequhai.com/search?s=x"))
        assertFalse(WebSafety.allowJavaScript("http://www.gequhai.com/search?s=x"))
        assertFalse(WebSafety.allowJavaScript("javascript:alert(1)"))
    }

    @Test
    fun detectsInstallerDownloads() {
        assertTrue(WebSafety.isExecutableDownload("https://evil.com/app.apk", "application/octet-stream"))
        assertTrue(WebSafety.isExecutableDownload("https://evil.com/x", "application/vnd.android.package-archive"))
        assertTrue(WebSafety.isExecutableDownload("https://evil.com/payload.exe?x=1", null))
        assertFalse(WebSafety.isExecutableDownload("https://evil.com/song.mp3", "audio/mpeg"))
    }

    @Test
    fun hostExtractionUsedForHeader() {
        assertEquals("gequhai.com", WebSafety.hostOf("https://www.gequhai.com/search?s=x"))
    }
}
