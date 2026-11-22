package com.example.my.data

/**
 * 参考来源收敛（纯计算，不联网，可单测）。
 *
 * 搜索引擎经常把同一篇文章的不同入口、同一站点的十个栏目一起返回：
 * 直接摊到「参考来源」里，用户看到的是十条知乎/百家号，真正有用的官方页面反而被挤掉。
 * 这里借鉴 Vane / Morphic 一类 AI 搜索前端的做法，在展示前做三件事：
 *  1. 只保留能安全打开的 http/https 地址（复用 [WebSafety] 的闸门）；
 *  2. 按「域名 + 路径 + 有效参数」判重，`utm_*`、`spm`、`share_*` 这类跟踪参数不参与判重；
 *  3. 同一站点最多保留 [perHost] 条，避免单一站点刷屏。
 *
 * 顺序保持原样：调用方按 [max] 截断后，展示编号仍然从 1 连续往下排。
 */
object WebSourcePicker {

    private val TRACKING_KEYS = setOf(
        "from", "spm", "scm", "share", "shareid", "share_token", "sharesource",
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "yclid", "igshid", "ref", "referer", "referrer"
    )

    fun refine(raw: List<WebHit>, max: Int = 10, perHost: Int = 2): List<WebHit> {
        if (raw.isEmpty() || max <= 0 || perHost <= 0) return emptyList()
        val seen = mutableSetOf<String>()
        val hostCount = mutableMapOf<String, Int>()
        val out = ArrayList<WebHit>(minOf(raw.size, max))
        for (h in raw) {
            if (out.size >= max) break
            val url = WebSafety.sanitize(h.url) ?: continue
            // 安装包/可执行文件不是"参考网页"，点开只会触发下载，直接不进来源列表
            if (WebSafety.isExecutableDownload(url, null)) continue
            val key = canonical(url)
            if (key.isBlank() || !seen.add(key)) continue
            val host = WebSafety.hostOf(url)
            if (host.isNotBlank() && hostCount.getOrDefault(host, 0) >= perHost) continue
            if (host.isNotBlank()) hostCount[host] = hostCount.getOrDefault(host, 0) + 1
            out += h.copy(url = url)
        }
        return out
    }

    /** 判重用的规范形式：去 fragment、统一小写、剔除跟踪参数，保留其余查询参数 */
    private fun canonical(url: String): String {
        val noFragment = url.substringBefore('#')
        val base = noFragment.substringBefore('?').trimEnd('/').lowercase()
        val query = noFragment.substringAfter('?', "")
        if (query.isBlank()) return base
        val kept = query.split('&')
            .filter { it.isNotBlank() }
            .filterNot { pair ->
                val name = pair.substringBefore('=').lowercase()
                name in TRACKING_KEYS || name.startsWith("utm_")
            }
            .sorted()
        return if (kept.isEmpty()) base else base + "?" + kept.joinToString("&")
    }
}
