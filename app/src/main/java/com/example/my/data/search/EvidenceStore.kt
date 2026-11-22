package com.example.my.data.search

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 临时证据库落盘。
 *
 * 清洗后的证据不在本轮直接塞给模型，而是先写进 cacheDir 下的 trip_evidence 目录。
 * 第二段生成时按槽位从文件里取，模型只看到命中的证据。
 *
 * 只在 cacheDir 写、超过 24 小时自动清理，不进入用户可见文件，也不会长期占空间。
 */
object EvidenceStore {

    private const val DIR = "trip_evidence"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    fun save(
        context: Context,
        key: String,
        query: String,
        deck: SeaDeck?
    ): List<EvidenceRecord> =
        save(context, key, EvidenceIndex.recordsFrom(deck, query))

    fun save(
        context: Context,
        key: String,
        records: List<EvidenceRecord>
    ): List<EvidenceRecord> {
        if (records.isEmpty()) return emptyList()
        val dir = File(context.cacheDir, DIR)
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            prune(dir)
            val file = File(dir, "${fileName(key)}.jsonl")
            file.writeText(
                records.joinToString("\n") { toJson(it).toString() },
                Charsets.UTF_8
            )
        }
        return records
    }

    fun load(context: Context, key: String): List<EvidenceRecord> =
        runCatching {
            val file = File(File(context.cacheDir, DIR), "${fileName(key)}.jsonl")
            if (!file.exists()) {
                emptyList()
            } else {
                file.readLines(Charsets.UTF_8).mapNotNull { line ->
                    runCatching { fromJson(JSONObject(line)) }.getOrNull()
                }
            }
        }.getOrDefault(emptyList())

    private fun fileName(key: String): String =
        "ev_" + Integer.toHexString(key.hashCode()) + "_" + key.length

    private fun prune(dir: File) {
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { f ->
            if (now - f.lastModified() > MAX_AGE_MS) runCatching { f.delete() }
        }
    }

    private fun toJson(r: EvidenceRecord): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("category", r.category)
        put("fact", r.fact)
        put("status", r.status)
        put("title", r.title)
        put("url", r.url)
        put("site", r.site)
        put("tier", r.tier)
        put("published", r.published)
        put("query", r.query)
        put("counter", r.counter)
        put("sourceIds", JSONArray(r.sourceIds))
    }

    private fun fromJson(o: JSONObject): EvidenceRecord {
        val ids = o.optJSONArray("sourceIds") ?: JSONArray()
        val sourceIds = (0 until ids.length()).mapNotNull {
            runCatching { ids.getInt(it) }.getOrNull()
        }
        return EvidenceRecord(
            id = o.optString("id"),
            category = o.optString("category"),
            fact = o.optString("fact"),
            status = o.optString("status"),
            title = o.optString("title"),
            url = o.optString("url"),
            site = o.optString("site"),
            tier = o.optInt("tier", 3),
            published = o.optString("published"),
            sourceIds = sourceIds,
            counter = o.optString("counter"),
            query = o.optString("query")
        )
    }
}
