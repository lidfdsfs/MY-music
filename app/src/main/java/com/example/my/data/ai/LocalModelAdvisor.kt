package com.example.my.data.ai

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs

/**
 * 本地模型下载建议。
 *
 * 不只看「哪个模型最好」，而是按用户的真实配置给建议：
 * - 设备内存 / 可用存储：决定能跑多大、装不装得下；
 * - 当前模型分配：对话、行程这类重任务交给本机时，优先理解力；
 *   只跑一键分析、路线标题这类短任务时，优先速度和省空间。
 *
 * 纯计算部分不依赖 Android，方便单测；只有 [profile] 需要读系统信息。
 */
object LocalModelAdvisor {

    data class DeviceProfile(
        val totalRamMb: Long,
        val freeStorageMb: Long,
        val cores: Int
    )

    data class Recommendation(
        val spec: LocalModelSpec,
        val reasonZh: String,
        val reasonEn: String
    )

    /** 读取当前设备的真实配置（总内存、模型目录可用空间、CPU 核数） */
    fun profile(context: Context): DeviceProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val totalRamMb = if (mi.totalMem > 0) mi.totalMem / (1024L * 1024L) else 0L
        val freeStorageMb = runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes / (1024L * 1024L)
        }.getOrDefault(0L)
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        return DeviceProfile(totalRamMb, freeStorageMb, cores)
    }

    /**
     * 推荐一个本地模型。
     *
     * @param needQuality 是否已经把对话 / 行程规划交给本机。true 时优先理解力，
     *                    false 时优先速度与省空间。
     * @param catalog 模型目录，默认用 [LocalLlm.CATALOG]，传参便于单测。
     */
    fun recommend(
        profile: DeviceProfile,
        needQuality: Boolean,
        catalog: List<LocalModelSpec> = LocalLlm.CATALOG,
        memBudgetMb: Long = 0L
    ): Recommendation {
        if (catalog.isEmpty()) {
            return Recommendation(
                spec = LocalLlm.CATALOG.last(),
                reasonZh = "模型目录为空，先选最小包应急",
                reasonEn = "Catalog is empty; pick the smallest package"
            )
        }
        // 留 512MB 余量给运行时的临时文件和系统缓存，避免下满后手机没空间。
        // 同时用「运行内存预算」卡一道：预算装不下的模型不推荐，避免下完又跑不起来。
        val affordable = catalog.filter {
            it.approxMb + 512L <= profile.freeStorageMb &&
                (memBudgetMb <= 0L ||
                    LocalAiPolicy.estimateRuntimeMb(it.approxMb, 8192) <= memBudgetMb)
        }.ifEmpty { listOf(catalog.minBy { it.approxMb }) }

        val tiny = affordable.minByOrNull { it.approxMb } ?: catalog.minBy { it.approxMb }
        // 0.3B 只作为最低配兜底，别把「最小」直接当成「最合适」
        val small = affordable.firstOrNull { it.approxMb in 300L..500L } ?: tiny
        // 短任务优先 0.8–1.0GB 这一档：比 0.6B 稳，又比 1.7B 快
        val light = affordable.firstOrNull { it.approxMb in 800L..1000L } ?: small
        val mid = affordable.filter { it.approxMb in 900L..1500L }
            .maxByOrNull { it.approxMb }
            ?: affordable.firstOrNull { it.approxMb in 400L..900L }
            ?: small
        val large = affordable.filter { it.approxMb in 1500L..3000L }
            .maxByOrNull { it.approxMb }
            ?: mid

        val spec = when {
            // 内存偏小（含 4GB 机型实际可用更少）：别硬上 1.5B，0.6B/0.5B 才跑得动
            profile.totalRamMb in 1L..4_500L -> small
            // 重任务 + 大内存 + 足够空间：可以上 4B
            needQuality && profile.totalRamMb >= 12_000L && large.approxMb >= 2_000L -> large
            // 重任务但内存一般：1.7B / 1.5B 是速度与质量的平衡点
            needQuality -> mid
            // 只做短任务：优先秒回，0.8–1.0GB 级足够
            profile.totalRamMb >= 6_000L -> light
            else -> small
        }

        val ramText = if (profile.totalRamMb > 0) {
            "约 ${(profile.totalRamMb / 1024L).coerceAtLeast(1L)} GB 内存"
        } else {
            "内存未知"
        }
        val storageText = if (profile.freeStorageMb > 0) {
            "可用存储约 ${profile.freeStorageMb} MB"
        } else {
            "存储信息暂不可用"
        }
        val usageText = if (needQuality) {
            "你已把对话 / 行程这类重任务交给本机，优先保证理解力"
        } else {
            "当前主要是一键分析、路线标题这类短任务，优先速度和省空间"
        }
        val budgetText = if (memBudgetMb > 0L) {
            "；运行内存预算 ${LocalAiPolicy.gbLabel(memBudgetMb.toInt())}"
        } else {
            ""
        }
        val budgetEn = if (memBudgetMb > 0L) {
            ", a ${LocalAiPolicy.gbLabel(memBudgetMb.toInt())} memory budget"
        } else {
            ""
        }
        return Recommendation(
            spec = spec,
            reasonZh = "按你的 $ramText、$storageText$budgetText 和当前分配：$usageText。${spec.display}（${spec.sizeHint}）最合适。",
            reasonEn = "Based on $ramText, $storageText$budgetEn and your routing: " +
                if (needQuality) "you run chat/trips on-device, so reasoning matters most. "
                else "you mainly run short tasks, so speed and storage matter most. " +
                "${spec.display} (${spec.sizeHint}) is the best fit."
        )
    }
}
