package com.example.my

import com.example.my.data.AiEndpoint
import com.example.my.data.AiFormat
import com.example.my.data.AiModelKind
import com.example.my.data.AiPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 对话 / 图片 / 视频三类端点必须分开统计，图片和视频不能冒充对话模型。 */
class AiModelKindTest {

    @Test
    fun parseKindDefaultsToChat() {
        assertEquals(AiModelKind.CHAT, AiModelKind.of(null))
        assertEquals(AiModelKind.CHAT, AiModelKind.of(""))
        assertEquals(AiModelKind.IMAGE, AiModelKind.of("IMAGE"))
        assertEquals(AiModelKind.VIDEO, AiModelKind.of("VIDEO"))
    }

    @Test
    fun cloudChatIgnoresMediaEndpoints() {
        val image = AiEndpoint(
            id = 2,
            name = "绘图",
            format = AiFormat.OPENAI,
            baseUrl = "https://example.com/v1",
            apiKey = "k",
            model = "gpt-image-1",
            kind = AiModelKind.IMAGE
        )
        val video = image.copy(id = 3, name = "视频", model = "sora-2", kind = AiModelKind.VIDEO)
        assertFalse(AiPresets.hasCloudEndpoint(listOf(image, video)))

        val chat = image.copy(id = 4, name = "对话", model = "deepseek-chat", kind = AiModelKind.CHAT)
        assertTrue(AiPresets.hasCloudEndpoint(listOf(image, chat)))
        assertFalse(AiPresets.hasCloudEndpoint(emptyList()))
    }
}
