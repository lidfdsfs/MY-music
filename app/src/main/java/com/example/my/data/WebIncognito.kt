package com.example.my.data

import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import java.net.HttpURLConnection

/**
 * 搜索/浏览的「无痕」收口。
 *
 * 两条链路分开处理：
 * - HTTP 取数（歌名搜索、封面、歌词、榜单）：不建立 Cookie 会话，请求显式禁用缓存；
 * - 内置 WebView 浏览：页面关闭/换关键词时清历史、表单、缓存和 WebStorage。
 *
 * QQ 音乐登录必须保留登录 Cookie，因此调用方传 clearCookies = false，
 * 只清浏览痕迹，不把用户刚登录的账号一起清掉。
 */
object WebIncognito {

    /** 给 HttpURLConnection 加上不落缓存的请求头 */
    fun applyTo(conn: HttpURLConnection) {
        conn.useCaches = false
        conn.setRequestProperty("Cache-Control", "no-store, no-cache, must-revalidate")
        conn.setRequestProperty("Pragma", "no-cache")
        conn.setRequestProperty("DNT", "1")
        // Global Privacy Control：和 DNT 一起发，明确表达「不要出售/共享」，
        // 不改变现有请求语义，也不会被当成 App 专属指纹。
        conn.setRequestProperty("Sec-GPC", "1")
        // 搜索请求不走长连接：取完这一页就让 HTTP 栈关掉 socket，
        // 不在后台留着 keep-alive 连接等下一次复用。
        conn.setRequestProperty("Connection", "close")
    }

    /** 清理一个 WebView 的浏览痕迹；clearCookies=false 时保留登录态 */
    fun clearWebView(wv: WebView?, clearCookies: Boolean = true) {
        if (wv != null) {
            runCatching {
                wv.stopLoading()
                wv.clearHistory()
                wv.clearFormData()
                wv.clearCache(true)
                wv.removeAllViews()
            }
        }
        clearTraces(clearCookies)
    }

    /**
     * 真正关闭浏览页：停加载、清痕迹、终止网页渲染进程，再 destroy。
     *
     * destroy() 只释放 Java 侧引用，Android 8+ 的 WebView 渲染进程仍可能留在后台；
     * 这里在 API 29+ 主动 terminate()，搜索页退出后不再保留网页进程。
     */
    fun releaseWebView(wv: WebView?, clearCookies: Boolean = true) {
        if (wv != null) {
            runCatching {
                wv.stopLoading()
                wv.clearHistory()
                wv.clearFormData()
                wv.clearCache(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    wv.webViewRenderProcess?.terminate()
                }
                wv.removeAllViews()
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            }
        }
        clearTraces(clearCookies)
    }

    private fun clearTraces(clearCookies: Boolean) {
        if (clearCookies) {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
        }
        runCatching { WebStorage.getInstance().deleteAllData() }
    }
}
