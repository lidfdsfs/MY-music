package com.example.my.data

import java.net.IDN
import java.net.URI

/**
 * 内置浏览器（WebSearchScreen）的链接安全闸门。
 *
 * 只放行 http/https 的正常网页地址，其余一律拦掉：
 * - javascript: / data: / file: / content: / intent: / market: / alipays: 这类伪协议，
 *   一个 intent:// 就能把外部 App 甚至安装器拉起来，是「点开链接被塞木马」的主要入口；
 * - user:pass@host 形式：浏览器会把 @ 前面的当账号信息、真正跳去 @ 后面的域名，
 *   常被用来伪装成可信站点；
 * - punycode 同形字（xn--…）与控制字符（\r\n 注入）；
 * - 超长地址。
 */
object WebSafety {

    /** 地址长度上限：正常搜索结果页远小于这个值，超长多半是塞了 payload */
    const val MAX_URL_LENGTH = 2048

    /** 可执行/安装类文件后缀，命中就不允许下载 */
    private val EXEC_EXTENSIONS = listOf(
        ".apk", ".aab", ".dex", ".jar", ".so", ".exe", ".msi", ".bat", ".cmd", ".sh", ".scr", ".dmg"
    )

    /** 返回可安全加载的地址；不合法返回 null（调用方直接拦下，不要兜底去加载） */
    fun sanitize(raw: String?): String? {
        val url = raw?.trim().orEmpty()
        if (url.isEmpty() || url.length > MAX_URL_LENGTH) return null
        // 空白/控制字符：既是注入手段，也会让日志和判重失真
        if (url.any { it.isWhitespace() || it.isISOControl() }) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.rawUserInfo != null) return null
        val host = uri.host?.lowercase().orEmpty()
        if (host.isEmpty()) return null
        // punycode 同形字：xn-- 开头直接拒（正常中文站点用不上它）
        if (host.startsWith("xn--")) return null
        if (host.any { it.code > 127 }) {
            val ascii = runCatching { IDN.toASCII(host) }.getOrNull() ?: return null
            if (ascii.isBlank() || ascii.startsWith("xn--")) return null
        }
        // App 网络策略已关闭明文流量：http 链接统一升级为同址 https，
        // 避免 WebView 直接请求 http 被 CLEARTEXT_NOT_PERMITTED 拦在错误页。
        return if (scheme == "http") "https://" + url.substringAfter("://") else url
    }

    /** 脚本放行策略：只有 HTTPS 页面才允许执行脚本，明文 HTTP 一律当静态页看，挡住中途注入 */
    fun allowJavaScript(url: String?): Boolean {
        val scheme = runCatching { URI(url.orEmpty()).scheme?.lowercase() }.getOrNull()
        return scheme == "https"
    }

    /** 是否是可执行/安装包下载（下载即拦，不让 WebView 把安装器落盘） */
    fun isExecutableDownload(url: String?, mime: String?): Boolean {
        val lower = url.orEmpty().lowercase()
        if (mime.orEmpty().lowercase().contains("android.package-archive")) return true
        return EXEC_EXTENSIONS.any { lower.substringBefore('?').endsWith(it) }
    }

    fun hostOf(url: String?): String = runCatching {
        URI(url.orEmpty()).host.orEmpty().removePrefix("www.")
    }.getOrDefault("")
}
