package com.example.my.data.ai

import android.app.ActivityManager
import android.content.Context

/**
 * 本地 AI 运行预算的持久化读取。真正的算法在 [LocalAiPolicy]。
 *
 * 两个键分开存：
 *  - memory_budget_mb：用户拉条选的内存预算（MB）
 *  - context_mode / manual_context_tokens：自动或手动的上下文档位
 *
 * 没保存过时：内存取设备建议值，上下文取自动。
 */
object LocalAiPrefs {

    private const val PREFS = "local_ai_runtime"
    private const val KEY_MEMORY = "memory_budget_mb"
    private const val KEY_CONTEXT_MODE = "context_mode"
    private const val KEY_MANUAL_TOKENS = "manual_context_tokens"

    fun totalRamMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return 0L
        val mi = ActivityManager.MemoryInfo()
        runCatching { am.getMemoryInfo(mi) }
        return if (mi.totalMem > 0L) mi.totalMem / (1024L * 1024L) else 0L
    }

    fun recommendedMemoryMb(context: Context): Int =
        LocalAiPolicy.recommendedMemoryMb(totalRamMb(context))

    fun maxMemoryMb(context: Context): Int =
        LocalAiPolicy.maxMemoryMb(totalRamMb(context))

    fun memoryBudgetMb(context: Context): Int {
        val saved = prefs(context).getInt(KEY_MEMORY, 0)
        val max = maxMemoryMb(context)
        return if (saved > 0) saved.coerceIn(LocalAiPolicy.MIN_MEMORY_MB, max)
        else recommendedMemoryMb(context).coerceIn(LocalAiPolicy.MIN_MEMORY_MB, max)
    }

    fun setMemoryBudgetMb(context: Context, mb: Int) {
        val max = maxMemoryMb(context)
        prefs(context).edit()
            .putInt(KEY_MEMORY, mb.coerceIn(LocalAiPolicy.MIN_MEMORY_MB, max))
            .apply()
    }

    fun contextMode(context: Context): LocalAiPolicy.ContextMode =
        if (prefs(context).getString(KEY_CONTEXT_MODE, null) == "manual") {
            LocalAiPolicy.ContextMode.MANUAL
        } else {
            LocalAiPolicy.ContextMode.AUTO
        }

    fun setContextMode(context: Context, mode: LocalAiPolicy.ContextMode) {
        prefs(context).edit()
            .putString(KEY_CONTEXT_MODE, if (mode == LocalAiPolicy.ContextMode.MANUAL) "manual" else "auto")
            .apply()
    }

    fun manualContextTokens(context: Context): Int {
        val saved = prefs(context).getInt(KEY_MANUAL_TOKENS, LocalAiPolicy.DEFAULT_MANUAL_TOKENS)
        return saved.coerceIn(
            LocalAiPolicy.MANUAL_CUSTOM_MIN_TOKENS,
            LocalAiPolicy.MANUAL_CUSTOM_MAX_TOKENS
        )
    }

    /** 保存手动档位：预设和自定义都走这里，范围 1K–512K。 */
    fun setManualContextTokens(context: Context, tokens: Int) {
        val safe = tokens.coerceIn(
            LocalAiPolicy.MANUAL_CUSTOM_MIN_TOKENS,
            LocalAiPolicy.MANUAL_CUSTOM_MAX_TOKENS
        )
        prefs(context).edit().putInt(KEY_MANUAL_TOKENS, safe).apply()
    }

    /**
     * 本次请求实际使用的上下文预算。
     * [modelLimit] 是模型自身窗口（0 = 未知，按 200K 处理）。
     */
    fun effectiveContextTokens(
        context: Context,
        userText: String,
        contextChars: Int = 0,
        historyTurns: Int = 0,
        modelLimit: Int = 0
    ): Int = LocalAiPolicy.resolveContext(
        mode = contextMode(context),
        manualTokens = manualContextTokens(context),
        userText = userText,
        contextChars = contextChars,
        historyTurns = historyTurns,
        modelLimit = modelLimit
    ).tokens

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
