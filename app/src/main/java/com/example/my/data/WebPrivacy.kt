package com.example.my.data

/**
 * 浏览器的隐私与「人机验证」识别。
 *
 * 隐私这块能做的（客户端能做的）：
 * - 换成通用 Chrome UA，不再带 App 专属后缀（自定义 UA 本身就是一枚指纹）；
 * - 拦截常见统计/广告/追踪域名，剪掉跨站画像；
 * - 只留第一方 Cookie，页面关掉后清 Cookie + WebStorage，不做跨会话追踪；
 * - 搜索页每次换目标也先清一次浏览痕迹；QQ 登录页只清缓存/历史，保留登录 Cookie；
 * - 请求带 DNT（Do Not Track），并对搜索取数显式禁用 HTTP 缓存。
 *
 * 说明：**IP 本身是网络层信息，网页服务器一定能看到**；要在客户端层面隐藏 IP，
 * 只能走代理/VPN，这不在 App 内能解决的范围内。
 */
object WebPrivacy {

    /** 通用移动版 Chrome UA：去掉 App 后缀，避免被当成可追踪的独立客户端 */
    const val STANDARD_UA =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    /** 常见统计 / 广告 / 追踪域名（含子域） */
    private val TRACKER_HOSTS = listOf(
        "doubleclick.net",
        "google-analytics.com",
        "googletagmanager.com",
        "googleadservices.com",
        "googlesyndication.com",
        "googleoptimize.com",
        "connect.facebook.net",
        "hotjar.com",
        "hotjar.io",
        "mixpanel.com",
        "segment.io",
        "segment.com",
        "heapanalytics.com",
        "fullstory.com",
        "amplitude.com",
        "umeng.com",
        "cnzz.com",
        "hm.baidu.com",
        "tongji.baidu.com",
        "growingio.com",
        "appsflyer.com",
        "adjust.com",
        "branch.io",
        "pangle.io",
        "pangolin-sdk-toutiao.com",
        "criteo.com",
        "taboola.com",
        "outbrain.com",
        "scorecardresearch.com",
        "quantserve.com",
        "adsrvr.org",
        "rubiconproject.com",
        "pubmatic.com"
    )

    /** 形如「人机验证 / 请稍候」的门禁页常见字样 */
    private val CHALLENGE_WORDS = listOf(
        "人机验证", "安全验证", "滑动验证", "访问验证", "请完成验证", "行为验证",
        "验证码", "点击验证", "验证你是人类", "请稍候",
        "just a moment", "checking your browser", "verify you are human",
        "are you a robot", "captcha", "unusual traffic", "attention required",
        "access denied", "too many requests"
    )

    /** 专门做验证的门禁域名 / 路径 */
    private val CHALLENGE_HOSTS = listOf(
        "challenges.cloudflare.com",
        "captcha",
        "recaptcha",
        "hcaptcha.com",
        "geetest.com",
        "gee-test.com"
    )

    /** 是不是第三方追踪域名 */
    fun isTracker(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = WebSafety.hostOf(url).lowercase()
        if (host.isBlank()) return false
        return TRACKER_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /**
     * 判断当前页面是不是「人机验证 / 风控门禁」页。
     * url 命门禁域名，或标题命中验证字样，都算。
     */
    fun looksLikeChallenge(url: String?, title: String?): Boolean {
        val host = WebSafety.hostOf(url.orEmpty()).lowercase()
        val path = url.orEmpty().lowercase()
        if (CHALLENGE_HOSTS.any { host == it || host.endsWith(".$it") }) return true
        if (path.contains("/captcha") || path.contains("challenge")) return true
        val t = title.orEmpty().lowercase()
        return CHALLENGE_WORDS.any { t.contains(it) }
    }

    /** 状态码层面的风控：403 / 429 / 503 常见于人机校验或频控 */
    fun isChallengeStatus(code: Int): Boolean = code == 403 || code == 429 || code == 503
}
