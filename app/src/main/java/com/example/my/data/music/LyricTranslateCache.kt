package com.example.my.data.music

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest

/**
 * 歌词逐行翻译的磁盘缓存。
 *
 * 不设条数上限：用户明确要求翻译结果长期复用，重复打开同一首歌、
 * 同一句副歌都直接命中，不再反复请求网页翻译接口。
 * 只在内存里保留最近 400 句，磁盘永远只有一份，设置页可整体清理。
 */
object LyricTranslateCache {

    private const val CACHE_DIR = "lyric_translate_cache"
    private const val MEMORY_LIMIT = 400
    private const val DEFAULT_TARGET = LyricTranslateLanguages.ENGLISH

    /** 访问顺序的 LRU，避免长会话把整本歌词都留在内存里 */
    private val memory = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > MEMORY_LIMIT
    }

    private fun dir(root: File): File = File(root, CACHE_DIR)

    private fun memoryKey(source: String, target: String): String = "$target\u0000$source"

    /**
     * 兼容旧缓存：英文默认仍是 sha256(原文)，这样升级后之前缓存的译文还能直接命中；
     * 新增语种则把目标语言混进 key，避免同一句中文的英文/日文互相覆盖。
     */
    private fun fileFor(root: File, source: String, target: String): File {
        val key = if (target == DEFAULT_TARGET) source else "$target\u0000$source"
        return File(dir(root), sha256(key) + ".json")
    }

    fun get(root: File, source: String, target: String = DEFAULT_TARGET): String? {
        val key = memoryKey(source, target)
        synchronized(memory) { memory[key] }?.let { return it }
        val file = fileFor(root, source, target)
        if (!file.isFile) return null
        val translated = runCatching {
            val obj = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
            if (obj.get("source")?.asString != source) return@runCatching null
            val savedTarget = obj.get("target")?.asString
            if (savedTarget != null && savedTarget != target) return@runCatching null
            obj.get("translated")?.asString?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: return null
        synchronized(memory) { memory[key] = translated }
        return translated
    }

    fun put(
        root: File,
        source: String,
        translated: String,
        target: String = DEFAULT_TARGET
    ) {
        if (source.isBlank() || translated.isBlank()) return
        synchronized(memory) { memory[memoryKey(source, target)] = translated }
        runCatching {
            val d = dir(root)
            if (!d.isDirectory && !d.mkdirs()) return@runCatching
            val targetFile = fileFor(root, source, target)
            val obj = JsonObject().apply {
                addProperty("source", source)
                addProperty("target", target)
                addProperty("translated", translated)
                addProperty("at", System.currentTimeMillis())
            }
            // 同目录临时文件 + rename，进程被杀也不会留下半截 JSON
            val tmp = File.createTempFile("tr_", ".tmp", d)
            tmp.writeText(obj.toString(), Charsets.UTF_8)
            if (!tmp.renameTo(targetFile)) {
                targetFile.delete()
                if (!tmp.renameTo(targetFile)) tmp.delete()
            }
        }
    }

    fun count(root: File): Int {
        val files = dir(root).listFiles { f -> f.isFile && f.extension.equals("json", true) }
        return files?.size ?: 0
    }

    fun sizeBytes(root: File): Long {
        val files = dir(root).listFiles { f -> f.isFile && f.extension.equals("json", true) }
        return files?.sumOf { it.length() } ?: 0L
    }

    fun summary(root: File): String {
        val n = count(root)
        if (n <= 0) return "0 句"
        val bytes = sizeBytes(root)
        val size = when {
            bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
            bytes >= 1024L -> "${bytes / 1024L} KB"
            else -> "${bytes} B"
        }
        return "$n 句 · $size"
    }

    fun clear(root: File) {
        synchronized(memory) { memory.clear() }
        runCatching { dir(root).deleteRecursively() }
    }

    private fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
