package com.example.my.data.ai

/**
 * 本地 AI「运行内存预算」与「上下文大小」的纯算法层。
 *
 * 不依赖 Android，方便 JVM 单测。系统信息读取放在 [LocalAiPrefs]。
 *
 * 这里刻意不把「内存」当成一个能强塞给 MNN 的硬上限：MNN 没有暴露
 * malloc 级别的封顶接口。我们能真实控制的是两件事：
 *  1. 加载前用「权重 + KV cache」估算是否超出预算，超了就不加载；
 *  2. 把上下文长度写进模型运行配置的 max_all_tokens，限制 KV cache 的规模。
 * 所以预算是一个真实参与决策的护栏，而不是只存在界面上的数字。
 */
object LocalAiPolicy {

    const val MIN_MEMORY_MB = 1024
    const val MAX_MEMORY_MB = 12 * 1024
    const val MEMORY_STEP_MB = 512

    /** 自动上下文的下限 / 上限 */
    const val AUTO_MIN_TOKENS = 4096
    const val AUTO_MAX_TOKENS = 32768

    /** 手动可选档位：32K / 64K / 84K / 128K；其他值由「自定义」输入 */
    val MANUAL_CONTEXT_OPTIONS = listOf(32_768, 65_536, 86_016, 131_072)

    /** 自定义上下文的允许范围（1K – 512K），实际还会被模型自身窗口夹住 */
    const val MANUAL_CUSTOM_MIN_TOKENS = 1024
    const val MANUAL_CUSTOM_MAX_TOKENS = 524_288
    const val DEFAULT_MANUAL_TOKENS = 32_768

    enum class ContextMode { AUTO, MANUAL }

    data class ContextPlan(
        val tokens: Int,
        /** AUTO / MANUAL / 因模型上限被夹住 */
        val source: String,
        val reasonZh: String,
        val reasonEn: String
    )

    /** 设备总内存对应的建议分配：约一半给本地 AI，最多 6GB，最少 1GB */
    fun recommendedMemoryMb(totalRamMb: Long): Int {
        if (totalRamMb <= 0L) return 3072
        val half = totalRamMb / 2
        return roundToStep(half).coerceIn(MIN_MEMORY_MB, 6144)
    }

    /** 滑条可到的上限：给系统至少留 1GB，最多 12GB；设备信息未知时给 8GB */
    fun maxMemoryMb(totalRamMb: Long): Int {
        if (totalRamMb <= 0L) return 8192
        val usable = totalRamMb - 1024L
        return roundToStep(usable).coerceIn(MIN_MEMORY_MB, MAX_MEMORY_MB)
    }

    fun roundToStep(mb: Long): Int {
        if (mb <= 0L) return MIN_MEMORY_MB
        val step = MEMORY_STEP_MB.toLong()
        return (((mb + step / 2) / step) * step).toInt()
    }

    /**
     * 估算运行占用：模型权重 + 上下文 KV cache + 运行时余量。
     * 系数是工程估算（不是精确 profiling），用于「能不能装进预算」的准入判断。
     */
    fun estimateRuntimeMb(modelMb: Long, contextTokens: Int): Int {
        val weights = modelMb.coerceAtLeast(0L)
        val kv = contextTokens.coerceAtLeast(0) * 0.055
        val overhead = 128.0
        return (weights + kv + overhead).toInt()
    }

    fun fitsMemoryBudget(modelMb: Long, budgetMb: Int, contextTokens: Int): Boolean =
        estimateRuntimeMb(modelMb, contextTokens) <= budgetMb

