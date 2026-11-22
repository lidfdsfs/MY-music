package com.example.my.data.search

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 云端模型的 `lookup_evidence` 工具：只查本轮已落盘的临时证据库。
 *
 * 第一段检索/清洗把证据写进 EvidenceStore，第二段模型可以边思考边按类别/关键词
 * 回查，而不是一次性把所有事实卡塞进上下文。工具本身不联网，也不扩大查询范围。
 */
object EvidenceLookupTool {

    const val NAME = "lookup_evidence"
    private const val MAX_CHARS = 1_600

    fun json(): JSONObject = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", NAME)
            put(
                "description",
                "从本轮临时证据库中按类别和关键词查询已经清洗过的事实。" +
                    "需要门票、开放时间、交通车程、路线玩法、美食、住宿、避坑、天气等具体信息时，" +
                    "先调用它；不要凭空编数字。不要再查已经拿到的同一类别。"
            )
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("category", JSONObject().apply {
                        put("type", "string")
                        put("description", "证据类别；不确定时用“全部证据”")
                        put("enum", JSONArray(listOf(
                            "门票与预约",
                            "开放时间",
                            "交通与车程",
                            "景点与路线",
                            "美食",
                            "住宿",
                            "避坑",
                            "天气",
                            "其他背景",
                            "全部证据"
                        )))
                    })
                    put("keyword", JSONObject().apply {
                        put("type", "string")
                        put("description", "地名、景点名或要核对的短关键词，例如“洪安古镇 门票”")
                    })
                })
                put("required", JSONArray(listOf("category")))
            })
        })
    }

    /**
     * 执行一次查询。没有证据/没有命中时返回明确说明，让模型知道可以换关键词或直接回答，
     * 而不是得到一段空字符串后继续编。
     */
    fun run(
        context: Context,
        key: String?,
        category: String,
        keyword: String
    ): String {
        if (key.isNullOrBlank()) {
            return "【lookup_evidence】本轮没有可用的临时证据库，请基于已有上下文回答，不要编具体数字。"
        }
        val records = EvidenceStore.load(context, key)
        if (records.isEmpty()) {
            return "【lookup_evidence】临时证据库为空（本轮清洗没有留下可用事实），" +
                "请基于已有来源摘要回答，并明确说明缺失。"
        }
        val hits = EvidenceIndex.lookup(records, category, keyword)
        if (hits.isEmpty()) {
            return "【lookup_evidence】类别「${category.ifBlank { "全部证据" }}」" +
                "、关键词「${keyword.ifBlank { "无" }}」没有命中证据。" +
                "可以换一个类别/关键词再查；如果仍没有，就不要编具体数字。"
        }
        val head = "【lookup_evidence 回填】category=${category.ifBlank { "全部证据" }}" +
            if (keyword.isBlank()) "" else "，keyword=$keyword"
        return buildString {
            appendLine(head)
            append(EvidenceIndex.render(hits, records.size, MAX_CHARS))
        }.trim()
    }
}
