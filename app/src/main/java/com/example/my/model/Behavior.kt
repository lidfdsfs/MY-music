package com.example.my.model

/**
 * 用户行为数据结构：曝光、点击、停留、转化
 * 用于因果推断的倾向得分加权与双稳健估计
 */
import kotlin.math.pow
import kotlin.math.exp

/**
 * 曝光上下文特征：混淆变量 + 协变量
 * @param position 曝光位置（0为首屏第一位，越大位置越靠后）
 * @param popularity 物品流行度（过去7天点击量归一化到[0,1]）
 * @param userActivity 用户活跃度（过去30天足迹数归一化到[0,1]）
 * @param itemCategory 物品类别（攻略/地点/游记等）
 * @param userTags 用户偏好标签（如"户外"、"美食"等）
 */
data class ExposureContext(
    val position: Int,
    val popularity: Float,
    val userActivity: Float,
    val itemCategory: String = "",
    val userTags: List<String> = emptyList()
)

/**
 * 曝光行为记录
 * @param userId 用户ID
 * @param itemId 物品ID（攻略ID/地点ID等）
 * @param context 曝光上下文
 * @param timestamp 曝光时间戳（毫秒）
 */
data class Exposure(
    val userId: String,
    val itemId: String,
    val context: ExposureContext,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 用户反馈类型
 */
enum class FeedbackType {
    CLICK, // 点击
    STAY,  // 停留超过2秒
    LIKE,  // 点赞
    COLLECT, // 收藏
    ADD_TO_PLAN // 添加到行程
}

/**
 * 用户反馈记录
 * @param exposureId 关联的曝光ID
 * @param type 反馈类型
 * @param value 反馈值（如停留时长秒数，点击为1）
 * @param timestamp 反馈时间戳
 */
data class Feedback(
    val exposureId: String,
    val type: FeedbackType,
    val value: Float = 1f,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 因果得分结果
 * @param causalScore 去偏后的真实兴趣得分（越高兴趣越大）
 * @param rawScore 原始预测得分（有偏差）
 * @param propensityScore 倾向得分（被曝光的概率）
 */
data class CausalScore(
    val causalScore: Float,
    val rawScore: Float,
    val propensityScore: Float
)