    /**
     * 自动上下文：按本轮问题的真实复杂度给档位。
     *
     * 目标不是「越大越好」，而是够用就好：
     *  - 寒暄、单句问答：4K
     *  - 带条件/多问的日常问题：8K
     *  - 分析、比较、规划、代码、长材料：16K
     *  - 多约束长任务 / 超大检索材料：32K
     */
    fun autoContextTokens(
        userText: String,
        contextChars: Int = 0,
        historyTurns: Int = 0
    ): Int {
        val text = userText.trim()
        var score = 0
        when {
            text.length >= 2000 -> score += 3
            text.length >= 800 -> score += 2
            text.length >= 300 -> score += 1
        }
        val markers = listOf(
            "分析", "比较", "对比", "规划", "方案", "详细", "报告", "论文",
            "代码", "推理", "为什么", "步骤", "多步", "总结", "翻译", "行程",
            "攻略", "预算", "计算", "评估", "建议", "路线"
        )
        val hits = markers.count { text.contains(it) }
        score += hits.coerceAtMost(4)
        if (text.count { it == '?' || it == '？' } >= 2) score += 1
        if (contextChars >= 12000) score += 3
        else if (contextChars >= 4000) score += 2
        else if (contextChars >= 1500) score += 1
        if (historyTurns >= 12) score += 2
        else if (historyTurns >= 8) score += 1

        return when {
            score <= 1 -> 4096
            score <= 3 -> 8192
            score <= 5 -> 16384
            else -> 32768
        }
    }

    fun resolveContext(
        mode: ContextMode,
        manualTokens: Int,
        userText: String,
        contextChars: Int = 0,
        historyTurns: Int = 0,
        modelLimit: Int = 0
    ): ContextPlan {
        val cap = if (modelLimit > 0) modelLimit else 200_000
        val requested = if (mode == ContextMode.AUTO) {
            autoContextTokens(userText, contextChars, historyTurns)
        } else {
            manualTokens.coerceAtLeast(MANUAL_CUSTOM_MIN_TOKENS)
        }
        val tokens = requested.coerceAtMost(cap).coerceAtLeast(1024)
        val clamped = tokens != requested
        return if (mode == ContextMode.AUTO) {
            ContextPlan(
                tokens = tokens,
                source = if (clamped) "AUTO_CLAMPED" else "AUTO",
                reasonZh = "按问题复杂度自动选择 ${label(tokens)}" +
                    if (clamped) "（受模型上限 ${label(cap)} 限制）" else "",
                reasonEn = "Auto ${label(tokens)}" +
                    if (clamped) " (capped at ${label(cap)})" else ""
            )
        } else {
            ContextPlan(
                tokens = tokens,
                source = if (clamped) "MANUAL_CLAMPED" else "MANUAL",
                reasonZh = "手动 ${label(tokens)}" +
                    if (clamped) "（受模型上限 ${label(cap)} 限制）" else "",
                reasonEn = "Manual ${label(tokens)}" +
                    if (clamped) " (capped at ${label(cap)})" else ""
            )
        }
    }

    /**
     * 本地模型还需要「够装下这次输入」：上下文至少覆盖 prompt + 输出。
     * 手动档位偏小时自动上调，避免模型直接读不全问题。
     */
    fun localContextTokens(
        requestedTokens: Int,
        promptChars: Int,
        maxNewTokens: Int,
        modelLimit: Int
    ): Int {
        val promptTokens = (promptChars / 1.8f).toInt() + maxNewTokens + 256
        val wanted = maxOf(requestedTokens, promptTokens)
        val cap = if (modelLimit > 0) modelLimit else wanted
        return wanted.coerceAtMost(cap).coerceAtLeast(1024)
    }

    fun label(tokens: Int): String = when {
        tokens >= 1024 && tokens % 1024 == 0 -> "${tokens / 1024}K"
        tokens >= 1024 -> String.format(java.util.Locale.US, "%.1fK", tokens / 1024f)
        else -> "$tokens"
    }

    fun gbLabel(mb: Int): String = when {
        mb % 1024 == 0 -> "${mb / 1024} GB"
        else -> String.format(java.util.Locale.US, "%.1f GB", mb / 1024f)
    }
}
