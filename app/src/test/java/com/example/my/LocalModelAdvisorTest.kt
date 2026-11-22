package com.example.my

import com.example.my.data.ai.LocalModelAdvisor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下载建议的纯算法测试：不碰 Android 系统信息，只验「按内存 / 存储 / 用途选哪一档」。
 */
class LocalModelAdvisorTest {

    @Test
    fun lowMemoryPicksSmallModel() {
        val rec = LocalModelAdvisor.recommend(
            LocalModelAdvisor.DeviceProfile(totalRamMb = 3_000, freeStorageMb = 10_000, cores = 4),
            needQuality = true
        )
        assertTrue("低配机不该推 1.5B 以上", rec.spec.approxMb <= 500)
        assertTrue(rec.reasonZh.contains(rec.spec.display))
    }

    @Test
    fun midMemoryWithQualityPicksBalancedModel() {
        val rec = LocalModelAdvisor.recommend(
            LocalModelAdvisor.DeviceProfile(totalRamMb = 8_000, freeStorageMb = 6_000, cores = 8),
            needQuality = true
        )
        assertTrue("重任务应选 0.9–1.5GB 的均衡档", rec.spec.approxMb in 900L..1_500L)
    }

    @Test
    fun largeMemoryAndQualityPicksLargerModel() {
        val rec = LocalModelAdvisor.recommend(
            LocalModelAdvisor.DeviceProfile(totalRamMb = 12_000, freeStorageMb = 8_000, cores = 8),
            needQuality = true
        )
        assertTrue("大内存重任务可以上 4B", rec.spec.approxMb >= 2_000)
    }

    @Test
    fun shortTasksPreferLightModel() {
        val rec = LocalModelAdvisor.recommend(
            LocalModelAdvisor.DeviceProfile(totalRamMb = 8_000, freeStorageMb = 6_000, cores = 8),
            needQuality = false
        )
        assertTrue("短任务优先 0.8–1.0GB 的快速档", rec.spec.approxMb in 800L..1_000L)
    }

    @Test
    fun lowStorageFallsBackToSmallestPackage() {
        val rec = LocalModelAdvisor.recommend(
            LocalModelAdvisor.DeviceProfile(totalRamMb = 8_000, freeStorageMb = 500, cores = 8),
            needQuality = true
        )
        assertEquals("存储不够时只能推最小包", 166L, rec.spec.approxMb)
    }

    @Test
    fun memoryBudgetKeepsRecommendationSmallEnough() {
        val rec = LocalModelAdvisor.recommend(
            LocalModelAdvisor.DeviceProfile(totalRamMb = 12_000, freeStorageMb = 8_000, cores = 8),
            needQuality = true,
            memBudgetMb = 2_048L
        )
        assertTrue("2GB 预算不该推 4B", rec.spec.approxMb < 2_000L)
        assertTrue(rec.reasonZh.contains("运行内存预算"))
    }
}
