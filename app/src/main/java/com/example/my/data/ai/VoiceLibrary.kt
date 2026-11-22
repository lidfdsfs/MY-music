package com.example.my.data.ai

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech

/** 系统 TTS 引擎里一个已安装的声音（voice） */
data class VoiceInfo(
    val id: String,
    val name: String,
    val locale: String,
    val quality: Int,
    /** true = 该音色合成时还要联网，离线场景别选 */
    val needsNetwork: Boolean
)

/**
 * 声音包目录里的一项。
 *
 * 两类：
 *  - 内置包（builtIn）：不下载任何东西，用系统已有中文音色 + 音调/语速组合出不同听感；
 *  - 扩展包：对应系统 TTS 引擎的一套语音数据（粤语、台湾腔、英日等），
 *    App 通过 ACTION_INSTALL_TTS_DATA 引导系统下载，回来后自动扫描并启用。
 */
data class VoicePack(
    val id: String,
    val zh: String,
    val en: String,
    val descZh: String,
    val descEn: String,
    /** 期望的语言标签，如 zh-CN / yue-HK / en-US */
    val locale: String,
    /** 匹配系统 voice 名称的关键词（小写） */
    val match: List<String> = emptyList(),
    /**
     * 同一语言里有多个系统音色时，取排序后的第几个（0 = 最合适的）。
     * 「温柔 / 沉稳 / 活泼」靠这个分别落到不同音色上——
     * 否则设备里只有一种中文音色时，四种试听听起来会一模一样。
     */
    val pick: Int = 0,
    val pitch: Float = 1.0f,
    val rate: Float = 1.0f,
    val builtIn: Boolean = false,
    val sizeZh: String,
    val sizeEn: String
)

/**
 * 语音库：可下载 / 可切换 / 可试听的声音包管理。
 *
 * 音色本体由系统 TTS 引擎提供（Android 标准能力），App 负责：
 * 目录与分类、下载引导、回来后自动识别新装语音、当前选择持久化、语速音调微调。
 * 这样不需要往 APK 里塞几百 MB 的离线合成模型，也不会编造一个并不存在的下载源。
 */
object VoiceLibrary {

    const val PREVIEW_TEXT = "你好，我是 MY足迹 的语音助手，带你去看看走过的路。"

    val CATALOG: List<VoicePack> = listOf(
        VoicePack(
            id = "standard", zh = "标准普通话", en = "Standard Mandarin",
            descZh = "系统默认音色，清晰稳重，开箱即用",
            descEn = "System default voice, clear and neutral",
            locale = "zh-CN", builtIn = true, sizeZh = "内置 · 0 MB", sizeEn = "Built-in · 0 MB"
        ),
        VoicePack(
            id = "gentle", zh = "温柔女声", en = "Gentle Female",
            descZh = "语速稍慢、音调柔和，适合长段播报",
            descEn = "Slower, softer pitch — good for long replies",
            locale = "zh-CN", match = listOf("female", "女", "xiaoxiao", "tingting", "xiaoyi"),
            pick = 1, pitch = 1.15f, rate = 0.86f, builtIn = true,
            sizeZh = "内置 · 0 MB", sizeEn = "Built-in · 0 MB"
        ),
        VoicePack(
            id = "calm", zh = "沉稳男声", en = "Calm Male",
            descZh = "音调偏低、节奏平稳，适合导航式播报",
            descEn = "Lower pitch, steady pace — good for directions",
            locale = "zh-CN", match = listOf("male", "男", "yunyang", "yunxi"),
            pick = 2, pitch = 0.72f, rate = 0.92f, builtIn = true,
            sizeZh = "内置 · 0 MB", sizeEn = "Built-in · 0 MB"
        ),
        VoicePack(
            id = "lively", zh = "活泼童声", en = "Lively Voice",
            descZh = "轻快明亮，适合短提醒与趣味文案",
            descEn = "Bright and upbeat — good for short reminders",
            locale = "zh-CN", pick = 3, pitch = 1.42f, rate = 1.16f, builtIn = true,
            sizeZh = "内置 · 0 MB", sizeEn = "Built-in · 0 MB"
        ),
        VoicePack(
            id = "cantonese", zh = "粤语声音包", en = "Cantonese",
            descZh = "粤语播报，需下载系统粤语语音数据",
            descEn = "Cantonese speech — needs system voice data",
            locale = "yue-HK", match = listOf("yue", "hk", "cantonese", "粤"),
            sizeZh = "约 30-60 MB", sizeEn = "~30-60 MB"
        ),
        VoicePack(
            id = "tw", zh = "台湾腔国语", en = "Taiwan Mandarin",
            descZh = "国语（台湾）音色，需下载对应语音数据",
            descEn = "Taiwan Mandarin — needs system voice data",
            locale = "zh-TW", match = listOf("tw", "taiwan", "zh_tw"),
            sizeZh = "约 30-60 MB", sizeEn = "~30-60 MB"
        ),
        VoicePack(
            id = "en_us", zh = "美式英语", en = "English (US)",
            descZh = "英文播报，多数设备已预装",
            descEn = "English speech — preinstalled on most devices",
            locale = "en-US", match = listOf("en-us", "en_us", "en-us-x"),
            sizeZh = "约 20-50 MB", sizeEn = "~20-50 MB"
        ),
        VoicePack(
            id = "ja", zh = "日本語", en = "Japanese",
            descZh = "日文播报，需下载系统日语语音数据",
            descEn = "Japanese speech — needs system voice data",
            locale = "ja-JP", match = listOf("ja", "jp", "japanese"),
            sizeZh = "约 30-60 MB", sizeEn = "~30-60 MB"
        )
    )

