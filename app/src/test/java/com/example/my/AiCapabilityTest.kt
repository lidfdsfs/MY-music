package com.example.my

import com.example.my.data.AiCapability
import com.example.my.data.AiEndpoint
import com.example.my.data.AiFormat
import com.example.my.data.AiModelKind
import com.example.my.data.AiPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 一个端点可以同时具备多种能力；旧数据按 kind 推导，不能因为升级丢能力。 */
class AiCapabilityTest {

    @Test
    fun parseCapabilityName() {
        assertEquals(AiCapability.TEXT, AiCapability.of("TEXT"))
        assertEquals(AiCapability.VISION, AiCapability.of("VISION"))
        assertEquals(AiCapability.IMAGE_GEN, AiCapability.of("IMAGE_GEN"))
        assertEquals(AiCapability.VIDEO_GEN, AiCapability.of("VIDEO_GEN"))
        assertEquals(null, AiCapability.of("unknown"))
    }

    @Test
    fun multiCapabilityEndpointSupportsEverythingItClaims() {
        val ep = AiEndpoint(
            id = 1,
            name = "全能模型",
            format = AiFormat.OPENAI,
            baseUrl = "https://example.com/v1",
            apiKey = "k",
            model = "gpt-4o",
            kind = AiModelKind.CHAT,
            capabilities = setOf(
                AiCapability.TEXT,
                AiCapability.VISION,
                AiCapability.IMAGE_GEN
            )
        )
        assertTrue(ep.supports(AiCapability.TEXT))
        assertTrue(ep.supports(AiCapability.VISION))
        assertTrue(ep.supports(AiCapability.IMAGE_GEN))
        assertFalse(ep.supports(AiCapability.VIDEO_GEN))
        assertEquals(AiModelKind.CHAT, ep.primaryKind)
        assertTrue(AiPresets.hasCloudEndpoint(listOf(ep)))
    }

    @Test
    fun legacyEndpointsKeepTheirOldAbility() {
        val image = AiEndpoint(
            id = 2,
            name = "绘图",
            format = AiFormat.OPENAI,
            baseUrl = "https://example.com/v1",
            apiKey = "k",
            model = "gpt-image-1",
            kind = AiModelKind.IMAGE
        )
        assertTrue(image.supports(AiCapability.IMAGE_GEN))
        assertFalse(image.supports(AiCapability.TEXT))
        assertFalse(AiPresets.hasCloudEndpoint(listOf(image)))

        val legacyChat = image.copy(id = 3, kind = AiModelKind.CHAT, model = "deepseek-chat")
        // 旧版看图对话会把图片直接发给对话模型，升级后保留文本 + 图像识别
        assertTrue(legacyChat.supports(AiCapability.TEXT))
        assertTrue(legacyChat.supports(AiCapability.VISION))
    }

    @Test
    fun imageAndVideoCanUseTheirOwnModelNames() {
        val ep = AiEndpoint(
            id = 5,
            name = "智谱",
            format = AiFormat.OPENAI,
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            apiKey = "k",
            model = "glm-4.7-flash",
            imageModel = "cogview-3-flash",
            videoModel = "cogvideox-3",
            capabilities = setOf(
                AiCapability.TEXT,
                AiCapability.VISION,
                AiCapability.IMAGE_GEN,
                AiCapability.VIDEO_GEN
            )
        )
        // 同一端点：聊天用 GLM，绘图必须用 CogView，不能把聊天模型发给绘图接口
        assertEquals("glm-4.7-flash", ep.model)
        assertEquals("cogview-3-flash", ep.image)
        assertEquals("cogvideox-3", ep.video)

        val onlyImage = ep.copy(
            baseUrl = "https://example.com/v1",
            imageModel = "",
            videoModel = ""
        )
        assertEquals("glm-4.7-flash", onlyImage.image)
        assertEquals("glm-4.7-flash", onlyImage.video)
    }

    @Test
    fun zhipuChatModelAutomaticallyUsesCogviewForImages() {
        val ep = AiEndpoint(
            id = 7,
            name = "智谱",
            format = AiFormat.OPENAI,
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            apiKey = "k",
            model = "glm-4.7-flash",
            capabilities = setOf(AiCapability.TEXT, AiCapability.IMAGE_GEN)
        )
        // 老配置没有单独填图片模型，也要能直接出图
        assertEquals("cogview-3-flash", ep.image)
        assertEquals("glm-4.7-flash", ep.model)
    }

    @Test
    fun zhipuImageUrlIsNotDoubled() {
        val base = "https://open.bigmodel.cn/api/paas/v4"
        val full = "$base/images/generations"
        val ep = AiEndpoint(
            id = 6,
            name = "智谱绘图",
            format = AiFormat.OPENAI,
            baseUrl = base,
            apiKey = "k",
            model = "cogview-3-flash",
            kind = AiModelKind.IMAGE
        )
        assertEquals(full, ep.endpointUrl("", "/images/generations"))
        assertEquals(full, ep.endpointUrl("images/generations", "/images/generations"))
        // 用户把完整地址填进 Base URL 时也不能重复拼成 .../images/generations/images/generations
        assertEquals(full, ep.copy(baseUrl = full).endpointUrl("", "/images/generations"))
        // 自定义完整 URL 直接使用
        assertEquals(
            "https://example.com/custom/draw",
            ep.endpointUrl("https://example.com/custom/draw", "/images/generations")
        )
    }
}
