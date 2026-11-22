package com.example.my.data.ai

import android.content.Context
import com.example.my.data.CommandIntent
import com.example.my.state.AppState
import org.json.JSONArray
import org.json.JSONObject

/**
 * 文档型约束：被标注的方法只能由能力网关（[AgentRuntime] / CapabilityRegistry）内部调用，
 * 不允许在 UI 层直接调用，从而绕过 check → execute → receipt 的统一流程。
 *
 * Kotlin 没有模块级强制访问控制，这里用 SOURCE 保留的注解 + KDoc 固化约定，配合代码评审；
 * 任何新的写操作都应登记成 Capability 走网关，而不是直接调被标注的方法。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class GateOnly


/**
 * 统一执行网关 + 结果回执。
 *
 * 以前动作一进 `AppState.applyCommand` 就石沉大海：模型不知道成没成，抽取失败就静默 return，
 * 用户问「刚才那步做了吗」AI 只能瞎猜。这里把每次调度变成一条带结果的回执：
 * 校验 → 模式过滤 → 确认分级 → 执行 → 记日志 → 回灌模型。
 *
 * P0 是渐进增强：旧 `<intent>` 链路里没登记的动作仍回退到 `applyCommand`，
 * 不砍任何已有功能；破坏性操作的二次确认 UI 留到 P1。
 */
object AgentRuntime {

    enum class Status {
        EXECUTED, NEEDS_CONFIRM, BLOCKED, BAD_ARGS, NOT_FOUND, FAILED, GATED
    }

    data class Call(val id: String, val args: JSONObject = JSONObject())

    data class Dispatch(
        val status: Status,
        val message: String,
        val capability: Capability? = null,
        val result: CapResult? = null
    ) {
        val ok: Boolean get() = status == Status.EXECUTED && result?.ok == true
    }

    private data class Entry(
        val ts: Long,
        val id: String,
        val summary: String,
        val ok: Boolean,
        val undo: (suspend () -> Boolean)? = null
    )

    private const val PREF = "ai_action_log"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_GAPS = "gaps"
    private const val MAX = 30

    private val lock = Any()
    private val entries = mutableListOf<Entry>()
    private val gaps = mutableListOf<String>()

    /** 把旧的 CommandIntent 映射到能力表；没登记的返回空 id，由调用方回退旧执行器 */
    fun callFromCommand(cmd: CommandIntent): Call {
        val args = JSONObject()
        val id = when (cmd.kind) {
            "open" -> { args.put("target", cmd.target); "open_screen" }
            "create_plan" -> "create_plan"
            "open_guide" -> { args.put("keyword", cmd.target); "open_guide" }
            "search_guide" -> { args.put("keyword", cmd.target); "search_guide" }
            "like_guide" -> { args.put("keyword", cmd.target); "toggle_like" }
            "fav_guide" -> { args.put("keyword", cmd.target); "toggle_fav" }
            "weather_city" -> { args.put("city", cmd.target); "set_weather_city" }
            "weather_query" -> { args.put("city", cmd.target); "query_weather_city" }
            "setting" -> {
                args.put("target", cmd.target)
                args.put("enabled", cmd.value != "false")
                "setting_switch"
            }
            "set_chat_mode" -> { args.put("mode", cmd.target); "set_chat_mode" }
            "language" -> { args.put("lang", cmd.target); "set_language" }
            "settings_page" -> { args.put("target", cmd.target); "open_settings_section" }
            "theme" -> { args.put("mode", cmd.target); "set_theme" }
            "haptic" -> { args.put("mode", cmd.target); "set_haptic" }
            "app_language" -> { args.put("lang", cmd.target); "set_app_language" }
            "edit_footprint" -> {
                args.put("keyword", cmd.target)
                args.put("note", cmd.value)
                "edit_footprint_note"
            }
            // P1：最后 4 类旧动作迁入能力表，confirmAction 不再需要回退 applyCommand
            "logout" -> "logout"
            "exit_app" -> "exit_app"
            "delete_trip" -> { args.put("keyword", cmd.target); "delete_trip" }
            "set_local_model" -> { args.put("model", cmd.target); "set_local_model" }
            else -> ""
        }
        return Call(id, args)
    }

    /**
     * 把协议里扁平的 Map<String,String> 还原成带类型的参数。
     * <next> / <plan> 用字符串承载所有值，BOOL/INT 能力直接收字符串会被 validate 拒掉。
     */
    fun coerceArgs(cap: Capability, raw: Map<String, String>): JSONObject = JSONObject().apply {
        raw.forEach { (k, v) ->
            val p = cap.params.firstOrNull { it.name == k }
            when (p?.type) {
                CapType.BOOL -> put(k, v.equals("true", true) || v == "1")
                CapType.INT -> put(k, v.toIntOrNull() ?: v)
                else -> put(k, v)
            }
        }
    }

    /** 外部（如「能力清单里没有这个操作」）记录一条待补能力缺口 */
    fun noteGap(name: String) {
        if (name.isNotBlank()) recordGap(name.trim().take(40))
    }

