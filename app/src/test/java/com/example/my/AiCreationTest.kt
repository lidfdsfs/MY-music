package com.example.my

import com.example.my.data.AiApi
import com.example.my.data.AiCapability
import com.example.my.data.AiEndpoint
import com.example.my.data.AiFormat
import com.example.my.data.AiCreationStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 创作专区的离线契约：没有配置/没有视频能力时，必须给出可读原因而不是静默失败 */
class AiCreationTest {

    private fun endpoint(
        format: AiFormat = AiFormat.OPENAI,
        capabilities: Set<AiCapability> = setOf(AiCapability.VIDEO_GEN),
        videoModel: String = ""
    ) = AiEndpoint(
        id = 1L,
        name = "测试端点",
        format = format,
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        apiKey = "test-key",
        model = "glm-4.7-flash",
        videoModel = videoModel,
        capabilities = capabilities
    )

    @Test
    fun videoOutcomeWithoutEndpointExplainsConfiguration() = runBlocking {
        val outcome = AiApi.generateVideoOutcome(null, "海边日落")
        assertEquals(null, outcome.url)
        assertTrue(outcome.error.contains("未配置"))
    }

    @Test
    fun videoOutcomeWithoutVideoCapabilityExplainsCapability() = runBlocking {
        val outcome = AiApi.generateVideoOutcome(
            endpoint(capabilities = setOf(AiCapability.TEXT)), "海边日落"
        )
        assertEquals(null, outcome.url)
        assertTrue(outcome.error.contains("视频生成"))
    }

    @Test
    fun videoOutcomeRejectsNonOpenAiProtocol() = runBlocking {
        val outcome = AiApi.generateVideoOutcome(
            endpoint(format = AiFormat.GEMINI), "海边日落"
        )
        assertEquals(null, outcome.url)
        assertTrue(outcome.error.contains("OpenAI"))
    }

    @Test
    fun videoCreationUsesVideoType() {
        val item = com.example.my.data.AiCreation(
            id = "x",
            type = AiCreationStore.TYPE_VIDEO,
            prompt = "海边日落",
            path = "/tmp/x.mp4",
            createdAt = 1L
        )
        assertTrue(item.isVideo)
    }
}