    private const val PREF = "voice_library"
    private const val KEY_ACTIVE = "active"
    private const val KEY_INSTALLED = "installed"
    private const val KEY_PENDING = "pending"
    private const val KEY_RATE = "rate"
    private const val KEY_PITCH = "pitch"
    private const val KEY_SYS_LOCALE = "sys_locale"
    private const val SYS_PREFIX = "sys:"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun activeId(ctx: Context): String = prefs(ctx).getString(KEY_ACTIVE, "standard") ?: "standard"

    fun isSystemVoice(id: String): Boolean = id.startsWith(SYS_PREFIX)

    fun systemVoiceId(id: String): String = id.removePrefix(SYS_PREFIX)

    fun activePack(ctx: Context): VoicePack {
        val id = activeId(ctx)
        if (isSystemVoice(id)) {
            return VoicePack(
                id = id, zh = systemVoiceId(id), en = systemVoiceId(id),
                descZh = "系统已安装的声音", descEn = "Installed system voice",
                locale = prefs(ctx).getString(KEY_SYS_LOCALE, "zh-CN") ?: "zh-CN",
                builtIn = true, sizeZh = "系统已安装", sizeEn = "Installed"
            )
        }
        return CATALOG.firstOrNull { it.id == id } ?: CATALOG.first()
    }

    fun setActive(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_ACTIVE, id).apply()
        markInstalled(ctx, id, true)
    }

    fun setSystemVoice(ctx: Context, voice: VoiceInfo) {
        prefs(ctx).edit()
            .putString(KEY_ACTIVE, SYS_PREFIX + voice.id)
            .putString(KEY_SYS_LOCALE, voice.locale)
            .apply()
        markInstalled(ctx, SYS_PREFIX + voice.id, true)
    }

    /** App 侧记录的「已下载」集合：用于区分「系统刚好有」与「用户主动装过」 */
    fun installed(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_INSTALLED, emptySet())?.toSet() ?: emptySet()

    fun markInstalled(ctx: Context, id: String, on: Boolean) {
        val next = installed(ctx).toMutableSet()
        if (on) next.add(id) else next.remove(id)
        prefs(ctx).edit().putStringSet(KEY_INSTALLED, next).apply()
    }

    fun pendingId(ctx: Context): String? = prefs(ctx).getString(KEY_PENDING, null)

    fun setPending(ctx: Context, id: String?) {
        prefs(ctx).edit().putString(KEY_PENDING, id).apply()
    }

    /** 全局语速 / 音调微调（0.5 ~ 2.0），与声音包自带参数相乘 */
    fun rate(ctx: Context): Float = prefs(ctx).getFloat(KEY_RATE, 1.0f)
    fun pitch(ctx: Context): Float = prefs(ctx).getFloat(KEY_PITCH, 1.0f)
    fun setRate(ctx: Context, v: Float) = prefs(ctx).edit().putFloat(KEY_RATE, v).apply()
    fun setPitch(ctx: Context, v: Float) = prefs(ctx).edit().putFloat(KEY_PITCH, v).apply()

    /** 语言标签归一化：zh_TW / zh-TW / zh-Hant-TW 都取前两段比较 */
    private fun norm(tag: String): String = tag.trim().lowercase().replace('_', '-')

    private fun langOf(tag: String): String = norm(tag).substringBefore('-')

    /**
     * 在系统已安装的声音里挑一个最贴合该声音包的：
     * 先看语言，再看名字关键词，最后偏好「不联网 + 质量高」。
     */
    fun matchVoice(pack: VoicePack, voices: List<VoiceInfo>): VoiceInfo? {
        if (voices.isEmpty()) return null
        val want = norm(pack.locale)
        val base = langOf(pack.locale)
        val candidates = voices.filter { v ->
            val l = norm(v.locale)
            l.startsWith(want) || l.startsWith(base) || l == base
        }.ifEmpty {
            // 粤语 yue 有些引擎标成 zh-HK，放宽到同语系
            if (base == "yue") voices.filter { norm(it.locale).contains("hk") } else emptyList()
        }
        if (candidates.isEmpty()) return null
        val scored = candidates.sortedWith(
            compareByDescending<VoiceInfo> { v ->
                pack.match.count { k -> v.name.lowercase().contains(k) || norm(v.locale).contains(k) }
            }.thenByDescending { if (it.needsNetwork) 0 else 1 }
                .thenByDescending { it.quality }
                .thenBy { it.id }
        )
        // 按 pick 序号落到不同音色：设备上只有一种中文音色时自然会回落到同一个，
        // 这时靠 pitch / rate 的差异仍然能听出区别
        return scored.getOrNull(pack.pick.coerceAtLeast(0) % scored.size)
    }

    /** 系统里已经有这套语音数据，就不用再引导下载了 */
    fun systemHas(pack: VoicePack, voices: List<VoiceInfo>): Boolean =
        pack.builtIn || matchVoice(pack, voices) != null

    /** 拉起系统 TTS 引擎的语音数据下载页 */
    fun installIntent(enginePackage: String? = null): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            if (!enginePackage.isNullOrBlank()) setPackage(enginePackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** 部分 ROM 没有标准数据安装入口，退回系统「文字转语音」设置页 */
    fun settingsIntent(): Intent = Intent("android.settings.TTS_SETTINGS").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * 从系统设置返回后调用：若等待中的声音包现在能匹配到系统 voice，
     * 自动标记为已下载并启用，返回它的 id 供 UI 提示。
     */
    fun settlePending(ctx: Context, voices: List<VoiceInfo>): String? {
        val id = pendingId(ctx) ?: return null
        val pack = CATALOG.firstOrNull { it.id == id } ?: run { setPending(ctx, null); return null }
        if (matchVoice(pack, voices) != null) {
            setActive(ctx, pack.id)
            setPending(ctx, null)
            return pack.id
        }
        return null
    }
}
