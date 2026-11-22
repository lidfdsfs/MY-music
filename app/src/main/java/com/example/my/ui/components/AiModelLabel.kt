package com.example.my.ui.components

import com.example.my.data.AiEndpoint
import com.example.my.data.AiFeature
import com.example.my.data.ai.LocalLlm
import com.example.my.state.AppState

/**
 * 给 AI 功能标注「当前实际会用哪个模型」。
 *
 * - 本机路由：显示具体本地模型名（例如 Qwen3-1.7B），不显示「本地模型」这种泛称；
 * - 云端路由：显示端点里配置的模型名（例如 glm-4.7-flash），端点没填模型时退回端点名。
 *
 * 走本机但本机不可用、已经回退云端时，这里展示的是真实会请求的云端模型，
 * 与分配页的「本机待就绪 / 实际走 API」保持一致。
 */
fun aiModelLabel(state: AppState, feature: AiFeature): String =
    aiModelLabel(
        endpoint = state.aiFeatureEndpoint(feature),
        useLocal = state.aiFeatureUsesLocal(feature),
        localModelId = state.aiFeatureLocalModelId(feature)
    )

fun aiModelLabel(
    endpoint: AiEndpoint,
    useLocal: Boolean,
    localModelId: String?
): String {
    if (useLocal) {
        val spec = localModelId?.let { id -> LocalLlm.CATALOG.firstOrNull { it.id == id } }
        return spec?.display ?: "当前本机模型"
    }
    return endpoint.model.ifBlank { endpoint.name }
}
