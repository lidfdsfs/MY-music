package com.example.my.data.music

/** 一个歌词翻译目标语言；code 用于持久化，google/myMemory 是两个网页接口各自认的代码。 */
data class LyricTargetLanguage(
    val code: String,
    val labelZh: String,
    val labelEn: String,
    val myMemory: String,
    val google: String
)

/**
 * 译词可选目标语言。
 *
 * 之前只有“中译英”，现在至少覆盖常见语种；接口不认的语种会自动退回 Google 翻译，
 * 缓存按“目标语言 + 原文”分文件，切换语言不会串味。
 */
object LyricTranslateLanguages {
    const val ENGLISH = "en"

    val ALL: List<LyricTargetLanguage> = listOf(
        LyricTargetLanguage("en", "英语", "English", "en-US", "en"),
        LyricTargetLanguage("zh", "简体中文", "Chinese", "zh-CN", "zh-CN"),
        LyricTargetLanguage("ja", "日语", "Japanese", "ja-JP", "ja"),
        LyricTargetLanguage("ko", "韩语", "Korean", "ko-KR", "ko"),
        LyricTargetLanguage("fr", "法语", "French", "fr-FR", "fr"),
        LyricTargetLanguage("de", "德语", "German", "de-DE", "de"),
        LyricTargetLanguage("es", "西班牙语", "Spanish", "es-ES", "es"),
        LyricTargetLanguage("ru", "俄语", "Russian", "ru-RU", "ru"),
        LyricTargetLanguage("pt", "葡萄牙语", "Portuguese", "pt-PT", "pt"),
        LyricTargetLanguage("it", "意大利语", "Italian", "it-IT", "it"),
        LyricTargetLanguage("th", "泰语", "Thai", "th-TH", "th"),
        LyricTargetLanguage("vi", "越南语", "Vietnamese", "vi-VN", "vi"),
        LyricTargetLanguage("ar", "阿拉伯语", "Arabic", "ar-SA", "ar")
    )

    fun byCode(code: String?): LyricTargetLanguage =
        ALL.firstOrNull { it.code == code } ?: ALL.first()
}
