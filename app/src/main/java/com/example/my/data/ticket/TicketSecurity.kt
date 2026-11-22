package com.example.my.data.ticket

import java.net.URI

/**
 * 票务接口的输入/输出清洗。
 *
 * 第三方 MCP 返回的是「文本」，不是可信代码：
 * - 先整段删掉 script/style，再删 HTML 标签，避免把标签或脚本内容原样画进 UI；
 * - 去掉控制字符、零宽字符和双向文本控制符，防止视觉伪装；
 * - 长度截断，避免超长字符串撑爆卡片或布局；
 * - 价格只接受有限范围内的数字，防止 `NaN` / 超大数 / 非数字进入金额展示。
 */
object TicketSecurity {

    private val SCRIPT_BLOCK = Regex("<(script|style)[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
    private val HTML_TAG = Regex("<[^>]*>")
    private val SPACES = Regex("[\\s\\u00A0]+")
    private val PRICE = Regex("^\\d{1,7}(\\.\\d{1,2})?$")

    private val ZERO_WIDTH = charArrayOf(
        '\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF'
    )
    private val BIDI_CONTROL = charArrayOf(
        '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
        '\u2066', '\u2067', '\u2068', '\u2069'
    )

    const val MAX_TEXT = 96

    fun sanitize(raw: String?, max: Int = MAX_TEXT): String {
        if (raw.isNullOrEmpty()) return ""
        var s = SCRIPT_BLOCK.replace(raw, " ")
        s = HTML_TAG.replace(s, " ")
        val cleaned = StringBuilder(s.length)
        for (ch in s) {
            if (ch in ZERO_WIDTH || ch in BIDI_CONTROL) continue
            if (ch.isISOControl()) {
                cleaned.append(' ')
                continue
            }
            cleaned.append(ch)
        }
        s = SPACES.replace(cleaned.toString(), " ").trim()
        return if (s.length > max) s.take(max).trimEnd() else s
    }

    /** 车次/航班号/三字码只保留字母数字，统一大写，限制长度 */
    fun code(raw: String?, max: Int = 8): String =
        raw.orEmpty().filter { it.isLetterOrDigit() }.uppercase().take(max)

    /** 时间只接受 HH:mm；其他一律丢弃，避免把奇怪文本当时刻显示 */
    fun time(raw: String?): String {
        val t = raw.orEmpty().trim()
        return if (Regex("^\\d{2}:\\d{2}$").matches(t)) t else ""
    }

    fun parsePrice(raw: String?): Double? {
        val t = raw.orEmpty().trim().removePrefix("¥").removePrefix("￥").replace(",", "")
        if (!PRICE.matches(t)) return null
        val v = t.toDoubleOrNull() ?: return null
        return if (v in 0.0..200_000.0) v else null
    }

    /**
     * 只允许固定 HTTPS 主机、443 端口、无 userinfo 的地址。
     * 票务客户端只用它校验写死的端点；任何上游返回的链接都不会进入 App。
     */
    fun isAllowedHttps(url: String?, allowedHosts: Set<String>): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (!uri.userInfo.isNullOrBlank()) return false
        if (uri.port != -1 && uri.port != 443) return false
        val host = uri.host?.lowercase() ?: return false
        return allowedHosts.any { host == it.lowercase() }
    }
}