    /** 只做判定、不执行：可在动作进确认卡之前就把非法参数拦下来 */
    fun check(
        call: Call,
        mode: ChatMode,
        confirmed: Boolean = false,
        doubleConfirmed: Boolean = false
    ): Dispatch {
        val cap = CapabilityRegistry.find(call.id)
        if (cap == null) {
            recordGap(call.id.ifBlank { "unknown" })
            return Dispatch(Status.NOT_FOUND, "能力「${call.id}」尚未登记", null)
        }
        if (cap.gated) return Dispatch(Status.GATED, "能力「${cap.title}」暂未开放", cap)
        val bad = cap.validate(call.args)
        if (bad != null) return Dispatch(Status.BAD_ARGS, bad, cap)
        if (cap.network == CapNetwork.REQUIRES_NETWORK && mode == ChatMode.LOCAL)
            return Dispatch(
                Status.BLOCKED,
                "「${cap.title}」需要联网，当前是纯本地模式，已按隐私设置拦截",
                cap
            )
        if (cap.confirm == CapConfirm.ONCE && !confirmed)
            return Dispatch(Status.NEEDS_CONFIRM, "「${cap.title}」需要你确认后执行", cap)
        if (cap.confirm == CapConfirm.DOUBLE && (!confirmed || !doubleConfirmed))
            return Dispatch(
                Status.NEEDS_CONFIRM,
                "「${cap.title}」是高风险操作，需要二次确认",
                cap
            )
        return Dispatch(Status.EXECUTED, "", cap)
    }

    /** 执行一条能力，并留下可回灌给模型、可撤销的回执 */
    suspend fun execute(
        state: AppState,
        app: Context,
        call: Call,
        mode: ChatMode,
        confirmed: Boolean = false,
        doubleConfirmed: Boolean = false
    ): Dispatch {
        val pre = check(call, mode, confirmed, doubleConfirmed)
        if (pre.status != Status.EXECUTED) return pre
        val cap = pre.capability ?: return Dispatch(Status.NOT_FOUND, "能力未登记")
        // 这里必须 try/catch：执行器是 suspend 的，runCatching 的 lambda 不是 suspend 作用域
        val result = try {
            cap.execute(CapContext(state, app, call.args))
        } catch (e: Exception) {
            CapResult(false, "执行失败：${e.message?.take(80) ?: "未知错误"}")
        }
        val status = if (result.ok) Status.EXECUTED else Status.FAILED
        val dispatch = Dispatch(status, result.message, cap, result)
        record(
            Entry(
                ts = System.currentTimeMillis(),
                id = cap.id,
                summary = "${cap.title}：${result.message.take(80)}",
                ok = result.ok,
                undo = result.undo
            ),
            app
        )
        return dispatch
    }

    /** 把一次执行结果翻译成模型能复盘的文本，避免「假装已执行」 */
    fun receipt(d: Dispatch): String = when (d.status) {
        Status.EXECUTED -> "执行成功｜${d.capability?.title ?: d.message}｜${d.result?.data.orEmpty()}"
        Status.FAILED -> "执行失败｜${d.message}｜应说明原因或换一种做法，不要说已经完成"
        Status.NEEDS_CONFIRM -> "等待用户确认｜${d.capability?.title ?: ""}｜不要说已经执行"
        Status.BAD_ARGS -> "参数被拒绝｜${d.message}｜请向用户补全信息后重试"
        Status.BLOCKED -> "被模式拦截｜${d.message}｜向用户解释隐私设置"
        Status.NOT_FOUND -> "能力缺口｜${d.message}｜明确告诉用户当前做不到，别承诺"
        Status.GATED -> "能力未开放｜${d.message}｜明确告诉用户当前做不到"
    }

    /** 注入 system 的最近操作回执，形成「执行 → 复盘 → 下一步」闭环 */
    fun renderReceipts(): String? {
        val recent = synchronized(lock) { entries.takeLast(3) }
        if (recent.isEmpty()) return null
        val lines = recent.joinToString("\n") { "- ${if (it.ok) "成功" else "失败"}：${it.summary}" }
        return "【最近执行的 App 操作（真实回执）】\n$lines\n" +
            "回答「刚才那步做了吗」「继续」这类问题必须以上面为准；失败的操作不要说成已完成。"
    }

    /** 撤销最近一次可撤销的操作 */
    suspend fun undoLast(): String {
        val target = synchronized(lock) { entries.lastOrNull { it.undo != null } }
            ?: return "没有可撤销的操作"
        val ok = try { target.undo?.invoke() ?: false } catch (e: Exception) { false }
        return if (ok) "已撤销：${target.summary}" else "撤销失败：${target.summary}"
    }

    /** 用户请求落到没登记的能力时记下来，作为下一批要补的功能清单 */
    fun gaps(): List<String> = synchronized(lock) { gaps.toList() }

    private fun recordGap(name: String) {
        synchronized(lock) {
            if (name !in gaps) gaps.add(name)
            if (gaps.size > MAX) gaps.removeAt(0)
        }
    }

    private fun record(e: Entry, app: Context) {
        synchronized(lock) {
            entries.add(e)
            if (entries.size > MAX) entries.removeAt(0)
        }
        runCatching {
            val arr = JSONArray()
            synchronized(lock) { entries.toList() }.forEach {
                arr.put(
                    JSONObject().put("ts", it.ts).put("id", it.id)
                        .put("summary", it.summary).put("ok", it.ok)
                )
            }
            app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY_ENTRIES, arr.toString())
                .putString(KEY_GAPS, JSONArray(synchronized(lock) { gaps.toList() }).toString())
                .apply()
        }
    }

    /** 冷启动恢复摘要（undo 不持久化，重启后不可撤销） */
    fun restore(app: Context) {
        runCatching {
            val p = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val arr = JSONArray(p.getString(KEY_ENTRIES, "[]") ?: "[]")
            val restored = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Entry(o.optLong("ts"), o.optString("id"), o.optString("summary"), o.optBoolean("ok"))
            }
            val g = JSONArray(p.getString(KEY_GAPS, "[]") ?: "[]")
            val restoredGaps = (0 until g.length()).mapNotNull { g.optString(it) }
            synchronized(lock) {
                entries.clear(); entries.addAll(restored.takeLast(MAX))
                gaps.clear(); gaps.addAll(restoredGaps.takeLast(MAX))
            }
        }
    }
}
