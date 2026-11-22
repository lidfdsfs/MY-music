package com.example.my

import com.example.my.data.ai.LocalAiPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 本地 AI 内存预算 / 自动上下文的纯算法测试，不依赖 Android。 */
class LocalAiPolicyTest {

    @Test
    fun recommendedMemoryIsHalfDeviceRamAndCapped() {
        assertEquals(2048, LocalAiPolicy.recommendedMemoryMb(4000))
        assertEquals(3072, LocalAiPolicy.recommendedMemoryMb(6000))
        assertEquals(4096, LocalAiPolicy.recommendedMemoryMb(8000))
        assertEquals(6144, LocalAiPolicy.recommendedMemoryMb(16000))
    }

    @Test
    fun unknownDeviceFallsBackToThreeGb() {
        assertEquals(3072, LocalAiPolicy.recommendedMemoryMb(0))
    }

    @Test
    fun maxMemoryLeavesOneGbForSystem() {
        assertEquals(3072, LocalAiPolicy.maxMemoryMb(4000))
        assertEquals(8192, LocalAiPolicy.maxMemoryMb(0))
    }

    @Test
    fun runtimeEstimateGrowsWithContext() {
        val small = LocalAiPolicy.estimateRuntimeMb(840, 4096)
        val large = LocalAiPolicy.estimateRuntimeMb(840, 32768)
        assertTrue(large > small)
        assertTrue(LocalAiPolicy.fitsMemoryBudget(350, 1024, 4096))
        assertFalse(LocalAiPolicy.fitsMemoryBudget(2600, 2048, 8192))
    }

    @Test
    fun simpleQuestionUsesSmallContext() {
        val tokens = LocalAiPolicy.autoContextTokens("今天天气怎么样？")
        assertEquals(4096, tokens)
    }

    @Test
    fun planningQuestionUsesLargerContext() {
        val tokens = LocalAiPolicy.autoContextTokens(
            "帮我详细规划一下重庆到成都三天的行程，比较几种路线，并给出预算分析和交通建议"
        )
        assertTrue("规划类问题应至少 16K", tokens >= 16384)
    }

    @Test
    fun longMaterialRaisesContext() {
        val tokens = LocalAiPolicy.autoContextTokens("总结下面的材料", contextChars = 15000)
        assertTrue("长材料应至少 16K", tokens >= 16384)
    }

    @Test
    fun manualContextIsClampedByModelLimit() {
        val plan = LocalAiPolicy.resolveContext(
            mode = LocalAiPolicy.ContextMode.MANUAL,
            manualTokens = 65536,
            userText = "你好",
            modelLimit = 8192
        )
        assertEquals(8192, plan.tokens)
        assertTrue(plan.source == "MANUAL_CLAMPED")
    }

    @Test
    fun localContextCoversPromptButHonorsModelLimit() {
        val tokens = LocalAiPolicy.localContextTokens(
            requestedTokens = 4096,
            promptChars = 12000,
            maxNewTokens = 512,
            modelLimit = 16384
        )
        assertTrue("输入很长时应上调，避免读不全", tokens > 4096)
        assertTrue(tokens <= 16384)
    }

    @Test
    fun manualPresetsInclude32_64_84_128K() {
        assertEquals(
            listOf(32_768, 65_536, 86_016, 131_072),
            LocalAiPolicy.MANUAL_CONTEXT_OPTIONS
        )
    }

    @Test
    fun customManualContextIsAcceptedBelowOldFloor() {
        val plan = LocalAiPolicy.resolveContext(
            mode = LocalAiPolicy.ContextMode.MANUAL,
            manualTokens = 2048,
            userText = "你好",
            modelLimit = 131_072
        )
        assertEquals(2048, plan.tokens)
        assertTrue(plan.source == "MANUAL")
    }

    @Test
    fun customManualContextIsClampedByModelWindow() {
        val plan = LocalAiPolicy.resolveContext(
            mode = LocalAiPolicy.ContextMode.MANUAL,
            manualTokens = 524_288,
            userText = "你好",
            modelLimit = 131_072
        )
        assertEquals(131_072, plan.tokens)
        assertTrue(plan.source == "MANUAL_CLAMPED")
    }
}
