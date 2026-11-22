package com.example.my.data

import java.net.URLDecoder

/** 资源探捞到的一个资源：音频或歌词 */
data class ProbedResource(
    val url: String,
    val name: String,
    val kind: Kind
) {
    enum class Kind { AUDIO, LYRICS }
}

/**
 * 内置浏览器的「资源探」——像桌面浏览器那样，把网页里的音频链接自动捞出来。
 *
 * 数据来源有三条，全部只读、不下载：
 * 1. WebView 的请求拦截（页面自己播放/加载音频时）；
 * 2. 页面加载完后的一次性脚本扫描（`a[href]` / `source[src]` 里的音频、歌词地址）；
 * 3. 用户点「下载」被拦下的那一下，把地址收进来。
 *
 * 识别只看扩展名和 Content-Type，别的一概不管；所有地址仍要过 [WebSafety] 闸门。
 */
object WebResourceProbe {

    /** 列表上限，防止刷页面把列表撑爆 */
    const val MAX_ITEMS = 30

    private val AUDIO_EXT = setOf(
        "mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "amr", "ape"
    )
    private val LYRICS_EXT = setOf("lrc")

    /** 判断一个地址是不是可播放音频 / 歌词；伪协议、伪装域名直接返回 null */
    fun kindOf(url: String, mime: String? = null): ProbedResource.Kind? {
        val safe = WebSafety.sanitize(url) ?: return null
        val type = mime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (type.startsWith("audio/")) return ProbedResource.Kind.AUDIO
        if (type == "application/x-lrc" || type == "text/lrc") return ProbedResource.Kind.LYRICS
        val lower = safe.lowercase()
        // 扩展名可能在路径里，也可能在查询参数里（?file=x.mp3 / ?url=...mp3）
        val path = lower.substringBefore('?').substringBefore('#')
        val exts = mutableListOf(path.substringAfterLast('.', ""))
        val query = lower.substringAfter('?', "").substringBefore('#')
        if (query.isNotBlank()) {
            query.split('&').forEach { pair ->
                val value = pair.substringAfter('=', pair)
                exts += value.substringAfterLast('.', "").trim()
            }
        }
        if (exts.any { it in AUDIO_EXT }) return ProbedResource.Kind.AUDIO
        if (exts.any { it in LYRICS_EXT }) return ProbedResource.Kind.LYRICS
        return null
    }

    /** 从地址里取一个能看的文件名，例如「花海-周杰伦.mp3」 */
    fun nameOf(url: String): String {
        val segment = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        if (segment.isBlank()) return WebSafety.hostOf(url)
        return try {
            URLDecoder.decode(segment, "UTF-8")
        } catch (_: Exception) {
            segment
        }
    }

    /**
     * 页面脚本扫出来的结果：WebView 回传的是「JSON 字符串里的 JSON 数组」，
     * 例如 "[\"https://a/b.mp3\"]"。这里手写解析，不依赖 org.json（纯 JVM 可单测）。
     */
    fun parseJsResult(raw: String?): List<String> {
        if (raw.isNullOrBlank() || raw == "null") return emptyList()
        var text = raw.trim()
        // 外层是 JS 字符串结果，先剥引号并反转义
        if (text.length >= 2 && text.first() == '"' && text.last() == '"') {
            text = unescapeJson(text.substring(1, text.length - 1))
        }
        val quoted = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
        return quoted.findAll(text)
            .map { unescapeJson(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun unescapeJson(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    '"', '\\', '/' -> { sb.append(n); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'u' -> {
                        val hex = s.substring(i + 2, minOf(i + 6, s.length))
                        val code = hex.toIntOrNull(16)
                        if (code != null && hex.length == 4) {
                            sb.append(code.toChar())
                            i += 6
                        } else {
                            sb.append(c)
                            i++
                        }
                    }
                    else -> { sb.append(c); i++ }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /**
     * 页面加载后执行的一次性扫描脚本：收集链接和媒体源里的音频 / 歌词地址。
     * 只读，不改页面，不注入任何 JS 桥。
     */
    fun scanScript(): String = """
        (function(){
          try{
            var r=[],es=document.querySelectorAll('a[href],source[src],audio[src],video[src]');
            for(var i=0;i<es.length;i++){
              var u=es[i].href||es[i].src||'';
              if(/\.(mp3|m4a|aac|flac|ogg|oga|opus|wav|wma|amr|ape|lrc)([?#]|$)/i.test(u)) r.push(u);
            }
            return JSON.stringify(r.slice(0,50));
          }catch(e){ return '[]'; }
        })()
    """.trimIndent()

    /** 往列表里加一条：去重、限量，返回是否真的加入 */
    fun add(target: MutableList<ProbedResource>, url: String, mime: String? = null): Boolean {
        val kind = kindOf(url, mime) ?: return false
        val safe = WebSafety.sanitize(url) ?: return false
        if (target.any { it.url == safe }) return false
        target.add(ProbedResource(safe, nameOf(safe), kind))
        while (target.size > MAX_ITEMS) target.removeAt(0)
        return true
    }
}
