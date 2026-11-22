package com.example.my.data.ai

import com.example.my.data.CityCoords
import com.example.my.data.IntentDecision
import com.example.my.state.AppState
import org.json.JSONObject

/**
 * 现场指代消解：把「这个」「刚才那篇」「切到那边」补成 App 里真实存在的对象。
 *
 * system 里已经注入了现场快照，但模型仍可能因为省略主语给出空 target；
 * 更常见的是用户自己就说「把它收藏一下」，模型只能原样回一个空参数，
 * 网关校验后回一句「缺少必填参数」——看起来就像 AI 听不懂人话。
 *
 * 这里不改变模型的判断，只在执行前补参数：**模型判意图，App 补现场**。
 * 分工明确，也避免把一堆 AppState 细节塞进 prompt。
 */
object AiResolution {

    data class SceneFacts(
        val guideTitle: String = "",
        val guideCity: String = "",
        val tripName: String = "",
        val tripDestination: String = "",
        val weatherCity: String = "",
        val lastFootprintCity: String = "",
        /** 本轮对话里出现的城市；现场没有明确对象时用它兜底 */
        val focusCity: String = ""
    ) {
        /** 一句话给模型做抽取上下文，避免把整个 AppState 塞进 prompt */
        fun hint(): String = buildList {
            if (guideTitle.isNotBlank()) add("正在看攻略《$guideTitle》${if (guideCity.isNotBlank()) "（$guideCity）" else ""}")
            if (tripName.isNotBlank()) add("正在看行程《$tripName》${if (tripDestination.isNotBlank()) "（$tripDestination）" else ""}")
            if (focusCity.isNotBlank()) add("本轮提到的城市：$focusCity")
            if (weatherCity.isNotBlank() && weatherCity != focusCity) add("当前天气城市：$weatherCity")
        }.joinToString("；")
    }

    /** 空参数或纯指代词：这些都不该被当成真实的目标名传给 AppState */
    private val PLACEHOLDERS = setOf(
        "", "这个", "它", "这篇", "那条", "刚才那条", "刚才那篇", "刚才那个", "上面那个",
        "当前", "同上", "刚才", "这里", "这边", "那边", "那儿", "这个城市", "这个地方", "它本身", "null"
    )

    fun isPlaceholder(s: String?): Boolean = s == null || s.trim() in PLACEHOLDERS

    fun facts(state: AppState, recentText: String): SceneFacts {
        val guide = state.currentGuideId?.let { id -> state.guides.firstOrNull { it.id == id } }
        val trip = state.tripDetailId?.let { id -> state.tripPlans.firstOrNull { it.id == id } }
        return SceneFacts(
            guideTitle = guide?.title.orEmpty(),
            guideCity = guide?.city.orEmpty(),
            tripName = trip?.name.orEmpty(),
            tripDestination = trip?.destination.orEmpty(),
            weatherCity = state.weatherCity,
            lastFootprintCity = state.footprints.lastOrNull()?.city.orEmpty(),
            focusCity = CityCoords.detectIn(recentText)
                ?: guide?.city?.takeIf { it.isNotBlank() }
                ?: trip?.destination?.takeIf { it.isNotBlank() }
                ?: ""
        )
    }

    /**
     * 补全一条意图里被省略的参数。只填空/占位词，模型明确给了名字时绝不覆盖。
     * 找不到可靠对象时保持原样，让网关去反问，绝不瞎猜一个相似名字。
     */
    fun resolve(decision: IntentDecision, scene: SceneFacts): IntentDecision {
        val obj = runCatching { JSONObject(decision.raw) }.getOrNull() ?: return decision
        var changed = false

        if (decision.action == "command" && isPlaceholder(decision.target)) {
            val filled = when (decision.kind) {
                "open_guide", "search_guide", "like_guide", "fav_guide" ->
                    scene.guideTitle.takeIf { it.isNotBlank() }
                        ?: scene.guideCity.takeIf { it.isNotBlank() }
                        ?: scene.focusCity.takeIf { it.isNotBlank() }

                "weather_city", "weather_query" ->
                    scene.focusCity.takeIf { it.isNotBlank() }
                        ?: scene.weatherCity.takeIf { it.isNotBlank() }

                "delete_trip" -> scene.tripName.takeIf { it.isNotBlank() }
                "edit_footprint" -> scene.focusCity.takeIf { it.isNotBlank() }
                    ?: scene.lastFootprintCity.takeIf { it.isNotBlank() }

                else -> null
            }
            if (!filled.isNullOrBlank()) {
                obj.put("target", filled)
                changed = true
            }
        }

        // 记足迹时城市/地点若省略，用现场补进 raw，供抽取器当上下文
        if (decision.action == "record_footprint") {
            if (isPlaceholder(obj.optString("city")) && scene.focusCity.isNotBlank()) {
                obj.put("city", scene.focusCity)
                changed = true
            }
            if (isPlaceholder(obj.optString("name")) && scene.guideTitle.isNotBlank()) {
                obj.put("name", scene.guideTitle)
                changed = true
            }
        }

        if (!changed) return decision
        val raw = obj.toString()
        return decision.copy(
            target = obj.optString("target"),
            value = obj.optString("value"),
            raw = raw
        )
    }

    /** 抽取结构化内容时，把现场拼进 spec，让「把这里记一下」能抽出真实城市 */
    fun specWithScene(userText: String, scene: SceneFacts): String {
        val hint = scene.hint()
        return if (hint.isBlank()) userText else "$userText\n（现场信息：$hint）"
    }
}
