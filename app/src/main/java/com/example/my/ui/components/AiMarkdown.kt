package com.example.my.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * 轻量 Markdown 渲染（零依赖）：把 AI 回复里的常见语法转成 AnnotatedString。
 *
 * 支持：# 标题、- / * 无序列表、1. 有序列表、**加粗**、`行内代码`、[链接](url)、> 引用、--- 分隔线。
 * 不支持表格、多级嵌套、图片——AI 回复用这些已经足够。
 */
fun markdownToAnnotated(text: String): AnnotatedString {
    // 兜底：所有 AI 回复渲染前统一剥掉控制标签，防止任何 UI 入口漏过滤
    val cleaned = com.example.my.ui.screens.stripControlMarkers(text)
    return buildAnnotatedString {
    val lines = cleaned.split('\n')
    lines.forEachIndexed { index, raw ->
        if (index > 0) append('\n')
        val line = raw.trimEnd()
        val trimmed = line.trimStart()
        val heading = Regex("^(#{1,4})\\s*(.*)$").find(trimmed)
        when {
            heading != null -> {
                val level = heading.groupValues[1].length
                append("▍")
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (level <= 2) 16.sp else 14.5.sp
                    )
                ) { appendInline(heading.groupValues[2]) }
            }
            trimmed == "---" || trimmed == "***" -> append("────────")
            trimmed.startsWith("> ") -> {
                withStyle(SpanStyle(color = Color(0xFF8A7F76))) {
                    append("｜")
                    appendInline(trimmed.removePrefix("> "))
                }
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                append("  •  ")
                appendInline(trimmed.substring(2))
            }
            Regex("^\\d+[.)]\\s").containsMatchIn(trimmed) -> appendInline(trimmed)
            else -> appendInline(line)
        }
    }
    }
}

private fun AnnotatedString.Builder.appendInline(src: String) {
    var i = 0
    while (i < src.length) {
        when {
            src.startsWith("**", i) -> {
                val end = src.indexOf("**", i + 2)
                if (end < 0) {
                    append(src.substring(i)); return
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInline(src.substring(i + 2, end))
                }
                i = end + 2
            }
            src[i] == '`' -> {
                val end = src.indexOf('`', i + 1)
                if (end < 0) {
                    append(src.substring(i)); return
                }
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0x14000000),
                        fontSize = 13.sp
                    )
                ) { append(src.substring(i + 1, end)) }
                i = end + 1
            }
            src.startsWith("[", i) -> {
                val close = src.indexOf(']', i + 1)
                val open = if (close >= 0) src.indexOf('(', close + 1) else -1
                val end = if (open == close + 1) src.indexOf(')', open + 1) else -1
                if (close < 0 || open != close + 1 || end < 0) {
                    append(src[i]); i++
                } else {
                    withStyle(SpanStyle(color = Color(0xFFC46A4E), fontWeight = FontWeight.Medium)) {
                        append(src.substring(i + 1, close))
                    }
                    i = end + 1
                }
            }
            src.startsWith("![", i) -> {
                // 图片语法退化为 alt 文本
                val close = src.indexOf(']', i + 2)
                val open = if (close >= 0) src.indexOf('(', close + 1) else -1
                val end = if (open == close + 1) src.indexOf(')', open + 1) else -1
                if (close < 0 || end < 0) {
                    append(src[i]); i++
                } else {
                    append(src.substring(i + 2, close))
                    i = end + 1
                }
            }
            else -> {
                append(src[i])
                i++
            }
        }
    }
}
