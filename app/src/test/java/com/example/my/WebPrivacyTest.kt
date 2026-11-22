package com.example.my

import com.example.my.data.WebPrivacy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 隐私：追踪域名识别 + 人机验证页识别 */
class WebPrivacyTest {

    @Test
    fun blocksTrackerHosts() {
        assertTrue(WebPrivacy.isTracker("https://www.google-analytics.com/collect?v=1"))
        assertTrue(WebPrivacy.isTracker("https://hm.baidu.com/hm.js?abc"))
        assertTrue(WebPrivacy.isTracker("https://connect.facebook.net/en_US/sdk.js"))
        assertTrue(WebPrivacy.isTracker("https://stats.g.doubleclick.net/jump"))
        assertFalse(WebPrivacy.isTracker("https://www.gequhai.com/search?s=x"))
        assertFalse(WebPrivacy.isTracker("https://notgoogle-analytics.com.example.org/"))
        assertFalse(WebPrivacy.isTracker(null))
    }

    @Test
    fun detectsChallengePages() {
        assertTrue(WebPrivacy.looksLikeChallenge("https://challenges.cloudflare.com/cdn-cgi/challenge", "x"))
        assertTrue(WebPrivacy.looksLikeChallenge("https://www.example.com/captcha?next=/a", ""))
        assertTrue(WebPrivacy.looksLikeChallenge("https://www.example.com/", "Just a moment..."))
        assertTrue(WebPrivacy.looksLikeChallenge("https://www.example.com/", "请完成安全验证"))
        assertFalse(WebPrivacy.looksLikeChallenge("https://www.gequhai.com/", "花海 - 周杰伦"))
        assertFalse(WebPrivacy.looksLikeChallenge(null, null))
    }

    @Test
    fun detectsChallengeStatusCodes() {
        assertTrue(WebPrivacy.isChallengeStatus(403))
        assertTrue(WebPrivacy.isChallengeStatus(429))
        assertTrue(WebPrivacy.isChallengeStatus(503))
        assertFalse(WebPrivacy.isChallengeStatus(404))
        assertFalse(WebPrivacy.isChallengeStatus(200))
    }

    @Test
    fun uaHasNoAppFingerprint() {
        assertFalse(WebPrivacy.STANDARD_UA.contains("MYZuji"))
        assertTrue(WebPrivacy.STANDARD_UA.startsWith("Mozilla/5.0"))
    }
}
