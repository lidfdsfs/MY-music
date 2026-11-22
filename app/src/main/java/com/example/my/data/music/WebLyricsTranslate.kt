package com.example.my.data.music

import android.content.Context
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 网页翻译引擎（不占本地模型内存）。
 *
 * 歌词翻译只用公开网页翻译接口，优先 MyMemory，失败再退 Google
 * 网页翻译。逐行翻译、逐行校验，保证时间轴不会错位。
 * 每句译文按“目标语言”落盘缓存，重复打开同一首歌不再联网；设置页可整体清理。
 */
object WebLyricsTranslate {

    private const val MAX_LINES = 160
    // MyMemory 免费接口限制约 5 次/秒，4 路并发留出余量
    private const val BATCH = 4
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /**
     * 把每行歌词翻译成 [target] 指定的语言。
     *
     * 返回列表长度必须与输入一致；任意一行失败就整体返回 null，
     * 由界面提示重试，绝不把半首译文贴到时间轴上。
     */
    suspend fun translate(
        context: Context,
        lines: List<String>,
        target: String = LyricTranslateLanguages.ENGLISH
    ): List<String>? {
        if (lines.isEmpty() || lines.size > MAX_LINES) return null
        val source = lines.map { it.trim() }
        if (source.any { it.isBlank() }) return null
        val targetLang = LyricTranslateLanguages.byCode(target)
        val sourceLang = detectSource(source)
        val root = context.applicationContext.filesDir
        val output = arrayOfNulls<String>(source.size)
        for (chunk in source.indices.chunked(BATCH)) {
            val translated = coroutineScope {
                chunk.map { index ->
                    async(Dispatchers.IO) {
                        translateLine(root, source[index], sourceLang, targetLang)
                    }
                }.awaitAll()
            }
            if (translated.any { it.isNullOrBlank() }) return null
            chunk.forEachIndexed { offset, index ->
                output[index] = translated[offset]
            }
        }
        return output.map { it ?: return null }
    }

    fun cacheSummary(context: Context): String =
        LyricTranslateCache.summary(context.applicationContext.filesDir)

    fun clearCache(context: Context) =
        LyricTranslateCache.clear(context.applicationContext.filesDir)

    private fun translateLine(
        root: File,
        text: String,
        sourceLang: String,
        targetLang: LyricTargetLanguage
    ): String? {
        // 原文和目标是同一种语言时不用白跑一次网络
        if (sourceLang.substringBefore("-").equals(targetLang.code, ignoreCase = true)) {
            return text
        }
        LyricTranslateCache.get(root, text, targetLang.code)?.let { return it }
        val translated = myMemory(text, sourceLang, targetLang.myMemory)
            ?: google(text, sourceLang, targetLang.google)
        if (!translated.isNullOrBlank()) {
            LyricTranslateCache.put(root, text, translated, targetLang.code)
        }
        return translated
    }

    /** MyMemory 免费网页翻译：国内可直连，返回 JSON */
    private fun myMemory(text: String, source: String, target: String): String? = runCatching {
        val pair = "$source|$target"
        val body = httpGet(
            "https://api.mymemory.translated.net/get?q=" +
                URLEncoder.encode(text, "UTF-8") + "&langpair=" +
                URLEncoder.encode(pair, "UTF-8")
        ) ?: return null
        val root = JsonParser.parseString(body).asJsonObject
        if (root.get("responseStatus")?.asInt != 200) return null
        val translated = root.getAsJsonObject("responseData")
            ?.get("translatedText")
            ?.asString
            ?.trim()
            .orEmpty()
        translated.takeIf {
            it.isNotBlank() &&
                !it.contains("MYMEMORY WARNING", ignoreCase = true) &&
                !it.contains("QUERY LENGTH LIMIT", ignoreCase = true) &&
                !it.contains("PLEASE SELECT", ignoreCase = true)
        }?.replace("&quot;", "\"")
            ?.replace("&#39;", "'")
            ?.replace("&amp;", "&")
    }.getOrNull()

    /** Google 网页翻译的公开接口：按句拼接所有片段 */
    private fun google(text: String, source: String, target: String): String? = runCatching {
        val sourceCode = source.substringBefore("-").lowercase()
        val body = httpGet(
            "https://translate.googleapis.com/translate_a/single?client=gtx" +
                "&sl=" + URLEncoder.encode(sourceCode, "UTF-8") +
                "&tl=" + URLEncoder.encode(target, "UTF-8") +
                "&dt=t&q=" + URLEncoder.encode(text, "UTF-8")
        ) ?: return null
        val root = JsonParser.parseString(body).asJsonArray
        val pieces = root.firstOrNull()?.asJsonArray?.mapNotNull { segment ->
            segment.asJsonArray.firstOrNull()?.asString
        }.orEmpty()
        pieces.joinToString("").trim().takeIf { it.isNotBlank() }
    }.getOrNull()

    /** 轻量源语言判断：中文/日文/韩文/俄文优先，其余按英文交给 Google 的 sl。 */
    private fun detectSource(lines: List<String>): String {
        val sample = lines.joinToString(" ").take(1_000)
        return when {
            sample.any { it in '\u3040'..'\u30FF' } -> "ja-JP"
            sample.any { it in '\uAC00'..'\uD7AF' } -> "ko-KR"
            sample.any { it in '\u4E00'..'\u9FFF' } -> "zh-CN"
            sample.any { it in '\u0400'..'\u04FF' } -> "ru-RU"
            else -> "en-US"
        }
    }

    private fun httpGet(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json,text/plain,*/*")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                // 每句译完立即断开，不把翻译接口的长连接留在后台
                setRequestProperty("Connection", "close")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
