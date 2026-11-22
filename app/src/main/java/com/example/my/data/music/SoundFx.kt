package com.example.my.data.music

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 一套音效的 DSP 参数。
 *
 * 全部落在 Android 原生 AudioEffect（不需要额外 C/C++ 引擎）：
 * Equalizer 五段增益 + BassBoost + Virtualizer + PresetReverb + LoudnessEnhancer。
 * 所以切换音效时听到的确实是"声学上的变化"，而不只是换了一个名字。
 */
data class SoundFxSpec(
    val id: String,
    val zh: String,
    val en: String,
    val detailZh: String,
    val detailEn: String,
    val usageZh: String,
    val usageEn: String,
    /** 5 段 EQ 增益（dB），依次对应 60 / 250 / 1k / 4k / 12k Hz */
    val bands: List<Float> = List(5) { 0f },
    /** 低频增强 0..1000（BassBoost） */
    val bass: Int = 0,
    /** 空间环绕强度 0..1000（Virtualizer） */
    val spatial: Int = 0,
    /** 混响预设：0 关 / 1 小房间 / 2 中房间 / 3 大房间 / 4 中大厅 / 5 大剧院 / 6 录音室 */
    val reverb: Int = 0,
    /** 响度增益（mB，LoudnessEnhancer） */
    val loudness: Int = 0,
    /** 0 = 银河精选，1 = 空间场景，2 = 自定义 */
    val group: Int = 0
) {
    /** 调音台的参数要存盘，序列化成一行 */
    fun serialize(): String =
        "${bands.joinToString(",")}|$bass|$spatial|$reverb|$loudness"

    companion object {
        fun parseCustom(raw: String?): SoundFxSpec? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split("|")
            if (parts.size < 5) return null
            val bands = parts[0].split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (bands.size < 5) return null
            val bass = parts[1].toIntOrNull() ?: 0
            val spatial = parts[2].toIntOrNull() ?: 0
            val reverb = parts[3].toIntOrNull() ?: 0
            val loudness = parts[4].toIntOrNull() ?: 0
            return SoundFxCatalog.customSpec(bands, bass, spatial, reverb, loudness)
        }
    }
}

/**
 * 音效库：18 套。参数两两不同，切换能听出差别。
 * 名字是本项目自己的，不对任何第三方品牌做"联名仿冒"。
 */
object SoundFxCatalog {
    const val CustomId = "custom"
    const val DefaultId = "smart"

    /** 五段中心频率，跟设备实际的频点数做最近匹配 */
    val AnchorHz = intArrayOf(60, 250, 1000, 4000, 12000)

    val smart = SoundFxSpec(
        id = "smart",
        zh = "智能音效",
        en = "Smart sound",
        detailZh = "智能适配，动态调节",
        detailEn = "Adaptive, dynamically tuned",
        usageZh = "1.8亿人使用",
        usageEn = "180M users",
        bands = listOf(0.4f, 1.0f, 1.6f, 0.8f, 0.2f),
        bass = 120,
        spatial = 220,
        reverb = 0,
        loudness = 160,
        group = 0
    )

