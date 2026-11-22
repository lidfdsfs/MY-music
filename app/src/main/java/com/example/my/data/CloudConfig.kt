package com.example.my.data

import android.content.Context

/**
 * 云端能力总开关（运行时可切换，用户可在设置页打开/关闭）。
 *
 * 打开：App 连接后端，登录后把足迹、行程、设置、照片/视频上传到服务器；
 * 关闭：完全隔离服务器，所有网络出口在 [BackendApi.request] / [BackendApi.multipartUpload]
 *       处直接短路，数据只落在本机（Room + DataStore + files/）。
 *
 * 状态持久化在 SharedPreferences（cloud_sync_prefs），冷启动由 [init] 读回内存，
 * 因此开关在 UI、同步任务、网络出口三处看到的是同一个值。
 */
object CloudConfig {

    private const val PREFS = "cloud_sync_prefs"
    private const val KEY_ENABLED = "cloud_enabled"

    /**
     * 后端地址（唯一入口）。
     * 公网 HTTPS 部署：由平台转发到后端 7070，App 不再依赖局域网。
     * 局域网联调仍可通过下面的 LAN 注释地址临时替换。
     */
    const val SERVER_URL = "https://7070-agq447ltbeo743hfuzt3otltqy.instabox.tech"

    /**
     * 短视频解析服务：服务端完成抖音分享页/短链的抓取与清洗，
     * 直接返回无水印视频、封面、作者、时长、图集、实况和配乐字段。
     * App 不再自己抓 HTML，避免平台改版/风控导致解析失败。
     */
    const val VIDEO_RESOLVER_URL = "https://8080-agq447ltbeo743hfuzt3otltqy.instabox.tech/"

    /**
     * 微足迹独立服务地址（microfeed-backend，端口 7080）。
     *
     * 和主后端是两个服务：微足迹的公开流、互动、搜索、媒体都在这一台上。
     * 已部署为公网 HTTPS，与 [SERVER_URL] 同一套平台转发（仅端口不同），
     * 因此不需要在 network_security_config 里放行明文。
     * App 内「微足迹服务」面板仍可运行期覆盖（自建服务器时用）。
     */
    const val MICROFEED_URL = "https://7080-agq447ltbeo743hfuzt3otltqy.instabox.tech"

    /**
     * 旧版编译期默认值（局域网明文）。只用于**迁移**：
     * 早期装机时若把默认地址存进了 SharedPreferences，升级后它仍然是这个 IP，
     * 会在新默认 HTTPS 生效后被误当成「用户自定义」而一直连局域网。
     * [com.example.my.data.microfeed.MicroFeedApi.init] 见到它就直接清掉。
     */
    const val MICROFEED_LEGACY_URL = "http://192.168.0.142:7080"

    @Volatile
    private var cache = false

    @Volatile
    private var ready = false

    /** true = 连接服务器；false = 仅本地。UI 与网络出口都读这个 */
    val ENABLED: Boolean get() = cache

    /** 本地模式下是否真的会走网络（供 UI 判断要不要显示"同步中"之类） */
    val localOnly: Boolean get() = !cache

    /**
     * 本地模式下的"作者 id"：自己发布的攻略用这个 id 标识。
     * 故意取 -99：演示数据（MockData）的 authorId 用的是模型默认值 -1，不能撞。
     */
    const val LOCAL_AUTHOR_ID = -99

    /** 进程启动时调用一次，把开关从磁盘读回内存（必须在任何网络出口之前） */
    fun init(context: Context) {
        if (ready) return
        // 新版本要求「必须登录才能进入」，默认即开启云端；老用户曾手动关闭的，启动时强制打开
        cache = true
        prefs(context).edit().putBoolean(KEY_ENABLED, true).apply()
        ready = true
    }

    fun isEnabled(context: Context): Boolean {
        init(context)
        return cache
    }

    /** 切换开关：立刻同时更新内存与磁盘，避免本次会话与下次冷启动不一致 */
    fun setEnabled(context: Context, on: Boolean) {
        cache = on
        ready = true
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
