package com.example.my.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import com.example.my.data.AiApi
import com.example.my.data.AiEndpoint
import com.example.my.data.AiInsight
import com.example.my.data.AiPresets
import com.example.my.data.ai.LocalLlm
import com.example.my.data.ai.LocalLlmState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用级 AI 任务持有器。
 *
 * 页面里的 rememberCoroutineScope 会在 Composable 离开组合（用户返回/切 Tab）时被取消，
 * 正在跑的 HTTP 流也跟着断。把任务和状态搬到这里：
 * - 协程跑在 SupervisorJob + Dispatchers.IO 上，不绑任何页面生命周期；
 * - 状态用 Compose mutableStateOf 保存，页面重新进来时观察同一份对象，
 *   直接看到已经累积的思考过程/正文/结果，等于「后台继续、回来续显」。
 */
object AiTaskCenter {
    private const val TAG = "AiTaskCenter"

    /** 应用级作用域：永不因页面销毁而取消 */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---------------- 一键分析 ----------------

    enum class InsightStatus { IDLE, RUNNING, DONE, ERROR }

    /** 单个分析任务的实时状态。字段都是 Compose 状态，改值即触发重组 */
    class InsightState {
        var status by mutableStateOf(InsightStatus.IDLE)
            private set
        /** 思考过程（GLM reasoning_content），边想边冒 */
        var reasoning by mutableStateOf("")
            private set
        /** 正文（最终是一段 JSON），流结束后解析 */
        var raw by mutableStateOf("")
            private set
        var insight by mutableStateOf<AiInsight?>(null)
            private set
        /** 真实错误文案（已经过 humanizeError 翻译），不再是「请检查网络」 */
        var error by mutableStateOf<String?>(null)
            private set
        /** 思考面板是否展开：流式期间默认展开 */
        var expanded by mutableStateOf(true)

        internal fun reset() {
            status = InsightStatus.RUNNING
            reasoning = ""
            raw = ""
            insight = null
            error = null
            expanded = true
        }

        internal fun done(v: AiInsight) {
            insight = v
            status = InsightStatus.DONE
            expanded = false
        }

        /** 用户手动修改分析结果：保留当前会话，下次进入同一份内容仍看到改后的版本 */
        fun editInsight(v: AiInsight) {
            if (status == InsightStatus.DONE) insight = v
        }

        internal fun fail(msg: String) {
            error = msg
            status = InsightStatus.ERROR
        }

        internal fun appendReasoning(s: String) { reasoning += s }
        internal fun appendRaw(s: String) { raw += s }
    }

    private val insights = mutableMapOf<String, InsightState>()

    fun insight(key: String): InsightState = insights.getOrPut(key) { InsightState() }

    fun startInsight(
        key: String,
        kind: String,
        title: String,
        facts: String,
        endpoint: AiEndpoint = AiPresets.builtin,
        context: Context? = null,
        useLocal: Boolean = false,
        localModelId: String? = null
    ) {
        val s = insight(key)
        if (s.status == InsightStatus.RUNNING) return
        s.reset()
        scope.launch {
            try {
                // 智能分配把这条短分析交给本机时，先试端侧；端侧没就绪或返回不合格，
                // 再走原来的云端流式分析，用户不会看到一次失败。
                if (useLocal && context != null && LocalLlm.state(context) == LocalLlmState.READY) {
                    s.appendReasoning("本机模型分析中…")
                    val local = AiApi.analyzeLocal(context, kind, title, facts, localModelId)
                    if (local != null) {
                        s.done(local)
                        return@launch
                    }
                    s.appendReasoning("本机结果不合格，回退云端…")
                }
                AiApi.analyzeStream(endpoint, kind, title, facts).collect { chunk ->
                    if (chunk.startsWith("\u0001")) {
                        s.appendReasoning(chunk.removePrefix("\u0001"))
                    } else {
                        s.appendRaw(chunk)
                    }
                }
                val parsed = runCatching { AiApi.parseInsight(s.raw) }.getOrNull()
                if (parsed == null) {
                    // 把模型真实返回的一小段带进错误：空正文说明模型只吐了思考没给结论；
                    // 有正文但解析失败说明格式跑偏，用户能看到是什么，别再给空话。
                    val snippet = s.raw.trim().take(120)
                    s.fail(
                        when {
                            s.raw.isBlank() -> "模型只输出了思考过程，没有给出结论正文，请重试"
                            else -> "模型返回的格式没法解析：${snippet}${if (s.raw.length > 120) "…" else ""}"
                        }
                    )
                } else {
                    s.done(parsed)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Throwable) {
                Log.e(TAG, "analyze failed: $key", e)
                // AiApi 抛错时 message 已经是 humanizeError 翻译过的人话（形如「HTTP 429 您的账户已达到速率限制…」），
                // 把前面的 HTTP 码剥掉，给用户看真正的原因，不再吞成「请检查网络」。
                val raw = e.message ?: "网络异常，请稍后重试"
                s.fail(raw.replace(Regex("^HTTP \\d+\\s*"), "").ifBlank { "网络异常，请稍后重试" })
            }
        }
    }

    // ---------------- AI 助手：进行中回复的镜像 ----------------
    // 页面里的 messages 是 remember 状态，离开即丢。这里把流式累积的原文镜像一份，
    // 用户中途退出去再进来时，根据 chatActive 判断「有回复正在生成」，把镜像渲染到最后一条气泡上。

    /** 已累积的回复正文（含控制标记原文，与页面 buffer 对齐） */
    var chatText by mutableStateOf("")
        private set
    var chatThinking by mutableStateOf("")
        private set
    /** 当前处理阶段（网页搜索中 / 获取相关知识 / 思考中 / 答案整合中），退页面回来也能接着显示 */
    var chatPhase by mutableStateOf<String?>(null)
        private set
    var chatActive by mutableStateOf(false)
        private set
    var chatFailure by mutableStateOf<String?>(null)
        private set

    fun beginChat() {
        chatText = ""
        chatThinking = ""
        chatPhase = null
        chatFailure = null
        chatActive = true
    }

    fun setPhase(phase: String?) {
        chatPhase = phase
    }

    fun appendChatChunk(chunk: String) {
        if (chunk.startsWith("\u0001")) chatThinking += chunk.removePrefix("\u0001")
        else chatText += chunk
    }

    /** 流式结束（无论成功失败）。页面在协程里会自己做后续处理 */
    fun finishChat(failure: String?) {
        chatFailure = failure
        chatPhase = null
        chatActive = false
    }
}
