package com.example.my

import com.example.my.data.ai.CapabilityRegistry
import com.example.my.data.ai.LocalCommandRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 助手接入设置的链路测试：本地兜底规则 → CommandIntent → 能力表校验。
 * 只测纯逻辑（不碰 Context / AppState），保证「说一句话真能落到对应设置」。
 */
class AiCommandRoutingTest {

    private fun kindOf(text: String): String? = LocalCommandRules.parse(text)?.kind

    private fun targetOf(text: String): String? = LocalCommandRules.parse(text)?.target

    @Test
    fun appearance_commands() {
        assertEquals("theme", kindOf("打开深色模式"))
        assertEquals("dark", targetOf("切换深色模式"))
        assertEquals("light", targetOf("换成浅色"))
        assertEquals("system", targetOf("切回跟随系统"))
        // 问句不该被当成命令
        assertNull(LocalCommandRules.parse("深色模式好不好看"))
    }

    @Test
    fun haptic_commands() {
        assertEquals("haptic", kindOf("关掉震动"))
        assertEquals("off", targetOf("关掉触感反馈"))
        assertEquals("on", targetOf("打开触感开关"))
        assertNull(LocalCommandRules.parse("触感反馈是什么"))
    }

    @Test
    fun language_commands() {
        assertEquals("app_language", kindOf("改成英文"))
        assertEquals("en", targetOf("把界面切成英文"))
        assertEquals("zh", targetOf("切回中文"))
        // 翻译是文本任务，不是切界面语言
        assertNull(LocalCommandRules.parse("把这句话翻译成英文"))
    }

    @Test
    fun settings_deep_link() {
        assertEquals("settings_page", kindOf("打开设置里的回收站"))
        assertEquals("trash", targetOf("打开设置里的回收站"))
        assertEquals("local_model", targetOf("进入设置中的本地模型"))
        assertEquals("main", targetOf("打开设置"))
    }

    @Test
    fun page_and_switch_commands() {
        assertEquals("open", kindOf("打开股市行情"))
        assertEquals("stock", targetOf("打开股市行情"))
        assertEquals("open", kindOf("打开资讯"))
        assertEquals("setting", kindOf("关闭消息通知"))
        assertEquals("false", LocalCommandRules.parse("关闭消息通知")?.value)
        assertEquals("true", LocalCommandRules.parse("打开语音播报")?.value)
    }

    @Test
    fun commands_map_to_registered_capabilities() {
        // 能力表里必须真的登记了这几项，否则会被网关判成能力缺口
        listOf("set_theme", "set_haptic", "set_app_language", "open_settings_section", "open_screen")
            .forEach { assertNotNull("能力 $it 未登记", CapabilityRegistry.find(it)) }

        // 本地规则产出的每个 target 都必须落在能力的枚举白名单里，否则会被参数校验拒掉
        val sections = CapabilityRegistry.find("open_settings_section")!!
            .params.first { it.name == "target" }.values
        listOf("打开设置里的回收站", "进入设置中的本地模型", "打开设置里的语音库").forEach { text ->
            val target = targetOf(text)!!
            assertTrue("$text -> $target", sections.contains(target))
        }

        val pages = CapabilityRegistry.find("open_screen")!!
            .params.first { it.name == "target" }.values
        listOf("打开股市行情", "打开资讯", "打开课程表", "打开年度报告").forEach { text ->
            val target = targetOf(text)!!
            assertTrue("$text -> $target", pages.contains(target))
        }

        assertTrue(CapabilityRegistry.find("set_theme")!!.params.first().values.contains("dark"))
        assertTrue(CapabilityRegistry.find("set_haptic")!!.params.first().values.contains("off"))
    }
}
