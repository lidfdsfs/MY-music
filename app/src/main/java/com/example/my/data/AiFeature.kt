package com.example.my.data

/**
 * 可以单独分配模型的 AI 功能。
 *
 * heavy = true 的任务通常需要更长上下文、更强推理（对话、行程、网页归纳、页内问答）；
 * heavy = false 的任务以短文本抽取/改写为主（歌曲故事、歌词补齐、一键分析、标题建议）。
 * 全局「智能分配」按复杂度和 localCapable 选择模型：重的跟当前主力模型，轻的用内置极速模型；
 * 允许本地参与时，适合端侧处理的轻任务（以及手动指定的行程规划）优先交给本机模型，失败再回退云端。
 */
enum class AiFeature(
    val key: String,
    val zh: String,
    val en: String,
    val descZh: String,
    val descEn: String,
    val heavy: Boolean,
    val localCapable: Boolean,
    val defaultRoute: String,
    /** 这个功能需要端点具备的能力；分配页只列真正能做这件事的模型 */
    val capability: AiCapability = AiCapability.TEXT
) {
    ASSISTANT(
        "assistant", "AI 助手对话", "AI assistant",
        "主对话、工具调用与意图识别", "Main chat, tools and intent routing",
        heavy = true, localCapable = true, defaultRoute = "global"
    ),
    DISCOVER(
        "discover", "发现页 AI 搜索总结", "Discover AI answer",
        "联网检索后归纳答案与来源", "Search the web, then summarize with sources",
        heavy = true, localCapable = false, defaultRoute = "global"
    ),
    TRIP(
        "trip", "AI 行程规划", "Trip planning",
        "按目的地、天数与预算生成逐日行程", "Build a day-by-day plan from destination and budget",
        heavy = true, localCapable = true, defaultRoute = "global"
    ),
    INLINE(
        "inline", "页面内 AI 问答", "In-page AI Q&A",
        "攻略、笔记等页面里的追问", "Follow-up questions inside guides and notes",
        heavy = true, localCapable = false, defaultRoute = "global"
    ),
    VISION(
        "vision", "图像识别 / 看图问答", "Image understanding",
        "分析照片内容、看图回答，以及图片文字识别", "Describe photos, answer questions about images and read text in them",
        heavy = true, localCapable = false, defaultRoute = "global",
        capability = AiCapability.VISION
    ),
    IMAGE_GEN(
        "image_gen", "AI 图片生成", "Image generation",
        "根据一句话生成图片，可在下方指定绘图模型", "Create an image from a prompt with a chosen image model",
        heavy = false, localCapable = false, defaultRoute = "global",
        capability = AiCapability.IMAGE_GEN
    ),
    VIDEO_GEN(
        "video_gen", "AI 视频生成", "Video generation",
        "根据一句话生成短视频，可在下方指定视频模型", "Create a short video from a prompt with a chosen video model",
        heavy = false, localCapable = false, defaultRoute = "global",
        capability = AiCapability.VIDEO_GEN
    ),
    SONG_STORY(
        "song_story", "歌曲故事 / 解读", "Song story",
        "联网查资料后归纳创作背景与歌词主题", "Research and summarize a song's background",
        heavy = false, localCapable = true, defaultRoute = "global"
    ),
    SONG_LYRICS(
        "song_lyrics", "歌词联网补齐", "Lyrics lookup",
        "网页歌词抓取失败时用模型补齐", "Model fallback when web lyrics are missing",
        heavy = false, localCapable = false, defaultRoute = "global"
    ),
    INSIGHT(
        "insight", "AI 一键分析", "AI insight",
        "足迹、花费与攻略的短分析", "Short analysis of footprints, spending and guides",
        heavy = false, localCapable = true, defaultRoute = "ep:1"
    ),
    FOOTPRINT_SUMMARY(
        "footprint_summary", "AI 足迹总结", "Footprint summary",
        "在足迹详情、上传页里生成一段总结", "Write a short summary for a footprint in its detail or upload page",
        heavy = false, localCapable = false, defaultRoute = "global"
    ),
    TICKET(
        "ticket", "车票 AI 建议", "Ticket advice",
        "结合车次、航班与价格给出出行建议", "Suggest a trip based on train and flight results",
        heavy = false, localCapable = false, defaultRoute = "global"
    ),
    SHOPPING(
        "shopping", "购物比价 AI 分析", "Shopping price analysis",
        "根据全网公开报价归纳价格区间与参考走势", "Summarize public prices and a reference trend",
        heavy = false, localCapable = false, defaultRoute = "global"
    ),
    OCR(
        "ocr", "图片文字识别（OCR）", "OCR",
        "课表、歌单等截图里的文字识别", "Read text from timetable and playlist screenshots",
        heavy = false, localCapable = false, defaultRoute = "global",
        capability = AiCapability.VISION
    ),
    ROUTE_TITLE(
        "route_title", "路线标题建议", "Route titles",
        "根据轨迹点生成一句路线名", "Name a route from its track points",
        heavy = false, localCapable = true, defaultRoute = "ep:1"
    );

    companion object {
        const val ROUTE_GLOBAL = "global"
        const val ROUTE_LOCAL = "local"
        /** 指定某个具体本地模型：local:<LocalModelSpec.id> */
        const val ROUTE_LOCAL_PREFIX = "local:"
        const val ROUTE_PREFIX_API = "ep:"

        fun fromKey(key: String): AiFeature? = values().firstOrNull { it.key == key }

        fun localRoute(modelId: String): String = ROUTE_LOCAL_PREFIX + modelId

        fun localModelId(route: String?): String? =
            route?.takeIf { it.startsWith(ROUTE_LOCAL_PREFIX) }
                ?.removePrefix(ROUTE_LOCAL_PREFIX)
                ?.takeIf { it.isNotBlank() }
    }
}

