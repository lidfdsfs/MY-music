package com.example.my.ui.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 运行期语言状态。
 *
 * 为什么不用 Android 的 `values-en/strings.xml` + `LocaleManager`：
 * 1) 那套机制切换语言要 recreate Activity，闪一下白屏、正在滚动的列表全部归零；
 * 2) 项目里现有文案是硬编码在 Compose 里的，搬进资源文件等于把 900+ 调用点全改一遍。
 *
 * 这里用 Compose 状态承载 `code`：`tr()` 在组合过程中读它，切换时所有用到 `tr()` 的
 * Composable 自动重组 —— 界面就地刷新，不重启、不丢状态。
 *
 * 文案写法：`Text(tr("足迹"))`，英文从 [Dict] 查；字典没收录的自动回退中文，
 * 所以可以分批补全翻译，中途不会出现空串或 key 泄漏。
 */
object Lang {

    /** "zh" / "en" */
    var code by mutableStateOf("zh")
        private set

    val isEn: Boolean get() = code == "en"

    fun apply(language: String) {
        code = if (language == "en") "en" else "zh"
    }

    fun toggle() = apply(if (isEn) "zh" else "en")

    /** 给需要显式区分「中文」的 UI（设置页选项）用 */
    val displayName: String get() = if (isEn) "English" else "简体中文"
}

/**
 * 取词：英文模式返回 [en]（没传则查字典），中文模式原样返回。
 *
 * 单参形式 `tr("设置")` 走字典；需要精准控制时用双参 `tr("足迹", "Footprints")`。
 * 注意：这是普通函数不是 Composable，但它读的是 snapshot state，
 * 在 Composable 里调用同样能建立依赖、切换时触发重组。
 */
fun tr(zh: String, en: String? = null): String {
    if (!Lang.isEn) return cleanGlyph(zh)
    if (en != null) return cleanGlyph(en)
    val hit = Dict.en(zh)
    return cleanGlyph(if (hit == zh) Dict.en(cleanGlyph(zh)) else hit)
}

/**
 * UI 文案里一律不用 emoji 当图标：
 * 旧文案里有 `⭐ 官方攻略`、`⚠️ 地图服务未生效` 这类前缀，统一在这里抹掉，
 * 不需要逐个改几百处调用点；英文表和中文原文都会被清理。
 * 注意只清 So（Symbol/Other，含星号、天气、警示等彩色符号），
 * 不碰 ✓ ✗ ▴ ▾ 这类排版符号，菜单里用它们做的选中标记仍然有效。
 */
private val UI_GLYPH = Regex("[\\p{So}\\uFE0F\\u20E3]")

private fun cleanGlyph(s: String): String =
    s.replace(UI_GLYPH, "").replace(Regex("  +"), " ").trim()
