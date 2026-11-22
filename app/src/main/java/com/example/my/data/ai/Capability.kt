package com.example.my.data.ai

import android.content.Context
import com.example.my.state.AppState
import org.json.JSONArray
import org.json.JSONObject

/**
 * App 级 AI 能力总线 · 数据契约。
 *
 * 以前 AI 能做什么，散落在三处：`AiApi.systemPrompt` 里的 <intent> 协议字符串、
 * `AiAssistantScreen.executeIntent()` 的 when 分支、`AppState.applyCommand()` 的 when 分支。
 * 加一个功能要改三处，而且「AI 到底能调度多少功能」根本无法度量。
 *
 * 这里把每个功能抽象成一条 [Capability]：自带参数 schema、副作用级别、网络策略、
 * 执行器和对应回执。一张表同时供模型工具定义、执行分发、确认分级、覆盖率审计使用。
 */

/** 副作用级别：决定要不要用户确认、能不能自动执行 */
enum class CapRisk {
    /** 纯读，不改任何状态，也不跳页面 */
    READ,
    /** 会改数据或跳页面，需要一次确认 */
    WRITE,
    /** 删除 / 退出 / 隐私开关，需要二次确认且必须可撤销 */
    DESTRUCTIVE
}

/** 网络依赖：纯本地模式下由网关统一短路，不在每个 UI 里判断 */
enum class CapNetwork {
    /** 只读本地内存 / Room / DataStore */
    LOCAL,
    /** 本地能给出降级结果，联网更好 */
    OPTIONAL,
    /** 必须联网，纯本地模式直接拒绝 */
    REQUIRES_NETWORK
}

/** 确认档位，由 [Capability.risk] 推导，不允许能力自己声明「免确认」 */
enum class CapConfirm { NONE, ONCE, DOUBLE }

/** 参数类型只保留三种，避免模型吐出嵌套结构导致解析地狱 */
enum class CapType(val json: String) { STRING("string"), INT("integer"), BOOL("boolean") }

data class CapParam(
    val name: String,
    val type: CapType = CapType.STRING,
    val desc: String,
    val required: Boolean = false,
    /** 枚举白名单：模型给的值不在里面直接拒绝，杜绝随便一个 target 就跳错页面 */
    val values: List<String> = emptyList(),
    /** 字符串长度上限，同时防提示注入塞长文本 */
    val maxLen: Int = 120
)

/**
 * 统一执行回执。
 * [message] 给用户看，[data] 给模型复盘，[changedIds] 供撤销与幂等去重。
 */
data class CapResult(
    val ok: Boolean,
    val message: String,
    val data: String = "",
    val changedIds: List<String> = emptyList(),
    /** 仅内存有效：进程重启后不持久化 lambda */
    val undo: (suspend () -> Boolean)? = null
)

/** 执行器拿到的三样东西：全局状态、Android Context、模型给的参数 */
data class CapContext(val state: AppState, val app: Context, val args: JSONObject) {
    fun str(name: String, fallback: String = ""): String {
        val v = runCatching { args.optString(name) }.getOrNull()
        return v?.takeIf { it.isNotBlank() && it != "null" } ?: fallback
    }

    fun bool(name: String, fallback: Boolean = false): Boolean =
        if (args.has(name)) args.optBoolean(name, fallback) else fallback

    fun int(name: String, fallback: Int = 0): Int = args.optInt(name, fallback)
}

typealias CapExecutor = suspend CapContext.() -> CapResult

data class Capability(
    val id: String,
    val title: String,
    val desc: String,
    val group: String,
    val risk: CapRisk,
    val network: CapNetwork = CapNetwork.LOCAL,
    val params: List<CapParam> = emptyList(),
    /** 背后调用的 AppState 方法名，用于覆盖率审计（差集 = 还没接进 AI 的功能） */
    val backedBy: Set<String> = emptySet(),
    /** 已经登记但暂不开放的能力（例如破坏性操作要等二次确认 UI 做完） */
    val gated: Boolean = false,
    /**
     * 可逆的低风险写操作（如点赞/收藏这类 toggle，再执行一次即还原）。
     * 网关对这类能力免确认、直接执行，执行后用轻提示告知，用户可随时再点一次撤销。
     * 只有 READ/WRITE 且不联网改数据的能力允许标记为可逆，DESTRUCTIVE 不得可逆。
     */
    val reversible: Boolean = false,
    val execute: CapExecutor
) {
    val confirm: CapConfirm
        get() = when {
            // 可逆操作免确认：它本身就是自己的撤销方式，不破坏数据
            reversible && risk != CapRisk.DESTRUCTIVE -> CapConfirm.NONE
            risk == CapRisk.READ -> CapConfirm.NONE
            risk == CapRisk.WRITE -> CapConfirm.ONCE
            else -> CapConfirm.DOUBLE
        }

    /**
     * 模型给的参数一律不可信。这里做类型、枚举、长度和控制标记校验，
     * 是提示注入进 App 的第一道闸：`<intent>` / `<action>` 不能混进业务参数。
     */
    fun validate(args: JSONObject): String? {
        for (p in params) {
            val present = args.has(p.name) && !args.isNull(p.name)
            if (p.required && !present) return "缺少必填参数「${p.name}」"
            if (!present) continue
            val raw = args.opt(p.name) ?: continue
            when (p.type) {
                CapType.INT ->
                    if (raw !is Number && raw.toString().toIntOrNull() == null)
                        return "参数「${p.name}」必须是整数"

                CapType.BOOL ->
                    if (raw !is Boolean && raw.toString() !in setOf("true", "false"))
                        return "参数「${p.name}」必须是 true 或 false"

                CapType.STRING -> {
                    val s = raw.toString()
                    if (s.length > p.maxLen) return "参数「${p.name}」过长（上限 ${p.maxLen} 字）"
                    if (s.contains("<intent", true) || s.contains("<action", true) || s.contains("\u0001"))
                        return "参数「${p.name}」含控制标记，已拒绝"
                    if (p.values.isNotEmpty() && s !in p.values)
                        return "参数「${p.name}」只能是：${p.values.joinToString(" / ")}"
                }
            }
        }
        return null
    }

    /** OpenAI Function Calling 格式；P1 接预检时直接拼进 tools 即可 */
    fun schema(): JSONObject = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", id)
            put("description", desc)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    params.forEach { p ->
                        put(p.name, JSONObject().apply {
                            put("type", p.type.json)
                            put("description", p.desc)
                            if (p.values.isNotEmpty()) put("enum", JSONArray(p.values))
                        })
                    }
                })
                val req = params.filter { it.required }.map { it.name }
                if (req.isNotEmpty()) put("required", JSONArray(req))
            })
        })
    }
}