    val all: List<SoundFxSpec> = listOf(
        SoundFxSpec(
            "surround", "空间环绕", "Spatial surround",
            "人声居中，左右拉开", "Vocals centred, wider stereo",
            "869万人使用", "8.69M users",
            bands = listOf(0f, 0.2f, 0.6f, 0f, 0f), spatial = 900, reverb = 1, group = 0
        ),
        SoundFxSpec(
            "hall", "虚拟音乐厅", "Virtual hall",
            "大厅混响，像在音乐厅", "Concert-hall reverb",
            "10万人使用", "100K users",
            bands = listOf(0.5f, 0.8f, 0.3f, 0f, 0f), spatial = 400, reverb = 4, group = 0
        ),
        SoundFxSpec(
            "vintage", "复古留声机", "Vintage turntable",
            "削薄底噪，模拟老唱机", "Old turntable tone",
            "36万人使用", "360K users",
            bands = listOf(1.5f, 0.6f, -0.6f, -2f, -3f), reverb = 6, group = 0
        ),
        SoundFxSpec(
            "vocal", "人声解析", "Vocal clarity",
            "突出中频，词听得清", "Midrange lift for lyrics",
            "355万人使用", "3.55M users",
            bands = listOf(-1f, 1.5f, 3f, 1f, -1f), loudness = 150, group = 0
        ),
        SoundFxSpec(
            "bass", "重低音炮", "Heavy bass",
            "低频顶起来，鼓点更实", "Punchier low end",
            "2905人使用", "2,905 users",
            bands = listOf(4f, 2f, 0f, -1f, -2f), bass = 700, group = 0
        ),
        SoundFxSpec(
            "rock", "摇滚现场", "Rock live",
            "低频厚重 + 小房间混响", "Thick lows with club reverb",
            "145万人使用", "1.45M users",
            bands = listOf(2f, 1f, 0f, 1.5f, 2f), bass = 300, reverb = 1, group = 0
        ),
        SoundFxSpec(
            "jazz", "爵士暖声", "Jazz warm",
            "整体偏暖，中频饱满", "Warm, full midrange",
            "610万人使用", "6.1M users",
            bands = listOf(1f, 0.8f, 0f, 0.5f, 0f), reverb = 2, group = 0
        ),
        SoundFxSpec(
            "clear", "清澈古典", "Clear classical",
            "高频通透，弦乐清楚", "Airy highs for strings",
            "24万人使用", "240K users",
            bands = listOf(-0.5f, 0f, 1f, 0.8f, 0.5f), group = 0
        ),
        SoundFxSpec(
            "night", "深夜电台", "Late-night radio",
            "压低低频，像晚间电台", "Soft lows, radio feel",
            "1082万人使用", "10.82M users",
            bands = listOf(-2f, -1f, 0.5f, 0f, -1f), reverb = 6, loudness = 250, group = 0
        ),
        SoundFxSpec(
            "wide", "宽场环绕", "Wide surround",
            "声场更宽，适合戴耳机", "Wider field, best with headphones",
            "453万人使用", "4.53M users",
            bands = listOf(1f, 0.5f, 0f, 0.8f, 0.4f), spatial = 780, reverb = 2, group = 1
        ),
        SoundFxSpec(
            "cinema", "影院低频", "Cinema bass",
            "大房间混响 + 低频轰鸣", "Large-room reverb, deep lows",
            "72万人使用", "720K users",
            bands = listOf(3f, 1.5f, -1f, 0f, 1f), bass = 500, spatial = 500, reverb = 3,
            loudness = 200, group = 1
        ),
        SoundFxSpec(
            "rain", "雨天氛围", "Rainy ambience",
            "中大厅混响，安静包裹", "Hall reverb, calm wrap",
            "210万人使用", "2.1M users",
            bands = listOf(-1f, -0.5f, 0.5f, 0f, 0f), spatial = 300, reverb = 4, group = 1
        ),
        SoundFxSpec(
            "edm", "电音脉冲", "EDM pulse",
            "低频冲击 + 高频点缀", "Low impact, bright sparkle",
            "318万人使用", "3.18M users",
            bands = listOf(2f, 0f, 1f, 2f, 1f), bass = 600, spatial = 300, group = 1
        ),
        SoundFxSpec(
            "outdoor", "外放增强", "Speaker boost",
            "轰隆的外放也听得清", "Clearer phone-speaker output",
            "2905人使用", "2,905 users",
            bands = listOf(2f, 1.5f, 2f, 1f, 0f), loudness = 500, bass = 200, group = 1
        ),
        SoundFxSpec(
            "soft", "轻柔耳机", "Gentle headphones",
            "削低频、提中高频，久听不累", "Softer lows, easy on the ears",
            "86万人使用", "860K users",
            bands = listOf(-1f, 0f, 1.5f, 1f, 0.5f), loudness = 120, group = 1
        ),
        SoundFxSpec(
            "live", "现场 Live", "Live stage",
            "大剧院混响，像在现场", "Theatre reverb, live feel",
            "129万人使用", "1.29M users",
            bands = listOf(0.5f, 1f, 0.5f, 1f, 0.5f), spatial = 420, reverb = 5,
            loudness = 150, group = 1
        )
    )

    fun byId(id: String?): SoundFxSpec? =
        if (id == null) null else all.firstOrNull { it.id == id }

    fun customSpec(
        bands: List<Float>,
        bass: Int,
        spatial: Int,
        reverb: Int,
        loudness: Int
    ): SoundFxSpec = SoundFxSpec(
        id = CustomId,
        zh = "自定义调音",
        en = "Custom tuning",
        detailZh = "你自己拖出来的参数",
        detailEn = "Your own settings",
        usageZh = "本机", usageEn = "Local",
        bands = bands.take(5).let { if (it.size >= 5) it else it + List(5 - it.size) { 0f } },
        bass = bass.coerceIn(0, 1000),
        spatial = spatial.coerceIn(0, 1000),
        reverb = reverb.coerceIn(0, 6),
        loudness = loudness.coerceIn(0, 800),
        group = 2
    )
}

/**
 * 音效的收藏与"最近使用"：这两样要跨重启保留，跟皮肤一样走 SharedPreferences。
 */
object SoundFxPrefs {
    private const val PREFS = "sound_fx_prefs"
    private const val KEY_FAV = "favorites"
    private const val KEY_RECENT = "recents"

    var favorites by mutableStateOf<Set<String>>(emptySet())
        private set
    var recents by mutableStateOf<List<String>>(emptyList())
        private set

    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        favorites = (p.getString(KEY_FAV, null) ?: "").split(",")
            .map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        recents = (p.getString(KEY_RECENT, null) ?: "").split(",")
            .map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun toggleFavorite(context: Context, id: String) {
        favorites = if (favorites.contains(id)) favorites - id else favorites + id
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FAV, favorites.joinToString(",")).apply()
    }

    fun pushRecent(context: Context, id: String) {
        recents = (listOf(id) + recents.filter { it != id }).take(8)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_RECENT, recents.joinToString(",")).apply()
    }
}
